# Spellcraft: Strength of Evoking (functional spec)

**Status:** implemented; describes the mod as of 2026-11-12 (Evoking I and Evoking II Parts 1–7). Playtest pending.
**Audience:** players, and anyone who needs to know what the spell does without reading how it is built.
**Short version:** [`evoking-kids-guide.md`](evoking-kids-guide.md), the main features in plain words, for a young
reader.
**Sources:** the five design specs in this folder. This document merges them, with every later amendment
already applied. Where this document and a design spec disagree about *what the player sees*, this one is
meant to be right: fix it here. The design specs stay the record of *how* and *why*.

| Design spec | Covers |
|---|---|
| [`2026-11-06-evoking-i-design.md`](2026-11-06-evoking-i-design.md) | Evoking I: the scroll, the keys, casting, the first fangs and vexes |
| [`2026-11-07-evoking-ii-design.md`](2026-11-07-evoking-ii-design.md) | Evoking II, all seven parts (the parent) |
| [`2026-11-09-evoking-ii-part-3-gear-and-stay-design.md`](2026-11-09-evoking-ii-part-3-gear-and-stay-design.md) | Part 3: gear, the stay order |
| [`2026-11-09-evoking-ii-parts-4-5-design.md`](2026-11-09-evoking-ii-parts-4-5-design.md) | Parts 4–5: cure, bribes, rescues, the Nether and the End |
| [`2026-11-10-evoking-ii-parts-6-7-design.md`](2026-11-10-evoking-ii-parts-6-7-design.md) | Parts 6–7: team moves, Casters, Mirror, Ghostrail, scroll mobs, villager ghosts |

## Summary

**Strength of Evoking** gives you an evoker's powers for as long as you want them. Press **Y** and evoker
fangs rise under every enemy in the square ahead of you. Press **V** and three **Bound Vexes** join you:
loyal, permanent helpers that follow you, guard you, fight what you fight, wear the armor and use the
weapons you hand them, cure zombie villagers, bribe an evoker's vexes, pull you out of lava and catch you
over the void. With enough of them out they fight as a team (**Shield**, **Multishot**, **Evader**,
**Raid**). One vex in three is a **Caster** that casts two more spells on you: **Mirror** (thirteen copies
of you that draw attacks away) and **Ghostrail** (fly through walls, unhurt). Both are scrolls you can read
yourself; illagers sometimes carry them; and a villager killed by monsters leaves a **ghost** that comes
back to help you when you are in trouble.

## How the paragraph numbers are used

Every paragraph below has a number (`3.4`). The code and the tests that implement a paragraph name it in a
comment, written exactly as `Functional spec §3.4`. To find what implements a rule, search the source for
that text. To find the rule a piece of code serves, read the number in its comment. A unit test
(`FunctionalSpecReferencesTest`) fails when a comment names a paragraph that does not exist here, or when a
paragraph here is named by no code. When you add, remove or renumber a paragraph, update the comments in
the same change; prefer adding a new number at the end of a section to renumbering.

## Glossary

| Term | Meaning |
|---|---|
| **Evoking** | The spell's effect on you ("Strength of Evoking"). While you have it, Y and V work. |
| **Cast** | One accepted key press: your arms go up, and one second later the spell happens. |
| **Target** | Something fangs may bite: see 3.2. |
| **Ally** | Someone your spells and vexes never hurt: see 4.1. |
| **Hostile** | A monster: zombies, skeletons, slimes, ghasts, phantoms, shulkers, the dragon, the wither, and "neutral" monsters such as endermen and zombified piglins. |
| **Bound Vex** | The mod's own vex, summoned with V. "Your vexes" always means these. |
| **Summoner** | The player who summoned a Bound Vex. "You" in this document. |
| **Following / staying** | A Bound Vex either follows you or stays at a post you gave it (6.4). |
| **Post** | The block a staying vex was in when you told it to stay. |
| **Guard area** | Where a vex looks for trouble. Following: a box 8 × 8 blocks wide and 7 tall around the vex. Staying: a box 13 × 13 wide and 7 tall around its post. |
| **Given gear** | Items you hand a vex, plus the iron sword it arrives with. Real: you get them back. |
| **Conjured gear** | Items a vex makes for a job or a team move. They vanish afterward, cannot be taken and never drop. |
| **Job** | One vex doing one task on one subject: repairing a golem, curing a zombie villager, bribing a vex, breaking an End crystal. |
| **Rescue** | A vex saving you from lava, drowning or the void. |
| **Team move** | Shield, Multishot, Evader or Raid: several vexes acting together with conjured gear. |
| **Free vex** | A vex that may join a team move: see 11.2. |
| **Ward** | The one an Evader protects. |
| **Caster** | A Bound Vex that casts spells instead of fighting (one in three). |
| **Carrier** | Whoever has a Mirror or Ghostrail effect on them. |
| **Mirror image** | One of the thirteen copies a Mirror makes. |
| **Villager ghost** | The spirit of a villager that a monster killed; it helps a player in need. |
| **In need** | Below half health, or with three or more hostiles within 8 blocks. |
| **Standable spot** | A solid floor with two blocks of air above it. |

Times are given in seconds; the game runs 20 ticks to a second.

## 1. The spell and its scroll

**1.1 The effect.** Strength of Evoking is a beneficial effect with no time limit; the HUD shows "∞". By
itself it does nothing: it is what makes the Y and V keys work.

**1.2 The scroll.** The Scroll of Evoking stacks to 16, is in the creative tab, and can be scribed (it is
in the lower scribing pool). Its icon is a light-mint "Y" with a head in its fork: an evoker with raised
arms.

**1.3 Turning it on and off.** Reading the scroll turns the spell on, in any game mode. Reading another
while it is on is refused ("Scroll not needed; already have effect!") and the scroll is kept. Sneaking and
reading a scroll turns it off. Milk clears it.

**1.4 Death.** The spell is gone when you respawn. Nothing is cast for a dead player.

## 2. Keys and casting

**2.1 The keys.** Four keys, listed under "Spellcraft" in Controls and rebindable there: **Y** "Evoking:
Fangs", **V** "Evoking: Summon Vexes", **J** "Evoking: Next Team Move", **K** "Evoking: Team Move!". Y and V
do nothing without the Evoking effect. If you bind a casting key to the same key as vanilla's "swap item
with offhand", then while you have Evoking that key casts and does not swap.

**2.2 A cast.** When a press is accepted you hear the evoker's "prepare" sound and your arms go up. One
second later the spell happens, with the evoker's "cast" sound. Your arms stay up for 2 seconds for fangs
and 5 seconds for vexes. You can move freely the whole time.

**2.3 Busy and cooldowns.** No cast is accepted while your arms are up. Fangs can be cast again 5 seconds
after the press, vexes 17 seconds after the press. A press that comes too early does nothing and says
nothing.

**2.4 A cast that is cancelled.** If you die in the second between the press and the spell, nothing
happens and the cooldown still counts. If you lose Evoking in that second (milk, a dispel), nothing
happens and your cooldowns are forgotten, as they are whenever the spell is turned off. If you change
dimension in that second, fangs are not placed, and vexes are summoned where you now stand. Logging out
forgets cooldowns and any cast in progress.

**2.5 What others see.** While you cast, every player who can see you sees your arms raised like an
evoker's and swaying, with spell particles at your hands (dark for fangs, pale for vexes). This shows in
third person and in the inventory, not in first person. The cast shows no icon on the HUD and no entry in
the inventory's effect list. No hand particles appear while the game is paused or frozen with `/tick
freeze`.

**2.6 The server decides.** A key press is only a request. The server ignores one from a player who is
dead, is a spectator, or does not have Evoking, and checks everything else itself.

## 3. Fangs (Y)

**3.1 The square.** Fangs look for targets in a 13 × 13 block square in front of you. You stand on its
edge: in the middle of an edge when you face straight along an axis, at a corner when you face diagonally.
The square slides block by block as you turn. It reaches from 14 blocks below your feet to 6 blocks above.

**3.2 Targets.** A target is any living thing in the square that can be attacked, except: you; your allies
(4.1); armor stands; creative and spectator players; and players you are not allowed to hurt (4.7).

**3.3 No targets.** With no target in the square, or none that has a floor under it (3.4), nothing is
cast: no arms, no sound, no cooldown. One second later the message "No targets in range" appears above the
hotbar. Pressing again before it appears still gives only one message, one second after the latest press.
A dead player gets no message.

**3.4 Where fangs rise.** One fang rises under each target, where it stood when you pressed the key. A
wide target (a ravager) gets a fang in every block column its body covers. Each fang stands on the solid
floor at the target's feet (the search runs from one block above the feet to two below); a target over no
floor gets none. No fang ever rises in your own block. Two targets on the same spot share one fang. All
the fangs snap together, one second after the press. No fang is placed in a part of the world that is too
far from players to be simulated.

**3.5 The bite.** A fang does 6 magic damage, credited to you. It bites whatever stands on it when it
snaps, not only the target it rose for, but never you, your allies or your summons. The spots are fixed at
the press: a target that walks away during the second is missed by its own fang (see 3.6).

**3.6 The chase fang.** When a fang snaps and its target has stepped off it, one more fang rises under the
target where it stands now, provided the target is alive, is within 16 blocks of you and is not an ally.
Each target is chased once per cast; a chase fang never chases again.

## 4. Allies, iron golems and other players

**4.1 Who is an ally.** Your allies are: you; players and mobs on your scoreboard team; animals you tamed;
allays that like you; mobs you summoned (your Bound Vexes, your Mirror images); any other mob you own;
villagers; wandering traders; iron golems that are not mad at you (4.3); and, for every player, a zombie
villager that a vex is curing (9.4).

**4.2 What being an ally means.** Your fangs do not target or bite allies, your vexes never fight them,
and the flame of Strength of Trailing does not burn them (while you, the flame's owner, are online). The
Trailing flame never lights any iron golem, mad or not.

**4.3 A mad golem.** An iron golem that is targeting you or angry at you is not your ally while that
lasts: your fangs, vexes and wolves may fight it.

**4.4 Mercy.** A hit from a player's side can never take an iron golem below 25 health. A player's side is
a player (sword hits included), a player's fangs, a tamed wolf, or any mob a player summoned. Other damage
(lava, falls, monsters, `/kill`) is unchanged.

**4.5 Forgiveness.** When such a hit leaves a golem at 25 health or less, the golem calms down at once and
is an ally again. It gets mad again only the usual way (you hit it or a villager).

**4.6 Golems and your side.** Iron golems never pick a mob a player summoned (a Bound Vex, a player's
Mirror image) as a target, nor a zombie villager that is being cured. Villagers do not run from Bound
Vexes.

**4.7 Other players.** Fangs and vexes never go for a player you are not allowed to hurt (the `pvp` game
rule off). A mob another player summoned is treated like that player: with PvP off your vexes leave it
alone, and with PvP on they fight it only after it has hurt your side (7.2), never on sight.

## 5. Summoning Bound Vexes (V)

**5.1 The summon.** V summons three Bound Vexes around you, two to three blocks away and one block up.
Each arrives holding an iron sword. If you are on a scoreboard team they join it.

**5.2 Permanent.** A Bound Vex has no timer and never despawns. It gives no experience and has no loot of
its own (it drops only gear, 8.12). There is no spawn egg and it never spawns naturally. One made with
`/summon` has no summoner: it hovers and does nothing.

**5.3 The cap of twelve.** You can have at most 12 Bound Vexes. With 12 out, V only says "You have too
many vexes": no cast, no sound, no cooldown. With 10 or 11 out, V summons just enough to make 12. Only
vexes in loaded parts of the world are counted.

**5.4 Peaceful.** On Peaceful, V is refused with "Vexes cannot be summoned on Peaceful" and no cooldown is
spent. Switching a world to Peaceful removes Bound Vexes as it removes monsters; the gear you gave them
drops.

**5.5 When you are away.** While you are offline, dead, a spectator or in another dimension, your vexes
stay where they are, hover, fight nothing and answer no clicks. They carry on when you are back in their
dimension. Nothing is removed when you log out or die.

**5.6 Only your clicks.** A Bound Vex answers only its summoner's right-clicks. Anyone else's click does
nothing.

## 6. Following, staying and travel

**6.1 Where a following vex hovers.** How close your vexes keep depends on how dangerous the place is.

| Where | Sideways from you | Height above your feet |
|---|---|---|
| Overworld by day; any modded dimension | 2 to 3.5 blocks | 0.5 to 1.5 blocks |
| Overworld at night or in a thunderstorm | 1 to 2 blocks | 2.5 to 3 blocks |
| Nether | 1 to 2 blocks | 2.5 to 3 blocks |
| End | 0.5 to 1.5 blocks | 2.5 to 3 blocks |

In the three close rows the vexes hover above your head, not at eye height, so that your clicks do not
land on them.

**6.2 How alert it is.** A vex looks for hostiles twice a second by day, five times a second at night and
in the Nether, and ten times a second in the End. A staying vex is as alert as the place calls for.

**6.3 Catching up.** A following vex that is more than 12 blocks from you teleports to your side: at most
2 blocks away in each direction, level with you or up to 2 blocks higher. Vexes pass through blocks, so
walls do not stop them.

**6.4 Stay.** Sneak and right-click your vex with an empty hand to tell it to stay; do it again and it
follows. You hear the vex's sound. A staying vex hovers at its post, fights in its 13 × 13 guard area
around the post, and flies back to the post when the fight is over. It never chases anything out of that
area: a target whose feet leave it is dropped.

**6.5 Portals.** When you go through a portal, your following vexes within 16 blocks come with you and
arrive around you with their gear. Staying vexes stay. This includes leaving the End through the exit
portal. Vexes are not brought to you when you respawn after dying.

## 7. What a Bound Vex fights

**7.1 On patrol.** A vex attacks a hostile whose feet are inside its guard area and that it can see. On
patrol it never picks a creeper, a player, or a mob another player summoned.

**7.2 Grudges.** A vex also goes for whatever, in the last 5 seconds: hurt that vex; hurt you; was hit by
you; or hurt one of your allies inside the guard area. A creeper can be attacked this way, and so can
another player or their summons when PvP allows (4.7).

**7.3 Joining an ally's fight.** A vex takes the target of an allied mob (an iron golem, a wolf) that is
inside its guard area, if that target is a hostile it could pick on patrol.

**7.4 Never.** A vex never fights you, an ally, an armor stand, a zombie villager (9.2), or an evoker's
vex that is being offered a bribe or has taken one (9.6, 9.7).

**7.5 Giving up a target.** A following vex takes targets within 7.25 blocks and drops one that gets
farther than that (12 blocks while it holds ranged gear, 8.6). A staying vex takes and keeps a target only
while its feet are in the stay area. Any vex drops a target that dies, becomes an ally, or is in another
dimension.

**7.6 Wolves join in.** When a vex takes a new target, each of your tamed wolves within 16 blocks that is
not sitting, has no target and would attack that kind of mob takes the same target.

**7.7 Hissing creepers.** When a creeper within 8 blocks starts to hiss, the vex flies straight away from
it to at least 8 blocks and waits until the creeper stops or explodes, then goes back to its fight.

**7.8 Dodging a golem.** When an iron golem within 3 blocks swings while facing the vex, the vex darts 3
blocks sideways. In practice that is a golem mad at you: a calm golem has no reason to swing at your side.

**7.9 Never hurting your side.** Whatever a vex does (a sword hit, an arrow, a potion, a rocket, a blast
it set off), the damage is cancelled when it would land on you or on anything that vex is not allowed to
fight. While you are away, an arrow or potion already in flight still cannot hurt anything that is not a
hostile, or another of your vexes.

## 8. Gear

**8.1 The click table.** Right-clicking your vex with your main hand:

| Main hand | Sneaking | What happens |
|---|---|---|
| empty | yes | Toggle stay (6.4) |
| empty | no | Take its weapon back (8.4) |
| a gear item (8.2) | either | Give it (8.3) |
| anything else | either | Nothing |

**8.2 What is gear.** A helmet, chestplate or leggings (worn in that slot); a shield (off hand); and, for
the main hand: a bow or crossbow, a splash or lingering potion that has an effect, an arrow of any kind,
or anything with attack damage (swords, axes, maces, tridents, tools). Not gear: boots and body armor,
water and other effect-less potions, and tipped arrows whose effects are all helpful.

**8.3 Giving.** The vex takes one item from your hand (a creative player's stack is not reduced) and puts
it in its slot. What the vex had in that slot comes back to your inventory, or drops at your feet if the
inventory is full. Armor gives the vex its protection and enchantments.

**8.4 Taking the weapon back.** A plain empty-hand click puts the vex's main-hand item in your hand, and
the vex fights bare-handed. Armor and the shield cannot be taken back by a click: they come back when you
replace them or when the vex dies.

**8.5 Melee.** A vex holding a melee weapon, or nothing, charges its target. The weapon's damage and
enchantments count, so a diamond sword hits harder than the iron one. A trident is used in melee, not
thrown.

**8.6 Ranged.** A vex holding a bow, a crossbow, an arrow, or a potion that is not a healing potion (8.8:
any effect that is not helpful) keeps 6 to 10 blocks from its target and, every 2 seconds when it can see the target, shoots an arrow or throws a
copy of the potion. A held tipped or spectral arrow is shot as that arrow. Its arrows cannot be picked up.

**8.7 Holding fire.** A ranged vex does not shoot while a friend (you, an ally, another of your vexes) is
within 1 block of the arrow's path, and does not throw a potion while a friend is within 4 blocks of the
target.

**8.8 Healing potions.** A vex holding a splash or lingering potion whose effects are all helpful does not
fight. Every 2 seconds it throws a copy at the nearest friend within 10 blocks that it can see and that is
below half health: you, an ally, or another of your vexes. It skips the undead, which healing would hurt.
It keeps following you while it does this.

**8.9 Shield.** A vex with a shield blocks melee hits, projectiles and explosions that come from in front
of it, with the shield sound. Arrows with Piercing get through. The vex still turns on whoever hit the
shield.

**8.10 Nothing is used up.** Weapons, armor and shields a vex holds never lose durability, and the potion
or arrow in its hand is never spent: you get back exactly what you gave.

**8.11 Seeing the gear.** The armor shows on the vex, sized to it. The main-hand item and the shield show
in its hands.

**8.12 Drops.** When a Bound Vex dies, everything you gave it drops, and so does its own iron sword.
Conjured items never drop. The usual rules still apply: nothing drops with the `mob_drops` game rule off,
and an item with Curse of Vanishing is destroyed.

**8.13 Conjured items and your gear.** When a vex conjures an item for a job or a team move, the item you
gave it for that hand or slot is set aside and comes back when the conjured one vanishes. A gift made
meanwhile also waits and appears afterward. A conjured item cannot be taken back. Gear, what is set aside,
and the stay order all survive saving and portals.

## 9. Jobs: repair, cure, bribe

**9.1 Repairing a golem.** A vex with no target repairs a hurt allied iron golem in its guard area, one vex
per golem. It flies to the golem's chest, conjures an iron ingot, and every 1.5 seconds heals the golem by
25 health with the repair sound, until the golem is whole. If a target appears it stops and fights.

**9.2 Zombie villagers are cured, not fought.** A Bound Vex never fights a zombie villager, not even one
that hit it or you.

**9.3 Starting a cure.** A vex cures a zombie villager in its guard area that is not already being cured
or converting, one vex per zombie villager. One that already stands frozen (a statue a map maker placed)
is left alone. A vex with no target cures any such zombie villager; a vex
that is busy fighting still stops to cure one that is attacking you or an ally. The zombie villager is
frozen the moment the vex picks it: it neither moves nor attacks.

**9.4 The cure.** The vex flies level with the zombie villager's head, holds out a golden apple for 1.5
seconds (eating sounds, crumbs at its mouth), turns its head aside and throws a gray splash potion at its
feet. The potion has no effect on anyone. Then the vex is free again, and the zombie villager stands
frozen in green and white smoke for 20 seconds and becomes a villager. It costs you nothing. The villager
gives you the cured-villager discounts, if you are in that dimension when the cure ends. While it is being
cured the zombie villager is an ally of every player (4.1), though nobody is attacked for hurting it.

**9.5 A cure that is cut short.** Fleeing a creeper, dodging a golem or rescuing you interrupts a cure;
so does the zombie villager dying, you leaving, or the vex failing to reach it within 5 seconds. The
zombie villager is then unfrozen and can be picked again later (a vex that could not reach it waits 10
seconds before trying anything). A new enemy appearing does not interrupt a cure. A zombie villager left
frozen because its vex vanished is released after 15 seconds. One that a player fed by hand meanwhile
keeps that player's cure.

**9.6 Offering a bribe.** When an evoker's vex (an ordinary vex) in the guard area is attacking you or an
ally, and has not been offered a bribe before, one of your vexes flies to it and holds out an emerald for
1 second, with a villager's "hmm". Meanwhile none of your vexes attacks it. Two times in three it takes
the bribe. Otherwise it refuses, is fought like any hostile, and is never offered another.

**9.7 A bribed vex.** A vex that took the bribe stops fighting and flies away from the nearest player,
through walls. It vanishes, with no sound, particles or drop, once it is at least 12 blocks from every
player nearby and none of them can see it (at once, if no player is within 64 blocks), or after 10 seconds
in any case. None of your vexes attacks it while it leaves.

**9.8 Jobs and staying vexes.** A staying vex does jobs only on subjects inside its stay area, and returns
to its post afterward.

## 10. Rescues and the other worlds

**10.1 The rescuer.** A rescue is done by your nearest following vex within 16 blocks (64 for the void
catch). It appears at your side at once, whatever it was doing, and returns to its duties afterward.
Staying vexes do not rescue. One rescue runs at a time.

**10.2 Who is not rescued.** Spectators, never. Creative players, only from the void. A player with Fire
Resistance gets no lava pull; one with Water Breathing or Conduit Power gets no drowning push. A player
riding something gets no push, but is caught over the void. A player with Ghostrail (section 14) gets no
rescue at all.

**10.3 The lava pull (Overworld and Nether).** While you are in lava, the rescue pushes you up and toward
the nearest standable spot within 6 blocks that is out of the lava and about level with you (from one
block below your feet to two above); with no such spot it only holds you up. The push adds to your own movement, so you keep steering. It lasts until you are out, at most 3
seconds at a time with a 1 second pause between.

**10.4 The water bucket (Nether).** When a lava pull in the Nether ends with you out of the lava, the vex
shows a water bucket, you hear it empty and hiss, a splash appears on you and you stop burning. No water
is placed. In the Overworld you keep burning.

**10.5 The drowning push (Overworld).** When you are under water with less than 3 seconds of air and there
is open water above you up to the surface (within 32 blocks), the rescue pushes you straight up until your
head is out, for at most 10 seconds. Under a ceiling there is no push: you are left free to swim sideways.

**10.6 The fireball return (Nether).** When a ghast's fireball (or any large fireball that is not yours)
is about to hit you, or anything within 4
blocks of you, and one of your vexes (following or staying) is within 7.25 blocks of the fireball, that
vex knocks it back with the knockback sound: at the ghast that shot it when the vex can see the ghast,
otherwise straight back the way it came. The returned fireball counts as yours, so it kills the ghast like
one you punched back yourself.

**10.7 Your own fireballs.** A fireball that is yours (returned by a vex or by you) passes through your
vexes without exploding on them.

**10.8 End crystals.** While the dragon is alive, a vex with no target flies to an End crystal within 32
blocks (inside the stay area, for a staying vex) and breaks it, one vex per crystal. A crystal that cannot
be broken (an invulnerable one) is left alone. It gives up on a
crystal it cannot reach in 10 seconds.

**10.9 Crystal blasts.** An End crystal's explosion never hurts a Bound Vex. A crystal that one of your
vexes breaks neither hurts nor throws you, your allies or your vexes. A crystal you break yourself still
throws you.

**10.10 Critical hits in the End.** In the End every melee hit by a Bound Vex does three times its damage,
with critical-hit sparks and sound. Arrows and potions are not tripled. Mercy for golems (4.4) still
holds.

**10.11 The void catch (End).** When you fall into the void in the End (not while flying), a vex puts you
back on the nearest standable spot within 32 blocks, or else on the last ground you stood on in the End.
Your fall is cancelled, and the vex feeds you an enchanted golden apple: you get its effects and its food,
and no item is spent. A rider is dismounted and caught. There is no cooldown. If there is nowhere to put
you, there is no catch, and the search for a place is tried again one second later.

## 11. Team moves

**11.1 Automatic or manual.** The server setting `teamMoves` (section `evoking` of the server config) is
`AUTOMATIC` by default: your vexes start team moves by themselves (11.4). Set to `MANUAL`, they start one
only when you ask with the keys (11.12). One move runs at a time per player.

**11.2 Free vexes.** A vex can join a move when it is alive, following, within 32 blocks of you, in no
other move, not in the middle of a job, and not rescuing you. Casters join only a Raid.

**11.3 Rosters.**

| Move | Needs | Who takes part |
|---|---|---|
| Shield | 3 free vexes | 9 when at least 9 are free, otherwise 3: the nearest to the enemy |
| Multishot | 3 free vexes | the vex under attack and the 2 nearest to it |
| Evader | 1 free vex | up to 3: the nearest to the ward |
| Raid | 12 free vexes | all 12 |

**11.4 Automatic triggers.** Twice a second, while no move is running and you are present, the game looks
at the hostiles inside the guard area of any of your following vexes within 32 blocks of you (not creepers, not another player's
summons) and starts the first of these that can start:

1. **Raid:** an enemy with at least 99 max health (a Ravager, a Warden, the Wither, the Dragon).
2. **Shield:** an enemy with at least 31 max health (an Enderman, a Piglin Brute). An enemy big enough for
   a Raid that cannot have one (fewer than 12 free vexes, or Raid cooling down) gets a Shield.
3. **Multishot:** an enemy with at least 6 attack damage that is targeting one of your free vexes.
4. **Evader:** you, or an ally within 16 blocks of you, below half health. The most badly hurt is
   protected; you win ties. A vex or a Mirror image is never the ward.

Among several enemies that fit, the nearest to you is chosen.

**11.5 Starting.** Each member sets aside its own gear, conjures the move's kit and goes for the enemy.
You see one message above the hotbar: "Your vexes form a Shield!", "Your vexes ready a Multishot!", "Your
vexes form an Evader guard!" or "Your vexes Raid!".

**11.6 Ending.** A move ends when its enemy is dead, gone or more than 48 blocks from you; when the
Evader's ward is back to half health (or dead, gone or 48 blocks away); when no member is left; when you
are no longer present; or after 2 minutes (Evader: 1 minute). The kit vanishes, each vex's own gear
returns, and that kind of move cannot start again for 30 seconds (logging out forgets that wait). There is
no message at the end.

**11.7 Members.** A member that dies or leaves is dropped and the move goes on with the rest; a Raid
continues with fewer than 12. A kit never drops. A member that has to flee a creeper or rescue you pauses
its part and resumes. Clicking a member to take its weapon or toggle stay answers "That vex is busy" and
changes nothing; a gift waits until the move ends (8.13).

**11.8 Shield.** Kit: a shield in each hand, which block as in 8.9. Each member closes in at half speed;
from within 7 blocks it dashes in, bashes the enemy for 4 damage with a strong knockback and the shield
sound, backs off to about 6 blocks, and bashes again 2 seconds later.

**11.9 Multishot.** Kit: a crossbow with Multishot. Each member takes its own place on an arc about 7
blocks from the enemy, so the three are not in each other's way, and every 2 seconds, with a clear line
past your side (8.7), fires three arrows at once. The other members of the move do not count as being in
the way; you, your allies and your other vexes do.

**11.10 Evader.** Kit: a shield in each hand. The members circle the ward 2 blocks out and follow it. Any
hostile that comes within 3 blocks of the ward is shoved away (no damage; each hostile at most once a
second). Any projectile about to hit the ward that was not shot by your side is knocked back the way it
came. Ghast fireballs are left to the fireball return (10.6).

**11.11 Raid.** Kit, different for each vex: a netherite or diamond sword or axe, a mace, a trident, or a
crossbow that fires firework rockets; a shield; and a full set of netherite or diamond armor. For 2
seconds the twelve gather in a 3 × 4 block, 8 blocks from the enemy and facing it. Then they charge
together: melee vexes strike about once a second, and rocket vexes hold 8 blocks off and fire a rocket
every 2 seconds, holding fire as in 11.9. A rocket's blast hurts only what your vexes would hunt: animals and neutral mobs near the
enemy are spared, as is your side. A Caster in a Raid gets the armor and the shield but no weapon: it
holds a Mirror scroll, casts Mirror on you as soon as the Raid starts (at once, with no wind-up, and
whatever its 6 minutes say, if you have no Mirror), then holds its place and never attacks.

**11.12 Manual mode.** **J** steps through Shield, Multishot, Evader, Raid and shows "Team move: …". **K**
asks for the selected move; you do not need the Evoking effect. The game answers with the first of these
that applies:

1. In automatic mode: "Team moves are automatic".
2. A move is running: "Your vexes are already in a team move".
3. That move is cooling down: "That team move is not ready yet".
4. Too few free vexes, or no fitting enemy within 16 blocks of you: "Not enough vexes, or no enemy for
   that move".
5. Otherwise the move starts against the nearest enemy that fits (the thresholds of 11.4; a Multishot
   enemy need not be targeting a vex; an Evader needs only a ward).

Requests closer together than half a second are ignored.

## 12. Casters

**12.1 One in three.** Each Bound Vex you summon has a 1 in 3 chance of being a Caster. A Caster wears a
torn purple robe with magenta trim, and stays a Caster through saving and portals. A vex made with
`/summon` is never a Caster.

**12.2 It does not fight.** A Caster never hits or shoots. With a target it keeps 4 to 6 blocks away,
facing it. In everything else it is a Bound Vex: it follows and stays, does jobs and rescues, wears armor,
blocks with a shield, and heals with a healing potion (8.8).

**12.3 Ghostrail.** A Caster casts Ghostrail (section 14) on you, or on another of your vexes, within 16
blocks, when that one is below a quarter of its health and has no Ghostrail; on you also when you are
stuck inside a block. You come first. Never on a creative or spectator player. Each Caster can do this
once every 4 minutes.

**12.4 Mirror.** A Caster casts Mirror (section 13) on you when at least 2 hostiles are in its guard area,
you are within 16 blocks and you have no Mirror. Each Caster can do this once every 6 minutes. A Caster
looks for a Ghostrail to cast before a Mirror.

**12.5 The wind-up.** Outside a Raid (11.11), a cast takes 1 second: the Caster holds a conjured scroll, raises its arms, purple
particles swirl at its hands, and you hear the evoker's two casting sounds. If it is interrupted (it
flees, rescues you or joins a Raid), nothing is cast and its 4 or 6 minutes are not spent. The same holds
when, as the second ends, the one it was casting on is dead, gone, out of range or already has the spell
(another Caster got there first).

## 13. Mirror

**13.1 The scroll.** The Scroll of Mirror lasts 6 minutes. Reading another while it is on is refused,
sneaking and reading turns it off, milk clears it, and it works in creative. It is in the lower scribing
pool. Its icon is two facing figures in magenta.

**13.2 The images.** Mirror makes 13 images of the carrier around it, 2 to 3 blocks away, never inside a
block; in a tight space the ones that do not fit start on the carrier's own spot. An image of a player has that player's skin and holds copies of what the player held in each hand;
it wears no armor and shows no name. An image of a mob looks like that mob.

**13.3 How images behave.** An image has 10 health and does not attack. It wanders within 6 blocks of the
carrier at walking pace, and when it stands still it faces where the carrier faces. It can be hit, flashes
and is knocked back like any creature, and dies in a puff with no drops and no experience. Images do not
push you, each other or anything else.

**13.4 Confusion.** When a mob newly picks the carrier as its target, and the carrier has images within 16
blocks, 13 times in 14 the mob goes for one of the images instead. A mob already attacking one of the
carrier's images stays on it. This fools any mob, not only monsters, with two exceptions: an iron golem is
never sent at a player's images, and a few mobs that choose targets their own way (a piglin brute, a
warden) are not fooled.

**13.5 Images are on your side.** A player's images are that player's allies (4.1): fangs, vexes and the
Trailing flame spare them, and golems never target them. A mob's images are nobody's allies.

**13.6 How it ends.** The images puff away when the effect ends for any reason (time, a dispel, milk), and
at once when the carrier dies. The other way round too: when the last image is gone, the effect ends. Each
image also lasts at most 6 minutes on its own. Images do not follow the carrier to another dimension; a
player who leaves them behind, or logs back in after they are gone, loses the effect within a few seconds.

## 14. Ghostrail

**14.1 The scroll.** The Scroll of Ghostrail lasts 4 minutes. Reading another while it is on is refused,
sneaking and reading turns it off, and milk clears it. A creative player's read is refused (they already
fly unhurt). It is a top-tier scroll, in the upper scribing pool with Infinity. Its icon is a pale arrow
passing through a wall.

**14.2 Through walls.** A survival or adventure player with Ghostrail passes through blocks and flies, and
is kept flying the whole time (otherwise they would sink through the floor).

**14.3 No damage.** A carrier takes no damage, with two exceptions: `/kill`, and falling out of the world.

**14.4 The trail.** A carrier leaves a trail of white smoke.

**14.5 Blocks do nothing.** A carrier does not set off portals or pressure plates and is not slowed by
cobwebs, honey or powder snow.

**14.6 How it ends.** When Ghostrail ends inside a block, the carrier is moved to the nearest free spot
within 8 blocks (a standable one if there is one; never into liquid or fire). With none, it goes back to
where the spell began, if that is still free; otherwise it stays put and suffocates as usual. Flight ends,
fall distance is cleared, and a carrier in mid-air falls from there.

**14.7 With Infinity.** Flight from Ghostrail and from Strength of Infinity do not cancel each other: when
one ends you keep flying while the other lasts, and a creative or spectator player's flight is never taken
away.

**14.8 On mobs.** A hostile mob's Ghostrail lasts 2 seconds. An evoker, vindicator or pillager with
Ghostrail flies straight at its target, through walls. Any other mob given Ghostrail simply stops
colliding with blocks. A Bound Vex's Ghostrail lasts 4 minutes; a villager ghost's never ends.

## 15. Illagers with scrolls

**15.1 Who carries one.** One evoker in four, and one vindicator or pillager in ten, spawns with a scroll
in its off hand, where you can see it: Mirror or Ghostrail, at even odds.

**15.2 When it is cast.** Such a mob casts the moment it first gets a target, instantly, with the evoker's
cast sound and a burst of purple particles. Mirror makes 13 images of the mob (at most once every 6
minutes). Ghostrail gives it 2 seconds to come through a wall at you (at most once every 30 seconds). The
scroll is not used up.

**15.3 The drop.** Killed by a player, the mob drops its scroll one time in two (Looting improves the
odds).

## 16. Villager ghosts

**16.1 Who leaves a ghost.** A villager killed by a monster leaves a ghost behind at the place it died,
including one a zombie turns into a zombie villager. A villager killed by a player, by a player's side
(4.4), or by lava, a fall and the like leaves none. Each dimension remembers the last 32 such deaths, and
they are saved with the world.

**16.2 When a ghost comes.** Once a second, for each living player with the Evoking effect who is in need
(below half health, or with 3 or more hostiles within 8 blocks), has a hostile within 16 blocks, and has
no ghost helping already: the ghost of the nearest death within 48 blocks appears where it died (a death
whose ghost is already out helping someone else is skipped). A ghost
that has a spell ready is preferred to a nearer one that does not. One ghost helps a player at a time.

**16.3 The ghost.** It looks like the villager it was, profession and all, see-through and pale blue, and
trails white smoke. It flies through walls to a spot 2 blocks from you. Nothing can hurt it and nothing
targets it.

**16.4 Its help.** Beside you (within 3 blocks) it casts with a 1 second wind-up, purple particles at its
hands and the evoker's casting sounds. Ghostrail first: when you are below a quarter of your health or
stuck inside a block, have no Ghostrail and are not in creative; once every 4 minutes for that ghost.
Then Mirror: when you have no Mirror; once every 6 minutes for that ghost.

**16.5 Fading.** When no hostile has been within 16 blocks of you for 10 seconds, or you are more than 64
blocks away, dead, a spectator or gone, the ghost fades in a puff with a faint vex sound. It can come back
the next time you are in need there. A ghost is not saved with the world; the memory of the death is.

## 17. Not included

There is no spawn egg for any of the new mobs, no Wololo, no boots or elytra on vexes, no
first-person arm animation, no on-screen cooldown, and no setting other than `teamMoves`. Mirror images
and villager ghosts never fight.
