package no.ntnu.ambulanceallocation.optimization.ga;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import no.ntnu.ambulanceallocation.optimization.Solution;
import no.ntnu.ambulanceallocation.optimization.initializer.Initializer;
import no.ntnu.ambulanceallocation.optimization.ma.EvolutionStrategy;
import no.ntnu.ambulanceallocation.optimization.sls.NeighborhoodFunction;
import no.ntnu.ambulanceallocation.optimization.sls.SlsSolution;
import no.ntnu.ambulanceallocation.simulation.BaseStation;
import no.ntnu.ambulanceallocation.simulation.Config;
import no.ntnu.ambulanceallocation.utils.Tuple;
import no.ntnu.ambulanceallocation.utils.Utils;

public class Individual extends Solution {

  public Individual(List<List<Integer>> chromosomes) {
    super(chromosomes);
  }

  public Individual(Initializer initializer, Config config) {
    super(initializer, config);
  }

  public Individual(Solution solution) {
    super(solution);
  }

  public void mutate(double mutationProbability) {
    var dna = new ArrayList<List<Integer>>();

    for (var chromosome : getAllocation()) {
      var newChromosome = new ArrayList<>(chromosome);

      for (var locus = 0; locus < newChromosome.size(); locus++) {
        if (Utils.randomDouble() < mutationProbability) {
          newChromosome.set(locus, Utils.randomInt(BaseStation.size()));
        }
      }
      dna.add(newChromosome);
    }
    setAllocation(dna);
  }

  public Tuple<Individual> recombineWith(Individual individual, double crossoverProbability) {
    if (Utils.randomDouble() < crossoverProbability) {
      var childA = new ArrayList<List<Integer>>();
      var childB = new ArrayList<List<Integer>>();

      for (var shift = 0; shift < getAllocation().size(); shift++) {
        var chromosomeFromA = new ArrayList<>(getAllocation().get(shift));
        var chromosomeFromB = new ArrayList<>(individual.getAllocation().get(shift));

        var crossoverPoint = 1 + Utils.randomInt(chromosomeFromA.size() - 2);

        var firstPartA = chromosomeFromA.subList(0, crossoverPoint);
        var firstPartB = chromosomeFromB.subList(0, crossoverPoint);
        var lastPartA = chromosomeFromA.subList(crossoverPoint, chromosomeFromA.size());
        var lastPartB = chromosomeFromB.subList(crossoverPoint, chromosomeFromB.size());

        childA.add(
            Stream.concat(firstPartA.stream(), lastPartB.stream()).collect(Collectors.toList()));
        childB.add(
            Stream.concat(firstPartB.stream(), lastPartA.stream()).collect(Collectors.toList()));
      }
      return new Tuple<>(new Individual(childA), new Individual(childB));
    }
    return new Tuple<>(this, individual);
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
