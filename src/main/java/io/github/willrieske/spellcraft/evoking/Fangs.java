package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.spell.Allies;
import io.github.willrieske.spellcraft.spell.rules.FangFootprints;
import io.github.willrieske.spellcraft.spell.rules.FangFootprints.Column;
import io.github.willrieske.spellcraft.spell.rules.FangSquare;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * The fang cast (Evoking spec §5 as amended by Evoking II §2.2): at the press, the square ahead of the caster, the
 * block columns every target in it covers, and the floor under each; one warmup later, a vanilla {@link EvokerFangs}
 * on each of those spots, placed the way the evoker places its own. A fang remembers its target: when it bites and the
 * target has stepped off its columns, one more fang (a chase fang, which chases nothing itself) rises under the
 * target's new footprint. Also the guard that keeps a player's fangs off the player's summons (spec §3).
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class Fangs {
    /** How far below the caster's feet the target scan reaches: fourteen blocks. */
    public static final int SCAN_DEPTH = 14;

    /** How far above the caster's feet the target scan reaches: six blocks. */
    public static final int SCAN_HEIGHT = 6;

    /** A chase fang follows a target at most this far from the caster. */
    public static final double CHASE_RANGE = 16.0;

    /**
     * The fang's tick on which vanilla bites: {@code EvokerFangs.tick} counts {@code warmupDelayTicks} down once per
     * tick and bites at −8, and {@code Entity.commonTick} bumps {@code tickCount} before {@code tick()}, so a fang
     * placed with delay 0 bites on the tick that leaves {@code tickCount == 8} (docs/api-notes.md §20).
     */
    public static final int BITE_TICK = 8;

    /** The evoker's floor window around one target's feet: one block up, two down. */
    private static final int FLOOR_UP = 1;

    private static final int FLOOR_DOWN = 2;

    /**
     * Where one fang goes: a column and the floor height found under it at the press.
     *
     * @param column the block column
     * @param y the fang's y: the floor block's top, raised by a carpet or slab in the cell
     */
    public record Spot(Column column, double y) {}

    /**
     * One target's footprint at the press.
     *
     * @param target the target's UUID
     * @param columns every column its box covered inside the square, before any dedupe (the chase reads it)
     * @param spots the spots this target gets fangs on: its columns with a floor, minus the spots an earlier target
     *     already claimed
     */
    public record Footprint(UUID target, List<Column> columns, List<Spot> spots) {}

    /**
     * What the press fixed (spec §2.2): the fangs are placed from this, not from where anything has walked to.
     *
     * @param dimension the level the caster stood in
     * @param feet the caster's block; every fang of the cast faces away from it
     * @param square the square ahead
     * @param footprints one per target that claimed at least one spot
     */
    public record Snapshot(
            ResourceKey<Level> dimension, BlockPos feet, FangSquare square, List<Footprint> footprints) {}

    /** The target a cast's fangs were placed for; the fangs of one footprint share one record. */
    private static final class Chase {
        final UUID target;
        final UUID caster;
        final BlockPos feet;
        final Set<Column> columns;
        boolean done;

        Chase(UUID target, UUID caster, BlockPos feet, Set<Column> columns) {
            this.target = target;
            this.caster = caster;
            this.feet = feet;
            this.columns = columns;
        }
    }

    /** Fang entity id → its chase record. Transient: entries leave with their fang and the server. */
    private static final Map<Integer, Chase> CHASES = new HashMap<>();

    private Fangs() {}

    /**
     * The snapshot for a press by {@code caster}, or empty when no target in the square claims a spot: the scan box is
     * the square's columns from {@link #SCAN_DEPTH} below the caster's feet to {@link #SCAN_HEIGHT} above. Each target's
     * columns are the ones its bounding box covers ({@link FangFootprints#columns}) inside the square, the caster's
     * column excluded ({@link FangFootprints#inSquare}); each column with a floor in the window around the target's
     * feet is a spot, and a spot two targets share goes to the first (one fang per spot). A target over no floor, or
     * whose spots are all taken, gets nothing.
     */
    static Optional<Snapshot> snapshot(ServerLevel level, ServerPlayer caster) {
        BlockPos feet = caster.blockPosition();
        FangSquare square = FangSquare.of(caster.getYRot());
        AABB box = new AABB(
                feet.getX() + square.minDx(),
                feet.getY() - SCAN_DEPTH,
                feet.getZ() + square.minDz(),
                feet.getX() + square.maxDx() + 1,
                feet.getY() + SCAN_HEIGHT + 1,
                feet.getZ() + square.maxDz() + 1);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box, e -> isTarget(caster, e));
        Set<Spot> claimed = new HashSet<>();
        List<Footprint> footprints = new ArrayList<>();
        for (LivingEntity target : targets) {
            List<Column> columns = FangFootprints.inSquare(columnsOf(target), square, feet.getX(), feet.getZ());
            int feetY = target.blockPosition().getY();
            List<Spot> spots = new ArrayList<>();
            for (Column column : columns) {
                OptionalDouble y = floorUnder(level, column, feetY);
                if (y.isPresent()) {
                    Spot spot = new Spot(column, y.getAsDouble());
                    if (claimed.add(spot)) {
                        spots.add(spot);
                    }
                }
            }
            if (!spots.isEmpty()) {
                footprints.add(new Footprint(target.getUUID(), List.copyOf(columns), List.copyOf(spots)));
            }
        }
        if (footprints.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Snapshot(level.dimension(), feet, square, List.copyOf(footprints)));
    }

    /** The block columns {@code entity}'s bounding box covers right now. */
    static List<Column> columnsOf(Entity entity) {
        AABB box = entity.getBoundingBox();
        return FangFootprints.columns(box.minX, box.minZ, box.maxX, box.maxZ);
    }

    /**
     * A target (spec glossary): an enemy a mob could attack, attackable, and neither the caster nor an ally. A player
     * counts only when the caster may harm that player ({@link ServerPlayer#canHarmPlayer}: the {@code pvp} game rule;
     * teammates are already spared by the ally rule), so fangs never start a cast whose bites vanilla would refuse.
     */
    // Entities compare by id (docs/api-notes.md §8); this asks "is it the caster".
    @SuppressWarnings("ReferenceEquality")
    static boolean isTarget(ServerPlayer caster, LivingEntity entity) {
        return entity != caster
                && entity.canBeSeenAsEnemy()
                && entity.attackable()
                && !Allies.isAllyOf(caster, entity)
                && (!(entity instanceof Player other) || caster.canHarmPlayer(other));
    }

    /**
     * Places the fangs of {@code snapshot}: one per spot, facing away from the caster's block at the press, no warmup
     * delay, each recorded for the chase. Nothing happens in another dimension than the snapshot's. Spots whose chunk
     * is not entity-ticking get none ({@link ServerLevel#isPositionEntityTicking}; no chunk is loaded or waited on).
     *
     * @param level the level to place the fangs in
     * @param caster the owner of the fangs
     * @param snapshot the footprints taken when the cast started
     * @return true when the snapshot's level matched (so the cast sound plays), even if no spot's chunk was ticking
     */
    static boolean perform(ServerLevel level, ServerPlayer caster, Snapshot snapshot) {
        if (!level.dimension().equals(snapshot.dimension())) {
            return false;
        }
        for (Footprint footprint : snapshot.footprints()) {
            Chase chase =
                    new Chase(footprint.target(), caster.getUUID(), snapshot.feet(), Set.copyOf(footprint.columns()));
            for (Spot spot : footprint.spots()) {
                place(level, caster, snapshot.feet(), spot).ifPresent(fang -> CHASES.put(fang.getId(), chase));
            }
        }
        return true;
    }

    /**
     * One fang on {@code spot}, delay 0, facing away from {@code feet}. Empty when the spot's chunk is not
     * entity-ticking: a fang there would be saved with the chunk and bite later.
     */
    private static Optional<EvokerFangs> place(ServerLevel level, ServerPlayer caster, BlockPos feet, Spot spot) {
        int x = spot.column().x();
        int z = spot.column().z();
        if (!level.isPositionEntityTicking(new BlockPos(x, Mth.floor(spot.y()), z))) {
            return Optional.empty();
        }
        float angle = (float) Mth.atan2(z - feet.getZ(), x - feet.getX());
        EvokerFangs fang = new EvokerFangs(level, x + 0.5, spot.y(), z + 0.5, angle, 0, caster);
        if (!level.addFreshEntity(fang)) {
            return Optional.empty(); // another mod cancelled the join: no fang, no record, no game event
        }
        level.gameEvent(GameEvent.ENTITY_PLACE, new Vec3(x + 0.5, spot.y(), z + 0.5), GameEvent.Context.of(caster));
        return Optional.of(fang);
    }

    /**
     * The floor for a target whose feet are at block height {@code feetY}: {@link #findFloor} over the window from
     * {@link #FLOOR_UP} above to {@link #FLOOR_DOWN} below. The press and the chase share it so they search alike.
     */
    private static OptionalDouble floorUnder(ServerLevel level, Column column, int feetY) {
        return findFloor(level, column.x(), column.z(), feetY + FLOOR_UP, feetY - FLOOR_DOWN);
    }

    /**
     * The evoker's floor search ({@code Evoker.createSpellEntity}): from {@code top} down to {@code bottom}, the first
     * cell whose block below has a sturdy top face, raised by the cell's own collision top (a slab, a carpet).
     */
    static OptionalDouble findFloor(ServerLevel level, int x, int z, int top, int bottom) {
        BlockPos pos = new BlockPos(x, top, z);
        while (pos.getY() >= bottom) {
            BlockPos below = pos.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                double topOffset = 0.0;
                if (!level.isEmptyBlock(pos)) {
                    VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
                    if (!shape.isEmpty()) {
                        topOffset = shape.max(Direction.Axis.Y);
                    }
                }
                return OptionalDouble.of(pos.getY() + topOffset);
            }
            pos = below;
        }
        return OptionalDouble.empty();
    }

    /**
     * The chase (spec §2.2): on a recorded fang's bite tick, if its target is alive, within {@link #CHASE_RANGE} of the
     * caster, no ally, and standing on none of the columns of its own footprint at the press, one fang rises under
     * each column of the target's current footprint that has a floor, with no record. One chase per target per cast:
     * the first of a footprint's fangs to reach the bite tick settles it, whether or not a fang is placed.
     *
     * <p>The caster's absence ({@code getPlayerByUUID} returning null) ends the chase quietly; that exit is covered
     * by reasoning only, not by a GameTest.
     *
     * @param event the post-tick event for any entity
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (CHASES.isEmpty()
                || !(event.getEntity() instanceof EvokerFangs fang)
                || fang.tickCount != BITE_TICK
                || !(fang.level() instanceof ServerLevel level)) {
            return;
        }
        Chase chase = CHASES.get(fang.getId());
        if (chase == null || chase.done) {
            return;
        }
        chase.done = true;
        if (!(level.getPlayerByUUID(chase.caster) instanceof ServerPlayer caster)
                || !(level.getEntity(chase.target) instanceof LivingEntity target)
                || !target.isAlive()
                || caster.distanceTo(target) > CHASE_RANGE
                || Allies.isAllyOf(caster, target)) {
            return;
        }
        List<Column> now = columnsOf(target);
        if (now.stream().anyMatch(chase.columns::contains)) {
            return; // still on its fangs: they bit, or will
        }
        int feetY = target.blockPosition().getY();
        for (Column column : now) {
            floorUnder(level, column, feetY)
                    .ifPresent(y -> place(level, caster, chase.feet, new Spot(column, y))); // no record: never chases
        }
    }

    /**
     * Drops a fang's chase record when the fang leaves the level: it dies of age on its 23rd tick, 15 ticks after the
     * bite, or its chunk stops being accessible (docs/api-notes.md §20; a fang unloaded before its bite tick never
     * chases, accepted).
     *
     * <p>Server levels only: the event is also posted for client levels, which share entity ids with the server in
     * single player, so a client-side removal would race with the server thread and could drop a live record.
     *
     * @param event the leave event for any entity
     */
    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)) {
            return;
        }
        if (event.getEntity() instanceof EvokerFangs fang) {
            CHASES.remove(fang.getId());
        }
    }

    /**
     * Drops every chase record when the server stops (single-player loads one server per world in the same JVM).
     *
     * @param event the server-stopped event
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CHASES.clear();
    }

    /**
     * Whether {@code fang} carries a chase record. For the GameTests.
     *
     * @param fang a fang
     * @return true while the fang's record exists
     */
    public static boolean hasChaseRecord(EvokerFangs fang) {
        return CHASES.containsKey(fang.getId());
    }

    /**
     * A player's fangs never bite the player's allies (spec §3). Vanilla already skips {@code isAlliedTo} allies; this
     * adds the summoner path (a vex the player called is no vanilla ally of the player). A fang whose caster has since
     * logged out has no owner and bites like a vanilla ownerless fang; accepted (a one-second window).
     *
     * @param event the incoming-damage event for any living entity
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (source.getDirectEntity() instanceof EvokerFangs
                && source.getEntity() instanceof Player owner
                && Allies.isAllyOf(owner, event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
