package org.millenaire.block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import org.millenaire.item.ModItems;

public class PistachioTreeLeavesBlock extends LeavesBlock {
   public static final int MAX_AGE = 3;
   public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);

   public PistachioTreeLeavesBlock(Properties properties) {
      super(properties);
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(AGE, 0)).setValue(DISTANCE, 7))
               .setValue(PERSISTENT, false))
            .setValue(WATERLOGGED, false)
      );
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      super.createBlockStateDefinition(builder);
      builder.add(new Property[]{AGE});
   }

   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
      if ((Integer)state.getValue(AGE) == 3) {
         if (!level.isClientSide) {
            popResource(level, pos.below(), new ItemStack((ItemLike)ModItems.PISTACHIOS.get()));
            level.setBlock(pos, (BlockState)state.setValue(AGE, 0), 2);
         }

         return InteractionResult.SUCCESS;
      } else {
         return InteractionResult.PASS;
      }
   }

   protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      super.randomTick(state, level, pos, random);
      long worldTime = level.getDayTime() % 24000L;
      int targetAge = 0;
      if (worldTime > 3000L && worldTime < 5000L) {
         targetAge = 1;
      } else if (worldTime > 5000L && worldTime < 6000L) {
         targetAge = 2;
      } else if (worldTime > 6000L && worldTime < 10000L) {
         targetAge = 3;
      }

      int validCurrentAge = targetAge - 1;
      if (validCurrentAge < 0) {
         validCurrentAge = 3;
      }

      int currentAge = (Integer)state.getValue(AGE);
      if (currentAge == validCurrentAge) {
         List<BlockPos> toTest = new ArrayList<>();
         Set<BlockPos> visited = new HashSet<>();
         toTest.add(pos);
         int count = 0;

         while (!toTest.isEmpty() && count < 10000) {
            BlockPos p = toTest.remove(toTest.size() - 1);
            if (!visited.add(p)) {
               count++;
            } else if (!level.isLoaded(p)) {
               count++;
            } else {
               BlockState bs = level.getBlockState(p);
               if (bs.getBlock() == this && (Integer)bs.getValue(AGE) == validCurrentAge) {
                  level.setBlock(p, (BlockState)bs.setValue(AGE, targetAge), 2);

                  for (int dx = -1; dx < 2; dx++) {
                     for (int dy = -1; dy < 2; dy++) {
                        for (int dz = -1; dz < 2; dz++) {
                           toTest.add(p.offset(dx, dy, dz));
                        }
                     }
                  }
               }

               count++;
            }
         }
      }
   }

   public boolean isRandomlyTicking(BlockState state) {
      return true;
   }
}
