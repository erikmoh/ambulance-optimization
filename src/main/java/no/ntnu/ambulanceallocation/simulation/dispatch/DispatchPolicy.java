package no.ntnu.ambulanceallocation.simulation.dispatch;

import static no.ntnu.ambulanceallocation.Parameters.PREDICTED_DEMAND_BASE_STATION;
import static no.ntnu.ambulanceallocation.simulation.Simulation.baseStationAmbulances;

import java.util.List;
import java.util.Objects;
import no.ntnu.ambulanceallocation.simulation.Ambulance;
import no.ntnu.ambulanceallocation.simulation.grid.Coordinate;
import no.ntnu.ambulanceallocation.simulation.grid.DistanceIO;
import no.ntnu.ambulanceallocation.simulation.incident.Incident;
import no.ntnu.ambulanceallocation.simulation.incident.IncidentIO;
import no.ntnu.ambulanceallocation.simulation.incident.UrgencyLevel;

public enum DispatchPolicy {
  Euclidean {
    @Override
    public void updateAmbulance(
        Ambulance ambulance, List<Ambulance> otherAmbulances, Incident incident) {
      var time = ambulance.getCurrentLocation().euclideanDistanceTo(incident.getLocation());
      ambulance.setTimeToIncident((int) time);

      if (ambulance.isTransportingPatient()) {
        // time from prev incident to hospital + time to this next incident
        ambulance.setTimeToIncident(
            ambulance.getTimeToHospital()
                + ambulance.getHospitalLocation().timeTo(incident.getLocation()));
      }
    }
  },

  Manhattan {
    @Override
    public void updateAmbulance(
        Ambulance ambulance, List<Ambulance> otherAmbulances, Incident incident) {
      var time = ambulance.getCurrentLocation().manhattanDistanceTo(incident.getLocation());
      ambulance.setTimeToIncident((int) time);

      if (ambulance.isTransportingPatient()) {
        // time from prev incident to hospital + time to this next incident
        ambulance.setTimeToIncident(
            ambulance.getTimeToHospital()
                + ambulance.getHospitalLocation().timeTo(incident.getLocation()));
      }
    }
  },

  Fastest {
    @Override
    public void updateAmbulance(
        Ambulance ambulance, List<Ambulance> otherAmbulances, Incident incident) {
      ambulance.setTimeToIncident(incident);

      if (ambulance.isTransportingPatient()) {
        // time from prev incident to hospital + time to this next incident
        ambulance.setTimeToIncident(
            ambulance.getTimeToHospital()
                + ambulance.getHospitalLocation().timeTo(incident.getLocation()));
      }
    }
  },

  CoverageBaseStation {
    @Override
    public void updateAmbulance(
        Ambulance ambulance, List<Ambulance> otherAmbulances, Incident incident) {

      int coveragePenaltyImportance = updateCoverage(ambulance, incident);
      if (coveragePenaltyImportance == 0) {
        return;
      }

      var numAvailable =
          (int)
              baseStationAmbulances.get(ambulance.getBaseStation()).stream()
                  .filter(Ambulance::isAvailable)
                  .count();

      var penalty =
          switch (numAvailable - 1) {
            case 0 -> 600;
            case 1 -> 180;
            default -> 0;
          };

      ambulance.updateCoveragePenalty(penalty * coveragePenaltyImportance);
    }
  },

  CoverageNearby {
    @Override
    public void updateAmbulance(
        Ambulance ambulance, List<Ambulance> otherAmbulances, Incident incident) {

      int coveragePenaltyImportance = updateCoverage(ambulance, incident);
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
          switch (closeAmbulances - 1) {
            case 0 -> 600;
            case 1 -> 180;
            default -> 0;
          };

      ambulance.updateCoveragePenalty(penalty * coveragePenaltyImportance);
    }
  },

  CoveragePredictedDemand {
    @Override
    public void updateAmbulance(
        Ambulance ambulance, List<Ambulance> otherAmbulances, Incident incident) {

      int coveragePenaltyImportance = updateCoverage(ambulance, incident);
      if (coveragePenaltyImportance == 0) {
        return;
      }

      // using the hour when the ambulance will arrive at incident instead of when the ambulance is
      // ready, since arrival time can be approximated using the known travel time
      var arrivalTime = incident.callReceived().plusSeconds(ambulance.getTimeToIncident());

      var location = ambulance.getCurrentLocation();
      if (ambulance.isTransportingPatient()) {
        // if the ambulance is transporting to hospital, use the location of hospital
        location = ambulance.getHospitalLocation();
      }

      var areaAmbulanceCount = 0L;
      var historicAreaDemand = 0.0;

      if (PREDICTED_DEMAND_BASE_STATION) {
        var baseStation = IncidentIO.gridZones.get(location.id());
        var distribution = IncidentIO.distributionsBaseStation.get(baseStation);
        historicAreaDemand = distribution.getHourAverage(arrivalTime);

        if (historicAreaDemand == 0.0) {
          ambulance.updateCoveragePenalty(0);
          return;
        }

        areaAmbulanceCount =
            otherAmbulances.stream()
                .map(a -> IncidentIO.gridZones.get(a.getCurrentLocation().id()))
                .filter(i -> Objects.equals(i, baseStation))
                .count();

      } else {
        // average demand in neighbourhood
        var neighbours = DistanceIO.getNeighbours(location);
        for (var neighbour : neighbours) {
          var distribution = IncidentIO.distributions.get(neighbour);
          var historicAverageDemand = distribution.getHourAverage(arrivalTime);
          historicAreaDemand += historicAverageDemand;
        }

        areaAmbulanceCount =
            otherAmbulances.stream()
                .map(a -> new Coordinate(a.getCurrentLocation().id()))
                .filter(neighbours::contains)
                .count();
      }

      areaAmbulanceCount = Math.max(0L, areaAmbulanceCount - incident.getDemand());
      var uncoveredDemand = Math.max(0.0, historicAreaDemand - areaAmbulanceCount);

      var penalty = uncoveredDemand * 180 * coveragePenaltyImportance;
      ambulance.updateCoveragePenalty((int) penalty);
    }
  };

  private static Integer updateCoverage(Ambulance ambulance, Incident incident) {
    ambulance.setTimeToIncident(incident);

    if (ambulance.isTransportingPatient()) {
      // time from prev incident to hospital + time to this next incident
      ambulance.setTimeToIncident(
          ambulance.getTimeToHospital()
              + ambulance.getHospitalLocation().timeTo(incident.getLocation()));
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
      Ambulance ambulance, List<Ambulance> otherAmbulances, Incident incident);
}
