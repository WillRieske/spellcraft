# Evoking II, Part 4 (cure and bribes) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Bound Vex cures a zombie villager instead of fighting it (apple, potion, twenty seconds of smoke, the summoner's discount) and offers an emerald to an enemy vex before fighting it (two in three it leaves for good).

**Architecture:** Two job goals shaped like `RepairGolemGoal` (`CureZombieVillagerGoal`, `BribeGoal`), each with a claim map. Three saved marks (data attachments) carry state on the *other* mob: `cure` on a zombie villager, `bribed` and `bribe_tried` on a vanilla vex. The cure's smoke, freeze and release are driven from the zombie villager's own tick (`CureEvents`), so they do not depend on the vex that started them. `BoundVex.isPrey` and `Allies.isAllyOf` learn the new "not prey" and "ally" cases in one place each. The odds are a pure rule (`spell.rules.BribeRule`). No packet, no Mixin; one access-transformer line.

**Tech Stack:** Minecraft 26.3, NeoForge 26.3.0.23-beta (ModDevGradle), Java 25, JUnit 5, jqwik, ArchUnit, AssertJ, Error Prone (`-Werror`), Spotless + palantir-java-format, PIT, pre-commit, gitlint, markdownlint-cli2.

**Spec:** `2026-11-09-evoking-ii-parts-4-5-design.md` ([the Parts 4–5 design spec](../specs/2026-11-09-evoking-ii-parts-4-5-design.md)), §2–§4, §9, §10. "§" below points into it.

## Global Constraints

- Minecraft **26.3**; NeoForge **26.3.0.23-beta**, pinned; Java **25**. Mod ID **`spellcraft`**; base package **`io.github.willrieske.spellcraft`**.
- **Branch:** `feat/evoking-ii-part-4` in the worktree `C:\dev\spellcraft\.claude\worktrees\evoking-ii-part-4`; commands run from that worktree's root. Never `cd` to `C:\dev\spellcraft` itself. In the Bash tool use `./gradlew`; in PowerShell use `.\gradlew.bat`. Push nothing between tasks.
- **Minecraft sources for checks:** unpacked in the main checkout at `C:\dev\spellcraft\build\mc-src` (vanilla, patched) and `C:\dev\spellcraft\build\nf-src` (NeoForge); the worktree has no copy. Read them with Grep/Read. **The sources win over this plan's sketches:** where a step shows code, read the cited vanilla or project method first and write what compiles and does what the spec section says; report every difference. Task 1 records the facts in `docs/api-notes.md` §23; read §21–§23 before writing Java.
- **The spec is the requirement.** Every task cites spec sections; read them in full. Numbers (verbatim): approach within **1.5** blocks, at most **100** ticks; apple **30** ticks (sound and 5 crumbs every 4 ticks); potion step **20** ticks, throw at tick **10**, head turned **60°**; conversion **400** ticks; smoke **4 + 4** particles per tick; stale-freeze release at **300** ticks; offer **20** ticks; bribe odds **2 in 3**; flee until **12** blocks from every player within **64** and unseen, or **200** ticks.
- **Existing code to model on:** `evoking/RepairGolemGoal.java` (job goal: claim map that treats a dead or removed holder as free, `canUse` on `vex.scanDue(BoundVex.SCAN_INTERVAL)`, `conjure`/`dismiss` of the main hand, `stop` releasing everything, `clearClaims()` called from `BoundVexEvents.onServerStopped`), `spell/AllyAttachments.java` (how a saved attachment is declared and registered), `spell/GolemMercy.java` (`LivingChangeTargetEvent` cancel), `evoking/BoundVexEvents.java` (event subscriber style).
- Pure core (`spell.rules`): no `net.minecraft`/NeoForge/jspecify imports (ArchUnit rule 2). Client-only code only under `client` (none in this plan).
- Error Prone runs with `-Werror`; suppress only with `@SuppressWarnings("<Check>")` plus a comment why. Every new private member needs a reader in the same task. Compare entities by UUID (`ReferenceEquality`). `-Xlint:all`: never assign `getMoveControl()` to a local.
- **Doc comments:** Javadoc on every new class, method and constant, in the style of `RepairGolemGoal.java` (what, why, the spec section, the api-notes section for vanilla facts).
- **Unit test style:** `~/.claude/development-methodology.md`, *Unit test style* (Javadoc "When …, should …" + `<p>Method under test: {@link …}`; one `@Nested` per method; Arrange/Act/Assert). GameTests keep their files' style.
- **GameTests:** helpers in `gametest/BoundVexTestSupport.java` (`boundVex`, `parked`, `followOnly`, `noTargets`, `husk`, `huskAt`, `stayingVex`, `summoner`, `AIR_SPOT`), `GameTestSupport.java` (`hold`, `whenDone`, `saved`, `reloaded`, `drainSounds`), `MockPlayers.java`, `FangGameTests.addPadded` / `caster`. Patterns that hold in this repo: in padded tests hang a vex at `AIR_SPOT` (the barrier shell blocks sight lower down) and place mobs by absolute `setPos` with `setNoGravity(true)` from a known point (rotated structures shift relative coordinates); every spawned mob gets `setNoAi(true)` unless its AI is the subject and is discarded through `whenDone`; a mob's `canUse` runs only on ticks where `(tickCount + id)` is even and scans run every 10 ticks, so a test waiting on a scan allows at least 20 ticks; prefer `thenWaitUntil` over fixed tick counts; a test that changes global state gets its own environment name.
- **No red run for GameTest tasks (3–5):** their tests compile only with the production change. Red-then-green is for Task 2.
- **Verification recipe:** `./gradlew <task> --console=plain > build/<task>.log 2>&1; echo "exit=$?"; tail -40 build/<task>.log` (never pipe Gradle itself through `tail`). `runGameTestServer` takes about a minute: run it in the background, then `grep -a -n "FAILED\|required tests\|   - spellcraft" build/runGameTestServer.log | tail -8`. Expected `All N required tests passed`, N = the previous total plus the tests the task registers (Task 1 records the baseline). While writing a test, `./gradlew runGameTestServer "-PgameTests=spellcraft:bound_vex_cure_*"` runs only the matching tests; the task's final verification is always the full run, **twice**.
- **Format before you build:** `./gradlew spotlessApply --console=plain`; stage what it changed. A formatter hook strips unused imports after each edit: add an import in the same edit as its use.
- Visual checks belong to Will: never claim a visual works; Task 6 adds playtest items.
- Commits: conventional (gitlint), title ≤ 100 characters, body lines ≤ 80, no attribution lines. The commit gate runs `spotlessCheck` and `build`. **Run `git add` and `git commit` as two separate tool calls**, then check `git log -1 --oneline`.
- No release steps (version, tag) in this plan.
- **Execution order:** strictly 1 → 6.

## File Structure

```text
src/main/java/io/github/willrieske/spellcraft/
├── spell/rules/BribeRule.java            PURE, new: accepts(roll), SIDES                          (Task 2)
├── evoking/VexMarks.java                 new: the cure, bribed and bribe_tried attachments        (Task 3)
├── evoking/BoundVex.java                 isPrey: zombie villagers, bribed, courted; two new goals (Tasks 3–5)
├── spell/Allies.java                     a zombie villager with the cure mark is an ally          (Task 3)
├── spell/GolemMercy.java                 golems may not target a marked zombie villager           (Task 3)
├── evoking/CureZombieVillagerGoal.java   new                                                      (Task 4)
├── evoking/CureEvents.java               new: smoke, release, the cured villager's AI             (Task 4)
├── evoking/BribeGoal.java                new                                                      (Task 5)
├── evoking/FleeFromViewGoal.java         new                                                      (Task 5)
├── evoking/BoundVexEvents.java           clears the new claims; reinstalls the flee goal on join  (Tasks 4–5)
└── gametest/BoundVexCureGameTests.java   new (Tasks 3–5), registered in SpellcraftGameTests
src/main/resources/META-INF/accesstransformer.cfg   + ZombieVillager.startConverting             (Task 1)
src/test/java/.../spell/rules/BribeRuleTest.java, BribeRulePropertiesTest.java                    (Task 2)
docs/api-notes.md (§23), docs/playtest-checklist.md, docs/mutation-testing.md, README.md
```

---

### Task 1: Verified facts, the access transformer, the baseline

**Files:**

- Modify: `docs/api-notes.md` (new §23 at the end)
- Modify: `src/main/resources/META-INF/accesstransformer.cfg`

**Interfaces:**

- Produces: `docs/api-notes.md` §23; `ZombieVillager.startConverting(UUID, int)` callable from mod code.

- [ ] **Step 1: Verify and record.** Open each item in `build/mc-src` / `build/nf-src` and write §23 ("Evoking II, Part 4: cure and bribes") in the file's existing style, each fact with `file:line`: `ZombieVillager` (`startConverting` signature and what it does, including that it removes weakness; `isConverting`; `tick`'s countdown and that it is not gated by `NoAI`; `finishConversion` and the starter-must-be-in-the-level rule for the reputation event; `setVillagerConversionTime`; `mobInteract` with a golden apple); the conversion copying `NoAI` (`ConversionType`) and NeoForge's `LivingConversionEvent.Post` (its accessors for the old and the new mob, and whether the old mob's attachments are still readable there); `EntityTickEvent.Post`; how `Mob.setNoAi` gates movement and attacks; vanilla `Vex` (`getOwner`, its goals, `setTarget(null)`, `GoalSelector.removeAllGoals(Predicate)`); `PotionContents` (the constructor or factory that builds contents with a custom color and no effects; weakness's color constant or how to read it from `MobEffects.WEAKNESS`) and how a splash potion entity with such a stack is spawned (cite §22's projectile bullets rather than repeating them); `ItemParticleOption` (its constructor in 26.3) and `ServerLevel.sendParticles`; the declared types of `SoundEvents.GENERIC_EAT` and `VILLAGER_AMBIENT`; `EntityJoinLevelEvent`; `Entity.hasLineOfSight` for a player looking at a mob; data attachments (`AttachmentType.builder(...).serialize(...)`, `setData`/`getExistingData`/`hasData`/`removeData`, and that they are saved with the entity and copied or not on conversion).
- [ ] **Step 2: The access transformer.** Add the line `public net.minecraft.world.entity.monster.zombie.ZombieVillager startConverting(Ljava/util/UUID;I)V` to `accesstransformer.cfg` (confirm the class's package and the descriptor against the source). Run `./gradlew build` (recipe): a cold re-decompile may take several minutes; run it in the background. Prove the transformer took: `grep -n "void startConverting" build/moddev/artifacts/*sources*/**/ZombieVillager.java` if the patched sources are regenerated there, or note in §23 how it was proven (a one-line scratch call that compiled, then removed).
- [ ] **Step 3: Baseline.** `runGameTestServer` (recipe). Record `All N required tests passed` in this plan's "Resume State" as `Baseline GameTests: N`.
- [ ] **Step 4: Commit**

```bash
git add docs/api-notes.md src/main/resources/META-INF/accesstransformer.cfg docs/superpowers/plans/2026-11-09-evoking-ii-part-4.md
```

```bash
git commit -m "build: open ZombieVillager.startConverting; record the 26.3 facts for Part 4"
```

---

### Task 2: Pure `BribeRule`

**Files:**

- Create: `src/main/java/io/github/willrieske/spellcraft/spell/rules/BribeRule.java`
- Test: `src/test/java/io/github/willrieske/spellcraft/spell/rules/BribeRuleTest.java`, `BribeRulePropertiesTest.java`

**Interfaces:**

- Produces: `BribeRule.SIDES` (`int`, 3); `static boolean BribeRule.accepts(int roll)` (true for 0 and 1, false for 2; `IllegalArgumentException` outside `0..2`); `static boolean BribeRule.roll(ScrollDraw.Dice dice)` if `ScrollDraw.Dice` fits (read `spell/rules/ScrollDraw.java` and `src/test/.../ScriptedDice.java` first: use the interface's existing method to draw a number in `0..SIDES-1`; if its shape does not fit a 3-sided roll, declare a one-method `BribeRule.Dice` instead and say so).

- [ ] **Step 1: Failing tests.** `BribeRuleTest`: `accepts(0)` and `accepts(1)` true, `accepts(2)` false; `accepts(-1)` and `accepts(3)` throw; `roll` with a scripted dice returning each side. `BribeRulePropertiesTest` (match `GuardAreaPropertiesTest`'s `@Group` style): over the three sides exactly two accept; `roll` never throws for a dice that stays in range.
- [ ] **Step 2: See them fail.** `./gradlew test --tests "*BribeRule*" --console=plain > build/test.log 2>&1; echo "exit=$?"; tail -20 build/test.log` → compile errors naming `BribeRule`.
- [ ] **Step 3: Implement** (spec §3.1, §9), with Javadoc saying the odds are Will's "1/1.5".
- [ ] **Step 4: See them pass** (same command, `exit=0`).
- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/willrieske/spellcraft/spell/rules/BribeRule.java src/test/java/io/github/willrieske/spellcraft/spell/rules
```

```bash
git commit -m "feat(evoking): add the pure bribe odds rule"
```

---

### Task 3: The marks, and who is prey or ally

**Files:**

- Create: `src/main/java/io/github/willrieske/spellcraft/evoking/VexMarks.java`
- Modify: `evoking/BoundVex.java` (`isPrey`), `spell/Allies.java`, `spell/GolemMercy.java`, `Spellcraft.java` (register the attachments if they need their own register)
- Create: `gametest/BoundVexCureGameTests.java`; modify `gametest/SpellcraftGameTests.java` (+ `BoundVexCureGameTests.registerAll();`)

**Interfaces:**

- Produces (`VexMarks`, public): `CURE` (a saved attachment holding the game time the cure began, a `Long`), `BRIBED` and `BRIBE_TRIED` (saved boolean marks); helpers `static boolean isBeingCured(Entity)`, `static boolean isBribed(Entity)`, `static boolean wasOfferedBribe(Entity)`. Declare them where `AllyAttachments` declares `SUMMONER` if that file's register is the natural home; otherwise in `VexMarks` with its own `DeferredRegister` wired in `Spellcraft` exactly as the existing one is. `Allies` is in `spell` and `VexMarks` in `evoking`: check the direction of existing imports between those packages (ArchUnit) and, if `spell` must not import `evoking`, put the `CURE` attachment and `isBeingCured` in `spell/AllyAttachments` instead.

- [ ] **Step 1: Marks.** Add the three attachments and helpers.
- [ ] **Step 2: Prey and allies** (spec §2.1, §3.2).
  - `BoundVex.isPrey(ServerLevel, UUID, LivingEntity)`: after the `attackable` check, answer false for a `ZombieVillager` and for a mob that `isBribed`. (Task 5 adds the courted case.)
  - `Allies.isAllyOf(owner, other)`: true when `other` is being cured, for every owner. Check `Allies.mayHaveAllies` (Trailing's fast path) and add zombie villagers there if the fast path would otherwise skip the check.
  - `GolemMercy`'s `LivingChangeTargetEvent` handler: also cancel when an `IronGolem` picks a mob that is being cured.
- [ ] **Step 3: GameTests** (new class; class Javadoc: "Bound Vex GameTests (Evoking II Parts 4–5 spec §2–§3, §9): the cure and the bribe."). Register each with the name prefix `bound_vex_cure_` or `bound_vex_bribe_`.

| Method | Arrange → Act → Assert |
|---|---|
| `aZombieVillagerIsNeverPrey` | A staying vex at `AIR_SPOT` with full target AI (job goals removed so nothing else acts); a no-AI zombie villager 3 blocks away and a husk 3 blocks the other way → the husk becomes the target; `vex.isPrey(zombieVillager)` is false; after `player.setLastHurtByMob(zombieVillager)` (as the grudge tests set it) and 30 ticks the target is still not the zombie villager. |
| `aBribedVexIsNotPrey` | A vanilla vex with `BRIBED` set → `BoundVex.isPrey(level, summonerUuid, it)` false; without the mark true. |
| `aZombieVillagerBeingCuredIsAnAlly` | A zombie villager with `CURE` set: `Allies.isAllyOf(player, it)` true; without the mark false. The summoner's fangs spare it: follow how `FangGameTests` casts and asserts a spared ally (a fang snapshot or a bite on a marked versus an unmarked zombie villager). |
| `aGolemLeavesAZombieVillagerBeingCured` (padded; golem on stone, full AI) | A marked no-AI zombie villager 3 blocks from an iron golem for 60 ticks → `golem.getTarget()` is never it; an unmarked one beside a second golem (or after the mark is removed) is targeted. |
| `marksSurviveASave` | `GameTestSupport.reloaded` of a zombie villager with `CURE` and of a vex with `BRIBED` and `BRIBE_TRIED` keeps them. |

Delta: 5 tests.

- [ ] **Step 4: Verify** (`spotlessApply`, `build`, `runGameTestServer` twice; total = baseline + 5) and **commit**:

```bash
git add src/main/java
```

```bash
git commit -m "feat(evoking): cure and bribe marks; zombie villagers are never prey"
```

---

### Task 4: The cure

**Files:**

- Create: `evoking/CureZombieVillagerGoal.java`, `evoking/CureEvents.java`
- Modify: `evoking/BoundVex.java` (`registerGoals`: priority 2, beside `RepairGolemGoal`), `evoking/BoundVexEvents.java` (`onServerStopped` also clears the cure claims)
- Modify: `gametest/BoundVexCureGameTests.java`

**Interfaces:**

- Consumes: `VexMarks.CURE`, `isBeingCured` (Task 3); `BoundVex.conjure/dismiss/isConjured`, `scanDue`, `summoner()`, `inGuardArea`, `guardArea()`; `ZombieVillager.startConverting` (Task 1).
- Produces: `CureZombieVillagerGoal` (package-private class; constants `REACH` 1.5, `APPROACH_LIMIT` 100, `APPLE_TICKS` 30, `POTION_TICKS` 20, `THROW_TICK` 10, `CONVERSION_TICKS` 400, visible to the tests as needed; `static void clearClaims()`); `CureEvents.RELEASE_AFTER` (300); `static ItemStack CureZombieVillagerGoal.curePotion()` (the effect-free, weakness-colored splash potion).

- [ ] **Step 1: The goal** (spec §2.2, every sentence). Flags `MOVE` and `LOOK`; `requiresUpdateEveryTick`. Shape:
  - `canUse`: prune dead claim holders first (as `RepairGolemGoal`); gate on `vex.scanDue(BoundVex.SCAN_INTERVAL)` and a present summoner; candidates are `ZombieVillager`s in `vex.guardArea()` filtered by `vex.inGuardArea`, alive, `!isConverting()`, `!isNoAi()`, no cure mark, not claimed by another living vex; and (`vex.getTarget() == null` or the candidate's target is the summoner or `Allies.isAllyOf(summoner, target)`). Nearest wins.
  - `start`: claim; `setNoAi(true)`; set `CURE` to `level.getGameTime()`; step = approach; tick counter 0.
  - `canContinueToUse`: the subject is alive and not removed, the summoner is present, the step is not finished, and the approach has not timed out. It does **not** look at `vex.getTarget()`.
  - `tick`: the four steps of the spec's table. Approach: `setWantedPosition` level with the head (`getEyeY()`), until within `REACH` of that point. Apple: `conjure(MAINHAND, new ItemStack(Items.GOLDEN_APPLE))` once; every 4 ticks the eat sound at the zombie villager and 5 item-crumb particles at its mouth. Potion: `conjure(MAINHAND, curePotion())`; for the first 10 ticks the head is turned 60° from the line to the subject (set the look target to a point rotated 60° about the vertical, through the look control, which this goal owns by its `LOOK` flag); at `THROW_TICK` spawn a `ThrownSplashPotion` of `curePotion()` aimed at the subject's feet with the spawn helper api-notes §22 records. Begin: if `!subject.isConverting()`, `subject.startConverting(summonerUuid, CONVERSION_TICKS)`; mark the goal finished.
  - `stop`: `vex.dismiss(MAINHAND)`; release the claim (only if this vex holds it); if the goal did not finish and the subject still exists and is not converting: `setNoAi(false)` and remove `CURE`. Null the fields.
  - The goal must be **interruptable** (do not override `isInterruptable` to false): `FleeCreeperGoal`, `DodgeGolemGoal` and Part 5's rescue replace it, and `stop` is then the abort.
- [ ] **Step 2: `CureEvents`** (spec §2.3): `@EventBusSubscriber(modid = Spellcraft.MODID)`.
  - `EntityTickEvent.Post`, server side, a `ZombieVillager` with `CURE`: converting → 4 `HAPPY_VILLAGER` and 4 `CLOUD` particles spread over its bounding box; not converting and `gameTime − mark > RELEASE_AFTER` → `setNoAi(false)`, remove the mark; otherwise nothing.
  - `LivingConversionEvent.Post`: when the old mob carried `CURE`, `setNoAi(false)` on the new mob (use what Task 1 recorded about reading the old mob's attachment there; if it is not readable in `Post`, do it in `Pre`/the tick handler and say how).
- [ ] **Step 3: Wire.** `BoundVex.registerGoals`: `goalSelector.addGoal(2, new CureZombieVillagerGoal(this));` next to the repair goal, and update the method's Javadoc. `BoundVexEvents.onServerStopped`: `CureZombieVillagerGoal.clearClaims()`.
- [ ] **Step 4: GameTests** (spec §9 "GameTests, Part 4", the cure bullets; padded; a staying vex at `AIR_SPOT` via `stayingVex`, zombie villagers placed by absolute position with no gravity; a zombie villager burns in daylight, so give each a helmet (`setItemSlot(HEAD, new ItemStack(Items.LEATHER_HELMET))`) or check how existing tests keep undead from burning; remove the vex's other job goals where they could interfere):

| Method | Asserts |
|---|---|
| `cureRunsItsTimeline` | Claimed at once: `isNoAi()` and `isBeingCured`. Then, in order (`thenWaitUntil` each): the vex holds a golden apple; the vex holds a splash potion equal to `curePotion()`; a `ThrownSplashPotion` exists near; `zombieVillager.isConverting()`; then the vex's main hand is its iron sword again and nothing is conjured. A second player standing 2 blocks from the zombie villager has no `WEAKNESS` at the end. |
| `cureEndsInAVillagerWithAiAndTheDiscount` | After `isConverting()`, the test calls `setVillagerConversionTime(1)` → within 20 ticks a `Villager` stands there, `!isNoAi()`, and its gossip for the summoner is positive (read how vanilla exposes it: `villager.getGossips()` and `GossipType.MAJOR_POSITIVE`, or `getPlayerReputation(player) > 0`); no zombie villager remains. |
| `aNewTargetDoesNotStopTheCure` | Mid-apple, a husk appears beside the vex and is set as its target → the cure still reaches `isConverting()`. |
| `anAttackerIsClaimedByABusyVex` | The vex has a target (a husk, no AI); a zombie villager whose `setTarget(summoner)` is set is claimed (frozen, marked) within 30 ticks; another with no target is not claimed while the vex still has its target. |
| `aCreeperInterruptsAndTheCureRestarts` | In the apple step a swelling creeper is placed within flee range (see how `BoundVexJobGameTests` makes one swell) → the zombie villager is unfrozen and unmarked and the vex's hand is its sword; the creeper is discarded → the cure starts again and reaches `isConverting()`. |
| `approachTimeoutAborts` | The vex is parked with its movement blocked (remove nothing; instead place the zombie villager in the guard area and hold the vex in place each tick with `setPos`) → after `APPROACH_LIMIT` ticks the zombie villager is unfrozen, unmarked, unclaimed. |
| `theSubjectDyingRestoresTheHand` | Kill the zombie villager in the apple step → the vex holds its sword, nothing conjured. |
| `twoVexesOneSubject` and `twoVexesTwoSubjects` | One zombie villager: exactly one vex ever holds the apple. Two: each vex works one (both end converting). |
| `aStaleFreezeIsReleased` | A zombie villager with `setNoAi(true)` and `CURE` set to `gameTime − 301` → on its next tick it has AI and no mark. With `CURE` set to `gameTime − 10`: still frozen and marked after 5 ticks. A converting one with an old mark stays frozen and marked. |
| `aHandFedZombieVillagerKeepsItsConversion` | During the apple step the test calls `startConverting(otherPlayerUuid, 3000)` → at the end the conversion time is still near 3000 (read `villagerConversionTime` through the save tag or an accessor) and the starter is the other player. |
| `aNoAiStatueIsNeverClaimed` | A zombie villager with `setNoAi(true)` and no mark stays unmarked for 40 ticks. |
| `aStayingVexCuresOnlyInItsArea` | One zombie villager 5 blocks from the post is claimed; one 9 blocks away is not. |

Delta: 13 tests.

- [ ] **Step 5: Verify** (`spotlessApply`, `build`, `runGameTestServer` twice; total = previous + 13) and **commit**:

```bash
git add src/main/java
```

```bash
git commit -m "feat(evoking): Bound Vexes cure zombie villagers"
```

---

### Task 5: The bribe

**Files:**

- Create: `evoking/BribeGoal.java`, `evoking/FleeFromViewGoal.java`
- Modify: `evoking/BoundVex.java` (`registerGoals`; `isPrey`: courted), `evoking/BoundVexEvents.java` (`onServerStopped` clears the bribe claims; `EntityJoinLevelEvent` reinstalls the flee goal)
- Modify: `gametest/BoundVexCureGameTests.java`

**Interfaces:**

- Consumes: `BribeRule` (Task 2); `VexMarks.BRIBED`, `BRIBE_TRIED` (Task 3).
- Produces: `BribeGoal` (constants `REACH` 1.5, `APPROACH_LIMIT` 100, `OFFER_TICKS` 20; `static boolean isCourted(Entity)`: claimed by a living Bound Vex; `static void clearClaims()`; `static void setDice(...)` / `resetDice()` for tests, public because the tests are in another package, with Javadoc saying they exist for tests); `FleeFromViewGoal` (public constructor taking the `Vex`; constants `CLEAR_DISTANCE` 12, `PLAYER_RANGE` 64, `LIMIT` 200); `static void FleeFromViewGoal.install(Vex)` (clears target, removes every goal and target goal, adds the flee goal).

- [ ] **Step 1: `FleeFromViewGoal`** (spec §3.2): flags `MOVE` and `LOOK`; always usable; each tick: find players within `PLAYER_RANGE` (not spectators); with none, or when the vex is at least `CLEAR_DISTANCE` from each and none `hasLineOfSight(vex)` → `vex.discard()`; else fly directly away from the nearest (`setWantedPosition` 8 blocks farther along the line from that player through the vex, charge speed); after `LIMIT` ticks `discard()`.
- [ ] **Step 2: `BribeGoal`** (spec §3.1, every sentence): flags `MOVE` and `LOOK`, priority 2. `canUse` on the scan tick with a present summoner: a `Vex` whose type is exactly `EntityTypes.VEX` (not a `BoundVex`), in the guard area, alive, neither mark, not courted by another living vex, whose `getTarget()` is the summoner or an ally of the summoner. Steps: approach (≤ 100 ticks; timeout ends with no mark); offer (conjure an emerald, `VILLAGER_AMBIENT` once, look at it, 20 ticks); roll (`BribeRule` through the goal's dice: by default the vex's `RandomSource`): accepted → `setData(BRIBED)` and `FleeFromViewGoal.install(other)`; refused → `setData(BRIBE_TRIED)`. `stop`: dismiss the hand, release the claim. Interruptable.
- [ ] **Step 3: Courted is not prey.** `BoundVex.isPrey` (static): false when `BribeGoal.isCourted(target)`. Because every keep-target test goes through `isPrey`, the other vexes drop a courted vex within a scan, and the Part 3 backstop cancels their hits on it. Update the Javadoc.
- [ ] **Step 4: Wire.** `registerGoals`: `goalSelector.addGoal(2, new BribeGoal(this));`. `BoundVexEvents`: `onServerStopped` → `BribeGoal.clearClaims()`; a new `EntityJoinLevelEvent` handler (server side): a `Vex` that `isBribed` gets `FleeFromViewGoal.install`.
- [ ] **Step 5: GameTests** (spec §9, the bribe bullets; padded; the vanilla vex is spawned with `setNoAi(false)` only where its flight matters, with `setTarget(player)` set by the test; scripted dice through `BribeGoal.setDice`, reset in `whenDone`; in its own environment name if the dice are static state shared across parallel tests: they are, so register every bribe test that sets the dice in an environment of its own, e.g. `"bribe_dice"`, so they do not run beside each other, or make the dice per-vex and avoid the problem; say which):

| Method | Asserts |
|---|---|
| `aBribeThatSucceeds` | The Bound Vex holds an emerald during the offer; then the vanilla vex has `BRIBED`, `getTarget() == null`, and its only goal is a `FleeFromViewGoal`; the Bound Vex's hand is restored; no Bound Vex takes the bribed vex as target for 40 ticks. |
| `aBribedVexLeavesOutOfSight` | A bribed vex 13 blocks from the only player, behind a stone wall → discarded within a few ticks. In plain sight 5 blocks away with its movement held: discarded only after `LIMIT` ticks. |
| `aBribeThatFails` | `BRIBE_TRIED` set, no `BRIBED`; the Bound Vex then targets it; no emerald is conjured again in 60 ticks. |
| `aCourtedVexIsNotPrey` | Two Bound Vexes, one vanilla vex: while one offers, the other's target is never the vanilla vex, and `vanillaVex.hurtServer(level, damageSources().mobAttack(otherBoundVex), 4)` leaves its health unchanged; after a scripted refusal the same hit hurts it. |
| `noOfferToAVexThatIsNotAttackingYourSide` | A vanilla vex with no target, and one targeting a pig → no emerald in 60 ticks. A `BoundVex` of another player targeting the summoner → no offer. |
| `theOfferEndsIfTheVexDies` and `anApproachTimeoutLeavesNoMark` | Kill the vanilla vex mid-offer → hand restored, claim free. Hold the Bound Vex in place → after 100 ticks no mark on the vanilla vex, hand restored. |
| `aBribedVexLoadedFromASaveOnlyFlees` | `reloaded` copy added to the level → `BRIBED` kept; its goal selector holds only the flee goal. |
| `aStayingVexBribesOnlyInItsArea` | A vanilla vex targeting the summoner 9 blocks from the post gets no offer; one 5 blocks away does. |

Delta: 9 tests.

- [ ] **Step 6: Verify** (`spotlessApply`, `build`, `runGameTestServer` twice; total = previous + 9) and **commit**:

```bash
git add src/main/java
```

```bash
git commit -m "feat(evoking): Bound Vexes bribe enemy vexes with an emerald"
```

---

### Task 6: Docs, playtest items, mutation testing

**Files:**

- Modify: `README.md`, `docs/playtest-checklist.md`, `docs/mutation-testing.md`, `docs/api-notes.md` (§23 additions found during the build)

- [ ] **Step 1: README.** In the Evoking paragraph, after the gear sentences, add in the README's plain second-person voice: your vexes never fight a zombie villager; one flies up, offers it a golden apple and throws a potion, and twenty seconds later it is a villager again, with the cured-villager discount for you; it costs you nothing. When an evoker's vex attacks you or your allies, one of your vexes offers it an emerald first: two times in three it takes it and flies off for good.
- [ ] **Step 2: Playtest checklist.** New section "Evoking II (Part 4)" with unchecked items: the cure's look (apple crumbs at its mouth, the head turn, the gray splash with nobody weakened, green and white smoke for 20 seconds, it stands still throughout); the villager has the discount; a cure in daylight (does it burn before it turns?); a village golem beside a curing zombie villager leaves it alone, and so do your fangs; a zombie villager that was chasing you stops when a vex claims it, even mid-fight; a creeper hissing next to the curing vex makes it flee and the cure restarts; the bribe's emerald and villager "hmm" with a dozen vexes out (the others hold off during the offer); a bribed vex flies away through walls and is gone without sound or drop; a refused bribe is followed by a normal fight.
- [ ] **Step 3: Mutation testing.** `./gradlew pitest` (recipe; `grep -n "Generated\|Line Coverage\|Mutation score\|killed" build/pitest.log | tail -10`); add a dated row for "Evoking II Part 4" in the file's existing form; kill or justify any `BribeRule` survivor.
- [ ] **Step 4: api-notes.** Append to §23 every vanilla fact the build found that Task 1 did not record (verify each against the sources).
- [ ] **Step 5: Verify and commit.** `./gradlew build` (recipe), then:

```bash
git add README.md docs
```

```bash
git commit -m "docs: describe the cure and the bribe; playtest items; mutation run"
```

---

## Self-review

- **Spec coverage:** §2.1 → Task 3; §2.2 → Task 4 Step 1; §2.3 → Task 4 Step 2; §3.1 → Task 5 Steps 2–3; §3.2 → Task 5 Steps 1, 4 and Task 3 (`isPrey`); §4 → the tests of Tasks 3–5 (the daylight item is a playtest item); §9 Part 4 tests → Tasks 3–5 tables; §9 docs → Tasks 1 and 6. Part 5 (§5–§8) is the next plan.
- **Names** used across tasks: `VexMarks.CURE` / `BRIBED` / `BRIBE_TRIED`, `isBeingCured`, `isBribed`, `wasOfferedBribe`, `BribeRule.accepts` / `roll` / `SIDES`, `CureZombieVillagerGoal.curePotion` / `clearClaims`, `CureEvents.RELEASE_AFTER`, `BribeGoal.isCourted` / `clearClaims` / `setDice` / `resetDice`, `FleeFromViewGoal.install`.

## Resume State

Not started. Baseline GameTests: (Task 1 fills this in.)
