package no.ntnu.ambulanceallocation.simulation;

import static java.lang.Math.exp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import no.ntnu.ambulanceallocation.simulation.incident.UrgencyLevel;
import no.ntnu.ambulanceallocation.utils.SimulatedIncidentResult;

public class SimulationResults {

  private final List<SimulatedIncidentResult> simulatedIncidents = new ArrayList<>();
  private final List<Double> survivalRates = new ArrayList<>();

  private final Map<String, Double> averageResults = new HashMap<>();

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

  public double averageAcuteResponseTimes() {
    return simulatedIncidents.stream()
        .filter(r -> r.urgencyLevel().equals(UrgencyLevel.ACUTE))
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
      var u1 = urgencyLevels.get(i).getCoefficient1();
      var u2 = urgencyLevels.get(i).getCoefficient2();
      var survivalRate = 1.0 / (1 + exp(-u1 + u2 * r));
      if (urgencyLevels.get(i).equals(UrgencyLevel.ACUTE)) {
        survivalRates.add(survivalRate * 2);
      } else {
        survivalRates.add(survivalRate);
      }
    }
    return survivalRates;
  }

  public double averageSurvivalRate() {
    if (survivalRates.isEmpty()) {
      getSurvivalRates();
    }
    return survivalRates.stream().mapToDouble(r -> r).average().orElseThrow();
  }

  public Map<String, Double> createAverageResults() {
    if (averageResults.isEmpty()) {
      var urgencyLevels = getUrgencyLevels();
      var responseTimes = getResponseTimes();
      var survivalRates = getSurvivalRates();
      var acuteLen = 0;
      var acuteResponse = 0;
      var acuteSurvival = 0.0;
      var urgentLen = 0;
      var urgentResponse = 0;
      var urgentSurvival = 0.0;
      for (int i = 0; i < urgencyLevels.size(); i++) {
        var urgency = urgencyLevels.get(i);
        var responseTime = responseTimes.get(i);
        var survivalRate = survivalRates.get(i);
        if (urgency.equals(UrgencyLevel.ACUTE)) {
          acuteResponse += responseTime;
          acuteSurvival += survivalRate;
          acuteLen++;
        } else {
          urgentResponse += responseTime;
          urgentSurvival += survivalRate;
          urgentLen++;
        }
      }
      averageResults.put("acuteResponse", (double) acuteResponse / acuteLen);
      averageResults.put("acuteSurvival", acuteSurvival / acuteLen);
      averageResults.put("urgentResponse", (double) urgentResponse / urgentLen);
      averageResults.put("urgentSurvival", urgentSurvival / urgentLen);
    }
    return averageResults;
  }
}
