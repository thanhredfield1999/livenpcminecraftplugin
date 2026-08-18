import math
import zipfile
from pathlib import Path
from PIL import Image, ImageDraw

JAR = Path(r"C:\Users\thanh\AppData\Roaming\.minecraft\versions\ForgeOptiFine 1.21.11\ForgeOptiFine 1.21.11.jar")
OUT = Path(r"F:\minecraftserver\Vanilla Item Icons 1.21.11\icons\building\rendered_item_model")
ITEMS = {"cobblestone": "cobblestone", "stone": "stone", "oak_fence": "oak_planks"}
SIZE = 128


def texture(jar, path):
    return Image.open(jar.open(path)).convert("RGBA").resize((32, 32), Image.Resampling.NEAREST)


def cube_icon(tex):
    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    top = [(64, 16), (104, 36), (64, 56), (24, 36)]
    left = [(24, 36), (64, 56), (64, 104), (24, 84)]
    right = [(64, 56), (104, 36), (104, 84), (64, 104)]
    top_img = tex.transform((80, 40), Image.Transform.QUAD, (0, 0, 32, 0, 32, 32, 0, 32), Image.Resampling.NEAREST)
    side = tex.transform((40, 80), Image.Transform.QUAD, (0, 0, 32, 0, 32, 32, 0, 32), Image.Resampling.NEAREST)
    canvas.alpha_composite(top_img, (24, 16))
    canvas.alpha_composite(side.resize((40, 80)), (24, 36))
    canvas.alpha_composite(side.resize((40, 80)), (64, 36))
    shade = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shade)
    sd.polygon(left, fill=(0, 0, 0, 28))
    sd.polygon(right, fill=(255, 255, 255, 16))
    canvas.alpha_composite(shade)
    return canvas


def fence_icon(planks):
    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    # Inventory fence model: two posts and horizontal rails, stylized isometric.
    def rect(x, y, w, h):
        patch = planks.resize((w, h), Image.Resampling.NEAREST)
        canvas.alpha_composite(patch, (x, y))
    rect(38, 28, 14, 68)
    rect(76, 28, 14, 68)
    rect(28, 45, 76, 12)
    rect(28, 70, 76, 12)
    draw = ImageDraw.Draw(canvas)
    draw.line((38, 28, 52, 28), fill=(255, 255, 255, 90), width=2)
    draw.line((76, 28, 90, 28), fill=(255, 255, 255, 90), width=2)
    return canvas


def main():
    with zipfile.ZipFile(JAR) as jar:
        OUT.mkdir(parents=True, exist_ok=True)
        for item, texture_name in ITEMS.items():
            if item == "oak_fence":
                image = fence_icon(texture(jar, "assets/minecraft/textures/block/oak_planks.png"))
            else:
                image = cube_icon(texture(jar, f"assets/minecraft/textures/block/{texture_name}.png"))
            image.save(OUT / f"{item}.png")
            print(item, OUT / f"{item}.png")


if __name__ == "__main__":
    main()
