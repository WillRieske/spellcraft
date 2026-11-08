package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.spell.rules.EvokerSpell;
import io.github.willrieske.spellcraft.spell.rules.EvokingCast;
import io.github.willrieske.spellcraft.spell.rules.EvokingCast.Readiness;
import io.github.willrieske.spellcraft.spell.rules.VexCap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Turns key presses into casts (spec §4; vex requests also check the cap of twelve, Evoking II spec §3.2): validates each request, keeps every player's pure {@link EvokingCast}
 * timers, and performs the pending spell on the player tick once its warmup has passed. The map is transient, like
 * {@code TrailLayer}'s trackers: dropped on logout and when the server stops, so a relog forgets cooldowns.
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class EvokingCaster {
    /** What a request did. Only {@code STARTED} starts a cast. */
    public enum Request {
        /** No Evoking, dead, or a spectator. */
        IGNORED,
        /** Another cast's arms are still up. */
        BUSY,
        /** This spell's interval has not passed. */
        COOLING,
        /** Fangs with nothing to bite: only the notice, one warmup later. */
        NO_TARGETS,
        /** Vexes on Peaceful, where the Bound Vex type cannot be created: only the notice, at once; no cast, no cooldown. */
        PEACEFUL,
        /** Vexes at the cap of twelve: only the notice, at once; no cast, no cooldown. */
        CAPPED,
        /** The cast started. */
        STARTED
    }

    /** Lang key of the action-bar notice. */
    public static final String NO_TARGETS_KEY = "message.spellcraft.no_targets";

    /** Lang key of the notice for a vex request on Peaceful. */
    public static final String PEACEFUL_KEY = "message.spellcraft.peaceful_vexes";

    /** Lang key of the notice for a vex request at the cap. */
    public static final String TOO_MANY_KEY = "message.spellcraft.too_many_vexes";

    /** One player's timers and, for a pending fang cast, the snapshot taken at the press. */
    private static final class Casting {
        final EvokingCast cast = new EvokingCast();
        Optional<Fangs.Snapshot> snapshot = Optional.empty();

        /** For a pending vex cast: how many to summon, decided at the press (spec §3.2). */
        int vexCount;
    }

    private static final Map<UUID, Casting> CASTINGS = new HashMap<>();

    private EvokingCaster() {}

    /**
     * Handles one key press for {@code spell} (spec §4): ignored without Evoking, while dead or as a spectator;
     * refused silently while busy or cooling; for vexes on Peaceful, where no Bound Vex can be created, sends the
     * notice at once and starts nothing; for vexes at the cap of twelve Bound Vexes, sends the too-many notice at once
     * and starts nothing; for fangs, scans for targets and schedules only the notice when there are none;
     * otherwise starts the cast (prepare sound, casting effect, timers).
     *
     * @param player the server-side player who pressed the key
     * @param spell the spell whose key was pressed
     * @return what the request did
     */
    public static Request request(ServerPlayer player, EvokerSpell spell) {
        if (!player.isAlive() || player.isSpectator() || !player.hasEffect(ModEffects.EVOKING)) {
            return Request.IGNORED;
        }
        ServerLevel level = player.level();
        long now = level.getGameTime();
        Casting casting = CASTINGS.computeIfAbsent(player.getUUID(), id -> new Casting());
        Readiness readiness = casting.cast.readiness(spell, now);
        if (readiness == Readiness.BUSY) {
            return Request.BUSY;
        }
        if (readiness == Readiness.COOLING) {
            return Request.COOLING;
        }
        if (spell == EvokerSpell.VEXES) {
            if (!ModEntities.BOUND_VEX.get().canSpawn(level)) {
                player.sendOverlayMessage(Component.translatable(PEACEFUL_KEY));
                return Request.PEACEFUL;
            }
            int toSummon = VexCap.toSummon(BoundVex.countOwnedBy(level.getServer(), player.getUUID()));
            if (toSummon == 0) {
                player.sendOverlayMessage(Component.translatable(TOO_MANY_KEY));
                return Request.CAPPED;
            }
            casting.vexCount = toSummon;
        }
        if (spell == EvokerSpell.FANGS) {
            Optional<Fangs.Snapshot> snapshot = Fangs.snapshot(level, player);
            if (snapshot.isEmpty()) {
                casting.cast.noTargets(now);
                return Request.NO_TARGETS;
            }
            casting.snapshot = snapshot;
        }
        casting.cast.start(spell, now);
        playSound(level, player, prepareSound(spell));
        player.addEffect(new MobEffectInstance(castingEffect(spell), spell.castingTicks(), 0, false, true, false));
        return Request.STARTED;
    }

    /**
     * Runs {@link #tick} after each player tick.
     *
     * @param event the post-tick event for a player
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tick(player);
        }
    }

    /**
     * One step for {@code player}: drops the entry without Evoking, else performs the pending spell or sends the
     * no-targets notice once due. Public so GameTests can step without waiting for a tick.
     *
     * @param player the server-side player to step
     */
    public static void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!player.hasEffect(ModEffects.EVOKING)) {
            CASTINGS.remove(id);
            return;
        }
        Casting casting = CASTINGS.get(id);
        if (casting == null) {
            return;
        }
        long now = player.level().getGameTime();
        casting.cast.due(now).ifPresent(due -> {
            switch (due.kind()) {
                case PERFORM -> perform(player, due.spell(), casting);
                case NOTIFY_NO_TARGETS -> {
                    if (player.isAlive()) {
                        player.sendOverlayMessage(Component.translatable(NO_TARGETS_KEY));
                    }
                }
            }
        });
    }

    /**
     * Drops the entry of a player who left.
     *
     * @param event the logout event
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CASTINGS.remove(event.getEntity().getUUID());
    }

    /**
     * Drops every entry when the server stops (single-player loads one server per world in the same JVM).
     *
     * @param event the server-stopped event
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CASTINGS.clear();
    }

    private static void perform(ServerPlayer player, EvokerSpell spell, Casting casting) {
        if (!player.isAlive()) {
            return;
        }
        ServerLevel level = player.level();
        boolean performed = switch (spell) {
            case FANGS -> {
                Optional<Fangs.Snapshot> snapshot = casting.snapshot;
                casting.snapshot = Optional.empty();
                yield snapshot.isPresent() && Fangs.perform(level, player, snapshot.get());
            }
            case VEXES -> {
                yield !VexSummons.summon(level, player, casting.vexCount).isEmpty();
            }
        };
        if (performed) {
            playSound(level, player, SoundEvents.EVOKER_CAST_SPELL);
        }
    }

    private static void playSound(ServerLevel level, ServerPlayer player, SoundEvent sound) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static SoundEvent prepareSound(EvokerSpell spell) {
        return switch (spell) {
            case FANGS -> SoundEvents.EVOKER_PREPARE_ATTACK;
            case VEXES -> SoundEvents.EVOKER_PREPARE_SUMMON;
        };
    }

    private static Holder<MobEffect> castingEffect(EvokerSpell spell) {
        return switch (spell) {
            case FANGS -> ModEffects.CASTING_FANGS;
            case VEXES -> ModEffects.CASTING_VEXES;
        };
    }
}
