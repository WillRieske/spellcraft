package io.github.willrieske.spellcraft.client.evoking;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.willrieske.spellcraft.evoking.MirrorImage;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Draws a Mirror image as the entity it copies (Parts 6-7 spec §7.2). Vanilla's renderers draw only the entity classes
 * they belong to, so the renderer keeps a client-side <b>stand-in</b> per image, created once and never added to the
 * level: a mannequin for a player, or a new entity of the copied type for a mob. Each frame the image's position,
 * rotations, hurt and death time, walk, pose and held items are copied into the stand-in, the dispatcher is asked for
 * the stand-in's render state, and {@code submit} hands that state back to the dispatcher. The stand-in is never
 * ticked (a ticking {@code LivingEntity} pushes nearby entities client-side), and the mannequin's own skin is only
 * applied inside {@code ClientMannequin.tick()}, so it would stay the default skin forever: the player's skin is put
 * into the extracted render state instead, from {@code PlayerSkinRenderCache.getOrDefault(profile)}, which answers the
 * resolved skin or the default on every call (the dispatcher picks the player renderer, wide or slim, from
 * {@code state.skin.model()}). Images carry the hands' items only, no armor (spec §7.2).
 *
 * <p>Functional spec §13.2, §13.3.
 */
public final class MirrorImageRenderer extends EntityRenderer<MirrorImage, MirrorImageRenderState> {
    /**
     * The stand-in made for one image, and what it was made from.
     *
     * @param image the image's UUID
     * @param copiedType the type the stand-in was made for
     * @param entity the stand-in, or null when no level was available
     */
    private record StandIn(
            UUID image, EntityType<?> copiedType, @Nullable Entity entity) {
        /** Whether this stand-in still fits {@code image} (entity ids restart per world, so the UUID is checked). */
        boolean fits(MirrorImage image) {
            return this.image.equals(image.getUUID()) && copiedType.equals(image.copiedType());
        }
    }

    /**
     * One stand-in per image, dropped with the image (weak keys). {@code Entity.equals} compares ids, which restart
     * per server, so a not-yet-collected entry of a previous world could match a new image: every lookup checks
     * {@link StandIn#fits}, and a mismatch makes a new stand-in.
     */
    private final Map<MirrorImage, StandIn> standIns = new WeakHashMap<>();

    /**
     * Creates the renderer. The shadow radius stays 0: the nested {@code dispatcher.submit} draws the stand-in's own
     * shadow, and a second one here would stack two.
     *
     * @param context the renderer context
     */
    public MirrorImageRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /** Makes an empty render state. */
    @Override
    public MirrorImageRenderState createRenderState() {
        return new MirrorImageRenderState();
    }

    /** Copies the image into its stand-in and stores the stand-in's extracted state. */
    @Override
    public void extractRenderState(MirrorImage image, MirrorImageRenderState state, float partialTicks) {
        super.extractRenderState(image, state, partialTicks);
        StandIn known = standIns.get(image);
        if (known == null || !known.fits(image)) {
            known = new StandIn(image.getUUID(), image.copiedType(), createStandIn(image));
            standIns.put(image, known);
        }
        Entity standIn = known.entity();
        if (standIn == null) {
            state.standIn = null;
            return;
        }
        copyPose(image, standIn);
        EntityRenderState inner = entityRenderDispatcher.extractEntity(standIn, partialTicks);
        inner.nameTag = null; // a mannequin's description label is not an image's ...
        // ... and neither is its below-name line (extractNameTags sets it from belowNameDisplay)
        inner.scoreText = null;
        if (inner instanceof AvatarRenderState avatar) {
            avatar.skin = Minecraft.getInstance()
                    .playerSkinRenderCache()
                    .getOrDefault(image.profile())
                    .playerSkin();
        }
        state.standIn = inner;
    }

    /** A stand-in for {@code image}: a mannequin for a player, else a new entity of the copied type. */
    private @Nullable Entity createStandIn(MirrorImage image) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        Entity standIn = image.copiedType().equals(EntityTypes.PLAYER)
                ? EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.LOAD)
                : image.copiedType().create(level, EntitySpawnReason.LOAD);
        if (standIn != null) {
            // Level.getNextEntityId() is 0 on the client (ids come from the spawn packet), and Entity.getId() throws
            // for 0; vanilla's ItemModelResolver.updateForLiving reads it as the item model seed. The image's own id
            // is non-zero. Entity.equals and hashCode follow the id, so the stand-in equals its image: it must stay
            // out of every level, set and map key (standIns holds it only as a value).
            standIn.setId(image.getId());
        }
        return standIn;
    }

    /**
     * Copies what the renderer reads: position, rotations and their previous values, hurt, death, walk, pose, hands.
     */
    private static void copyPose(MirrorImage image, Entity standIn) {
        standIn.setPos(image.position());
        standIn.xo = image.xo;
        standIn.yo = image.yo;
        standIn.zo = image.zo;
        standIn.xOld = image.xOld;
        standIn.yOld = image.yOld;
        standIn.zOld = image.zOld;
        standIn.setYRot(image.getYRot());
        standIn.setXRot(image.getXRot());
        standIn.yRotO = image.yRotO;
        standIn.xRotO = image.xRotO;
        standIn.setPose(image.getPose());
        if (standIn instanceof LivingEntity living) {
            living.yBodyRot = image.yBodyRot;
            living.yBodyRotO = image.yBodyRotO;
            living.yHeadRot = image.yHeadRot;
            living.yHeadRotO = image.yHeadRotO;
            living.hurtTime = image.hurtTime;
            living.hurtDuration = image.hurtDuration;
            living.deathTime = image.deathTime;
            living.setItemSlot(EquipmentSlot.MAINHAND, image.getMainHandItem());
            living.setItemSlot(EquipmentSlot.OFFHAND, image.getOffhandItem());
            // The walk animation advances once per client tick, not once per frame: the stand-in's tick count is the
            // marker. It smooths an already smoothed speed, so the stand-in's legs lag the image slightly (playtest).
            if (standIn.tickCount != image.tickCount) {
                standIn.tickCount = image.tickCount;
                living.walkAnimation.update(image.walkAnimation.speed(), 0.4F, 1.0F);
            }
        }
    }

    /** Hands the stand-in's state to the dispatcher, which draws it where the image is. */
    @Override
    public void submit(
            MirrorImageRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (state.standIn != null) {
            // The pose stack is already at the image; the dispatcher adds the renderer's own offset
            entityRenderDispatcher.submit(state.standIn, camera, 0.0, 0.0, 0.0, poseStack, collector);
        }
    }
}
