package org.millenaire.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlockGrapeVine extends BushBlock implements BonemealableBlock {
   public static final MapCodec<BlockGrapeVine> CODEC = simpleCodec(BlockGrapeVine::new);
   public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 7);
   public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
   private static final VoxelShape SHAPE_LOWER = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);
   private static final VoxelShape SHAPE_UPPER = Block.box(2.0, 0.0, 2.0, 14.0, 12.0, 14.0);

   public BlockGrapeVine(Properties properties) {
      super(properties);
      this.registerDefaultState((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(AGE, 0)).setValue(HALF, Half.BOTTOM));
   }

   protected MapCodec<? extends BushBlock> codec() {
      return CODEC;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{AGE, HALF});
   }

   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return state.getValue(HALF) == Half.BOTTOM ? SHAPE_LOWER : SHAPE_UPPER;
   }

   protected boolean mayPlaceOn(BlockState groundState, BlockGetter level, BlockPos pos) {
      return groundState.getBlock() instanceof FarmBlock;
   }

   protected boolean isRandomlyTicking(BlockState state) {
      return state.getValue(HALF) == Half.BOTTOM && (Integer)state.getValue(AGE) < 7;
   }

   protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
      if (state.getValue(HALF) == Half.BOTTOM) {
         BlockState below = level.getBlockState(pos.below());
         return below.getBlock() instanceof FarmBlock;
      } else {
         BlockState below = level.getBlockState(pos.below());
         return below.is(this) && below.getValue(HALF) == Half.BOTTOM;
      }
   }

   protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      if (state.getValue(HALF) == Half.BOTTOM) {
         int age = (Integer)state.getValue(AGE);
         if (age < 7) {
            if (random.nextInt(5) == 0) {
               int newAge = age + 1;
               level.setBlock(pos, (BlockState)state.setValue(AGE, newAge), 2);
               BlockPos upperPos = pos.above();
               BlockState upperState = level.getBlockState(upperPos);
               if (newAge >= 2) {
                  if (upperState.is(this) && upperState.getValue(HALF) == Half.TOP) {
                     level.setBlock(upperPos, (BlockState)upperState.setValue(AGE, newAge), 2);
                  } else if (upperState.isAir()) {
                     level.setBlock(upperPos, (BlockState)((BlockState)this.defaultBlockState().setValue(HALF, Half.TOP)).setValue(AGE, newAge), 2);
                  }
               }
            }
         }
      }
   }

   protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
      super.onRemove(state, level, pos, newState, isMoving);
      if (!state.is(newState.getBlock())) {
         if (state.getValue(HALF) == Half.BOTTOM) {
            BlockPos upperPos = pos.above();
            BlockState upperState = level.getBlockState(upperPos);
            if (upperState.is(this) && upperState.getValue(HALF) == Half.TOP) {
               level.setBlock(upperPos, Blocks.AIR.defaultBlockState(), 3);
            }
         } else {
            BlockPos lowerPos = pos.below();
            BlockState lowerState = level.getBlockState(lowerPos);
            if (lowerState.is(this) && lowerState.getValue(HALF) == Half.BOTTOM) {
               level.setBlock(lowerPos, Blocks.AIR.defaultBlockState(), 3);
            }
         }
      }
   }

   public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
      return (Integer)state.getValue(AGE) < 7;
   }

   public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
      return true;
   }

   public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
      int newAge = Math.min((Integer)state.getValue(AGE) + random.nextIntBetweenInclusive(2, 5), 7);
      if (state.getValue(HALF) == Half.TOP) {
         BlockPos lowerPos = pos.below();
         BlockState lowerState = level.getBlockState(lowerPos);
         if (lowerState.is(this) && lowerState.getValue(HALF) == Half.BOTTOM) {
            level.setBlock(lowerPos, (BlockState)lowerState.setValue(AGE, newAge), 2);
         }

         level.setBlock(pos, (BlockState)state.setValue(AGE, newAge), 2);
      } else {
         level.setBlock(pos, (BlockState)state.setValue(AGE, newAge), 2);
         BlockPos upperPos = pos.above();
         BlockState upperState = level.getBlockState(upperPos);
         if (upperState.is(this) && upperState.getValue(HALF) == Half.TOP) {
            level.setBlock(upperPos, (BlockState)upperState.setValue(AGE, newAge), 2);
         } else if (newAge >= 2 && upperState.isAir()) {
            level.setBlock(upperPos, (BlockState)((BlockState)this.defaultBlockState().setValue(HALF, Half.TOP)).setValue(AGE, newAge), 2);
         }
      }
   }
}
