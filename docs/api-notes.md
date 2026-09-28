# NeoForge 26.3.0.23-beta API notes

Verified against the decompiled sources (`build/mc-src`, unpacked from
`build/moddev/artifacts/minecraft-patched-26.3.0.23-beta-sources.jar`; NeoForge classes from
`build/nf-src`, unpacked from the Gradle-cache `neoforge-26.3.0.23-beta-sources.jar`) on 2026-09-28.
Re-verify after any NeoForge/Minecraft bump: each fact below is a premise of the Upside-Down design.

Paths are relative to `build/mc-src` (vanilla, patched) or `build/nf-src` (NeoForge).

## 1. Camera roll hook (Tasks 5, 8)

Verified.

- `net/minecraft/client/Camera.java:270-271` posts
  `new ViewportEvent.ComputeCameraAngles(this, partialTicks, entity.getViewYRot(partialTicks), entity.getViewXRot(partialTicks), 0)`.
- `Camera.java:273`: `this.setRotation(cameraSetup.getYaw(), cameraSetup.getPitch(), cameraSetup.getRoll());`
- `Camera.java:357-364`: `setRotation(float yRot, float xRot, float roll)` calls
  `this.rotation.rotationYXZ(PI - yRot*k, -xRot*k, -roll*k)` (line 361), then `FORWARDS.rotate`,
  `UP.rotate(this.rotation, this.up)` (363) and `LEFT.rotate` (lines 362-364). So roll also rotates `UP` and `LEFT`.
- `Camera.java:353-354`: the 2-arg `setRotation(yRot, xRot)` is deprecated and forwards roll 0.
- F5-front (mirrored): `Camera.java:284` `this.setRotation(this.yRot + 180.0F, -this.xRot, -this.roll);`
  Front view negates the roll, so `turn`/strafe compensation is exact only at 0 and 180 degrees there.
  **Accepted limitation.**
- Event class: `net/neoforged/neoforge/client/event/ViewportEvent.java:240` `ComputeCameraAngles`;
  ctor line 246 `(Camera, float renderPartialTicks, float yaw, float pitch, float roll)`; `getPartialTick()` at line 64.

Roll-axis derivation (Task 5's property test relies on it): with `r` = roll, the screen's right axis is
`(cos r, -sin r)` and up axis is `(sin r, cos r)` in the unrolled frame. A mouse delta `(dx, dy)` therefore
moves the gaze by `(dx*cos r - dy*sin r, dx*sin r + dy*cos r) = (dx', dy')`; `dy'` is fed to `turn` unchanged.

## 2. Minecart exception (Task 8)

Verified. `Camera.java:259-268`: the branch for `Minecart` passenger with `NewMinecartBehavior`
(`cartHasPosRotLerp()`) calls 2-arg `setRotation` (line 267) and `getCartLerpPosition` (268) and does **not**
post the event. The event is posted only in the `else` branch (line 269 onward). Roll is not applied
while riding a new-behavior minecart with lerp active.

## 3. Sound listener (no code needed)

Verified. `net/minecraft/client/sounds/SoundEngine.java:520`:
`new ListenerTransform(camera.position(), new Vec3(camera.forwardVector()), new Vec3(camera.upVector()))`.
`Camera.upVector()` is at `Camera.java:495`, so roll reaches stereo with no code.

## 4. Mouse (Task 6, Mixin)

Verified. `net/minecraft/client/MouseHandler.java`:

- line 350: `var event = net.neoforged.neoforge.client.ClientHooks.getTurnPlayerValues(sensitivity, smoothCamera);`
  (only sensitivity / cinematic mode; a Mixin is required).
- line 375 (inside `if (this.minecraft.player != null)`):
  `this.minecraft.player.turn(this.minecraft.options.invertMouseX().get() ? -xo : xo, this.minecraft.options.invertMouseY().get() ? -yo : yo);`
  The `LocalPlayer.turn(DD)V` invoke is the Mixin target (`@Redirect`/`@WrapOperation` on that call, `require = 1`).
  Arguments are the invert-adjusted `xo`, `yo` expressions above.

## 5. Movement input (Task 9)

Verified.

- `net/minecraft/client/player/ClientInput.java:10` `public Input keyPresses = Input.EMPTY;`
  line 11 `protected Vec2 moveVector = Vec2.ZERO;`
- `KeyboardInput.java:27` `this.keyPresses = new Input(...)` and `:38` `this.moveVector = new Vec2(leftImpulse, forwardImpulse).normalized();`
  Both reassigned in `tick()` every tick, so edits never accumulate.
- `net/minecraft/client/player/LocalPlayer.java:812-813`: `this.input.tick();` then
  `net.neoforged.neoforge.client.ClientHooks.onMovementInputUpdate(this, this.input);`
  (`ClientHooks.java:474-475` posts `MovementInputUpdateEvent(player, input)`; event class at
  `net/neoforged/neoforge/client/event/MovementInputUpdateEvent.java:24`, `getInput()` line 36).
- `moveVector` is `protected` in `ClientInput`: open it with the access transformer per plan Task 9 Step 2
  (`public net.minecraft.client.player.ClientInput moveVector` in `META-INF/accesstransformer.cfg`).
  Do not add a Mixin accessor.
- `keyPresses` is public and its type is a record: `net/minecraft/world/entity/player/Input.java:6`
  `public record Input(boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean shift, boolean sprint)`,
  so replace `keyPresses` whole with a new `Input`.

## 6. GameTest API (Task 7) - API shape

Recorded with one difference from the plan text: `TestData` is generic. Task 7 uses `new TestData<>(...)`
(diamond), so the plan's existing code compiles unchanged.

- `net/minecraft/gametest/framework/TestData.java:15` `public record TestData<EnvironmentType>(...)`.
- `TestData.java:51` `public TestData(EnvironmentType environment, Identifier structure, int maxTicks, int setupTicks, boolean required)`
  (also line 47 with a trailing `Rotation`). For instances the type argument is
  `Holder<TestEnvironmentDefinition<?>>`.
- `FunctionGameTestInstance.java:22`
  `public FunctionGameTestInstance(ResourceKey<Consumer<GameTestHelper>> function, TestData<Holder<TestEnvironmentDefinition<?>>> info)`.
- `net/minecraft/core/registries/BuiltInRegistries.java:368`
  `public static final Registry<Consumer<GameTestHelper>> TEST_FUNCTION`.
- NeoForge `net/neoforged/neoforge/event/RegisterGameTestsEvent.java:39,43` `registerEnvironment(Identifier, ...)` returns
  `Holder<TestEnvironmentDefinition<?>>`; `:49` `registerTest(Identifier, Function<TestData<Holder<...>>, GameTestInstance>, TestData<Holder<...>>)`;
  `:53` `registerTest(Identifier, GameTestInstance)`.
- `GameTestHelper.java` (in mc-src): `makeMockServerPlayerInLevel()` line 398, `assertValueEqual(N, N, String|Component)` lines 1095/1099,
  `assertFalse(boolean, String|Component)` lines 1115/1119.

## 7. Item / effect API (Tasks 8, 10) - API shape

Verified as planned.

- `net/minecraft/world/item/Item.java:226` `public InteractionResult use(Level level, Player player, InteractionHand hand)`.
- `ItemCooldowns.java:54` `public void addCooldown(ItemStack item, int time)` (also `:58` by `Identifier` cooldown group).
- `net/minecraft/world/effect/MobEffect.java:56` `protected MobEffect(MobEffectCategory category, int color)`
  (also `:65`, `:75` with particle args). Subclass to construct.
- `MobEffectInstance.java:65` `public MobEffectInstance(Holder<MobEffect>, int duration, int amplifier, boolean ambient, boolean visible, boolean showIcon)`.
- Milk: there is no `MilkBucketItem` class; milk is `Items.MILK_BUCKET` (`Items.java:1978-1980`) using
  `DataComponents.CONSUMABLE` = `Consumables.MILK_BUCKET`.
- Task 7's call `Items.MILK_BUCKET.finishUsingItem(new ItemStack(Items.MILK_BUCKET), helper.getLevel(), player)`
  works in 26.3, provided the level is a server level (`helper.getLevel()` is):
  `Item.java:251-254` `finishUsingItem` reads `DataComponents.CONSUMABLE` and returns `consumable.onConsume(level, entity, itemStack)`;
  `Consumable.java:86-88` runs each on-consume effect when `!level.isClientSide()`;
  `Consumables.java:65` `MILK_BUCKET = defaultDrink().onConsume(ClearAllStatusEffectsConsumeEffect.INSTANCE).build()`;
  `consume_effects/ClearAllStatusEffectsConsumeEffect.java:21-22` `apply` returns `user.removeAllEffects()`
  (`LivingEntity.java:1000`, which returns false on the client). The `ServerPlayer` branch
  (`Consumable.java:80-83`) only awards stats and triggers criteria.

## 8. Identity (Task 9/10)

Verified. `net/minecraft/world/entity/Entity.java:435-436`:
`public boolean equals(Object obj) { return obj instanceof Entity entity ? entity.getId() == this.getId() : false; }`
Player-identity checks need `==` with a `ReferenceEquality` suppression (plus a comment why).

## 9. Mixin coverage (Task 10)

Client Mixins are not exercised headlessly (runData does not load `MouseHandler`, checked 2026-09-28).
`require = 1` makes a missing target fail at client startup; the playtest (`runClient`) is the check.

Evidence: with `systemProperty 'mixin.debug.export', 'true'` temporarily added to `neoForge.runs.data`,
`./gradlew runData` succeeded (Mixin ran with `Env=CLIENT`), then

```text
$ find . -path "*/.mixin.out/*" -name "MouseHandler.class" -not -path "./build/*"
(no output)
```

The `build.gradle` change was reverted.

`compatibilityLevel`: `JAVA_25` in `spellcraft.mixins.json` is accepted by Mixin 0.8.7 (debug log:
"Compatibility level JAVA_25 specified by spellcraft.mixins.json is higher than the maximum level supported by
this version of mixin (JAVA_13). Compatibility level set to JAVA_25"), so no fallback to `JAVA_21` was needed.
`com.llamalad7.mixinextras` (0.5.5, bundled with NeoForge) resolves.
