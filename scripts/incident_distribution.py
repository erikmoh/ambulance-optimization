import copy
import json
import pandas as pd
from tqdm import tqdm
from datetime import datetime

from processing import filter_urgency_levels, convert_to_datetime, filter_erroneous_timestamps, aggregate_concurrent_incidents, save_data, set_index, sort_index, filter_regions, keep_period_of_interest
from coordinate_converter import utm_to_ssb_grid_id


CREATE_NEW_PROCESSED = False
PROCESSED_FILE = "data/incidents_all_processed.csv"
DISTRIBUTION_FILE = "data/incidents_distribution.json"

FIELDS = ['tidspunkt', 'varslet', 'rykker_ut', 'ank_hentested',
                'avg_hentested',
                'ank_levsted', 'ledig', 'xcoor', 'ycoor', 'hastegrad',
                'tiltak_type', 'ssbid1000M']
FEATURES_KEEP = ['tidspunkt', 'xcoor', 'ycoor']


def get_processed_file():
    if CREATE_NEW_PROCESSED:
        return create_processed_file()
    return pd.read_csv(PROCESSED_FILE, encoding='utf-8', escapechar='\\', parse_dates=True)
    

def create_processed_file():
    df = pd.read_csv("proprietary_data/cleaned_data.csv", encoding='utf-8', escapechar='\\', 
                     usecols=FIELDS, parse_dates=True)
    print("Filter regions")
    df = filter_regions(df)
    print("Convert datetime")
    df = convert_to_datetime(df)
    print("Filter years")
    df = keep_period_of_interest(df)
    print("Filter urgency")
    df = filter_urgency_levels(df)
    print("Filter erronous timestamps")
    df = filter_erroneous_timestamps(df)
    print("Aggregate concurrent incidents")
    df = aggregate_concurrent_incidents(df)
    df = set_index(df)
    df = sort_index(df)

    print("Save processed file")
    save_data(df, PROCESSED_FILE)

    return df


def create_empty_distribution():
    grids = pd.read_csv("data/grid_centroids.csv")

    incident_distribution = {}

    time = {}
    for month in range(1, 13):
        time[month] = {}
        for dayweek in range(1, 8):
            time[month][dayweek] = {}
            for hour in range(0, 24):
                time[month][dayweek][hour] = 0

    for grid in grids.values:
        ssb_grid_id = utm_to_ssb_grid_id(int(grid[0]), int(grid[1]))
        incident_distribution[ssb_grid_id] = copy.deepcopy(time)

    return incident_distribution


def create_num_weekdays():
    num_weekdays = {}
    for month in range(1, 13):
        num_weekdays[month] = {}
        for dayweek in range(1, 8):
            num_weekdays[month][dayweek] = 0
    return num_weekdays


def count_incidents(df, incident_distribution, num_weekdays):
    prev_weekday = 0

    for incident in tqdm(df.values, desc="Count incidents per day"):
        dt = datetime.strptime(incident[0], '%Y-%m-%d %H:%M:%S')
        weekday = dt.weekday() + 1
        incident_grid = utm_to_ssb_grid_id(int(incident[1]), int(incident[2]))
        if incident_grid not in incident_distribution.keys():
            continue
        incident_distribution[incident_grid][dt.month][weekday][dt.hour] += 1

        if weekday != prev_weekday:
            if weekday - prev_weekday != 1 and weekday - prev_weekday != -6 and prev_weekday != 0:
                print("Skipped one or more days")
            num_weekdays[dt.month][weekday] += 1
            prev_weekday = weekday


def average_count(incident_distribution, num_weekdays):
    for grid_id in tqdm(incident_distribution.keys(), desc="Change to average per day"):
        for month in incident_distribution[grid_id].keys():
            for weekday in incident_distribution[grid_id][month].keys():
                for hour in incident_distribution[grid_id][month][weekday].keys():
                    count = incident_distribution[grid_id][month][weekday][hour]
                    if count > 0:
                        num_weekday_month = num_weekdays[month][weekday]
                        weekday_in_month_average = count/num_weekday_month
                        incident_distribution[grid_id][month][weekday][hour] = weekday_in_month_average

def main():
    df = get_processed_file()
    df = df[FEATURES_KEEP]

    print("Creating empty dictionaries for counts")
    incident_distribution = create_empty_distribution()
    num_weekdays = create_num_weekdays()

    count_incidents(df, incident_distribution, num_weekdays)
    average_count(incident_distribution, num_weekdays)

    print("Saving distribution to file...")
    with open(DISTRIBUTION_FILE, 'w') as f:
        json.dump(incident_distribution, f, indent=2)
    

main()