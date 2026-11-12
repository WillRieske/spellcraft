# Spellcraft: Evoking II, Bound Vexes (design)

**Date:** 2026-11-07
**Status:** implemented 2026-11-12 via `2026-11-10-evoking-ii-part-7.md`; playtest pending
<!-- organize-docs: pinned -->
**Current behavior in one place:** [`evoking-functional-spec.md`](evoking-functional-spec.md) (the functional spec; it merges this document with the other Evoking specs).
<!-- child-plans: 2026-11-07-evoking-ii-part-1.md, 2026-11-08-evoking-ii-part-2.md, 2026-11-09-evoking-ii-part-3.md, 2026-11-09-evoking-ii-part-4.md, 2026-11-10-evoking-ii-part-5.md, 2026-11-10-evoking-ii-part-6.md, 2026-11-10-evoking-ii-part-7.md -->
**Shape:** built in seven parts (§1), each with its own plan, branch and PR
**Written by:** brainstorm with Will on 2026-11-07; §13 lists the decisions Will did not state
**Target:** Minecraft Java 26.3, NeoForge 26.3.0.23-beta, Java 25, ModDevGradle
**Builds on:** [`2026-11-06-evoking-i-design.md`](2026-11-06-evoking-i-design.md) (Evoking: keys, casting,
fangs, vexes), [`2026-11-04-trailing-design.md`](../2026-11-04-trailing-design.md) (allies) and
[`2026-09-29-infinity-design.md`](../2026-09-29-infinity-design.md) (the spell framework).
**Amended by:** [`2026-11-09-evoking-ii-part-3-gear-and-stay-design.md`](2026-11-09-evoking-ii-part-3-gear-and-stay-design.md)
(Part 3 as built: stay moves to the sneak click, a staying vex hovers and guards a 13 × 13 area,
the plain empty-hand click takes the weapon back, version 1.2.2). Where the two differ, that one wins.
**Amended by:** [`2026-11-09-evoking-ii-parts-4-5-design.md`](2026-11-09-evoking-ii-parts-4-5-design.md)
(Parts 4 and 5 as designed for the build: the cure's freeze and potion, bribed vexes, scan cadence,
the fireball's owner, rescue limits). Where the two differ, that one wins.
**Amended by:** [`2026-11-10-evoking-ii-parts-6-7-design.md`](2026-11-10-evoking-ii-parts-6-7-design.md)
(Parts 6 and 7 as designed for the build: team-move state, rosters, triggers and each move's
behavior; the packet's shape; the Caster's goals; Mirror images and confusion; Ghostrail's Mixin,
flight and damage rules; scroll mobs; which villagers leave ghosts). Where the two differ, that one wins.
**Amends:** the Evoking I spec's §2 keys (F → Y), §5 fangs (a fang per target, not a carpet), §6
vexes (vanilla vexes → Bound Vexes), §3 allies (three new ally kinds); the Trailing spec's spared
rule (iron golems are never lit, §2.3); `CLAUDE.md`'s packet rule (the one packet gains a second
request kind, §7.1).

## Summary

This is the second round of **Strength of Evoking**. Will played the first round and asked for
changes. The biggest one: the three vexes you summon with **V** become **Bound Vexes**, a new mob
of this mod that looks like a vex but acts like a loyal pet. Bound Vexes follow you (through
portals too), never fade away on a timer, guard you and your allies, fight what you fight, and run
from a hissing creeper. You can hand them armor and weapons, and they hand back what they held.
They cure zombie villagers with a golden apple and a splash potion, bribe an evoker's vexes with
an emerald, repair iron golems with iron ingots, and in the Nether and the End they do more:
knock Ghast fireballs back, pull you from lava, catch you over the void. Twelve of them can team
up for special moves (Shield, Multishot, Evader, Raid), and one in three is a **Caster** that
casts two new spells, **Mirror** (thirteen copies of you) and **Ghostrail** (fly through walls,
unhurt). Those two spells are real scrolls players can use too, evokers and illagers can carry
them, and a villager that dies to monsters leaves a **ghost** that comes back to help when you
need it.

Smaller changes ride along: fangs are cast with **Y** instead of F, they come up only under
enemies (and chase a mob that steps off), they reach 14 blocks down, and villagers, wandering
traders and iron golems count as allies, with a mercy rule for a golem that is mad at you.

The work is split into seven parts, built in order, each small enough for one plan and one PR.

## Glossary

| Term | Meaning |
|---|---|
| **Bound Vex** | `spellcraft:bound_vex`, this mod's own mob, a subclass of vanilla `Vex`. What **V** summons from now on. Carries the **summoner** attachment. |
| **Summoner** | The player who summoned a Bound Vex (attachment `spellcraft:summoner`, from the Evoking I spec). "You" in this document. |
| **Ally** | What `Allies.isAllyOf(player, entity)` says: vanilla's allies (team, pets, allays), the player's summons and owned mobs, and now villagers, wandering traders and iron golems (§2.3). |
| **Hostile** | A mob that implements `Enemy` (every `Monster`, slimes, ghasts, phantoms, shulkers, the dragon, the wither). Creepers are hostile but Bound Vexes skip them unless provoked (§3.4), and so are another player's summons (§3.4). |
| **Guard area** | The box around a Bound Vex in which it looks for hostiles: 8 × 8 × 7 blocks (±4 east/west, ±4 north/south, ±3.5 up/down), measured from the vex's feet. An entity is in it when its feet are, faces included. A staying vex's guard area is its **stay area** instead: 13 × 13 × 7 around its post (Part 3 spec §2.3). |
| **Reach** | 7.25 blocks: how far a following Bound Vex keeps a chosen target before it gives up on it. A staying vex keeps a target while it is in the stay area. The `FOLLOW_RANGE` attribute is 20 (Part 3 spec §2.3). |
| **Staying** | A Bound Vex told to hold its spot (sneak + empty-hand right-click toggles it; Part 3 spec §2). It hovers at its post, fights in its stay area and does not follow. |
| **Conjured gear** | Items a Bound Vex pulls out by itself (team moves, the cure, the bribe, repairs). They vanish when their job is done, cannot be taken, and never drop. |
| **Given gear** | Items you hand a Bound Vex, plus its own iron sword. Real: you can take them back and they drop on death. |
| **Team move** | One of Shield, Multishot, Evader, Raid (§8): several Bound Vexes acting together with conjured gear. |
| **Caster** | A Bound Vex rolled as a spellcaster at summon time (1 in 3). It casts scrolls instead of fighting, and wears a torn purple-and-magenta robe. |
| **Mirror**, **Ghostrail** | The two new spells (§9). Scrolls in the spell framework, castable by players; also cast by Casters, evokers, illagers and villager ghosts. |
| **Villager ghost** | `spellcraft:villager_ghost`: the spirit of a villager that monsters killed. Appears when you need help nearby, casts Mirror and Ghostrail on you, then fades, and can come back. |
| **Mercy** | A hit from your side can never take an iron golem below 25 health; at 25 it forgives you. |

## 1. Scope and the seven parts

Everything below is in scope. It is built in this order; each part is one plan, one branch, one PR,
and each lands before the next starts, so Will can play each one.

| Part | Name | What lands | Spec |
|---|---|---|---|
| 1 | Small fixes | Y key; fangs under targets, chase fang, 14 blocks down; villagers, traders and golems as allies; golem anger, mercy and forgiveness | §2 |
| 2 | Bound Vex basics | The mob, V summons it, no timer, cap of 12, follow/stay/teleport/portals, who it fights, wolves join, creeper flight, golem rules, golem repair | §3 |
| 3 | Gear | Armor you can see, weapons, shields, potions and arrows, taking a weapon back, drops | §4 |
| 4 | Cure and bribes | The zombie-villager cure; bribing an evoker's vexes | §5 |
| 5 | World helpers | Overworld, Nether and End behaviors | §6 |
| 6 | Team moves | Shield, Multishot, Evader, Raid; the automatic/manual setting and keys | §7–§8 |
| 7 | Casters, scrolls, ghosts | The Caster variant, Mirror, Ghostrail, illagers with scrolls, villager ghosts | §9 |

**Out of scope:** a spawn egg for any new mob (Will: none); Wololo; boots or elytra on vexes (they
have no feet and their wings are their own); any new clientbound packet (§7.1 keeps the one
packet, client → server); configuration beyond the one team-move setting (§7.3).

Cross-cutting rules: §10 (error handling), §11 (testing), §12 (docs and delivery), §13
(decisions).

## 2. Part 1: small fixes

### 2.1 Fangs on Y

`key.spellcraft.fangs` gets default `InputConstants.KEY_Y` (28). Vanilla binds keys by raw
scancode in `Options.java` (`"key.swapOffhand", 9`), and no binding there, plain or F3-chord,
uses 28 (checked 2026-11-07 against every `new KeyMapping(` line). The mapping is renamed `key.spellcraft.fangs_y` so a saved `options.txt` from
round one does not pin F: vanilla stores bindings by mapping name, and a new name falls back to the
new default. The lang entry stays "Evoking: Fangs". The swap-offhand swallow in `EvokingKeys` is
kept as is: it only fires when a casting key is `.same` as swap-offhand, so with Y it is inert and
with a user rebind to F it still works. V is unchanged.

### 2.2 Fangs only under enemies

**Scan.** `Fangs.snapshot` keeps the 13×13 square ahead and the target rule (`isTarget`), but the
scan box's vertical window becomes `y ∈ [F.y − 14, F.y + 7)`: fourteen blocks down, six up
(`SCAN_DEPTH = 14`, `SCAN_HEIGHT = 6`, replacing `SCAN_HALF_HEIGHT`).

**Snapshot.** Instead of `top`/`bottom`, the snapshot records a list of **target footprints**: for
every target, the set of block columns its bounding box covers at the press (`AABB` floored to
`BlockPos` columns, intersected with the square), each with the target's feet `y`. A ravager covers
several columns and gets a fang in each; a zombie gets one. The caster's own column is skipped as
before. For each column the floor is found at the press (the search below), so a footprint is a list of **spots** (column + floor height). Two targets sharing a spot get one fang (the first found keeps it); two targets in one column on different floors (one on a ledge) get one each. A target over no floor gets nothing.

**Perform.** The floor search (run at the press) is the evoker's own window around one target: from
`y0 + 1` down to `y0 − 2` for a target with feet `y0`, the same sturdy-face rule as today; the chunk
check runs at the perform; a fang per spot with `delayTicks = 0` (no ripple: the fangs are scattered, so they all snap together
one second after the press). Angle = `atan2(dz, dx)` from the caster's block at the press, as before (a chase
fang uses the same block). The `ENTITY_PLACE` game event is posted per fang, as in round one.

**Chase fang.** A fang records its intended target (the `Fangs` snapshot keeps `(column → target
UUID)`; the perform stores it in a transient map `fang entity id → (target UUID, caster UUID)`,
dropped when the fang is removed). When a fang bites (the mod listens on `EntityTickEvent.Post` for
the fangs in its map and acts on the tick vanilla deals damage: `EvokerFangs.tick` bites when its
warmup counter reaches −8, so eight ticks after the warmup ends; the plan verifies the number
against `build/mc-src`) and its target is alive, within 16 blocks of
the caster, not an ally, and **not standing on any of the fang's columns**, one more fang is placed
under the target's current footprint with the same floor search, `delayTicks 0`, and **no further
chase** (the chase fang carries no target record). One chase per target per cast.

**No targets:** unchanged (the notice, no cooldown).

### 2.3 New allies: villagers, wandering traders, iron golems

`Allies.isAllyOf(owner, other)` adds: `other instanceof AbstractVillager` (villagers and wandering
traders; both subclass it) → ally; `other instanceof IronGolem golem && !Allies.isMadAt(golem,
owner)` → ally. Every golem counts, village-made or player-made (Will: every golem, decision
§13).

`Allies.mayHaveAllies(other)` adds `AbstractVillager` and `IronGolem` so Trailing's fast path keeps
working.

**A mad golem.** `IronGolem` is a `NeutralMob`; the new helper `Allies.isMadAt(golem, owner)` is
`golem.getTarget() == owner || golem.isAngryAt(owner, (ServerLevel) golem.level())`
(`NeutralMob.isAngryAt(LivingEntity, ServerLevel)`, `NeutralMob.java:95`; `Allies` runs on the
server only, as `isAllyOf` already does). While it is mad at you it is not your ally: your fangs, vexes and
wolves may fight it. The Trailing flame is the exception: fire damage carries no attacker, so mercy
(below) could not tell your flame's burn from lava. **`TrailFires.isSpared` spares every
`IronGolem`, mad or not** (an extra clause before the ally check). The Trailing spec's spared rule
is amended by this line.

**Mercy.** `GolemMercy` (`spell` package, `@EventBusSubscriber`) listens on
`LivingDamageEvent.Pre`. When the victim is an `IronGolem` and the damage comes from a player's
side, the new damage is clamped to `max(0, health − 25)`. "A player's side" means the damage
source's causing entity is a player (fangs: `indirectMagic(fangs, caster)`; a sword hit is also the
player: Will's own hits are clamped too, see §13), or a tamed wolf whose owner is a player, or any mob carrying the **summoner** attachment (round one's vexes
now, Bound Vexes from Part 2 on: the mark is the test, not the class). "A player" is any player, not
only the fangs' owner. Lava, falls, other mobs and `/kill` are untouched.

**Forgiveness.** On `LivingDamageEvent.Post` (not in the clamp's handler: vanilla records the attacker
*after* the damage lands, `LivingEntity.resolveMobResponsibleForDamage`, so a clear in `Pre` would be
undone), when the hit leaves the golem at ≤ 25: `golem.stopBeingAngry()`, which clears the target, the
persistent anger and its timer, and the hit's memory. The golem
is an ally again at once (`isAngryAtPlayer` is false). It gets mad again only through vanilla's
rules (you hit it or a villager). Repair is Part 2 (§3.7).

**Golems and summons.** Vanilla golems attack any `Enemy` but creepers (`IronGolem.registerGoals`,
the `NearestAttackableTargetGoal<Mob>` with `target instanceof Enemy && !(target instanceof
Creeper)`). A Bound Vex is a `Monster`, so a golem would attack it. `GolemMercy` also cancels
`LivingChangeTargetEvent` when an `IronGolem` picks a mob that carries the **summoner**
attachment (round one's vanilla vexes in Part 1, Bound Vexes from Part 2 on, since they carry it
too). Villagers do not flee a Bound Vex: their hostile sensor lists `EntityType.VEX`, and the
Bound Vex is its own type.

## 3. Part 2: Bound Vex basics

### 3.1 The mob

`io.github.willrieske.spellcraft.evoking.BoundVex extends Vex`. `Vex` is a public, non-final class
with a public constructor (`Vex.java:47,57`), so subclassing is clean: the move control, the no-clip
flight and the vex model are inherited. Vanilla's vex goals are private inner classes
(`Vex.java:222,280,299,329`), so `BoundVex` does not call `super.registerGoals()` and has its own
copies of the charge and wander goals. **Amended by the Part 3 spec §2.2:** the wander goal is
replaced by `HoldPostGoal`. What changes:

- **Registration:** `ModEntities.BOUND_VEX` (`DeferredRegister<EntityType<?>>`,
  `EntityType.Builder.of(BoundVex::new, MobCategory.MONSTER).sized(0.4F, 0.8F)` with vex's
  tracking settings and `notInPeaceful()`), attributes = `Vex.createAttributes()` with `FOLLOW_RANGE` 7.25 (**amended by the Part 3 spec §2.3:** now 20),
  registered in `EntityAttributeCreationEvent`. `MobCategory.MONSTER` keeps vanilla's hostile-mob bookkeeping (it
  is still a monster to golems and villagers, handled in §2.3).
- **No spawn egg, no natural spawn, no loot table and no experience** (it drops only gear, §4.5;
  `xpReward` is 0, since summon-and-kill would farm 3 XP per vex). Will asked for no
  egg; `/summon spellcraft:bound_vex` works for testing and sets no summoner, so such a vex idles.
- **No timer:** `BoundVex` never calls `setLimitedLife`; `Vex.tick` only counts down when
  `hasLimitedLife` is true (`Vex.java:79`). `removeWhenFarAway` returns false and
  `requiresCustomPersistence` returns true, so it never despawns.
- **Summoner:** the attachment from round one. `summoner()` returns `Optional<ServerPlayer>`
  (online, alive, same level, not a spectator: as vanilla pets ignore a spectating owner, a
  spectating summoner counts as absent, so its vexes neither follow nor fight nor come through a
  portal until it plays again). Without a summoner the vex idles: hovers, fights nothing (§3.4 needs
  the summoner to compute allies), takes no interactions but still shows its gear.
- **Synced data:** each part adds the flag it first reads: `STAYING` (boolean, Part 2; also saved
  as NBT, because a cross-dimension teleport makes a new entity that copies the save but not the
  synced data), `TEAM_MOVE` (byte, §8, Part 6), `CASTER` (boolean, §9.1, Part 7). **Amended by the
  Parts 6–7 spec §2.1:** the team move is a server-only field, not synced data.
- **Renderer:** Part 2 registers vanilla's `VexRenderer::new` directly for the type, in
  `client.evoking.BoundVexRenderers` (`EntityRenderersEvent.RegisterRenderers`): same texture and
  model. Part 3 adds the `BoundVexRenderer extends VexRenderer` subclass with the armor layer (§4).
- **`VexSummons.summon`** creates `ModEntities.BOUND_VEX` instead of `EntityTypes.VEX`, keeps the
  ring offsets, `finalizeSpawn` (iron sword), bound origin, team join, and drops the
  `setLimitedLife` line and the `onJoin` goal install (goals are the class's own now). `isPrey`
  becomes `BoundVex.isPrey` (§3.4). `EvokingCaster.request(VEXES)` on Peaceful: refused as before,
  now by reading `ModEntities.BOUND_VEX.get().canSpawn(level)`. The type's `notInPeaceful()` flag
  (`EntityType.java:653`) is what makes `create` refuse on Peaceful and `Mob.checkDespawn` remove
  one already out (`Mob.java:715-716`); a `Monster` built without it would live on Peaceful.

### 3.2 The cap of twelve

`EvokingCaster.request(VEXES)`, when ready and not Peaceful, counts the summoner's living Bound
Vexes in every loaded level (`server.getAllLevels()`, `level.getEntities(ModEntities.BOUND_VEX,
v -> summoner matches)`; unloaded vexes are not counted, accepted §13).

- `count ≥ 12`: the answer is the new outcome `CAPPED`, `sendOverlayMessage` with
  `message.spellcraft.too_many_vexes` ("You have too many vexes"), no cast, no cooldown, no sound.
- `9 < count < 12`: the cast starts as usual and `VexSummons.summon` summons `12 − count`.
- otherwise three.

### 3.3 Following, staying, teleporting, portals

Goals (goal selector, in priority order; the target selector is §3.4):

0. `FleeCreeperGoal` (§3.5) and `DodgeGolemGoal` (§3.6), then vanilla's `FloatGoal`.
1. `TeamMoveGoal` (added by Part 6, which reads it; no placeholder before then).
2. The job goals of later parts (cure §5.1, bribe §5.2, repair §3.7, world helpers §6). Each job
   claims its subject (golem, zombie villager, evoker's vex) in a claim map keyed by the subject
   that **frees itself when the claiming vex is gone** (dead, removed or unloaded): a goal's `stop`
   never runs then, so the map checks whether the claimant is still alive instead of trusting
   `stop` to release it.
3. A copy of vanilla's charge goal (`Vex.VexChargeAttackGoal` is private; also drives melee with
   given weapons).
4. `FollowSummonerGoal`: when not staying and no target, hover within an orbit band of the summoner
   (default 2–4 blocks, §6 tightens it per dimension); when farther than **12 blocks**, teleport to
   a free cell within 2 blocks of the summoner (vexes clip through blocks, so any air cell works).
   Part 5 adds the summoner's **last ground position** (the last block the summoner stood on with
   `onGround()`), used by the void rescue (§6.3). **Amended by the Parts 4–5 spec §6.1:** where a
   vex hovers and how fast it flies is the posture table there; the close postures are overhead
   (2.2–3.0 above the feet), not a distance band at eye height.
5. A copy of vanilla's wander goal (`Vex.VexRandomMoveGoal`, around the bound origin) **only while
   staying**: the bound origin is set to the stay position when stay is toggled on, so a staying vex
   drifts around its post. A staying vex that changes dimension (only a command or a vehicle can do
   that: a vex never enters a portal itself) re-posts at the block where it arrives, since the
   saved post is the old dimension's coordinates. **Amended by the Part 3 spec §2.2:** the wander
   goal is replaced by `HoldPostGoal`; a staying vex hovers at its post and returns to it.
6. Vanilla's two `LookAtPlayerGoal`s, as `Vex.registerGoals` has them (no `RandomLookAroundGoal`).

**Stay/follow toggle:** (as Part 2 built it; **the Part 3 spec §2.1 and §3 move the toggle to the
sneak click and give the plain click to the take-back**.) `mobInteract` with an empty main hand and no sneak toggles `STAYING`; a
small sound (`VEX_AMBIENT`). Sneak + empty hand is Part 3 (§4.4). Interactions are accepted only
from the summoner. The answer is `InteractionResult.SUCCESS_SERVER` on both sides: the client does
not know the summoner (the mark is not synced) but must answer a success for every unsneaked
empty-main-hand click, because `Minecraft.startUseItem` falls through to the off-hand item on
`PASS` (a pearl would be thrown on every toggle). The server alone checks the summoner and answers
`PASS` for anyone else.

**Portals:** two events. `EntityTravelToDimensionEvent` fires before the move, while the player
still stands at the portal; it notes every living, non-staying Bound Vex of that player within 16
blocks. It also fires for same-dimension teleports (pearls, `/tp`), which are skipped.
`PlayerEvent.PlayerChangedDimensionEvent` then teleports the noted vexes to the player's arrival
position via `Entity.teleport(TeleportTransition)` (`Entity.java:3177`), placed on the ring cells
around the player. Staying vexes stay. A vex arriving with the player keeps gear, flags and
summoner (the teleport makes a new entity from the old one's save).
**The End exit:** the first time a player leaves the End through the exit portal (the credits),
the game respawns the player instead of teleporting it, and posts neither event above. The
followers are noted on NeoForge's `PlayerRespawnPositionEvent` (it carries the old player, still
at its old position, and says the respawn follows the End fight) and brought on
`PlayerRespawnEvent` when it says the End was conquered, with the same filter and the same ring
cells. A respawn after a death brings nothing (below).

**Logout:** nothing is removed. The vexes idle where they are and resume when the summoner is back
in their level. Death of the summoner: same as logout until respawn; after respawn they are in the
old level and follow again when the summoner returns there (no cross-dimension teleport on respawn;
§13).

### 3.4 Who a Bound Vex fights

`BoundVex.isPrey(level, summonerUuid, target)` keeps round one's base rule (attackable, summoner
online and alive in the level, not an ally, PvP rule for players) and extends the PvP rule to mobs
another player summoned: such a mob is prey only if the summoner may harm its owner (an offline
owner falls back to the level's `pvp` rule), so two players' vexes leave each other alone with PvP
off. With PvP on, a mob carrying another player's summoner mark is treated like that player: prey
only for the grudge goal (item 1, after it hurt this vex, the summoner or an ally), and never a
target of the patrol or ally goals (items 2 and 3), which hunt only what `BoundVex.huntable` accepts:
an `Enemy`, not a `Creeper`, carrying no other player's summoner mark. A player is not an `Enemy`,
so another player is never a patrol or ally target either. The target selector installs, in
priority order:

1. `HurtBySummonerSideGoal` (a `TargetGoal` the mod writes): the first of these, in this order,
   that is at most **100 ticks** old on its owner's clock and passes `isPrey`: the mob that last
   hurt **this vex** (`getLastHurtByMob`), the mob that last hurt **the summoner**
   (`summoner.getLastHurtByMob`), the mob the summoner last **hit** (`summoner.getLastHurtMob`), or
   the mob that last hurt **an ally** within the guard area (the ally's `getLastHurtByMob`). The
   window matters because vanilla forgets `lastHurtByMob` after 100 ticks but never forgets
   `lastHurtMob`. A creeper is allowed here (provoked).
2. `AllyFightGoal`: the current target of any **ally `Mob`** within the guard area (never the vex
   itself; an iron golem's or a wolf's `getTarget()`), if `isPrey` and `huntable` (hostile, so not a
   pig; never a creeper, since an ally's fight is not the vex being provoked; never another
   player's summon). Vexes join fights allies start.
3. `NearestHostileGoal`: `NearestAttackableTargetGoal<LivingEntity>` over the guard area (a custom
   `getTargetSearchArea` returning the 8×8×7 box) with selector `isPrey(...) && huntable(...)`
   (`Enemy`, not a `Creeper`, no other player's summoner mark). Line of sight is vanilla's (`mustSee`). A vanilla `Vex`
   (an evoker's) is an `Enemy`, so it is engaged; Part 4 makes that engagement a bribe attempt
   first (§5.2).

All three override `canContinueToUse` as round one did: the target is dropped when it stops being
prey, dies, is in another dimension (a summoner's memory can name a mob it left behind, whose raw
coordinates may line up with the vex's), can no longer be attacked (`canAttack`: a creative or spectator player, for one), or is
farther than **reach** (7.25). **Amended by the Part 3 spec §2.3 and §4.3:** that limit is for
a *following* vex only, and *taking* and *keeping* differ. A staying vex takes and keeps a target
only while its feet are in the stay area; a following vex takes a target within 7.25 whatever it
holds, but a following vex with ranged gear keeps one out to 12. "In the guard area" means the
entity's feet are in the box, faces included.

The three goals scan every **10 ticks** on one shared clock keyed to the vex's entity id
(`(tickCount + id) % 10 == 0`). A plain `tickCount % 10` would never fire for half the vexes:
vanilla only calls a goal's `canUse` every second tick, on ticks that depend on the id
(`Mob.serverAiStep`). Vanilla's own `NearestAttackableTargetGoal` cadence is a random roll, so the
patrol goal is built with a random interval of 1 and uses the shared clock too. §6's every-tick
scans will need their own tick hook, since `canUse` runs at most every second tick. **Amended by
the Parts 4–5 spec §6.1:** there is no tick hook; the cadences are 4 ticks (night, Nether) and 2
ticks (End) on the goal clock.

**Wolves join vex fights.** When a Bound Vex takes a new target (`setTarget` override on
`BoundVex`; a re-set of the same target does not count), every tamed `Wolf` owned by the summoner
within 16 blocks that has no target, is not sitting and would attack it (`Wolf.wantsToAttack`, so
never a creeper) gets `setTarget(sameTarget)`. Wolves are the only tamed mob that fights; golems
already fight hostiles on their own (§13).

**Mercy** applies to a Bound Vex's hits on a golem through `GolemMercy` (§2.3) by its summoner
attachment; nothing new is needed here.

### 3.5 Fleeing a hissing creeper

`FleeCreeperGoal` (priority 0): when any `Creeper` within 8 blocks has `getSwellDir() > 0`
(`Creeper.java:195`), the vex flies directly away from it to at least 8 blocks and stays there until
the creeper stops swelling or explodes. It abandons its target meanwhile (`setTarget(null)` is not
called; the flee goal simply outranks the charge goal, so the target is kept and resumed).

### 3.6 Iron golems and Bound Vexes

- Golems never target Bound Vexes (`GolemMercy` cancels `LivingChangeTargetEvent`, §2.3).
- `DodgeGolemGoal` (priority 0): when an `IronGolem` within 3 blocks is mid-swing
  (`getAttackAnimationTick() > 0`, `IronGolem.java:237`, public) and faces the vex (dot of the
  golem's **head** look vector, `getHeadLookAngle`, and the offset to the middle of the vex's box
  > 0.5; `getLookAngle` would read the body), the vex darts 3 blocks sideways (perpendicular to the golem's look) in one move. Every time, no roll:
  the case only arises while a mad golem is being fought (§13). The vex sidesteps *after* the swing:
  `IronGolem.doHurtTarget` sets the animation tick in the same call that applies the damage, so the
  goal reacts to a swing that has landed near the vex (a reaction, not an evasion; §13).

### 3.7 Repairing a golem

`RepairGolemGoal` (job goal): while the vex has **no target** (it stops when one appears, so a vex
under attack fights instead of healing), an ally `IronGolem` (so: not mad) within the guard area
with `health < maxHealth` and no other living Bound Vex already repairing it (a transient claim map
golem → vex that frees itself when the vex is gone, §3.3). The vex flies to within 1.5 blocks of
the golem's chest, conjures an **iron ingot** in its main hand on arrival (the given item is set
aside: by the goal itself until Part 3's stash, §4.5, lands), and every 30 ticks: swings the arm
(`swing(MAIN_HAND, SwingAnimation.DEFAULT)`; `VexModel` has no arm swing, so the sound is the
visible cue), plays `SoundEvents.IRON_GOLEM_REPAIR`, `golem.heal(25)` (the vanilla ingot amount,
`IronGolem.java:55`). Repeats until full, then the ingot vanishes and the given item returns. This
covers the forgiven golem of §2.3 and any hurt ally golem.

## 4. Part 3: gear

### 4.1 Armor

`mobInteract` by the summoner holding an item whose `DataComponents.EQUIPPABLE` slot is `HEAD`,
`CHEST` or `LEGS`: the vex equips it (`setItemSlot`), and the piece it wore there, if any, goes to
the summoner (`player.getInventory().add`, else dropped at the player). `FEET` and `BODY` items are
refused (`PASS`). Armor applies its attributes (vanilla `setItemSlot`) and enchantments.

### 4.2 Weapons, shields, potions, arrows

Right-click by the summoner with:

- a **melee weapon** (`Items` with an `ATTACK_DAMAGE` modifier: swords, axes, maces, tridents) or
  a **bow/crossbow**: it goes in the main hand; the old main-hand item (the iron sword, or an
  earlier gift) goes to the summoner. Vanilla's charge attack uses the main hand's attribute
  modifiers and enchantments (`Mob.doHurtTarget`), so a diamond sword hits harder. A bow or
  crossbow makes the vex a shooter: it shoots a plain arrow every 40 ticks from up to 10 blocks,
  keeping 6 blocks away, never charging. Tridents are melee (no throwing; §13).
- a **shield**: it goes in the **offhand**; the old offhand item returns. A shielded vex blocks: in
  `hurtServer`, melee or projectile damage whose source is in front of the vex (dot of the head's
  look vector, `getHeadLookAngle`, and source direction > 0) is cancelled with `SoundEvents.SHIELD_BLOCK`; no durability is used
  (nothing is ever used up). **Amended by the Part 3 spec §4.3 (Shield):** the test is vanilla's
  flat head-yaw geometry (the flat view vector from `getYHeadRot` against the flat direction to the
  source), not `getHeadLookAngle`, and a piercing arrow is not blocked.
- a **splash or lingering potion** or **tipped arrow**: main hand (the thrown entities are
  `projectile.throwableitemprojectile.ThrownSplashPotion` / `ThrownLingeringPotion`; the arrow
  is `projectile.arrow.Arrow`). If every effect is
  `MobEffectCategory.BENEFICIAL` it is **support gear**: every 40 ticks, when the summoner, an ally
  or a Bound Vex of the same summoner within 10 blocks is below **half health**, the vex throws a
  copy of the potion at it (`ThrownSplashPotion`/`ThrownLingeringPotion`) or shoots a copy of the
  arrow (an `Arrow` carrying the potion contents). Otherwise (any `HARMFUL` effect, or a plain
  arrow) it is a **ranged weapon**: thrown or shot at the target every 40 ticks from up to 10
  blocks, keeping 6 blocks away. The stack in hand never shrinks.
  **Amended by the Part 3 spec §4.1 and §4.3:** support gear is potions only. A tipped arrow
  whose effects are all beneficial is not gear (an arrow wounds before its effect heals), and a
  potion with no effects is not gear either.
- anything else: `PASS` (vanilla does nothing to a vex either).

### 4.3 Nothing is used up

No gear a Bound Vex holds loses durability or count: shield blocks spend no durability (§4.2),
thrown and shot items are copies, and melee hits restore the main-hand stack after each hit
(`BoundVex.doHurtTarget` snapshots the stack before `super.doHurtTarget` and puts it back), so the
item the player gets back is exactly the one they gave. **Amended by the Part 3 spec §4.5:**
nothing wears the weapon in 26.3 (`Mob.doHurtTarget` never reaches the durability call), so the
restore is a guard against an enchantment or a later version that does.

### 4.4 Taking a weapon back

**Amended by the Part 3 spec §3 and §4.4: the take-back is the plain (un-sneaked) empty-hand
click; sneak + empty hand toggles stay.** Right-click with an empty hand by the summoner: the main-hand item goes to the summoner,
and the vex fights bare-handed (vanilla vex attack damage). Armor and the offhand shield cannot be
taken back (Will's rule; §13). During a team move (Part 6) the answer is the overlay
`message.spellcraft.vex_busy` ("That vex is busy") and nothing moves.

### 4.5 Stash, drops, saving

- **Stash:** when a job or team move conjures gear into a slot, the given item in that slot moves
  to a per-slot stash (`stash` NBT, an `ItemStack` per slot) and comes back when the conjured item
  is dropped. `CONJURED` is a bit set of slots; a slot in it never drops and cannot be taken.
- **Death:** every non-conjured slot drops at 100%, plus the stash (how, and the Peaceful drop:
  the Part 3 spec §5.2). The own iron sword drops too (Will: real sword).
- **Saving:** vanilla saves a mob's equipment; the stash, the conjured bits, `STAYING` and `CASTER`
  are added in `addAdditionalSaveData`/`readAdditionalSaveData`.

### 4.6 Seeing the armor

`BoundVexArmorLayer` (`client.evoking`), added to `BoundVexRenderer`: renders the three armor
pieces through `EquipmentLayerRenderer` on a `HumanoidModel` armor set (the player's inner/outer
armor layers), posed from the vex's parts each frame: the helmet follows the vex head part, the
chestplate and leggings follow the body part, all scaled to the vex's size (body scale about 0.6;
the exact numbers are the plan's and Will's eye). `BoundVexRenderState extends VexRenderState`
carries the three stacks and the offhand; `extractRenderState` fills them. The main-hand item is
already drawn by vanilla's `ItemInHandLayer` on `VexModel` (an `ArmedModel`); the offhand shield
is drawn the same way in the left hand.

## 5. Part 4: cure and bribes

### 5.1 Curing zombie villagers

Zombie villagers are never prey (`isPrey` excludes `ZombieVillager`). `CureZombieVillagerGoal`
(job goal) claims a `ZombieVillager` in the guard area that is not converting and not claimed by
another Bound Vex, then runs a timeline. The zombie villager is frozen for the whole cure
(`setNoAi(true)`; `ZombieVillager.tick` still runs the conversion, as `tick` is not gated by AI).

| Step | Ticks | What you see |
|---|---|---|
| 1 | until within 1.5 blocks | The vex flies up level with the zombie villager's head. |
| 2 | 30 | A conjured **golden apple** in the vex's hand, `GENERIC_EAT` sounds and `ITEM` crumb particles at the zombie's mouth, arm swings. |
| 3 | 20 | The vex turns its head 60° to one side (`setYHeadRot`), then a real `ThrownSplashPotion` of **weakness** is thrown from the vex at the zombie from 1 block (glass break, splash, the brief weakness effect). |
| 4 | 400 | The vex backs off to 2 blocks. `startConverting(summonerUuid, 400)` (private in vanilla, `ZombieVillager.java:196`: one access-transformer line makes it public). Every tick, `sendParticles` of `HAPPY_VILLAGER` (green) and `CLOUD` (white) around the zombie, 4 each. Vanilla's conversion sound and shaking run as usual. |
| 5 | | Vanilla `finishConversion` makes the villager; the smoke stops. Because the conversion starter is the summoner, the villager gives the summoner the cured-villager discounts (vanilla). |

Costs you nothing (conjured apple, a potion created from nothing). If the zombie villager dies or
is unloaded mid-cure, the goal ends and the vex's gear returns. If the vex dies, is removed or is
unloaded, the frozen zombie is unfrozen (`setNoAi(false)`) and its conversion continues or not as
vanilla left it. The goal's `stop` never runs in those cases (§3.3), so the freeze is tied to the
curing vex and released whenever that vex is gone; Part 4's plan says how.

**Amended by the Parts 4–5 spec §2:** the zombie villager carries a saved cure mark, is frozen
from the claim, is an ally of every player while marked and cannot be a golem's target (§2.1); the
potion is weakness-colored with no effects (§2.2); the vex is free after the throw, and the smoke
and the freeze are driven from the zombie villager's own tick (§2.3). Flee, dodge and rescue may
interrupt the cure and that aborts it cleanly; a stale freeze is released by the mark's age (300
ticks), not by tracking the vex.

### 5.2 Bribing an evoker's vex

When a vanilla `Vex` (not a Bound Vex) in the guard area has the summoner or an ally as its target
and carries no `BRIBE_TRIED` attachment, `BribeGoal` (job goal, ahead of the charge goal) claims
it: fly to within 1.5 blocks, conjure an **emerald**, hold it out for 20 ticks with
`SoundEvents.VILLAGER_AMBIENT` (the villager "hmm"), then roll:

- **2 in 3:** the vex is bribed. It gets attachment `BRIBED`, its goals are replaced by one
  `FleeFromViewGoal`: fly away from the summoner, through blocks, until it is at least 12 blocks
  from every player within 64 blocks and no such player has line of sight to it
  (`hasLineOfSight`); then `discard()`: no sound, no particles, no drop. If it cannot reach that
  state within 200 ticks it discards anyway.
- **1 in 3:** the bribe fails; `BRIBE_TRIED` is set so no Bound Vex tries again, and the vex is
  prey as any hostile.

A bribed vex that is saved and reloaded keeps `BRIBED` and gets `FleeFromViewGoal` on
`EntityJoinLevelEvent`.

**Amended by the Parts 4–5 spec §3.1 and §3.2:** a vex claimed for a bribe ("courted") and a
bribed vex are not prey to any Bound Vex; a bribed vex flees the nearest player, not the briber's
summoner.

## 6. Part 5: world helpers

Each helper is a job goal that runs only in its dimension (`level.dimension()`). **Amended by the
Parts 4–5 spec §5:** only the crystal helper is a job goal; rescues are driven from the player's
tick (`VexRescues`) and the fireball return from `ProjectileImpactEvent`.

### 6.1 Overworld

- **Night guard:** when `level.isDarkOutside()`, the follow orbit is 1–2 blocks and the hostile
  scan runs every 5 ticks (day: 2–4 blocks, every 10).
- **Lava rescue:** when the summoner `isInLava()`, the nearest Bound Vex flies to the summoner and,
  each tick for up to 60 ticks, pushes them (`setDeltaMovement` toward the nearest non-lava,
  standable block within 6 blocks, plus 0.3 up; `hurtMarked = true` so the client gets it) until
  they are out. The vex holds the summoner's arm (hovers at their shoulder). The summoner keeps
  burning (Will chose only the pull here).
- **Drowning rescue:** when the summoner `isUnderWater()` with `getAirSupply() < 60`, the nearest
  vex pushes them straight up (0.4 per tick) until their eyes are in air.

**Amended by the Parts 4–5 spec §6.1, §5 and §6.2:** the night posture row and its 4-tick cadence
are in §6.1. `hurtMarked` does not exist in 26.3; the field is `syncVelocity`, and pushes add to
the player's velocity (§5, §6.2). The lava rescue has an episode, limits and a spot rule (§6.2).
The drowning push needs open water above and keeps the sideways velocity (§6.2).

### 6.2 Nether

- **Guard more:** orbit 1–2 blocks always, scan every 5 ticks.
- **Fireball return:** any `LargeFireball` within 4 blocks of the summoner moving toward them: the
  nearest vex within reach darts to it, swings, `setOwner(vex)`, and sets its velocity to the
  reverse of its heading (toward the ghast that shot it, if it is alive and in sight: aimed at it).
  `SoundEvents.PLAYER_ATTACK_KNOCKBACK`.
- **Lava rescue + water bucket:** as 6.1, then the vex conjures a **water bucket**, plays
  `BUCKET_EMPTY` and `FIRE_EXTINGUISH`, shows `SPLASH` particles on the summoner and
  `clearFire()`. No water block is placed (the Nether evaporates water; the splash is visual).

**Amended by the Parts 4–5 spec §6.1, §6.2 and §6.3:** the Nether posture row is in §6.1. The
fireball is caught at its impact (`ProjectileImpactEvent`), for hits on the player or within 4
blocks of them, and its new owner is the summoner, not the vex. The bucket plays once and only if
the player got out of the lava.

### 6.3 End

- **Guard most:** orbit 0.5–1.5 blocks, scan every tick (its own tick hook, §3.4).
- **Crystals:** while the dragon fight is active (`level.getDragonFight() != null` and the dragon
  is alive), `EndCrystal` entities within 32 blocks are targets of `AttackCrystalGoal`: fly to
  within 2 blocks and `crystal.hurt(level, mobAttack(vex), 1)`, which vanilla turns into the
  crystal's explosion. Bound Vexes take no damage from an End crystal's explosion
  (`LivingIncomingDamageEvent` cancelled when the source is an explosion whose direct entity is an
  `EndCrystal`; §13).
- **Critical hits:** every melee hit by a Bound Vex in the End does **3×** damage, with `CRIT`
  particles on the target and `PLAYER_ATTACK_CRIT`.
- **Void catch + Notch apple:** when the summoner's `y < level.getMinY() − 4` (past the island's
  edge) or they are falling with no block within 64 below (a downward `clip` of 64 blocks hits
  nothing) and `y < minY + 16`: the nearest vex teleports the
  summoner to the nearest standable block top within 32 blocks horizontally, searched outward
  from the summoner's column, else to the summoner's **last ground position** (§3.3); velocity
  zeroed, `fallDistance` zeroed; the vex is snapped beside them. Then the vex conjures an
  **enchanted golden apple** and feeds it: `stack.get(CONSUMABLE).onConsume(level, player,
  copy)` applies the vanilla effects and eating sound, with no item spent. No cooldown (Will).

**Amended by the Parts 4–5 spec §6.1, §7 and §6.4:** the End posture row scans every 2 ticks, not
every tick (§6.1). The crystal goal has its own start and end rules, and a vex's crystal blast
neither hurts nor knocks back the summoner's side (`ExplosionKnockbackEvent`, §7.1). Crits are
melee only, tripled in `LivingIncomingDamageEvent` (§7.2). The void catch does not apply while
flying, uses a 64-block rescuer range and a height-map search, and dismounts a rider (§6.4).

## 7. Casting-related plumbing for Part 6

**Amended by the Parts 6–7 spec §2.1, §3:** the packet is `EvokingRequest(Kind, byte)`; the team-move
record lives in `TeamMoves`; J always cycles and the server answers K, with two more refusals.

### 7.1 The request packet grows

`EvokingRequest` becomes `EvokingRequest(Kind kind, byte arg)`: `CAST(spell)` as before, and
`TEAM_MOVE(move)` for the manual team-move key (§7.3). Still one packet, client → server, a
request the server validates in full. `CLAUDE.md`'s rule text gains "(two request kinds)".

### 7.2 Team-move state

`BoundVex.TEAM_MOVE` (synced byte): `NONE`, `SHIELD`, `MULTISHOT`, `EVADER`, `RAID`. Per summoner,
`EvokingCaster` keeps a transient `TeamMoves` record: the active move, its roster (vex UUIDs),
its enemy, its start tick. One move at a time per summoner.

### 7.3 Setting and keys

`ServerConfig.TEAM_MOVES`: enum `AUTOMATIC` (default) | `MANUAL`.

- `AUTOMATIC`: the triggers in §8 start moves; the keys below only show
  `message.spellcraft.team_moves_automatic` ("Team moves are automatic").
- `MANUAL`: no automatic triggers. Two new key mappings in the Spellcraft category: **J**
  (`key.spellcraft.team_move_cycle`, "Evoking: Next Team Move", `InputConstants.KEY_J` = 13)
  cycles the selected move on the client (Shield → Multishot → Evader → Raid → Shield) and shows
  it in the action bar from a client-side lang string ("Team move: Shield"); **K**
  (`key.spellcraft.team_move_go`, "Evoking: Team Move!", `KEY_K` = 14) sends
  `TEAM_MOVE(selected)`. The server starts the move if its roster can be filled and a suitable
  enemy (the move's own enemy rule, §8) is within 16 blocks of the summoner; else
  `message.spellcraft.team_move_unavailable` ("Not enough vexes, or no enemy for that move").
  Scancodes 13 and 14 appear in no `Options.java` binding, plain or F3-chord. (G was the first
  idea; it is vanilla's `key.quickActions`, scancode 10, and H is the F3-chord
  `key.debug.showAdvancedTooltips`, 11.)

## 8. Part 6: team moves

**Amended by the Parts 6–7 spec §2 and §4:** free vexes, the trigger order (a ≥ 99 enemy without 12
free vexes falls through to a Shield), the end rules and each move's behavior in buildable terms.
The "H key" below is K.

Common rules. A move needs a roster of Bound Vexes of the summoner that are alive, not staying,
not Casters (§9.1; Casters join a Raid, below), and not in another move. The trigger vex "alerts"
the others: they get `TEAM_MOVE` set, conjure the move's kit (stashing given gear), and the
summoner sees the overlay `message.spellcraft.team_move_<name>` ("Your vexes form a Shield!").
Move gear is **conjured** (vanishes when the move ends, cannot be taken, never drops; §13). A
move ends when its enemy is dead or farther than 48 blocks from the summoner, when the hurt one is
healed (Evader), or after its time limit; then the kit vanishes, stashes return, `TEAM_MOVE` is
`NONE`, and a 30-second cooldown per move starts.

| Move | Trigger (automatic mode) | Roster | Kit (conjured) | Behavior | Limit |
|---|---|---|---|---|---|
| **Shield** | A hostile with `maxHealth ≥ 31` enters any vex's guard area | 3, or 9 when ≥ 9 are free | 2 shields (both hands) | Fly at half speed toward the enemy; **lunge bash**: from up to 7 blocks, dash in, hit for 4 + knockback 1.5 (`SHIELD_BLOCK` sound), dash back out to 6 blocks; repeat every 40 ticks. Shields block as §4.2. | 120 s |
| **Multishot** | A hostile with `ATTACK_DAMAGE ≥ 6` takes a Bound Vex as target | That vex + 2 | Crossbow with Multishot | Keep 6–8 blocks from the enemy, shoot every 40 ticks (three arrows, vanilla Multishot). | 120 s |
| **Evader** | The summoner or an ally within 16 blocks drops below half health | 1–3 (as many as free) | 2 shields | Orbit the hurt one at 2 blocks; any hostile within 3 blocks of them is bashed away (knockback 2); any projectile within 2 blocks heading at them has its velocity reversed. | until the protected one is above half health, or 60 s |
| **Raid** | A hostile with `maxHealth ≥ 99` enters any vex's guard area | 12 (all; Casters included) | Random per vex from the raid kit: netherite or diamond sword, axe or mace; trident; crossbow with firework rockets; shield (offhand); netherite or diamond helmet, chestplate, leggings. Casters conjure a **Mirror scroll** (§9) and cast it on the summoner. | Gather in a 3 × 4 grid 8 blocks from the enemy facing it (2 s), then charge together; crossbow vexes fire rockets (explode on hit) from 8 blocks. | 120 s |

Thresholds pick vanilla mobs: ≥ 31 health is an Enderman (40), Piglin Brute (50), Ravager (100);
≥ 99 is a Ravager, Warden, Wither, Ender Dragon. A ≥ 99 enemy starts a Raid, not a Shield (Raid
checks first). In `MANUAL` mode the H key starts the selected move against the nearest enemy that
fits its rule (the K key).

## 9. Part 7: Casters, Mirror, Ghostrail, villager ghosts

**Amended by the Parts 6–7 spec §6–§10:** the Caster's goals and 20-tick wind-up; durations as
constants; the Mirror effect ends with its last image and confusion applies to any mob; the
Ghostrail Mixin target, shared flight, `/kill` and void damage still landing, mob steering; scroll
drops by vanilla's drop chance; only monster kills leave ghosts, one ghost per player.

### 9.1 The Caster

At summon, each Bound Vex rolls **1 in 3** for `CASTER`. A Caster:

- wears a **torn purple-and-magenta robe**: `BoundVexRobeLayer` draws
  `textures/entity/bound_vex_robe.png` over the body and arms (ragged lower edge painted in the
  texture, alpha-cut). Will judges the look.
- never melees or shoots: its target selector is the same but its charge goal is replaced by
  `CasterGoal`, which keeps 4–6 blocks from any target and casts.
- casts, with the evoker's casting look (arms up, `ENTITY_EFFECT` hand particles in purple, the
  casting sounds), holding a conjured scroll:
  - **Mirror** on the summoner when ≥ 2 hostiles are in its guard area and no Mirror images of the
    summoner are alive; once per **6 minutes** per Caster.
  - **Ghostrail** on the summoner (or on a Bound Vex of the same summoner) when that one is below
    a quarter of its health or is inside a solid block; once per **4 minutes** per Caster.
- is still a Bound Vex for everything else (follow, stay, gear it is given, portals, rescues). It
  takes part only in Raids among the team moves.

**Amended by the Parts 6–7 spec §6.2:** a Caster tries Ghostrail before Mirror, and its numbers live in the pure `CasterRules`.

### 9.2 The two spells (framework spells)

Both are real spells: declarations, effects, scrolls, glyphs, lang, scribing pool, cast rules,
exactly the Infinity recipe. Players cast them by scroll. The closed lists grow:
`SpellDeclarations.ALL` and `Spells.ALL` become `[UPSIDE_DOWN, INFINITY, TRAILING, EVOKING,
MIRROR, GHOSTRAIL]`; `ModItems` registers both scrolls; `SpellDeclarationsTest`,
`CastRulesTest.decidesEveryCase` and `SpellAssetsTest` gain rows for both; the scribing tests'
**top-tier pool now holds two spells** (Infinity and Ghostrail), so any test that expects the
top roll to be an Infinity scroll accepts either, and the lower pool gains Mirror.

**Amended by the Parts 6–7 spec §7.1:** `Spells.cast` applies an effect for any caster, and `Spells.refusedFor` is the one creative refusal for casters that are not scrolls.

| | Mirror | Ghostrail |
|---|---|---|
| `SpellRules` | `mirror`, `AMAZING`, `REFUSE`, dispellable, not redundant in creative | `ghostrail`, `EXTREME` (the top tier: 4 minutes unhurt is the strongest thing in the mod), `REFUSE`, dispellable, redundant in creative (creative already flies and is unhurt; spectator too) |
| Duration | 6 minutes | 4 minutes |
| Glyph ink | magenta `#E066FF` | pale `#DDE6FF` |
| On apply | Spawn 13 **Mirror images** (§9.3) of the caster around it (ring cells, Chebyshev 2–3, like the vexes). | `noPhysics` + flight + no damage + trail (§9.4). |
| On end (expiry, dispel, milk) | The images poof (`POOF` particles, no sound). | Push out of solid blocks, stop flight, clear `fallDistance`. |

**Who else casts them.** Besides players and Casters:

- **Evokers** spawn with a scroll in the **offhand** 1 time in 4 (Mirror or Ghostrail, even odds);
  **vindicators and pillagers** 1 time in 10. The scroll shows in the hand. Such a mob casts when
  it first gets a target: Mirror makes 13 images of itself (the evoker side of §9.3; cooldown 6
  minutes); Ghostrail lasts **2 seconds** on a hostile mob (`GhostrailEffect` reads the carrier: a
  hostile gets 40 ticks), enough to step through a wall at you; cooldown 30 seconds. On death the
  scroll drops 1 time in 2 (the loot path for players; §13).
- **Villager ghosts** have Ghostrail permanently (§9.5) and cast Mirror on you.

### 9.3 Mirror images

`MirrorImage` (`spellcraft:mirror_image`, `LivingEntity` subclass, not a `Mob`): synced
`copiedType` (`EntityType`) and, for players, a `ResolvableProfile` of the caster. Health 10, no
armor, no attack, 6-minute life (`setLimitedLife` of its own), wanders within 6 blocks of the
caster at walking speed, faces where the caster faces when idle.

- **Looks:** `MirrorImageRenderer` keeps a client-side **stand-in** entity of `copiedType`
  (`type.create(clientLevel, MOB_SUMMONED)`, or a client avatar with the caster's skin for players),
  copies the image's position, rotation and hurt time into it each frame and renders that through
  `EntityRenderDispatcher`. A hit image flashes red and is knocked back like any `LivingEntity`
  (vanilla); at 0 health it does the vanilla death fall and poofs. No drops, no XP.
- **Confusion:** `LivingChangeTargetEvent`: when a hostile mob picks the caster as target and
  live images of that caster exist within 16 blocks, the target is swapped with probability 13/14
  for a random one of them. The player is never redirected (a player picks their own target), so
  an evoker's images only confuse by sight.
- Images are allies of the caster (`Allies`: `MirrorImage.caster == owner`), so the caster's own
  spells spare them.

**Amended by the Parts 6–7 spec §7.2:** the stand-in is a mannequin whose skin the renderer reads from the skin cache; the effect ends with its last image; a carrier's death poofs its images; confusion applies to any mob, not only hostile ones.

### 9.4 Ghostrail's mechanics

`GhostrailEffect` (a `MobEffect` with a tick):

- **No physics:** `Player.tick` sets `noPhysics = isSpectator()` every tick (`Player.java:243`), so
  a `@ModifyExpressionValue`/`WrapOperation` **Mixin on that call** returns true while the player
  has Ghostrail (common Mixin package; no event runs between that assignment and movement). On
  the client the same Mixin applies to `LocalPlayer` (it reads the synced effect), so movement
  does not rubber-band. Non-player carriers get `noPhysics = true` and `setNoGravity(true)` on
  apply and the reverse on removal.
- **Flight:** on apply, `abilities.mayfly = true` (+ `onUpdateAbilities`); on removal, the game
  mode's abilities are restored (`gameMode.updatePlayerAbilities`). Flying through walls is
  spectator-like but the player stays in survival (hunger, inventory, no mob transparency).
- **No damage:** `LivingIncomingDamageEvent` cancelled for a carrier, except `GENERIC_KILL`
  (`/kill`).
- **Trail:** each tick, 2 `WHITE_SMOKE` particles at the carrier's previous position (server
  `sendParticles`).
- **Ending inside a block:** search outward (up to 8 blocks) for a free 1×2 space and teleport
  there; clear `fallDistance`.
- **Duration by carrier:** 4 minutes for players and Bound Vexes; 40 ticks for hostile mobs
  (§9.2); infinite for villager ghosts.

**Amended by the Parts 6–7 spec §7.3:** the Mixin wraps the first `isSpectator()` call and only survival and adventure players get it; one shared abilities sync (`AbilitySync`) replaces the per-spell grants; `/kill` and void damage still land; hostile mobs are steered by a chase goal.

### 9.5 Villager ghosts

- **Record:** `LivingDeathEvent` for a `Villager` killed by anything that is not a player or a
  player's side (same test as mercy's "player's side", §2.3) writes a **ghost record** into
  `VillagerGhosts` (`SavedData` per level): death position, the villager's profession and type for
  the look, a ghost UUID. At most 32 records per level; the oldest is dropped.
- **Appear:** every 20 ticks, for each player with Evoking: if a record's death position is within
  48 blocks and the player is **in need** (below half health, or ≥ 3 hostiles within 8 blocks)
  and that record has no live ghost, spawn `VillagerGhost` (`spellcraft:villager_ghost`) at the
  death position. It flies (no-clip move control like the vex's) to the player at 2 blocks.
- **Help:** casts **Mirror** on the player if no images of the player live (6-minute cooldown per
  record), and **Ghostrail** on the player if below a quarter health or stuck (4-minute cooldown).
  Casting look as the Caster's.
- **Disappear:** when no hostile is within 16 blocks of the player for 10 seconds, or the player
  leaves 64 blocks, the ghost fades (`POOF`, `VEX_DEATH` at low volume) and `discard`s. The record
  stays, so it can come back.
- **Ghostrail forever:** the ghost carries an infinite Ghostrail (invulnerable, no-clip, trail).
- **Looks:** `VillagerGhostRenderer` draws the villager model with the recorded profession skin at
  alpha 0.6 and a pale tint (`RenderType.entityTranslucent`). Not a `Mob`, no AI beyond the flight
  goal; nothing targets it (not `attackable`).

**Amended by the Parts 6–7 spec §9:** only monster kills leave records, a ghost needs a hostile within 16 blocks as well as the need, one ghost helps a player at a time, and the ghost moves by position each tick.

## 10. Error handling

- **No summoner online, alive, in this level and not a spectator:** a Bound Vex idles; interactions `PASS`; it
  fights nothing (`isPrey` false). Vanilla would despawn a far monster; `removeWhenFarAway` is false.
- **Summoner dead:** as offline until respawn (§3.3).
- **Cap counting across dimensions:** only loaded levels and entities count; a player who parks 12
  vexes in an unloaded chunk can summon more. Accepted (§13).
- **Peaceful:** the Bound Vex type is built with `notInPeaceful()`, so `EntityType.create` returns
  null there; `request` refuses first (round one, now via `canSpawn`). A loaded world switched to
  Peaceful **removes** Bound Vexes as vanilla removes monsters (`Mob.checkDespawn` discards a
  `notInPeaceful` type, `Mob.java:715-716`); their given gear drops (§4.5 drop rule runs in `remove` for
  `RemovalReason.DISCARDED` too). Noted for the playtest.
- **Gear given to a vex that dies in lava:** items burn as vanilla drops do. Accepted.
- **Cure interrupted** (zombie villager dies or is unloaded, or the vex dies, is removed or is
  unloaded): the frozen zombie is unfrozen; a converting one keeps converting (vanilla). The
  goal's `stop` covers only the first two; the freeze is released whenever the curing vex is gone
  (§5.1). **Amended by the Parts 4–5 spec §2.2 and §2.3:** an interruption aborts the cure
  cleanly (the zombie villager is unfrozen and unmarked), and a stale freeze is released by the
  mark's age on the zombie villager's own tick.
- **Bribe target saved mid-flee:** `BRIBED` is persistent; the flee goal is reinstalled on join.
- **Portal with staying vexes:** they stay; the summoner's count still includes them (loaded).
- **Rescue pushes vs. anti-cheat:** server-set velocity on a player uses `hurtMarked` so the
  client receives it; the void teleport is a server `teleportTo`. **Amended by the Parts 4–5 spec
  §5:** the flag is `syncVelocity` (`hurtMarked` does not exist in 26.3).
- **Ghostrail ending inside bedrock or deep stone:** search up to 8 blocks for a free space; if
  none, teleport to the carrier's last ground position (tracked as §3.3 for players with Evoking,
  and by the effect itself for any carrier, each tick they are on ground). **Amended by the Parts 6–7
  spec §7.3 and §10:** the fallback is the spot where the effect started (positions during the effect
  are inside walls), and if that is no longer free the carrier stays put and suffocates as vanilla would.
- **Ghostrail on a creative player:** redundant; the cast is refused like Infinity's rule.
- **Mirror images in a non-ticking chunk:** they are entities with their own life counter; a
  reloaded image keeps its remaining life and poofs when due.
- **A mob with a scroll loses its target before casting:** it casts the next time it gets one;
  cooldowns are per mob (attachment).
- **Team move with a roster member dying:** the move continues with the rest down to 1; a Raid
  below 12 continues (it was legitimately started).
- **Manual team move in automatic mode or vice versa:** the overlay messages of §7.3.
- **`/summon spellcraft:bound_vex`:** no summoner, idles; `/summon` of an image or ghost without
  data: discards itself on first tick (no caster/record).

## 11. Testing

Methodology as round one: pure core TDD with jqwik properties, GameTests for server rules,
`runData` for client wiring, Will for everything visual. The pure core gains:

- `FangFootprints` (`spell.rules`): columns of a box intersected with a square; dedupe; the caster
  column skipped. Properties: every column is inside the square; a box within one block gives one
  column; a box `w` wide gives at most `(⌈w⌉ + 1)²` columns (a 3-wide box across block edges touches
  4 per axis). The collapse of shared spots is not here: it needs the floor, so `Fangs.snapshot` does it.
- `GuardArea` (`spell.rules`): the 8×8×7 box math and the 7.25 reach test; properties: symmetric,
  a point at the box edge is inside, a point at reach + ε is out. **Amended by the Part 3 spec §8:**
  plus the stay box (`containsStaying`) and `FOLLOW_RANGE`, and the pure `SegmentDistance` (a
  point to a segment, for the hold-fire line).
- `GolemMercyRule.clamp(health, damage)` (`spell.rules`, pure; `GolemMercy` in `spell` is the event
  subscriber): `min(damage, max(0, health − 25))`, total (NaN → 0; infinities follow the arithmetic); property: health
  after is never below 25 when it was above, and a hit at ≤ 25 does 0.
- `VexCap.toSummon(count)`: 3 / `12 − count` / 0; property: count + result ≤ 12.
- `TeamMoveRules` (pure): roster sizes from free count (Shield 3 or 9, Evader 1–3, Raid 12 or
  none), trigger thresholds (31, 99, attack 6), the "Raid before Shield" rule.
- `GhostrailDuration.of(carrierKind)`: 4800 / 40 / infinite.

GameTests per part (the plan names them; the shape):

- **Part 1:** fang per target column (one zombie → one fang; a ravager → its footprint; empty
  cells → none); 14-down scan finds a target 14 below and not 15; chase fang appears once under a moved
  target and never twice; villager, trader, golem are allies; a golem whose target is the player
  is not; mercy clamps a fang bite at 25 and the golem forgives (`isAngryAt` false, target null);
  trail never lights a golem; a golem never takes a summoned vex as target (event cancelled).
- **Part 2:** V spawns `bound_vex` ×3 with the summoner, no `life_ticks`; cap: 12 → `CAPPED` and
  the message; 10 → 2 spawned; follow teleport at 13 blocks; stay toggle; portal follow into a
  prepared Nether pocket; hurt-by/owner-hit/ally-hurt/ally-fight targeting cases; hostile in area
  attacked, pig ignored, creeper ignored unless provoked; wolf takes the vex's target; flee from a
  swelling creeper; dodge; repair heals a hurt ally golem by 25 per tap with the sound event.
- **Part 3** (the full list is the Part 3 spec §8): each slot's give/return; the take-back; shield block from the front cancels damage
  and from behind does not; support potion thrown at a hurt ally, harmful at a target; stack never
  shrinks; drops on death include everything given and nothing conjured.
- **Part 4:** cure timeline (apple, potion entity, `isConverting` with starter = summoner, NoAi on
  then off at finish); bribe success → flee goal, discard out of sight; failure → `BRIBE_TRIED`.
  **Amended by the Parts 4–5 spec §9:** the full Part 4 test list is there.
- **Part 5:** per dimension (Nether/End pockets as round one's E16): lava push, drowning push,
  fireball owner swap and reversal, crystal hurt → explosion, no explosion damage to the vex,
  3× damage in the End, void teleport + apple effects. **Amended by the Parts 4–5 spec §9:** the
  full Part 5 test list is there.
- **Part 6:** each trigger starts the right move with the right roster and kit; manual mode: K
  with no enemy → message; move end restores stashes; cooldown. **Amended by the Parts 6–7 spec §5:** the full
  Part 6 test list is there.
- **Part 7:** Caster roll is 1/3 over 300 summons (statistical bound); Mirror spawns 13 images
  that die at 0 and poof at 6 minutes; redirect probability; Ghostrail noPhysics/mayfly/damage
  cancel/push-out; mob carriers 40 ticks; evoker spawns with a scroll 1/4 (bound); ghost record on
  villager death by zombie and none by the player; ghost appears when in need and fades. **Amended by
  the Parts 6–7 spec §11:** the full Part 7 test list is there.

`runData` proves the new renderers, layers and key mappings load. **Amended by the Parts 6–7 spec §11:**
`runData` never fires `RegisterRenderers`, so it proves only that the client code compiles and the specs load;
the first render of each new renderer, layer, model and particle provider is the playtest's. ArchUnit rules unchanged;
`pitest` after each pure-core change with `docs/mutation-testing.md` rows.

**Playtest items** (added per part to `docs/playtest-checklist.md`, a new "Evoking II" section):
Y casts, F swaps; fangs only under mobs and the chase; vexes follow, teleport, stay; portal follow;
armor shows and fits; the cure looks right; the bribe's emerald and silent exit; fireball return;
void catch; each team move's look and message; the robe; images look like you; the ghost's fade.

## 12. Docs and delivery

Per part: README (spells and keys), mod description, playtest checklist, `docs/api-notes.md`
(every new hook verified: `LivingChangeTargetEvent`, `LivingDamageEvent.Pre`,
`PlayerChangedDimensionEvent`, `Entity.teleport(TeleportTransition)`, `Consumable.onConsume`, the
`Player.tick` noPhysics line, `startConverting` AT), `CLAUDE.md` (packet rule wording, the Mixin
list gains the Ghostrail one), `docs/mutation-testing.md`. `mod_version` gets a patch release per part when Will asks for one (Part 2: `1.2.1`; Part 3:
`1.2.2`), and `1.3.0` with a tag at the end of Part 7.

## 13. Decisions Will did not state

| Decision | Choice | Why, and the alternative |
|---|---|---|
| Y key migration | Rename the mapping so saved options fall back to Y | A rename is the only way vanilla forgets a saved binding; alternative: keep the name and let old saves stay on F. |
| Fang ripple | None; all target fangs snap at warmup + 0 | Scattered fangs have no center to ripple from. |
| Chase fang | One per target, within 16 blocks of the caster | Will: "summon another fang where the fast mob is"; one keeps a cast finite. |
| Mercy on Will's own sword | Clamped too | "Your side" includes you; the point is that a golem survives you. Alternative: only spells and summons. |
| Trail vs. golem | Never lit | Fire damage has no attacker; mercy cannot clamp it. |
| Every golem is an ally | Yes, village-made too | Will chose X. |
| Cap counting | Loaded entities only | Unloaded chunks cannot be scanned without loading them. |
| Partial summon at 10–11 | Summon the remainder up to 12 | Offered to Will with the cap and not objected to; alternative: refuse unless all three fit. |
| Mob category | `MONSTER` | Keeps vanilla bookkeeping; golems and villagers handled by event and type identity. |
| Wolves join | Only the summoner's tamed wolves, 16 blocks, no target yet | Wolves are the only fighting tamed mob; golems already fight hostiles. |
| Dodge roll | Always | Only arises against a mad golem; a roll adds nothing. |
| Repair scope | Any hurt ally golem in the guard area | The ingots are free; "mercy" reads as medic. |
| Attacker memory | The first fresh one in §3.4's order, at most 100 ticks old | One pass, and the order already says what matters most; alternative: compare four ages for the newest. |
| Other players' vexes | Treated like their player: prey for the grudge goal under the PvP rule, never patrol or ally-fight targets | With PvP off, two players' vexes would otherwise fight; an offline owner falls back to the level's `pvp` setting. A vex answers a hit, but never starts a fight on sight. |
| Experience | None | Summon-and-kill would farm 3 XP per vex, 3 per press. |
| Dodge timing | After the swing | Golems never target Bound Vexes, so the swing is never aimed at the vex; a pre-swing read would need the golem's target prediction. |
| Neutral monsters | Patrol targets them (zombified piglins, endermen) | `Enemy` includes them, so a vex may start a fight with a piglin horde. Playtest item. |
| Portal ring cells | May coincide | Vexes clip through each other and blocks; two on one spot spread as they move. |
| Following after a job | A vex finishes its wander leg or repair (up to about 3 s) before following | Goals end on their own terms; cutting a repair short would waste the claim. |
| Unloaded vexes | Follow and ride portals only while their chunk ticks | An unloaded vex runs no goals; it resumes when its chunk loads with the summoner there. |
| Ingot kept mid-repair | Until Part 3, a vex unloaded, reloaded or portaled mid-repair keeps the ingot | The goal holds the given item in a field; Part 3's stash makes it survive saving. |
| Tridents | Melee only | Throwing would need a return rule; nothing is used up anyway. |
| Shield from the player | Offhand, blocks from the front | Will chose A+C; shields are handable. |
| Armor and shield take-back | Not possible | Will: a click takes the weapon; nothing else. (The click is the plain one since the Part 3 spec.) |
| Cure duration | 400 ticks (vanilla takes 3–5 min) | Will: 20 seconds of smoke. |
| Cure visuals | Golden apple, real splash potion, green + white particles | Will's choreography. (Since the Parts 4–5 spec: the potion is weakness-colored with no effects, so it weakens nobody.) |
| Bribe sound | `VILLAGER_AMBIENT` | The villager "hmm". |
| Bribe odds | 2 in 3 | Will: "1/1.5". |
| Bribed vex exit | Out of every player's sight, then `discard`, silent | Will's D. |
| Rescue pushes | Velocity, not teleport | Feels like being pulled; the void catch teleports because there is nothing to push against. (Since the Parts 4–5 spec: pushes add to the player's velocity and are sent with `syncVelocity`.) |
| Crystal explosion vs. vexes | No damage to Bound Vexes | Otherwise every crystal hit kills the vex. (Since the Parts 4–5 spec: a vex's blast also neither hurts nor moves the summoner's side.) |
| End crits | 3× | "+++" read as "much stronger". |
| Shield / Raid thresholds | 31 / 99 max health | Will's numbers; vanilla mobs listed in §8. |
| Multishot trigger | Attacker `ATTACK_DAMAGE ≥ 6` | "Strong melee monster". |
| Move gear | Conjured (**Amended by the Parts 6–7 spec §4 and §14:** the kits are made per vex, rolled where the parent listed choices, and all gear is unbreakable) | Will's A. |
| Move time limits | 120 s (Evader 60 s), 30 s cooldown | Keeps a move from running forever. |
| Manual keys | J cycles, K goes | Unbound in vanilla (scancodes 13, 14); G and H are taken (§7.3). |
| Caster odds and look | 1 in 3; torn purple-and-magenta robe | Will. |
| Ghostrail tier | `EXTREME` | Four minutes unhurt is the strongest effect in the mod. |
| Ghostrail on hostiles | 2 seconds | Will. |
| Scroll carriers | Evokers 1/4, vindicators and pillagers 1/10, drop 1/2 | Gives players a way to find the scrolls. |
| Mirror copies | 13 for 6 minutes, health 10 | Will; health is a guess, images die like mobs. |
| Image confusion | 13/14 redirect of a hostile's target | Thirteen copies and one real you. |
| Villager ghosts | Records in `SavedData`, appear when in need, fade, return; 32 per level | Will: appear, disappear, appear again. |
| Ghost trigger | Below half health or ≥ 3 hostiles within 8 blocks (**Amended by the Parts 6–7 spec §9 and §14:** and a hostile within 16 blocks) | "When you need help". |
| Which villagers leave ghosts | Not those killed by a player's side (**Amended by the Parts 6–7 spec §9:** only those killed by a monster) | A murdered villager should not help its murderer. |
| Version | Patch releases per part as Will asks; 1.3.0 after Part 7 | Will tagged Part 2 as 1.2.1 and asked for Part 3 as a patch. |
