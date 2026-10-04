package org.millenaire.block.mock;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.millenaire.block.BlockSilkWorm;
import org.millenaire.block.BlockSnailSoil;
import org.millenaire.block.ModBlocks;
import org.millenaire.building.SpecialPoint;

public class MockMarkerBlock extends MockBlock {
   public static final EnumProperty<MarkerType> TYPE = EnumProperty.create("type", MarkerType.class);
   private static final VoxelShape CARPET_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);

   public MockMarkerBlock(Properties properties) {
      super(properties);
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(TYPE, MarkerType.SLEEPING_POS));
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{TYPE});
   }

   public SpecialPoint toSpecialPoint(BlockState state, BlockPos pos) {
      MarkerType type = (MarkerType)state.getValue(TYPE);
      return new SpecialPoint(type.specialPointType(), type.specialPointSubtype(), null, pos);
   }

   protected Property<? extends Comparable<?>> variantProperty() {
      return TYPE;
   }

   protected String translationKeyPrefix() {
      return "block.millenaire.mock_marker.";
   }

   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return ((MarkerType)state.getValue(TYPE)).hasSolidCollision() ? Shapes.block() : CARPET_SHAPE;
   }

   protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return ((MarkerType)state.getValue(TYPE)).hasSolidCollision() ? Shapes.block() : Shapes.empty();
   }

   @Nullable
   public BlockState getReplacementState(BlockState mockState) {
      MarkerType type = (MarkerType)mockState.getValue(TYPE);

      return switch (type) {
         case TORCH -> Blocks.TORCH.defaultBlockState();
         case SILKWORM_BLOCK -> ((BlockSilkWorm)ModBlocks.SILK_WORM.get()).defaultBlockState();
         case SNAIL_SOIL_BLOCK -> ((BlockSnailSoil)ModBlocks.SNAIL_SOIL.get()).defaultBlockState();
         default -> null;
      };
   }
}
