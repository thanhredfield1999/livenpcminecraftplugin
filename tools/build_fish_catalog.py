import html
import os
import shutil
from pathlib import Path

SOURCE = Path(r"F:\minecraftserver\Vanilla Item Icons 1.21.11\icons")
DEST = Path(r"F:\minecraftserver\villagedefense2026\bluemap\web\livingnpc-fish-icons")

fish_ids = {
    "cod", "cooked_cod", "cod_bucket", "cod_spawn_egg",
    "salmon", "cooked_salmon", "salmon_bucket", "salmon_spawn_egg",
    "pufferfish", "pufferfish_bucket", "pufferfish_spawn_egg",
    "tropical_fish", "tropical_fish_bucket", "tropical_fish_spawn_egg",
}
files = sorted(path for path in SOURCE.rglob("*.png") if path.stem.lower() in fish_ids)
shutil.rmtree(DEST, ignore_errors=True)
(DEST / "icons").mkdir(parents=True)
rows = []
for source in files:
    target = DEST / "icons" / source.name
    shutil.copy2(source, target)
    rows.append((source.stem, f"icons/{source.name}", source.parent.name))

cards = "".join(
    f'<article><img src="{html.escape(file)}"><b>{html.escape(item)}</b><span>{html.escape(group)}</span></article>'
    for item, file, group in rows
)
page = f'''<!doctype html><html lang="vi"><meta charset="utf-8"><title>Fish Icons - LivingNPC BlueMap</title><style>body{{font:14px system-ui;background:#172033;color:#fff;margin:24px}}main{{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:12px}}article{{background:#24324d;border:1px solid #5eead4;border-radius:8px;padding:12px;display:grid;gap:6px}}img{{width:96px;height:96px;image-rendering:pixelated;background:#101827}}b{{overflow-wrap:anywhere}}span{{color:#9fb0c9}}</style><h1>Fish Icons</h1><p>Tổng: {len(rows)} icon cá vanilla 1.21.11</p><main>{cards}</main></html>'''
(DEST / "index.html").write_text(page, encoding="utf-8")
(DEST / "README.txt").write_text(
    f"Fish icon catalog\n\nTổng: {len(rows)}\nNguồn: Vanilla Item Icons 1.21.11\nChỉ dùng icon PNG local cho BlueMap.\n",
    encoding="utf-8",
)
print(f"count={len(rows)} destination={DEST}")
for row in rows:
    print(*row, sep=" | ")
if __name__ == "__main__":
    pass
