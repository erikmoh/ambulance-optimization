package no.ntnu.ambulanceallocation.optimization.initializer;

import java.util.List;
import no.ntnu.ambulanceallocation.utils.Utils;

public class Mix implements Initializer {

  private final PopulationProportionate populationProportionate = new PopulationProportionate();
  private final Random randomInitializer = new Random();
  private final UniformRandom uniformRandomInitializer = new UniformRandom();

  @Override
  public List<Integer> initialize(int numberOfAmbulances) {
    var p = Utils.randomDouble();
    if (p < 0.8) {
      return randomInitializer.initialize(numberOfAmbulances);
    } else if (p < 0.9) {
      return populationProportionate.initialize(numberOfAmbulances);
    } else {
      return uniformRandomInitializer.initialize(numberOfAmbulances);
    }
  }
}
