package no.ntnu.ambulanceallocation.simulation;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import no.ntnu.ambulanceallocation.simulation.event.NewCall;
import no.ntnu.ambulanceallocation.simulation.grid.Coordinate;
import no.ntnu.ambulanceallocation.simulation.grid.DistanceIO;
import no.ntnu.ambulanceallocation.simulation.grid.Route;
import no.ntnu.ambulanceallocation.simulation.incident.Incident;
import no.ntnu.ambulanceallocation.simulation.incident.UrgencyLevel;

public class Ambulance {

  // Only used for visualization
  private static LocalDateTime currentGlobalTime;
  private final BaseStation baseStation;
  private boolean isOffDuty = true;
  private Coordinate hospitalLocation = null;
  private Incident incident;

  @SuppressWarnings({"unused", "FieldCanBeLocal"})
  private final int id; // for testing only, can be removed

  private LocalDateTime travelStartTime;
  private Coordinate originatingLocation;
  private Route route;
  private Coordinate destination = null;
  private Coordinate currentLocation;
  private NewCall call;
  private NextCall nextCall;
  private int currentRouteIndex;

  private int timeToIncident;
  private int coveragePenalty = 0;

  public Ambulance(BaseStation baseStation, int id) {
    this.id = id;
    this.baseStation = baseStation;
    this.currentLocation = baseStation.getCoordinate();
  }

  public static void setCurrentGlobalTime(LocalDateTime time) {
    currentGlobalTime = time;
  }

  public void startNewShift() {
    isOffDuty = false;
  }

  public void finishShift() {
    isOffDuty = true;
  }

  public boolean isOffDuty() {
    return isOffDuty;
  }

  public boolean isAtBaseStation() {
    return currentLocation.equals(baseStation.getCoordinate());
  }

  public BaseStation getBaseStation() {
    return baseStation;
  }

  public Coordinate getBaseStationLocation() {
    return baseStation.getCoordinate();
  }

  public Coordinate getHospitalLocation() {
    return hospitalLocation;
  }

  public Coordinate getDestination() {
    return destination;
  }

  public Coordinate getCurrentLocation() {
    return currentLocation;
  }

  public Incident getIncident() {
    return incident;
  }

  public NewCall getCall() {
    return call;
  }

  public void setCall(NewCall newCall) {
    call = newCall;
  }

  public boolean isAvailable() {
    return incident == null && !isOffDuty;
  }

  public boolean canBeReassigned() {
    return !isOffDuty
        && incident != null
        && incident.urgencyLevel() != UrgencyLevel.ACUTE
        // not at scene
        && !currentLocation.equals(incident.getLocation())
        // not transporting to hospital
        && !destination.equals(hospitalLocation)
        // only one dispatched ambulance
        // (easier to reassign, could implement reassign solution for multiple ambulances)
        && incident.nonTransportingVehicles() + incident.transportingVehicles() == 1;
  }

  public boolean canBeQueued() {
    return !isOffDuty
        && incident != null
        // transporting to hospital
        && destination.equals(hospitalLocation)
        // cannot already have a next call
        && nextCall == null;
  }

  public boolean isStationary() {
    return currentLocation.equals(destination) || route == null;
  }

  public boolean isTransport() {
    return hospitalLocation != null;
  }

  public void flagAsAvailable() {
    incident = null;
    call = null;
    hospitalLocation = null;
    travelStartTime = currentGlobalTime;
    originatingLocation = currentLocation;
    destination = baseStation.getCoordinate();
    route = DistanceIO.getRoute(originatingLocation, destination);
    currentRouteIndex = 0;
  }

  public void dispatch(NewCall newCall, Coordinate hospital, boolean isNextCall) {
    if (isNextCall) {
      nextCall = new NextCall(newCall, hospital);
      return;
    }
    incident = newCall.incident;
    travelStartTime = currentGlobalTime;
    originatingLocation = currentLocation;
    destination = new Coordinate(incident.getLocation());
    hospitalLocation = hospital;
    route = DistanceIO.getRoute(currentLocation, destination);
    currentRouteIndex = 0;
  }

  public void dispatchNextCall() {
    if (nextCall != null) {
      dispatch(nextCall.newCall, nextCall.hospitalLocation, false);
    }
    nextCall = null;
  }

  public void transport() {
    travelStartTime = currentGlobalTime;
    originatingLocation = currentLocation;
    destination = new Coordinate(hospitalLocation);
    route = DistanceIO.getRoute(currentLocation, destination);
    currentRouteIndex = 0;
  }

  public void arriveAtHospital() {
    if (hospitalLocation == null) {
      throw new IllegalStateException("Cannot arrive at hospital when it is null");
    }
    currentLocation = new Coordinate(hospitalLocation);
  }

  // Only used for visualization
  public Coordinate getCurrentLocationVisualized(LocalDateTime currentTime) {
    if (isAvailable() || isStationary()) {
      return currentLocation;
    }

    if (travelStartTime == null) {
      travelStartTime = currentTime;
      return currentLocation;
    }

    var elapsedTime = (int) ChronoUnit.SECONDS.between(travelStartTime, currentTime);

    if (elapsedTime >= route.time()) {
      currentLocation = destination;
      return destination;
    }

    var halfway = (DistanceIO.getTravelTimeInterval() / 2.0) * 60;
    if (elapsedTime <= halfway) {
      return currentLocation;
    }

    var routeCoordinates = route.routeCoordinates();

    var nextRouteIndex =
        (int) Math.round((elapsedTime / 60.0) / DistanceIO.getTravelTimeInterval()) - 1;
    if (nextRouteIndex >= routeCoordinates.size()) {
      currentLocation = destination;
      return destination;
    }
    var nextLocationId = routeCoordinates.get(nextRouteIndex);
    currentLocation = new Coordinate(Long.parseLong(nextLocationId));
    return currentLocation;
  }

  public void updateLocation(int timePeriod) {
    if (currentLocation.equals(destination)) {
      return;
    }

    if (timePeriod == DistanceIO.getTravelTimeInterval()) {
      var routeCoordinates = route.routeCoordinates();

      currentRouteIndex++;

      if (currentRouteIndex >= routeCoordinates.size()) {
        currentLocation = destination;
        return;
      }

      var nextLocationId = routeCoordinates.get(currentRouteIndex);
      currentLocation = new Coordinate(Long.parseLong(nextLocationId));

    } else {
      var originTimeToDestination = route.time();
      var previousTimeToDestination = currentLocation.timeTo(destination);
      var elapsedTime = originTimeToDestination - previousTimeToDestination + (timePeriod * 60);

      if (elapsedTime >= originTimeToDestination) {
        currentLocation = destination;
        return;
      }

      if (elapsedTime <= (DistanceIO.getTravelTimeInterval() / 2.0) * 60) {
        return;
      }

      var routeCoordinates = route.routeCoordinates();

      var nextRouteIndex =
          (int) Math.round((elapsedTime / 60.0) / DistanceIO.getTravelTimeInterval()) - 1;
      if (nextRouteIndex >= routeCoordinates.size()) {
        currentLocation = destination;
        return;
      }
      var nextLocationId = routeCoordinates.get(nextRouteIndex);
      currentLocation = new Coordinate(Long.parseLong(nextLocationId));
    }
  }

  public boolean endOfJourney() {
    return currentLocation.equals(destination);
  }

  public int timeTo(Incident incident) {
    return Math.round(this.currentLocation.timeTo(incident.getLocation()));
  }

  public int timeTo(Ambulance other) {
    return Math.round(this.currentLocation.timeTo(other.getCurrentLocation()));
  }

  public void updateTimeTo(Incident incident) {
    timeToIncident = timeTo(incident);
  }

  public void updateTimeTo(int time) {
    timeToIncident = time;
  }

  public int getTimeToIncident() {
    return timeToIncident;
  }

  public int getTimeToHospital() {
    return currentLocation.timeTo(hospitalLocation);
  }

  public void updateCoveragePenalty(int penalty) {
    coveragePenalty = penalty;
  }

  public int getCoveragePenalty() {
    return coveragePenalty;
  }

  @Override
  public String toString() {
    return String.format(
        "Ambulance[baseStation=%s, destination=%s, currentLocation=%s, hospitalLocation=%s]",
        baseStation, destination, currentLocation, hospitalLocation);
  }

  private record NextCall(NewCall newCall, Coordinate hospitalLocation) {}
}
