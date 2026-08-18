from pathlib import Path

root = Path(r"F:\minecraftserver\Vanilla Item Icons 1.21.11")
readme = root / "README.txt"
text = readme.read_text(encoding="utf-8") if readme.exists() else "Vanilla Item Icons 1.21.11\n"
addition = """

Generated model textures:
- generated-item-models.csv lists 325 generated layer0 PNG files.
- icons/generated_item_model/ contains copied vanilla layer0 textures for item models.
- These are direct vanilla texture layers, not full renders for 3D/block/special models.
- oak_fence, cobblestone, doors using block models remain in vanilla-renderer-plan.csv as model work.

Web research:
- MC Assets Cloud exposes 1.21.11 textures/models, but not a complete inventory renderer.
- Local client JAR remains source of truth for exact target version.
"""
if "Generated model textures:" not in text:
    readme.write_text(text.rstrip() + addition, encoding="utf-8")
print(f"updated={readme}")
