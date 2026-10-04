package org.millenaire.network;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.client.ClientLanguageCache;
import org.millenaire.client.ClientQuestCache;
import org.millenaire.client.FireplaceSmokeHandler;
import org.millenaire.client.gui.ControlledProjectsScreen;
import org.millenaire.client.gui.ImportTableScreen;
import org.millenaire.client.gui.InfoPanelScreen;
import org.millenaire.client.gui.NegationWandScreen;
import org.millenaire.client.gui.NewBuildingProjectScreen;
import org.millenaire.client.gui.NewVillageScreen;
import org.millenaire.client.gui.QuestScreen;
import org.millenaire.client.gui.TravelBookScreen;
import org.millenaire.client.gui.VillageBookScreen;
import org.millenaire.client.gui.VillageChiefScreen;
import org.millenaire.client.gui.VillagePanelScreen;
import org.millenaire.client.gui.VillagerInfoScreen;
import org.millenaire.client.gui.WandDebugScreen;
import org.millenaire.commerce.TradeMenu;
import org.millenaire.language.DisplayNameResolver;
import org.millenaire.language.SentenceRenderer;
import org.millenaire.language.SpeechResolver;
import org.slf4j.Logger;

public final class ClientPayloadHandler {
   private static final Logger LOGGER = LogUtils.getLogger();

   private ClientPayloadHandler() {
   }

   public static void handleVillagerInfo(VillagerInfoPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", payload.cultureKey());
         ClientLanguageCache.update(cultureId, payload.languageScore());
         Minecraft.getInstance().setScreen(new VillagerInfoScreen(payload));
      });
   }

   public static void handleVillageChief(VillageChiefPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> Minecraft.getInstance().setScreen(new VillageChiefScreen(payload)));
   }

   public static void handleTrade(TradePayload payload, IPayloadContext context) {
      LOGGER.debug("TradePayload received (legacy stub, ignored) — trade uses menu system");
   }

   public static void handleTradeStockUpdate(TradeStockUpdatePayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && mc.player.containerMenu instanceof TradeMenu tradeMenu && tradeMenu.containerId == payload.containerId()) {
            tradeMenu.updateStocks(payload.stocks(), payload.donationMode());
         }
      });
   }

   public static void handleVillageBook(VillageBookPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> Minecraft.getInstance().setScreen(new VillageBookScreen(payload)));
   }

   public static void handleImportTableSync(ImportTableSyncPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> Minecraft.getInstance().setScreen(new ImportTableScreen(payload)));
   }

   public static void handleImportTableCosts(ImportTableCostsPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> ImportTableScreen.applyCosts(payload));
   }

   public static void handleTravelBook(TravelBookContentPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> TravelBookScreen.applyContent(payload));
   }

   public static void handleFireplacePositions(FireplacePositionsPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> FireplaceSmokeHandler.updatePositions(payload.villageId(), payload.villageCenter(), payload.positions()));
   }

   public static void handleSpeechChat(SpeechChatPayload payload, IPayloadContext context) {
      context.enqueueWork(
         () -> {
            Minecraft mc = Minecraft.getInstance();
            String prefix = payload.villagerName() + ": ";
            ResourceLocation payloadCultureId = ResourceLocation.fromNamespaceAndPath("millenaire", payload.cultureKey());
            ClientLanguageCache.update(payloadCultureId, payload.languageScore());
            String[] speechParts = SpeechResolver.resolve(payload.speechRef(), payloadCultureId, payload.languageScore());
            String nativeText = speechParts[0];
            String translation = speechParts[1];
            if (translation == null && payload.vanillaFallbackKey() != null && !payload.vanillaFallbackKey().isEmpty()) {
               String cultureSpecificKey = payload.vanillaFallbackKey() + "." + payload.cultureKey();
               String resolvedKey = I18n.exists(cultureSpecificKey) ? cultureSpecificKey : payload.vanillaFallbackKey();
               String vanilla = Component.translatable(resolvedKey, new Object[]{payload.villagerName()}).getString();
               double ratio = SentenceRenderer.languageRatio(payload.languageScore());
               if (ratio >= 1.0) {
                  translation = vanilla;
               } else if (ratio > 0.0) {
                  translation = SentenceRenderer.maskTranslation(vanilla, ratio);
               }
            }

            if (DisplayNameResolver.equivalent(nativeText, translation)) {
               translation = null;
            }

            if (nativeText != null) {
               MutableComponent msg = Component.empty()
                  .append(Component.literal(prefix).withStyle(s -> s.withColor(16777215)))
                  .append(Component.literal(nativeText).withStyle(s -> s.withColor(5592575)));
               if (translation != null) {
                  msg.append(Component.literal(" ").withStyle(s -> s.withColor(16777215)))
                     .append(Component.literal(translation).withStyle(s -> s.withColor(11141120)));
               }

               mc.player.sendSystemMessage(msg);
            }
         }
      );
   }

   public static void handleVillageTypeList(VillageTypeListPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> Minecraft.getInstance().setScreen(new NewVillageScreen(payload)));
   }

   public static void handleInfoPanel(InfoPanelContentPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> Minecraft.getInstance().setScreen(new InfoPanelScreen(payload)));
   }

   public static void handlePanelContent(PanelContentPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (payload.hasMapData()) {
            Minecraft.getInstance().setScreen(new VillagePanelScreen(payload.toContent(), payload));
         } else {
            Minecraft.getInstance().setScreen(new VillagePanelScreen(payload.toContent()));
         }
      });
   }

   public static void handleQuestSync(QuestInstanceSyncPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> ClientQuestCache.update(payload));
   }

   public static void handleQuestDestroy(QuestInstanceDestroyPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> ClientQuestCache.remove(payload.uniqueId()));
   }

   public static void handleQuestResult(QuestResultTextPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         Minecraft mc = Minecraft.getInstance();
         if (mc.screen instanceof QuestScreen questScreen) {
            questScreen.onQuestResult(payload.resultText(), payload.isSuccess());
         } else {
            LOGGER.debug("Quest result received but no QuestScreen open: questId={}, success={}", payload.questUniqueId(), payload.isSuccess());
         }
      });
   }

   public static void handleQuestOpen(QuestOpenPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> Minecraft.getInstance().setScreen(new QuestScreen(payload)));
   }

   public static void handleNegationWand(NegationWandPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> Minecraft.getInstance().setScreen(new NegationWandScreen(payload)));
   }

   public static void handleBuildingProjectList(BuildingProjectListPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> Minecraft.getInstance().setScreen(new NewBuildingProjectScreen(payload)));
   }

   public static void handleControlledProjects(ControlledProjectsPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> Minecraft.getInstance().setScreen(new ControlledProjectsScreen(payload)));
   }

   public static void handleWandDebugMenu(WandDebugMenuPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> Minecraft.getInstance().setScreen(new WandDebugScreen(payload)));
   }
}
