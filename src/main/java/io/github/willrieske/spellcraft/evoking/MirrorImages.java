package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.spell.rules.MirrorRule;
import io.github.willrieske.spellcraft.spell.rules.VexRing;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Spawning, ending and the confusion of Mirror images (Parts 6-7 spec §7.2).
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
                image -> image.isAlive()
                        && image.casterId().filter(caster::equals).isPresent()));
    }

    /**
     * Spawns {@link MirrorRule#IMAGES} images of {@code carrier} on its ring cells ({@link VexRing}, Chebyshev 2 to 3):
     * cells where the copied type's box is free first (standable ones before the rest), in a random order, then the
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
        Collections.shuffle(cells, new Random(level.getRandom().nextLong()));
        List<BlockPos> ordered = new ArrayList<>();
        List<BlockPos> roomy = cells.stream()
                .filter(cell -> level.noBlockCollision(null, size.makeBoundingBox(Vec3.atBottomCenterOf(cell))))
                .toList();
        roomy.stream().filter(cell -> StandableSpots.standable(level, cell)).forEach(ordered::add);
        roomy.stream().filter(cell -> !ordered.contains(cell)).forEach(ordered::add);
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

    /**
     * Confusion (spec §7.2; amends the parent §9.3 to any mob): when a mob is about to pick a carrier that has live
     * images within {@link MirrorRule#CONFUSION_RANGE} of the carrier, the new target is replaced, with probability
     * 13/14, by a random one of those images. Skipped when the new target has no Mirror effect (this event fires for
     * every target change of every mob in the world, and finding images walks every entity in the level, so the cheap
     * effect test comes first; "no images alive" and "no Mirror effect" are the same thing), when the new target is
     * already an image, when it is the mob's current target (a target goal re-sets its target every tick, and each
     * re-set fires this event; rolling on those would redirect almost every mob), when the mob already targets an
     * image (a live image of this carrier: the change is cancelled and the mob stays on it; any other image: the
     * change goes ahead; no roll either way), and for an iron golem
     * going for a player (the images carry the player's mark, which {@code GolemMercy} refuses as a golem's target).
     * The roll is taken only for an eligible change, so a scripted GameTest roll is never spent on a skipped one.
     * Brain mobs that write their target without this event are not confused (accepted).
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
                || (mob instanceof IronGolem && carrier instanceof Player)) {
            return;
        }
        if (mob.getTarget() instanceof MirrorImage current) {
            if (current.isAlive()
                    && current.casterId().filter(carrier.getUUID()::equals).isPresent()) {
                event.setCanceled(true);
            }
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
}
