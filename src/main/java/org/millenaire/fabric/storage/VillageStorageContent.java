package org.millenaire.fabric.storage;

import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.millenaire.fabric.LegacyContentRegistry;

import java.util.Set;

public final class VillageStorageContent {
    public static BlockEntityType<VillageChestBlockEntity> CHEST_TYPE;
    public static BlockEntityType<VillagePanelBlockEntity> PANEL_TYPE;
    private VillageStorageContent() {}
    public static void registerBlockEntities() {
        CHEST_TYPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath("millenaire", "locked_chest"),
                new BlockEntityType<>(VillageChestBlockEntity::new, Set.of(LegacyContentRegistry.block("locked_chest"), LegacyContentRegistry.block("mainchest"))));
        PANEL_TYPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath("millenaire", "panel"),
                new BlockEntityType<>(VillagePanelBlockEntity::new, Set.of(LegacyContentRegistry.block("panel"))));
        // A non-null empty storage suppresses the generic inventory fallback, including null-side queries.
        ItemStorage.SIDED.registerForBlockEntities((chest, side) -> Storage.empty(), CHEST_TYPE);
    }
}
