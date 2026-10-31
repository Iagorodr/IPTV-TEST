#!/usr/bin/env python3
"""
Desenha o ícone do app (launcher), a mesma marca do banner de TV e do topo do app:
duas setas de "play" sobrepostas, num selo com o degradê vinho -> vermelho.

Uso:  python3 tools/make_icon.py            -> escreve app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
Precisa só do Pillow:  pip install pillow
"""
import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png")

SIZE = 192
S = 4  # desenha em maior resolução e reduz no fim (bordas lisas)

CARMIM = (84, 26, 46)
RED = (181, 26, 43)
SALMON = (255, 165, 134)
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


def tri(cx, cy, w, h):
    return [(cx, cy - h / 2), (cx + w, cy), (cx, cy + h / 2)]


def main():
    w = SIZE * S
    img = diagonal_gradient(w, w, CARMIM, RED).convert("RGBA")
    mask = Image.new("L", (w, w), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, w - 1], radius=int(w * 0.24), fill=255)
    bg = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    bg.paste(img, (0, 0), mask)

    d = ImageDraw.Draw(bg)
    back = tri(w * 0.52, w * 0.50, w * 0.34, w * 0.60)
    d.polygon(back, fill=SALMON + (115,))
    front = tri(w * 0.22, w * 0.50, w * 0.40, w * 0.64)
    d.polygon(front, fill=TEXT + (255,))

    out = bg.convert("RGB").resize((SIZE, SIZE), Image.LANCZOS)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    out.save(OUT, optimize=True)
    print("ícone gerado ->", OUT)


if __name__ == "__main__":
    main()
