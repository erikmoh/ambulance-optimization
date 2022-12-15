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

          var generation = 0;
          var startTime = System.nanoTime();

          while (generation < Parameters.GENERATIONS) {

            printAndSaveSummary(logger, generation, population);

            var elite = population.elite(Parameters.ELITE_SIZE);
            var nextPopulation = new Population(elite);

            var countDownLatch =
                new CountDownLatch(Parameters.POPULATION_SIZE - Parameters.ELITE_SIZE);

            for (var i = 0; i < ((Parameters.POPULATION_SIZE - Parameters.ELITE_SIZE) / 2); i++) {
              executor.execute(
                  () -> {
                    var parents = population.selection(Parameters.TOURNAMENT_SIZE);
                    var offspringA = parents.first();
                    var offspringB = parents.second();

                    var offspring =
                        offspringA.recombineWith(offspringB, Parameters.CROSSOVER_PROBABILITY);
                    offspringA = offspring.first();
                    offspringB = offspring.second();

                    offspringA.mutate(Parameters.MUTATION_PROBABILITY);
                    offspringB.mutate(Parameters.MUTATION_PROBABILITY);

                    synchronized (nextPopulation) {
                      if (nextPopulation.size() < Parameters.POPULATION_SIZE) {
                        nextPopulation.add(offspringA);
                        countDownLatch.countDown();
                        if (nextPopulation.size() < Parameters.POPULATION_SIZE) {
                          nextPopulation.add(offspringB);
                          countDownLatch.countDown();
                        }
                      }
                    }
                  });
            }
            try {
              countDownLatch.await();
            } catch (InterruptedException e) {
              // TODO Auto-generated catch block
              e.printStackTrace();
            }

            population = nextPopulation;
            population.evaluate();
            generation++;
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
    logger.info("Best fitness: {}", bestFitness);
    logger.info("Average fitness: {}", averageFitness);
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
