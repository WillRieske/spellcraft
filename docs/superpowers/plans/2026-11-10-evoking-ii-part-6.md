# Evoking II Part 6: Team Moves Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Your Bound Vexes act as a team: they form a Shield against something big, ready a Multishot volley when a strong monster goes after one of them, circle a hurt summoner or ally as an Evader guard, and Raid a giant together; a server setting turns the automatic triggers off, and then **J** picks a move and **K** starts it.

**Architecture:** Two pure classes in `spell.rules` (`TeamMove`, `TeamMoveRules`) hold every number and choice. `TeamMoves` (driven from the summoner's tick) owns one transient record per summoner: it scans for triggers, picks the roster, conjures the kit, runs the record-level parts (the Evader's shoves and deflections, the Raid's phase) and ends the move; `TeamMoveGoal` (priority 1) runs each member's own part by calling one small class per move (`ShieldMove`, `MultishotMove`, `EvaderMove`, `RaidMove`). `EvokingRequest` grows a kind and a byte so the same packet carries a team-move request.

**Tech Stack:** Minecraft 26.3, NeoForge 26.3.0.23-beta, Java 25, JUnit 5 + jqwik + AssertJ (pure core), NeoForge GameTests (server rules).

**Spec:** `2026-11-10-evoking-ii-parts-6-7-design.md` §1–§5 (Part 6), with the rows of §12 and §14 that name Part 6 and the glossary. Parent: `2026-11-07-evoking-ii-design.md` §7–§8 (the new spec wins where they differ).

**Builds on:** Part 5 (branch `feat/evoking-ii-part-5`, merged): `BoundVex.conjure/dismiss/isConjured/stashed`, `BoundVex.huntable/isPrey/inGuardArea`, `Jobs.Claims` and the four job goals, `VexRescues.rescuer`, `RangedGearGoal`'s friendly-fire line, `VexFireballs` (owns `LargeFireball` returns), the friendly-fire backstop in `BoundVexEvents.onIncomingDamage`, `EvokingCaster` and the one packet.

## Global Constraints

- Server owns state. **One packet changes shape (`EvokingRequest` gains a kind and a byte, Task 8), no new packet, no Mixin, no access-transformer line.** The client never reads a team move. If a step seems to need anything else, stop and report.
- Pure core (`spell.rules`): no `net.minecraft`/NeoForge imports (ArchUnit rule 2). TDD; jqwik properties for invariants. `TeamMove` lives there so `TeamMoveRules` can name it.
- Error Prone runs with `-Werror`; suppress only with `@SuppressWarnings("<Check>")` plus a comment saying why.
- Javadoc on every class, method and constant, lines ≤ 120 columns; American spelling; plain words.
- Tests follow `~/.claude/development-methodology.md` § Conventions, *Unit test style* (When/should docs, Arrange/Act/Assert, `@Nested` per method, one-line form for one-statement tests). Mirror `GuardPostureTest` and `VexCapPropertiesTest` (unit and properties) and `BoundVexStayGameTests` (GameTests).
- Never claim a visual or feel behavior works: add playtest items (Task 13) instead.
- Never bump the version, never create a tag.
- Never run `git checkout`/`git restore`/`git stash` on a file with uncommitted work. Stage and commit in two separate commands. Conventional commit messages, no attribution lines.
- Every vanilla or NeoForge call is checked against `C:\dev\spellcraft\build\mc-src` and `build\nf-src` before use. `docs/api-notes.md` §25 (Task 1) records what was verified; where this plan's snippet disagrees with the sources, the sources win and the as-built note says so.
- GameTests: inner loop `./gradlew runGameTestServer "-PgameTests=spellcraft:team_move_*"`; before each task's commit, the full `./gradlew runGameTestServer` **twice** (flakiness), expecting `All N required tests passed` both times. The baseline at the start of this plan is whatever the first full run of Task 1 reports (Part 5 ended at 496 plus its review fixes): record it in Task 1's as-built note.
- GameTest practices that already cost time here: place mobs by absolute `setPos` with no gravity; never `GameTestHelper.onEachTick` (use `BoundVexTestSupport.everyTick`); `thenWaitUntil(N, …)` demands an exact delay, so wait with `thenWaitUntil(() -> …)` or `thenExecuteAfter`; a mock player is never ticked or moved by the server (call `TeamMoves.tick(player)` by hand, or every tick through `TeamMoveTestSupport.stepEveryTick`, as `FangGameTests.step` calls `EvokingCaster.tick`). Limits and pauses count game ticks: a test that "steps" N times does one step per server tick, never a loop inside one tick.
- **Which tests use which start.** Trigger tests (Task 6) and request tests (Task 7) start moves through the real scan and the real request. Behavior tests (Tasks 9–12) and lifecycle tests (Tasks 4–5) start them through `TeamMoves.start(player, move, subject)`, which is the production method the scan and the request both call (no test-only seam exists or is added).
- **Why `TeamMoves` keeps its scan clock in game time.** The spec says the scan runs "on the summoner's `tickCount`", but a mock player is never ticked, so its `tickCount` never moves and a tick-count gate would be open on every call or never. The record stores `nextScan` (a game time) instead: a scan runs when `gameTime >= nextScan` and sets it 10 ticks on. Every real tick after the first reaches the same cadence.
- **Shared state in a concurrent batch.** Tests in a batch run **at the same time**, about 29 blocks apart. `TeamMoves` is keyed by the summoner's UUID (each mock player has its own), so concurrent tests never share a record. Three things are global, each in an environment of its own: `Dice.RAID_KIT` scripting (`team_move_dice`, one test), config changes through `withConfig` (`config_mutation`), and `TeamMoves.clear()` (`team_move_server_stop`, one test).
- Numbers below are requirements copied from the spec. Do not round or "improve" them.
- The snippets leave out imports and the Javadoc of some private constants and fields to stay short. The code you commit has both: Javadoc on every member, private ones included, and no line over 120 columns (`spotlessApply` does not wrap comments). Remove imports a change leaves unused.
- **Hooks Part 7 will use (leave them, write nothing for them).** (1) `TeamMoves.isFree` carries the one-line comment `// Part 7: and not a Caster (a Caster is free only for a Raid)`; `isCaster()` does not exist yet, so the free-vex test has no Caster clause in Part 6. (2) `TeamMoveKits.raid` carries `// Part 7: a Caster rolls only the armor, gets the shield, and holds the Mirror scroll`. (3) `RaidMove.tick` carries `// Part 7: a Caster only gathers, casts Mirror, then holds its cell`. (4) `Dice` is a class of named instances (`RAID_KIT` today); Part 7 adds the Caster, scroll-mob and Mirror dice beside it. (5) `EvokingNetwork.handle` is where a third request kind would be added; none is.

## File Structure

| File | Responsibility |
|---|---|
| `spell/rules/TeamMove.java` (new) | The five-value enum, its packet argument, its lang id, the J cycle |
| `spell/rules/TeamMoveRules.java` (new) | Roster sizes, which enemy gets which move, time limits, the Raid grid, the Evader orbit angle, the 48-block rule (spec §2.2, §2.3, §2.5, §4) |
| `config/TeamMoveMode.java` (new), `config/ServerConfig.java` | The `teamMoves` setting (spec §3.1) |
| `evoking/Dice.java` (new) | The scriptable global dice (`RAID_KIT`) |
| `evoking/TeamMoves.java` (new) | Per-summoner record: free vexes, start, tick, end, request, cooldowns, logout and server stop |
| `evoking/TeamMoveKits.java` (new) | What each move conjures; putting a kit back; taking it away |
| `evoking/TeamMoveGoal.java` (new) | Goal priority 1: keeps the kit on and runs the member's part of the move |
| `evoking/TeamMoveTriggers.java` (new) | The automatic scan (spec §2.3) |
| `evoking/TeamFlight.java` (new) | Fly-to, look-at, box gap, ring point |
| `evoking/ShieldMove.java`, `MultishotMove.java`, `EvaderMove.java`, `RaidMove.java` (new) | One move's behavior each (spec §4) |
| `evoking/BoundVex.java` | `teamMove` field, `vex_busy` refusals, static `huntable`, the goal |
| `evoking/Jobs.java` and the four job goals | `Claims.heldBy`, `Jobs.busy`, `holds(BoundVex)` |
| `evoking/VexShots.java`, `RangedGearGoal.java` | `friendInTheWay` moves to `VexShots`; `volley` and `rocket` |
| `evoking/EvokingRequest.java`, `EvokingNetwork.java` | The packet's second kind and its dispatch |
| `evoking/BoundVexEvents.java` | Server stop clears `TeamMoves` |
| `client/evoking/EvokingKeys.java` | J and K |
| `gametest/TeamMoveTestSupport.java` (new) | Frozen vexes, foes, stepping |
| `gametest/BoundVexTeamMove{Roster,Lifecycle,Trigger,Request,Shield,Multishot,Evader,Raid}GameTests.java` (new) | Part 6 GameTests |
| `assets/spellcraft/lang/en_us.json` | New keys |
| `README.md`, `CLAUDE.md`, `docs/api-notes.md` §25, `docs/playtest-checklist.md`, `docs/mutation-testing.md` | Docs |

All Java paths are under `src/main/java/io/github/willrieske/spellcraft/`; unit tests under `src/test/java/io/github/willrieske/spellcraft/`.

---

### Task 1: API notes §25

**Files:**

- Modify: `docs/api-notes.md` (new §25)

- [ ] **Step 1: Take the baseline**

Run: `./gradlew build` then `./gradlew runGameTestServer` once. Expected: green; note the `All N required tests passed` number `N`. It goes into this task's as-built note, and every later task's gate compares against it plus the tests that task added.

- [ ] **Step 2: Verify the facts and write `docs/api-notes.md` §25**

Check each line below in the sources (`Grep` the quoted names in `build/mc-src` and `build/nf-src`; keep or correct the cited line). They were read once while writing this plan; confirm them. If a line is wrong, write what the sources say and tell the coordinator which later task is affected.

```markdown
## 25. Evoking II Part 6 (team moves)

Verified against `build/mc-src` (vanilla with NeoForge patches) and `build/nf-src`; paths are under
`net/minecraft/` and `net/neoforged/neoforge/` respectively.

- **A crossbow fires only what is loaded.** `CrossbowItem.performShooting(Level, LivingEntity shooter, InteractionHand,
  ItemStack weapon, float power, float uncertainty, @Nullable LivingEntity targetOverride)` (`world/item/CrossbowItem.java:170`)
  reads `DataComponents.CHARGED_PROJECTILES` off the weapon, sets it to `EMPTY`, and shoots the stacks it held through
  `ProjectileWeaponItem.shoot` (`world/item/ProjectileWeaponItem.java:73`). The loading that adds Multishot's two
  extra projectiles is `draw` (`ProjectileWeaponItem.java:~140`, `processProjectileCount`), run only by
  `tryLoadProjectiles` (`CrossbowItem.java`); `performShooting` never calls it. So a mob that sets
  `CHARGED_PROJECTILES` by hand (`ChargedProjectiles.ofNonEmpty(List<ItemStack>)`,
  `world/item/component/ChargedProjectiles.java:44`) gets exactly those projectiles.
- **Multishot's spread comes from the stack.** `shoot` calls `EnchantmentHelper.processProjectileSpread`
  (`world/item/enchantment/EnchantmentHelper.java:311`); the Multishot data file (`data/minecraft/enchantment/multishot.json`)
  gives `projectile_spread` 10 at level 1, so three projectiles leave at 0° and ±10°. The enchantment must be on the
  weapon stack: `ItemStack.enchant(Holder<Enchantment>, int)` (`world/item/ItemStack.java:1090`), the holder from
  `level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MULTISHOT)`
  (`world/item/enchantment/Enchantments.java:123`; `InfinityDeathGameTests` makes the same lookup).
- **Durability.** `shoot` calls `weapon.hurtAndBreak(getDurabilityUse(projectile), …)` per projectile
  (`ProjectileWeaponItem.java:101`; a rocket costs 3, `CrossbowItem.getDurabilityUse`). `ItemStack.isDamageableItem()` is
  false while the stack has `DataComponents.UNBREAKABLE` (`ItemStack.java:448`), and `processDurabilityChange` then
  returns 0. `UNBREAKABLE` is a `Unit` component (`core/component/DataComponents.java:126`): `stack.set(DataComponents.UNBREAKABLE,
  Unit.INSTANCE)` (`net.minecraft.util.Unit` is an enum).
- **Arrow pickup.** `AbstractArrow.pickup` defaults to `DISALLOWED` (`world/entity/projectile/arrow/AbstractArrow.java:71`).
  Only a stack carrying `INTANGIBLE_PROJECTILE` (set by `useAmmo`, which a hand-loaded volley skips) makes it
  `CREATIVE_ONLY`. A vex's volley arrows are therefore not pickable, with nothing to set.
- **Inaccuracy a mob uses.** `RangedAttackMob.rangedAttackUncertainty(Level)` is `14 - level.getDifficulty().getId() * 4`
  (`world/entity/monster/RangedAttackMob.java:9`); a Bound Vex is no `RangedAttackMob`, so `VexShots` repeats the formula.
- **Rockets.** `CrossbowItem.createProjectile` (`CrossbowItem.java:152`) builds a `FireworkRocketEntity(level, stack,
  shooter, x, eyeY − 0.15, z, shotAtAngle = true)` (`world/entity/projectile/FireworkRocketEntity.java:88`) for a firework
  stack; `getShootingPower` (`CrossbowItem.java:81`) is 1.6 for a rocket. `Fireworks(int flightDuration,
  List<FireworkExplosion>)` (`world/item/component/Fireworks.java:17`), `FireworkExplosion.DEFAULT`
  (`FireworkExplosion.java:28`). `dealExplosionDamage` (`FireworkRocketEntity.java:227`) deals damage only if the
  explosion list is non-empty: `5 + 2 × explosions` scaled by `sqrt((5 − distance) / 5)` to every `LivingEntity`
  within 5 blocks that a ray from the rocket reaches, with `damageSources().fireworks(rocket, owner)`
  (`world/damagesource/DamageSources.java:227`): direct entity the rocket, causing entity the owner (the vex), which
  is what `BoundVexEvents.onIncomingDamage` reads.
- **Deflecting.** `Projectile.deflect(ProjectileDeflection, @Nullable Entity, @Nullable EntityReference<Entity> newOwner,
  boolean byAttack, double power)` (`world/entity/projectile/Projectile.java:313`) builds `Vec3(power, power, power)` and
  runs the deflection, sets the owner, calls `onDeflection`. `ProjectileDeflection.REVERSE`
  (`world/entity/projectile/ProjectileDeflection.java:11`) multiplies the velocity by `−power × 0.5` per axis and sets
  `needsSync`: power 2.0 is a full reversal. `EntityReference.of(entity)` (`world/entity/EntityReference.java:43`).
- **Knockback direction.** `LivingEntity.knockback(double power, double xd, double zd, DamageSource, float damage)`
  (`world/entity/LivingEntity.java:1740`) moves the victim along `−(xd, zd)`: pass `(attacker − victim)` to push the
  victim away from the attacker. It scales by `1 − KNOCKBACK_RESISTANCE` (line 1721; a Ravager resists 75 %) and posts
  `LivingKnockBackEvent`.
- **Swing.** `Mob.swingForAttack(InteractionHand)` (`world/entity/Mob.java:1524`) is the public swing (the spec's
  "`swing(MAIN_HAND)`" does not exist in this form in 26.3).
- **Attributes.** `LivingEntity.getAttribute(Holder<Attribute>)` is `@Nullable` (`LivingEntity.java:2287`); `Ghast`,
  `Shulker` and `EnderDragon` define no `ATTACK_DAMAGE`; `Vindicator` 5, `Ravager` 12 (`monster/illager/Vindicator.java:94`,
  `monster/Ravager.java:103`). A brain mob's `getTarget()` reads its brain (`Warden.java:474` → `Mob.getTargetFromBrain`, `Mob.java:253`).
- **Entities across levels.** `ServerLevel.getEntityInAnyDimension(UUID)` (`server/level/ServerLevel.java:1533`) looks in
  the level, then in every other level of the server; `Level.getEntity(UUID)` (`world/level/Level.java:846`) only in its own.
- **The server config enum.** `ModConfigSpec.Builder.defineEnum(String, V)` returns `EnumValue<V>`
  (`neoforge/common/ModConfigSpec.java:642`, class at line 1361); `get()` reads the live value, `set(V)` is what
  `GameTestSupport.withConfig` uses.
- **Keys.** `InputConstants.KEY_J = 13`, `KEY_K = 14` (`com/mojang/blaze3d/platform/InputConstants.java:50-51`); no vanilla
  mapping in `net/minecraft/client` uses either. `LocalPlayer.sendOverlayMessage(Component)`
  (`client/player/LocalPlayer.java:465`); on the server `Player.sendOverlayMessage` (`world/entity/player/Player.java:1414`).
- **Packet codecs.** `ByteBufCodecs.BYTE` (`network/codec/ByteBufCodecs.java:72`), `NeoForgeStreamCodecs.enumCodec(Class)`
  (`neoforge/network/codec/NeoForgeStreamCodecs.java:62`), `StreamCodec.composite` with two fields.
- **Vex flight.** `Vex.VexMoveControl.tick` (`world/entity/monster/Vex.java:299-326`): each tick adds `delta × speed × 0.05 /
  length` to the velocity, goes to `WAIT` (and halves the velocity) when within the vex's box size of the wanted spot,
  and turns the vex toward its target when it has one. "Normal speed" is the charge goal's modifier, 1.0.
- **Unhurtable mobs in tests.** `Entity.setPermanentlyInvulnerable(boolean)` (`world/entity/Entity.java:3158`) is the call;
  there is no `setInvulnerable`. `Entity.getEyePosition()` is final (`Entity.java:2032`); `getItemEnchantmentLevel(Holder<Enchantment>,
  ItemInstance)` (`EnchantmentHelper.java:53`) reads Multishot off a stack.
- **A dead player is still ticked.** (Part 5's `BoundVexRescueLifecycleGameTests.dieAndTick`:) `PlayerTickEvent.Post` fires
  for a dead player until it respawns, so `TeamMoves.tick` sees `isAlive() == false`.
```

Add to §25 anything else you had to look up for later steps.

- [ ] **Step 3: Commit**

```bash
git add docs/api-notes.md
git commit -m "docs(evoking): api-notes 25, the vanilla facts behind Part 6"
```

---

### Task 2: The pure core (`TeamMove`, `TeamMoveRules`)

**Files:**

- Create: `spell/rules/TeamMove.java`, `spell/rules/TeamMoveRules.java`
- Test: `spell/rules/TeamMoveTest.java`, `TeamMoveRulesTest.java`, `TeamMoveRulesPropertiesTest.java` (under `src/test/java/…`)

**Interfaces:**

- Produces:
  - `TeamMove { SHIELD, MULTISHOT, EVADER, RAID, NONE }`; `String id()` (lower case: `"shield"`); `byte arg()` (the ordinal, 0–3 for the four real moves); `TeamMove next()` (the J cycle; `NONE.next()` is `SHIELD`); `static Optional<TeamMove> fromArg(int arg)` (empty for anything but 0–3, `NONE`'s ordinal 4 included).
  - `TeamMoveRules` constants `RAID_HEALTH = 99.0`, `SHIELD_HEALTH = 31.0`, `MULTISHOT_DAMAGE = 6.0`, `RAID_SIZE = 12`, `LIMIT_TICKS = 2400`, `EVADER_LIMIT_TICKS = 1200`, `COOLDOWN_TICKS = 600`, `END_DISTANCE = 48.0`; `static int rosterSize(TeamMove move, int free)`; `static boolean fits(TeamMove move, double maxHealth, double attackDamage)`; `static boolean needsGuard(float health, float maxHealth)`; `static TeamMove pick(double maxHealth, double attackDamage, boolean targetsFreeVex, boolean raidReady, boolean shieldReady, boolean multishotReady)`; `static long endsAt(TeamMove move, long startTick)`; `static boolean tooFar(double distanceSqr)`; `static double gridAcross(int cell)`; `static double gridDepth(int cell)`; `static double orbitDegrees(int slot, int count, long ticksSinceStart)`.

`NONE` is last on purpose: the packet argument is the ordinal and spec §3.2 says "Shield 0 … Raid 3". `BoundVex.teamMove` is `NONE` by default (spec §2.1), and it can never be requested, which `fromArg` enforces.

- [ ] **Step 1: Write the failing tests**

`TeamMoveTest` (`@Nested class Next`, `Arg`, `FromArg`, `Id`):

```java
class TeamMoveTest {
    @Nested
    class Next {
        /**
         * When called on each of the four real moves, next should walk Shield, Multishot, Evader, Raid and back to
         * Shield (the J key's cycle).
         *
         * <p>Method under test: {@link TeamMove#next()}
         */
        @Test
        void cyclesTheFourMoves() {
            // Arrange & Act & Assert
            assertThat(TeamMove.SHIELD.next()).isEqualTo(TeamMove.MULTISHOT);
            assertThat(TeamMove.MULTISHOT.next()).isEqualTo(TeamMove.EVADER);
            assertThat(TeamMove.EVADER.next()).isEqualTo(TeamMove.RAID);
            assertThat(TeamMove.RAID.next()).isEqualTo(TeamMove.SHIELD);
        }

        /**
         * When called on NONE, next should start the cycle at Shield: a client that somehow holds NONE never gets stuck.
         *
         * <p>Method under test: {@link TeamMove#next()}
         */
        @Test
        void noneStartsTheCycle() {
            assertThat(TeamMove.NONE.next()).isEqualTo(TeamMove.SHIELD);
        }
    }

    @Nested
    class FromArg {
        /**
         * When the argument is 0 to 3, fromArg should name the move with that ordinal.
         *
         * <p>Method under test: {@link TeamMove#fromArg(int)}
         */
        @ParameterizedTest
        @CsvSource({"0,SHIELD", "1,MULTISHOT", "2,EVADER", "3,RAID"})
        void namesTheMove(int arg, TeamMove expected) {
            assertThat(TeamMove.fromArg(arg)).contains(expected);
        }

        /**
         * When the argument is outside 0 to 3 (NONE's ordinal 4 included), fromArg should name nothing.
         *
         * <p>Method under test: {@link TeamMove#fromArg(int)}
         */
        @ParameterizedTest
        @ValueSource(ints = {4, 5, -1, 127, 128, Integer.MIN_VALUE})
        void refusesAnythingElse(int arg) {
            assertThat(TeamMove.fromArg(arg)).isEmpty();
        }
    }

    @Nested
    class Arg {
        /**
         * When called on each real move, arg should be its ordinal and fromArg should give the move back.
         *
         * <p>Method under test: {@link TeamMove#arg()}
         */
        @ParameterizedTest
        @EnumSource(
                value = TeamMove.class,
                names = {"SHIELD", "MULTISHOT", "EVADER", "RAID"})
        void roundTrips(TeamMove move) {
            assertThat(move.arg()).isEqualTo((byte) move.ordinal());
            assertThat(TeamMove.fromArg(move.arg())).contains(move);
        }

        /**
         * When called on NONE, arg should be 4, which no request may carry.
         *
         * <p>Method under test: {@link TeamMove#arg()}
         */
        @Test
        void noneIsFour() {
            assertThat(TeamMove.NONE.arg()).isEqualTo((byte) 4);
        }
    }

    @Nested
    class Id {
        /**
         * When called on each move, id should be its name in lower case.
         *
         * <p>Method under test: {@link TeamMove#id()}
         */
        @ParameterizedTest
        @CsvSource({"SHIELD,shield", "MULTISHOT,multishot", "EVADER,evader", "RAID,raid", "NONE,none"})
        void isTheLowerCaseName(TeamMove move, String expected) {
            assertThat(move.id()).isEqualTo(expected);
        }
    }
}
```

`TeamMoveRulesTest` (`@Nested` per method; `@ParameterizedTest` where rows differ only by values):

```java
class TeamMoveRulesTest {
    @Nested
    class RosterSize {
        /**
         * When the free count is given, rosterSize should answer the spec's table (Shield 3 or 9, Multishot 3, Evader
         * up to 3, Raid 12, nothing below the minimum).
         *
         * <p>Method under test: {@link TeamMoveRules#rosterSize(TeamMove, int)}
         */
        @ParameterizedTest
        @CsvSource({
            "SHIELD,0,0", "SHIELD,2,0", "SHIELD,3,3", "SHIELD,8,3", "SHIELD,9,9", "SHIELD,12,9",
            "MULTISHOT,2,0", "MULTISHOT,3,3", "MULTISHOT,12,3",
            "EVADER,0,0", "EVADER,1,1", "EVADER,2,2", "EVADER,3,3", "EVADER,12,3",
            "RAID,11,0", "RAID,12,12", "RAID,13,12", "NONE,12,0"
        })
        void followsTheTable(TeamMove move, int free, int expected) {
            assertThat(TeamMoveRules.rosterSize(move, free)).isEqualTo(expected);
        }
    }

    @Nested
    class Fits {
        /**
         * When an enemy is at the edge of a move's threshold, fits should say yes at the threshold and no just under.
         *
         * <p>Method under test: {@link TeamMoveRules#fits(TeamMove, double, double)}
         */
        @ParameterizedTest
        @CsvSource({
            "SHIELD,31.0,0.0,true", "SHIELD,30.99,99.0,false",
            "RAID,99.0,0.0,true", "RAID,98.99,0.0,false",
            "MULTISHOT,0.0,6.0,true", "MULTISHOT,500.0,5.99,false",
            "EVADER,500.0,500.0,false", "NONE,500.0,500.0,false"
        })
        void edges(TeamMove move, double maxHealth, double attackDamage, boolean expected) {
            assertThat(TeamMoveRules.fits(move, maxHealth, attackDamage)).isEqualTo(expected);
        }
    }

    @Nested
    class NeedsGuard {
        /**
         * When health is under half the maximum, needsGuard should be true; at exactly half, a zero maximum or NaN, false.
         *
         * <p>Method under test: {@link TeamMoveRules#needsGuard(float, float)}
         */
        @ParameterizedTest
        @CsvSource({"9.0,20.0,true", "10.0,20.0,false", "0.5,20.0,true", "5.0,0.0,false", "NaN,20.0,false"})
        void underHalf(float health, float maxHealth, boolean expected) {
            assertThat(TeamMoveRules.needsGuard(health, maxHealth)).isEqualTo(expected);
        }
    }

    @Nested
    class Pick {
        /**
         * When an enemy earns a Raid and one can start, pick should answer Raid; with the Raid not ready, a Shield.
         *
         * <p>Method under test: {@link TeamMoveRules#pick(double, double, boolean, boolean, boolean, boolean)}
         */
        @Test
        void ravagerGetsARaidThenFallsThroughToAShield() {
            assertThat(TeamMoveRules.pick(100, 12, false, true, true, true)).isEqualTo(TeamMove.RAID);
            assertThat(TeamMoveRules.pick(100, 12, false, false, true, true)).isEqualTo(TeamMove.SHIELD);
            assertThat(TeamMoveRules.pick(100, 12, true, false, false, true)).isEqualTo(TeamMove.MULTISHOT);
        }

        /**
         * When the enemy has 40 health and hits for 7, pick should answer Shield.
         *
         * <p>Method under test: {@link TeamMoveRules#pick(double, double, boolean, boolean, boolean, boolean)}
         */
        @Test
        void endermanGetsAShield() {
            assertThat(TeamMoveRules.pick(40, 7, false, true, true, true)).isEqualTo(TeamMove.SHIELD);
        }

        /**
         * When the enemy hits for 5 (a vindicator), pick should answer none even if it hunts a free vex; at 6 it
         * answers Multishot, but not when no free vex is hunted or Multishot is not ready.
         *
         * <p>Method under test: {@link TeamMoveRules#pick(double, double, boolean, boolean, boolean, boolean)}
         */
        @Test
        void multishotNeedsSixDamageAndAHuntedVex() {
            assertThat(TeamMoveRules.pick(24, 5, true, true, true, true)).isEqualTo(TeamMove.NONE);
            assertThat(TeamMoveRules.pick(20, 6, true, true, true, true)).isEqualTo(TeamMove.MULTISHOT);
            assertThat(TeamMoveRules.pick(20, 6, false, true, true, true)).isEqualTo(TeamMove.NONE);
            assertThat(TeamMoveRules.pick(20, 6, true, true, true, false)).isEqualTo(TeamMove.NONE);
        }
    }

    @Nested
    class EndsAt {
        /**
         * When called with a start time, endsAt should add 2400 ticks, 1200 for an Evader and none for none.
         *
         * <p>Method under test: {@link TeamMoveRules#endsAt(TeamMove, long)}
         */
        @ParameterizedTest
        @CsvSource({"SHIELD,2500", "MULTISHOT,2500", "RAID,2500", "EVADER,1300", "NONE,100"})
        void addsTheLimit(TeamMove move, long expected) {
            assertThat(TeamMoveRules.endsAt(move, 100)).isEqualTo(expected);
        }
    }

    @Nested
    class TooFar {
        /**
         * When the squared distance is exactly 48 squared, tooFar should be false; a hair beyond, true.
         *
         * <p>Method under test: {@link TeamMoveRules#tooFar(double)}
         */
        @Test
        void isStrictlyBeyondFortyEight() {
            assertThat(TeamMoveRules.tooFar(0)).isFalse();
            assertThat(TeamMoveRules.tooFar(48.0 * 48.0)).isFalse();
            assertThat(TeamMoveRules.tooFar(48.0 * 48.0 + 0.01)).isTrue();
        }
    }

    @Nested
    class Grid {
        /**
         * When the cell is 0 to 11, gridAcross should be -2.25, -0.75, 0.75, 2.25 in each row and gridDepth -1.5, 0 and
         * 1.5 by row.
         *
         * <p>Method under test: {@link TeamMoveRules#gridAcross(int)}, {@link TeamMoveRules#gridDepth(int)}
         */
        @Test
        void isThreeRowsOfFour() {
            double[] across = {-2.25, -0.75, 0.75, 2.25};
            for (int cell = 0; cell < 12; cell++) {
                assertThat(TeamMoveRules.gridAcross(cell)).isEqualTo(across[cell % 4]);
                assertThat(TeamMoveRules.gridDepth(cell)).isEqualTo((cell / 4 - 1) * 1.5);
            }
        }

        /**
         * When the cell is outside 0 to 11, both should throw.
         *
         * <p>Method under test: {@link TeamMoveRules#gridAcross(int)}, {@link TeamMoveRules#gridDepth(int)}
         */
        @Test
        void refusesOtherCells() {
            assertThatThrownBy(() -> TeamMoveRules.gridAcross(-1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> TeamMoveRules.gridDepth(12)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class OrbitDegrees {
        /**
         * When the clock runs, orbitDegrees should spread the slots evenly and add 6 degrees a tick, modulo 360.
         *
         * <p>Method under test: {@link TeamMoveRules#orbitDegrees(int, int, long)}
         */
        @ParameterizedTest
        @CsvSource({"0,3,0,0", "1,3,0,120", "2,3,0,240", "0,3,10,60", "1,3,50,60", "0,1,60,0"})
        void spreadsAndTurns(int slot, int count, long ticks, double expected) {
            assertThat(TeamMoveRules.orbitDegrees(slot, count, ticks)).isCloseTo(expected, within(1e-9));
        }

        /**
         * When the count is zero, orbitDegrees should throw.
         *
         * <p>Method under test: {@link TeamMoveRules#orbitDegrees(int, int, long)}
         */
        @Test
        void refusesAnEmptyRoster() {
            assertThatThrownBy(() -> TeamMoveRules.orbitDegrees(0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
```

`TeamMoveRulesPropertiesTest` (`@Group` classes; jqwik refuses static `@Group` classes, so suppress `ClassCanBeStatic` as `VexCapPropertiesTest` does):

```java
@Group
@SuppressWarnings("ClassCanBeStatic")
class RosterSize {
    /**
     * For any move and free count, rosterSize should never exceed the free count and never go negative.
     *
     * <p>Method under test: {@link TeamMoveRules#rosterSize(TeamMove, int)}
     */
    @Property
    void neverMoreThanFree(@ForAll TeamMove move, @ForAll @IntRange(min = 0, max = 40) int free) {
        assertThat(TeamMoveRules.rosterSize(move, free)).isBetween(0, free);
    }

    /**
     * For any free count, a Raid roster should be exactly 12 or nobody.
     *
     * <p>Method under test: {@link TeamMoveRules#rosterSize(TeamMove, int)}
     */
    @Property
    void raidIsTwelveOrNobody(@ForAll @IntRange(min = 0, max = 40) int free) {
        assertThat(TeamMoveRules.rosterSize(TeamMove.RAID, free)).isIn(0, TeamMoveRules.RAID_SIZE);
    }

    /**
     * For any move that can start with free vexes, enough free vexes should always give a roster, and the roster
     * should not shrink as more vexes become free.
     *
     * <p>Method under test: {@link TeamMoveRules#rosterSize(TeamMove, int)}
     */
    @Property
    void growsWithTheFreeCount(@ForAll TeamMove move, @ForAll @IntRange(min = 0, max = 39) int free) {
        assertThat(TeamMoveRules.rosterSize(move, free + 1)).isGreaterThanOrEqualTo(TeamMoveRules.rosterSize(move, free));
    }
}
```

Further properties, in the same file:

```java
@Group
@SuppressWarnings("ClassCanBeStatic")
class Pick {
    /**
     * For any enemy and readiness, pick should answer none, Raid, Shield or Multishot (never Evader), and never a
     * move whose own readiness is false.
     *
     * <p>Method under test: {@link TeamMoveRules#pick(double, double, boolean, boolean, boolean, boolean)}
     */
    @Property
    void neverPicksAMoveThatIsNotReady(
            @ForAll @DoubleRange(min = 0, max = 2000) double maxHealth,
            @ForAll @DoubleRange(min = 0, max = 2000) double attackDamage,
            @ForAll boolean targetsFreeVex,
            @ForAll boolean raidReady,
            @ForAll boolean shieldReady,
            @ForAll boolean multishotReady) {
        TeamMove picked = TeamMoveRules.pick(
                maxHealth, attackDamage, targetsFreeVex, raidReady, shieldReady, multishotReady);

        assertThat(picked).isIn(TeamMove.NONE, TeamMove.RAID, TeamMove.SHIELD, TeamMove.MULTISHOT);
        assertThat(picked != TeamMove.RAID || raidReady).isTrue();
        assertThat(picked != TeamMove.SHIELD || shieldReady).isTrue();
        assertThat(picked != TeamMove.MULTISHOT || (multishotReady && targetsFreeVex)).isTrue();
    }

    /**
     * For any enemy of 99 health or more, pick should never answer Multishot while a Shield could start.
     *
     * <p>Method under test: {@link TeamMoveRules#pick(double, double, boolean, boolean, boolean, boolean)}
     */
    @Property
    void aGiantIsNeverAMultishotWhileAShieldCouldStart(
            @ForAll @DoubleRange(min = 99, max = 2000) double maxHealth,
            @ForAll @DoubleRange(min = 0, max = 2000) double attackDamage,
            @ForAll boolean targetsFreeVex,
            @ForAll boolean raidReady,
            @ForAll boolean multishotReady) {
        assertThat(TeamMoveRules.pick(maxHealth, attackDamage, targetsFreeVex, raidReady, true, multishotReady))
                .isNotEqualTo(TeamMove.MULTISHOT);
    }

    /**
     * For any enemy under 31 health, pick should never answer Shield or Raid.
     *
     * <p>Method under test: {@link TeamMoveRules#pick(double, double, boolean, boolean, boolean, boolean)}
     */
    @Property
    void aSmallEnemyIsNeverAShieldOrARaid(
            @ForAll @DoubleRange(min = 0, max = 30.99) double maxHealth,
            @ForAll @DoubleRange(min = 0, max = 2000) double attackDamage,
            @ForAll boolean targetsFreeVex) {
        assertThat(TeamMoveRules.pick(maxHealth, attackDamage, targetsFreeVex, true, true, true))
                .isNotIn(TeamMove.SHIELD, TeamMove.RAID);
    }
}

@Group
@SuppressWarnings("ClassCanBeStatic")
class Grid {
    /**
     * For two cells, gridAcross and gridDepth together should name the same place only when the cells are equal.
     *
     * <p>Method under test: {@link TeamMoveRules#gridAcross(int)}, {@link TeamMoveRules#gridDepth(int)}
     */
    @Property
    void cellsAreDistinct(@ForAll @IntRange(min = 0, max = 11) int a, @ForAll @IntRange(min = 0, max = 11) int b) {
        boolean same = TeamMoveRules.gridAcross(a) == TeamMoveRules.gridAcross(b)
                && TeamMoveRules.gridDepth(a) == TeamMoveRules.gridDepth(b);

        assertThat(same).isEqualTo(a == b);
    }
}

@Group
@SuppressWarnings("ClassCanBeStatic")
class OrbitDegrees {
    /**
     * For any roster, time and slot, orbitDegrees should stay in [0, 360).
     *
     * <p>Method under test: {@link TeamMoveRules#orbitDegrees(int, int, long)}
     */
    @Property
    void staysInOneTurn(
            @ForAll @IntRange(min = 1, max = 12) int count,
            @ForAll @IntRange(min = 0, max = 11) int slot,
            @ForAll @LongRange(min = 0, max = 100_000) long ticks) {
        Assume.that(slot < count);

        assertThat(TeamMoveRules.orbitDegrees(slot, count, ticks)).isGreaterThanOrEqualTo(0.0).isLessThan(360.0);
    }

    /**
     * For any roster and time, two different slots should be at least an even share of the circle apart.
     *
     * <p>Method under test: {@link TeamMoveRules#orbitDegrees(int, int, long)}
     */
    @Property
    void slotsStayApart(
            @ForAll @IntRange(min = 2, max = 12) int count,
            @ForAll @IntRange(min = 0, max = 11) int a,
            @ForAll @IntRange(min = 0, max = 11) int b,
            @ForAll @LongRange(min = 0, max = 100_000) long ticks) {
        Assume.that(a < count && b < count && a != b);

        double gap = Math.abs(TeamMoveRules.orbitDegrees(a, count, ticks) - TeamMoveRules.orbitDegrees(b, count, ticks));
        double around = Math.min(gap, 360.0 - gap);

        assertThat(around).isGreaterThanOrEqualTo(360.0 / count - 1e-6);
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests "*TeamMoveTest" --tests "*TeamMoveRulesTest" --tests "*TeamMoveRulesPropertiesTest"`
Expected: compilation fails (the classes do not exist).

- [ ] **Step 3: Implement**

```java
package io.github.willrieske.spellcraft.spell.rules;

import java.util.Locale;
import java.util.Optional;

/**
 * A team move of the Bound Vexes (Parts 6–7 spec §2). {@link #NONE} is last on purpose: the packet argument of a
 * team-move request is the ordinal (spec §3.2, "Shield 0 … Raid 3"), and {@link #NONE} is what a vex that takes part
 * in no move carries (spec §2.1), so it can never be requested.
 */
public enum TeamMove {
    /** A few vexes bash a big enemy with conjured shields (spec §4.1). */
    SHIELD,
    /** Three vexes fire three arrows at once at a strong enemy that hunts one of them (spec §4.2). */
    MULTISHOT,
    /** Up to three vexes circle a hurt summoner or ally and shove and deflect (spec §4.3). */
    EVADER,
    /** All twelve vexes gear up, line up and charge a giant (spec §4.4). */
    RAID,
    /** No move. */
    NONE;

    private static final TeamMove[] PLAYABLE = {SHIELD, MULTISHOT, EVADER, RAID};

    /**
     * The move's name in lower case: the end of its lang keys ({@code message.spellcraft.team_move_<id>},
     * {@code team_move.spellcraft.<id>}).
     *
     * @return the id
     */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The packet argument for this move.
     *
     * @return the ordinal, 0 to 3 for a real move
     */
    public byte arg() {
        return (byte) ordinal();
    }

    /**
     * The next move in the J key's cycle: Shield, Multishot, Evader, Raid, Shield.
     *
     * @return the next move; {@link #SHIELD} after {@link #NONE}
     */
    public TeamMove next() {
        return this == NONE ? SHIELD : PLAYABLE[(ordinal() + 1) % PLAYABLE.length];
    }

    /**
     * The move a packet argument names.
     *
     * @param arg the byte from the request, widened
     * @return the move, or empty for an argument outside 0 to 3
     */
    public static Optional<TeamMove> fromArg(int arg) {
        return arg >= 0 && arg < PLAYABLE.length ? Optional.of(PLAYABLE[arg]) : Optional.empty();
    }
}
```

```java
package io.github.willrieske.spellcraft.spell.rules;

/**
 * The numbers and choices of the team moves (Parts 6–7 spec §2.2, §2.3, §2.5, §4): how many free vexes a move takes,
 * which enemy earns which move, when a move times out, and the two shapes the vexes fly in. Pure: the Minecraft side
 * reads the world and asks.
 */
public final class TeamMoveRules {
    /** An enemy with at least this much maximum health earns a Raid. */
    public static final double RAID_HEALTH = 99.0;

    /** An enemy with at least this much maximum health earns a Shield. */
    public static final double SHIELD_HEALTH = 31.0;

    /** An enemy with at least this much attack damage that hunts a vex earns a Multishot. */
    public static final double MULTISHOT_DAMAGE = 6.0;

    /** The Raid takes every vex there can be. */
    public static final int RAID_SIZE = 12;

    /** Ticks a Shield, Multishot or Raid lasts at most (120 s). */
    public static final int LIMIT_TICKS = 2400;

    /** Ticks an Evader lasts at most (60 s). */
    public static final int EVADER_LIMIT_TICKS = 1200;

    /** Ticks a move kind cannot start again after it ends (30 s). */
    public static final int COOLDOWN_TICKS = 600;

    /** A move ends when its enemy or ward is farther than this from the summoner. */
    public static final double END_DISTANCE = 48.0;

    private static final int SHIELD_SMALL = 3;
    private static final int SHIELD_LARGE = 9;
    private static final int MULTISHOT_SIZE = 3;
    private static final int EVADER_MAX = 3;
    private static final int GRID_COLUMNS = 4;
    private static final int GRID_CELLS = 12;
    private static final double GRID_SPACING = 1.5;
    private static final double ORBIT_DEGREES_PER_TICK = 6.0;
    private static final double FULL_TURN = 360.0;

    private TeamMoveRules() {}

    /**
     * How many vexes a move takes (spec §2.2).
     *
     * @param move the move
     * @param free how many free vexes the summoner has (for Multishot, counting the targeted one)
     * @return the roster size, or 0 when the move cannot start
     */
    public static int rosterSize(TeamMove move, int free) {
        return switch (move) {
            case SHIELD -> free >= SHIELD_LARGE ? SHIELD_LARGE : free >= SHIELD_SMALL ? SHIELD_SMALL : 0;
            case MULTISHOT -> free >= MULTISHOT_SIZE ? MULTISHOT_SIZE : 0;
            case EVADER -> Math.clamp(free, 0, EVADER_MAX);
            case RAID -> free >= RAID_SIZE ? RAID_SIZE : 0;
            case NONE -> 0;
        };
    }

    /**
     * Whether an enemy is one a move is fought against (spec §2.3, §3.4).
     *
     * @param move the move
     * @param maxHealth the enemy's maximum health
     * @param attackDamage the enemy's attack damage, 0 when it has none
     * @return true if the enemy fits; always false for Evader (it has a ward, not an enemy) and None
     */
    public static boolean fits(TeamMove move, double maxHealth, double attackDamage) {
        return switch (move) {
            case SHIELD -> maxHealth >= SHIELD_HEALTH;
            case RAID -> maxHealth >= RAID_HEALTH;
            case MULTISHOT -> attackDamage >= MULTISHOT_DAMAGE;
            case EVADER, NONE -> false;
        };
    }

    /**
     * Whether a summoner or ally is hurt enough for an Evader guard: below half health (spec §2.3).
     *
     * @param health the current health
     * @param maxHealth the maximum health
     * @return true below half; false for NaN
     */
    public static boolean needsGuard(float health, float maxHealth) {
        return health < maxHealth / 2.0F;
    }

    /**
     * The enemy-side order of the automatic triggers (spec §2.3): Raid, then Shield, then Multishot. An enemy that
     * earns a Raid but cannot start one (fewer than 12 free vexes, or the Raid cooling) falls through to a Shield.
     *
     * @param maxHealth the enemy's maximum health
     * @param attackDamage the enemy's attack damage, 0 when it has none
     * @param targetsFreeVex whether the enemy's target is a free Bound Vex of the summoner
     * @param raidReady whether a Raid could start: 12 free vexes and not cooling
     * @param shieldReady whether a Shield could start: at least 3 free vexes and not cooling
     * @param multishotReady whether a Multishot could start: at least 3 free vexes and not cooling
     * @return the move to start, or {@link TeamMove#NONE}
     */
    public static TeamMove pick(
            double maxHealth,
            double attackDamage,
            boolean targetsFreeVex,
            boolean raidReady,
            boolean shieldReady,
            boolean multishotReady) {
        if (raidReady && fits(TeamMove.RAID, maxHealth, attackDamage)) {
            return TeamMove.RAID;
        }
        if (shieldReady && fits(TeamMove.SHIELD, maxHealth, attackDamage)) {
            return TeamMove.SHIELD;
        }
        if (multishotReady && targetsFreeVex && fits(TeamMove.MULTISHOT, maxHealth, attackDamage)) {
            return TeamMove.MULTISHOT;
        }
        return TeamMove.NONE;
    }

    /**
     * The game time a move runs out (spec §2.5).
     *
     * @param move the move
     * @param startTick the game time it started
     * @return the time it ends at: 120 s later, Evader 60 s; a move that is none ends at once
     */
    public static long endsAt(TeamMove move, long startTick) {
        return switch (move) {
            case EVADER -> startTick + EVADER_LIMIT_TICKS;
            case SHIELD, MULTISHOT, RAID -> startTick + LIMIT_TICKS;
            case NONE -> startTick;
        };
    }

    /**
     * Whether an enemy or ward is too far from the summoner for the move to go on (spec §2.5).
     *
     * @param distanceSqr the squared distance
     * @return true beyond {@link #END_DISTANCE}
     */
    public static boolean tooFar(double distanceSqr) {
        return distanceSqr > END_DISTANCE * END_DISTANCE;
    }

    /**
     * Where a Raid member stands across the grid (spec §4.4): 4 columns, 1.5 blocks apart, centered.
     *
     * @param cell the member's cell, 0 to 11 in roster order
     * @return the sideways offset from the grid's center line, -2.25 to 2.25
     * @throws IllegalArgumentException for a cell outside 0 to 11
     */
    public static double gridAcross(int cell) {
        requireCell(cell);
        return (cell % GRID_COLUMNS - (GRID_COLUMNS - 1) / 2.0) * GRID_SPACING;
    }

    /**
     * Where a Raid member stands along the grid's depth (spec §4.4): 3 rows, 1.5 blocks apart; row 0 is the front
     * row, nearest the enemy.
     *
     * @param cell the member's cell, 0 to 11 in roster order
     * @return the offset from the grid's center, toward the summoner: -1.5, 0 or 1.5
     * @throws IllegalArgumentException for a cell outside 0 to 11
     */
    public static double gridDepth(int cell) {
        requireCell(cell);
        return (cell / GRID_COLUMNS - 1) * GRID_SPACING;
    }

    private static void requireCell(int cell) {
        if (cell < 0 || cell >= GRID_CELLS) {
            throw new IllegalArgumentException("cell " + cell + " is outside 0.." + (GRID_CELLS - 1));
        }
    }

    /**
     * The angle an Evader member circles at (spec §4.3): the members spread evenly and the circle turns 6° a tick.
     *
     * @param slot the member's place in the roster
     * @param count how many members there are
     * @param ticksSinceStart ticks since the move began
     * @return the angle in degrees, in {@code [0, 360)}
     * @throws IllegalArgumentException if {@code count} is not positive
     */
    public static double orbitDegrees(int slot, int count, long ticksSinceStart) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive: " + count);
        }
        return (FULL_TURN * slot / count + ORBIT_DEGREES_PER_TICK * ticksSinceStart) % FULL_TURN;
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew test --tests "*TeamMoveTest" --tests "*TeamMoveRulesTest" --tests "*TeamMoveRulesPropertiesTest"`
Expected: PASS. If a property fails on a counterexample, the spec's table decides: fix the code, never the property.

- [ ] **Step 5: Gates, then commit**

Run: `./gradlew spotlessApply build`. Expected: green.

```bash
git add src/main/java/io/github/willrieske/spellcraft/spell/rules src/test/java/io/github/willrieske/spellcraft/spell/rules
git commit -m "feat(evoking): the pure rules of the team moves"
```

---

### Task 3: Free vexes, the move flag and the busy refusals

**Files:**

- Create: `evoking/TeamMoves.java` (first slice: the free-vex test), `gametest/TeamMoveTestSupport.java`, `gametest/BoundVexTeamMoveRosterGameTests.java`
- Modify: `evoking/BoundVex.java`, `evoking/Jobs.java`, `evoking/RepairGolemGoal.java`, `CureZombieVillagerGoal.java`, `BribeGoal.java`, `AttackCrystalGoal.java`
- Modify: `assets/spellcraft/lang/en_us.json`, `gametest/SpellcraftGameTests.java` (register the class), `gametest/BoundVexJobGameTests.java` (`GOLEM_SPOT` and `golemOnStone` become package-private; one new test), `gametest/BoundVexLavaGameTests.java` (one new test)

**Interfaces:**

- Consumes: `VexRescues.rescuer(ServerPlayer)`, `BoundVex.isSummonedBy/isStaying`, the four goals' private `CLAIMS` maps.
- Produces:
  - `Jobs.Claims.heldBy(BoundVex holder)` (package-private); `static boolean holds(BoundVex vex)` on each of the four job goals; `Jobs.busy(BoundVex)` (package-private).
  - `BoundVex.teamMove()` / `setTeamMove(TeamMove)` (public; server-only, not synced, not saved); `BoundVex.BUSY_KEY = "message.spellcraft.vex_busy"`; package-private `static boolean huntable(@Nullable UUID summoner, LivingEntity target)` (the instance `huntable` calls it).
  - `TeamMoves.FREE_RANGE = 32.0`, `public static boolean isFree(BoundVex vex, ServerPlayer owner)`, `public static List<BoundVex> freeVexes(ServerPlayer owner)`.
  - In `TeamMoveTestSupport` (package-private, all static): `Vec3 hangPoint(GameTestHelper)`, `List<BoundVex> frozenVexes(GameTestHelper, ServerPlayer, int count)` (following, no AI, no gravity, in rows of four around the hang point), `List<BoundVex> flyingVexes(GameTestHelper, ServerPlayer, int count, Vec3 center)` (full AI, no target goals, in a row of one-block spacing), `<T extends Mob> T foe(GameTestHelper, EntityType<T>, Vec3 at)` (no AI, no gravity, persistent), `Husk bigHusk(GameTestHelper, Vec3 at)` (40 health, no armor), `void setBase(LivingEntity, Holder<Attribute>, double)`.

- [ ] **Step 1: Write the failing tests**

`TeamMoveTestSupport` (it must compile before any test can; the helpers only use classes that exist after this task):

```java
final class TeamMoveTestSupport {
    private TeamMoveTestSupport() {}

    /** The point (absolute) a test hangs its vexes around: over the structure's middle, above the barrier shell. */
    static Vec3 hangPoint(GameTestHelper helper) {
        return helper.absoluteVec(Vec3.atBottomCenterOf(BoundVexTestSupport.AIR_SPOT));
    }

    /** {@code count} following Bound Vexes of {@code player} with no AI and no gravity, in rows of four around the hang point. */
    static List<BoundVex> frozenVexes(GameTestHelper helper, ServerPlayer player, int count) {
        List<BoundVex> vexes = new ArrayList<>();
        Vec3 hang = hangPoint(helper);
        for (int i = 0; i < count; i++) {
            BoundVex vex = BoundVexTestSupport.boundVex(helper, BoundVexTestSupport.AIR_SPOT, player);
            vex.setNoAi(true);
            vex.setPos(hang.add(i % 4 - 1.5, 0.0, i / 4 - 1.0));
            vexes.add(vex);
        }
        return vexes;
    }

    /** {@code count} Bound Vexes with their full AI but no target goals, in a row one block apart through {@code center}. */
    static List<BoundVex> flyingVexes(GameTestHelper helper, ServerPlayer player, int count, Vec3 center) {
        List<BoundVex> vexes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BoundVex vex = BoundVexTestSupport.noTargets(
                    BoundVexTestSupport.boundVex(helper, BoundVexTestSupport.AIR_SPOT, player));
            vex.setPos(center.add(i - (count - 1) / 2.0, 0.0, 0.0));
            vexes.add(vex);
        }
        return vexes;
    }

    /** A mob with no AI and no gravity at {@code at} (absolute), kept by the world and discarded when the test ends. */
    static <T extends Mob> T foe(GameTestHelper helper, EntityType<T> type, Vec3 at) {
        T mob = helper.spawn(type, BoundVexTestSupport.FEET);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setPersistenceRequired();
        mob.setPos(at);
        GameTestSupport.whenDone(helper, mob::discard);
        return mob;
    }

    /** A husk with 40 health and no armor: big enough for a Shield, and plain damage numbers. */
    static Husk bigHusk(GameTestHelper helper, Vec3 at) {
        Husk husk = foe(helper, EntityTypes.HUSK, at);
        setBase(husk, Attributes.MAX_HEALTH, 40.0);
        husk.setHealth(40.0F);
        setBase(husk, Attributes.ARMOR, 0.0);
        return husk;
    }

    /** Sets an attribute's base value; fails loudly when the entity has no such attribute. */
    static void setBase(LivingEntity entity, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            throw new IllegalStateException(entity.getType() + " has no attribute " + attribute.getRegisteredName());
        }
        instance.setBaseValue(value);
    }
}
```

`BoundVexTeamMoveRosterGameTests` registers through `FangGameTests.addPadded(SHARED, …)` (the vexes sit in the padding) and is added to the static block of `SpellcraftGameTests` after `BoundVexRescueLifecycleGameTests.registerAll()`:

```java
static void registerAll() {
    FangGameTests.addPadded(SHARED, "team_move_free_a_following_vex", BoundVexTeamMoveRosterGameTests::followingVexIsFree);
    FangGameTests.addPadded(SHARED, "team_move_free_not_a_staying_vex", BoundVexTeamMoveRosterGameTests::stayingVexIsNotFree);
    FangGameTests.addPadded(SHARED, "team_move_free_not_a_marked_vex", BoundVexTeamMoveRosterGameTests::markedVexIsNotFree);
    FangGameTests.addPadded(
            SHARED, "team_move_free_not_another_summoners_vex", BoundVexTeamMoveRosterGameTests::strangersVexIsNotFree);
    FangGameTests.addPadded(
            SHARED, "team_move_free_not_a_vex_in_another_level", BoundVexTeamMoveRosterGameTests::otherLevelVexIsNotFree);
    FangGameTests.addPadded(
            SHARED, "team_move_free_not_a_summoned_by_command_vex", BoundVexTeamMoveRosterGameTests::unmarkedVexIsNotFree);
    FangGameTests.addPadded(SHARED, "team_move_busy_refuses_the_stay_click", BoundVexTeamMoveRosterGameTests::busyRefusesStay);
    FangGameTests.addPadded(
            SHARED, "team_move_busy_refuses_the_take_back_click", BoundVexTeamMoveRosterGameTests::busyRefusesTakeBack);
    FangGameTests.addPadded(SHARED, "team_move_busy_still_takes_a_gift", BoundVexTeamMoveRosterGameTests::busyTakesAGift);
}
```

```java
/**
 * When a summoner has three following vexes with no mark, no job and no rescue near them, should every one be free and
 * listed.
 *
 * <p>Method under test: {@link TeamMoves#isFree}, {@link TeamMoves#freeVexes}
 */
static void followingVexIsFree(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    List<BoundVex> vexes = frozenVexes(helper, player, 3);

    // Act
    List<BoundVex> free = TeamMoves.freeVexes(player);

    // Assert
    helper.assertValueEqual(free.size(), 3, "three free vexes");
    helper.assertTrue(TeamMoves.isFree(vexes.get(0), player), "the first one is free");
    helper.succeed();
}

/**
 * When one of three vexes is told to stay, should it not be free and the other two should be.
 *
 * <p>Method under test: {@link TeamMoves#isFree}
 */
static void stayingVexIsNotFree(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    List<BoundVex> vexes = frozenVexes(helper, player, 3);
    vexes.get(1).setStaying(true);

    // Act & Assert
    helper.assertFalse(TeamMoves.isFree(vexes.get(1), player), "a staying vex is not free");
    helper.assertValueEqual(TeamMoves.freeVexes(player).size(), 2, "the other two are");
    helper.succeed();
}

/**
 * When a vex already carries a team move, should it not be free.
 *
 * <p>Method under test: {@link TeamMoves#isFree}
 */
static void markedVexIsNotFree(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    List<BoundVex> vexes = frozenVexes(helper, player, 2);
    vexes.get(0).setTeamMove(TeamMove.SHIELD);

    // Act & Assert
    helper.assertFalse(TeamMoves.isFree(vexes.get(0), player), "a vex in a move is not free");
    helper.assertValueEqual(TeamMoves.freeVexes(player), List.of(vexes.get(1)), "only the other is listed");
    helper.succeed();
}

/**
 * When another player's vex hangs beside the summoner's, should the summoner's list leave it out.
 *
 * <p>Method under test: {@link TeamMoves#freeVexes}
 */
static void strangersVexIsNotFree(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    ServerPlayer stranger = MockPlayers.inLevel(helper, GameType.SURVIVAL);
    MockPlayers.moveIntoTest(helper, stranger);
    List<BoundVex> mine = frozenVexes(helper, player, 1);
    BoundVex theirs = BoundVexTestSupport.boundVex(helper, BoundVexTestSupport.AIR_SPOT, stranger);
    theirs.setNoAi(true);

    // Act & Assert
    helper.assertValueEqual(TeamMoves.freeVexes(player), mine, "only my vex is free for me");
    helper.assertFalse(TeamMoves.isFree(theirs, player), "a stranger's vex is not");
    helper.succeed();
}

/**
 * When the summoner stands in the Nether and the vexes were left in the test level, should none be free.
 *
 * <p>Method under test: {@link TeamMoves#isFree}: the same-level clause
 */
static void otherLevelVexIsNotFree(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    List<BoundVex> vexes = frozenVexes(helper, player, 3);
    BoundVexWorldTestSupport.room(helper, player, Level.NETHER, 3, 4);

    // Act & Assert
    helper.assertTrue(TeamMoves.freeVexes(player).isEmpty(), "nobody is free across levels");
    helper.assertFalse(TeamMoves.isFree(vexes.get(0), player), "a vex in another level is not free");
    helper.succeed();
}

/**
 * When a vex has no summoner mark (as {@code /summon} makes one), should it never be free for anyone.
 *
 * <p>Method under test: {@link TeamMoves#isFree}: the summoner clause
 */
static void unmarkedVexIsNotFree(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    BoundVex vex = BoundVexTestSupport.unmarkedVex(helper, BoundVexTestSupport.AIR_SPOT);

    // Act & Assert
    helper.assertFalse(TeamMoves.isFree(vex, player), "a vex nobody summoned is not free");
    helper.assertTrue(TeamMoves.freeVexes(player).isEmpty(), "and is not listed");
    helper.succeed();
}

/**
 * When the summoner sneak-clicks a vex that is in a team move with an empty hand, should it answer "That vex is
 * busy" and keep following.
 *
 * <p>Method under test: {@code BoundVex#mobInteract} (protected), through {@link BoundVex#interact}
 */
static void busyRefusesStay(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    BoundVex vex = frozenVexes(helper, player, 1).get(0);
    vex.setTeamMove(TeamMove.SHIELD);
    GameTestSupport.hold(player, ItemStack.EMPTY);
    player.setShiftKeyDown(true);

    // Act
    InteractionResult result = vex.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);

    // Assert
    helper.assertValueEqual(result, InteractionResult.SUCCESS_SERVER, "the click is answered");
    helper.assertFalse(vex.isStaying(), "the vex still follows");
    GameTestSupport.assertActionBarKey(helper, player, BoundVex.BUSY_KEY);
    helper.succeed();
}

/**
 * When the summoner clicks a vex in a team move with an empty hand, should it answer "That vex is busy" and keep the
 * sword in its hand.
 *
 * <p>Method under test: {@code BoundVex#mobInteract} (protected), through {@link BoundVex#interact}
 */
static void busyRefusesTakeBack(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    BoundVex vex = frozenVexes(helper, player, 1).get(0);
    vex.setTeamMove(TeamMove.SHIELD);
    GameTestSupport.hold(player, ItemStack.EMPTY);

    // Act
    vex.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);

    // Assert
    helper.assertTrue(vex.getMainHandItem().is(Items.IRON_SWORD), "the sword stays");
    helper.assertTrue(player.getMainHandItem().isEmpty(), "and the player's hand stays empty");
    GameTestSupport.assertActionBarKey(helper, player, BoundVex.BUSY_KEY);
    helper.succeed();
}

/**
 * When the summoner gives a diamond sword to a vex in a team move, should the gift be taken as always (the refusal is
 * for the empty hand only) and no busy message come.
 *
 * <p>Method under test: {@code BoundVex#mobInteract} (protected), through {@link BoundVex#interact}
 */
static void busyTakesAGift(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    BoundVex vex = frozenVexes(helper, player, 1).get(0);
    vex.setTeamMove(TeamMove.SHIELD);
    GameTestSupport.hold(player, new ItemStack(Items.DIAMOND_SWORD));

    // Act
    vex.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);

    // Assert
    helper.assertTrue(vex.getMainHandItem().is(Items.DIAMOND_SWORD), "the gift is taken");
    helper.assertTrue(GameTestSupport.drainActionBar(player).isEmpty(), "and nothing is said");
    helper.succeed();
}
```

In `BoundVexJobGameTests`: change `private static final BlockPos GOLEM_SPOT` and `private static IronGolem golemOnStone(…)` to package-private (drop `private`), register `FangGameTests.addPadded(SHARED, "bound_vex_job_repairing_vex_is_not_free", BoundVexJobGameTests::repairingVexIsNotFree);` and add (arranged as `repairsAHurtAllyGolem`, read it first):

```java
/**
 * When a Bound Vex is repairing a hurt ally golem (it holds the claim), should the free-vex list leave it out and keep
 * an idle vex of the same summoner in.
 *
 * <p>Method under test: {@code Jobs.busy}, through {@link TeamMoves#isFree}
 */
static void repairingVexIsNotFree(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    MockPlayers.moveTo(helper, player, new Vec3(6.5, 1, 1.5), NORTH);
    golemOnStone(helper, GOLEM_SPOT, 50.0F);
    BoundVex worker = unleashed(helper, GOLEM_SPOT.north(2).above(2), player);
    BoundVex idle = TeamMoveTestSupport.frozenVexes(helper, player, 1).get(0);

    // Act & Assert
    helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(worker.getMainHandItem().is(Items.IRON_INGOT), "setup: it repairs"))
            .thenExecute(() -> {
                helper.assertFalse(TeamMoves.isFree(worker, player), "a vex mid-job is not free");
                helper.assertValueEqual(TeamMoves.freeVexes(player), List.of(idle), "only the idle vex is listed");
            })
            .thenSucceed();
}
```

In `BoundVexLavaGameTests` register `FangGameTests.addPadded(SHARED, "bound_vex_world_lava_rescuer_is_not_free", BoundVexLavaGameTests::lavaRescuerIsNotFree);` (match how that class registers; read its `registerAll`) and add:

```java
/**
 * When a vex is the rescuer of a player in lava, should the free-vex list be empty (the rescuer is the only vex).
 *
 * <p>Method under test: {@link TeamMoves#isFree}: the rescuer clause
 */
static void lavaRescuerIsNotFree(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = lavaPool(helper, 0);
    BoundVex rescuer = followingVex(helper, player);

    // Act
    step(player);

    // Assert
    helper.assertTrue(VexRescues.rescuer(player).equals(Optional.of(rescuer)), "setup: the vex is the rescuer");
    helper.assertFalse(TeamMoves.isFree(rescuer, player), "the rescuer is not free");
    helper.assertTrue(TeamMoves.freeVexes(player).isEmpty(), "nobody is free");
    helper.succeed();
}
```

Known gap, recorded in the as-built note: the 32-block bound of `FREE_RANGE` has no GameTest. The padded box is 13 blocks; a vex 33 blocks away is outside it. The clause is one comparison with a constant.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:team_move_free_*"`
Expected: compilation fails (`TeamMoves`, `TeamMove` on `BoundVex`, `BUSY_KEY` do not exist).

- [ ] **Step 3: Implement**

`Jobs.Claims` gets the scan the keyed map needs (add beside `held`):

```java
/**
 * Whether {@code holder} holds a live claim on any subject. The map is keyed by subject, so this scans the values.
 *
 * @param holder a Bound Vex
 * @return true while one of its claims is live
 */
boolean heldBy(BoundVex holder) {
    UUID self = holder.getUUID();
    return claims.values().stream()
            .anyMatch(claim -> claim.holder().getUUID().equals(self)
                    && live(claim, claim.holder().level().getGameTime()));
}
```

and `Jobs` gets:

```java
/**
 * Whether {@code vex} is in the middle of one of the four jobs (a repair, a cure, a bribe, a crystal): a vex mid-job
 * finishes it and is not free for a team move (Parts 6–7 spec §2.2).
 *
 * @param vex a Bound Vex
 * @return true while it holds a live claim in any job
 */
static boolean busy(BoundVex vex) {
    return RepairGolemGoal.holds(vex)
            || CureZombieVillagerGoal.holds(vex)
            || BribeGoal.holds(vex)
            || AttackCrystalGoal.holds(vex);
}
```

Each of the four goals gets (the same method, its own `CLAIMS`; `AttackCrystalGoal` and `BribeGoal` already have a public `isClaimed`/`isCourted`, which stay):

```java
/**
 * Whether {@code vex} holds a live claim of this job.
 *
 * @param vex a Bound Vex
 * @return true while it does
 */
static boolean holds(BoundVex vex) {
    return CLAIMS.heldBy(vex);
}
```

`BoundVex`: add the field and accessors beside `stash` and `conjured`, the key beside the other constants, change the empty-hand branch of `mobInteract`, and split `huntable`:

```java
/** Lang key of the notice for an empty-hand click on a vex in a team move (Parts 6–7 spec §2.6). */
public static final String BUSY_KEY = "message.spellcraft.vex_busy";

/**
 * The team move this vex takes part in, {@link TeamMove#NONE} when it takes part in none (Parts 6–7 spec §2.1).
 * Server-only: not synced and not saved, because nothing on the client reads it and a vex that loads, or comes
 * through a portal, is a new entity whose kit {@code readAdditionalSaveData} has already dismissed.
 */
private TeamMove teamMove = TeamMove.NONE;

/** @return the team move this vex takes part in, or {@link TeamMove#NONE} */
public TeamMove teamMove() {
    return teamMove;
}

/**
 * Marks the vex as a member of {@code move} ({@link TeamMove#NONE} frees it). {@link TeamMoves} is the only caller in
 * play; the GameTests mark vexes directly.
 *
 * @param move the move
 */
public void setTeamMove(TeamMove move) {
    teamMove = Objects.requireNonNull(move, "move");
}
```

In `mobInteract`, the branches become (the gift branch is unchanged; the new branch is the second):

```java
if (slot.isPresent()) {
    give(player, held, slot.get());
} else if (teamMove != TeamMove.NONE) {
    // A member of a team move holds its post: the empty-hand clicks change nothing (Parts 6–7 spec §2.6).
    player.sendOverlayMessage(Component.translatable(BUSY_KEY));
} else if (player.isShiftKeyDown()) {
    setStaying(!isStaying());
    playSound(SoundEvents.VEX_AMBIENT, 1.0F, 1.0F);
} else {
    takeBackWeapon(player);
}
```

and the class Javadoc of `mobInteract` gains "an empty hand on a vex in a team move answers {@link #BUSY_KEY}". `huntable` becomes:

```java
boolean huntable(LivingEntity target) {
    return huntable(summonerId().orElse(null), target);
}

/**
 * {@link #huntable(LivingEntity)} for a summoner that need not have a vex at hand: the team-move scan and request ask
 * it for the summoner alone.
 *
 * @param summoner the summoner's UUID, or null for a vex nobody summoned
 * @param target the candidate
 * @return whether the patrol and ally goals of that summoner's vexes may pick it
 */
static boolean huntable(@Nullable UUID summoner, LivingEntity target) {
    return target instanceof Enemy
            && !(target instanceof Creeper)
            && target.getExistingData(AllyAttachments.SUMMONER)
                    .filter(mark -> !mark.equals(summoner))
                    .isEmpty();
}
```

`TeamMoves` (first slice):

```java
package io.github.willrieske.spellcraft.evoking;

/**
 * Team moves of a summoner's Bound Vexes (Parts 6–7 spec §2). This slice holds the test for a free vex; the record,
 * the triggers and the rest arrive in the next tasks.
 */
public final class TeamMoves {
    /** A vex farther than this from its summoner is not free (spec §2.2). */
    public static final double FREE_RANGE = 32.0;

    private TeamMoves() {}

    /**
     * Whether {@code vex} may join a move of {@code owner} (spec §2.2): alive, theirs, in their level, within
     * {@link #FREE_RANGE} blocks of them, following (not staying), in no move, in no job ({@link Jobs#busy}) and not
     * the player's rescuer.
     *
     * @param vex a Bound Vex
     * @param owner the summoner
     * @return true if it is free
     */
    public static boolean isFree(BoundVex vex, ServerPlayer owner) {
        // Part 7: and not a Caster (a Caster is free only for a Raid)
        return vex.isAlive()
                && vex.isSummonedBy(owner.getUUID())
                && vex.level().equals(owner.level())
                && !vex.isStaying()
                && vex.teamMove() == TeamMove.NONE
                && vex.distanceTo(owner) <= FREE_RANGE
                && !Jobs.busy(vex)
                && !VexRescues.rescuer(owner).map(vex::equals).orElse(false);
    }

    /**
     * The free vexes of {@code owner} in their level.
     *
     * @param owner the summoner
     * @return the free vexes, in query order
     */
    public static List<BoundVex> freeVexes(ServerPlayer owner) {
        return owner.level()
                .getEntitiesOfClass(BoundVex.class, owner.getBoundingBox().inflate(FREE_RANGE), vex -> isFree(vex, owner));
    }
}
```

Lang: add `"message.spellcraft.vex_busy": "That vex is busy",` after `too_many_vexes`.

- [ ] **Step 4: Run the tests**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:team_move_free_*"`, then `"-PgameTests=spellcraft:team_move_busy_*"`, `"…bound_vex_job_repairing_*"`, `"…bound_vex_world_lava_rescuer_*"`.
Expected: all pass. If `lavaRescuerIsNotFree` finds no rescuer, read `lavaPull` (it starts the rescue with one `step`) and match it.

- [ ] **Step 5: Full gates, then commit**

Run: `./gradlew spotlessApply build`, then `./gradlew runGameTestServer` twice (baseline + 11).

```bash
git add src/main/java/io/github/willrieske/spellcraft src/main/resources/assets/spellcraft/lang/en_us.json
git commit -m "feat(evoking): free vexes, the team-move flag and the busy refusals"
```

---

### Task 4: Starting a move: roster, kit, message

**Files:**

- Create: `evoking/Dice.java`, `evoking/TeamMoveKits.java`
- Modify: `evoking/TeamMoves.java` (the record, `start`, the read-only accessors), `assets/spellcraft/lang/en_us.json`
- Modify: `gametest/BoundVexTeamMoveRosterGameTests.java` (the start tests)

**Interfaces:**

- Consumes: `TeamMoveRules.rosterSize`, `TeamMove.id`, `BoundVex.conjure/stashed/isConjured/setTeamMove`.
- Produces:
  - `Dice.RAID_KIT`; `int below(RandomSource, int bound)`; `void script(int... rolls)`; `void unscript()` (public).
  - `TeamMoveKits.kit(TeamMove, ServerLevel, RandomSource)` → `Map<EquipmentSlot, ItemStack>`; `conjure(BoundVex, Map)`; `restore(BoundVex, Map)`; `dismiss(BoundVex, TeamMove)`; `slotsOf(TeamMove)` (all package-private).
  - `TeamMoves.start(ServerPlayer owner, TeamMove move, @Nullable LivingEntity subject)` → `boolean` (public); `running(ServerPlayer)` → `Optional<TeamMove>`; `roster(ServerPlayer)` → `List<BoundVex>` (roster order); `subject(ServerPlayer)` → `Optional<UUID>` (all public); `MESSAGE_PREFIX = "message.spellcraft.team_move_"`.
  - Package-private nested `TeamMoves.Record` (fields `move`, `roster`, `members`, `subject`, `start`, `phase`, `cooldownEnd`, `nextScan`, `shoved`; methods `active()`, `cooling(TeamMove, long)`, `begin(…)`, `add(UUID, Map)`, `finish(long)`), `TeamMoves.Member` (`cell`, `kit`, `nextAction`, `lungeStart`, `stage`), `enum Phase { GATHER, CHARGE }`, `Member.Stage { READY, LUNGING, BACKING }`.

- [ ] **Step 1: Write the failing tests**

Add to `BoundVexTeamMoveRosterGameTests.registerAll` (all `FangGameTests.addPadded(SHARED, …)`): `team_move_start_shield_three`, `team_move_start_shield_nine`, `team_move_start_refused_with_two_free`, `team_move_start_multishot_targeted_vex_is_in`, `team_move_start_multishot_nearest_when_nothing_hunts`, `team_move_start_evader_nearest_three`, `team_move_start_raid_twelve_geared`, `team_move_start_stashes_given_gear`, `team_move_start_refused_while_running`, `team_move_start_refused_without_a_subject`, `team_move_start_message_once`. The tests (import statics `frozenVexes`, `foe`, `bigHusk`, `hangPoint` from `TeamMoveTestSupport`; a helper `near(helper)` is `hangPoint(helper).add(3.0, 0.0, 0.0)`):

```java
private static Vec3 near(GameTestHelper helper) {
    return hangPoint(helper).add(3.0, 0.0, 0.0);
}

/** The roster vexes' main-hand and off-hand are the conjured shields. */
private static void assertShieldKit(GameTestHelper helper, BoundVex vex) {
    helper.assertTrue(vex.isConjured(EquipmentSlot.MAINHAND) && vex.getMainHandItem().is(Items.SHIELD), "main-hand shield");
    helper.assertTrue(vex.isConjured(EquipmentSlot.OFFHAND) && vex.getOffhandItem().is(Items.SHIELD), "off-hand shield");
    helper.assertTrue(vex.getMainHandItem().has(DataComponents.UNBREAKABLE), "unbreakable");
}

/**
 * When four vexes are free and a 40-health husk stands beside them, should a Shield start with the three vexes nearest
 * the husk, each marked, targeting it and holding a shield in each hand, and the fourth left alone.
 *
 * <p>Method under test: {@link TeamMoves#start}: the Shield roster and kit
 */
static void startShieldThree(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    List<BoundVex> vexes = frozenVexes(helper, player, 4);
    Husk husk = bigHusk(helper, near(helper));
    List<BoundVex> nearest = vexes.stream()
            .sorted(Comparator.comparingDouble(vex -> vex.distanceToSqr(husk)))
            .limit(3)
            .toList();

    // Act
    boolean started = TeamMoves.start(player, TeamMove.SHIELD, husk);

    // Assert
    helper.assertTrue(started, "the move started");
    helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.SHIELD), "a Shield runs");
    helper.assertValueEqual(Set.copyOf(TeamMoves.roster(player)), Set.copyOf(nearest), "the three nearest");
    for (BoundVex vex : nearest) {
        assertShieldKit(helper, vex);
        helper.assertValueEqual(vex.teamMove(), TeamMove.SHIELD, "marked");
        helper.assertTrue(husk.equals(vex.getTarget()), "targets the husk");
    }
    BoundVex left = vexes.stream().filter(vex -> !nearest.contains(vex)).findFirst().orElseThrow();
    helper.assertValueEqual(left.teamMove(), TeamMove.NONE, "the fourth is left alone");
    helper.succeed();
}
```

Further tests (add these beside it):

```java
/** The items a Raid member's main hand may hold (spec §4.4). */
private static final Set<Item> RAID_MAIN = Set.of(
        Items.NETHERITE_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_AXE, Items.DIAMOND_AXE, Items.MACE,
        Items.TRIDENT, Items.CROSSBOW);

/**
 * When ten vexes are free and a 40-health husk stands beside them, should a Shield take nine.
 *
 * <p>Method under test: {@link TeamMoves#start}: the Shield roster size
 */
static void startShieldNine(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 10);
    Husk husk = bigHusk(helper, near(helper));

    // Act
    boolean started = TeamMoves.start(player, TeamMove.SHIELD, husk);

    // Assert
    helper.assertTrue(started, "the move started");
    helper.assertValueEqual(TeamMoves.roster(player).size(), 9, "nine of ten");
    TeamMoves.roster(player).forEach(vex -> assertShieldKit(helper, vex));
    helper.succeed();
}

/**
 * When only two vexes are free, should a Shield not start, nobody be marked and nothing be said.
 *
 * <p>Method under test: {@link TeamMoves#start}: the minimum roster
 */
static void startRefusedWithTwoFree(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    List<BoundVex> vexes = frozenVexes(helper, player, 2);
    Husk husk = bigHusk(helper, near(helper));

    // Act
    boolean started = TeamMoves.start(player, TeamMove.SHIELD, husk);

    // Assert
    helper.assertFalse(started, "two vexes are not enough");
    helper.assertTrue(TeamMoves.running(player).isEmpty(), "nothing runs");
    helper.assertTrue(vexes.stream().allMatch(vex -> vex.teamMove() == TeamMove.NONE), "nobody is marked");
    helper.assertTrue(GameTestSupport.drainActionBar(player).isEmpty(), "and nothing is said");
    helper.succeed();
}

/**
 * When a husk with attack damage 6 hunts one of four free vexes, should a Multishot start with that vex and two more,
 * each with a conjured, unbreakable crossbow enchanted with Multishot, and the summoner be told once.
 *
 * <p>Method under test: {@link TeamMoves#start}: the Multishot roster and kit
 */
static void startMultishotTargetedVexIsIn(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    List<BoundVex> vexes = frozenVexes(helper, player, 4);
    Husk husk = foe(helper, EntityTypes.HUSK, near(helper));
    setBase(husk, Attributes.ATTACK_DAMAGE, 6.0);
    husk.setTarget(vexes.get(2));
    Holder<Enchantment> multishot = helper.getLevel()
            .registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.MULTISHOT);

    // Act
    boolean started = TeamMoves.start(player, TeamMove.MULTISHOT, husk);

    // Assert
    helper.assertTrue(started, "the move started");
    List<BoundVex> roster = TeamMoves.roster(player);
    helper.assertValueEqual(roster.size(), 3, "three vexes");
    helper.assertTrue(roster.contains(vexes.get(2)), "the hunted vex is in");
    for (BoundVex vex : roster) {
        ItemStack crossbow = vex.getMainHandItem();
        helper.assertTrue(vex.isConjured(EquipmentSlot.MAINHAND) && crossbow.is(Items.CROSSBOW), "a conjured crossbow");
        helper.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(multishot, crossbow), 1, "Multishot I");
        helper.assertTrue(crossbow.has(DataComponents.UNBREAKABLE), "unbreakable");
        helper.assertFalse(vex.isConjured(EquipmentSlot.OFFHAND), "the off hand is its own");
        helper.assertTrue(husk.equals(vex.getTarget()), "it targets the husk");
    }
    GameTestSupport.assertActionBarKey(helper, player, TeamMoves.MESSAGE_PREFIX + "multishot");
    helper.succeed();
}

/**
 * When a husk with attack damage 6 hunts nobody, should a Multishot take the free vex nearest the husk and the two
 * nearest that one.
 *
 * <p>Method under test: {@link TeamMoves#start}: the Multishot anchor
 */
static void startMultishotNearestWhenNothingHunts(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    List<BoundVex> vexes = frozenVexes(helper, player, 4);
    Husk husk = foe(helper, EntityTypes.HUSK, near(helper));
    setBase(husk, Attributes.ATTACK_DAMAGE, 6.0);
    BoundVex nearest = vexes.stream()
            .min(Comparator.comparingDouble(vex -> vex.distanceToSqr(husk)))
            .orElseThrow();

    // Act
    boolean started = TeamMoves.start(player, TeamMove.MULTISHOT, husk);

    // Assert
    helper.assertTrue(started, "the move started");
    helper.assertValueEqual(TeamMoves.roster(player).size(), 3, "three vexes");
    helper.assertTrue(TeamMoves.roster(player).contains(nearest), "the nearest vex is in");
    helper.succeed();
}

/**
 * When the summoner is below half health and five vexes are free, should an Evader start with the three nearest the
 * summoner, a shield in each hand, no target, the summoner as its ward, and one message.
 *
 * <p>Method under test: {@link TeamMoves#start}: the Evader roster, kit and ward
 */
static void startEvaderNearestThree(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    List<BoundVex> vexes = frozenVexes(helper, player, 5);
    player.setHealth(8.0F);
    List<BoundVex> nearest = vexes.stream()
            .sorted(Comparator.comparingDouble(vex -> vex.distanceToSqr(player)))
            .limit(3)
            .toList();

    // Act
    boolean started = TeamMoves.start(player, TeamMove.EVADER, player);

    // Assert
    helper.assertTrue(started, "the move started");
    helper.assertValueEqual(Set.copyOf(TeamMoves.roster(player)), Set.copyOf(nearest), "the three nearest");
    for (BoundVex vex : TeamMoves.roster(player)) {
        assertShieldKit(helper, vex);
        helper.assertTrue(vex.getTarget() == null, "an Evader member takes no target");
    }
    helper.assertValueEqual(TeamMoves.subject(player), Optional.of(player.getUUID()), "the ward is the summoner");
    GameTestSupport.assertActionBarKey(helper, player, TeamMoves.MESSAGE_PREFIX + "evader");
    helper.succeed();
}

/**
 * When twelve vexes in iron helmets and swords are free and a ravager stands beside them, should a Raid take all
 * twelve, each with a raid weapon, a shield and three armor pieces of one material in the slots, all unbreakable, the
 * iron gear in the stash, and one message.
 *
 * <p>Method under test: {@link TeamMoves#start}: the Raid roster and kit
 */
static void startRaidTwelveGeared(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 12).forEach(vex -> vex.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET)));
    Ravager ravager = foe(helper, EntityTypes.RAVAGER, near(helper));

    // Act
    boolean started = TeamMoves.start(player, TeamMove.RAID, ravager);

    // Assert
    helper.assertTrue(started, "the move started");
    helper.assertValueEqual(TeamMoves.roster(player).size(), 12, "all twelve");
    for (BoundVex vex : TeamMoves.roster(player)) {
        helper.assertTrue(
                vex.isConjured(EquipmentSlot.MAINHAND) && RAID_MAIN.contains(vex.getMainHandItem().getItem()),
                "a raid weapon, not " + vex.getMainHandItem());
        helper.assertTrue(vex.isConjured(EquipmentSlot.OFFHAND) && vex.getOffhandItem().is(Items.SHIELD), "a shield");
        boolean netherite = vex.getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHERITE_HELMET);
        Item chest = netherite ? Items.NETHERITE_CHESTPLATE : Items.DIAMOND_CHESTPLATE;
        Item legs = netherite ? Items.NETHERITE_LEGGINGS : Items.DIAMOND_LEGGINGS;
        helper.assertTrue(
                netherite || vex.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET), "a netherite or diamond helmet");
        helper.assertTrue(vex.getItemBySlot(EquipmentSlot.CHEST).is(chest), "the chestplate matches the helmet");
        helper.assertTrue(vex.getItemBySlot(EquipmentSlot.LEGS).is(legs), "the leggings match the helmet");
        for (EquipmentSlot slot : BoundVex.GEAR_SLOTS) {
            helper.assertTrue(vex.isConjured(slot), slot + " is conjured");
            helper.assertTrue(vex.getItemBySlot(slot).has(DataComponents.UNBREAKABLE), slot + " is unbreakable");
        }
        helper.assertTrue(vex.stashed(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "the iron helmet is stashed");
        helper.assertTrue(vex.stashed(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD), "the iron sword is stashed");
    }
    GameTestSupport.assertActionBarKey(helper, player, TeamMoves.MESSAGE_PREFIX + "raid");
    helper.succeed();
}

/**
 * When a Shield starts for four free vexes, should each of the three members hold its iron sword in the stash and
 * the fourth keep it in its hand.
 *
 * <p>Method under test: {@link TeamMoves#start}: given gear is stashed
 */
static void startStashesGivenGear(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    List<BoundVex> vexes = frozenVexes(helper, player, 4);
    Husk husk = bigHusk(helper, near(helper));

    // Act
    TeamMoves.start(player, TeamMove.SHIELD, husk);

    // Assert
    for (BoundVex vex : vexes) {
        if (vex.teamMove() == TeamMove.SHIELD) {
            helper.assertTrue(vex.stashed(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD), "a member stashes its sword");
        } else {
            helper.assertTrue(BoundVexTestSupport.swordBack(vex), "the fourth keeps its sword");
        }
    }
    helper.succeed();
}

/**
 * When a Shield is running, should a second start (even of another husk, with vexes to spare) be refused and no more
 * than the first three vexes be marked.
 *
 * <p>Method under test: {@link TeamMoves#start}: one move at a time per summoner
 */
static void startRefusedWhileRunning(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    List<BoundVex> vexes = frozenVexes(helper, player, 6);
    Husk first = bigHusk(helper, near(helper));
    Husk second = bigHusk(helper, near(helper).add(0.0, 0.0, 1.0));
    helper.assertTrue(TeamMoves.start(player, TeamMove.SHIELD, first), "setup: the first move starts");

    // Act
    boolean again = TeamMoves.start(player, TeamMove.SHIELD, second);

    // Assert
    helper.assertFalse(again, "a second move is refused");
    helper.assertValueEqual(
            vexes.stream().filter(vex -> vex.teamMove() != TeamMove.NONE).count(), 3L, "still three marked");
    helper.succeed();
}

/**
 * When the subject is missing or the move is none, should nothing start.
 *
 * <p>Method under test: {@link TeamMoves#start}: the guards
 */
static void startRefusedWithoutASubject(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    List<BoundVex> vexes = frozenVexes(helper, player, 4);
    Husk husk = bigHusk(helper, near(helper));

    // Act & Assert
    helper.assertFalse(TeamMoves.start(player, TeamMove.SHIELD, null), "no subject, no move");
    helper.assertFalse(TeamMoves.start(player, TeamMove.NONE, husk), "none is not a move");
    helper.assertTrue(vexes.stream().allMatch(vex -> vex.teamMove() == TeamMove.NONE), "nobody is marked");
    helper.succeed();
}

/**
 * When a Shield starts, should the summoner's action bar hold exactly one message, "Your vexes form a Shield!"'s key.
 *
 * <p>Method under test: {@link TeamMoves#start}: the overlay message
 */
static void startMessageOnce(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 3);
    Husk husk = bigHusk(helper, near(helper));

    // Act
    TeamMoves.start(player, TeamMove.SHIELD, husk);

    // Assert
    GameTestSupport.assertActionBarKey(helper, player, TeamMoves.MESSAGE_PREFIX + "shield");
    helper.succeed();
}
```

Register them (same `FangGameTests.addPadded(SHARED, …)` shape as Task 3) under the names `team_move_start_shield_nine`, `team_move_start_refused_with_two_free`, `team_move_start_multishot_targeted_vex_is_in`, `team_move_start_multishot_nearest_when_nothing_hunts`, `team_move_start_evader_nearest_three`, `team_move_start_raid_twelve_geared`, `team_move_start_stashes_given_gear`, `team_move_start_refused_while_running`, `team_move_start_refused_without_a_subject`, `team_move_start_message_once`, plus `team_move_start_shield_three` for the first test above.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:team_move_start_*"`
Expected: compilation fails (`TeamMoves.start`, `running`, `roster`, `subject`, `MESSAGE_PREFIX` do not exist).

- [ ] **Step 3: Implement**

```java
package io.github.willrieske.spellcraft.evoking;

/**
 * The scriptable dice of the team moves (Parts 6–7 spec §5, "Dice"): the same shape as the bribe's per-vex dice, but
 * global, because the vexes a roll is for are chosen at the start. In play every roll is the random source's; a
 * GameTest scripts rolls with {@link #script} and must {@link #unscript} (a test that scripts one die runs in an
 * environment of its own, since the script is shared). Part 7 adds its dice (the Caster roll, scroll mobs, the Mirror
 * confusion) as more instances beside {@link #RAID_KIT}.
 */
public final class Dice {
    /** The Raid kit's rolls: per member, the main-hand item (0–6) then the armor material (0–1). */
    public static final Dice RAID_KIT = new Dice();

    private final Deque<Integer> scripted = new ArrayDeque<>();

    private Dice() {}

    /**
     * The next roll: the scripted one while any is left, else the random source's.
     *
     * @param random the source for an unscripted roll
     * @param bound the exclusive upper bound
     * @return a value in {@code [0, bound)}
     * @throws IllegalStateException if a scripted roll is outside the range
     */
    public int below(RandomSource random, int bound) {
        Integer next = scripted.poll();
        if (next == null) {
            return random.nextInt(bound);
        }
        if (next < 0 || next >= bound) {
            throw new IllegalStateException("scripted roll " + next + " is outside [0, " + bound + ")");
        }
        return next;
    }

    /**
     * Queues rolls to be returned in order before any random one.
     *
     * @param rolls the rolls
     */
    public void script(int... rolls) {
        for (int roll : rolls) {
            scripted.add(roll);
        }
    }

    /** Drops every queued roll. */
    public void unscript() {
        scripted.clear();
    }
}
```

```java
package io.github.willrieske.spellcraft.evoking;

/**
 * What each team move conjures (Parts 6–7 spec §4): the kit of a member, how to put it back after a rescue's item took
 * the hand, and how to take it away. Every kit item is unbreakable.
 */
final class TeamMoveKits {
    private static final List<Item> RAID_WEAPONS = List.of(
            Items.NETHERITE_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_AXE, Items.DIAMOND_AXE, Items.MACE, Items.TRIDENT);

    /** The seventh main-hand roll: a crossbow that fires rockets. */
    private static final int ROCKET_CROSSBOW = RAID_WEAPONS.size();

    private TeamMoveKits() {}

    /**
     * The slots a move conjures into.
     *
     * @param move the move
     * @return the slots, empty for none
     */
    static List<EquipmentSlot> slotsOf(TeamMove move) {
        return switch (move) {
            case SHIELD, EVADER -> List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);
            case MULTISHOT -> List.of(EquipmentSlot.MAINHAND);
            case RAID -> List.of(
                    EquipmentSlot.MAINHAND,
                    EquipmentSlot.OFFHAND,
                    EquipmentSlot.HEAD,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS);
            case NONE -> List.of();
        };
    }

    /**
     * Builds one member's kit. A Raid kit is rolled here (through {@link Dice#RAID_KIT}) and kept, so a member put back
     * after a rescue gets the same items.
     *
     * @param move the move
     * @param level the level, for the enchantment lookup
     * @param random the source for unscripted rolls
     * @return the kit by slot
     */
    static Map<EquipmentSlot, ItemStack> kit(TeamMove move, ServerLevel level, RandomSource random) {
        Map<EquipmentSlot, ItemStack> kit = new EnumMap<>(EquipmentSlot.class);
        switch (move) {
            case SHIELD, EVADER -> {
                kit.put(EquipmentSlot.MAINHAND, unbreakable(new ItemStack(Items.SHIELD)));
                kit.put(EquipmentSlot.OFFHAND, unbreakable(new ItemStack(Items.SHIELD)));
            }
            case MULTISHOT -> kit.put(EquipmentSlot.MAINHAND, multishotCrossbow(level));
            case RAID -> raid(kit, random);
            case NONE -> {
                // no kit
            }
        }
        return kit;
    }

    private static ItemStack multishotCrossbow(ServerLevel level) {
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        crossbow.enchant(
                level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MULTISHOT), 1);
        return unbreakable(crossbow);
    }

    /**
     * One of seven main-hand items at even odds, a shield, and head, chest and legs all netherite or all diamond (one
     * roll per member).
     */
    private static void raid(Map<EquipmentSlot, ItemStack> kit, RandomSource random) {
        // Part 7: a Caster rolls only the armor, gets the shield, and holds the Mirror scroll
        int main = Dice.RAID_KIT.below(random, ROCKET_CROSSBOW + 1);
        Item weapon = main < ROCKET_CROSSBOW ? RAID_WEAPONS.get(main) : Items.CROSSBOW;
        kit.put(EquipmentSlot.MAINHAND, unbreakable(new ItemStack(weapon)));
        kit.put(EquipmentSlot.OFFHAND, unbreakable(new ItemStack(Items.SHIELD)));
        boolean netherite = Dice.RAID_KIT.below(random, 2) == 0;
        kit.put(EquipmentSlot.HEAD, unbreakable(new ItemStack(netherite ? Items.NETHERITE_HELMET : Items.DIAMOND_HELMET)));
        kit.put(
                EquipmentSlot.CHEST,
                unbreakable(new ItemStack(netherite ? Items.NETHERITE_CHESTPLATE : Items.DIAMOND_CHESTPLATE)));
        kit.put(
                EquipmentSlot.LEGS,
                unbreakable(new ItemStack(netherite ? Items.NETHERITE_LEGGINGS : Items.DIAMOND_LEGGINGS)));
    }

    private static ItemStack unbreakable(ItemStack stack) {
        stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        return stack;
    }

    /** Conjures every item of {@code kit} into {@code vex} (given gear goes to the stash). */
    static void conjure(BoundVex vex, Map<EquipmentSlot, ItemStack> kit) {
        kit.forEach((slot, stack) -> vex.conjure(slot, stack.copy()));
    }

    /**
     * Puts back what a rescue's bucket or apple, or anything else, took out of a kit slot: a slot that is not conjured,
     * or whose conjured item is another item, is conjured again. A kit item that is still there is left alone, so a
     * crossbow fired this tick is not replaced.
     */
    static void restore(BoundVex vex, Map<EquipmentSlot, ItemStack> kit) {
        kit.forEach((slot, stack) -> {
            if (!vex.isConjured(slot) || !vex.getItemBySlot(slot).is(stack.getItem())) {
                vex.conjure(slot, stack.copy());
            }
        });
    }

    /** Dismisses the slots {@code move} conjures into: the conjured items go and the stashed gear returns. */
    static void dismiss(BoundVex vex, TeamMove move) {
        slotsOf(move).forEach(vex::dismiss);
    }
}
```

`TeamMoves` gets the record and `start`. Add these members (and the imports):

```java
/** Lang key prefix of the overlay message each start shows: {@code …team_move_shield} and so on. */
public static final String MESSAGE_PREFIX = "message.spellcraft.team_move_";

/** The Raid's two phases (spec §4.4). */
enum Phase {
    GATHER,
    CHARGE
}

/** One roster member's own state: its cell, its kit and its clocks. */
static final class Member {
    /** What a Shield member is doing. */
    enum Stage {
        READY,
        LUNGING,
        BACKING
    }

    /** The member's place in the roster at the start: its Raid grid cell. */
    final int cell;

    /** The kit this member conjured, kept so a put-back gives the same items. */
    final Map<EquipmentSlot, ItemStack> kit;

    /** The game time of the member's next bash, shot or attack; 0 means ready. */
    long nextAction;

    /** The game time the member's current lunge began. */
    long lungeStart;

    /** The member's Shield stage. */
    Stage stage = Stage.READY;

    Member(int cell, Map<EquipmentSlot, ItemStack> kit) {
        this.cell = cell;
        this.kit = kit;
    }
}

/** One summoner's record: the running move and the memory that outlives it (cooldowns, the scan clock). */
static final class Record {
    TeamMove move = TeamMove.NONE;
    final List<UUID> roster = new ArrayList<>();
    final Map<UUID, Member> members = new HashMap<>();
    @Nullable UUID subject;
    long start;
    Phase phase = Phase.GATHER;
    final EnumMap<TeamMove, Long> cooldownEnd = new EnumMap<>(TeamMove.class);
    long nextScan;
    final Map<UUID, Long> shoved = new HashMap<>();

    boolean active() {
        return move != TeamMove.NONE;
    }

    boolean cooling(TeamMove kind, long now) {
        return cooldownEnd.getOrDefault(kind, 0L) > now;
    }

    void begin(TeamMove kind, UUID subjectId, long now) {
        move = kind;
        subject = subjectId;
        start = now;
        phase = Phase.GATHER;
    }

    Member add(UUID vex, Map<EquipmentSlot, ItemStack> kit) {
        Member member = new Member(roster.size(), kit);
        roster.add(vex);
        members.put(vex, member);
        return member;
    }

    void finish(long cooldownUntil) {
        cooldownEnd.put(move, cooldownUntil);
        move = TeamMove.NONE;
        roster.clear();
        members.clear();
        subject = null;
        shoved.clear();
        phase = Phase.GATHER;
    }
}

/** Every summoner's record, by UUID. Transient, like {@code EvokingCaster}'s map. */
private static final Map<UUID, Record> STATES = new HashMap<>();

/**
 * Starts {@code move} for {@code owner} against {@code subject} (the enemy; for an Evader, the ward), if the summoner is
 * here, no move runs, the kind is not cooling and the free vexes fill a roster (spec §2.2, §2.4). Each member gets the
 * mark, the target (not for an Evader) and its kit (given gear goes to the stash); the summoner gets the overlay
 * message. This is the one method the scan, the request and the GameTests start a move through.
 *
 * @param owner the summoner
 * @param move the move
 * @param subject the enemy or ward
 * @return whether the move started
 */
public static boolean start(ServerPlayer owner, TeamMove move, @Nullable LivingEntity subject) {
    if (move == TeamMove.NONE || subject == null || !owner.isAlive() || owner.isSpectator()) {
        return false;
    }
    ServerLevel level = owner.level();
    long now = level.getGameTime();
    Record record = STATES.computeIfAbsent(owner.getUUID(), id -> new Record());
    if (record.active() || record.cooling(move, now)) {
        return false;
    }
    List<BoundVex> roster = chooseRoster(move, freeVexes(owner), subject);
    if (roster.isEmpty()) {
        return false;
    }
    record.begin(move, subject.getUUID(), now);
    for (BoundVex vex : roster) {
        Member member = record.add(vex.getUUID(), TeamMoveKits.kit(move, level, vex.getRandom()));
        vex.setTeamMove(move);
        if (move != TeamMove.EVADER) {
            vex.setTarget(subject);
        }
        TeamMoveKits.conjure(vex, member.kit);
    }
    owner.sendOverlayMessage(Component.translatable(MESSAGE_PREFIX + move.id()));
    return true;
}

/**
 * The roster of a move (spec §2.2): Shield and Evader take the free vexes nearest the enemy or ward, Multishot the
 * vex the enemy hunts (else the free vex nearest the enemy) and the two nearest that one, Raid every free vex.
 */
private static List<BoundVex> chooseRoster(TeamMove move, List<BoundVex> free, LivingEntity subject) {
    int size = TeamMoveRules.rosterSize(move, free.size());
    if (size == 0) {
        return List.of();
    }
    if (move == TeamMove.MULTISHOT) {
        BoundVex anchor = subject instanceof Mob mob && mob.getTarget() instanceof BoundVex hunted && free.contains(hunted)
                ? hunted
                : nearest(free, subject);
        List<BoundVex> roster = new ArrayList<>(List.of(anchor));
        free.stream()
                .filter(vex -> vex != anchor)
                .sorted(Comparator.comparingDouble(vex -> vex.distanceToSqr(anchor)))
                .limit(size - 1L)
                .forEach(roster::add);
        return roster;
    }
    return free.stream()
            .sorted(Comparator.comparingDouble(vex -> vex.distanceToSqr(subject)))
            .limit(size)
            .toList();
}

private static BoundVex nearest(List<BoundVex> vexes, Entity to) {
    return vexes.stream().min(Comparator.comparingDouble(vex -> vex.distanceToSqr(to))).orElseThrow();
}

/**
 * The move running for {@code owner}.
 *
 * @param owner the summoner
 * @return the move, or empty when none runs
 */
public static Optional<TeamMove> running(ServerPlayer owner) {
    Record record = STATES.get(owner.getUUID());
    return record != null && record.active() ? Optional.of(record.move) : Optional.empty();
}

/**
 * The living roster members in the summoner's level, in roster order.
 *
 * @param owner the summoner
 * @return the vexes; empty when no move runs
 */
public static List<BoundVex> roster(ServerPlayer owner) {
    Record record = STATES.get(owner.getUUID());
    return record == null ? List.of() : members(owner.level(), record);
}

/** The roster of {@code record} resolved to the entities alive in {@code level}, in roster order. */
static List<BoundVex> members(ServerLevel level, Record record) {
    List<BoundVex> vexes = new ArrayList<>();
    for (UUID id : record.roster) {
        if (level.getEntity(id) instanceof BoundVex vex && vex.isAlive()) {
            vexes.add(vex);
        }
    }
    return vexes;
}

/**
 * The UUID of the enemy or ward of the running move.
 *
 * @param owner the summoner
 * @return the UUID, or empty when none runs
 */
public static Optional<UUID> subject(ServerPlayer owner) {
    Record record = STATES.get(owner.getUUID());
    return record != null && record.active() ? Optional.ofNullable(record.subject) : Optional.empty();
}
```

Lang: add `"message.spellcraft.team_move_shield": "Your vexes form a Shield!"`, `"…team_move_multishot": "Your vexes ready a Multishot!"`, `"…team_move_evader": "Your vexes form an Evader guard!"`, `"…team_move_raid": "Your vexes Raid!"`.

- [ ] **Step 4: Run the tests**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:team_move_start_*"`
Expected: all pass. `startRaidTwelveGeared` uses the random Raid dice and asserts structure only.

- [ ] **Step 5: Full gates, then commit**

Run: `./gradlew spotlessApply build`, then `./gradlew runGameTestServer` twice (previous total + 11).

```bash
git add src/main/java/io/github/willrieske/spellcraft src/main/resources/assets/spellcraft/lang/en_us.json
git commit -m "feat(evoking): starting a team move picks the roster and conjures the kit"
```

---

### Task 5: Running and ending a move; the goal that keeps the kit

**Files:**

- Create: `evoking/TeamMoveGoal.java`, `gametest/BoundVexTeamMoveLifecycleGameTests.java`
- Modify: `evoking/TeamMoves.java` (tick, end rules, cooldown, logout, clear), `evoking/BoundVex.java` (register the goal at priority 1), `evoking/BoundVexEvents.java` (server stop), `gametest/TeamMoveTestSupport.java`, `gametest/SpellcraftGameTests.java` (register the class)

**Interfaces:**

- Consumes: Task 4's `Record`, `Member`, `TeamMoveKits`, `TeamMoveRules.endsAt/tooFar/needsGuard/COOLDOWN_TICKS`.
- Produces:
  - `TeamMoves.tick(ServerPlayer)` (public), `onPlayerTick`, `onLogout` (subscribers), `clear()` (package-private; `BoundVexEvents.onServerStopped` calls it), `cooling(ServerPlayer, TeamMove)` and `age(ServerPlayer, long ticks)` (public; `age` is for GameTests, never called in play, like `Claims.age`), `release(BoundVex)`, `membership(BoundVex)` → `Optional<TeamMoves.Membership>` (`record Membership(Record record, Member member)`), `isPresent(ServerPlayer)` (alive and not a spectator).
  - `TeamMoveGoal` (public final, package-private constructor): priority 1, flags `MOVE` and `LOOK`; `canUse` while `TeamMoves.membership(vex)` is present; `start` and `tick` put the kit back (`TeamMoveKits.restore`); `stop` dismisses the kit only when the record no longer lists the vex.
  - `TeamMoveTestSupport.stepEveryTick(GameTestHelper, ServerPlayer)`, `TeamMoveTestSupport.goalOf(BoundVex)` → `TeamMoveGoal`.

- [ ] **Step 1: Write the failing tests**

Add to `TeamMoveTestSupport`:

```java
/** Steps {@code TeamMoves.tick(player)} once per tick until the test ends; a mock player gets no player tick. */
static void stepEveryTick(GameTestHelper helper, ServerPlayer player) {
    BoundVexTestSupport.everyTick(helper, () -> TeamMoves.tick(player));
}

/** The vex's team-move goal, found in its goal selector. */
static TeamMoveGoal goalOf(BoundVex vex) {
    return vex.goalSelector.getAvailableGoals().stream()
            .map(wrapped -> wrapped.getGoal())
            .filter(TeamMoveGoal.class::isInstance)
            .map(TeamMoveGoal.class::cast)
            .findFirst()
            .orElseThrow();
}
```

`BoundVexTeamMoveLifecycleGameTests` registers `FangGameTests.addPadded(SHARED, …)` for every test below except the last, which uses `FangGameTests.addPadded("team_move_server_stop", …)` (the server-stop handler clears every summoner's record, so it runs alone), and is added to `SpellcraftGameTests` after the roster class. Names: `team_move_end_enemy_dies`, `team_move_end_enemy_removed`, `team_move_end_enemy_beyond_48`, `team_move_end_time_limit`, `team_move_end_evader_time_limit`, `team_move_end_ward_healed`, `team_move_end_roster_empty`, `team_move_end_summoner_dies`, `team_move_end_summoner_spectates`, `team_move_end_summoner_changes_level`, `team_move_end_cooldown_per_kind`, `team_move_end_logout_releases_kits`, `team_move_end_dead_member_drops_no_kit`, `team_move_goal_runs_for_a_listed_member`, `team_move_goal_stop_keeps_the_kit_while_listed`, `team_move_goal_puts_a_replaced_item_back`, `team_move_end_server_stop_clears_records`.

```java
private record Scene(ServerPlayer player, List<BoundVex> vexes, Husk husk) {}

/** A running Shield: three frozen vexes and a 40-health husk beside them. */
private static Scene shield(GameTestHelper helper) {
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    List<BoundVex> vexes = frozenVexes(helper, player, 3);
    Husk husk = bigHusk(helper, hangPoint(helper).add(3.0, 0.0, 0.0));
    helper.assertTrue(TeamMoves.start(player, TeamMove.SHIELD, husk), "setup: the Shield starts");
    return new Scene(player, vexes, husk);
}

/** Fails unless the Shield is over: unmarked members with their swords back and the Shield cooling. */
private static void assertOver(GameTestHelper helper, Scene scene) {
    helper.assertTrue(TeamMoves.running(scene.player()).isEmpty(), "the move is over");
    for (BoundVex vex : scene.vexes()) {
        helper.assertValueEqual(vex.teamMove(), TeamMove.NONE, "unmarked");
        helper.assertTrue(BoundVexTestSupport.swordBack(vex), "the sword is back");
        helper.assertFalse(vex.isConjured(EquipmentSlot.OFFHAND), "the shield is gone");
        helper.assertTrue(vex.getOffhandItem().isEmpty(), "the off hand is empty again");
    }
    helper.assertTrue(TeamMoves.cooling(scene.player(), TeamMove.SHIELD), "the Shield cools down");
}

/**
 * When the move enemy dies, should the next tick end the move: kits gone, given gear back, vexes unmarked, no
 * message, the Shield cooling.
 *
 * <p>Method under test: {@link TeamMoves#tick}: the enemy-dead rule
 */
static void enemyDies(GameTestHelper helper) {
    // Arrange
    Scene scene = shield(helper);
    GameTestSupport.drainActionBar(scene.player());
    scene.husk().kill(helper.getLevel());

    // Act
    TeamMoves.tick(scene.player());

    // Assert
    assertOver(helper, scene);
    helper.assertTrue(GameTestSupport.drainActionBar(scene.player()).isEmpty(), "no message at the end");
    helper.succeed();
}

/**
 * When the move enemy is removed from the level, should the next tick end the move.
 *
 * <p>Method under test: {@link TeamMoves#tick}: the enemy-removed rule
 */
static void enemyRemoved(GameTestHelper helper) {
    // Arrange
    Scene scene = shield(helper);
    scene.husk().discard();

    // Act
    TeamMoves.tick(scene.player());

    // Assert
    assertOver(helper, scene);
    helper.succeed();
}

/**
 * When the enemy is 47 blocks from the summoner, should the move go on; at 49 blocks (straight up, where the chunk is
 * loaded) the next tick should end it.
 *
 * <p>Method under test: {@link TeamMoves#tick}: the 48-block rule
 */
static void enemyBeyond48(GameTestHelper helper) {
    // Arrange
    Scene scene = shield(helper);
    Husk husk = scene.husk();
    Vec3 flat = husk.position();

    // Act & Assert
    husk.setPos(flat.x, scene.player().getY() + 47.0, flat.z);
    TeamMoves.tick(scene.player());
    helper.assertTrue(TeamMoves.running(scene.player()).isPresent(), "47 blocks off: still running");
    husk.setPos(flat.x, scene.player().getY() + 49.0, flat.z);
    TeamMoves.tick(scene.player());
    assertOver(helper, scene);
    helper.succeed();
}

/**
 * When 2399 ticks have passed, should the move still run; one more tick and it should end (the limit is 2400).
 *
 * <p>Method under test: {@link TeamMoves#tick}: the time limit
 */
static void timeLimit(GameTestHelper helper) {
    // Arrange
    Scene scene = shield(helper);

    // Act & Assert
    TeamMoves.age(scene.player(), 2399);
    TeamMoves.tick(scene.player());
    helper.assertTrue(TeamMoves.running(scene.player()).isPresent(), "2399 ticks: still running");
    TeamMoves.age(scene.player(), 1);
    TeamMoves.tick(scene.player());
    assertOver(helper, scene);
    helper.succeed();
}

/**
 * When an Evader has run 1199 ticks, should it go on; one more and it should end (its limit is 1200) and the Evader
 * start cooling.
 *
 * <p>Method under test: {@link TeamMoves#tick}: the Evader time limit
 */
static void evaderTimeLimit(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 2);
    player.setHealth(8.0F);
    helper.assertTrue(TeamMoves.start(player, TeamMove.EVADER, player), "setup: the Evader starts");

    // Act & Assert
    TeamMoves.age(player, 1199);
    TeamMoves.tick(player);
    helper.assertTrue(TeamMoves.running(player).isPresent(), "1199 ticks: still running");
    TeamMoves.age(player, 1);
    TeamMoves.tick(player);
    helper.assertTrue(TeamMoves.running(player).isEmpty(), "1200 ticks: over");
    helper.assertTrue(TeamMoves.cooling(player, TeamMove.EVADER), "the Evader cools down");
    helper.succeed();
}

/**
 * When the ward of an Evader is healed to half health, should the next tick end the move.
 *
 * <p>Method under test: {@link TeamMoves#tick}: the ward-healed rule
 */
static void wardHealed(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    List<BoundVex> vexes = frozenVexes(helper, player, 2);
    player.setHealth(8.0F);
    helper.assertTrue(TeamMoves.start(player, TeamMove.EVADER, player), "setup: the Evader starts");
    TeamMoves.tick(player);
    helper.assertTrue(TeamMoves.running(player).isPresent(), "setup: still hurt, still running");
    player.setHealth(10.0F);

    // Act
    TeamMoves.tick(player);

    // Assert
    helper.assertTrue(TeamMoves.running(player).isEmpty(), "healed to half: over");
    helper.assertTrue(vexes.stream().allMatch(vex -> vex.teamMove() == TeamMove.NONE), "unmarked");
    helper.succeed();
}

/**
 * When every member dies, should the next tick end the move with the husk still alive.
 *
 * <p>Method under test: {@link TeamMoves#tick}: the empty-roster rule
 */
static void rosterEmpty(GameTestHelper helper) {
    // Arrange
    Scene scene = shield(helper);
    scene.vexes().forEach(vex -> vex.kill(helper.getLevel()));

    // Act
    TeamMoves.tick(scene.player());

    // Assert
    helper.assertTrue(TeamMoves.running(scene.player()).isEmpty(), "no members, no move");
    helper.assertTrue(scene.husk().isAlive(), "the enemy lives");
    helper.assertTrue(TeamMoves.cooling(scene.player(), TeamMove.SHIELD), "the Shield cools down");
    helper.succeed();
}

/**
 * When the summoner dies (a dead player is still ticked), should the next tick end the move.
 *
 * <p>Method under test: {@link TeamMoves#tick}: the summoner-absent rule
 */
static void summonerDies(GameTestHelper helper) {
    // Arrange
    Scene scene = shield(helper);
    scene.player().kill(helper.getLevel());
    helper.assertFalse(scene.player().isAlive(), "setup: the summoner is dead");

    // Act
    TeamMoves.tick(scene.player());

    // Assert
    assertOver(helper, scene);
    helper.succeed();
}

/**
 * When the summoner turns spectator, should the next tick end the move.
 *
 * <p>Method under test: {@link TeamMoves#tick}: the summoner-absent rule
 */
static void summonerSpectates(GameTestHelper helper) {
    // Arrange
    Scene scene = shield(helper);
    scene.player().setGameMode(GameType.SPECTATOR);

    // Act
    TeamMoves.tick(scene.player());

    // Assert
    assertOver(helper, scene);
    helper.succeed();
}

/**
 * When the summoner moves to the Nether mid-move, should the next tick put every member's kit away (the members stay
 * behind in the test level) and end the move.
 *
 * <p>Method under test: {@link TeamMoves#tick}: members in another level leave the roster
 */
static void summonerChangesLevel(GameTestHelper helper) {
    // Arrange
    Scene scene = shield(helper);
    BoundVexWorldTestSupport.room(helper, scene.player(), Level.NETHER, 3, 4);

    // Act
    TeamMoves.tick(scene.player());

    // Assert
    assertOver(helper, scene);
    helper.succeed();
}

/**
 * When a Shield has ended, should the same trigger not start another Shield, the cooldown being per kind, and an
 * Evader still be able to start.
 *
 * <p>Method under test: {@link TeamMoves#tick}, {@link TeamMoves#start}: the 30-second cooldown
 */
static void cooldownPerKind(GameTestHelper helper) {
    // Arrange
    Scene scene = shield(helper);
    scene.husk().kill(helper.getLevel());
    TeamMoves.tick(scene.player());
    assertOver(helper, scene);
    Husk another = bigHusk(helper, hangPoint(helper).add(3.0, 0.0, 1.0));
    scene.player().setHealth(8.0F);

    // Act & Assert
    helper.assertFalse(TeamMoves.start(scene.player(), TeamMove.SHIELD, another), "the same kind is still cooling");
    helper.assertFalse(TeamMoves.cooling(scene.player(), TeamMove.EVADER), "another kind is not");
    helper.assertTrue(TeamMoves.start(scene.player(), TeamMove.EVADER, scene.player()), "an Evader starts");
    helper.succeed();
}

/**
 * When the summoner logs out mid-move, should every member's kit be dismissed, its mark cleared and its record
 * dropped, with no cooldown remembered.
 *
 * <p>Method under test: {@link TeamMoves#onLogout}
 */
static void logoutReleasesKits(GameTestHelper helper) {
    // Arrange
    Scene scene = shield(helper);

    // Act
    TeamMoves.onLogout(new PlayerEvent.PlayerLoggedOutEvent(scene.player()));

    // Assert
    helper.assertTrue(TeamMoves.running(scene.player()).isEmpty(), "the record is gone");
    for (BoundVex vex : scene.vexes()) {
        helper.assertValueEqual(vex.teamMove(), TeamMove.NONE, "unmarked");
        helper.assertTrue(BoundVexTestSupport.swordBack(vex), "the sword is back");
        helper.assertFalse(vex.isConjured(EquipmentSlot.OFFHAND), "the shield is gone");
    }
    helper.assertFalse(TeamMoves.cooling(scene.player(), TeamMove.SHIELD), "a logout starts no cooldown");
    helper.succeed();
}

/**
 * When a member dies, should it drop its given sword but no shield, and the next tick should take it out of the
 * roster while the move goes on.
 *
 * <p>Method under test: {@link TeamMoves#tick}: pruning the roster; {@code BoundVex#dropCustomDeathLoot}
 */
static void deadMemberDropsNoKit(GameTestHelper helper) {
    // Arrange
    Scene scene = shield(helper);
    BoundVex dying = scene.vexes().get(0);
    AABB around = new AABB(dying.position(), dying.position()).inflate(3.0);
    GameTestSupport.whenDone(
            helper, () -> helper.getLevel().getEntitiesOfClass(ItemEntity.class, around).forEach(ItemEntity::discard));

    // Act
    dying.kill(helper.getLevel());
    TeamMoves.tick(scene.player());

    // Assert
    List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, around);
    helper.assertTrue(items.stream().anyMatch(item -> item.getItem().is(Items.IRON_SWORD)), "the given sword drops");
    helper.assertFalse(items.stream().anyMatch(item -> item.getItem().is(Items.SHIELD)), "no shield drops");
    helper.assertValueEqual(TeamMoves.roster(scene.player()).size(), 2, "the dead member left the roster");
    helper.assertTrue(TeamMoves.running(scene.player()).isPresent(), "and the move goes on");
    helper.succeed();
}

/**
 * When a vex is on the roster, should its goal run; a vex that is not should not.
 *
 * <p>Method under test: {@link TeamMoveGoal#canUse}
 */
static void goalRunsForAListedMember(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    List<BoundVex> vexes = frozenVexes(helper, player, 4);
    helper.assertTrue(
            TeamMoves.start(player, TeamMove.SHIELD, bigHusk(helper, hangPoint(helper).add(3.0, 0.0, 0.0))), "setup");
    BoundVex member = TeamMoves.roster(player).get(0);
    BoundVex left = vexes.stream().filter(vex -> vex.teamMove() == TeamMove.NONE).findFirst().orElseThrow();

    // Act & Assert
    helper.assertTrue(TeamMoveTestSupport.goalOf(member).canUse(), "a listed member runs the goal");
    helper.assertFalse(TeamMoveTestSupport.goalOf(left).canUse(), "an unlisted vex does not");
    helper.succeed();
}

/**
 * When a goal stops while the record still lists the vex (a rescue took the move control), should the kit stay.
 *
 * <p>Method under test: {@link TeamMoveGoal#stop}
 */
static void goalStopKeepsTheKitWhileListed(GameTestHelper helper) {
    // Arrange
    Scene scene = shield(helper);
    BoundVex member = scene.vexes().get(0);

    // Act
    TeamMoveTestSupport.goalOf(member).stop();

    // Assert
    helper.assertValueEqual(member.teamMove(), TeamMove.SHIELD, "still marked");
    helper.assertTrue(member.isConjured(EquipmentSlot.MAINHAND), "the kit stays");
    helper.succeed();
}

/**
 * When a rescue's dismiss gave a member its sword back, should the next goal tick conjure the shield again and stash
 * the sword again.
 *
 * <p>Method under test: {@link TeamMoveGoal#tick}: putting the kit back
 */
static void goalPutsAReplacedItemBack(GameTestHelper helper) {
    // Arrange
    Scene scene = shield(helper);
    BoundVex member = scene.vexes().get(0);
    member.dismiss(EquipmentSlot.MAINHAND);
    helper.assertTrue(BoundVexTestSupport.swordBack(member), "setup: the sword is back in the hand");

    // Act
    TeamMoveTestSupport.goalOf(member).tick();

    // Assert
    helper.assertTrue(
            member.isConjured(EquipmentSlot.MAINHAND) && member.getMainHandItem().is(Items.SHIELD), "the shield is back");
    helper.assertTrue(member.stashed(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD), "and the sword is stashed again");
    helper.succeed();
}

/**
 * When the server stops, should every record be dropped, the goal of a former member refuse to run, and its stop
 * dismiss the kit the record can no longer look after.
 *
 * <p>Method under test: {@code BoundVexEvents.onServerStopped}, {@link TeamMoves#clear}, {@link TeamMoveGoal#stop}
 */
static void serverStopClearsRecords(GameTestHelper helper) {
    // Arrange
    Scene scene = shield(helper);
    BoundVex member = scene.vexes().get(0);

    // Act
    BoundVexEvents.onServerStopped(new ServerStoppedEvent(helper.getLevel().getServer()));

    // Assert
    helper.assertTrue(TeamMoves.running(scene.player()).isEmpty(), "no record is left");
    helper.assertFalse(TeamMoveTestSupport.goalOf(member).canUse(), "the goal no longer runs");
    TeamMoveTestSupport.goalOf(member).stop();
    helper.assertValueEqual(member.teamMove(), TeamMove.NONE, "its stop unmarks the vex");
    helper.assertTrue(BoundVexTestSupport.swordBack(member), "and puts the sword back");
    helper.succeed();
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:team_move_end_*"`
Expected: compilation fails (`TeamMoves.tick`, `cooling`, `age`, `onLogout`, `TeamMoveGoal` do not exist).

- [ ] **Step 3: Implement**

`TeamMoves` gets (the class gains `@EventBusSubscriber(modid = Spellcraft.MODID)`):

```java
/** A vex's place in a running move: the record and its own member state. */
record Membership(Record record, Member member) {}

/**
 * Runs {@link #tick} after each server player's tick (a dead player is still ticked until it respawns).
 *
 * @param event the post-tick event
 */
@SubscribeEvent
public static void onPlayerTick(PlayerTickEvent.Post event) {
    if (event.getEntity() instanceof ServerPlayer player) {
        tick(player);
    }
}

/**
 * One step for {@code owner} (spec §2.5): drops roster members that are gone or no longer marked, and ends the move
 * when its roster is empty, the summoner is absent, the enemy or ward is gone or beyond 48 blocks, the Evader's ward
 * is healed, or the time limit is up. Public so GameTests can step a mock player, which gets no player tick.
 *
 * @param owner the summoner
 */
public static void tick(ServerPlayer owner) {
    long now = owner.level().getGameTime();
    Record record = STATES.computeIfAbsent(owner.getUUID(), id -> new Record());
    if (record.active()) {
        prune(owner, record);
        if (shouldEnd(owner, record, now)) {
            end(owner, record, now);
        }
    }
}

/** Whether the summoner can lead a move: alive and not a spectator (spec §2.3). */
static boolean isPresent(ServerPlayer owner) {
    return owner.isAlive() && !owner.isSpectator();
}

/**
 * Drops the members that are dead, removed, in another level or no longer marked with this move. A member found alive
 * and still marked in another level has its kit put away now.
 */
private static void prune(ServerPlayer owner, Record record) {
    ServerLevel level = owner.level();
    record.roster.removeIf(id -> {
        Entity found = level.getEntityInAnyDimension(id);
        if (found instanceof BoundVex vex && vex.isAlive() && vex.teamMove() == record.move) {
            if (vex.level().equals(level)) {
                return false;
            }
            release(vex);
        }
        record.members.remove(id);
        return true;
    });
}

private static boolean shouldEnd(ServerPlayer owner, Record record, long now) {
    if (record.roster.isEmpty() || !isPresent(owner) || now >= TeamMoveRules.endsAt(record.move, record.start)) {
        return true;
    }
    Entity subject = record.subject == null ? null : owner.level().getEntity(record.subject);
    if (!(subject instanceof LivingEntity living)
            || !living.isAlive()
            || TeamMoveRules.tooFar(living.distanceToSqr(owner))) {
        return true;
    }
    return record.move == TeamMove.EVADER && !TeamMoveRules.needsGuard(living.getHealth(), living.getMaxHealth());
}

/** Ends the move: every remaining member's kit goes, its mark clears, and the kind's 30-second cooldown starts. */
private static void end(ServerPlayer owner, Record record, long now) {
    releaseRoster(owner.level(), record);
    record.finish(now + TeamMoveRules.COOLDOWN_TICKS);
}

private static void releaseRoster(ServerLevel level, Record record) {
    for (UUID id : record.roster) {
        if (level.getEntityInAnyDimension(id) instanceof BoundVex vex && vex.teamMove() == record.move) {
            release(vex);
        }
    }
}

/**
 * Takes a vex out of its move: the kit slots are dismissed (the stash returns) and the mark clears.
 *
 * @param vex a Bound Vex
 */
static void release(BoundVex vex) {
    TeamMoveKits.dismiss(vex, vex.teamMove());
    vex.setTeamMove(TeamMove.NONE);
}

/**
 * The vex's place in the record that lists it.
 *
 * @param vex a Bound Vex
 * @return the record and member state, or empty for a vex in no running move
 */
static Optional<Membership> membership(BoundVex vex) {
    TeamMove mark = vex.teamMove();
    if (mark == TeamMove.NONE) {
        return Optional.empty();
    }
    return vex.summonerId()
            .map(STATES::get)
            .filter(record -> record.move == mark && record.members.containsKey(vex.getUUID()))
            .map(record -> new Membership(record, record.members.get(vex.getUUID())));
}

/**
 * Whether a move kind is cooling down for {@code owner}.
 *
 * @param owner the summoner
 * @param move the kind
 * @return true during the 30 seconds after the kind last ended
 */
public static boolean cooling(ServerPlayer owner, TeamMove move) {
    Record record = STATES.get(owner.getUUID());
    return record != null && record.cooling(move, owner.level().getGameTime());
}

/**
 * Makes the running move {@code ticks} older; for GameTests, never called in play.
 *
 * @param owner the summoner
 * @param ticks how much older the move becomes
 */
public static void age(ServerPlayer owner, long ticks) {
    Record record = STATES.get(owner.getUUID());
    if (record != null) {
        record.start -= ticks;
    }
}

/**
 * Drops a logged-out summoner's record and puts every member's kit away, so no vex stays marked with nobody to end its
 * move. No cooldown is remembered.
 *
 * @param event the logout event
 */
@SubscribeEvent
public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
    if (event.getEntity() instanceof ServerPlayer owner) {
        Record record = STATES.remove(owner.getUUID());
        if (record != null && record.active()) {
            releaseRoster(owner.level(), record);
        }
    }
}

/** Forgets every record (single-player loads one server per world in the same JVM). */
static void clear() {
    STATES.clear();
}
```

`TeamMoveGoal`:

```java
package io.github.willrieske.spellcraft.evoking;

/**
 * A Bound Vex's part in a team move (Parts 6–7 spec §2.5). Priority 1 on {@code MOVE} and {@code LOOK}: above the job,
 * charge, ranged and follow goals, below the flee and dodge goals (0) and the rescue (−1), which pause it. While
 * {@link TeamMoves#membership} lists the vex it keeps the kit on ({@link TeamMoveKits#restore}: a rescue's bucket or
 * apple dismisses back to the given gear, and the kit is conjured again when the vex returns). Its {@code stop}
 * dismisses the kit only when the record no longer lists the vex, so a pause keeps the kit. Each move's own behavior
 * is added to {@link #tick} by the tasks that build the moves.
 */
public final class TeamMoveGoal extends Goal {
    private final BoundVex vex;

    TeamMoveGoal(BoundVex vex) {
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return TeamMoves.membership(vex).isPresent();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        TeamMoves.membership(vex).ifPresent(membership -> TeamMoveKits.restore(vex, membership.member().kit));
    }

    @Override
    public void tick() {
        TeamMoves.Membership membership = TeamMoves.membership(vex).orElse(null);
        if (membership == null || vex.summoner().isEmpty()) {
            return;
        }
        TeamMoveKits.restore(vex, membership.member().kit);
    }

    @Override
    public void stop() {
        if (TeamMoves.membership(vex).isEmpty()) {
            TeamMoves.release(vex);
        }
    }
}
```

`BoundVex.registerGoals`: add `goalSelector.addGoal(1, new TeamMoveGoal(this));` after the priority-0 goals, and add "{@link TeamMoveGoal} (priority 1) runs a team move's part" to its Javadoc. `BoundVexEvents.onServerStopped` gets `TeamMoves.clear();` after `VexRescues.clear();` and "every team-move record" in its Javadoc list.

- [ ] **Step 4: Run the tests**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:team_move_end_*"` then `"…team_move_goal_*"`.
Expected: all pass. If `enemyBeyond48` fails at 47, read the husk's horizontal offset: the test places it 3 blocks from the hang point and the summoner stands under it, so 47 up is about 47.1 away; move the husk to the summoner's x and z if a different structure position changed that.

- [ ] **Step 5: Full gates, then commit**

Run: `./gradlew spotlessApply build`, then `./gradlew runGameTestServer` twice (previous total + 17).

```bash
git add src/main/java/io/github/willrieske/spellcraft
git commit -m "feat(evoking): team moves run, end by the spec's rules and keep the kit on"
```

---

### Task 6: The setting and the automatic triggers

**Files:**

- Create: `config/TeamMoveMode.java`, `evoking/TeamMoveTriggers.java`, `gametest/BoundVexTeamMoveTriggerGameTests.java`
- Modify: `config/ServerConfig.java`, `evoking/TeamMoves.java` (the scan in `tick`, `attackDamage`, `wardFor`), `gametest/TeamMoveTestSupport.java`, `gametest/SpellcraftGameTests.java`, `assets/spellcraft/lang/en_us.json`

**Interfaces:**

- Consumes: `TeamMoveRules.pick/rosterSize/needsGuard`, `TeamMoves.start/freeVexes`, `BoundVex.huntable(UUID, LivingEntity)`, `BoundVex.isPrey(ServerLevel, UUID, LivingEntity)`, `BoundVex.guardArea/inGuardArea`.
- Produces:
  - `TeamMoveMode { AUTOMATIC, MANUAL }`; `ServerConfig.TEAM_MOVES` (`ModConfigSpec.EnumValue<TeamMoveMode>`, key `teamMoves` in section `evoking`, default `AUTOMATIC`); `ServerConfig.teamMoveMode()`.
  - `TeamMoves.SCAN_INTERVAL = 10`; package-private `static double attackDamage(LivingEntity)` (0 when the mob has no `ATTACK_DAMAGE`); package-private `static @Nullable LivingEntity wardFor(ServerPlayer owner)`.
  - `TeamMoveTriggers.scan(ServerPlayer, TeamMoves.Record, long now)` (package-private).
  - `TeamMoveTestSupport.assertRaidGear(GameTestHelper, BoundVex)`.

- [ ] **Step 1: Write the failing tests**

Add to `TeamMoveTestSupport`:

```java
/** Fails unless {@code vex} holds a conjured main-hand item, a conjured shield and three conjured armor pieces. */
static void assertRaidGear(GameTestHelper helper, BoundVex vex) {
    helper.assertTrue(vex.isConjured(EquipmentSlot.MAINHAND), "a conjured weapon");
    helper.assertTrue(vex.isConjured(EquipmentSlot.OFFHAND) && vex.getOffhandItem().is(Items.SHIELD), "a shield");
    for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS)) {
        helper.assertTrue(
                vex.isConjured(slot) && VexGear.kindOf(vex.getItemBySlot(slot)) == VexGear.Kind.ARMOR, slot + " armor");
    }
}
```

`BoundVexTeamMoveTriggerGameTests` (all `FangGameTests.addPadded(SHARED, …)` except the last, `config_mutation`; each test steps `TeamMoves.tick(player)` once; the foe stands 3 blocks east of the hang point, inside the guard area of every frozen vex):

```java
private static Vec3 near(GameTestHelper helper) {
    return hangPoint(helper).add(3.0, 0.0, 0.0);
}

/**
 * When twelve vexes are free and a ravager (100 health) stands in their guard area, should a Raid start with all
 * twelve, each geared.
 *
 * <p>Method under test: {@code TeamMoveTriggers.scan}: the Raid trigger
 */
static void ravagerWithTwelveRaids(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 12);
    foe(helper, EntityTypes.RAVAGER, near(helper));

    // Act
    TeamMoves.tick(player);

    // Assert
    helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.RAID), "a Raid starts");
    helper.assertValueEqual(TeamMoves.roster(player).size(), 12, "with all twelve");
    TeamMoves.roster(player).forEach(vex -> TeamMoveTestSupport.assertRaidGear(helper, vex));
    helper.succeed();
}

/**
 * When only four vexes are free beside a ravager, should a Shield start with three.
 *
 * <p>Method under test: {@code TeamMoveTriggers.scan}: the Raid fall-through to a Shield
 */
static void ravagerWithFourShields(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 4);
    foe(helper, EntityTypes.RAVAGER, near(helper));

    // Act
    TeamMoves.tick(player);

    // Assert
    helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.SHIELD), "a Shield starts");
    helper.assertValueEqual(TeamMoves.roster(player).size(), 3, "with three");
    helper.succeed();
}

/**
 * When eleven vexes are free beside a ravager (one short of a Raid), should a Shield start with nine.
 *
 * <p>Method under test: {@code TeamMoveTriggers.scan}: the Raid fall-through to a Shield
 */
static void ravagerWithElevenShields(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 11);
    foe(helper, EntityTypes.RAVAGER, near(helper));

    // Act
    TeamMoves.tick(player);

    // Assert
    helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.SHIELD), "a Shield starts");
    helper.assertValueEqual(TeamMoves.roster(player).size(), 9, "with nine");
    helper.succeed();
}

/**
 * When nine vexes are free beside an enderman (40 health), should a Shield start with nine.
 *
 * <p>Method under test: {@code TeamMoveTriggers.scan}: the Shield trigger
 */
static void endermanWithNineShields(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 9);
    foe(helper, EntityTypes.ENDERMAN, near(helper));

    // Act
    TeamMoves.tick(player);

    // Assert
    helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.SHIELD), "a Shield starts");
    helper.assertValueEqual(TeamMoves.roster(player).size(), 9, "with nine");
    helper.succeed();
}

/**
 * When a vindicator (attack damage 5) hunts a free vex, should no move start.
 *
 * <p>Method under test: {@code TeamMoveTriggers.scan}: the Multishot damage threshold
 */
static void vindicatorStartsNothing(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    List<BoundVex> vexes = frozenVexes(helper, player, 3);
    foe(helper, EntityTypes.VINDICATOR, near(helper)).setTarget(vexes.get(0));

    // Act
    TeamMoves.tick(player);

    // Assert
    helper.assertTrue(TeamMoves.running(player).isEmpty(), "attack damage 5 earns no move");
    helper.succeed();
}

/**
 * When a husk with attack damage 6 hunts a free vex, should a Multishot start with that vex in its roster of three.
 *
 * <p>Method under test: {@code TeamMoveTriggers.scan}: the Multishot trigger
 */
static void strongHunterStartsAMultishot(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    List<BoundVex> vexes = frozenVexes(helper, player, 4);
    Husk husk = foe(helper, EntityTypes.HUSK, near(helper));
    setBase(husk, Attributes.ATTACK_DAMAGE, 6.0);
    husk.setTarget(vexes.get(0));

    // Act
    TeamMoves.tick(player);

    // Assert
    helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.MULTISHOT), "a Multishot starts");
    helper.assertValueEqual(TeamMoves.roster(player).size(), 3, "with three");
    helper.assertTrue(TeamMoves.roster(player).contains(vexes.get(0)), "the hunted vex is in");
    helper.succeed();
}

/**
 * When a husk with attack damage 6 hunts nobody, should no move start.
 *
 * <p>Method under test: {@code TeamMoveTriggers.scan}: Multishot needs a hunted free vex
 */
static void strongHunterOfNobodyStartsNothing(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 4);
    setBase(foe(helper, EntityTypes.HUSK, near(helper)), Attributes.ATTACK_DAMAGE, 6.0);

    // Act
    TeamMoves.tick(player);

    // Assert
    helper.assertTrue(TeamMoves.running(player).isEmpty(), "no free vex is hunted");
    helper.succeed();
}

/**
 * When the summoner is at 40 % health with five free vexes and no enemy, should an Evader start with three, warding the
 * summoner.
 *
 * <p>Method under test: {@code TeamMoveTriggers.scan}: the Evader trigger for the summoner
 */
static void hurtSummonerStartsAnEvader(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 5);
    player.setHealth(player.getMaxHealth() * 0.4F);

    // Act
    TeamMoves.tick(player);

    // Assert
    helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.EVADER), "an Evader starts");
    helper.assertValueEqual(TeamMoves.roster(player).size(), 3, "with three");
    helper.assertValueEqual(TeamMoves.subject(player), Optional.of(player.getUUID()), "warding the summoner");
    helper.succeed();
}

/**
 * When a villager within 16 blocks is at 40 % health, should an Evader start warding it.
 *
 * <p>Method under test: {@code TeamMoveTriggers.scan}: the Evader trigger for an ally
 */
static void hurtVillagerStartsAnEvader(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 3);
    Villager villager = foe(helper, EntityTypes.VILLAGER, hangPoint(helper).add(2.0, -3.0, 0.0));
    villager.setHealth(villager.getMaxHealth() * 0.4F);

    // Act
    TeamMoves.tick(player);

    // Assert
    helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.EVADER), "an Evader starts");
    helper.assertValueEqual(TeamMoves.subject(player), Optional.of(villager.getUUID()), "warding the villager");
    helper.succeed();
}

/**
 * When one of the summoner's own vexes is at 40 % health, should no Evader start (a vex is never warded).
 *
 * <p>Method under test: {@code TeamMoves.wardFor}: no summoner-marked mob
 */
static void hurtVexIsNotWarded(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    List<BoundVex> vexes = frozenVexes(helper, player, 3);
    vexes.get(0).setHealth(vexes.get(0).getMaxHealth() * 0.4F);

    // Act
    TeamMoves.tick(player);

    // Assert
    helper.assertTrue(TeamMoves.running(player).isEmpty(), "no Evader for a hurt vex");
    helper.succeed();
}

/**
 * When a ravager stands beside twelve free vexes and the summoner is hurt, should the Raid win over the Evader (the
 * enemy side comes first).
 *
 * <p>Method under test: {@code TeamMoveTriggers.scan}: the order of the triggers
 */
static void enemyMovesComeBeforeTheEvader(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 12);
    foe(helper, EntityTypes.RAVAGER, near(helper));
    player.setHealth(player.getMaxHealth() * 0.4F);

    // Act
    TeamMoves.tick(player);

    // Assert
    helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.RAID), "the Raid wins");
    helper.succeed();
}

/**
 * When a plain husk (20 health, attack damage 3) stands in the guard area, should no move start.
 *
 * <p>Method under test: {@code TeamMoveTriggers.scan}: the thresholds
 */
static void plainHuskStartsNothing(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 4);
    foe(helper, EntityTypes.HUSK, near(helper));

    // Act
    TeamMoves.tick(player);

    // Assert
    helper.assertTrue(TeamMoves.running(player).isEmpty(), "nothing earns a move");
    helper.succeed();
}

/**
 * When a ravager stands ten blocks from the vexes (outside every guard area), should no move start.
 *
 * <p>Method under test: {@code TeamMoveTriggers.scan}: candidates are inside a following vex's guard area
 */
static void enemyOutsideTheGuardAreaStartsNothing(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 4);
    foe(helper, EntityTypes.RAVAGER, hangPoint(helper).add(10.0, 0.0, 0.0));

    // Act
    TeamMoves.tick(player);

    // Assert
    helper.assertTrue(TeamMoves.running(player).isEmpty(), "out of reach, out of mind");
    helper.succeed();
}

/**
 * When one of three vexes stays, should a ravager start no move (two free vexes) and a staying vex never be taken.
 *
 * <p>Method under test: {@code TeamMoveTriggers.scan}: staying vexes are not free
 */
static void stayingVexIsNotTaken(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 3).get(1).setStaying(true);
    foe(helper, EntityTypes.RAVAGER, near(helper));

    // Act
    TeamMoves.tick(player);

    // Assert
    helper.assertTrue(TeamMoves.running(player).isEmpty(), "two free vexes are too few for a Shield");
    helper.succeed();
}

/**
 * When the scan found nothing this tick and a husk of 40 health appears, should a second step in the same tick not scan
 * again and one ten ticks later start the Shield.
 *
 * <p>Method under test: {@code TeamMoves.tick}: the 10-tick scan clock
 */
static void scanClockIsTenTicks(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 3);
    TeamMoves.tick(player);
    bigHusk(helper, near(helper));

    // Act
    TeamMoves.tick(player);

    // Assert
    helper.assertTrue(TeamMoves.running(player).isEmpty(), "the clock is closed within the same tick");
    helper.startSequence()
            .thenExecuteAfter(10, () -> {
                TeamMoves.tick(player);
                helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.SHIELD), "ten ticks later");
            })
            .thenSucceed();
}

/**
 * When the setting is manual, should the scan not run: a ravager beside twelve vexes starts nothing.
 *
 * <p>Method under test: {@code TeamMoves.tick}: the {@code teamMoves} setting
 */
static void manualModeDoesNotScan(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 12);
    foe(helper, EntityTypes.RAVAGER, near(helper));

    // Act
    GameTestSupport.withConfig(ServerConfig.TEAM_MOVES, TeamMoveMode.MANUAL, () -> TeamMoves.tick(player));

    // Assert
    helper.assertTrue(TeamMoves.running(player).isEmpty(), "manual mode starts nothing by itself");
    helper.succeed();
}
```

Names: `team_move_trigger_ravager_with_twelve_raids`, `…_ravager_with_four_shields`, `…_ravager_with_eleven_shields`, `…_enderman_with_nine_shields`, `…_vindicator_starts_nothing`, `…_strong_hunter_starts_a_multishot`, `…_strong_hunter_of_nobody_starts_nothing`, `…_hurt_summoner_starts_an_evader`, `…_hurt_villager_starts_an_evader`, `…_hurt_vex_is_not_warded`, `…_enemy_moves_come_before_the_evader`, `…_plain_husk_starts_nothing`, `…_enemy_outside_the_guard_area_starts_nothing`, `…_staying_vex_is_not_taken`, `…_scan_clock_is_ten_ticks`, and `team_move_trigger_manual_mode_does_not_scan` registered with `FangGameTests.addPadded("config_mutation", …)` (it changes the global setting for one synchronous call).

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:team_move_trigger_*"`
Expected: compilation fails (`ServerConfig.TEAM_MOVES`, `TeamMoveMode` do not exist) and, once they do, every trigger test fails (nothing scans).

- [ ] **Step 3: Implement**

```java
package io.github.willrieske.spellcraft.config;

/** Whether Bound Vexes start team moves by themselves (Parts 6–7 spec §3.1). */
public enum TeamMoveMode {
    /** The vexes start team moves when a trigger fires; the J and K keys only say so. */
    AUTOMATIC,
    /** No trigger runs; J picks a move and K starts it. */
    MANUAL
}
```

`ServerConfig`: add the field `public static final ModConfigSpec.EnumValue<TeamMoveMode> TEAM_MOVES;`, in the static block before `SPEC = builder.build();`:

```java
builder.push("evoking");
TEAM_MOVES = builder.comment("AUTOMATIC: Bound Vexes start team moves (Shield, Multishot, Evader, Raid) by themselves. "
                + "MANUAL: only the J key (pick) and the K key (start) start them.")
        .defineEnum("teamMoves", TeamMoveMode.AUTOMATIC);
builder.pop();
```

and the accessor:

```java
/** Whether the automatic team-move triggers run. */
public static TeamMoveMode teamMoveMode() {
    return TEAM_MOVES.get();
}
```

Lang: `"spellcraft.configuration.evoking": "Strength of Evoking"`, `"spellcraft.configuration.teamMoves": "Team moves"`.

`TeamMoves` gets the constants and helpers, and `tick` gets the scan:

```java
/** The scan for triggers runs once every this many ticks (spec §2.3). */
public static final int SCAN_INTERVAL = 10;

private static final double WARD_RANGE = 16.0;

public static void tick(ServerPlayer owner) {
    long now = owner.level().getGameTime();
    Record record = STATES.computeIfAbsent(owner.getUUID(), id -> new Record());
    if (record.active()) {
        prune(owner, record);
        if (shouldEnd(owner, record, now)) {
            end(owner, record, now);
        }
    }
    if (!record.active()
            && ServerConfig.teamMoveMode() == TeamMoveMode.AUTOMATIC
            && isPresent(owner)
            && now >= record.nextScan) {
        record.nextScan = now + SCAN_INTERVAL;
        TeamMoveTriggers.scan(owner, record, now);
    }
}

/** An entity's attack damage, 0 for one with no such attribute (ghasts, shulkers, the dragon). */
static double attackDamage(LivingEntity entity) {
    AttributeInstance damage = entity.getAttribute(Attributes.ATTACK_DAMAGE);
    return damage == null ? 0.0 : damage.getValue();
}

/**
 * The one an Evader would protect (spec §2.3): the summoner, or a living ally within 16 blocks of them, below half
 * health. Never a mob carrying a summoner mark (a Bound Vex or an image). The lowest health fraction wins; the summoner
 * wins ties.
 */
static @Nullable LivingEntity wardFor(ServerPlayer owner) {
    List<LivingEntity> hurt = new ArrayList<>();
    if (isPresent(owner) && TeamMoveRules.needsGuard(owner.getHealth(), owner.getMaxHealth())) {
        hurt.add(owner);
    }
    hurt.addAll(owner.level()
            .getEntitiesOfClass(
                    LivingEntity.class,
                    owner.getBoundingBox().inflate(WARD_RANGE),
                    ally -> ally != owner
                            && ally.isAlive()
                            && ally.distanceTo(owner) <= WARD_RANGE
                            && ally.getExistingData(AllyAttachments.SUMMONER).isEmpty()
                            && Allies.isAllyOf(owner, ally)
                            && TeamMoveRules.needsGuard(ally.getHealth(), ally.getMaxHealth())));
    return hurt.stream()
            .min(Comparator.comparingDouble((LivingEntity ally) -> ally.getHealth() / ally.getMaxHealth())
                    .thenComparingInt(ally -> ally == owner ? 0 : 1))
            .orElse(null);
}
```

`TeamMoveTriggers`:

```java
package io.github.willrieske.spellcraft.evoking;

/**
 * The automatic triggers of the team moves (Parts 6–7 spec §2.3). One scan, every {@link TeamMoves#SCAN_INTERVAL}
 * ticks per summoner, while no move runs. Candidates are hostiles that are huntable and prey for the summoner and whose
 * feet are inside the guard area of at least one following vex. In order the first move that can start wins: Raid,
 * Shield, Multishot (each against the nearest candidate that earns it), then Evader. Multishot is detected here, from
 * {@code getTarget()}, not from the target event: a brain mob (a piglin brute, the warden) writes its target without
 * always firing it.
 */
final class TeamMoveTriggers {
    /** The enemy-side moves in trigger order. */
    private static final List<TeamMove> ENEMY_MOVES = List.of(TeamMove.RAID, TeamMove.SHIELD, TeamMove.MULTISHOT);

    private TeamMoveTriggers() {}

    /**
     * Looks for a trigger and starts the first move that can start.
     *
     * @param owner the summoner, present
     * @param record their record, no move running
     * @param now the game time
     */
    static void scan(ServerPlayer owner, TeamMoves.Record record, long now) {
        List<BoundVex> followers = owner.level()
                .getEntitiesOfClass(
                        BoundVex.class,
                        owner.getBoundingBox().inflate(TeamMoves.FREE_RANGE),
                        vex -> vex.isAlive() && vex.isSummonedBy(owner.getUUID()) && !vex.isStaying());
        if (followers.isEmpty()) {
            return;
        }
        List<BoundVex> free = TeamMoves.freeVexes(owner);
        boolean raidReady = ready(record, TeamMove.RAID, free.size(), now);
        boolean shieldReady = ready(record, TeamMove.SHIELD, free.size(), now);
        boolean multishotReady = ready(record, TeamMove.MULTISHOT, free.size(), now);
        List<LivingEntity> candidates = candidates(owner, followers);
        for (TeamMove move : ENEMY_MOVES) {
            for (LivingEntity candidate : candidates) {
                boolean targetsFreeVex = candidate instanceof Mob mob
                        && mob.getTarget() instanceof BoundVex hunted
                        && free.contains(hunted);
                TeamMove picked = TeamMoveRules.pick(
                        candidate.getMaxHealth(),
                        TeamMoves.attackDamage(candidate),
                        targetsFreeVex,
                        raidReady,
                        shieldReady,
                        multishotReady);
                if (picked == move && TeamMoves.start(owner, move, candidate)) {
                    return;
                }
            }
        }
        if (ready(record, TeamMove.EVADER, free.size(), now)) {
            LivingEntity ward = TeamMoves.wardFor(owner);
            if (ward != null) {
                TeamMoves.start(owner, TeamMove.EVADER, ward);
            }
        }
    }

    /** Whether {@code move} could start: not cooling and enough free vexes. */
    private static boolean ready(TeamMoves.Record record, TeamMove move, int free, long now) {
        return !record.cooling(move, now) && TeamMoveRules.rosterSize(move, free) > 0;
    }

    /** The hostiles in any follower's guard area, nearest the summoner first, each once. */
    private static List<LivingEntity> candidates(ServerPlayer owner, List<BoundVex> followers) {
        ServerLevel level = owner.level();
        Map<UUID, LivingEntity> found = new LinkedHashMap<>();
        for (BoundVex vex : followers) {
            for (LivingEntity entity : level.getEntitiesOfClass(
                    LivingEntity.class,
                    vex.guardArea(),
                    candidate -> candidate.isAlive()
                            && vex.inGuardArea(candidate)
                            && BoundVex.huntable(owner.getUUID(), candidate)
                            && BoundVex.isPrey(level, owner.getUUID(), candidate))) {
                found.putIfAbsent(entity.getUUID(), entity);
            }
        }
        return found.values().stream()
                .sorted(Comparator.comparingDouble(owner::distanceToSqr))
                .toList();
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:team_move_trigger_*"`
Expected: all pass. A trigger test that fails because the foe is "not prey" means it stands outside the summoner's reach rules; read `BoundVex.isPrey` before moving it.

- [ ] **Step 5: Full gates, then commit**

Run: `./gradlew spotlessApply build`, then `./gradlew runGameTestServer` twice (previous total + 16).

```bash
git add src/main/java/io/github/willrieske/spellcraft src/main/resources/assets/spellcraft/lang/en_us.json
git commit -m "feat(evoking): the teamMoves setting and the automatic team-move triggers"
```

---

### Task 7: The manual request

**Files:**

- Modify: `evoking/TeamMoves.java` (`request`, `Answer`, the subject search), `assets/spellcraft/lang/en_us.json`
- Create: `gametest/BoundVexTeamMoveRequestGameTests.java`; Modify: `gametest/SpellcraftGameTests.java`

**Interfaces:**

- Consumes: `ServerConfig.teamMoveMode()`, `TeamMoves.start/wardFor/attackDamage`, `TeamMoveRules.fits`.
- Produces: `TeamMoves.request(ServerPlayer, TeamMove)` → `TeamMoves.Answer { IGNORED, AUTOMATIC, RUNNING, COOLING, UNAVAILABLE, STARTED }` (public); the key constants `AUTOMATIC_KEY`, `RUNNING_KEY`, `COOLING_KEY`, `UNAVAILABLE_KEY` and `REQUEST_RANGE = 16.0`.

- [ ] **Step 1: Write the failing tests**

`BoundVexTeamMoveRequestGameTests` registers `FangGameTests.addPadded(…)`: the first test in `SHARED`, the rest in `config_mutation` (each sets the setting to `MANUAL` for one synchronous call through a helper `manual(Supplier)`):

```java
/** Runs {@code body} with the setting on MANUAL and gives back its answer; the setting is restored. */
private static <T> T manual(Supplier<T> body) {
    AtomicReference<T> answer = new AtomicReference<>();
    GameTestSupport.withConfig(ServerConfig.TEAM_MOVES, TeamMoveMode.MANUAL, () -> answer.set(body.get()));
    return answer.get();
}

private static Vec3 near(GameTestHelper helper) {
    return hangPoint(helper).add(3.0, 0.0, 0.0);
}

/**
 * When the setting is automatic, should every request answer "Team moves are automatic" and start nothing, even with an
 * enemy and vexes ready.
 *
 * <p>Method under test: {@link TeamMoves#request}: the automatic answer
 */
static void automaticAnswers(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 3);
    bigHusk(helper, near(helper));

    // Act
    TeamMoves.Answer answer = TeamMoves.request(player, TeamMove.SHIELD);

    // Assert
    helper.assertValueEqual(answer, TeamMoves.Answer.AUTOMATIC, "the answer");
    GameTestSupport.assertActionBarKey(helper, player, TeamMoves.AUTOMATIC_KEY);
    helper.assertTrue(TeamMoves.running(player).isEmpty(), "nothing started");
    helper.succeed();
}

/**
 * When the setting is manual and no enemy is near, should the request answer "unavailable".
 *
 * <p>Method under test: {@link TeamMoves#request}: no subject
 */
static void noEnemyIsUnavailable(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 3);

    // Act
    TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.SHIELD));

    // Assert
    helper.assertValueEqual(answer, TeamMoves.Answer.UNAVAILABLE, "the answer");
    GameTestSupport.assertActionBarKey(helper, player, TeamMoves.UNAVAILABLE_KEY);
    helper.succeed();
}

/**
 * When the setting is manual and only two vexes are free, should the request answer "unavailable" for a Shield.
 *
 * <p>Method under test: {@link TeamMoves#request}: no roster
 */
static void tooFewVexesIsUnavailable(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 2);
    bigHusk(helper, near(helper));

    // Act
    TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.SHIELD));

    // Assert
    helper.assertValueEqual(answer, TeamMoves.Answer.UNAVAILABLE, "the answer");
    helper.assertTrue(TeamMoves.running(player).isEmpty(), "nothing started");
    helper.succeed();
}

/**
 * When the setting is manual and an enemy 18 blocks from the summoner would fit, should the request answer
 * "unavailable" (the search stops at 16 blocks).
 *
 * <p>Method under test: {@link TeamMoves#request}: the 16-block search
 */
static void farEnemyIsUnavailable(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 3);
    bigHusk(helper, player.position().add(0.0, 18.0, 0.0));

    // Act
    TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.SHIELD));

    // Assert
    helper.assertValueEqual(answer, TeamMoves.Answer.UNAVAILABLE, "the answer");
    helper.succeed();
}

/**
 * When the setting is manual and a move runs, should a second request answer "already in a team move" whatever the
 * kind.
 *
 * <p>Method under test: {@link TeamMoves#request}: one move at a time
 */
static void runningMoveAnswersRunning(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 6);
    helper.assertTrue(TeamMoves.start(player, TeamMove.SHIELD, bigHusk(helper, near(helper))), "setup: a Shield runs");
    GameTestSupport.drainActionBar(player);

    // Act
    TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.EVADER));

    // Assert
    helper.assertValueEqual(answer, TeamMoves.Answer.RUNNING, "the answer");
    GameTestSupport.assertActionBarKey(helper, player, TeamMoves.RUNNING_KEY);
    helper.succeed();
}

/**
 * When the setting is manual and the Shield ended a moment ago, should a request for a Shield answer "not ready yet".
 *
 * <p>Method under test: {@link TeamMoves#request}: the cooldown
 */
static void coolingKindAnswersCooling(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 3);
    Husk first = bigHusk(helper, near(helper));
    helper.assertTrue(TeamMoves.start(player, TeamMove.SHIELD, first), "setup: a Shield runs");
    first.kill(helper.getLevel());
    TeamMoves.tick(player);
    bigHusk(helper, near(helper));
    GameTestSupport.drainActionBar(player);

    // Act
    TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.SHIELD));

    // Assert
    helper.assertValueEqual(answer, TeamMoves.Answer.COOLING, "the answer");
    GameTestSupport.assertActionBarKey(helper, player, TeamMoves.COOLING_KEY);
    helper.succeed();
}

/**
 * When the setting is manual with three free vexes and a 40-health husk near, should a Shield request start the Shield
 * with its usual message and nothing else.
 *
 * <p>Method under test: {@link TeamMoves#request}: the Shield
 */
static void shieldRequestStarts(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 3);
    Husk husk = bigHusk(helper, near(helper));

    // Act
    TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.SHIELD));

    // Assert
    helper.assertValueEqual(answer, TeamMoves.Answer.STARTED, "the answer");
    helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.SHIELD), "a Shield runs");
    helper.assertValueEqual(TeamMoves.subject(player), Optional.of(husk.getUUID()), "against the husk");
    GameTestSupport.assertActionBarKey(helper, player, TeamMoves.MESSAGE_PREFIX + "shield");
    helper.succeed();
}

/**
 * When the setting is manual and a husk with attack damage 6 hunts nobody, should a Multishot request start with the
 * nearest free vex in the roster (a hunted vex is not needed).
 *
 * <p>Method under test: {@link TeamMoves#request}: the Multishot
 */
static void multishotRequestNeedsNoHuntedVex(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    List<BoundVex> vexes = frozenVexes(helper, player, 4);
    Husk husk = foe(helper, EntityTypes.HUSK, near(helper));
    setBase(husk, Attributes.ATTACK_DAMAGE, 6.0);
    BoundVex nearest = vexes.stream().min(Comparator.comparingDouble(vex -> vex.distanceToSqr(husk))).orElseThrow();

    // Act
    TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.MULTISHOT));

    // Assert
    helper.assertValueEqual(answer, TeamMoves.Answer.STARTED, "the answer");
    helper.assertTrue(TeamMoves.roster(player).contains(nearest), "the nearest vex is in");
    helper.succeed();
}

/**
 * When the setting is manual and the summoner is hurt, should an Evader request start with no enemy near.
 *
 * <p>Method under test: {@link TeamMoves#request}: the Evader
 */
static void evaderRequestNeedsNoEnemy(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 2);
    player.setHealth(8.0F);

    // Act
    TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.EVADER));

    // Assert
    helper.assertValueEqual(answer, TeamMoves.Answer.STARTED, "the answer");
    helper.assertValueEqual(TeamMoves.roster(player).size(), 2, "both vexes");
    helper.succeed();
}

/**
 * When the setting is manual with twelve free vexes and a ravager near, should a Raid request start the Raid.
 *
 * <p>Method under test: {@link TeamMoves#request}: the Raid
 */
static void raidRequestStarts(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 12);
    foe(helper, EntityTypes.RAVAGER, near(helper));

    // Act
    TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.RAID));

    // Assert
    helper.assertValueEqual(answer, TeamMoves.Answer.STARTED, "the answer");
    helper.assertValueEqual(TeamMoves.roster(player).size(), 12, "all twelve");
    helper.succeed();
}

/**
 * When the summoner no longer has the Evoking effect, should a manual request still start the move (the vexes outlive
 * the effect).
 *
 * <p>Method under test: {@link TeamMoves#request}: no Evoking needed
 */
static void requestNeedsNoEvoking(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    player.removeEffect(ModEffects.EVOKING);
    frozenVexes(helper, player, 3);
    bigHusk(helper, near(helper));

    // Act
    TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.SHIELD));

    // Assert
    helper.assertValueEqual(answer, TeamMoves.Answer.STARTED, "the answer");
    helper.succeed();
}

/**
 * When the sender is dead, should the request be ignored: no answer on the action bar and nothing started.
 *
 * <p>Method under test: {@link TeamMoves#request}: dead and spectating senders
 */
static void deadSenderIsIgnored(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    frozenVexes(helper, player, 3);
    bigHusk(helper, near(helper));
    player.kill(helper.getLevel());

    // Act
    TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.SHIELD));

    // Assert
    helper.assertValueEqual(answer, TeamMoves.Answer.IGNORED, "the answer");
    helper.assertTrue(TeamMoves.running(player).isEmpty(), "nothing started");
    helper.succeed();
}
```

Names: `team_move_request_automatic_answers` (`SHARED`), then in `config_mutation`: `team_move_request_no_enemy_is_unavailable`, `…_too_few_vexes_is_unavailable`, `…_far_enemy_is_unavailable`, `…_running_move_answers_running`, `…_cooling_kind_answers_cooling`, `…_shield_starts`, `…_multishot_needs_no_hunted_vex`, `…_evader_needs_no_enemy`, `…_raid_starts`, `…_needs_no_evoking`, `…_dead_sender_is_ignored`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:team_move_request_*"`
Expected: compilation fails (`TeamMoves.request`, `Answer` and the keys do not exist).

- [ ] **Step 3: Implement**

Add to `TeamMoves`:

```java
/** Lang key of the answer in automatic mode. */
public static final String AUTOMATIC_KEY = "message.spellcraft.team_moves_automatic";

/** Lang key of the answer while a move runs. */
public static final String RUNNING_KEY = "message.spellcraft.team_move_running";

/** Lang key of the answer while the kind is cooling. */
public static final String COOLING_KEY = "message.spellcraft.team_move_cooling";

/** Lang key of the answer when no roster or no subject fits. */
public static final String UNAVAILABLE_KEY = "message.spellcraft.team_move_unavailable";

/** The manual request looks for an enemy this far from the summoner (spec §3.4). */
public static final double REQUEST_RANGE = 16.0;

/** What a team-move request did. Only {@code STARTED} starts a move. */
public enum Answer {
    /** A dead or spectating sender, or no real move. */
    IGNORED,
    /** The setting is automatic. */
    AUTOMATIC,
    /** A move already runs. */
    RUNNING,
    /** The kind is cooling down. */
    COOLING,
    /** No roster or no subject. */
    UNAVAILABLE,
    /** The move started. */
    STARTED
}

/**
 * Handles one team-move request from the K key (spec §3.4). It ignores a dead or spectating sender and does not need
 * the Evoking effect (the vexes outlive it, and automatic moves do not need it either). Then the first that applies is
 * the answer, shown on the action bar: automatic mode; a move already running; the kind cooling; no roster or no
 * subject (the nearest hostile within {@link #REQUEST_RANGE} blocks that is huntable, prey and fits the move; for an
 * Evader the ward of {@link #wardFor}); otherwise the move starts with its usual message.
 *
 * @param owner the sender
 * @param move the requested move
 * @return what the request did
 */
public static Answer request(ServerPlayer owner, TeamMove move) {
    if (move == TeamMove.NONE || !isPresent(owner)) {
        return Answer.IGNORED;
    }
    if (ServerConfig.teamMoveMode() == TeamMoveMode.AUTOMATIC) {
        return refuse(owner, Answer.AUTOMATIC, AUTOMATIC_KEY);
    }
    Record record = STATES.computeIfAbsent(owner.getUUID(), id -> new Record());
    if (record.active()) {
        return refuse(owner, Answer.RUNNING, RUNNING_KEY);
    }
    if (record.cooling(move, owner.level().getGameTime())) {
        return refuse(owner, Answer.COOLING, COOLING_KEY);
    }
    LivingEntity subject = subjectFor(owner, move);
    if (subject == null || !start(owner, move, subject)) {
        return refuse(owner, Answer.UNAVAILABLE, UNAVAILABLE_KEY);
    }
    return Answer.STARTED;
}

private static Answer refuse(ServerPlayer owner, Answer answer, String key) {
    owner.sendOverlayMessage(Component.translatable(key));
    return answer;
}

/** The enemy a manual move is fought against, or the ward of an Evader. */
private static @Nullable LivingEntity subjectFor(ServerPlayer owner, TeamMove move) {
    if (move == TeamMove.EVADER) {
        return wardFor(owner);
    }
    ServerLevel level = owner.level();
    return level.getEntitiesOfClass(
                    LivingEntity.class,
                    owner.getBoundingBox().inflate(REQUEST_RANGE),
                    enemy -> enemy.isAlive()
                            && enemy.distanceTo(owner) <= REQUEST_RANGE
                            && BoundVex.huntable(owner.getUUID(), enemy)
                            && BoundVex.isPrey(level, owner.getUUID(), enemy)
                            && TeamMoveRules.fits(move, enemy.getMaxHealth(), attackDamage(enemy)))
            .stream()
            .min(Comparator.comparingDouble(owner::distanceToSqr))
            .orElse(null);
}
```

Lang: `"message.spellcraft.team_moves_automatic": "Team moves are automatic"`, `"message.spellcraft.team_move_running": "Your vexes are already in a team move"`, `"message.spellcraft.team_move_cooling": "That team move is not ready yet"`, `"message.spellcraft.team_move_unavailable": "Not enough vexes, or no enemy for that move"`.

- [ ] **Step 4: Run the tests**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:team_move_request_*"`
Expected: all pass.

- [ ] **Step 5: Full gates, then commit**

Run: `./gradlew spotlessApply build`, then `./gradlew runGameTestServer` twice (previous total + 12).

```bash
git add src/main/java/io/github/willrieske/spellcraft src/main/resources/assets/spellcraft/lang/en_us.json
git commit -m "feat(evoking): the manual team-move request answers in the spec's order"
```

---

### Task 8: The packet's second kind and the J and K keys

**Files:**

- Modify: `evoking/EvokingRequest.java`, `evoking/EvokingNetwork.java`, `client/evoking/EvokingKeys.java`, `gametest/FangGameTests.java` (`requestCodecRoundTrip`), `gametest/BoundVexTeamMoveRequestGameTests.java`, `assets/spellcraft/lang/en_us.json`
- Modify: `CLAUDE.md` (the packet rule)

**Interfaces:**

- Consumes: `TeamMove.fromArg/arg/next/id`, `TeamMoves.request`, `EvokingCaster.request`.
- Produces:
  - `EvokingRequest(Kind kind, byte arg)` with `enum Kind { CAST, TEAM_MOVE }`; `static EvokingRequest cast(EvokerSpell)`; `static EvokingRequest teamMove(TeamMove)`; `STREAM_CODEC` writes the kind as an enum and the argument as a byte.
  - `EvokingNetwork.handle(ServerPlayer, EvokingRequest)` (public static): validates in full and dispatches; `VERSION` becomes `"2"`.
  - `EvokingKeys.TEAM_MOVE_CYCLE` (J) and `EvokingKeys.TEAM_MOVE_GO` (K).

- [ ] **Step 1: Write the failing tests**

Change `FangGameTests.requestCodecRoundTrip` (and its Javadoc: "for both spells and every team move") to build requests from the new factories, plus one raw request:

```java
static void requestCodecRoundTrip(GameTestHelper helper) {
    List<EvokingRequest> requests = new ArrayList<>();
    for (EvokerSpell spell : EvokerSpell.values()) {
        requests.add(EvokingRequest.cast(spell));
    }
    for (TeamMove move : TeamMove.values()) {
        requests.add(EvokingRequest.teamMove(move));
    }
    requests.add(new EvokingRequest(EvokingRequest.Kind.TEAM_MOVE, (byte) 99));
    for (EvokingRequest request : requests) {
        // Arrange
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);

        // Act
        EvokingRequest.STREAM_CODEC.encode(buf, request);
        EvokingRequest decoded = EvokingRequest.STREAM_CODEC.decode(buf);

        // Assert
        helper.assertValueEqual(decoded, request, "round trip of " + request);
        helper.assertValueEqual(buf.readableBytes(), 0, "bytes left after " + request);
        buf.release();
    }
    helper.succeed();
}
```

Add to `BoundVexTeamMoveRequestGameTests` (`FangGameTests.addPadded(SHARED, …)`; names `team_move_packet_cast_still_casts`, `…_team_move_reaches_the_request`, `…_out_of_range_team_move_is_ignored`, `…_out_of_range_cast_is_ignored`):

```java
/**
 * When a CAST request names the vexes spell, should the player start casting vexes exactly as a direct request does.
 *
 * <p>Method under test: {@link EvokingNetwork#handle}: the CAST kind
 */
static void castStillCasts(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);

    // Act
    EvokingNetwork.handle(player, EvokingRequest.cast(EvokerSpell.VEXES));

    // Assert
    helper.assertTrue(player.hasEffect(ModEffects.CASTING_VEXES), "the vexes spell is being cast");
    helper.succeed();
}

/**
 * When a TEAM_MOVE request arrives in automatic mode, should the answer be "Team moves are automatic": the request got
 * to {@code TeamMoves.request}.
 *
 * <p>Method under test: {@link EvokingNetwork#handle}: the TEAM_MOVE kind
 */
static void teamMoveReachesTheRequest(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);

    // Act
    EvokingNetwork.handle(player, EvokingRequest.teamMove(TeamMove.RAID));

    // Assert
    GameTestSupport.assertActionBarKey(helper, player, TeamMoves.AUTOMATIC_KEY);
    helper.succeed();
}

/**
 * When a TEAM_MOVE request carries NONE's ordinal, a negative byte or 99, should it be ignored: no message, no move.
 *
 * <p>Method under test: {@link EvokingNetwork#handle}: the argument range of TEAM_MOVE
 */
static void outOfRangeTeamMoveIsIgnored(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);

    // Act
    for (byte arg : new byte[] {4, -1, 99}) {
        EvokingNetwork.handle(player, new EvokingRequest(EvokingRequest.Kind.TEAM_MOVE, arg));
    }

    // Assert
    helper.assertTrue(GameTestSupport.drainActionBar(player).isEmpty(), "nothing is said");
    helper.assertTrue(TeamMoves.running(player).isEmpty(), "nothing started");
    helper.succeed();
}

/**
 * When a CAST request carries an ordinal no spell has, should it be ignored: no casting effect.
 *
 * <p>Method under test: {@link EvokingNetwork#handle}: the argument range of CAST
 */
static void outOfRangeCastIsIgnored(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);

    // Act
    for (byte arg : new byte[] {(byte) EvokerSpell.values().length, -1, 99}) {
        EvokingNetwork.handle(player, new EvokingRequest(EvokingRequest.Kind.CAST, arg));
    }

    // Assert
    helper.assertFalse(player.hasEffect(ModEffects.CASTING_VEXES), "no vexes");
    helper.assertFalse(player.hasEffect(ModEffects.CASTING_FANGS), "no fangs");
    helper.succeed();
}
```

`runData` is the check for the keys; the client code has no GameTest.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:team_move_packet_*"` and `"-PgameTests=spellcraft:fangs_request_codec_round_trip"`
Expected: compilation fails (`EvokingRequest.cast`, `Kind`, `EvokingNetwork.handle` do not exist).

- [ ] **Step 3: Implement**

```java
/**
 * The client's "I pressed this key" (spec §4; Part 6 spec §3.2): the mod's one custom packet. A request, never a
 * command: {@link EvokingNetwork#handle} validates everything. Two kinds: {@link Kind#CAST} (the argument is the
 * {@link EvokerSpell} ordinal) and {@link Kind#TEAM_MOVE} (the {@link TeamMove} ordinal, Shield 0 to Raid 3).
 *
 * @param kind what the key asks for
 * @param arg the ordinal of the spell or the move
 */
public record EvokingRequest(Kind kind, byte arg) implements CustomPacketPayload {
    /** What a request asks for. */
    public enum Kind {
        /** Cast an Evoking spell. */
        CAST,
        /** Start a team move by hand. */
        TEAM_MOVE
    }

    /** The payload id, {@code spellcraft:evoking_request}. */
    public static final Type<EvokingRequest> TYPE = new Type<>(Spellcraft.id("evoking_request"));

    /** Writes the kind as an enum and the argument as a byte. */
    public static final StreamCodec<RegistryFriendlyByteBuf, EvokingRequest> STREAM_CODEC = StreamCodec.composite(
            NeoForgeStreamCodecs.<RegistryFriendlyByteBuf, Kind>enumCodec(Kind.class),
            EvokingRequest::kind,
            ByteBufCodecs.BYTE,
            EvokingRequest::arg,
            EvokingRequest::new);

    /** Rejects a null kind, so a decoded or constructed request always names one. */
    public EvokingRequest {
        Objects.requireNonNull(kind, "kind");
    }

    /**
     * A request to cast {@code spell}.
     *
     * @param spell the spell whose key was pressed
     * @return the request
     */
    public static EvokingRequest cast(EvokerSpell spell) {
        return new EvokingRequest(Kind.CAST, (byte) spell.ordinal());
    }

    /**
     * A request to start {@code move}.
     *
     * @param move the move whose key was pressed
     * @return the request
     */
    public static EvokingRequest teamMove(TeamMove move) {
        return new EvokingRequest(Kind.TEAM_MOVE, move.arg());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

`EvokingNetwork`: `VERSION = "2"` (Javadoc: "bumped to 2 when the payload gained a kind"), and:

```java
private static final Logger LOGGER = LogUtils.getLogger();

@SubscribeEvent
public static void register(RegisterPayloadHandlersEvent event) {
    event.registrar(VERSION).playToServer(EvokingRequest.TYPE, EvokingRequest.STREAM_CODEC, (payload, context) -> {
        if (context.player() instanceof ServerPlayer player) {
            handle(player, payload);
        }
    });
}

/**
 * Validates a request in full and hands it on (Part 6 spec §3.2): an argument out of range for its kind is ignored
 * (logged at debug); a {@code CAST} goes to {@link EvokingCaster#request} exactly as before, a {@code TEAM_MOVE} to
 * {@link TeamMoves#request}. Public so GameTests can drive it without a connection.
 *
 * @param player the sender
 * @param request the decoded request
 */
public static void handle(ServerPlayer player, EvokingRequest request) {
    int arg = request.arg();
    switch (request.kind()) {
        case CAST -> {
            EvokerSpell[] spells = EvokerSpell.values();
            if (arg < 0 || arg >= spells.length) {
                LOGGER.debug("Ignoring a cast request with argument {} from {}", arg, player.getName().getString());
                return;
            }
            EvokingCaster.request(player, spells[arg]);
        }
        case TEAM_MOVE -> TeamMove.fromArg(arg)
                .ifPresentOrElse(
                        move -> TeamMoves.request(player, move),
                        () -> LOGGER.debug(
                                "Ignoring a team-move request with argument {} from {}",
                                arg,
                                player.getName().getString()));
    }
}
```

`EvokingKeys`: `EvokingRequest.cast(spell)` replaces `new EvokingRequest(spell)`; add

```java
/** Default J (scancode 13): no vanilla binding uses it, docs/api-notes.md §25. Cycles the selected team move. */
public static final KeyMapping TEAM_MOVE_CYCLE = new KeyMapping(
        "key.spellcraft.team_move_cycle",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYBOARD,
        InputConstants.KEY_J,
        CATEGORY);

/** Default K (scancode 14): no vanilla binding uses it. Asks the server to start the selected team move. */
public static final KeyMapping TEAM_MOVE_GO = new KeyMapping(
        "key.spellcraft.team_move_go",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYBOARD,
        InputConstants.KEY_K,
        CATEGORY);

/** The move J has selected. Client-side only; starts at Shield each session (spec §3.3). */
private static TeamMove selected = TeamMove.SHIELD;
```

`onRegisterKeys` registers both; `onClientTick` ends with `teamMoveKeys(player);`:

```java
/**
 * J cycles the selection and shows it on the action bar; K sends the selected move. Neither needs Evoking (the server
 * decides, and does not need it either) and neither shares the swap-offhand key, so there is nothing to swallow. In
 * automatic mode J still cycles and the server's answer to K is "Team moves are automatic": the client cannot see the
 * server's setting.
 */
private static void teamMoveKeys(@Nullable LocalPlayer player) {
    while (TEAM_MOVE_CYCLE.consumeClick()) {
        selected = selected.next();
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(
                    "message.spellcraft.team_move_selected",
                    Component.translatable("team_move.spellcraft." + selected.id())));
        }
    }
    while (TEAM_MOVE_GO.consumeClick()) {
        if (player != null) {
            ClientPacketDistributor.sendToServer(EvokingRequest.teamMove(selected));
        }
    }
}
```

Lang: `"key.spellcraft.team_move_cycle": "Evoking: Next Team Move"`, `"key.spellcraft.team_move_go": "Evoking: Team Move!"`, `"message.spellcraft.team_move_selected": "Team move: %s"`, `"team_move.spellcraft.shield": "Shield"`, `"…multishot": "Multishot"`, `"…evader": "Evader"`, `"…raid": "Raid"`.

`CLAUDE.md`: in the Rules bullet "Server owns state; the client renders it", the sentence about the one custom packet keeps its words and gains, inside the parentheses after "this key was pressed", the text "two request kinds: cast, team move" (it then reads: the `EvokingRequest` ("this key was pressed"; two request kinds: cast, team move)).

- [ ] **Step 4: Run the tests and the client boot**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:team_move_packet_*"`, `"…fangs_request_codec_round_trip"`, then `./gradlew runData`.
Expected: the tests pass; `runData` stays green (it loads the new key mappings).

- [ ] **Step 5: Full gates, then commit**

Run: `./gradlew spotlessApply build`, then `./gradlew runGameTestServer` twice (previous total + 4).

```bash
git add src/main/java/io/github/willrieske/spellcraft src/main/resources/assets/spellcraft/lang/en_us.json CLAUDE.md
git commit -m "feat(evoking): the request packet carries a team move; J picks and K starts"
```

---

### Task 9: Shield

**Files:**

- Create: `evoking/TeamFlight.java`, `evoking/ShieldMove.java`, `gametest/BoundVexTeamMoveShieldGameTests.java`
- Modify: `evoking/TeamMoveGoal.java` (dispatch by move), `gametest/SpellcraftGameTests.java`

**Interfaces:**

- Consumes: `TeamMoves.Record/Member/Member.Stage`, `TeamMoves.membership`, `BoundVex.hurtServer` (the Part 3 shield block).
- Produces:
  - `TeamFlight` (package-private, static): `NORMAL = 1.0`, `HALF = 0.5`; `flyTo(BoundVex, Vec3 spot, double speed)`; `lookAt(BoundVex, Entity)`; `boxGap(Entity, Entity)` (the distance between the two bounding boxes, 0 when they touch or overlap); `ringPoint(Vec3 center, Vec3 from, double radius, double y)` (the point at `radius` from `center` on the line toward `from`, at height `y`).
  - `ShieldMove.tick(BoundVex, TeamMoves.Record, TeamMoves.Member, ServerLevel)` (package-private); constants `APPROACH_RANGE = 7.0`, `BACK_OFF = 6.0`, `BASH_REACH = 1.5`, `BASH_DAMAGE = 4.0F`, `BASH_KNOCKBACK = 1.5`, `BASH_INTERVAL = 40`.
  - `TeamMoveGoal.tick` calls `ShieldMove.tick` for a `SHIELD` record.

- [ ] **Step 1: Write the failing tests**

`BoundVexTeamMoveShieldGameTests` registers `FangGameTests.addPadded(SHARED, name, 200, body)` for the three sequence tests and plain `FangGameTests.addPadded(SHARED, …)` for the two block tests. Names: `team_move_shield_bashes_for_four_and_knocks_back`, `team_move_shield_backs_off_to_about_six`, `team_move_shield_bashes_again_after_forty_ticks`, `team_move_shield_blocks_a_hit_from_the_front`, `team_move_shield_takes_a_hit_from_behind`.

```java
private record Scene(ServerPlayer player, List<BoundVex> vexes, Husk husk) {}

/** A Shield against a 40-health husk, with three flying vexes nine blocks south of it, stepped every tick. */
private static Scene scene(GameTestHelper helper) {
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    Vec3 hang = hangPoint(helper);
    Husk husk = bigHusk(helper, hang.add(0.0, 0.0, -4.0));
    List<BoundVex> vexes = flyingVexes(helper, player, 3, hang.add(0.0, 0.0, 5.0));
    helper.assertTrue(TeamMoves.start(player, TeamMove.SHIELD, husk), "setup: the Shield starts");
    TeamMoveTestSupport.stepEveryTick(helper, player);
    return new Scene(player, vexes, husk);
}

/**
 * When three vexes in a Shield come at a husk from the south, should the first bash cost exactly 4 health and push the
 * husk north, away from them.
 *
 * <p>Method under test: {@code ShieldMove.tick}: approach, lunge and bash
 */
static void bashesForFourAndKnocksBack(GameTestHelper helper) {
    // Arrange
    Scene scene = scene(helper);
    AtomicReference<Vec3> landed = new AtomicReference<>();

    // Act & Assert
    helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(scene.husk().getHealth() < 40.0F, "the first bash lands"))
            .thenExecute(() -> {
                helper.assertTrue(
                        Math.abs(scene.husk().getHealth() - 36.0F) < 0.01F,
                        "a bash costs 4, health is " + scene.husk().getHealth());
                landed.set(scene.husk().position());
            })
            .thenExecuteAfter(6, () -> helper.assertTrue(
                    scene.husk().getZ() < landed.get().z - 0.1, "the husk was pushed north, away from the vexes"))
            .thenSucceed();
}

/**
 * When the first bash has landed, should every member be back at about 6 blocks from the husk 30 ticks later (5 to 8.5).
 *
 * <p>Method under test: {@code ShieldMove.tick}: the back-off
 */
static void backsOffToAboutSix(GameTestHelper helper) {
    // Arrange
    Scene scene = scene(helper);

    // Act & Assert
    helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(scene.husk().getHealth() < 40.0F, "the first bash lands"))
            .thenExecuteAfter(30, () -> {
                for (BoundVex vex : scene.vexes()) {
                    double distance = vex.distanceTo(scene.husk());
                    helper.assertTrue(distance >= 5.0 && distance <= 8.5, "backed off to about 6, not " + distance);
                }
            })
            .thenSucceed();
}

/**
 * When the first bash has landed, should the next one land between 38 and 75 ticks later: each member waits out its
 * own 40-tick clock.
 *
 * <p>Method under test: {@code ShieldMove.tick}: the bash clock
 */
static void bashesAgainAfterFortyTicks(GameTestHelper helper) {
    // Arrange
    Scene scene = scene(helper);
    AtomicLong first = new AtomicLong();

    // Act & Assert
    helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(scene.husk().getHealth() < 40.0F, "the first bash lands"))
            .thenExecute(() -> first.set(helper.getTick()))
            .thenWaitUntil(() -> helper.assertTrue(scene.husk().getHealth() < 36.0F, "the second bash lands"))
            .thenExecute(() -> {
                long gap = helper.getTick() - first.get();
                helper.assertTrue(gap >= 38 && gap <= 75, "the second bash came " + gap + " ticks after the first");
            })
            .thenSucceed();
}

/**
 * When a husk hits a Shield member from the front, should the shield block it: the hit returns false and health does
 * not change.
 *
 * <p>Method under test: {@code BoundVex.hurtServer}: the Part 3 block, with the conjured off-hand shield
 */
static void blocksAHitFromTheFront(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    List<BoundVex> vexes = frozenVexes(helper, player, 3);
    TeamMoves.start(player, TeamMove.SHIELD, bigHusk(helper, hangPoint(helper).add(0.0, 0.0, -6.0)));
    BoundVex vex = vexes.get(0);
    vex.setYRot(-90.0F);
    vex.setYHeadRot(-90.0F);
    Husk attacker = foe(helper, EntityTypes.HUSK, vex.position().add(2.0, 0.0, 0.0));
    float before = vex.getHealth();

    // Act
    boolean hurt = vex.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 4.0F);

    // Assert
    helper.assertFalse(hurt, "the hit from the front is blocked");
    helper.assertValueEqual(vex.getHealth(), before, "health is unchanged");
    helper.succeed();
}

/**
 * When a husk hits a Shield member from behind, should the shield not help: the hit lands.
 *
 * <p>Method under test: {@code BoundVex.hurtServer}: a block covers the front only
 */
static void takesAHitFromBehind(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    List<BoundVex> vexes = frozenVexes(helper, player, 3);
    TeamMoves.start(player, TeamMove.SHIELD, bigHusk(helper, hangPoint(helper).add(0.0, 0.0, -6.0)));
    BoundVex vex = vexes.get(0);
    vex.setYRot(-90.0F);
    vex.setYHeadRot(-90.0F);
    Husk attacker = foe(helper, EntityTypes.HUSK, vex.position().add(-2.0, 0.0, 0.0));
    float before = vex.getHealth();

    // Act
    vex.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 4.0F);

    // Assert
    helper.assertTrue(vex.getHealth() < before, "the hit from behind lands");
    helper.succeed();
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:team_move_shield_*"`
Expected: the three sequence tests time out (nothing moves the members); the two block tests pass already (Part 3's block applies to the conjured shield), which is what they are for.

- [ ] **Step 3: Implement**

```java
package io.github.willrieske.spellcraft.evoking;

/** Flying and looking for the team moves: the vex's own move control, as the charge goal uses it (spec §4). */
final class TeamFlight {
    /** The charge goal's speed modifier: "normal speed". */
    static final double NORMAL = 1.0;

    /** Half of {@link #NORMAL}. */
    static final double HALF = 0.5;

    private TeamFlight() {}

    /** Orders the vex to fly to {@code spot} at {@code speed}; a vex has no gravity and clips through blocks. */
    static void flyTo(BoundVex vex, Vec3 spot, double speed) {
        vex.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, speed);
    }

    /** Turns the vex's head toward {@code target}'s eyes. */
    static void lookAt(BoundVex vex, Entity target) {
        Vec3 eye = target.getEyePosition();
        vex.getLookControl().setLookAt(eye.x, eye.y, eye.z, 30.0F, 30.0F);
    }

    /** The distance between the bounding boxes of {@code a} and {@code b}; 0 when they touch or overlap. */
    static double boxGap(Entity a, Entity b) {
        AABB x = a.getBoundingBox();
        AABB y = b.getBoundingBox();
        double dx = Math.max(0.0, Math.max(x.minX - y.maxX, y.minX - x.maxX));
        double dy = Math.max(0.0, Math.max(x.minY - y.maxY, y.minY - x.maxY));
        double dz = Math.max(0.0, Math.max(x.minZ - y.maxZ, y.minZ - x.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * The point {@code radius} blocks from {@code center} on the horizontal line toward {@code from}, at height
     * {@code y}; east of the center when {@code from} is straight above it.
     */
    static Vec3 ringPoint(Vec3 center, Vec3 from, double radius, double y) {
        Vec3 away = from.subtract(center).multiply(1.0, 0.0, 1.0);
        Vec3 direction = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
        return new Vec3(center.x + direction.x * radius, y, center.z + direction.z * radius);
    }
}
```

```java
package io.github.willrieske.spellcraft.evoking;

/**
 * A Shield member's part (Parts 6–7 spec §4.1), each on its own 40-tick bash clock that starts ready. Farther than 7
 * blocks from the enemy it flies in at half speed; within 7 and ready it lunges at normal speed and, within 1.5 blocks
 * of the enemy's box, bashes: 4 damage as a mob attack from the vex (so mercy, the backstop and End crits apply),
 * knockback 1.5 away from the vex, the shield-block sound at the enemy, a swing. A lunge that has not landed after 40
 * ticks gives up. After either it flies off to 6 blocks and holds there, looking at the enemy, until the clock is ready.
 */
final class ShieldMove {
    static final double APPROACH_RANGE = 7.0;
    static final double BACK_OFF = 6.0;
    static final double BASH_REACH = 1.5;
    static final float BASH_DAMAGE = 4.0F;
    static final double BASH_KNOCKBACK = 1.5;
    static final int BASH_INTERVAL = 40;

    private ShieldMove() {}

    /** One tick of one member's Shield. */
    static void tick(BoundVex vex, TeamMoves.Record record, TeamMoves.Member member, ServerLevel level) {
        if (record.subject == null
                || !(level.getEntity(record.subject) instanceof LivingEntity enemy)
                || !enemy.isAlive()) {
            return;
        }
        long now = level.getGameTime();
        TeamFlight.lookAt(vex, enemy);
        double distance = vex.distanceTo(enemy);
        switch (member.stage) {
            case READY -> {
                if (distance > APPROACH_RANGE) {
                    TeamFlight.flyTo(vex, enemy.getBoundingBox().getCenter(), TeamFlight.HALF);
                } else if (now >= member.nextAction) {
                    member.stage = TeamMoves.Member.Stage.LUNGING;
                    member.lungeStart = now;
                }
            }
            case LUNGING -> {
                if (TeamFlight.boxGap(vex, enemy) <= BASH_REACH) {
                    bash(vex, enemy, level);
                    backOff(member, now);
                } else if (now - member.lungeStart > BASH_INTERVAL) {
                    backOff(member, now);
                } else {
                    TeamFlight.flyTo(vex, enemy.getBoundingBox().getCenter(), TeamFlight.NORMAL);
                }
            }
            case BACKING -> {
                if (distance < BACK_OFF) {
                    Vec3 spot = TeamFlight.ringPoint(enemy.position(), vex.position(), BACK_OFF + 1.0, enemy.getY(0.5));
                    TeamFlight.flyTo(vex, spot, TeamFlight.NORMAL);
                } else if (now >= member.nextAction) {
                    member.stage = TeamMoves.Member.Stage.READY;
                }
            }
        }
    }

    private static void backOff(TeamMoves.Member member, long now) {
        member.stage = TeamMoves.Member.Stage.BACKING;
        member.nextAction = now + BASH_INTERVAL;
    }

    /** The bash: damage, knockback away from the vex, the sound at the enemy, the swing. */
    private static void bash(BoundVex vex, LivingEntity enemy, ServerLevel level) {
        DamageSource source = vex.damageSources().mobAttack(vex);
        enemy.hurtServer(level, source, BASH_DAMAGE);
        enemy.knockback(BASH_KNOCKBACK, vex.getX() - enemy.getX(), vex.getZ() - enemy.getZ(), source, BASH_DAMAGE);
        level.playSound(
                null,
                enemy.getX(),
                enemy.getY(),
                enemy.getZ(),
                SoundEvents.SHIELD_BLOCK.value(),
                vex.getSoundSource(),
                1.0F,
                1.0F);
        vex.swingForAttack(InteractionHand.MAIN_HAND);
    }
}
```

`TeamMoveGoal.tick` becomes:

```java
@Override
public void tick() {
    TeamMoves.Membership membership = TeamMoves.membership(vex).orElse(null);
    if (membership == null || vex.summoner().isEmpty() || !(vex.level() instanceof ServerLevel level)) {
        return;
    }
    TeamMoveKits.restore(vex, membership.member().kit);
    switch (membership.record().move) {
        case SHIELD -> ShieldMove.tick(vex, membership.record(), membership.member(), level);
        default -> {
            // the other moves are added by the tasks that build them
        }
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:team_move_shield_*"`
Expected: all pass. If the bash lands for less than 4 or twice in a tick, read the husk's armor and invulnerability frames: `bigHusk` removes armor, and a second bash inside 10 ticks is absorbed by the frames at the same size.

- [ ] **Step 5: Full gates, then commit**

Run: `./gradlew spotlessApply build`, then `./gradlew runGameTestServer` twice (previous total + 5).

```bash
git add src/main/java/io/github/willrieske/spellcraft
git commit -m "feat(evoking): the Shield team move bashes, backs off and bashes again"
```

---

### Task 10: Multishot

**Files:**

- Create: `evoking/MultishotMove.java`, `gametest/BoundVexTeamMoveMultishotGameTests.java`
- Modify: `evoking/VexShots.java` (`friendInTheWay` moves here; `volley`, `rocket`), `evoking/RangedGearGoal.java` (calls the moved method), `evoking/TeamMoveGoal.java`, `gametest/SpellcraftGameTests.java`

**Interfaces:**

- Consumes: `TeamFlight`, `VexShots.aimPoint`, `RangedGearGoal.LINE_CLEARANCE/SPLASH_CLEARANCE`.
- Produces:
  - `VexShots.friendInTheWay(BoundVex vex, LivingEntity target)` → `boolean` (package-private; the former private method of `RangedGearGoal`, unchanged); `VexShots.volley(BoundVex, LivingEntity)` (three arrows through `CrossbowItem.performShooting`); `VexShots.rocket(BoundVex, LivingEntity)` (one rocket with one explosion; Task 12 uses it).
  - `MultishotMove.tick(BoundVex, TeamMoves.Record, TeamMoves.Member, ServerLevel)`; constants `NEAR = 6.0`, `FAR = 8.0`, `HOLD = 7.0`, `SHOT_INTERVAL = 40`.

- [ ] **Step 1: Write the failing tests**

`BoundVexTeamMoveMultishotGameTests` (`FangGameTests.addPadded(SHARED, name, 200, body)`): `team_move_multishot_volley_is_three_arrows`, `team_move_multishot_closes_in_from_twelve`, `team_move_multishot_backs_off_from_three`, `team_move_multishot_holds_fire_for_a_friend`.

```java
/** The arrows in flight that a Bound Vex owns, around the hang point. */
private static List<AbstractArrow> volleyArrows(GameTestHelper helper) {
    return helper.getLevel()
            .getEntitiesOfClass(
                    AbstractArrow.class,
                    new AABB(hangPoint(helper), hangPoint(helper)).inflate(16.0),
                    arrow -> arrow.getOwner() instanceof BoundVex);
}

/** The angle in degrees between the velocities of two arrows. */
private static double angleBetween(AbstractArrow a, AbstractArrow b) {
    double cos = a.getDeltaMovement().normalize().dot(b.getDeltaMovement().normalize());
    return Math.toDegrees(Math.acos(Math.clamp(cos, -1.0, 1.0)));
}

/**
 * When three vexes keep seven blocks from a husk, should each shooter's first volley be exactly three arrows owned by
 * that vex, not pickable, spread over about 20 degrees, and leave its crossbow unharmed and empty.
 *
 * <p>Method under test: {@code MultishotMove.tick}, {@code VexShots.volley}
 */
static void volleyIsThreeArrows(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    Vec3 hang = hangPoint(helper);
    Husk husk = bigHusk(helper, hang.add(0.0, 0.0, -4.0));
    List<BoundVex> vexes = flyingVexes(helper, player, 3, hang.add(0.0, 0.0, 3.0));
    helper.assertTrue(TeamMoves.start(player, TeamMove.MULTISHOT, husk), "setup: the Multishot starts");
    TeamMoveTestSupport.stepEveryTick(helper, player);

    // Act & Assert
    helper.startSequence()
            .thenWaitUntil(() -> helper.assertFalse(volleyArrows(helper).isEmpty(), "a volley is fired"))
            .thenExecute(() -> {
                Map<UUID, List<AbstractArrow>> byShooter = volleyArrows(helper).stream()
                        .collect(Collectors.groupingBy(arrow -> arrow.getOwner().getUUID()));
                byShooter.forEach((shooter, arrows) -> {
                    helper.assertValueEqual(arrows.size(), 3, "three arrows from one crossbow");
                    arrows.forEach(arrow -> helper.assertValueEqual(
                            arrow.pickup, AbstractArrow.Pickup.DISALLOWED, "an arrow nobody can pick up"));
                    double widest = Math.max(
                            angleBetween(arrows.get(0), arrows.get(1)),
                            Math.max(angleBetween(arrows.get(0), arrows.get(2)), angleBetween(arrows.get(1), arrows.get(2))));
                    helper.assertTrue(widest >= 10.0 && widest <= 32.0, "spread over about 20 degrees, not " + widest);
                    BoundVex vex = vexes.stream().filter(v -> v.getUUID().equals(shooter)).findFirst().orElseThrow();
                    ItemStack crossbow = vex.getMainHandItem();
                    helper.assertFalse(CrossbowItem.isCharged(crossbow), "the crossbow is empty after the shot");
                    helper.assertValueEqual(crossbow.getDamageValue(), 0, "and unharmed");
                    helper.assertTrue(vex.isConjured(EquipmentSlot.MAINHAND), "and still conjured");
                });
            })
            .thenSucceed();
}

/**
 * When the vexes start twelve blocks from an (invulnerable) husk, should every member close in to between 5.5 and 8.5
 * blocks and stay there.
 *
 * <p>Method under test: {@code MultishotMove.tick}: the 6 to 8 band
 */
static void closesInFromTwelve(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    Vec3 hang = hangPoint(helper);
    Husk husk = bigHusk(helper, hang.add(0.0, 0.0, -6.0));
    husk.setPermanentlyInvulnerable(true);
    List<BoundVex> vexes = flyingVexes(helper, player, 3, hang.add(0.0, 0.0, 6.0));
    helper.assertTrue(TeamMoves.start(player, TeamMove.MULTISHOT, husk), "setup: the Multishot starts");
    TeamMoveTestSupport.stepEveryTick(helper, player);

    // Act & Assert
    helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(
                    vexes.stream().allMatch(vex -> vex.distanceTo(husk) <= 8.5), "every member is within 8.5 blocks"))
            .thenExecuteFor(20, () -> vexes.forEach(vex -> {
                double distance = vex.distanceTo(husk);
                helper.assertTrue(distance >= 5.5 && distance <= 8.8, "holds the band, not " + distance);
            }))
            .thenSucceed();
}

/**
 * When the vexes start three blocks from an (invulnerable) husk, should every member back off to at least 5.5 blocks.
 *
 * <p>Method under test: {@code MultishotMove.tick}: closer than 6 backs off
 */
static void backsOffFromThree(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    Vec3 hang = hangPoint(helper);
    Husk husk = bigHusk(helper, hang.add(0.0, 0.0, -2.0));
    husk.setPermanentlyInvulnerable(true);
    List<BoundVex> vexes = flyingVexes(helper, player, 3, hang.add(0.0, 0.0, 1.0));
    helper.assertTrue(TeamMoves.start(player, TeamMove.MULTISHOT, husk), "setup: the Multishot starts");
    TeamMoveTestSupport.stepEveryTick(helper, player);

    // Act & Assert
    helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(
                    vexes.stream().allMatch(vex -> vex.distanceTo(husk) >= 5.5), "every member backed off"))
            .thenSucceed();
}

/**
 * When the summoner stands on the line between the vexes and the husk, should no member shoot for 60 ticks (the Part 3
 * hold-fire rule).
 *
 * <p>Method under test: {@code MultishotMove.tick}: {@code VexShots.friendInTheWay}
 */
static void holdsFireForAFriend(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    Vec3 stand = player.position();
    Husk husk = bigHusk(helper, stand.add(0.0, 0.0, -3.5));
    husk.setPermanentlyInvulnerable(true);
    flyingVexes(helper, player, 3, stand.add(0.0, 1.0, 3.5));
    helper.assertTrue(TeamMoves.start(player, TeamMove.MULTISHOT, husk), "setup: the Multishot starts");
    TeamMoveTestSupport.stepEveryTick(helper, player);

    // Act & Assert
    helper.startSequence()
            .thenExecuteFor(60, () -> helper.assertTrue(volleyArrows(helper).isEmpty(), "nobody shoots past a friend"))
            .thenSucceed();
}
```

Also run the existing ranged-gear tests (`./gradlew runGameTestServer "-PgameTests=spellcraft:bound_vex_gear_*"`) in Step 4: they cover `RangedGearGoal` and the moved friendly-fire line.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:team_move_multishot_*"`
Expected: the volley, close-in and back-off tests time out (no member shoots or moves); the hold-fire test passes (nothing shoots yet).

- [ ] **Step 3: Implement**

`VexShots`: move `friendInTheWay` and `lineDistance` here from `RangedGearGoal` (delete them there; in `RangedGearGoal.tick` call `VexShots.friendInTheWay(vex, target)`; update its Javadoc), and add the two launches:

```java
/** A volley: three plain arrows, loaded by hand, fired through the crossbow (Parts 6–7 spec §4.2). */
static void volley(BoundVex vex, LivingEntity at) {
    ItemStack weapon = vex.getMainHandItem();
    if (!(vex.level() instanceof ServerLevel level) || !(weapon.getItem() instanceof CrossbowItem crossbow)) {
        return;
    }
    weapon.set(
            DataComponents.CHARGED_PROJECTILES,
            ChargedProjectiles.ofNonEmpty(
                    List.of(new ItemStack(Items.ARROW), new ItemStack(Items.ARROW), new ItemStack(Items.ARROW))));
    // The Multishot enchantment on the stack gives the spread (0 and ±10 degrees); vanilla's draw, which would add the
    // extra two projectiles, is not reachable from performShooting (docs/api-notes.md §25).
    crossbow.performShooting(level, vex, InteractionHand.MAIN_HAND, weapon, ARROW_SPEED, inaccuracy(level), at);
}

/** One rocket with one explosion, fired through the crossbow (Parts 6–7 spec §4.4); a rocket with none does no damage. */
static void rocket(BoundVex vex, LivingEntity at) {
    ItemStack weapon = vex.getMainHandItem();
    if (!(vex.level() instanceof ServerLevel level) || !(weapon.getItem() instanceof CrossbowItem crossbow)) {
        return;
    }
    ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
    rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(FireworkExplosion.DEFAULT)));
    weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(rocket)));
    crossbow.performShooting(level, vex, InteractionHand.MAIN_HAND, weapon, ROCKET_SPEED, ROCKET_INACCURACY, at);
}

/** What a pillager's crossbow uses: {@code 14 − difficulty × 4}. */
private static float inaccuracy(ServerLevel level) {
    return 14.0F - level.getDifficulty().getId() * 4.0F;
}
```

with constants `ROCKET_SPEED = 1.6F` and `ROCKET_INACCURACY = 1.0F`.

```java
package io.github.willrieske.spellcraft.evoking;

/**
 * A Multishot member's part (Parts 6–7 spec §4.2): keep 6 to 8 blocks from the enemy (closer: back off; farther: close
 * in; between: hold, looking at it) and every 40 ticks, with line of sight and no friend in the way, fire a volley of
 * three arrows from the conjured crossbow.
 */
final class MultishotMove {
    static final double NEAR = 6.0;
    static final double FAR = 8.0;
    static final double HOLD = 7.0;
    static final int SHOT_INTERVAL = 40;

    private MultishotMove() {}

    /** One tick of one member's Multishot. */
    static void tick(BoundVex vex, TeamMoves.Record record, TeamMoves.Member member, ServerLevel level) {
        if (record.subject == null
                || !(level.getEntity(record.subject) instanceof LivingEntity enemy)
                || !enemy.isAlive()) {
            return;
        }
        TeamFlight.lookAt(vex, enemy);
        double distance = vex.distanceTo(enemy);
        if (distance < NEAR || distance > FAR) {
            Vec3 spot = TeamFlight.ringPoint(enemy.position(), vex.position(), HOLD, enemy.getEyeY());
            TeamFlight.flyTo(vex, spot, TeamFlight.NORMAL);
        }
        long now = level.getGameTime();
        if (now >= member.nextAction && vex.hasLineOfSight(enemy) && !VexShots.friendInTheWay(vex, enemy)) {
            // Holding fire leaves the clock at zero: the volley goes as soon as the way is clear.
            VexShots.volley(vex, enemy);
            member.nextAction = now + SHOT_INTERVAL;
        }
    }
}
```

`TeamMoveGoal.tick`: add `case MULTISHOT -> MultishotMove.tick(vex, membership.record(), membership.member(), level);`.

- [ ] **Step 4: Run the tests**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:team_move_multishot_*"` and `"-PgameTests=spellcraft:bound_vex_gear_*"`.
Expected: all pass. If `volleyIsThreeArrows` sees six arrows from one owner, a volley's arrows from the earlier tick have not hit yet and a second shooter's are being grouped under the wrong owner: the grouping is by owner, so check `arrow.getOwner()` first.

- [ ] **Step 5: Full gates, then commit**

Run: `./gradlew spotlessApply build`, then `./gradlew runGameTestServer` twice (previous total + 4).

```bash
git add src/main/java/io/github/willrieske/spellcraft
git commit -m "feat(evoking): the Multishot team move fires three arrows at once"
```

---

### Task 11: Evader

**Files:**

- Create: `evoking/EvaderMove.java`, `gametest/BoundVexTeamMoveEvaderGameTests.java`
- Modify: `evoking/TeamMoves.java` (`advance`), `evoking/TeamMoveGoal.java`, `gametest/SpellcraftGameTests.java`

**Interfaces:**

- Consumes: `TeamMoveRules.orbitDegrees`, `TeamFlight`, `TeamMoves.members`, `BoundVex.huntable(UUID, LivingEntity)`, `BoundVex.isPrey(LivingEntity)`, `Allies.isAllyOf`.
- Produces: `EvaderMove.tick(BoundVex, TeamMoves.Record, TeamMoves.Member, ServerLevel)` (a member's orbit); `EvaderMove.guard(ServerPlayer, TeamMoves.Record, long now)` (the record-level shoves and deflections, run once a tick from `TeamMoves.tick`); constants `ORBIT_RADIUS = 2.0`, `SHOVE_RANGE = 3.0`, `SHOVE_KNOCKBACK = 2.0`, `SHOVE_PAUSE = 20`, `DEFLECT_RANGE = 2.0`, `DEFLECT_POWER = 2.0`.

The shoves and deflections run from `TeamMoves.tick`, not from the members' goals: a member paused for a rescue must not stop the guard, and the nearest member does the work whatever it is doing.

- [ ] **Step 1: Write the failing tests**

`BoundVexTeamMoveEvaderGameTests` (`FangGameTests.addPadded(SHARED, name, 200, body)`): `team_move_evader_orbits_at_two_blocks`, `team_move_evader_shoves_a_hostile_within_three`, `team_move_evader_shoves_a_hostile_once_per_twenty_ticks`, `team_move_evader_reverses_a_skeletons_arrow`, `team_move_evader_leaves_the_summoners_arrow_alone`.

```java
private record Scene(ServerPlayer player, List<BoundVex> vexes, LivingEntity ward) {}

/** An Evader warding the hurt summoner with three flying vexes, stepped every tick. */
private static Scene guardingTheSummoner(GameTestHelper helper) {
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    player.setHealth(8.0F);
    List<BoundVex> vexes = flyingVexes(helper, player, 3, hangPoint(helper));
    helper.assertTrue(TeamMoves.start(player, TeamMove.EVADER, player), "setup: the Evader starts");
    TeamMoveTestSupport.stepEveryTick(helper, player);
    return new Scene(player, vexes, player);
}

/** The point an Evader member circles around: the ward's middle. */
private static Vec3 center(LivingEntity ward) {
    return ward.position().add(0.0, ward.getBbHeight() / 2.0, 0.0);
}

/** An arrow of {@code owner} two blocks east of the ward, flying straight at it, added to the level. */
private static Arrow arrowAt(GameTestHelper helper, LivingEntity owner, LivingEntity ward) {
    Arrow arrow = new Arrow(helper.getLevel(), owner, new ItemStack(Items.ARROW), null);
    arrow.setNoGravity(true);
    arrow.setPos(center(ward).add(2.0, 0.0, 0.0));
    arrow.setDeltaMovement(-0.5, 0.0, 0.0);
    helper.getLevel().addFreshEntity(arrow);
    GameTestSupport.whenDone(helper, arrow::discard);
    return arrow;
}

/**
 * When three vexes guard the summoner, should each settle about two blocks from the summoner's middle and the three be
 * spread round the circle (at least two blocks apart).
 *
 * <p>Method under test: {@code EvaderMove.tick}: the orbit
 */
static void orbitsAtTwoBlocks(GameTestHelper helper) {
    // Arrange
    Scene scene = guardingTheSummoner(helper);

    // Act & Assert
    helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(
                    scene.vexes().stream()
                            .allMatch(vex -> Math.abs(vex.position().distanceTo(center(scene.ward())) - 2.0) <= 0.7),
                    "every member is about two blocks out"))
            .thenExecuteFor(20, () -> {
                for (int i = 0; i < 3; i++) {
                    for (int j = i + 1; j < 3; j++) {
                        helper.assertTrue(
                                scene.vexes().get(i).distanceTo(scene.vexes().get(j)) >= 2.0, "the members are spread");
                    }
                }
            })
            .thenSucceed();
}

/**
 * When a husk stands 2.5 blocks from the summoner, should it be shoved away within a few ticks, without damage.
 *
 * <p>Method under test: {@code EvaderMove.guard}: the shove
 */
static void shovesAHostileWithinThree(GameTestHelper helper) {
    // Arrange
    Scene scene = guardingTheSummoner(helper);
    Husk husk = foe(helper, EntityTypes.HUSK, scene.player().position().add(2.5, 0.0, 0.0));
    Vec3 before = husk.position();

    // Act & Assert
    helper.startSequence()
            .thenExecuteAfter(5, () -> {
                helper.assertTrue(husk.getX() > before.x + 0.2, "the husk was pushed away from the summoner");
                helper.assertValueEqual(husk.getHealth(), husk.getMaxHealth(), "and not hurt");
            })
            .thenSucceed();
}

/**
 * When a husk is held within three blocks of the summoner, should it be shoved once in the next 19 ticks and again
 * shortly after 20 (the shield-block sound counts the shoves).
 *
 * <p>Method under test: {@code EvaderMove.guard}: once per hostile per 20 ticks
 */
static void shovesOncePerTwentyTicks(GameTestHelper helper) {
    // Arrange
    Scene scene = guardingTheSummoner(helper);
    Husk husk = foe(helper, EntityTypes.HUSK, scene.player().position().add(2.5, 0.0, 0.0));
    BoundVexTestSupport.holdAt(helper, husk, husk.position());

    // Act & Assert
    helper.startSequence()
            .thenExecuteAfter(19, () -> helper.assertValueEqual(
                    GameTestSupport.drainSounds(scene.player()).stream()
                            .filter(sound -> sound.equals(SoundEvents.SHIELD_BLOCK.value()))
                            .count(),
                    1L,
                    "one shove in the first 19 ticks"))
            .thenExecuteAfter(5, () -> helper.assertValueEqual(
                    GameTestSupport.drainSounds(scene.player()).stream()
                            .filter(sound -> sound.equals(SoundEvents.SHIELD_BLOCK.value()))
                            .count(),
                    1L,
                    "and the next one after twenty"))
            .thenSucceed();
}

/**
 * When a skeleton's arrow flies two blocks from the summoner straight at them, should a member reverse it and own
 * it.
 *
 * <p>Method under test: {@code EvaderMove.guard}: the deflection
 */
static void reversesASkeletonsArrow(GameTestHelper helper) {
    // Arrange
    Scene scene = guardingTheSummoner(helper);
    Mob skeleton = foe(helper, EntityTypes.SKELETON, scene.player().position().add(9.0, 0.0, 0.0));
    Arrow arrow = arrowAt(helper, skeleton, scene.ward());

    // Act
    TeamMoves.tick(scene.player());

    // Assert
    helper.assertTrue(arrow.getOwner() instanceof BoundVex, "a vex owns the arrow now");
    helper.assertTrue(arrow.getDeltaMovement().x > 0.0, "and it flies away from the summoner");
    helper.succeed();
}

/**
 * When the summoner's own arrow flies at a hurt villager the Evader guards, should no member touch it.
 *
 * <p>Method under test: {@code EvaderMove.guard}: only projectiles from outside the summoner's side
 */
static void leavesTheSummonersArrowAlone(GameTestHelper helper) {
    // Arrange
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    Villager villager = foe(helper, EntityTypes.VILLAGER, hangPoint(helper).add(0.0, -3.0, 0.0));
    villager.setHealth(villager.getMaxHealth() * 0.4F);
    flyingVexes(helper, player, 2, hangPoint(helper));
    helper.assertTrue(TeamMoves.start(player, TeamMove.EVADER, villager), "setup: the Evader starts");
    Arrow arrow = arrowAt(helper, player, villager);

    // Act
    TeamMoves.tick(player);

    // Assert
    helper.assertTrue(player.equals(arrow.getOwner()), "the summoner still owns the arrow");
    helper.assertTrue(arrow.getDeltaMovement().x < 0.0, "and it still flies at the villager");
    helper.succeed();
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:team_move_evader_*"`
Expected: the orbit, shove and reverse tests fail; `leavesTheSummonersArrowAlone` passes (nothing touches the arrow yet), which is what it is for.

- [ ] **Step 3: Implement**

```java
package io.github.willrieske.spellcraft.evoking;

/**
 * The Evader (Parts 6–7 spec §4.3). Each member circles the ward at 2 blocks, spread evenly, the circle turning 6° a
 * tick ({@link #tick}). The guard ({@link #guard}) runs once a tick for the whole record: the nearest member shoves any
 * hostile whose box comes within 3 blocks of the ward's (knockback 2 away from the ward, no damage, each hostile at most
 * once per 20 ticks) and reverses any projectile within 2 blocks of the ward that flies toward it and was not shot from
 * the summoner's side (not a large fireball: Part 5's return owns those). The reversed projectile's new owner is the
 * vex, so it is never deflected twice and the friendly-fire backstop covers it.
 */
final class EvaderMove {
    static final double ORBIT_RADIUS = 2.0;
    static final double SHOVE_RANGE = 3.0;
    static final double SHOVE_KNOCKBACK = 2.0;
    static final int SHOVE_PAUSE = 20;
    static final double DEFLECT_RANGE = 2.0;
    static final double DEFLECT_POWER = 2.0;

    private EvaderMove() {}

    /** One tick of one member's orbit. */
    static void tick(BoundVex vex, TeamMoves.Record record, TeamMoves.Member member, ServerLevel level) {
        if (record.subject == null
                || !(level.getEntity(record.subject) instanceof LivingEntity ward)
                || !ward.isAlive()) {
            return;
        }
        int slot = Math.max(0, record.roster.indexOf(vex.getUUID()));
        double radians = Math.toRadians(
                TeamMoveRules.orbitDegrees(slot, Math.max(1, record.roster.size()), level.getGameTime() - record.start));
        Vec3 middle = ward.position().add(0.0, ward.getBbHeight() / 2.0, 0.0);
        TeamFlight.flyTo(
                vex,
                middle.add(Math.cos(radians) * ORBIT_RADIUS, 0.0, Math.sin(radians) * ORBIT_RADIUS),
                TeamFlight.NORMAL);
        TeamFlight.lookAt(vex, ward);
    }

    /** The shoves and the deflections of one tick. */
    static void guard(ServerPlayer owner, TeamMoves.Record record, long now) {
        ServerLevel level = owner.level();
        if (record.subject == null || !(level.getEntity(record.subject) instanceof LivingEntity ward)) {
            return;
        }
        List<BoundVex> members = TeamMoves.members(level, record);
        if (members.isEmpty()) {
            return;
        }
        record.shoved.values().removeIf(until -> until <= now);
        for (LivingEntity hostile : level.getEntitiesOfClass(
                LivingEntity.class,
                ward.getBoundingBox().inflate(SHOVE_RANGE),
                other -> other != ward
                        && other.isAlive()
                        && !record.shoved.containsKey(other.getUUID())
                        && TeamFlight.boxGap(other, ward) <= SHOVE_RANGE
                        && BoundVex.huntable(owner.getUUID(), other))) {
            BoundVex member = nearest(members, hostile.position());
            if (member.isPrey(hostile)) {
                shove(member, hostile, ward);
                record.shoved.put(hostile.getUUID(), now + SHOVE_PAUSE);
            }
        }
        for (Projectile projectile : level.getEntitiesOfClass(
                Projectile.class,
                ward.getBoundingBox().inflate(DEFLECT_RANGE),
                shot -> shot.isAlive()
                        && !(shot instanceof LargeFireball)
                        && TeamFlight.boxGap(shot, ward) <= DEFLECT_RANGE
                        && approaches(shot, ward)
                        && !onTheirSide(owner, shot.getOwner()))) {
            BoundVex member = nearest(members, projectile.position());
            projectile.deflect(
                    ProjectileDeflection.REVERSE, member, EntityReference.of(member), false, DEFLECT_POWER);
            member.playSound(SoundEvents.SHIELD_BLOCK.value(), 1.0F, 1.0F);
        }
    }

    private static BoundVex nearest(List<BoundVex> members, Vec3 point) {
        return members.stream()
                .min(Comparator.comparingDouble(member -> member.distanceToSqr(point)))
                .orElseThrow();
    }

    /** The shove: knockback away from the ward, no damage, the sound and the swing. */
    private static void shove(BoundVex member, LivingEntity hostile, LivingEntity ward) {
        hostile.knockback(
                SHOVE_KNOCKBACK,
                ward.getX() - hostile.getX(),
                ward.getZ() - hostile.getZ(),
                member.damageSources().mobAttack(member),
                0.0F);
        member.playSound(SoundEvents.SHIELD_BLOCK.value(), 1.0F, 1.0F);
        member.swingForAttack(InteractionHand.MAIN_HAND);
    }

    /** Whether the projectile's velocity points toward the ward's middle. */
    private static boolean approaches(Projectile shot, LivingEntity ward) {
        return shot.getDeltaMovement().dot(ward.getBoundingBox().getCenter().subtract(shot.position())) > 0.0;
    }

    /** Whether the shooter is the summoner, one of their allies or a mob carrying their mark. */
    private static boolean onTheirSide(ServerPlayer summoner, @Nullable Entity shooter) {
        return shooter != null
                && (shooter.getUUID().equals(summoner.getUUID())
                        || Allies.isAllyOf(summoner, shooter)
                        || shooter.getExistingData(AllyAttachments.SUMMONER)
                                .filter(summoner.getUUID()::equals)
                                .isPresent());
    }
}
```

`TeamMoves` gets, called from `tick` when the move is not over (`else advance(owner, record, now);` after the `shouldEnd` branch):

```java
/** The record-level part of a running move: what no single member does (the Evader's shoves and deflections). */
private static void advance(ServerPlayer owner, Record record, long now) {
    switch (record.move) {
        case EVADER -> EvaderMove.guard(owner, record, now);
        default -> {
            // the other moves have nothing at record level, or get it with the task that builds them
        }
    }
}
```

`TeamMoveGoal.tick`: add `case EVADER -> EvaderMove.tick(vex, membership.record(), membership.member(), level);`.

- [ ] **Step 4: Run the tests**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:team_move_evader_*"`
Expected: all pass. If the shove test finds the husk unmoved, check the husk's knockback resistance (a husk has none) and that the husk is `huntable` and `isPrey` for the member (it is an `Enemy`, not a creeper, no mark).

- [ ] **Step 5: Full gates, then commit**

Run: `./gradlew spotlessApply build`, then `./gradlew runGameTestServer` twice (previous total + 5).

```bash
git add src/main/java/io/github/willrieske/spellcraft
git commit -m "feat(evoking): the Evader team move circles, shoves and deflects"
```

---

### Task 12: Raid

**Files:**

- Create: `evoking/RaidMove.java`, `gametest/BoundVexTeamMoveRaidGameTests.java`
- Modify: `evoking/TeamMoves.java` (`advance` flips the phase), `evoking/TeamMoveGoal.java`, `gametest/SpellcraftGameTests.java`

**Interfaces:**

- Consumes: `TeamMoveRules.gridAcross/gridDepth`, `TeamMoves.Phase`, `VexShots.rocket/friendInTheWay`, `Dice.RAID_KIT` (scripted by one test).
- Produces: `public final class RaidMove` with `public static Vec3 cell(Vec3 enemyEye, Vec3 summoner, int cell)` (the grid cell's position: 8 blocks from the enemy toward the summoner, rows facing the enemy, at the enemy's eye height), `static void advance(TeamMoves.Record, long now)` (Gather → Charge after 40 ticks), `static void tick(BoundVex, TeamMoves.Record, TeamMoves.Member, ServerLevel)`; constants `GATHER_TICKS = 40`, `GRID_DISTANCE = 8.0`, `ATTACK_INTERVAL = 20`, `ROCKET_NEAR = 7.0`, `ROCKET_FAR = 9.0`, `ROCKET_HOLD = 8.0`, `ROCKET_INTERVAL = 40`.

- [ ] **Step 1: Write the failing tests**

`BoundVexTeamMoveRaidGameTests`: the first two in `SHARED` (`FangGameTests.addPadded(SHARED, name, 300, body)`), the last in its own environment `team_move_dice` (`FangGameTests.addPadded("team_move_dice", name, 400, body)`), because it scripts the global `Dice.RAID_KIT` and a second scripting test running beside it would eat the script. Names: `team_move_raid_members_reach_their_grid_cells`, `team_move_raid_melee_members_hit_the_enemy`, `team_move_raid_dice_kits_and_rocket_blast`.

```java
private record Scene(ServerPlayer player, List<BoundVex> vexes, Ravager ravager) {}

/** A Raid against a ravager four blocks north of the hang point, with twelve flying vexes south of it, stepped every tick. */
private static Scene scene(GameTestHelper helper) {
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    Vec3 hang = hangPoint(helper);
    Ravager ravager = foe(helper, EntityTypes.RAVAGER, hang.add(0.0, 0.0, -4.0));
    List<BoundVex> vexes = flyingVexes(helper, player, 12, hang.add(0.0, 0.0, 3.0));
    helper.assertTrue(TeamMoves.start(player, TeamMove.RAID, ravager), "setup: the Raid starts");
    TeamMoveTestSupport.stepEveryTick(helper, player);
    return new Scene(player, vexes, ravager);
}

/**
 * When a Raid gathers, should every member reach its own grid cell (within 1.3 blocks) at some tick of the 40-tick
 * gather.
 *
 * <p>Method under test: {@code RaidMove.tick}: the gather
 */
static void membersReachTheirCells(GameTestHelper helper) {
    // Arrange
    Scene scene = scene(helper);

    // Act & Assert
    helper.startSequence()
            .thenWaitUntil(() -> {
                List<BoundVex> roster = TeamMoves.roster(scene.player());
                for (int i = 0; i < roster.size(); i++) {
                    Vec3 cell = RaidMove.cell(scene.ravager().getEyePosition(), scene.player().position(), i);
                    helper.assertTrue(
                            roster.get(i).position().distanceTo(cell) <= 1.3,
                            "member " + i + " is at its cell, not " + roster.get(i).position().distanceTo(cell) + " off");
                }
            })
            .thenSucceed();
}

/**
 * When the Raid has charged, should the enemy lose health to the melee members within 150 ticks.
 *
 * <p>Method under test: {@code RaidMove.tick}: the charge
 */
static void meleeMembersHitTheEnemy(GameTestHelper helper) {
    // Arrange
    Scene scene = scene(helper);

    // Act & Assert
    helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(
                    scene.ravager().getHealth() < scene.ravager().getMaxHealth(), "the charge hurts the ravager"))
            .thenSucceed();
}

/**
 * When the Raid's dice are scripted, should each member get the scripted weapon and armor material in roster order, and
 * the rocket crossbow of member 0 fire a rocket that explodes and hurts the ravager but not the summoner standing three
 * blocks from it.
 *
 * <p>Method under test: {@code TeamMoveKits.raid}, {@code RaidMove.tick}, {@code VexShots.rocket}
 */
static void diceKitsAndRocketBlast(GameTestHelper helper) {
    // Arrange: member k rolls (weapon, material); weapon 6 is the rocket crossbow, material 0 netherite, 1 diamond
    int[] weapons = {6, 0, 1, 2, 3, 4, 5, 0, 1, 2, 3, 4};
    Item[] items = {
        Items.NETHERITE_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_AXE, Items.DIAMOND_AXE, Items.MACE, Items.TRIDENT,
        Items.CROSSBOW
    };
    int[] script = new int[24];
    for (int k = 0; k < 12; k++) {
        script[2 * k] = weapons[k];
        script[2 * k + 1] = k % 2;
    }
    Dice.RAID_KIT.script(script);
    GameTestSupport.whenDone(helper, Dice.RAID_KIT::unscript);
    ServerPlayer player = FangGameTests.caster(helper, NORTH);
    Vec3 stand = player.position();
    Ravager ravager = foe(helper, EntityTypes.RAVAGER, stand.add(0.0, 0.0, -3.0));
    flyingVexes(helper, player, 12, stand.add(0.0, 3.0, 5.0));

    // Act
    helper.assertTrue(TeamMoves.start(player, TeamMove.RAID, ravager), "setup: the Raid starts");
    Dice.RAID_KIT.unscript();
    TeamMoveTestSupport.stepEveryTick(helper, player);

    // Assert: the kits
    List<BoundVex> roster = TeamMoves.roster(player);
    for (int k = 0; k < 12; k++) {
        BoundVex vex = roster.get(k);
        helper.assertTrue(vex.getMainHandItem().is(items[weapons[k]]), "member " + k + " holds " + items[weapons[k]]);
        helper.assertTrue(
                vex.getItemBySlot(EquipmentSlot.HEAD).is(k % 2 == 0 ? Items.NETHERITE_HELMET : Items.DIAMOND_HELMET),
                "member " + k + " wears the scripted material");
    }
    BoundVex rocketeer = roster.get(0);

    // Assert: the rocket
    helper.startSequence()
            .thenWaitUntil(() -> helper.assertFalse(rockets(helper, rocketeer).isEmpty(), "member 0 fires a rocket"))
            .thenWaitUntil(() -> helper.assertTrue(rockets(helper, rocketeer).isEmpty(), "and it explodes"))
            .thenExecute(() -> {
                helper.assertTrue(ravager.getHealth() < ravager.getMaxHealth(), "the ravager was hurt");
                helper.assertValueEqual(
                        player.getHealth(), player.getMaxHealth(), "the summoner, three blocks from the blast, was not");
            })
            .thenSucceed();
}

/** The rockets of {@code vex} still in flight. */
private static List<FireworkRocketEntity> rockets(GameTestHelper helper, BoundVex vex) {
    return helper.getLevel()
            .getEntitiesOfClass(
                    FireworkRocketEntity.class,
                    new AABB(vex.position(), vex.position()).inflate(30.0),
                    rocket -> vex.equals(rocket.getOwner()));
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:team_move_raid_*"`
Expected: compilation fails (`RaidMove` does not exist); after a stub, the three tests time out.

- [ ] **Step 3: Implement**

```java
package io.github.willrieske.spellcraft.evoking;

/**
 * The Raid (Parts 6–7 spec §4.4). Gather, 40 ticks: the 12 members take the cells of a 3 × 4 grid, 1.5 blocks apart,
 * centered 8 blocks from the enemy toward the summoner, rows facing the enemy, at the enemy's eye height, each flying
 * there at normal speed and looking at the enemy. Charge, to the end: melee members fly at the enemy and hit it with
 * {@code doHurtTarget} when their box meets its box, each on a 20-tick attack clock (the charge goal has none; a member
 * pressed against the enemy would hit every tick); rocket members (a crossbow in the main hand) keep 8 blocks (hold
 * between 7 and 9) and every 40 ticks, with line of sight and no friend in the way, fire a rocket. The blast's damage
 * source names the vex, so the friendly-fire backstop spares the summoner and everything the vex may not fight. A Raid
 * whose roster falls below 12 continues.
 */
public final class RaidMove {
    static final int GATHER_TICKS = 40;
    static final double GRID_DISTANCE = 8.0;
    static final int ATTACK_INTERVAL = 20;
    static final double ROCKET_NEAR = 7.0;
    static final double ROCKET_FAR = 9.0;
    static final double ROCKET_HOLD = 8.0;
    static final int ROCKET_INTERVAL = 40;

    private RaidMove() {}

    /**
     * The position of a grid cell. Row 0 is the front row, nearest the enemy; column 0 is the left one as the grid
     * faces the enemy.
     *
     * @param enemyEye the enemy's eye position
     * @param summoner the summoner's position: the grid lies on the line from the enemy toward them
     * @param cell the cell, 0 to 11 in roster order
     * @return where a member of that cell flies to during the gather
     */
    public static Vec3 cell(Vec3 enemyEye, Vec3 summoner, int cell) {
        Vec3 toward = new Vec3(summoner.x - enemyEye.x, 0.0, summoner.z - enemyEye.z);
        Vec3 forward = toward.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : toward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        return enemyEye
                .add(forward.scale(GRID_DISTANCE + TeamMoveRules.gridDepth(cell)))
                .add(right.scale(TeamMoveRules.gridAcross(cell)));
    }

    /** Moves the record from the gather to the charge once the gather's 40 ticks are up. */
    static void advance(TeamMoves.Record record, long now) {
        if (record.phase == TeamMoves.Phase.GATHER && now - record.start >= GATHER_TICKS) {
            record.phase = TeamMoves.Phase.CHARGE;
        }
    }

    /** One tick of one member's Raid. */
    static void tick(BoundVex vex, TeamMoves.Record record, TeamMoves.Member member, ServerLevel level) {
        // Part 7: a Caster only gathers, casts Mirror, then holds its cell
        if (record.subject == null
                || !(level.getEntity(record.subject) instanceof LivingEntity enemy)
                || !enemy.isAlive()) {
            return;
        }
        TeamFlight.lookAt(vex, enemy);
        long now = level.getGameTime();
        if (record.phase == TeamMoves.Phase.GATHER) {
            Vec3 summoner = vex.summoner().map(Entity::position).orElse(vex.position());
            TeamFlight.flyTo(vex, cell(enemy.getEyePosition(), summoner, member.cell), TeamFlight.NORMAL);
        } else if (vex.getMainHandItem().getItem() instanceof CrossbowItem) {
            rocketeer(vex, member, enemy, now);
        } else {
            charger(vex, member, enemy, level, now);
        }
    }

    private static void charger(BoundVex vex, TeamMoves.Member member, LivingEntity enemy, ServerLevel level, long now) {
        TeamFlight.flyTo(vex, enemy.getBoundingBox().getCenter(), TeamFlight.NORMAL);
        if (vex.getBoundingBox().intersects(enemy.getBoundingBox()) && now >= member.nextAction) {
            vex.doHurtTarget(level, enemy);
            vex.swingForAttack(InteractionHand.MAIN_HAND);
            member.nextAction = now + ATTACK_INTERVAL;
        }
    }

    private static void rocketeer(BoundVex vex, TeamMoves.Member member, LivingEntity enemy, long now) {
        double distance = vex.distanceTo(enemy);
        if (distance < ROCKET_NEAR || distance > ROCKET_FAR) {
            Vec3 spot = TeamFlight.ringPoint(enemy.position(), vex.position(), ROCKET_HOLD, enemy.getEyeY());
            TeamFlight.flyTo(vex, spot, TeamFlight.NORMAL);
        }
        if (now >= member.nextAction && vex.hasLineOfSight(enemy) && !VexShots.friendInTheWay(vex, enemy)) {
            VexShots.rocket(vex, enemy);
            member.nextAction = now + ROCKET_INTERVAL;
        }
    }
}
```

`TeamMoves.advance` gets `case RAID -> RaidMove.advance(record, now);`; `TeamMoveGoal.tick` gets `case RAID -> RaidMove.tick(vex, membership.record(), membership.member(), level);` and, now that every move has its case, the `default` branch is removed (the switch is exhaustive over the real moves; add `case NONE -> {}` with the comment "a vex marked NONE has no membership"). Same for `TeamMoves.advance`: `case SHIELD, MULTISHOT, NONE -> { }` with a comment that these have nothing at record level.

- [ ] **Step 4: Run the tests**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:team_move_raid_*"`.
Expected: all pass. If the rocket never fires, the summoner is blocking member 0's line (the hold-fire rule): read `VexShots.friendInTheWay`, print the distance from the summoner to the segment, and move the rocketeer to the cell the numbers favor (the scripted weapon index is `weapons[0]`; the roster order is the order the dice are read in, which `start` fixes).

- [ ] **Step 5: Full gates, then commit**

Run: `./gradlew spotlessApply build`, then `./gradlew runGameTestServer` twice (previous total + 3).

```bash
git add src/main/java/io/github/willrieske/spellcraft
git commit -m "feat(evoking): the Raid team move gathers in a grid, charges and fires rockets"
```

---

### Task 13: Docs, playtest items, mutation testing

**Files:**

- Modify: `README.md`, `src/main/templates/META-INF/neoforge.mods.toml`, `docs/playtest-checklist.md`, `docs/mutation-testing.md`, `docs/api-notes.md` (§25 corrections), `CLAUDE.md` (verify only; Task 8 changed it)
- Modify: `docs/superpowers/specs/2026-11-10-evoking-ii-parts-6-7-design.md` (only the as-built sentences listed below)

- [ ] **Step 1: README**

In the Bound Vex paragraph (after Part 5's crit sentence, before "Fangs spare you and your allies"), add, in the README's own voice: your vexes act as a team. When something big comes close they grab conjured shields and take turns bashing it back (**Shield**); when a strong monster goes after one of them, that vex and two friends pull out crossbows that fire three arrows at once (**Multishot**); when you or an ally of yours drops below half health, up to three vexes circle the hurt one, shove away anything that comes close and knock arrows back (**Evader**); and when a giant (a Ravager, a Warden, the Wither, the Dragon) shows up and all twelve vexes are out, they gear up in netherite and diamond, line up in a 3 × 4 block and charge together while the crossbow vexes fire rockets (**Raid**). The conjured gear vanishes when the move ends and your own gear comes back; a vex in a move answers a click with "That vex is busy". Set `teamMoves` in the server config (section `evoking`) to `MANUAL` to turn the automatic triggers off; then **J** picks a move and **K** starts it. The keys rebind in Controls (they sit with the Evoking keys).

- [ ] **Step 2: Mod description**

Read `src/main/templates/META-INF/neoforge.mods.toml`. It lists spells, not vex behaviors, and Part 6 adds no spell; add only this clause to the description line: `…and Strength of Evoking (whose Bound Vexes fight as a team).`

- [ ] **Step 3: Playtest checklist**

Add a section "Evoking II (Part 6)" to `docs/playtest-checklist.md`, in the file's existing item format, after "Evoking II (Part 5)", with these items (each is Will's to check; none is claimed):

1. Shield: three, or nine, vexes with a shield in each hand fly at something big (an enderman, a ravager), take turns lunging, and back off to about six blocks. Do the bashes read as bashes? Is the sound right? Do the shields in both hands look odd on the vex model?
2. Multishot: a zombie with a raised attack (or a pillager captain, a piglin brute) going after one vex: the vex and two friends hold crossbows and fire three arrows at once. Is the spread right? Do the volleys look like a crossbow volley, with the crossbow visible in the vex's hand?
3. Evader: drop below half health (`/damage @s 12`). Do three vexes circle you at two blocks, shove a zombie that gets close, and knock a skeleton's arrow back? Does the circle get in the way of your clicks? Does it end when you are healed?
4. Raid: a ravager (`/summon ravager`) with twelve vexes out. Do they gear up, line up in a 3 × 4 block eight blocks from it facing it, charge together, and do the crossbow ones fire rockets that hurt the ravager and not you? Are the armor and weapons visible?
5. Which mobs trigger what. Walk past an enderman, a witch, a pillager party, a baby zombie with a raised attack: do the moves start when you would want them to, and not when you would not?
6. J and K: J cycles Shield, Multishot, Evader, Raid with the "Team move: …" line; K in automatic mode says "Team moves are automatic"; with `teamMoves = MANUAL` K starts the picked move, and the four refusals read right (already in a move, not ready yet, not enough vexes or no enemy).
7. A move does not fight you for control of your vexes: the empty-hand clicks say "That vex is busy" and change nothing; giving a vex gear still works and the gift waits until the move ends.
8. Rescues in a move: stand in lava with a Shield running; the rescuer pulls you out, and its shields come back when it returns to the move.
9. The moves with the Evoking effect gone: the vexes finish the move.
10. The setting in the in-game config screen: "Team moves" under "Strength of Evoking".

- [ ] **Step 4: Mutation testing**

Run: `./gradlew pitest` (background it if it takes over two minutes; read the summary from its report).
Add a row to `docs/mutation-testing.md` for this part in the file's format: date 2026-11-10, the new classes `TeamMove` and `TeamMoveRules`, killed/total, and each surviving mutant with either the test added to kill it or one line on why it is equivalent. Add the tests that kill non-equivalent survivors, then rerun until only equivalents remain.

- [ ] **Step 5: API notes and spec sentences that the code settled**

Re-read §25 against what Tasks 3–12 found; correct any line the sources contradicted, and add the facts the tasks needed that §25 did not have. In the Parts 6–7 spec, bring these sentences in line with the code as built (and with any divergence the earlier tasks' as-built notes record):

- §2.3: the scan clock is a stored game-time deadline (`nextScan`), not the summoner's `tickCount` (a mock player is never ticked).
- §2.1/§3.2: `TeamMove.NONE` is the last constant, so the packet argument for Shield to Raid is 0 to 3 and 4 is refused; `EvokingRequest.cast/teamMove` are the factories.
- §2.5: the 48-block rule is `TeamMoveRules.tooFar`; the shoves and deflections of an Evader run from `TeamMoves.tick`, not from the members' goals.
- §4.1, §4.3: `Mob.swingForAttack(InteractionHand)` is the swing.
- §4.2: arrow pickup needs nothing set (`AbstractArrow.pickup` defaults to `DISALLOWED`).
- §5: the Raid's dice test runs in its own environment, with one test, because the script is global; the 32-block free range has no GameTest.

- [ ] **Step 6: Whole-plan checks**

Run:

```bash
git diff main --stat -- src/main/java/io/github/willrieske/spellcraft/mixin src/main/resources/META-INF src/main/resources/spellcraft.mixins.json src/main/resources/spellcraft.client.mixins.json
git grep -n "message.spellcraft.team_move\|message.spellcraft.team_moves\|message.spellcraft.vex_busy\|key.spellcraft.team_move\|team_move.spellcraft" -- src/main/resources/assets/spellcraft/lang/en_us.json
```

Expected: the first prints nothing (no Mixin, no access-transformer line); the second lists the keys this plan added: `vex_busy`, `team_move_shield`, `team_move_multishot`, `team_move_evader`, `team_move_raid`, `team_moves_automatic`, `team_move_running`, `team_move_cooling`, `team_move_unavailable`, `team_move_selected`, `key.spellcraft.team_move_cycle`, `key.spellcraft.team_move_go`, `team_move.spellcraft.shield|multishot|evader|raid`, `spellcraft.configuration.evoking`, `spellcraft.configuration.teamMoves`. Check each against the code that uses it (`Grep` the key in `src/main/java`): none missing, none unused.

- [ ] **Step 7: Gates and commit**

Run: `./gradlew spotlessApply build`, `./gradlew runData` (the client-dist boot: it loads the two new key mappings), `./gradlew runGameTestServer` twice.

```bash
git add README.md CLAUDE.md docs src/main/templates
git commit -m "docs(evoking): Part 6 in the README, playtest items, mutation testing, as-built spec notes"
```

---

## Divergence index

<!-- generated by plan_map.py --write-index; do not edit -->

| Task | SHA | Divergence |
|---|---|---|

## Resume State

- Last completed task: none
- Commit SHA: none yet
- Checkboxes: 0/65 ticked
- Next task: Task 1: API notes §25
- Known-failing tests / caveats: none
