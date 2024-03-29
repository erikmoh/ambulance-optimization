package no.ntnu.ambulanceallocation.simulation;

import java.time.Duration;
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
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.stream.IntStream;
import javafx.beans.property.DoubleProperty;
import no.ntnu.ambulanceallocation.optimization.Allocation;
import no.ntnu.ambulanceallocation.simulation.event.AbortIncident;
import no.ntnu.ambulanceallocation.simulation.event.Event;
import no.ntnu.ambulanceallocation.simulation.event.HospitalDeparture;
import no.ntnu.ambulanceallocation.simulation.event.LocationUpdate;
import no.ntnu.ambulanceallocation.simulation.event.NewCall;
import no.ntnu.ambulanceallocation.simulation.event.PartiallyRespondedCall;
import no.ntnu.ambulanceallocation.simulation.event.SceneArrival;
import no.ntnu.ambulanceallocation.simulation.event.SceneDeparture;
import no.ntnu.ambulanceallocation.simulation.grid.Coordinate;
import no.ntnu.ambulanceallocation.simulation.incident.Incident;
import no.ntnu.ambulanceallocation.simulation.incident.IncidentIO;
import no.ntnu.ambulanceallocation.utils.SimulatedIncidentResult;
import no.ntnu.ambulanceallocation.utils.TriConsumer;
import no.ntnu.ambulanceallocation.utils.Utils;

public final class Simulation {

  private static final Map<Config, List<Incident>> memoizedIncidentList = new HashMap<>();
  private static int factor = 0;

  private final DoubleProperty simulationUpdateInterval;
  private final TriConsumer<LocalDateTime, Collection<Ambulance>, Collection<NewCall>> onTimeUpdate;
  private final Config config;
  private final boolean visualizationMode;
  private final List<Ambulance> ambulances = new ArrayList<>();
  private final Queue<NewCall> callQueue = new LinkedList<>();
  private final PriorityQueue<Event> eventQueue = new PriorityQueue<>();
  private final Map<Incident, Integer> plannedTravelTimes = new HashMap<>();
  private final Map<ShiftType, Map<BaseStation, Integer>> baseStationShiftCount = new HashMap<>();
  private final Map<BaseStation, List<Ambulance>> baseStationAmbulances = new HashMap<>();
  private final Map<BaseStation, Integer> remainingOffDutyAmbulances = new HashMap<>();
  private final Map<Incident, List<Ambulance>> ambulancesAtScene = new HashMap<>();
  private SimulationResults simulationResults;
  private LocalDateTime time;
  private ShiftType currentShift;
  private long lastVisualUpdate = 0;
  private LocalDateTime lastInternalUpdate = LocalDateTime.MIN;

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

  public static SimulationResults simulate(
      final List<Integer> dayShiftAllocation, final List<Integer> nightShiftAllocation, int f) {
    factor = f;
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
          case NewCall newCall -> handleNewCall(newCall, false);
          case AbortIncident abortIncident -> handleAbortIncident(abortIncident);
          case SceneArrival sceneArrival -> handleSceneArrival(sceneArrival);
          case SceneDeparture sceneDeparture -> handleSceneDeparture(sceneDeparture);
          case HospitalDeparture hospitalDeparture -> handleHospitalDeparture(hospitalDeparture);
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
    if (memoizedIncidentList.containsKey(config)) {
      var events = memoizedIncidentList.get(config);
      eventQueue.addAll(events.stream().map(this::toNewCall).toList());
    } else {
      var incidents = IncidentIO.incidents.stream().filter(this::isInTimeRange).toList();
      eventQueue.addAll(incidents.stream().map(this::toNewCall).toList());
      memoizedIncidentList.put(config, incidents);
    }
  }

  private boolean isInTimeRange(Incident incident) {
    var callReceived = incident.callReceived();
    var bufferStartDateTime = config.START_DATE_TIME().minusHours(config.BUFFER_SIZE());
    return callReceived.isAfter(bufferStartDateTime)
        && callReceived.isBefore(config.END_DATE_TIME());
  }

  private NewCall toNewCall(Incident incident) {
    var providesResponseTime =
        incident.callReceived().isAfter(config.START_DATE_TIME())
            && !incident.urgencyLevel().isRegular();
    return new NewCall(incident, providesResponseTime);
  }

  private void initialize(final Allocation allocation) {
    // in case simulate() is called multiple times on the same simulation object
    callQueue.clear();
    eventQueue.clear();
    baseStationShiftCount.clear();
    plannedTravelTimes.clear();
    baseStationAmbulances.clear();
    remainingOffDutyAmbulances.clear();
    ambulancesAtScene.clear();

    createEventQueue();
    simulationResults = new SimulationResults();
    baseStationShiftCount.put(ShiftType.DAY, new HashMap<>());
    baseStationShiftCount.put(ShiftType.NIGHT, new HashMap<>());
    currentShift = ShiftType.get(config.START_DATE_TIME());
    Ambulance.setConfig(config);

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

  private void handleNewCall(NewCall newCall, boolean reassigned) {
    var dispatchedAmbulances = dispatch(newCall);

    if (dispatchedAmbulances.isEmpty()) {
      return;
    }

    var incident = newCall.incident;
    var callQueueTime = (int) ChronoUnit.SECONDS.between(incident.callReceived(), time);
    var handlingTime = config.HANDLING_DELAY().get(incident);
    if (reassigned) {
      handlingTime = Math.max(0, handlingTime - callQueueTime);
    }

    if (incident.departureFromScene().isEmpty() && incident.arrivalAtScene().isEmpty()) {
      // Assume incident was aborted
      var abortTime = time.plusSeconds(incident.getTimeBeforeAborting());
      eventQueue.add(new AbortIncident(abortTime, newCall, dispatchedAmbulances));

      for (var ambulance : dispatchedAmbulances) {
        if (ambulance.getNextCall() != null) {
          // ambulance is queued for this event but busy with previous
          continue;
        }
        var delay = handlingTime + config.DISPATCH_DELAY().get(incident, ambulance);
        var updateTime = time.plusSeconds(delay).plusMinutes(config.UPDATE_LOCATION_PERIOD());
        if (updateTime.isBefore(abortTime)) {
          eventQueue.add(new LocationUpdate(updateTime, ambulance));
        }
      }
      return;
    }

    var firstAmbulance = dispatchedAmbulances.get(0);
    var timeToIncident = firstAmbulance.getUpdatedTimeToIncident(incident);
    var waitingTime = Math.max(callQueueTime, handlingTime);
    if (reassigned) {
      waitingTime = callQueueTime + handlingTime;
    }
    var responseTime = waitingTime + timeToIncident;

    if (newCall instanceof PartiallyRespondedCall && plannedTravelTimes.containsKey(incident)) {
      responseTime = Math.min(plannedTravelTimes.get(incident), responseTime);
    }
    // set or update travel time
    plannedTravelTimes.put(incident, responseTime);

    int timeAtScene;
    if (incident.departureFromScene().isPresent()) {
      // An ambulance should transport patients to a hospital
      if (incident.arrivalAtScene().isPresent()) {
        timeAtScene = incident.getTimeSpentAtScene();
      } else {
        // No arrival time at scene, so we simulate it by using dispatch and travel time
        var simulatedArrivalTime = time.plusSeconds(timeToIncident);
        timeAtScene =
            (int)
                ChronoUnit.SECONDS.between(
                    simulatedArrivalTime, incident.departureFromScene().get());
      }
    } else {
      // No ambulances transported patients to a hospital so the job will be completed
      timeAtScene = incident.getTimeSpentAtSceneNonTransport();
    }

    var departureTime =
        time.plusSeconds(handlingTime).plusSeconds(timeToIncident).plusSeconds(timeAtScene);

    for (var ambulance : dispatchedAmbulances) {
      var arrivalTime =
          time.plusSeconds(handlingTime).plusSeconds(ambulance.getUpdatedTimeToIncident(incident));
      eventQueue.add(new SceneArrival(arrivalTime, newCall, ambulance, departureTime));

      if (ambulance.getNextCall() != null) {
        // ambulance is queued for this event but busy with previous
        continue;
      }

      var delay = handlingTime + config.DISPATCH_DELAY().get(incident, ambulance);
      var updateTime = time.plusSeconds(delay).plusMinutes(config.UPDATE_LOCATION_PERIOD());
      if (updateTime.isBefore(arrivalTime)) {
        eventQueue.add(new LocationUpdate(updateTime, ambulance));
      }
    }
  }

  private void handleAbortIncident(AbortIncident abortIncident) {
    for (var ambulance : abortIncident.getAmbulances()) {
      if (ambulance.getNextCall() != null) {
        // ambulance is busy with previous event, but cancel this queued incident
        ambulance.removeNextCall();
        continue;
      }
      jobCompleted(ambulance);
    }
  }

  private void handleSceneArrival(SceneArrival sceneArrival) {
    var incident = sceneArrival.incident;
    var ambulance = sceneArrival.ambulance;

    ambulance.arriveAtScene();

    ambulancesAtScene.computeIfAbsent(incident, k -> new ArrayList<>()).add(ambulance);
    // only depart if all ambulances have arrived
    if (ambulancesAtScene.get(incident).size() < incident.getDemand()) {
      return;
    }

    var newCall = sceneArrival.newCall;
    var travelTime = plannedTravelTimes.remove(newCall.incident);
    if (newCall.providesResponseTime && travelTime != null) {
      saveResponseTime(newCall, travelTime);
    }

    var departureTime = sceneArrival.departureTime;
    if (time.isAfter(departureTime)) {
      departureTime = time;
    }

    var dispatchedAmbulances = ambulancesAtScene.remove(incident);

    eventQueue.add(new SceneDeparture(departureTime, sceneArrival.newCall, dispatchedAmbulances));
  }

  private void handleSceneDeparture(SceneDeparture sceneDeparture) {
    var newCall = sceneDeparture.newCall;

    for (var ambulance : sceneDeparture.getAmbulances()) {
      if (ambulance.isTransport()) {
        ambulance.transport();

        var transportTime = ambulance.getTimeToHospital();
        var hospitalTime = newCall.incident.getHospitalTime(config);
        var availableTime = time.plusSeconds(transportTime + hospitalTime);
        eventQueue.add(new HospitalDeparture(availableTime, ambulance, ambulance.getCall()));

        var updateTime = time.plusMinutes(config.UPDATE_LOCATION_PERIOD());
        if (updateTime.isBefore(availableTime)) {
          eventQueue.add(new LocationUpdate(updateTime, ambulance));
        }
      } else {
        jobCompleted(ambulance);
      }
    }
  }

  private void handleHospitalDeparture(HospitalDeparture hospitalDeparture) {
    var ambulance = hospitalDeparture.ambulance;

    ambulance.arriveAtHospital();
    ambulance.flagAsAvailable();
    var dispatched = ambulance.dispatchNextCall();

    var updateTime = time.plusMinutes(config.UPDATE_LOCATION_PERIOD());
    if (dispatched) {
      // queued event was dispatched so create next LocationUpdate for ambulance
      if (ambulance.notArrived() && ambulance.willArriveAfter(updateTime)) {
        eventQueue.add(new LocationUpdate(updateTime, ambulance));
      }
    } else {
      // return to base station
      var stationTime = time.plusSeconds(ambulance.getTimeToBaseStation());
      if (stationTime.isBefore(updateTime)) {
        updateTime = stationTime;
      }
      eventQueue.add(new LocationUpdate(updateTime, ambulance));
    }
    checkQueue();
  }

  private void jobCompleted(Ambulance ambulance) {
    ambulance.flagAsAvailable();

    var ambulancesToReturn = remainingOffDutyAmbulances.get(ambulance.getBaseStation());
    if (ambulancesToReturn > 0) {
      ambulance.finishShift();
      remainingOffDutyAmbulances.put(ambulance.getBaseStation(), --ambulancesToReturn);
    }

    var stationTime = time.plusSeconds(ambulance.getTimeToBaseStation());
    var updateTime = time.plusMinutes(config.UPDATE_LOCATION_PERIOD());
    if (stationTime.isBefore(updateTime)) {
      updateTime = stationTime;
    }
    eventQueue.add(new LocationUpdate(updateTime, ambulance));

    checkQueue();
  }

  private void handleLocationUpdate(LocationUpdate locationUpdate) {
    var ambulance = locationUpdate.ambulance;
    ambulance.updateLocation(config.UPDATE_LOCATION_PERIOD());

    var updateTime = time.plusMinutes(config.UPDATE_LOCATION_PERIOD());
    if (ambulance.notArrived() && ambulance.willArriveAfter(updateTime)) {
      eventQueue.add(new LocationUpdate(updateTime, ambulance));
    }
  }

  private List<Ambulance> dispatch(NewCall newCall) {
    var incident = newCall.incident;
    // call can be partially responded, so use call.getDemand instead of incident.getDemand
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
    available.forEach(
        a ->
            config
                .DISPATCH_POLICY()
                .updateAmbulance(
                    a, available, incident, demand, baseStationAmbulances, time, config, factor));

    // sort ambulances based on dispatch score.
    // if reassign score is equal to regular, regular ambulance will be first when sorted
    var bestAmbulances =
        available.stream().sorted(Comparator.comparing(Ambulance::getDispatchScore)).toList();

    // dispatch transport ambulances
    var hospital = transportDemand > 0 ? findNearestHospital(incident) : null;
    var transportAmbulances =
        bestAmbulances.subList(0, Math.min(transportDemand, supply)).stream()
            .peek(a -> a.dispatch(newCall, hospital))
            .toList();
    var dispatchedTransport = transportAmbulances.size();

    // dispatch non-transport ambulances
    var nonTransportAmbulances =
        bestAmbulances
            .subList(
                dispatchedTransport, Math.min(dispatchedTransport + nonTransportDemand, supply))
            .stream()
            .peek(a -> a.dispatch(newCall, null))
            .toList();
    var dispatchedNonTransport = nonTransportAmbulances.size();

    // all dispatched ambulances
    var dispatchedAmbulances = Utils.concatenateLists(transportAmbulances, nonTransportAmbulances);

    // remove old events if ambulances were reassigned
    removeOldDispatchEvents(dispatchedAmbulances, reassignable);
    dispatchedAmbulances.stream()
        .filter(ambulance -> !queueable.contains(ambulance))
        .forEach(ambulance -> ambulance.setCall(newCall));

    // create partially responded call
    if (dispatchedTransport < transportDemand || dispatchedNonTransport < nonTransportDemand) {
      var partiallyRespondedCall = new PartiallyRespondedCall(newCall);
      partiallyRespondedCall.respondWithTransportingVehicles(dispatchedTransport);
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
    return config.ENABLE_REDISPATCH() && !incident.urgencyLevel().isRegular();
  }

  private boolean doQueueNext(int totalDemand) {
    // problems occur when multiple ambulances is needed
    // (could potentially be fixed, but might be complicated and slow for little gain)
    return config.ENABLE_QUEUE_NEXT() && totalDemand == 1;
  }

  private void removeOldDispatchEvents(
      List<Ambulance> dispatchedAmbulances, List<Ambulance> reassignAmbulances) {
    // remove old events for dispatched reassigned ambulances
    if (config.ENABLE_REDISPATCH() && !reassignAmbulances.isEmpty()) {
      reassignAmbulances.stream()
          .filter(dispatchedAmbulances::contains)
          .forEach(
              ambulance -> {
                var oldCall = ambulance.getCall();
                eventQueue.removeIf(e -> Objects.equals(e.newCall, oldCall));
                handleNewCall(oldCall, true);
                ambulance.setReassigned(true);
              });
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

    var responseTime = simulatedDispatchTime + travelTime;
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

    try {
      var internalTimeSinceUpdate = Duration.between(lastInternalUpdate, time);
      if (internalTimeSinceUpdate.getSeconds() < 120) {
        return;
      }

      var timeSinceUpdate = System.currentTimeMillis() - lastVisualUpdate;
      var updateInterval = Math.max(10, simulationUpdateInterval.longValue());
      if (timeSinceUpdate < updateInterval) {
        Thread.sleep(updateInterval - timeSinceUpdate);
      }

      onTimeUpdate.accept(time, ambulances, callQueue);

      lastInternalUpdate = time;
      lastVisualUpdate = System.currentTimeMillis();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
