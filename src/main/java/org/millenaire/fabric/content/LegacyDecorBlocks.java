package org.millenaire.fabric.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Block shapes of the legacy decorative content that need orientation or bed behaviour. */
public final class LegacyDecorBlocks {
    private LegacyDecorBlocks() {}

    /** Straw and charpoy beds: two-block beds with their own low mattress model. */
    public static final class LegacyBedBlock extends BedBlock {
        private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 4, 16);
        public LegacyBedBlock(BlockBehaviour.Properties properties) { super(DyeColor.WHITE, properties); }
        @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    }

    /** Tiles whose pattern runs along the x or z axis. */
    public static final class AxisBlock extends Block {
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        public AxisBlock(BlockBehaviour.Properties properties) {
            super(properties);
            registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.X));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(AXIS); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
        }
        @Override protected BlockState rotate(BlockState state, Rotation rotation) { return rotateAxis(state, AXIS, rotation); }
    }

    public static final class AxisSlabBlock extends SlabBlock {
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        public AxisSlabBlock(BlockBehaviour.Properties properties) {
            super(properties);
            registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.X));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(AXIS);
        }
        @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
            BlockState state = super.getStateForPlacement(context);
            return state == null ? null : state.setValue(AXIS, context.getHorizontalDirection().getAxis());
        }
        @Override protected BlockState rotate(BlockState state, Rotation rotation) { return rotateAxis(state, AXIS, rotation); }
    }

    private static BlockState rotateAxis(BlockState state, EnumProperty<Direction.Axis> axis, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90)
            return state.setValue(axis, state.getValue(axis) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        return state;
    }

    /** Carvings and statues that face a horizontal direction. */
    public static final class FacingBlock extends HorizontalDirectionalBlock {
        public FacingBlock(BlockBehaviour.Properties properties) {
            super(properties);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }
    }

    public enum TopBottom implements StringRepresentable {
        TOP("top"), BOTTOM("bottom");
        private final String name;
        TopBottom(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }

    /** Wooden bars with a rosette panel: top or bottom half, facing the viewer. */
    public static final class RosetteBarsBlock extends IronBarsBlock {
        public static final EnumProperty<TopBottom> TOPBOTTOM = EnumProperty.create("topbottom", TopBottom.class);
        public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
        public RosetteBarsBlock(BlockBehaviour.Properties properties) {
            super(properties);
            registerDefaultState(defaultBlockState().setValue(TOPBOTTOM, TopBottom.BOTTOM).setValue(FACING, Direction.SOUTH));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(TOPBOTTOM, FACING);
        }
        @Override protected BlockState rotate(BlockState state, Rotation rotation) {
            return super.rotate(state, rotation).setValue(FACING, rotation.rotate(state.getValue(FACING)));
        }
        @Override protected BlockState mirror(BlockState state, Mirror mirror) {
            return super.mirror(state, mirror).setValue(FACING, mirror.mirror(state.getValue(FACING)));
        }
    }
}
