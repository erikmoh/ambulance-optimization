package no.ntnu.ambulanceallocation.optimization.initializer;

import java.util.ArrayList;
import java.util.List;

import no.ntnu.ambulanceallocation.simulation.BaseStation;
import no.ntnu.ambulanceallocation.utils.Utils;

public class Random implements Initializer {

  @Override
  public List<Integer> initialize(int numberOfAmbulances) {
    var ambulanceAllocation = new ArrayList<Integer>();

    for (var i = 0; i < numberOfAmbulances; i++) {
      ambulanceAllocation.add(Utils.randomInt(BaseStation.size()));
    }

    return ambulanceAllocation;
  }
}
