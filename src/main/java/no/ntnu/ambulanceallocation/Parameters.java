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
  public static final int RUNS = 1;
  public static final int MAX_RUNNING_TIME = (int) (6.0 * 60); // 6 minutes

  // Simulation
  public static final int BUFFER_SIZE = 4; // hours
  public static final LocalDateTime START_DATE_TIME = LocalDateTime.of(2017, 8, 7, 0, 0, 0);
  public static final LocalDateTime END_DATE_TIME = LocalDateTime.of(2017, 8, 14, 0, 0, 0);

  public static final int NUMBER_OF_AMBULANCES_DAY = 45;
  public static final int NUMBER_OF_AMBULANCES_NIGHT = 29;

  public static final LocalTime DAY_SHIFT_START = LocalTime.of(8, 0);
  public static final LocalTime NIGHT_SHIFT_START = LocalTime.of(20, 0);

  public static final ConstraintStrategy CONSTRAINT_STRATEGY = ConstraintStrategy.NONE;
  public static final DispatchPolicy DISPATCH_POLICY = DispatchPolicy.CoverageBaseStation;
  public static final DispatchDelay DISPATCH_DELAY = DispatchDelay.HISTORIC_MEDIAN;
  public static final HandlingDelay HANDLING_DELAY = HandlingDelay.HISTORIC_MEDIAN;
  public static final boolean HISTORIC_HOSPITAL_TIME = false;
  public static final IncidentDistribution INCIDENT_DISTRIBUTION = IncidentDistribution.PREDICTION;
  public static final boolean ENABLE_REDISPATCH = false;
  public static final boolean ENABLE_QUEUE_NEXT = false;

  public static final int UPDATE_LOCATION_PERIOD = 5; // minutes

  // SLS
  public static final int MAX_TRIES = 999;
  public static final double RESTART_PROBABILITY = 0.025;
  public static final double NOISE_PROBABILITY = 0.8;

  // Genetic & Memetic Algorithm
  public static final Initializer INITIALIZER =
      new no.ntnu.ambulanceallocation.optimization.initializer.Mix();
  public static final int ISLANDS = 5;
  public static final int GENERATIONS_ISLAND = 100;
  public static final int GENERATIONS_COMBINED = 999;
  public static final int POPULATION_SIZE = 100;
  public static final int ELITE_SIZE = 100;
  public static final int TOURNAMENT_SIZE = 4;
  public static final int DIVERSITY_LIMIT = 2;
  public static final int DIVERSIFY_GENERATIONS = 30; // generations without improvement
  public static final int RESET_GENERATIONS = 100; // generations without improvement

  public static final double CROSSOVER_PROBABILITY = 0.2;
  public static final double MUTATION_PROBABILITY = 0.014;
  public static final double IMPROVE_PROBABILITY = 0.25;

  public static final boolean INCLUDE_REGULAR_INCIDENTS = true;
  public static final boolean USE_URGENCY_FITNESS = true;
  public static final boolean PRESET_URGENCY = false;
  public static final double PRESET_URGENCY_PROBABILITY = 0.75; // change acute to urgent
}
