import csv
import json
import zipfile
from pathlib import Path
from PIL import Image

JAR = Path(r"C:\Users\thanh\AppData\Roaming\.minecraft\versions\ForgeOptiFine 1.21.11\ForgeOptiFine 1.21.11.jar")
ROOT = Path(r"F:\minecraftserver\Vanilla Item Icons 1.21.11")
OUT = ROOT / "icons" / "generated_item_model"
PLAN = ROOT / "vanilla-renderer-plan.csv"


def texture_path(value):
    value = value.removeprefix("minecraft:")
    if value.startswith("item/"):
        return f"assets/minecraft/textures/{value}.png"
    return f"assets/minecraft/textures/{value}.png"


def main():
    plan = list(csv.DictReader(PLAN.open(encoding="utf-8-sig")))
    targets = [row["item_id"] for row in plan if row["renderer"] == "generated_item"]
    rendered = []
    skipped = []
    with zipfile.ZipFile(JAR) as jar:
        names = set(jar.namelist())
        for item_id in targets:
            model_path = f"assets/minecraft/models/item/{item_id}.json"
            if model_path not in names:
                skipped.append((item_id, "model_missing"))
                continue
            model = json.loads(jar.read(model_path))
            textures = model.get("textures", {})
            layer = textures.get("layer0")
            if not layer:
                skipped.append((item_id, "layer0_missing"))
                continue
            tex_path = texture_path(layer)
            if tex_path not in names:
                skipped.append((item_id, tex_path))
                continue
            target = OUT / f"{item_id}.png"
            target.parent.mkdir(parents=True, exist_ok=True)
            image = Image.open(jar.open(tex_path)).convert("RGBA")
            image.save(target)
            rendered.append({"item_id": item_id, "file": f"icons/generated_item_model/{item_id}.png", "source_model": model_path, "source_texture": tex_path, "kind": "generated_layer0_copy"})
    with (ROOT / "generated-item-models.csv").open("w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.DictWriter(handle, fieldnames=["item_id", "file", "source_model", "source_texture", "kind"])
        writer.writeheader()
        writer.writerows(rendered)
    print(f"targets={len(targets)} rendered={len(rendered)} skipped={len(skipped)}")
    if skipped:
        print("skipped_sample=" + repr(skipped[:20]))


if __name__ == "__main__":
    main()
    
