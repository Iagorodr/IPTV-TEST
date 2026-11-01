"""
O símbolo da marca IPTVROD: o "D que aponta" (a curva do D fecha numa ponta, como um play, e o miolo vazado tem
forma de play). Usado por make_icon.py e make_banner.py; o mesmo desenho está no app (AppLogoMark, em Screens.kt).

O D é descrito numa grade de 100x100; a caixa dele vai de x 18 a 91 e de y 10 a 90.
"""
from PIL import Image, ImageDraw

BOX = (18.0, 10.0, 91.0, 90.0)  # x0, y0, x1, y1 do D na grade de 100


def _cubic(p0, p1, p2, p3, n=48):
    out = []
    for i in range(1, n + 1):
        t = i / n
        a = (1 - t) ** 3
        b = 3 * (1 - t) ** 2 * t
        c = 3 * (1 - t) * t ** 2
        d = t ** 3
        out.append((a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1]))
    return out


def _quad(p0, p1, p2, n=16):
    out = []
    for i in range(1, n + 1):
        t = i / n
        a = (1 - t) ** 2
        b = 2 * (1 - t) * t
        c = t ** 2
        out.append((a * p0[0] + b * p1[0] + c * p2[0], a * p0[1] + b * p1[1] + c * p2[1]))
    return out


def outline():
    """Contorno de fora do D (pontos na grade de 100)."""
    pts = [(25, 10), (50, 10)]
    pts += _cubic((50, 10), (72, 10), (86, 30), (91, 50))
    pts += _cubic((91, 50), (86, 70), (72, 90), (50, 90))
    pts += [(25, 90)]
    pts += _quad((25, 90), (18, 90), (18, 83))
    pts += [(18, 17)]
    pts += _quad((18, 17), (18, 10), (25, 10))
    return pts


def counter():
    """Miolo vazado em forma de play (pontos na grade de 100)."""
    pts = [(38, 31)]
    pts += _quad((38, 31), (35, 29.5), (35, 33))
    pts += [(35, 67)]
    pts += _quad((35, 67), (35, 70.5), (38, 69))
    pts += [(64, 52)]
    pts += _quad((64, 52), (66.5, 50), (64, 48))
    return pts


def d_mask(height):
    """Máscara (modo L) só do D, recortada na caixa dele, com `height` px de altura."""
    x0, y0, x1, y1 = BOX
    k = height / (y1 - y0)
    w = int(round((x1 - x0) * k))
    m = Image.new("L", (w, int(height)), 0)
    dr = ImageDraw.Draw(m)
    tr = lambda pts: [((x - x0) * k, (y - y0) * k) for x, y in pts]
    dr.polygon(tr(outline()), fill=255)
    dr.polygon(tr(counter()), fill=0)
    return m
