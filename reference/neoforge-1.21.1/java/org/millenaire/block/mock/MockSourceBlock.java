package org.millenaire.block.mock;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.millenaire.building.SpecialPoint;

public class MockSourceBlock extends MockBlock {
   public static final EnumProperty<SourceType> MATERIAL = EnumProperty.create("material", SourceType.class);

   public MockSourceBlock(Properties properties) {
      super(properties);
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(MATERIAL, SourceType.STONE));
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{MATERIAL});
   }

   protected Property<? extends Comparable<?>> variantProperty() {
      return MATERIAL;
   }

   protected String translationKeyPrefix() {
      return "block.millenaire.mock_source.";
   }

   public SpecialPoint toSpecialPoint(BlockState state, BlockPos pos) {
      SourceType material = (SourceType)state.getValue(MATERIAL);
      return new SpecialPoint("source", material.getSerializedName(), null, pos);
   }

   @Nullable
   public BlockState getReplacementState(BlockState mockState) {
      SourceType material = (SourceType)mockState.getValue(MATERIAL);

      return switch (material) {
         case STONE -> Blocks.STONE.defaultBlockState();
         case SAND -> Blocks.SAND.defaultBlockState();
         case SANDSTONE -> Blocks.SANDSTONE.defaultBlockState();
         case CLAY -> Blocks.CLAY.defaultBlockState();
         case GRAVEL -> Blocks.GRAVEL.defaultBlockState();
         case GRANITE -> Blocks.GRANITE.defaultBlockState();
         case DIORITE -> Blocks.DIORITE.defaultBlockState();
         case ANDESITE -> Blocks.ANDESITE.defaultBlockState();
         case SNOW -> Blocks.SNOW_BLOCK.defaultBlockState();
         case ICE -> Blocks.ICE.defaultBlockState();
         case RED_SANDSTONE -> Blocks.RED_SANDSTONE.defaultBlockState();
         case QUARTZ -> Blocks.QUARTZ_BLOCK.defaultBlockState();
      };
   }
}
