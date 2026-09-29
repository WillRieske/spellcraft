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
OUTLINE = (98, 74, 46, 255)
PARCHMENT = (238, 226, 190, 255)
ROLL = (200, 166, 112, 255)
INK = (127, 90, 240, 255)  # same purple as the effect icon


def pixel(x: int, y: int) -> tuple[int, int, int, int]:
    if 1 <= x <= 14 and 1 <= y <= 14:
        if x in (1, 14) or y in (1, 14):
            return OUTLINE
        if y in (2, 13):  # rolled top and bottom edges
            return ROLL
        if 7 <= x <= 8 and 4 <= y <= 8:  # arrow shaft
            return INK
        if 8 <= y <= 11 and abs(x - 7.5) <= 11 - y:  # arrowhead
            return INK
        return PARCHMENT
    return CLEAR


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
