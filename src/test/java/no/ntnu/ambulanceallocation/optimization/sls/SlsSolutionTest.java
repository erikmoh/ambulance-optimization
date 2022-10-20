package no.ntnu.ambulanceallocation.optimization.sls;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class SlsSolutionTest {

  @Test
  public void comparableBasedOnFitnessShouldWork() {
    var solution1 = new SlsSolution();
    var solution2 = new SlsSolution();

    var solutions = Arrays.asList(solution1, solution2);
    Collections.sort(solutions);
    var bestSolution = solutions.get(0);
    var worstSolution = solutions.get(1);
    assertTrue(bestSolution.compareTo(worstSolution) <= 0);
  }
}
