package io.github.willrieske.spellcraft.spell;

import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.player.Player;

/**
 * The one ally rule every spell uses (spec §3): what vanilla calls an ally (the player itself, the same team, a
 * tamed animal whose owner is the player, an allay that likes the player), plus the mobs the player summoned
 * ({@link AllyAttachments#SUMMONER}) and any other {@code OwnableEntity} the player owns, which vanilla's {@code
 * Vex} has but never reports through {@code isAlliedTo}.
 */
public final class Allies {
    private Allies() {}

    /** Whether a spell cast by {@code owner} must spare {@code other}. */
    public static boolean isAllyOf(Player owner, Entity other) {
        return owner.isAlliedTo(other) || ownedBy(owner.getUUID(), other);
    }

    /**
     * The cheap pre-check for callers that would otherwise have to look the owner up (Trailing runs it every tick
     * for every entity in a flame): {@code false} means {@link #isAllyOf} is false for every owner except through a
     * vanilla override this list does not know: team, {@code OwnableEntity}, {@code Allay}, the summoner attachment.
     * A modded entity with its own {@code considersEntityAsAlly} would need adding here.
     *
     * <p>Why those four are enough: {@code Entity.java:2879-2889} {@code isAlliedTo} is {@code this == other ||
     * considersEntityAsAlly(other) || other.considersEntityAsAlly(this)}, and the default {@code
     * considersEntityAsAlly} is a team check that is false when either side has no team ({@code Team.java:17},
     * {@code Entity.java:2887}). The vanilla overrides that ally a team-less entity to a player are {@code
     * TamableAnimal.java:209-222} (an {@code OwnableEntity}, {@code TamableAnimal.java:34}; its {@code getTeam()}
     * {@code :192-206} also borrows the owner's) and {@code Allay.java:160-163} (the player it likes); {@code
     * AbstractIllager} and {@code Evoker} ally only illagers and vexes. The summoner attachment is this mod's own
     * rule.
     */
    public static boolean mayHaveAllies(Entity other) {
        return other.getTeam() != null
                || other instanceof OwnableEntity
                || other instanceof Allay
                || other.hasData(AllyAttachments.SUMMONER);
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
