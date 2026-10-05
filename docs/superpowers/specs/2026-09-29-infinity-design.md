# Spellcraft: "Strength of Infinity" and the shared spell framework (design)

**Date:** 2026-09-29
**Status:** Implemented (plan: docs/superpowers/plans/2026-09-29-infinity.md); playtest pending
**Target:** Minecraft Java 26.3, NeoForge 26.3.0.23-beta, Java 25, ModDevGradle
**Builds on:** [`2026-09-28-upside-down-design.md`](2026-09-28-upside-down-design.md). Where they
disagree, this spec wins (§9 lists what it supersedes).

## Summary

**Strength of Infinity** gives a survival (or adventure) player creative mode minus
immortality. You get flight, the creative inventory and creative building, but you still take
damage, get hungry and can die. You get it by using an *Infinity scroll*. It has no time limit,
and nothing in the game removes it except death or `/effect clear`.

This spec also turns the one-spell framework into a small set of shared business rules. Every
scroll is now single-use and vanishes in a puff of dark smoke. Scrolls are crafted by
"scribing" paper at an enchanting table; the result is a random spell, and paying more lapis
raises the odds of a top-tier spell. Adding a later spell means a declaration, an effect, a
glyph and lang entries.

## Glossary

| Term | Meaning |
|---|---|
| **Ability flags** | Vanilla's per-player `Abilities`: `mayfly` (may fly), `instabuild` (creative inventory, instant break, items not used up), `invulnerable`. Game modes are presets of these; vanilla syncs them to the client. |
| **Pure core** | Code with no `net.minecraft`/NeoForge imports, unit-testable with plain JUnit (package `spell.rules`, plus `client.upsidedown` from the first spec). |
| **Scribing** | Turning paper into a random scroll at an enchanting table. |
| **Tier** | A spell's power band: `NOOB`, `STARTER`, `GOOD`, `AMAZING`, `EXTREME`. *Top tier* means `EXTREME`. |
| **Death stash** | A saved per-player data attachment holding the items an Infinity player keeps through death. |
| **Granted mark** | A saved per-player data attachment recording that Infinity (not vanilla or another mod) turned the player's ability flags on. |

## 1. Scope

**In scope:**

- The shared spell framework (§2) and its application to Upside-Down (single-use scrolls,
  cooldown removed).
- The Infinity effect, scroll, abilities (§3) and death rule (§4).
- Scribing at the enchanting table, with its client tooltip (§5).
- A shared icon generator for all spell icons (§6).
- Unit tests, GameTests, script tests and playtest-checklist items (§8).

**Out of scope (sub-project 2, its own spec later):**

- The **knowledge book** on the `B` key (recipes, command secrets, tips). It will depend on the
  Infinity effect built here.

**Out of scope (for now):**

- Mana or other casting costs, spell registries beyond a static list, publishing.
- Any change to damage, hunger or `invulnerable`.

## 2. Spell framework (shared business rules)

### Declaration

`Spell` (common, Minecraft-facing) holds `id`, `effect`, and a pure `SpellRules`:

| `SpellRules` field | Upside-Down | Infinity |
|---|---|---|
| `tier` (`SpellTier`) | `STARTER` | `EXTREME` |
| `duration` | finite, from config (default 180 s) | infinite (`MobEffectInstance.INFINITE_DURATION`) |
| `recast` (while active) | `REFRESH` | `REFUSE` |
| `dispellable` (sneak + use while active) | yes | no |
| `redundantInCreative` | no | yes |

Cures live on the effect, not the declaration: `MarkerEffect` keeps vanilla cures (milk,
totem); `InfinityEffect` declares none (§3).

`Spells` holds the static list `ALL = [UPSIDE_DOWN, INFINITY]`. `ModItems` registers one
`SpellScrollItem` per entry, stack size **16**, listed in the creative tab.

### Decision: `CastRules.decide(rules, caster) → CastOutcome`

`caster` is `(sneaking, effectActive, mode)`, with `mode` one of `SURVIVAL`, `ADVENTURE`,
`CREATIVE`, `SPECTATOR` (pure enum; spectators can't use items, but the rule is total).
The rules apply in this order; the first match wins:

1. `mode == CREATIVE && redundantInCreative` → **REFUSE**
2. `effectActive && sneaking && dispellable` → **DISPEL**
3. `effectActive && recast == REFUSE` → **REFUSE**
4. `effectActive` → **REFRESH**
5. otherwise → **CAST**

`CastOutcome` is a sealed type, and `SpellScrollItem` switches over it exhaustively:

| Outcome | Effect | Scroll | Feedback |
|---|---|---|---|
| CAST | apply for `duration` | used up | smoke + fizzle |
| REFRESH | re-apply for the full `duration` (timer resets, no remove/add gap) | used up | smoke + fizzle |
| DISPEL | remove | used up | smoke + fizzle |
| REFUSE | none | kept | action bar: *"Scroll not needed; already have effect!"* |

- **"Used up" means `stack.shrink(1)`**, not `ItemStack.consume`. `consume` skips players with
  `instabuild`, and the scroll must be used up in creative and during Infinity too.
- **Smoke + fizzle:** server-side `sendParticles` of dark smoke at the player's hand and a
  short extinguish-style sound. The exact particle and sound are picked at playtest.
- **The client runs the same decision** (it knows its effects and game mode through vanilla
  sync). REFUSE returns `InteractionResult.FAIL` on both sides, so a refused use shows no
  swing. The server alone changes state.
- Effects are applied with particles hidden and the icon shown, as in the first spec.

## 3. The Infinity effect and abilities

### Effect

`ModEffects.INFINITY`: `InfinityEffect`, "Strength of Infinity", category *beneficial*, gold.
It overrides the NeoForge cure hook to declare **no cures**, so milk and totems leave it. Only
**death** and **`/effect clear`** remove it. With infinite duration, the HUD shows "∞".

### Abilities: `InfinityAbilities.decide(mode, hasInfinity, marked, mayfly, instabuild) → action`

A pure function; `invulnerable` is never an input or an output.

| Situation | Action |
|---|---|
| `hasInfinity`, mode `SURVIVAL`/`ADVENTURE`, and not (`marked && mayfly && instabuild`) | **GRANT**: set `mayfly` and `instabuild`, set the mark |
| `!hasInfinity && marked` | **REVOKE**: reset the flags to the current game mode's defaults, stop flying if that mode can't fly, clear the mark |
| `hasInfinity`, mode `CREATIVE`/`SPECTATOR`, `marked` | **RELEASE**: clear the mark only (vanilla owns the flags in those modes) |
| anything else | **NONE** |

A server-side `PlayerTickEvent.Post` handler calls `decide` for each player, applies the
action, and calls `onUpdateAbilities()` only when the action is not NONE. Vanilla's abilities
packet then syncs the client, whose `InventoryScreen` switches to the creative inventory by
itself (`hasInfiniteMaterials()`), and whose creative-slot packets the server now accepts.

Consequences:

- **Game mode changes:** vanilla rewrites the flags for the new mode. Back in
  survival/adventure, the next tick grants again. Mid-air flight stops at the switch;
  double-tap Space to resume.
- **`/effect clear` in mid-air:** REVOKE stops flight and you fall.
- **Another mod's flags** are never touched unless the mark is set. Known limit: a mod that sets
  `mayfly` *while* Infinity is active loses it on REVOKE.
- **Unchanged from survival:** hunger, damage, fall damage while not flying, mobs.
- **Creative building comes whole** (§1 decision A): instant break, broken blocks drop nothing,
  placed blocks and arrows not used up. This is vanilla's `instabuild` behavior.

## 4. Dying with Infinity

**Rule:** no items drop. Armor is deleted. Hotbar, main inventory and offhand are restored on
respawn in the same slots. XP follows vanilla (orbs drop, respawn at 0). Infinity is gone
after respawn.

### Pure core: `DeathRule.sort(slots, keepInventory) → {delete, stash}`

- Hotbar (0–8), main (9–35) and offhand → **stash**, same position.
- Armor (head, chest, legs, feet) → **delete**.
- Any item with Curse of Vanishing → **delete** (vanilla parity).
- Empty slots → skipped.
- `keepInventory` on → the rule doesn't run (empty result).

### Plumbing (server)

1. **`LivingDeathEvent`**, registered at `EventPriority.LOWEST` and not receiving cancelled
   events, so every mod that might cancel the death has already run. For a player with
   Infinity and `keepInventory` off: delete, stash, then empty the inventory. Vanilla's drop
   step finds nothing to drop; XP is untouched.
2. **`PlayerEvent.Clone`** with `wasDeath`: copy the stash into the new player's slots, then
   clear it.
3. The **death stash** and the **granted mark** are data attachments with `copyOnDeath`, saved
   with the player. After respawn the new player has no Infinity but still carries the mark, so
   the first tick REVOKEs the abilities cleanly.

### Edge cases

| Case | Behavior |
|---|---|
| Logout on the death screen, or a server crash before respawn | The stash is saved; the items restore on the next respawn. |
| `keepInventory` on | Vanilla: everything kept, armor too. The stash stays empty. |
| Totem of Undying | Not a death: the totem saves you, Infinity stays, inventory untouched. |
| Grave or other death-drop mods | They see an empty vanilla inventory; items they add from other sources are theirs. |
| The stash can't restore (e.g. corrupt data) | Log a warning with the player and slot; drop nothing; never crash the respawn. |

## 5. Scribing at the enchanting table

### Rule

Put paper (item tag `#spellcraft:scribable`, containing `minecraft:paper`) in the item slot
and lapis in the lapis slot. The three options cost **1/2/3 lapis and 1/2/3 levels**
(bookshelves don't matter). Clicking option *n* takes the cost and turns the paper into one
scroll:

1. With chance `topTierChancePercent[n]` (defaults **25 / 50 / 75**), draw from the
   **EXTREME** pool; otherwise from **all other tiers**.
2. Pick evenly within the pool. If that pool is empty, use the other.

Players with `instabuild` scribe for free (vanilla's enchanting waiver). The item slot holds
one item (vanilla), so each click scribes one paper. Scribing is not enchanting: no "Enchanter"
advancement.

### Pure core: `ScrollDraw.draw(option, spells, chances, random) → spell`

`random` is an injected `RandomGenerator`, so tests are deterministic. The roll happens at
click time from the level's random source, not the enchant seed, so reopening the table can't
preview the result. An empty `spells` list is rejected at startup with a clear message.

### Common Mixin: `EnchantmentMenuMixin` (`io.github.willrieske.spellcraft.mixin`)

Both injections act only when the item slot holds a scribable item; every other item takes the
untouched vanilla path. `require = 1` on each.

- **`slotsChanged`** (server, via the menu's level access): set `costs = {1, 2, 3}`, put
  `topTierChancePercent[n]` into `levelClue[n]` and `0` into `enchantClue[n]` (any
  non-negative value; it only stops NeoForge greying the option out), broadcast, and skip vanilla's logic. The clue data slots are synced, so
  the client learns the odds without its own config.
- **`clickMenuButton`:** vanilla's own checks run unchanged (button range, lapis count,
  levels, creative waiver). Only the server's "apply" step is replaced by scribing: take *n*
  lapis, `onEnchantmentPerformed(stack, n)` (levels and seed reroll), `ScrollDraw`, swap the
  paper for the scroll, award `Stats.ENCHANT_ITEM`, play the enchanting sound, re-run
  `slotsChanged`.

This is the mod's first common Mixin; `spellcraft.mixins.json` gains a common `mixins` list
beside the existing `client` list.

### Client Mixin: `EnchantmentScreenMixin` (`client.mixin`)

While a scribable item is in the slot:

- Render the three options as **enabled** (bypassing NeoForge's "no clue means disabled"
  check), with vanilla's random enchanting-script text and cost numbers.
- **Replace the tooltip** with: *"Scribe a random scroll"*, *"N% chance of an extreme-tier
  spell"* (N from `levelClue`), then vanilla's lapis and level cost lines (red when short).

### Config (server)

`scribing.topTierChancePercent`: a list of exactly 3 integers, each 0–100.

## 6. Icons: shared generator

`scripts/spell_icons.py` replaces `make_scroll_icon.py` and `make_effect_icon.py`. It is
stdlib only, like its predecessors.

- **Shared:** the PNG writer, the diagonal parchment sheet (the current `SHEET`/`PALETTE`) and
  a table of spells, each with one **glyph** (ASCII art) and an ink color.
- **Effect icon (18×18):** the glyph at 2× scale, centered.
- **Scroll icon (16×16):** the glyph at 1× on the sheet.
- `python scripts/spell_icons.py` regenerates every icon into `src/main/resources`.
- **Upside-Down:** its glyph is the current scroll arrow. Its effect icon is re-rendered from
  it at 2× and will look a little chunkier than today's (playtest item).
- **Infinity:** a gold ∞ of about 7×4 px.
- Remove the committed `scripts/__pycache__/` and add `__pycache__/` to `.gitignore`.

## 7. Error handling

| Risk | Handling |
|---|---|
| A Minecraft or NeoForge update moves a Mixin target | `require = 1` on every injection: loud failure at startup. |
| Beta API drift | Plan task 1 checks every hook against `build/mc-src` and `build/nf-src` and records it in `docs/api-notes.md`: the effect-cure hook, `LivingDeathEvent` order against drops, `PlayerEvent.Clone`, data attachments with `copyOnDeath`, `PlayerTickEvent`, infinite duration, both Mixin targets, whether a mock player can go through `PlayerList.respawn` headlessly, and whether a GameTest can drive `handleSetCreativeModeSlot` for a mock player (if not, that test moves to the playtest). |
| Config out of range | NeoForge range checks: chances 0–100 (exactly 3), Upside-Down duration ≥ 1 s. The removed `cooldownSeconds` is dropped by NeoForge's config correction, which logs it. |
| Ability desync | The client never decides abilities; the server resends only on change through vanilla's packet. |
| Stash restore failure | See §4 edge cases. |

## 8. Testing

### Unit tests and jqwik properties (pure core, TDD, `./gradlew test`)

- **`CastRules`:** the full matrix of sneaking × effect active × {survival, adventure,
  creative, spectator} × both spells, each case listed in the plan. Properties: only REFUSE
  keeps the scroll; Infinity never gets DISPEL or REFRESH; a `REFRESH` spell never refuses an
  active recast outside creative-redundancy.
- **`InfinityAbilities`:** every mode × `hasInfinity` × mark × flag combination gives the
  table's action. Property: `invulnerable` is never touched.
- **`DeathRule`:**

  | # | Case | Expected |
  |---|---|---|
  | U1 | Each hotbar slot 0–8 | stash, same index |
  | U2 | Each main slot 9–35 | stash, same index |
  | U3 | Offhand | stash |
  | U4 | Each armor slot | delete |
  | U5 | Vanishing item in hotbar, main, offhand or armor | delete |
  | U6 | Empty slot | skipped |
  | U7 | `keepInventory` on | empty result |
  | U8 | Every slot filled | 37 stashed, 4 deleted, 0 lost |

  Properties: stashed + deleted = every non-empty slot, disjoint; stash then restore gives back
  the original non-armor, non-Vanishing slots in place; no armor slot is ever stashed.
- **`ScrollDraw`:** a roll just below or above the threshold picks the top or lower pool; 0%
  never gives EXTREME, 100% always does; empty-pool fallback; the result is always a declared
  spell; even picks within a pool (chi-square over 10,000 draws, fixed seed).
- **`SpellAssetsTest`:** for every declared spell, the item texture (16×16), effect texture
  (18×18), item model, item definition and lang keys exist.

### GameTests (`./gradlew runGameTestServer`)

**Upside-Down (rewrites the current set):** cast uses up the scroll and gives 180 s; recast
while active refreshes and uses up a scroll; sneak-dispel removes it and uses up a scroll;
sneak while inactive casts; milk cures it; casting in creative uses up the scroll; casting
while Infinity is active uses up the scroll; the config duration is honored.

**Infinity:**

- Casting in survival and in adventure: infinite duration, scroll used up, `mayfly` and
  `instabuild` on, `invulnerable` off.
- In creative: refused, scroll kept. While active: refused, scroll kept, sneak + use too.
- Milk and a totem don't cure it; `/effect clear` does and revokes the abilities.
- Survival → creative → survival: abilities regained.
- A flag another mod set, with no mark, is left alone.
- A creative-slot packet is accepted from an Infinity player and rejected after removal.

**Death:**

| # | Setup | Assert |
|---|---|---|
| G1 | Infinity; items in hotbar, main, offhand, armor; lethal damage | No item entities; stash holds hotbar, main, offhand; armor gone |
| G2 | G1, then respawn | Same items in the same slots; armor empty; stash cleared |
| G3 | G1 with XP level 10 | Vanilla XP orbs; new player at 0 XP |
| G4 | Infinity, death, respawn, 1 tick | No Infinity; `mayfly` and `instabuild` off; mark cleared |
| G5 | Vanishing item in the hotbar | Not stashed, not dropped, not restored |
| G6 | `keepInventory` on | Vanilla: all kept including armor; stash empty |
| G7 | No Infinity | Vanilla drops; stash empty |
| G8 | Infinity, totem in offhand, lethal damage | Survives; Infinity kept; inventory untouched; totem used |
| G9 | Stash filled, player data saved and reloaded before respawn | Stash survives and restores |
| G10 | Death in the Nether, respawn in the Overworld | Same as G2 |
| G11 | Death while flying | Same as G1; respawned player not flying |

If a mock player can't go through `PlayerList.respawn` headlessly (plan task 1), G2 and G10
fire `Clone` directly and the full respawn moves to the playtest.

**Scribing (real menu at a real enchanting table):** each option turns paper into a scroll and
takes *n* lapis and *n* levels; too few lapis or levels is refused with nothing changed;
`instabuild` scribes free; chances forced to 0% / 100% give only Upside-Down / only Infinity;
a book still enchants as vanilla; a second click with the scroll in the slot does nothing.

### Script tests (`python -m unittest scripts/test_spell_icons.py`)

| # | Test |
|---|---|
| I1 | Every glyph pixel lands on a sheet fill pixel (not rim, shadow or transparent). |
| I2 | Glyphs are rectangular, use only `#` and `.`, and at 2× fit 18×18 with a 1 px margin. |
| I3 | Output decodes with stdlib: PNG signature, IHDR size (16 or 18), RGBA, valid CRCs. |
| I4 | Two runs produce identical bytes. |
| I5 | Each committed PNG matches the generator's current output. |
| I6 | Every spell in the table has both outputs, and every generated PNG has a spell. |

Run by a local pre-commit hook when `scripts/` or `textures/` change, and in CI.

### Architecture and mutation testing

- ArchUnit rule 2 (purity) extends to `io.github.willrieske.spellcraft.spell.rules`
  (`CastRules`, `CastOutcome`, `SpellRules`, `SpellTier`, `ScrollDraw`, `InfinityAbilities`,
  `DeathRule`).
- New ArchUnit rule: the common Mixin package references no client classes.
- PIT's scope adds `spell.rules`; results go in `docs/mutation-testing.md`.

### Playtest checklist additions (Will, `./gradlew runClient`)

- Infinity in survival: flight feel, `E` opens the creative inventory, instant break, items
  taken from the creative inventory stick after relog, the ∞ in the HUD.
- Every scroll use: smoke puff and fizzle; refusal message wording; no swing on refusal.
- Scribing: options look enabled; tooltip text and percentages; result lands in the slot.
- Icons: both scroll icons, the Infinity effect icon, the re-rendered Upside-Down effect icon.
- Death with Infinity: no drops, armor gone, rest restored; quit on the death screen and
  rejoin; after respawn no flight and a survival inventory.

## 9. Changes to the Upside-Down spec

This spec supersedes these parts of `2026-09-28-upside-down-design.md`:

- §1 "Crafting recipes (creative tab only)": scrolls are now scribed (§5).
- §2 `Spell` record and `SpellScrollItem` behavior: replaced by §2 here. Scrolls are
  single-use, stack to 16, and dispel uses up a scroll.
- §2 server config `upsideDown.cooldownSeconds`: removed.
- §5 GameTests "Recast resets to 180 s" and the cooldown tests: replaced by §8 here.

The Upside-Down spec gets a note at the top pointing here. `CLAUDE.md` gains the icon
generator command and its test command.
