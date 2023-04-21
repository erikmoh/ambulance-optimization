package no.ntnu.ambulanceallocation.optimization.ga;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import no.ntnu.ambulanceallocation.optimization.Solution;
import no.ntnu.ambulanceallocation.optimization.initializer.Initializer;
import no.ntnu.ambulanceallocation.simulation.BaseStation;
import no.ntnu.ambulanceallocation.simulation.Config;
import no.ntnu.ambulanceallocation.utils.Tuple;
import no.ntnu.ambulanceallocation.utils.Utils;

public class Population implements Iterable<Individual> {

  private final List<Individual> population;

  public Population(int populationSize, Initializer initializer, Config config) {
    population = new ArrayList<>();
    for (int i = 0; i < populationSize; i++) {
      population.add(new Individual(initializer, config));
    }
  }

  public Population(List<Individual> population) {
    this.population = population.stream().map(Individual::new).collect(Collectors.toList());
  }

  public boolean add(Individual individual, ConstraintStrategy constraintStrategy) {
    var popSize = population.size();
    constraintStrategy.add(population, new Individual(individual));
    return popSize != population.size();
  }

  public Individual get(int index) {
    return population.get(index);
  }

  public int size() {
    return population.size();
  }

  public double getAverageFitness() {
    return population.stream().mapToDouble(Individual::getFitness).average().orElseThrow();
  }

  public double getBestFitness() {
    return population.stream().mapToDouble(Individual::getFitness).min().orElseThrow();
  }

  public double getDiversity() {
    var bins = BaseStation.size();
    var entropy = 0.0;
    var numChromosomes = population.get(0).getAllocation().size();

    for (var chromosomeNum = 0; chromosomeNum < numChromosomes; chromosomeNum++) {

      var total = population.size() * population.get(0).getAllocation().get(chromosomeNum).size();

      var finalChromosomeNum = chromosomeNum;
      var occurrences =
          population.stream()
              .flatMap(individual -> individual.getAllocation().get(finalChromosomeNum).stream())
              .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
              .values();

      var information =
          occurrences.parallelStream()
              .mapToDouble(occurrence -> occurrence / (double) total)
              .map(probability -> probability * Utils.logn(probability, bins));

      entropy -= information.sum();
    }
    return entropy / numChromosomes;
  }

  public List<Individual> elite(int eliteSize) {
    Collections.sort(population);
    return population.subList(0, eliteSize);
  }

  public void evaluate() {
    population.parallelStream().forEach(Solution::getFitness);
  }

  public Tuple<Individual> selection(int tournamentSize) {
    var tournament = Utils.randomChooseN(population, tournamentSize);
    Collections.sort(tournament);
    return new Tuple<>(tournament.subList(0, 2));
  }

  @Override
  public Iterator<Individual> iterator() {
    return population.iterator();
  }
}
