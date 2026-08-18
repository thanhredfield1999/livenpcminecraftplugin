import csv
import json
import zipfile
from pathlib import Path

JAR = Path(r"C:\Users\thanh\AppData\Roaming\.minecraft\versions\ForgeOptiFine 1.21.11\ForgeOptiFine 1.21.11.jar")
OUT = Path(r"F:\minecraftserver\Vanilla Item Icons 1.21.11")


def load_json(z, path):
    try:
        return json.loads(z.read(path))
    except (KeyError, json.JSONDecodeError):
        return None


def main():
    with zipfile.ZipFile(JAR) as z:
        names = set(z.namelist())
        direct = {
            Path(n).stem for n in names
            if n.startswith("assets/minecraft/textures/item/") and n.endswith(".png")
        }
        models = {
            Path(n).stem: n for n in names
            if n.startswith("assets/minecraft/models/item/") and n.endswith(".json")
        }
        item_defs = {
            Path(n).stem: n for n in names
            if n.startswith("assets/minecraft/items/") and n.endswith(".json")
        }
        rows = []
        for item_id in sorted(set(direct) | set(models) | set(item_defs)):
            item_path = item_defs.get(item_id, "")
            model_path = models.get(item_id, "")
            item_data = load_json(z, item_path) if item_path else None
            model_data = load_json(z, model_path) if model_path else None
            if item_id in direct:
                kind = "direct_texture"
                source = f"assets/minecraft/textures/item/{item_id}.png"
            elif model_path:
                kind = "item_model"
                source = model_path
            elif item_path:
                kind = "item_definition"
                source = item_path
            else:
                kind = "unsupported"
                source = ""
            model_ref = ""
            if isinstance(item_data, dict):
                model_ref = str(item_data.get("model", {}).get("model", ""))
            if not model_ref and isinstance(model_data, dict):
                model_ref = str(model_data.get("parent", ""))
            rows.append({
                "item_id": item_id,
                "kind": kind,
                "item_definition": item_path,
                "model_file": model_path,
                "model_reference": model_ref,
                "source": source,
                "render_status": "direct_png_available" if kind == "direct_texture" else "needs_model_renderer",
            })
    with (OUT / "vanilla-item-model-catalog.csv").open("w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    counts = {}
    for row in rows:
        counts[row["kind"]] = counts.get(row["kind"], 0) + 1
    print(f"items={len(rows)}")
    print("counts=" + ", ".join(f"{key}:{value}" for key, value in sorted(counts.items())))
    print(f"catalog={OUT / 'vanilla-item-model-catalog.csv'}")


if __name__ == "__main__":
    main()
