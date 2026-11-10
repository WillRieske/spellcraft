# Spellcraft: Evoking II Parts 4 and 5, cure, bribes and world helpers (design)

**Date:** 2026-11-09
**Status:** approved
**Written by:** `/one-shot`, unattended, on 2026-11-09; §10 lists the decisions Will did not state.
**Target:** Minecraft Java 26.3, NeoForge 26.3.0.23-beta, Java 25, ModDevGradle
**Builds on:** [`2026-11-07-evoking-ii-design.md`](2026-11-07-evoking-ii-design.md) ("the parent
spec"), whose Part 4 (§5) and Part 5 (§6) this builds, and
[`2026-11-09-evoking-ii-part-3-gear-and-stay-design.md`](2026-11-09-evoking-ii-part-3-gear-and-stay-design.md)
("the Part 3 spec": the stash, the stay area, the friendly-fire backstop).
**Amends:** the parent spec's §5.1 (the cure's freeze, potion and timeline), §5.2 (what a bribed
vex is to other vexes), §6.1–§6.3 (scan cadence, the velocity flag, who owns a returned fireball,
rescue limits), §10 (the cure's interruption rule) and §11 (Part 4 and 5 tests). The parent
carries inline "Amended by" notes at those places, added in the same change.

## Summary

Two more parts of the Bound Vex design, built one after the other, each with its own plan,
branch and pull request.

**Part 4, cure and bribes.** A Bound Vex that meets a zombie villager does not fight it. It
flies up, holds out a golden apple, throws a splash potion, and the zombie villager stands
still in green and white smoke for twenty seconds and turns back into a villager, who gives you
the cured-villager discounts. It costs you nothing. A Bound Vex that meets an evoker's vex
attacking your side offers it an emerald first: two times in three the vex takes the bribe,
flies off out of sight and is gone; otherwise it is fought as usual.

**Part 5, world helpers.** Your vexes guard you more closely where it is more dangerous: at
night, in the Nether, and most of all in the End. In lava, one of them pulls you toward solid
ground (and in the Nether puts you out with a water bucket). When you are about to drown, one
pushes you up to air. In the Nether, one knocks a ghast's fireball back at the ghast. In the End,
they break the dragon's crystals, their melee hits do triple damage, and if you fall into the
void one catches you, puts you back on the island and feeds you an enchanted golden apple.

Three things in the parent design could not be built as written in Minecraft 26.3 and are
changed here: the velocity flag it names does not exist; a fireball owned by a vex cannot hurt a
ghast; and "scan every 5 ticks" and "every tick" are not reachable on the goal clock.

## Glossary

| Term | Meaning |
|---|---|
| **Bound Vex**, **summoner**, **ally**, **guard area**, **stay area**, **conjured gear**, **stash** | As in the parent and Part 3 specs. |
| **Job goal** | A goal that makes a vex do one task on one subject (repair a golem, cure a zombie villager, bribe a vex, break a crystal). It claims its subject so two vexes never share one. |
| **Claim** | A transient map entry "subject → the vex working on it". A claim whose vex is dead or removed counts as free. |
| **Cure mark** | The saved mark `spellcraft:cure` on a zombie villager a vex is curing: the game time the cure began. While it is there the zombie villager is frozen. |
| **Frozen** | `setNoAi(true)`: the mob neither moves nor attacks. Vanilla still counts down a conversion on a frozen zombie villager. |
| **Bribed**, **bribe tried** | Saved marks on a vanilla vex: `spellcraft:bribed` (it took the emerald and is leaving) and `spellcraft:bribe_tried` (it refused; no vex offers again). |
| **Posture** | How closely a following vex keeps to its summoner and how often it looks for hostiles, chosen from the dimension and the time of day (§6.1). |
| **Rescue** | A vex acting on its summoner in an emergency: the lava pull, the drowning push, the void catch. One vex per summoner at a time. |
| **Rescuer** | The vex picked for a rescue: the nearest living, following (not staying) Bound Vex of that summoner within 16 blocks, in the same level. |
| **Standable spot** | A block position whose block below has a sturdy top face and which, with the block above it, is free of collision and of any fluid. |
| **Last ground position** | The last standable spot a player stood on, with its dimension; kept in memory for every online player. |

## 1. Scope and delivery

| Part | Branch | What lands | Sections |
|---|---|---|---|
| 4 | `feat/evoking-ii-part-4` (from `main`) | The cure; the bribe | §2–§4 |
| 5 | `feat/evoking-ii-part-5` (from Part 4's branch) | Posture; lava, drowning and void rescues; fireball return; crystals; End crits | §5–§8 |

One plan and one draft PR per part. Part 5 is stacked on Part 4 because both add job goals to
`BoundVex.registerGoals` and marks to the same files.

Out of scope: Parts 6–7; any new packet; any config; a version bump (Will asks for a release
when he wants one; the PRs say which version would be next).

## 2. Part 4: curing a zombie villager

### 2.1 Never prey

`BoundVex.isPrey` (the static rule every target test uses) answers false for a
`ZombieVillager`, whatever it did: a Bound Vex never fights one, not even one that hit it or its
summoner. The cure stops such a zombie villager instead (it is frozen from the moment it is
claimed).

### 2.2 The goal

`CureZombieVillagerGoal` (`evoking` package), a job goal at priority 2 with the `MOVE` flag,
shaped like `RepairGolemGoal`.

**Start** (`canUse`, on the vex's scan tick): the vex has a summoner present and **no target**;
a `ZombieVillager` whose feet are in the guard area (the stay area for a staying vex) is alive,
not converting, carries no cure mark and is not claimed by another living vex. The nearest one
is taken: it is claimed, **frozen**, and given the cure mark.

**Timeline.** Once started, the goal runs to its end whatever happens around it: a new target
does not interrupt it (a half-cured zombie villager left standing is worse than one vex out of
the fight for a few seconds). It ends early only if the zombie villager dies or is removed, or
the summoner is gone.

| Step | Lasts | What happens |
|---|---|---|
| 1 Approach | until within 1.5 blocks of the zombie villager's head, at most 100 ticks | The vex flies level with its head (charge speed). After 100 ticks without arriving the goal gives up (step "abort" below). |
| 2 Apple | 30 ticks | `conjure(MAINHAND, golden apple)`. Every 4 ticks: `SoundEvents.GENERIC_EAT` at the zombie villager and 5 `ITEM` crumb particles of a golden apple at its mouth; the vex swings its arm every 10 ticks. |
| 3 Potion | 20 ticks | `conjure(MAINHAND, the cure potion)` (it replaces the apple in the same conjured slot). The vex turns its head 60° to one side for the first 10 ticks; at tick 10 it throws a `ThrownSplashPotion` of the cure potion at the zombie villager's feet. |
| 4 Begin | at the end of step 3 | `startConverting(summonerUuid, 400)` on the zombie villager; `dismiss(MAINHAND)`; the claim is released; the goal ends. The vex is free again. |

**The cure potion** is a splash potion with **no effects** and weakness's color (a
`PotionContents` with a custom color and an empty effect list): the throw, the glass break and
the gray splash look like a weakness potion, and nobody standing near (the summoner, the vex, a
villager) is weakened. Vanilla's `startConverting` removes weakness from the zombie villager
anyway, so a real weakness potion would only have hurt bystanders.

**Abort** (the zombie villager died, the approach timed out, or the summoner is gone before
step 4): the vex's hand is dismissed, the claim released, and the zombie villager, if it still
exists, is unfrozen and its cure mark removed.

`startConverting` is private in vanilla (`ZombieVillager.java:196`): one access-transformer line
makes it public. The conversion starter is the summoner's UUID, so the cured villager gives the
summoner vanilla's discounts, provided the summoner is in that level when the conversion ends
(vanilla's rule).

### 2.3 The smoke, the freeze and its release

A zombie villager's cure outlives the goal, so it is driven from the zombie villager itself.
`CureEvents` (`evoking`, an `@EventBusSubscriber`) listens on `EntityTickEvent.Post` for a
`ZombieVillager` carrying the cure mark, on the server:

- **Converting:** each tick, `sendParticles` of `HAPPY_VILLAGER` and `CLOUD`, 4 each, spread
  over its body. It stays frozen.
- **Not converting, and the mark is older than 300 ticks** (the vex died, was unloaded or never
  finished; steps 1–3 take at most 150): unfreeze, remove the mark. This is what releases a
  freeze whose goal can no longer run `stop`; nothing else has to notice that the vex is gone.
- **Not converting, mark younger:** nothing (the goal is still working).

When the conversion finishes vanilla replaces the zombie villager with a villager; the mark goes
with the old entity. The new villager must not be frozen: a GameTest asserts it has AI, and if
vanilla's conversion copies `NoAI`, `CureEvents` clears it on NeoForge's
`LivingConversionEvent.Post`.

The mark is saved (a zombie villager unloaded mid-cure keeps converting when it loads again,
with its smoke, and is released by the same rules). It holds the game time the cure began.

## 3. Part 4: bribing a vex

### 3.1 The goal

`BribeGoal` (`evoking`), a job goal at priority 2 with the `MOVE` flag. Because priority 2
outranks the charge goal, a vex that already targets the enemy vex bribes first.

**Start** (on the scan tick): a summoner is present; a vanilla vex (`EntityTypes.VEX` exactly,
never a Bound Vex) whose feet are in the guard area is alive, carries neither mark, is not
claimed, and its target is the summoner or an ally of the summoner. Whether the Bound Vex has a
target does not matter.

| Step | Lasts | What happens |
|---|---|---|
| 1 Approach | until within 1.5 blocks, at most 100 ticks | Fly to it. A timeout ends the goal with no mark (it may try again). |
| 2 Offer | 20 ticks | `conjure(MAINHAND, emerald)`; `SoundEvents.VILLAGER_AMBIENT` once at the start; the vex looks at the other vex. |
| 3 Roll | | One roll from the pure rule `BribeRule` (§9): **2 in 3** it is bribed, **1 in 3** it refuses. `dismiss(MAINHAND)`; the claim is released. |

**Bribed:** the vex gets the `bribed` mark, its target is cleared, every goal and target goal
it has is removed, and `FleeFromViewGoal` is added (§3.2). **Refused:** it gets the
`bribe_tried` mark and is prey as before.

The goal ends early, with no mark, if the other vex dies or leaves the guard area.

### 3.2 A bribed vex

- It is **never prey** (`BoundVex.isPrey` answers false for a mob carrying `bribed`), so no
  Bound Vex attacks it while it leaves. Golems, wolves and players may still hurt it; it does
  not fight back (it has no goals).
- `FleeFromViewGoal`: each tick it flies directly away from the nearest player (through blocks,
  as vexes do). When it is at least **12 blocks** from every player within 64 blocks and none of
  those players has line of sight to it, it is `discard()`ed: no sound, no particles, no drop.
  After **200 ticks** it is discarded anyway.
- Saved and reloaded, it keeps the mark; `EntityJoinLevelEvent` strips its goals again and
  reinstalls the flee goal (the 200 ticks start over).

## 4. Part 4: error handling

- **Two vexes, one subject:** the claim maps (one per job) allow one; they are cleared when the
  server stops, like the repair claims.
- **The curing vex dies, is discarded or unloads in steps 1–3:** its hand items were conjured,
  so nothing drops; the zombie villager is released by the 300-tick rule (§2.3).
- **The summoner leaves mid-cure after step 4:** the conversion finishes; no discount (vanilla).
- **A zombie villager in sunlight:** it burns as vanilla; a frozen one cannot walk to shade.
  Accepted (the cure takes 20 seconds; a helmeted or shaded one survives). Playtest item.
- **A vex with a stay order:** cures and bribes only subjects inside its stay area, and returns
  to its post afterwards (`HoldPostGoal`).
- **Peaceful:** zombie villagers and vexes do not exist; nothing runs.
- **A bribed vex in a sealed room with the player:** it flies through the wall; if a player keeps
  it in sight for 200 ticks it is discarded in view. Accepted.

## 5. Part 5: what changes for every helper

- **Where code runs.** Things a vex does to a *subject it flies to* are job goals (crystals).
  Things done *for the summoner in an emergency* are driven from the summoner: `VexRescues`
  (`evoking`) has a `tick(ServerPlayer)` called from `PlayerTickEvent.Post` (GameTests call it by
  hand, as they call `EvokingCaster.tick`, because mock players get no player tick). It detects
  the need, picks the rescuer, and hands it the work through `RescueGoal`.
- **Who is not rescued.** A player who cannot be hurt by the danger gets no rescue: creative and
  spectator players never; a player with Fire Resistance gets no lava pull; a player with Water
  Breathing or Conduit Power gets no drowning push.
- **Dimension tests** read `level.dimension()` against `Level.OVERWORLD`, `NETHER`, `END`. A
  modded dimension counts as none of them: the default posture, no helpers.
- **Velocity.** A push sets the player's velocity on the server
  (`player.setDeltaMovement(...)`) and marks it for sending. The parent spec's `hurtMarked` does
  not exist in 26.3; the field is `Entity.syncVelocity` (the plan confirms it reaches a real
  client by reading `ServerEntity`, and Will confirms the feel).

## 6. Part 5: posture, rescues, the fireball

### 6.1 Posture

The pure class `spell.rules.GuardPosture` answers, from a dimension kind (`OVERWORLD`, `NETHER`,
`END`, `OTHER`) and "is it dark outside": the orbit band a following vex keeps and its scan
interval.

| Where | Orbit (blocks from the summoner's feet) | Scan every |
|---|---|---|
| Overworld by day; any other dimension | 2–4 | 10 ticks |
| Overworld when `level.isDarkOutside()` (night, or a thunderstorm) | 1–2 | 4 ticks |
| Nether | 1–2 | 4 ticks |
| End | 0.5–1.5 | 2 ticks |

- `FollowSummonerGoal` takes its band from the posture each time it is asked (the vex leaves the
  band's outer edge alone and flies to a random spot between the inner edge and just inside the
  outer edge, as today). The teleport rule (farther than 12 blocks) is unchanged; a teleported
  vex lands within 2 blocks and may take one more hop into a tight band.
- Every scanning goal reads its interval from `BoundVex.scanInterval()` (the posture's) instead
  of the constant 10: the three target goals and the job goals.
- **Why 4 and 2, not the parent's 5 and 1.** Vanilla asks a goal whether it may start only on
  every second tick, on ticks fixed by the mob's id (api-notes §21). A 5-tick clock keyed that
  way fires every 10 ticks, and nothing can fire every tick. 4 and 2 are the nearest cadences
  the goal clock can keep, and both are faster than asked for the Overworld night and the
  Nether.
- A staying vex has no orbit; its scan interval still follows the posture.

### 6.2 Rescues

`VexRescues.tick(player)` runs each tick for every online, living player in survival or
adventure mode:

1. Record the **last ground position** when the player is `onGround()` on a standable spot.
2. If a rescue for this player is running, leave it to its `RescueGoal`.
3. Otherwise test the needs in this order and start the first that holds and has a rescuer:
   the void (End), lava (Overworld and Nether), drowning (Overworld).

A rescue is a record (kind, rescuer, start tick) in a transient map keyed by the player. It ends
when the need ends, when its time limit passes, or when the rescuer is dead, removed or no
longer has this summoner present; then the map entry goes. The map is cleared for a player on
logout and wholly when the server stops.

`RescueGoal` on the vex (priority 0, added first so it outranks the flee and dodge goals; flags
`MOVE` and `LOOK`) runs while the vex is the rescuer of a running rescue. At its start the vex
is **snapped to the summoner's shoulder** (teleported to 0.6 blocks beside and 1.2 above the
player's feet; vexes pass through blocks) and each tick it is kept there and acts:

| Rescue | Need | Each tick | Ends |
|---|---|---|---|
| **Lava pull** (Overworld, Nether) | `player.isInLava()` | Push toward the nearest standable spot within 6 blocks (searched in the cube around the player, nearest first): velocity = 0.35 toward it horizontally and 0.3 up. With no such spot: straight up at 0.4. | The player is out of lava, or 60 ticks. After a lava pull ends by its time limit, no new one starts for that player for 20 ticks. |
| **Drowning push** (Overworld) | `player.isUnderWater()` and `getAirSupply() < 60` | Velocity straight up at 0.4. | The player's eyes are out of water, or 200 ticks. |
| **Void catch** (End) | §6.4 | One action, in the tick it starts. | Immediately. |

**The Nether's water bucket.** When a lava pull ends in the Nether, whether the player got out
or not: the rescuer conjures a **water bucket** in its main hand for 20 ticks,
`SoundEvents.BUCKET_EMPTY` and `FIRE_EXTINGUISH` play at the player, 12 `SPLASH` particles
appear on the player, and `player.clearFire()`. No water block is placed. In the Overworld the
player keeps burning (Will chose only the pull there).

A rescuer keeps its own target and returns to its duties when the rescue ends. A rescue never
uses a staying vex.

### 6.3 The fireball return (Nether)

Each tick, for a player in the Nether who has a Bound Vex, `VexRescues` looks for a
`LargeFireball` within **4 blocks** of the player that is moving toward them (its velocity has a
positive component toward the player) and was not returned already (its owner is not that
player). If a living Bound Vex of the player, following or staying, is within **7.25 blocks** of
the fireball:

- the vex is snapped to the fireball, swings its arm, and `SoundEvents.PLAYER_ATTACK_KNOCKBACK`
  plays;
- the fireball is deflected with vanilla's `Projectile.deflect`, as a punch does, with **the
  summoner as its new owner** and the vex as the deflecting entity. Its new heading is toward
  the ghast that shot it when that ghast is alive and the vex has line of sight to it; otherwise
  the exact reverse of its heading. Its speed is its speed before.

The summoner is the owner because of two vanilla rules: a ghast takes damage from a fireball
only when a *player* returned it, and a fireball whose owner is removed vanishes. The parent
spec had the vex as owner; a ghast would have been immune to it. A returned fireball therefore
counts as the summoner's own (it also earns vanilla's ghast advancement).

A staying vex that returned a fireball flies back to its post afterwards.

### 6.4 The void catch (End)

**Need:** the player is in the End and either `y < level.getMinY() − 4`, or all of: not on the
ground, moving down, `y < level.getMinY() + 16`, and a 64-block downward `clip` from their feet
(colliders, no fluids) hits nothing.

**Action:** find where to put them: the nearest **standable spot** to the player's column
within 32 blocks horizontally, taken from the height map column by column outward (a column
with no blocks is skipped); else the player's **last ground position** if it is in the End.
With neither, there is no catch. Otherwise: the player is teleported there, their velocity and
fall distance are zeroed, the rescuer is snapped beside them, and the vex feeds them an
**enchanted golden apple**: the apple's own consume effects are applied to the player through
vanilla's `Consumable.onConsume` on a throwaway stack, with its eating sound; the vex shows the
apple in its hand (conjured) for 20 ticks. No item is spent and there is no cooldown (Will).

The rescuer for a void catch may be up to **64 blocks** away (a falling player outruns the
16-block rule).

## 7. Part 5: the End's crystals and critical hits

### 7.1 Crystals

`AttackCrystalGoal` (`evoking`), a job goal at priority 2 with the `MOVE` flag, only in the End
and only while the dragon fight has a living dragon (`level.getDragonFight()` is not null, its
`dragonUUID()` names an entity in the level, and that entity is alive).

**Start** (on the scan tick): the vex has a summoner present and no target; an `EndCrystal`
within **32 blocks** of a following vex, or inside the stay area of a staying one, is not
claimed. The nearest is claimed. The vex flies to within 2 blocks and calls
`crystal.hurtServer(level, damageSources().mobAttack(vex), 1)`, which vanilla turns into the
crystal's explosion. The goal ends; a vex that flew far is brought back by the follow goal's
teleport.

**The explosion and your side.** The explosion's damage names the vex as its cause, so the
Part 3 backstop already cancels it for the summoner, allies and the summoner's other vexes. The
vex itself is not covered by that rule (nothing is its own prey question), so `BoundVexEvents`
also cancels damage to a Bound Vex whose direct source entity is an `EndCrystal`: a Bound Vex
is never hurt by a crystal's explosion, whoever set it off.

### 7.2 Critical hits

In the End, every **melee** hit by a Bound Vex does **3×** damage: in `BoundVexEvents`'s
`LivingIncomingDamageEvent` handler, when the damage's direct entity is a Bound Vex (its own
body, so arrows and potions are excluded) in the End and the hit is not being cancelled by the
backstop, the amount is tripled, 6 `CRIT` particles appear on the victim and
`SoundEvents.PLAYER_ATTACK_CRIT` plays. The golem mercy clamp runs later in the damage pipeline
and still holds.

## 8. Part 5: error handling

- **No rescuer in range:** no rescue; the test repeats each tick.
- **The rescuer dies mid-rescue:** the rescue ends; the next tick may pick another.
- **A push against a ceiling or a wall:** the push continues until its time limit; the player is
  not teleported (only the void catch teleports).
- **A player with Fire Resistance in lava, or riding a strider:** no pull (§5); a ridden player
  is not pushed at all (a passenger's velocity is its vehicle's): no rescue while
  `player.isPassenger()`.
- **Lava or water that is one block deep:** a push of a tick or two; harmless.
- **The fireball's ghast is gone or out of sight:** the fireball is sent straight back along its
  path.
- **Two fireballs in one tick:** each gets its own vex if two are in range; a vex returns one
  fireball per tick.
- **The void catch finds lava or a pillar top as "ground":** a standable spot excludes fluids;
  an obsidian pillar's top is a valid spot.
- **The dragon is dead or was never there:** no crystal goal; crystals a player places to
  respawn the dragon are left alone until the new dragon exists.
- **Mock players and commands:** `/tp` into the void triggers a catch like a fall.
- **A logged-out or dead player:** no tick, no rescue; their map entries are dropped.

## 9. Testing, docs

**Pure core (TDD, jqwik, `spell.rules`):**

- `BribeRule.accepts(roll)` for a roll in `0..2`: true for 0 and 1; the dice come through the
  same small interface the scroll draw uses, so a GameTest scripts the outcome. Property: over
  the three rolls exactly two accept.
- `GuardPosture.of(dimension, dark)`: the table of §6.1. Properties: the inner edge is below the
  outer edge; every scan interval is even and at least 2; a darker or more dangerous place never
  has a wider band or a slower scan than the Overworld day.
- `RescuePush`: the lava-pull velocity from an offset (0.35 horizontal toward the spot, 0.3 up;
  straight up 0.4 with no spot; a spot directly above or below gives no horizontal part and no
  NaN) and the "moving toward" test for the fireball. Properties: the horizontal speed is 0 or
  0.35; no component is NaN for any finite input.
- `VoidRule.needsCatch(y, minY, onGround, movingDown, groundWithin64)`: the two conditions of
  §6.4.

`./gradlew pitest` after each part, with a row in `docs/mutation-testing.md`.

**GameTests, Part 4** (new class `BoundVexCureGameTests`):

- A zombie villager is never taken as a target, not by the patrol and not after it hit the
  summoner; a husk beside it still is.
- The cure: the zombie villager is frozen and marked at once; the vex holds a golden apple,
  then the potion; a thrown splash potion entity appears; after step 4 `isConverting()` is true,
  the vex's hand is back to its sword and the vex can take a target again; a bystander player
  two blocks away has no weakness; with the conversion time shortened by the test
  (`setVillagerConversionTime`), a villager with AI replaces the zombie villager and holds
  positive gossip for the summoner.
- A new target during steps 1–3 does not stop the cure.
- Two vexes, two zombie villagers: one each; two vexes, one zombie villager: one works.
- Release: a marked, frozen zombie villager whose mark is 300 ticks old and that is not
  converting is unfrozen and unmarked; a converting one stays frozen and marked; a marked one
  survives a save and load with the mark.
- Abort: the zombie villager dies in step 2 → the vex's hand is restored.
- The bribe, scripted to succeed: the emerald, then the vanilla vex has the `bribed` mark, no
  target and only the flee goal; no Bound Vex takes it as a target; it is discarded once it is
  12 blocks from the player behind a wall, and after 200 ticks in plain sight.
- The bribe, scripted to fail: `bribe_tried`, the Bound Vex then fights it, and no second offer
  is made.
- A vanilla vex whose target is not on the summoner's side gets no offer; a Bound Vex of another
  player gets none.
- A bribed vex loaded from a save has only the flee goal.

**GameTests, Part 5** (new class `BoundVexWorldGameTests`; Nether and End pockets as the
existing `toNether` helper builds them, with a new `toEnd` twin):

- Posture: a following vex settles within the band for each row of §6.1 (Overworld day, forced
  night, Nether, End); `scanInterval()` per row.
- Lava pull: a player in a lava pool beside a stone floor is pushed (velocity as §6.2) and ends
  on the floor; with Fire Resistance, or in creative, or riding, or with only a staying vex:
  no rescue; in the Nether the fire is cleared and the bucket shown; in the Overworld the player
  still burns.
- Drowning push: air below 60 under water → upward velocity each tick; with air at 100: none;
  with Water Breathing: none.
- Fireball: a `LargeFireball` flying at the player within 4 blocks is deflected, its owner is
  the player, and its heading is toward the shooter ghast (in sight) or reversed (no ghast); a
  fireball moving away is left alone; a returned one is not returned again; no vex within 7.25
  blocks: nothing.
- Void: a player teleported below the island is put on the nearest standable spot, with zero
  fall distance and the apple's effects (Regeneration, Absorption, Resistance, Fire
  Resistance); with no ground in 32 blocks but a last ground position: put there; a player
  falling over solid ground within 64 blocks: no catch; in the Overworld: no catch.
- Crystals: with a (test-made) living dragon fight, a vex breaks a crystal within 32 blocks,
  takes no damage, and the summoner two blocks from the crystal takes none; without a dragon
  the crystal is left alone; two vexes, two crystals: one each.
- Crits: a vex's melee hit on a husk does three times its Overworld damage in the End; its
  arrow does not; a hit on a mercy-protected golem still stops at 25.

Tests that need the End's dragon fight build the smallest thing vanilla accepts; if a real
`EnderDragonFight` cannot be made on the GameTest server, the dragon-alive test is put behind a
small package-private seam in `AttackCrystalGoal` and the plan says so.

**Playtest items** (Will; `docs/playtest-checklist.md`, sections "Evoking II (Part 4)" and
"(Part 5)"): the cure's look (apple crumbs, head turn, gray splash, smoke for 20 seconds, the
discount); a cure in daylight; the bribe's emerald, sound and silent exit; how close the vexes
crowd you at night, in the Nether and in the End (first-person view with twelve vexes at 0.5–1.5
blocks); the feel of the lava pull and the drowning push on a real client; the bucket and
splash; the fireball swing; breaking caged crystals; the void catch and the apple.

**Docs per part:** README (the vex paragraph), `docs/api-notes.md` §23 (Part 4) and §24
(Part 5) with every vanilla fact verified against `build/mc-src`, the playtest checklist,
`docs/mutation-testing.md`, the parent spec's inline notes, and `CLAUDE.md` only if a rule in it
changes (none is expected: no packet, no new Mixin; one access-transformer line).

## 10. Decisions Will did not state

| Decision | Choice | Why, and the alternative |
|---|---|---|
| Zombie villagers that attack | Still never fought; frozen by the cure instead | The parent spec says never prey; the freeze starts at the claim, so the attack stops within a scan. |
| The vex after the potion | Free at once; the smoke and freeze belong to the zombie villager | A vex held for 20 seconds is out of the fight for nothing. Alternative: it hovers 2 blocks off for the whole cure, as the parent's table can be read. |
| A fight starting mid-cure | Does not interrupt steps 1–3 | Under 8 seconds; an abandoned, unfrozen zombie villager is worse. |
| The potion | Weakness-colored, no effects | A real one weakens you, your villagers and the vex; vanilla removes weakness from the zombie villager at once anyway. |
| Freeze release | By the mark's age (300 ticks), on the zombie villager's own tick | Works however the vex vanished; no cross-entity bookkeeping. |
| A bribed vex and other Bound Vexes | Not prey | Otherwise the bribe ends in a kill. |
| Bribe subject | Any vanilla vex targeting your side | The parent's rule; an evoker-owned test would skip `/summon`ed vexes for no gain. |
| Bribed vex flees from | The nearest player | It has no summoner of its own to remember across a save. |
| Scan cadence | 4 ticks (night, Nether), 2 ticks (End) | The goal clock cannot keep 5 or 1; see §6.1. |
| Night | `isDarkOutside()`, so thunderstorms count | Hostiles spawn then too. |
| Rescuer | Nearest following vex within 16 blocks (64 for the void) | The portal rule's distance; staying vexes hold their posts. |
| Rescuer placement | Snapped to the shoulder | A vex 16 blocks away would arrive after you burned. |
| Who is not rescued | Creative, spectator, Fire Resistance (lava), Water Breathing or Conduit Power (drowning), passengers | A pull you do not need is a shove. |
| Lava pull limit | 60 ticks, then 20 ticks off | The parent's 60; the pause stops an endless shove when no way out exists. |
| Drowning limit | 200 ticks | A deep dive needs more than 60. |
| Fireball owner | The summoner | A ghast is immune to a vex's fireball, and a fireball dies with its owner. |
| Fireball detection | Each tick near players in the Nether who have a vex | 4 blocks at fireball speed is a few ticks. |
| Crystal range | 32 blocks from a following vex; the stay area for a staying one | The parent's 32; a staying vex does not leave its area. |
| Crystal explosion | Never hurts any Bound Vex | The parent's rule, widened from "the vex that hit it" to all, since twelve hover together. |
| Crits | Melee only | "Every melee hit" in the parent. |
| Void ground search | Height map, columns outward, 32 blocks | Finds the island edge you fell from. |
| Apple | Vanilla's own consume effects | Whatever vanilla gives, with no numbers copied. |
| Modded dimensions | Default posture, no helpers | Nothing is known about them. |
| Version | No bump | Will asks for releases; the next would be `1.2.3` per part or `1.3.0` after Part 7. |
