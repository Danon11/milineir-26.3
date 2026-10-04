package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.ModEntities;
import org.slf4j.Logger;

public final class WaypointTraversalTester implements AutoCloseable {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final double TEST_FOLLOW_RANGE = 64.0;
   private static final int PATH_ACCURACY = 1;
   private static final float ALMOST_REACHED_THRESHOLD = 8.0F;
   private final MillVillager testMob;
   private final Map<WaypointTraversalTester.PairKey, WaypointTraversalTester.Result> cache = new HashMap<>();
   private boolean firstFailureLogged = false;

   public WaypointTraversalTester(ServerLevel level, @Nullable Village village) {
      MillVillager mob = (MillVillager)((EntityType)ModEntities.MILL_VILLAGER.get()).create(level);
      if (mob != null) {
         mob.setNoAi(true);
         mob.setSilent(true);
         mob.setInvulnerable(true);
         if (village != null) {
            mob.setVillageId(village.getId());
         }

         AttributeInstance attr = mob.getAttribute(Attributes.FOLLOW_RANGE);
         if (attr != null) {
            attr.setBaseValue(64.0);
         }

         if (!level.addFreshEntity(mob)) {
            LOGGER.warn("[Millenaire] WaypointTraversalTester: addFreshEntity rejected the test mob");
            mob = null;
         }
      }

      this.testMob = mob;
   }

   public WaypointTraversalTester(ServerLevel level) {
      this(level, null);
   }

   public WaypointTraversalTester.Result findPath(BlockPos from, BlockPos to) {
      if (this.testMob == null) {
         return WaypointTraversalTester.Result.PERMISSIVE;
      }

      WaypointTraversalTester.PairKey key = pairKey(from, to);
      WaypointTraversalTester.Result cached = this.cache.get(key);
      if (cached != null) {
         return cached;
      }

      ServerLevel sl = (ServerLevel)this.testMob.level();
      BlockPos resolvedFrom = snapToSubUnitFloor(from, sl);
      BlockPos resolvedTo = snapToSubUnitFloor(to, sl);
      this.testMob.teleportTo(resolvedFrom.getX() + 0.5, resolvedFrom.getY(), resolvedFrom.getZ() + 0.5);
      this.testMob.setOnGround(true);

      WaypointTraversalTester.Result result;
      try {
         Path path = this.testMob.getNavigation().createPath(resolvedTo, 1);
         boolean accept = path != null && (path.canReach() || path.getDistToTarget() < 8.0F);
         if (accept) {
            List<BlockPos> nodes = new ArrayList<>(path.getNodeCount());

            for (int i = 0; i < path.getNodeCount(); i++) {
               nodes.add(path.getNode(i).asBlockPos());
            }

            result = new WaypointTraversalTester.Result(true, Collections.unmodifiableList(nodes));
         } else {
            if (!this.firstFailureLogged) {
               this.firstFailureLogged = true;
               this.logFailureDetails(from, to, path, resolvedTo);
            }

            result = WaypointTraversalTester.Result.FAIL;
         }
      } catch (Throwable t) {
         LOGGER.warn("[Millenaire] WaypointTraversalTester pathfind threw: {}", t.toString());
         result = WaypointTraversalTester.Result.PERMISSIVE;
      }

      this.cache.put(key, result);
      return result;
   }

   private static BlockPos snapToSubUnitFloor(BlockPos pos, ServerLevel level) {
      BlockState here = level.getBlockState(pos);
      if (!here.isAir()) {
         return pos;
      }

      BlockPos below = pos.below();
      BlockState belowState = level.getBlockState(below);
      if (belowState.isAir()) {
         return pos;
      }

      VoxelShape shape = belowState.getCollisionShape(level, below);
      if (shape.isEmpty()) {
         return pos;
      }

      double maxY = shape.max(Axis.Y);
      return maxY < 0.999 ? below : pos;
   }

   public void close() {
      if (this.testMob != null) {
         this.testMob.discard();
      }
   }

   private static WaypointTraversalTester.PairKey pairKey(BlockPos a, BlockPos b) {
      long la = a.asLong();
      long lb = b.asLong();
      return la < lb ? new WaypointTraversalTester.PairKey(la, lb) : new WaypointTraversalTester.PairKey(lb, la);
   }

   private void logFailureDetails(BlockPos from, BlockPos to, @Nullable Path path, BlockPos targetTo) {
      int nodeCount = path == null ? -1 : path.getNodeCount();
      boolean canReach = path != null && path.canReach();
      BlockPos mobPos = this.testMob.blockPosition();
      ServerLevel sl = (ServerLevel)this.testMob.level();
      LOGGER.info(
         "[Millenaire] First pathfind failure: from={} to={} (resolvedTo={}) → path={}, nodeCount={}, canReach={}, mobPos={}, onGround={}, mob@={} mobBelow={}, target@={} targetBelow={}",
         new Object[]{
            from,
            to,
            targetTo,
            path,
            nodeCount,
            canReach,
            mobPos,
            this.testMob.onGround(),
            sl.getBlockState(mobPos),
            sl.getBlockState(mobPos.below()),
            sl.getBlockState(targetTo),
            sl.getBlockState(targetTo.below())
         }
      );
   }

   private record PairKey(long lo, long hi) {
   }

   public record Result(boolean reachable, List<BlockPos> nodes) {
      public static final WaypointTraversalTester.Result FAIL = new WaypointTraversalTester.Result(false, List.of());
      public static final WaypointTraversalTester.Result PERMISSIVE = new WaypointTraversalTester.Result(true, List.of());
   }
}
