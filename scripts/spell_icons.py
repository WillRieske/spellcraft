"""Write every spell's icons from one glyph per spell. Stdlib only.

- Scroll item (16x16): the glyph at 1x on a diagonal parchment sheet (same silhouette as the vanilla paper item).
- Effect icon (18x18): the glyph at 2x, centred.

Original art: nothing is copied from Minecraft's textures.

Usage: python scripts/spell_icons.py      (writes into src/main/resources/assets/spellcraft/textures)
"""

import struct
import zlib
from dataclasses import dataclass
from pathlib import Path

Pixel = tuple[int, int, int, int]

CLEAR: Pixel = (0, 0, 0, 0)
SCROLL_SIZE = 16
EFFECT_SIZE = 18
EFFECT_SCALE = 2
TEXTURES = Path(__file__).resolve().parent.parent / "src/main/resources/assets/spellcraft/textures"

# A diagonal sheet of paper. Digits index PALETTE; '.' is transparent.
SHEET = [
    "................",
    "................",
    ".........11.....",
    "........1321....",
    "......1124431...",
    ".....134444426..",
    "...112444444416.",
    "..12344444444216",
    ".12344444444436.",
    "..623444444326..",
    "...6234444366...",
    "....6234356.....",
    ".....62566......",
    "......66........",
    "................",
    "................",
]
PALETTE: dict[str, Pixel] = {
    "1": (150, 118, 72, 255),  # rim
    "2": (214, 190, 140, 255),
    "3": (232, 214, 170, 255),
    "4": (240, 228, 188, 255),  # fill
    "5": (178, 148, 100, 255),
    "6": (120, 92, 56, 255),  # shadow edge
}


@dataclass(frozen=True)
class Spell:
    id: str
    ink: Pixel
    glyph: tuple[str, ...]  # '#' = ink, '.' = clear
    origin: tuple[int, int]  # top-left of the glyph on the scroll sheet


SPELLS = (
    Spell(
        "upside_down",
        (127, 90, 240, 255),  # purple, as the effect colour 0x7F5AF0
        ("..##..", "..##..", "..##..", "..##..", "######", ".####.", "..##.."),
        (5, 4),
    ),
    Spell(
        "infinity",
        (214, 150, 20, 255),  # gold, as the effect colour 0xD69614
        (".##.##.", "#..#..#", "#..#..#", ".##.##."),
        (5, 6),
    ),
)


def glyph_pixels(spell: Spell, scale: int, offset: tuple[int, int]) -> frozenset[tuple[int, int]]:
    ox, oy = offset
    return frozenset(
        (ox + x * scale + dx, oy + y * scale + dy)
        for y, row in enumerate(spell.glyph)
        for x, cell in enumerate(row)
        if cell == "#"
        for dy in range(scale)
        for dx in range(scale)
    )


def effect_offset(spell: Spell) -> tuple[int, int]:
    width = len(spell.glyph[0]) * EFFECT_SCALE
    height = len(spell.glyph) * EFFECT_SCALE
    return (EFFECT_SIZE - width) // 2, (EFFECT_SIZE - height) // 2


def chunk(kind: bytes, data: bytes) -> bytes:
    body = kind + data
    return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)


def encode_png(size: int, pixel_at) -> bytes:
    rows = b"".join(b"\x00" + b"".join(bytes(pixel_at(x, y)) for x in range(size)) for y in range(size))
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(rows, 9))
        + chunk(b"IEND", b"")
    )


def scroll_png(spell: Spell) -> bytes:
    ink = glyph_pixels(spell, 1, spell.origin)
    return encode_png(SCROLL_SIZE, lambda x, y: spell.ink if (x, y) in ink else PALETTE.get(SHEET[y][x], CLEAR))


def effect_png(spell: Spell) -> bytes:
    ink = glyph_pixels(spell, EFFECT_SCALE, effect_offset(spell))
    return encode_png(EFFECT_SIZE, lambda x, y: spell.ink if (x, y) in ink else CLEAR)


def outputs(spell: Spell) -> dict[Path, bytes]:
    return {
        TEXTURES / "item" / f"{spell.id}_scroll.png": scroll_png(spell),
        TEXTURES / "mob_effect" / f"{spell.id}.png": effect_png(spell),
    }


def main() -> None:
    for spell in SPELLS:
        for path, png in outputs(spell).items():
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(png)


if __name__ == "__main__":
    main()
