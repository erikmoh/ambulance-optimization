package no.ntnu.ambulanceallocation.simulation;

import no.ntnu.ambulanceallocation.simulation.event.NewCall;
import no.ntnu.ambulanceallocation.simulation.grid.Coordinate;
import no.ntnu.ambulanceallocation.simulation.grid.DistanceIO;
import no.ntnu.ambulanceallocation.simulation.grid.Route;
import no.ntnu.ambulanceallocation.simulation.incident.Incident;
import no.ntnu.ambulanceallocation.simulation.incident.UrgencyLevel;

public class Ambulance {

  private final BaseStation baseStation;
  private boolean isOffDuty = true;
  private Coordinate hospitalLocation = null;
  private Incident incident;

  // only used for debugging
  public final int id;
  public boolean reassigned = false;

  private Coordinate originatingLocation;
  private Route route;
  private Coordinate destination = null;
  private Coordinate currentLocation;
  private NewCall call;
  private NextCall nextCall;
  private int currentRouteIndex;
  private boolean transportingPatient = false;

  private int timeToIncident;
  private int coveragePenalty = 0;

  public Ambulance(BaseStation baseStation, int id) {
    this.id = id;
    this.baseStation = baseStation;
    this.currentLocation = baseStation.getCoordinate();
  }

  public BaseStation getBaseStation() {
    return baseStation;
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

  public int getTimeToIncident() {
    return timeToIncident;
  }

  public int getTimeToHospital() {
    return currentLocation.timeTo(hospitalLocation);
  }

  public int getDispatchScore() {
    return timeToIncident + coveragePenalty;
  }

  public int getTimeTo(Incident incident) {
    return Math.round(currentLocation.timeTo(incident.getLocation()));
  }

  public int getTimeTo(Ambulance other) {
    return Math.round(currentLocation.timeTo(other.getCurrentLocation()));
  }

  public void startNewShift() {
    isOffDuty = false;
  }

  public void finishShift() {
    isOffDuty = true;
  }

  public void setCall(NewCall newCall) {
    call = newCall;
  }

  public void setTimeToIncident(Incident incident) {
    timeToIncident = getTimeTo(incident);
  }

  public void setTimeToIncident(int time) {
    timeToIncident = time;
  }

  public void updateCoveragePenalty(int penalty) {
    coveragePenalty = penalty;
  }

  public void setReassigned(boolean status) {
    reassigned = status;
  }

  public boolean isOffDuty() {
    return isOffDuty;
  }

  public boolean isAtBaseStation() {
    return currentLocation.equals(baseStation.getCoordinate());
  }

  public boolean isArrived() {
    return currentLocation.equals(destination);
  }

  public boolean isAvailable() {
    return incident == null && !isOffDuty;
  }

  public boolean isTransport() {
    return hospitalLocation != null;
  }

  public boolean isTransportingPatient() {
    return transportingPatient;
  }

  public boolean isReassigned() {
    return reassigned;
  }

  public boolean canBeReassigned(Incident newIncident) {
    return !isOffDuty
        && incident != null
        && incident.urgencyLevel() != UrgencyLevel.ACUTE
        // only reassign if new incident is more urgent
        && incident.urgencyLevel() != newIncident.urgencyLevel()
        // not at scene
        && !currentLocation.equals(incident.getLocation())
        // not transporting a patient
        && !transportingPatient
        // only one dispatched ambulance
        // (easier to reassign, could implement reassign solution for multiple ambulances)
        && incident.nonTransportingVehicles() + incident.transportingVehicles() == 1;
  }

  public boolean canBeQueued() {
    return !isOffDuty
        && incident != null
        // transporting to hospital
        && transportingPatient
        // cannot already have a next call
        && nextCall == null;
  }

  public void flagAsAvailable() {
    incident = null;
    call = null;
    hospitalLocation = null;
    originatingLocation = currentLocation;
    destination = baseStation.getCoordinate();
    route = DistanceIO.getRoute(originatingLocation, destination);
    currentRouteIndex = 0;
    reassigned = false;
    transportingPatient = false;
  }

  public void dispatch(NewCall newCall, Coordinate hospital) {
    if (canBeQueued()) {
      nextCall = new NextCall(newCall, hospital);
      return;
    }
    incident = newCall.incident;
    originatingLocation = currentLocation;
    destination = new Coordinate(incident.getLocation());
    hospitalLocation = hospital;
    route = DistanceIO.getRoute(currentLocation, destination);
    currentRouteIndex = 0;
  }

  public void dispatchNextCall() {
    if (nextCall != null) {
      dispatch(nextCall.newCall, nextCall.hospitalLocation);
      call = nextCall.newCall;
      nextCall = null;
    }
  }

  public void transport() {
    transportingPatient = true;
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

  public void updateLocation(int timePeriod) {
    if (currentLocation.equals(destination)) {
      return;
    }

    if (timePeriod == DistanceIO.getTravelTimeInterval()) {
      currentRouteIndex++;
      currentLocation = getNewLocation();
    } else {
      currentLocation = getNewLocation(timePeriod);
    }
  }

  private Coordinate getNewLocation() {
    var routeCoordinates = route.routeCoordinates();

    if (currentRouteIndex >= routeCoordinates.size()) {
      return destination;
    }

    var nextLocationId = routeCoordinates.get(currentRouteIndex);
    return new Coordinate(Long.parseLong(nextLocationId));
  }

  private Coordinate getNewLocation(int timePeriod) {
    var originTimeToDestination = route.time();
    var previousTimeToDestination = currentLocation.timeTo(destination);
    var elapsedTime = originTimeToDestination - previousTimeToDestination + (timePeriod * 60);

    if (elapsedTime >= originTimeToDestination) {
      return destination;
    }

    if (elapsedTime <= (DistanceIO.getTravelTimeInterval() / 2.0) * 60) {
      return currentLocation;
    }

    var routeCoordinates = route.routeCoordinates();

    var nextRouteIndex =
        (int) Math.round((elapsedTime / 60.0) / DistanceIO.getTravelTimeInterval()) - 1;
    if (nextRouteIndex >= routeCoordinates.size()) {
      return destination;
    }
    var nextLocationId = routeCoordinates.get(nextRouteIndex);
    return new Coordinate(Long.parseLong(nextLocationId));
  }

  @Override
  public String toString() {
    return String.format(
        "Ambulance[baseStation=%s, destination=%s, currentLocation=%s, hospitalLocation=%s]",
        baseStation, destination, currentLocation, hospitalLocation);
  }

  private record NextCall(NewCall newCall, Coordinate hospitalLocation) {}
}
