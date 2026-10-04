package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.DisplayUtils;
import org.millenaire.building.AnywoodHelper;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.commerce.ShopProfile;
import org.millenaire.commerce.ShopProfileLoader;
import org.millenaire.commerce.TradeGood;
import org.millenaire.commerce.TradeGoodsLoader;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.TravelBookCategories;
import org.millenaire.culture.VillageType;
import org.millenaire.culture.VillagerType;
import org.millenaire.discovery.DiscoveryTracker;
import org.millenaire.item.ItemHelper;
import org.millenaire.item.MoneyHelper;
import org.millenaire.language.BuildingNameHelper;
import org.millenaire.language.LanguageHelper;
import org.millenaire.network.TravelBookContentPayload;
import org.millenaire.network.TravelBookRequestPayload;
import org.millenaire.quest.QuestInstance;
import org.millenaire.quest.QuestRegistry;
import org.millenaire.quest.QuestStep;
import org.millenaire.quest.QuestTextRenderer;
import org.slf4j.Logger;

public final class TravelBookContentBuilder {
   private static final Logger LOGGER = LogUtils.getLogger();

   private TravelBookContentBuilder() {
   }

   public static void handleRequest(ServerPlayer player, TravelBookRequestPayload payload) {
      UUID playerId = player.getUUID();
      int navAction = payload.navAction();
      TravelBookScreenState targetState;
      String culture;
      String category;
      String item;
      switch (navAction) {
         case 1:
            TravelBookNavigationState.NavEntry prev = TravelBookNavigationState.goBack(playerId);
            if (prev == null) {
               targetState = TravelBookScreenState.HOME;
               culture = "";
               category = "";
               item = "";
            } else {
               targetState = prev.state();
               culture = prev.culture();
               category = prev.category();
               item = prev.item();
            }
            break;
         case 2:
            String nextItem = TravelBookNavigationState.getNextItem(playerId);
            if (nextItem == null) {
               targetState = TravelBookNavigationState.getCurrentState(playerId);
               culture = TravelBookNavigationState.getCurrentCulture(playerId);
               category = TravelBookNavigationState.getCurrentCategory(playerId);
               item = TravelBookNavigationState.getCurrentItem(playerId);
            } else {
               targetState = TravelBookNavigationState.getCurrentState(playerId);
               culture = TravelBookNavigationState.getCurrentCulture(playerId);
               category = TravelBookNavigationState.getCurrentCategory(playerId);
               item = nextItem;
               TravelBookNavigationState.navigate(playerId, targetState, culture, category, item);
            }
            break;
         case 3:
            String prevItem = TravelBookNavigationState.getPrevItem(playerId);
            if (prevItem == null) {
               targetState = TravelBookNavigationState.getCurrentState(playerId);
               culture = TravelBookNavigationState.getCurrentCulture(playerId);
               category = TravelBookNavigationState.getCurrentCategory(playerId);
               item = TravelBookNavigationState.getCurrentItem(playerId);
            } else {
               targetState = TravelBookNavigationState.getCurrentState(playerId);
               culture = TravelBookNavigationState.getCurrentCulture(playerId);
               category = TravelBookNavigationState.getCurrentCategory(playerId);
               item = prevItem;
               TravelBookNavigationState.navigate(playerId, targetState, culture, category, item);
            }
            break;
         default:
            targetState = payload.targetState();
            culture = payload.cultureKey();
            category = payload.categoryKey();
            item = payload.itemKey();
            TravelBookNavigationState.navigate(playerId, targetState, culture, category, item);
      }

      List<TravelBookLine> lines = buildContent(player, targetState, culture, category, item);
      boolean hasBack = TravelBookNavigationState.hasBack(playerId);
      boolean hasNext = TravelBookNavigationState.hasNext(playerId);
      boolean hasPrev = TravelBookNavigationState.hasPrev(playerId);
      TravelBookContentBuilder.TitleResult pageTitle = buildPageTitle(player, targetState, culture, category, item);
      TravelBookContentBuilder.MockAppearance mock = targetState == TravelBookScreenState.VILLAGER_DETAIL
         ? buildMockAppearance(culture, item)
         : TravelBookContentBuilder.MockAppearance.EMPTY;
      TravelBookContentPayload response = new TravelBookContentPayload(
         targetState,
         lines,
         hasBack,
         hasNext,
         hasPrev,
         pageTitle.text(),
         pageTitle.translatable(),
         mock.modelType(),
         mock.texture(),
         mock.cloth0(),
         mock.cloth1(),
         mock.scale(),
         mock.heldItem(),
         mock.heldItemOffHand()
      );
      PacketDistributor.sendToPlayer(player, response, new CustomPacketPayload[0]);
   }

   private static List<TravelBookLine> buildContent(ServerPlayer player, TravelBookScreenState state, String culture, String category, String item) {
      return switch (state) {
         case HOME -> buildHome(player);
         case CULTURE -> buildCulture(player, culture);
         case VILLAGERS_LIST -> buildVillagersList(player, culture, category);
         case VILLAGER_DETAIL -> buildVillagerDetail(player, culture, item);
         case VILLAGES_LIST -> buildVillagesList(player, culture);
         case VILLAGE_DETAIL -> buildVillageDetail(player, culture, item);
         case BUILDINGS_LIST -> buildBuildingsList(player, culture, category);
         case BUILDING_DETAIL -> buildBuildingDetail(player, culture, item);
         case TRADE_GOODS_LIST -> buildTradeGoodsList(player, culture, category);
         case TRADE_GOOD_DETAIL -> buildTradeGoodDetail(player, culture, item);
      };
   }

   private static TravelBookContentBuilder.TitleResult buildPageTitle(
      ServerPlayer player, TravelBookScreenState state, String culture, String category, String item
   ) {
      boolean learning = isLearningMode();
      DiscoveryTracker tracker = learning ? getTracker(player) : null;
      UUID playerId = player.getUUID();

      return switch (state) {
         case HOME -> TravelBookContentBuilder.TitleResult.literal(t("millenaire.travel_book.title"));
         case CULTURE -> TravelBookContentBuilder.TitleResult.literal(getCultureDisplayName(culture));
         case VILLAGERS_LIST -> TravelBookContentBuilder.TitleResult.literal(getCultureDisplayName(culture) + " - " + resolveCategoryName(culture, category));
         case VILLAGER_DETAIL -> learning && tracker != null && !tracker.isVillagerUnlocked(playerId, culture, item)
            ? TravelBookContentBuilder.TitleResult.literal(t("millenaire.travel_book.unknown_villager"))
            : TravelBookContentBuilder.TitleResult.literal(getVillagerDisplayName(culture, item));
         case VILLAGES_LIST -> TravelBookContentBuilder.TitleResult.literal(getCultureDisplayName(culture) + " - " + t("millenaire.travel_book.villages"));
         case VILLAGE_DETAIL -> learning && tracker != null && !tracker.isVillageUnlocked(playerId, culture, item)
            ? TravelBookContentBuilder.TitleResult.literal(t("millenaire.travel_book.unknown_village"))
            : TravelBookContentBuilder.TitleResult.literal(getVillageDisplayName(culture, item));
         case BUILDINGS_LIST -> TravelBookContentBuilder.TitleResult.literal(getCultureDisplayName(culture) + " - " + resolveCategoryName(culture, category));
         case BUILDING_DETAIL -> {
            if (learning && tracker != null && !tracker.isBuildingUnlocked(playerId, culture, item)) {
               yield TravelBookContentBuilder.TitleResult.literal(t("millenaire.travel_book.unknown_building"));
            } else {
               ResourceLocation bpsId = ResourceLocation.fromNamespaceAndPath("millenaire", item);
               BuildingPlanSet bps = ModCultures.getBuildingPlanSet(bpsId);
               yield bps != null && LanguageHelper.canReadBuildingNames(player, bps.culture())
                  ? TravelBookContentBuilder.TitleResult.translatable(getBuildingTranslationKey(culture, item))
                  : TravelBookContentBuilder.TitleResult.literal(bps != null ? bps.nativeName() : item);
            }
         }
         case TRADE_GOODS_LIST -> TravelBookContentBuilder.TitleResult.literal(getCultureDisplayName(culture) + " - " + resolveCategoryName(culture, category));
         case TRADE_GOOD_DETAIL -> learning && tracker != null && !tracker.isTradeGoodUnlocked(playerId, culture, item)
            ? TravelBookContentBuilder.TitleResult.literal(t("millenaire.travel_book.unknown_trade_good"))
            : TravelBookContentBuilder.TitleResult.literal(getTradeGoodDisplayName(culture, item));
      };
   }

   private static String resolveCategoryName(String cultureKey, String categoryKey) {
      if (categoryKey != null && !categoryKey.isEmpty()) {
         Culture culture = ModCultures.getCulture(cultureId(cultureKey));
         return culture == null ? formatCategoryName(categoryKey) : resolveCatName(culture.travelBookCategories(), categoryKey);
      } else {
         return "";
      }
   }

   private static String resolveCatName(TravelBookCategories categories, String categoryKey) {
      String i18nKey = categories.categoryNames().getOrDefault(categoryKey, "");
      return i18nKey.isEmpty() ? formatCategoryName(categoryKey) : t(i18nKey);
   }

   private static List<TravelBookLine> buildHome(ServerPlayer player) {
      List<TravelBookLine> lines = new ArrayList<>();
      lines.add(TravelBookLine.text(t("millenaire.travel_book.intro")));
      lines.add(TravelBookLine.separator());

      for (Entry<ResourceLocation, Culture> entry : ModCultures.getAllCultures()
         .entrySet()
         .stream()
         .sorted(Comparator.comparing(e -> e.getValue().displayName()))
         .toList()) {
         Culture culture = entry.getValue();
         String cultureKey = entry.getKey().getPath();
         TravelBookLine.TravelBookNavTarget target = new TravelBookLine.TravelBookNavTarget(TravelBookScreenState.CULTURE, cultureKey, "", "");
         lines.add(TravelBookLine.clickable(getCultureDisplayName(cultureKey), target));
      }

      appendQuestSection(player, lines);
      return lines;
   }

   private static void appendQuestSection(ServerPlayer player, List<TravelBookLine> lines) {
      ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
      if (overworld != null) {
         PlayerQuestData questData = PlayerQuestData.get(overworld, QuestRegistry::get);
         List<QuestInstance> activeQuests = questData.getActiveQuests(player.getUUID());
         if (!activeQuests.isEmpty()) {
            lines.add(TravelBookLine.separator());
            lines.add(TravelBookLine.translatable("millenaire.travel_book.quests_in_progress"));
            long worldTime = overworld.getDayTime();
            String playerName = player.getName().getString();
            String locale = QuestTextRenderer.playerLocale(player);

            for (QuestInstance qi : activeQuests) {
               QuestStep step = qi.getCurrentStep();
               if (step != null) {
                  String labelKey = qi.getQuest().key() + "_" + qi.getCurrentStepIndex() + "_label";
                  String inlineLabel = step.labels().getOrDefault(locale, step.labels().getOrDefault("en", qi.getQuest().key()));
                  String label = QuestTextRenderer.lookupText(labelKey, locale, inlineLabel);
                  label = QuestTextRenderer.substitute(label, qi, playerName, overworld);
                  lines.add(TravelBookLine.text("§6" + label));
                  String listingKey = qi.getQuest().key() + "_" + qi.getCurrentStepIndex() + "_listing";
                  String inlineListing = step.listings().getOrDefault(locale, step.listings().getOrDefault("en", ""));
                  String listing = QuestTextRenderer.lookupText(listingKey, locale, inlineListing);
                  if (!listing.isEmpty()) {
                     listing = QuestTextRenderer.substitute(listing, qi, playerName, overworld);
                     lines.add(TravelBookLine.text("  " + listing));
                  }

                  long timeLeftHours = Math.round((qi.getCurrentStepStart() + step.duration() * 1000L - worldTime) / 1000.0);
                  if (timeLeftHours <= 0L) {
                     lines.add(TravelBookLine.translatable("millenaire.travel_book.quest_time_expired"));
                  } else {
                     lines.add(
                        TravelBookLine.text(
                           "  " + t("millenaire.travel_book.quest_time_remaining") + ": " + timeLeftHours + " " + t("millenaire.travel_book.quest_hours")
                        )
                     );
                  }
               }
            }
         }
      }
   }

   private static List<TravelBookLine> buildCulture(ServerPlayer player, String cultureKey) {
      List<TravelBookLine> lines = new ArrayList<>();
      ResourceLocation cultureId = cultureId(cultureKey);
      Culture culture = ModCultures.getCulture(cultureId);
      if (culture == null) {
         lines.add(TravelBookLine.text("Unknown culture: " + cultureKey));
         return lines;
      }

      lines.add(TravelBookLine.text(getCultureDisplayName(cultureKey)));
      lines.add(TravelBookLine.separator());
      TravelBookCategories categories = culture.travelBookCategories();
      if (!categories.villagerCategories().isEmpty()) {
         lines.add(TravelBookLine.text(t("millenaire.travel_book.villagers")));

         for (String cat : categories.villagerCategories()) {
            String catName = resolveCatName(categories, cat);
            TravelBookLine.TravelBookNavTarget target = new TravelBookLine.TravelBookNavTarget(TravelBookScreenState.VILLAGERS_LIST, cultureKey, cat, "");
            String icon = categories.categoryIcons().getOrDefault(cat, "");
            if (!icon.isEmpty()) {
               lines.add(TravelBookLine.clickableWithIcon(catName, "", icon, target));
            } else {
               lines.add(TravelBookLine.clickable("  " + catName, target));
            }
         }
      }

      lines.add(TravelBookLine.separator());
      lines.add(
         TravelBookLine.clickable(
            t("millenaire.travel_book.villages"), new TravelBookLine.TravelBookNavTarget(TravelBookScreenState.VILLAGES_LIST, cultureKey, "", "")
         )
      );
      if (!categories.buildingCategories().isEmpty()) {
         lines.add(TravelBookLine.separator());
         lines.add(TravelBookLine.text(t("millenaire.travel_book.buildings")));

         for (String cat : categories.buildingCategories()) {
            String catName = resolveCatName(categories, cat);
            TravelBookLine.TravelBookNavTarget target = new TravelBookLine.TravelBookNavTarget(TravelBookScreenState.BUILDINGS_LIST, cultureKey, cat, "");
            lines.add(TravelBookLine.clickable("  " + catName, target));
         }
      }

      if (!categories.tradeGoodCategories().isEmpty()) {
         lines.add(TravelBookLine.separator());
         lines.add(TravelBookLine.text(t("millenaire.travel_book.trade_goods")));

         for (String cat : categories.tradeGoodCategories()) {
            String catName = resolveCatName(categories, cat);
            TravelBookLine.TravelBookNavTarget target = new TravelBookLine.TravelBookNavTarget(TravelBookScreenState.TRADE_GOODS_LIST, cultureKey, cat, "");
            lines.add(TravelBookLine.clickable("  " + catName, target));
         }
      }

      return lines;
   }

   private static List<TravelBookLine> buildVillagersList(ServerPlayer player, String cultureKey, String category) {
      List<TravelBookLine> lines = new ArrayList<>();
      ResourceLocation cultureId = cultureId(cultureKey);
      boolean learning = isLearningMode();
      DiscoveryTracker tracker = getTracker(player);
      UUID playerId = player.getUUID();
      List<VillagerType> types = ModCultures.getAllVillagerTypes()
         .values()
         .stream()
         .filter(vtx -> vtx.culture().equals(cultureId))
         .filter(VillagerType::travelBookDisplay)
         .filter(vtx -> matchesCategory(vtx.travelBookCategory(), category))
         .sorted(Comparator.comparing(VillagerType::nativeName))
         .toList();
      List<String> itemKeys = types.stream().map(vtx -> vtx.id().getPath()).toList();
      TravelBookNavigationState.setCurrentCategoryItems(playerId, itemKeys);

      for (VillagerType vt : types) {
         String itemKey = vt.id().getPath();
         boolean unlocked = !learning || tracker.isVillagerUnlocked(playerId, cultureKey, itemKey);
         TravelBookLine.TravelBookNavTarget target = new TravelBookLine.TravelBookNavTarget(
            TravelBookScreenState.VILLAGER_DETAIL, cultureKey, category, itemKey
         );
         if (unlocked) {
            lines.add(villagerLine(vt, player, target));
         } else {
            lines.add(TravelBookLine.clickable("§o" + t("millenaire.travel_book.unknown_villager"), target));
         }
      }

      if (types.isEmpty()) {
         lines.add(TravelBookLine.text(t("millenaire.travel_book.no_entries")));
      }

      return lines;
   }

   private static List<TravelBookLine> buildVillagerDetail(ServerPlayer player, String cultureKey, String itemKey) {
      List<TravelBookLine> lines = new ArrayList<>();
      ResourceLocation cultureId = cultureId(cultureKey);
      boolean learning = isLearningMode();
      DiscoveryTracker tracker = getTracker(player);
      UUID playerId = player.getUUID();
      boolean unlocked = !learning || tracker.isVillagerUnlocked(playerId, cultureKey, itemKey);
      ResourceLocation vtId = ResourceLocation.fromNamespaceAndPath("millenaire", itemKey);
      VillagerType vt = ModCultures.getVillagerType(vtId);
      if (vt == null) {
         lines.add(TravelBookLine.text("Unknown villager type: " + itemKey));
         return lines;
      }

      if (!unlocked) {
         lines.add(TravelBookLine.text("§4" + t("millenaire.travel_book.unknown_villager")));
         lines.add(TravelBookLine.text(vt.nativeName()));
         return lines;
      }

      lines.add(villagerLine(vt, player, null));
      lines.add(TravelBookLine.separator());
      lines.add(TravelBookLine.columns(t("millenaire.travel_book.health"), String.valueOf((int)vt.maxHealth())));
      float attackStrength = vt.maxHealth() > 20.0F ? 4.0F : 2.0F;
      lines.add(TravelBookLine.columns(t("millenaire.travel_book.attack"), String.valueOf((int)attackStrength)));
      lines.add(TravelBookLine.columns(t("millenaire.travel_book.gender"), t("millenaire.travel_book.gender." + vt.gender().name().toLowerCase())));
      if (!vt.tags().isEmpty()) {
         lines.add(TravelBookLine.separator());
         lines.add(TravelBookLine.text(t("millenaire.travel_book.tags")));

         for (String tag : vt.tags()) {
            lines.add(TravelBookLine.text("  - " + t("millenaire.travel_book.tag." + tag)));
         }
      }

      if (!vt.goals().isEmpty()) {
         lines.add(TravelBookLine.separator());
         lines.add(TravelBookLine.text(t("millenaire.travel_book.goals")));

         for (ResourceLocation goalId : vt.goals()) {
            String goalName = t("goal.millenaire." + goalId.getPath());
            lines.add(TravelBookLine.text("  - " + goalName));
         }
      }

      if (!vt.requiredGoods().isEmpty()) {
         lines.add(TravelBookLine.separator());
         lines.add(TravelBookLine.text(t("millenaire.travel_book.required_goods")));

         for (Entry<String, Integer> entry : vt.requiredGoods().entrySet()) {
            String itemName = formatItemName(ResourceLocation.parse(entry.getKey()));
            lines.add(TravelBookLine.columns("  " + itemName, "x" + entry.getValue()));
         }
      }

      if (!vt.toolNeededClasses().isEmpty()) {
         lines.add(TravelBookLine.separator());
         lines.add(TravelBookLine.text(t("millenaire.travel_book.tools_needed")));

         for (String toolCat : vt.toolNeededClasses()) {
            lines.add(TravelBookLine.text("  - " + t("millenaire.travel_book.tool_class." + toolCat)));
         }
      }

      List<BuildingPlanSet> residences = findResidences(cultureId, vt.id().getPath());
      if (!residences.isEmpty()) {
         lines.add(TravelBookLine.separator());
         lines.add(TravelBookLine.text(t("millenaire.travel_book.residences")));

         for (BuildingPlanSet bps : residences) {
            TravelBookLine.TravelBookNavTarget target = new TravelBookLine.TravelBookNavTarget(
               TravelBookScreenState.BUILDING_DETAIL, cultureKey, "", bps.id().getPath()
            );
            lines.add(buildingLine(bps, player, target));
         }
      }

      List<VillageType> villages = findVillagesWithVillager(cultureId, vt.id());
      if (!villages.isEmpty()) {
         lines.add(TravelBookLine.separator());
         lines.add(TravelBookLine.text(t("millenaire.travel_book.found_in_villages")));

         for (VillageType village : villages) {
            TravelBookLine.TravelBookNavTarget target = new TravelBookLine.TravelBookNavTarget(
               TravelBookScreenState.VILLAGE_DETAIL, cultureKey, "", village.id().getPath()
            );
            lines.add(TravelBookLine.clickable("  " + village.name(), target));
         }
      }

      return lines;
   }

   private static List<TravelBookLine> buildVillagesList(ServerPlayer player, String cultureKey) {
      List<TravelBookLine> lines = new ArrayList<>();
      ResourceLocation cultureId = cultureId(cultureKey);
      boolean learning = isLearningMode();
      DiscoveryTracker tracker = getTracker(player);
      UUID playerId = player.getUUID();
      List<VillageType> types = ModCultures.getAllVillageTypes()
         .values()
         .stream()
         .filter(vtx -> vtx.culture().equals(cultureId))
         .filter(VillageType::travelBookDisplay)
         .filter(vtx -> !vtx.loneBuilding() || vtx.keyLoneBuilding())
         .sorted(Comparator.comparing(VillageType::name))
         .toList();
      List<String> itemKeys = types.stream().map(vtx -> vtx.id().getPath()).toList();
      TravelBookNavigationState.setCurrentCategoryItems(playerId, itemKeys);

      for (VillageType vt : types) {
         String itemKey = vt.id().getPath();
         boolean unlocked = !learning || tracker.isVillageUnlocked(playerId, cultureKey, itemKey);
         String displayName = unlocked ? vt.name() : "§o" + t("millenaire.travel_book.unknown_village");
         TravelBookLine.TravelBookNavTarget target = new TravelBookLine.TravelBookNavTarget(TravelBookScreenState.VILLAGE_DETAIL, cultureKey, "", itemKey);
         if (vt.icon() != null && !vt.icon().isEmpty()) {
            lines.add(TravelBookLine.clickableWithIcon(displayName, "", vt.icon(), target));
         } else {
            lines.add(TravelBookLine.clickable(displayName, target));
         }
      }

      if (types.isEmpty()) {
         lines.add(TravelBookLine.text(t("millenaire.travel_book.no_entries")));
      }

      return lines;
   }

   private static List<TravelBookLine> buildVillageDetail(ServerPlayer player, String cultureKey, String itemKey) {
      List<TravelBookLine> lines = new ArrayList<>();
      ResourceLocation cultureId = cultureId(cultureKey);
      boolean learning = isLearningMode();
      DiscoveryTracker tracker = getTracker(player);
      UUID playerId = player.getUUID();
      boolean unlocked = !learning || tracker.isVillageUnlocked(playerId, cultureKey, itemKey);
      ResourceLocation vtId = ResourceLocation.fromNamespaceAndPath("millenaire", itemKey);
      VillageType vt = ModCultures.getVillageType(vtId);
      if (vt == null) {
         lines.add(TravelBookLine.text("Unknown village type: " + itemKey));
         return lines;
      }

      if (!unlocked) {
         lines.add(TravelBookLine.text("§4" + t("millenaire.travel_book.unknown_village")));
         lines.add(TravelBookLine.text(vt.name()));
         return lines;
      }

      lines.add(TravelBookLine.text(vt.name()));
      lines.add(TravelBookLine.separator());
      lines.add(TravelBookLine.columns(t("millenaire.travel_book.radius"), String.valueOf(vt.radius())));
      if (vt.weight() > 0) {
         lines.add(TravelBookLine.columns(t("millenaire.travel_book.weight"), String.valueOf(vt.weight())));
      }

      if (!vt.biomeTags().isEmpty()) {
         lines.add(TravelBookLine.separator());
         lines.add(TravelBookLine.text(t("millenaire.travel_book.biomes")));

         for (TagKey<Biome> biomeTag : vt.biomeTags()) {
            lines.add(TravelBookLine.text("  - " + biomeTag.location().getPath()));
         }
      }

      if (!vt.layout().isEmpty()) {
         lines.add(TravelBookLine.separator());
         lines.add(TravelBookLine.text(t("millenaire.travel_book.buildings_layout")));
         Map<String, List<VillageType.LayoutSlot>> byRole = new TreeMap<>();

         for (VillageType.LayoutSlot slot : vt.layout()) {
            byRole.computeIfAbsent(slot.role(), k -> new ArrayList<>()).add(slot);
         }

         for (Entry<String, List<VillageType.LayoutSlot>> roleEntry : byRole.entrySet()) {
            lines.add(TravelBookLine.text("  [" + roleEntry.getKey() + "]"));
            Set<ResourceLocation> seen = new HashSet<>();

            for (VillageType.LayoutSlot slot : roleEntry.getValue()) {
               if (seen.add(slot.plan())) {
                  TravelBookLine.TravelBookNavTarget target = new TravelBookLine.TravelBookNavTarget(
                     TravelBookScreenState.BUILDING_DETAIL, cultureKey, "", slot.plan().getPath()
                  );
                  BuildingPlanSet bps = ModCultures.getBuildingPlanSet(slot.plan());
                  if (bps != null) {
                     lines.add(buildingLine(bps, player, target));
                  } else {
                     lines.add(TravelBookLine.clickable(slot.plan().getPath(), target));
                  }
               }
            }
         }
      }

      return lines;
   }

   private static List<TravelBookLine> buildBuildingsList(ServerPlayer player, String cultureKey, String category) {
      List<TravelBookLine> lines = new ArrayList<>();
      ResourceLocation cultureId = cultureId(cultureKey);
      boolean learning = isLearningMode();
      DiscoveryTracker tracker = getTracker(player);
      UUID playerId = player.getUUID();
      List<BuildingPlanSet> sets = ModCultures.getAllBuildingPlanSets()
         .values()
         .stream()
         .filter(bpsx -> bpsx.culture().equals(cultureId))
         .filter(BuildingPlanSet::travelBookDisplay)
         .filter(bpsx -> matchesCategory(bpsx.travelBookCategory(), category))
         .sorted(Comparator.comparing(BuildingPlanSet::nativeName))
         .toList();
      List<String> itemKeys = sets.stream().map(bpsx -> bpsx.id().getPath()).toList();
      TravelBookNavigationState.setCurrentCategoryItems(playerId, itemKeys);

      for (BuildingPlanSet bps : sets) {
         String itemKey = bps.id().getPath();
         boolean unlocked = !learning || tracker.isBuildingUnlocked(playerId, cultureKey, itemKey);
         TravelBookLine.TravelBookNavTarget target = new TravelBookLine.TravelBookNavTarget(
            TravelBookScreenState.BUILDING_DETAIL, cultureKey, category, itemKey
         );
         if (unlocked) {
            lines.add(buildingLine(bps, player, target));
         } else {
            lines.add(TravelBookLine.clickable("§o" + t("millenaire.travel_book.unknown_building"), target));
         }
      }

      if (sets.isEmpty()) {
         lines.add(TravelBookLine.text(t("millenaire.travel_book.no_entries")));
      }

      return lines;
   }

   private static List<TravelBookLine> buildBuildingDetail(ServerPlayer player, String cultureKey, String itemKey) {
      List<TravelBookLine> lines = new ArrayList<>();
      ResourceLocation cultureId = cultureId(cultureKey);
      boolean learning = isLearningMode();
      DiscoveryTracker tracker = getTracker(player);
      UUID playerId = player.getUUID();
      boolean unlocked = !learning || tracker.isBuildingUnlocked(playerId, cultureKey, itemKey);
      ResourceLocation bpsId = ResourceLocation.fromNamespaceAndPath("millenaire", itemKey);
      BuildingPlanSet bps = ModCultures.getBuildingPlanSet(bpsId);
      if (bps == null) {
         lines.add(TravelBookLine.text("Unknown building: " + itemKey));
         return lines;
      }

      if (!unlocked) {
         lines.add(TravelBookLine.text("§4" + t("millenaire.travel_book.unknown_building")));
         lines.add(TravelBookLine.text(bps.nativeName()));
         return lines;
      }

      lines.add(buildingLine(bps, player, null));
      lines.add(TravelBookLine.separator());
      if (bps.price() > 0) {
         lines.add(TravelBookLine.columns(t("millenaire.travel_book.price"), MoneyHelper.formatPrice(bps.price())));
         if (bps.reputation() > 0) {
            lines.add(TravelBookLine.columns(t("millenaire.travel_book.reputation_required"), String.valueOf(bps.reputation())));
         }

         lines.add(TravelBookLine.separator());
      }

      for (Entry<String, List<BuildingPlanSet.LevelDef>> variantEntry : bps.variants().entrySet()) {
         String variant = variantEntry.getKey();
         if (bps.variants().size() > 1) {
            lines.add(TravelBookLine.text(t("millenaire.travel_book.variant") + " " + variant));
         }

         for (BuildingPlanSet.LevelDef level : variantEntry.getValue()) {
            String levelLabel = t("millenaire.travel_book.level") + " " + level.level();
            if (level.nativeName() != null) {
               levelLabel = levelLabel + " - " + level.nativeName();
            }

            lines.add(TravelBookLine.text("  " + levelLabel));
            lines.add(TravelBookLine.columns("    " + t("millenaire.travel_book.size"), level.width() + "x" + level.depth()));
            BuildingPlan plan = ModCultures.getBuildingPlan(level.planId());
            if (plan != null && plan.shopId() != null) {
               lines.add(TravelBookLine.columns("    " + t("millenaire.travel_book.shop"), plan.shopId()));
            }

            if (!level.requiredResources().isEmpty()) {
               lines.add(TravelBookLine.text("    " + t("millenaire.travel_book.construction_cost")));

               for (Entry<ResourceLocation, Integer> res : level.requiredResources().entrySet()) {
                  String itemName;
                  if (AnywoodHelper.isAnywood(res.getKey())) {
                     itemName = Component.translatable("item.millenaire.anywood_log").getString();
                  } else {
                     itemName = formatItemName(res.getKey());
                  }

                  lines.add(TravelBookLine.columns("      " + itemName, "x" + res.getValue()));
               }
            }

            if (!level.subBuildings().isEmpty()) {
               lines.add(TravelBookLine.text("    " + t("millenaire.travel_book.sub_buildings")));

               for (String sub : level.subBuildings()) {
                  ResourceLocation subId = ResourceLocation.fromNamespaceAndPath("millenaire", cultureKey + "/" + sub);
                  BuildingPlanSet subBps = ModCultures.getBuildingPlanSet(subId);
                  String subName = subBps != null ? subBps.nativeName() : sub;
                  TravelBookLine.TravelBookNavTarget target = new TravelBookLine.TravelBookNavTarget(
                     TravelBookScreenState.BUILDING_DETAIL, cultureKey, "", subId.getPath()
                  );
                  lines.add(TravelBookLine.clickable("      " + subName, target));
               }
            }
         }
      }

      if (!bps.maleResidents().isEmpty() || !bps.femaleResidents().isEmpty()) {
         lines.add(TravelBookLine.separator());
         lines.add(TravelBookLine.text(t("millenaire.travel_book.residents")));

         for (String resKey : bps.maleResidents()) {
            addResidentLink(lines, cultureKey, resKey);
         }

         for (String resKey : bps.femaleResidents()) {
            addResidentLink(lines, cultureKey, resKey);
         }
      }

      List<VillageType> villages = findVillagesWithBuilding(cultureId, bpsId);
      if (!villages.isEmpty()) {
         lines.add(TravelBookLine.separator());
         lines.add(TravelBookLine.text(t("millenaire.travel_book.found_in_villages")));

         for (VillageType village : villages) {
            TravelBookLine.TravelBookNavTarget target = new TravelBookLine.TravelBookNavTarget(
               TravelBookScreenState.VILLAGE_DETAIL, cultureKey, "", village.id().getPath()
            );
            lines.add(TravelBookLine.clickable("  " + village.name(), target));
         }
      }

      return lines;
   }

   private static List<TravelBookLine> buildTradeGoodsList(ServerPlayer player, String cultureKey, String category) {
      List<TravelBookLine> lines = new ArrayList<>();
      ResourceLocation cultureId = cultureId(cultureKey);
      boolean learning = isLearningMode();
      DiscoveryTracker tracker = getTracker(player);
      UUID playerId = player.getUUID();
      List<TradeGood> goods = TradeGoodsLoader.getGoods(cultureId)
         .stream()
         .filter(TradeGood::travelBookDisplay)
         .filter(g -> matchesCategory(g.category(), category))
         .sorted(Comparator.comparing(TradeGood::id))
         .toList();
      List<String> itemKeys = goods.stream().map(TradeGood::id).toList();
      TravelBookNavigationState.setCurrentCategoryItems(playerId, itemKeys);

      for (TradeGood good : goods) {
         boolean unlocked = !learning || tracker.isTradeGoodUnlocked(playerId, cultureKey, good.id());
         String displayName = unlocked ? good.id() : "§o" + t("millenaire.travel_book.unknown_trade_good");
         TravelBookLine.TravelBookNavTarget target = new TravelBookLine.TravelBookNavTarget(
            TravelBookScreenState.TRADE_GOOD_DETAIL, cultureKey, category, good.id()
         );
         lines.add(TravelBookLine.clickable(displayName, target));
      }

      if (goods.isEmpty()) {
         lines.add(TravelBookLine.text(t("millenaire.travel_book.no_entries")));
      }

      return lines;
   }

   private static List<TravelBookLine> buildTradeGoodDetail(ServerPlayer player, String cultureKey, String itemKey) {
      List<TravelBookLine> lines = new ArrayList<>();
      ResourceLocation cultureId = cultureId(cultureKey);
      boolean learning = isLearningMode();
      DiscoveryTracker tracker = getTracker(player);
      UUID playerId = player.getUUID();
      boolean unlocked = !learning || tracker.isTradeGoodUnlocked(playerId, cultureKey, itemKey);
      TradeGood good = TradeGoodsLoader.getGoodById(cultureId, itemKey);
      if (good == null) {
         lines.add(TravelBookLine.text("Unknown trade good: " + itemKey));
         return lines;
      }

      if (!unlocked) {
         lines.add(TravelBookLine.text("§4" + t("millenaire.travel_book.unknown_trade_good")));
         lines.add(TravelBookLine.text(good.id()));
         return lines;
      }

      lines.add(TravelBookLine.text(formatItemName(good.itemLocation())));
      lines.add(TravelBookLine.separator());
      String itemName = formatItemName(good.itemLocation());
      lines.add(TravelBookLine.columns(t("millenaire.travel_book.item"), itemName));
      if (good.canSell()) {
         lines.add(TravelBookLine.columns(t("millenaire.travel_book.sell_price"), MoneyHelper.formatPrice(good.sellingPrice())));
      }

      if (good.canBuy()) {
         lines.add(TravelBookLine.columns(t("millenaire.travel_book.buy_price"), MoneyHelper.formatPrice(good.buyingPrice())));
      }

      if (good.minReputation() != 0) {
         lines.add(TravelBookLine.columns(t("millenaire.travel_book.min_reputation"), String.valueOf(good.minReputation())));
      }

      if (good.foreignMerchantPrice() > 0) {
         lines.add(TravelBookLine.columns(t("millenaire.travel_book.market_price"), MoneyHelper.formatPrice(good.foreignMerchantPrice())));
      }

      if (good.autoGenerate()) {
         lines.add(TravelBookLine.text(t("millenaire.travel_book.auto_generated")));
      }

      List<String> sellingShops = new ArrayList<>();
      List<String> buyingShops = new ArrayList<>();
      Map<String, ShopProfile> profiles = ShopProfileLoader.getProfiles(cultureId);
      if (profiles != null) {
         for (Entry<String, ShopProfile> entry : profiles.entrySet()) {
            ShopProfile profile = entry.getValue();
            if (profile.sells().contains(itemKey)) {
               sellingShops.add(entry.getKey());
            }

            if (profile.buys().contains(itemKey) || profile.buysOptional().contains(itemKey)) {
               buyingShops.add(entry.getKey());
            }
         }
      }

      if (!sellingShops.isEmpty()) {
         lines.add(TravelBookLine.separator());
         lines.add(TravelBookLine.text(t("millenaire.travel_book.sold_by")));

         for (String shopId : sellingShops) {
            BuildingPlanSet bps = findBuildingWithShop(cultureId, shopId);
            if (bps != null) {
               TravelBookLine.TravelBookNavTarget target = new TravelBookLine.TravelBookNavTarget(
                  TravelBookScreenState.BUILDING_DETAIL, cultureKey, "", bps.id().getPath()
               );
               lines.add(buildingLine(bps, player, target));
            } else {
               lines.add(TravelBookLine.text("  " + shopId));
            }
         }
      }

      if (!buyingShops.isEmpty()) {
         lines.add(TravelBookLine.separator());
         lines.add(TravelBookLine.text(t("millenaire.travel_book.bought_by")));

         for (String shopId : buyingShops) {
            BuildingPlanSet bps = findBuildingWithShop(cultureId, shopId);
            if (bps != null) {
               TravelBookLine.TravelBookNavTarget target = new TravelBookLine.TravelBookNavTarget(
                  TravelBookScreenState.BUILDING_DETAIL, cultureKey, "", bps.id().getPath()
               );
               lines.add(buildingLine(bps, player, target));
            } else {
               lines.add(TravelBookLine.text("  " + shopId));
            }
         }
      }

      if (good.foreignMerchantPrice() > 0) {
         List<String> merchantNames = new ArrayList<>();

         for (Entry<ResourceLocation, VillagerType> vtEntry : ModCultures.getAllVillagerTypes().entrySet()) {
            VillagerType vt = vtEntry.getValue();
            if (vt.culture().equals(cultureId) && vt.hasTag("foreignmerchant") && vt.foreignMerchantStock().containsKey(good.itemLocation())) {
               merchantNames.add(resolveRoleName(vtEntry.getKey()));
            }
         }

         if (!merchantNames.isEmpty()) {
            lines.add(TravelBookLine.separator());
            lines.add(TravelBookLine.text(t("millenaire.travel_book.sold_by_merchants")));

            for (String name : merchantNames) {
               lines.add(TravelBookLine.text("  " + name));
            }
         }
      }

      return lines;
   }

   private static String t(String key) {
      return DisplayUtils.t(key);
   }

   private static boolean isLearningMode() {
      return (Boolean)MillenaireServerConfig.SERVER.travelBookLearning.get();
   }

   private static DiscoveryTracker getTracker(ServerPlayer player) {
      ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
      return DiscoveryTracker.get(overworld);
   }

   private static ResourceLocation cultureId(String cultureKey) {
      return ResourceLocation.fromNamespaceAndPath("millenaire", cultureKey);
   }

   private static boolean matchesCategory(@Nullable String typeCategory, String requestedCategory) {
      if (requestedCategory.isEmpty()) {
         return true;
      }

      String effective = typeCategory != null ? typeCategory : "misc";
      return effective.equals(requestedCategory);
   }

   private static String extractSimpleName(ResourceLocation id) {
      String path = id.getPath();
      int slash = path.lastIndexOf(47);
      return slash >= 0 ? path.substring(slash + 1) : path;
   }

   private static String formatCategoryName(String category) {
      if (category.isEmpty()) {
         return "";
      }

      String formatted = category.replace('_', ' ');
      return Character.toUpperCase(formatted.charAt(0)) + formatted.substring(1);
   }

   private static TravelBookLine buildingLine(BuildingPlanSet bps, ServerPlayer player, @Nullable TravelBookLine.TravelBookNavTarget target) {
      if (LanguageHelper.canReadBuildingNames(player, bps.culture())) {
         String key = BuildingNameHelper.getTranslationKey(bps);
         return target != null ? TravelBookLine.clickableWithTranslation(bps.nativeName(), key, target) : TravelBookLine.withTranslation(bps.nativeName(), key);
      } else {
         return target != null ? TravelBookLine.clickable(bps.nativeName(), target) : TravelBookLine.text(bps.nativeName());
      }
   }

   private static TravelBookLine villagerLine(VillagerType vt, ServerPlayer player, @Nullable TravelBookLine.TravelBookNavTarget target) {
      if (LanguageHelper.canReadVillagerNames(player, vt.culture())) {
         String key = BuildingNameHelper.getVillagerRoleKey(vt);
         return target != null ? TravelBookLine.clickableWithTranslation(vt.nativeName(), key, target) : TravelBookLine.withTranslation(vt.nativeName(), key);
      } else {
         return target != null ? TravelBookLine.clickable(vt.nativeName(), target) : TravelBookLine.text(vt.nativeName());
      }
   }

   private static String resolveRoleName(ResourceLocation typeId) {
      return DisplayUtils.resolveRoleName(typeId);
   }

   private static String formatItemName(ResourceLocation itemId) {
      Item item = ItemHelper.resolve(itemId);
      return item != null ? Component.translatable(item.getDescriptionId()).getString() : extractSimpleName(itemId);
   }

   private static String getCultureDisplayName(String cultureKey) {
      String i18nKey = "culture.millenaire." + cultureKey;
      String resolved = t(i18nKey);
      if (!resolved.equals(i18nKey)) {
         return resolved;
      }

      Culture culture = ModCultures.getCulture(cultureId(cultureKey));
      return culture != null ? culture.displayName() : cultureKey;
   }

   private static String getVillagerDisplayName(String cultureKey, String itemKey) {
      ResourceLocation vtId = ResourceLocation.fromNamespaceAndPath("millenaire", itemKey);
      VillagerType vt = ModCultures.getVillagerType(vtId);
      return vt != null ? vt.nativeName() : itemKey;
   }

   private static String getVillageDisplayName(String cultureKey, String itemKey) {
      ResourceLocation vtId = ResourceLocation.fromNamespaceAndPath("millenaire", itemKey);
      VillageType vt = ModCultures.getVillageType(vtId);
      return vt != null ? vt.name() : itemKey;
   }

   private static String getBuildingTranslationKey(String cultureKey, String itemKey) {
      ResourceLocation bpsId = ResourceLocation.fromNamespaceAndPath("millenaire", itemKey);
      String key = BuildingNameHelper.getTranslationKey(bpsId);
      return key != null ? key : itemKey;
   }

   private static String getTradeGoodDisplayName(String cultureKey, String itemKey) {
      ResourceLocation cultureId = cultureId(cultureKey);
      TradeGood good = TradeGoodsLoader.getGoodById(cultureId, itemKey);
      return good != null ? formatItemName(good.itemLocation()) : itemKey;
   }

   private static void addResidentLink(List<TravelBookLine> lines, String cultureKey, String resKey) {
      String fullPath = cultureKey + "_" + resKey;
      ResourceLocation vtId = ResourceLocation.fromNamespaceAndPath("millenaire", fullPath);
      VillagerType vt = ModCultures.getVillagerType(vtId);
      String name = vt != null ? vt.nativeName() : resKey;
      TravelBookLine.TravelBookNavTarget target = new TravelBookLine.TravelBookNavTarget(TravelBookScreenState.VILLAGER_DETAIL, cultureKey, "", vtId.getPath());
      lines.add(TravelBookLine.clickable("  " + name, target));
   }

   private static String toResidentKey(ResourceLocation cultureId, String villagerPath) {
      String prefix = cultureId.getPath() + "_";
      return villagerPath.startsWith(prefix) ? villagerPath.substring(prefix.length()) : villagerPath;
   }

   private static List<BuildingPlanSet> findResidences(ResourceLocation cultureId, String villagerIdPath) {
      String residentKey = toResidentKey(cultureId, villagerIdPath);
      return ModCultures.getAllBuildingPlanSets()
         .values()
         .stream()
         .filter(bps -> bps.culture().equals(cultureId))
         .filter(bps -> bps.maleResidents().contains(residentKey) || bps.femaleResidents().contains(residentKey))
         .sorted(Comparator.comparing(BuildingPlanSet::nativeName))
         .toList();
   }

   private static List<VillageType> findVillagesWithVillager(ResourceLocation cultureId, ResourceLocation villagerId) {
      String residentKey = toResidentKey(cultureId, villagerId.getPath());
      Set<ResourceLocation> buildingIds = ModCultures.getAllBuildingPlanSets()
         .values()
         .stream()
         .filter(bps -> bps.culture().equals(cultureId))
         .filter(bps -> bps.maleResidents().contains(residentKey) || bps.femaleResidents().contains(residentKey))
         .map(BuildingPlanSet::id)
         .collect(Collectors.toSet());
      return ModCultures.getAllVillageTypes()
         .values()
         .stream()
         .filter(vt -> vt.culture().equals(cultureId))
         .filter(vt -> !vt.loneBuilding())
         .filter(vt -> vt.layout().stream().anyMatch(slot -> buildingIds.contains(slot.plan())))
         .sorted(Comparator.comparing(VillageType::name))
         .toList();
   }

   private static List<VillageType> findVillagesWithBuilding(ResourceLocation cultureId, ResourceLocation buildingId) {
      return ModCultures.getAllVillageTypes()
         .values()
         .stream()
         .filter(vt -> vt.culture().equals(cultureId))
         .filter(vt -> !vt.loneBuilding())
         .filter(vt -> vt.layout().stream().anyMatch(slot -> slot.plan().equals(buildingId)))
         .sorted(Comparator.comparing(VillageType::name))
         .toList();
   }

   @Nullable
   private static BuildingPlanSet findBuildingWithShop(ResourceLocation cultureId, String shopId) {
      for (BuildingPlanSet bps : ModCultures.getAllBuildingPlanSets().values()) {
         if (bps.culture().equals(cultureId)) {
            for (List<BuildingPlanSet.LevelDef> levels : bps.variants().values()) {
               for (BuildingPlanSet.LevelDef level : levels) {
                  BuildingPlan plan = ModCultures.getBuildingPlan(level.planId());
                  if (plan != null && shopId.equals(plan.shopId())) {
                     return bps;
                  }
               }
            }
         }
      }

      return null;
   }

   private static TravelBookContentBuilder.MockAppearance buildMockAppearance(String cultureKey, String itemKey) {
      ResourceLocation vtId = ResourceLocation.fromNamespaceAndPath("millenaire", itemKey);
      VillagerType vt = ModCultures.getVillagerType(vtId);
      if (vt == null) {
         return TravelBookContentBuilder.MockAppearance.EMPTY;
      }

      Random random = new Random(vtId.hashCode());
      List<ResourceLocation> textures = vt.textures();
      String texture = textures.isEmpty() ? "" : textures.get(random.nextInt(textures.size())).toString();
      float scale = vt.isChild() ? 0.5F : vt.baseScale() * (0.8F + random.nextFloat() * 0.09F);
      String cloth0 = "";
      String cloth1 = "";
      VillagerType.ClothSet freeSet = vt.clothes().get("free");
      VillagerType.ClothSet naturalSet = vt.clothes().get("natural");
      if (naturalSet != null && naturalSet.layer0() != null && !naturalSet.layer0().isEmpty()) {
         cloth0 = naturalSet.layer0().get(random.nextInt(naturalSet.layer0().size())).toString();
      } else if (freeSet != null && freeSet.layer0() != null && !freeSet.layer0().isEmpty()) {
         cloth0 = freeSet.layer0().get(random.nextInt(freeSet.layer0().size())).toString();
      }

      if (naturalSet != null && naturalSet.layer1() != null && !naturalSet.layer1().isEmpty()) {
         cloth1 = naturalSet.layer1().get(random.nextInt(naturalSet.layer1().size())).toString();
      } else if (freeSet != null && freeSet.layer1() != null && !freeSet.layer1().isEmpty()) {
         cloth1 = freeSet.layer1().get(random.nextInt(freeSet.layer1().size())).toString();
      }

      String heldItem = vt.travelBookHeldItem() != null ? vt.travelBookHeldItem() : "";
      String heldItemOffHand = vt.travelBookHeldItemOffHand() != null ? vt.travelBookHeldItemOffHand() : "";
      return new TravelBookContentBuilder.MockAppearance(vt.modelType().toByte(), texture, cloth0, cloth1, scale, heldItem, heldItemOffHand);
   }

   private record MockAppearance(byte modelType, String texture, String cloth0, String cloth1, float scale, String heldItem, String heldItemOffHand) {
      static final TravelBookContentBuilder.MockAppearance EMPTY = new TravelBookContentBuilder.MockAppearance((byte)0, "", "", "", 0.0F, "", "");
   }

   private record TitleResult(String text, boolean translatable) {
      static TravelBookContentBuilder.TitleResult literal(String text) {
         return new TravelBookContentBuilder.TitleResult(text, false);
      }

      static TravelBookContentBuilder.TitleResult translatable(String key) {
         return new TravelBookContentBuilder.TitleResult(key, true);
      }
   }
}
