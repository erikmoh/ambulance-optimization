import json
import pandas as pd
from coordinate_converter import utm_to_ssb_grid_id

grids = pd.read_csv("data/grid_centroids.csv")
grids = grids[["xcoor","ycoor"]]

ssbids = [str(utm_to_ssb_grid_id(easting, northing)) for _, easting, northing in grids.itertuples()]

od = {}
with open(f'data/od_path_matrix_complete.json', 'r') as r:
    od = json.load(r)

missing = {}
for id1 in ssbids:
    if id1 not in od.keys():
        missing[id1] = {}
    else:
        for id2 in ssbids:
            if id2 not in od[id1].keys():
                if id1 in missing.keys():
                    missing[id1][id2] = 0
                else:
                    missing[id1] = {}
                    missing[id1][id2] = 0

print("Saving od to file...")
with open(f'data/od_complete_missing_grid_centroids.json', 'w') as f:
    json.dump(missing, f, indent=2)
print("Done.")