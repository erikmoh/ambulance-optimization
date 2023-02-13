package no.ntnu.ambulanceallocation.simulation;

import no.ntnu.ambulanceallocation.simulation.incident.Incident;
import no.ntnu.ambulanceallocation.simulation.incident.IncidentIO;

public enum DispatchDelay {
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
      return medianDispatchTime;
    }
  };

  private static final int medianDispatchTime = getMedianDispatchTime();

  private static int getMedianDispatchTime() {
    var sortedIncidents = IncidentIO.incidents.stream().map(Incident::getDispatchDelay).sorted().toList();
    var size = sortedIncidents.size();
    if (size % 2 == 0) {
      return sortedIncidents.get(size/2 - 1);
    }
    return sortedIncidents.get(size/2);
  }

  public abstract int get(Incident incident);
}
