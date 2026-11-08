# Evoking II, Part 2 (Bound Vex basics) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the evoker's short-lived vanilla vexes with the **Bound Vex**, the mod's own mob: V summons it with no timer, at most twelve per player; it follows its summoner (teleporting when far, through portals too), can be told to stay, fights what its side fights, calls the summoner's wolves into the fight, flees a hissing creeper, dodges a golem's swing, and repairs hurt ally golems with a conjured iron ingot.

**Architecture:** `evoking.BoundVex extends Vex`, registered as `spellcraft:bound_vex` through a `DeferredRegister.Entities`, rendered by vanilla's `VexRenderer`. Vanilla's vex goals are private inner classes, so the Bound Vex owns copies of the charge and wander goals and adds its own: follow/teleport, flee-creeper, dodge-golem and repair-golem in the goal selector; three target goals (hurt-by-the-summoner's-side, join-an-ally's-fight, nearest-hostile) in the target selector, each dropping a target that stops being prey or leaves reach. Two pure rules carry the numbers: `spell.rules.VexCap` (3 / `12 − count` / 0) and `spell.rules.GuardArea` (the 8×8×7 guard box and the 7.25 reach). `VexSummons` creates Bound Vexes instead of vanilla vexes; `EvokingCaster` counts the summoner's living Bound Vexes before a V cast and answers `CAPPED` with a notice at twelve. A `BoundVexEvents` subscriber brings non-staying vexes through dimension changes (noted on `EntityTravelToDimensionEvent`, moved on `PlayerChangedDimensionEvent`). Nothing is sent clientbound beyond vanilla's entity sync: the one synced flag (`STAYING`) rides the entity data.

**Tech Stack:** Minecraft 26.3, NeoForge 26.3.0.23-beta (ModDevGradle), Java 25, JUnit 5, jqwik, ArchUnit, AssertJ, Error Prone (`-Werror`), Spotless + palantir-java-format, PIT, pre-commit, gitlint, markdownlint-cli2.

**Spec:** [`docs/superpowers/specs/2026-11-07-evoking-ii-design.md`](../specs/2026-11-07-evoking-ii-design.md), §3 (Part 2), with §10–§13 as cross-cutting rules. Section references below point into it. Part 1 (§2) is on branch `feat/evoking-ii-part-1` (draft PR WillRieske/spellcraft#3); this plan builds on it.

## Global Constraints

- Minecraft **26.3**; NeoForge **26.3.0.23-beta**, pinned; Java **25**. Mod ID **`spellcraft`**; base package **`io.github.willrieske.spellcraft`**.
- **Branch:** `feat/evoking-ii-part-2`, cut from `feat/evoking-ii-part-1` at `e92d517` (stacked: Part 1's code is needed and not yet on `main`), in the worktree `C:\dev\spellcraft\.claude\worktrees\evoking-ii-part-2`; commands run from that worktree's root. In the Bash tool use `./gradlew`; in PowerShell use `.\gradlew.bat`. Never `cd` to `C:\dev\spellcraft` itself.
- **Minecraft sources for checks:** the main checkout has them unpacked at `C:\dev\spellcraft\build\mc-src` (vanilla, patched) and `C:\dev\spellcraft\build\nf-src` (NeoForge); read those with Grep/Read instead of unpacking again. Line numbers below are from them (2026-11-08). The facts this plan rests on are listed in Task 1 (api-notes §21); read that section before writing Java.
- The mob (§3.1): `spellcraft:bound_vex`, `MobCategory.MONSTER`, built exactly like vanilla's vex (`fireImmune`, `sized(0.4F, 0.8F)`, `eyeHeight(0.51875F)`, `passengerAttachments(0.7375F)`, `ridingOffset(0.04F)`, `clientTrackingRange(8)`, **`notInPeaceful()`**); attributes `Vex.createAttributes()` plus **`FOLLOW_RANGE` 7.25**; `removeWhenFarAway` false, `requiresCustomPersistence` true; never `setLimitedLife`. No spawn egg, no natural spawn, no loot table. The summoner is the `AllyAttachments.SUMMONER` mark; without a summoner **online in the vex's level** the vex idles (no target, interactions `PASS`).
- The cap (§3.2): **12** per summoner, counted over every loaded level; a press at 12 answers `Request.CAPPED` with `message.spellcraft.too_many_vexes` ("You have too many vexes"), no cast, no cooldown, no sound; at 10 or 11 the cast summons the remainder; otherwise 3. The count is taken at the press and summoned at the perform.
- Following (§3.3): orbit band **2–4** blocks; teleport when farther than **12** blocks to an air cell within **2** blocks of the summoner; the stay toggle is the summoner's **empty main hand, not sneaking** click (`VEX_AMBIENT`, `SUCCESS_SERVER`); a staying vex wanders around its post (the bound origin set when stay was switched on); non-staying vexes within **16** blocks follow a dimension change onto ring cells (`VexRing`) around the arrival point; logout and death remove nothing.
- Fighting (§3.4): `BoundVex.isPrey` is round one's rule unchanged (attackable, summoner in the level, no ally, `canHarmPlayer` for players). Target goals in order: hurt-by-the-summoner's-side (creepers allowed), join-an-ally's-fight (allies within the guard area), nearest hostile (`Enemy`, no `Creeper`, inside the **8×8×7** guard box, line of sight). Every target goal's `canContinueToUse` is `BoundVex.keepsTarget`: alive, still prey, within **7.25** blocks. When a Bound Vex takes a target, the summoner's tamed, un-sitting, idle wolves within **16** blocks take it too.
- Flee, dodge, repair (§3.5–§3.7): a creeper within **8** blocks with `getSwellDir() > 0` → fly straight away to **8** blocks until it stops; an iron golem within **3** blocks mid-swing (`getAttackAnimationTick() > 0`) facing the vex (dot > **0.5**) → dart **3** blocks sideways, every time; an ally golem in the guard area under full health → one vex (a transient claim set) flies within **1.5** blocks of its chest, holds a conjured **iron ingot**, and every **30** ticks swings, plays `IRON_GOLEM_REPAIR` and heals **25**; the ingot goes and the given item returns when the golem is full.
- Pure core (`spell.rules`): no `net.minecraft`/NeoForge/jspecify imports (ArchUnit rule 2 allows only `java..`). Client-only code only under `client` (rule 1): the renderer registration lives in `client.evoking`. Common code never references `client`.
- Error Prone runs with `-Werror`; suppress only with `@SuppressWarnings("<Check>")` plus a comment why. Compare entities by UUID where you can, to need no `ReferenceEquality` suppression. **`-Xlint:all` is on too:** never assign `getMoveControl()` to a local or field (`MoveControl` is raw there and `rawtypes` fails the build); call through it (`vex.getMoveControl().setWantedPosition(...)`) as vanilla does.
- **Unit test style:** new unit and jqwik test classes follow the methodology's *Unit test style* (`~/.claude/development-methodology.md`): a Javadoc per test ("When …, the method should …" then `<p>Method under test: {@link …}`), one `@Nested` class per method under test, `// Arrange` / `// Act` / `// Assert` sections except in one-statement tests. GameTests keep their files' style (the same Javadoc shape, `static void` bodies, `helper.assert…`).
- GameTests: the shared structure is a 3×3×3 box in a barrier shell that blocks line of sight; a test whose mob flies, teleports or needs headroom registers through **`FangGameTests.addPadded`** (padding 13, the long tick budget; the padded box is air 13 blocks down, so lay stone with `helper.setBlock`). **Hostile test mobs are husks** (`EntityTypes.HUSK`: an `Enemy` that does not burn in daylight), never zombies or skeletons; animals are pigs; a golem needs the padded box (2.7 tall; it suffocates under the shared box's ceiling). Every spawned test mob gets `setNoAi(true)` unless the test is about its AI, and is discarded through `whenDone`. A test that mutates global state gets its own environment name.
- **Execution order:** **Task 1 first** (api-notes §21, cited by every Javadoc). Task 2 (pure) is independent. Task 3 needs Tasks 1–2 and is the big switch (registration, summoning, cap); Tasks 4, 5 and 6 each add goals to `BoundVex.registerGoals` and tests to `BoundVexGameTests` and run in order (4, 5, 6) because each edits the same two files; Task 7 is last. Only Task 3 edits `SpellcraftGameTests`'s static block and `Spellcraft`'s constructor.
- **No red run for GameTest tasks (3–6):** their test steps compile only with the production change; the red-then-green cycle is for the pure-core task (2). A GameTest step's "Expected" is the green run after the production code lands.
- **Verification recipe:** every Gradle verification step runs `./gradlew <task> --console=plain > build/<task>.log 2>&1; echo "exit=$?"; tail -40 build/<task>.log` (never `| tail`, which hides Gradle's exit status). Expected: `exit=0`. `runGameTestServer` takes minutes; run it in the background and read the log when it is done. Its last line names the count: expect `All N required tests passed` with no `FAILED` line.
- **Format before you build:** in every task that writes Java, run `./gradlew spotlessApply --console=plain` right before the build/commit step and stage every file it changed in your task's files (check `git status --short`).
- Visual checks belong to Will: never claim a visual works; add `docs/playtest-checklist.md` items instead (Task 7).
- Commits: conventional (gitlint), title ≤ 100 chars, body lines ≤ 80, no attribution lines. The commit gate runs `spotlessCheck` and `build` (minutes); pre-push runs `runGameTestServer`. **Run the `git add` line and the `git commit` line as two separate tool calls**, then check `git log -1 --oneline` landed.
- Release (bump `mod_version`, tag) is not in this plan: the version bumps once, after Part 7 (§12).

## File Structure

```text
src/main/java/io/github/willrieske/spellcraft/
├── Spellcraft.java                               + ModEntities.ENTITIES.register(modEventBus)
├── spell/rules/VexCap.java                       PURE, new: CAP 12, toSummon(count)
├── spell/rules/GuardArea.java                    PURE, new: the 8×8×7 box, REACH 7.25, contains, withinReach
├── evoking/ModEntities.java                      new: the entity register, BOUND_VEX, the attribute event
├── evoking/BoundVex.java                         new: the mob (data, persistence, summoner, isPrey, stay toggle,
│                                                 goal wiring, wolves join, the ChargeGoal and StayWanderGoal copies)
├── evoking/FollowSummonerGoal.java               new (Task 4): orbit, teleport at 12
├── evoking/BoundVexEvents.java                   new (Task 4): portal follow (two events), server-stop cleanup
├── evoking/HurtBySummonerSideGoal.java           new (Task 5): the four memories
├── evoking/AllyFightGoal.java                    new (Task 5): an ally's current target
├── evoking/NearestHostileGoal.java               new (Task 5): Enemy, no creeper, the guard box
├── evoking/FleeCreeperGoal.java                  new (Task 6)
├── evoking/DodgeGolemGoal.java                   new (Task 6)
├── evoking/RepairGolemGoal.java                  new (Task 6): claims, the ingot, 25 per tap
├── evoking/VexSummons.java                       summon(level, summoner, count) creates Bound Vexes; onJoin/install/isPrey removed
├── evoking/EvokingCaster.java                    Request.CAPPED, TOO_MANY_KEY, the count at the press
├── client/evoking/BoundVexRenderers.java         new: RegisterRenderers → VexRenderer::new
└── gametest/
    ├── SpellcraftGameTests.java                  + BoundVexGameTests.registerAll()
    ├── VexGameTests.java                         E8/E9/E16/peaceful/prey rule updated; the install-goal tests removed
    └── BoundVexGameTests.java                    new: cap, stay, persistence (Task 3); follow/teleport/portal (4);
                                                  targeting and wolves (5); flee/dodge/repair (6)
src/main/resources/assets/spellcraft/lang/en_us.json   + entity.spellcraft.bound_vex, message.spellcraft.too_many_vexes
src/test/java/io/github/willrieske/spellcraft/
    spell/rules/VexCapTest.java, VexCapPropertiesTest.java, GuardAreaTest.java, GuardAreaPropertiesTest.java (new)
docs/api-notes.md                                 §21: the facts below
docs/playtest-checklist.md                        the Evoking section's vex items; a new "Evoking II (Part 2)" section
docs/mutation-testing.md                          a row for this part
README.md                                         the Evoking paragraph (vexes)
Not modified: src/main/templates/META-INF/neoforge.mods.toml (no new spell), CLAUDE.md (the packet rule is unchanged; no Mixin), gradle.properties (no version bump), the Evoking spec's one packet (nothing is sent clientbound).
```

## Divergences from the spec (for the plan review's Phase 2)

The spec was written before these lines of game code were read; the plan follows the code.

1. **Vanilla's vex goals are private.** §3.1 says the charge and wander goals are inherited. `Vex.VexChargeAttackGoal`, `VexRandomMoveGoal`, `VexCopyOwnerTargetGoal` and `VexMoveControl` are all `private class` (`Vex.java:222,280,299,329`), so a subclass can neither reuse nor subclass them. `BoundVex.registerGoals` does **not** call `super.registerGoals()` and adds its own copies of the charge and wander goals (the wander copy runs only while staying, §3.3). The move control *is* inherited: `Vex`'s constructor assigns the private `VexMoveControl` instance to the protected `moveControl` field (`Vex.java:57-61`).
2. **Peaceful is a builder flag.** §3.1 says "a `Monster` subclass still refuses to spawn on Peaceful via `EntityType.create`". It is `EntityType.Builder.notInPeaceful()` (`EntityType.java:653`) that makes `canSpawn`/`create` refuse and `Mob.checkDespawn` discard the mob on Peaceful (`Mob.java:715-716`); a `Monster` built without it lives on Peaceful. The type is built with it, and `EvokingCaster` reads `ModEntities.BOUND_VEX.get().canSpawn(level)`.
3. **Only `STAYING` is synced in Part 2.** §3.1 lists `CASTER` (§9.1) and `TEAM_MOVE` (§8) too; nothing reads them before Parts 6 and 7, and Error Prone fails the build on unused private fields. Each later part adds its own.
4. **No `TeamMoveGoal` placeholder, no last-ground memory yet.** §3.3 lists a no-op `TeamMoveGoal` (Part 6) and the summoner's last ground position (for §6.3, Part 5). Both land with the part that reads them.
5. **Portal follow uses two events.** §3.3 names `PlayerChangedDimensionEvent` alone; it fires after the move (`ServerPlayer.java:1236`) and carries no "where the player left". `ServerPlayer.teleport` first posts the cancellable `EntityTravelToDimensionEvent` (`ServerPlayer.java:1177`) while the player still stands at the portal, so `BoundVexEvents` notes the followers there and moves them on the changed-dimension event.
6. **"Newest" memory → first fresh memory in the spec's order.** §3.4 asks for the newest of four memories. Their timestamps are each entity's own `tickCount` (`LivingEntity.setLastHurtByMob`, `:668`) and cannot be compared across entities; the goal takes the first memory that is at most **100** ticks old (vanilla's `HurtByTargetGoal` window) in the spec's order: this vex, the summoner's attacker, the summoner's victim, an ally's attacker.
7. **Repair waits for peace.** §3.3 ranks the job goals (repair) above the charge goal, so a vex under attack would stop to heal a golem. `RepairGolemGoal.canUse` also requires `getTarget() == null`; the priority order itself is kept.
8. **The repair ingot is held by the goal, not a stash.** §3.7 says "the given item is stashed, §4.5"; the stash is Part 3's. In Part 2 the goal keeps the previous main-hand stack in a field and puts it back in `stop()`. A vex unloaded mid-repair keeps the ingot until Part 3's stash lands (noted for the playtest).
9. **Interaction result.** §3.3 says `InteractionResult.SUCCESS`. `mobInteract` runs on both sides and the client does not know the summoner (the mark is not synced), so the client answers `PASS` and the server `SUCCESS_SERVER`, whose swing comes from the server (`InteractionResult.java:11-12`).
10. **No `swing(hand)` in 26.3.** §3.7's `swing(MAIN_HAND)` is `Mob.swing(InteractionHand, SwingAnimation)` (`Mob.java:1529`) with `SwingAnimation.DEFAULT`.
11. **Cross-dimension teleport makes a new entity.** `Entity.teleport` creates a fresh instance and `restoreFrom` copies the save (`Entity.java:3209-3243`): serialized attachments survive, synced data does not. `STAYING` is therefore also saved as NBT (`staying`), and the portal code uses the entity `teleport` returns, never the old one.
12. **The follow range bonus is removed.** `Mob.finalizeSpawn` adds a random ±11.5 % `FOLLOW_RANGE` modifier; `NearestAttackableTargetGoal`'s range reads the attribute (api-notes §19). `BoundVex.finalizeSpawn` removes it, as round one's `install` did, so the targeting range is exactly 7.25.
13. **`VexSummons.onJoin`/`install` go away.** §3.1 drops the `onJoin` goal install; with them goes the `AGGRO_RANGE` 5 rule of round one (the guard box replaces it). A vanilla `Vex` that carries a summoner mark (the test helper `GameTestSupport.summonedVex`) keeps vanilla's goals from now on; it is still an ally and spared.

---

### Task 1: Record the verified 26.3 API facts

**Files:**

- Modify: `docs/api-notes.md` (append a §21 after §20)

**Interfaces:**

- Consumes: nothing.
- Produces: the section every later task's Javadoc and comments cite (`docs/api-notes.md §21`), so this task runs first.

- [ ] **Step 1: Append §21 to `docs/api-notes.md`**

Append after the last line of §20:

````markdown

## 21. Evoking II, Part 2: the Bound Vex (entity type, goals, teleports) (Evoking II Part 2 task)

Verified 2026-11-08 against the same unpacked sources (`build/mc-src`, `build/nf-src`).

- `Vex` (`monster/Vex.java`): `public class Vex extends Monster implements TraceableEntity, OwnableEntity` (`:47`), one
  constructor `Vex(EntityType<? extends Vex>, Level)` (`:57`) whose body assigns `this.moveControl = new VexMoveControl<>(this)`
  (`:58`). `createAttributes()` (`:98`) is `Monster.createMonsterAttributes()` plus `MAX_HEALTH` 14 and `ATTACK_DAMAGE` 4.
  `registerGoals` (`:86-96`): goalSelector `0 FloatGoal`, `4 VexChargeAttackGoal`, `8 VexRandomMoveGoal`,
  `9 LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F)`, `10 LookAtPlayerGoal(this, Mob.class, 8.0F)`; targetSelector
  `1 HurtByTargetGoal(this, Raider.class).setAlertOthers()`, `2 VexCopyOwnerTargetGoal`, `3 NearestAttackableTargetGoal<>(this, Player.class, true)`.
  **All four inner classes are `private class`** (`VexChargeAttackGoal :222`, `VexCopyOwnerTargetGoal :280`,
  `VexMoveControl :299`, `VexRandomMoveGoal :329`): a subclass in another package can neither construct nor extend them;
  `BoundVex` keeps its own copies of the charge and wander goals and skips `super.registerGoals()`. `tick()` (`:74-83`):
  `noPhysics = true; super.tick(); noPhysics = false; setNoGravity(true)`, then the life countdown only while the private
  `hasLimitedLife` is set, so a vex that never gets `setLimitedLife` lives on. `boundOrigin` (`:53`) through
  `setBoundOrigin`/`getBoundOrigin` (`:145-152`); `isCharging`/`setIsCharging` (`:169-175`); `finalizeSpawn` (`:207`) gives the
  iron sword (`populateDefaultEquipmentSlots`, `:217-220`); `getAmbientSound` is `VEX_AMBIENT` (`:187`). `Vex` overrides none of
  `removeWhenFarAway`, `checkDespawn`, `requiresCustomPersistence`; `Mob.removeWhenFarAway(double)` is public and true
  (`Mob.java:702`), `Mob.requiresCustomPersistence()` is public (`:706`, declared in `Mob`, not `Entity`).
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
  `registerEntityRenderer(ModEntities.BOUND_VEX.get(), VexRenderer::new)` compiles with `T = Vex`. It picks the charging texture from
  `entity.isCharging()` (`:36-39`).
- Synced data: `SynchedEntityData.defineId(Class<? extends SyncedDataHolder>, EntityDataSerializer<T>)` (`:30`),
  `EntityDataSerializers.BOOLEAN` (`:89`); `Entity.defineSynchedData(SynchedEntityData.Builder)` (`:428`, `Vex` overrides at `:103-106`
  and must be called through `super`); `Builder.define(accessor, value)`. Save: `addAdditionalSaveData(ValueOutput)` /
  `readAdditionalSaveData(ValueInput)` (`Entity.java:2288-2290`); `ValueOutput.putBoolean` (`:17`), `ValueInput.getBooleanOr` (`:29`).
- Interaction: `Mob.interact(Player, InteractionHand, Vec3)` (`Mob.java:1160`) is the public entry (name tag and spawn egg first,
  then `Entity.interact`, then `mobInteract`); `Mob.mobInteract(Player, InteractionHand)` is **protected** (`:1214`) and runs on
  both sides. `InteractionResult` is a sealed interface (`InteractionResult.java:11-16`): `SUCCESS` (client-predicted swing),
  `SUCCESS_SERVER` (the server sends the swing), `CONSUME`, `FAIL`, `PASS`, `TRY_WITH_EMPTY_HAND`; `interact` treats a result as
  handled when `consumesAction()` (`Mob.java:1165,1174`). `IronGolem.mobInteract` (`:262-278`) heals `25.0F`, plays
  `IRON_GOLEM_REPAIR` at pitch `1 + (r − r) · 0.2` and returns `SUCCESS`.
- Teleport: `Entity.teleport(TeleportTransition)` (`Entity.java:3177`) returns the entity to use afterwards, or null when
  NeoForge's `EntityTravelToDimensionEvent` is cancelled (`CommonHooks.onTravelToDimension`, `nf-src/.../common/CommonHooks.java:841-845`,
  posted from `Entity.teleport` `:3178` **and** `ServerPlayer.teleport` `ServerPlayer.java:1177`, before the move). Same dimension
  returns `this` (`:3192`); **cross-dimension creates a new entity** with `getType().create(newLevel, DIMENSION_TRAVEL)`, calls
  `newEntity.restoreFrom(this)` (save and load: NBT fields and serialized attachments survive, synced data and plain fields do not,
  goals are rebuilt by the constructor) and removes the old one (`:3209-3243`). `TeleportTransition(ServerLevel, Vec3 pos, Vec3 speed,
  float yRot, float xRot, PostTeleportTransition)` (`TeleportTransition.java:28`), `TeleportTransition.DO_NOTHING` (`:24`).
  `PlayerEvent.PlayerChangedDimensionEvent` (`nf-src/.../event/entity/player/PlayerEvent.java:517`; game bus) has `getFrom()`,
  `getTo()` (`:527,531`) and `getEntity()`, posted at the end of `ServerPlayer.teleport` (`ServerPlayer.java:1236`).
  `EntityTravelToDimensionEvent` (`nf-src/.../event/entity/EntityTravelToDimensionEvent.java:24`) has `getEntity()` and
  `getDimension()` (`:32`). Entities by UUID: `ServerLevel.getEntities()` (`:1875`, a `LevelEntityGetter<Entity>`) `.get(UUID)`.
- Creeper, golem, wolf: `Creeper.getSwellDir()` (`:195`) is the synced swell direction, `1` while swelling, `-1` otherwise
  (`Creeper.tick`, `:128-141`; `setSwellDir(int)` `:199`, `ignite()` `:265`); the fuse length is the `Fuse` short tag (`:109,118`,
  default 30). `IronGolem.getAttackAnimationTick()` (`:237`, public) counts down from 10 after a hit; `handleEntityEvent((byte) 4)`
  sets it to 10 on either side (`:224-227`). `TamableAnimal.isTame()` (`:115`), `isOrderedToSit()` (`:238`), `isOwnedBy(LivingEntity)`
  (`:183`), `tame(Player)` (`:170`), `setOrderedToSit(boolean)` (`:242`). `Mob.swing(InteractionHand, SwingAnimation)` (`Mob.java:1529`;
  there is no `swing(InteractionHand)` in 26.3), `SwingAnimation.DEFAULT` (`world/item/component/SwingAnimation.java:11-12`).
  `LivingEntity.heal(float)` (`:1197`), `getMaxHealth()` (`:2083`). `SoundEvents.IRON_GOLEM_REPAIR` (`:894`), `VEX_AMBIENT` (`:1673`),
  `VEX_CHARGE` (`:1674`). `Entity.getLookAngle()` (`:2676`) is the view vector from the view rotations; for a `LivingEntity` the
  yaw is the head rotation (`LivingEntity.getViewYRot`, `:3462`), so a test turns a golem with `setYHeadRot` as well as `setYRot`.
- Goals: `Goal` (`ai/goal/Goal.java`): `canUse()` abstract (`:12`), `canContinueToUse()` defaults to `canUse()` (`:14`),
  `requiresUpdateEveryTick()` false (`:28`), `setFlags(EnumSet<Flag>)` (`:35`), `reducedTickDelay(int)` (`:53`), `Flag {MOVE, LOOK, JUMP, TARGET}`.
  `TargetGoal(Mob, boolean mustSee)` (`:26`); its `canContinueToUse` re-checks only `canAttack` (`:37`); `start()` reads
  `mob.getTarget()`, so set the target before `super.start()`. `NearestAttackableTargetGoal(Mob, Class<T>, boolean mustSee,
  TargetingConditions.Selector)` (`:25`), protected `getTargetSearchArea(double)` (`:53`), `findTarget()` (`:57`), protected fields
  `targetConditions`, `target` (`:16-19`); default random interval 10 → `reducedTickDelay` 5 → `random.nextInt(5) == 0` per tick.
  `TargetingConditions.Selector.test(LivingEntity target, ServerLevel level)` (`:99-100`, **target first**). `Mob.goalSelector` and
  `targetSelector` are `public final` (`Mob.java:135-136`); `GoalSelector.addGoal(int, Goal)` (`:28`), `removeAllGoals(Predicate)` (`:32`),
  `getAvailableGoals()` (`:109`). `MoveControl<T extends Mob>` is generic (`ai/control/MoveControl.java:15`); `Mob.getMoveControl()`
  returns the raw type (`Mob.java:214`), `hasWanted()` (`:32`), `setWantedPosition(double, double, double, double)` (`:40`), `setWait()` (`:165`).
  `Mob.doHurtTarget(ServerLevel, Entity)` (`:1415`), `Mob.getLookControl()` (`:210`), `LookControl.setLookAt(double, double, double, float, float)` (`:38`).
- Queries: `ServerLevel.getEntities(EntityTypeTest<Entity, T>, Predicate<? super T>)` → `List<? extends T>` (`:941`, the whole level);
  `EntityType<T>` is an `EntityTypeTest<Entity, T>` (`EntityType.java:54`); `MinecraftServer.getAllLevels()` → `Iterable<ServerLevel>`
  (`:1213`); `EntityGetter.getEntitiesOfClass(Class<T>, AABB, Predicate<? super T>)` (`:23`). `LivingEntity.getLastHurtByMob()` (`:638`),
  `getLastHurtByMobTimestamp()` (`:651`, the victim's `tickCount` at the hit), `setLastHurtByMob` (`:668`), `getLastHurtMob()` (`:673`),
  `getLastHurtMobTimestamp()` (`:677`), `setLastHurtMob(Entity)` (`:681`). `Enemy` is a marker interface every `Monster` implements
  (`Monster.java:30`). `AABB(Vec3, Vec3)` (`:36`), `inflate(double, double, double)` (`:190`); `LevelReader.isEmptyBlock(BlockPos)` (`:90`,
  air only); `Entity.teleportTo(double, double, double)` (`:3477`, same level, keeps the velocity) and `getRandom()` (`:4261`).
- GameTests: `GameTestHelper.spawn(EntityType<E>, BlockPos)` (`:161`) creates with `EntitySpawnReason.STRUCTURE`, calls
  `setPersistenceRequired()` and **not** `finalizeSpawn` (no iron sword) (`GameTestEntityBuilder.java:44-67`); `spawn(type, pos,
  EntitySpawnReason)` (`:181`) does call it. `helper.getEntities(EntityType<T>)` (`:660`). A `Creeper` loaded from a tag with a large
  `Fuse` hisses without exploding (`setSwellDir(1)` then counts up to `maxSwell`).
````

- [ ] **Step 2: Lint and commit**

Run: `pre-commit run markdownlint-cli2 --files docs/api-notes.md`
Expected: `Passed`.

```bash
git add docs/api-notes.md
```

```bash
git commit -m "docs(api-notes): record the Evoking II Part 2 facts (vex internals, entity registration, teleports)"
```

Then `git log -1 --oneline` shows the commit.

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
- Produces: `VexCap.CAP` (int 12), `VexCap.BATCH` (int 3), `VexCap.toSummon(int count)` → int; `GuardArea.HALF_WIDTH` (double 4.0), `GuardArea.HALF_HEIGHT` (double 3.5), `GuardArea.REACH` (double 7.25), `GuardArea.contains(double dx, double dy, double dz)` → boolean, `GuardArea.withinReach(double dx, double dy, double dz)` → boolean. Task 3 uses `VexCap`; Tasks 3–6 use `GuardArea`.

- [ ] **Step 1: Write the failing tests**

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

        /**
         * When the constants are read, the cap should be twelve and the batch the ring's summon count, so a change to
         * either shows up here.
         *
         * <p>Method under test: {@link VexCap#CAP} and {@link VexCap#BATCH}
         */
        @Test
        void capIsTwelveAndBatchIsTheRingsCount() {
            assertThat(VexCap.CAP).isEqualTo(12);
            assertThat(VexCap.BATCH).isEqualTo(VexRing.SUMMON_COUNT);
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
         * For any count, the count plus what toSummon answers should never exceed the cap, and the answer should never
         * exceed a batch or go negative.
         *
         * <p>Method under test: {@link VexCap#toSummon(int)}
         */
        @Property
        void neverOverTheCapAndNeverMoreThanABatch(@ForAll @IntRange(min = 0, max = 1000) int count) {
            // Act
            int summoned = VexCap.toSummon(count);
            // Assert
            assertThat(summoned).isBetween(0, VexCap.BATCH);
            assertThat(count + summoned).isLessThanOrEqualTo(Math.max(count, VexCap.CAP));
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
         * When a point sits inside the 8×8×7 box or on its faces, contains should answer true; one block past a face it
         * should answer false, on every axis and both signs.
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
        void edgesAreInsideAndOneStepPastThemIsOut(double dx, double dy, double dz, boolean inside) {
            assertThat(GuardArea.contains(dx, dy, dz)).isEqualTo(inside);
        }

        /**
         * When any offset is NaN, contains should answer false: a bad position is never inside.
         *
         * <p>Method under test: {@link GuardArea#contains(double, double, double)}
         */
        @Test
        void nanIsOutside() {
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
        @CsvSource({"7.25, 0, 0, true", "0, 7.25, 0, true", "7.26, 0, 0, false", "4, 3.5, 4, true", "5, 3.5, 5, false"})
        void reachIsSevenAndAQuarter(double dx, double dy, double dz, boolean within) {
            assertThat(GuardArea.withinReach(dx, dy, dz)).isEqualTo(within);
        }

        /**
         * When any offset is NaN, withinReach should answer false.
         *
         * <p>Method under test: {@link GuardArea#withinReach(double, double, double)}
         */
        @Test
        void nanIsOutOfReach() {
            assertThat(GuardArea.withinReach(Double.NaN, 0, 0)).isFalse();
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
         * For any point inside the box, withinReach should answer true: a vex can always fight what it guards.
         *
         * <p>Method under test: {@link GuardArea#withinReach(double, double, double)}
         */
        @Property
        void everyPointOfTheBoxIsWithinReach(
                @ForAll @DoubleRange(min = -4, max = 4) double dx,
                @ForAll @DoubleRange(min = -3.5, max = 3.5) double dy,
                @ForAll @DoubleRange(min = -4, max = 4) double dz) {
            // Arrange
            assertThat(GuardArea.contains(dx, dy, dz)).isTrue();
            // Act & Assert
            assertThat(GuardArea.withinReach(dx, dy, dz)).isTrue();
        }
    }

    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class WithinReach {
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

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests "io.github.willrieske.spellcraft.spell.rules.VexCap*" --tests "io.github.willrieske.spellcraft.spell.rules.GuardArea*" --console=plain > build/test.log 2>&1; echo "exit=$?"; tail -40 build/test.log`
Expected: `exit=1`, compilation fails with `cannot find symbol ... VexCap` / `GuardArea`.

- [ ] **Step 3: Write the two rules**

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
 * 7 tall, centered on the vex, and a target farther than 7.25 blocks is dropped. Offsets are from the vex's position.
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
     * @return true at or under the reach; false beyond it, and for NaN
     */
    public static boolean withinReach(double dx, double dy, double dz) {
        return dx * dx + dy * dy + dz * dz <= REACH * REACH;
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew spotlessApply --console=plain` then the Step 2 command.
Expected: `exit=0`, `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/willrieske/spellcraft/spell/rules/VexCap.java src/main/java/io/github/willrieske/spellcraft/spell/rules/GuardArea.java src/test/java/io/github/willrieske/spellcraft/spell/rules/VexCapTest.java src/test/java/io/github/willrieske/spellcraft/spell/rules/VexCapPropertiesTest.java src/test/java/io/github/willrieske/spellcraft/spell/rules/GuardAreaTest.java src/test/java/io/github/willrieske/spellcraft/spell/rules/GuardAreaPropertiesTest.java
```

```bash
git commit -m "feat(rules): add the pure VexCap and GuardArea rules for Bound Vexes"
```

Then `git log -1 --oneline` shows the commit.

---

### Task 3: The Bound Vex entity, summoned on V with the cap of twelve, and the stay toggle

**Files:**

- Create: `src/main/java/io/github/willrieske/spellcraft/evoking/ModEntities.java`
- Create: `src/main/java/io/github/willrieske/spellcraft/evoking/BoundVex.java`
- Create: `src/main/java/io/github/willrieske/spellcraft/client/evoking/BoundVexRenderers.java`
- Modify: `src/main/java/io/github/willrieske/spellcraft/Spellcraft.java:29-41` (one `register` line)
- Modify: `src/main/java/io/github/willrieske/spellcraft/evoking/VexSummons.java` (rewrite: Bound Vexes, a count, no goal install)
- Modify: `src/main/java/io/github/willrieske/spellcraft/evoking/EvokingCaster.java` (`CAPPED`, `TOO_MANY_KEY`, the count at the press)
- Modify: `src/main/java/io/github/willrieske/spellcraft/spell/rules/VexRing.java` (the life formula goes)
- Modify: `src/main/resources/assets/spellcraft/lang/en_us.json` (two keys)
- Modify: `src/main/java/io/github/willrieske/spellcraft/gametest/SpellcraftGameTests.java:46-61` (one `registerAll` line)
- Modify: `src/main/java/io/github/willrieske/spellcraft/gametest/VexGameTests.java` (E8, E9, peaceful, prey rule updated; five install-goal tests removed)
- Create: `src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexGameTests.java`
- Test: `src/test/java/io/github/willrieske/spellcraft/spell/rules/VexRingTest.java` (and any `*PropertiesTest` naming `lifeTicks`: the life groups go)

**Interfaces:**

- Consumes: `VexCap.toSummon`, `GuardArea.REACH/HALF_WIDTH/HALF_HEIGHT/withinReach` (Task 2); `AllyAttachments.SUMMONER`, `Allies.isAllyOf` (Part 1); `VexRing.offset/COUNT/SUMMON_COUNT`; api-notes §21.
- Produces: `ModEntities.BOUND_VEX` (`DeferredHolder<EntityType<?>, EntityType<BoundVex>>`); `BoundVex` with `isStaying()`, `setStaying(boolean)`, `summonerId()` → `Optional<UUID>`, `isSummonedBy(UUID)`, `summoner()` → `Optional<ServerPlayer>`, static `isPrey(ServerLevel, UUID, LivingEntity)`, `isPrey(LivingEntity)`, package-private `keepsTarget(@Nullable LivingEntity)`, `withinReach(Entity)`, `guardArea()` → `AABB`, static `countOwnedBy(MinecraftServer, UUID)` → int, and `registerGoals()` where Tasks 4–6 add lines; `VexSummons.summon(ServerLevel, ServerPlayer)` and `summon(ServerLevel, ServerPlayer, int count)` → `List<BoundVex>`; `EvokingCaster.Request.CAPPED`, `EvokingCaster.TOO_MANY_KEY`; GameTest helpers `BoundVexGameTests.boundVex(helper, relative, summoner)`, `parked(helper, relative, summoner)`, `husk(helper, relative)`, constants `VEX_SPOT`, `PREY_SPOT`.

- [ ] **Step 1: The entity, its registration and its renderer**

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
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Bound Vex (Evoking II spec §3): a vex a player summons on V that lives until it dies. Vanilla's {@code Vex} gives it
 * the no-clip flight, the move control, the charging texture and the iron sword; this class owns the goals (vanilla's
 * are private classes, docs/api-notes.md §21), the stay flag, the summoner lookup and the prey rule. Without a summoner
 * online in its level it idles. The summoner is the {@link AllyAttachments#SUMMONER} mark, so {@code Allies},
 * {@code GolemMercy} and the Trailing flame already treat it as the summoner's.
 */
public class BoundVex extends Vex {
    /** Synced: whether the vex holds its post instead of following (spec §3.3). */
    private static final EntityDataAccessor<Boolean> STAYING =
            SynchedEntityData.defineId(BoundVex.class, EntityDataSerializers.BOOLEAN);

    private static final String STAYING_TAG = "staying";

    /**
     * The permanent FOLLOW_RANGE modifier {@code Mob.finalizeSpawn} adds ({@code Mob.RANDOM_SPAWN_BONUS_ID}, protected);
     * removed so the targeting range is exactly {@link GuardArea#REACH}.
     */
    private static final Identifier RANDOM_SPAWN_BONUS_ID = Identifier.withDefaultNamespace("random_spawn_bonus");

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
     * Vanilla's spawn (the iron sword), then the random follow-range bonus goes so the reach is exactly
     * {@link GuardArea#REACH} (docs/api-notes.md §19: the targeting goal reads the attribute).
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
     * {@code pvp} game rule; teammates are already allies), and nothing while the summoner is not in {@code level}
     * (offline or in another dimension). Public for its GameTest.
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
        return !(target instanceof Player other) || owner.canHarmPlayer(other);
    }

    /**
     * {@link #isPrey(ServerLevel, UUID, LivingEntity)} for this vex: false on a client level and for a vex with no mark.
     *
     * @param target the candidate
     * @return whether this vex may fight it
     */
    public boolean isPrey(LivingEntity target) {
        return level() instanceof ServerLevel level
                && summonerId().filter(id -> isPrey(level, id, target)).isPresent();
    }

    /**
     * Whether a chosen target is still worth keeping (spec §3.4): alive, still prey, and within {@link GuardArea#REACH}.
     * Every target goal's {@code canContinueToUse}.
     */
    boolean keepsTarget(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && isPrey(target) && withinReach(target);
    }

    /** Whether {@code other} is within {@link GuardArea#REACH} of this vex. */
    boolean withinReach(Entity other) {
        Vec3 offset = other.position().subtract(position());
        return GuardArea.withinReach(offset.x, offset.y, offset.z);
    }

    /** The guard box around this vex (spec §3.4): 8×8×7, centered on its position. */
    AABB guardArea() {
        return new AABB(position(), position()).inflate(GuardArea.HALF_WIDTH, GuardArea.HALF_HEIGHT, GuardArea.HALF_WIDTH);
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
     * not know the summoner (the mark is not synced), so the arm swing comes from the server
     * ({@code SUCCESS_SERVER}, docs/api-notes.md §21).
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!(level() instanceof ServerLevel)
                || hand != InteractionHand.MAIN_HAND
                || !player.getMainHandItem().isEmpty()
                || player.isShiftKeyDown()
                || !isSummonedBy(player.getUUID())) {
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

Notes for the implementer: match `Vex`'s access modifiers on `addAdditionalSaveData`/`readAdditionalSaveData` (read `Vex.java:108-134`; if vanilla declares them `public`, so must you). If Error Prone reports `UnusedVariable` or similar on anything here, fix the cause rather than suppress. `registerGoals` runs inside `Mob`'s constructor, before this class's own constructor body: the inner goals must not read `BoundVex` fields at construction (they do not; they read them in `canUse`/`tick`).

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

- [ ] **Step 2: Summon Bound Vexes, count them at the press**

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

- In the `Request` enum, after `PEACEFUL`, add:

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

In `VexRing.java`, remove `LIFE_ROLL_BOUND`, `TICKS_PER_SECOND`, `MIN_LIFE_SECONDS` and `lifeTicks` and the "and how long they live … the evoker's life formula" words of the class Javadoc; in `src/test/.../spell/rules/VexRingTest.java` remove the `LifeTicks` nested class (`grep -rn "lifeTicks\|LIFE_ROLL_BOUND" src/test src/main` must then find nothing).

- [ ] **Step 3: The GameTests**

In `SpellcraftGameTests.java`'s static block, add `BoundVexGameTests.registerAll();` after `VexGameTests.registerAll();`.

In `VexGameTests.java`:

- Class Javadoc: "Vex GameTests for Strength of Evoking (spec §9, table E8, E9, E12, E16 and the prey rule) as Evoking II Part 2 left them: the vexes are Bound Vexes ({@code BoundVexGameTests} covers their goals). …" (keep the padding and team sentences).
- Remove `e10Targeting`, `e10RangeLimit`, `e11GoalsOnJoin`, `e18ArmorStand`, `targetRecheck`, the `park` helper, their five `registerAll` lines, the constants `VEX_SPOT`, `PREY_SPOT`, `STAND_SPOT`, `OUTSIDE_VEX_SPOT`, `RANGE_WINDOW`, and every import that is then unused (the build fails on unused imports).
- `e8ThreeBesideTheCaster`: the Javadoc's "three marked vexes" becomes "three marked Bound Vexes with no life limit"; inside the loop replace the `FOLLOW_RANGE`, "one target goal" and `life_ticks` assertions with:

```java
                helper.assertTrue(vex instanceof BoundVex, "a Bound Vex, not a vanilla vex");
                helper.assertValueEqual(vex.getAttributeValue(Attributes.FOLLOW_RANGE), GuardArea.REACH, "range");
                helper.assertTrue(vex.getMainHandItem().is(Items.IRON_SWORD), "the evoker's iron sword");
                helper.assertValueEqual(FangGameTests.savedInt(vex, "life_ticks"), Integer.MIN_VALUE, "no life limit");
```

- `e9JoinTheTeam`: `List<Vex> vexes` becomes `List<BoundVex> vexes`; the loop variable `Vex vex` becomes `BoundVex vex`; `vexes.forEach(Vex::discard)` becomes `vexes.forEach(Entity::discard)`.
- `peacefulNotice`: after `server.setDifficulty(Difficulty.PEACEFUL, true);` add
  `helper.assertFalse(ModEntities.BOUND_VEX.get().canSpawn(helper.getLevel()), "setup: a Bound Vex cannot be created on Peaceful");`
  and the Javadoc notes the type is `notInPeaceful`.
- `preyRule`: every `VexSummons.isPrey` becomes `BoundVex.isPrey`; the Javadoc's `{@link VexSummons#isPrey}` becomes `{@link BoundVex#isPrey(ServerLevel, UUID, LivingEntity)}`.
- Imports: add `BoundVex`, `ModEntities`, `GuardArea`, `Items`, `Entity`; remove `VexSummons` if nothing else uses it (`e9` and `e8` do not call it any more? `e9` does: `VexSummons.summon`; keep it then).

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
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import io.github.willrieske.spellcraft.spell.rules.EvokerSpell;
import io.github.willrieske.spellcraft.spell.rules.VexCap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
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
 * Bound Vex GameTests (Evoking II spec §3, §11 "Part 2"). Tests whose vex flies or teleports, or that need headroom,
 * register through {@code FangGameTests.addPadded} (padding 13); the rest run inside the shared 3×3×3 box, where the
 * barrier shell blocks line of sight, so a targeting test keeps the vex and its prey both inside the box. Hostiles are
 * husks (they do not burn in daylight).
 */
public final class BoundVexGameTests {
    private static final BlockPos FEET = FangGameTests.FEET;
    private static final float NORTH = 180.0F;
    private static final int WARMUP = EvokerSpell.WARMUP_TICKS;

    /** The box's top north-west corner: where a test parks the vex. */
    static final BlockPos VEX_SPOT = new BlockPos(0, 2, 0);

    /** The box's opposite bottom corner: farther from {@link #VEX_SPOT} than the summoner at {@link #FEET}. */
    static final BlockPos PREY_SPOT = new BlockPos(2, 1, 2);

    private BoundVexGameTests() {}

    static void registerAll() {
        SpellcraftGameTests.add(SHARED, "bound_vex_cap_refuses_at_twelve", BoundVexGameTests::capRefusesAtTwelve);
        FangGameTests.addPadded(SHARED, "bound_vex_cap_tops_up_to_twelve", BoundVexGameTests::capTopsUpToTwelve);
        SpellcraftGameTests.add(SHARED, "bound_vex_stay_toggle", BoundVexGameTests::stayToggle);
        SpellcraftGameTests.add(SHARED, "bound_vex_idles_without_a_summoner", BoundVexGameTests::idlesWithoutASummoner);
        SpellcraftGameTests.add(SHARED, "bound_vex_save_keeps_stay_and_mark", BoundVexGameTests::saveKeepsStayAndMark);
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
     * cast and spend no cooldown: with the vexes gone, the same press starts.
     *
     * <p>Method under test: {@link EvokingCaster#request}, through {@link BoundVex#countOwnedBy} and
     * {@link VexCap#toSummon}
     */
    static void capRefusesAtTwelve(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
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
        helper.assertValueEqual(
                BoundVex.countOwnedBy(helper.getLevel().getServer(), player.getUUID()), VexCap.CAP, "twelve counted");

        // Act: the vexes go, and the same press starts, so no cooldown was spent
        vexes.forEach(Entity::discard);
        helper.assertValueEqual(
                BoundVex.countOwnedBy(helper.getLevel().getServer(), player.getUUID()), 0, "none counted once gone");
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.VEXES), Request.STARTED, "no cooldown spent");
        helper.succeed();
    }

    /**
     * When the summoner has ten Bound Vexes alive, should start the cast and summon two, for twelve in all.
     *
     * <p>Method under test: {@link EvokingCaster#request} then {@link EvokingCaster#tick}, through
     * {@code VexSummons.summon(level, player, count)}
     */
    static void capTopsUpToTwelve(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        VexGameTests.discardLeftoverVexes(helper);
        for (int i = 0; i < VexCap.CAP - 2; i++) {
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
                    "topped up to twelve");
            helper.succeed();
        });
    }

    /**
     * When the summoner clicks a Bound Vex with an empty main hand, should toggle stay on (the post is the block it
     * stands at) and off again, each handled by the server; a stranger's click, a sneaking click and a click with an
     * item in hand should pass and change nothing.
     *
     * <p>Method under test: {@code BoundVex.mobInteract} (protected), through {@code Mob.interact}
     */
    static void stayToggle(GameTestHelper helper) {
        // Arrange
        ServerPlayer summoner = summoner(helper);
        ServerPlayer stranger = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, summoner);
        BlockPos post = helper.absolutePos(VEX_SPOT);

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
        helper.succeed();
    }

    /**
     * When a Bound Vex has no summoner mark (as {@code /summon} makes one), should idle: no summoner, no prey, a click
     * passes, and it is never removed for being far away.
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
}
```

Check `Husk`'s package in `build/mc-src/net/minecraft/world/entity/monster/` (`zombie/Husk.java` or `Husk.java`) and fix the import. `EvokingGameTests.evoking()` is the effect instance `FangGameTests.caster` uses; it is package-private, so it is reachable here.

- [ ] **Step 4: Format, build, GameTests, client load**

Run: `./gradlew spotlessApply --console=plain`, then `./gradlew build --console=plain > build/build.log 2>&1; echo "exit=$?"; tail -40 build/build.log`
Expected: `exit=0`. Unit tests: the `VexRingTest` life group is gone; everything else green.

Run in the background: `./gradlew runGameTestServer --console=plain > build/runGameTestServer.log 2>&1; echo "exit=$?"; grep -n "FAILED\|required tests" build/runGameTestServer.log | tail -5`
Expected: `exit=0`, `All N required tests passed` with N = 164 − 5 removed + 5 new = **164** (the count stays; the five install-goal tests are replaced by the five Bound Vex tests above; Tasks 4–6 add more).

Run: `./gradlew runData --console=plain > build/runData.log 2>&1; echo "exit=$?"; tail -20 build/runData.log`
Expected: `exit=0` (the client subscriber `BoundVexRenderers` and `ModEntities` load; `EvokingArmPoseCheck` still passes).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/willrieske/spellcraft/Spellcraft.java src/main/java/io/github/willrieske/spellcraft/evoking/ModEntities.java src/main/java/io/github/willrieske/spellcraft/evoking/BoundVex.java src/main/java/io/github/willrieske/spellcraft/evoking/VexSummons.java src/main/java/io/github/willrieske/spellcraft/evoking/EvokingCaster.java src/main/java/io/github/willrieske/spellcraft/client/evoking/BoundVexRenderers.java src/main/java/io/github/willrieske/spellcraft/spell/rules/VexRing.java src/main/resources/assets/spellcraft/lang/en_us.json src/main/java/io/github/willrieske/spellcraft/gametest/SpellcraftGameTests.java src/main/java/io/github/willrieske/spellcraft/gametest/VexGameTests.java src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexGameTests.java src/test/java/io/github/willrieske/spellcraft/spell/rules/VexRingTest.java
```

```bash
git commit -m "feat(evoking): add the Bound Vex, summon it on V with a cap of twelve, and the stay toggle"
```

Then `git log -1 --oneline` shows the commit.

---

### Task 4: Following, teleporting, staying and portals

**Files:**

- Create: `src/main/java/io/github/willrieske/spellcraft/evoking/FollowSummonerGoal.java`
- Create: `src/main/java/io/github/willrieske/spellcraft/evoking/BoundVexEvents.java`
- Modify: `src/main/java/io/github/willrieske/spellcraft/evoking/BoundVex.java` (`registerGoals`: one line)
- Modify: `src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexGameTests.java` (four tests)

**Interfaces:**

- Consumes: `BoundVex.isStaying/summoner/isSummonedBy/getMoveControl/getBoundOrigin` (Task 3); `VexRing.offset/COUNT`; api-notes §21 (teleports, the two dimension events).
- Produces: `FollowSummonerGoal` (package-private; constants `ORBIT_MIN` 2.0, `ORBIT_MAX` 4.0, `TELEPORT_DISTANCE` 12.0, `TELEPORT_SPREAD` 2); `BoundVexEvents` (public, `@EventBusSubscriber`; `PORTAL_RANGE` 16.0; Task 6 adds a line to its `onServerStopped`).

- [ ] **Step 1: The follow goal**

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
 * a vex farther than the orbit band flies to a random spot 2–4 blocks from the summoner's eyes, and a vex farther than
 * {@link #TELEPORT_DISTANCE} blocks teleports to an air cell beside the summoner instead. Each use makes one move; the
 * move control flies it there and {@code canUse} picks the next. Without a summoner in the level the goal is idle.
 */
final class FollowSummonerGoal extends Goal {
    /** The orbit band's inner edge: the vex is left alone inside it. */
    static final double ORBIT_MIN = 2.0;

    /** The orbit band's outer edge: beyond it the vex moves. */
    static final double ORBIT_MAX = 4.0;

    /** Farther than this, the vex teleports instead of flying. */
    static final double TELEPORT_DISTANCE = 12.0;

    /** The teleport lands within this many blocks of the summoner's feet sideways, and 0–2 blocks up. */
    static final int TELEPORT_SPREAD = 2;

    private static final int TELEPORT_TRIES = 10;
    private static final double SPEED = 1.0;

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

    private void flyToOrbit(ServerPlayer owner) {
        RandomSource random = vex.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double radius = ORBIT_MIN + random.nextDouble() * (ORBIT_MAX - ORBIT_MIN);
        Vec3 spot = owner.getEyePosition()
                .add(Math.cos(angle) * radius, random.nextDouble() - 0.5, Math.sin(angle) * radius);
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

In `BoundVex.registerGoals`, add after the `ChargeGoal` line:

```java
        goalSelector.addGoal(4, new FollowSummonerGoal(this));
```

- [ ] **Step 2: Portals**

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
 * Bound Vexes follow their summoner through a dimension change (Evoking II spec §3.3): the non-staying ones within
 * {@link #PORTAL_RANGE} blocks of the summoner when the move starts land on ring cells around the summoner when it ends.
 * Two events, because the arrival event no longer knows where the player left (docs/api-notes.md §21): the followers are
 * noted on {@code EntityTravelToDimensionEvent} and moved on {@code PlayerChangedDimensionEvent}. Staying vexes stay;
 * a moved vex is a new entity instance that keeps its mark (an attachment) and its stay flag (saved as NBT).
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class BoundVexEvents {
    /** How far from the leaving summoner a following vex comes along. */
    static final double PORTAL_RANGE = 16.0;

    /** Per traveling player: the vexes to bring. Transient, like {@code EvokingCaster}'s map. */
    private static final Map<UUID, List<UUID>> FOLLOWERS = new HashMap<>();

    private BoundVexEvents() {}

    /**
     * Notes the followers while the player still stands where they leave.
     *
     * @param event the travel event, posted before any entity's dimension change
     */
    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.level();
        UUID id = player.getUUID();
        List<UUID> followers = level.getEntities(
                        ModEntities.BOUND_VEX.get(),
                        vex -> !vex.isStaying() && vex.isSummonedBy(id) && vex.distanceTo(player) <= PORTAL_RANGE)
                .stream()
                .map(Entity::getUUID)
                .toList();
        if (followers.isEmpty()) {
            FOLLOWERS.remove(id);
        } else {
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
            if (from.getEntities().get(id) instanceof BoundVex vex) {
                VexRing.Offset offset = VexRing.offset(random.nextInt(VexRing.COUNT));
                Vec3 spot = Vec3.atCenterOf(feet.offset(offset.dx(), 1, offset.dz()));
                vex.teleport(new TeleportTransition(
                        to, spot, Vec3.ZERO, vex.getYRot(), vex.getXRot(), TeleportTransition.DO_NOTHING));
            }
        }
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

If `Entity.teleport`'s return value trips an Error Prone check, assign it to a local named `moved` and ignore it with a comment that the old entity is removed by the teleport itself (a `@SuppressWarnings("UnusedVariable")` is **not** the fix: do not store it at all if no check fires).

- [ ] **Step 3: The GameTests**

In `BoundVexGameTests.registerAll`, add:

```java
        FangGameTests.addPadded(SHARED, "bound_vex_follow_flies_into_the_orbit", BoundVexGameTests::followFliesIntoTheOrbit);
        FangGameTests.addPadded(
                SHARED, "bound_vex_follow_teleports_from_thirteen_blocks", BoundVexGameTests::followTeleportsFromThirteen);
        FangGameTests.addPadded(SHARED, "bound_vex_staying_holds_its_post", BoundVexGameTests::stayingHoldsItsPost);
        FangGameTests.addPadded(SHARED, "bound_vex_portal_follow", BoundVexGameTests::portalFollow);
```

and the tests (imports: `FollowSummonerGoal` is package-private in `evoking`, so the tests name its numbers through the constants below; add `ServerLevel`, `Level`, `List` imports as needed):

```java
    /** The follow goal's numbers (spec §3.3), repeated here because the goal is package-private in {@code evoking}. */
    private static final double ORBIT_MAX = 4.0;

    private static final double TELEPORT_SPREAD = 2.0;
    /** A vex this far north of the caster is outside the teleport distance (12) and inside the padding (13). */
    private static final int FAR_NORTH = 13;

    /**
     * When a following Bound Vex hovers seven blocks from its summoner, should fly into the orbit band (within four
     * blocks, give or take the move control's stopping margin) without teleporting.
     *
     * <p>Method under test: {@code FollowSummonerGoal} (package-private), through the vex's goal selector
     */
    static void followFliesIntoTheOrbit(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = boundVex(helper, FEET.north(7), player);

        // Act & Assert: never within the orbit band on the first tick (a teleport would be), then inside it
        helper.assertTrue(vex.distanceTo(player) > ORBIT_MAX + 1.0, "setup: seven blocks out");
        helper.succeedWhen(() -> helper.assertTrue(
                vex.distanceTo(player) <= ORBIT_MAX + 1.0, "in the orbit band, got " + vex.distanceTo(player)));
    }

    /**
     * When a following Bound Vex is thirteen blocks from its summoner, should be beside the summoner within five ticks:
     * a flight could not cover that distance so fast, so it teleported.
     *
     * <p>Method under test: {@code FollowSummonerGoal} (package-private), through the vex's goal selector
     */
    static void followTeleportsFromThirteen(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = boundVex(helper, FEET.north(FAR_NORTH), player);

        // Act & Assert
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(
                    vex.distanceTo(player) <= TELEPORT_SPREAD + 2.5,
                    "beside the summoner after a teleport, got " + vex.distanceTo(player));
            helper.succeed();
        });
    }

    /**
     * When a staying Bound Vex is thirteen blocks from its summoner, should neither fly nor teleport to the summoner
     * over sixty ticks, and should drift no farther than the wander range from its post.
     *
     * <p>Method under test: {@code FollowSummonerGoal} and {@code BoundVex.StayWanderGoal}, through the goal selector
     */
    static void stayingHoldsItsPost(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = boundVex(helper, FEET.north(FAR_NORTH), player);
        vex.setStaying(true);
        Vec3 post = Vec3.atCenterOf(helper.absolutePos(FEET.north(FAR_NORTH)));

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(60, () -> {
                    helper.assertTrue(vex.distanceTo(player) > ORBIT_MAX + 1.0, "a staying vex never comes along");
                    helper.assertTrue(
                            vex.position().distanceTo(post) <= 9.0, "drifts around its post, got " + vex.position().distanceTo(post));
                })
                .thenSucceed();
    }

    /**
     * When the summoner moves to the Nether with one following and one staying Bound Vex nearby, should bring the
     * following one to a ring cell beside the arrival point, still marked, and leave the staying one in the Overworld.
     *
     * <p>Method under test: {@link io.github.willrieske.spellcraft.evoking.BoundVexEvents#onTravel} and
     * {@link io.github.willrieske.spellcraft.evoking.BoundVexEvents#onChangedDimension}
     */
    static void portalFollow(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        UUID id = player.getUUID();
        BoundVex following = boundVex(helper, FEET.south(2), player);
        BoundVex staying = boundVex(helper, FEET.north(2), player);
        staying.setStaying(true);
        ServerLevel overworld = helper.getLevel();

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
            helper.assertValueEqual(left.size(), 1, "one Bound Vex left in the Overworld");
            helper.assertTrue(left.get(0).isStaying(), "the staying one stayed");
        });
    }
```

`BlockPos.closerThan(Vec3i, double)` exists on `Vec3i`; if the name differs in 26.3, use `blockPosition().distSqr(netherFeet) <= 4.5 * 4.5`. `FangGameTests.toNether`'s cleanup discards every `Vex` near the pocket, Bound Vexes included.

- [ ] **Step 4: Format, build, GameTests**

Run: `./gradlew spotlessApply --console=plain`, then the build recipe. Expected: `exit=0`.
Run `runGameTestServer` in the background. Expected: `exit=0`, `All 168 required tests passed`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/willrieske/spellcraft/evoking/FollowSummonerGoal.java src/main/java/io/github/willrieske/spellcraft/evoking/BoundVexEvents.java src/main/java/io/github/willrieske/spellcraft/evoking/BoundVex.java src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexGameTests.java
```

```bash
git commit -m "feat(evoking): Bound Vexes follow their summoner, teleport when far, stay on command and ride portals"
```

Then `git log -1 --oneline` shows the commit.

---

### Task 5: Who a Bound Vex fights, and the wolves that join

**Files:**

- Create: `src/main/java/io/github/willrieske/spellcraft/evoking/HurtBySummonerSideGoal.java`
- Create: `src/main/java/io/github/willrieske/spellcraft/evoking/AllyFightGoal.java`
- Create: `src/main/java/io/github/willrieske/spellcraft/evoking/NearestHostileGoal.java`
- Modify: `src/main/java/io/github/willrieske/spellcraft/evoking/BoundVex.java` (`registerGoals`: three lines; a `setTarget` override and `WOLF_CALL_RANGE`)
- Modify: `src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexGameTests.java` (eleven tests)

**Interfaces:**

- Consumes: `BoundVex.summoner/isPrey/keepsTarget/guardArea/tickCount` (Task 3); `Allies.isAllyOf`; api-notes §21 (goals, memories).
- Produces: `HurtBySummonerSideGoal`, `AllyFightGoal`, `NearestHostileGoal` (package-private `TargetGoal`s; `SCAN_INTERVAL` 10, `MEMORY_TICKS` 100); `BoundVex.WOLF_CALL_RANGE` 16.0 and the `setTarget` override.

- [ ] **Step 1: The three target goals**

`src/main/java/io/github/willrieske/spellcraft/evoking/HurtBySummonerSideGoal.java`:

```java
package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.spell.Allies;
import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.jspecify.annotations.Nullable;

/**
 * The grudge goal (Evoking II spec §3.4, first in the target selector): every ten ticks, the first fresh memory in this
 * order that is still worth fighting ({@code BoundVex.keepsTarget}): the mob that last hurt this vex, the mob that
 * last hurt the summoner, the mob the summoner last hit, the mob that last hurt an ally inside the guard area. A memory
 * is fresh for {@link #MEMORY_TICKS} on its owner's own clock (vanilla's {@code HurtByTargetGoal} window; the clocks of
 * different entities cannot be compared, docs/api-notes.md §21). A creeper is allowed here: it provoked.
 */
final class HurtBySummonerSideGoal extends TargetGoal {
    /** How often the memories are read. */
    static final int SCAN_INTERVAL = 10;

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
        if (vex.tickCount % SCAN_INTERVAL != 0) {
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

    /** {@code remembered} when the memory is fresh on {@code rememberer}'s clock and the vex may fight it. */
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
        for (Mob ally : vex.level().getEntitiesOfClass(Mob.class, vex.guardArea(), mob -> Allies.isAllyOf(owner, mob))) {
            LivingEntity attacker = remembered(ally, ally.getLastHurtByMob(), ally.getLastHurtByMobTimestamp());
            if (attacker != null) {
                return attacker;
            }
        }
        return null;
    }

    @Override
    public void start() {
        vex.setTarget(found);
        super.start();
    }

    @Override
    public boolean canContinueToUse() {
        return vex.keepsTarget(vex.getTarget());
    }
}
```

`src/main/java/io/github/willrieske/spellcraft/evoking/AllyFightGoal.java`:

```java
package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.spell.Allies;
import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import org.jspecify.annotations.Nullable;

/**
 * Vexes join fights their allies start (Evoking II spec §3.4, second in the target selector): every ten ticks, the
 * nearest hostile ({@code Enemy}) that an ally mob inside the guard area (an iron golem, a wolf, another Bound Vex) has
 * as its target, if the vex may fight it ({@code BoundVex.keepsTarget}).
 */
final class AllyFightGoal extends TargetGoal {
    /** How often the allies are read. */
    static final int SCAN_INTERVAL = 10;

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
        if (vex.tickCount % SCAN_INTERVAL != 0) {
            return false;
        }
        ServerPlayer owner = vex.summoner().orElse(null);
        if (owner == null) {
            return false;
        }
        found = null;
        double nearest = Double.MAX_VALUE;
        for (Mob ally : vex.level().getEntitiesOfClass(Mob.class, vex.guardArea(), mob -> Allies.isAllyOf(owner, mob))) {
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

    @Override
    public void start() {
        vex.setTarget(found);
        super.start();
    }

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
 * The patrol goal (Evoking II spec §3.4, third in the target selector): vanilla's nearest-target search over the guard
 * box instead of the follow-range cube, with line of sight, for anything hostile ({@code Enemy}) but a creeper that the
 * vex may fight. Vanilla re-checks only {@code canAttack} when keeping a target, so {@code canContinueToUse} adds
 * {@code BoundVex.keepsTarget} (a target that became an ally, died or left reach is dropped).
 */
final class NearestHostileGoal extends NearestAttackableTargetGoal<LivingEntity> {
    private final BoundVex vex;

    NearestHostileGoal(BoundVex vex) {
        super(
                vex,
                LivingEntity.class,
                true,
                (target, level) -> target instanceof Enemy && !(target instanceof Creeper) && vex.isPrey(target));
        this.vex = vex;
    }

    @Override
    protected AABB getTargetSearchArea(double followDistance) {
        return vex.guardArea();
    }

    @Override
    public boolean canContinueToUse() {
        return super.canContinueToUse() && vex.keepsTarget(mob.getTarget());
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

- a constant after `RANDOM_SPAWN_BONUS_ID`:

```java
    /** How far the summoner's wolves hear the call to a fight (spec §3.4). */
    static final double WOLF_CALL_RANGE = 16.0;
```

- and the override, after `mobInteract` (imports `net.minecraft.world.entity.animal.wolf.Wolf`):

```java
    /**
     * Takes the target and calls the summoner's tamed, idle, un-sitting wolves within {@link #WOLF_CALL_RANGE} onto it
     * (spec §3.4). The call goes out only when the target really changed to {@code target} ({@code GolemMercy} may cancel
     * a change, and clearing a target calls nobody).
     */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target);
        LivingEntity set = getTarget();
        if (target == null
                || set == null
                || !set.getUUID().equals(target.getUUID())
                || !(level() instanceof ServerLevel level)) {
            return;
        }
        summoner().ifPresent(owner -> {
            AABB around = new AABB(position(), position()).inflate(WOLF_CALL_RANGE);
            for (Wolf wolf : level.getEntitiesOfClass(
                    Wolf.class,
                    around,
                    wolf -> wolf.isTame() && wolf.isOwnedBy(owner) && wolf.getTarget() == null && !wolf.isOrderedToSit())) {
                wolf.setTarget(target);
            }
        });
    }
```

- Update the class Javadoc's goal sentence to name the three target goals.

- [ ] **Step 2: The GameTests**

In `BoundVexGameTests.registerAll`, add (`TARGET_WINDOW` tests need more than 20 ticks, so they pass their own tick limit):

```java
        SpellcraftGameTests.add(SHARED, "bound_vex_three_target_goals", BoundVexGameTests::threeTargetGoals);
        SpellcraftGameTests.add(SHARED, "bound_vex_attacks_a_hostile_in_the_area", BoundVexGameTests::attacksAHostile);
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
                SHARED, "bound_vex_drops_a_target_out_of_reach", BoundVexGameTests::dropsATargetOutOfReach);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_drops_a_target_that_becomes_an_ally", LONG, 0, BoundVexGameTests::dropsANewAlly);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_no_summoner_no_target", LONG, 0, BoundVexGameTests::noSummonerNoTarget);
```

with the constants and tests (imports: `Creeper`, `Pig`, `Wolf`, `LivingEntity`, `UUID`; `FangGameTests.pig` is the pig helper):

```java
    /** Ticks for a test that waits for a 10-tick target scan or watches a 40-tick window. */
    private static final int LONG = 100;

    /** The window over which "never a target" is watched: four target scans. */
    private static final int TARGET_WINDOW = 40;

    /** The box's bottom north-east corner, a second spot inside the box for an ally or a second mob. */
    private static final BlockPos SIDE_SPOT = new BlockPos(2, 1, 0);

    /** A tamed wolf of {@code owner} with no AI at {@code relative}, discarded when the test ends. */
    private static Wolf pet(GameTestHelper helper, BlockPos relative, ServerPlayer owner) {
        Wolf wolf = helper.spawn(EntityTypes.WOLF, relative);
        wolf.setNoAi(true);
        wolf.tame(owner);
        whenDone(helper, wolf::discard);
        return wolf;
    }

    private static boolean targets(BoundVex vex, LivingEntity expected) {
        LivingEntity target = vex.getTarget();
        return target != null && target.getUUID().equals(expected.getUUID());
    }

    /**
     * When a Bound Vex is made, should carry the three target goals of spec §3.4 and nothing else in its target
     * selector.
     *
     * <p>Method under test: {@code BoundVex.registerGoals} (protected)
     */
    static void threeTargetGoals(GameTestHelper helper) {
        BoundVex vex = boundVex(helper, VEX_SPOT, summoner(helper));
        helper.assertValueEqual(vex.targetSelector.getAvailableGoals().size(), 3, "three target goals");
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
        FangGameTests.pig(helper, PREY_SPOT);
        Husk[] husk = new Husk[1];

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(TARGET_WINDOW, () -> helper.assertTrue(vex.getTarget() == null, "a pig is never a target"))
                .thenExecute(() -> husk[0] = husk(helper, SIDE_SPOT))
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, husk[0]), "the husk is the target"))
                .thenSucceed();
    }

    /**
     * When a calm creeper stands in the guard area, should take no target over forty ticks; once the creeper hurts the
     * vex, should take it as its target.
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
                .thenExecute(() -> vex.hurtServer(
                        helper.getLevel(), helper.getLevel().damageSources().mobAttack(creeper), 1.0F))
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
        Pig pig = FangGameTests.pig(helper, PREY_SPOT);
        whenDone(helper, pig::discard);

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
     * When a Bound Vex takes a target, should give it to the summoner's tamed, idle wolf within sixteen blocks, and not
     * to a sitting one.
     *
     * <p>Method under test: {@link BoundVex#setTarget}
     */
    static void callsTheSummonersWolves(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Wolf idle = pet(helper, SIDE_SPOT, player);
        Wolf sitting = pet(helper, new BlockPos(0, 1, 2), player);
        sitting.setOrderedToSit(true);

        // Act
        Husk husk = husk(helper, PREY_SPOT);

        // Assert
        helper.succeedWhen(() -> {
            helper.assertTrue(targets(vex, husk), "setup: the vex took the husk");
            helper.assertTrue(
                    idle.getTarget() != null && idle.getTarget().getUUID().equals(husk.getUUID()),
                    "the idle wolf joins");
            helper.assertTrue(sitting.getTarget() == null, "the sitting wolf stays put");
        });
    }

    /**
     * When a parked Bound Vex's husk target is moved eight blocks away (past the 7.25 reach and out of the guard box),
     * should drop it within a few ticks and not take it again.
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
     * When a Bound Vex has no summoner mark, should take no target over forty ticks with a husk beside it.
     *
     * <p>Method under test: {@link BoundVex#isPrey(net.minecraft.world.entity.LivingEntity)}, through every target goal
     */
    static void noSummonerNoTarget(GameTestHelper helper) {
        // Arrange
        BoundVex vex = helper.spawn(ModEntities.BOUND_VEX.get(), VEX_SPOT);
        vex.goalSelector.removeAllGoals(goal -> true);
        whenDone(helper, vex::discard);
        husk(helper, PREY_SPOT);

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(TARGET_WINDOW, () -> helper.assertTrue(vex.getTarget() == null, "nothing without a summoner"))
                .thenSucceed();
    }
```

The `goal instanceof NearestAttackableTargetGoal` filter runs on `WrappedGoal.getGoal()` inside `removeAllGoals`? Check `GoalSelector.removeAllGoals(Predicate<Goal>)` (`:32`): it tests the wrapped goal's inner `Goal`; if it tests the `WrappedGoal` itself, use `goal -> goal.getGoal() instanceof NearestAttackableTargetGoal` with the matching predicate type. Zombies are not used anywhere: husks stand in daylight.

- [ ] **Step 3: Format, build, GameTests**

Run: `./gradlew spotlessApply --console=plain`, then the build recipe. Expected: `exit=0`.
Run `runGameTestServer` in the background. Expected: `exit=0`, `All 180 required tests passed`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/github/willrieske/spellcraft/evoking/HurtBySummonerSideGoal.java src/main/java/io/github/willrieske/spellcraft/evoking/AllyFightGoal.java src/main/java/io/github/willrieske/spellcraft/evoking/NearestHostileGoal.java src/main/java/io/github/willrieske/spellcraft/evoking/BoundVex.java src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexGameTests.java
```

```bash
git commit -m "feat(evoking): Bound Vexes fight what their side fights and call the summoner's wolves"
```

Then `git log -1 --oneline` shows the commit.

---

### Task 6: Fleeing a creeper, dodging a golem, repairing a golem

**Files:**

- Create: `src/main/java/io/github/willrieske/spellcraft/evoking/FleeCreeperGoal.java`
- Create: `src/main/java/io/github/willrieske/spellcraft/evoking/DodgeGolemGoal.java`
- Create: `src/main/java/io/github/willrieske/spellcraft/evoking/RepairGolemGoal.java`
- Modify: `src/main/java/io/github/willrieske/spellcraft/evoking/BoundVex.java` (`registerGoals`: three lines)
- Modify: `src/main/java/io/github/willrieske/spellcraft/evoking/BoundVexEvents.java` (`onServerStopped`: one line)
- Modify: `src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexGameTests.java` (three tests)

**Interfaces:**

- Consumes: `BoundVex.summoner/guardArea/getMoveControl/getTarget/tickCount` (Task 3); `Allies.isAllyOf`; `GolemGameTests.golem(helper, pos, health)`; api-notes §21.
- Produces: `FleeCreeperGoal` (`NOTICE_RANGE` 8.0, `SAFE_DISTANCE` 8.0), `DodgeGolemGoal` (`NOTICE_RANGE` 3.0, `FACING` 0.5, `DART` 3.0), `RepairGolemGoal` (`HEAL` 25.0F, `TAP_INTERVAL` 30, `REACH` 1.5; static `clearClaims()`).

- [ ] **Step 1: The three goals**

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
 * {@link #NOTICE_RANGE} is swelling ({@code getSwellDir() > 0}), the vex flies directly away from it to
 * {@link #SAFE_DISTANCE} blocks and holds there until the creeper stops swelling, dies or explodes. The goal outranks
 * the charge goal (both hold the MOVE flag), so the target is kept and the charge resumes afterwards.
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

    @Nullable
    private Creeper nearestSwelling() {
        AABB around = new AABB(vex.position(), vex.position()).inflate(NOTICE_RANGE);
        return vex.level()
                .getEntitiesOfClass(Creeper.class, around, candidate -> candidate.isAlive() && candidate.getSwellDir() > 0)
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

    @Override
    public void start() {
        tick();
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
 * mid-swing ({@code getAttackAnimationTick() > 0}) and faces the vex (the dot of its look and the direction to the vex
 * is over {@link #FACING}), the vex moves {@link #DART} blocks sideways, perpendicular to the golem's look, in one move,
 * every time. One dart per swing: the goal waits out the swing's ten ticks before it can start again.
 */
final class DodgeGolemGoal extends Goal {
    /** How near a swinging golem must be. */
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
        Vec3 toVex = vex.position().subtract(candidate.getEyePosition());
        return toVex.lengthSqr() > 1.0E-4 && candidate.getLookAngle().dot(toVex.normalize()) > FACING;
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
        Vec3 look = golem.getLookAngle();
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
import java.util.HashSet;
import java.util.Set;
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
 * A Bound Vex repairs a hurt ally golem (Evoking II spec §3.7): every ten ticks, when the vex is idle (no target) and
 * its summoner is here, the nearest iron golem in the guard area that is an ally (so: not mad), under full health and
 * not already being repaired by another Bound Vex (a transient claim set keyed by golem id) is taken. The vex flies to
 * within {@link #REACH} of the golem's chest holding a conjured iron ingot, and every {@link #TAP_INTERVAL} ticks
 * swings, plays the repair sound and heals {@link #HEAL} (vanilla's ingot amount). When the golem is full, the ingot
 * goes and the item the vex held comes back. The held item lives in this goal until Part 3's stash: a vex unloaded
 * mid-repair keeps the ingot (noted for the playtest).
 */
final class RepairGolemGoal extends Goal {
    /** Health per tap: vanilla's iron-ingot repair. */
    static final float HEAL = 25.0F;

    /** Ticks between taps. */
    static final int TAP_INTERVAL = 30;

    /** How near the golem's chest the vex taps from. */
    static final double REACH = 1.5;

    private static final int SCAN_INTERVAL = 10;
    private static final double CHEST_HEIGHT = 1.4;
    private static final double SPEED = 1.0;

    /** Golems under repair, by UUID, so two vexes never share one (spec §3.7). Cleared when the server stops. */
    private static final Set<UUID> CLAIMED = new HashSet<>();

    private final BoundVex vex;

    @Nullable
    private IronGolem golem;

    private ItemStack held = ItemStack.EMPTY;
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
        if (vex.tickCount % SCAN_INTERVAL != 0 || vex.getTarget() != null) {
            return false;
        }
        ServerPlayer owner = vex.summoner().orElse(null);
        if (owner == null) {
            return false;
        }
        golem = vex.level()
                .getEntitiesOfClass(IronGolem.class, vex.guardArea(), candidate -> needsRepair(owner, candidate)
                        && !CLAIMED.contains(candidate.getUUID()))
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

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        if (golem == null) {
            return;
        }
        CLAIMED.add(golem.getUUID());
        held = vex.getMainHandItem().copy();
        vex.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT));
        ticksSinceTap = 0;
    }

    @Override
    public boolean canContinueToUse() {
        if (golem == null || vex.getTarget() != null) {
            return false;
        }
        ServerPlayer owner = vex.summoner().orElse(null);
        return owner != null && needsRepair(owner, golem) && vex.guardArea().contains(golem.position());
    }

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
            CLAIMED.remove(golem.getUUID());
        }
        vex.setItemSlot(EquipmentSlot.MAINHAND, held);
        held = ItemStack.EMPTY;
        golem = null;
    }
}
```

In `BoundVex.registerGoals`, add before the `FloatGoal` line (two at 0) and after the `ChargeGoal` line (the job):

```java
        goalSelector.addGoal(0, new FleeCreeperGoal(this));
        goalSelector.addGoal(0, new DodgeGolemGoal(this));
```

```java
        goalSelector.addGoal(2, new RepairGolemGoal(this));
```

so the final order reads 0 flee, 0 dodge, 0 float, 2 repair, 3 charge, 4 follow, 5 wander, 9 and 10 look. In `BoundVexEvents.onServerStopped`, add `RepairGolemGoal.clearClaims();`.

`AABB.contains(Vec3)` exists on `AABB`; if the 26.3 name differs, use `contains(double, double, double)`.

- [ ] **Step 2: The GameTests**

In `BoundVexGameTests.registerAll`, add:

```java
        FangGameTests.addPadded(SHARED, "bound_vex_flees_a_hissing_creeper", BoundVexGameTests::fleesAHissingCreeper);
        FangGameTests.addPadded(SHARED, "bound_vex_dodges_a_golems_swing", BoundVexGameTests::dodgesAGolemsSwing);
        FangGameTests.addPadded(SHARED, "bound_vex_repairs_a_hurt_ally_golem", BoundVexGameTests::repairsAHurtAllyGolem);
```

and the tests (imports: `IronGolem`, `CompoundTag`, `TagValueInput`, `ProblemReporter`, `Blocks`, `Items`):

```java
    /** The flee goal's safe distance (spec §3.5), repeated because the goal is package-private in {@code evoking}. */
    private static final double SAFE_DISTANCE = 8.0;

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
                        vex.distanceTo(player) <= ORBIT_MAX + 1.0, "back with the summoner once the creeper calms"))
                .thenSucceed();
    }

    /**
     * When an iron golem two blocks from an idle Bound Vex swings while facing it, should dart about three blocks
     * sideways (perpendicular to the golem's look) within eight ticks and not away from it.
     *
     * <p>Method under test: {@code DodgeGolemGoal} (package-private), through the goal selector
     */
    static void dodgesAGolemsSwing(GameTestHelper helper) {
        // Arrange: the golem stands on stone (the padded box is air), looks east; the vex hovers two blocks east of it,
        // three blocks from the summoner so the follow goal is idle
        helper.setBlock(FEET.east(3).below(), Blocks.STONE);
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        IronGolem golem = GolemGameTests.golem(helper, FEET.east(3));
        golem.setYRot(-90.0F);
        golem.setYHeadRot(-90.0F);
        golem.yBodyRot = -90.0F;
        BoundVex vex = boundVex(helper, FEET.east(5), player);
        Vec3 before = vex.position();

        // Act
        golem.handleEntityEvent((byte) 4);

        // Assert
        helper.assertTrue(golem.getAttackAnimationTick() > 0, "setup: the golem is mid-swing");
        helper.runAfterDelay(8, () -> {
            Vec3 moved = vex.position().subtract(before);
            helper.assertTrue(Math.abs(moved.z) >= 1.0, "darted sideways (north or south), got dz " + moved.z);
            helper.assertTrue(Math.abs(moved.x) < 1.5, "not away from the golem, got dx " + moved.x);
            helper.succeed();
        });
    }

    /**
     * When an ally golem at half health stands in the guard area of two idle Bound Vexes, should have exactly one vex
     * take it (holding an iron ingot while the other keeps its sword), heal it by 25 on the first tap and by 25 more
     * thirty ticks later, and give the vex its sword back once the golem is full.
     *
     * <p>Method under test: {@code RepairGolemGoal} (package-private), through the goal selector
     */
    static void repairsAHurtAllyGolem(GameTestHelper helper) {
        // Arrange
        helper.setBlock(FEET.east(3).below(), Blocks.STONE);
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        IronGolem golem = GolemGameTests.golem(helper, FEET.east(3), 50.0F);
        BoundVex first = boundVex(helper, FEET.east(3).above(2), player);
        BoundVex second = boundVex(helper, FEET.east(2).above(2), player);
        List<BoundVex> vexes = List.of(first, second);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(golem.getHealth() >= 75.0F, "one tap: 25 healed"))
                .thenExecute(() -> {
                    long ingots = vexes.stream().filter(vex -> vex.getMainHandItem().is(Items.IRON_INGOT)).count();
                    long swords = vexes.stream().filter(vex -> vex.getMainHandItem().is(Items.IRON_SWORD)).count();
                    helper.assertValueEqual(ingots, 1L, "one vex holds the ingot");
                    helper.assertValueEqual(swords, 1L, "the other keeps its sword");
                    helper.assertTrue(golem.getHealth() < 100.0F, "setup: not full after one tap");
                })
                .thenWaitUntil(() -> helper.assertValueEqual(golem.getHealth(), 100.0F, "full after the second tap"))
                .thenWaitUntil(() -> helper.assertTrue(
                        vexes.stream().allMatch(vex -> vex.getMainHandItem().is(Items.IRON_SWORD)),
                        "both hold swords again once the golem is full"))
                .thenSucceed();
    }
```

`helper.assertValueEqual(golem.getHealth(), 100.0F, …)` compares boxed floats with `equals`; `heal` clamps to the max, so the value is exactly `100.0F`. If the sequence's `thenWaitUntil` for 75 fires before the first tap because the vex spawned out of reach, that is a real finding: the vex at two blocks above the golem's feet is 0.6 blocks from its chest.

- [ ] **Step 3: Format, build, GameTests**

Run: `./gradlew spotlessApply --console=plain`, then the build recipe. Expected: `exit=0`.
Run `runGameTestServer` in the background. Expected: `exit=0`, `All 183 required tests passed`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/github/willrieske/spellcraft/evoking/FleeCreeperGoal.java src/main/java/io/github/willrieske/spellcraft/evoking/DodgeGolemGoal.java src/main/java/io/github/willrieske/spellcraft/evoking/RepairGolemGoal.java src/main/java/io/github/willrieske/spellcraft/evoking/BoundVex.java src/main/java/io/github/willrieske/spellcraft/evoking/BoundVexEvents.java src/main/java/io/github/willrieske/spellcraft/gametest/BoundVexGameTests.java
```

```bash
git commit -m "feat(evoking): Bound Vexes flee a hissing creeper, dodge a golem's swing and repair hurt golems"
```

Then `git log -1 --oneline` shows the commit.

---

### Task 7: Docs, playtest items, mutation testing

**Files:**

- Modify: `README.md:15-24` (the Evoking bullet)
- Modify: `docs/playtest-checklist.md:135` (the V item of the Evoking section) and a new "Evoking II (Part 2)" section before "## Real profile"
- Modify: `docs/mutation-testing.md` (one new row in the PIT table)
- Possibly modify: a `src/test/.../spell/rules/*Test.java` file, if a PIT survivor needs a new test (committed separately)
- Not modified: `src/main/templates/META-INF/neoforge.mods.toml` (no new spell), `CLAUDE.md`, `gradle.properties` (no version bump; §12 bumps once after Part 7)

**Interfaces:**

- Consumes: everything above.
- Produces: the docs Will reads.

- [ ] **Step 1: README**

Replace the Strength of Evoking bullet (lines 15–24) with:

```markdown
- <img src="src/main/resources/assets/spellcraft/textures/item/evoking_scroll.png" alt="" width="24" height="24"> **Strength of Evoking**: an evoker's powers, with no time limit. Press **Y** to raise your arms
  and, a second later, make evoker fangs rise under every enemy in the 13×13 square ahead of you (a
  big mob that covers several blocks gets one fang under each block; enemies up to 14 blocks below
  you and 6 above count). An enemy that steps off its block before the bite gets one more fang
  under its new spot. Press **V** to call three **Bound Vexes** to your side. They stay until they
  die, and you can have twelve at once (at twelve, V only says "You have too many vexes"). They fly
  around you, pop up beside you when you get more than 12 blocks away, follow you through portals,
  and fight whatever attacks you, your pets or them, whatever your pets are fighting, and any
  hostile mob that comes within four blocks of them (not creepers, unless one attacks first); your
  tamed wolves join their fights. Click a vex with an empty hand to make it stay where it is; click
  again to make it follow. A vex flies away from a hissing creeper, darts out of an iron golem's
  swing, and patches up a hurt iron golem with an iron ingot. Fangs and vexes spare you and your
  allies (your team, your pets, your own vexes, villagers, wandering traders, and iron golems that
  are not mad at you) and hurt everything else. You, your pets, your fangs and your vexes cannot
  take an iron golem below 25 health; when it gets that low, it calms down and stops being mad at
  you. The keys rebind in Controls. Sneak + scroll or milk turns it off.
```

- [ ] **Step 2: Playtest checklist, the Evoking section**

In `docs/playtest-checklist.md`, replace line 135 (the V item) with:

```markdown
- [ ] V: the "prepare summon" sound, arms up for five seconds, pale particles at the hands; after a second three Bound Vexes appear 2–3 blocks from you, one block up. They look like vexes, fly around you and ignore you. Tame a wolf: they ignore it. They do not die on their own (watch one for three minutes).
```

- [ ] **Step 3: Playtest checklist, the new section**

Insert before "## Real profile":

```markdown
## Evoking II (Part 2)

Survival world, open ground, a village nearby if you can, with Strength of Evoking on. The vexes in the Strength of Evoking items above are Bound Vexes now; these items add to them.

- [ ] Press V four times (wait for each cooldown): 3, 6, 9, 12 vexes. A fifth press: "You have too many vexes" above the hotbar, arms stay down, no sound; kill one vex and V works again at once (no cooldown was spent on the refusal). With ten vexes, V summons two.
- [ ] Walk and sprint around: the vexes keep up and hover 2–4 blocks from you. `/tp @s ~15 ~ ~`: they pop up beside you within a second.
- [ ] Right-click a vex with an empty hand (not sneaking): the vex's sound and your arm swings; it stops following and drifts around that spot. Walk 20 blocks away: it stays. Right-click it again: it follows. Right-click while holding something, or while sneaking: nothing. A friend's click (LAN): nothing.
- [ ] Walk through a nether portal with vexes near you: they arrive beside you in the Nether, still yours (a click still toggles stay). A staying vex stays behind in the Overworld.
- [ ] Log out and back in: the vexes are where you left them, still yours. Quit to title and reopen the world: same.
- [ ] A zombie attacks you: the vexes go for it. Hit a cow yourself: they go for the cow. A creeper walks up to you: ignored until it hurts you or a vex, then they attack it. A zombie attacks your wolf or a village golem: the vexes go for it.
- [ ] Your tamed wolf (standing, no target) joins when a vex picks a target; a sitting wolf stays put.
- [ ] Light a creeper's fuse near your vexes (flint and steel, then back off): they fly straight away from it to about eight blocks and hold; after it explodes (or calms) they come back to you.
- [ ] Make a village golem mad at you and let it swing at a vex (stand so the vex is between you): the vex darts sideways out of the swing. The vexes fighting it stop at 25 health and it calms down (Part 1).
- [ ] Hurt a calm golem without angering it (`/damage @e[type=iron_golem,limit=1,sort=nearest] 40`): one vex flies to its chest holding an iron ingot and taps it every 1.5 seconds with the repair sound (anvil-like clank) until it is full, then holds its sword again; a second vex nearby does not join the repair.
- [ ] `/summon spellcraft:bound_vex`: a vex that hovers and fights nothing, even with a zombie next to it; its name reads "Bound Vex" (F3 + B shows the id; `/kill` it and the death message names it).
- [ ] On Peaceful: existing Bound Vexes vanish (vanilla's monster rule); V says "Vexes cannot be summoned on Peaceful". Back on Normal, V works.
- [ ] Known until Part 3: a vex that is unloaded (you leave the area) while repairing a golem keeps the ingot in its hand.
```

- [ ] **Step 4: Mutation testing**

Run: `./gradlew pitest --console=plain > build/pitest.log 2>&1; echo "exit=$?"; grep -n "Generated\|mutations\|Line Coverage\|Mutation score\|killed" build/pitest.log | tail -10`
Expected: `exit=0`. Read the totals and the survivors in `VexCap` and `GuardArea` from `build/reports/pitest/index.html` (or the log summary). If a `VexCap` or `GuardArea` mutant survives and is not equivalent, add the unit test that kills it (same style as Task 2), run `./gradlew spotlessApply --console=plain`, commit it on its own as `test(rules): kill the <Class> <mutator> survivor` (stage, then commit, as two calls), then re-run `pitest` and record the final numbers. `VexRing`'s `lifeTicks` mutants are gone with the method.

Add a row to the PIT table in `docs/mutation-testing.md` (the date is the run's date):

```markdown
| <run date> (Evoking II Part 2) | <score>% (<killed>/<total> killed; from 98%, 247/251, on 2026-11-08) | <line>% (<covered>/<lines>) | <"None new in `spell.rules` (every `VexCap` and `GuardArea` mutant was killed; `VexRing` lost its `lifeTicks` mutants with the life limit)" or each survivor with its file:line, mutator and why it is equivalent>. The same four `UpsideDownRollState` non-killed mutants as above, unchanged, all justified in the first row. |
```

- [ ] **Step 5: Lint the docs**

Run: `pre-commit run markdownlint-cli2 --files README.md docs/playtest-checklist.md docs/mutation-testing.md`
Expected: `Passed`.

- [ ] **Step 6: Closing sweep**

Run: `grep -rn "30–120\|30-120\|limited life\|life_ticks\|lifeTicks\|five blocks\|AGGRO_RANGE\|VexSummons.isPrey\|install(" README.md docs/api-notes.md docs/playtest-checklist.md docs/mutation-testing.md src/main/java src/test/java --include=*.md --include=*.java | grep -v "docs/superpowers"`
Read every hit and fix any statement Tasks 3–6 made false (a vex that dies after 30–120 s, a five-block aggro range, a goal installed on join). Hits inside api-notes §19 describing vanilla's evoker are history, not statements about the mod: leave them. Spec files under `docs/superpowers` are out of scope for this task.

- [ ] **Step 7: Commit**

```bash
git add README.md docs/playtest-checklist.md docs/mutation-testing.md
```

```bash
git commit -m "docs: describe Part 2 of Evoking II (Bound Vexes: no timer, cap of twelve, follow, stay, fights, repair)"
```

Then `git log -1 --oneline` shows the commit. If the sweep changed a Java Javadoc, stage and commit that file first as `docs(java): fix Javadoc made stale by Evoking II Part 2`.

---

## Self-review (done while writing)

- **Spec coverage.** §3.1 mob, registration, no egg/spawn/loot, no timer, summoner, synced `STAYING`, renderer, `VexSummons.summon` → Task 3 (Divergences 1–3, 9, 11–13); `MobCategory.MONSTER` → Task 3. §3.2 cap → Task 2 (`VexCap`), Task 3 (`countOwnedBy`, `CAPPED`, the notice, the top-up; `capRefusesAtTwelve`, `capTopsUpToTwelve`). §3.3 goals and priorities → Tasks 3–6 (`registerGoals`; Divergences 4, 7); follow/orbit/teleport → Task 4 (`followFliesIntoTheOrbit`, `followTeleportsFromThirteen`); stay toggle and wander → Tasks 3–4 (`stayToggle`, `stayingHoldsItsPost`); portals → Task 4 (`portalFollow`; Divergence 5); logout/death → nothing to build (`summoner()` is empty, the goals idle; `idlesWithoutASummoner`, `noSummonerNoTarget`). §3.4 `isPrey`, the three target goals, `canContinueToUse`, wolves join, mercy through the mark → Task 5 (every `bound_vex_*` targeting test; Divergence 6), `GuardArea` → Task 2. §3.5 → Task 6 (`fleesAHissingCreeper`). §3.6 golems never target Bound Vexes → Part 1's `GolemMercy` (the mark); dodge → Task 6 (`dodgesAGolemsSwing`). §3.7 → Task 6 (`repairsAHurtAllyGolem`; Divergences 7, 8). §10 no summoner, dead summoner, cap across dimensions, Peaceful removal, `/summon` → Tasks 3–5. §11 pure core `VexCap`, `GuardArea` → Task 2; GameTests for Part 2 → all named above; `runData` → Task 3. §12 docs → Tasks 1, 7; `mod_version` untouched; mod description unchanged (no new spell). §13 cap counting, partial summon, mob category, wolves join, dodge always, repair scope → Tasks 2–6.
- **Placeholders.** None; the mutation row's angle-bracket fields are filled from the run.
- **Type consistency.** `BoundVex.isPrey(ServerLevel, UUID, LivingEntity)` static and `isPrey(LivingEntity)`; `keepsTarget(@Nullable LivingEntity)`, `guardArea()`, `summoner()` → `Optional<ServerPlayer>`, `isSummonedBy(UUID)`, `countOwnedBy(MinecraftServer, UUID)` are defined in Task 3 and used as such in Tasks 4–6; `VexSummons.summon(ServerLevel, ServerPlayer, int)` matches `EvokingCaster.perform`'s call; `VexCap.toSummon(int)` matches `request`'s call; the GameTest helpers `boundVex`, `parked`, `husk`, `summoner`, `pet`, `targets`, `patientCreeper` are defined before they are used; `GolemGameTests.golem(helper, pos)` and `golem(helper, pos, health)` exist on the base branch.

## Divergence index

<!-- generated by plan_map.py --write-index; do not edit -->

*No divergences recorded.*

## Resume State

- Last completed task: None
- Commit SHA: none yet (plan written, not started)
- Checkboxes: 0/46 ticked
- Next task: Task 1
- Known-failing tests / caveats: none yet; Part 1 is on feat/evoking-ii-part-1 (draft PR #3), this branch stacks on it
