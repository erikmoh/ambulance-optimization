package no.ntnu.ambulanceallocation.experiments;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import no.ntnu.ambulanceallocation.Parameters;
import no.ntnu.ambulanceallocation.optimization.initializer.Initializer;
import no.ntnu.ambulanceallocation.optimization.initializer.PopulationProportionate;
import no.ntnu.ambulanceallocation.simulation.ResponseTimes;
import no.ntnu.ambulanceallocation.simulation.Simulation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecutionTimeExperiment implements Experiment {

  private static final Logger logger = LoggerFactory.getLogger(ExecutionTimeExperiment.class);

  private final Result allocationResult = new Result();
  private final Result responseTimeResult = new Result();

  @Override
  public void run() {
    var populationProportionate = new PopulationProportionate();
    runDeterministicExperiment(populationProportionate);
  }

  @Override
  public void saveResults() {
    responseTimeResult.saveResults("execution_time_response_times");
    allocationResult.saveResults("execution_time_allocations");
  }

  private void runDeterministicExperiment(Initializer initializer) {
    var name = initializer.getClass().getSimpleName();
    logger.info("Running {} ...", name);

    var dayShiftAllocation = initializer.initialize(Parameters.NUMBER_OF_AMBULANCES_DAY);
    var nightShiftAllocation = initializer.initialize(Parameters.NUMBER_OF_AMBULANCES_NIGHT);

    var executionTimes = new ArrayList<Duration>();
    ResponseTimes responseTimes = null;
    for (int i = 0; i < 10000; i++) {
      var startTime = LocalDateTime.now();
      responseTimes = Simulation.simulate(dayShiftAllocation, nightShiftAllocation);
      var endTime = LocalDateTime.now();
      executionTimes.add(Duration.between(startTime, endTime));
    }

    var averageExecutionTime =
        executionTimes.stream().mapToLong(Duration::getSeconds).average().orElseThrow();

    logger.info("Average execution time: {}", averageExecutionTime);
    logger.info("Average response time: {}", responseTimes.average());
  }

  public static void main(String[] args) {
    logger.info("Running execution time experiment...");
    var executionTimeExperiment = new ExecutionTimeExperiment();
    executionTimeExperiment.run();
    logger.info("Done");
  }
}
