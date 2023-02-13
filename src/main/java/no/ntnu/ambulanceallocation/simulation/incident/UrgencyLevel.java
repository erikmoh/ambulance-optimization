package no.ntnu.ambulanceallocation.simulation.incident;

import java.util.HashMap;
import java.util.Map;

public enum UrgencyLevel {
  ACUTE("A", 2.0),
  URGENT("H", 6.0),
  REGULAR("V", 8.0),
  REGULAR_UNPLANNED("V1", 8.0),
  REGULAR_PLANNED("V2", 8.0);

  private final String symbol;
  private final double coefficient;

  // Map for getting an urgency level from a symbol
  private static final Map<String, UrgencyLevel> urgencyLevels = new HashMap<>();

  UrgencyLevel(String symbol, double survivalCoefficient) {
    this.symbol = symbol;
    this.coefficient = survivalCoefficient;
  }

  public String getSymbol() {
    return symbol;
  }

  public Double getCoefficient() {
    return coefficient;
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
