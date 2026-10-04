package org.millenaire.fabric.storage;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.millenaire.fabric.economy.LegacyItemResolver;
import org.millenaire.fabric.economy.StartingStock;

import static org.junit.jupiter.api.Assertions.*;

class VillageStorageTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        // 26.3 binds item prototypes during data loading, after registry bootstrap.
        // This fixture binds the stack limit for the two vanilla inventory test items.
        var prototype = DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build();
        for (var item : java.util.List.of(Items.WHEAT, Items.EMERALD))
            if (!item.builtInRegistryHolder().areComponentsBound()) item.builtInRegistryHolder().bindComponents(prototype);
    }
    private static BuildingBinding binding(Optional<UUID> owner) {
        return new BuildingBinding(new BlockPos(12, 64, -9), "norman:manor_A0", "Manoir", true, true, owner);
    }
    @Test void preservesBindingAndEnforcesOwnerLockAndAdministratorAccess() {
        var owner = UUID.randomUUID(); var stranger = UUID.randomUUID(); var binding = binding(Optional.of(owner));
        assertTrue(binding.allows(owner, false)); assertFalse(binding.allows(stranger, false)); assertTrue(binding.allows(stranger, true));
        var unlocked = new BuildingBinding(binding.origin(), binding.plan(), binding.name(), true, false, Optional.empty());
        assertTrue(unlocked.allows(stranger, false));
        var encoded = BuildingBinding.CODEC.encodeStart(NbtOps.INSTANCE, binding).getOrThrow();
        assertEquals(binding, BuildingBinding.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow());
    }

    @Test void chestInventoryAndBuildingMetadataSurviveActualBlockEntitySerialization() {
        // Vanilla types stand in for the mod types in JUnit's frozen registry.
        var first = new VillageChestBlockEntity(BlockEntityTypes.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        first.setItem(0, new ItemStack(Items.WHEAT, 19)); first.setItem(26, new ItemStack(Items.EMERALD, 3));
        first.getItem(0).set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Запасы"));
        first.bind(binding(Optional.of(UUID.randomUUID())));
        var lookup = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var tag = first.saveWithoutMetadata(lookup);
        var restored = new VillageChestBlockEntity(BlockEntityTypes.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        restored.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, lookup, tag));
        assertEquals(first.binding(), restored.binding()); assertEquals(27, restored.getContainerSize());
        assertTrue(ItemStack.matches(first.getItem(0), restored.getItem(0)));
        assertTrue(ItemStack.matches(first.getItem(26), restored.getItem(26))); assertTrue(restored.getItem(1).isEmpty());
    }

    @Test void playerPlacedChestsAreAccessibleButAutomationIsAlwaysDisabled() {
        var chest = new VillageChestBlockEntity(BlockEntityTypes.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        var player = UUID.randomUUID(); assertTrue(chest.allows(player, false));
        chest.bind(binding(Optional.empty())); assertFalse(chest.allows(player, false)); assertTrue(chest.allows(player, true));
        for (var side : Direction.values()) {
            assertEquals(0, chest.getSlotsForFace(side).length);
            assertFalse(chest.canPlaceItemThroughFace(0, new ItemStack(Items.WHEAT), side));
            assertFalse(chest.canTakeItemThroughFace(0, new ItemStack(Items.WHEAT), side));
        }
    }

    @Test void startingStockIsFilledOnceAndCannotBeRefilledAfterReloadEvenWhenEmpty() {
        var chest = new VillageChestBlockEntity(BlockEntityTypes.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        chest.bind(binding(Optional.empty()));
        var stock = new StartingStock.Inventory(BlockPos.ZERO, List.of(new StartingStock.Slot(0, LegacyItemResolver.translate("minecraft:wheat", 0), 19)));
        chest.fillStartingStock(stock); assertTrue(chest.startingStockInitialized()); assertEquals(19, chest.getItem(0).getCount());
        var lookup = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var restored = new VillageChestBlockEntity(BlockEntityTypes.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        restored.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, lookup, chest.saveWithoutMetadata(lookup)));
        assertTrue(restored.startingStockInitialized()); assertEquals(19, restored.getItem(0).getCount());
        restored.clearContent(); assertThrows(IllegalStateException.class, () -> restored.fillStartingStock(stock)); assertTrue(restored.isEmpty());
    }

    @Test void panelTextWaxAndBindingRoundTripWithoutExposingEditing() {
        var lookup = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var binding = binding(Optional.empty());
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, lookup);
        output.store("millenaire_building", BuildingBinding.CODEC, binding);
        output.store("front_text", SignText.CODEC, VillagePanelBlockEntity.labelText(binding)); output.putBoolean("is_waxed", true);
        var panel = new VillagePanelBlockEntity(BlockEntityTypes.SIGN, BlockPos.ZERO, Blocks.OAK_WALL_SIGN.defaultBlockState());
        panel.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, lookup, output.buildResult()));
        assertEquals(Optional.of(binding), panel.binding()); assertTrue(panel.isWaxed());
        assertEquals("Millénaire", panel.getText(SignTextSlot.FRONT).getMessages(false).getFirst().getString());
        assertEquals("Manoir", panel.getText(SignTextSlot.FRONT).getMessages(false).get(1).getString());
        var copy = new VillagePanelBlockEntity(BlockEntityTypes.SIGN, BlockPos.ZERO, Blocks.OAK_WALL_SIGN.defaultBlockState());
        copy.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, lookup, panel.saveWithoutMetadata(lookup)));
        assertEquals(panel.binding(), copy.binding()); assertEquals(panel.getText(SignTextSlot.FRONT), copy.getText(SignTextSlot.FRONT));
        assertTrue(copy.isWaxed());
    }
}
