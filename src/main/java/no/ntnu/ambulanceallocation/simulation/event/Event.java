package no.ntnu.ambulanceallocation.simulation.event;

import java.time.LocalDateTime;

public abstract sealed class Event implements Comparable<Event>
    permits NewCall, SceneDeparture, HospitalArrival, LocationUpdate {

  private final LocalDateTime time;
  public final NewCall newCall;

  public Event(LocalDateTime time, NewCall newCall) {
    this.time = time;
    this.newCall = newCall;
  }

  @Override
  public int compareTo(Event otherEvent) {
    return time.compareTo(otherEvent.time);
  }

  public LocalDateTime getTime() {
    return time;
  }

  @Override
  public String toString() {
    return String.format("%sEvent - %s", this.getClass().getSimpleName(), time.toString());
  }
}
