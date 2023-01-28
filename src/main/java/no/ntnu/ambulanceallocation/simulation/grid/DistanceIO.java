package no.ntnu.ambulanceallocation.simulation.grid;

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
import no.ntnu.ambulanceallocation.utils.Tuple;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DistanceIO {

  public static final String routesFilePath =
      new File("src/main/resources/data/od_paths.json").getAbsolutePath();
  // Interval time for od_paths route coordinates
  private static int TRAVEL_TIME_INTERVAL;
  public static final Set<Coordinate> uniqueGridCoordinates = new HashSet<>();

  public static final Map<Tuple<Coordinate>, Route> routes = new HashMap<>();
  public static final Map<String, Coordinate> coordinateCache = new HashMap<>();
  private static final Logger logger = LoggerFactory.getLogger(DistanceIO.class);

  static {
    loadRoutesFromFile();
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
      return 0.0;
    }
    if (!routes.containsKey(new Tuple<>(from, to))) {
      logger.info("Failed to find distance from {} to {}", from, to);
      return 60.0;
    }
    return routes.get(new Tuple<>(from, to)).time();
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

  private static ArrayList<String> getRouteFromJsonArray(JSONArray jsonArray) {
    var route = new ArrayList<String>();
    for (var i = 0; i < jsonArray.length(); i++) {
      route.add(jsonArray.getString(i));
    }
    return route;
  }

  private static void loadRoutesFromFileOld() {
    logger.info("Loading routes from file...");

    try {
      var routeJsonObject = new JSONObject(Files.readString(Path.of(routesFilePath)));

      for (var originKey : routeJsonObject.names()) {
        var origin = getCoordinateFromString(originKey.toString());
        uniqueGridCoordinates.add(origin);
        var destinationsObject = (JSONObject) routeJsonObject.get(originKey.toString());

        if (destinationsObject.names() != null) {
          for (var destKey : destinationsObject.names()) {
            var destinationObject = (JSONObject) destinationsObject.get(destKey.toString());
            var destination = getCoordinateFromString(destKey.toString());
            var route = getRouteFromJsonArray(destinationObject.getJSONArray("route"));
            var time = destinationObject.getInt("travel_time");
            routes.put(new Tuple<>(origin, destination), new Route(route, time));
          }
        }
      }
    } catch (JSONException | IOException e) {
      e.printStackTrace();
      System.exit(1);
    }

    logger.info("Loaded {} routes.", routes.size());
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
}
