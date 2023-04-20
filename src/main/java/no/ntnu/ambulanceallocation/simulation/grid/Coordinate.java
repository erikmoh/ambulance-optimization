package no.ntnu.ambulanceallocation.simulation.grid;

public record Coordinate(int x, int y, long id) {

  private static final double FALSE_EASTING = 2_000_000.0;
  private static final double GRID_SIZE = 1_000.0;

  public Coordinate(int x, int y) {
    this(x, y, getId(x, y));
  }

  public Coordinate(long id) {
    this(getGridCentroidEastingFromId(id), getGridCentroidNorthingFromId(id), id);
  }

  @SuppressWarnings("CopyConstructorMissesField")
  public Coordinate(Coordinate coordinate) {
    this(coordinate.x(), coordinate.y(), coordinate.id());
  }

  public int timeTo(Coordinate other) {
    return (int) Math.round(DistanceIO.getDistance(this, other));
  }

  public double euclideanDistanceTo(Coordinate other) {
    return Math.hypot(this.x() - other.x(), this.y() - other.y());
  }

  public double manhattanDistanceTo(Coordinate other) {
    return Math.abs(this.x() - other.x()) + Math.abs(this.y() - other.y());
  }

  private static int getGridCornerEasting(int easting) {
    return (int) (Math.floor((easting + FALSE_EASTING) / GRID_SIZE) * GRID_SIZE - FALSE_EASTING);
  }

  private static int getGridCornerNorthing(int northing) {
    return (int) (Math.floor(northing / GRID_SIZE) * GRID_SIZE);
  }

  private static int getGridCentroidEastingFromId(long id) {
    return (int) (((int) (id / Math.pow(10, 7))) - 2 * Math.pow(10, 6) + GRID_SIZE / 2);
  }

  private static int getGridCentroidNorthingFromId(long id) {
    return (int) (id - ((int) (id / Math.pow(10, 7))) * Math.pow(10, 7) + GRID_SIZE / 2);
  }

  private static long getId(int easting, int northing) {
    var gridEasting = getGridCornerEasting(easting);
    var gridNorthing = getGridCornerNorthing(northing);
    return (long) (2 * Math.pow(10, 13) + gridEasting * Math.pow(10, 7) + gridNorthing);
  }
}
