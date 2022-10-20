package no.ntnu.ambulanceallocation.optimization.initializer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import no.ntnu.ambulanceallocation.simulation.BaseStation;
import no.ntnu.ambulanceallocation.utils.Utils;

public class UniformRandom implements Initializer {

  @Override
  public List<Integer> initialize(int numberOfAmbulances) {
    var ambulanceAllocation = new ArrayList<Integer>();
    var ids = Arrays.stream(BaseStation.values()).map(BaseStation::getId).toList();

    while (ambulanceAllocation.size() < numberOfAmbulances) {
      if (ambulanceAllocation.size() + ids.size() <= numberOfAmbulances) {
        ambulanceAllocation.addAll(ids);
      } else {
        var remaining = numberOfAmbulances - ambulanceAllocation.size();
        var rest = Utils.randomChooseN(ids, remaining);
        ambulanceAllocation.addAll(rest);
      }
    }
    return ambulanceAllocation;
  }
}
