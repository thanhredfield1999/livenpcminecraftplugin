import os
import shutil

src = r"F:/minecraftserver/Vanilla Item Icons 1.21.11/icons"
dst = r"F:/minecraftserver/villagedefense2026/bluemap/web/livingnpc-icons"
categories = {"food", "fish", "farming", "materials", "building", "tools", "armor", "transport_and_buckets"}
shutil.rmtree(dst, ignore_errors=True)
count = 0
for category in sorted(categories):
    source_dir = os.path.join(src, category)
    target_dir = os.path.join(dst, category)
    os.makedirs(target_dir, exist_ok=True)
    if not os.path.isdir(source_dir):
        continue
    for filename in os.listdir(source_dir):
        if filename.endswith(".png"):
            shutil.copy2(os.path.join(source_dir, filename), os.path.join(target_dir, filename))
            count += 1
print(f"copied={count} categories={len(categories)} destination={dst}")
