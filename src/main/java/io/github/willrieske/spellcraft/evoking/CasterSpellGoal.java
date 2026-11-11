package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.spell.ModItems;
import io.github.willrieske.spellcraft.spell.Spell;
import io.github.willrieske.spellcraft.spell.Spells;
import io.github.willrieske.spellcraft.spell.rules.CasterRules;
import io.github.willrieske.spellcraft.spell.rules.GhostrailDuration;
import java.util.Comparator;
import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * A Caster casts a scroll on its summoner (Parts 6-7 spec §6.2): Ghostrail first when the summoner, or a sibling vex
 * within {@link CasterRules#RANGE} blocks, is in an emergency; else Mirror when the summoner is near, has none and at
 * least {@link CasterRules#MIRROR_HOSTILES} hostiles stand in the Caster's guard area. Each spell has its own
 * cooldown, saved on the vex. The cast takes {@link CasterRules#WIND_UP_TICKS} ticks, holding a conjured scroll of the
 * spell in the main hand (the given item is stashed) with the casting effect on; at the end the spell lands if the
 * subject still qualifies, and the scroll and the effect go at that moment. A flee, a rescue or a team move that
 * preempts the goal dismisses the scroll and spends nothing. Ghostrail is never cast on a creative or spectator
 * player ({@link Spells#refusedFor}). Priority 2, beside the job goals, and needs no target. A Caster that holds a
 * target may be far from its summoner (it flies to its ring point around the target); the range tests below then
 * simply find nothing to cast on, and nothing here assumes the two stay close.
 */
final class CasterSpellGoal extends Goal {
    /** The Caster this goal belongs to. */
    private final BoundVex vex;

    /** The spell chosen for the cast in progress, or null. */
    private @Nullable Spell spell;

    /** Who the cast in progress is for, or null. */
    private @Nullable LivingEntity subject;

    /** Ticks into the wind-up. */
    private int ticks;

    /** Whether the cast has landed or been given up, so the goal can end. */
    private boolean done;

    /** Whether the scroll has been dismissed for this cast, so it is dismissed at most once. */
    private boolean dismissed;

    /**
     * Creates the goal for one vex (it only runs while the vex {@code isCaster()}).
     *
     * @param vex the vex
     */
    CasterSpellGoal(BoundVex vex) {
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return vex.isCaster() && vex.scanDue(vex.scanInterval()) && choose();
    }

    @Override
    public boolean canContinueToUse() {
        return !done && subject != null && subject.isAlive();
    }

    @Override
    public void start() {
        ticks = 0;
        done = false;
        dismissed = false;
        if (spell == null) {
            return;
        }
        vex.conjure(EquipmentSlot.MAINHAND, ModItems.scrollFor(spell));
        ScrollCasting.windUp(vex);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (subject != null) {
            TeamFlight.lookAt(vex, subject);
        }
        if (++ticks >= CasterRules.WIND_UP_TICKS) {
            finish();
        }
    }

    /**
     * The end of the wind-up: cast if the subject still qualifies, then start the cooldown; either way the goal ends.
     * The scroll and the casting effect go here, not only in {@link #stop()}: the goal selector calls {@code stop()}
     * one or two ticks after {@code done} is set (only on even {@code tickCount + id} ticks), and a test or a player
     * watching right after the spell lands must not see a scroll still in hand.
     */
    private void finish() {
        done = true;
        LivingEntity target = subject;
        Spell chosen = spell;
        if (target != null && chosen != null && stillQualifies(target, chosen)) {
            GhostrailDuration.Kind kind =
                    target instanceof Player ? GhostrailDuration.Kind.PLAYER : GhostrailDuration.Kind.BOUND_VEX;
            if (ScrollCasting.land(vex, target, chosen, kind)) {
                long readyAt = vex.level().getGameTime() + ScrollCasting.cooldownFor(chosen);
                if (chosen.equals(Spells.MIRROR)) {
                    vex.setMirrorReadyAt(readyAt);
                } else {
                    vex.setGhostrailReadyAt(readyAt);
                }
            }
        }
        dismissScroll();
    }

    /** Whether {@code target} is still worth the spell at the end of the wind-up. */
    private boolean stillQualifies(LivingEntity target, Spell chosen) {
        return target.isAlive()
                && target.level().equals(vex.level())
                && target.distanceTo(vex) <= CasterRules.RANGE
                && !target.hasEffect(chosen.effect());
    }

    /** Takes the conjured scroll out of the main hand (the stashed item comes back) and ends the casting look. */
    private void dismissScroll() {
        if (dismissed) {
            return;
        }
        dismissed = true;
        vex.dismiss(EquipmentSlot.MAINHAND);
        ScrollCasting.windDown(vex);
    }

    @Override
    public void stop() {
        // The normal end already dismissed the scroll in finish() (dismissScroll runs once); an interruption comes here
        dismissScroll();
        spell = null;
        subject = null;
    }

    /** Picks the spell and subject for this scan, or false when nothing is worth casting. */
    private boolean choose() {
        spell = null;
        subject = null;
        ServerPlayer owner = vex.summoner().orElse(null);
        if (owner == null) {
            return false;
        }
        long now = vex.level().getGameTime();
        if (CasterRules.ready(now, vex.ghostrailReadyAt())) {
            LivingEntity hurt = emergency(owner);
            if (hurt != null) {
                spell = Spells.GHOSTRAIL;
                subject = hurt;
                return true;
            }
        }
        if (CasterRules.ready(now, vex.mirrorReadyAt())
                && owner.distanceTo(vex) <= CasterRules.RANGE
                && !owner.hasEffect(ModEffects.MIRROR)
                && CasterRules.wantsMirror(hostilesInGuardArea())) {
            spell = Spells.MIRROR;
            subject = owner;
            return true;
        }
        return false;
    }

    /**
     * The summoner if it needs Ghostrail (and {@link Spells#refusedFor} does not forbid it: a creative or spectator
     * summoner flies already), else the neediest sibling vex in range, else null.
     */
    private @Nullable LivingEntity emergency(ServerPlayer owner) {
        if (owner.distanceTo(vex) <= CasterRules.RANGE
                && !owner.hasEffect(ModEffects.GHOSTRAIL)
                && !Spells.refusedFor(owner, Spells.GHOSTRAIL)
                && CasterRules.wantsGhostrail(owner.getHealth(), owner.getMaxHealth(), owner.isInWall(), true)) {
            return owner;
        }
        return vex
                .nearby(
                        BoundVex.class,
                        CasterRules.RANGE,
                        other -> !other.equals(vex)
                                && other.isSummonedBy(owner.getUUID())
                                && !other.hasEffect(ModEffects.GHOSTRAIL)
                                && CasterRules.wantsGhostrail(other.getHealth(), other.getMaxHealth(), false, false))
                .stream()
                .min(Comparator.comparingDouble(other -> other.getHealth() / other.getMaxHealth()))
                .orElse(null);
    }

    /**
     * How many hostiles this vex may hunt stand in its guard area. The broad phase is {@code BoundVex.guardArea()},
     * which is the stay box around the post for a staying Caster (13 x 13 x 7) and the box around the vex otherwise;
     * a box inflated by a fixed amount around the vex would miss hostiles 5 to 6.5 blocks out in a stay box.
     */
    private int hostilesInGuardArea() {
        return vex.level()
                .getEntitiesOfClass(
                        LivingEntity.class,
                        vex.guardArea(),
                        other -> other.isAlive() && vex.inGuardArea(other) && vex.huntable(other) && vex.isPrey(other))
                .size();
    }
}
