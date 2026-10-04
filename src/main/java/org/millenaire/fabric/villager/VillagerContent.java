package org.millenaire.fabric.villager;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class VillagerContent {
    public static final Identifier VILLAGER_ID = Identifier.fromNamespaceAndPath("millenaire", "villager");
    public static EntityType<MillVillagerEntity> VILLAGER;

    private VillagerContent() {}

    public static void register() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, VILLAGER_ID);
        VILLAGER = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
                EntityType.Builder.<MillVillagerEntity>of(MillVillagerEntity::new, MobCategory.MISC)
                        .sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10).build(key));
        FabricDefaultAttributeRegistry.register(VILLAGER, MillVillagerEntity.createAttributes());
    }
}
