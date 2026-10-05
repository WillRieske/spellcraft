"""Tests for spell_icons.py. Stdlib only. Run: python -m unittest discover -s scripts -p "test_*.py" """

import struct
import unittest
import zlib

import spell_icons as icons

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def chunks(png: bytes) -> list[tuple[bytes, bytes]]:
    """Split a PNG into (type, data) chunks, checking every CRC."""
    assert png.startswith(PNG_SIGNATURE), "bad PNG signature"
    out, pos = [], len(PNG_SIGNATURE)
    while pos < len(png):
        (length,) = struct.unpack(">I", png[pos : pos + 4])
        kind = png[pos + 4 : pos + 8]
        data = png[pos + 8 : pos + 8 + length]
        (crc,) = struct.unpack(">I", png[pos + 8 + length : pos + 12 + length])
        assert crc == zlib.crc32(kind + data) & 0xFFFFFFFF, f"bad CRC in {kind!r}"
        out.append((kind, data))
        pos += 12 + length
    return out


class SpellIconsTest(unittest.TestCase):
    def test_i1_every_glyph_pixel_lands_on_the_paper(self):
        for spell in icons.SPELLS:
            for x, y in icons.glyph_pixels(spell, 1, spell.origin):
                with self.subTest(spell=spell.id, x=x, y=y):
                    self.assertTrue(0 <= x < icons.SCROLL_SIZE and 0 <= y < icons.SCROLL_SIZE)
                    self.assertNotEqual(icons.SHEET[y][x], ".", "glyph pixel off the paper")

    def test_i2_glyphs_are_well_formed_and_fit_the_effect_icon(self):
        for spell in icons.SPELLS:
            with self.subTest(spell=spell.id):
                widths = {len(row) for row in spell.glyph}
                self.assertEqual(len(widths), 1, "rows must be the same width")
                self.assertTrue(set("".join(spell.glyph)) <= {"#", "."})
                width, height = widths.pop() * icons.EFFECT_SCALE, len(spell.glyph) * icons.EFFECT_SCALE
                self.assertLessEqual(width, icons.EFFECT_SIZE - 2)
                self.assertLessEqual(height, icons.EFFECT_SIZE - 2)

    def test_i3_outputs_are_valid_rgba_pngs_of_the_right_size(self):
        for spell in icons.SPELLS:
            for path, png in icons.outputs(spell).items():
                with self.subTest(path=path.name):
                    parsed = chunks(png)
                    self.assertEqual(parsed[0][0], b"IHDR")
                    width, height, depth, color = struct.unpack(">IIBB", parsed[0][1][:10])
                    size = icons.SCROLL_SIZE if path.parent.name == "item" else icons.EFFECT_SIZE
                    self.assertEqual((width, height, depth, color), (size, size, 8, 6))
                    raw = zlib.decompress(b"".join(d for k, d in parsed if k == b"IDAT"))
                    self.assertEqual(len(raw), height * (1 + width * 4))
                    self.assertEqual(parsed[-1][0], b"IEND")

    def test_i4_generation_is_deterministic(self):
        for spell in icons.SPELLS:
            self.assertEqual(icons.outputs(spell), icons.outputs(spell))

    def test_i5_committed_pngs_match_the_generator(self):
        for spell in icons.SPELLS:
            for path, png in icons.outputs(spell).items():
                with self.subTest(path=path.name):
                    self.assertTrue(path.exists(), f"{path} missing: run python scripts/spell_icons.py")
                    self.assertEqual(path.read_bytes(), png, f"{path} stale: run python scripts/spell_icons.py")

    def test_i6_every_spell_has_both_icons_and_no_icon_lacks_a_spell(self):
        expected = {path for spell in icons.SPELLS for path in icons.outputs(spell)}
        self.assertEqual(len(expected), 2 * len(icons.SPELLS))
        on_disk = set((icons.TEXTURES / "item").glob("*_scroll.png")) | set((icons.TEXTURES / "mob_effect").glob("*.png"))
        self.assertEqual(on_disk, expected)


if __name__ == "__main__":
    unittest.main()
