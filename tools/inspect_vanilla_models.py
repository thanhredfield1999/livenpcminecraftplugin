import json
import zipfile
from pathlib import Path

JAR = Path(r"C:\Users\thanh\AppData\Roaming\.minecraft\versions\ForgeOptiFine 1.21.11\ForgeOptiFine 1.21.11.jar")
NAMES = ["oak_fence", "cobblestone", "stone", "oak_door", "oak_sign", "oak_boat"]

with zipfile.ZipFile(JAR) as z:
    names = set(z.namelist())
    for name in NAMES:
        print(f"--- {name} ---")
        for path in [f"assets/minecraft/items/{name}.json", f"assets/minecraft/models/item/{name}.json", f"assets/minecraft/models/block/{name}_inventory.json", f"assets/minecraft/models/block/{name}.json"]:
            if path in names:
                print(path)
                print(z.read(path).decode("utf-8"))
        print()
