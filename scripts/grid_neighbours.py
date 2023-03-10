import pandas as pd
import json
from tqdm import tqdm

from coordinate_converter import utm_to_ssb_grid_id


MAX_DISTANCE = 10 # min


# get grid coordinates
grids = pd.read_csv("data/grid_centroids.csv")
grids["type"] = "grid"
# get base station coordinates
base_stations = pd.read_csv("data/base_stations.csv")
base_stations = base_stations[["easting", "northing", "latitude", "longitude"]]
base_stations.columns = ("xcoor", "ycoor", "lat", "long")
base_stations["type"] = "base_station"
# get base station coordinates
hospitals = pd.read_csv("data/hospitals.csv")
hospitals = hospitals[["xcoor", "ycoor", "lat", "long"]]
hospitals.columns = ("xcoor", "ycoor", "lat", "long")
hospitals["type"] = "hospital"
# combine coordinates
all = pd.concat([grids, base_stations, hospitals], ignore_index=True)

print("Loading od_paths")
od_paths = {}
with open(f'data/od_paths.json', 'r') as r:
    od_paths = json.load(r)

neighbours = {}

for x1, y1, lat1, lon1, type in tqdm(all.values, desc="Finding grid neighbours"):
    start = str(utm_to_ssb_grid_id(x1, y1))
    if type != "grid":
        start = f"_{x1:.0f}_{y1:.0f}"
    neighbours[start] = {}
    for x2, y2, lat2, lon2, _ in grids.values:
        end = str(utm_to_ssb_grid_id(x2, y2))
        distance = round(od_paths[start][end]["travel_time"]/60, 2)
        if distance < MAX_DISTANCE:
            neighbours[start][end] = distance

print("Saving closest_neighbours to file...")
with open(f'data/closest_neighbours.json', 'w') as f:
    json.dump(neighbours, f, indent=2)
