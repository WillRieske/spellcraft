# Evoking II, Part 1 (small fixes) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the fang key to **Y**, make fangs rise only under the blocks enemies stand on (14 blocks down, 6 up, with one chase fang for a target that steps away), and make villagers, wandering traders and iron golems allies, with a mercy rule that never lets the player's side (the player, fangs, tamed wolves, summoned mobs) take a golem under 25 health and a forgiveness rule that calms it there.

**Architecture:** Three small changes to round one's code, each behind a pure rule. `Fangs.snapshot` records *target footprints* (the block columns each target's box covers, computed by the pure `spell.rules.FangFootprints`, with the floor under each column found at the press) instead of the whole square, and `Fangs.perform` places one delay-0 fang per footprint spot; a transient `fang id → chase record` map and an `EntityTickEvent.Post` listener add the chase fang on the bite tick. `Allies` gains the two new kinds and `isMadAt`; `TrailFires.isSpared` spares every golem. A new `spell.GolemMercy` subscriber clamps player-side damage on `LivingDamageEvent.Pre` through the pure `spell.rules.GolemMercyRule`, forgives on `LivingDamageEvent.Post` (vanilla records the attacker *after* the damage lands, so forgiving in `Pre` would be undone), and cancels `LivingChangeTargetEvent` when a golem picks a summoned mob. The client change is the key mapping's name and default only.

**Tech Stack:** Minecraft 26.3, NeoForge 26.3.0.23-beta (ModDevGradle), Java 25, JUnit 5, jqwik, ArchUnit, AssertJ, Error Prone (`-Werror`), Spotless + palantir-java-format, PIT, pre-commit, gitlint, markdownlint-cli2.

**Spec:** [`docs/superpowers/specs/2026-11-07-evoking-ii-design.md`](../specs/2026-11-07-evoking-ii-design.md), §2 (Part 1), with §10–§13 as cross-cutting rules. Section references below point into it.

## Global Constraints

- Minecraft **26.3**; NeoForge **26.3.0.23-beta**, pinned; Java **25**. Mod ID **`spellcraft`**; base package **`io.github.willrieske.spellcraft`**.
- **Branch:** `feat/evoking-ii-part-1`, cut from `main`, in a worktree the executor creates (`superpowers:using-git-worktrees`); commands run from that worktree's root. In the Bash tool use `./gradlew`; in PowerShell use `.\gradlew.bat`. Never `cd` to `C:\dev\spellcraft` itself.
- **Minecraft sources for checks:** the main checkout already has them unpacked at `C:\dev\spellcraft\build\mc-src` (vanilla, patched) and `C:\dev\spellcraft\build\nf-src` (NeoForge); read those with Grep/Read instead of unpacking again in the worktree. Line numbers below are from them (2026-11-07) and were re-checked by the plan review.
- Key (§2.1): mapping name **`key.spellcraft.fangs_y`**, default **`InputConstants.KEY_Y`** (28), `InputConstants.Type.KEYBOARD`, `KeyConflictContext.IN_GAME`, category `spellcraft:spells`; lang text stays **"Evoking: Fangs"**. **Never `GLFW.GLFW_KEY_*`**: 26.3 key codes are SDL scancodes. V is unchanged.
- Fangs (§2.2): scan window **`y ∈ [feet − 14, feet + 7)`** (`SCAN_DEPTH = 14`, `SCAN_HEIGHT = 6`); a footprint = the columns a target's bounding box covers with positive width, inside the square, the caster's column excluded; for each column the floor is searched at the press from **`feetY + 1` down to `feetY − 2`**; one fang per **spot** (column + floor height; two targets sharing a spot get one fang, the first target found keeps it); **`delayTicks 0`**; each fang faces away from the caster's block at the press; the entity-ticking chunk check stays. Chase: one per target per cast, on the fang's bite tick (**the fang's 8th tick**, `tickCount == 8`), only if the target is alive, within **16** blocks of the caster, not an ally and not standing on any column of its own footprint; the chase fang faces away from the caster's block at the press too and carries no record.
- Allies (§2.3): `AbstractVillager` → ally; `IronGolem` → ally unless `Allies.isMadAt(golem, owner)` (`getTarget()` is the owner, or `isAngryAt(owner, level)`); both added to `mayHaveAllies`. `TrailFires.isSpared` spares every `IronGolem` once the flame has an owner.
- Mercy (§2.3): on `LivingDamageEvent.Pre` for an `IronGolem` whose damage source's `getEntity()` is **a `Player`, a `Wolf` whose owner is a `Player`, or any entity carrying the `AllyAttachments.SUMMONER` mark** (round one's summoned vexes now, Bound Vexes from Part 2 on): `newDamage = min(newDamage, max(0, health − 25))`. Forgiveness on `LivingDamageEvent.Post` for the same sources when `health ≤ 25`: `golem.stopBeingAngry()` (four calls: `setLastHurtByMob(null)`, `setPersistentAngerTarget(null)`, `setTarget(null)`, `setPersistentAngerEndTime(-1L)`, `NeutralMob.java:136-141`). Any player's side, not only the fangs' owner. Lava, falls, other mobs, `/kill`: untouched. `LivingChangeTargetEvent` is cancelled when an `IronGolem` would target a mob that `hasData(AllyAttachments.SUMMONER)`.
- Pure core (`spell.rules`): no `net.minecraft`/NeoForge/jspecify imports (ArchUnit rule 2 allows only `java..`). Client-only code only under `client` (rule 1). Common code never references `client`.
- Error Prone runs with `-Werror`; suppress only with `@SuppressWarnings("<Check>")` plus a comment why (`ReferenceEquality` for entity identity, docs/api-notes.md §8). Compare entities by UUID where you can, to need no suppression.
- **Unit test style:** new unit and jqwik test classes follow the methodology's *Unit test style* (`~/.claude/development-methodology.md`): a Javadoc per test ("When …, the method should …" then `<p>Method under test: {@link …}`), one `@Nested` class per method under test, `// Arrange` / `// Act` / `// Assert` sections except in one-statement tests. GameTests keep their file's current style (the same Javadoc shape, `static void` bodies, `helper.assert…`).
- GameTests that place fangs through a cast register through **`FangGameTests.addPadded`** (padding 13, the long tick budget); the padded box is cleared to air 13 blocks down, so those tests lay their own stone with `FangGameTests.floor`. The one exception is Task 7's `mercyClampsAFangBite`, which builds a single `EvokerFangs` inside the 3×3×3 structure and needs no padding. Targets are **pigs or a ravager**, never zombies in tests that wait ticks (zombies burn in the GameTest server's daylight); after Task 6 a calm iron golem is an ally, so only a mad one can be a target. A test that mutates global state gets its own environment name.
- **Execution order:** **Task 1 first** (its api-notes §20 is cited by the Javadoc of Tasks 4–7). Then Tasks 2, 3 and 4 are independent; Task 5 needs Task 2; Task 6 is independent (it also edits `FangGameTests.e7`); Task 7 needs Tasks 3 and 6 (it adds tests inside `GolemGameTests.registerAll()` and does not touch `SpellcraftGameTests`); Task 8 is last. Only Task 6 edits `SpellcraftGameTests`'s static block.
- **No red run for GameTest tasks (5–7):** their Step 1 only writes the tests, which compile only with the production change; the red-then-green cycle is for the pure-core tasks (2, 3). A GameTest step's "Expected" is the green run after the production code lands.
- **Verification recipe:** every Gradle verification step runs `./gradlew <task> --console=plain > build/<task>.log 2>&1; echo "exit=$?"; tail -40 build/<task>.log` (never `| tail`, which hides Gradle's exit status). Expected: `exit=0`. `runGameTestServer` takes minutes; run it in the background. If a test's setup assertion about room under the caster fails, the test is running at an unexpected Y: report it, do not change the window constants.
- **Format before you build:** in every task that writes Java, run `./gradlew spotlessApply --console=plain` right before the build/commit step and stage every file it changed in your task's files (check `git status --short`).
- Visual checks belong to Will: never claim a visual works; add `docs/playtest-checklist.md` items instead (Task 8).
- Commits: conventional (gitlint), title ≤ 100 chars, body lines ≤ 80, no attribution lines. The commit gate runs `spotlessCheck` and `build` (minutes); pre-push runs `runGameTestServer`. **Run the `git add` line and the `git commit` line as two separate tool calls**, then check `git log -1 --oneline` landed.
- Release (bump `mod_version`, tag) is not in this plan: the version bumps once, after Part 7 (§12).

## File Structure

```text
src/main/java/io/github/willrieske/spellcraft/
├── spell/rules/FangFootprints.java              PURE, new: box → block columns; the square filter
├── spell/rules/FangSquare.java                  Cell, CELL_COUNT and cells() removed (the ripple is gone)
├── spell/rules/GolemMercyRule.java              PURE, new: clamp(health, damage), forgives(health), MERCY_HEALTH
├── spell/Allies.java                            + AbstractVillager, IronGolem, isMadAt
├── spell/GolemMercy.java                        new: Pre clamp, Post forgiveness, target-cancel subscriber
├── trailing/TrailFires.java                     isSpared: golem clause
├── trailing/TrailFireBlock.java                 entityInside Javadoc names golems
├── evoking/Fangs.java                           footprints snapshot, per-spot perform, chase map + tick/leave/stop
├── client/evoking/EvokingKeys.java              FANGS: name fangs_y, default KEY_Y
└── gametest/
    ├── SpellcraftGameTests.java                 + GolemGameTests.registerAll()
    ├── FangGameTests.java                       E1, E2, E3, E7, E17, E19, server-stop updated; fangs_ii_* added
    ├── AlliesGameTests.java                     + a7 villagers and traders
    └── GolemGameTests.java                      new: allies-unless-mad, trail, mercy, forgiveness, target cancel
src/main/resources/assets/spellcraft/lang/en_us.json   key.spellcraft.fangs → key.spellcraft.fangs_y
src/test/java/io/github/willrieske/spellcraft/
    SpellAssetsTest.java                         + the key-mapping lang keys
    spell/rules/FangFootprintsTest.java, FangFootprintsPropertiesTest.java,
    spell/rules/GolemMercyRuleTest.java, GolemMercyRulePropertiesTest.java,
    spell/rules/FangSquareTest.java, FangSquarePropertiesTest.java   (the Cells groups removed)
docs/api-notes.md                                §20: the facts below
docs/playtest-checklist.md                       Trailing line 105; Evoking section: Y and under-target fangs; new "Evoking II" section
docs/mutation-testing.md                         a row for this part; the Fangs.perform probe row renamed
README.md                                        the Trailing and Evoking paragraphs
Not modified: src/main/templates/META-INF/neoforge.mods.toml (no new spell in Part 1), CLAUDE.md (the packet rule is unchanged), gradle.properties (no version bump).
```

## Divergences from the spec (for the plan review's Phase 2)

The spec was written before these lines of game code were read; the plan follows the code.

1. **Forgiveness moves to `LivingDamageEvent.Post`.** §2.3 says forgive "in the same handler" as the clamp (`Pre`). But `LivingEntity.hurtServer` records the attacker *after* `actuallyHurt` (`resolveMobResponsibleForDamage`, `LivingEntity.java:1298` → `setLastHurtByMob`, `:1435`), so a `Pre` forgiveness is overwritten one line later and `HurtByTargetGoal` makes the golem mad again. `Post` fires at `:1355`, after that.
2. **`golem.stopBeingAngry()` alone.** §2.3 lists three calls; `NeutralMob.stopBeingAngry` (`NeutralMob.java:136-141`) is those three plus `setPersistentAngerEndTime(-1L)`, which also ends universal anger.
3. **Pure clamp lives in `spell.rules.GolemMercyRule`**, not `GolemMercy` (§11 says "`GolemMercy.clamp` (pure)"): the subscriber needs Minecraft imports, and ArchUnit rule 2 keeps `spell.rules` pure. `clamp` is total: NaN gives 0 damage and infinite damage clamps to the room, so a bad value from another mod never throws inside a damage event.
4. **Footprint property bound.** §11 says "a 3×3 box gives ≤ 9" columns. A box 3.0 wide that straddles block boundaries covers 4 columns per axis, so the true bound is `(⌈w⌉ + 1)²` = 16. The property tests use that.
5. **The game event stays on.** §2.2 says "The game event stays off"; round one's `Fangs.perform` posts `GameEvent.ENTITY_PLACE` per fang and this plan keeps doing so, as today.
6. **Dedupe lives in `Fangs.snapshot`, keyed by column and floor height, not in `FangFootprints`.** §2.2 and §11 put "duplicates collapse" with the pure column math. The floor under a column depends on the target's own feet (a skeleton on a ledge over a zombie shares the column but not the floor), so the collapse needs the level and happens at the press, in `snapshot`: one fang per *spot* (column + floor y). A target whose spots are all claimed, or that floats more than two blocks over a floor, gets no fang and no chase record.
7. **Summoned mobs are the player's side now.** §2.3 says Bound Vexes count "from Part 2 on, when the class exists". Round one's vanilla summoned vexes already carry the summoner mark and would otherwise kill a mad golem with no mercy; the mark, not the class, is the test, so Part 1 covers them and Part 2 needs no change here.
8. **Mercy covers any player's side**, not only the fangs' owner: the spec's "a player's side" is read literally.

---

### Task 1: Record the verified 26.3 API facts

**Files:**

- Modify: `docs/api-notes.md` (append a §20 after §19)

**Interfaces:**

- Consumes: nothing.
- Produces: the section every later task's Javadoc and comments cite (`docs/api-notes.md §20`), so this task runs first.

- [x] **Step 1: Append §20 to `docs/api-notes.md`**

Append after the last line of §19:

````markdown

> **As built (`c45ad64`):** matches plan.

## 20. Evoking II, Part 1: fang footprints, chase, golem mercy (Evoking II Part 1 task)

Verified 2026-11-07 against the same unpacked sources. Paths relative to `build/mc-src` or `build/nf-src`.

- Key: `InputConstants.KEY_Y = 28` (`com/mojang/blaze3d/platform/InputConstants.java:65`); no `new KeyMapping(` line in
  `Options.java` binds 28 (checked for the spec). `KEY_F = 9` (`:46`) stays swap-offhand's.
- Fang bite tick: `EvokerFangs.tick` (`world/entity/projectile/EvokerFangs.java:85-97`) runs `--warmupDelayTicks < 0`
  once per server tick, bites when the counter is `-8`, over `getBoundingBox().inflate(0.2, 0.0, 0.2)`, and
  discards itself when `lifeTicks` (22) runs out: a fang placed with `delayTicks 0` bites on its 8th tick and dies of
  age on its 23rd, 15 ticks after the bite. `ServerLevel.tickNonPassenger` runs `entity.commonTick()`
  (`Entity.java:525-536`, `tickCount++` at `:535`; `Entity.tick` → `baseTick` never touches `tickCount`), then
  `entity.tick()`, then posts `EntityTickEvent.Post` (`server/level/ServerLevel.java:863-865`), so on the fang's
  Nth `tick()` the public `tickCount` (`Entity.java:255`) is N when `Post` fires: 8 on the bite tick. A fang added
  during a tick is not ticked until the next one (`EntityTickList` iterates a snapshot). GameTests run after every
  level has ticked (`MinecraftServer.java:1145`).
- `EntityTickEvent.Post` (`nf-src/.../event/tick/EntityTickEvent.java:51`) fires for every entity, as above.
  `EntityLeaveLevelEvent(Entity, Level)` (`nf-src/.../event/entity/EntityLeaveLevelEvent.java:22-33`) is posted from
  the entity callbacks' `onTrackingEnd` (`ServerLevel.java:2079`, the post at `:2108`): for a discarded fang it comes
  through `PersistentEntitySectionManager.onRemove` (`:466-468`), and it also fires when the entity's chunk stops
  being accessible with no removal (`:143-146`). `Level.getEntity(UUID)` (`world/level/Level.java:846`);
  `Entity.distanceTo(Entity)` (`Entity.java:1888`); `Entity.isRemoved()`; `Entity.getBoundingBox()` is final
  (`:3582`) and `AABB.minX/maxX/minZ/maxZ` are public finals (`world/phys/AABB.java:16-21`).
- Damage pipeline: `LivingEntity.hurtServer` (`world/entity/LivingEntity.java:1223`) → `actuallyHurt` (`:1290`) fires
  `LivingDamageEvent.Pre` after the armor and magic reductions (`:2035-2043`; killing the entity in `Pre` throws) and
  applies `getNewDamage()` to health (`setHealth` is skipped when it is 0, `:2056-2060`); **then**
  `resolveMobResponsibleForDamage` (`:1298`, body `:1431-1437`) calls `setLastHurtByMob(source.getEntity())` for
  any living causer not tagged `NO_ANGER`, and `resolvePlayerResponsibleForDamage` (`:1299`, `:1439-1450`) records
  the player or a tame animal's owner; then `LivingDamageEvent.Post` (`:1355`), which fires even when the damage was
  clamped to 0 (`hurtServer` has no `damage <= 0` early return, `:1223-1358`). So anger cleared in `Pre` is re-set;
  clear it in `Post`. The hurt cooldown is `damageCooldownTime` (`:242`, set `:1288/:1292`, compared `:1272`):
  while it runs, a hit no larger than `lastHurt` returns false before any event. `Entity.invulnerableTime` is a
  different field that `hurtServer` never sets.
  `LivingDamageEvent.Pre`: `getSource()`, `getNewDamage()`, `setNewDamage(float)` (non-negative,
  `DamageContainer.java:91-93`) (`nf-src/.../living/LivingDamageEvent.java:48-83`); `Post`: `getSource()`,
  `getOriginalDamage()`, `getInflictedDamage()`, `getHealthDamage()` (`:97-160`).
  `DamageSource.getEntity()` (`world/damagesource/DamageSource.java:61`) is the causing entity: the owner for
  `indirectMagic(fangs, owner)` (`DamageSources.java:243`), the mob for `mobAttack(LivingEntity)` (`:195`; a vex's
  and a wolf's hits go through `Mob.doHurtTarget`, `Mob.java:1422`), the player for `playerAttack`.
  `OwnableEntity.getOwner()` returns `@Nullable LivingEntity` (`world/entity/OwnableEntity.java:13`).
- `HurtByTargetGoal.canUse` (`world/entity/ai/goal/target/HurtByTargetGoal.java:33-36`) needs `getLastHurtByMob() !=
  null` and a new `getLastHurtByMobTimestamp()`; `LivingEntity.setLastHurtByMob` (`:668-671`) sets both.
- Anger: `NeutralMob.isAngryAt(LivingEntity, ServerLevel)` (`world/entity/NeutralMob.java:95-106`) = `canAttack(entity)`
  and (universal anger, or the persistent anger target matches); `stopBeingAngry()` (`:136-141`) =
  `setLastHurtByMob(null)`, `setPersistentAngerTarget(null)`, `setTarget(null)`, `setPersistentAngerEndTime(-1L)`;
  `updatePersistentAnger(level, true)` (`:58-89`) copies `getTarget()` into the persistent target each tick.
- `IronGolem` (`world/entity/animal/golem/IronGolem.java:53`, `implements NeutralMob`, no armor attribute `:93-99`, not
  fire immune, `EntityTypes.java:590-592`): target goals (`:76-84`) `DefendVillageTargetGoal` 1, `HurtByTargetGoal` 2,
  players via `isAngryAt` 3, `Mob` that is `Enemy && !Creeper` 3; `doPush` (`:107-114`) targets a pushing `Enemy` 1
  time in 20; `aiStep` (`:116-130`) runs `updatePersistentAnger(level, true)` every tick, **also with AI off**
  (`setNoAi` skips goals and movement, not `aiStep`); `canAttack` (`:138-144`) is false for players when
  player-created and false for creepers; `MAX_HEALTH` 100 (`:95`); `hurtServer` (`:209-217`) plays the crack sound;
  `Crackiness.GOLEM` thresholds are 0.75, 0.5, 0.25 with `fraction < threshold` (`Crackiness.java:6,19-25`), so a
  golem at exactly 25 of 100 shows medium cracks, not the heaviest; `mobInteract` heals 25 with an ingot (`:262-277`);
  `setPersistentAngerTarget` (`:176`), `startPersistentAngerTimer` (`:161`), `isPlayerCreated` (`:289`), `getTarget()`
  from `Mob`. Builder `.sized(1.4F, 2.7F)` (`EntityTypes.java:591`).
- `Mob.setTarget` (`world/entity/Mob.java:257-263`) posts `LivingChangeTargetEvent` (cancellable,
  `nf-src/.../living/LivingChangeTargetEvent.java:33`) with `MOB_TARGET`; when cancelled the old target stays.
  `getNewAboutToBeSetTarget()` (`:51`).
- Classes: `AbstractVillager` (`world/entity/npc/villager/AbstractVillager.java:55`, abstract, extends `AgeableMob`;
  `Villager` `:99` and `npc/wanderingtrader/WanderingTrader.java` extend it; `ZombieVillager` extends `Zombie`,
  `ZombieVillager.java:61`, not `AbstractVillager`), `animal/wolf/Wolf.java`, `monster/Ravager.java` (`.sized(1.95F,
  2.2F)`, `EntityTypes.java:868`), villager and trader `.sized(0.6F, 1.95F)` (`:1068`, `:1081`).
- `EntityReference.of(T)` (`world/entity/EntityReference.java:43`) builds the reference `setPersistentAngerTarget` takes.
- GameTest server: default difficulty Normal (`GameTestServer.java:113`), so `canAttack(player)` holds; a finished
  batch's entities are not cleared (`StructureGridSpawner(..., clearOnBatch = false)`, `GameTestServer.java:338`) and
  the next batch starts the same tick (`GameTestRunner.java:107-115`), so a test must never count global state that
  another batch's leftover fangs can change.
````

- [ ] **Step 2: Lint and commit**

Run: `pre-commit run markdownlint-cli2 --files docs/api-notes.md`
Expected: `Passed`.

```bash
git add docs/api-notes.md
```

```bash
git commit -m "docs(api-notes): record the Evoking II Part 1 facts (bite tick, damage order, golem anger)"
```

Then `git log -1 --oneline` shows the commit.

---

### Task 2: Pure `FangFootprints`

**Files:**

- Create: `src/main/java/io/github/willrieske/spellcraft/spell/rules/FangFootprints.java`
- Test: `src/test/java/io/github/willrieske/spellcraft/spell/rules/FangFootprintsTest.java`
- Test: `src/test/java/io/github/willrieske/spellcraft/spell/rules/FangFootprintsPropertiesTest.java`

**Interfaces:**

- Consumes: `FangSquare.contains(int dx, int dz)` (existing).
- Produces: `FangFootprints.Column(int x, int z)` record (absolute block column); `static List<Column> columns(double minX, double minZ, double maxX, double maxZ)`; `static List<Column> inSquare(List<Column> columns, FangSquare square, int feetX, int feetZ)`. Task 5 uses both. Dedupe between targets is **not** here (Divergence 6): it needs the floor, so `Fangs.snapshot` does it.

- [x] **Step 1: Write the failing unit tests**

`FangFootprintsTest.java`:

```java
package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.willrieske.spellcraft.spell.rules.FangFootprints.Column;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FangFootprintsTest {
    @Nested
    class Columns {
        /**
         * When the box sits inside one block (a pig, 0.9 wide, centered on the block), columns should answer that one
         * column.
         *
         * <p>Method under test: {@link FangFootprints#columns(double, double, double, double)}
         */
        @Test
        void aBoxInsideOneBlockGivesThatColumn() {
            assertThat(FangFootprints.columns(2.05, -3.95, 2.95, -3.05)).containsExactly(new Column(2, -4));
        }

        /**
         * When the box's edge sits exactly on a block boundary, columns should not claim the block beyond the edge:
         * [2.0, 3.0] is column 2 alone.
         *
         * <p>Method under test: {@link FangFootprints#columns(double, double, double, double)}
         */
        @Test
        void anEdgeOnABoundaryClaimsNoBlockBeyondIt() {
            assertThat(FangFootprints.columns(2.0, 5.0, 3.0, 6.0)).containsExactly(new Column(2, 5));
        }

        /**
         * When the box straddles a boundary, columns should answer both blocks: [2.5, 3.5] is columns 2 and 3.
         *
         * <p>Method under test: {@link FangFootprints#columns(double, double, double, double)}
         */
        @Test
        void aBoxAcrossABoundaryClaimsBothBlocks() {
            assertThat(FangFootprints.columns(2.5, 0.2, 3.5, 0.8)).containsExactly(new Column(2, 0), new Column(3, 0));
        }

        /**
         * When a ravager (1.95 wide) stands on the center of block (5, -6), columns should answer the 3×3 around it,
         * row by row. (Block -6 spans [-6, -5), so its center is z = -5.5.)
         *
         * <p>Method under test: {@link FangFootprints#columns(double, double, double, double)}
         */
        @Test
        void aRavagerOnABlockCenterCoversThreeByThree() {
            // Arrange
            double half = 1.95 / 2;
            // Act
            List<Column> columns = FangFootprints.columns(5.5 - half, -5.5 - half, 5.5 + half, -5.5 + half);
            // Assert
            assertThat(columns)
                    .containsExactly(
                            new Column(4, -7), new Column(5, -7), new Column(6, -7),
                            new Column(4, -6), new Column(5, -6), new Column(6, -6),
                            new Column(4, -5), new Column(5, -5), new Column(6, -5));
        }

        /**
         * When the box has no width on an axis (min equals max), columns should still answer the column it sits in; a
         * point on a boundary belongs to the block it starts.
         *
         * <p>Method under test: {@link FangFootprints#columns(double, double, double, double)}
         */
        @Test
        void aZeroWidthBoxClaimsItsOwnColumn() {
            assertThat(FangFootprints.columns(2.5, 3.0, 2.5, 3.0)).containsExactly(new Column(2, 3));
        }

        /**
         * When a coordinate is NaN or infinite, or max is below min, columns should throw.
         *
         * <p>Method under test: {@link FangFootprints#columns(double, double, double, double)}
         */
        @Test
        void rejectsNonFiniteOrInvertedBoxes() {
            // Act & Assert
            assertThatThrownBy(() -> FangFootprints.columns(Double.NaN, 0, 1, 1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> FangFootprints.columns(0, 0, Double.POSITIVE_INFINITY, 1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> FangFootprints.columns(2, 0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> FangFootprints.columns(0, 2, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class InSquare {
        /**
         * When columns lie inside the square (one of them beside the caster, in the caster's own row), on its edge, past
         * its edge and on the caster's own block, inSquare should keep the inside and edge ones, in order, and drop the
         * rest.
         *
         * <p>Method under test: {@link FangFootprints#inSquare(List, FangSquare, int, int)}
         */
        @Test
        void keepsTheSquaresColumnsButTheCasters() {
            // Arrange: facing north from (10, 20): dx -6…6, dz -12…0
            FangSquare square = FangSquare.of(180);
            List<Column> columns = List.of(
                    new Column(10, 20), // the caster's block
                    new Column(11, 20), // the east neighbor, in the caster's row: kept
                    new Column(10, 19), // just ahead
                    new Column(17, 19), // dx 7: past the east edge
                    new Column(16, 8), // the far corner, on the edge
                    new Column(10, 7), // dz -13: past the far edge
                    new Column(10, 21)); // behind
            // Act
            List<Column> kept = FangFootprints.inSquare(columns, square, 10, 20);
            // Assert
            assertThat(kept).containsExactly(new Column(11, 20), new Column(10, 19), new Column(16, 8));
        }

        /**
         * When no column is inside, inSquare should answer an empty list.
         *
         * <p>Method under test: {@link FangFootprints#inSquare(List, FangSquare, int, int)}
         */
        @Test
        void answersEmptyWhenNothingIsInside() {
            assertThat(FangFootprints.inSquare(List.of(new Column(0, 5)), FangSquare.of(180), 0, 0)).isEmpty();
        }
    }
}
```

`FangFootprintsPropertiesTest.java`:

```java
package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.willrieske.spellcraft.spell.rules.FangFootprints.Column;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Group;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;

class FangFootprintsPropertiesTest {
    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class Columns {
        /**
         * For any box that fits inside one block on both axes, columns should answer exactly that block.
         *
         * <p>Method under test: {@link FangFootprints#columns(double, double, double, double)}
         */
        @Property
        void aBoxInsideOneBlockGivesOneColumn(
                @ForAll @IntRange(min = -50, max = 50) int bx,
                @ForAll @IntRange(min = -50, max = 50) int bz,
                @ForAll @DoubleRange(min = 0.0, max = 0.5) double startX,
                @ForAll @DoubleRange(min = 0.0, max = 0.5) double startZ,
                @ForAll @DoubleRange(min = 0.0, max = 0.49) double width) {
            // Act
            List<Column> columns =
                    FangFootprints.columns(bx + startX, bz + startZ, bx + startX + width, bz + startZ + width);
            // Assert
            assertThat(columns).containsExactly(new Column(bx, bz));
        }

        /**
         * For any box of width w ≤ 3 on each axis, columns should answer exactly the distinct columns the box has
         * positive width in (a zero-width axis still gives the column it sits in), and so at most (⌈w⌉ + 1)² of them.
         * (The spec's "a 3×3 box gives ≤ 9" holds only for a block-aligned box; a straddling 3.0-wide box touches four
         * columns per axis.)
         *
         * <p>Method under test: {@link FangFootprints#columns(double, double, double, double)}
         */
        @Property
        void columnsAreExactlyTheBlocksWithPositiveWidthInTheBox(
                @ForAll @DoubleRange(min = -40, max = 40) double minX,
                @ForAll @DoubleRange(min = -40, max = 40) double minZ,
                @ForAll @DoubleRange(min = 0.0, max = 3.0) double width) {
            // Arrange
            double maxX = minX + width;
            double maxZ = minZ + width;
            int perAxis = (int) Math.ceil(width) + 1;
            List<Column> expected = new ArrayList<>();
            for (int z = (int) Math.floor(minZ) - 1; z <= (int) Math.ceil(maxZ); z++) {
                for (int x = (int) Math.floor(minX) - 1; x <= (int) Math.ceil(maxX); x++) {
                    boolean xIn = (x + 1 > minX && x < maxX) || (width == 0.0 && x == (int) Math.floor(minX));
                    boolean zIn = (z + 1 > minZ && z < maxZ) || (width == 0.0 && z == (int) Math.floor(minZ));
                    if (xIn && zIn) {
                        expected.add(new Column(x, z));
                    }
                }
            }
            // Act
            List<Column> columns = FangFootprints.columns(minX, minZ, maxX, maxZ);
            // Assert
            assertThat(columns).containsExactlyElementsOf(expected);
            assertThat(columns).hasSizeLessThanOrEqualTo(perAxis * perAxis);
        }
    }

    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class InSquare {
        /**
         * For any yaw, caster block and box near the caster, inSquare should answer exactly the input columns that lie
         * inside the square and are not the caster's own block, in input order.
         *
         * <p>Method under test: {@link FangFootprints#inSquare(List, FangSquare, int, int)}
         */
        @Property
        void keepsExactlyTheSquaresColumnsButTheCasters(
                @ForAll @DoubleRange(min = -360, max = 360) double yaw,
                @ForAll @IntRange(min = -100, max = 100) int feetX,
                @ForAll @IntRange(min = -100, max = 100) int feetZ,
                @ForAll @DoubleRange(min = -15, max = 15) double dx,
                @ForAll @DoubleRange(min = -15, max = 15) double dz,
                @ForAll @DoubleRange(min = 0.0, max = 3.0) double width) {
            // Arrange
            FangSquare square = FangSquare.of(yaw);
            List<Column> all = FangFootprints.columns(feetX + dx, feetZ + dz, feetX + dx + width, feetZ + dz + width);
            List<Column> expected = all.stream()
                    .filter(c -> square.contains(c.x() - feetX, c.z() - feetZ))
                    .filter(c -> !(c.x() == feetX && c.z() == feetZ))
                    .toList();
            // Act
            List<Column> kept = FangFootprints.inSquare(all, square, feetX, feetZ);
            // Assert
            assertThat(kept).containsExactlyElementsOf(expected);
        }
    }
}
```

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests '*FangFootprints*' --console=plain > build/test.log 2>&1; echo "exit=$?"; tail -40 build/test.log`
Expected: `exit=1`, compilation errors naming `FangFootprints`.

- [x] **Step 3: Write `FangFootprints`**

```java
package io.github.willrieske.spellcraft.spell.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The block columns a target's bounding box covers (Evoking II spec §2.2): a fang goes under each one. Pure: the box
 * arrives as its horizontal extent, not as an {@code AABB}. Meant for entity-sized boxes (a few blocks wide); a box
 * thousands of blocks wide would list every column in it.
 */
public final class FangFootprints {
    /**
     * One block column, in absolute block coordinates.
     *
     * @param x the block's x
     * @param z the block's z
     */
    public record Column(int x, int z) {}

    private FangFootprints() {}

    /**
     * Every column the box covers with positive width: from {@code floor(min)} to {@code ceil(max) − 1} on each axis,
     * so a box whose edge sits exactly on a block boundary does not claim the block beyond it. A box with no width on
     * an axis still claims the column it sits in. Row by row (z, then x).
     *
     * @param minX the box's west edge
     * @param minZ the box's north edge
     * @param maxX the box's east edge
     * @param maxZ the box's south edge
     * @return the columns, west to east within each row, north to south
     * @throws IllegalArgumentException if a coordinate is not finite or a max is below its min
     */
    public static List<Column> columns(double minX, double minZ, double maxX, double maxZ) {
        requireBox(minX, maxX);
        requireBox(minZ, maxZ);
        int x0 = (int) Math.floor(minX);
        int x1 = Math.max(x0, (int) Math.ceil(maxX) - 1);
        int z0 = (int) Math.floor(minZ);
        int z1 = Math.max(z0, (int) Math.ceil(maxZ) - 1);
        List<Column> columns = new ArrayList<>();
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                columns.add(new Column(x, z));
            }
        }
        return Collections.unmodifiableList(columns);
    }

    /**
     * The columns of {@code columns} that lie inside {@code square} for a caster whose block is {@code (feetX,
     * feetZ)}, the caster's own column dropped; order kept.
     *
     * @param columns the candidate columns, absolute
     * @param square the square ahead of the caster
     * @param feetX the caster's block x
     * @param feetZ the caster's block z
     * @return the kept columns
     */
    public static List<Column> inSquare(List<Column> columns, FangSquare square, int feetX, int feetZ) {
        List<Column> kept = new ArrayList<>();
        for (Column column : columns) {
            int dx = column.x() - feetX;
            int dz = column.z() - feetZ;
            if ((dx != 0 || dz != 0) && square.contains(dx, dz)) {
                kept.add(column);
            }
        }
        return Collections.unmodifiableList(kept);
    }

    private static void requireBox(double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max) || max < min) {
            throw new IllegalArgumentException("not a box: [" + min + ", " + max + "]");
        }
    }
}
```

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test --tests '*FangFootprints*' --console=plain > build/test.log 2>&1; echo "exit=$?"; tail -40 build/test.log`
Expected: `exit=0`, BUILD SUCCESSFUL.

- [x] **Step 5: Format and commit**

Run: `./gradlew spotlessApply --console=plain`

```bash
git add src/main/java/io/github/willrieske/spellcraft/spell/rules/FangFootprints.java src/test/java/io/github/willrieske/spellcraft/spell/rules/FangFootprintsTest.java src/test/java/io/github/willrieske/spellcraft/spell/rules/FangFootprintsPropertiesTest.java
```

```bash
git commit -m "feat(rules): add FangFootprints, the block columns a target covers"
```

Then `git log -1 --oneline` shows the commit.

> **As built (`f4e5a84`):** matches plan.

---

### Task 3: Pure `GolemMercyRule`

**Files:**

- Create: `src/main/java/io/github/willrieske/spellcraft/spell/rules/GolemMercyRule.java`
- Test: `src/test/java/io/github/willrieske/spellcraft/spell/rules/GolemMercyRuleTest.java`
- Test: `src/test/java/io/github/willrieske/spellcraft/spell/rules/GolemMercyRulePropertiesTest.java`

**Interfaces:**

- Consumes: nothing.
- Produces: `GolemMercyRule.MERCY_HEALTH = 25.0F`; `static float clamp(float health, float damage)` (total: never throws); `static boolean forgives(float health)`. Task 7 uses all three.

- [ ] **Step 1: Write the failing unit tests**

`GolemMercyRuleTest.java`:

```java
package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GolemMercyRuleTest {
    @Nested
    class Clamp {
        /**
         * When the hit would leave the golem at 25 or more, clamp should let it through whole.
         *
         * <p>Method under test: {@link GolemMercyRule#clamp(float, float)}
         */
        @ParameterizedTest(name = "health {0}, damage {1} -> {2}")
        @CsvSource({"100, 6, 6", "31, 6, 6", "30, 5, 5", "30, 0, 0"})
        void aHitThatLeavesTwentyFiveOrMoreLandsWhole(float health, float damage, float expected) {
            assertThat(GolemMercyRule.clamp(health, damage)).isEqualTo(expected);
        }

        /**
         * When the hit would take the golem under 25, clamp should cut it down to what leaves exactly 25.
         *
         * <p>Method under test: {@link GolemMercyRule#clamp(float, float)}
         */
        @ParameterizedTest(name = "health {0}, damage {1} -> {2}")
        @CsvSource({"30, 6, 5", "26, 100, 1", "100, 80, 75"})
        void aHitThatWouldGoUnderTwentyFiveStopsThere(float health, float damage, float expected) {
            assertThat(GolemMercyRule.clamp(health, damage)).isEqualTo(expected);
        }

        /**
         * When the golem is at or under 25, clamp should answer 0 whatever the damage; one float step above 25 it
         * should answer that step.
         *
         * <p>Method under test: {@link GolemMercyRule#clamp(float, float)}
         */
        @Test
        void atOrUnderTwentyFiveNothingLands() {
            // Act & Assert
            assertThat(GolemMercyRule.clamp(25, 6)).isZero();
            assertThat(GolemMercyRule.clamp(20, 6)).isZero();
            assertThat(GolemMercyRule.clamp(0, 6)).isZero();
            assertThat(GolemMercyRule.clamp(-5, 6)).isZero();
            float justAbove = Math.nextUp(25.0F);
            assertThat(GolemMercyRule.clamp(justAbove, 6)).isEqualTo(justAbove - 25.0F);
        }

        /**
         * When the damage is negative, clamp should answer 0, never a heal.
         *
         * <p>Method under test: {@link GolemMercyRule#clamp(float, float)}
         */
        @Test
        void negativeDamageBecomesZero() {
            assertThat(GolemMercyRule.clamp(50, -3)).isZero();
        }

        /**
         * When health or damage is NaN or infinite, clamp should still answer a finite, non-negative value: NaN means
         * nothing lands, infinite damage lands the whole room, infinite health leaves every hit whole.
         *
         * <p>Method under test: {@link GolemMercyRule#clamp(float, float)}
         */
        @ParameterizedTest(name = "health {0}, damage {1} -> {2}")
        @CsvSource({
            "NaN, 6, 0",
            "50, NaN, 0",
            "50, Infinity, 25",
            "50, -Infinity, 0",
            "Infinity, 6, 6",
            "-Infinity, 6, 0"
        })
        void nonFiniteInputsNeverThrow(float health, float damage, float expected) {
            assertThat(GolemMercyRule.clamp(health, damage)).isEqualTo(expected);
        }
    }

    @Nested
    class Forgives {
        /**
         * When the golem's health is at or under 25, forgives should answer true; just above, false.
         *
         * <p>Method under test: {@link GolemMercyRule#forgives(float)}
         */
        @ParameterizedTest(name = "health {0} -> {1}")
        @CsvSource({"25, true", "24.5, true", "0, true", "25.5, false", "100, false", "NaN, false"})
        void forgivesAtOrUnderTwentyFive(float health, boolean expected) {
            assertThat(GolemMercyRule.forgives(health)).isEqualTo(expected);
        }

        /**
         * When the health is one float step above 25, forgives should answer false.
         *
         * <p>Method under test: {@link GolemMercyRule#forgives(float)}
         */
        @Test
        void oneStepAboveTwentyFiveIsNotForgiven() {
            assertThat(GolemMercyRule.forgives(Math.nextUp(25.0F))).isFalse();
        }
    }
}
```

`GolemMercyRulePropertiesTest.java`:

```java
package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Group;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.FloatRange;

class GolemMercyRulePropertiesTest {
    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class Clamp {
        /**
         * For any health above 25 and any damage, the health after the clamped hit should never be under 25, and the
         * clamped damage should never exceed the asked damage or go negative.
         *
         * <p>Method under test: {@link GolemMercyRule#clamp(float, float)}
         */
        @Property
        void neverTakesAGolemUnderTwentyFive(
                @ForAll @FloatRange(min = 25.0f, max = 100.0f) float health,
                @ForAll @FloatRange(min = 0.0f, max = 200.0f) float damage) {
            // Act
            float dealt = GolemMercyRule.clamp(health, damage);
            // Assert
            assertThat(dealt).isBetween(0.0f, damage);
            assertThat(health - dealt).isGreaterThanOrEqualTo(GolemMercyRule.MERCY_HEALTH);
        }

        /**
         * For any health at or under 25, clamp should answer 0 whatever the damage.
         *
         * <p>Method under test: {@link GolemMercyRule#clamp(float, float)}
         */
        @Property
        void aGolemAtOrUnderTwentyFiveTakesNothing(
                @ForAll @FloatRange(min = -10.0f, max = 25.0f) float health,
                @ForAll @FloatRange(min = 0.0f, max = 200.0f) float damage) {
            assertThat(GolemMercyRule.clamp(health, damage)).isZero();
        }
    }

    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class Forgives {
        /**
         * For any golem above 25 hit hard enough to reach 25 (damage at least health − 25), the health left after the
         * clamped hit should be forgiven: the clamp and the forgiveness line agree.
         *
         * <p>Method under test: {@link GolemMercyRule#forgives(float)}
         */
        @Property
        void aClampedHitThatReachesTheLineIsForgiven(
                @ForAll @FloatRange(min = 25.0f, max = 100.0f) float health,
                @ForAll @FloatRange(min = 0.0f, max = 200.0f) float extra) {
            // Arrange
            float damage = (health - GolemMercyRule.MERCY_HEALTH) + extra;
            // Act
            float after = health - GolemMercyRule.clamp(health, damage);
            // Assert
            assertThat(GolemMercyRule.forgives(after)).isTrue();
        }

        /**
         * For any hit that leaves the golem strictly above 25, forgives should answer false.
         *
         * <p>Method under test: {@link GolemMercyRule#forgives(float)}
         */
        @Property
        void aHitThatLeavesMoreThanTwentyFiveIsNotForgiven(
                @ForAll @FloatRange(min = 26.0f, max = 100.0f) float health,
                @ForAll @FloatRange(min = 0.0f, max = 0.5f) float fraction) {
            // Arrange: damage strictly less than the room above 25
            float damage = (health - GolemMercyRule.MERCY_HEALTH) * fraction;
            // Act
            float after = health - GolemMercyRule.clamp(health, damage);
            // Assert
            assertThat(GolemMercyRule.forgives(after)).isFalse();
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests '*GolemMercyRule*' --console=plain > build/test.log 2>&1; echo "exit=$?"; tail -40 build/test.log`
Expected: `exit=1`, compilation errors naming `GolemMercyRule`.

- [ ] **Step 3: Write `GolemMercyRule`**

```java
package io.github.willrieske.spellcraft.spell.rules;

/**
 * Mercy for iron golems (Evoking II spec §2.3): the player's side never takes a golem under {@link #MERCY_HEALTH}, and
 * a golem left there forgives. Pure arithmetic; {@code spell.GolemMercy} applies it to the damage events.
 */
public final class GolemMercyRule {
    /** The health a golem keeps at least, against the player's side. */
    public static final float MERCY_HEALTH = 25.0F;

    private GolemMercyRule() {}

    /**
     * The damage to deal: {@code min(damage, max(0, health − 25))}. Total, because it runs inside a damage event: a
     * negative or NaN damage counts as 0, a NaN health as no room, and infinities follow the arithmetic (infinite
     * damage lands the whole room; infinite health leaves the hit whole).
     *
     * @param health the golem's health before the hit
     * @param damage the damage the hit would deal
     * @return the damage that leaves the golem at or above {@link #MERCY_HEALTH}, or 0 if it already is
     */
    public static float clamp(float health, float damage) {
        if (Float.isNaN(health) || Float.isNaN(damage)) {
            return 0.0F;
        }
        float room = Math.max(0.0F, health - MERCY_HEALTH);
        return Math.min(Math.max(0.0F, damage), room);
    }

    /**
     * Whether a golem at {@code health} is at or under the mercy line, so it stops being angry. NaN is not.
     *
     * @param health the golem's health after the hit
     * @return true at or under {@link #MERCY_HEALTH}
     */
    public static boolean forgives(float health) {
        return health <= MERCY_HEALTH;
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test --tests '*GolemMercyRule*' --console=plain > build/test.log 2>&1; echo "exit=$?"; tail -40 build/test.log`
Expected: `exit=0`.

- [ ] **Step 5: Format and commit**

Run: `./gradlew spotlessApply --console=plain`

```bash
git add src/main/java/io/github/willrieske/spellcraft/spell/rules/GolemMercyRule.java src/test/java/io/github/willrieske/spellcraft/spell/rules/GolemMercyRuleTest.java src/test/java/io/github/willrieske/spellcraft/spell/rules/GolemMercyRulePropertiesTest.java
```

```bash
git commit -m "feat(rules): add GolemMercyRule, the 25-health clamp and forgiveness line"
```

Then `git log -1 --oneline` shows the commit.

---

### Task 4: Fangs on Y

**Files:**

- Modify: `src/main/java/io/github/willrieske/spellcraft/client/evoking/EvokingKeys.java:19-26` (class Javadoc) and `:32-38` (the `FANGS` Javadoc and constant)
- Modify: `src/main/resources/assets/spellcraft/lang/en_us.json:32`
- Modify: `src/test/java/io/github/willrieske/spellcraft/SpellAssetsTest.java` (one new test)

**Interfaces:**

- Consumes: nothing.
- Produces: `EvokingKeys.FANGS` named `key.spellcraft.fangs_y`, default `InputConstants.KEY_Y`. Task 8's docs describe it.

- [ ] **Step 1: Write the failing lang-key test**

Add to `SpellAssetsTest.java`, after `scrollAndEffectHaveEnglishNames`, with the import `org.junit.jupiter.api.Test`:

```java
    /**
     * When the lang file is read, it should name both casting keys by their current mapping names and not the old
     * fangs name: a mismatch with {@code EvokingKeys} would show a raw key in Controls (the client class cannot be
     * loaded here, so the names are spelled out).
     *
     * <p>Method under test: the lang file against {@code EvokingKeys}
     */
    @Test
    void castingKeysHaveEnglishNames() throws IOException {
        // Act
        String lang = Files.readString(ASSETS.resolve("lang/en_us.json"));
        // Assert
        assertThat(lang).contains("\"key.spellcraft.fangs_y\"", "\"key.spellcraft.vexes\"");
        assertThat(lang).doesNotContain("\"key.spellcraft.fangs\"");
    }
```

Run: `./gradlew test --tests '*SpellAssetsTest*' --console=plain > build/test.log 2>&1; echo "exit=$?"; tail -20 build/test.log`
Expected: `exit=1`, `castingKeysHaveEnglishNames` fails (the lang file still has `key.spellcraft.fangs`).

- [ ] **Step 2: Rename the lang key**

In `en_us.json` replace the line

```json
  "key.spellcraft.fangs": "Evoking: Fangs",
```

with

```json
  "key.spellcraft.fangs_y": "Evoking: Fangs",
```

- [ ] **Step 3: Change the mapping**

In `EvokingKeys.java`, replace lines 19–26 (the whole class Javadoc) with:

```java
/**
 * The two casting keys (Evoking spec §2, §7; Evoking II §2.1). A press while the local player has Evoking sends an
 * {@link EvokingRequest}; the server decides what it does. Fangs default to Y (an unbound key in vanilla) and vexes to
 * V. While Evoking is on, a casting key bound to the same key as vanilla's swap-offhand (either of the two, if the
 * user rebinds one to F) swallows the swap: {@code ClientTickEvent.Pre} runs before {@code Minecraft.handleKeybinds},
 * so clicks consumed here never reach it (docs/api-notes.md §19). The swallow rule: the swap-offhand clicks are
 * drained only on a tick where a casting key that is bound to the same key as swap consumed at least one click, so
 * with Fangs rebound to Shift+F a plain F press still swaps. With the defaults it never fires.
 */
```

and replace lines 32–38 (the `FANGS` Javadoc and constant) with:

```java
    /**
     * Default Y (scancode 28; no vanilla binding uses it, docs/api-notes.md §20). Named {@code fangs_y} rather than
     * {@code fangs} so a saved {@code options.txt} from round one, which pinned the old name to F, falls back to the new
     * default: vanilla stores bindings by mapping name.
     */
    public static final KeyMapping FANGS = new KeyMapping(
            "key.spellcraft.fangs_y",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_Y,
            CATEGORY);
```

Leave `VEXES`, `onRegisterKeys`, `onClientTick` and `send` as they are. The result has exactly one doc comment above the class and one above `FANGS` (`-Xlint:all -Werror` flags a dangling doc comment).

- [ ] **Step 4: Check nothing else names the old key**

Run: `grep -rn "key.spellcraft.fangs\b\|KEY_F\b" src`
Expected: no output.

- [ ] **Step 5: Test, then prove the client classes still load**

Run: `./gradlew spotlessApply --console=plain`
Run: `./gradlew test --tests '*SpellAssetsTest*' --console=plain > build/test.log 2>&1; echo "exit=$?"; tail -20 build/test.log`
Expected: `exit=0`.
Run: `./gradlew runData --console=plain > build/runData.log 2>&1; echo "exit=$?"; tail -20 build/runData.log`
Expected: `exit=0` (runData initializes the client subscriber classes, `EvokingKeys` included, so the mapping registers without error; the commit gate runs the full `build`).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/willrieske/spellcraft/client/evoking/EvokingKeys.java src/main/resources/assets/spellcraft/lang/en_us.json src/test/java/io/github/willrieske/spellcraft/SpellAssetsTest.java
```

```bash
git commit -m "feat(evoking): cast fangs with Y; rename the mapping so old saves forget F"
```

Then `git log -1 --oneline` shows the commit.

---

### Task 5: Fangs only under targets, 14 blocks down, with a chase fang

**Files:**

- Modify: `src/main/java/io/github/willrieske/spellcraft/evoking/Fangs.java` (whole file)
- Modify: `src/main/java/io/github/willrieske/spellcraft/spell/rules/FangSquare.java:20-32` (remove `CELL_COUNT`, `Cell`) and `:85-98` (remove `cells()`); class Javadoc
- Modify: `src/test/java/io/github/willrieske/spellcraft/spell/rules/FangSquareTest.java` (remove the `Cells` nested class) and `FangSquarePropertiesTest.java` (remove the `Cells` group)
- Modify: `src/main/java/io/github/willrieske/spellcraft/gametest/FangGameTests.java` (E1, E2, E3, E17, E19, server-stop; new tests; registrations)

**Interfaces:**

- Consumes: `FangFootprints.columns`, `FangFootprints.inSquare`, `FangFootprints.Column` (Task 2); `FangSquare`, `Allies.isAllyOf`, `EvokingCaster.request/tick` (existing).
- Produces: `Fangs.SCAN_DEPTH = 14`, `Fangs.SCAN_HEIGHT = 6`, `Fangs.CHASE_RANGE = 16.0`, `Fangs.BITE_TICK = 8`; `Fangs.Spot(Column column, double y)`; `Fangs.Footprint(UUID target, List<Column> columns, List<Spot> spots)`; `Fangs.Snapshot(ResourceKey<Level> dimension, BlockPos feet, FangSquare square, List<Footprint> footprints)`; `static Optional<Snapshot> snapshot(ServerLevel, ServerPlayer)` and `static boolean perform(ServerLevel, ServerPlayer, Snapshot)` keep their signatures (so `EvokingCaster` is untouched); `public static boolean hasChaseRecord(EvokerFangs)` for the GameTests. `SCAN_HALF_HEIGHT` is removed: `grep -rn SCAN_HALF_HEIGHT src` finds only `Fangs.java` (e3's range comment is rewritten in Step 1(e)). `FangSquare.Cell`, `CELL_COUNT` and `cells()` are removed: after this task nothing reads them (`grep -rn "CELL_COUNT\|\.cells()\|FangSquare.Cell" src` must find nothing).

- [ ] **Step 1: Update the fang GameTests to the new behavior**

(They compile only once Step 2 lands; there is no red run for GameTests.) In `FangGameTests.java`:

(a) Class Javadoc: replace the whole comment with

```java
/**
 * Fang GameTests for Strength of Evoking (spec §9, table E1–E7, E13–E18, E19 for the real tick subscriber, E21 for
 * the request codec and E22 for the server stop) and Evoking II Part 1 (the {@code fangs_ii_*} tests: fangs only under
 * targets, the 14-down scan and the chase fang), driven through the public {@link EvokingCaster#request}. A mock
 * player is never ticked by the server (docs/api-notes.md §19), so the tests drive the caster with {@link #step}
 * inside each delayed callback, and E19 posts the player-tick event itself. Every test that places fangs registers
 * with padding 13 and {@link #PADDED_MAX_TICKS} (the body's 200 ticks plus the wait for the padded chunks; its padded
 * box is cleared to air, so {@link #floor} lays stone under the square). The caster stands at {@link #FEET}, the
 * structure's middle; targets are pigs or a ravager (zombies burn in daylight; a calm iron golem is an ally).
 */
```

(b) Add to `registerAll()` after the `fangs_e24_pvp_off_notice` line:

```java
        addPadded(SHARED, "fangs_ii_footprints", FangGameTests::iiFootprints);
        addPadded(SHARED, "fangs_ii_scan_window", FangGameTests::iiScanWindow);
        addPadded(SHARED, "fangs_ii_floating_targets", FangGameTests::iiFloatingTargets);
        addPadded(SHARED, "fangs_ii_chase", FangGameTests::iiChase);
        addPadded(SHARED, "fangs_ii_chase_once_per_target", FangGameTests::iiChaseOncePerTarget);
        addPadded(SHARED, "fangs_ii_chase_range", FangGameTests::iiChaseRange);
        addPadded(SHARED, "fangs_ii_chase_record_leaves_with_the_fang", FangGameTests::iiChaseRecordLeaves);
```

(c) `e1CarpetNorth`: the pig stands on the carpet cell, and one fang rises there. Replace the whole method:

```java
    /**
     * When a caster facing north presses the key with a pig on a carpet in the square, should the cast start, the
     * casting effect sync its particle, and one warmup later exactly one fang stand under the pig, on the carpet's
     * collision top, owned by the caster, with no warmup delay.
     *
     * <p>Method under test: {@link EvokingCaster#request}
     */
    static void e1CarpetNorth(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        BlockPos carpet = FEET.offset(3, 0, -4); // on the floor layer: its fang stands on the carpet's collision top
        helper.setBlock(carpet, Blocks.MOSS_CARPET);
        ServerPlayer player = caster(helper, NORTH);
        pig(helper, carpet);

        // Act & Assert
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        MobEffectInstance casting = requireEffect(helper, player, ModEffects.CASTING_FANGS);
        helper.assertValueEqual(casting.getDuration(), EvokerSpell.FANGS.castingTicks(), "casting ticks");
        helper.assertTrue(casting.isVisible(), "visible: the particle syncs");
        helper.assertFalse(casting.showIcon(), "no HUD icon");
        helper.assertTrue(fangs(helper).isEmpty(), "no fangs before the warmup");
        // ChunkMap's sync pass (updateDataBeforeSync) refreshes the list early in the next tick; 2, not 1, for margin.
        helper.runAfterDelay(
                2,
                () -> helper.assertTrue(
                        player.getEntityData()
                                .get(LivingEntity.DATA_EFFECT_PARTICLES)
                                .contains(ModParticles.CASTING_FANGS.get()),
                        "the synced particle list carries the casting particle"));
        helper.runAfterDelay(WARMUP, () -> {
            step(player); // the perform is due now: the fang is placed by this call and has not ticked yet
            List<EvokerFangs> fangs = fangs(helper);
            helper.assertValueEqual(fangs.size(), 1, "one fang, under the pig");
            EvokerFangs fang = fangs.get(0);
            assertInside(helper, fang, -6, 6, -12, 0);
            helper.assertValueEqual(fang.blockPosition(), helper.absolutePos(carpet), "the fang is on the pig's block");
            helper.assertTrue(
                    fang.getOwner() != null && fang.getOwner().getUUID().equals(player.getUUID()),
                    "the caster owns the fang");
            helper.assertTrue(
                    Math.abs(fang.getY() - (helper.absolutePos(carpet).getY() + 1.0 / 16.0)) < 1.0E-6,
                    "the fang stands at the carpet's collision top, one sixteenth up: " + fang.getY());
            helper.assertValueEqual(savedInt(fang, "Warmup"), 0, "no ripple: the fang has no warmup"); // not ticked yet
            helper.succeed();
        });
    }
```

(d) `e2CornerNorthEast`: the Javadoc's first sentence becomes "When a caster facing north-east presses the key with a target on the square's far corner and then walks on, should the one fang stand on the corner cell taken at the press, not one from the live position." The Assert becomes

```java
            // Assert
            List<EvokerFangs> fangs = fangs(helper);
            helper.assertValueEqual(fangs.size(), 1, "one fang, under the pig on the corner");
            fangs.forEach(fang -> assertInside(helper, fang, 0, 12, -12, 0));
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, FEET.offset(12, 0, -12));
            helper.succeed();
```

(e) `e3NoTargetsNotice`: the window is now `[feet − 14, feet + 7)`. Replace the three lines from `pig(helper, FEET.offset(0, 8, -3));` through `pig(helper, FEET.offset(0, -8, -3));` with

```java
        pig(helper, FEET.offset(0, 8, -3)); // above the window: y in [feet-14, feet+7)
        helper.assertTrue(
                helper.absolutePos(FEET).getY() - 17 > helper.getLevel().getMinY(),
                "setup: the world has room 17 blocks under the caster");
        helper.setBlock(FEET.offset(0, -17, -3), Blocks.STONE); // a floor for the pig below the window
        pig(helper, FEET.offset(0, -16, -3)); // below the window: its top (0.9 tall) is under feet-14
```

(f) `e7BusyAndCooling` is edited by Task 6 (its golem target becomes a pig); do not touch it here.

(g) `e17UnevenFloor`: three pigs, three fangs, each on its own floor. Replace the whole method:

```java
    /**
     * When the square has a pig on a one-block pillar, a pig at the bottom of a two-deep hole and a pig at the bottom
     * of a five-deep hole (all within the 14-down scan), should each get one fang at its own floor height and no other
     * cell get one.
     *
     * <p>Method under test: {@link Fangs#perform}
     */
    static void e17UnevenFloor(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        BlockPos pillar = FEET.offset(2, 0, -3);
        BlockPos shallowHole = FEET.offset(-2, 0, -3);
        BlockPos deepHole = FEET.offset(0, 0, -6);
        helper.setBlock(pillar, Blocks.STONE);
        helper.setBlock(shallowHole.below(), Blocks.AIR);
        helper.setBlock(shallowHole.below(3), Blocks.STONE);
        helper.setBlock(deepHole.below(), Blocks.AIR);
        helper.setBlock(deepHole.below(6), Blocks.STONE);
        ServerPlayer player = caster(helper, NORTH);
        pig(helper, pillar.above());
        pig(helper, shallowHole.below(2));
        pig(helper, deepHole.below(5));

        // Act
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        helper.runAfterDelay(WARMUP, () -> {
            step(player);

            // Assert
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, pillar.above());
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, shallowHole.below(2));
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, deepHole.below(5));
            helper.assertValueEqual(fangs(helper).size(), 3, "one fang per pig and none elsewhere");
            helper.succeed();
        });
    }
```

(h) `e19PlayerTickEvent`: the assertion becomes `helper.assertValueEqual(fangs(helper).size(), 1, "one fang, under the pig");` and the Javadoc's "one fang per cell" becomes "one fang under the pig".

(i) `serverStopDropsTheCast`: also prove the stop clears a chase record. Replace the whole method:

```java
    /**
     * When the server stops between the press and the perform, should the cast be dropped: no fangs once the
     * warmup has passed (the map is transient, like {@code TrailLayer}'s trackers). When it stops after a fang with a
     * chase record was placed, should the record be gone too.
     *
     * <p>Method under test: {@link EvokingCaster#onServerStopped}, and {@link Fangs#onServerStopped}
     */
    static void serverStopDropsTheCast(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        pig(helper, FEET.north(5));
        ServerStoppedEvent stopped = new ServerStoppedEvent(helper.getLevel().getServer());
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");

        // Act
        EvokingCaster.onServerStopped(stopped);

        // Assert
        helper.runAfterDelay(WARMUP + 2, () -> {
            step(player); // past the perform time, but the entry is gone: nothing to perform
            helper.assertTrue(fangs(helper).isEmpty(), "no fangs after the server stopped");
        });
        // Arrange: a second cast that performs, so a fang carries a chase record
        helper.runAfterDelay(WARMUP + EvokerSpell.FANGS.intervalTicks(), () -> {
            step(player);
            helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "second cast");
        });
        helper.runAfterDelay(2 * WARMUP + EvokerSpell.FANGS.intervalTicks(), () -> {
            step(player);
            List<EvokerFangs> fangs = fangs(helper);
            helper.assertValueEqual(fangs.size(), 1, "the second cast placed its fang");
            helper.assertTrue(Fangs.hasChaseRecord(fangs.get(0)), "setup: the fang has a chase record");

            // Act
            Fangs.onServerStopped(stopped);

            // Assert
            helper.assertFalse(Fangs.hasChaseRecord(fangs.get(0)), "the stop dropped the chase record");
            helper.succeed();
        });
    }
```

(j) New tests, after `e24PvpOffNotice`:

```java
    /**
     * When the square holds a ravager (1.95 wide, on a block center), a pig, two pigs stacked in one column on one
     * floor, and two pigs in one column on different floors (one on the ground, one on a ledge three blocks up),
     * should one fang rise under every block the ravager covers (3×3), one under the lone pig, one for the stacked
     * pair (same spot: duplicates collapse), two for the ledge pair (different floors), and none on an empty cell:
     * thirteen in all.
     *
     * <p>Method under test: {@link Fangs#snapshot}, through {@link EvokingCaster#request}
     */
    static void iiFootprints(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        BlockPos ravagerBlock = FEET.offset(0, 0, -6);
        Ravager ravager = helper.spawn(EntityTypes.RAVAGER, ravagerBlock);
        ravager.setNoAi(true);
        BlockPos lonePig = FEET.offset(4, 0, -3);
        pig(helper, lonePig);
        BlockPos stacked = FEET.offset(-4, 0, -3);
        pig(helper, stacked);
        pig(helper, stacked.above()); // its box covers the same column and its floor search finds the same floor
        BlockPos ground = FEET.offset(-4, 0, -8);
        BlockPos ledge = ground.above(2); // a stone ledge two blocks over the ground pig's head
        helper.setBlock(ledge, Blocks.STONE);
        pig(helper, ground);
        pig(helper, ledge.above()); // standing on the ledge: a different floor in the same column

        // Act
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        helper.runAfterDelay(WARMUP, () -> {
            step(player);

            // Assert
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, ravagerBlock.offset(dx, 0, dz));
                }
            }
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, lonePig);
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, stacked);
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, ground);
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, ledge.above());
            helper.assertValueEqual(
                    fangs(helper).size(), 13, "9 under the ravager, 1 under the pig, 1 for the stack, 2 for the ledge");
            ravager.discard();
            helper.succeed();
        });
    }

    /**
     * When one pig stands 14 blocks below the caster and one 6 above (the last blocks inside the scan), while one
     * stands 15 below and one 7 above (the first blocks outside), should the two outside ones alone give the
     * no-targets answer, and with the two inside ones added the cast start and place a fang under each, at its own
     * height.
     *
     * <p>Method under test: {@link Fangs#snapshot}, through {@link EvokingCaster#request}
     */
    static void iiScanWindow(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        helper.assertTrue(
                helper.absolutePos(FEET).getY() - 17 > helper.getLevel().getMinY(),
                "setup: the world has room 17 blocks under the caster");
        ServerPlayer player = caster(helper, NORTH);
        BlockPos tooDeep = FEET.offset(-3, -15, -3);
        BlockPos tooHigh = FEET.offset(3, 7, -3);
        helper.setBlock(tooDeep.below(), Blocks.STONE);
        helper.setBlock(tooHigh.below(), Blocks.STONE);
        pig(helper, tooDeep); // its top is under feet-14
        pig(helper, tooHigh); // its feet are at feet+7, the window's open end

        // Act & Assert: the two outside the window
        helper.assertValueEqual(
                EvokingCaster.request(player, EvokerSpell.FANGS), Request.NO_TARGETS, "15 down and 7 up are outside");

        // Arrange: the two inside
        BlockPos deep = FEET.offset(-3, -14, -5);
        BlockPos high = FEET.offset(3, 6, -5);
        helper.setBlock(deep.below(), Blocks.STONE);
        helper.setBlock(high.below(), Blocks.STONE);
        pig(helper, deep);
        pig(helper, high);

        // Act & Assert: the cast finds them and the fangs stand at their heights
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "14 down and 6 up");
        helper.runAfterDelay(WARMUP, () -> {
            step(player);
            List<EvokerFangs> fangs = helper.getLevel()
                    .getEntitiesOfClass(EvokerFangs.class, new AABB(helper.absolutePos(FEET)).inflate(14.0, 20.0, 14.0));
            helper.assertValueEqual(fangs.size(), 2, "one fang per pig inside the window");
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, deep);
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, high);
            helper.succeed();
        });
    }

    /**
     * When a pig hangs two blocks over the floor (inside the floor window) and another three blocks over it (outside),
     * should the first get a fang on the floor under it and the second none: the floor search runs from one block
     * above a target's feet to two below.
     *
     * <p>Method under test: {@link Fangs#snapshot}
     */
    static void iiFloatingTargets(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        BlockPos twoUp = FEET.offset(-4, 2, -3); // feet two blocks over the floor at FEET.y
        BlockPos threeUp = FEET.offset(4, 3, -3);
        pig(helper, twoUp); // a no-AI pig does not fall
        pig(helper, threeUp);

        // Act
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        helper.runAfterDelay(WARMUP, () -> {
            step(player);

            // Assert
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, FEET.offset(-4, 0, -3));
            helper.assertEntityNotPresent(EntityTypes.EVOKER_FANGS, FEET.offset(4, 0, -3));
            helper.assertValueEqual(fangs(helper).size(), 1, "only the pig within two blocks of a floor gets a fang");
            helper.succeed();
        });
    }

    /**
     * When a pig steps three blocks east after its fang is placed and before it bites, should one chase fang rise under
     * the pig's new block on the bite tick, a second move give no further fang, and a pig that became the caster's
     * ally after the press get no chase.
     *
     * <p>Method under test: {@link Fangs#onEntityTick}
     */
    static void iiChase(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        BlockPos start = FEET.north(5);
        Pig runner = pig(helper, start);
        BlockPos allyStart = FEET.offset(-3, 0, -3);
        Pig turnedAlly = pig(helper, allyStart);
        float runnerHealth = runner.getHealth();

        // Act
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        helper.runAfterDelay(WARMUP, () -> {
            step(player);
            helper.assertValueEqual(fangs(helper).size(), 2, "one fang under each pig");
            // Both pigs move before the bite (the fangs' 8th tick); one has become an ally meanwhile.
            Vec3 moved = helper.absoluteVec(new Vec3(start.getX() + 3.5, start.getY(), start.getZ() + 0.5));
            runner.teleportTo(moved.x, moved.y, moved.z);
            turnedAlly.setData(AllyAttachments.SUMMONER, player.getUUID());
            Vec3 allyMoved = helper.absoluteVec(new Vec3(allyStart.getX() + 2.5, allyStart.getY(), allyStart.getZ() + 0.5));
            turnedAlly.teleportTo(allyMoved.x, allyMoved.y, allyMoved.z);
        });
        helper.runAfterDelay(WARMUP + Fangs.BITE_TICK + 2, () -> {
            // Assert: one chase fang, under the runner's new block, none for the ally
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, start.east(3));
            helper.assertEntityNotPresent(EntityTypes.EVOKER_FANGS, allyStart.east(2));
            helper.assertValueEqual(fangs(helper).size(), 3, "the two first fangs and one chase fang");
            // Act: the runner moves again before the chase fang bites
            Vec3 again = helper.absoluteVec(new Vec3(start.getX() + 5.5, start.getY(), start.getZ() + 0.5));
            runner.teleportTo(again.x, again.y, again.z);
        });
        helper.runAfterDelay(WARMUP + 2 * Fangs.BITE_TICK + 4, () -> {
            // Assert: no second chase; the first fangs may have died by now, so check only the new cell
            helper.assertEntityNotPresent(EntityTypes.EVOKER_FANGS, start.east(5));
            helper.assertValueEqual(runner.getHealth(), runnerHealth, "a chase fang never chases; the runner escaped");
            helper.succeed();
        });
    }

    /**
     * When a ravager with nine fangs under it steps four blocks east before they bite, should exactly nine chase fangs
     * rise under its new footprint (the nine first fangs settle one chase, not nine); when a pig shifts within its own
     * block, should no chase fang rise (it still stands on its column).
     *
     * <p>Method under test: {@link Fangs#onEntityTick}
     */
    static void iiChaseOncePerTarget(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        BlockPos ravagerBlock = FEET.offset(-2, 0, -6);
        Ravager ravager = helper.spawn(EntityTypes.RAVAGER, ravagerBlock);
        ravager.setNoAi(true);
        BlockPos pigBlock = FEET.offset(4, 0, -3);
        Pig shifter = pig(helper, pigBlock);
        whenDone(helper, ravager::discard);

        // Act
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        helper.runAfterDelay(WARMUP, () -> {
            step(player);
            helper.assertValueEqual(fangs(helper).size(), 10, "nine under the ravager, one under the pig");
            Vec3 east = helper.absoluteVec(
                    new Vec3(ravagerBlock.getX() + 4.5, ravagerBlock.getY(), ravagerBlock.getZ() + 0.5));
            ravager.teleportTo(east.x, east.y, east.z);
            Vec3 shifted = helper.absoluteVec(new Vec3(pigBlock.getX() + 0.8, pigBlock.getY(), pigBlock.getZ() + 0.2));
            shifter.teleportTo(shifted.x, shifted.y, shifted.z); // still inside its block
        });
        helper.runAfterDelay(WARMUP + Fangs.BITE_TICK + 2, () -> {
            // Assert
            for (int dx = 3; dx <= 5; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, ravagerBlock.offset(dx, 0, dz));
                }
            }
            helper.assertValueEqual(fangs(helper).size(), 19, "ten first fangs and exactly nine chase fangs");
            helper.succeed();
        });
    }

    /**
     * When the caster has walked more than 16 blocks from a pig by the time its fang bites, and the pig has stepped off
     * its block, should no chase fang rise.
     *
     * <p>Method under test: {@link Fangs#onEntityTick}
     */
    static void iiChaseRange(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        BlockPos start = FEET.north(5);
        Pig runner = pig(helper, start);

        // Act
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        helper.runAfterDelay(WARMUP, () -> {
            step(player);
            helper.assertValueEqual(fangs(helper).size(), 1, "one fang under the pig");
            // The caster walks 12 blocks south (still inside the padded, ticking box): 17 blocks from the pig.
            MockPlayers.moveTo(helper, player, STAND.add(0, 0, 12), NORTH);
            Vec3 moved = helper.absoluteVec(new Vec3(start.getX() + 2.5, start.getY(), start.getZ() + 0.5));
            runner.teleportTo(moved.x, moved.y, moved.z);
            helper.assertTrue(player.distanceTo(runner) > Fangs.CHASE_RANGE, "setup: the pig is out of chase range");
        });
        helper.runAfterDelay(WARMUP + Fangs.BITE_TICK + 2, () -> {
            // Assert
            helper.assertEntityNotPresent(EntityTypes.EVOKER_FANGS, start.east(2));
            helper.assertValueEqual(fangs(helper).size(), 1, "no chase fang beyond 16 blocks");
            helper.succeed();
        });
    }

    /**
     * When a fang with a chase record bites and then dies of age, should its record exist while it lives and be gone
     * once the fang is removed.
     *
     * <p>Method under test: {@link Fangs#onEntityLeave}
     */
    static void iiChaseRecordLeaves(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        pig(helper, FEET.north(5));
        EvokerFangs[] placed = new EvokerFangs[1];

        // Act
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        helper.runAfterDelay(WARMUP, () -> {
            step(player);
            List<EvokerFangs> fangs = fangs(helper);
            helper.assertValueEqual(fangs.size(), 1, "one fang");
            placed[0] = fangs.get(0);
            helper.assertTrue(Fangs.hasChaseRecord(placed[0]), "the fang has a chase record while it lives");
        });
        // A fang with delay 0 bites on its 8th tick and discards itself on its 23rd (docs/api-notes.md §20).
        helper.runAfterDelay(WARMUP + 40, () -> {
            // Assert
            helper.assertTrue(placed[0].isRemoved(), "the fang died of age");
            helper.assertFalse(Fangs.hasChaseRecord(placed[0]), "its record left with it");
            helper.succeed();
        });
    }
```

Add the imports `io.github.willrieske.spellcraft.evoking.Fangs`, `io.github.willrieske.spellcraft.spell.AllyAttachments` and `net.minecraft.world.entity.monster.Ravager`; remove the now-unused `io.github.willrieske.spellcraft.spell.rules.FangSquare` import only if nothing in the file still uses `FangSquare` (`floor` does, so keep it). Spotless sorts imports.

Timing note for the implementer: `runAfterDelay(WARMUP)` places the fangs during that tick's GameTest phase, which runs after every level has ticked (api-notes §20), so the fangs tick for the first time on the next server tick and bite on tick `WARMUP + 9` relative to the request at the latest; `+ 10` is the earliest safe check, and a chase fang placed then bites around `+ 17`. In `iiChase` the runner is moved again at `+ 10`, before the chase fang bites, so its health never changes; that is the point of the last assertion.

- [ ] **Step 2: Remove the ripple from `FangSquare` and its tests**

In `FangSquare.java`: delete the `CELL_COUNT` constant with its Javadoc (lines 22–23), the `Cell` record with its Javadoc (lines 25–32) and the `cells()` method with its Javadoc (lines 85–98); drop the now-unused imports `java.util.ArrayList`, `java.util.Collections`, `java.util.List`. In the class Javadoc, change "The 13×13 block area a fang cast covers (spec §5)" to "The 13×13 block area a fang cast scans for targets (Evoking spec §5, Evoking II §2.2)". `HALF`, `SIZE`, `of`, `minDx`…`maxDz` and `contains` stay.

In `FangSquareTest.java`: delete the `@Nested class Cells { … }` block (both tests in it) and the imports `io.github.willrieske.spellcraft.spell.rules.FangSquare.Cell` and `java.util.List`. In `FangSquarePropertiesTest.java`: delete the `@Group class Cells { … }` block and the same two imports.

- [ ] **Step 3: Rewrite `Fangs.java`**

```java
package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.spell.Allies;
import io.github.willrieske.spellcraft.spell.rules.FangFootprints;
import io.github.willrieske.spellcraft.spell.rules.FangFootprints.Column;
import io.github.willrieske.spellcraft.spell.rules.FangSquare;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * The fang cast (Evoking spec §5 as amended by Evoking II §2.2): at the press, the square ahead of the caster, the
 * block columns every target in it covers, and the floor under each; one warmup later, a vanilla {@link EvokerFangs}
 * on each of those spots, placed the way the evoker places its own. A fang remembers its target: when it bites and the
 * target has stepped off its columns, one more fang (a chase fang, which chases nothing itself) rises under the
 * target's new footprint. Also the guard that keeps a player's fangs off the player's summons (spec §3).
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class Fangs {
    /** How far below the caster's feet the target scan reaches: fourteen blocks. */
    public static final int SCAN_DEPTH = 14;

    /** How far above the caster's feet the target scan reaches: six blocks. */
    public static final int SCAN_HEIGHT = 6;

    /** A chase fang follows a target at most this far from the caster. */
    public static final double CHASE_RANGE = 16.0;

    /**
     * The fang's tick on which vanilla bites: {@code EvokerFangs.tick} counts {@code warmupDelayTicks} down once per
     * tick and bites at −8, and {@code Entity.commonTick} bumps {@code tickCount} before {@code tick()}, so a fang
     * placed with delay 0 bites on the tick that leaves {@code tickCount == 8} (docs/api-notes.md §20).
     */
    public static final int BITE_TICK = 8;

    /** The evoker's floor window around one target's feet: one block up, two down. */
    private static final int FLOOR_UP = 1;

    private static final int FLOOR_DOWN = 2;

    /**
     * Where one fang goes: a column and the floor height found under it at the press.
     *
     * @param column the block column
     * @param y the fang's y: the floor block's top, raised by a carpet or slab in the cell
     */
    public record Spot(Column column, double y) {}

    /**
     * One target's footprint at the press.
     *
     * @param target the target's UUID
     * @param columns every column its box covered inside the square, before any dedupe (the chase reads it)
     * @param spots the spots this target gets fangs on: its columns with a floor, minus the spots an earlier target
     *     already claimed
     */
    public record Footprint(UUID target, List<Column> columns, List<Spot> spots) {}

    /**
     * What the press fixed (spec §2.2): the fangs are placed from this, not from where anything has walked to.
     *
     * @param dimension the level the caster stood in
     * @param feet the caster's block; every fang of the cast faces away from it
     * @param square the square ahead
     * @param footprints one per target that claimed at least one spot
     */
    public record Snapshot(ResourceKey<Level> dimension, BlockPos feet, FangSquare square, List<Footprint> footprints) {}

    /** The target a cast's fangs were placed for; the fangs of one footprint share one record. */
    private static final class Chase {
        final UUID target;
        final UUID caster;
        final BlockPos feet;
        final Set<Column> columns;
        boolean done;

        Chase(UUID target, UUID caster, BlockPos feet, Set<Column> columns) {
            this.target = target;
            this.caster = caster;
            this.feet = feet;
            this.columns = columns;
        }
    }

    /** Fang entity id → its chase record. Transient: entries leave with their fang and the server. */
    private static final Map<Integer, Chase> CHASES = new HashMap<>();

    private Fangs() {}

    /**
     * The snapshot for a press by {@code caster}, or empty when no target in the square claims a spot: the scan box is
     * the square's columns from {@link #SCAN_DEPTH} below the caster's feet to {@link #SCAN_HEIGHT} above. Each target's
     * columns are the ones its bounding box covers ({@link FangFootprints#columns}) inside the square, the caster's
     * column excluded ({@link FangFootprints#inSquare}); each column with a floor in the window around the target's
     * feet is a spot, and a spot two targets share goes to the first (one fang per spot). A target over no floor, or
     * whose spots are all taken, gets nothing.
     */
    static Optional<Snapshot> snapshot(ServerLevel level, ServerPlayer caster) {
        BlockPos feet = caster.blockPosition();
        FangSquare square = FangSquare.of(caster.getYRot());
        AABB box = new AABB(
                feet.getX() + square.minDx(),
                feet.getY() - SCAN_DEPTH,
                feet.getZ() + square.minDz(),
                feet.getX() + square.maxDx() + 1,
                feet.getY() + SCAN_HEIGHT + 1,
                feet.getZ() + square.maxDz() + 1);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box, e -> isTarget(caster, e));
        Set<Spot> claimed = new HashSet<>();
        List<Footprint> footprints = new ArrayList<>();
        for (LivingEntity target : targets) {
            List<Column> columns = FangFootprints.inSquare(columnsOf(target), square, feet.getX(), feet.getZ());
            int feetY = target.blockPosition().getY();
            List<Spot> spots = new ArrayList<>();
            for (Column column : columns) {
                OptionalDouble y = findFloor(level, column.x(), column.z(), feetY + FLOOR_UP, feetY - FLOOR_DOWN);
                if (y.isPresent()) {
                    Spot spot = new Spot(column, y.getAsDouble());
                    if (claimed.add(spot)) {
                        spots.add(spot);
                    }
                }
            }
            if (!spots.isEmpty()) {
                footprints.add(new Footprint(target.getUUID(), List.copyOf(columns), List.copyOf(spots)));
            }
        }
        if (footprints.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Snapshot(level.dimension(), feet, square, List.copyOf(footprints)));
    }

    /** The block columns {@code entity}'s bounding box covers right now. */
    static List<Column> columnsOf(Entity entity) {
        AABB box = entity.getBoundingBox();
        return FangFootprints.columns(box.minX, box.minZ, box.maxX, box.maxZ);
    }

    /**
     * A target (spec glossary): an enemy a mob could attack, attackable, and neither the caster nor an ally. A player
     * counts only when the caster may harm that player ({@link ServerPlayer#canHarmPlayer}: the {@code pvp} game rule;
     * teammates are already spared by the ally rule), so fangs never start a cast whose bites vanilla would refuse.
     */
    // Entities compare by id (docs/api-notes.md §8); this asks "is it the caster".
    @SuppressWarnings("ReferenceEquality")
    static boolean isTarget(ServerPlayer caster, LivingEntity entity) {
        return entity != caster
                && entity.canBeSeenAsEnemy()
                && entity.attackable()
                && !Allies.isAllyOf(caster, entity)
                && (!(entity instanceof Player other) || caster.canHarmPlayer(other));
    }

    /**
     * Places the fangs of {@code snapshot}: one per spot, facing away from the caster's block at the press, no warmup
     * delay, each recorded for the chase. Nothing happens in another dimension than the snapshot's. Spots whose chunk
     * is not entity-ticking get none ({@link ServerLevel#isPositionEntityTicking}; no chunk is loaded or waited on).
     *
     * @param level the level to place the fangs in
     * @param caster the owner of the fangs
     * @param snapshot the footprints taken when the cast started
     * @return true when the snapshot's level matched (so the cast sound plays), even if no spot's chunk was ticking
     */
    static boolean perform(ServerLevel level, ServerPlayer caster, Snapshot snapshot) {
        if (!level.dimension().equals(snapshot.dimension())) {
            return false;
        }
        for (Footprint footprint : snapshot.footprints()) {
            Chase chase = new Chase(
                    footprint.target(), caster.getUUID(), snapshot.feet(), Set.copyOf(footprint.columns()));
            for (Spot spot : footprint.spots()) {
                place(level, caster, snapshot.feet(), spot).ifPresent(fang -> CHASES.put(fang.getId(), chase));
            }
        }
        return true;
    }

    /**
     * One fang on {@code spot}, delay 0, facing away from {@code feet}. Empty when the spot's chunk is not
     * entity-ticking: a fang there would be saved with the chunk and bite later.
     */
    private static Optional<EvokerFangs> place(ServerLevel level, ServerPlayer caster, BlockPos feet, Spot spot) {
        int x = spot.column().x();
        int z = spot.column().z();
        if (!level.isPositionEntityTicking(new BlockPos(x, Mth.floor(spot.y()), z))) {
            return Optional.empty();
        }
        float angle = (float) Mth.atan2(z - feet.getZ(), x - feet.getX());
        EvokerFangs fang = new EvokerFangs(level, x + 0.5, spot.y(), z + 0.5, angle, 0, caster);
        level.addFreshEntity(fang);
        level.gameEvent(GameEvent.ENTITY_PLACE, new Vec3(x + 0.5, spot.y(), z + 0.5), GameEvent.Context.of(caster));
        return Optional.of(fang);
    }

    /**
     * The evoker's floor search ({@code Evoker.createSpellEntity}): from {@code top} down to {@code bottom}, the first
     * cell whose block below has a sturdy top face, raised by the cell's own collision top (a slab, a carpet).
     */
    static OptionalDouble findFloor(ServerLevel level, int x, int z, int top, int bottom) {
        BlockPos pos = new BlockPos(x, top, z);
        while (pos.getY() >= bottom) {
            BlockPos below = pos.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                double topOffset = 0.0;
                if (!level.isEmptyBlock(pos)) {
                    VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
                    if (!shape.isEmpty()) {
                        topOffset = shape.max(Direction.Axis.Y);
                    }
                }
                return OptionalDouble.of(pos.getY() + topOffset);
            }
            pos = below;
        }
        return OptionalDouble.empty();
    }

    /**
     * The chase (spec §2.2): on a recorded fang's bite tick, if its target is alive, within {@link #CHASE_RANGE} of the
     * caster, no ally, and standing on none of the columns of its own footprint at the press, one fang rises under
     * each column of the target's current footprint that has a floor, with no record. One chase per target per cast:
     * the first of a footprint's fangs to reach the bite tick settles it, whether or not a fang is placed.
     *
     * @param event the post-tick event for any entity
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (CHASES.isEmpty()
                || !(event.getEntity() instanceof EvokerFangs fang)
                || fang.tickCount != BITE_TICK
                || !(fang.level() instanceof ServerLevel level)) {
            return;
        }
        Chase chase = CHASES.get(fang.getId());
        if (chase == null || chase.done) {
            return;
        }
        chase.done = true;
        if (!(level.getPlayerByUUID(chase.caster) instanceof ServerPlayer caster)
                || !(level.getEntity(chase.target) instanceof LivingEntity target)
                || !target.isAlive()
                || caster.distanceTo(target) > CHASE_RANGE
                || Allies.isAllyOf(caster, target)) {
            return;
        }
        List<Column> now = columnsOf(target);
        if (now.stream().anyMatch(chase.columns::contains)) {
            return; // still on its fangs: they bit, or will
        }
        int feetY = target.blockPosition().getY();
        for (Column column : now) {
            findFloor(level, column.x(), column.z(), feetY + FLOOR_UP, feetY - FLOOR_DOWN)
                    .ifPresent(y -> place(level, caster, chase.feet, new Spot(column, y))); // no record: never chases
        }
    }

    /**
     * Drops a fang's chase record when the fang leaves the level: it dies of age on its 23rd tick, 15 ticks after the
     * bite, or its chunk stops being accessible (docs/api-notes.md §20; a fang unloaded before its bite tick never
     * chases, accepted).
     *
     * @param event the leave event for any entity
     */
    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof EvokerFangs fang) {
            CHASES.remove(fang.getId());
        }
    }

    /**
     * Drops every chase record when the server stops (single-player loads one server per world in the same JVM).
     *
     * @param event the server-stopped event
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CHASES.clear();
    }

    /**
     * Whether {@code fang} carries a chase record. For the GameTests.
     *
     * @param fang a fang
     * @return true while the fang's record exists
     */
    public static boolean hasChaseRecord(EvokerFangs fang) {
        return CHASES.containsKey(fang.getId());
    }

    /**
     * A player's fangs never bite the player's allies (spec §3). Vanilla already skips {@code isAlliedTo} allies; this
     * adds the summoner path (a vex the player called is no vanilla ally of the player). A fang whose caster has since
     * logged out has no owner and bites like a vanilla ownerless fang; accepted (a one-second window).
     *
     * @param event the incoming-damage event for any living entity
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (source.getDirectEntity() instanceof EvokerFangs
                && source.getEntity() instanceof Player owner
                && Allies.isAllyOf(owner, event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
```

`EvokingCaster` needs no change: it stores `Optional<Fangs.Snapshot>` and calls `snapshot`/`perform` with the same signatures.

- [ ] **Step 4: Build (unit tests included)**

Run: `./gradlew spotlessApply --console=plain`
Run: `./gradlew build --console=plain > build/build.log 2>&1; echo "exit=$?"; tail -40 build/build.log`
Expected: `exit=0` (Error Prone may flag an unused import; fix it). The `FangSquare` tests still pass without their `Cells` groups.

- [ ] **Step 5: Run the GameTests (background; minutes)**

Run: `./gradlew runGameTestServer --console=plain > build/runGameTestServer.log 2>&1; echo "exit=$?"; grep -n "fangs_\|FAILED\|passed\|failed" build/runGameTestServer.log | tail -40`
Expected: `exit=0`; every test passes, the seven `fangs_ii_*` included. (`fangs_e7_busy_and_cooling` still uses a golem until Task 6 and passes here, since golems are not allies yet.)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/willrieske/spellcraft/evoking/Fangs.java src/main/java/io/github/willrieske/spellcraft/spell/rules/FangSquare.java src/test/java/io/github/willrieske/spellcraft/spell/rules/FangSquareTest.java src/test/java/io/github/willrieske/spellcraft/spell/rules/FangSquarePropertiesTest.java src/main/java/io/github/willrieske/spellcraft/gametest/FangGameTests.java
```

```bash
git commit -m "feat(evoking): fangs rise only under targets, 14 blocks down, with one chase fang per target"
```

Then `git log -1 --oneline` shows the commit.

---

### Task 6: Villagers, traders and golems as allies; the trail spares golems

**Files:**

- Modify: `src/main/java/io/github/willrieske/spellcraft/spell/Allies.java` (whole file)
- Modify: `src/main/java/io/github/willrieske/spellcraft/trailing/TrailFires.java:372-397` (the `isSpared` Javadoc and body)
- Modify: `src/main/java/io/github/willrieske/spellcraft/trailing/TrailFireBlock.java:90` (one Javadoc line)
- Modify: `src/main/java/io/github/willrieske/spellcraft/gametest/FangGameTests.java` (`e7BusyAndCooling`: a pig instead of the golem)
- Modify: `src/main/java/io/github/willrieske/spellcraft/gametest/AlliesGameTests.java` (registration + a7)
- Create: `src/main/java/io/github/willrieske/spellcraft/gametest/GolemGameTests.java`
- Modify: `src/main/java/io/github/willrieske/spellcraft/gametest/SpellcraftGameTests.java:46-60` (the static block)

**Interfaces:**

- Consumes: `AllyAttachments.SUMMONER` (existing); `TrailGameTests.A/C/D/floors/trailingPlayer/teleport/light/stepInto/assertSpared/assertBurned/forget` (existing package-private statics).
- Produces: `Allies.isAllyOf` and `mayHaveAllies` with the two new kinds; `public static boolean isMadAt(IronGolem golem, Player owner)`; `GolemGameTests` with `registerAll()`, `SPOT`, `golem(helper, relative)` and `anger(golem, player)`, which Task 7 extends.

- [ ] **Step 1: Write the GameTests (they compile only with Step 2's code)**

(a) `FangGameTests.e7BusyAndCooling`: a calm golem becomes an ally in this task, so it can no longer be the target. Replace

```java
        IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, FEET.north(5));
        golem.setNoAi(true);
```

with

```java
        pig(helper, FEET.north(5));
```

and remove the import `net.minecraft.world.entity.animal.golem.IronGolem` (nothing else in the file uses it).

(b) `AlliesGameTests.registerAll()`: add after the `allies_a5_strangers` line

```java
        SpellcraftGameTests.add(SHARED, "allies_a7_villagers_and_traders", AlliesGameTests::a7VillagersAndTraders);
```

and the test after `a5Strangers`:

```java
    /**
     * When a villager and a wandering trader stand near a player with no team, should both be allies and may have
     * allies, though neither has a team, an owner or a summoner mark; a zombie villager (a {@code Zombie}, not an
     * {@code AbstractVillager}) should not be an ally (Evoking II §2.3).
     *
     * <p>Method under test: {@link Allies#isAllyOf}, and {@link Allies#mayHaveAllies}
     */
    static void a7VillagersAndTraders(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        helper.assertTrue(player.getTeam() == null, "setup: the player is on no team");
        Villager villager = helper.spawn(EntityTypes.VILLAGER, SPOT);
        villager.setNoAi(true);
        WanderingTrader trader = helper.spawn(EntityTypes.WANDERING_TRADER, SPOT);
        trader.setNoAi(true);
        ZombieVillager zombieVillager = helper.spawn(EntityTypes.ZOMBIE_VILLAGER, SPOT);
        zombieVillager.setNoAi(true);
        helper.assertFalse(villager.hasData(AllyAttachments.SUMMONER), "setup: the villager carries no mark");
        helper.assertFalse(trader.hasData(AllyAttachments.SUMMONER), "setup: the trader carries no mark");

        // Act & Assert
        helper.assertTrue(Allies.isAllyOf(player, villager), "a villager is an ally");
        helper.assertTrue(Allies.isAllyOf(player, trader), "a wandering trader is an ally");
        helper.assertTrue(Allies.mayHaveAllies(villager), "a villager may have allies");
        helper.assertTrue(Allies.mayHaveAllies(trader), "a trader may have allies");
        helper.assertFalse(Allies.isAllyOf(player, zombieVillager), "a zombie villager is not an ally");
        zombieVillager.discard();
        helper.succeed();
    }
```

Imports: `net.minecraft.world.entity.npc.villager.Villager`, `net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader`, `net.minecraft.world.entity.monster.zombie.ZombieVillager`.

(c) New `GolemGameTests.java`:

```java
package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.A;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.C;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.D;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.assertBurned;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.assertSpared;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.floors;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.forget;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.light;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.stepInto;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.teleport;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.trailingPlayer;

import io.github.willrieske.spellcraft.spell.Allies;
import io.github.willrieske.spellcraft.trailing.TrailFires;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;

/**
 * Iron golems as allies, the trail's golem clause, golem mercy and forgiveness, and the rule that a golem never targets
 * a summon (Evoking II spec §2.3). Registered in {@link SpellcraftGameTests}.
 */
public final class GolemGameTests {
    static final BlockPos SPOT = new BlockPos(1, 1, 1);

    private GolemGameTests() {}

    static void registerAll() {
        SpellcraftGameTests.add(SHARED, "golems_ii_allies_unless_mad", GolemGameTests::alliesUnlessMad);
        SpellcraftGameTests.add(SHARED, "golems_ii_trail_spares_every_golem", GolemGameTests::trailSparesEveryGolem);
    }

    /**
     * A golem with no AI: no goals, so no target selection or movement. Its {@code aiStep} still runs, and with it the
     * anger upkeep ({@code updatePersistentAnger}, docs/api-notes.md §20), which is why {@link #anger} also starts the
     * anger timer.
     */
    static IronGolem golem(GameTestHelper helper, BlockPos relative) {
        IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, relative);
        golem.setNoAi(true);
        return golem;
    }

    /**
     * Makes {@code golem} mad at {@code player} the way vanilla does: target, persistent anger target and the anger
     * timer (without the timer the upkeep in {@code aiStep} would clear the anger on the next tick; Task 7's 35-tick
     * mercy test depends on this).
     */
    static void anger(IronGolem golem, ServerPlayer player) {
        golem.setTarget(player);
        golem.setPersistentAngerTarget(EntityReference.of(player));
        golem.startPersistentAngerTimer();
    }

    /**
     * When a calm golem stands near the player, should be an ally and may have allies; with the player as its target,
     * or with its persistent anger naming the player, should not be an ally; after it stops being angry, should be one
     * again.
     *
     * <p>Method under test: {@link Allies#isAllyOf}, {@link Allies#isMadAt}, and {@link Allies#mayHaveAllies}
     */
    static void alliesUnlessMad(GameTestHelper helper) {
        // Arrange
        ServerLevel level = helper.getLevel();
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        IronGolem golem = golem(helper, SPOT);

        // Act & Assert
        helper.assertTrue(Allies.isAllyOf(player, golem), "a calm golem is an ally");
        helper.assertTrue(Allies.mayHaveAllies(golem), "a golem may have allies");
        helper.assertFalse(Allies.isMadAt(golem, player), "a calm golem is not mad");
        golem.setTarget(player);
        helper.assertTrue(Allies.isMadAt(golem, player), "a golem targeting the player is mad");
        helper.assertFalse(Allies.isAllyOf(player, golem), "and not an ally");
        golem.setTarget(null);
        golem.setPersistentAngerTarget(EntityReference.of(player));
        golem.startPersistentAngerTimer();
        helper.assertTrue(golem.isAngryAt(player, level), "setup: vanilla calls it angry at the player");
        helper.assertFalse(Allies.isAllyOf(player, golem), "a golem whose anger names the player is not an ally");
        golem.stopBeingAngry();
        helper.assertTrue(Allies.isAllyOf(player, golem), "forgiven: an ally again");
        helper.succeed();
    }

    /**
     * When a calm golem, then a golem mad at the owner, then the same golem with the owner offline step into the
     * owner's flame, should none of them burn, while a zombie stepping in does (the flame works). The offline case
     * pins that the golem clause does not go through the ally path, which spares only while the owner is online.
     *
     * <p>Method under test: {@link TrailFires#isSpared}
     */
    static void trailSparesEveryGolem(GameTestHelper helper) {
        // Arrange
        floors(helper);
        ServerPlayer owner = trailingPlayer(helper);
        UUID ownerId = owner.getUUID();
        teleport(helper, owner, D);
        helper.assertTrue(light(helper, owner, A), "lit A");
        IronGolem golem = golem(helper, C);

        // Act & Assert
        stepInto(helper, A, golem);
        assertSpared(helper, golem, "a calm golem");
        anger(golem, owner);
        helper.assertTrue(Allies.isMadAt(golem, owner), "setup: the golem is mad at the owner");
        stepInto(helper, A, golem);
        assertSpared(helper, golem, "a golem mad at the owner");
        Zombie stranger = helper.spawn(EntityTypes.ZOMBIE, C);
        stepInto(helper, A, stranger);
        assertBurned(helper, stranger, "a zombie");
        owner.discard();
        helper.assertTrue(helper.getLevel().getPlayerByUUID(ownerId) == null, "setup: the owner is offline");
        stepInto(helper, A, golem);
        assertSpared(helper, golem, "a golem with the owner offline");
        forget(helper, owner);
        helper.succeed();
    }
}
```

(d) `SpellcraftGameTests` static block (lines 46–60): add `GolemGameTests.registerAll();` after `VexGameTests.registerAll();`.

- [ ] **Step 2: Change `Allies`**

Replace the file:

```java
package io.github.willrieske.spellcraft.spell;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;

/**
 * The one ally rule every spell uses (Evoking spec §3, Evoking II §2.3): what vanilla calls an ally (the player itself,
 * the same team, a tamed animal whose owner is the player, an allay that likes the player), plus the mobs the player
 * summoned ({@link AllyAttachments#SUMMONER}), any other {@code OwnableEntity} the player owns (vanilla's {@code Vex}
 * has an owner but never reports it through {@code isAlliedTo}), every villager and wandering trader, and every iron
 * golem that is not mad at the player (on a client level a golem counts as not mad; the spells call this on the
 * server).
 */
public final class Allies {
    private Allies() {}

    /**
     * Whether a spell cast by {@code owner} must spare {@code other}.
     *
     * @param owner the caster
     * @param other the entity the spell might hit
     * @return true if {@code other} is an ally of {@code owner}
     */
    public static boolean isAllyOf(Player owner, Entity other) {
        return owner.isAlliedTo(other)
                || ownedBy(owner.getUUID(), other)
                || other instanceof AbstractVillager
                || (other instanceof IronGolem golem && !isMadAt(golem, owner));
    }

    /**
     * Whether {@code golem} is mad at {@code owner}: the owner is its target, or its persistent anger names the owner
     * ({@code NeutralMob.isAngryAt}, docs/api-notes.md §20). While it is, the owner's spells and summons may fight it;
     * the Trailing flame is the exception ({@code TrailFires.isSpared}).
     *
     * @param golem the golem
     * @param owner the player
     * @return true if the golem is after the player
     */
    public static boolean isMadAt(IronGolem golem, Player owner) {
        LivingEntity target = golem.getTarget();
        if (target != null && target.getUUID().equals(owner.getUUID())) {
            return true;
        }
        return golem.level() instanceof ServerLevel level && golem.isAngryAt(owner, level);
    }

    /**
     * The cheap pre-check for callers that would otherwise have to look the owner up (Trailing runs it every tick
     * for every entity in a flame): {@code false} means {@link #isAllyOf} is false for every owner except through a
     * vanilla override this list does not know: team, {@code OwnableEntity}, {@code Allay}, the summoner attachment,
     * {@code AbstractVillager}, {@code IronGolem}. A modded entity with its own {@code considersEntityAsAlly} would need
     * adding here.
     *
     * <p>Why those six are enough: {@code Entity.java:2879-2889} {@code isAlliedTo} is {@code this == other ||
     * considersEntityAsAlly(other) || other.considersEntityAsAlly(this)}, and the default {@code
     * considersEntityAsAlly} is a team check that is false when either side has no team ({@code Team.java:17},
     * {@code Entity.java:2887}). The vanilla overrides that ally a team-less entity to a player are {@code
     * TamableAnimal.java:209-222} (an {@code OwnableEntity}, {@code TamableAnimal.java:34}; its {@code getTeam()}
     * {@code :192-206} also borrows the owner's) and {@code Allay.java:160-163} (the player it likes); {@code
     * AbstractIllager} and {@code Evoker} ally only illagers and vexes. The summoner attachment, villagers and golems
     * are this mod's own rules.
     *
     * @param other the entity to check
     * @return false only when no player can be its ally
     */
    public static boolean mayHaveAllies(Entity other) {
        return other.getTeam() != null
                || other instanceof OwnableEntity
                || other instanceof Allay
                || other instanceof AbstractVillager
                || other instanceof IronGolem
                || other.hasData(AllyAttachments.SUMMONER);
    }

    private static boolean ownedBy(UUID owner, Entity other) {
        if (other.getExistingData(AllyAttachments.SUMMONER)
                .filter(owner::equals)
                .isPresent()) {
            return true;
        }
        if (other instanceof OwnableEntity ownable) {
            EntityReference<LivingEntity> reference = ownable.getOwnerReference();
            return reference != null && owner.equals(reference.getUUID());
        }
        return false;
    }
}
```

- [ ] **Step 3: Change `TrailFires.isSpared` and the block's Javadoc**

In `TrailFires.java`, add to the end of the `isSpared` Javadoc (lines 372–383) the sentence "An iron golem is always spared once the flame has an owner (Evoking II §2.3)." and replace the body (lines 384–397) with

```java
    public boolean isSpared(ServerLevel level, BlockPos pos, Entity entity) {
        UUID owner = ownerByPos.get(pos.asLong());
        if (owner == null) {
            return false;
        }
        if (owner.equals(entity.getUUID())) {
            return true;
        }
        // Every iron golem, mad or not (Evoking II §2.3): fire damage carries no attacker, so golem mercy could not tell
        // this flame's burn from lava. Only an owned flame spares it; a /setblock flame burns like vanilla fire.
        if (entity instanceof IronGolem) {
            return true;
        }
        if (!Allies.mayHaveAllies(entity)) {
            return false;
        }
        Player ownerPlayer = level.getPlayerByUUID(owner);
        return ownerPlayer != null && Allies.isAllyOf(ownerPlayer, entity);
    }
```

Add the import `net.minecraft.world.entity.animal.golem.IronGolem`.

In `TrailFireBlock.java` line 90, change the Javadoc to

```java
    /** Server only; spared entities (the owner, their allies, and every iron golem) feel nothing, everyone else gets soul fire's. */
```

(Spotless may wrap it.)

- [ ] **Step 4: Build and run the GameTests**

Run: `./gradlew spotlessApply --console=plain`
Run: `./gradlew build --console=plain > build/build.log 2>&1; echo "exit=$?"; tail -40 build/build.log`
Expected: `exit=0`.
Run (background): `./gradlew runGameTestServer --console=plain > build/runGameTestServer.log 2>&1; echo "exit=$?"; grep -n "FAILED\|passed\|failed" build/runGameTestServer.log | tail -20`
Expected: `exit=0`; every test passes, `allies_a7_villagers_and_traders`, `golems_ii_allies_unless_mad`, `golems_ii_trail_spares_every_golem` and the pig-targeted `fangs_e7_busy_and_cooling` included.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/willrieske/spellcraft/spell/Allies.java src/main/java/io/github/willrieske/spellcraft/trailing/TrailFires.java src/main/java/io/github/willrieske/spellcraft/trailing/TrailFireBlock.java src/main/java/io/github/willrieske/spellcraft/gametest/FangGameTests.java src/main/java/io/github/willrieske/spellcraft/gametest/AlliesGameTests.java src/main/java/io/github/willrieske/spellcraft/gametest/GolemGameTests.java src/main/java/io/github/willrieske/spellcraft/gametest/SpellcraftGameTests.java
```

```bash
git commit -m "feat(allies): villagers, traders and calm iron golems are allies; the trail spares every golem"
```

Then `git log -1 --oneline` shows the commit.

---

### Task 7: Golem mercy, forgiveness, and no summons as golem targets

**Files:**

- Create: `src/main/java/io/github/willrieske/spellcraft/spell/GolemMercy.java`
- Modify: `src/main/java/io/github/willrieske/spellcraft/gametest/GolemGameTests.java` (registrations inside `registerAll()` + four tests; nothing in `SpellcraftGameTests`)

**Interfaces:**

- Consumes: `GolemMercyRule.clamp/forgives` (Task 3); `AllyAttachments.SUMMONER`; `GolemGameTests.golem/anger/SPOT` (Task 6); `GameTestSupport.summonedVex`.
- Produces: `GolemMercy` with `onDamagePre`, `onDamagePost`, `onChangeTarget` and the private `isPlayersSide(DamageSource)`.

- [ ] **Step 1: Write the GameTests (they compile only with Step 2's code)**

In `GolemGameTests.registerAll()` add:

```java
        // Builds one EvokerFangs inside the structure (no padding needed); it bites on its 8th tick and the second hit
        // comes after the 20-tick hurt cooldown: 60 ticks.
        SpellcraftGameTests.add(SHARED, "golems_ii_mercy_clamps_a_fang_bite", 60, 0, GolemGameTests::mercyClampsAFangBite);
        SpellcraftGameTests.add(SHARED, "golems_ii_mercy_sword_wolf_and_summon", GolemGameTests::mercySwordWolfAndSummon);
        SpellcraftGameTests.add(SHARED, "golems_ii_other_damage_untouched", GolemGameTests::otherDamageUntouched);
        SpellcraftGameTests.add(SHARED, "golems_ii_never_targets_a_summon", GolemGameTests::neverTargetsASummon);
```

and the tests:

```java
    /**
     * When a golem mad at the player, at 30 health, is bitten by the player's fang (6 damage), should it stop at 25,
     * forget its target, anger and the hit's memory, and be an ally again; a player hit at 25 afterwards should do
     * nothing and leave no grudge.
     *
     * <p>Method under test: {@link GolemMercy#onDamagePre}, and {@link GolemMercy#onDamagePost}
     */
    static void mercyClampsAFangBite(GameTestHelper helper) {
        // Arrange
        ServerLevel level = helper.getLevel();
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        IronGolem golem = golem(helper, SPOT);
        golem.setHealth(30.0F);
        anger(golem, player);
        helper.assertTrue(golem.isAngryAt(player, level), "setup: angry");
        helper.assertTrue(Allies.isMadAt(golem, player), "setup: mad");

        // Act
        level.addFreshEntity(new EvokerFangs(level, golem.getX(), golem.getY(), golem.getZ(), 0.0F, 0, player));
        helper.runAfterDelay(12, () -> {
            // Assert
            helper.assertValueEqual(golem.getHealth(), 25.0F, "the bite stops at 25");
            helper.assertTrue(golem.getTarget() == null, "the target is forgotten");
            helper.assertFalse(golem.isAngryAt(player, level), "the anger is gone");
            helper.assertTrue(Allies.isAllyOf(player, golem), "an ally again");
            // Forgiveness in Pre would be undone by the attacker record written after the damage lands; a null memory
            // is what keeps HurtByTargetGoal.canUse from firing (HurtByTargetGoal.java:33-36).
            helper.assertTrue(golem.getLastHurtByMob() == null, "it does not remember the hit");
        });
        // 35 ticks is past the bite (about tick 9) plus the 20-tick hurt cooldown, so the second hit is not swallowed.
        helper.runAfterDelay(35, () -> {
            // Act
            golem.hurtServer(level, level.damageSources().playerAttack(player), 10.0F);
            // Assert
            helper.assertValueEqual(golem.getHealth(), 25.0F, "a hit at 25 does nothing");
            helper.assertTrue(golem.getLastHurtByMob() == null, "and leaves no grudge");
            helper.succeed();
        });
    }

    /**
     * When the player's sword hit would take a golem from 30 to 20, should it land at 25 with no grudge; when the
     * player's tamed wolf would take one from 40 to 20, should it land at 25 too; when the player's summoned vex would
     * take one from 30 to 20, should it land at 25; when a sword hit takes one from 40 to 30, should it land at 30 and
     * remember the player (vanilla gets mad on its own); when a player hits a golem already at 20, should nothing land.
     *
     * <p>Method under test: {@link GolemMercy#onDamagePre}, and {@link GolemMercy#onDamagePost}
     */
    static void mercySwordWolfAndSummon(GameTestHelper helper) {
        // Arrange
        ServerLevel level = helper.getLevel();
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        IronGolem bySword = golem(helper, SPOT);
        bySword.setHealth(30.0F);
        IronGolem byWolf = golem(helper, SPOT);
        byWolf.setHealth(40.0F);
        Wolf pet = helper.spawn(EntityTypes.WOLF, SPOT);
        pet.setNoAi(true);
        pet.tame(player);
        IronGolem byVex = golem(helper, SPOT);
        byVex.setHealth(30.0F);
        Vex summon = summonedVex(helper, SPOT, player.getUUID());
        IronGolem roomLeft = golem(helper, SPOT);
        roomLeft.setHealth(40.0F);
        IronGolem alreadyUnder = golem(helper, SPOT);
        alreadyUnder.setHealth(20.0F);

        // Act
        bySword.hurtServer(level, level.damageSources().playerAttack(player), 10.0F);
        byWolf.hurtServer(level, level.damageSources().mobAttack(pet), 20.0F);
        byVex.hurtServer(level, level.damageSources().mobAttack(summon), 10.0F);
        roomLeft.hurtServer(level, level.damageSources().playerAttack(player), 10.0F);
        alreadyUnder.hurtServer(level, level.damageSources().playerAttack(player), 10.0F);

        // Assert
        helper.assertValueEqual(bySword.getHealth(), 25.0F, "the sword stops at 25");
        helper.assertTrue(bySword.getLastHurtByMob() == null, "forgiven: no grudge against the player");
        helper.assertValueEqual(byWolf.getHealth(), 25.0F, "the wolf stops at 25");
        helper.assertTrue(byWolf.getLastHurtByMob() == null, "forgiven: no grudge against the wolf");
        helper.assertValueEqual(byVex.getHealth(), 25.0F, "the summoned vex stops at 25");
        helper.assertTrue(byVex.getLastHurtByMob() == null, "forgiven: no grudge against the vex");
        helper.assertValueEqual(roomLeft.getHealth(), 30.0F, "above 25 the hit lands whole");
        helper.assertTrue(
                roomLeft.getLastHurtByMob() != null
                        && roomLeft.getLastHurtByMob().getUUID().equals(player.getUUID()),
                "above 25 the golem remembers the player");
        helper.assertValueEqual(alreadyUnder.getHealth(), 20.0F, "under 25 nothing lands");
        helper.assertTrue(alreadyUnder.getLastHurtByMob() == null, "and no grudge");
        summon.discard();
        helper.succeed();
    }

    /**
     * When a golem at 30 is hurt by magic with no owner (a vanilla ownerless fang), another by a zombie, and another by
     * a wolf nobody owns, should all three land at 20: mercy is only for the player's side.
     *
     * <p>Method under test: {@link GolemMercy#onDamagePre}
     */
    static void otherDamageUntouched(GameTestHelper helper) {
        // Arrange
        ServerLevel level = helper.getLevel();
        IronGolem byMagic = golem(helper, SPOT);
        byMagic.setHealth(30.0F);
        IronGolem byZombie = golem(helper, SPOT);
        byZombie.setHealth(30.0F);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, SPOT);
        zombie.setNoAi(true);
        IronGolem byStrayWolf = golem(helper, SPOT);
        byStrayWolf.setHealth(30.0F);
        Wolf stray = helper.spawn(EntityTypes.WOLF, SPOT);
        stray.setNoAi(true);

        // Act
        byMagic.hurtServer(level, level.damageSources().magic(), 10.0F);
        byZombie.hurtServer(level, level.damageSources().mobAttack(zombie), 10.0F);
        byStrayWolf.hurtServer(level, level.damageSources().mobAttack(stray), 10.0F);

        // Assert
        helper.assertValueEqual(byMagic.getHealth(), 20.0F, "ownerless magic lands whole");
        helper.assertValueEqual(byZombie.getHealth(), 20.0F, "a zombie's hit lands whole");
        helper.assertValueEqual(byStrayWolf.getHealth(), 20.0F, "an unowned wolf's hit lands whole");
        zombie.discard();
        helper.succeed();
    }

    /**
     * When a golem is told to target a vex the player summoned, should it keep no target; told to target a zombie,
     * should it take it (the control); told to target the summon while it has the zombie, should it keep the zombie.
     *
     * <p>Method under test: {@link GolemMercy#onChangeTarget}
     */
    static void neverTargetsASummon(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        IronGolem golem = golem(helper, SPOT);
        Vex summon = summonedVex(helper, SPOT, player.getUUID());
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, SPOT);
        zombie.setNoAi(true);

        // Act & Assert
        golem.setTarget(summon);
        helper.assertTrue(golem.getTarget() == null, "a summoned vex is never a golem's target");
        golem.setTarget(zombie);
        helper.assertTrue(
                golem.getTarget() != null && golem.getTarget().getUUID().equals(zombie.getUUID()),
                "a zombie still is");
        golem.setTarget(summon);
        helper.assertTrue(
                golem.getTarget() != null && golem.getTarget().getUUID().equals(zombie.getUUID()),
                "a cancelled change keeps the old target");
        summon.discard();
        zombie.discard();
        helper.succeed();
    }
```

Imports to add: `static io.github.willrieske.spellcraft.gametest.GameTestSupport.summonedVex`, `io.github.willrieske.spellcraft.spell.GolemMercy`, `net.minecraft.world.entity.animal.wolf.Wolf`, `net.minecraft.world.entity.monster.Vex`, `net.minecraft.world.entity.projectile.EvokerFangs`.

- [ ] **Step 2: Write `GolemMercy`**

```java
package io.github.willrieske.spellcraft.spell;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.spell.rules.GolemMercyRule;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Golem mercy (Evoking II spec §2.3): a player's side (any player, their fangs, a wolf a player owns, a mob a player
 * summoned) never takes an iron golem under {@link GolemMercyRule#MERCY_HEALTH}, a golem left there forgives, and a
 * golem never takes a player's summon as its target. The rule is {@link GolemMercyRule}; this class is the three
 * event hooks.
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class GolemMercy {
    private GolemMercy() {}

    /**
     * Clamps player-side damage so the golem keeps at least 25 health. Runs after armor and magic reductions, on the
     * value that would reach health.
     *
     * @param event the pre-damage event for any living entity
     */
    @SubscribeEvent
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof IronGolem golem && isPlayersSide(event.getSource())) {
            event.setNewDamage(GolemMercyRule.clamp(golem.getHealth(), event.getNewDamage()));
        }
    }

    /**
     * Forgives a golem the player's side has left at or under 25: target, persistent anger and the hit's memory are
     * cleared. In {@code Post}, not {@code Pre}: vanilla records the attacker after the damage lands
     * ({@code resolveMobResponsibleForDamage}, docs/api-notes.md §20), so a clear in {@code Pre} would be undone and
     * {@code HurtByTargetGoal} would make the golem mad again.
     *
     * @param event the post-damage event for any living entity
     */
    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof IronGolem golem
                && isPlayersSide(event.getSource())
                && GolemMercyRule.forgives(golem.getHealth())) {
            golem.stopBeingAngry();
        }
    }

    /**
     * A golem never targets a mob a player summoned (the summoner attachment): vanilla's golem attacks every
     * {@code Enemy} but creepers, and a summoned vex is one. A cancelled change keeps the golem's old target.
     *
     * @param event the change-target event for any living entity
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (event.getEntity() instanceof IronGolem && target != null && target.hasData(AllyAttachments.SUMMONER)) {
            event.setCanceled(true);
        }
    }

    /**
     * Whether the damage comes from a player's side: its causing entity is a player (a sword, or fangs through
     * {@code indirectMagic(fangs, caster)}), a wolf a player owns, or any mob carrying a summoner mark (round one's
     * vexes, Bound Vexes from Part 2 on). Lava, falls, other mobs and {@code /kill} are not.
     */
    private static boolean isPlayersSide(DamageSource source) {
        Entity cause = source.getEntity();
        return cause instanceof Player
                || (cause instanceof Wolf wolf && wolf.getOwner() instanceof Player)
                || (cause != null && cause.hasData(AllyAttachments.SUMMONER));
    }
}
```

- [ ] **Step 3: Build and run the GameTests**

Run: `./gradlew spotlessApply --console=plain`
Run: `./gradlew build --console=plain > build/build.log 2>&1; echo "exit=$?"; tail -40 build/build.log`
Expected: `exit=0`.
Run (background): `./gradlew runGameTestServer --console=plain > build/runGameTestServer.log 2>&1; echo "exit=$?"; grep -n "golems_\|FAILED\|passed\|failed" build/runGameTestServer.log | tail -40`
Expected: `exit=0`; all six `golems_ii_*` tests pass, and every other test still passes.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/github/willrieske/spellcraft/spell/GolemMercy.java src/main/java/io/github/willrieske/spellcraft/gametest/GolemGameTests.java
```

```bash
git commit -m "feat(allies): golem mercy stops the player's side at 25 health, forgives there, spares summons"
```

Then `git log -1 --oneline` shows the commit.

---

### Task 8: Docs, playtest items, mutation testing

**Files:**

- Modify: `README.md:11-20` (the Trailing and Evoking bullets)
- Modify: `docs/playtest-checklist.md:105` (Trailing), `:123-151` (the Evoking section), and a new "Evoking II (Part 1)" section before "## Real profile"
- Modify: `docs/mutation-testing.md` (one new row in the PIT table; the `Fangs.perform` probe row)
- Possibly modify: a `src/test/.../spell/rules/*Test.java` file, if a PIT survivor needs a new test (committed separately)
- Not modified: `src/main/templates/META-INF/neoforge.mods.toml` (no new spell in Part 1), `CLAUDE.md` (the packet rule is unchanged), `gradle.properties` (no version bump; §12 bumps once after Part 7)

**Interfaces:**

- Consumes: everything above.
- Produces: the docs Will reads.

- [ ] **Step 1: README**

Replace the Strength of Trailing bullet's ally list (lines 11–14): "spares your allies (your team, your pets, your own vexes) while you are in the same dimension, and burns everything else" becomes "spares your allies (your team, your pets, your own vexes, villagers, wandering traders) while you are in the same dimension, never burns an iron golem, and burns everything else".

Replace the Strength of Evoking bullet (lines 15–20) with:

```markdown
- <img src="src/main/resources/assets/spellcraft/textures/item/evoking_scroll.png" alt="" width="24" height="24"> **Strength of Evoking**: an evoker's powers, with no time limit. Press **Y** to raise your arms
  and, a second later, make evoker fangs rise under every enemy in the 13×13 square ahead of you (a
  big mob that covers several blocks gets one fang under each block; enemies up to 14 blocks below
  you and 6 above count). An enemy that steps off its block before the bite gets one more fang
  under its new spot. Press **V** to call three vexes to your side for 30–120 s. Fangs and vexes
  spare you and your allies (your team, your pets, your own vexes, villagers, wandering traders, and
  iron golems that are not mad at you) and hurt everything else; the vexes attack whatever comes
  within five blocks of them. You, your pets, your fangs and your vexes cannot take an iron golem
  below 25 health; when it gets that low, it calms down and stops being mad at you. The keys rebind
  in Controls. Sneak + scroll or milk turns it off.
```

- [ ] **Step 2: Playtest checklist, Trailing and Evoking sections**

In `docs/playtest-checklist.md`:

- Line 105: replace "A zombie, cow or villager in the trail catches fire and takes damage." with "A zombie, cow or pig in the trail catches fire and takes damage. A villager, a wandering trader or an iron golem does not."
- Line 125 (the Evoking intro): replace "F and V are the defaults; Controls → Spellcraft shows "Evoking: Fangs" and "Evoking: Summon Vexes". F and V both show red in Controls. F shares its key with Swap Item With Offhand, and V with the F3-chord Dump Version Info. Both conflicts are accepted, and pressing the key still reaches the mod's mapping." with "Y and V are the defaults; Controls → Spellcraft shows "Evoking: Fangs" and "Evoking: Summon Vexes". Y shows no conflict; V shows red (it shares the F3-chord Dump Version Info, accepted)."
- Line 130: replace the whole item with "- [ ] Y with a cow a few blocks ahead: the evoker's "prepare attack" sound; in F5, your arms go up like an evoker's and sway; dark purple particles swirl at both hands for two seconds. A second later one fang rises under the cow (none anywhere else) and bites it (6 damage)."
- Line 131: replace the whole item with "- [ ] Turn slowly on the spot and press Y each time it is ready (every five seconds): each time, the fangs find a cow ahead of you, wherever the square points."
- Lines 132, 133, 136, 142, 146 and 150: change each "F" that means the key to "Y" (line 142's `"f"` becomes `"y"`). Line 134 has no key letter; leave it.
- Line 138: replace the whole item with "- [ ] Press F with Evoking on: your hands swap as normal. Press Y: the cast starts and nothing swaps (Y is free in vanilla). Rebind Fangs to F in Controls: F casts instead of swapping while Evoking is on, and swaps again once the effect ends. Rebind Fangs to Shift+F: a plain F press swaps, Shift+F casts."
- Then run `grep -n "\bF\b" docs/playtest-checklist.md | sed -n '/Strength of Evoking/,$p'` by eye: inside the Evoking section the only F left should be in line 138's rebind-to-F sentences.

- [ ] **Step 3: Playtest checklist, the new section**

Insert before "## Real profile":

```markdown
## Evoking II (Part 1)

Survival world, open ground, a village nearby if you can. The items below add to the Strength of Evoking ones above. Where a golem can hurt you, use Creative or give yourself Resistance (`/effect give @s resistance 600 4`).

- [ ] Use the same game profile where you had Fangs on F (its saved settings carry over): Controls shows Fangs on Y, not F.
- [ ] Y at a group of three cows standing apart: three fangs, one under each, none on the grass between them.
- [ ] Y at a ravager (`/summon ravager` is fine) or at an iron golem that is mad at you (hit a village golem once first, then step back): several fangs rise under it, one on each block it stands on.
- [ ] Stand on a cliff 10–14 blocks above a cow and press Y: the fang reaches it. A cow on a ledge 6 blocks above you: bitten. A cow 7 or more blocks above you, or 15 or more below: "No targets in range".
- [ ] Hit a pig so it runs, then press Y at it while it flees: a fang rises where it was, and a second one where it ran to; no third.
- [ ] Y with only a villager, a wandering trader, or a calm iron golem ahead: "No targets in range". Vexes ignore them too.
- [ ] Hit a village iron golem with a sword again and again: when it is about half cracked (25 health; `/data get entity @e[type=iron_golem,limit=1,sort=nearest] Health` reads `25.0f`) it stops losing health, and on that hit it stops chasing you. Your tamed wolf (have one with you) and your fangs stop there too.
- [ ] Make a golem mad at you, then call your vexes next to it: the vexes fight it, it ends at 25 health and calms down.
- [ ] With Strength of Trailing on too, make a mad golem chase you across your fire trail: the golem is not hurt and does not catch fire.
- [ ] Summon vexes near a village golem: the golem never swings at them.
```

- [ ] **Step 4: Mutation testing**

Run: `./gradlew pitest --console=plain > build/pitest.log 2>&1; echo "exit=$?"; grep -n "Generated\|mutations\|Line Coverage\|Mutation score\|killed" build/pitest.log | tail -10`
Expected: `exit=0`. Read the totals and the survivors in `FangFootprints` and `GolemMercyRule` from `build/reports/pitest/index.html` (or the log summary). If a `FangFootprints` or `GolemMercyRule` mutant survives and is not equivalent, add the unit test that kills it (same style as Tasks 2–3), run `./gradlew spotlessApply --console=plain`, commit it on its own as `test(rules): kill the <Class> <mutator> survivor` (stage, then commit, as two calls), then re-run `pitest` and record the final numbers.

Add a row to the PIT table in `docs/mutation-testing.md` (the date is the run's date):

```markdown
| <run date> (Evoking II Part 1) | <score>% (<killed>/<total> killed; from 98%, 229/233, on 2026-11-06) | <line>% (<covered>/<lines>) | <"None new in `spell.rules` (every `FangFootprints` and `GolemMercyRule` mutant was killed; `FangSquare` lost its `cells` mutants with the ripple)" or each survivor with its file:line, mutator and why it is equivalent>. The same four `UpsideDownRollState` non-killed mutants as above, unchanged, all justified in the first row. |
```

In the hand-run probes table, change the 2026-11-06 row's probe text from "`Fangs.perform` `isPositionEntityTicking` guard" to "`Fangs.perform` `isPositionEntityTicking` guard (since Evoking II Part 1 the guard is in `Fangs.place`, called by `perform` and the chase)".

- [ ] **Step 5: Lint the docs**

Run: `pre-commit run markdownlint-cli2 --files README.md docs/playtest-checklist.md docs/mutation-testing.md`
Expected: `Passed`.

- [ ] **Step 6: Closing sweep**

Run: `grep -rn "spare\|villager\|Fangs.perform\|press F\|Press F\|\*\*F\*\*" README.md docs/api-notes.md docs/playtest-checklist.md docs/mutation-testing.md src/main/java --include=*.md --include=*.java | grep -v "docs/superpowers"`
Read every hit and fix any statement Tasks 4–7 made false (a villager that burns, a fang carpet, F as the fang key, a guard named in the wrong method). Spec files under `docs/superpowers` are out of scope for this task.

- [ ] **Step 7: Commit**

```bash
git add README.md docs/playtest-checklist.md docs/mutation-testing.md
```

```bash
git commit -m "docs: describe Part 1 of Evoking II (Y key, fangs under targets, new allies, golem mercy)"
```

Then `git log -1 --oneline` shows the commit. If the sweep changed a Java Javadoc, stage and commit that file first as `docs(java): fix Javadoc made stale by Evoking II Part 1`.

---

## Self-review (done while writing, re-done after the plan review)

- **Spec coverage.** §2.1 → Task 4. §2.2 scan → Task 5 (`SCAN_DEPTH`, `SCAN_HEIGHT`, `iiScanWindow` at both edges, `e3`); snapshot footprints → Tasks 2, 5 (`iiFootprints`, `iiFloatingTargets`); perform → Task 5 (`e1`, `e2`, `e17`); chase → Task 5 (`iiChase`, `iiChaseOncePerTarget`, `iiChaseRange`, `iiChaseRecordLeaves`, the server-stop test); no targets unchanged (`e3`). §2.3 allies → Task 6 (`a7`, `alliesUnlessMad`, `e7` moved to a pig); `mayHaveAllies` → Task 6; mad golem → Task 6; Trailing clause → Task 6 (`trailSparesEveryGolem`, offline owner included); mercy → Tasks 3, 7 (`mercyClampsAFangBite`, `mercySwordWolfAndSummon`, `otherDamageUntouched`); forgiveness → Task 7; golems and summons → Task 7 (`neverTargetsASummon`). §11 pure core `FangFootprints`, `GolemMercy.clamp` → Tasks 2, 3 (Divergences 3, 4, 6). §11 GameTests for Part 1 → all named above. §12 docs → Tasks 1, 8; `mod_version` untouched; mod description unchanged (no new spell in Part 1). §13 "Mercy on Will's own sword: clamped too" → `mercySwordWolfAndSummon`.
- **Placeholders.** None; the mutation row's angle-bracket fields are filled from the run.
- **Type consistency.** `FangFootprints.Column(int x, int z)` everywhere; `Fangs.Spot(Column, double)`, `Fangs.Footprint(UUID, List<Column>, List<Spot>)` and `Fangs.Snapshot(ResourceKey<Level>, BlockPos, FangSquare, List<Footprint>)` match between Task 5's code and tests; `Fangs.hasChaseRecord(EvokerFangs)`, `Fangs.BITE_TICK` and `Fangs.CHASE_RANGE` are defined in Step 3 and used in Step 1; `GolemMercyRule.clamp(float, float)`/`forgives(float)` match Tasks 3 and 7; `GolemGameTests.golem/anger/SPOT` are defined in Task 6 and used in Task 7; no task names `trackedFangs`, `CHASE_RECORDS` or `rejectsNonFiniteInputs`.

## Divergence index

<!-- generated by plan_map.py --write-index; do not edit -->

*No divergences recorded.*
