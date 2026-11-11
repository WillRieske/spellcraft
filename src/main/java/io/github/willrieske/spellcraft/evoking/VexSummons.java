package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.spell.AllyAttachments;
import io.github.willrieske.spellcraft.spell.rules.CasterRules;
import io.github.willrieske.spellcraft.spell.rules.VexRing;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.scores.PlayerTeam;

/**
 * The vex summon (Evoking spec §6, Evoking II §3.1–§3.2): Bound Vexes beside the summoner, marked with the
 * summoner attachment, with no life limit. How many is the caster's call ({@code VexCap}); the goals are the mob's
 * own.
 */
public final class VexSummons {
    private VexSummons() {}

    /**
     * A whole batch ({@link VexRing#SUMMON_COUNT}) beside {@code summoner}.
     *
     * @param level the level to summon in
     * @param summoner the player who summons them
     * @return the vexes, in spawn order
     */
    public static List<BoundVex> summon(ServerLevel level, ServerPlayer summoner) {
        return summon(level, summoner, VexRing.SUMMON_COUNT);
    }

    /**
     * Summons {@code count} Bound Vexes beside {@code summoner}: random ring cells one block up, iron swords from
     * {@code finalizeSpawn}, the summoner's team if it has one, the summoner mark, a bound origin at the cell.
     *
     * @param level the level to summon in
     * @param summoner the player who summons them
     * @param count how many; {@code EvokingCaster} takes it from {@code VexCap.toSummon} at the press
     * @return the vexes, in spawn order; empty on Peaceful, where the type cannot be created
     *     ({@code EvokingCaster.request} refuses first, so this is only a guard for another caller)
     */
    public static List<BoundVex> summon(ServerLevel level, ServerPlayer summoner, int count) {
        RandomSource random = level.getRandom();
        PlayerTeam team = summoner.getTeam();
        BlockPos feet = summoner.blockPosition();
        List<BoundVex> vexes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            VexRing.Offset offset = VexRing.offset(random.nextInt(VexRing.COUNT));
            BlockPos pos = feet.offset(offset.dx(), 1, offset.dz());
            BoundVex vex = ModEntities.BOUND_VEX.get().create(level, EntitySpawnReason.MOB_SUMMONED);
            if (vex == null) {
                continue;
            }
            vex.snapTo(pos, 0.0F, 0.0F);
            vex.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
            vex.setBoundOrigin(pos);
            if (Dice.CASTER_ROLL.below(random, CasterRules.CASTER_ONE_IN) == 0) {
                vex.setCaster(true);
            }
            vex.setData(AllyAttachments.SUMMONER, summoner.getUUID());
            if (team != null) {
                level.getScoreboard().addPlayerToTeam(vex.getScoreboardName(), team);
            }
            level.addFreshEntityWithPassengers(vex);
            vexes.add(vex);
        }
        return vexes;
    }
}
