package org.millenaire.block.mock;

import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.millenaire.building.SpecialPoint;

public class MockFacingMarkerBlock extends MockBlock {
   public static final EnumProperty<FacingMarkerType> TYPE = EnumProperty.create("type", FacingMarkerType.class);
   public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
   public static final BooleanProperty GUESS = BooleanProperty.create("guess");
   private static final Map<Direction, VoxelShape> SIGN_SHAPES = Map.of(
      Direction.NORTH,
      Block.box(0.0, 0.0, 15.0, 16.0, 16.0, 16.0),
      Direction.EAST,
      Block.box(15.0, 0.0, 0.0, 16.0, 16.0, 16.0),
      Direction.SOUTH,
      Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 1.0),
      Direction.WEST,
      Block.box(0.0, 0.0, 0.0, 1.0, 16.0, 16.0)
   );

   public MockFacingMarkerBlock(Properties properties) {
      super(properties);
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(TYPE, FacingMarkerType.FURNACE))
               .setValue(FACING, Direction.NORTH))
            .setValue(GUESS, false)
      );
   }

   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return switch ((FacingMarkerType)state.getValue(TYPE)) {
         case FURNACE -> Shapes.block();
         case SIGN_POS -> (VoxelShape)SIGN_SHAPES.getOrDefault(state.getValue(FACING), Shapes.block());
      };
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{TYPE, FACING, GUESS});
   }

   @Nullable
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      return (BlockState)this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
   }

   public BlockState rotate(BlockState state, Rotation rotation) {
      return (BlockState)state.setValue(FACING, rotation.rotate((Direction)state.getValue(FACING)));
   }

   public BlockState mirror(BlockState state, Mirror mirror) {
      return state.rotate(mirror.getRotation((Direction)state.getValue(FACING)));
   }

   protected Property<? extends Comparable<?>> variantProperty() {
      return TYPE;
   }

   protected String translationKeyPrefix() {
      return "block.millenaire.mock_facing_marker.";
   }

   public SpecialPoint toSpecialPoint(BlockState state, BlockPos pos) {
      FacingMarkerType type = (FacingMarkerType)state.getValue(TYPE);
      Direction facing = (Direction)state.getValue(FACING);
      boolean guess = (Boolean)state.getValue(GUESS);
      String orientation = guess ? "guess" : facing.getSerializedName();
      return new SpecialPoint(type.specialPointType(), null, orientation, pos);
   }

   @Nullable
   public BlockState getReplacementState(BlockState mockState) {
      FacingMarkerType type = (FacingMarkerType)mockState.getValue(TYPE);
      Direction facing = (Direction)mockState.getValue(FACING);

      return switch (type) {
         case FURNACE -> (BlockState)Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, facing);
         case SIGN_POS -> null;
      };
   }
}
