import json
import pandas as pd
from coordinate_converter import node_to_id


print("Getting missing coordinates")
missing = []
with open(f'data/od_complete_missing_grid_centroids.json', 'r') as r:
    od_missing = json.load(r)
    missing = list(od_missing.keys())

print("Loading all coordinates")
# get grid coordinates
grids = pd.read_csv("data/grid_centroids.csv")
grids = grids[["lat", "long"]]
grids["type"] = "grid"
# get base station coordinates
base_stations = pd.read_csv("data/base_stations.csv")
base_stations = base_stations[["latitude", "longitude"]]
base_stations.columns = ("lat", "long")
base_stations["type"] = "base_station"
# get base station coordinates
hospitals = pd.read_csv("data/hospitals.csv")
hospitals = hospitals[["lat", "long"]]
hospitals["type"] = "hospital"
# combine coordinates
combined = pd.concat([grids, base_stations, hospitals], ignore_index=True)
xs = combined["long"].values
ys = combined["lat"].values

print("Creating ssb ids from coordinates...")
ssb_ids = [node_to_id(id, type, xs, ys) for id, _, _, type in combined.itertuples()]

print("Loading od")
od = {}
with open(f'data/od_path_matrix_complete_with_missing.json', 'r') as r:
    od = json.load(r)

print("Replacing paths")
for id1 in missing:
    for id2 in ssb_ids:
        opposite = None
        if id1 == id2:
            opposite = {"travel_time": 0, "route": []}
        elif id2 not in od.keys() or id1 not in od[id2].keys():
            continue
        else:
            opposite = od[id2][id1]
        new = {
            "travel_time": opposite["travel_time"], 
            "route": opposite["route"][::-1]
            }
        if id1 in od.keys():
            od[id1][id2] = new
        else:
            od[id1] = {}
            od[id1][id2] = new


print("Saving od to file...")
with open(f'data/od_path_matrix_complete_final.json', 'w') as f:
    json.dump(od, f, indent=2)
print("Done.")