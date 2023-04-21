package no.ntnu.ambulanceallocation.optimization;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import no.ntnu.ambulanceallocation.optimization.ga.ConstraintStrategy;
import no.ntnu.ambulanceallocation.optimization.initializer.Initializer;
import no.ntnu.ambulanceallocation.simulation.BaseStation;
import no.ntnu.ambulanceallocation.simulation.Config;
import no.ntnu.ambulanceallocation.simulation.Simulation;

public abstract class Solution implements Comparable<Solution> {

  private Allocation allocation;
  private double fitness = 0.0;
  private boolean hasAllocationChanged = true;
  private Config config = Config.defaultConfig();

  public Solution(Initializer initializer, Config config) {
    this.config = config;
    setAllocation(
        List.of(
            initializer.initialize(config.NUMBER_OF_AMBULANCES_DAY()),
            initializer.initialize(config.NUMBER_OF_AMBULANCES_NIGHT())));
  }

  protected Solution(Initializer initializer) {
    setAllocation(
        List.of(
            initializer.initialize(config.NUMBER_OF_AMBULANCES_DAY()),
            initializer.initialize(config.NUMBER_OF_AMBULANCES_NIGHT())));
  }

  public Solution(List<List<Integer>> allocations) {
    setAllocation(allocations);
  }

  public Solution(Solution solution) {
    config = solution.config;
    fitness = solution.fitness;
    allocation = new Allocation(solution.allocation);
    hasAllocationChanged = solution.hasAllocationChanged;
  }

  public void copy(Solution solution) {
    config = solution.config;
    fitness = solution.fitness;
    allocation = new Allocation(solution.allocation);
    hasAllocationChanged = solution.hasAllocationChanged;
  }

  public double getFitness() {
    if (hasAllocationChanged) {
      calculateFitness();
      hasAllocationChanged = false;
    }
    return fitness;
  }

  private void calculateFitness() {
    var simulationResults = Simulation.withConfig(config).simulate(allocation);

    var violations =
        config.CONSTRAINT_STRATEGY().equals(ConstraintStrategy.PENALTY)
            ? allocation.getCapacityViolationsCount()
            : 0;

    var simulatedFitness =
        config.USE_URGENCY_FITNESS()
            ? 1 - simulationResults.averageSurvivalRate()
            : simulationResults.averageResponseTimes();

    var penaltyFactor = config.USE_URGENCY_FITNESS() ? 0.01 : 10;

    fitness = simulatedFitness + (violations * penaltyFactor);
  }

  public Allocation getAllocation() {
    return allocation;
  }

  public List<Integer> getDayShiftAllocation() {
    return allocation.getDayShiftAllocation();
  }

  public List<Integer> getNightShiftAllocation() {
    return allocation.getNightShiftAllocation();
  }

  protected void setFitness(double fitness) {
    this.fitness = fitness;
  }

  protected void setAllocation(int subAllocation, int variable, int variableValue) {
    var previousValue = this.allocation.get(subAllocation).set(variable, variableValue);
    hasAllocationChanged = !previousValue.equals(variableValue);
  }

  protected void setAllocation(List<List<Integer>> allocation) {
    var newAllocation = new Allocation(allocation);

    if (this.allocation != null && this.allocation.equals(newAllocation)) {
      return;
    }

    this.allocation = newAllocation;
    hasAllocationChanged = true;
  }

  public Solution conformToConstraints() {
    var dayShift = getDayShiftAllocation();
    var nightShift = getNightShiftAllocation();

    for (var baseStation : BaseStation.values()) {
      var id = baseStation.getId();

      var dayOverCapacity =
          Math.max(0, Collections.frequency(dayShift, id) - baseStation.getCapacity());
      var nightOverCapacity =
          Math.max(0, Collections.frequency(nightShift, id) - baseStation.getCapacity());

      if (dayOverCapacity > 0 || nightOverCapacity > 0) {
        Collections.nCopies(dayOverCapacity, id)
            .forEach(
                i -> {
                  dayShift.remove(i);
                  dayShift.add(closestAvailableBaseStation(baseStation, dayShift).getId());
                });
        Collections.nCopies(nightOverCapacity, id)
            .forEach(
                i -> {
                  nightShift.remove(i);
                  nightShift.add(closestAvailableBaseStation(baseStation, nightShift).getId());
                });
      }
    }
    return this;
  }

  private BaseStation closestAvailableBaseStation(BaseStation baseStation, List<Integer> shift) {
    return Arrays.stream(BaseStation.values())
        .filter(b -> !b.equals(baseStation))
        .filter(b -> b.getCapacity() - Collections.frequency(shift, b.getId()) > 0)
        .min(Comparator.comparingDouble(b -> baseStation.getCoordinate().timeTo(b.getCoordinate())))
        .orElseThrow();
  }

  @Override
  public int compareTo(@Nonnull Solution otherSolution) {
    return Comparator.comparing(Solution::getFitness).compare(this, otherSolution);
  }

  @Override
  public String toString() {
    return String.format(
        "Solution(f=%.1f, a_day=%s, a_night=%s)",
        getFitness(), getDayShiftAllocation(), getNightShiftAllocation());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Solution solution)) return false;
    return Stream.concat(
            getDayShiftAllocation().stream().sorted(), getNightShiftAllocation().stream().sorted())
        .equals(
            Stream.concat(
                solution.getDayShiftAllocation().stream().sorted(),
                solution.getNightShiftAllocation().stream().sorted()));
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        Stream.concat(
            getDayShiftAllocation().stream().sorted(),
            getNightShiftAllocation().stream().sorted()));
  }
}
