package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.spell.rules.RescuePush;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Bound Vexes rescue their summoner (Parts 4–5 spec §5, §6.2): driven from the player's tick, this class finds the
 * need, picks the rescuer and applies the push itself, so a rescue never waits on a vex's goals. The rescuer's
 * {@link RescueGoal} only keeps the vex at the player's side. All state is transient.
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class VexRescues {
    /** What a rescue is for. */
    public enum Kind {
        /** The lava pull. */
        LAVA,
        /** The drowning push. */
        DROWNING,
        /** The void catch. */
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

    /** How far to the right of the way the player faces the rescuer hangs. */
    private static final double SIDE = 0.6;

    /** How far above the player's feet the rescuer hangs. */
    private static final double SIDE_HEIGHT = 1.2;

    /** How many splash particles the Nether's water bucket makes. */
    private static final int SPLASHES = 12;

    /**
     * One running rescue. {@code tailUntil} is 0 until the work is done; {@code conjured} says the tail owns the
     * hand.
     */
    private static final class Rescue {
        /** What the rescue is for. */
        final Kind kind;

        /** The vex doing it. */
        final BoundVex rescuer;

        /** The game time it started. */
        final long start;

        /** How many ticks in a row the player has been out of lava. */
        int outOfLava;

        /** The game time the tail ends, or 0 while the rescue's work goes on. */
        long tailUntil;

        /** Whether the tail put an item in the rescuer's hand, which the end of the rescue must take away. */
        boolean conjured;

        /** Starts a rescue of {@code kind} by {@code rescuer} at game time {@code start}. */
        Rescue(Kind kind, BoundVex rescuer, long start) {
            this.kind = kind;
            this.rescuer = rescuer;
            this.start = start;
        }
    }

    /**
     * Where a player last stood on a standable spot.
     *
     * @param dimension the dimension of the spot
     * @param position the player's position there
     */
    record Ground(ResourceKey<Level> dimension, Vec3 position) {}

    /** The running rescue of each player. */
    private static final Map<UUID, Rescue> RUNNING = new HashMap<>();

    /** The last standable ground of each player. */
    private static final Map<UUID, Ground> LAST_GROUND = new HashMap<>();

    /** The game time before which no lava pull starts for each player. */
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
     * Drops a player's rescue and the rest of their state when they log out.
     *
     * @param event the logout event
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        drop(id);
        LAST_GROUND.remove(id);
        LAVA_PAUSED_UNTIL.remove(id);
    }

    /**
     * One step for {@code player}: notes where they last stood, carries a running rescue on, or starts the first
     * rescue they need. Public so GameTests can step a mock player, which gets no player tick.
     *
     * @param player the player
     */
    public static void tick(ServerPlayer player) {
        if (player instanceof FakePlayer) {
            return;
        }
        UUID id = player.getUUID();
        if (!player.isAlive() || player.isSpectator()) {
            drop(id);
            return;
        }
        ServerLevel level = player.level();
        if (player.onGround() && StandableSpots.standable(level, player.blockPosition()) && moved(id, player)) {
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

    /**
     * The kind of rescue running for {@code player}.
     *
     * @param player the player
     * @return the kind, or empty when none runs (a tail counts as running)
     */
    public static Optional<Kind> running(ServerPlayer player) {
        return Optional.ofNullable(RUNNING.get(player.getUUID())).map(rescue -> rescue.kind);
    }

    /**
     * The vex rescuing {@code player}.
     *
     * @param player the player
     * @return the rescuer, or empty when no rescue runs
     */
    public static Optional<BoundVex> rescuer(ServerPlayer player) {
        return Optional.ofNullable(RUNNING.get(player.getUUID())).map(rescue -> rescue.rescuer);
    }

    /**
     * The online player that {@code vex} is rescuing, in the vex's own level.
     *
     * @param vex the vex
     * @return the player, or empty when the vex rescues nobody
     */
    static Optional<ServerPlayer> rescuedBy(BoundVex vex) {
        MinecraftServer server = vex.level().getServer();
        if (server == null) {
            return Optional.empty();
        }
        for (Map.Entry<UUID, Rescue> entry : RUNNING.entrySet()) {
            if (entry.getValue().rescuer.equals(vex)) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                return player != null && player.level().equals(vex.level()) ? Optional.of(player) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Where {@code player} last stood on a standable spot (read by the void catch).
     *
     * @param player the player's id
     * @return the ground, or empty when they never did
     */
    static Optional<Ground> lastGround(UUID player) {
        return Optional.ofNullable(LAST_GROUND.get(player));
    }

    /** Drops every rescue (giving hands back) and forgets every player; called when the server stops. */
    static void clear() {
        for (UUID id : List.copyOf(RUNNING.keySet())) {
            drop(id);
        }
        RUNNING.clear();
        LAST_GROUND.clear();
        LAVA_PAUSED_UNTIL.clear();
    }

    /** Starts the first rescue {@code player} needs and has a rescuer for; Tasks 5 and 6 add their kinds here. */
    private static void startFirstNeeded(ServerPlayer player) {
        if (needsLavaPull(player)) {
            start(player, Kind.LAVA, RESCUER_RANGE);
        }
    }

    /** Whether the stored last ground of {@code id} is on another block or in another dimension than {@code player}. */
    private static boolean moved(UUID id, ServerPlayer player) {
        Ground stored = LAST_GROUND.get(id);
        return stored == null
                || !stored.dimension().equals(player.level().dimension())
                || !BlockPos.containing(stored.position()).equals(player.blockPosition());
    }

    /** Whether {@code player} is in lava and no rule keeps a vex from pulling them out. */
    private static boolean needsLavaPull(ServerPlayer player) {
        long now = player.level().getGameTime();
        return player.isInLava()
                && wantsLavaPull(player)
                && now >= LAVA_PAUSED_UNTIL.getOrDefault(player.getUUID(), 0L);
    }

    /** Whether {@code player} is the kind of player a lava pull is for: not immune, not creative, not riding. */
    private static boolean wantsLavaPull(ServerPlayer player) {
        ResourceKey<Level> dimension = player.level().dimension();
        return (dimension.equals(Level.OVERWORLD) || dimension.equals(Level.NETHER))
                && !player.isCreative()
                && !player.isPassenger()
                && !player.hasEffect(MobEffects.FIRE_RESISTANCE);
    }

    /**
     * Starts a rescue of {@code kind} when a rescuer is within {@code range}; it goes to the player's side. The
     * move can take the rescuer away from the target it was fighting: it keeps that target only if the target is
     * still within the vex's reach from the player's side, otherwise the vex's own rules drop it.
     */
    private static Optional<Rescue> start(ServerPlayer player, Kind kind, double range) {
        UUID id = player.getUUID();
        Optional<BoundVex> rescuer = player
                .level()
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
                && vex.level().equals(player.level())
                && vex.isSummonedBy(player.getUUID());
    }

    /** One tick of a running rescue: the tail counts down, otherwise the kind's work goes on. */
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

    /** One tick of a lava pull: counts the ticks out of lava, ends the pull, or pushes. */
    private static void lava(ServerPlayer player, Rescue rescue, long now) {
        if (!wantsLavaPull(player)) {
            drop(player.getUUID());
            return;
        }
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

    /** Ends a lava pull: starts the pause, then in the Nether (player out of lava) the bucket and its tail. */
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
        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.BUCKET_EMPTY,
                SoundSource.NEUTRAL,
                1.0F,
                1.0F);
        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.FIRE_EXTINGUISH,
                SoundSource.NEUTRAL,
                0.7F,
                1.0F);
        level.sendParticles(
                ParticleTypes.SPLASH, player.getX(), player.getY(0.5), player.getZ(), SPLASHES, 0.3, 0.5, 0.3, 0.0);
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

    /** Gives the player the velocity {@code v} and marks it for sending to their client. */
    private static void push(ServerPlayer player, RescuePush.Vector v) {
        player.setDeltaMovement(v.x(), v.y(), v.z());
        player.syncVelocity = true;
    }

    /** The pure core's vector for {@code v}. */
    private static RescuePush.Vector vector(Vec3 v) {
        return new RescuePush.Vector(v.x, v.y, v.z);
    }

    /** Ends the player's rescue, whatever its state, and gives the rescuer's hand back if the tail took it. */
    private static void drop(UUID player) {
        Rescue rescue = RUNNING.remove(player);
        if (rescue != null && rescue.conjured && rescue.rescuer.isAlive()) {
            rescue.rescuer.dismiss(EquipmentSlot.MAINHAND);
        }
    }

    /**
     * The point at a player's side where their rescuer hangs: 0.6 blocks to the right of the way they face, 1.2 above
     * their feet.
     *
     * @param player the player
     * @return the point
     */
    static Vec3 sideOf(ServerPlayer player) {
        double yaw = Math.toRadians(player.getYRot());
        return player.position().add(-Math.cos(yaw) * SIDE, SIDE_HEIGHT, -Math.sin(yaw) * SIDE);
    }

    /**
     * Puts {@code vex} at the player's side, still. Vexes pass through blocks, so any point works. A target out of
     * the vex's reach from there is dropped by the vex's own rules, not here.
     *
     * @param vex the rescuer
     * @param player the player
     */
    static void placeBeside(BoundVex vex, ServerPlayer player) {
        Vec3 side = sideOf(player);
        vex.setPos(side.x, side.y, side.z);
        vex.setDeltaMovement(Vec3.ZERO);
    }
}
