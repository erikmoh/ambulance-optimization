package no.ntnu.ambulanceallocation.optimization.ga;

import java.util.List;

public enum ConstraintStrategy {
  NONE {
    @Override
    void add(List<Individual> population, Individual individual) {
      population.add(individual);
    }
  },
  PENALTY {
    @Override
    void add(List<Individual> population, Individual individual) {
      population.add(individual);
    }
  },
  CHANGE {
    @Override
    void add(List<Individual> population, Individual individual) {
      population.add((Individual) individual.conformToConstraints());
    }
  };

  abstract void add(List<Individual> population, Individual individual);
}
