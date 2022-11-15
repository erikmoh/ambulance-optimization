package no.ntnu.ambulanceallocation.simulation;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.stream.Stream;

import javafx.beans.property.DoubleProperty;
import no.ntnu.ambulanceallocation.optimization.Allocation;
import no.ntnu.ambulanceallocation.simulation.event.Event;
import no.ntnu.ambulanceallocation.simulation.event.JobCompletion;
import no.ntnu.ambulanceallocation.simulation.event.LocationUpdate;
import no.ntnu.ambulanceallocation.simulation.event.NewCall;
import no.ntnu.ambulanceallocation.simulation.event.PartiallyRespondedCall;
import no.ntnu.ambulanceallocation.simulation.event.SceneDeparture;
import no.ntnu.ambulanceallocation.simulation.grid.Coordinate;
import no.ntnu.ambulanceallocation.simulation.incident.Incident;
import no.ntnu.ambulanceallocation.simulation.incident.IncidentIO;
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
  private final Map<ShiftType, Map<BaseStation, Integer>> baseStationShiftCount = new HashMap<>();
  private final Map<BaseStation, List<Ambulance>> baseStationAmbulances = new HashMap<>();
  private final Map<BaseStation, Integer> remainingOffDutyAmbulances = new HashMap<>();
  private ResponseTimes responseTimes;
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

  public static ResponseTimes simulate(
      final List<Integer> dayShiftAllocation, final List<Integer> nightShiftAllocation) {
    return withDefaultConfig()
        .simulate(new Allocation(List.of(dayShiftAllocation, nightShiftAllocation)));
  }

  public ResponseTimes simulate(final Allocation allocation) {
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

      switch (event) {
        case NewCall newCall -> handleNewCall(newCall);
        case SceneDeparture sceneDeparture -> handleSceneDeparture(sceneDeparture);
        case JobCompletion jobCompletion -> handleJobCompletion(jobCompletion);
        case LocationUpdate locationUpdate -> handleLocationUpdate(locationUpdate);
      }

      if (visualizationMode) {
        visualizationCallback();
      }
    }

    return responseTimes;
  }

  private void createEventQueue() {
    if (memoizedEventList.containsKey(config)) {
      eventQueue.addAll(memoizedEventList.get(config));
    } else {
      var events =
          IncidentIO.incidents.stream()
              .filter(
                  incident ->
                      incident
                              .callReceived()
                              .isAfter(config.START_DATE_TIME().minusHours(config.BUFFER_SIZE()))
                          && incident.callReceived().isBefore(config.END_DATE_TIME()))
              .map(
                  incident ->
                      new NewCall(
                          incident, incident.callReceived().isAfter(config.START_DATE_TIME())))
              .toList();
      eventQueue.addAll(events);
      memoizedEventList.put(config, events);
    }
  }

  private void initialize(final Allocation allocation) {
    responseTimes = new ResponseTimes();
    callQueue.clear();
    eventQueue.clear();
    createEventQueue();
    currentShift = ShiftType.get(config.START_DATE_TIME());
    baseStationShiftCount.clear();
    baseStationAmbulances.clear();
    remainingOffDutyAmbulances.clear();
    baseStationShiftCount.put(ShiftType.DAY, new HashMap<>());
    baseStationShiftCount.put(ShiftType.NIGHT, new HashMap<>());

    for (var baseStation : BaseStation.values()) {
      var dayShiftCount =
          Collections.frequency(allocation.getDayShiftAllocation(), baseStation.getId());
      var nightShiftCount =
          Collections.frequency(allocation.getNightShiftAllocation(), baseStation.getId());
      var maxBaseStationAmbulances = Math.max(dayShiftCount, nightShiftCount);

      baseStationAmbulances.put(
          baseStation,
          Stream.generate(() -> new Ambulance(baseStation))
              .limit(maxBaseStationAmbulances)
              .toList());
      baseStationShiftCount.get(ShiftType.DAY).put(baseStation, dayShiftCount);
      baseStationShiftCount.get(ShiftType.NIGHT).put(baseStation, nightShiftCount);
      ambulances.addAll(baseStationAmbulances.get(baseStation));
      remainingOffDutyAmbulances.put(baseStation, 0);
      baseStationAmbulances.get(baseStation).stream()
          .limit(baseStationShiftCount.get(currentShift).get(baseStation))
          .forEach(Ambulance::startNewShift);
    }
  }

  private void setCurrentShift() {
    if (ShiftType.get(time) != currentShift) {
      currentShift = ShiftType.get(time);

      for (var baseStation : baseStationAmbulances.keySet()) {
        var ambulanceDifference =
            baseStationShiftCount.get(currentShift.previous()).get(baseStation)
                - baseStationShiftCount.get(currentShift).get(baseStation);
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

    if (!dispatchedAmbulances.isEmpty()) {
      var firstAmbulance = dispatchedAmbulances.get(0);
      var travelTime = firstAmbulance.timeTo(newCall.incident);
      if (newCall.incident.departureFromScene().isPresent()) {
        var duration = newCall.incident.getTimeSpentAtScene();
        eventQueue.add(new SceneDeparture(time.plusSeconds(travelTime + duration), newCall));
      } else {
        for (var ambulance : dispatchedAmbulances) {
          var duration = newCall.incident.getTimeSpentAtSceneNonTransport();
          eventQueue.add(new JobCompletion(time.plusSeconds(travelTime + duration), ambulance));
        }
      }
      saveResponseTime(newCall, travelTime);
    }
  }

  private void handleSceneDeparture(SceneDeparture sceneDeparture) {
    var assignedAmbulances =
        Utils.filterList(
            ambulances, (ambulance) -> ambulance.getIncident() == sceneDeparture.incident);

    for (var ambulance : assignedAmbulances) {
      if (ambulance.isTransport()) {
        var transportTime = sceneDeparture.incident.getTimeFromDepartureToAvailableTransport();
        ambulance.transport();
        eventQueue.add(new JobCompletion(time.plusSeconds(transportTime), ambulance));
      } else {
        ambulance.flagAsAvailable();

        var ambulancesToReturn = remainingOffDutyAmbulances.get(ambulance.getBaseStation());

        if (ambulancesToReturn > 0) {
          ambulance.finishShift();
          remainingOffDutyAmbulances.put(ambulance.getBaseStation(), --ambulancesToReturn);
        }

        eventQueue.add(
            new LocationUpdate(time.plusSeconds(config.UPDATE_LOCATION_PERIOD()), ambulance));
      }
    }

    checkQueue();
  }

  private void handleJobCompletion(JobCompletion jobCompletion) {
    if (jobCompletion.ambulance.isTransport()) {
      jobCompletion.ambulance.arriveAtHospital();
    }

    jobCompletion.ambulance.flagAsAvailable();

    var ambulancesToReturn =
        remainingOffDutyAmbulances.get(jobCompletion.ambulance.getBaseStation());

    if (ambulancesToReturn > 0) {
      jobCompletion.ambulance.finishShift();
      remainingOffDutyAmbulances.put(
          jobCompletion.ambulance.getBaseStation(), --ambulancesToReturn);
    }

    eventQueue.add(
        new LocationUpdate(
            time.plusMinutes(config.UPDATE_LOCATION_PERIOD()), jobCompletion.ambulance));

    checkQueue();
  }

  private void handleLocationUpdate(LocationUpdate locationUpdate) {
    locationUpdate.ambulance.updateLocation(config.UPDATE_LOCATION_PERIOD());

    if (!locationUpdate.ambulance.endOfJourney()) {
      eventQueue.add(
          new LocationUpdate(
              time.plusMinutes(config.UPDATE_LOCATION_PERIOD()), locationUpdate.ambulance));
    }
  }

  private List<Ambulance> dispatch(NewCall newCall) {
    var availableAmbulances = Utils.filterList(ambulances, Ambulance::isAvailable);

    var supply = availableAmbulances.size();

    if (supply == 0) {
      callQueue.add(newCall);
      return Collections.emptyList();
    }

    var numberOfTransportAmbulances = newCall.getTransportingVehicleDemand();
    var numberOfNonTransportAmbulances = newCall.getNonTransportingVehicleDemand();
    var hospitalLocation = findNearestHospital(newCall.incident);

    // Sort based on proximity
    List<Ambulance> nearestAmbulances = new ArrayList<>(availableAmbulances);
    nearestAmbulances.sort(config.DISPATCH_POLICY().useOn(newCall.incident));

    // Transport ambulances first
    var transportAmbulances =
        nearestAmbulances.subList(0, Math.min(supply, numberOfTransportAmbulances));
    transportAmbulances.forEach(
        ambulance -> ambulance.dispatchTransport(newCall.incident, hospitalLocation));
    var dispatchedAmbulances = new ArrayList<>(transportAmbulances);

    // Remove transport ambulances from the pool
    nearestAmbulances =
        nearestAmbulances.subList(Math.min(supply, numberOfTransportAmbulances), supply);

    // Non-transport ambulances second
    var nonTransportAmbulances =
        nearestAmbulances.subList(
            0, Math.min(supply - transportAmbulances.size(), numberOfNonTransportAmbulances));
    nonTransportAmbulances.forEach((ambulance) -> ambulance.dispatch(newCall.incident));
    dispatchedAmbulances.addAll(nonTransportAmbulances);

    if (transportAmbulances.size() < newCall.getTransportingVehicleDemand()
        || nonTransportAmbulances.size() < newCall.getNonTransportingVehicleDemand()) {

      var partiallyRespondedCall = new PartiallyRespondedCall(newCall);
      partiallyRespondedCall.respondWithTransportingVehicles(transportAmbulances.size());
      partiallyRespondedCall.respondWithNonTransportingVehicles(nonTransportAmbulances.size());
      callQueue.add(partiallyRespondedCall);
    }

    return dispatchedAmbulances;
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

  private void saveResponseTime(NewCall newCall, Integer travelTime) {
    if (newCall.providesResponseTime && newCall.incident.arrivalAtScene().isPresent()) {

      var simulatedDispatchTime =
          (int) ChronoUnit.SECONDS.between(newCall.incident.callReceived(), newCall.getTime());
      var dispatchTime = Math.max(simulatedDispatchTime, newCall.incident.getDispatchDelay());

      var responseTime = dispatchTime + travelTime;
      if (responseTime < 0) {
        throw new IllegalStateException("Response time should never be negative");
      }

      responseTimes.add(newCall.incident.callReceived(), responseTime);
    }
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

  private static Coordinate findNearestHospital(Incident incident) {
    var nearestHospitals = Arrays.asList(Hospital.values());
    nearestHospitals.sort(Hospital.closestTo(incident));

    return nearestHospitals.get(0).getCoordinate();
  }
}
