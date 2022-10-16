package no.ntnu.ambulanceallocation.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import no.ntnu.ambulanceallocation.simulation.grid.Coordinate;
import no.ntnu.ambulanceallocation.simulation.grid.GridIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class CoordinateTest {

  private static final List<Coordinate> coordinates = new ArrayList<>();

  @BeforeAll
  public static void setup() {
    coordinates.clear();
    coordinates.addAll(GridIO.getGridCoordinates());
  }

  @Test
  public void generatedIdsShouldEqualOfficialIds() {
    var testResult = true;
    for (var coordinate : coordinates) {
      if (!coordinate.equals(new Coordinate(coordinate.x(), coordinate.y()))) {
        testResult = false;
        break;
      }
    }

    assertTrue(testResult);
  }

  @Test
  public void generatedCoordinatesFromIdShouldBeCorrect() {
    var coordinateWithCoordinatesOnly = new Coordinate(145500, 6851500);
    var coordinateWithIdOnly = new Coordinate(21450006851000L);
    assertEquals(coordinateWithCoordinatesOnly, coordinateWithIdOnly);
  }
}
