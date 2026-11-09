package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.spell.Allies;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Bound Vex repairs a hurt ally golem (Evoking II spec §3.7): on each scan ({@code BoundVex.scanDue}), when the vex
 * is idle (no target) and its summoner is here, the nearest iron golem whose feet are in the guard area, that is an
 * ally (so: not mad), under full health and not claimed by another living Bound Vex is taken. The vex flies to within
 * {@link #REACH} of the golem's chest, conjures an iron ingot there, and every {@link #TAP_INTERVAL} ticks swings,
 * plays the repair sound and heals {@link #HEAL} (vanilla's ingot amount). When the golem is full, or the repair
 * stops for any reason (a target, the summoner gone, the golem out of the area or mad, flee or dodge taking over),
 * the ingot goes and the item the vex held comes back. The held item lives in this goal until Part 3's stash: a vex
 * unloaded, saved and reloaded, or carried through a portal while repairing keeps the ingot (a known gap, on the
 * playtest checklist). Repair outranks follow, so a summoner who walks off waits until the golem is full: at most four
 * taps (a golem under 25 health needs them all), which is 4.5 seconds (90 ticks) after the vex reaches the golem, plus
 * the flight there.
 */
final class RepairGolemGoal extends Goal {
    /** Health per tap: vanilla's iron-ingot repair. */
    static final float HEAL = 25.0F;

    /** Ticks between taps. */
    static final int TAP_INTERVAL = 30;

    /** How near the golem's chest the vex taps from. */
    static final double REACH = 1.5;

    private static final double CHEST_HEIGHT = 1.4;
    private static final double SPEED = 1.0;

    /**
     * Golems under repair: golem UUID to the claiming vex, so two vexes never share one (spec §3.7). A claim whose vex
     * is dead or removed is free, because a vex that dies, despawns or is unloaded never runs {@code stop()}. Cleared
     * when the server stops. The prune in {@code canUse} only frees memory, and the by-UUID condition in {@code stop}
     * (a stop never removes another vex's claim) can only matter if a claim were taken over: both are defensive and
     * untested, since no test can tell a claim map without them apart.
     */
    private static final Map<UUID, BoundVex> CLAIMED = new HashMap<>();

    private final BoundVex vex;

    @Nullable
    private IronGolem golem;

    private ItemStack held = ItemStack.EMPTY;
    private boolean conjured;
    private int ticksSinceTap;

    RepairGolemGoal(BoundVex vex) {
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    /** Forgets every claim (single-player loads one server per world in the same JVM). */
    static void clearClaims() {
        CLAIMED.clear();
    }

    @Override
    public boolean canUse() {
        // A vex that died, was discarded or unloaded never ran stop(); drop its claim so the map does not keep it
        // (and its level) reachable. Done before any early return, so it does not wait for an idle scan. Such a claim
        // already counts as free in claimedByAnother.
        CLAIMED.values().removeIf(holder -> holder.isRemoved() || !holder.isAlive());
        if (!vex.scanDue(BoundVex.SCAN_INTERVAL) || vex.getTarget() != null) {
            return false;
        }
        ServerPlayer owner = vex.summoner().orElse(null);
        if (owner == null) {
            return false;
        }
        golem = vex
                .level()
                .getEntitiesOfClass(
                        IronGolem.class,
                        vex.guardArea(),
                        candidate -> vex.inGuardArea(candidate)
                                && needsRepair(owner, candidate)
                                && !claimedByAnother(candidate))
                .stream()
                .min(Comparator.comparingDouble(vex::distanceToSqr))
                .orElse(null);
        return golem != null;
    }

    private static boolean needsRepair(ServerPlayer owner, IronGolem candidate) {
        return candidate.isAlive()
                && candidate.getHealth() < candidate.getMaxHealth()
                && Allies.isAllyOf(owner, candidate);
    }

    /** Whether a living Bound Vex other than this one holds the claim on {@code candidate}. */
    private boolean claimedByAnother(IronGolem candidate) {
        BoundVex holder = CLAIMED.get(candidate.getUUID());
        return holder != null
                && holder.isAlive()
                && !holder.isRemoved()
                && !holder.getUUID().equals(vex.getUUID());
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        if (golem == null) {
            return;
        }
        CLAIMED.put(golem.getUUID(), vex);
        conjured = false;
        ticksSinceTap = 0;
    }

    @Override
    public boolean canContinueToUse() {
        if (golem == null || vex.getTarget() != null) {
            return false;
        }
        ServerPlayer owner = vex.summoner().orElse(null);
        return owner != null && needsRepair(owner, golem) && vex.inGuardArea(golem);
    }

    /** Flies to the chest; there, conjures the ingot once and taps every {@link #TAP_INTERVAL} ticks. */
    @Override
    public void tick() {
        if (golem == null) {
            return;
        }
        Vec3 chest = golem.position().add(0.0, CHEST_HEIGHT, 0.0);
        if (vex.position().distanceTo(chest) > REACH) {
            vex.getMoveControl().setWantedPosition(chest.x, chest.y, chest.z, SPEED);
            return;
        }
        if (!conjured) {
            held = vex.getMainHandItem().copy();
            vex.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT));
            conjured = true;
        }
        if (ticksSinceTap == 0) {
            tap(golem);
        }
        ticksSinceTap = (ticksSinceTap + 1) % TAP_INTERVAL;
    }

    /** One repair tap: the swing, vanilla's repair sound at vanilla's pitch, the heal. */
    private void tap(IronGolem target) {
        vex.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
        // Two separate draws (vanilla's triangular spread); Error Prone cannot see that the calls differ.
        float first = vex.getRandom().nextFloat();
        float second = vex.getRandom().nextFloat();
        float pitch = 1.0F + (first - second) * 0.2F;
        vex.playSound(SoundEvents.IRON_GOLEM_REPAIR, 1.0F, pitch);
        target.heal(HEAL);
    }

    @Override
    public void stop() {
        if (golem != null) {
            UUID self = vex.getUUID();
            CLAIMED.computeIfPresent(
                    golem.getUUID(), (id, holder) -> holder.getUUID().equals(self) ? null : holder);
        }
        if (conjured) {
            vex.setItemSlot(EquipmentSlot.MAINHAND, held);
        }
        held = ItemStack.EMPTY;
        conjured = false;
        golem = null;
    }
}
