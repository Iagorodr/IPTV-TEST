import json, re, sys, os
D = os.path.dirname(os.path.abspath(__file__))
keys = json.load(open(os.path.join(D, "keys.json"), encoding="utf-8"))
OUT = os.path.join(D, "..", "..", "app/src/main/assets/i18n")
os.makedirs(OUT, exist_ok=True)
for lang in sys.argv[1:]:
    tr = {}
    for line in open(os.path.join(D, lang + ".txt"), encoding="utf-8").read().split("\n"):
        if not line.strip():
            continue
        i, t = line.split("\t", 1)
        tr[int(i)] = t.replace("⏎", "\n")
    out, problems = {}, []
    for i, k in enumerate(keys):
        t = tr.get(i)
        if t is None:
            problems.append(f"falta {i}: {k!r}")
            continue
        # o editor tira espaços das pontas: devolve os que a chave tem
        if k[:1] == " " and t[:1] != " ":
            t = " " + t
        if k[-1:] == " " and t[-1:] != " ":
            t = t + " "
        if k.startswith("  ") and not t.startswith("  "):
            t = " " + t
        if sorted(re.findall(r"\{\d\}", k)) != sorted(re.findall(r"\{\d\}", t)):
            problems.append(f"placeholders {i}: {k!r} -> {t!r}")
        if (k[:1] == " ") != (t[:1] == " ") or (k[-1:] == " ") != (t[-1:] == " "):
            problems.append(f"espaços {i}: {k!r} -> {t!r}")
        out[k] = t
    json.dump(out, open(os.path.join(OUT, lang + ".json"), "w", encoding="utf-8"), ensure_ascii=False, indent=0)
    print(lang, len(out), "textos;", len(problems), "avisos")
    for p in problems[:30]:
        print("  ", p)
