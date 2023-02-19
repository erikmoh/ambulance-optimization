package no.ntnu.ambulanceallocation.simulation.incident;

import java.util.Map;

public class IncidentDistribution {

  private final Map<Integer, Map<Integer, Map<Integer, Double>>> distribution;

  public IncidentDistribution(Map<Integer, Map<Integer, Map<Integer, Double>>> distribution) {
    this.distribution = distribution;
  }

  public Double getHourAverage(Integer month, Integer weekday, Integer hour) {
    return distribution.get(month).get(weekday).get(hour);
  }
}
