package no.ntnu.ambulanceallocation.experiments;

import java.util.List;
import no.ntnu.ambulanceallocation.Parameters;
import no.ntnu.ambulanceallocation.optimization.initializer.Initializer;
import no.ntnu.ambulanceallocation.optimization.initializer.PopulationProportionate;
import no.ntnu.ambulanceallocation.simulation.Simulation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleAllocationExperiment implements Experiment {

  private static final Logger logger = LoggerFactory.getLogger(SimpleAllocationExperiment.class);
  private final Result incidentResults = new Result();
  private final Result fitnessResult = new Result();
  private final Result allocationResult = new Result();

  @Override
  public void run() {
    var populationProportionate = new PopulationProportionate();
    runDeterministicExperiment(populationProportionate);
  }

  @Override
  public void saveResults() {
    var PREFIX = "";
    var POSTFIX = "";
    incidentResults.saveResults(PREFIX + "simple_response_times" + POSTFIX);
    fitnessResult.saveResults(PREFIX + "simple_result" + POSTFIX);
    allocationResult.saveResults(PREFIX + "allocations" + POSTFIX);
  }

  private void runDeterministicExperiment(Initializer initializer) {
    var name = initializer.getClass().getSimpleName();
    logger.info("Running {} ...", name);

    var dayShiftAllocation = initializer.initialize(Parameters.NUMBER_OF_AMBULANCES_DAY);
    var nightShiftAllocation = initializer.initialize(Parameters.NUMBER_OF_AMBULANCES_NIGHT);

    allocationResult.saveColumn(name + "_d", dayShiftAllocation);
    allocationResult.saveColumn(name + "_n", nightShiftAllocation);

    var simulationResults = Simulation.simulate(dayShiftAllocation, nightShiftAllocation);

    incidentResults.saveColumn("timestamp", simulationResults.getCallTimes());
    incidentResults.saveColumn("urgency", simulationResults.getUrgencyLevels());
    incidentResults.saveColumn("response_time", simulationResults.getResponseTimes());

    fitnessResult.saveColumn(
        "All",
        List.of(simulationResults.averageResponseTimes(), simulationResults.averageSurvivalRate()));
    var resultMap = simulationResults.createAverageResults();
    fitnessResult.saveColumn(
        "A", List.of(resultMap.get("acuteResponse"), resultMap.get("acuteSurvival")));
    fitnessResult.saveColumn(
        "H", List.of(resultMap.get("urgentResponse"), resultMap.get("urgentSurvival")));

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
