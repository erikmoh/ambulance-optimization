package no.ntnu.ambulanceallocation.simulation.event;

import java.time.LocalDateTime;
import no.ntnu.ambulanceallocation.simulation.Ambulance;

public final class HospitalArrival extends Event {

  public final Ambulance ambulance;

  public HospitalArrival(LocalDateTime time, Ambulance ambulance, NewCall newCall) {
    super(time, newCall);
    this.ambulance = ambulance;
    newCall.setNextEvent(this);
  }
}
