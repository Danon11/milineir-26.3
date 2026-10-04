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
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.block.AppleTreeSaplingBlock;
import org.millenaire.block.ModBlocks;
import org.millenaire.block.OliveTreeSaplingBlock;
import org.millenaire.block.PistachioTreeSaplingBlock;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.SpecialPoint;
import org.millenaire.culture.ModCultures;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.gathering.GatheringTarget;
import org.millenaire.goal.gathering.GatheringType;
import org.millenaire.village.Village;
import org.slf4j.Logger;

public class SaplingPlantingHandler extends AbstractGatheringHandler {
   private static final Logger LOGGER = LogUtils.getLogger();

   public String id() {
      return "sapling_planting";
   }

   public Item getDefaultHeldItem(GatheringType type) {
      return Items.OAK_SAPLING;
   }

   public boolean canStart(GoalContext ctx, GatheringType type) {
      List<BuildingInstance> buildings = this.resolveTargetBuildingsWithGrove(ctx, type);
      String subtypeFilter = this.getSubtypeFilter(type);
      return !this.collectEmptyTreeSpawns(ctx.level(), buildings, subtypeFilter).isEmpty();
   }

   @Nullable
   public GatheringTarget findTarget(GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget) {
      List<BuildingInstance> buildings = this.resolveTargetBuildingsWithGrove(ctx, type);
      String subtypeFilter = this.getSubtypeFilter(type);
      List<BlockPos> candidates = this.collectEmptyTreeSpawns(ctx.level(), buildings, subtypeFilter);
      BlockPos reference = lastTarget != null ? lastTarget.navigationPos() : ctx.villager().blockPosition();
      BlockPos best = findClosestBlock(candidates, reference, lastTarget, type.batchRadius());
      return best != null ? new GatheringTarget.BlockTarget(best) : null;
   }

   public boolean performAction(GoalContext ctx, GatheringType type, GatheringTarget target) {
      if (target instanceof GatheringTarget.BlockTarget blockTarget) {
         BlockPos pos = blockTarget.pos();
         ServerLevel level = ctx.level();
         String subtype = this.findSubtypeAt(pos, ctx.village());
         BlockState stateAtPos = level.getBlockState(pos);
         if (!isPlantablePosition(stateAtPos)) {
            return true;
         }

         Block sapling = this.resolveSaplingFromSubtype(subtype);
         BlockState saplingState = sapling.defaultBlockState();
         if (!saplingState.canSurvive(level, pos)) {
            return true;
         }

         level.setBlock(pos, saplingState, 3);
         return true;
      } else {
         return true;
      }
   }

   private List<BuildingInstance> resolveTargetBuildingsWithGrove(GoalContext ctx, GatheringType type) {
      List<BuildingInstance> buildings = this.resolveTargetBuildings(ctx, type);
      return !buildings.isEmpty() ? buildings : this.findBuildingsWithTag(ctx.village(), "grove");
   }

   @Nullable
   private String getSubtypeFilter(GatheringType type) {
      JsonObject params = type.handlerParams();
      return GsonHelper.getAsString(params, "subtypeFilter", null);
   }

   private List<BlockPos> collectEmptyTreeSpawns(ServerLevel level, List<BuildingInstance> buildings, @Nullable String subtypeFilter) {
      List<BlockPos> candidates = new ArrayList<>();

      for (BuildingInstance building : buildings) {
         if (building.isOperational()) {
            for (SpecialPoint sp : building.getResolvedPoints()) {
               if (sp.isType("treeSpawn") && (subtypeFilter == null || subtypeFilter.equals(sp.subtype()))) {
                  BlockPos spPos = sp.pos();
                  if (level.isLoaded(spPos)) {
                     BlockState stateAt = level.getBlockState(spPos);
                     if (!stateAt.is(BlockTags.SAPLINGS)
                        && !stateAt.is(BlockTags.LOGS)
                        && !stateAt.is(BlockTags.LEAVES)
                        && !(stateAt.getBlock() instanceof AppleTreeSaplingBlock)
                        && !(stateAt.getBlock() instanceof OliveTreeSaplingBlock)
                        && !(stateAt.getBlock() instanceof PistachioTreeSaplingBlock)
                        && isPlantablePosition(stateAt)) {
                        Block sapling = this.resolveSaplingFromSubtype(sp.subtype());
                        if (sapling.defaultBlockState().canSurvive(level, spPos)) {
                           candidates.add(spPos);
                        }
                     }
                  }
               }
            }
         }
      }

      return candidates;
   }

   @Nullable
   private String findSubtypeAt(BlockPos pos, Village village) {
      for (BuildingInstance building : village.getBuildings()) {
         BuildingPlan plan = ModCultures.getBuildingPlan(building.getPlanId());
         if (plan != null) {
            for (SpecialPoint sp : building.getResolvedPoints()) {
               if (sp.isType("treeSpawn") && sp.pos().equals(pos)) {
                  return sp.subtype();
               }
            }
         }
      }

      return null;
   }

   private Block resolveSaplingFromSubtype(@Nullable String subtype) {
      if (subtype == null) {
         return Blocks.OAK_SAPLING;
      }

      return (Block)(switch (subtype) {
         case "oak" -> Blocks.OAK_SAPLING;
         case "pine" -> Blocks.SPRUCE_SAPLING;
         case "dark_oak" -> Blocks.DARK_OAK_SAPLING;
         case "birch" -> Blocks.BIRCH_SAPLING;
         case "jungle" -> Blocks.JUNGLE_SAPLING;
         case "acacia" -> Blocks.ACACIA_SAPLING;
         case "apple" -> (AppleTreeSaplingBlock)ModBlocks.APPLE_TREE_SAPLING.get();
         case "olive" -> (OliveTreeSaplingBlock)ModBlocks.OLIVE_TREE_SAPLING.get();
         case "pistachio" -> (PistachioTreeSaplingBlock)ModBlocks.PISTACHIO_TREE_SAPLING.get();
         default -> Blocks.OAK_SAPLING;
      });
   }

   private static boolean isPlantablePosition(BlockState state) {
      if (state.isAir()) {
         return true;
      } else if (state.is(Blocks.SNOW)) {
         return true;
      } else if (state.is(BlockTags.REPLACEABLE_BY_TREES)) {
         return true;
      } else {
         return state.is(BlockTags.FLOWERS) ? true : state.is(BlockTags.SMALL_FLOWERS);
      }
   }
}
