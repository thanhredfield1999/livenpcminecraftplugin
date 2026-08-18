import csv
import html
import os
import shutil
from pathlib import Path

SOURCE = Path(r"F:\minecraftserver\Icons v.1.13.4")
DEST = Path(r"F:\minecraftserver\Icons v.1.13.4 - Extracted PNG Catalog")


def group_for(relative: str) -> str:
    parts = Path(relative).parts
    if len(parts) >= 4 and parts[0] == "assets":
        namespace = parts[1]
        area = parts[2]
        subarea = parts[3]
        return f"{namespace}/{area}/{subarea}"
    return "/".join(parts[:-1]) or "root"


def main() -> None:
    if not SOURCE.is_dir():
        raise SystemExit(f"Missing source: {SOURCE}")
    if DEST.exists():
        shutil.rmtree(DEST)
    DEST.mkdir(parents=True)

    rows = []
    for path in sorted(SOURCE.rglob("*.png")):
        relative = path.relative_to(SOURCE).as_posix()
        target = DEST / "png" / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)
        rows.append({
            "file": relative,
            "group": group_for(relative),
            "name": path.stem,
            "bytes": path.stat().st_size,
        })

    with (DEST / "manifest.csv").open("w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.DictWriter(handle, fieldnames=["file", "group", "name", "bytes"])
        writer.writeheader()
        writer.writerows(rows)

    groups = sorted({row["group"] for row in rows})
    options = ''.join(f'<option value="{html.escape(group)}">{html.escape(group)}</option>' for group in groups)
    cards = []
    for row in rows:
        cards.append(
            f'<article data-group="{html.escape(row["group"])}" data-name="{html.escape(row["name"])}">'
            f'<img loading="lazy" src="png/{html.escape(row["file"])}">'
            f'<b>{html.escape(row["name"])}</b>'
            f'<span>{html.escape(row["group"])}</span>'
            f'<small>{html.escape(row["file"])}</small></article>'
        )
    page = f'''<!doctype html><html lang="vi"><meta charset="utf-8"><title>Icons v.1.13.4 - Extracted PNG Catalog</title>
<style>body{{font:14px system-ui;background:#202124;color:#eee;margin:24px}}header{{position:sticky;top:0;background:#202124;padding:12px 0;z-index:2}}input,select{{padding:8px;margin-right:8px;background:#303134;color:#fff;border:1px solid #666}}main{{display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:10px}}article{{background:#2b2c2f;border:1px solid #4a4b50;border-radius:8px;padding:10px;display:grid;gap:5px;min-height:170px}}img{{width:96px;height:96px;image-rendering:pixelated;background:#111;object-fit:contain}}b{{font-size:12px;overflow-wrap:anywhere}}span{{color:#8fe3c0;overflow-wrap:anywhere}}small{{color:#aaa;overflow-wrap:anywhere}}</style>
<header><h1>Icons v.1.13.4 — PNG Catalog</h1><p>Catalog local để xem và phân loại. Không phải resourcepack.</p><input id="q" placeholder="Tìm tên/path..." oninput="filter()"><select id="g" onchange="filter()"><option value="">Tất cả nhóm</option>{options}</select><strong id="count">{len(rows)} PNG</strong></header><main id="grid">{''.join(cards)}</main>
<script>function filter(){{let q=document.getElementById('q').value.toLowerCase(),g=document.getElementById('g').value,n=0;document.querySelectorAll('article').forEach(x=>{{let ok=(!q||x.innerText.toLowerCase().includes(q))&&(!g||x.dataset.group===g);x.hidden=!ok;if(ok)n++}});document.getElementById('count').textContent=n+' PNG'}}</script></html>'''
    (DEST / "index.html").write_text(page, encoding="utf-8")

    readme = f'''Icons v.1.13.4 — Extracted PNG Catalog

Đây là bản trích xuất cục bộ để xem/phân loại PNG từ:
{SOURCE}

Tổng PNG: {len(rows)}
Nhóm: {len(groups)}

Mở index.html để xem toàn bộ icon.
manifest.csv chứa file, group, name, bytes.
png/ giữ nguyên namespace và đường dẫn tương đối của pack gốc.

LICENSE / TERMS:
- Chỉ dùng nội bộ để kiểm tra, phát triển và tham chiếu.
- Pack gốc cho phép dùng trong modpack và content creation có dẫn link/credit.
- Không redistribute asset đã chỉnh hoặc chưa chỉnh.
- Không re-upload pack.
- Không claim ownership.
- Credits xem readme.md của pack gốc.

Không đưa thư mục này vào BlueMap/server web công khai nếu chưa có quyền phù hợp.
'''
    (DEST / "README.txt").write_text(readme, encoding="utf-8")
    print(f"source={SOURCE}")
    print(f"dest={DEST}")
    print(f"png={len(rows)}")
    print(f"groups={len(groups)}")
    print("group_counts=")
    for group in groups:
        print(f"  {group}: {sum(row['group'] == group for row in rows)}")


if __name__ == "__main__":
    main()
