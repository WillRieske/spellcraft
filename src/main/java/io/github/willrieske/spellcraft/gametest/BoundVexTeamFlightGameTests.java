package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.foe;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.hangPoint;

import io.github.willrieske.spellcraft.evoking.TeamFlight;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.Vec3;

/** GameTests of the team moves' flight helpers (Parts 6-7 spec section 4). */
final class BoundVexTeamFlightGameTests {
    private BoundVexTeamFlightGameTests() {}

    /** Registers the tests. */
    static void registerAll() {
        FangGameTests.addPadded(SHARED, "team_move_shield_flight_math", BoundVexTeamFlightGameTests::flightMath);
    }

    /**
     * When two overlapping boxes and two boxes 3 blocks apart are measured, should {@code boxGap} give 0 and the plain
     * gap, and {@code ringPoint} from straight above the center give due east.
     *
     * <p>Method under test: {@link TeamFlight#boxGap}, {@link TeamFlight#ringPoint}: the documented edge cases
     */
    static void flightMath(GameTestHelper helper) {
        // Arrange
        Vec3 at = hangPoint(helper);
        Husk a = foe(helper, EntityTypes.HUSK, at);
        Husk overlapping = foe(helper, EntityTypes.HUSK, at.add(0.2, 0.0, 0.0));
        Husk apart = foe(helper, EntityTypes.HUSK, at.add(3.0 + a.getBbWidth(), 0.0, 0.0));

        // Act & Assert
        helper.assertValueEqual(TeamFlight.boxGap(a, overlapping), 0.0, "overlapping boxes touch");
        helper.assertTrue(
                Math.abs(TeamFlight.boxGap(a, apart) - 3.0) < 1.0E-6,
                "boxes 3 blocks apart, less nothing: " + TeamFlight.boxGap(a, apart));
        Vec3 east = TeamFlight.ringPoint(at, at.add(0.0, 5.0, 0.0), 6.0, at.y);
        helper.assertTrue(
                Math.abs(east.x - (at.x + 6.0)) < 1.0E-9 && Math.abs(east.z - at.z) < 1.0E-9,
                "straight above the center falls back to due east: " + east);
        helper.succeed();
    }
}
