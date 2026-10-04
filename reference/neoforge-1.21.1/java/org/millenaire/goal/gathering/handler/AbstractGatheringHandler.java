package org.millenaire.goal.gathering.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPlan;
import org.millenaire.culture.ModCultures;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.gathering.GatheringHandler;
import org.millenaire.goal.gathering.GatheringTarget;
import org.millenaire.goal.gathering.GatheringType;
import org.millenaire.item.BlockHelper;
import org.millenaire.item.ItemHelper;
import org.millenaire.village.Village;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractGatheringHandler implements GatheringHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(AbstractGatheringHandler.class);

   @Nullable
   protected String getBuildingTag(GatheringType type) {
      return GsonHelper.getAsString(type.handlerParams(), "buildingTag", null);
   }

   @Nullable
   protected String getRequiredTag(GatheringType type) {
      return GsonHelper.getAsString(type.handlerParams(), "requiredTag", null);
   }

   protected List<BuildingInstance> findBuildingsWithTag(Village village, String tag) {
      return village.getOperationalBuildingsWithTag(tag);
   }

   protected List<BlockPos> collectSoilPositions(Village village) {
      List<BlockPos> positions = new ArrayList<>();

      for (BuildingInstance building : village.getBuildings()) {
         if (building.isOperational()) {
            positions.addAll(building.getSoilPositions());
         }
      }

      return positions;
   }

   protected List<BlockPos> collectSoilPositions(GoalContext ctx, GatheringType type) {
      String soilSubtype = GsonHelper.getAsString(type.handlerParams(), "soilSubtype", null);
      List<BuildingInstance> candidates = this.resolveTargetBuildings(ctx, type);
      List<BlockPos> positions = new ArrayList<>();

      for (BuildingInstance building : candidates) {
         if (building.isOperational()) {
            positions.addAll(building.getSoilPositions(soilSubtype));
         }
      }

      return positions;
   }

   protected Map<Item, Integer> parseItemList(JsonObject params, String key) {
      Map<Item, Integer> items = new HashMap<>();
      if (!params.has(key)) {
         return items;
      }

      for (JsonElement elem : params.getAsJsonArray(key)) {
         JsonObject obj = elem.getAsJsonObject();
         String itemId = GsonHelper.getAsString(obj, "item");
         int count = GsonHelper.getAsInt(obj, "count", 1);
         Item item = ItemHelper.resolve(itemId);
         if (item != null && item != Items.AIR) {
            items.merge(item, count, Integer::sum);
         }
      }

      return items;
   }

   @Nullable
   protected Item resolveSeedItem(GatheringType type) {
      JsonObject params = type.handlerParams();
      String seedItemId = GsonHelper.getAsString(params, "seedItem", null);
      return seedItemId == null ? null : ItemHelper.resolve(seedItemId);
   }

   protected boolean homeBuildingHasItem(GoalContext ctx, Item item) {
      return ctx.resolveHomeInventory().map(inv -> inv.getCount(ctx.level(), item) > 0).orElse(false);
   }

   protected boolean takeFromHomeBuilding(GoalContext ctx, Item item, int count) {
      BuildingInventory inv = ctx.resolveHomeInventory().orElse(null);
      if (inv == null) {
         return false;
      } else {
         int removed = inv.remove(ctx.level(), item, count);
         if (removed > 0) {
            ctx.villager().getInventory().add(item, removed);
            return true;
         } else {
            return false;
         }
      }
   }

   protected List<BuildingInstance> resolveTargetBuildings(GoalContext ctx, GatheringType type) {
      String buildingTag = this.getBuildingTag(type);
      List<BuildingInstance> candidates;
      if (buildingTag != null) {
         candidates = this.findBuildingsWithTag(ctx.village(), buildingTag);
      } else {
         BuildingId homeId = ctx.villager().getHomeBuilding();
         if (homeId == null) {
            return List.of();
         }

         BuildingInstance home = ctx.village().getBuilding(homeId);
         if (home == null || !home.isOperational()) {
            return List.of();
         }

         candidates = List.of(home);
      }

      String requiredTag = this.getRequiredTag(type);
      if (requiredTag != null) {
         List<BuildingInstance> filtered = new ArrayList<>();

         for (BuildingInstance b : candidates) {
            BuildingPlan plan = ModCultures.getBuildingPlan(b.getPlanId());
            if (plan != null && plan.hasTag(requiredTag)) {
               filtered.add(b);
            }
         }

         return filtered;
      } else {
         return candidates;
      }
   }

   protected List<String> validateItemList(GatheringType type, String paramName, boolean required) {
      List<String> errors = new ArrayList<>();
      JsonObject params = type.handlerParams();
      if (!params.has(paramName)) {
         if (required) {
            errors.add("missing required handlerParam '" + paramName + "'");
         }

         return errors;
      } else {
         for (JsonElement elem : params.getAsJsonArray(paramName)) {
            JsonObject obj = elem.getAsJsonObject();
            String itemId = GsonHelper.getAsString(obj, "item");
            Item item = ItemHelper.resolve(itemId);
            if (item == null || item == Items.AIR) {
               errors.add("unresolvable item '" + itemId + "' in '" + paramName + "'");
            }
         }

         return errors;
      }
   }

   protected static List<String> validateItemParam(GatheringType type, String paramName, boolean required) {
      List<String> errors = new ArrayList<>();
      String itemId = GsonHelper.getAsString(type.handlerParams(), paramName, null);
      if (itemId == null) {
         if (required) {
            errors.add("missing required handlerParam '" + paramName + "'");
         }

         return errors;
      } else {
         Item item = ItemHelper.resolve(itemId);
         if (item == null || item == Items.AIR) {
            errors.add("unresolvable item '" + itemId + "' in '" + paramName + "'");
         }

         return errors;
      }
   }

   protected static List<String> validateBlockParam(GatheringType type, String paramName, boolean required) {
      List<String> errors = new ArrayList<>();
      String blockId = GsonHelper.getAsString(type.handlerParams(), paramName, null);
      if (blockId != null && !blockId.isEmpty()) {
         Block block = BlockHelper.resolve(blockId);
         if (block == null) {
            errors.add("unresolvable block '" + blockId + "' in '" + paramName + "'");
         }

         return errors;
      } else {
         if (required) {
            errors.add("missing required handlerParam '" + paramName + "'");
         }

         return errors;
      }
   }

   protected static List<String> validateBlockList(GatheringType type, String paramName, boolean required) {
      List<String> errors = new ArrayList<>();
      JsonObject params = type.handlerParams();
      if (!params.has(paramName)) {
         if (required) {
            errors.add("missing required handlerParam '" + paramName + "'");
         }

         return errors;
      } else {
         JsonArray array = params.getAsJsonArray(paramName);
         if (array.isEmpty() && required) {
            errors.add("empty '" + paramName + "' array");
            return errors;
         }

         for (JsonElement elem : array) {
            String blockId = elem.getAsString();
            Block block = BlockHelper.resolve(blockId);
            if (block == null) {
               errors.add("unresolvable block '" + blockId + "' in '" + paramName + "'");
            }
         }

         return errors;
      }
   }

   protected static List<String> validateItemIdList(GatheringType type, String paramName, boolean required) {
      List<String> errors = new ArrayList<>();
      JsonObject params = type.handlerParams();
      if (!params.has(paramName)) {
         if (required) {
            errors.add("missing required handlerParam '" + paramName + "'");
         }

         return errors;
      } else {
         JsonArray array = params.getAsJsonArray(paramName);
         if (array.isEmpty() && required) {
            errors.add("empty '" + paramName + "' array");
            return errors;
         }

         for (JsonElement elem : array) {
            String itemId = elem.getAsString();
            Item item = ItemHelper.resolve(itemId);
            if (item == null || item == Items.AIR) {
               errors.add("unresolvable item '" + itemId + "' in '" + paramName + "'");
            }
         }

         return errors;
      }
   }

   protected static int countAnimalSpawnPoints(BuildingInstance building) {
      return (int)building.getResolvedPoints().stream().filter(p -> p.isType("animalSpawn")).count();
   }

   protected static AABB scanBoxAround(BlockPos origin, int radiusXZ, int radiusY) {
      return new AABB(
         origin.getX() - radiusXZ,
         origin.getY() - radiusY,
         origin.getZ() - radiusXZ,
         origin.getX() + radiusXZ,
         origin.getY() + radiusY,
         origin.getZ() + radiusXZ
      );
   }

   @Nullable
   public static BlockPos findClosestBlock(
      List<BlockPos> candidates, Predicate<BlockPos> filter, BlockPos reference, @Nullable GatheringTarget lastTarget, int batchRadius
   ) {
      BlockPos best = null;
      double bestDistSq = Double.MAX_VALUE;

      for (BlockPos pos : candidates) {
         if (filter.test(pos)) {
            double distSq = reference.distSqr(pos);
            if ((lastTarget == null || !(distSq > (double)batchRadius * batchRadius)) && distSq < bestDistSq) {
               bestDistSq = distSq;
               best = pos;
            }
         }
      }

      return best;
   }

   @Nullable
   public static BlockPos findClosestBlock(List<BlockPos> candidates, BlockPos reference, @Nullable GatheringTarget lastTarget, int batchRadius) {
      BlockPos best = null;
      double bestDistSq = Double.MAX_VALUE;

      for (BlockPos pos : candidates) {
         double distSq = reference.distSqr(pos);
         if ((lastTarget == null || !(distSq > (double)batchRadius * batchRadius)) && distSq < bestDistSq) {
            bestDistSq = distSq;
            best = pos;
         }
      }

      return best;
   }

   @Nullable
   protected static <T extends Entity> T findClosestEntity(List<T> candidates, BlockPos reference, @Nullable GatheringTarget lastTarget, int batchRadius) {
      T best = null;
      double bestDistSq = Double.MAX_VALUE;

      for (T entity : candidates) {
         double distSq = reference.distSqr(entity.blockPosition());
         if ((lastTarget == null || !(distSq > (double)batchRadius * batchRadius)) && distSq < bestDistSq) {
            bestDistSq = distSq;
            best = entity;
         }
      }

      return best;
   }
}
