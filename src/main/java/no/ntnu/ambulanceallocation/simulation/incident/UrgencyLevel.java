package no.ntnu.ambulanceallocation.simulation.incident;

import java.util.HashMap;
import java.util.Map;

public enum UrgencyLevel {
  ACUTE("A", 2.0, false),
  URGENT("H", 6.0, false),
  REGULAR("V", 8.0, true),
  REGULAR_UNPLANNED("V1", 8.0, true),
  REGULAR_PLANNED("V2", 8.0, true);

  private final String symbol;
  private final double coefficient;
  private final boolean regular;

  // Map for getting an urgency level from a symbol
  private static final Map<String, UrgencyLevel> urgencyLevels = new HashMap<>();

  UrgencyLevel(String symbol, double survivalCoefficient, boolean regular) {
    this.symbol = symbol;
    this.coefficient = survivalCoefficient;
    this.regular = regular;
  }

  public String getSymbol() {
    return symbol;
  }

  public Double getCoefficient() {
    return coefficient;
  }

  public boolean isRegular() {
    return regular;
  }

  public static UrgencyLevel get(String symbol) {
    return urgencyLevels.get(symbol);
  }

  static {
    for (var urgencyLevel : UrgencyLevel.values()) {
      urgencyLevels.put(urgencyLevel.getSymbol(), urgencyLevel);
    }
  }

  @Override
  public String toString() {
    return symbol;
  }
}
