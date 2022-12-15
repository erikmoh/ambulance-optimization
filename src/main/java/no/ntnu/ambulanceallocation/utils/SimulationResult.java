package no.ntnu.ambulanceallocation.utils;

import java.time.LocalDateTime;
import no.ntnu.ambulanceallocation.simulation.incident.UrgencyLevel;

public record SimulationResult(
    LocalDateTime callTimestamp, int responseTime, UrgencyLevel urgencyLevel) {

  @Override
  public String toString() {
    return String.format("(%s, %s, %s)", callTimestamp, responseTime, urgencyLevel);
  }
}
