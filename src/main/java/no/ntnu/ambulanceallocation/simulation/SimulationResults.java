package no.ntnu.ambulanceallocation.simulation;

import static java.lang.Math.exp;
import static java.lang.Math.pow;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import no.ntnu.ambulanceallocation.simulation.incident.UrgencyLevel;
import no.ntnu.ambulanceallocation.utils.SimulatedIncidentResult;

public class SimulationResults {

  private final List<SimulatedIncidentResult> simulatedIncidents = new ArrayList<>();
  private final List<Double> survivalRates = new ArrayList<>();

  public void add(SimulatedIncidentResult simulatedIncidentResult) {
    simulatedIncidents.add(simulatedIncidentResult);
  }

  public List<LocalDateTime> getCallTimes() {
    return simulatedIncidents.stream().map(SimulatedIncidentResult::callTimestamp).toList();
  }

  public List<Integer> getResponseTimes() {
    return simulatedIncidents.stream().map(SimulatedIncidentResult::responseTime).toList();
  }

  public List<UrgencyLevel> getUrgencyLevels() {
    return simulatedIncidents.stream().map(SimulatedIncidentResult::urgencyLevel).toList();
  }

  public double averageResponseTimes() {
    return simulatedIncidents.stream()
        .map(SimulatedIncidentResult::responseTime)
        .mapToLong(Integer::valueOf)
        .average()
        .orElseThrow();
  }

  public List<Double> getSurvivalRates() {
    var responseTimes = getResponseTimes();
    var urgencyLevels = getUrgencyLevels();

    for (int i = 0; i < responseTimes.size(); i++) {
      var r = responseTimes.get(i) / 60.0;
      var u = urgencyLevels.get(i).getCoefficient();
      var survivalRate = pow((1 + exp(-u + 0.1 * r)), -1);
      survivalRates.add(survivalRate);
    }
    return survivalRates;
  }

  public double averageSurvivalRate() {
    if (survivalRates.isEmpty()) {
      getSurvivalRates();
    }
    return survivalRates.stream().mapToDouble(r -> r).average().orElseThrow();
  }
}
