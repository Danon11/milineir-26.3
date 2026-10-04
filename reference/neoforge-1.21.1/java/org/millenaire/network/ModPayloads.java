package org.millenaire.network;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.millenaire.village.BuildingPurchaseService;
import org.millenaire.village.VillageBookService;

public final class ModPayloads {
   private ModPayloads() {
   }

   public static void register(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar registrar = event.registrar("2");
      if (FMLEnvironment.dist.isClient()) {
         ModPayloads.ClientHandlers.register(registrar);
      } else {
         registrar.playToClient(VillagerInfoPayload.TYPE, VillagerInfoPayload.STREAM_CODEC, (p, c) -> {});
         registrar.playToClient(VillageChiefPayload.TYPE, VillageChiefPayload.STREAM_CODEC, (p, c) -> {});
         registrar.playToClient(TradePayload.TYPE, TradePayload.STREAM_CODEC, (p, c) -> {});
         registrar.playToClient(TradeStockUpdatePayload.TYPE, TradeStockUpdatePayload.STREAM_CODEC, (p, c) -> {});
         registrar.playToClient(PanelContentPayload.TYPE, PanelContentPayload.STREAM_CODEC, (p, c) -> {});
         registrar.playToClient(VillageBookPayload.TYPE, VillageBookPayload.STREAM_CODEC, (p, c) -> {});
         registrar.playToClient(ImportTableSyncPayload.TYPE, ImportTableSyncPayload.STREAM_CODEC, (p, c) -> {});
         registrar.playToClient(ImportTableCostsPayload.TYPE, ImportTableCostsPayload.STREAM_CODEC, (p, c) -> {});
         registrar.playToClient(TravelBookContentPayload.TYPE, TravelBookContentPayload.STREAM_CODEC, (p, c) -> {});
         registrar.playToClient(FireplacePositionsPayload.TYPE, FireplacePositionsPayload.STREAM_CODEC, (p, c) -> {});
         registrar.playToClient(SpeechChatPayload.TYPE, SpeechChatPayload.STREAM_CODEC, (p, c) -> {});
         registrar.playToClient(VillageTypeListPayload.TYPE, VillageTypeListPayload.STREAM_CODEC, (p, c) -> {});
         registrar.playToClient(InfoPanelContentPayload.TYPE, InfoPanelContentPayload.STREAM_CODEC, (p, c) -> {});
         registrar.playToClient(QuestInstanceSyncPayload.TYPE, QuestInstanceSyncPayload.STREAM_CODEC, (p, c) -> {});
         registrar.playToClient(QuestInstanceDestroyPayload.TYPE, QuestInstanceDestroyPayload.STREAM_CODEC, (p, c) -> {});
         registrar.playToClient(QuestResultTextPayload.TYPE, QuestResultTextPayload.STREAM_CODEC, (p, c) -> {});
         registrar.playToClient(QuestOpenPayload.TYPE, QuestOpenPayload.STREAM_CODEC, (p, c) -> {});
         registrar.playToClient(WandDebugMenuPayload.TYPE, WandDebugMenuPayload.STREAM_CODEC, (p, c) -> {});
         registrar.playToClient(NegationWandPayload.TYPE, NegationWandPayload.STREAM_CODEC, (p, c) -> {});
         registrar.playToClient(BuildingProjectListPayload.TYPE, BuildingProjectListPayload.STREAM_CODEC, (p, c) -> {});
         registrar.playToClient(ControlledProjectsPayload.TYPE, ControlledProjectsPayload.STREAM_CODEC, (p, c) -> {});
      }

      registrar.playToServer(VillageScrollPurchasePayload.TYPE, VillageScrollPurchasePayload.STREAM_CODEC, VillageBookService::handleScrollPurchasePacket);
      registrar.playToServer(BuildingPurchasePayload.TYPE, BuildingPurchasePayload.STREAM_CODEC, BuildingPurchaseService::handlePurchasePacket);
      registrar.playToServer(ImportTableActionPayload.TYPE, ImportTableActionPayload.STREAM_CODEC, ImportTableActionHandler::handleAction);
      registrar.playToServer(TravelBookRequestPayload.TYPE, TravelBookRequestPayload.STREAM_CODEC, TravelBookRequestPayload::handleOnServer);
      registrar.playToServer(DiplomacyActionPayload.TYPE, DiplomacyActionPayload.STREAM_CODEC, DiplomacyActionPayload::handleOnServer);
      registrar.playToServer(VillageCreationRequestPayload.TYPE, VillageCreationRequestPayload.STREAM_CODEC, VillageCreationRequestPayload::handleOnServer);
      registrar.playToServer(CropLearningPayload.TYPE, CropLearningPayload.STREAM_CODEC, CropLearningPayload::handleOnServer);
      registrar.playToServer(HuntingLearningPayload.TYPE, HuntingLearningPayload.STREAM_CODEC, HuntingLearningPayload::handleOnServer);
      registrar.playToServer(CultureControlPurchasePayload.TYPE, CultureControlPurchasePayload.STREAM_CODEC, CultureControlPurchasePayload::handleOnServer);
      registrar.playToServer(InfoPanelRequestPayload.TYPE, InfoPanelRequestPayload.STREAM_CODEC, InfoPanelRequestPayload::handleOnServer);
      registrar.playToServer(QuestCompleteStepPayload.TYPE, QuestCompleteStepPayload.STREAM_CODEC, QuestCompleteStepPayload::handle);
      registrar.playToServer(QuestRefusePayload.TYPE, QuestRefusePayload.STREAM_CODEC, QuestRefusePayload::handleOnServer);
      registrar.playToServer(WandDebugActionPayload.TYPE, WandDebugActionPayload.STREAM_CODEC, WandDebugActionPayload::handleOnServer);
      registrar.playToServer(NegationWandConfirmPayload.TYPE, NegationWandConfirmPayload.STREAM_CODEC, NegationWandConfirmPayload::handleOnServer);
      registrar.playToServer(BuildingProjectRequestPayload.TYPE, BuildingProjectRequestPayload.STREAM_CODEC, BuildingProjectRequestPayload::handleOnServer);
      registrar.playToServer(
         BuildingUpgradeToggleRequestPayload.TYPE, BuildingUpgradeToggleRequestPayload.STREAM_CODEC, BuildingUpgradeToggleRequestPayload::handleOnServer
      );
      registrar.playToServer(
         BuildingProjectCancelRequestPayload.TYPE, BuildingProjectCancelRequestPayload.STREAM_CODEC, BuildingProjectCancelRequestPayload::handleOnServer
      );
   }

   private static final class ClientHandlers {
      static void register(PayloadRegistrar registrar) {
         registrar.playToClient(VillagerInfoPayload.TYPE, VillagerInfoPayload.STREAM_CODEC, ClientPayloadHandler::handleVillagerInfo);
         registrar.playToClient(VillageChiefPayload.TYPE, VillageChiefPayload.STREAM_CODEC, ClientPayloadHandler::handleVillageChief);
         registrar.playToClient(TradePayload.TYPE, TradePayload.STREAM_CODEC, ClientPayloadHandler::handleTrade);
         registrar.playToClient(TradeStockUpdatePayload.TYPE, TradeStockUpdatePayload.STREAM_CODEC, ClientPayloadHandler::handleTradeStockUpdate);
         registrar.playToClient(PanelContentPayload.TYPE, PanelContentPayload.STREAM_CODEC, ClientPayloadHandler::handlePanelContent);
         registrar.playToClient(VillageBookPayload.TYPE, VillageBookPayload.STREAM_CODEC, ClientPayloadHandler::handleVillageBook);
         registrar.playToClient(ImportTableSyncPayload.TYPE, ImportTableSyncPayload.STREAM_CODEC, ClientPayloadHandler::handleImportTableSync);
         registrar.playToClient(ImportTableCostsPayload.TYPE, ImportTableCostsPayload.STREAM_CODEC, ClientPayloadHandler::handleImportTableCosts);
         registrar.playToClient(TravelBookContentPayload.TYPE, TravelBookContentPayload.STREAM_CODEC, ClientPayloadHandler::handleTravelBook);
         registrar.playToClient(FireplacePositionsPayload.TYPE, FireplacePositionsPayload.STREAM_CODEC, ClientPayloadHandler::handleFireplacePositions);
         registrar.playToClient(SpeechChatPayload.TYPE, SpeechChatPayload.STREAM_CODEC, ClientPayloadHandler::handleSpeechChat);
         registrar.playToClient(VillageTypeListPayload.TYPE, VillageTypeListPayload.STREAM_CODEC, ClientPayloadHandler::handleVillageTypeList);
         registrar.playToClient(InfoPanelContentPayload.TYPE, InfoPanelContentPayload.STREAM_CODEC, ClientPayloadHandler::handleInfoPanel);
         registrar.playToClient(QuestInstanceSyncPayload.TYPE, QuestInstanceSyncPayload.STREAM_CODEC, ClientPayloadHandler::handleQuestSync);
         registrar.playToClient(QuestInstanceDestroyPayload.TYPE, QuestInstanceDestroyPayload.STREAM_CODEC, ClientPayloadHandler::handleQuestDestroy);
         registrar.playToClient(QuestResultTextPayload.TYPE, QuestResultTextPayload.STREAM_CODEC, ClientPayloadHandler::handleQuestResult);
         registrar.playToClient(QuestOpenPayload.TYPE, QuestOpenPayload.STREAM_CODEC, ClientPayloadHandler::handleQuestOpen);
         registrar.playToClient(WandDebugMenuPayload.TYPE, WandDebugMenuPayload.STREAM_CODEC, ClientPayloadHandler::handleWandDebugMenu);
         registrar.playToClient(NegationWandPayload.TYPE, NegationWandPayload.STREAM_CODEC, ClientPayloadHandler::handleNegationWand);
         registrar.playToClient(BuildingProjectListPayload.TYPE, BuildingProjectListPayload.STREAM_CODEC, ClientPayloadHandler::handleBuildingProjectList);
         registrar.playToClient(ControlledProjectsPayload.TYPE, ControlledProjectsPayload.STREAM_CODEC, ClientPayloadHandler::handleControlledProjects);
      }
   }
}
