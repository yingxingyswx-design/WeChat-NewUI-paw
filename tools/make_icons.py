"""Generates Android launcher icons (adaptive + legacy) from the module art."""
import os
from PIL import Image, ImageDraw

SRC = r"C:\Users\paw_C\.dsh\attachments\v1\objects\56\56a68d8124780486f0ed28deaf22e2386ec2bd209c94c3fc431ce435d4bdf9ee"
RES = r"C:\codes\模块\wechat_chat_ui\app\src\main\res"

art = Image.open(SRC).convert("RGBA")
w, h = art.size
side = min(w, h)
art = art.crop(((w - side) // 2, (h - side) // 2, (w + side) // 2, (h + side) // 2))

# Background colour from a corner (the artwork's green paper).
bg = art.getpixel((4, 4))
bg = (bg[0], bg[1], bg[2], 255)

# Adaptive icon: 108dp canvas, artwork inside the 66dp safe zone.
safe = 66.0 / 108.0
for dpi, size in (("mdpi", 108), ("hdpi", 162), ("xhdpi", 216),
                  ("xxhdpi", 324), ("xxxhdpi", 432)):
    out_dir = os.path.join(RES, "mipmap-" + dpi)
    os.makedirs(out_dir, exist_ok=True)
    # Foreground (transparent, art centred in the safe zone).
    fg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    art_side = max(1, int(round(size * safe * 0.92)))
    scaled = art.resize((art_side, art_side), Image.LANCZOS)
    off = (size - art_side) // 2
    fg.alpha_composite(scaled, (off, off))
    fg.save(os.path.join(out_dir, "ic_launcher_foreground.png"))

    # Legacy icon: background + art, rounded square.
    legacy = Image.new("RGBA", (size, size), bg)
    lart_side = max(1, int(round(size * 0.82)))
    lscaled = art.resize((lart_side, lart_side), Image.LANCZOS)
    loff = (size - lart_side) // 2
    legacy.alpha_composite(lscaled, (loff, loff))
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, size - 1, size - 1), radius=int(size * 0.22), fill=255)
    legacy.putalpha(mask)
    legacy.save(os.path.join(out_dir, "ic_launcher.png"))
    legacy.save(os.path.join(out_dir, "ic_launcher_round.png"))

print("bg", "#%02X%02X%02X" % bg[:3])
print("done")
