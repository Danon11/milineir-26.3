package org.millenaire.entity;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.advancement.MillAdvancements;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.SpecialPoint;
import org.millenaire.commerce.ShopProfile;
import org.millenaire.commerce.ShopProfileLoader;
import org.millenaire.commerce.TradeGood;
import org.millenaire.commerce.TradeGoodsLoader;
import org.millenaire.commerce.TradeMenu;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.ReputationLabel;
import org.millenaire.culture.VillageType;
import org.millenaire.culture.VillagerType;
import org.millenaire.goal.impl.SellerGoal;
import org.millenaire.item.ItemHelper;
import org.millenaire.item.MoneyHelper;
import org.millenaire.network.QuestOpenPayload;
import org.millenaire.network.VillageChiefPayload;
import org.millenaire.network.VillagerInfoPayload;
import org.millenaire.quest.QuestInstance;
import org.millenaire.quest.QuestItemRef;
import org.millenaire.quest.QuestRegistry;
import org.millenaire.quest.QuestStep;
import org.millenaire.quest.QuestTextRenderer;
import org.millenaire.village.PlayerCultureReputation;
import org.millenaire.village.PlayerQuestData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageRelations;
import org.millenaire.village.VillageReputation;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

public final class VillagerInteraction {
   private static final Logger LOGGER = LogUtils.getLogger();

   private VillagerInteraction() {
   }

   public static void openInfoScreen(ServerPlayer player, MillVillager villager) {
      VillagerType vType = resolveVillagerType(villager);
      MillAdvancements.grant(player, MillAdvancements.FIRST_CONTACT);
      if (vType != null && villager.getVillagerTypeId() != null) {
         String typePath = villager.getVillagerTypeId().getPath();
         if ("indian_sadhu".equals(typePath) || "alchemist".equals(typePath)) {
            MillAdvancements.grant(player, MillAdvancements.MAITRE_A_PENSER);
         }
      }

      ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
      if (overworld != null) {
         PlayerQuestData questData = PlayerQuestData.get(overworld, QuestRegistry::get);
         QuestInstance qi = questData.getQuestForVillager(player.getUUID(), villager.getUUID());
         if (qi != null) {
            UUID stepVillagerId = qi.getCurrentStepVillagerId();
            if (stepVillagerId != null && stepVillagerId.equals(villager.getUUID())) {
               boolean isActivelySelling = villager.getGoalScheduler() != null && SellerGoal.ID.equals(villager.getGoalScheduler().getCurrentGoalId());
               if (!isActivelySelling) {
                  sendQuestPayload(player, villager, qi, overworld);
                  return;
               }

               LOGGER.debug("BUG-226: villager {} is in SellerGoal — routing to trade instead of quest '{}'", villager.getUUID(), qi.getQuest().key());
            } else {
               LOGGER.debug(
                  "Villager {} is in quest '{}' but not current step target (expected={}, got={})",
                  new Object[]{villager.getUUID(), qi.getQuest().key(), stepVillagerId, villager.getUUID()}
               );
            }
         }
      }

      if (vType != null && vType.hasTag("localmerchant")) {
         player.sendSystemMessage(Component.translatable("other.millenaire.localmerchantinteract", new Object[]{villager.getVillagerDisplayName()}));
      } else {
         if (vType != null && vType.hasTag("chief")) {
            sendChiefPayload(player, villager);
         } else if (vType != null && vType.hasTag("foreignmerchant")) {
            sendForeignMerchantTradePayload(player, villager);
         } else if (vType != null && vType.hasTag("seller")) {
            sendTradePayload(player, villager);
         } else {
            sendInfoPayload(player, villager);
         }
      }
   }

   private static void sendInfoPayload(ServerPlayer player, MillVillager villager) {
      Village village = resolveVillage(villager);
      String villageName = buildVillageName(villager, village);
      String cultureName = resolveCultureName(villager);
      int reputation = 0;
      String reputationLabel = "Unknown";
      if (village != null) {
         if (villager.level() instanceof ServerLevel serverLevel) {
            reputation = village.getCombinedReputation(serverLevel, player.getUUID());
         }

         reputationLabel = resolveReputationLabel(reputation, village.getCultureId());
      }

      String goalLabel = villager.getGoalLabel();
      if (goalLabel == null || goalLabel.isEmpty()) {
         goalLabel = "Inactive";
      }

      int languageScore = 0;
      if (!(Boolean)MillenaireServerConfig.SERVER.languageLearning.get()) {
         languageScore = Integer.MAX_VALUE;
      } else if (village != null && villager.level() instanceof ServerLevel sl) {
         languageScore = PlayerCultureReputation.get(sl).getLanguageKnowledge(player.getUUID(), village.getCultureId());
      }

      List<VillagerInfoPayload.InvEntry> invEntries = new ArrayList<>();

      for (Entry<Item, Integer> entry : villager.getInventory().getAll().entrySet()) {
         String itemId = BuiltInRegistries.ITEM.getKey(entry.getKey()).toString();
         invEntries.add(new VillagerInfoPayload.InvEntry(itemId, entry.getValue()));
      }

      List<String> possibleGoals = new ArrayList<>();
      ResourceLocation vtId = villager.getVillagerTypeId();
      VillagerType vType = vtId != null ? ModCultures.getVillagerType(vtId) : null;
      if (vType != null) {
         for (ResourceLocation goalId : vType.goals()) {
            possibleGoals.add("goal.millenaire." + goalId.getPath());
         }
      }

      VillagerInteraction.TravelBookRef tbRef = extractTravelBookRef(villager);
      boolean travelBookVisible = vType != null && vType.travelBookDisplay();
      String nativeOccupation = vType != null ? vType.nativeName() : "";
      VillagerInfoPayload payload = new VillagerInfoPayload(
         villager.getId(),
         villager.getVillagerDisplayName(),
         villager.getRoleName(),
         nativeOccupation,
         villageName,
         goalLabel,
         villager.getHealth(),
         villager.getMaxHealth(),
         reputation,
         reputationLabel,
         cultureName,
         languageScore,
         invEntries,
         possibleGoals,
         tbRef.cultureKey(),
         tbRef.villagerTypeKey(),
         travelBookVisible
      );
      PacketDistributor.sendToPlayer(player, payload, new CustomPacketPayload[0]);
      LOGGER.debug(
         "VillagerInfo payload sent to {} for villager {} ({})",
         new Object[]{player.getName().getString(), villager.getVillagerDisplayName(), villager.getRoleName()}
      );
   }

   private static void sendChiefPayload(ServerPlayer player, MillVillager villager) {
      Village village = resolveVillage(villager);
      String villageName = buildVillageName(villager, village);
      String cultureName = resolveCultureName(villager);
      int reputation = 0;
      String reputationLabel = "Unknown";
      int totalBuildings = 0;
      int completeBuildings = 0;
      int underConstructionBuildings = 0;
      int totalVillagers = 0;
      List<String> buildingEntries = new ArrayList<>();
      if (village != null) {
         if (villager.level() instanceof ServerLevel serverLevel) {
            reputation = village.getCombinedReputation(serverLevel, player.getUUID());
         }

         reputationLabel = resolveReputationLabel(reputation, village.getCultureId());
         totalVillagers = village.getVillagerUuids().size();
         List<BuildingInstance> buildings = village.getBuildings();
         totalBuildings = buildings.size();

         for (BuildingInstance b : buildings) {
            BuildingInstance.Status status = b.getStatus();
            if (status == BuildingInstance.Status.COMPLETE) {
               completeBuildings++;
            } else if (status == BuildingInstance.Status.UNDER_CONSTRUCTION || status == BuildingInstance.Status.UPGRADING) {
               underConstructionBuildings++;
            }

            String displayName = b.getPlanId().getPath();
            BuildingPlanSet bps = ModCultures.getBuildingPlanSet(b.getPlanSetId());
            if (bps != null && bps.nativeName() != null && !bps.nativeName().isEmpty()) {
               displayName = bps.nativeName();
            }

            buildingEntries.add(displayName + "|" + status.name());
         }
      }

      String villageId = village != null ? village.getId().uuid().toString() : "";
      int playerMoney = MoneyHelper.getTotalDeniers(player.getInventory());
      int playerReputation = 0;
      if (village != null && villager.level() instanceof ServerLevel serverLevel) {
         playerReputation = village.getCombinedReputation(serverLevel, player.getUUID());
      }

      List<VillageChiefPayload.PlayerBuildingEntry> playerBuildingEntries = new ArrayList<>();
      if (village != null) {
         VillageType vType = ModCultures.getVillageType(village.getVillageTypeId());
         if (vType != null) {
            for (ResourceLocation pbId : vType.playerBuildings()) {
               BuildingPlanSet pbPlanSet = ModCultures.getBuildingPlanSet(pbId);
               if (pbPlanSet != null && pbPlanSet.price() > 0) {
                  boolean alreadyBuilt = village.getBuildings().stream().anyMatch(b -> pbId.equals(b.getPlanSetId()));
                  String status;
                  if (alreadyBuilt) {
                     status = "BUILT";
                  } else if (village.isBuildingBought(pbId)) {
                     status = "BOUGHT";
                  } else if (reputation < pbPlanSet.reputation()) {
                     status = "NO_REP";
                  } else if (playerMoney < pbPlanSet.price()) {
                     status = "NO_MONEY";
                  } else {
                     status = "AVAILABLE";
                  }

                  playerBuildingEntries.add(
                     new VillageChiefPayload.PlayerBuildingEntry(pbId.toString(), pbPlanSet.nativeName(), pbPlanSet.price(), pbPlanSet.reputation(), status)
                  );
               }
            }
         }
      }

      List<VillageChiefPayload.RelationEntry> relationEntries = new ArrayList<>();
      int diplomacyPts = 0;
      if (village != null && villager.level() instanceof ServerLevel serverLevel) {
         VillageManager vm = VillageSavedData.get(serverLevel).getVillageManager();

         for (Entry<VillageId, Integer> relEntry : village.getRelations().entrySet()) {
            Village otherVillage = vm.getVillage(relEntry.getKey());
            if (otherVillage != null
               && (village.getParentVillageId() == null || !village.getParentVillageId().equals(otherVillage.getId()))
               && (otherVillage.getParentVillageId() == null || !otherVillage.getParentVillageId().equals(village.getId()))) {
               String otherName = otherVillage.getVillageName() != null ? otherVillage.getVillageName() : otherVillage.getVillageTypeId().getPath();
               Culture otherCulture = ModCultures.getCulture(otherVillage.getCultureId());
               String otherCultureName = otherCulture != null ? otherCulture.displayName() : "?";
               int rel = relEntry.getValue();
               String relLabel = VillageRelations.getRelationKey(rel);
               relationEntries.add(new VillageChiefPayload.RelationEntry(relEntry.getKey().uuid().toString(), otherName, otherCultureName, rel, relLabel));
            }
         }

         PlayerCultureReputation cultureRep = PlayerCultureReputation.get(serverLevel);
         diplomacyPts = cultureRep.getDiplomacyPoints(player.getUUID(), village.getId());
      }

      List<VillageChiefPayload.LearningOffer> cropOffers = new ArrayList<>();
      List<VillageChiefPayload.LearningOffer> huntingOffers = new ArrayList<>();
      boolean cultureControlAvailable = false;
      boolean hasCultureControlFlag = false;
      String cultureIdStr = "";
      if (village != null && villager.level() instanceof ServerLevel serverLevel2) {
         Culture culture = ModCultures.getCulture(village.getCultureId());
         if (culture != null) {
            cultureIdStr = culture.id().toString();
            PlayerCultureReputation cultureRep = PlayerCultureReputation.get(serverLevel2);

            for (String cropKey : culture.knownCrops()) {
               String status;
               if (cultureRep.hasLearnedCrop(player.getUUID(), cropKey)) {
                  status = "LEARNED";
               } else if (reputation < 8192) {
                  status = "NO_REP";
               } else if (playerMoney < 512) {
                  status = "NO_MONEY";
               } else {
                  status = "AVAILABLE";
               }

               String translationKey = "millenaire.crop." + cropKey;
               cropOffers.add(new VillageChiefPayload.LearningOffer(cropKey, translationKey, status));
            }

            for (String dropKey : culture.knownHuntingDrops()) {
               String status;
               if (cultureRep.hasLearnedHuntingDrop(player.getUUID(), dropKey)) {
                  status = "LEARNED";
               } else if (reputation < 8192) {
                  status = "NO_REP";
               } else if (playerMoney < 512) {
                  status = "NO_MONEY";
               } else {
                  status = "AVAILABLE";
               }

               String translationKey = "millenaire.hunting." + dropKey;
               huntingOffers.add(new VillageChiefPayload.LearningOffer(dropKey, translationKey, status));
            }

            hasCultureControlFlag = cultureRep.hasCultureControl(player.getUUID(), culture.id());
            cultureControlAvailable = !hasCultureControlFlag && reputation >= 131072;
         }
      }

      VillagerInteraction.TravelBookRef chiefRef = extractTravelBookRef(villager);
      VillageChiefPayload payload = new VillageChiefPayload(
         villager.getId(),
         villager.getVillagerDisplayName(),
         villager.getRoleName(),
         villageName,
         cultureName,
         reputation,
         reputationLabel,
         totalBuildings,
         completeBuildings,
         underConstructionBuildings,
         totalVillagers,
         buildingEntries,
         villageId,
         playerMoney,
         playerReputation,
         playerBuildingEntries,
         relationEntries,
         diplomacyPts,
         cropOffers,
         huntingOffers,
         cultureControlAvailable,
         hasCultureControlFlag,
         cultureIdStr,
         chiefRef.villagerTypeKey()
      );
      PacketDistributor.sendToPlayer(player, payload, new CustomPacketPayload[0]);
      LOGGER.debug(
         "VillageChief payload sent to {} for chief {} (village {})",
         new Object[]{player.getName().getString(), villager.getVillagerDisplayName(), villageName}
      );
   }

   private static void sendTradePayload(ServerPlayer player, MillVillager villager) {
      Village village = resolveVillage(villager);
      if (village == null) {
         LOGGER.warn("No village for seller {} — fallback to info screen", villager.getVillagerTypeId());
         sendInfoPayload(player, villager);
      } else if (village.isControlledBy(player.getUUID())) {
         sendInfoPayload(player, villager);
      } else if (!village.areChestsLocked()) {
         player.sendSystemMessage(Component.translatable("message.millenaire.trade_not_possible"));
      } else {
         int totalRep = 0;
         if (villager.level() instanceof ServerLevel sl) {
            totalRep = village.getCombinedReputation(sl, player.getUUID());
         }

         if (totalRep < -1024) {
            player.sendSystemMessage(Component.translatable("message.millenaire.trade_boycott"));
         } else {
            BuildingInstance shopBuilding = null;
            BlockPos sellingPos = null;
            double bestDistSq = Double.MAX_VALUE;

            for (BuildingInstance b : village.getBuildings()) {
               if (b.isOperational()) {
                  BuildingPlan bPlan = ModCultures.getBuildingPlan(b.getPlanId());
                  if (bPlan != null && bPlan.shopId() != null) {
                     List<SpecialPoint> sellingPoints = b.getPointsByType("sellingPos");
                     if (sellingPoints.isEmpty()) {
                        BlockPos sp = b.getFirstPointPos("sleepingPos");
                        if (sp != null) {
                           double distSq = villager.blockPosition().distSqr(sp);
                           if (distSq < 25.0 && distSq < bestDistSq) {
                              bestDistSq = distSq;
                              shopBuilding = b;
                              sellingPos = sp;
                           }
                        }
                     } else {
                        for (SpecialPoint point : sellingPoints) {
                           double distSq = villager.blockPosition().distSqr(point.pos());
                           if (distSq < 25.0 && distSq < bestDistSq) {
                              bestDistSq = distSq;
                              shopBuilding = b;
                              sellingPos = point.pos();
                           }
                        }
                     }
                  }
               }
            }

            if (shopBuilding == null) {
               LOGGER.warn("No shop within range of seller {} — fallback to info screen", villager.getVillagerTypeId());
               sendInfoPayload(player, villager);
            } else {
               BuildingPlan plan = ModCultures.getBuildingPlan(shopBuilding.getPlanId());
               if (plan != null && plan.shopId() != null) {
                  String shopId = plan.shopId();
                  ResourceLocation cultureId = village.getCultureId();
                  ShopProfile shopProfile = ShopProfileLoader.getProfile(cultureId, shopId);
                  if (shopProfile == null) {
                     LOGGER.warn("No shop profile '{}' for culture {} — fallback to info screen", shopId, cultureId);
                     sendInfoPayload(player, villager);
                  } else {
                     List<TradeGood> tradeCatalog = TradeGoodsLoader.getGoods(cultureId);
                     if (tradeCatalog.isEmpty()) {
                        LOGGER.warn("No traded_goods for culture {} — fallback to info screen", cultureId);
                        sendInfoPayload(player, villager);
                     } else {
                        BuildingInstance finalShop = shopBuilding;
                        ShopProfile finalProfile = shopProfile;
                        List<TradeGood> finalCatalog = tradeCatalog;
                        BlockPos finalSellingPos = sellingPos;
                        TradeMenu previewMenu = new TradeMenu(0, player.getInventory(), village, finalShop, finalProfile, finalCatalog, finalSellingPos);
                        player.openMenu(
                           new SimpleMenuProvider(
                              (containerId, playerInv, p) -> new TradeMenu(
                                 containerId, playerInv, village, finalShop, finalProfile, finalCatalog, finalSellingPos
                              ),
                              Component.translatable("container.millenaire.trade")
                           ),
                           previewMenu::writeToBuffer
                        );
                        LOGGER.debug(
                           "TradeMenu opened for {} in shop {} (village {})", new Object[]{player.getName().getString(), shopId, village.getVillageName()}
                        );
                     }
                  }
               } else {
                  LOGGER.warn("No shopId for building {} — fallback to info screen", shopBuilding.getPlanId());
                  sendInfoPayload(player, villager);
               }
            }
         }
      }
   }

   private static void sendForeignMerchantTradePayload(ServerPlayer player, MillVillager villager) {
      Village village = resolveVillage(villager);
      if (village == null) {
         LOGGER.warn("No village for foreign merchant {} — fallback to info screen", villager.getVillagerTypeId());
         sendInfoPayload(player, villager);
      } else {
         BuildingInstance market = villager.getHomeBuilding() != null ? village.getBuilding(villager.getHomeBuilding()) : null;
         if (market == null) {
            LOGGER.warn("No market building for foreign merchant {} — fallback to info screen", villager.getVillagerTypeId());
            sendInfoPayload(player, villager);
         } else {
            VillagerType vType = resolveVillagerType(villager);
            if (vType != null && !vType.foreignMerchantStock().isEmpty()) {
               ResourceLocation merchantCulture = vType.culture();
               ResourceLocation villageCulture = village.getCultureId();
               boolean crossCulture = !merchantCulture.equals(villageCulture);
               List<TradeGood> merchantGoods = new ArrayList<>();
               List<TradeGood> cultureCatalog = TradeGoodsLoader.getGoods(merchantCulture);

               for (Entry<ResourceLocation, Integer> entry : vType.foreignMerchantStock().entrySet()) {
                  ResourceLocation itemId = entry.getKey();
                  String itemStr = itemId.toString();
                  int foreignPrice = 0;
                  String goodId = itemStr;

                  for (TradeGood tg : cultureCatalog) {
                     if (tg.item().equals(itemStr) && tg.foreignMerchantPrice() > 0) {
                        foreignPrice = tg.foreignMerchantPrice();
                        goodId = tg.id();
                        break;
                     }
                  }

                  if (crossCulture && foreignPrice > 0) {
                     foreignPrice = (int)(foreignPrice * 1.5);
                  }

                  if (foreignPrice > 0) {
                     merchantGoods.add(new TradeGood(goodId, itemStr, foreignPrice, 0, 0, entry.getValue(), false, 0, "merchant", true, foreignPrice));
                  }
               }

               if (merchantGoods.isEmpty()) {
                  sendInfoPayload(player, villager);
               } else {
                  List<String> sellIds = merchantGoods.stream().map(TradeGood::id).toList();
                  ShopProfile merchantProfile = new ShopProfile(sellIds, List.of(), List.of(), List.of());
                  BlockPos finalStallPos = null;
                  BuildingInstance finalMarket = market;
                  ShopProfile finalProfile = merchantProfile;
                  List<TradeGood> finalGoods = merchantGoods;
                  TradeMenu previewMenu = new TradeMenu(0, player.getInventory(), village, finalMarket, finalProfile, finalGoods, finalStallPos);
                  previewMenu.setMerchantCultureId(merchantCulture);
                  player.openMenu(new SimpleMenuProvider((containerId, playerInv, p) -> {
                     TradeMenu menu = new TradeMenu(containerId, playerInv, village, finalMarket, finalProfile, finalGoods, finalStallPos);
                     menu.setMerchantCultureId(merchantCulture);
                     return menu;
                  }, Component.translatable("container.millenaire.trade")), previewMenu::writeToBuffer);
                  LOGGER.debug(
                     "Foreign merchant trade opened for {} with {} (village {})",
                     new Object[]{player.getName().getString(), villager.getVillagerTypeId(), village.getVillageName()}
                  );
               }
            } else {
               sendInfoPayload(player, villager);
            }
         }
      }
   }

   private static void sendQuestPayload(ServerPlayer player, MillVillager villager, QuestInstance qi, ServerLevel overworld) {
      QuestStep step = qi.getCurrentStep();
      if (step != null) {
         String playerName = player.getName().getString();
         String locale = QuestTextRenderer.playerLocale(player);
         String descKey = qi.getQuest().key() + "_" + qi.getCurrentStepIndex() + "_description";
         String inlineDesc = step.descriptions().getOrDefault(locale, step.descriptions().getOrDefault("en", ""));
         String descText = QuestTextRenderer.lookupText(descKey, locale, inlineDesc);
         descText = QuestTextRenderer.substitute(descText, qi, playerName, overworld);
         boolean conditionsMet = true;
         StringBuilder conditionText = new StringBuilder();
         if (!step.requiredGoods().isEmpty()) {
            for (Entry<QuestItemRef, Integer> entry : step.requiredGoods().entrySet()) {
               QuestItemRef ref = entry.getKey();
               int needed = entry.getValue();
               Item item = ItemHelper.resolve(ref.itemId());
               if (item == null) {
                  conditionsMet = false;
               } else {
                  int playerHas = countPlayerItems(player, item);
                  if (playerHas < needed) {
                     conditionsMet = false;
                     if (step.showRequiredGoods()) {
                        if (conditionText.length() > 0) {
                           conditionText.append(", ");
                        }

                        conditionText.append(needed - playerHas).append(" ").append(item.getDescription().getString());
                     }
                  }
               }
            }

            if (!conditionsMet && conditionText.length() > 0) {
               conditionText.insert(0, Component.translatable("gui.millenaire.quest.missing_goods").getString() + ": ");
            } else if (!conditionsMet) {
               conditionText.append(Component.translatable("gui.millenaire.quest.conditions_not_met").getString());
            }
         }

         String nativeOccupation = "";
         String gameOccupation = "";
         VillagerType vType = resolveVillagerType(villager);
         if (vType != null) {
            nativeOccupation = vType.nativeName() != null ? vType.nativeName() : "";
            String roleName = villager.getRoleName();
            if (roleName != null && !roleName.isEmpty()) {
               gameOccupation = roleName.startsWith("role.") ? Component.translatable(roleName).getString() : roleName;
            }
         }

         VillagerInteraction.TravelBookRef questRef = extractTravelBookRef(villager);
         QuestOpenPayload payload = new QuestOpenPayload(
            villager.getId(),
            qi.getUniqueId(),
            villager.getVillagerDisplayName(),
            nativeOccupation,
            gameOccupation,
            descText,
            conditionText.toString(),
            conditionsMet,
            qi.getCurrentStepIndex() == 0,
            qi.getCurrentStepIndex(),
            questRef.cultureKey(),
            questRef.villagerTypeKey()
         );
         PacketDistributor.sendToPlayer(player, payload, new CustomPacketPayload[0]);
         LOGGER.debug(
            "QuestOpen payload sent to {} for quest {} step {}", new Object[]{player.getName().getString(), qi.getQuest().key(), qi.getCurrentStepIndex()}
         );
      }
   }

   private static int countPlayerItems(ServerPlayer player, Item item) {
      int count = 0;

      for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (stack.is(item)) {
            count += stack.getCount();
         }
      }

      return count;
   }

   @Nullable
   private static VillagerType resolveVillagerType(MillVillager villager) {
      ResourceLocation typeId = villager.getVillagerTypeId();
      return typeId == null ? null : ModCultures.getVillagerType(typeId);
   }

   private static VillagerInteraction.TravelBookRef extractTravelBookRef(MillVillager villager) {
      ResourceLocation vtId = villager.getVillagerTypeId();
      return vtId == null
         ? VillagerInteraction.TravelBookRef.EMPTY
         : new VillagerInteraction.TravelBookRef(ModCultures.extractCultureId(vtId).getPath(), vtId.getPath());
   }

   @Nullable
   private static Village resolveVillage(MillVillager villager) {
      if (villager.getVillageId() == null) {
         return null;
      } else {
         return villager.level() instanceof ServerLevel serverLevel ? Village.resolve(serverLevel, villager.getVillageId()) : null;
      }
   }

   private static String buildVillageName(MillVillager villager, @Nullable Village village) {
      if (village == null) {
         return "Unaffiliated";
      } else {
         return village.getVillageName() != null ? village.getVillageName() : resolveCultureName(villager);
      }
   }

   private static String resolveCultureName(MillVillager villager) {
      ResourceLocation vtId = villager.getVillagerTypeId();
      if (vtId == null) {
         return "Unknown";
      }

      ResourceLocation cultureId = ModCultures.extractCultureId(vtId);
      Culture culture = ModCultures.getCulture(cultureId);
      if (culture != null) {
         return culture.displayName();
      }

      String cp = cultureId.getPath();
      return cp.substring(0, 1).toUpperCase() + cp.substring(1);
   }

   private static String resolveReputationLabel(int reputation, ResourceLocation cultureId) {
      List<ReputationLabel> labels = ModCultures.getReputationLabels(cultureId);
      String label = VillageReputation.getLabel(reputation, labels);
      return label != null ? label : "Unknown";
   }

   private record TravelBookRef(String cultureKey, String villagerTypeKey) {
      static final VillagerInteraction.TravelBookRef EMPTY = new VillagerInteraction.TravelBookRef("", "");
   }
}
