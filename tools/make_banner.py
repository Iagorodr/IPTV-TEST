#!/usr/bin/env python3
"""
Desenha o cartão (banner) do app na tela inicial da Android TV.

O nome que aparece no cartão é lido de app/src/main/res/values/strings.xml (app_name): é o mesmo nome do app na
tela inicial e dentro do app. Mudou o nome lá? O cartão acompanha na próxima compilação (o GitHub Actions roda este
script antes de compilar).

Uso:  python3 tools/make_banner.py            -> escreve app/src/main/res/drawable-xhdpi/tv_banner.png
Precisa só do Pillow:  pip install pillow
"""
import math
import os
import re
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STRINGS = os.path.join(ROOT, "app/src/main/res/values/strings.xml")
OUT = os.path.join(ROOT, "app/src/main/res/drawable-xhdpi/tv_banner.png")
BOLD = os.path.join(ROOT, "tools/fonts/Poppins-Bold.ttf")
MEDIUM = os.path.join(ROOT, "tools/fonts/Poppins-Medium.ttf")

W, H = 640, 360  # banner de TV: 320x180 dp em xhdpi
S = 2  # desenha em dobro e reduz no fim (bordas lisas)

NAVY = (22, 30, 47)
NAVY2 = (36, 47, 73)
CARMIM = (84, 26, 46)
SALMON = (255, 165, 134)
RED = (181, 26, 43)
TEXT = (255, 243, 236)
MUTED = (154, 163, 184)
MTN_BACK = (31, 42, 68)


def app_name() -> str:
    txt = open(STRINGS, encoding="utf-8").read()
    m = re.search(r'<string name="app_name">(.*?)</string>', txt, re.S)
    if not m:
        sys.exit("app_name não encontrado em strings.xml")
    name = m.group(1).strip()
    for a, b in (("&amp;", "&"), ("&lt;", "<"), ("&gt;", ">"), ("\\'", "'"), ("&apos;", "'")):
        name = name.replace(a, b)
    return name


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def gradient_bg(w, h):
    """Degradê diagonal (115°): azul-noite -> azul médio -> carmim."""
    ang = math.radians(115)
    dx, dy = math.sin(ang), -math.cos(ang)
    corners = [(0, 0), (w, 0), (0, h), (w, h)]
    proj = [x * dx + y * dy for x, y in corners]
    lo, hi = min(proj), max(proj)
    px = []
    for y in range(h):
        for x in range(w):
            t = (x * dx + y * dy - lo) / (hi - lo)
            if t < 0.42:
                px.append(lerp(NAVY, NAVY2, t / 0.42))
            else:
                px.append(lerp(NAVY2, CARMIM, (t - 0.42) / 0.58))
    img = Image.new("RGB", (w, h))
    img.putdata(px)
    return img


def vertical_gradient(w, h, stops):
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        for i in range(len(stops) - 1):
            t0, c0 = stops[i]
            t1, c1 = stops[i + 1]
            if t0 <= t <= t1:
                d.line([(0, y), (w, y)], fill=lerp(c0, c1, (t - t0) / (t1 - t0)))
                break
    return img


def spaced_width(font, text, spacing):
    return sum(font.getlength(ch) + spacing for ch in text) - spacing


def draw_spaced(draw, xy, text, font, fill, spacing):
    x, y = xy
    for ch in text:
        draw.text((x, y), ch, font=font, fill=fill)
        x += font.getlength(ch) + spacing


def main():
    name = app_name()
    img = gradient_bg(W * S, H * S).convert("RGBA")

    # brilho e sol, à direita
    glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    cx, cy, r = 503 * S, 135 * S, 95 * S
    gd.ellipse([cx - r * 1.9, cy - r * 1.9, cx + r * 1.9, cy + r * 1.9], fill=SALMON + (58,))
    glow = glow.filter(ImageFilter.GaussianBlur(46 * S))
    img = Image.alpha_composite(img, glow)

    sun = vertical_gradient(2 * r, 2 * r, [(0.0, SALMON), (0.55, (226, 85, 106)), (1.0, RED)]).convert("RGBA")
    mask = Image.new("L", (2 * r, 2 * r), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, 2 * r - 1, 2 * r - 1], fill=255)
    img.paste(sun, (cx - r, cy - r), mask)

    # montanhas (duas camadas)
    d = ImageDraw.Draw(img)
    back = [(0, 250), (70, 205), (130, 235), (205, 170), (290, 232), (370, 188), (450, 238), (530, 196), (640, 250), (640, 360), (0, 360)]
    front = [(0, 290), (90, 250), (170, 282), (260, 236), (350, 286), (440, 246), (540, 284), (640, 258), (640, 360), (0, 360)]
    d.polygon([(x * S, y * S) for x, y in back], fill=MTN_BACK + (242,))
    d.polygon([(x * S, y * S) for x, y in front], fill=NAVY + (255,))

    # selo "AO VIVO" (o mesmo do app), no canto de cima
    f_pill = ImageFont.truetype(BOLD, 15 * S)
    label = "AO VIVO"
    tw = spaced_width(f_pill, label, 1.5 * S)
    px0, py0 = 40 * S, 38 * S
    pw, ph = int(tw + 40 * S), 28 * S
    d.rounded_rectangle([px0, py0, px0 + pw, py0 + ph], radius=ph // 2, fill=RED + (255,))
    d.ellipse([px0 + 12 * S, py0 + ph // 2 - 4 * S, px0 + 20 * S, py0 + ph // 2 + 4 * S], fill=(255, 255, 255, 255))
    asc, desc = f_pill.getmetrics()
    draw_spaced(d, (px0 + 27 * S, py0 + (ph - (asc + desc)) // 2 - 1 * S), label, f_pill, (255, 255, 255, 255), 1.5 * S)

    # nome em duas cores: primeira palavra clara, o resto salmão. Encolhe se o nome for comprido.
    words = name.split()
    first = words[0] if words else name
    rest = " ".join(words[1:])
    max_w = 400 * S
    size = 68 * S
    while size > 24 * S:
        f_name = ImageFont.truetype(BOLD, size)
        total = f_name.getlength(name)
        if total <= max_w:
            break
        size -= 2 * S
    f_name = ImageFont.truetype(BOLD, size)
    asc, desc = f_name.getmetrics()
    base_y = 292 * S - asc  # a linha de base do nome fica a ~292 px
    x = 40 * S
    d.text((x, base_y), first, font=f_name, fill=TEXT + (255,))
    if rest:
        x += f_name.getlength(first + " ")
        d.text((x, base_y), rest, font=f_name, fill=SALMON + (255,))

    # barrinha salmão + subtítulo
    d.rounded_rectangle([40 * S, 306 * S, 40 * S + 44 * S, 306 * S + 4 * S], radius=2 * S, fill=SALMON + (255,))
    f_sub = ImageFont.truetype(MEDIUM, 13 * S)
    draw_spaced(d, (40 * S, 318 * S), "FILMES · SÉRIES · CANAIS", f_sub, MUTED + (255,), 3.2 * S)

    out = img.convert("RGB").resize((W, H), Image.LANCZOS)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    out.save(OUT, optimize=True)
    print("banner gerado para:", name, "->", OUT)


if __name__ == "__main__":
    main()
