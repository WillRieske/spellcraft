package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.spell.ModItems;
import io.github.willrieske.spellcraft.spell.Spell;
import io.github.willrieske.spellcraft.spell.SpellScrollItem;
import io.github.willrieske.spellcraft.spell.Spells;
import io.github.willrieske.spellcraft.spell.rules.CasterRules;
import io.github.willrieske.spellcraft.spell.rules.GhostrailDuration;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

/**
 * Evokers, vindicators and pillagers that carry a Mirror or Ghostrail scroll and use it on themselves (Parts 6-7 spec
 * §8). The scroll sits in the offhand with a drop chance of 0.5, which vanilla honors only for a player's kill (Looting
 * raises it): that is the parent spec's "loot path for players". Vanilla's own equipment fills only the main hand and
 * runs after {@code FinalizeSpawnEvent}.
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class ScrollMobs {
    /** An evoker carries a scroll one time in this many. */
    public static final int EVOKER_ONE_IN = 4;

    /** A vindicator or pillager carries a scroll one time in this many. */
    public static final int RAIDER_ONE_IN = 10;

    /** The offhand drop chance of a carried scroll. */
    static final float DROP_CHANCE = 0.5F;

    /** Ticks before a mob may cast Ghostrail on itself again (30 seconds; Mirror uses ScrollCasting's cooldown). */
    public static final int MOB_GHOSTRAIL_COOLDOWN_TICKS = 600;

    /** The cast's purple, the Evoker-spell color of {@code CastingParticles} for a scroll. */
    private static final int PURPLE = 0xFFB24CFF;

    /** Particles in the cast's burst. */
    private static final int BURST = 10;

    private ScrollMobs() {}

    /**
     * The sides of the carry die for {@code mob}: 4 for an evoker, 10 for a vindicator or pillager, 0 for any other
     * mob.
     */
    private static int oneIn(Mob mob) {
        if (mob instanceof Evoker) {
            return EVOKER_ONE_IN;
        }
        return mob instanceof Vindicator || mob instanceof Pillager ? RAIDER_ONE_IN : 0;
    }

    /**
     * Gives a new mob a scroll: the carry roll, then the spell (0 Mirror, 1 Ghostrail).
     *
     * @param event the finalize-spawn event
     */
    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        Mob mob = event.getEntity();
        int oneIn = oneIn(mob);
        if (oneIn == 0 || Dice.SCROLL_MOB.below(mob.getRandom(), oneIn) != 0) {
            return;
        }
        Spell spell = Dice.SCROLL_MOB.below(mob.getRandom(), 2) == 0 ? Spells.MIRROR : Spells.GHOSTRAIL;
        mob.setItemSlot(EquipmentSlot.OFFHAND, ModItems.scrollFor(spell));
        mob.setDropChance(EquipmentSlot.OFFHAND, DROP_CHANCE);
    }

    /**
     * Casts the carried scroll when the mob goes from no target to a target (so a mob that loses its target casts next
     * time it gets one) and the spell's cooldown is over. Instant: a 20-tick wind-up on a fighting illager is easy to
     * miss. The scroll is not used up.
     *
     * @param event the change-target event for any living entity
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)
                || event.getNewAboutToBeSetTarget() == null
                || mob.getTarget() != null
                || !(mob.level() instanceof ServerLevel level)
                || !(mob.getOffhandItem().getItem() instanceof SpellScrollItem scroll)) {
            return;
        }
        long now = level.getGameTime();
        Spell spell = scroll.spell();
        if (spell.equals(Spells.MIRROR)
                && !mob.hasEffect(ModEffects.MIRROR)
                && CasterRules.ready(
                        now,
                        mob.getExistingData(EvokingAttachments.MOB_MIRROR_READY).orElse(0L))
                && ScrollCasting.land(mob, mob, Spells.MIRROR, GhostrailDuration.Kind.HOSTILE_MOB)) {
            mob.setData(EvokingAttachments.MOB_MIRROR_READY, now + ScrollCasting.cooldownFor(Spells.MIRROR));
            burst(level, mob);
        } else if (spell.equals(Spells.GHOSTRAIL)
                && !mob.hasEffect(ModEffects.GHOSTRAIL)
                && CasterRules.ready(
                        now,
                        mob.getExistingData(EvokingAttachments.MOB_GHOSTRAIL_READY)
                                .orElse(0L))
                && ScrollCasting.land(mob, mob, Spells.GHOSTRAIL, GhostrailDuration.Kind.HOSTILE_MOB)) {
            mob.setData(EvokingAttachments.MOB_GHOSTRAIL_READY, now + MOB_GHOSTRAIL_COOLDOWN_TICKS);
            burst(level, mob);
        }
    }

    /** A burst of purple particles (the cast sound came from {@link ScrollCasting#land}). */
    private static void burst(ServerLevel level, Mob mob) {
        level.sendParticles(
                ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, PURPLE),
                mob.getX(),
                mob.getY() + mob.getBbHeight() / 2.0,
                mob.getZ(),
                BURST,
                0.4,
                0.5,
                0.4,
                0.05);
    }

    /**
     * Installs the chase goal on an evoker, vindicator or pillager that joins a level (new, loaded or arrived).
     *
     * @param event the join event
     */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob && oneIn(mob) != 0) {
            install(mob);
        }
    }

    /**
     * Adds a {@link GhostrailChaseGoal} (priority 0) unless the mob has one.
     *
     * @param mob a mob
     */
    public static void install(Mob mob) {
        boolean present = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof GhostrailChaseGoal);
        if (!present) {
            mob.goalSelector.addGoal(0, new GhostrailChaseGoal(mob));
        }
    }
}
