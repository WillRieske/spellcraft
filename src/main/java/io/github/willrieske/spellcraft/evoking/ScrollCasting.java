package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.spell.Spell;
import io.github.willrieske.spellcraft.spell.Spells;
import io.github.willrieske.spellcraft.spell.rules.CasterRules;
import io.github.willrieske.spellcraft.spell.rules.GhostrailDuration;
import io.github.willrieske.spellcraft.spell.rules.MirrorRule;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * What every cast that is not a player's hand shares (Parts 6-7 spec §6.2, §8, §9): how long a spell lasts for each
 * kind of subject, how long its cooldown is, the wind-up's look and sounds, and the landing. A Caster Vex, a scroll
 * mob and a villager ghost call these, so a number or a sound changes in one place.
 *
 * <p>Functional spec §12.5, §15.2, §16.4.
 */
public final class ScrollCasting {
    private ScrollCasting() {}

    /**
     * Ticks {@code spell} lasts on a subject of {@code kind}: Mirror always {@link MirrorRule#DURATION_TICKS},
     * Ghostrail {@link GhostrailDuration#of}.
     *
     * @param spell Mirror or Ghostrail
     * @param kind what carries the effect
     * @return the duration in ticks
     */
    public static int ticksFor(Spell spell, GhostrailDuration.Kind kind) {
        return spell.equals(Spells.GHOSTRAIL) ? GhostrailDuration.of(kind) : MirrorRule.DURATION_TICKS;
    }

    /**
     * Ticks before the same caster may cast {@code spell} again (a Caster Vex and a villager ghost; a scroll mob's
     * Ghostrail has its own shorter one).
     *
     * @param spell Mirror or Ghostrail
     * @return the cooldown in ticks
     */
    public static long cooldownFor(Spell spell) {
        return spell.equals(Spells.GHOSTRAIL)
                ? CasterRules.GHOSTRAIL_COOLDOWN_TICKS
                : CasterRules.MIRROR_COOLDOWN_TICKS;
    }

    /**
     * Starts a wind-up on {@code caster}: the casting effect (visible particles, no icon) for
     * {@link CasterRules#WIND_UP_TICKS} and the prepare sound.
     *
     * @param caster whoever winds up
     */
    public static void windUp(LivingEntity caster) {
        caster.addEffect(
                new MobEffectInstance(ModEffects.CASTING_SCROLL, CasterRules.WIND_UP_TICKS, 0, false, true, false));
        caster.playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.0F, 1.0F);
    }

    /**
     * Ends the wind-up's look.
     *
     * @param caster whoever wound up
     */
    public static void windDown(LivingEntity caster) {
        caster.removeEffect(ModEffects.CASTING_SCROLL);
    }

    /**
     * Lands {@code spell} on {@code subject}: refused for a creative or spectator player when the spell is redundant
     * there ({@link Spells#refusedFor}); otherwise cast for {@link #ticksFor}, with the cast sound from the caster
     * when the effect was added.
     *
     * @param caster whoever casts (the sound comes from it)
     * @param subject who gets the effect
     * @param spell Mirror or Ghostrail
     * @param kind what the subject is, for Ghostrail's duration
     * @return whether the effect was added
     */
    public static boolean land(LivingEntity caster, LivingEntity subject, Spell spell, GhostrailDuration.Kind kind) {
        if (Spells.refusedFor(subject, spell) || !Spells.cast(subject, spell, ticksFor(spell, kind))) {
            return false;
        }
        caster.playSound(SoundEvents.EVOKER_CAST_SPELL, 1.0F, 1.0F);
        return true;
    }
}
