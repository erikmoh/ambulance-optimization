package no.ntnu.ambulanceallocation.simulation;

import static java.lang.Math.max;

import java.util.List;
import no.ntnu.ambulanceallocation.simulation.grid.Coordinate;
import no.ntnu.ambulanceallocation.simulation.grid.DistanceIO;
import no.ntnu.ambulanceallocation.simulation.incident.Incident;
import no.ntnu.ambulanceallocation.simulation.incident.IncidentIO;
import no.ntnu.ambulanceallocation.simulation.incident.UrgencyLevel;

public enum DispatchPolicy {
  Euclidean {
    @Override
    public void updateAmbulance(
        Ambulance ambulance, List<Ambulance> baseStationAmbulances, Incident incident) {
      var time = ambulance.getCurrentLocation().euclideanDistanceTo(incident.getLocation());
      ambulance.updateTimeTo((int) time);
    }
  },

  Manhattan {
    @Override
    public void updateAmbulance(
        Ambulance ambulance, List<Ambulance> baseStationAmbulances, Incident incident) {
      var time = ambulance.getCurrentLocation().manhattanDistanceTo(incident.getLocation());
      ambulance.updateTimeTo((int) time);
    }
  },

  Fastest {
    @Override
    public void updateAmbulance(
        Ambulance ambulance, List<Ambulance> baseStationAmbulances, Incident incident) {
      ambulance.updateTimeTo(incident);
    }
  },

  CoverageBaseStation {
    @Override
    public void updateAmbulance(
        Ambulance ambulance, List<Ambulance> baseStationAmbulances, Incident incident) {

      ambulance.updateTimeTo(incident);

      if (incident.urgencyLevel().equals(UrgencyLevel.ACUTE)) {
        ambulance.updateCoveragePenalty(0);
        return;
      }

      var numAvailable =
          (int) baseStationAmbulances.stream().filter(Ambulance::isAvailable).count();

      var penalty =
          switch (numAvailable - 1) {
            case 0 -> 600;
            case 1 -> 180;
            default -> 0;
          };

      ambulance.updateCoveragePenalty(penalty);
    }
  },

  CoverageNearby {
    @Override
    public void updateAmbulance(
        Ambulance ambulance, List<Ambulance> availableAmbulances, Incident incident) {

      ambulance.updateTimeTo(incident);

      if (incident.urgencyLevel().equals(UrgencyLevel.ACUTE)) {
        ambulance.updateCoveragePenalty(0);
        return;
      }

      var closeAmbulances =
          (int)
              availableAmbulances.stream()
                  .mapToLong(ambulance::timeTo)
                  .filter(distance -> distance < 7.0 * 60)
                  .count();

      var penalty =
          switch (closeAmbulances - 1) {
            case 0 -> 600;
            case 1 -> 180;
            default -> 0;
          };

      ambulance.updateCoveragePenalty(penalty);
    }
  },

  CoveragePredictedDemand {
    @Override
    public void updateAmbulance(
        Ambulance ambulance, List<Ambulance> availableAmbulances, Incident incident) {

      ambulance.updateTimeTo(incident);

      if (incident.urgencyLevel().equals(UrgencyLevel.ACUTE)) {
        ambulance.updateCoveragePenalty(0);
        return;
      }

      // using the hour when the ambulance will arrive at incident
      var arrivalTime = incident.callReceived().plusSeconds(ambulance.getTimeToIncident());

      // average demand in area of ambulance origin location
      var neighbours = DistanceIO.getNeighbours(ambulance.getCurrentLocation());
      var totalAreaDemand = 0.0;
      for (var neighbour : neighbours) {
        var distribution = IncidentIO.distributions.get(neighbour);
        var historicAverageDemand = distribution.getHourAverage(arrivalTime);
        totalAreaDemand += historicAverageDemand;
      }

      if (totalAreaDemand == 0.0) {
        ambulance.updateCoveragePenalty(0);
        return;
      }

      var neighbourAmbulanceCount =
          availableAmbulances.stream()
              .map(a -> new Coordinate(a.getCurrentLocation().id()))
              .filter(neighbours::contains)
              .count();

      var uncoveredDemand = max(0.0, totalAreaDemand - (neighbourAmbulanceCount - 1));

      ambulance.updateCoveragePenalty((int) uncoveredDemand * 180);
    }
  };

  public abstract void updateAmbulance(
      Ambulance ambulance, List<Ambulance> otherAmbulances, Incident incident);
}
