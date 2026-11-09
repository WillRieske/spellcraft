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
- Cooldown gate: `ServerPlayerGameMode.java:352-359` `useItem` returns `InteractionResult.PASS` when
  `player.getCooldowns().isOnCooldown(itemStack)` (`ItemCooldowns.java:17`), before `itemStack.use` (`:366`) runs. So
  `Item.use` never sees a cast during a cooldown; the mod needs no cooldown check of its own. GameTests must go
  through `player.gameMode.useItem` to exercise the gate; calling `Item.use` directly bypasses it.
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

## 10. Effect cures: there is no cure API in 26.3 (Infinity task)

Verified 2026-09-29. `build/nf-src` has no `EffectCure`/`EffectCures`/`fillEffectCures`.

- Milk: `Consumables.MILK_BUCKET` → `ClearAllStatusEffectsConsumeEffect`
  (`world/item/consume_effects/ClearAllStatusEffectsConsumeEffect.java`, `apply` returns `user.removeAllEffects()`).
- Totem: `world/item/component/DeathProtection.java:25-27` `TOTEM_OF_UNDYING` includes
  `new ClearAllStatusEffectsConsumeEffect()`; applied from `LivingEntity.checkTotemDeathProtection` (`:1465`).
- `/effect clear`: `server/commands/EffectCommands.java:211` `removeAllEffects()`; `:224` `removeEffect(holder)`.
- `LivingEntity.removeAllEffects` (`:1000-1017`) posts `MobEffectEvent.Remove` per effect; the event has no cause.
- So "immune to milk and totem, removable by command" = a Mixin on `ClearAllStatusEffectsConsumeEffect.apply`
  wrapping the `LivingEntity.removeAllEffects()Z` call.

## 11. Abilities and the creative inventory (Infinity task)

- `Player.hasInfiniteMaterials()` (`world/entity/player/Player.java:1360`) is `abilities.instabuild`.
- `ServerGamePacketListenerImpl.handleSetCreativeModeSlot` (`:2128-2152`) accepts the slot only if
  `player.hasInfiniteMaterials()`; `inventoryMenu` slot 36 = hotbar 0.
- `InventoryScreen.init`/`containerTick` (`client/gui/screens/inventory/InventoryScreen.java:37-60`) swap to
  `CreativeModeInventoryScreen` when `hasInfiniteMaterials()`: no client code needed.
- `GameType.updatePlayerAbilities` (`world/level/GameType.java:68-86`): survival/adventure → `mayfly`,
  `instabuild`, `invulnerable`, `flying` all false; creative → first three true; spectator → mayfly, invulnerable,
  flying true.
- `ServerPlayer.onUpdateAbilities` (`:1859`) sends `ClientboundPlayerAbilitiesPacket`; `setGameMode` (`:1870`)
  rewrites abilities; `gameMode()` (`:1897`).

## 12. Death and respawn (Infinity task)

- `ServerPlayer.die` (`:928`): `CommonHooks.onLivingDeath` (`:930`, `LivingDeathEvent`, cancellable) runs before
  `dropAllDeathLoot` (`:968`).
- `Player.dropEquipment` (`:567-575`): only if `!KEEP_INVENTORY`: `destroyVanishingCursedItems()` (by
  `EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP`) then `inventory.dropAll()`.
- XP: `Player.java:1674`, and `isAlwaysExperienceDropper` → orbs drop on death.
- `ServerPlayer.restoreFrom(old, restoreAll)` (`:1720-1767`): `setGameModeForPlayer(old mode)` resets abilities
  (`:1723`); inventory transferred only with `KEEP_INVENTORY` or spectator (`:1739`); fires
  `PlayerEvent.Clone(new, old, wasDeath = !restoreAll)` at `:1767`.
- `PlayerList.respawn(ServerPlayer, boolean keepAllPlayerData, Entity.RemovalReason)` (`server/players/PlayerList.java:402`).
- `Inventory`: `INVENTORY_SIZE = 36`, `getSelectionSize() = 9`, `SLOT_OFFHAND = 40`, armor 36-39, body 41,
  saddle 42 (`world/entity/player/Inventory.java:31-50`).
- Game rules: `world/level/gamerules/GameRules.java` `get(GameRule)` (`:124`), `set(GameRule, T, MinecraftServer)` (`:133`);
  `GameRules.KEEP_INVENTORY`.

## 13. Data attachments (Infinity task)

- `attachment/AttachmentType.java`: `builder(Supplier)` (`:109`), `Builder.serialize(MapCodec)` (`:198`),
  `copyOnDeath()` (`:237`, not used). Register in `NeoForgeRegistries.ATTACHMENT_TYPES`.
- `IAttachmentHolder`: `getData(Supplier)`, `setData(Supplier, T)`, `removeData(Supplier)`.

## 14. GameTest players (all tasks)

- `GameTestHelper.makeMockServerPlayerInLevel()` (`:397-409`) returns a `ServerPlayer` whose `gameMode()` is
  hard-coded to `CREATIVE`. Our `MockPlayers.inLevel(helper, GameType)` copies its connection setup
  (`CommonListenerCookie.createInitial`, `Connection(PacketFlow.SERVERBOUND)`, `EmbeddedChannel`,
  `PlayerList.placeNewPlayer`) without the override, then calls `setGameMode`.
- `ServerPlayer.teleportTo(ServerLevel, x, y, z, Set<Relative>, yRot, xRot, resetCamera)` (`:1824`).
- A mock player is invulnerable (damage and `kill()` do nothing) until `connection.markClientLoaded()`:
  `ServerPlayer.isInvulnerableTo` (`:1375`) is true while `!connection.hasClientLoaded()`. `MockPlayers.inLevel`
  calls it. After a dimension change also call `player.hasChangedDimension()` (clears `isChangingDimension`,
  same check) and `markClientLoaded()` again before killing.
- `PlayerList.respawn(dead, false, RemovalReason.KILLED)` works for mock players and fires `PlayerEvent.Clone`
  (Infinity death GameTests G2-G6, G10, G11 use it; no fallback needed).

## 15. Enchanting table (scribing task)

- `world/inventory/EnchantmentMenu.java`: private `enchantSlots` (`:34`), `access`, `enchantmentSeed`; public
  `costs`, `enchantClue`, `levelClue`, all synced by `DataSlot.shared` (`:72-82`).
- `slotsChanged(Container)` (`:85-131`): non-enchantable items take the `else` branch, which zeroes the costs.
  This runs on both sides; the client's `access` is `ContainerLevelAccess.NULL`.
- `clickMenuButton(Player, int)` (`:135-181`): checks lapis (`currency.getCount() < buttonId + 1`) and levels,
  waived by `hasInfiniteMaterials()`, then does all work in one `this.access.execute(BiConsumer)` call.
  The client calls it first (`EnchantmentScreen.mouseClicked`, `:84`) and sends the click only if it returns true.
- `EnchantmentScreen.java:114`: the option looks disabled when `enchantClue[i] == -1` (NeoForge patch).
  The tooltip is built in `extractRenderState` (`:152-205`) and set with
  `GuiGraphicsExtractor.setComponentTooltipForNextFrame(Font, List, int, int)`, which keeps the first
  tooltip per frame (`client/gui/GuiGraphicsExtractor.java:1173`).
- `AbstractContainerScreen.topPos` is `protected` (`:50`), but NeoForge patches in a public `getTopPos()` (`:624`): use that, no access transformer.

## 16. Config lists and randomness (scribing task)

- `ModConfigSpec.Builder.defineList(List<String> path, Supplier<List<? extends T>>, Supplier<T>, Predicate<Object>, Range<Integer> sizeRange)` (`common/ModConfigSpec.java:621`); `Range.of(int, int)` (`:1014`).
- `util/RandomSource.java:10` does not extend `java.util.random.RandomGenerator`, so the pure core takes a `Dice`.

## 17. Scroll effects (framework task)

- `ParticleTypes.LARGE_SMOKE` (`core/particles/ParticleTypes.java:105`), `SoundEvents.FIRE_EXTINGUISH` (`sounds/SoundEvents.java:636`).
- `ServerLevel.sendParticles(T, x, y, z, count, xDist, yDist, zDist, speed)` (`:1355`);
  `Level.playSound(@Nullable Entity, x, y, z, SoundEvent, SoundSource, volume, pitch)` (`:434`).
- `Player.sendOverlayMessage(Component)` (`:1414`) writes to the action bar.

## 18. Fire blocks, level attachments and allies (Trailing task)

Verified 2026-11-04 against the same unpacked sources.

- `world/level/block/BaseFireBlock.java`: ctor `:32` `(BlockBehaviour.Properties, float fireDamage)`;
  `entityInside(BlockState, Level, BlockPos, Entity, InsideBlockEffectApplier, boolean isPrecise)` `:128-132`
  applies `CLEAR_FREEZE`, `FIRE_IGNITE`, then
  `runAfter(FIRE_IGNITE, e -> e.hurt(e.level().damageSources().inFire(), this.fireDamage))` (`:131`);
  `fireIgnite(Entity)` `:134` skips `fireImmune()` entities (`Entity.java:1614`); `onPlace` `:150-165`, whose whole
  body is guarded by `!oldState.is(state.getBlock())`, calls `PortalShape.findEmptyPortalShape` (through
  `EventHooks.onTrySpawnPortal`, `:154`) in the Overworld/Nether (**lights portals**) and then removes the block if
  `!canSurvive`. A subclass that must not light portals overrides `onPlace`, keeping that guard and the
  `!canSurvive` → `removeBlock(pos, false)` tail.
- `SoulFireBlock.java`: `canSurvive` `:32-34` requires `BlockTags.SOUL_FIRE_BASE_BLOCKS` below (`canSurviveOnBlock`
  `:36`), so vanilla soul fire cannot sit on ordinary ground; `updateShape` `:18-29` returns air when it can't
  survive. Fire damage 2.0 (`:14`).
- `Blocks.java:1368-1379` `SOUL_FIRE` properties: `mapColor(COLOR_LIGHT_BLUE)`, `replaceable()`, `noCollision()`,
  `instabreak()`, `lightLevel(state -> 10)`, `sound(SoundType.WOOL)`, `pushReaction(PushReaction.POPPED)`.
- TNT by fire: the only path from a fire block is `FireBlock.java:235` `oldState.onCaughtFire(...)` inside the
  fire's scheduled tick (`checkBurnOut`). TNT's other ignition paths involve no fire block: redstone
  (`TntBlock.java:46,54` `hasNeighborSignal`), breaking unstable TNT (`:62`), flint and steel or a fire charge
  (`:109`) and burning projectiles (`:134`). A fire block with no scheduled tick never ignites TNT and never
  spreads; a burning arrow shot by a mob standing in a flame still can, as in vanilla.
- `BlockTags.FIRE` users (`grep -rn "BlockTags.FIRE\b" build/mc-src`): `NodeEvaluator.java:99` (mobs path
  around it), `AbstractThrownPotion.java:124` (water bottles extinguish), `FallingBlock.java:60`,
  `PortalShape.java:167`, `Cushion.java:130`, `ShearsItem.java:54`, `PistonBaseBlock.java:284`,
  `SculkVeinBlock.java:164`, `VanillaBlockTagsProvider.java:212,246` (also in `DRAGON_TRANSPARENT`).
- Render layer: `client/renderer/chunk/ChunkSectionLayer.java:31-37` `byTransparency(Transparency)` and
  `client/resources/model/geometry/BakedQuad.java:99` pick the layer from the texture's transparency. There is no
  `ItemBlockRenderTypes` or `render_type` field in 26.3 (`grep -rn render_type build/mc-src build/nf-src` → none),
  so a block that reuses `minecraft:block/soul_fire_*` models renders cutout with no client code.
- Level attachments: `world/level/Level.java:94` `Level extends net.neoforged.neoforge.attachment.AttachmentHolder`;
  `ServerLevel.java:318` calls `LevelAttachmentsSavedData.init(this)`; `LevelAttachmentsSavedData.java:62-65`
  `isDirty()` returns `true` ("Always re-save"), so an attachment value mutated in place is saved on every world
  save. `IAttachmentHolder.java:46` `getData(Supplier<AttachmentType<T>>)` creates the default on first read.
  `AttachmentType.java:210` `Builder.serialize(MapCodec<T>, Predicate<? super T> shouldSerialize)` skips saving
  values the predicate rejects (used so an empty ledger is not written for every level).
- Ticks: `event/tick/LevelTickEvent.java:61` `Post` (per level, `getLevel()` `:41` returns `Level`);
  `event/tick/PlayerTickEvent.java:52` `Post`. `PlayerEvent.java:480` `PlayerLoggedOutEvent`;
  `event/server/ServerStoppedEvent.java` (game bus) for clearing server-wide static state.
- Allies: `Entity.java:2879-2885` `isAlliedTo(Entity)` is `final`: `this == other || considersEntityAsAlly(other)
  || other.considersEntityAsAlly(this)` (false for `null`), and `considersEntityAsAlly` is the team check
  (`isAlliedTo(other.getTeam())`; the `isAlliedTo(Team)` overload is at `:2887`);
  `TamableAnimal.java:209-222` overrides it: a tamed animal considers its root owner (and the owner's allies) allies.
  `TamableAnimal.getTeam()` (`:192-206`) also returns the owner's team, so a pet of a teamed owner is allied through
  the team path too; a pet test must use an owner with no team to exercise the tame path.
- Entity state: `Entity.java:737` `onGround()`, `:2774` `isPassenger()`, `:3866` `blockPosition()`, `:696`
  `setOnGround(boolean)`, `:2504` `startRiding(Entity, boolean force, boolean sendEventAndTriggers)`,
  `:660` `igniteForSeconds(float)`, `:676` `getRemainingFireTicks()`; `Player.java:1721` `isSpectator()`.
  `ServerPlayer.java:1866` `level()` returns `ServerLevel`. `Level.java:689` `isLoaded(BlockPos)`.
- Test entry points: `BlockBehaviour.BlockStateBase` `entityInside(Level, BlockPos, Entity, InsideBlockEffectApplier,
  boolean)` `:773` and `randomTick(ServerLevel, BlockPos, RandomSource)` `:769` are public;
  `InsideBlockEffectApplier.StepBasedCollector` (`world/entity/InsideBlockEffectApplier.java:32`) collects the
  effects and `applyAndClear(Entity)` runs them. `BlockBehaviour.BlockStateBase.canBeReplaced()` `:840` (no-arg)
  reads the `replaceable` property; `isFaceSturdy(BlockGetter, BlockPos, Direction)` `:893`.
- Registration: `DeferredRegister.Blocks.registerBlock(String, Function<Properties, B>, Supplier<Properties>)`
  (`registries/DeferredRegister.java:433`) sets the block id on the properties.
- GameTest helpers used: `TamableAnimal.tame(Player)` `:170`; `Scoreboard.addPlayerTeam(String)` `:249`,
  `addPlayerToTeam(String, PlayerTeam)` `:272`, `removePlayerTeam(PlayerTeam)` `:262`;
  `GameTestHelper.assertValueEqual(N, N, String)` `:1095`; `Vec3.atBottomCenterOf(Vec3i)` `:61`.
- Chunk loading (stale flames, fix passes 2 and 3): `Level.java:369-376` `getBlockState` calls `getChunk(x, z)`
  (`:208-210`), which is `getChunk(x, z, ChunkStatus.FULL)` and so `LevelReader.java:133-135`
  `getChunk(x, z, status, true)`: **reading a block in an unloaded chunk loads (or generates) it synchronously**;
  `ServerChunkCache.java:144-178` schedules the load and `managedBlock`s the server thread until it is done (`:169`),
  and `Level.java:213-219` throws if that load fails. **`isLoaded` means "ticketed", not "ready"**: `Level.java:689-693`
  `isLoaded(BlockPos)` is `isInValidBounds(pos)` and then `getChunkSource().hasChunk(chunkX, chunkZ)`, and
  `ServerChunkCache.java:262-267` `hasChunk` only checks that the visible chunk holder's ticket level has reached FULL
  (`chunkAbsent`, `:258-260`). That is true as soon as the ticket is added, while the load may still be running, and a
  block read then waits for it. `ServerChunkCache.java:180-210` `getChunkNow(chunkX, chunkZ)` (`@Nullable LevelChunk`,
  declared on `ChunkSource.java:16`) is the readiness test: a lookup in the 4-entry cache and the chunk holder that
  returns the chunk only at FULL status (`getChunkIfPresent(ChunkStatus.FULL)`, `:201`), or NeoForge's
  `currentlyLoading` chunk while it is being promoted on the server thread (`:200`, set in
  `ChunkStatusTasks.java:202-215`), and `null` otherwise; it never loads or waits, and returns `null` off the server
  thread (`:182-184`). `ServerLevel.java:1245` `getChunkSource()` returns `ServerChunkCache`. Code that must neither
  load nor wait for a chunk checks `getChunkNow(...) != null` before any `getBlockState`/`setBlock`/`removeBlock`;
  `BlockPos.getX(long)`/`getZ(long)` (`core/BlockPos.java:69,77`) and `SectionPos.blockToSectionCoord(int)`
  (`core/SectionPos.java:81`) give the chunk coordinates from a packed position. Tests that prove nothing was loaded
  still assert `isLoaded` is false (the stronger claim: not even ticketed).
- Build limits: `Level.java:182-184` `isInValidBounds(BlockPos)` is build height plus the horizontal chunk limit;
  `getBlockState` returns `VOID_AIR` outside it (`:370-372`, which passes `isAir()`) and `setBlock` returns `false`
  (`:223-226`; `LevelWriter.java:16-17` the 3-argument overload returns the same boolean).
- `Codec.optionalFieldOf(name, default)` (DataFixerUpper 10.0.21 sources, `com/mojang/serialization/Codec.java:274`,
  `:303-308`; not in `build/mc-src`: unzip `datafixerupper-10.0.21-sources.jar` from the Gradle cache): decoding a
  missing field yields the default (`codecs/OptionalFieldCodec.java:30-33`), so a save written before the field
  existed still loads; encoding a value **equal to the default omits the field** (`Objects.equals(a, defaultValue)
  ? Optional.empty()`). It is not lenient: a present but malformed field fails the decode. `RecordCodecBuilder.mapCodec`
  (`codecs/RecordCodecBuilder.java:75`) builds the `MapCodec` that `AttachmentType.Builder.serialize` takes.
- Attachment saving (G27): `AttachmentHolder.java:120-137` `serializeAttachments(ValueOutput)` is public and final;
  for each serializable attachment it writes `tag.child(<type id>)` and `discard`s that key when the serializer
  returns false. `AttachmentType.java:210-227`: the `serialize(MapCodec, Predicate)` serializer returns false when
  the predicate rejects the value, else `output.store(codec, value)` (the map codec's fields go straight into the
  child). `LevelAttachmentsSavedData.java:46-48` saves a level through `TagValueOutput.createWithContext(reporter,
  registryAccess)` (`world/level/storage/TagValueOutput.java:27`) and `buildResult()` (`:159`), the same calls a
  test can make on `ServerLevel` (it inherits `serializeAttachments` from `Level`).
- **NeoForge loads the chunk an entity moves into**: `Entity.java:3983-3986` (end of `setPosRaw`, "Neo: ensure
  target chunk is loaded") calls `level.getChunk(x >> 4, z >> 4)` for any server entity that is added to its level
  and not removed. Every move goes through it (`setPos` `:486`, `teleportSetPosition` `:3298-3300`, and so
  `ServerPlayer.teleport` `ServerPlayer.java:1176-1197` within a level), so teleporting a player to a far spot loads
  that spot's chunk. A test that needs a player standing in an unloaded chunk uses a `discard()`ed player.
  `Entity.setOnGround` `:696-699` runs `checkSupportingBlock`, whose collision lookup does not load chunks.
- Ally overrides that matter to a team-less entity and a player (for the `isSpared` fast path):
  `TamableAnimal.java:209-222` (an `OwnableEntity`, `:34`) and `Allay.java:160-163` (`isLikedPlayer`, the
  `LIKED_PLAYER` brain memory, `MemoryModuleType.java:136`). `AbstractIllager.java:31-38` allies only
  `#illager_friends` (illagers) and `Evoker.java:81-99` its vexes. `Team.isAlliedTo(null)` is false
  (`world/scores/Team.java:17-19`). `EntityGetter.java:113-122` `getPlayerByUUID` scans the level's player list.
- Test entry points (fix pass 2): `BlockBehaviour.BlockStateBase.tick(ServerLevel, BlockPos, RandomSource)` `:765` is
  public; `PlayerEvent.PlayerLoggedOutEvent(Player)` (`PlayerEvent.java:481`) and `ServerStoppedEvent(MinecraftServer)`
  (`event/server/ServerStoppedEvent.java:18`) have public constructors; `GameTestHelper.runAfterDelay(long,
  Runnable)` `:964`, `getBlockState(BlockPos)` `:111` (relative); `CompoundTag.contains(String)` `:293`,
  `getCompoundOrEmpty(String)` `:373`; `ProblemReporter.DISCARDING` (`util/ProblemReporter.java:19`). The GameTest
  server uses `WorldPresets.FLAT_ALL_DIMENSIONS` (`GameTestServer.java:127`), so it has a Nether.

## 19. Evoker spells, synced effect particles, keys, packets and enum extensions (Evoking task)

Verified 2026-11-06 against the same unpacked sources, plus FML `loader-12.0.0-sources.jar` unpacked to
`build/fml-src`.

- Evoker timings: `monster/illager/Evoker.java` `EvokerAttackSpellGoal.getCastingTime` 40, `getCastingInterval` 100
  (`:135-142`); `EvokerSummonSpellGoal` 100 / 340 (`:244-251`); warmup `SpellcasterIllager.java`
  `SpellcasterUseSpellGoal.getCastWarmupTime` 20 (`:219-221`); `start()` sets `spellCastingTickCount = getCastingTime()`
  and plays the prepare sound (`:196-206`) and sets `nextAttackTickCount = tickCount + getCastingInterval()` (`:199`,
  so the interval counts from the press); `canUse` (`:185`) refuses while `isCastingSpell()` or before
  `nextAttackTickCount`; `tick()` performs when the warmup hits 0 and plays `getCastingSoundEvent`
  (`:209-215`). Fang placement: `Evoker.createSpellEntity` (`:176-204`) walks down from `BlockPos.containing(x, maxY,
  z)` while `y >= floor(minY) - 1` until the block below `isFaceSturdy(UP)`, adds the cell's collision-shape top, and
  spawns `new EvokerFangs(level, x, y + topOffset, z, angle, delayTicks, evoker)`; `minY = min(target.y, evoker.y)`,
  `maxY = max(...) + 1` (`:147-149`); the line pattern's delay is the block index (`:161-173`). Vex summon
  (`:254-275`): `offset(-2 + random(5), 1, -2 + random(5))` (`:259`), `EntityTypes.VEX.create(level, MOB_SUMMONED)`,
  `snapTo(pos, 0, 0)`, `finalizeSpawn(level, difficultyAt(pos), MOB_SUMMONED, null)`, `setOwner(this)`,
  `setBoundOrigin(pos)`, `setLimitedLife(20 * (30 + random(90)))`, team join, `addFreshEntityWithPassengers`.
- Casting particles: `SpellcasterIllager.tick` (`:81-115`): `bodyAngle = yBodyRot·π/180 + cos(tickCount·0.6662)·0.25`,
  hands at `(x ± cos·0.6·scale, y + 1.8·scale, z ± sin·0.6·scale)`, `ColorParticleOption.create(ENTITY_EFFECT, r, g, b)`;
  colors `SUMMON_VEX(0.7, 0.7, 0.8)`, `FANGS(0.4, 0.3, 0.35)` (`:124-125`). Arm pose: `IllagerModel.java:128-136`
  SPELLCASTING: `xRot = cos(ageInTicks·0.6662)·0.25`, `rightArm.zRot = 3π/4`, `leftArm.zRot = −3π/4`.
- `EvokerFangs.java`: ctor `(Level, x, y, z, rotationRadians, warmupDelayTicks, LivingEntity owner)` (`:35-41`);
  the bite at `warmupDelayTicks == -8` over `getBoundingBox().inflate(0.2, 0, 0.2)` (`:87-91`); `dealDamageTo`
  skips the owner and `owner.isAlliedTo(entity)`, hurts with `indirectMagic(this, owner)` for 6 (`:104-120`);
  reads `Warmup` (`:58`) and saves it (`:64`). So a fang's damage source has `getDirectEntity()` = the fangs and `getEntity()` = the owner.
- `Vex.java`: `setOwner(Mob)` (`:177`) cannot take a player; target goals = `HurtByTargetGoal(Raider)`,
  `VexCopyOwnerTargetGoal`, `NearestAttackableTargetGoal<Player>` (`:93-95`); `setLimitedLife` (`:181`),
  `setBoundOrigin` (`:149`), `getBoundOrigin` (`:145`); saves `life_ticks` while limited (`:128-130`) and `owner`
  through `EntityReference.store` (`:132`); `Vex` is an `OwnableEntity` whose `considersEntityAsAlly` is **not**
  overridden (only `Evoker.considersEntityAsAlly` knows its vexes, `Evoker.java:81-99`). `Mob.goalSelector` and
  `targetSelector` are public (`Mob.java:135-136`); `GoalSelector.removeAllGoals(Predicate)` (`:32`),
  `getAvailableGoals()` (`:109`), `WrappedGoal.getGoal()` (`:84`).
- Targeting: `NearestAttackableTargetGoal(Mob, Class<T>, boolean mustSee, TargetingConditions.Selector)` (`:25`)
  builds `forCombat().range(getFollowDistance())` **at construction** (`:40`), and `TargetGoal.getFollowDistance`
  is the `FOLLOW_RANGE` attribute (`:74-76`): set the attribute before adding the goal.
  `TargetingConditions.test` (`:59-96`): `canBeSeenByAnyone`, the selector, `targeter.canAttack(target)`
  (`LivingEntity.java:974-976` → `canBeSeenAsEnemy` = `!isInvulnerable() && canBeSeenByAnyone()`, `:978-984`;
  `Player.java:745-746` adds `!abilities.invulnerable`, so creative and spectator players are never enemies),
  `isAlliedTo`, range, line of sight. Nothing there checks `attackable()`: `ArmorStand.attackable()` is false
  (`:622-624`) and the vindicator's goal adds `target.attackable()` to its selector (`Vindicator.java:209`).
- Effect sync: `LivingEntity.DATA_EFFECT_PARTICLES` is `private static final EntityDataAccessor<List<ParticleOptions>>`
  (`:188`); `updateSynchronizedMobEffectParticles` (`:923-932`) fills it with the particle options of every effect the
  `EffectParticleModificationEvent` leaves visible (default `effect.isVisible()`, `EffectParticleModificationEvent.java:32`);
  the client spawns one of them at a random body position with chance 1/4 per tick (`:892-900`). Effects themselves
  reach only the entity's own client (`ServerPlayer.java:1783,1795`), passengers (`LivingEntity.java:1127`) and
  pairing (`PlayerList.java:488-492`), so the synced particle list is the only vanilla channel that tells other clients
  about an effect. `MobEffect(MobEffectCategory, int color, Function<MobEffectInstance, ParticleOptions>)`
  (`MobEffect.java:75`) is NeoForge's lazy particle constructor. `MobEffectInstance(effect, duration, amplifier,
  ambient, visible, showIcon)` (`:65`); the HUD draws only `showIcon()` effects (`Hud.java:552`); the inventory list
  filters through `IClientMobEffectExtensions.isVisibleInInventory` (`ClientHooks.java:589-591`), registered with
  `RegisterClientExtensionsEvent.registerMobEffect(extensions, Holder<MobEffect>...)` (`:86`).
- Particles: `SimpleParticleType(boolean overrideLimiter)` is public (`:11`);
  `RegisterParticleProvidersEvent.registerSpecial(ParticleType<T>, ParticleProvider<T>)` (`:49`), whose provider may
  return `null`; `ParticleResources.reload` loads only the `particles/*.json` it lists (`:214-231`), so a special
  type needs no json. `ColorParticleOption.create(ParticleType<ColorParticleOption>, float, float, float)` (`:52`).
- Keys: 26.3 key codes are SDL scancodes: `InputConstants.KEY_F = 9`, `KEY_V = 25` (`InputConstants.java:46,62`);
  `Options.java:676` binds swap-offhand to 9 (F) and `:736` `key.debug.dumpVersion` to 25 (V, an F3 chord).
  `KeyMapping(String, IKeyConflictContext, InputConstants.Type, int, Category)` (`KeyMapping.java:122`);
  `same(KeyMapping)` (`:244`), `consumeClick()` (`:201`); `Category(Identifier)` is a public record whose
  `label()` key is `id.toLanguageKey("key.category")` (`:333-334`); `RegisterKeyMappingsEvent.register` (`:42`) and
  `registerCategory` (`:49`). `Minecraft.tick` fires `ClientTickEvent.Pre` (`:1837`) before `handleKeybinds()`
  (`:1865`), which drains `keySwapOffhand.consumeClick()` (`:2000`): clicks consumed in `Pre` never swap.
- Packets: `RegisterPayloadHandlersEvent.registrar(String)` (`:40`), `PayloadRegistrar.playToServer(Type,
  StreamCodec, IPayloadHandler)` (`:60`), handlers run on the main thread by default (`:29`);
  `NeoForgeStreamCodecs.enumCodec(Class)` (`:62`); `ClientPacketDistributor.sendToServer` (`:22`);
  `CustomPacketPayload.Type(Identifier)` (`:79`); `IPayloadContext.player()`. `RegisterPayloadHandlersEvent`,
  `RegisterKeyMappingsEvent`, `RegisterParticleProvidersEvent`, `RegisterClientExtensionsEvent` and
  `RegisterRenderStateModifiersEvent` all implement `IModBusEvent`, as `RegisterGameTestsEvent` does, so a plain
  `@EventBusSubscriber(modid = …)` class receives them (`SpellcraftGameTests` already relies on this).
- Render: `AvatarRenderer.extractRenderState` sets `state.leftArmPose`/`rightArmPose` (`:174-178`), then the registered
  modifiers run: `EntityRenderer.createRenderState` calls `extractRenderState` (`EntityRenderer.java:163`) and then
  `RenderStateExtensions.onUpdateEntityRenderState` (`:165`);
  `RegisterRenderStateModifiersEvent.registerAvatarEntityModifier` (`:101`) registers an
  `AvatarRenderStateModifier` (`accept(T extends Avatar & ClientAvatarEntity, AvatarRenderState)`,
  `AvatarRenderStateModifier.java:16`); `Avatar extends LivingEntity`. `ArmedEntityRenderState.rightArmPose`/`leftArmPose` are public (`:17,20`);
  `EntityRenderState.ageInTicks` (`:22`). `HumanoidModel.poseRightArm/poseLeftArm` route an unknown pose to
  `ArmPose.applyTransform` (`:385,433`), which calls the NeoForge `IArmPoseTransformer(HumanoidModel<?>,
  HumanoidRenderState, HumanoidArm)` (`:543`); `rightArm`/`leftArm` are public (`:76-77`). `ArmPose` is an
  `IExtensibleEnum` with the private constructor `(boolean twoHanded, boolean affectsOffhandPose,
  IArmPoseTransformer)` (`:527`): FML enum extension (`build/fml-src/.../enumextension/EnumPrototype.java:42-104`)
  reads `META-INF/enumextensions.json` entries `{enum, name, constructor, parameters: {class, field}}`, the `name`
  must start with the mod id (`:49`), the `field` is an `EnumProxy`; `EnumProxy.getValue()` forces the enum class to load (which applies the
  extension) and throws an NPE ("Enum not initialized ...") only if `enumextensions.json` did not point a
  `parameters.field` at this proxy (`EnumProxy.java:38-47`), and the file is named by `enumExtensions=` in `neoforge.mods.toml` (`FMLLoader.java:707`).
- Allies: `Entity.isAlliedTo(Entity)` is final (`:2879`); `OwnableEntity.getOwnerReference()` and
  `EntityReference.getUUID()` (`:51`); entities are attachment holders (`Entity.java:162-163`) and save them
  (`serializeAttachments` `:2176`, `deserializeAttachments` `:2246`); `IAttachmentHolder.hasData(Supplier)`,
  `getExistingData(Supplier)`, `getData(Supplier)`, `setData(Supplier, T)`.
- Events: `EntityJoinLevelEvent` is posted for every entity added to a server level, freshly spawned or loaded from
  disk (`PersistentEntitySectionManager.java:75`, with `loadedFromDisk()`); `LivingIncomingDamageEvent` is posted
  from `LivingEntity.hurtServer` (`:1237`) and is cancellable; `PlayerTickEvent.Post` (`:52`).
- Misc: `DerivedLevelData.getGameTime` delegates to the overworld's (`:23-24`), so game time is global;
  `Player.sendOverlayMessage` (`:1414`); `EntityGetter.getEntitiesOfClass(Class, AABB, Predicate)` (`:23`);
  `SoundEvents.EVOKER_CAST_SPELL`, `EVOKER_PREPARE_ATTACK`, `EVOKER_PREPARE_SUMMON` (`:608,613,614`);
  `Entity.snapTo(BlockPos, float, float)` (`:1839`); `Attributes.FOLLOW_RANGE` (`:51`), `AttributeInstance.setBaseValue`
  (`:45`); `Entity.save(ValueOutput)` (`:2116`), `load(ValueInput)` (`:2203`); `TagValueOutput.createWithContext`
  (`:27`), `buildResult` (`:159`); `TagValueInput.create(ProblemReporter, HolderLookup.Provider, CompoundTag)` (`:40`);
  `CompoundTag.getIntOr` (`:321`), `store(String, Codec, T)` (`:508`); `UUIDUtil.CODEC` (`:23`).
- GameTests: `TestData` has 12 fields (`:15-27`): `environment, dimension, structure, maxTicks, setupTicks, required,
  rotation, manualOnly, maxAttempts, requiredSuccesses, skyAccess, padding` (padding 0…128). The grid spawner spaces
  tests by `getTestBounds()` (`StructureGridSpawner.java:65-67`), which `TestInstanceBlockEntity.getTestBounds`
  inflates by the padding (`:97-99`); `StructureUtils.clearSpaceForStructure` turns the **whole padded box** to air
  (`clearBlock`, `:119-130`: stone only below the box), so a padded test stands over a pit and lays its own floor.
  The GameTest server world is flat (`GameTestServer.java:127`, §18) and has a Nether; a player reaches it with
  `ServerPlayer.teleportTo(ServerLevel, x, y, z, Set<Relative>, yRot, xRot, boolean)`, which returns `boolean`
  (`:1824`), and `server.getLevel(Level.NETHER)` (`MinecraftServer.java:1205`).
- Mock players are not given a player tick: `ServerPlayer.tick()` (`ServerPlayer.java:598-637`) never calls
  `super.tick()` (the only `super.tick()` in the file is in `doTick()`, `:668` with the call at `:671`), and the only
  caller of `doTick()` is `ServerGamePacketListenerImpl.tickPlayer()` (`:322`, call at `:327`), itself called only from
  `ServerGamePacketListenerImpl.tick()` (`:301`, call at `:307`), which `Connection.tick()`
  (`Connection.java:361-364`) reaches through `TickablePacketListener.tick()`, and `ServerConnectionListener` calls per
  connection (`ServerConnectionListener.java:181`), so it runs only for connections the server tracks. A
  `MockPlayers.inLevel` player is therefore not ticked like a real one: `PlayerTickEvent` does not fire for it and its
  mob effects do not count down. Its `tickCount` DOES advance, one per tick: `placeNewPlayer` puts it in the level's
  entity tick list, and `ServerLevel.tickNonPassenger` (`:857`) calls `entity.commonTick()` (`:863`), which does
  `tickCount++` (`Entity.java:535`), then `entity.tick()` (`:864`), which is `ServerPlayer.tick()` without
  `super.tick()`. GameTests step the caster by hand (`FangGameTests.step`).
- `ClientTickEvent.Post` fires while the game is paused (`Minecraft.java:1941-1942`, not guarded by `pause`), but the
  level tick (`tickEntities` `:1876`, `tickBlockEntities` `:1878`, inside `if (!this.pause)` `:1872`) and the particle
  engine (`particleEngine.tick()` `:1925`, inside `if (!this.pause && ...)` `:1924`) do not run then, so a `Post`
  handler must check `Minecraft.isPaused()` itself.
- `RegisterClientExtensionsEvent.registerMobEffect(IClientMobEffectExtensions, Holder<MobEffect>...)` is
  `@SafeVarargs` (`:85-86`).
- `runData` initializes the client subscriber classes and parses the config specs, and now `HumanoidModel.ArmPose`
  too, through `EvokingArmPoseCheck` (client datagen, `GatherDataEvent.Client`): it initializes `ArmPose` and checks
  that `SPELLCRAFT_EVOKING` is `EvokingArmPose.PROXY`'s value, two-handed, and does not affect the offhand pose. Without
  the check a class literal would not initialize `ArmPose`, and the extension would first apply at the first humanoid
  render.
- Enum-extension faults come in three cases: a syntax error, wrong prefix or missing file in `enumextensions.json` fails
  at launch on every dist; a wrong constructor descriptor fails in `runData` (the class literal in `EvokingArmPose`'s
  initializer loads and transforms `ArmPose`); a wrong `field`, `class` or argument list fails in `ArmPose.<clinit>` at
  the first humanoid render state, which `runData` now reaches through the check.
- Under `/tick freeze` the client particle engine does not tick but players do, so `CastingParticles` skips its hand
  particles when `!level.tickRateManager().runsNormally()`.
- `EntityType.canSpawn(Level)` (`EntityType.java:339`) is false on Peaceful for types not allowed there, and `create`
  returns null then (`:348`): a vex cannot be created on Peaceful.
- `TargetGoal.canContinueToUse` (`TargetGoal.java:37-72`) never re-checks the selector (it checks `canAttack`, the
  team, the follow range and, with `mustSee`, line of sight, then re-sets the target; §21), so an anonymous subclass is
  needed to drop a target that became an ally.
- `ServerPlayer.canHarmPlayer` (`:1047`) adds the `pvp` game rule (`isPvpAllowed`, `:1051`) on top of
  `Player.canHarmPlayer`'s team check (`Player.java:749`).
- A vex's hit names the vex as the causing entity (`Mob.doHurtTarget`, `Mob.java:1415-1422`, damage source from
  `weaponItem.getDamageSource(this)`), so the PvP gate in `ServerPlayer.hurtServer` (`:1036`, which tests `source.getEntity()
  instanceof Player`) never applies to it.
- `ServerLevel.isPositionEntityTicking(BlockPos)` (`ServerLevel.java:1937`) reads the chunk's entity-ticking state
  without loading anything.
- `MinecraftServer.setDifficulty(Difficulty, boolean ignoreLock)` (`MinecraftServer.java:1308`) works from a GameTest and
  needs its own batch, since the difficulty is global.
- `EntityTypes.VEX` is built `.fireImmune()` (`EntityTypes.java:1059`), so a vex never burns: test the Trail flame
  on a vex through `TrailFires.isSpared`, not through fire ticks.
- The GameTest framework encases every structure in a barrier shell from min-1 to max
  (`TestInstanceBlockEntity.java:383-394`, called from `GameTestInfo.java:75`). It blocks `hasLineOfSight` (COLLIDER clip, `LivingEntity.java:3446-3458`), so a
  targeting test keeps both entities inside the box or both outside it.

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

## 21. Evoking II, Part 2: the Bound Vex (entity type, goals, teleports) (Evoking II Part 2 task)

Verified 2026-11-08 against the same unpacked sources (`build/mc-src`, `build/nf-src`).

- `Vex` (`monster/Vex.java`): `public class Vex extends Monster implements TraceableEntity, OwnableEntity` (`:47`), one
  constructor `Vex(EntityType<? extends Vex>, Level)` (`:57`) whose body (`:58-61`) calls `super` and then assigns
  `this.moveControl = new VexMoveControl<>(this)` (`:59`), so a subclass inherits the move control. `createAttributes()`
  (`:98`) is `Monster.createMonsterAttributes()` plus `MAX_HEALTH` 14 and `ATTACK_DAMAGE` 4. `registerGoals` (`:86-96`):
  goalSelector `0 FloatGoal`, `4 VexChargeAttackGoal`, `8 VexRandomMoveGoal`, `9 LookAtPlayerGoal(this, Player.class,
  3.0F, 1.0F)`, `10 LookAtPlayerGoal(this, Mob.class, 8.0F)` (no `RandomLookAroundGoal`); targetSelector `1
  HurtByTargetGoal(this, Raider.class).setAlertOthers()`, `2 VexCopyOwnerTargetGoal`, `3
  NearestAttackableTargetGoal<>(this, Player.class, true)`. **All four inner classes are `private class`**
  (`VexChargeAttackGoal :222`, `VexCopyOwnerTargetGoal :280`, `VexMoveControl :299`, `VexRandomMoveGoal :329`): a
  subclass in another package can neither construct nor extend them; `BoundVex` keeps its own copies of the charge and
  wander goals and skips `super.registerGoals()`. `tick()` (`:74-83`): `noPhysics = true; super.tick(); noPhysics =
  false; setNoGravity(true)`, then the life countdown only while the private `hasLimitedLife` is set, so a vex that
  never gets `setLimitedLife` lives on. `boundOrigin` (`:53`) through `setBoundOrigin`/`getBoundOrigin` (`:145-152`);
  `isCharging`/`setIsCharging` (`:169-175`); `finalizeSpawn` (`:207`) gives the iron sword
  (`populateDefaultEquipmentSlots`, `:217-220`); `getAmbientSound` is `VEX_AMBIENT` (`:187`). `Vex` overrides none of
  `removeWhenFarAway`, `checkDespawn`, `requiresCustomPersistence`; `Mob.removeWhenFarAway(double)` is public and true
  (`Mob.java:702`), `Mob.requiresCustomPersistence()` is public (`:706`, declared in `Mob`, not `Entity`).
  `Mob.RANDOM_SPAWN_BONUS_ID` is `protected static` (`Mob.java:122`); `finalizeSpawn` adds that random `FOLLOW_RANGE`
  modifier (`:1120-1125`), so a subclass removes it by that name without redeclaring it.
- Peaceful: `Mob.checkDespawn` (`Mob.java:711-741`) discards a mob whose type is not `isAllowedInPeaceful()`
  (`:715-716`, `EntityType.java:501`), set by `EntityType.Builder.notInPeaceful()` (`:653`);
  `EntityType.canSpawn(Level)` (`:339`) and `create` (`:348`) refuse on Peaceful for the same flag. Vanilla's `VEX` is
  built (`EntityTypes.java:1056-1066`) `EntityType.Builder.of(Vex::new, MobCategory.MONSTER).fireImmune().sized(0.4F,
  0.8F).eyeHeight(0.51875F) .passengerAttachments(0.7375F).ridingOffset(0.04F).clientTrackingRange(8).notInPeaceful()`
  (no `updateInterval`: the default 3).
- Registration: `DeferredRegister.createEntities(modid)` (`nf-src/.../registries/DeferredRegister.java:184`) →
  `DeferredRegister.Entities.registerEntityType(String, EntityType.EntityFactory<E>, MobCategory,
  UnaryOperator<EntityType.Builder<E>>)` (`:824`), which builds with a `ResourceKey` itself;
  `EntityFactory<T>.create(EntityType<T>, Level)` (`EntityType.java:713-716`). `EntityAttributeCreationEvent` is a
  mod-bus event (`nf-src/.../event/entity/EntityAttributeCreationEvent.java:23`) with `put(EntityType<? extends
  LivingEntity>, AttributeSupplier)` (`:30`); `AttributeSupplier.Builder.build()`.
- Renderer: `EntityRenderersEvent.RegisterRenderers.registerEntityRenderer(EntityType<? extends T>,
  EntityRendererProvider<T>)` (`nf-src/.../client/event/EntityRenderersEvent.java:103`, mod bus); `VexRenderer extends
  MobRenderer<Vex, VexRenderState, VexModel>` (`client/renderer/entity/VexRenderer.java:15`, not final, constructor
  `(EntityRendererProvider.Context)` `:19`), so `registerEntityRenderer(ModEntities.BOUND_VEX.get(), VexRenderer::new)`
  compiles with `T = Vex`. `extractRenderState` copies `isCharging()` into the render state (`:36-39`), and
  `getTextureLocation` picks the charging texture from it. `VexModel` has no arm swing.
- Synced data: `SynchedEntityData.defineId(Class<? extends SyncedDataHolder>, EntityDataSerializer<T>)` (`:30`),
  `EntityDataSerializers.BOOLEAN` (`:89`); `Entity.defineSynchedData(SynchedEntityData.Builder)` (`:428`, `Vex`
  overrides at `:103-106` and must be called through `super`); `Builder.define(accessor, value)`. Save:
  `addAdditionalSaveData(ValueOutput)` / `readAdditionalSaveData(ValueInput)` (`Entity.java:2288-2290`);
  `ValueOutput.putBoolean` (`:17`), `ValueInput.getBooleanOr` (`:29`).
- Interaction: `Mob.interact(Player, InteractionHand, Vec3)` (`Mob.java:1160`) is the public entry (name tag and spawn
  egg first, then `Entity.interact`, then `mobInteract`); `Mob.mobInteract(Player, InteractionHand)` is **protected**
  (`:1214`) and runs on both sides. `InteractionResult` is a sealed interface (`InteractionResult.java:11-16`):
  `SUCCESS` (client-predicted swing), `SUCCESS_SERVER` (the server sends the swing), `CONSUME`, `FAIL`, `PASS`,
  `TRY_WITH_EMPTY_HAND`; `interact` treats a result as handled when `consumesAction()` (`Mob.java:1165,1174`). On the
  client, `Minecraft.startUseItem` (`Minecraft.java:1760-1825`) stops at the first `InteractionResult.Success` and
  otherwise goes on to the next hand, so a client-side `PASS` for the main hand lets the off-hand item be used (a pearl
  thrown, a shield raised). `IronGolem.mobInteract` (`:262-278`) heals `25.0F`, plays `IRON_GOLEM_REPAIR` at pitch `1 +
  (r − r) · 0.2` and returns `SUCCESS`.
- Teleport: `Entity.teleport(TeleportTransition)` (`Entity.java:3177`) returns the entity to use afterwards, or null
  when NeoForge's `EntityTravelToDimensionEvent` is cancelled (`CommonHooks.onTravelToDimension`,
  `nf-src/.../common/CommonHooks.java:841-845`, posted from `Entity.teleport` `:3178` **and** `ServerPlayer.teleport`
  `ServerPlayer.java:1177`, before the move, for **every** teleport, same dimension included: `ServerPlayer.teleport`
  returns early for a same-dimension move at `:1190`, after the post). Same dimension returns `this` (`:3192`);
  **cross-dimension creates a new entity** with `getType().create(newLevel, DIMENSION_TRAVEL)`, calls
  `newEntity.restoreFrom(this)` (save and load: NBT fields and serialized attachments survive, synced data and plain
  fields do not, goals are rebuilt by the constructor) and removes the old one (`:3209-3243`).
  `TeleportTransition(ServerLevel, Vec3 pos, Vec3 speed, float yRot, float xRot, PostTeleportTransition)`
  (`TeleportTransition.java:28`), `TeleportTransition.DO_NOTHING` (`:24`). `PlayerEvent.PlayerChangedDimensionEvent`
  (`nf-src/.../event/entity/player/PlayerEvent.java:517`; game bus) has `getFrom()`, `getTo()` (`:527,531`) and
  `getEntity()`, posted at the end of `ServerPlayer.teleport` (`ServerPlayer.java:1236`) only when the dimension
  changed. `EntityTravelToDimensionEvent` (`nf-src/.../event/entity/EntityTravelToDimensionEvent.java:24`) has
  `getEntity()` and `getDimension()` (`:32`). Entities by UUID: `ServerLevel.getEntities()` (`:1875`, a
  `LevelEntityGetter<Entity>`) `.get(UUID)`.
- Creeper, golem, wolf: `Creeper.getSwellDir()` (`:195`) is the synced swell direction: `-1` by default
  (`defineSynchedData`, `Creeper.java:100`), `1` while `SwellGoal` runs (`SwellGoal.java:43-50`) or once ignited
  (`tick`, `:129-131`); a NoAI creeper stays at `-1` until a test calls `setSwellDir(1)` (`:199`; `ignite()` `:265`).
  The fuse length is the `Fuse` short tag (`:109,118`, default 30). `IronGolem.getAttackAnimationTick()` (`:237`,
  public) counts down from 10 after a hit; `handleEntityEvent((byte) 4)` sets it to 10 on either side (`:224-227`).
  `TamableAnimal.isTame()` (`:115`), `isOrderedToSit()` (`:238`), `isOwnedBy(LivingEntity)` (`:183`), `tame(Player)`
  (`:170`), `setOrderedToSit(boolean)` (`:242`). `Wolf.wantsToAttack(LivingEntity target, LivingEntity owner)`
  (`animal/wolf/Wolf.java:641-651`) is false for creepers, ghasts, armor stands, the owner's own tamed wolves, players
  the owner may not harm (`canHarmPlayer`), tamed horses and tamed animals. `Mob.swing(InteractionHand, SwingAnimation)`
  (`Mob.java:1529`; there is no `swing(InteractionHand)` in 26.3), `SwingAnimation.DEFAULT`
  (`world/item/component/SwingAnimation.java:11-12`). `LivingEntity.heal(float)` (`:1197`), `getMaxHealth()` (`:2083`).
  `SoundEvents.IRON_GOLEM_REPAIR` (`:894`), `VEX_AMBIENT` (`:1673`), `VEX_CHARGE` (`:1674`).
- Looking: `Entity.getLookAngle()` (`Entity.java:2676-2678`) is `calculateViewVector(getXRot(), getYRot())`, the
  **body** yaw; `LivingEntity` does not override it. The **head** vectors are `Entity.getHeadLookAngle()` (`:2684`,
  `getYHeadRot()`) and `getViewVector(float)` (`:1986`, through `getViewYRot`, which `LivingEntity` overrides to the
  head yaw, `:3462`). A golem winding up a swing turns its head toward its target before its body follows, so "the golem
  faces the vex" reads `getHeadLookAngle()`, and a test turns the golem with `setYHeadRot`.
- The goal clock: `Mob.serverAiStep` (`Mob.java:750-764`) runs the full `targetSelector.tick()` and
  `goalSelector.tick()` (the only place a goal's `canUse` is asked) only when `tickCount + getId()` is even (or
  `tickCount <= 1`); on odd values it only ticks the running goals (`tickRunningGoals(false)`). So `canUse` is asked
  every second tick, on a parity set by the entity id, and a `tickCount % 10 == 0` gate never opens for a mob with an
  odd id. A gate on `(tickCount + getId()) % 10 == 0` always lands on a full tick, once every 10 ticks, for any id
  (`BoundVex.scanDue`).
- Goals: `Goal` (`ai/goal/Goal.java`): `canUse()` abstract (`:12`), `canContinueToUse()` defaults to `canUse()` (`:14`),
  `requiresUpdateEveryTick()` false (`:28`), `setFlags(EnumSet<Flag>)` (`:35`), `reducedTickDelay(int)` (`:53`,
  `ceil(ticks / 2)`), `Flag {MOVE, LOOK, JUMP, TARGET}`. `TargetGoal(Mob, boolean mustSee)` (`:26`).
  **`TargetGoal.start()` (`:76-81`) does not set the target**: it only zeroes the reach cache and the unseen counter.
  Vanilla's subclasses set it in their own `start()` (`NearestAttackableTargetGoal.java:74-77`,
  `HurtByTargetGoal.java:60-69`: `mob.setTarget(...)` then `super.start()`), so a custom `TargetGoal` must call
  `mob.setTarget(...)` itself in `start()`. `TargetGoal.stop()` (`:83-87`) sets the target to null.
  `TargetGoal.canContinueToUse()` (`:37-72`) takes `mob.getTarget()` (falling back to the private `targetMob`), drops it
  on `!mob.canAttack(target)`, on a shared team, beyond `FOLLOW_RANGE` (`getFollowDistance()`) and, with `mustSee`,
  after `unseenMemoryTicks` (60) without line of sight, and **ends with `mob.setTarget(target)`**, so it re-sets the
  target on every full tick. A goal that overrides `canContinueToUse` chooses whether to keep those checks: the Bound
  Vex grudge and ally goals (no sight rule) use `BoundVex.keepsTarget` alone and do not call `super`; the patrol goal
  calls `keepsTarget` and then `super` for the sight rule. A `setTarget` override must therefore tell a re-set of the
  same target from a new one. `NearestAttackableTargetGoal(Mob, Class<T>, boolean mustSee,
  TargetingConditions.Selector)` (`:25`) uses a random interval of 10 → `reducedTickDelay` 5 → `canUse` (`:44-51`) goes
  on only when `random.nextInt(5) == 0`; the six-argument constructor `(Mob, Class<T>, int randomInterval, boolean
  mustSee, boolean mustReach, Selector)` (`:33-40`) with `randomInterval` 1 makes the roll always pass (`nextInt(1) ==
  0`). Protected `getTargetSearchArea(double)` (`:53`), `findTarget()` (`:57`), protected fields `targetConditions`,
  `target` (`:16-19`). `TargetingConditions.Selector.test(LivingEntity target, ServerLevel level)` (`:99-100`, **target
  first**). `Mob.goalSelector` and `targetSelector` are `public final` (`Mob.java:135-136`); `GoalSelector.addGoal(int,
  Goal)` (`:28`), `removeAllGoals(Predicate<Goal>)` (`:32-40`, tests the inner `Goal`, not the `WrappedGoal`),
  `getAvailableGoals()` (`:109`, the `WrappedGoal`s: `getGoal()`, `getPriority()`, `isRunning()`). `Mob.getTarget()`
  returns null for a target `canAttack` refuses (a creative or spectator player, an invulnerable mob). `MoveControl<T
  extends Mob>` is generic (`ai/control/MoveControl.java:15`); `Mob.getMoveControl()` returns the raw type
  (`Mob.java:214`), `hasWanted()` (`:32`), `setWantedPosition(double, double, double, double)` (`:40`), `setWait()`
  (`:165`). `Mob.doHurtTarget(ServerLevel, Entity)` (`:1415`), `Mob.getLookControl()` (`:210`),
  `LookControl.setLookAt(double, double, double, float, float)` (`:38`).
- Memories: `LivingEntity.getLastHurtByMob()` (`:638`), `getLastHurtByMobTimestamp()` (`:651`, the victim's `tickCount`
  at the hit), `setLastHurtByMob` (`:668`), `getLastHurtMob()` (`:673`), `getLastHurtMobTimestamp()` (`:677`),
  `setLastHurtMob(Entity)` (`:681`). `LivingEntity.tick` forgets `lastHurtByMob` when it dies or after 100 ticks
  (`:513-519`), but forgets `lastHurtMob` only when it dies (`:509-511`): a "fresh memory" rule needs its own age check.
- Queries: `ServerLevel.getEntities(EntityTypeTest<Entity, T>, Predicate<? super T>)` → `List<? extends T>` (`:941`, the
  whole level); `EntityType<T>` is an `EntityTypeTest<Entity, T>` (`EntityType.java:54`);
  `MinecraftServer.getAllLevels()` → `Iterable<ServerLevel>` (`:1213`); `EntityGetter.getEntitiesOfClass(Class<T>, AABB,
  Predicate<? super T>)` (`:23`) returns entities whose **bounding box intersects** the `AABB`, not whose position is
  inside it. `AABB.contains(double, double, double)` (`AABB.java:264-266`) is half-open (`min <= x < max`). So a
  "position inside the closed guard box" check filters the query's result through `GuardArea.contains`
  (`BoundVex.inGuardArea`). `Enemy` is a marker interface every `Monster` implements (`Monster.java:30`), neutral ones
  (endermen, zombified piglins) and Bound Vexes included. `AABB(Vec3, Vec3)` (`:36`), `inflate(double, double, double)`
  (`:190`); `LevelReader.isEmptyBlock(BlockPos)` (`:90`, air only); `Entity.teleportTo(double, double, double)`
  (`:3477`, same level, keeps the velocity) and `getRandom()` (`:4261`).
- GameTests: `GameTestHelper.spawn(EntityType<E>, BlockPos)` (`:161`) creates with `EntitySpawnReason.STRUCTURE`, calls
  `setPersistenceRequired()` and **not** `finalizeSpawn` (no iron sword) (`GameTestEntityBuilder.java:44-67`);
  `spawn(type, pos, EntitySpawnReason)` (`:181`) does call it. `helper.getEntities(EntityType<T>)` (`:660`). A `Creeper`
  with `setSwellDir(1)` and a large `Fuse` hisses without exploding until the swell counts up to `maxSwell`.
