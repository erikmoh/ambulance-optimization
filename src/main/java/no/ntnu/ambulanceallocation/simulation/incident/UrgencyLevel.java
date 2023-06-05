package no.ntnu.ambulanceallocation.simulation.incident;

import java.util.HashMap;
import java.util.Map;

public enum UrgencyLevel {
  ACUTE("A", 0.26, 0.139, false),
  URGENT("H", 4.0, 0.05, false),
  REGULAR("V", 8.0, 0.0, true),
  REGULAR_UNPLANNED("V1", 8.0, 0.0, true),
  REGULAR_PLANNED("V2", 8.0, 0.0, true);

  private final String symbol;
  private final double coefficient1;
  private final double coefficient2;
  private final boolean regular;

  // Map for getting an urgency level from a symbol
  private static final Map<String, UrgencyLevel> urgencyLevels = new HashMap<>();

  UrgencyLevel(
      String symbol, double survivalCoefficient1, double survivalCoefficient2, boolean regular) {
    this.symbol = symbol;
    this.coefficient1 = survivalCoefficient1;
    this.coefficient2 = survivalCoefficient2;
    this.regular = regular;
  }

  public String getSymbol() {
    return symbol;
  }

  public Double getCoefficient1() {
    return coefficient1;
  }

  public Double getCoefficient2() {
    return coefficient2;
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
