#!/usr/bin/env python3
"""
Ajuda da tradução: envolve com tr(...) os textos de tela (em português) de um arquivo Kotlin.
Textos com $variavel / ${expr} viram tr("... {0} ...", variavel).

Uso: python3 tools/i18n_wrap.py app/src/main/java/app/iptvtest/Screens.kt [...]
Depois revise o diff: o script pula chaves, comparações, logs e afins, mas não é perfeito.
"""
import re
import sys

LIT = re.compile(r'"((?:[^"\\]|\\.)*)"')
SKIP_BEFORE = re.compile(
    r'(key\s*=\s*|focusId\s*=\s*|(?<!\{ )label\s*=\s*|contentType\s*=\s*|tag\s*=\s*|==\s*|!=\s*|equals\(\s*|startsWith\(\s*|endsWith\(\s*|'
    r'contains\(\s*|split\(\s*|replace\(\s*|Regex\(\s*|getString\(\s*|putString\(\s*|optString\(\s*|optInt\(\s*|optLong\(\s*|optBoolean\(\s*|'
    r'optJSONArray\(\s*|optJSONObject\(\s*|getLong\(\s*|getInt\(\s*|putInt\(\s*|putLong\(\s*|putBoolean\(\s*|getBoolean\(\s*|\.put\(\s*|'
    r'setRequestProperty\(\s*|Log\.[a-z]\(\s*|tr\(\s*|format\(\s*|getSharedPreferences\(\s*|Uri\.parse\(\s*|setOf\(\s*|'
    r'item\(\s*key\s*=\s*|remove\(\s*|indexOf\(\s*|substringBefore\(\s*|substringAfter\(\s*|removePrefix\(\s*|removeSuffix\(\s*|'
    r'Locale\([^)]*|forLanguageTag\(\s*|NotificationChannel\(\s*\w+\s*,\s*)$'
)
SKIP_LINE = re.compile(r'^\s*(//|\*|/\*|import |package |@)|const val|Log\.|Regex\(|\.toRegex|private val [A-Z_]+ = ')


def looks_ui(s):
    if not re.search(r'[A-Za-zÀ-ú]{2}', s):
        return False
    if '|' in s or s.startswith('http') or re.match(r'^[a-z0-9_.:/#-]+$', s):
        return False
    if re.match(r'^[A-Z0-9_]+$', s) and len(s) <= 4:  # "4K", "M3U", "OK"
        return False
    if '%' in s:  # formatos: tratar à mão
        return False
    return bool(re.search(r'[ À-úçãõ]', s) or re.match(r'^[A-ZÀ-Ú][a-zà-ú]+[.!?:…]*$', s) or re.match(r'^[A-ZÀ-Ú]{4,}$', s))


TEMPLATE = re.compile(r'\$\{([^}]*)\}|\$([A-Za-z_][A-Za-z0-9_]*)')


def wrap_lit(content):
    args = []

    def sub(m):
        args.append(m.group(1) if m.group(1) is not None else m.group(2))
        return '{' + str(len(args) - 1) + '}'

    key = TEMPLATE.sub(sub, content)
    if args:
        return 'tr("' + key + '", ' + ', '.join(args) + ')'
    return 'tr("' + content + '")'


def process_line(line):
    if SKIP_LINE.search(line):
        return line
    out, pos = [], 0
    for m in LIT.finditer(line):
        s = m.group(1)
        before = line[:m.start()]
        after = line[m.end():]
        ok = looks_ui(s)
        if ok and SKIP_BEFORE.search(before):
            ok = False
        if ok and re.match(r'\s*(->|to\b|\|)', after):  # ramo de when, par de mapa
            ok = False
        if ok and re.search(r'\bto\s*$', before):  # segundo item de um par: "pt-BR" to "Português"
            ok = False
        out.append(line[pos:m.start()])
        out.append(wrap_lit(s) if ok else line[m.start():m.end()])
        pos = m.end()
    out.append(line[pos:])
    return ''.join(out)


def main():
    for path in sys.argv[1:]:
        src = open(path, encoding='utf-8').read().split('\n')
        res = [process_line(l) for l in src]
        open(path, 'w', encoding='utf-8').write('\n'.join(res))
        n = sum(1 for a, b in zip(src, res) if a != b)
        print(path, n, 'linhas')


if __name__ == '__main__':
    main()
