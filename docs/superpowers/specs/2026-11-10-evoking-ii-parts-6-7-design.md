# Spellcraft: Evoking II Parts 6 and 7, team moves, Casters, Mirror, Ghostrail, villager ghosts (design)

**Date:** 2026-11-10
**Status:** plan written (`2026-11-10-evoking-ii-part-6.md`); phase 2 plan not yet written
**Written by:** `/one-shot`, unattended, on 2026-11-10; §14 lists the decisions Will did not state.
**Target:** Minecraft Java 26.3, NeoForge 26.3.0.23-beta, Java 25, ModDevGradle
**Builds on:** [`2026-11-07-evoking-ii-design.md`](2026-11-07-evoking-ii-design.md) ("the parent
spec"), whose Part 6 (§7–§8) and Part 7 (§9) this builds;
[`2026-11-09-evoking-ii-part-3-gear-and-stay-design.md`](2026-11-09-evoking-ii-part-3-gear-and-stay-design.md)
("the Part 3 spec": the stash, conjured slots, the friendly-fire backstop);
[`2026-11-09-evoking-ii-parts-4-5-design.md`](2026-11-09-evoking-ii-parts-4-5-design.md) ("the
Parts 4–5 spec": job claims, posture, rescues); and the spell framework,
[`2026-09-29-infinity-design.md`](2026-09-29-infinity-design.md).
**Amends:** the parent spec's §3.1 (the `TEAM_MOVE` synced byte becomes a server-only field;
`CASTER` stays synced), §7.1 (the packet's shape), §7.2 (where team-move state lives), §7.3 (who
shows "Team moves are automatic", and two more refusal messages), §8 (roster rules, trigger
order and fall-through, each move's behavior in buildable terms, the stray "H key"), §9.1 (the
Caster's goals, its casting time, which gear it uses), §9.2 (durations as constants; who an
evoker's Mirror confuses; the scroll's drop rule), §9.3 (the stand-ins, the Mirror effect ends
with its last image, confusion applies to any mob), §9.4 (the Mixin target, flight shared with
Infinity, which damage still lands, mob carriers' steering), §9.5 (which deaths leave a ghost,
one ghost per player, the ghost is a non-saved `LivingEntity`), §10 (Ghostrail's fallback), §11
(Part 6 and 7 tests) and §13 (rows "Move gear", "Ghost trigger", "Which villagers leave
ghosts"). The parent carries inline "Amended by" notes at those places, added in the same
change. `CLAUDE.md`'s packet rule gains the second request kind (Part 6) and its Mixin list the
Ghostrail Mixin (Part 7).

## Summary

The last two parts of the Bound Vex design, built one after the other, each with its own plan,
branch and pull request.

**Part 6, team moves.** Your Bound Vexes can act as a team. When something big comes close they
grab conjured shields and take turns bashing it back (**Shield**); when a strong monster goes
after one of them, that vex and two friends pull out crossbows that fire three arrows at once
(**Multishot**); when you or a friend drop below half health, up to three vexes circle the hurt
one, shove away anything that comes close and knock arrows back (**Evader**); and when a giant
(a Ravager, a Warden, the Wither, the Dragon) shows up and all twelve vexes are out, they gear up
in netherite and diamond, line up in a 3 × 4 block and charge together while the crossbow vexes
fire rockets (**Raid**). The conjured gear vanishes when the move ends and your own gear comes
back. A server setting turns the automatic triggers off; then **J** picks a move and **K** starts
it.

**Part 7, Casters, two new spells, villager ghosts.** One Bound Vex in three is a **Caster**: it
wears a torn purple robe, never fights, and casts two new spells on you. **Mirror** surrounds
you with thirteen copies of yourself for six minutes; a monster that picks you as its target
almost always picks a copy instead. **Ghostrail** lets you fly through walls, unhurt, for four
minutes, leaving a trail of white smoke. Both are new scrolls you can scribe and read yourself.
Evokers, vindicators and pillagers sometimes carry one of the scrolls and use it on you, and drop
it when you kill them. And a villager that monsters kill leaves a **ghost**: when you are in
trouble near where it died, it floats over, casts the spells on you, and fades when the fight is
over, ready to come back next time.

Some details in the parent design cannot be built as written in 26.3: a crossbow fires only what
is loaded into it, a rocket with no explosion does no damage, villager textures cannot be drawn
see-through by vanilla's layer, `RenderType.entityTranslucent` is now `RenderTypes`, the client
cannot read the server's team-move setting, and a Ghostrail effect on an evoker would not take it
through a wall. Each is resolved below.

## Glossary

| Term | Meaning |
|---|---|
| **Bound Vex**, **summoner**, **ally**, **guard area**, **stay area**, **conjured gear**, **given gear**, **stash**, **job goal**, **claim**, **posture** | As in the parent, Part 3 and Parts 4–5 specs. |
| **Team move** | One of Shield, Multishot, Evader, Raid: several Bound Vexes acting together with conjured gear, under one record per summoner. |
| **Roster** | The vexes taking part in a move. |
| **Free vex** | A Bound Vex that may join a move: §2.2. |
| **Move enemy** | The hostile a move is fought against (Shield, Multishot, Raid). Evader has a **ward** instead. |
| **Carrier** | Whoever has a Mirror or Ghostrail effect (§7). Not the same as a Caster. |
| **Ward** | The one an Evader protects: the summoner or an ally below half health. |
| **Kit** | The conjured gear of a move. |
| **Caster** | A Bound Vex rolled at summon (1 in 3) to cast scrolls instead of fighting. |
| **Mirror image** | `spellcraft:mirror_image`: one of the thirteen copies a Mirror makes. Not a mob; it wanders and can be hit. |
| **Stand-in** | A client-only entity, never added to the level, that a renderer copies an image's pose into so it can draw the image as a player or a mob. |
| **Ghostrail** | The effect that makes its carrier pass through blocks, fly, and take no damage. |
| **Ghost record** | A saved note of where a villager died to a monster and what it looked like. |
| **Villager ghost** | `spellcraft:villager_ghost`: the spirit drawn from a ghost record, which helps a player in need. |
| **In need** | Below half health, or three or more hostiles within 8 blocks. |

## 1. Scope and delivery

| Part | Branch | What lands | Sections |
|---|---|---|---|
| 6 | `feat/evoking-ii-part-6` (from `main`) | The packet's second kind, the setting, J and K, the four team moves | §2–§5 |
| 7 | `feat/evoking-ii-part-7` (from `main` after Part 6 merges) | Casters, the Mirror and Ghostrail spells, mob scrolls, villager ghosts, `1.3.0` | §6–§11 |

One plan and one PR per part. Will asked for each PR to be merged once it is green, so Part 7
starts from a `main` that already holds Part 6 (no stacked branches).

Out of scope: any clientbound packet; any config beyond `teamMoves`; spawn eggs; new sounds or
models beyond the robe texture and the ghost's tint.

## 2. Part 6: the shared machinery

### 2.1 State

- **`TeamMoves`** (`evoking` package): a transient map summoner UUID → **record**: the move kind,
  the roster (vex UUIDs), the move enemy's or ward's UUID, the start game time, a phase (Raid
  only, §4.4), and per-kind cooldown ends (game time). Kept like `EvokingCaster`'s `CASTINGS`:
  dropped on logout and server stop. It is not saved: a move does not survive a restart.
- **`BoundVex.teamMove`**: a plain server-side field (`TeamMove` enum, `NONE` by default), not
  synced and not saved. **Amends the parent §3.1/§7.2:** nothing on the client reads a team move,
  so the synced byte would be unused. A vex loaded from a save or carried through a portal is a
  new entity with `NONE` and its kit already dismissed (`readAdditionalSaveData` dismisses every
  conjured slot today); the record drops it at its next check (§2.5).

### 2.2 Free vexes and rosters

A **free vex** is a Bound Vex of the summoner that is alive, in the summoner's level, within
32 blocks of the summoner, following (not staying), not a Caster (Part 7; a Caster is free only
for a Raid), with `teamMove == NONE`, not holding any job claim (repair, cure, bribe, crystal: a
vex mid-job finishes it), and not the current rescuer. The job test is a new `Jobs.busy(vex)`
that asks each of the four job goals' claim maps whether any live claim is held by that vex (a new
`Claims.heldBy(BoundVex)`; the maps are keyed by subject, so it scans their values).

Roster size and choice (the pure `TeamMoveRules`, §5):

| Move | Free vexes needed | Roster | Chosen |
|---|---|---|---|
| Shield | ≥ 3 | 9 when ≥ 9 are free, else 3 | nearest to the enemy |
| Multishot | ≥ 3, including the targeted vex | the targeted vex + 2 | the 2 nearest to the targeted vex |
| Evader | ≥ 1 | min(3, free) | nearest to the ward |
| Raid | 12 | all 12 | — |

### 2.3 Triggers (automatic mode)

Each summoner is checked every **10 ticks** (from `TeamMoves.tick`, §2.5, on a stored game-time deadline kept apart from the record, not the summoner's
`tickCount`: a mock player is never ticked), only when no move is running and the summoner is present (online, alive, not a
spectator). Candidates are hostiles that are `isPrey` and `huntable` for the summoner (an
`Enemy`, not a creeper, no other player's mark) and whose feet are inside the guard area of at
least one following vex of the summoner. In this order, the first that can be started wins:

1. **Raid:** a candidate with `getMaxHealth() ≥ 99`, 12 free vexes, Raid not cooling.
2. **Shield:** a candidate with `getMaxHealth() ≥ 31`, ≥ 3 free vexes, Shield not cooling. A
   ≥ 99 enemy that cannot start a Raid (fewer than 12 free, or Raid cooling) falls through to a
   Shield.
3. **Multishot:** a candidate whose `getTarget()` is a free Bound Vex of the summoner and whose
   `ATTACK_DAMAGE` attribute exists and is ≥ 6 (`getAttribute` null-checked: ghasts, shulkers,
   the dragon have none), ≥ 3 free vexes counting that one, Multishot not cooling. Detected by
   this scan, not by `LivingChangeTargetEvent`: a brain mob (a piglin brute, the warden) writes its
   target memory without always firing the event, but `getTarget()` reads it either way.
4. **Evader:** the summoner, or a living ally within 16 blocks of the summoner (`Allies.isAllyOf`,
   but never a mob carrying a summoner mark: a Bound Vex or an image is not warded), with
   `health < maxHealth / 2`, Evader not cooling, ≥ 1 free vex. The lowest health fraction wins;
   the summoner wins ties.

Among several candidates for one move, the nearest to the summoner wins. One move at a time per
summoner.

### 2.4 Starting a move

The roster is chosen (§2.2); each member gets `teamMove`, `setTarget(enemy)` (Shield, Multishot,
Raid; the target goals may later move it, the move does not read the target), and its kit
conjured (`BoundVex.conjure`, which stashes given gear). The summoner gets the overlay
`message.spellcraft.team_move_<kind>`:

| Key | Text |
|---|---|
| `team_move_shield` | "Your vexes form a Shield!" |
| `team_move_multishot` | "Your vexes ready a Multishot!" |
| `team_move_evader` | "Your vexes form an Evader guard!" |
| `team_move_raid` | "Your vexes Raid!" |

### 2.5 Running and ending a move

`TeamMoves.tick(ServerPlayer)` (public, so GameTests can step it: a mock player is never ticked
by the server and fires no `PlayerTickEvent`) runs every tick from the summoner's
`PlayerTickEvent.Post`; it runs the §2.3 trigger scan on its 10-tick clock and checks the record. The
record is read with `get` and created only when a move begins or an automatic scan is due, so a refusal
leaves no empty record. The Evader's shoves and deflections run from this method, not from the members' goals. Roster
members that are dead, removed, in another level or no longer marked with this move are dropped
from the roster (their kits were dismissed by whatever removed them, or are dismissed now). The
move **ends** when any of these holds:

- the roster is empty;
- the summoner is absent (offline, dead, a spectator, in another level);
- the move enemy is dead, removed, in another level or more than **48 blocks** from the summoner
  (`TeamMoveRules.tooFar`);
- Evader: the ward is dead, removed, in another level, more than 48 blocks away, or at
  `health ≥ maxHealth / 2`;
- the time limit: 120 s (2400 ticks), Evader 60 s (1200 ticks).

On end, every remaining member's kit slots are dismissed (`BoundVex.dismiss`, which returns the
stash), its `teamMove` is `NONE`, and that move kind's **30-second** cooldown starts. No message
at the end. A move member that dies drops no kit (conjured slots never drop, Part 3 spec).

**Goal.** `TeamMoveGoal` at goal priority **1** (between the flee/dodge goals at 0 and the job
goals at 2; `RescueGoal` at −1 still outranks it), flags `MOVE` and `LOOK`, `canUse` while
`teamMove != NONE` and the vex's record lists it. It runs the move's behavior (§4) every tick. A
fleeing or rescuing member pauses its part and resumes; the move does not end for it. Its `stop`
dismisses the kit **only when the record no longer lists the vex** (a pause keeps the kit), and
its `start` and `tick` re-conjure any kit slot that is not conjured or holds something else. As built,
a rescue does not conjure its bucket or apple over a kit item (nothing is conjured while the main hand
holds one), so a rescue never dismisses a kit slot; what can dismiss one is the `stop` of a preempted
job goal (which dismisses its own hand), and the re-conjure is the defensive repair for that.

### 2.6 What a member refuses

The take-back click and the stay click on a move member answer the overlay
`message.spellcraft.vex_busy` ("That vex is busy") and change nothing (parent §4.4). A gift to a
conjured slot already waits in the stash (Part 3 spec), so giving gear needs no change.

## 3. Part 6: the setting, the keys and the packet

### 3.1 The setting

`ServerConfig.TEAM_MOVES`: `defineEnum("teamMoves", TeamMoveMode.AUTOMATIC)` in a
`[evoking]`-style section with a comment; `AUTOMATIC` | `MANUAL`. In `MANUAL` the §2.3 triggers do
not run.

### 3.2 The packet

`EvokingRequest(Kind kind, byte arg)`: `Kind.CAST` with `arg` the `EvokerSpell` ordinal, and
`Kind.TEAM_MOVE` with `arg` the `TeamMove` ordinal (Shield 0 … Raid 3; `NONE` is the last
constant, so 4 is refused; `EvokingRequest.cast` and `EvokingRequest.teamMove` are the factories). The codec writes the kind
as an enum and the byte as a byte; `EvokingNetwork.VERSION` becomes `"2"`. The server validates in
full: an `arg` out of range for its kind is ignored (logged at debug); a `CAST` goes to
`EvokingCaster.request` exactly as today. Still one packet, client → server. `CLAUDE.md`'s rule
reads "(two request kinds: cast, team move)".

### 3.3 The keys

Two key mappings in the Spellcraft category, registered beside the casting keys in
`EvokingKeys`:

- **J**, `key.spellcraft.team_move_cycle` ("Evoking: Next Team Move", `InputConstants.KEY_J` = 13):
  cycles a client-side selection Shield → Multishot → Evader → Raid → Shield (starting at Shield
  each session) and shows `LocalPlayer.sendOverlayMessage` of
  `message.spellcraft.team_move_selected` ("Team move: %s") with the move's lang name
  (`team_move.spellcraft.shield` …).
- **K**, `key.spellcraft.team_move_go` ("Evoking: Team Move!", `KEY_K` = 14): sends
  `TEAM_MOVE(selected)`.

**Amends the parent §7.3:** J always cycles, and "Team moves are automatic" is the server's
answer to K. NeoForge does sync server configs to clients, but the server is the one that decides
whether a move starts, so it is the one that answers: one source of truth, and a key that works
the same before and after a config reload. Neither key needs the swap-offhand swallow (neither defaults to F).

### 3.4 The manual request

On `TEAM_MOVE(kind)` the server ignores a dead or spectating sender (as for a cast) and does
**not** require the Evoking effect: the vexes outlive it, and automatic moves do not need it
either. Then, in order, the first that applies is the overlay answer:

1. Mode `AUTOMATIC` → `message.spellcraft.team_moves_automatic` ("Team moves are automatic").
2. A move is running → `message.spellcraft.team_move_running` ("Your vexes are already in a team
   move").
3. That kind is cooling → `message.spellcraft.team_move_cooling` ("That team move is not ready
   yet").
4. No roster or no subject → `message.spellcraft.team_move_unavailable` ("Not enough vexes, or no
   enemy for that move").
5. Otherwise the move starts (§2.4) with its usual message.

A summoner's requests are throttled: one fewer than **10 ticks** after their last handled request is
ignored without an answer (`TeamMoves.REQUEST_INTERVAL`), so a modified client cannot make the server
scan 16 blocks and answer on the action bar many times a tick.

The subject is the nearest hostile within **16 blocks** of the summoner that is `isPrey`,
`huntable` and fits the move: Shield `maxHealth ≥ 31`, Raid `maxHealth ≥ 99`, Multishot
`ATTACK_DAMAGE ≥ 6` (it need not be targeting a vex; the targeted vex of the roster is then the
nearest free vex to it); Evader takes the ward rule of §2.3 (no enemy needed). The roster rule is
§2.2's.

## 4. Part 6: the four moves

Movement uses the vex's own `MoveControl` (`setWantedPosition`), as the charge goal does: vexes
fly and clip through blocks. "Normal speed" is the charge goal's speed modifier; "half speed" is
half of it.

### 4.1 Shield

Kit: a **shield in each hand** (`MAINHAND` and `OFFHAND`). The Part 3 block (front hits cancelled
with `SHIELD_BLOCK`, a piercing arrow passes) reads the offhand shield, so it applies unchanged.

Each member, on its own 40-tick **bash clock** (starting ready):

- **Approach:** farther than 7 blocks from the enemy: fly toward it at half speed, looking at it.
- **Lunge:** within 7 blocks and the clock ready: dash at normal speed to the enemy; when its box
  is within 1.5 blocks of the enemy's box, **bash**: `enemy.hurtServer(level,
  damageSources().mobAttack(vex), 4)`, `enemy.knockback(1.5, vex.x − enemy.x, vex.z − enemy.z,
  source, 4)` (away from the vex), `SHIELD_BLOCK` at the enemy, `Mob.swingForAttack(MAIN_HAND)`. The clock
  restarts. A lunge that has not landed after 40 ticks gives up and backs off.
- **Back off:** after a bash, fly away from the enemy until 6 blocks off, then hold there
  (hovering, looking at it) until the clock is ready. As built, the back-off aims at 7 blocks
  (`BACK_OFF_SPOT`, one past the 6) and holds from 6, because the vex's move control coasts to a stop
  near the wanted point.

The bash is a mob attack from the vex, so mercy, the backstop and End crits (melee from the vex)
apply as for any vex hit.

### 4.2 Multishot

Kit: a **crossbow** (`MAINHAND`) enchanted with Multishot (looked up from the level's enchantment
registry by `Enchantments.MULTISHOT`) and `Unbreakable`. Each member keeps its own **slot on an arc
round the enemy**, 7 blocks out at the enemy's eye height, 25 degrees a slot
(`MultishotMove.ARC_DEGREES`, the middle slot straight toward the summoner), so the shooters are not
in each other's lines: a member more than 1 block (sideways; a vex bobs up and down by itself) from
its slot flies to it, one nearer holds, looking at the enemy. As built, a plain 6 to 8 block band let
two members that happened to enter it in a line block each other for the rest of the move, so the
slot is the rule and not only a way into the band. Every
**40 ticks**, with line of sight and no friend in the way (the Part 3 hold-fire rule,
`VexShots.aimPoint` and the 1-block segment test; the private `RangedGearGoal.friendInTheWay`
moves to `VexShots` so the move can share it; fellow members of the roster do not count as friends in
the way, as in the Raid, while the summoner, allies and Bound Vexes outside the roster still hold the
fire; only the center line is tested, so a flank arrow of the fan can pass a friend 1.2 to 1.5 blocks
off it, and the backstop then cancels its damage), loads the crossbow by hand
(`CHARGED_PROJECTILES` = three plain arrows; vanilla's `draw` that would add Multishot's extra two
is not reachable) and fires it with `CrossbowItem.performShooting(level, vex, MAIN_HAND, crossbow,
1.6F, 14 − difficulty × 4, enemy)`, as a pillager does. The enchantment on the stack gives the
spread; the three arrows are owned by the vex (so the Part 3 backstop covers them), `pickup` is
disallowed, and `performShooting` plays `CROSSBOW_SHOOT` and empties the charge.

### 4.3 Evader

Kit: a shield in each hand, as Shield.

- **Orbit:** members spread evenly on a 2-block circle around the ward at its mid-height, the
  circle turning 6° per tick. As built, each steers to its slot with a position/velocity push plus
  feed-forward (named constants; the ward's own motion is its known movement, because a player's delta
  movement stays near zero on the server), because the vex move control alone (it only adds 0.05 x speed a
  tick) cannot hold 2 blocks: a plain fly-to settled at about 3.1.
- **Shove:** any hostile (`huntable` and `isPrey`) whose box comes within 3 blocks of the ward's
  is shoved by the nearest member: `knockback(2.0, ward.x − hostile.x, ward.z − hostile.z, …)`
  (away from the ward; no damage), `SHIELD_BLOCK`, `Mob.swingForAttack`. Each hostile at most once per 20
  ticks.
- **Deflect:** any `Projectile` within 2 blocks of the ward's box, moving toward the ward
  (velocity · offset to the ward > 0; as built it also counts a projectile whose next step would cross
  that range, since a full-draw arrow moves 3 blocks a tick and can jump the window between two
  samples), whose owner is not on the summoner's side (not the
  summoner, an ally, or a mob carrying the summoner's mark), and that is not a `LargeFireball`
  (Part 5's return owns those), is deflected by the nearest member:
  `projectile.deflect(ProjectileDeflection.REVERSE, vex, EntityReference.of(vex), false, 2.0)`
  (power 2.0 gives a full reversal, since `REVERSE` scales by −power × 0.5), `SHIELD_BLOCK`. Its
  new owner is the vex, so it is never deflected twice and the backstop covers it.

The ward may be the summoner or an ally; the Evader follows it if it moves.

### 4.4 Raid

Kit, rolled per member when the Raid starts (the Raid's kit dice, §5): **main hand** one of seven, even odds: netherite sword, diamond sword, netherite axe,
diamond axe, mace, trident, or a **rocket crossbow** (a crossbow, `Unbreakable`, no enchantment);
**offhand** a shield; **head, chest, legs** all netherite or all diamond (one roll per member).
Every kit item is `Unbreakable`. A **Caster** (Part 7) rolls only the armor and gets the
offhand shield; its main hand holds the conjured Mirror scroll during the gather and is conjured
**empty** during the charge (so its given gear stays stashed).

Phases (the record's phase):

1. **Gather** (40 ticks): the grid is 3 rows × 4 columns, 1.5 blocks apart, centered on the point
   8 blocks from the enemy toward the summoner, its rows facing the enemy, at the enemy's eye
   height. Each member takes a cell (in roster order; column 0 is the right-hand column as the grid
   faces the enemy, `RaidMove.cell`), flies there at normal speed and faces the
   enemy. Casters (Part 7) cast Mirror on the summoner here (§6.2).
2. **Charge** (to the end): melee members fly at the enemy at normal speed and attack it with
   `doHurtTarget` when their box intersects the enemy's (the charge goal's reach rule), each on its
   own **20-tick attack clock** (the charge goal has no cooldown; without one a member pressed
   against the enemy would hit every tick);
   melee members steer in close (a spring plus damping inside 3 blocks of the enemy keeps the 20-tick
   hit cadence); rocket members keep 8 blocks (hold between 7 and 9), and every 40 ticks with line of sight and
   no friend in the way (fellow members of the same Raid do not count as friends in the way:
   `VexShots.friendInTheWay` takes the roster; the summoner, allies and Bound Vexes outside the roster
   still hold fire) fire a **rocket**: one `FIREWORK_ROCKET` carrying `FIREWORKS` = flight 1, one
   `FireworkExplosion.DEFAULT` (a rocket with no explosion deals no damage), spawned shot-at-an-angle
   from the vex's eyes and aimed straight at `VexShots.aimPoint` at speed 1.6 and inaccuracy 1.0
   (the crossbow is the kit item and plays the sound; `performShooting` with a target would lift the
   aim by a fifth of the flat distance for an arrow's drop, and a rocket has no drop, so it would fly
   over a short enemy). The blast hurts every
   living thing within 5 blocks in sight; its damage source's entity is the vex, so the Part 3
   backstop cancels it for the summoner and everything the vex may not fight, and, for a rocket, for
   everything it may not hunt (`huntable` and `isPrey`): farm animals and neutral mobs near the enemy
   are spared. Casters hold their
   grid cell during the charge and do not attack.

A Raid whose roster falls below 12 continues (parent §10).

## 5. Part 6: pure core, errors, tests, docs

**Pure core (`spell.rules`, TDD, jqwik):** `TeamMoveRules`: `rosterSize(move, free)` (the §2.2
table: Shield 3 or 9 or none, Multishot 3 or none, Evader `min(3, free)` or none, Raid 12 or
none); `pick(maxHealth, attackDamage, targetsFreeVex, raidReady, shieldReady, multishotReady)`
for the enemy-side order of §2.3 including the fall-through; `endsAt(move, startTick)`. Properties:
a roster never exceeds the free count; a Raid roster is exactly 12; a ≥ 99 enemy is never a
Multishot when a Shield could start; no move is picked while its own readiness is false.
`TeamMove` (the enum) lives in `spell.rules` so the pure rules can name it.

**Errors:** a roster member that dies, is unloaded or portals away leaves the roster (§2.5); the
move ends when its roster is empty. An enemy that portals away ends the move. A summoner who logs
out mid-move: the record is dropped on logout, and each member's goal ends because its record is
gone; the goal's `stop` dismisses the kit. `/summon`ed vexes have no summoner and never join. A
server stop drops all records; a member saved mid-move loads with its kit dismissed
(`readAdditionalSaveData` dismisses every conjured slot) and `teamMove` `NONE`.

**Dice.** The Raid's kit roll goes through a static, test-scriptable dice source (`script(int...)`
/ `unscript()`, empty in play), the same shape the bribe's per-vex dice use but global, since the
vexes it rolls for are chosen at the start. The Raid's dice tests run in their own GameTest environment
(`team_move_dice`) because the script is global; the 32-block free range is tested by `rangeIsThirtyTwo`
(the player, not the vex, is moved). Part 7 adds one each for the Caster roll, scroll mobs
and the Mirror confusion.

**GameTests** (new class `BoundVexTeamMoveGameTests`; the tests step `TeamMoves.tick(player)` by
hand; `TeamMoves` gets a public `startForTest` seam only if a trigger cannot be reached; prefer
real triggers):

- Each automatic trigger starts the right move with the right roster and kit: a ravager
  (`maxHealth` 100) beside 12 free vexes → Raid with 12, each with a main-hand kit item, an offhand
  shield and three armor pieces; the same ravager beside 4 free vexes → Shield with 3; an enderman
  (40) beside 9 → Shield with 9; a vindicator (`ATTACK_DAMAGE` 5) targeting a vex → none, a
  zombie with the attribute raised to 6 targeting a vex → Multishot with that vex in the roster; a
  summoner at 40 % health → Evader with up to 3; a villager at 40 % within 16 blocks → Evader
  warding it; a Bound Vex at 40 % → no Evader.
- Staying vexes, Casters (Part 7: in a Raid only), vexes in a job, and the rescuer are not taken.
- The overlay message is sent once per start.
- A member's given gear is stashed while the kit is out and back in its hands at the end; the kit
  never drops when a member dies.
- End rules: enemy dead; enemy 49 blocks from the summoner; time limit (with a test-shortened
  clock seam if 2400 ticks is too long for the batch); Evader ward healed. Each starts the
  30-second cooldown: the same trigger right after does not start that move, another
  kind still can.
- A summoner who logs out mid-move: every member's kit is dismissed and its `teamMove` is `NONE`.
- Shield (the enderman scene: a ravager resists 75 % of knockback): a member bashes for 4 with
  knockback away from it and backs off to about 6; a hit from the front is blocked.
- Multishot: one shot spawns three arrows owned by the vex, the crossbow keeps no durability loss
  and is empty after.
- Evader: a zombie walked within 3 blocks is shoved away; an arrow shot at the ward by a skeleton
  is reversed and owned by the vex; an arrow shot by the summoner is not touched.
- Raid: members reach their grid cells during the gather; a rocket member's rocket explodes on the
  enemy and does not hurt the summoner standing 3 blocks from it.
- The take-back and stay clicks on a member answer `vex_busy` and change nothing.
- Manual mode (config set by the test and restored): K with no enemy → `team_move_unavailable`;
  K with a running move → `team_move_running`; K during a cooldown → `team_move_cooling`; K in
  automatic mode → `team_moves_automatic`; K with a fitting enemy and roster starts the move; the
  automatic triggers do not run in manual mode.
- The packet: `FangGameTests.requestCodecRoundTrip` and `EvokingKeys` build `new
  EvokingRequest(spell)` today and change to the new shape; the round-trip test gains a
  `TEAM_MOVE` case, and a `TEAM_MOVE` with an out-of-range byte is ignored; `CAST` still casts.

`runData` proves the two new key mappings load. `./gradlew pitest` with a `docs/mutation-testing.md`
row.

**Playtest (Will; "Evoking II (Part 6)"):** each move's look and message (Shield bashes and the
back-off, three-arrow volleys, the Evader circle and deflections, the Raid grid and charge,
rockets), that J and K feel right, and that a move does not fight you for control of your vexes.

**Docs:** README (team moves, J/K, the setting), mod description, `docs/api-notes.md` §25 (every
vanilla fact used: `performShooting`, `ChargedProjectiles`, `Fireworks`, `deflect`/`REVERSE`,
`knockback`, `getAttribute`, `defineEnum`, the key scancodes, `LocalPlayer.sendOverlayMessage`),
playtest checklist, mutation testing, the parent's inline notes, `CLAUDE.md` (packet rule).

## 6. Part 7: the Caster

### 6.1 The roll and the mark

`VexSummons.summon` rolls each new Bound Vex **1 in 3** for Caster through the Caster dice (§5's
scriptable global dice). `BoundVex.CASTER` is a synced boolean (the client draws the robe) and saved
in `addAdditionalSaveData` (a portal copy keeps it). A `/summon`ed vex is not a Caster.

### 6.2 What a Caster does

- **Goals:** its target selector is a normal Bound Vex's. In the goal selector, the charge goal
  and `RangedGearGoal` are replaced by **`CasterGoal`** (priority 3): with a target, keep **4–6
  blocks** from it (back off, close in, hold), looking at it; it never melees or shoots. Every
  other goal stays: follow, stay, posture, flee, dodge, jobs, rescues, `SupportGearGoal` (healing
  potions are support, not fighting) and the given-gear rules (armor shows, a shield blocks).
  Because goals are added in `registerGoals` (which runs in the constructor, before `CASTER` is
  known), both sets are registered and each `canUse` checks `isCaster()`.
- **Casting** (a `CasterSpellGoal` at priority 2, beside the job goals, needing no target):
  - **Mirror** on the summoner when ≥ 2 hostiles (`huntable`, `isPrey`) are in the Caster's guard
    area, the summoner is within 16 blocks and has no Mirror effect, and this Caster's Mirror
    cooldown (6 minutes, 7200 ticks, saved as a game time) is over.
  - **Ghostrail** on the summoner, or on a Bound Vex of the same summoner, within 16 blocks, that
    is below a quarter of its max health or (the summoner only) `isInWall()`, has no Ghostrail
    effect (a vex clips through blocks all the time, so being in a wall is no emergency for one), and this
    Caster's Ghostrail cooldown (4 minutes, 4800 ticks) is over. The summoner first.
  - The cast takes **20 ticks**: the Caster holds a conjured scroll of the spell in its main hand
    (stashing the given item), carries the casting effect (§6.4), and plays
    `EVOKER_PREPARE_SUMMON` at the start and `EVOKER_CAST_SPELL` at the end. At the end, if the
    subject still qualifies (alive, in range, without the effect), the spell is applied to it
    (§7.1) and the cooldown starts; either way the scroll is dismissed. A Caster interrupted
    (flee, rescue) dismisses the scroll and spends no cooldown.
- **Team moves:** a Caster joins only a Raid (kit: §4.4). In the Raid's gather it casts Mirror on
  the summoner at once (no 20-tick wind-up; the scroll is its gather kit) if the summoner has no
  Mirror; this ignores and then sets its Mirror cooldown. It holds its grid cell during the charge
  with an empty conjured main hand.

### 6.3 The robe

`BoundVexRobeLayer` (`client.evoking`), added to `BoundVexRenderer`, draws the vex model again
with `textures/entity/bound_vex_robe.png` for a Caster (`BoundVexRenderState.caster`), scaled up
slightly (about 1.06 around the body, the plan's number and Will's eye) so it never z-fights the
body, with `RenderTypes.entityCutout` (26.3 has no `entityCutoutNoCull`; `entityCutout` is the
no-cull variant beside `entityCutoutCull`). The texture uses the vex texture's UV layout (its size
read from `VexModel.createBodyLayer`), painted only on the body and arm faces: purple
(`#5B2A86`) with magenta (`#C03FA8`) trim, a ragged transparent lower edge; head and wings
transparent. It is this mod's own art, generated by `scripts/robe_texture.py` (with a unit test
like the icon script's: size, transparency where the head is), committed as a PNG.

### 6.4 The casting look

A new marker effect **`spellcraft:casting_scroll`** (hidden from the HUD like the other casting
effects, with a new particle type the client maps to a purple `#B24CFF`) is put on any non-player
caster for its wind-up (Casters, villager ghosts). `CastingState.of(entity)` returns
`Optional<EvokerSpell>` today, and `EvokerSpell`'s ordinals are the packet's `CAST` argument, so it
is not extended: `CastingState.of` returns a new client-side `CastLook` enum (`FANGS`, `VEXES`,
`SCROLL`) instead, and its callers map from it.
`CastingParticles` then draws the two hand particles for **every living entity in render range**
whose `CastingState` is present, not only players (hand offsets from `EvokerPose`, scaled by the
entity's size). The arms-up pose is drawn for Bound Vexes only: a `BoundVexModel extends VexModel`
overrides `setupAnim` and, when `BoundVexRenderState.casting` is set, raises both arms with
`EvokerPose.armRotation`. (The villager model's arms are one crossed part; the ghost shows the
particles and the sounds only.)

## 7. Part 7: the two spells

### 7.1 Declarations, scrolls, casting by others

Both spells follow the framework recipe: `SpellDeclarations.MIRROR` (`mirror`, `AMAZING`,
`REFUSE`, dispellable, not redundant in creative) and `GHOSTRAIL` (`ghostrail`, `EXTREME`,
`REFUSE`, dispellable, redundant in creative); `ALL` and `Spells.ALL` become `[UPSIDE_DOWN,
INFINITY, TRAILING, EVOKING, MIRROR, GHOSTRAIL]`; `ModEffects` registers `MIRROR` (a
`MirrorEffect`) and `GHOSTRAIL` (a `GhostrailEffect`), both milk-curable; `ModItems` registers both
scrolls; `scripts/spell_icons.py` gains both glyphs (Mirror: magenta `#E066FF`, two facing
figures; Ghostrail: pale `#DDE6FF`, an arrow passing through a wall; the plan draws the rows);
lang, item models and item definitions as for the others. Durations are constants (Mirror 7200,
Ghostrail 4800 for players): no new config (parent §1).

**Casting by others.** `Spells.cast(LivingEntity target, Spell spell, int ticks)` (extracted from
`SpellScrollItem.apply`) applies the effect (`MobEffectInstance(effect, ticks, 0, false, false,
true)`); the scroll's own path calls it with the spell's duration. There is no separate on-apply
action: what a spell does when it starts lives in its effect (`MirrorEffect.onEffectAdded` spawns
the images, `MobEffect.java:108`; Ghostrail's per-tick sync grants flight), so a scroll, a Caster,
a mob, a ghost and `/effect give` all behave the same. The plan verifies when vanilla calls
`onEffectAdded` (a fresh add, not a reload from a save). Casters, mobs and ghosts call it directly; the cast rules (creative refusal, recast) are
the scroll's, and other casters check "no effect yet" themselves.

**Tests that change:** `SpellDeclarationsTest`, `CastRulesTest.decidesEveryCase` (rows for both),
and `ScribingGameTests`' top-tier draws (lines that expect only the Infinity scroll now accept
Infinity or Ghostrail; the lower pool gains Mirror). `SpellAssetsTest` iterates `ALL` and needs no
edit.

### 7.2 Mirror

In this section the **carrier** is whoever has the Mirror effect (a player, a Caster's summoner,
an evoker), not the Caster vex.

- **On apply** (`MirrorEffect.onEffectAdded`, any `LivingEntity` carrier): spawn **13** Mirror images on the ring cells
  (`VexRing`, Chebyshev 2–3, standable ones first, any free cell else) around the carrier. Each
  image is given copies of the carrier's main-hand and offhand items at spawn (`setItemSlot`,
  which vanilla syncs), so the renderer needs nothing from the carrier.
- **The effect ends with its images.** Each image watches its carrier: when the carrier is loaded
  in the image's level and no longer has the Mirror effect (expiry, dispel, milk, `/effect
  clear`), the image **poofs** (`POOF` particles, no sound, `discard`). When the last live image of
  a caster dies, the caster's Mirror effect is removed. So "no images alive" and "no Mirror
  effect" are the same thing, and the Caster's and ghost's "no images" test reads the effect.
- **Images** (`MirrorImage extends LivingEntity`, `spellcraft:mirror_image`, `MobCategory.MISC`,
  sized like the caster at spawn, synced `copiedType` (the carrier's type id) and an optional
  `ResolvableProfile` (players), saved `caster` UUID (the carrier) and `lifeTicks`; attributes
  `MAX_HEALTH` 10 and `MOVEMENT_SPEED` 0.25 registered in `EntityAttributeCreationEvent`):
  - health 10, no armor, no attack, not pushable by the caster's images (they overlap freely);
  - **6-minute life** of its own (`lifeTicks` 7200 counted down and saved), then poofs, so an
    image whose caster is unloaded or gone still ends;
  - wanders within 6 blocks of its caster at walking speed (its own `serverAiStep`: pick a random
    point within 6 blocks of the caster every 60–120 ticks and walk to it with `xxa`/`zza` and
    `setSpeed(0.1)`; it does not pathfind, it walks and jumps one block), and faces where the
    caster faces when idle;
  - hurt, it flashes and is knocked back (vanilla); at 0 health the vanilla death fall, then
    vanilla's poof; no drops (`noLootTable`) and no experience;
  - for a **player** caster it carries the player's summoner mark (`AllyAttachments.SUMMONER` =
    the player), so `Allies` treat it as the player's own (spells spare it), golems never target
    it, and its (nonexistent) damage counts as the player's side. A **mob** caster's images carry
    no mark;
  - `/summon spellcraft:mirror_image` with no caster discards itself on its first tick.
- **Looks** (`MirrorImageRenderer`, `client.evoking`): keeps a client-side **stand-in** per image,
  created once: for a player image, a mannequin (`EntityTypes.MANNEQUIN.create(clientLevel, …)`,
  given the image's `ResolvableProfile` through a `PROFILE` item component, its description label
  hidden; the skin loads asynchronously, so the first frames may show the default skin); for a
  mob image, `copiedType.create(clientLevel, …)`, also never added to the level. Each frame
  `extractRenderState` copies the image's position, rotations (and their previous values), hurt
  and death time, walk animation, pose and the image's own (synced) held items into the stand-in, asks the
  dispatcher's renderer for the stand-in's state, and `submit` hands that state to
  `EntityRenderDispatcher.submit`. The plan verifies the mannequin path; the fallback is the
  default player skin.
- **Confusion:** on `LivingChangeTargetEvent` (`MOB_TARGET` or `BEHAVIOR_TARGET`) where the new
  target is a carrier with live images within 16 blocks of it **and differs from the mob's current
  target** (a target goal re-sets its target every tick, `TargetGoal.java:70`, and each re-set
  fires the event; rolling on those would redirect almost every mob), the new target is replaced,
  with probability **13/14** (the confusion dice), by a random one of those images. An **iron
  golem is never redirected onto a player's images**: they carry the player's mark, which
  `GolemMercy` already refuses as a golem target. **Amends the parent §9.3:** any
  mob, not only hostiles (a golem going for an evoker with images is confused too; a mob picking a
  player is usually hostile anyway). A mob already targeting an image is not re-rolled. Brain mobs
  that write their target without the event are not confused (accepted; a playtest note).

### 7.3 Ghostrail

- **No physics (players):** a common Mixin on `Player.tick`, `@WrapOperation` on the **first**
  `isSpectator()` call (ordinal 0, the line `this.noPhysics = this.isSpectator()`,
  `Player.java:243`), `require = 1`, returns `original || player.hasEffect(GHOSTRAIL)`. It runs on
  both sides (`LocalPlayer.tick` reaches `Player.tick`; the effect is synced), so the client does
  not rubber-band and the server accepts the moves (`ServerGamePacketListenerImpl` skips its
  "moved wrongly" check for `noPhysics`).
- **Flight (players):** a player's `mayfly` comes from the game mode, Infinity or Ghostrail.
  The abilities sync of `InfinityAbilitySync` is generalized into one per-tick sync that grants
  what any source needs and, when a source ends, restores the game mode's abilities
  (`gameMode().updatePlayerAbilities`) and re-grants what the others still need, then
  `onUpdateAbilities()`. The decision is a pure function (`GrantedAbilities.decide(gameMode,
  infinity, ghostrail, granted)`, extending `InfinityAbilities`); Ghostrail grants `mayfly`
  and `flying`, and is tracked by its own **saved** `GRANTED`-style mark (as Infinity's is), so a
  revoke is never applied twice and a relog mid-effect does not leave `mayfly` on for good.
  `flying` is set on apply and re-asserted every tick while the effect lasts, as spectator mode
  does: `noPhysics` skips collision, so a player who is not flying would sink through the floor.
- **No damage:** `LivingIncomingDamageEvent` is cancelled for a carrier except
  `DamageTypes.GENERIC_KILL` (`/kill`) and `DamageTypes.FELL_OUT_OF_WORLD` (a ghost who dives
  under the world still dies there).
- **Trail:** every tick, 2 `WHITE_SMOKE` particles at the carrier's previous position
  (`sendParticles`).
- **Start spot:** when the effect is added (`onEffectAdded`), the carrier's block position is
  remembered (an attachment, not saved) if it is a standable spot. `onGround` is not updated while
  `noPhysics` moves an entity (`Entity.java:748-754`), so positions are not recorded during the
  effect.
- **Ending** (expiry, dispel, milk; detected by the same per-tick poll, which also catches
  `/effect clear`): if the carrier's box collides with blocks, search outward (up to 8 blocks) for
  a free spot its box fits on a standable block, else any free spot, and teleport there; if none,
  to its start spot if that is still free; if none, stay. Then flight is revoked as above and `fallDistance` is 0. A
  player who ends in mid-air falls from there.
- **Mob carriers:** a carrier that is not a `Vex` or a `VillagerGhost` (those fly through blocks
  already; `Vex.noPhysics` is true only inside its own tick, so the flag cannot be the test) gets
  `noPhysics = true` and `setNoGravity(true)` each tick, and on the end `noPhysics = false` and
  its **original** `NoGravity` back (from the mark), then the push-out. Pathfinding would walk
  around the wall Ghostrail is for, and `Mob.serverAiStep` overwrites a wanted position set from
  outside the goals, so **scroll-carrying mobs** (§8) get a `GhostrailChaseGoal` (installed on
  `EntityJoinLevelEvent`, goal priority 0, flag `MOVE`): while the mob has Ghostrail and a target,
  it stops the navigation and sets the mob's velocity straight at the target's middle at 0.3
  blocks per tick (no gravity, so it can rise and sink). Any other mob given Ghostrail by command
  just loses collision and walks its normal path.
- **Rescues:** the lava pull, the drowning push and the void catch (Part 5, `VexRescues`) skip a
  Ghostrail carrier: it takes no damage, and a pull or catch would fight its flight.
- **Who drives it:** `Ghostrail.tick(LivingEntity)` (public, so GameTests step it) runs the
  trail, the flight sync and the end check: for players from `PlayerTickEvent.Post`, for other
  carriers from `EntityTickEvent.Post` for entities carrying the saved **ghostrail mark** (set on
  apply, holding the mob's original `NoGravity`; its presence with no effect is how the end is
  noticed after expiry, milk or `/effect clear`).
- **Durations** (`GhostrailDuration.of(kind)`, pure): player and Bound Vex 4800, hostile mob 40,
  villager ghost infinite (`MobEffectInstance.INFINITE_DURATION`).

## 8. Part 7: mobs that carry scrolls

- **Who:** on `FinalizeSpawnEvent` (any spawn reason; it runs before vanilla's own equipment, which
  fills only the main hand), an `Evoker` gets a scroll in the **offhand** 1 time in 4, a
  `Vindicator` or `Pillager` 1 time in 10; Mirror or Ghostrail at even odds (dice). The offhand
  drop chance is set to **0.5**: vanilla drops it only on a player's kill (with Looting raising
  it), which is "the loot path for players" of the parent.
- **When:** on `LivingChangeTargetEvent` where such a mob goes from no target to a target (the old
  target null, the new one not) and that spell's cooldown is over (an attachment of game times,
  saved):
  - **Mirror:** cast on itself (13 images of itself; cooldown 7200 ticks), unless it has the
    effect;
  - **Ghostrail:** cast on itself for **40 ticks** (cooldown 600 ticks, 30 seconds).
  The cast is instant: `EVOKER_CAST_SPELL` and a burst of 10 purple `ENTITY_EFFECT` particles. The
  scroll is not used up.
- A mob that loses its target before casting casts the next time it gets one (the rule above
  already does this).

## 9. Part 7: villager ghosts

- **Record:** `LivingDeathEvent` for a `Villager` whose killer (`source.getEntity()`) is an
  `Enemy` that is not a player's side (a villager a zombie then converts still leaves a record: it
  died to a monster, and the event fires before the conversion) (`Allies.isPlayersSide`, moved out of `GolemMercy`, which
  now calls it). **Amends the parent §9.5:** only monsters leave ghosts; a villager killed by
  lava, a fall or a cactus leaves none ("a villager that dies to monsters"). The record goes into
  `VillagerGhosts`, a `SavedData` of that level (`SavedDataType`, a `RecordCodecBuilder` codec,
  `level.getDataStorage().computeIfAbsent`): a record id (UUID), the death position, the
  villager's `VillagerData` (type, profession, level), the game times its Mirror and Ghostrail
  cooldowns end. At most **32** records per level; adding a 33rd drops the oldest (the pure
  `GhostRecords` list rule).
- **Appear:** `VillagerGhosts.step(ServerPlayer)` (public, for GameTests) runs every 20 ticks of
  the player's `tickCount` from `PlayerTickEvent.Post`, for each player with the Evoking effect, alive, not a spectator,
  **in need** (below half health, or ≥ 3 hostiles that are `huntable` and `isPrey` for them
  within 8 blocks), and with **no ghost already helping them**: the nearest record in their level
  whose death position is within 48 blocks of them, whose chunk is loaded, and that has no live
  ghost, spawns a `VillagerGhost` at its death position. **Amends the parent §9.5:** at most one
  ghost per player, so a village massacre does not send a crowd.
- **The ghost** (`VillagerGhost extends LivingEntity`, `spellcraft:villager_ghost`, `MISC`, sized
  like a villager): synced `VillagerData` (for the look), transient record id and helped player.
  Attributes `MAX_HEALTH` 20 and `MOVEMENT_SPEED` 0.5 are registered as for the image. It is
  **not saved** (`shouldBeSaved` false): it vanishes with its chunk or a restart, and the
  record stays. It carries an infinite Ghostrail (applied on spawn), is not attackable, not
  pickable and invulnerable, and flies by setting its own velocity toward the spot 2 blocks from
  the player at the player's eye height (no gravity, no physics). `/summon` with no record:
  discards itself on its first tick.
- **Help:** once beside the player (within 3 blocks), it casts with a 20-tick wind-up (the
  casting effect for the hand particles, the two evoker sounds):
  - **Mirror** on the player if the player has no Mirror effect and the record's Mirror cooldown
    (7200) is over;
  - **Ghostrail** on the player (4800 ticks) if the player is below a quarter of max health or
    `isInWall()`, has no Ghostrail and the record's Ghostrail cooldown (4800) is over. Ghostrail
    first when both apply. Cooldowns are saved on the record (`setDirty`).
- **Disappear:** when no hostile (`huntable`, `isPrey`) is within 16 blocks of the player for 200
  ticks in a row, or the player is more than 64 blocks away, in another level, offline, dead or a
  spectator: `POOF` particles, `VEX_DEATH` at volume 0.3, `discard`. The record stays, so the ghost
  can come back.
- **Looks** (`VillagerGhostRenderer`, `client.evoking`): a `LivingEntityRenderer<VillagerGhost,
  VillagerRenderState, VillagerModel>` (vanilla's `VillagerRenderer` needs a `Villager`) with the
  base villager texture, drawn with `RenderTypes.entityTranslucent` and a model tint of alpha 0.6
  and a pale blue (`0x99DDE6FF`); and a copy of vanilla's profession layer (its helpers are
  private, and it draws with `entityCutout`, which ignores alpha) that draws the type, profession
  and level textures translucent with the same tint. **Amends the parent §9.5:** the render type
  is `RenderTypes.entityTranslucent` (26.3's name).

## 10. Part 7: errors

- **Mirror on a creative player:** allowed (not redundant). Mirror while active: refused (REFUSE),
  sneak-cast dispels (images poof).
- **Ghostrail on a creative or spectator player:** refused (redundant in creative).
- **Ghostrail ends deep in stone with no free space within 8 and no free start spot:** the carrier
  stays put and takes suffocation as vanilla would. Accepted, noted for the playtest.
- **An image whose caster is unloaded:** it wanders around where it is and poofs when its own life
  runs out; an image that loads in with no caster in its level keeps its life counter.
- **Images in a non-ticking chunk:** they do not count down; they keep their remaining life.
- **The caster dies:** a player's or mob's effects are cleared on death, so the images poof.
- **A mob with a scroll is converted** (a pillager is not; an evoker cannot be): nothing special.
- **A ghost whose record was dropped** (a 33rd death): the ghost discards itself on its next tick.
- **Two Casters ready at once:** each checks "no effect" at the end of its wind-up, so only the
  first one's cast lands; the second spends no cooldown.

## 11. Part 7: tests, docs, version

**Pure core:** `GhostrailDuration.of(kind)` (4800 / 40 / infinite); `GrantedAbilities.decide`
(the abilities table: every combination of game mode, Infinity and Ghostrail; properties: a
source ending never removes what another source still grants; creative and spectator abilities
are never taken away); `GhostRecords.add(list, record, max)` (properties: never more than 32;
the newest is always kept; the dropped one is the oldest); `GhostNeed.inNeed(health, maxHealth,
hostiles)`; `MirrorRule.redirects(roll)` (13 of 14 rolls). `./gradlew pitest` with rows.

**GameTests** (new classes `CasterGameTests`, `MirrorGameTests`, `GhostrailGameTests`,
`ScrollMobGameTests`, `VillagerGhostGameTests`):

- Caster: a scripted roll makes a Caster with the synced flag, saved across a save and load; over
  300 summons with real dice the share is within a statistical bound of 1/3 (the parent's
  shape); a Caster with a target never hurts it and keeps 4–6 blocks; two hostiles in its area
  with the summoner beside → after the 20-tick wind-up the summoner has Mirror and 13 images, the
  Caster's hand is restored, and its cooldown blocks a second cast; a summoner at a quarter health
  gets Ghostrail; a hurt sibling vex gets Ghostrail; a flee during the wind-up spends no cooldown.
- Mirror: the scroll cast spawns 13 images; images die at 0 health with no drops; images poof at
  the effect's end, on milk, on `/effect clear`, and on their own life when the caster is gone;
  the last image dying removes the effect; a zombie picking the player is redirected to an image
  when the dice say so and not when they do not; images are allies of their player (fangs spare
  them).
Mock players are never ticked by the server and their effects never count down, so these tests
step the public seams by hand (`Ghostrail.tick`, `VillagerGhosts.step`, `TeamMoves.tick`),
reach the Mixin through `ServerPlayer.doTick()` (public), end effects with `removeEffect`, and
test void damage with `hurtServer` and `damageSources().fellOutOfWorld()`. Rolls go through the
scripted global dice (§5).

- Ghostrail: a player with it gets `noPhysics` in `Player.tick` (the Mixin, via `doTick`),
  `mayfly` and `flying`; a player on stone given Ghostrail and stepped 20 ticks keeps its `y`;
  damage is cancelled but `/kill` and void damage land; a Ghostrail player in lava, under water
  with low air, or below the End island gets no rescue; ending inside stone moves the player to a free
  spot with `fallDistance` 0 and no `mayfly`; ending with Infinity active keeps Infinity's flight;
  Infinity ending with Ghostrail active keeps `mayfly`; a creative player's cast is refused; a
  zombie given 40 ticks has `noPhysics` and loses it at the end; trail particles are sent.
- Scroll mobs: with scripted dice an evoker spawned through `finalizeSpawn` holds a scroll in its
  offhand with drop chance 0.5; an evoker with Mirror casts on taking a target (13 images) and not
  again within the cooldown; a pillager with Ghostrail gets 40 ticks of it on taking a target.
- Villager ghosts: a villager killed by a zombie writes a record (saved and reloaded); one killed by
  the player, by a Bound Vex or by lava writes none; 33 deaths keep 32 records; a player with
  Evoking at 40 % health near a record gets a ghost that flies to them, casts Mirror, and fades
  (`discard`) 200 ticks after the last hostile is gone; a player not in need gets none; a second
  record near the same player does not send a second ghost; the ghost cannot be hurt.

`runData` proves the new renderers, layer, model and particle provider load.

**Playtest (Will; "Evoking II (Part 7)"):** the robe's look; the Caster's arms-up and particles;
images look like you (skin) and like an evoker; images wandering and being hit; the 13/14
confusion in a real fight; flying through walls with Ghostrail and ending inside a hill; the smoke
trail; an evoker with a scroll casting; the ghost's look, flight and fade.

**Docs:** README (Casters, the two spells, scroll mobs, ghosts), mod description (six spells),
`docs/api-notes.md` §26 (every vanilla fact used), playtest checklist, mutation testing, the
parent's inline notes, `CLAUDE.md` (the Mixin list gains the Ghostrail Mixin), scribing docs if
any list the pools.

**Version (a release step, not a plan task):** after the Part 7 plan is done, `mod_version`
becomes `1.3.0` on the Part 7 branch before its PR, and the merge commit is tagged `v1.3.0`
(parent §12: "1.3.0 with a tag at the end of Part 7").

## 12. What each part touches (orientation for the plans)

Part 6: `spell.rules.TeamMove`, `TeamMoveRules`; `evoking.TeamMoves`, `TeamMoveGoal`, `BoundVex`
(field, goal, refusals), `VexShots` (crossbow volleys and rockets), `EvokingRequest`,
`EvokingNetwork`, `EvokingCaster` (dispatch), `config.ServerConfig`; `client.evoking.EvokingKeys`;
lang; GameTests; docs.

Part 7: `spell.rules.SpellDeclarations`, `GhostrailDuration`, `GrantedAbilities` (from
`InfinityAbilities`), `GhostRecords`, `GhostNeed`, `MirrorRule`; `spell.Spells`, `ModItems`,
`SpellScrollItem`, `Allies` (`isPlayersSide`), `GolemMercy`; `effect.ModEffects`, `MirrorEffect`,
`GhostrailEffect`; `mixin.PlayerGhostrailMixin`; `infinity.InfinityAbilitySync` → shared sync;
`evoking.BoundVex` (Caster), `CasterGoal`, `CasterSpellGoal`, `VexSummons`, `MirrorImage`,
`MirrorImages` (events), `ScrollMobs`, `GhostrailChaseGoal`, `Ghostrail` (the per-tick driver),
`VexRescues` (skip Ghostrail), `Jobs` (Part 6: `busy`), `VillagerGhost`, `VillagerGhosts` (SavedData + spawner),
`ModEntities`; `particle.ModParticles`; `client.evoking.BoundVexModel`, `BoundVexRobeLayer`,
`BoundVexRenderer`, `BoundVexRenderState`, `CastingState`, `CastingParticles`,
`MirrorImageRenderer`, `VillagerGhostRenderer`, the ghost profession layer; `scripts/spell_icons.py`,
`scripts/robe_texture.py`; assets; GameTests; docs; `gradle.properties`.

## 13. Not built

A spawn egg; Wololo; any clientbound packet; config beyond `teamMoves`; images that fight; a ghost
that fights; Ghostrail for blocks other than collision (it does not open containers through
walls).

## 14. Decisions Will did not state

| Decision | Choice | Why, and the alternative |
|---|---|---|
| Team-move flag | Server-only field, not synced | Nothing on the client reads it. |
| Who is free | Following, not a Caster (Raid aside), not in a job, not the rescuer, within 32 blocks | A move should not cut a cure short or pull a staying guard off its post. |
| Trigger scan | Every 10 ticks per summoner, from a stored game-time deadline (kept apart from the record) | The goals' clock; one place for all four triggers. |
| A ≥ 99 enemy without 12 free vexes | Falls through to a Shield | Otherwise a Ravager gets no move at all with 11 vexes out. |
| Multishot detection | `getTarget()` in the scan, not the target event | Brain mobs skip the event. |
| Evader ward | The summoner or an ally within 16 blocks; never a vex or image | A vex at half health is not who the move is for. |
| Manual moves without Evoking | Allowed | The vexes outlive the effect. |
| J in automatic mode | Still cycles; the server answers K | The client cannot see the server's setting. |
| Extra refusal messages | "already in a team move", "not ready yet" | One "unavailable" for four reasons hides the cooldown. |
| Multishot out-of-band movement | Each member flies to its own arc slot, 25 degrees apart | Three members flying to one point end up in a row and block each other. |
| Multishot slot rule | A member more than 1 block (sideways) from its slot flies to it, in or out of the 6 to 8 band; roster members are not friends in the way | Members that entered the band in a line blocked each other for the rest of the move (found in the branch review). |
| Raid rocket aim | Spawned straight at the target's lower body, not through `performShooting` | The crossbow's target override lifts the aim for an arrow's drop; a rocket has none and overflew short enemies. |
| Raid rocket blast | Hurts only what the vex may hunt (`huntable` and `isPrey`) | A 5-block blast at a farm would otherwise kill animals and neutral mobs; the Part 3 splash rule alone spares only what the vex may not fight. |
| Request throttle | At least 10 ticks between a summoner's handled requests; extra ones ignored silently | A flood of packets must not cost a 16-block scan each. |
| Raid rocketeers and fellow members | Roster members are ignored in the line-of-fire test | Twelve vexes in a block would otherwise stop every shot; outsiders still hold fire. |
| Shield bash | 4 damage, knockback 1.5, each member on its own 40-tick clock | The parent's numbers, made per vex. |
| Multishot loading | Three arrows by hand, the enchantment on the stack for the spread | The crossbow fires only what is loaded. |
| Evader shove | Knockback 2, no damage, once per 20 ticks per hostile | A shove, not a fight. |
| Deflected projectile owner | The vex | Never deflected twice; the backstop covers it. |
| Raid kit | Even odds of seven main-hand items; one armor material per vex; all unbreakable | The parent's list, made rollable. |
| Rockets | One explosion each; the backstop spares your side | A rocket with no explosion does nothing. |
| Casters and gear | Armor, shields and healing potions kept; no melee or ranged | "Never melees or shoots"; healing is not shooting. |
| Caster wind-up | 20 ticks | Enough to see the arms go up. |
| Mirror effect and images | The effect ends when the last image dies | "No images" and "no effect" then mean one thing. |
| Who images confuse | Any mob picking the caster | A golem going for an evoker should be fooled too. |
| Player image skin | A mannequin stand-in with the caster's profile | The only client path that resolves an offline player's skin. |
| Ghostrail vs. `/kill` and the void | Both still land | A ghost under the world must not fall forever. |
| Mob Ghostrail steering | A chase goal on scroll mobs, straight at the target | Pathfinding walks around the wall Ghostrail is for, and a wanted position set outside the goals is overwritten. |
| Ghostrail and rescues | No rescue for a carrier | It takes no damage and is flying. |
| Ghostrail fallback spot | Where the effect started, if still free | Positions during the effect are inside walls. |
| Move members and rescues | A member may be the rescuer; its kit is put back when it returns | Your life first; the move resumes. |
| Mob scroll cast | Instant, on gaining a target | A 20-tick wind-up on a fighting illager is easy to miss. |
| Mob Ghostrail cooldown | 30 s | The parent's number. |
| Scroll drop | Vanilla offhand drop chance 0.5 (player kills, Looting) | "The loot path for players". |
| Which villagers leave ghosts | Killed by a monster (`Enemy`) not on a player's side | The summary's "dies to monsters". |
| Ghosts per player | One at a time | A massacre would otherwise send a crowd. |
| Ghost saving | Not saved; the record is | A ghost is a visit, not a resident. |
| Version | `1.3.0` and tag `v1.3.0` with Part 7 | The parent's §12. |
