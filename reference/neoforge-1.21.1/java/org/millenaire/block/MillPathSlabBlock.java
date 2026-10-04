package org.millenaire.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class MillPathSlabBlock extends SlabBlock {
   public static final BooleanProperty STABLE = BooleanProperty.create("stable");
   private static final VoxelShape BOTTOM_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 7.0, 16.0);
   private static final VoxelShape TOP_SHAPE = Block.box(0.0, 8.0, 0.0, 16.0, 15.0, 16.0);
   private static final VoxelShape DOUBLE_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 15.0, 16.0);
   private final PathTier tier;

   public MillPathSlabBlock(Properties properties, PathTier tier) {
      super(properties.speedFactor(tier.speedFactor()));
      this.tier = tier;
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(TYPE, SlabType.BOTTOM)).setValue(WATERLOGGED, false))
            .setValue(STABLE, false)
      );
   }

   public PathTier tier() {
      return this.tier;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      super.createBlockStateDefinition(builder);
      builder.add(new Property[]{STABLE});
   }

   public BlockState getStateForPlacement(BlockPlaceContext context) {
      BlockState state = super.getStateForPlacement(context);
      return state != null ? (BlockState)state.setValue(STABLE, true) : null;
   }

   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      SlabType type = (SlabType)state.getValue(TYPE);

      return switch (type) {
         case TOP -> TOP_SHAPE;
         case DOUBLE -> DOUBLE_SHAPE;
         default -> BOTTOM_SHAPE;
      };
   }

   public boolean useShapeForLightOcclusion(BlockState state) {
      return true;
   }

   @Nullable
   public PathType getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob mob) {
      return PathType.WALKABLE;
   }
}
