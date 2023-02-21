package no.ntnu.ambulanceallocation.simulation.incident;

import java.time.LocalDateTime;
import java.util.Map;

public class IncidentDistribution {

  private final Map<Integer, Map<Integer, Map<Integer, Double>>> distribution;

  public IncidentDistribution(Map<Integer, Map<Integer, Map<Integer, Double>>> distribution) {
    this.distribution = distribution;
  }

  public Double getHourAverage(LocalDateTime arrivalTime) {
    return distribution
        .get(arrivalTime.getMonth().getValue())
        .get(arrivalTime.getDayOfWeek().getValue())
        .get(arrivalTime.getHour());
  }
}
