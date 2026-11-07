# Spellcraft: "Strength of Evoking" (design)

**Date:** 2026-11-06
**Status:** implemented 2026-11-06 via `2026-11-06-evoking.md`
**Written by:** `/one-shot`, unattended, on 2026-11-06; §10 lists the decisions Will did not state.
**Target:** Minecraft Java 26.3, NeoForge 26.3.0.23-beta, Java 25, ModDevGradle
**Builds on:** [`2026-09-29-infinity-design.md`](2026-09-29-infinity-design.md) (the shared spell
framework) and [`2026-11-04-trailing-design.md`](2026-11-04-trailing-design.md) (allies). §3 of this
spec changes the Trailing spec's ally rule; nothing else there is superseded.
**Amends:** the Trailing spec's glossary row **Ally** and its §4 description of `isSpared` (both
now defer to §3 here); `CLAUDE.md`'s "No custom packets" rule (§4).

## Summary

**Strength of Evoking** gives you an evoker's two spells for as long as you want them. With the
effect on, **F** raises your arms and, one second later, carpets a 13×13 square in front of you
with evoker fangs that bite everything in it except you and your allies. **V** raises your arms
and, one second later, calls three vexes to your side; they ignore you and your allies and
attack anything else that comes within five blocks of them. While you cast, your arms go up
like an evoker's and spell particles swirl at your hands, visible to everyone. The timings are
the evoker's own: fangs take one second to appear and keep your arms up for two; vexes take one
second and keep your arms up for five; fangs can be cast every five seconds and vexes every
seventeen.

The spell has no time limit. A scroll turns it on, sneak + a scroll (or milk) turns it off, and
a second scroll while it is on is refused like Infinity's.

Adding the spell follows the framework recipe (a declaration, an effect, a glyph, lang
entries), plus three new things: a **key press that reaches the server** (the mod's first and
only custom packet), a **shared ally rule** that every spell now uses (the mod's own summons
count as allies, which is the edit to Trailing Will asked for), and a **casting state** that
the server owns and every client renders (arms and particles) from vanilla-synced data.

## Glossary

| Term | Meaning |
|---|---|
| **Evoking** | The marker effect `spellcraft:evoking`. While a player has it, the two keys work. |
| **Cast** | One press of a key that the server accepts: arms up, a sound, then the spell's **perform** one second later. |
| **Warmup** | The 20 ticks between an accepted press and the perform (vanilla `SpellcasterUseSpellGoal.getCastWarmupTime`). |
| **Casting ticks** | How long the arms stay up: 40 for fangs, 100 for vexes (vanilla `getCastingTime`). No other cast is accepted meanwhile. |
| **Interval** | How long after an accepted press the same spell can be pressed again: 100 ticks for fangs, 340 for vexes (vanilla `getCastingInterval`). |
| **Casting effect** | One of two short marker effects (`spellcraft:casting_fangs`, `spellcraft:casting_vexes`) the server puts on the caster for the casting ticks. Its only job is to tell every client "this player is casting this spell". |
| **Fang square** | The 13×13 block area a fang cast covers. The caster's block is on its edge; where on the edge depends on the caster's yaw. |
| **Target** | A living entity the fangs or vexes may hurt: alive, visible as an enemy (not a creative or spectator player, not invulnerable, not a spectator), attackable, not a player the caster cannot harm (`pvp` rule; teammates are already spared by the ally rule), and not the caster or an ally. |
| **Ally** | Per the shared rule in §3: what vanilla `Entity.isAlliedTo` says (same team, a tamed animal whose owner is the player), or a mob the player summoned (its **summoner** attachment names the player), or any other `OwnableEntity` the player owns. |
| **Summoner** | A saved entity attachment, `spellcraft:summoner`, holding the UUID of the player who summoned a mob. Evoking puts it on every vex it calls. |

## 1. Scope

**In scope:**

- The Evoking declaration, effect, scroll, icons and lang entries (§2).
- The shared ally rule and the Trailing edit (§3).
- The key bindings, the request packet and the server's casting state (§4).
- Fangs (§5) and vexes (§6).
- The client's arm pose and hand particles (§7).
- Unit tests, GameTests, script tests, docs and playtest items (§9).

**Out of scope:**

- Wololo, the evoker's third spell. Nothing in the request asks for it.
- Any first-person hand animation. You cannot see raised arms from inside them; the
  first-person view is vanilla's (§10).
- A cooldown or busy message on the HUD (§10), and configuration for any of the numbers below:
  they are the evoker's, and the request says to match them.
- Saving casting state across a relog: a cast in flight or a cooldown is forgotten when the
  player logs out (the casting effect itself is saved with the player, and expires harmlessly).

## 2. The spell

### Declaration (pure core, `SpellDeclarations.EVOKING`)

| `SpellRules` field | Value | Why |
|---|---|---|
| `name` | `evoking` | scroll `evoking_scroll`, effect `evoking` |
| `tier` | `AMAZING` | stronger than Trailing (`GOOD`), weaker than the top tier; the first spell in this band |
| `recast` | `REFUSE` | the effect has no duration to refresh, so a second scroll would be wasted (as Infinity) |
| `dispellable` | yes | sneak + scroll turns it off (as Upside-Down and Trailing) |
| `redundantInCreative` | no | creative players have no fangs or vexes |

`Spells.EVOKING` binds it to `ModEffects.EVOKING` with `Spell.INFINITE`. `Spells.ALL` and
`SpellDeclarations.ALL` become `[UPSIDE_DOWN, INFINITY, TRAILING, EVOKING]`; `ModItems` registers
`EVOKING_SCROLL`. Scribing picks it up from `Spells.ALL` by itself (it joins the lower pool).

### Effect

`ModEffects.EVOKING`: a `MarkerEffect`, "Strength of Evoking", category *beneficial*, color
`0xD2F8EA` (light mint). Infinite duration, so the HUD shows "∞". Milk cures it (vanilla
default), like Upside-Down and Trailing. Applied as every other spell is: particles hidden,
icon shown.

### Casting effects

`ModEffects.CASTING_FANGS` and `ModEffects.CASTING_VEXES`: `CastingEffect`, a `MobEffect`
subclass that uses NeoForge's particle-factory constructor so each effect's particle is the
matching **casting particle** (§7): `ModParticles.CASTING_FANGS` / `CASTING_VEXES`, two
`SimpleParticleType`s the mod registers. Category *neutral*. The server applies one at every
accepted press with `duration = casting ticks`, `amplifier 0`, `ambient false`, **`visible
true`**, **`showIcon false`**. Visible means vanilla puts the effect's particle into the
entity's synced particle list, which reaches every client that sees the caster; that list is the
casting state's sync channel (§7). `showIcon false` keeps it off the HUD, and a client extension
(`IClientMobEffectExtensions`, §7) keeps it out of the inventory, so neither needs a texture.
These effects do nothing by themselves; losing one early (milk, `/effect clear`) only drops the
arms.

### Scroll and icons

"Scroll of Evoking", stack size 16, in the creative tab, scribable, `/give @s
spellcraft:evoking_scroll`. The glyph (`scripts/spell_icons.py`), ink `(210, 248, 234, 255)`
= `#D2F8EA`, a "Y" with a head in its fork:

```text
#.###.#
#.###.#
#.###.#
.#.#.#.
..###..
...#...
...#...
...#...
```

Rows 0–2: the raised arms (outer columns) beside a 3×3 head. Row 3: the arms angle in to the
shoulders; the single center pixel is the neck. Row 4: the shoulders. Rows 5–7: the body, the
stem of the Y. Origin `(5, 5)` on the parchment; the effect icon is the glyph at 2× centered in
18×18, as for every spell. Whether it reads as an evoker is Will's call (§9 playtest).

### Keys

Two key mappings, registered in `RegisterKeyMappingsEvent`, conflict context `IN_GAME`, in a
new category `spellcraft:spells` ("Spellcraft"):

| Mapping | Default | Lang key |
|---|---|---|
| `key.spellcraft.fangs` | **F** (`InputConstants.KEY_F`) | "Evoking: Fangs" |
| `key.spellcraft.vexes` | **V** (`InputConstants.KEY_V`) | "Evoking: Summon Vexes" |
| category `spellcraft:spells` | | `key.category.spellcraft.spells` = "Spellcraft" (`KeyMapping.Category.label()` builds the key from the id) |

Key codes in 26.3 are SDL scancodes, not GLFW codes (`Options.java:676` binds swap-offhand to
`9`; `InputConstants.java:46,62` name `KEY_F = 9` and `KEY_V = 25`): use the `InputConstants`
constants and `InputConstants.Type.KEYBOARD`, never `GLFW.GLFW_KEY_*`.

F is vanilla's *Swap Item With Offhand*. **While the local player has Evoking, a press of the
fangs key casts and does not swap**: the client drains the swap mapping's clicks in the same
tick when either Evoking key is bound to the same key as swap-offhand (`KeyMapping.same`), so
no vanilla swap click gets through. Without Evoking, F swaps as always and the mod sends
nothing. The player can rebind either key in Controls. V is vanilla's F3-chord *Dump version
info* (`key.debug.dumpVersion`, `Options.java:736`, category Debug). In Controls both keys show
red: F against Swap Item With Offhand, V against the F3-chord Dump Version Info. Both conflicts
are accepted (Will chose F and V); pressing V without F3 held reaches only the mod's mapping.

### Config

None. Every number is the evoker's.

## 3. Allies: one rule for every spell

Will's request: "consider tamed mobs as allies for all spells (which requires editing Strength
of Trailing)". Trailing already spares tamed animals: `TrailFires.isSpared` calls
`owner.isAlliedTo(entity)`, and vanilla's `TamableAnimal.considersEntityAsAlly` makes a pet an
ally of its owner (`docs/api-notes.md` §18, GameTest G7b). What Trailing does **not** spare is
a mob the player summoned: vanilla's `Vex` has an owner but never tells `isAlliedTo` about it
(only the *evoker* side of that pair is overridden), so without this section the caster's
own trail would burn the caster's own vexes, and the caster's fangs would bite them.

`io.github.willrieske.spellcraft.spell.Allies` (common; Minecraft-facing, so not in the pure
core) is the one rule:

- `isAllyOf(Player owner, Entity other)`: `owner.isAlliedTo(other)` (true for `owner` itself,
  the same team, pets, allays that like the owner), or `other` carries the **summoner**
  attachment with `owner`'s UUID, or `other` is an `OwnableEntity` whose owner reference is
  `owner`'s UUID (covers any ownable mob whose `considersEntityAsAlly` vanilla forgot, with
  no live owner lookup).
- `mayHaveAllies(Entity other)`: the cheap pre-check Trailing's fast path needs: a team, an
  `OwnableEntity`, an `Allay`, or a summoner attachment. False means `isAllyOf` cannot be true
  for any owner, except through a vanilla override the list does not know (team, `OwnableEntity`,
  `Allay` and the summoner attachment are what it knows); a modded entity with its own
  `considersEntityAsAlly` would need adding.

`AllyAttachments.SUMMONER` (`spellcraft:summoner`, `UUID`, serialized with `UUIDUtil.CODEC`,
not copied on death) is the attachment. It is a `spell`-package registration so that `trailing`
and `evoking` both depend on `spell` and not on each other.

**Trailing edit:** `TrailFires.isSpared` keeps its shape and swaps its two tests for
`Allies.mayHaveAllies` and `Allies.isAllyOf`. Its behavior for teams, pets and allays is
unchanged (G7, G7b, G7c still pass); new is that a flame spares the owner's summoned vexes.
The Trailing spec's glossary row **Ally** now points here.

**Fangs edit:** vanilla `EvokerFangs.dealDamageTo` already skips `owner.isAlliedTo(victim)`.
For the summoner path, `Fangs` (§5) cancels `LivingIncomingDamageEvent` when the source's
direct entity is an `EvokerFangs`, its causing entity is a player, and `Allies.isAllyOf(player,
victim)`. An event, so no Mixin.

## 4. Casting: from a key press to a perform

### Client → server: the request packet

`EvokingRequest(EvokerSpell spell)` is a `CustomPacketPayload` (`spellcraft:evoking_request`,
stream codec = NeoForge's `enumCodec`), registered in `RegisterPayloadHandlersEvent` as
`playToServer`, registrar version `"1"`, handled on the server thread. The client sends one per
key press while the local player has Evoking. The server **validates everything**: the packet
is a request, never a command.

This is the mod's first custom packet, and `CLAUDE.md` says "No custom packets". That rule was
written for Upside-Down, where the client only renders a synced effect. A key-triggered spell
has to tell the server which key was pressed, and vanilla carries no such message (chat
commands would do it, but they are rate-limited and kick for spam). The rule is amended to:
*the client never decides state; the one packet is a request the server validates*. The
clientbound direction stays packet-free: casting state reaches clients through vanilla's
effect-particle sync (§7).

### Server: `EvokingCaster` (plumbing) and `EvokingCast` (pure core)

`EvokingCaster` (`@EventBusSubscriber`, package `io.github.willrieske.spellcraft.evoking`)
keeps a transient `Map<UUID, Casting>` like `TrailLayer`'s trackers: dropped on logout and when
the server stops. `Casting` holds the pure `EvokingCast` state and, for a fang cast, the
**snapshot** taken at the press (§5).

`request(ServerPlayer player, EvokerSpell spell) → Request` (public, so GameTests can call it
without a packet). `Request` is `IGNORED`, `BUSY`, `COOLING`, `NO_TARGETS`, `PEACEFUL` or `STARTED`:

1. Ignore the request (`IGNORED`) unless the player is alive, not a spectator, and has Evoking.
2. `EvokingCast.readiness(spell, now)` answers `READY`, `BUSY` (another cast's casting ticks are
   running) or `COOLING` (this spell's interval has not passed). `BUSY` and `COOLING` are
   returned and otherwise ignored silently (§10); nothing is scanned.
3. `VEXES` only, when ready, and `EntityTypes.VEX.canSpawn(level)` is false (Peaceful: vanilla
   refuses to create a vex there): `sendOverlayMessage` with `message.spellcraft.peaceful_vexes`
   ("Vexes cannot be summoned on Peaceful") at once, the answer is `PEACEFUL`, and nothing else
   happens: no cast, no casting effect, no sound, no cooldown (§10). Busy and cooling presses
   stay silent, because step 2 answers them first.
4. `FANGS` only, when ready: take the snapshot and scan for targets (§5). With none,
   `EvokingCast.noTargets(now)` schedules the notice (below), the answer is `NO_TARGETS`, and
   nothing else happens: no arms, no sound, no cooldown.
5. Otherwise `EvokingCast.start(spell, now)`, the answer is `STARTED`, the prepare sound plays at
   the player (`EVOKER_PREPARE_ATTACK` or `EVOKER_PREPARE_SUMMON`, source `PLAYERS`, volume 1,
   pitch 1), and the spell's casting effect goes on for its casting ticks (§2). The player keeps
   moving freely; only the evoker (a mob) stands still to cast.

`now` is the level's game time (`DerivedLevelData.getGameTime` delegates to the overworld's, so
every dimension shares it).

`EvokingCast` (pure, `spell.rules`): one player's timers. Callers ask `readiness` before
`start` or `noTargets`; `start` throws `IllegalStateException` when not ready.

- `readiness(spell, now) → Readiness {READY, BUSY, COOLING}`: `BUSY` when `now < castingUntil`;
  else `COOLING` when `now < readyAt[spell]`; else `READY`.
- `start(spell, now)`: sets `castingUntil = now + spell.castingTicks()`, `performAt = now +
  WARMUP`, `pending = spell`, `readyAt[spell] = now + spell.intervalTicks()`, and drops any
  scheduled notice.
- `noTargets(now)`: sets `noticeAt = now + WARMUP` (a later press moves it; one notice at most).
- `due(now) → Optional<Due>`: `PERFORM(spell)` the first time `now ≥ performAt` with a pending
  spell, else `NOTIFY_NO_TARGETS` the first time `now ≥ noticeAt`; each is returned once.
- `isCasting(now)`: `now < castingUntil`.
- `EvokerSpell` (pure enum): `FANGS(castingTicks 40, intervalTicks 100)`, `VEXES(100, 340)`;
  `WARMUP = 20` is shared.

Every server player tick (`PlayerTickEvent.Post`), `EvokingCaster.tick(player)` (public for
tests) asks `due(now)`:

- `PERFORM(spell)`: if the player is still alive, still has Evoking, and (fangs) is still in the
  snapshot's dimension, perform it (§5 or §6) and play `EVOKER_CAST_SPELL` at the player.
  Otherwise nothing happens; the cooldown stands.
- `NOTIFY_NO_TARGETS`: only if the player is alive, `sendOverlayMessage` with
  `message.spellcraft.no_targets` ("No targets in range"), the same action-bar channel as the
  refused scroll. A dead player gets no notice (§8).

Without Evoking the player's entry is dropped, so a dispel also forgets cooldowns.

## 5. Fangs

### The square (pure core, `FangSquare`)

The caster's yaw gives a facing vector `(fx, fz) = (−sin yaw, cos yaw)`. Scale it so its larger
component is ±1 (`k = max(|fx|, |fz|)`, `(cx, cz) = (fx / k, fz / k)`) and round:
`center = (round(6·cx), round(6·cz))` relative to the caster's block. The square is
`center ± 6` on both axes: 13×13 blocks, 169 cells. One of the center's offsets is exactly ±6,
so the caster's cell is always on an edge, and the other offset sweeps −6…6 as the player turns,
which is the block-by-block shift Will described. Examples: facing north (yaw 180) the square
runs 12 blocks north with the caster mid-way along its south edge; facing north-east the caster
is its south-west corner; facing east, mid-way along its west edge.

`FangSquare.of(yawDegrees)` exposes `centerDx`, `centerDz`, `minDx/maxDx/minDz/maxDz` and
`cells()`: the 168 cells other than the caster's, each with `dx`, `dz` and a `delayTicks` of
`chebyshev(dx, dz) − 1` (0…11): the fangs ripple outward from the caster one block per tick,
as the evoker's line does. `round` is half-up (`Math.round`).

### Targets and the snapshot (server, `Fangs`)

At the press, with the caster's feet block `F = blockPosition()` and the square `S`:

- Scan box: `x ∈ [F.x + S.minDx, F.x + S.maxDx + 1)`, `z` likewise, `y ∈ [F.y − 6, F.y + 7)`:
  the square's columns, six blocks down and six up from the caster's feet (§10).
- Targets: `level.getEntitiesOfClass(LivingEntity.class, box, e -> e != caster &&
  e.canBeSeenAsEnemy() && e.attackable() && !Allies.isAllyOf(caster, e))`.
  `canBeSeenAsEnemy` is vanilla's "may a mob attack this": alive, not a spectator, not
  invulnerable, and for players not creative. `attackable` drops armor stands. A player the
  caster cannot harm (`ServerPlayer.canHarmPlayer`: the `pvp` game rule off; teammates are already spared by the
  ally rule) is not a target (`Fangs.isTarget`), so fangs never start a cast whose bites vanilla
  would refuse.
- No targets: step 4 of §4 (notice only).
- Otherwise the snapshot is `(dimension, F, S, top, bottom)` with `top = max(F.y, highest
  target's feet y) + 1` and `bottom = min(F.y, lowest target's feet y) − 1`, the evoker's own
  window generalized to several targets.

### Perform

For each of the 168 cells, at `(F.x + dx, F.z + dz)`: starting at `y = top`, walk down while
`y ≥ bottom` until the block below is face-sturdy on top (`isFaceSturdy(level, below, UP)`);
then the fang stands at `y + (the cell's collision shape's max Y, if the cell is not empty)`,
exactly `Evoker.createSpellEntity`. No sturdy floor in the window: no fang in that cell. A cell
gets a fang only when its chunk is entity-ticking (`ServerLevel.isPositionEntityTicking` at the
caster's height), not merely loaded: a fang in a non-ticking chunk would be saved with the chunk
and bite later, and the floor search would load chunks synchronously. Each
fang is a vanilla `EvokerFangs(level, x + 0.5, y, z + 0.5, angle, delayTicks, caster)` with
`angle = atan2(dz, dx)` (it faces away from the caster, as the evoker's line does) and the
cell's `delayTicks`. `addFreshEntity`, no game event (the evoker emits one per fang; 168 of
them would wake every sculk sensor nearby for nothing).

The fangs are vanilla from here: they bite after their warmup for 6 magic damage credited to
the caster (`indirectMagic(fangs, caster)`), skip the caster and `isAlliedTo` allies, and the
`LivingIncomingDamageEvent` guard (§3) skips the caster's summons. A fang bites whoever is
there when it snaps, not only the targets that were scanned.

The square and the targets are frozen at the press. A target that leaves in the warmup second
is missed; one that walks in is bitten. The perform uses the snapshot's `F`, not where the
caster has walked to.

## 6. Vexes

### Perform (server, `VexSummons`)

Three vexes. For each, with `dice = level.getRandom()`:

- Offset: `VexRing.offset(dice.below(40))` (pure): the 40 cells with Chebyshev distance 2 or 3
  from the caster's cell, in a fixed order; spawn block = `F.offset(dx, 1, dz)`, as the evoker's
  `offset(…, 1, …)`. This is "2–3 blocks away" measured per axis; Will's call if it should be
  Euclidean (§10).
- `EntityTypes.VEX.create(level, MOB_SUMMONED)`, `snapTo(pos, 0, 0)`, `finalizeSpawn(level,
  difficultyAt(pos), MOB_SUMMONED, null)` (gives it its iron sword), `setBoundOrigin(pos)`,
  `setLimitedLife(20 · (30 + dice.below(90)))` (30–120 s, then it starves, vanilla), the
  **summoner attachment** = caster's UUID, join the caster's scoreboard team if it has one
  (vanilla does this for the evoker), `addFreshEntityWithPassengers`.

On Peaceful `EntityTypes.VEX.create` returns null (`canSpawn(Level)` is false there), so
`VexSummons.summon` skips the vex; `EvokingCaster.request` turns the press away first (§4), so
the skip is only a guard for another caller.

`Vex.setOwner` takes a `Mob`, so the vanilla owner field stays empty; the attachment is the
ownership record, and it is saved with the entity.

### Goals

`VexSummons` listens to `EntityJoinLevelEvent` on the server: for a `Vex` that carries the
summoner attachment (freshly summoned or loaded from disk), it

- removes every target goal (`targetSelector.removeAllGoals(g -> true)`): vanilla's hurt-by,
  copy-owner-target and nearest-player goals;
- sets `Attributes.FOLLOW_RANGE` base value to **5** (the targeting range vanilla's goal uses:
  "a 5-block aggro range with themselves at the center");
- adds `NearestAttackableTargetGoal<>(vex, LivingEntity.class, mustSee = true, selector)` at
  priority 1, with `selector = (target, level) -> VexSummons.isPrey(level, summoner, target)`:
  `target.attackable() && summoner is online in that level && !Allies.isAllyOf(summoner, target)
  && (target is no player || summoner.canHarmPlayer(target))`. `attackable()` drops armor stands, which
  vanilla's conditions do not check (the vindicator's goal adds the same test,
  `Vindicator.java:209`). Vanilla's combat conditions (`TargetingConditions.test`) add the rest:
  alive, visible as an enemy (no creative or spectator players, nothing invulnerable), not
  `isAlliedTo` the vex, in line of sight, within range.

The goal is an anonymous subclass that overrides `canContinueToUse`: vanilla's
`TargetGoal.canContinueToUse` re-checks only `canAttack` and never the selector, so the chosen
target is re-checked against the same rule (`isPrey`) every tick and dropped when it becomes an
ally (tamed, or marked by the summoner) or the summoner leaves.

The summoner's PvP rules apply to its vexes: `isPrey` also refuses a player the summoner cannot
harm (`canHarmPlayer`: the `pvp` game rule; teammates are already spared by the ally rule).

So a summoned vex never targets its summoner, the summoner's team, pets or other summons, and
attacks any other living thing that comes within five blocks, players included only while the
summoner may harm them. With
the summoner offline it attacks nothing (§10). The movement goals (charge, wander around the
bound origin, look) are vanilla's. Installing on join rather than at the summon means a vex
that survives a save and reload keeps its manners.

## 7. Client: arms and particles

Everything here lives under `client.evoking` (ArchUnit rule 1) and only reads state the server
synced.

### Casting state from synced data (`CastingState`)

`LivingEntity.DATA_EFFECT_PARTICLES` is the synced list of particle options of an entity's
visible effects; vanilla fills it on the server and reads it on every client that tracks the
entity. An access transformer line makes the accessor public (the project's second AT entry,
same file as `ClientInput.moveVector`). `CastingState.of(LivingEntity) → Optional<EvokerSpell>`
answers `FANGS` if the list contains `ModParticles.CASTING_FANGS`, `VEXES` for
`CASTING_VEXES`, else empty. Both are `SimpleParticleType` singletons, so identity suffices.

Why this and not a packet: it is the vanilla channel for "show this effect's particles on
everyone's screen", it reaches a client that starts tracking the caster mid-cast (the data is
part of the entity's spawn data), it expires with the effect, and it keeps "server owns state;
the client renders it" literally true.

### No vanilla swirl (`CastingParticles`)

`RegisterParticleProvidersEvent`: `registerSpecial` for both casting particle types with a
provider that returns `null`, so vanilla's random effect swirl around the body draws nothing.
Special providers need no `particles/*.json`: `ParticleResources.reload` only loads the json
files it finds (`ParticleResources.java:214-231`), and a missing file is never looked for.

Every `ClientTickEvent.Post`, for each `AbstractClientPlayer` in the client level with a
casting state, add two `ENTITY_EFFECT` particles at the hands, the evoker's own formula
(`SpellcasterIllager.tick`): `a = yBodyRot·π/180 + cos(tickCount · 0.6662) · 0.25`, hands at
`(x ± cos a · 0.6, y + 1.8, z ± sin a · 0.6)`, color `(0.4, 0.3, 0.35)` for fangs and
`(0.7, 0.7, 0.8)` for vexes. `EvokerPose.handOffset(bodyYawDegrees, tick)` (pure core) gives
one hand's offset; the other is its negation (done by the caller). The two colors are constants
in `CastingParticles`. While the client is paused (`Minecraft.isPaused()`) or the level is frozen (`/tick freeze`) no hand particles
are emitted, so none pile up behind the pause menu.

### Raised arms (`EvokingArmPose`)

NeoForge lets a mod add an `HumanoidModel.ArmPose` with a transformer (FML enum extension):

- `META-INF/enumextensions.json` declares `SPELLCRAFT_EVOKING` on
  `net/minecraft/client/model/HumanoidModel$ArmPose`, constructor
  `(ZZLnet/neoforged/neoforge/client/IArmPoseTransformer;)V`, parameters from the static field
  `EvokingArmPose.PROXY` (an `EnumProxy<ArmPose>` holding `true, false, transformer`);
  `neoforge.mods.toml` gains `enumExtensions="META-INF/enumextensions.json"`.
- The transformer, for each arm: `xRot = cos(ageInTicks · 0.6662) · 0.25`, `yRot = 0`,
  `zRot = +3π/4` (right) or `−3π/4` (left): `IllagerModel`'s SPELLCASTING pose. The player's
  arms already sit at x = ∓5, so the illager's position shift is not needed.
  `EvokerPose.armRotation(ageInTicks, rightArm)` (pure) supplies the numbers.
- `RegisterRenderStateModifiersEvent.registerAvatarEntityModifier`: after vanilla extracts a
  player's render state, if `CastingState.of(player)` is present set both `rightArmPose` and
  `leftArmPose` to `PROXY.getValue()`. `HumanoidModel.setupAnim` then routes both arms to the
  transformer. This applies in third person, on other players, and in the inventory doll.

### Inventory and HUD

`RegisterClientExtensionsEvent.registerMobEffect` with an `IClientMobEffectExtensions` whose
`isVisibleInInventory` and `isVisibleInGui` return false, for both casting effects.

### Keys (`EvokingKeys`)

`RegisterKeyMappingsEvent` registers the category and the two mappings (§2). On
`ClientTickEvent.Pre` (posted at the head of `Minecraft.tick`, before `handleKeybinds`):

```text
for each (mapping, spell): while mapping.consumeClick(): if player has Evoking: send EvokingRequest(spell)
if player has Evoking and a casting mapping that is .same(options.keySwapOffhand) consumed a click this tick: while keySwapOffhand.consumeClick() {}
```

So with Fangs rebound to Shift+F a plain F press still swaps hands and Shift+F casts.

## 8. Error handling

- A request from a player without Evoking, dead, or a spectator: ignored. The packet is
  trusted for nothing.
- Evoking and death: Evoking survives death until respawn; `removeAllEffects` is called only by
  `/effect clear` and the consume effect. The perform-time alive check is what stops a cast. A
  request from a dead player is ignored (`IGNORED`). No no-targets notice is sent to a dead
  player.
- Vexes on Peaceful: the press is refused with the notice `Vexes cannot be summoned on Peaceful`
  (`PEACEFUL`), no cast, no cooldown (§4).
- PvP: fangs and vexes treat a player the caster cannot harm (`canHarmPlayer`: the `pvp` game
  rule; teammates are already spared by the ally rule) as no target and no prey (§5, §6).
- Casting ticks running or interval not passed: `BUSY` / `COOLING`, ignored silently (§10).
- Evoking lost between press and perform (milk, dispel, `/effect clear`): no perform. Death keeps Evoking; the alive checks stop the cast instead (below).
  The casting effect, if still present, expires on its own.
- Dimension change between press and perform: fangs are not placed (the snapshot is for the
  old level); vexes are summoned where the player now stands.
- Logout or server stop: the caster's entry is dropped; the casting effect is saved with the
  player and expires after the relog. A fang whose caster has since logged out has no owner and
  bites like a vanilla ownerless fang, allies included; accepted, a one-second window.
- A cell with no sturdy floor in the window, outside the build limits, or in a chunk that is not
  entity-ticking: no fang there. Implemented as `ServerLevel.isPositionEntityTicking(BlockPos)`
  tested at the caster's y, before the floor walk. A fang in a non-ticking chunk would be saved
  with the chunk and bite later, and the floor search would load chunks synchronously.
- Repeated no-target requests from a modified client each cost one 13×13×13 scan and start no
  cooldown; accepted. The Peaceful notice is likewise unthrottled (one overlay message per refused press); accepted.
- A vex that fails to `create` (only on Peaceful, which `request` already refuses): skipped, a guard for other callers. A cast started on Normal and performed after a switch to Peaceful summons nothing and plays no cast sound.
- Summoner offline: the vex is passive; it starves out within two minutes anyway.
- Enum-extension faults come in three cases. A syntax error, wrong prefix or missing file in
  `enumextensions.json` fails at launch on every dist. A wrong constructor descriptor fails in
  `runData` (the class literal in `EvokingArmPose`'s initializer loads and transforms `ArmPose`).
  A wrong `field`, `class` or argument list fails in `ArmPose.<clinit>` at the first humanoid
  render state, which `runData` now reaches through `EvokingArmPoseCheck` (client datagen,
  `GatherDataEvent.Client`): it initializes `ArmPose` and checks that `SPELLCRAFT_EVOKING` is
  `EvokingArmPose.PROXY`'s value, two-handed, and does not affect the offhand pose. The real
  render of the raised arms stays a playtest item.
- Under `/tick freeze` the client particle engine does not tick but players do, so
  `CastingParticles` skips its hand particles when `!level.tickRateManager().runsNormally()`.

## 9. Testing

### Unit tests and jqwik properties (pure core, TDD, `./gradlew test`)

New classes follow the methodology's *Unit test style* (Javadoc per test, `@Nested` per method,
Arrange/Act/Assert).

**`FangSquare`:**

| # | Case | Expected |
|---|---|---|
| F1 | yaw 180 (north) | center `(0, −6)`; dx −6…6, dz −12…0 |
| F2 | yaw 0 (south), −90 (east), 90 (west) | centers `(0, 6)`, `(6, 0)`, `(−6, 0)` |
| F3 | yaw −135 (north-east) | center `(6, −6)`; the caster is the south-west corner |
| F4 | yaw 180 ± 20 | center dz −6; dx shifts by whole blocks, monotonic in yaw |
| F5 | `cells()` | 168 cells, none `(0, 0)`, all within the square, all distinct |
| F6 | delays | `chebyshev − 1`; adjacent cells 0, far corner 11 |
| F7 | yaw outside −180…180 (e.g. 540) | same as the normalized yaw |
| F8 | yaw `NaN` or infinite | `of` throws `IllegalArgumentException` |

Properties: for every yaw, `max(|centerDx|, |centerDz|) == 6` (the caster is on an edge);
cells count 168 and exclude the origin; rotating yaw by 90° rotates the center by 90°;
`of(yaw)` equals `of(yaw + 360)`.

**`EvokingCast`:**

| # | Case | Expected |
|---|---|---|
| C1 | fresh, `readiness(FANGS, 100)` then `start(FANGS, 100)` | `READY`; `isCasting(139)` true, `isCasting(140)` false |
| C2 | then `readiness(VEXES, 120)` | `BUSY` (fangs' casting ticks run to 140) |
| C3 | then `readiness(FANGS, 150)` | `COOLING` (ready at 200); `readiness(FANGS, 200)` `READY` |
| C4 | `start(VEXES, 0)` then `readiness(FANGS, 100)` | `READY` (vexes' casting ticks ended at 100; fangs' own interval is free) |
| C5 | `due` after a start at 100 | empty at 119, `PERFORM(FANGS)` at 120, empty at 121 and after |
| C6 | `due` when first asked late (150) | `PERFORM` once, then empty |
| C7 | `noTargets(100)`, `due` | empty at 119, `NOTIFY_NO_TARGETS` at 120, once |
| C8 | `noTargets(100)`, `noTargets(110)` | one notice, at 130 |
| C9 | `noTargets(100)`, `start(FANGS, 105)` | no notice; `PERFORM` at 125 |
| C10 | `start` while `readiness` is not `READY` | `IllegalStateException` |
| C11 | `noTargets` on a fresh state | no start, so `readiness` stays `READY` (no cooldown) and `isCasting` is false (no arms) |

Properties: `due` never returns the same perform twice; after any sequence, at most one
perform is pending; `isCasting` is false once `now ≥ start + castingTicks`; a notice comes
exactly 20 ticks after the latest no-targets press unless a `start` dropped it.

**`VexRing`:** 40 offsets, all distinct, every one with Chebyshev distance 2 or 3, every such
cell present; `offset(i)` for `i` outside 0…39 throws.

**`EvokerPose`:** `armRotation(t, rightArm)` gives `xRot = cos(0.6662 t)·0.25`, right `zRot =
3π/4`, left `−3π/4`; `handOffset(yaw, t)` gives one hand's offset and matches the evoker's
formula at a few points (the other hand is its negation, done by the caller).
**`EvokerSpell`:** the four numbers and `WARMUP`.

**`SpellDeclarationsTest`** gains Evoking (`AMAZING`, not top, `REFUSE`, dispellable, not
redundant). **`CastRulesTest.decidesEveryCase`** gains `EVOKING` rows: the first spell that
refuses a recast *and* dispels on sneak (active + sneaking → `DISPEL`, active → `REFUSE`,
creative → `CAST`). **`SpellAssetsTest`** covers the new assets by itself. **`ArchitectureTest`** is
unchanged: `evoking` is common, `client.evoking` is client, `spell.rules` stays pure.

### GameTests (`./gradlew runGameTestServer`)

Mock players are never ticked by the server (`docs/api-notes.md` §19), so a GameTest steps the
caster by hand (`EvokingCaster.tick(player)`, via a `step` helper) inside every delayed callback
that asserts a perform or a notice.

Cast rules in `EvokingGameTests`, written like `InfinityGameTests` (`CastRuleGameTests`
assumes a finite, configured duration and a refreshing recast, so it does not fit): cast
consumes the scroll and grants an infinite effect (`MobEffectInstance.INFINITE_DURATION`);
creative casts (not refused); recast refuses, keeps the scroll and shows the refused message;
sneak + scroll dispels; sneak while inactive casts; sneak with another item keeps the effect;
milk clears; category beneficial. The scribing zero-percent test accepts any lower-tier scroll
(Upside-Down, Trailing or Evoking).

`SpellcraftGameTests.add` gains an overload taking `maxTicks` and `padding`, which fills
`TestData`'s 12-field form (`TestData.java:15-27`: `dimension OVERWORLD`, `rotation NONE`,
`manualOnly false`, `maxAttempts 1`, `requiredSuccesses 1`, `skyAccess false`). Every test
below that places fangs or vexes uses **200 ticks and padding 13**: `StructureGridSpawner`
spaces tests by their padded bounds (`TestInstanceBlockEntity.getTestBounds` inflates by the
padding), so a 13×13 fang carpet or a vex wandering ±7 blocks never reaches a neighboring
test's mobs or mock player. The other evoking tests keep the shared 20 ticks and no padding.

Casting (driven through the public `EvokingCaster.request` / `tick`, a mock survival player
with Evoking, flat ground around the structure):

| # | Setup | Assert |
|---|---|---|
| E1 | Facing north, a zombie 5 blocks north, `request(FANGS)` | `STARTED`: `casting_fangs` on the player (duration 40, `showIcon` false, `isVisible` true), the synced particle list holds `CASTING_FANGS`; no fangs yet; after 20 ticks 168 `EvokerFangs` whose owner is the player, none on the caster's cell, all inside `x ∈ F.x−6…F.x+6, z ∈ F.z−12…F.z`; the saved `Warmup` of a fang beside the caster is 0 and of the far corner's 11 |
| E2 | Facing north-east (yaw −135), a pig on the far corner of the square; the caster walks 3 blocks during the warmup | fangs still land on the square frozen at the press: all inside `x ∈ F.x…F.x+12, z ∈ F.z−12…F.z` |
| E3 | Five strangers just outside the square (behind the caster, past the far edge, past the side edge, above the vertical window, below it: the lower edge of the scan window) | no casting effect, no fangs; after 20 ticks the action bar holds "No targets in range" (`drainActionBar`); a request 5 ticks later still yields one notice |
| E4 | Only allies in the square: a tamed wolf (owner on no team), a zombie on the player's team, a vex with the summoner attachment | treated as no targets |
| E5 | A creative player and a spectator in the square | no targets; a survival player in it is a target |
| E6 | `EvokerFangs` with owner = the player, warmup 0, under (a) the player's summoned vex, (b) a stranger zombie; tick past the bite | vex health unchanged; zombie hurt |
| E7 | `request(FANGS)` then `request(VEXES)` 5 ticks later; then `request(FANGS)` at 50 and at 100 ticks | second is `BUSY`, third `COOLING`, fourth `STARTED` |
| E8 | `request(VEXES)` | `STARTED`, `casting_vexes` for 100 ticks; after 20 ticks exactly 3 vexes at `y = F.y + 1`, each at Chebyshev 2–3 horizontally, each with the summoner attachment, `FOLLOW_RANGE` 5, one target goal, `getBoundOrigin()` = its spawn block, saved NBT `life_ticks` in 600…2400 |
| E9 | Player on a team, `request(VEXES)` | the vexes are on the team |
| E10 | Deterministic: a summoned vex snapped 1 block from the owner, with a `noAi` pig 4 blocks away | the vex targets the pig, never the owner; second case: a lone pig 8 blocks away is never targeted over 40 ticks |
| E11 | A `Vex` created by hand with the attachment, then `addFreshEntity`; then saved and reloaded | goals installed (one target goal, range 5); the reloaded copy has the same goal and range; a vex without the attachment keeps vanilla's three target goals |
| E12 | Owner's trail flame, the owner's summoned vex runs `entityInside`; a stranger's vex does the same | the owner's vex untouched; the stranger's vex ignited and hurt |
| E13 | `request(FANGS)` accepted, then `removeEffect(EVOKING)` before the perform | no fangs at 20 ticks |
| E14 | `request(FANGS)` from a player without Evoking; from a spectator with it | nothing: no effect, no fangs, no notice |
| E15 | `request(FANGS)` accepted, then `PlayerLoggedOutEvent` posted to the bus for the player (constructed as `TrailLayerGameTests` does), then 20 ticks | no fangs; `tick` on the player afterwards does nothing and throws nothing |
| E16 | Both cases use a prepared Nether pocket in a forced chunk. `request(FANGS)` accepted, then the player is teleported to the Nether before the perform | no fangs in either level; `request(VEXES)` accepted and the same teleport: the three vexes appear beside the player in the Nether |
| E17 | Zombie in the square; one cell holds a 1-block pillar, one cell is a 2-deep hole, one cell is a hole deeper than the window | the pillar cell's fang stands on the pillar, the shallow hole's fang stands at its floor, the deep hole has no fang |
| E18 | An armor stand, alone, in the square | no targets (`attackable()` false); a summoned vex 3 blocks from an armor stand never targets it; positive control: a pig farther than the armor stand becomes the target |
| E19 | `PlayerTickEvent.Post` posted for a mock player after a press | drives the perform: fangs appear after the warmup |
| E20 | Each casting effect | neutral category, mint color, carries its own particle (`CASTING_FANGS` / `CASTING_VEXES`) |
| E21 | `EvokingRequest.STREAM_CODEC` round trip for each spell | the decoded request equals the original |
| E22 | `request(FANGS)` accepted, then `onServerStopped` | the pending cast is cancelled: no fangs at 20 ticks |
| E23 | The caster dies (Evoking survives death, so only the alive checks can stop anything): `fangs_e23_death_cancels` after an accepted `request(FANGS)`; `fangs_e23_death_cancels_the_notice` after a `NO_TARGETS` press; `fangs_e23_dead_request_ignored` presses while dead with a pig in the square | no fangs after the warmup; no notice; the request is `IGNORED`, with no casting effect and no fangs |
| E24 | `fangs_e24_pvp_off_notice`: `pvp` off (own `evoking_pvp_off` environment, rule restored through `whenDone`), the only living thing in the square is another survival player | `NO_TARGETS`, the notice after the warmup, no fangs (E5 is the positive control with `pvp` on) |
| E25 | `vexes_peaceful_notice`: difficulty Peaceful (own `peaceful` batch, restored through `whenDone`), `request(VEXES)` | `PEACEFUL`, the notice `message.spellcraft.peaceful_vexes` at once, no `casting_vexes`, no vex after the warmup; back on Normal the next press is `STARTED` (no cooldown spent) |
| E26 | `vexes_target_recheck`: a vex has chosen an untamed wolf, then the summoner tames it | the target is dropped within 3 ticks (`canContinueToUse` re-checks `isPrey`) |

Allies (`Allies.isAllyOf` / `mayHaveAllies`, no Evoking needed):

| # | Setup | Assert |
|---|---|---|
| A1 | The player and a zombie on one team; a zombie on no team | ally; not an ally |
| A2 | A tamed wolf of a team-less player | ally; `mayHaveAllies` true |
| A3 | A vex with the summoner attachment naming the player; one naming another UUID | ally; not an ally |
| A4 | A vex loaded from NBT whose `owner` is the player's UUID (no attachment, no team; vanilla never calls it an ally); a stranger's owned vex (random UUID) | ally through the `OwnableEntity` path; the stranger's is not an ally |
| A5 | A plain zombie, a creeper | `mayHaveAllies` false; `isAllyOf` false for the player |
| A6 | Direct `VexSummons.isPrey` (`vexes_prey_rule`) | a stranger player is prey; a teammate, a tamed wolf, an armor stand and a vex whose summoner is offline are not |
| A6b | `allies_a6_pvp_off`: `pvp` off (own `pvp_off` batch, rule restored through `whenDone`), `isPrey` for a stranger player and for a pig | the stranger is not prey, the pig is |
| A7 | A mob carrying the owner's mark is an ally: a marked zombie in `allies_a3_summoner_attachment` (a vex alone would pass through `OwnableEntity`), and `trail_g7d_spares_a_summoned_mob`, where the owner's flame spares a marked zombie (not fire-immune, so a burn would show) and burns one marked by a stranger | the attachment alone spares and allies the mob |

### Script tests

`scripts/test_spell_icons.py` (`test_i7`): the Evoking glyph is 7 wide and 8 tall, its ink is
`(210, 248, 234, 255)`, the generated scroll is 16×16 and the effect icon 18×18 (the existing
parametrized checks pick it up from `SPELLS`).

### Architecture and mutation testing

ArchUnit rules 1 and 2 as they are. `./gradlew pitest` after the pure classes land; the new row
in `docs/mutation-testing.md` names any survivor and why.

### Playtest checklist additions (Will)

A new **Strength of Evoking** section: scroll icon (mint Y with a head); "∞" on the HUD; F with
zombies ahead: arms up in F5, evoker particles at the hands, the prepare sound, fangs a second
later rippling out across the square ahead, the square turning with you block by block, none
under you; F with nothing ahead: "No targets in range" after a second, no arms; F while the
arms are up or within five seconds: nothing; V: arms up for five seconds, three vexes beside
you after one; vexes ignore you and your tamed wolf, attack a zombie or cow within five blocks,
ignore one farther away, and die after 30–120 s; your own fangs and trail spare your vexes;
F no longer swaps hands while Evoking is on and does again after sneak + scroll; the inventory
shows no "Casting" entry; Controls lists "Spellcraft: Evoking: Fangs / Summon Vexes"; pausing
mid-cast leaves no particles piled up behind the menu; a second
client (LAN) sees your arms and particles; recast refused; milk clears; on Peaceful V shows the
notice and casts nothing; with `pvp` off fangs and vexes ignore other players; fangs near the edge
of simulation distance place nothing in the far cells; no hand-particle burst after `/tick freeze`.

## 10. Decisions Will did not state

| Decision | Choice | Why, and the alternative |
|---|---|---|
| What a fang cast places | Fangs on every cell of the square except the caster's (168), rippling outward | "No fang spawns on the player's square" only needs saying if fangs fill the square. Alternative: one fang under each target. |
| Client → server | One custom request packet; `CLAUDE.md` amended | Vanilla has no "key pressed" message; commands are rate-limited. |
| Casting state to other clients | Vanilla's effect-particle sync (no clientbound packet) | Keeps the client a renderer of synced state. |
| Cooldowns | The evoker's intervals (100 / 340 ticks), counted from the press; silent when busy or cooling | "Matches the normal evoker values." Alternative: no interval beyond the casting ticks, or a HUD hint. |
| No targets | Only the delayed notice; no arms, no sound, no cooldown | The request says the notification replaces the cast. |
| Target scan height | Six blocks below to six above the caster's feet | The request gives no height; 13 tall matches the square. |
| Fang floor window | The evoker's, over all targets | Same rule as vanilla, generalized. |
| F vs swap-hands | F casts while Evoking is on, swaps otherwise; rebindable | Will chose F. |
| Vex count and life | 3 per cast, 30–120 s, vanilla | "The powers of an evoker." No cap on how many are alive at once (the evoker's random cap would make V feel broken). |
| Vex retaliation | None: no hurt-by goal; a vex attacks only what enters its five blocks | A retaliation goal cannot exclude the owner. |
| Vex line of sight | Vanilla's (a target behind a wall is not seen) | Vanilla combat conditions. |
| Vex spawn ring | Chebyshev 2–3 per axis, y + 1 | Vanilla's 5×5 pattern widened; Euclidean would be the other reading. |
| Summoner offline | Vex attacks nothing | Allies cannot be computed without the owner. |
| First person | Vanilla hands | Raised arms are off-screen in first person. |
| Tier / recast / dispel | `AMAZING` / `REFUSE` / sneak-dispel, milk cures | Infinite duration makes refresh pointless; the other infinite spell refuses too. |
| Moving while casting | Allowed; the evoker stops because it is a mob | Freezing a player for five seconds would be hostile. |
| Losing Evoking mid-cast | The perform is dropped | The spell is gone. |
| Game events | None per fang | 168 sculk pings per cast. |
| Config | None | Every number is the evoker's. |
| Version bump | Deferred: the delivery does not bump `mod_version` | The release step (bump, tag) follows the final review. |
| Chunks that are not entity-ticking, and build limits | A cell there gets no fang (`ServerLevel.isPositionEntityTicking` at the caster's y) | A fang in a non-ticking chunk would be saved and bite later, and the floor search would force-load chunks; a 13×13 square near the edge of simulation distance must do neither. Alternative: `Level.isLoaded`, which accepts loaded but non-ticking chunks. |
| PvP | Fangs and vexes never target a player the caster cannot harm (`canHarmPlayer`: the `pvp` game rule; teammates are already spared by the ally rule) | A vex's hit names the vex as the cause, so vanilla's PvP gate in `ServerPlayer.hurtServer` never applies to it; without the rule a vex would break `pvp false`. |
| Peaceful | V is refused at request time with the notice `Vexes cannot be summoned on Peaceful` (lang key `message.spellcraft.peaceful_vexes`, request outcome `PEACEFUL`): no cast, no casting effect, no cooldown; busy and cooling presses stay silent | Vanilla refuses to create a vex on Peaceful, so a cast would show arms and spend the cooldown for nothing. Alternative: a silent no-op. |
| Vex target re-check | The chosen target is re-checked every tick (`canContinueToUse`) and dropped when it becomes an ally or the summoner leaves | Vanilla's goal re-checks only `canAttack`, so a pet tamed after being chosen would stay a target. |
| Paused or frozen client | No hand particles while `Minecraft.isPaused()` or while `/tick freeze` holds the level | `ClientTickEvent.Post` fires while paused, and players keep ticking while the level is frozen; particles would pile up. |

## 11. Delivery

README (four spells, the keys), the mod description, the playtest checklist, `docs/api-notes.md`
§19 (every hook used here, verified), `CLAUDE.md` (the packet rule, the pure-core list
unchanged), `docs/mutation-testing.md`. No version bump: the release step (bump `mod_version`,
tag) follows the final review. The jar name carries `mod_version`, so the real-profile install
copies the built jar and removes older `spellcraft-*.jar` files from the mods folder. One plan,
one branch (`feat/evoking`), one draft PR.
