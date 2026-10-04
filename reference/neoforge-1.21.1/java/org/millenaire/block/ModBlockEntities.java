package org.millenaire.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityType.Builder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
   public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "millenaire");
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LockedChestBlockEntity>> LOCKED_CHEST = BLOCK_ENTITIES.register(
      "locked_chest", () -> Builder.of(LockedChestBlockEntity::new, new Block[]{(Block)ModBlocks.LOCKED_CHEST.get()}).build(null)
   );
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VillagePanelBlockEntity>> VILLAGE_PANEL = BLOCK_ENTITIES.register(
      "village_panel", () -> Builder.of(VillagePanelBlockEntity::new, new Block[]{(Block)ModBlocks.VILLAGE_PANEL.get()}).build(null)
   );
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ImportTableBlockEntity>> IMPORT_TABLE = BLOCK_ENTITIES.register(
      "import_table", () -> Builder.of(ImportTableBlockEntity::new, new Block[]{(Block)ModBlocks.IMPORT_TABLE.get()}).build(null)
   );
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FirePitBlockEntity>> FIRE_PIT = BLOCK_ENTITIES.register(
      "fire_pit", () -> Builder.of(FirePitBlockEntity::new, new Block[]{(Block)ModBlocks.FIRE_PIT.get()}).build(null)
   );

   private ModBlockEntities() {
   }

   public static void register(IEventBus modEventBus) {
      BLOCK_ENTITIES.register(modEventBus);
   }
}
