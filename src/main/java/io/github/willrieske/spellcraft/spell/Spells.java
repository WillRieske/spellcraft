package io.github.willrieske.spellcraft.spell;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.config.ServerConfig;
import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.spell.rules.GhostrailDuration;
import io.github.willrieske.spellcraft.spell.rules.MirrorRule;
import io.github.willrieske.spellcraft.spell.rules.SpellDeclarations;
import java.util.List;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Binds each pure {@link SpellDeclarations} entry to its effect and duration.
 *
 * <p>Functional spec §1.1, §12.3, §13.1, §14.1, §16.4.
 */
public final class Spells {
    public static final Spell UPSIDE_DOWN = new Spell(
            Spellcraft.id(SpellDeclarations.UPSIDE_DOWN.name()),
            ModEffects.UPSIDE_DOWN,
            SpellDeclarations.UPSIDE_DOWN,
            ServerConfig::upsideDownDurationTicks);

    public static final Spell INFINITY = new Spell(
            Spellcraft.id(SpellDeclarations.INFINITY.name()),
            ModEffects.INFINITY,
            SpellDeclarations.INFINITY,
            Spell.INFINITE);

    public static final Spell TRAILING = new Spell(
            Spellcraft.id(SpellDeclarations.TRAILING.name()),
            ModEffects.TRAILING,
            SpellDeclarations.TRAILING,
            ServerConfig::trailingDurationTicks);

    // Functional spec §1.1
    public static final Spell EVOKING = new Spell(
            Spellcraft.id(SpellDeclarations.EVOKING.name()),
            ModEffects.EVOKING,
            SpellDeclarations.EVOKING,
            Spell.INFINITE);

    /** Mirror: thirteen images of the caster for six minutes. Functional spec §13.1. */
    public static final Spell MIRROR = new Spell(
            Spellcraft.id(SpellDeclarations.MIRROR.name()),
            ModEffects.MIRROR,
            SpellDeclarations.MIRROR,
            () -> MirrorRule.DURATION_TICKS);

    /**
     * Ghostrail: fly through walls, unhurt, for four minutes (a player's duration; other carriers pass their own).
     * Functional spec §14.1.
     */
    public static final Spell GHOSTRAIL = new Spell(
            Spellcraft.id(SpellDeclarations.GHOSTRAIL.name()),
            ModEffects.GHOSTRAIL,
            SpellDeclarations.GHOSTRAIL,
            () -> GhostrailDuration.PLAYER_TICKS);

    /** Every spell, in declaration order (creative tab, scribing). */
    public static final List<Spell> ALL = List.of(UPSIDE_DOWN, INFINITY, TRAILING, EVOKING, MIRROR, GHOSTRAIL);

    private Spells() {}

    /**
     * Puts {@code spell}'s effect on {@code target} for {@code ticks}: no particles, the icon shown. Particles are
     * hidden so other players see nothing unusual; the icon is shown. Adding an effect that is already active with a
     * longer duration refreshes it without a remove/add pair, so the client sees no gap. What a spell does when it
     * starts lives in its effect (Mirror's images in {@code MirrorEffect.onEffectAdded}), so a scroll, a Caster, a
     * mob, a ghost and {@code /effect give} behave alike. This method applies no rules: a scroll's rules (recast,
     * dispel) are {@code CastRules}'; other casters check "no effect yet" themselves and ask {@link #refusedFor}
     * first.
     *
     * @param target who gets the effect
     * @param spell the spell
     * @param ticks how long, or {@link Spell#INFINITE}'s value
     * @return whether the effect was added or lengthened
     */
    public static boolean cast(LivingEntity target, Spell spell, int ticks) {
        return target.addEffect(new MobEffectInstance(spell.effect(), ticks, 0, false, false, true));
    }

    /**
     * Whether a caster that is not a scroll (a Caster Vex, a villager ghost) must not cast {@code spell} on
     * {@code target}: the target is a creative or spectator player and the spell is redundant in creative (Ghostrail:
     * creative players already fly). The scroll's own refusal is {@code CastRules}'; this is the one predicate for
     * every other caster, so they cannot diverge. Functional spec §12.3, §16.4.
     *
     * @param target who would get the effect
     * @param spell the spell
     * @return true if the cast must be skipped
     */
    public static boolean refusedFor(LivingEntity target, Spell spell) {
        return target instanceof Player player
                && (player.isCreative() || player.isSpectator())
                && spell.rules().redundantInCreative();
    }
}
