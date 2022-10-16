package no.ntnu.ambulanceallocation.simulation.grid;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class GridIO {

  private static final List<Coordinate> gridCoordinates = new ArrayList<>();

  static {
    var coordinatesFilesList = List.of("data/oslo.csv", "data/akershus.csv");
    for (var coordinatesFile : coordinatesFilesList) {
      loadCoordinatesFile(GridIO.class.getClassLoader().getResource(coordinatesFile));
    }
  }

  private static void loadCoordinatesFile(URL coordinatesFilePath) {
    if (coordinatesFilePath == null) {
      throw new IllegalArgumentException("Coordinates file not found!");
    }

    try (var scanner = new Scanner(new File(coordinatesFilePath.toURI()))) {
      scanner.nextLine(); // Skip header row
      var line = scanner.nextLine();

      while (scanner.hasNextLine()) {
        line = scanner.nextLine();
        var values = Arrays.asList(line.split(","));

        var gridCoordinate =
            new Coordinate(
                Integer.parseInt(values.get(0)),
                Integer.parseInt(values.get(1)),
                Long.parseLong(values.get(4)));
        gridCoordinates.add(gridCoordinate);
      }
    } catch (IOException | URISyntaxException e) {
      e.printStackTrace();
    }
  }

  public static List<Coordinate> getGridCoordinates() {
    return gridCoordinates;
  }
}
