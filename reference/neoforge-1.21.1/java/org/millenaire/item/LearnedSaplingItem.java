package org.millenaire.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.millenaire.village.PlayerCultureReputation;

public class LearnedSaplingItem extends BlockItem {
   private final String cropKey;

   public LearnedSaplingItem(Block block, String cropKey, Properties properties) {
      super(block, properties);
      this.cropKey = cropKey;
   }

   public InteractionResult useOn(UseOnContext context) {
      Level level = context.getLevel();
      if (level.isClientSide) {
         return InteractionResult.SUCCESS;
      } else {
         ServerPlayer player = (ServerPlayer)context.getPlayer();
         if (player == null) {
            return InteractionResult.PASS;
         } else {
            ServerLevel serverLevel = (ServerLevel)level;
            PlayerCultureReputation cultureRep = PlayerCultureReputation.get(serverLevel);
            if (!cultureRep.hasLearnedCrop(player.getUUID(), this.cropKey)) {
               player.sendSystemMessage(Component.translatable("message.millenaire.crop_planting_knowledge"));
               return InteractionResult.FAIL;
            } else {
               return super.useOn(context);
            }
         }
      }
   }
}
