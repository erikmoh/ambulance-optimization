package no.ntnu.ambulanceallocation.optimization.initializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import no.ntnu.ambulanceallocation.simulation.BaseStation;
import no.ntnu.ambulanceallocation.utils.Utils;

public class SatisfyConstraintRandom implements Initializer {

  @Override
  public List<Integer> initialize(int numberOfAmbulances) {
    var allocation = new ArrayList<Integer>();

    while (allocation.size() < numberOfAmbulances) {
      var id = Utils.randomInt(BaseStation.size());
      if (Collections.frequency(allocation, id) < BaseStation.get(id).getCapacity()) {
        allocation.add(id);
      }
    }

    return allocation;
  }
}
