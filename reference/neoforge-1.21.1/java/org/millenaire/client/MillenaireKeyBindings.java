package org.millenaire.client;

import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.network.InfoPanelRequestPayload;

@EventBusSubscriber(modid = "millenaire", value = Dist.CLIENT)
public final class MillenaireKeyBindings {
   private static final String CATEGORY = "key.categories.millenaire";
   public static final KeyMapping KEY_VILLAGES = new KeyMapping("key.millenaire.villages", Type.KEYSYM, 86, "key.categories.millenaire");
   public static final KeyMapping KEY_INFO_PANEL = new KeyMapping("key.millenaire.info_panel", Type.KEYSYM, 77, "key.categories.millenaire");

   private MillenaireKeyBindings() {
   }

   @SubscribeEvent
   public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
      event.register(KEY_VILLAGES);
      event.register(KEY_INFO_PANEL);
      NeoForge.EVENT_BUS.addListener(MillenaireKeyBindings::onClientTick);
   }

   private static void onClientTick(Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.screen == null) {
         while (KEY_VILLAGES.consumeClick()) {
            mc.player.connection.sendCommand("millenaire villages");
         }

         while (KEY_INFO_PANEL.consumeClick()) {
            PacketDistributor.sendToServer(new InfoPanelRequestPayload(), new CustomPacketPayload[0]);
         }
      }
   }
}
