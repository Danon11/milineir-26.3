package org.millenaire.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.block.ModBlocks;
import org.millenaire.block.RicePaddyBlock;
import org.millenaire.village.PlayerCultureReputation;

public class RiceSeedItem extends Item {
   public RiceSeedItem(Properties properties) {
      super(properties);
   }

   public InteractionResult useOn(UseOnContext context) {
      Level level = context.getLevel();
      BlockPos clickedPos = context.getClickedPos();
      if (context.getClickedFace() != Direction.UP) {
         return InteractionResult.PASS;
      }

      BlockPos waterPos = clickedPos.above();
      BlockState waterState = level.getBlockState(waterPos);
      if (waterState.is(Blocks.WATER) && level.getFluidState(waterPos).isSource()) {
         BlockState groundState = level.getBlockState(clickedPos);
         if (!groundState.isFaceSturdy(level, clickedPos, Direction.UP)) {
            if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer sp) {
               sp.sendSystemMessage(Component.translatable("message.millenaire.rice_needs_water"));
            }

            return InteractionResult.FAIL;
         } else {
            BlockPos aboveWater = waterPos.above();
            BlockState aboveWaterState = level.getBlockState(aboveWater);
            if (!aboveWaterState.isAir()) {
               if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer sp) {
                  sp.sendSystemMessage(Component.translatable("message.millenaire.rice_needs_water"));
               }

               return InteractionResult.FAIL;
            } else {
               if (!level.isClientSide) {
                  ServerPlayer player = (ServerPlayer)context.getPlayer();
                  if (player == null) {
                     return InteractionResult.PASS;
                  }

                  ServerLevel serverLevel = (ServerLevel)level;
                  PlayerCultureReputation cultureRep = PlayerCultureReputation.get(serverLevel);
                  if (!cultureRep.hasLearnedCrop(player.getUUID(), "rice")) {
                     player.sendSystemMessage(Component.translatable("message.millenaire.crop_planting_knowledge"));
                     return InteractionResult.FAIL;
                  }

                  BlockState paddyState = (BlockState)((BlockState)((BlockState)((Block)ModBlocks.RICE_PADDY.get())
                           .defaultBlockState()
                           .setValue(RicePaddyBlock.PLANTED, true))
                        .setValue(RicePaddyBlock.AGE, 0))
                     .setValue(RicePaddyBlock.WATERLOGGED, true);
                  level.setBlock(waterPos, paddyState, 3);
                  if (!player.getAbilities().instabuild) {
                     context.getItemInHand().shrink(1);
                  }

                  level.playSound(null, waterPos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
               }

               return InteractionResult.sidedSuccess(level.isClientSide);
            }
         }
      } else {
         if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.translatable("message.millenaire.rice_needs_water"));
         }

         return InteractionResult.FAIL;
      }
   }
}
