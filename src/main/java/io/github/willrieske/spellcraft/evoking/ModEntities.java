package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's entity types. {@code Spellcraft} registers {@link #ENTITIES} on the mod bus. */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class ModEntities {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Spellcraft.MODID);

    /**
     * The Bound Vex (Evoking II spec §3.1): vanilla's vex build (docs/api-notes.md §21), so it is fire immune,
     * 0.4×0.8, tracked eight chunks out and not allowed on Peaceful, where {@code create} refuses and
     * {@code Mob.checkDespawn} discards it. No spawn egg, no natural spawn, no loot table (stated by
     * {@code noLootTable()}, so a datapack cannot add one by accident) and no experience; {@code /summon
     * spellcraft:bound_vex} makes one with no summoner, which idles.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<BoundVex>> BOUND_VEX = ENTITIES.registerEntityType(
            "bound_vex",
            BoundVex::new,
            MobCategory.MONSTER,
            builder -> builder.fireImmune()
                    .sized(0.4F, 0.8F)
                    .eyeHeight(0.51875F)
                    .passengerAttachments(0.7375F)
                    .ridingOffset(0.04F)
                    .clientTrackingRange(8)
                    .noLootTable()
                    .notInPeaceful());

    private ModEntities() {}

    /**
     * Gives the Bound Vex its attributes (a mod-bus event, received here as {@code SpellcraftGameTests} receives
     * {@code RegisterGameTestsEvent}).
     *
     * @param event the attribute event
     */
    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(BOUND_VEX.get(), BoundVex.createAttributes().build());
    }
}
