package no.ntnu.ambulanceallocation.simulation.dispatch;

import no.ntnu.ambulanceallocation.simulation.Ambulance;
import no.ntnu.ambulanceallocation.simulation.incident.Incident;
import no.ntnu.ambulanceallocation.simulation.incident.UrgencyLevel;

/***
 * Time from when an idle ambulance is notified of the incident until it leaves its base station.
 ***/
public enum DispatchDelay {
  SIMULATED {
    @Override
    public int get(Incident incident, Ambulance ambulance) {
      return 0;
    }
  },
  HISTORIC {
    @Override
    public int get(Incident incident, Ambulance ambulance) {
      return incident.getDispatchDelay();
    }
  },
  HISTORIC_MEDIAN {
    @Override
    public int get(Incident incident, Ambulance ambulance) {
      if (!ambulance.isAtBaseStation() || ambulance.getIncident() != null) {
        // assume some time from the ambulance is called until it starts to move to incident
        return 60;
      }
      // median times are found using scripts
      if (incident.urgencyLevel().isRegular()) {
        return 209;
      }
      if (incident.urgencyLevel().equals(UrgencyLevel.URGENT)) {
        return 122;
      }
      return 88;
    }
  };

  public abstract int get(Incident incident, Ambulance ambulance);
}
