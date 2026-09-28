"""Write the 18x18 Upside-Down effect icon (a downward arrow) as a PNG. Stdlib only.

Usage: python scripts/make_effect_icon.py src/main/resources/assets/spellcraft/textures/mob_effect/upside_down.png
"""

import struct
import sys
import zlib
from pathlib import Path

SIZE = 18
INK = (127, 90, 240, 255)
CLEAR = (0, 0, 0, 0)


def pixel(x: int, y: int) -> tuple[int, int, int, int]:
    if 8 <= x <= 9 and 2 <= y <= 10:  # shaft
        return INK
    if 9 <= y <= 15 and abs(x - 8.5) <= 15 - y:  # arrowhead
        return INK
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
