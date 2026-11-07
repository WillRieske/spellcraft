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
- Mock players are not ticked: `ServerPlayer.tick()` (`ServerPlayer.java:598`) does not call `Player.tick()`, which is
  reached only through `ServerPlayer.doTick()` (`:668`), and the only caller of `doTick()` is
  `ServerGamePacketListenerImpl.tick()` (`:327`) for connections the server tracks. A `MockPlayers.inLevel` player is
  never ticked: `PlayerTickEvent` does not fire for it, its mob effects do not count down and its `tickCount` does
  not advance. GameTests step the caster by hand (`FangGameTests.step`).
- `ClientTickEvent.Post` fires while the game is paused (`Minecraft.java:1941-1942`), but the level tick and the
  particle engine do not run then, so a `Post` handler must check `Minecraft.isPaused()` itself.
- `RegisterClientExtensionsEvent.registerMobEffect(IClientMobEffectExtensions, Holder<MobEffect>...)` is
  `@SafeVarargs` (`:85-86`).
- `runData` initializes the client subscriber classes and parses the config specs, but not `HumanoidModel.ArmPose`
  (a class literal does not initialize it), so the enum extension is first applied at client launch.
