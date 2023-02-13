package no.ntnu.ambulanceallocation.experiments;

import static no.ntnu.ambulanceallocation.utils.Utils.round;

import java.util.List;
import java.util.Map;
import no.ntnu.ambulanceallocation.Parameters;
import no.ntnu.ambulanceallocation.optimization.Allocation;
import no.ntnu.ambulanceallocation.optimization.Optimizer;
import no.ntnu.ambulanceallocation.optimization.ga.GeneticAlgorithm;
import no.ntnu.ambulanceallocation.optimization.initializer.Initializer;
import no.ntnu.ambulanceallocation.optimization.initializer.PopulationProportionate;
import no.ntnu.ambulanceallocation.simulation.Simulation;
import no.ntnu.ambulanceallocation.simulation.SimulationResults;
import no.ntnu.ambulanceallocation.simulation.incident.UrgencyLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UrgencyExperiment implements Experiment {

  private static final Logger logger = LoggerFactory.getLogger(UrgencyExperiment.class);

  private final Result allocationResult = new Result();
  private final Result incidentResults = new Result();
  private final Result bestFitness = new Result();

  private final String PREFIX = "";

  @Override
  public void run() {
    var populationProportionate = new PopulationProportionate();
    var geneticAlgorithm = new GeneticAlgorithm();
    runDeterministicExperiment(populationProportionate);
    runStochasticExperiment(geneticAlgorithm);
  }

  @Override
  public void saveResults() {
    incidentResults.saveResults(PREFIX + "urgency_incidents");
    allocationResult.saveResults(PREFIX + "urgency_allocations");
    bestFitness.saveResults(PREFIX + "urgency_result");
  }

  private void runDeterministicExperiment(Initializer initializer) {
    var name = initializer.getClass().getSimpleName();
    logger.info("Running {}...", name);

    var dayShiftAllocation = initializer.initialize(Parameters.NUMBER_OF_AMBULANCES_DAY);
    var nightShiftAllocation = initializer.initialize(Parameters.NUMBER_OF_AMBULANCES_NIGHT);
    var simulationResults = Simulation.simulate(dayShiftAllocation, nightShiftAllocation);

    allocationResult.saveColumn("pop_prop_d", dayShiftAllocation.stream().sorted().toList());
    allocationResult.saveColumn("pop_prop_n", nightShiftAllocation.stream().sorted().toList());

    incidentResults.saveColumn("timestamp", simulationResults.getCallTimes());
    incidentResults.saveColumn("urgency", simulationResults.getUrgencyLevels());
    incidentResults.saveColumn("pop_prop_response", simulationResults.getResponseTimes());
    incidentResults.saveColumn(
        "pop_prop_survival",
        simulationResults.getSurvivalRates().stream().map(d -> round(d, 2)).toList());

    bestFitness.saveColumn(
        "pop_prop",
        List.of(simulationResults.averageResponseTimes(), simulationResults.averageSurvivalRate()));

    var resultMap = createAverageResults(simulationResults);

    bestFitness.saveColumn(
        "A", List.of(resultMap.get("acuteResponse"), resultMap.get("acuteSurvival")));
    bestFitness.saveColumn(
        "H", List.of(resultMap.get("urgentResponse"), resultMap.get("urgentSurvival")));
  }

  private void runStochasticExperiment(Optimizer optimizer) {
    var optimizerName = optimizer.getAbbreviation();
    var overallBestFitness = Double.MAX_VALUE;
    var overallBestAllocation = new Allocation();
    var overallBestRunStatistics = new Result();

    for (int i = 0; i < Parameters.RUNS; i++) {
      logger.info("Starting {}... run {}/{}", optimizerName, i + 1, Parameters.RUNS);

      optimizer.optimize();
      var solution = optimizer.getOptimalSolution();

      if (solution.getFitness() < overallBestFitness) {
        overallBestFitness = solution.getFitness();
        overallBestAllocation = solution.getAllocation();
        overallBestRunStatistics = optimizer.getRunStatistics();
      }

      logger.info("{} run {}/{} completed.", optimizerName, i + 1, Parameters.RUNS);
    }

    var overallBestSimulationResults =
        Simulation.withDefaultConfig().simulate(overallBestAllocation);

    incidentResults.saveColumn("ga_response", overallBestSimulationResults.getResponseTimes());
    incidentResults.saveColumn(
        "ga_survival",
        overallBestSimulationResults.getSurvivalRates().stream().map(d -> round(d, 2)).toList());

    allocationResult.saveColumn(
        optimizerName + "_d", overallBestAllocation.getDayShiftAllocationSorted());
    allocationResult.saveColumn(
        optimizerName + "_n", overallBestAllocation.getNightShiftAllocationSorted());

    overallBestRunStatistics.saveResults(PREFIX + "urgency_ga");

    bestFitness.saveColumn(
        "ga",
        List.of(
            overallBestSimulationResults.averageResponseTimes(),
            overallBestSimulationResults.averageSurvivalRate()));

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
    logger.info("Running urgency experiment...");
    var urgencyExperiment = new UrgencyExperiment();
    urgencyExperiment.run();
    logger.info("Done");

    logger.info("Saving results for urgency experiment...");
    urgencyExperiment.saveResults();
    logger.info("Urgency experiment completed successfully");
  }
}
