package no.ntnu.ambulanceallocation.experiments;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import no.ntnu.ambulanceallocation.Parameters;
import no.ntnu.ambulanceallocation.optimization.Allocation;
import no.ntnu.ambulanceallocation.optimization.Optimizer;
import no.ntnu.ambulanceallocation.optimization.ga.GeneticAlgorithm;
import no.ntnu.ambulanceallocation.simulation.Simulation;
import no.ntnu.ambulanceallocation.simulation.SimulationResults;
import no.ntnu.ambulanceallocation.simulation.incident.UrgencyLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OptimizationExperiment implements Experiment {

  private static final Logger logger = LoggerFactory.getLogger(OptimizationExperiment.class);

  private final Result allocationResult = new Result();
  private final Result bestFitness = new Result();

  @Override
  public void run() {
    var geneticAlgorithm = new GeneticAlgorithm();
    runStochasticExperiment(geneticAlgorithm);
  }

  @Override
  public void saveResults() {
    var POSTFIX = "_islands_nosort_6m_5";
    allocationResult.saveResults("allocations" + POSTFIX);
    bestFitness.saveResults("result" + POSTFIX);
  }

  private void runStochasticExperiment(Optimizer optimizer) {
    var optimizerName = optimizer.getAbbreviation();
    var overallBestFitness = Double.MAX_VALUE;
    var overallBestAllocation = new Allocation();

    var bestFitnesses = new ArrayList<Double>();
    var bestResponseTimeAverages = new ArrayList<Double>();

    for (int i = 0; i < Parameters.RUNS; i++) {
      logger.info("Starting {}... run {}/{}", optimizerName, i + 1, Parameters.RUNS);

      optimizer.optimize();
      var solution = optimizer.getOptimalSolution();

      bestFitnesses.add(solution.getFitness());
      bestResponseTimeAverages.add(
          Simulation.withDefaultConfig().simulate(solution.getAllocation()).averageResponseTimes());

      if (solution.getFitness() < overallBestFitness) {
        overallBestFitness = solution.getFitness();
        overallBestAllocation = solution.getAllocation();
      }

      logger.info("{} run {}/{} completed.", optimizerName, i + 1, Parameters.RUNS);
    }

    var overallBestSimulationResults =
        Simulation.withDefaultConfig().simulate(overallBestAllocation);

    allocationResult.saveColumn(
        optimizerName + "_d", overallBestAllocation.getDayShiftAllocationSorted());
    allocationResult.saveColumn(
        optimizerName + "_n", overallBestAllocation.getNightShiftAllocationSorted());

    bestFitness.saveColumn(
        "ga",
        List.of(
            overallBestSimulationResults.averageResponseTimes(),
            overallBestSimulationResults.averageSurvivalRate()));

    var averageFitness =
        bestFitnesses.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    var averageResponseTimeAverage =
        bestResponseTimeAverages.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    bestFitness.saveColumn("ga avg", List.of(averageResponseTimeAverage, 1.0 - averageFitness));

    var resultMap = createAverageResults(overallBestSimulationResults);

    bestFitness.saveColumn(
        "ga A", List.of(resultMap.get("acuteResponse"), resultMap.get("acuteSurvival")));
    bestFitness.saveColumn(
        "ga H", List.of(resultMap.get("urgentResponse"), resultMap.get("urgentSurvival")));
  }

  public Map<String, Double> createAverageResults(SimulationResults results) {
    var acuteLen = 0;
    var acuteResponse = 0;
    var acuteSurvival = 0.0;
    var urgentLen = 0;
    var urgentResponse = 0;
    var urgentSurvival = 0.0;
    for (int i = 0; i < results.getUrgencyLevels().size(); i++) {
      var urgency = results.getUrgencyLevels().get(i);
      var responseTime = results.getResponseTimes().get(i);
      var survivalRate = results.getSurvivalRates().get(i);
      if (urgency.equals(UrgencyLevel.ACUTE)) {
        acuteResponse += responseTime;
        acuteSurvival += survivalRate;
        acuteLen++;
      } else {
        urgentResponse += responseTime;
        urgentSurvival += survivalRate;
        urgentLen++;
      }
    }

    return Map.of(
        "acuteResponse",
        (double) acuteResponse / acuteLen,
        "acuteSurvival",
        acuteSurvival / acuteLen,
        "urgentResponse",
        (double) urgentResponse / urgentLen,
        "urgentSurvival",
        urgentSurvival / urgentLen);
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
