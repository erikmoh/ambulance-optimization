package no.ntnu.ambulanceallocation.optimization.sls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import no.ntnu.ambulanceallocation.optimization.Solution;
import no.ntnu.ambulanceallocation.optimization.initializer.Initializer;
import no.ntnu.ambulanceallocation.optimization.initializer.Random;
import no.ntnu.ambulanceallocation.simulation.BaseStation;
import no.ntnu.ambulanceallocation.simulation.Config;
import no.ntnu.ambulanceallocation.utils.Utils;

public class SlsSolution extends Solution {

  private static final Initializer initializer = new Random();
  private static final int MAX_VALUE = BaseStation.size();

  public SlsSolution() {
    super(initializer);
  }

  public SlsSolution(Config config) {
    super(initializer, config);
  }

  private SlsSolution(List<List<Integer>> variables) {
    super(variables);
  }

  public SlsSolution(Solution solution) {
    super(solution);
  }

  private SlsSolution(SlsSolution root, int variableSet, int variable) {
    this(root);

    var allocation = new ArrayList<>(getAllocation().allocation());
    allocation.set(variableSet, forwardStep(getAllocation().get(variableSet), variable));
    setAllocation(allocation);
  }

  private SlsSolution(SlsSolution root, int variableSet, int variable, int variableValue) {
    this(root);

    setAllocation(variableSet, variable, variableValue);
  }

  public void noiseStep() {
    var randomVariableSet = Utils.randomInt(getAllocation().size());
    var randomVariable = Utils.randomIndexOf(getAllocation().get(randomVariableSet));
    var randomNeighbor = new SlsSolution(this, randomVariableSet, randomVariable);

    copy(randomNeighbor);
  }

  public SlsSolution greedyStep(NeighborhoodFunction neighborhoodFunction) {
    var neighborhood =
        switch (neighborhoodFunction) {
          case FORWARD -> getForwardNeighborhood();
          case HAMMING -> getHammingNeighborhood();
        };

    neighborhood.parallelStream().forEach(Solution::getFitness);
    Collections.sort(neighborhood);

    var bestNeighbor = neighborhood.get(0);
    copy(bestNeighbor);

    return this;
  }

  public void restartStep() {
    copy(new SlsSolution());
  }

  private List<SlsSolution> getForwardNeighborhood() {
    var neighborhood = new ArrayList<SlsSolution>();

    for (var variableSet = 0; variableSet < getAllocation().size(); variableSet++) {

      var size = getAllocation().get(variableSet).size();
      for (var rootVariable = 0; rootVariable < size; rootVariable++) {
        neighborhood.add(new SlsSolution(this, variableSet, rootVariable));
      }
    }
    return neighborhood;
  }

  private List<SlsSolution> getHammingNeighborhood() {
    var neighborhood = new ArrayList<SlsSolution>();

    for (var variableSet = 0; variableSet < getAllocation().size(); variableSet++) {
      for (var variable = 0; variable < getAllocation().get(variableSet).size(); variable++) {
        for (var variableValue = 0; variableValue < MAX_VALUE; variableValue++) {

          var currentValue = getAllocation().get(variableSet).get(variable);
          if (variableValue != currentValue) {
            neighborhood.add(new SlsSolution(this, variableSet, variable, variableValue));
          }
        }
      }
    }
    return neighborhood;
  }

  private List<Integer> forwardStep(List<Integer> variableSet, int variable) {
    var newVariableSet = new ArrayList<>(variableSet);
    var value = newVariableSet.get(variable);

    value = (value + 1) % MAX_VALUE;
    newVariableSet.set(variable, value);

    return newVariableSet;
  }
}
