package no.ntnu.ambulanceallocation.optimization.ga;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import no.ntnu.ambulanceallocation.Parameters;
import no.ntnu.ambulanceallocation.experiments.Result;
import no.ntnu.ambulanceallocation.optimization.Optimizer;
import no.ntnu.ambulanceallocation.optimization.Solution;
import no.ntnu.ambulanceallocation.simulation.Config;
import no.ntnu.ambulanceallocation.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeneticAlgorithm implements Optimizer {

  private static final ExecutorService executor = Executors.newCachedThreadPool();

  private final Logger logger = LoggerFactory.getLogger(GeneticAlgorithm.class);

  private final List<Double> bestFitness = new ArrayList<>();
  private final List<Double> averageFitness = new ArrayList<>();
  private final List<Double> diversity = new ArrayList<>();

  protected Config config;
  protected Population population;

  public GeneticAlgorithm() {
    this.config = Config.defaultConfig();
  }

  public GeneticAlgorithm(Config config) {
    this.config = config;
  }

  @Override
  public Solution getOptimalSolution() {
    return population.elite(Parameters.ELITE_SIZE).get(0);
  }

  @Override
  public void optimize() {
    clearRunStatistics();
    population = new Population(Parameters.POPULATION_SIZE, Parameters.INITIALIZER, config);

    Runnable optimizationWrapper =
        () -> {
          logger.info("Starting GA optimizer...");

          population.evaluate();

          var generation = 1;
          var startTime = System.nanoTime();
          var nonEliteSize = Parameters.POPULATION_SIZE - Parameters.ELITE_SIZE;

          printAndSaveSummary(logger, generation, population);

          while (generation < Parameters.GENERATIONS
              && elapsedTime(startTime) < Parameters.MAX_RUNNING_TIME) {

            var nextPopulation = new Population();
            if (Parameters.ELITE_SIZE > 0) {
              var elite = population.elite(Parameters.ELITE_SIZE);
              nextPopulation.addAll(elite);
            }

            var countDownLatch = new CountDownLatch(nonEliteSize);

            for (var i = 0; i < ((nonEliteSize) / 2); i++) {
              executor.execute(
                  () -> {
                    var parents = population.selection(Parameters.TOURNAMENT_SIZE);
                    var offspringA = new Individual(parents.first());
                    var offspringB = new Individual(parents.second());

                    offspringA.recombineWith(offspringB, Parameters.CROSSOVER_PROBABILITY);

                    offspringA.mutate(Parameters.MUTATION_PROBABILITY);
                    offspringB.mutate(Parameters.MUTATION_PROBABILITY);

                    synchronized (nextPopulation) {
                      if (nextPopulation.size() < Parameters.POPULATION_SIZE) {
                        nextPopulation.add(offspringA, config.CONSTRAINT_STRATEGY());
                        countDownLatch.countDown();
                        if (nextPopulation.size() < Parameters.POPULATION_SIZE) {
                          nextPopulation.add(offspringB, config.CONSTRAINT_STRATEGY());
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

            if (Parameters.ELITE_SIZE > 0) {
              population = nextPopulation;
              population.evaluate();
            } else {
              nextPopulation.evaluate();
              population.addAll(nextPopulation);
              population.reducePopulation(Parameters.POPULATION_SIZE);
            }
            generation++;

            printAndSaveSummary(logger, generation, population);
          }

          logger.info("GA finished successfully.");
        };

    var optimizationTime = Utils.timeIt(optimizationWrapper);
    logger.info("Total GA optimization time: " + optimizationTime + " seconds");
  }

  @Override
  public Result getRunStatistics() {
    var runStatistics = new Result();
    runStatistics.saveColumn("best", bestFitness);
    runStatistics.saveColumn("average", averageFitness);
    runStatistics.saveColumn("diversity", diversity);
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
    var bestFitness = population.getBestFitness();
    var averageFitness = population.getAverageFitness();
    var diversity = population.getDiversity();
    if (config.USE_URGENCY_FITNESS()) {
      logger.info("Best fitness: {}", 1.0 - bestFitness);
      logger.info("Average fitness: {}", 1.0 - averageFitness);
    } else {
      logger.info("Best fitness: {}", bestFitness);
      logger.info("Average fitness: {}", averageFitness);
    }
    logger.info("Diversity: {}", diversity);
    this.bestFitness.add(bestFitness);
    this.averageFitness.add(averageFitness);
    this.diversity.add(diversity);
  }

  protected void clearRunStatistics() {
    bestFitness.clear();
    averageFitness.clear();
    diversity.clear();
  }
}
