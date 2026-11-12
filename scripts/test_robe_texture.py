"""Tests for robe_texture.py. Stdlib only. Run: python -m unittest discover -s scripts -p "test_*.py" """

import struct
import unittest
import zlib

import robe_texture as robe


def decode(png: bytes) -> tuple[int, int, dict[tuple[int, int], tuple[int, int, int, int]]]:
    """Return (width, height, pixels) of an 8-bit RGBA PNG written by encode_png (filter 0, one IDAT)."""
    pos, idat, size = 8, b"", (0, 0)
    while pos < len(png):
        (length,) = struct.unpack(">I", png[pos : pos + 4])
        kind, data = png[pos + 4 : pos + 8], png[pos + 8 : pos + 8 + length]
        if kind == b"IHDR":
            size = struct.unpack(">II", data[:8])
        elif kind == b"IDAT":
            idat += data
        pos += 12 + length
    width, height = size
    raw = zlib.decompress(idat)
    pixels = {}
    for y in range(height):
        row = raw[y * (1 + 4 * width) + 1 : (y + 1) * (1 + 4 * width)]
        for x in range(width):
            pixels[(x, y)] = tuple(row[4 * x : 4 * x + 4])
    return width, height, pixels


class RobeTextureTest(unittest.TestCase):
    def test_r1_the_texture_is_the_vex_textures_size(self):
        """When the robe is generated, png should be a 32 x 32 image (the size of VexModel's layer).

        Function under test: :func:`robe_texture.png`
        """
        width, height, _ = decode(robe.png())
        self.assertEqual((width, height), (32, 32))

    def test_r2_the_head_and_the_wings_are_transparent(self):
        """When the robe is generated, png should leave the head's and the wings' regions fully transparent.

        Function under test: :func:`robe_texture.png`
        """
        _, _, pixels = decode(robe.png())
        regions = [(0, 0, 20, 10), (16, 22, 32, 27)]  # the head's UV block; the wing's two faces
        for x0, y0, x1, y1 in regions:
            for y in range(y0, y1):
                for x in range(x0, x1):
                    with self.subTest(x=x, y=y):
                        self.assertEqual(pixels[(x, y)][3], 0)

    def test_r3_the_body_front_is_purple_with_magenta_trim(self):
        """When the robe is generated, png should paint the body's front purple with a magenta top row.

        Function under test: :func:`robe_texture.png`
        """
        _, _, pixels = decode(robe.png())
        self.assertEqual(pixels[(3, 12)], robe.MAGENTA)  # first row of the front face of the upper body cube
        self.assertEqual(pixels[(3, 13)], robe.PURPLE)

    def test_r4_the_arms_are_painted(self):
        """When the robe is generated, png should paint both arms' front faces.

        Function under test: :func:`robe_texture.png`
        """
        _, _, pixels = decode(robe.png())
        self.assertEqual(pixels[(25, 4)][3], 255)  # right arm: front face at (25, 2), third row of four
        self.assertEqual(pixels[(25, 10)][3], 255)  # left arm: front face at (25, 8), third row of four

    def test_r5_the_lower_edge_is_ragged(self):
        """When the robe is generated, png should cut transparent notches into the lower body's last row.

        Function under test: :func:`robe_texture.png`
        """
        _, _, pixels = decode(robe.png())
        last_row = [pixels[(x, 22)][3] for x in range(2, 5)]  # front face of the lower cube, its last row
        self.assertIn(0, last_row)
        self.assertIn(255, last_row)

    def test_r6_only_the_two_robe_colors_are_used_and_the_output_is_stable(self):
        """When the robe is generated twice, png should give the same bytes and only purple, magenta or clear.

        Function under test: :func:`robe_texture.png`
        """
        first, second = robe.png(), robe.png()
        self.assertEqual(first, second)
        _, _, pixels = decode(first)
        self.assertTrue(set(pixels.values()) <= {robe.PURPLE, robe.MAGENTA, robe.CLEAR})

    def test_r7_the_committed_file_is_what_the_generator_writes(self):
        """When the robe is generated, png should equal the committed bound_vex_robe.png byte for byte.

        Function under test: :func:`robe_texture.png`
        """
        self.assertEqual(robe.OUT.read_bytes(), robe.png())
