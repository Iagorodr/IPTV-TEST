#!/usr/bin/env python3
"""Lista os textos marcados com tr("...") no app (as chaves da tradução), um por linha em JSON."""
import glob
import json
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/java/app/iptvtest"
CALL = re.compile(r'\btr\(\s*"((?:[^"\\]|\\.)*)"')
# textos que chegam ao tr() por variável (gêneros, abas, dias, idiomas de legenda...)
EXTRA = [
    "Ação", "Aventura", "Animação", "Comédia", "Crime", "Documentários", "Drama", "Família", "Fantasia",
    "Ficção Científica", "Guerra", "Romance", "Suspense", "Terror", "Infantil", "Mistério", "História", "Biografia",
    "Policial", "Música", "Faroeste", "Turcas", "Shows", "Religiosos", "Nacionais", "Dorama", "Clássicos", "Anime",
    "Lançamentos", "Adicionados recentemente", "Sem categoria", "Continuar assistindo", "Favoritos",
    "Filmes", "Séries", "Canais", "Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb",
    "Português (Brasil)", "Português (Portugal)", "Inglês", "Espanhol", "Francês", "Italiano", "Alemão", "Holandês",
    "Novo: Fale com a gente", "Novo: Modo jogo", "O app no seu idioma", "Nunca perca um jogo", "Canal travou? A gente troca",
    "Em Ajustes, aponte o celular para o QR code e mande um problema, uma ideia ou um elogio. A resposta aparece aqui na TV.",
    "No player, toque em Modo jogo: cores mais vivas e mais contraste, ótimo para futebol.",
    "Português, inglês, espanhol, francês, italiano, alemão ou holandês. Troque quando quiser em Ajustes > Perfil.",
    "Escolha seus times e ligas: os jogos aparecem no Início, com o dia, a hora e o canal onde passam.",
    "Quando um canal tem várias fontes, o app escolhe a mais estável ou a de melhor imagem (você decide em Ajustes) e troca sozinho se travar.",
    "Sua lista, organizada", "Mais de uma lista? Junte tudo",
    "Filmes, séries e canais se organizam sozinhos por gênero, com visual de Netflix — não importa de onde vem a sua lista.",
    "Tem mais de uma assinatura? Entre com as duas e o app mostra tudo junto, num catálogo só.",
]


def unescape(s):
    return s.replace('\\"', '"').replace("\\n", "\n").replace("\\$", "$").replace("\\\\", "\\")


# a ordem antiga não muda (os .txt de tradução usam a posição): os textos novos entram no fim
import os
OLD = os.path.join(os.path.dirname(os.path.abspath(__file__)), "i18n", "keys.json")
keys = json.load(open(OLD, encoding="utf-8")) if os.path.exists(OLD) else []
seen = set(keys)
for f in sorted(glob.glob(ROOT + "/*.kt")):
    for m in CALL.finditer(open(f, encoding="utf-8").read()):
        k = unescape(m.group(1))
        if k and k not in seen:
            seen.add(k)
            keys.append(k)
for k in EXTRA:
    if k not in seen:
        seen.add(k)
        keys.append(k)
print(json.dumps(keys, ensure_ascii=False, indent=0))
