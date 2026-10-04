package org.millenaire.goal.gathering.handler;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.SpecialPoint;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.gathering.GatheringTarget;
import org.millenaire.goal.gathering.GatheringType;
import org.millenaire.village.Village;
import org.slf4j.Logger;

public class FishingHandler extends AbstractGatheringHandler {
   private static final Logger LOGGER = LogUtils.getLogger();

   public String id() {
      return "fishing";
   }

   public Item getDefaultHeldItem(GatheringType type) {
      return Items.FISHING_ROD;
   }

   public List<String> validate(GatheringType type) {
      return this.validateItemList(type, "loot", false);
   }

   public boolean supportsRemoteAction() {
      return true;
   }

   public boolean canStart(GoalContext ctx, GatheringType type) {
      List<BlockPos> spots = this.findFishingSpots(ctx.village(), type);
      return !spots.isEmpty();
   }

   @Nullable
   public GatheringTarget findTarget(GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget) {
      List<BlockPos> spots = this.findFishingSpots(ctx.village(), type);
      if (spots.isEmpty()) {
         return null;
      }

      BlockPos reference = lastTarget != null ? lastTarget.navigationPos() : ctx.villager().blockPosition();
      BlockPos best = findClosestBlock(spots, reference, lastTarget, type.batchRadius());
      return best != null ? new GatheringTarget.BlockTarget(best) : null;
   }

   public boolean performAction(GoalContext ctx, GatheringType type, GatheringTarget target) {
      Map<Item, Integer> loot = this.parseItemList(type.handlerParams(), "loot");
      if (!loot.isEmpty()) {
         for (Entry<Item, Integer> entry : loot.entrySet()) {
            ctx.villager().getInventory().add(entry.getKey(), entry.getValue());
         }
      } else {
         ctx.villager().getInventory().add(Items.COD, 1);
      }

      LOGGER.debug("Fishing: fish added to villager inventory at {}", target.navigationPos());
      return true;
   }

   private List<BlockPos> findFishingSpots(Village village, GatheringType type) {
      String buildingTag = this.getBuildingTag(type);
      List<BlockPos> positions = new ArrayList<>();

      for (BuildingInstance building : buildingTag != null ? village.getOperationalBuildingsWithTag(buildingTag) : village.getBuildings()) {
         if (building.isOperational()) {
            for (SpecialPoint sp : building.getResolvedPoints()) {
               if (sp.isType("fishingSpot")) {
                  positions.add(sp.pos());
               }
            }
         }
      }

      return positions;
   }
}
