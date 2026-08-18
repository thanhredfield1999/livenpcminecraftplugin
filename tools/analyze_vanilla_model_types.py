import csv
from collections import Counter
from pathlib import Path

p = Path(r"F:\minecraftserver\Vanilla Item Icons 1.21.11\vanilla-item-model-catalog.csv")
rows = list(csv.DictReader(p.open(encoding="utf-8-sig")))
counts = Counter()
for row in rows:
    ref = row["model_reference"]
    if "cube_all" in ref:
        key = "cube_all"
    elif "cube" in ref:
        key = "cube_family"
    elif "fence_inventory" in ref:
        key = "fence_inventory"
    elif "generated" in ref:
        key = "generated"
    elif "handheld" in ref:
        key = "handheld"
    elif ref:
        key = ref
    else:
        key = "no_reference"
    counts[key] += 1
print("total", len(rows))
for key, value in counts.most_common():
    print(key, value)
print("examples")
for row in rows:
    if any(x in row["model_reference"] for x in ("cube_all", "fence_inventory", "generated")):
        print(row["item_id"], row["model_reference"])
        if sum(1 for r in rows if any(x in r["model_reference"] for x in ("cube_all", "fence_inventory", "generated"))) >= 20:
            break
