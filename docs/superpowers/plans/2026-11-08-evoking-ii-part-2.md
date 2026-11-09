# Evoking II, Part 2 (Bound Vex basics) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the evoker's short-lived vanilla vexes with the **Bound Vex**, the mod's own mob: V summons it with no timer, at most twelve per player; it follows its summoner (teleporting when far, through portals too), can be told to stay, fights what its side fights, calls the summoner's wolves into the fight, flees a hissing creeper, dodges a golem's swing, and repairs hurt ally golems with a conjured iron ingot.

**Architecture:** `evoking.BoundVex extends Vex`, registered as `spellcraft:bound_vex` through a `DeferredRegister.Entities`, rendered by vanilla's `VexRenderer`. Vanilla's vex goals are private inner classes, so the Bound Vex owns copies of the charge and wander goals and adds its own: follow/teleport, flee-creeper, dodge-golem and repair-golem in the goal selector; three target goals (hurt-by-the-summoner's-side, join-an-ally's-fight, nearest-hostile) in the target selector, each dropping a target that stops being prey or leaves reach. Three small shared rules live on `BoundVex` so every goal agrees: `scanDue(interval)` (the goal clock: vanilla runs `canUse` only every other tick, keyed to the entity id), `inGuardArea(entity)` (the closed 8×8×7 box) and `keepsTarget(target)`. Two pure rules carry the numbers: `spell.rules.VexCap` (3 / `12 − count` / 0) and `spell.rules.GuardArea` (the box and the 7.25 reach). `VexSummons` creates Bound Vexes instead of vanilla vexes; `EvokingCaster` counts the summoner's living Bound Vexes before a V cast and answers `CAPPED` with a notice at twelve. A `BoundVexEvents` subscriber brings non-staying vexes through dimension changes (noted on `EntityTravelToDimensionEvent`, moved on `PlayerChangedDimensionEvent`). Nothing is sent clientbound beyond vanilla's entity sync: the one synced flag (`STAYING`) rides the entity data.

**Tech Stack:** Minecraft 26.3, NeoForge 26.3.0.23-beta (ModDevGradle), Java 25, JUnit 5, jqwik, ArchUnit, AssertJ, Error Prone (`-Werror`), Spotless + palantir-java-format, PIT, pre-commit, gitlint, markdownlint-cli2.

**Spec:** [`docs/superpowers/specs/2026-11-07-evoking-ii-design.md`](../specs/2026-11-07-evoking-ii-design.md), §3 (Part 2), with §10–§13 as cross-cutting rules. Section references below point into it. Part 1 (§2) is on branch `feat/evoking-ii-part-1` (draft PR WillRieske/spellcraft#3); this plan builds on it.

## Global Constraints

- Minecraft **26.3**; NeoForge **26.3.0.23-beta**, pinned; Java **25**. Mod ID **`spellcraft`**; base package **`io.github.willrieske.spellcraft`**.
- **Branch:** `feat/evoking-ii-part-2`, cut from `feat/evoking-ii-part-1` at `e92d517` (stacked: Part 1's code is needed and not yet on `main`), in the worktree `C:\dev\spellcraft\.claude\worktrees\evoking-ii-part-2`; commands run from that worktree's root. In the Bash tool use `./gradlew`; in PowerShell use `.\gradlew.bat`. Never `cd` to `C:\dev\spellcraft` itself. **Push nothing between tasks:** Task 3's commit is behaviorally regressed (no follow or target goals until Tasks 4 and 5); the branch is pushed once, after Task 7.
- **Minecraft sources for checks:** the main checkout has them unpacked at `C:\dev\spellcraft\build\mc-src` (vanilla, patched) and `C:\dev\spellcraft\build\nf-src` (NeoForge); read those with Grep/Read instead of unpacking again. Line numbers below are from them (2026-11-08) and were re-checked by the plan review. The facts this plan rests on are listed in Task 1 (api-notes §21); read that section before writing Java.
- The mob (§3.1): `spellcraft:bound_vex`, `MobCategory.MONSTER`, built exactly like vanilla's vex (`fireImmune`, `sized(0.4F, 0.8F)`, `eyeHeight(0.51875F)`, `passengerAttachments(0.7375F)`, `ridingOffset(0.04F)`, `clientTrackingRange(8)`, **`notInPeaceful()`**); attributes `Vex.createAttributes()` plus **`FOLLOW_RANGE` 7.25**; `removeWhenFarAway` false, `requiresCustomPersistence` true; never `setLimitedLife`. No spawn egg, no natural spawn, no loot table. The summoner is the `AllyAttachments.SUMMONER` mark; without a summoner **online, alive and in the vex's level** the vex idles (no target, interactions `PASS`).
- The cap (§3.2): **12** per summoner, counted over every loaded level; a press at 12 answers `Request.CAPPED` with `message.spellcraft.too_many_vexes` ("You have too many vexes"), no cast, no cooldown, no sound; at 10 or 11 the cast summons the remainder; otherwise 3. The count is taken at the press and summoned at the perform.
- Following (§3.3): orbit band **2–4** blocks; teleport when farther than **12** blocks to an air cell within **2** blocks of the summoner; the stay toggle is the summoner's **empty main hand, not sneaking** click (`VEX_AMBIENT`; `SUCCESS_SERVER` on both sides, see Divergence 9); a staying vex wanders around its post (the bound origin set when stay was switched on); non-staying vexes within **16** blocks follow a dimension change onto ring cells (`VexRing`) around the arrival point; logout and death remove nothing.
- Fighting (§3.4): `BoundVex.isPrey` is round one's rule (attackable, summoner in the level, no ally, `canHarmPlayer` for players) plus the same PvP rule for a mob carrying another player's summoner mark (Divergence 15). Target goals in order: hurt-by-the-summoner's-side (creepers allowed), join-an-ally's-fight (allies within the guard area, never the vex itself), nearest hostile (`Enemy`, no `Creeper`, inside the **8×8×7** guard box, line of sight). Every target goal reads its clock from `BoundVex.scanDue(SCAN_INTERVAL)` (**10**) and keeps a target only while `BoundVex.keepsTarget`: alive, `canAttack`, still prey, within **7.25** blocks. When a Bound Vex takes a **new** target, the summoner's tamed, un-sitting, idle wolves within **16** blocks that `wantsToAttack` it take it too.
- Flee, dodge, repair (§3.5–§3.7): a creeper within **8** blocks with `getSwellDir() > 0` → fly straight away to **8** blocks until it stops; an iron golem within **3** blocks mid-swing (`getAttackAnimationTick() > 0`) whose head faces the vex (dot > **0.5**) → dart **3** blocks sideways, once per swing; an ally golem in the guard area under full health → one vex (a transient claim map that frees itself when the claimant dies) flies within **1.5** blocks of its chest, conjures an **iron ingot** on arrival, and every **30** ticks swings, plays `IRON_GOLEM_REPAIR` and heals **25**; the ingot goes and the given item returns when the golem is full or the repair stops.
- Pure core (`spell.rules`): no `net.minecraft`/NeoForge/jspecify imports (ArchUnit rule 2 allows only `java..`). Client-only code only under `client` (rule 1): the renderer registration lives in `client.evoking`. Common code never references `client`.
- Error Prone runs with `-Werror`; suppress only with `@SuppressWarnings("<Check>")` plus a comment why. Compare entities by UUID where you can, to need no `ReferenceEquality` suppression. **Every new private constant, field or method needs a reader in the same task** (`UnusedVariable`/`UnusedMethod` fail the build). **`-Xlint:all` is on too:** never assign `getMoveControl()` to a local or field (`MoveControl` is raw there and `rawtypes` fails the build); call through it (`vex.getMoveControl().setWantedPosition(...)`) as vanilla does.
- **Unit test style:** new unit and jqwik test classes follow the methodology's *Unit test style* (`~/.claude/development-methodology.md`): a Javadoc per test ("When …, the method should …" then `<p>Method under test: {@link …}`), one `@Nested` class per method under test, `// Arrange` / `// Act` / `// Assert` sections except in one-statement tests. GameTests keep their files' style (the same Javadoc shape, `static void` bodies, `helper.assert…`).
- GameTests: the shared structure is a 3×3×3 box in a barrier shell that blocks line of sight; a test whose mob flies, teleports or needs headroom, or that parks many vexes, registers through **`FangGameTests.addPadded`** (padding 13, the long tick budget; the padded box is air 13 blocks down, so lay stone with `helper.setBlock` under anything that must stand). **Hostile test mobs are husks** (`EntityTypes.HUSK`, `net.minecraft.world.entity.monster.zombie.Husk`: an `Enemy` that does not burn in daylight), never zombies or skeletons; animals are pigs; a golem needs the padded box (2.7 tall; it suffocates under the shared box's ceiling) and stone under it. Every spawned test mob gets `setNoAi(true)` unless the test is about its AI, and is discarded through `whenDone` (golems too: they stand in the padding, outside the structure's own cleanup). A test that mutates global state gets its own environment name. The GameTest clock: a mob's `canUse` runs only on ticks where `(tickCount + id)` is even (api-notes §21), so a scan "every 10 ticks" fires at most once per 10 ticks and a test waiting on one needs a budget of at least 20 ticks beyond its setup.
- **Execution order:** **Task 1 first** (api-notes §21, cited by every Javadoc). Task 2 (pure) is independent. Task 3 needs Tasks 1–2 and is the big switch (registration, summoning, cap); Tasks 4, 5 and 6 are **strictly sequential (3 → 4 → 5 → 6)**: each adds goals to `BoundVex.registerGoals` and tests to `BoundVexGameTests`, and each reads constants the previous one declared; Task 7 is last. Only Task 3 edits `SpellcraftGameTests`'s static block and `Spellcraft`'s constructor.
- **No red run for GameTest tasks (3–6):** their test steps compile only with the production change; the red-then-green cycle is for the pure-core task (2). A GameTest step's "Expected" is the green run after the production code lands.
- **Verification recipe:** every Gradle verification step runs `./gradlew <task> --console=plain > build/<task>.log 2>&1; echo "exit=$?"; tail -40 build/<task>.log` (never `| tail`, which hides Gradle's exit status). Expected: `exit=0`. `runGameTestServer` takes minutes; run it in the background and read the log when it is done. Its last line names the count: expect `All N required tests passed` with no `FAILED` line, where N is **the previous task's total plus the tests this task registers** (each task states its delta and the expected total; if a fix changed the registrations, recount the `registerAll` lines rather than deleting a test to match).
- **Format before you build:** in every task that writes Java, run `./gradlew spotlessApply --console=plain` right before the build/commit step and stage every file it changed in your task's files (check `git status --short`). Spotless also removes unused imports.
- Visual checks belong to Will: never claim a visual works; add `docs/playtest-checklist.md` items instead (Task 7).
- Commits: conventional (gitlint), **title ≤ 100 characters** (count before committing), body lines ≤ 80, no attribution lines. The commit gate runs `spotlessCheck` and `build` (minutes); pre-push runs `runGameTestServer`. **Run the `git add` line and the `git commit` line as two separate tool calls**, then check `git log -1 --oneline` landed.
- Release (bump `mod_version`, tag) is not in this plan: the version bumps once, after Part 7 (§12).

## File Structure

```text
src/main/java/io/github/willrieske/spellcraft/
├── Spellcraft.java                               + ModEntities.ENTITIES.register(modEventBus)
├── spell/rules/VexCap.java                       PURE, new: CAP 12, BATCH 3, toSummon(count)
├── spell/rules/GuardArea.java                    PURE, new: the 8×8×7 box, REACH 7.25, contains, withinReach
├── spell/rules/VexRing.java                      lifeTicks and its constants removed (no life limit)
├── evoking/ModEntities.java                      new: the entity register, BOUND_VEX, the attribute event
├── evoking/BoundVex.java                         new: the mob (data, persistence, summoner, isPrey, keepsTarget,
│                                                 scanDue, inGuardArea, stay toggle, goal wiring, wolves join,
│                                                 the ChargeGoal and StayWanderGoal copies)
├── evoking/FollowSummonerGoal.java               new (Task 4): orbit, teleport at 12
├── evoking/BoundVexEvents.java                   new (Task 4): portal follow (two events), logout and stop cleanup
├── evoking/HurtBySummonerSideGoal.java           new (Task 5): the four memories
├── evoking/AllyFightGoal.java                    new (Task 5): an ally's current target
├── evoking/NearestHostileGoal.java               new (Task 5): Enemy, no creeper, the guard box, on the scan clock
├── evoking/FleeCreeperGoal.java                  new (Task 6)
├── evoking/DodgeGolemGoal.java                   new (Task 6)
├── evoking/RepairGolemGoal.java                  new (Task 6): claims, the ingot, 25 per tap
├── evoking/VexSummons.java                       summon(level, summoner, count) creates Bound Vexes; onJoin/install/isPrey removed
├── evoking/EvokingCaster.java                    Request.CAPPED, TOO_MANY_KEY, the count at the press
├── client/evoking/BoundVexRenderers.java         new: RegisterRenderers → VexRenderer::new
└── gametest/
    ├── SpellcraftGameTests.java                  + BoundVexGameTests.registerAll()
    ├── GameTestSupport.java                      + drainSounds (Task 6)
    ├── VexGameTests.java                         E8, E9, peaceful, prey rule updated; the five install-goal tests removed
    ├── AlliesGameTests.java                      VexSummons.isPrey → BoundVex.isPrey (three calls, one link)
    └── BoundVexGameTests.java                    new: cap, stay, idle, persistence (Task 3); follow/teleport/portal (4);
                                                  targeting and wolves (5); flee/dodge/repair (6)
src/main/resources/assets/spellcraft/lang/en_us.json   + entity.spellcraft.bound_vex, message.spellcraft.too_many_vexes
src/test/java/io/github/willrieske/spellcraft/
    spell/rules/VexCapTest.java, VexCapPropertiesTest.java, GuardAreaTest.java, GuardAreaPropertiesTest.java (new)
    spell/rules/VexRingTest.java                  the LifeTicks group removed
docs/api-notes.md                                 §21: the facts below
docs/playtest-checklist.md                        the Evoking section's vex lines (135, 146, 164); a new "Evoking II (Part 2)" section
docs/mutation-testing.md                          a row for this part
README.md                                         the Evoking paragraph (vexes)
Not modified: src/main/templates/META-INF/neoforge.mods.toml (no new spell), CLAUDE.md (the packet rule is unchanged; no Mixin), gradle.properties (no version bump), the Evoking spec's one packet (nothing is sent clientbound).
```

## Divergences from the spec (for the plan review's Phase 2)

The spec was written before these lines of game code were read; the plan follows the code. This list is the record until the tasks run; the Divergence index at the end fills from the as-built notes.

1. **Vanilla's vex goals are private.** §3.1 says the charge and wander goals are inherited. `Vex.VexChargeAttackGoal`, `VexRandomMoveGoal`, `VexCopyOwnerTargetGoal` and `VexMoveControl` are all `private class` (`Vex.java:222,280,299,329`), so a subclass can neither reuse nor subclass them. `BoundVex.registerGoals` does **not** call `super.registerGoals()` and adds its own copies of the charge and wander goals (the wander copy runs only while staying, §3.3). The move control *is* inherited: `Vex`'s constructor assigns the private `VexMoveControl` instance to the protected `moveControl` field (`Vex.java:59`).
2. **Peaceful is a builder flag.** §3.1 says "a `Monster` subclass still refuses to spawn on Peaceful via `EntityType.create`". It is `EntityType.Builder.notInPeaceful()` (`EntityType.java:653`) that makes `canSpawn`/`create` refuse and `Mob.checkDespawn` discard the mob on Peaceful (`Mob.java:715-716`); a `Monster` built without it lives on Peaceful. The type is built with it, and `EvokingCaster` reads `ModEntities.BOUND_VEX.get().canSpawn(level)`.
3. **Only `STAYING` is synced in Part 2.** §3.1 lists `CASTER` (§9.1) and `TEAM_MOVE` (§8) too; nothing reads them before Parts 6 and 7, and Error Prone fails the build on unused private fields. Each later part adds its own.
4. **No `TeamMoveGoal` placeholder, no last-ground memory yet.** §3.3 lists a no-op `TeamMoveGoal` (Part 6) and the summoner's last ground position (for §6.3, Part 5). Both land with the part that reads them; Part 5 will also turn the orbit band into per-dimension values.
5. **Portal follow uses two events.** §3.3 names `PlayerChangedDimensionEvent` alone; it fires after the move (`ServerPlayer.java:1236`) and carries no "where the player left". `ServerPlayer.teleport` first posts the cancellable `EntityTravelToDimensionEvent` (`ServerPlayer.java:1177`) while the player still stands at the portal, so `BoundVexEvents` notes the followers there and moves them on the changed-dimension event. The travel event also fires for same-dimension teleports (pearls, `/tp`), so the note is taken only when the dimension changes.
6. **"Newest" memory → first fresh memory in the spec's order.** §3.4 asks for the newest of four memories. Their ages (`rememberer.tickCount − timestamp`) could be compared, but the spec's order already says what matters most (the vex's own attacker, then the summoner's attacker, then the summoner's victim, then an ally's attacker) and one pass is cheaper than four; the goal takes the first memory at most **100** ticks old in that order. The window matters because vanilla expires `lastHurtByMob` after 100 ticks (`LivingEntity.java:513-519`) but never expires `lastHurtMob`.
7. **Repair waits for peace.** §3.3 ranks the job goals (repair) above the charge goal, so a vex under attack would stop to heal a golem. `RepairGolemGoal` runs only while the vex has no target and stops when one appears; the priority order itself is kept.
8. **The repair ingot is held by the goal, not a stash.** §3.7 says "the given item is stashed, §4.5"; the stash is Part 3's. In Part 2 the goal keeps the previous main-hand stack in a field and puts it back in `stop()`. A vex that is unloaded, saved and reloaded, or carried through a portal while repairing keeps the ingot and loses the sword until Part 3's stash lands (noted for the playtest); the claim it held frees itself (the claim map checks whether the claimant is still alive).
9. **Interaction result.** §3.3 says `InteractionResult.SUCCESS`. `mobInteract` runs on both sides and the client does not know the summoner (the mark is not synced). The client must still answer a `Success` for the unsneaked empty-main-hand click, because `Minecraft.startUseItem` (`Minecraft.java:1760-1825`) falls through to the off-hand item on `PASS` and would throw a pearl or raise a shield on every toggle; so both sides answer `SUCCESS_SERVER` for that click (the swing comes from the server, `InteractionResult.java:11-12`), and the server alone checks the summoner, answering `PASS` for anyone else.
10. **No `swing(hand)` in 26.3.** §3.7's `swing(MAIN_HAND)` is `Mob.swing(InteractionHand, SwingAnimation)` (`Mob.java:1529`) with `SwingAnimation.DEFAULT`; `VexModel` has no arm swing, so the repair sound is the visible cue.
11. **Cross-dimension teleport makes a new entity.** `Entity.teleport` creates a fresh instance and `restoreFrom` copies the save (`Entity.java:3209-3243`): serialized attachments survive, synced data does not. `STAYING` is therefore also saved as NBT (`staying`), and the portal code never touches the old instance after the call.
12. **The follow range bonus is removed.** `Mob.finalizeSpawn` adds a random ±11.5 % `FOLLOW_RANGE` modifier (`Mob.java:1120-1125`); `NearestAttackableTargetGoal`'s range reads the attribute (api-notes §19). `BoundVex.finalizeSpawn` removes it (`Mob.RANDOM_SPAWN_BONUS_ID`, protected, `Mob.java:122`), as round one's `install` did, so the targeting range is exactly 7.25.
13. **`VexSummons.onJoin`/`install` go away.** §3.1 drops the `onJoin` goal install; with them goes the `AGGRO_RANGE` 5 rule of round one (the guard box replaces it). A vanilla `Vex` that carries a summoner mark (the test helper `GameTestSupport.summonedVex`, and any round-one vex still alive in a saved world) keeps vanilla's goals from now on, player targeting included for its remaining life; it is still an ally and spared.
14. **No `RandomLookAroundGoal`.** §3.3 lists one at priority 6; vanilla's `Vex.registerGoals` (`:86-96`) has none, only the two `LookAtPlayerGoal`s, which the Bound Vex copies.
15. **The PvP rule covers other players' summons.** §3.4 applies `canHarmPlayer` to player targets. Another player's Bound Vex is a `Monster` and so an `Enemy`; without more, two players' vexes would fight each other with the `pvp` rule off. `isPrey` treats a mob carrying another player's summoner mark like that player: prey only if the summoner may harm its owner.
16. **The patrol goal scans on the shared clock, not vanilla's dice.** §3.4 says "every 10 ticks"; `NearestAttackableTargetGoal` rolls `random.nextInt(5)` per call instead (api-notes §21), which made a 20-tick test flaky. `NearestHostileGoal` is built with `randomInterval` 1 and gates `canUse` on `scanDue(SCAN_INTERVAL)`, like the other two target goals.

---

### Task 1: Record the verified 26.3 API facts

**Files:**

- Modify: `docs/api-notes.md` (append a §21 after §20; correct one §19 bullet)

**Interfaces:**

- Consumes: nothing.
- Produces: the section every later task's Javadoc and comments cite (`docs/api-notes.md §21`), so this task runs first.

- [x] **Step 1: Append §21 to `docs/api-notes.md`**

Append after the last line of §20:

````markdown

## 21. Evoking II, Part 2: the Bound Vex (entity type, goals, teleports) (Evoking II Part 2 task)

Verified 2026-11-08 against the same unpacked sources (`build/mc-src`, `build/nf-src`).

- `Vex` (`monster/Vex.java`): `public class Vex extends Monster implements TraceableEntity, OwnableEntity` (`:47`), one
  constructor `Vex(EntityType<? extends Vex>, Level)` (`:57`) whose body (`:58-61`) calls `super` and then assigns
  `this.moveControl = new VexMoveControl<>(this)` (`:59`), so a subclass inherits the move control.
  `createAttributes()` (`:98`) is `Monster.createMonsterAttributes()` plus `MAX_HEALTH` 14 and `ATTACK_DAMAGE` 4.
  `registerGoals` (`:86-96`): goalSelector `0 FloatGoal`, `4 VexChargeAttackGoal`, `8 VexRandomMoveGoal`,
  `9 LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F)`, `10 LookAtPlayerGoal(this, Mob.class, 8.0F)` (no
  `RandomLookAroundGoal`); targetSelector `1 HurtByTargetGoal(this, Raider.class).setAlertOthers()`,
  `2 VexCopyOwnerTargetGoal`, `3 NearestAttackableTargetGoal<>(this, Player.class, true)`.
  **All four inner classes are `private class`** (`VexChargeAttackGoal :222`, `VexCopyOwnerTargetGoal :280`,
  `VexMoveControl :299`, `VexRandomMoveGoal :329`): a subclass in another package can neither construct nor extend them;
  `BoundVex` keeps its own copies of the charge and wander goals and skips `super.registerGoals()`. `tick()` (`:74-83`):
  `noPhysics = true; super.tick(); noPhysics = false; setNoGravity(true)`, then the life countdown only while the private
  `hasLimitedLife` is set, so a vex that never gets `setLimitedLife` lives on. `boundOrigin` (`:53`) through
  `setBoundOrigin`/`getBoundOrigin` (`:145-152`); `isCharging`/`setIsCharging` (`:169-175`); `finalizeSpawn` (`:207`) gives the
  iron sword (`populateDefaultEquipmentSlots`, `:217-220`); `getAmbientSound` is `VEX_AMBIENT` (`:187`). `Vex` overrides none of
  `removeWhenFarAway`, `checkDespawn`, `requiresCustomPersistence`; `Mob.removeWhenFarAway(double)` is public and true
  (`Mob.java:702`), `Mob.requiresCustomPersistence()` is public (`:706`, declared in `Mob`, not `Entity`).
  `Mob.RANDOM_SPAWN_BONUS_ID` is `protected static` (`Mob.java:122`); `finalizeSpawn` adds that random `FOLLOW_RANGE`
  modifier (`:1120-1125`), so a subclass removes it by that name without redeclaring it.
- Peaceful: `Mob.checkDespawn` (`Mob.java:711-741`) discards a mob whose type is not `isAllowedInPeaceful()` (`:715-716`,
  `EntityType.java:501`), set by `EntityType.Builder.notInPeaceful()` (`:653`); `EntityType.canSpawn(Level)` (`:339`) and
  `create` (`:348`) refuse on Peaceful for the same flag. Vanilla's `VEX` is built (`EntityTypes.java:1056-1066`)
  `EntityType.Builder.of(Vex::new, MobCategory.MONSTER).fireImmune().sized(0.4F, 0.8F).eyeHeight(0.51875F)
  .passengerAttachments(0.7375F).ridingOffset(0.04F).clientTrackingRange(8).notInPeaceful()` (no `updateInterval`: the default 3).
- Registration: `DeferredRegister.createEntities(modid)` (`nf-src/.../registries/DeferredRegister.java:184`) →
  `DeferredRegister.Entities.registerEntityType(String, EntityType.EntityFactory<E>, MobCategory, UnaryOperator<EntityType.Builder<E>>)`
  (`:824`), which builds with a `ResourceKey` itself; `EntityFactory<T>.create(EntityType<T>, Level)` (`EntityType.java:713-716`).
  `EntityAttributeCreationEvent` is a mod-bus event (`nf-src/.../event/entity/EntityAttributeCreationEvent.java:23`) with
  `put(EntityType<? extends LivingEntity>, AttributeSupplier)` (`:30`); `AttributeSupplier.Builder.build()`.
- Renderer: `EntityRenderersEvent.RegisterRenderers.registerEntityRenderer(EntityType<? extends T>, EntityRendererProvider<T>)`
  (`nf-src/.../client/event/EntityRenderersEvent.java:103`, mod bus); `VexRenderer extends MobRenderer<Vex, VexRenderState, VexModel>`
  (`client/renderer/entity/VexRenderer.java:15`, not final, constructor `(EntityRendererProvider.Context)` `:19`), so
  `registerEntityRenderer(ModEntities.BOUND_VEX.get(), VexRenderer::new)` compiles with `T = Vex`. `extractRenderState` copies
  `isCharging()` into the render state (`:36-39`), and `getTextureLocation` picks the charging texture from it. `VexModel` has no
  arm swing.
- Synced data: `SynchedEntityData.defineId(Class<? extends SyncedDataHolder>, EntityDataSerializer<T>)` (`:30`),
  `EntityDataSerializers.BOOLEAN` (`:89`); `Entity.defineSynchedData(SynchedEntityData.Builder)` (`:428`, `Vex` overrides at `:103-106`
  and must be called through `super`); `Builder.define(accessor, value)`. Save: `addAdditionalSaveData(ValueOutput)` /
  `readAdditionalSaveData(ValueInput)` (`Entity.java:2288-2290`); `ValueOutput.putBoolean` (`:17`), `ValueInput.getBooleanOr` (`:29`).
- Interaction: `Mob.interact(Player, InteractionHand, Vec3)` (`Mob.java:1160`) is the public entry (name tag and spawn egg first,
  then `Entity.interact`, then `mobInteract`); `Mob.mobInteract(Player, InteractionHand)` is **protected** (`:1214`) and runs on
  both sides. `InteractionResult` is a sealed interface (`InteractionResult.java:11-16`): `SUCCESS` (client-predicted swing),
  `SUCCESS_SERVER` (the server sends the swing), `CONSUME`, `FAIL`, `PASS`, `TRY_WITH_EMPTY_HAND`; `interact` treats a result as
  handled when `consumesAction()` (`Mob.java:1165,1174`). On the client, `Minecraft.startUseItem` (`Minecraft.java:1760-1825`)
  stops at the first `InteractionResult.Success` and otherwise goes on to the next hand, so a client-side `PASS` for the main hand
  lets the off-hand item be used (a pearl thrown, a shield raised). `IronGolem.mobInteract` (`:262-278`) heals `25.0F`, plays
  `IRON_GOLEM_REPAIR` at pitch `1 + (r − r) · 0.2` and returns `SUCCESS`.
- Teleport: `Entity.teleport(TeleportTransition)` (`Entity.java:3177`) returns the entity to use afterwards, or null when
  NeoForge's `EntityTravelToDimensionEvent` is cancelled (`CommonHooks.onTravelToDimension`, `nf-src/.../common/CommonHooks.java:841-845`,
  posted from `Entity.teleport` `:3178` **and** `ServerPlayer.teleport` `ServerPlayer.java:1177`, before the move, for **every**
  teleport, same dimension included: `ServerPlayer.teleport` returns early for a same-dimension move at `:1190`, after the post).
  Same dimension returns `this` (`:3192`); **cross-dimension creates a new entity** with `getType().create(newLevel, DIMENSION_TRAVEL)`,
  calls `newEntity.restoreFrom(this)` (save and load: NBT fields and serialized attachments survive, synced data and plain fields do
  not, goals are rebuilt by the constructor) and removes the old one (`:3209-3243`). `TeleportTransition(ServerLevel, Vec3 pos, Vec3 speed,
  float yRot, float xRot, PostTeleportTransition)` (`TeleportTransition.java:28`), `TeleportTransition.DO_NOTHING` (`:24`).
  `PlayerEvent.PlayerChangedDimensionEvent` (`nf-src/.../event/entity/player/PlayerEvent.java:517`; game bus) has `getFrom()`,
  `getTo()` (`:527,531`) and `getEntity()`, posted at the end of `ServerPlayer.teleport` (`ServerPlayer.java:1236`) only when the
  dimension changed. `EntityTravelToDimensionEvent` (`nf-src/.../event/entity/EntityTravelToDimensionEvent.java:24`) has `getEntity()`
  and `getDimension()` (`:32`). Entities by UUID: `ServerLevel.getEntities()` (`:1875`, a `LevelEntityGetter<Entity>`) `.get(UUID)`.
- Creeper, golem, wolf: `Creeper.getSwellDir()` (`:195`) is the synced swell direction: `-1` by default (`defineSynchedData`,
  `Creeper.java:100`), `1` while `SwellGoal` runs (`SwellGoal.java:43-50`) or once ignited (`tick`, `:129-131`); a NoAI creeper
  stays at `-1` until a test calls `setSwellDir(1)` (`:199`; `ignite()` `:265`). The fuse length is the `Fuse` short tag (`:109,118`,
  default 30). `IronGolem.getAttackAnimationTick()` (`:237`, public) counts down from 10 after a hit; `handleEntityEvent((byte) 4)`
  sets it to 10 on either side (`:224-227`). `TamableAnimal.isTame()` (`:115`), `isOrderedToSit()` (`:238`), `isOwnedBy(LivingEntity)`
  (`:183`), `tame(Player)` (`:170`), `setOrderedToSit(boolean)` (`:242`). `Wolf.wantsToAttack(LivingEntity target, LivingEntity owner)`
  (`animal/wolf/Wolf.java:641-651`) is false for creepers, ghasts, armor stands, the owner's own tamed wolves, players the owner may
  not harm (`canHarmPlayer`), tamed horses and tamed animals. `Mob.swing(InteractionHand, SwingAnimation)` (`Mob.java:1529`;
  there is no `swing(InteractionHand)` in 26.3), `SwingAnimation.DEFAULT` (`world/item/component/SwingAnimation.java:11-12`).
  `LivingEntity.heal(float)` (`:1197`), `getMaxHealth()` (`:2083`). `SoundEvents.IRON_GOLEM_REPAIR` (`:894`), `VEX_AMBIENT` (`:1673`),
  `VEX_CHARGE` (`:1674`).
- Looking: `Entity.getLookAngle()` (`Entity.java:2676-2678`) is `calculateViewVector(getXRot(), getYRot())`, the **body** yaw;
  `LivingEntity` does not override it. The **head** vectors are `Entity.getHeadLookAngle()` (`:2684`, `getYHeadRot()`) and
  `getViewVector(float)` (`:1986`, through `getViewYRot`, which `LivingEntity` overrides to the head yaw, `:3462`). A golem winding up a
  swing turns its head toward its target before its body follows, so "the golem faces the vex" reads `getHeadLookAngle()`, and a
  test turns the golem with `setYHeadRot`.
- The goal clock: `Mob.serverAiStep` (`Mob.java:750-764`) runs the full `targetSelector.tick()` and `goalSelector.tick()` (the only
  place a goal's `canUse` is asked) only when `tickCount + getId()` is even (or `tickCount <= 1`); on odd values it only ticks the
  running goals (`tickRunningGoals(false)`). So `canUse` is asked every second tick, on a parity set by the entity id, and a
  `tickCount % 10 == 0` gate never opens for a mob with an odd id. A gate on `(tickCount + getId()) % 10 == 0` always lands on a
  full tick, once every 10 ticks, for any id (`BoundVex.scanDue`).
- Goals: `Goal` (`ai/goal/Goal.java`): `canUse()` abstract (`:12`), `canContinueToUse()` defaults to `canUse()` (`:14`),
  `requiresUpdateEveryTick()` false (`:28`), `setFlags(EnumSet<Flag>)` (`:35`), `reducedTickDelay(int)` (`:53`, `ceil(ticks / 2)`),
  `Flag {MOVE, LOOK, JUMP, TARGET}`. `TargetGoal(Mob, boolean mustSee)` (`:26`). **`TargetGoal.start()` (`:76-81`) does not set the
  target**: it only zeroes the reach cache and the unseen counter. Vanilla's subclasses set it in their own `start()`
  (`NearestAttackableTargetGoal.java:74-77`, `HurtByTargetGoal.java:60-69`: `mob.setTarget(...)` then `super.start()`), so a custom
  `TargetGoal` must call `mob.setTarget(...)` itself in `start()`. `TargetGoal.stop()` (`:83-87`) sets the target to null.
  `TargetGoal.canContinueToUse()` (`:37-72`) takes `mob.getTarget()` (falling back to the private `targetMob`), drops it on
  `!mob.canAttack(target)`, on a shared team, beyond `FOLLOW_RANGE` (`getFollowDistance()`) and, with `mustSee`, after
  `unseenMemoryTicks` (60) without line of sight, and **ends with `mob.setTarget(target)`**, so it re-sets the target on every full
  tick. A goal that overrides `canContinueToUse` chooses whether to keep those checks: the Bound Vex grudge and ally goals
  (no sight rule) use `BoundVex.keepsTarget` alone and do not call `super`; the patrol goal calls `keepsTarget` and then `super`
  for the sight rule. A `setTarget` override must therefore tell a re-set of the same target from a new one.
  `NearestAttackableTargetGoal(Mob, Class<T>, boolean mustSee, TargetingConditions.Selector)` (`:25`) uses a random interval
  of 10 → `reducedTickDelay` 5 → `canUse` (`:44-51`) goes on only when `random.nextInt(5) == 0`; the six-argument constructor
  `(Mob, Class<T>, int randomInterval, boolean mustSee, boolean mustReach, Selector)` (`:33-40`) with `randomInterval` 1 makes the roll
  always pass (`nextInt(1) == 0`). Protected `getTargetSearchArea(double)` (`:53`), `findTarget()` (`:57`), protected fields
  `targetConditions`, `target` (`:16-19`). `TargetingConditions.Selector.test(LivingEntity target, ServerLevel level)` (`:99-100`,
  **target first**). `Mob.goalSelector` and `targetSelector` are `public final` (`Mob.java:135-136`); `GoalSelector.addGoal(int, Goal)`
  (`:28`), `removeAllGoals(Predicate<Goal>)` (`:32-40`, tests the inner `Goal`, not the `WrappedGoal`), `getAvailableGoals()` (`:109`,
  the `WrappedGoal`s: `getGoal()`, `getPriority()`, `isRunning()`). `Mob.getTarget()` returns null for a target `canAttack` refuses
  (a creative or spectator player, an invulnerable mob). `MoveControl<T extends Mob>` is generic (`ai/control/MoveControl.java:15`);
  `Mob.getMoveControl()` returns the raw type (`Mob.java:214`), `hasWanted()` (`:32`), `setWantedPosition(double, double, double,
  double)` (`:40`), `setWait()` (`:165`). `Mob.doHurtTarget(ServerLevel, Entity)` (`:1415`), `Mob.getLookControl()` (`:210`),
  `LookControl.setLookAt(double, double, double, float, float)` (`:38`).
- Memories: `LivingEntity.getLastHurtByMob()` (`:638`), `getLastHurtByMobTimestamp()` (`:651`, the victim's `tickCount` at the hit),
  `setLastHurtByMob` (`:668`), `getLastHurtMob()` (`:673`), `getLastHurtMobTimestamp()` (`:677`), `setLastHurtMob(Entity)` (`:681`).
  `LivingEntity.tick` forgets `lastHurtByMob` when it dies or after 100 ticks (`:513-519`), but forgets `lastHurtMob` only when it
  dies (`:509-511`): a "fresh memory" rule needs its own age check.
- Queries: `ServerLevel.getEntities(EntityTypeTest<Entity, T>, Predicate<? super T>)` → `List<? extends T>` (`:941`, the whole level);
  `EntityType<T>` is an `EntityTypeTest<Entity, T>` (`EntityType.java:54`); `MinecraftServer.getAllLevels()` → `Iterable<ServerLevel>`
  (`:1213`); `EntityGetter.getEntitiesOfClass(Class<T>, AABB, Predicate<? super T>)` (`:23`) returns entities whose **bounding box
  intersects** the `AABB`, not whose position is inside it. `AABB.contains(double, double, double)` (`AABB.java:264-266`) is
  half-open (`min <= x < max`). So a "position inside the closed guard box" check filters the query's result through
  `GuardArea.contains` (`BoundVex.inGuardArea`). `Enemy` is a marker interface every `Monster` implements (`Monster.java:30`), neutral
  ones (endermen, zombified piglins) and Bound Vexes included. `AABB(Vec3, Vec3)` (`:36`), `inflate(double, double, double)` (`:190`);
  `LevelReader.isEmptyBlock(BlockPos)` (`:90`, air only); `Entity.teleportTo(double, double, double)` (`:3477`, same level, keeps the
  velocity) and `getRandom()` (`:4261`).
- GameTests: `GameTestHelper.spawn(EntityType<E>, BlockPos)` (`:161`) creates with `EntitySpawnReason.STRUCTURE`, calls
  `setPersistenceRequired()` and **not** `finalizeSpawn` (no iron sword) (`GameTestEntityBuilder.java:44-67`); `spawn(type, pos,
  EntitySpawnReason)` (`:181`) does call it. `helper.getEntities(EntityType<T>)` (`:660`). A `Creeper` with `setSwellDir(1)` and a
  large `Fuse` hisses without exploding until the swell counts up to `maxSwell`.
````

- [x] **Step 2: Correct the §19 `canContinueToUse` bullet**

In `docs/api-notes.md` §19, replace the bullet

```markdown
- `TargetGoal.canContinueToUse` (`TargetGoal.java:37`) re-checks only `canAttack` and never the selector, so an
  anonymous subclass is needed to drop a target that became an ally.
```

with

```markdown
- `TargetGoal.canContinueToUse` (`TargetGoal.java:37-72`) never re-checks the selector (it checks `canAttack`, the
  team, the follow range and, with `mustSee`, line of sight, then re-sets the target; §21), so an anonymous subclass is
  needed to drop a target that became an ally.
```

- [x] **Step 3: Lint and commit**

Run: `pre-commit run markdownlint-cli2 --files docs/api-notes.md`
Expected: `Passed`.

```bash
git add docs/api-notes.md
```

```bash
git commit -m "docs(api-notes): record the Evoking II Part 2 facts (vex internals, goal clock, teleports)"
```

Then `git log -1 --oneline` shows the commit.

> **As built (`d999052`):** matches plan.

---

### Task 2: Pure `VexCap` and `GuardArea`

**Files:**

- Create: `src/main/java/io/github/willrieske/spellcraft/spell/rules/VexCap.java`
- Create: `src/main/java/io/github/willrieske/spellcraft/spell/rules/GuardArea.java`
- Test: `src/test/java/io/github/willrieske/spellcraft/spell/rules/VexCapTest.java`
- Test: `src/test/java/io/github/willrieske/spellcraft/spell/rules/VexCapPropertiesTest.java`
- Test: `src/test/java/io/github/willrieske/spellcraft/spell/rules/GuardAreaTest.java`
- Test: `src/test/java/io/github/willrieske/spellcraft/spell/rules/GuardAreaPropertiesTest.java`

**Interfaces:**

- Consumes: `VexRing.SUMMON_COUNT` (3).
- Produces: `VexCap.CAP` (int 12), `VexCap.BATCH` (int 3), `VexCap.toSummon(int count)` → int; `GuardArea.HALF_WIDTH` (double 4.0), `GuardArea.HALF_HEIGHT` (double 3.5), `GuardArea.REACH` (double 7.25), `GuardArea.contains(double dx, double dy, double dz)` → boolean, `GuardArea.withinReach(double dx, double dy, double dz)` → boolean. Task 3 uses `VexCap`, and `BoundVex.inGuardArea` and `BoundVex.withinReach` (Task 3) call `GuardArea.contains` and `GuardArea.withinReach`; Tasks 5 and 6 reach the box only through those two `BoundVex` methods.

- [x] **Step 1: Write the failing tests**

`src/test/java/io/github/willrieske/spellcraft/spell/rules/VexCapTest.java`:

```java
package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VexCapTest {
    @Nested
    class ToSummon {
        /**
         * When the summoner has room for a whole batch (0 to 9 vexes), toSummon should answer three; at 10 and 11 it
         * should answer the remainder up to twelve; at twelve or more it should answer zero.
         *
         * <p>Method under test: {@link VexCap#toSummon(int)}
         */
        @ParameterizedTest(name = "{0} vexes → summon {1}")
        @CsvSource({"0, 3", "1, 3", "9, 3", "10, 2", "11, 1", "12, 0", "13, 0", "100, 0"})
        void threeThenTheRemainderThenNothing(int count, int expected) {
            assertThat(VexCap.toSummon(count)).isEqualTo(expected);
        }

        /**
         * When the count is negative, toSummon should refuse it: a negative count is a caller bug, not a summoner with
         * room for more.
         *
         * <p>Method under test: {@link VexCap#toSummon(int)}
         */
        @Test
        void rejectsANegativeCount() {
            assertThatThrownBy(() -> VexCap.toSummon(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("-1");
        }
    }

    @Nested
    class Constants {
        /**
         * When the cap is read, it should be twelve (Evoking II spec §3.2).
         *
         * <p>Field under test: {@link VexCap#CAP}
         */
        @Test
        void capIsTwelve() {
            assertThat(VexCap.CAP).isEqualTo(12);
        }

        /**
         * When the batch is read, it should be three, the ring's summon count; a change to the ring shows up here.
         *
         * <p>Field under test: {@link VexCap#BATCH}
         */
        @Test
        void batchIsThree() {
            assertThat(VexCap.BATCH).isEqualTo(3);
        }
    }
}
```

`src/test/java/io/github/willrieske/spellcraft/spell/rules/VexCapPropertiesTest.java`:

```java
package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Group;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class VexCapPropertiesTest {
    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class ToSummon {
        /**
         * For any count up to the cap, the count plus what toSummon answers should never exceed the cap, and the
         * answer should never exceed a batch or go negative.
         *
         * <p>Method under test: {@link VexCap#toSummon(int)}
         */
        @Property
        void neverOverTheCapAndNeverMoreThanABatch(@ForAll @IntRange(min = 0, max = 12) int count) {
            // Act
            int summoned = VexCap.toSummon(count);
            // Assert
            assertThat(summoned).isBetween(0, VexCap.BATCH);
            assertThat(count + summoned).isLessThanOrEqualTo(VexCap.CAP);
        }

        /**
         * For any count at or over the cap, toSummon should answer zero.
         *
         * <p>Method under test: {@link VexCap#toSummon(int)}
         */
        @Property
        void nothingAtOrOverTheCap(@ForAll @IntRange(min = 12, max = 1000) int count) {
            assertThat(VexCap.toSummon(count)).isZero();
        }

        /**
         * For any count under the cap, toSummon should answer at least one: a summoner with room always gets a vex.
         *
         * <p>Method under test: {@link VexCap#toSummon(int)}
         */
        @Property
        void roomMeansAtLeastOne(@ForAll @IntRange(min = 0, max = 11) int count) {
            assertThat(VexCap.toSummon(count)).isPositive();
        }
    }
}
```

`src/test/java/io/github/willrieske/spellcraft/spell/rules/GuardAreaTest.java`:

```java
package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GuardAreaTest {
    @Nested
    class Contains {
        /**
         * When a point sits inside the 8×8×7 box or on its faces, contains should answer true; just past a face (0.01
         * blocks) it should answer false, on every axis and both signs.
         *
         * <p>Method under test: {@link GuardArea#contains(double, double, double)}
         */
        @ParameterizedTest(name = "({0}, {1}, {2}) inside = {3}")
        @CsvSource({
            "0, 0, 0, true",
            "4, 3.5, 4, true",
            "-4, -3.5, -4, true",
            "4.01, 0, 0, false",
            "-4.01, 0, 0, false",
            "0, 3.51, 0, false",
            "0, -3.51, 0, false",
            "0, 0, 4.01, false",
            "0, 0, -4.01, false"
        })
        void edgesAreInsideAndJustPastThemIsOut(double dx, double dy, double dz, boolean inside) {
            assertThat(GuardArea.contains(dx, dy, dz)).isEqualTo(inside);
        }

        /**
         * When any offset is NaN, contains should answer false: a bad position is never inside.
         *
         * <p>Method under test: {@link GuardArea#contains(double, double, double)}
         */
        @Test
        void nanIsOutside() {
            // Act & Assert
            assertThat(GuardArea.contains(Double.NaN, 0, 0)).isFalse();
            assertThat(GuardArea.contains(0, Double.NaN, 0)).isFalse();
            assertThat(GuardArea.contains(0, 0, Double.NaN)).isFalse();
        }
    }

    @Nested
    class WithinReach {
        /**
         * When a point is exactly 7.25 blocks away along one axis, withinReach should answer true; a hair farther it
         * should answer false; the box's far corner (about 6.65 blocks) should be within reach.
         *
         * <p>Method under test: {@link GuardArea#withinReach(double, double, double)}
         */
        @ParameterizedTest(name = "({0}, {1}, {2}) within reach = {3}")
        @CsvSource({
            "7.25, 0, 0, true",
            "0, 7.25, 0, true",
            "0, 0, -7.25, true",
            "7.26, 0, 0, false",
            "0, -7.26, 0, false",
            "0, 0, 7.26, false",
            "4, 3.5, 4, true",
            "5, 3.5, 5, false"
        })
        void reachIsSevenAndAQuarter(double dx, double dy, double dz, boolean within) {
            assertThat(GuardArea.withinReach(dx, dy, dz)).isEqualTo(within);
        }

        /**
         * When any offset is NaN or infinite, withinReach should answer false.
         *
         * <p>Method under test: {@link GuardArea#withinReach(double, double, double)}
         */
        @Test
        void nanAndInfinityAreOutOfReach() {
            // Act & Assert
            assertThat(GuardArea.withinReach(Double.NaN, 0, 0)).isFalse();
            assertThat(GuardArea.withinReach(0, Double.NaN, 0)).isFalse();
            assertThat(GuardArea.withinReach(0, 0, Double.NaN)).isFalse();
            assertThat(GuardArea.withinReach(Double.POSITIVE_INFINITY, 0, 0)).isFalse();
        }
    }
}
```

`src/test/java/io/github/willrieske/spellcraft/spell/rules/GuardAreaPropertiesTest.java`:

```java
package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Group;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;

class GuardAreaPropertiesTest {
    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class Contains {
        /**
         * For any offset, contains should answer the same for the mirrored offset: the box is centered on the vex.
         *
         * <p>Method under test: {@link GuardArea#contains(double, double, double)}
         */
        @Property
        void isSymmetric(
                @ForAll @DoubleRange(min = -10, max = 10) double dx,
                @ForAll @DoubleRange(min = -10, max = 10) double dy,
                @ForAll @DoubleRange(min = -10, max = 10) double dz) {
            assertThat(GuardArea.contains(dx, dy, dz)).isEqualTo(GuardArea.contains(-dx, -dy, -dz));
        }

        /**
         * For any point with two axes inside the box, putting the third axis exactly on a face (either sign) should be
         * inside, and 0.01 blocks past that face should be outside.
         *
         * <p>Method under test: {@link GuardArea#contains(double, double, double)}
         */
        @Property
        void aFaceIsInsideAndJustPastItIsOut(
                @ForAll @IntRange(min = 0, max = 2) int axis,
                @ForAll boolean negative,
                @ForAll @DoubleRange(min = -GuardArea.HALF_WIDTH, max = GuardArea.HALF_WIDTH) double u,
                @ForAll @DoubleRange(min = -GuardArea.HALF_HEIGHT, max = GuardArea.HALF_HEIGHT) double v) {
            // Arrange
            double half = axis == 1 ? GuardArea.HALF_HEIGHT : GuardArea.HALF_WIDTH;
            double sign = negative ? -1 : 1;
            double[] onFace = edgePoint(axis, sign * half, u, v);
            double[] pastFace = edgePoint(axis, sign * (half + 0.01), u, v);
            // Act & Assert
            assertThat(GuardArea.contains(onFace[0], onFace[1], onFace[2])).isTrue();
            assertThat(GuardArea.contains(pastFace[0], pastFace[1], pastFace[2])).isFalse();
        }

        /**
         * Builds a point whose {@code axis} coordinate is {@code edge}; of the other two, a horizontal one takes
         * {@code u} and the vertical one {@code v} (when the edge axis is vertical, both horizontals take {@code u}).
         */
        private static double[] edgePoint(int axis, double edge, double u, double v) {
            return switch (axis) {
                case 0 -> new double[] {edge, v, u};
                case 1 -> new double[] {u, edge, u};
                default -> new double[] {u, v, edge};
            };
        }
    }

    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class WithinReach {
        /**
         * For any point inside the box, withinReach should answer true: a vex can always fight what it guards.
         *
         * <p>Method under test: {@link GuardArea#withinReach(double, double, double)}
         */
        @Property
        void everyPointOfTheBoxIsWithinReach(
                @ForAll @DoubleRange(min = -GuardArea.HALF_WIDTH, max = GuardArea.HALF_WIDTH) double dx,
                @ForAll @DoubleRange(min = -GuardArea.HALF_HEIGHT, max = GuardArea.HALF_HEIGHT) double dy,
                @ForAll @DoubleRange(min = -GuardArea.HALF_WIDTH, max = GuardArea.HALF_WIDTH) double dz) {
            // Act & Assert
            assertThat(GuardArea.contains(dx, dy, dz) && GuardArea.withinReach(dx, dy, dz))
                    .isTrue();
        }

        /**
         * For any direction, a point just past the reach along it should be out of reach, and a point just short of it
         * within.
         *
         * <p>Method under test: {@link GuardArea#withinReach(double, double, double)}
         */
        @Property
        void reachIsASphere(
                @ForAll @DoubleRange(min = 0, max = 6.283) double yaw,
                @ForAll @DoubleRange(min = -1.57, max = 1.57) double pitch) {
            // Arrange
            double cx = Math.cos(pitch) * Math.cos(yaw);
            double cy = Math.sin(pitch);
            double cz = Math.cos(pitch) * Math.sin(yaw);
            double in = GuardArea.REACH - 0.01;
            double out = GuardArea.REACH + 0.01;
            // Act & Assert
            assertThat(GuardArea.withinReach(cx * in, cy * in, cz * in)).isTrue();
            assertThat(GuardArea.withinReach(cx * out, cy * out, cz * out)).isFalse();
        }
    }
}
```

`aFaceIsInsideAndJustPastItIsOut` draws `v` from the height range and `u` from the width range, so every non-edge coordinate stays inside its own half-size; `HALF_WIDTH` and `HALF_HEIGHT` are compile-time constants, which is what lets them appear in `@DoubleRange`.

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests "io.github.willrieske.spellcraft.spell.rules.VexCap*" --tests "io.github.willrieske.spellcraft.spell.rules.GuardArea*" --console=plain > build/test.log 2>&1; echo "exit=$?"; tail -40 build/test.log`
Expected: `exit=1`, compilation fails with `cannot find symbol ... VexCap` / `GuardArea`.

- [x] **Step 3: Write the two rules**

`src/main/java/io/github/willrieske/spellcraft/spell/rules/VexCap.java`:

```java
package io.github.willrieske.spellcraft.spell.rules;

/**
 * How many Bound Vexes one V press summons (Evoking II spec §3.2): a batch of three while there is room for a whole
 * batch, the remainder up to the cap when there is room for less, and none at the cap.
 */
public final class VexCap {
    /** The most Bound Vexes one summoner may have alive at once. */
    public static final int CAP = 12;

    /** How many one press summons while they all fit. */
    public static final int BATCH = VexRing.SUMMON_COUNT;

    private VexCap() {}

    /**
     * How many Bound Vexes to summon for a summoner who has {@code count} alive.
     *
     * @param count the summoner's living Bound Vexes, in every loaded level
     * @return {@link #BATCH} while {@code count + BATCH ≤ CAP}, else {@code CAP − count}, else 0
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public static int toSummon(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be at least 0, got " + count);
        }
        return Math.max(0, Math.min(BATCH, CAP - count));
    }
}
```

`src/main/java/io/github/willrieske/spellcraft/spell/rules/GuardArea.java`:

```java
package io.github.willrieske.spellcraft.spell.rules;

/**
 * The box a Bound Vex guards and the reach it fights at (Evoking II spec §3.4): 8 blocks east–west, 8 north–south and
 * 7 tall, centered on the vex, faces included, and a target farther than 7.25 blocks is dropped. Offsets are from the
 * vex's {@code Entity.position()} (its feet) to the other entity's {@code position()} (its feet), never eyes or box
 * centers.
 */
public final class GuardArea {
    /** Half the box's width, east–west and north–south. */
    public static final double HALF_WIDTH = 4.0;

    /** Half the box's height. */
    public static final double HALF_HEIGHT = 3.5;

    /** How far a vex keeps fighting a target; also its {@code FOLLOW_RANGE}. */
    public static final double REACH = 7.25;

    private GuardArea() {}

    /**
     * Whether a point is inside the guard box, faces included.
     *
     * @param dx blocks east of the vex
     * @param dy blocks above the vex
     * @param dz blocks south of the vex
     * @return true inside the box or on a face; false outside, and for NaN
     */
    public static boolean contains(double dx, double dy, double dz) {
        return Math.abs(dx) <= HALF_WIDTH && Math.abs(dy) <= HALF_HEIGHT && Math.abs(dz) <= HALF_WIDTH;
    }

    /**
     * Whether a point is within {@link #REACH} of the vex.
     *
     * @param dx blocks east of the vex
     * @param dy blocks above the vex
     * @param dz blocks south of the vex
     * @return true at or under the reach; false beyond it, and for NaN or infinity
     */
    public static boolean withinReach(double dx, double dy, double dz) {
        return dx * dx + dy * dy + dz * dz <= REACH * REACH;
    }
}
```

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew spotlessApply --console=plain` then the Step 2 command.
Expected: `exit=0`, `BUILD SUCCESSFUL`.

- [x] **Step 5: Mutation-test the two rules**

CLAUDE.md asks for PIT after a pure-core change; running it here catches a survivor while the rule is fresh (Task 7 records the whole part's numbers).

Run: `./gradlew pitest --console=plain > build/pitest.log 2>&1; echo "exit=$?"; tail -40 build/pitest.log`
Expected: `exit=0`. Open `build/reports/pitest/index.html` (reports are not timestamped) and read the `VexCap` and `GuardArea` rows: every mutant killed. If one survives, add a test to the matching test class above that kills it, rerun Step 4 and this step, and note the added test in the commit body.

- [x] **Step 6: Commit**

```bash
git add src/main/java/io/github/willrieske/spellcraft/spell/rules/VexCap.java src/main/java/io/github/willrieske/spellcraft/spell/rules/GuardArea.java src/test/java/io/github/willrieske/spellcraft/spell/rules/VexCapTest.java src/test/java/io/github/willrieske/spellcraft/spell/rules/VexCapPropertiesTest.java src/test/java/io/github/willrieske/spellcraft/spell/rules/GuardAreaTest.java src/test/java/io/github/willrieske/spellcraft/spell/rules/GuardAreaPropertiesTest.java
```

```bash
git commit -m "feat(rules): add the pure VexCap and GuardArea rules for Bound Vexes"
```

Then `git log -1 --oneline` shows the commit.

> **As built (`78efc58`):** diverged — `reachIsASphere` adds `@Scale(3)` to its yaw and pitch parameters; reason: jqwik's default scale of 2 rejects the three-decimal `@DoubleRange` bounds (6.283, 1.57), so the test as written could not run; intent unchanged (implementer, accepted by the task review).

---

### Task 3: The Bound Vex entity, summoned on V with the cap of twelve, and the stay toggle

**Files:**

- Create: `src/main/java/io/github/willrieske/spellcraft/evoking/ModEntities.java`
- Create: `src/main/java/io/github/willrieske/spellcraft/evoking/BoundVex.java`
- Create: `src/main/java/io/github/willrieske/spellcraft/client/evoking/BoundVexRenderers.java`
- Modify: `src/main/java/io/github/willrieske/spellcraft/Spellcraft.java`
- Modify: `src/main/java/io/github/willrieske/spellcraft/evoking/VexSummons.java`
- Modify: `src/main/java/io/github/willrieske/spellcraft/evoking/EvokingCaster.java`
- Modify: `src/main/java/io/github/willrieske/spellcraft/spell/rules/VexRing.java`
- Modify: `src/main/resources/assets/spellcraft/lang/en_us.json`
- Modify: `src/main/java/io/github/willrieske/spellcraft/gametest/SpellcraftGameTests.java`
- Modify: `src/main/java/io/github/willrieske/spellcraft/gametest/VexGameTests.java`
- Modify: `src/main/java/io/github/willrieske/spellcraft/gametest/AlliesGameTests.java`
- Create: `src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexGameTests.java`
- Test: `src/test/java/io/github/willrieske/spellcraft/spell/rules/VexRingTest.java`

What changes in each modified file is in the steps below. **This task's commit is behaviorally regressed on purpose:** V summons Bound Vexes that charge only a target someone else gave them and neither follow nor pick targets until Tasks 4 and 5 add those goals. Do not "fix" that here, and push nothing until Task 7.

**Interfaces:**

- Consumes: `VexCap.toSummon`, `GuardArea.REACH/HALF_WIDTH/HALF_HEIGHT/contains/withinReach` (Task 2); `AllyAttachments.SUMMONER`, `Allies.isAllyOf` (Part 1); `VexRing.offset/COUNT/SUMMON_COUNT`; api-notes §21.
- Produces: `ModEntities.BOUND_VEX` (`DeferredHolder<EntityType<?>, EntityType<BoundVex>>`); `BoundVex` with `isStaying()`, `setStaying(boolean)`, `summonerId()` → `Optional<UUID>`, `isSummonedBy(UUID)`, `summoner()` → `Optional<ServerPlayer>` (online, alive, in this level), static `isPrey(ServerLevel, UUID, LivingEntity)`, `isPrey(LivingEntity)` (false without a `summoner()`), public `inGuardArea(Entity)` (the closed 8×8×7 box, through `GuardArea.contains`), package-private `scanDue(int interval)` (the goal clock: `(tickCount + getId()) % interval == 0`), `keepsTarget(@Nullable LivingEntity)` (alive, `canAttack`, prey, within reach), `withinReach(Entity)`, `guardArea()` → `AABB` (the broad-phase box for entity queries only; filter its hits through `inGuardArea`), static `countOwnedBy(MinecraftServer, UUID)` → int, and `registerGoals()` where Tasks 4–6 add lines; `VexSummons.summon(ServerLevel, ServerPlayer)` and `summon(ServerLevel, ServerPlayer, int count)` → `List<BoundVex>`; `EvokingCaster.Request.CAPPED`, `EvokingCaster.TOO_MANY_KEY`; GameTest helpers `BoundVexGameTests.boundVex(helper, relative, summoner)`, `parked(helper, relative, summoner)`, `husk(helper, relative)`, constants `VEX_SPOT`, `PREY_SPOT`, `NORTH`.

- [x] **Step 1: The entity, its registration and its renderer**

`src/main/java/io/github/willrieske/spellcraft/evoking/ModEntities.java`:

```java
package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's entity types. {@code Spellcraft} registers {@link #ENTITIES} on the mod bus. */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class ModEntities {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Spellcraft.MODID);

    /**
     * The Bound Vex (Evoking II spec §3.1): vanilla's vex build (docs/api-notes.md §21), so it is fire immune, 0.4×0.8,
     * tracked eight chunks out and not allowed on Peaceful, where {@code create} refuses and {@code Mob.checkDespawn}
     * discards it. No spawn egg, no natural spawn, no loot table; {@code /summon spellcraft:bound_vex} makes one with no
     * summoner, which idles.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<BoundVex>> BOUND_VEX = ENTITIES.registerEntityType(
            "bound_vex",
            BoundVex::new,
            MobCategory.MONSTER,
            builder -> builder.fireImmune()
                    .sized(0.4F, 0.8F)
                    .eyeHeight(0.51875F)
                    .passengerAttachments(0.7375F)
                    .ridingOffset(0.04F)
                    .clientTrackingRange(8)
                    .notInPeaceful());

    private ModEntities() {}

    /**
     * Gives the Bound Vex its attributes (a mod-bus event, received here as {@code SpellcraftGameTests} receives
     * {@code RegisterGameTestsEvent}).
     *
     * @param event the attribute event
     */
    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(BOUND_VEX.get(), BoundVex.createAttributes().build());
    }
}
```

`src/main/java/io/github/willrieske/spellcraft/evoking/BoundVex.java` (Tasks 4–6 add goals in `registerGoals` and the `setTarget` override; everything else is final here):

```java
package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.spell.Allies;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import io.github.willrieske.spellcraft.spell.rules.GuardArea;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Bound Vex (Evoking II spec §3): a vex a player summons on V that lives until it dies. Vanilla's {@code Vex} gives it
 * the no-clip flight, the move control, the charging texture and the iron sword; this class owns the goals (vanilla's
 * are private classes, docs/api-notes.md §21), the stay flag, the summoner lookup, the prey rule, and the three small
 * rules every goal shares: {@link #scanDue} (the clock), {@link #inGuardArea} (the box) and {@link #keepsTarget}.
 * Without a summoner online, alive and in its level it idles. The summoner is the {@link AllyAttachments#SUMMONER}
 * mark, so {@code Allies}, {@code GolemMercy} and the Trailing flame already treat it as the summoner's.
 */
public class BoundVex extends Vex {
    /** Synced: whether the vex holds its post instead of following (spec §3.3). */
    private static final EntityDataAccessor<Boolean> STAYING =
            SynchedEntityData.defineId(BoundVex.class, EntityDataSerializers.BOOLEAN);

    private static final String STAYING_TAG = "staying";

    public BoundVex(EntityType<? extends BoundVex> type, Level level) {
        super(type, level);
    }

    /**
     * Vanilla's vex attributes with the reach as the follow range (spec §3.1).
     *
     * @return the attribute builder {@code ModEntities} registers
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Vex.createAttributes().add(Attributes.FOLLOW_RANGE, GuardArea.REACH);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(STAYING, false);
    }

    /**
     * The goals (spec §3.3). Not {@code super.registerGoals()}: vanilla's are private classes (docs/api-notes.md §21),
     * so the charge and wander goals are this class's copies, and a Bound Vex has no raider grudge, no owner to copy a
     * target from and no player hunt. Tasks 4–6 add the follow, target, flee, dodge and repair goals here.
     */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(3, new ChargeGoal());
        goalSelector.addGoal(5, new StayWanderGoal());
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean(STAYING_TAG, isStaying());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(STAYING, input.getBooleanOr(STAYING_TAG, false));
    }

    /** Never removed for being far from players (spec §3.1); Peaceful still removes it through the type. */
    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    /**
     * Vanilla's spawn (the iron sword), then the random follow-range bonus {@code Mob.finalizeSpawn} adds (the inherited
     * {@code RANDOM_SPAWN_BONUS_ID}, docs/api-notes.md §21) goes, so the reach is exactly {@link GuardArea#REACH}
     * (docs/api-notes.md §19: the targeting goal reads the attribute).
     */
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            EntitySpawnReason reason,
            @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
        AttributeInstance range = getAttribute(Attributes.FOLLOW_RANGE);
        if (range != null) {
            range.removeModifier(RANDOM_SPAWN_BONUS_ID);
        }
        return data;
    }

    /** Whether the vex holds its post instead of following. */
    public boolean isStaying() {
        return entityData.get(STAYING);
    }

    /**
     * Turns staying on or off; on, the block it stands at becomes the post the wander goal drifts around (spec §3.3).
     *
     * @param staying true to stay, false to follow again
     */
    public void setStaying(boolean staying) {
        entityData.set(STAYING, staying);
        if (staying) {
            setBoundOrigin(blockPosition());
        }
    }

    /** The UUID in the summoner mark, or empty for a vex nobody summoned ({@code /summon}). */
    public Optional<UUID> summonerId() {
        return getExistingData(AllyAttachments.SUMMONER);
    }

    /**
     * Whether {@code player} is this vex's summoner.
     *
     * @param player a player's UUID
     * @return true if the summoner mark holds it
     */
    public boolean isSummonedBy(UUID player) {
        return summonerId().filter(player::equals).isPresent();
    }

    /**
     * The summoner while online, alive and in this vex's level (spec §3.1); empty otherwise, and the vex idles.
     *
     * @return the summoner, or empty
     */
    public Optional<ServerPlayer> summoner() {
        if (!(level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        return summonerId()
                .map(level::getPlayerByUUID)
                .filter(player -> player instanceof ServerPlayer && player.isAlive())
                .map(ServerPlayer.class::cast);
    }

    /**
     * Whether a Bound Vex of {@code summoner} may fight {@code target} (spec §3.4; round one's rule): anything
     * attackable that is no ally of the summoner, no player the summoner cannot harm ({@code canHarmPlayer}: the
     * {@code pvp} game rule; teammates are already allies), no mob another player summoned unless the summoner may
     * harm that player (Divergence 15: with {@code pvp} off, two players' vexes leave each other alone; an offline
     * summoner's mobs follow the {@code pvp} rule alone), and nothing while the summoner is not in {@code level}
     * (offline or in another dimension). Public for its GameTests.
     *
     * @param level the level the vex is in
     * @param summoner the UUID in the vex's summoner mark
     * @param target the candidate
     * @return whether the vex may take {@code target} as its target
     */
    public static boolean isPrey(ServerLevel level, UUID summoner, LivingEntity target) {
        if (!target.attackable()) {
            return false;
        }
        Player owner = level.getPlayerByUUID(summoner);
        if (owner == null || Allies.isAllyOf(owner, target)) {
            return false;
        }
        if (target instanceof Player other) {
            return owner.canHarmPlayer(other);
        }
        Optional<UUID> othersSummoner = target.getExistingData(AllyAttachments.SUMMONER);
        if (othersSummoner.isPresent()) {
            Player other = level.getServer().getPlayerList().getPlayer(othersSummoner.get());
            return other != null ? owner.canHarmPlayer(other) : level.isPvpAllowed();
        }
        return true;
    }

    /**
     * {@link #isPrey(ServerLevel, UUID, LivingEntity)} for this vex: false on a client level, for a vex with no mark,
     * and while the summoner is dead, offline or in another level (spec §10: "as offline until respawn"), so a vex
     * without a {@link #summoner()} takes and keeps no target.
     *
     * @param target the candidate
     * @return whether this vex may fight it
     */
    public boolean isPrey(LivingEntity target) {
        return level() instanceof ServerLevel level
                && summoner().filter(owner -> isPrey(level, owner.getUUID(), target)).isPresent();
    }

    /**
     * Whether a target is worth taking or keeping (spec §3.4): alive, attackable by this vex ({@code Mob.canAttack}:
     * false for a creative or spectator player, an invulnerable mob or a ghast, the targets {@code setTarget} would
     * not hold), still prey, and within {@link GuardArea#REACH}. Every target goal's {@code canContinueToUse}, which
     * replaces {@code TargetGoal}'s and never calls {@code super} (docs/api-notes.md §21), and every target goal's
     * candidate filter.
     */
    boolean keepsTarget(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && canAttack(target) && isPrey(target) && withinReach(target);
    }

    /** Whether {@code other} is within {@link GuardArea#REACH} of this vex, feet to feet. */
    boolean withinReach(Entity other) {
        Vec3 offset = other.position().subtract(position());
        return GuardArea.withinReach(offset.x, offset.y, offset.z);
    }

    /**
     * Whether {@code other}'s position is inside this vex's guard box (spec §3.4: 8×8×7, faces included, feet to feet,
     * {@link GuardArea#contains}). The one box test every goal uses: entity queries take {@link #guardArea()} as the
     * broad phase and keep only what this accepts. Public for its GameTest.
     *
     * @param other the entity to test
     * @return true when its feet are in the box
     */
    public boolean inGuardArea(Entity other) {
        Vec3 offset = other.position().subtract(position());
        return GuardArea.contains(offset.x, offset.y, offset.z);
    }

    /**
     * The broad-phase box for entity queries around this vex: {@code getEntitiesOfClass} returns what this box
     * <em>intersects</em>, which is more than {@link #inGuardArea} accepts, so filter every query's hits through it.
     */
    AABB guardArea() {
        return new AABB(position(), position()).inflate(GuardArea.HALF_WIDTH, GuardArea.HALF_HEIGHT, GuardArea.HALF_WIDTH);
    }

    /**
     * The goal clock (spec §3.4 "every 10 ticks"): true once every {@code interval} ticks. {@code Mob.serverAiStep}
     * asks a goal's {@code canUse} only when {@code tickCount + getId()} is even (docs/api-notes.md §21, "The goal
     * clock"), so a plain {@code tickCount % 10} gate never opens for an odd id; keyed the same way, an even interval
     * always lands on a tick where {@code canUse} runs. Every scanning goal reads its cadence here.
     *
     * @param interval ticks between scans; even
     * @return whether this tick is a scan tick
     */
    boolean scanDue(int interval) {
        return (tickCount + getId()) % interval == 0;
    }

    /**
     * The living Bound Vexes {@code summoner} has in every loaded level (spec §3.2); vexes in unloaded chunks are not
     * counted (spec §13).
     *
     * @param server the server
     * @param summoner the summoner's UUID
     * @return the count
     */
    public static int countOwnedBy(MinecraftServer server, UUID summoner) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            count += level.getEntities(ModEntities.BOUND_VEX.get(), vex -> vex.isAlive() && vex.isSummonedBy(summoner))
                    .size();
        }
        return count;
    }

    /**
     * The summoner's empty-handed, un-sneaked main-hand click toggles stay (spec §3.3), with the vex's own sound;
     * everything else passes (the sneaking click is Part 3's take-back). Only the server decides, since the client does
     * not know the summoner (the mark is not synced). The client still answers {@code SUCCESS_SERVER} for every
     * empty-handed, un-sneaked main-hand click: a client {@code PASS} would send {@code Minecraft.startUseItem} on to
     * the off hand and throw a pearl or raise a shield on each toggle (docs/api-notes.md §21, Divergence 9). The swing
     * comes from the server; a stranger's click is answered {@code PASS} there and changes nothing.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty() || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!(level() instanceof ServerLevel)) {
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!isSummonedBy(player.getUUID())) {
            return InteractionResult.PASS;
        }
        setStaying(!isStaying());
        playSound(SoundEvents.VEX_AMBIENT, 1.0F, 1.0F);
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * Vanilla's {@code Vex.VexChargeAttackGoal}, copied because it is private (docs/api-notes.md §21): fly at the
     * target's eyes and hit it; this is how a Bound Vex uses the sword it holds.
     */
    private final class ChargeGoal extends Goal {
        ChargeGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = BoundVex.this.getTarget();
            return target != null
                    && target.isAlive()
                    && !BoundVex.this.getMoveControl().hasWanted()
                    && BoundVex.this.random.nextInt(reducedTickDelay(7)) == 0
                    && BoundVex.this.distanceToSqr(target) > 4.0;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = BoundVex.this.getTarget();
            return BoundVex.this.getMoveControl().hasWanted()
                    && BoundVex.this.isCharging()
                    && target != null
                    && target.isAlive();
        }

        @Override
        public void start() {
            LivingEntity target = BoundVex.this.getTarget();
            if (target != null) {
                Vec3 eye = target.getEyePosition();
                BoundVex.this.getMoveControl().setWantedPosition(eye.x, eye.y, eye.z, 1.0);
            }
            BoundVex.this.setIsCharging(true);
            BoundVex.this.playSound(SoundEvents.VEX_CHARGE, 1.0F, 1.0F);
        }

        @Override
        public void stop() {
            BoundVex.this.setIsCharging(false);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = BoundVex.this.getTarget();
            if (target == null) {
                return;
            }
            if (BoundVex.this.getBoundingBox().intersects(target.getBoundingBox())) {
                BoundVex.this.doHurtTarget(getServerLevel(BoundVex.this.level()), target);
                BoundVex.this.setIsCharging(false);
            } else if (BoundVex.this.distanceToSqr(target) < 9.0) {
                Vec3 eye = target.getEyePosition();
                BoundVex.this.getMoveControl().setWantedPosition(eye.x, eye.y, eye.z, 1.0);
            }
        }
    }

    /**
     * Vanilla's {@code Vex.VexRandomMoveGoal}, copied because it is private, and run only while staying (spec §3.3):
     * drift to a random air block within 7 blocks sideways and 5 up or down of the post.
     */
    private final class StayWanderGoal extends Goal {
        StayWanderGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return BoundVex.this.isStaying()
                    && !BoundVex.this.getMoveControl().hasWanted()
                    && BoundVex.this.random.nextInt(reducedTickDelay(7)) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void tick() {
            BlockPos origin = BoundVex.this.getBoundOrigin();
            if (origin == null) {
                origin = BoundVex.this.blockPosition();
            }
            for (int attempt = 0; attempt < 3; attempt++) {
                BlockPos spot = origin.offset(
                        BoundVex.this.random.nextInt(15) - 7,
                        BoundVex.this.random.nextInt(11) - 5,
                        BoundVex.this.random.nextInt(15) - 7);
                if (BoundVex.this.level().isEmptyBlock(spot)) {
                    BoundVex.this.getMoveControl()
                            .setWantedPosition(spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 0.25);
                    if (BoundVex.this.getTarget() == null) {
                        BoundVex.this.getLookControl()
                                .setLookAt(spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 180.0F, 20.0F);
                    }
                    break;
                }
            }
        }
    }
}
```

Notes for the implementer: `addAdditionalSaveData`/`readAdditionalSaveData` are `protected` in `Vex` (`Vex.java:108-134`), as written. `scanDue`, `keepsTarget`, `withinReach` and `guardArea` are package-private and get their readers in Tasks 5 and 6 (Error Prone's `UnusedMethod` flags only private members); `inGuardArea` is public and read by this task's `guardAreaEdges` test. If Error Prone reports `UnusedVariable` or similar on anything here, fix the cause rather than suppress. `registerGoals` runs inside `Mob`'s constructor, before this class's own constructor body: the inner goals must not read `BoundVex` fields at construction (they do not; they read them in `canUse`/`tick`).

`src/main/java/io/github/willrieske/spellcraft/client/evoking/BoundVexRenderers.java`:

```java
package io.github.willrieske.spellcraft.client.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.evoking.ModEntities;
import net.minecraft.client.renderer.entity.VexRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Draws the Bound Vex with vanilla's vex renderer (Evoking II spec §3.1); Part 3 adds the armor layer. */
@EventBusSubscriber(modid = Spellcraft.MODID, value = Dist.CLIENT)
public final class BoundVexRenderers {
    private BoundVexRenderers() {}

    /**
     * Registers the renderer (a mod-bus event). {@code VexRenderer} renders any {@code Vex}, charging texture included
     * (docs/api-notes.md §21).
     *
     * @param event the renderer registration event
     */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BOUND_VEX.get(), VexRenderer::new);
    }
}
```

In `Spellcraft.java`, add after `ModItems.ITEMS.register(modEventBus);`:

```java
        ModEntities.ENTITIES.register(modEventBus);
```

with the import `io.github.willrieske.spellcraft.evoking.ModEntities`.

In `src/main/resources/assets/spellcraft/lang/en_us.json`, add after the `block.spellcraft.trail_fire` line:

```json
  "entity.spellcraft.bound_vex": "Bound Vex",
```

and after the `message.spellcraft.peaceful_vexes` line:

```json
  "message.spellcraft.too_many_vexes": "You have too many vexes",
```

- [x] **Step 2: Summon Bound Vexes, count them at the press**

Replace `src/main/java/io/github/willrieske/spellcraft/evoking/VexSummons.java` with:

```java
package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.spell.AllyAttachments;
import io.github.willrieske.spellcraft.spell.rules.VexRing;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.scores.PlayerTeam;

/**
 * The vex summon (Evoking spec §6, Evoking II §3.1–§3.2): Bound Vexes beside the summoner, marked with the summoner
 * attachment, with no life limit. How many is the caster's call ({@code VexCap}); the goals are the mob's own.
 */
public final class VexSummons {
    private VexSummons() {}

    /**
     * A whole batch ({@link VexRing#SUMMON_COUNT}) beside {@code summoner}.
     *
     * @param level the level to summon in
     * @param summoner the player who summons them
     * @return the vexes, in spawn order
     */
    public static List<BoundVex> summon(ServerLevel level, ServerPlayer summoner) {
        return summon(level, summoner, VexRing.SUMMON_COUNT);
    }

    /**
     * Summons {@code count} Bound Vexes beside {@code summoner}: random ring cells one block up, iron swords from
     * {@code finalizeSpawn}, the summoner's team if it has one, the summoner mark, a bound origin at the cell.
     *
     * @param level the level to summon in
     * @param summoner the player who summons them
     * @param count how many; {@code EvokingCaster} takes it from {@code VexCap.toSummon} at the press
     * @return the vexes, in spawn order; empty on Peaceful, where the type cannot be created ({@code EvokingCaster.request}
     *     refuses first, so this is only a guard for another caller)
     */
    public static List<BoundVex> summon(ServerLevel level, ServerPlayer summoner, int count) {
        RandomSource random = level.getRandom();
        PlayerTeam team = summoner.getTeam();
        BlockPos feet = summoner.blockPosition();
        List<BoundVex> vexes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            VexRing.Offset offset = VexRing.offset(random.nextInt(VexRing.COUNT));
            BlockPos pos = feet.offset(offset.dx(), 1, offset.dz());
            BoundVex vex = ModEntities.BOUND_VEX.get().create(level, EntitySpawnReason.MOB_SUMMONED);
            if (vex == null) {
                continue;
            }
            vex.snapTo(pos, 0.0F, 0.0F);
            vex.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
            vex.setBoundOrigin(pos);
            vex.setData(AllyAttachments.SUMMONER, summoner.getUUID());
            if (team != null) {
                level.getScoreboard().addPlayerToTeam(vex.getScoreboardName(), team);
            }
            level.addFreshEntityWithPassengers(vex);
            vexes.add(vex);
        }
        return vexes;
    }
}
```

In `EvokingCaster.java`:

- In the `Request` enum, the `PEACEFUL` Javadoc becomes `/** Vexes on Peaceful, where the Bound Vex type cannot be created: only the notice, at once; no cast, no cooldown. */`, and after `PEACEFUL` add:

```java
        /** Vexes at the cap of twelve: only the notice, at once; no cast, no cooldown. */
        CAPPED,
```

- After `PEACEFUL_KEY`, add:

```java
    /** Lang key of the notice for a vex request at the cap. */
    public static final String TOO_MANY_KEY = "message.spellcraft.too_many_vexes";
```

- In `Casting`, add a field after `snapshot`:

```java
        /** For a pending vex cast: how many to summon, decided at the press (spec §3.2). */
        int vexCount;
```

- In `request`, replace the Peaceful `if` with:

```java
        if (spell == EvokerSpell.VEXES) {
            if (!ModEntities.BOUND_VEX.get().canSpawn(level)) {
                player.sendOverlayMessage(Component.translatable(PEACEFUL_KEY));
                return Request.PEACEFUL;
            }
            int toSummon = VexCap.toSummon(BoundVex.countOwnedBy(level.getServer(), player.getUUID()));
            if (toSummon == 0) {
                player.sendOverlayMessage(Component.translatable(TOO_MANY_KEY));
                return Request.CAPPED;
            }
            casting.vexCount = toSummon;
        }
```

- In `perform`, the `VEXES` arm becomes `yield !VexSummons.summon(level, player, casting.vexCount).isEmpty();`.
- Update the class and `request` Javadocs (the request Javadoc gains "at the cap of twelve Bound Vexes, sends the too-many notice at once and starts nothing"); replace the `net.minecraft.world.entity.EntityTypes` import with `io.github.willrieske.spellcraft.spell.rules.VexCap` (same package for `ModEntities`/`BoundVex`, no import).

In `VexRing.java`, remove `LIFE_ROLL_BOUND`, `TICKS_PER_SECOND`, `MIN_LIFE_SECONDS` and `lifeTicks` and the "and how long they live … the evoker's life formula" words of the class Javadoc; in `src/test/java/io/github/willrieske/spellcraft/spell/rules/VexRingTest.java` remove the `LifeTicks` nested class (no other test names `lifeTicks`). After Step 3's `VexGameTests` edits, `grep -rn "lifeTicks\|LIFE_ROLL_BOUND" src/test src/main` must find nothing.

- [x] **Step 3: The GameTests**

In `SpellcraftGameTests.java`'s static block, add `BoundVexGameTests.registerAll();` after `VexGameTests.registerAll();`.

In `VexGameTests.java`:

- Class Javadoc: "Vex GameTests for Strength of Evoking (spec §9, table E8, E9, E12, E16 and the prey rule) as Evoking II Part 2 left them: the vexes are Bound Vexes ({@code BoundVexGameTests} covers their goals). …" (keep the padding and team sentences).
- Remove `e10Targeting`, `e10RangeLimit`, `e11GoalsOnJoin`, `e18ArmorStand`, `targetRecheck`, the `park` helper, their five `registerAll` lines, and the constants `VEX_SPOT`, `PREY_SPOT`, `STAND_SPOT`, `OUTSIDE_VEX_SPOT`, `RANGE_WINDOW`. The imports that become unused (`EntitySpawnReason`, `Vec3`, and any other the removed code alone used) are removed by `spotlessApply` in Step 4 (`removeUnusedImports()`); `spotlessCheck` fails the build only if that run is skipped. `AllyAttachments` stays (the `e12` Javadoc links it); `Pig`, `Wolf`, `ArmorStand`, `Zombie`, `EntityTypes` and `AABB` stay used.
- `e8ThreeBesideTheCaster`: the Javadoc's "three marked vexes" becomes "three marked Bound Vexes with no life limit"; inside the loop delete the comment "Created in this tick, so no life has run down yet: the roll bounds are exact." and replace the `FOLLOW_RANGE`, "one target goal" and `life_ticks` assertions with:

```java
                helper.assertTrue(vex instanceof BoundVex, "a Bound Vex, not a vanilla vex");
                helper.assertValueEqual(vex.getAttributeValue(Attributes.FOLLOW_RANGE), GuardArea.REACH, "range");
                helper.assertTrue(vex.getMainHandItem().is(Items.IRON_SWORD), "the evoker's iron sword");
                helper.assertValueEqual(FangGameTests.savedInt(vex, "life_ticks"), Integer.MIN_VALUE, "no life limit");
```

- `e9JoinTheTeam`: `List<Vex> vexes` becomes `List<BoundVex> vexes`; the loop variable `Vex vex` becomes `BoundVex vex`; `vexes.forEach(Vex::discard)` becomes `vexes.forEach(Entity::discard)`.
- `peacefulNotice`: before the difficulty changes, park a Bound Vex (it cannot be created on Peaceful): right after `discardLeftoverVexes(helper);` add `BoundVex parkedVex = BoundVexGameTests.parked(helper, BoundVexGameTests.VEX_SPOT, player);`. After `server.setDifficulty(Difficulty.PEACEFUL, true);` add

  ```java
          helper.assertFalse(
                  ModEntities.BOUND_VEX.get().canSpawn(helper.getLevel()),
                  "setup: a Bound Vex cannot be created on Peaceful");
          parkedVex.checkDespawn();
          helper.assertTrue(parkedVex.isRemoved(), "a Bound Vex already out is removed on Peaceful");
  ```

  The Javadoc gains: "The Bound Vex type is `notInPeaceful` (Divergence 2), so it cannot be created there and `Mob.checkDespawn` removes one already out (spec §10)." (Method under test adds `{@link net.minecraft.world.entity.Mob#checkDespawn}`.)
- `preyRule`: every `VexSummons.isPrey` becomes `BoundVex.isPrey`; the Javadoc's `{@link VexSummons#isPrey}` becomes `{@link BoundVex#isPrey(ServerLevel, UUID, LivingEntity)}` (add `import net.minecraft.world.entity.LivingEntity;` for the link; `ServerLevel` and `UUID` are already imported).
- Imports: add `BoundVex`, `ModEntities`, `GuardArea`, `Items`, `Entity`, `LivingEntity`. Keep `VexSummons`: `e9` still calls `VexSummons.summon`.

In `AlliesGameTests.java` (the fourth caller of the removed `VexSummons.isPrey`):

- Replace the import `io.github.willrieske.spellcraft.evoking.VexSummons` with `io.github.willrieske.spellcraft.evoking.BoundVex`, and add `import net.minecraft.world.entity.LivingEntity;`.
- In `a6PvpOff`'s Javadoc, `{@link VexSummons#isPrey}` becomes `{@link BoundVex#isPrey(ServerLevel, UUID, LivingEntity)}`, and the first sentence gains: "nor a Bound Vex that stranger summoned (Divergence 15), nor a mob summoned by an offline player".
- In `a6PvpOff`, the three `VexSummons.isPrey(` calls become `BoundVex.isPrey(`. After the pig is spawned, add the two summons (the stranger's vex is parked at `SPOT`; the offline player's mark is a random UUID nobody has):

  ```java
          BoundVex strangersVex = BoundVexGameTests.parked(helper, SPOT, stranger);
          Pig offlinesPig = helper.spawn(EntityTypes.PIG, SPOT);
          offlinesPig.setData(AllyAttachments.SUMMONER, UUID.randomUUID());
  ```

  and add `offlinesPig.discard();` inside the existing `whenDone` lambda. After the `setup: with pvp on, a stranger is prey` assertion add

  ```java
          helper.assertTrue(
                  BoundVex.isPrey(level, summoner.getUUID(), strangersVex),
                  "setup: with pvp on, a stranger's Bound Vex is prey");
          helper.assertTrue(
                  BoundVex.isPrey(level, summoner.getUUID(), offlinesPig),
                  "setup: with pvp on, an offline player's summon is prey");
  ```

  and after the `with pvp off, a stranger player is not prey` assertion add

  ```java
          helper.assertFalse(
                  BoundVex.isPrey(level, summoner.getUUID(), strangersVex),
                  "with pvp off, a stranger's Bound Vex is not prey");
          helper.assertFalse(
                  BoundVex.isPrey(level, summoner.getUUID(), offlinesPig),
                  "with pvp off, an offline player's summon is not prey");
  ```

  `AllyAttachments`, `UUID`, `Pig` and `EntityTypes` are already imported there.

Create `src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexGameTests.java`:

```java
package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.EvokingCaster;
import io.github.willrieske.spellcraft.evoking.EvokingCaster.Request;
import io.github.willrieske.spellcraft.evoking.ModEntities;
import io.github.willrieske.spellcraft.evoking.VexSummons;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import io.github.willrieske.spellcraft.spell.rules.EvokerSpell;
import io.github.willrieske.spellcraft.spell.rules.VexCap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Bound Vex GameTests (Evoking II spec §3, §11 "Part 2"). Tests whose vex flies or teleports, that need headroom, or
 * that park many vexes register through {@code FangGameTests.addPadded} (padding 13); the rest run inside the shared
 * 3×3×3 box, where the barrier shell blocks line of sight, so a targeting test keeps the vex and its prey both inside
 * the box. Hostiles are husks (they do not burn in daylight).
 */
public final class BoundVexGameTests {
    /** The yaw a caster faces; {@code FangGameTests.caster} takes it. */
    static final float NORTH = 180.0F;

    private static final int WARMUP = EvokerSpell.WARMUP_TICKS;

    /** The box's top north-west corner: where a test parks the vex. */
    static final BlockPos VEX_SPOT = new BlockPos(0, 2, 0);

    /** The box's opposite bottom corner: farther from {@link #VEX_SPOT} than the summoner standing at the box's middle. */
    static final BlockPos PREY_SPOT = new BlockPos(2, 1, 2);

    private BoundVexGameTests() {}

    static void registerAll() {
        FangGameTests.addPadded(SHARED, "bound_vex_cap_refuses_at_twelve", BoundVexGameTests::capRefusesAtTwelve);
        FangGameTests.addPadded(
                SHARED, "bound_vex_cap_tops_up_from_ten", helper -> capTopsUpToTwelve(helper, VexCap.CAP - 2));
        FangGameTests.addPadded(
                SHARED, "bound_vex_cap_tops_up_from_eleven", helper -> capTopsUpToTwelve(helper, VexCap.CAP - 1));
        FangGameTests.addPadded(SHARED, "bound_vex_cap_is_per_summoner", BoundVexGameTests::capIsPerSummoner);
        FangGameTests.addPadded(SHARED, "bound_vex_cap_counts_every_level", BoundVexGameTests::capCountsEveryLevel);
        SpellcraftGameTests.add(SHARED, "bound_vex_stay_toggle", BoundVexGameTests::stayToggle);
        SpellcraftGameTests.add(SHARED, "bound_vex_idles_without_a_summoner", BoundVexGameTests::idlesWithoutASummoner);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_idles_with_a_dead_summoner", BoundVexGameTests::idlesWithADeadSummoner);
        FangGameTests.addPadded(
                SHARED, "bound_vex_idles_with_the_summoner_elsewhere", BoundVexGameTests::idlesWithTheSummonerElsewhere);
        SpellcraftGameTests.add(SHARED, "bound_vex_save_keeps_stay_and_mark", BoundVexGameTests::saveKeepsStayAndMark);
        FangGameTests.addPadded(SHARED, "bound_vex_guard_area_edges", BoundVexGameTests::guardAreaEdges);
    }

    /**
     * A Bound Vex of {@code summoner} at {@code relative} with its full AI and the iron sword ({@code finalizeSpawn}
     * runs because a spawn reason is given), discarded when the test ends. Not through {@code VexSummons.summon}: the
     * test chooses where it hovers.
     *
     * @param helper the test helper
     * @param relative where to spawn it, relative to the structure
     * @param summoner the player it counts as summoned by
     * @return the vex
     */
    static BoundVex boundVex(GameTestHelper helper, BlockPos relative, ServerPlayer summoner) {
        BoundVex vex = helper.spawn(ModEntities.BOUND_VEX.get(), relative, EntitySpawnReason.MOB_SUMMONED);
        vex.setData(AllyAttachments.SUMMONER, summoner.getUUID());
        vex.setBoundOrigin(helper.absolutePos(relative));
        // A fresh vex has gravity until its first tick sets noGravity, and the drift would sink it into the floor.
        vex.setNoGravity(true);
        vex.setDeltaMovement(Vec3.ZERO);
        whenDone(helper, vex::discard);
        return vex;
    }

    /**
     * {@link #boundVex} with its movement goals removed, so it hovers where it was put while its target goals still
     * run. Parking fixes every distance in a test.
     *
     * @param helper the test helper
     * @param relative where to park it
     * @param summoner the player it counts as summoned by
     * @return the parked vex
     */
    static BoundVex parked(GameTestHelper helper, BlockPos relative, ServerPlayer summoner) {
        BoundVex vex = boundVex(helper, relative, summoner);
        vex.goalSelector.removeAllGoals(goal -> true);
        return vex;
    }

    /**
     * A husk (a hostile that does not burn in daylight) with no AI, discarded when the test ends.
     *
     * @param helper the test helper
     * @param relative where to spawn it
     * @return the husk
     */
    static Husk husk(GameTestHelper helper, BlockPos relative) {
        Husk husk = helper.spawn(EntityTypes.HUSK, relative);
        husk.setNoAi(true);
        whenDone(helper, husk::discard);
        return husk;
    }

    /** A survival player with Evoking inside the shared box (no padding wait). */
    private static ServerPlayer summoner(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.addEffect(EvokingGameTests.evoking());
        MockPlayers.moveIntoTest(helper, player);
        return player;
    }

    /**
     * When the summoner already has twelve Bound Vexes alive, should answer CAPPED with the too-many notice, start no
     * cast and spend no cooldown: with the vexes gone, the same press starts. Padded: twelve vexes in the shared box
     * would sit near the edge of a neighbor's vex search.
     *
     * <p>Method under test: {@link EvokingCaster#request}, through {@link BoundVex#countOwnedBy} and
     * {@link VexCap#toSummon}
     */
    static void capRefusesAtTwelve(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        VexGameTests.discardLeftoverVexes(helper);
        MinecraftServer server = helper.getLevel().getServer();
        List<BoundVex> vexes = new ArrayList<>();
        for (int i = 0; i < VexCap.CAP; i++) {
            vexes.add(parked(helper, VEX_SPOT, player));
        }
        GameTestSupport.drainActionBar(player);

        // Act
        Request request = EvokingCaster.request(player, EvokerSpell.VEXES);

        // Assert
        helper.assertValueEqual(request, Request.CAPPED, "request at the cap");
        GameTestSupport.assertActionBarKey(helper, player, EvokingCaster.TOO_MANY_KEY);
        helper.assertFalse(player.hasEffect(ModEffects.CASTING_VEXES), "no casting effect");
        helper.assertValueEqual(BoundVex.countOwnedBy(server, player.getUUID()), VexCap.CAP, "twelve counted");

        // Act: the vexes go, and the same press starts, so no cooldown was spent
        vexes.forEach(Entity::discard);
        helper.assertValueEqual(BoundVex.countOwnedBy(server, player.getUUID()), 0, "none counted once gone");
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.VEXES), Request.STARTED, "no cooldown spent");
        helper.succeed();
    }

    /**
     * When the summoner has ten (or eleven) Bound Vexes alive, should start the cast and summon two (or one), for
     * twelve in all: the remainder, not a whole batch.
     *
     * <p>Method under test: {@link EvokingCaster#request} then {@link EvokingCaster#tick}, through
     * {@code VexSummons.summon(level, player, count)}
     */
    static void capTopsUpToTwelve(GameTestHelper helper, int alive) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        VexGameTests.discardLeftoverVexes(helper);
        for (int i = 0; i < alive; i++) {
            parked(helper, VEX_SPOT, player);
        }

        // Act
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.VEXES), Request.STARTED, "request with room");

        // Assert
        helper.runAfterDelay(WARMUP, () -> {
            FangGameTests.step(player);
            helper.assertValueEqual(
                    BoundVex.countOwnedBy(helper.getLevel().getServer(), player.getUUID()),
                    VexCap.CAP,
                    "topped up from " + alive + " to twelve");
            helper.succeed();
        });
    }

    /**
     * When another player has twelve Bound Vexes alive and an unmarked Bound Vex ({@code /summon}) is near, should count
     * none of them for this summoner, whose press starts.
     *
     * <p>Method under test: {@link BoundVex#countOwnedBy}, through {@link EvokingCaster#request}
     */
    static void capIsPerSummoner(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        VexGameTests.discardLeftoverVexes(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer stranger = summoner(helper);
        for (int i = 0; i < VexCap.CAP; i++) {
            parked(helper, VEX_SPOT, stranger);
        }
        BoundVex unmarked = helper.spawn(ModEntities.BOUND_VEX.get(), VEX_SPOT);
        unmarked.goalSelector.removeAllGoals(goal -> true);
        whenDone(helper, unmarked::discard);
        helper.assertValueEqual(BoundVex.countOwnedBy(server, stranger.getUUID()), VexCap.CAP, "setup: stranger capped");

        // Act
        Request request = EvokingCaster.request(player, EvokerSpell.VEXES);

        // Assert
        helper.assertValueEqual(BoundVex.countOwnedBy(server, player.getUUID()), 0, "nobody else's vexes counted");
        helper.assertValueEqual(request, Request.STARTED, "this summoner's press starts");
        helper.succeed();
    }

    /**
     * When the summoner has one Bound Vex in the Nether and eleven here, should count twelve and answer CAPPED: the
     * count spans every loaded level (spec §3.2). A count of this level alone would see eleven and start.
     *
     * <p>Method under test: {@link BoundVex#countOwnedBy}, through {@link EvokingCaster#request}
     */
    static void capCountsEveryLevel(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        VexGameTests.discardLeftoverVexes(helper);
        FangGameTests.toNether(helper, player);
        List<BoundVex> inNether = VexSummons.summon(player.level(), player, 1);
        inNether.forEach(vex -> whenDone(helper, vex::discard));
        helper.assertValueEqual(inNether.size(), 1, "setup: one Bound Vex in the Nether");
        MockPlayers.moveIntoTest(helper, player);
        helper.assertValueEqual(player.level().dimension(), helper.getLevel().dimension(), "setup: summoner back");
        for (int i = 0; i < VexCap.CAP - 1; i++) {
            parked(helper, VEX_SPOT, player);
        }

        // Act
        Request request = EvokingCaster.request(player, EvokerSpell.VEXES);

        // Assert
        helper.assertValueEqual(
                BoundVex.countOwnedBy(helper.getLevel().getServer(), player.getUUID()), VexCap.CAP, "both levels");
        helper.assertValueEqual(request, Request.CAPPED, "capped by the Nether vex");
        helper.succeed();
    }

    /**
     * When the summoner clicks a Bound Vex with an empty main hand, should toggle stay on (the post is the block it
     * stands at, whatever origin it had before) and off again, each handled by the server, the post kept when stay goes
     * off; a stranger's click, a sneaking click and a click with an item in hand should pass and change nothing.
     *
     * <p>Method under test: {@code BoundVex.mobInteract} (protected), through {@code Mob.interact}
     */
    static void stayToggle(GameTestHelper helper) {
        // Arrange
        ServerPlayer summoner = summoner(helper);
        ServerPlayer stranger = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, summoner);
        BlockPos post = helper.absolutePos(VEX_SPOT);
        vex.setBoundOrigin(post.above(3)); // another origin, so only the click can make it the post

        // Act & Assert
        helper.assertFalse(
                vex.interact(stranger, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction(), "a stranger's click passes");
        helper.assertFalse(vex.isStaying(), "still following after a stranger's click");
        summoner.setShiftKeyDown(true);
        helper.assertFalse(
                vex.interact(summoner, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction(), "a sneaking click passes");
        summoner.setShiftKeyDown(false);
        GameTestSupport.hold(summoner, new ItemStack(Items.STICK));
        helper.assertFalse(
                vex.interact(summoner, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction(), "a click with an item passes");
        GameTestSupport.hold(summoner, ItemStack.EMPTY);
        helper.assertFalse(vex.isStaying(), "setup: following before the real click");
        helper.assertValueEqual(
                vex.interact(summoner, InteractionHand.MAIN_HAND, Vec3.ZERO),
                InteractionResult.SUCCESS_SERVER,
                "the summoner's empty click is handled by the server");
        helper.assertTrue(vex.isStaying(), "staying");
        helper.assertValueEqual(vex.getBoundOrigin(), post, "the post is where it stood");
        helper.assertValueEqual(
                vex.interact(summoner, InteractionHand.MAIN_HAND, Vec3.ZERO),
                InteractionResult.SUCCESS_SERVER,
                "a second click is handled too");
        helper.assertFalse(vex.isStaying(), "following again");
        helper.assertValueEqual(vex.getBoundOrigin(), post, "switching stay off leaves the post");
        helper.succeed();
    }

    /**
     * When a Bound Vex has no summoner mark at all (as {@code /summon} makes one), should idle: no summoner, no prey, a
     * click passes, and it is never removed for being far away.
     *
     * <p>Method under test: {@link BoundVex#summoner}, {@link BoundVex#isPrey(net.minecraft.world.entity.LivingEntity)},
     * {@link BoundVex#removeWhenFarAway}, {@link BoundVex#requiresCustomPersistence}
     */
    static void idlesWithoutASummoner(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = helper.spawn(ModEntities.BOUND_VEX.get(), VEX_SPOT);
        vex.goalSelector.removeAllGoals(goal -> true);
        whenDone(helper, vex::discard);

        // Act & Assert
        helper.assertTrue(vex.summoner().isEmpty(), "no summoner");
        helper.assertFalse(vex.isPrey(player), "nothing is prey without a summoner");
        helper.assertFalse(vex.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction(), "a click passes");
        helper.assertFalse(vex.removeWhenFarAway(1.0E6), "never removed for distance");
        helper.assertTrue(vex.requiresCustomPersistence(), "persists on its own");
        helper.succeed();
    }

    /**
     * When a staying Bound Vex is saved and loaded the way a chunk save does, should still be staying and still carry
     * its summoner mark, and should save no life limit.
     *
     * <p>Method under test: {@code BoundVex.addAdditionalSaveData}/{@code readAdditionalSaveData} (protected), through
     * {@link GameTestSupport#reloaded}
     */
    static void saveKeepsStayAndMark(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.setStaying(true);

        // Act
        if (!(GameTestSupport.reloaded(helper.getLevel(), vex) instanceof BoundVex copy)) {
            throw helper.assertionException("the reloaded copy is not a Bound Vex");
        }

        // Assert
        helper.assertTrue(copy.isStaying(), "staying survives the save");
        helper.assertTrue(copy.isSummonedBy(player.getUUID()), "the mark survives the save");
        helper.assertValueEqual(FangGameTests.savedInt(vex, "life_ticks"), Integer.MIN_VALUE, "no life limit saved");
        helper.succeed();
    }

    /**
     * When a marked Bound Vex's summoner dies, should idle as if the summoner were offline (spec §10, "as offline until
     * respawn"): no summoner, and a husk that was prey a moment ago no longer is.
     *
     * <p>Method under test: {@link BoundVex#summoner}, {@link BoundVex#isPrey(net.minecraft.world.entity.LivingEntity)}
     */
    static void idlesWithADeadSummoner(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Husk husk = husk(helper, PREY_SPOT);
        helper.assertTrue(vex.isPrey(husk), "setup: a husk is prey while the summoner lives");

        // Act
        player.kill(helper.getLevel());

        // Assert
        helper.assertFalse(player.isAlive(), "setup: the summoner is dead");
        helper.assertTrue(vex.summoner().isEmpty(), "no summoner while dead");
        helper.assertFalse(vex.isPrey(husk), "nothing is prey while the summoner is dead");
        helper.succeed();
    }

    /**
     * When a marked Bound Vex's summoner goes to another dimension, should idle (spec §3.1: the summoner must be in the
     * vex's level): no summoner, and a husk that was prey a moment ago no longer is.
     *
     * <p>Method under test: {@link BoundVex#summoner}, {@link BoundVex#isPrey(net.minecraft.world.entity.LivingEntity)}
     */
    static void idlesWithTheSummonerElsewhere(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Husk husk = husk(helper, PREY_SPOT);
        helper.assertTrue(vex.isPrey(husk), "setup: a husk is prey while the summoner is here");

        // Act
        FangGameTests.toNether(helper, player);

        // Assert
        helper.assertTrue(vex.summoner().isEmpty(), "no summoner while it is in the Nether");
        helper.assertFalse(vex.isPrey(husk), "nothing is prey while the summoner is elsewhere");
        helper.succeed();
    }

    /**
     * When another entity's feet are exactly on a face of the 8×8×7 guard box, should count it inside; half a block
     * past the face (or past the top), outside. The goals of Tasks 5 and 6 all ask this one method.
     *
     * <p>Method under test: {@link BoundVex#inGuardArea}
     */
    static void guardAreaEdges(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Husk husk = husk(helper, PREY_SPOT);
        Vec3 at = vex.position();

        // Act & Assert
        husk.setPos(at.add(4.0, 0.0, 0.0));
        helper.assertTrue(vex.inGuardArea(husk), "on the east face: inside");
        husk.setPos(at.add(4.5, 0.0, 0.0));
        helper.assertFalse(vex.inGuardArea(husk), "half a block east of it: outside");
        husk.setPos(at.add(0.0, 0.0, -4.0));
        helper.assertTrue(vex.inGuardArea(husk), "on the north face: inside");
        husk.setPos(at.add(0.0, 0.0, -4.5));
        helper.assertFalse(vex.inGuardArea(husk), "half a block north of it: outside");
        husk.setPos(at.add(0.0, 3.5, 0.0));
        helper.assertTrue(vex.inGuardArea(husk), "on the top face: inside");
        husk.setPos(at.add(0.0, 4.0, 0.0));
        helper.assertFalse(vex.inGuardArea(husk), "half a block above it: outside");
        husk.setPos(at.add(4.0, -3.5, 4.0));
        helper.assertTrue(vex.inGuardArea(husk), "a bottom corner: inside");
        helper.succeed();
    }
}
```

`Husk` is `net.minecraft.world.entity.monster.zombie.Husk`. `EvokingGameTests.evoking()` is the effect instance `FangGameTests.caster` uses; it is package-private, so it is reachable here. The padded tests use `FangGameTests.caster` (it loads the padding and stands the player at the box's middle); `FangGameTests.toNether` needs the Evoking effect `caster` gives, and its cleanup discards every vex near the Nether pocket.

- [x] **Step 4: Format, build, GameTests, client load**

Run: `./gradlew spotlessApply --console=plain`, then `./gradlew build --console=plain > build/build.log 2>&1; echo "exit=$?"; tail -40 build/build.log`
Expected: `exit=0`. Unit tests: the `VexRingTest` life group is gone; everything else green.

Run in the background: `./gradlew runGameTestServer --console=plain > build/runGameTestServer.log 2>&1; echo "exit=$?"; grep -n "FAILED\|required tests" build/runGameTestServer.log | tail -5`
Expected: `exit=0`, `All N required tests passed` with N = 164 − 5 removed + 11 new = **170**. Removed: `e10Targeting`, `e10RangeLimit`, `e11GoalsOnJoin`, `e18ArmorStand`, `targetRecheck`. New (the eleven `registerAll` lines above): cap refuses at twelve, tops up from ten, tops up from eleven, per summoner, every level, stay toggle, idles without a summoner, idles with a dead summoner, idles with the summoner elsewhere, save keeps stay and mark, guard-area edges. `peacefulNotice` and `a6PvpOff` gain assertions, not registrations.

Run: `./gradlew runData --console=plain > build/runData.log 2>&1; echo "exit=$?"; tail -20 build/runData.log`
Expected: `exit=0` (the client subscriber `BoundVexRenderers` and `ModEntities` load; `EvokingArmPoseCheck` still passes).

- [x] **Step 5: Commit**

```bash
git add src/main/java/io/github/willrieske/spellcraft/Spellcraft.java src/main/java/io/github/willrieske/spellcraft/evoking/ModEntities.java src/main/java/io/github/willrieske/spellcraft/evoking/BoundVex.java src/main/java/io/github/willrieske/spellcraft/evoking/VexSummons.java src/main/java/io/github/willrieske/spellcraft/evoking/EvokingCaster.java src/main/java/io/github/willrieske/spellcraft/client/evoking/BoundVexRenderers.java src/main/java/io/github/willrieske/spellcraft/spell/rules/VexRing.java src/main/resources/assets/spellcraft/lang/en_us.json src/main/java/io/github/willrieske/spellcraft/gametest/SpellcraftGameTests.java src/main/java/io/github/willrieske/spellcraft/gametest/VexGameTests.java src/main/java/io/github/willrieske/spellcraft/gametest/AlliesGameTests.java src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexGameTests.java src/test/java/io/github/willrieske/spellcraft/spell/rules/VexRingTest.java
```

```bash
git commit -m "feat(evoking): add the Bound Vex, summon it on V with a cap of twelve, and the stay toggle"
```

Then `git log -1 --oneline` shows the commit.

> **As built (`66c82f6`):** diverged — `BoundVex` also imports `net.minecraft.world.level.Level` (the plan's constructor used it unimported); `capCountsEveryLevel` polls with `helper.succeedWhen` (count first, then `CAPPED`) instead of asserting at once, because a vex summoned in the tick that forces its Nether chunk is not in `ServerLevel.getEntities` until the ticket takes effect; production code as planned; reason: implementer, accepted by the task review.

---

### Task 4: Following, teleporting and portals

**Files:**

- Create: `src/main/java/io/github/willrieske/spellcraft/evoking/FollowSummonerGoal.java`
- Create: `src/main/java/io/github/willrieske/spellcraft/evoking/BoundVexEvents.java`
- Modify: `src/main/java/io/github/willrieske/spellcraft/evoking/BoundVex.java` (`registerGoals`: one line)
- Modify: `src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexGameTests.java` (seven tests, `FEET`, `FAR_NORTH`)

**Interfaces:**

- Consumes: `BoundVex.isStaying/setStaying/summoner/isSummonedBy/getMoveControl` (Task 3); `BoundVexGameTests.boundVex/parked/NORTH` (Task 3); `VexRing.offset/COUNT`; api-notes §21 (teleports, the two dimension events).
- Produces: `FollowSummonerGoal` (public class so the GameTests read its numbers; package-private constructor; public constants `ORBIT_MIN` 2.0, `ORBIT_MAX` 4.0, `TELEPORT_DISTANCE` 12.0, `TELEPORT_SPREAD` 2, `TELEPORT_LANDING_MAX` 4.5); `BoundVexEvents` (public, `@EventBusSubscriber`; public `PORTAL_RANGE` 16.0; Task 6 adds a line to its `onServerStopped`); `BoundVexGameTests.FEET`, `FAR_NORTH` (13), read by Tasks 5 and 6.

- [x] **Step 1: The follow goal**

`src/main/java/io/github/willrieske/spellcraft/evoking/FollowSummonerGoal.java`:

```java
package io.github.willrieske.spellcraft.evoking;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps a Bound Vex at its summoner's side (Evoking II spec §3.3): while following (not staying) and idle (no target),
 * a vex farther than the orbit band flies to a random spot inside it, and a vex farther than
 * {@link #TELEPORT_DISTANCE} blocks teleports to an air cell beside the summoner instead. Each use makes one move; the
 * move control flies it there and {@code canUse} picks the next. Without a summoner in the level the goal is idle.
 * Like vanilla's random move, the goal waits for the move control to be free ({@code !hasWanted()}), so a vex finishes
 * a wander leg or a charge before it follows.
 */
public final class FollowSummonerGoal extends Goal {
    /** The orbit band's inner edge (feet to feet): the vex is left alone inside the band. */
    public static final double ORBIT_MIN = 2.0;

    /** The orbit band's outer edge (feet to feet): beyond it the vex moves. */
    public static final double ORBIT_MAX = 4.0;

    /** Farther than this (feet to feet), the vex teleports instead of flying. */
    public static final double TELEPORT_DISTANCE = 12.0;

    /** The teleport lands within this many blocks of the summoner's feet sideways, and 0–2 blocks up. */
    public static final int TELEPORT_SPREAD = 2;

    /**
     * The farthest a teleported vex lands from the summoner's feet: a cell 2 blocks off on both horizontal axes and 2
     * up, entered at its center, is √(2² + 2.5² + 2²) ≈ 3.8 blocks away; 4.5 leaves room for a few ticks of hover.
     */
    public static final double TELEPORT_LANDING_MAX = 4.5;

    /**
     * The orbit spot's farthest horizontal distance; with the spot 0.5–1.5 blocks above the summoner's feet, every spot
     * is at most √(3.5² + 1.5²) ≈ 3.8 blocks from the feet, inside {@link #ORBIT_MAX}, so one hop lands in the band.
     */
    private static final double ORBIT_SPOT_RADIUS = 3.5;

    private static final int TELEPORT_TRIES = 10;

    /** Below vanilla's charge speed (1.0), so the vex coasts about a block past its spot instead of darting through. */
    private static final double SPEED = 0.7;

    private final BoundVex vex;

    FollowSummonerGoal(BoundVex vex) {
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (vex.isStaying() || vex.getTarget() != null || vex.getMoveControl().hasWanted()) {
            return false;
        }
        return vex.summoner().filter(owner -> vex.distanceTo(owner) > ORBIT_MAX).isPresent();
    }

    /** One move per use. */
    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        vex.summoner().ifPresent(owner -> {
            if (vex.distanceTo(owner) > TELEPORT_DISTANCE) {
                teleportBeside(owner);
            } else {
                flyToOrbit(owner);
            }
        });
    }

    /**
     * A random spot 2–3.5 blocks sideways from the summoner's feet and 0.5–1.5 blocks up: built from the same point
     * {@code canUse} measures from, so the spot is always inside the band.
     */
    private void flyToOrbit(ServerPlayer owner) {
        RandomSource random = vex.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double radius = ORBIT_MIN + random.nextDouble() * (ORBIT_SPOT_RADIUS - ORBIT_MIN);
        Vec3 spot = owner.position().add(Math.cos(angle) * radius, 0.5 + random.nextDouble(), Math.sin(angle) * radius);
        vex.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, SPEED);
    }

    /**
     * An air cell within {@link #TELEPORT_SPREAD} of the summoner's feet, 0–2 blocks up; after ten misses the cell
     * above the summoner's own (vexes clip through blocks, so any cell works). The velocity and the wanted position
     * are cleared so the vex hovers where it lands.
     */
    private void teleportBeside(ServerPlayer owner) {
        RandomSource random = vex.getRandom();
        BlockPos feet = owner.blockPosition();
        BlockPos cell = feet.above();
        for (int attempt = 0; attempt < TELEPORT_TRIES; attempt++) {
            BlockPos candidate = feet.offset(
                    random.nextInt(2 * TELEPORT_SPREAD + 1) - TELEPORT_SPREAD,
                    random.nextInt(3),
                    random.nextInt(2 * TELEPORT_SPREAD + 1) - TELEPORT_SPREAD);
            if (vex.level().isEmptyBlock(candidate)) {
                cell = candidate;
                break;
            }
        }
        vex.teleportTo(cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5);
        vex.setDeltaMovement(Vec3.ZERO);
        vex.getMoveControl().setWait();
    }
}
```

In `BoundVex.registerGoals`, add after the `ChargeGoal` line (source order stays priority order: charge 3, follow 4, stay-wander 5):

```java
        goalSelector.addGoal(4, new FollowSummonerGoal(this));
```

- [x] **Step 2: Portals**

`src/main/java/io/github/willrieske/spellcraft/evoking/BoundVexEvents.java`:

```java
package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.spell.rules.VexRing;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Bound Vexes follow their summoner through a dimension change (Evoking II spec §3.3): the living, non-staying ones
 * within {@link #PORTAL_RANGE} blocks of the summoner when the move starts land on ring cells around the summoner when
 * it ends. Two events, because the arrival event no longer knows where the player left (docs/api-notes.md §21): the
 * followers are noted on {@code EntityTravelToDimensionEvent} and moved on {@code PlayerChangedDimensionEvent}. Staying
 * vexes stay; a moved vex is a new entity instance that keeps its mark (an attachment) and its stay flag (saved as
 * NBT). Each follower draws its ring cell on its own, so two may share one (harmless for vexes, which clip).
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class BoundVexEvents {
    /** How far from the leaving summoner a following vex comes along. */
    public static final double PORTAL_RANGE = 16.0;

    /**
     * Per player changing dimension: the vexes to bring. Transient, like {@code EvokingCaster}'s map: an entry lives
     * from the travel event to the changed-dimension event of the same {@code ServerPlayer.teleport} call, and logout
     * and server stop drop it.
     */
    private static final Map<UUID, List<UUID>> FOLLOWERS = new HashMap<>();

    private BoundVexEvents() {}

    /**
     * Notes the followers while the player still stands where they leave. The event is posted before every teleport,
     * same-dimension ones (pearls, chorus fruit, {@code /tp}) included (docs/api-notes.md §21); those, which no
     * changed-dimension event follows, only drop an old note.
     *
     * @param event the travel event
     */
    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        FOLLOWERS.remove(id);
        ServerLevel level = player.level();
        if (event.getDimension().equals(level.dimension())) {
            return;
        }
        List<UUID> followers = level.getEntitiesOfClass(
                        BoundVex.class,
                        player.getBoundingBox().inflate(PORTAL_RANGE),
                        vex -> vex.isAlive()
                                && !vex.isStaying()
                                && vex.isSummonedBy(id)
                                && vex.distanceTo(player) <= PORTAL_RANGE)
                .stream()
                .map(Entity::getUUID)
                .toList();
        if (!followers.isEmpty()) {
            FOLLOWERS.put(id, followers);
        }
    }

    /**
     * Brings the noted followers to ring cells one block up around the arrived player.
     *
     * @param event the changed-dimension event, posted after the player's move
     */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        List<UUID> followers = FOLLOWERS.remove(player.getUUID());
        ServerLevel to = player.level();
        ServerLevel from = to.getServer().getLevel(event.getFrom());
        if (followers == null || from == null) {
            return;
        }
        RandomSource random = to.getRandom();
        BlockPos feet = player.blockPosition();
        for (UUID id : followers) {
            if (from.getEntities().get(id) instanceof BoundVex vex && vex.isAlive()) {
                VexRing.Offset offset = VexRing.offset(random.nextInt(VexRing.COUNT));
                Vec3 spot = Vec3.atCenterOf(feet.offset(offset.dx(), 1, offset.dz()));
                vex.teleport(new TeleportTransition(
                        to, spot, Vec3.ZERO, vex.getYRot(), vex.getXRot(), TeleportTransition.DO_NOTHING));
            }
        }
    }

    /**
     * Drops a logged-out player's note (a cancelled travel event can leave one).
     *
     * @param event the logout event
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FOLLOWERS.remove(event.getEntity().getUUID());
    }

    /**
     * Drops every note when the server stops (single-player loads one server per world in the same JVM).
     *
     * @param event the server-stopped event
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        FOLLOWERS.clear();
    }
}
```

`Entity.teleport`'s return is ignored: no Error Prone check fires on it, and the portal code never touches the old instance after the call (the teleport removes it).

- [x] **Step 3: The GameTests**

In `BoundVexGameTests.registerAll`, add:

```java
        FangGameTests.addPadded(SHARED, "bound_vex_follow_flies_into_the_orbit", BoundVexGameTests::followFliesIntoTheOrbit);
        FangGameTests.addPadded(
                SHARED, "bound_vex_follow_teleports_from_thirteen_blocks", BoundVexGameTests::followTeleportsFromThirteen);
        FangGameTests.addPadded(
                SHARED, "bound_vex_follow_idles_without_a_summoner", BoundVexGameTests::followIdlesWithoutASummoner);
        FangGameTests.addPadded(SHARED, "bound_vex_staying_holds_its_post", BoundVexGameTests::stayingHoldsItsPost);
        FangGameTests.addPadded(SHARED, "bound_vex_follows_again_after_stay", BoundVexGameTests::followsAgainAfterStay);
        FangGameTests.addPadded(SHARED, "bound_vex_portal_follow", BoundVexGameTests::portalFollow);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_same_dimension_teleport_brings_nothing",
                BoundVexGameTests::sameDimensionTeleportBringsNothing);
```

Imports to add: `io.github.willrieske.spellcraft.evoking.BoundVexEvents`, `io.github.willrieske.spellcraft.evoking.FollowSummonerGoal`, `java.util.UUID`, `net.minecraft.server.level.ServerLevel`, `net.minecraft.world.level.Level`, `net.neoforged.neoforge.common.NeoForge`, `net.neoforged.neoforge.event.entity.player.PlayerEvent` (`List`, `Vec3`, `BlockPos` and `ModEntities` are already imported). Then the constants and tests:

```java
    /** The middle of the structure's box, where {@code FangGameTests.caster} stands the player. */
    private static final BlockPos FEET = FangGameTests.FEET;

    /** A vex this far north of the caster is outside the teleport distance (12) and inside the padding (13). */
    static final int FAR_NORTH = 13;

    /**
     * When a following Bound Vex hovers seven blocks from its summoner, should fly into the orbit band: three ticks in
     * it is still more than six blocks out (a teleport would have put it within {@code TELEPORT_LANDING_MAX}), and it
     * then comes within five blocks (the band's edge plus one; a flight toward a spot inside the band always crosses
     * that line).
     *
     * <p>Method under test: {@link FollowSummonerGoal}, through the vex's goal selector
     */
    static void followFliesIntoTheOrbit(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = boundVex(helper, FEET.north(7), player);
        helper.assertTrue(vex.distanceTo(player) > FollowSummonerGoal.ORBIT_MAX + 2.0, "setup: seven blocks out");

        // Act & Assert
        helper.runAfterDelay(3, () -> helper.assertTrue(
                vex.distanceTo(player) > FollowSummonerGoal.ORBIT_MAX + 2.0,
                "still flying after three ticks, not teleported, got " + vex.distanceTo(player)));
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 3, "not before the flight check");
            helper.assertTrue(
                    vex.distanceTo(player) <= FollowSummonerGoal.ORBIT_MAX + 1.0,
                    "in the orbit band, got " + vex.distanceTo(player));
        });
    }

    /**
     * When a following Bound Vex is thirteen blocks from its summoner, should be beside the summoner within five ticks:
     * a flight could not cover that distance so fast, so it teleported.
     *
     * <p>Method under test: {@link FollowSummonerGoal}, through the vex's goal selector
     */
    static void followTeleportsFromThirteen(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = boundVex(helper, FEET.north(FAR_NORTH), player);

        // Act & Assert
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(
                    vex.distanceTo(player) <= FollowSummonerGoal.TELEPORT_LANDING_MAX,
                    "beside the summoner after a teleport, got " + vex.distanceTo(player));
            helper.succeed();
        });
    }

    /**
     * When a Bound Vex with no summoner mark hovers thirteen blocks from a player, should neither fly nor teleport for
     * forty ticks: the follow goal is idle without a summoner (spec §10).
     *
     * <p>Method under test: {@link FollowSummonerGoal}, through the vex's goal selector
     */
    static void followIdlesWithoutASummoner(GameTestHelper helper) {
        // Arrange
        FangGameTests.caster(helper, NORTH);
        BoundVex vex = helper.spawn(ModEntities.BOUND_VEX.get(), FEET.north(FAR_NORTH));
        vex.setNoGravity(true);
        vex.setDeltaMovement(Vec3.ZERO);
        whenDone(helper, vex::discard);
        Vec3 start = vex.position();

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(40, () -> helper.assertTrue(
                        vex.position().distanceTo(start) < 0.01,
                        "an unmarked vex stays put, moved " + vex.position().distanceTo(start)))
                .thenSucceed();
    }

    /**
     * When a staying Bound Vex's post is thirteen blocks from its summoner, should neither fly nor teleport to the
     * summoner over sixty ticks. The post is the structure's middle and the player stands thirteen blocks south, so
     * the wander (±7 blocks around the post) stays inside the padding.
     *
     * <p>Method under test: {@link FollowSummonerGoal} and {@code BoundVex.StayWanderGoal}, through the goal selector
     */
    static void stayingHoldsItsPost(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = boundVex(helper, FEET, player);
        vex.setStaying(true);
        MockPlayers.moveTo(helper, player, Vec3.atBottomCenterOf(FEET.south(FAR_NORTH)));

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(60, () -> helper.assertTrue(
                        vex.distanceTo(player) > FollowSummonerGoal.ORBIT_MAX + 1.0,
                        "a staying vex stays more than five blocks from the summoner, got " + vex.distanceTo(player)))
                .thenSucceed();
    }

    /**
     * When a staying Bound Vex thirteen blocks out is switched back to following after twenty ticks, should come back
     * within five blocks of its summoner (once any wander leg under way is done).
     *
     * <p>Method under test: {@link BoundVex#setStaying} and {@link FollowSummonerGoal}, through the goal selector
     */
    static void followsAgainAfterStay(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = boundVex(helper, FEET.north(FAR_NORTH), player);
        vex.setStaying(true);

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(
                            vex.distanceTo(player) > FollowSummonerGoal.ORBIT_MAX + 1.0, "setup: still away while staying");
                    vex.setStaying(false);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.distanceTo(player) <= FollowSummonerGoal.ORBIT_MAX + 1.0,
                        "back beside the summoner, got " + vex.distanceTo(player)))
                .thenSucceed();
    }

    /**
     * When the summoner moves to the Nether, should bring the one following Bound Vex two blocks away to a ring cell
     * beside the arrival point, still marked, and leave behind the staying one, a following one seventeen blocks away
     * (past {@code PORTAL_RANGE}, which then idles where it is) and another player's vex two blocks away.
     *
     * <p>Method under test: {@link BoundVexEvents#onTravel} and {@link BoundVexEvents#onChangedDimension}
     */
    static void portalFollow(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        ServerPlayer stranger = summoner(helper);
        UUID id = player.getUUID();
        BoundVex following = boundVex(helper, FEET.south(2), player);
        BoundVex staying = boundVex(helper, FEET.north(2), player);
        staying.setStaying(true);
        BoundVex far = boundVex(helper, FEET.offset(-12, 0, -12), player); // about 16.97 blocks from the player
        BoundVex strangers = boundVex(helper, FEET.east(2), stranger);
        Vec3 farStart = far.position();
        ServerLevel overworld = helper.getLevel();
        helper.assertTrue(far.distanceTo(player) > BoundVexEvents.PORTAL_RANGE, "setup: the far vex is out of range");

        // Act
        BlockPos netherFeet = FangGameTests.toNether(helper, player);
        ServerLevel nether = player.level();

        // Assert
        helper.succeedWhen(() -> {
            List<? extends BoundVex> arrived =
                    nether.getEntities(ModEntities.BOUND_VEX.get(), vex -> vex.isSummonedBy(id) && vex.isAlive());
            helper.assertValueEqual(arrived.size(), 1, "one Bound Vex in the Nether");
            helper.assertTrue(
                    arrived.get(0).blockPosition().closerThan(netherFeet, 4.5), "beside the arrival point (a ring cell)");
            helper.assertFalse(arrived.get(0).isStaying(), "the one that came is the following one");
            helper.assertFalse(following.isAlive(), "the old instance is gone (a cross-dimension teleport makes a new one)");
            List<? extends BoundVex> left =
                    overworld.getEntities(ModEntities.BOUND_VEX.get(), vex -> vex.isSummonedBy(id) && vex.isAlive());
            helper.assertValueEqual(left.size(), 2, "the staying and the far vex are left in the Overworld");
            helper.assertTrue(staying.isAlive() && staying.isStaying(), "the staying one stayed");
            helper.assertTrue(far.isAlive(), "the far one stayed");
            helper.assertTrue(
                    far.position().distanceTo(farStart) < 0.01,
                    "the far one idles without its summoner in the level, moved " + far.position().distanceTo(farStart));
            helper.assertTrue(strangers.isAlive(), "another player's vex stayed");
        });
    }

    /**
     * When the summoner teleports within the Overworld (as a pearl or {@code /tp} does) with a following Bound Vex
     * near, and a changed-dimension event is then posted by hand, should move nothing: the travel event took no note
     * for a same-dimension teleport.
     *
     * <p>Method under test: {@link BoundVexEvents#onTravel} and {@link BoundVexEvents#onChangedDimension}
     */
    static void sameDimensionTeleportBringsNothing(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, FEET.north(2), player);
        Vec3 start = vex.position();

        // Act
        MockPlayers.moveTo(helper, player, Vec3.atBottomCenterOf(FEET.south(10)));
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(
                player, Level.NETHER, helper.getLevel().dimension()));

        // Assert
        helper.assertTrue(vex.isAlive(), "the same vex is still here");
        helper.assertTrue(
                vex.position().distanceTo(start) < 0.01,
                "not moved to the summoner, moved " + vex.position().distanceTo(start));
        helper.succeed();
    }
```

`BlockPos.closerThan(Vec3i, double)` is `Vec3i.closerThan` (strict `<`). `FangGameTests.toNether`'s cleanup discards every `Vex` near the pocket, Bound Vexes included, and moves the player back. The hand-posted changed-dimension event in `sameDimensionTeleportBringsNothing` reaches every subscriber; the mod's other handlers (`EvokingCaster`, `TrailLayer`) listen to logout, not to it (`grep -rn PlayerChangedDimensionEvent src/main/java` finds only `BoundVexEvents`).

- [x] **Step 4: Format, build, GameTests**

Run: `./gradlew spotlessApply --console=plain`, then `./gradlew build --console=plain > build/build.log 2>&1; echo "exit=$?"; tail -40 build/build.log`. Expected: `exit=0`.
Run in the background: `./gradlew runGameTestServer --console=plain > build/runGameTestServer.log 2>&1; echo "exit=$?"; grep -n "FAILED\|required tests" build/runGameTestServer.log | tail -5`. Expected: `exit=0`, `All 177 required tests passed` (Task 3's 170 + the seven registered above).

- [x] **Step 5: Commit**

```bash
git add src/main/java/io/github/willrieske/spellcraft/evoking/FollowSummonerGoal.java src/main/java/io/github/willrieske/spellcraft/evoking/BoundVexEvents.java src/main/java/io/github/willrieske/spellcraft/evoking/BoundVex.java src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexGameTests.java
```

```bash
git commit -m "feat(evoking): Bound Vexes follow their summoner, teleport when far and ride portals"
```

Then `git log -1 --oneline` shows the commit.

> **As built (`88373a8`):** diverged — `sameDimensionTeleportBringsNothing` posts `PlayerChangedDimensionEvent` with `from` = `to` = the test level (not `Level.NETHER`) and asserts the vex is a follower first, so it fails when the `onTravel` same-dimension guard is removed (shown red, then restored); the `TELEPORT_LANDING_MAX` Javadoc gives the worst case for a summoner anywhere in its block (≈ 4.33) and the extra hop; production code as planned; reason: task review (Important finding, controller ruling that the test's intent governs).

---

### Task 5: Who a Bound Vex fights, and the wolves that join

**Files:**

- Create: `src/main/java/io/github/willrieske/spellcraft/evoking/HurtBySummonerSideGoal.java`
- Create: `src/main/java/io/github/willrieske/spellcraft/evoking/AllyFightGoal.java`
- Create: `src/main/java/io/github/willrieske/spellcraft/evoking/NearestHostileGoal.java`
- Modify: `src/main/java/io/github/willrieske/spellcraft/evoking/BoundVex.java` (`registerGoals`: three lines; `SCAN_INTERVAL`, `WOLF_CALL_RANGE`, `alliesInGuardArea`, a `setTarget` override)
- Modify: `src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexGameTests.java` (twenty tests and their helpers)

**Interfaces:**

- Consumes: `BoundVex.summoner/isPrey/keepsTarget/inGuardArea/guardArea/scanDue` (Task 3); `BoundVexGameTests.FEET/FAR_NORTH` (Task 4), `boundVex/parked/husk/summoner/VEX_SPOT/PREY_SPOT/NORTH` (Task 3); `Allies.isAllyOf`; api-notes §21 (the goal clock, `TargetGoal`, memories).
- Produces: `HurtBySummonerSideGoal`, `AllyFightGoal`, `NearestHostileGoal` (package-private `TargetGoal`s; `HurtBySummonerSideGoal.MEMORY_TICKS` 100); `BoundVex.SCAN_INTERVAL` 10 (package-private; Task 6's repair goal reads it), `BoundVex.WOLF_CALL_RANGE` 16.0, `BoundVex.alliesInGuardArea(ServerPlayer)` → `List<Mob>` (package-private), and the `setTarget` override.

- [x] **Step 1: The three target goals**

`src/main/java/io/github/willrieske/spellcraft/evoking/HurtBySummonerSideGoal.java`:

```java
package io.github.willrieske.spellcraft.evoking;

import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.jspecify.annotations.Nullable;

/**
 * The grudge goal (Evoking II spec §3.4, first in the target selector): on each scan ({@code BoundVex.scanDue}), the
 * first fresh memory in this order that the vex may take ({@code BoundVex.keepsTarget}): the mob that last hurt this
 * vex, the mob that last hurt the summoner, the mob the summoner last hit, the mob that last hurt an ally inside the
 * guard area. The order is the spec's priority (self-defense first, the ally scan only when nothing nearer matches) and
 * costs one pass instead of comparing four ages (Divergence 6). A memory is fresh for {@link #MEMORY_TICKS} on its
 * owner's own clock ({@code rememberer.tickCount - timestamp}): vanilla forgets {@code lastHurtByMob} after 100 ticks
 * but never forgets {@code lastHurtMob} while it lives (docs/api-notes.md §21), so the window is checked here for all
 * four. A creeper is allowed here: it provoked.
 */
final class HurtBySummonerSideGoal extends TargetGoal {
    /** How old a hurt memory may be, in the remembering entity's ticks. */
    static final int MEMORY_TICKS = 100;

    private final BoundVex vex;

    @Nullable
    private LivingEntity found;

    HurtBySummonerSideGoal(BoundVex vex) {
        super(vex, false);
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!vex.scanDue(BoundVex.SCAN_INTERVAL)) {
            return false;
        }
        ServerPlayer owner = vex.summoner().orElse(null);
        if (owner == null) {
            return false;
        }
        found = remembered(vex, vex.getLastHurtByMob(), vex.getLastHurtByMobTimestamp());
        if (found == null) {
            found = remembered(owner, owner.getLastHurtByMob(), owner.getLastHurtByMobTimestamp());
        }
        if (found == null) {
            found = remembered(owner, owner.getLastHurtMob(), owner.getLastHurtMobTimestamp());
        }
        if (found == null) {
            found = allyGrudge(owner);
        }
        return found != null;
    }

    /** {@code remembered} when the memory is fresh on {@code rememberer}'s clock and the vex may take it. */
    @Nullable
    private LivingEntity remembered(LivingEntity rememberer, @Nullable LivingEntity remembered, int timestamp) {
        if (remembered == null || rememberer.tickCount - timestamp > MEMORY_TICKS) {
            return null;
        }
        return vex.keepsTarget(remembered) ? remembered : null;
    }

    /** The attacker of the first ally in the guard area that remembers one. */
    @Nullable
    private LivingEntity allyGrudge(ServerPlayer owner) {
        for (Mob ally : vex.alliesInGuardArea(owner)) {
            LivingEntity attacker = remembered(ally, ally.getLastHurtByMob(), ally.getLastHurtByMobTimestamp());
            if (attacker != null) {
                return attacker;
            }
        }
        return null;
    }

    /** {@code TargetGoal.start()} does not set the target (docs/api-notes.md §21), so this does. */
    @Override
    public void start() {
        vex.setTarget(found);
        super.start();
    }

    /** {@code keepsTarget} alone: this goal has no sight rule (spec §3.4 item 1), so {@code super}'s checks are not run. */
    @Override
    public boolean canContinueToUse() {
        return vex.keepsTarget(vex.getTarget());
    }
}
```

`src/main/java/io/github/willrieske/spellcraft/evoking/AllyFightGoal.java`:

```java
package io.github.willrieske.spellcraft.evoking;

import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import org.jspecify.annotations.Nullable;

/**
 * Vexes join fights their allies start (Evoking II spec §3.4, second in the target selector): on each scan, the
 * nearest hostile ({@code Enemy}) that an ally mob inside the guard area (an iron golem, a wolf, another Bound Vex; never
 * this vex itself, {@code BoundVex.alliesInGuardArea}) has as its target, if the vex may take it
 * ({@code BoundVex.keepsTarget}).
 */
final class AllyFightGoal extends TargetGoal {
    private final BoundVex vex;

    @Nullable
    private LivingEntity found;

    AllyFightGoal(BoundVex vex) {
        super(vex, false);
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!vex.scanDue(BoundVex.SCAN_INTERVAL)) {
            return false;
        }
        ServerPlayer owner = vex.summoner().orElse(null);
        if (owner == null) {
            return false;
        }
        found = null;
        double nearest = Double.MAX_VALUE;
        for (Mob ally : vex.alliesInGuardArea(owner)) {
            LivingEntity target = ally.getTarget();
            if (target instanceof Enemy && vex.keepsTarget(target)) {
                double distance = vex.distanceToSqr(target);
                if (distance < nearest) {
                    nearest = distance;
                    found = target;
                }
            }
        }
        return found != null;
    }

    /** {@code TargetGoal.start()} does not set the target (docs/api-notes.md §21), so this does. */
    @Override
    public void start() {
        vex.setTarget(found);
        super.start();
    }

    /** {@code keepsTarget} alone: this goal has no sight rule (spec §3.4 item 2), so {@code super}'s checks are not run. */
    @Override
    public boolean canContinueToUse() {
        return vex.keepsTarget(vex.getTarget());
    }
}
```

`src/main/java/io/github/willrieske/spellcraft/evoking/NearestHostileGoal.java`:

```java
package io.github.willrieske.spellcraft.evoking;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

/**
 * The patrol goal (Evoking II spec §3.4, third in the target selector): vanilla's nearest-target search, with line of
 * sight, for anything hostile ({@code Enemy}) but a creeper whose feet are inside the guard box and that the vex may
 * take. It scans on the shared clock ({@code BoundVex.scanDue}), not on vanilla's dice: the six-argument constructor
 * with a random interval of 1 makes the roll always pass (Divergence 16, docs/api-notes.md §21). To keep a target it
 * runs {@code super}'s checks (sight: dropped after 60 unseen ticks; they end by re-setting the target, which calls no
 * wolves for an unchanged target) and then {@code BoundVex.keepsTarget} (a target that became an ally, died or left
 * reach is dropped), because vanilla's never re-checks the selector.
 */
final class NearestHostileGoal extends NearestAttackableTargetGoal<LivingEntity> {
    private final BoundVex vex;

    NearestHostileGoal(BoundVex vex) {
        super(
                vex,
                LivingEntity.class,
                1,
                true,
                false,
                (target, level) -> target instanceof Enemy
                        && !(target instanceof Creeper)
                        && vex.inGuardArea(target)
                        && vex.keepsTarget(target));
        this.vex = vex;
    }

    /** Only on a scan tick; then vanilla's search, whose roll always passes with a random interval of 1. */
    @Override
    public boolean canUse() {
        return vex.scanDue(BoundVex.SCAN_INTERVAL) && super.canUse();
    }

    /** The broad phase of the search; the selector keeps only what {@code inGuardArea} accepts. */
    @Override
    protected AABB getTargetSearchArea(double followDistance) {
        return vex.guardArea();
    }

    @Override
    public boolean canContinueToUse() {
        return vex.keepsTarget(mob.getTarget()) && super.canContinueToUse();
    }
}
```

In `BoundVex`:

- `registerGoals` gains, after the goal-selector lines:

```java
        targetSelector.addGoal(1, new HurtBySummonerSideGoal(this));
        targetSelector.addGoal(2, new AllyFightGoal(this));
        targetSelector.addGoal(3, new NearestHostileGoal(this));
```

- two constants after `STAYING_TAG`:

```java
    /** Ticks between the target goals' scans (spec §3.4 "every 10 ticks"); read through {@link #scanDue}. */
    static final int SCAN_INTERVAL = 10;

    /** How far the summoner's wolves hear the call to a fight (spec §3.4), feet to feet. */
    static final double WOLF_CALL_RANGE = 16.0;
```

- after `guardArea()`, the one ally scan both the grudge and the ally goal use (imports `java.util.List`):

```java
    /**
     * The summoner's allies ({@code Allies.isAllyOf}) whose feet are inside this vex's guard area, this vex excepted:
     * a vex is its own summoner's ally, and counting itself would hand its own target to the ally goal (spec §3.4).
     *
     * @param owner the summoner
     * @return the allies in the area
     */
    List<Mob> alliesInGuardArea(ServerPlayer owner) {
        UUID self = getUUID();
        return level().getEntitiesOfClass(
                Mob.class,
                guardArea(),
                mob -> !mob.getUUID().equals(self) && inGuardArea(mob) && Allies.isAllyOf(owner, mob));
    }
```

- and the override, after `mobInteract` (imports `net.minecraft.world.entity.animal.wolf.Wolf`):

```java
    /**
     * Takes the target and, when it is a new one, calls the summoner's tamed, idle, un-sitting wolves within
     * {@link #WOLF_CALL_RANGE} that want to attack it ({@code Wolf.wantsToAttack}: never a creeper, a ghast or a
     * player the summoner may not harm) onto it (spec §3.4). "New" means it differs from the target held just before:
     * {@code TargetGoal.canContinueToUse} re-sets the same target on every full tick (docs/api-notes.md §21), and those
     * re-sets call nobody. A change {@code GolemMercy} cancels, a target {@code getTarget} refuses, and clearing a
     * target call nobody either.
     */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        LivingEntity before = getTarget();
        super.setTarget(target);
        LivingEntity set = getTarget();
        if (target == null
                || set == null
                || !set.getUUID().equals(target.getUUID())
                || (before != null && before.getUUID().equals(target.getUUID()))
                || !(level() instanceof ServerLevel level)) {
            return;
        }
        summoner().ifPresent(owner -> {
            for (Wolf wolf : level.getEntitiesOfClass(
                    Wolf.class,
                    getBoundingBox().inflate(WOLF_CALL_RANGE),
                    wolf -> wolf.isTame()
                            && wolf.isOwnedBy(owner)
                            && wolf.getTarget() == null
                            && !wolf.isOrderedToSit()
                            && wolf.distanceToSqr(this) <= WOLF_CALL_RANGE * WOLF_CALL_RANGE
                            && wolf.wantsToAttack(target, owner))) {
                wolf.setTarget(target);
            }
        });
    }
```

- Update the class Javadoc's goal sentence to name the three target goals.

- [x] **Step 2: The GameTests**

In `BoundVexGameTests.registerAll`, add (the tests that wait on a scan pass their own tick limit `LONG`; a scan comes at most every 10 ticks, so 20 ticks is the floor and 100 the margin):

```java
        SpellcraftGameTests.add(SHARED, "bound_vex_three_target_goals", BoundVexGameTests::threeTargetGoals);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_attacks_a_hostile_in_the_area", LONG, 0, BoundVexGameTests::attacksAHostile);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_ignores_a_pig_but_not_a_husk", LONG, 0, BoundVexGameTests::ignoresAPigButNotAHusk);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_ignores_a_creeper_until_provoked", LONG, 0, BoundVexGameTests::ignoresACreeperUntilProvoked);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_targets_what_hurt_the_summoner", LONG, 0, BoundVexGameTests::targetsWhatHurtTheSummoner);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_targets_what_the_summoner_hit", LONG, 0, BoundVexGameTests::targetsWhatTheSummonerHit);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_targets_what_hurt_an_ally", LONG, 0, BoundVexGameTests::targetsWhatHurtAnAlly);
        SpellcraftGameTests.add(SHARED, "bound_vex_joins_an_allys_fight", LONG, 0, BoundVexGameTests::joinsAnAllysFight);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_calls_the_summoners_wolves", LONG, 0, BoundVexGameTests::callsTheSummonersWolves);
        FangGameTests.addPadded(
                SHARED, "bound_vex_wolf_call_skips_the_wrong_wolves", BoundVexGameTests::wolfCallSkipsTheWrongWolves);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_wolf_call_only_on_a_new_target", BoundVexGameTests::wolfCallOnlyOnANewTarget);
        SpellcraftGameTests.add(SHARED, "bound_vex_wolf_call_skips_a_creeper", BoundVexGameTests::wolfCallSkipsACreeper);
        FangGameTests.addPadded(
                SHARED, "bound_vex_drops_a_target_out_of_reach", BoundVexGameTests::dropsATargetOutOfReach);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_drops_a_target_that_becomes_an_ally", LONG, 0, BoundVexGameTests::dropsANewAlly);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_no_summoner_no_target", LONG, 0, BoundVexGameTests::noSummonerNoTarget);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_held_by_the_patrol_goal", LONG, 0, BoundVexGameTests::heldByThePatrolGoal);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_ignores_a_stale_memory", LONG, 0, BoundVexGameTests::ignoresAStaleMemory);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_own_attacker_comes_first", LONG, 0, BoundVexGameTests::ownAttackerComesFirst);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_a_creative_attacker_does_not_block",
                LONG,
                0,
                BoundVexGameTests::creativeAttackerDoesNotBlock);
        FangGameTests.addPadded(
                SHARED, "bound_vex_ignores_an_ally_outside_the_area", BoundVexGameTests::ignoresAnAllyOutsideTheArea);
```

Imports to add: `java.util.Map`, `java.util.TreeMap`, `net.minecraft.world.entity.LivingEntity`, `net.minecraft.world.entity.Mob`, `net.minecraft.world.entity.ai.goal.WrappedGoal`, `net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal`, `net.minecraft.world.entity.animal.pig.Pig`, `net.minecraft.world.entity.animal.wolf.Wolf`, `net.minecraft.world.entity.monster.Creeper` (`AllyAttachments`, `GameType` and `EntityTypes` are already imported). `GoalSelector.removeAllGoals(Predicate<Goal>)` tests each wrapped goal's inner `Goal` (`GoalSelector.java:32-40`), so `goal -> goal instanceof NearestAttackableTargetGoal` removes the patrol goal. `FangGameTests.pig` does not discard its pig, so each test does. Then the constants, helpers and tests:

```java
    /** Ticks for a test that waits for a 10-tick target scan or watches a 40-tick window. */
    private static final int LONG = 100;

    /** The window over which "never a target" is watched: four target scans. */
    private static final int TARGET_WINDOW = 40;

    /** The box's bottom north-east corner, a second spot inside the box for an ally or a second mob. */
    private static final BlockPos SIDE_SPOT = new BlockPos(2, 1, 0);

    /** The box's bottom south-west corner, a third spot inside the box. */
    private static final BlockPos CORNER_SPOT = new BlockPos(0, 1, 2);

    /** A tamed wolf of {@code owner} with no AI at {@code relative}, discarded when the test ends. */
    private static Wolf pet(GameTestHelper helper, BlockPos relative, ServerPlayer owner) {
        Wolf wolf = helper.spawn(EntityTypes.WOLF, relative);
        wolf.setNoAi(true);
        wolf.tame(owner);
        whenDone(helper, wolf::discard);
        return wolf;
    }

    /** A pig with no AI at {@code relative}, discarded when the test ends. */
    private static Pig pigHere(GameTestHelper helper, BlockPos relative) {
        Pig pig = FangGameTests.pig(helper, relative);
        whenDone(helper, pig::discard);
        return pig;
    }

    /** Whether {@code mob}'s target is {@code expected} (by UUID). */
    private static boolean targets(Mob mob, LivingEntity expected) {
        LivingEntity target = mob.getTarget();
        return target != null && target.getUUID().equals(expected.getUUID());
    }

    /** The simple class name of the vex's running target goal, or {@code "none"}. */
    private static String runningTargetGoal(BoundVex vex) {
        return vex.targetSelector.getAvailableGoals().stream()
                .filter(WrappedGoal::isRunning)
                .map(wrapped -> wrapped.getGoal().getClass().getSimpleName())
                .findFirst()
                .orElse("none");
    }

    /**
     * When a Bound Vex is made, should carry exactly the three target goals of spec §3.4, in its order: the grudge goal
     * at priority 1, the ally goal at 2, the patrol goal at 3.
     *
     * <p>Method under test: {@code BoundVex.registerGoals} (protected)
     */
    static void threeTargetGoals(GameTestHelper helper) {
        // Arrange
        BoundVex vex = boundVex(helper, VEX_SPOT, summoner(helper));

        // Act
        Map<Integer, String> byPriority = new TreeMap<>();
        vex.targetSelector
                .getAvailableGoals()
                .forEach(wrapped -> byPriority.put(wrapped.getPriority(), wrapped.getGoal().getClass().getSimpleName()));

        // Assert
        helper.assertValueEqual(
                byPriority,
                Map.of(1, "HurtBySummonerSideGoal", 2, "AllyFightGoal", 3, "NearestHostileGoal"),
                "the three target goals, in order");
        helper.succeed();
    }

    /**
     * When a husk stands inside a parked Bound Vex's guard area with the summoner nearer than the husk, should take the
     * husk as its target and never the summoner.
     *
     * <p>Method under test: {@code NearestHostileGoal} (package-private), through the target selector
     */
    static void attacksAHostile(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);

        // Act
        Husk husk = husk(helper, PREY_SPOT);

        // Assert
        helper.succeedWhen(() -> helper.assertTrue(targets(vex, husk), "the husk, not the nearer summoner, is the target"));
    }

    /**
     * When only a pig stands in the guard area, should take no target over forty ticks; a husk added afterwards should
     * become the target (the control: the vex and its line of sight work here).
     *
     * <p>Method under test: {@code NearestHostileGoal} (package-private): its {@code Enemy} filter
     */
    static void ignoresAPigButNotAHusk(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        pigHere(helper, PREY_SPOT);
        Husk[] husk = new Husk[1];

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(TARGET_WINDOW, () -> helper.assertTrue(vex.getTarget() == null, "a pig is never a target"))
                .thenExecute(() -> husk[0] = husk(helper, SIDE_SPOT))
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, husk[0]), "the husk is the target"))
                .thenSucceed();
    }

    /**
     * When a calm creeper stands in the guard area, should take no target over forty ticks; once the vex remembers the
     * creeper hurting it, should take it as its target. (The memory is set directly: a real hit would knock the parked
     * vex out of the box and past reach.)
     *
     * <p>Method under test: {@code NearestHostileGoal}'s creeper exclusion and {@code HurtBySummonerSideGoal}'s own-hurt
     * memory (both package-private)
     */
    static void ignoresACreeperUntilProvoked(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Creeper creeper = helper.spawn(EntityTypes.CREEPER, PREY_SPOT);
        creeper.setNoAi(true);
        whenDone(helper, creeper::discard);

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        TARGET_WINDOW, () -> helper.assertTrue(vex.getTarget() == null, "a calm creeper is never a target"))
                .thenExecute(() -> vex.setLastHurtByMob(creeper))
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, creeper), "the creeper that hurt it is the target"))
                .thenSucceed();
    }

    /**
     * When a husk hurts the summoner, should take the husk as its target.
     *
     * <p>Method under test: {@code HurtBySummonerSideGoal} (package-private): the summoner's attacker
     */
    static void targetsWhatHurtTheSummoner(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Husk husk = husk(helper, PREY_SPOT);
        // The husk is a hostile in the area too, so block the patrol goal's view of it: the memory must do the work.
        vex.targetSelector.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal);

        // Act
        player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(husk), 1.0F);

        // Assert
        helper.assertTrue(
                player.getLastHurtByMob() != null && player.getLastHurtByMob().getUUID().equals(husk.getUUID()),
                "setup: the summoner remembers the husk");
        helper.succeedWhen(() -> helper.assertTrue(targets(vex, husk), "the summoner's attacker is the target"));
    }

    /**
     * When the summoner hits a pig (what {@code Player.attack} records as the last hurt mob), should take the pig as
     * its target even though a pig is not hostile.
     *
     * <p>Method under test: {@code HurtBySummonerSideGoal} (package-private): the summoner's victim
     */
    static void targetsWhatTheSummonerHit(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Pig pig = pigHere(helper, PREY_SPOT);

        // Act
        player.setLastHurtMob(pig);

        // Assert
        helper.succeedWhen(() -> helper.assertTrue(targets(vex, pig), "the pig the summoner hit is the target"));
    }

    /**
     * When a husk hurts the summoner's tamed wolf inside the guard area, should take the husk as its target.
     *
     * <p>Method under test: {@code HurtBySummonerSideGoal} (package-private): an ally's attacker
     */
    static void targetsWhatHurtAnAlly(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Wolf wolf = pet(helper, SIDE_SPOT, player);
        Husk husk = husk(helper, PREY_SPOT);
        vex.targetSelector.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal);

        // Act
        wolf.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(husk), 1.0F);

        // Assert
        helper.succeedWhen(() -> helper.assertTrue(targets(vex, husk), "the ally's attacker is the target"));
    }

    /**
     * When the summoner's tamed wolf inside the guard area has a husk as its target, should take that husk too.
     *
     * <p>Method under test: {@code AllyFightGoal} (package-private)
     */
    static void joinsAnAllysFight(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Wolf wolf = pet(helper, SIDE_SPOT, player);
        Husk husk = husk(helper, PREY_SPOT);
        vex.targetSelector.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal);

        // Act
        wolf.setTarget(husk);

        // Assert
        helper.succeedWhen(() -> helper.assertTrue(targets(vex, husk), "the ally's target is the target"));
    }

    /**
     * When a Bound Vex takes a target through its goals, should give it to the summoner's tamed, idle wolf within
     * sixteen blocks, and not to a sitting one.
     *
     * <p>Method under test: {@link BoundVex#setTarget}, through the target selector
     */
    static void callsTheSummonersWolves(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Wolf idle = pet(helper, SIDE_SPOT, player);
        Wolf sitting = pet(helper, CORNER_SPOT, player);
        sitting.setOrderedToSit(true);

        // Act
        Husk husk = husk(helper, PREY_SPOT);

        // Assert
        helper.succeedWhen(() -> {
            helper.assertTrue(targets(vex, husk), "setup: the vex took the husk");
            helper.assertTrue(targets(idle, husk), "the idle wolf joins");
            helper.assertTrue(sitting.getTarget() == null, "the sitting wolf stays put");
        });
    }

    /**
     * When a Bound Vex takes a husk, should call only the summoner's tamed, idle, un-sitting wolf within sixteen blocks:
     * not an untamed wolf, another player's tamed wolf, a wolf already fighting, a sitting wolf, or the summoner's wolf
     * about seventeen blocks away (inside the cube a box query covers, outside the sixteen-block sphere).
     *
     * <p>Method under test: {@link BoundVex#setTarget}
     */
    static void wolfCallSkipsTheWrongWolves(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        ServerPlayer stranger = summoner(helper);
        BoundVex vex = parked(helper, FEET, player);
        vex.targetSelector.removeAllGoals(goal -> true);
        Husk husk = husk(helper, PREY_SPOT);
        Pig pig = pigHere(helper, SIDE_SPOT);
        Wolf idle = pet(helper, FEET.east(2), player);
        Wolf untamed = helper.spawn(EntityTypes.WOLF, FEET.east(3));
        untamed.setNoAi(true);
        whenDone(helper, untamed::discard);
        Wolf strangers = pet(helper, FEET.west(2), stranger);
        Wolf busy = pet(helper, FEET.west(3), player);
        busy.setTarget(pig);
        Wolf sitting = pet(helper, FEET.south(2), player);
        sitting.setOrderedToSit(true);
        Wolf far = pet(helper, FEET.offset(-12, 0, -12), player);
        helper.assertTrue(far.distanceTo(vex) > 16.0, "setup: the far wolf is past sixteen blocks");

        // Act
        vex.setTarget(husk);

        // Assert
        helper.assertTrue(targets(idle, husk), "the summoner's idle wolf joins (the control)");
        helper.assertTrue(untamed.getTarget() == null, "an untamed wolf is not called");
        helper.assertTrue(strangers.getTarget() == null, "another player's wolf is not called");
        helper.assertTrue(targets(busy, pig), "a wolf already fighting keeps its own target");
        helper.assertTrue(sitting.getTarget() == null, "a sitting wolf is not called");
        helper.assertTrue(far.getTarget() == null, "a wolf past sixteen blocks is not called");
        helper.succeed();
    }

    /**
     * When a Bound Vex re-sets the target it already holds (as {@code TargetGoal.canContinueToUse} does on every full
     * tick), should not call a wolf that has gone idle; clearing the target should call nobody either.
     *
     * <p>Method under test: {@link BoundVex#setTarget}
     */
    static void wolfCallOnlyOnANewTarget(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.targetSelector.removeAllGoals(goal -> true);
        Husk husk = husk(helper, PREY_SPOT);
        Wolf wolf = pet(helper, SIDE_SPOT, player);
        vex.setTarget(husk);
        helper.assertTrue(targets(wolf, husk), "setup: the new target called the wolf");
        wolf.setTarget(null);

        // Act
        vex.setTarget(husk);

        // Assert
        helper.assertTrue(wolf.getTarget() == null, "the same target again calls nobody");
        vex.setTarget(null);
        helper.assertTrue(wolf.getTarget() == null, "clearing the target calls nobody");
        helper.succeed();
    }

    /**
     * When a Bound Vex takes a creeper (one that provoked it), should not call the summoner's wolf into the blast
     * ({@code Wolf.wantsToAttack}); a husk taken next should call it (the control).
     *
     * <p>Method under test: {@link BoundVex#setTarget}
     */
    static void wolfCallSkipsACreeper(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.targetSelector.removeAllGoals(goal -> true);
        Creeper creeper = helper.spawn(EntityTypes.CREEPER, PREY_SPOT);
        creeper.setNoAi(true);
        whenDone(helper, creeper::discard);
        Wolf wolf = pet(helper, SIDE_SPOT, player);

        // Act
        vex.setTarget(creeper);

        // Assert
        helper.assertTrue(targets(vex, creeper), "setup: the vex holds the creeper");
        helper.assertTrue(wolf.getTarget() == null, "no wolf is sent at a creeper");
        Husk husk = husk(helper, CORNER_SPOT);
        vex.setTarget(husk);
        helper.assertTrue(targets(wolf, husk), "a husk calls the wolf");
        helper.succeed();
    }

    /**
     * When a parked Bound Vex's husk target is moved past the 7.25 reach and out of the guard box, should drop it
     * within a few ticks and not take it again.
     *
     * <p>Method under test: {@link BoundVex#keepsTarget}, through every target goal's {@code canContinueToUse}
     */
    static void dropsATargetOutOfReach(GameTestHelper helper) {
        // Arrange: both outside the shared box, so the barrier shell is not in the way
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, FEET.north(5), player);
        Husk husk = husk(helper, FEET.north(7));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, husk), "setup: the husk is the target"))
                .thenExecute(() -> FangGameTests.moveTo(helper, husk, FEET, 0, -FAR_NORTH))
                .thenWaitUntil(() -> helper.assertTrue(vex.getTarget() == null, "dropped once out of reach"))
                .thenExecuteFor(20, () -> helper.assertTrue(vex.getTarget() == null, "and not taken again"))
                .thenSucceed();
    }

    /**
     * When a parked Bound Vex has an untamed wolf as its target (the summoner hit it) and the summoner then tames it,
     * should drop the target within a few ticks.
     *
     * <p>Method under test: {@link BoundVex#keepsTarget}, through {@code HurtBySummonerSideGoal.canContinueToUse}
     */
    static void dropsANewAlly(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Wolf wolf = helper.spawn(EntityTypes.WOLF, PREY_SPOT);
        wolf.setNoAi(true);
        whenDone(helper, wolf::discard);
        player.setLastHurtMob(wolf);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, wolf), "setup: the untamed wolf is the target"))
                .thenExecute(() -> wolf.tame(player))
                .thenWaitUntil(() -> helper.assertTrue(vex.getTarget() == null, "the tamed wolf is dropped"))
                .thenSucceed();
    }

    /**
     * When a Bound Vex's summoner mark is taken away, should take no target over forty ticks with a husk beside it;
     * once the mark is back, should take the husk (the control: the vex ticks and sees the husk).
     *
     * <p>Method under test: {@link BoundVex#isPrey(net.minecraft.world.entity.LivingEntity)}, through every target goal
     */
    static void noSummonerNoTarget(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.removeData(AllyAttachments.SUMMONER);
        Husk husk = husk(helper, PREY_SPOT);

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(TARGET_WINDOW, () -> helper.assertTrue(vex.getTarget() == null, "nothing without a summoner"))
                .thenExecute(() -> vex.setData(AllyAttachments.SUMMONER, player.getUUID()))
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, husk), "with the mark back, the husk is the target"))
                .thenSucceed();
    }

    /**
     * When a parked Bound Vex takes a husk through the patrol goal with no ally near, should still hold it through the
     * patrol goal two scans later: the ally goal does not count the vex itself as an ally and take over.
     *
     * <p>Method under test: {@code AllyFightGoal} and {@code BoundVex.alliesInGuardArea} (package-private)
     */
    static void heldByThePatrolGoal(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Husk husk = husk(helper, PREY_SPOT);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, husk), "setup: the husk is the target"))
                .thenIdle(20) // two scans of the ally goal
                .thenExecute(() -> {
                    helper.assertTrue(targets(vex, husk), "still the husk");
                    helper.assertValueEqual(runningTargetGoal(vex), "NearestHostileGoal", "held by the patrol goal");
                })
                .thenSucceed();
    }

    /**
     * When the summoner hit a pig more than a hundred of its own ticks ago, should take no target over forty ticks; the
     * same hit made fresh should make the pig the target (the control).
     *
     * <p>Method under test: {@code HurtBySummonerSideGoal} (package-private): the freshness window
     */
    static void ignoresAStaleMemory(GameTestHelper helper) {
        // Arrange: the mock summoner is never ticked, so its clock is what the test sets
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Pig pig = pigHere(helper, PREY_SPOT);
        player.setLastHurtMob(pig);
        player.tickCount += 500;

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(TARGET_WINDOW, () -> helper.assertTrue(vex.getTarget() == null, "a stale hit is forgotten"))
                .thenExecute(() -> player.setLastHurtMob(pig))
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, pig), "a fresh hit makes the pig the target"))
                .thenSucceed();
    }

    /**
     * When the vex remembers one husk hurting it and the summoner remembers another, both fresh, should take the vex's
     * own attacker: the first memory in the spec's order wins.
     *
     * <p>Method under test: {@code HurtBySummonerSideGoal} (package-private): the memory order
     */
    static void ownAttackerComesFirst(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.targetSelector.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal);
        Husk own = husk(helper, PREY_SPOT);
        Husk summoners = husk(helper, SIDE_SPOT);

        // Act
        vex.setLastHurtByMob(own);
        player.setLastHurtByMob(summoners);

        // Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.getTarget() != null, "setup: a target is taken"))
                .thenExecute(() -> helper.assertTrue(targets(vex, own), "the vex's own attacker, not the summoner's"))
                .thenSucceed();
    }

    /**
     * When the summoner's remembered attacker is a creative-mode player (whom a mob cannot target), should keep a husk
     * taken through the patrol goal through every scan: the memory is passed over ({@code keepsTarget}'s
     * {@code canAttack}), not taken and dropped each scan.
     *
     * <p>Method under test: {@link BoundVex#keepsTarget}, through {@code HurtBySummonerSideGoal.canUse}
     */
    static void creativeAttackerDoesNotBlock(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        ServerPlayer creative = MockPlayers.inLevel(helper, GameType.CREATIVE);
        MockPlayers.moveIntoTest(helper, creative);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        player.setLastHurtByMob(creative);
        Husk husk = husk(helper, PREY_SPOT);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, husk), "setup: the husk is the target"))
                .thenExecuteFor(30, () -> helper.assertTrue(targets(vex, husk), "the husk is kept through three scans"))
                .thenSucceed();
    }

    /**
     * When the summoner's tamed wolf stands outside the guard area, targeting a husk within reach and remembering it as
     * its attacker, should take no target over forty ticks; once the wolf is inside the area, should take the husk.
     *
     * <p>Method under test: {@code BoundVex.alliesInGuardArea}, through {@code HurtBySummonerSideGoal} and
     * {@code AllyFightGoal}
     */
    static void ignoresAnAllyOutsideTheArea(GameTestHelper helper) {
        // Arrange: the husk is seven blocks north of the vex (within reach, outside the box), the wolf six
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, FEET, player);
        Husk husk = husk(helper, FEET.north(7));
        Wolf wolf = pet(helper, FEET.north(6), player);
        wolf.setTarget(husk);
        wolf.setLastHurtByMob(husk);
        helper.assertFalse(vex.inGuardArea(wolf), "setup: the wolf is outside the area");

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        TARGET_WINDOW, () -> helper.assertTrue(vex.getTarget() == null, "an ally outside the area is ignored"))
                .thenExecute(() -> wolf.setPos(helper.absoluteVec(Vec3.atBottomCenterOf(SIDE_SPOT))))
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, husk), "inside the area, its fight is joined"))
                .thenSucceed();
    }
```

In `ignoresAnAllyOutsideTheArea` the wolf's attacker memory ages on the wolf's own clock (a NoAI mob still ticks), about 40 ticks by the time it moves in, inside the 100-tick window, so either goal may take the husk then; the test only asks that one does.

- [x] **Step 3: Format, build, GameTests**

Run: `./gradlew spotlessApply --console=plain`, then `./gradlew build --console=plain > build/build.log 2>&1; echo "exit=$?"; tail -40 build/build.log`. Expected: `exit=0`.
Run in the background: `./gradlew runGameTestServer --console=plain > build/runGameTestServer.log 2>&1; echo "exit=$?"; grep -n "FAILED\|required tests" build/runGameTestServer.log | tail -5`. Expected: `exit=0`, `All 197 required tests passed` (Task 4's 177 + the twenty registered above). Then run it a second time: a test that passes once and fails once depends on entity-id parity or a random roll, which this task's clock is meant to rule out.

- [x] **Step 4: Commit**

```bash
git add src/main/java/io/github/willrieske/spellcraft/evoking/HurtBySummonerSideGoal.java src/main/java/io/github/willrieske/spellcraft/evoking/AllyFightGoal.java src/main/java/io/github/willrieske/spellcraft/evoking/NearestHostileGoal.java src/main/java/io/github/willrieske/spellcraft/evoking/BoundVex.java src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexGameTests.java
```

```bash
git commit -m "feat(evoking): Bound Vexes fight what their side fights and call the summoner's wolves"
```

Then `git log -1 --oneline` shows the commit.

> **As built (`7d4f5d7`):** diverged — production code as planned; eight more GameTests (205 in all, not 197): an ally and a hostile whose box touches the guard area while their feet are outside, patrol start sight behind a wall and a drop when unseen (`mustSee`), patrol- and ally-goal drops of a husk that becomes an ally, the summoner's victim before an ally's attacker, and a stale summoner's-attacker memory; reason: task review (the planned tests could not fail without the guard-area filter, `mustSee` or the `keepsTarget` overrides; red runs shown for the guard-area and sight tests).

---

### Task 6: Fleeing a creeper, dodging a golem, repairing a golem

**Files:**

- Create: `src/main/java/io/github/willrieske/spellcraft/evoking/FleeCreeperGoal.java`
- Create: `src/main/java/io/github/willrieske/spellcraft/evoking/DodgeGolemGoal.java`
- Create: `src/main/java/io/github/willrieske/spellcraft/evoking/RepairGolemGoal.java`
- Modify: `src/main/java/io/github/willrieske/spellcraft/evoking/BoundVex.java` (`registerGoals`: three lines)
- Modify: `src/main/java/io/github/willrieske/spellcraft/evoking/BoundVexEvents.java` (`onServerStopped`: one line)
- Modify: `src/main/java/io/github/willrieske/spellcraft/gametest/GameTestSupport.java` (`drainSounds`)
- Modify: `src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexGameTests.java` (nine tests and their helpers)

**Interfaces:**

- Consumes: `BoundVex.summoner/inGuardArea/guardArea/scanDue/getMoveControl/getTarget` (Task 3), `BoundVex.SCAN_INTERVAL` (Task 5); `FollowSummonerGoal` and its public `ORBIT_MAX` (Task 4), `BoundVexEvents.onServerStopped` (Task 4); `BoundVexGameTests.FEET/NORTH/boundVex/husk` (Tasks 3–4); `Allies.isAllyOf`; `GolemGameTests.golem(helper, pos, health)`, `GolemGameTests.anger(golem, player)`; api-notes §21.
- Produces: `FleeCreeperGoal` (`NOTICE_RANGE` 8.0, `SAFE_DISTANCE` 8.0), `DodgeGolemGoal` (`NOTICE_RANGE` 3.0, `FACING` 0.5, `DART` 3.0), `RepairGolemGoal` (`HEAL` 25.0F, `TAP_INTERVAL` 30, `REACH` 1.5; static `clearClaims()`); `GameTestSupport.drainSounds(ServerPlayer)` → `List<SoundEvent>`.

- [x] **Step 1: The three goals**

`src/main/java/io/github/willrieske/spellcraft/evoking/FleeCreeperGoal.java`:

```java
package io.github.willrieske.spellcraft.evoking;

import java.util.Comparator;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Bound Vex flies straight away from a hissing creeper (Evoking II spec §3.5): when a creeper within
 * {@link #NOTICE_RANGE} blocks (feet to feet) is swelling ({@code getSwellDir() > 0}), the vex flies directly away from
 * it to {@link #SAFE_DISTANCE} blocks and holds there until the creeper stops swelling, dies or explodes. The goal
 * outranks the charge goal (both hold the MOVE flag), so the target is kept and the charge resumes afterwards. The
 * selector ticks a goal right after starting it, so {@code start} needs no move of its own.
 */
final class FleeCreeperGoal extends Goal {
    /** How near a swelling creeper must be to be noticed. */
    static final double NOTICE_RANGE = 8.0;

    /** How far from the creeper the vex flies. */
    static final double SAFE_DISTANCE = 8.0;

    private static final double SPEED = 1.0;

    private final BoundVex vex;

    @Nullable
    private Creeper creeper;

    FleeCreeperGoal(BoundVex vex) {
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        creeper = nearestSwelling();
        return creeper != null;
    }

    /** The broad-phase box is a cube; the distance filter makes the range the sphere the spec names. */
    @Nullable
    private Creeper nearestSwelling() {
        AABB around = new AABB(vex.position(), vex.position()).inflate(NOTICE_RANGE);
        return vex.level()
                .getEntitiesOfClass(
                        Creeper.class,
                        around,
                        candidate -> candidate.isAlive()
                                && candidate.getSwellDir() > 0
                                && vex.distanceToSqr(candidate) <= NOTICE_RANGE * NOTICE_RANGE)
                .stream()
                .min(Comparator.comparingDouble(vex::distanceToSqr))
                .orElse(null);
    }

    @Override
    public boolean canContinueToUse() {
        return creeper != null && creeper.isAlive() && creeper.getSwellDir() > 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /** Aims for the point {@link #SAFE_DISTANCE} + 1 blocks from the creeper, straight away from it, at the vex's height. */
    @Override
    public void tick() {
        if (creeper == null) {
            return;
        }
        Vec3 away = vex.position().subtract(creeper.position());
        if (away.length() >= SAFE_DISTANCE) {
            return;
        }
        Vec3 flat = new Vec3(away.x, 0.0, away.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = new Vec3(1.0, 0.0, 0.0);
        }
        Vec3 spot = creeper.position()
                .add(flat.normalize().scale(SAFE_DISTANCE + 1.0))
                .add(0.0, vex.getY() - creeper.getY(), 0.0);
        vex.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, SPEED);
    }

    @Override
    public void stop() {
        creeper = null;
    }
}
```

`src/main/java/io/github/willrieske/spellcraft/evoking/DodgeGolemGoal.java`:

```java
package io.github.willrieske.spellcraft.evoking;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Bound Vex darts out of an iron golem's swing (Evoking II spec §3.6): when a golem within {@link #NOTICE_RANGE} is
 * mid-swing ({@code getAttackAnimationTick() > 0}) and its head faces the vex (the dot of its head look,
 * {@code getHeadLookAngle()}, and the direction from its eyes to the vex's middle is over {@link #FACING}), the vex
 * moves {@link #DART} blocks sideways, perpendicular to that look, in one move, every time. The head, not the body:
 * a golem winding up a swing turns its head to its target first (docs/api-notes.md §21). One dart per swing: the goal
 * waits out the swing's ten ticks before it can start again.
 */
final class DodgeGolemGoal extends Goal {
    /** How near a swinging golem must be: the half-size of the box searched around the vex. */
    static final double NOTICE_RANGE = 3.0;

    /** How squarely the golem must face the vex: the dot of two unit vectors. */
    static final double FACING = 0.5;

    /** How far the vex darts. */
    static final double DART = 3.0;

    /** A golem's swing animation lasts this many ticks. */
    private static final int SWING_TICKS = 10;

    private static final double SPEED = 1.5;

    private final BoundVex vex;
    private int readyAt;

    @Nullable
    private IronGolem golem;

    DodgeGolemGoal(BoundVex vex) {
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (vex.tickCount < readyAt) {
            return false;
        }
        AABB around = new AABB(vex.position(), vex.position()).inflate(NOTICE_RANGE);
        golem = vex.level()
                .getEntitiesOfClass(IronGolem.class, around, candidate -> candidate.isAlive() && isSwingingAtVex(candidate))
                .stream()
                .findFirst()
                .orElse(null);
        return golem != null;
    }

    private boolean isSwingingAtVex(IronGolem candidate) {
        if (candidate.getAttackAnimationTick() <= 0) {
            return false;
        }
        Vec3 toVex = vex.getBoundingBox().getCenter().subtract(candidate.getEyePosition());
        return toVex.lengthSqr() > 1.0E-4 && candidate.getHeadLookAngle().dot(toVex.normalize()) > FACING;
    }

    /** One dart per use. */
    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        if (golem == null) {
            return;
        }
        Vec3 look = golem.getHeadLookAngle();
        Vec3 side = new Vec3(-look.z, 0.0, look.x);
        if (side.lengthSqr() < 1.0E-4) {
            side = new Vec3(1.0, 0.0, 0.0);
        }
        side = side.normalize().scale(vex.getRandom().nextBoolean() ? DART : -DART);
        Vec3 spot = vex.position().add(side);
        vex.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, SPEED);
        readyAt = vex.tickCount + SWING_TICKS;
    }

    @Override
    public void stop() {
        golem = null;
    }
}
```

`src/main/java/io/github/willrieske/spellcraft/evoking/RepairGolemGoal.java`:

```java
package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.spell.Allies;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Bound Vex repairs a hurt ally golem (Evoking II spec §3.7): on each scan ({@code BoundVex.scanDue}), when the vex is
 * idle (no target) and its summoner is here, the nearest iron golem whose feet are in the guard area, that is an ally
 * (so: not mad), under full health and not claimed by another living Bound Vex is taken. The vex flies to within
 * {@link #REACH} of the golem's chest, conjures an iron ingot there, and every {@link #TAP_INTERVAL} ticks swings, plays
 * the repair sound and heals {@link #HEAL} (vanilla's ingot amount). When the golem is full, or the repair stops for
 * any reason (a target, the summoner gone, the golem out of the area or mad, flee or dodge taking over), the ingot goes
 * and the item the vex held comes back. The held item lives in this goal until Part 3's stash: a vex unloaded, saved
 * and reloaded, or carried through a portal while repairing keeps the ingot (Divergence 8, noted for the playtest).
 * Repair outranks follow, so a summoner who walks off waits until the golem is full (at most about three seconds).
 */
final class RepairGolemGoal extends Goal {
    /** Health per tap: vanilla's iron-ingot repair. */
    static final float HEAL = 25.0F;

    /** Ticks between taps. */
    static final int TAP_INTERVAL = 30;

    /** How near the golem's chest the vex taps from. */
    static final double REACH = 1.5;

    private static final double CHEST_HEIGHT = 1.4;
    private static final double SPEED = 1.0;

    /**
     * Golems under repair: golem UUID to the claiming vex, so two vexes never share one (spec §3.7). A claim whose vex
     * is dead or removed is free, because a vex that dies, despawns or is unloaded never runs {@code stop()}. Cleared
     * when the server stops.
     */
    private static final Map<UUID, BoundVex> CLAIMED = new HashMap<>();

    private final BoundVex vex;

    @Nullable
    private IronGolem golem;

    private ItemStack held = ItemStack.EMPTY;
    private boolean conjured;
    private int ticksSinceTap;

    RepairGolemGoal(BoundVex vex) {
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    /** Forgets every claim (single-player loads one server per world in the same JVM). */
    static void clearClaims() {
        CLAIMED.clear();
    }

    @Override
    public boolean canUse() {
        if (!vex.scanDue(BoundVex.SCAN_INTERVAL) || vex.getTarget() != null) {
            return false;
        }
        ServerPlayer owner = vex.summoner().orElse(null);
        if (owner == null) {
            return false;
        }
        golem = vex.level()
                .getEntitiesOfClass(
                        IronGolem.class,
                        vex.guardArea(),
                        candidate -> vex.inGuardArea(candidate)
                                && needsRepair(owner, candidate)
                                && !claimedByAnother(candidate))
                .stream()
                .min(Comparator.comparingDouble(vex::distanceToSqr))
                .orElse(null);
        return golem != null;
    }

    private static boolean needsRepair(ServerPlayer owner, IronGolem candidate) {
        return candidate.isAlive()
                && candidate.getHealth() < candidate.getMaxHealth()
                && Allies.isAllyOf(owner, candidate);
    }

    /** Whether a living Bound Vex other than this one holds the claim on {@code candidate}. */
    private boolean claimedByAnother(IronGolem candidate) {
        BoundVex holder = CLAIMED.get(candidate.getUUID());
        return holder != null
                && holder.isAlive()
                && !holder.isRemoved()
                && !holder.getUUID().equals(vex.getUUID());
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        if (golem == null) {
            return;
        }
        CLAIMED.put(golem.getUUID(), vex);
        conjured = false;
        ticksSinceTap = 0;
    }

    @Override
    public boolean canContinueToUse() {
        if (golem == null || vex.getTarget() != null) {
            return false;
        }
        ServerPlayer owner = vex.summoner().orElse(null);
        return owner != null && needsRepair(owner, golem) && vex.inGuardArea(golem);
    }

    /** Flies to the chest; there, conjures the ingot once and taps every {@link #TAP_INTERVAL} ticks. */
    @Override
    public void tick() {
        if (golem == null) {
            return;
        }
        Vec3 chest = golem.position().add(0.0, CHEST_HEIGHT, 0.0);
        if (vex.position().distanceTo(chest) > REACH) {
            vex.getMoveControl().setWantedPosition(chest.x, chest.y, chest.z, SPEED);
            return;
        }
        if (!conjured) {
            held = vex.getMainHandItem().copy();
            vex.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT));
            conjured = true;
        }
        if (ticksSinceTap == 0) {
            tap(golem);
        }
        ticksSinceTap = (ticksSinceTap + 1) % TAP_INTERVAL;
    }

    /** One repair tap: the swing, vanilla's repair sound at vanilla's pitch, the heal. */
    private void tap(IronGolem target) {
        vex.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
        float pitch = 1.0F + (vex.getRandom().nextFloat() - vex.getRandom().nextFloat()) * 0.2F;
        vex.playSound(SoundEvents.IRON_GOLEM_REPAIR, 1.0F, pitch);
        target.heal(HEAL);
    }

    @Override
    public void stop() {
        if (golem != null) {
            UUID self = vex.getUUID();
            CLAIMED.computeIfPresent(golem.getUUID(), (id, holder) -> holder.getUUID().equals(self) ? null : holder);
        }
        if (conjured) {
            vex.setItemSlot(EquipmentSlot.MAINHAND, held);
        }
        held = ItemStack.EMPTY;
        conjured = false;
        golem = null;
    }
}
```

In `BoundVex.registerGoals`, add the two quick-reaction goals before the `FloatGoal` line, and the job before the `ChargeGoal` line, so source order equals priority order:

```java
        goalSelector.addGoal(0, new FleeCreeperGoal(this));
        goalSelector.addGoal(0, new DodgeGolemGoal(this));
```

```java
        goalSelector.addGoal(2, new RepairGolemGoal(this));
```

The goal selector then reads 0 flee, 0 dodge, 0 float, 2 repair, 3 charge, 4 follow, 5 wander, 9 and 10 look. In `BoundVexEvents.onServerStopped`, add `RepairGolemGoal.clearClaims();`.

- [x] **Step 2: The GameTests**

In `GameTestSupport`, after `drainActionBar`, add (imports `net.minecraft.network.protocol.game.ClientboundSoundPacket`, `net.minecraft.sounds.SoundEvent`):

```java
    /**
     * Empties the mock player's outbound packet buffer, as {@link #drainActionBar} does, and returns the sounds it was
     * sent ({@code ClientboundSoundPacket}s: what {@code Entity.playSound} on the server broadcasts to players within
     * 16 blocks). Draining drops every other packet too.
     */
    public static List<SoundEvent> drainSounds(ServerPlayer player) {
        List<SoundEvent> sounds = new ArrayList<>();
        EmbeddedChannel channel =
                (EmbeddedChannel) player.connection.getConnection().channel();
        player.connection.getConnection().flushChannel();
        Object packet;
        while ((packet = channel.readOutbound()) != null) {
            if (packet instanceof ClientboundSoundPacket sound) {
                sounds.add(sound.getSound().value());
            }
        }
        return sounds;
    }
```

In `BoundVexGameTests.registerAll`, add:

```java
        FangGameTests.addPadded(SHARED, "bound_vex_flees_a_hissing_creeper", BoundVexGameTests::fleesAHissingCreeper);
        FangGameTests.addPadded(
                SHARED, "bound_vex_flee_ignores_a_calm_or_far_creeper", BoundVexGameTests::fleeIgnoresACalmOrFarCreeper);
        FangGameTests.addPadded(SHARED, "bound_vex_dodges_a_golems_swing", BoundVexGameTests::dodgesAGolemsSwing);
        FangGameTests.addPadded(
                SHARED, "bound_vex_dodge_ignores_a_swing_away_or_far", BoundVexGameTests::dodgeIgnoresASwingAwayOrFar);
        FangGameTests.addPadded(SHARED, "bound_vex_repairs_a_hurt_ally_golem", BoundVexGameTests::repairsAHurtAllyGolem);
        FangGameTests.addPadded(SHARED, "bound_vex_repairs_two_golems_apart", BoundVexGameTests::repairsTwoGolemsApart);
        FangGameTests.addPadded(
                SHARED, "bound_vex_repair_claim_frees_when_the_vex_is_gone", BoundVexGameTests::claimFreesWhenTheVexIsGone);
        FangGameTests.addPadded(
                SHARED, "bound_vex_a_fight_interrupts_a_repair", BoundVexGameTests::fightInterruptsARepair);
        FangGameTests.addPadded(
                SHARED, "bound_vex_no_repair_for_a_mad_or_full_golem", BoundVexGameTests::noRepairForAMadOrFullGolem);
```

Imports to add: `io.github.willrieske.spellcraft.evoking.FollowSummonerGoal` (if Task 4 did not already), `net.minecraft.nbt.CompoundTag`, `net.minecraft.sounds.SoundEvents`, `net.minecraft.util.ProblemReporter`, `net.minecraft.world.entity.animal.golem.IronGolem`, `net.minecraft.world.level.block.Blocks`, `net.minecraft.world.level.storage.TagValueInput` (`Creeper`, `EntityTypes`, `Items`, `List`, `Vec3` and `Husk` are already imported by Tasks 3 and 5). Then the constants, helpers and tests:

```java
    /** The flee goal's safe distance (spec §3.5); the goal is package-private in {@code evoking}. */
    private static final double SAFE_DISTANCE = 8.0;

    /** The flee goal's notice range ({@code FleeCreeperGoal.NOTICE_RANGE}, spec §3.5). */
    private static final double FLEE_NOTICE_RANGE = 8.0;

    /** Where the repair tests stand the golem: three blocks east of the box's middle, in the padding. */
    private static final BlockPos GOLEM_SPOT = FEET.east(3);

    /**
     * A creeper with no AI whose fuse is so long that it hisses without exploding once {@code setSwellDir(1)} is
     * called: the fuse is the {@code Fuse} short tag, written over the creeper's own save and loaded back.
     */
    private static Creeper patientCreeper(GameTestHelper helper, BlockPos relative) {
        Creeper creeper = helper.spawn(EntityTypes.CREEPER, relative);
        creeper.setNoAi(true);
        CompoundTag tag = GameTestSupport.saved(creeper);
        tag.putShort("Fuse", Short.MAX_VALUE);
        creeper.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
        whenDone(helper, creeper::discard);
        return creeper;
    }

    /**
     * A golem with no AI at {@code relative} and {@code health}, standing on a stone block (the padded box is air), and
     * discarded when the test ends (it stands in the padding, outside the structure's own cleanup).
     */
    private static IronGolem golemOnStone(GameTestHelper helper, BlockPos relative, float health) {
        helper.setBlock(relative.below(), Blocks.STONE);
        IronGolem golem = GolemGameTests.golem(helper, relative, health);
        whenDone(helper, golem::discard);
        return golem;
    }

    /** {@link #boundVex} without its follow goal, so it stays where a repair test puts it until a job moves it. */
    private static BoundVex unleashed(GameTestHelper helper, BlockPos relative, ServerPlayer summoner) {
        BoundVex vex = boundVex(helper, relative, summoner);
        vex.goalSelector.removeAllGoals(goal -> goal instanceof FollowSummonerGoal);
        return vex;
    }

    /** Turns {@code golem}'s body and head to {@code yaw} (−90 east, 90 west, 0 south, 180 north). */
    private static void face(IronGolem golem, float yaw) {
        golem.setYRot(yaw);
        golem.setYHeadRot(yaw);
        golem.yBodyRot = yaw;
    }

    /**
     * When a creeper two blocks from a following Bound Vex starts swelling, should fly to eight blocks from it and hold;
     * when the creeper calms down, should come back to the summoner.
     *
     * <p>Method under test: {@code FleeCreeperGoal} (package-private), through the goal selector
     */
    static void fleesAHissingCreeper(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        Creeper creeper = patientCreeper(helper, FEET.north(4));
        BoundVex vex = boundVex(helper, FEET.north(2), player);

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> creeper.setSwellDir(1))
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.distanceTo(creeper) >= SAFE_DISTANCE, "eight blocks from the creeper, got " + vex.distanceTo(creeper)))
                .thenExecuteFor(20, () -> helper.assertTrue(vex.distanceTo(creeper) >= SAFE_DISTANCE - 1.0, "holds there"))
                .thenExecute(() -> creeper.setSwellDir(-1))
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.distanceTo(player) <= FollowSummonerGoal.ORBIT_MAX + 1.0,
                        "back with the summoner once the creeper calms"))
                .thenSucceed();
    }

    /**
     * When a calm creeper hovers two blocks from a Bound Vex and a swelling one about 8.5 blocks away (outside the
     * eight-block range but inside the cube a box query covers), should stay with its summoner for forty ticks; once
     * the near creeper swells, should flee it (the control).
     *
     * <p>Method under test: {@code FleeCreeperGoal} (package-private): the swelling and range filters
     */
    static void fleeIgnoresACalmOrFarCreeper(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = boundVex(helper, FEET.north(2), player);
        Creeper near = patientCreeper(helper, FEET.north(4));
        Creeper far = patientCreeper(helper, FEET.offset(6, 0, 4)); // (6, 0, 6) from the vex: about 8.49 blocks
        far.setSwellDir(1);
        helper.assertTrue(vex.distanceTo(far) > FLEE_NOTICE_RANGE, "setup: the swelling creeper is out of range");

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(TARGET_WINDOW, () -> helper.assertTrue(
                        vex.distanceTo(player) <= FollowSummonerGoal.ORBIT_MAX,
                        "stays with the summoner, got " + vex.distanceTo(player)))
                .thenExecute(() -> near.setSwellDir(1))
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.distanceTo(near) >= SAFE_DISTANCE, "flees the near creeper once it swells"))
                .thenSucceed();
    }

    /**
     * When an iron golem two blocks from a Bound Vex swings with its head facing it, should dart two to four and a half
     * blocks sideways (perpendicular to the golem's look; the dart is three) within twelve ticks and not away from it.
     * The summoner stands two blocks east of the vex, so the follow goal is idle on either side of the dart (about 3.6
     * blocks away).
     *
     * <p>Method under test: {@code DodgeGolemGoal} (package-private), through the goal selector
     */
    static void dodgesAGolemsSwing(GameTestHelper helper) {
        // Arrange: the golem looks east; the vex hovers two blocks east of it
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        MockPlayers.moveTo(helper, player, new Vec3(8.5, 1, 1.5), NORTH);
        IronGolem golem = golemOnStone(helper, GOLEM_SPOT, 100.0F);
        face(golem, -90.0F);
        BoundVex vex = boundVex(helper, FEET.east(5), player);
        Vec3 before = vex.position();

        // Act
        golem.handleEntityEvent((byte) 4);

        // Assert
        helper.assertTrue(golem.getAttackAnimationTick() > 0, "setup: the golem is mid-swing");
        helper.runAfterDelay(12, () -> {
            Vec3 moved = vex.position().subtract(before);
            helper.assertTrue(
                    Math.abs(moved.z) >= 2.0 && Math.abs(moved.z) <= 4.5,
                    "darted about three blocks sideways (north or south), got dz " + moved.z);
            helper.assertTrue(Math.abs(moved.x) < 1.5, "not away from the golem, got dx " + moved.x);
            helper.succeed();
        });
    }

    /**
     * When one golem two blocks from a Bound Vex swings with its head turned away, and another five blocks away swings
     * facing it, should not dart for ten ticks; once the near golem turns to face it and swings, should dart (the
     * control).
     *
     * <p>Method under test: {@code DodgeGolemGoal} (package-private): the facing and range filters
     */
    static void dodgeIgnoresASwingAwayOrFar(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        MockPlayers.moveTo(helper, player, new Vec3(8.5, 1, 1.5), NORTH);
        IronGolem away = golemOnStone(helper, GOLEM_SPOT, 100.0F);
        face(away, 90.0F); // looks west, the vex is east
        IronGolem far = golemOnStone(helper, FEET.east(5).north(5), 100.0F);
        face(far, 0.0F); // looks south, at the vex five blocks off
        BoundVex vex = boundVex(helper, FEET.east(5), player);
        Vec3 before = vex.position();

        // Act
        away.handleEntityEvent((byte) 4);
        far.handleEntityEvent((byte) 4);

        // Assert
        helper.startSequence()
                .thenExecuteFor(10, () -> helper.assertTrue(
                        vex.position().distanceTo(before) < 0.5,
                        "no dart from a swing away or far, moved " + vex.position().distanceTo(before)))
                .thenExecute(() -> {
                    face(away, -90.0F);
                    away.handleEntityEvent((byte) 4);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        Math.abs(vex.position().z - before.z) >= 1.0, "darts once the golem faces it"))
                .thenSucceed();
    }

    /**
     * When an ally golem at half health stands in the guard area of two idle Bound Vexes, both out of reach of its
     * chest, should have exactly one vex fly in and take it (holding an iron ingot while the other keeps its sword),
     * heal it by 25 on the first tap with the repair sound and by 25 more thirty ticks later, and give the vex its
     * sword back once the golem is full.
     *
     * <p>Method under test: {@code RepairGolemGoal} (package-private), through the goal selector
     */
    static void repairsAHurtAllyGolem(GameTestHelper helper) {
        // Arrange: each vex starts about 2.1 blocks from the chest (reach 1.5), so the one that claims must fly
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        MockPlayers.moveTo(helper, player, new Vec3(6.5, 1, 1.5), NORTH);
        IronGolem golem = golemOnStone(helper, GOLEM_SPOT, 50.0F);
        BoundVex first = boundVex(helper, GOLEM_SPOT.north(2).above(2), player);
        BoundVex second = boundVex(helper, GOLEM_SPOT.south(2).above(2), player);
        List<BoundVex> vexes = List.of(first, second);
        GameTestSupport.drainSounds(player);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(golem.getHealth() >= 75.0F, "one tap: 25 healed"))
                .thenExecute(() -> {
                    long ingots = vexes.stream().filter(vex -> vex.getMainHandItem().is(Items.IRON_INGOT)).count();
                    long swords = vexes.stream().filter(vex -> vex.getMainHandItem().is(Items.IRON_SWORD)).count();
                    helper.assertValueEqual(ingots, 1L, "one vex holds the ingot");
                    helper.assertValueEqual(swords, 1L, "the other keeps its sword");
                    helper.assertTrue(golem.getHealth() < 100.0F, "setup: not full after one tap");
                    helper.assertTrue(
                            GameTestSupport.drainSounds(player).contains(SoundEvents.IRON_GOLEM_REPAIR),
                            "the tap played the repair sound");
                })
                .thenWaitUntil(() -> helper.assertValueEqual(golem.getHealth(), 100.0F, "full after the second tap"))
                .thenWaitUntil(() -> helper.assertTrue(
                        vexes.stream().allMatch(vex -> vex.getMainHandItem().is(Items.IRON_SWORD)),
                        "both hold swords again once the golem is full"))
                .thenSucceed();
    }

    /**
     * When two hurt ally golems stand eight blocks apart with one Bound Vex beside each (each golem in only its own
     * vex's guard area), should have both golems repaired. Two vexes spawned one after the other have one odd and one
     * even entity id, so a scan gate that opens only for one parity fails here (docs/api-notes.md §21, the goal clock).
     *
     * <p>Method under test: {@code RepairGolemGoal} (package-private), through {@code BoundVex.scanDue}
     */
    static void repairsTwoGolemsApart(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        IronGolem east = golemOnStone(helper, FEET.east(4), 50.0F);
        IronGolem west = golemOnStone(helper, FEET.west(4), 50.0F);
        unleashed(helper, FEET.east(4).above(2), player);
        unleashed(helper, FEET.west(4).above(2), player);

        // Act & Assert
        helper.succeedWhen(() -> {
            helper.assertTrue(east.getHealth() >= 75.0F, "the east golem is repaired, got " + east.getHealth());
            helper.assertTrue(west.getHealth() >= 75.0F, "the west golem is repaired, got " + west.getHealth());
        });
    }

    /**
     * When the Bound Vex repairing a golem is removed mid-repair (as death, Peaceful or an unload removes it, with no
     * goal {@code stop()}), should leave the golem free: a second vex then repairs it to full.
     *
     * <p>Method under test: {@code RepairGolemGoal}'s claim map (package-private)
     */
    static void claimFreesWhenTheVexIsGone(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        IronGolem golem = golemOnStone(helper, GOLEM_SPOT, 50.0F);
        BoundVex first = unleashed(helper, GOLEM_SPOT.above(2), player);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(golem.getHealth() >= 75.0F, "setup: the first vex tapped once"))
                .thenExecute(() -> {
                    helper.assertTrue(golem.getHealth() < 100.0F, "setup: not full yet");
                    first.discard();
                    unleashed(helper, GOLEM_SPOT.above(2), player);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(golem.getHealth(), 100.0F, "the second vex finished it"))
                .thenSucceed();
    }

    /**
     * When a Bound Vex repairing a golem takes a target after the first tap, should stop: its sword comes back and the
     * golem is free, so a second vex (with no target goals) repairs it to full.
     *
     * <p>Method under test: {@code RepairGolemGoal.canContinueToUse} and {@code stop} (package-private), Divergence 7
     */
    static void fightInterruptsARepair(GameTestHelper helper) {
        // Arrange: target goals off, so only the test decides who has a target
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        IronGolem golem = golemOnStone(helper, GOLEM_SPOT, 25.0F);
        BoundVex first = unleashed(helper, GOLEM_SPOT.above(2), player);
        first.targetSelector.removeAllGoals(goal -> true);
        Husk husk = husk(helper, GOLEM_SPOT.west(2));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(first.getMainHandItem().is(Items.IRON_INGOT), "setup: repairing"))
                .thenExecute(() -> first.setTarget(husk))
                .thenWaitUntil(() -> helper.assertTrue(first.getMainHandItem().is(Items.IRON_SWORD), "the sword is back"))
                .thenExecute(() -> {
                    float now = golem.getHealth();
                    helper.assertTrue(now < 100.0F, "setup: stopped before full, at " + now);
                    BoundVex second = unleashed(helper, GOLEM_SPOT.above(2), player);
                    second.targetSelector.removeAllGoals(goal -> true);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(golem.getHealth(), 100.0F, "the second vex finished it"))
                .thenSucceed();
    }

    /**
     * When a golem mad at the summoner stands at half health and a full-health ally golem stands beside it, both in a
     * Bound Vex's guard area, should repair neither for sixty ticks (the vex keeps its sword); once the mad golem calms
     * down, should repair it (the control).
     *
     * <p>Method under test: {@code RepairGolemGoal} (package-private): the ally and health filters
     */
    static void noRepairForAMadOrFullGolem(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        IronGolem mad = golemOnStone(helper, GOLEM_SPOT, 50.0F);
        GolemGameTests.anger(mad, player);
        IronGolem full = golemOnStone(helper, GOLEM_SPOT.south(2), 100.0F);
        BoundVex vex = unleashed(helper, GOLEM_SPOT.south(1).above(2), player);
        vex.targetSelector.removeAllGoals(goal -> true);

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(60, () -> {
                    helper.assertValueEqual(mad.getHealth(), 50.0F, "a mad golem is not repaired");
                    helper.assertValueEqual(full.getHealth(), 100.0F, "setup: the full golem stays full");
                    helper.assertTrue(vex.getMainHandItem().is(Items.IRON_SWORD), "no ingot for either golem");
                })
                .thenExecute(() -> {
                    mad.setTarget(null);
                    mad.stopBeingAngry();
                })
                .thenWaitUntil(() -> helper.assertTrue(mad.getHealth() >= 75.0F, "repaired once it calms down"))
                .thenSucceed();
    }
```

Notes for the implementer:

- `helper.assertValueEqual(golem.getHealth(), 100.0F, …)` compares boxed floats with `equals`; `heal` clamps to the max, so the value is exactly `100.0F`.
- `GolemGameTests.anger` sets the golem's target and persistent anger and starts the anger timer (20–39 s), so the golem stays mad through the sixty-tick window; `GolemMercy` cancels golem targeting of summons, not of the player.
- In `fightInterruptsARepair`, the golem starts at 25 health so it is still short of full after the first vex's taps when the target arrives (the first tap lands within a few ticks of the claim, the next 30 ticks later, and the target is set as soon as the ingot is seen).
- The repair tests stand the player within 16 blocks of the vexes, so `drainSounds` sees the broadcast sound.

- [x] **Step 3: Format, build, GameTests**

Run: `./gradlew spotlessApply --console=plain`, then `./gradlew build --console=plain > build/build.log 2>&1; echo "exit=$?"; tail -40 build/build.log`. Expected: `exit=0`.
Run in the background: `./gradlew runGameTestServer --console=plain > build/runGameTestServer.log 2>&1; echo "exit=$?"; grep -n "FAILED\|required tests" build/runGameTestServer.log | tail -5`. Expected: `exit=0`, `All 206 required tests passed` (Task 5's 197 + the nine registered above).

- [x] **Step 4: Commit**

```bash
git add src/main/java/io/github/willrieske/spellcraft/evoking/FleeCreeperGoal.java src/main/java/io/github/willrieske/spellcraft/evoking/DodgeGolemGoal.java src/main/java/io/github/willrieske/spellcraft/evoking/RepairGolemGoal.java src/main/java/io/github/willrieske/spellcraft/evoking/BoundVex.java src/main/java/io/github/willrieske/spellcraft/evoking/BoundVexEvents.java src/main/java/io/github/willrieske/spellcraft/gametest/GameTestSupport.java src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexGameTests.java
```

```bash
git commit -m "feat(evoking): Bound Vexes flee a hissing creeper, dodge a golem's swing and repair hurt golems"
```

Then `git log -1 --oneline` shows the commit.

> **As built (`0aeda6f`):** diverged — the tap pitch uses two `nextFloat()` locals (Error Prone `IdentityBinaryExpression` rejects `nextFloat() - nextFloat()`); `GOLEM_SPOT` is `FEET.east(4)` (at `east(3)` the golem's eyes sat in the barrier shell, its wall damage made a full golem hurt and the repair goal pulled vexes off their darts), with the dodge tests moved to match; after the task review: `RepairGolemGoal.canUse` prunes dead claim holders, `DodgeGolemGoal` also filters by distance (a 3-block sphere, not the cube; new test `dodgeIgnoresADiagonalSwingBeyondThreeBlocks`, red run shown), `GameTestSupport.drainOutbound` is shared, the flee Javadoc explains its one-block margin, and the four follow-only tests use a `followOnly` vex without target goals plus a state dump on `followsAgainAfterStay`'s failure (it flaked twice in about ten runs before, cause unproven, then passed 35 runs). 215 GameTests (214 + 1). Reason: implementer and task review.

---

### Task 7: Docs, playtest items, mutation testing

**Files:**

- Modify: `README.md:15-24` (the Evoking bullet)
- Modify: `docs/playtest-checklist.md:135,146,164` (three items of the Evoking sections) and a new "Evoking II (Part 2)" section before "## Real profile"
- Modify: `docs/mutation-testing.md` (one new row in the PIT table)
- Possibly modify: a `src/test/.../spell/rules/*Test.java` file, if a PIT survivor needs a new test (committed separately)
- Not modified: `src/main/templates/META-INF/neoforge.mods.toml` (no new spell), `CLAUDE.md`, `gradle.properties` (no version bump; §12 bumps once after Part 7)

**Interfaces:**

- Consumes: everything above.
- Produces: the docs Will reads.

- [x] **Step 1: README**

Replace the Strength of Evoking bullet (lines 15–24) with:

```markdown
- <img src="src/main/resources/assets/spellcraft/textures/item/evoking_scroll.png" alt="" width="24" height="24"> **Strength of Evoking**: an evoker's powers, with no time limit. Press **Y** to raise your arms
  and, a second later, make evoker fangs rise under every enemy in the 13×13 square ahead of you (a
  big mob that covers several blocks gets one fang under each block; enemies up to 14 blocks below
  you and 6 above count). An enemy that steps off its block before the bite gets one more fang
  under its new spot. Press **V** to call three **Bound Vexes** to your side. They stay until they
  die (Peaceful removes them), and you can have twelve at once: with ten or eleven, V calls just
  enough to make twelve, and at twelve V only says "You have too many vexes". They fly around you,
  pop up beside you when you get more than 12 blocks away, and follow you through portals when they
  are within 16 blocks of you and not told to stay. They fight whatever attacks you, your pets or
  them, whatever you hit, whatever your pets are fighting, and any hostile mob that comes within
  four blocks of them (not creepers, unless one attacks first); your tamed wolves join their fights.
  Click a vex with an empty hand to make it stay where it is; click again to make it follow. A vex
  flies away from a hissing creeper, darts out of an iron golem's swing, and patches up a hurt iron
  golem with an iron ingot. Fangs spare you and your allies (your team, your pets, your own vexes,
  villagers, wandering traders, and iron golems that are not mad at you) and hurt everything else;
  vexes spare them too and fight only what the list above says. You, your pets, your fangs and your
  vexes cannot take an iron golem below 25 health; when it gets that low, it calms down and stops
  being mad at you. The keys rebind in Controls. Sneak + scroll or milk turns it off.
```

- [x] **Step 2: Playtest checklist, the Evoking sections**

In `docs/playtest-checklist.md`, replace line 135 (the V item) with:

```markdown
- [ ] V: the "prepare summon" sound, arms up for five seconds, pale particles at the hands; after a second three Bound Vexes appear 2–3 blocks from you, one block up. They look like vexes, fly around you and ignore you. Tame a wolf: they ignore it. They do not die on their own (watch one for three minutes).
```

Replace line 146 (the pvp item) with:

```markdown
- [ ] With `/gamerule pvp false`, Y with only another player in the square gives `No targets in range`, and V's vexes never attack another player or that player's vexes. With pvp on, they attack another player (or that player's vexes) only after it hits you, your pets or your vexes, or after you hit it.
```

Replace line 164 (the mad-golem item of Evoking II Part 1) with:

```markdown
- [ ] Make a golem mad at you, then call your vexes next to it: once it hits you (or you hit it), the vexes fight it; it ends at 25 health and calms down.
```

- [x] **Step 3: Playtest checklist, the new section**

Insert before "## Real profile", with one blank line between the section's last item and that heading:

```markdown
## Evoking II (Part 2)

Survival world, open ground, a village nearby if you can, with Strength of Evoking on. The vexes in the Strength of Evoking items above are Bound Vexes now; these items add to them. Where a golem or a creeper can hurt you, give yourself Resistance (`/effect give @s resistance 600 4`).

- [ ] Press V four times (wait for each cooldown): 3, 6, 9, 12 vexes (`/execute if entity @e[type=spellcraft:bound_vex]` prints the count). A fifth press: "You have too many vexes" above the hotbar, arms stay down, no sound; `/kill @e[type=spellcraft:bound_vex,limit=1]` and V works again at once (no cooldown was spent on the refusal). `/kill @e[type=spellcraft:bound_vex,limit=2]` from twelve: V summons two. From eleven: one.
- [ ] Walk and sprint around: the vexes keep up and hover 2–4 blocks from you, and settle there without darting through you. `/tp @s ~15 ~ ~`: they pop up beside you within a second.
- [ ] Right-click a vex with an empty hand (not sneaking): the vex's sound and your arm swings; it stops following and drifts around that spot. Walk 20 blocks away: it stays. Right-click it again: it follows (it may finish a short drift first). Right-click while holding something, or while sneaking: nothing. A friend's click (LAN): nothing.
- [ ] Hold an ender pearl (or a shield) in your off hand and an empty main hand, then right-click your vex: stay toggles, and no pearl is thrown (no shield is raised).
- [ ] Walk through a nether portal with vexes near you: they arrive beside you in the Nether, still yours (a click still toggles stay). A staying vex stays behind in the Overworld, and so does a following vex more than 16 blocks from you when you step in.
- [ ] Log out and back in: the vexes are where you left them, still yours. Quit to title and reopen the world: same.
- [ ] Die (or, on LAN, have the friend whose vexes are out log out): the vexes hover where they are and fight nothing until you respawn near them (or the friend returns).
- [ ] A zombie attacks you: the vexes go for it. Hit a cow yourself: they go for the cow. A creeper walks up to you: ignored until it hurts you or a vex, then they attack it. A zombie attacks your wolf or a village golem: the vexes go for it.
- [ ] Your tamed wolf (standing, no target) joins when a vex picks a target; a sitting wolf stays put. A creeper that hurt a vex: the vexes attack it, your wolf does not.
- [ ] In the Nether, near zombified piglins: the vexes attack them (the game counts them as hostile mobs), which angers the whole group. Note how that plays; it is the spec's rule, not a bug.
- [ ] `/summon creeper ~5 ~ ~ {NoAI:1b,ignited:1b,Fuse:200s}` with your vexes near you: they fly straight away from it to about eight blocks and hold there; when it explodes ten seconds later they come back to you. Then let a normal creeper walk up and start hissing: the vexes back off; run more than seven blocks away so it stops hissing: they come back.
- [ ] Make a village golem mad at you and stand so a vex hovers between you and the golem: when the golem swings at you, the vex darts sideways out of the swing. The vexes fighting it stop at 25 health and it calms down (Part 1).
- [ ] Hurt a calm golem without angering it, standing a few blocks from it with your vexes (`/damage @e[type=iron_golem,limit=1,sort=nearest] 40`): one vex flies to its chest, an iron ingot appears in its hand there, and every 1.5 seconds you hear the repair sound (an anvil-like clank; the vex's arm may not move, the vex model has no swing) until the golem is full; then it holds its sword again. A second vex nearby does not join the repair. Walk off mid-repair: the vex finishes (at most about three seconds) before it follows.
- [ ] `/summon spellcraft:bound_vex`: a vex that hovers and fights nothing, even with a zombie next to it; its name reads "Bound Vex" (`/data get entity @e[type=spellcraft:bound_vex,limit=1,sort=nearest] id` shows `spellcraft:bound_vex`; `/kill` it and the death message names it).
- [ ] On Peaceful: existing Bound Vexes vanish (vanilla's monster rule); V says "Vexes cannot be summoned on Peaceful". Back on Normal, V works.
- [ ] Optional (LAN, two players with vexes, `/gamerule pvp false`): your vexes and theirs leave each other alone; with pvp on, they fight only after one side hits the other.
- [ ] Known until Part 3: a vex that is unloaded (you leave the area), saved and reloaded, or carried through a portal while repairing a golem keeps the ingot in its hand instead of its sword.
- [ ] Known and accepted: two vexes can land on the same spot after a portal; a vex only follows or rides a portal while its chunk is loaded, so after an elytra flight, a pearl or a `/tp` far away, vexes left in unloaded chunks stay there until you come back.
- [ ] If this world still has round-one vexes (summoned before this update): those vanilla vexes may attack you for their remaining seconds, then die on their own.
```

- [x] **Step 4: Mutation testing**

Run: `./gradlew pitest --console=plain > build/pitest.log 2>&1; echo "exit=$?"; grep -n "Generated\|mutations\|Line Coverage\|Mutation score\|killed" build/pitest.log | tail -10`
Expected: `exit=0`. Read the totals and the survivors in `VexCap` and `GuardArea` from `build/reports/pitest/index.html` (or the log summary); Task 2 already ran PIT on them, so expect none. If a `VexCap` or `GuardArea` mutant survives and is not equivalent, add the unit test that kills it (same style as Task 2), run `./gradlew spotlessApply --console=plain`, commit it on its own as `test(rules): kill the <Class> <mutator> survivor` (stage, then commit, as two calls), then re-run `pitest` and record the final numbers. `VexRing`'s `lifeTicks` mutants are gone with the method.

Add a row to the PIT table in `docs/mutation-testing.md` (the date is the run's date):

```markdown
| <run date> (Evoking II Part 2) | <score>% (<killed>/<total> killed; from 98%, 247/251, on 2026-11-08) | <line>% (<covered>/<lines>) | <"None new in `spell.rules` (every `VexCap` and `GuardArea` mutant was killed; `VexRing` lost its `lifeTicks` mutants with the life limit)" or each survivor with its file:line, mutator and why it is equivalent>. The same four `UpsideDownRollState` non-killed mutants as above, unchanged, all justified in the first row. |
```

- [x] **Step 5: Lint the docs**

Run: `pre-commit run markdownlint-cli2 --files README.md docs/playtest-checklist.md docs/mutation-testing.md`
Expected: `Passed`.

- [x] **Step 6: Closing sweep**

Run: `grep -rn "30–120\|30-120\|limited life\|life_ticks\|lifeTicks\|five blocks\|AGGRO_RANGE\|VexSummons.isPrey\|install(\|onJoin\|EntityJoinLevelEvent\|vanilla vex\|how long they live" README.md docs/api-notes.md docs/playtest-checklist.md docs/mutation-testing.md src/main/java src/test/java`
Then read every `vex` line of `docs/playtest-checklist.md` (`grep -n -i "vex" docs/playtest-checklist.md`: at least lines 125, 135, 136, 139, 145, 146, 150, 162, 164, 166 and the new section).
Read every hit and fix any statement Tasks 3–6 made false (a vex that dies after 30–120 s, a five-block aggro range, a goal installed on join, a vex that attacks a player or a mad golem unprovoked). Hits in api-notes §19–§21 (vanilla's evoker and vex, `EvokerFangs.lifeTicks`) and in `docs/mutation-testing.md`'s older rows are history, not statements about the mod: leave them. Spec files under `docs/superpowers` are out of scope for this task.

- [x] **Step 7: Commit**

```bash
git add README.md docs/playtest-checklist.md docs/mutation-testing.md
```

```bash
git commit -m "docs: describe Evoking II Part 2 (Bound Vexes: no timer, cap, follow, stay, fights, repair)"
```

Then `git log -1 --oneline` shows the commit. If the sweep changed a Java Javadoc, stage and commit that file first as `docs(java): fix Javadoc made stale by Evoking II Part 2`.

> **As built (`4754fb1`):** diverged — the docs match the plan; an extra test commit (`e336697`) adds two `CsvSource` rows that kill the two `FangFootprints.requireBox` boundary survivors (Part 1 code) the pitest run found, noted in the mutation row (264/268 killed, 99%; line coverage 270/284); reason: implementer, accepted by the task review.

---

## Self-review (done while writing; redone after the plan review)

- **Spec coverage.** §3.1 mob, registration, no egg/spawn/loot, no timer, summoner, synced `STAYING`, renderer, `VexSummons.summon` → Task 3 (Divergences 1–3, 9, 11–13); `MobCategory.MONSTER` → Task 3. §3.2 cap → Task 2 (`VexCap`), Task 3 (`countOwnedBy`, `CAPPED`, the notice, the top-up; `capRefusesAtTwelve`, `capTopsUpToTwelve` from ten and from eleven, `capIsPerSummoner`, `capCountsEveryLevel`). §3.3 goals and priorities → Tasks 3–6 (`registerGoals`; `threeTargetGoals`; Divergences 4, 7, 14); follow/orbit/teleport → Task 4 (`followFliesIntoTheOrbit`, `followTeleportsFromThirteen`, `followIdlesWithoutASummoner`); stay toggle and wander → Tasks 3–4 (`stayToggle`, `stayingHoldsItsPost`, `followsAgainAfterStay`); portals → Task 4 (`portalFollow`, `sameDimensionTeleportBringsNothing`; Divergence 5). §3.4 `isPrey` → Task 3 (`preyRule`, `a6PvpOff` with another player's and an offline player's summons; Divergence 15), the guard box → Tasks 2–3 (`GuardArea`, `guardAreaEdges`), the three target goals, their clock and `keepsTarget` → Task 5 (every `bound_vex_*` targeting test: `attacksAHostile`, `ignoresAPigButNotAHusk`, `ignoresACreeperUntilProvoked`, `targetsWhatHurtTheSummoner`, `targetsWhatTheSummonerHit`, `targetsWhatHurtAnAlly`, `joinsAnAllysFight`, `dropsATargetOutOfReach`, `dropsANewAlly`, `heldByThePatrolGoal`, `ignoresAStaleMemory`, `ownAttackerComesFirst`, `creativeAttackerDoesNotBlock`, `ignoresAnAllyOutsideTheArea`; Divergences 6, 16), wolves join → Task 5 (`callsTheSummonersWolves`, `wolfCallSkipsTheWrongWolves`, `wolfCallOnlyOnANewTarget`, `wolfCallSkipsACreeper`). §3.5 → Task 6 (`fleesAHissingCreeper`, `fleeIgnoresACalmOrFarCreeper`). §3.6 golems never target Bound Vexes → Part 1's `GolemMercy` (the mark); dodge → Task 6 (`dodgesAGolemsSwing`, `dodgeIgnoresASwingAwayOrFar`). §3.7 → Task 6 (`repairsAHurtAllyGolem` with the sound, `repairsTwoGolemsApart`, `claimFreesWhenTheVexIsGone`, `fightInterruptsARepair`, `noRepairForAMadOrFullGolem`; Divergences 7, 8). §10: no summoner → `idlesWithoutASummoner`, `noSummonerNoTarget`, `followIdlesWithoutASummoner`; dead summoner → `idlesWithADeadSummoner`; summoner in another dimension → `idlesWithTheSummonerElsewhere` and the far vex of `portalFollow`; cap across dimensions → `capCountsEveryLevel`; Peaceful removal → `peacefulNotice` (`checkDespawn`); `/summon` → `idlesWithoutASummoner`. §11 pure core `VexCap`, `GuardArea` → Task 2 (with PIT); GameTests for Part 2 → all named above; `runData` → Task 3. §12 docs → Tasks 1, 7; `mod_version` untouched; mod description unchanged (no new spell). §13 cap counting, partial summon, mob category, wolves join, dodge always, repair scope → Tasks 2–6; the accepted behaviors (shared ring cells, a wander leg finished before following, vexes that only follow while their chunk ticks, repair outranking follow) → Task 7's playtest items.
- **Placeholders.** None; the mutation row's angle-bracket fields are filled from the run.
- **Type consistency.** `BoundVex.isPrey(ServerLevel, UUID, LivingEntity)` static and `isPrey(LivingEntity)`; `keepsTarget(@Nullable LivingEntity)`, `inGuardArea(Entity)`, `scanDue(int)`, `guardArea()`, `summoner()` → `Optional<ServerPlayer>`, `isSummonedBy(UUID)`, `countOwnedBy(MinecraftServer, UUID)` are defined in Task 3 and used as such in Tasks 4–6; `SCAN_INTERVAL`, `WOLF_CALL_RANGE` and `alliesInGuardArea` are defined in Task 5 and read there and in Task 6; `FollowSummonerGoal`'s public constants are defined in Task 4 and read by the GameTests of Tasks 4 and 6; `VexSummons.summon(ServerLevel, ServerPlayer, int)` matches `EvokingCaster.perform`'s call; `VexCap.toSummon(int)` matches `request`'s call; the GameTest helpers `boundVex`, `parked`, `husk`, `summoner`, `pet`, `pigHere`, `targets`, `runningTargetGoal`, `patientCreeper`, `golemOnStone`, `unleashed`, `face` and `GameTestSupport.drainSounds` are defined before they are used; `GolemGameTests.golem(helper, pos)`, `golem(helper, pos, health)` and `anger(golem, player)` exist on the base branch.
- **Expected GameTest totals.** 164 at the start (Part 1's run log); Task 3: −5 + 11 = 170; Task 4: +7 = 177; Task 5: +20 = 197; Task 6: +9 = 206. Each task names its registrations; if a fix changes them, recount the `registerAll` lines. As built: Task 5 205 (+8 from its review), Task 6 215 (+1 from its review); see the Divergence index.

## Divergence index

<!-- generated by plan_map.py --write-index; do not edit -->

| Task | SHA | Divergence |
|---|---|---|
| 2 | `78efc58` | `reachIsASphere` adds `@Scale(3)` to its yaw and pitch parameters; reason: jqwik's default scale of 2 rejects the three-decimal `@DoubleRange` bounds (6.283, 1.57), so the test as written could not run; intent unchanged (implementer, accepted by the task review). |
| 3 | `66c82f6` | `BoundVex` also imports `net.minecraft.world.level.Level` (the plan's constructor used it unimported); `capCountsEveryLevel` polls with `helper.succeedWhen` (count first, then `CAPPED`) instead of asserting at once, because a vex summoned in the tick that forces its Nether chunk is not in `ServerLevel.getEntities` until the ticket takes effect; production code as planned; reason: implementer, accepted by the task review. |
| 4 | `88373a8` | `sameDimensionTeleportBringsNothing` posts `PlayerChangedDimensionEvent` with `from` = `to` = the test level (not `Level.NETHER`) and asserts the vex is a follower first, so it fails when the `onTravel` same-dimension guard is removed (shown red, then restored); the `TELEPORT_LANDING_MAX` Javadoc gives the worst case for a summoner anywhere in its block (≈ 4.33) and the extra hop; production code as planned; reason: task review (Important finding, controller ruling that the test's intent governs). |
| 5 | `7d4f5d7` | production code as planned; eight more GameTests (205 in all, not 197): an ally and a hostile whose box touches the guard area while their feet are outside, patrol start sight behind a wall and a drop when unseen (`mustSee`), patrol- and ally-goal drops of a husk that becomes an ally, the summoner's victim before an ally's attacker, and a stale summoner's-attacker memory; reason: task review (the planned tests could not fail without the guard-area filter, `mustSee` or the `keepsTarget` overrides; red runs shown for the guard-area and sight tests). |
| 6 | `0aeda6f` | the tap pitch uses two `nextFloat()` locals (Error Prone `IdentityBinaryExpression` rejects `nextFloat() - nextFloat()`); `GOLEM_SPOT` is `FEET.east(4)` (at `east(3)` the golem's eyes sat in the barrier shell, its wall damage made a full golem hurt and the repair goal pulled vexes off their darts), with the dodge tests moved to match; after the task review: `RepairGolemGoal.canUse` prunes dead claim holders, `DodgeGolemGoal` also filters by distance (a 3-block sphere, not the cube; new test `dodgeIgnoresADiagonalSwingBeyondThreeBlocks`, red run shown), `GameTestSupport.drainOutbound` is shared, the flee Javadoc explains its one-block margin, and the four follow-only tests use a `followOnly` vex without target goals plus a state dump on `followsAgainAfterStay`'s failure (it flaked twice in about ten runs before, cause unproven, then passed 35 runs). 215 GameTests (214 + 1). Reason: implementer and task review. |
| 7 | `4754fb1` | the docs match the plan; an extra test commit (`e336697`) adds two `CsvSource` rows that kill the two `FangFootprints.requireBox` boundary survivors (Part 1 code) the pitest run found, noted in the mutation row (264/268 killed, 99%; line coverage 270/284); reason: implementer, accepted by the task review. |

## Resume State

- Last completed task: Task 7: Docs, playtest items, mutation testing
- Commit SHA: 5381347
- Checkboxes: 34/56 ticked (`count_boxes`; the other 22 are sample checklist lines inside Task 7's fenced blocks, so `checkbox_counts` reads 34/34 complete)
- Next task: none (plan complete)
- Known-failing tests / caveats: branch `feat/evoking-ii-part-2` is stacked on `feat/evoking-ii-part-1` (draft PR WillRieske/spellcraft#3); after the plan, `/review-will` (fast, four slices) found 0 Critical, 5 Important and 30 Minor findings; all were fixed in four waves (`e66ffd5`..`ead0acc`: no XP, `BoundVex.huntable` for the patrol and ally goals, the stay-and-follow flake's cause, reach/cleanup/memory tests, the GameTests split into four area classes, docs and spec) except M6's `mob != this` (kept the UUID compare by project rule), and two scoped re-reviews passed; then a deep (adversarial, mutation-run) review found 8 Important and 21 Minor gaps (the End-credits exit left vexes behind; other-dimension targets; spectator summoners; a staying vex's post after a dimension change; untested guards), all fixed or marked defensive in `c23673e`..`5381347`; GameTests now total 249; visual and feel checks are Will's (`docs/playtest-checklist.md`, Evoking II (Part 2))
