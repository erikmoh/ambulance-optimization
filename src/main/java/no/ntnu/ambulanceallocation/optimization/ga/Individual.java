package no.ntnu.ambulanceallocation.optimization.ga;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import no.ntnu.ambulanceallocation.optimization.Solution;
import no.ntnu.ambulanceallocation.optimization.initializer.Initializer;
import no.ntnu.ambulanceallocation.optimization.ma.EvolutionStrategy;
import no.ntnu.ambulanceallocation.optimization.sls.NeighborhoodFunction;
import no.ntnu.ambulanceallocation.optimization.sls.SlsSolution;
import no.ntnu.ambulanceallocation.simulation.BaseStation;
import no.ntnu.ambulanceallocation.simulation.Config;
import no.ntnu.ambulanceallocation.utils.Utils;

public class Individual extends Solution {

  public Individual(Initializer initializer, Config config) {
    super(initializer, config);
  }

  public Individual(Solution solution) {
    super(solution);
  }

  public void mutate(double mutationProbability) {
    for (var chromosome : getAllocation()) {
      for (var i = 0; i < chromosome.size(); i++) {
        if (Utils.randomDouble() < mutationProbability) {
          var newInt = Utils.randomInt(BaseStation.size());
          if (chromosome.get(i) != newInt) {
            chromosome.set(i, newInt);
            allocationChanged();
          }
        }
      }
    }
  }

  public void recombineWith(Individual individual, double crossoverProbability) {
    if (Utils.randomDouble() < crossoverProbability) {
      for (var shift = 0; shift < getAllocation().size(); shift++) {
        var chromosomeFromA = getAllocation().get(shift); // .stream().sorted().toList();
        var chromosomeFromB = individual.getAllocation().get(shift); // .stream().sorted().toList();

        var crossoverPoint = 1 + Utils.randomInt(chromosomeFromA.size() - 2);

        var firstPartA = chromosomeFromA.subList(0, crossoverPoint);
        var firstPartB = chromosomeFromB.subList(0, crossoverPoint);
        var lastPartA = chromosomeFromA.subList(crossoverPoint, chromosomeFromA.size());
        var lastPartB = chromosomeFromB.subList(crossoverPoint, chromosomeFromB.size());

        var allocationA =
            Stream.concat(firstPartA.stream(), lastPartB.stream()).collect(Collectors.toList());
        var allocationB =
            Stream.concat(firstPartB.stream(), lastPartA.stream()).collect(Collectors.toList());

        getAllocation().setShiftAllocation(shift, allocationA);
        individual.getAllocation().setShiftAllocation(shift, allocationB);
      }

      if (!this.getAllocation().equals(individual.getAllocation())) {
        this.allocationChanged();
        individual.allocationChanged();
      }
    }
  }

  // Memetic method
  public void improve(
      EvolutionStrategy evolutionStrategy,
      NeighborhoodFunction neighborhoodFunction,
      double improveProbability) {

    if (Utils.randomDouble() < improveProbability) {
      switch (evolutionStrategy) {
        case DARWINIAN -> {}
        case BALDWINIAN -> {
          var bestNeighbor = getBestNeighbor(neighborhoodFunction);
          if (bestNeighbor.getFitness() > getFitness()) {
            this.setFitness(bestNeighbor.getFitness());
          }
        }
        case LAMARCKIAN -> {
          var bestNeighbor = getBestNeighbor(neighborhoodFunction);
          if (bestNeighbor.getFitness() > getFitness()) {
            copy(bestNeighbor);
          }
        }
      }
    }
  }

  // Memetic method
  private Individual getBestNeighbor(NeighborhoodFunction neighborhoodFunction) {
    var slsSolution = new SlsSolution(this);
    var bestNeighborhood = slsSolution.greedyStep(neighborhoodFunction);
    return new Individual(bestNeighborhood);
  }
}
