import pandas as pd
import json
from tqdm import tqdm

from coordinate_converter import utm_to_ssb_grid_id


MAX_DISTANCE = 10 # min


# get grid coordinates
grids = pd.read_csv("data/grid_centroids.csv")

print("Loading od_paths")
od_paths = {}
with open(f'data/od_paths.json', 'r') as r:
    od_paths = json.load(r)

neighbours = {}

for x1, y1, lat1, lon1 in tqdm(grids.values, desc="Finding grid neighbours"):
    start = str(utm_to_ssb_grid_id(x1, y1))
    neighbours[start] = {}
    for x2, y2, lat2, lon2 in grids.values:
        end = str(utm_to_ssb_grid_id(x2, y2))
        distance = round(od_paths[start][end]["travel_time"]/60, 2)
        if distance < MAX_DISTANCE:
            neighbours[start][end] = distance

print("Saving closest_neighbours to file...")
with open(f'data/closest_neighbours.json', 'w') as f:
    json.dump(neighbours, f, indent=2)
