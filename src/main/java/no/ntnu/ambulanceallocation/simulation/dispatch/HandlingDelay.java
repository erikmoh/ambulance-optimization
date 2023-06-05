package no.ntnu.ambulanceallocation.simulation.dispatch;

import no.ntnu.ambulanceallocation.simulation.incident.Incident;
import no.ntnu.ambulanceallocation.simulation.incident.UrgencyLevel;

/***
 * Time from when an idle ambulance is notified of the incident until it leaves its base station.
 ***/
public enum HandlingDelay {
  SIMULATED {
    @Override
    public int get(Incident incident) {
      return 0;
    }
  },
  HISTORIC {
    @Override
    public int get(Incident incident) {
      return incident.getDispatchDelay();
    }
  },
  HISTORIC_MEDIAN {
    @Override
    public int get(Incident incident) {
      if (incident.urgencyLevel().isRegular()) {
        // assume no handling time for regular events
        // this is not accurate, but it's not detrimental since regular response times are not saved
        return 0;
      }
      // median times found using scripts
      if (incident.urgencyLevel().equals(UrgencyLevel.URGENT)) {
        return 365;
      }
      return 125;
    }
  };

  public abstract int get(Incident incident);
}
