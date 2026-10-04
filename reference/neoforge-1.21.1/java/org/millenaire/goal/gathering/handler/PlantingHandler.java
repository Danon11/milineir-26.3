package org.millenaire.goal.gathering.handler;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import org.millenaire.block.BlockGrapeVine;
import org.millenaire.entity.VillagerInventory;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.gathering.GatheringTarget;
import org.millenaire.goal.gathering.GatheringType;
import org.millenaire.item.BlockHelper;
import org.slf4j.Logger;

public class PlantingHandler extends AbstractGatheringHandler {
   private static final Logger LOGGER = LogUtils.getLogger();

   public String id() {
      return "planting";
   }

   public Item getDefaultHeldItem(GatheringType type) {
      return Items.WHEAT_SEEDS;
   }

   public List<String> validate(GatheringType type) {
      List<String> errors = new ArrayList<>();
      errors.addAll(validateBlockParam(type, "cropBlock", true));
      errors.addAll(validateItemParam(type, "seedItem", false));
      return errors;
   }

   public boolean canStart(GoalContext ctx, GatheringType type) {
      Item seedItem = this.resolveSeedItem(type);
      return seedItem != null && !ctx.villager().getInventory().has(seedItem, 1) && !this.homeBuildingHasItem(ctx, seedItem)
         ? false
         : this.findPlantableBlock(ctx.level(), ctx, type, null, ctx.villager().blockPosition(), type.batchRadius()) != null;
   }

   @Nullable
   public GatheringTarget findTarget(GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget) {
      BlockPos found = this.findPlantableBlock(ctx.level(), ctx, type, lastTarget, ctx.villager().blockPosition(), type.batchRadius());
      return found != null ? new GatheringTarget.BlockTarget(found) : null;
   }

   public boolean performAction(GoalContext ctx, GatheringType type, GatheringTarget target) {
      if (!(target instanceof GatheringTarget.BlockTarget blockTarget)) {
         return true;
      } else {
         BlockPos soilPos = blockTarget.pos();
         ServerLevel level = ctx.level();
         BlockPos placePos = soilPos.above();
         BlockState aboveState = level.getBlockState(placePos);
         if (!aboveState.isAir() && !isClearableAboveSoil(aboveState)) {
            return true;
         }

         Item seedItem = this.resolveSeedItem(type);
         VillagerInventory inventory = ctx.villager().getInventory();
         if (seedItem != null && !inventory.has(seedItem, 1) && !this.takeFromHomeBuilding(ctx, seedItem, 1)) {
            return false;
         }

         BlockState soilState = level.getBlockState(soilPos);
         if (soilState.is(Blocks.GRASS_BLOCK) || soilState.is(Blocks.DIRT)) {
            level.setBlock(soilPos, Blocks.FARMLAND.defaultBlockState(), 3);
         }

         JsonObject params = type.handlerParams();
         String cropBlockId = GsonHelper.getAsString(params, "cropBlock", null);
         if (cropBlockId == null) {
            return true;
         }

         Block cropBlock = BlockHelper.resolve(cropBlockId);
         if (cropBlock == null) {
            return true;
         }

         boolean isVine = cropBlock instanceof BlockGrapeVine;
         if (!level.getBlockState(placePos).isAir()) {
            level.destroyBlock(placePos, false);
         }

         if (isVine) {
            BlockPos upperPos = placePos.above();
            if (!level.getBlockState(upperPos).isAir()) {
               level.destroyBlock(upperPos, false);
            }
         }

         BlockState cropState = cropBlock.defaultBlockState();
         if (seedItem != null && inventory.remove(seedItem, 1) < 1) {
            return false;
         }

         level.setBlock(placePos, cropState, 3);
         if (isVine) {
            BlockState upperState = (BlockState)cropState.setValue(BlockGrapeVine.HALF, Half.TOP);
            level.setBlock(placePos.above(), upperState, 3);
         }

         return true;
      }
   }

   @Nullable
   private BlockPos findPlantableBlock(
      ServerLevel level, GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget, BlockPos villagerPos, int batchRadius
   ) {
      List<BlockPos> soilPositions = this.collectSoilPositions(ctx, type);
      BlockPos reference = lastTarget != null ? lastTarget.navigationPos() : villagerPos;
      boolean isDoubleHeight = this.isDoubleHeightCrop(type);
      return findClosestBlock(
         soilPositions,
         soil -> level.isLoaded(soil)
            && isPlantableSoil(level.getBlockState(soil))
            && isFreeToPlan(level.getBlockState(soil.above()))
            && (!isDoubleHeight || isFreeToPlan(level.getBlockState(soil.above(2)))),
         reference,
         lastTarget,
         batchRadius
      );
   }

   private static boolean isPlantableSoil(BlockState state) {
      return state.is(Blocks.FARMLAND) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT);
   }

   private static boolean isFreeToPlan(BlockState state) {
      return state.isAir() || isClearableAboveSoil(state);
   }

   private boolean isDoubleHeightCrop(GatheringType type) {
      String cropBlockId = GsonHelper.getAsString(type.handlerParams(), "cropBlock", null);
      if (cropBlockId == null) {
         return false;
      }

      Block block = BlockHelper.resolve(cropBlockId);
      return block instanceof BlockGrapeVine;
   }

   private static boolean isClearableAboveSoil(BlockState state) {
      if (!state.is(Blocks.SNOW) && !state.is(BlockTags.LEAVES)) {
         Block block = state.getBlock();
         return block instanceof BushBlock && !(block instanceof CropBlock) && !(block instanceof BlockGrapeVine);
      } else {
         return true;
      }
   }
}
