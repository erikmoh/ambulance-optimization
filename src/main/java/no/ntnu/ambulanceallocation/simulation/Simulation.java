package no.ntnu.ambulanceallocation.simulation;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.stream.IntStream;
import javafx.beans.property.DoubleProperty;
import no.ntnu.ambulanceallocation.optimization.Allocation;
import no.ntnu.ambulanceallocation.simulation.event.Event;
import no.ntnu.ambulanceallocation.simulation.event.HospitalArrival;
import no.ntnu.ambulanceallocation.simulation.event.LocationUpdate;
import no.ntnu.ambulanceallocation.simulation.event.NewCall;
import no.ntnu.ambulanceallocation.simulation.event.PartiallyRespondedCall;
import no.ntnu.ambulanceallocation.simulation.event.SceneDeparture;
import no.ntnu.ambulanceallocation.simulation.grid.Coordinate;
import no.ntnu.ambulanceallocation.simulation.incident.Incident;
import no.ntnu.ambulanceallocation.simulation.incident.IncidentIO;
import no.ntnu.ambulanceallocation.simulation.incident.UrgencyLevel;
import no.ntnu.ambulanceallocation.utils.SimulatedIncidentResult;
import no.ntnu.ambulanceallocation.utils.TriConsumer;
import no.ntnu.ambulanceallocation.utils.Utils;

public final class Simulation {

  private static final Map<Config, List<NewCall>> memoizedEventList = new HashMap<>();

  private final DoubleProperty simulationUpdateInterval;
  private final TriConsumer<LocalDateTime, Collection<Ambulance>, Collection<NewCall>> onTimeUpdate;
  private final Config config;
  private final boolean visualizationMode;
  private final List<Ambulance> ambulances = new ArrayList<>();
  private final Queue<NewCall> callQueue = new LinkedList<>();
  private final PriorityQueue<Event> eventQueue = new PriorityQueue<>();
  private final Map<NewCall, Integer> plannedTravelTimes = new HashMap<>();
  private final Map<ShiftType, Map<BaseStation, Integer>> baseStationShiftCount = new HashMap<>();
  private final Map<BaseStation, List<Ambulance>> baseStationAmbulances = new HashMap<>();
  private final Map<BaseStation, Integer> remainingOffDutyAmbulances = new HashMap<>();
  private SimulationResults simulationResults;
  private LocalDateTime time;
  private ShiftType currentShift;

  public Simulation(final Config config) {
    this.config = config;
    this.visualizationMode = false;
    this.simulationUpdateInterval = null;
    this.onTimeUpdate = null;
  }

  public Simulation(
      final Config config,
      final TriConsumer<LocalDateTime, Collection<Ambulance>, Collection<NewCall>> onTimeUpdate,
      final DoubleProperty simulationUpdateInterval) {
    this.config = config;
    this.visualizationMode = true;
    this.simulationUpdateInterval = simulationUpdateInterval;
    this.onTimeUpdate = onTimeUpdate;
  }

  public static Simulation withConfig(final Config config) {
    return new Simulation(config);
  }

  public static Simulation withDefaultConfig() {
    return new Simulation(Config.defaultConfig());
  }

  public static Simulation withinPeriod(final LocalDateTime start, final LocalDateTime end) {
    return new Simulation(Config.withinPeriod(start, end));
  }

  public static void visualizedSimulation(
      final Allocation allocation,
      final TriConsumer<LocalDateTime, Collection<Ambulance>, Collection<NewCall>> onTimeUpdate,
      final DoubleProperty simulationUpdateInterval) {
    new Simulation(Config.defaultConfig(), onTimeUpdate, simulationUpdateInterval)
        .simulate(allocation);
  }

  public static SimulationResults simulate(
      final List<Integer> dayShiftAllocation, final List<Integer> nightShiftAllocation) {
    return withDefaultConfig()
        .simulate(new Allocation(List.of(dayShiftAllocation, nightShiftAllocation)));
  }

  public SimulationResults simulate(final Allocation allocation) {
    initialize(allocation);
    Event event;
    time = null;

    while (!eventQueue.isEmpty()) {
      event = eventQueue.poll();
      if (time != null && event.getTime().isBefore(time)) {
        throw new IllegalStateException("Event queue is not sorted");
      }

      time = event.getTime();
      setCurrentShift();

      try {
        switch (event) {
          case NewCall newCall -> handleNewCall(newCall);
          case SceneDeparture sceneDeparture -> handleSceneDeparture(sceneDeparture);
          case HospitalArrival hospitalArrival -> handleHospitalArrival(hospitalArrival);
          case LocationUpdate locationUpdate -> handleLocationUpdate(locationUpdate);
        }
      } catch (Exception e) {
        e.printStackTrace();
        System.exit(0);
      }

      if (visualizationMode) {
        visualizationCallback();
      }
    }

    return simulationResults;
  }

  private void createEventQueue() {
    if (memoizedEventList.containsKey(config)) {
      eventQueue.addAll(memoizedEventList.get(config));
    } else {
      var events =
          IncidentIO.incidents.stream().filter(this::isInTimeRange).map(this::toNewCall).toList();
      eventQueue.addAll(events);
      memoizedEventList.put(config, events);
    }
  }

  private boolean isInTimeRange(Incident incident) {
    var callReceived = incident.callReceived();
    var bufferStartDateTime = config.START_DATE_TIME().minusHours(config.BUFFER_SIZE());
    return callReceived.isAfter(bufferStartDateTime)
        && callReceived.isBefore(config.END_DATE_TIME());
  }

  private NewCall toNewCall(Incident incident) {
    var providesResponseTime = incident.callReceived().isAfter(config.START_DATE_TIME());
    return new NewCall(incident, providesResponseTime);
  }

  private void initialize(final Allocation allocation) {
    simulationResults = new SimulationResults();
    callQueue.clear();
    eventQueue.clear();
    createEventQueue();
    currentShift = ShiftType.get(config.START_DATE_TIME());
    baseStationShiftCount.clear();
    baseStationAmbulances.clear();
    remainingOffDutyAmbulances.clear();
    baseStationShiftCount.put(ShiftType.DAY, new HashMap<>());
    baseStationShiftCount.put(ShiftType.NIGHT, new HashMap<>());

    var j = 1;
    for (var baseStation : BaseStation.values()) {
      var dayShiftCount =
          Collections.frequency(allocation.getDayShiftAllocation(), baseStation.getId());
      var nightShiftCount =
          Collections.frequency(allocation.getNightShiftAllocation(), baseStation.getId());
      var maxBaseStationAmbulances = Math.max(dayShiftCount, nightShiftCount);

      var ambulancesStation =
          IntStream.rangeClosed(j, maxBaseStationAmbulances + j - 1)
              .mapToObj(i -> new Ambulance(baseStation, i))
              .toList();
      j += ambulancesStation.size();

      baseStationAmbulances.put(baseStation, ambulancesStation);
      baseStationShiftCount.get(ShiftType.DAY).put(baseStation, dayShiftCount);
      baseStationShiftCount.get(ShiftType.NIGHT).put(baseStation, nightShiftCount);

      ambulances.addAll(ambulancesStation);
      remainingOffDutyAmbulances.put(baseStation, 0);
      ambulancesStation.stream()
          .limit(baseStationShiftCount.get(currentShift).get(baseStation))
          .forEach(Ambulance::startNewShift);
    }
  }

  private void setCurrentShift() {
    var newShift = ShiftType.get(time);
    if (newShift != currentShift) {
      currentShift = newShift;

      for (var baseStation : baseStationAmbulances.keySet()) {
        var shiftCountMap = baseStationShiftCount.get(currentShift);
        var prevShiftCountMap = baseStationShiftCount.get(currentShift.previous());
        var ambulanceDifference =
            prevShiftCountMap.get(baseStation) - shiftCountMap.get(baseStation);

        if (ambulanceDifference > 0) {
          var availableAmbulances =
              baseStationAmbulances.get(baseStation).stream()
                  .filter(Ambulance::isAvailable)
                  .limit(ambulanceDifference)
                  .toList();

          availableAmbulances.forEach(Ambulance::finishShift);

          remainingOffDutyAmbulances.put(
              baseStation, ambulanceDifference - availableAmbulances.size());

        } else if (ambulanceDifference < 0) {
          baseStationAmbulances.get(baseStation).stream()
              .filter(Ambulance::isOffDuty)
              .limit(-ambulanceDifference)
              .forEach(Ambulance::startNewShift);
        }
      }
    }
  }

  private void handleNewCall(NewCall newCall) {
    var dispatchedAmbulances = dispatch(newCall);

    if (dispatchedAmbulances.isEmpty()) {
      return;
    }

    var incident = newCall.incident;
    var firstAmbulance = dispatchedAmbulances.get(0);
    var travelTime = firstAmbulance.timeTo(incident);

    // set or update travel time
    plannedTravelTimes.put(newCall, travelTime);

    var timeToNextEvent = 0;

    if (incident.departureFromScene().isPresent()) {
      // An ambulance transported patients to a hospital
      if (incident.arrivalAtScene().isPresent()) {
        var timeAtScene = incident.getTimeSpentAtScene();
        timeToNextEvent = travelTime + timeAtScene;
      } else {
        // No arrival time at scene, so we simulate it by using dispatch and travel time
        var simulatedArrivalTime = incident.dispatched().plusSeconds(travelTime);
        var timeAtScene =
            ChronoUnit.SECONDS.between(simulatedArrivalTime, incident.departureFromScene().get());
        timeToNextEvent = (int) (travelTime + timeAtScene);
      }
    } else {
      // No ambulances transported patients to a hospital so the job will be completed
      if (incident.arrivalAtScene().isPresent()) {
        // Job is completed when the ambulance leaves the scene
        var timeAtScene = incident.getTimeSpentAtSceneNonTransport();
        timeToNextEvent = travelTime + timeAtScene;
      } else {
        // The incident had no arrival at scene time, so it is assumed that it was aborted
        timeToNextEvent = incident.getTimeBeforeAborting();
      }
    }

    eventQueue.add(
        new SceneDeparture(time.plusSeconds(timeToNextEvent), newCall, dispatchedAmbulances));
  }

  private void handleSceneDeparture(SceneDeparture sceneDeparture) {
    var newCall = sceneDeparture.newCall;

    for (var ambulance : sceneDeparture.getAmbulances()) {
      if (ambulance.isTransport()) {
        var transportTime = ambulance.getTimeToHospital();
        var availableTime = time.plusSeconds(transportTime);
        ambulance.transport();
        eventQueue.add(new HospitalArrival(availableTime, ambulance, newCall));
      } else {
        jobCompleted(ambulance, newCall);
      }
    }
  }

  private void handleHospitalArrival(HospitalArrival hospitalArrival) {
    hospitalArrival.ambulance.arriveAtHospital();

    jobCompleted(hospitalArrival.ambulance, hospitalArrival.newCall);

    hospitalArrival.ambulance.dispatchNextCall();
  }

  private void jobCompleted(Ambulance ambulance, NewCall newCall) {
    ambulance.flagAsAvailable();

    var ambulancesToReturn = remainingOffDutyAmbulances.get(ambulance.getBaseStation());
    if (ambulancesToReturn > 0) {
      ambulance.finishShift();
      remainingOffDutyAmbulances.put(ambulance.getBaseStation(), --ambulancesToReturn);
    }

    eventQueue.add(
        new LocationUpdate(time.plusSeconds(config.UPDATE_LOCATION_PERIOD()), ambulance));

    var travelTime = plannedTravelTimes.remove(newCall);
    if (newCall.providesResponseTime && travelTime != null) {
      saveResponseTime(newCall, travelTime);
    }

    checkQueue();
  }

  private void handleLocationUpdate(LocationUpdate locationUpdate) {
    var ambulance = locationUpdate.ambulance;
    ambulance.updateLocation(config.UPDATE_LOCATION_PERIOD());

    if (!ambulance.endOfJourney()) {
      eventQueue.add(
          new LocationUpdate(time.plusMinutes(config.UPDATE_LOCATION_PERIOD()), ambulance));
    }
  }

  private List<Ambulance> dispatch(NewCall newCall) {
    var incident = newCall.incident;
    var transportDemand = newCall.getTransportingVehicleDemand();
    var nonTransportDemand = newCall.getNonTransportingVehicleDemand();
    var demand = transportDemand + nonTransportDemand;

    var available = new ArrayList<Ambulance>();
    var reassignable = new ArrayList<Ambulance>();
    var queueable = new ArrayList<Ambulance>();
    // fill available ambulance lists
    getAllAvailableAmbulances(incident, demand, available, reassignable, queueable);

    var supply = available.size();
    if (supply == 0) {
      callQueue.add(newCall);
      return Collections.emptyList();
    }

    // update ambulance dispatch score based on dispatch strategy
    available.forEach(a -> config.DISPATCH_POLICY().updateAmbulance(a, available, incident));

    // sort ambulances based on dispatch score.
    // if reassign score is equal to regular, regular ambulance will be first when sorted
    var nearestAmbulances =
        available.stream().sorted(Comparator.comparing(Ambulance::getDispatchScore)).toList();

    // dispatch transport ambulances
    var hospital = transportDemand > 0 ? findNearestHospital(incident) : null;
    var transportAmbulances =
        nearestAmbulances.subList(0, Math.min(transportDemand, supply)).stream()
            .peek(a -> a.dispatch(newCall, hospital, queueable.contains(a)))
            .toList();
    var dispatchedTransport = transportAmbulances.size();

    // dispatch non-transport ambulances
    var nonTransportAmbulances =
        nearestAmbulances
            .subList(
                dispatchedTransport, Math.min(dispatchedTransport + nonTransportDemand, supply))
            .stream()
            .peek(a -> a.dispatch(newCall, null, queueable.contains(a)))
            .toList();
    var dispatchedNonTransport = nonTransportAmbulances.size();

    // all dispatched ambulances
    var dispatchedAmbulances = Utils.concatenateLists(transportAmbulances, nonTransportAmbulances);

    // remove old events if ambulances were reassigned
    removeOldDispatchEvents(dispatchedAmbulances, reassignable, newCall);

    // create partially responded call
    if (dispatchedTransport < transportDemand || dispatchedNonTransport < nonTransportDemand) {
      var partiallyRespondedCall = new PartiallyRespondedCall(newCall);
      partiallyRespondedCall.respondWithTransportingVehicles(transportAmbulances.size());
      partiallyRespondedCall.respondWithNonTransportingVehicles(dispatchedNonTransport);
      callQueue.add(partiallyRespondedCall);
    }

    return dispatchedAmbulances;
  }

  private void getAllAvailableAmbulances(
      Incident incident,
      int demand,
      List<Ambulance> availableAmbulances,
      List<Ambulance> reassignAmbulances,
      List<Ambulance> queueAmbulances) {

    availableAmbulances.addAll(Utils.filterList(ambulances, Ambulance::isAvailable));

    // add ambulances that are already on their way to an incident
    if (doReDispatch(incident)) {
      reassignAmbulances.addAll(Utils.filterList(ambulances, a -> a.canBeReassigned(incident)));
      availableAmbulances.addAll(reassignAmbulances);
    }

    // add ambulances that are transporting to hospital and can be queued for next incident
    if (doQueueNext(demand)) {
      queueAmbulances.addAll(Utils.filterList(ambulances, Ambulance::canBeQueued));
      availableAmbulances.addAll(queueAmbulances);
    }
  }

  private boolean doReDispatch(Incident incident) {
    return config.ENABLE_REDISPATCH() && incident.urgencyLevel().equals(UrgencyLevel.ACUTE)
        || incident.urgencyLevel().equals(UrgencyLevel.URGENT);
  }

  private boolean doQueueNext(int totalDemand) {
    // problems occur when multiple ambulances is needed
    // (could potentially be fixed, but might be complicated and slow for little gain)
    return config.ENABLE_QUEUE_NEXT() && totalDemand == 1;
  }

  private void removeOldDispatchEvents(
      List<Ambulance> dispatchedAmbulances, List<Ambulance> reassignAmbulances, NewCall newCall) {
    // remove old events for dispatched reassigned ambulances
    if (config.ENABLE_REDISPATCH()) {
      reassignAmbulances.stream()
          .filter(dispatchedAmbulances::contains)
          .forEach(
              ambulance -> {
                var oldCall = ambulance.getCall();
                if (eventQueue.removeIf(
                    e -> e instanceof SceneDeparture && e.newCall.equals(oldCall))) {
                  handleNewCall(oldCall);
                }
                ambulance.setWasReassigned(true);
              });
      dispatchedAmbulances.forEach(ambulance -> ambulance.setCall(newCall));
    }
  }

  private static Coordinate findNearestHospital(Incident incident) {
    return Arrays.stream(Hospital.values())
        .min(Hospital.closestTo(incident))
        .map(Hospital::getCoordinate)
        .orElseThrow();
  }

  private void checkQueue() {
    var availableAmbulances = (int) ambulances.stream().filter(Ambulance::isAvailable).count();

    while (!callQueue.isEmpty() && availableAmbulances > 0) {
      var newCall = callQueue.poll();

      if (newCall instanceof PartiallyRespondedCall call) {
        eventQueue.add(new PartiallyRespondedCall(call, time));
      } else {
        eventQueue.add(new NewCall(newCall, time));
      }

      availableAmbulances -=
          newCall.getNonTransportingVehicleDemand() + newCall.getTransportingVehicleDemand();
    }
  }

  private void saveResponseTime(NewCall newCall, int travelTime) {
    var incident = newCall.incident;

    var simulatedDispatchTime =
        (int) ChronoUnit.SECONDS.between(incident.callReceived(), newCall.getTime());

    var responseTime = config.DISPATCH_DELAY().get(incident) + simulatedDispatchTime + travelTime;
    if (responseTime < 0) {
      throw new IllegalStateException("Response time should never be negative");
    }

    simulationResults.add(
        new SimulatedIncidentResult(
            incident.callReceived(), responseTime, incident.urgencyLevel()));
  }

  private void visualizationCallback() {
    if (onTimeUpdate == null || simulationUpdateInterval == null) {
      throw new IllegalStateException("Cannot call visualize method in non-visualized simulation");
    }

    Ambulance.setCurrentGlobalTime(time);
    onTimeUpdate.accept(time, ambulances, callQueue);

    try {
      Thread.sleep(simulationUpdateInterval.longValue());
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
