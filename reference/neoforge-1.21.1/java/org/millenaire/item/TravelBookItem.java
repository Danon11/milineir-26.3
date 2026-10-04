package org.millenaire.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import org.millenaire.network.TravelBookRequestPayload;
import org.millenaire.village.TravelBookContentBuilder;
import org.millenaire.village.TravelBookScreenState;

public class TravelBookItem extends Item {
   public TravelBookItem(Properties properties) {
      super(properties);
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
         TravelBookContentBuilder.handleRequest(serverPlayer, new TravelBookRequestPayload(TravelBookScreenState.HOME, "", "", "", 0));
      }

      return InteractionResultHolder.success(player.getItemInHand(hand));
   }
}
