package no.ntnu.ambulanceallocation.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import no.ntnu.ambulanceallocation.simulation.grid.Coordinate;
import org.junit.jupiter.api.Test;

public class CoordinateTest {

  @Test
  public void generatedCoordinatesFromIdShouldBeCorrect() {
    var coordinateWithCoordinatesOnly = new Coordinate(145500, 6851500);
    var coordinateWithIdOnly = new Coordinate(21450006851000L);
    assertEquals(coordinateWithCoordinatesOnly, coordinateWithIdOnly);
  }
}
