package org.millenaire.goal.gathering.handler;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.SpecialPoint;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.gathering.GatheringTarget;
import org.millenaire.goal.gathering.GatheringType;
import org.millenaire.tool.ToolCategory;
import org.millenaire.tool.ToolCategoryRegistry;
import org.millenaire.village.Village;
import org.slf4j.Logger;

public class MiningHandler extends AbstractGatheringHandler {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Set<String> HARD_SUBTYPES = Set.of("stone", "sandstone", "red_sandstone", "diorite", "iron");
   private static final Set<String> SOFT_SUBTYPES = Set.of("sand", "clay", "gravel");
   private static final Set<String> FROZEN_SUBTYPES = Set.of("snow", "ice");

   public String id() {
      return "mining";
   }

   public List<String> validate(GatheringType type) {
      return this.validateItemList(type, "loot", false);
   }

   public int getActionCooldown(GoalContext ctx, GatheringType type) {
      String subtype = this.getSourceSubtype(type);
      if (subtype == null) {
         return type.actionCooldown();
      }

      if (FROZEN_SUBTYPES.contains(subtype)) {
         return 70;
      }

      float efficiency;
      if (SOFT_SUBTYPES.contains(subtype)) {
         ToolCategory category = ToolCategoryRegistry.get("toolsshovel");
         if (category == null) {
            return type.actionCooldown();
         }

         efficiency = category.getBestDestroySpeed(
            item -> ctx.villager().getInventory().getCount(item) > 0, Blocks.SAND.defaultBlockState(), Items.WOODEN_SHOVEL
         );
      } else {
         if (!HARD_SUBTYPES.contains(subtype)) {
            return type.actionCooldown();
         }

         ToolCategory category = ToolCategoryRegistry.get("toolspickaxe");
         if (category == null) {
            return type.actionCooldown();
         }

         efficiency = category.getBestDestroySpeed(
            item -> ctx.villager().getInventory().getCount(item) > 0, Blocks.SANDSTONE.defaultBlockState(), Items.WOODEN_PICKAXE
         );
      }

      return 140 - 4 * (int)efficiency;
   }

   public String getHeldToolCategoryId(GatheringType type) {
      String subtype = this.getSourceSubtype(type);
      if (subtype == null) {
         return "toolspickaxe";
      } else if (SOFT_SUBTYPES.contains(subtype)) {
         return "toolsshovel";
      } else {
         return HARD_SUBTYPES.contains(subtype) ? "toolspickaxe" : null;
      }
   }

   public boolean supportsRemoteAction() {
      return false;
   }

   public boolean canStart(GoalContext ctx, GatheringType type) {
      List<BlockPos> sources = this.findSourcePositions(ctx.village(), type);
      return !sources.isEmpty();
   }

   @Nullable
   public GatheringTarget findTarget(GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget) {
      List<BlockPos> sources = this.findSourcePositions(ctx.village(), type);
      if (sources.isEmpty()) {
         return null;
      }

      BlockPos reference = lastTarget != null ? lastTarget.navigationPos() : ctx.villager().blockPosition();
      BlockPos best = findClosestBlock(sources, reference, lastTarget, type.batchRadius());
      return best != null ? new GatheringTarget.BlockTarget(best) : null;
   }

   public boolean performAction(GoalContext ctx, GatheringType type, GatheringTarget target) {
      Map<Item, Integer> loot = this.parseItemList(type.handlerParams(), "loot");

      for (Entry<Item, Integer> entry : loot.entrySet()) {
         ctx.villager().getInventory().add(entry.getKey(), entry.getValue());
      }

      LOGGER.debug("Mining: loot added to villager inventory at {}", target.navigationPos());
      return true;
   }

   private List<BlockPos> findSourcePositions(Village village, GatheringType type) {
      String sourceSubtype = this.getSourceSubtype(type);
      String buildingTag = this.getBuildingTag(type);
      List<BlockPos> positions = new ArrayList<>();

      for (BuildingInstance building : buildingTag != null ? village.getOperationalBuildingsWithTag(buildingTag) : village.getBuildings()) {
         if (building.isOperational()) {
            for (SpecialPoint sp : building.getResolvedPoints()) {
               if (sp.isType("source") && (sourceSubtype == null || sourceSubtype.equals(sp.subtype()))) {
                  positions.add(sp.pos());
               }
            }
         }
      }

      return positions;
   }

   @Nullable
   private String getSourceSubtype(GatheringType type) {
      return GsonHelper.getAsString(type.handlerParams(), "sourceSubtype", null);
   }
}
