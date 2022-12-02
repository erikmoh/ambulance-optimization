package no.ntnu.ambulanceallocation.simulation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import no.ntnu.ambulanceallocation.simulation.incident.UrgencyLevel;
import no.ntnu.ambulanceallocation.utils.ResponseTime;

public class ResponseTimes {

  private final List<ResponseTime> responseTimes = new ArrayList<>();

  public void add(ResponseTime responseTime) {
    responseTimes.add(responseTime);
  }

  public List<LocalDateTime> getCallTimes() {
    return responseTimes.stream().map(ResponseTime::callTimestamp).toList();
  }

  public List<Integer> getResponseTimes() {
    return responseTimes.stream().map(ResponseTime::responseTime).toList();
  }

  public List<UrgencyLevel> getUrgencyLevels() {
    return responseTimes.stream().map(ResponseTime::urgencyLevel).toList();
  }

  public double average() {
    return responseTimes.stream()
        .map(ResponseTime::responseTime)
        .mapToLong(Integer::valueOf)
        .average()
        .orElseThrow();
  }
}
