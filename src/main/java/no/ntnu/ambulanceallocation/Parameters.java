package no.ntnu.ambulanceallocation;

import java.time.LocalDateTime;
import java.time.LocalTime;
import no.ntnu.ambulanceallocation.optimization.ga.ConstraintStrategy;
import no.ntnu.ambulanceallocation.optimization.initializer.Initializer;
import no.ntnu.ambulanceallocation.simulation.dispatch.DispatchDelay;
import no.ntnu.ambulanceallocation.simulation.dispatch.DispatchPolicy;
import no.ntnu.ambulanceallocation.simulation.dispatch.HandlingDelay;
import no.ntnu.ambulanceallocation.simulation.incident.IncidentDistribution;

public final class Parameters {
  // General
  public static final int RUNS = 10;
  public static final int MAX_RUNNING_TIME = (int) (4.0 * 60); // 4 minutes

  // Simulation
  public static final int BUFFER_SIZE = 4; // hours
  public static final LocalDateTime START_DATE_TIME = LocalDateTime.of(2017, 8, 7, 0, 0, 0);
  public static final LocalDateTime END_DATE_TIME = LocalDateTime.of(2017, 8, 14, 0, 0, 0);

  public static final int NUMBER_OF_AMBULANCES_DAY = 45;
  public static final int NUMBER_OF_AMBULANCES_NIGHT = 29;

  public static final LocalTime DAY_SHIFT_START = LocalTime.of(8, 0);
  public static final LocalTime NIGHT_SHIFT_START = LocalTime.of(20, 0);

  public static final ConstraintStrategy CONSTRAINT_STRATEGY = ConstraintStrategy.NONE;
  public static final DispatchPolicy DISPATCH_POLICY = DispatchPolicy.CoveragePredictedDemand;
  public static final DispatchDelay DISPATCH_DELAY = DispatchDelay.HISTORIC_MEDIAN;
  public static final HandlingDelay HANDLING_DELAY = HandlingDelay.HISTORIC_MEDIAN;
  public static final boolean HISTORIC_HOSPITAL_TIME = true;
  public static final IncidentDistribution INCIDENT_DISTRIBUTION = IncidentDistribution.PREDICTION;
  public static final boolean ENABLE_REDISPATCH = true;
  public static final boolean ENABLE_QUEUE_NEXT = true;

  public static final int UPDATE_LOCATION_PERIOD = 5; // minutes

  // SLS
  public static final int MAX_TRIES = 999;
  public static final double RESTART_PROBABILITY = 0.025;
  public static final double NOISE_PROBABILITY = 0.8;

  // Genetic & Memetic Algorithm
  public static final Initializer INITIALIZER =
      new no.ntnu.ambulanceallocation.optimization.initializer.Random();
  public static final int ISLANDS = 0; // set to 0 to disable islands
  public static final int GENERATIONS_ISLAND = 90;
  public static final int GENERATIONS_COMBINED = 9999;
  public static final int POPULATION_SIZE = 200;
  public static final int ELITE_SIZE = 10;
  public static final int TOURNAMENT_SIZE = 6;
  public static final int TOURNAMENT_TUNE_END = 30;
  public static final int DIVERSITY_LIMIT = 0;
  public static final int DIVERSIFY_GENERATIONS = 99999; // generations without improvement
  public static final int RESET_GENERATIONS = 100; // generations without improvement
  public static final boolean CROWDING = false;
  public static final boolean DISTINCT = true;

  public static final double CROSSOVER_TUNE_START = 0.8;
  public static final double CROSSOVER_PROBABILITY = 0.1;
  public static final double MUTATION_TUNE_START = 0.05;
  public static final double MUTATION_PROBABILITY = 0.014;
  public static final double IMPROVE_PROBABILITY = 0.25;

  public static final boolean INCLUDE_REGULAR_INCIDENTS = true;
  public static final boolean USE_URGENCY_FITNESS = true;
  public static final boolean PRESET_URGENCY = false;
  public static final double PRESET_URGENCY_PROBABILITY = 0.75; // change acute to urgent
}
