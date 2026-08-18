import csv
from collections import Counter
from pathlib import Path

root = Path(r"F:\minecraftserver\Vanilla Item Icons 1.21.11")
source = root / "vanilla-item-model-catalog.csv"
out = root / "vanilla-renderer-plan.csv"
rows = list(csv.DictReader(source.open(encoding="utf-8-sig")))


def plan(row):
    if row["kind"] == "direct_texture":
        return "direct_texture", "ready"
    ref = row["model_reference"]
    if "fence_inventory" in ref:
        return "fence_inventory", "needs_3d_renderer"
    if "cube" in ref or "/block/" in ref:
        return "block_model", "needs_3d_renderer"
    if "generated" in ref:
        return "generated_item", "needs_2d_compositor"
    if "handheld" in ref or "bow" in ref or "crossbow" in ref or "spear" in ref:
        return "handheld_or_animated", "needs_context_renderer"
    if "potion" in row["item_id"] or "tipped_arrow" in row["item_id"]:
        return "tinted_item", "needs_color_renderer"
    if "spawn_egg" in row["item_id"] or "head" in ref:
        return "entity_or_head", "needs_entity_renderer"
    return "special_model", "needs_special_renderer"

planned = []
for row in rows:
    renderer, status = plan(row)
    planned.append({
        "item_id": row["item_id"],
        "renderer": renderer,
        "status": status,
        "model_reference": row["model_reference"],
        "direct_file": f"icons/{row['item_id']}.png" if row["kind"] == "direct_texture" else "",
    })

with out.open("w", newline="", encoding="utf-8-sig") as handle:
    writer = csv.DictWriter(handle, fieldnames=list(planned[0]))
    writer.writeheader()
    writer.writerows(planned)

counts = Counter((row["renderer"], row["status"]) for row in planned)
print(f"items={len(planned)}")
for (renderer, status), count in sorted(counts.items()):
    print(f"{renderer}: {count} ({status})")
print(f"output={out}")

if __name__ == "__main__":
    pass
