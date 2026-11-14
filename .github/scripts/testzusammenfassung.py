"""Schreibt JUnit-XML-Berichte als Markdown, damit GitHub sie in der Zusammenfassung eines Laufs anzeigt.

Aufruf: python3 testzusammenfassung.py "Titel" ordner [ordner ...] >> "$GITHUB_STEP_SUMMARY"
"""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def faelle_lesen(datei):
    suite = ET.parse(datei).getroot()
    faelle = []
    for fall in suite.iter("testcase"):
        fehler = fall.find("failure")
        if fehler is None:
            fehler = fall.find("error")
        if fehler is not None:
            status, hinweis = "❌", (fehler.get("message") or "").splitlines()[:1]
        elif fall.find("skipped") is not None:
            status, hinweis = "⏭️", []
        else:
            status, hinweis = "✅", []
        zeit = float(fall.get("time", "0"))
        faelle.append((status, fall.get("name", "?"), zeit, hinweis[0] if hinweis else ""))
    return suite.get("name", datei.stem), faelle


def zelle(text):
    return text.replace("|", "\\|")


def main():
    titel, *ordner = sys.argv[1:]
    sys.stdout.reconfigure(encoding="utf-8")
    berichte = [faelle_lesen(d) for o in ordner for d in sorted(Path(o).glob("TEST-*.xml"))]
    alle = [f for _, faelle in berichte for f in faelle]
    fehlgeschlagen = sum(1 for f in alle if f[0] == "❌")

    print(f"## {titel}\n")
    if not alle:
        print("Keine Testberichte gefunden.")
        return
    print(f"{len(alle)} Tests, {fehlgeschlagen} fehlgeschlagen\n")
    for klasse, faelle in berichte:
        print(f"**{klasse.rsplit('.', 1)[-1]}**\n")
        print("| | Test | Zeit |\n|---|---|---|")
        for status, name, zeit, _ in faelle:
            sekunden = str(round(zeit, 1)).replace(".", ",")
            print(f"| {status} | {zelle(name)} | {sekunden} s |")
        for status, name, _, hinweis in faelle:
            if status == "❌":
                print(f"\n❌ {zelle(name)}: {hinweis}")
        print()


if __name__ == "__main__":
    main()
