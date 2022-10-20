package no.ntnu.ambulanceallocation.optimization.initializer;

import java.util.ArrayList;
import java.util.List;

import no.ntnu.ambulanceallocation.simulation.BaseStation;

public class Uniform implements Initializer {

  @Override
  public List<Integer> initialize(int numberOfAmbulances) {
    var ambulanceAllocation = new ArrayList<Integer>();

    for (var i = 0; i < numberOfAmbulances; i++) {
      ambulanceAllocation.add(i % BaseStation.size());
    }

    return ambulanceAllocation;
  }
}
