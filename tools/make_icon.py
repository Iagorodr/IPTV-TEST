#!/usr/bin/env python3
"""
Desenha o ícone do app (launcher), a mesma marca do banner de TV e do topo do app:
o "D que aponta" (D com a curva em ponta e o miolo em forma de play), num selo com o degradê vinho -> vermelho.

Uso:  python3 tools/make_icon.py            -> escreve app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
Precisa só do Pillow:  pip install pillow
"""
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from brandmark import d_mask  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png")

SIZE = 192
S = 4  # desenha em maior resolução e reduz no fim (bordas lisas)

CARMIM = (84, 26, 46)
RED = (181, 26, 43)
TEXT = (255, 243, 236)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def diagonal_gradient(w, h, c0, c1, angle_deg=135):
    ang = math.radians(angle_deg)
    dx, dy = math.sin(ang), -math.cos(ang)
    corners = [(0, 0), (w, 0), (0, h), (w, h)]
    proj = [x * dx + y * dy for x, y in corners]
    lo, hi = min(proj), max(proj)
    px = []
    for y in range(h):
        for x in range(w):
            t = (x * dx + y * dy - lo) / (hi - lo)
            px.append(lerp(c0, c1, t))
    img = Image.new("RGB", (w, h))
    img.putdata(px)
    return img


def main():
    w = SIZE * S
    grad = diagonal_gradient(w, w, CARMIM, RED).convert("RGBA")
    mask = Image.new("L", (w, w), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, w - 1], radius=int(w * 0.24), fill=255)
    bg = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    bg.paste(grad, (0, 0), mask)

    # o D ocupa 60% da altura do selo, centralizado
    dm = d_mask(int(w * 0.60))
    bg.paste(Image.new("RGBA", dm.size, TEXT + (255,)), ((w - dm.width) // 2, (w - dm.height) // 2), dm)

    out = bg.resize((SIZE, SIZE), Image.LANCZOS)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    out.save(OUT, optimize=True)
    print("ícone gerado ->", OUT)


if __name__ == "__main__":
    main()
