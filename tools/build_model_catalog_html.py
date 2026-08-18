import csv
import html
from pathlib import Path

root = Path(r"F:\minecraftserver\Vanilla Item Icons 1.21.11")
rows = list(csv.DictReader((root / "vanilla-item-model-catalog.csv").open(encoding="utf-8-sig")))
rows.sort(key=lambda row: row["item_id"])
options = "".join(
    f'<option value="{html.escape(kind)}">{html.escape(kind)}</option>'
    for kind in sorted({row["kind"] for row in rows})
)
cards = "".join(
    f'<article data-kind="{html.escape(row["kind"])}" data-name="{html.escape(row["item_id"])}">'
    f'<b>{html.escape(row["item_id"])}</b>'
    f'<span>{html.escape(row["kind"])}</span>'
    f'<small>{html.escape(row["model_reference"] or row["source"])}</small></article>'
    for row in rows
)
page = f'''<!doctype html><html lang="vi"><meta charset="utf-8"><title>Vanilla 1.21.11 Item Model Catalog</title>
<style>body{{font:14px system-ui;background:#202124;color:#eee;margin:24px}}header{{position:sticky;top:0;background:#202124;padding:12px 0;z-index:2}}input,select{{padding:8px;margin-right:8px;background:#303134;color:#fff;border:1px solid #666}}main{{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:10px}}article{{background:#2b2c2f;border:1px solid #4a4b50;border-radius:8px;padding:10px;display:grid;gap:5px;min-height:80px}}b{{overflow-wrap:anywhere}}span{{color:#8fe3c0}}small{{color:#aaa;overflow-wrap:anywhere}}</style>
<header><h1>Vanilla 1.21.11 Item Model Catalog</h1><p>Catalog model/definition vanilla. Chưa phải PNG render.</p><input id="q" placeholder="Tìm item..." oninput="filter()"><select id="k" onchange="filter()"><option value="">Tất cả loại</option>{options}</select><strong id="count">{len(rows)} entries</strong></header><main>{cards}</main><script>function filter(){{let q=document.getElementById('q').value.toLowerCase(),k=document.getElementById('k').value,n=0;document.querySelectorAll('article').forEach(x=>{{let ok=(!q||x.innerText.toLowerCase().includes(q))&&(!k||x.dataset.kind===k);x.hidden=!ok;if(ok)n++}});document.getElementById('count').textContent=n+' entries'}}</script></html>'''
(root / "model-catalog.html").write_text(page, encoding="utf-8")
print(f"entries={len(rows)}")
print(f"output={root / 'model-catalog.html'}")
