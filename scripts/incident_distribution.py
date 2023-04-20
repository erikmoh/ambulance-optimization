import copy
import csv
import json
import pandas as pd
from tqdm import tqdm
from datetime import datetime

from processing import filter_urgency_levels, convert_to_datetime, filter_erroneous_timestamps, aggregate_concurrent_incidents, save_data, set_index, sort_index, filter_regions, keep_period_of_interest
from coordinate_converter import utm_to_ssb_grid_id


CREATE_NEW_PROCESSED = False
PROCESSED_FILE = "data/incidents_all_processed.csv"
DISTRIBUTION_FILE = "data/incidents_distribution_station_truths.json"

FIELDS = ['tidspunkt', 'varslet', 'rykker_ut', 'ank_hentested',
                'avg_hentested',
                'ank_levsted', 'ledig', 'xcoor', 'ycoor', 'hastegrad',
                'tiltak_type', 'ssbid1000M']
FEATURES_KEEP = ['tidspunkt', 'xcoor', 'ycoor']

DISTRIBUTION_FILE_CSV = "data/incidents_distribution_station_truths.csv"
CSV_COLUMNS = ['Base Station','Year','Month','Day','Week','Weekday','Hour','Incidents']


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
    # grids = pd.read_csv("data/grid_centroids.csv")
    base_stations = pd.read_csv("data/base_stations.csv")

    incident_distribution = {}

    time = {}
    for year in range(2015, 2019):
        time[year] = {}
        for month in range(1, 13):
            time[year][month] = {}
            for day in range(6, 14):
                time[year][month][day] = {}
                for hour in range(0, 24):
                    time[year][month][day][hour] = 0

    for station in base_stations.values:
        incident_distribution[int(station[0])] = copy.deepcopy(time)

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

    grid_zones = pd.read_csv("data/grid_zones.csv")

    for incident in tqdm(df.values, desc="Count incidents per hour"):
        date = datetime.strptime(incident[0], '%Y-%m-%d %H:%M:%S')
        if date > datetime(2017, 8, 5, 23, 59, 59) and date < datetime(2017, 8, 14):
            #weekday = date.weekday() + 1
            incident_grid = utm_to_ssb_grid_id(int(incident[1]), int(incident[2]))
            try:
                incident_station = grid_zones.loc[grid_zones["SSBID1000M"] == incident_grid, "base_station"].iloc[0]
            except:
                print(f"grid {incident_grid} was not in grid_zones.csv")
                continue
            incident_distribution[incident_station][date.year][date.month][date.day][date.hour] += 1

        """ if weekday != prev_weekday:
            if weekday - prev_weekday != 1 and weekday - prev_weekday != -6 and prev_weekday != 0:
                print("Skipped one or more days")
            num_weekdays[date.month][weekday] += 1
            prev_weekday = weekday """


def average_count(incident_distribution, num_weekdays):
    for station_id in tqdm(incident_distribution.keys(), desc="Change to average per day"):
        for month in incident_distribution[station_id].keys():
            for weekday in incident_distribution[station_id][month].keys():
                for hour in incident_distribution[station_id][month][weekday].keys():
                    count = incident_distribution[station_id][month][weekday][hour]
                    if count > 0:
                        num_weekday_month = num_weekdays[month][weekday]
                        weekday_in_month_average = count/num_weekday_month
                        incident_distribution[station_id][month][weekday][hour] = round(weekday_in_month_average, 4)


def save_distribution_to_csv(distribution):
    with open(DISTRIBUTION_FILE_CSV, 'w', newline='') as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames=CSV_COLUMNS)
        writer.writeheader()
        for station_id in distribution.keys():
            for year in distribution[station_id].keys():
                for month in distribution[station_id][year].keys():
                    for day in distribution[station_id][year][month].keys():
                        for hour in distribution[station_id][year][month][day].keys():
                            count = distribution[station_id][year][month][day][hour]
                            try:
                                date = datetime(year, month, day)
                                if date > datetime(2017, 8, 5, 23, 59, 59) and date < datetime(2017, 8, 14):
                                    week = date.isocalendar().week
                                    weekday = date.isocalendar().weekday
                                    row = {
                                        'Base Station': station_id,
                                        'Year': year,
                                        'Month': month, 
                                        'Day': day,
                                        'Week': week,
                                        'Weekday': weekday,
                                        'Hour': hour, 
                                        'Incidents': count
                                    }
                                    writer.writerow(row)
                            except:
                                break


def main():
    df = get_processed_file()
    df = df[FEATURES_KEEP]

    print("Creating empty dictionaries for counts")
    incident_distribution = create_empty_distribution()
    num_weekdays = create_num_weekdays()

    count_incidents(df, incident_distribution, num_weekdays)
    #average_count(incident_distribution, num_weekdays)

    print("Saving distribution to file...")
    with open(DISTRIBUTION_FILE, 'w') as f:
        json.dump(incident_distribution, f, indent=2)
        
    print("Saving distribution to csv")
    save_distribution_to_csv(incident_distribution)


main()