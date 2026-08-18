import csv
from pathlib import Path

root = Path(r"F:\minecraftserver\Vanilla Item Icons 1.21.11")
folder = root / "icons" / "building" / "rendered_item_model"
rows = []
for path in sorted(folder.glob("*.png")):
    rows.append({
        "item_id": path.stem,
        "file": f"icons/building/rendered_item_model/{path.name}",
        "kind": "derived_inventory_model_preview",
        "source": "Minecraft vanilla 1.21.11 block/item model + vanilla block texture",
        "note": "Preview render; not direct Mojang item texture",
    })
with (root / "rendered-models.csv").open("w", newline="", encoding="utf-8-sig") as handle:
    writer = csv.DictWriter(handle, fieldnames=["item_id", "file", "kind", "source", "note"])
    writer.writeheader()
    writer.writerows(rows)
(root / "RENDERED_MODELS_README.txt").write_text(
    "Rendered item-model previews\n\n"
    "Các PNG trong icons/building/rendered_item_model là ảnh preview tự render từ model/texture vanilla 1.21.11.\n"
    "Không phải texture item gốc. Không tạo mod/resourcepack.\n"
    "Cần review hình học trước khi dùng làm icon BlueMap chính thức.\n",
    encoding="utf-8",
)
print(f"rendered_models={len(rows)}")
for row in rows:
    print(row["item_id"], row["file"])

if __name__ == "__main__":
    pass
