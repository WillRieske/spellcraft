# Spellcraft: "Strength of Trailing" (design)

**Date:** 2026-11-04
**Status:** implemented 2026-11-04 via `2026-11-04-trailing.md`
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
| **Stale flame** | A flame whose turn to fade came while its chunk was not ready. The ledger keeps its owner and puts it out the next tick its chunk is ready (`getChunkNow` returns it). |
| **Spared** | What a flame does for its owner and the owner's allies: `entityInside` applies no fire and no damage to them (`TrailFires.isSpared`). |
| **Ally** | What vanilla's `Entity.isAlliedTo` says: the same scoreboard team, or a tamed animal whose owner is (allied to) the player. |
| **Fuse** | How a trail behaves: the oldest flame goes out first, the rest wait their turn. |
| **Tracker** | The per-player record of where the player stood last tick and whether they were grounded there (`TrailTracker`). |
| **Grounded** | On the ground, not riding, not a spectator: `onGround() && !isPassenger() && !isSpectator()`. |
| **Ledger** | The per-level record of every trail and which flame belongs to whom (`TrailFires`). |
| **Pure core** | Code with no `net.minecraft`/NeoForge imports, unit-testable with plain JUnit (package `spell.rules`). |
| **Level attachment** | NeoForge data stored on a world (`ServerLevel`) and saved with it. |

## 1. Scope

**In scope:**

- The Trailing declaration, effect, scroll, icons, lang entries and config (§2).
- The trail fire block (§3).
- Laying and fading trails, ownership and the ally rule (§4).
- Unit tests, jqwik properties, GameTests, script tests, docs and playtest items (§6).
- Handing Will the built jar and the commands for the local-install test (§7).

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
  a previous observation exists, `pos` differs from it, and the player was grounded **at any
  point** while on it; otherwise empty. Then records `pos`, and whether the player has been
  grounded on it so far (a repeat observation of the same block ORs the flag; a new block
  starts it fresh).
- `reset()`: forgets the observation (level change, effect start).

Consequences: the first tick after the effect starts lights nothing; jumping lights the block
you jumped from even though you leave the ground a tick before you leave the block; nothing
is lit for blocks only passed in the air; landing on a block and leaving it lights it;
standing still lights nothing. "Grounded" is supplied by the plumbing as
`onGround() && !isPassenger() && !isSpectator()`, so riding and spectating lay no trail.

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
in `TrailingAttachments`): a map from owner UUID to `Trail`, a list of **stale flames** (owner
and position), plus a derived index from flame position to owner (trails and stale flames
alike), rebuilt when loaded. Level attachments are re-saved on every world save, so the object
is mutated in place; an empty ledger (no trails, no stale flames) is not written (every level
reads the attachment each tick, which would otherwise save an empty record per dimension). The
saved form is `trails: [{owner, flames, countdown}]` and `stale: [{owner, pos}]`; `stale` is
optional when read, so a save without it loads as having none. If a save lists one owner
twice, the last non-empty trail wins, only its flames are indexed, and a warning is logged.
A position belongs to whoever lit it last: the index is the truth. A trail may still list a
flame it no longer owns (its flame was put out and another owner relit the spot); that entry
is dead, its turn is a no-op, and the save drops it (`entries()` and the saved stale list keep
only flames the index gives to that owner), so a reload can never hand a spot back to the
older owner.

- `light(ServerLevel, ServerPlayer owner, BlockPos feet)`: the **stand-in cell** is `feet`
  when it can take a flame, else `feet.above()` (a player on soul sand or mud stands 2/16 deep
  inside the block, so `blockPosition()` is the block itself; both report a full support
  shape, so the cell above them qualifies). A cell can take a flame when it is inside the
  world's build limits (`Level.isInValidBounds`; above the top layer `getBlockState` reports
  void air, which would otherwise pass) and its state is air, or replaceable with no fluid and
  not in `#minecraft:fire`, and the block below has a sturdy top face. If neither cell
  qualifies, or `setBlock` reports no change, nothing is lit or recorded. Sets `trail_fire`, records a `Flame`
  (`lingers` = the block below the flame is in `#minecraft:soul_fire_base_blocks`), then
  `trim(maxTrailBlocks)` and extinguishes what it returns. It does not consult spawn
  protection or claim mods (`Level.mayInteract`): a trail burns across protected ground like
  any other effect of walking there (accepted).
- `fadeTick(ServerLevel)`: `tick()` every trail; extinguish each returned flame; drop empty
  trails. Then sweep the stale flames: each whose chunk is **ready** is put out the same way
  and forgotten. Ready means `ServerChunkCache.getChunkNow(x, z)` returns the chunk (a map
  lookup, no load, no wait). `Level.isLoaded` is not enough: it is true as soon as the chunk's
  ticket reaches FULL, while its load may still be running, and a block read would then block
  the server thread until the load finishes.
- `extinguish(level, owner, flame)`: only when the index still says the flame is this owner's
  (if the spot was put out and relit by someone else, that newer flame stays). If the flame's
  chunk is ready, drops the index entry and removes the block if it is still `trail_fire`. If
  it is not, the flame becomes stale instead: it leaves the trail, keeps its index entry (so
  the owner and their allies stay spared if they get there first) and waits for the sweep. No
  chunk is ever loaded, or waited on, to put a flame out.
- `ownerOf(BlockPos) → Optional<UUID>`; `isSpared(ServerLevel, BlockPos, Entity)`: true when
  the entity's UUID is the owner, or the owner is online in this level and
  `owner.isAlliedTo(entity)`. An offline owner spares only themself (their pets burn until
  they return: accepted). An entity with no team that is neither an `OwnableEntity` nor an
  allay (vanilla allies an allay to the player it likes, `Allay.considersEntityAsAlly`) cannot
  be allied to anyone, so the owner lookup (a scan of the level's players) is skipped for it;
  this runs every tick for every entity standing in a flame. A modded entity with its own
  ally rule for players would be missed by this fast path (documented in the Javadoc).
- `forget(UUID owner)`: drops the owner's trail, their stale flames and their index entries
  without touching any block. GameTest support only (each test cleans its own ledger entries).

**`TrailLayer`** (`@EventBusSubscriber`, game bus):

- `PlayerTickEvent.Post`, server players only: with the effect, fetch or create the player's
  `TrailTracker` (reset when the player's level changed since last tick), `observe`, and
  `light` what it returns if that block's chunk is ready (`TrailFires.isReady`, the same
  `getChunkNow` test the ledger uses; after a long teleport it may not be; no chunk is loaded
  or waited on to lay a flame). Without the effect, drop the tracker. Trackers
  live in a static map keyed by UUID, removed on `PlayerLoggedOutEvent` and cleared on
  `ServerStoppedEvent`; they are transient by design (the next tick after a relog starts a
  fresh observation).
- `LevelTickEvent.Post`, server levels only: `TrailFires.of(level).fadeTick(level)`.
- `track(ServerPlayer)` and the fade entry point are public so GameTests can drive them
  without waiting for ticks (as `InfinityAbilitySync.sync` does).

### Edge cases

| Case | Behavior |
|---|---|
| Effect ends or is dispelled | No new flames; the existing trail keeps fading at its rate; the owner stays spared from it. |
| Owner logs out | Trail keeps fading (level tick). Allies of an offline owner are not spared. |
| World reload | Flames are blocks and the trails are saved with the level: fading and ownership continue. |
| Flame removed by water, a piston or a player | It stays in the trail until its turn; extinguishing a missing flame is a no-op. If another player relights the spot first, the new flame is theirs and the old entry's turn leaves it alone. If the **same** owner relights it, their trail lists the spot twice and the older entry puts the new flame out early: accepted (rare, and the flame only fades sooner). |
| Ground removed under a flame | `updateShape` turns it to air; the trail entry is harmless. |
| Standing on a snow layer | The snow block is the feet block and is replaceable: the flame replaces the snow. |
| Standing on soul sand or mud | The player stands 2/16 inside the block, so the feet block is the soul sand or mud itself. Both report a full support shape, so the stand-in cell above it takes the flame; over soul sand it lingers. |
| Standing on farmland or a dirt path | The feet block is the farmland or path itself, and its top is not a full sturdy face, so neither it nor the cell above qualifies: no flame, as vanilla fire cannot be lit on them either. Accepted. |
| Standing on carpet or a slab | The feet block is the carpet or the slab itself, neither replaceable, and a carpet or bottom slab has no sturdy top: no flame. Accepted. A top slab's surface is a full face, so standing on one lights the cell above it like any floor. |
| Walking over your own or another player's flame | The target is in `#fire`: no new flame, no ownership change. |
| Two trailing players share a tile over time | The second to arrive lights nothing while the first flame burns; after it fades, the next departure lights it under the new owner. |
| Dimension change | The tracker resets, so the first position in the new level is not lit. |
| Spectator mode | Never grounded, so no flames. |
| The block just left is in a chunk that is not ready (long teleport) | Not lit; nothing is loaded or waited on to lay a flame. |
| Config `maxTrailBlocks` lowered while a trail is longer | The next `light` trims to the new value. |
| A flame's turn to fade comes while its chunk is not ready | It becomes a stale flame: out of the trail, still owned, put out the next tick its chunk is ready. The trail's fuse keeps burning meanwhile. |
| A reload after a trail's oldest flame went dead (relit by another owner) | The save drops the dead entry, so the countdown saved for it now applies to the next flame (clamped to that flame's delay): the trail may fade one turn early. Accepted. |
| Orphan flame | Burns everyone; removed on its next random tick (about a minute on average at the default `randomTickSpeed`; with the gamerule at 0 it stays until broken by hand or water). Orphans come only from `/setblock`, world-editing tools or a lost ledger. |
| `/setblock spellcraft:trail_fire` by hand | An orphan: see above. Useful for testing the look. |

## 5. Error handling

| Risk | Handling |
|---|---|
| Beta API drift | Plan task 1 re-checks every hook used here against `build/mc-src` and `build/nf-src` and records it in `docs/api-notes.md` §18: `BaseFireBlock` hooks and `fireIgnite`, `SoulFireBlock.canSurvive`, `FireBlock.tick` as the TNT path, `BlockTags.FIRE` users, `ChunkSectionLayer.byTransparency`, `Level` as an attachment holder with always-dirty saving and the serialize predicate, `LevelTickEvent.Post`, `PlayerTickEvent.Post`, `ServerStoppedEvent`, `Entity.isAlliedTo`, `TamableAnimal.considersEntityAsAlly` and `getTeam`, `Entity.onGround`/`isPassenger`/`isSpectator`/`blockPosition`, `Level.isLoaded` versus `ServerChunkCache.getChunkNow` (ready, not merely ticketed), NeoForge's `Entity.setPosRaw` chunk load, `Allay.considersEntityAsAlly`, `AttachmentHolder.serializeAttachments`, `BlockState.entityInside`/`randomTick` entry points for tests. |
| Corrupt or missing attachment | The codec default is an empty `TrailFires`; flames already in the world become orphans and clean themselves up. Never crash a level load. |
| Config out of range | NeoForge range checks (table in §2). |
| A flame's chunk is not ready when it should fade | The flame becomes stale (§4): it keeps its owner in the index and is put out by the sweep the next tick its chunk is ready (`getChunkNow`). Nothing is force-loaded or waited on: the fuse burns from the far end, which after half a minute of running sits in chunks nobody is in, and an offline owner's whole trail fades out of sight, so synchronous loads were not rare. Decided against dropping the ledger entry: that would leave an ownerless flame that burns everyone, the owner included, until a random tick. |
| Corrupt save lists an owner twice | The last non-empty trail wins; a warning names the owner so an operator can explain orphan flames in the log. |

## 6. Testing

### Unit tests and jqwik properties (pure core, TDD, `./gradlew test`)

**`TrailTracker`:** first observation lights nothing; same position lights nothing; grounded
then moved lights the previous position; airborne then moved lights nothing; grounded, then
airborne on the same block, then moved lights that block; grounded, airborne move, landing
elsewhere, leaving lights only the landing block; `reset` forgets. Properties: an observation
returns the previous position exactly when the position changed and the player was grounded
at some point on the previous block; a reset tracker answers like a new one.

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

Also: the rebuild constructor clamps a saved countdown into `1 … fadeTicks(oldest)` and gives
an empty trail countdown 0; adding after the trail emptied starts a fresh countdown.

Properties: flames come out in the order they went in; the number of ticks to empty a trail
equals the sum of `fadeTicks` over its flames; `size()` never exceeds `max` after `trim`;
the countdown is always in `1 … fadeTicks(oldest)` while non-empty, under ticks alone and under
any mix of add, tick and trim.

**`SpellDeclarationsTest`** gains Trailing (tier `GOOD`, not top). **`SpellAssetsTest`** covers
the new assets by itself. New unit and property test classes follow the methodology's *Unit
test style*; existing test files keep their style.

### GameTests (`./gradlew runGameTestServer`)

Cast rules: survival cast gives 480 s and uses up the scroll; creative casts (not refused);
recast refreshes; sneak dispels; sneak while inactive casts; milk cures; category beneficial.
The scribing zero-percent test now accepts either lower-tier scroll (Upside-Down or Trailing).

Trail (driven through the public `track`/fade entry points, all synchronous):

| # | Setup | Assert |
|---|---|---|
| G1 | Trailing player grounded at A, moves to B, `track` twice | trail fire at A, none at B; `ownerOf(A)` is the player |
| G2 | Same but not on ground at A | no fire at A |
| G3 | Player riding a pig at A, moves | no fire |
| G4 | A is over soul sand | flame lingers: 39 fade ticks keep it, the 40th removes it |
| G5 | Flames at A then B | 20 fade ticks remove A only; 20 more remove B |
| G6 | `maxTrailBlocks = 2` (config batch), three flames | the first is gone immediately, the other two stand |
| G7 | The owner (on a team), a zombie on the same team and a zombie on no team each run `entityInside` on the flame | the stranger zombie ignited and hurt; the owner and teammate untouched |
| G7b | A tamed wolf of an owner with no team runs `entityInside` | untouched (the tame path alone spares it) |
| G7c | An allay that likes the owner (no teams) runs `entityInside` | untouched (vanilla `Allay.considersEntityAsAlly`; guards the `isSpared` fast path) |
| G8 | A second, unallied player runs `entityInside` | ignited and hurt |
| G9 | Flame placed by `setBlock` (no owner), `randomTick` | removed; an owned flame's `randomTick` leaves it standing (its neighbors are G25's check) |
| G10 | Owned flame, remove the block below; a flame set where nothing supports it | the flame is gone; the unsupported flame removes itself at once (`onPlace`) |
| G11 | Effect removed, then the player moves; effect re-added, two more moves | no flame at the old spots; the first new departure after re-adding lights nothing, the next lights its block |
| G12 | `TrailFires` with two owners and two stale flames, codec round trip; then a spot that X's trail lists but Y lit last (the index says Y), and a stale flame of X at a spot Y's trail owns | equal trails, countdowns, stale flames (in order) and index; a save without `stale` loads with none; the contested spots are saved only under Y, so the reloaded index still says Y |
| G13 | Flames at A and B, player walks A→B→A→B | A and B each lit once, same owner, two flames in the trail |
| G14 | X's flame at A put out by hand, Y relights A, X's turn comes | A still burns and is Y's; it fades on Y's turn |
| G15 | Lighting over grass, over an own flame, on a fence top, on vanilla soul fire, in water | only the grass spot is lit; the owner's trail holds one flame |
| G16 | `TrailFires` rebuilt from two entries with the same owner | only the last trail is kept; the dropped trail's spots have no owner in the index |
| G17 | Trailing player grounded on real soul sand (feet 2/16 inside it), moves to stone, `track` twice | a lingering flame in the cell above the sand (39 fade ticks keep it, the 40th removes it); none on the sand block itself |
| G18 | Owner's trail rebuilt with one flame at a far, unloaded position, countdown 1; one `fadeTick` | the flame is stale: out of the trail, `ownerOf` still the owner, `level.isLoaded` there still false (no chunk was loaded) |
| G19 | A stale flame at a lit, loaded spot (ledger rebuilt with it in `stale`); one `fadeTick`. Then a stale flame of X at a spot whose index owner is Y (Y's live flame there); one `fadeTick` | the block is removed, `ownerOf` is empty, the ledger is empty; Y's flame still stands and is Y's, X's stale entry is gone |
| G20 | Trailing player grounded at A; `onLogout` with a `PlayerLoggedOutEvent` for them; they stand at B, then C | A stays dark (the tracker was dropped); B lights |
| G21 | Same with `onServerStopped` for the test server | A stays dark; B lights |
| G22 | Trailing player grounded at a far, unloaded position X, then moves to A (the player is a discarded mock: NeoForge's `Entity.setPosRaw` loads the chunk any in-level entity moves into, so a live one cannot stand in an unloaded chunk) | X is not lit and `level.isLoaded(X)` is still false; `ownerOf(X)` is empty |
| G23 | Owner on a team lights A and is discarded (offline); a zombie on the same team runs `entityInside` | the zombie burns (allies of an offline owner are not spared) |
| G24 | Owner lights A, the effect is removed, the owner runs `entityInside` on A | untouched (the owner stays spared after the effect ends) |
| G25 | Owned flame beside oak planks and TNT; the flame's `tick` and `randomTick` run, then a few level ticks pass | planks and TNT unchanged, no fire anywhere else (no spread, nothing scheduled) |
| G26 | Trailing player grounded on a carpet (over stone), then on a top slab, each left for stone | no flame for the carpet spot; the cell above the slab lights |
| G27 | `level.serializeAttachments(...)` with an empty ledger, then with one flame | the trailing_fires key is absent, then present and decodes to the same ledger |
| G28 | Stone under A's coordinates in the nether as well; trailing player grounded at A in the overworld; the same player (same UUID) in the nether stands at B then C | the nether cell at A's coordinates stays dark and unowned (without the reset the first nether observation would light it), overworld A stays dark, and the player's nether trail holds exactly B |

The cast rules also cover: sneaking with another item in hand keeps the effect, and casting
takes one scroll from a stack. The scribing check asserts the lower-tier candidate list holds
the Trailing scroll directly (a random draw cannot prove both scrolls are reachable).

The nether-portal override has no GameTest (a frame does not fit the 3×3×3 structure); it is
a playtest item, and the plan's review checks that `onPlace` does not call portal code. The
spectator exclusion is a playtest item too.

### Script tests

The existing I1–I6 cover the new glyph (I1 fails if any pixel misses the paper; I5 if the
PNGs are stale).

### Architecture and mutation testing

- ArchUnit rule 2 already covers `spell.rules`, so `TrailTracker` and `Trail` must stay pure.
- `./gradlew pitest` after the pure core lands; a new row in `docs/mutation-testing.md`.

### Playtest checklist additions (Will)

- The scroll icon (light-blue flame on parchment) and the effect icon.
- Walk, sprint, jump: flames appear on blocks you left (the take-off block of a jump included),
  never under you, never in the air.
- Stand still: the far end burns back at about one block a second; over soul sand it lingers.
- Snow layers are replaced by the flame; carpet and slab tops get none.
- You walk back through your trail unharmed; a zombie or cow in it burns; a tamed wolf does not.
- Walk through an obsidian portal frame: no portal. Walk past TNT and wooden planks: nothing
  catches. Throw a water bottle at a flame: it goes out.
- Riding, creative flight and spectator mode lay no flames.
- Through a nether portal: nothing on arrival, and nothing on the portal block itself (it is not replaceable, so `canLight` refuses it); the first ordinary block you step off in the new dimension lights.
- Quit to title mid-trail and rejoin: the trail is still there and still fading.
- Real profile: the jar Will copied into `.minecraft/mods` loads, and the spell works there.

## 7. Delivery

- `mod_version` → **1.2.0** (Will's explicit request); README and `neoforge.mods.toml` describe
  three spells.
- `docs/api-notes.md` §18 (new facts), `docs/mutation-testing.md` (new row),
  `docs/playtest-checklist.md` (new section).
- Local-install test: after the gate and GameTests pass, the agent reports the built
  `build/libs/spellcraft-1.2.0.jar` and gives Will the PowerShell commands to copy it into
  `%APPDATA%\.minecraft\mods` and remove the stale `spellcraft-1.0.0.jar` there (only that
  jar). Will runs them from a normal PowerShell with Minecraft closed: the Claude desktop app
  is MSIX-packaged, and files its shells write under `%APPDATA%` land in a private copy the
  real launcher never sees. Will then plays from the real NeoForge 26.3 profile.
