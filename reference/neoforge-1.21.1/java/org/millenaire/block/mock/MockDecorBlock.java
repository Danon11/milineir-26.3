package org.millenaire.block.mock;

import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.millenaire.building.SpecialPoint;

public class MockDecorBlock extends MockBlock {
   public static final EnumProperty<MockDecorBlock.DecorType> DECOR_TYPE = EnumProperty.create("decor_type", MockDecorBlock.DecorType.class);
   public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
   private static final Map<Direction, VoxelShape> WALL_SHAPES = Map.of(
      Direction.NORTH,
      Block.box(0.0, 0.0, 15.0, 16.0, 16.0, 16.0),
      Direction.EAST,
      Block.box(15.0, 0.0, 0.0, 16.0, 16.0, 16.0),
      Direction.SOUTH,
      Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 1.0),
      Direction.WEST,
      Block.box(0.0, 0.0, 0.0, 1.0, 16.0, 16.0)
   );

   public MockDecorBlock(Properties properties) {
      super(properties);
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(DECOR_TYPE, MockDecorBlock.DecorType.TAPESTRY))
            .setValue(FACING, Direction.NORTH)
      );
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{DECOR_TYPE, FACING});
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
      return DECOR_TYPE;
   }

   protected String translationKeyPrefix() {
      return "block.millenaire.mock_decor.";
   }

   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return WALL_SHAPES.getOrDefault(state.getValue(FACING), Shapes.block());
   }

   public SpecialPoint toSpecialPoint(BlockState state, BlockPos pos) {
      MockDecorBlock.DecorType type = (MockDecorBlock.DecorType)state.getValue(DECOR_TYPE);
      return new SpecialPoint("wall_decoration", type.getSerializedName(), null, pos);
   }

   @Nullable
   public BlockState getReplacementState(BlockState mockState) {
      return null;
   }

   public enum DecorType implements StringRepresentable {
      TAPESTRY("tapestry"),
      INDIAN_STATUE("indian_statue"),
      MAYAN_STATUE("mayan_statue"),
      BYZANTINE_ICON_SMALL("byzantine_icon_small"),
      BYZANTINE_ICON_MEDIUM("byzantine_icon_medium"),
      BYZANTINE_ICON_LARGE("byzantine_icon_large"),
      HIDE_HANGING("hide_hanging"),
      WALL_CARPET_SMALL("wall_carpet_small"),
      WALL_CARPET_MEDIUM("wall_carpet_medium"),
      WALL_CARPET_LARGE("wall_carpet_large");

      private final String name;

      DecorType(String name) {
         this.name = name;
      }

      public String getSerializedName() {
         return this.name;
      }
   }
}
