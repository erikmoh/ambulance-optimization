package no.ntnu.ambulanceallocation.simulation.grid;

import static no.ntnu.ambulanceallocation.Parameters.DISPATCH_POLICY;
import static no.ntnu.ambulanceallocation.Parameters.PREDICTED_DEMAND_BASE_STATION;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import no.ntnu.ambulanceallocation.simulation.dispatch.DispatchPolicy;
import no.ntnu.ambulanceallocation.utils.Tuple;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DistanceIO {

  public static final String routesFilePath =
      new File("src/main/resources/data/od_paths.json").getAbsolutePath();
  public static final String neighboursFilePath =
      new File("src/main/resources/data/closest_neighbours.json").getAbsolutePath();
  private static int TRAVEL_TIME_INTERVAL; // Interval time for od_paths route coordinates
  public static final Set<Coordinate> uniqueGridCoordinates = new HashSet<>();

  public static final Map<Tuple<Coordinate>, Route> routes = new HashMap<>();
  public static final Map<Coordinate, Map<Coordinate, Double>> neighbours = new HashMap<>();
  public static final Map<String, Coordinate> coordinateCache = new HashMap<>();
  private static final Logger logger = LoggerFactory.getLogger(DistanceIO.class);

  static {
    loadRoutesFromFile();
    loadNeighboursFromFile();
    coordinateCache.clear();
  }

  public static int getTravelTimeInterval() {
    return TRAVEL_TIME_INTERVAL;
  }

  public static Route getRoute(Coordinate from, Coordinate to) {
    if (!routes.containsKey(new Tuple<>(from, to))) {
      logger.info("Failed to find route from {} to {}", from, to);
      return new Route(new ArrayList<>(), 60 * 60);
    }
    return routes.get(new Tuple<>(from, to));
  }

  public static double getDistance(Coordinate from, Coordinate to) {
    if (from == to) {
      return 60.0;
    }
    if (!routes.containsKey(new Tuple<>(from, to))) {
      logger.info("Failed to find distance from {} to {}", from, to);
      return 60.0;
    }
    return routes.get(new Tuple<>(from, to)).time();
  }

  public static Set<Coordinate> getNeighbours(Coordinate from) {
    if (!neighbours.containsKey(from)) {
      logger.info("Failed to find neighbours of {}", from);
      return new HashSet<>();
    }
    return neighbours.get(from).keySet();
  }

  public static Coordinate getCoordinateFromString(String coordinateString) {
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

  private static void loadRoutesFromFile() {
    logger.info("Loading routes from file...");

    try {
      var inputStream = new FileInputStream(routesFilePath);
      var reader = new JsonReader(new BufferedReader(new InputStreamReader(inputStream)));

      reader.beginObject();
      while (reader.hasNext()) {
        if (reader.peek().equals(JsonToken.END_OBJECT)) {
          reader.close();
          return;
        }

        handleOriginObject(reader);
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }

    logger.info("Loaded {} routes.", routes.size());
  }

  private static void handleOriginObject(JsonReader reader) throws IOException {
    var name = reader.nextName();

    if (name.equals("update_period_minutes")) {
      TRAVEL_TIME_INTERVAL = reader.nextInt();
      return;
    }

    var origin = getCoordinateFromString(name);
    uniqueGridCoordinates.add(origin);

    reader.beginObject();
    while (reader.peek().equals(JsonToken.NAME)) {
      handleDestinationObject(reader, origin);
    }

    reader.endObject();
  }

  private static void handleDestinationObject(JsonReader reader, Coordinate origin)
      throws IOException {
    var destination = getCoordinateFromString(reader.nextName());
    reader.beginObject();

    reader.nextName();
    var travelTime = reader.nextInt();

    reader.nextName();
    var route = new ArrayList<String>();
    reader.beginArray();
    while (reader.peek().equals(JsonToken.STRING)) {
      route.add(reader.nextString());
    }
    reader.endArray();

    routes.put(new Tuple<>(origin, destination), new Route(route, travelTime));

    reader.endObject();
  }

  private static void loadNeighboursFromFile() {
    if (!DISPATCH_POLICY.equals(DispatchPolicy.CoveragePredictedDemand)
        || PREDICTED_DEMAND_BASE_STATION) {
      return;
    }

    logger.info("Loading neighbours from file...");

    try {
      var neighboursJsonObject = new JSONObject(Files.readString(Path.of(neighboursFilePath)));

      for (var originKey : neighboursJsonObject.names()) {
        var origin = getCoordinateFromString(originKey.toString());
        var destinationsObject = (JSONObject) neighboursJsonObject.get(originKey.toString());

        if (destinationsObject.names() != null) {
          var destinationMap = new HashMap<Coordinate, Double>();
          for (var destKey : destinationsObject.names()) {
            var destination = getCoordinateFromString(destKey.toString());
            var time = destinationsObject.getDouble(destKey.toString());
            destinationMap.put(destination, time);
          }
          neighbours.put(origin, destinationMap);
        }
      }
    } catch (JSONException | IOException e) {
      e.printStackTrace();
      System.exit(1);
    }

    logger.info("Loaded neighbours for {} coordinates.", neighbours.size());
  }
}
