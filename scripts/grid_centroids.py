import pandas as pd

import map.geojson_tools as geojson_tools


def apply_utm_to_longitude_latitude(row):
  row['lat'], row['long'] = geojson_tools.utm_to_latitude_longitude(
    (row.xcoor, row.ycoor))
  return row


def main():
  incidents = pd.read_csv('proprietary_data/processed_data.csv',
                          index_col=False)
  centroids = incidents.groupby(['xcoor', 'ycoor'], as_index=False).size()
  centroids = centroids.apply(apply_utm_to_longitude_latitude, axis=1)
  centroids = centroids.drop(['size'], axis=1)
  centroids.to_csv('data/grid_centroids.csv', index=False)


if __name__ == '__main__':
  main()
