package org.millenaire.goal.gathering.handler;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.SpecialPoint;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.gathering.GatheringTarget;
import org.millenaire.goal.gathering.GatheringType;
import org.millenaire.item.BlockHelper;
import org.millenaire.item.ItemHelper;
import org.millenaire.village.Village;
import org.slf4j.Logger;

public class FruitHarvestingHandler extends AbstractGatheringHandler {
   private static final Logger LOGGER = LogUtils.getLogger();

   public String id() {
      return "fruit_harvesting";
   }

   public List<String> validate(GatheringType type) {
      List<String> errors = new ArrayList<>();
      errors.addAll(validateBlockParam(type, "targetBlock", true));
      errors.addAll(validateItemParam(type, "harvestItem", true));
      return errors;
   }

   public boolean canStart(GoalContext ctx, GatheringType type) {
      return !this.collectRipeFruits(ctx.level(), ctx.village(), type).isEmpty();
   }

   @Nullable
   public GatheringTarget findTarget(GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget) {
      List<BlockPos> candidates = this.collectRipeFruits(ctx.level(), ctx.village(), type);
      BlockPos reference = lastTarget != null ? lastTarget.navigationPos() : ctx.villager().blockPosition();
      BlockPos best = findClosestBlock(candidates, reference, lastTarget, type.batchRadius());
      return best != null ? new GatheringTarget.BlockTarget(best) : null;
   }

   public boolean performAction(GoalContext ctx, GatheringType type, GatheringTarget target) {
      if (target instanceof GatheringTarget.BlockTarget blockTarget) {
         BlockPos pos = blockTarget.pos();
         ServerLevel level = ctx.level();
         BlockState state = level.getBlockState(pos);
         JsonObject params = type.handlerParams();
         Block targetBlock = BlockHelper.resolve(GsonHelper.getAsString(params, "targetBlock", ""));
         if (targetBlock != null && state.is(targetBlock)) {
            int ripeAge = GsonHelper.getAsInt(params, "ripeAge", 3);
            String agePropertyName = GsonHelper.getAsString(params, "ageProperty", "age");
            if (state.getBlock().getStateDefinition().getProperty(agePropertyName) instanceof IntegerProperty ageProp) {
               int currentAge = (Integer)state.getValue(ageProp);
               if (currentAge != ripeAge) {
                  return true;
               }

               int resetAge = GsonHelper.getAsInt(params, "resetAge", 0);
               level.setBlock(pos, (BlockState)state.setValue(ageProp, resetAge), 2);
               String harvestItemId = GsonHelper.getAsString(params, "harvestItem", "");
               Item harvestItem = ItemHelper.resolve(harvestItemId);
               if (harvestItem != null) {
                  int harvestCount = GsonHelper.getAsInt(params, "harvestCount", 1);
                  ctx.villager().getInventory().add(harvestItem, harvestCount);
               }

               return true;
            } else {
               return true;
            }
         } else {
            return true;
         }
      } else {
         return true;
      }
   }

   private List<BlockPos> collectRipeFruits(ServerLevel level, Village village, GatheringType type) {
      JsonObject params = type.handlerParams();
      Block targetBlock = BlockHelper.resolve(GsonHelper.getAsString(params, "targetBlock", ""));
      if (targetBlock == null) {
         return List.of();
      }

      int ripeAge = GsonHelper.getAsInt(params, "ripeAge", 3);
      String agePropertyName = GsonHelper.getAsString(params, "ageProperty", "age");
      String buildingTag = GsonHelper.getAsString(params, "buildingTag", "grove");
      List<BlockPos> candidates = new ArrayList<>();

      for (BuildingInstance building : village.getOperationalBuildingsWithTag(buildingTag)) {
         for (SpecialPoint sp : building.getResolvedPoints()) {
            if (sp.isType("treeSpawn")) {
               BlockPos center = sp.pos();
               int scanRadius = 5;

               for (int dx = -scanRadius; dx <= scanRadius; dx++) {
                  for (int dy = -2; dy <= 8; dy++) {
                     for (int dz = -scanRadius; dz <= scanRadius; dz++) {
                        BlockPos checkPos = center.offset(dx, dy, dz);
                        if (level.isLoaded(checkPos)) {
                           BlockState state = level.getBlockState(checkPos);
                           if (state.is(targetBlock)
                              && state.getBlock().getStateDefinition().getProperty(agePropertyName) instanceof IntegerProperty ageProp
                              && (Integer)state.getValue(ageProp) == ripeAge) {
                              candidates.add(checkPos);
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return candidates;
   }
}
