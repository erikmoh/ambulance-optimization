package no.ntnu.ambulanceallocation.simulation;

import static java.lang.Math.exp;
import static java.lang.Math.pow;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import no.ntnu.ambulanceallocation.simulation.incident.UrgencyLevel;
import no.ntnu.ambulanceallocation.utils.SimulationResult;

public class SimulationResults {

  private final List<SimulationResult> simulationResults = new ArrayList<>();
  private final List<Double> survivalRates = new ArrayList<>();

  public void add(SimulationResult simulationResult) {
    simulationResults.add(simulationResult);
  }

  public List<LocalDateTime> getCallTimes() {
    return simulationResults.stream().map(SimulationResult::callTimestamp).toList();
  }

  public List<Integer> getResponseTimes() {
    return simulationResults.stream().map(SimulationResult::responseTime).toList();
  }

  public List<UrgencyLevel> getUrgencyLevels() {
    return simulationResults.stream().map(SimulationResult::urgencyLevel).toList();
  }

  public double averageResponseTimes() {
    return simulationResults.stream()
        .map(SimulationResult::responseTime)
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
