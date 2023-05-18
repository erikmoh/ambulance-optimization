package no.ntnu.ambulanceallocation.simulation.dispatch;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import no.ntnu.ambulanceallocation.simulation.Ambulance;
import no.ntnu.ambulanceallocation.simulation.BaseStation;
import no.ntnu.ambulanceallocation.simulation.Config;
import no.ntnu.ambulanceallocation.simulation.grid.Coordinate;
import no.ntnu.ambulanceallocation.simulation.grid.DistanceIO;
import no.ntnu.ambulanceallocation.simulation.incident.Incident;
import no.ntnu.ambulanceallocation.simulation.incident.IncidentDistribution;
import no.ntnu.ambulanceallocation.simulation.incident.UrgencyLevel;

public enum DispatchPolicy {
  Euclidean {
    @Override
    public void updateAmbulance(
        Ambulance ambulance,
        List<Ambulance> otherAmbulances,
        Incident incident,
        Integer demand,
        Map<BaseStation, List<Ambulance>> baseStationAmbulances,
        LocalDateTime currentTime,
        Config config) {
      ambulance.updateDispatchDelay(incident);

      var time = ambulance.getCurrentLocation().euclideanDistanceTo(incident.getLocation());
      ambulance.setTimeToIncident((int) time);

      if (ambulance.isTransportingPatient()) {
        ambulance.updateTransportingAmbulance(incident);
      }
    }
  },

  Manhattan {
    @Override
    public void updateAmbulance(
        Ambulance ambulance,
        List<Ambulance> otherAmbulances,
        Incident incident,
        Integer demand,
        Map<BaseStation, List<Ambulance>> baseStationAmbulances,
        LocalDateTime currentTime,
        Config config) {
      ambulance.updateDispatchDelay(incident);

      var time = ambulance.getCurrentLocation().manhattanDistanceTo(incident.getLocation());
      ambulance.setTimeToIncident((int) time);

      if (ambulance.isTransportingPatient()) {
        ambulance.updateTransportingAmbulance(incident);
      }
    }
  },

  Fastest {
    @Override
    public void updateAmbulance(
        Ambulance ambulance,
        List<Ambulance> otherAmbulances,
        Incident incident,
        Integer demand,
        Map<BaseStation, List<Ambulance>> baseStationAmbulances,
        LocalDateTime currentTime,
        Config config) {
      ambulance.updateDispatchDelay(incident);

      ambulance.setTimeToIncident(incident);

      if (ambulance.isTransportingPatient()) {
        ambulance.updateTransportingAmbulance(incident);
      }
    }
  },

  CoverageBaseStation {
    @Override
    public void updateAmbulance(
        Ambulance ambulance,
        List<Ambulance> otherAmbulances,
        Incident incident,
        Integer demand,
        Map<BaseStation, List<Ambulance>> baseStationAmbulances,
        LocalDateTime currentTime,
        Config config) {

      int coveragePenaltyImportance = updateAmbulance(ambulance, incident);
      if (coveragePenaltyImportance == 0) {
        return;
      }

      var numAvailable =
          (int)
              baseStationAmbulances.get(ambulance.getBaseStation()).stream()
                  .filter(Ambulance::isAvailable)
                  .count();

      var penalty =
          switch (Math.max(0, numAvailable - demand)) {
            case 0 -> 660;
            case 1 -> 215;
            case 2 -> 30;
            case 3 -> 15;
            default -> 0;
          };

      ambulance.updateCoveragePenalty(penalty * coveragePenaltyImportance);
    }
  },

  CoverageNearby {
    @Override
    public void updateAmbulance(
        Ambulance ambulance,
        List<Ambulance> otherAmbulances,
        Incident incident,
        Integer demand,
        Map<BaseStation, List<Ambulance>> baseStationAmbulances,
        LocalDateTime currentTime,
        Config config) {

      int coveragePenaltyImportance = updateAmbulance(ambulance, incident);
      if (coveragePenaltyImportance == 0) {
        return;
      }

      var hospitalLocation = ambulance.getHospitalLocation();

      var closeAmbulances =
          (int)
              otherAmbulances.stream()
                  .mapToLong(
                      a -> {
                        if (ambulance.isTransportingPatient()) {
                          return a.getCurrentLocation().timeTo(hospitalLocation);
                        }
                        return ambulance.getTimeTo(a);
                      })
                  .filter(distance -> distance < 7.0 * 60)
                  .count();

      var penalty =
          switch (Math.max(0, closeAmbulances - demand)) {
            case 0 -> 660;
            case 1 -> 215;
            case 2 -> 30;
            case 3 -> 15;
            default -> 0;
          };

      ambulance.updateCoveragePenalty(penalty * coveragePenaltyImportance);
    }
  },

  CoveragePredictedDemand {
    @Override
    public void updateAmbulance(
        Ambulance ambulance,
        List<Ambulance> otherAmbulances,
        Incident incident,
        Integer demand,
        Map<BaseStation, List<Ambulance>> baseStationAmbulances,
        LocalDateTime currentTime,
        Config config) {

      int coveragePenaltyImportance = updateAmbulance(ambulance, incident);
      if (coveragePenaltyImportance == 0) {
        return;
      }

      // using the hour when the ambulance will arrive at incident instead of when the ambulance is
      // ready, since arrival time can be approximated using the known travel time
      var arrivalTime = currentTime.plusSeconds(ambulance.getTimeToIncident());

      var areaAmbulanceCount = 0L;
      var predictedDemand = 0.0;

      if (!config.INCIDENT_DISTRIBUTION().equals(IncidentDistribution.GRID)) {
        var baseStation = ambulance.getBaseStation().getId();
        predictedDemand =
            config.INCIDENT_DISTRIBUTION().getPredictedDemand(baseStation, arrivalTime);

        areaAmbulanceCount =
            baseStationAmbulances.get(ambulance.getBaseStation()).stream()
                .filter(Ambulance::isAvailable)
                .count();
      } else {
        var location = ambulance.getCurrentLocation();
        if (ambulance.isTransportingPatient()) {
          // if the ambulance is transporting to hospital, use the location of hospital
          location = ambulance.getHospitalLocation();
        }

        // total demand in neighbourhood
        var neighbours = DistanceIO.getNeighbours(location);
        for (var neighbour : neighbours) {
          var neighbourDemand =
              config.INCIDENT_DISTRIBUTION().getPredictedDemand(neighbour, arrivalTime);
          predictedDemand += neighbourDemand;
        }

        areaAmbulanceCount =
            otherAmbulances.stream()
                .filter(Ambulance::isAvailable)
                .map(a -> new Coordinate(a.getCurrentLocation().id()))
                .filter(neighbours::contains)
                .count();
      }

      var availableAmbulanceCount = Math.max(0L, areaAmbulanceCount - demand);
      var penalty =
          switch ((int) availableAmbulanceCount) {
            case 0 -> 660;
            case 1 -> 215;
            case 2 -> 30;
            case 3 -> 15;
            default -> 0;
          };

      var penaltyFactor = switch (config.INCIDENT_DISTRIBUTION()) {
        // found by testing factors in a range of [0, 200] and picking the one that gave best result
        case PREDICTION -> 26;
        case TRUTH -> 77;
        case default -> 39;
      };
      penalty += predictedDemand * penaltyFactor;

      ambulance.updateCoveragePenalty(penalty * coveragePenaltyImportance);
    }
  };

  static Integer updateAmbulance(Ambulance ambulance, Incident incident) {
    ambulance.updateDispatchDelay(incident);

    ambulance.setTimeToIncident(incident);

    if (ambulance.isTransportingPatient()) {
      ambulance.updateTransportingAmbulance(incident);
    }

    if (incident.urgencyLevel().equals(UrgencyLevel.ACUTE)) {
      ambulance.updateCoveragePenalty(0);
      return 0;
    }

    if (incident.urgencyLevel().isRegular()) {
      // incident is unimportant, avoid dispatching ambulance in potentially busy area
      return 2;
    }

    return 1;
  }

  public abstract void updateAmbulance(
      Ambulance ambulance,
      List<Ambulance> otherAmbulances,
      Incident incident,
      Integer demand,
      Map<BaseStation, List<Ambulance>> baseStationAmbulances,
      LocalDateTime currentTime,
      Config config);
}
