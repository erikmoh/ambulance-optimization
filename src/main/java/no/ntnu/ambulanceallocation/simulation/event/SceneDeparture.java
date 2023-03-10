package no.ntnu.ambulanceallocation.simulation.event;

import java.time.LocalDateTime;
import java.util.List;
import no.ntnu.ambulanceallocation.simulation.Ambulance;
import no.ntnu.ambulanceallocation.simulation.incident.Incident;

public final class SceneDeparture extends Event {

  public final Incident incident;
  private final List<Ambulance> ambulances;

  public SceneDeparture(LocalDateTime time, NewCall newCall, List<Ambulance> ambulances) {
    super(time, newCall);
    this.incident = newCall.incident;
    this.ambulances = ambulances;
  }

  public List<Ambulance> getAmbulances() {
    return ambulances;
  }
}
