package no.ntnu.ambulanceallocation.simulation.incident;

import java.time.LocalDateTime;
import no.ntnu.ambulanceallocation.simulation.grid.Coordinate;

public enum IncidentDistribution {
  GRID {
    @Override
    public Double getPredictedDemand(Coordinate coordinate, LocalDateTime arrivalTime) {
      return IncidentIO.gridDistribution
          .get(coordinate)
          .get(arrivalTime.getMonth().getValue())
          .get(arrivalTime.getDayOfWeek().getValue())
          .get(arrivalTime.getHour());
    }

    @Override
    public Double getPredictedDemand(Integer baseStation, LocalDateTime arrivalTime) {
      throw new IllegalStateException("Cannot get grid demand using base station integer");
    }
  },
  BASE_STATION {
    @Override
    public Double getPredictedDemand(Coordinate coordinate, LocalDateTime arrivalTime) {
      throw new IllegalStateException("Cannot get base station average demand using coordinate");
    }

    @Override
    public Double getPredictedDemand(Integer baseStation, LocalDateTime arrivalTime) {
      return IncidentIO.baseStationDistribution
          .get(baseStation)
          .get(arrivalTime.getMonth().getValue())
          .get(arrivalTime.getDayOfWeek().getValue())
          .get(arrivalTime.getHour());
    }
  },
  PREDICTION {
    @Override
    public Double getPredictedDemand(Coordinate coordinate, LocalDateTime arrivalTime) {
      throw new IllegalStateException("Cannot get predicted demand using coordinate");
    }

    @Override
    public Double getPredictedDemand(Integer baseStation, LocalDateTime arrivalTime) {
      if (!IncidentIO.predictionDistribution
          .get(baseStation)
          .containsKey(arrivalTime.getDayOfMonth())) {
        return 0.0;
      }
      return IncidentIO.predictionDistribution
          .get(baseStation)
          .get(arrivalTime.getDayOfMonth())
          .get(arrivalTime.getHour());
    }
  },
  TRUTH {
    @Override
    public Double getPredictedDemand(Coordinate coordinate, LocalDateTime arrivalTime) {
      throw new IllegalStateException("Cannot get truth demand using coordinate");
    }

    @Override
    public Double getPredictedDemand(Integer baseStation, LocalDateTime arrivalTime) {
      return IncidentIO.truthDistribution
          .get(baseStation)
          .get(arrivalTime.getDayOfMonth())
          .get(arrivalTime.getHour());
    }
  };

  public abstract Double getPredictedDemand(Coordinate coordinate, LocalDateTime arrivalTime);

  public abstract Double getPredictedDemand(Integer baseStation, LocalDateTime arrivalTime);
}
