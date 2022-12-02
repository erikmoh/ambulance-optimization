package no.ntnu.ambulanceallocation.experiments;

import no.ntnu.ambulanceallocation.Parameters;
import no.ntnu.ambulanceallocation.optimization.initializer.Initializer;
import no.ntnu.ambulanceallocation.optimization.initializer.PopulationProportionate;
import no.ntnu.ambulanceallocation.simulation.Simulation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UrgencyExperiment implements Experiment {

  private static final Logger logger = LoggerFactory.getLogger(UrgencyExperiment.class);

  private final Result allocationResult = new Result();
  private final Result responseTimeResult = new Result();

  @Override
  public void run() {
    var populationProportionate = new PopulationProportionate();
    runDeterministicExperiment(populationProportionate);
  }

  @Override
  public void saveResults() {
    responseTimeResult.saveResults("urgency_experiment_response_times");
    allocationResult.saveResults("urgency_experiment_allocations");
  }

  private void runDeterministicExperiment(Initializer initializer) {
    var name = initializer.getClass().getSimpleName();
    logger.info("Running {}...", name);

    var dayShiftAllocation = initializer.initialize(Parameters.NUMBER_OF_AMBULANCES_DAY);
    var nightShiftAllocation = initializer.initialize(Parameters.NUMBER_OF_AMBULANCES_NIGHT);
    var responseTimes = Simulation.simulate(dayShiftAllocation, nightShiftAllocation);

    allocationResult.saveColumn(name + "_d", dayShiftAllocation.stream().sorted().toList());
    allocationResult.saveColumn(name + "_n", nightShiftAllocation.stream().sorted().toList());
    responseTimeResult.saveColumn("urgency", responseTimes.getUrgencyLevels());
    responseTimeResult.saveColumn("timestamp", responseTimes.getCallTimes());
    responseTimeResult.saveColumn(name, responseTimes.getResponseTimes());

    logger.info("Done");
  }

  public static void main(String[] args) {
    logger.info("Running urgency experiment...");
    var urgencyExperiment = new UrgencyExperiment();
    urgencyExperiment.run();
    logger.info("Done");

    logger.info("Saving results for urgency experiment...");
    urgencyExperiment.saveResults();
    logger.info("Urgency experiment completed successfully");
  }
}
