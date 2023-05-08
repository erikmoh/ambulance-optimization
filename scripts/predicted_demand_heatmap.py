import json
import pandas as pd

import map.geojson_tools as geojson_tools
import map.map_tools as map_tools
import map.styles as styles

import chromedriver_autoinstaller as chromedriver
chromedriver.install()

def process_base_stations():
  base_stations = pd.read_csv('data/base_stations.csv', encoding='utf-8', index_col=0)
  base_stations = base_stations[['easting', 'northing']]
  return base_stations


def process_predictions(day, hour):
  predictions_file = json.load(open('data/incidents_distribution/station_predictions.json', 'r'))
  predictions = {}
  for station in range(0, 19):
    prediction = predictions_file[str(station)][str(day)][str(hour)]
    predictions[station] = prediction
  return predictions


def process_truths(day, hour):
  truths_file = json.load(open('data/incidents_distribution/station_truths.json', 'r'))
  truths = {}
  for station in range(0, 19):
    truth = truths_file[str(station)][str(2017)][str(8)][str(day)][str(hour)]
    truths[station] = truth
  return truths


def process_grids(predictions):
  grids = pd.read_csv('data/grid_zones.csv', index_col=0)

  grids['prediction'] = grids['base_station'].map(predictions)
  grids = grids[['easting', 'northing', 'prediction']]

  empty_cells = pd.read_csv('data/empty_cells.csv', encoding='utf-8', index_col=0)
  empty_cells = empty_cells[['X', 'Y']].rename(columns={'X': 'easting', 'Y': 'northing'})
  empty_cells['easting'] = empty_cells['easting'].astype(int)
  empty_cells['northing'] = empty_cells['northing'].astype(int)

  grids = pd.concat([grids, empty_cells.assign(prediction=-1)])
  return grids


def main():
  base_stations = process_base_stations()
  points = geojson_tools.dataframe_to_points(base_stations)
  circle_markers = map_tools.create_circle_markers(points)

  day = 12
  for hour in range(0, 24):
    # predictions = process_predictions(day, hour)
    # grids = process_grids(predictions)
    truths = process_truths(day, hour)
    grids = process_grids(truths)

    filename = "base_station_demand_truth"

    features = geojson_tools.dataframe_to_squares(grids)
    geojson_tools.export_features(features, f'data/{filename}_{hour}.geojson')

    heatmap = map_tools.get_map()

    geojson = map_tools.get_geojson_items(f'data/{filename}_{hour}.geojson', styles.heatmap_prediction_style)
    geojson.add_to(heatmap)

    for circle_marker in circle_markers:
      circle_marker.add_to(heatmap)

    map_tools.export_map_with_chrome(heatmap, f'{filename}_{hour}')


def plot_color_limits():
  import matplotlib.pyplot as plt
  import numpy as np

  start = np.log10(0.1)
  end = np.log10(2.5)
  num_values = 6

  log_values = np.linspace(start, end, num_values)
  exp_values = np.power(10, log_values)

  x = np.linspace(0, num_values - 1, num_values)
  plt.plot(x, exp_values, 'o-')
  plt.xticks(x, ['1', '2', '3', '4', '5', '6'])
  plt.xlabel('Value index')
  plt.ylabel('Value')
  plt.title('Exponentially distributed values between 0.1 and 2.5')
  plt.show()


if __name__ == '__main__':
  #plot_color_limits()
  main()
