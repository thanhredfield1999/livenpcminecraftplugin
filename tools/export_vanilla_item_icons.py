import csv
import html
import json
import os
import re
import shutil
import zipfile
from pathlib import Path

SOURCE = Path(r"C:\Users\thanh\AppData\Roaming\.minecraft\versions\ForgeOptiFine 1.21.11\ForgeOptiFine 1.21.11.jar")
DEST = Path(r"F:\minecraftserver\Vanilla Item Icons 1.21.11")

CATEGORY_RULES = [
    ("fish", {"cod", "salmon", "pufferfish", "tropical_fish", "ink_sac", "glow_ink_sac"}),
    ("food", {"apple", "golden_apple", "enchanted_golden_apple", "bread", "cookie", "cake", "pumpkin_pie", "beef", "porkchop", "mutton", "chicken", "rabbit", "rotten_flesh", "carrot", "golden_carrot", "potato", "baked_potato", "poisonous_potato", "beetroot", "melon_slice", "sweet_berries", "glow_berries", "dried_kelp", "chorus_fruit", "spider_eye", "fermented_spider_eye", "honey_bottle", "mushroom_stew", "rabbit_stew", "suspicious_stew"}),
    ("farming", {"wheat", "wheat_seeds", "beetroot_seeds", "melon_seeds", "pumpkin_seeds", "torchflower_seeds", "pitcher_pod", "bone_meal", "shears", "flint_and_steel", "water_bucket", "lava_bucket", "milk_bucket", "powder_snow_bucket", "composter"}),
    ("building", {"oak_fence", "spruce_fence", "birch_fence", "jungle_fence", "acacia_fence", "dark_oak_fence", "mangrove_fence", "cherry_fence", "bamboo_fence", "crimson_fence", "warped_fence", "oak_door", "spruce_door", "birch_door", "jungle_door", "acacia_door", "dark_oak_door", "mangrove_door", "cherry_door", "bamboo_door", "crimson_door", "warped_door", "stone", "cobblestone", "dirt", "sand", "gravel", "glass", "bricks", "oak_planks"}),
    ("tools", {"wooden_pickaxe", "stone_pickaxe", "iron_pickaxe", "golden_pickaxe", "diamond_pickaxe", "netherite_pickaxe", "wooden_axe", "stone_axe", "iron_axe", "golden_axe", "diamond_axe", "netherite_axe", "wooden_shovel", "stone_shovel", "iron_shovel", "golden_shovel", "diamond_shovel", "netherite_shovel", "wooden_hoe", "stone_hoe", "iron_hoe", "golden_hoe", "diamond_hoe", "netherite_hoe", "fishing_rod", "bow", "crossbow", "trident", "shield", "compass", "recovery_compass", "clock"}),
    ("armor", {"leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots", "chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots", "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots", "golden_helmet", "golden_chestplate", "golden_leggings", "golden_boots", "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots", "netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots", "turtle_helmet", "elytra"}),
    ("materials", {"iron_ingot", "gold_ingot", "copper_ingot", "netherite_ingot", "diamond", "emerald", "coal", "charcoal", "redstone", "lapis_lazuli", "quartz", "amethyst_shard", "stick", "string", "leather", "feather", "flint", "clay_ball", "brick", "paper", "book", "glass_bottle", "bucket", "name_tag", "saddle", "rabbit_foot", "blaze_rod", "ghast_tear", "ender_pearl", "ender_eye", "slime_ball", "magma_cream"}),
]


def category(item_id: str) -> str:
    base = item_id.rsplit("/", 1)[-1]
    for name, ids in CATEGORY_RULES:
        if base in ids or any(base.startswith(x + "_") for x in ids):
            return name
    if base.endswith("_spawn_egg"):
        return "spawn_eggs"
    if "sword" in base or "helmet" in base or "chestplate" in base or "leggings" in base or "boots" in base:
        return "combat"
    if "_bucket" in base or base.endswith("_boat") or base.endswith("_minecart"):
        return "transport_and_buckets"
    if base.endswith("_banner") or "banner_pattern" in base:
        return "banners"
    if base.endswith("_dye") or base in {"ink_sac", "glow_ink_sac"}:
        return "dyes"
    if "potion" in base or base in {"experience_bottle", "ominous_bottle"}:
        return "potions"
    return "other"


def display_name(item_id: str, lang: dict) -> str:
    key = "item.minecraft." + item_id
    value = lang.get(key)
    if isinstance(value, str):
        return value
    return item_id.replace("_", " ").title()


def main() -> None:
    if not SOURCE.is_file():
        raise SystemExit(f"Missing source JAR: {SOURCE}")
    if DEST.exists():
        shutil.rmtree(DEST)
    (DEST / "icons").mkdir(parents=True)
    manifest = []
    with zipfile.ZipFile(SOURCE) as jar:
        try:
            lang = json.loads(jar.read("assets/minecraft/lang/en_us.json"))
        except KeyError:
            lang = {}
        paths = sorted(n for n in jar.namelist() if n.startswith("assets/minecraft/textures/item/") and n.endswith(".png"))
        for source_path in paths:
            filename = Path(source_path).name
            item_id = filename[:-4]
            group = category(item_id)
            target_dir = DEST / "icons" / group
            target_dir.mkdir(parents=True, exist_ok=True)
            target = target_dir / filename
            target.write_bytes(jar.read(source_path))
            manifest.append({"item_id": item_id, "name_en": display_name(item_id, lang), "category": group, "file": f"icons/{group}/{filename}", "source": "Minecraft vanilla 1.21.11 client assets"})

    with (DEST / "manifest.csv").open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=["item_id", "name_en", "category", "file", "source"])
        writer.writeheader()
        writer.writerows(manifest)

    cards = []
    for row in manifest:
        cards.append(f'<article data-category="{html.escape(row["category"])}" data-name="{html.escape(row["name_en"])}"><img loading="lazy" src="{html.escape(row["file"])}"><b>{html.escape(row["item_id"])}</b><span>{html.escape(row["name_en"])}</span><small>{html.escape(row["category"])}</small></article>')
    categories = sorted({row["category"] for row in manifest})
    options = ''.join(f'<option value="{html.escape(x)}">{html.escape(x)}</option>' for x in categories)
    page = f'''<!doctype html><html lang="vi"><meta charset="utf-8"><title>Vanilla Item Icons 1.21.11</title><style>body{{font:14px system-ui;background:#202124;color:#eee;margin:24px}}header{{position:sticky;top:0;background:#202124;padding:12px 0;z-index:2}}input,select{{padding:8px;margin-right:8px;background:#303134;color:#fff;border:1px solid #666}}main{{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:10px}}article{{background:#2b2c2f;border:1px solid #4a4b50;border-radius:8px;padding:10px;display:grid;gap:5px;min-height:150px}}img{{width:64px;height:64px;image-rendering:pixelated;background:#111}}b{{font-size:12px;overflow-wrap:anywhere}}span{{color:#ddd}}small{{color:#8fe3c0}}</style><header><h1>Vanilla Item Icons 1.21.11</h1><p>Chỉ icon PNG inventory vanilla. Không phải mod/resourcepack.</p><input id="q" placeholder="Tìm item..." oninput="filter()"><select id="c" onchange="filter()"><option value="">Tất cả nhóm</option>{options}</select><strong id="count">{len(manifest)} icons</strong></header><main id="grid">{''.join(cards)}</main><script>function filter(){{let q=document.getElementById('q').value.toLowerCase(),c=document.getElementById('c').value,n=0;document.querySelectorAll('article').forEach(x=>{{let ok=(!q||x.innerText.toLowerCase().includes(q))&&(!c||x.dataset.category===c);x.hidden=!ok;if(ok)n++}});document.getElementById('count').textContent=n+' icons'}};</script></html>'''
    (DEST / "index.html").write_text(page, encoding="utf-8")
    readme = f"""Vanilla Item Icons 1.21.11

Nội dung: {len(manifest)} PNG item textures trực tiếp từ Minecraft client 1.21.11.

Đây chỉ là thư mục icon, không phải mod và không phải resourcepack.
- Mở index.html để xem icon, tên tiếng Anh, nhóm.
- manifest.csv chứa item_id, tên, nhóm, đường dẫn.
- icons/<category>/ chứa PNG.

Nguồn: Minecraft vanilla 1.21.11 client assets.
Không trộn với F:/minecraftserver/Icons v.1.13.4.

Lưu ý: một số block-item như fence dùng block model trong inventory, không có PNG item riêng trong client assets; bộ này chỉ xuất texture PNG item trực tiếp.
"""
    (DEST / "README.txt").write_text(readme, encoding="utf-8")
    print(f"created={DEST}")
    print(f"icons={len(manifest)}")
    print("categories=" + ",".join(f"{x}:{sum(1 for r in manifest if r['category']==x)}" for x in categories))


if __name__ == "__main__":
    main()
