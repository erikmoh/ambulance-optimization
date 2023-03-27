package no.ntnu.ambulanceallocation.simulation.event;

import java.time.LocalDateTime;
import no.ntnu.ambulanceallocation.simulation.Ambulance;
import no.ntnu.ambulanceallocation.simulation.incident.Incident;

public final class SceneArrival extends Event {

  public final Incident incident;
  public final Ambulance ambulance;
  public final LocalDateTime departureTime;

  public SceneArrival(
      LocalDateTime time, NewCall newCall, Ambulance ambulance, LocalDateTime departureTime) {
    super(time, newCall);
    this.incident = newCall.incident;
    this.ambulance = ambulance;
    this.departureTime = departureTime;
    newCall.setNextEvent(this);
  }
}
