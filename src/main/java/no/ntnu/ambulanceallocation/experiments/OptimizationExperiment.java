package no.ntnu.ambulanceallocation.experiments;

import java.util.ArrayList;
import java.util.List;
import no.ntnu.ambulanceallocation.Parameters;
import no.ntnu.ambulanceallocation.optimization.Allocation;
import no.ntnu.ambulanceallocation.optimization.Optimizer;
import no.ntnu.ambulanceallocation.optimization.ga.GeneticAlgorithm;
import no.ntnu.ambulanceallocation.simulation.Simulation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OptimizationExperiment implements Experiment {

  private static final Logger logger = LoggerFactory.getLogger(OptimizationExperiment.class);

  private final Result allocationResult = new Result();
  private final Result bestFitness = new Result();
  private final Result runs = new Result();

  private static final String POSTFIX = "";

  @Override
  public void run() {
    var geneticAlgorithm = new GeneticAlgorithm();
    runStochasticExperiment(geneticAlgorithm);
  }

  @Override
  public void saveResults() {
    allocationResult.saveResults("allocations" + POSTFIX);
    bestFitness.saveResults("result" + POSTFIX);
    runs.saveResults("runs" + POSTFIX);
  }

  private void runStochasticExperiment(Optimizer optimizer) {
    var optimizerName = optimizer.getAbbreviation();
    var overallBestFitness = Double.MAX_VALUE;
    var overallBestAllocation = new Allocation();
    var overallBestRunStatistics = new Result();

    var bestSurvivals = new ArrayList<Double>();
    var bestAcuteSurvivals = new ArrayList<Double>();
    var bestUrgentSurvivals = new ArrayList<Double>();
    var bestResponseTimeAverages = new ArrayList<Double>();
    var bestAcuteResponseTimeAverages = new ArrayList<Double>();
    var bestUrgentResponseTimeAverages = new ArrayList<Double>();

    for (int i = 0; i < Parameters.RUNS; i++) {
      logger.info("Starting {}... run {}/{}", optimizerName, i + 1, Parameters.RUNS);

      optimizer.optimize();
      var solution = optimizer.getOptimalSolution();

      var results = Simulation.withDefaultConfig().simulate(solution.getAllocation());

      var resultMap = results.createAverageResults();
      bestAcuteSurvivals.add(resultMap.get("acuteSurvival"));
      bestUrgentSurvivals.add(resultMap.get("urgentSurvival"));
      bestAcuteResponseTimeAverages.add(resultMap.get("acuteResponse"));
      bestUrgentResponseTimeAverages.add(resultMap.get("urgentResponse"));

      bestSurvivals.add(results.averageSurvivalRate());
      bestResponseTimeAverages.add(results.averageResponseTimes());

      if (solution.getFitness() < overallBestFitness) {
        overallBestFitness = solution.getFitness();
        overallBestAllocation = solution.getAllocation();
        overallBestRunStatistics = optimizer.getRunStatistics();
      }

      logger.info("{} run {}/{} completed.", optimizerName, i + 1, Parameters.RUNS);
    }

    overallBestRunStatistics.saveResults("ga" + POSTFIX);

    runs.saveColumn("bestSurvivals", bestSurvivals);
    runs.saveColumn("bestAcuteSurvivals", bestAcuteSurvivals);
    runs.saveColumn("bestUrgentSurvivals", bestUrgentSurvivals);
    runs.saveColumn("bestResponseTimeAverages", bestResponseTimeAverages);
    runs.saveColumn("bestAcuteResponseTimeAverages", bestAcuteResponseTimeAverages);
    runs.saveColumn("bestUrgentResponseTimeAverages", bestUrgentResponseTimeAverages);

    var overallBestSimulationResults =
        Simulation.withDefaultConfig().simulate(overallBestAllocation);

    allocationResult.saveColumn(
        optimizerName + "_d", overallBestAllocation.getDayShiftAllocationSorted());
    allocationResult.saveColumn(
        optimizerName + "_n", overallBestAllocation.getNightShiftAllocationSorted());

    bestFitness.saveColumn(
        "ga best",
        List.of(
            overallBestSimulationResults.averageResponseTimes(),
            overallBestSimulationResults.averageSurvivalRate()));
    var resultMap = overallBestSimulationResults.createAverageResults();
    bestFitness.saveColumn(
        "ga A best", List.of(resultMap.get("acuteResponse"), resultMap.get("acuteSurvival")));
    bestFitness.saveColumn(
        "ga H best", List.of(resultMap.get("urgentResponse"), resultMap.get("urgentSurvival")));

    var averageSurvival =
        bestSurvivals.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    var averageResponseTimeAverage =
        bestResponseTimeAverages.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    bestFitness.saveColumn("ga avg", List.of(averageResponseTimeAverage, averageSurvival));

    var averageAcuteSurvival =
        bestAcuteSurvivals.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    var averageAcuteResponseTimeAverage =
        bestAcuteResponseTimeAverages.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElseThrow();
    bestFitness.saveColumn(
        "ga A avg", List.of(averageAcuteResponseTimeAverage, averageAcuteSurvival));

    var averageUrgentSurvival =
        bestUrgentSurvivals.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    var averageUrgentResponseTimeAverage =
        bestUrgentResponseTimeAverages.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElseThrow();
    bestFitness.saveColumn(
        "ga H avg", List.of(averageUrgentResponseTimeAverage, averageUrgentSurvival));
  }

  public static void main(String[] args) {
    logger.info("Running optimization experiment...");
    var optimizationExperiment = new OptimizationExperiment();
    optimizationExperiment.run();
    logger.info("Done");

    logger.info("Saving results for optimization experiment...");
    optimizationExperiment.saveResults();
    logger.info("Optimization experiment completed successfully");
  }
}
