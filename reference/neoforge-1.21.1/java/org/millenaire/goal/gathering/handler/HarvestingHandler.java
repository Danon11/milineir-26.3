package org.millenaire.goal.gathering.handler;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.gathering.GatheringTarget;
import org.millenaire.goal.gathering.GatheringType;
import org.millenaire.item.BlockHelper;
import org.slf4j.Logger;

public class HarvestingHandler extends AbstractGatheringHandler {
   private static final Logger LOGGER = LogUtils.getLogger();
   private final Map<ResourceLocation, Predicate<BlockState>> predicateCache = new ConcurrentHashMap<>();

   public String id() {
      return "harvesting";
   }

   public String getHeldToolCategoryId(GatheringType type) {
      return "toolshoe";
   }

   public List<String> validate(GatheringType type) {
      return validateBlockParam(type, "targetBlock", true);
   }

   public boolean canStart(GoalContext ctx, GatheringType type) {
      Predicate<BlockState> predicate = this.getCachedPredicate(type);

      for (BlockPos soil : this.collectSoilPositions(ctx, type)) {
         BlockPos above = soil.above();
         if (ctx.level().isLoaded(above) && predicate.test(ctx.level().getBlockState(above))) {
            return true;
         }
      }

      return false;
   }

   @Nullable
   public GatheringTarget findTarget(GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget) {
      Predicate<BlockState> predicate = this.getCachedPredicate(type);
      ServerLevel level = ctx.level();
      List<BlockPos> soilPositions = this.collectSoilPositions(ctx, type);
      List<BlockPos> abovePositions = new ArrayList<>(soilPositions.size());

      for (BlockPos soil : soilPositions) {
         abovePositions.add(soil.above());
      }

      BlockPos reference = lastTarget != null ? lastTarget.navigationPos() : ctx.villager().blockPosition();
      BlockPos best = findClosestBlock(
         abovePositions, pos -> level.isLoaded(pos) && predicate.test(level.getBlockState(pos)), reference, lastTarget, type.batchRadius()
      );
      return best != null ? new GatheringTarget.BlockTarget(best) : null;
   }

   public boolean performAction(GoalContext ctx, GatheringType type, GatheringTarget target) {
      if (target instanceof GatheringTarget.BlockTarget blockTarget) {
         BlockPos pos = blockTarget.pos();
         ServerLevel level = ctx.level();
         Predicate<BlockState> predicate = this.getCachedPredicate(type);
         if (!predicate.test(level.getBlockState(pos))) {
            return true;
         }

         BlockState state = level.getBlockState(pos);
         level.destroyBlock(pos, false);
         boolean skipDrops = GsonHelper.getAsBoolean(type.handlerParams(), "skipDrops", false);
         if (!skipDrops) {
            for (ItemStack drop : Block.getDrops(state, level, pos, null)) {
               ctx.villager().getInventory().add(drop.getItem(), drop.getCount());
            }
         }

         return true;
      } else {
         return true;
      }
   }

   public void onClear() {
      this.predicateCache.clear();
   }

   private Predicate<BlockState> getCachedPredicate(GatheringType type) {
      return this.predicateCache.computeIfAbsent(type.id(), id -> this.buildPredicate(type));
   }

   private Predicate<BlockState> buildPredicate(GatheringType type) {
      JsonObject params = type.handlerParams();
      String targetBlockId = GsonHelper.getAsString(params, "targetBlock", null);
      JsonObject targetState = params.has("targetState") ? GsonHelper.getAsJsonObject(params, "targetState") : null;
      if (targetBlockId == null) {
         return state -> false;
      }

      Block targetBlock = BlockHelper.resolve(targetBlockId);
      return targetBlock == null ? state -> false : state -> {
         if (!state.is(targetBlock)) {
            return false;
         }

         if (targetState != null) {
            for (String key : targetState.keySet()) {
               int expectedValue = targetState.get(key).getAsInt();
               if (state.getBlock().getStateDefinition().getProperty(key) instanceof IntegerProperty intProp) {
                  Integer actual = (Integer)state.getValue(intProp);
                  if (actual != expectedValue) {
                     return false;
                  }
               }
            }
         }

         return true;
      };
   }
}
