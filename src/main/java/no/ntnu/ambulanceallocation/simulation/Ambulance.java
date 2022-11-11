package no.ntnu.ambulanceallocation.simulation;

import static java.lang.Math.round;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import no.ntnu.ambulanceallocation.simulation.grid.Coordinate;
import no.ntnu.ambulanceallocation.simulation.grid.DistanceIO;
import no.ntnu.ambulanceallocation.simulation.grid.Route;
import no.ntnu.ambulanceallocation.simulation.incident.Incident;

public class Ambulance {

  private static final double CONVERSION_FACTOR = 3.6;

  // Only used for visualization
  private static LocalDateTime currentGlobalTime;
  private final BaseStation baseStation;
  private boolean isOffDuty = true;
  private Coordinate hospitalLocation = null;
  private Incident incident;

  private LocalDateTime travelStartTime;
  private Coordinate originatingLocation;
  private Route route;
  private Coordinate destination = null;
  private int timeToDestination = 0;
  private Coordinate currentLocation;

  public Ambulance(BaseStation baseStation) {
    this.baseStation = baseStation;
    this.currentLocation = baseStation.getCoordinate();
  }

  public static List<Ambulance> generateFromAllocation(Collection<Integer> allocation) {
    return allocation.stream()
        .map(id -> new Ambulance(BaseStation.get(id)))
        .collect(Collectors.toList());
  }

  public static void setCurrentGlobalTime(LocalDateTime time) {
    currentGlobalTime = time;
  }

  public static Comparator<Ambulance> closestTo(Incident incident) {
    return Comparator.comparingDouble(ambulance -> ambulance.timeTo(incident));
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

  public boolean isAvailable() {
    return incident == null && !isOffDuty;
  }

  public boolean isTransport() {
    return hospitalLocation != null;
  }

  public boolean isNonTransport() {
    return !isTransport();
  }

  public void flagAsAvailable() {
    incident = null;
    hospitalLocation = null;
    destination = baseStation.getCoordinate();
    travelStartTime = currentGlobalTime;
    originatingLocation = currentLocation;
    route = DistanceIO.getRoute(originatingLocation, destination);
  }

  public void dispatch(Incident incident) {
    this.incident = incident;
    travelStartTime = currentGlobalTime;
    originatingLocation = currentLocation;
    destination = new Coordinate(incident.getLocation());
    route = DistanceIO.getRoute(currentLocation, destination);
  }

  public void dispatchTransport(Incident incident, Coordinate hospitalLocation) {
    dispatch(incident);
    this.hospitalLocation = hospitalLocation;
  }

  // Only used for visualization
  public Coordinate getCurrentLocationVisualized(LocalDateTime currentTime) {
    if (isAvailable() || currentLocation == destination || route == null) {
      return currentLocation;
    }

    if (travelStartTime == null) {
      travelStartTime = currentTime;
    }

    var timePeriod = 5;
    var originTimeToDestination = route.time();
    var elapsedTime = (int) ChronoUnit.SECONDS.between(travelStartTime, currentTime);

    if (elapsedTime >= originTimeToDestination) {
      currentLocation = destination;
      return destination;
    }

    if (timePeriod == 5) {
      var routeCoordinates = route.routeCoordinates();

      var nextRouteIndex = (int) round(elapsedTime * 60.0 / 5);
      if (nextRouteIndex >= routeCoordinates.size()) {
        return destination;
      }
      var nextLocationId = routeCoordinates.get(nextRouteIndex);
      return new Coordinate(Long.parseLong(nextLocationId));
    }

    var timeDelta = originTimeToDestination - elapsedTime;
    var closeNeighbor =
        currentLocation.getNeighbors().stream()
            .min(
                Comparator.comparingInt(
                    c -> Math.abs(c.getNearbyAverageTravelTimeTo(destination) - timeDelta)))
            .orElseThrow();

    if (Math.abs(closeNeighbor.getNearbyAverageTravelTimeTo(destination) - timeDelta)
        < Math.abs(currentLocation.timeTo(destination) - timeDelta)) {
      return closeNeighbor;
    }
    return currentLocation;
  }

  public void updateLocation(int timePeriod) {
    if (currentLocation == destination) {
      return;
    }

    if (timePeriod == 5) {
      var routeCoordinates = route.routeCoordinates();
      var currentLocationId = String.valueOf(currentLocation.getIdNum());
      var routeIndex = routeCoordinates.indexOf(currentLocationId);

      if (routeIndex + 1 >= routeCoordinates.size()) {
        currentLocation = destination;
        return;
      }

      var nextLocationId = routeCoordinates.get(routeIndex + 1);
      currentLocation = new Coordinate(Long.parseLong(nextLocationId));

    } else {
      var previousTimeToDestination = currentLocation.timeTo(destination);

      var closeNeighbor =
          currentLocation.getNeighbors().stream()
              .min(
                  Comparator.comparingInt(c -> Math.abs(c.timeTo(destination) - timeToDestination)))
              .orElseThrow();

      if (Math.abs(closeNeighbor.timeTo(destination) - timeToDestination)
          < Math.abs(previousTimeToDestination - timeToDestination)) {
        currentLocation = closeNeighbor;
      }
    }
  }

  public boolean endOfJourney() {
    return currentLocation.equals(destination);
  }

  public void transport() {
    travelStartTime = currentGlobalTime;
    originatingLocation = currentLocation;
    destination = new Coordinate(hospitalLocation);
  }

  public void arriveAtHospital() {
    currentLocation = new Coordinate(hospitalLocation);
  }

  public int timeTo(Incident incident) {
    return round(this.currentLocation.timeTo(incident.getLocation()));
  }

  @Override
  public String toString() {
    return String.format(
        "Ambulance[baseStation=%s, destination=%s, currentLocation=%s, hospitalLocation=%s]",
        baseStation, destination, currentLocation, hospitalLocation);
  }
}
