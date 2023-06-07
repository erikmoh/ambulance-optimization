package no.ntnu.ambulanceallocation.optimization.ga;

import static no.ntnu.ambulanceallocation.Parameters.CROSSOVER_PROBABILITY;
import static no.ntnu.ambulanceallocation.Parameters.CROSSOVER_TUNE_START;
import static no.ntnu.ambulanceallocation.Parameters.DISTINCT;
import static no.ntnu.ambulanceallocation.Parameters.GENERATIONS_COMBINED;
import static no.ntnu.ambulanceallocation.Parameters.GENERATIONS_ISLAND;
import static no.ntnu.ambulanceallocation.Parameters.INITIALIZER;
import static no.ntnu.ambulanceallocation.Parameters.ISLANDS;
import static no.ntnu.ambulanceallocation.Parameters.MAX_RUNNING_TIME;
import static no.ntnu.ambulanceallocation.Parameters.MUTATION_PROBABILITY;
import static no.ntnu.ambulanceallocation.Parameters.MUTATION_TUNE_START;
import static no.ntnu.ambulanceallocation.Parameters.POPULATION_SIZE;
import static no.ntnu.ambulanceallocation.Parameters.TOURNAMENT_SIZE;

import com.github.sh0nk.matplotlib4j.Plot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import no.ntnu.ambulanceallocation.experiments.Result;
import no.ntnu.ambulanceallocation.optimization.Optimizer;
import no.ntnu.ambulanceallocation.optimization.Solution;
import no.ntnu.ambulanceallocation.simulation.BaseStation;
import no.ntnu.ambulanceallocation.simulation.Config;
import no.ntnu.ambulanceallocation.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiObjectiveGeneticAlgorithm implements Optimizer {

  private static final ExecutorService executor = Executors.newCachedThreadPool();

  private final Logger logger = LoggerFactory.getLogger(MultiObjectiveGeneticAlgorithm.class);

  private final List<Double> bestFitness = new ArrayList<>();
  private final List<Double> averageFitness = new ArrayList<>();
  private final List<Double> diversity = new ArrayList<>();
  private final List<Double> diversityOld = new ArrayList<>();

  protected Config config;
  protected Population population;
  protected Set<Population> populationIslands;

  public MultiObjectiveGeneticAlgorithm() {
    this.config = Config.defaultConfig();
  }

  @Override
  public Solution getOptimalSolution() {
    return population.bestMO();
  }

  @Override
  public void optimize() {
    clearRunStatistics();
    population = new Population(POPULATION_SIZE, INITIALIZER, config);
    populationIslands = new HashSet<>();

    Runnable optimizationWrapper =
        () -> {
          var startTime = System.nanoTime();
          var generation = 1;
          var combinedGeneration = 1;
          var runningCombined = ISLANDS == 0;

          logger.info("Starting GA optimizer...");
          population.evaluateMO();
          printAndSaveSummary(logger, generation, population);

          // plotRankedPopulation();

          while (combinedGeneration < GENERATIONS_COMBINED
              && elapsedTime(startTime) < MAX_RUNNING_TIME) {

            if (!runningCombined && generation == GENERATIONS_ISLAND) {
              populationIslands.add(population);
              logger.info("islands: {}", populationIslands.size());
              if (populationIslands.size() == ISLANDS) {
                combineIslands();
                runningCombined = true;
              } else {
                newIsland();
                generation = 1;
              }
              printAndSaveSummary(logger, generation, population);
            }

            var nextPopulation = new Population();
            var countDownLatch = new CountDownLatch(POPULATION_SIZE);
            var crossoverP = getCrossoverProbability(generation);
            var mutationP = getMutationProbability(generation);

            for (var i = 0; i < POPULATION_SIZE / 2; i++) {
              executor.execute(
                  () -> {
                    var parents = population.selectionMO(TOURNAMENT_SIZE);
                    var offspringA = new Individual(parents.first());
                    var offspringB = new Individual(parents.second());

                    offspringA.recombineWith(offspringB, crossoverP);

                    offspringA.mutate(mutationP);
                    offspringB.mutate(mutationP);

                    synchronized (nextPopulation) {
                      if (nextPopulation.size() < POPULATION_SIZE) {
                        if (!DISTINCT || offspringA.hasChanged()) {
                          nextPopulation.add(offspringA, config.CONSTRAINT_STRATEGY());
                        }
                        countDownLatch.countDown();
                        if (!DISTINCT || nextPopulation.size() < POPULATION_SIZE) {
                          if (offspringB.hasChanged()) {
                            nextPopulation.add(offspringB, config.CONSTRAINT_STRATEGY());
                          }
                          countDownLatch.countDown();
                        }
                      }
                    }
                  });
            }

            try {
              countDownLatch.await();
            } catch (InterruptedException e) {
              e.printStackTrace();
            }

            population.addAll(nextPopulation);
            population.reduceRankedPopulation(POPULATION_SIZE);

            generation++;
            if (runningCombined) combinedGeneration++;
            printAndSaveSummary(logger, generation, population);

            // if (generation % 10 == 0) plotRankedPopulation();
          }

          logger.info("GA finished successfully.");
        };

    var optimizationTime = Utils.timeIt(optimizationWrapper);
    logger.info("Total GA optimization time: " + optimizationTime + " seconds");
  }

  private void newIsland() {
    population = new Population(POPULATION_SIZE, INITIALIZER, config);
    logger.info("Started new island.");
    population.evaluateMO();
  }

  private void combineIslands() {
    var combinedPopulation = new Population();
    for (var pop : populationIslands) {
      var allocation = new HashMap<Integer, Integer>();
      for (var b : BaseStation.values()) {
        var f = Collections.frequency(pop.get(0).getAllocation().get(0), b.getId());
        allocation.put(b.getId(), f);
      }
      logger.info("day: {}", allocation);
      combinedPopulation.addAll(pop);
    }
    combinedPopulation.reduceRankedPopulation(POPULATION_SIZE);
    population = combinedPopulation;
    logger.info("Combined islands.");
  }

  private double getCrossoverProbability(int generation) {
    var generationReduction = generation / 200.0;
    return Math.max(CROSSOVER_PROBABILITY, CROSSOVER_TUNE_START - generationReduction);
  }

  private double getMutationProbability(int generation) {
    var generationReduction = generation / 200.0;
    return Math.max(MUTATION_PROBABILITY, MUTATION_TUNE_START - generationReduction);
  }

  @Override
  public Result getRunStatistics() {
    var runStatistics = new Result();
    runStatistics.saveColumn("best", bestFitness);
    runStatistics.saveColumn("average", averageFitness);
    runStatistics.saveColumn("diversity", diversity);
    runStatistics.saveColumn("diversityOld", diversityOld);
    return runStatistics;
  }

  @Override
  public String getAbbreviation() {
    return "GA";
  }

  protected long elapsedTime(long startTime) {
    return TimeUnit.SECONDS.convert((System.nanoTime() - startTime), TimeUnit.NANOSECONDS);
  }

  protected void printAndSaveSummary(Logger logger, int generation, Population population) {
    logger.info("{} generation: {}", getAbbreviation(), generation);
    var best = population.bestMO();
    var bestFitness = best.getFitness();
    var averageFitness = population.getAverageFitness();
    var diversity = population.getDiversity();
    var diversityOld = population.getDiversityOld();
    if (config.USE_URGENCY_FITNESS()) {
      logger.info("Best fitness: {}", 1.0 - bestFitness);
      logger.info("Average fitness: {}", 1.0 - averageFitness);
    } else {
      var bestSurvivalRate = best.getSurvivalRate();
      logger.info("Best fitness: {}", bestFitness);
      logger.info("Average fitness: {}", averageFitness);
      logger.info("Best survival rate: {}", bestSurvivalRate);
    }
    logger.info("Diversity: {}", diversity);
    this.bestFitness.add(bestFitness);
    this.averageFitness.add(averageFitness);
    this.diversity.add(diversity);
    this.diversityOld.add(diversityOld);
  }

  protected void plotRankedPopulation() {
    var colors =
        new ArrayList<>(
            List.of(
                "red",
                "orange",
                "yellow",
                "gold",
                "pink",
                "purple",
                "navy",
                "blue",
                "cyan",
                "aquamarine",
                "lawngreen",
                "green"));

    try {
      var plt = Plot.create();
      for (var individual : population) {
        var x = individual.getResponseTimeH() / 60;
        var y = individual.getResponseTimeA() / 60;
        var color = "gray";
        if (individual.getRank() - 1 < colors.size()) {
          color = colors.get(individual.getRank() - 1);
        }
        plt.plot().add(List.of(x), List.of(y), "o").color(color);
      }
      plt.title("Response time");
      plt.xlabel("Urgent");
      plt.ylabel("Acute");
      plt.xlim(14.2, 15.6);
      plt.ylim(9.4, 10.3);
      plt.show();
    } catch (Exception e) {
      logger.error("Failed to plot population", e);
    }
  }

  protected void clearRunStatistics() {
    bestFitness.clear();
    averageFitness.clear();
    diversity.clear();
    diversityOld.clear();
  }
}
