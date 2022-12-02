package no.ntnu.ambulanceallocation.experiments.old;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import no.ntnu.ambulanceallocation.Parameters;
import no.ntnu.ambulanceallocation.experiments.Experiment;
import no.ntnu.ambulanceallocation.experiments.Result;
import no.ntnu.ambulanceallocation.optimization.Allocation;
import no.ntnu.ambulanceallocation.optimization.initializer.AllCityCenter;
import no.ntnu.ambulanceallocation.optimization.initializer.Initializer;
import no.ntnu.ambulanceallocation.optimization.initializer.PopulationProportionate;
import no.ntnu.ambulanceallocation.optimization.initializer.Random;
import no.ntnu.ambulanceallocation.optimization.initializer.Uniform;
import no.ntnu.ambulanceallocation.optimization.initializer.UniformRandom;
import no.ntnu.ambulanceallocation.simulation.Simulation;
import no.ntnu.ambulanceallocation.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FirstExperiment implements Experiment {

  private static final Logger logger = LoggerFactory.getLogger(FirstExperiment.class);

  private final Result allocationResult = new Result();
  private final Result responseTimeResult = new Result();
  private final Result stochasticResponseTimeResult = new Result();

  @Override
  public void run() {
    // Setup
    var random = new Random();
    var allCityCenter = new AllCityCenter();
    var uniform = new Uniform();
    var uniformRandom = new UniformRandom();
    var populationProportionate = new PopulationProportionate();

    // Partial experiments
    runStochasticExperiment(random);
    runDeterministicExperiment(allCityCenter);
    runDeterministicExperiment(uniform);
    runStochasticExperiment(uniformRandom);
    runDeterministicExperiment(populationProportionate);
  }

  @Override
  public void saveResults() {
    responseTimeResult.saveResults("first_experiment_response_times");
    allocationResult.saveResults("first_experiment_allocations");
    stochasticResponseTimeResult.saveResults("first_experiment_distribution");
  }

  private void runDeterministicExperiment(Initializer initializer) {
    var name = initializer.getClass().getSimpleName();
    logger.info("Running {} ...", name);

    var dayShiftAllocation = initializer.initialize(Parameters.NUMBER_OF_AMBULANCES_DAY);
    var nightShiftAllocation = initializer.initialize(Parameters.NUMBER_OF_AMBULANCES_NIGHT);
    var responseTimes = Simulation.simulate(dayShiftAllocation, nightShiftAllocation);

    allocationResult.saveColumn(name + "_d", dayShiftAllocation.stream().sorted().toList());
    allocationResult.saveColumn(name + "_n", nightShiftAllocation.stream().sorted().toList());
    responseTimeResult.saveColumn("urgency", responseTimes.getUrgencyLevels());
    responseTimeResult.saveColumn("timestamp", responseTimes.getCallTimes());
    responseTimeResult.saveColumn(name, responseTimes.getResponseTimes());
    stochasticResponseTimeResult.saveColumn(
        name, Collections.nCopies(Parameters.RUNS, responseTimes.average()));

    logger.info("Done");
  }

  private void runStochasticExperiment(Initializer initializer) {
    var name = initializer.getClass().getSimpleName();
    logger.info("Running {} ...", name);

    var fitness = new ArrayList<Double>();
    var allocations = new ArrayList<Allocation>();

    for (var i = 0; i < Parameters.RUNS; i++) {
      var dayShiftAllocation = initializer.initialize(Parameters.NUMBER_OF_AMBULANCES_DAY);
      var nightShiftAllocation = initializer.initialize(Parameters.NUMBER_OF_AMBULANCES_NIGHT);
      allocations.add(new Allocation(List.of(dayShiftAllocation, nightShiftAllocation)));
      var responseTimes = Simulation.simulate(dayShiftAllocation, nightShiftAllocation);
      fitness.add(responseTimes.average());
    }

    var medianIndex = Utils.medianIndexOf(fitness);
    var medianAllocation = allocations.get(medianIndex);
    var medianResponseTimes = Simulation.withDefaultConfig().simulate(medianAllocation);

    allocationResult.saveColumn(name + "_d", medianAllocation.getDayShiftAllocationSorted());
    allocationResult.saveColumn(name + "_n", medianAllocation.getNightShiftAllocationSorted());
    responseTimeResult.saveColumn("timestamp", medianResponseTimes.getCallTimes());
    responseTimeResult.saveColumn(name, medianResponseTimes.getResponseTimes());
    stochasticResponseTimeResult.saveColumn(name, fitness);

    logger.info("Done");
  }

  public static void main(String[] args) {
    logger.info("Running experiment 1 ...");
    var firstExperiment = new FirstExperiment();
    firstExperiment.run();
    logger.info("Done");

    logger.info("Saving results for experiment 1 ...");
    firstExperiment.saveResults();
    logger.info("Experiment 1 completed successfully.");
  }
}
