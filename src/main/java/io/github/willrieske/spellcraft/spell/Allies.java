package io.github.willrieske.spellcraft.spell;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;

/**
 * The one ally rule every spell uses (Evoking spec §3, Evoking II §2.3): what vanilla calls an ally (the player itself,
 * the same team, a tamed animal whose owner is the player, an allay that likes the player), plus the mobs the player
 * summoned ({@link AllyAttachments#SUMMONER}), any other {@code OwnableEntity} the player owns (vanilla's {@code Vex}
 * has an owner but never reports it through {@code isAlliedTo}), every villager and wandering trader, every zombie
 * villager a Bound Vex is curing ({@link AllyAttachments#CURE}, Evoking II Parts 4-5 spec §2.1), and every iron
 * golem that is not mad at the player (on a client level a golem counts as not mad; the spells call this on the
 * server).
 */
public final class Allies {
    private Allies() {}

    /**
     * Whether a spell cast by {@code owner} must spare {@code other}.
     *
     * @param owner the caster
     * @param other the entity the spell might hit
     * @return true if {@code other} is an ally of {@code owner}
     */
    public static boolean isAllyOf(Player owner, Entity other) {
        return owner.isAlliedTo(other)
                || ownedBy(owner.getUUID(), other)
                || other instanceof AbstractVillager
                || AllyAttachments.isBeingCured(other)
                || (other instanceof IronGolem golem && !isMadAt(golem, owner));
    }

    /**
     * Whether {@code golem} is mad at {@code owner}: the owner is its target, or its persistent anger names the owner
     * ({@code NeutralMob.isAngryAt}, docs/api-notes.md §20). While it is, the owner's spells and summons may fight it;
     * the Trailing flame is the exception ({@code TrailFires.isSpared}).
     *
     * @param golem the golem
     * @param owner the player
     * @return true if the golem is after the player
     */
    public static boolean isMadAt(IronGolem golem, Player owner) {
        LivingEntity target = golem.getTarget();
        if (target != null && target.getUUID().equals(owner.getUUID())) {
            return true;
        }
        return golem.level() instanceof ServerLevel level && golem.isAngryAt(owner, level);
    }

    /**
     * The cheap pre-check for callers that would otherwise have to look the owner up (Trailing runs it every tick
     * for every entity in a flame): {@code false} means {@link #isAllyOf} is false for every owner except through a
     * vanilla override this list does not know: team, {@code OwnableEntity}, {@code Allay}, the summoner attachment,
     * {@code AbstractVillager}, {@code IronGolem}, the cure mark. A modded entity with its own {@code considersEntityAsAlly} would need
     * adding here.
     *
     * <p>Why those are enough: {@code Entity.java:2879-2889} {@code isAlliedTo} is {@code this == other ||
     * considersEntityAsAlly(other) || other.considersEntityAsAlly(this)}, and the default {@code
     * considersEntityAsAlly} is a team check that is false when either side has no team ({@code Team.java:17},
     * {@code Entity.java:2887}). The vanilla overrides that ally a team-less entity to a player are {@code
     * TamableAnimal.java:209-222} (an {@code OwnableEntity}, {@code TamableAnimal.java:34}; its {@code getTeam()}
     * {@code :192-206} also borrows the owner's) and {@code Allay.java:160-163} (the player it likes); {@code
     * AbstractIllager} and {@code Evoker} ally only illagers and vexes. The summoner and cure attachments, villagers and golems
     * are this mod's own rules.
     *
     * @param other the entity to check
     * @return false only when no player can be its ally
     */
    public static boolean mayHaveAllies(Entity other) {
        return other.getTeam() != null
                || other instanceof OwnableEntity
                || other instanceof Allay
                || other instanceof AbstractVillager
                || other instanceof IronGolem
                || other.hasData(AllyAttachments.SUMMONER)
                || other.hasData(AllyAttachments.CURE);
    }

    private static boolean ownedBy(UUID owner, Entity other) {
        if (other.getExistingData(AllyAttachments.SUMMONER)
                .filter(owner::equals)
                .isPresent()) {
            return true;
        }
        if (other instanceof OwnableEntity ownable) {
            EntityReference<LivingEntity> reference = ownable.getOwnerReference();
            return reference != null && owner.equals(reference.getUUID());
        }
        return false;
    }
}
