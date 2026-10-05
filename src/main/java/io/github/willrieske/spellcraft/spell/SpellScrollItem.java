package io.github.willrieske.spellcraft.spell;

import io.github.willrieske.spellcraft.spell.rules.CastOutcome;
import io.github.willrieske.spellcraft.spell.rules.CastRules;
import io.github.willrieske.spellcraft.spell.rules.Caster;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * A single-use scroll for one {@link Spell}. Every use goes through {@link CastRules}; both sides decide (so a
 * refusal shows no swing), and only the server changes state.
 */
public class SpellScrollItem extends Item {
    public static final String REFUSED_KEY = "message.spellcraft.scroll_not_needed";

    private final Spell spell;

    public SpellScrollItem(Spell spell, Item.Properties properties) {
        super(properties);
        this.spell = spell;
    }

    public Spell spell() {
        return spell;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        CastOutcome outcome = CastRules.decide(
                spell.rules(),
                new Caster(player.isShiftKeyDown(), player.hasEffect(spell.effect()), PlayerModes.of(player)));
        if (!outcome.consumesScroll()) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable(REFUSED_KEY));
            }
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel serverLevel) {
            apply(outcome, player);
            // shrink, not consume: consume skips players with instabuild (creative, Infinity), and a scroll is
            // single-use for everyone.
            player.getItemInHand(hand).shrink(1);
            ScrollFx.wither(serverLevel, player);
        }
        return InteractionResult.SUCCESS;
    }

    private void apply(CastOutcome outcome, Player player) {
        switch (outcome) {
            // Particles hidden (visible = false): other players see nothing unusual; icon shown. addEffect on an
            // active effect with a longer duration refreshes it without a remove/add pair, so the client sees
            // no gap.
            case CAST, REFRESH ->
                player.addEffect(new MobEffectInstance(
                        spell.effect(), spell.durationTicks().getAsInt(), 0, false, false, true));
            case DISPEL -> player.removeEffect(spell.effect());
            case REFUSE -> throw new IllegalStateException("REFUSE keeps the scroll and never reaches apply");
        }
    }
}
