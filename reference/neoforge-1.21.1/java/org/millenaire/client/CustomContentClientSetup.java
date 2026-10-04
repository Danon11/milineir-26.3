package org.millenaire.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn;
import net.neoforged.neoforge.common.NeoForge;
import org.millenaire.content.ContentDirectoryManager;
import org.millenaire.culture.CultureLoader;
import org.slf4j.Logger;

@EventBusSubscriber(modid = "millenaire", value = Dist.CLIENT)
public final class CustomContentClientSetup {
   private static final Logger LOGGER = LogUtils.getLogger();

   private CustomContentClientSetup() {
   }

   @SubscribeEvent
   public static void onClientSetup(FMLClientSetupEvent event) {
      ContentDirectoryManager.initClient(FMLPaths.GAMEDIR.get());
   }

   public static void onClientLoggingIn(LoggingIn event) {
      Minecraft mc = Minecraft.getInstance();
      if (!mc.hasSingleplayerServer()) {
         try {
            LOGGER.info("Client connecting to a dedicated server — loading local Millénaire content");
            CultureLoader.loadAll();
         } catch (Exception e) {
            LOGGER.error("Failed to load client-side custom content: {}. Custom culture display may be incomplete; gameplay continues.", e.getMessage(), e);
         }
      }
   }

   static {
      NeoForge.EVENT_BUS.addListener(CustomContentClientSetup::onClientLoggingIn);
   }
}
