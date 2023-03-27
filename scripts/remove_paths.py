import json
from tqdm import tqdm


od = {}
print("Loading od path matrix")
with open(f'data/od_paths_updated.json', 'r') as r:
    od = json.load(r)

print("Removing source")
od.pop("_279154_6657789")

print("Removing targets")
for source in tqdm(od.keys()):
    if source == 'update_period_minutes':
        continue
    targets = od[source]
    try:
        targets.pop("_279154_6657789")
    except:
        continue

print("Saving od to file...")
with open(f'data/od_paths_updated_2.json', 'w') as f:
    json.dump(od, f, indent=2)
print("Done.")