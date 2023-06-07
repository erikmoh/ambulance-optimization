package no.ntnu.ambulanceallocation.optimization.ga;

import static no.ntnu.ambulanceallocation.Parameters.CROSSOVER_PROBABILITY;
import static no.ntnu.ambulanceallocation.Parameters.CROSSOVER_TUNE_START;
import static no.ntnu.ambulanceallocation.Parameters.CROWDING;
import static no.ntnu.ambulanceallocation.Parameters.DISTINCT;
import static no.ntnu.ambulanceallocation.Parameters.DIVERSIFY_GENERATIONS;
import static no.ntnu.ambulanceallocation.Parameters.DIVERSITY_LIMIT;
import static no.ntnu.ambulanceallocation.Parameters.ELITE_SIZE;
import static no.ntnu.ambulanceallocation.Parameters.GENERATIONS_COMBINED;
import static no.ntnu.ambulanceallocation.Parameters.GENERATIONS_ISLAND;
import static no.ntnu.ambulanceallocation.Parameters.INITIALIZER;
import static no.ntnu.ambulanceallocation.Parameters.ISLANDS;
import static no.ntnu.ambulanceallocation.Parameters.MAX_RUNNING_TIME;
import static no.ntnu.ambulanceallocation.Parameters.MUTATION_PROBABILITY;
import static no.ntnu.ambulanceallocation.Parameters.MUTATION_TUNE_START;
import static no.ntnu.ambulanceallocation.Parameters.POPULATION_SIZE;
import static no.ntnu.ambulanceallocation.Parameters.TOURNAMENT_SIZE;
import static no.ntnu.ambulanceallocation.Parameters.TOURNAMENT_TUNE_END;
import static no.ntnu.ambulanceallocation.utils.Utils.nextBoolean;
import static no.ntnu.ambulanceallocation.utils.Utils.randomDouble;

import com.github.sh0nk.matplotlib4j.Plot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import no.ntnu.ambulanceallocation.utils.Tuple;
import no.ntnu.ambulanceallocation.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeneticAlgorithm implements Optimizer {

  private static final ExecutorService executor = Executors.newCachedThreadPool();

  private final Logger logger = LoggerFactory.getLogger(GeneticAlgorithm.class);

  private final List<Double> bestFitness = new ArrayList<>();
  private final List<Double> averageFitness = new ArrayList<>();
  private final List<Double> diversity = new ArrayList<>();
  private final List<Double> diversityOld = new ArrayList<>();

  protected Config config;
  protected Population population;
  protected Set<Population> populationIslands;

  public GeneticAlgorithm() {
    this.config = Config.defaultConfig();
  }

  public GeneticAlgorithm(Config config) {
    this.config = config;
  }

  @Override
  public Solution getOptimalSolution() {
    return population.best();
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
          var noImprovementCount = 0;
          var bestFitness = 0.0;

          logger.info("Starting GA optimizer...");
          population.evaluate();
          printAndSaveSummary(logger, generation, population);

          while (combinedGeneration < GENERATIONS_COMBINED
              && elapsedTime(startTime) < MAX_RUNNING_TIME) {

            // plotPopulation();

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
              bestFitness = 0.0;
              printAndSaveSummary(logger, generation, population);
            }

            var nextPopulation = new Population();
            var nonElite = POPULATION_SIZE - ELITE_SIZE;
            if (DISTINCT) {
              nonElite = POPULATION_SIZE;
            }
            var nonEliteFinal = nonElite;
            var countDownLatch = new CountDownLatch(nonEliteFinal);
            // var tournamentSize = getTournamentSize(generation);
            var crossoverP = getCrossoverProbability(generation);
            var mutationP = getMutationProbability(generation);
            var f = getScalingFactor(population.getDiversity());

            for (var i = 0; i < nonEliteFinal / 2; i++) {
              executor.execute(
                  () -> {
                    var parents = population.selection(TOURNAMENT_SIZE);
                    var offspringA = new Individual(parents.first());
                    var offspringB = new Individual(parents.second());

                    offspringA.recombineWith(offspringB, crossoverP);

                    offspringA.mutate(mutationP);
                    offspringB.mutate(mutationP);

                    if (CROWDING) {
                      var offspring = competeCrowding(parents, offspringA, offspringB, f);
                      offspringA = offspring.first();
                      offspringB = offspring.second();
                    }

                    synchronized (nextPopulation) {
                      if (nextPopulation.size() < nonEliteFinal) {
                        if (!DISTINCT || offspringA.hasChanged()) {
                          nextPopulation.add(offspringA, config.CONSTRAINT_STRATEGY());
                        }
                        countDownLatch.countDown();
                        if (nextPopulation.size() < nonEliteFinal) {
                          if (!DISTINCT || offspringB.hasChanged()) {
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

            nextPopulation.evaluate();
            if (DISTINCT) {
              population.reducePopulation(
                  Math.max(ELITE_SIZE, POPULATION_SIZE - nextPopulation.size()));
              population.addAll(nextPopulation);
              population.reducePopulation(POPULATION_SIZE);
            } else {
              population.reducePopulation(ELITE_SIZE);
              population.addAll(nextPopulation);
            }

            generation++;
            if (runningCombined) combinedGeneration++;
            var newBest = 1.0 - population.getBestFitness();
            if (newBest > bestFitness) {
              bestFitness = newBest;
              noImprovementCount = 0;
            } else {
              noImprovementCount++;
            }

            printAndSaveSummary(logger, generation, population);

            if (population.getDiversity() < DIVERSITY_LIMIT
                && noImprovementCount > DIVERSIFY_GENERATIONS) {
              diversify();
              noImprovementCount = 0;
            }
            /*if (noImprovementCount > RESET_GENERATIONS
                || (generation == 50 && bestFitness < 0.882)
                || (generation == 100 && bestFitness < 0.883)) {
              generation = 1;
              bestFitness = 0.0;
              reset(generation);
            }*/
          }

          if (!runningCombined || ISLANDS == 0) {
            populationIslands.add(population);
            population =
                populationIslands.stream()
                    .min(Comparator.comparingDouble(Population::getBestFitness))
                    .orElse(population);
            printAndSaveSummary(logger, generation, population);
          }
          logger.info("GA finished successfully.");
        };

    var optimizationTime = Utils.timeIt(optimizationWrapper);
    logger.info("Total GA optimization time: " + optimizationTime + " seconds");
  }

  private Tuple<Individual> competeCrowding(
      Tuple<Individual> parents, Individual c1, Individual c2, double f) {
    var p1 = parents.first();
    var p2 = parents.second();
    Individual w1;
    Individual w2;
    if (p1.distance(c1) + p2.distance(c2) < p1.distance(c2) + p2.distance(c1)) {
      w1 = compete(p1, c1, f);
      w2 = compete(p2, c2, f);
    } else {
      w1 = compete(p1, c2, f);
      w2 = compete(p2, c1, f);
    }
    return new Tuple<>(w1, w2);
  }

  private Individual competeDeterministic(Individual p, Individual c) {
    if (c.getFitness() < p.getFitness()) {
      return c;
    } else if (p.getFitness() < c.getFitness()) {
      return p;
    }
    return nextBoolean() ? p : c;
  }

  private Individual competeProbabilistic(Individual p, Individual c) {
    var prob = c.getFitness() / (c.getFitness() + p.getFitness());
    // reverse p and c because minimization problem
    if (prob > randomDouble()) {
      return p;
    }
    return c;
  }

  private Individual compete(Individual p, Individual c, double f) {
    var prob = 0.5; // of picking p
    if (c.getFitness() > p.getFitness()) {
      prob = c.getFitness() / (c.getFitness() + (f * p.getFitness()));
    } else if (p.getFitness() > c.getFitness()) {
      prob = (f * c.getFitness()) / ((f * c.getFitness()) + p.getFitness());
    }
    return prob > randomDouble() ? p : c;
  }

  private void newIsland() {
    population = new Population(POPULATION_SIZE, INITIALIZER, config);
    logger.info("Started new island.");
    population.evaluate();
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
      pop.reducePopulation(POPULATION_SIZE / ISLANDS);
      combinedPopulation.addAll(pop);
    }
    population = combinedPopulation;
    logger.info("Combined islands.");
  }

  private void diversify() {
    logger.info("Diversifying");
    population.nonElite(ELITE_SIZE).forEach(i -> i.mutate(0.3));
    population.evaluate();
  }

  private void reset(int generation) {
    populationIslands.add(population);
    newIsland();
    printAndSaveSummary(logger, generation, population);
  }

  private double getCrossoverProbability(int generation) {
    var generationReduction = generation / 200.0;
    return Math.max(CROSSOVER_PROBABILITY, CROSSOVER_TUNE_START - generationReduction);
  }

  private double getMutationProbability(int generation) {
    var generationReduction = generation / 200.0;
    return Math.max(MUTATION_PROBABILITY, MUTATION_TUNE_START - generationReduction);
  }

  private int getTournamentSize(int generation) {
    var startGeneration = 1;
    var endGeneration = 250;

    // Calculate the exponential growth factor
    var growthFactor =
        Math.pow(
            (double) TOURNAMENT_TUNE_END / TOURNAMENT_SIZE,
            1.0 / (endGeneration - startGeneration));

    // Calculate the tournament size based on the current generation
    var tournamentSize = TOURNAMENT_SIZE * Math.pow(growthFactor, (generation - startGeneration));
    return (int) Math.floor(tournamentSize);
  }

  private double getScalingFactor(double diversity) {
    return diversity / 20;
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
    var best = population.best();
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
    logger.info("DiversityOld: {}", diversityOld);
    this.bestFitness.add(bestFitness);
    this.averageFitness.add(averageFitness);
    this.diversity.add(diversity);
    this.diversityOld.add(diversityOld);
  }

  protected void plotPopulation() {
    try {
      var plt = Plot.create();
      var x = new ArrayList<Integer>();
      var y = new ArrayList<Double>();
      for (var i = 0; i < population.size(); i++) {
        x.add(i);
        // x.add(population.get(i).getNovelty(population.getList()));
        /*x.add(
        population.get(i).getDayShiftAllocation().subList(10, 15).stream()
            .mapToInt(Integer::intValue)
            .sum());*/
        y.add(1.0 - population.get(i).getFitness());
      }
      plt.plot().add(x, y, "o");
      plt.title("Fitness distribution");
      // plt.ylim(0.860, 0.884);
      plt.show();
    } catch (Exception e) {
      return;
    }
  }

  protected void clearRunStatistics() {
    bestFitness.clear();
    averageFitness.clear();
    diversity.clear();
    diversityOld.clear();
  }
}
