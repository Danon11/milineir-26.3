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
import org.millenaire.block.ModBlocks;
import org.millenaire.block.RicePaddyBlock;
import org.millenaire.building.SpecialPoint;

public class MockSoilBlock extends MockBlock {
   public static final EnumProperty<CropType> CROP = EnumProperty.create("crop", CropType.class);

   public MockSoilBlock(Properties properties) {
      super(properties);
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(CROP, CropType.WHEAT));
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{CROP});
   }

   protected Property<? extends Comparable<?>> variantProperty() {
      return CROP;
   }

   protected String translationKeyPrefix() {
      return "block.millenaire.mock_soil.";
   }

   public SpecialPoint toSpecialPoint(BlockState state, BlockPos pos) {
      CropType crop = (CropType)state.getValue(CROP);
      return new SpecialPoint("soil", crop.getSerializedName(), null, pos);
   }

   @Nullable
   public BlockState getReplacementState(BlockState mockState) {
      CropType crop = (CropType)mockState.getValue(CROP);
      if (crop == CropType.RICE) {
         return (BlockState)((BlockState)((Block)ModBlocks.RICE_PADDY.get()).defaultBlockState().setValue(RicePaddyBlock.PLANTED, false))
            .setValue(RicePaddyBlock.WATERLOGGED, true);
      } else if (crop == CropType.SUGAR_CANE) {
         return Blocks.SAND.defaultBlockState();
      } else {
         return crop == CropType.FLOWER ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
      }
   }
}
