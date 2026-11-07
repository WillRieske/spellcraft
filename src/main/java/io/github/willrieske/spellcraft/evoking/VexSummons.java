package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.spell.Allies;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import io.github.willrieske.spellcraft.spell.rules.VexRing;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * The vex summon (spec §6): three vanilla vexes beside the summoner, marked with the summoner attachment, living
 * 30–120 s as the evoker's do. Their target goals are replaced whenever one joins a server level (freshly summoned
 * or loaded from disk): they ignore the summoner and the summoner's allies and attack anything else within five
 * blocks.
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class VexSummons {
    /** How far a summoned vex looks for prey: its {@code FOLLOW_RANGE}. */
    public static final double AGGRO_RANGE = 5.0;

    private static final int TARGET_GOAL_PRIORITY = 1;

    /**
     * The permanent FOLLOW_RANGE modifier {@code Mob.finalizeSpawn} adds ({@code Mob.RANDOM_SPAWN_BONUS_ID},
     * protected).
     */
    private static final Identifier RANDOM_SPAWN_BONUS_ID = Identifier.withDefaultNamespace("random_spawn_bonus");

    private VexSummons() {}

    /**
     * Summons the vexes beside {@code summoner}: random ring cells one block up, iron swords from
     * {@code finalizeSpawn}, the summoner's team if it has one.
     *
     * @param level the level to summon in
     * @param summoner the player who summons them
     * @return the vexes, in spawn order
     */
    public static List<Vex> summon(ServerLevel level, ServerPlayer summoner) {
        RandomSource random = level.getRandom();
        PlayerTeam team = summoner.getTeam();
        BlockPos feet = summoner.blockPosition();
        List<Vex> vexes = new ArrayList<>(VexRing.SUMMON_COUNT);
        for (int i = 0; i < VexRing.SUMMON_COUNT; i++) {
            VexRing.Offset offset = VexRing.offset(random.nextInt(VexRing.COUNT));
            BlockPos pos = feet.offset(offset.dx(), 1, offset.dz());
            Vex vex = EntityTypes.VEX.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (vex == null) {
                continue;
            }
            vex.snapTo(pos, 0.0F, 0.0F);
            vex.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
            vex.setBoundOrigin(pos);
            vex.setLimitedLife(VexRing.lifeTicks(random.nextInt(VexRing.LIFE_ROLL_BOUND)));
            vex.setData(AllyAttachments.SUMMONER, summoner.getUUID());
            if (team != null) {
                level.getScoreboard().addPlayerToTeam(vex.getScoreboardName(), team);
            }
            level.addFreshEntityWithPassengers(vex);
            vexes.add(vex);
        }
        return vexes;
    }

    /** Installs the summoned goals on every marked vex that joins a server level, loaded or fresh. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel
                && event.getEntity() instanceof Vex vex
                && vex.hasData(AllyAttachments.SUMMONER)) {
            install(vex);
        }
    }

    /**
     * Replaces the vex's target goals with one: the nearest living thing within {@link #AGGRO_RANGE} that is
     * attackable and no ally of the summoner. The range is set first so the goal's constructor sees five; later
     * scans re-read the attribute anyway. The vanilla random spawn bonus (±11.5% of the base, either way) is removed so the
     * effective range is exactly five. Only for a marked vex: on an unmarked one {@code getData} would create the
     * mark with its default, so the only caller, {@link #onJoin}, checks {@code hasData} first.
     */
    static void install(Vex vex) {
        vex.targetSelector.removeAllGoals(goal -> true);
        AttributeInstance range = vex.getAttribute(Attributes.FOLLOW_RANGE);
        if (range != null) {
            range.removeModifier(RANDOM_SPAWN_BONUS_ID);
            range.setBaseValue(AGGRO_RANGE);
        }
        UUID summoner = vex.getData(AllyAttachments.SUMMONER);
        vex.targetSelector.addGoal(
                TARGET_GOAL_PRIORITY,
                new NearestAttackableTargetGoal<>(
                        vex, LivingEntity.class, true, (target, level) -> isPrey(level, summoner, target)) {
                    // Vanilla re-checks only canAttack here, never the selector, so a target that became an ally
                    // (tamed, or marked by the summoner) after it was chosen would be kept. Re-check it.
                    @Override
                    public boolean canContinueToUse() {
                        LivingEntity current = mob.getTarget();
                        return super.canContinueToUse()
                                && current != null
                                && isPrey(getServerLevel(mob), summoner, current);
                    }
                });
    }

    /**
     * Whether a vex summoned by {@code summoner} may attack {@code target}: anything attackable that is no ally of
     * the summoner, no player the summoner cannot harm ({@code canHarmPlayer}: the {@code pvp} game rule and team
     * friendly-fire), and nothing while the summoner is not in {@code level} (offline or in another dimension). Public
     * for its GameTest in the {@code gametest} package.
     *
     * @param level the level the vex is in
     * @param summoner the UUID in the vex's summoner attachment
     * @param target the candidate target
     * @return whether the vex may take {@code target} as its target
     */
    public static boolean isPrey(ServerLevel level, UUID summoner, LivingEntity target) {
        if (!target.attackable()) {
            return false;
        }
        Player owner = level.getPlayerByUUID(summoner);
        if (owner == null || Allies.isAllyOf(owner, target)) {
            return false;
        }
        // The summoner's PvP rules apply to its vexes: the pvp gamerule and team friendly-fire.
        return !(target instanceof Player other) || owner.canHarmPlayer(other);
    }
}
