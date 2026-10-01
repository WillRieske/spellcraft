package io.github.willrieske.spellcraft.spell.rules;

import java.util.List;
import java.util.Objects;

/**
 * Scribing's draw (spec §5): roll the option's top-tier chance, then pick evenly within the chosen pool. An
 * empty pool falls back to the other one.
 */
public final class ScrollDraw {
    /** The enchanting table's three options; option {@code n} costs {@code n + 1} lapis and levels. */
    public static final int OPTIONS = 3;

    private static final int PERCENT = 100;

    /** A source of rolls; {@code below(bound)} returns a value in {@code [0, bound)}. */
    @FunctionalInterface
    public interface Dice {
        int below(int bound);
    }

    public record Candidate<S>(S spell, SpellTier tier) {
        public Candidate {
            Objects.requireNonNull(spell, "spell");
            Objects.requireNonNull(tier, "tier");
        }
    }

    private ScrollDraw() {}

    public static <S> void requireCandidates(List<Candidate<S>> candidates) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("scribing needs at least one spell to draw from");
        }
    }

    public static void requireChances(List<Integer> topTierChancePercent) {
        if (topTierChancePercent.size() != OPTIONS) {
            throw new IllegalArgumentException(
                    "expected " + OPTIONS + " top-tier chances, got " + topTierChancePercent.size());
        }
        for (int chance : topTierChancePercent) {
            if (chance < 0 || chance > PERCENT) {
                throw new IllegalArgumentException("top-tier chance must be 0-100, got " + chance);
            }
        }
    }

    /** Rolls against {@code topTierChancePercent[option]}, then picks evenly within the chosen pool (spec §5). */
    public static <S> S draw(int option, List<Candidate<S>> candidates, List<Integer> topTierChancePercent, Dice dice) {
        requireCandidates(candidates);
        requireChances(topTierChancePercent);
        if (option < 0 || option >= OPTIONS) {
            throw new IllegalArgumentException("option must be 0-" + (OPTIONS - 1) + ", got " + option);
        }
        List<S> top = candidates.stream()
                .filter(c -> c.tier().isTop())
                .map(Candidate::spell)
                .toList();
        List<S> rest = candidates.stream()
                .filter(c -> !c.tier().isTop())
                .map(Candidate::spell)
                .toList();
        boolean wantTop = dice.below(PERCENT) < topTierChancePercent.get(option);
        List<S> pool = wantTop ? (top.isEmpty() ? rest : top) : (rest.isEmpty() ? top : rest);
        return pool.get(dice.below(pool.size()));
    }
}
