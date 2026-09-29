package io.github.willrieske.spellcraft.spell;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A reusable scroll that casts one {@link Spell}. Right-click casts (recasting resets the timer);
 * sneak + right-click while the spell is active dispels it. The server decides; the client only
 * plays the swing.
 */
public class SpellScrollItem extends Item {
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
        if (!level.isClientSide()) {
            if (player.isShiftKeyDown() && player.hasEffect(spell.effect())) {
                player.removeEffect(spell.effect());
            } else {
                cast(player, player.getItemInHand(hand));
            }
        }
        return InteractionResult.SUCCESS;
    }

    private void cast(Player player, ItemStack scroll) {
        // Particles hidden (visible = false): other players see nothing unusual. Icon shown.
        // addEffect on an active effect with a longer duration refreshes it without a
        // remove/add pair, so the client sees no gap and stays flipped.
        player.addEffect(
                new MobEffectInstance(spell.effect(), spell.durationTicks().getAsInt(), 0, false, false, true));
        // Every cast, including a recast of an active spell, starts the scroll's cooldown; the
        // default is 0, so it only bites when the server config sets one.
        int cooldown = spell.cooldownTicks().getAsInt();
        if (cooldown > 0) {
            player.getCooldowns().addCooldown(scroll, cooldown);
        }
    }
}
