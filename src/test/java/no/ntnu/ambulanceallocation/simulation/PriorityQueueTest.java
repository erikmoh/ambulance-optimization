package no.ntnu.ambulanceallocation.simulation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.PriorityQueue;
import no.ntnu.ambulanceallocation.simulation.event.Event;
import no.ntnu.ambulanceallocation.simulation.event.JobCompletion;
import no.ntnu.ambulanceallocation.simulation.event.NewCall;
import no.ntnu.ambulanceallocation.simulation.incident.Incident;
import no.ntnu.ambulanceallocation.simulation.incident.IncidentIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class PriorityQueueTest {

  private static final PriorityQueue<Event> eventQueue = new PriorityQueue<>();
  private static final LocalDateTime startDateTime = LocalDateTime.of(2018, 1, 1, 0, 0, 0);
  private static final List<Incident> incidents = IncidentIO.loadIncidentsFromFile();

  @BeforeAll
  public static void setup() {
    eventQueue.clear();
    eventQueue.addAll(incidents.stream().map(incident -> new NewCall(incident, true)).toList());
  }

  @Test
  public void shouldPollEventsInCorrectOrder() {
    var testResult = true;

    while (eventQueue.size() > 2) {
      var event = eventQueue.poll();
      var nextEvent = eventQueue.poll();

      assert nextEvent != null;
      if (event.getTime().isAfter(nextEvent.getTime())) {
        testResult = false;
        break;
      }
    }

    assertTrue(testResult);
  }

  @Test
  public void shouldPollEventsInCorrectOrderWhenInserted() {
    for (var i = 0; i < 30; i++) {
      eventQueue.add(new JobCompletion(startDateTime.plusSeconds(40 * i + i), null));
    }

    var testResult = true;

    while (eventQueue.size() > 2) {
      var event = eventQueue.poll();
      var nextEvent = eventQueue.poll();

      assert nextEvent != null;
      if (event.getTime().isAfter(nextEvent.getTime())) {
        testResult = false;
        break;
      }
    }

    assertTrue(testResult);
  }
}
