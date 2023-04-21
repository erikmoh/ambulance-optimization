package no.ntnu.ambulanceallocation.optimization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.ntnu.ambulanceallocation.simulation.BaseStation;

public record Allocation(List<List<Integer>> allocation) implements Iterable<List<Integer>> {

  public Allocation(List<List<Integer>> allocation) {
    var allocationCopy = new ArrayList<List<Integer>>();

    for (var subAllocation : allocation) {
      allocationCopy.add(new ArrayList<>(subAllocation));
    }
    this.allocation = allocationCopy;
  }

  public Allocation() {
    this(List.of(new ArrayList<>(), new ArrayList<>()));
  }

  public Allocation(Allocation allocation) {
    this(allocation.allocation);
  }

  public List<Integer> getDayShiftAllocation() {
    return allocation.get(0);
  }

  public List<Integer> getNightShiftAllocation() {
    return allocation.get(1);
  }

  public List<Integer> getDayShiftAllocationSorted() {
    return getDayShiftAllocation().stream().sorted().collect(Collectors.toList());
  }

  public List<Integer> getNightShiftAllocationSorted() {
    return getNightShiftAllocation().stream().sorted().collect(Collectors.toList());
  }

  public int size() {
    return allocation.size();
  }

  public List<Integer> get(int index) {
    if (index > allocation.size()) {
      throw new IndexOutOfBoundsException(String.format("no allocation at index %d", index));
    }
    return allocation.get(index);
  }

  public int getCapacityViolationsCount() {
    var violations = 0;

    var dayShift = getDayShiftAllocation();
    var nightShift = getNightShiftAllocation();

    for (var baseStation : BaseStation.values()) {
      var dayShiftCount = Collections.frequency(dayShift, baseStation.getId());
      var nightShiftCount = Collections.frequency(nightShift, baseStation.getId());
      var maxCount = Math.max(dayShiftCount, nightShiftCount);
      var numOverCapacity = Math.max(0, maxCount - baseStation.getCapacity());
      violations += numOverCapacity;
    }
    return violations;
  }

  @Override
  public Iterator<List<Integer>> iterator() {
    return allocation.iterator();
  }

  public Stream<List<Integer>> stream() {
    return allocation.stream();
  }
}
