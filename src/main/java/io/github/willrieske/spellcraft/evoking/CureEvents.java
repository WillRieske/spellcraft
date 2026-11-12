package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import java.util.Optional;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * The smoke, the freeze and its release (Evoking II Parts 4-5 spec §2.3). A zombie villager's cure outlives the
 * {@link CureZombieVillagerGoal} that began it, so it is driven from the zombie villager's own tick, and does not
 * depend on the vex: while the zombie villager carries the cure mark ({@link AllyAttachments#CURE}) it smokes while it
 * converts, stays frozen, and is released if the cure never began. The mark is saved, so a zombie villager unloaded
 * mid-cure keeps converting when it loads again, with its smoke, and is released by the same rules. The vanilla facts
 * (the tick order, the conversion events) are docs/api-notes.md §23.
 *
 * <p>Functional spec §9.4, §9.5.
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class CureEvents {
    /**
     * Ticks after which a mark on a zombie villager that is not converting is stale: the vex died, was unloaded or
     * never finished (steps 1-3 take at most 150 ticks). This releases a freeze whose goal can no longer run
     * {@code stop}. Functional spec §9.5.
     */
    public static final long RELEASE_AFTER = 300L;

    /** Smoke particles of each kind per tick while converting. */
    private static final int SMOKE = 4;

    /** The particle speed of the smoke. */
    private static final double SMOKE_SPEED = 0.02;

    private CureEvents() {}

    /**
     * Each tick of a marked zombie villager on the server. Converting: {@link #SMOKE} {@code HAPPY_VILLAGER} and
     * {@code CLOUD} particles over its body, and it stays frozen. Not converting and the mark older than
     * {@link #RELEASE_AFTER}: unfreeze, remove the mark. Not converting and the mark younger: nothing (the goal is
     * still working). On the tick a conversion finishes the old zombie villager is already removed when this fires
     * (docs/api-notes.md §23), so it is skipped. Functional spec §9.4, §9.5.
     *
     * @param event the post-tick event for any entity
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ZombieVillager zombieVillager)
                || zombieVillager.isRemoved()
                || !(zombieVillager.level() instanceof ServerLevel level)) {
            return;
        }
        Optional<Long> since = zombieVillager.getExistingData(AllyAttachments.CURE);
        if (since.isEmpty()) {
            return;
        }
        if (zombieVillager.isConverting()) {
            double width = zombieVillager.getBbWidth() * 0.5;
            double height = zombieVillager.getBbHeight() * 0.5;
            double x = zombieVillager.getX();
            double y = zombieVillager.getY() + height;
            double z = zombieVillager.getZ();
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, SMOKE, width, height, width, SMOKE_SPEED);
            level.sendParticles(ParticleTypes.CLOUD, x, y, z, SMOKE, width, height, width, SMOKE_SPEED);
        } else if (level.getGameTime() - since.get() > RELEASE_AFTER) {
            zombieVillager.setNoAi(false);
            zombieVillager.removeData(AllyAttachments.CURE);
        }
    }

    /**
     * Vanilla's conversion copies {@code NoAI} to the villager, so a villager made from a frozen zombie villager would
     * stand still: when the old mob carried the cure mark (still readable here, the new villager gets no attachments,
     * docs/api-notes.md §23), the new one gets its AI back. Functional spec §9.4.
     *
     * @param event the post-conversion event: the old mob and its outcome
     */
    @SubscribeEvent
    public static void onConversion(LivingConversionEvent.Post event) {
        if (AllyAttachments.isBeingCured(event.getEntity()) && event.getOutcome() instanceof Mob converted) {
            converted.setNoAi(false);
        }
    }
}
