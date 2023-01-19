import utm

from math import dist, floor, radians, sin, cos, asin, sqrt, inf

ZONE_NUMBER = 33
FALSE_EASTING = 2_000_000


def ssb_grid_id_to_utm_centroid(ssb_grid_id, grid_cell_size=1000):
    easting = floor(ssb_grid_id * (10**(-7))) - (2 * (10**6)) + (grid_cell_size / 2)
    northing = ssb_grid_id - (floor(ssb_grid_id * (10**(-7))) * (10**7)) + (grid_cell_size / 2)
    return easting, northing


def utm_to_ssb_grid_id(x: int, y: int, grid_cell_size=1000) -> int:
    Xf = x + FALSE_EASTING
    XfK = Xf / grid_cell_size
    Xcorner = floor(XfK) * grid_cell_size - FALSE_EASTING
    YK = y / grid_cell_size
    Ycorner = (floor(YK) * grid_cell_size)
    return 20_000_000_000_000 + (Xcorner * 10_000_000) + Ycorner


def latitude_longitude_to_utm(latitude, longitude):
    easting, northing, _, _, = utm.from_latlon(latitude, longitude, ZONE_NUMBER)
    return easting, northing


def snap_utm_to_ssb_grid(utm_coordinate, grid_cell_size=1000):
    easting, northing = utm_coordinate
    return (
        floor((easting + FALSE_EASTING) / grid_cell_size) * grid_cell_size - FALSE_EASTING,
        floor(northing / grid_cell_size) * grid_cell_size
    )


def utm_to_longitude_latitude(utm_coordinate, zone_number=ZONE_NUMBER, northern=True):
    """Converts (zone 33W) UTM coordinates to latitude and longitude

        Parameters
        ----------
        utm_coordinate: tuple[int]
            Standard UTM coordinate (E, N) with zone 33W

        Returns
        -------
        [latitude, longitude]: list[float]
            Latitude and longitude of the coordinate
    """
    easting, northing = utm_coordinate
    return list(utm.to_latlon(easting, northing, zone_number, northern=northern, strict=False)[::-1])


def utm_to_latitude_longitude(utm_coordinate, zone_number=ZONE_NUMBER, northern=True):
    """Converts (zone 33W) UTM coordinates to latitude and longitude

        Parameters
        ----------
        utm_coordinate: tuple[int]
            Standard UTM coordinate (E, N) with zone 33W

        Returns
        -------
        [latitude, longitude]: list[float]
            Latitude and longitude of the coordinate
    """
    easting, northing = utm_coordinate
    return list(utm.to_latlon(easting, northing, zone_number, northern=northern, strict=False))


def utm_distance(start, end):
    """Computes the distance between two (zone 33W) UTM coordinates

        Parameters
        ----------
        start: tuple[int]
            Start point using the standard UTM coordinate (E, N) with zone 33W
        end: tuple[int]
            End point using the standard UTM coordinate (E, N) with zone 33W

        Returns
        -------
        distance: float
            Distance between start and end
    """
    return dist(start, end)


def node_to_id(node_id, type, xs, ys):
    lat = ys[node_id]
    long = xs[node_id]
    easting, northing, _, _ = utm.from_latlon(lat, long, ZONE_NUMBER)
    if type == "grid":
        return str(utm_to_ssb_grid_id(easting, northing))
    return f"_{easting:.0f}_{northing:.0f}"


def calc_dist(lon1, lat1, lon2, lat2):
    """
    Calculate the great circle distance between two points 
    on the earth (specified in decimal degrees)
    """
    # convert decimal degrees to radians 
    lon1, lat1, lon2, lat2 = map(radians, [lon1, lat1, lon2, lat2])
    # haversine formula 
    dlon = lon2 - lon1 
    dlat = lat2 - lat1 
    a = sin(dlat/2)**2 + cos(lat1) * cos(lat2) * sin(dlon/2)**2
    c = 2 * asin(sqrt(a)) 
    # Radius of earth in kilometers is 6371
    km = 6371* c
    return km