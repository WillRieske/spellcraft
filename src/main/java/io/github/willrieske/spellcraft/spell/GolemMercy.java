package io.github.willrieske.spellcraft.spell;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.spell.rules.GolemMercyRule;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Golem mercy (Evoking II spec §2.3): a player's side (any player, their fangs, a wolf a player owns, a mob a player
 * summoned) never takes an iron golem under {@link GolemMercyRule#MERCY_HEALTH}, a golem left there forgives, and a
 * golem never takes a player's summon as its target. The rule is {@link GolemMercyRule}; this class is the three
 * event hooks.
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class GolemMercy {
    private GolemMercy() {}

    /**
     * Clamps player-side damage so the golem keeps at least 25 health. Runs after armor and magic reductions, on the
     * value that would reach health.
     *
     * @param event the pre-damage event for any living entity
     */
    @SubscribeEvent
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof IronGolem golem && isPlayersSide(event.getSource())) {
            event.setNewDamage(GolemMercyRule.clamp(golem.getHealth(), event.getNewDamage()));
        }
    }

    /**
     * Forgives a golem the player's side has left at or under 25: target, persistent anger and the hit's memory are
     * cleared. In {@code Post}, not {@code Pre}: vanilla records the attacker after the damage lands
     * ({@code resolveMobResponsibleForDamage}, docs/api-notes.md §20), so a clear in {@code Pre} would be undone and
     * {@code HurtByTargetGoal} would make the golem mad again.
     *
     * @param event the post-damage event for any living entity
     */
    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof IronGolem golem
                && isPlayersSide(event.getSource())
                && GolemMercyRule.forgives(golem.getHealth())) {
            golem.stopBeingAngry();
        }
    }

    /**
     * A golem never targets a mob a player summoned (the summoner attachment): vanilla's golem attacks every
     * {@code Enemy} but creepers, and a summoned vex is one. A cancelled change keeps the golem's old target.
     *
     * @param event the change-target event for any living entity
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (event.getEntity() instanceof IronGolem && target != null && target.hasData(AllyAttachments.SUMMONER)) {
            event.setCanceled(true);
        }
    }

    /**
     * Whether the damage comes from a player's side: its causing entity is a player (a sword, or fangs through
     * {@code indirectMagic(fangs, caster)}), a tamed wolf (every tamed wolf is a player's, even while its owner is
     * offline and {@code getOwner()} cannot resolve it), or any mob carrying a summoner mark (round one's
     * vexes, Bound Vexes from Part 2 on). Lava, falls, other mobs and {@code /kill} are not.
     */
    private static boolean isPlayersSide(DamageSource source) {
        Entity cause = source.getEntity();
        return cause instanceof Player
                || (cause instanceof Wolf wolf && wolf.isTame())
                || (cause != null && cause.hasData(AllyAttachments.SUMMONER));
    }
}
