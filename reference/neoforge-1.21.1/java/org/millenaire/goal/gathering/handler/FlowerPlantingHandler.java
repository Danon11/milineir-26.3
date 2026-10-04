package org.millenaire.goal.gathering.handler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import org.millenaire.entity.VillagerInventory;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.gathering.GatheringTarget;
import org.millenaire.goal.gathering.GatheringType;
import org.millenaire.item.BlockHelper;
import org.slf4j.Logger;

public class FlowerPlantingHandler extends AbstractGatheringHandler {
   private static final Logger LOGGER = LogUtils.getLogger();

   public String id() {
      return "flower_planting";
   }

   public List<String> validate(GatheringType type) {
      List<String> errors = new ArrayList<>();
      errors.addAll(validateBlockList(type, "flowers", true));
      errors.addAll(validateItemParam(type, "seedItem", false));
      return errors;
   }

   public boolean canStart(GoalContext ctx, GatheringType type) {
      Item seedItem = this.resolveSeedItem(type);
      return seedItem != null && !ctx.villager().getInventory().has(seedItem, 1) && !this.homeBuildingHasItem(ctx, seedItem)
         ? false
         : this.findPlantableBlock(ctx.level(), ctx, type, null) != null;
   }

   @Nullable
   public GatheringTarget findTarget(GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget) {
      BlockPos found = this.findPlantableBlock(ctx.level(), ctx, type, lastTarget);
      return found != null ? new GatheringTarget.BlockTarget(found) : null;
   }

   public boolean performAction(GoalContext ctx, GatheringType type, GatheringTarget target) {
      if (target instanceof GatheringTarget.BlockTarget blockTarget) {
         BlockPos soilPos = blockTarget.pos();
         ServerLevel level = ctx.level();
         BlockPos placePos = soilPos.above();
         if (!level.getBlockState(placePos).isAir()) {
            return true;
         }

         List<Block> flowers = this.resolveFlowers(type);
         if (flowers.isEmpty()) {
            return true;
         }

         Block flower = flowers.get(ThreadLocalRandom.current().nextInt(flowers.size()));
         if (flower instanceof DoublePlantBlock && !level.getBlockState(placePos.above()).isAir()) {
            return true;
         }

         Item seedItem = this.resolveSeedItem(type);
         if (seedItem != null) {
            VillagerInventory inventory = ctx.villager().getInventory();
            if (!inventory.has(seedItem, 1) && !this.takeFromHomeBuilding(ctx, seedItem, 1)) {
               return false;
            }

            inventory.remove(seedItem, 1);
         }

         if (flower instanceof DoublePlantBlock doublePlant) {
            DoublePlantBlock.placeAt(level, doublePlant.defaultBlockState(), placePos, 3);
         } else {
            level.setBlock(placePos, flower.defaultBlockState(), 3);
         }

         return true;
      } else {
         return true;
      }
   }

   @Nullable
   private BlockPos findPlantableBlock(ServerLevel level, GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget) {
      List<BlockPos> soilPositions = this.collectSoilPositions(ctx, type);
      boolean needsDoubleAir = this.allFlowersAreDoublePlant(type);
      BlockPos reference = lastTarget != null ? lastTarget.navigationPos() : ctx.villager().blockPosition();
      return findClosestBlock(
         soilPositions,
         soil -> level.isLoaded(soil)
            && level.getBlockState(soil).is(Blocks.GRASS_BLOCK)
            && level.getBlockState(soil.above()).isAir()
            && (!needsDoubleAir || level.getBlockState(soil.above().above()).isAir()),
         reference,
         lastTarget,
         type.batchRadius()
      );
   }

   private boolean allFlowersAreDoublePlant(GatheringType type) {
      List<Block> flowers = this.resolveFlowers(type);
      if (flowers.isEmpty()) {
         return false;
      }

      for (Block flower : flowers) {
         if (!(flower instanceof DoublePlantBlock)) {
            return false;
         }
      }

      return true;
   }

   private List<Block> resolveFlowers(GatheringType type) {
      List<Block> flowers = new ArrayList<>();
      JsonObject params = type.handlerParams();
      if (!params.has("flowers")) {
         return flowers;
      }

      for (JsonElement elem : params.getAsJsonArray("flowers")) {
         String blockId = elem.getAsString();
         Block block = BlockHelper.resolve(blockId);
         if (block != null) {
            flowers.add(block);
         }
      }

      return flowers;
   }
}
