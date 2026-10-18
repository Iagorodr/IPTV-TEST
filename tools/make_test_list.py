#!/usr/bin/env python3
"""Gera listas M3U de TESTE (nomes de exemplo, vídeos de amostra públicos) com conteúdo de vários países e idiomas.
Uso: python3 tools/make_test_list.py  ->  testlists/test-multipais.m3u e testlists/test-multipais-grande.m3u
Os links apontam para vídeos de amostra públicos: serve para testar a organização do app, não a programação real."""
import random

LIVE = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8"
VOD = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4"

# (ano, EN, PT, ES, FR, DE, IT)
FILMS = [
    (2010, "Inception", "A Origem", "Origen", "Inception", "Inception", "Inception"),
    (1994, "The Lion King", "O Rei Leão", "El rey león", "Le Roi lion", "Der König der Löwen", "Il re leone"),
    (1999, "The Matrix", "Matrix", "Matrix", "Matrix", "Matrix", "Matrix"),
    (2003, "Finding Nemo", "Procurando Nemo", "Buscando a Nemo", "Le Monde de Nemo", "Findet Nemo", "Alla ricerca di Nemo"),
    (1972, "The Godfather", "O Poderoso Chefão", "El padrino", "Le Parrain", "Der Pate", "Il padrino"),
    (1993, "Jurassic Park", "Jurassic Park - O Parque dos Dinossauros", "Parque Jurásico", "Jurassic Park", "Jurassic Park", "Jurassic Park"),
    (1994, "The Shawshank Redemption", "Um Sonho de Liberdade", "Cadena perpetua", "Les Évadés", "Die Verurteilten", "Le ali della libertà"),
    (1985, "Back to the Future", "De Volta para o Futuro", "Regreso al futuro", "Retour vers le futur", "Zurück in die Zukunft", "Ritorno al futuro"),
    (2000, "Gladiator", "Gladiador", "Gladiator", "Gladiator", "Gladiator", "Il gladiatore"),
    (2013, "Frozen", "Frozen - Uma Aventura Congelante", "Frozen", "La Reine des neiges", "Die Eiskönigin", "Frozen"),
    (2009, "Up", "Up - Altas Aventuras", "Up", "Là-haut", "Oben", "Up"),
    (2008, "The Dark Knight", "Batman: O Cavaleiro das Trevas", "El caballero oscuro", "The Dark Knight", "The Dark Knight", "Il cavaliere oscuro"),
    (2001, "Harry Potter and the Philosopher's Stone", "Harry Potter e a Pedra Filosofal", "Harry Potter y la piedra filosofal", "Harry Potter à l'école des sorciers", "Harry Potter und der Stein der Weisen", "Harry Potter e la pietra filosofale"),
    (2004, "The Incredibles", "Os Incríveis", "Los Increíbles", "Les Indestructibles", "Die Unglaublichen", "Gli Incredibili"),
    (2001, "Spirited Away", "A Viagem de Chihiro", "El viaje de Chihiro", "Le Voyage de Chihiro", "Chihiros Reise ins Zauberland", "La città incantata"),
    (2019, "Parasite", "Parasita", "Parásitos", "Parasite", "Parasite", "Parasite"),
    (2017, "Coco", "Viva - A Vida é uma Festa", "Coco", "Coco", "Coco", "Coco"),
    (2012, "The Avengers", "Os Vingadores", "Los Vengadores", "Avengers", "The Avengers", "The Avengers"),
    (2014, "Interstellar", "Interestelar", "Interstellar", "Interstellar", "Interstellar", "Interstellar"),
    (1997, "Titanic", "Titanic", "Titanic", "Titanic", "Titanic", "Titanic"),
    (1999, "Fight Club", "Clube da Luta", "El club de la lucha", "Fight Club", "Fight Club", "Fight Club"),
    (2016, "Zootopia", "Zootopia - Essa Cidade é o Bicho", "Zootrópolis", "Zootopie", "Zoomania", "Zootropolis"),
]
# (grupo filme, grupo série, sufixo de nome)
LANGS = {
    "EN": ("EN | MOVIES", "EN | SERIES", ""),
    "PT": ("PT | FILMES", "PT | SÉRIES", ""),
    "BR": ("BR | FILMES | DUBLADOS", "BR | SÉRIES", ""),
    "ES": ("ES | PELÍCULAS", "ES | SERIES", ""),
    "FR": ("FR | FILMS", "FR | SÉRIES", " VF"),
    "DE": ("DE | FILME", "DE | SERIEN", ""),
    "IT": ("IT | FILM", "IT | SERIE TV", ""),
}
IDX = {"EN": 1, "PT": 2, "BR": 2, "ES": 3, "FR": 4, "DE": 5, "IT": 6}
SERIES = {  # nome por idioma
    "EN": ["Breaking Bad", "Money Heist", "Squid Game", "Dark", "The Crown", "Stranger Things", "Friends", "The Witcher"],
    "PT": ["Breaking Bad", "La Casa de Papel", "Round 6", "Dark", "The Crown", "Stranger Things", "Friends", "The Witcher"],
    "BR": ["Breaking Bad", "La Casa de Papel", "Round 6", "Dark", "The Crown", "Stranger Things", "Friends", "The Witcher"],
    "ES": ["Breaking Bad", "La Casa de Papel", "El juego del calamar", "Dark", "The Crown", "Stranger Things", "Friends", "The Witcher"],
    "FR": ["Breaking Bad", "La Casa de Papel", "Squid Game", "Dark", "The Crown", "Stranger Things", "Friends", "The Witcher"],
    "DE": ["Breaking Bad", "Haus des Geldes", "Squid Game", "Dark", "The Crown", "Stranger Things", "Friends", "The Witcher"],
    "IT": ["Breaking Bad", "La casa di carta", "Squid Game", "Dark", "The Crown", "Stranger Things", "Friends", "The Witcher"],
}
# canais por país: (grupo, [nomes])
CHANNELS = [
    ("PT | GERAL", ["RTP 1", "RTP 2", "SIC", "TVI", "CMTV", "SIC Notícias", "TVI Reality"]),
    ("PT | DESPORTO", ["Sport TV 1", "Sport TV 2", "Sport TV 3", "Eleven Sports 1", "Eleven Sports 2", "BTV"]),
    ("BR | ABERTOS", ["Globo SP", "Globo RJ", "SBT", "Record", "Band", "RedeTV"]),
    ("BR | ESPORTES", ["SporTV", "SporTV 2", "ESPN Brasil", "Premiere 1", "Combate"]),
    ("BR | FILMES E SÉRIES", ["Telecine Premium", "Telecine Action", "HBO", "Warner Channel"]),
    ("ES | GENERALISTAS", ["La 1", "La 2", "Antena 3", "Cuatro", "Telecinco", "laSexta"]),
    ("ES | DEPORTES", ["DAZN 1", "Movistar Liga de Campeones", "LaLiga TV", "Eurosport 1 ES"]),
    ("FR | GÉNÉRALISTES", ["TF1", "France 2", "France 3", "M6", "Arte", "C8"]),
    ("FR | SPORT", ["Canal+ Sport", "beIN Sports 1", "beIN Sports 2", "RMC Sport 1", "Eurosport 1 FR"]),
    ("FR | CINÉMA", ["Canal+ Cinéma", "OCS Max", "Paramount Channel"]),
    ("UK | ENTERTAINMENT", ["BBC One", "BBC Two", "ITV 1", "Channel 4", "Sky Showcase"]),
    ("UK | SPORTS", ["Sky Sports Main Event", "Sky Sports Premier League", "TNT Sports 1", "TNT Sports 2"]),
    ("US | NEWS", ["CNN", "Fox News", "MSNBC", "ABC News"]),
    ("US | SPORTS", ["ESPN", "ESPN 2", "Fox Sports 1", "NBA TV"]),
    ("DE | ALLGEMEIN", ["Das Erste", "ZDF", "RTL", "ProSieben", "Sat.1"]),
    ("DE | SPORT", ["Sky Sport Bundesliga 1", "DAZN 1 DE", "Eurosport 1 DE"]),
    ("IT | GENERALISTE", ["Rai 1", "Rai 2", "Canale 5", "Italia 1", "La7"]),
    ("IT | SPORT", ["Sky Sport Calcio", "DAZN Italia", "Eurosport 1 IT"]),
    ("AR | عام", ["MBC 1", "Al Jazeera", "Rotana Cinema"]),
    ("TR | ULUSAL", ["TRT 1", "Show TV", "Star TV", "ATV"]),
    ("XXX | ADULT 18+", ["Adult Test 1", "Adult Test 2"]),
]
QUAL = ["", " HD", " FHD", " SD", " 4K", " H265"]


def esc(s):
    return s.replace('"', "'")


def extinf(name, group, logo=None, tmdb=None):
    lg = ' tvg-logo="%s"' % logo if logo else ""
    if tmdb:
        lg += ' tvg-id="tmdb:%s"' % tmdb
    return '#EXTINF:-1%s group-title="%s",%s' % (lg, esc(group), name)


def small():
    out = ["#EXTM3U"]
    n = 0
    # canais
    for group, names in CHANNELS:
        for nm in names:
            qs = random.sample(QUAL, 3)
            for q in qs:
                n += 1
                out.append(extinf(nm + q, group))
                out.append(LIVE + "?c=%d" % n)
    # filmes, cada um em vários idiomas
    for fi, f in enumerate(FILMES_LOOP()):
        y, names = f[0], f[1:]
        for lg, (gm, gs, suf) in LANGS.items():
            nm = names[IDX[lg] - 1]
            tag = " MULTI" if random.random() < 0.15 else ""
            for ver in ("", " 4K") if random.random() < 0.3 else ("",):
                n += 1
                out.append(extinf("%s (%d)%s%s%s" % (nm, y, suf, tag, ver), gm, None, 1000 + fi))
                out.append(VOD + "?m=%d" % n)
    # séries
    for lg, (gm, gs, suf) in LANGS.items():
        for si, sname in enumerate(SERIES[lg]):
            for season in (1, 2):
                for ep in (1, 2, 3):
                    n += 1
                    out.append(extinf("%s S%02dE%02d" % (sname, season, ep), gs, None, 5000 + si))
                    out.append(VOD + "?s=%d" % n)
    return out


def FILMES_LOOP():
    return FILMS


def big(target_vod=22000, target_live=7000):
    random.seed(7)
    sy = ["ka", "to", "mi", "ra", "lu", "ve", "no", "sa", "di", "el", "or", "an", "ti", "ma", "re", "so"]
    def word():
        return "".join(random.choice(sy) for _ in range(random.randint(2, 4))).capitalize()
    out = ["#EXTM3U"]
    n = 0
    langs = list(LANGS.items())
    for i in range(target_live):
        grp, names = random.choice(CHANNELS[:-1])
        nm = "%s %s%s" % (word(), word(), random.choice(QUAL))
        n += 1
        out.append(extinf(nm, grp, "https://example.invalid/logo/%d.png" % i))
        out.append(LIVE + "?c=%d" % n)
    for i in range(target_vod):
        lg, (gm, gs, suf) = random.choice(langs)
        if random.random() < 0.8:
            y = random.randint(1960, 2025)
            n += 1
            out.append(extinf("%s %s (%d)%s" % (word(), word(), y, suf), gm + (" | " + random.choice(["ACTION", "DRAMA", "COMEDY", "HORROR", "ANIMATION"]))))
            out.append(VOD + "?m=%d" % n)
        else:
            sname = "%s %s" % (word(), word())
            for season in range(1, random.randint(2, 4)):
                for ep in range(1, random.randint(3, 7)):
                    n += 1
                    out.append(extinf("%s S%02dE%02d" % (sname, season, ep), gs))
                    out.append(VOD + "?s=%d" % n)
    return out


if __name__ == "__main__":
    random.seed(11)
    open("testlists/test-multipais.m3u", "w", encoding="utf-8").write("\n".join(small()) + "\n")
    open("testlists/test-multipais-grande.m3u", "w", encoding="utf-8").write("\n".join(big()) + "\n")
