# Evoking II, Part 3 (gear and the new stay) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Bound Vex takes gear from its summoner's right-click (armor, weapons, a shield, potions, arrows), fights with it, shows it, never uses it up and drops it on death; stay moves to the sneak + empty-hand click, and a staying vex hovers at its post and defends a 13 × 13 block area around it.

**Architecture:** Everything hangs off `evoking.BoundVex`. The pure class `spell.rules.GuardArea` gains the stay box and the follow-range bound; `BoundVex` answers "in the guard area" from the vex (following) or from the post (staying), so every existing goal follows the mode with no change of its own. A small `HoldPostGoal` replaces the wander goal. Gear is classified by `evoking.VexGear` (reads only the stack's item and components, so both sides agree), handed over in `BoundVex.mobInteract`, and used by two new goals (`RangedGearGoal`, `SupportGearGoal`) beside the existing charge goal. A per-slot stash with a conjured flag lets a job put a temporary item in a slot; `RepairGolemGoal` moves onto it. The client gets `BoundVexRenderer` with an armor layer. No packet is added.

**Tech Stack:** Minecraft 26.3, NeoForge 26.3.0.23-beta (ModDevGradle), Java 25, JUnit 5, jqwik, ArchUnit, AssertJ, Error Prone (`-Werror`), Spotless + palantir-java-format, PIT, pre-commit, gitlint, markdownlint-cli2.

**Spec:** `2026-11-09-evoking-ii-part-3-gear-and-stay-design.md` ([the Part 3 design spec](../specs/2026-11-09-evoking-ii-part-3-gear-and-stay-design.md)); "§" below points into it. Its parent is `2026-11-07-evoking-ii-design.md` ("parent §").

## Global Constraints

- Minecraft **26.3**; NeoForge **26.3.0.23-beta**, pinned; Java **25**. Mod ID **`spellcraft`**; base package **`io.github.willrieske.spellcraft`**.
- **Branch:** `feat/evoking-ii-part-3-gear` in the worktree `C:\dev\spellcraft\.claude\worktrees\evoking-ii-part-3`; commands run from that worktree's root. Never `cd` to `C:\dev\spellcraft` itself. In the Bash tool use `./gradlew`; in PowerShell use `.\gradlew.bat`. Push nothing between tasks.
- **Minecraft sources for checks:** unpacked in the main checkout at `C:\dev\spellcraft\build\mc-src` (vanilla, patched) and `C:\dev\spellcraft\build\nf-src` (NeoForge); every `build/mc-src` path below means that directory (the worktree has no copy). Read them with Grep/Read. Every vanilla name in this plan was checked on 2026-11-09 where a `file:line` is given; where a step says **confirm**, read the cited vanilla method first and follow what it does, not this plan's sketch. Task 1 records the facts in `docs/api-notes.md` §22; read §21 and §22 before writing Java.
- **Numbers (spec, verbatim):** stay area **13 × 13 × 7** (`±6.5`, `±3.5`, `±6.5` from the post point, faces included); post point = `(x + 0.5, y, z + 0.5)` of the bound-origin block; return tolerance **1** block; return speed **0.7**; `FOLLOW_RANGE` attribute **20**; following reach stays **7.25** and the following box **8 × 8 × 7**; ranged band **6–10** blocks; shot interval **40** ticks; support range **10** blocks, below **half health**; one item per gift.
- **Click table (§3), main hand only, summoner only:** empty + sneaking → toggle stay; empty + not sneaking → take the main-hand weapon back; a gear item (sneaking or not) → give; anything else → `PASS`. The client answers `SUCCESS_SERVER` for an empty main hand and for a gear item, `PASS` otherwise; the server answers `PASS` to anyone but the summoner.
- Pure core (`spell.rules`): no `net.minecraft`/NeoForge/jspecify imports (ArchUnit rule 2). Client-only code only under `client` (rule 1). Common code never references `client`.
- Error Prone runs with `-Werror`; suppress only with `@SuppressWarnings("<Check>")` plus a comment why. **Every new private constant, field or method needs a reader in the same task.** `-Xlint:all` is on: never assign `getMoveControl()` to a local (raw type); call through it.
- **Doc comments:** every new class, method and constant gets Javadoc in the style of `BoundVex.java` (what, why, the spec section, the api-notes section for vanilla facts).
- **Unit test style:** `~/.claude/development-methodology.md`, *Unit test style*: a Javadoc per test ("When …, should …" then `<p>Method under test: {@link …}`), one `@Nested` class per method under test, `// Arrange` / `// Act` / `// Assert`. GameTests keep their files' style (same Javadoc shape, `static void` bodies, `helper.assert…`).
- **GameTests:** helpers are in `gametest/BoundVexTestSupport.java` (`boundVex`, `parked`, `followOnly`, `noTargets`, `husk`, `summoner`, `placeBeside`), `GameTestSupport.java` (`hold`, `whenDone`, `saved`, `reloaded`, `drainSounds`) and `MockPlayers.java`. A test whose vex flies or needs room registers through `FangGameTests.addPadded(SHARED, "name", Class::method)`; the rest through `SpellcraftGameTests.add(SHARED, "name", Class::method)`. Hostiles are husks with `setNoAi(true)`; every spawned mob is discarded through `whenDone`. A mob's `canUse` runs only on ticks where `(tickCount + id)` is even, and scans run every 10 ticks: a test waiting on a scan needs at least 20 ticks beyond its setup. Clicks are simulated with `vex.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO)` after `GameTestSupport.hold(player, stack)`; sneaking with `player.setShiftKeyDown(true)`.
- **No red run for GameTest tasks (3–6):** their tests compile only with the production change. The red-then-green cycle is for Task 2 (pure).
- **Verification recipe:** `./gradlew <task> --console=plain > build/<task>.log 2>&1; echo "exit=$?"; tail -40 build/<task>.log` (never `| tail` on Gradle itself). Expected `exit=0`. `runGameTestServer` takes minutes: run it in the background, then `grep -n "FAILED\|required tests" build/runGameTestServer.log | tail -5`. Expected: `All N required tests passed`, where N is the previous task's total plus the tests this task registers (Task 1 records the baseline; each task states its delta).
- **Format before you build:** `./gradlew spotlessApply --console=plain` before each build/commit; stage what it changed.
- Visual checks belong to Will: never claim a visual works; Task 8 adds `docs/playtest-checklist.md` items.
- Commits: conventional (gitlint), title ≤ 100 characters, body lines ≤ 80, no attribution lines. The commit gate runs `spotlessCheck` and `build` (minutes). **Run `git add` and `git commit` as two separate tool calls**, then check `git log -1 --oneline`.
- Release steps (version, tag) are not in this plan; they follow the branch review.
- **Execution order:** strictly 1 → 8. Tasks 3–6 all edit `BoundVex.java`.

## File Structure

```text
src/main/java/io/github/willrieske/spellcraft/
├── spell/rules/GuardArea.java            + STAY_HALF_WIDTH, FOLLOW_RANGE, containsStaying        (Task 2)
├── evoking/BoundVex.java                 mode-aware area, HoldPostGoal, click table              (Task 3)
│                                         stash, drops, saving                                     (Task 4)
│                                         give, take back                                          (Task 5)
│                                         shield, nothing used up, style checks                    (Task 6)
├── evoking/RepairGolemGoal.java          uses the stash                                           (Task 4)
├── evoking/VexGear.java                  new: gear kinds, slots, ranged/support tests             (Task 5)
├── evoking/VexShots.java                 new: shoot or throw a copy of the held item              (Task 6)
├── evoking/RangedGearGoal.java           new                                                      (Task 6)
├── evoking/SupportGearGoal.java          new                                                      (Task 6)
├── client/evoking/BoundVexRenderState.java  new                                                   (Task 7)
├── client/evoking/BoundVexArmorLayer.java   new                                                   (Task 7)
├── client/evoking/BoundVexRenderer.java     new                                                   (Task 7)
├── client/evoking/BoundVexRenderers.java    registers BoundVexRenderer                            (Task 7)
└── gametest/
    ├── BoundVexStayGameTests.java        new (Task 3)
    ├── BoundVexGearGameTests.java        new (Tasks 4–6)
    ├── BoundVexCapGameTests.java         stayToggle rewritten (Task 3)
    └── SpellcraftGameTests.java          + two registerAll() lines (Tasks 3, 4)
src/test/java/.../spell/rules/GuardAreaTest.java, GuardAreaPropertiesTest.java   + staying cases (Task 2)
docs/api-notes.md (§22, §21 note), docs/playtest-checklist.md, docs/mutation-testing.md, README.md
```

---

### Task 1: Record the verified 26.3 facts and the GameTest baseline

**Files:**

- Modify: `docs/api-notes.md` (new §22 at the end; one sentence in §21's interaction bullet)

**Interfaces:**

- Produces: `docs/api-notes.md` §22, cited by later Javadoc as "docs/api-notes.md §22".

- [x] **Step 1: Re-check each fact below against `build/mc-src`** (open the cited line; correct the note if a line number moved) and add the section:

```markdown
## 22. Evoking II, Part 3: gear, drops, blocking, the armor layer (Evoking II Part 3 task)

Verified 2026-11-09 against the unpacked sources (`build/mc-src`).

- Interaction: `Mob.interact` (`Mob.java:1160-1183`) runs the name tag and spawn egg first, then
  `Entity.interact`, then `mobInteract`. The client sends the interact packet with
  `player.isShiftKeyDown()` (`MultiPlayerGameMode.java:488-491`); sneaking does not skip an entity
  interaction. `Minecraft.startUseItem` (`Minecraft.java:1775-1789`) returns at the first
  `InteractionResult.Success` from `gameMode.interact`; on anything else it goes on to the item's own
  use and to the off hand.
- Vex move control: `Vex.VexMoveControl.tick` (`Vex.java:304-327`) stops (operation `WAIT`, velocity
  halved) when the wanted position is nearer than `getBoundingBox().getSize()` (the mean of the three
  sides, about 0.53 for a 0.4 × 0.8 vex); otherwise it adds `speedModifier * 0.05` per tick toward it.
- Follow range: `NearestAttackableTargetGoal` builds its range from `FOLLOW_RANGE` at construction
  (§19) and `TargetGoal.canContinueToUse` reads the attribute live (§21); attributes are loaded by
  `LivingEntity.readAdditionalSaveData`, so a subclass that resets a base value does it after `super`.
- Drops: `LivingEntity.dropAllDeathLoot` (`LivingEntity.java:1589-1603`) calls `dropCustomDeathLoot`
  only when `shouldDropLoot` (`:606`, the `mob_drops` game rule). `Mob.dropCustomDeathLoot`
  (`Mob.java:920-946`) drops each slot by `dropChances`; a slot marked by `setGuaranteedDrop`
  (`Mob.java:621`) is "preserved": it drops whoever the killer was, undamaged, unless the item has
  `PREVENT_EQUIPMENT_DROP` (Curse of Vanishing). `Vex.populateDefaultEquipmentSlots`
  (`Vex.java:217-220`) sets the main hand's chance to 0 at spawn. `drop_chances` is saved
  (`Mob.java:388-389`).
- Peaceful: `Mob.checkDespawn` (`Mob.java:711-716`) asks NeoForge's `checkMobDespawn` hook first, then
  `discard()`s a mob whose type is not allowed in Peaceful: no death, no drops.
- Armor wear: `LivingEntity.hurtArmor` and `hurtHelmet` are empty (`LivingEntity.java:1957-1961`);
  only `Player`, `Wolf` and `Horse` override them, so a mob's worn armor is never damaged.
- Weapon wear: `Mob.doHurtTarget` (`Mob.java:1415-1436`) reads `getWeaponItem()` (the main hand,
  `LivingEntity.java:2316`), applies its enchantments, and calls `weaponItem.hurtEnemy`, which costs
  durability.
- Blocking: `LivingEntity.applyItemBlocking` (`LivingEntity.java:1378-1420`) needs
  `getItemBlockingWith()` (an item in use), which a mob never has. Its geometry: no block for a
  piercing arrow; otherwise the flat view vector `calculateViewVector(0, getYHeadRot())` against the
  flattened, normalized vector from `position()` to `source.getSourcePosition()`
  (`DamageSource.java:106`). `DamageTypeTags.BYPASSES_SHIELD` exists (`DamageTypeTags.java:10`).
  A shield is any stack with `DataComponents.BLOCKS_ATTACKS` (`DataComponents.java:213`).
- Projectiles: `ProjectileUtil.getMobArrow(LivingEntity, ItemStack projectile, float power,
  @Nullable ItemStack firedFromWeapon)` (`ProjectileUtil.java:273`); `AbstractArrow.pickup` is a
  public field, default `DISALLOWED` (`AbstractArrow.java:71`);
  `ThrownSplashPotion(Level, LivingEntity owner, ItemStack)` (`ThrownSplashPotion.java:27`) and
  `ThrownLingeringPotion(Level, LivingEntity, ItemStack)` (`ThrownLingeringPotion.java:20`).
- Items: `ItemStack.consumeAndReturn(int, @Nullable LivingEntity)` (`ItemStack.java:1211`).
- Client: `VexRenderer` (`VexRenderer.java:15-43`) adds one `ItemInHandLayer`; `VexRenderState extends
  ArmedEntityRenderState`, which carries both hands' items (`ArmedEntityRenderState.java:18-22`), and
  `VexModel.translateToHand` (`VexModel.java:119-125`) handles the left arm, so an offhand shield is
  drawn with no new code. `HumanoidArmorLayer` (`HumanoidArmorLayer.java:19`) needs a
  `HumanoidRenderState` and cannot sit on a `VexModel`; its `renderArmorPiece` (`:54-67`) shows the
  call: `EquipmentLayerRenderer.renderLayers(LayerType, assetId, model, state, stack, poseStack,
  collector, light, outlineColor)` (`EquipmentLayerRenderer.java:41`), layer type `HUMANOID_LEGGINGS`
  for `LEGS` and `HUMANOID` otherwise. Armor models: `ArmorModelSet.bake(locations, modelSet,
  factory)` (`ArmorModelSet.java:37`), `EntityRendererProvider.Context.getEquipmentRenderer()`
  (`EntityRendererProvider.java:95`).
```

Also, for each of these four, read the vanilla body and add one bullet stating what you found (they are used in Tasks 5 and 6): `ItemStack.consumeAndReturn` (does it leave the stack alone for a player with infinite materials?); how a stack's attack-damage modifier is read (`DataComponents.ATTRIBUTE_MODIFIERS`, the `ItemAttributeModifiers` entry type and its slot test); how a potion stack's effects are listed (`DataComponents.POTION_CONTENTS`, `PotionContents.getAllEffects`, `MobEffect.getCategory`); and how vanilla mobs launch a projectile into the level (`AbstractSkeleton.performRangedAttack` and `Witch.performRangedAttack`: the spawn helper and the `shoot` arguments).

In §21, find the bullet that describes the stay click (`SUCCESS_SERVER` for the empty-handed, un-sneaked click) and append: `Part 3 changed the click rule (§22 and the Part 3 spec §3): sneak + empty hand toggles stay, the plain empty hand takes the weapon back, a gear item is given.`

- [x] **Step 2: Record the GameTest baseline.** Run `./gradlew runGameTestServer --console=plain > build/runGameTestServer.log 2>&1; echo "exit=$?"; grep -n "FAILED\|required tests" build/runGameTestServer.log | tail -5` in the background. Expected `exit=0` and `All N required tests passed`. Write N into this plan's "Resume State" section at the bottom (`Baseline GameTests: N`).

- [x] **Step 3: Commit**

```bash
git add docs/api-notes.md docs/superpowers/plans/2026-11-09-evoking-ii-part-3.md
```

```bash
git commit -m "docs(api-notes): record the 26.3 facts for Evoking II Part 3"
```

> **As built (`8854374`):** diverged — the sources corrected the plan in four places later tasks use: `Player.drop` takes three arguments (`drop(ItemStack, boolean thrownFromHand, Prediction)`); `SoundEvents.ARMOR_EQUIP_GENERIC` and `SHIELD_BLOCK` are holders (`.value()`); `LivingEntity.onEquipItem` already plays the equip sound for armor and an offhand shield set with `setItemSlot`, so Task 5 plays no armor sound of its own; mobs launch projectiles with `Projectile.spawnProjectileUsingShoot` (signatures in api-notes §22); reason: api-notes §22 records what `build/mc-src` says.

---

### Task 2: Pure `GuardArea`: the stay box and the follow range

**Files:**

- Modify: `src/main/java/io/github/willrieske/spellcraft/spell/rules/GuardArea.java`
- Test: `src/test/java/io/github/willrieske/spellcraft/spell/rules/GuardAreaTest.java`, `GuardAreaPropertiesTest.java`

**Interfaces:**

- Produces: `GuardArea.STAY_HALF_WIDTH` (`double`, 6.5), `GuardArea.FOLLOW_RANGE` (`double`, 20.0), `static boolean GuardArea.containsStaying(double dx, double dy, double dz)`.

- [x] **Step 1: Write the failing tests.** Read both test files first and match their structure. Add to `GuardAreaTest` a `@Nested class ContainsStaying` and a `@Nested class FollowRange`:

```java
    @Nested
    class ContainsStaying {
        /**
         * When the point is on a face of the stay box (6.5 sideways, 3.5 up or down), should be inside.
         *
         * <p>Method under test: {@link GuardArea#containsStaying}
         */
        @ParameterizedTest
        @CsvSource({"6.5, 0, 0", "-6.5, 0, 0", "0, 3.5, 0", "0, -3.5, 0", "0, 0, 6.5", "0, 0, -6.5", "6.5, 3.5, -6.5"})
        void acceptsTheFaces(double dx, double dy, double dz) {
            assertThat(GuardArea.containsStaying(dx, dy, dz)).isTrue();
        }

        /**
         * When the point is just past a face, should be outside.
         *
         * <p>Method under test: {@link GuardArea#containsStaying}
         */
        @ParameterizedTest
        @CsvSource({"6.51, 0, 0", "-6.51, 0, 0", "0, 3.51, 0", "0, -3.51, 0", "0, 0, 6.51", "0, 0, -6.51"})
        void rejectsJustOutside(double dx, double dy, double dz) {
            assertThat(GuardArea.containsStaying(dx, dy, dz)).isFalse();
        }

        /**
         * When a coordinate is NaN, should be outside.
         *
         * <p>Method under test: {@link GuardArea#containsStaying}
         */
        @Test
        void rejectsNaN() {
            assertThat(GuardArea.containsStaying(Double.NaN, 0, 0)).isFalse();
            assertThat(GuardArea.containsStaying(0, Double.NaN, 0)).isFalse();
            assertThat(GuardArea.containsStaying(0, 0, Double.NaN)).isFalse();
        }
    }

    @Nested
    class FollowRange {
        /**
         * When a staying vex and its target are at opposite corners of the stay box, should still be inside the
         * follow range, and so should the following reach.
         *
         * <p>Method under test: {@link GuardArea#FOLLOW_RANGE}
         */
        @Test
        void coversTheStayBoxDiagonalAndTheReach() {
            // Arrange
            double diagonal = 2.0
                    * Math.sqrt(2.0 * GuardArea.STAY_HALF_WIDTH * GuardArea.STAY_HALF_WIDTH
                            + GuardArea.HALF_HEIGHT * GuardArea.HALF_HEIGHT);

            // Assert
            assertThat(GuardArea.FOLLOW_RANGE).isGreaterThanOrEqualTo(diagonal).isGreaterThanOrEqualTo(GuardArea.REACH);
        }
    }
```

Add to `GuardAreaPropertiesTest`, inside a new `@Group class ContainsStaying` like the file's existing `@Group` classes (add `import net.jqwik.api.Assume;`):

```java
    /**
     * When a point is mirrored in any axis, containsStaying should give the same answer.
     *
     * <p>Method under test: {@link GuardArea#containsStaying}
     */
    @Property
    void stayBoxIsSymmetric(
            @ForAll @DoubleRange(min = -20, max = 20) double dx,
            @ForAll @DoubleRange(min = -20, max = 20) double dy,
            @ForAll @DoubleRange(min = -20, max = 20) double dz) {
        boolean inside = GuardArea.containsStaying(dx, dy, dz);
        assertThat(GuardArea.containsStaying(-dx, dy, dz)).isEqualTo(inside);
        assertThat(GuardArea.containsStaying(dx, -dy, dz)).isEqualTo(inside);
        assertThat(GuardArea.containsStaying(dx, dy, -dz)).isEqualTo(inside);
    }

    /**
     * When the following box accepts a point, the stay box should accept it too (it is the wider box).
     *
     * <p>Method under test: {@link GuardArea#containsStaying}
     */
    @Property
    void stayBoxContainsTheFollowingBox(
            @ForAll @DoubleRange(min = -5, max = 5) double dx,
            @ForAll @DoubleRange(min = -5, max = 5) double dy,
            @ForAll @DoubleRange(min = -5, max = 5) double dz) {
        Assume.that(GuardArea.contains(dx, dy, dz));
        assertThat(GuardArea.containsStaying(dx, dy, dz)).isTrue();
    }
```

- [x] **Step 2: Run to see them fail.** `./gradlew test --tests "*GuardArea*" --console=plain > build/test.log 2>&1; echo "exit=$?"; tail -20 build/test.log`. Expected: non-zero exit, compile errors naming `containsStaying`, `STAY_HALF_WIDTH`, `FOLLOW_RANGE`.

- [x] **Step 3: Implement.** In `GuardArea.java`, update the class Javadoc's first sentence to mention both boxes, change the `REACH` Javadoc to `/** How far a following vex keeps fighting a target. */`, and add:

```java
    /** Half the width of the box a staying vex defends around its post: 13 blocks across (Part 3 spec §2.3). */
    public static final double STAY_HALF_WIDTH = 6.5;

    /**
     * The vex's {@code FOLLOW_RANGE} attribute: vanilla's outer bound on a target's distance from the vex. A staying
     * vex and its target can be at opposite corners of the stay box, 2·√(6.5² + 6.5² + 3.5²) ≈ 19.7 blocks apart, so
     * the bound is 20; the box and the reach are the real limits (Part 3 spec §2.3).
     */
    public static final double FOLLOW_RANGE = 20.0;

    /**
     * Whether a point is inside the stay box, faces included.
     *
     * @param dx blocks east of the post point
     * @param dy blocks above the post point
     * @param dz blocks south of the post point
     * @return true inside the box or on a face; false outside, and for NaN
     */
    public static boolean containsStaying(double dx, double dy, double dz) {
        return Math.abs(dx) <= STAY_HALF_WIDTH && Math.abs(dy) <= HALF_HEIGHT && Math.abs(dz) <= STAY_HALF_WIDTH;
    }
```

- [x] **Step 4: Run to see them pass.** Same command. Expected `exit=0`.

- [x] **Step 5: Commit**

```bash
git add src/main/java/io/github/willrieske/spellcraft/spell/rules/GuardArea.java src/test/java/io/github/willrieske/spellcraft/spell/rules/GuardAreaTest.java src/test/java/io/github/willrieske/spellcraft/spell/rules/GuardAreaPropertiesTest.java
```

```bash
git commit -m "feat(evoking): add the stay box and the follow-range bound to GuardArea"
```

> **As built (`e5ef3e0`):** matches plan.

---

### Task 3: The new stay: sneak click, hover at the post, the stay area

**Files:**

- Modify: `src/main/java/io/github/willrieske/spellcraft/evoking/BoundVex.java`
- Modify: `src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexCapGameTests.java` (`stayToggle` and any test that toggles stay through a click or asserts `FOLLOW_RANGE` equals `REACH`)
- Modify: `src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexFollowGameTests.java` (Javadoc mentions of the wander goal)
- Create: `src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexStayGameTests.java`
- Modify: `src/main/java/io/github/willrieske/spellcraft/gametest/SpellcraftGameTests.java` (+ `BoundVexStayGameTests.registerAll();` after the `BoundVexJobGameTests` line)

**Interfaces:**

- Consumes: `GuardArea.STAY_HALF_WIDTH`, `GuardArea.FOLLOW_RANGE`, `GuardArea.containsStaying` (Task 2).
- Produces: `BoundVex.postPoint()` (package-private, `Vec3`), `BoundVex.POST_TOLERANCE` (`static final double`, 1.0, package-private); `inGuardArea`, `guardArea()`, `keepsTarget` keep their signatures. `mobInteract` handles the empty hand only; Task 5 adds gear.

- [x] **Step 1: `BoundVex` production changes.**

(a) `createAttributes`: `return Vex.createAttributes().add(Attributes.FOLLOW_RANGE, GuardArea.FOLLOW_RANGE);` and fix its Javadoc ("the follow-range bound (Part 3 spec §2.3)"). `finalizeSpawn`'s Javadoc: the bonus is removed so the attribute is exactly `GuardArea.FOLLOW_RANGE`.

(b) `readAdditionalSaveData`: after the `STAYING` line add

```java
        // A vex saved by Part 2 carries the old base value (7.25, the reach), which would cut a staying vex's fights
        // short: TargetGoal.canContinueToUse reads the attribute live (docs/api-notes.md §22).
        AttributeInstance range = getAttribute(Attributes.FOLLOW_RANGE);
        if (range != null) {
            range.setBaseValue(GuardArea.FOLLOW_RANGE);
        }
```

(c) Add the post helpers and make the three area methods mode-aware (replace the bodies; keep and extend the Javadoc to say "following: the box around the vex; staying: the stay box around the post point"):

```java
    /** How far from its post point a staying, idle vex may hang before it flies back (Part 3 spec §2.2). */
    static final double POST_TOLERANCE = 1.0;

    /** The return flight's speed: the follow goal's coasting speed. */
    private static final double POST_SPEED = 0.7;

    /**
     * The point a staying vex hovers at and its stay area is centered on: the middle of the post block's floor
     * (Part 3 spec glossary). The post is vanilla's bound origin, set by {@link #setStaying}; a vex without one
     * (never the case for a staying vex) answers with its own block.
     */
    Vec3 postPoint() {
        BlockPos post = getBoundOrigin();
        return Vec3.atBottomCenterOf(post != null ? post : blockPosition());
    }

    public boolean inGuardArea(Entity other) {
        if (isStaying()) {
            Vec3 offset = other.position().subtract(postPoint());
            return GuardArea.containsStaying(offset.x, offset.y, offset.z);
        }
        Vec3 offset = other.position().subtract(position());
        return GuardArea.contains(offset.x, offset.y, offset.z);
    }

    AABB guardArea() {
        if (isStaying()) {
            Vec3 post = postPoint();
            return new AABB(post, post)
                    .inflate(GuardArea.STAY_HALF_WIDTH, GuardArea.HALF_HEIGHT, GuardArea.STAY_HALF_WIDTH);
        }
        return new AABB(position(), position())
                .inflate(GuardArea.HALF_WIDTH, GuardArea.HALF_HEIGHT, GuardArea.HALF_WIDTH);
    }
```

In `keepsTarget`, replace the last clause `&& withinReach(target)` with `&& (isStaying() ? inGuardArea(target) : withinReach(target))` and say so in its Javadoc.

(d) Replace the whole `StayWanderGoal` inner class with:

```java
    /**
     * Brings a staying, idle vex back to its post point (Part 3 spec §2.2): one flight per use, started when the vex
     * hangs farther than {@link #POST_TOLERANCE} from it and the move control is free. The tolerance is wider than
     * the distance at which vanilla's move control calls a move finished (about 0.53, docs/api-notes.md §22), so the
     * goal never re-issues a move the control just ended. A vex has no gravity, so once there it hangs still.
     */
    private final class HoldPostGoal extends Goal {
        HoldPostGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return BoundVex.this.isStaying()
                    && BoundVex.this.getTarget() == null
                    && !BoundVex.this.getMoveControl().hasWanted()
                    && BoundVex.this.position().distanceToSqr(BoundVex.this.postPoint())
                            > POST_TOLERANCE * POST_TOLERANCE;
        }

        /** One move per use. */
        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            Vec3 post = BoundVex.this.postPoint();
            BoundVex.this.getMoveControl().setWantedPosition(post.x, post.y, post.z, POST_SPEED);
        }
    }
```

and in `registerGoals`: `goalSelector.addGoal(5, new HoldPostGoal());`. Update the class Javadoc and `setStaying`'s Javadoc ("the post the vex hovers at"). Remove imports Spotless reports unused.

(e) Replace `mobInteract` and its Javadoc:

```java
    /**
     * The summoner's main-hand clicks (Part 3 spec §3). With an empty hand: sneaking toggles stay, with the vex's own
     * sound; not sneaking is the weapon take-back, which Task 5 of the Part 3 plan adds. Only the server decides, since
     * the client does not know the summoner (the mark is not synced). The client answers {@code SUCCESS_SERVER} for
     * every empty-handed main-hand click: a client {@code PASS} would send {@code Minecraft.startUseItem} on to the
     * off hand and throw a pearl or raise a shield on each click (docs/api-notes.md §22). The swing comes from the
     * server; a stranger's click is answered {@code PASS} there and changes nothing.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!(level() instanceof ServerLevel)) {
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!isSummonedBy(player.getUUID())) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            setStaying(!isStaying());
            playSound(SoundEvents.VEX_AMBIENT, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS_SERVER;
    }
```

- [x] **Step 2: Fix the existing tests.** `grep -n "interact(\|FOLLOW_RANGE\|StayWander\|wander" src/main/java/io/github/willrieske/spellcraft/gametest/*.java`. In `BoundVexCapGameTests.stayToggle` (and any other test that toggles by click): the toggling clicks now need `summoner.setShiftKeyDown(true)` first; add assertions that a plain (un-sneaked) empty-hand click by the summoner is consumed (`consumesAction()`) and leaves `isStaying()` unchanged; the stranger's sneak click still passes and changes nothing. Any assertion that the `FOLLOW_RANGE` attribute equals `GuardArea.REACH` becomes `GuardArea.FOLLOW_RANGE`. Update Javadoc that describes the wander ("drifts", "wander leg", `StayWanderGoal`) to the hover (`HoldPostGoal`); `followsAgainAfterStay` and `stayingHoldsItsPost` keep their bodies.

- [x] **Step 3: New GameTests.** Create `BoundVexStayGameTests` (class Javadoc: "Bound Vex GameTests (Evoking II Part 3 spec §2, §8): the post, the hover and the stay area."), using `SpellcraftGameTests.SHARED` as the sibling classes do (copy their header and imports), a `static void registerAll()` registering every test below through `FangGameTests.addPadded(SHARED, "bound_vex_stay_<name>", BoundVexStayGameTests::<method>)`, and these tests. Lay stone under any husk (`helper.setBlock(pos.below(), Blocks.STONE)`): the padded box is air.

The first, in full, as the pattern:

```java
    /**
     * When a staying Bound Vex is moved three blocks off its post, should fly back to within the post tolerance and
     * then hang there: over the next sixty ticks it never leaves the tolerance (no wander).
     *
     * <p>Method under test: {@code BoundVex.HoldPostGoal}, through the goal selector
     */
    static void returnsToItsPostAndHovers(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = followOnly(helper, FEET.above(2), player);
        vex.setStaying(true);
        Vec3 post = Vec3.atBottomCenterOf(vex.getBoundOrigin());
        vex.setPos(post.add(3.0, 0.0, 0.0));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.position().distanceTo(post) <= BoundVex.POST_TOLERANCE,
                        "back at the post, got " + vex.position().distanceTo(post)))
                .thenExecuteFor(
                        60,
                        () -> helper.assertTrue(
                                vex.position().distanceTo(post) <= BoundVex.POST_TOLERANCE + 0.5,
                                "hovers at the post, got " + vex.position().distanceTo(post)))
                .thenSucceed();
    }
```

(`NORTH`, `FEET`, `followOnly` as the follow tests import them; `POST_TOLERANCE + 0.5` allows the coast of one return pass. If the vex settles outside it, lower `POST_SPEED` until it holds and say so in the constant's Javadoc.)

The other five (write each with the same Javadoc shape and Arrange/Act/Assert):

| Method | Arrange | Act | Assert |
|---|---|---|---|
| `attacksAHostileInsideTheArea` | `caster`; `boundVex` (full AI) at `FEET.above(2)`, `setStaying(true)`; a husk on stone 6 blocks east of the post block, at the post's height | wait (budget ≥ 40 ticks) | `vex.getTarget()` is the husk (compare UUIDs) |
| `ignoresAHostileOutsideTheArea` | same, the husk 8 blocks east | `thenExecuteFor(40, …)` | `vex.getTarget() == null` throughout |
| `areaIsMeasuredFromThePostNotTheVex` | `parked` vex, `setStaying(true)`, then `vex.setPos` 5 blocks west of the post; a husk 4 blocks west of the *vex* (9 from the post) and another 6 blocks east of the post (11 from the vex) | none | `!vex.inGuardArea(westHusk)`, `vex.inGuardArea(eastHusk)`; after `setStaying(false)` (which keeps the vex where it is): `vex.inGuardArea(westHusk)`, `!vex.inGuardArea(eastHusk)` |
| `dropsATargetThatLeavesTheArea` | `parked` staying vex with full target goals; a husk 5 blocks east; wait until it is the target | `husk.setPos` to 9 blocks east of the post | within 30 ticks `vex.getTarget() == null` |
| `stayOffDropsAFarTarget` | `parked` staying vex; `vex.setPos` to the area's west edge (6 west of the post); a husk 6 blocks east of the post (12 from the vex); wait until it is the target | `vex.setStaying(false)` | within 30 ticks `vex.getTarget() == null` (beyond reach 7.25) |
| `oldSaveGetsTheNewFollowRange` | `boundVex`; `vex.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(GuardArea.REACH)` | `BoundVex copy = (BoundVex) GameTestSupport.reloaded(helper.getLevel(), vex)` (read `reloaded` first; discard `copy` through `whenDone` if it adds it to the level) | `copy.getAttributeBaseValue(Attributes.FOLLOW_RANGE) == GuardArea.FOLLOW_RANGE` |

That is seven tests with the worked example; register all seven. Add to `BoundVexCapGameTests` one more registered test, `stayToggleKeepsTheOffHandItem`: the summoner holds an ender pearl in the off hand (`player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ENDER_PEARL))`), sneaks, clicks with the empty main hand: the result is `InteractionResult.SUCCESS_SERVER` (a `Success`, which is what stops the client's fall-through), stay toggled, the pearl stack still has count 1. (No GameTest covered the pearl before; a server-side test cannot run the client's fall-through, so the result type is what it pins, and the playtest item covers the rest.)

- [x] **Step 4: Verify.** `spotlessApply`, then `./gradlew build` (recipe), then `runGameTestServer` in the background. Expected: `exit=0`; total = baseline + 8. Run the GameTests a second time: a test that passes once and fails once depends on entity-id parity.

- [x] **Step 5: Commit**

```bash
git add src/main/java/io/github/willrieske/spellcraft/evoking/BoundVex.java src/main/java/io/github/willrieske/spellcraft/gametest
```

```bash
git commit -m "feat(evoking): stay on the sneak click; a staying vex hovers and guards 13x13"
```

> **As built (`222c588`):** diverged — `POST_TOLERANCE` is public (the GameTests are in another package); the stay tests hang the vex at `FEET.above(4)` (the barrier shell at `FEET.above(2)` blocks line of sight) and place husks with no gravity by absolute `setPos` from the post point instead of on stone at structure coordinates (rotated tests put those a block off); the two "drops the target" tests wait with `thenWaitUntil` instead of a 30-tick window; reason: implementer, to make the tests correct under rotation and Error Prone.

---

### Task 4: The stash, drops and saving

**Files:**

- Modify: `src/main/java/io/github/willrieske/spellcraft/evoking/BoundVex.java`
- Modify: `src/main/java/io/github/willrieske/spellcraft/evoking/RepairGolemGoal.java`
- Create: `src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexGearGameTests.java`
- Modify: `src/main/java/io/github/willrieske/spellcraft/gametest/SpellcraftGameTests.java` (+ `BoundVexGearGameTests.registerAll();`)

**Interfaces:**

- Produces (all on `BoundVex`, public for the GameTests): `static final List<EquipmentSlot> GEAR_SLOTS` (`MAINHAND, OFFHAND, HEAD, CHEST, LEGS`); `void conjure(EquipmentSlot slot, ItemStack stack)`; `void dismiss(EquipmentSlot slot)`; `boolean isConjured(EquipmentSlot slot)`; `ItemStack stashed(EquipmentSlot slot)` (the stashed stack or `ItemStack.EMPTY`); package-private `ItemStack stashGift(EquipmentSlot slot, ItemStack gift)` (puts `gift` in the stash of a conjured slot and returns what was stashed, or `EMPTY`), used by Task 5.

- [x] **Step 1: `BoundVex`: the stash.** Add fields and methods (with Javadoc citing Part 3 spec §5.1):

```java
    /** The slots a Bound Vex can hold gear in (Part 3 spec §4.1, §5.1). */
    public static final List<EquipmentSlot> GEAR_SLOTS = List.of(
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS);

    private static final String STASH_TAG_PREFIX = "stash_";
    private static final String CONJURED_TAG_PREFIX = "conjured_";

    /** Given items set aside while a conjured item sits in their slot. */
    private final Map<EquipmentSlot, ItemStack> stash = new EnumMap<>(EquipmentSlot.class);

    /** The slots that hold a conjured item right now: never dropped, never taken. */
    private final Set<EquipmentSlot> conjured = EnumSet.noneOf(EquipmentSlot.class);

    public void conjure(EquipmentSlot slot, ItemStack stack) {
        if (conjured.add(slot)) {
            stash.put(slot, getItemBySlot(slot).copy());
        }
        setItemSlot(slot, stack);
    }

    public void dismiss(EquipmentSlot slot) {
        if (conjured.remove(slot)) {
            ItemStack back = stash.remove(slot);
            setItemSlot(slot, back != null ? back : ItemStack.EMPTY);
        }
    }

    public boolean isConjured(EquipmentSlot slot) {
        return conjured.contains(slot);
    }

    public ItemStack stashed(EquipmentSlot slot) {
        return stash.getOrDefault(slot, ItemStack.EMPTY);
    }

    ItemStack stashGift(EquipmentSlot slot, ItemStack gift) {
        ItemStack before = stash.put(slot, gift);
        return before != null ? before : ItemStack.EMPTY;
    }
```

`stashGift` has no reader until Task 5: to satisfy "every method needs a reader", it is package-private (Error Prone's `UnusedMethod` only flags private members) and Step 5's test `giftDuringAConjureWaitsInTheStash` calls it.

- [x] **Step 2: Saving.** In `addAdditionalSaveData`, for each slot in `GEAR_SLOTS`: if `stashed(slot)` is not empty, `output.store(STASH_TAG_PREFIX + slot.getName(), ItemStack.CODEC, stashed(slot))`; if `isConjured(slot)`, `output.putBoolean(CONJURED_TAG_PREFIX + slot.getName(), true)`. In `readAdditionalSaveData` (after `super`, which has loaded the equipment): clear both collections; for each slot, `input.read(STASH_TAG_PREFIX + slot.getName(), ItemStack.CODEC).ifPresent(stack -> stash.put(slot, stack))`, and if `input.getBooleanOr(CONJURED_TAG_PREFIX + slot.getName(), false)` add the slot to `conjured`; then `GEAR_SLOTS.forEach(this::dismiss)` with the comment "the goal that conjured the item is not running after a load or a cross-dimension copy (Part 3 spec §5.1)". **Confirm** `output.store(String, Codec, T)` and `input.read(String, Codec)` against `Mob.java:388-389, 417` and that an empty `ItemStack` is never passed to `ItemStack.CODEC` (it rejects empty stacks; the "not empty" guard above is why). A conjured slot whose stash was empty has no stash entry: `dismiss` then empties the slot.

- [x] **Step 3: Drops.**

```java
    /**
     * Drops everything the vex was given (Part 3 spec §5.2): conjured items vanish and their stashed items return to
     * their slots, then every gear slot is marked a guaranteed drop, which vanilla drops undamaged whoever the killer
     * was (docs/api-notes.md §22). The marks are set here, not at spawn: vanilla zeroes the main hand's chance at
     * spawn and saves the chances.
     */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        for (EquipmentSlot slot : GEAR_SLOTS) {
            dismiss(slot);
            setGuaranteedDrop(slot);
        }
        super.dropCustomDeathLoot(level, source, killedByPlayer);
    }

    /**
     * On Peaceful, vanilla discards the vex with no death and no drops (docs/api-notes.md §22), so its gear is dropped
     * here first (Part 3 spec §5.2).
     */
    @Override
    public void checkDespawn() {
        if (level() instanceof ServerLevel level
                && level.getDifficulty() == Difficulty.PEACEFUL
                && !getType().isAllowedInPeaceful()) {
            for (EquipmentSlot slot : GEAR_SLOTS) {
                dismiss(slot);
                ItemStack stack = getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    spawnAtLocation(level, stack);
                    setItemSlot(slot, ItemStack.EMPTY);
                }
            }
        }
        super.checkDespawn();
    }
```

- [x] **Step 4: `RepairGolemGoal` onto the stash.** Remove the `held` and `conjured` fields. In `tick`: replace the `if (!conjured) {…}` block with

```java
        if (!vex.isConjured(EquipmentSlot.MAINHAND)) {
            vex.conjure(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT));
        }
```

In `start` drop the `conjured = false;` line. In `stop` replace the `if (conjured) {…}`, `held = …` and `conjured = false` lines with `vex.dismiss(EquipmentSlot.MAINHAND);`. Rewrite the class Javadoc's "The held item lives in this goal until Part 3's stash … (a known gap, on the playtest checklist)" sentence: the given item waits in the vex's stash (`BoundVex.conjure`), which is saved, and a vex loaded mid-repair gets its item back. Run the existing repair GameTests' greps (`grep -n "IRON_INGOT\|getMainHandItem" src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexJobGameTests.java`) and keep them green; a test that pinned the known gap (ingot kept after a reload) must now assert the sword is back.

- [x] **Step 5: GameTests.** Create `BoundVexGearGameTests` (class Javadoc: "Bound Vex GameTests (Evoking II Part 3 spec §3–§5, §8): the stash, drops, saving, giving, taking back, fighting with gear."), `registerAll()` with names `bound_vex_gear_<name>`; these fit the shared box (`SpellcraftGameTests.add`) unless noted. Use `summoner(helper)` and `parked(helper, VEX_SPOT, player)`.

| Method | Arrange → Act → Assert |
|---|---|
| `conjureAndDismissRoundTrip` | Parked vex holds its iron sword. `conjure(MAINHAND, ingot)` → main hand is an ingot, `isConjured`, `stashed(MAINHAND)` is the sword. `conjure(MAINHAND, emerald)` → emerald in hand, stash still the sword. `dismiss(MAINHAND)` → sword back, not conjured, stash empty. A second `dismiss` changes nothing. |
| `giftDuringAConjureWaitsInTheStash` | `conjure(MAINHAND, ingot)`; `ItemStack before = vex.stashGift(MAINHAND, new ItemStack(Items.DIAMOND_SWORD))` → `before` is the iron sword, the hand still holds the ingot; `dismiss` → the diamond sword is in hand. |
| `deathDropsGivenGearAndNoConjuredItem` (padded; needs room for item entities) | Vex with iron sword; `setItemSlot(HEAD, iron helmet)`, `setItemSlot(OFFHAND, shield)`, `conjure(MAINHAND, ingot)`. `vex.kill(helper.getLevel())` (**confirm** the 26.3 kill method name on `LivingEntity`). → within 5 ticks the item entities within 3 blocks hold exactly one iron sword (damage value 0), one helmet, one shield, and no ingot. Discard the item entities through `whenDone`. |
| `saveKeepsTheStashAndDismissesConjured` | `conjure(MAINHAND, ingot)`; `BoundVex copy = (BoundVex) GameTestSupport.reloaded(level, vex)` → the copy's main hand is the iron sword, nothing conjured, stash empty. |
| `peacefulRemovalDropsGear` (padded, registered in the `"peaceful"` environment exactly as `VexGameTests.peacefulNotice` is: `FangGameTests.addPadded("peaceful", …)`; read that test and copy how it sets `server.setDifficulty(Difficulty.PEACEFUL, true)` and restores the old difficulty through `whenDone`) | Vex with sword and helmet on the starting difficulty; set Peaceful → within 40 ticks the vex is removed and a sword and a helmet item entity lie near where it was. |

Delta: 5 tests.

- [x] **Step 6: Verify** (`spotlessApply`, `build`, `runGameTestServer`; total = previous + 5) and **commit**:

```bash
git add src/main/java/io/github/willrieske/spellcraft/evoking src/main/java/io/github/willrieske/spellcraft/gametest
```

```bash
git commit -m "feat(evoking): a gear stash for Bound Vexes; given gear drops on death and on Peaceful"
```

> **As built (`53e2f9f`):** diverged — `stashGift` is public (the GameTests are in another package); the Peaceful drop test runs in its own environment `peaceful_gear` (two tests in one `peaceful` batch run side by side and the difficulty change broke the other); reason: implementer, both needed to compile and pass.

---

### Task 5: Gear kinds, giving and taking back

**Files:**

- Create: `src/main/java/io/github/willrieske/spellcraft/evoking/VexGear.java`
- Modify: `src/main/java/io/github/willrieske/spellcraft/evoking/BoundVex.java` (`mobInteract`)
- Modify: `src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexGearGameTests.java`

**Interfaces:**

- Consumes: `BoundVex.isConjured`, `stashGift`, `GEAR_SLOTS` (Task 4).
- Produces: `VexGear.Kind` (`ARMOR, SHIELD, LAUNCHER, THROWABLE, ARROW, MELEE, NONE`); `static Kind VexGear.kindOf(ItemStack)`; `static Optional<EquipmentSlot> VexGear.slotFor(ItemStack)`; `static boolean VexGear.isSupport(ItemStack)`; `static boolean VexGear.isRanged(ItemStack)` (all public: the GameTests are in another package).

- [ ] **Step 1: `VexGear`.** A `public final class` with a private constructor. Class Javadoc: what a held item is to a Bound Vex (Part 3 spec §4.1), and that it reads only the stack's item and components, so the client and the server classify alike (§3). Order of the tests is the spec's table:

```java
    public static Kind kindOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return Kind.NONE;
        }
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null) {
            EquipmentSlot slot = equippable.slot();
            if (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS) {
                return Kind.ARMOR;
            }
        }
        if (stack.has(DataComponents.BLOCKS_ATTACKS)) {
            return Kind.SHIELD;
        }
        if (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem) {
            return Kind.LAUNCHER;
        }
        if (stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)) {
            return Kind.THROWABLE;
        }
        if (stack.getItem() instanceof ArrowItem) {
            return Kind.ARROW;
        }
        if (equippable != null) {
            return Kind.NONE; // boots, body armor, saddles: wearable, but not by a vex
        }
        return hasMainHandAttackDamage(stack) ? Kind.MELEE : Kind.NONE;
    }

    public static Optional<EquipmentSlot> slotFor(ItemStack stack) {
        return switch (kindOf(stack)) {
            case ARMOR -> Optional.of(stack.get(DataComponents.EQUIPPABLE).slot());
            case SHIELD -> Optional.of(EquipmentSlot.OFFHAND);
            case LAUNCHER, THROWABLE, ARROW, MELEE -> Optional.of(EquipmentSlot.MAINHAND);
            case NONE -> Optional.empty();
        };
    }

    /** Throwable or arrow with at least one effect, all of them beneficial (Part 3 spec §4.3). */
    public static boolean isSupport(ItemStack stack) {
        Kind kind = kindOf(stack);
        if (kind != Kind.THROWABLE && kind != Kind.ARROW) {
            return false;
        }
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return false;
        }
        boolean any = false;
        for (MobEffectInstance effect : contents.getAllEffects()) {
            if (effect.getEffect().value().getCategory() != MobEffectCategory.BENEFICIAL) {
                return false;
            }
            any = true;
        }
        return any;
    }

    /** A launcher, or a throwable or arrow that is not support gear (Part 3 spec §4.3). */
    public static boolean isRanged(ItemStack stack) {
        Kind kind = kindOf(stack);
        return kind == Kind.LAUNCHER || ((kind == Kind.THROWABLE || kind == Kind.ARROW) && !isSupport(stack));
    }
```

`hasMainHandAttackDamage` (private): true when `DataComponents.ATTRIBUTE_MODIFIERS` holds an entry for `Attributes.ATTACK_DAMAGE` whose slot group includes `EquipmentSlot.MAINHAND`. **Confirm** the entry accessors with what Task 1 recorded in api-notes §22 (the `ItemAttributeModifiers` entry type and its slot test) and write it from that. `slotFor`'s `ARMOR` arm dereferences a nullable `get`: bind the component to a local with a null check (or return through `Optional.ofNullable(...).map(Equippable::slot)`) so NullAway/Error Prone is satisfied. Note the shield is tested before `EQUIPPABLE`-is-other because a vanilla shield carries `EQUIPPABLE` for `OFFHAND`; the order above handles it (armor slots first, then shield, and only then "other equippable → NONE").

- [ ] **Step 2: `BoundVex.mobInteract`, complete.** Replace the method (keep Task 3's Javadoc, rewriting its second sentence to the full click table and citing §3, §4.2, §4.4):

```java
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getMainHandItem();
        Optional<EquipmentSlot> slot = VexGear.slotFor(held);
        if (!held.isEmpty() && slot.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!(level() instanceof ServerLevel)) {
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!isSummonedBy(player.getUUID())) {
            return InteractionResult.PASS;
        }
        if (slot.isPresent()) {
            give(player, held, slot.get());
        } else if (player.isShiftKeyDown()) {
            setStaying(!isStaying());
            playSound(SoundEvents.VEX_AMBIENT, 1.0F, 1.0F);
        } else {
            takeBackWeapon(player);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * Takes one item from the summoner's hand into {@code slot} and hands back what was there (Part 3 spec §4.2). While
     * the slot holds a conjured item the gift waits in the stash and the previously stashed item comes back instead.
     */
    private void give(Player player, ItemStack held, EquipmentSlot slot) {
        boolean armor = VexGear.kindOf(held) == VexGear.Kind.ARMOR;
        ItemStack gift = held.consumeAndReturn(1, player);
        ItemStack back;
        if (isConjured(slot)) {
            back = stashGift(slot, gift);
        } else {
            back = getItemBySlot(slot);
            setItemSlot(slot, gift);
        }
        if (!back.isEmpty() && !player.getInventory().add(back)) {
            player.drop(back, false);
        }
        playSound(armor ? SoundEvents.ARMOR_EQUIP_GENERIC.value() : SoundEvents.VEX_AMBIENT, 1.0F, 1.0F);
    }

    /** Hands the main-hand item to the summoner's empty hand; a conjured item is not handed over (spec §4.4). */
    private void takeBackWeapon(Player player) {
        ItemStack weapon = getMainHandItem();
        if (weapon.isEmpty() || isConjured(EquipmentSlot.MAINHAND)) {
            return;
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        playSound(SoundEvents.VEX_AMBIENT, 1.0F, 1.0F);
    }
```

**Confirm:** `consumeAndReturn` leaves a creative player's stack alone (api-notes §22, Task 1); if it does not, guard with `player.hasInfiniteMaterials() ? held.copyWithCount(1) : held.consumeAndReturn(1, player)`. `SoundEvents.ARMOR_EQUIP_GENERIC` is a `Holder<SoundEvent>` in recent versions (hence `.value()`); check its declared type. `Mob.setItemSlot` may play the item's own equip sound for armor; if it does, drop the explicit armor sound so it is not doubled. `player.drop(ItemStack, boolean)` exists on `Player`.

- [ ] **Step 3: GameTests** (add to `BoundVexGearGameTests`, shared box, `summoner` + `parked`; each click is `GameTestSupport.hold(player, stack)` then `vex.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO)`):

| Method | Arrange → Act → Assert |
|---|---|
| `kindsAndSlots` | No world needed beyond the helper: assert `VexGear.kindOf`/`slotFor` for iron helmet (ARMOR/HEAD), diamond chestplate (CHEST), leggings (LEGS), boots (NONE/empty), shield (SHIELD/OFFHAND), bow and crossbow (LAUNCHER/MAINHAND), splash potion (THROWABLE), arrow and tipped arrow (ARROW), diamond sword, iron axe, trident, mace (MELEE), stick and `ItemStack.EMPTY` (NONE). `isSupport`: splash potion of healing true, of harming false, of water false, tipped arrow of regeneration true, plain arrow false. `isRanged`: bow true, harming potion true, water potion true, plain arrow true, healing potion false, sword false. Build potions with `PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING)` (**confirm** the factory's name). |
| `givingArmorSwapsThePiece` | Give an iron helmet → result consumes the action, the vex wears it, the hand is empty. Give a diamond helmet → the vex wears diamond, the player's inventory holds the iron helmet. |
| `givingAWeaponReturnsTheSword` | Give a diamond sword → vex main hand diamond sword; the inventory holds one iron sword. |
| `givingAShieldUsesTheOffhand` | Give a shield → offhand shield, main hand still the iron sword. |
| `givingTakesOneFromAStack` | Hold 5 splash potions of harming; give → the hand holds 4, the vex holds 1, and the returned iron sword is in the inventory. |
| `creativeKeepsItsStack` | A creative summoner (`MockPlayers.inLevel(helper, GameType.CREATIVE)` marked as the vex's summoner) holds 1 diamond sword; give → the hand still holds it, and the vex holds one too. |
| `nonGearPasses` | Holding a stick, then boots: `interact(...).consumesAction()` is false, nothing changes. Off-hand call with a helmet in the off hand and an empty main hand: `vex.interact(player, InteractionHand.OFF_HAND, Vec3.ZERO)` does not consume and the vex wears nothing. |
| `plainEmptyClickTakesTheWeaponOnly` | Vex with sword, helmet (set directly) and shield. Empty hand, not sneaking, click → the player's main hand is the iron sword; the vex's main hand is empty; helmet and shield still worn. A second click (hand now full of sword → that is a give; so first `hold(player, ItemStack.EMPTY)`) → consumes, nothing changes, `isStaying()` false. |
| `conjuredItemIsNotTaken` | `conjure(MAINHAND, ingot)`; plain empty click → hand still empty, ingot still held. Give a diamond sword → ingot still held, `stashed(MAINHAND)` is the diamond sword, the inventory holds the iron sword. |
| `strangerCannotGiveOrTake` | A second mock player holding a helmet clicks → not consumed, the helmet still in their hand; empty-handed plain click → not consumed, the vex keeps its sword. |
| `sneakWithGearStillGives` | Sneaking summoner holding a helmet clicks → the helmet is worn, `isStaying()` false. |

Delta: 11 tests.

- [ ] **Step 4: Verify** (`spotlessApply`, `build`, `runGameTestServer`; total = previous + 11) and **commit**:

```bash
git add src/main/java/io/github/willrieske/spellcraft/evoking src/main/java/io/github/willrieske/spellcraft/gametest
```

```bash
git commit -m "feat(evoking): give a Bound Vex armor, weapons and shields; take the weapon back"
```

---

### Task 6: Fighting with gear: ranged, support, shield, nothing used up

**Files:**

- Create: `src/main/java/io/github/willrieske/spellcraft/evoking/VexShots.java`, `RangedGearGoal.java`, `SupportGearGoal.java`
- Modify: `src/main/java/io/github/willrieske/spellcraft/evoking/BoundVex.java`
- Modify: `src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexGearGameTests.java`

**Interfaces:**

- Consumes: `VexGear.kindOf`, `isRanged`, `isSupport` (Task 5); `BoundVex.summoner()`, `nearby(Class, double, Predicate)`, `isSummonedBy(UUID)`.
- Produces: `static void VexShots.launch(BoundVex vex, LivingEntity at)` (shoots or throws a copy of the main-hand item at `at`; the held stack is untouched); `RangedGearGoal.MIN_DISTANCE` (6.0), `MAX_DISTANCE` (10.0), `SHOT_INTERVAL` (40); `SupportGearGoal.RANGE` (10.0), `INTERVAL` (40). Constants are `static final`, package-private or public as the tests need.

- [ ] **Step 1: `VexShots.launch`.** Package-private final class. **Read `AbstractSkeleton.performRangedAttack` and `Witch.performRangedAttack` in `build/mc-src` first** and build the launch exactly as they do (the spawn helper, the aim arithmetic); the logic:

```java
    static void launch(BoundVex vex, LivingEntity at) {
        if (!(vex.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack held = vex.getMainHandItem().copy();
        double dx = at.getX() - vex.getX();
        double dz = at.getZ() - vex.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        switch (VexGear.kindOf(held)) {
            case LAUNCHER -> {
                AbstractArrow arrow = ProjectileUtil.getMobArrow(vex, new ItemStack(Items.ARROW), 1.0F, held);
                shootArrow(level, vex, arrow, at, dx, dz, flat);
                vex.playSound(
                        held.getItem() instanceof CrossbowItem ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.ARROW_SHOOT,
                        1.0F,
                        1.0F);
            }
            case ARROW -> {
                AbstractArrow arrow = ProjectileUtil.getMobArrow(vex, held, 1.0F, null);
                shootArrow(level, vex, arrow, at, dx, dz, flat);
                vex.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F);
            }
            case THROWABLE -> {
                // Aim and spawn as Witch.performRangedAttack does, with a copy of the held stack:
                // ThrownSplashPotion(level, vex, held) or ThrownLingeringPotion(level, vex, held) by the item.
                throwPotion(level, vex, held, at, dx, dz, flat);
            }
            default -> {
                // not a launchable item: nothing happens
            }
        }
    }
```

`shootArrow`: as the skeleton: `dy = at.getY(1.0 / 3.0) - arrow.getY()`, `arrow.pickup = AbstractArrow.Pickup.DISALLOWED`, then the skeleton's spawn-and-shoot call with velocity 1.6 and inaccuracy 2 (a vex is a fair shot; the skeleton's is `14 - difficulty * 4`). `throwPotion`: as the witch: aim at the target's eye height minus 1.1, velocity 0.75, inaccuracy 8, the item's throw sound (`SPLASH_POTION_THROW` / `LINGERING_POTION_THROW`). `ProjectileUtil.getMobArrow`'s `firedFromWeapon` may be required non-null for enchantment hooks in the bow path and is nullable for the arrow path (`ProjectileUtil.java:273`).

- [ ] **Step 2: `RangedGearGoal`.** Package-private final class, flag `MOVE`, `requiresUpdateEveryTick` true. Javadoc: Part 3 spec §4.3 "Ranged".

```java
    @Override
    public boolean canUse() {
        LivingEntity target = vex.getTarget();
        return target != null && target.isAlive() && VexGear.isRanged(vex.getMainHandItem());
    }

    @Override
    public void start() {
        cooldown = SHOT_INTERVAL / 2;
    }

    @Override
    public void tick() {
        LivingEntity target = vex.getTarget();
        if (target == null) {
            return;
        }
        double distance = vex.distanceTo(target);
        Vec3 eye = target.getEyePosition();
        vex.getLookControl().setLookAt(eye.x, eye.y, eye.z, 30.0F, 30.0F);
        if (distance < MIN_DISTANCE || distance > MAX_DISTANCE) {
            // A point on the line through the target and the vex, in the middle of the band, at the target's eye height.
            Vec3 away = vex.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
            Vec3 direction = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
            Vec3 spot = target.position().add(direction.scale((MIN_DISTANCE + MAX_DISTANCE) / 2.0));
            vex.getMoveControl().setWantedPosition(spot.x, eye.y, spot.z, 1.0);
        }
        if (cooldown > 0) {
            cooldown--;
        } else if (distance <= MAX_DISTANCE && vex.hasLineOfSight(target)) {
            VexShots.launch(vex, target);
            cooldown = SHOT_INTERVAL;
        }
    }
```

`canContinueToUse` is `canUse()`. Register in `BoundVex.registerGoals` at priority 3, before the `ChargeGoal` line. In `ChargeGoal.canUse` add as the first conjunct `!VexGear.isRanged(BoundVex.this.getMainHandItem()) && !VexGear.isSupport(BoundVex.this.getMainHandItem())` and say why in its Javadoc (the main hand picks the fighting style, spec §4.3). A staying ranged vex may back out of its stay area while holding the band; it returns through `HoldPostGoal` when the target is gone (spec §2.2).

- [ ] **Step 3: `SupportGearGoal`.** Package-private final class, **no flags** (`setFlags(EnumSet.noneOf(Goal.Flag.class))`), so follow and hold-post keep running beside it (spec §4.3). `requiresUpdateEveryTick` true.

```java
    @Override
    public boolean canUse() {
        return VexGear.isSupport(vex.getMainHandItem()) && vex.summoner().isPresent();
    }

    @Override
    public void tick() {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        ServerPlayer owner = vex.summoner().orElse(null);
        if (owner == null) {
            return;
        }
        vex.nearby(LivingEntity.class, RANGE, friend -> isHurtFriend(owner, friend)).stream()
                .min(Comparator.comparingDouble(vex::distanceToSqr))
                .ifPresent(friend -> {
                    VexShots.launch(vex, friend);
                    cooldown = INTERVAL;
                });
    }

    /** The summoner, an ally or another Bound Vex of the summoner, alive, in sight and below half health. */
    private boolean isHurtFriend(ServerPlayer owner, LivingEntity friend) {
        return !friend.getUUID().equals(vex.getUUID())
                && friend.isAlive()
                && friend.getHealth() < friend.getMaxHealth() / 2.0F
                && (friend.getUUID().equals(owner.getUUID()) || Allies.isAllyOf(owner, friend))
                && vex.hasLineOfSight(friend);
    }
```

(`Allies.isAllyOf` already counts the summoner's own vexes: its Javadoc and `BoundVex.alliesInGuardArea`'s comment say a vex is its summoner's ally. Read `Allies.isAllyOf` to confirm; if it does not, add `|| (friend instanceof BoundVex other && other.isSummonedBy(owner.getUUID()))`.) Register at priority 3 after the charge goal. `nearby` is package-private on `BoundVex`: the goal is in the same package.

- [ ] **Step 4: Shield and wear on `BoundVex`.**

```java
    /**
     * A shield in the offhand blocks from the front (Part 3 spec §4.3). Vanilla's own blocking needs the entity to be
     * using the shield, which a mob never is, so this repeats its geometry (docs/api-notes.md §22): no block without
     * a source position (fire, a fall), for a shield-bypassing damage type or a piercing arrow; otherwise the flat
     * view vector from the head's yaw against the flat direction to the source. No durability is spent.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (blocksWithShield(source)) {
            playSound(SoundEvents.SHIELD_BLOCK.value(), 1.0F, 1.0F);
            return false;
        }
        return super.hurtServer(level, source, damage);
    }

    private boolean blocksWithShield(DamageSource source) {
        Vec3 from = source.getSourcePosition();
        if (from == null
                || VexGear.kindOf(getOffhandItem()) != VexGear.Kind.SHIELD
                || source.is(DamageTypeTags.BYPASSES_SHIELD)
                || (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getPierceLevel() > 0)) {
            return false;
        }
        Vec3 toSource = from.subtract(position()).multiply(1.0, 0.0, 1.0);
        if (toSource.lengthSqr() < 1.0E-8) {
            return true;
        }
        return calculateViewVector(0.0F, getYHeadRot()).dot(toSource.normalize()) > 0.0;
    }

    /** The weapon is never worn down (Part 3 spec §4.5): the hit runs on the stack and a copy is put back after. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        ItemStack before = getMainHandItem().copy();
        boolean hurt = super.doHurtTarget(level, target);
        if (!ItemStack.matches(before, getMainHandItem())) {
            setItemSlot(EquipmentSlot.MAINHAND, before);
        }
        return hurt;
    }
```

**Confirm:** `SoundEvents.SHIELD_BLOCK`'s declared type (`Holder<SoundEvent>` or `SoundEvent`); `calculateViewVector` is accessible from a subclass (`Entity`, protected or public; vanilla's `applyItemBlocking` calls it unqualified); `ItemStack.matches(ItemStack, ItemStack)` exists. The `Vex` class may not expose `hurtServer` as public-overridable differently: match the super signature at `LivingEntity.java:1223`.

- [ ] **Step 5: GameTests** (add to `BoundVexGearGameTests`; padded unless noted; husks on stone, `setNoAi(true)`; set gear directly with `setItemSlot`, the giving is Task 5's subject):

| Method | Arrange → Act → Assert |
|---|---|
| `aBetterSwordHitsHarder` (shared) | Two husks with equal health. `vex.doHurtTarget(level, huskA)` with the iron sword; `setItemSlot(MAINHAND, diamond sword)`; `vex.doHurtTarget(level, huskB)` → huskB lost more health than huskA. |
| `theSwordIsNotWornDown` (shared) | Diamond sword, damage value 0; `doHurtTarget` on a husk five times (reset `husk.invulnerableTime = 0` between hits) → the held sword's damage value is still 0 and its count 1. |
| `aBowVexShootsAndKeepsItsDistance` | `boundVex` (full AI) with a bow; a husk 3 blocks away → within 100 ticks an `AbstractArrow` owned by the vex exists within 16 blocks, and at that moment (and for the next 20 ticks) `vex.distanceTo(husk) >= RangedGearGoal.MIN_DISTANCE - 1.5`; the vex never `isCharging()`; the bow's damage value is 0. Discard arrows through `whenDone`. |
| `aHarmingPotionVexThrowsAtItsTarget` | Vex holding 1 splash potion of harming; a husk 7 blocks away → within 100 ticks a `ThrownSplashPotion` exists near; the vex still holds exactly 1 potion. (Harming heals undead; the test asserts the throw, not the damage.) |
| `aHealingPotionVexThrowsAtAHurtAllyOnly` | Vex holding a splash potion of healing; an iron golem on stone (an ally by `Allies.isAllyOf`) at 20 of 100 health, 5 blocks away, and a husk 5 blocks away the other side → within 100 ticks a thrown potion exists, the golem's health rose, the vex never charged, and the vex still holds 1 potion. Second phase or second test `aHealingPotionVexIgnoresAHealthyAlly`: the golem at full health → no `ThrownSplashPotion` appears in 100 ticks. |
| `aSupportVexStillFollows` | `followOnly` vex holding a healing potion, 8 blocks from the summoner, nobody hurt → within 100 ticks `vex.distanceTo(player) <= FollowSummonerGoal.ORBIT_MAX + 1.0`. |
| `aShieldBlocksFromTheFront` (shared) | Parked vex with a shield; `vex.setYHeadRot(0)` (facing south, +z). `level.damageSources().mobAttack(husk)` with the husk 2 blocks south → `vex.hurtServer(...)` is false and health unchanged. Husk moved 2 blocks north → true, health dropped. `level.damageSources().onFire()` → true (no source position). A piercing arrow from the south → true. `AbstractArrow.setPierceLevel` is private (`AbstractArrow.java:682`), so make the arrow pierce through its save: write it with the `TagValueOutput` pattern of `GameTestSupport.saved`, `putByte("PierceLevel", (byte) 1)` on the tag, and read the tag back into the arrow with `arrow.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag))` (the pattern in `GameTestSupport.reloaded`); assert `arrow.getPierceLevel() == 1` as setup, then hurt the vex with `level.damageSources().arrow(arrow, husk)`. A plain arrow from the south in the same test → false (blocked). |
| `withoutAShieldNothingIsBlocked` (shared) | The same front hit with an empty offhand → true. |

Delta: 9 tests (count the two healing tests separately).

- [ ] **Step 6: Verify** (`spotlessApply`, `build`, `runGameTestServer` twice; total = previous + 9) and **commit**:

```bash
git add src/main/java/io/github/willrieske/spellcraft/evoking src/main/java/io/github/willrieske/spellcraft/gametest
```

```bash
git commit -m "feat(evoking): Bound Vexes shoot, throw, heal and block with the gear they hold"
```

---

### Task 7: Seeing the armor (client)

**Files:**

- Create: `src/main/java/io/github/willrieske/spellcraft/client/evoking/BoundVexRenderState.java`, `BoundVexArmorLayer.java`, `BoundVexRenderer.java`
- Modify: `src/main/java/io/github/willrieske/spellcraft/client/evoking/BoundVexRenderers.java`

**Interfaces:**

- Consumes: `BoundVex` (reads `getItemBySlot`); vanilla as recorded in api-notes §22 "Client".
- Produces: nothing other tasks read.

- [ ] **Step 1: Read first.** `build/mc-src/net/minecraft/client/renderer/entity/VexRenderer.java`, `layers/HumanoidArmorLayer.java`, `layers/EquipmentLayerRenderer.java`, `ArmorModelSet.java`, `model/monster/vex/VexModel.java` (the part names: root children `body` and `head`; `body` has the arms and wings), `ZombieRenderer.java` (how an armor set is baked: `ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR, context.getModelSet(), ZombieModel::new)`), and `model/HumanoidModel.java` (its constructor and whether a plain `HumanoidModel<HumanoidRenderState>` can be built from an armor layer's `ModelPart`).

- [ ] **Step 2: Write the three classes.**

`BoundVexRenderState extends VexRenderState`: three public `ItemStack` fields `headArmor`, `chestArmor`, `legsArmor`, all `ItemStack.EMPTY` by default. Javadoc: the offhand and main-hand items already ride `ArmedEntityRenderState`.

`BoundVexRenderer extends VexRenderer`: constructor `super(context); addLayer(new BoundVexArmorLayer(this, context));`; `createRenderState()` returns a `BoundVexRenderState`; `extractRenderState(Vex entity, VexRenderState state, float partialTicks)` calls `super` and, when `state instanceof BoundVexRenderState gear`, copies `entity.getItemBySlot(HEAD/CHEST/LEGS)` (use `.copy()` as vanilla's humanoid extraction does, **confirm** in `HumanoidMobRenderer.extractHumanoidRenderState`). If `VexRenderer`'s generics do not let a subclass add a layer typed on `VexRenderState`/`VexModel`, type the layer on those exact types and downcast the state inside the layer.

`BoundVexArmorLayer extends RenderLayer<VexRenderState, VexModel>`: holds `ArmorModelSet<HumanoidModel<HumanoidRenderState>>` baked from `ModelLayers.ZOMBIE_ARMOR` with `HumanoidModel::new` (or the factory vanilla uses for a plain humanoid armor model), the `EquipmentLayerRenderer` from `context.getEquipmentRenderer()`, and one reusable neutral `HumanoidRenderState`. In `submit(poseStack, collector, light, state, yRot, xRot)`: for each of HEAD, CHEST, LEGS whose stack has `EQUIPPABLE` with an asset id for that slot (`HumanoidArmorLayer.shouldRender(stack, slot)` is public static):

```java
        poseStack.pushPose();
        getParentModel().root().translateAndRotate(poseStack);
        ModelPart anchor = getParentModel().root().getChild(slot == EquipmentSlot.HEAD ? "head" : "body");
        anchor.translateAndRotate(poseStack);
        poseStack.translate(0.0F, OFFSET_Y[slot], 0.0F);
        poseStack.scale(SCALE, SCALE, SCALE);
        equipmentRenderer.renderLayers(
                slot == EquipmentSlot.LEGS
                        ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS
                        : EquipmentClientInfo.LayerType.HUMANOID,
                equippable.assetId().orElseThrow(),
                armorModels.get(slot),
                neutralState,
                stack,
                poseStack,
                collector,
                light,
                state.outlineColor);
        poseStack.popPose();
```

Constants (first guess, named and Javadoc'd as "Will's eye decides, playtest checklist Part 3"): `SCALE = 0.55F` (the scale `VexModel.translateToHand` uses for held items); per-slot Y offsets `HEAD 0.0F`, `CHEST 0.0F`, `LEGS 0.0F` as three named `private static final float` constants (not an array). The armor model's own parts are positioned for a humanoid whose head pivot is at the origin: for `HEAD`, anchoring at the vex's head part puts the helmet on the head; for `CHEST` and `LEGS`, the body anchor puts the chestplate at the body's top. Hide the parts a slot does not use if the baked model shows them (vanilla's armor models per slot already contain only that slot's parts in 26.3: **confirm** in `ArmorModelSet`/`HumanoidModel.createArmorMeshSet`). Call `armorModels.get(slot).setupAnim(neutralState)` once before rendering if the model needs it to reset its pose.

`BoundVexRenderers.onRegisterRenderers`: `event.registerEntityRenderer(ModEntities.BOUND_VEX.get(), BoundVexRenderer::new);` and update the class Javadoc ("with the armor layer (Part 3 spec §6)").

Everything is under `client.evoking` (ArchUnit rule 1). `@OnlyIn` is not used in this mod's client classes; follow `BoundVexRenderers`.

- [ ] **Step 3: Verify.** `spotlessApply`; `./gradlew build` (recipe); `./gradlew runData` (recipe): `exit=0`, which loads the client classes and constructs the renderers (if `runData` does not bake entity renderers, say so in the task report: then the layer's construction is proven only by Will's playtest; do not claim otherwise). `runGameTestServer` is unchanged by this task but run it once: the dedicated server must still start (no client class leaked into common code).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/github/willrieske/spellcraft/client/evoking
```

```bash
git commit -m "feat(evoking): draw a Bound Vex's helmet, chestplate and leggings"
```

---

### Task 8: Docs, playtest items, mutation testing

**Files:**

- Modify: `README.md`, `docs/playtest-checklist.md`, `docs/mutation-testing.md`, `src/main/templates/META-INF/neoforge.mods.toml` (only if its description mentions vex clicks)

- [ ] **Step 1: README.** In the Evoking paragraph (the sentences from "Click a vex with an empty hand to make it stay" on), replace the stay sentence and add gear, in the README's plain second-person voice:

```markdown
  Sneak and right-click a vex with an empty hand to make it stay: it hovers on that spot and guards a
  13 × 13 block area around it; do it again and it follows you. Right-click a vex while holding a
  helmet, chestplate, leggings, a weapon, a shield, a bow or crossbow, a splash or lingering potion or
  an arrow and it takes one and hands back what it had there. You can see the armor on it. A vex with a
  bow, a harmful potion or arrows shoots from a distance; a vex with a healing potion throws it at you
  and your allies when you are badly hurt; a shield blocks hits from the front. Nothing a vex holds is
  ever used up. Right-click with an empty hand (not sneaking) to take its weapon back; everything you
  gave drops when it dies.
```

- [ ] **Step 2: Playtest checklist.** In "Evoking II (Part 2)", rewrite the three click items (the lines beginning "Right-click a vex with an empty hand (not sneaking)", "Hold an ender pearl", and the portal line's "(a click still toggles stay)") for the sneak click, the hover ("it hangs still on that spot; it does not drift") and remove the repair item's known-gap note about the ingot if present. Then add after that section:

```markdown
## Evoking II (Part 3)

- [ ] Sneak + right-click a vex with an empty hand: its sound, your arm swings, it stops and hovers on the spot. Walk 20 blocks away: it has not moved. A zombie that walks within 6 blocks of the spot is attacked, and the vex flies back to the spot afterwards; a zombie 8 blocks away is left alone. Sneak + right-click again: it follows.
- [ ] Is it clear enough which vexes are staying? (There is no marker beyond the hover.)
- [ ] Right-click (not sneaking) with an empty hand: you now hold the vex's iron sword and the vex fights bare-handed. Right-click it holding the sword: it has it back.
- [ ] Give a helmet, a chestplate and leggings (iron, diamond, a dyed leather piece, a trimmed piece): each shows on the vex, sits where it should and is the right size; giving a second helmet returns the first to you. Boots: nothing happens.
- [ ] Give a diamond sword: the iron sword comes back, and the vex kills a zombie faster. Give an axe, a mace, a trident: held and used in melee.
- [ ] Give a shield: it shows in the left hand, the sword stays in the right. Let a skeleton shoot at the vex from the front: arrows are blocked with the shield sound.
- [ ] Give a bow, then a crossbow: the vex backs off to about 6 to 10 blocks and shoots an arrow every two seconds. The arrows cannot be picked up.
- [ ] Give a splash potion of harming (from a stack of several: only one leaves your hand): the vex throws one at its target every two seconds and still holds one.
- [ ] Give a splash potion of healing, then get below half health near the vex: it throws healing at you. It does not attack while it holds the healing potion.
- [ ] Give a vex full armor, a shield and a sword, then `/kill` it: everything drops, undamaged. Switch to Peaceful with a geared vex out: its gear drops where it was.
- [ ] While a vex repairs a golem (ingot in hand), give it a sword: the ingot stays until the repair ends, then the new sword is in its hand and the old one came back to you.
- [ ] A friend (LAN) right-clicks your vex with a sword or an empty hand: nothing happens.
- [ ] Hold an ender pearl in your off hand, empty main hand, and click your vex (sneaking and not): no pearl is thrown.
```

- [ ] **Step 3: Mutation testing.** `./gradlew pitest --console=plain > build/pitest.log 2>&1; echo "exit=$?"; grep -n "Generated\|mutations\|Line Coverage\|Mutation score\|killed" build/pitest.log | tail -10`. Add a row to the first table of `docs/mutation-testing.md` in the existing rows' form: `| 2026-11-09 (Evoking II Part 3) | <score> (<killed>/<total>; from 99%, 264/268, on 2026-11-08) | <line coverage> | <what survived in GuardArea.containsStaying, or "None new in spell.rules"> |`. If a `containsStaying` mutant survives, add the test row that kills it (a boundary value) and re-run.

- [ ] **Step 4: Verify and commit.** `./gradlew build` (recipe), then:

```bash
git add README.md docs src/main/templates
```

```bash
git commit -m "docs: describe Bound Vex gear and the new stay; playtest items; mutation run"
```

---

## Self-review

- **Spec coverage:** §2.1 gesture → Task 3 (e), Task 5; §2.2 hover → Task 3 (d); §2.3 area and follow range → Tasks 2, 3 (a–c); §3 click table and the two sides → Tasks 3, 5; §4.1 kinds → Task 5; §4.2 giving → Task 5; §4.3 styles, shield → Task 6; §4.4 take-back → Task 5; §4.5 wear → Task 6 (weapon), api-notes (armor); §5.1 stash and repair → Task 4; §5.2 drops → Task 4; §5.3 saving → Task 4; §6 → Task 7; §7 error cases → tests in Tasks 3–5 (stranger, full inventory falls to vanilla's `drop`, gift during repair, old saves); §8 docs → Tasks 1, 8. Version and tag: after the branch review, outside this plan.
- **Names:** `conjure`/`dismiss`/`isConjured`/`stashed`/`stashGift`, `GEAR_SLOTS`, `postPoint`, `POST_TOLERANCE`, `VexGear.kindOf`/`slotFor`/`isSupport`/`isRanged`, `VexShots.launch`, `RangedGearGoal.MIN_DISTANCE`/`MAX_DISTANCE`/`SHOT_INTERVAL`, `SupportGearGoal.RANGE`/`INTERVAL` are used with the same spelling in every task.

## Divergences from the spec

1. The full-inventory case (§7) has no GameTest: it is one vanilla call (`Inventory.add` false → `Player.drop`).
2. The client branch of `mobInteract` (the `SUCCESS_SERVER`-or-`PASS` answer from the hand alone, §3) runs only on a client level, which no GameTest has; `VexGear.slotFor`, which decides it, is tested (`kindsAndSlots`), and the playtest's off-hand pearl item covers the effect.
3. `runData` may not construct entity renderers; if it does not, the armor layer's wiring is proven only in the playtest (Task 7 Step 3 reports which).

## Divergence index

<!-- generated by plan_map.py --write-index; do not edit -->

| Task | SHA | Divergence |
|---|---|---|
| 1 | `8854374` | the sources corrected the plan in four places later tasks use: `Player.drop` takes three arguments (`drop(ItemStack, boolean thrownFromHand, Prediction)`); `SoundEvents.ARMOR_EQUIP_GENERIC` and `SHIELD_BLOCK` are holders (`.value()`); `LivingEntity.onEquipItem` already plays the equip sound for armor and an offhand shield set with `setItemSlot`, so Task 5 plays no armor sound of its own; mobs launch projectiles with `Projectile.spawnProjectileUsingShoot` (signatures in api-notes §22); reason: api-notes §22 records what `build/mc-src` says. |
| 3 | `222c588` | `POST_TOLERANCE` is public (the GameTests are in another package); the stay tests hang the vex at `FEET.above(4)` (the barrier shell at `FEET.above(2)` blocks line of sight) and place husks with no gravity by absolute `setPos` from the post point instead of on stone at structure coordinates (rotated tests put those a block off); the two "drops the target" tests wait with `thenWaitUntil` instead of a 30-tick window; reason: implementer, to make the tests correct under rotation and Error Prone. |
| 4 | `53e2f9f` | `stashGift` is public (the GameTests are in another package); the Peaceful drop test runs in its own environment `peaceful_gear` (two tests in one `peaceful` batch run side by side and the difficulty change broke the other); reason: implementer, both needed to compile and pass. |

## Resume State

Not started. Baseline GameTests: 249 (measured 2026-11-09 at `667c882`, before Task 1; Task 1 Step 2 is already done).
