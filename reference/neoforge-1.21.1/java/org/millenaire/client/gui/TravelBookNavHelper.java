package org.millenaire.client.gui;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.network.TravelBookRequestPayload;
import org.millenaire.village.TravelBookScreenState;
import org.millenaire.village.panel.PanelLine;

public final class TravelBookNavHelper {
   private TravelBookNavHelper() {
   }

   public static void openHelp(@Nullable Screen callingScreen) {
      Minecraft.getInstance().setScreen(new HelpScreen(callingScreen));
   }

   public static void openCulture(@Nullable Screen callingScreen, String cultureKey) {
      TravelBookScreen.setCallingScreen(callingScreen);
      PacketDistributor.sendToServer(new TravelBookRequestPayload(TravelBookScreenState.CULTURE, cultureKey, "", "", 0), new CustomPacketPayload[0]);
   }

   public static void openHome(@Nullable Screen callingScreen) {
      TravelBookScreen.setCallingScreen(callingScreen);
      PacketDistributor.sendToServer(new TravelBookRequestPayload(TravelBookScreenState.HOME, "", "", "", 0), new CustomPacketPayload[0]);
   }

   public static void openVillagerDetail(@Nullable Screen callingScreen, String cultureKey, String villagerTypeKey) {
      TravelBookScreen.setCallingScreen(callingScreen);
      PacketDistributor.sendToServer(
         new TravelBookRequestPayload(TravelBookScreenState.VILLAGER_DETAIL, cultureKey, "villagers", villagerTypeKey, 0), new CustomPacketPayload[0]
      );
   }

   public static void openBuilding(@Nullable Screen callingScreen, String cultureKey, String buildingKey) {
      TravelBookScreen.setCallingScreen(callingScreen);
      PacketDistributor.sendToServer(
         new TravelBookRequestPayload(TravelBookScreenState.BUILDING_DETAIL, cultureKey, "", buildingKey, 0), new CustomPacketPayload[0]
      );
   }

   public static void openVillageType(@Nullable Screen callingScreen, String cultureKey, String villageTypeKey) {
      TravelBookScreen.setCallingScreen(callingScreen);
      PacketDistributor.sendToServer(
         new TravelBookRequestPayload(TravelBookScreenState.VILLAGE_DETAIL, cultureKey, "", villageTypeKey, 0), new CustomPacketPayload[0]
      );
   }

   public static void openFromNavTarget(@Nullable Screen callingScreen, PanelLine.PanelNavTarget target) {
      TravelBookScreen.setCallingScreen(callingScreen);

      TravelBookScreenState state;
      try {
         state = TravelBookScreenState.valueOf(target.targetStateName());
      } catch (IllegalArgumentException e) {
         state = TravelBookScreenState.HOME;
      }

      PacketDistributor.sendToServer(
         new TravelBookRequestPayload(state, target.cultureKey(), target.categoryKey(), target.itemKey(), 0), new CustomPacketPayload[0]
      );
   }
}
