package org.millenaire.test.terrain;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import org.millenaire.building.ClearMargins;
import org.millenaire.world.TerrainPreparer;
import org.slf4j.Logger;

public final class TerrainTestBench {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MARGIN = 5;

   private TerrainTestBench() {
   }

   public static TerrainTestBench.TestContext prepare(GameTestHelper helper, TestTerrain terrain, TestBuilding building) {
      ServerLevel level = helper.getLevel();
      int offsetX = (33 - building.width()) / 2;
      int offsetZ = (33 - building.depth()) / 2;
      BlockPos origin = helper.absolutePos(new BlockPos(offsetX, 0, offsetZ)).atY(4);
      terrain.build(level, origin, building.width(), building.depth(), 5);
      TerrainSnapshot before = TerrainSnapshot.capture(level, origin, building.width(), building.depth(), 5);
      int baseY = TerrainPreparer.clearAndFlatten(
         level, origin, building.width(), building.height(), building.depth(), Rotation.NONE, building.groundLevel(), ClearMargins.defaults()
      );
      return new TerrainTestBench.TestContext(level, origin, building, terrain, baseY, before);
   }

   public static List<Violation> verify(TerrainTestBench.TestContext ctx) {
      List<Violation> violations = TerrainInvariantChecker.check(
         ctx.level,
         ctx.origin,
         ctx.building.width(),
         ctx.building.height(),
         ctx.building.depth(),
         ctx.baseY,
         ctx.terrain,
         ctx.before,
         ctx.building.groundLevel()
      );
      if (!violations.isEmpty()) {
         String crossCenter = TerrainAsciiRenderer.renderCrossSection(
            ctx.level, ctx.origin, ctx.building.width(), ctx.building.depth(), ctx.baseY, 5, Direction.NORTH, ctx.building.depth() / 2
         );
         String crossCorner = TerrainAsciiRenderer.renderCrossSection(
            ctx.level, ctx.origin, ctx.building.width(), ctx.building.depth(), ctx.baseY, 5, Direction.NORTH, 0
         );
         String crossEast = TerrainAsciiRenderer.renderCrossSection(
            ctx.level, ctx.origin, ctx.building.width(), ctx.building.depth(), ctx.baseY, 5, Direction.EAST, ctx.building.width() / 2
         );
         String heightmap = TerrainAsciiRenderer.renderSurfaceHeightmap(ctx.level, ctx.origin, ctx.building.width(), ctx.building.depth(), ctx.baseY, 5);
         String report = violations.stream().map(Violation::toString).collect(Collectors.joining("\n  "));
         LOGGER.error(
            "=== TERRAIN TEST FAILED: {} on {} ===\nbaseY={}, footprint={}x{}, {} violation(s):\n  {}\n\n--- Cross-section center ---\n{}\n--- Cross-section corner ---\n{}\n--- Cross-section east (along X) ---\n{}\n--- Surface heightmap ---\n{}",
            new Object[]{
               ctx.building.name(),
               ctx.terrain.name(),
               ctx.baseY,
               ctx.building.width(),
               ctx.building.depth(),
               violations.size(),
               report,
               crossCenter,
               crossCorner,
               crossEast,
               heightmap
            }
         );
      } else {
         LOGGER.info("[TERRAIN OK] {} on {} — baseY={}", new Object[]{ctx.building.name(), ctx.terrain.name(), ctx.baseY});
      }

      return violations;
   }

   private static void verifyAndReport(GameTestHelper helper, TerrainTestBench.TestContext ctx) {
      List<Violation> violations = verify(ctx);
      if (!violations.isEmpty()) {
         helper.fail("Terrain violations: " + violations.size() + " on " + ctx.building.name() + "/" + ctx.terrain.name() + " (see server log for diagnostic)");
      } else {
         helper.succeed();
      }
   }

   public static void runTest(GameTestHelper helper, TestTerrain terrain, TestBuilding building) {
      TerrainTestBench.TestContext ctx = prepare(helper, terrain, building);
      verifyAndReport(helper, ctx);
   }

   public record TestContext(ServerLevel level, BlockPos origin, TestBuilding building, TestTerrain terrain, int baseY, TerrainSnapshot before) {
   }
}
