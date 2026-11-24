r"""Gera app/src/main/assets/emojis_pt.tsv a partir das anotacoes em portugues do CLDR (Unicode).

Uso:  python -I -X utf8 ferramentas\gerar_emojis.py <annotations.json> <annotationsDerived.json> <saida.tsv>

Arquivos de origem (licenca Unicode, ver NOTICE em app/src/main/assets/emojis_NOTICE.txt):
  cldr-json/cldr-annotations-full/annotations/pt/annotations.json
  cldr-json/cldr-annotations-derived-full/annotationsDerived/pt/annotations.json
Cada linha de saida: emoji <TAB> nome curto ("tts") em portugues do Brasil.
"""
import json
import sys


def ler(caminho, bloco):
    with open(caminho, encoding="utf-8") as f:
        dados = json.load(f)
    return dados[bloco]["annotations"]


def principal(argv):
    if len(argv) != 4:
        print(__doc__)
        return 2
    base = ler(argv[1], "annotations")
    derivadas = ler(argv[2], "annotationsDerived")
    nomes = {}
    for origem in (base, derivadas):
        for emoji, info in origem.items():
            tts = info.get("tts") or []
            if tts:
                nomes[emoji] = tts[0]
    saida = []
    for emoji, nome in sorted(nomes.items()):
        so_ascii = all(ord(c) < 0x80 for c in emoji)
        if so_ascii:
            continue                       # letras e sinais comuns nao sao emoji
        if emoji.replace("️", "") in ("©", "®", "™"):
            continue                       # aparecem em texto normal
        if "\t" in nome or "\n" in nome:
            continue
        saida.append(f"{emoji}\t{nome}")
    # teclas (1️⃣ # * etc.) tem ascii, entao entram aparte
    for emoji, nome in sorted(nomes.items()):
        if "⃣" in emoji:
            saida.append(f"{emoji}\t{nome}")
    with open(argv[3], "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(dict.fromkeys(saida)) + "\n")
    print(f"{len(set(saida))} emojis gravados em {argv[3]}")
    return 0


if __name__ == "__main__":
    sys.exit(principal(sys.argv))
