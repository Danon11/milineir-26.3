package org.millenaire.test;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.SpecialPoint;
import org.millenaire.village.VillageWaypointGraph;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class VillageWaypointGraphRebuildGameTests {
   private static final ResourceLocation TEST_CULTURE = ResourceLocation.fromNamespaceAndPath("millenaire", "norman");
   private static final ResourceLocation TEST_PLAN_ID = ResourceLocation.fromNamespaceAndPath("millenaire", "test_building");

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 200)
   public static void rebuild_openGround_producesValidatedEdgesWithNodes(GameTestHelper helper) {
      paveStoneSlab(helper, 0, 0, 32, 32);
      BlockPos center = helper.absolutePos(new BlockPos(16, 1, 16));
      List<BuildingInstance> buildings = new ArrayList<>();
      buildings.add(makeBuilding(helper.absolutePos(new BlockPos(4, 1, 4)), helper.absolutePos(new BlockPos(4, 1, 4))));
      buildings.add(makeBuilding(helper.absolutePos(new BlockPos(28, 1, 4)), helper.absolutePos(new BlockPos(28, 1, 4))));
      buildings.add(makeBuilding(helper.absolutePos(new BlockPos(16, 1, 28)), helper.absolutePos(new BlockPos(16, 1, 28))));
      VillageWaypointGraph graph = new VillageWaypointGraph();
      graph.rebuildForTesting(buildings, center, helper.getLevel());
      if (graph.waypointCount() != 4) {
         helper.fail("Expected 4 waypoints (3 buildings + center), got " + graph.waypointCount());
      } else {
         List<VillageWaypointGraph.DirectedEdge> edges = graph.getEdges();
         if (edges.size() < 3) {
            helper.fail("Expected at least 3 edges on open ground (full connectivity), got " + edges.size());
         } else {
            for (VillageWaypointGraph.DirectedEdge e : edges) {
               if (e.pathNodes().isEmpty()) {
                  helper.fail(
                     "Edge "
                        + e.from().pos().toShortString()
                        + " → "
                        + e.to().pos().toShortString()
                        + " has empty pathNodes — validation bypassed unexpectedly?"
                  );
                  return;
               }
            }

            helper.succeed();
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 200)
   public static void rebuild_buildingsBeyondMaxDistance_areNotConnected(GameTestHelper helper) {
      paveStoneSlab(helper, 0, 0, 16, 16);
      BlockPos center = helper.absolutePos(new BlockPos(8, 1, 8));
      BlockPos near = helper.absolutePos(new BlockPos(8, 1, 8));
      BlockPos far = near.offset(70, 0, 0);
      List<BuildingInstance> buildings = List.of(makeBuilding(near, near), makeBuilding(far, far));
      VillageWaypointGraph graph = new VillageWaypointGraph();
      graph.rebuildForTesting(buildings, center, helper.getLevel());

      for (VillageWaypointGraph.DirectedEdge e : graph.getEdges()) {
         BlockPos a = e.from().pos();
         BlockPos b = e.to().pos();
         if (a.equals(near) && b.equals(far) || a.equals(far) && b.equals(near)) {
            helper.fail("Edge created between waypoints separated by " + a.distManhattan(b) + " blocks (> MAX_EDGE_DISTANCE)");
            return;
         }
      }

      helper.succeed();
   }

   private static BuildingInstance makeBuilding(BlockPos origin, BlockPos pathStart) {
      BlockPos relative = pathStart.subtract(origin);
      BuildingPlan plan = new BuildingPlan(
         TEST_PLAN_ID,
         TEST_CULTURE,
         "test_template",
         10,
         10,
         10,
         0,
         1,
         BlockPos.ZERO,
         List.of(),
         "default",
         "flat",
         List.of(new SpecialPoint("pathStartPos", null, null, relative)),
         null
      );
      BuildingInstance b = new BuildingInstance(BuildingId.random(), plan.id(), origin, Rotation.NONE, BuildingInstance.Status.COMPLETE);
      b.resolveSpecialPoints(plan);
      return b;
   }

   private static void paveStoneSlab(GameTestHelper helper, int x0, int z0, int x1, int z1) {
      for (int x = x0; x < x1; x++) {
         for (int z = z0; z < z1; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);

            for (int y = 1; y < 6; y++) {
               helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
         }
      }
   }
}
