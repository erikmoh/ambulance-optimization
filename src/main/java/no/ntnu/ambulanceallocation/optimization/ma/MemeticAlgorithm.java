package no.ntnu.ambulanceallocation.optimization.ma;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import no.ntnu.ambulanceallocation.Parameters;
import no.ntnu.ambulanceallocation.optimization.ga.GeneticAlgorithm;
import no.ntnu.ambulanceallocation.optimization.ga.Population;
import no.ntnu.ambulanceallocation.optimization.sls.NeighborhoodFunction;
import no.ntnu.ambulanceallocation.simulation.Config;
import no.ntnu.ambulanceallocation.utils.Utils;

public class MemeticAlgorithm extends GeneticAlgorithm {

  private static final AtomicInteger instanceCount = new AtomicInteger();

  static {
    instanceCount.getAndIncrement();
  }

  private static final ExecutorService executor = Executors.newCachedThreadPool();

  private final Logger logger = LoggerFactory.getLogger(MemeticAlgorithm.class);

  private final EvolutionStrategy evolutionStrategy;
  private final NeighborhoodFunction neighborhoodFunction;

  public MemeticAlgorithm(
      EvolutionStrategy evolutionStrategy, NeighborhoodFunction neighborhoodFunction) {
    this.evolutionStrategy = evolutionStrategy;
    this.neighborhoodFunction = neighborhoodFunction;
  }

  public MemeticAlgorithm(
      EvolutionStrategy evolutionStrategy,
      NeighborhoodFunction neighborhoodFunction,
      Config config) {
    super(config);
    this.evolutionStrategy = evolutionStrategy;
    this.neighborhoodFunction = neighborhoodFunction;
  }

  @Override
  public void optimize() {
    clearRunStatistics();
    population = new Population(Parameters.POPULATION_SIZE, Parameters.INITIALIZER, config);

    Runnable optimizationWrapper =
        () -> {
          logger.info("Starting {} optimizer...", getAbbreviation());

          population.evaluate();

          var generation = 0;
          var startTime = System.nanoTime();

          while (elapsedTime(startTime) < Parameters.MAX_RUNNING_TIME
              && generation < Parameters.GENERATIONS) {

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

                    offspringA.recombineWith(offspringB, Parameters.CROSSOVER_PROBABILITY);

                    offspringA.mutate(Parameters.MUTATION_PROBABILITY);
                    offspringB.mutate(Parameters.MUTATION_PROBABILITY);
                    // MA step
                    offspringA.improve(
                        evolutionStrategy, neighborhoodFunction, Parameters.IMPROVE_PROBABILITY);
                    offspringB.improve(
                        evolutionStrategy, neighborhoodFunction, Parameters.IMPROVE_PROBABILITY);

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
              // TODO Auto-generated catch block
              e.printStackTrace();
            }

            population = nextPopulation;
            population.evaluate();
            generation++;
          }

          logger.info("{} finished successfully.", getAbbreviation());
        };

    var optimizationTime = Utils.timeIt(optimizationWrapper);
    logger.info("Total {} optimization time: {} s", getAbbreviation(), optimizationTime);
  }

  @Override
  public String getAbbreviation() {
    if (instanceCount.get() == 1) {
      return "MA";
    }
    return switch (neighborhoodFunction) {
      case FORWARD -> "FMA";
      case HAMMING -> "HMA";
    };
  }
}
