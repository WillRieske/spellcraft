package io.github.willrieske.spellcraft.gametest;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.VexGear;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Helpers of the Part 6 team-move GameTests: frozen and flying vexes, foes and attribute setters. */
final class TeamMoveTestSupport {
    private TeamMoveTestSupport() {}

    /** The point (absolute) a test hangs its vexes around: over the structure's middle, above the barrier shell. */
    static Vec3 hangPoint(GameTestHelper helper) {
        return helper.absoluteVec(Vec3.atBottomCenterOf(BoundVexTestSupport.AIR_SPOT));
    }

    /** A point 3 blocks east of the hang point, where a test's foe stands. */
    static Vec3 beside(GameTestHelper helper) {
        return hangPoint(helper).add(3.0, 0.0, 0.0);
    }

    /**
     * {@code count} following Bound Vexes of {@code player} with no AI and no gravity, in rows of four around the
     * hang point.
     */
    static List<BoundVex> frozenVexes(GameTestHelper helper, ServerPlayer player, int count) {
        List<BoundVex> vexes = new ArrayList<>();
        Vec3 hang = hangPoint(helper);
        for (int i = 0; i < count; i++) {
            BoundVex vex = BoundVexTestSupport.boundVex(helper, BoundVexTestSupport.AIR_SPOT, player);
            vex.setNoAi(true);
            vex.setPos(hang.add(i % 4 - 1.5, 0.0, Math.floorDiv(i, 4) - 1.0));
            vexes.add(vex);
        }
        return vexes;
    }

    /**
     * {@code count} Bound Vexes with their full AI but no target goals, in a row one block apart through
     * {@code center}.
     */
    static List<BoundVex> flyingVexes(GameTestHelper helper, ServerPlayer player, int count, Vec3 center) {
        List<BoundVex> vexes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BoundVex vex = BoundVexTestSupport.noTargets(
                    BoundVexTestSupport.boundVex(helper, BoundVexTestSupport.AIR_SPOT, player));
            vex.setPos(center.add(i - (count - 1) / 2.0, 0.0, 0.0));
            vexes.add(vex);
        }
        return vexes;
    }

    /** A mob with no AI and no gravity at {@code at} (absolute), kept by the world and discarded when the test ends. */
    static <T extends Mob> T foe(GameTestHelper helper, EntityType<T> type, Vec3 at) {
        T mob = helper.spawn(type, BoundVexTestSupport.FEET);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setPersistenceRequired();
        mob.setPos(at);
        GameTestSupport.whenDone(helper, mob::discard);
        return mob;
    }

    /** A husk with 40 health and no armor: big enough for a Shield, and plain damage numbers. */
    static Husk bigHusk(GameTestHelper helper, Vec3 at) {
        Husk husk = foe(helper, EntityTypes.HUSK, at);
        setBase(husk, Attributes.MAX_HEALTH, 40.0);
        husk.setHealth(40.0F);
        setBase(husk, Attributes.ARMOR, 0.0);
        return husk;
    }

    /** Sets an attribute's base value; fails loudly when the entity has no such attribute. */
    static void setBase(LivingEntity entity, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            throw new IllegalStateException(entity.getType() + " has no attribute " + attribute.getRegisteredName());
        }
        instance.setBaseValue(value);
    }

    /** Fails unless {@code vex} holds a conjured main-hand item, a conjured shield and three conjured armor pieces. */
    static void assertRaidGear(GameTestHelper helper, BoundVex vex) {
        helper.assertTrue(vex.isConjured(EquipmentSlot.MAINHAND), "a conjured weapon");
        helper.assertTrue(
                vex.isConjured(EquipmentSlot.OFFHAND) && vex.getOffhandItem().is(Items.SHIELD), "a shield");
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS)) {
            helper.assertTrue(
                    vex.isConjured(slot) && VexGear.kindOf(vex.getItemBySlot(slot)) == VexGear.Kind.ARMOR,
                    slot + " armor");
        }
    }
}
