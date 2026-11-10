# Evoking II Part 5: World Helpers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bound Vexes guard closer where it is dangerous, pull their summoner out of lava, push them up from drowning, catch them over the End's void, return ghast fireballs, break end crystals, and hit three times as hard in the End.

**Architecture:** Three pure classes in `spell.rules` (`GuardPosture`, `RescuePush`, `VoidRule`) hold every number. `VexRescues` (driven from the player's tick) detects a need, picks the rescuer and applies the push itself; `RescueGoal` (priority −1) only keeps the rescuer at the player's side. The fireball return hangs on `ProjectileImpactEvent`; crystals are a job goal like the golem repair; blast protection and critical hits are event handlers in `BoundVexEvents`.

**Tech Stack:** Minecraft 26.3, NeoForge 26.3.0.23-beta, Java 25, JUnit 5 + jqwik (pure core), NeoForge GameTests (server rules).

**Spec:** `2026-11-09-evoking-ii-parts-4-5-design.md` §5–§9 (Part 5). Parent: `2026-11-07-evoking-ii-design.md` §6.

**Builds on:** Part 4 (branch `feat/evoking-ii-part-4`): `BoundVex.conjure/dismiss/isConjured`, `scanDue`, the claim-map pattern of `RepairGolemGoal`, the friendly-fire backstop in `BoundVexEvents.onIncomingDamage`.

## Global Constraints

- Server owns state. **No packet, no Mixin, no access-transformer line** is added. If a step seems to need one, stop and report.
- Pure core (`spell.rules`): no `net.minecraft`/NeoForge imports (ArchUnit rule 2). TDD; jqwik properties for invariants.
- Error Prone runs with `-Werror`; suppress only with `@SuppressWarnings("<Check>")` plus a comment saying why.
- Javadoc on every class, method and constant, lines ≤ 120 columns; American spelling; plain words.
- Tests follow `~/.claude/development-methodology.md` § Conventions, *Unit test style* (When/should docs, Arrange/Act/Assert, group per method). Mirror `BribeRuleTest` (unit) and `BoundVexCureGameTests` (GameTests).
- Never claim a visual or feel behavior works: add playtest items (Task 10) instead.
- Never bump the version, never create a tag.
- Never run `git checkout`/`git restore`/`git stash` on a file with uncommitted work. Stage and commit in two separate commands. Conventional commit messages, no attribution lines.
- Every vanilla or NeoForge call is checked against `C:\dev\spellcraft\build\mc-src` and `build\nf-src` (the main checkout; this worktree has no copy) before use. `docs/api-notes.md` §24 (Task 1) records what was verified; where this plan's snippet disagrees with the sources, the sources win and the as-built note says so.
- GameTests: inner loop `./gradlew runGameTestServer "-PgameTests=spellcraft:bound_vex_world_*"`; before each task's commit, the full `./gradlew runGameTestServer` **twice** (flakiness), expecting `All N required tests passed` both times. Baseline at the start of this plan: 340 plus whatever Part 4's branch review added (read it from the first full run and record it in Task 1's as-built note).
- GameTest practices that already cost time here: place mobs by absolute `setPos` with no gravity; never `GameTestHelper.onEachTick` (use `BoundVexTestSupport.everyTick`); `thenWaitUntil(N, …)` demands an exact delay, so wait with `thenWaitUntil(() -> …)` or `thenExecuteAfter`; a mock player is never ticked or moved by the server (call `VexRescues.tick(player)` by hand, as `FangGameTests.step` calls `EvokingCaster.tick`).
- Numbers below are requirements copied from the spec. Do not round or "improve" them.
- The snippets leave out imports and the Javadoc of some private constants and fields to stay short. The code you commit has both: Javadoc on every member, private ones included, and no line over 120 columns (`spotlessApply` does not wrap comments).
- Tests in a batch run **at the same time**, about 29 blocks apart, and share every static map. A test never calls a global reset (`onServerStopped`, `clear()`, `clearClaims()`) outside an environment of its own, and never touches another dimension except through `BoundVexWorldTestSupport` (Task 1), which gives each test a private place.
- **World test support as built (Task 1, `2c484b4` and `2b93648`).** `BoundVexWorldTestSupport`: `room(helper, player, dimension, halfWidth, height)` (half-width at most 89), `endVoid(helper, player, reach)` (reach at most 90; keep it small: it clears and restores the flat world's floor, about 50,000 blocks at reach 48), `platform(helper, level, center, halfWidth)`, `set(helper, level, pos, state)`, `vexIn(helper, level, at, summoner)`, `soak(player)`. The GameTest world is flat in **every** dimension (a floor at y 0..3, End included; `endVoid` removes it inside its reach). Every block a test changes in a slot goes through `platform` or `set` (they restore it); a cleanup acts on entities only once they have ticked. A mock player is made unhurtable with `setPermanentlyInvulnerable(true)` (there is no `setInvulnerable` on it), and `isInLava()` and `isUnderWater()` both need `soak`'s two `baseTick` calls. `docs/api-notes.md` §24 has the rest.
- Limits and pauses count game ticks. A test that "steps" a player N times does one step per server tick (`thenExecuteFor(N, …)` or chained `thenExecuteAfter(1, …)`), never a loop inside one tick.

## File Structure

| File | Responsibility |
|---|---|
| `spell/rules/GuardPosture.java` (new) | Where a following vex hovers, how fast it flies there, its scan interval (spec §6.1) |
| `spell/rules/RescuePush.java` (new) | Lava-pull, drowning-push and returned-fireball velocities (spec §6.2, §6.3) |
| `spell/rules/VoidRule.java` (new) | Whether a player needs the void catch (spec §6.4) |
| `evoking/VexRescues.java` (new) | Per-player rescue state machine: need, rescuer, push, tails (spec §5, §6.2, §6.4) |
| `evoking/RescueGoal.java` (new) | Keeps the rescuer at the player's side (priority −1) |
| `evoking/StandableSpots.java` (new) | "Standable spot" test and the two searches (lava shore, void landing) |
| `evoking/VexFireballs.java` (new) | The fireball return on `ProjectileImpactEvent` (spec §6.3) |
| `evoking/AttackCrystalGoal.java` (new) | The end-crystal job goal (spec §7.1) |
| `evoking/BoundVex.java` | `posture()`, `scanInterval()`, the two new goals |
| `evoking/FollowSummonerGoal.java` | Asks the posture |
| `evoking/BoundVexEvents.java` | Crystal-blast immunity, knockback, End critical hits, clearing new transient state |
| the six scanning goals | Read `vex.scanInterval()` |
| `gametest/BoundVexWorldGameTests.java` (new), `BoundVexWorldTestSupport.java` (new) | Part 5 GameTests and their End/Nether rooms |
| `docs/api-notes.md` §24, `README.md`, `docs/playtest-checklist.md`, `docs/mutation-testing.md` | Docs |

All Java paths are under `src/main/java/io/github/willrieske/spellcraft/`; unit tests under `src/test/java/io/github/willrieske/spellcraft/`.

---

### Task 1: API notes §24 and the world test support

**Files:**

- Modify: `docs/api-notes.md` (new §24)
- Create: `gametest/BoundVexWorldTestSupport.java`, `gametest/BoundVexWorldGameTests.java`
- Modify: `gametest/SpellcraftGameTests.java` (register the class; pin the time of day per environment), `gametest/FangGameTests.java` (`STAND` becomes package-private)

**Interfaces:**

- Consumes: `FangGameTests.toNether`, `FangGameTests.addPadded/caster`, `GameTestSupport.whenDone`, `MockPlayers`.
- Produces (all `static`, package-private, in `BoundVexWorldTestSupport`):
  - `ServerLevel room(GameTestHelper helper, ServerPlayer player, ResourceKey<Level> dimension, int halfWidth, int height)`: an air room `(2·halfWidth+1)` wide and `height` tall inside a one-block stone shell, at a **private slot** of `dimension` (below), with the player standing on the middle of its floor.
  - `ServerLevel endVoid(GameTestHelper helper, ServerPlayer player, int reach)`: the player in the End at a private slot with **no blocks at all**; the chunks within `reach` blocks are forced and loaded.
  - `void platform(GameTestHelper helper, ServerLevel level, BlockPos center, int halfWidth)`: an open stone platform (one layer, nothing above it), removed when the test ends.
  - `BoundVex vexIn(GameTestHelper helper, ServerLevel level, Vec3 at, ServerPlayer summoner)`: a full-AI Bound Vex with the sword in another level (as `BoundVexTestSupport.boundVex` does in the test level).
  - `void soak(ServerPlayer player)`: makes a mock player report the fluid it stands in (two `baseTick()` calls, see below).
- Produces in `SpellcraftGameTests`: `static final String NIGHT = "night"` (an environment).

**Private slots.** Concurrent tests must not share ground, chunks, crystals or cleanup in the other dimensions, so a room is not built at the player's own coordinates. Each dimension has a pool of slots: grid points 200 blocks apart; in the End only those whose sideways distance from the origin is between 450 and 850 (the main island ends near 200 and the outer islands start near 1000, so a slot is in empty void), at `y = 80`; in the Nether any grid points from `x = 1000` on, at `y = 100` (the stone shell keeps lava out). `room` and `endVoid` take a free slot and give it back when the test ends; with none free the test fails with "setup: no free slot". Everything a helper does stays inside its slot: it forces and loads (`level.getChunk`, as `toNether` does, so height-map reads are real) only the chunks within the room or `reach`, unforces exactly those, restores exactly the blocks it replaced, discards every `Vex`, `EndCrystal`, `ItemEntity`, `Projectile` and other non-player entity within `reach + 8` (rooms: `halfWidth + 8`) of the slot, and moves the player back to `FangGameTests.STAND`. No test may use a `reach` over 90.

- [x] **Step 1: Verify the facts and write `docs/api-notes.md` §24**

Check each line below in the sources (cite `file:line` in the note). These were read once already; confirm them.

```markdown
## 24. Evoking II Part 5 (world helpers)

- **Velocity sync.** `Entity.syncVelocity` (public boolean field; `hurtMarked` does not exist). `ServerEntity.sendChanges`
  sends `ClientboundSetEntityMotionPacket` to tracking players *and the entity's own client*, then clears the flag.
  Setting the velocity alone sends nothing.
- **What a player is doing.** A `ServerPlayer`'s `getDeltaMovement()` is not the client's velocity: the client moves
  itself. `ServerPlayer.getKnownMovement()` is the last movement the client reported
  (`ServerGamePacketListenerImpl.handleMovePlayer` → `setKnownMovement`). A push reads the known movement, adds to it,
  and sends the sum.
- **Fluids on a mock player.** `Entity.isInLava()` is `!firstTick && fluidInteraction.isInFluid(lava)`;
  `isUnderWater()` is `wasEyeInWater && isInWater()`. Both are filled by `baseTick()` (public); `updateFluidInteraction`
  is protected. A GameTest mock player is never ticked, so a test calls `baseTick()` (twice for `isUnderWater`, because
  `wasEyeInWater` is read before the update). `LivingEntity.baseTick` also burns and drowns: tests make the player
  invulnerable first.
- **`ProjectileImpactEvent`** (`net.neoforged.neoforge.event.entity`, cancellable; `getProjectile()`,
  `getRayTraceResult()`). Vanilla posts it from `AbstractHurtingProjectile.tick()` through
  `EventHooks.onProjectileImpact(Projectile, HitResult)` (true = cancelled). Cancelled: the projectile is not
  discarded, moves by its velocity this tick and tests again next tick.
- **`Projectile.deflect(ProjectileDeflection, @Nullable Entity deflector, @Nullable EntityReference<Entity> newOwner,
  boolean byAttack, double power)`**: runs the deflection, sets the owner, calls `onDeflection(byAttack)`; always true.
  `ProjectileDeflection` is a functional interface `(projectile, entity, random, power)`.
  `AbstractHurtingProjectile.onDeflection(true)` sets `accelerationPower = 0.1`. `REVERSE` halves the speed;
  `AIM_DEFLECT` aims along the deflector's look.
- **Ghast one-hit rule.** `Ghast.isReflectedFireball`: the direct entity is a `LargeFireball` and the causing entity a
  `Player`; then `hurtServer` deals 1000.
- **`ExplosionKnockbackEvent`** (`net.neoforged.neoforge.event.level`, not cancellable): `getAffectedEntity()`,
  `getKnockbackVelocity()`, `setKnockbackVelocity(Vec3)`, `getExplosion()` (a `ServerExplosion`:
  `getDirectSourceEntity()`, `getIndirectSourceEntity()`, `getDamageSource()`). Posted after the damage, before
  `pushFromExplosion`.
- **`EndCrystal.hurtServer`**: removes the crystal; unless the source is itself an explosion, explodes with power 6,
  the crystal as the explosion's source, and `damageSources().explosion(crystal, source.getEntity())` as the damage
  source (direct entity = the crystal, causing entity = the attacker). `isInvulnerable()` crystals ignore it.
- **`EnderDragonFight`** (not `EndDragonFight`): `ServerLevel.getDragonFight()` (null outside the End),
  `dragonUUID()` (nullable), `level.getEntity(UUID)`.
- **`Consumable.onConsume(Level, LivingEntity, ItemStack)`**: sound and particles, the item-used statistic and the
  consume-item trigger for a `ServerPlayer`, the consume effects, a game event, then `stack.consume(1, user)`. The
  enchanted golden apple (`DataComponents.CONSUMABLE` on its stack): Regeneration II 400 ticks, Resistance 6000,
  Fire Resistance 6000, Absorption IV 2400.
- **Height map.** `Level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)` is the first free Y (top block
  + 1); an empty or unloaded column gives `getMinY()`.
- **`Level.isDarkOutside()`**: false in a fixed-time dimension (Nether, End); otherwise sky darkening ≥ 4 (night, or a
  thunderstorm).
- **Goal priorities.** A negative priority is legal. A goal starts only if every current holder of its flags is
  interruptable and has a strictly higher priority number; the holders are stopped.
- **Clip.** `new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)`; a miss has
  `HitResult.Type.MISS`.
```

Add to §24 anything else you had to look up for Step 2 (the environment classes, how the GameTest server treats the daylight cycle).

- [x] **Step 2: Pin the time of day per environment**

A night test must not change the time under the other tests, and today's follow tests must never run at night. In `SpellcraftGameTests`:

The GameTest server advances the time of day (`GameTestServer` turns off only mob spawning and the weather cycle; `ADVANCE_TIME` defaults to true), so both pins are needed. Vanilla's own definition, `TestEnvironmentDefinition.ClockTime(Holder<WorldClock>, int)`, cannot be used here: `RegisterGameTestsEvent.registerEnvironment` hands out no clock holder and `ServerClockManager.getInstance` rejects any holder but the registry's own. So:

1. Read `TestEnvironmentDefinition` (`setup`, `teardown`, `codec`), `ClockTime` and `ServerClockManager` in `build/mc-src`, and `RegisterGameTestsEvent.registerEnvironment` in `build/nf-src`.
2. Add a package-private record `gametest/OverworldTime(int time) implements TestEnvironmentDefinition<Long>` (use the interface's real type parameter and method signatures): `setup(level)` looks the Overworld clock up from the level's own registry (`level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD)`), remembers the clock manager's current total ticks for it, and sets the clock so the time of day is `time`; `teardown` puts the remembered value back; `codec()` returns a unit codec (the codec only serves data packs). Do what `ClockTime.setup` does, with the holder looked up at setup time.
3. Add `static final String NIGHT = "night";` (Javadoc: "Tests that need the Overworld dark; a batch of its own.").
4. In `register`, give every environment a time: `18000` for `NIGHT`, `6000` for all others:

```java
BY_ENVIRONMENT.forEach((environment, registered) -> {
    int time = environment.equals(NIGHT) ? 18000 : 6000;
    Holder<TestEnvironmentDefinition<?>> holder =
            event.registerEnvironment(Spellcraft.id(environment), new OverworldTime(time));
    registered.forEach(entry -> test(event, holder, entry));
});
```

Add one test to prove both pins: `bound_vex_world_setup_night` (environment `NIGHT`) asserts `helper.getLevel().isDarkOutside()` after 2 ticks, and `bound_vex_world_setup_day` (`SHARED`) asserts it is false. If the clock cannot be set this way, report what the sources allow before trying anything else (a Mixin or a game-rule change is not acceptable).

- [x] **Step 3: Write the support class and three setup tests (they fail to compile first)**

`BoundVexWorldGameTests.registerAll()` (added to the static block in `SpellcraftGameTests`, after `BoundVexCureGameTests`) registers, through `FangGameTests.addPadded(SpellcraftGameTests.SHARED, …)`:

- `bound_vex_world_setup_end_room`: `caster(helper, 0)`; `ServerLevel end = room(helper, player, Level.END, 3, 4)`. Assert: `player.level() == end`, `end.dimension() == Level.END`, the block under the player's feet is stone, the two blocks at the feet are air, `player.isAlive()`; a `vexIn(helper, end, player.position().add(1, 1, 0), player)` is alive after 5 ticks and `vex.summoner().isPresent()`.
- `bound_vex_world_setup_end_void`: `endVoid(helper, player, 48)`; assert that every chunk within 32 blocks is loaded (`end.hasChunk`) and that `end.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz) == end.getMinY()` for every `dx, dz` in `-32..32` step 8 (nothing to land on); after `platform(helper, end, player.blockPosition().offset(5, -3, 0), 1)` the height at that column is the platform's top + 1; the player is alive after 5 ticks (a mock player does not fall).
- `bound_vex_world_setup_two_places_are_apart`: two `endVoid` calls for two players in one test give places at least 200 blocks apart.
- `bound_vex_world_setup_fluids`: in the test level, lava at `FEET` and `FEET.above()` with stone on all four sides of both blocks (lava spreads into air within 30 ticks) → after `player.setPermanentlyInvulnerable(true)` and `soak(player)`: `player.isInLava()`. Then water in both blocks (replace the lava), `soak(player)` → `player.isUnderWater()`. Then air, `soak(player)` → neither.

`room` is `FangGameTests.toNether` generalized (read it, lines 243–304): same recording of replaced blocks, same forcing and loading, same `whenDone` restore, at a private slot instead of the player's coordinates. `endVoid` takes a slot and builds nothing. Make `FangGameTests.STAND` package-private for the move back.

```java
/**
 * Makes a mock player report the fluid at its feet and eyes: the server never ticks a mock player, and the fluid
 * state is filled by {@code baseTick} (docs/api-notes.md §24). Twice, because {@code isUnderWater} reads the eye
 * state the previous tick left. The player must be invulnerable: {@code baseTick} also burns and drowns.
 */
static void soak(ServerPlayer player) {
    player.baseTick();
    player.baseTick();
}
```

- [x] **Step 4: Run the three tests**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:bound_vex_world_setup_*"`
Expected: `All 6 required tests passed`. If `soak` does not make `isInLava()` true, read `Entity.baseTick` and `EntityFluidInteraction` and fix `soak` (public calls only); record what worked in §24.

- [x] **Step 5: Full gates, then commit**

Run: `./gradlew build`, then `./gradlew runGameTestServer` twice. Expected: build green; all tests pass both times (baseline + 6).

```bash
git add docs/api-notes.md src/main/java/io/github/willrieske/spellcraft/gametest
git commit -m "test(evoking): End and Nether rooms, fluid stepping and pinned time for Part 5; api-notes 24"
```

> **As built (`2c484b4`):** diverged — the GameTest world is flat in every dimension (a floor at y 0..3 even in the End), so `endVoid` clears the blocks below its slot in the forced chunks and restores them; `setInvulnerable` does not exist (`setPermanentlyInvulnerable(true)`); `isInLava` also needs the second `baseTick`; `Ghast` is in `monster`; the night setup test is registered unpadded. Baseline 349, now 355; reason: the sources and the server as they are.

---

### Task 2: The pure core (`GuardPosture`, `RescuePush`, `VoidRule`)

**Files:**

- Create: `spell/rules/GuardPosture.java`, `spell/rules/RescuePush.java`, `spell/rules/VoidRule.java`
- Test: `spell/rules/GuardPostureTest.java`, `RescuePushTest.java`, `VoidRuleTest.java` (under `src/test/java/…`)

**Interfaces:**

- Produces:
  - `GuardPosture.Place { OVERWORLD, NETHER, END, OTHER }`; `static GuardPosture of(Place place, boolean dark)`; accessors `sideMin() sideMax() heightMin() heightMax() speed() scanInterval() close()`; `boolean inPlace(double dx, double dy, double dz)`; `double sideways(double roll)`; `double height(double roll)`; constant `GuardPosture.WIDE`.
  - `RescuePush.Vector(double x, double y, double z)`; `static Vector lavaPull(Vector v, double toSpotX, double toSpotZ)`; `static Vector lavaLift(Vector v)`; `static Vector drowningPush(Vector v)`; `static Vector returned(Vector old, double headingX, double headingY, double headingZ)`.
  - `VoidRule.needsCatch(double y, int minY, boolean onGround, boolean movingDown, boolean flying, BooleanSupplier groundWithin64)`.

- [x] **Step 1: Write the failing tests**

`GuardPostureTest` (examples, one test per row, then properties):

```java
/** When the place is the Overworld by day, should give today's wide band at the 10-tick scan. */
@Test
void of_overworldDay_isTheWideRow() {
    GuardPosture posture = GuardPosture.of(GuardPosture.Place.OVERWORLD, false);

    assertEquals(2.0, posture.sideMin());
    assertEquals(3.5, posture.sideMax());
    assertEquals(0.5, posture.heightMin());
    assertEquals(1.5, posture.heightMax());
    assertEquals(0.7, posture.speed());
    assertEquals(10, posture.scanInterval());
    assertFalse(posture.close());
}
```

Same shape for: Overworld dark → `1, 2, 2.2, 3.0, 0.35, 4, close`; Nether (dark true and false) → `1, 2, 2.2, 3.0, 0.35, 4, close`; End (both) → `0.5, 1.5, 2.2, 3.0, 0.35, 2, close`; OTHER (both) → the wide row.

`inPlace` examples: wide row: `(4, 0, 0)` true, `(4.01, 0, 0)` false, `(0, 0, 0)` true (inside the band is left alone, as today), `(3, 3, 0)` false (distance 4.24). Dark Overworld row: `(2.5, 2.2, 0)` true (outer edge + 0.5), `(2.51, 2.2, 0)` false, `(1, 1.7, 0)` true (height range widened by 0.5; in doubles `2.2 − 0.5` is `1.7000000000000002`, which is why the code compares with a tiny tolerance), `(1, 1.69, 0)` false, `(1, 3.5, 0)` true, `(1, 3.51, 0)` false, `(0, 2.5, 0)` true (no inner limit: directly overhead is in place).

`sideways`/`height`: `sideways(0) == sideMin`, `sideways(1) == sideMax`, `height(0) == heightMin`, `height(1) == heightMax`.

jqwik properties (over every `Place` × `dark`, rolls in `[0, 1]`, angle in `[0, 2π)`):

```java
/** Every spot the posture hands out should count as in place, so one hop ends the move. */
@Property
void spot_isAlwaysInPlace(
        @ForAll GuardPosture.Place place,
        @ForAll boolean dark,
        @ForAll @DoubleRange(min = 0, max = 1) double sideRoll,
        @ForAll @DoubleRange(min = 0, max = 1) double heightRoll,
        @ForAll @DoubleRange(min = 0, max = 6.283) double angle) {
    GuardPosture posture = GuardPosture.of(place, dark);
    double side = posture.sideways(sideRoll);

    boolean inPlace = posture.inPlace(Math.cos(angle) * side, posture.height(heightRoll), Math.sin(angle) * side);

    assertTrue(inPlace);
}
```

Further properties: `sideMin < sideMax` and `heightMin < heightMax`; every `scanInterval` is even and ≥ 2; no posture has a wider band (`sideMax`) or a slower scan (larger `scanInterval`) than `WIDE`; in a `close` posture every `height(roll)` is ≥ 2.2.

`RescuePushTest` examples:

- `lavaPull((0,0,0), 3, 0)` → `(0.1, 0.3, 0)`.
- `lavaPull((0.3,0,0), 3, 0)` → x `0.35` (capped), y `0.3`.
- `lavaPull((0.5,0,0), 3, 0)` → x `0.5` (already faster than the cap: not sped up, not slowed), y `0.3`.
- `lavaPull((0,0,0.2), 3, 0)` → `(0.1, 0.3, 0.2)` (keeps the sideways velocity it had).
- `lavaPull((0,0.6,0), 3, 0)` → y `0.6` (never lowered).
- `lavaPull((0,0,0), 0, 0)` → `(0, 0.3, 0)` (a spot directly above or below adds nothing sideways; no NaN).
- `lavaLift((0.2,-0.1,0.1))` → `(0.2, 0.4, 0.1)`.
- `drowningPush((0.2,-0.1,0.1))` → `(0.2, 0.4, 0.1)`; `drowningPush((0,0.9,0))` → y `0.9`.
- `returned((0,0,-1.5), 0, 3, 4)` → `(0, 0.9, 1.2)` (speed 1.5 along the unit heading `(0, 0.6, 0.8)`).
- `returned((1,0,0), 0, 0, 0)` → `(-1, 0, 0)` (no heading: straight back).

Properties (finite inputs in `[-10, 10]`): the sideways speed after `lavaPull` is ≤ `max(0.35, sideways speed before) + 1e-9`; `y` never decreases in `lavaPull`, `lavaLift`, `drowningPush`; no component of any result is NaN; `returned` keeps the speed (`|result| == |old|` within `1e-9`).

`VoidRuleTest` (with `minY = 0`, supplier `() -> false` unless said): `y = -4.01` → true even on the ground flag false/true and not moving down; `y = -4` standing → false; falling (`onGround false, movingDown true`) at `y = 15.9` with no ground → true; at `y = 16` → false; with ground within 64 → false; `onGround true` → false; `movingDown false` → false; `flying true` → false in every case above, including `y = -100`. One test asserts the supplier is **not called** when `y ≥ minY + 16` (it is a block ray: `AtomicInteger` counter stays 0).

- [x] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests "*GuardPostureTest" --tests "*RescuePushTest" --tests "*VoidRuleTest"`
Expected: compilation fails (the classes do not exist).

- [x] **Step 3: Implement**

```java
package io.github.willrieske.spellcraft.spell.rules;

/**
 * Where a following Bound Vex hovers around its summoner, how fast it flies there and how often it scans (Parts 4–5
 * spec §6.1). Distances are from the summoner's feet: sideways in the horizontal plane, height straight up. In the
 * three close postures the band is above the summoner's head, out of the way of their clicks.
 *
 * @param sideMin the band's inner sideways edge
 * @param sideMax the band's outer sideways edge
 * @param heightMin the band's lowest height above the summoner's feet
 * @param heightMax the band's highest height above the summoner's feet
 * @param speed the move-control speed toward a spot
 * @param scanInterval ticks between scans; even, because the goal clock runs every second tick
 * @param close whether this is one of the three close, overhead postures
 */
public record GuardPosture(
        double sideMin, double sideMax, double heightMin, double heightMax, double speed, int scanInterval,
        boolean close) {
    /** The kind of dimension a vex is in; a modded dimension is {@link #OTHER}. */
    public enum Place {
        OVERWORLD,
        NETHER,
        END,
        OTHER
    }

    /** The Overworld by day, and any other dimension: the geometry of Parts 1–4. */
    public static final GuardPosture WIDE = new GuardPosture(2.0, 3.5, 0.5, 1.5, 0.7, 10, false);

    private static final GuardPosture NEAR = new GuardPosture(1.0, 2.0, 2.2, 3.0, 0.35, 4, true);
    private static final GuardPosture NEAREST = new GuardPosture(0.5, 1.5, 2.2, 3.0, 0.35, 2, true);

    /** In the wide posture a vex farther than this from the summoner's feet moves (feet to feet, as before Part 5). */
    private static final double WIDE_LIMIT = 4.0;

    /** How far outside a close band a vex may hang before it moves. */
    private static final double SLACK = 0.5;

    /** Absorbs floating-point error at the band's edges (2.2 − 0.5 is not exactly 1.7 in doubles). */
    private static final double TOLERANCE = 1.0e-9;

    /**
     * The posture for a place.
     *
     * @param place the kind of dimension
     * @param dark whether it is dark outside (night or a thunderstorm); only the Overworld asks
     * @return the posture
     */
    public static GuardPosture of(Place place, boolean dark) {
        return switch (place) {
            case OVERWORLD -> dark ? NEAR : WIDE;
            case NETHER -> NEAR;
            case END -> NEAREST;
            case OTHER -> WIDE;
        };
    }

    /**
     * Whether a vex at this offset from the summoner's feet is where it should be. Wide: within 4 blocks. Close: at
     * most the outer edge plus 0.5 sideways, and within the height range widened by 0.5 each way.
     *
     * @param dx blocks east of the summoner's feet
     * @param dy blocks above the summoner's feet
     * @param dz blocks south of the summoner's feet
     * @return true when the vex need not move; false for NaN
     */
    public boolean inPlace(double dx, double dy, double dz) {
        if (!close) {
            return dx * dx + dy * dy + dz * dz <= WIDE_LIMIT * WIDE_LIMIT;
        }
        double limit = sideMax + SLACK + TOLERANCE;
        return dx * dx + dz * dz <= limit * limit
                && dy >= heightMin - SLACK - TOLERANCE
                && dy <= heightMax + SLACK + TOLERANCE;
    }

    /**
     * A sideways distance inside the band.
     *
     * @param roll a unit roll, 0 to 1
     * @return the distance, from the inner edge (0) to the outer (1)
     */
    public double sideways(double roll) {
        return sideMin + roll * (sideMax - sideMin);
    }

    /**
     * A height inside the band.
     *
     * @param roll a unit roll, 0 to 1
     * @return the height above the summoner's feet
     */
    public double height(double roll) {
        return heightMin + roll * (heightMax - heightMin);
    }
}
```

```java
package io.github.willrieske.spellcraft.spell.rules;

/**
 * The velocities a rescuing Bound Vex gives (Parts 4–5 spec §6.2, §6.3). Every push adds to what the player is
 * already doing, so they keep steering; none ever lowers the upward speed.
 */
public final class RescuePush {
    /** Sideways speed added toward the shore each tick of a lava pull. */
    public static final double PULL = 0.1;

    /** The lava pull never speeds the player past this sideways. */
    public static final double PULL_CAP = 0.35;

    /** The least upward speed during a lava pull toward a shore. */
    public static final double PULL_UP = 0.3;

    /** The least upward speed during a drowning push, and a lava pull with no shore. */
    public static final double LIFT = 0.4;

    private static final double TINY = 1.0e-6;

    /**
     * A velocity or direction, in blocks per tick.
     *
     * @param x east
     * @param y up
     * @param z south
     */
    public record Vector(double x, double y, double z) {}

    private RescuePush() {}

    /**
     * One tick of the lava pull toward a shore: adds {@link #PULL} sideways toward the spot, keeps the sideways speed
     * at or under the larger of {@link #PULL_CAP} and what it already was, and raises the upward speed to at least
     * {@link #PULL_UP}. A spot straight above or below adds nothing sideways.
     *
     * @param v the player's velocity
     * @param toSpotX blocks east from the player to the spot
     * @param toSpotZ blocks south from the player to the spot
     * @return the pushed velocity
     */
    public static Vector lavaPull(Vector v, double toSpotX, double toSpotZ) {
        double distance = Math.hypot(toSpotX, toSpotZ);
        double x = v.x();
        double z = v.z();
        if (distance > TINY) {
            x += PULL * toSpotX / distance;
            z += PULL * toSpotZ / distance;
            double limit = Math.max(PULL_CAP, Math.hypot(v.x(), v.z()));
            double speed = Math.hypot(x, z);
            if (speed > limit) {
                x *= limit / speed;
                z *= limit / speed;
            }
        }
        return new Vector(x, Math.max(v.y(), PULL_UP), z);
    }

    /**
     * One tick of the lava pull with no shore in reach: only the upward speed, raised to at least {@link #LIFT}.
     *
     * @param v the player's velocity
     * @return the pushed velocity
     */
    public static Vector lavaLift(Vector v) {
        return new Vector(v.x(), Math.max(v.y(), LIFT), v.z());
    }

    /**
     * One tick of the drowning push: the upward speed raised to at least {@link #LIFT}, the rest kept.
     *
     * @param v the player's velocity
     * @return the pushed velocity
     */
    public static Vector drowningPush(Vector v) {
        return new Vector(v.x(), Math.max(v.y(), LIFT), v.z());
    }

    /**
     * A returned fireball's velocity: its old speed along the new heading; with no heading (zero length), straight
     * back the way it came.
     *
     * @param old the fireball's velocity
     * @param headingX the new heading, east
     * @param headingY the new heading, up
     * @param headingZ the new heading, south
     * @return the new velocity
     */
    public static Vector returned(Vector old, double headingX, double headingY, double headingZ) {
        double length = Math.sqrt(headingX * headingX + headingY * headingY + headingZ * headingZ);
        if (length <= TINY) {
            return new Vector(-old.x(), -old.y(), -old.z());
        }
        double speed = Math.sqrt(old.x() * old.x() + old.y() * old.y() + old.z() * old.z());
        return new Vector(speed * headingX / length, speed * headingY / length, speed * headingZ / length);
    }
}
```

```java
package io.github.willrieske.spellcraft.spell.rules;

import java.util.function.BooleanSupplier;

/** Whether a player over the End's void needs the catch (Parts 4–5 spec §6.4). */
public final class VoidRule {
    /** Below the level's lowest Y by more than this, the catch is certain. */
    public static final int BELOW_FLOOR = 4;

    /** A falling player under the lowest Y plus this is checked for ground below. */
    public static final int NEAR_FLOOR = 16;

    private VoidRule() {}

    /**
     * Whether the player needs catching: never while flying; always once they are more than {@link #BELOW_FLOOR}
     * under the level's floor; and when they are in the air, moving down, under the floor plus {@link #NEAR_FLOOR}
     * and there is no ground within 64 blocks below them.
     *
     * @param y the player's feet
     * @param minY the level's lowest Y
     * @param onGround whether the player stands on something
     * @param movingDown whether the player's vertical movement is downward
     * @param flying whether the player flies (elytra or creative flight)
     * @param groundWithin64 a block ray straight down; asked only when everything else holds
     * @return whether to catch them
     */
    public static boolean needsCatch(
            double y, int minY, boolean onGround, boolean movingDown, boolean flying, BooleanSupplier groundWithin64) {
        if (flying) {
            return false;
        }
        if (y < minY - BELOW_FLOOR) {
            return true;
        }
        return !onGround && movingDown && y < minY + NEAR_FLOOR && !groundWithin64.getAsBoolean();
    }
}
```

- [x] **Step 4: Run the tests**

Run: `./gradlew test --tests "*GuardPostureTest" --tests "*RescuePushTest" --tests "*VoidRuleTest"`
Expected: all pass.

- [x] **Step 5: Build and commit**

Run: `./gradlew spotlessApply build`
Expected: BUILD SUCCESSFUL (ArchUnit rule 2 holds: no Minecraft import).

```bash
git add src/main/java/io/github/willrieske/spellcraft/spell/rules src/test/java/io/github/willrieske/spellcraft/spell/rules
git commit -m "feat(evoking): guard posture, rescue pushes and the void rule (pure core)"
```

> **As built (`deed037`):** matches plan.

---

### Task 3: Posture: where a following vex hovers and how often it scans

**Files:**

- Modify: `evoking/BoundVex.java`, `evoking/FollowSummonerGoal.java`, `evoking/AllyFightGoal.java`, `HurtBySummonerSideGoal.java`, `NearestHostileGoal.java`, `RepairGolemGoal.java`, `CureZombieVillagerGoal.java`, `BribeGoal.java`
- Modify: `gametest/BoundVexWorldGameTests.java`

**Interfaces:**

- Consumes: `GuardPosture` (Task 2); `room`, `vexIn`, `NIGHT` (Task 1).
- Produces: `GuardPosture BoundVex.posture()` (public, for GameTests); `int BoundVex.scanInterval()` (public). `BoundVex.SCAN_INTERVAL` stays, used only by `SupportGearGoal` (its 40-tick throw cooldown makes a faster look pointless; say so in its Javadoc).

- [x] **Step 1: Write the failing GameTests**

Register with `FangGameTests.addPadded` (`SHARED` unless said):

- `bound_vex_world_posture_overworld_day`: `caster`; `followOnly(helper, FEET.above(2), player)`; assert `vex.posture() == GuardPosture.WIDE` and `vex.scanInterval() == 10`.
- `bound_vex_world_posture_night` (environment `NIGHT`): assert first `helper.getLevel().isDarkOutside()` ("setup: it is night"). A `followOnly` vex 6 blocks north of the player at feet height: `thenWaitUntil` it is in place (`vex.posture().inPlace(offset.x, offset.y, offset.z)` with `offset = vex.position() − player.position()`), then for the following 60 ticks (`thenExecuteFor(60, …)`) its feet are at least `1.7` above the player's feet (the spec's assertion) and, as a guard against a runaway only, its sideways distance is under `5`; `scanInterval() == 4`. The test also records the largest sideways distance it saw in those 60 ticks; put that number in the task's as-built note (a vex at speed 0.35 coasts past its spot, and whether it stays inside the band's 0.5 slack or hops again is a measurement the spec's author needs, not something to assert here).
- `bound_vex_world_posture_nether`: `room(helper, player, Level.NETHER, 4, 5)`; `vexIn` 3 blocks east of the player at feet height, target goals removed (`BoundVexTestSupport.noTargets`): same assertions and the same recorded number as the night test; `scanInterval() == 4`.
- `bound_vex_world_posture_end`: `room(helper, player, Level.END, 4, 5)`: in place, then 60 ticks at least `1.7` above the feet, sideways under `5`, the number recorded; `scanInterval() == 2`.
- `bound_vex_world_posture_staying_scans_by_place`: in the End room a staying vex (`vex.setStaying(true)`) does not move for 40 ticks (within 0.5 of where it was) and `scanInterval() == 2`.
- `bound_vex_world_scan_cadence_end`: in the End room, a parked vex (`goalSelector.removeAllGoals`) and a husk 3 blocks away with no AI and no gravity: the vex has the husk as target within **3 ticks** (`thenExecuteAfter(3, …)`); the same setup in `bound_vex_world_scan_cadence_overworld_day` asserts only "within 11 ticks" (the old cadence still works).

- [x] **Step 2: Run them to see them fail**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:bound_vex_world_posture_*"`
Expected: compilation fails (`posture()` does not exist).

- [x] **Step 3: `BoundVex.posture()` and `scanInterval()`**

```java
/**
 * Where this vex hovers and how often it scans, by where it is (Parts 4–5 spec §6.1): the Overworld by day and any
 * modded dimension keep the wide posture; the dark Overworld, the Nether and the End close in overhead.
 *
 * @return the posture for this vex's level, now
 */
public GuardPosture posture() {
    ResourceKey<Level> dimension = level().dimension();
    GuardPosture.Place place = dimension.equals(Level.OVERWORLD)
            ? GuardPosture.Place.OVERWORLD
            : dimension.equals(Level.NETHER)
                    ? GuardPosture.Place.NETHER
                    : dimension.equals(Level.END) ? GuardPosture.Place.END : GuardPosture.Place.OTHER;
    return GuardPosture.of(place, level().isDarkOutside());
}

/**
 * Ticks between this vex's scans: the posture's (10, 4 or 2). Even, so it lands on the goal clock
 * ({@link #scanDue}).
 *
 * @return the scan interval
 */
public int scanInterval() {
    return posture().scanInterval();
}
```

In the six goals listed under Files, replace `vex.scanDue(BoundVex.SCAN_INTERVAL)` with `vex.scanDue(vex.scanInterval())`. Update `SCAN_INTERVAL`'s Javadoc ("the wide posture's interval; the support goal's fixed cadence") and `scanDue`'s ("every 10 ticks" → "the posture's interval").

- [x] **Step 4: `FollowSummonerGoal` asks the posture**

Replace `canUse`'s last line and `flyToOrbit`; delete `ORBIT_SPOT_RADIUS` and `SPEED`. **Keep** `ORBIT_MIN`, `ORBIT_MAX`, `TELEPORT_DISTANCE`, `TELEPORT_SPREAD` and `TELEPORT_LANDING_MAX` with their values: the follow, gear and job GameTests read them (`ORBIT_MIN = GuardPosture.WIDE.sideMin()`; `ORBIT_MAX = 4.0`, documented as the wide posture's limit). Update the class Javadoc to describe the posture.

```java
@Override
public boolean canUse() {
    if (vex.isStaying() || vex.getTarget() != null || vex.getMoveControl().hasWanted()) {
        return false;
    }
    GuardPosture posture = vex.posture();
    return vex.summoner()
            .filter(owner -> {
                Vec3 offset = vex.position().subtract(owner.position());
                return !posture.inPlace(offset.x, offset.y, offset.z);
            })
            .isPresent();
}

/** A random spot inside the posture's band, measured from the summoner's feet as {@code canUse} measures. */
private void flyToOrbit(ServerPlayer owner) {
    RandomSource random = vex.getRandom();
    GuardPosture posture = vex.posture();
    double angle = random.nextDouble() * Math.PI * 2.0;
    double side = posture.sideways(random.nextDouble());
    Vec3 spot = owner.position()
            .add(Math.cos(angle) * side, posture.height(random.nextDouble()), Math.sin(angle) * side);
    vex.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, posture.speed());
}
```

`start()` keeps the teleport rule (`vex.distanceTo(owner) > TELEPORT_DISTANCE`).

- [x] **Step 5: Run the new tests, then every follow test**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:bound_vex_world_*"` then `"-PgameTests=spellcraft:bound_vex_follow_*"` (use the real prefix of `BoundVexFollowGameTests`).
Expected: all pass. If a close-posture vex overshoots its band (the "60 ticks" assertion), do not widen the assertion: report it with the measured positions; the fix is a spec ruling, not a test edit.

- [x] **Step 6: Full gates, then commit**

Run: `./gradlew spotlessApply build`, `./gradlew runGameTestServer` twice.

```bash
git add src/main/java/io/github/willrieske/spellcraft/evoking src/main/java/io/github/willrieske/spellcraft/gametest
git commit -m "feat(evoking): Bound Vexes close in overhead and scan faster at night, in the Nether and the End"
```

> **As built (`9fa69ec`):** diverged — a close-posture flight is one goal use that ends on arrival, where the velocity is zeroed (a coasting vex sank to 1.5 above the feet), and a target mid-flight drops the wanted position; the close bands start at 2.5, not 2.2 (the move control stops up to half a block short). Measured after: min height 1.88, max sideways 2.46, no hops in 60 ticks. Review fixes for Task 2 are in `cce1ddc`. 366 tests; reason: coordinator rulings on the measurements, recorded in spec §6.1 and §10.

---

### Task 4: Rescues: the lava pull and the Nether's water bucket

**Files:**

- Create: `evoking/VexRescues.java`, `evoking/RescueGoal.java`, `evoking/StandableSpots.java`
- Modify: `evoking/BoundVex.java` (`registerGoals`), `evoking/BoundVexEvents.java` (`onServerStopped`)
- Modify: `gametest/BoundVexWorldGameTests.java`

**Interfaces:**

- Consumes: `RescuePush` (Task 2), `BoundVex.conjure/dismiss/isConjured`, `soak`, `room`, `vexIn`.
- Produces:
  - `public static void VexRescues.tick(ServerPlayer player)`; `public static Optional<VexRescues.Kind> VexRescues.running(ServerPlayer player)`; `public static Optional<BoundVex> VexRescues.rescuer(ServerPlayer player)`; `static Optional<ServerPlayer> VexRescues.rescuedBy(BoundVex vex)`; `static void VexRescues.clear()`; `enum Kind { LAVA, DROWNING, VOID }`.
  - `static boolean StandableSpots.standable(Level level, BlockPos feet)`; `static Optional<BlockPos> StandableSpots.shore(ServerLevel level, Vec3 from)`.
  - Tasks 5 and 6 add their kinds to `VexRescues`; write `startFirstNeeded` so a kind is one more branch.

**Design, read before coding:**

- `tick(player)` order (spec §6.2): (1) a dead or spectating player: drop their rescue and return; (2) record the last ground position when `player.onGround()` and `standable(level, player.blockPosition())` (kept per player with its dimension; Task 6 reads it); (3) a running rescue: if its rescuer no longer holds (dead, removed, another level, staying, or no longer this player's), drop it and return (the next tick may start another); otherwise carry it on and return; (4) otherwise start the first need that holds and has a rescuer: void, lava, drowning (Tasks 6, this one, 5).
- **Who gets the lava pull:** in the Overworld or the Nether, `player.isInLava()`, not creative, no `MobEffects.FIRE_RESISTANCE`, not `isPassenger()`, and no lava pause running for this player.
- **Rescuer:** the nearest `BoundVex` with `isAlive()`, `!isStaying()`, `isSummonedBy(player.getUUID())`, within 16 blocks (`distanceTo`), same level. At the start it is placed at the player's side (below) and its velocity zeroed.
- **The player's velocity** is `player.getKnownMovement()` (api-notes §24), not `getDeltaMovement()`. A push is `player.setDeltaMovement(pushed); player.syncVelocity = true;`.
- **Each tick of a lava rescue:** if the player is in lava: reset the out-of-lava count, find `StandableSpots.shore`, and push with `RescuePush.lavaPull` (a spot) or `lavaLift` (none). If not: count the tick, no push. End when the count reaches 10, or the player is out of lava and `onGround()`, or 60 ticks after the start.
- **End of a lava rescue:** no lava rescue starts for this player for 20 ticks. In the Nether, when the player is out of lava: the bucket, once (conjure `Items.WATER_BUCKET` in the rescuer's main hand unless something is already conjured there, `SoundEvents.BUCKET_EMPTY` and `FIRE_EXTINGUISH` at the player, 12 `ParticleTypes.SPLASH` on the player, `player.clearFire()`), and the rescue lives on as a **tail** for 20 ticks (the rescuer stays at the player's side holding the bucket; the tail's end dismisses the bucket only if this rescue conjured it). Otherwise the rescue is dropped at once.
- **`RescueGoal`** (priority −1, flags `MOVE` and `LOOK`, `requiresUpdateEveryTick`): `canUse` and `canContinueToUse` are `VexRescues.rescuedBy(vex).isPresent()`; `start` calls `vex.getMoveControl().setWait()`; `tick` sets the vex's position to the player's side, zeroes its velocity and looks at the player. The side: 0.6 blocks to the right of the way the player faces and 1.2 above their feet. With yaw `θ` in degrees (0 = south), the right-hand unit vector is `(−cos θ, 0, −sin θ)` (facing south, right is west).
- **Standable spot:** the block under the feet has a sturdy top face (`isFaceSturdy(level, below, Direction.UP)`), and the feet block and the one above are **air** (`isAir()`; so lava, water, portals and gateways never qualify).
- **Shore:** the nearest standable spot (by distance from the player to the spot's bottom center) whose center is within 6 blocks sideways of the player and whose Y is between the player's feet block −1 and +2.
- All maps are transient: a player's entries go on logout; everything on server stop (`BoundVexEvents.onServerStopped` calls `VexRescues.clear()`).

- [x] **Step 1: Write the failing GameTests**

Shared arrangement (a private helper `lavaPool(helper)` in the test class): `caster(helper, 0)`; `player.setPermanentlyInvulnerable(true)`. Lava spreads into any air beside it (every 30 ticks in the Overworld, every 10 in the Nether), so **no air may touch a lava block**: the player's block and the block above are lava, with stone under them and stone on all four sides of both lava blocks; the shore is **two** blocks east of the player: a stone block at the player's feet level − 1 with two air blocks above it (the shore search ignores the stone between); no other standable spot within 6 blocks (fill or roof whatever the padded box offers). A helper `step(player)` does `soak(player)` then `VexRescues.tick(player)`; every step is on its own server tick (Global Constraints).

- `bound_vex_world_lava_pull`: a following vex (`followOnly`) 5 blocks from the player. After one `step`: `VexRescues.running(player)` is `LAVA`; `player.getDeltaMovement()` is `(0.1, 0.3, 0)` within `1e-6` (east is +x); the vex is within `0.1` of the player's side point; `player.syncVelocity` was set (assert it is `true` right after the tick, before the server's next entity tick).
- `bound_vex_world_lava_pull_keeps_your_steering`: `player.setKnownMovement(new Vec3(0, 0, 0.2))` before the step → velocity `(0.1, 0.3, 0.2)`.
- `bound_vex_world_lava_pull_ends_on_the_shore`: after a step, replace the lava with air, move the player onto the shore (`player.setPos`, `player.setOnGround(true)`), `step` → no rescue running; put the lava and the player back; the pause lasts until 20 ticks after the ending tick: the next 19 steps start none, the 20th starts `LAVA` again.
- `bound_vex_world_lava_pull_pauses_after_60_ticks`: held in lava: running through the 60th step, ended by the 61st (`now − start >= 60`); then 19 steps with none and the 20th starts one again.
- `bound_vex_world_lava_pull_ignores_a_low_shore`: the only standable spot is two blocks east and 4 blocks lower → velocity `(0, 0.4, 0)` (the lift).
- `bound_vex_world_lava_pull_not_for_the_unhurt`: one test each or one test in turn: with Fire Resistance; in spectator mode; in creative mode; riding a no-AI, invulnerable pig placed in the lava (`player.startRiding(pig)`; if a mock player cannot ride, say so in the as-built note and drop that case); with only a **staying** vex in range → after a step, no rescue and the velocity is untouched.
- `bound_vex_world_lava_rescuer_keeps_its_target`: the rescuer had a target (a no-AI, invulnerable husk); after the rescue ends the vex still has that target.
- `bound_vex_world_rescue_dropped_on_logout`: a running rescue; post `new PlayerEvent.PlayerLoggedOutEvent(player)` on `NeoForge.EVENT_BUS` → none running, and the rescuer's goal lets go (after 2 ticks the vex is no longer held at the side point).
- `bound_vex_world_lava_pull_takes_a_fleeing_vex`: a full-AI vex with a hissing `patientCreeper` beside it (`setSwellDir(1)`), so its flee goal runs (assert the setup: after 12 ticks the vex has moved away), then the lava step → that vex is the rescuer and after 2 server ticks it is at the player's side.
- `bound_vex_world_lava_pull_replaces_a_dead_rescuer`: two vexes; after a step, `VexRescues.rescuer(player)` is the nearer; kill it; `step` (drops the rescue), `step` → the other vex is the rescuer.
- `bound_vex_world_lava_overworld_keeps_burning`: `player.setRemainingFireTicks(100)`; rescue ends on the shore → `player.getRemainingFireTicks() > 0` and the rescuer's main hand is its sword (no bucket).
- `bound_vex_world_lava_nether_bucket`: the same pool built in a Nether `room` (`halfWidth 4, height 5`; build the pool with `level.setBlock`, restored by the room's cleanup): the rescue ends on the shore → `player.getRemainingFireTicks() <= 0`, the rescuer holds `Items.WATER_BUCKET` and `isConjured(MAINHAND)`, `VexRescues.running(player)` is still `LAVA` (the tail), and after 20 more `VexRescues.tick` calls none runs and the hand is the sword again. A second assertion: during the tail, no second bucket sound is needed to test; instead assert the fire ticks stay cleared and that `tick` during the tail gives no push (velocity untouched).
- `bound_vex_world_lava_nether_no_bucket_while_in_lava`: held in lava in the Nether for 61 steps → the rescuer never held a bucket and the player still burns.
- `bound_vex_world_rescues_cleared_on_server_stop`, in an environment of its own (a new constant beside the existing `SERVER_STOP*` ones; read how `BoundVexJobGameTests` registers its server-stop test): a running rescue, then `BoundVexEvents.onServerStopped(new ServerStoppedEvent(server))` → `running(player)` is empty.

- [x] **Step 2: Run them to see them fail**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:bound_vex_world_lava_*"`
Expected: compilation fails (`VexRescues` does not exist).

- [x] **Step 3: `StandableSpots`**

```java
package io.github.willrieske.spellcraft.evoking;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Where a rescued player can be put or pulled to (Parts 4–5 spec §6.2, §6.4): a standable spot is a feet block with a
 * sturdy top under it and two blocks of air, so lava, water, portals and gateways never count.
 */
final class StandableSpots {
    /** How far sideways the lava pull looks for a shore. */
    static final double SHORE_RANGE = 6.0;

    private StandableSpots() {}

    /**
     * Whether a player can stand with their feet in {@code feet}.
     *
     * @param level the level
     * @param feet the feet block
     * @return true for a sturdy top below and air in the feet block and the one above
     */
    static boolean standable(Level level, BlockPos feet) {
        BlockPos below = feet.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.getBlockState(feet).isAir()
                && level.getBlockState(feet.above()).isAir();
    }

    /**
     * The nearest standable spot for a player in lava at {@code from}: within {@link #SHORE_RANGE} blocks sideways,
     * and from one block below their feet block to two above.
     *
     * @param level the level
     * @param from the player's position
     * @return the shore's feet block, or empty
     */
    static Optional<BlockPos> shore(ServerLevel level, Vec3 from) {
        BlockPos feet = BlockPos.containing(from);
        int reach = (int) Math.ceil(SHORE_RANGE);
        Iterable<BlockPos> box = BlockPos.betweenClosed(feet.offset(-reach, -1, -reach), feet.offset(reach, 2, reach));
        return StreamSupport.stream(box.spliterator(), false)
                .map(BlockPos::immutable)
                .filter(pos -> sideways(from, pos) <= SHORE_RANGE && standable(level, pos))
                .min(Comparator.comparingDouble(pos -> Vec3.atBottomCenterOf(pos).distanceToSqr(from)));
    }

    private static double sideways(Vec3 from, BlockPos pos) {
        return Math.hypot(pos.getX() + 0.5 - from.x, pos.getZ() + 0.5 - from.z);
    }
}
```

- [x] **Step 4: `VexRescues` (lava only; the other kinds come in Tasks 5 and 6)**

```java
package io.github.willrieske.spellcraft.evoking;

// imports

/**
 * Bound Vexes rescue their summoner (Parts 4–5 spec §5, §6.2): driven from the player's tick, this class finds the
 * need, picks the rescuer and applies the push itself, so a rescue never waits on a vex's goals. The rescuer's
 * {@link RescueGoal} only keeps the vex at the player's side. All state is transient.
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class VexRescues {
    /** What a rescue is for. */
    public enum Kind {
        LAVA,
        DROWNING,
        VOID
    }

    /** How far the rescuer may be when a lava or drowning rescue starts. */
    public static final double RESCUER_RANGE = 16.0;

    /** A lava pull lasts at most this long. */
    public static final int LAVA_LIMIT = 60;

    /** A lava pull ends when the player has been out of lava this long. */
    public static final int OUT_OF_LAVA = 10;

    /** After a lava pull, none starts for the same player for this long. */
    public static final int LAVA_PAUSE = 20;

    /** How long the rescuer shows what it used (the bucket, the apple) after the rescue's work is done. */
    public static final int TAIL = 20;

    private static final double SIDE = 0.6;
    private static final double SIDE_HEIGHT = 1.2;
    private static final int SPLASHES = 12;

    /** One running rescue. {@code tailUntil} is 0 until the work is done; {@code conjured} says the tail owns the hand. */
    private static final class Rescue {
        final Kind kind;
        final BoundVex rescuer;
        final long start;
        int outOfLava;
        long tailUntil;
        boolean conjured;

        Rescue(Kind kind, BoundVex rescuer, long start) {
            this.kind = kind;
            this.rescuer = rescuer;
            this.start = start;
        }
    }

    /** Where a player last stood on a standable spot. */
    record Ground(ResourceKey<Level> dimension, Vec3 position) {}

    private static final Map<UUID, Rescue> RUNNING = new HashMap<>();
    private static final Map<UUID, Ground> LAST_GROUND = new HashMap<>();
    private static final Map<UUID, Long> LAVA_PAUSED_UNTIL = new HashMap<>();

    private VexRescues() {}

    /**
     * Runs {@link #tick} after each server player's tick.
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
     * One step for {@code player}: notes where they last stood, carries a running rescue on, or starts the first
     * rescue they need. Public so GameTests can step a mock player, which gets no player tick.
     *
     * @param player the player
     */
    public static void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!player.isAlive() || player.isSpectator()) {
            drop(id);
            return;
        }
        ServerLevel level = player.level();
        if (player.onGround() && StandableSpots.standable(level, player.blockPosition())) {
            LAST_GROUND.put(id, new Ground(level.dimension(), player.position()));
        }
        Rescue rescue = RUNNING.get(id);
        if (rescue != null) {
            if (!holds(rescue.rescuer, player)) {
                drop(id);
            } else {
                carryOn(player, rescue);
            }
            return;
        }
        startFirstNeeded(player);
    }

    private static void startFirstNeeded(ServerPlayer player) {
        if (needsLavaPull(player)) {
            start(player, Kind.LAVA, RESCUER_RANGE);
        }
    }

    private static boolean needsLavaPull(ServerPlayer player) {
        ResourceKey<Level> dimension = player.level().dimension();
        long now = player.level().getGameTime();
        return (dimension.equals(Level.OVERWORLD) || dimension.equals(Level.NETHER))
                && player.isInLava()
                && !player.isCreative()
                && !player.isPassenger()
                && !player.hasEffect(MobEffects.FIRE_RESISTANCE)
                && now >= LAVA_PAUSED_UNTIL.getOrDefault(player.getUUID(), 0L);
    }

    /** Starts a rescue of {@code kind} when a rescuer is within {@code range}; the rescuer goes to the player's side. */
    private static Optional<Rescue> start(ServerPlayer player, Kind kind, double range) {
        UUID id = player.getUUID();
        Optional<BoundVex> rescuer = player.level()
                .getEntitiesOfClass(
                        BoundVex.class,
                        player.getBoundingBox().inflate(range),
                        vex -> holds(vex, player) && vex.distanceTo(player) <= range)
                .stream()
                .min(Comparator.comparingDouble(vex -> vex.distanceToSqr(player)));
        return rescuer.map(vex -> {
            Rescue rescue = new Rescue(kind, vex, player.level().getGameTime());
            RUNNING.put(id, rescue);
            placeBeside(vex, player);
            carryOn(player, rescue);
            return rescue;
        });
    }

    /** Whether {@code vex} can (still) rescue {@code player}: alive, here, following, and theirs. */
    private static boolean holds(BoundVex vex, ServerPlayer player) {
        return vex.isAlive()
                && !vex.isRemoved()
                && !vex.isStaying()
                && vex.level() == player.level()
                && vex.isSummonedBy(player.getUUID());
    }

    private static void carryOn(ServerPlayer player, Rescue rescue) {
        long now = player.level().getGameTime();
        if (rescue.tailUntil != 0) {
            if (now >= rescue.tailUntil) {
                drop(player.getUUID());
            }
            return;
        }
        switch (rescue.kind) {
            case LAVA -> lava(player, rescue, now);
            case DROWNING, VOID -> drop(player.getUUID()); // Tasks 5 and 6
        }
    }

    private static void lava(ServerPlayer player, Rescue rescue, long now) {
        if (player.isInLava()) {
            rescue.outOfLava = 0;
        } else {
            rescue.outOfLava++;
        }
        boolean out = rescue.outOfLava > 0;
        if (rescue.outOfLava >= OUT_OF_LAVA || (out && player.onGround()) || now - rescue.start >= LAVA_LIMIT) {
            endLava(player, rescue, now, out);
            return;
        }
        if (!out) {
            RescuePush.Vector v = vector(player.getKnownMovement());
            Vec3 at = player.position();
            push(
                    player,
                    StandableSpots.shore(player.level(), at)
                            .map(spot -> RescuePush.lavaPull(v, spot.getX() + 0.5 - at.x, spot.getZ() + 0.5 - at.z))
                            .orElseGet(() -> RescuePush.lavaLift(v)));
        }
    }

    private static void endLava(ServerPlayer player, Rescue rescue, long now, boolean out) {
        LAVA_PAUSED_UNTIL.put(player.getUUID(), now + LAVA_PAUSE);
        if (out && player.level().dimension().equals(Level.NETHER)) {
            douse(player);
            showInHand(rescue, new ItemStack(Items.WATER_BUCKET), now);
        } else {
            drop(player.getUUID());
        }
    }

    /** The Nether's water bucket: the sounds, the splash, the fire out. No water block is placed. */
    private static void douse(ServerPlayer player) {
        ServerLevel level = player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUCKET_EMPTY,
                SoundSource.NEUTRAL, 1.0F, 1.0F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH,
                SoundSource.NEUTRAL, 0.7F, 1.0F);
        level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY(0.5), player.getZ(), SPLASHES,
                0.3, 0.5, 0.3, 0.0);
        player.clearFire();
    }

    /** Starts the tail: the rescuer shows {@code item} for {@link #TAIL} ticks, unless its hand is already conjured. */
    private static void showInHand(Rescue rescue, ItemStack item, long now) {
        if (!rescue.rescuer.isConjured(EquipmentSlot.MAINHAND)) {
            rescue.rescuer.conjure(EquipmentSlot.MAINHAND, item);
            rescue.conjured = true;
        }
        rescue.tailUntil = now + TAIL;
    }

    private static void push(ServerPlayer player, RescuePush.Vector v) {
        player.setDeltaMovement(v.x(), v.y(), v.z());
        player.syncVelocity = true;
    }

    private static RescuePush.Vector vector(Vec3 v) {
        return new RescuePush.Vector(v.x, v.y, v.z);
    }

    /** Ends the player's rescue, whatever its state, and gives the rescuer's hand back if the tail took it. */
    private static void drop(UUID player) {
        Rescue rescue = RUNNING.remove(player);
        if (rescue != null && rescue.conjured) {
            rescue.rescuer.dismiss(EquipmentSlot.MAINHAND);
        }
    }

    /**
     * The point at a player's side where their rescuer hangs: 0.6 blocks to the right of the way they face, 1.2 above
     * their feet.
     */
    static Vec3 sideOf(ServerPlayer player) {
        double yaw = Math.toRadians(player.getYRot());
        return player.position().add(-Math.cos(yaw) * SIDE, SIDE_HEIGHT, -Math.sin(yaw) * SIDE);
    }

    /** Puts {@code vex} at the player's side, still. Vexes pass through blocks, so any point works. */
    static void placeBeside(BoundVex vex, ServerPlayer player) {
        Vec3 side = sideOf(player);
        vex.setPos(side.x, side.y, side.z);
        vex.setDeltaMovement(Vec3.ZERO);
    }

    // running(player), rescuer(player), rescuedBy(vex), clear(), onLogout: see below
}
```

Finish the class:

- `running(player)`: `Optional.ofNullable(RUNNING.get(id)).map(r -> r.kind)`; `rescuer(player)` likewise.
- `rescuedBy(vex)`: the online player whose running rescue has this vex (`RUNNING` entries whose `rescuer == vex`; resolve the player with `vex.level().getServer().getPlayerList().getPlayer(id)`, and require the same level).
- `clear()`: drops every rescue through `drop` (so hands are given back), then clears the three maps.
- `@SubscribeEvent onLogout(PlayerEvent.PlayerLoggedOutEvent)`: `drop(id)`, remove the player's other two entries.
- A dismissed tail when the rescuer died: `dismiss` on a dead or removed vex must be harmless; read `BoundVex.dismiss` and guard with `isAlive()` if it is not.
- Javadoc everything; complete the imports; `LAST_GROUND` is read in Task 6 (add a package-private `lastGround(UUID)` accessor now so Error Prone does not flag an unused field).

- [x] **Step 5: `RescueGoal` and its registration**

```java
package io.github.willrieske.spellcraft.evoking;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Keeps a rescuing Bound Vex at its summoner's side (Parts 4–5 spec §6.2) for as long as {@link VexRescues} names it
 * the rescuer, the tail included. Priority −1 on {@code MOVE} and {@code LOOK}: it replaces whatever the vex was
 * doing (a flight from a creeper, a cure, a charge), and that goal's own {@code stop} cleans up after it. The push
 * itself is not this goal's work.
 */
final class RescueGoal extends Goal {
    private final BoundVex vex;

    RescueGoal(BoundVex vex) {
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return VexRescues.rescuedBy(vex).isPresent();
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
        vex.getMoveControl().setWait();
    }

    @Override
    public void tick() {
        VexRescues.rescuedBy(vex).ifPresent(player -> {
            VexRescues.placeBeside(vex, player);
            vex.getLookControl().setLookAt(player);
        });
    }
}
```

In `BoundVex.registerGoals`, first line: `goalSelector.addGoal(-1, new RescueGoal(this));`. Check in the sources that `setWait()` exists on the vex's move control (as `FollowSummonerGoal.teleportBeside` uses it) and which `setLookAt` overload takes an entity. In `BoundVexEvents.onServerStopped` add `VexRescues.clear();` and name it in the Javadoc.

- [x] **Step 6: Run the lava tests**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:bound_vex_world_lava_*"` and `"…bound_vex_world_rescues_*"`
Expected: all pass.

- [x] **Step 7: Full gates, then commit**

Run: `./gradlew spotlessApply build`, `./gradlew runGameTestServer` twice.

```bash
git add src/main/java/io/github/willrieske/spellcraft/evoking src/main/java/io/github/willrieske/spellcraft/gametest
git commit -m "feat(evoking): a Bound Vex pulls its summoner out of lava; in the Nether it douses them"
```

> **As built (`c8613d0`):** diverged — the target-keeping test uses a vulnerable husk out of reach (an invulnerable mob cannot be targeted); lava does not ignite a mock player through `baseTick`, so the burning tests set fire ticks; a rescue starts only once the vex has ticked; the Overworld pool is cased in stone so the padded box offers no other shore; "not for the unhurt" is five tests; the tail gives the hand back only to a living rescuer. 384 tests; reason: how the GameTest server and mock players behave.

---

### Task 5: The drowning push

**Files:**

- Modify: `evoking/VexRescues.java`, `gametest/BoundVexWorldGameTests.java`

**Interfaces:**

- Consumes: `VexRescues.start/carryOn/push/drop` (Task 4), `RescuePush.drowningPush`.
- Produces: the `DROWNING` kind runs; constants `VexRescues.LOW_AIR = 60`, `DROWNING_LIMIT = 200`, `WAY_UP = 32`.

- [x] **Step 1: Write the failing GameTests**

Arrangement (`waterColumn(helper, depth)`): the player invulnerable at `FEET`; water in the player's two blocks and `depth` blocks above the head, then air; stone around the column. A step here is `soak(player)`, then `player.setAirSupply(40)` (or the test's value), then `VexRescues.tick(player)`, in that order: `baseTick` changes the air supply (down under water, up in air), so the air is set after it. One step per server tick. Water also spreads into air beside it: keep stone on all four sides of the column.

- `bound_vex_world_drowning_push`: a following vex within 16 blocks; `setKnownMovement((0.1, -0.2, 0.05))`; step → `running` is `DROWNING`, velocity `(0.1, 0.4, 0.05)`, the vex at the player's side.
- `bound_vex_world_drowning_not_under_a_ceiling`: a stone block instead of the air above the column → no rescue, velocity untouched.
- `bound_vex_world_drowning_not_with_air_left`: air supply 100 → none.
- `bound_vex_world_drowning_not_with_water_breathing`: `MobEffects.WATER_BREATHING` → none; a second player or second half with `MobEffects.CONDUIT_POWER` → none; a creative player → none.
- `bound_vex_world_drowning_ends_at_the_surface`: after a step, move the player up so their eyes are in air (`setPos`), step → none running.
- `bound_vex_world_drowning_ends_when_the_way_up_closes`: after a step, put stone over the column, step → none running.
- `bound_vex_world_drowning_ends_after_200_ticks`: 200 steps running (reset the air to 40 each step), not running after the next; and it starts again on the step after (no pause for drowning).
- `bound_vex_world_drowning_only_in_the_overworld`: the same column in a Nether `room` (water placed with `setBlock`; if the Nether refuses water blocks through `setBlock`, drop this test and say so in the as-built note: the rule is then tested by the `needsDrowningPush` dimension check alone) → none.

- [x] **Step 2: Run them to see them fail**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:bound_vex_world_drowning_*"`
Expected: the tests fail (no rescue starts).

- [x] **Step 3: Implement**

In `startFirstNeeded`, after the lava branch (`else if`):

```java
} else if (needsDrowningPush(player)) {
    start(player, Kind.DROWNING, RESCUER_RANGE);
}
```

```java
private static boolean needsDrowningPush(ServerPlayer player) {
    return player.level().dimension().equals(Level.OVERWORLD)
            && player.isUnderWater()
            && player.getAirSupply() < LOW_AIR
            && !player.isCreative()
            && !player.isPassenger()
            && !player.hasEffect(MobEffects.WATER_BREATHING)
            && !player.hasEffect(MobEffects.CONDUIT_POWER)
            && hasWayUp(player);
}

/**
 * Whether open water leads from the player's head straight up to air within {@link #WAY_UP} blocks: every block on
 * the way is water with no collision. Under a ceiling there is no push (it would pin the player to the roof).
 */
private static boolean hasWayUp(ServerPlayer player) {
    ServerLevel level = player.level();
    BlockPos.MutableBlockPos pos = BlockPos.containing(player.getEyePosition()).mutable();
    for (int step = 0; step <= WAY_UP; step++) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        if (!state.getFluidState().is(FluidTags.WATER) || !state.getCollisionShape(level, pos).isEmpty()) {
            return false;
        }
        pos.move(Direction.UP);
    }
    return false;
}

private static void drowning(ServerPlayer player, Rescue rescue, long now) {
    if (!player.isUnderWater() || !hasWayUp(player) || now - rescue.start >= DROWNING_LIMIT) {
        drop(player.getUUID());
        return;
    }
    push(player, RescuePush.drowningPush(vector(player.getKnownMovement())));
}
```

Wire `case DROWNING -> drowning(player, rescue, now);` in `carryOn`. The start tick pushes too (`start` calls `carryOn`), which is what the first test asserts.

- [x] **Step 4: Run the drowning tests, full gates, commit**

Run: the filtered run, then `./gradlew spotlessApply build`, `./gradlew runGameTestServer` twice.

```bash
git add src/main/java/io/github/willrieske/spellcraft/evoking src/main/java/io/github/willrieske/spellcraft/gametest
git commit -m "feat(evoking): a Bound Vex pushes its drowning summoner up through open water"
```

> **As built (`7ba6f2a`):** diverged — the pushes apply the fluid drag to the reported movement (lava 0.5, water 0.8) and the lava pull has a hard 0.35 cap, so `drowning_push` expects `(0.08, 0.4, 0.04)`; a rescue also ends when the player stops needing it; review fixes for Task 4 are in `7730532`. 408 tests; reason: coordinator ruling after the Task 4 review (sending the undragged movement back each tick would let a steering player speed up without limit), recorded in spec §5, §6.2 and §9.

---

### Task 6: The void catch

**Files:**

- Modify: `evoking/VexRescues.java`, `evoking/StandableSpots.java`, `gametest/BoundVexWorldGameTests.java`

**Interfaces:**

- Consumes: `VoidRule.needsCatch` (Task 2), `VexRescues.lastGround`, `showInHand`, `endVoid`, `room`, `vexIn`.
- Produces: the `VOID` kind; `static Optional<BlockPos> StandableSpots.landing(ServerLevel level, Vec3 from)`; `VexRescues.VOID_RESCUER_RANGE = 64.0`.

**Design:**

- **Need** (End only; creative players included; spectators never reach here): `VoidRule.needsCatch(player.getY(), level.getMinY(), player.onGround(), player.getKnownMovement().y < 0, player.isFallFlying() || player.getAbilities().flying, () -> a 64-block downward clip from the feet hits a block)`.
- **Order:** the void is tested first in `startFirstNeeded`.
- **Landing:** `StandableSpots.landing`: columns within 32 blocks sideways of the player's column, nearest first; for each, `y = level.getHeight(MOTION_BLOCKING_NO_LEAVES, x, z)`; skip when `y <= level.getMinY()` (empty or unloaded); take the first whose `new BlockPos(x, y, z)` is `standable`. The offsets are sorted once (a static list of the `(dx, dz)` pairs with `dx² + dz² ≤ 32²`, ordered by that sum). Else the player's last ground position, if its dimension is the End **and it is still standable** (a broken bridge is not a landing; this adds to the spec's sentence, which the as-built note records). With neither: no rescue starts.
- **No landing, no search for a second:** when a catch is needed and there is a rescuer but neither a landing nor a usable last ground, remember it for that player for 20 ticks and skip the 3,217-column search until then (a transient map like the lava pause; cleared with the others).
- **Action** (all in the starting tick, in this order): `player.stopRiding()`; `player.setDeltaMovement(Vec3.ZERO)` and `resetFallDistance()` **before** the teleport (the teleport packet carries the velocity); teleport with `player.teleportTo(level, x + 0.5, y, z + 0.5, Set.of(), player.getYRot(), player.getXRot(), false)` (the form `MockPlayers.moveTo` uses; check the parameters in the sources); `syncVelocity = true`; the rescuer to the player's side; the apple: a throwaway `new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)`, its `DataComponents.CONSUMABLE` component's `onConsume(level, player, stack)`; then `showInHand(rescue, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), now)`. No cooldown.
- The catch needs a rescuer first (within 64 blocks): find the landing only when one exists.

- [x] **Step 1: Write the failing GameTests**

Every void test uses `endVoid` (a `room` has a roof, which the height map would see) and builds its ground with `platform`: open stone, nothing above. The End's lowest Y is 0; a slot is at `y = 80`, so "below the floor" means the test moves the player to `y = end.getMinY() − 5` by `setPos`. `reach` covers everything the test places.

- `bound_vex_world_void_catch`: `endVoid(helper, player, 48)`; a 3×3 `platform` 10 blocks east of the player's column at `y = 60`; a vex beside the player; move the player to `y = minY − 5` (same column); `VexRescues.tick(player)` → the player stands on the platform (`StandableSpots.standable` at their block, the block below is stone, and it is the platform's nearest column to where they fell), `player.fallDistance == 0`, velocity zero, has Regeneration, Resistance, Fire Resistance and Absorption; `running` is `VOID`; the vex is at their side holding the apple (conjured); after 20 more ticks none runs and the vex holds its sword.
- `bound_vex_world_void_catch_falling_near_the_floor`: no platform within 32 blocks but a recorded last ground (as in the next test); the player at `minY + 10`, `setOnGround(false)`, `setKnownMovement((0, -0.5, 0))` → caught. The same with a single stone block 5 blocks under them (`platform` with half-width 0) → not caught (ground below). The same without the downward movement → not caught.
- `bound_vex_world_void_catch_uses_last_ground`: a 1-block platform 40 blocks east (outside the 32-block search); the player stands on it (`setPos`, `setOnGround(true)`, one `VexRescues.tick` to record it), then is moved to the slot's column below the floor → teleported onto the platform. A second player who never stood anywhere: no rescue, they stay where they are.
- `bound_vex_world_void_catch_last_ground_gone`: as above, then the platform block is removed before the fall → no rescue.
- `bound_vex_world_void_catch_rescuer_range`: `reach` 80; a vex 40 blocks away catches; in a second half (or test) a vex 70 blocks away does not.
- `bound_vex_world_void_catch_dismounts`: the player rides a no-AI, no-gravity, invulnerable pig below the floor → `!player.isPassenger()` and caught (drop the case with a note if a mock player cannot ride).
- `bound_vex_world_void_catch_creative`: a creative player (`player.setGameMode(GameType.CREATIVE)`, abilities not flying) → caught; with `player.getAbilities().flying = true` → not; a survival player with `isFallFlying()` true (through whatever public call sets it; if none works on a mock player, the rule is covered by `VoidRuleTest` alone: say so in the as-built note) → not.
- `bound_vex_world_void_catch_skips_a_portal_column`: a platform whose nearest column to the fallen player is bedrock with an `END_PORTAL` block on top (placed with `setBlock`; it has no collision, so the height map reports the portal's own Y as free and the feet block is not air) → the player lands on another column of the platform, never in the portal block.
- `bound_vex_world_void_no_landing_is_not_searched_every_tick`: no platform, no last ground, a rescuer present: after a tick with no catch, a platform is built within 32 blocks; ticks within the next 19 do not catch; the tick after the 20-tick memo does.
- `bound_vex_world_void_not_in_the_overworld`: in the test level, a player moved to `y = minY − 5` with a vex → no rescue. (Move them back before the test ends.)

- [x] **Step 2: Run them to see them fail**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:bound_vex_world_void_*"`
Expected: the tests fail (no catch).

- [x] **Step 3: Implement**

`StandableSpots`:

```java
/** How far sideways the void catch looks for a landing. */
static final int LANDING_RANGE = 32;

/** Every column offset within {@link #LANDING_RANGE} blocks, nearest first. */
private static final List<int[]> COLUMNS = columns();

private static List<int[]> columns() {
    List<int[]> offsets = new ArrayList<>();
    for (int dx = -LANDING_RANGE; dx <= LANDING_RANGE; dx++) {
        for (int dz = -LANDING_RANGE; dz <= LANDING_RANGE; dz++) {
            if (dx * dx + dz * dz <= LANDING_RANGE * LANDING_RANGE) {
                offsets.add(new int[] {dx, dz});
            }
        }
    }
    offsets.sort(Comparator.comparingInt(offset -> offset[0] * offset[0] + offset[1] * offset[1]));
    return List.copyOf(offsets);
}

/**
 * The nearest column to {@code from} within {@link #LANDING_RANGE} blocks whose top is a standable spot. A column
 * with no blocks, or in an unloaded chunk, reports the level's lowest Y and is skipped.
 *
 * @param level the level
 * @param from the falling player's position
 * @return the landing's feet block, or empty
 */
static Optional<BlockPos> landing(ServerLevel level, Vec3 from) {
    BlockPos column = BlockPos.containing(from);
    for (int[] offset : COLUMNS) {
        int x = column.getX() + offset[0];
        int z = column.getZ() + offset[1];
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y > level.getMinY()) {
            BlockPos feet = new BlockPos(x, y, z);
            if (standable(level, feet)) {
                return Optional.of(feet);
            }
        }
    }
    return Optional.empty();
}
```

`VexRescues` (the void branch goes first in `startFirstNeeded`):

```java
private static void startFirstNeeded(ServerPlayer player) {
    if (needsVoidCatch(player)) {
        catchFromVoid(player);
    } else if (needsLavaPull(player)) {
        start(player, Kind.LAVA, RESCUER_RANGE);
    } else if (needsDrowningPush(player)) {
        start(player, Kind.DROWNING, RESCUER_RANGE);
    }
}

private static boolean needsVoidCatch(ServerPlayer player) {
    ServerLevel level = player.level();
    return level.dimension().equals(Level.END)
            && VoidRule.needsCatch(
                    player.getY(),
                    level.getMinY(),
                    player.onGround(),
                    player.getKnownMovement().y < 0,
                    player.isFallFlying() || player.getAbilities().flying,
                    () -> groundBelow(player));
}

private static boolean groundBelow(ServerPlayer player) {
    Vec3 feet = player.position();
    return player.level()
                    .clip(new ClipContext(
                            feet, feet.subtract(0, GROUND_CHECK, 0), ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, player))
                    .getType()
            != HitResult.Type.MISS;
}
```

`GROUND_CHECK` is a new constant, `64.0` ("how far down a falling player is checked for ground"). `StandableSpots` needs the imports `Heightmap`, `ArrayList`, `List`.

`catchFromVoid`: find the rescuer (refactor `start` so the rescuer search is its own method `nearestRescuer(player, range)`, and `start` takes the found vex); none → return. Landing: `StandableSpots.landing(level, player.position()).map(Vec3::atBottomCenterOf)`, else the last ground (`dimension == Level.END` and `standable(level, BlockPos.containing(position))`); neither → return. Then the action in the order of the Design list, register the rescue (`RUNNING.put`), and `showInHand`. `case VOID` in `carryOn` is never reached before the tail is set; keep the `drop` there with a comment.

- [x] **Step 4: Run the void tests, full gates, commit**

Run: the filtered run, then `./gradlew spotlessApply build`, `./gradlew runGameTestServer` twice.

```bash
git add src/main/java/io/github/willrieske/spellcraft/evoking src/main/java/io/github/willrieske/spellcraft/gametest
git commit -m "feat(evoking): a Bound Vex catches its summoner over the End's void and feeds them the apple"
```

> **As built (`5f3fab6`):** diverged — test vexes are `NoAI` (a following vex teleports to the player before the test drops them); the 20-tick memo guards only the landing search, the last ground is still tried; the glide, ground-below and not-falling cases are separate tests; `teleportTo` already dismounts and zeroes the velocity, so those two lines cannot be seen red. 422 tests; reason: how the GameTest server and vanilla teleport behave.

---

### Task 7: The fireball return

**Files:**

- Create: `evoking/VexFireballs.java`
- Modify: `gametest/BoundVexWorldGameTests.java`

**Interfaces:**

- Consumes: `RescuePush.returned`, `GuardArea.REACH` (7.25), `room`, `vexIn`.
- Produces: `VexFireballs.onImpact(ProjectileImpactEvent)` (a subscriber); constants `NEAR_PLAYER = 4.0`.

- [ ] **Step 1: Write the failing GameTests**

All in a Nether `room(helper, player, Level.NETHER, 12, 8)` unless said. Fireballs are created and **not** added to the level; the test posts the impact through `EventHooks.onProjectileImpact(fireball, hit)` (true = cancelled). A ghast: `EntityTypes.GHAST.create(nether, EntitySpawnReason.COMMAND)`, no AI, no gravity, invulnerable, added 9 blocks north of the player at their eye height (inside the room, in sight). A fireball of that ghast: `new LargeFireball(nether, ghast, new Vec3(0, 0, 1), 1)` positioned 1.5 blocks north of the player with `setDeltaMovement(0, 0, 1.5)`; without a ghast: `EntityTypes.FIREBALL.create(nether, EntitySpawnReason.COMMAND)`.

- `bound_vex_world_fireball_returned_to_the_ghast`: a vex 3 blocks from the fireball; impact on the player (`new EntityHitResult(player)`) → cancelled; `fireball.getOwner() == player`; speed `1.5` within `1e-6`; the velocity's direction is toward the ghast's eyes from the fireball (dot product of the unit vectors > 0.999); the vex is at the fireball's position (within 0.1).
- `bound_vex_world_fireball_reversed_without_a_ghast`: an ownerless fireball flying `(0.3, 0, 1.2)` → velocity `(−0.3, 0, −1.2)`; owner the player.
- `bound_vex_world_fireball_reversed_when_the_ghast_is_hidden`: a stone wall between the vex's new position and the ghast → reversed.
- `bound_vex_world_fireball_block_hit_near_you`: a `BlockHitResult` on the floor 3 blocks from the player → cancelled; 6 blocks away → not cancelled, owner and velocity untouched.
- `bound_vex_world_fireball_left_alone`: each not cancelled: the player owns the fireball (`setOwner(player)`); no vex within 7.25 blocks of the fireball (one 9 blocks away); the player is in spectator mode; the same arrangement in the Overworld test level (no room).
- `bound_vex_world_fireball_nearest_vex_acts`: two vexes at 2 and 5 blocks → the nearer one is at the fireball's position, the other has not moved.
- `bound_vex_world_fireball_staying_vex_acts`: a staying vex within 7.25 → cancelled; `thenWaitUntil` it is back within 1 block of its post point (it flies back afterwards).
- `bound_vex_world_fireball_kills_the_ghast`: a vulnerable ghast; after the return, call the fireball's hit on the ghast as vanilla would (add the fireball to the level and let it fly, `thenWaitUntil(() -> !ghast.isAlive())`) → the ghast dies from the one hit (vanilla's rule needs the player as owner; this is the reason the owner is the summoner).

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:bound_vex_world_fireball_*"`
Expected: fail (nothing cancels the impact).

- [ ] **Step 3: Implement**

```java
package io.github.willrieske.spellcraft.evoking;

// imports

/**
 * A Bound Vex bats a ghast's fireball back (Parts 4–5 spec §6.3). The return hangs on the impact itself: a fireball
 * covers 1.2 to 1.9 blocks a tick, so a look once per tick would miss most of them. Nether only.
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class VexFireballs {
    /** An impact this near a player (or on them) is theirs to have returned. */
    public static final double NEAR_PLAYER = 4.0;

    private VexFireballs() {}

    /**
     * Cancels the impact of a large fireball that is about to hit a player, or near one, when a Bound Vex of that
     * player is within reach of it, and sends it back.
     *
     * @param event the impact, before vanilla explodes the fireball
     */
    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof LargeFireball fireball)
                || !(fireball.level() instanceof ServerLevel level)
                || !level.dimension().equals(Level.NETHER)) {
            return;
        }
        HitResult hit = event.getRayTraceResult();
        Entity struck = hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || player.equals(fireball.getOwner())) {
                continue;
            }
            if (!player.equals(struck) && hit.getLocation().distanceTo(player.position()) > NEAR_PLAYER) {
                continue;
            }
            Optional<BoundVex> batter = level
                    .getEntitiesOfClass(
                            BoundVex.class,
                            fireball.getBoundingBox().inflate(GuardArea.REACH),
                            vex -> vex.isAlive()
                                    && vex.isSummonedBy(player.getUUID())
                                    && vex.distanceTo(fireball) <= GuardArea.REACH)
                    .stream()
                    .min(Comparator.comparingDouble(vex -> vex.distanceToSqr(fireball)));
            if (batter.isPresent()) {
                sendBack(level, fireball, batter.get(), player);
                event.setCanceled(true);
                return;
            }
        }
    }

    /**
     * The return: the vex jumps to the fireball, the knockback sound plays, and the fireball flies at its old speed
     * toward the ghast that shot it (alive and in the vex's sight), or straight back. The summoner becomes its owner:
     * vanilla kills a ghast in one hit only with a fireball a player returned.
     */
    private static void sendBack(ServerLevel level, LargeFireball fireball, BoundVex vex, ServerPlayer player) {
        Vec3 at = fireball.position();
        vex.setPos(at.x, at.y, at.z);
        vex.setDeltaMovement(Vec3.ZERO);
        vex.getMoveControl().setWait();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, vex.getSoundSource(), 1.0F, 1.0F);
        Vec3 old = fireball.getDeltaMovement();
        Vec3 heading = fireball.getOwner() instanceof Ghast ghast && ghast.isAlive() && vex.hasLineOfSight(ghast)
                ? ghast.getEyePosition().subtract(at)
                : Vec3.ZERO;
        RescuePush.Vector back =
                RescuePush.returned(new RescuePush.Vector(old.x, old.y, old.z), heading.x, heading.y, heading.z);
        fireball.deflect(
                (projectile, deflector, random, power) -> {
                    projectile.setDeltaMovement(back.x(), back.y(), back.z());
                    projectile.needsSync = true;
                },
                vex,
                EntityReference.of(player),
                true,
                1.0);
    }
}
```

Check in the sources: the `deflect` overload and `EntityReference.of`; that `needsSync` is public (if not, use the public call vanilla's `ProjectileDeflection.REVERSE` uses); `ServerLevel.players()`; the import path of `LargeFireball` and `Ghast`. "Following or staying" is why there is no `isStaying` test. A vex whose summoner is that player is always "present" here, since the loop walks the level's living players.

- [ ] **Step 4: Run the fireball tests, full gates, commit**

Run: the filtered run, then `./gradlew spotlessApply build`, `./gradlew runGameTestServer` twice.

```bash
git add src/main/java/io/github/willrieske/spellcraft/evoking src/main/java/io/github/willrieske/spellcraft/gametest
git commit -m "feat(evoking): a Bound Vex returns a ghast's fireball in the Nether"
```

---

### Task 8: End crystals and their blast

**Files:**

- Create: `evoking/AttackCrystalGoal.java`
- Modify: `evoking/BoundVex.java` (`registerGoals`), `evoking/BoundVexEvents.java`
- Modify: `gametest/BoundVexWorldGameTests.java`

**Interfaces:**

- Consumes: the claim-map pattern of `RepairGolemGoal` (read it first), `vex.nearby`, `vex.guardArea/inGuardArea`, `vex.scanDue(vex.scanInterval())`, `endVoid`, `vexIn`.
- Produces: `public static boolean AttackCrystalGoal.livingDragon(ServerLevel level)`; test hooks `public static void AttackCrystalGoal.scriptDragon(BoundVex vex, boolean alive)` and `unscriptDragon(BoundVex vex)` (per vex, like `BribeGoal.scriptDice`); `static void AttackCrystalGoal.clearClaims()`; `BoundVexEvents.onExplosionKnockback(ExplosionKnockbackEvent)`.

**Design:**

- The goal: priority 2, flag `MOVE`, `requiresUpdateEveryTick`. `canUse` (on `vex.scanDue(vex.scanInterval())`): the level is the End; the dragon lives (the scripted answer for this vex if one is set, else `livingDragon(level)`); the summoner is present; no target; the nearest `EndCrystal` that is alive, `!isInvulnerable()`, not claimed by another living vex, and within **32 blocks** of a following vex (`vex.nearby(EndCrystal.class, 32, …)`) or inside the stay area of a staying one (`getEntitiesOfClass(EndCrystal.class, vex.guardArea(), vex::inGuardArea)`).
- `tick`: fly at speed 1.0 toward the crystal's position plus 1 block up; within **2 blocks** of that point: `crystal.hurtServer(level, vex.damageSources().mobAttack(vex), 1.0F)` once (guard with a `finished` flag: `tick` can run once after the end condition, api-notes §21).
- `canContinueToUse`: not finished, the crystal alive, no target, the summoner present, fewer than **200** ticks since the start.
- `stop`: release this vex's claim. When the goal ended on the 200-tick limit, this vex claims no crystal for `Jobs.RETRY_DELAY` (200) ticks (a `blockedUntil` game time checked in `canUse`, as the cure and bribe goals do): vanilla asks `canUse` in the same pass that stopped the goal, so without the delay the vex would claim the same crystal again at once and no other vex could ever take it.
- `livingDragon(level)`: `level.getDragonFight()` not null, its `dragonUUID()` not null, `level.getEntity(uuid)` a living entity.
- **Blast damage** (`BoundVexEvents.onIncomingDamage`, first lines, before the attacker test): a `BoundVex` victim whose damage source's direct entity is an `EndCrystal` → cancelled. The acting vex, the summoner and allies are already covered by the backstop (the crystal's damage source names the vex as the causing entity).
- **Knockback** (`onExplosionKnockback`): zero (`Vec3.ZERO`) when (a) the explosion's direct source entity is an `EndCrystal` and the affected entity is a `BoundVex`; or (b) the explosion's damage source's causing entity (`getDamageSource().getEntity()`) is a `BoundVex` with its summoner present, and the affected entity is a `LivingEntity` the backstop spares. Extract the backstop's owner-present test into `private static boolean spares(BoundVex vex, ServerPlayer owner, LivingEntity victim)` (`victim` is the owner, or `!vex.isPrey(victim)`) and use it in both handlers.

- [ ] **Step 1: Write the failing GameTests**

All in the End through `endVoid(helper, player, 48)` (a mock player does not fall; vexes and crystals float; the slot is private, so no other test's vex can reach these crystals). A crystal: `EntityTypes.END_CRYSTAL.create(end, EntitySpawnReason.COMMAND)`, `setPos`, `end.addFreshEntity`. Each test scripts the dragon for its own vexes (`scriptDragon(vex, true)`, undone in `whenDone`).

- `bound_vex_world_crystal_broken`: a crystal 20 blocks from a following vex → `thenWaitUntil(() -> crystal.isRemoved())`; then the vex is alive at full health.
- `bound_vex_world_crystal_spares_your_side`: the player 2 blocks from a crystal (survival, vulnerable, full health), a no-AI wolf tamed by the player 2 blocks from it, a second vex of the player beside it; a vex breaks it → the player's health is unchanged **and `player.getDeltaMovement()` is zero**; the wolf and both vexes are unhurt and the wolf's velocity is zero.
- `bound_vex_world_crystal_invulnerable_left_alone`: `crystal.setInvulnerable(true)` → after 60 ticks it exists and the vex is within 6 blocks of the player (it never left).
- `bound_vex_world_crystal_removed_on_the_way`: two crystals, A 30 blocks east and B 30 blocks west; the vex heads for the nearer (make A nearer by a block); A is discarded after 10 ticks → the vex's goal ended and started again for B: `thenWaitUntil(() -> crystalB.isRemoved())`.
- `bound_vex_world_crystal_needs_a_dragon`: `scriptDragon(vex, false)` → after 60 ticks the crystal exists. And unscripted: `AttackCrystalGoal.livingDragon(helper.getLevel())` is false (the Overworld has no fight).
- `bound_vex_world_crystal_one_each`: two vexes, two crystals 6 blocks apart and 15 away → both removed, and at the tick the first is removed the two vexes were heading for different crystals (assert through the claims: expose a package-private `claimedBy(EndCrystal)` only if nothing public can show it; otherwise assert both are gone within 100 ticks and both vexes are alive).
- `bound_vex_world_crystal_not_while_fighting`: the vex has a target (a no-AI husk 3 blocks away, held alive with `setPermanentlyInvulnerable(true)`) → the crystal 10 blocks away exists after 60 ticks.
- `bound_vex_world_crystal_staying_vex_only_in_its_area`: a staying vex; a crystal 5 blocks from its post is broken; one 12 blocks away is not (after 100 ticks).
- `bound_vex_world_crystal_gives_up_after_200_ticks`: the vex is held away from the crystal (`holdAt`) → after its 200 ticks its claim is free and stays free for the retry delay: a second vex of the same player, added at tick 205, breaks the crystal (register this test with a tick budget that covers it).
- `bound_vex_world_crystal_blast_by_a_player`: no scripted dragon; the test calls `crystal.hurtServer(end, end.damageSources().playerAttack(player), 1)` with a vex 2 blocks away and the player 2 blocks away (invulnerable off, health noted) → the vex is unhurt and its velocity zero; the **player's velocity is not zero** (vanilla throws them) and their health dropped.
- `bound_vex_world_crystal_blast_spares_another_players_vex`: a second mock player's vex 2 blocks from a crystal broken by the first player's vex → unhurt, velocity zero.
- `bound_vex_world_crystal_claims_cleared_on_server_stop`: mirror the repair goal's server-stop test, in an environment of its own (the stop handler clears every test's state).

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:bound_vex_world_crystal_*"`
Expected: compilation fails (`AttackCrystalGoal` does not exist).

- [ ] **Step 3: Implement `AttackCrystalGoal`**

Write it on `RepairGolemGoal`'s frame, with the shared claim map Part 4's branch review added (`Jobs.Claims`, no age limit; read `evoking/Jobs.java` for its exact methods: claim in `start`, prune at the top of `canUse`, "held by another" in the candidate filter, release in `stop`, `clearClaims`) and `Jobs.SPEED`. The snippet below writes those calls as `CLAIMED.values().removeIf(…)` and `claimedByAnother(…)`; use the `Jobs.Claims` equivalents. The parts that differ from the repair goal:

```java
/** How far a following vex looks for a crystal. */
static final double RANGE = 32.0;

/** How near the point above the crystal the vex strikes from. */
static final double STRIKE = 2.0;

/** A vex gives a crystal up after this long. */
static final int LIMIT = 200;

/** Scripted answers to "does the dragon live", per vex, for GameTests; empty in play. */
private static final Map<UUID, Boolean> SCRIPTED_DRAGON = new HashMap<>();

/**
 * Whether the level's dragon fight has a living dragon: crystals are fought only then, so the ones a player places
 * to bring the dragon back are left alone.
 *
 * @param level the level
 * @return true in the End while its dragon lives
 */
public static boolean livingDragon(ServerLevel level) {
    EnderDragonFight fight = level.getDragonFight();
    if (fight == null) {
        return false;
    }
    UUID dragon = fight.dragonUUID();
    return dragon != null && level.getEntity(dragon) instanceof LivingEntity living && living.isAlive();
}

@Override
public boolean canUse() {
    CLAIMED.values().removeIf(holder -> holder.isRemoved() || !holder.isAlive());
    if (!vex.scanDue(vex.scanInterval())
            || vex.getTarget() != null
            || !(vex.level() instanceof ServerLevel level)
            || !level.dimension().equals(Level.END)
            || vex.summoner().isEmpty()
            || !SCRIPTED_DRAGON.getOrDefault(vex.getUUID(), livingDragon(level))) {
        return false;
    }
    Predicate<EndCrystal> free =
            candidate -> candidate.isAlive() && !candidate.isInvulnerable() && !claimedByAnother(candidate);
    List<EndCrystal> crystals = vex.isStaying()
            ? level.getEntitiesOfClass(
                    EndCrystal.class, vex.guardArea(), candidate -> vex.inGuardArea(candidate) && free.test(candidate))
            : vex.nearby(EndCrystal.class, RANGE, free);
    crystal = crystals.stream().min(Comparator.comparingDouble(vex::distanceToSqr)).orElse(null);
    return crystal != null;
}

@Override
public void tick() {
    if (crystal == null || finished || !(vex.level() instanceof ServerLevel level)) {
        return;
    }
    ticks++;
    Vec3 above = crystal.position().add(0.0, 1.0, 0.0);
    if (vex.position().distanceTo(above) > STRIKE) {
        vex.getMoveControl().setWantedPosition(above.x, above.y, above.z, SPEED);
        return;
    }
    finished = true;
    crystal.hurtServer(level, vex.damageSources().mobAttack(vex), 1.0F);
}
```

`getOrDefault` evaluates `livingDragon` every scan; that is fine (two lookups). Register in `BoundVex.registerGoals` after the bribe goal: `goalSelector.addGoal(2, new AttackCrystalGoal(this));`. `BoundVexEvents.onServerStopped` calls `AttackCrystalGoal.clearClaims()` (which also clears the scripted answers).

- [ ] **Step 4: The blast in `BoundVexEvents`**

At the top of `onIncomingDamage`:

```java
if (event.getEntity() instanceof BoundVex && event.getSource().getDirectEntity() instanceof EndCrystal) {
    event.setCanceled(true); // a crystal's blast never hurts a Bound Vex, whoever set it off (Parts 4–5 spec §7.1)
    return;
}
```

New handler:

```java
/**
 * Stops a blast from throwing those it may not hurt (Parts 4–5 spec §7.1): vanilla pushes everything in range even
 * when the damage was cancelled. A crystal's blast never moves a Bound Vex; a blast a Bound Vex caused never moves
 * its summoner or anyone the friendly-fire backstop spares.
 *
 * @param event the knockback for one entity, after the damage and before the push
 */
@SubscribeEvent
public static void onExplosionKnockback(ExplosionKnockbackEvent event) {
    ServerExplosion explosion = event.getExplosion();
    Entity affected = event.getAffectedEntity();
    if (explosion.getDirectSourceEntity() instanceof EndCrystal && affected instanceof BoundVex) {
        event.setKnockbackVelocity(Vec3.ZERO);
        return;
    }
    if (explosion.getDamageSource().getEntity() instanceof BoundVex vex && affected instanceof LivingEntity victim) {
        vex.summoner().filter(owner -> spares(vex, owner, victim)).ifPresent(owner -> event.setKnockbackVelocity(Vec3.ZERO));
    }
}
```

Rewrite the owner-present branch of `onIncomingDamage` to call `spares`, and extend the method's Javadoc with the crystal rule. New imports: `ExplosionKnockbackEvent` (`net.neoforged.neoforge.event.level`), `ServerExplosion` (`net.minecraft.world.level`), `EndCrystal` (`net.minecraft.world.entity.boss.enderdragon`); wrap the long `ifPresent` line.

- [ ] **Step 5: Run the crystal tests, full gates, commit**

Run: the filtered run, then `./gradlew spotlessApply build`, `./gradlew runGameTestServer` twice.

```bash
git add src/main/java/io/github/willrieske/spellcraft/evoking src/main/java/io/github/willrieske/spellcraft/gametest
git commit -m "feat(evoking): Bound Vexes break end crystals; the blast spares and does not throw your side"
```

---

### Task 9: Critical hits in the End

**Files:**

- Modify: `evoking/BoundVexEvents.java`, `gametest/BoundVexWorldGameTests.java`

**Interfaces:**

- Consumes: `onIncomingDamage` (Task 8's shape), `room`, `vexIn`.
- Produces: `BoundVexEvents.END_CRIT = 3.0F`.

- [ ] **Step 1: Write the failing GameTests**

- `bound_vex_world_crit_in_the_end`: measure, do not assume the sword's damage: in the Overworld test level a parked vex hits a no-AI husk once (`vex.doHurtTarget(level, husk)`; the husk's armor is removed by the test so the numbers are plain: `husk.getAttribute(Attributes.ARMOR).setBaseValue(0)`), note the health lost `d`. In an End `room`, the same with a `vexIn` vex and a husk created in the End → the health lost is `3 · d` within `0.01` (give both husks 100 max health first so neither dies).
- `bound_vex_world_crit_not_for_arrows`: in the End, damage the husk with `end.damageSources().arrow(arrow, vex)` (an `Arrow` entity owned by the vex, created and not added) by `husk.hurtServer(end, source, 4)` → loses 4, not 12.
- `bound_vex_world_crit_not_when_spared`: in the End the vex hits the summoner's tamed wolf (`vex.doHurtTarget`) → the wolf's health is unchanged.
- `bound_vex_world_crit_keeps_golem_mercy`: in the End, a mercy-protected golem (arrange it as `GolemGameTests` does for the clamp test: read that class) hit by the vex's melee → its health stops where the clamp test expects (25).
- `bound_vex_world_crit_on_a_player_under_the_pvp_rule`: in the End, a second mock player who is prey for the vex (arrange the PvP rule as the existing target tests do for "a player the summoner may harm": read `BoundVexTargetGameTests`) is hit by the vex's melee → loses three times what the same hit costs that player in the Overworld (measure both; same armor, none).
- `bound_vex_world_no_crit_in_the_nether`: in a Nether room the hit does `d`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew runGameTestServer "-PgameTests=spellcraft:bound_vex_world_crit_*"` and `"…bound_vex_world_no_crit_*"`
Expected: the first test fails (`d`, not `3 · d`).

- [ ] **Step 3: Implement**

At the end of `onIncomingDamage` (after the backstop's branches; restructure the early `return` for a non-vex attacker so the flow stays readable):

```java
if (!event.isCanceled()
        && vex.equals(event.getSource().getDirectEntity())
        && vex.level().dimension().equals(Level.END)) {
    crit(event, victim);
}
```

```java
/** In the End every melee hit by a Bound Vex does this many times its damage (Parts 4–5 spec §7.2). */
public static final float END_CRIT = 3.0F;

private static final int CRIT_PARTICLES = 6;

/** Triples the hit and shows it: vanilla's critical-hit particles and sound on the victim. */
private static void crit(LivingIncomingDamageEvent event, LivingEntity victim) {
    event.setAmount(event.getAmount() * END_CRIT);
    if (victim.level() instanceof ServerLevel level) {
        level.sendParticles(
                ParticleTypes.CRIT, victim.getX(), victim.getY(0.5), victim.getZ(), CRIT_PARTICLES, 0.3, 0.3, 0.3, 0.1);
        level.playSound(
                null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_CRIT,
                SoundSource.NEUTRAL, 1.0F, 1.0F);
    }
}
```

The direct entity being the vex itself is what excludes arrows and potions. Extend the method's Javadoc: the crit comes after the backstop and before the golem mercy clamp (`LivingDamageEvent.Pre`, later in the pipeline).

- [ ] **Step 4: Run the crit tests, full gates, commit**

Run: the filtered runs, then `./gradlew spotlessApply build`, `./gradlew runGameTestServer` twice.

```bash
git add src/main/java/io/github/willrieske/spellcraft/evoking src/main/java/io/github/willrieske/spellcraft/gametest
git commit -m "feat(evoking): a Bound Vex's melee hits three times as hard in the End"
```

---

### Task 10: Docs, playtest items, mutation testing

**Files:**

- Modify: `README.md`, `docs/playtest-checklist.md`, `docs/mutation-testing.md`, `docs/api-notes.md` (§24 corrections found on the way)
- Modify: `docs/superpowers/specs/2026-11-09-evoking-ii-parts-4-5-design.md` (only the as-built sentences listed below)

- [ ] **Step 1: README**

In the Bound Vex paragraph (after Part 4's cure and bribe sentences) add, in the README's own voice: at night, in the Nether and in the End the vexes close in above your head and watch more often; one pulls you out of lava (and douses you in the Nether), pushes you up when you are drowning under open water, catches you over the End's void and feeds you an enchanted golden apple, bats a ghast's fireball back, breaks end crystals while the dragon lives, and hits three times as hard in the End. Update the mod description file if the README's summary is mirrored there (check `neoforge.mods.toml`'s description; change it only if it lists vex behaviors).

- [ ] **Step 2: Playtest checklist**

Add a section "Evoking II (Part 5)" to `docs/playtest-checklist.md`, in the file's existing item format, with these items (each is Will's to check; none is claimed):

1. Twelve vexes overhead at night, in the Nether and in the End: are they out of the way of your clicks (draw a bow, place a block, hit a mob)? Is "above your head" the right place?
2. The change of posture at dusk and dawn, and in a thunderstorm.
3. The lava pull on a real client: do you reach the shore, can you still steer, does it feel like a pull and not a teleport? A lake with no shore within 6 blocks: are you held at the surface?
4. The Nether's water bucket: the bucket in the vex's hand, the two sounds, the splash, the fire out.
5. The drowning push: under open water you rise; under a ceiling you are left alone.
6. The void catch: step off the End island; where you land; the apple in the vex's hand; the eating sound; the four effects. Riding a strider or a boat off the edge. With an elytra open: no catch.
7. The fireball return: the sound, the fireball's path back to the ghast, the one-hit kill and its advancement.
8. Crystals: vexes break them (caged ones too) while you stand on a tower beside one: no damage, no throw. A crystal you shoot yourself still throws you.
9. Critical hits in the End: the particles and sound on every vex hit.
10. A rescuer that was mid-cure or fleeing a creeper: does it come, and does the cure restart afterwards?

- [ ] **Step 3: Mutation testing**

Run: `./gradlew pitest` (background it if it takes over two minutes; read the summary from its report).
Add a row to `docs/mutation-testing.md` for this part in the file's format: date 2026-11-10, the three new classes, killed/total, and each surviving mutant with either the test added to kill it or one line on why it is equivalent. Add the tests that kill non-equivalent survivors, then rerun until only equivalents remain.

- [ ] **Step 4: Spec sentences that the code settled**

In the Parts 4–5 spec, bring these sentences in line with the code as built (and with any divergence the earlier tasks' as-built notes record):

- §5 "Velocity": the push reads the client's last reported movement (`getKnownMovement()`), not the server-side velocity.
- §6.4: the last ground position is used only if it is still a standable spot; a failed search is not repeated for 20 ticks; the apple also feeds (vanilla's `onConsume` applies its food value).
- §7.1: a vex that gave a crystal up after 200 ticks claims none for another 200.
- §9: `VoidRule.needsCatch`'s parameter order as built (`…, movingDown, flying, groundWithin64`), and that the ground check is a supplier asked last.
- §6.1: only if Task 3's as-built note reports that a vex coasts out of a close band: the measured overshoot, as a note for Will (no rule change without him).
- §9: mock players report fluids only after `baseTick`; the dragon-alive test is behind the per-vex scripted answer in `AttackCrystalGoal`.

- [ ] **Step 5: Gates and commit**

Run: `./gradlew spotlessApply build`, `./gradlew runData` (the client-dist boot; nothing here touches client code, so it must stay green), `./gradlew runGameTestServer` twice.

```bash
git add README.md docs
git commit -m "docs(evoking): Part 5 in the README, playtest items, mutation testing, as-built spec notes"
```

## Divergence index

<!-- generated by plan_map.py --write-index; do not edit -->

| Task | SHA | Divergence |
|---|---|---|
| 1 | `2c484b4` | the GameTest world is flat in every dimension (a floor at y 0..3 even in the End), so `endVoid` clears the blocks below its slot in the forced chunks and restores them; `setInvulnerable` does not exist (`setPermanentlyInvulnerable(true)`); `isInLava` also needs the second `baseTick`; `Ghast` is in `monster`; the night setup test is registered unpadded. Baseline 349, now 355; reason: the sources and the server as they are. |
| 3 | `9fa69ec` | a close-posture flight is one goal use that ends on arrival, where the velocity is zeroed (a coasting vex sank to 1.5 above the feet), and a target mid-flight drops the wanted position; the close bands start at 2.5, not 2.2 (the move control stops up to half a block short). Measured after: min height 1.88, max sideways 2.46, no hops in 60 ticks. Review fixes for Task 2 are in `cce1ddc`. 366 tests; reason: coordinator rulings on the measurements, recorded in spec §6.1 and §10. |
| 4 | `c8613d0` | the target-keeping test uses a vulnerable husk out of reach (an invulnerable mob cannot be targeted); lava does not ignite a mock player through `baseTick`, so the burning tests set fire ticks; a rescue starts only once the vex has ticked; the Overworld pool is cased in stone so the padded box offers no other shore; "not for the unhurt" is five tests; the tail gives the hand back only to a living rescuer. 384 tests; reason: how the GameTest server and mock players behave. |
| 5 | `7ba6f2a` | the pushes apply the fluid drag to the reported movement (lava 0.5, water 0.8) and the lava pull has a hard 0.35 cap, so `drowning_push` expects `(0.08, 0.4, 0.04)`; a rescue also ends when the player stops needing it; review fixes for Task 4 are in `7730532`. 408 tests; reason: coordinator ruling after the Task 4 review (sending the undragged movement back each tick would let a steering player speed up without limit), recorded in spec §5, §6.2 and §9. |
| 6 | `5f3fab6` | test vexes are `NoAI` (a following vex teleports to the player before the test drops them); the 20-tick memo guards only the landing search, the last ground is still tried; the glide, ground-below and not-falling cases are separate tests; `teleportTo` already dismounts and zeroes the velocity, so those two lines cannot be seen red. 422 tests; reason: how the GameTest server and vanilla teleport behave. |
