import pandas as pd

import map.geojson_tools as geojson_tools
import map.map_tools as map_tools
import map.styles as styles

import chromedriver_autoinstaller as chromedriver
chromedriver.install()


def calltime_to_datetime(df):
  df['tidspunkt'] = pd.to_datetime(df['tidspunkt'], dayfirst=True)
  df = df.set_index('tidspunkt')
  return df


def keep_period_of_interest(df, buffer_size=0):
  time = df.index + pd.Timedelta(hours=buffer_size)
  df_period = df[(time.year == 2017) & (time.week == 32)]
  return df_period


def process_dataframe():
  incidents = pd.read_csv('proprietary_data/processed_distribution.csv', index_col=False)
  incidents = calltime_to_datetime(incidents)
  incidents = keep_period_of_interest(incidents)

  counts = incidents.groupby(['xcoor', 'ycoor'], as_index=False).size()
  counts['counts'] = counts['size']
  counts.drop(['size'], axis=1, inplace=True)

  empty_cells = pd.read_csv('data/empty_cells.csv', encoding='utf-8', index_col=False)
  empty_cells = empty_cells[['X', 'Y']].rename(columns={'X': 'xcoor', 'Y': 'ycoor'})

  counts = pd.concat([counts, empty_cells.assign(counts=0)])
  counts = counts.sort_values('counts')

  return counts


def main():
  df = process_dataframe()

  features = geojson_tools.dataframe_to_squares(df)
  geojson_tools.export_features(features, 'data/grid.geojson')

  heatmap = map_tools.get_map()

  geojson = map_tools.get_geojson_items('data/grid.geojson',
                                        styles.heatmap_style)
  geojson.add_to(heatmap)

  map_tools.export_map_with_chrome(heatmap, "heatmap")


if __name__ == '__main__':
  main()
