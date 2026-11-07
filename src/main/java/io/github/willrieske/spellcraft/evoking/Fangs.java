package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.spell.Allies;
import io.github.willrieske.spellcraft.spell.rules.FangSquare;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
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
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * The fang cast (spec §5): at the press, the square ahead of the caster and whether anything in it is a target; one
 * warmup later, a vanilla {@link EvokerFangs} on every cell of the square but the caster's, placed the way the
 * evoker places its own. Also the guard that keeps a player's fangs off the player's summons (spec §3).
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class Fangs {
    /** How far below and above the caster's feet the target scan reaches. */
    public static final int SCAN_HALF_HEIGHT = 6;

    /**
     * What the press fixed (spec §5): the fangs are placed from this, not from where the caster has walked to.
     *
     * @param dimension the level the caster stood in
     * @param feet the caster's block
     * @param square the square ahead
     * @param top the highest cell a fang's floor search starts from: max(feet, highest target) + 1
     * @param bottom the lowest cell the search reaches: min(feet, lowest target) − 1
     */
    public record Snapshot(ResourceKey<Level> dimension, BlockPos feet, FangSquare square, int top, int bottom) {}

    private Fangs() {}

    /**
     * The snapshot for a press by {@code caster}, or empty when no target stands in the square's columns within
     * {@link #SCAN_HALF_HEIGHT} blocks of the caster's feet.
     */
    static Optional<Snapshot> snapshot(ServerLevel level, ServerPlayer caster) {
        BlockPos feet = caster.blockPosition();
        FangSquare square = FangSquare.of(caster.getYRot());
        AABB box = new AABB(
                feet.getX() + square.minDx(),
                feet.getY() - SCAN_HALF_HEIGHT,
                feet.getZ() + square.minDz(),
                feet.getX() + square.maxDx() + 1,
                feet.getY() + SCAN_HALF_HEIGHT + 1,
                feet.getZ() + square.maxDz() + 1);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box, e -> isTarget(caster, e));
        if (targets.isEmpty()) {
            return Optional.empty();
        }
        int highest = feet.getY();
        int lowest = feet.getY();
        for (LivingEntity target : targets) {
            int y = target.blockPosition().getY();
            highest = Math.max(highest, y);
            lowest = Math.min(lowest, y);
        }
        return Optional.of(new Snapshot(level.dimension(), feet, square, highest + 1, lowest - 1));
    }

    /** A target (spec glossary): an enemy a mob could attack, attackable, and neither the caster nor an ally. */
    // Entities compare by id (docs/api-notes.md §8); this asks "is it the caster".
    @SuppressWarnings("ReferenceEquality")
    static boolean isTarget(ServerPlayer caster, LivingEntity entity) {
        return entity != caster && entity.canBeSeenAsEnemy() && entity.attackable() && !Allies.isAllyOf(caster, entity);
    }

    /**
     * Places the fangs of {@code snapshot}: one per cell with a sturdy floor in the window, facing away from the
     * caster, with the cell's ripple delay. Nothing happens in another dimension than the snapshot's. Cells outside
     * the build limits or in unloaded chunks get none ({@link Level#isLoaded}; no chunk is loaded or waited on).
     *
     * @return true when the snapshot's level matched (so the cast sound plays), even if no cell had a floor
     */
    static boolean perform(ServerLevel level, ServerPlayer caster, Snapshot snapshot) {
        if (!level.dimension().equals(snapshot.dimension())) {
            return false;
        }
        BlockPos feet = snapshot.feet();
        for (FangSquare.Cell cell : snapshot.square().cells()) {
            int x = feet.getX() + cell.dx();
            int z = feet.getZ() + cell.dz();
            if (!level.isLoaded(new BlockPos(x, feet.getY(), z))) {
                continue; // at the caster's height this trips only on the horizontal world bounds or an unloaded chunk
                // (spec §8)
            }
            findFloor(level, x, z, snapshot.top(), snapshot.bottom()).ifPresent(y -> {
                float angle = (float) Mth.atan2(cell.dz(), cell.dx());
                level.addFreshEntity(new EvokerFangs(level, x + 0.5, y, z + 0.5, angle, cell.delayTicks(), caster));
                level.gameEvent(GameEvent.ENTITY_PLACE, new Vec3(x + 0.5, y, z + 0.5), GameEvent.Context.of(caster));
            });
        }
        return true;
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
