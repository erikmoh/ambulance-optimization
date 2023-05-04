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

  public Population() {
    population = new ArrayList<>();
  }

  public Population(int populationSize, Initializer initializer, Config config) {
    population = new ArrayList<>();
    for (int i = 0; i < populationSize; i++) {
      population.add(new Individual(initializer, config));
    }
  }

  public Population(List<Individual> population) {
    this.population = population.stream().map(Individual::new).collect(Collectors.toList());
  }

  public List<Individual> getList() {
    return population;
  }

  public void add(Individual individual, ConstraintStrategy constraintStrategy) {
    constraintStrategy.add(population, individual);
  }

  public void addAll(Population newPopulation) {
    population.addAll(newPopulation.population);
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
    return population.stream().mapToDouble(i -> i.getNovelty(population)).average().orElseThrow();
  }

  public double getDiversityOld() {
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

  public Individual best() {
    Collections.sort(population);
    return population.get(0);
  }

  public List<Individual> nonElite(int eliteSize) {
    Collections.sort(population);
    return population.subList(eliteSize, population.size());
  }

  public void evaluate() {
    population.parallelStream().forEach(Solution::getFitness);
  }

  public void sortAllocations() {
    population.parallelStream().forEach(Solution::sortAllocation);
  }

  public Tuple<Individual> selection(int tournamentSize) {
    var tournament = Utils.randomChooseN(population, tournamentSize);
    Individual best1 = null;
    Individual best2 = null;
    for (var individual : tournament) {
      if (best1 == null || individual.getFitness() < best1.getFitness()) {
        best2 = best1;
        best1 = individual;
      } else if (best2 == null || individual.getFitness() < best2.getFitness()) {
        best2 = individual;
      }
    }
    return new Tuple<>(best1, best2);
  }

  public Tuple<Individual> diversitySelection(int tournamentSize) {
    var tournament = Utils.randomChooseN(population, tournamentSize);
    tournament.forEach(Individual::setCalculateNovelty);
    Individual best1 = null;
    Individual best2 = null;
    for (var individual : tournament) {
      if (best1 == null || individual.parentSelectOver(best1, population)) {
        best2 = best1;
        best1 = individual;
      } else if (best2 == null || individual.parentSelectOver(best2, population)) {
        best2 = individual;
      }
    }
    return new Tuple<>(best1, best2);
  }

  public void reducePopulation(int popSize) {
    if (popSize >= population.size()) {
      return;
    }
    Collections.sort(population);
    population.subList(popSize, population.size()).clear();
  }

  @Override
  public Iterator<Individual> iterator() {
    return population.iterator();
  }
}
