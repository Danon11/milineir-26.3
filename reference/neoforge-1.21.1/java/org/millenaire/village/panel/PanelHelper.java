package org.millenaire.village.panel;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.millenaire.DisplayUtils;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.commerce.TradeGood;
import org.millenaire.commerce.TradeGoodsLoader;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.language.BuildingNameHelper;
import org.millenaire.village.Village;

public final class PanelHelper {
   private PanelHelper() {
   }

   static String t(String key) {
      return DisplayUtils.t(key);
   }

   static String t(String key, Object... args) {
      return DisplayUtils.t(key, args);
   }

   static String resolveRoleName(ResourceLocation typeId) {
      return DisplayUtils.resolveRoleName(typeId);
   }

   static String resolveRoleKey(ResourceLocation typeId) {
      return DisplayUtils.resolveRoleKey(typeId);
   }

   static String resolveTradeGoodName(ResourceLocation cultureId, String tradeGoodKey) {
      TradeGood good = TradeGoodsLoader.getGoodById(cultureId, tradeGoodKey);
      if (good != null) {
         Item item = good.resolveItem();
         if (item != null) {
            return item.getDescription().getString();
         }
      }

      return tradeGoodKey.replace('_', ' ');
   }

   static PanelHelper.DirectionInfo computeDirectionInfo(BlockPos from, BlockPos to) {
      int dx = to.getX() - from.getX();
      int dz = to.getZ() - from.getZ();
      double dist = Math.sqrt(dx * dx + dz * dz);
      if (dist < 5.0) {
         return new PanelHelper.DirectionInfo(true, (int)dist, null);
      }

      double angle = Math.toDegrees(Math.atan2(-dx, dz));
      if (angle < 0.0) {
         angle += 360.0;
      }

      String dirKey;
      if (angle < 22.5 || angle >= 337.5) {
         dirKey = "panel.millenaire.dir.south";
      } else if (angle < 67.5) {
         dirKey = "panel.millenaire.dir.southwest";
      } else if (angle < 112.5) {
         dirKey = "panel.millenaire.dir.west";
      } else if (angle < 157.5) {
         dirKey = "panel.millenaire.dir.northwest";
      } else if (angle < 202.5) {
         dirKey = "panel.millenaire.dir.north";
      } else if (angle < 247.5) {
         dirKey = "panel.millenaire.dir.northeast";
      } else if (angle < 292.5) {
         dirKey = "panel.millenaire.dir.east";
      } else {
         dirKey = "panel.millenaire.dir.southeast";
      }

      return new PanelHelper.DirectionInfo(false, (int)dist, dirKey);
   }

   static String computeDirection(BlockPos from, BlockPos to) {
      int dx = to.getX() - from.getX();
      int dz = to.getZ() - from.getZ();
      double dist = Math.sqrt(dx * dx + dz * dz);
      if (dist < 5.0) {
         return t("panel.millenaire.at_center");
      }

      double angle = Math.toDegrees(Math.atan2(-dx, dz));
      if (angle < 0.0) {
         angle += 360.0;
      }

      String dirKey;
      if (angle < 22.5 || angle >= 337.5) {
         dirKey = "panel.millenaire.dir.south";
      } else if (angle < 67.5) {
         dirKey = "panel.millenaire.dir.southwest";
      } else if (angle < 112.5) {
         dirKey = "panel.millenaire.dir.west";
      } else if (angle < 157.5) {
         dirKey = "panel.millenaire.dir.northwest";
      } else if (angle < 202.5) {
         dirKey = "panel.millenaire.dir.north";
      } else if (angle < 247.5) {
         dirKey = "panel.millenaire.dir.northeast";
      } else if (angle < 292.5) {
         dirKey = "panel.millenaire.dir.east";
      } else {
         dirKey = "panel.millenaire.dir.southeast";
      }

      return (int)dist + " " + t("panel.millenaire.blocks") + " " + t("panel.millenaire.to_the") + " " + t(dirKey);
   }

   static String getBuildingDisplayName(BuildingInstance building) {
      if (building.getPlanSetId() != null) {
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(building.getPlanSetId());
         if (planSet != null) {
            BuildingPlanSet.LevelDef levelDef = planSet.getLevel(building.getVariant(), building.getLevel());
            if (levelDef != null && levelDef.nativeName() != null) {
               return levelDef.nativeName();
            }

            return planSet.nativeName();
         }
      }

      return building.getPlanId().getPath();
   }

   static String getPendingProjectName(Village.PendingProject pending) {
      BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(pending.planSetId());
      if (planSet != null) {
         BuildingPlanSet.LevelDef levelDef = planSet.getLevel(pending.variant(), pending.level());
         return levelDef != null && levelDef.nativeName() != null ? levelDef.nativeName() : planSet.nativeName();
      } else {
         return pending.planSetId().getPath();
      }
   }

   static String getBuildingTranslationKey(BuildingInstance building) {
      return BuildingNameHelper.getTranslationKey(building);
   }

   static String getPendingProjectKey(Village.PendingProject pending) {
      String key = BuildingNameHelper.getPendingProjectTranslationKey(pending.planSetId());
      return key != null ? key : pending.planSetId().getPath();
   }

   static String resolveBuildingIcon(@Nullable BuildingInstance building) {
      if (building != null && building.getPlanSetId() != null) {
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(building.getPlanSetId());
         return planSet != null && planSet.icon() != null ? planSet.icon() : "";
      } else {
         return "";
      }
   }

   @Nullable
   static MillVillager findVillager(ServerLevel level, UUID uuid) {
      return level.getEntity(uuid) instanceof MillVillager mv ? mv : null;
   }

   static ResourceLocation findTypeId(Village village, MillVillager mv) {
      ResourceLocation typeId = village.getVillagerTypes().get(mv.getUUID());
      return typeId != null ? typeId : ResourceLocation.withDefaultNamespace("unknown");
   }

   @Nullable
   static PanelLine.PanelNavTarget buildingNavTarget(BuildingInstance b) {
      ResourceLocation planSetId = b.getPlanSetId();
      if (planSetId == null) {
         return null;
      }

      String cultureKey = ModCultures.extractCultureId(planSetId).getPath();
      return new PanelLine.PanelNavTarget("BUILDING_DETAIL", cultureKey, "", planSetId.getPath());
   }

   @Nullable
   static PanelLine.PanelNavTarget planSetNavTarget(ResourceLocation planSetId) {
      if (planSetId == null) {
         return null;
      }

      String cultureKey = ModCultures.extractCultureId(planSetId).getPath();
      return new PanelLine.PanelNavTarget("BUILDING_DETAIL", cultureKey, "", planSetId.getPath());
   }

   @Nullable
   static PanelLine.PanelNavTarget villagerNavTarget(ResourceLocation typeId) {
      VillagerType vtype = ModCultures.getVillagerType(typeId);
      if (vtype != null && vtype.travelBookDisplay()) {
         String cultureKey = ModCultures.extractCultureId(typeId).getPath();
         return new PanelLine.PanelNavTarget("VILLAGER_DETAIL", cultureKey, "villagers", typeId.getPath());
      } else {
         return null;
      }
   }

   @Nullable
   static PanelLine.PanelNavTarget tradeGoodNavTarget(TradeGood good, String cultureKey) {
      return !good.travelBookDisplay() ? null : new PanelLine.PanelNavTarget("TRADE_GOOD_DETAIL", cultureKey, good.category(), good.id());
   }

   record DirectionInfo(boolean atCenter, int distance, @Nullable String cardinalKey) {
   }
}
