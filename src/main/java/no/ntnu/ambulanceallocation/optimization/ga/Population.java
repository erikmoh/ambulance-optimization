package no.ntnu.ambulanceallocation.optimization.ga;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

  private List<Individual> population;

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

  public Individual bestMO() {
    evaluate();
    var layers = rankPopulation();
    var paretoFront = layers.get(0);
    paretoFront.sort(Comparator.comparing(Individual::getFitness));
    return paretoFront.get(0);
  }

  public List<Individual> nonElite(int eliteSize) {
    Collections.sort(population);
    return population.subList(eliteSize, population.size());
  }

  public void evaluate() {
    population.parallelStream().forEach(Solution::getFitness);
  }

  public void evaluateMO() {
    evaluate();
    rankPopulation();
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

  public Tuple<Individual> selectionMO(int tournamentSize) {
    var tournament = Utils.randomChooseN(population, tournamentSize);
    Individual best1 = null;
    Individual best2 = null;
    for (var individual : tournament) {
      if (best1 == null || individual.betterThan(best1)) {
        best2 = best1;
        best1 = individual;
      } else if (best2 == null || individual.betterThan(best2)) {
        best2 = individual;
      }
    }
    return new Tuple<>(best1, best2);
  }

  private List<List<Individual>> rankPopulation() {
    population.sort(Comparator.comparing(Individual::getResponseTimeA));
    var fronts = new ArrayList<List<Individual>>();
    fronts.add(new ArrayList<>());
    for (var individual : population) {
      for (int i = 0; i < fronts.size(); i++) {
        var front = fronts.get(i);
        var dominated = false;
        for (int j = front.size() - 1; j >= 0; j--) {
          var ranked = front.get(j);
          if (ranked.dominates(individual)) {
            dominated = true;
            break;
          }
        }
        if (!dominated) {
          individual.setRank(i + 1);
          front.add(individual);
          break;
        } else if (i + 1 == fronts.size()) {
          individual.setRank(i + 2);
          fronts.add(new ArrayList<>(List.of(individual)));
          break;
        }
      }
    }
    updateCrowdingDistance(fronts);
    return fronts;
  }

  private void updateCrowdingDistance(List<List<Individual>> layers) {
    for (var layer : layers) {
      for (var individual : layer) {
        individual.setCrowdingDistance(0.0);
      }
      layer.sort(Comparator.comparing(Individual::getResponseTimeA).reversed());
      layer.get(0).setCrowdingDistance(Double.MAX_VALUE);
      layer.get(layer.size() - 1).setCrowdingDistance(Double.MAX_VALUE);
      for (int i = 1; i < layer.size() - 1; i++) {
        var individual = layer.get(i);
        var before = layer.get(i - 1);
        var after = layer.get(i + 1);
        var crowdingDistance =
            individual.getCrowdingDistance()
                + ((before.getResponseTimeA() - after.getResponseTimeA()) / 1_000);
        individual.setCrowdingDistance(crowdingDistance);
      }
      layer.sort(Comparator.comparing(Individual::getResponseTimeH).reversed());
      layer.get(0).setCrowdingDistance(Double.MAX_VALUE);
      layer.get(layer.size() - 1).setCrowdingDistance(Double.MAX_VALUE);
      for (int i = 1; i < layer.size() - 1; i++) {
        var genotype = layer.get(i);
        var before = layer.get(i - 1);
        var after = layer.get(i + 1);
        var crowdingDistance =
            genotype.getCrowdingDistance()
                + ((before.getResponseTimeH() - after.getResponseTimeH()) / 1_000);
        genotype.setCrowdingDistance(crowdingDistance);
      }
    }
  }

  private void sortRankedPopulation(List<List<Individual>> layers) {
    var sortedPopulation = new ArrayList<Individual>();
    for (var layer : layers) {
      layer.sort(Comparator.comparing(Individual::getCrowdingDistance).reversed());
      var numExtra = sortedPopulation.size() + layer.size() - population.size();
      if (numExtra <= 0) {
        sortedPopulation.addAll(layer);
      } else {
        for (var p : layer) {
          sortedPopulation.add(p);
          if (sortedPopulation.size() == population.size()) {
            break;
          }
        }
        break;
      }
    }
    population = sortedPopulation;
  }

  public void reducePopulation(int popSize) {
    if (popSize >= population.size()) {
      return;
    }
    Collections.sort(population);
    population.subList(popSize, population.size()).clear();
  }

  public void reduceRankedPopulation(int popSize) {
    evaluateMO();
    var layers = rankPopulation();
    if (popSize >= population.size()) {
      return;
    }
    sortRankedPopulation(layers);
    population.subList(popSize, population.size()).clear();
  }

  @Override
  public Iterator<Individual> iterator() {
    return population.iterator();
  }
}
