"""Write the Caster's robe texture: 32 x 32, laid out like the vex texture. Stdlib only.

Only the body's and the arms' faces are painted (purple #5B2A86, magenta #C03FA8 trim, a ragged transparent lower
edge); the head and the wings stay transparent, so the layer that draws the model again with this texture dresses
only the body. The cubes' UV blocks follow VexModel.createBodyLayer (the cube at texOffs (u, v) with width w, height h
and depth d has its top at (u + d, v), its bottom at (u + d + w, v), and, from row v + d down, its right side at u, its
front at u + d, its left side at u + d + w and its back at u + 2d + w).

Original art: nothing is copied from Minecraft's textures.

Usage: python scripts/robe_texture.py      (writes into src/main/resources/assets/spellcraft/textures/entity)
"""

from pathlib import Path

from spell_icons import encode_png

Pixel = tuple[int, int, int, int]

SIZE = 32
CLEAR: Pixel = (0, 0, 0, 0)
PURPLE: Pixel = (0x5B, 0x2A, 0x86, 255)
MAGENTA: Pixel = (0xC0, 0x3F, 0xA8, 255)
OUT = (
    Path(__file__).resolve().parent.parent
    / "src/main/resources/assets/spellcraft/textures/entity/bound_vex_robe.png"
)

# (u, v, width, height, depth) of the four painted cubes in VexModel: upper body, lower body, right arm, left arm.
CUBES = ((0, 10, 3, 4, 2), (0, 16, 3, 5, 2), (23, 0, 2, 4, 2), (23, 6, 2, 4, 2))


def side_faces(cube: tuple[int, int, int, int, int]) -> list[tuple[int, int, int, int]]:
    """Return the four side faces (x, y, width, height) of a cube's UV block."""
    u, v, w, h, d = cube
    return [(u, v + d, d, h), (u + d, v + d, w, h), (u + d + w, v + d, d, h), (u + 2 * d + w, v + d, w, h)]


def cap_faces(cube: tuple[int, int, int, int, int]) -> list[tuple[int, int, int, int]]:
    """Return the top and bottom faces (x, y, width, height) of a cube's UV block."""
    u, v, w, _, d = cube
    return [(u + d, v, w, d), (u + d + w, v, w, d)]


def pixels() -> dict[tuple[int, int], Pixel]:
    """Return the painted pixels: purple faces, a magenta first row on each side face, notches on the hem."""
    painted: dict[tuple[int, int], Pixel] = {}
    for index, cube in enumerate(CUBES):
        for x0, y0, w, h in cap_faces(cube):
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    painted[(x, y)] = PURPLE
        for x0, y0, w, h in side_faces(cube):
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    painted[(x, y)] = MAGENTA if y == y0 else PURPLE
                    if index == 1 and y == y0 + h - 1 and (x + 2 * y) % 3 == 0:
                        del painted[(x, y)]  # the ragged hem of the lower body
    return painted


def png() -> bytes:
    """Return the robe texture as PNG bytes."""
    painted = pixels()
    return encode_png(SIZE, lambda x, y: painted.get((x, y), CLEAR))


def main() -> None:
    """Write the texture."""
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(png())


if __name__ == "__main__":
    main()
