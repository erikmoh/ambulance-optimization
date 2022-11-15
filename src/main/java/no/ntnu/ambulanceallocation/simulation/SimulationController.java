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
import java.time.temporal.ChronoUnit;
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
import javafx.scene.layout.HBox;
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
import no.ntnu.ambulanceallocation.simulation.incident.UrgencyLevel;
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
  private final URL ambulanceIcon = getClass().getResource("/images/ambulance_top.png");
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
  /** the box containing the top controls, must be enabled when mapView is initialized */
  @FXML private HBox topControls;
  /** Slider to change the zoom value */
  @FXML private Slider sliderZoom;
  /** Accordion for all the different options */
  @FXML private Accordion leftControls;
  /** section containing the location button */
  @FXML private TitledPane optionsLocations;
  /** for editing the animation duration */
  @FXML private TextField animationDuration;
  /** the BIng Maps API Key. */
  @FXML private TextField bingMapsApiKey;

  @FXML private Label currentTime;
  @FXML private Label activeAmbulances;
  @FXML private TextField numDayAmbulances;
  @FXML private TextField numNightAmbulances;
  @FXML private TextField dayShift;
  @FXML private TextField nightShift;
  /** Label to display the current center */
  @FXML private Label labelCenter;
  /** Label to display the current zoom */
  @FXML private Label labelZoom;
  /** RadioButton for MapStyle OSM */
  @FXML private RadioButton radioMsOSM;
  /** RadioButton for MapStyle Bing Roads */
  @FXML private RadioButton radioMsBR;
  /** RadioButton for MapStyle Bing Roads - dark */
  @FXML private RadioButton radioMsCd;
  /** RadioButton for MapStyle Bing Roads - grayscale */
  @FXML private RadioButton radioMsCg;
  /** RadioButton for MapStyle Bing Roads - light */
  @FXML private RadioButton radioMsCl;
  /** RadioButton for MapStyle Bing Aerial */
  @FXML private RadioButton radioMsBA;
  /** RadioButton for MapStyle Bing Aerial with Label */
  @FXML private RadioButton radioMsBAwL;
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
  @FXML private Slider simulationUpdateIntervalSlider;
  private List<MapCircle> incidentCircleList = Collections.synchronizedList(new ArrayList<>());
  private LocalDateTime currentTimeInternal = LocalDateTime.MIN;
  private Thread simulationThread;
  private long lastUiUpdate = 0;

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
              new MapLabel(
                      String.format(
                          "x:%s y:%s id:%s",
                          utmCoordinate.x(), utmCoordinate.y(), utmCoordinate.getIdNum()),
                      0,
                      10)
                  .setPosition(coordinate)
                  .setCssClass("coordinate-label")
                  .setVisible(false));
        });

    var markerClick = Marker.createProvided(Marker.Provided.ORANGE).setVisible(false);
    var labelClick = new MapLabel("click!", 10, -10).setVisible(false).setCssClass("orange-label");
    markerClick.attachLabel(labelClick);
  }

  /**
   * called after the fxml is loaded and all objects are created. This is not called initialize
   * anymore, because we need to pass in the projection before initializing.
   *
   * @param projection the projection to use in the map.
   */
  public void initMapAndControls(Projection projection) {
    setCustomAllocation();

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

    sliderZoom.valueProperty().bindBidirectional(mapView.zoomProperty());

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

    // bind the map's center and zoom properties to the corresponding labels and format them
    labelCenter.textProperty().bind(Bindings.format("center: %s", mapView.centerProperty()));
    labelZoom.textProperty().bind(Bindings.format("zoom: %.0f", mapView.zoomProperty()));
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
              } else if (newValue == radioMsCd) {
                mapType = MapType.BINGMAPS_CANVAS_DARK;
              } else if (newValue == radioMsCg) {
                mapType = MapType.BINGMAPS_CANVAS_GRAY;
              } else if (newValue == radioMsCl) {
                mapType = MapType.BINGMAPS_CANVAS_LIGHT;
              } else if (newValue == radioMsBA) {
                mapType = MapType.BINGMAPS_AERIAL;
              } else if (newValue == radioMsBAwL) {
                mapType = MapType.BINGMAPS_AERIAL_WITH_LABELS;
              } else if (newValue == radioMsWMS) {
                mapView.setWMSParam(wmsParam);
                mapType = MapType.WMS;
              } else if (newValue == radioMsXYZ) {
                mapView.setXYZParam(xyzParams);
                mapType = MapType.XYZ;
              }
              mapView.setBingMapsApiKey(bingMapsApiKey.getText());
              mapView.setMapType(mapType);
            });
    mapTypeGroup.selectToggle(radioMsOSM);

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
    topControls.setDisable(flag);
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
                          new MapCircle(utmToLatLongMap.get(call.incident.getLocation()), 1000)
                              .setColor(
                                  call.incident.urgencyLevel() == UrgencyLevel.ACUTE
                                      ? Color.web("#ff0000", 0.7)
                                      : Color.web("#ffff00", 0.7))
                              .setVisible(checkShowIncidents.isSelected()))
                  .toList();
          incidentCircleList.forEach(mapView::addMapCircle);
        });
  }

  private void updateAmbulances(Collection<Ambulance> ambulanceList) {
    Platform.runLater(
        () -> {
          if (ambulanceMarkers.size() > 0) {

            synchronized (ambulanceMarkers) {
              for (var ambulance : ambulanceList) {

                /*logger.info("route: {}", ambulance.getRoute().routeCoordinates());*/
                var pos =
                    ambulance.getCurrentLocationVisualized(currentTimeInternal, utmToLatLongMap);
                var coordinate = utmToLatLongMap.get(pos);
                /*logger.info(
                "{}: Updated pos when updating ambulance: {} {}",
                currentTimeInternal,
                coordinate.toString(),
                pos);*/
                var marker = ambulanceMarkers.get(ambulance);
                // var markerLabel = marker.getMapLabel().get();

                if (ambulance.isAtBaseStation() && ambulance.isOffDuty()) {
                  marker.setVisible(false);
                } else {
                  marker.setVisible(checkShowAmbulances.isSelected());
                }

                if (!marker.getPosition().equals(coordinate)) {
                  marker.setRotation(bearingInDegrees(marker.getPosition(), coordinate) + 90);
                  updateAmbulanceDestinationLine(ambulance);
                }

                updateAmbulanceUrgencyMarker();
                updateAmbulanceDestinationCircle(ambulance);

                animateMarker(marker, marker.getPosition(), coordinate);
              }
            }
          } else {
            updateAmbulancesNoMarkers(ambulanceList);
          }

          currentTime.setText("Current time:\n" + currentTimeInternal.toString());

          var activeCount =
              ambulanceList.stream().filter(ambulance -> !ambulance.isOffDuty()).count();

          activeAmbulances.setText("Active ambulances: " + activeCount + "");
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

  private void updateAmbulanceDestinationLine(Ambulance ambulance) {
    Color color;
    if (!ambulance.isAvailable()
        && ambulance.isTransport()
        && ambulance.getDestination().equals(ambulance.getHospitalLocation())) {
      color = Color.web("#ff0000", 0.9);
    } else if (ambulance.isAvailable()
        && ambulance.getDestination() == ambulance.getBaseStationLocation()) {
      color = Color.web("#0000ff", 0.9);
    } else {
      color = Color.web("#00ff00", 0.9);
    }

    if (destinationLines.containsKey(ambulance)) {
      mapView.removeCoordinateLine(destinationLines.get(ambulance));
    }

    var ambulanceDestination = utmToLatLongMap.get(ambulance.getDestination());
    var currentPosition =
        utmToLatLongMap.get(
            ambulance.getCurrentLocationVisualized(currentTimeInternal, utmToLatLongMap));
    /*logger.info(
    "{}: Updated pos when updating line: {}", currentTimeInternal, currentPosition.toString());*/

    destinationLines.put(
        ambulance,
        new CoordinateLine(ambulanceDestination, currentPosition)
            .setColor(color)
            .setVisible(checkShowPathLines.isSelected()));
    mapView.addCoordinateLine(destinationLines.get(ambulance));
  }

  private void updateAmbulanceUrgencyMarker() {
    /*if (ambulance.isAvailable()) {
        markerLabel.setVisible(false);
    } else if (!ambulance.isOffDuty()) {
        if (!ambulance.isAvailable() && !ambulance.isTransport()) {
            UrgencyLevel urgencyLevel = ambulance.getIncident().urgencyLevel();
            markerLabel
                    .setCssClass(urgencyLevel == UrgencyLevel.ACUTE ? "red-label" : "orange-label")
                    .setVisible(checkShowAmbulances.isSelected());
        } else if (!ambulance.isAvailable()
                && ambulance.isTransport()
                && ambulance.getDestination().equals(ambulance.getHospitalLocation())) {
            markerLabel.setCssClass("green-label").setVisible(checkShowAmbulances.isSelected());
        }
    }*/
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
            ambulance,
            new MapCircle(destinationCoordinate, 1000)
                .setColor(
                    ambulance.getIncident().urgencyLevel() == UrgencyLevel.ACUTE
                        ? Color.web("#ff0000", 0.7)
                        : Color.web("#ffff00", 0.7))
                .setVisible(checkShowIncidents.isSelected()));
        mapView.addMapCircle(destinationCircles.get(ambulance));
      }
    }
  }

  private void animateMarker(Marker marker, Coordinate oldPosition, Coordinate newPosition) {
    // animate the marker to the new position
    final Transition transition =
        new Transition() {
          private final Double oldPositionLongitude = oldPosition.getLongitude();
          private final Double oldPositionLatitude = oldPosition.getLatitude();
          private final double deltaLatitude = newPosition.getLatitude() - oldPositionLatitude;
          private final double deltaLongitude = newPosition.getLongitude() - oldPositionLongitude;

          {
            setCycleDuration(Duration.seconds(0.5));
            setOnFinished(evt -> marker.setPosition(newPosition));
          }

          @Override
          protected void interpolate(double v) {
            final var latitude = oldPosition.getLatitude() + v * deltaLatitude;
            final var longitude = oldPosition.getLongitude() + v * deltaLongitude;
            marker.setPosition(new Coordinate(latitude, longitude));
          }
        };
    transition.play();
  }

  private void updateAmbulancesNoMarkers(Collection<Ambulance> ambulanceList) {
    if (ambulanceIcon == null) {
      throw new IllegalStateException("Missing ambulance icon");
    }

    for (var ambulance : ambulanceList) {

      var coordinates =
          utmToLatLongMap.get(
              ambulance.getCurrentLocationVisualized(currentTimeInternal, utmToLatLongMap));
      /*logger.info(
      "{}: Updated pos when setting marker: {}", currentTimeInternal, coordinates.toString());*/
      var marker =
          new Marker(ambulanceIcon, -15, -15)
              .setPosition(coordinates)
              .setVisible(checkShowAmbulances.isSelected());

      ambulanceMarkers.put(ambulance, marker);
      /*
      var label = new MapLabel("Responding", -25, 20)
          .setVisible(false)
          .setCssClass("orange-label");
      marker.attachLabel(label).setVisible(true);
      */
      mapView.addMarker(marker);
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
  private void setCustomAllocation() {
    dayShift.setText("5");
    nightShift.setText("5");
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
    logger.info("Setting ambulance layer visibility to " + checkShowAmbulances.isSelected());
    ambulanceMarkers.values().forEach(mapView::removeMarker);
    ambulanceMarkers
        .values()
        .forEach(marker -> marker.setVisible(checkShowAmbulances.isSelected()));
    ambulanceMarkers.values().forEach(mapView::addMarker);
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
                    if (ChronoUnit.SECONDS.between(currentTimeInternal, currentTime) > 120) {
                      currentTimeInternal = currentTime;
                      updateAmbulances(ambulanceList);
                      updateIncidents(callQueue);
                      lastUiUpdate = System.currentTimeMillis();
                    }
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
