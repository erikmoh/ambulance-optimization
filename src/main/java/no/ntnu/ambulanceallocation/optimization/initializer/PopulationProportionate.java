package no.ntnu.ambulanceallocation.optimization.initializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import no.ntnu.ambulanceallocation.simulation.BaseStation;
import no.ntnu.ambulanceallocation.utils.Tuple;
import no.ntnu.ambulanceallocation.utils.Utils;

public class PopulationProportionate implements Initializer {

  @Override
  public List<Integer> initialize(int numberOfAmbulances) {
    var ambulancesPerStation =
        BaseStation.getPopulationDistribution().stream()
            .map(populationProportion -> populationProportion * numberOfAmbulances)
            .map(proportion -> (int) Math.round(proportion))
            .collect(Collectors.toList());

    return fairRepair(
        generateAllocation(ambulancesPerStation), ambulancesPerStation, numberOfAmbulances);
  }

  private List<Integer> generateAllocation(List<Integer> ambulancesPerStation) {
    var allocation = new ArrayList<Integer>();

    for (var baseStationId = 0; baseStationId < BaseStation.size(); baseStationId++) {
      for (var i = 0; i < ambulancesPerStation.get(baseStationId); i++) {
        allocation.add(baseStationId);
      }
    }
    return allocation;
  }

  private List<Integer> stochasticRepair(List<Integer> allocation, int numberOfAmbulances) {
    while (allocation.size() != numberOfAmbulances) {
      if (allocation.size() > numberOfAmbulances) {
        removeRandom(allocation);
      } else {
        addRandom(allocation);
      }
    }
    return allocation;
  }

  private List<Integer> fairRepair(
      List<Integer> allocation, List<Integer> ambulancesPerStation, int numberOfAmbulances) {

    var deviations = computeDeviations(ambulancesPerStation, numberOfAmbulances);

    var difference = allocation.size() - numberOfAmbulances;
    var deficit = difference < 0;

    if (deficit) {
      Collections.reverse(deviations);
    }

    for (int i = 0; i < Math.abs(difference); i++) {
      if (deficit) {
        allocation.add(deviations.get(i));
      } else {
        allocation.remove(deviations.get(i));
      }
    }
    return allocation;
  }

  private List<Integer> computeDeviations(
      List<Integer> ambulancesPerStation, int numberOfAmbulances) {

    var deviations = new ArrayList<Tuple<Double>>();
    var populationDistribution = BaseStation.getPopulationDistribution();

    for (var id : BaseStation.ids()) {
      var ambulanceProportion = populationDistribution.get(id) * numberOfAmbulances;
      var difference = ambulanceProportion - ambulancesPerStation.get(id);
      deviations.add(new Tuple<>((double) id, difference));
    }

    deviations.sort(Comparator.comparing(Tuple::second));
    return deviations.stream()
        .map(element -> element.first().intValue())
        .collect(Collectors.toList());
  }

  private void removeRandom(List<Integer> allocation) {
    allocation.remove(Utils.randomInt(allocation.size()));
  }

  private void addRandom(List<Integer> allocation) {
    allocation.add(Utils.randomInt(BaseStation.size()));
  }
}
