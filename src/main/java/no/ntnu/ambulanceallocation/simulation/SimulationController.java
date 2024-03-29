/*
 Copyright 2015-2020 Peter-Josef Meisch (pj.meisch@sothawo.com)

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package no.ntnu.ambulanceallocation.simulation;

import com.sothawo.mapjfx.Configuration;
import com.sothawo.mapjfx.Coordinate;
import com.sothawo.mapjfx.CoordinateLine;
import com.sothawo.mapjfx.MapCircle;
import com.sothawo.mapjfx.MapLabel;
import com.sothawo.mapjfx.MapType;
import com.sothawo.mapjfx.MapView;
import com.sothawo.mapjfx.Marker;
import com.sothawo.mapjfx.Projection;
import com.sothawo.mapjfx.WMSParam;
import com.sothawo.mapjfx.XYZParam;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.animation.Transition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import no.ntnu.ambulanceallocation.Parameters;
import no.ntnu.ambulanceallocation.optimization.Allocation;
import no.ntnu.ambulanceallocation.optimization.initializer.AllCityCenter;
import no.ntnu.ambulanceallocation.optimization.initializer.PopulationProportionate;
import no.ntnu.ambulanceallocation.optimization.initializer.Random;
import no.ntnu.ambulanceallocation.optimization.initializer.Uniform;
import no.ntnu.ambulanceallocation.optimization.initializer.UniformRandom;
import no.ntnu.ambulanceallocation.simulation.event.NewCall;
import no.ntnu.ambulanceallocation.simulation.grid.DistanceIO;
import no.ntnu.ambulanceallocation.simulation.incident.Incident;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the FXML defined code.
 *
 * @author P.J. Meisch (pj.meisch@sothawo.com).
 */
public class SimulationController {

  /** logger for the class. */
  private static final Logger logger = LoggerFactory.getLogger(SimulationController.class);

  /** default zoom value. */
  private static final int ZOOM_DEFAULT = 11;

  private final Coordinate center = new Coordinate(59.929671, 10.738381);
  private final URL hospitalIcon = getClass().getResource("/images/hospital.png");
  private final URL baseStationIcon = getClass().getResource("/images/base_station.png");
  private final URL ambulanceIcon = getClass().getResource("/images/ambulance.png");
  private final Map<no.ntnu.ambulanceallocation.simulation.grid.Coordinate, Coordinate>
      utmToLatLongMap = new HashMap<>();
  private final List<Coordinate> baseStationCoordinateList = new ArrayList<>();
  private final List<Marker> baseStationMarkerList =
      Collections.synchronizedList(new ArrayList<>());
  private final List<MapLabel> baseStationLabelList =
      Collections.synchronizedList(new ArrayList<>());
  private final List<Coordinate> hospitalCoordinateList =
      Collections.synchronizedList(new ArrayList<>());
  private final List<Marker> hospitalMarkerList = Collections.synchronizedList(new ArrayList<>());
  private final List<MapLabel> hospitalLabelList = Collections.synchronizedList(new ArrayList<>());
  private final List<MapCircle> gridCentroidCirclesList =
      Collections.synchronizedList(new ArrayList<>());
  private final List<MapLabel> gridCentroidLabelList =
      Collections.synchronizedList(new ArrayList<>());
  private final Map<Ambulance, Marker> ambulanceMarkers =
      Collections.synchronizedMap(new HashMap<>());
  private final Map<Ambulance, MapLabel> ambulanceLabels =
      Collections.synchronizedMap(new HashMap<>());
  private final Map<Ambulance, MapCircle> destinationCircles =
      Collections.synchronizedMap(new HashMap<>());
  private final Map<Ambulance, CoordinateLine> destinationLines =
      Collections.synchronizedMap(new HashMap<>());
  /** params for the WMS server. */
  private final WMSParam wmsParam =
      new WMSParam().setUrl("http://ows.terrestris.de/osm/service?").addParam("layers", "OSM-WMS");

  private final XYZParam xyzParams =
      new XYZParam()
          .withUrl(
              "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x})")
          .withAttributions(
              "'Tiles &copy; <a href=\"https://services.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer\">ArcGIS</a>'");
  @FXML private Button startSimulationButton;
  /** the MapView containing the map */
  @FXML private MapView mapView;
  /** Accordion for all the different options */
  @FXML private Accordion leftControls;
  /** section containing the location button */
  @FXML private TitledPane optionsLocations;
  /** for editing the animation duration */
  @FXML private TextField animationDuration;
  /** the BIng Maps API Key. */
  @FXML private Label currentTime;

  @FXML private Label activeAmbulances;
  @FXML private TextField numDayAmbulances;
  @FXML private TextField numNightAmbulances;
  @FXML private TextField dayShift;
  @FXML private TextField nightShift;
  /** Label to display the current center */
  @FXML private Label labelCenter;
  /** RadioButton for MapStyle OSM */
  @FXML private RadioButton radioMsOSM;
  /** RadioButton for MapStyle Bing Roads */
  @FXML private RadioButton radioMsBR;
  /** RadioButton for MapStyle WMS. */
  @FXML private RadioButton radioMsWMS;
  /** RadioButton for MapStyle XYZ */
  @FXML private RadioButton radioMsXYZ;
  /** ToggleGroup for the MapStyle radios */
  @FXML private ToggleGroup mapTypeGroup;

  @FXML private CheckBox checkShowGridCentroids;
  @FXML private CheckBox checkShowGridLabels;
  @FXML private CheckBox checkShowPathLines;
  @FXML private CheckBox checkShowHospitals;
  @FXML private CheckBox checkShowLabels;
  @FXML private CheckBox checkShowBaseStations;
  @FXML private CheckBox checkShowIncidents;
  @FXML private CheckBox checkShowAmbulances;
  @FXML private CheckBox checkShowAmbulanceLabels;
  @FXML private Slider simulationUpdateIntervalSlider;
  private List<MapCircle> incidentCircleList = Collections.synchronizedList(new ArrayList<>());
  private LocalDateTime currentTimeInternal = LocalDateTime.MIN;
  private Thread simulationThread;

  public SimulationController() {

    if (baseStationIcon == null || hospitalIcon == null) {
      throw new IllegalStateException("Missing one or more icons");
    }

    logger.debug("Reading coordinate CSV files");
    readCSVThenParse(
        "/data/base_stations.csv",
        values -> {
          var coordinate = new Coordinate(Double.valueOf(values[1]), Double.valueOf(values[2]));
          var mapMarker =
              new Marker(baseStationIcon, -15, -15).setPosition(coordinate).setVisible(true);
          var label =
              new MapLabel(values[0], -57, 20)
                  .setPosition(coordinate)
                  .setVisible(false)
                  .setCssClass("label");

          baseStationCoordinateList.add(coordinate);
          baseStationMarkerList.add(mapMarker);
          baseStationLabelList.add(label);
        });

    readCSVThenParse(
        "/data/hospitals.csv",
        values -> {
          var coordinate = new Coordinate(Double.valueOf(values[1]), Double.valueOf(values[2]));
          var mapMarker =
              new Marker(hospitalIcon, -15, -15).setPosition(coordinate).setVisible(true);
          var label =
              new MapLabel(values[0], -57, 20)
                  .setPosition(coordinate)
                  .setVisible(false)
                  .setCssClass("label");

          hospitalCoordinateList.add(coordinate);
          hospitalMarkerList.add(mapMarker);
          hospitalLabelList.add(label);
        });

    logger.debug("Reading UTM to LatLong conversion map");
    readCSVThenParse(
        "/data/grid_coordinates.csv",
        values -> {
          var coordinate = new Coordinate(Double.valueOf(values[2]), Double.valueOf(values[3]));

          utmToLatLongMap.put(
              new no.ntnu.ambulanceallocation.simulation.grid.Coordinate(
                  Integer.parseInt(values[0]), Integer.parseInt(values[1])),
              coordinate);
        });

    DistanceIO.uniqueGridCoordinates.forEach(
        utmCoordinate -> {
          var coordinate = utmToLatLongMap.get(utmCoordinate);

          gridCentroidCirclesList.add(
              new MapCircle(coordinate, 100)
                  .setFillColor(Color.web("#000000", 0.4))
                  .setColor(Color.TRANSPARENT)
                  .setVisible(false));

          gridCentroidLabelList.add(
              new MapLabel(String.format("x:%s\ny:%s", utmCoordinate.x(), utmCoordinate.y()), 0, 10)
                  .setPosition(coordinate)
                  .setCssClass("coordinate-label")
                  .setVisible(false));
        });
  }

  /**
   * called after the fxml is loaded and all objects are created. This is not called initialize
   * anymore, because we need to pass in the projection before initializing.
   *
   * @param projection the projection to use in the map.
   */
  public void initMapAndControls(Projection projection) {
    setPopulationProportionateAllocation();

    checkShowPathLines.setSelected(true);
    checkShowHospitals.setSelected(true);
    checkShowBaseStations.setSelected(true);
    checkShowIncidents.setSelected(true);
    checkShowAmbulances.setSelected(true);

    logger.trace("begin initialize");

    // init MapView-Cache
    final var offlineCache = mapView.getOfflineCache();
    final var cacheDir = System.getProperty("java.io.tmpdir") + "/mapjfx-cache";
    logger.info("using dir for cache: " + cacheDir);
    try {
      Files.createDirectories(Paths.get(cacheDir));
      offlineCache.setCacheDirectory(cacheDir);
      offlineCache.setActive(true);
    } catch (IOException e) {
      logger.warn("could not activate offline cache", e);
    }

    // set the custom css file for the MapView
    mapView.setCustomMapviewCssURL(
        Objects.requireNonNull(getClass().getResource("/css/custom_mapview.css")));

    leftControls.setExpandedPane(optionsLocations);

    // set the controls to disabled, this will be changed when the MapView is initialized
    setControlsDisable(true);

    // add a listener to the animationDuration field and make sure we only accept int values
    animationDuration
        .textProperty()
        .addListener(
            (observable, oldValue, newValue) -> {
              if (newValue.isEmpty()) {
                mapView.setAnimationDuration(0);
              } else {
                try {
                  mapView.setAnimationDuration(Integer.parseInt(newValue));
                } catch (NumberFormatException e) {
                  animationDuration.setText(oldValue);
                }
              }
            });
    animationDuration.setText("500");

    // bind the map's center to the corresponding labels and format them
    labelCenter.textProperty().bind(Bindings.format("center: %s", mapView.centerProperty()));
    logger.trace("options and labels done");

    // watch the MapView's initialized property to finish initialization
    mapView
        .initializedProperty()
        .addListener(
            (observable, oldValue, newValue) -> {
              if (newValue) {
                afterMapIsInitialized();
              }
            });

    // observe the map type radio buttons
    mapTypeGroup
        .selectedToggleProperty()
        .addListener(
            (observable, oldValue, newValue) -> {
              logger.debug("map type toggled to {}", newValue.toString());
              var mapType = MapType.OSM;
              if (newValue == radioMsBR) {
                mapType = MapType.BINGMAPS_ROAD;
              } else if (newValue == radioMsWMS) {
                mapView.setWMSParam(wmsParam);
                mapType = MapType.WMS;
              } else if (newValue == radioMsXYZ) {
                mapView.setXYZParam(xyzParams);
                mapType = MapType.XYZ;
              }
              mapView.setMapType(mapType);
            });
    mapTypeGroup.selectToggle(radioMsOSM);

    // set current time to start time
    currentTime.setText(
        "Current time:\n" + Parameters.START_DATE_TIME.minusHours(Parameters.BUFFER_SIZE));

    // finally initialize the map view
    logger.trace("start map initialization");
    mapView.initialize(
        Configuration.builder().projection(projection).showZoomControls(false).build());
    logger.debug("initialization finished");
  }

  /**
   * enables / disables the different controls
   *
   * @param flag if true the controls are disabled
   */
  private void setControlsDisable(boolean flag) {
    leftControls.setDisable(flag);
  }

  /** finishes setup after the mpa is initialized */
  private void afterMapIsInitialized() {
    logger.trace("map intialized");
    logger.debug("setting center and enabling controls...");
    // start at the harbour with default zoom
    mapView.setZoom(ZOOM_DEFAULT);
    mapView.setCenter(center);
    baseStationMarkerList.forEach(mapView::addMarker);
    baseStationLabelList.forEach(mapView::addLabel);
    hospitalMarkerList.forEach(mapView::addMarker);
    hospitalLabelList.forEach(mapView::addLabel);
    gridCentroidCirclesList.forEach(mapView::addMapCircle);
    gridCentroidLabelList.forEach(mapView::addLabel);
    // now enable the controls
    setControlsDisable(false);
  }

  private void readCSVThenParse(String fileName, Consumer<String[]> consumer) {
    try {
      var lines =
          new BufferedReader(
                  new InputStreamReader(
                      Objects.requireNonNull(getClass().getResource(fileName)).openStream(),
                      StandardCharsets.UTF_8))
              .lines();

      lines.map(line -> line.split(",")).forEach(consumer);

    } catch (IOException | NumberFormatException e) {
      logger.error("load", e);
    }
  }

  private void updateIncidents(Collection<NewCall> callQueue) {
    Platform.runLater(
        () -> {
          incidentCircleList.forEach(mapView::removeMapCircle);
          incidentCircleList =
              callQueue.stream()
                  .map(
                      call ->
                          createIncidentCircle(
                              call.incident, utmToLatLongMap.get(call.incident.getLocation())))
                  .toList();
          incidentCircleList.forEach(mapView::addMapCircle);
        });
  }

  private void updateAmbulances(Collection<Ambulance> ambulanceList) {
    Platform.runLater(
        () -> {
          if (ambulanceMarkers.size() == 0) {
            updateAmbulancesNoMarkers(ambulanceList);
          } else {

            synchronized (ambulanceMarkers) {
              for (var ambulance : ambulanceList) {

                var coordinate = utmToLatLongMap.get(ambulance.getCurrentLocation());
                var marker = ambulanceMarkers.get(ambulance);
                var label = ambulanceLabels.get(ambulance);

                if (ambulance.isAtBaseStation() && ambulance.isOffDuty()) {
                  marker.setVisible(false);
                  label.setVisible(false);
                } else {
                  marker.setVisible(checkShowAmbulances.isSelected());
                  label.setVisible(checkShowAmbulanceLabels.isSelected());
                }

                if (!marker.getPosition().equals(coordinate)) {
                  animateMarker(marker, marker.getPosition(), coordinate, label);
                  marker.setRotation(bearingInDegrees(marker.getPosition(), coordinate) + 90);
                }

                if (ambulance.getDestination() != null) {
                  updateAmbulanceDestinationLine(ambulance, coordinate);
                }

                updateAmbulanceDestinationCircle(ambulance);
              }
            }
          }

          currentTime.setText("Current time:\n" + currentTimeInternal.toString());

          var activeCount =
              ambulanceList.stream().filter(ambulance -> !ambulance.isOffDuty()).count();

          activeAmbulances.setText("Active ambulances: " + activeCount);
        });
  }

  private static int bearingInDegrees(Coordinate src, Coordinate dst) {
    var srcLat = Math.toRadians(src.getLatitude());
    var dstLat = Math.toRadians(dst.getLatitude());
    var dLng = Math.toRadians(dst.getLongitude() - src.getLongitude());

    return (int)
        Math.round(
            Math.toDegrees(
                (Math.atan2(
                        Math.sin(dLng) * Math.cos(dstLat),
                        Math.cos(srcLat) * Math.sin(dstLat)
                            - Math.sin(srcLat) * Math.cos(dstLat) * Math.cos(dLng))
                    + Math.PI)));
  }

  private void updateAmbulanceDestinationLine(Ambulance ambulance, Coordinate currentPosition) {
    if (destinationLines.containsKey(ambulance)) {
      mapView.removeCoordinateLine(destinationLines.get(ambulance));
    }

    var ambulanceDestination = utmToLatLongMap.get(ambulance.getDestination());

    Color color;
    if (!ambulance.isAvailable() && ambulance.isTransportingPatient()) {
      color = Color.web("#ff0000", 0.9);
    } else if (ambulance.isAvailable()
        && ambulance.getDestination() == ambulance.getBaseStation().getCoordinate()) {
      color = Color.web("#0000ff", 0.9);
    } else if (ambulance.isReassigned()) {
      color = Color.web("#ff00ff", 0.9);
    } else {
      color = Color.web("#00ff00", 0.9);
    }

    destinationLines.put(
        ambulance,
        new CoordinateLine(ambulanceDestination, currentPosition)
            .setColor(color)
            .setVisible(checkShowPathLines.isSelected()));
    mapView.addCoordinateLine(destinationLines.get(ambulance));
  }

  private void updateAmbulanceDestinationCircle(Ambulance ambulance) {
    if (destinationCircles.containsKey(ambulance)) {
      if (ambulance.getDestination() == null
          || !destinationCircles
              .get(ambulance)
              .getCenter()
              .equals(utmToLatLongMap.get(ambulance.getDestination()))) {
        mapView.removeMapCircle(destinationCircles.get(ambulance));
        destinationCircles.remove(ambulance);
      }
    }

    if (ambulance.getDestination() != null && !destinationCircles.containsKey(ambulance)) {

      var destinationCoordinate = utmToLatLongMap.get(ambulance.getDestination());

      if (!baseStationCoordinateList.contains(destinationCoordinate)
          && !hospitalCoordinateList.contains(destinationCoordinate)) {

        destinationCircles.put(
            ambulance, createIncidentCircle(ambulance.getIncident(), destinationCoordinate));

        mapView.addMapCircle(destinationCircles.get(ambulance));
      }
    }
  }

  private MapCircle createIncidentCircle(Incident incident, Coordinate coordinate) {
    var color =
        switch (incident.urgencyLevel()) {
          case ACUTE -> "#ff0000";
          case URGENT -> "#ffff00";
          default -> "0000ff";
        };

    return new MapCircle(coordinate, 1000)
        .setColor(Color.web(color, 0.7))
        .setVisible(checkShowIncidents.isSelected());
  }

  private void animateMarker(
      Marker marker, Coordinate oldPosition, Coordinate newPosition, MapLabel label) {
    // animate the marker to the new position
    final Transition transition =
        new Transition() {
          private final Double oldPositionLongitude = oldPosition.getLongitude();
          private final Double oldPositionLatitude = oldPosition.getLatitude();
          private final double deltaLatitude = newPosition.getLatitude() - oldPositionLatitude;
          private final double deltaLongitude = newPosition.getLongitude() - oldPositionLongitude;

          {
            var animationDuration = Math.max(1, simulationUpdateIntervalSlider.getValue());
            setCycleDuration(Duration.millis(animationDuration * 1.3));
            setOnFinished(evt -> marker.setPosition(newPosition));
            setOnFinished(evt -> label.setPosition(newPosition));
          }

          @Override
          protected void interpolate(double v) {
            final var latitude = oldPosition.getLatitude() + v * deltaLatitude;
            final var longitude = oldPosition.getLongitude() + v * deltaLongitude;
            marker.setPosition(new Coordinate(latitude, longitude));
            label.setPosition(new Coordinate(latitude, longitude));
          }
        };
    transition.play();
  }

  private void updateAmbulancesNoMarkers(Collection<Ambulance> ambulanceList) {
    if (ambulanceIcon == null) {
      throw new IllegalStateException("Missing ambulance icon");
    }

    for (var ambulance : ambulanceList) {

      var coordinates = utmToLatLongMap.get(ambulance.getCurrentLocation());

      var marker =
          new Marker(ambulanceIcon, -15, -15)
              .setPosition(coordinates)
              .setVisible(checkShowAmbulances.isSelected());
      var label =
          new MapLabel(String.valueOf(ambulance.id), 5, 5)
              .setPosition(coordinates)
              .setCssClass("ambulance-label")
              .setVisible(checkShowAmbulanceLabels.isSelected());

      ambulanceMarkers.put(ambulance, marker);
      ambulanceLabels.put(ambulance, label);
      mapView.addMarker(marker);
      mapView.addLabel(label);
    }
  }

  private int getDayAllocation() {
    if (numDayAmbulances.getText() == null || numDayAmbulances.getText().isEmpty()) {
      return Parameters.NUMBER_OF_AMBULANCES_DAY;
    } else {
      return Integer.parseInt(numDayAmbulances.getText());
    }
  }

  private int getNightAllocation() {
    if (numNightAmbulances.getText() == null || numNightAmbulances.getText().isEmpty()) {
      return Parameters.NUMBER_OF_AMBULANCES_NIGHT;
    } else {
      return Integer.parseInt(numNightAmbulances.getText());
    }
  }

  @FXML
  private void setUniformAllocation() {
    dayShift.setText(
        new Uniform()
            .initialize(getDayAllocation()).stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ")));
    nightShift.setText(
        new Uniform()
            .initialize(getNightAllocation()).stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ")));
  }

  @FXML
  private void setUniformRandomAllocation() {
    dayShift.setText(
        new UniformRandom()
            .initialize(getDayAllocation()).stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ")));
    nightShift.setText(
        new UniformRandom()
            .initialize(getNightAllocation()).stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ")));
  }

  @FXML
  private void setPopulationProportionateAllocation() {
    dayShift.setText(
        new PopulationProportionate()
            .initialize(getDayAllocation()).stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ")));
    nightShift.setText(
        new PopulationProportionate()
            .initialize(getNightAllocation()).stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ")));
  }

  @FXML
  private void setAllCityCenterAllocation() {
    dayShift.setText(
        new AllCityCenter()
            .initialize(getDayAllocation()).stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ")));
    nightShift.setText(
        new AllCityCenter()
            .initialize(getNightAllocation()).stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ")));
  }

  @FXML
  private void setRandomAllocation() {
    dayShift.setText(
        new Random()
            .initialize(getDayAllocation()).stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ")));
    nightShift.setText(
        new Random()
            .initialize(getNightAllocation()).stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ")));
  }

  @FXML
  private void setVisibilityBaseStations() {
    logger.info("Setting base station layer visibility to " + checkShowBaseStations.isSelected());
    baseStationMarkerList.forEach(marker -> marker.setVisible(checkShowBaseStations.isSelected()));
    baseStationLabelList.forEach(
        label ->
            label.setVisible(checkShowBaseStations.isSelected() && checkShowLabels.isSelected()));
  }

  @FXML
  private void setVisibilityHospitals() {
    logger.info("Setting hospital layer visibility to " + checkShowHospitals.isSelected());
    hospitalMarkerList.forEach(marker -> marker.setVisible(checkShowHospitals.isSelected()));
    hospitalLabelList.forEach(
        label -> label.setVisible(checkShowHospitals.isSelected() && checkShowLabels.isSelected()));
  }

  @FXML
  private void setVisibilityLabels() {
    logger.info("Setting label visibility to " + checkShowLabels.isSelected());
    if (checkShowHospitals.isSelected()) {
      hospitalLabelList.forEach(label -> label.setVisible(checkShowLabels.isSelected()));
    }
    if (checkShowBaseStations.isSelected()) {
      baseStationLabelList.forEach(label -> label.setVisible(checkShowLabels.isSelected()));
    }
  }

  @FXML
  private void setVisibilityAmbulances() {
    logger.info("Setting ambulance visibility to " + checkShowAmbulances.isSelected());
    ambulanceMarkers.values().forEach(mapView::removeMarker);
    ambulanceLabels.values().forEach(mapView::removeLabel);
    ambulanceMarkers.values().forEach(m -> m.setVisible(checkShowAmbulances.isSelected()));
    ambulanceLabels.values().forEach(l -> l.setVisible(checkShowAmbulances.isSelected()));
    ambulanceMarkers.values().forEach(mapView::addMarker);
    ambulanceLabels.values().forEach(mapView::addLabel);
  }

  @FXML
  private void setVisibilityAmbulanceLabels() {
    logger.info("Setting ambulance labels visibility to " + checkShowAmbulanceLabels.isSelected());
    ambulanceLabels.values().forEach(mapView::removeLabel);
    ambulanceLabels.values().forEach(l -> l.setVisible(checkShowAmbulanceLabels.isSelected()));
    ambulanceLabels.values().forEach(mapView::addLabel);
  }

  @FXML
  private void setVisibilityGridCentroids() {
    logger.info(
        "Setting grid centroids layer visibility to " + checkShowGridCentroids.isSelected());
    gridCentroidCirclesList.forEach(mapView::removeMapCircle);
    gridCentroidCirclesList.forEach(
        circle -> circle.setVisible(checkShowGridCentroids.isSelected()));
    gridCentroidCirclesList.forEach(mapView::addMapCircle);
  }

  @FXML
  private void setVisibilityGridLabels() {
    logger.info("Setting grid labels layer visibility to " + checkShowGridLabels.isSelected());
    gridCentroidLabelList.forEach(label -> label.setVisible(checkShowGridLabels.isSelected()));
  }

  @FXML
  private void setVisibilityPathLines() {
    logger.info("Setting path line layer visibility to " + checkShowPathLines.isSelected());
    destinationLines.values().forEach(mapView::removeCoordinateLine);
    destinationLines.values().forEach(line -> line.setVisible(checkShowPathLines.isSelected()));
    destinationLines.values().forEach(mapView::addCoordinateLine);
  }

  @FXML
  private void setVisibilityIncidents() {
    logger.info("Setting incident layer visibility to " + checkShowIncidents.isSelected());
    destinationCircles.values().forEach(mapView::removeMapCircle);
    incidentCircleList.forEach(mapView::removeMapCircle);
    destinationCircles
        .values()
        .forEach(circle -> circle.setVisible(checkShowIncidents.isSelected()));
    incidentCircleList.forEach(circle -> circle.setVisible(checkShowIncidents.isSelected()));
    destinationCircles.values().forEach(mapView::addMapCircle);
    incidentCircleList.forEach(mapView::addMapCircle);
  }

  @FXML
  private void centerMap() {
    mapView.setCenter(center);
    mapView.setZoom(ZOOM_DEFAULT);
  }

  @FXML
  private void startSimulation() {
    if (simulationThread == null || !simulationThread.isAlive()) {
      var task =
          new Task<>() {
            @Override
            protected Void call() {
              var dayShiftAllocation =
                  Stream.of(dayShift.getText().replaceAll("\\s", "").split(","))
                      .mapToInt(Integer::parseInt)
                      .boxed()
                      .toList();
              var nightShiftAllocation =
                  Stream.of(nightShift.getText().replaceAll("\\s", "").split(","))
                      .mapToInt(Integer::parseInt)
                      .boxed()
                      .toList();

              var allocation = new Allocation(List.of(dayShiftAllocation, nightShiftAllocation));

              Simulation.visualizedSimulation(
                  allocation,
                  (currentTime, ambulanceList, callQueue) -> {
                    currentTimeInternal = currentTime;
                    updateAmbulances(ambulanceList);
                    updateIncidents(callQueue);
                  },
                  simulationUpdateIntervalSlider.valueProperty());

              return null;
            }
          };
      simulationThread = new Thread(task);
      simulationThread.setDaemon(true);
      simulationThread.start();
    }
    startSimulationButton.setVisible(false);
  }
}
