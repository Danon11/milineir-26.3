package org.millenaire.village.panel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.commerce.ShopProfile;
import org.millenaire.commerce.ShopProfileLoader;
import org.millenaire.commerce.TradeGood;
import org.millenaire.commerce.TradeGoodsLoader;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.language.BuildingNameHelper;
import org.millenaire.village.Village;

public final class CommercePanelGenerator {
   private CommercePanelGenerator() {
   }

   public static PanelContent generateInnVisitors(Village village, @Nullable BuildingInstance building) {
      List<PanelLine> lines = new ArrayList<>();
      String buildingTitle = building != null ? BuildingNameHelper.getServerFallbackName(building) : village.getVillageName();
      lines.add(PanelLine.translatableWithArgsColored("panel.millenaire.inn_visitors_title", 170, buildingTitle));
      lines.add(PanelLine.text(""));
      if (building != null && !building.getVisitorLog().isEmpty()) {
         List<String> log = building.getVisitorLog();

         for (int i = log.size() - 1; i >= 0; i--) {
            String entry = log.get(i);
            String[] parts = entry.split(";");
            if (parts.length <= 1) {
               lines.add(PanelLine.text("  " + entry));
            } else {
               String[] args = new String[parts.length - 1];
               int argMask = 0;

               for (int j = 0; j < args.length; j++) {
                  args[j] = parts[j + 1];
                  if (args[j].startsWith("role.millenaire.")) {
                     argMask |= 1 << j;
                  }
               }

               if (argMask != 0) {
                  lines.add(PanelLine.translatableWithMixedArgs(parts[0], argMask, args));
               } else {
                  lines.add(PanelLine.translatableWithArgs(parts[0], args));
               }
            }

            lines.add(PanelLine.text(""));
         }
      } else {
         lines.add(PanelLine.translatable("panel.millenaire.no_visitors_currently"));
      }

      return new PanelContent(PanelType.INN_VISITORS, buildingTitle, lines, false, null);
   }

   public static PanelContent generateInnGoods(Village village, @Nullable BuildingInstance building) {
      List<PanelLine> lines = new ArrayList<>();
      String buildingTitle = building != null ? BuildingNameHelper.getServerFallbackName(building) : village.getVillageName();
      lines.add(PanelLine.colored(buildingTitle, 170));
      lines.add(PanelLine.text(""));
      lines.add(PanelLine.translatable("panel.millenaire.goods_traded"));
      lines.add(PanelLine.text(""));
      ResourceLocation cultureId = village.getCultureId();
      ShopProfile shopProfile = null;
      if (building != null) {
         BuildingPlan plan = ModCultures.getBuildingPlan(building.getPlanId());
         if (plan != null && plan.shopId() != null) {
            shopProfile = ShopProfileLoader.getProfile(cultureId, plan.shopId());
         }
      }

      if (shopProfile == null) {
         lines.add(PanelLine.translatable("panel.millenaire.no_shops"));
         return new PanelContent(PanelType.INN_TRADE_GOODS, buildingTitle, lines, false, null);
      }

      lines.add(PanelLine.translatable("panel.millenaire.goods_imported"));
      lines.add(PanelLine.text(""));
      List<String> importGoods = new ArrayList<>(shopProfile.buys());
      if (importGoods.isEmpty()) {
         lines.add(PanelLine.text("  §8-"));
      } else {
         addGoodLines(lines, importGoods, cultureId);
      }

      lines.add(PanelLine.text(""));
      lines.add(PanelLine.translatable("panel.millenaire.goods_exported"));
      lines.add(PanelLine.text(""));
      if (shopProfile.sells().isEmpty()) {
         lines.add(PanelLine.text("  §8-"));
      } else {
         addGoodLines(lines, shopProfile.sells(), cultureId);
      }

      return new PanelContent(PanelType.INN_TRADE_GOODS, buildingTitle, lines, false, null);
   }

   public static PanelContent generateVisitors(Village village, @Nullable BuildingInstance building, boolean isMarket) {
      List<PanelLine> lines = new ArrayList<>();
      String buildingTitle = building != null ? BuildingNameHelper.getServerFallbackName(building) : village.getVillageName();
      lines.add(PanelLine.colored(buildingTitle, 170));
      lines.add(PanelLine.text(""));
      if (isMarket) {
         int stallCount = 0;
         if (building != null) {
            stallCount = (int)building.getResolvedPoints().stream().filter(p -> p.isType("stall")).count();
            if (stallCount == 0) {
               stallCount = (int)building.getResolvedPoints().stream().filter(p -> p.isType("sellingPos")).count();
            }
         }

         int merchantCount = 0;
         if (building != null) {
            for (Entry<UUID, ResourceLocation> entry : village.getVillagerTypes().entrySet()) {
               UUID uuid = entry.getKey();
               if (building.getId().equals(village.getVillagerHome(uuid))) {
                  VillagerType vType = ModCultures.getVillagerType(entry.getValue());
                  if (vType != null && vType.hasTag("foreignmerchant")) {
                     merchantCount++;
                  }
               }
            }
         }

         lines.add(PanelLine.translatableWithArgs("panel.millenaire.merchant_count", String.valueOf(merchantCount), String.valueOf(stallCount)));
         lines.add(PanelLine.text(""));
         if (building != null && merchantCount > 0) {
            for (Entry<UUID, ResourceLocation> entry : village.getVillagerTypes().entrySet()) {
               UUID uuid = entry.getKey();
               if (building.getId().equals(village.getVillagerHome(uuid))) {
                  VillagerType vType = ModCultures.getVillagerType(entry.getValue());
                  if (vType != null && vType.hasTag("foreignmerchant")) {
                     String roleKey = PanelHelper.resolveRoleKey(entry.getValue());
                     Culture merchantCulture = ModCultures.getCulture(vType.culture());
                     String cultureStr = merchantCulture != null ? merchantCulture.displayName() : vType.culture().getPath();
                     lines.add(PanelLine.translatableWithMixedArgs("panel.millenaire.merchant_line", 1, roleKey, cultureStr));
                  }
               }
            }
         } else {
            lines.add(PanelLine.translatable("panel.millenaire.no_merchants_currently"));
         }
      } else {
         int visitorCount = 0;
         if (building != null) {
            for (Entry<UUID, ResourceLocation> entry : village.getVillagerTypes().entrySet()) {
               UUID uuid = entry.getKey();
               if (building.getId().equals(village.getVillagerHome(uuid))) {
                  VillagerType vType = ModCultures.getVillagerType(entry.getValue());
                  if (vType != null && vType.hasTag("visitor")) {
                     visitorCount++;
                  }
               }
            }
         }

         lines.add(PanelLine.translatableWithArgs("panel.millenaire.visitor_count", String.valueOf(visitorCount)));
         lines.add(PanelLine.text(""));
         if (visitorCount == 0) {
            lines.add(PanelLine.translatable("panel.millenaire.no_visitors_currently"));
         }
      }

      PanelType resultType = isMarket ? PanelType.MARKET_MERCHANTS : PanelType.VISITORS;
      return new PanelContent(resultType, buildingTitle, lines, false, null);
   }

   static void addGoodLines(List<PanelLine> lines, List<String> goodIds, ResourceLocation cultureId) {
      for (String goodId : goodIds) {
         TradeGood good = TradeGoodsLoader.getGoodById(cultureId, goodId);
         if (good != null) {
            Item item = good.resolveItem();
            String itemId = good.itemLocation().toString();
            if (item != null) {
               String descId = item.getDescriptionId();
               lines.add(PanelLine.withIconTranslatable(descId, "§8" + good.targetQuantity(), itemId, 0));
            } else {
               String fallbackName = good.item().replace('_', ' ');
               lines.add(PanelLine.withIcon("  §0" + fallbackName, "§8" + good.targetQuantity(), itemId));
            }
         }
      }
   }

   static void addCommerce3D(PanelContentGenerator.DisplayData data, PanelType type, Village village, @Nullable BuildingInstance building) {
      if (building != null) {
         String bldIcon = PanelHelper.resolveBuildingIcon(building);
         data.addCenteredBuildingNameWithIcons(
            PanelHelper.getBuildingTranslationKey(building), bldIcon, bldIcon, BuildingNameHelper.getServerFallbackName(building)
         );
         data.addCentered("");
         String tag = type == PanelType.MARKET_MERCHANTS ? "foreignmerchant" : "visitor";
         int count = 0;

         for (Entry<UUID, ResourceLocation> entry : village.getVillagerTypes().entrySet()) {
            UUID uuid = entry.getKey();
            if (building.getId().equals(village.getVillagerHome(uuid))) {
               VillagerType vType = ModCultures.getVillagerType(entry.getValue());
               if (vType != null && vType.hasTag(tag)) {
                  count++;
               }
            }
         }

         if (count > 0) {
            String label = type == PanelType.MARKET_MERCHANTS
               ? PanelHelper.t("panel.millenaire.merchant_list")
               : PanelHelper.t("panel.millenaire.visitor_list");
            data.addCentered(label + ": " + count);
         } else {
            data.addCentered(PanelHelper.t("panel.millenaire.no_visitors_currently"));
         }
      } else {
         data.addCentered(PanelHelper.t("panel.millenaire.panel_type." + type.name().toLowerCase(Locale.ROOT)));
      }
   }
}
