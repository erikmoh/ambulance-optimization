package no.ntnu.ambulanceallocation.simulation.incident;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import no.ntnu.ambulanceallocation.simulation.grid.Coordinate;

public record Incident(
    LocalDateTime callReceived,
    int xCoordinate,
    int yCoordinate,
    UrgencyLevel urgencyLevel,
    LocalDateTime dispatched,
    Optional<LocalDateTime> arrivalAtScene,
    Optional<LocalDateTime> departureFromScene,
    LocalDateTime availableNonTransport,
    LocalDateTime availableTransport,
    int nonTransportingVehicles,
    int transportingVehicles) {

  public Coordinate getLocation() {
    return new Coordinate(xCoordinate, yCoordinate);
  }

  public int getDispatchDelay() {
    return (int) ChronoUnit.SECONDS.between(callReceived, dispatched);
  }

  public int getTimeSpentAtScene() {
    if (arrivalAtScene.isEmpty() || departureFromScene.isEmpty()) {
      throw new IllegalStateException(
          "Cannot compute time spent at scene without arrival and departure time");
    }
    return (int) ChronoUnit.SECONDS.between(arrivalAtScene.get(), departureFromScene.get());
  }

  public int getTimeSpentAtSceneNonTransport() {
    if (arrivalAtScene.isEmpty()) {
      throw new IllegalStateException("Cannot compute time spent at scene without arrival time");
    }
    if (arrivalAtScene.get().isAfter(availableNonTransport)) {
      // assume that the availableNonTransport is wrong, but it could also be an aborted incident
      return (int) ChronoUnit.SECONDS.between(arrivalAtScene.get(), availableTransport);
    }
    return (int) ChronoUnit.SECONDS.between(arrivalAtScene.get(), availableNonTransport);
  }

  public int getTimeBeforeAborting() {
    if (dispatched.isAfter(availableNonTransport)) {
      throw new IllegalStateException("Dispatch time cannot be after available non-transport time");
    }
    return (int) ChronoUnit.SECONDS.between(dispatched, availableNonTransport);
  }

  public int getTimeFromDepartureToAvailableTransport() {
    if (departureFromScene.isEmpty()) {
      throw new IllegalStateException("Cannot compute duration without departure time");
    }
    return (int) ChronoUnit.SECONDS.between(departureFromScene.get(), availableTransport);
  }
}
