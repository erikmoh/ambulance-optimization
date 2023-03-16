import pandas as pd
import matplotlib.pyplot as plt
from tqdm import tqdm
from statistics import median

print("loading data")
df = pd.read_csv("proprietary_data/cleaned_data.csv", encoding='utf-8', escapechar='\\', parse_dates=True, low_memory=False)

print("calculating delay")
df["delay"] = pd.to_datetime(df["rykker_ut"]) - pd.to_datetime(df["varslet"])

urgencies = [["A"], ["H"], ["V", "V1", "V2"]]

stop_point = -1
time_max = 60*15
time_min = 60

medians = []

print("filter and sort:")
for urgency in tqdm(urgencies):
    df_urgency = df[df["hastegrad"].isin(urgency)]
    df_urgency = pd.TimedeltaIndex(data = df_urgency["delay"])
    df_urgency = df_urgency.seconds.values
    df_urgency = [u for u in df_urgency if time_min < u < time_max]
    df_urgency.sort()
    df_urgency = df_urgency[:stop_point]
    medians.append(median(df_urgency))
    plt.plot(range(len(df_urgency)), df_urgency, label=urgency[0])

print(medians)

plt.legend()
plt.show()