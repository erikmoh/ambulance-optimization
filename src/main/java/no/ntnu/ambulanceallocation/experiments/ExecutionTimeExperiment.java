package no.ntnu.ambulanceallocation.experiments;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import no.ntnu.ambulanceallocation.Parameters;
import no.ntnu.ambulanceallocation.optimization.initializer.Initializer;
import no.ntnu.ambulanceallocation.optimization.initializer.PopulationProportionate;
import no.ntnu.ambulanceallocation.simulation.Simulation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecutionTimeExperiment implements Experiment {

  private static final Logger logger = LoggerFactory.getLogger(ExecutionTimeExperiment.class);

  private final Result responseTimeResult = new Result();

  @Override
  public void run() {
    var populationProportionate = new PopulationProportionate();
    runDeterministicExperiment(populationProportionate);
  }

  @Override
  public void saveResults() {
    responseTimeResult.saveResults("factor_acute_response_times");
  }

  private void runDeterministicExperiment(Initializer initializer) {
    var name = initializer.getClass().getSimpleName();
    logger.info("Running {} ...", name);

    var dayShiftAllocation = initializer.initialize(Parameters.NUMBER_OF_AMBULANCES_DAY);
    var nightShiftAllocation = initializer.initialize(Parameters.NUMBER_OF_AMBULANCES_NIGHT);

    var executionTimes = new ArrayList<Duration>();
    var simulationResults = Simulation.simulate(dayShiftAllocation, nightShiftAllocation);

    var best = 10000.0;
    var bestFactor = 0;

    var times = new ArrayList<Double>();
    var factors = new ArrayList<Integer>();

    for (int i = 0; i < 1000; i += 1) {
      var startTime = LocalDateTime.now();
      simulationResults = Simulation.simulate(dayShiftAllocation, nightShiftAllocation, i);
      var endTime = LocalDateTime.now();

      var resultMap = simulationResults.createAverageResults();
      var ac = resultMap.get("acuteResponse");
      // ac = simulationResults.averageSurvivalRate();
      // logger.info("factor: {}, all: {}, acute: {}, urgent: {}, ", i, all, ac, ur);
      times.add(ac);
      factors.add(i);

      if (ac < best) {
        best = ac;
        bestFactor = i;
      }

      executionTimes.add(Duration.between(startTime, endTime));
    }

    var averageExecutionTime =
        executionTimes.stream().mapToLong(Duration::getNano).average().orElseThrow();
    responseTimeResult.saveColumn("factor", factors);
    responseTimeResult.saveColumn("time", times);

    logger.info("Best factor: {}, bestAcute: {}", bestFactor, best);
    logger.info("Average execution time: {}", (averageExecutionTime / 1_000_000_000));
    logger.info("Average response time: {}", simulationResults.averageResponseTimes());
    logger.info("Average survival rate: {}", simulationResults.averageSurvivalRate());
  }

  public static void main(String[] args) {
    logger.info("Running execution time experiment...");
    var executionTimeExperiment = new ExecutionTimeExperiment();
    executionTimeExperiment.run();
    logger.info("Done");

    logger.info("Saving results for execution experiment...");
    executionTimeExperiment.saveResults();
    logger.info("Execution experiment completed successfully");
  }
}
