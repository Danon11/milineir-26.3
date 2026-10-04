package org.millenaire.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.millenaire.village.WaypointTraversalTester;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class WaypointTraversalTesterGameTests {
   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 200)
   public static void findPath_openGround_returnsReachableWithNodes(GameTestHelper helper) {
      paveStoneSlab(helper, 0, 0, 16, 16);
      BlockPos from = helper.absolutePos(new BlockPos(2, 1, 2));
      BlockPos to = helper.absolutePos(new BlockPos(13, 1, 13));

      try (WaypointTraversalTester tester = new WaypointTraversalTester(helper.getLevel())) {
         WaypointTraversalTester.Result r = tester.findPath(from, to);
         if (!r.reachable()) {
            helper.fail("Open ground pathfind returned unreachable — testeur cassé (addFreshEntity / setOnGround mismatch?) from=" + from + " to=" + to);
            return;
         }

         if (r.nodes().isEmpty()) {
            helper.fail("Open ground pathfind reachable but nodes list is empty");
            return;
         }
      }

      helper.succeed();
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 200)
   public static void findPath_repeatedCalls_returnConsistentResults(GameTestHelper helper) {
      paveStoneSlab(helper, 0, 0, 16, 16);
      BlockPos a = helper.absolutePos(new BlockPos(2, 1, 2));
      BlockPos b = helper.absolutePos(new BlockPos(13, 1, 8));
      BlockPos c = helper.absolutePos(new BlockPos(7, 1, 13));

      try (WaypointTraversalTester tester = new WaypointTraversalTester(helper.getLevel())) {
         WaypointTraversalTester.Result ab1 = tester.findPath(a, b);
         WaypointTraversalTester.Result ac1 = tester.findPath(a, c);
         WaypointTraversalTester.Result ab2 = tester.findPath(a, b);
         WaypointTraversalTester.Result bc = tester.findPath(b, c);
         if (!ab1.reachable() || !ac1.reachable() || !bc.reachable()) {
            helper.fail("Pairs on open ground should all be reachable: ab=" + ab1.reachable() + " ac=" + ac1.reachable() + " bc=" + bc.reachable());
            return;
         }

         if (ab1.nodes().size() != ab2.nodes().size()) {
            helper.fail("Cached call returned different result: " + ab1.nodes().size() + " vs " + ab2.nodes().size());
            return;
         }
      }

      helper.succeed();
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 200)
   public static void findPath_fromInsideSealedCage_returnsUnreachable(GameTestHelper helper) {
      paveStoneSlab(helper, 0, 0, 16, 16);
      int fx = 2;
      int fz = 8;
      helper.setBlock(new BlockPos(fx + 1, 1, fz), Blocks.STONE);
      helper.setBlock(new BlockPos(fx + 1, 2, fz), Blocks.STONE);
      helper.setBlock(new BlockPos(fx - 1, 1, fz), Blocks.STONE);
      helper.setBlock(new BlockPos(fx - 1, 2, fz), Blocks.STONE);
      helper.setBlock(new BlockPos(fx, 1, fz + 1), Blocks.STONE);
      helper.setBlock(new BlockPos(fx, 2, fz + 1), Blocks.STONE);
      helper.setBlock(new BlockPos(fx, 1, fz - 1), Blocks.STONE);
      helper.setBlock(new BlockPos(fx, 2, fz - 1), Blocks.STONE);
      helper.setBlock(new BlockPos(fx, 3, fz), Blocks.STONE);
      BlockPos from = helper.absolutePos(new BlockPos(fx, 1, fz));
      BlockPos to = helper.absolutePos(new BlockPos(13, 1, 8));

      try (WaypointTraversalTester tester = new WaypointTraversalTester(helper.getLevel())) {
         WaypointTraversalTester.Result r = tester.findPath(from, to);
         if (r.reachable()) {
            StringBuilder nodes = new StringBuilder();
            int max = Math.min(r.nodes().size(), 12);

            for (int i = 0; i < max; i++) {
               nodes.append(r.nodes().get(i).toShortString()).append(" → ");
            }

            if (r.nodes().size() > max) {
               nodes.append("…(+").append(r.nodes().size() - max).append(")");
            }

            helper.fail(
               "Sealed-cage pathfind reported reachable — tester accepts impossible edges. from="
                  + from.toShortString()
                  + " to="
                  + to.toShortString()
                  + " nodes("
                  + r.nodes().size()
                  + "): "
                  + nodes
            );
            return;
         }
      }

      helper.succeed();
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
