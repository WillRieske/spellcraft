package io.github.willrieske.spellcraft.effect;

/**
 * Marks an effect that milk and totems leave alone; only death and {@code /effect clear} remove it. 26.3 NeoForge
 * has no cure API (docs/api-notes.md §10), so {@code ClearAllStatusEffectsConsumeEffectMixin} honours this.
 */
public interface CureImmune {}
