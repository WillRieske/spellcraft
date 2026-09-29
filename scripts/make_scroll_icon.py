"""Write the 16x16 Upside-Down scroll item icon (parchment with the purple down-arrow) as a PNG. Stdlib only.

Original art: nothing is copied from Minecraft's textures.

Usage: python scripts/make_scroll_icon.py src/main/resources/assets/spellcraft/textures/item/upside_down_scroll.png
"""

import struct
import sys
import zlib
from pathlib import Path

SIZE = 16
CLEAR = (0, 0, 0, 0)
INK = (127, 90, 240, 255)  # same purple as the effect icon

# A diagonal sheet of paper (same silhouette as the vanilla paper item). Digits index PALETTE;
# '.' is transparent.
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
PALETTE = {
    "1": (150, 118, 72, 255),  # rim
    "2": (214, 190, 140, 255),
    "3": (232, 214, 170, 255),
    "4": (240, 228, 188, 255),  # fill
    "5": (178, 148, 100, 255),
    "6": (120, 92, 56, 255),  # shadow edge
}
# The down-arrow: a 2px shaft and a stepped head, all inside the sheet.
ARROW = {(x, y) for y in range(4, 9) for x in (7, 8)}
ARROW |= {(x, 8) for x in range(5, 11)} | {(x, 9) for x in range(6, 10)} | {(x, 10) for x in (7, 8)}


def pixel(x: int, y: int) -> tuple[int, int, int, int]:
    if (x, y) in ARROW:
        return INK
    return PALETTE.get(SHEET[y][x], CLEAR)


def chunk(kind: bytes, data: bytes) -> bytes:
    body = kind + data
    return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)


def main(out: Path) -> None:
    rows = b"".join(
        b"\x00" + b"".join(bytes(pixel(x, y)) for x in range(SIZE)) for y in range(SIZE)
    )
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(rows, 9))
        + chunk(b"IEND", b"")
    )
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(png)


if __name__ == "__main__":
    main(Path(sys.argv[1]))
