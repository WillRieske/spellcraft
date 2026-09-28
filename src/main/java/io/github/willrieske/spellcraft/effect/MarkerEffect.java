package io.github.willrieske.spellcraft.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** A status effect with no tick behaviour: its presence is the whole point. Milk-curable (vanilla default). */
public class MarkerEffect extends MobEffect {
    public MarkerEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
