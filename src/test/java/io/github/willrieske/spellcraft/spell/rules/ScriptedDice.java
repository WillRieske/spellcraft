package io.github.willrieske.spellcraft.spell.rules;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/** A {@link ScrollDraw.Dice} that returns scripted rolls in order and checks each is in range. */
final class ScriptedDice implements ScrollDraw.Dice {
    private final Deque<Integer> rolls;

    ScriptedDice(Integer... rolls) {
        this.rolls = new ArrayDeque<>(List.of(rolls));
    }

    @Override
    public int below(int bound) {
        int roll = Objects.requireNonNull(rolls.poll(), "dice script exhausted");
        if (roll < 0 || roll >= bound) {
            throw new IllegalStateException("scripted roll " + roll + " outside [0, " + bound + ")");
        }
        return roll;
    }

    boolean exhausted() {
        return rolls.isEmpty();
    }
}
