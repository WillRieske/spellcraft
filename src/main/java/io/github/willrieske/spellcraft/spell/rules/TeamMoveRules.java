package io.github.willrieske.spellcraft.spell.rules;

/**
 * The numbers and choices of the team moves (Parts 6–7 spec §2.2, §2.3, §2.5, §4): how many free vexes a
 * move takes, which enemy earns which move, when a move times out, and the two shapes the vexes fly in.
 * Pure: the Minecraft side reads the world and asks.
 *
 * <p>Functional spec §11.3, §11.4, §11.6, §11.10, §11.11, §11.12.
 */
public final class TeamMoveRules {
    /** An enemy with at least this much maximum health earns a Raid. Functional spec §11.4. */
    public static final double RAID_HEALTH = 99.0;

    /** An enemy with at least this much maximum health earns a Shield. Functional spec §11.4. */
    public static final double SHIELD_HEALTH = 31.0;

    /** An enemy with at least this much attack damage that hunts a vex earns a Multishot. Functional spec §11.4. */
    public static final double MULTISHOT_DAMAGE = 6.0;

    /** The Raid takes every vex there can be. Functional spec §11.3. */
    public static final int RAID_SIZE = 12;

    /** Ticks a Shield, Multishot or Raid lasts at most (120 s). Functional spec §11.6. */
    public static final int LIMIT_TICKS = 2400;

    /** Ticks an Evader lasts at most (60 s). Functional spec §11.6. */
    public static final int EVADER_LIMIT_TICKS = 1200;

    /** Ticks a move kind cannot start again after it ends (30 s). Functional spec §11.6. */
    public static final int COOLDOWN_TICKS = 600;

    /** A move ends when its enemy or ward is farther than this from the summoner. Functional spec §11.6. */
    public static final double END_DISTANCE = 48.0;

    private static final int SHIELD_SMALL = 3;
    private static final int SHIELD_LARGE = 9;
    private static final int MULTISHOT_SIZE = 3;
    private static final int EVADER_MAX = 3;
    private static final int GRID_COLUMNS = 4;
    private static final int GRID_CELLS = 12;
    private static final double GRID_SPACING = 1.5;
    private static final double ORBIT_DEGREES_PER_TICK = 6.0;
    private static final double FULL_TURN = 360.0;

    private TeamMoveRules() {}

    /**
     * How many vexes a move takes (spec §2.2). Functional spec §11.3.
     *
     * @param move the move
     * @param free how many free vexes the summoner has (for Multishot, counting the targeted one)
     * @return the roster size, or 0 when the move cannot start
     */
    public static int rosterSize(TeamMove move, int free) {
        return switch (move) {
            case SHIELD -> free >= SHIELD_LARGE ? SHIELD_LARGE : free >= SHIELD_SMALL ? SHIELD_SMALL : 0;
            case MULTISHOT -> free >= MULTISHOT_SIZE ? MULTISHOT_SIZE : 0;
            case EVADER -> Math.clamp(free, 0, EVADER_MAX);
            case RAID -> free >= RAID_SIZE ? RAID_SIZE : 0;
            case NONE -> 0;
        };
    }

    /**
     * Whether an enemy is one a move is fought against (spec §2.3, §3.4). Functional spec §11.4, §11.12.
     *
     * @param move the move
     * @param maxHealth the enemy's maximum health
     * @param attackDamage the enemy's attack damage, 0 when it has none
     * @return true if the enemy fits; always false for Evader (it has a ward, not an enemy) and None
     */
    public static boolean fits(TeamMove move, double maxHealth, double attackDamage) {
        return switch (move) {
            case SHIELD -> maxHealth >= SHIELD_HEALTH;
            case RAID -> maxHealth >= RAID_HEALTH;
            case MULTISHOT -> attackDamage >= MULTISHOT_DAMAGE;
            case EVADER, NONE -> false;
        };
    }

    /**
     * Whether a summoner or ally is hurt enough for an Evader guard: below half health (spec §2.3). Functional spec
     * §11.4, §11.6.
     *
     * @param health the current health
     * @param maxHealth the maximum health
     * @return true below half; false for NaN
     */
    public static boolean needsGuard(float health, float maxHealth) {
        return health < maxHealth / 2.0F;
    }

    /**
     * The enemy-side order of the automatic triggers (spec §2.3): Raid, then Shield, then Multishot. An enemy that
     * earns a Raid but cannot start one (fewer than 12 free vexes, or the Raid cooling) falls through to a Shield.
     * Functional spec §11.4.
     *
     * @param maxHealth the enemy's maximum health
     * @param attackDamage the enemy's attack damage, 0 when it has none
     * @param targetsFreeVex whether the enemy's target is a free Bound Vex of the summoner
     * @param raidReady whether a Raid could start: 12 free vexes and not cooling
     * @param shieldReady whether a Shield could start: at least 3 free vexes and not cooling
     * @param multishotReady whether a Multishot could start: at least 3 free vexes and not cooling
     * @return the move to start, or {@link TeamMove#NONE}
     */
    public static TeamMove pick(
            double maxHealth,
            double attackDamage,
            boolean targetsFreeVex,
            boolean raidReady,
            boolean shieldReady,
            boolean multishotReady) {
        if (raidReady && fits(TeamMove.RAID, maxHealth, attackDamage)) {
            return TeamMove.RAID;
        }
        if (shieldReady && fits(TeamMove.SHIELD, maxHealth, attackDamage)) {
            return TeamMove.SHIELD;
        }
        if (multishotReady && targetsFreeVex && fits(TeamMove.MULTISHOT, maxHealth, attackDamage)) {
            return TeamMove.MULTISHOT;
        }
        return TeamMove.NONE;
    }

    /**
     * The game time a move runs out (spec §2.5). Functional spec §11.6.
     *
     * @param move the move
     * @param startTick the game time it started
     * @return the time it ends at: 120 s later, Evader 60 s; a move that is none ends at once
     */
    public static long endsAt(TeamMove move, long startTick) {
        return switch (move) {
            case EVADER -> startTick + EVADER_LIMIT_TICKS;
            case SHIELD, MULTISHOT, RAID -> startTick + LIMIT_TICKS;
            case NONE -> startTick;
        };
    }

    /**
     * Whether an enemy or ward is too far from the summoner for the move to go on (spec §2.5). Functional spec §11.6.
     *
     * @param distanceSqr the squared distance
     * @return true beyond {@link #END_DISTANCE}
     */
    public static boolean tooFar(double distanceSqr) {
        return distanceSqr > END_DISTANCE * END_DISTANCE;
    }

    /**
     * Where a Raid member stands across the grid (spec §4.4): 4 columns, 1.5 blocks apart, centered. Functional spec
     * §11.11.
     *
     * @param cell the member's cell, 0 to 11 in roster order
     * @return the sideways offset from the grid's center line, -2.25 to 2.25
     * @throws IllegalArgumentException for a cell outside 0 to 11
     */
    public static double gridAcross(int cell) {
        requireCell(cell);
        return (cell % GRID_COLUMNS - (GRID_COLUMNS - 1) / 2.0) * GRID_SPACING;
    }

    /**
     * Where a Raid member stands along the grid's depth (spec §4.4): 3 rows, 1.5 blocks apart; row 0 is the front
     * row, nearest the enemy. Functional spec §11.11.
     *
     * @param cell the member's cell, 0 to 11 in roster order
     * @return the offset from the grid's center, toward the summoner: -1.5, 0 or 1.5
     * @throws IllegalArgumentException for a cell outside 0 to 11
     */
    public static double gridDepth(int cell) {
        requireCell(cell);
        return (cell / GRID_COLUMNS - 1) * GRID_SPACING;
    }

    private static void requireCell(int cell) {
        if (cell < 0 || cell >= GRID_CELLS) {
            throw new IllegalArgumentException("cell " + cell + " is outside 0.." + (GRID_CELLS - 1));
        }
    }

    /**
     * The angle an Evader member circles at (spec §4.3): the members spread evenly and the circle turns 6° a tick.
     * Functional spec §11.10.
     *
     * @param slot the member's place in the roster
     * @param count how many members there are
     * @param ticksSinceStart ticks since the move began
     * @return the angle in degrees, in {@code [0, 360)}
     * @throws IllegalArgumentException if {@code count} is not positive, or {@code slot} is negative or not below
     *     {@code count}
     */
    public static double orbitDegrees(int slot, int count, long ticksSinceStart) {
        if (count <= 0 || slot < 0 || slot >= count) {
            throw new IllegalArgumentException(
                    "slot " + slot + " is outside 0.." + (count - 1) + " (count " + count + ")");
        }
        double degrees = FULL_TURN * slot / count + ORBIT_DEGREES_PER_TICK * ticksSinceStart;
        return degrees - FULL_TURN * Math.floor(degrees / FULL_TURN);
    }
}
