# Spellcraft: "Strength of Trailing" (design)

**Date:** 2026-11-04
**Status:** Approved in brainstorming (fade model A, "fuse"); plan to follow
**Target:** Minecraft Java 26.3, NeoForge 26.3.0.23-beta, Java 25, ModDevGradle
**Builds on:** [`2026-09-29-infinity-design.md`](2026-09-29-infinity-design.md) (the shared spell
framework: declarations, cast rules, scribing, icon generator). Nothing there is superseded.

## Summary

**Strength of Trailing** makes you leave a trail of soul fire behind you for eight minutes. Each
time you step off a block while on the ground, the block you just left catches a light-blue
flame. The flames never hurt you or your allies, but anything else that touches them burns
as if it stood in soul fire. The trail is a slow fuse: once a second the oldest flame goes out
(every two seconds while that flame sits on soul sand or soul soil), so running leaves a long
burning path that burns back toward you when you stop. The flames spread to nothing, light no
TNT and open no nether portals.

Adding the spell follows the framework recipe: a declaration, an effect, a glyph and lang
entries. New here is the **trail fire block** and the server-side bookkeeping that lays and
fades trails and remembers who owns each flame.

## Glossary

| Term | Meaning |
|---|---|
| **Trail fire** | The mod's own fire block, `spellcraft:trail_fire`. Looks like vanilla soul fire, behaves as this spec says. |
| **Flame** | One trail fire block laid by one player, with a flag saying whether it *lingers*. |
| **Lingers** | A flame over a block in `#minecraft:soul_fire_base_blocks` (soul sand, soul soil). It takes 2 s to fade instead of 1 s. |
| **Trail** | One player's flames, oldest first, plus the countdown until the oldest fades. |
| **Owner** | The player who laid a flame. Known for every flame the world remembers. |
| **Orphan** | A trail fire block with no owner (placed by `/setblock`, or left after lost data). |
| **Ally** | What vanilla's `Entity.isAlliedTo` says: the same scoreboard team, or a tamed animal whose owner is (allied to) the player. |
| **Pure core** | Code with no `net.minecraft`/NeoForge imports, unit-testable with plain JUnit (package `spell.rules`). |
| **Level attachment** | NeoForge data stored on a world (`ServerLevel`) and saved with it. |

## 1. Scope

**In scope:**

- The Trailing declaration, effect, scroll, icons, lang entries and config (§2).
- The trail fire block (§3).
- Laying and fading trails, ownership and the ally rule (§4).
- Unit tests, jqwik properties, GameTests, script tests, docs and playtest items (§6).
- Deploying the built jar into Will's real `.minecraft/mods` for the local-install test (§7).

**Out of scope:**

- Any client code. The block renders through data (blockstate + vanilla models), and 26.3 picks
  the chunk layer from the texture's transparency, so no render-layer registration exists or is
  needed.
- Trails from vehicles, trails in the air, trails on water. Fire from the trail is the only
  damage; no extra damage types, no particles of our own.

## 2. The spell

### Declaration (pure core, `SpellDeclarations.TRAILING`)

| `SpellRules` field | Value |
|---|---|
| `name` | `trailing` |
| `tier` | `GOOD` |
| `recast` | `REFRESH` |
| `dispellable` | yes (sneak + use while active) |
| `redundantInCreative` | no (creative players cast it too) |

`SpellDeclarations.ALL = [UPSIDE_DOWN, INFINITY, TRAILING]`; `Spells.ALL` and `ModItems`
follow (the creative tab and scribing pick it up by themselves; `GOOD` is in scribing's
lower pool).

### Effect

`ModEffects.TRAILING`: a `MarkerEffect`, "Strength of Trailing", category *beneficial*, color
`0x4FD4E6` (soul-fire blue). Milk and totems cure it (vanilla default); the timer runs out at
the configured duration. The effect itself does nothing: the trail layer (§4) acts on its
presence.

### Duration and config (server, `trailing` section)

| Key | Default | Range | Meaning |
|---|---|---|---|
| `durationSeconds` | 480 | 1 … 1 000 000 | How long Strength of Trailing lasts. Read at cast time. |
| `maxTrailBlocks` | 1024 | 1 … 65 536 | A safety valve for servers: a player's trail never holds more flames than this; the oldest go out at once. |

### Scroll and icons

`trailing_scroll`, stack 16, glint, single-use, in the Tools & Utilities tab, like the others.
`scripts/spell_icons.py` gets a `trailing` entry: a 7×8 flame glyph in soul-fire blue
`(79, 212, 230)` on the parchment; the 18×18 effect icon is the same glyph at 2×. Lang:
*Scroll of Trailing*, *Strength of Trailing*, *Trail Fire*, and the two config labels.

## 3. The trail fire block

`spellcraft:trail_fire`, class `TrailFireBlock extends BaseFireBlock`, registered through a
`DeferredRegister.Blocks`. It has no item and no loot table.

**Properties:** soul fire's (`mapColor` light blue, `replaceable`, `noCollision`, `instabreak`,
`lightLevel` 10, `sound` wool, `pushReaction` POPPED) plus `randomTicks` (for §4 orphan cleanup)
and `noLootTable`. Fire damage **2.0** (soul fire's).

**Behavior, and why each differs from vanilla soul fire:**

| Hook | Trail fire | Reason |
|---|---|---|
| `canSurvive` | the block below has a sturdy top face (`isFaceSturdy(UP)`) | Soul fire needs soul sand below; the trail must sit on any ground. |
| `updateShape` | air when it can no longer survive (as `SoulFireBlock`) | Flames fall away when the ground goes. |
| `onPlace` | only the survive check | `BaseFireBlock.onPlace` lights nether portals; walking through an obsidian frame must not. |
| `canBurn` | always false | Nothing is flammable to it (only affects smoke particles). |
| scheduled tick | none (not overridden) | Vanilla spread and burn-out live in `FireBlock.tick`, which is the only vanilla path that ignites TNT by fire. No tick, no spread, no TNT. |
| `entityInside` | client: nothing. Server: nothing when the entity is *spared* (§4), else vanilla's clear-freeze, ignite and 2 damage | The whole point. |
| `randomTick` | an orphan removes itself; an owned flame does nothing | Cleans up flames the world no longer tracks. |

**Assets and data:** `blockstates/trail_fire.json` copies vanilla soul fire's multipart,
pointing at `minecraft:block/soul_fire_floor0/1` and the four side models, so the flame looks
exactly like soul fire (no textures copied). The block joins `#minecraft:fire`
(`data/minecraft/tags/block/fire.json`, `replace: false`): mobs path around it
(`NodeEvaluator`), splash water puts it out (`AbstractThrownPotion`), falling blocks replace
it, and `PortalShape` treats it as empty space, which only matters when someone else lights a
portal there.

## 4. Laying and fading trails (server)

### Pure core (`spell.rules`)

**`TrailTracker`** remembers one observation per player and decides what to light:

- `observe(long pos, boolean grounded) → OptionalLong`: returns the *previous* position when
  a previous observation exists, it was grounded, and `pos` differs from it; otherwise empty.
  Then stores `(pos, grounded)`.
- `reset()`: forgets the observation (level change, effect start).

Consequences: the first tick after the effect starts lights nothing; jumping lights the block
you jumped from (you were grounded there); nothing is lit while airborne; landing on a block
and leaving it lights it; standing still lights nothing. "Grounded" is supplied by the
plumbing as `onGround() && !isPassenger()`, so riding lays no trail.

**`Trail`** is one player's fuse:

- `record Flame(long pos, boolean lingers)`; constants `FADE_TICKS = 20`,
  `LINGER_TICKS = 40`; `fadeTicks(flame)` picks one.
- `add(Flame)`: appends; if the trail was empty, the countdown becomes `fadeTicks(flame)`.
- `tick() → Optional<Flame>`: on an empty trail, nothing. Otherwise the countdown drops by one;
  when it reaches zero the oldest flame is removed and returned, and the countdown restarts
  at `fadeTicks(new oldest)` if any remain.
- `trim(int max) → List<Flame>`: removes and returns the oldest flames until `size() ≤ max`;
  if the oldest changed, the countdown restarts for the new oldest.
- `flames()` (oldest first) and `countdown()` for serialization; a constructor that rebuilds
  from both.

### Plumbing (`io.github.willrieske.spellcraft.trailing`)

**`TrailFires`** is the value of a level attachment (`trailing_fires`, serialized, registered
in `TrailingAttachments`): a map from owner UUID to `Trail`, plus a derived index from flame
position to owner, rebuilt when loaded. Level attachments are re-saved on every world save, so
the object is mutated in place.

- `light(ServerLevel, ServerPlayer owner, BlockPos)`: lights `pos` when its state is air, or
  replaceable with no fluid and not in `#minecraft:fire`, and the block below has a sturdy top
  face. Sets `trail_fire`, records a `Flame` (`lingers` = below is in
  `#minecraft:soul_fire_base_blocks`), then `trim(maxTrailBlocks)` and extinguishes what it
  returns.
- `fadeTick(ServerLevel)`: `tick()` every trail; extinguish each returned flame; drop empty
  trails.
- `extinguish(level, flame)`: removes the block only if it is still `trail_fire` (someone may
  have put it out already), and drops the index entry.
- `ownerOf(BlockPos) → Optional<UUID>`; `isSpared(ServerLevel, BlockPos, Entity)`: true when
  the entity's UUID is the owner, or the owner is online in this level and
  `owner.isAlliedTo(entity)`. An offline owner spares only themself (their pets burn until
  they return: accepted).

**`TrailLayer`** (`@EventBusSubscriber`, game bus):

- `PlayerTickEvent.Post`, server players only: with the effect, fetch or create the player's
  `TrailTracker` (reset when the player's level changed since last tick), `observe`, and
  `light` what it returns. Without the effect, drop the tracker. Trackers live in a static
  map keyed by UUID and are removed on `PlayerLoggedOutEvent`; they are transient by design
  (the next tick after a relog starts a fresh observation).
- `LevelTickEvent.Post`, server levels only: `TrailFires.of(level).fadeTick(level)`.
- `track(ServerPlayer)` and the fade entry point are public so GameTests can drive them
  without waiting for ticks (as `InfinityAbilitySync.sync` does).

### Edge cases

| Case | Behavior |
|---|---|
| Effect ends or is dispelled | No new flames; the existing trail keeps fading at its rate; the owner stays spared from it. |
| Owner logs out | Trail keeps fading (level tick). Allies of an offline owner are not spared. |
| World reload | Flames are blocks and the trails are saved with the level: fading and ownership continue. |
| Flame removed by water, a piston or a player | It stays in the trail until its turn; extinguishing a missing flame is a no-op. |
| Ground removed under a flame | `updateShape` turns it to air; the trail entry is harmless. |
| Standing on a snow layer, carpet or slab | The feet block is not replaceable (or is the slab itself): no flame. Accepted. |
| Walking over your own or another player's flame | The target is in `#fire`: no new flame, no ownership change. |
| Two trailing players share a tile over time | The second to arrive lights nothing while the first flame burns; after it fades, the next departure lights it under the new owner. |
| Dimension change | The tracker resets, so the first position in the new level is not lit. |
| Config `maxTrailBlocks` lowered while a trail is longer | The next `light` trims to the new value. |
| Orphan flame | Burns everyone; removed on its next random tick (about a minute at default random tick speed). |
| `/setblock spellcraft:trail_fire` by hand | An orphan: see above. Useful for testing the look. |

## 5. Error handling

| Risk | Handling |
|---|---|
| Beta API drift | Plan task 1 re-checks every hook used here against `build/mc-src` and `build/nf-src` and records it in `docs/api-notes.md` §18: `BaseFireBlock` hooks and `fireIgnite`, `SoulFireBlock.canSurvive`, `FireBlock.tick` as the TNT path, `BlockTags.FIRE` users, `ChunkSectionLayer.byTransparency`, `Level` as an attachment holder with always-dirty saving, `LevelTickEvent.Post`, `PlayerTickEvent.Post`, `Entity.isAlliedTo` and `TamableAnimal.considersEntityAsAlly`, `Entity.onGround`/`isPassenger`/`blockPosition`, `BlockState.entityInside`/`randomTick` entry points for tests. |
| Corrupt or missing attachment | The codec default is an empty `TrailFires`; flames already in the world become orphans and clean themselves up. Never crash a level load. |
| Config out of range | NeoForge range checks (table in §2). |
| A flame's chunk is unloaded when it should fade | `extinguish` checks the block state through `ServerLevel`, which loads the chunk if needed; trails are short-lived, so this is rare and accepted. |

## 6. Testing

### Unit tests and jqwik properties (pure core, TDD, `./gradlew test`)

**`TrailTracker`:** first observation lights nothing; same position lights nothing; grounded
then moved lights the previous position; airborne then moved lights nothing; grounded, airborne
move, landing elsewhere, leaving lights only the landing block; `reset` forgets. Property: an
observation returns at most the previous position, and only if that previous one was grounded.

**`Trail`:**

| # | Case | Expected |
|---|---|---|
| T1 | `tick` on an empty trail | empty, countdown unchanged |
| T2 | one plain flame | ticks 1–19 return nothing; tick 20 returns it; trail empty |
| T3 | one lingering flame | ticks 1–39 nothing; tick 40 returns it |
| T4 | plain then lingering | tick 20 returns the first; the next 40 ticks return the second on the 40th |
| T5 | add while counting down | the countdown of the oldest is untouched |
| T6 | `trim` below size | returns the oldest, oldest first; countdown restarts for the new oldest |
| T7 | `trim` at or above size | returns nothing, countdown untouched |
| T8 | rebuild from `flames()` + `countdown()` | behaves identically from then on |

Properties: flames come out in the order they went in; the number of ticks to empty a trail
equals the sum of `fadeTicks` over its flames; `size()` never exceeds `max` after `trim`;
the countdown is always in `1 … fadeTicks(oldest)` while non-empty.

**`SpellDeclarationsTest`** gains Trailing (tier `GOOD`, not top). **`SpellAssetsTest`** covers
the new assets by itself.

### GameTests (`./gradlew runGameTestServer`)

Cast rules: survival cast gives 480 s and uses up the scroll; creative casts (not refused);
recast refreshes; sneak dispels; milk cures; category beneficial.

Trail (driven through the public `track`/fade entry points, all synchronous):

| # | Setup | Assert |
|---|---|---|
| G1 | Trailing player grounded at A, moves to B, `track` twice | trail fire at A, none at B; `ownerOf(A)` is the player |
| G2 | Same but not on ground at A | no fire at A |
| G3 | Player riding a pig at A, moves | no fire |
| G4 | A is over soul sand | flame lingers: 39 fade ticks keep it, the 40th removes it |
| G5 | Flames at A then B | 20 fade ticks remove A only; 20 more remove B |
| G6 | `maxTrailBlocks = 2` (config batch), three flames | the first is gone immediately, the other two stand |
| G7 | Zombie, the owner, a teammate and a tamed wolf each run `entityInside` on the flame | zombie ignited and hurt; the others untouched |
| G8 | A second, unallied player runs `entityInside` | ignited and hurt |
| G9 | Flame placed by `setBlock` (no owner), `randomTick` | removed; an owned flame's `randomTick` leaves it and its neighbors (planks, TNT) unchanged |
| G10 | Owned flame, remove the block below | the flame is gone |
| G11 | Effect removed, then the player moves | no flame |
| G12 | `TrailFires` with two owners, codec round trip | equal trails, countdowns and index |
| G13 | Flame at A, player moves back onto A then away | still one flame at A, same owner |

The nether-portal override has no GameTest (a frame does not fit the 3×3×3 structure); it is
a playtest item, and the plan's review checks that `onPlace` does not call portal code.

### Script tests

The existing I1–I6 cover the new glyph (I1 fails if any pixel misses the paper; I5 if the
PNGs are stale).

### Architecture and mutation testing

- ArchUnit rule 2 already covers `spell.rules`, so `TrailTracker` and `Trail` must stay pure.
- `./gradlew pitest` after the pure core lands; a new row in `docs/mutation-testing.md`.

### Playtest checklist additions (Will)

- The scroll icon (light-blue flame on parchment) and the effect icon.
- Walk, sprint, jump: flames appear on blocks you left, never under you, never in the air.
- Stand still: the far end burns back at about one block a second; over soul sand it lingers.
- You walk back through your trail unharmed; a zombie or cow in it burns; a tamed wolf does not.
- Walk through an obsidian portal frame: no portal. Walk past TNT and wooden planks: nothing
  catches. Throw a water bottle at a flame: it goes out.
- Quit to title mid-trail and rejoin: the trail is still there and still fading.
- Real profile: the deployed jar in `.minecraft/mods` loads, and the spell works there.

## 7. Delivery

- `mod_version` → **1.2.0**; README and `neoforge.mods.toml` describe three spells.
- `docs/api-notes.md` §18 (new facts), `docs/mutation-testing.md` (new row),
  `docs/playtest-checklist.md` (new section).
- Local-install test: after the gate and GameTests pass, copy `build/libs/spellcraft-1.2.0.jar`
  into `%APPDATA%\.minecraft\mods` and remove the stale `spellcraft-1.0.0.jar` there. Will
  plays it from the real NeoForge 26.3 profile.
