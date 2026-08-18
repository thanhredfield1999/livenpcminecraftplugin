import csv
import json
import os
from pathlib import Path

marker_path = Path(r"F:\minecraftserver\villagedefense2026\bluemap\web\maps\stillcliff\live\markers.json")
root = Path(r"F:\minecraftserver\Vanilla Item Icons 1.21.11")
markers = json.loads(marker_path.read_text(encoding="utf-8"))
rows = []
for marker in markers.get("livingnpc-observatory", {}).get("markers", {}).values():
    detail = marker.get("detail", "").replace("\\u003c", "<").replace("\\u003e", ">")
    data = {}
    for line in detail.split("<br>"):
        if "=" in line:
            key, value = line.split("=", 1)
            data[key] = value
    if data.get("kind") != "VILLAGE_ECONOMY":
        continue
    for entry in data.get("inventory", "").split(","):
        if not entry:
            continue
        item, _, amount = entry.rpartition(":")
        rows.append({"village": data.get("villageId", ""), "item_id": item, "amount": amount})

catalog = {}
with (root / "vanilla-item-model-catalog.csv").open(encoding="utf-8-sig") as handle:
    for row in csv.DictReader(handle):
        catalog[row["item_id"]] = row

generated = set()
with (root / "generated-item-models.csv").open(encoding="utf-8-sig") as handle:
    generated = {row["item_id"] for row in csv.DictReader(handle)}
for row in rows:
    item = row["item_id"]
    info = catalog.get(item, {})
    direct = root / "icons" / "fish" / f"{item}.png"
    if not direct.exists():
        direct = next(root.glob(f"icons/*/{item}.png"), None)
    generated_path = root / "icons" / "generated_item_model" / f"{item}.png"
    if direct and direct.exists():
        status = "direct_png"
        file = str(direct)
    elif generated_path.exists():
        status = "generated_layer0"
        file = str(generated_path)
    else:
        status = info.get("kind", "missing")
        file = ""
    row.update({"catalog_kind": info.get("kind", "missing"), "status": status, "file": file})

out = root / "live-economy-icon-report.csv"
with out.open("w", newline="", encoding="utf-8-sig") as handle:
    writer = csv.DictWriter(handle, fieldnames=["village", "item_id", "amount", "catalog_kind", "status", "file"])
    writer.writeheader()
    writer.writerows(rows)

print(f"economy_items={len(rows)}")
for row in rows:
    print(f"{row['item_id']} amount={row['amount']} status={row['status']}")
print(f"report={out}")
print(f"missing={sum(row['status'] not in {'direct_png', 'generated_layer0'} for row in rows)}")

if __name__ == "__main__":
    pass
