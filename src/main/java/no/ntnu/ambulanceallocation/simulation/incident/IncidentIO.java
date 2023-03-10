package no.ntnu.ambulanceallocation.simulation.incident;

import static no.ntnu.ambulanceallocation.simulation.grid.DistanceIO.getCoordinateFromString;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import no.ntnu.ambulanceallocation.Parameters;
import no.ntnu.ambulanceallocation.simulation.grid.Coordinate;
import no.ntnu.ambulanceallocation.utils.Utils;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncidentIO {

  private static final Logger logger = LoggerFactory.getLogger(IncidentIO.class);

  public static final String incidentsFilePath =
      new File("src/main/resources/data/incidents.csv").getAbsolutePath();
  public static final String incidentDistributionFilePath =
      new File("src/main/resources/data/incidents_distribution.json").getAbsolutePath();
  public static final DateTimeFormatter dateTimeFormatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public static final List<Incident> incidents;
  public static final Map<Coordinate, IncidentDistribution> distributions;

  static {
    incidents = loadIncidentsFromFile();
    distributions = loadDistributionsFromFile();
  }

  public static List<Incident> loadIncidentsFromFile() {

    var incidents = new ArrayList<Incident>();

    logger.info("Loading incidents from file...");

    var processedLines = 0;
    var skippedLines = 0;

    try (var bufferedReader = new BufferedReader(new FileReader(incidentsFilePath))) {
      var header = bufferedReader.readLine();

      // tidspunkt,xcoor,ycoor,hastegrad,tiltak_type,rykker_ut,ank_hentested,avg_hentested,ledig,total_vehicles_assigned,transporting_vehicles,cancelled_vehicles
      logger.info("Incident CSV header: {}", header);

      var line = bufferedReader.readLine();

      while (line != null) {
        var values = Arrays.asList(line.split(","));

        if (isValid(values)) {
          var urgencyLevel = UrgencyLevel.get(values.get(3));
          var changeUrgency =
              Parameters.PRESET_URGENCY
                  && urgencyLevel.equals(UrgencyLevel.ACUTE)
                  && Utils.randomDouble() < Parameters.PRESET_URGENCY_PROBABILITY;

          incidents.add(createIncident(values, changeUrgency));

          processedLines++;
        } else {
          // Skip line
          skippedLines++;
        }

        line = bufferedReader.readLine();
      }

    } catch (IOException exception) {
      logger.error("An IOException occurred while loading incidents from file: ", exception);
      System.exit(1);
    }

    logger.info("Loading incidents from file was successful.");

    var percentageSkipped = 100 * Utils.round(skippedLines / (double) processedLines, 6);
    logger.info("{} incidents were successfully processed", processedLines);
    logger.info("{} incidents were skipped ({}%)", skippedLines, percentageSkipped);

    return incidents;
  }

  private static Incident createIncident(List<String> values, boolean changeUrgency) {
    var callReceived = LocalDateTime.parse(values.get(0), dateTimeFormatter);

    var xCoordinate = Integer.parseInt(values.get(1));
    var yCoordinate = Integer.parseInt(values.get(2));

    var urgencyLevel = UrgencyLevel.get(values.get(3));
    if (changeUrgency) {
      urgencyLevel = UrgencyLevel.URGENT;
    }
    // String dispatchType = values.get(4); // Always ambulance

    var dispatched = LocalDateTime.parse(values.get(5), dateTimeFormatter);
    var arrivalAtScene = parseDateTime(values.get(6));
    var departureFromScene = parseDateTime(values.get(7));
    var availableNonTransport = LocalDateTime.parse(values.get(8), dateTimeFormatter);
    var availableTransport = LocalDateTime.parse(values.get(9), dateTimeFormatter);

    var nonTransportingVehicles = Integer.parseInt(values.get(10));
    var transportingVehicles = Integer.parseInt(values.get(11));

    return new Incident(
        callReceived,
        xCoordinate,
        yCoordinate,
        urgencyLevel,
        dispatched,
        arrivalAtScene,
        departureFromScene,
        availableNonTransport,
        availableTransport,
        nonTransportingVehicles,
        transportingVehicles);
  }

  private static boolean isValid(List<String> values) {
    return !values.get(5).isBlank() && !values.get(8).isBlank();
  }

  private static Optional<LocalDateTime> parseDateTime(String dateTime) {
    if (dateTime.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(LocalDateTime.parse(dateTime, dateTimeFormatter));
  }

  private static Map<Coordinate, IncidentDistribution> loadDistributionsFromFile() {
    logger.info("Loading distributions from file...");
    var distributions = new HashMap<Coordinate, IncidentDistribution>();

    try {
      var distributionJsonObject =
          new JSONObject(Files.readString(Path.of(incidentDistributionFilePath)));

      for (var originKey : distributionJsonObject.names()) {
        var origin = getCoordinateFromString(originKey.toString());
        var monthJsonObject = (JSONObject) distributionJsonObject.get(originKey.toString());
        var monthMap = new HashMap<Integer, Map<Integer, Map<Integer, Double>>>();

        for (var monthKey : monthJsonObject.names()) {
          var month = Integer.parseInt(monthKey.toString());
          var weekdayJsonObject = (JSONObject) monthJsonObject.get(monthKey.toString());
          var weekdayMap = new HashMap<Integer, Map<Integer, Double>>();

          for (var weekdayKey : weekdayJsonObject.names()) {
            var weekday = Integer.parseInt(weekdayKey.toString());
            var hourJsonObject = (JSONObject) weekdayJsonObject.get(weekdayKey.toString());
            var hourMap = new HashMap<Integer, Double>();

            for (var hourKey : hourJsonObject.names()) {
              var hour = Integer.parseInt(hourKey.toString());
              hourMap.put(hour, hourJsonObject.getDouble(hourKey.toString()));
            }
            weekdayMap.put(weekday, hourMap);
          }
          monthMap.put(month, weekdayMap);
        }
        distributions.put(origin, new IncidentDistribution(monthMap));
      }
    } catch (JSONException | IOException e) {
      e.printStackTrace();
      System.exit(1);
    }

    logger.info("Loaded {} distributions.", distributions.size());
    return distributions;
  }
}
