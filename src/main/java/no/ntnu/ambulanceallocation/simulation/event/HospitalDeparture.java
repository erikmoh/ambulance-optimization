package no.ntnu.ambulanceallocation.simulation.event;

import java.time.LocalDateTime;
import no.ntnu.ambulanceallocation.simulation.Ambulance;

public final class HospitalDeparture extends Event {

  public final Ambulance ambulance;

  public HospitalDeparture(LocalDateTime time, Ambulance ambulance, NewCall newCall) {
    super(time, newCall);
    this.ambulance = ambulance;
    newCall.setNextEvent(this);
  }
}
