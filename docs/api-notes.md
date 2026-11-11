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
  0.8F).eyeHeight(0.51875F).passengerAttachments(0.7375F).ridingOffset(0.04F).clientTrackingRange(8).notInPeaceful()`
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
  thrown, a shield raised). `IronGolem.mobInteract` (`:262-278`) heals `25.0F`, plays `IRON_GOLEM_REPAIR` at pitch
  `1 + (r1 - r2) * 0.2` and returns `SUCCESS`.
  Part 3 changed the click rule (§22 and the Part 3 spec §3): sneak + empty hand toggles stay, the plain empty hand
  takes the weapon back, a gear item is given.
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
- The End exit (the credits) and the respawn path: `EndPortalBlock.entityInside` (`EndPortalBlock.java:56-57`) sends a
  player in the End who has not seen the credits through `ServerPlayer.showEndCredits`, **not** through a portal
  teleport. The client's `PERFORM_RESPAWN` then calls `PlayerList.respawn(player, true, CHANGED_DIMENSION)`
  (`ServerGamePacketListenerImpl.java:1993-1996`), the same method a death respawn uses with `false`. That path posts
  neither `EntityTravelToDimensionEvent` nor `PlayerChangedDimensionEvent`. `PlayerList.respawn` (`PlayerList.java:402-482`)
  works out the destination (`:403`), removes the **old** player from its level (`:405`), posts
  `PlayerRespawnPositionEvent` with the old player (`:408`, `EventHooks.firePlayerRespawnPositionEvent` `:929`), builds
  the new player in the event's level (`:411-432`) and posts `PlayerEvent.PlayerRespawnEvent` for it (`:455`,
  `EventHooks.firePlayerRespawnEvent` `:939`). `PlayerRespawnPositionEvent` (`nf-src/.../event/entity/player/`) is
  not cancellable, has `getTeleportTransition()` (the destination, `newLevel()` included) and `isFromEndFight()`
  (`:108`, true exactly when `keepAllPlayerData` is: the End exit); its `getEntity()` is the old player, which after
  `removePlayerImmediately` still returns its old `level()` and position. `PlayerEvent.PlayerRespawnEvent.isEndConquered()`
  (`PlayerEvent.java:506`) is the same flag on the new player, whose level is the destination and whose position is set.
  A listener that must see another listener's change of destination uses `EventPriority.LOWEST`.
- Cross-dimension order (`Entity.teleportCrossDimension`, `Entity.java:3209-3243`): `getType().create(newLevel, ...)`
  (`:3223`), `newEntity.restoreFrom(this)` (`:3229`, a save-and-load: the new copy has **no arrival position yet**, only
  the old one's saved coordinates), `removeAfterChangingDimensions()` (`:3230`), `teleportSetPosition` (`:3231`, the
  arrival position), then `newLevel.addDuringTeleport(newEntity)` (`:3232`), which calls `onAddedToLevel()`
  (`ServerLevel.java:1036`; also called on every chunk load, so a one-shot flag set in `restoreFrom` tells an arrival
  from a load). So "where it arrives" is readable in `onAddedToLevel`, never in `restoreFrom`. A `LivingChangeTargetEvent`
  listener is added with `NeoForge.EVENT_BUS.addListener(Class, Consumer)` and removed with `unregister(consumer)`.

## 22. Evoking II, Part 3: gear, drops, blocking, the armor layer (Evoking II Part 3 task)

Verified 2026-11-09 against the unpacked sources (`build/mc-src`). Paths are under `net/minecraft/` unless noted.

- Interaction: `Mob.interact` (`Mob.java:1160-1183`) runs the name tag and spawn egg first, then
  `Entity.interact`, then `mobInteract`. The client sends the interact packet with
  `player.isShiftKeyDown()` (`MultiPlayerGameMode.java:488-491`); sneaking does not skip an entity
  interaction. `Minecraft.startUseItem` (`Minecraft.java:1775-1789`) returns at the first
  `InteractionResult.Success` from `gameMode.interact`; on anything else it goes on to the item's own
  use and to the off hand.
- Vex move control: `Vex.VexMoveControl.tick` (`Vex.java:304-327`) stops (operation `WAIT`, velocity
  halved) when the wanted position is nearer than `getBoundingBox().getSize()` (the mean of the three
  sides, about 0.53 for a 0.4 x 0.8 vex); otherwise it adds `speedModifier * 0.05` per tick toward it
  (the direction is normalized).
- Follow range: `NearestAttackableTargetGoal` builds its range from `FOLLOW_RANGE` at construction
  (§19) and `TargetGoal.canContinueToUse` reads the attribute live (§21); attributes are loaded by
  `LivingEntity.readAdditionalSaveData` (`LivingEntity.java:826-829`), so a subclass that resets a base
  value does it after `super`.
- Drops: `LivingEntity.dropAllDeathLoot` (`LivingEntity.java:1589-1603`) calls `dropCustomDeathLoot`
  only when `shouldDropLoot` (`:606`, the `mob_drops` game rule, and not a baby). `Mob.dropCustomDeathLoot`
  (`Mob.java:920-947`) drops each slot with a nonzero `dropChances` entry; a slot marked by
  `setGuaranteedDrop` (`Mob.java:621`, chance `2.0F`; `DropChances.isPreserved` is "chance above 1") is
  "preserved": it drops whoever the killer was, undamaged (no durability loss), unless the item has
  `PREVENT_EQUIPMENT_DROP` (Curse of Vanishing). A non-preserved slot drops only when killed by a player,
  with random wear. The drop is `spawnAtLocation(ServerLevel, ItemStack)` (`Entity.java:2296`) and the
  slot is then emptied. `Vex.populateDefaultEquipmentSlots` (`Vex.java:217-220`) sets the main hand's
  chance to 0 at spawn. `drop_chances` is saved (`Mob.java:388-389`).
- Peaceful: `Mob.checkDespawn` (`Mob.java:711-716`) asks NeoForge's `checkMobDespawn` hook first, then
  `discard()`s a mob whose type is not allowed in Peaceful: no death, no drops.
- Armor wear: `LivingEntity.hurtArmor` and `hurtHelmet` are empty (`LivingEntity.java:1957-1961`);
  only `Player`, `Wolf` and `Horse` override them, so a mob's worn armor is never damaged.
- Weapon wear: `Mob.doHurtTarget` (`Mob.java:1415-1436`) reads `getWeaponItem()` (the main hand,
  `LivingEntity.java:2316`), applies its enchantments, and calls `weaponItem.hurtEnemy`. That call costs
  no durability in 26.3: `ItemStack.hurtEnemy` (`ItemStack.java:577`) only runs `Item.hurtEnemy`
  (empty, `Item.java:287`) and awards a stat to a player. The wear is in `ItemStack.postHurtEnemy`
  (`ItemStack.java:591`, `hurtAndBreak(weapon.itemDamagePerAttack())`), which only `Player.attack`
  calls (`Player.itemAttackInteraction`, `Player.java:1153`). So a mob's weapon is not worn by vanilla; `BoundVex.doHurtTarget`'s
  restore is a guard (a modded `hurtEnemy`, a later vanilla change), and the test's control wears a
  sword through `postHurtEnemy`.
- Blocking: `LivingEntity.applyItemBlocking` (`LivingEntity.java:1378-1422`) needs
  `getItemBlockingWith()` (`:3768`; null unless `isUsingItem()`), which a mob never has. Its geometry: no
  block for a piercing arrow; otherwise the flat view vector `calculateViewVector(0, getYHeadRot())`
  against the flattened, normalized vector from `position()` to `source.getSourcePosition()`
  (`DamageSource.java:106`), `angle = acos(dot)`, passed to `BlocksAttacks.resolveBlockedDamage`.
  `DamageTypeTags.BYPASSES_SHIELD` exists (`DamageTypeTags.java:10`). A shield is any stack with
  `DataComponents.BLOCKS_ATTACKS` (`DataComponents.java:213`).
- View vector: `Entity.calculateViewVector(float xRot, float yRot)` is `public static Vec3`
  (`Entity.java:2010`); degrees in, x = `sin(-yRot) * cos(xRot)`, y = `-sin(xRot)`,
  z = `cos(-yRot) * cos(xRot)`.
- Projectiles: `ProjectileUtil.getMobArrow(LivingEntity, ItemStack projectile, float power,
  @Nullable ItemStack firedFromWeapon)` (`ProjectileUtil.java:273`; an `ArrowItem` projectile is used, any
  other item becomes `Items.ARROW`; it calls `setBaseDamageFromMob(power)`, `AbstractArrow.java:712`);
  `AbstractArrow.pickup` is a public field, default `DISALLOWED` (`AbstractArrow.java:71`);
  `ThrownSplashPotion(Level, LivingEntity owner, ItemStack)` (`ThrownSplashPotion.java:27`) and
  `ThrownLingeringPotion(Level, LivingEntity, ItemStack)` (`ThrownLingeringPotion.java:20`).
- Items: `ItemStack.consumeAndReturn(int amount, @Nullable LivingEntity owner)` (`ItemStack.java:1211`) is
  `copyWithCount(amount)` followed by `consume(amount, owner)` (`:1205`), and `consume` shrinks only when
  `owner == null || !owner.hasInfiniteMaterials()`. `LivingEntity.hasInfiniteMaterials` is false
  (`LivingEntity.java:4210`); `Player` returns `abilities.instabuild` (`Player.java:1360`). So for a
  creative player the stack is left alone and the call still returns a copy (a free duplicate); for a
  survival player, or `null`, the stack shrinks. A caller that must not duplicate for a creative player
  checks `hasInfiniteMaterials()` itself. `ItemStack.matches(ItemStack, ItemStack)` is
  `public static boolean` (`ItemStack.java:654`): same instance, or same count and
  `isSameItemSameComponents`; it is not an instance method.
- Dropping: `Player.drop` has no two-argument form; it is `drop(ItemStack, boolean thrownFromHand,
  net.minecraft.util.Prediction)` (`Player.java:2154`, public, fires NeoForge's toss event; `Prediction`
  is `PREDICTED` or `SERVER_ONLY`). `LivingEntity.drop` has the same signature (`LivingEntity.java:803`),
  swings the main hand, and returns null for an empty stack or on the client.
  `LivingEntity.createItemStackToDrop(ItemStack, boolean randomly, boolean thrownFromHand)` (`:3560`) is
  public.
- Attack damage of a stack: `DataComponents.ATTRIBUTE_MODIFIERS` (`DataComponents.java:158`) holds an
  `ItemAttributeModifiers` record (`modifiers()`: a `List<ItemAttributeModifiers.Entry>`), each
  `Entry(Holder<Attribute> attribute, AttributeModifier modifier, EquipmentSlotGroup slot, Display display)`
  (`ItemAttributeModifiers.java:298`). The slot test is `entry.slot().test(EquipmentSlot)`
  (`EquipmentSlotGroup.java:64`; the `MAINHAND` group matches `EquipmentSlot.MAINHAND`, and `ANY` and
  `HAND` match it too). `ItemAttributeModifiers.compute(Holder<Attribute>, double base, EquipmentSlot)`
  (`:87-103`) folds the matching entries (`ADD_VALUE`, `ADD_MULTIPLIED_BASE`, `ADD_MULTIPLIED_TOTAL`) over
  a base value. `AttributeModifier` is `record(Identifier id, double amount, Operation operation)`. A
  vanilla sword's entry is an `ADD_VALUE` bonus on `Attributes.ATTACK_DAMAGE` for the main hand (the
  player's base of 1 is not in it). `ItemStack.forEachModifier(EquipmentSlot, BiConsumer<Holder<Attribute>,
  AttributeModifier>)` (`ItemStack.java:1126`) goes through NeoForge's modifier events and adds enchantment
  modifiers; prefer it over reading the component directly.
- Potion effects of a stack: `DataComponents.POTION_CONTENTS` (`DataComponents.java:253`) holds a
  `PotionContents` record (`potion`: `Optional<Holder<Potion>>`, `customColor`, `customEffects`,
  `customName`). `PotionContents.getAllEffects()` (`PotionContents.java:92`) returns an
  `Iterable<MobEffectInstance>`: the potion's own effects followed by `customEffects`, or just the custom
  ones when there is no potion. `MobEffectInstance.getEffect()` is a `Holder<MobEffect>`;
  `MobEffect.getCategory()` (`MobEffect.java:139`) returns `MobEffectCategory` (`BENEFICIAL`, `HARMFUL`,
  `NEUTRAL`). The factory for a potion stack is `PotionContents.createItemStack(Item, Holder<Potion>)`
  (`PotionContents.java:74`, static; sets `POTION_CONTENTS` on a new stack of the item).
- How mobs launch a projectile: `AbstractSkeleton.performRangedAttack(LivingEntity target, float power)`
  (`AbstractSkeleton.java:160-177`, from `RangedAttackMob`) finds the bow, builds the arrow with
  `getArrow` -> `ProjectileUtil.getMobArrow`, aims at `xd = target.x - x`,
  `yd = target.getY(0.3333) - arrow.getY()`, `zd = target.z - z`, and spawns it with
  `Projectile.spawnProjectileUsingShoot(arrow, serverLevel, projectileStack, xd, yd + horizontalDistance *
  0.2F, zd, 1.6F, rangedAttackUncertainty(serverLevel))`. `Witch.performRangedAttack(LivingEntity, float)`
  (`Witch.java:224`) aims at the target's position plus its motion (`yd = target.getEyeY() - 1.1F -
  getY()`) and spawns `Projectile.spawnProjectileUsingShoot(ThrownSplashPotion::new, serverLevel,
  potionStack, this, xd, yd + dist * 0.2, zd, dist <= 2.0 ? 0.45F : 0.75F,
  rangedAttackUncertainty(serverLevel))`. The two helpers (`Projectile.java:225` and `:241`) are
  `spawnProjectileUsingShoot(ProjectileFactory<T>, ServerLevel, ItemStack, LivingEntity source, double
  targetX, double targetY, double targetZ, float pow, float uncertainty)` (the factory is
  `T create(ServerLevel, LivingEntity, ItemStack)`, so `ThrownSplashPotion::new` fits) and
  `spawnProjectileUsingShoot(T projectile, ServerLevel, ItemStack, double, double, double, float pow,
  float uncertainty)` for an entity already built. Both call `projectile.shoot(x, y, z, pow, uncertainty)`,
  then `serverLevel.addFreshEntity`, then `applyOnProjectileSpawned`. In `performRangedAttack`, `power`
  only feeds the arrow's damage; the launch speed (`pow`) is the separate constant.
  `RangedAttackMob.rangedAttackUncertainty(Level)` is a default method, `14 - difficultyId * 4`
  (`RangedAttackMob.java:9`). The skeleton plays `SKELETON_SHOOT` and the witch `WITCH_THROW` afterwards.
- Sounds: `SoundEvents.ARMOR_EQUIP_GENERIC` is a `Holder<SoundEvent>` (`SoundEvents.java:94`);
  `SoundEvents.SHIELD_BLOCK` is a `Holder.Reference<SoundEvent>` (`SoundEvents.java:1437`). Both are
  holders: call `.value()` where a `SoundEvent` is needed.
- Equip sound: `Mob` does not override `setItemSlot`; `LivingEntity.setItemSlot(EquipmentSlot, ItemStack)`
  (`LivingEntity.java:2375`) calls `onEquipItem` (`:711-735`), which on the server plays
  `getEquipSound(...)` (default `Equippable.equipSound`, `ARMOR_EQUIP_GENERIC` unless the item sets one)
  at the mob when the entity is not silent, not a spectator and past its first tick, the new stack is not
  the same item with the same components, and the stack's `EQUIPPABLE` slot equals the slot being set. So
  armor, and a shield in the off hand (`Items.SHIELD` is `equippableUnswappable(OFFHAND)`), make a sound; a
  sword or bow in the main hand has no `EQUIPPABLE` and is silent. `setItemSlot(slot, stack, true)` skips
  all of this.
- Client: `VexRenderer` (`VexRenderer.java:15-41`) adds one `ItemInHandLayer` (`:21`); `VexRenderState
  extends ArmedEntityRenderState`, which carries both hands' items (`ArmedEntityRenderState.java:18-22`),
  and `VexModel.translateToHand` (`VexModel.java:119-127`) handles the left arm, so an offhand shield is
  drawn with no new code. `HumanoidArmorLayer` (`HumanoidArmorLayer.java:19`) needs a
  `HumanoidRenderState` and cannot sit on a `VexModel`; its `renderArmorPiece` (`:53-67`) shows the
  call: `EquipmentLayerRenderer.renderLayers(LayerType, assetId, model, state, stack, poseStack,
  collector, light, outlineColor)` (`EquipmentLayerRenderer.java:41`), layer type `HUMANOID_LEGGINGS`
  for `LEGS` and `HUMANOID` otherwise (`HUMANOID_BABY` for a baby). Armor models:
  `ArmorModelSet.bake(locations, modelSet, factory)` (`ArmorModelSet.java:37`),
  `EntityRendererProvider.Context.getEquipmentRenderer()` (`EntityRendererProvider.java:95`).
- Armor in the render state: `HumanoidMobRenderer.extractHumanoidRenderState`
  (`HumanoidMobRenderer.java:57-86`) sets `state.headEquipment`, `chestEquipment`, `legsEquipment` and
  `feetEquipment` (fields of `HumanoidRenderState`, `HumanoidRenderState.java:24`) to
  `getEquipmentIfRenderable(entity, slot)` (`:88-91`): `entity.getItemBySlot(slot).copy()` when
  `HumanoidArmorLayer.shouldRender(stack, slot)` (`:37`, public static; the stack has `EQUIPPABLE` with an
  `assetId` and the same slot), else `ItemStack.EMPTY`. A custom renderer for a non-humanoid state (the
  Bound Vex) does the same four reads on its own render state.
- Damage gate in tests: `Entity.invulnerableTime` is private (`Entity.java:260`; `setInvulnerableTime`
  `:4346` and `isTemporarilyInvulnerable` `:3150` are the only doors). The post-hit gate that
  `LivingEntity.hurtServer` tests (`LivingEntity.java:1272`, `> 10` unless the source bypasses the
  cooldown) is the public field `LivingEntity.damageCooldownTime` (`:242`, set after a hit to the damage
  container's post-attack invulnerability ticks, 20 by default, `:1288-1292`; counted down in
  `baseTick` `:495` except for a `ServerPlayer`; §20 has the same field). A test that hits the same mob
  twice in one tick sets that field to 0 between the hits.
- Silent equipment: `LivingEntity.setItemSlot(EquipmentSlot, ItemStack, boolean insideTransaction)`
  (`LivingEntity.java:2380`) is NeoForge's addition; with `true` it only stores the stack and skips
  `onEquipItem` (no sound, no `EQUIP` game event). The two-argument `setItemSlot` is the `false` form.
  `onEquipItem` (`:711-735`) also does nothing for a new stack that is the same item with the same
  components as the old one (`ItemStack.isSameItemSameComponents`), so swapping an identical helmet is
  silent unless the caller plays its own sound.
- A stack's attribute modifiers reach a mob on its next tick, not at `setItemSlot`:
  `LivingEntity.detectEquipmentUpdates` (`LivingEntity.java:3036`, called from the tick at `:2888`)
  compares the slots with `lastEquipmentItems` and applies the new stack's `forEachModifier` as
  transient modifiers. A test that swaps a sword and measures the next hit waits a few ticks.
- `runData` and renderers: the headless client-dist boot of `./gradlew runData` scans and subscribes
  `@SubscribeEvent` listeners for `EntityRenderersEvent.RegisterRenderers` (the log shows
  `BoundVexRenderers.onRegisterRenderers` subscribed to the mod bus) but does not fire the event, so no
  entity renderer and no render layer is constructed. Observed from the `runData` log and the Part 3
  armor layer's build (it compiled and `runData` passed with the layer unproven); the layer's first run is
  the playtest.
- Error Prone `this-escape` (`-Xlint:all -Werror`): a renderer that adds a layer in its constructor
  (`addLayer(new BoundVexArmorLayer(this, context))`) passes `this` out of a constructor of a
  non-final class and fails the build (Part 3 Task 7's as-built note). `BoundVexRenderer` is `final`,
  which is the fix; no suppression is needed.

## 23. Evoking II, Part 4: cure and bribes (Evoking II Part 4 task)

Verified 2026-11-09 against the unpacked sources (`build/mc-src`, `build/nf-src`). Paths are under `net/minecraft/`
(vanilla) or `net/neoforged/neoforge/` (NeoForge) unless noted. Where this section differs from the Part 4 plan or the
Parts 4-5 spec, this section wins (the list is at the end).

- `ZombieVillager` (`world/entity/monster/zombie/ZombieVillager.java`, package `...entity.monster.zombie`):
  `extends Zombie implements VillagerDataHolder` (`:61`). `startConverting(@Nullable UUID player, int time)` was
  `private void` (`:196`); the access transformer makes it `public void`, and the regenerated patched sources show
  `public void startConverting(@Nullable UUID player, int time)` at `:196`. It stores the starter and
  `villagerConversionTime`, sets `DATA_CONVERTING_ID` (synced), **removes weakness**
  (`removeEffect(MobEffects.WEAKNESS)`, `:200`), adds Strength for `time` ticks with amplifier
  `Math.min(difficultyId - 1, 0)` (so 0 on Easy, Normal and Hard, `:201`) and broadcasts entity event 16 (`:202`, the
  cure sound on clients, `handleEntityEvent` `:211-229`). `isConverting()` (`:192`) is public and reads the synced
  flag. `setVillagerConversionTime(int)` (`:277`) is public (`@VisibleForTesting`): it only sets the countdown, no
  event, no sync.
- Countdown: `ZombieVillager.tick` (`:150-161`) runs on the server when alive and converting: it subtracts
  `getConversionProgress()` (1, plus 0.3-chance bonuses from `SPEEDS_UP_ZOMBIE_VILLAGER_CURING` blocks within 4
  blocks, rolled 1% of ticks, `:281-304`), and at `<= 0` asks NeoForge's `EventHooks.canLivingConvert` (a cancellable
  `LivingConversionEvent.Pre`, `EventHooks.java:858-860`) before `finishConversion`. **`tick()` is not gated by
  `NoAI`**: the only NoAI gate is `isEffectiveAi()` (`Mob.java:1338`), tested by `LivingEntity.aiStep` for
  `serverAiStep` (`LivingEntity.java:3181`: goals, targets, navigation, controls) and for `travel` (`:3235`). So a
  NoAI zombie villager keeps counting down, does not move, has no goals and never attacks (the attack is a goal,
  `Zombie.addBehaviourGoals` `:131-141`); it still burns in daylight (`Mob.aiStep` `:484` `burnUndead`, not gated),
  and `removeWhenFarAway` is `!isConverting() && villagerXp == 0` (`:188`), so a frozen one that is not converting
  yet can despawn.
- `finishConversion(ServerLevel)` (`:231-274`) calls `convertTo(EntityTypes.VILLAGER,
  ConversionParams.single(this, false, false), lambda)`. In the lambda: gossips and offers are copied, the starter is
  looked up with `level.getPlayerByUUID(conversionStarter)` (`:259`, searches `level.players()`), and **only if that
  is a `ServerPlayer` in this level** does the cure trigger the advancement and
  `level.onReputationEvent(ReputationEventType.ZOMBIE_VILLAGER_CURED, player, villager)` (`:260-263`); then Nausea 200
  on the villager (`:266`) and `EventHooks.onLivingConvert(this, villager)` last (`:271`). A starter who is offline,
  dead or in another level gets nothing (the conversion still happens). `ServerLevel.onReputationEvent` (`:1707`)
  calls `Villager.onReputationEventFrom` (`Villager.java:883`): `MAJOR_POSITIVE` +20 and `MINOR_POSITIVE` +25 for the
  starter's UUID. A test reads it with `villager.getPlayerReputation(Player)` (`Villager.java:669`, public, the sum
  over all gossip types) or `villager.getGossips().getReputation(UUID, Predicate<GossipType>)`
  (`GossipContainer.java:109`; `getGossips()` public `:915`). Weights: `GossipType` `MAJOR_POSITIVE` 5,
  `MINOR_POSITIVE` 1 (`GossipType.java:9-10`), so a fresh cure is worth 125 (before the per-type caps). A mock player
  from `MockPlayers.inLevel` is a real `ServerPlayer` in `level.players()` (`placeNewPlayer`), so it qualifies.
- `ZombieVillager.mobInteract` (`:164-180`) with a golden apple: if the zombie villager has weakness it consumes one
  apple (`itemStack.consume(1, player)`), calls `startConverting(player.getUUID(), random.nextInt(2401) + 3600)` on
  the server and returns `SUCCESS_SERVER`; without weakness it returns `CONSUME` and does nothing. (So the vex's
  apple is a prop; `startConverting` is the real work.)
- Conversion: `Mob.convertTo(type, params, spawnReason, AfterConversion)` (`Mob.java:1263-1287`) creates the new mob,
  runs `params.type().convert(this, newMob, params)`, then the lambda (which posts the Post event), then
  `serverLevel.addFreshEntity(newMob)`, then `discard()`s the old mob (`SINGLE` discards, `ConversionType.java:15`,
  `:88`). So **inside `LivingConversionEvent.Post` the old mob is still alive and in the level, and the new mob is
  fully set up but not yet added** (no `EntityJoinLevelEvent` yet). `ConversionType.convertCommon` copies `NoAI`
  (`ConversionType.java:122`), persistence, silence, no-gravity, tags, name and custom data, and active effects
  (`:97-99`; the villager therefore also gets the zombie villager's Strength), but **no data attachments**.
- `LivingConversionEvent` (`event/entity/living/LivingConversionEvent.java:13`; game bus; extends `LivingEvent`):
  `Pre` (`:29`, cancellable, `getOutcome()` = the target `EntityType`, `setConversionTimer(int)`); `Post` (`:68`, not
  cancellable): `getEntity()` is the **old** mob (`LivingEvent.getEntity()` `:30`), `getOutcome()` (`:82`) the
  **new** `LivingEntity`. The old mob's attachments are readable (`getExistingData`, `hasData`). NeoForge itself
  listens (`AttachmentInternals.onLivingConvert`, `attachment/AttachmentInternals.java:61-64`) and runs
  `outcome.copyAttachmentsFrom(old, true)`, where `true` means *death* semantics: only attachment types built with
  `copyOnDeath()` are copied (`:52-53`). Our `cure`, `bribed` and `bribe_tried` types are not `copyOnDeath`, so the
  **new villager has no cure mark**, and a handler that must clear `NoAI` reads the mark from the old mob and calls
  `setNoAi(false)` on the outcome (a `LivingEntity`: cast to `Mob` first).
- `EntityTickEvent` (`event/tick/EntityTickEvent.java:18`; game bus): `Pre` (cancellable) and `Post` (`:51`,
  `getEntity()` from `EntityEvent`); both fire on server and client. **They are posted from
  `ServerLevel.tickNonPassenger` (`ServerLevel.java:861-866`: `commonTick()` and `tick()` between them) and
  `Entity.rideTick` (`Entity.java:2452-2458`), not from `LivingEntity.tick`** (the Javadoc of
  `EventHooks.java:952-967` says "tail of `LivingEntity#tick()`"; the call sites are the two above). The `Post` for a
  zombie villager therefore fires after the countdown in `ZombieVillager.tick`, in the same tick; on the tick the
  conversion finishes, the old mob has already been discarded when `Post` fires (a handler must test `isRemoved()`).
- `Mob.setNoAi(boolean)` (`Mob.java:1342`) flips bit 0 of the synced `DATA_MOB_FLAGS_ID`; `isNoAi()` (`:1357`); saved
  as the `NoAI` byte only when set (`:404-405`, read `:427`).
- Vanilla `Vex` (`monster/Vex.java`): `getOwner()` is `@Nullable LivingEntity` (`:136`, from `OwnableEntity`, resolved
  through `owner`, an `EntityReference<LivingEntity>`, `:52,141`), `setOwner(Mob)` (`:177`). `Vex` does not override
  `setTarget`; `Mob.setTarget(@Nullable LivingEntity)` (`Mob.java:257`) filters through `asValidTarget` (`:245`: null
  for a creative or spectator player or a `canAttack` refusal), then posts NeoForge's `LivingChangeTargetEvent` and
  stores the event's new target unless canceled. `setTarget(null)` is therefore accepted unless a listener cancels
  it. `getTarget()` (`:237`) applies the same `asValidTarget`; `getTargetUnchecked()` (`:241`) is the raw field. A
  **zombie villager's** own goals (inherited from `Zombie.addBehaviourGoals`, `:131-141`): target goals
  `HurtByTargetGoal` (1), `NearestAttackableTargetGoal` for `Player` (2, must see), `AbstractVillager` (3),
  `IronGolem` (3), `Turtle` (5). Vanilla vex goals: §21. `GoalSelector.removeAllGoals(Predicate<Goal>)`
  (`GoalSelector.java:32-40`) **stops running matches first** and then removes them; `removeGoal(Goal)` (`:42`) wraps
  it. `Vex.tick` keeps working with no goals: its `VexMoveControl` (§22) still moves it toward a wanted position, and
  the limited-life countdown (§21) is independent of goals.
- Potion stack with no effects: `PotionContents` is `record(Optional<Holder<Potion>> potion, Optional<Integer>
  customColor, List<MobEffectInstance> customEffects, Optional<String> customName)` (`PotionContents.java:42`), so the
  contents are `new PotionContents(Optional.empty(), Optional.of(color), List.of(), Optional.empty())` (canonical
  constructor, public). The one-argument constructor (`:70`) and `createItemStack(Item, Holder<Potion>)` (`:74`) only
  take a potion. Build the stack as `new ItemStack(Items.SPLASH_POTION)` (`Items.java:2432`) and
  `stack.set(DataComponents.POTION_CONTENTS, contents)`. `hasEffects()` (`:155`) is false for it. Weakness's color is
  the `MobEffect`'s color: `MobEffects.WEAKNESS` is `new MobEffect(MobEffectCategory.HARMFUL, 4738376)`
  (`MobEffects.java:75-79`), i.e. `0x484D48`, readable as `MobEffects.WEAKNESS.value().getColor()`
  (`MobEffect.java:143`).
- A thrown splash potion with such a stack: `ThrownSplashPotion` is in package
  `net.minecraft.world.entity.projectile.throwableitemprojectile` (`ThrownSplashPotion.java:22`; constructors
  `(Level, LivingEntity, ItemStack)` `:27` and `(Level, double, double, double, ItemStack)` `:31`); spawn it the way
  §22 shows (`Projectile.spawnProjectileUsingShoot`, `Projectile.java:225,241`). `AbstractThrownPotion.onHit`
  (`:68-92`) calls `onHitAsPotion` only `if (potion.hasEffects())` (`:74`), so nothing is applied to anyone; it then
  posts level event 2002 (the splash particles) with `potion.getColor()` (`:84`), which is the custom color, plus the
  glass-break sound (event 1053) unless the entity is silent, and discards itself. A splash potion with no effects and
  a custom color therefore splashes in that color and weakens nobody.
- Particles and sounds: `ItemParticleOption` (`core/particles/ItemParticleOption.java:9`) has two public
  constructors, `(ParticleType<ItemParticleOption>, Item)` (`:21`) and `(ParticleType<ItemParticleOption>,
  ItemStackTemplate)` (`:25`); **it takes no `ItemStack`** (26.3 uses `ItemStackTemplate`, constructors `(Item)`
  `ItemStackTemplate.java:40` and `(Holder<Item>)` `:58`). The type is `ParticleTypes.ITEM` (`ParticleTypes.java:93`).
  `ParticleTypes.HAPPY_VILLAGER` (`:87`) and `CLOUD` (`:33`) are `SimpleParticleType`.
  `ServerLevel.sendParticles(T particle, double x, double y, double z, int count, double xDist, double yDist, double
  zDist, double speed)` (`ServerLevel.java:1355`; `T extends ParticleOptions`; returns the number of players sent
  to). Sounds: `SoundEvents.GENERIC_EAT` is a **`Holder.Reference<SoundEvent>`** (`SoundEvents.java:697`),
  `SoundEvents.VILLAGER_AMBIENT` a plain `SoundEvent` (`:1677`); `Level.playSound(@Nullable Entity except, Entity
  source, SoundEvent, SoundSource, float, float)` (`Level.java:442`) and the `Holder<SoundEvent>` overload (`:446`)
  both exist, so pass `GENERIC_EAT` as is to the second and use `.value()` for the first. `Items.GOLDEN_APPLE`
  (`Items.java:1798`), `Items.EMERALD` (`:1649`).
- `EntityJoinLevelEvent` (`event/entity/EntityJoinLevelEvent.java:30`; game bus, cancellable, both sides):
  `loadedFromDisk()` (`:55`) is **true only for entities read back from a chunk**
  (`PersistentEntitySectionManager.processPendingLoads` `:249-253` and `addLegacyChunkEntities` `:117-119`); it is
  **false** for `addFreshEntity` (`ServerLevel.addEntity` `:1030-1035`, then `addNewEntity`
  `PersistentEntitySectionManager.java:70-72`), world-gen entities (`:124-126`) and a cross-dimension arrival
  (`addDuringTeleport`, §21). At the event, a loaded entity's save data (including attachments, `Entity.java:2246`)
  has already been read and its goals already exist (`registerGoals` runs in the `Mob` constructor), so
  `hasData(BRIBED)` is readable and goals can be stripped then. Because a cross-dimension arrival is a save-and-load
  copy that reports `loadedFromDisk() == false`, the bribed vex's handler keys on the mark, not on
  `loadedFromDisk()`.
- Line of sight: `hasLineOfSight` is a **`LivingEntity`** method, not `Entity`'s: `hasLineOfSight(Entity target)`
  (`LivingEntity.java:3445`) clips a `COLLIDER` ray from this entity's eye to the target's x/z at the target's eye
  height (`target.getEyeY()`), false across levels or beyond 128 blocks; it ignores where the viewer is looking. A
  `Player` is a `LivingEntity`, so `player.hasLineOfSight(vex)` is the geometric "nothing solid between us" test;
  "none of those players has line of sight" (spec §3.2) is that test, not a view-cone test. `Sensing.hasLineOfSight`
  (`ai/sensing/Sensing.java:24`) is a per-mob cache of the same call.
- Data attachments (NeoForge): `AttachmentType.builder(Supplier<T>)` (`attachment/AttachmentType.java:109`),
  `Builder.serialize(MapCodec<T>)` (`:198`; the entity saves it under the attachments key, `Entity.java:2176`, and
  `deserializeAttachments` restores it on load, `:2246`), `copyOnDeath()` (`:237`, requires a serializer). On an
  `Entity` (an `IAttachmentHolder`): `hasData(AttachmentType)` (`IAttachmentHolder.java:24`), `getData` (`:39`,
  **stores and returns the default when absent**), `getExistingData(AttachmentType)` returning `Optional<T>` (`:55`),
  `getExistingDataOrNull` (`:72`), `setData(type, value)` (`:87`, returns the previous value or null),
  `removeData(type)` (`:103`); each also has a `Supplier<AttachmentType<T>>` overload, so a `DeferredHolder` passes
  directly. Saved with the entity; copied to a converted mob only for `copyOnDeath` types (see Conversion above);
  `restoreFrom` (cross-dimension) keeps serializable ones (§21).
- Access transformer, the line added to `accesstransformer.cfg`:
  `public net.minecraft.world.entity.monster.zombie.ZombieVillager startConverting(Ljava/util/UUID;I)V`
  (descriptor from `startConverting(@Nullable UUID, int)` returning `void`). Proven two ways after `./gradlew build`
  re-ran `transformSources`: the regenerated `build/moddev/artifacts/minecraft-patched-26.3.0.23-beta-sources.jar`
  shows `public void startConverting(@Nullable UUID player, int time)` at `ZombieVillager.java:196`, and a scratch
  class in `src/main/java` calling `zombieVillager.startConverting(uuid, 400)` compiled (`./gradlew compileJava`,
  exit 0) and was then deleted.
- GameTest baseline on the Task 1 tree: `All 304 required tests passed` (also in the plan's Resume State).

Found while building (verified against the sources or the code):

- `GameTestHelper.onEachTick(Runnable)` (`GameTestHelper.java:1150`) calls `testInfo.setRunAtTickTime(i, action::run)`
  for every tick from now to the timeout, so it puts one entry per remaining tick into the test's
  `Object2LongOpenHashMap<Runnable>` (`GameTestInfo.java:38,172`), each keyed by a fresh lambda. The tick loop walks
  that map with an iterator while the entries run and removes each with `it.remove()` (`GameTestInfo.java:122-134`).
  A test that also schedules a delayed action (`runAfterDelay`, `runAtTickTime`: the padded wrapper does) puts into
  the same map from inside an entry that is running; that crashed the GameTest server in the map's iterator. It is
  unsafe whenever a test also schedules delayed actions. The replacement is `BoundVexTestSupport.everyTick`, a
  parallel `startSequence().thenExecuteFor(span, action)`, which adds no entries to the map.
- `GameTestSequence.thenWaitUntil(long expectedDelay, Runnable)` (`GameTestSequence.java:24`) is not "wait up to N
  ticks": when the assertion finally passes, `tick` compares the ticks since the previous event with `expectedDelay`
  and fails the test with `test.error.sequence.invalid_tick` unless they are equal (`GameTestSequence.java:111-114`).
  `thenWaitAtLeast` is the minimum-delay form; the plain `thenWaitUntil(Runnable)` has no delay check.
- The vex's move control (`Vex.VexMoveControl.tick`, `Vex.java:299-327`) accelerates toward the wanted point
  (`delta.scale(speed * 0.05 / length)` added to the velocity each tick) and switches to `WAIT` only when the distance
  is under `getBoundingBox().getSize()` (about 0.5 blocks for a vex), then halves its velocity. A vex ordered to the
  zombie villager's head therefore ends inside the head, with momentum. To hold a distance, order it to a point short
  of the target, or call `getMoveControl().setWait()` and zero the velocity once it is near enough.
- A goal's `canContinueToUse` is asked only on the selector's full pass, which `Mob.serverAiStep` runs only when
  `tickCount + getId()` is even (§21, "The goal clock"); on the other ticks only `tickRunningGoals(false)` runs, which
  calls `tick()` on goals with `requiresUpdateEveryTick()` (`Mob.java` around `:750-763`, `GoalSelector.java:96-107`).
  So a goal whose own work finished on an odd tick gets one more `tick()` before `canContinueToUse` can end it; a
  `tick()` that has a side effect (conjuring an item) must guard on its own finished flag.
- In one full pass `GoalSelector.tick` (`GoalSelector.java:66-94`) first stops every running goal whose
  `canContinueToUse` is false or whose flags are disabled, then asks `canUse` of every goal that is not running,
  including the one just stopped, in the same pass; only then does it tick the running goals. A goal that aborts
  because the approach timed out can therefore be started again at once by its own `canUse`; that is why the cure and
  the bribe keep a retry delay (`RETRY_DELAY`).
- `LivingChangeTargetEvent` canceled keeps the **old** target: `Mob.setTarget` stores the event's new target only
  `if (!changeTargetEvent.isCanceled())` (`Mob.java:259-262`). Cancelling a change to a new target does not clear an
  existing one: an iron golem that already targets a zombie villager keeps hitting it after the cure mark appears, so
  the claim must call `golem.setTarget(null)` itself (a null target is not canceled by `GolemMercy`, whose check is
  `target != null && ...`), with the mark set first so that the golem's target goals cannot hand the same target
  straight back.

Where the sources differ from the plan or the spec (the sources win):

- `EntityTickEvent.Post` is posted from `ServerLevel.tickNonPassenger` and `Entity.rideTick`, not at the tail of
  `LivingEntity.tick` as `EventHooks`' Javadoc says; the same-tick behavior the spec needs holds.
- `hasLineOfSight` is declared on `LivingEntity`, not `Entity`, and is a geometric clip, not a "looking at" test.
- `ItemParticleOption` takes an `Item` or an `ItemStackTemplate`, never an `ItemStack`.
- `SoundEvents.GENERIC_EAT` is a `Holder.Reference<SoundEvent>`; `VILLAGER_AMBIENT` is a `SoundEvent`.
- `ThrownSplashPotion` lives in `projectile.throwableitemprojectile`.
- NeoForge's own `LivingConversionEvent.Post` listener copies only `copyOnDeath` attachments, so the new villager has
  no cure mark (as the spec assumes) and the old mob is readable inside `Post` (it is discarded after).
- A frozen zombie villager still burns in sunlight and can despawn while not converting; the spec covers the sunlight
  (playtest item), the despawn is new and harmless (the 300-tick release rule never runs on a removed mob).

## 24. Evoking II Part 5 (world helpers) (Evoking II Part 5, Task 1)

Verified against `build/mc-src` (vanilla with NeoForge patches) and `build/nf-src`; paths are under
`net/minecraft/` and `net/neoforged/neoforge/` respectively.

- **Velocity sync.** `Entity.syncVelocity` is a public boolean field (`world/entity/Entity.java:284`); `hurtMarked` does
  not exist. `ServerEntity.sendChanges` sends `ClientboundSetEntityMotionPacket` to tracking players *and the entity's
  own client* and clears the flag (`server/level/ServerEntity.java:209-212`). Setting the velocity alone sends nothing.
- **What a player is doing.** A `ServerPlayer`'s `getDeltaMovement()` is not the client's velocity: the client moves
  itself. `ServerPlayer.getKnownMovement()` (`server/level/ServerPlayer.java:2382`) is the last movement the client
  reported (`ServerGamePacketListenerImpl.handleMovePlayer` → `setKnownMovement`,
  `ServerGamePacketListenerImpl.java:2315`). A push reads the known movement, adds to it, and sends the sum.
- **Fluids on a mock player.** `Entity.isInLava()` is `!firstTick && fluidInteraction.isInFluid(LAVA_TYPE)`
  (`Entity.java:1782-1783`); `isUnderWater()` is `wasEyeInWater && isInWater()` (`Entity.java:1656-1657`).
  `baseTick()` (public, `Entity.java:538`) fills the fluid state through `updateFluidInteraction()` (protected,
  `:559`, `:1688`), sets `wasEyeInWater` from the state *before* that update (`:558`) and clears `firstTick` last
  (`:588`). So `soak` calls `baseTick()` twice, and both `isInLava` and `isUnderWater` need the second call. A GameTest
  mock player is never ticked. `LivingEntity.baseTick` also burns and drowns: tests call
  `setPermanentlyInvulnerable(true)` first (`Entity.setInvulnerable` does not exist; `Entity.java:3158`). Verified in
  `bound_vex_world_setup_fluids`: lava, water and air each give exactly the expected answer after `soak`.
- **`ProjectileImpactEvent`** (`event/entity/ProjectileImpactEvent.java`, cancellable; `getProjectile()`,
  `getRayTraceResult()`). Vanilla posts it from
  `AbstractHurtingProjectile.tick()` (the cancel behavior below is that class's; arrows, throwables and shulker bullets
  also post it, each with its own handling) through
  `EventHooks.onProjectileImpact(Projectile, HitResult)` (`event/EventHooks.java:704`; call in
  `AbstractHurtingProjectile.java:74`; true = canceled). Canceled: the projectile is not discarded, moves by its
  velocity this tick and tests again next tick.
- **`Projectile.deflect`** (`world/entity/projectile/Projectile.java:301-318`): two overloads,
  `deflect(ProjectileDeflection, @Nullable Entity deflector, @Nullable EntityReference<Entity> newOwner,
  boolean byAttack, Vec3 power)` and the same with `double power` (which builds `new Vec3(p, p, p)`). It runs the
  deflection, sets the owner, calls `onDeflection(byAttack)`; always true. `ProjectileDeflection` is a functional
  interface `(projectile, entity, random, Vec3 power)`. `AbstractHurtingProjectile.onDeflection(true)` sets
  `accelerationPower = 0.1` (`:183-189`). `REVERSE` halves the speed; `AIM_DEFLECT` aims along the deflector's look
  (`ProjectileDeflection.java:11-21`).
- **Ghast one-hit rule.** `monster/Ghast.isReflectedFireball` (`Ghast.java:76-78`): the direct entity is a
  `LargeFireball` and the causing entity a `Player`; then `hurtServer` deals 1000 (`:100-104`). (The class is
  `world/entity/monster/Ghast`, not in a `ghast` package.)
- **`ExplosionKnockbackEvent`** (`event/level/ExplosionKnockbackEvent.java`, not cancellable): `getAffectedEntity()`,
  `getKnockbackVelocity()`, `setKnockbackVelocity(Vec3)`, `getAffectedBlocks()`, and `getExplosion()` from
  `ExplosionEvent` (a `ServerExplosion` in `world/level/ServerExplosion.java`: `getDirectSourceEntity()`,
  `getIndirectSourceEntity()`, `getDamageSource()`). Posted by `EventHooks.getExplosionKnockback`
  (`EventHooks.java:630`) after the damage, before `pushFromExplosion` (`ServerExplosion.java:207-208`).
- **`EndCrystal.hurtServer`** (`world/entity/boss/enderdragon/EndCrystal.java:89-109`): returns false when
  `isInvulnerableToBase(source)` (so invulnerable crystals ignore it) or when the attacker is an `EnderDragon`;
  otherwise removes the crystal and, unless the source is itself an explosion (`IS_EXPLOSION`), explodes with power 6,
  the crystal as the explosion's source, interaction `BLOCK`, and `damageSources().explosion(crystal, attacker)` as the
  damage source (direct entity = the crystal, causing entity = the attacker).
- **`EnderDragonFight`** (not `EndDragonFight`; `world/level/dimension/end/EnderDragonFight.java`):
  `ServerLevel.getDragonFight()` (`ServerLevel.java:1824`, null outside the End),
  `EnderDragonFight.dragonUUID()` (`EnderDragonFight.java:595`, nullable), then `level.getEntity(UUID)`.
- **`Consumable.onConsume(Level, LivingEntity, ItemStack)`** (`world/item/component/Consumable.java:77-90`): sound and
  particles, the item-used statistic and the consume-item trigger for a `ServerPlayer`, the `ConsumableListener`
  components, the consume effects (server side), a game event, then `stack.consume(1, user)`. The enchanted golden
  apple (`Consumables.ENCHANTED_GOLDEN_APPLE`, `Consumables.java:26-37`): Regeneration amplifier 1 for 400 ticks,
  Resistance 6000, Fire Resistance 6000, Absorption amplifier 3 (IV) for 2400.
- **Height map.** `Level.getHeight(Heightmap.Types, x, z)` (`world/level/Level.java:349-357`) is the chunk's height
  plus one, the first free Y above the top block; an unloaded column gives `getMinY()`. An empty loaded column gives
  `getMinY()` too.
- **`Level.isDarkOutside()`** (`Level.java:389-395`): `!hasFixedTime() && !isBrightOutside()`, with
  `isBrightOutside()` = `!hasFixedTime() && skyDarken < 4`. So false in a fixed-time dimension (Nether, End).
- **Goal priorities.** A negative priority is legal. A goal starts only if every current holder of its flags is
  interruptable and has a strictly higher priority number; the holders are stopped (`GoalSelector.java:66-94`).
- **Clip.** `new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)`
  (`world/level/ClipContext.java:28`); a miss has `HitResult.Type.MISS`.
- **The day and night pins.** `GameTestServer` turns off `SPAWN_MOBS` and `ADVANCE_WEATHER` only
  (`gametest/framework/GameTestServer.java:96-97`), so the clock keeps running during a test.
  `TestEnvironmentDefinition.ClockTime(Holder<WorldClock>, int)` needs the clock's holder when it is built, but
  `RegisterGameTestsEvent.registerEnvironment(Identifier, TestEnvironmentDefinition<?>)` runs before any registry is
  at hand and `ServerClockManager.getInstance` rejects any holder but the registry's own
  (`world/clock/ServerClockManager.java:75-82`). So `gametest/OverworldTime(int time)` implements
  `TestEnvironmentDefinition<Long>` (`setup(ServerLevel)`, `teardown(ServerLevel, Long)`, `codec()`), looks the holder
  up with `level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD)` at setup,
  and calls `server.clockManager().setTotalTicks(clock, time)` as `ClockTime.setup` does. Every environment gets
  one: 18000 for `NIGHT`, 6000 for the rest. Proven by `bound_vex_world_setup_night` and `bound_vex_world_setup_day`.
- **Test environments are sent to every joining client.** `Registries.TEST_ENVIRONMENT` is in
  `RegistryDataLoader.SYNCHRONIZED_REGISTRIES`, and `SynchronizeRegistriesTask.sendRegistries` encodes each entry
  with `TestEnvironmentDefinition.DIRECT_CODEC`, which dispatches on the definition's type in
  `BuiltInRegistries.TEST_ENVIRONMENT_DEFINITION_TYPE`. A definition whose codec is not registered there fails with
  "Unregistered holder", the server swallows the error, and the client waits on "Loading terrain" forever: every world
  join broke that way while `OverworldTime` had an unregistered `MapCodec.unit` (2026-11-10). So a custom definition
  needs a real codec registered as its type (`SpellcraftGameTests.ENVIRONMENT_TYPES`, `spellcraft:overworld_time`).
  Neither the GameTest server nor `runData` nor a client that only reaches the title screen sends registries; the
  GameTest `bound_vex_world_setup_registries_reach_a_client` packs and decodes them as a join does.
- **The GameTest world is flat.** Every dimension of the GameTest server's "Test Level" has a solid floor at
  `y = 0..3` (bedrock, then three layers of stone of the dimension), even far from the origin in the End. So "the End
  with nothing to land on" needs the floor removed: `BoundVexWorldTestSupport.endVoid` clears every block below the
  slot's Y in its forced chunks (found through the height map), with `Block.UPDATE_CLIENTS` only, and puts them back.
  Without that, `getHeight` in the End void gives 4, not `getMinY()`.
- **Private slots.** A slot is a grid point 200 blocks from its neighbors; End slots are 450..850 blocks from the
  origin at `y = 80`, Nether slots start at `x = 1000` at `y = 100`. A forced chunk in a slot is entity-ticking within
  the setup test (a Bound Vex there has `tickCount > 0` five ticks after the helper returned), so `room`/`endVoid`
  need no extra wait.
- **Cleanup is tested and always completes.** `Place.clean()` runs every step (entities, block restores, unforce,
  slot release, active-set removal) even if one throws, rethrows the first failure, and is a no-op the second time;
  `Place.finish()` adds the player's return to `FangGameTests.STAND`. `whenDone` runs it at the end of the test.
  `bound_vex_world_setup_cleanup_end_void` and `..._cleanup_room` call it by hand and check the slot, blocks, forced
  chunks (`ServerLevel.getForceLoadedChunks()`), entities and player; a second call changes nothing. They wait until
  an entity in the place has ticked first: in a busy full run, a cleanup three ticks after the spawn once missed the
  vex, so a place that ends within a few ticks of its first entity may leave it behind.
- **Helper rule.** Every block a test changes in a slot goes through `BoundVexWorldTestSupport.platform` or
  `BoundVexWorldTestSupport.set(helper, level, pos, state)`; both record the block's first state in the place and
  restore it at cleanup. Blocks an explosion destroys are restored only if they were placed through them.
- **`ServerPlayer.teleportTo` and the void catch.** `ServerPlayer.teleportTo` (`ServerPlayer.java:1824`) calls
  `Entity.teleportTo` (`Entity.java:3466`), which builds a `TeleportTransition` with `Vec3.ZERO` as its movement and
  calls `ServerPlayer.teleport` (`ServerPlayer.java:1176`). That method returns null (so `teleportTo` returns false)
  when the NeoForge travel event is canceled (`CommonHooks.onTravelToDimension`) or the player is removed. It dismounts the
  player (`removeVehicle`, line 1190), and the position packet carries the transition's zero movement
  (`PositionMoveRotation.of`, lines 1194 and 1217), also on the client: no `setDeltaMovement`, `stopRiding` or
  `syncVelocity` is needed. It does **not** reset the fall distance: `resetFallDistance()` is still the caller's job.
- **Which reads load a chunk.** `Level.getBlockState` (`Level.java:370`) calls `getChunk(x, z)`, which loads or
  generates the chunk on the server thread. `Level.getHeight` (`Level.java:349-352`) does not: it asks `hasChunk` first
  and reports `getMinY()` for an unloaded column. The non-loading test is `LevelReader.hasChunk(chunkX, chunkZ)`
  (`ServerChunkCache.hasChunk`, `ServerChunkCache.java:263`); `hasChunkAt(BlockPos)` calls it but is deprecated. The
  void catch uses `hasChunk(SectionPos.blockToSectionCoord(x), …)` and trusts a last ground in an unloaded chunk.
- **`LivingDamageEvent.Post` and the crit's show.** `LivingEntity.hurtServer` posts it (`CommonHooks.onLivingDamagePost`,
  `LivingEntity.java:1355`) after `actuallyHurt`, whenever the hit was not dropped earlier. A hit canceled in
  `LivingIncomingDamageEvent`, or one the invulnerability frames refuse (`damage <= lastHurt`, `LivingEntity.java:1273`),
  returns before it. It is **not** a proof that health was lost: a source the victim is invulnerable to, or a fully blocked
  hit, still posts it with `getInflictedDamage()` 0. A harder hit inside the frames posts it with
  `shouldCauseSideEffects()` false (the hurt sound is skipped the same way). The crit's particles and sound require a
  Bound Vex's End melee source, `shouldCauseSideEffects()` and `getInflictedDamage() > 0`.

## 25. Evoking II, Part 6: team moves (Evoking II Part 6, Task 1)

Verified against `build/mc-src` (vanilla with NeoForge patches) and `build/nf-src`; paths are under
`net/minecraft/` and `net/neoforged/neoforge/` respectively.

- **A crossbow fires only what is loaded.** `CrossbowItem.performShooting(Level, LivingEntity shooter, InteractionHand,
  ItemStack weapon, float power, float uncertainty, @Nullable LivingEntity targetOverride)` (`world/item/CrossbowItem.java:170`)
  reads `DataComponents.CHARGED_PROJECTILES` off the weapon, sets it to `EMPTY`, and shoots the stacks it held through
  `ProjectileWeaponItem.shoot` (`world/item/ProjectileWeaponItem.java:73`). The loading that adds Multishot's two
  extra projectiles is `draw` (`ProjectileWeaponItem.java:133`; `processProjectileCount` is called at `:138`), run only by
  `tryLoadProjectiles` (`CrossbowItem.java:91`); `performShooting` never calls it. So a mob that sets
  `CHARGED_PROJECTILES` by hand (`ChargedProjectiles.ofNonEmpty(List<ItemStack>)`,
  `world/item/component/ChargedProjectiles.java:44`) gets exactly those projectiles. `performShooting` is an instance
  method: call it on the stack's `CrossbowItem` (`weapon.getItem() instanceof CrossbowItem crossbow`). `shoot` marks
  arrows as crits only for a `Player` shooter, and only a `Player` shooter posts `EventHooks.onArrowLoose`. The arrow
  constructor throws `IllegalArgumentException` if `firedFromWeapon` is empty (`AbstractArrow.java:97-99`), so the
  weapon stack must be non-empty.
- **Multishot's spread comes from the stack.** `shoot` calls `EnchantmentHelper.processProjectileSpread`
  (`world/item/enchantment/EnchantmentHelper.java:311`); the Multishot data file (`data/minecraft/enchantment/multishot.json`)
  gives `projectile_spread` 10 at level 1 (`linear`, base 10, 10 more per level above the first), so three projectiles
  leave at 0° and ±10°. The enchantment must be on the
  weapon stack: `ItemStack.enchant(Holder<Enchantment>, int)` (`world/item/ItemStack.java:1090`), the holder from
  `level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MULTISHOT)`
  (`world/item/enchantment/Enchantments.java:123`; `InfinityDeathGameTests` makes the same lookup).
- **Durability.** `shoot` calls `weapon.hurtAndBreak(getDurabilityUse(projectile), …)` per projectile
  (`ProjectileWeaponItem.java:101`; a rocket costs 3, `CrossbowItem.getDurabilityUse`, line 166). `ItemStack.isDamageableItem()`
  is false while the stack has `DataComponents.UNBREAKABLE` (`ItemStack.java:448-449`), and the private
  `ItemStack.processDurabilityChange` (`ItemStack.java:492`) then returns 0. `UNBREAKABLE` is a `Unit` component
  (`core/component/DataComponents.java:126`): `stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE)`
  (`net.minecraft.util.Unit` is an enum).
- **Arrow pickup.** `AbstractArrow.pickup` defaults to `DISALLOWED` (`world/entity/projectile/arrow/AbstractArrow.java:71`).
  Only a stack carrying `INTANGIBLE_PROJECTILE` (set by `useAmmo`, which a hand-loaded volley skips) makes it
  `CREATIVE_ONLY` (`AbstractArrow.java:91-93`). A vex's volley arrows are therefore not pickable, with nothing to set.
  (`AbstractArrow.setOwner`, `AbstractArrow.java:626-634`, flips `DISALLOWED` to `ALLOWED` for a `Player` owner; a vex
  owner never does.)
- **Inaccuracy a mob uses.** `RangedAttackMob.rangedAttackUncertainty(Level)` is `14 - level.getDifficulty().getId() * 4`
  (`world/entity/monster/RangedAttackMob.java:9-10`); a Bound Vex is no `RangedAttackMob`, so `VexShots` repeats the formula.
- **Rockets.** `CrossbowItem.createProjectile` (`CrossbowItem.java:152`) builds a `FireworkRocketEntity(level, stack,
  shooter, x, eyeY − 0.15, z, shotAtAngle = true)` (`world/entity/projectile/FireworkRocketEntity.java:88`) for a firework
  stack; `getShootingPower` (`CrossbowItem.java:81`) is 1.6 for a rocket. `Fireworks(int flightDuration,
  List<FireworkExplosion>)` (`world/item/component/Fireworks.java:17`), `FireworkExplosion.DEFAULT`
  (`FireworkExplosion.java:28`). `dealExplosionDamage` (`FireworkRocketEntity.java:227`) deals damage only if the
  explosion list is non-empty: `5 + 2 × explosions` scaled by `sqrt((5 − distance) / 5)` to every `LivingEntity`
  within 5 blocks that a ray from the rocket reaches (a ray to the target's feet or 0.5 above), with
  `damageSources().fireworks(rocket, owner)` (`world/damagesource/DamageSources.java:227`): direct entity the rocket,
  causing entity the owner (the vex), which is what `BoundVexEvents.onIncomingDamage` reads.
- **Deflecting.** `Projectile.deflect(ProjectileDeflection, @Nullable Entity, @Nullable EntityReference<Entity> newOwner,
  boolean byAttack, double power)` (`world/entity/projectile/Projectile.java:313`) builds `Vec3(power, power, power)` and
  runs the deflection, sets the owner, calls `onDeflection`. `ProjectileDeflection.REVERSE`
  (`world/entity/projectile/ProjectileDeflection.java:11`) multiplies the velocity by `−power × 0.5` per axis and sets
  `needsSync`: power 2.0 is a full reversal. `EntityReference.of(entity)` (`world/entity/EntityReference.java:43`).
- **Knockback direction.** `LivingEntity.knockback(double power, double xd, double zd, DamageSource, float damage)`
  (`world/entity/LivingEntity.java:1740`, delegating to the six-argument overload at line 1714) moves the victim along
  `−(xd, zd)`: pass `(attacker − victim)` to push the victim away from the attacker. It scales by
  `1 − KNOCKBACK_RESISTANCE` (line 1721; a Ravager resists 75 %, `Ravager.java:102`) and posts `LivingKnockBackEvent`
  (line 1715).
- **Swing.** `Mob.swingForAttack(InteractionHand)` (`world/entity/Mob.java:1524`) is the public swing (the spec's
  "`swing(MAIN_HAND)`" does not exist in this form in 26.3; `Mob.swing(InteractionHand, SwingAnimation)` also exists,
  `Mob.java:1529`, and `swingForAttack(InteractionHand.MAIN_HAND)` needs no animation argument).
- **Attributes.** `LivingEntity.getAttribute(Holder<Attribute>)` is `@Nullable` (`LivingEntity.java:2287`); `Ghast`,
  `Shulker` and `EnderDragon` define no `ATTACK_DAMAGE`; `Vindicator` 5, `Ravager` 12 (`monster/illager/Vindicator.java:94`,
  `monster/Ravager.java:103`). A brain mob's `getTarget()` reads its brain (`world/entity/monster/warden/Warden.java:474` → `Mob.getTargetFromBrain`, `Mob.java:253`).
- **Entities across levels.** `ServerLevel.getEntityInAnyDimension(UUID)` (`server/level/ServerLevel.java:1533`) looks in
  the level, then in every other level of the server; `Level.getEntity(UUID)` (`world/level/Level.java:846`) only in its own.
- **The server config enum.** `ModConfigSpec.Builder.defineEnum(String, V)` returns `EnumValue<V>`
  (`neoforge/common/ModConfigSpec.java:642`, class at line 1361); `get()` reads the live value, `set(V)` is what
  `GameTestSupport.withConfig` uses.
- **Keys.** `InputConstants.KEY_J = 13`, `KEY_K = 14` (`com/mojang/blaze3d/platform/InputConstants.java:50-51`); no vanilla
  mapping in `net/minecraft/client` uses either. `LocalPlayer.sendOverlayMessage(Component)`
  (`client/player/LocalPlayer.java:465`); on the server `ServerPlayer.sendOverlayMessage`
  (`server/level/ServerPlayer.java:1915`; the base `Player.sendOverlayMessage`, `Player.java:1414`, is empty).
- **Packet codecs.** `ByteBufCodecs.BYTE` (`network/codec/ByteBufCodecs.java:72`), `NeoForgeStreamCodecs.enumCodec(Class)`
  (`neoforge/network/codec/NeoForgeStreamCodecs.java:62`), `StreamCodec.composite` with two fields.
- **Vex flight.** `Vex.VexMoveControl.tick` (`world/entity/monster/Vex.java:299-326`): each tick adds `delta × speed × 0.05 /
  length` to the velocity, goes to `WAIT` (and halves the velocity) when within the vex's box size of the wanted spot,
  and turns the vex toward its target when it has one. "Normal speed" is the charge goal's modifier, 1.0.
- **Holding a distance with the vex move control.** Because it only adds `0.05 x speed` to the velocity a tick and coasts to
  a stop near the wanted point, a plain `moveControl.setWantedPosition` toward a ring point settles well outside the ring
  (a 2-block Evader orbit settled at about 3.1 blocks; a Shield back-off aimed at 6 stopped inside 6). As built, the
  orbit steers with a position/velocity push plus feed-forward, melee Raid members use a spring plus damping inside 3
  blocks of the enemy, and the Shield back-off aims at 7 and holds from 6.
- **Mock players never tick.** A GameTest mock player is not ticked by the server and fires no `PlayerTickEvent`, so
  tests step `TeamMoves.tick(player)` by hand; the scan clock is a stored game-time deadline (`nextScan`), not
  `tickCount`.
- **Rescues and kits.** A rescue does not conjure its bucket or apple while the main hand holds a kit item, so it never
  dismisses a kit slot; a preempted job goal's `stop` is what can.
- **Unhurtable mobs in tests.** `Entity.setPermanentlyInvulnerable(boolean)` (`world/entity/Entity.java:3158`) is the call;
  there is no `setInvulnerable`. `Entity.getEyePosition()` is final (`Entity.java:2032`); `getItemEnchantmentLevel(Holder<Enchantment>,
  ItemInstance)` (`EnchantmentHelper.java:53`) reads Multishot off a stack.
- **A dead player is still ticked.** (Part 5's `BoundVexRescueLifecycleGameTests.dieAndTick`:) `PlayerTickEvent.Post` fires
  for a dead player until it respawns, so `TeamMoves.tick` sees `isAlive() == false`.
- **Baseline.** The first full `./gradlew runGameTestServer` of Part 6 (Task 1) reported `All 497 required tests passed`
  (measured on 981eae7); every Part 6 gate compares against 497 plus the tests the task added.
  As built, the Part 6 total was 630 required tests at Task 13 and is 648 required tests (three full runs in a row) after the branch review's fixes.
- **Later additions to this section.** Tasks 2 to 13 and the review fixes extended it beyond the Task 1 baseline: keys,
  codecs, `Mob.swingForAttack`, attributes, entities across levels, vex flight, mock players, and the two bullets below.
- **Rocket aim.** `CrossbowItem.shootProjectile` with a target override aims at `target.getY(1/3)` and adds
  `0.2 * flatDistance` to the vertical component (`CrossbowItem.java:107-125`), which is an arrow's drop; a
  `FireworkRocketEntity` built with `shotAtAngle = true` (`FireworkRocketEntity(Level, ItemStack, Entity, x, y, z,
  boolean)`) has no gravity and flies that lifted line over a short enemy. `VexShots.rocket` therefore builds the rocket
  itself and calls `Projectile.spawnProjectileUsingShoot(projectile, level, stack, dx, dy, dz, power, uncertainty)`
  (`Projectile.java:241`) at the un-lifted aim point. The blast's damage source is `fireworks(rocket, owner)`: the direct
  entity is the rocket and the causing entity the vex, which the friendly-fire backstop reads.
- **A player's motion on the server.** `ServerPlayer.getDeltaMovement()` stays near zero (the client moves the player);
  `Entity.getKnownMovement()` (`Entity.java:4265`, overridden in `ServerPlayer`) is the real motion, and is
  `getDeltaMovement()` for any other entity.
