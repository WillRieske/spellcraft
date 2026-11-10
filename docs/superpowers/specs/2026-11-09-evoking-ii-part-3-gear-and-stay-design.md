# Spellcraft: Evoking II Part 3, gear and the new stay (design)

**Date:** 2026-11-09
**Status:** implemented 2026-11-09 via `2026-11-09-evoking-ii-part-3.md`
**Written by:** `/one-shot`, unattended, on 2026-11-09, from Will's playtest report; §9 lists the
decisions Will did not state.
**Target:** Minecraft Java 26.3, NeoForge 26.3.0.23-beta, Java 25, ModDevGradle
**Builds on:** [`2026-11-07-evoking-ii-design.md`](2026-11-07-evoking-ii-design.md) ("the parent
spec"): this is its Part 3 (§4, gear), built as designed there except where this document says
otherwise.
**Amends:** the parent spec's glossary rows **Guard area**, **Reach** and **Staying**, its §3.3
(the wander goal and the stay toggle), §3.4 and §3.7 (which box a staying vex uses), §4.1–§4.2
(sneaking while giving), §4.4 (which click takes the weapon back), §4.5 (when gear drops), §11
(Part 3 tests), §12 and §13 (the version). The edits are made in the parent spec in the same
change, so the two documents agree.

## Summary

Will played Part 2 and reported two things about his Bound Vexes.

**Giving gear did nothing.** He right-clicked a vex while holding a weapon or a piece of armor
and expected a swap. That is Part 3 of the Evoking II design, which was never built. This change
builds it: right-click your vex holding armor, a weapon, a shield, a splash potion or an arrow and
the vex takes it and hands back what it had in that place. You can see the armor on the vex. A vex
with a bow shoots; a vex with a healing potion throws it at hurt friends. Nothing a vex holds is
ever used up, and everything you gave drops when the vex dies.

**Stay should be sneak + right-click, and a staying vex should hover and guard.** Today a plain
empty-hand right-click toggles stay, and a staying vex drifts around inside a 15-block-wide space
while guarding only the 8 × 8 box around itself. From now on **sneak + right-click with an empty
hand** toggles stay. A staying vex hovers at its post, fights anything hostile inside a **13 × 13
block area** centered on the post, and flies back to the post when the fight is over.

The plain empty-hand right-click, freed by that move, now takes the vex's weapon back (the parent
spec had put that on the sneak click).

The change is released as a patch: version `1.2.2`, tag `v1.2.2`.

## Glossary

| Term | Meaning |
|---|---|
| **Bound Vex**, **summoner**, **ally**, **hostile** | As in the parent spec's glossary. |
| **Post** | The block a staying vex was in when it was told to stay (vanilla's `boundOrigin`). The **post point** is the middle of that block's floor: `(x + 0.5, y, z + 0.5)`. |
| **Stay area** | The box a staying vex defends: 13 × 13 blocks wide (the post block and six blocks each way, so ±6.5 from the post point east–west and north–south) and 7 tall (±3.5 from the post point). It does not move while the vex fights. |
| **Guard area** | The box a vex looks for hostiles, allies and golems in. For a following vex: the parent spec's 8 × 8 × 7 box around the vex. For a staying vex: the stay area. |
| **Reach** | 7.25 blocks: how far a *following* vex keeps a target (12 blocks while it holds ranged gear, §4.3). A staying vex keeps a target for as long as the target's feet are in the stay area. |
| **Given gear** | Items the summoner handed the vex, plus its own iron sword. Real: they come back or drop. |
| **Conjured gear** | Items a vex makes for a job (today only the repair's iron ingot). Never drops, cannot be taken. |
| **Stash** | Where a given item waits while a conjured item sits in its slot. Saved with the vex. |
| **Support gear** | A splash or lingering potion whose effects are all beneficial: thrown at hurt friends, not at enemies. |
| **Gear kind** | What a held item is to a vex: armor (head, chest or legs), melee weapon, launcher (bow or crossbow), shield, throwable (splash or lingering potion), arrow, or not gear. |

## 1. Scope

In scope:

1. The new stay: gesture, hover, stay area (§2).
2. The click table: what each right-click on a Bound Vex does (§3).
3. Gear as the parent spec's §4 designs it: armor, weapons, shields, potions and arrows, nothing
   used up, the stash, drops, saving, the armor you can see (§4–§6).
4. Docs, tests, version `1.2.2` (§7–§8).

Out of scope: Parts 4–7 of the parent spec; the team-move "busy" answer of the parent's §4.4
(Part 6 adds it with the moves); any new packet; any on-screen sign that a vex is staying beyond
what the hover shows (Will judges in the playtest whether one is needed).

## 2. The new stay

### 2.1 The gesture

Sneak + right-click with an empty main hand, by the summoner, toggles `STAYING`, with
`SoundEvents.VEX_AMBIENT` as today. The plain empty-hand click no longer toggles it (§3).

### 2.2 The post and the hover

`setStaying(true)` sets the bound origin to the vex's block, as today; that block is the **post**.
`StayWanderGoal` (the drift) is replaced by `HoldPostGoal`, same priority (5) and flag (`MOVE`):

- `canUse`: staying, no target, the move control is free (`!hasWanted()`), the vex is **nearly
  still** (squared speed under `STILL_SPEED_SQR = 0.01`, so the goal waits out a coast), and the
  vex's feet are farther than **1 block** from the post point.
- `start`: `setWantedPosition(post point, speed)` with speed **0.7** (the follow goal's coasting
  speed) when the vex is more than **3 blocks** (`POST_NEAR`) from the post point, and **0.25**
  within 3 blocks, slow enough that a pass cannot overshoot the tolerance. One move per use
  (`canContinueToUse` is false).

So a staying vex hangs still at its post (vanilla's vex has no gravity and its move control stops
it when it arrives), leaves it to charge a target, to flee a hissing creeper, to dodge a golem or
to repair one, and comes back when that is over. The 1-block tolerance is wider than the distance
at which vanilla's move control calls a move finished (`getBoundingBox().getSize()`, the mean
of the box's three sides: about 0.53 for the 0.4 × 0.8 vex, `Vex.java:309`), so the goal never
fights the move control. Vanilla's move control only halves the velocity on arrival, so a vex
that flew far coasts a couple of blocks past the post; the nearly-still test makes the goal wait
that coast out, and the slow approach speed keeps the second pass inside the tolerance. A vex with a ranged weapon (§4.3) keeps its distance from the
target as any shooter does and returns afterwards.

The re-post after a cross-dimension teleport (the parent's §3.3) is unchanged: the arrival block
becomes the post.

### 2.3 The stay area

The pure class `GuardArea` (`spell.rules`) gains:

- `STAY_HALF_WIDTH = 6.5` and `containsStaying(dx, dy, dz)`: `|dx| ≤ 6.5 && |dy| ≤ 3.5 && |dz| ≤
  6.5`, faces included, false for NaN. Offsets are from the **post point** to the other entity's
  feet. The height is the following box's (`HALF_HEIGHT`), since Will gave only the 13 × 13.
- `FOLLOW_RANGE = 20.0`: the value of the vex's `FOLLOW_RANGE` attribute from now on (it was
  `REACH`). Vanilla's target goals drop a target beyond the attribute, measured from the vex; a
  staying vex and its target can be at opposite corners of the stay area, `2 · √(6.5² + 6.5² +
  3.5²) ≈ 19.7` blocks apart, so the attribute must be at least that. The mod's own tests (below)
  stay the real limit; the attribute is only vanilla's outer bound. A vex saved by Part 2 carries
  the old base value 7.25 in its save, so `readAdditionalSaveData` sets the base value again.

`BoundVex` routes every "in the guard area" question through one place, so every goal follows
the mode:

- `inGuardArea(entity)`: following → `GuardArea.contains` of the offset from the vex (as today);
  staying → `GuardArea.containsStaying` of the offset from the post point.
- `guardArea()` (the broad-phase box for entity queries): following → as today; staying → the
  stay box around the post point.
- `takesTarget(target)` and `keepsTarget(target)`: the reach clause becomes "following → within
  `REACH` of the vex; staying → `inGuardArea(target)`". The other clauses (same dimension, alive,
  attackable, prey) are unchanged. **Taking** is the test every goal runs when it *picks* a target
  (the grudge goal's `remembered`, the ally goal, the patrol goal's selector); **keeping** is the
  test of every goal's `canContinueToUse`, and differs only for a following vex with ranged gear
  (§4.3).

What follows from that, with no further code: the patrol goal hunts hostiles in the stay area;
the ally-fight goal joins fights of allies in the stay area; the grudge goal's "an ally in the
guard area was hurt" reads the stay area, and its other three sources (who hurt the vex, who hurt
the summoner, whom the summoner hit) are taken only while that mob stands in the stay area;
`RepairGolemGoal` repairs golems in the stay area. A staying vex never chases anything out of its
area: the moment the target's feet leave the box, the target is dropped and the vex returns.

Toggling stay off while the vex has a target farther than `REACH` drops the target on the next
check (the following rule applies again) and the vex flies to its summoner.

## 3. The click table

`BoundVex.mobInteract`, main hand only (an off-hand call always answers `PASS`), summoner only:

| Main hand | Sneaking | What happens |
|---|---|---|
| empty | yes | Toggle stay (§2.1). |
| empty | no | Take the main-hand weapon back (§4.4). Nothing in the vex's main hand, or a conjured item there: nothing happens. |
| a gear item (§4.1) | either | Give it: the vex takes one, the summoner gets back what the vex had in that slot (§4.2). |
| anything else | either | `PASS`, as vanilla. |

**The two sides.** The client does not know the summoner (the mark is not synced), so it answers
from the hand alone: `SUCCESS_SERVER` for an empty main hand (sneaking or not) and for a gear
item, `PASS` otherwise. That keeps `Minecraft.startUseItem` from falling through to the off hand
or to the item's own use (a potion would be thrown at the vex, a bow drawn) on every click that
the server will handle. The server decides: it answers `PASS` for anyone who is not the summoner
and changes nothing, and `SUCCESS_SERVER` for the summoner in the first three rows, including the
"nothing happens" case. The gear test must therefore read only what both sides have: the stack's
item and components.

A stranger's click with a gear item is swallowed on their client (their potion is not thrown at
your vex). Accepted: it is the same trade Part 2 made for the empty hand.

## 4. Gear

Built as the parent spec's §4 says. This section restates the rules compactly and marks every
difference.

### 4.1 Gear kinds

`VexGear` (`evoking` package; it reads vanilla items, so it is not pure core) classifies a stack,
in this order:

| Kind | Test | Slot |
|---|---|---|
| Armor | `DataComponents.EQUIPPABLE` with slot `HEAD`, `CHEST` or `LEGS` | that slot |
| Shield | `DataComponents.BLOCKS_ATTACKS` present (vanilla's shield test; the plan confirms the component's name in 26.3) | `OFFHAND` |
| Launcher | `BowItem` or `CrossbowItem` | `MAINHAND` |
| Throwable | `Items.SPLASH_POTION` or `Items.LINGERING_POTION` with at least one effect | `MAINHAND` |
| Arrow | `ArrowItem` (plain, tipped, spectral), unless it is tipped with only beneficial effects | `MAINHAND` |
| Melee | an `ATTACK_DAMAGE` modifier for `MAINHAND` in `DataComponents.ATTRIBUTE_MODIFIERS` (swords, axes, maces, tridents, and tools) | `MAINHAND` |
| Not gear | everything else; `EQUIPPABLE` items for `FEET` or `BODY`; a potion with no effects (water, awkward); a tipped arrow whose effects are all beneficial (an arrow wounds whoever it hits, so it cannot heal a friend) | none |

### 4.2 Giving

The vex takes **one** item (`ItemStack.consumeAndReturn(1, player)`, `ItemStack.java:1211`: a
copy of one, and the hand shrinks by one unless the player has infinite materials; the plan
confirms the creative rule in its body) into the kind's slot with `setItemSlot`, and the item
that was there goes to the summoner (`getInventory().add`, else dropped at the summoner). A
conjured item in that slot (a repair in progress) is not handed over: the new gift goes to the
stash (§5.1) and the old stashed item comes back instead. `SoundEvents.ARMOR_EQUIP_GENERIC` for
armor and the vex's ambient sound for the rest; the summoner's arm swings (the server's
`SUCCESS_SERVER`).

Armor applies its attributes and enchantments (vanilla `setItemSlot`).

### 4.3 How a vex fights with what it holds

The main-hand item picks the fighting style; the charge goal's copy and the two new goals check
it in `canUse`, so exactly one runs:

- **Melee, or bare-handed, or a non-combat item** (nothing, or the conjured ingot): the charge
  goal, as today. Vanilla's `Mob.doHurtTarget` uses the main hand's attribute modifiers and
  enchantments, so a diamond sword hits harder. Tridents are melee.
- **Ranged** (a launcher, a throwable or arrow that is not support gear): `RangedGearGoal`
  (priority 3, flags `MOVE` and `LOOK`), while the vex has a target: hold between 6 and 10 blocks from it
  (fly away under 6, toward it over 10, at the charge speed), look at it, and every **40 ticks**
  with line of sight shoot:
  - bow or crossbow: `ProjectileUtil.getMobArrow(vex, new ItemStack(Items.ARROW), 1.0F, heldCopy)`
    (`ProjectileUtil.java:273`), `pickup = DISALLOWED`, `SoundEvents.ARROW_SHOOT` (bow) or
    `CROSSBOW_SHOOT`;
  - arrow item held: `getMobArrow(vex, heldCopy, 1.0F, null)`, which asks the `ArrowItem` for its
    own entity (tipped effects carried, a spectral arrow stays spectral), `pickup = DISALLOWED`
    (set explicitly: the default is disallowed, but the rule should not rest on a default),
    `ARROW_SHOOT`;
  - potion: `ThrownSplashPotion` / `ThrownLingeringPotion` of a copy of the stack,
    `SPLASH_POTION_THROW` / `LINGERING_POTION_THROW`.
  A staying vex with a ranged weapon moves the same way; its target is still limited to the stay
  area (§2.3). A **following** vex with ranged gear *keeps* its target out to **12 blocks** (the
  band's far edge plus a 2-block margin for the coast) instead of the 7.25 reach, or it would drop
  the target the moment it backed off; it still only *takes* targets within the 7.25 reach
  (`takesTarget`, §2.3) and, for the patrol, inside its 8 × 8 × 7 box: a grudge or an ally's fight
  ten blocks away is not taken, though a target already held at ten is kept.
  **Friendly fire.** A ranged vex holds its fire while a friend (the summoner, an ally, a Bound Vex
  of the same summoner) is in the way: for arrows, within 1 block of the segment from the vex's eyes to the **point the arrow is
  aimed at** (`VexShots.aimPoint`, a third of the way up the target's box, shared with the aim so
  the two cannot drift apart; the distance is the pure `SegmentDistance.distanceToSegment`); for
  potions, within 4 blocks of the target (the splash). As a backstop, damage whose attacker is a
  Bound Vex and whose victim is its summoner or something it may not fight (`!isPrey`) is
  cancelled (`LivingIncomingDamageEvent`). **With the summoner absent** (a vex that has a summoner
  mark whose player is offline, dead, spectating or in another level, so `isPrey` cannot be asked),
  an arrow or potion already in flight is still stopped: *indirect* damage (the direct entity is
  not the vex itself) is cancelled for any victim that is not an `Enemy` and for any victim
  carrying the same summoner mark; the vex's own melee is untouched, and a vex with no mark at all
  does nothing here. A lingering cloud left on the ground can still catch
  a friend who walks into it; accepted, and a playtest item.
- **Support gear** (a throwable whose effects are all `MobEffectCategory.BENEFICIAL`, and at
  least one effect): the vex does not fight (no charge, no
  ranged goal; it still takes targets, which calls the wolves). `SupportGearGoal` (priority 3,
  **no flags**, so the follow goal and the post goal keep running beside it): every 40 ticks, the nearest of {the summoner, an ally `LivingEntity`, a Bound Vex
  of the same summoner, itself excluded} within 10 blocks, with line of sight and **below half
  health**, gets a copy of the potion thrown at it. Friends that healing would hurt (the undead,
  `isInvertedHealAndHarm`) are skipped. The scan runs on the shared 10-tick clock. The goal does
  not move the vex.

**Shield.** Vanilla's own blocking (`LivingEntity.applyItemBlocking`, `LivingEntity.java:1378`)
only works for an entity that is *using* the shield (`getItemBlockingWith`), which a mob never
is, so `BoundVex.hurtServer` does the check itself with vanilla's geometry. With a shield in the
offhand, damage is cancelled (returns false, plays `SoundEvents.SHIELD_BLOCK`) when the source has
a position (`getSourcePosition() != null`: melee, projectiles and explosions, not fire or fall),
is not `DamageTypeTags.BYPASSES_SHIELD`, is not a piercing arrow (`AbstractArrow.getPierceLevel()
> 0`, vanilla's exception), and comes from in front: the dot of the flat view vector from the
head's yaw (`calculateViewVector(0, getYHeadRot())`) with the flattened, normalized direction from
the vex to the source is ≥ 0 (vanilla's 90° each side). A hit that would not have hurt anyway (a
dead or invulnerable vex, zero damage) is not a block. A blocked hit still records its attacker
(`setLastHurtByMob`), as vanilla's blocking does, so the vex answers it. No durability is spent.

### 4.4 Taking the weapon back

Plain (un-sneaked) empty-hand right-click by the summoner: the vex's main-hand item goes to the
summoner's hand (`setItemInHand(MAIN_HAND, …)`, the hand is empty by the rule), and the vex fights
bare-handed (vanilla's vex damage). `SoundEvents.VEX_AMBIENT`. Armor and the shield cannot be
taken back by a click; they come back when replaced or when the vex dies (the parent's §13,
unchanged). While the main hand holds a conjured item nothing is taken (§3).

### 4.5 Nothing is used up

- Melee: nothing wears the weapon. In 26.3 the durability cost of a hit is in
  `ItemStack.postHurtEnemy`, which only the player's attack path calls; `Mob.doHurtTarget` never
  reaches it (found in the build; api-notes §22). `BoundVex.doHurtTarget` still copies the
  main-hand stack before `super` and puts the copy back if it changed, as a guard against an
  enchantment or a later version that does.
- Armor: nothing to do. A mob's worn armor is never damaged in 26.3: `LivingEntity.hurtArmor` and
  `hurtHelmet` are empty (`LivingEntity.java:1957-1961`) and only `Player`, `Wolf` and `Horse`
  override them.
- Ranged and support: copies are thrown; the held stack is never shrunk.
- Shield: no durability call is made.

## 5. Stash, drops, saving

### 5.1 The stash

`BoundVex` keeps, per `EquipmentSlot` it uses (`MAINHAND`, `OFFHAND`, `HEAD`, `CHEST`, `LEGS`), a
stashed `ItemStack` and a **conjured** flag:

- `conjure(slot, stack)`: if the slot is not conjured, its item moves to the stash; the conjured
  stack takes the slot; the flag is set. (Conjuring over a conjured item just replaces it.)
- `dismiss(slot)`: if the flag is set, the stashed item returns to the slot and the flag clears.
- `isConjured(slot)`.

`RepairGolemGoal` uses these instead of its own `held` field, which closes Part 2's known gap (a
vex saved or carried through a portal mid-repair kept the ingot): on load, `BoundVex` dismisses
every conjured slot, because the goal that conjured it is not running any more.

### 5.2 Drops

Bound Vexes have no loot table. Vanilla drops a mob's equipment in `Mob.dropCustomDeathLoot`
(`Mob.java:920-946`) by per-slot drop chance; a **guaranteed** slot (`setGuaranteedDrop`,
`Mob.java:621`) drops whoever the killer was and undamaged. `Vex.populateDefaultEquipmentSlots`
sets the main hand's chance to 0 at spawn (`Vex.java:219`), and saved vexes carry that, so the
chances are not trusted to be right beforehand: `BoundVex.dropCustomDeathLoot` first dismisses
every conjured slot (the conjured item vanishes, the stashed item is back in its slot), then
marks its five slots guaranteed, then calls `super`. Every given item, the iron sword included,
drops as-is; no conjured item does. Vanilla's own limits apply: nothing drops with the
`mob_drops` game rule off (`shouldDropLoot`), and an item with Curse of Vanishing does not drop.

On Peaceful removal the same items drop. `Mob.checkDespawn` discards the vex
(`Mob.java:715-716`) without any death, so `BoundVex.checkDespawn`, when the level is Peaceful
and the type is not allowed there, dismisses conjured slots and spawns every slot's item
(`spawnAtLocation`, clearing the slot) before calling `super`. The parent's §10 said "in `remove`
for `DISCARDED`"; that would also drop gear on a test cleanup or a later part's silent
`discard()`, so the drop is tied to the Peaceful check alone. If a NeoForge despawn listener
keeps the vex alive, it has dropped its gear anyway; accepted. `/kill` is a death and drops.

### 5.3 Saving

Vanilla saves equipment. `addAdditionalSaveData` / `readAdditionalSaveData` add the stash (a
list of slot name + stack) and the conjured slots, next to `staying`. A cross-dimension teleport
copies the save, so gear, stash and flags arrive with the vex.

## 6. Seeing the gear

As the parent's §4.6: `client.evoking.BoundVexRenderer extends VexRenderer` with
`BoundVexRenderState extends VexRenderState` carrying the head, chest and legs stacks;
`BoundVexArmorLayer` draws them through vanilla's equipment layer renderer on a humanoid armor
model posed from the vex model's head and body parts and scaled to the vex. The main-hand item is
drawn by vanilla's `ItemInHandLayer` already; the offhand shield through the same layer's left
hand (the plan confirms `VexModel` and `VexRenderState` carry the left-hand item in 26.3, and
adds it to the state if they do not). `BoundVexRenderers` registers the new renderer.

The exact scale and offsets are the plan's first guess and Will's eye: playtest items.

## 7. Error handling

- **Not the summoner, or no summoner mark** (`/summon`): every click answers `PASS` on the server;
  the gear the vex spawned with still shows and still drops.
- **Summoner offline, dead, in another level or spectating:** no click reaches the vex from them
  (a spectator's click is not an interaction; a dead player has none). A staying vex keeps its
  post and, as today, fights nothing without a summoner (`isPrey`).
- **Summoner's inventory full on a swap:** the returned item is dropped at the summoner.
- **Giving while a repair is in progress:** §4.2 (the gift waits in the stash).
- **A vex with support gear and no hurt friend:** it idles at its orbit or post.
- **Stay toggled on inside a wall:** vexes clip through blocks; the post is that block.
- **Old saves:** a Part 2 vex has no stash data (read as empty), drop chances are set in the
  constructor, the follow range is reset on load (§2.3). A vex that was drifting when saved flies
  to its post on load.
- **Shield and the golem dodge, the creeper flee:** unchanged; an explosion has a source position,
  so a shield blocks a creeper blast from the front. Accepted (vanilla shields do too).

## 8. Testing, docs, delivery

**Pure core (TDD, jqwik):** `SegmentDistance.distanceToSegment` (beside the middle, beyond each
end clamped, a zero-length segment, a point on the segment; property: never more than the distance
to either end) and `GuardArea.containsStaying` (examples at the faces and just outside;
properties: symmetric in each axis, every point the following box accepts is accepted, NaN is
out) and `FOLLOW_RANGE ≥ 2 · the stay box's half-diagonal` and `≥ REACH`. `./gradlew pitest`
afterwards and `docs/mutation-testing.md` updated.

**GameTests** (new class `BoundVexGearGameTests`; stay cases join `BoundVexFollowGameTests` and
`BoundVexTargetGameTests`; the existing stay-toggle tests are rewritten for the sneak click):

- Stay: sneak + empty click toggles; plain empty click does not; a stranger's click does nothing;
  toggling stay off with a target farther than reach drops it; a vex loaded from a save whose
  follow-range base is 7.25 has 20 afterwards;
  a staying vex pushed 3 blocks off its post returns to within 1 block and does not wander
  (position sampled over time stays within 1 block); a zombie 6 blocks from the post is attacked,
  one 8 blocks away is not; a target that leaves the area is dropped; the off-hand pearl is not
  thrown on a sneak click.
- Gear: each kind lands in its slot and the old item returns (helmet over helmet, sword for the
  iron sword, shield, bow, potion); boots and a stick answer `PASS`; exactly one item leaves a
  stack of several, and none leaves a creative player's; plain empty click returns the weapon and leaves armor and shield; a stranger
  cannot give or take.
- Fighting: a diamond-sword hit does more than an iron-sword hit; the sword's damage value is
  unchanged after a hit; a bow vex spawns an arrow entity and keeps ≥ 6 blocks; a poison-tipped
  arrow item is shot as an `Arrow` carrying poison; a harming-potion vex spawns a thrown potion at
  its target, a lingering one a `ThrownLingeringPotion`; a staying bow vex whose target dies
  returns to within the post tolerance plus half a block; a healing-potion vex throws at a half-health ally and
  not at a full-health one and never at the zombie; a following bow vex keeps its target while it
  holds the band; a ranged vex holds its fire with a friend in the way (a golem, the summoner on the arrow
  line, the summoner within 4 blocks of a splash target, and fires once clear), and its arrow does
  not hurt its summoner; a following bow vex does not take a grudge ten blocks away (and takes one
  at five); the backstop spares a calm golem, a sibling vex and the summoner (arrow and potion
  sources) but not a husk or a golem mad at the summoner, and with the summoner absent it spares a
  pig and a sibling; the held stack's count never changes.
- Shield: a hit from the front is cancelled, from behind is not, damage with no position is not,
  a piercing arrow from the front is not; a blocked hit makes the attacker the vex's last attacker.
- Support gear does not hold the vex: a healing-potion vex still follows its summoner.
- Stash and drops: conjure/dismiss round trip; a gift during a conjure comes back after
  `dismiss`; death drops given gear, the sword and the stash, and no conjured item; a save/load
  round trip (`saveWithoutId` → a new vex `load`) keeps stash and staying and dismisses conjured
  slots; Peaceful removal drops gear.

**`runData`** proves the renderer, the layer and the render state load on the client.

**Playtest checklist:** a new "Evoking II (Part 3)" section (gear swaps, armor fit, shooter and
support behavior, shield, drops) and the Part 2 stay items rewritten for the sneak click, the
hover and the 13 × 13 area.

**Docs:** README (the vex paragraph: the new clicks, gear, the stay area), `neoforge.mods.toml`
description if it mentions vex controls, `docs/api-notes.md` §22 (every vanilla fact this part
relies on, verified against `build/mc-src`) and its §21 interaction note (the click rule it
records is the old one), `docs/mutation-testing.md`, the parent spec's
amended lines, and the Part 2 known-gap note in `RepairGolemGoal`'s Javadoc and the checklist.

**Delivery:** branch `feat/evoking-ii-part-3-gear`, one PR. `mod_version` becomes `1.2.2` in the
PR (`chore(release)` commit); the tag `v1.2.2` is created on `main` after the merge, since CI
checks a tag against `mod_version` and the tag must name the merged commit.

## 9. Decisions Will did not state

| Decision | Choice | Why, and the alternative |
|---|---|---|
| What "equipping" covers | All of the parent's Part 3 | Will designed it there and expected it to work; a swap with no shooting, no armor to see and no drops would be a second half-feature. Alternative: only the armor and melee swap. |
| Plain empty-hand click | Takes the weapon back | Will moved stay onto the sneak click, which the parent spec had given to the take-back; the plain click is the only free gesture, and "empty hand for its weapon" is also an exchange. Alternative: no take-back at all. |
| Sneaking while holding gear | Ignored: it gives | One rule per hand content. |
| Post | The block's floor center, not the exact spot | The 13 × 13 is then exactly thirteen block columns each way; the settle is under a block. |
| Stay area height | 7 blocks (±3.5), the following box's | Will gave only 13 × 13. |
| Stay area is fixed to the post | Yes | "Defend an area around it": a box that moved with the fighting vex would walk away from the post. |
| Staying vex and far grudges | Only inside the stay area | A staying vex that left to avenge its summoner would not be staying. |
| Return tolerance | 1 block | Just over the move control's own arrival distance. |
| `FOLLOW_RANGE` attribute | 20 for every Bound Vex | Vanilla reads it when the goal is built; the mod's own reach and area tests are the real limits. |
| One item per gift | Yes, from a stack too | A vex holds one potion and never uses it up. |
| Tools as weapons | Yes (anything with a main-hand attack-damage modifier) | The parent's rule; a pickaxe is a poor sword but a harmless gift. |
| Support target | Nearest hurt friend within 10 blocks, with line of sight | The parent's numbers; sight keeps potions out of walls. |
| Support gear is potions only | A beneficial tipped arrow is not gear | The parent spec allowed it, but an arrow does damage before its effect: it would wound, or kill, the friend it was meant to heal. |
| Potions with no effects | Not gear | A vex throwing water forever is noise. |
| Ranged keep range | 12 blocks for a following vex with ranged gear | The 6–10 band lies beyond the 7.25 reach; found in the build. |
| Friendly fire | Hold fire near friends, cancel direct damage to them | A pet that shoots its owner is worse than one that waits. |
| Curse of Vanishing | Such gear is destroyed with the vex | Vanilla's rule for every mob. |
| Shield test | Vanilla's blocking geometry (flat head-yaw view vs. flat direction to the source, piercing arrows excepted), done in `hurtServer` | Vanilla's blocking needs the entity to be using the shield; a hit from above the front still counts. |
| Support goal holds no move flag | Yes | A healer that stopped following would be left behind. |
| Peaceful drop | Tied to the Peaceful check, not to every discard | A discard is also how tests and later parts remove a vex silently. |
| Stay indicator | None | The hover is the sign; Will judges in the playtest. |
| Version | `1.2.2` now | Will: tag this as a patch. The parent's "one bump to 1.3.0 after Part 7" is amended to "patch releases per part as Will asks". |
