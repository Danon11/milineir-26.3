package org.millenaire.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;

@EventBusSubscriber(modid = "millenaire", value = Dist.CLIENT)
public final class ClientGameEvents {
   private ClientGameEvents() {
   }

   @SubscribeEvent
   public static void onClientTick(Post event) {
      if (Minecraft.getInstance().player == null) {
         FireplaceSmokeHandler.clearAll();
      } else {
         FireplaceSmokeHandler.tick();
      }
   }
}
