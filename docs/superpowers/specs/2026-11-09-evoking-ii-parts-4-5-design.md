# Spellcraft: Evoking II Parts 4 and 5, cure, bribes and world helpers (design)

**Date:** 2026-11-09
**Status:** plan written (`2026-11-10-evoking-ii-part-5.md`)
<!-- child-plans: 2026-11-09-evoking-ii-part-4.md, 2026-11-10-evoking-ii-part-5.md -->
**Written by:** `/one-shot`, unattended, on 2026-11-09; §10 lists the decisions Will did not state.
**Target:** Minecraft Java 26.3, NeoForge 26.3.0.23-beta, Java 25, ModDevGradle
**Builds on:** [`2026-11-07-evoking-ii-design.md`](2026-11-07-evoking-ii-design.md) ("the parent
spec"), whose Part 4 (§5) and Part 5 (§6) this builds, and
[`2026-11-09-evoking-ii-part-3-gear-and-stay-design.md`](2026-11-09-evoking-ii-part-3-gear-and-stay-design.md)
("the Part 3 spec": the stash, the stay area, the friendly-fire backstop).
**Amends:** the parent spec's §3.3 (where a following vex hovers in the tight postures), §3.4
(its "every-tick scans will need their own tick hook" sentence: there is no such hook, §6.1),
§5.1 (the cure's freeze, potion and timeline), §5.2 (what a bribed or courted vex is to other
vexes; it flees the nearest player, not its briber's summoner), the §6 preamble ("each helper is
a job goal": rescues and the fireball are driven from the player and from an event), §6.1–§6.3
(scan cadence, the velocity flag, how a fireball is caught and who owns it, rescue limits, the
knockback rule for crystals), §10 (the cure's interruption rule; the `hurtMarked` bullet), §11
(Part 4 and 5 tests) and §13 (the rows "Cure visuals", "Rescue pushes" and "Crystal explosion vs.
vexes"). The parent carries inline "Amended by" notes at those places, added in the same change.

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
| **Claim** | A transient map entry "subject → the vex working on it". A claim whose vex is dead or removed counts as free; so does a cure claim on a zombie villager that no longer carries the cure mark, and a bribe claim older than 140 ticks (§4). |
| **Cure mark** | The saved mark `spellcraft:cure` on a zombie villager a vex is curing: the game time the cure began. While it is there the zombie villager is frozen. |
| **Frozen** | `setNoAi(true)`: the mob neither moves nor attacks. Vanilla still counts down a conversion on a frozen zombie villager. |
| **Bribed**, **bribe tried** | Saved marks on a vanilla vex: `spellcraft:bribed` (it took the emerald and is leaving) and `spellcraft:bribe_tried` (it refused; no vex offers again). |
| **Posture** | How closely a following vex keeps to its summoner and how often it looks for hostiles, chosen from the dimension and the time of day (§6.1). |
| **Rescue** | A vex acting on its summoner in an emergency: the lava pull, the drowning push, the void catch. One vex per summoner at a time. |
| **Rescuer** | The vex picked for a rescue: the nearest living, following (not staying) Bound Vex of that summoner within 16 blocks, in the same level. |
| **Standable spot** | A block position whose block below has a sturdy top face and where that position and the one above it are both air (so no fluid, no portal block, nothing to collide with). |
| **Courted** | A vanilla vex that a Bound Vex has claimed for a bribe and not yet rolled for. Not prey while it lasts. |
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
summoner. The cure stops such a zombie villager instead: it is frozen from the moment it is
claimed, and one that is attacking the summoner's side is claimed even by a vex that is busy
fighting (§2.2).

**A zombie villager being cured is on your side.** While it carries the cure mark it stands
frozen for up to half a minute, in a village more often than not. `Allies.isAllyOf` answers
true for a mob with the cure mark, for every player, so fangs, the Trailing flame and every
Bound Vex's projectiles spare it; and `GolemMercy`'s `LivingChangeTargetEvent` handler cancels
an iron golem taking a marked zombie villager as its target, as it does for summons. A cancelled
change keeps a golem's old target, so the claim also clears the target of every iron golem
within 24 blocks that is already fighting that zombie villager.

Two limits on "on your side". It is spared, not avenged: the vexes' grudge and ally goals do not
count a marked zombie villager among the allies they defend, so whoever hurts it (another player
with PvP on) is not attacked for it. And only the mark makes it an ally: a zombie villager that
no vex has claimed yet is never prey for a Bound Vex, but the summoner's own fangs, flame and
sword still hit it like any hostile.

### 2.2 The goal

`CureZombieVillagerGoal` (`evoking` package), a job goal at priority 2 with the `MOVE` and
`LOOK` flags, shaped like `RepairGolemGoal`.

**Start** (`canUse`, on the vex's scan tick): the vex has a summoner present; a `ZombieVillager`
whose feet are in the guard area (the stay area for a staying vex) is alive, not converting,
not already `NoAI` (a map-made statue is left alone), carries no cure mark and is not claimed by
another living vex; and either the vex has **no target**, or the zombie villager's own target is
the summoner or an ally (an attacker is stopped even mid-fight). The nearest one is taken: it is
claimed, **frozen**, and given the cure mark.

**Timeline.** A new target for the vex does not end the goal (`canContinueToUse` does not look
at it): a half-cured zombie villager left standing is worse than one vex out of the fight for a
few seconds. The goal can still be **interrupted** by the goals that outrank it on a shared
flag: fleeing a hissing creeper, dodging a golem, and Part 5's rescue. An interruption before
step 4, like every other early end (the zombie villager dies or is removed, the approach times
out, the summoner is gone), is the **abort** below; because the abort removes the mark, another
vex can claim that zombie villager on a later scan. The vex whose approach timed out does not
claim anything for 200 ticks: vanilla asks a goal whether it may start in the same pass that
stopped it, so without the delay a vex that cannot reach its subject would claim it again at
once, forever.

| Step | Lasts | What happens |
|---|---|---|
| 1 Approach | until within 1.5 blocks of the zombie villager's head, at most 100 ticks | The vex flies level with its head (charge speed) and stops there, 1.5 blocks short: its velocity is zeroed and it holds that point through steps 2 and 3 instead of coasting into the head. After 100 ticks without arriving the goal gives up (step "abort" below). |
| 2 Apple | 30 ticks | `conjure(MAINHAND, golden apple)`. Every 4 ticks: `SoundEvents.GENERIC_EAT` at the zombie villager and 5 `ITEM` crumb particles of a golden apple at its mouth. (No arm swing: the vex model has none; the sound and crumbs are the cue.) |
| 3 Potion | 20 ticks | `conjure(MAINHAND, the cure potion)` (it replaces the apple in the same conjured slot). The vex turns its head 60° to one side for the first 10 ticks; at tick 10 it throws a `ThrownSplashPotion` of the cure potion at the zombie villager's feet. |
| 4 Begin | at the end of step 3 | If the zombie villager is not converting yet: `startConverting(summonerUuid, 400)`. (If a player fed it by hand meanwhile, vanilla's conversion and that player's discount are left as they are.) Then `dismiss(MAINHAND)`; the claim is released; the goal ends. The vex is free again; the mark and the freeze stay until the conversion ends (§2.3). |

**The cure potion** is a splash potion with **no effects** and weakness's color (a
`PotionContents` with a custom color and an empty effect list): the throw, the glass break and
the gray splash look like a weakness potion, and nobody standing near (the summoner, the vex, a
villager) is weakened. Vanilla's `startConverting` removes weakness from the zombie villager
anyway, so a real weakness potion would only have hurt bystanders.

**Abort** (any end before step 4, the goal's `stop` included): the vex's hand is dismissed, the
claim released, and the zombie villager, if it still exists and is not converting, is unfrozen
and its cure mark removed.

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
with the old entity. Vanilla's conversion copies `NoAI` to the new mob
(`ConversionType.java:122`), so `CureEvents` clears it on NeoForge's
`LivingConversionEvent.Post` when the old mob carried the cure mark; a GameTest asserts the new
villager has AI.

The mark is saved (a zombie villager unloaded mid-cure keeps converting when it loads again,
with its smoke, and is released by the same rules). It holds the game time the cure began.

## 3. Part 4: bribing a vex

### 3.1 The goal

`BribeGoal` (`evoking`), a job goal at priority 2 with the `MOVE` and `LOOK` flags. Priority 2
outranks the charge goal, so a vex that already targets the enemy vex stops charging once the
bribe starts.

**A courted vex is not prey.** From the claim until the roll, `BoundVex.isPrey` answers false
for the claimed vex, for every Bound Vex: the summoner's other vexes drop it as a target and the
Part 3 backstop cancels a hit already on its way. Without this, eleven other vexes would kill
it (14 health) before the offer ended. After a refusal it is prey again.

**Start** (on the scan tick): a summoner is present; a vanilla vex (`EntityTypes.VEX` exactly,
never a Bound Vex) whose feet are in the guard area is alive, carries neither mark, is not
claimed, and its target is the summoner or an ally of the summoner. Whether the Bound Vex has a
target does not matter.

| Step | Lasts | What happens |
|---|---|---|
| 1 Approach | until within 1.5 blocks, at most 100 ticks | Fly to it. A timeout ends the goal with no mark; another vex may try at once, the same vex after 200 ticks (the cure's rule, §2.2). |
| 2 Offer | 20 ticks | `conjure(MAINHAND, emerald)`; `SoundEvents.VILLAGER_AMBIENT` once at the start; the vex looks at the other vex. |
| 3 Roll | | One roll from the pure rule `BribeRule` (§9): **2 in 3** it is bribed, **1 in 3** it refuses. `dismiss(MAINHAND)`; the claim is released. The roll comes from `BribeGoal`'s `Dice` (the small interface `ScrollDraw` already uses), which is the vex's random source in play and which a GameTest replaces for one vex through a public test hook (`BribeGoal.scriptDice`), removed when the test ends. |

**Bribed:** the vex gets the `bribed` mark, its target is cleared, every goal and target goal
it has is removed, and `FleeFromViewGoal` is added (§3.2). **Refused:** it gets the
`bribe_tried` mark and is prey as before.

The goal ends early, with no mark, if the other vex dies or leaves the guard area, or when a
higher goal interrupts it (flee, dodge, a rescue); the claim is released and the vex is prey
again.

### 3.2 A bribed vex

- It is **never prey** (`BoundVex.isPrey` answers false for a mob carrying `bribed`), so no
  Bound Vex attacks it while it leaves. Golems, wolves and players may still hurt it; it does
  not fight back (it has no goals).
- `FleeFromViewGoal`: each tick it flies directly away from the nearest player (through blocks,
  as vexes do). When it is at least **12 blocks** from every player within 64 blocks and none of
  those players has line of sight to it, it is `discard()`ed: no sound, no particles, no drop.
  After **200 ticks** it is discarded anyway. Spectators do not count as players here: it does
  not flee from one, and one watching does not keep it in the world.
- Saved and reloaded, it keeps the mark; `EntityJoinLevelEvent` strips its goals again and
  reinstalls the flee goal (the 200 ticks start over).

## 4. Part 4: error handling

- **Two vexes, one subject:** the claim maps (one per job) allow one; `BoundVexEvents`'s
  server-stop handler clears them next to the repair claims.
- **The curing vex dies, is discarded or unloads in steps 1–3:** its hand items were conjured,
  so nothing drops; the zombie villager is released by the 300-tick rule (§2.3).
- **A vex that stops ticking mid-job** (alive, in a chunk that is loaded but no longer ticks)
  never runs its goal's `stop`. Its cure claim is free once the 300-tick rule has removed the
  mark, and a cure goes on only while the mark is there, so a vex that wakes later drops the
  job instead of finishing it on an unfrozen zombie villager. Its bribe claim is free after 140
  ticks (the approach, the offer and a margin), so the courted vex becomes prey again.
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
  the need, picks the rescuer, **and applies the push itself**; the rescuer's `RescueGoal` only
  brings the vex to the player's side for the look of it, so a rescue never waits on a vex's
  goals. The fireball return hangs on an event (§6.3).
- **Who is not rescued.** A player who cannot be hurt by the danger gets no rescue: spectators
  never; creative players only the void catch (the void kills them too); a player with Fire
  Resistance gets no lava pull; a player with Water Breathing or Conduit Power gets no drowning
  push. A player riding something gets no push (a passenger's velocity is its vehicle's), but
  the void catch dismounts and catches them.
- **Dimension tests** read `level.dimension()` against `Level.OVERWORLD`, `NETHER`, `END`. A
  modded dimension counts as none of them: the default posture, no helpers.
- **Velocity.** A push changes the player's velocity on the server and marks it for sending.
  The parent spec's `hurtMarked` does not exist in 26.3; the field is `Entity.syncVelocity`,
  which `ServerEntity` sends to the player's own client too. Because that packet replaces the
  client's velocity, every push **adds to** what the player is doing instead of overwriting it
  (the formulas in §6.2), so the player keeps steering. "What the player is doing" is the last
  movement their client reported (`ServerPlayer.getKnownMovement()`); the server-side velocity
  of a player is not their real one. That movement is taken **with the fluid's drag applied**
  (×0.5 in lava, ×0.8 in water, vanilla's factors): the client moves, then slows; a packet that
  sent the unslowed movement back every tick would cancel the drag, and a player steering toward
  the shore would speed up without limit.

## 6. Part 5: posture, rescues, the fireball

### 6.1 Posture

The pure class `spell.rules.GuardPosture` answers, from a dimension kind (`OVERWORLD`, `NETHER`,
`END`, `OTHER`) and "is it dark outside": where a following vex hovers, how fast it flies there,
and its scan interval.

| Where | Sideways from the summoner | Height above the summoner's feet | Speed | Scan every |
|---|---|---|---|---|
| Overworld by day; any other dimension | 2–3.5 | 0.5–1.5 | 0.7 | 10 ticks |
| Overworld when `level.isDarkOutside()` (night, or a thunderstorm) | 1–2 | 2.5–3.0 | 0.35 | 4 ticks |
| Nether | 1–2 | 2.5–3.0 | 0.35 | 4 ticks |
| End | 0.5–1.5 | 2.5–3.0 | 0.35 | 2 ticks |

- **Close means overhead.** In the three close postures the vexes hover **above the player's
  head** (the player is 1.8 tall), not at eye height. At eye height, a ring of vexes within two
  blocks sits in the crosshair: a right-click meant to draw a bow or place a block would land on
  a vex and hand it the bow (the client asks the entity first), and a left-click would hit it.
  Overhead they are as near, and out of the way unless the player looks up. The first row is
  today's geometry, unchanged.
- **When a vex moves.** `FollowSummonerGoal` asks the posture each time. A vex is *in place*
  when its sideways distance from the summoner is at most the row's outer edge plus 0.5 and its
  height above the summoner's feet is within the row's range widened by 0.5 each way; otherwise,
  when idle, it flies to a random spot inside the row's two ranges at the row's speed. In the
  three close postures the flight is one continuous goal use that ends when the vex arrives, and
  on arrival its velocity is zeroed: a vex left to coast sank to 1.5 blocks above the feet, eye
  height, which is what "overhead" is there to prevent. Vanilla's move control calls a spot
  reached up to half a block short, which is why the band starts at 2.5 and not lower: a vex
  that stops short is still about 2 blocks up. A target appearing mid-flight ends the flight at
  once (the wanted position is dropped so the charge goal can start). The first row keeps today's test (farther than 4
  blocks, feet to feet). The teleport rule (farther than 12 blocks) is unchanged; a teleported
  vex lands within 2 blocks and then takes its place.
- **Scan interval.** The three target goals and the job goals read their interval from
  `BoundVex.scanInterval()` (the posture's) instead of the constant 10. `SupportGearGoal` keeps
  10: its 40-tick throw cooldown makes a faster look pointless.
- **Why 4 and 2, not the parent's 5 and 1.** Vanilla asks a goal whether it may start only on
  every second tick, on ticks fixed by the mob's id (api-notes §21). A 5-tick clock keyed that
  way fires every 10 ticks, and nothing can fire every tick. 4 and 2 are the nearest cadences
  the goal clock can keep. No separate tick hook is added.
- A staying vex has no orbit; its scan interval still follows the posture.

### 6.2 Rescues

`VexRescues.tick(player)` runs each tick for every online, living player who is not a spectator:

1. Record the **last ground position** when the player is `onGround()` on a standable spot.
2. If a rescue for this player is running, carry it on (below).
3. Otherwise test the needs in this order and start the first that holds and has a rescuer:
   the void (End), lava (Overworld and Nether), drowning (Overworld).

A rescue is a record (kind, rescuer, start tick, state) in a transient map keyed by the player.
It ends as its row says, or when its rescuer is dead, removed or no longer has this summoner
present (the next tick may then start a new one with another vex). The map entry for a player
goes on logout; the whole map when the server stops.

The **rescuer** is the nearest living, following Bound Vex of the player within 16 blocks. A
vex that is fleeing a creeper or mid-cure is still eligible: `RescueGoal` (priority **−1**, so
it replaces any other goal on its flags, `MOVE` and `LOOK`) runs while the vex is the rescuer
of a running rescue, including a rescue's 20-tick tail. At its start the vex is teleported to
the player's side: 0.6 blocks to the right of the way the player faces and 1.2 above their
feet (vexes pass through blocks); each tick it is kept there, looking at the player.

| Rescue | Need | Each tick, to the player's velocity `v` | Ends |
|---|---|---|---|
| **Lava pull** (Overworld, Nether) | `player.isInLava()` | Find the nearest standable spot within 6 blocks sideways whose height is between the player's feet −1 and +2 and that is not in lava. `v` is the player's movement with lava's drag (×0.5). With a spot: add 0.1 toward it sideways, cap the sideways speed at 0.35 (a hard cap), and raise `v.y` to at least 0.3. With none: only raise `v.y` to at least 0.4. | When the player has been out of lava for 10 ticks, or is out of lava and on the ground; or 60 ticks after it began, whichever is first. No push is applied on ticks the player is out of lava. After any lava rescue ends, none starts for that player for 20 ticks. |
| **Drowning push** (Overworld) | `player.isUnderWater()`, `getAirSupply() < 60`, and **a way up**: every block from the player's head upward is water (no collision) until air is reached, within 32 blocks | `v` is the player's movement with water's drag (×0.8). Raise `v.y` to at least 0.4; nothing is added sideways. | The player's eyes are out of water, the way up closes, or 200 ticks. |
| **Void catch** (End) | §6.4 | One action, in the tick it starts. | After a 20-tick tail (the apple in the vex's hand). |

A drowning player under a ceiling gets no push: holding them against the roof would stop them
swimming sideways to air. They are on their own, as without vexes.

**The Nether's water bucket.** When a lava rescue ends in the Nether with the player out of
lava: the rescuer conjures a **water bucket** in its main hand for a 20-tick tail,
`SoundEvents.BUCKET_EMPTY` and `FIRE_EXTINGUISH` play at the player, 12 `SPLASH` particles appear
on the player, and `player.clearFire()`. Once per rescue. No water block is placed. In the
Overworld the player keeps burning (Will chose only the pull there).

A rescuer returns to its duties when the rescue ends. It keeps its target if that target is
still within its reach from the player's side; one it was fighting farther off is dropped by the
vex's ordinary rules (it was moved away from it). A rescue never uses a staying vex. A rescue
also ends when the player stops needing it: they gain the protecting effect, go creative or
mount something.

### 6.3 The fireball return (Nether)

A check once per tick would miss too many fireballs: a ghast's fireball covers 1.2 to 1.9 blocks
per tick, so it can go from outside any sensible radius to impact between two looks. The return
therefore hangs on the impact itself: NeoForge's cancellable `ProjectileImpactEvent`, which
vanilla fires for a `LargeFireball` just before it explodes.

When a `LargeFireball` in the Nether is about to hit, and for some living, non-spectator player:

- the fireball's owner is not that player (a fireball they or their vex already returned is
  left alone), and
- the thing hit is that player, or a block or entity within **4 blocks** of that player, and
- a living Bound Vex of that player, following or staying, is within **7.25 blocks** of the
  fireball (the nearest one acts),

then the impact is cancelled and:

- the vex is teleported to the fireball and `SoundEvents.PLAYER_ATTACK_KNOCKBACK` plays (the
  sound is the cue; the vex model has no swing);
- the fireball is deflected through vanilla's `Projectile.deflect` with a deflection of this
  mod's own (vanilla's two either halve the speed or aim along the deflector's look): its
  velocity becomes its old speed along the new heading, **the summoner is its new owner**, the
  vex is the deflecting entity, and it counts as deflected by an attack (which is what keeps its
  acceleration). The new heading is toward the eyes of the ghast that shot it when that ghast
  is alive and the vex has line of sight to it; otherwise the exact reverse of its old heading.

The summoner is the owner for three reasons: vanilla's one-hit kill (a ghast takes the direct
hit only from a fireball a *player* returned; from any other owner only the blast hurts it),
vanilla's ghast advancement, and that a fireball whose owner is removed vanishes. The parent
spec had the vex as owner. Because the owner is the player, the blast is the player's own
explosion to vanilla and to the Part 3 backstop: it can hurt the player's allies like any
returned fireball. Accepted (it flies away from them).

A staying vex that returned a fireball flies back to its post afterwards.

### 6.4 The void catch (End)

**Need:** the player is in the End, is not flying with an elytra or creative flight, and either
`y < level.getMinY() − 4`, or all of: not on the ground, moving down, `y < level.getMinY() +
16`, and a 64-block downward `clip` from their feet (colliders, no fluids) hits nothing.

**Rescuer:** the nearest living, following Bound Vex of the player within **64 blocks** (a
falling player outruns the 16-block rule).

**Action:** find where to put them: the nearest **standable spot** to the player's column
within 32 blocks sideways, taken column by column outward from the
`MOTION_BLOCKING_NO_LEAVES` height map (a column with no blocks, or whose top is not a standable
spot, is skipped); else the player's **last ground position** if it is in the End. With neither,
there is no catch. Otherwise: a riding player is dismounted; the player is teleported there;
their velocity and fall distance are zeroed; the rescuer is brought to their side; and the vex
feeds them an **enchanted golden apple**: the apple's own consume effects are applied through
vanilla's `Consumable.onConsume` on a throwaway stack, with its eating sound, and the vex shows
the apple in its hand (conjured) for the 20-tick tail. No item is spent.

**No cooldown** (Will's choice, kept knowingly): stepping off the island with a vex near is a
repeatable source of the apple's effects. Vanilla also counts the apple as eaten for statistics
and the "consume item" advancement trigger.

## 7. Part 5: the End's crystals and critical hits

### 7.1 Crystals

`AttackCrystalGoal` (`evoking`), a job goal at priority 2 with the `MOVE` flag, only in the End
and only while the dragon fight has a living dragon (`level.getDragonFight()` is not null, its
`dragonUUID()` names an entity in the level, and that entity is alive).

**Start** (on the scan tick): the vex has a summoner present and no target; an `EndCrystal`
that is not invulnerable, within **32 blocks** of a following vex or inside the stay area of a
staying one, is not claimed. The nearest is claimed. The vex flies to within 2 blocks and calls
`crystal.hurtServer(level, damageSources().mobAttack(vex), 1)`, which vanilla turns into the
crystal's explosion. The goal ends then, and also when the crystal is removed by anything else
(a neighbor's blast), when the vex gets a target, or after 200 ticks; a vex that flew far is
brought back by the follow goal's teleport.

**The explosion and your side.** A crystal explodes with power 6: lethal near it, and its
knockback reaches 12 blocks. Players stand on towers to reach caged crystals, and their vexes
get there first.

- **Damage.** The explosion names the vex as its cause, so the Part 3 backstop already cancels
  its damage for the summoner, allies and the summoner's vexes, the acting vex included (a vex
  is its own summoner's ally). `BoundVexEvents` also cancels damage to *any* Bound Vex whose
  direct source entity is an `EndCrystal`, which covers what the backstop does not: a crystal
  set off by a player, by another player's vex, or while the summoner is away.
- **Knockback.** Vanilla pushes everything in range whether or not the damage was cancelled.
  `BoundVexEvents` handles NeoForge's `ExplosionKnockbackEvent`: when the explosion was caused
  by a Bound Vex, the knockback is set to zero for every entity the backstop spares (the
  summoner, allies, the summoner's vexes); and for an explosion whose direct source is an
  `EndCrystal`, for every Bound Vex. So a vex breaking the crystal beside you neither hurts you
  nor throws you off the tower. A crystal *you* break still throws you, as in vanilla.

### 7.2 Critical hits

In the End, every **melee** hit by a Bound Vex does **3×** damage: in `BoundVexEvents`'s
`LivingIncomingDamageEvent` handler, after the backstop's test and only when the hit was not
cancelled, when the damage's direct entity is a Bound Vex (its own body, so arrows and potions
are excluded) in the End, the amount is tripled, 6 `CRIT` particles appear on the victim and
`SoundEvents.PLAYER_ATTACK_CRIT` plays. This holds for any victim the vex may fight, a player
under the PvP rule included, and with any weapon it was given. The golem mercy clamp runs later
in the damage pipeline (`LivingDamageEvent.Pre`) and still holds.

## 8. Part 5: error handling

- **No rescuer in range:** no rescue; the test repeats each tick.
- **The rescuer dies mid-rescue:** the rescue ends; the next tick may pick another.
- **A lava push against a wall:** it continues to its 60-tick limit, then pauses 20; the player
  is not teleported (only the void catch teleports).
- **A lava lake with no shore within 6 blocks:** the player is held at the surface in 60-tick
  pushes with 20-tick gaps; the Nether bucket does not play (they are still in lava).
- **Lava or water one block deep:** a push of a tick or two; harmless.
- **The fireball's ghast is gone or out of sight:** the fireball goes straight back along its
  path. **A fireball that is not a ghast's** (a command's, for one) is returned the same way.
- **Two fireballs in one tick:** each impact is its own event; the nearest vex acts for each.
- **The void catch finds lava, a portal or a gateway:** a standable spot needs two blocks of
  air, so none of them qualifies; an obsidian pillar's top does.
- **Elytra and creative flight:** no void catch while flying; it applies again the moment the
  player stops.
- **The dragon is dead or was never there:** no crystal goal. Crystals a player places to
  respawn the dragon are left alone until the new dragon exists (vanilla removes them itself
  when it summons it).
- **An invulnerable crystal:** never claimed.
- **`/tp` into the void** triggers a catch like a fall.
- **A logged-out or dead player:** no tick, no rescue; their map entries are dropped. The rescue
  map, the last-ground map and the crystal claims are cleared with the other transient state
  when the server stops.

## 9. Testing, docs

**Pure core (TDD, jqwik, `spell.rules`):**

- `BribeRule.accepts(roll)` for a roll in `0..2`: true for 0 and 1; the dice come through the
  same small interface the scroll draw uses, so a GameTest scripts the outcome. Property: over
  the three rolls exactly two accept.
- `GuardPosture.of(dimension, dark)`: the table of §6.1. Properties: the inner edge is below the
  outer edge; every scan interval is even and at least 2; a darker or more dangerous place never
  has a wider band or a slower scan than the Overworld day.
- `GuardPosture` also answers "in place" and gives a spot from two unit rolls. Properties: every
  spot is inside its row's ranges; every spot is in place; in the three close rows every spot is
  at least 2.5 above the feet.
- `RescuePush`: the lava-pull velocity from the reported movement and the offset to the spot
  (lava's drag, then add 0.1 sideways toward it, sideways speed capped at 0.35, `y` raised to at
  least 0.3; with no spot only the drag and `y` raised to at least 0.4; a spot directly above or
  below adds nothing sideways and gives no NaN), the drowning push (water's drag, `y` raised to
  at least 0.4), and the returned fireball's velocity (old speed along the new heading).
  Properties: after a lava pull the sideways speed never exceeds 0.35; a lift or a drowning push
  never raises the sideways speed; `y` is never under its floor; no component is NaN for any
  finite input; and fed its own output plus a constant steering input, tick after tick, the
  sideways speed stays bounded (no ratchet).
- `VoidRule.needsCatch(y, minY, onGround, movingDown, groundWithin64, flying)`: the conditions
  of §6.4.

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
- A new target during steps 1–3 does not stop the cure. A zombie villager whose target is the
  summoner is claimed by a vex that has a target; one with no such target is not.
- A swelling creeper beside the vex in step 2 interrupts the cure: the zombie villager is
  unfrozen and unmarked, the vex's hand is restored, and the cure starts again once the creeper
  is gone. The approach timeout and a summoner who leaves end the same way.
- A marked zombie villager is an ally: an iron golem beside it does not take it as a target, the
  summoner's fangs spare it, and a `NoAI` zombie villager with no mark is never claimed.
- A zombie villager fed by hand during steps 1–3 keeps its own conversion (starter and time).
- A staying vex cures a zombie villager inside its stay area and not one outside it.
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
- A courted vex is not prey: with two Bound Vexes and one vanilla vex, the second Bound Vex
  holds no target on it during the offer, and a hit on it from a Bound Vex is cancelled.
- The bribe ends with no mark when the other vex dies mid-offer or the approach times out.
- A vanilla vex whose target is not on the summoner's side gets no offer; a Bound Vex of another
  player gets none.
- A bribed vex loaded from a save has only the flee goal.

**GameTests, Part 5** (new class `BoundVexWorldGameTests`; Nether and End pockets as the
existing `toNether` helper builds them, with a new `toEnd` twin that carves and clears a fixed
region, so "ground within 32 blocks" is what the test built and nothing else). A mock player is
never moved by the server, so push tests assert the velocity the tick set and then move the
player by hand to reach the next state.

- Posture: for each row of §6.1 (Overworld day, forced night, Nether, End) a following vex
  settles in place; in the three close rows its feet are at least 1.7 above the summoner's feet
  throughout the following 60 ticks; `scanInterval()` per row.
- Lava pull: a player in lava beside a stone floor gets the §6.2 velocity (toward the floor,
  upward), keeps part of a sideways velocity the test gave them, and the rescuer is at their
  side; moved onto the floor, the rescue ends and none starts for 20 ticks; held in lava, it
  ends after 60 ticks and resumes after the pause; a spot across the pool but 4 blocks lower is
  not chosen; with Fire Resistance, as a spectator, riding, or with only a staying vex: no
  rescue; a rescuer that was fleeing a creeper still comes; a rescuer killed mid-rescue is
  replaced. In the Nether, ending out of lava clears the fire and shows the bucket once; ending
  still in lava does neither; in the Overworld the player still burns.
- Drowning push: air below 60 under open water → `y` raised, `x` and `z` unchanged; under a
  stone ceiling: none; with air at 100: none; with Water Breathing: none; it ends when the
  player's eyes leave the water and after 200 ticks.
- Fireball: firing `ProjectileImpactEvent` for a `LargeFireball` about to hit the player is
  cancelled, the fireball's owner is the player, its speed is unchanged and its heading is
  toward the shooter ghast (in sight) or reversed (no ghast); the same for a block hit 3 blocks
  from the player; a hit 6 blocks away, a fireball the player owns, no vex within 7.25 blocks,
  and the Overworld: not cancelled; with two vexes in range the nearer one is moved.
- Void: a player below the island is put on the nearest standable spot with zero fall distance
  and the apple's four effects; with no ground in 32 blocks but a last ground position: put
  there; with neither: no catch; a rescuer 40 blocks away catches, one 70 blocks away does not;
  a riding player is dismounted and caught; a creative player is caught; an elytra-flying player
  is not; a portal block's column is skipped; a player falling over ground within 64 blocks, and
  the Overworld: no catch.
- Crystals: with a living dragon fight (or the seam below), a vex breaks a crystal within 32
  blocks and takes no damage; the summoner two blocks from the crystal takes no damage **and
  their velocity is unchanged**; an invulnerable crystal is never claimed; a crystal removed
  while a vex flies to it ends that vex's goal; without a dragon the crystal is left alone; two
  vexes, two crystals: one each; a crystal blast caused by a player does not hurt or move a
  Bound Vex, and still moves the player.
- Crits: a vex's melee hit on a husk does three times its Overworld damage in the End; its
  arrow does not; a hit the backstop cancels stays cancelled; a hit on a mercy-protected golem
  still stops at 25.

Tests that need the End's dragon fight build the smallest thing vanilla accepts; if a living
dragon fight cannot be made on the GameTest server, the dragon-alive test is put behind a small
package-private seam in `AttackCrystalGoal` and the plan says so.

**Playtest items** (Will; `docs/playtest-checklist.md`, sections "Evoking II (Part 4)" and
"(Part 5)"): the cure's look (apple crumbs, head turn, gray splash, smoke for 20 seconds, the
discount); a cure in daylight; the bribe's emerald, sound and silent exit, with a dozen vexes out; twelve vexes
overhead at night, in the Nether and in the End (are they out of the way of your clicks; is
"above your head" right); the feel of the lava pull and the drowning push on a real client; the bucket and
splash; the fireball swing; breaking caged crystals; the void catch and the apple.

**Docs per part:** README (the vex paragraph), `docs/api-notes.md` §23 (Part 4) and §24
(Part 5) with every vanilla fact verified against `build/mc-src`, the playtest checklist,
`docs/mutation-testing.md`, the parent spec's inline notes, and `CLAUDE.md` only if a rule in it
changes (none is expected: no packet, no new Mixin; one access-transformer line).

## 10. Decisions Will did not state

| Decision | Choice | Why, and the alternative |
|---|---|---|
| Zombie villagers that attack | Still never fought; frozen by the cure instead | The parent spec says never prey; an attacker is claimed, and so frozen, even by a vex that is fighting something else. |
| A zombie villager being cured | An ally of every player; golems may not target it | It stands frozen for half a minute where golems and fangs are. |
| Interruptions | Flee, dodge and rescue may interrupt a cure or a bribe; that aborts it cleanly | A vex that will not flee a creeper or come to a burning player is worse than a restarted cure. |
| The vex after the potion | Free at once; the smoke and freeze belong to the zombie villager | A vex held for 20 seconds is out of the fight for nothing. Alternative: it hovers 2 blocks off for the whole cure, as the parent's table can be read. |
| A fight starting mid-cure | Does not interrupt steps 1–3 | Under 8 seconds; an abandoned, unfrozen zombie villager is worse. |
| The potion | Weakness-colored, no effects | A real one weakens you, your villagers and the vex; vanilla removes weakness from the zombie villager at once anyway. |
| Freeze release | By the mark's age (300 ticks), on the zombie villager's own tick | Works however the vex vanished; no cross-entity bookkeeping. |
| A bribed or courted vex and other Bound Vexes | Not prey | Otherwise the bribe ends in a kill, usually before the roll. |
| Bribe subject | Any vanilla vex targeting your side | The parent's rule; an evoker-owned test would skip `/summon`ed vexes for no gain. |
| Bribed vex flees from | The nearest player | It has no summoner of its own to remember across a save. |
| Close postures | Overhead (2.5–3.0 above the feet), stopping dead on arrival | At eye height your clicks land on your vexes. Alternative: keep the parent's "blocks from the summoner" and accept that. The band was 2.2–3.0 until the build measured vexes ending at 1.7. |
| Scan cadence | 4 ticks (night, Nether), 2 ticks (End) | The goal clock cannot keep 5 or 1; see §6.1. |
| Night | `isDarkOutside()`, so thunderstorms count | Hostiles spawn then too. |
| Rescuer | Nearest following vex within 16 blocks (64 for the void) | The portal rule's distance; staying vexes hold their posts. |
| Rescuer placement | Teleported to your side; the push comes from the rescue itself | A vex 16 blocks away would arrive after you burned, and a push that waited on the vex's goals could never start. |
| Pushes add to your velocity | Yes | An overwriting push takes the controls away. |
| Drowning under a ceiling | No push | Pinning you to the roof stops you swimming to air. |
| Who is not rescued | Spectators; creative except from the void; Fire Resistance (lava); Water Breathing or Conduit Power (drowning); passengers except from the void | A pull you do not need is a shove. |
| Lava rescue | One episode until you are out and standing (or out for 10 ticks), at most 60 ticks, then 20 off; the bucket once, only if you got out | Ending at "not in lava" made it restart every few ticks. |
| Drowning limit | 200 ticks | A deep dive needs more than 60. |
| Fireball owner | The summoner | The one-hit kill and the advancement need a player, and a fireball dies with its owner. |
| Fireball detection | At the impact (`ProjectileImpactEvent`), for hits on you or within 4 blocks of you | A per-tick look misses fireballs that cross the radius between two ticks. |
| Crystal range | 32 blocks from a following vex; the stay area for a staying one | The parent's 32; a staying vex does not leave its area. |
| Crystal explosion | Never hurts any Bound Vex; a vex's crystal blast neither hurts nor moves your side | Twelve hover together, and you are standing on a tower. |
| Crits | Melee only | "Every melee hit" in the parent. |
| Void ground search | Height map, columns outward, 32 blocks | Finds the island edge you fell from. |
| Void catch cooldown | None | Will's rule; the farmable apple effects are noted in §6.4. |
| Support vexes' cadence | Unchanged (10) | Their 40-tick cooldown rules. |
| Apple | Vanilla's own consume effects | Whatever vanilla gives, with no numbers copied. |
| Modded dimensions | Default posture, no helpers | Nothing is known about them. |
| Version | No bump | Will asks for releases; the next would be `1.2.3` per part or `1.3.0` after Part 7. |
