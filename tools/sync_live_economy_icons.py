import csv
import json
import shutil
from pathlib import Path

MARKERS = Path(r"F:\minecraftserver\villagedefense2026\bluemap\web\maps\stillcliff\live\markers.json")
SOURCE = Path(r"F:\minecraftserver\Vanilla Item Icons 1.21.11\icons")
DEST = Path(r"F:\minecraftserver\villagedefense2026\bluemap\web\livingnpc-icons")


def items_from_markers():
    payload = json.loads(MARKERS.read_text(encoding="utf-8"))
    result = set()
    for marker in payload.get("livingnpc-observatory", {}).get("markers", {}).values():
        detail = marker.get("detail", "")
        fields = {}
        for line in detail.split("<br>"):
            if "=" in line:
                key, value = line.split("=", 1)
                fields[key] = value
        if fields.get("kind") == "VILLAGE_ECONOMY":
            for entry in fields.get("inventory", "").split(","):
                item, _, _ = entry.rpartition(":")
                if item:
                    result.add(item)
    return sorted(result)


def find_source(item):
    matches = sorted(SOURCE.glob(f"*/{item}.png"))
    if matches:
        return matches[0]
    rendered = SOURCE / "building" / "rendered_item_model" / f"{item}.png"
    return rendered if rendered.is_file() else None


def main():
    rows = []
    for item in items_from_markers():
        source = find_source(item)
        if source is None:
            rows.append({"item_id": item, "status": "missing_source", "source": "", "web_file": ""})
            continue
        category = "building" if source.parent.name == "rendered_item_model" else source.parent.name
        target_dir = DEST / category
        target_dir.mkdir(parents=True, exist_ok=True)
        target = target_dir / source.name
        shutil.copy2(source, target)
        rows.append({"item_id": item, "status": "synced", "source": str(source), "web_file": f"/livingnpc-icons/{category}/{source.name}"})
    report = DEST / "live-economy-icon-map.csv"
    with report.open("w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.DictWriter(handle, fieldnames=["item_id", "status", "source", "web_file"])
        writer.writeheader()
        writer.writerows(rows)
    print(f"items={len(rows)} synced={sum(row['status'] == 'synced' for row in rows)} missing={sum(row['status'] != 'synced' for row in rows)}")
    print(f"report={report}")


if __name__ == "__main__":
    main()
