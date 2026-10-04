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

public class BlockWetBrick extends Block {
   public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 2);
   private static final int MIN_LIGHT = 15;

   public BlockWetBrick(Properties properties) {
      super(properties);
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(AGE, 0));
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{AGE});
   }

   public boolean isRandomlyTicking(BlockState state) {
      return true;
   }

   protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      if (level.getMaxLocalRawBrightness(pos.above()) >= 15) {
         int age = (Integer)state.getValue(AGE);
         if (age >= 2) {
            level.setBlock(pos, ((Block)ModBlocks.MUD_BRICK.get()).defaultBlockState(), 2);
         } else {
            int newAge = random.nextBoolean() ? age + 2 : age + 1;
            level.setBlock(pos, (BlockState)state.setValue(AGE, Math.min(newAge, 2)), 2);
         }
      }
   }
}
