package no.ntnu.ambulanceallocation.simulation;

import static java.lang.Math.round;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import no.ntnu.ambulanceallocation.simulation.grid.Coordinate;
import no.ntnu.ambulanceallocation.simulation.grid.DistanceIO;
import no.ntnu.ambulanceallocation.simulation.grid.Route;
import no.ntnu.ambulanceallocation.simulation.incident.Incident;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ambulance {

  private static final Logger logger = LoggerFactory.getLogger(Ambulance.class);

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
  private Coordinate currentLocation;

  public Ambulance(BaseStation baseStation) {
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

  public boolean isAvailable() {
    return incident == null && !isOffDuty;
  }

  public boolean isStationary() {
    return currentLocation == destination || route == null;
  }

  public boolean isTransport() {
    return hospitalLocation != null;
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

  public void transport() {
    travelStartTime = currentGlobalTime;
    originatingLocation = currentLocation;
    destination = new Coordinate(hospitalLocation);
    route = DistanceIO.getRoute(currentLocation, destination);
  }

  public void arriveAtHospital() {
    currentLocation = new Coordinate(hospitalLocation);
  }

  // Only used for visualization
  public Coordinate getCurrentLocationVisualized(
      LocalDateTime currentTime, Map<Coordinate, com.sothawo.mapjfx.Coordinate> utmToLatLongMap) {
    if (currentLocation.getIdNum() == 22640006640000L) {
      var s = 0;
    }
    logger.info(
        "{}: Updating pos from current pos: {} {}",
        currentTime,
        currentLocation,
        utmToLatLongMap.get(currentLocation));
    if (isAvailable() || isStationary()) {
      logger.info(
          "{}: Returned current pos {} {}",
          currentTime,
          currentLocation,
          utmToLatLongMap.get(currentLocation));
      return currentLocation;
    }

    if (travelStartTime == null) {
      travelStartTime = currentTime;
      logger.info(
          "{}: Returned current pos {} {}",
          currentTime,
          currentLocation,
          utmToLatLongMap.get(currentLocation));
      return currentLocation;
    }

    var originTimeToDestination = route.time();
    var elapsedTime = (int) ChronoUnit.SECONDS.between(travelStartTime, currentTime);

    if (elapsedTime <= (DistanceIO.getTravelTimeInterval() / 2) * 60) {
      logger.info(
          "{}: Returned current pos {} {}",
          currentTime,
          currentLocation,
          utmToLatLongMap.get(currentLocation));
      return currentLocation;
    }

    if (elapsedTime >= originTimeToDestination) {
      logger.info(
          "{}: Returned destination {} {}",
          currentTime,
          destination,
          utmToLatLongMap.get(destination));
      currentLocation = destination;
      return destination;
    }

    var routeCoordinates = route.routeCoordinates();

    var nextRouteIndex = (int) round((elapsedTime / 60.0) / DistanceIO.getTravelTimeInterval()) - 1;
    if (nextRouteIndex >= routeCoordinates.size()) {
      logger.info(
          "{}: Returned destination {} {}",
          currentTime,
          destination,
          utmToLatLongMap.get(destination));
      currentLocation = destination;
      return destination;
    }
    var nextLocationId = routeCoordinates.get(nextRouteIndex);
    currentLocation = new Coordinate(Long.parseLong(nextLocationId));
    logger.info(
        "{}: Returned new pos {} {}",
        currentTime,
        currentLocation,
        utmToLatLongMap.get(currentLocation));
    return currentLocation;
  }

  public void updateLocation(int timePeriod) {
    if (currentLocation == destination) {
      return;
    }

    if (timePeriod == DistanceIO.getTravelTimeInterval()) {
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
      var originTimeToDestination = route.time();
      var previousTimeToDestination = currentLocation.timeTo(destination);
      var elapsedTime = previousTimeToDestination + timePeriod;

      if (elapsedTime <= (timePeriod / 2.0) * 60) {
        return;
      }

      if (elapsedTime >= originTimeToDestination) {
        currentLocation = destination;
        return;
      }

      var routeCoordinates = route.routeCoordinates();

      var nextRouteIndex =
          (int) round((elapsedTime / 60.0) / DistanceIO.getTravelTimeInterval()) - 1;
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
    return round(this.currentLocation.timeTo(incident.getLocation()));
  }

  @Override
  public String toString() {
    return String.format(
        "Ambulance[baseStation=%s, destination=%s, currentLocation=%s, hospitalLocation=%s]",
        baseStation, destination, currentLocation, hospitalLocation);
  }
}
