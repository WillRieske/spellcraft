# Evoking II Part 7: Casters, Mirror, Ghostrail, Scroll Mobs and Villager Ghosts Implementation Plan

**Status:** implemented 2026-11-12

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One Bound Vex in three is a robed **Caster** that never fights and casts the two new spells on you; **Mirror** (13 copies of you, 6 minutes) and **Ghostrail** (fly through walls unhurt, 4 minutes, a white-smoke trail) are new scrolls anyone can scribe and read; evokers, vindicators and pillagers sometimes carry one and use it on themselves; and a villager a monster kills leaves a **ghost** that comes to help a player in need.

**Architecture:** Six pure classes in `spell.rules` (`GhostrailDuration`, `GrantedAbilities`, `GhostRecords`, `GhostNeed`, `MirrorRule`, `CasterRules`) hold the rules and the numbers of the spells and of the Caster and the ghost's need; a few numbers live next to the code that uses them (the scroll mobs' odds, drop chance and Ghostrail cooldown in `ScrollMobs`, the ghost's 48-block reach, 64-block leash and 200-tick quiet limit in `VillagerGhosts` and `VillagerGhost`, the ghost's 16-block "enemy near" radius reused from `CasterRules.RANGE`), and `ScrollCasting` is the one place for what every cast that is not a player's hand shares (durations, cooldowns, the wind-up's look, the landing). The spells follow the framework recipe (`SpellDeclarations` + `Spells` + effect + scroll) and put what a spell does in its **effect** (`MirrorEffect.onEffectAdded` spawns the images; `Ghostrail` is driven per tick), so a scroll, a Caster, a mob, a ghost and `/effect give` all behave the same through one `Spells.cast`. `MirrorImage` and `VillagerGhost` are non-saved-AI `LivingEntity` subclasses; the client draws images and the ghost with stand-in entities and a translucent villager renderer. One common Mixin (`PlayerGhostrailMixin`) lets a Ghostrail player pass through blocks, because no event reaches `Player.noPhysics`.

**Tech Stack:** Minecraft 26.3, NeoForge 26.3.0.23-beta, Java 25, JUnit 5 + jqwik + AssertJ (pure core), NeoForge GameTests (server rules), Python 3 stdlib + `unittest` (the icon and robe scripts).

**Spec:** `2026-11-10-evoking-ii-parts-6-7-design.md` §6–§11 (Part 7), with the rows of §12 that name Part 7, §13, §14 and the glossary. Parent: `2026-11-07-evoking-ii-design.md` §9 (the new spec wins where they differ).

**Builds on:** Part 6 (branch `feat/evoking-ii-part-6`, merged into `main`): `Dice` (named scriptable instances; `RAID_KIT` today), `TeamMoves.isFree/freeVexes/start/clear`, `TeamMoveKits.kit/raid`, `RaidMove.tick`, `Jobs.busy`; and Parts 2–5: `BoundVex` (conjure/dismiss, `huntable`, `isPrey`, `inGuardArea`, `summoner`), `VexSummons`, `VexRing`, `VexRescues`, `StandableSpots`, `Allies`, `GolemMercy`, `CastingEffect`/`ModParticles`, `InfinityAbilitySync`, `Spells`/`SpellScrollItem`, and the client `BoundVexRenderer`, `CastingState`, `CastingParticles`.

## Global Constraints

- Server owns state. **No new packet** (not even clientbound): the client reads vanilla-synced state only (effects, the effect-particle list, entity data). **One Mixin (`PlayerGhostrailMixin`, Task 6) and no access-transformer line**; no other Mixin. CLAUDE.md says "Mixins only where no event exists": Part 7 needs a Mixin only because `Player.tick` assigns `noPhysics` from `isSpectator()` and NeoForge has no event for it (api-notes §26 records the check). If a step seems to need a second Mixin, an AT line or a packet, stop and report.
- Pure core (`spell.rules`): no `net.minecraft`/NeoForge imports (ArchUnit rule 2). TDD (RED test first); jqwik properties for invariants; `./gradlew pitest` after changing it and update `docs/mutation-testing.md` (Task 15).
- Error Prone runs with `-Werror`; suppress only with `@SuppressWarnings("<Check>")` plus a comment saying why (Mixin handlers: `UnusedMethod`; `Abilities#mayfly`: `deprecation`; `EnumOrdinal` where an ordinal is the wire value).
- Compare entities with `equals`, never `==`/`!=` (`Entity` overrides `equals`; `ReferenceEquality` fails the build).
- Javadoc on every class, method, field and constant (private ones too), lines <= 120 columns; American spelling; plain words. `spotlessApply` does not wrap comments, so re-wrap any comment line of a snippet that runs past 120 columns before you commit it. The snippets leave some Javadoc out (enum constants, record components with `@param`, constructors, overrides, event-handler `@param`s, private fields and constants, test helpers); it is still required in the code you commit. Every test (JUnit and GameTest) gets a When/should Javadoc and a `<p>Method under test:` line.
- Tests follow `~/.claude/development-methodology.md` § Conventions, *Unit test style* (When/should docs, `// Arrange` / `// Act` / `// Assert`, `@Nested` per method, one-line form for one-statement tests). Mirror `TeamMoveRulesTest` / `TeamMoveRulesPropertiesTest` (unit and properties) and `BoundVexTeamMoveRosterGameTests` (GameTests). Python tests: a docstring `When ..., <function> should ....` and a `Function under test:` line.
- Never claim a visual or feel behavior works: add playtest items (Task 15) instead. Visual items are Will's.
- **Never bump the version, never create a tag.** (The release to 1.3.0 happens after the branch review, outside this plan.) No branch switching, no merging, no pushing.
- Never run `git checkout`/`git restore`/`git stash` on a file with uncommitted work. Stage and commit in two separate commands. Conventional commit messages (gitlint), no attribution lines. Commit before dispatching a subagent in the same checkout.
- Every vanilla or NeoForge call is checked against `C:\dev\spellcraft\build\mc-src` and `build\nf-src` (extract per CLAUDE.md "Minecraft sources" if missing) before use. `docs/api-notes.md` §26 (Task 1) records what was verified; where this plan's snippet disagrees with the sources, the sources win and the as-built note says so. The snippets leave out imports, and some private Javadoc, to stay short; the code you commit has both, and removes imports a change leaves unused.
- GameTests: inner loop `./gradlew runGameTestServer "-PgameTests=spellcraft:<prefix>_*"`; before each task's commit, the full `./gradlew runGameTestServer` **twice** (flakiness), expecting `All M required tests passed` both times, where M is the previous task's total plus the tests this task registers (count the new `addPadded(`/`add(` lines in `git diff --cached`; write M in the commit's as-built note). The baseline is **650** (Part 6's final total); Task 1 re-measures it and writes it into the last bullet of `docs/api-notes.md` §26. The planned chain, from that baseline, is 650, 657 (Task 3), 676, 683, 694, 713, 728, 743, 757, 767, 782 (Task 12); Tasks 1, 2, 13, 14 and 15 add no GameTest and say "the total the previous task recorded". Each task's "Expected" number assumes the counts in that task's registration list; recount if you add or drop a test.
- GameTest practices that already cost time here: place mobs by absolute `setPos` with no gravity; never `GameTestHelper.onEachTick` (use `BoundVexTestSupport.everyTick`); `thenWaitUntil(N, …)` demands an exact delay, so wait with `thenWaitUntil(() -> …)` or `thenExecuteAfter`; a mock player is never ticked or moved by the server (call `Ghostrail.tick(player)`, `VillagerGhosts.step(player)`, `TeamMoves.tick(player)` by hand, or every tick through `BoundVexTestSupport.everyTick`; reach the Mixin through `ServerPlayer.doTick()`, which is public); a mock player's effects never count down (end an effect with `removeEffect`); void damage is tested with `hurtServer(level, level.damageSources().fellOutOfWorld(), amount)`. Limits and pauses count game ticks.
- **The three Part 6 GameTest traps still apply:** (1) a vex within 16 blocks of its summoner follows it through a dimension change, so create vexes after `BoundVexWorldTestSupport.room(...)`; (2) a mob with no AI never moves from knockback, so assert velocity, not position; (3) the framework's barrier shell (min-1 to max+1 of the 3x3x3 box) blocks line of sight, so a test that needs `hasLineOfSight` keeps everyone above it (`TeamMoveTestSupport.hangPoint`, y >= 5).
- **Registering a GameTest class.** Each class has `static void registerAll()` with one line per test, `FangGameTests.addPadded(SHARED, "<prefix>_<name>", Class::method);` (`addPadded(SHARED, name, 200, Class::method)` for a sequence test that needs more than the default body ticks; another environment name for a test that mutates global state), and its call goes into the static block of `SpellcraftGameTests` after the previous Part 7 class (the first goes after `BoundVexTeamMoveRaidGameTests.registerAll()`). A test's registered name is its prefix plus the method name in snake case (Tasks 8-10 follow this exactly; Tasks 3-7 and 11-12 use longer descriptive names, so always select by the prefix, and recount if a name changes). Lang keys: append each task's keys directly after the previous task's in `en_us.json`, and spell every key in full.
- **Global dice and batches.** Batches (one per environment name) run one after another; tests inside one batch run at the same time. `Dice` instances are global, so a test that scripts one runs in the environment named for it (`caster_dice`, `scroll_mob_dice`, `mirror_dice`; a statistical test with real dice in `caster_dice_real`), scripts **immediately before the one synchronous call that consumes the roll** (`VexSummons.summon`, `finalizeSpawn`, `Mob.setTarget`), and unscripts in a `finally` (`Dice.unscriptAll()`).
- **Existing tests that summon through `VexSummons.summon` (the V key) now roll the Caster dice for real** (1 in 3). Task 8 greps for them and scripts a plain vex wherever a test relies on a fighting vex.
- Numbers below are requirements copied from the spec. Do not round or "improve" them.

## File Structure

| File | Responsibility |
|---|---|
| `spell/rules/GhostrailDuration.java`, `GrantedAbilities.java`, `GhostRecords.java`, `GhostNeed.java`, `MirrorRule.java`, `CasterRules.java` (new) | The pure core (spec §6.2, §7.2, §7.3, §8, §9, §11) |
| `spell/rules/InfinityAbilities.java` (deleted) and its two tests | Replaced by `GrantedAbilities` (the Infinity rows move to `GrantedAbilitiesTest`) |
| `spell/rules/SpellDeclarations.java`, `spell/Spells.java`, `spell/ModItems.java`, `spell/SpellScrollItem.java` | `MIRROR` and `GHOSTRAIL`; `Spells.cast(LivingEntity, Spell, int)` and `Spells.refusedFor(LivingEntity, Spell)` (the one creative refusal for casters that are not scrolls) |
| `effect/ModEffects.java`, `MirrorEffect.java`, `GhostrailEffect.java` (new) | The two effects and the `casting_scroll` marker effect |
| `particle/ModParticles.java` | `CASTING_SCROLL` |
| `evoking/Dice.java` | `CASTER_ROLL`, `SCROLL_MOB`, `MIRROR_CONFUSION`, `unscriptAll()` |
| `evoking/MirrorImage.java`, `MirrorImages.java` (new) | The image entity; spawning, poofing, confusion (events) |
| `evoking/Ghostrail.java`, `EvokingAttachments.java` (new) | The per-tick driver, damage cancel, trail, end; the saved marks and cooldowns |
| `mixin/PlayerGhostrailMixin.java`, `resources/spellcraft.mixins.json` | The one Mixin |
| `infinity/AbilitySync.java` (renamed from `InfinityAbilitySync`), `infinity/InfinityAttachments.java` | The shared abilities sync; the Ghostrail granted mark |
| `evoking/BoundVex.java`, `VexSummons.java`, `CasterGoal.java`, `CasterSpellGoal.java`, `ScrollCasting.java` (new), `TeamMoves.java`, `TeamMoveKits.java`, `TeamMoveTriggers.java`, `RaidMove.java`, `VexRescues.java` | The Caster; the shared cast pieces; the Raid's Caster rules; no rescue for a Ghostrail carrier |
| `evoking/ScrollMobs.java`, `GhostrailChaseGoal.java` (new) | Mobs that carry scrolls |
| `evoking/VillagerGhost.java`, `VillagerGhosts.java` (new), `ModEntities.java` | The ghost, its records, its spawner |
| `spell/Allies.java`, `spell/GolemMercy.java` | `Allies.isPlayersSide` moves out of `GolemMercy` |
| `client/evoking/BoundVexModel.java`, `BoundVexRobeLayer.java`, `CastLook.java`, `MirrorImageRenderer.java`, `VillagerGhostRenderer.java`, `VillagerGhostProfessionLayer.java` (new); `BoundVexRenderer`, `BoundVexRenderState`, `BoundVexRenderers`, `CastingState`, `CastingParticles` | The look of all of it |
| `scripts/robe_texture.py`, `scripts/test_robe_texture.py` (new), `scripts/spell_icons.py`, `scripts/test_spell_icons.py` | The robe texture; the two new glyphs |
| `assets/spellcraft/{lang,items,models,textures}` | Names, item definitions, icons, robe texture |
| `gametest/ScrollTestSupport.java` (new) | Shared helpers: scenes, hostiles, ticking |
| `gametest/{SpellScroll,Mirror,MirrorConfusion,GhostrailFlight,Ghostrail,Caster,CasterSpell,ScrollMob,VillagerGhost}GameTests.java` (new), `InfinityAbilityGameTests`, `ScribingGameTests`, `BoundVexLavaGameTests`, `BoundVexDrowningGameTests`, `BoundVexVoidGameTests` (edited) | Part 7 GameTests (mob-carrier Ghostrail tests live in `GhostrailGameTests`) |
| `src/test/…/SpellAssetsTest.java`, `SpellDeclarationsTest`, `CastRulesTest` | The assets and rules of the two spells |
| `README.md`, `CLAUDE.md`, `neoforge.mods.toml`, `docs/api-notes.md` §26, `docs/playtest-checklist.md`, `docs/mutation-testing.md`, the parent spec's inline notes | Docs |

All Java paths are under `src/main/java/io/github/willrieske/spellcraft/`; unit tests under `src/test/java/io/github/willrieske/spellcraft/`.

---

### Task 1: API notes §26 and the baseline

**Files:**

- Modify: `docs/api-notes.md` (new §26)

- [x] **Step 1: Take the baseline**

Check `build/mc-src` and `build/nf-src` exist (extract them per CLAUDE.md "Minecraft sources" if not). Run `./gradlew build` and then `./gradlew runGameTestServer` once, one after the other in a single background command (two Gradle runs at once block on the build lock and can fail on the `run/` directory; a cold build takes 5-15 min; `build` also warms the Gradle cache so the commit gate's own `build` fits in 300 s). Expected: green; note the `All N required tests passed` number `N` (650 expected). Write it into the last bullet of §26 (Step 2) before the commit.

- [x] **Step 2: Verify the facts and write `docs/api-notes.md` §26**

Check each line below in the sources (`Grep` the quoted names in `build/mc-src` and `build/nf-src`; keep or correct the cited line). They were read once while writing this plan; confirm them. If a line is wrong, write what the sources say and tell the coordinator which later task is affected. Every fact below was checked once already; no `(verify)` marker may reach the committed note, so if a check disagrees, write the corrected fact in the note's own words.

```markdown
## 26. Evoking II, Part 7: Casters, Mirror, Ghostrail, scroll mobs, villager ghosts (Evoking II Part 7, Task 1)

Verified against `build/mc-src` (vanilla with NeoForge patches) and `build/nf-src`; paths are under
`net/minecraft/` and `net/neoforged/neoforge/` respectively.

- **When a spell's effect "starts".** `MobEffect.onEffectAdded(LivingEntity, int)` (`world/effect/MobEffect.java:108`; the
  default plays the effect's sound, so an override calls `super`) is reached from `LivingEntity.addEffect`
  (`world/entity/LivingEntity.java:1050-1057`) only when the entity had no such effect (`effect == null`), after the instance is
  already in `activeEffects`. An existing effect goes through `effect.update` and `onEffectUpdated` instead. `forceAddEffect`
  (`:1085`) and a reload from a save (`:832-836`, `activeEffects.put`) never call the `MobEffect` hook. So a scroll, a Caster, a mob
  and `/effect give` all start Mirror through `addEffect`, and a relog mid-effect does not spawn a second set. The client never reaches
  that hook: `ClientPacketListener.java:1832` calls `forceAddEffect`, which reaches only `LivingEntity.onEffectAdded(instance, source)`
  (`LivingEntity.java:1116`, a server-only body). The `isClientSide()` test in `MirrorEffect.onEffectAdded` is therefore a harmless
  guard, not a necessity; build no client behavior on a client-side hook call.
- **Death and removal.** A dead *player's* effects are NOT cleared (they stay until the respawn), so an effect-based "the caster is
  gone" check never fires for a player on the death screen; Mirror needs a `LivingDeathEvent` handler for players. A *mob's*
  `LivingEntity.remove(reason)` (`LivingEntity.java:742-745`) calls `triggerOnDeathMobEffects` (`:760`, which calls
  `MobEffect.onMobRemoved(ServerLevel, LivingEntity, int, Entity.RemovalReason)`, `MobEffect.java:113`, then clears the effects) only for
  `KILLED` and `DISCARDED`; `UNLOADED_TO_CHUNK` (`Entity.java:4421`) skips it. A removed mob is also no longer found by
  `server.getEntities().get(uuid)`.
- **Player.noPhysics.** `Player.tick()` (`world/entity/player/Player.java:241`) runs `net.neoforged.neoforge.event.EventHooks.firePlayerTickPre(this)`
  (`:242`) and then `this.noPhysics = this.isSpectator();` (`:243`, the spec's number is right). That is the first `isSpectator()` call
  in `tick()` (ordinal 0; the second is the `if (this.isSpectator() || this.isPassenger())` on `:244`). `Player.isSpectator()` is
  concrete (`:1721`, `return this.gameMode() == GameType.SPECTATOR`), and the Mixin owner is `Player`. `Entity.noPhysics`
  is a public field (`world/entity/Entity.java:253`). `Entity.isInWall()` returns false while `noPhysics` (`:2321-2323`), so a Ghostrail
  carrier is never "in a wall"; the Casters and ghosts test the effect first. `Entity.isAffectedByBlocks()` (`:1030`) is
  `!noPhysics`, so a Ghostrail carrier also triggers no block effects (portals, pressure plates, cobweb, honey, powder snow); this is
  a playtest item (Task 15).
- **Target changes.** `LivingChangeTargetEvent(LivingEntity, @Nullable LivingEntity, ILivingTargetType)`
  (`event/entity/living/LivingChangeTargetEvent.java:40`): `getNewAboutToBeSetTarget()` `:51`, `setNewAboutToBeSetTarget` `:60`,
  `getTargetType()` `:67`; the two stock types are `LivingTargetType.MOB_TARGET` (`Mob.setTarget`) and `BEHAVIOR_TARGET`
  (`:91-100`). Cancelling keeps the old target. `TargetGoal` re-sets its target every tick (`TargetGoal.java:70`), so Mirror
  rolls only when the new target differs from `mob.getTarget()`.
- **Spawn equipment.** `FinalizeSpawnEvent(Mob, …)` (`event/entity/living/FinalizeSpawnEvent.java:55`) fires before `Mob.finalizeSpawn`
  runs vanilla's `populateDefaultEquipmentSlots` (`world/entity/Mob.java:992`). `Mob.finalizeSpawn` never fills equipment, and `Vindicator`
  and `Pillager` set only `MAINHAND`, so an offhand scroll survives. `Mob.setDropChance(EquipmentSlot, float)` (`Mob.java:1137`).
  NeoForge's `EventHooks.finalizeMobSpawn` (`EventHooks.java:334`) posts the event; `Mob.finalizeSpawn` is deprecated and does not, so the
  scroll-mob GameTests call the hook (a direct `finalizeSpawn` call fails the `-Werror` build).
  `Mob.dropCustomDeathLoot` (`Mob.java:920-944`) drops a slot's item only when `killedByPlayer || preserved`, with probability
  `dropChance` raised by Looting (`processEquipmentDropChance`), so a 0.5 offhand chance is "half of player kills".
- **Saved data.** `SavedDataType<T>(Identifier, Supplier<T>, Codec<T>)` (`world/level/saveddata/SavedDataType.java:14-19`) and
  `serverLevel.getDataStorage().computeIfAbsent(TYPE)` (`ServerLevel.java:285`, as `Raids.TYPE`, `Raids.java:43`). `setDirty()` marks it for save.
  `VillagerData.CODEC` (`world/entity/npc/villager/VillagerData.java:20`).
- **Entity types.** Vanilla types are `EntityTypes.X` (`world/entity/EntityTypes.java`), e.g. `EntityTypes.MANNEQUIN`.
  `EntityType.Builder.noSave()` (`EntityType.java:609`), `.noLootTable()` `:648`. A `LivingEntity` subclass needs `getMainArm()`
  (`LivingEntity.java:3536`, the only abstract method) and attributes from `EntityAttributeCreationEvent` with
  `LivingEntity.createLivingAttributes()` (`:346`). `Entity.shouldBeSaved()` (`Entity.java:4125`), `isPickable`, `isAttackable` (`:3097`),
  `LivingEntity.isInvulnerableTo(ServerLevel, DamageSource)` (`:4214`). `Entity` has only `getDimensions(Pose)` (`:3848`), which
  `LivingEntity` makes `final` (`LivingEntity.java:3913`); a subclass overrides `LivingEntity.getDefaultDimensions(Pose)` (protected,
  `LivingEntity.java:3917`), never `getDimensions`. `LivingEntity.serverAiStep()` (`:3336`) runs each tick for a server-side non-`Mob`
  living entity: `aiStep` calls it when `isEffectiveAi() && !isClientSide()` (`:3181`), and `isEffectiveAi()` is
  `isLocalInstanceAuthoritative()` (`Entity.java:3754`), true server-side when no passenger controls the entity; `xxa`/`zza`
  (`:257`, `:259`) drive `travel`.
- **Goals.** `GoalSelector.tick` (`world/entity/ai/goal/GoalSelector.java:66-90`) stops a goal that cannot continue or is preempted
  (`stop()`) before it starts the new one (`start()`), so a preempted `CasterSpellGoal.stop()` runs before the Raid goal's `start()`.
- **Damage and effects.** `DamageTypes.GENERIC_KILL` and `FELL_OUT_OF_WORLD` (`world/damagesource/DamageTypes.java:24,58`);
  `LivingIncomingDamageEvent` (`event/entity/living/`). `ServerLevel.sendParticles(T, x, y, z, count, xDist, yDist, zDist, speed)`
  (`ServerLevel.java:1355`). `ColorParticleOption.create(ParticleType<ColorParticleOption>, int rgb)` (`core/particles/ColorParticleOption.java:48`).
  `ParticleTypes.WHITE_SMOKE`, `POOF`, `ENTITY_EFFECT`. `SoundEvents.EVOKER_PREPARE_SUMMON` `:614`, `EVOKER_CAST_SPELL` `:608`, `VEX_DEATH` `:1675`.
  `ServerPlayer.doTick()` is public (`server/level/ServerPlayer.java:668`).
- **Client: the vex.** `VexModel.createBodyLayer()` is `LayerDefinition.create(mesh, 32, 32)`
  (`client/model/monster/vex/VexModel.java:71`; `createBodyLayer` is `:33`): the robe texture is 32 x 32. The arms (`right_arm`, `left_arm`) are private fields; reach
  them by `root().getChild("body").getChild("right_arm")`. `LivingEntityRenderer.model` is protected and not final
  (`client/renderer/entity/LivingEntityRenderer.java:45`), so `BoundVexRenderer` can swap in a `BoundVexModel` after `super(context)`.
  `VexRenderer` builds its model in `super(...)` (`VexRenderer.java:22`). `RenderTypes.entityCutout(Identifier)` `:604`,
  `entityTranslucent(Identifier)` `:652` (`client/renderer/rendertype/RenderTypes.java`); there is no `entityCutoutNoCull`; `entityCutoutZOffset(Identifier)` `:612` draws an overlay without z-fighting.
  `LivingEntityRenderer.getModelTint(S)` `:119` and `getRenderType(S, boolean, boolean, boolean)` `:125` are overridable.
  `RenderLayer.renderColoredCutoutModel` (`RenderLayer.java:39`) always uses `entityCutout`; for alpha use
  `submitNodeCollector.submitModel(model, state, poseStack, RenderTypes.entityTranslucent(texture), light, overlay, argb, null, outline)`.
- **Client: villager.** `VillagerProfessionLayer` (`client/renderer/entity/layers/VillagerProfessionLayer.java`) keeps `getIdentifier`,
  `getHatData` and the caches private/instance-bound and draws with `renderColoredCutoutModel`; the ghost needs its own copy that draws
  translucent. `VillagerRenderer` extends `AgeableMobRenderer<Villager, …>`, which needs a `Villager`, so the ghost renderer extends
  `LivingEntityRenderer<VillagerGhost, VillagerRenderState, VillagerModel>` and fills `state.villagerData` itself.
- **Client: the image stand-in.** `Mannequin` (`world/entity/decoration/Mannequin.java:28`): `EntityTypes.MANNEQUIN.create(level, …)`; its
  profile is a synced `ResolvableProfile` set through `entity.setComponent(DataComponents.PROFILE, profile)` (`Entity.java:4306` →
  `Mannequin.applyImplicitComponent` `:167`); `ResolvableProfile.createResolved(GameProfile)` `:57`, `createUnresolved(UUID)` `:65`;
  `EntityDataSerializers.RESOLVABLE_PROFILE` `:146` and `STRING` `:58`. The description label is drawn through the render state's name
  tag and its below-name line: clear **both** `state.nameTag` and `state.scoreText` (`EntityRenderState.java:33-34`) on the extracted state;
  `EntityRenderer.extractNameTags` sets `scoreText = entity.belowNameDisplay()` within 10 blocks (`EntityRenderer.java:272`) and
  `submitNameDisplay` draws it (`:131-135`). `ClientMannequin.setSkin` runs only inside `ClientMannequin.tick()`, and a stand-in that is
  never added to the level is never ticked, so the stand-in's own skin stays `DEFAULT_SKIN` forever: after `extractEntity`, for an
  `AvatarRenderState` set `state.skin = Minecraft.getInstance().playerSkinRenderCache().getOrDefault(profile).playerSkin()`
  (`PlayerSkinRenderCache.getOrDefault` `:63`, `RenderInfo.playerSkin()` `:104`, `Minecraft.playerSkinRenderCache()` `:3013`; it
  returns the resolved skin or the default on each call, and the dispatcher picks the renderer from `state.skin.model()`). Do not tick the
  stand-in: `LivingEntity.tick` pushes nearby entities client-side. For the copied animation state, `hurtTime`, `hurtDuration`,
  `deathTime`, `yBodyRotO`, `yHeadRotO`, `walkAnimation`, `xOld/yOld/zOld`, `xo/yo/zo` and `tickCount` are public
  (`LivingEntity.java:239-249`, `Entity.java:225-255`); `EntityType.create(Level, EntitySpawnReason)` exists (`EntityType.java:343`),
  as do `EntityTypes.MANNEQUIN` and `EntityTypes.PLAYER`; `Entity.setComponent` is public (`Entity.java:4306`), so no access transformer
  is needed. `EntityRenderDispatcher.extractEntity(entity, partialTicks)` `:128` returns a state; `submit(state, camera, x, y, z, poseStack, collector)`
  `:143` translates by `x, y, z` plus the renderer's offset, so the image renderer, which is already translated to the image, passes 0, 0, 0.
- **Casting look.** `CastingState` reads `LivingEntity.DATA_EFFECT_PARTICLES` (the existing AT line); an effect shows in that list when its
  instance is `visible` and the effect has a particle (`MobEffect` constructor with a `ParticleOptions` function, as `CastingEffect`).
  `ClientLevel.entitiesForRendering()` (`ClientLevel.java:465`) returns `Iterable<Entity>`, so the `CastingParticles` loop over every
  living entity in render range filters with `instanceof LivingEntity`.
- **Test seams.** Batches run one after another; tests inside a batch run at the same time (`SpellcraftGameTests.register`).
- **Baseline.** `./gradlew runGameTestServer`: `All <N> required tests passed` on <date> (Part 6 ended at 650). Every Part 7 gate is this number plus the tests registered so far.
```

- [x] **Step 3: Commit**

```bash
git add docs/api-notes.md
git commit -m "docs(evoking): API notes for Evoking II Part 7"
```

> **As built (`a1532b6`):** matches plan.

---

### Task 2: The pure core

**Files:**

- Create: `spell/rules/GhostrailDuration.java`, `GrantedAbilities.java`, `GhostRecords.java`, `GhostNeed.java`, `MirrorRule.java`, `CasterRules.java`
- Delete: `spell/rules/InfinityAbilities.java`, `src/test/…/spell/rules/InfinityAbilitiesTest.java`, `InfinityAbilitiesPropertiesTest.java` (their rows move)
- Modify: `infinity/InfinityAbilitySync.java` (compiles against `GrantedAbilities`; the full sync rewrite is Task 6)
- Test: `spell/rules/GhostrailDurationTest.java`, `GrantedAbilitiesTest.java`, `GrantedAbilitiesPropertiesTest.java`, `GhostRecordsTest.java`, `GhostRecordsPropertiesTest.java`, `GhostNeedTest.java`, `MirrorRuleTest.java`, `CasterRulesTest.java` (under `src/test/java/…`)

**Interfaces:**

- Produces:
  - `GhostrailDuration`: `enum Kind { PLAYER, BOUND_VEX, HOSTILE_MOB, VILLAGER_GHOST }`; `int PLAYER_TICKS = 4800`, `int HOSTILE_TICKS = 40`, `int INFINITE = -1` (equals `MobEffectInstance.INFINITE_DURATION`; Task 7 asserts it); `static int of(Kind kind)`.
  - `GrantedAbilities`: `record State(PlayerMode mode, boolean infinity, boolean ghostrail, boolean infinityMarked, boolean ghostrailMarked, boolean mayfly, boolean instabuild, boolean flying)`; `record Plan(boolean reset, boolean mayfly, boolean instabuild, boolean flying, boolean infinityMarked, boolean ghostrailMarked)` with `boolean changesAbilities()`; `static Plan decide(State state)`. A `Plan` is applied in this order: if `reset`, reset to the game mode's abilities; then set `mayfly`, `instabuild`, `flying` to true where the plan says; then store the two marks.
  - `GhostRecords`: `int MAX = 32`; `static <T> List<T> add(List<T> records, T record, int max)` (a new list; oldest first; the newest is last).
  - `GhostNeed`: `int CROWD = 3`, `double CROWD_RANGE = 8.0`; `static boolean belowHalf(float health, float maxHealth)`, `belowQuarter(float, float)`, `inNeed(float health, float maxHealth, int hostiles)`.
  - `MirrorRule`: `int IMAGES = 13`, `int CONFUSION_BOUND = 14`, `int LIFE_TICKS = 7200`, `int DURATION_TICKS = 7200`, `double WANDER_RADIUS = 6.0`, `double CONFUSION_RANGE = 16.0`; `static boolean redirects(int roll)` (roll 0 to 13; true below 13).
  - `CasterRules`: `enum Approach { BACK_OFF, CLOSE_IN, HOLD }`; `double KEEP_MIN = 4.0`, `KEEP_MAX = 6.0`, `int WIND_UP_TICKS = 20`, `int MIRROR_COOLDOWN_TICKS = 7200`, `int GHOSTRAIL_COOLDOWN_TICKS = 4800`, `double RANGE = 16.0`, `int MIRROR_HOSTILES = 2`; `static Approach approach(double distance)`; `static boolean wantsMirror(int hostiles)`; `static boolean wantsGhostrail(float health, float maxHealth, boolean inWall, boolean subjectIsSummoner)`; `static boolean ready(long now, long readyAt)`.

`GrantedAbilities` replaces `InfinityAbilities`: its Infinity rows keep their meaning (a row of the old table with `ghostrail` false and `ghostrailMarked` false gives the same action: GRANT is `reset=false, mayfly, instabuild`; REVOKE is `reset=true`; RELEASE is a plan with no change and the mark cleared; NONE is a plan with no change and the mark kept).

- [x] **Step 1: Write the failing tests**

`GhostrailDurationTest`:

```java
class GhostrailDurationTest {
    @Nested
    class Of {
        /**
         * When called for each kind of carrier, of should answer 4800 for players and Bound Vexes, 40 for a hostile
         * mob and the infinite sentinel for a villager ghost.
         *
         * <p>Method under test: {@link GhostrailDuration#of(GhostrailDuration.Kind)}
         */
        @ParameterizedTest
        @CsvSource({"PLAYER,4800", "BOUND_VEX,4800", "HOSTILE_MOB,40", "VILLAGER_GHOST,-1"})
        void followsTheSpec(GhostrailDuration.Kind kind, int expected) {
            assertThat(GhostrailDuration.of(kind)).isEqualTo(expected);
        }
    }

    /**
     * When the constants are read, they should be the spec's numbers: 4800 ticks for a player, 40 for a hostile mob.
     *
     * <p>Method under test: the constants of {@link GhostrailDuration}
     */
    @Test
    void constantsAreTheSpecsNumbers() {
        assertThat(GhostrailDuration.PLAYER_TICKS).isEqualTo(4800);
        assertThat(GhostrailDuration.HOSTILE_TICKS).isEqualTo(40);
    }
}
```

`GhostNeedTest`:

```java
class GhostNeedTest {
    @Nested
    class InNeed {
        /**
         * When health is below half, or three hostiles are close, inNeed should be true; at exactly half with two
         * hostiles it should be false.
         *
         * <p>Method under test: {@link GhostNeed#inNeed(float, float, int)}
         */
        @ParameterizedTest
        @CsvSource({"9.9,20,0,true", "10,20,0,false", "20,20,2,false", "20,20,3,true", "20,20,9,true", "1,20,3,true"})
        void followsTheSpec(float health, float max, int hostiles, boolean expected) {
            assertThat(GhostNeed.inNeed(health, max, hostiles)).isEqualTo(expected);
        }

        /**
         * When the maximum health is not positive, or health or the hostile count is negative, inNeed should refuse.
         *
         * <p>Method under test: {@link GhostNeed#inNeed(float, float, int)}
         */
        @ParameterizedTest
        @CsvSource({"5,0,0", "5,-1,0", "-1,20,0", "5,20,-1"})
        void refusesNonsense(float health, float max, int hostiles) {
            assertThatThrownBy(() -> GhostNeed.inNeed(health, max, hostiles))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class BelowHalf {
        /**
         * When health is below half of the maximum, belowHalf should be true; at exactly half, false.
         *
         * <p>Method under test: {@link GhostNeed#belowHalf(float, float)}
         */
        @ParameterizedTest
        @CsvSource({"9.9,20,true", "10,20,false", "0,20,true", "20,20,false"})
        void followsTheSpec(float health, float max, boolean expected) {
            assertThat(GhostNeed.belowHalf(health, max)).isEqualTo(expected);
        }
    }

    @Nested
    class BelowQuarter {
        /**
         * When health is below a quarter of the maximum, belowQuarter should be true; at exactly a quarter, false.
         *
         * <p>Method under test: {@link GhostNeed#belowQuarter(float, float)}
         */
        @ParameterizedTest
        @CsvSource({"4.9,20,true", "5,20,false", "0,20,true", "20,20,false"})
        void followsTheSpec(float health, float max, boolean expected) {
            assertThat(GhostNeed.belowQuarter(health, max)).isEqualTo(expected);
        }
    }

    /**
     * When the constants are read, they should be the spec's numbers: a crowd is three hostiles within 8 blocks.
     *
     * <p>Method under test: the constants of {@link GhostNeed}
     */
    @Test
    void constantsAreTheSpecsNumbers() {
        assertThat(GhostNeed.CROWD).isEqualTo(3);
        assertThat(GhostNeed.CROWD_RANGE).isEqualTo(8.0);
    }
}
```

`MirrorRuleTest`:

```java
class MirrorRuleTest {
    @Nested
    class Redirects {
        /**
         * When the roll is 0 to 12, redirects should be true (13 of the 14 rolls); at 13 it should be false.
         *
         * <p>Method under test: {@link MirrorRule#redirects(int)}
         */
        @ParameterizedTest
        @CsvSource({"0,true", "6,true", "12,true", "13,false"})
        void is13Of14(int roll, boolean expected) {
            assertThat(MirrorRule.redirects(roll)).isEqualTo(expected);
        }

        /**
         * When the roll is outside 0 to 13, redirects should refuse.
         *
         * <p>Method under test: {@link MirrorRule#redirects(int)}
         */
        @ParameterizedTest
        @ValueSource(ints = {-1, 14, 100, Integer.MIN_VALUE})
        void refusesOutOfRange(int roll) {
            assertThatThrownBy(() -> MirrorRule.redirects(roll)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * When the constants are read, they should be the spec's numbers: 13 images, a 7200-tick life and effect, a
     * 14-sided die.
     *
     * <p>Method under test: the constants of {@link MirrorRule}
     */
    @Test
    void constantsAreTheSpecsNumbers() {
        assertThat(MirrorRule.IMAGES).isEqualTo(13);
        assertThat(MirrorRule.CONFUSION_BOUND).isEqualTo(14);
        assertThat(MirrorRule.LIFE_TICKS).isEqualTo(7200);
        assertThat(MirrorRule.DURATION_TICKS).isEqualTo(7200);
        assertThat(MirrorRule.WANDER_RADIUS).isEqualTo(6.0);
        assertThat(MirrorRule.CONFUSION_RANGE).isEqualTo(16.0);
    }
}
```

`CasterRulesTest`:

```java
class CasterRulesTest {
    @Nested
    class ApproachTo {
        /**
         * When the distance to the target is under 4, 4 to 6 or over 6, approach should answer back off, hold and
         * close in; the edges 4 and 6 hold.
         *
         * <p>Method under test: {@link CasterRules#approach(double)}
         */
        @ParameterizedTest
        @CsvSource({"0,BACK_OFF", "3.99,BACK_OFF", "4,HOLD", "5,HOLD", "6,HOLD", "6.01,CLOSE_IN", "30,CLOSE_IN"})
        void followsTheSpec(double distance, CasterRules.Approach expected) {
            assertThat(CasterRules.approach(distance)).isEqualTo(expected);
        }
    }

    @Nested
    class WantsMirror {
        /**
         * When two or more hostiles are in the guard area, wantsMirror should be true; with one or none, false.
         *
         * <p>Method under test: {@link CasterRules#wantsMirror(int)}
         */
        @ParameterizedTest
        @CsvSource({"0,false", "1,false", "2,true", "9,true"})
        void needsTwo(int hostiles, boolean expected) {
            assertThat(CasterRules.wantsMirror(hostiles)).isEqualTo(expected);
        }
    }

    @Nested
    class WantsGhostrail {
        /**
         * When the subject is below a quarter of its health, wantsGhostrail should be true for anyone; in a wall, only
         * for the summoner (a vex clips through blocks all the time).
         *
         * <p>Method under test: {@link CasterRules#wantsGhostrail(float, float, boolean, boolean)}
         */
        @ParameterizedTest
        @CsvSource({
            "4,20,false,false,true", "4,20,false,true,true", "5,20,false,true,false", "20,20,true,true,true",
            "20,20,true,false,false", "20,20,false,true,false"
        })
        void followsTheSpec(float health, float max, boolean inWall, boolean summoner, boolean expected) {
            assertThat(CasterRules.wantsGhostrail(health, max, inWall, summoner)).isEqualTo(expected);
        }
    }

    @Nested
    class Ready {
        /**
         * When the game time has reached the ready time, ready should be true; one tick before it, false.
         *
         * <p>Method under test: {@link CasterRules#ready(long, long)}
         */
        @ParameterizedTest
        @CsvSource({"99,100,false", "100,100,true", "101,100,true", "0,0,true"})
        void comparesGameTimes(long now, long readyAt, boolean expected) {
            assertThat(CasterRules.ready(now, readyAt)).isEqualTo(expected);
        }
    }

    /**
     * When the constants are read, they should be the spec's numbers.
     *
     * <p>Method under test: the constants of {@link CasterRules}
     */
    @Test
    void constantsAreTheSpecsNumbers() {
        assertThat(CasterRules.KEEP_MIN).isEqualTo(4.0);
        assertThat(CasterRules.KEEP_MAX).isEqualTo(6.0);
        assertThat(CasterRules.WIND_UP_TICKS).isEqualTo(20);
        assertThat(CasterRules.MIRROR_COOLDOWN_TICKS).isEqualTo(7200);
        assertThat(CasterRules.GHOSTRAIL_COOLDOWN_TICKS).isEqualTo(4800);
        assertThat(CasterRules.RANGE).isEqualTo(16.0);
        assertThat(CasterRules.MIRROR_HOSTILES).isEqualTo(2);
    }
}
```

`GhostRecordsTest` and `GhostRecordsPropertiesTest`:

```java
class GhostRecordsTest {
    @Nested
    class Add {
        /**
         * When there is room, add should append the record and keep the order.
         *
         * <p>Method under test: {@link GhostRecords#add(List, Object, int)}
         */
        @Test
        void appendsWhenThereIsRoom() {
            assertThat(GhostRecords.add(List.of(1, 2), 3, 3)).containsExactly(1, 2, 3);
        }

        /**
         * When the list is full, add should drop the oldest (first) record and keep the newest.
         *
         * <p>Method under test: {@link GhostRecords#add(List, Object, int)}
         */
        @Test
        void dropsTheOldestWhenFull() {
            assertThat(GhostRecords.add(List.of(1, 2, 3), 4, 3)).containsExactly(2, 3, 4);
        }

        /**
         * When called, add should not change the list it was given.
         *
         * <p>Method under test: {@link GhostRecords#add(List, Object, int)}
         */
        @Test
        void leavesTheInputAlone() {
            // Arrange
            List<Integer> before = new ArrayList<>(List.of(1, 2, 3));
            // Act
            GhostRecords.add(before, 4, 3);
            // Assert
            assertThat(before).containsExactly(1, 2, 3);
        }

        /**
         * When the maximum is below one, add should refuse (a list that can hold nothing).
         *
         * <p>Method under test: {@link GhostRecords#add(List, Object, int)}
         */
        @Test
        void refusesAMaximumBelowOne() {
            assertThatThrownBy(() -> GhostRecords.add(List.of(), 1, 0)).isInstanceOf(IllegalArgumentException.class);
        }

        /**
         * When the list is already over the maximum (an old save), add should trim it to the maximum, oldest first.
         *
         * <p>Method under test: {@link GhostRecords#add(List, Object, int)}
         */
        @Test
        void trimsAnOverlongInput() {
            assertThat(GhostRecords.add(List.of(1, 2, 3, 4), 5, 2)).containsExactly(4, 5);
        }
    }

    /**
     * When the constant is read, it should be 32.
     *
     * <p>Method under test: {@link GhostRecords#MAX}
     */
    @Test
    void maxIs32() {
        assertThat(GhostRecords.MAX).isEqualTo(32);
    }
}
```

```java
class GhostRecordsPropertiesTest {
    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class Add {
        /**
         * For any list and maximum, add should never return more than the maximum.
         *
         * <p>Method under test: {@link GhostRecords#add(List, Object, int)}
         */
        @Property
        void neverMoreThanMax(
                @ForAll List<@IntRange(min = 0, max = 1000) Integer> records,
                @ForAll @IntRange(min = 0, max = 1000) int record,
                @ForAll @IntRange(min = 1, max = 40) int max) {
            assertThat(GhostRecords.add(records, record, max)).hasSizeLessThanOrEqualTo(max);
        }

        /**
         * For any list and maximum, add should keep the newest record, last.
         *
         * <p>Method under test: {@link GhostRecords#add(List, Object, int)}
         */
        @Property
        void keepsTheNewestLast(
                @ForAll List<@IntRange(min = 0, max = 1000) Integer> records,
                @ForAll @IntRange(min = 0, max = 1000) int record,
                @ForAll @IntRange(min = 1, max = 40) int max) {
            assertThat(GhostRecords.add(records, record, max)).last().isEqualTo(record);
        }

        /**
         * For any list and maximum, the records add drops should all be older than the ones it keeps: the result is the
         * tail of the old list plus the new record.
         *
         * <p>Method under test: {@link GhostRecords#add(List, Object, int)}
         */
        @Property
        void dropsOnlyTheOldest(
                @ForAll List<@IntRange(min = 0, max = 1000) Integer> records,
                @ForAll @IntRange(min = 0, max = 1000) int record,
                @ForAll @IntRange(min = 1, max = 40) int max) {
            // Arrange
            List<Integer> all = new ArrayList<>(records);
            all.add(record);
            // Act & Assert
            assertThat(GhostRecords.add(records, record, max))
                    .isEqualTo(all.subList(Math.max(0, all.size() - max), all.size()));
        }
    }
}
```

`GrantedAbilitiesTest` (the old Infinity table first, then Ghostrail rows):

```java
class GrantedAbilitiesTest {
    /** The plan that changes nothing and keeps no mark. */
    private static final Plan NOTHING = new Plan(false, false, false, false, false, false);

    @Nested
    class Decide {
        /**
         * When only Infinity is involved (the old table of {@code InfinityAbilities}), decide should grant, hold,
         * release or revoke exactly as that table did.
         *
         * <p>Method under test: {@link GrantedAbilities#decide(State)}
         */
        @ParameterizedTest(name = "{0} infinity={1} marked={2} mayfly={3} instabuild={4} -> {5}")
        @MethodSource("infinityRows")
        void infinityRowsKeepTheirMeaning(
                PlayerMode mode, boolean infinity, boolean marked, boolean mayfly, boolean instabuild, Plan expected) {
            assertThat(GrantedAbilities.decide(
                            new State(mode, infinity, false, marked, false, mayfly, instabuild, false)))
                    .isEqualTo(expected);
        }

        /** The rows of the old Infinity table: mode, active, marked, mayfly, instabuild and the expected plan. */
        static Stream<Arguments> infinityRows() {
            Plan grant = new Plan(false, true, true, false, true, false);
            Plan hold = new Plan(false, false, false, false, true, false);
            Plan revoke = new Plan(true, false, false, false, false, false);
            return Stream.of(
                    Arguments.of(PlayerMode.SURVIVAL, true, false, false, false, grant),
                    Arguments.of(PlayerMode.ADVENTURE, true, false, false, false, grant),
                    Arguments.of(PlayerMode.SURVIVAL, true, true, true, true, hold),
                    Arguments.of(PlayerMode.ADVENTURE, true, true, true, true, hold),
                    // Vanilla reset a flag (a game mode round trip): grant again.
                    Arguments.of(PlayerMode.SURVIVAL, true, true, false, true, grant),
                    Arguments.of(PlayerMode.SURVIVAL, true, true, true, false, grant),
                    // Flags from elsewhere while Infinity is active: take ownership so removal revokes them.
                    Arguments.of(PlayerMode.SURVIVAL, true, false, true, true, grant),
                    // Creative and spectator own their flags: drop the mark, change nothing.
                    Arguments.of(PlayerMode.CREATIVE, true, true, true, true, NOTHING),
                    Arguments.of(PlayerMode.SPECTATOR, true, true, true, false, NOTHING),
                    Arguments.of(PlayerMode.CREATIVE, true, false, true, true, NOTHING),
                    // Infinity gone: revoke only what we granted.
                    Arguments.of(PlayerMode.SURVIVAL, false, true, true, true, revoke),
                    Arguments.of(PlayerMode.CREATIVE, false, true, true, true, revoke),
                    Arguments.of(PlayerMode.SURVIVAL, false, false, true, false, NOTHING),
                    Arguments.of(PlayerMode.ADVENTURE, false, false, false, false, NOTHING));
        }

        /**
         * When Ghostrail is involved, decide should grant flight (mayfly and flying, no instabuild), keep it while it
         * is whole, and re-assert flying when the player stopped flying.
         *
         * <p>Method under test: {@link GrantedAbilities#decide(State)}
         */
        @ParameterizedTest(name = "{0} marked={1} mayfly={2} flying={3} -> {4}")
        @MethodSource("ghostrailRows")
        void ghostrailRows(PlayerMode mode, boolean marked, boolean mayfly, boolean flying, Plan expected) {
            assertThat(GrantedAbilities.decide(new State(mode, false, true, false, marked, mayfly, false, flying)))
                    .isEqualTo(expected);
        }

        /** The Ghostrail rows: mode, marked, mayfly, flying and the expected plan. */
        static Stream<Arguments> ghostrailRows() {
            return Stream.of(
                    Arguments.of(PlayerMode.SURVIVAL, false, false, false, new Plan(false, true, false, true, false, true)),
                    Arguments.of(PlayerMode.ADVENTURE, false, false, false, new Plan(false, true, false, true, false, true)),
                    // The player toggled flying off: put it back, every tick.
                    Arguments.of(PlayerMode.SURVIVAL, true, true, false, new Plan(false, true, false, true, false, true)),
                    Arguments.of(PlayerMode.SURVIVAL, true, true, true, new Plan(false, false, false, false, false, true)),
                    // Creative and spectator own their flags.
                    Arguments.of(PlayerMode.CREATIVE, false, true, false, NOTHING),
                    Arguments.of(PlayerMode.SPECTATOR, false, true, true, NOTHING));
        }

        /**
         * When Ghostrail ends, decide should reset the abilities (and clear its mark); with Infinity still active the
         * reset is followed by Infinity's grant, so Infinity keeps its flight and instant building.
         *
         * <p>Method under test: {@link GrantedAbilities#decide(State)}
         */
        @Test
        void ghostrailEndingKeepsInfinitys() {
            // Arrange
            State s = new State(PlayerMode.SURVIVAL, true, false, true, true, true, true, true);
            // Act & Assert
            assertThat(GrantedAbilities.decide(s)).isEqualTo(new Plan(true, true, true, false, true, false));
        }

        /**
         * When Infinity ends with Ghostrail still active, decide should reset, then grant mayfly and flying again.
         *
         * <p>Method under test: {@link GrantedAbilities#decide(State)}
         */
        @Test
        void infinityEndingKeepsGhostrails() {
            // Arrange
            State s = new State(PlayerMode.SURVIVAL, false, true, true, true, true, true, true);
            // Act & Assert
            assertThat(GrantedAbilities.decide(s)).isEqualTo(new Plan(true, true, false, true, false, true));
        }

        /**
         * When both start at once, decide should grant all three flags and set both marks.
         *
         * <p>Method under test: {@link GrantedAbilities#decide(State)}
         */
        @Test
        void bothAtOnce() {
            assertThat(GrantedAbilities.decide(
                            new State(PlayerMode.SURVIVAL, true, true, false, false, false, false, false)))
                    .isEqualTo(new Plan(false, true, true, true, true, true));
        }

        /**
         * When Ghostrail ends in creative (its mark was set in survival), decide should reset to creative's own
         * abilities, which keep flight.
         *
         * <p>Method under test: {@link GrantedAbilities#decide(State)}
         */
        @Test
        void endingInCreativeResetsToCreative() {
            assertThat(GrantedAbilities.decide(
                            new State(PlayerMode.CREATIVE, false, false, false, true, true, true, true)))
                    .isEqualTo(new Plan(true, false, false, false, false, false));
        }

        /**
         * When the state has no mode, the State constructor should refuse.
         *
         * <p>Method under test: {@link GrantedAbilities.State}
         */
        @Test
        void stateRequiresAMode() {
            assertThatThrownBy(() -> new State(null, true, false, false, false, false, false, false))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("mode");
        }
    }

    @Nested
    class ChangesAbilities {
        /**
         * When a plan resets or sets any flag, changesAbilities should be true; a plan that only moves marks, false.
         *
         * <p>Method under test: {@link GrantedAbilities.Plan#changesAbilities()}
         */
        @Test
        void isTrueForAnyFlagChange() {
            assertThat(new Plan(true, false, false, false, false, false).changesAbilities()).isTrue();
            assertThat(new Plan(false, true, false, false, false, false).changesAbilities()).isTrue();
            assertThat(new Plan(false, false, true, false, false, false).changesAbilities()).isTrue();
            assertThat(new Plan(false, false, false, true, false, false).changesAbilities()).isTrue();
            assertThat(new Plan(false, false, false, false, true, true).changesAbilities()).isFalse();
        }
    }
}
```

`GrantedAbilitiesPropertiesTest`:

```java
class GrantedAbilitiesPropertiesTest {
    /** Test model of the plumbing: what applying {@code plan} leaves behind (vanilla mode defaults on a reset). */
    private static State after(State s, Plan p) {
        boolean modeFlies = s.mode() == PlayerMode.CREATIVE || s.mode() == PlayerMode.SPECTATOR;
        boolean mayfly = s.mayfly();
        boolean instabuild = s.instabuild();
        boolean flying = s.flying();
        if (p.reset()) {
            mayfly = modeFlies;
            instabuild = s.mode() == PlayerMode.CREATIVE;
            flying = s.mode() == PlayerMode.SPECTATOR || (modeFlies && flying);
        }
        return new State(
                s.mode(), s.infinity(), s.ghostrail(), p.infinityMarked(), p.ghostrailMarked(),
                mayfly || p.mayfly(), instabuild || p.instabuild(), flying || p.flying());
    }

    /**
     * Whether the game mode leaves abilities to granted sources (survival and adventure, not creative or spectator).
     */
    private static boolean granting(PlayerMode mode) {
        return mode == PlayerMode.SURVIVAL || mode == PlayerMode.ADVENTURE;
    }

    /**
     * For any state, one plan should settle it: deciding again on the state the plan leaves behind changes nothing.
     *
     * <p>Method under test: {@link GrantedAbilities#decide(State)}
     */
    @Property
    void oneDecisionSettlesTheState(
            @ForAll PlayerMode mode, @ForAll boolean infinity, @ForAll boolean ghostrail, @ForAll boolean iMark,
            @ForAll boolean gMark, @ForAll boolean mayfly, @ForAll boolean instabuild, @ForAll boolean flying) {
        State s = new State(mode, infinity, ghostrail, iMark, gMark, mayfly, instabuild, flying);
        Plan again = GrantedAbilities.decide(after(s, GrantedAbilities.decide(s)));
        assertThat(again.changesAbilities()).isFalse();
    }

    /**
     * For any state, a source that is still active should find its abilities in place after the plan, whatever the
     * other source did: ending one never removes what the other grants.
     *
     * <p>Method under test: {@link GrantedAbilities#decide(State)}
     */
    @Property
    void anActiveSourceKeepsItsAbilities(
            @ForAll PlayerMode mode, @ForAll boolean infinity, @ForAll boolean ghostrail, @ForAll boolean iMark,
            @ForAll boolean gMark, @ForAll boolean mayfly, @ForAll boolean instabuild, @ForAll boolean flying) {
        // Arrange
        State s = new State(mode, infinity, ghostrail, iMark, gMark, mayfly, instabuild, flying);
        // Act
        State result = after(s, GrantedAbilities.decide(s));
        // Assert
        if (infinity && granting(mode)) {
            assertThat(result.mayfly() && result.instabuild()).isTrue();
        }
        if (ghostrail && granting(mode)) {
            assertThat(result.mayfly() && result.flying()).isTrue();
        }
    }

    /** Properties that bind {@code decide} itself for the two game modes that own their abilities. */
    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class CreativeAndSpectator {
        /** The two game modes whose abilities the game mode owns. */
        @Provide
        Arbitrary<PlayerMode> ownModes() {
            return Arbitraries.of(PlayerMode.CREATIVE, PlayerMode.SPECTATOR);
        }

        /**
         * For any creative or spectator state (any flags, any marks), decide should never set mayfly, instabuild or
         * flying: those modes' own abilities are never granted over.
         *
         * <p>Method under test: {@link GrantedAbilities#decide(State)}
         */
        @Property
        void neverSetsAFlag(
                @ForAll("ownModes") PlayerMode mode, @ForAll boolean infinity, @ForAll boolean ghostrail,
                @ForAll boolean iMark, @ForAll boolean gMark, @ForAll boolean mayfly, @ForAll boolean instabuild,
                @ForAll boolean flying) {
            // Arrange
            State s = new State(mode, infinity, ghostrail, iMark, gMark, mayfly, instabuild, flying);
            // Act
            Plan p = GrantedAbilities.decide(s);
            // Assert
            assertThat(p.mayfly() || p.instabuild() || p.flying()).isFalse();
        }

        /**
         * For any creative or spectator state with neither mark set, decide should do nothing at all (the plan is
         * exactly {@code NOTHING}): it never resets what it did not grant.
         *
         * <p>Method under test: {@link GrantedAbilities#decide(State)}
         */
        @Property
        void doesNothingWithoutAMark(
                @ForAll("ownModes") PlayerMode mode, @ForAll boolean infinity, @ForAll boolean ghostrail,
                @ForAll boolean mayfly, @ForAll boolean instabuild, @ForAll boolean flying) {
            // Arrange
            State s = new State(mode, infinity, ghostrail, false, false, mayfly, instabuild, flying);
            // Act & Assert
            assertThat(GrantedAbilities.decide(s)).isEqualTo(new Plan(false, false, false, false, false, false));
        }

        /**
         * For a creative or spectator player who starts with the game mode's own abilities and any marks, applying the
         * plan should leave mayfly set, and instabuild too in creative.
         *
         * <p>Method under test: {@link GrantedAbilities#decide(State)}
         */
        @Property
        void keepsItsOwnAbilities(
                @ForAll("ownModes") PlayerMode mode, @ForAll boolean infinity, @ForAll boolean ghostrail,
                @ForAll boolean iMark, @ForAll boolean gMark) {
            // Arrange
            boolean creative = mode == PlayerMode.CREATIVE;
            State s = new State(mode, infinity, ghostrail, iMark, gMark, true, creative, !creative);
            // Act
            State result = after(s, GrantedAbilities.decide(s));
            // Assert
            assertThat(result.mayfly()).isTrue();
            assertThat(result.instabuild()).isEqualTo(creative);
        }
    }

    /**
     * For any state, a mark should be set only while its source is active in survival or adventure.
     *
     * <p>Method under test: {@link GrantedAbilities#decide(State)}
     */
    @Property
    void marksFollowActiveSources(
            @ForAll PlayerMode mode, @ForAll boolean infinity, @ForAll boolean ghostrail, @ForAll boolean iMark,
            @ForAll boolean gMark, @ForAll boolean mayfly, @ForAll boolean instabuild, @ForAll boolean flying) {
        Plan p = GrantedAbilities.decide(
                new State(mode, infinity, ghostrail, iMark, gMark, mayfly, instabuild, flying));
        assertThat(p.infinityMarked()).isEqualTo(infinity && granting(mode));
        assertThat(p.ghostrailMarked()).isEqualTo(ghostrail && granting(mode));
    }
}
```

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests "*GhostrailDurationTest" --tests "*GrantedAbilities*" --tests "*GhostRecords*" --tests "*GhostNeedTest" --tests "*MirrorRuleTest" --tests "*CasterRulesTest"`
Expected: FAIL to compile (the classes do not exist).

- [x] **Step 3: Write the implementations**

```java
/** How long a Ghostrail lasts for each kind of carrier (Parts 6-7 spec §7.3, "Durations"). */
public final class GhostrailDuration {
    /** What kind of thing carries the effect. */
    public enum Kind {
        /** A player (4 minutes). */
        PLAYER,
        /** A Bound Vex (4 minutes). */
        BOUND_VEX,
        /** A hostile mob that cast it on itself (2 seconds). */
        HOSTILE_MOB,
        /** A villager ghost (until the ghost is gone). */
        VILLAGER_GHOST
    }

    /** Ticks for a player or a Bound Vex (4 minutes). */
    public static final int PLAYER_TICKS = 4800;

    /** Ticks for a hostile mob that cast it on itself (2 seconds). */
    public static final int HOSTILE_TICKS = 40;

    /** The never-ending duration; the same number as {@code MobEffectInstance.INFINITE_DURATION}. */
    public static final int INFINITE = -1;

    private GhostrailDuration() {}

    /**
     * The duration for a carrier.
     *
     * @param kind who carries the effect
     * @return ticks, or {@link #INFINITE}
     */
    public static int of(Kind kind) {
        return switch (kind) {
            case PLAYER, BOUND_VEX -> PLAYER_TICKS;
            case HOSTILE_MOB -> HOSTILE_TICKS;
            case VILLAGER_GHOST -> INFINITE;
        };
    }
}
```

```java
/** When a player is "in need" and when a spell subject is in an emergency (Parts 6-7 spec §6.2, §9). */
public final class GhostNeed {
    /** This many hostiles close by make a player in need. */
    public static final int CROWD = 3;

    /** How close, in blocks, a hostile counts toward the crowd. */
    public static final double CROWD_RANGE = 8.0;

    private GhostNeed() {}

    /** Whether {@code health} is below half of {@code maxHealth}. */
    public static boolean belowHalf(float health, float maxHealth) {
        check(health, maxHealth);
        return health * 2.0F < maxHealth;
    }

    /** Whether {@code health} is below a quarter of {@code maxHealth}. */
    public static boolean belowQuarter(float health, float maxHealth) {
        check(health, maxHealth);
        return health * 4.0F < maxHealth;
    }

    /**
     * Whether a player is in need: below half health, or {@link #CROWD} or more hostiles within {@link #CROWD_RANGE}.
     *
     * @param health the player's health
     * @param maxHealth the player's maximum health, positive
     * @param hostiles how many hostiles (already filtered by the caller) are close
     * @return true when a ghost should come
     */
    public static boolean inNeed(float health, float maxHealth, int hostiles) {
        if (hostiles < 0) {
            throw new IllegalArgumentException("hostiles must not be negative: " + hostiles);
        }
        return belowHalf(health, maxHealth) || hostiles >= CROWD;
    }

    private static void check(float health, float maxHealth) {
        if (maxHealth <= 0.0F || health < 0.0F) {
            throw new IllegalArgumentException("health " + health + " of " + maxHealth + " is not a health");
        }
    }
}
```

```java
/** The numbers of Mirror (Parts 6-7 spec §7.2). */
public final class MirrorRule {
    /** Images a Mirror makes. */
    public static final int IMAGES = 13;

    /** Sides of the confusion die: a roll of 0 to 13. */
    public static final int CONFUSION_BOUND = 14;

    /** Ticks an image lives on its own (6 minutes). */
    public static final int LIFE_TICKS = 7200;

    /** Ticks a Mirror effect lasts (6 minutes). */
    public static final int DURATION_TICKS = 7200;

    /** How far from its carrier an image wanders. */
    public static final double WANDER_RADIUS = 6.0;

    /** A mob picking a carrier is confused only by images this close to the carrier. */
    public static final double CONFUSION_RANGE = 16.0;

    private MirrorRule() {}

    /**
     * Whether a confusion roll sends the mob to an image: 13 of the 14 rolls.
     *
     * @param roll a roll of the confusion die, 0 to {@link #CONFUSION_BOUND} - 1
     * @return true if the new target is replaced by an image
     */
    public static boolean redirects(int roll) {
        if (roll < 0 || roll >= CONFUSION_BOUND) {
            throw new IllegalArgumentException("roll must be 0-" + (CONFUSION_BOUND - 1) + ", got " + roll);
        }
        return roll < IMAGES;
    }
}
```

```java
/** The Caster's numbers and tests (Parts 6-7 spec §6.2). */
public final class CasterRules {
    /** What the Caster does about its distance from its target. */
    public enum Approach {
        /** Closer than {@link #KEEP_MIN}: move away. */
        BACK_OFF,
        /** Farther than {@link #KEEP_MAX}: move closer. */
        CLOSE_IN,
        /** Between the two: stay. */
        HOLD
    }

    /** Nearest the Caster stays to its target. */
    public static final double KEEP_MIN = 4.0;

    /** Farthest the Caster stays from its target. */
    public static final double KEEP_MAX = 6.0;

    /** Ticks a cast takes. */
    public static final int WIND_UP_TICKS = 20;

    /** Ticks before the same Caster may cast Mirror again (6 minutes). */
    public static final int MIRROR_COOLDOWN_TICKS = 7200;

    /** Ticks before the same Caster may cast Ghostrail again (4 minutes). */
    public static final int GHOSTRAIL_COOLDOWN_TICKS = 4800;

    /** The summoner or sibling must be within this many blocks of the Caster. */
    public static final double RANGE = 16.0;

    /** A Mirror needs this many hostiles in the guard area. */
    public static final int MIRROR_HOSTILES = 2;

    private CasterRules() {}

    /** What to do at {@code distance} from the target: back off under 4, close in over 6, else hold. */
    public static Approach approach(double distance) {
        if (distance < KEEP_MIN) {
            return Approach.BACK_OFF;
        }
        return distance > KEEP_MAX ? Approach.CLOSE_IN : Approach.HOLD;
    }

    /** Whether {@code hostiles} in the guard area are enough for a Mirror. */
    public static boolean wantsMirror(int hostiles) {
        return hostiles >= MIRROR_HOSTILES;
    }

    /**
     * Whether a subject needs Ghostrail: below a quarter of its health, or (the summoner only) inside a wall.
     *
     * @param health the subject's health
     * @param maxHealth its maximum health
     * @param inWall whether it is inside a block
     * @param subjectIsSummoner true for the summoner, false for a vex
     * @return true if the Caster should cast Ghostrail on it
     */
    public static boolean wantsGhostrail(float health, float maxHealth, boolean inWall, boolean subjectIsSummoner) {
        return GhostNeed.belowQuarter(health, maxHealth) || (subjectIsSummoner && inWall);
    }

    /** Whether a cooldown that ends at game time {@code readyAt} is over at {@code now}. */
    public static boolean ready(long now, long readyAt) {
        return now >= readyAt;
    }
}
```

```java
/** The one list rule of the villager-ghost records (Parts 6-7 spec §9): the oldest record goes first. */
public final class GhostRecords {
    /** Most records a level keeps. */
    public static final int MAX = 32;

    private GhostRecords() {}

    /**
     * {@code records} with {@code record} added at the end, trimmed from the front to at most {@code max}.
     *
     * @param records the records so far, oldest first; not changed
     * @param record the new record
     * @param max the most to keep, at least 1
     * @param <T> the record type
     * @return a new list, oldest first, newest last
     */
    public static <T> List<T> add(List<T> records, T record, int max) {
        if (max < 1) {
            throw new IllegalArgumentException("max must be at least 1, got " + max);
        }
        List<T> all = new ArrayList<>(records.size() + 1);
        all.addAll(records);
        all.add(record);
        return List.copyOf(all.subList(Math.max(0, all.size() - max), all.size()));
    }
}
```

```java
/**
 * Which creative-style abilities Infinity and Ghostrail should hold (Parts 6-7 spec §7.3; formerly
 * {@code InfinityAbilities}). Infinity grants {@code mayfly} and {@code instabuild}; Ghostrail grants
 * {@code mayfly} and {@code flying}. {@code invulnerable} is deliberately absent. A source's "mark" records that
 * it, not vanilla or another mod, set the flags, so a revoke is never applied twice.
 */
public final class GrantedAbilities {
    /**
     * Everything the decision reads.
     *
     * @param mode the player's game mode
     * @param infinity whether the Infinity effect is active
     * @param ghostrail whether the Ghostrail effect is active
     * @param infinityMarked whether Infinity set the flags it holds
     * @param ghostrailMarked whether Ghostrail set the flags it holds
     * @param mayfly the player's current {@code mayfly} flag
     * @param instabuild the player's current {@code instabuild} flag
     * @param flying the player's current {@code flying} flag
     */
    public record State(
            PlayerMode mode, boolean infinity, boolean ghostrail, boolean infinityMarked, boolean ghostrailMarked,
            boolean mayfly, boolean instabuild, boolean flying) {
        /** Refuses a missing game mode. */
        public State {
            Objects.requireNonNull(mode, "mode");
        }
    }

    /**
     * What to do, in this order: reset to the game mode's abilities if {@code reset}; set {@code mayfly},
     * {@code instabuild} and {@code flying} true where they say so; store the two marks.
     *
     * @param reset whether to reset to the game mode's own abilities first
     * @param mayfly whether to set {@code mayfly}
     * @param instabuild whether to set {@code instabuild}
     * @param flying whether to set {@code flying}
     * @param infinityMarked the Infinity mark to store
     * @param ghostrailMarked the Ghostrail mark to store
     */
    public record Plan(
            boolean reset, boolean mayfly, boolean instabuild, boolean flying, boolean infinityMarked,
            boolean ghostrailMarked) {
        /** Whether applying the plan touches the player's abilities (and so needs an abilities packet). */
        public boolean changesAbilities() {
            return reset || mayfly || instabuild || flying;
        }
    }

    private GrantedAbilities() {}

    /**
     * One plan settles the state every tick. A source that ended (its mark set, its effect gone) resets the
     * abilities; whatever is still active then grants again in the same plan.
     *
     * @param state the player's mode, effects, marks and flags
     * @return the plan
     */
    public static Plan decide(State state) {
        boolean granting = state.mode() == PlayerMode.SURVIVAL || state.mode() == PlayerMode.ADVENTURE;
        boolean infinityNeed = state.infinity() && granting;
        boolean ghostrailNeed = state.ghostrail() && granting;
        boolean ended = (state.infinityMarked() && !state.infinity()) || (state.ghostrailMarked() && !state.ghostrail());
        if (!infinityNeed && !ghostrailNeed) {
            return new Plan(ended, false, false, false, false, false);
        }
        boolean grant = ended
                || !state.mayfly()
                || (infinityNeed && (!state.instabuild() || !state.infinityMarked()))
                || (ghostrailNeed && (!state.flying() || !state.ghostrailMarked()));
        return new Plan(ended, grant, grant && infinityNeed, grant && ghostrailNeed, infinityNeed, ghostrailNeed);
    }
}
```

Delete `InfinityAbilities.java` and the two `InfinityAbilities*Test.java` files. `InfinityAbilitySync.sync` keeps working until Task 6 rewrites it: replace its body now with the thin adapter below so the build stays green (Task 6 moves it to `AbilitySync` and adds Ghostrail).

```java
public static void sync(ServerPlayer player) {
    Abilities abilities = player.getAbilities();
    GrantedAbilities.Plan plan = GrantedAbilities.decide(new GrantedAbilities.State(
            PlayerModes.of(player),
            player.hasEffect(ModEffects.INFINITY),
            false,
            player.getData(InfinityAttachments.GRANTED),
            false,
            abilities.mayfly,
            abilities.instabuild,
            abilities.flying));
    if (plan.reset()) {
        player.gameMode().updatePlayerAbilities(abilities);
    }
    abilities.mayfly |= plan.mayfly();
    abilities.instabuild |= plan.instabuild();
    if (plan.infinityMarked() != player.getData(InfinityAttachments.GRANTED)) {
        player.setData(InfinityAttachments.GRANTED, plan.infinityMarked());
    }
    if (plan.changesAbilities()) {
        player.onUpdateAbilities();
    }
}
```

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test`
Expected: PASS, including `ArchitectureTest.spellRulesCoreIsPure` and the unchanged `InfinityAbilityGameTests` logic (they run in Step 5).

- [x] **Step 5: Gates and commit**

Run: `./gradlew spotlessApply build`, then `./gradlew runGameTestServer` twice. Expected: `All N required tests passed` both times, where N is the baseline Task 1 recorded (no GameTest was added; the Infinity ability tests prove the adapter kept the old behavior).

```bash
git add src
git commit -m "feat(spells): pure core for Casters, Mirror, Ghostrail and villager ghosts"
```

> **As built (`02d5693`):** matches plan.

---

### Task 3: The two spells' framework recipe, `Spells.cast`, icons, assets and lang

**Files:**

- Modify: `spell/rules/SpellDeclarations.java`, `spell/Spells.java`, `spell/ModItems.java`, `spell/SpellScrollItem.java`, `effect/ModEffects.java`, `particle/ModParticles.java`, `scripts/spell_icons.py`, `scripts/test_spell_icons.py`
- Create: `effect/MirrorEffect.java`, `effect/GhostrailEffect.java` (empty hooks for now), `assets/spellcraft/items/mirror_scroll.json`, `ghostrail_scroll.json`, `models/item/mirror_scroll.json`, `ghostrail_scroll.json`, the four generated PNGs
- Modify: `assets/spellcraft/lang/en_us.json`, `gametest/ScribingGameTests.java`, `gametest/SpellcraftGameTests.java`
- Create: `gametest/SpellScrollGameTests.java`, `gametest/ScrollTestSupport.java` (only `plainPlayer` and `floor` for now; Task 4 adds the rest)
- Test: `src/test/…/spell/rules/SpellDeclarationsTest.java`, `CastRulesTest.java`, `SpellAssetsTest.java`

**Interfaces:**

- Consumes: Task 2 (`MirrorRule.DURATION_TICKS`, `GhostrailDuration.PLAYER_TICKS`).
- Produces:
  - `SpellDeclarations.MIRROR` (`mirror`, `SpellTier.AMAZING`, `Recast.REFUSE`, dispellable `true`, redundantInCreative `false`) and `GHOSTRAIL` (`ghostrail`, `EXTREME`, `REFUSE`, dispellable `true`, redundantInCreative `true`); `ALL = [UPSIDE_DOWN, INFINITY, TRAILING, EVOKING, MIRROR, GHOSTRAIL]`.
  - `Spells.MIRROR`, `Spells.GHOSTRAIL`, `Spells.ALL` in that order; `static boolean Spells.cast(LivingEntity target, Spell spell, int ticks)` (adds `new MobEffectInstance(spell.effect(), ticks, 0, false, false, true)`; returns `addEffect`'s result); `static boolean Spells.refusedFor(LivingEntity target, Spell spell)` (the one creative refusal for casters that are not scrolls: a creative or spectator `Player` and a spell whose rules say `redundantInCreative`; Tasks 9 and 12 call it, the scroll keeps using `CastRules`).
  - `ModEffects.MIRROR`, `ModEffects.GHOSTRAIL`, `ModEffects.CASTING_SCROLL` (all `DeferredHolder<MobEffect, MobEffect>`); `ModParticles.CASTING_SCROLL`; `ModItems.MIRROR_SCROLL`, `ModItems.GHOSTRAIL_SCROLL`.
  - `MirrorEffect extends MobEffect` (color `0xE066FF`, `BENEFICIAL`) and `GhostrailEffect extends MobEffect` (color `0xDDE6FF`, `BENEFICIAL`), both with an `onEffectAdded` override that calls `super` and is otherwise empty until Task 4 (Mirror's images) and Task 7 (Ghostrail's start).

Read `SpellDeclarationsTest` and `CastRulesTest` first; edit them as below (do not rewrite the files).

- [x] **Step 1: Write the failing tests**

`SpellDeclarationsTest` has no table: it has one `@Test` per spell plus `declaresEverySpellWithItsTier`, whose `containsExactly` lists the four spells. Extend that `containsExactly` to `UPSIDE_DOWN, INFINITY, TRAILING, EVOKING, MIRROR, GHOSTRAIL`, add `assertThat(SpellDeclarations.MIRROR.tier()).isEqualTo(SpellTier.AMAZING)` and `GHOSTRAIL.tier()` `EXTREME` (with `isTop()` true) there, and add one test per new spell in the shape of `evokingRefusesARecastIsDispellableAndCastsInCreative` (When/should Javadoc, `Method under test: {@link SpellDeclarations#MIRROR}`):

- `mirrorRefusesARecastIsDispellableAndCastsInCreative`: name `mirror`, recast `REFUSE`, dispellable true, `redundantInCreative` false.
- `ghostrailRefusesARecastIsDispellableAndIsRedundantInCreative`: name `ghostrail`, recast `REFUSE`, dispellable true, `redundantInCreative` true.

In `CastRulesTest.decidesEveryCase`, the CSV rows go through a `switch (spell)` whose `default` throws `unknown spell in the CSV`, so first add two `case` arms (`case "MIRROR" -> SpellDeclarations.MIRROR;` and `case "GHOSTRAIL" -> SpellDeclarations.GHOSTRAIL;`), then add 16 rows per spell (4 modes x sneaking x active, in the file's `SPELL, sneaking, active, MODE, OUTCOME` order and spacing). The expected values follow `CastRules`:

- Mirror (not redundant, dispellable, refuses a recast): not active, any mode, either sneaking value: `CAST`; active and not sneaking: `REFUSE`; active and sneaking: `DISPEL`. Mirror never refuses a first cast.
- Ghostrail (redundant in creative, dispellable, refuses a recast): every `CREATIVE` row (all eight) is `REFUSE` (the creative refusal wins before the recast rules); every other mode behaves like Mirror (`CAST`, `REFUSE`, `DISPEL`).

In `SpellAssetsTest` add (it iterates `ALL`, so the existing tests now cover both spells' scroll, effect icon, item definition and names), plus this version of the test, which names only what exists after this task:

```java
/**
 * When the casting effect is named in the lang file, should its key exist: a missing one would show a raw key in
 * game. Tasks 4 and 12 add the entity keys to this test as their entities arrive.
 *
 * <p>Method under test: the lang file against the Part 7 names
 */
@Test
void part7NamesAreEnglish() throws IOException {
    // Arrange
    String lang = Files.readString(ASSETS.resolve("lang/en_us.json"));
    // Act & Assert
    assertThat(lang).contains("\"effect.spellcraft.casting_scroll\"");
}
```

Task 4 appends `.contains("\"entity.spellcraft.mirror_image\"")` and Task 12 appends `.contains("\"entity.spellcraft.villager_ghost\"")` (and both reword the Javadoc's first sentence to name the keys it then checks). The two item scrolls are covered by the existing `ALL` loop. Say so in the commit.

In `scripts/test_spell_icons.py` the existing tests (`test_i1` to `test_i7`) loop over `icons.SPELLS`, so they cover the two new glyphs; add one test that the six spells have six distinct ink colors:

```python
def test_i8_every_spell_has_its_own_ink(self):
    """When the six spells are listed, SPELLS should give each its own ink color.

    Function under test: :data:`spell_icons.SPELLS`
    """
    inks = [spell.ink for spell in icons.SPELLS]
    self.assertEqual(len(inks), len(set(inks)))
    self.assertEqual([spell.id for spell in icons.SPELLS][-2:], ["mirror", "ghostrail"])
```

`ScrollTestSupport` (created here with the two helpers these tests need; Task 4 adds the rest):

```java
/** Helpers of the Part 7 GameTests: players, floors, images and hostiles. */
final class ScrollTestSupport {
    private ScrollTestSupport() {}

    /** A survival player at {@link FangGameTests#STAND} (the padded box is loaded), without the Evoking effect. */
    static ServerPlayer plainPlayer(GameTestHelper helper) {
        ServerPlayer player = FangGameTests.caster(helper, FangGameTests.NORTH);
        player.removeEffect(ModEffects.EVOKING);
        return player;
    }

    /** Lays stone under the square of {@code radius} blocks around the standing spot (the padded box is air). */
    static void floor(GameTestHelper helper, int radius) {
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                helper.setBlock(FangGameTests.FEET.offset(dx, -1, dz), Blocks.STONE);
            }
        }
    }
}
```

`SpellScrollGameTests` (registered in this task; seven tests). **The four Mirror tests and the two Ghostrail tests that use a player register through `FangGameTests.addPadded(SHARED, …)`** and take their player from `plainPlayer(helper)` over a `floor(helper, 6)` (a `MockPlayers.inLevel` player is never moved, so it would sit at the world's random spawn point; once Task 4 lands, each Mirror cast there would leave 13 ticking images that no test bound clears, stacking up with every other unmoved mock player; in the padded box they sit inside the test's own bounds, which the framework clears). The mob test needs no padding:

```java
/** GameTests of the Mirror and Ghostrail scrolls and of {@code Spells.cast} (Parts 6-7 spec §7.1, §10). */
public final class SpellScrollGameTests {
    private SpellScrollGameTests() {}

    static void registerAll() {
        FangGameTests.addPadded(SHARED, "spell_scroll_mirror_gives_the_effect_for_7200_ticks", SpellScrollGameTests::mirrorScroll);
        FangGameTests.addPadded(SHARED, "spell_scroll_ghostrail_gives_the_effect_for_4800_ticks", SpellScrollGameTests::ghostrailScroll);
        FangGameTests.addPadded(SHARED, "spell_scroll_mirror_is_refused_while_active", SpellScrollGameTests::mirrorRefusedWhileActive);
        FangGameTests.addPadded(SHARED, "spell_scroll_mirror_sneak_cast_dispels", SpellScrollGameTests::mirrorSneakDispels);
        FangGameTests.addPadded(SHARED, "spell_scroll_ghostrail_is_refused_in_creative", SpellScrollGameTests::ghostrailRefusedInCreative);
        FangGameTests.addPadded(SHARED, "spell_scroll_mirror_is_allowed_in_creative", SpellScrollGameTests::mirrorAllowedInCreative);
        SpellcraftGameTests.add(SHARED, "spell_cast_helper_applies_any_spell_to_a_mob", SpellScrollGameTests::castHelperOnAMob);
    }
```

with these bodies (`GameTestSupport.hold`, `useHeld`, `requireEffect` exist; read their signatures and `InfinityGameTests` for the exact mock-player pattern, and copy it; the creative tests take `plainPlayer(helper)` and then call `player.setGameMode(GameType.CREATIVE)`):

```java
/** When a survival player reads a Mirror scroll, should they get Mirror for 7200 ticks and the scroll be used up. */
static void mirrorScroll(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = plainPlayer(helper);
    floor(helper, 6);
    GameTestSupport.hold(player, new ItemStack(ModItems.MIRROR_SCROLL.get(), 2));
    // Act
    GameTestSupport.useHeld(helper, player);
    // Assert
    MobEffectInstance effect = GameTestSupport.requireEffect(helper, player, ModEffects.MIRROR);
    helper.assertValueEqual(effect.getDuration(), MirrorRule.DURATION_TICKS, "Mirror lasts 7200 ticks");
    helper.assertValueEqual(GameTestSupport.heldCount(player), 1, "one scroll is used up");
    helper.succeed();
}
```

`ghostrailScroll` is the same with `GHOSTRAIL_SCROLL`, `ModEffects.GHOSTRAIL` and `GhostrailDuration.PLAYER_TICKS`. Each of the other padded tests starts with `plainPlayer(helper)` and `floor(helper, 6)` the same way. `mirrorRefusedWhileActive`: add `Spells.cast(player, Spells.MIRROR, 100)` first, `useHeld`, then assert the scroll count did not change, the action bar shows `SpellScrollItem.REFUSED_KEY` (`GameTestSupport.assertActionBarKey`). `mirrorSneakDispels`: effect on, `player.setShiftKeyDown(true)`, `useHeld`: the effect is gone, one scroll used. `ghostrailRefusedInCreative` and `mirrorAllowedInCreative`: `GameType.CREATIVE`; refused keeps the scroll and gives no effect; allowed gives the effect. `castHelperOnAMob`:

```java
/** When Spells.cast is called on a zombie with 40 ticks, should the zombie carry that spell's effect for 40 ticks. */
static void castHelperOnAMob(GameTestHelper helper) {
    // Arrange
    Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
    zombie.setNoAi(true);
    // Act
    boolean added = Spells.cast(zombie, Spells.GHOSTRAIL, 40);
    // Assert
    helper.assertTrue(added, "a fresh effect is added");
    helper.assertValueEqual(zombie.getEffect(ModEffects.GHOSTRAIL).getDuration(), 40, "40 ticks");
    helper.assertFalse(zombie.getEffect(ModEffects.GHOSTRAIL).isVisible(), "no particles");
    helper.succeed();
}
```

`ScribingGameTests`: in `zeroPercent`, the lower pool now holds Upside-Down, Trailing, Evoking **and Mirror**: add `helper.assertTrue(lowerPool.contains(ModItems.MIRROR_SCROLL.get()), "the lower pool holds Mirror");`, add `ModItems.MIRROR_SCROLL.get()` to the `drawsOnly(helper, List.of(0, 0, 0), …)` list, and change the Javadoc ("every non-extreme spell (Upside-Down, Trailing, Evoking and Mirror)"). The loop `poolSize = 1; for (pick...)` already walks the whole pool. In `hundredPercent` (the top tier now holds two spells), copy `zeroPercent`'s reachability loop: run the same `FixedPick` loop with `List.of(100, 100, 100)` over `Scribing.candidates()`, assert that both `ModItems.INFINITY_SCROLL.get()` and `ModItems.GHOSTRAIL_SCROLL.get()` are reached and that the pool size is 2, then call `drawsOnly(helper, List.of(100, 100, 100), ModItems.INFINITY_SCROLL.get(), ModItems.GHOSTRAIL_SCROLL.get())` for the in-menu path. (A single random draw accepting either scroll would pass about half the time for an implementation that never draws Ghostrail, and always for a pool bug that returns only Infinity; the loop closes that.) Grep the file for any other place that expects only the Infinity scroll (`INFINITY_SCROLL`) and accept both.

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test` (compile failure: `SpellDeclarations.MIRROR` does not exist).

- [x] **Step 3: Write the implementation**

`SpellDeclarations` (append, and extend `ALL`):

```java
/** Scroll of Mirror: amazing tier, refuses a recast (the images are one set), dispellable, cast in creative too. */
public static final SpellRules MIRROR =
        new SpellRules("mirror", SpellTier.AMAZING, SpellRules.Recast.REFUSE, true, false);

/** Scroll of Ghostrail: extreme tier, refuses a recast, dispellable, redundant in creative (creative flies already). */
public static final SpellRules GHOSTRAIL =
        new SpellRules("ghostrail", SpellTier.EXTREME, SpellRules.Recast.REFUSE, true, true);

public static final List<SpellRules> ALL = List.of(UPSIDE_DOWN, INFINITY, TRAILING, EVOKING, MIRROR, GHOSTRAIL);
```

`Spells`:

```java
/** Mirror: thirteen images of the caster for six minutes. */
public static final Spell MIRROR = new Spell(
        Spellcraft.id(SpellDeclarations.MIRROR.name()),
        ModEffects.MIRROR,
        SpellDeclarations.MIRROR,
        () -> MirrorRule.DURATION_TICKS);

/** Ghostrail: fly through walls, unhurt, for four minutes (a player's duration; other carriers pass their own). */
public static final Spell GHOSTRAIL = new Spell(
        Spellcraft.id(SpellDeclarations.GHOSTRAIL.name()),
        ModEffects.GHOSTRAIL,
        SpellDeclarations.GHOSTRAIL,
        () -> GhostrailDuration.PLAYER_TICKS);

public static final List<Spell> ALL = List.of(UPSIDE_DOWN, INFINITY, TRAILING, EVOKING, MIRROR, GHOSTRAIL);

/**
 * Puts {@code spell}'s effect on {@code target} for {@code ticks}: no particles, the icon shown. What a spell does
 * when it starts lives in its effect (Mirror's images in {@code MirrorEffect.onEffectAdded}), so a scroll, a Caster,
 * a mob, a ghost and {@code /effect give} behave alike. This method applies no rules: a scroll's rules (recast,
 * dispel) are {@code CastRules}'; other casters check "no effect yet" themselves and ask {@link #refusedFor} first.
 *
 * @param target who gets the effect
 * @param spell the spell
 * @param ticks how long, or {@link Spell#INFINITE}'s value
 * @return whether the effect was added or lengthened
 */
public static boolean cast(LivingEntity target, Spell spell, int ticks) {
    return target.addEffect(new MobEffectInstance(spell.effect(), ticks, 0, false, false, true));
}

/**
 * Whether a caster that is not a scroll (a Caster Vex, a villager ghost) must not cast {@code spell} on
 * {@code target}: the target is a creative or spectator player and the spell is redundant in creative (Ghostrail:
 * creative players already fly). The scroll's own refusal is {@code CastRules}'; this is the one predicate for every
 * other caster, so Tasks 9 and 12 cannot diverge.
 *
 * @param target who would get the effect
 * @param spell the spell
 * @return true if the cast must be skipped
 */
public static boolean refusedFor(LivingEntity target, Spell spell) {
    return target instanceof Player player
            && (player.isCreative() || player.isSpectator())
            && spell.rules().redundantInCreative();
}
```

`SpellScrollItem.apply`: `case CAST, REFRESH -> Spells.cast(player, spell, spell.durationTicks().getAsInt());` (keep the comment about hidden particles and the refresh gap, moved into `Spells.cast`'s doc).

`ModItems`: after `EVOKING_SCROLL`:

```java
public static final DeferredItem<SpellScrollItem> MIRROR_SCROLL = registerScroll(Spells.MIRROR);
public static final DeferredItem<SpellScrollItem> GHOSTRAIL_SCROLL = registerScroll(Spells.GHOSTRAIL);
```

`ModEffects`:

```java
/** Strength of Mirror. {@code MirrorEffect.onEffectAdded} spawns the images; they end with the effect. */
public static final DeferredHolder<MobEffect, MobEffect> MIRROR = MOB_EFFECTS.register("mirror", MirrorEffect::new);

/** Strength of Ghostrail. {@code Ghostrail} runs it every tick; the Mixin lets a player through blocks. */
public static final DeferredHolder<MobEffect, MobEffect> GHOSTRAIL = MOB_EFFECTS.register("ghostrail", GhostrailEffect::new);

/**
 * On a non-player caster for the 20 ticks a scroll cast keeps the hands lit; carries {@code
 * ModParticles.CASTING_SCROLL}.
 */
public static final DeferredHolder<MobEffect, MobEffect> CASTING_SCROLL =
        MOB_EFFECTS.register("casting_scroll", () -> new CastingEffect(ModParticles.CASTING_SCROLL));
```

`ModParticles`: `CASTING_SCROLL = PARTICLE_TYPES.register("casting_scroll", () -> new SimpleParticleType(false));` with a Javadoc ("Carried by `ModEffects.CASTING_SCROLL`: a Caster or a villager ghost is casting a scroll"). The client's null provider for it is Task 13.

`MirrorEffect` / `GhostrailEffect`:

```java
/** Strength of Mirror (Parts 6-7 spec §7.2). Milk-curable. Task 4 fills in {@link #onEffectAdded}. */
public final class MirrorEffect extends MobEffect {
    /** The effect's color, as the Mirror icon's ink. */
    public static final int COLOR = 0xE066FF;

    public MirrorEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }

    @Override
    public void onEffectAdded(LivingEntity mob, int amplifier) {
        super.onEffectAdded(mob, amplifier);
    }
}
```

`GhostrailEffect` is the same with color `0xDDE6FF` and a Javadoc pointing at Task 7 (where `onEffectAdded` calls `Ghostrail.begin`); give both constructors a Javadoc.

`scripts/spell_icons.py`: append two spells to `SPELLS`; first draw, then check the "glyph lands on the paper" test passes (it fails loudly if a pixel is off the sheet; adjust the `origin`, not the sheet):

```python
    Spell(
        "mirror",
        (224, 102, 255, 255),  # magenta, as the effect color 0xE066FF
        (
            "##...##",
            "##.#.##",
            ".#.#.#.",
            "###.###",
            ".#.#.#.",
            "##.#.##",
            "#..#..#",
        ),
        (6, 4),
    ),
    Spell(
        "ghostrail",
        (221, 230, 255, 255),  # pale blue, as the effect color 0xDDE6FF
        (
            "...#...",
            "...#...",
            "...#.#.",
            "#######",
            "...#.#.",
            "...#...",
            "...#...",
        ),
        (5, 5),
    ),
```

Run `python scripts/spell_icons.py` (writes the four PNGs) and `python -m unittest discover -s scripts -p "test_*.py"`. The Mirror glyph at origin `(6, 4)` was checked against the sheet (origin `(5, 5)` puts one pixel off the paper); if `test_i1` still reports a pixel off the paper, move the origin, never the sheet. Look at both scroll PNGs with the Read tool to check the glyphs read (a figure pair; an arrow through a wall); the eye test is Will's, record it in the playtest list (Task 15).

Item definitions and models, copying the Evoking files with the name swapped:

```json
{ "model": { "type": "minecraft:model", "model": "spellcraft:item/mirror_scroll" } }
```

```json
{ "parent": "minecraft:item/generated", "textures": { "layer0": "spellcraft:item/mirror_scroll" } }
```

(and the same pair for `ghostrail_scroll`). Lang keys, appended after `"item.spellcraft.evoking_scroll"` and `"effect.spellcraft.evoking"` respectively, in the file's order:

```json
"item.spellcraft.mirror_scroll": "Scroll of Mirror",
"item.spellcraft.ghostrail_scroll": "Scroll of Ghostrail",
"effect.spellcraft.mirror": "Strength of Mirror",
"effect.spellcraft.ghostrail": "Strength of Ghostrail",
"effect.spellcraft.casting_scroll": "Casting Scroll",
```

(`casting_scroll` goes after `casting_vexes`.)

Add `SpellScrollGameTests.registerAll();` after `BoundVexTeamMoveRaidGameTests.registerAll();` in `SpellcraftGameTests`.

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test`, `python -m unittest discover -s scripts -p "test_*.py"`, then the inner loop `./gradlew runGameTestServer "-PgameTests=spellcraft:spell_*"` and `"-PgameTests=spellcraft:scribing_*"`.
Expected: PASS.

- [x] **Step 5: Gates and commit**

Run: `./gradlew spotlessApply build`, then `./gradlew runGameTestServer` twice (`runData` is not needed here: this task adds nothing it can check). Expected: `All 657 required tests passed` both times (the baseline of Task 1 plus the 7 tests registered here).

```bash
git add src scripts
git commit -m "feat(spells): Mirror and Ghostrail scrolls, effects, icons and Spells.cast"
```

> **As built (`b63ced0`):** diverged — `hundredPercent` passes option index 0 with chances `List.of(100, 100, 100)` (the plan implied passing 100 as the option); reason: `ScrollDraw.draw`'s first argument is an option index; the Task 2 review's Javadoc minors were fixed first in 978244f.

### Task 4: Mirror images (the entity, the effect, the dice)

**Files:**

- Create: `evoking/MirrorImage.java`, `evoking/MirrorImages.java`, `gametest/MirrorGameTests.java`
- Modify: `gametest/ScrollTestSupport.java` (Task 3 created it), `evoking/ModEntities.java`, `evoking/Dice.java`, `evoking/TeamMoves.java` (`clear`), `effect/MirrorEffect.java`, `assets/spellcraft/lang/en_us.json`, `gametest/SpellcraftGameTests.java`, `src/test/…/SpellAssetsTest.java`

**Interfaces:**

- Consumes: Task 2 (`MirrorRule`), Task 3 (`ModEffects.MIRROR`, `Spells.cast`), `VexRing.offset(int)` / `VexRing.COUNT`, `StandableSpots.standable(Level, BlockPos)` (package-private), `AllyAttachments.SUMMONER`.
- Produces:
  - `Dice.CASTER_ROLL`, `Dice.SCROLL_MOB`, `Dice.MIRROR_CONFUSION` (public static final `Dice` instances) and `static void Dice.unscriptAll()` (unscripts every instance, `RAID_KIT` included); `TeamMoves.clear()` calls it instead of `Dice.RAID_KIT.unscript()`.
  - `ModEntities.MIRROR_IMAGE` (`DeferredHolder<EntityType<?>, EntityType<MirrorImage>>`, id `spellcraft:mirror_image`, `MobCategory.MISC`, `sized(0.6F, 1.8F)`, `clientTrackingRange(8)`, `noLootTable()`); attributes `MAX_HEALTH` 10 and `MOVEMENT_SPEED` 0.25 in `onAttributes`.
  - `MirrorImage extends LivingEntity` (public final): `Optional<UUID> casterId()`, `EntityType<?> copiedType()`, `ResolvableProfile profile()` (the player's profile, else `Mannequin.DEFAULT_PROFILE`), `int lifeTicks()`, `void setLifeTicks(int)`, `void bind(LivingEntity caster)` (server side: sets the caster id, the copied type and profile, copies both hands, and for a `Player` caster the summoner mark), `void poof()` (POOF particles, no sound, `discard`). Saved: `caster`, `life_ticks`, `copied_type`, `profile`.
  - `MirrorImages` (public final, `@EventBusSubscriber`): `static List<MirrorImage> of(ServerLevel level, UUID caster)` (live images of that caster in that level, any distance), `static void spawn(LivingEntity carrier)` (13 images, after `poofAll`; never inside a block), `static void poofAll(LivingEntity carrier)`, `static void afterEnd(ServerLevel level, UUID caster, MirrorImage ending)` (removes the carrier's Mirror effect when no other live image of it remains), and the `LivingDeathEvent` handler `onDeath` (poofs a dying carrier's images).
  - `MirrorEffect.onEffectAdded` calls `MirrorImages.spawn(mob)` on a server level; `MirrorEffect.onMobRemoved` calls `MirrorImages.poofAll(mob)`.
  - `gametest/ScrollTestSupport` (package-private, all static): Task 3 created `ServerPlayer plainPlayer(GameTestHelper)` (a survival mock player at `FangGameTests.STAND` after the padding wait, Evoking removed) and `void floor(GameTestHelper, int radius)` (stone under the square of that radius around `FangGameTests.FEET`); this task adds `List<MirrorImage> images(LivingEntity carrier)`, `Vec3 at(GameTestHelper, double dx, double dy, double dz)` (absolute, from `FangGameTests.STAND`), `<T extends Mob> T foeAt(GameTestHelper, EntityType<T>, Vec3 absolute)` (= `TeamMoveTestSupport.foe`) and `Husk hostileAt(GameTestHelper, Vec3 absolute)` (= `foeAt(helper, EntityTypes.HUSK, at)`: a frozen hostile that does not burn in daylight, unlike a zombie; Tasks 5, 7, 9 and 12 use it).

- [x] **Step 1: Write the failing tests**

Adding the Mirror images changes what the Task 3 tests in `SpellScrollGameTests` (`spell_scroll_*`) do: each Mirror cast there now spawns 13 ticking images. They are already padded and take their player from `plainPlayer` over a floor (Task 3), so the images stay inside each test's own bounds; confirm that by running `./gradlew runGameTestServer "-PgameTests=spellcraft:spell_scroll_*"` in Step 4.

Add to `ScrollTestSupport` (beside `plainPlayer` and `floor`, which Task 3 created):

```java
/** Helpers of the Part 7 GameTests: players, floors, images and hostiles. */
final class ScrollTestSupport {
    // ... plainPlayer and floor from Task 3 stay as they are ...

    /** The live images of {@code carrier} in its level. */
    static List<MirrorImage> images(LivingEntity carrier) {
        return MirrorImages.of((ServerLevel) carrier.level(), carrier.getUUID());
    }

    /** The absolute position {@code (dx, dy, dz)} from the standing spot. */
    static Vec3 at(GameTestHelper helper, double dx, double dy, double dz) {
        return helper.absoluteVec(FangGameTests.STAND.add(dx, dy, dz));
    }

    /** A mob with no AI and no gravity at {@code at}, discarded when the test ends. */
    static <T extends Mob> T foeAt(GameTestHelper helper, EntityType<T> type, Vec3 at) {
        return TeamMoveTestSupport.foe(helper, type, at);
    }

    /** A frozen husk (hostile, and unlike a zombie it does not burn in daylight) at {@code at}. */
    static Husk hostileAt(GameTestHelper helper, Vec3 at) {
        return foeAt(helper, EntityTypes.HUSK, at);
    }
}
```

`MirrorGameTests` registers through `FangGameTests.addPadded(SHARED, …)` (the images sit in the padding) and is added to the static block of `SpellcraftGameTests` after `SpellScrollGameTests.registerAll()`. Javadoc: `/** GameTests of the Mirror effect and its images (Parts 6-7 spec §7.2, §10). */`.

```java
static void registerAll() {
    FangGameTests.addPadded(SHARED, "mirror_effect_spawns_thirteen_images", MirrorGameTests::spawnsThirteen);
    FangGameTests.addPadded(SHARED, "mirror_images_stand_on_the_ring", MirrorGameTests::standOnTheRing);
    FangGameTests.addPadded(SHARED, "mirror_images_hold_copies_of_the_carriers_items", MirrorGameTests::holdCopiesOfItems);
    FangGameTests.addPadded(SHARED, "mirror_player_images_carry_the_players_mark", MirrorGameTests::playerImagesAreAllies);
    FangGameTests.addPadded(SHARED, "mirror_mob_images_carry_no_mark", MirrorGameTests::mobImagesHaveNoMark);
    FangGameTests.addPadded(SHARED, "mirror_image_dies_without_drops_or_experience", MirrorGameTests::diesWithNoDrops);
    FangGameTests.addPadded(SHARED, "mirror_images_poof_when_the_effect_is_removed", MirrorGameTests::poofOnRemove);
    FangGameTests.addPadded(SHARED, "mirror_images_poof_when_all_effects_are_cleared", MirrorGameTests::poofOnClearAll);
    FangGameTests.addPadded(SHARED, "mirror_images_poof_on_their_own_life_when_the_caster_is_gone", MirrorGameTests::poofOnOwnLife);
    FangGameTests.addPadded(SHARED, "mirror_images_poof_when_a_mob_carrier_is_killed", MirrorGameTests::poofWhenAMobCarrierIsKilled);
    FangGameTests.addPadded(SHARED, "mirror_images_poof_when_a_player_carrier_dies", MirrorGameTests::poofWhenAPlayerCarrierDies);
    FangGameTests.addPadded(SHARED, "mirror_the_last_image_dying_removes_the_effect", MirrorGameTests::lastImageRemovesTheEffect);
    FangGameTests.addPadded(SHARED, "mirror_image_saves_and_loads", MirrorGameTests::savesAndLoads);
    FangGameTests.addPadded(SHARED, "mirror_image_without_a_caster_discards_itself", MirrorGameTests::discardsWithNoCaster);
    FangGameTests.addPadded(SHARED, "mirror_images_wander_near_the_caster", 200, MirrorGameTests::wanderNearTheCaster);
    FangGameTests.addPadded(SHARED, "mirror_an_idle_image_faces_where_the_caster_faces", MirrorGameTests::idleImageFacesTheCaster);
    FangGameTests.addPadded(SHARED, "mirror_a_second_cast_makes_no_extra_images", MirrorGameTests::secondCastMakesNoExtras);
    FangGameTests.addPadded(SHARED, "mirror_images_never_spawn_inside_blocks", MirrorGameTests::tunnelKeepsImagesOutOfWalls);
    // Scripts every die, so it runs in an environment of its own (Dice is global).
    SpellcraftGameTests.add("dice_unscript", "dice_unscript_all_clears_every_die", MirrorGameTests::unscriptAllClearsEveryDie);
}
```

Bodies (each starts with `ServerPlayer player = plainPlayer(helper); floor(helper, 6);` unless it uses another carrier; imports: statics from `ScrollTestSupport`):

```java
/** When Mirror is cast on a player, should exactly 13 images of them exist. */
static void spawnsThirteen(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = plainPlayer(helper);
    floor(helper, 6);
    // Act
    Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
    // Assert
    helper.assertValueEqual(images(player).size(), MirrorRule.IMAGES, "thirteen images");
    helper.succeed();
}

/** When Mirror is cast over a floor, should the 13 images stand on 13 different ring cells 2 to 3 blocks away. */
static void standOnTheRing(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = plainPlayer(helper);
    floor(helper, 6);
    // Act
    Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
    // Assert
    Set<BlockPos> cells = new HashSet<>();
    for (MirrorImage image : images(player)) {
        BlockPos cell = image.blockPosition();
        int reach = Math.max(
                Math.abs(cell.getX() - player.blockPosition().getX()),
                Math.abs(cell.getZ() - player.blockPosition().getZ()));
        helper.assertTrue(reach >= VexRing.MIN_DISTANCE && reach <= VexRing.MAX_DISTANCE, "ring distance " + reach);
        cells.add(cell);
    }
    helper.assertValueEqual(cells.size(), MirrorRule.IMAGES, "13 different cells");
    helper.succeed();
}

/** When the carrier holds 3 bricks and a shield, should every image hold copies, not the carrier's stacks. */
static void holdCopiesOfItems(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = plainPlayer(helper);
    floor(helper, 6);
    player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BRICK, 3));
    player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
    // Act
    Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
    // Assert
    for (MirrorImage image : images(player)) {
        helper.assertTrue(image.getMainHandItem().is(Items.BRICK), "main hand");
        helper.assertTrue(image.getOffhandItem().is(Items.SHIELD), "offhand");
    }
    images(player).get(0).getMainHandItem().setCount(1);
    helper.assertValueEqual(player.getMainHandItem().getCount(), 3, "the carrier's stack is untouched");
    helper.succeed();
}

/** When a player casts Mirror, should each image carry that player's summoner mark and count as the player's ally. */
static void playerImagesAreAllies(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = plainPlayer(helper);
    floor(helper, 6);
    // Act
    Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
    // Assert
    for (MirrorImage image : images(player)) {
        helper.assertValueEqual(image.getExistingData(AllyAttachments.SUMMONER).orElse(null), player.getUUID(), "mark");
        helper.assertTrue(Allies.isAllyOf(player, image), "an ally, so fangs spare it");
    }
    helper.succeed();
}

/** When an evoker has Mirror, should its images copy the evoker's type and carry no summoner mark. */
static void mobImagesHaveNoMark(GameTestHelper helper) {
    // Arrange
    plainPlayer(helper);
    floor(helper, 6);
    Evoker evoker = foeAt(helper, EntityTypes.EVOKER, at(helper, 0.0, 0.0, 0.0));
    // Act
    Spells.cast(evoker, Spells.MIRROR, MirrorRule.DURATION_TICKS);
    // Assert
    helper.assertValueEqual(images(evoker).size(), MirrorRule.IMAGES, "thirteen");
    for (MirrorImage image : images(evoker)) {
        helper.assertTrue(image.copiedType().equals(EntityTypes.EVOKER), "looks like an evoker");
        helper.assertFalse(image.hasData(AllyAttachments.SUMMONER), "no mark");
    }
    helper.succeed();
}

/**
 * When an image takes lethal damage, should it die and leave no item and no experience orb, its hands' items included.
 */
static void diesWithNoDrops(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = plainPlayer(helper);
    floor(helper, 6);
    player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
    Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
    MirrorImage image = images(player).get(0);
    ServerLevel level = helper.getLevel();
    // Act
    image.hurtServer(level, level.damageSources().generic(), 100.0F);
    // Assert
    AABB around = image.getBoundingBox().inflate(6.0);
    helper.assertFalse(image.isAlive(), "dead");
    helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, around).isEmpty(), "no drops");
    helper.assertTrue(level.getEntitiesOfClass(ExperienceOrb.class, around).isEmpty(), "no experience");
    helper.succeed();
}

/** When the carrier loses the effect (expiry, dispel), should every image poof within a few ticks. */
static void poofOnRemove(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = plainPlayer(helper);
    floor(helper, 6);
    Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
    // Act
    player.removeEffect(ModEffects.MIRROR);
    // Assert
    helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(images(player).isEmpty(), "the images are gone"))
            .thenSucceed();
}

/** When every effect is cleared (milk, a totem), should the images poof too. */
static void poofOnClearAll(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = plainPlayer(helper);
    floor(helper, 6);
    Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
    // Act
    player.removeAllEffects();
    // Assert
    helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(images(player).isEmpty(), "the images are gone"))
            .thenSucceed();
}

/**
 * When the caster is removed without dying (unloaded with its chunk), should each image still end when its own life
 * runs out. (A killed or discarded mob takes its images with it: {@code poofWhenAMobCarrierIsKilled}.)
 */
static void poofOnOwnLife(GameTestHelper helper) {
    // Arrange
    plainPlayer(helper);
    floor(helper, 6);
    Evoker evoker = foeAt(helper, EntityTypes.EVOKER, at(helper, 0.0, 0.0, 0.0));
    Spells.cast(evoker, Spells.MIRROR, MirrorRule.DURATION_TICKS);
    List<MirrorImage> images = images(evoker);
    // Act: UNLOADED_TO_CHUNK skips LivingEntity.triggerOnDeathMobEffects, so MirrorEffect.onMobRemoved does not run
    evoker.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
    helper.assertTrue(images.stream().allMatch(MirrorImage::isAlive), "the images outlive an unloaded carrier");
    images.forEach(image -> image.setLifeTicks(3));
    // Assert
    helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(images.stream().noneMatch(MirrorImage::isAlive), "all ended"))
            .thenSucceed();
}

/** When a mob carrier is killed (removed with the KILLED reason), should all its images poof at once. */
static void poofWhenAMobCarrierIsKilled(GameTestHelper helper) {
    // Arrange
    plainPlayer(helper);
    floor(helper, 6);
    Evoker evoker = foeAt(helper, EntityTypes.EVOKER, at(helper, 0.0, 0.0, 0.0));
    Spells.cast(evoker, Spells.MIRROR, MirrorRule.DURATION_TICKS);
    helper.assertValueEqual(images(evoker).size(), MirrorRule.IMAGES, "thirteen to start with");
    // Act
    evoker.remove(Entity.RemovalReason.KILLED);
    // Assert
    helper.assertTrue(images(evoker).isEmpty(), "the images went with their carrier");
    helper.succeed();
}

/** When a player carrier dies (its effects stay until the respawn), should all its images poof at once. */
static void poofWhenAPlayerCarrierDies(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = plainPlayer(helper);
    floor(helper, 6);
    Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
    helper.assertValueEqual(images(player).size(), MirrorRule.IMAGES, "thirteen to start with");
    ServerLevel level = helper.getLevel();
    // Act
    player.hurtServer(level, level.damageSources().genericKill(), 1000.0F);
    // Assert
    helper.assertFalse(player.isAlive(), "the player died");
    helper.assertTrue(player.hasEffect(ModEffects.MIRROR), "vanilla keeps a dead player's effects");
    helper.assertTrue(images(player).isEmpty(), "the images did not wait for the effect to go");
    helper.succeed();
}

/** When the images die one by one, should the effect stay until the last one dies and then go. */
static void lastImageRemovesTheEffect(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = plainPlayer(helper);
    floor(helper, 6);
    Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
    ServerLevel level = helper.getLevel();
    List<MirrorImage> images = images(player);
    // Act & Assert
    for (int i = 0; i < images.size() - 1; i++) {
        images.get(i).hurtServer(level, level.damageSources().generic(), 100.0F);
    }
    helper.assertTrue(player.hasEffect(ModEffects.MIRROR), "twelve dead, one left: still Mirror");
    images.get(images.size() - 1).hurtServer(level, level.damageSources().generic(), 100.0F);
    helper.assertFalse(player.hasEffect(ModEffects.MIRROR), "the last image's death ends the effect");
    helper.succeed();
}

/** When an image is saved and loaded, should it keep its caster, life, look and held item. */
static void savesAndLoads(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = plainPlayer(helper);
    floor(helper, 6);
    player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BRICK, 2));
    Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
    MirrorImage image = images(player).get(0);
    image.setLifeTicks(1234);
    // Act
    Entity copy = GameTestSupport.reloaded(helper.getLevel(), image);
    // Assert
    helper.assertTrue(copy instanceof MirrorImage, "an image");
    MirrorImage loaded = (MirrorImage) copy;
    helper.assertValueEqual(loaded.casterId().orElse(null), player.getUUID(), "caster");
    helper.assertValueEqual(loaded.lifeTicks(), 1234, "life");
    helper.assertTrue(loaded.copiedType().equals(EntityTypes.PLAYER), "look");
    helper.assertTrue(loaded.getMainHandItem().is(Items.BRICK), "held item");
    helper.succeed();
}

/** When an image is summoned with no caster (as /summon does), should it discard itself on its first tick. */
static void discardsWithNoCaster(GameTestHelper helper) {
    // Arrange & Act
    MirrorImage image = helper.spawn(ModEntities.MIRROR_IMAGE.get(), new BlockPos(1, 2, 1));
    // Assert
    helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(image.isRemoved(), "discarded"))
            .thenSucceed();
}

/**
 * When 140 ticks pass over a floor, should every image still be within 8 blocks of the carrier, with less life, and
 * at least 3 of the 13 should have walked at least a block from where they started (an image that never moves, whatever
 * the cause, fails this).
 */
static void wanderNearTheCaster(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = plainPlayer(helper);
    floor(helper, 12);
    Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
    Map<MirrorImage, Vec3> starts = new HashMap<>();
    images(player).forEach(image -> starts.put(image, image.position()));
    // Act & Assert
    helper.startSequence()
            .thenExecuteAfter(140, () -> {
                int moved = 0;
                for (MirrorImage image : images(player)) {
                    helper.assertTrue(image.distanceTo(player) <= MirrorRule.WANDER_RADIUS + 2.0, "within reach");
                    helper.assertTrue(image.lifeTicks() < MirrorRule.LIFE_TICKS, "its life counts down");
                    Vec3 start = starts.get(image);
                    double dx = image.getX() - start.x;
                    double dz = image.getZ() - start.z;
                    if (dx * dx + dz * dz >= 1.0) {
                        moved++;
                    }
                }
                helper.assertTrue(moved >= 3, "at least 3 of 13 walked a block, got " + moved);
            })
            .thenSucceed();
}

/** When an image has arrived at its wander target, should it face the way the caster faces. */
static void idleImageFacesTheCaster(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = plainPlayer(helper);
    floor(helper, 6);
    Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
    MirrorImage image = images(player).get(0);
    player.setYRot(90.0F);
    // Act: the image's target is where it already stands, so it has "arrived"
    image.setWanderTo(image.position());
    // Assert
    helper.startSequence()
            .thenExecuteAfter(3, () -> helper.assertValueEqual(image.getYRot(), 90.0F, "faces where the caster faces"))
            .thenSucceed();
}

/**
 * When Mirror is cast in a one-wide stone tunnel, should all 13 images exist, none inside a block, and still all 13
 * after 20 ticks (an image in a block takes wall damage and dies).
 */
static void tunnelKeepsImagesOutOfWalls(GameTestHelper helper) {
    // Arrange: solid stone around the standing spot, with only a one-wide corridor (two high) along z
    ServerPlayer player = plainPlayer(helper);
    for (int dz = -6; dz <= 6; dz++) {
        for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -1; dy <= 3; dy++) {
                boolean corridor = dx == 0 && (dy == 0 || dy == 1);
                helper.setBlock(FangGameTests.FEET.offset(dx, dy, dz), corridor ? Blocks.AIR : Blocks.STONE);
            }
        }
    }
    // Act
    Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
    // Assert
    helper.assertValueEqual(images(player).size(), MirrorRule.IMAGES, "thirteen images");
    helper.assertTrue(images(player).stream().noneMatch(MirrorImage::isInWall), "none starts inside a block");
    helper.startSequence()
            .thenExecuteAfter(20, () -> {
                helper.assertValueEqual(images(player).size(), MirrorRule.IMAGES, "still thirteen: none took wall damage");
                helper.assertTrue(images(player).stream().noneMatch(MirrorImage::isInWall), "none is inside a block");
            })
            .thenSucceed();
}

/**
 * When every die is scripted with a roll that is out of range for its bound and {@code Dice.unscriptAll()} runs,
 * should each die answer from the real random source (a leaked script would throw).
 */
static void unscriptAllClearsEveryDie(GameTestHelper helper) {
    // Arrange
    RandomSource random = helper.getLevel().getRandom();
    List<Dice> dice = List.of(Dice.RAID_KIT, Dice.CASTER_ROLL, Dice.SCROLL_MOB, Dice.MIRROR_CONFUSION);
    dice.forEach(die -> die.script(5));
    // Act
    Dice.unscriptAll();
    // Assert
    for (Dice die : dice) {
        helper.assertValueEqual(die.below(random, 1), 0, "no script is left: the only roll below 1 is 0");
    }
    helper.succeed();
}

/**
 * When Mirror is removed and cast again in the same tick, should exactly 13 images exist (the old set poofs at once).
 */
static void secondCastMakesNoExtras(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = plainPlayer(helper);
    floor(helper, 6);
    Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
    player.removeEffect(ModEffects.MIRROR);
    // Act
    Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
    // Assert
    helper.assertValueEqual(images(player).size(), MirrorRule.IMAGES, "thirteen, not twenty-six");
    helper.succeed();
}
```

Add `"entity.spellcraft.mirror_image": "Mirror Image",` to the lang file (after `"entity.spellcraft.bound_vex"`) and the matching `.contains("\"entity.spellcraft.mirror_image\"")` line to `SpellAssetsTest.part7NamesAreEnglish` (Task 3 added the test with one line; reword its Javadoc to "casting effect and the Mirror image").

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew compileJava` (expected: FAIL, `MirrorImage`/`ModEntities.MIRROR_IMAGE` do not exist).

- [x] **Step 3: Write the implementation**

`Dice` (append beside `RAID_KIT`). Rewrite the class Javadoc's last two sentences, from "{@code TeamMoves.clear()} (server stop) calls {@link #unscript} on {@link #RAID_KIT}" on, to: "{@link #unscriptAll} names every instance, and {@code TeamMoves.clear()} (server stop) calls it, so a leaked script cannot outlive a world. A new die must be added to {@link #unscriptAll}; the GameTest {@code dice_unscript_all_clears_every_die} fails when one is forgotten." The first users of the new dice are Tasks 5 (`MIRROR_CONFUSION`), 8 (`CASTER_ROLL`) and 10 (`SCROLL_MOB`), which test them; `MirrorGameTests.unscriptAllClearsEveryDie` (Step 1) covers the shared `unscriptAll`.

```java
/** The Caster roll at each summon: a roll of 0 in {@code [0, 3)} makes a Caster (1 in 3). */
public static final Dice CASTER_ROLL = new Dice();

/** Scroll mobs: per spawning mob the "carries one" roll, then (for a carrier) the spell: 0 Mirror, 1 Ghostrail. */
public static final Dice SCROLL_MOB = new Dice();

/** The Mirror confusion roll, {@code [0, MirrorRule.CONFUSION_BOUND)}. */
public static final Dice MIRROR_CONFUSION = new Dice();

/** Drops every queued roll of every instance. */
public static void unscriptAll() {
    RAID_KIT.unscript();
    CASTER_ROLL.unscript();
    SCROLL_MOB.unscript();
    MIRROR_CONFUSION.unscript();
}
```

`ModEntities` (add the type and the attributes):

```java
/**
 * The Mirror image (Parts 6-7 spec §7.2): a copy of a caster that wanders and can be hit. Not a mob: a living entity
 * with its own small walk. Sized at spawn like its caster ({@link MirrorImage#getDefaultDimensions}); no loot table.
 */
public static final DeferredHolder<EntityType<?>, EntityType<MirrorImage>> MIRROR_IMAGE = ENTITIES.registerEntityType(
        "mirror_image",
        MirrorImage::new,
        MobCategory.MISC,
        builder -> builder.sized(0.6F, 1.8F).clientTrackingRange(8).noLootTable());

// in onAttributes:
event.put(
        MIRROR_IMAGE.get(),
        LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .build());
```

(Check in `build/mc-src` that `AttributeSupplier.Builder.add(holder, value)` replaces the value `createLivingAttributes` already set; if it throws on a duplicate, build from `AttributeSupplier.builder()` plus every attribute `LivingEntity` needs.)

`MirrorImage`:

```java
/**
 * One of the thirteen copies a Mirror makes (Parts 6-7 spec §7.2). A {@code LivingEntity}, not a mob: health 10, no
 * attack, no pathfinding. It wanders within {@link MirrorRule#WANDER_RADIUS} blocks of its caster by walking at a
 * random point and jumping one block, lives {@link MirrorRule#LIFE_TICKS} ticks of its own, and poofs when its caster
 * is loaded in its level and no longer has Mirror. For a player caster it carries the player's summoner mark, so {@code
 * Allies} treat it as the player's own. The client draws it as a stand-in of {@link #copiedType()} (Task 14).
 */
public final class MirrorImage extends LivingEntity {
    /** Synced: the registry id of the type the image copies, e.g. {@code minecraft:player}. */
    private static final EntityDataAccessor<String> COPIED_TYPE =
            SynchedEntityData.defineId(MirrorImage.class, EntityDataSerializers.STRING);

    /** Synced: the profile of a player caster (the skin), else {@link Mannequin#DEFAULT_PROFILE}. */
    private static final EntityDataAccessor<ResolvableProfile> PROFILE =
            SynchedEntityData.defineId(MirrorImage.class, EntityDataSerializers.RESOLVABLE_PROFILE);

    /** Save key of the caster's UUID. */
    private static final String CASTER_TAG = "caster";

    /** Save key of the ticks left of the image's own life. */
    private static final String LIFE_TAG = "life_ticks";

    /** Save key of the copied entity type's registry id. */
    private static final String TYPE_TAG = "copied_type";

    /** Save key of the player profile. */
    private static final String PROFILE_TAG = "profile";

    /** Fewest ticks an image keeps one wander target (it idles there once it arrives). */
    private static final int WANDER_MIN_TICKS = 60;

    /** Extra ticks, at most, on top of {@link #WANDER_MIN_TICKS}. */
    private static final int WANDER_SPREAD_TICKS = 61;

    /** The speed an image walks at, as {@code setSpeed} takes it. */
    private static final float WALK_SPEED = 0.1F;

    /** The caster's UUID, or null for an image nobody made (it discards itself on its first tick). */
    private @Nullable UUID casterId;

    /** Ticks left of this image's own life. */
    private int lifeTicks = MirrorRule.LIFE_TICKS;

    /** Ticks until the image picks a new wander target. */
    private int wanderTicks;

    /** The point the image is walking to (idling at, once there), or null before its first pick. */
    private @Nullable Vec3 wanderTo;

    /**
     * Creates an image (the entity type's factory; also used when a save is loaded).
     *
     * @param type the entity type
     * @param level the level
     */
    public MirrorImage(EntityType<? extends MirrorImage> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(COPIED_TYPE, "minecraft:player");
        entityData.define(PROFILE, Mannequin.DEFAULT_PROFILE);
    }

    /**
     * The caster this image copies.
     *
     * @return the caster's UUID, if this image has one
     */
    public Optional<UUID> casterId() {
        return Optional.ofNullable(casterId);
    }

    /**
     * The type this image looks like.
     *
     * @return the type this image copies; the player when the id is unknown
     */
    public EntityType<?> copiedType() {
        return BuiltInRegistries.ENTITY_TYPE
                .getOptional(Identifier.tryParse(entityData.get(COPIED_TYPE)))
                .orElse(EntityTypes.PLAYER);
    }

    /**
     * The skin this image wears.
     *
     * @return the skin profile of a player caster, else the default profile
     */
    public ResolvableProfile profile() {
        return entityData.get(PROFILE);
    }

    /**
     * How long this image still lives on its own.
     *
     * @return the ticks left of this image's own life
     */
    public int lifeTicks() {
        return lifeTicks;
    }

    /**
     * Sets the ticks left of this image's own life (tests shorten it).
     *
     * @param ticks the new number of ticks left
     */
    public void setLifeTicks(int ticks) {
        lifeTicks = ticks;
    }

    /**
     * Sends the image to walk to {@code point} (a test hook: setting the image's own position makes it idle there,
     * facing where its caster faces, until the next pick).
     *
     * @param point the new wander target
     */
    public void setWanderTo(Vec3 point) {
        wanderTo = point;
        wanderTicks = WANDER_MIN_TICKS;
    }

    /**
     * Makes this image a copy of {@code caster}: the caster id, the copied type and profile, copies of both hands, and,
     * for a player, the player's summoner mark. Call once, before the image is added to the level.
     *
     * @param caster whoever has the Mirror effect
     */
    public void bind(LivingEntity caster) {
        casterId = caster.getUUID();
        entityData.set(COPIED_TYPE, BuiltInRegistries.ENTITY_TYPE.getKey(caster.getType()).toString());
        if (caster instanceof Player player) {
            entityData.set(PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
            setData(AllyAttachments.SUMMONER, player.getUUID());
        }
        setItemSlot(EquipmentSlot.MAINHAND, caster.getMainHandItem().copy());
        setItemSlot(EquipmentSlot.OFFHAND, caster.getOffhandItem().copy());
        refreshDimensions();
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return copiedType().getDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (COPIED_TYPE.equals(accessor)) {
            refreshDimensions();
        }
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    /**
     * Images are not pushable: they overlap each other and the carrier freely, nothing shoves them, and they do not
     * count toward {@code MAX_ENTITY_CRAMMING}. (Overriding {@code doPush} and {@code push} is not enough: a player's
     * {@code doPush(image)} calls {@code image.push(player)}, which moves both.)
     */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server) {
            serverTick(server);
        }
    }

    /** Ends the image when it has no caster, no life left, or a caster that lost Mirror. */
    private void serverTick(ServerLevel server) {
        if (casterId == null) {
            discard();
            return;
        }
        if (--lifeTicks <= 0) {
            poof();
            MirrorImages.afterEnd(server, casterId, this);
            return;
        }
        if (server.getEntities().get(casterId) instanceof LivingEntity caster && !caster.hasEffect(ModEffects.MIRROR)) {
            poof();
        }
    }

    /** Walks toward a random point near the caster; faces where the caster faces when it has arrived. */
    @Override
    protected void serverAiStep() {
        super.serverAiStep();
        LivingEntity caster = casterId != null && level() instanceof ServerLevel server
                        && server.getEntities().get(casterId) instanceof LivingEntity found
                ? found
                : null;
        Vec3 center = caster != null ? caster.position() : position();
        // A new target only when the timer runs out: an image that has arrived idles (facing the caster) until then.
        if (wanderTo == null || --wanderTicks <= 0) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = random.nextDouble() * MirrorRule.WANDER_RADIUS;
            wanderTo = center.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            wanderTicks = WANDER_MIN_TICKS + random.nextInt(WANDER_SPREAD_TICKS);
        }
        double dx = wanderTo.x - getX();
        double dz = wanderTo.z - getZ();
        if (dx * dx + dz * dz < 1.0) {
            zza = 0.0F;
            if (caster != null) {
                setYRot(caster.getYRot());
                yBodyRot = getYRot();
                yHeadRot = getYRot();
            }
            return;
        }
        setYRot((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F);
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        setSpeed(WALK_SPEED);
        zza = 1.0F;
        setJumping(horizontalCollision && onGround());
    }

    /** Ends the image with a puff and no sound. */
    public void poof() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() / 2.0, getZ(), 8, 0.2, 0.3, 0.2, 0.02);
        }
        discard();
    }

    /** A dead image may have been the last one: then the caster's Mirror ends with it. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server && casterId != null) {
            MirrorImages.afterEnd(server, casterId, this);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (casterId != null) {
            output.store(CASTER_TAG, UUIDUtil.CODEC, casterId);
        }
        output.putInt(LIFE_TAG, lifeTicks);
        output.putString(TYPE_TAG, entityData.get(COPIED_TYPE));
        output.store(PROFILE_TAG, ResolvableProfile.CODEC, entityData.get(PROFILE));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        casterId = input.read(CASTER_TAG, UUIDUtil.CODEC).orElse(null);
        lifeTicks = input.getIntOr(LIFE_TAG, MirrorRule.LIFE_TICKS);
        entityData.set(COPIED_TYPE, input.getStringOr(TYPE_TAG, "minecraft:player"));
        input.read(PROFILE_TAG, ResolvableProfile.CODEC).ifPresent(profile -> entityData.set(PROFILE, profile));
        refreshDimensions();
    }
}
```

(Verify against the sources: `ValueOutput.putInt/putString/store`, `ValueInput.getIntOr/getStringOr/read` (the Bound Vex uses `putBoolean`, `getBooleanOr`, `store`, `read`), `LivingEntity.setJumping`, `Mth.RAD_TO_DEG`, and that `LivingEntity.addAdditionalSaveData` already saves the equipment (the `savesAndLoads` test asserts the held item); if it does not, save both hands with `ItemStack.CODEC` as `BoundVex` saves its stash.)

`MirrorImages`:

```java
/**
 * Spawning, ending and the confusion of Mirror images (Parts 6-7 spec §7.2). The death handler is here; Task 5 adds the
 * confusion handler beside it (same class, same annotation).
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class MirrorImages {
    private MirrorImages() {}

    /**
     * The live images of {@code caster} in {@code level}, at any distance.
     *
     * @param level the level
     * @param caster the carrier's UUID
     * @return the images, in query order
     */
    public static List<MirrorImage> of(ServerLevel level, UUID caster) {
        return List.copyOf(level.getEntities(
                EntityTypeTest.forClass(MirrorImage.class),
                image -> image.isAlive() && image.casterId().filter(caster::equals).isPresent()));
    }

    /**
     * Spawns {@link MirrorRule#IMAGES} images of {@code carrier} on its ring cells ({@link VexRing}, Chebyshev 2 to 3):
     * standable cells first in a random order, then any other cell where an image's box is free of blocks, then the
     * carrier's own spot for every image that is left (a tunnel has few free cells; the carrier's spot is free by
     * definition). An image is never placed inside a block, so none takes wall damage on its first ticks. Images the
     * carrier still has are poofed first, so a recast in the tick after a removal never doubles the set.
     *
     * @param carrier whoever just got the Mirror effect
     */
    public static void spawn(LivingEntity carrier) {
        if (!(carrier.level() instanceof ServerLevel level)) {
            return;
        }
        poofAll(carrier);
        BlockPos feet = carrier.blockPosition();
        EntityDimensions size = carrier.getType().getDimensions();
        List<BlockPos> cells = new ArrayList<>();
        for (int i = 0; i < VexRing.COUNT; i++) {
            VexRing.Offset offset = VexRing.offset(i);
            cells.add(feet.offset(offset.dx(), 0, offset.dz()));
        }
        Collections.shuffle(cells, new java.util.Random(level.getRandom().nextLong()));
        List<BlockPos> ordered = new ArrayList<>();
        cells.stream().filter(cell -> StandableSpots.standable(level, cell)).forEach(ordered::add);
        cells.stream()
                .filter(cell -> !ordered.contains(cell))
                .filter(cell -> level.noBlockCollision(null, size.makeBoundingBox(Vec3.atBottomCenterOf(cell))))
                .forEach(ordered::add);
        for (int i = 0; i < MirrorRule.IMAGES; i++) {
            MirrorImage image = ModEntities.MIRROR_IMAGE.get().create(level, EntitySpawnReason.MOB_SUMMONED);
            if (image == null) {
                return;
            }
            image.bind(carrier);
            Vec3 spot = i < ordered.size() ? Vec3.atBottomCenterOf(ordered.get(i)) : carrier.position();
            image.snapTo(spot.x, spot.y, spot.z, carrier.getYRot(), 0.0F);
            level.addFreshEntity(image);
        }
    }

    /**
     * Poofs every live image of {@code carrier} at once.
     *
     * @param carrier the carrier
     */
    public static void poofAll(LivingEntity carrier) {
        if (carrier.level() instanceof ServerLevel level) {
            of(level, carrier.getUUID()).forEach(MirrorImage::poof);
        }
    }

    /**
     * A carrier that dies takes its images with it. A dead player's effects are not cleared (vanilla keeps them until
     * the respawn), so the images' own "caster lost Mirror" check would never fire; a mob's effects are cleared only
     * when it is removed ({@code MirrorEffect.onMobRemoved}), which comes up to 20 ticks after the death. Lowest
     * priority and not called for a cancelled event, so a totem or another mod that cancels the death keeps the images.
     *
     * @param event the death of any living entity
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dying = event.getEntity();
        if (dying.hasEffect(ModEffects.MIRROR)) {
            poofAll(dying);
        }
    }

    /**
     * Called when an image dies or runs out of life: when no other live image of the caster remains and the caster is
     * loaded in {@code level}, its Mirror effect goes, so "no images" and "no effect" are one thing.
     *
     * @param level the image's level
     * @param caster the carrier's UUID
     * @param ending the image that just ended (excluded: it may still be listed this tick)
     */
    public static void afterEnd(ServerLevel level, UUID caster, MirrorImage ending) {
        boolean others = of(level, caster).stream().anyMatch(image -> !image.equals(ending));
        if (!others && level.getEntities().get(caster) instanceof LivingEntity carrier) {
            carrier.removeEffect(ModEffects.MIRROR);
        }
    }
}
```

`MirrorEffect.onEffectAdded` and `onMobRemoved`:

```java
@Override
public void onEffectAdded(LivingEntity mob, int amplifier) {
    super.onEffectAdded(mob, amplifier);
    // The client never reaches this hook (the effect packet goes through forceAddEffect); the test is a guard.
    if (mob.level() instanceof ServerLevel) {
        MirrorImages.spawn(mob);
    }
}

/**
 * A mob carrier that is killed or discarded loses its effects in {@code LivingEntity.remove}, which calls this hook
 * (api-notes §26): its images go with it. A carrier removed any other way (unloaded) keeps them until their own life
 * ends, as the spec says.
 */
@Override
public void onMobRemoved(ServerLevel level, LivingEntity mob, int amplifier, Entity.RemovalReason reason) {
    MirrorImages.poofAll(mob);
}
```

A killed *player* keeps its effects until the respawn, so `MirrorImages.onDeath` (above) poofs a dying carrier's images; the two handlers overlap for a mob (death, then removal) and both are idempotent.

`TeamMoves.clear()`: replace `Dice.RAID_KIT.unscript();` with `Dice.unscriptAll();`.

Add `MirrorGameTests.registerAll();` after `SpellScrollGameTests.registerAll();`.

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:mirror_*"`, `"-PgameTests=spellcraft:spell_scroll_*"` (the Task 3 tests now spawn images) and `"-PgameTests=spellcraft:dice_*"`, and `./gradlew test`. Expected: PASS. If `mirror_images_stand_on_the_ring` finds fewer than 13 distinct cells, read `VexRing.offset` (the ring has 40 cells, and the shell of the test box takes 16 of them, so 13 distinct always exist there).

- [x] **Step 5: Gates and commit**

Run: `./gradlew spotlessApply build`, then `./gradlew runGameTestServer` twice (`runData` is not needed: nothing here is client code it can check). Expected: `All 676 required tests passed` both times (the Task 3 total plus the 19 tests registered here: the 14 `mirror_*` tests of the first list, `mirror_images_poof_when_a_mob_carrier_is_killed`, `mirror_images_poof_when_a_player_carrier_dies`, `mirror_an_idle_image_faces_where_the_caster_faces`, `mirror_images_never_spawn_inside_blocks` and `dice_unscript_all_clears_every_die`).

```bash
git add src
git commit -m "feat(evoking): Mirror images that copy the caster and end with the effect"
```

> **As built (`e4e09ca`):** diverged — one extra GameTest `mirror_tall_images_never_spawn_inside_a_ceiling` (total 677, not 676), and every spawn tier checks the copied type's box; reason: per-task review found a tall copied type could overlap a ceiling. No RED capture.

---

### Task 5: Mirror confusion

**Files:**

- Modify: `evoking/MirrorImages.java` (the event handler), `gametest/SpellcraftGameTests.java`
- Create: `gametest/MirrorConfusionGameTests.java`

**Interfaces:**

- Consumes: Task 4 (`MirrorImages.of`, `Dice.MIRROR_CONFUSION`, `MirrorRule.redirects/CONFUSION_BOUND/CONFUSION_RANGE`).
- Produces: `MirrorImages.onChangeTarget(LivingChangeTargetEvent)` (`@SubscribeEvent`; `MirrorImages` is already `@EventBusSubscriber(modid = Spellcraft.MODID)` since Task 4, which added its death handler).

- [x] **Step 1: Write the failing tests**

`MirrorConfusionGameTests` (javadoc: `/** GameTests of the Mirror confusion (Parts 6-7 spec §7.2: 13 of 14, any mob, never a golem onto a player's images). */`). Tests that script the die register in the environment `mirror_dice` (one batch); the ones that cannot roll register in `SHARED`. Each scripts **immediately before** the call that consumes it and unscripts in a `finally`. Every test in this class (and in Tasks 7-12) gets a When/should Javadoc **and** a `<p>Method under test:` line, as in Task 2's tests; the Javadoc shown on a body below is its When/should part. The mobs here are `Husk`s from `ScrollTestSupport.hostileAt` (Task 4): a husk is a `Zombie` that does not burn in daylight, so a frozen foe lives out the test. In the prose below, every `zombie`, `zombieA` and `zombieB` is such a husk.

```java
/** The dice environment of the Mirror tests: one batch, since the confusion die is global. */
private static final String DICE = "mirror_dice";

static void registerAll() {
    FangGameTests.addPadded(DICE, "mirror_confusion_redirects_when_the_dice_say_so", MirrorConfusionGameTests::redirects);
    FangGameTests.addPadded(DICE, "mirror_confusion_keeps_the_target_when_the_dice_say_no", MirrorConfusionGameTests::keeps);
    FangGameTests.addPadded(DICE, "mirror_confusion_does_not_roll_for_a_mob_already_on_the_carrier", MirrorConfusionGameTests::noRerollOnTheCarrier);
    FangGameTests.addPadded(DICE, "mirror_confusion_does_not_roll_for_a_mob_already_on_an_image", MirrorConfusionGameTests::noRerollOnAnImage);
    FangGameTests.addPadded(DICE, "mirror_confusion_ignores_images_beyond_16_blocks", MirrorConfusionGameTests::farImagesDoNotCount);
    FangGameTests.addPadded(DICE, "mirror_confusion_never_sends_a_golem_onto_a_players_images", MirrorConfusionGameTests::golemAndPlayer);
    FangGameTests.addPadded(DICE, "mirror_confusion_confuses_a_golem_going_for_a_mob_carrier", MirrorConfusionGameTests::golemAndEvoker);
}
```

```java
/** A frozen husk (no AI, no gravity) 4 blocks from the standing spot. */
private static Husk zombie(GameTestHelper helper) {
    return hostileAt(helper, at(helper, 4.0, 0.0, 0.0));
}

/**
 * When the dice roll 0 and a zombie picks a Mirror carrier as its target, should the zombie end up targeting one of
 * that carrier's images.
 */
static void redirects(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = plainPlayer(helper);
    floor(helper, 6);
    Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
    Husk zombie = zombie(helper);
    // Act
    try {
        Dice.MIRROR_CONFUSION.script(0);
        zombie.setTarget(player);
    } finally {
        Dice.unscriptAll();
    }
    // Assert
    helper.assertTrue(zombie.getTarget() instanceof MirrorImage image
            && image.casterId().filter(player.getUUID()::equals).isPresent(), "the target is an image of the player");
    helper.succeed();
}
```

`keeps`: script `MirrorRule.CONFUSION_BOUND - 1` (13); after `setTarget(player)` assert `zombie.getTarget().equals(player)`.

`noRerollOnTheCarrier`: script `13, 0`; `zombieA.setTarget(player)` twice (the first consumes 13 and keeps the player; the second is not a new pick and must not consume the 0); `zombieB.setTarget(player)` (consumes 0: redirected). Assert A's target equals `player`, B's target is a `MirrorImage`, in `finally` `Dice.unscriptAll()`.

`noRerollOnAnImage`: script `0`; `zombieA.setTarget(images(player).get(0))` (the new target is an image: no roll); `zombieA.setTarget(player)` (its current target is an image: no roll); assert A still targets that image; `zombieB.setTarget(player)` consumes the 0 and is redirected (proves nothing was spent).

`farImagesDoNotCount`: cast; for each image `image.setPos(image.getX() + 20.0, image.getY(), image.getZ())` (the images are more than 16 blocks from the carrier); script `0`; `zombie.setTarget(player)`; assert the target equals the player; `finally unscriptAll()`.

`golemAndPlayer`: `IronGolem golem = foeAt(helper, EntityTypes.IRON_GOLEM, at(helper, 4.0, 0.0, 0.0))` (a golem is not a hostile `Enemy`, so `foeAt` with its type, not `hostileAt`); cast on the player; script `0`; `golem.setTarget(player)`; assert the target equals the player (a golem is never redirected onto a player's images).

`golemAndEvoker`: an evoker carrier at `at(helper, 0, 0, 0)` with `Spells.cast(evoker, Spells.MIRROR, …)`; script `0`; `golem.setTarget(evoker)`; assert the golem's target is a `MirrorImage` whose `casterId` is the evoker's UUID (a golem going for an evoker with images is fooled too).

Add `MirrorConfusionGameTests.registerAll();` after `MirrorGameTests.registerAll();`.

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:mirror_confusion_*"`. Expected: the redirect tests FAIL (no handler yet), the keep tests pass.

- [x] **Step 3: Write the implementation**

Add to `MirrorImages` (the class is already annotated `@EventBusSubscriber(modid = Spellcraft.MODID)`):

```java
/**
 * Confusion (spec §7.2; amends the parent §9.3 to any mob): when a mob is about to pick a carrier that has live
 * images within {@link MirrorRule#CONFUSION_RANGE} of the carrier, the new target is replaced, with probability 13/14,
 * by a random one of those images. Skipped when the new target has no Mirror effect (this event fires for every
 * target change of every mob in the world, and finding images walks every entity in the level, so the cheap
 * effect test comes first; "no images alive" and "no Mirror effect" are the same thing), when the new target is
 * already an image, when it is the mob's current target (a target goal re-sets its target every tick, and each
 * re-set fires this event; rolling on those would redirect almost every mob), when the mob already targets an
 * image, and for an iron golem going for a player (the images carry the player's mark, which {@code GolemMercy}
 * refuses as a golem's target). The roll is taken only for an eligible change, so a scripted GameTest roll is never
 * spent on a skipped one. Brain mobs that write their target without this event are not confused (accepted).
 *
 * @param event the change-target event for any living entity
 */
@SubscribeEvent
public static void onChangeTarget(LivingChangeTargetEvent event) {
    LivingEntity carrier = event.getNewAboutToBeSetTarget();
    if (carrier == null
            || !carrier.hasEffect(ModEffects.MIRROR)
            || carrier instanceof MirrorImage
            || !(event.getEntity() instanceof Mob mob)
            || !(carrier.level() instanceof ServerLevel level)
            || carrier.equals(mob.getTarget())
            || mob.getTarget() instanceof MirrorImage
            || (mob instanceof IronGolem && carrier instanceof Player)) {
        return;
    }
    List<MirrorImage> near = of(level, carrier.getUUID()).stream()
            .filter(image -> image.distanceTo(carrier) <= MirrorRule.CONFUSION_RANGE)
            .toList();
    if (near.isEmpty()) {
        return;
    }
    int roll = Dice.MIRROR_CONFUSION.below(mob.getRandom(), MirrorRule.CONFUSION_BOUND);
    if (MirrorRule.redirects(roll)) {
        event.setNewAboutToBeSetTarget(near.get(mob.getRandom().nextInt(near.size())));
    }
}
```

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:mirror_*"`. Expected: PASS.

- [x] **Step 5: Gates and commit**

Run: `./gradlew spotlessApply build`, `./gradlew runGameTestServer` twice. Expected: `All 683 required tests passed` both times (the Task 4 total plus the 7 `mirror_confusion_*` tests). No GameTest uses a real target goal to show that the same-target re-set does not re-roll (the reason for the `carrier.equals(mob.getTarget())` guard): Task 15's playtest list has an item for it.

```bash
git add src
git commit -m "feat(evoking): Mirror confuses a mob that picks the carrier 13 times in 14"
```

> **As built (`b30d56d`):** diverged — a mob already targeting a live image of this carrier keeps it (the change is cancelled, no roll), where the plan's handler returned early and let vanilla switch it to the carrier; reason: controller ruling on spec §7.2 "not re-rolled" read with the 13-in-14 rule.

### Task 6: Ghostrail for players: the Mixin and the shared abilities sync

**Files:**

- Create: `mixin/PlayerGhostrailMixin.java`, `gametest/GhostrailFlightGameTests.java`
- Modify: `src/main/resources/spellcraft.mixins.json`, `infinity/InfinityAttachments.java`, `gametest/SpellcraftGameTests.java`
- Rename: `infinity/InfinityAbilitySync.java` to `infinity/AbilitySync.java` (`git mv`); update its four referencing files: `effect/InfinityEffect.java`, `effect/ModEffects.java` (Javadoc), `gametest/InfinityAbilityGameTests.java`, `gametest/InfinityDeathGameTests.java`

**Interfaces:**

- Consumes: Task 2 (`GrantedAbilities`), Task 3 (`ModEffects.GHOSTRAIL`, `Spells.cast`).
- Produces:
  - `AbilitySync.sync(ServerPlayer)` (public static; the one per-tick abilities sync for Infinity and Ghostrail; subscribed to `PlayerTickEvent.Post`); `InfinityAttachments.GHOSTRAIL_GRANTED` (saved `Boolean`, default false, id `ghostrail_granted`; not copied on death).
  - `PlayerGhostrailMixin` (package-private abstract, `@Mixin(Player.class)`): `Player.tick`'s first `isSpectator()` call returns `original || player.hasEffect(ModEffects.GHOSTRAIL)`, `require = 1`.

- [x] **Step 1: Write the failing tests**

`GhostrailFlightGameTests` (javadoc `/** GameTests of a Ghostrail player's physics and flight (Parts 6-7 spec §7.3). */`; no mobs. Tests that run a full player tick through `doTick()` register through `FangGameTests.addPadded(SHARED, …)`, because `ServerPlayer.doTick` (`ServerPlayer.java:670`) runs `super.tick()` only when `!isSpectator() || !touchingUnloadedChunk()`, and an unpadded structure forces only its own chunks, so near a chunk edge the tick (and with it `noPhysics`) can be skipped; the rest use `SpellcraftGameTests.add(SHARED, name, body)`. No existing test calls `doTick()` on a mock player, so the first padded test is its probe: if `doTick()` throws for a mock player (read the stack trace), run the same assertion through `player.tick()` after `FangGameTests.loadPadding(helper)` and say so in the as-built note. The class is `@SuppressWarnings("deprecation")` with the same comment as `InfinityAbilityGameTests`, because every test reads `Abilities#mayfly`). A helper `private static ServerPlayer player(GameTestHelper helper, GameType mode)` = `MockPlayers.inLevel(helper, mode)` then `MockPlayers.moveIntoTest(helper, player)`.

```java
static void registerAll() {
    FangGameTests.addPadded(SHARED, "ghostrail_flight_player_gets_no_physics_through_the_mixin", GhostrailFlightGameTests::noPhysicsThroughTheMixin);
    FangGameTests.addPadded(SHARED, "ghostrail_flight_no_effect_means_physics", GhostrailFlightGameTests::noEffectMeansPhysics);
    FangGameTests.addPadded(SHARED, "ghostrail_flight_spectator_still_has_no_physics", GhostrailFlightGameTests::spectatorStillHasNoPhysics);
    FangGameTests.addPadded(SHARED, "ghostrail_flight_player_on_stone_stays_aloft_and_passes_through_it", GhostrailFlightGameTests::staysAloftOverStone);
    SpellcraftGameTests.add(SHARED, "ghostrail_flight_grants_mayfly_and_flying", GhostrailFlightGameTests::grantsFlight);
    SpellcraftGameTests.add(SHARED, "ghostrail_flight_flying_is_reasserted_each_sync", GhostrailFlightGameTests::reassertsFlying);
    SpellcraftGameTests.add(SHARED, "ghostrail_flight_ending_revokes_the_flight", GhostrailFlightGameTests::endingRevokes);
    SpellcraftGameTests.add(SHARED, "ghostrail_flight_ending_with_infinity_keeps_infinitys_flight", GhostrailFlightGameTests::endingKeepsInfinity);
    SpellcraftGameTests.add(SHARED, "ghostrail_flight_infinity_ending_with_ghostrail_keeps_flight", GhostrailFlightGameTests::infinityEndingKeepsGhostrail);
    SpellcraftGameTests.add(SHARED, "ghostrail_flight_mark_is_saved", GhostrailFlightGameTests::markIsSaved);
    SpellcraftGameTests.add(SHARED, "ghostrail_flight_creative_player_keeps_creative_flight", GhostrailFlightGameTests::creativeKeepsFlight);
}
```

```java
/** When a survival player with Ghostrail runs a full player tick, should noPhysics be true afterwards (the Mixin). */
static void noPhysicsThroughTheMixin(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = player(helper, GameType.SURVIVAL);
    Spells.cast(player, Spells.GHOSTRAIL, GhostrailDuration.PLAYER_TICKS);
    // Act
    player.doTick();
    // Assert
    helper.assertTrue(player.noPhysics, "noPhysics follows the effect");
    helper.succeed();
}

/** When a survival player without Ghostrail runs a full player tick, should noPhysics stay false (the control). */
static void noEffectMeansPhysics(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = player(helper, GameType.SURVIVAL);
    // Act
    player.doTick();
    // Assert
    helper.assertFalse(player.noPhysics, "physics as usual");
    helper.succeed();
}
```

`spectatorStillHasNoPhysics`: `GameType.SPECTATOR`, `doTick()`, `noPhysics` true (the `original ||` half). `grantsFlight`: cast, `AbilitySync.sync(player)`, assert `abilities.mayfly && abilities.flying && !abilities.instabuild`. `reassertsFlying`: cast, sync, `abilities.flying = false`, sync, assert `flying`. `endingRevokes`: cast, sync, `removeEffect(GHOSTRAIL)`, sync: assert `!mayfly && !flying`. `endingKeepsInfinity`: cast Infinity (`Spells.cast(player, Spells.INFINITY, Spell.INFINITE.getAsInt())`) and Ghostrail, sync; remove Ghostrail; sync: assert `mayfly && instabuild && !flying`. `infinityEndingKeepsGhostrail`: both, sync; remove Infinity; sync: assert `mayfly && flying && !instabuild`. `markIsSaved`: cast Ghostrail, sync, then read the saved player the way `InfinityAbilityGameTests.grantedMarkSurvivesSave` reads the Infinity mark (read it first and copy its pattern) and assert the `ghostrail_granted` mark is saved true; also assert `player.getData(InfinityAttachments.GHOSTRAIL_GRANTED)` is true. `creativeKeepsFlight`: `GameType.CREATIVE`; cast Ghostrail (as a command would); set `abilities.flying = false` first, then sync: assert `mayfly` and `instabuild` still true and `flying` still **false** (sync must not force flying on a creative player: creative owns its flags); remove Ghostrail; sync: `mayfly` still true.

`staysAloftOverStone` (padded; the spec's "a player on stone given Ghostrail and stepped 20 ticks keeps its `y`", and the order the Mixin and the sync depend on): `ServerPlayer player = player(helper, GameType.SURVIVAL)` on `ScrollTestSupport.floor(helper, 6)`; record `double y = player.getY()`; cast Ghostrail; run one `player.doTick()` and assert `noPhysics` is true (the Mixin: `Player.tick` assigns `noPhysics` at its top, before the sync) and `abilities.flying` is true (the sync sets it in the Post event of the same tick); then `helper.startSequence().thenExecuteFor(19, player::doTick)`, `thenExecute` assert `getY()` equals `y` exactly, `flying` and `mayfly` still true, `noPhysics` true. Last, `player.move(MoverType.SELF, new Vec3(0.0, -1.5, 0.0))` and assert `getY()` fell by 1.5 (a `noPhysics` move ignores the stone floor; an ordinary player would stop on it). A mock player is not simulated by the server, so the unchanged `y` shows only that nothing in the Mixin or the sync moves the player; the `move` call is what proves the pass-through, and the `flying` assertions are why `AbilitySync` re-asserts it (without flight, `noPhysics` skips collision and a real player sinks).

Add `GhostrailFlightGameTests.registerAll();` after `MirrorConfusionGameTests.registerAll();`.

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:ghostrail_flight_*"`. Expected: FAIL (`AbilitySync` does not exist; the Mixin is missing).

- [x] **Step 3: Write the implementation**

`git mv` the sync to `AbilitySync.java`, rename the class, and update its references in the four files (`InfinityAbilityGameTests`, `InfinityDeathGameTests`, the `InfinityEffect` and `ModEffects` comments and calls). New body:

```java
/**
 * Keeps each player's creative-style abilities in line with Infinity and Ghostrail, every server tick (Parts 6-7
 * spec §7.3; formerly {@code InfinityAbilitySync}). Vanilla's abilities packet syncs the client, which then opens the
 * creative inventory by itself (docs/api-notes.md §11). The decision is the pure {@link GrantedAbilities}. This
 * class is the one driver of the sync: {@code Ghostrail.tick} (Task 7) does not call it, and the two do not depend
 * on each other's order in the Post event (a GameTest that steps {@code Ghostrail.tick} by hand and needs the
 * abilities settled calls {@link #sync} itself).
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class AbilitySync {
    private AbilitySync() {}

    /**
     * Syncs the abilities of every server-side player after each tick.
     *
     * @param event the player's post-tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    /**
     * Applies one {@link GrantedAbilities} plan: a reset to the game mode's abilities when a source ended, the
     * grants of what is still active, both marks. Public so GameTests can run one sync without waiting for a tick.
     *
     * @param player the player to sync
     */
    @SuppressWarnings("deprecation") // Abilities#mayfly is @Deprecated (-Xlint:all -Werror). Granting/revoking it
    // directly is the per-flag control we need: IPlayerExtension#mayFly() only reads, and folds in
    // CREATIVE_FLIGHT-attribute flight that we neither grant nor revoke.
    public static void sync(ServerPlayer player) {
        Abilities abilities = player.getAbilities();
        GrantedAbilities.Plan plan = GrantedAbilities.decide(new GrantedAbilities.State(
                PlayerModes.of(player),
                player.hasEffect(ModEffects.INFINITY),
                player.hasEffect(ModEffects.GHOSTRAIL),
                player.getData(InfinityAttachments.GRANTED),
                player.getData(InfinityAttachments.GHOSTRAIL_GRANTED),
                abilities.mayfly,
                abilities.instabuild,
                abilities.flying));
        if (plan.reset()) {
            player.gameMode().updatePlayerAbilities(abilities);
        }
        if (plan.mayfly()) {
            abilities.mayfly = true;
        }
        if (plan.instabuild()) {
            abilities.instabuild = true;
        }
        if (plan.flying()) {
            // noPhysics skips collision, so a player who is not flying would sink through the floor
            // (as in spectator mode)
            abilities.flying = true;
        }
        // Write a mark only when it changes (the old sync did the same; this runs for every player every tick).
        if (plan.infinityMarked() != player.getData(InfinityAttachments.GRANTED)) {
            player.setData(InfinityAttachments.GRANTED, plan.infinityMarked());
        }
        if (plan.ghostrailMarked() != player.getData(InfinityAttachments.GHOSTRAIL_GRANTED)) {
            player.setData(InfinityAttachments.GHOSTRAIL_GRANTED, plan.ghostrailMarked());
        }
        if (plan.changesAbilities()) {
            player.onUpdateAbilities();
        }
    }
}
```

`InfinityAttachments`:

```java
/**
 * True while Ghostrail (not vanilla, Infinity or another mod) set the player's {@code mayfly} and {@code flying}.
 * Saved, so a relog mid-effect does not leave {@code mayfly} on for good. Not copied on death, as {@link #GRANTED}.
 * Not the same as {@code EvokingAttachments.GHOSTRAIL_MARK} (Task 7): that one records the carrier's original
 * {@code noGravity}, for every kind of carrier; this one records only who set the abilities flags, for players.
 */
public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> GHOSTRAIL_GRANTED =
        ATTACHMENT_TYPES.register(
                "ghostrail_granted",
                () -> AttachmentType.builder(() -> false)
                        .serialize(Codec.BOOL.fieldOf("granted"))
                        .build());
```

`PlayerGhostrailMixin`:

```java
/**
 * Lets a Ghostrail player pass through blocks. {@code Player.tick} sets {@code noPhysics} from {@code isSpectator()}
 * on both sides ({@code LocalPlayer.tick} reaches it; the effect is synced), and no event reaches that assignment,
 * so this wraps the first {@code isSpectator()} call in {@code tick()} (ordinal 0: the statement
 * {@code this.noPhysics = this.isSpectator()}, api-notes §26). The server then accepts the moves
 * ({@code ServerGamePacketListenerImpl} skips its "moved wrongly" check for {@code noPhysics}). {@code require = 1}:
 * a moved call fails startup loudly. The handler is one expression (the original answer or the effect), so there is
 * no logic to share.
 */
@Mixin(Player.class)
abstract class PlayerGhostrailMixin {
    /**
     * Wraps the first {@code isSpectator()} call of {@code Player.tick}: true for a spectator (the original answer)
     * or for a player with Ghostrail.
     *
     * @param player the ticking player
     * @param original the original call
     * @return whether {@code noPhysics} should be set this tick
     */
    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;isSpectator()Z",
                    ordinal = 0),
            require = 1)
    @SuppressWarnings("UnusedMethod") // invoked by Mixin at runtime, not by Java code
    private boolean spellcraft$ghostrailNoPhysics(Player player, Operation<Boolean> original) {
        return original.call(player) || player.hasEffect(ModEffects.GHOSTRAIL);
    }
}
```

`spellcraft.mixins.json`: `"mixins": ["ClearAllStatusEffectsConsumeEffectMixin", "EnchantmentMenuMixin", "PlayerGhostrailMixin"]`.

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:ghostrail_flight_*"`, then `"-PgameTests=spellcraft:infinity_*"` (the Infinity tests must still pass unchanged). If startup fails with a Mixin error, read the target in `build/mc-src/net/minecraft/world/entity/player/Player.java` (the owner of the first `isSpectator()` call in `tick`; api-notes §26) and fix the `target` string, never `require`.
Expected: PASS.

- [x] **Step 5: Gates and commit**

Run: `./gradlew spotlessApply build`, then `./gradlew runGameTestServer` twice (`runData` is not needed: the Mixin is common code that the GameTest server loads, and nothing here is client code). Expected: `All 694 required tests passed` both times (the Task 5 total plus the 11 `ghostrail_flight_*` tests: the 10 of the list and `ghostrail_flight_player_on_stone_stays_aloft_and_passes_through_it`).

```bash
git add src
git commit -m "feat(ghostrail): players pass through blocks and fly; one abilities sync for two spells"
```

> **As built (`dabd455`):** diverged — `staysAloftOverStone` asserts a drift under 0.5 blocks, not an exact y; reason: `ServerPlayer.doTick` runs `aiStep` and the first tick, before the sync sets `flying`, takes full gravity, which flight then damps (reviewed against `Player.travel`).

---

### Task 7: Ghostrail's per-tick driver: no damage, trail, ending, mob carriers, no rescue

**Files:**

- Create: `evoking/Ghostrail.java`, `evoking/EvokingAttachments.java`, `gametest/GhostrailGameTests.java`
- Modify: `Spellcraft.java` (register `EvokingAttachments.ATTACHMENT_TYPES`), `effect/GhostrailEffect.java`, `evoking/VexRescues.java`, `gametest/SpellcraftGameTests.java`, `gametest/BoundVexLavaGameTests.java`, `gametest/BoundVexDrowningGameTests.java`, `gametest/BoundVexVoidGameTests.java`

**Interfaces:**

- Consumes: Task 6 (`AbilitySync.sync`, called by the tests only), Task 4 (`ScrollTestSupport.hostileAt`), `StandableSpots.standable(Level, BlockPos)`.
- Produces:
  - `EvokingAttachments` (public final): `ATTACHMENT_TYPES` (`DeferredRegister<AttachmentType<?>>`), `GHOSTRAIL_MARK` (`AttachmentType<Boolean>`, saved, id `ghostrail_mark`: its presence means "this carrier has Ghostrail running"; its value is the carrier's original `NoGravity`), `GHOSTRAIL_START` (`AttachmentType<BlockPos>`, **not** saved, id `ghostrail_start`: the standable spot the effect began at; read with `getExistingData`), `MOB_MIRROR_READY` and `MOB_GHOSTRAIL_READY` (`AttachmentType<Long>`, saved, ids `mob_mirror_ready` and `mob_ghostrail_ready`: the game time the cooldown ends; Task 10 uses them).
  - `Ghostrail` (public final): `static void begin(LivingEntity carrier)` (called by `GhostrailEffect.onEffectAdded` on a server level: sets the mark from `carrier.isNoGravity()` only when no mark is set yet, so an effect removed and cast again before the end ran keeps the original value, and, if the block is standable, the start spot); `static void tick(LivingEntity carrier)` (public so GameTests step it: a missing mark re-set while the effect is on, the mob-carrier hold, the trail, and the end when the mark is set with no effect; for a carrier that is no longer alive it only clears the mark and gives gravity back, no push-out; it does **not** sync a player's abilities: `AbilitySync` is the one driver of that, and a GameTest that needs it calls `AbilitySync.sync` itself); `static boolean flies(LivingEntity carrier)` (`carrier instanceof Vex`; Task 12 adds `|| carrier instanceof VillagerGhost`); events `onPlayerTick`, `onEntityTick`, `onIncomingDamage`.

- [x] **Step 1: Write the failing tests**

`GhostrailGameTests` (javadoc `/** GameTests of Ghostrail's damage rule, trail, ending and mob carriers (Parts 6-7 spec §7.3, §10). */`; tests that place a player or a hostile mob in the padding register through `FangGameTests.addPadded(SHARED, …)`; the one test that reads a constant registers through `SpellcraftGameTests.add(SHARED, …)`; the bodies call `ScrollTestSupport.plainPlayer` as `plainPlayer(helper)`; every mob below is a `Husk` from `hostileAt` (a zombie burns in daylight), and a `Husk` is a `Zombie`):

```java
static void registerAll() {
    FangGameTests.addPadded(SHARED, "ghostrail_damage_is_cancelled", GhostrailGameTests::damageIsCancelled);
    FangGameTests.addPadded(SHARED, "ghostrail_kill_still_lands", GhostrailGameTests::killStillLands);
    FangGameTests.addPadded(SHARED, "ghostrail_void_damage_still_lands", GhostrailGameTests::voidDamageStillLands);
    FangGameTests.addPadded(SHARED, "ghostrail_trail_particles_are_sent", GhostrailGameTests::trailIsSent);
    FangGameTests.addPadded(SHARED, "ghostrail_ending_inside_stone_moves_to_a_free_spot", GhostrailGameTests::endingInStone);
    FangGameTests.addPadded(SHARED, "ghostrail_ending_in_the_open_stays_put", GhostrailGameTests::endingInTheOpen);
    FangGameTests.addPadded(SHARED, "ghostrail_ending_deep_in_stone_goes_to_the_start_spot", GhostrailGameTests::endingGoesToTheStartSpot);
    FangGameTests.addPadded(SHARED, "ghostrail_ending_deep_in_stone_with_no_start_spot_stays", GhostrailGameTests::endingStaysWithNoStartSpot);
    FangGameTests.addPadded(SHARED, "ghostrail_mob_carrier_gets_no_physics_and_no_gravity", GhostrailGameTests::mobGetsNoPhysics);
    FangGameTests.addPadded(SHARED, "ghostrail_mob_carrier_gets_its_gravity_back_at_the_end", GhostrailGameTests::mobGetsGravityBack);
    FangGameTests.addPadded(SHARED, "ghostrail_mob_carrier_keeps_an_original_no_gravity", GhostrailGameTests::mobKeepsOriginalNoGravity);
    FangGameTests.addPadded(SHARED, "ghostrail_mob_carrier_recast_before_the_end_keeps_the_original_gravity", GhostrailGameTests::recastKeepsTheOriginal);
    FangGameTests.addPadded(SHARED, "ghostrail_dead_carrier_is_not_pushed_out_of_the_stone", GhostrailGameTests::deadCarrierIsNotPushed);
    FangGameTests.addPadded(SHARED, "ghostrail_vex_carrier_is_left_alone", GhostrailGameTests::vexIsLeftAlone);
    SpellcraftGameTests.add(SHARED, "ghostrail_infinite_duration_is_vanillas", GhostrailGameTests::infiniteIsVanillas);
    FangGameTests.addPadded(SHARED, "ghostrail_mob_mark_is_saved", GhostrailGameTests::mobMarkIsSaved);
}
```

```java
/**
 * When a player takes 5 damage from a generic source, should their health drop without Ghostrail (the control) and
 * not change with it.
 */
static void damageIsCancelled(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = plainPlayer(helper);
    ServerLevel level = helper.getLevel();
    float full = player.getHealth();
    player.hurtServer(level, level.damageSources().generic(), 5.0F);
    helper.assertValueEqual(player.getHealth(), full - 5.0F, "control: without Ghostrail the hit lands");
    player.setHealth(full);
    player.invulnerableTime = 0; // the hit above started the invulnerability frames
    Spells.cast(player, Spells.GHOSTRAIL, GhostrailDuration.PLAYER_TICKS);
    // Act
    player.hurtServer(level, level.damageSources().generic(), 5.0F);
    // Assert
    helper.assertValueEqual(player.getHealth(), full, "no damage");
    helper.succeed();
}

/** When a Ghostrail player is hit by /kill's damage, should they die. */
static void killStillLands(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = plainPlayer(helper);
    Spells.cast(player, Spells.GHOSTRAIL, GhostrailDuration.PLAYER_TICKS);
    ServerLevel level = helper.getLevel();
    // Act
    player.hurtServer(level, level.damageSources().genericKill(), 1000.0F);
    // Assert
    helper.assertFalse(player.isAlive(), "/kill still kills");
    helper.succeed();
}
```

`voidDamageStillLands` is the same with `level.damageSources().fellOutOfWorld()`. `trailIsSent`: cast; `GameTestSupport.drainShown(player)` once to empty the queue; call `Ghostrail.tick(player)` (the trail is drawn at the previous position, so no move is needed); assert `GameTestSupport.drainShown(player).particles()` contains `ParticleTypes.WHITE_SMOKE` (read `GameTestSupport.Shown` and `drainShown` for the exact accessor names).

The ending tests that build stone share one helper, which sets the blocks with flag `Block.UPDATE_CLIENTS` (2: no neighbor updates, no drops) and puts air back when the test ends (a padded test clears only its own padded box, and these cubes can reach past it):

```java
/** Fills the box between two relative corners with stone; puts air back when the test ends. */
private static void stoneBox(GameTestHelper helper, BlockPos from, BlockPos to) {
    ServerLevel level = helper.getLevel();
    List<BlockPos> filled = new ArrayList<>();
    for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(from), helper.absolutePos(to))) {
        BlockPos fixed = pos.immutable();
        level.setBlock(fixed, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        filled.add(fixed);
    }
    GameTestSupport.whenDone(helper, () -> filled.forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS)));
}
```

```java
/**
 * When Ghostrail ends with the player's box inside stone, should they move to a free spot, fall distance 0, no mayfly.
 */
static void endingInStone(GameTestHelper helper) {
    // Arrange: a 7 x 7 x 7 block of stone with the player's feet in it, over a floor
    ServerPlayer player = plainPlayer(helper);
    ServerLevel level = helper.getLevel();
    stoneBox(helper, FangGameTests.FEET.offset(-3, -1, -3), FangGameTests.FEET.offset(3, 5, 3));
    Spells.cast(player, Spells.GHOSTRAIL, GhostrailDuration.PLAYER_TICKS);
    AbilitySync.sync(player);
    Ghostrail.tick(player);
    player.fallDistance = 12.0;
    // Act
    player.removeEffect(ModEffects.GHOSTRAIL);
    AbilitySync.sync(player);
    Ghostrail.tick(player);
    // Assert
    helper.assertTrue(level.noCollision(player, player.getBoundingBox()), "the player's box is free");
    helper.assertTrue(player.blockPosition().distManhattan(helper.absolutePos(FangGameTests.FEET)) <= 24, "within the search");
    helper.assertValueEqual(player.fallDistance, 0.0, "no fall damage to come");
    helper.assertFalse(player.getAbilities().mayfly, "no flight left");
    helper.succeed();
}
```

(The padded box is air, so the stone cube is a pocket. If a standable spot is found outside the cube the player ends up standing on the floor stone or in mid-air; both pass.)

`endingInTheOpen`: cast, tick, remove, tick: the position is unchanged (`player.position()` equal to the position before the removal) and `fallDistance` is 0. `endingGoesToTheStartSpot`: the player stands at the standing spot over a floor (`floor(helper, 3)`), Ghostrail is cast (the start spot is recorded: assert `player.getExistingData(EvokingAttachments.GHOSTRAIL_START)` is present and equals the player's block), then the player is moved 6 blocks up (`MockPlayers.moveTo(helper, player, FangGameTests.STAND.add(0, 6, 0), yaw)`) and a stone cube of half-width 8 (17 x 17 x 17: every block within `Ghostrail.SEARCH_RADIUS` of the player is stone, so nothing within 8 blocks is free, and the cube's top, 8 above the moved player, stays inside the padded box, whose top is 13 above the 3-tall structure) is set with `stoneBox` around the new position, the effect is removed and `Ghostrail.tick` runs: assert the player's block equals the start spot. `endingStaysWithNoStartSpot`: the same but the cast happens inside the stone (no start spot recorded: assert `getExistingData(GHOSTRAIL_START)` is empty) and the player's position is unchanged after the end. `mobGetsNoPhysics`: a husk from `ScrollTestSupport.hostileAt`; `Spells.cast(husk, Spells.GHOSTRAIL, GhostrailDuration.HOSTILE_TICKS)`; `Ghostrail.tick(husk)`; assert `husk.noPhysics && husk.isNoGravity()`. `mobGetsGravityBack`: a husk with `setNoGravity(false)`: cast, tick, `husk.removeAllEffects()` (the `/effect clear` path: the effect is gone but the mark remains), tick: assert `!noPhysics && !isNoGravity()` and `!husk.hasData(EvokingAttachments.GHOSTRAIL_MARK)`. `mobKeepsOriginalNoGravity`: the husk starts with `setNoGravity(true)` (`foeAt` already sets it): after the end `isNoGravity()` is still true and `noPhysics` is false. `recastKeepsTheOriginal`: a husk with `setNoGravity(false)`: cast, tick (the hold sets `noGravity`), `removeEffect`, cast again **before** any tick (the mark is still set; `begin` must not overwrite the stored `false` with the hold's `true`), tick, `removeEffect`, tick: assert `!isNoGravity()` (gravity is restored). `deadCarrierIsNotPushed`: a husk inside a stone pocket (`stoneBox` around it) with Ghostrail, ticked once; kill it with `husk.hurtServer(level, level.damageSources().genericKill(), 1000.0F)` (a kill still lands through Ghostrail); tick again: assert its position is unchanged (a corpse is not teleported), `!hasData(GHOSTRAIL_MARK)` and `!noPhysics`. `vexIsLeftAlone`: a `BoundVex` (from `BoundVexTestSupport.unmarkedVex`) given Ghostrail: after `Ghostrail.tick(vex)` its `noPhysics` is unchanged (false between ticks) and, after the end, `isNoGravity()` equals its value before. `infiniteIsVanillas`: `helper.assertValueEqual(GhostrailDuration.INFINITE, MobEffectInstance.INFINITE_DURATION, "the sentinel")` (a pure constant comparison, so it registers through `SpellcraftGameTests.add`, with no padding). `mobMarkIsSaved`: a husk with Ghostrail: `((Husk) GameTestSupport.reloaded(level, husk)).hasData(EvokingAttachments.GHOSTRAIL_MARK)` is true.

Add three no-rescue tests to the existing classes (each is one registered line plus one method; they call the class's own private helpers, so they live in those classes):

- `BoundVexLavaGameTests`: no new method; the registration inlines the lambda, as its neighbors `..._not_for_fire_resistance` and `..._not_for_creative` do: `padded("bound_vex_world_lava_pull_not_for_a_ghostrail_carrier", helper -> lavaPullNotForTheUnhurt(helper, player -> Spells.cast(player, Spells.GHOSTRAIL, GhostrailDuration.PLAYER_TICKS), false));`.
- `BoundVexDrowningGameTests`: `drowningNotForAGhostrailCarrier` = `drowningNotFor(helper, player -> Spells.cast(player, Spells.GHOSTRAIL, GhostrailDuration.PLAYER_TICKS), false, VexRescues.LOW_AIR - 1)` (a method, like its neighbors `drowningNotForCreative` and `drowningNotForPassengers`); registered with `padded(...)`, name `bound_vex_world_drowning_not_for_a_ghostrail_carrier`.
- `BoundVexVoidGameTests`: `voidCatchNotForAGhostrailCarrier`: arrange like `voidCatch` (a `voidPlayer`, `endVoid`, a platform ten blocks east, a `voidVex`), then in the sequence step: `Spells.cast(player, Spells.GHOSTRAIL, GhostrailDuration.PLAYER_TICKS)`, `dropTo(player, column(slot, end.getMinY() - BELOW_FLOOR))`, `VexRescues.tick(player)`, `assertStillThere(helper, player, column(slot, end.getMinY() - BELOW_FLOOR), "no void catch for a Ghostrail carrier")`. Read `voidCatch` and its neighbors first and copy the arrange lines and registration style exactly.

Add `GhostrailGameTests.registerAll();` after `GhostrailFlightGameTests.registerAll();`.

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:ghostrail_*"` (FAIL: `Ghostrail` does not exist).

- [x] **Step 3: Write the implementation**

`EvokingAttachments` (register `ATTACHMENT_TYPES` in the `Spellcraft` constructor beside `AllyAttachments`):

```java
/** Entity data of Part 7's spells (Parts 6-7 spec §7.3, §8). */
public final class EvokingAttachments {
    /** The register of these attachment types; the mod constructor adds it to the mod event bus. */
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Spellcraft.MODID);

    /**
     * Present on a carrier while Ghostrail runs; the value is the carrier's own {@code NoGravity} from before. Saved,
     * so a reload mid-effect still ends it properly, and its presence with no effect is how the end is noticed after
     * expiry, milk or {@code /effect clear}. Read with {@code getExistingData}/{@code hasData}. Not the player's
     * {@code InfinityAttachments.GHOSTRAIL_GRANTED}, which records who set the abilities flags.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> GHOSTRAIL_MARK =
            ATTACHMENT_TYPES.register(
                    "ghostrail_mark",
                    () -> AttachmentType.builder(() -> Boolean.FALSE)
                            .serialize(Codec.BOOL.fieldOf("no_gravity"))
                            .build());

    /** The standable spot Ghostrail began at; not saved. The last place to send a carrier that ends deep in stone. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<BlockPos>> GHOSTRAIL_START =
            ATTACHMENT_TYPES.register("ghostrail_start", () -> AttachmentType.builder(() -> BlockPos.ZERO).build());

    /** On a scroll mob: the game time its Mirror cooldown ends. Saved. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> MOB_MIRROR_READY =
            ATTACHMENT_TYPES.register(
                    "mob_mirror_ready",
                    () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG.fieldOf("ready")).build());

    /** On a scroll mob: the game time its Ghostrail cooldown ends. Saved. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> MOB_GHOSTRAIL_READY =
            ATTACHMENT_TYPES.register(
                    "mob_ghostrail_ready",
                    () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG.fieldOf("ready")).build());

    private EvokingAttachments() {}
}
```

`Ghostrail`:

```java
/**
 * The per-tick driver of Ghostrail (Parts 6-7 spec §7.3). The player's physics are the Mixin's and the flight is
 * {@link AbilitySync}'s (the one driver of the abilities sync); this runs the rest: no damage (but {@code /kill} and
 * the void still land), the white-smoke trail, the hold on a mob carrier ({@code noPhysics}, no gravity), and the
 * end (push out of blocks, give gravity back). Nothing here depends on the order of the two Post-tick handlers:
 * the end only moves the carrier and never touches abilities.
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class Ghostrail {
    /** How far from a carrier that ends inside blocks to look for a free spot. */
    static final int SEARCH_RADIUS = 8;

    /** Trail particles per tick, at the carrier's previous position. */
    private static final int TRAIL_COUNT = 2;

    private Ghostrail() {}

    /**
     * Whether {@code carrier} flies through blocks by itself: a Vex ({@code Vex.noPhysics} is true only inside its own
     * tick, so the flag cannot be the test). Task 12 adds the villager ghost, and updates this Javadoc with it.
     *
     * @param carrier any living entity
     * @return true when Ghostrail must not touch its physics
     */
    static boolean flies(LivingEntity carrier) {
        return carrier instanceof Vex;
    }

    /**
     * Notes that Ghostrail began on {@code carrier} (the effect's {@code onEffectAdded}): the mark with its original
     * {@code NoGravity} (only when no mark is set yet: if the effect was removed and cast again before the end ran,
     * the hold has already set {@code noGravity}, and the stored original must survive), and the block it stands in
     * if that is a standable spot.
     *
     * @param carrier the new carrier, on a server level
     */
    public static void begin(LivingEntity carrier) {
        if (!carrier.hasData(EvokingAttachments.GHOSTRAIL_MARK)) {
            carrier.setData(EvokingAttachments.GHOSTRAIL_MARK, carrier.isNoGravity());
        }
        if (StandableSpots.standable(carrier.level(), carrier.blockPosition())) {
            carrier.setData(EvokingAttachments.GHOSTRAIL_START, carrier.blockPosition());
        } else {
            carrier.removeData(EvokingAttachments.GHOSTRAIL_START);
        }
    }

    /**
     * One step for {@code carrier}. Public so GameTests step a mock player or a mob by hand.
     *
     * @param carrier a player or a mob that may carry Ghostrail
     */
    public static void tick(LivingEntity carrier) {
        if (!(carrier.level() instanceof ServerLevel level)) {
            return;
        }
        boolean has = carrier.hasEffect(ModEffects.GHOSTRAIL);
        boolean marked = carrier.hasData(EvokingAttachments.GHOSTRAIL_MARK);
        if (!carrier.isAlive()) {
            // A killed carrier keeps its effects until it is removed (a player until the respawn): clear the mark and
            // give gravity back, but never teleport a corpse
            if (marked) {
                end(level, carrier, false);
            }
            return;
        }
        if (has) {
            if (!marked) {
                // The effect came back from a save or a forced add, which skip the hook
                carrier.setData(EvokingAttachments.GHOSTRAIL_MARK, carrier.isNoGravity());
            }
            if (!(carrier instanceof Player) && !flies(carrier)) {
                carrier.noPhysics = true;
                carrier.setNoGravity(true);
            }
            level.sendParticles(
                    ParticleTypes.WHITE_SMOKE,
                    carrier.xOld,
                    carrier.yOld + carrier.getBbHeight() / 2.0,
                    carrier.zOld,
                    TRAIL_COUNT,
                    0.1,
                    0.1,
                    0.1,
                    0.0);
        } else if (marked) {
            end(level, carrier, true);
        }
    }

    /**
     * Ends Ghostrail on {@code carrier}: gravity back, out of the blocks (when {@code moveOut}), no fall to come.
     */
    private static void end(ServerLevel level, LivingEntity carrier, boolean moveOut) {
        boolean originalNoGravity = carrier.getExistingData(EvokingAttachments.GHOSTRAIL_MARK).orElse(false);
        carrier.removeData(EvokingAttachments.GHOSTRAIL_MARK);
        if (!(carrier instanceof Player) && !flies(carrier)) {
            carrier.noPhysics = false;
            carrier.setNoGravity(originalNoGravity);
        }
        if (moveOut) {
            pushOut(level, carrier);
        }
        carrier.removeData(EvokingAttachments.GHOSTRAIL_START);
        carrier.fallDistance = 0.0;
    }

    /**
     * When the carrier's box collides with blocks: the nearest standable spot its box fits on within
     * {@link #SEARCH_RADIUS} blocks, else the nearest free spot, else the spot the effect began at if that is still
     * free, else it stays (and suffocates as vanilla would; accepted, a playtest note).
     */
    private static void pushOut(ServerLevel level, LivingEntity carrier) {
        if (level.noCollision(carrier, carrier.getBoundingBox())) {
            return;
        }
        BlockPos here = carrier.blockPosition();
        Optional<BlockPos> spot = search(level, carrier, here, true).or(() -> search(level, carrier, here, false));
        if (spot.isEmpty()) {
            spot = carrier.getExistingData(EvokingAttachments.GHOSTRAIL_START)
                    .filter(start -> fits(level, carrier, start));
        }
        spot.ifPresent(pos -> teleport(level, carrier, Vec3.atBottomCenterOf(pos)));
    }

    /** The first spot outward from {@code from}, ring by ring, where the box fits (and, if asked, a floor is below). */
    private static Optional<BlockPos> search(ServerLevel level, LivingEntity carrier, BlockPos from, boolean standable) {
        for (int ring = 1; ring <= SEARCH_RADIUS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dy = -ring; dy <= ring; dy++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != ring) {
                            continue;
                        }
                        BlockPos pos = from.offset(dx, dy, dz);
                        if (fits(level, carrier, pos) && (!standable || StandableSpots.standable(level, pos))) {
                            return Optional.of(pos);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Whether the carrier's box, with its feet at the bottom middle of {@code pos}, touches no block. */
    private static boolean fits(ServerLevel level, LivingEntity carrier, BlockPos pos) {
        AABB box = carrier.getDimensions(carrier.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(pos));
        return level.noCollision(carrier, box);
    }

    /**
     * Moves {@code carrier} to {@code spot}: a player through the full teleport (it syncs the client), a mob by
     * position.
     */
    private static void teleport(ServerLevel level, LivingEntity carrier, Vec3 spot) {
        if (carrier instanceof ServerPlayer player) {
            player.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), player.getYRot(), player.getXRot(), false);
        } else {
            carrier.teleportTo(spot.x, spot.y, spot.z);
        }
    }

    /**
     * Drives every server player's Ghostrail after its tick.
     *
     * @param event the player's post-tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tick(player);
        }
    }

    /**
     * Drives every non-player carrier (an entity that holds the saved mark) after its tick.
     *
     * @param event the entity's post-tick event
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living
                && !(living instanceof Player)
                && living.hasData(EvokingAttachments.GHOSTRAIL_MARK)) {
            tick(living);
        }
    }

    /**
     * Cancels every hit on a carrier except {@code /kill} and the void, so a carrier that dives under the world
     * still dies there.
     *
     * @param event the incoming-damage event
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (event.getEntity().hasEffect(ModEffects.GHOSTRAIL)
                && !source.is(DamageTypes.GENERIC_KILL)
                && !source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            event.setCanceled(true);
        }
    }
}
```

(Verified against the sources: `CollisionGetter.noCollision(Entity, AABB)`, `EntityDimensions.makeBoundingBox(Vec3)`, `IAttachmentHolder.removeData(AttachmentType)`, `Entity.teleportTo(double, double, double)` and the `Entity`/`ServerPlayer.teleportTo(ServerLevel, double, double, double, Set<Relative>, float, float, boolean)` overload that `MockPlayers` uses; the carrier is always a `LivingEntity`, so `carrier.getPose()` exists.)

`GhostrailEffect.onEffectAdded`:

```java
@Override
public void onEffectAdded(LivingEntity mob, int amplifier) {
    super.onEffectAdded(mob, amplifier);
    if (mob.level() instanceof ServerLevel) {
        Ghostrail.begin(mob);
    }
}
```

`VexRescues.tick`, right after `VoidCatch.recordGround(player);`:

```java
if (player.hasEffect(ModEffects.GHOSTRAIL)) {
    // It takes no damage and is flying: a pull, a push or a catch would fight its flight (Parts 6-7 spec §7.3)
    drop(id);
    return;
}
```

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:ghostrail_*"`, then `"-PgameTests=spellcraft:bound_vex_world_*"`. Expected: PASS.

- [x] **Step 5: Gates and commit**

Run: `./gradlew spotlessApply build`, `./gradlew runGameTestServer` twice. Expected: `All 713 required tests passed` both times (the Task 6 total plus 19: the 16 `ghostrail_*` tests of the list in `GhostrailGameTests` and the 3 no-rescue tests).

```bash
git add src
git commit -m "feat(ghostrail): no damage, a smoke trail, a safe ending and mob carriers"
```

> **As built (`6302d73`):** diverged — `endingGoesToTheStartSpot` lifts the player 10, not 6, inside a stone cube (the plan's lift left the start spot within the search radius); a recast in stone keeps the earlier start spot (review fix, +1 test); the Task 6 review's creative-player fix (+1 test) landed first in 30ed2b4. No RED capture.

### Task 8: The Caster: the roll, the flag, the goal that keeps its distance, the Raid roster

**Files:**

- Create: `evoking/CasterGoal.java`, `gametest/CasterGameTests.java`
- Modify: `evoking/BoundVex.java`, `evoking/VexSummons.java`, `evoking/RangedGearGoal.java` (one clause), `evoking/TeamMoves.java`, `evoking/TeamMoveTriggers.java`, `evoking/TeamMoveKits.java`, `spell/rules/CasterRules.java`, `gametest/ScrollTestSupport.java`, `gametest/SpellcraftGameTests.java`
- Test: `src/test/…/spell/rules/CasterRulesTest.java` (one constant)
- Modify (maybe): existing GameTests that summon through the V key (Step 3 finds them)

**Interfaces:**

- Consumes: Task 2 (`CasterRules.approach`), Task 4 (`Dice.CASTER_ROLL`), Part 6 (`TeamFlight.flyTo/ringPoint/lookAt`, `TeamMoves`, `TeamMoveKits`).
- Produces:
  - `CasterRules.CASTER_ONE_IN = 3`.
  - `BoundVex.isCaster()` / `setCaster(boolean)` (public; `CASTER` is a synced boolean, saved as `caster`); `long mirrorReadyAt()`, `void setMirrorReadyAt(long)`, `long ghostrailReadyAt()`, `void setGhostrailReadyAt(long)` (public; game times, saved as `mirror_ready` and `ghostrail_ready`, 0 means ready).
  - `CasterGoal` (package-private final, priority 3, flags `MOVE` and `LOOK`): with a live target, keeps `CasterRules.KEEP_MIN` to `KEEP_MAX` blocks from it. `ChargeGoal` and `RangedGearGoal` do not run for a Caster.
  - `VexSummons.summon` rolls `Dice.CASTER_ROLL.below(random, CasterRules.CASTER_ONE_IN) == 0` for each new vex.
  - `TeamMoves.isFree(BoundVex, ServerPlayer)` is false for a Caster; new `TeamMoves.isFree(BoundVex, ServerPlayer, boolean forRaid)` is true for a Caster when `forRaid`; `TeamMoves.freeVexes(ServerPlayer)` (kept: Part 6 tests call it) and `freeVexes(ServerPlayer, boolean forRaid)`, and the package-private list form `freeVexes(ServerPlayer, List<BoundVex>, boolean forRaid)` **replaces** the two-argument list form (its only caller, `TeamMoveTriggers.scan`, passes the flag); the Raid roster takes Casters.
  - `TeamMoveKits.kit(TeamMove, ServerLevel, RandomSource, boolean caster)` **replaces** the three-argument form (its only caller, `TeamMoves.start`, passes `vex.isCaster()`): a Raid kit for a Caster is the Mirror scroll in the main hand (`ModItems.scrollFor(Spells.MIRROR)`), a shield in the off hand, and head, chest and legs of one armor material from **one** `Dice.RAID_KIT` roll (the Part 6 comment `// Part 7: a Caster rolls only the armor...` becomes this code).
  - `ScrollTestSupport.casterVex(GameTestHelper, ServerPlayer, Vec3 absolute)` (a parked Caster: `BoundVexTestSupport.noTargets(boundVex(...))`, then `BoundVexTestSupport.pauseFollowing(vex)` so the follow goal does not pull it toward the player, with `setCaster(true)` at that position; tests place it clearly inside `GuardPosture.WIDE_LIMIT` (4.0) of the player or well outside it, never exactly on the limit); `ScrollTestSupport.raidScene(GameTestHelper)` and its `RaidScene(player, husk, caster)` record (below).

- [x] **Step 1: Write the failing tests**

Add to `CasterRulesTest.constantsAreTheSpecsNumbers`: `assertThat(CasterRules.CASTER_ONE_IN).isEqualTo(3);`.

`CasterGameTests` (javadoc `/** GameTests of the Caster flag, its roll and its goal (Parts 6-7 spec §6.1, §6.2). */`; vexes sit in the padding, so `FangGameTests.addPadded`; the scripted-roll tests register in `caster_dice`, the real-dice statistics in `caster_dice_real`):

Each registered name is `caster_` plus the method name in snake case (the rule of the preamble), so the inner-loop selector `spellcraft:caster_*` finds every one.

```java
static void registerAll() {
    FangGameTests.addPadded("caster_dice", "caster_scripted_roll_makes_a_caster", CasterGameTests::scriptedRollMakesACaster);
    FangGameTests.addPadded("caster_dice", "caster_scripted_roll_makes_a_plain_vex", CasterGameTests::scriptedRollMakesAPlainVex);
    FangGameTests.addPadded(SHARED, "caster_flag_survives_save_and_load", CasterGameTests::flagSurvivesSaveAndLoad);
    FangGameTests.addPadded(SHARED, "caster_command_vex_is_not_a_caster", CasterGameTests::commandVexIsNotACaster);
    FangGameTests.addPadded("caster_dice_real", "caster_share_is_about_a_third", CasterGameTests::shareIsAboutAThird);
    FangGameTests.addPadded(SHARED, "caster_never_hurts_its_target", CasterGameTests::neverHurtsItsTarget);
    FangGameTests.addPadded(SHARED, "caster_keeps_its_distance", 200, CasterGameTests::keepsItsDistance);
    FangGameTests.addPadded(SHARED, "caster_backs_off", 200, CasterGameTests::backsOff);
    FangGameTests.addPadded(SHARED, "caster_never_shoots", CasterGameTests::neverShoots);
    FangGameTests.addPadded(SHARED, "caster_plain_vex_charges", 200, CasterGameTests::plainVexCharges);
    FangGameTests.addPadded(SHARED, "caster_plain_vex_shoots", 200, CasterGameTests::plainVexShoots);
    FangGameTests.addPadded(SHARED, "caster_not_free_for_ordinary_moves", CasterGameTests::notFreeForOrdinaryMoves);
    FangGameTests.addPadded(SHARED, "caster_free_for_a_raid", CasterGameTests::freeForARaid);
    FangGameTests.addPadded(SHARED, "caster_counts_toward_a_raid_trigger", CasterGameTests::countsTowardARaidTrigger);
    // Scripts Dice.RAID_KIT, so it runs in the environment Part 6's Raid tests use for the same die.
    FangGameTests.addPadded("team_move_dice", "caster_raid_kit", 300, CasterGameTests::raidKit);
}
```

```java
/** When the Caster die rolls 0 on a summon, should that vex be a Caster and the synced flag be set. */
static void scriptedRollMakesACaster(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = plainPlayer(helper);
    // Act
    List<BoundVex> vexes;
    try {
        Dice.CASTER_ROLL.script(0, 1, 2);
        vexes = VexSummons.summon(helper.getLevel(), player, 3);
    } finally {
        Dice.unscriptAll();
    }
    vexes.forEach(vex -> GameTestSupport.whenDone(helper, vex::discard));
    // Assert
    helper.assertTrue(vexes.get(0).isCaster(), "roll 0 makes a Caster");
    helper.assertFalse(vexes.get(1).isCaster(), "roll 1 does not");
    helper.assertFalse(vexes.get(2).isCaster(), "roll 2 does not");
    helper.succeed();
}
```

(`isCaster()` reads the synced data, so these asserts prove the flag the client sees.)

`scriptedRollMakesAPlainVex`: script `1`: `summon(level, player, 1)` returns a vex with `isCaster()` false. `flagSurvivesSaveAndLoad`: a vex from `BoundVexTestSupport.boundVex`, `setCaster(true)`, `setMirrorReadyAt(5000L)`, `setGhostrailReadyAt(6000L)`; `BoundVex copy = (BoundVex) GameTestSupport.reloaded(level, vex)`: `copy.isCaster()` and both ready times kept. `commandVexIsNotACaster`: `BoundVexTestSupport.unmarkedVex` is not a Caster. `shareIsAboutAThird`: with real dice, summon 100 batches of 3 (300 vexes; discard them all at the end), count Casters, assert `count` is within `[59, 141]` (five standard deviations of 300 at 1/3 is 41). `neverHurtsItsTarget`: a player, a Caster at the hang point (`ScrollTestSupport.casterVex`) and a frozen `Husk` (from `TeamMoveTestSupport.foe(helper, EntityTypes.HUSK, hang.add(1.0, 0.0, 0.0))`), `vex.setTarget(husk)`; for 60 ticks (`helper.startSequence().thenExecuteFor(60, check)`) the husk's health equals its maximum. `keepsItsDistance`: the husk at the hang point, the Caster 9 blocks east (and, like every Caster in these scenes, parked through `casterVex`, so `FollowSummonerGoal` is paused), `setTarget(husk)`; `thenWaitUntil(() -> helper.assertTrue(distance >= 3.5 && distance <= 6.5, "in the band"))` (the test's own tick limit, 200, is the time bound), then `thenExecuteFor(20, ...)` asserts a wider band, `distance >= 3.0 && distance <= 7.0`, holds: the goal steers with a spring and damping (below), so it settles, but the assertion allows for the last of the coast. `backsOff`: the Caster 2 blocks from the husk: `thenWaitUntil` distance `>= 3.5`. `countsTowardARaidTrigger` (the `TeamMoveTriggers.scan` change): copy `BoundVexTeamMoveTriggerGameTests.ravagerWithTwelveRaids` with 11 frozen plain vexes plus one Caster (`casterVex`, which `isFree(…, true)` counts), a ravager beside, `TeamMoves.tick(player)`: assert `TeamMoves.running(player)` is `Optional.of(TeamMove.RAID)`, the roster has 12 and contains the Caster, and the Caster's main hand holds the Mirror scroll; its control is the same scene without the Caster (11 free: a Shield starts, not a Raid), asserted first. `raidKit` (environment `team_move_dice`): `RaidScene scene = raidScene(helper)` (below), `Dice.RAID_KIT.script(new int[23])` immediately before `TeamMoves.start(player, TeamMove.RAID, husk)` in the same call (11 ordinary members take 2 rolls each and the Caster takes 1: all zero is a sword and netherite), `finally Dice.unscriptAll()`; assert `start` returned true, the Caster's main hand is the Mirror scroll, its off hand a shield, and head, chest and legs are netherite; and an ordinary member passes `TeamMoveTestSupport.assertRaidGear`.

`ScrollTestSupport` also gets the Raid scene that this task's `raidKit` and Task 9's Raid tests share:

```java
/** A summoner, a Raid-sized husk and twelve flying vexes, the first of which is a Caster; nothing is started yet. */
record RaidScene(ServerPlayer player, Husk husk, BoundVex caster) {}

/**
 * Builds a Raid scene: a plain player, {@code TeamMoveTestSupport.bigHusk} beside the hang point with its health
 * raised to 1000 (the netherite swordsmen would otherwise kill the 40-health husk in the first volley and end the move
 * before a test can look at the Caster), twelve vexes from {@code flyingVexes}, the first made a Caster.
 */
static RaidScene raidScene(GameTestHelper helper) {
    ServerPlayer player = plainPlayer(helper);
    Vec3 hang = TeamMoveTestSupport.hangPoint(helper);
    Husk husk = TeamMoveTestSupport.bigHusk(helper, TeamMoveTestSupport.beside(helper));
    TeamMoveTestSupport.setBase(husk, Attributes.MAX_HEALTH, 1000.0);
    husk.setHealth(1000.0F);
    List<BoundVex> vexes = TeamMoveTestSupport.flyingVexes(helper, player, 12, hang);
    vexes.get(0).setCaster(true);
    return new RaidScene(player, husk, vexes.get(0));
}
``` `neverShoots`: the Caster holds `Items.BOW`, the husk is 5 blocks away, 120 ticks: the husk's health is unchanged and `level.getEntitiesOfClass(AbstractArrow.class, vex.getBoundingBox().inflate(20.0))` is empty. `plainVexCharges` (control): the same scene with a plain vex (`BoundVexTestSupport.noTargets(boundVex(...))`, its iron sword) and `setTarget(husk)`: `thenWaitUntil(() -> helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "a plain vex hurts it"))`. `plainVexShoots` (control): the plain vex holds `Items.BOW` at 5 blocks: `thenWaitUntil` an `AbstractArrow` exists within 20 blocks or the husk is hurt. `notFreeForOrdinaryMoves`: three frozen plain vexes and one Caster: `TeamMoves.isFree(caster, player)` is false, `TeamMoves.freeVexes(player).size()` is 3, and `TeamMoves.start(player, TeamMove.SHIELD, husk)` takes only the three. `freeForARaid`: `TeamMoves.isFree(caster, player, true)` is true and `TeamMoves.freeVexes(player, true)` has 4.

Add `CasterGameTests.registerAll();` after `GhostrailGameTests.registerAll();` in `SpellcraftGameTests`.

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test` and `./gradlew compileJava` (FAIL: no `isCaster`, no `CASTER_ONE_IN`).

- [x] **Step 3: Write the implementation**

`CasterRules`: `/** One vex in this many is a Caster. */ public static final int CASTER_ONE_IN = 3;`.

`BoundVex`: beside `STAYING`:

```java
/** Synced: whether the vex is a Caster (the client draws the robe and the casting pose). */
private static final EntityDataAccessor<Boolean> CASTER =
        SynchedEntityData.defineId(BoundVex.class, EntityDataSerializers.BOOLEAN);

private static final String CASTER_TAG = "caster";
private static final String MIRROR_READY_TAG = "mirror_ready";
private static final String GHOSTRAIL_READY_TAG = "ghostrail_ready";

/** The game time this Caster may cast Mirror again; 0 means now. Saved. */
private long mirrorReadyAt;

/** The game time this Caster may cast Ghostrail again; 0 means now. Saved. */
private long ghostrailReadyAt;
```

`defineSynchedData`: `entityData.define(CASTER, false);`. `addAdditionalSaveData`: `output.putBoolean(CASTER_TAG, isCaster()); output.putLong(MIRROR_READY_TAG, mirrorReadyAt); output.putLong(GHOSTRAIL_READY_TAG, ghostrailReadyAt);`. `readAdditionalSaveData`: `entityData.set(CASTER, input.getBooleanOr(CASTER_TAG, false)); mirrorReadyAt = input.getLongOr(MIRROR_READY_TAG, 0L); ghostrailReadyAt = input.getLongOr(GHOSTRAIL_READY_TAG, 0L);`. Accessors with Javadoc:

```java
/** Whether this vex is a Caster: it wears the robe, never fights, and casts scrolls on its summoner (Part 7). */
public boolean isCaster() {
    return entityData.get(CASTER);
}

/** Makes this vex a Caster or a plain vex. */
public void setCaster(boolean caster) {
    entityData.set(CASTER, caster);
}
```

(plus the four ready-time accessors). `registerGoals`, after `RangedGearGoal`'s line, and in the class Javadoc one sentence: "Both sets of fighting goals and the Caster goals are registered, because `registerGoals` runs in the constructor, before `CASTER` is known; each `canUse` tests `isCaster()`."

```java
goalSelector.addGoal(3, new CasterGoal(this));
```

(`CasterSpellGoal`, priority 2, comes in Task 9.) `ChargeGoal.canUse` gets `&& !BoundVex.this.isCaster()`; `RangedGearGoal.canUse` gets `&& !vex.isCaster()`. A Caster's `SupportGearGoal` (healing potions), follow, stay, posture, flee, dodge, job and rescue goals are unchanged.

`CasterGoal`:

```java
/**
 * A Caster with a target keeps {@link CasterRules#KEEP_MIN} to {@link CasterRules#KEEP_MAX} blocks from it, looking at
 * it, and never melees or shoots (Parts 6-7 spec §6.2). Priority 3: above the follow goal, below the team move, job and
 * casting goals.
 */
final class CasterGoal extends Goal {
    /** The distance the Caster flies to when it has to move: the middle of the band. */
    private static final double HOLD_DISTANCE = (CasterRules.KEEP_MIN + CasterRules.KEEP_MAX) / 2.0;

    /** The Caster this goal flies. */
    private final BoundVex vex;

    /**
     * Creates the goal for one vex (it only runs while the vex {@code isCaster()}).
     *
     * @param vex the vex
     */
    CasterGoal(BoundVex vex) {
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = vex.getTarget();
        return vex.isCaster() && target != null && target.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = vex.getTarget();
        if (target == null) {
            return;
        }
        TeamFlight.lookAt(vex, target);
        Vec3 spot = TeamFlight.ringPoint(target.position(), vex.position(), HOLD_DISTANCE, target.getEyeY());
        Vec3 gap = spot.subtract(vex.position());
        boolean hold = CasterRules.approach(vex.distanceTo(target)) == CasterRules.Approach.HOLD;
        if (!hold && gap.length() > SETTLE_RANGE) {
            TeamFlight.flyTo(vex, spot, TeamFlight.NORMAL);
            return;
        }
        // A vex only accelerates toward its wanted point and coasts, so a plain "fly to the spot" overshoots the
        // band: near the spot (or inside the band) the push is a spring to the spot, none when holding, with
        // damping on the vex's own velocity, as RaidMove.closeIn does.
        Vec3 spring = hold ? Vec3.ZERO : gap.scale(SETTLE_POSITION_GAIN);
        TeamFlight.steer(vex, spring.subtract(vex.getDeltaMovement().scale(SETTLE_VELOCITY_GAIN)));
    }
}
```

with, beside `HOLD_DISTANCE`, the three constants (each with a Javadoc line): `SETTLE_RANGE = 3.0` (inside this distance from the spot the push is a spring), `SETTLE_POSITION_GAIN = 0.06` and `SETTLE_VELOCITY_GAIN = 0.3` (the values `RaidMove` uses for the same job).

`VexSummons.summon`, after `vex.finalizeSpawn(...)`:

```java
if (Dice.CASTER_ROLL.below(random, CasterRules.CASTER_ONE_IN) == 0) {
    vex.setCaster(true);
}
```

Also give the `BoundVex` constants (`CASTER_TAG`, `MIRROR_READY_TAG`, `GHOSTRAIL_READY_TAG`) a one-line Javadoc each (every constant, private ones too).

`TeamMoves`: rename the body of `isFree(vex, owner)` into the three-argument form and delegate:

```java
public static boolean isFree(BoundVex vex, ServerPlayer owner) {
    return isFree(vex, owner, false);
}

/**
 * {@link #isFree(BoundVex, ServerPlayer)}, with Casters counted when {@code forRaid}: a Caster is free only for a Raid
 * (Parts 6-7 spec §2.2, §6.2).
 */
public static boolean isFree(BoundVex vex, ServerPlayer owner, boolean forRaid) {
    return vex.isAlive()
            && (forRaid || !vex.isCaster())
            && vex.isSummonedBy(owner.getUUID())
            && vex.level().equals(owner.level())
            && !vex.isStaying()
            && vex.teamMove() == TeamMove.NONE
            && vex.distanceTo(owner) <= FREE_RANGE
            && !Jobs.busy(vex)
            && !VexRescues.rescuer(owner).map(vex::equals).orElse(false);
}
```

(remove the `// Part 7: and not a Caster ...` comment), and the same split for `freeVexes`: `freeVexes(owner)` = `freeVexes(owner, false)` (kept: Part 6 tests call it); `freeVexes(owner, forRaid)` queries with `vex -> isFree(vex, owner, forRaid)`; the package-private list form **becomes** `freeVexes(owner, vexes, forRaid)` (change its signature; its only caller is `scan`, so no two-argument overload stays behind). In `start`: `chooseRoster(move, freeVexes(owner, move == TeamMove.RAID), subject)` and `TeamMoveKits.kit(move, level, vex.getRandom(), vex.isCaster())`. In `TeamMoveTriggers.scan`: `List<BoundVex> free = TeamMoves.freeVexes(owner, followers, false);` and `List<BoundVex> raidFree = TeamMoves.freeVexes(owner, followers, true);`, and use `raidFree.size()` only for `raidReady`; every other readiness and `targetsFreeVex` keep the Caster-free list (`countsTowardARaidTrigger` pins this).

`TeamMoveKits`: change `kit(move, level, random)` into the form with a `caster` flag (no three-argument overload stays: `TeamMoves.start` is its only caller); split the armor out of `raid`:

```java
static Map<EquipmentSlot, ItemStack> kit(TeamMove move, ServerLevel level, RandomSource random, boolean caster) {
    Map<EquipmentSlot, ItemStack> kit = new EnumMap<>(EquipmentSlot.class);
    switch (move) {
        case SHIELD, EVADER -> {
            kit.put(EquipmentSlot.MAINHAND, unbreakable(new ItemStack(Items.SHIELD)));
            kit.put(EquipmentSlot.OFFHAND, unbreakable(new ItemStack(Items.SHIELD)));
        }
        case MULTISHOT -> kit.put(EquipmentSlot.MAINHAND, multishotCrossbow(level));
        case RAID -> {
            if (caster) {
                raidCaster(kit, random);
            } else {
                raid(kit, random);
            }
        }
        case NONE -> {
            // no kit
        }
    }
    return kit;
}

/** A Caster's Raid kit: the Mirror scroll, a shield, and one armor material from one roll. */
private static void raidCaster(Map<EquipmentSlot, ItemStack> kit, RandomSource random) {
    kit.put(EquipmentSlot.MAINHAND, ModItems.scrollFor(Spells.MIRROR));
    kit.put(EquipmentSlot.OFFHAND, unbreakable(new ItemStack(Items.SHIELD)));
    armor(kit, Dice.RAID_KIT.below(random, 2) == 0);
}

/** Head, chest and legs, all netherite or all diamond, unbreakable. */
private static void armor(Map<EquipmentSlot, ItemStack> kit, boolean netherite) {
    kit.put(EquipmentSlot.HEAD, unbreakable(new ItemStack(netherite ? Items.NETHERITE_HELMET : Items.DIAMOND_HELMET)));
    kit.put(
            EquipmentSlot.CHEST,
            unbreakable(new ItemStack(netherite ? Items.NETHERITE_CHESTPLATE : Items.DIAMOND_CHESTPLATE)));
    kit.put(
            EquipmentSlot.LEGS,
            unbreakable(new ItemStack(netherite ? Items.NETHERITE_LEGGINGS : Items.DIAMOND_LEGGINGS)));
}
```

`raid()` keeps its main-hand and off-hand rolls and ends with `armor(kit, Dice.RAID_KIT.below(random, 2) == 0)` (its comment `// Part 7: ...` goes). The scroll is not made unbreakable (a scroll has no durability). `RaidMove.tick` and the Caster's gather come in Task 9; until then a Caster in a Raid is a charging melee member: that is why Task 9 follows at once. (Task 8's only Raid-with-a-Caster test, `countsTowardARaidTrigger`, asserts the roster and the kit right after the start, before any member acts.)

Find the existing tests that summon through `VexSummons.summon`, directly or through the V key's request, and rely on a fighting vex:

```bash
git grep -n "VexSummons.summon\|EvokerSpell.VEXES" -- src/main/java/io/github/willrieske/spellcraft/gametest
```

(The known hits are `BoundVexCapGameTests` and `BoundVexFollowGameTests`; `EvokingCaster.request` for fangs is not a summon.) Read each hit. A test that calls `VexSummons.summon` itself is synchronous: if it then needs the vex to fight, hold gear, follow or count on a plain vex, script a plain vex around the call, `Dice.CASTER_ROLL.script(1, 1, 1)` immediately before and `Dice.unscriptAll()` in a `finally` after, and register the test in the environment `caster_dice` (a test in `SHARED` must not script). A test that summons through `EvokingCaster.request` is asynchronous: the roll happens inside a later `EvokingCaster.tick`, so there is no "immediately before" to script; if such a test needs a plain vex, call `vex.setCaster(false)` on the vexes as soon as they exist. A test that only counts vexes or checks messages needs nothing (the cap tests are of this kind). Note the tests you changed in the commit message.

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test`, `./gradlew runGameTestServer "-PgameTests=spellcraft:caster_*"`, then the full `./gradlew runGameTestServer` once to see whether any existing test broke because a summoned vex was a Caster. Expected: PASS after the Step 3 fixes.

- [x] **Step 5: Gates and commit**

Run: `./gradlew spotlessApply build`, `./gradlew runGameTestServer` twice. Expected: `All 728 required tests passed` both times (the Task 7 total plus the 15 `caster_*` tests registered in `CasterGameTests`).

```bash
git add src
git commit -m "feat(evoking): Casters, one vex in three, that keep their distance and never fight"
```

> **As built (`31ca0c1`):** diverged — no RED capture (production code partly written before tests); caster_plain_vex_charges resets a vex parked at rest beside the frozen husk each tick (test-only: ChargeGoal needs >2 blocks, a real target moves); countsTowardARaidTrigger proves the Shield control with a second summoner; reason: implementer report, review approved

---

### Task 9: The Caster casts: wind-up, cooldowns, the Raid gather

**Files:**

- Create: `evoking/CasterSpellGoal.java`, `evoking/ScrollCasting.java`, `gametest/CasterSpellGameTests.java`
- Modify: `evoking/BoundVex.java` (register the goal), `evoking/TeamMoves.java` (`Member.casterDone`), `evoking/RaidMove.java`, `gametest/SpellcraftGameTests.java`

**Interfaces:**

- Consumes: Task 3 (`Spells.cast`, `Spells.refusedFor`, `ModEffects.CASTING_SCROLL`, `ModItems.scrollFor`), Tasks 4-7 (`MirrorImages`, `Ghostrail`; the Ghostrail casts need Task 7's effect hook and Task 6's flight), Task 8 (`isCaster`, ready times, `CasterRules`, `ScrollTestSupport.casterVex/raidScene`), Task 4 (`ScrollTestSupport.hostileAt`).
- Produces:
  - `ScrollCasting` (public final, all static; the one home of the pieces every non-player cast shares, used by this task, Task 10 and Task 12): `int ticksFor(Spell, GhostrailDuration.Kind)` (Mirror `MirrorRule.DURATION_TICKS`; Ghostrail `GhostrailDuration.of(kind)`); `long cooldownFor(Spell)` (Mirror `CasterRules.MIRROR_COOLDOWN_TICKS`, Ghostrail `CasterRules.GHOSTRAIL_COOLDOWN_TICKS`); `void windUp(LivingEntity caster)` (adds `ModEffects.CASTING_SCROLL` for `CasterRules.WIND_UP_TICKS`, visible particles, no icon, and plays `EVOKER_PREPARE_SUMMON`); `void windDown(LivingEntity caster)` (removes it); `boolean land(LivingEntity caster, LivingEntity subject, Spell spell, GhostrailDuration.Kind kind)` (false when `Spells.refusedFor(subject, spell)`; else `Spells.cast(subject, spell, ticksFor(spell, kind))`, `EVOKER_CAST_SPELL` from the caster when it was added; returns whether the effect was added).
  - `CasterSpellGoal` (public final, priority 2, flags `MOVE` and `LOOK`, no target needed): picks a spell and subject (never Ghostrail for a creative or spectator player: `Spells.refusedFor`), holds a conjured scroll of the spell in the main hand for `CasterRules.WIND_UP_TICKS` while carrying `ModEffects.CASTING_SCROLL`, applies the spell at the end if the subject still qualifies, dismisses the scroll and removes the casting effect right there, then sets the cooldown. An interruption dismisses the scroll and spends nothing.
  - `TeamMoves.Member.casterDone` (package-private mutable boolean).
  - `RaidMove.tick`: a Caster member flies to its cell, casts Mirror on the summoner once at the start of the gather if the summoner has none (ignoring and then setting its Mirror cooldown), holds the scroll through the gather and, when the charge starts, empties its conjured main hand and holds its cell.

- [x] **Step 1: Write the failing tests**

`CasterSpellGameTests` (javadoc `/** GameTests of a Caster's casting and of a Caster in a Raid (Parts 6-7 spec §6.2). */`). A scene helper:

```java
/** A summoner, a parked Caster 3 blocks above them holding a golden sword, and {@code hostiles} husks beside it. */
private static Scene scene(GameTestHelper helper, int hostiles) {
    ServerPlayer player = plainPlayer(helper);
    floor(helper, 3);
    // 3 blocks above the standing spot: clearly inside GuardPosture.WIDE_LIMIT (4.0), and above the barrier shell
    Vec3 perch = at(helper, 0.0, 3.0, 0.0);
    BoundVex caster = casterVex(helper, player, perch);
    caster.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
    for (int i = 0; i < hostiles; i++) {
        hostileAt(helper, perch.add(i == 0 ? 2.0 : -2.0, 0.0, 0.0));
    }
    return new Scene(player, caster);
}

/** The cast scene's summoner and Caster. */
private record Scene(ServerPlayer player, BoundVex caster) {}
```

(`casterVex` pauses `FollowSummonerGoal`, so nothing pulls the Caster off its perch. The Caster is 3 blocks above the player, inside the 16-block range of every cast rule.) Registration (all `FangGameTests.addPadded(SHARED, name, 200, …)` except the Raid ones, which register in `"team_move_dice"` because they script `Dice.RAID_KIT`; each name is `caster_` plus the method name in snake case):

```java
static void registerAll() {
    FangGameTests.addPadded(SHARED, "caster_casts_mirror", 200, CasterSpellGameTests::castsMirror);
    FangGameTests.addPadded(SHARED, "caster_wind_up_look", 200, CasterSpellGameTests::windUpLook);
    FangGameTests.addPadded(SHARED, "caster_gives_the_item_back", 200, CasterSpellGameTests::givesTheItemBack);
    FangGameTests.addPadded(SHARED, "caster_cooldown_blocks_a_second", 200, CasterSpellGameTests::cooldownBlocksASecond);
    FangGameTests.addPadded(SHARED, "caster_one_hostile_is_not_enough", 200, CasterSpellGameTests::oneHostileIsNotEnough);
    FangGameTests.addPadded(SHARED, "caster_ghostrail_on_the_hurt_summoner", 200, CasterSpellGameTests::ghostrailOnTheHurtSummoner);
    FangGameTests.addPadded(SHARED, "caster_ghostrail_on_a_hurt_vex", 200, CasterSpellGameTests::ghostrailOnAHurtVex);
    FangGameTests.addPadded(SHARED, "caster_ghostrail_in_a_wall", 200, CasterSpellGameTests::ghostrailInAWall);
    FangGameTests.addPadded(SHARED, "caster_no_ghostrail_for_a_creative_summoner", 200, CasterSpellGameTests::noGhostrailForCreative);
    FangGameTests.addPadded(SHARED, "caster_interruption_spends_nothing", 200, CasterSpellGameTests::interruptionSpendsNothing);
    FangGameTests.addPadded(SHARED, "caster_two_casters_one_cast", 200, CasterSpellGameTests::twoCastersOneCast);
    FangGameTests.addPadded("team_move_dice", "caster_raid_casts_mirror", 300, CasterSpellGameTests::raidCastsMirror);
    FangGameTests.addPadded("team_move_dice", "caster_raid_skips_the_cast", 300, CasterSpellGameTests::raidSkipsTheCast);
    FangGameTests.addPadded("team_move_dice", "caster_raid_holds_its_cell", 300, CasterSpellGameTests::raidHoldsItsCell);
    FangGameTests.addPadded("team_move_dice", "caster_raid_caster_never_attacks", 300, CasterSpellGameTests::raidCasterNeverAttacks);
}
```

(Task 8's `caster_counts_toward_a_raid_trigger` and `caster_raid_kit` already prove that a Caster joins the Raid roster and gets its kit; they are not repeated here.)

```java
/**
 * When two hostiles stand in a Caster's guard area with the summoner near, should the Caster cast Mirror after the
 * 20-tick wind-up: the summoner gets the effect and 13 images, the Caster's cooldown starts and the scroll is gone.
 */
static void castsMirror(GameTestHelper helper) {
    // Arrange
    Scene scene = scene(helper, 2);
    // Act & Assert
    helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(scene.player().hasEffect(ModEffects.MIRROR), "the summoner has Mirror"))
            .thenExecute(() -> {
                helper.assertValueEqual(images(scene.player()).size(), MirrorRule.IMAGES, "13 images");
                helper.assertTrue(
                        scene.caster().mirrorReadyAt() > helper.getLevel().getGameTime(), "the cooldown started");
                helper.assertFalse(scene.caster().isConjured(EquipmentSlot.MAINHAND), "the scroll is dismissed");
            })
            .thenSucceed();
}

/**
 * When a Caster winds up, should it hold the Mirror scroll in its main hand and carry the casting effect, and have
 * neither once the spell lands.
 */
static void windUpLook(GameTestHelper helper) {
    // Arrange
    Scene scene = scene(helper, 2);
    // Act & Assert
    helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(
                    scene.caster().getMainHandItem().is(ModItems.MIRROR_SCROLL.get()), "the scroll is in hand"))
            .thenExecute(() -> helper.assertTrue(
                    scene.caster().hasEffect(ModEffects.CASTING_SCROLL), "the casting effect is on"))
            .thenWaitUntil(() -> helper.assertTrue(scene.player().hasEffect(ModEffects.MIRROR), "the spell lands"))
            .thenExecute(() -> helper.assertFalse(
                    scene.caster().hasEffect(ModEffects.CASTING_SCROLL), "the casting effect is gone"))
            .thenSucceed();
}
```

`givesTheItemBack`: after the cast (wait for the player's Mirror) the Caster's main hand is the golden sword and the slot is not conjured. `cooldownBlocksASecond`: after the first cast `scene.player().removeEffect(ModEffects.MIRROR)`; `thenExecuteAfter(60, …)` asserts the summoner still has no Mirror (hostiles are still there). `oneHostileIsNotEnough`: `scene(helper, 1)`; `thenExecuteAfter(80, …)` asserts no Mirror. `ghostrailOnTheHurtSummoner`: `scene(helper, 0)`, `scene.player().setHealth(4.0F)`; wait for `hasEffect(GHOSTRAIL)` on the player. `ghostrailOnAHurtVex`: `scene(helper, 0)` and a sibling `BoundVexTestSupport.noTargets(BoundVexTestSupport.boundVex(helper, BoundVexTestSupport.AIR_SPOT, player))` with `setHealth(1.0F)`; wait for the sibling's Ghostrail; the summoner has none. `ghostrailInAWall`: `scene(helper, 0)`; `helper.setBlock(FangGameTests.FEET.above(), Blocks.STONE)` (the block at the player's eyes), assert `player.isInWall()` as setup, wait for the player's Ghostrail. `interruptionSpendsNothing`:

```java
static void interruptionSpendsNothing(GameTestHelper helper) {
    // Arrange
    Scene scene = scene(helper, 2);
    Goal blocker = new Goal() {
        {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return true;
        }
    };
    // Act & Assert
    helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(
                    scene.caster().getMainHandItem().is(ModItems.MIRROR_SCROLL.get()), "winding up"))
            .thenExecute(() -> scene.caster().goalSelector.addGoal(0, blocker))
            .thenExecuteAfter(5, () -> {
                helper.assertFalse(scene.caster().isConjured(EquipmentSlot.MAINHAND), "the scroll is dismissed");
                helper.assertValueEqual(scene.caster().mirrorReadyAt(), 0L, "no cooldown spent");
                helper.assertFalse(scene.player().hasEffect(ModEffects.MIRROR), "no spell");
                scene.caster().goalSelector.removeGoal(blocker);
            })
            .thenSucceed();
}
```

`twoCastersOneCast`: `scene(helper, 2)` plus a second Caster from `casterVex` at `hang.add(0.0, 0.0, 1.0)`; wait until the summoner has Mirror, then `thenExecuteAfter(60, …)`: `images(player).size() == 13` (only one set) and exactly one of the two Casters has `mirrorReadyAt() > 0`.

`noGhostrailForCreative` (`Spells.refusedFor`, the one creative refusal of Part 7): `scene(helper, 0)`, `scene.player().setGameMode(GameType.CREATIVE)` and `scene.player().setHealth(4.0F)` (below a quarter: the emergency is real); `thenExecuteAfter(80, …)` asserts the player has no Ghostrail, the Caster's `ghostrailReadyAt()` is still 0 and its main hand is still the golden sword. Its control is `ghostrailOnTheHurtSummoner`, the same scene in survival.

Raid tests. Each starts from `ScrollTestSupport.raidScene(helper)` (Task 8: a player, a 1000-health big husk beside the hang point, twelve flying vexes, the first a Caster; the husk's health is raised because eleven netherite swordsmen would kill the Part 6 husk, which has 40 health, in the first volley, and `TeamMoves.endReason` "subject gone" would end the move and dismiss the kits before the test looks at the Caster), `Dice.RAID_KIT.script(new int[23])` (11 ordinary members take 2 rolls each, the Caster takes 1: all zero is a sword and netherite) immediately before `TeamMoves.start(player, TeamMove.RAID, husk)` in the same call, `finally Dice.unscriptAll()`, and then `TeamMoveTestSupport.stepEveryTick(helper, player)`. `raidCastsMirror`: wait until `player.hasEffect(MIRROR)` (within 5 ticks, no 20-tick wind-up), then assert `mirrorReadyAt() > 0` and that the Caster's main hand **still** holds the Mirror scroll (it holds it through the gather); after the gather (`thenWaitUntil` the main hand is empty, at most 60 ticks) the slot is empty and still conjured. `raidSkipsTheCast`: `Spells.cast(player, Spells.MIRROR, 7200)` first; after 10 ticks the Caster's main hand still holds the scroll, `mirrorReadyAt() == 0` and `images(player).size() == 13` (no second set). `raidHoldsItsCell`: after 150 ticks (the gather is 40) the husk is still alive (assert it, so the scene cannot silently end the move), the Caster is within 2 blocks of `RaidMove.cell(husk.getEyePosition(), player.position(), TeamMoves.roster(player).indexOf(caster))` and its main hand is empty. `raidCasterNeverAttacks` (the spec's "a Caster never fights", in a Raid, where the other eleven members do): `helper.startSequence().thenExecuteFor(150, …)` asserts every tick that `TeamFlight.boxGap(caster, husk) >= 1.0` (a member in the charge flies into the enemy's box to hit it; the Caster holds its grid cell outside it), and that the other members did reach the husk at least once (`husk.getHealth() < husk.getMaxHealth()` at the end), so the scene really is a charge.

Add `CasterSpellGameTests.registerAll();` after `CasterGameTests.registerAll();`.

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:caster_*"`. Expected: the new tests FAIL (no casting yet).

- [x] **Step 3: Write the implementation**

`ScrollCasting` (the shared pieces; Tasks 10 and 12 call it too):

```java
/**
 * What every cast that is not a player's hand shares (Parts 6-7 spec §6.2, §8, §9): how long a spell lasts for each
 * kind of subject, how long its cooldown is, the wind-up's look and sounds, and the landing. A Caster Vex, a scroll
 * mob and a villager ghost call these, so a number or a sound changes in one place.
 */
public final class ScrollCasting {
    private ScrollCasting() {}

    /**
     * Ticks {@code spell} lasts on a subject of {@code kind}: Mirror always {@link MirrorRule#DURATION_TICKS},
     * Ghostrail {@link GhostrailDuration#of}.
     *
     * @param spell Mirror or Ghostrail
     * @param kind what carries the effect
     * @return the duration in ticks
     */
    public static int ticksFor(Spell spell, GhostrailDuration.Kind kind) {
        return spell.equals(Spells.GHOSTRAIL) ? GhostrailDuration.of(kind) : MirrorRule.DURATION_TICKS;
    }

    /**
     * Ticks before the same caster may cast {@code spell} again (a Caster Vex and a villager ghost; a scroll mob's
     * Ghostrail has its own shorter one).
     *
     * @param spell Mirror or Ghostrail
     * @return the cooldown in ticks
     */
    public static long cooldownFor(Spell spell) {
        return spell.equals(Spells.GHOSTRAIL) ? CasterRules.GHOSTRAIL_COOLDOWN_TICKS : CasterRules.MIRROR_COOLDOWN_TICKS;
    }

    /**
     * Starts a wind-up on {@code caster}: the casting effect (visible particles, no icon) for
     * {@link CasterRules#WIND_UP_TICKS} and the prepare sound.
     *
     * @param caster whoever winds up
     */
    public static void windUp(LivingEntity caster) {
        caster.addEffect(new MobEffectInstance(
                ModEffects.CASTING_SCROLL, CasterRules.WIND_UP_TICKS, 0, false, true, false));
        caster.playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.0F, 1.0F);
    }

    /**
     * Ends the wind-up's look.
     *
     * @param caster whoever wound up
     */
    public static void windDown(LivingEntity caster) {
        caster.removeEffect(ModEffects.CASTING_SCROLL);
    }

    /**
     * Lands {@code spell} on {@code subject}: refused for a creative or spectator player when the spell is redundant
     * there ({@link Spells#refusedFor}); otherwise cast for {@link #ticksFor}, with the cast sound from the caster
     * when the effect was added.
     *
     * @param caster whoever casts (the sound comes from it)
     * @param subject who gets the effect
     * @param spell Mirror or Ghostrail
     * @param kind what the subject is, for Ghostrail's duration
     * @return whether the effect was added
     */
    public static boolean land(LivingEntity caster, LivingEntity subject, Spell spell, GhostrailDuration.Kind kind) {
        if (Spells.refusedFor(subject, spell) || !Spells.cast(subject, spell, ticksFor(spell, kind))) {
            return false;
        }
        caster.playSound(SoundEvents.EVOKER_CAST_SPELL, 1.0F, 1.0F);
        return true;
    }
}
```

`CasterSpellGoal`:

```java
/**
 * A Caster casts a scroll on its summoner (Parts 6-7 spec §6.2): Ghostrail first when the summoner, or a sibling vex
 * within {@link CasterRules#RANGE} blocks, is in an emergency; else Mirror when the summoner is near, has none and at
 * least {@link CasterRules#MIRROR_HOSTILES} hostiles stand in the Caster's guard area. Each spell has its own
 * cooldown, saved on the vex. The cast takes {@link CasterRules#WIND_UP_TICKS} ticks, holding a conjured scroll of the
 * spell in the main hand (the given item is stashed) with the casting effect on; at the end the spell lands if the
 * subject still qualifies, and the scroll and the effect go at that moment. A flee, a rescue or a team move that
 * preempts the goal dismisses the scroll and spends nothing. Ghostrail is never cast on a creative or spectator
 * player ({@link Spells#refusedFor}). Priority 2, beside the job goals, and needs no target.
 */
public final class CasterSpellGoal extends Goal {
    /** The Caster this goal belongs to. */
    private final BoundVex vex;

    /** The spell chosen for the cast in progress, or null. */
    private @Nullable Spell spell;

    /** Who the cast in progress is for, or null. */
    private @Nullable LivingEntity subject;

    /** Ticks into the wind-up. */
    private int ticks;

    /** Whether the cast has landed or been given up, so the goal can end. */
    private boolean done;

    /**
     * Creates the goal for one vex (it only runs while the vex {@code isCaster()}).
     *
     * @param vex the vex
     */
    CasterSpellGoal(BoundVex vex) {
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return vex.isCaster() && vex.scanDue(vex.scanInterval()) && choose();
    }

    @Override
    public boolean canContinueToUse() {
        return !done && subject != null && subject.isAlive();
    }

    @Override
    public void start() {
        ticks = 0;
        done = false;
        if (spell == null) {
            return;
        }
        vex.conjure(EquipmentSlot.MAINHAND, ModItems.scrollFor(spell));
        ScrollCasting.windUp(vex);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (subject != null) {
            TeamFlight.lookAt(vex, subject);
        }
        if (++ticks >= CasterRules.WIND_UP_TICKS) {
            finish();
        }
    }

    /**
     * The end of the wind-up: cast if the subject still qualifies, then start the cooldown; either way the goal ends.
     * The scroll and the casting effect go here, not only in {@link #stop()}: the goal selector calls {@code stop()}
     * one or two ticks after {@code done} is set (only on even {@code tickCount + id} ticks), and a test or a player
     * watching right after the spell lands must not see a scroll still in hand.
     */
    private void finish() {
        done = true;
        LivingEntity target = subject;
        Spell chosen = spell;
        if (target != null && chosen != null && stillQualifies(target, chosen)) {
            GhostrailDuration.Kind kind =
                    target instanceof Player ? GhostrailDuration.Kind.PLAYER : GhostrailDuration.Kind.BOUND_VEX;
            if (ScrollCasting.land(vex, target, chosen, kind)) {
                long readyAt = vex.level().getGameTime() + ScrollCasting.cooldownFor(chosen);
                if (chosen.equals(Spells.MIRROR)) {
                    vex.setMirrorReadyAt(readyAt);
                } else {
                    vex.setGhostrailReadyAt(readyAt);
                }
            }
        }
        dismissScroll();
    }

    /** Whether {@code target} is still worth the spell at the end of the wind-up. */
    private boolean stillQualifies(LivingEntity target, Spell chosen) {
        return target.isAlive()
                && target.level().equals(vex.level())
                && target.distanceTo(vex) <= CasterRules.RANGE
                && !target.hasEffect(chosen.effect());
    }

    /** Takes the conjured scroll out of the main hand (the stashed item comes back) and ends the casting look. */
    private void dismissScroll() {
        vex.dismiss(EquipmentSlot.MAINHAND);
        ScrollCasting.windDown(vex);
    }

    @Override
    public void stop() {
        // Idempotent: the normal end already dismissed the scroll in finish(); an interruption comes only here
        dismissScroll();
        spell = null;
        subject = null;
    }

    /** Picks the spell and subject for this scan, or false when nothing is worth casting. */
    private boolean choose() {
        ServerPlayer owner = vex.summoner().orElse(null);
        if (owner == null) {
            return false;
        }
        long now = vex.level().getGameTime();
        if (CasterRules.ready(now, vex.ghostrailReadyAt())) {
            LivingEntity hurt = emergency(owner);
            if (hurt != null) {
                spell = Spells.GHOSTRAIL;
                subject = hurt;
                return true;
            }
        }
        if (CasterRules.ready(now, vex.mirrorReadyAt())
                && owner.distanceTo(vex) <= CasterRules.RANGE
                && !owner.hasEffect(ModEffects.MIRROR)
                && CasterRules.wantsMirror(hostilesInGuardArea())) {
            spell = Spells.MIRROR;
            subject = owner;
            return true;
        }
        return false;
    }

    /**
     * The summoner if it needs Ghostrail (and {@link Spells#refusedFor} does not forbid it: a creative or spectator
     * summoner flies already), else the neediest sibling vex in range, else null.
     */
    private @Nullable LivingEntity emergency(ServerPlayer owner) {
        if (owner.distanceTo(vex) <= CasterRules.RANGE
                && !owner.hasEffect(ModEffects.GHOSTRAIL)
                && !Spells.refusedFor(owner, Spells.GHOSTRAIL)
                && CasterRules.wantsGhostrail(owner.getHealth(), owner.getMaxHealth(), owner.isInWall(), true)) {
            return owner;
        }
        return vex.nearby(
                        BoundVex.class,
                        CasterRules.RANGE,
                        other -> !other.equals(vex)
                                && other.isSummonedBy(owner.getUUID())
                                && !other.hasEffect(ModEffects.GHOSTRAIL)
                                && CasterRules.wantsGhostrail(other.getHealth(), other.getMaxHealth(), false, false))
                .stream()
                .min(Comparator.comparingDouble(other -> other.getHealth() / other.getMaxHealth()))
                .orElse(null);
    }

    /**
     * How many hostiles this vex may hunt stand in its guard area. The broad phase is {@code BoundVex.guardArea()},
     * which is the stay box around the post for a staying Caster (13 x 13 x 7) and the box around the vex otherwise;
     * a box inflated by a fixed amount around the vex would miss hostiles 5 to 6.5 blocks out in a stay box.
     */
    private int hostilesInGuardArea() {
        return vex.level()
                .getEntitiesOfClass(
                        LivingEntity.class,
                        vex.guardArea(),
                        other -> other.isAlive() && vex.inGuardArea(other) && vex.huntable(other) && vex.isPrey(other))
                .size();
    }
}
```

Register it in `BoundVex.registerGoals` beside the job goals: `goalSelector.addGoal(2, new CasterSpellGoal(this));`.

`TeamMoves.Member`: add

```java
/** Whether a Caster member has already done its one Raid cast (or skipped it). */
boolean casterDone;
```

`RaidMove.tick`: delete the `// Part 7: a Caster only gathers, casts Mirror, then holds its cell` comment on the first line of the method, and insert, right after `long now = level.getGameTime();` (where `enemy` and `now` exist):

```java
if (vex.isCaster()) {
    casterTick(vex, record, member, enemy, now);
    return;
}
```

and add:

```java
/**
 * A Caster in a Raid: it flies to its grid cell and holds it for the whole move, and never attacks. At the start of
 * the gather it casts Mirror on the summoner at once (no wind-up) if the summoner has none, ignoring its own Mirror
 * cooldown and then setting it. It holds the scroll through the gather (spec §4.4: the scroll is its gather kit) and
 * empties the conjured main hand only when the charge starts.
 */
private static void casterTick(
        BoundVex vex, TeamMoves.Move record, TeamMoves.Member member, LivingEntity enemy, long now) {
    Vec3 summoner = vex.summoner().map(Entity::position).orElse(vex.position());
    TeamFlight.flyTo(vex, cell(enemy.getEyePosition(), summoner, member.cell), TeamFlight.NORMAL);
    if (!member.casterDone) {
        member.casterDone = true;
        vex.summoner().filter(owner -> !owner.hasEffect(ModEffects.MIRROR)).ifPresent(owner -> {
            if (ScrollCasting.land(vex, owner, Spells.MIRROR, GhostrailDuration.Kind.PLAYER)) {
                vex.setMirrorReadyAt(now + ScrollCasting.cooldownFor(Spells.MIRROR));
            }
        });
    }
    if (record.phase == TeamMoves.Phase.CHARGE && !vex.getMainHandItem().isEmpty()) {
        member.kit.put(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        vex.conjure(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }
}
```

(`member.kit` must be a mutable map: it is the `EnumMap` `TeamMoveKits.kit` returned. If `Member.kit` is a final field of an immutable copy, make it mutable.)

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:caster_*"` and `"-PgameTests=spellcraft:team_move_*"` (Part 6's Raid tests must still pass). Expected: PASS.

- [x] **Step 5: Gates and commit**

Run: `./gradlew spotlessApply build`, `./gradlew runGameTestServer` twice. Expected: `All 743 required tests passed` both times (the Task 8 total plus the 15 `caster_*` tests registered in `CasterSpellGameTests`).

```bash
git add src
git commit -m "feat(evoking): Casters cast Mirror and Ghostrail, alone and in a Raid"
```

> **As built (`f871ee5`):** diverged — CasterSpellGameTests scene floor is 8 blocks, not 3 (Mirror images wander up to 6 blocks and fell off a 3-block floor; spec has no ledge avoidance); review minors fixed in f871ee5 (dismiss once, exact cast-timing bound, choose() clears its pick); reason: implementer report, review approved

---

### Task 10: Mobs that carry scrolls

**Files:**

- Create: `evoking/ScrollMobs.java`, `evoking/GhostrailChaseGoal.java`, `gametest/ScrollMobGameTests.java`
- Modify: `gametest/SpellcraftGameTests.java`

**Interfaces:**

- Consumes: Task 4 (`Dice.SCROLL_MOB`), Task 7 (`EvokingAttachments.MOB_MIRROR_READY/MOB_GHOSTRAIL_READY`, `Ghostrail`), Task 3 (`Spells.cast`, `ModItems.scrollFor`), Task 9 (`ScrollCasting.ticksFor/cooldownFor/land`).
- Produces:
  - `ScrollMobs` (public final, `@EventBusSubscriber`): `onFinalizeSpawn(FinalizeSpawnEvent)` (an `Evoker` carries a scroll in the **offhand** 1 time in 4, a `Vindicator` or `Pillager` 1 in 10, Mirror or Ghostrail at even odds, offhand drop chance 0.5; the rolls are `Dice.SCROLL_MOB.below(random, 4 or 10)` and, for a carrier, `Dice.SCROLL_MOB.below(random, 2)` where 0 is Mirror), `onChangeTarget(LivingChangeTargetEvent)` (a mob with a Mirror or Ghostrail scroll in its offhand that goes from no target to a target casts at once if that spell's cooldown is over: Mirror on itself for 7200 ticks with a 7200-tick cooldown unless it has the effect; Ghostrail on itself for 40 ticks with a 600-tick cooldown; both through `ScrollCasting.land` (so the durations, the cast sound and the cooldown of Mirror come from the one place) and then a burst of 10 purple `ENTITY_EFFECT` particles; the scroll is not used up), `onJoin(EntityJoinLevelEvent)` (installs the chase goal), `static void install(Mob mob)` (public, idempotent).
  - `GhostrailChaseGoal` (public final, goal priority 0, flag `MOVE`): while the mob has Ghostrail and a target it stops the navigation and sets its velocity straight at the target's middle at 0.3 blocks per tick.

- [x] **Step 1: Write the failing tests**

A spawn fires `FinalizeSpawnEvent` through `EventHooks.finalizeMobSpawn(mob, level, difficulty, spawnType, spawnData)` (`EventHooks.java:334`; it posts the event, then calls the deprecated `Mob.finalizeSpawn`): call exactly that from the tests, never `Mob.finalizeSpawn` itself (it does not post the event, and calling it directly fails the `-Werror` build).

`ScrollMobGameTests` (javadoc `/** GameTests of the mobs that carry scrolls (Parts 6-7 spec §8) and the Ghostrail chase goal (§7.3). */`). Tests that script `Dice.SCROLL_MOB` register in the environment `private static final String DICE = "scroll_mob_dice";`; the rest in `SHARED`; all through `FangGameTests.addPadded`. Helpers: `private static <T extends Mob> T spawned(GameTestHelper helper, EntityType<T> type)` creates the mob with `type.create(level, EntitySpawnReason.SPAWNER)`, positions it with `setPos(at(helper, 4.0, 0.0, 0.0))`, calls `EventHooks.finalizeMobSpawn(...)` and adds it (`level.addFreshEntity`) with `noAi`, `whenDone(helper, mob::discard)`; `private static Evoker armed(GameTestHelper helper, Spell spell)` is a no-AI evoker from `foeAt(helper, EntityTypes.EVOKER, at(helper, 4.0, 0.0, 0.0))` (4 blocks east of the standing spot, so a test's player stands apart from it) with `setItemSlot(OFFHAND, ModItems.scrollFor(spell))`; a pillager variant in `castsGhostrail` is placed the same way.

```java
static void registerAll() {
    FangGameTests.addPadded(DICE, "scroll_mob_evoker_spawn", ScrollMobGameTests::evokerSpawn);
    FangGameTests.addPadded(DICE, "scroll_mob_drop_chance_is_half", ScrollMobGameTests::dropChanceIsHalf);
    FangGameTests.addPadded(DICE, "scroll_mob_pillager_ghostrail", ScrollMobGameTests::pillagerGhostrail);
    FangGameTests.addPadded(DICE, "scroll_mob_evoker_roll_is_one_in_four", ScrollMobGameTests::evokerRollIsOneInFour);
    FangGameTests.addPadded(DICE, "scroll_mob_pillager_roll_is_one_in_ten", ScrollMobGameTests::pillagerRollIsOneInTen);
    FangGameTests.addPadded(SHARED, "scroll_mob_casts_mirror", ScrollMobGameTests::castsMirror);
    FangGameTests.addPadded(SHARED, "scroll_mob_cooldown_holds", ScrollMobGameTests::cooldownHolds);
    FangGameTests.addPadded(SHARED, "scroll_mob_casts_ghostrail", ScrollMobGameTests::castsGhostrail);
    FangGameTests.addPadded(SHARED, "scroll_mob_no_scroll_no_cast", ScrollMobGameTests::noScrollNoCast);
    FangGameTests.addPadded(SHARED, "scroll_mob_no_recast_on_a_switch", ScrollMobGameTests::noRecastOnASwitch);
    FangGameTests.addPadded(SHARED, "scroll_mob_sound_burst_and_scroll", ScrollMobGameTests::soundBurstAndScroll);
    FangGameTests.addPadded(SHARED, "scroll_mob_survives_a_save", ScrollMobGameTests::survivesASave);
    FangGameTests.addPadded(SHARED, "scroll_mob_chase_goal_installed", ScrollMobGameTests::chaseGoalInstalled);
    FangGameTests.addPadded(SHARED, "scroll_mob_chases_through_a_wall", 100, ScrollMobGameTests::chasesThroughAWall);
}
```

```java
/**
 * When the dice roll 0 (carries) then 0 (Mirror) for an evoker, should it spawn with a Mirror scroll in its offhand.
 */
static void evokerSpawn(GameTestHelper helper) {
    // Arrange
    plainPlayer(helper);
    // Act
    Evoker evoker;
    try {
        Dice.SCROLL_MOB.script(0, 0);
        evoker = spawned(helper, EntityTypes.EVOKER);
    } finally {
        Dice.unscriptAll();
    }
    // Assert
    helper.assertTrue(evoker.getOffhandItem().is(ModItems.MIRROR_SCROLL.get()), "a Mirror scroll in the offhand");
    helper.assertTrue(evoker.getMainHandItem().isEmpty(), "the main hand is vanilla's");
    helper.succeed();
}
```

`dropChanceIsHalf`: the same spawn: `evoker.getDropChances().byEquipment(EquipmentSlot.OFFHAND)` equals `0.5F`. `pillagerGhostrail`: script `0, 1`, spawn a `Pillager`: offhand is `GHOSTRAIL_SCROLL`. `evokerRollIsOneInFour`: script `3` (the last side of the 4-sided die, so "no"): an evoker's offhand is empty; and `helper.assertValueEqual(ScrollMobs.EVOKER_ONE_IN, 4, "an evoker rolls a 4-sided die")`. `pillagerRollIsOneInTen`: script `9` (the last side of the 10-sided die): a pillager's offhand is empty; and `ScrollMobs.RAIDER_ONE_IN` equals 10. (The two constants are therefore `public`. A scripted roll outside the bound makes `Dice` throw, but whether that exception reaches the test through the event bus is not something to depend on, so no test relies on it.)

```java
/** When an evoker with a Mirror scroll takes a target, should it get Mirror at once with 13 images of itself. */
static void castsMirror(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = plainPlayer(helper);
    floor(helper, 6);
    Evoker evoker = armed(helper, Spells.MIRROR);
    // Act
    evoker.setTarget(player);
    // Assert
    helper.assertTrue(evoker.hasEffect(ModEffects.MIRROR), "Mirror on the evoker");
    helper.assertValueEqual(images(evoker).size(), MirrorRule.IMAGES, "13 images of the evoker");
    helper.succeed();
}
```

`cooldownHolds`: after the cast, `evoker.setTarget(null)`, `evoker.removeEffect(ModEffects.MIRROR)`, `evoker.setTarget(player)` again: no Mirror (cooldown 7200). `castsGhostrail`: `armed`-style pillager with a Ghostrail scroll: after `setTarget(player)` the pillager's `getEffect(GHOSTRAIL).getDuration()` is `GhostrailDuration.HOSTILE_TICKS` and `getExistingData(EvokingAttachments.MOB_GHOSTRAIL_READY)` equals `level.getGameTime() + 600`. `noScrollNoCast`: a no-AI evoker with an empty offhand takes a target: no Mirror, no Ghostrail. `noRecastOnASwitch`: an armed evoker `setTarget(husk)` (casts once, from no target to a target); then clear everything that could block a second cast **other than** the switch rule: `evoker.removeEffect(ModEffects.MIRROR)` and `evoker.setData(EvokingAttachments.MOB_MIRROR_READY, 0L)` (the 7200-tick cooldown would otherwise block it and the test could never fail); assert the first cast happened, then `evoker.setTarget(player)` with the old target (the husk) still set: no second cast (the effect stays absent). Without the `mob.getTarget() != null` guard in `onChangeTarget` this test fails. `soundBurstAndScroll`: a mock player near; `GameTestSupport.drainShown(player)` first; the evoker takes a target; the drain holds `SoundEvents.EVOKER_CAST_SPELL` and `ParticleTypes.ENTITY_EFFECT`, and the evoker's offhand is still the scroll. `survivesASave`: after a cast, `Evoker copy = (Evoker) GameTestSupport.reloaded(level, evoker)`: `copy.getExistingData(EvokingAttachments.MOB_MIRROR_READY)` equals the original's and its offhand is a Mirror scroll. `chaseGoalInstalled`: a `helper.spawn(EntityTypes.PILLAGER, ...)`'s `goalSelector.getAvailableGoals()` holds a `GhostrailChaseGoal` (join event), and an evoker and a vindicator too; a zombie has none.

```java
/**
 * When a pillager with Ghostrail and a target has a stone wall between it and the target, should it fly straight at the
 * target through the wall and end up within 2 blocks of it.
 */
static void chasesThroughAWall(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = plainPlayer(helper);
    player.setPermanentlyInvulnerable(true);
    GameTestSupport.whenDone(helper, () -> player.setPermanentlyInvulnerable(false));
    floor(helper, 12);
    // A wall across the whole floor (z -12 to 12, 4 high): there is no ground beyond its ends and it is too high to
    // jump, so a pillager that reaches the player has not walked around it
    for (int y = 0; y <= 3; y++) {
        for (int z = -12; z <= 12; z++) {
            helper.setBlock(FangGameTests.FEET.offset(5, y, z), Blocks.STONE);
        }
    }
    Pillager pillager = helper.spawn(EntityTypes.PILLAGER, FangGameTests.FEET.offset(9, 0, 0));
    GameTestSupport.whenDone(helper, pillager::discard);
    pillager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    // Act
    Spells.cast(pillager, Spells.GHOSTRAIL, 400);
    pillager.setTarget(player);
    // Assert: 100 ticks is the test's limit; the chase covers the 8 blocks in about 30
    helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(pillager.distanceTo(player) < 2.0, "the pillager reached the target"))
            .thenSucceed();
}
```

Add `ScrollMobGameTests.registerAll();` after `CasterSpellGameTests.registerAll();`.

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:scroll_mob_*"`. Expected: FAIL (nothing spawns with a scroll yet).

- [x] **Step 3: Write the implementation**

`ScrollMobs`:

```java
/**
 * Evokers, vindicators and pillagers that carry a Mirror or Ghostrail scroll and use it on themselves (Parts 6-7 spec
 * §8). The scroll sits in the offhand with a drop chance of 0.5, which vanilla honors only for a player's kill (Looting
 * raises it): that is the parent spec's "loot path for players". Vanilla's own equipment fills only the main hand and
 * runs after {@code FinalizeSpawnEvent}.
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class ScrollMobs {
    /** An evoker carries a scroll one time in this many. */
    public static final int EVOKER_ONE_IN = 4;

    /** A vindicator or pillager carries a scroll one time in this many. */
    public static final int RAIDER_ONE_IN = 10;

    /** The offhand drop chance of a carried scroll. */
    static final float DROP_CHANCE = 0.5F;

    /** Ticks before a mob may cast Ghostrail on itself again (30 seconds; Mirror's is {@code ScrollCasting}'s). */
    static final int MOB_GHOSTRAIL_COOLDOWN_TICKS = 600;

    /** The cast's purple, the Evoker-spell color of {@code CastingParticles} for a scroll. */
    private static final int PURPLE = 0xFFB24CFF;

    /** Particles in the cast's burst. */
    private static final int BURST = 10;

    private ScrollMobs() {}

    /**
     * The sides of the carry die for {@code mob}: 4 for an evoker, 10 for a vindicator or pillager, 0 for any other
     * mob.
     */
    private static int oneIn(Mob mob) {
        if (mob instanceof Evoker) {
            return EVOKER_ONE_IN;
        }
        return mob instanceof Vindicator || mob instanceof Pillager ? RAIDER_ONE_IN : 0;
    }

    /**
     * Gives a new mob a scroll: the carry roll, then the spell (0 Mirror, 1 Ghostrail).
     *
     * @param event the finalize-spawn event
     */
    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        Mob mob = event.getEntity();
        int oneIn = oneIn(mob);
        if (oneIn == 0 || Dice.SCROLL_MOB.below(mob.getRandom(), oneIn) != 0) {
            return;
        }
        Spell spell = Dice.SCROLL_MOB.below(mob.getRandom(), 2) == 0 ? Spells.MIRROR : Spells.GHOSTRAIL;
        mob.setItemSlot(EquipmentSlot.OFFHAND, ModItems.scrollFor(spell));
        mob.setDropChance(EquipmentSlot.OFFHAND, DROP_CHANCE);
    }

    /**
     * Casts the carried scroll when the mob goes from no target to a target (so a mob that loses its target casts next
     * time it gets one) and the spell's cooldown is over. Instant: a 20-tick wind-up on a fighting illager is easy to
     * miss. The scroll is not used up.
     *
     * @param event the change-target event for any living entity
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)
                || event.getNewAboutToBeSetTarget() == null
                || mob.getTarget() != null
                || !(mob.level() instanceof ServerLevel level)
                || !(mob.getOffhandItem().getItem() instanceof SpellScrollItem scroll)) {
            return;
        }
        long now = level.getGameTime();
        Spell spell = scroll.spell();
        if (spell.equals(Spells.MIRROR)
                && !mob.hasEffect(ModEffects.MIRROR)
                && CasterRules.ready(now, mob.getExistingData(EvokingAttachments.MOB_MIRROR_READY).orElse(0L))
                && ScrollCasting.land(mob, mob, Spells.MIRROR, GhostrailDuration.Kind.HOSTILE_MOB)) {
            mob.setData(EvokingAttachments.MOB_MIRROR_READY, now + ScrollCasting.cooldownFor(Spells.MIRROR));
            burst(level, mob);
        } else if (spell.equals(Spells.GHOSTRAIL)
                && !mob.hasEffect(ModEffects.GHOSTRAIL)
                && CasterRules.ready(now, mob.getExistingData(EvokingAttachments.MOB_GHOSTRAIL_READY).orElse(0L))
                && ScrollCasting.land(mob, mob, Spells.GHOSTRAIL, GhostrailDuration.Kind.HOSTILE_MOB)) {
            mob.setData(EvokingAttachments.MOB_GHOSTRAIL_READY, now + MOB_GHOSTRAIL_COOLDOWN_TICKS);
            burst(level, mob);
        }
    }

    /** A burst of purple particles (the cast sound came from {@link ScrollCasting#land}). */
    private static void burst(ServerLevel level, Mob mob) {
        level.sendParticles(
                ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, PURPLE),
                mob.getX(),
                mob.getY() + mob.getBbHeight() / 2.0,
                mob.getZ(),
                BURST,
                0.4,
                0.5,
                0.4,
                0.05);
    }

    /**
     * Installs the chase goal on an evoker, vindicator or pillager that joins a level (new, loaded or arrived).
     *
     * @param event the join event
     */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob && oneIn(mob) != 0) {
            install(mob);
        }
    }

    /**
     * Adds a {@link GhostrailChaseGoal} (priority 0) unless the mob has one.
     *
     * @param mob a mob
     */
    public static void install(Mob mob) {
        boolean present = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof GhostrailChaseGoal);
        if (!present) {
            mob.goalSelector.addGoal(0, new GhostrailChaseGoal(mob));
        }
    }
}
```

(A mob's Mirror cooldown is `CasterRules.MIRROR_COOLDOWN_TICKS` (7200), the same number as a Caster's; its Ghostrail cooldown is its own 600.)

`GhostrailChaseGoal`:

```java
/**
 * While a scroll mob has Ghostrail and a target, flies straight at the target's middle at {@link #SPEED} blocks per
 * tick (Parts 6-7 spec §7.3). Pathfinding would walk around the wall Ghostrail is for, and {@code Mob.serverAiStep}
 * overwrites a wanted position set from outside the goals, so the goal stops the navigation and sets the velocity
 * itself; the mob has no gravity and no collision while it carries the effect ({@code Ghostrail.tick}), so it can rise
 * and sink.
 */
public final class GhostrailChaseGoal extends Goal {
    /** Blocks per tick. */
    static final double SPEED = 0.3;

    /** The mob that chases. */
    private final Mob mob;

    /**
     * Creates the goal for one mob (it only runs while the mob has Ghostrail and a target).
     *
     * @param mob the mob
     */
    GhostrailChaseGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return mob.hasEffect(ModEffects.GHOSTRAIL) && target != null && target.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        mob.getNavigation().stop();
        Vec3 gap = target.getBoundingBox().getCenter().subtract(mob.getBoundingBox().getCenter());
        double length = gap.length();
        if (length > 1.0E-3) {
            mob.setDeltaMovement(gap.scale(Math.min(SPEED, length) / length));
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }
}
```

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:scroll_mob_*"`. If `chasesThroughAWall` times out, read the log for the pillager's position per tick before changing the goal: the usual cause is the vanilla `AbstractIllager`/`Raider` goals running at a higher priority on `MOVE` (none should, the goal is priority 0) or `Ghostrail.tick` not holding `noPhysics` (the `EntityTickEvent.Post` needs the saved mark: `Ghostrail.begin` sets it in the effect hook).
Expected: PASS.

- [x] **Step 5: Gates and commit**

Run: `./gradlew spotlessApply build`, `./gradlew runGameTestServer` twice. Expected: `All 757 required tests passed` both times (the Task 9 total plus the 14 `scroll_mob_*` tests of `ScrollMobGameTests`).

```bash
git add src
git commit -m "feat(evoking): evokers, vindicators and pillagers that carry scrolls"
```

> **As built (`ea7c862`):** diverged — chasesThroughAWall keeps the player vulnerable (Mob.asValidTarget rejects an invulnerable target via canBeSeenAsEnemy, so the plan's setTarget was dropped) and empties the pillager's main hand instead; floored scenes use radius 12 for Mirror image wander room; EVOKER_CAST_SPELL has no .value() in 26.3; MOB_GHOSTRAIL_COOLDOWN_TICKS made public for the test; RED was compile-time; reason: implementer report, review approved

### Task 11: Villager ghost records

**Files:**

- Create: `evoking/VillagerGhosts.java` (records and the death hook; the spawner comes in Task 12), `gametest/VillagerGhostGameTests.java`
- Modify: `spell/Allies.java`, `spell/GolemMercy.java`, `gametest/ScrollTestSupport.java`, `gametest/SpellcraftGameTests.java`

**Interfaces:**

- Consumes: Task 2 (`GhostRecords`), Task 4 (`ScrollTestSupport`, which this task edits), `Allies`, `AllyAttachments.SUMMONER`.
- Produces:
  - `Allies.isPlayersSide(DamageSource)` (public static; moved out of `GolemMercy`, which now calls it): the damage's causing entity is a `Player`, a tamed `Wolf`, or any entity carrying a summoner mark.
  - `VillagerGhosts` (public final, `extends SavedData`): `record Record(UUID id, BlockPos pos, VillagerData data, long mirrorReadyAt, long ghostrailReadyAt)` with `Record withMirrorReadyAt(long)` and `Record withGhostrailReadyAt(long)`; `static final Codec<VillagerGhosts> CODEC`; `static final SavedDataType<VillagerGhosts> TYPE` (id `spellcraft:villager_ghosts`); `static VillagerGhosts get(ServerLevel)`; `List<Record> records()` (oldest first); `void add(Record)` (applies `GhostRecords.add(…, GhostRecords.MAX)`, `setDirty`); `Optional<Record> find(UUID)`; `void remove(UUID)`; `void setMirrorReadyAt(UUID, long)`; `void setGhostrailReadyAt(UUID, long)` (each replaces the record and calls `setDirty`).
  - `VillagerGhosts.onDeath(LivingDeathEvent)` (`@SubscribeEvent`; the class is `@EventBusSubscriber`): a `Villager` whose killer (`source.getEntity()`) is an `Enemy` and `!Allies.isPlayersSide(source)` leaves a record (a villager a zombie then converts still does: the event fires before the conversion). A villager killed by lava, a fall, a cactus, a player, a golem or a Bound Vex leaves none (amends the parent §9.5).
  - `ScrollTestSupport.sampleVillagerData(GameTestHelper)` (a fixed, **non-default** `VillagerData`: desert type, librarian profession, level 3; a fresh villager's data equals the codec's defaults, so a default sample could not catch a codec that drops a field) and `ScrollTestSupport.record(GameTestHelper, Vec3 absolute)` (adds a record there with the sample data, removes it again in `whenDone`, returns it).

- [x] **Step 1: Write the failing tests**

`VillagerGhostGameTests` (javadoc `/** GameTests of the villager-ghost records (Parts 6-7 spec §9, §10) and, from Task 12, the ghosts. */`). **Record budget:** the records live in one level-wide list capped at 32, and tests of one batch run at the same time, so a cap eviction would remove another test's record. The `SHARED` tests of this task keep at most three records alive at once (the kill test, the data test and the save test), and each cleans up its own records (`ScrollTestSupport.record` and `whenDone` do); the 33-deaths test and the conversion test run in environments of their own, `villager_ghost_records` and `villager_ghost_hard`, and Task 12 puts its tests in environments of their own too (`villager_ghost_help`, `villager_ghost_fade`), so the budget never nears 32.

```java
/** The environment of the 33-deaths test: it fills the whole record list. */
private static final String RECORDS = "villager_ghost_records";

/** The environment of the conversion test: it raises the difficulty to Hard (a global setting). */
private static final String HARD = "villager_ghost_hard";

static void registerAll() {
    FangGameTests.addPadded(SHARED, "villager_ghost_record_a_pillager_kill_writes_a_record", VillagerGhostGameTests::pillagerKillWritesARecord);
    FangGameTests.addPadded(SHARED, "villager_ghost_record_keeps_the_villagers_data", VillagerGhostGameTests::recordKeepsTheData);
    FangGameTests.addPadded(SHARED, "villager_ghost_record_none_for_a_player_kill", VillagerGhostGameTests::playerKillWritesNone);
    FangGameTests.addPadded(SHARED, "villager_ghost_record_none_for_a_bound_vex_kill", VillagerGhostGameTests::boundVexKillWritesNone);
    FangGameTests.addPadded(SHARED, "villager_ghost_record_none_for_a_golem_kill", VillagerGhostGameTests::golemKillWritesNone);
    FangGameTests.addPadded(SHARED, "villager_ghost_record_none_for_lava", VillagerGhostGameTests::lavaWritesNone);
    FangGameTests.addPadded(SHARED, "villager_ghost_record_none_for_a_fall", VillagerGhostGameTests::fallWritesNone);
    FangGameTests.addPadded(SHARED, "villager_ghost_records_survive_a_save", VillagerGhostGameTests::recordsSurviveASave);
    FangGameTests.addPadded(RECORDS, "villager_ghost_records_33_deaths_keep_32", 400, VillagerGhostGameTests::thirtyThreeDeaths);
    FangGameTests.addPadded(HARD, "villager_ghost_record_survives_a_zombie_conversion", VillagerGhostGameTests::conversionStillLeavesARecord);
}
```

```java
/** Kills {@code villager} with {@code source} (a hit far above its health). */
private static void kill(GameTestHelper helper, Villager villager, DamageSource source) {
    villager.hurtServer(helper.getLevel(), source, 1000.0F);
}

/** The record at {@code villager}'s block, if any. */
private static Optional<VillagerGhosts.Record> recordAt(GameTestHelper helper, BlockPos pos) {
    return VillagerGhosts.get(helper.getLevel()).records().stream()
            .filter(record -> record.pos().equals(pos))
            .findFirst();
}

/**
 * A no-AI villager of the non-default sample data in a test-owned spot, which removes the record at its spot when
 * the test ends.
 */
private static Villager villager(GameTestHelper helper) {
    Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
    villager.setNoAi(true);
    villager.setVillagerData(ScrollTestSupport.sampleVillagerData(helper));
    BlockPos pos = villager.blockPosition();
    ServerLevel level = helper.getLevel();
    GameTestSupport.whenDone(helper, () -> recordAt(helper, pos)
            .ifPresent(record -> VillagerGhosts.get(level).remove(record.id())));
    return villager;
}

/** When a pillager (an Enemy that is not a Zombie) kills a villager, should a record of the death be written there. */
static void pillagerKillWritesARecord(GameTestHelper helper) {
    // Arrange (the padded wrapper has already loaded the box; no player is needed)
    ServerLevel level = helper.getLevel();
    Pillager pillager = foeAt(helper, EntityTypes.PILLAGER, at(helper, 3.0, 0.0, 0.0));
    Villager villager = villager(helper);
    BlockPos pos = villager.blockPosition();
    // Act
    kill(helper, villager, level.damageSources().mobAttack(pillager));
    // Assert
    helper.assertTrue(recordAt(helper, pos).isPresent(), "a record at the death spot");
    helper.succeed();
}
```

(A zombie or husk is the wrong killer for the plain tests: `Zombie.killedEntity` converts a killed villager 50 % of the time on Normal, which spawns a live `ZombieVillager` that nothing discards. The conversion has its own test below.)

`recordKeepsTheData`: the same, then `recordAt(helper, pos).orElseThrow().data()` equals `ScrollTestSupport.sampleVillagerData(helper)` (the non-default sample the villager was given; this fails for a record that stores the default, or a codec that drops type, profession or level). `playerKillWritesNone`: `kill(helper, villager, level.damageSources().playerAttack(player))` then no record at `pos`. `boundVexKillWritesNone`: a `BoundVexTestSupport.boundVex(helper, BoundVexTestSupport.AIR_SPOT, player)` is the killer (an `Enemy` that carries a summoner mark, so this is the test of `isPlayersSide`): `level.damageSources().mobAttack(vex)`; no record. `golemKillWritesNone`: an iron golem (`foeAt(helper, EntityTypes.IRON_GOLEM, …)`) is the killer; no record. `lavaWritesNone`: `level.damageSources().lava()`; no record. `fallWritesNone`: `level.damageSources().fall()`; no record. `conversionStillLeavesARecord` (environment `villager_ghost_hard`): set the server difficulty to Hard, saving the old one and restoring it in `whenDone`, as `BoundVexGearGameTests` does around line 371 (`server.setDifficulty(saved, true)`); a frozen husk (`hostileAt`) kills the villager with `mobAttack(husk)`; on Hard `Zombie.killedEntity` always converts it: assert that a `ZombieVillager` now exists near the spot (discard it in `whenDone`) **and** that a record exists at `pos` (the spec's "a villager a zombie then converts still leaves a record"). `recordsSurviveASave`:

```java
/** When the records are encoded and decoded, should the same records come back, oldest first. */
static void recordsSurviveASave(GameTestHelper helper) {
    // Arrange
    VillagerData data = sampleVillagerData(helper);
    VillagerGhosts ghosts = VillagerGhosts.get(helper.getLevel());
    VillagerGhosts.Record record = new VillagerGhosts.Record(UUID.randomUUID(), new BlockPos(7, 70, 9), data, 123L, 456L);
    ghosts.add(record);
    GameTestSupport.whenDone(helper, () -> ghosts.remove(record.id()));
    // Act
    Tag encoded = VillagerGhosts.CODEC.encodeStart(NbtOps.INSTANCE, ghosts).getOrThrow();
    VillagerGhosts decoded = VillagerGhosts.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
    // Assert
    helper.assertTrue(decoded.find(record.id()).filter(record::equals).isPresent(), "the record comes back whole");
    helper.succeed();
}
```

`thirtyThreeDeaths` (the killer is a frozen pillager, not a zombie): every villager spawns at the same block, so every record has the same `pos`, and a lookup by `pos` (`recordAt`) finds the **oldest** record at that spot, not the one just written. So after each of the 33 kills take the new record's id from the **tail** of the list, `VillagerGhosts.get(level).records().get(size - 1).id()`, and keep the 33 ids in order. At the end assert `records().size() == GhostRecords.MAX`, the first id is gone (`find(first)` is empty), the second is still present (the oldest kept) and the last is present (the newest is never evicted); `whenDone` removes every id this test wrote.

`ScrollTestSupport` additions:

```java
/** A fixed non-default villager look for a record: desert type, librarian profession, level 3. */
static VillagerData sampleVillagerData(GameTestHelper helper) {
    RegistryAccess registries = helper.getLevel().registryAccess();
    return new VillagerData(
            registries.lookupOrThrow(Registries.VILLAGER_TYPE).getOrThrow(VillagerType.DESERT),
            registries.lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(VillagerProfession.LIBRARIAN),
            3);
}

/** Adds a record at {@code at} (absolute) and removes it when the test ends. */
static VillagerGhosts.Record record(GameTestHelper helper, Vec3 at) {
    VillagerGhosts ghosts = VillagerGhosts.get(helper.getLevel());
    VillagerGhosts.Record record =
            new VillagerGhosts.Record(UUID.randomUUID(), BlockPos.containing(at), sampleVillagerData(helper), 0L, 0L);
    ghosts.add(record);
    GameTestSupport.whenDone(helper, () -> ghosts.remove(record.id()));
    return record;
}
```

Add `VillagerGhostGameTests.registerAll();` after `ScrollMobGameTests.registerAll();`.

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew compileJava` (FAIL: `VillagerGhosts` does not exist).

- [x] **Step 3: Write the implementation**

`Allies`: add (and delete the private copy from `GolemMercy`, whose two call sites, `GolemMercy.java:36` and `:52`, become `Allies.isPlayersSide(...)`; the `Entity`, `Player`, `Wolf` and `DamageSource` imports there are then unused, and `spotlessApply` removes them):

```java
/**
 * Whether the damage comes from a player's side: its causing entity is a player (a sword, or fangs through {@code
 * indirectMagic(fangs, caster)}), a tamed wolf (every tamed wolf is a player's, even while its owner is offline and
 * {@code getOwner()} cannot resolve it), or any mob carrying a summoner mark (round one's vexes, Bound Vexes from Part
 * 2 on, Mirror images of a player). Lava, falls, other mobs and {@code /kill} are not. Shared by {@code GolemMercy} and
 * the villager-ghost records.
 *
 * @param source the damage
 * @return true if a player's side caused it
 */
public static boolean isPlayersSide(DamageSource source) {
    Entity cause = source.getEntity();
    return cause instanceof Player
            || (cause instanceof Wolf wolf && wolf.isTame())
            || (cause != null && cause.hasData(AllyAttachments.SUMMONER));
}
```

`VillagerGhosts`:

```java
/**
 * The villager-ghost records of one level (Parts 6-7 spec §9): where a villager died to a monster, what it looked like
 * and when its two spells may be cast again. At most {@link GhostRecords#MAX} are kept; a new one drops the oldest.
 * Saved with the level; a ghost is the visit, the record is the memory. Task 12 adds the spawner.
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class VillagerGhosts extends SavedData {
    /** One note: a UUID, the death block, the villager's type, profession and level, and the two cooldown ends. */
    public record Record(UUID id, BlockPos pos, VillagerData data, long mirrorReadyAt, long ghostrailReadyAt) {
        /** The save format of one record. */
        private static final Codec<Record> CODEC = RecordCodecBuilder.create(i -> i.group(
                        UUIDUtil.CODEC.fieldOf("id").forGetter(Record::id),
                        BlockPos.CODEC.fieldOf("pos").forGetter(Record::pos),
                        VillagerData.CODEC.fieldOf("data").forGetter(Record::data),
                        Codec.LONG.fieldOf("mirror_ready").forGetter(Record::mirrorReadyAt),
                        Codec.LONG.fieldOf("ghostrail_ready").forGetter(Record::ghostrailReadyAt))
                .apply(i, Record::new));

        /** This record with a new Mirror cooldown end. */
        Record withMirrorReadyAt(long time) {
            return new Record(id, pos, data, time, ghostrailReadyAt);
        }

        /** This record with a new Ghostrail cooldown end. */
        Record withGhostrailReadyAt(long time) {
            return new Record(id, pos, data, mirrorReadyAt, time);
        }
    }

    /** The save format: the list, oldest first. */
    public static final Codec<VillagerGhosts> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Record.CODEC.listOf().fieldOf("records").forGetter(ghosts -> ghosts.records))
            .apply(i, VillagerGhosts::new));

    /** The saved-data type; one file per level. */
    public static final SavedDataType<VillagerGhosts> TYPE =
            new SavedDataType<>(Spellcraft.id("villager_ghosts"), VillagerGhosts::new, CODEC);

    /** The kept records, oldest first; replaced (never edited) on every change. */
    private List<Record> records;

    /** An empty list: the type's factory for a level with no saved file. */
    private VillagerGhosts() {
        this(List.of());
    }

    /** A list with the given records (the codec's constructor). */
    private VillagerGhosts(List<Record> records) {
        this.records = List.copyOf(records);
    }

    /** The records of {@code level}. */
    public static VillagerGhosts get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** The records, oldest first. */
    public List<Record> records() {
        return records;
    }

    /** Adds a record, dropping the oldest past {@link GhostRecords#MAX}. */
    public void add(Record record) {
        records = GhostRecords.add(records, record, GhostRecords.MAX);
        setDirty();
    }

    /** The record with {@code id}, if it is still kept. */
    public Optional<Record> find(UUID id) {
        return records.stream().filter(record -> record.id().equals(id)).findFirst();
    }

    /** Forgets the record with {@code id} (tests; nothing in play removes one). */
    public void remove(UUID id) {
        records = records.stream().filter(record -> !record.id().equals(id)).toList();
        setDirty();
    }

    /** Sets the game time the record's Mirror cooldown ends. */
    public void setMirrorReadyAt(UUID id, long time) {
        replace(id, record -> record.withMirrorReadyAt(time));
    }

    /** Sets the game time the record's Ghostrail cooldown ends. */
    public void setGhostrailReadyAt(UUID id, long time) {
        replace(id, record -> record.withGhostrailReadyAt(time));
    }

    private void replace(UUID id, UnaryOperator<Record> change) {
        records = records.stream().map(record -> record.id().equals(id) ? change.apply(record) : record).toList();
        setDirty();
    }

    /**
     * A villager that dies to a monster (an {@code Enemy} that is not on a player's side) leaves a record. The event
     * fires before a zombie's conversion of the villager, which is why a villager a zombie then converts still leaves
     * one.
     *
     * @param event the death event for any living entity
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        DamageSource source = event.getSource();
        if (event.getEntity() instanceof Villager villager
                && villager.level() instanceof ServerLevel level
                && source.getEntity() instanceof Enemy
                && !Allies.isPlayersSide(source)) {
            get(level).add(new Record(UUID.randomUUID(), villager.blockPosition(), villager.getVillagerData(), 0L, 0L));
        }
    }
}
```

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:villager_ghost_*"`, `"-PgameTests=spellcraft:golems_ii_*"`, `"-PgameTests=spellcraft:bound_vex_world_crit_*"` and `"-PgameTests=spellcraft:allies_*"` (the golem mercy tests, registered as `golems_ii_*` and `bound_vex_world_crit_keeps_golem_mercy`, must be unchanged; a selector that matches nothing fails the run). Expected: PASS.

- [x] **Step 5: Gates and commit**

Run: `./gradlew spotlessApply build`, `./gradlew runGameTestServer` twice. Expected: `All 767 required tests passed` both times (the Task 10 total plus the 10 `villager_ghost_record*` tests of this task).

```bash
git add src
git commit -m "feat(evoking): villagers that monsters kill leave a saved record"
```

> **As built (`ff499bf`):** diverged — no RED capture (production code written before the tests compiled); `VillagerGhosts.Record` carries `@SuppressWarnings("AvoidCommonTypeNames")` (Error Prone rejects the name the spec mandates); test helpers discard their villagers and any converted zombie villager when done; reason: implementer report, review approved (its minors are fixed first in Task 12).

---

### Task 12: The villager ghost: the entity, the spawner, the help, the fade

**Files:**

- Create: `evoking/VillagerGhost.java`
- Modify: `evoking/VillagerGhosts.java` (the spawner), `evoking/ModEntities.java`, `evoking/Ghostrail.java` (`flies`), `assets/spellcraft/lang/en_us.json`, `src/test/…/SpellAssetsTest.java`, `gametest/VillagerGhostGameTests.java`

**Interfaces:**

- Consumes: Task 2 (`GhostNeed`, `CasterRules`), Task 3 (`Spells.cast`, `Spells.refusedFor`), Task 7 (`Ghostrail`), Task 9 (`ScrollCasting`), Task 11 (`VillagerGhosts`), Task 4 (`ScrollTestSupport.hostileAt`).
- Produces:
  - `ModEntities.VILLAGER_GHOST` (`MISC`, `sized(0.6F, 1.95F)`, `clientTrackingRange(8)`, `updateInterval(1)` (the default of 3 ticks makes a ghost that moves by position every tick look steppy), `noSave()`, `noLootTable()`); attributes `MAX_HEALTH` 20, `MOVEMENT_SPEED` 0.5.
  - `VillagerGhost extends LivingEntity` (public final): synced `VillagerData` (`EntityDataSerializers.VILLAGER_DATA`); `VillagerData villagerData()`; `Optional<UUID> recordId()`; `Optional<UUID> helpedId()`; `void bind(VillagerGhosts.Record record, ServerPlayer player)` (call it **after** the ghost is positioned: it casts Ghostrail, whose start-spot probe reads the block at the ghost's position); `void fade()` (POOF particles, `VEX_DEATH` at volume 0.3, `discard`). Not saved (`shouldBeSaved` false), invulnerable (`setPermanentlyInvulnerable(true)`, so vanilla's `canBeSeenAsEnemy` is false), not attackable (`isAttackable()` and `attackable()` both false: fangs, a vindicator or a wither must not target it), not pickable, no gravity, no physics; moves by setting its own position toward the spot 2 blocks from the player at the player's eye height (at most its `MOVEMENT_SPEED` attribute in blocks per tick, easing in; the spec said velocity, position is simpler for a no-physics entity and gives the same motion); carries an infinite Ghostrail from the spawn. Like any Ghostrail carrier it can still be removed by `/kill` and the void (the invulnerable flag does not stop `GENERIC_KILL`); the record stays.
  - `VillagerGhosts.step(ServerPlayer)` (public static; the body of the 20-tick check; `onPlayerTick` runs it when `player.tickCount % 20 == 0`): a living, non-spectating player with the Evoking effect gets a ghost only when **all** hold, checked cheapest first: **at least one hostile** (`huntable` and `isPrey` for them) within `CasterRules.RANGE` of them (the ghost's own "still needed" test, so spawn and fade agree: a hurt player with no enemy near gets no ghost that would puff away at once); they are **in need** (`GhostNeed.inNeed(health, max, crowd)` with `crowd` the hostiles among those within `GhostNeed.CROWD_RANGE`); **no ghost is already helping** them; and a record exists in their level within 48 blocks, whose chunk is loaded and which has no live ghost (the nearest wins).

- [x] **Step 1: Write the failing tests**

Add to `VillagerGhostGameTests.registerAll` (all `FangGameTests.addPadded(ENV, name, 400, …)`: ghosts take up to 250 ticks). The tests that fill the record list register in their own environments so the level-wide 32-record cap is never near (Task 11's budget note): the help tests in `HELP = "villager_ghost_help"`, the fade test in `FADE = "villager_ghost_fade"` (the 200-tick quiet rule counts any hostile within 16 blocks, and the shared batch's padding is only 13, so a neighbor's ravager near its box edge could keep the ghost alive past the test's budget):

```java
FangGameTests.addPadded(HELP, "villager_ghost_appears_for_a_player_in_need_near_a_record", 400, VillagerGhostGameTests::appears);
FangGameTests.addPadded(HELP, "villager_ghost_takes_the_villagers_look", 400, VillagerGhostGameTests::takesTheLook);
FangGameTests.addPadded(HELP, "villager_ghost_flies_to_the_player_and_casts_mirror", 400, VillagerGhostGameTests::castsMirror);
FangGameTests.addPadded(HELP, "villager_ghost_casts_ghostrail_first_when_both_apply", 400, VillagerGhostGameTests::ghostrailFirst);
FangGameTests.addPadded(HELP, "villager_ghost_casts_no_ghostrail_for_a_creative_player", 400, VillagerGhostGameTests::noGhostrailForCreative);
FangGameTests.addPadded(FADE, "villager_ghost_fades_200_ticks_after_the_last_hostile_is_gone", 400, VillagerGhostGameTests::fades);
FangGameTests.addPadded(HELP, "villager_ghost_is_not_sent_to_a_player_not_in_need", 400, VillagerGhostGameTests::notForTheUnhurt);
FangGameTests.addPadded(HELP, "villager_ghost_is_not_sent_to_a_hurt_player_with_no_hostile_near", 400, VillagerGhostGameTests::notForAHurtPlayerWithNoHostile);
FangGameTests.addPadded(HELP, "villager_ghost_is_not_sent_to_a_player_without_evoking", 400, VillagerGhostGameTests::notWithoutEvoking);
FangGameTests.addPadded(HELP, "villager_ghost_a_second_record_sends_no_second_ghost", 400, VillagerGhostGameTests::oneGhostPerPlayer);
FangGameTests.addPadded(HELP, "villager_ghost_is_not_sent_for_a_record_beyond_48_blocks", 400, VillagerGhostGameTests::tooFar);
FangGameTests.addPadded(HELP, "villager_ghost_cannot_be_hurt", 400, VillagerGhostGameTests::cannotBeHurt);
FangGameTests.addPadded(HELP, "villager_ghost_is_no_target_for_fangs", 400, VillagerGhostGameTests::fangsIgnoreTheGhost);
FangGameTests.addPadded(HELP, "villager_ghost_without_a_record_discards_itself", 400, VillagerGhostGameTests::noRecordNoGhost);
FangGameTests.addPadded(HELP, "villager_ghost_whose_record_was_dropped_discards_itself", 400, VillagerGhostGameTests::droppedRecord);
```

(Both constants are `private static final String` with a Javadoc line each. The old `isNotSaved` test, which asserted that `shouldBeSaved()` returns a constant, is gone; `EntityType.noSave()` plus the constant need no test of their own.)

A scene helper (the player is in need by health, a frozen husk is the hostile that lets the ghost come and keeps it from fading):

```java
/**
 * A player with Evoking at 40 % health, optionally a frozen husk 3 blocks away (a hostile within the ghost's 16 blocks:
 * without one no ghost is sent, and with one it does not fade) and optionally a record 10 blocks east. A test that
 * needs the husk gone returns it from the scene and discards it.
 */
private static Scene scene(GameTestHelper helper, boolean husk, boolean record) {
    ServerPlayer player = FangGameTests.caster(helper, FangGameTests.NORTH);
    floor(helper, 14);
    player.setHealth(player.getMaxHealth() * 0.4F);
    Husk hostile = husk ? hostileAt(helper, at(helper, 3.0, 0.0, 0.0)) : null;
    VillagerGhosts.Record near = record ? ScrollTestSupport.record(helper, at(helper, 10.0, 0.0, 0.0)) : null;
    return new Scene(player, near, hostile);
}

/** The ghost scene's player, its record (null when not asked for) and its husk (null when not asked for). */
private record Scene(ServerPlayer player, @Nullable VillagerGhosts.Record record, @Nullable Husk husk) {}

/** The live ghosts for {@code record} in the test level. */
private static List<VillagerGhost> ghosts(GameTestHelper helper, VillagerGhosts.Record record) {
    return helper.getLevel().getEntities(EntityTypeTest.forClass(VillagerGhost.class),
            ghost -> ghost.isAlive() && ghost.recordId().filter(record.id()::equals).isPresent()).stream()
            .map(VillagerGhost.class::cast).toList();
}
```

```java
/**
 * When a player in need has a record 10 blocks away, should one step spawn a ghost at the record helping that player.
 */
static void appears(GameTestHelper helper) {
    // Arrange
    Scene scene = scene(helper, true, true);
    // Act
    VillagerGhosts.step(scene.player());
    // Assert
    List<VillagerGhost> ghosts = ghosts(helper, scene.record());
    helper.assertValueEqual(ghosts.size(), 1, "one ghost");
    helper.assertTrue(ghosts.get(0).helpedId().filter(scene.player().getUUID()::equals).isPresent(), "for this player");
    helper.assertTrue(ghosts.get(0).distanceToSqr(Vec3.atCenterOf(scene.record().pos())) < 4.0, "at the record");
    helper.succeed();
}
```

`takesTheLook`: after a step, `ghosts.get(0).villagerData()` equals `scene.record().data()` (the non-default sample, so a ghost that kept the default look fails). `castsMirror`: step, then `thenWaitUntil(() -> assertTrue(player.hasEffect(MIRROR)))`; then `ghost.distanceTo(player) <= 3.5`; and `VillagerGhosts.get(level).find(record.id()).orElseThrow().mirrorReadyAt() > level.getGameTime()` (the cooldown is saved on the record). `ghostrailFirst`: `player.setHealth(3.0F)` (below a quarter): after the step the first spell to land is Ghostrail: `thenWaitUntil(() -> assertTrue(player.hasEffect(GHOSTRAIL)))` and at that moment `!player.hasEffect(MIRROR)`; the record's `ghostrailReadyAt` is set. `noGhostrailForCreative` (`Spells.refusedFor`, the one creative refusal of Part 7): the same scene with `player.setGameMode(GameType.CREATIVE)` and `setHealth(3.0F)`; the ghost comes and casts Mirror (the control: wait for `hasEffect(MIRROR)`), and by then the player has no Ghostrail and the record's `ghostrailReadyAt` is still 0. `fades`: `scene(helper, true, true)`, step (the ghost comes: the husk is a hostile within 16 blocks); then `scene.husk().discard()` and `thenWaitUntil(() -> assertTrue(ghost.isRemoved(), "the ghost faded"))` within the test's ticks (200 quiet ticks after the husk is gone), and the record is still kept: `VillagerGhosts.get(level).find(record.id()).isPresent()`. `notForTheUnhurt`: full health, a husk near (a hostile, but no crowd and no hurt): step, `ghosts(helper, record)` is empty. `notForAHurtPlayerWithNoHostile` (spawn and fade must agree): `scene(helper, false, true)` (40 % health, no hostile anywhere near): step; no ghost for the record, and none after another 100 ticks of stepping (`thenExecuteFor(100, () -> VillagerGhosts.step(player))`: the hurt player is not sent a ghost that would puff away and be re-sent). `notWithoutEvoking`: `player.removeEffect(ModEffects.EVOKING)`; step; none. `oneGhostPerPlayer`: a second record via `ScrollTestSupport.record(helper, at(helper, -10.0, 0.0, 0.0))`; step twice; the two records together have exactly one ghost. `tooFar`: `scene(helper, true, false)` (a hurt player, a husk, **no** record of the scene's own) and a record 60 blocks away (`ScrollTestSupport.record(helper, at(helper, 60.0, 0.0, 0.0))`): step; no ghost for it. (With the scene's own 10-block record in the world, nearest-record selection and one ghost per player would pass whatever `RANGE` is; with only the far record, deleting the range check makes the test fail.) A boundary pair (a record at about 40 blocks gets a ghost, one at about 56 does not) needs a chunk loaded that far out; add it only if `FangGameTests.loadPadding`'s reach allows, and say in the as-built note if it does not. `cannotBeHurt`: after a step, `ghost.hurtServer(level, level.damageSources().generic(), 5.0F)` returns false, `ghost.getHealth() == ghost.getMaxHealth()`, `!ghost.isPickable()`, `!ghost.isAttackable()` and `!ghost.attackable()`. `fangsIgnoreTheGhost`: after a step the ghost hovers 2 blocks from the Evoking player and is the only living thing near (the husk of the scene is removed first); `EvokingCaster.request(player, EvokerSpell.FANGS)` returns `Request.NO_TARGETS` (`Fangs.isTarget` is package-private; the request goes through it and counts any `canBeSeenAsEnemy() && attackable()` entity as a target, so a ghost that was targetable would start a cast). `noRecordNoGhost`: `helper.spawn(ModEntities.VILLAGER_GHOST.get(), new BlockPos(1, 2, 1))`: `thenWaitUntil(ghost.isRemoved())`. `droppedRecord`: step, then `VillagerGhosts.get(level).remove(record.id())`: the ghost is removed within a few ticks.

Add `"entity.spellcraft.villager_ghost": "Villager Ghost",` to the lang file (after `"entity.spellcraft.mirror_image"`) and the `.contains("\"entity.spellcraft.villager_ghost\"")` line of `SpellAssetsTest.part7NamesAreEnglish`.

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew compileJava` (FAIL: `VillagerGhost` does not exist).

- [x] **Step 3: Write the implementation**

`ModEntities`:

```java
/**
 * The villager ghost (Parts 6-7 spec §9): the spirit drawn from a record, sized like a villager. Never saved: it
 * vanishes with its chunk or a restart, and the record stays.
 */
public static final DeferredHolder<EntityType<?>, EntityType<VillagerGhost>> VILLAGER_GHOST =
        ENTITIES.registerEntityType(
                "villager_ghost",
                VillagerGhost::new,
                MobCategory.MISC,
                builder -> builder.sized(0.6F, 1.95F)
                        .clientTrackingRange(8)
                        .updateInterval(1)
                        .noSave()
                        .noLootTable());

// in onAttributes:
event.put(
        VILLAGER_GHOST.get(),
        LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .build());
```

`Ghostrail.flies`: `return carrier instanceof Vex || carrier instanceof VillagerGhost;`, with its Javadoc updated to match: "Whether {@code carrier} flies through blocks by itself: a Vex or a villager ghost (a Vex's {@code noPhysics} is true only inside its own tick, so the flag cannot be the test; a ghost sets its own position and has no physics at all)."

`VillagerGhost`:

```java
/**
 * The spirit of a villager that a monster killed (Parts 6-7 spec §9). It floats to a player in need, casts Mirror or
 * Ghostrail on them with a 20-tick wind-up (the casting effect lights its hands; the villager model's arms are one
 * crossed part, so there is no arms-up pose), and fades when the fight is over, ready to come back next time. It
 * flies by setting its own position (no gravity, no collision), cannot be hurt, targeted or picked, and is never
 * saved. It carries an infinite Ghostrail from the spawn.
 */
public final class VillagerGhost extends LivingEntity {
    /** Synced: the villager's type, profession and level, for the look. */
    private static final EntityDataAccessor<VillagerData> VILLAGER_DATA =
            SynchedEntityData.defineId(VillagerGhost.class, EntityDataSerializers.VILLAGER_DATA);

    /** The distance from the player at which the ghost hovers, and from which it casts. */
    private static final double HOVER = 2.0;

    /** How close to the player the ghost must be to start a cast. */
    private static final double CAST_RANGE = 3.0;

    /** How far from the player a ghost stays. */
    private static final double LEAVE_DISTANCE = 64.0;

    /** Ticks with no hostile near the player before the ghost fades. */
    private static final int QUIET_LIMIT = 200;

    /** Ticks between two counts of the hostiles near the player (a 200-tick rule needs no finer count). */
    private static final int HOSTILE_CHECK_TICKS = 5;

    /** The record this ghost is the spirit of, or null for a ghost nobody sent (it discards itself). */
    private @Nullable UUID recordId;

    /** The player this ghost helps. */
    private @Nullable UUID helped;

    /** Ticks since a hostile was last near the player, counted in steps of {@link #HOSTILE_CHECK_TICKS}. */
    private int quietTicks;

    /** Ticks into the wind-up of the cast in progress. */
    private int windUp;

    /** The spell being wound up, or null. */
    private @Nullable Spell casting;

    /**
     * Creates a ghost (the entity type's factory). It is invulnerable from the start, so vanilla's
     * {@code canBeSeenAsEnemy} is false for it.
     *
     * @param type the entity type
     * @param level the level
     */
    public VillagerGhost(EntityType<? extends VillagerGhost> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setPermanentlyInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(VILLAGER_DATA, Villager.createDefaultVillagerData());
    }
    // villagerData(), recordId(), helpedId() accessors with Javadoc

    /**
     * Makes this ghost the spirit of {@code record}, helping {@code player}; infinite Ghostrail from now on. Call it
     * **after** positioning the ghost ({@code snapTo}): the Ghostrail it casts probes the block at the ghost's
     * position for a start spot, and an unpositioned entity sits at (0, 0, 0), which would load or generate that chunk.
     *
     * @param record the record the ghost is the spirit of
     * @param player the player it comes to help
     */
    public void bind(VillagerGhosts.Record record, ServerPlayer player) {
        recordId = record.id();
        helped = player.getUUID();
        entityData.set(VILLAGER_DATA, record.data());
        Spells.cast(this, Spells.GHOSTRAIL, GhostrailDuration.of(GhostrailDuration.Kind.VILLAGER_GHOST));
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    /** Not a target for anything: {@code Entity.isAttackable} (a player's attack) and {@code attackable} (mobs). */
    @Override
    public boolean isAttackable() {
        return false;
    }

    /** Mobs and fangs ask this one ({@code LivingEntity.attackable}, a different method from {@code isAttackable}). */
    @Override
    public boolean attackable() {
        return false;
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server) {
            serverTick(server);
        }
    }

    /** One server tick: the end conditions, the flight, the cast. */
    private void serverTick(ServerLevel level) {
        VillagerGhosts ghosts = VillagerGhosts.get(level);
        ServerPlayer player = helped == null ? null : level.getServer().getPlayerList().getPlayer(helped);
        if (recordId == null
                || ghosts.find(recordId).isEmpty()
                || player == null
                || !player.level().equals(level)
                || !player.isAlive()
                || player.isSpectator()
                || distanceTo(player) > LEAVE_DISTANCE) {
            fade();
            return;
        }
        if (tickCount % HOSTILE_CHECK_TICKS == 0) {
            boolean quiet = VillagerGhosts.hostilesWithin(level, player, CasterRules.RANGE).isEmpty();
            quietTicks = quiet ? quietTicks + HOSTILE_CHECK_TICKS : 0;
        }
        if (quietTicks >= QUIET_LIMIT) {
            fade();
            return;
        }
        flyToward(player);
        if (casting != null) {
            windUpTick(level, ghosts, player);
        } else if (distanceTo(player) <= CAST_RANGE) {
            startCast(level, ghosts, player);
        }
    }

    /** Moves, with no physics, toward the spot {@link #HOVER} blocks from the player at the player's eye height. */
    private void flyToward(ServerPlayer player) {
        Vec3 away = position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
        Vec3 side = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
        Vec3 spot = new Vec3(player.getX() + side.x * HOVER, player.getEyeY(), player.getZ() + side.z * HOVER);
        Vec3 gap = spot.subtract(position());
        double step = Math.min(getAttributeValue(Attributes.MOVEMENT_SPEED), gap.length() * 0.2);
        if (gap.length() > 1.0E-3) {
            setPos(position().add(gap.normalize().scale(step)));
        }
        setDeltaMovement(Vec3.ZERO);
        double dx = player.getX() - getX();
        double dz = player.getZ() - getZ();
        setYRot((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F);
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    /** Picks the spell (Ghostrail first when both apply) and starts the wind-up. */
    private void startCast(ServerLevel level, VillagerGhosts ghosts, ServerPlayer player) {
        VillagerGhosts.Record record = ghosts.find(recordId).orElseThrow();
        long now = level.getGameTime();
        if (!player.hasEffect(ModEffects.GHOSTRAIL)
                && !Spells.refusedFor(player, Spells.GHOSTRAIL)
                && CasterRules.ready(now, record.ghostrailReadyAt())
                && CasterRules.wantsGhostrail(player.getHealth(), player.getMaxHealth(), player.isInWall(), true)) {
            casting = Spells.GHOSTRAIL;
        } else if (!player.hasEffect(ModEffects.MIRROR) && CasterRules.ready(now, record.mirrorReadyAt())) {
            casting = Spells.MIRROR;
        } else {
            return;
        }
        windUp = 0;
        ScrollCasting.windUp(this);
    }

    /** Counts the wind-up; at the end casts if the player still qualifies and writes the cooldown to the record. */
    private void windUpTick(ServerLevel level, VillagerGhosts ghosts, ServerPlayer player) {
        if (++windUp < CasterRules.WIND_UP_TICKS) {
            return;
        }
        Spell spell = casting;
        casting = null;
        ScrollCasting.windDown(this);
        if (spell == null || player.hasEffect(spell.effect())) {
            return;
        }
        if (ScrollCasting.land(this, player, spell, GhostrailDuration.Kind.PLAYER)) {
            long readyAt = level.getGameTime() + ScrollCasting.cooldownFor(spell);
            if (spell.equals(Spells.GHOSTRAIL)) {
                ghosts.setGhostrailReadyAt(recordId, readyAt);
            } else {
                ghosts.setMirrorReadyAt(recordId, readyAt);
            }
        }
    }

    /** Ends the visit: a puff, a quiet vex death sound and {@code discard}. The record stays. */
    public void fade() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() / 2.0, getZ(), 8, 0.2, 0.3, 0.2, 0.02);
        }
        playSound(SoundEvents.VEX_DEATH, 0.3F, 1.0F);
        discard();
    }
}
```

(`Villager.createDefaultVillagerData()` is public static, `Villager.java:455`; `bind` always overwrites it, so the default only matters for a `/summon`ed ghost that discards itself on its first tick.) The ghost keeps no hostile-counting code of its own: it calls `VillagerGhosts.hostilesWithin` below, the one definition that the spawner uses too (spawn and fade must agree on what a hostile near the player is). Add a Javadoc to the `recordId()`, `helpedId()` and `villagerData()` accessors.

`VillagerGhosts` (add to the class):

```java
/** A record is only considered within this many blocks of the player. */
static final double RANGE = 48.0;

/** Ticks between two checks of each player. */
private static final int STEP_INTERVAL = 20;

/**
 * Runs {@link #step} every 20 ticks of each player's {@code tickCount}.
 *
 * @param event the player's post-tick event
 */
@SubscribeEvent
public static void onPlayerTick(PlayerTickEvent.Post event) {
    if (event.getEntity() instanceof ServerPlayer player && player.tickCount % STEP_INTERVAL == 0) {
        step(player);
    }
}

/**
 * Sends a ghost to {@code player} when they are in need and an enemy is near (spec §9). The conditions run cheapest
 * first: the player's own state, then one query for the hostiles within {@link CasterRules#RANGE} (none: no ghost,
 * because the ghost's own "still needed" test is the same query, so a ghost sent for a hurt player with no enemy would
 * puff away at once and be sent again), then the need, and only then the level-wide scans for a ghost already helping
 * or already out on a record. Public so GameTests step a mock player, which gets no player tick.
 *
 * @param player a player
 */
public static void step(ServerPlayer player) {
    ServerLevel level = player.level();
    if (!player.isAlive() || player.isSpectator() || !player.hasEffect(ModEffects.EVOKING)) {
        return;
    }
    List<LivingEntity> hostiles = hostilesWithin(level, player, CasterRules.RANGE);
    if (hostiles.isEmpty()) {
        return;
    }
    int crowd = (int) hostiles.stream()
            .filter(hostile -> hostile.distanceTo(player) <= GhostNeed.CROWD_RANGE)
            .count();
    if (!GhostNeed.inNeed(player.getHealth(), player.getMaxHealth(), crowd) || helping(level, player)) {
        return;
    }
    VillagerGhosts ghosts = get(level);
    ghosts.records().stream()
            .filter(record -> record.pos().distToCenterSqr(player.position()) <= RANGE * RANGE
                    && level.hasChunkAt(record.pos())
                    && !hasLiveGhost(level, record.id()))
            .min(Comparator.comparingDouble(record -> record.pos().distToCenterSqr(player.position())))
            .ifPresent(record -> spawn(level, record, player));
}

/**
 * The hostiles near a player: living, within {@code range} blocks, {@code huntable} and {@code isPrey} for that
 * player. The one definition of "a hostile near the player", used by {@link #step} (with
 * {@link CasterRules#RANGE}, then filtered to {@link GhostNeed#CROWD_RANGE} for the crowd) and by the ghost's fade
 * check. {@code BoundVex.huntable(UUID, LivingEntity)} is package-private static and takes a {@code @Nullable UUID};
 * {@code BoundVex.isPrey(ServerLevel, UUID, LivingEntity)} is public static.
 *
 * @param level the level
 * @param player the player
 * @param range the radius in blocks
 * @return the hostiles, in query order
 */
static List<LivingEntity> hostilesWithin(ServerLevel level, ServerPlayer player, double range) {
    return level.getEntitiesOfClass(
            LivingEntity.class,
            player.getBoundingBox().inflate(range),
            other -> other.isAlive()
                    && other.distanceTo(player) <= range
                    && BoundVex.huntable(player.getUUID(), other)
                    && BoundVex.isPrey(level, player.getUUID(), other));
}

/** Whether a live ghost is already helping {@code player}. */
private static boolean helping(ServerLevel level, ServerPlayer player) {
    return !level.getEntities(
                    EntityTypeTest.forClass(VillagerGhost.class),
                    ghost -> ghost.isAlive() && ghost.helpedId().filter(player.getUUID()::equals).isPresent())
            .isEmpty();
}

/** Whether a live ghost is already out for the record {@code recordId}. */
private static boolean hasLiveGhost(ServerLevel level, UUID recordId) {
    return !level.getEntities(
                    EntityTypeTest.forClass(VillagerGhost.class),
                    ghost -> ghost.isAlive() && ghost.recordId().filter(recordId::equals).isPresent())
            .isEmpty();
}

/**
 * Sends a ghost out from {@code record} to {@code player}. The order matters: position the ghost first, then bind
 * it (its Ghostrail probes the block at its position for a start spot; an unpositioned entity sits at (0, 0, 0) and
 * the probe would load that chunk), then add it to the level.
 */
private static void spawn(ServerLevel level, Record record, ServerPlayer player) {
    VillagerGhost ghost = ModEntities.VILLAGER_GHOST.get().create(level, EntitySpawnReason.MOB_SUMMONED);
    if (ghost == null) {
        return;
    }
    ghost.snapTo(record.pos().getX() + 0.5, record.pos().getY(), record.pos().getZ() + 0.5, 0.0F, 0.0F);
    ghost.bind(record, player);
    level.addFreshEntity(ghost);
}
```

(The three scans `helping`, `hasLiveGhost` and the hostile query are the only world queries of a step; `ServerLevel.getEntities(EntityTypeTest, Predicate)` walks every entity of the level, which is why the cheap player and need checks come first and a player who is neither hurt nor near an enemy costs one box query every 20 ticks.)

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:villager_ghost_*"`, `"-PgameTests=spellcraft:ghostrail_*"`. Expected: PASS. In the as-built note, record the one divergence from the spec: the ghost moves by `setPos`, not by velocity.

- [x] **Step 5: Gates and commit**

Run: `./gradlew spotlessApply build`, `./gradlew runGameTestServer` twice. Expected: `All 782 required tests passed` both times (the Task 11 total plus the 15 `villager_ghost_*` tests registered in this task).

```bash
git add src
git commit -m "feat(evoking): villager ghosts that come to a player in need and cast for them"
```

> **As built (`4a481bf`):** diverged — 788 GameTests (plan 782 + offset 3 + 3 added in review): the reach test moved out of HELP into its own environment, and the plan's optional boundary pair was built as 46 and 50 blocks (tighter than its 'about 40 / about 56'), each alone in its own environment with the record's chunk force-loaded and the unforce left to the runner (an unforce from a whenDone listener hung a full run, see api-notes §26); a creative-in-the-wind-up test added; step scans for live ghosts once and only when a loaded record is in range; reason: review fix round 1 and its re-review minors, re-review approved

### Task 13: The Caster's look: the robe, the arms-up pose, the casting particles

**Files:**

- Create: `scripts/robe_texture.py`, `scripts/test_robe_texture.py`, `assets/spellcraft/textures/entity/bound_vex_robe.png` (generated and committed), `client/evoking/BoundVexModel.java`, `BoundVexRobeLayer.java`, `CastLook.java`
- Modify: `client/evoking/BoundVexRenderer.java`, `BoundVexRenderState.java`, `CastingState.java`, `CastingParticles.java`, `src/test/…/SpellAssetsTest.java`

**Interfaces:**

- Consumes: Task 3 (`ModEffects.CASTING_SCROLL`, the casting effect, and `ModParticles.CASTING_SCROLL`), Task 8 (`BoundVex.isCaster`), `EvokerPose.armRotation/handOffset/HAND_HEIGHT`.
- Produces:
  - `CastLook { FANGS, VEXES, SCROLL }`; `CastingState.of(LivingEntity)` returns `Optional<CastLook>` (it used to return `Optional<EvokerSpell>`, whose ordinals are the packet's `CAST` argument and so are not extended); `EvokingArmPose` only calls `.isPresent()` and needs no edit.
  - `BoundVexRenderState.caster` and `.casting` (public booleans).
  - `BoundVexModel extends VexModel`: `setupAnim` raises both arms with `EvokerPose.armRotation` while `state.casting`.
  - `BoundVexRobeLayer extends RenderLayer<VexRenderState, VexModel>`.
  - `CastingParticles` draws the two hand particles for every living entity in render range whose `CastingState` is present, scaled by the entity's size (`caster.getScale() * caster.getType().getHeight() / 1.8F`: `EntityType.getHeight()` is the standing height, `EntityType.java:330`, so the scale equals `getScale()` for a player in any pose, exactly as Part 5 did, and is right for a Vex (0.8) and a villager ghost (1.95); `getBbHeight()` is pose dependent: 1.5 sneaking, 0.6 swimming, 0.2 sleeping); the third color (`SCROLL`) is purple `#B24CFF`; a null provider for `ModParticles.CASTING_SCROLL` and the hidden-HUD extension for `ModEffects.CASTING_SCROLL`.

The client code has no GameTest (the client classes never load on the test server). `./gradlew runData` boots the client distribution headlessly, but it only subscribes the `RegisterRenderers` listeners and never fires the event (`docs/api-notes.md:883-889`), so no renderer, layer, model or particle provider is constructed and no texture is resolved: for the client path this task's gate proves **compilation only**. The first render (stand-in creation, `ModelLayers`, the robe texture path, the particle provider) is Will's playtest (Task 15 has an item: launch the client, summon a Mirror image, a Caster and a ghost: no crash, a clean log). Javadoc omitted from a snippet below (constructors, overrides, private fields and constants) is still required in the code you commit.

- [x] **Step 1: Write the failing tests**

`scripts/test_robe_texture.py` (stdlib `unittest`, like `test_spell_icons.py`; `decode` below needs no helper from another test module):

```python
"""Tests for robe_texture.py. Stdlib only. Run: python -m unittest discover -s scripts -p "test_*.py" """

import struct
import unittest
import zlib

import robe_texture as robe


def decode(png: bytes) -> tuple[int, int, dict[tuple[int, int], tuple[int, int, int, int]]]:
    """Return (width, height, pixels) of an 8-bit RGBA PNG written by encode_png (filter 0, one IDAT)."""
    pos, idat, size = 8, b"", (0, 0)
    while pos < len(png):
        (length,) = struct.unpack(">I", png[pos : pos + 4])
        kind, data = png[pos + 4 : pos + 8], png[pos + 8 : pos + 8 + length]
        if kind == b"IHDR":
            size = struct.unpack(">II", data[:8])
        elif kind == b"IDAT":
            idat += data
        pos += 12 + length
    width, height = size
    raw = zlib.decompress(idat)
    pixels = {}
    for y in range(height):
        row = raw[y * (1 + 4 * width) + 1 : (y + 1) * (1 + 4 * width)]
        for x in range(width):
            pixels[(x, y)] = tuple(row[4 * x : 4 * x + 4])
    return width, height, pixels


class RobeTextureTest(unittest.TestCase):
    def test_r1_the_texture_is_the_vex_textures_size(self):
        """When the robe is generated, png should be a 32 x 32 image (the size of VexModel's layer).

        Function under test: :func:`robe_texture.png`
        """
        width, height, _ = decode(robe.png())
        self.assertEqual((width, height), (32, 32))

    def test_r2_the_head_and_the_wings_are_transparent(self):
        """When the robe is generated, png should leave the head's and the wings' regions fully transparent.

        Function under test: :func:`robe_texture.png`
        """
        _, _, pixels = decode(robe.png())
        regions = [(0, 0, 20, 10), (16, 22, 32, 27)]  # the head's UV block; the wing's two faces
        for x0, y0, x1, y1 in regions:
            for y in range(y0, y1):
                for x in range(x0, x1):
                    with self.subTest(x=x, y=y):
                        self.assertEqual(pixels[(x, y)][3], 0)

    def test_r3_the_body_front_is_purple_with_magenta_trim(self):
        """When the robe is generated, png should paint the body's front purple with a magenta top row.

        Function under test: :func:`robe_texture.png`
        """
        _, _, pixels = decode(robe.png())
        self.assertEqual(pixels[(3, 12)], robe.MAGENTA)  # first row of the front face of the upper body cube
        self.assertEqual(pixels[(3, 13)], robe.PURPLE)

    def test_r4_the_arms_are_painted(self):
        """When the robe is generated, png should paint both arms' front faces.

        Function under test: :func:`robe_texture.png`
        """
        _, _, pixels = decode(robe.png())
        self.assertEqual(pixels[(25, 4)][3], 255)  # right arm: front face at (25, 2), third row of four
        self.assertEqual(pixels[(25, 10)][3], 255)  # left arm: front face at (25, 8), third row of four

    def test_r5_the_lower_edge_is_ragged(self):
        """When the robe is generated, png should cut transparent notches into the lower body's last row.

        Function under test: :func:`robe_texture.png`
        """
        _, _, pixels = decode(robe.png())
        last_row = [pixels[(x, 22)][3] for x in range(2, 5)]  # front face of the lower cube, its last row
        self.assertIn(0, last_row)
        self.assertIn(255, last_row)

    def test_r6_only_the_two_robe_colors_are_used_and_the_output_is_stable(self):
        """When the robe is generated twice, png should give the same bytes and only purple, magenta or clear.

        Function under test: :func:`robe_texture.png`
        """
        first, second = robe.png(), robe.png()
        self.assertEqual(first, second)
        _, _, pixels = decode(first)
        self.assertTrue(set(pixels.values()) <= {robe.PURPLE, robe.MAGENTA, robe.CLEAR})

    def test_r7_the_committed_file_is_what_the_generator_writes(self):
        """When the robe is generated, png should equal the committed bound_vex_robe.png byte for byte.

        Function under test: :func:`robe_texture.png`
        """
        self.assertEqual(robe.OUT.read_bytes(), robe.png())
```

(`encode_png` writes filter byte 0 per row and one IDAT, so `decode` above reads it back; keep `decode` as shown. `test_r7` is the robe's twin of `test_spell_icons.test_i5`: a stale texture cannot be committed.)

`SpellAssetsTest`: add

```java
/**
 * When the robe texture is read, should it be a 32 x 32 PNG: a different size would mis-map the vex model's UV layout.
 *
 * <p>Method under test: the robe texture file
 */
@Test
void robeTextureIs32x32() throws IOException {
    assertSquarePng(ASSETS.resolve("textures/entity/bound_vex_robe.png"), 32);
}
```

- [x] **Step 2: Run the tests to verify they fail**

Run: `python -m unittest discover -s scripts -p "test_*.py"` (FAIL: no `robe_texture`) and `./gradlew test --tests "*SpellAssetsTest"` (FAIL: no file).

- [x] **Step 3: Write the implementation**

`scripts/robe_texture.py` (reuse `spell_icons.encode_png`, which writes any square 8-bit RGBA PNG):

```python
"""Write the Caster's robe texture: 32 x 32, laid out like the vex texture. Stdlib only.

Only the body's and the arms' faces are painted (purple #5B2A86, magenta #C03FA8 trim, a ragged transparent lower
edge); the head and the wings stay transparent, so the layer that draws the model again with this texture dresses
only the body. The cubes' UV blocks follow VexModel.createBodyLayer (the cube at texOffs (u, v) with width w, height h
and depth d has its top at (u + d, v), its bottom at (u + d + w, v), and, from row v + d down, its right side at u, its
front at u + d, its left side at u + d + w and its back at u + 2d + w).

Original art: nothing is copied from Minecraft's textures.

Usage: python scripts/robe_texture.py      (writes into src/main/resources/assets/spellcraft/textures/entity)
"""

from pathlib import Path

from spell_icons import encode_png

Pixel = tuple[int, int, int, int]

SIZE = 32
CLEAR: Pixel = (0, 0, 0, 0)
PURPLE: Pixel = (0x5B, 0x2A, 0x86, 255)
MAGENTA: Pixel = (0xC0, 0x3F, 0xA8, 255)
OUT = (
    Path(__file__).resolve().parent.parent
    / "src/main/resources/assets/spellcraft/textures/entity/bound_vex_robe.png"
)

# (u, v, width, height, depth) of the four painted cubes in VexModel: upper body, lower body, right arm, left arm.
CUBES = ((0, 10, 3, 4, 2), (0, 16, 3, 5, 2), (23, 0, 2, 4, 2), (23, 6, 2, 4, 2))


def side_faces(cube: tuple[int, int, int, int, int]) -> list[tuple[int, int, int, int]]:
    """Return the four side faces (x, y, width, height) of a cube's UV block."""
    u, v, w, h, d = cube
    return [(u, v + d, d, h), (u + d, v + d, w, h), (u + d + w, v + d, d, h), (u + 2 * d + w, v + d, w, h)]


def cap_faces(cube: tuple[int, int, int, int, int]) -> list[tuple[int, int, int, int]]:
    """Return the top and bottom faces (x, y, width, height) of a cube's UV block."""
    u, v, w, _, d = cube
    return [(u + d, v, w, d), (u + d + w, v, w, d)]


def pixels() -> dict[tuple[int, int], Pixel]:
    """Return the painted pixels: purple faces, a magenta first row on each side face, notches on the hem."""
    painted: dict[tuple[int, int], Pixel] = {}
    for index, cube in enumerate(CUBES):
        for x0, y0, w, h in cap_faces(cube):
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    painted[(x, y)] = PURPLE
        for x0, y0, w, h in side_faces(cube):
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    painted[(x, y)] = MAGENTA if y == y0 else PURPLE
                    if index == 1 and y == y0 + h - 1 and (x + 2 * y) % 3 == 0:
                        del painted[(x, y)]  # the ragged hem of the lower body
    return painted


def png() -> bytes:
    """Return the robe texture as PNG bytes."""
    painted = pixels()
    return encode_png(SIZE, lambda x, y: painted.get((x, y), CLEAR))


def main() -> None:
    """Write the texture."""
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(png())


if __name__ == "__main__":
    main()
```

Run `python scripts/robe_texture.py` and the Python tests (expected PASS); open the PNG with the Read tool to check it is a purple body strip with trim. If `test_r5` fails because no pixel of the last row is cleared by the `(x + 2 * y) % 3` pattern, change the pattern constants, not the test.

`CastLook`:

```java
/**
 * What an entity is casting, for the casting look (Parts 6-7 spec §6.4); client-only, separate from the packet's
 * {@code EvokerSpell}.
 */
public enum CastLook {
    /** A fang cast: the evoker's dark fang color. */
    FANGS,
    /** A vex summon: the evoker's pale vex color. */
    VEXES,
    /** A scroll cast by a Caster or a villager ghost: purple. */
    SCROLL
}
```

`CastingState.of`:

```java
public static Optional<CastLook> of(LivingEntity entity) {
    List<ParticleOptions> particles = entity.getEntityData().get(LivingEntity.DATA_EFFECT_PARTICLES);
    if (particles.contains(ModParticles.CASTING_FANGS.get())) {
        return Optional.of(CastLook.FANGS);
    }
    if (particles.contains(ModParticles.CASTING_VEXES.get())) {
        return Optional.of(CastLook.VEXES);
    }
    return particles.contains(ModParticles.CASTING_SCROLL.get()) ? Optional.of(CastLook.SCROLL) : Optional.empty();
}
```

(and the class Javadoc's "which spell" becomes "which look".) `CastingParticles`: add `private static final ColorParticleOption SCROLL_COLOR = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFFB24CFF);`; `onRegisterProviders` also registers `ModParticles.CASTING_SCROLL` with the same null provider; `onRegisterExtensions` adds `ModEffects.CASTING_SCROLL` to the hidden list; the tick handler and the hand drawing become:

```java
for (Entity entity : level.entitiesForRendering()) {
    if (entity instanceof LivingEntity living) {
        CastingState.of(living).ifPresent(look -> handParticles(level, living, look));
    }
}

/**
 * The evoker's two particles, one at each hand, following the body and swaying with the tick, scaled to the entity.
 * The scale is the standing size, not the current box: a sneaking or swimming caster keeps Part 5's particles.
 */
private static void handParticles(ClientLevel level, LivingEntity caster, CastLook look) {
    ColorParticleOption color = switch (look) {
        case FANGS -> FANGS_COLOR;
        case VEXES -> VEXES_COLOR;
        case SCROLL -> SCROLL_COLOR;
    };
    float size = caster.getScale() * caster.getType().getHeight() / PLAYER_HEIGHT;
    EvokerPose.HandOffset hand = EvokerPose.handOffset(caster.yBodyRot, caster.tickCount);
    double y = caster.getY() + EvokerPose.HAND_HEIGHT * size;
    level.addParticle(color, caster.getX() + hand.dx() * size, y, caster.getZ() + hand.dz() * size, 0, 0, 0);
    level.addParticle(color, caster.getX() - hand.dx() * size, y, caster.getZ() - hand.dz() * size, 0, 0, 0);
}
```

with `private static final float PLAYER_HEIGHT = 1.8F;` (a player is 1.8 blocks tall: `EvokerPose.HAND_HEIGHT` is for a player; give it a one-line Javadoc). The class Javadoc's "every casting player" becomes "every casting living entity in view".

`BoundVexRenderState` adds:

```java
/** Whether the vex is a Caster: the robe layer draws the robe. */
public boolean caster;

/** Whether the vex is casting (the casting effect is on): {@code BoundVexModel} raises both arms. */
public boolean casting;
```

`BoundVexModel`:

```java
/** The vex model with the arms-up casting pose of a Caster (Parts 6-7 spec §6.4). */
public final class BoundVexModel extends VexModel {
    /**
     * Creates the model from its baked layer.
     *
     * @param root the baked {@code ModelLayers.VEX} root
     */
    public BoundVexModel(ModelPart root) {
        super(root);
    }

    /** Vanilla's pose, then, while casting, both arms raised as {@link EvokerPose#armRotation} says. */
    @Override
    public void setupAnim(VexRenderState state) {
        super.setupAnim(state);
        if (state instanceof BoundVexRenderState vex && vex.casting) {
            ModelPart body = root().getChild("body");
            raise(body.getChild("right_arm"), EvokerPose.armRotation(state.ageInTicks, true));
            raise(body.getChild("left_arm"), EvokerPose.armRotation(state.ageInTicks, false));
        }
    }

    /** Sets all three rotations of {@code arm} from {@code rotation}. */
    private static void raise(ModelPart arm, EvokerPose.ArmRotation rotation) {
        arm.xRot = rotation.xRot();
        arm.yRot = rotation.yRot();
        arm.zRot = rotation.zRot();
    }
}
```

`BoundVexRobeLayer`:

```java
/**
 * Draws the vex model again with the robe texture for a Caster (Parts 6-7 spec §6.3), scaled up about the body and
 * drawn with {@code RenderTypes.entityCutoutZOffset} (26.3 has no {@code entityCutoutNoCull}; {@code entityCutout}
 * is the no-cull variant; the z-offset variant exists for exactly this overlay case). The scale alone moves the 2-pixel
 * body out by only about 0.06 pixel, too little to rule out z-fighting at range, so the z offset does that job and the
 * growth is for the look. The texture is transparent on the head and the wings, so only the body and the arms are
 * dressed. Both are first guesses for Will's eye (playtest checklist, Part 7).
 */
public final class BoundVexRobeLayer extends RenderLayer<VexRenderState, VexModel> {
    /** The robe texture, 32 x 32 like the vex's. */
    private static final Identifier ROBE = Spellcraft.id("textures/entity/bound_vex_robe.png");

    /** The robe is this much larger than the body, about the body's pivot. */
    private static final float GROWTH = 1.06F;

    /** The body's pivot height in blocks: the root's offset (-2.5 pixels) plus the body's (20), over 16. */
    private static final float BODY_PIVOT = 17.5F / 16.0F;

    /**
     * Creates the layer for one renderer.
     *
     * @param renderer the vex renderer it dresses
     */
    public BoundVexRobeLayer(RenderLayerParent<VexRenderState, VexModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(
            PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, VexRenderState state, float yRot, float xRot) {
        if (!(state instanceof BoundVexRenderState vex) || !vex.caster || state.isInvisible) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.0F, BODY_PIVOT, 0.0F);
        poseStack.scale(GROWTH, GROWTH, GROWTH);
        poseStack.translate(0.0F, -BODY_PIVOT, 0.0F);
        collector.submitModel(
                getParentModel(),
                state,
                poseStack,
                RenderTypes.entityCutoutZOffset(ROBE),
                lightCoords,
                LivingEntityRenderer.getOverlayCoords(state, 0.0F),
                -1,
                null,
                state.outlineColor);
        poseStack.popPose();
    }
}
```

`BoundVexRenderer`: constructor

```java
super(context);
this.model = new BoundVexModel(context.bakeLayer(ModelLayers.VEX));
addLayer(new BoundVexRobeLayer(this));
addLayer(new BoundVexArmorLayer(this, context));
```

(`LivingEntityRenderer.model` is protected and not final; `VexRenderer`'s constructor built a vanilla model first and that one is simply dropped. Check `ItemInHandLayer` reads `getParentModel()` at render time, so it uses the new model.) In `extractRenderState`, inside the `BoundVexRenderState` branch:

```java
gear.caster = entity instanceof BoundVex vex && vex.isCaster();
gear.casting = CastingState.of(entity).isPresent();
```

Update the class Javadocs (the renderer now draws the robe, the pose and the armor).

- [x] **Step 4: Run the tests to verify they pass**

Run: `python -m unittest discover -s scripts -p "test_*.py"`, `./gradlew test`, `./gradlew runData`. Expected: PASS. `runData` here proves that the client classes compile and the client distribution boots; it does not construct the renderer, the layer, the model or the particle provider, so a missing texture or a wrong layer name will not show in its log (Task 15's playtest item covers the first render).

- [x] **Step 5: Gates and commit**

Run: `./gradlew spotlessApply build`, `./gradlew runGameTestServer` twice. Expected: `All N required tests passed` both times, where N is the total Task 12 recorded (this task adds no GameTest).

```bash
git add src scripts
git commit -m "feat(evoking): a robe, arms-up pose and casting particles for Casters"
```

> **As built (`80c98ce`):** diverged — RED captured only as the missing-PNG failure (the robe script and its tests were written together; SpellAssetsTest.robeTextureIs32x32 added after the PNG existed); CastingParticles gained PLAYER_HEIGHT and SCROLL_COLOR constants; review minors (Javadoc on the casting colors and the robe layer) fixed in 80c98ce; client path proven to compile only, first render is Will's playtest; reason: implementer report, review approved

---

### Task 14: The look of images and ghosts

**Files:**

- Create: `client/evoking/MirrorImageRenderer.java`, `MirrorImageRenderState.java`, `VillagerGhostRenderer.java`, `VillagerGhostProfessionLayer.java`
- Modify: `client/evoking/BoundVexRenderers.java`

**Interfaces:**

- Consumes: Task 4 (`MirrorImage`: `copiedType()`, `profile()`), Task 12 (`VillagerGhost.villagerData()`), `ModEntities.MIRROR_IMAGE`, `ModEntities.VILLAGER_GHOST`.
- Produces: `MirrorImageRenderer` and `VillagerGhostRenderer` registered for their entity types; no new data, no packet.

As Task 13, no GameTest reaches client code, and `./gradlew runData` never fires `RegisterRenderers` (`docs/api-notes.md:883-889`): for this task it proves **compilation only**, and the first render of both renderers is Will's playtest (Task 15). Read the sources named in api-notes §26 before writing: every client call below is checked against `build/mc-src`. Javadoc omitted from a snippet below (constructors, overrides, private fields and constants) is still required in the code you commit.

- [x] **Step 1: Write the renderers**

`MirrorImageRenderState`:

```java
/**
 * The render state of a Mirror image: the stand-in's own render state, drawn by the dispatcher (Parts 6-7 spec §7.2).
 */
public final class MirrorImageRenderState extends EntityRenderState {
    /** The state of the stand-in for this frame, or null when no stand-in could be made. */
    public @Nullable EntityRenderState standIn;
}
```

`MirrorImageRenderer`:

```java
/**
 * Draws a Mirror image as the entity it copies (Parts 6-7 spec §7.2). Vanilla's renderers draw only the entity classes
 * they belong to, so the renderer keeps a client-side <b>stand-in</b> per image, created once and never added to the
 * level: a mannequin for a player, or a new entity of the copied type for a mob. Each frame the image's position,
 * rotations, hurt and death time, walk, pose and held items are copied into the stand-in, the dispatcher is asked for
 * the stand-in's render state, and {@code submit} hands that state back to the dispatcher. The stand-in is never
 * ticked (a ticking {@code LivingEntity} pushes nearby entities client-side), and the mannequin's own skin is only
 * applied inside {@code ClientMannequin.tick()}, so it would stay the default skin forever: the player's skin is put
 * into the extracted render state instead, from {@code PlayerSkinRenderCache.getOrDefault(profile)}, which answers the
 * resolved skin or the default on every call (the dispatcher picks the player renderer, wide or slim, from
 * {@code state.skin.model()}). Images carry the hands' items only, no armor (spec §7.2).
 */
public final class MirrorImageRenderer extends EntityRenderer<MirrorImage, MirrorImageRenderState> {
    /** The stand-in made for one image, and what it was made from. */
    private record StandIn(UUID image, EntityType<?> copiedType, @Nullable Entity entity) {
        /** Whether this stand-in still fits {@code image} (entity ids restart per world, so the UUID is checked). */
        boolean fits(MirrorImage image) {
            return this.image.equals(image.getUUID()) && copiedType.equals(image.copiedType());
        }
    }

    /**
     * One stand-in per image, dropped with the image (weak keys). {@code Entity.equals} compares ids, which restart
     * per server, so a not-yet-collected entry of a previous world could match a new image: every lookup checks
     * {@link StandIn#fits}, and a mismatch makes a new stand-in.
     */
    private final Map<MirrorImage, StandIn> standIns = new WeakHashMap<>();

    /**
     * Creates the renderer. The shadow radius stays 0: the nested {@code dispatcher.submit} draws the stand-in's own
     * shadow, and a second one here would stack two.
     *
     * @param context the renderer context
     */
    public MirrorImageRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public MirrorImageRenderState createRenderState() {
        return new MirrorImageRenderState();
    }

    @Override
    public void extractRenderState(MirrorImage image, MirrorImageRenderState state, float partialTicks) {
        super.extractRenderState(image, state, partialTicks);
        StandIn known = standIns.get(image);
        if (known == null || !known.fits(image)) {
            known = new StandIn(image.getUUID(), image.copiedType(), createStandIn(image));
            standIns.put(image, known);
        }
        Entity standIn = known.entity();
        if (standIn == null) {
            state.standIn = null;
            return;
        }
        copyPose(image, standIn);
        EntityRenderState inner = entityRenderDispatcher.extractEntity(standIn, partialTicks);
        inner.nameTag = null; // a mannequin's description label is not an image's ...
        // ... and neither is its below-name line (extractNameTags sets it from belowNameDisplay)
        inner.scoreText = null;
        if (inner instanceof AvatarRenderState avatar) {
            avatar.skin = Minecraft.getInstance().playerSkinRenderCache().getOrDefault(image.profile()).playerSkin();
        }
        state.standIn = inner;
    }

    /** A stand-in for {@code image}: a mannequin for a player, else a new entity of the copied type. */
    private @Nullable Entity createStandIn(MirrorImage image) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        if (image.copiedType().equals(EntityTypes.PLAYER)) {
            return EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.LOAD);
        }
        return image.copiedType().create(level, EntitySpawnReason.LOAD);
    }

    /**
     * Copies what the renderer reads: position, rotations and their previous values, hurt, death, walk, pose, hands.
     */
    private static void copyPose(MirrorImage image, Entity standIn) {
        standIn.setPos(image.position());
        standIn.xo = image.xo;
        standIn.yo = image.yo;
        standIn.zo = image.zo;
        standIn.xOld = image.xOld;
        standIn.yOld = image.yOld;
        standIn.zOld = image.zOld;
        standIn.setYRot(image.getYRot());
        standIn.setXRot(image.getXRot());
        standIn.yRotO = image.yRotO;
        standIn.xRotO = image.xRotO;
        standIn.setPose(image.getPose());
        if (standIn instanceof LivingEntity living) {
            living.yBodyRot = image.yBodyRot;
            living.yBodyRotO = image.yBodyRotO;
            living.yHeadRot = image.yHeadRot;
            living.yHeadRotO = image.yHeadRotO;
            living.hurtTime = image.hurtTime;
            living.hurtDuration = image.hurtDuration;
            living.deathTime = image.deathTime;
            living.setItemSlot(EquipmentSlot.MAINHAND, image.getMainHandItem());
            living.setItemSlot(EquipmentSlot.OFFHAND, image.getOffhandItem());
            // The walk animation advances once per client tick, not once per frame: the stand-in's tick count is the
            // marker. It smooths an already smoothed speed, so the stand-in's legs lag the image slightly (playtest).
            if (standIn.tickCount != image.tickCount) {
                standIn.tickCount = image.tickCount;
                living.walkAnimation.update(image.walkAnimation.speed(), 0.4F, 1.0F);
            }
        }
    }

    @Override
    public void submit(
            MirrorImageRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.standIn != null) {
            // The pose stack is already at the image; the dispatcher adds the renderer's own offset
            entityRenderDispatcher.submit(state.standIn, camera, 0.0, 0.0, 0.0, poseStack, collector);
        }
    }
}
```

(Already checked against the 26.3 sources, so no deferred "checks to make" remain: `hurtTime`, `hurtDuration`, `deathTime`, `yBodyRotO`, `yHeadRotO`, `walkAnimation`, `xOld/yOld/zOld`, `xo/yo/zo` and `tickCount` are all public (`LivingEntity.java:239-249`, `Entity.java:225-255`), so no access transformer is needed; `EntityType.create(Level, EntitySpawnReason)` exists (`EntityType.java:343`) and `EntityTypes.MANNEQUIN` and `EntityTypes.PLAYER` exist; `EntityRenderState.nameTag` and `scoreText` are both public fields (`EntityRenderState.java:33-34`). If the extracted state of a mannequin turns out not to be an `AvatarRenderState`, read `EntityRenderDispatcher.getRenderer(S)` before changing the skin line.)

`VillagerGhostRenderer`:

```java
/**
 * Draws a villager ghost (Parts 6-7 spec §9): the villager model with the base villager texture, translucent and tinted
 * pale blue (alpha 0.6), plus a copy of vanilla's profession layer that draws the type, profession and level textures
 * with the same tint. Vanilla's {@code VillagerRenderer} needs a {@code Villager} and the profession layer's helpers
 * are private and draw with {@code entityCutout}, which ignores alpha; hence the renderer and the layer are this mod's
 * own. The villager model's arms are one crossed part, so a ghost shows particles and sounds only when it casts.
 */
public final class VillagerGhostRenderer extends LivingEntityRenderer<VillagerGhost, VillagerRenderState, VillagerModel> {
    /** The base villager texture. */
    private static final Identifier BASE = Identifier.withDefaultNamespace("textures/entity/villager/villager.png");

    /** Pale blue at alpha 0.6, as ARGB (a first guess for Will's eye). */
    static final int TINT = 0x99DDE6FF;

    /**
     * Creates the renderer: the villager model and the profession layer's no-hat model.
     *
     * @param context the renderer context
     */
    public VillagerGhostRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
        addLayer(new VillagerGhostProfessionLayer(
                this, context.getResourceManager(), new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER_NO_HAT))));
    }

    @Override
    public Identifier getTextureLocation(VillagerRenderState state) {
        return BASE;
    }

    @Override
    public VillagerRenderState createRenderState() {
        return new VillagerRenderState();
    }

    @Override
    public void extractRenderState(VillagerGhost ghost, VillagerRenderState state, float partialTicks) {
        super.extractRenderState(ghost, state, partialTicks);
        state.villagerData = ghost.villagerData();
        state.isUnhappy = false;
    }

    /** Translucent, so the tint's alpha shows (vanilla's cutout type would ignore it). */
    @Override
    protected @Nullable RenderType getRenderType(
            VillagerRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
        return isBodyVisible ? RenderTypes.entityTranslucent(getTextureLocation(state)) : null;
    }

    @Override
    protected int getModelTint(VillagerRenderState state) {
        return TINT;
    }
}
```

`VillagerGhostProfessionLayer` is a copy of vanilla's `VillagerProfessionLayer` (read it in `build/mc-src/net/minecraft/client/renderer/entity/layers/VillagerProfessionLayer.java`, api-notes §26) without the baby branch, drawing translucent and tinted:

```java
/** Draws the type, profession and level textures of a ghost translucent and tinted (vanilla's layer draws cutout). */
public final class VillagerGhostProfessionLayer extends RenderLayer<VillagerRenderState, VillagerModel> {
    private static final Map<Integer, Identifier> LEVEL_LOCATIONS = Map.of(
            1, Identifier.withDefaultNamespace("stone"),
            2, Identifier.withDefaultNamespace("iron"),
            3, Identifier.withDefaultNamespace("gold"),
            4, Identifier.withDefaultNamespace("emerald"),
            5, Identifier.withDefaultNamespace("diamond"));

    /** The hat metadata of the type textures read so far. */
    private final Map<ResourceKey<VillagerType>, VillagerMetadataSection.Hat> typeHats = new HashMap<>();

    /** The hat metadata of the profession textures read so far. */
    private final Map<ResourceKey<VillagerProfession>, VillagerMetadataSection.Hat> professionHats = new HashMap<>();

    /** Where the texture metadata is read from. */
    private final ResourceManager resourceManager;

    /** The model drawn for the type texture when a profession's hat covers the type's. */
    private final VillagerModel noHatModel;

    /**
     * Creates the layer for one renderer.
     *
     * @param renderer the ghost renderer it dresses
     * @param resourceManager where the hat metadata comes from
     * @param noHatModel the baked no-hat villager model
     */
    public VillagerGhostProfessionLayer(
            RenderLayerParent<VillagerRenderState, VillagerModel> renderer,
            ResourceManager resourceManager,
            VillagerModel noHatModel) {
        super(renderer);
        this.resourceManager = resourceManager;
        this.noHatModel = noHatModel;
    }

    @Override
    public void submit(
            PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, VillagerRenderState state, float yRot, float xRot) {
        VillagerData data = state.getVillagerData();
        if (state.isInvisible || data == null) {
            return;
        }
        Holder<VillagerType> type = data.type();
        Holder<VillagerProfession> profession = data.profession();
        VillagerMetadataSection.Hat typeHat = hat(typeHats, "type", type);
        VillagerMetadataSection.Hat professionHat = hat(professionHats, "profession", profession);
        VillagerModel model = getParentModel();
        boolean typeHatVisible = professionHat == VillagerMetadataSection.Hat.NONE
                || (professionHat == VillagerMetadataSection.Hat.PARTIAL && typeHat != VillagerMetadataSection.Hat.FULL);
        draw(typeHatVisible ? model : noHatModel, identifier("type", type), poseStack, collector, lightCoords, state, 1);
        if (!profession.is(VillagerProfession.NONE)) {
            draw(model, identifier("profession", profession), poseStack, collector, lightCoords, state, 2);
            if (!profession.is(VillagerProfession.NITWIT)) {
                Identifier level = LEVEL_LOCATIONS.get(Mth.clamp(data.level(), 1, LEVEL_LOCATIONS.size()));
                draw(model, identifier("profession_level", level), poseStack, collector, lightCoords, state, 3);
            }
        }
    }

    /** One texture, translucent, in the ghost's tint, in the draw order {@code order}. */
    private static void draw(
            VillagerModel model, Identifier texture, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
            VillagerRenderState state, int order) {
        collector.order(order)
                .submitModel(
                        model,
                        state,
                        poseStack,
                        RenderTypes.entityTranslucent(texture),
                        lightCoords,
                        LivingEntityRenderer.getOverlayCoords(state, 0.0F),
                        VillagerGhostRenderer.TINT,
                        null,
                        state.outlineColor);
    }

    /** The texture path of a key: {@code textures/entity/villager/<kind>/<path>.png}. */
    private static Identifier identifier(String kind, Identifier key) {
        return key.withPath(path -> "textures/entity/villager/" + kind + "/" + path + ".png");
    }

    /** The texture path of a registry holder, or the missing-texture location for an unbound holder. */
    private static Identifier identifier(String kind, Holder<?> holder) {
        return holder.unwrapKey()
                .map(key -> identifier(kind, key.identifier()))
                .orElse(MissingTextureAtlasSprite.getLocation());
    }

    /** The hat metadata of a type or profession texture ({@code NONE} when it has none), read once and cached. */
    private <K> VillagerMetadataSection.Hat hat(
            Map<ResourceKey<K>, VillagerMetadataSection.Hat> cache, String kind, Holder<K> holder) {
        ResourceKey<K> key = holder.unwrapKey().orElse(null);
        if (key == null) {
            return VillagerMetadataSection.Hat.NONE;
        }
        return cache.computeIfAbsent(key, k -> resourceManager
                .getResource(identifier(kind, key.identifier()))
                .flatMap(resource -> {
                    try {
                        return resource.metadata().getSection(VillagerMetadataSection.TYPE).map(VillagerMetadataSection::hat);
                    } catch (IOException e) {
                        return Optional.empty();
                    }
                })
                .orElse(VillagerMetadataSection.Hat.NONE));
    }
}
```

(Check each call against 26.3's `VillagerProfessionLayer` lines 54-122; if the vanilla version differs, the vanilla version wins.)

`BoundVexRenderers.onRegisterRenderers` registers the two new renderers beside the vex (update the class Javadoc):

```java
event.registerEntityRenderer(ModEntities.MIRROR_IMAGE.get(), MirrorImageRenderer::new);
event.registerEntityRenderer(ModEntities.VILLAGER_GHOST.get(), VillagerGhostRenderer::new);
```

- [x] **Step 2: Run the checks**

Run: `./gradlew spotlessApply build`, `./gradlew runData`. Expected: PASS. This proves compilation and that the client distribution boots, nothing more: `runData` does not construct the renderers (api-notes §26), so a `NoSuchFieldError`, a missing model layer or a missing texture surfaces only at the first render, in Will's playtest (Task 15). Read the model-layer and texture names against `build/mc-src` once more before committing.

- [x] **Step 3: Commit**

Run `./gradlew runGameTestServer` twice first (expected `All N required tests passed`, where N is the total Task 12 recorded: this task adds no GameTest).

```bash
git add src
git commit -m "feat(evoking): renderers for Mirror images and villager ghosts"
```

> **As built (`be07a02`):** matches plan.

---

### Task 15: Docs, playtest items, mutation testing

**Files:**

- Modify: `README.md`, `CLAUDE.md`, `src/main/templates/META-INF/neoforge.mods.toml`, `docs/playtest-checklist.md`, `docs/mutation-testing.md`, `docs/api-notes.md` (§26 corrections), `docs/superpowers/specs/2026-11-07-evoking-ii-design.md` (inline notes), `docs/superpowers/specs/2026-11-10-evoking-ii-parts-6-7-design.md` (as-built sentences)
- Modify: `src/test/…/spell/rules/*Test.java` (the tests Step 5 adds to kill surviving mutants)

- [x] **Step 1: README**

In the opening line change "It has four:" to "It has six:". After the Strength of Evoking bullet's Team moves paragraph (the indented "- **Team moves.**" block), add, in the README's own voice, a block for the Part 7 additions: **Casters** (one Bound Vex in three is a Caster: it wears a torn purple robe, never fights, keeps four to six blocks from its target, and casts Mirror or Ghostrail on you, Ghostrail first when you are in trouble; it joins a Raid only to cast Mirror and hold its place), **scroll mobs** (evokers one time in four, vindicators and pillagers one in ten, spawn with a Mirror or Ghostrail scroll in the offhand and use it on themselves when they get a target; kill one with a player's attack and it may drop the scroll, Looting helps), and **villager ghosts** (a villager a monster kills leaves a ghost: when you have Strength of Evoking and are hurt below half health or have three monsters close, the nearest ghost within 48 blocks of a death floats to you, casts the spells on you and fades when the fight is over; one ghost at a time; killed by lava, a fall or your own side, a villager leaves none). Add two bullets to the spell list, with their icons, in the style of the others, after the Evoking bullet:

```markdown
- <img src="src/main/resources/assets/spellcraft/textures/item/mirror_scroll.png" alt="" width="24" height="24"> **Strength of Mirror**: for six minutes (not configurable) thirteen copies of you stand around you
  and wander near you. A monster that picks you as its target almost always (13 times in 14) picks a copy instead; any mob is
  fooled, a golem that goes for an evoker with copies included, but an iron golem never goes for a player's copies. The copies are
  your allies, have 10 health, drop nothing and give no experience, and end with the effect (the last one dying ends it too). A
  scroll cast while it is active is refused; sneak and use the scroll to dispel it.
- <img src="src/main/resources/assets/spellcraft/textures/item/ghostrail_scroll.png" alt="" width="24" height="24"> **Strength of Ghostrail**: for four minutes (not configurable) you fly through blocks, unhurt (only `/kill` and the
  void still hurt), leaving a trail of white smoke. When it ends inside blocks you are moved to the nearest free spot within eight blocks
  (the spot you started at, if there is none), with no fall damage. Creative players cannot use the scroll; sneak and use the scroll to end it.
```

and the last paragraph's list `(upside_down, infinity, trailing or evoking)` becomes `(upside_down, infinity, trailing, evoking, mirror or ghostrail)`.

- [x] **Step 2: Mod description and CLAUDE.md**

`neoforge.mods.toml`: the description line becomes `Spells for Minecraft. Spells so far: Strength of Upside-Down, Strength of Infinity, Strength of Trailing, Strength of Evoking (whose Bound Vexes fight as a team and cast), Strength of Mirror and Strength of Ghostrail.` `CLAUDE.md`: in the Rules bullet "Mixins only where no event exists", append one sentence naming the one Part 7 Mixin: "`PlayerGhostrailMixin` (Ghostrail's `noPhysics`) is the one Mixin Part 7 adds; the Part 6 packet rule is unchanged: Part 7 adds no packet." (CLAUDE.md has no Mixin list today; the spec's "the Mixin list gains the Ghostrail Mixin" is met by this sentence.) Add a row to the Commands table, after the `python scripts/spell_icons.py` row: `` `python scripts/robe_texture.py` | Regenerate the Caster's robe texture (`bound_vex_robe.png`) ``. Correct the `./gradlew runData` row: it loads client classes and config specs and proves the `ArmPose` enum wiring, but it never fires `RegisterRenderers` (api-notes, the `runData` finding), so no renderer, layer, model or particle provider is constructed and no texture is resolved; say so in one clause, so nobody reads a green `runData` as proof of the renderers.

- [x] **Step 3: Playtest checklist**

Add a section "Evoking II (Part 7)" to `docs/playtest-checklist.md`, after "Evoking II (Part 6)" and before "Real profile". One line of setup (survival world, Strength of Evoking on, three Bound Vexes out; `/give @s spellcraft:mirror_scroll` and `ghostrail_scroll`; `/summon evoker` and `/summon zombie` for fights), then `- [ ]` bullets (each is Will's to check; none is claimed working):

- The scroll icons: Mirror (two facing figures, magenta) and Ghostrail (an arrow through a wall, pale blue) read at 16 x 16 and as effect icons.
- First launch of the client after Part 7 (nothing automated constructs the new renderers): summon a Mirror image (cast the scroll), a Caster (`/summon` plus the V key until one rolls) and a villager ghost (kill a villager with a zombie, then get hurt): no crash, a clean log (no missing texture, model layer or field errors) on the first render of each.
- Casters: one vex in three wears a torn purple robe. Does it look like a robe (the 1.06 growth and the z-offset render type: no flicker against the body at range; head and wings bare)? Is the trim readable?
- A Caster with a target keeps its distance and never attacks; with two zombies near and you beside it, it raises both arms, purple particles rise from its hands, the sounds play, a Mirror scroll shows in its hand for a second, then you have Mirror. A hurt you (below a quarter health, or in a wall) gets Ghostrail first.
- A Caster in a Raid: it casts Mirror at the start of the gather without the wind-up, then holds its place in the grid and does nothing else.
- Mirror: do the thirteen images look like you (your skin from the first frame, slim or wide as yours is, the right size, your held items; no armor: an armored player gets unarmored images, by design)? No "Mannequin" label above or below an image, and one shadow under it, not two stacked? Do the legs walk in step, or lag the walk slightly (the walk animation is smoothed twice)? Do they wander without piling up or walking into walls, flash and fall back when hit, vanish in a puff at the end and when you milk the effect, and when the carrier dies (even on the death screen)?
- Mirror and a dimension change: step through a portal with Mirror on; the images stay behind in the old dimension and the effect stays on (accepted: they poof when their own six minutes end). Is that acceptable, or should the portal poof them?
- Mirror in a real fight: do zombies, skeletons, spiders and creepers go for copies about 13 times in 14? Does a mob that keeps the same target for a long time keep going for the copy (the same-target re-set must not re-roll and drag it back to you; no GameTest drives a real target goal)? Does a pillager crossbow or a creeper still reach you? Are brain mobs (piglins, villagers, the Warden) unfooled, as accepted?
- A mob's Mirror (an evoker with a scroll): the images look like evokers; a golem going for the evoker goes for an image.
- Ghostrail and blocks: a carrier has no collision, so it also triggers no block effects: walk (fly) through a nether portal frame, a pressure plate, cobweb, honey and powder snow and see that nothing reacts. Is that fine?
- Ghostrail: fly through a hill and walls, no rubber-banding; the white-smoke trail; fall damage and mob hits do nothing; the end inside a hill moves you out (where, is it the nearest air?), and deep inside stone with nothing within eight blocks it puts you back where you started or leaves you suffocating (accepted).
- Ghostrail with Infinity on: ending Ghostrail keeps Infinity's flight; ending Infinity keeps Ghostrail's.
- A pillager or evoker with a Ghostrail scroll comes straight at you through a wall for two seconds. Does it look right? Does it stay put behind the wall afterwards?
- Scroll mobs: how often a raid party carries scrolls; the scroll drop on a player kill (about half the time).
- Villager ghosts: kill a villager with a zombie, then go hurt near it. Does the ghost float in (translucent pale blue, the profession clothes), hover about two blocks from you at your eye height, cast, and fade when the fight is over? Does the translucent texture look right at distance and in the dark? Does it glide smoothly (its update interval is 1 tick) rather than in steps? A hurt you with no monster anywhere near gets no ghost.
- Config screen and keys: nothing new (Part 7 adds no setting and no key).

- [x] **Step 4: API notes and spec sentences that the code settled**

Re-read §26 against what Tasks 3–14 found; correct every line the sources contradicted and add the facts the tasks needed that §26 did not have (especially: how `FinalizeSpawnEvent` is posted, which `LivingEntity` fields the image renderer could read, the Mirror's `MirrorImage` save of the equipment, `EntityRenderState.nameTag` **and** `scoreText`, which the image renderer both clears). No `(verify)` marker may remain in §26: `git grep -n "(verify)" -- docs/api-notes.md` prints nothing. Fill in the final baseline line with the real totals. In the Parts 6–7 spec, bring these sentences in line with the code as built (and with any divergence the earlier tasks' as-built notes record): §6.2 (a Caster tries Ghostrail before Mirror, as the villager ghost does; a Caster in a wind-up is not "busy"; `CasterRules` is the pure home of its numbers), §6.4 (`CastingState.of` returns `CastLook`), §7.1 (`Spells.cast`), §7.2 (images also save their copied type and profile; a recast poofs the old set first; `MirrorImages.afterEnd`; the ring spawn keeps images out of blocks; the images carry the hands' items only, no armor), §7.3 (`GrantedAbilities.Plan`; `AbilitySync` is the renamed `InfinityAbilitySync`; the spec's `Player.java:243` is right and stays), §8 (the chase goal is installed on every evoker, vindicator and pillager that joins, a scroll or not), §9 (the step is a public seam, the 20-tick gate is in `onPlayerTick`; no ghost without a hostile near; the ghost moves by position), §12 (the new pure classes and `CasterRules`) and §14 (rows for Ghostrail before Mirror, the extra save fields, the chase goal install). The Parts 6–7 spec's §7.1 (one creative refusal for casters that are not scrolls: `Spells.refusedFor`), §9 (a ghost needs a hostile near), §10 (what happens on a carrier's death) and §11 (what `runData` proves) were amended while the plan was reviewed; check that each still matches the code as built. Add the inline "Amended by the Parts 6–7 spec" notes to the parent spec at §9.1, §9.2, §9.3, §9.4, §9.5, §10 (Ghostrail's fallback), §11 and §13 (rows "Move gear", "Ghost trigger", "Which villagers leave ghosts"), in the style of the notes already there (one bold sentence naming the new spec's section and what changed); the notes for §3.1, §7 and §8 are already in.

- [x] **Step 5: Mutation testing**

Run: `./gradlew pitest` (background it if it takes over two minutes; read the summary from its report). Add a row to `docs/mutation-testing.md` for this part in the file's format: date, the new classes (`GhostrailDuration`, `GrantedAbilities`, `GhostRecords`, `GhostNeed`, `MirrorRule`, `CasterRules`), killed/total, and each surviving mutant with either the test added to kill it or one line on why it is equivalent. Add the tests that kill non-equivalent survivors, then rerun until only equivalents remain. (`InfinityAbilities` and its tests are gone: say so in the row, since the totals change.)

- [x] **Step 6: Whole-plan checks**

Run:

```bash
git diff main --stat -- src/main/resources/META-INF src/main/resources/spellcraft.client.mixins.json
git diff main --stat -- src/main/java/io/github/willrieske/spellcraft/mixin src/main/resources/spellcraft.mixins.json
git diff main -- gradle.properties
git show main:src/main/resources/assets/spellcraft/lang/en_us.json | grep -c "message.spellcraft\|key.spellcraft\|spellcraft.configuration"
grep -c "message.spellcraft\|key.spellcraft\|spellcraft.configuration" src/main/resources/assets/spellcraft/lang/en_us.json
```

Expected: the first prints nothing (no access-transformer or client Mixin change); the second lists exactly `PlayerGhostrailMixin.java` and `spellcraft.mixins.json`; the `git diff main -- gradle.properties` prints nothing (no version bump); the last two commands print the same number (the message, key and configuration lines of the lang file on `main`, which holds Part 6, and in the working tree: Part 7 adds no message, key or setting). No new `CustomPacketPayload` exists: `git grep -n "CustomPacketPayload" -- src/main/java` lists only `EvokingRequest`.

- [x] **Step 7: Gates and commit**

Run: `./gradlew spotlessApply build`, `./gradlew runData`, `./gradlew runGameTestServer` twice (expected `All N required tests passed`, where N is the total Task 12 recorded: this task adds no GameTest), and `python -m unittest discover -s scripts -p "test_*.py"`.

The mutation-killing unit tests of Step 5 are code, so they get their own commit:

```bash
git add src/test
git commit -m "test(spells): tests that kill the surviving mutants of the Part 7 rules"
```

Then the docs (skip the first commit if Step 5 added no test):

```bash
git add README.md CLAUDE.md docs src/main
git commit -m "docs(evoking): Part 7 in the README, playtest items, mutation testing, as-built spec notes"
```

> **As built (`2675609`):** diverged — the Step 6 lang-file count was run against f44271d (Part 6's end) instead of main, which does not hold Part 6 yet (32 = 32); GhostNeedTest gained BelowHalf and BelowQuarter refusesNonsense tests (pitest 555/562, the new classes fully killed); three extra playtest bullets and the CLAUDE.md runGameTestServer timeout clause, from the controller; review minors (README structure and ghost-reach wording) fixed in this commit; reason: implementer report, controller rulings, review approved

---

## Divergence index

<!-- generated by plan_map.py --write-index; do not edit -->

| Task | SHA | Divergence |
|---|---|---|
| 3 | `b63ced0` | `hundredPercent` passes option index 0 with chances `List.of(100, 100, 100)` (the plan implied passing 100 as the option); reason: `ScrollDraw.draw`'s first argument is an option index; the Task 2 review's Javadoc minors were fixed first in 978244f. |
| 4 | `e4e09ca` | one extra GameTest `mirror_tall_images_never_spawn_inside_a_ceiling` (total 677, not 676), and every spawn tier checks the copied type's box; reason: per-task review found a tall copied type could overlap a ceiling. No RED capture. |
| 5 | `b30d56d` | a mob already targeting a live image of this carrier keeps it (the change is cancelled, no roll), where the plan's handler returned early and let vanilla switch it to the carrier; reason: controller ruling on spec §7.2 "not re-rolled" read with the 13-in-14 rule. |
| 6 | `dabd455` | `staysAloftOverStone` asserts a drift under 0.5 blocks, not an exact y; reason: `ServerPlayer.doTick` runs `aiStep` and the first tick, before the sync sets `flying`, takes full gravity, which flight then damps (reviewed against `Player.travel`). |
| 7 | `6302d73` | `endingGoesToTheStartSpot` lifts the player 10, not 6, inside a stone cube (the plan's lift left the start spot within the search radius); a recast in stone keeps the earlier start spot (review fix, +1 test); the Task 6 review's creative-player fix (+1 test) landed first in 30ed2b4. No RED capture. |
| 8 | `31ca0c1` | no RED capture (production code partly written before tests); caster_plain_vex_charges resets a vex parked at rest beside the frozen husk each tick (test-only: ChargeGoal needs >2 blocks, a real target moves); countsTowardARaidTrigger proves the Shield control with a second summoner; reason: implementer report, review approved |
| 9 | `f871ee5` | CasterSpellGameTests scene floor is 8 blocks, not 3 (Mirror images wander up to 6 blocks and fell off a 3-block floor; spec has no ledge avoidance); review minors fixed in f871ee5 (dismiss once, exact cast-timing bound, choose() clears its pick); reason: implementer report, review approved |
| 10 | `ea7c862` | chasesThroughAWall keeps the player vulnerable (Mob.asValidTarget rejects an invulnerable target via canBeSeenAsEnemy, so the plan's setTarget was dropped) and empties the pillager's main hand instead; floored scenes use radius 12 for Mirror image wander room; EVOKER_CAST_SPELL has no .value() in 26.3; MOB_GHOSTRAIL_COOLDOWN_TICKS made public for the test; RED was compile-time; reason: implementer report, review approved |
| 11 | `ff499bf` | no RED capture (production code written before the tests compiled); `VillagerGhosts.Record` carries `@SuppressWarnings("AvoidCommonTypeNames")` (Error Prone rejects the name the spec mandates); test helpers discard their villagers and any converted zombie villager when done; reason: implementer report, review approved (its minors are fixed first in Task 12). |
| 12 | `4a481bf` | 788 GameTests (plan 782 + offset 3 + 3 added in review): the reach test moved out of HELP into its own environment, and the plan's optional boundary pair was built as 46 and 50 blocks (tighter than its 'about 40 / about 56'), each alone in its own environment with the record's chunk force-loaded and the unforce left to the runner (an unforce from a whenDone listener hung a full run, see api-notes §26); a creative-in-the-wind-up test added; step scans for live ghosts once and only when a loaded record is in range; reason: review fix round 1 and its re-review minors, re-review approved |
| 13 | `80c98ce` | RED captured only as the missing-PNG failure (the robe script and its tests were written together; SpellAssetsTest.robeTextureIs32x32 added after the PNG existed); CastingParticles gained PLAYER_HEIGHT and SCROLL_COLOR constants; review minors (Javadoc on the casting colors and the robe layer) fixed in 80c98ce; client path proven to compile only, first render is Will's playtest; reason: implementer report, review approved |
| 15 | `2675609` | the Step 6 lang-file count was run against f44271d (Part 6's end) instead of main, which does not hold Part 6 yet (32 = 32); GhostNeedTest gained BelowHalf and BelowQuarter refusesNonsense tests (pitest 555/562, the new classes fully killed); three extra playtest bullets and the CLAUDE.md runGameTestServer timeout clause, from the controller; review minors (README structure and ghost-reach wording) fixed in this commit; reason: implementer report, controller rulings, review approved |
