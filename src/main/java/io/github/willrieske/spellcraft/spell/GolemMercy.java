package io.github.willrieske.spellcraft.spell;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.spell.rules.GolemMercyRule;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Golem mercy (Evoking II spec §2.3): a player's side (any player, their fangs, a wolf a player owns, a mob a player
 * summoned) never takes an iron golem under {@link GolemMercyRule#MERCY_HEALTH}, a golem left there forgives, and a
 * golem never takes a player's summon, nor a zombie villager a Bound Vex is curing (the cure mark), as its target. The
 * rule is {@link GolemMercyRule}; this class is the three event hooks. A golem already fighting a zombie villager when
 * a vex claims it is told to stop by the cure itself ({@code CureZombieVillagerGoal}), because a canceled change only
 * keeps the old target.
 *
 * <p>Functional spec §4.4, §4.5, §4.6.
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class GolemMercy {
    private GolemMercy() {}

    /**
     * Clamps player-side damage so the golem keeps at least 25 health. Runs after armor and magic reductions, on the
     * value that would reach health. Functional spec §4.4.
     *
     * @param event the pre-damage event for any living entity
     */
    @SubscribeEvent
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof IronGolem golem && Allies.isPlayersSide(event.getSource())) {
            event.setNewDamage(GolemMercyRule.clamp(golem.getHealth(), event.getNewDamage()));
        }
    }

    /**
     * Forgives a golem the player's side has left at or under 25: target, persistent anger and the hit's memory are
     * cleared. In {@code Post}, not {@code Pre}: vanilla records the attacker after the damage lands
     * ({@code resolveMobResponsibleForDamage}, docs/api-notes.md §20), so a clear in {@code Pre} would be undone and
     * {@code HurtByTargetGoal} would make the golem mad again. Functional spec §4.5.
     *
     * @param event the post-damage event for any living entity
     */
    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof IronGolem golem
                && Allies.isPlayersSide(event.getSource())
                && GolemMercyRule.forgives(golem.getHealth())) {
            golem.stopBeingAngry();
        }
    }

    /**
     * A golem never targets a mob a player summoned (the summoner attachment) nor a zombie villager a Bound Vex
     * is curing (the cure mark, Parts 4-5 spec §2.1): vanilla's golem attacks every {@code Enemy} but creepers, and a
     * summoned vex or a zombie villager is one. A canceled change keeps the golem's old target. Functional spec §4.6.
     *
     * @param event the change-target event for any living entity
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (event.getEntity() instanceof IronGolem
                && target != null
                && (target.hasData(AllyAttachments.SUMMONER) || AllyAttachments.isBeingCured(target))) {
            event.setCanceled(true);
        }
    }
}
