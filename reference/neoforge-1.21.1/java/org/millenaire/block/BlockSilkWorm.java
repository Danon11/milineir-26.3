package org.millenaire.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

public class BlockSilkWorm extends Block {
   public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
   private static final int MAX_LIGHT = 7;

   public BlockSilkWorm(Properties properties) {
      super(properties);
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(AGE, 0));
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{AGE});
   }

   public boolean isRandomlyTicking(BlockState state) {
      return (Integer)state.getValue(AGE) < 3;
   }

   protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      if (level.getMaxLocalRawBrightness(pos.above()) < 7) {
         if (!random.nextBoolean()) {
            int age = (Integer)state.getValue(AGE);
            if (age < 3) {
               level.setBlock(pos, (BlockState)state.setValue(AGE, age + 1), 2);
            }
         }
      }
   }
}
