package org.millenaire.block.mock;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.millenaire.block.ModBlocks;
import org.millenaire.building.SpecialPoint;

public class MockFreeBlock extends MockBlock {
   public static final EnumProperty<FreeBlockType> MATERIAL = EnumProperty.create("material", FreeBlockType.class);

   public MockFreeBlock(Properties properties) {
      super(properties);
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(MATERIAL, FreeBlockType.STONE));
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{MATERIAL});
   }

   protected Property<? extends Comparable<?>> variantProperty() {
      return MATERIAL;
   }

   protected String translationKeyPrefix() {
      return "block.millenaire.mock_free.";
   }

   public SpecialPoint toSpecialPoint(BlockState state, BlockPos pos) {
      FreeBlockType material = (FreeBlockType)state.getValue(MATERIAL);
      return new SpecialPoint("freeBlock", material.getSerializedName(), null, pos);
   }

   @Nullable
   public BlockState getReplacementState(BlockState mockState) {
      FreeBlockType material = (FreeBlockType)mockState.getValue(MATERIAL);

      return switch (material) {
         case STONE -> Blocks.STONE.defaultBlockState();
         case SAND -> Blocks.SAND.defaultBlockState();
         case GRAVEL -> Blocks.GRAVEL.defaultBlockState();
         case SANDSTONE -> Blocks.SANDSTONE.defaultBlockState();
         case WOOL -> Blocks.WHITE_WOOL.defaultBlockState();
         case COBBLESTONE -> Blocks.COBBLESTONE.defaultBlockState();
         case STONE_BRICK -> Blocks.STONE_BRICKS.defaultBlockState();
         case PAINTED_BRICK -> ((Block)ModBlocks.PAINTED_BRICKS.get(DyeColor.WHITE).get()).defaultBlockState();
         case GRASS_BLOCK -> Blocks.GRASS_BLOCK.defaultBlockState();
      };
   }
}
