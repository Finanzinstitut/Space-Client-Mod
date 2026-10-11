#!/usr/bin/env python3
"""
Erzeugt die Rang-Zeichen.

Die vier ersten wurden von Hand gezeichnet, und alle vier tragen exakt
dieselbe Schattierungskarte - nur die Farbtreppe unterscheidet sie. Genau das
macht dieses Skript: die Karte bleibt, die Treppe kommt aus einer Grundfarbe.

Die vier vorhandenen Dateien werden nicht angefasst. Sie sind richtig, und
eine nachgerechnete Farbtreppe waere ihnen nur aehnlich, nicht gleich - ein
Unterschied, den man bei acht Pixeln sieht.
"""

from PIL import Image
import os

# 7x7-Kugel in einem 8x8-Feld, die unterste Zeile bleibt leer.
# 0 = Umriss, 1 = untere Haelfte, 2 = obere Haelfte, 3 = Band, 4 = Glanzpunkt.
SHADE = [
    "..0000..",
    ".022220.",
    "02422220",
    "33333333",
    "01111110",
    ".011110.",
    "..0000..",
    "........",
]


def mix(colour, target, amount):
    return tuple(round(c + (t - c) * amount) for c, t in zip(colour, target))


def ramp(mid):
    """
    Fuenf Stufen aus einer Grundfarbe.

    Die Zahlen sind an den vier gezeichneten Zeichen abgelesen, nicht erfunden:
    der Umriss liegt bei knapp einem Drittel, das Band wird am hellsten, und
    der Glanzpunkt ist fast weiss, ohne es zu sein - ganz weiss verliert den
    Farbton, und dann sehen alle Zeichen an der Stelle gleich aus.
    """
    return [
        mix(mix(mid, (0, 0, 0), 0.70), (20, 16, 30), 0.15),  # 0 Umriss
        mid,                                                  # 1 untere Haelfte
        mix(mid, (255, 255, 255), 0.58),                      # 2 obere Haelfte
        mix(mid, (255, 255, 255), 0.76),                      # 3 Band
        mix(mid, (255, 255, 255), 0.93),                      # 4 Glanz
    ]


def draw(mid):
    steps = ramp(mid)
    image = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    for y, row in enumerate(SHADE):
        for x, cell in enumerate(row):
            if cell == ".":
                continue
            image.putpixel((x, y), steps[int(cell)] + (255,))
    return image


# Name der Datei -> Grundfarbe. Die Namen sind frei waehlbar; wer einen Rang
# umbenennen will, aendert diese Zeile, den Aufzaehlungstyp in Badges.java und
# den Eintrag in badge.json.
NEW = {
    "badge_mod":       (86, 200, 122),   # Moderator - Gruen
    "badge_partner":   (226, 122, 204),  # Partner - Magenta
    "badge_creator":   (236, 106, 106),  # Creator - Korallenrot
    "badge_supporter": (240, 140, 70),   # Supporter - Orange
    "badge_tester":    (170, 214, 74),   # Tester - Limette
    "badge_og":        (176, 182, 200),  # OG - Silber
}

OUT = "src/main/resources/assets/spaceclient/textures/font"

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for name, colour in NEW.items():
        path = os.path.join(OUT, name + ".png")
        draw(colour).save(path)
        print("geschrieben", path)
