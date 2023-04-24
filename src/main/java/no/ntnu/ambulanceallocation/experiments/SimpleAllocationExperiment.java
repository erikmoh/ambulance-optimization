package no.ntnu.ambulanceallocation.experiments;

import no.ntnu.ambulanceallocation.Parameters;
import no.ntnu.ambulanceallocation.optimization.initializer.Initializer;
import no.ntnu.ambulanceallocation.optimization.initializer.PopulationProportionate;
import no.ntnu.ambulanceallocation.simulation.Simulation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleAllocationExperiment implements Experiment {

  private static final Logger logger = LoggerFactory.getLogger(SimpleAllocationExperiment.class);
  private final Result incidentResults = new Result();

  @Override
  public void run() {
    var populationProportionate = new PopulationProportionate();
    runDeterministicExperiment(populationProportionate);
  }

  @Override
  public void saveResults() {
    incidentResults.saveResults("simple_response_times");
  }

  private void runDeterministicExperiment(Initializer initializer) {
    var name = initializer.getClass().getSimpleName();
    logger.info("Running {} ...", name);

    var dayShiftAllocation = initializer.initialize(Parameters.NUMBER_OF_AMBULANCES_DAY);
    var nightShiftAllocation = initializer.initialize(Parameters.NUMBER_OF_AMBULANCES_NIGHT);

    var simulationResults = Simulation.simulate(dayShiftAllocation, nightShiftAllocation);

    incidentResults.saveColumn("timestamp", simulationResults.getCallTimes());
    incidentResults.saveColumn("urgency", simulationResults.getUrgencyLevels());
    incidentResults.saveColumn("response_time", simulationResults.getResponseTimes());

    logger.info("Average response time: {}", simulationResults.averageResponseTimes());
    logger.info("Average survival rate: {}", simulationResults.averageSurvivalRate());
  }

  public static void main(String[] args) {
    logger.info("Running simple experiment...");
    var simpleExperiment = new SimpleAllocationExperiment();
    simpleExperiment.run();
    logger.info("Done");

    logger.info("Saving results for simple experiment...");
    simpleExperiment.saveResults();
    logger.info("Simple experiment completed successfully");
  }
}
