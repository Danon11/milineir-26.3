package org.millenaire.goal.gathering.handler;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Plane;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.culture.ModCultures;
import org.millenaire.entity.VillagerInventory;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.gathering.GatheringTarget;
import org.millenaire.goal.gathering.GatheringType;
import org.millenaire.tool.ToolCategory;
import org.millenaire.tool.ToolCategoryRegistry;
import org.millenaire.village.Village;
import org.slf4j.Logger;

public class ChoppingHandler extends AbstractGatheringHandler {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int GROVE_MARGIN = 3;
   private static final int GROVE_HEIGHT = 20;
   private static final int MAX_LOGS_IN_INVENTORY = 64;
   private static final int MAX_LEAVES_PER_ACTION = 3;
   private static final int SAPLING_DROP_CHANCE = 4;

   public String id() {
      return "chopping";
   }

   public String getHeldToolCategoryId(GatheringType type) {
      return "toolsaxe";
   }

   public int getActionCooldown(GoalContext ctx, GatheringType type) {
      ToolCategory category = ToolCategoryRegistry.get("toolsaxe");
      if (category == null) {
         return type.actionCooldown();
      }

      float efficiency = category.getBestDestroySpeed(
         item -> ctx.villager().getInventory().getCount(item) > 0, Blocks.OAK_LOG.defaultBlockState(), Items.WOODEN_AXE
      );
      return 20 - (int)efficiency * 2;
   }

   public boolean canStart(GoalContext ctx, GatheringType type) {
      if (this.countLogsInInventory(ctx.villager().getInventory()) >= 64) {
         return false;
      }

      List<BuildingInstance> groves = this.resolveTargetBuildings(ctx, type);
      if (groves.isEmpty()) {
         return false;
      }

      ServerLevel level = ctx.level();
      List<ChoppingHandler.BlockBounds> otherBounds = this.computeOtherBuildingBounds(ctx.village(), groves);

      for (BuildingInstance grove : groves) {
         if (this.findLogInGrove(level, grove, otherBounds) != null) {
            return true;
         }
      }

      return false;
   }

   @Nullable
   public GatheringTarget findTarget(GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget) {
      ServerLevel level = ctx.level();
      List<BuildingInstance> groves = this.resolveTargetBuildings(ctx, type);
      if (groves.isEmpty()) {
         return null;
      }

      List<ChoppingHandler.BlockBounds> otherBounds = this.computeOtherBuildingBounds(ctx.village(), groves);
      if (lastTarget != null) {
         BlockPos lastPos = lastTarget.navigationPos();
         BlockPos below = this.findLogBelow(level, lastPos);
         if (below != null && this.isInAnyGrove(below, groves) && !this.isInAnyOtherBuilding(below, otherBounds)) {
            return new GatheringTarget.BlockTarget(below);
         }

         BuildingInstance currentGrove = this.findContainingGrove(lastPos, groves);
         if (currentGrove != null) {
            BlockPos found = this.findLogInGrove(level, currentGrove, otherBounds);
            if (found != null) {
               return new GatheringTarget.BlockTarget(found);
            }
         }
      }

      BlockPos villagerPos = ctx.villager().blockPosition();
      double bestDistSq = Double.MAX_VALUE;
      BlockPos bestLog = null;

      for (BuildingInstance grove : groves) {
         BlockPos log = this.findLogInGrove(level, grove, otherBounds);
         if (log != null) {
            double distSq = villagerPos.distSqr(grove.getOrigin());
            if (distSq < bestDistSq) {
               bestDistSq = distSq;
               bestLog = log;
            }
         }
      }

      return bestLog != null ? new GatheringTarget.BlockTarget(bestLog) : null;
   }

   public boolean performAction(GoalContext ctx, GatheringType type, GatheringTarget target) {
      if (!(target instanceof GatheringTarget.BlockTarget blockTarget)) {
         return true;
      } else {
         BlockPos pos = blockTarget.pos();
         ServerLevel level = ctx.level();
         BlockState state = level.getBlockState(pos);
         if (!state.is(BlockTags.LOGS)) {
            return true;
         }

         List<ItemStack> drops = Block.getDrops(state, level, pos, null);
         level.destroyBlock(pos, false);
         VillagerInventory inventory = ctx.villager().getInventory();

         for (ItemStack drop : drops) {
            inventory.add(drop.getItem(), drop.getCount());
         }

         this.cutNearbyLeaves(level, pos, inventory);
         this.breakNearbyBeeNests(level, pos);
         return true;
      }
   }

   private void breakNearbyBeeNests(ServerLevel level, BlockPos logPos) {
      MutableBlockPos cursor = new MutableBlockPos();

      for (int dy = -1; dy <= 1; dy++) {
         for (Direction facing : Plane.HORIZONTAL) {
            cursor.set(logPos.getX() + facing.getStepX(), logPos.getY() + dy, logPos.getZ() + facing.getStepZ());
            if (level.isLoaded(cursor)) {
               BlockState state = level.getBlockState(cursor);
               if (state.is(Blocks.BEE_NEST)) {
                  BlockPos nestPos = cursor.immutable();
                  level.removeBlock(nestPos, false);
                  LOGGER.debug("Lumberjack removed bee_nest at {} (chopped log at {})", nestPos, logPos);
               }
            }
         }
      }
   }

   private void cutNearbyLeaves(ServerLevel level, BlockPos logPos, VillagerInventory inventory) {
      int leavesCut = 0;
      Random random = new Random();

      for (int dy = 0; dy <= 3; dy++) {
         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               if (leavesCut >= 3) {
                  return;
               }

               if (dx != 0 || dy != 0 || dz != 0) {
                  BlockPos leafPos = logPos.offset(dx, dy, dz);
                  if (level.isLoaded(leafPos)) {
                     BlockState leafState = level.getBlockState(leafPos);
                     if (leafState.is(BlockTags.LEAVES)) {
                        level.destroyBlock(leafPos, false);
                        leavesCut++;
                        if (random.nextInt(4) == 0) {
                           Item sapling = this.resolveSaplingFromLeaf(leafState);
                           if (sapling != null) {
                              inventory.add(sapling, 1);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Nullable
   private Item resolveSaplingFromLeaf(BlockState leafState) {
      Block block = leafState.getBlock();
      if (block == Blocks.OAK_LEAVES) {
         return Items.OAK_SAPLING;
      } else if (block == Blocks.SPRUCE_LEAVES) {
         return Items.SPRUCE_SAPLING;
      } else if (block == Blocks.BIRCH_LEAVES) {
         return Items.BIRCH_SAPLING;
      } else if (block == Blocks.JUNGLE_LEAVES) {
         return Items.JUNGLE_SAPLING;
      } else if (block == Blocks.ACACIA_LEAVES) {
         return Items.ACACIA_SAPLING;
      } else if (block == Blocks.DARK_OAK_LEAVES) {
         return Items.DARK_OAK_SAPLING;
      } else if (block == Blocks.CHERRY_LEAVES) {
         return Items.CHERRY_SAPLING;
      } else {
         return block == Blocks.MANGROVE_LEAVES ? Items.MANGROVE_PROPAGULE : Items.OAK_SAPLING;
      }
   }

   private int countLogsInInventory(VillagerInventory inventory) {
      return inventory.getCountByTag(ItemTags.LOGS);
   }

   @Nullable
   private BlockPos findLogInGrove(ServerLevel level, BuildingInstance grove, List<ChoppingHandler.BlockBounds> otherBounds) {
      ChoppingHandler.BlockBounds bounds = this.computeGroveBounds(grove);
      if (bounds == null) {
         return null;
      }

      MutableBlockPos mutable = new MutableBlockPos();
      BlockPos best = null;
      int bestY = Integer.MIN_VALUE;

      for (int x = bounds.minX; x <= bounds.maxX; x++) {
         for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
            for (int y = bounds.maxY; y >= bounds.minY; y--) {
               mutable.set(x, y, z);
               if (level.isLoaded(mutable) && level.getBlockState(mutable).is(BlockTags.LOGS)) {
                  if (!this.isInAnyOtherBuilding(mutable, otherBounds) && y > bestY) {
                     bestY = y;
                     best = mutable.immutable();
                  }
                  break;
               }
            }
         }
      }

      return best;
   }

   @Nullable
   private BlockPos findLogBelow(ServerLevel level, BlockPos pos) {
      MutableBlockPos mutable = new MutableBlockPos(pos.getX(), pos.getY() - 1, pos.getZ());

      for (int dy = 1; dy <= 20; dy++) {
         mutable.setY(pos.getY() - dy);
         if (!level.isLoaded(mutable)) {
            break;
         }

         BlockState state = level.getBlockState(mutable);
         if (state.is(BlockTags.LOGS)) {
            return mutable.immutable();
         }

         if (!state.isAir() && !state.is(BlockTags.LEAVES)) {
            break;
         }
      }

      return null;
   }

   private boolean isInAnyGrove(BlockPos pos, List<BuildingInstance> groves) {
      for (BuildingInstance grove : groves) {
         ChoppingHandler.BlockBounds bounds = this.computeGroveBounds(grove);
         if (bounds != null && bounds.contains(pos)) {
            return true;
         }
      }

      return false;
   }

   @Nullable
   private BuildingInstance findContainingGrove(BlockPos pos, List<BuildingInstance> groves) {
      for (BuildingInstance grove : groves) {
         ChoppingHandler.BlockBounds bounds = this.computeGroveBounds(grove);
         if (bounds != null && bounds.contains(pos)) {
            return grove;
         }
      }

      return null;
   }

   @Nullable
   private ChoppingHandler.BlockBounds computeGroveBounds(BuildingInstance grove) {
      BuildingPlan plan = ModCultures.getBuildingPlan(grove.getPlanId());
      if (plan == null) {
         return null;
      }

      int[] rect = computeFootprintRect(grove, plan, 3);
      return new ChoppingHandler.BlockBounds(
         rect[0], grove.getOrigin().getY(), rect[1], rect[0] + rect[2] - 1, grove.getOrigin().getY() + 20, rect[1] + rect[3] - 1
      );
   }

   private List<ChoppingHandler.BlockBounds> computeOtherBuildingBounds(Village village, List<BuildingInstance> groves) {
      List<ChoppingHandler.BlockBounds> result = new ArrayList<>();

      for (BuildingInstance b : village.getBuildings()) {
         if (b.isOperational() && !groves.contains(b)) {
            BuildingPlan plan = ModCultures.getBuildingPlan(b.getPlanId());
            if (plan != null) {
               int[] rect = computeFootprintRect(b, plan, 0);
               result.add(
                  new ChoppingHandler.BlockBounds(
                     rect[0], b.getOrigin().getY(), rect[1], rect[0] + rect[2] - 1, b.getOrigin().getY() + plan.height() - 1, rect[1] + rect[3] - 1
                  )
               );
            }
         }
      }

      return result;
   }

   private static int[] computeFootprintRect(BuildingInstance building, BuildingPlan plan, int margin) {
      int ox = building.getOrigin().getX();
      int oz = building.getOrigin().getZ();
      int w = plan.width();
      int d = plan.depth();

      return switch (building.getRotation()) {
         case CLOCKWISE_90 -> new int[]{ox - d + 1 - margin, oz - margin, d + 2 * margin, w + 2 * margin};
         case CLOCKWISE_180 -> new int[]{ox - w + 1 - margin, oz - d + 1 - margin, w + 2 * margin, d + 2 * margin};
         case COUNTERCLOCKWISE_90 -> new int[]{ox - margin, oz - w + 1 - margin, d + 2 * margin, w + 2 * margin};
         default -> new int[]{ox - margin, oz - margin, w + 2 * margin, d + 2 * margin};
      };
   }

   private boolean isInAnyOtherBuilding(BlockPos pos, List<ChoppingHandler.BlockBounds> otherBounds) {
      for (ChoppingHandler.BlockBounds bounds : otherBounds) {
         if (bounds.contains(pos)) {
            return true;
         }
      }

      return false;
   }

   private record BlockBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
      boolean contains(BlockPos pos) {
         return pos.getX() >= this.minX
            && pos.getX() <= this.maxX
            && pos.getY() >= this.minY
            && pos.getY() <= this.maxY
            && pos.getZ() >= this.minZ
            && pos.getZ() <= this.maxZ;
      }
   }
}
