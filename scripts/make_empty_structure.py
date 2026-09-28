"""Write the empty 3x3x3 GameTest structure as gzipped NBT. Stdlib only.

Usage: python scripts/make_empty_structure.py src/main/resources/data/spellcraft/structure/empty.nbt
"""

import gzip
import struct
import sys
from pathlib import Path

# world_version from Minecraft 26.3's version.json (client.jar).
DATA_VERSION = 5023

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


def string(value: str) -> bytes:
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def named(tag: int, name: str, payload: bytes) -> bytes:
    return bytes([tag]) + string(name) + payload


def int_list(values: list[int]) -> bytes:
    return (
        bytes([TAG_INT])
        + struct.pack(">i", len(values))
        + b"".join(struct.pack(">i", v) for v in values)
    )


def compound_list(payloads: list[bytes]) -> bytes:
    return bytes([TAG_COMPOUND]) + struct.pack(">i", len(payloads)) + b"".join(payloads)


def empty_list() -> bytes:
    return bytes([TAG_END]) + struct.pack(">i", 0)


def main(out: Path) -> None:
    air = named(TAG_STRING, "Name", string("minecraft:air")) + bytes([TAG_END])
    root = (
        named(TAG_INT, "DataVersion", struct.pack(">i", DATA_VERSION))
        + named(TAG_LIST, "size", int_list([3, 3, 3]))
        + named(TAG_LIST, "palette", compound_list([air]))
        + named(TAG_LIST, "blocks", empty_list())
        + named(TAG_LIST, "entities", empty_list())
        + bytes([TAG_END])
    )
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(gzip.compress(named(TAG_COMPOUND, "", root), mtime=0))


if __name__ == "__main__":
    main(Path(sys.argv[1]))
