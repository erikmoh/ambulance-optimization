package no.ntnu.ambulanceallocation.simulation.dispatch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import no.ntnu.ambulanceallocation.simulation.Ambulance;
import no.ntnu.ambulanceallocation.simulation.incident.Incident;
import no.ntnu.ambulanceallocation.simulation.incident.IncidentIO;
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
      if (incident.urgencyLevel().isRegular()) {
        return medianDispatchTimeMap.get(UrgencyLevel.REGULAR);
      }
      return medianDispatchTimeMap.get(incident.urgencyLevel());
    }
  };

  private static final Map<UrgencyLevel, Integer> medianDispatchTimeMap =
      createMedianDispatchTime();

  public static Map<UrgencyLevel, Integer> createMedianDispatchTime() {
    var map = new HashMap<UrgencyLevel, Integer>();

    for (var i = 0; i < UrgencyLevel.values().length - 2; i++) {
      var level = UrgencyLevel.values()[i];
      var levels = new java.util.ArrayList<>(List.of(level));
      if (level.equals(UrgencyLevel.REGULAR)) {
        levels.add(UrgencyLevel.REGULAR_PLANNED);
        levels.add(UrgencyLevel.REGULAR_UNPLANNED);
      }
      var sortedIncidents =
          IncidentIO.incidents.stream()
              .filter(
                  incident ->
                      levels.contains(incident.urgencyLevel())
                          // filter short times (ambulance was probably not idle at base station)
                          && incident.getDispatchDelay() > 60
                          // filter long times (unlikely that it took that long to leave)
                          && incident.getDispatchDelay() < 60 * 15)
              .map(Incident::getDispatchDelay)
              .sorted()
              .toList();
      var size = sortedIncidents.size();
      var midpoint = size % 2 == 0 ? size / 2 - 1 : size / 2;
      map.put(level, sortedIncidents.get(midpoint));
    }

    return map;
  }

  public abstract int get(Incident incident, Ambulance ambulance);
}
