package no.ntnu.ambulanceallocation.simulation.grid;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import no.ntnu.ambulanceallocation.Parameters;
import no.ntnu.ambulanceallocation.utils.Tuple;

public final class DistanceIO {

  public static final String distancesFilePath =
      new File("src/main/resources/data/od.json").getAbsolutePath();
  public static final String neighborsFilePath =
      new File("src/main/resources/data/od_nearest_neighbors.json").getAbsolutePath();
  public static final Set<Coordinate> uniqueGridCoordinates = new HashSet<>();
  public static final Map<Coordinate, List<Coordinate>> coordinateNeighbors = new HashMap<>();
  public static final Map<Tuple<Coordinate>, Double> distances = new HashMap<>();
  public static final Map<String, Coordinate> coordinateCache = new HashMap<>();
  private static final Logger logger = LoggerFactory.getLogger(DistanceIO.class);

  static {
    loadDistancesFromFile();
    loadNearestNeighborsFromFile();
    coordinateCache.clear();
  }

  public static double getDistance(Coordinate from, Coordinate to) {
    if (from == to) {
      return 0.0;
    }
    if (!distances.containsKey(new Tuple<>(from, to))) {
      logger.info("Failed to find distance from {} to {}", from, to);
      return 60.0;
    }
    return distances.get(new Tuple<>(from, to));
  }

  private static Coordinate getCoordinateFromString(String coordinateString) {
    if (coordinateCache.containsKey(coordinateString)) {
      return coordinateCache.get(coordinateString);
    }

    Coordinate coordinate;
    try {
      var gridId = Long.parseLong(coordinateString);
      coordinate = new Coordinate(gridId);
    } catch (NumberFormatException e) {
      var utmCoordinates = coordinateString.split("_");
      var easting = Integer.parseInt(utmCoordinates[1]);
      var northing = Integer.parseInt(utmCoordinates[2]);
      coordinate = new Coordinate(easting, northing);
    }

    coordinateCache.put(coordinateString, coordinate);
    return coordinate;
  }

  private static void loadDistancesFromFile() {
    logger.info("Loading distances from file...");

    try {
      var distanceJsonObject = new JSONObject(Files.readString(Path.of(distancesFilePath)));

      for (var originKey : distanceJsonObject.names()) {
        var origin = getCoordinateFromString(originKey.toString());
        uniqueGridCoordinates.add(origin);
        var destinationObject = (JSONObject) distanceJsonObject.get(originKey.toString());

        if (destinationObject.names() != null) {
          for (var destKey : destinationObject.names()) {
            var time = destinationObject.getDouble(destKey.toString());
            var destination = getCoordinateFromString(destKey.toString());
            distances.put(new Tuple<>(origin, destination), (time * 60));
          }
        }
      }
    } catch (JSONException | IOException e) {
      e.printStackTrace();
      System.exit(1);
    }

    logger.info("Loaded {} distances.", distances.size());
  }

  private static void loadNearestNeighborsFromFile() {
    logger.info("Loading nearest grid coordinate neighbors from file...");

    try {
      var neighborsJsonObject = new JSONObject(Files.readString(Path.of(neighborsFilePath)));

      for (var originKey : neighborsJsonObject.names()) {
        var nearbyGridList = new ArrayList<Coordinate>();
        var origin = getCoordinateFromString(originKey.toString());
        var neighborJsonArray = neighborsJsonObject.getJSONArray(originKey.toString());

        if (neighborJsonArray.length() < 1) {
          throw new IllegalArgumentException("No neighbors found for coordinate " + origin);
        }

        for (var i = 0; i < neighborJsonArray.length(); i++) {
          var neighbor = getCoordinateFromString(neighborJsonArray.getString(i));
          if (neighbor.euclideanDistanceTo(origin) <= Parameters.COORDINATE_NEIGHBOR_DISTANCE) {
            nearbyGridList.add(neighbor);
          }
        }
        coordinateNeighbors.put(origin, nearbyGridList);
      }
    } catch (JSONException | IOException e) {
      e.printStackTrace();
      System.exit(1);
    }

    logger.info("Loaded neighbors for {} coordinates.", coordinateNeighbors.size());
  }
}
