import json
import pandas as pd


FILE = "data/incidents_distribution_station_count_test"


csv_df = pd.read_csv(FILE + ".csv", encoding='utf-8', escapechar='\\')

d = {}
for station in range(0, 19):
    d[station] = {}
    for day in range(7, 14):
        d[station][day] = {}
        for hour in range(0, 24):
            d[station][day][hour] = {}

for index, row in csv_df.iterrows():
    station = row["Base Station"]
    day = row["Day"]
    hour = row["Hour"]
    incidents = row["Incidents"]

    d[station][day][hour] = int(incidents)

with open(FILE + ".json", 'w') as f:
    json.dump(d, f, indent=2)