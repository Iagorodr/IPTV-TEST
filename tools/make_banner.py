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

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from brandmark import d_mask  # noqa: E402

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


def diagonal_gradient(w, h, c0, c1, angle_deg=135):
    """Degradê diagonal simples entre duas cores (usado no selo da marca)."""
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

    # selo da marca (o "D que aponta"), grande, vazando pela borda direita
    # (estilo "hero graphic" dos cartões de app de streaming), com brilho atrás e um brilho de vidro por cima
    bs = 300 * S
    bx, by = 420 * S, (H * S - bs) // 2
    bcx, bcy = bx + bs // 2, by + bs // 2
    glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gr = bs * 0.80
    gd.ellipse([bcx - gr, bcy - gr, bcx + gr, bcy + gr], fill=SALMON + (60,))
    glow = glow.filter(ImageFilter.GaussianBlur(48 * S))
    img = Image.alpha_composite(img, glow)

    badge = diagonal_gradient(bs, bs, CARMIM, RED).convert("RGBA")
    mask = Image.new("L", (bs, bs), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, bs - 1, bs - 1], radius=int(bs * 0.22), fill=255)
    img.paste(badge, (bx, by), mask)

    # o D no selo: 50% da altura, centrado na parte que aparece (o selo vaza pela direita)
    dm = d_mask(int(bs * 0.50))
    visible = W * S - bx
    dx = bx + (visible - dm.width) // 2
    img.paste(Image.new("RGBA", dm.size, TEXT + (255,)), (dx, by + (bs - dm.height) // 2), dm)

    # brilho de vidro (sheen) no selo: uma luz suave no canto superior esquerdo, recortada pelo mesmo arredondado
    sheen = Image.new("RGBA", (bs, bs), (0, 0, 0, 0))
    ImageDraw.Draw(sheen).ellipse([-bs * 0.35, -bs * 0.55, bs * 0.70, bs * 0.55], fill=(255, 255, 255, 50))
    sheen.putalpha(ImageChops.multiply(sheen.split()[3], mask))
    img.paste(sheen, (bx, by), sheen)

    d = ImageDraw.Draw(img)

    # nome: a primeira palavra; se ela termina em "D", o D vira o símbolo da marca (em degradê salmão -> vermelho).
    # As outras palavras (ex.: PRO) viram um selinho discreto ao lado. Encolhe se o nome for comprido.
    words = name.split()
    first = words[0] if words else name
    tag = " ".join(words[1:]).upper()
    use_mark = first.upper().endswith("D") and len(first) > 1
    letters = first[:-1] if use_mark else first
    max_w = 330 * S
    size = 64 * S

    def measure(sz):
        f = ImageFont.truetype(BOLD, sz)
        cap_top, cap_bot = f.getbbox("H")[1], f.getbbox("H")[3]
        cap = cap_bot - cap_top
        mark_w = int(cap * 73 / 80) if use_mark else 0
        ft = ImageFont.truetype(BOLD, max(10 * S, int(sz * 0.22)))
        tag_w = int(ft.getlength(tag) + len(tag) * 1.5 * S + 14 * S) if tag else 0
        total = f.getlength(letters) + (mark_w + int(sz * 0.04) if use_mark else 0) + (tag_w + 10 * S if tag else 0)
        return f, ft, cap_top, cap, mark_w, tag_w, total

    while True:
        f_name, f_tag, cap_top, cap, mark_w, tag_w, total = measure(size)
        if total <= max_w or size <= 24 * S:
            break
        size -= 2 * S

    base_y = 286 * S  # linha de base do nome
    x = 40 * S
    d.text((x, base_y - cap_top - cap), letters, font=f_name, fill=TEXT + (255,))
    x += f_name.getlength(letters)
    if use_mark:
        x += int(size * 0.04)
        mm = d_mask(cap)
        fill = diagonal_gradient(mm.width, mm.height, SALMON, RED).convert("RGBA")
        img.paste(fill, (int(x), base_y - cap), mm)
        x += mm.width
    if tag:
        x += 10 * S
        th = int(f_tag.size * 1.55)
        ty = base_y - cap  # alinhado ao topo das letras, como um expoente
        d.rounded_rectangle([x, ty, x + tag_w, ty + th], radius=5 * S, outline=SALMON + (255,), width=int(1.5 * S))
        tb = f_tag.getbbox(tag)
        draw_spaced(d, (x + 7 * S, ty + (th - (tb[3] - tb[1])) // 2 - tb[1]), tag, f_tag, SALMON + (255,), 1.5 * S)

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
