package org.millenaire.test.terrain;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.building.ClearMargins;
import org.millenaire.building.PlacementStep;
import org.millenaire.world.BuildingPlacer;
import org.millenaire.world.TerrainPreparer;
import org.slf4j.Logger;

public final class TerrainParityTest {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int BLOCK_UPDATE_FLAGS = 3;
   private static final int MARGIN = 5;

   private TerrainParityTest() {
   }

   public static TerrainParityTest.ParityResult runParity(GameTestHelper helper, TestTerrain terrain, TestBuilding building, Rotation rotation) {
      ServerLevel level = helper.getLevel();
      int margin = 5;
      int effWidth = TerrainPreparer.effectiveWidth(building.width(), building.depth(), rotation);
      int effDepth = TerrainPreparer.effectiveDepth(building.width(), building.depth(), rotation);
      int offsetX = (33 - effWidth) / 2;
      int offsetZ = (33 - effDepth) / 2;
      BlockPos origin = helper.absolutePos(new BlockPos(offsetX, 0, offsetZ)).atY(4);
      terrain.build(level, origin, effWidth, effDepth, margin);
      BlockPos effOrigin = TerrainPreparer.effectiveOrigin(origin, building.width(), building.depth(), rotation);
      int scanMargin = margin + 2;
      int scanStartX = effOrigin.getX() - scanMargin;
      int scanEndX = effOrigin.getX() + effWidth + scanMargin;
      int scanStartZ = effOrigin.getZ() - scanMargin;
      int scanEndZ = effOrigin.getZ() + effDepth + scanMargin;
      int scanMinY = -11;
      int scanMaxY = 4 + building.height() + 55;
      Map<BlockPos, BlockState> snapshot = captureSnapshot(level, scanStartX, scanEndX, scanStartZ, scanEndZ, scanMinY, scanMaxY);
      int baseY = TerrainPreparer.clearAndFlatten(
         level, origin, building.width(), building.height(), building.depth(), rotation, building.groundLevel(), ClearMargins.defaults()
      );
      Map<BlockPos, BlockState> stateA = captureSnapshot(level, scanStartX, scanEndX, scanStartZ, scanEndZ, scanMinY, scanMaxY);
      restoreSnapshot(level, snapshot);
      List<PlacementStep> steps = BuildingPlacer.compileTerrainPrepSteps(
         level, origin, building.width(), building.height(), building.depth(), baseY, origin, rotation, building.groundLevel(), ClearMargins.defaults()
      );

      for (PlacementStep step : steps) {
         BlockPos absPos = new BlockPos(
            step.relativePos().getX() + origin.getX(), step.relativePos().getY() + origin.getY(), step.relativePos().getZ() + origin.getZ()
         );
         level.setBlock(absPos, step.blockState(), 3);
      }

      Map<BlockPos, BlockState> stateB = captureSnapshot(level, scanStartX, scanEndX, scanStartZ, scanEndZ, scanMinY, scanMaxY);
      List<TerrainParityTest.Mismatch> mismatches = new ArrayList<>();
      int fixBlockBelowDiffs = 0;

      for (Entry<BlockPos, BlockState> entry : stateA.entrySet()) {
         BlockPos pos = entry.getKey();
         BlockState blockA = entry.getValue();
         BlockState blockB = stateB.getOrDefault(pos, Blocks.AIR.defaultBlockState());
         if (!blockA.equals(blockB)) {
            if (isFixBlockBelowDivergence(blockA, blockB)) {
               fixBlockBelowDiffs++;
            } else {
               mismatches.add(new TerrainParityTest.Mismatch(pos, blockA, blockB));
            }
         }
      }

      if (mismatches.isEmpty()) {
         LOGGER.info(
            "[PARITY OK] {} on {} rotation={} — {} fixBlockBelow ignored, {} steps applied",
            new Object[]{building.name(), terrain.name(), rotation, fixBlockBelowDiffs, steps.size()}
         );
      } else {
         LOGGER.error(
            "[PARITY FAIL] {} on {} rotation={} — {} divergences, {} fixBlockBelow ignored",
            new Object[]{building.name(), terrain.name(), rotation, mismatches.size(), fixBlockBelowDiffs}
         );
         int limit = Math.min(mismatches.size(), 20);

         for (int i = 0; i < limit; i++) {
            LOGGER.error("{}", mismatches.get(i));
         }

         if (mismatches.size() > 20) {
            LOGGER.error("  ... and {} more divergences", mismatches.size() - 20);
         }
      }

      return new TerrainParityTest.ParityResult(mismatches, fixBlockBelowDiffs);
   }

   private static boolean isFixBlockBelowDivergence(BlockState stateA, BlockState stateB) {
      return stateA.is(Blocks.GRASS_BLOCK) && stateB.is(Blocks.DIRT);
   }

   private static Map<BlockPos, BlockState> captureSnapshot(ServerLevel level, int startX, int endX, int startZ, int endZ, int minY, int maxY) {
      Map<BlockPos, BlockState> snapshot = new HashMap<>();

      for (int x = startX; x < endX; x++) {
         for (int z = startZ; z < endZ; z++) {
            for (int y = minY; y <= maxY; y++) {
               BlockPos pos = new BlockPos(x, y, z);
               BlockState state = level.getBlockState(pos);
               snapshot.put(pos, state);
            }
         }
      }

      return snapshot;
   }

   private static void restoreSnapshot(ServerLevel level, Map<BlockPos, BlockState> snapshot) {
      for (Entry<BlockPos, BlockState> entry : snapshot.entrySet()) {
         level.setBlock(entry.getKey(), entry.getValue(), 3);
      }
   }

   public static void runAndReport(GameTestHelper helper, TestTerrain terrain, TestBuilding building, Rotation rotation) {
      TerrainParityTest.ParityResult result = runParity(helper, terrain, building, rotation);
      if (!result.passed()) {
         helper.fail(
            "Terrain parity failed: "
               + result.mismatches().size()
               + " divergences on "
               + building.name()
               + "/"
               + terrain.name()
               + " rotation="
               + rotation
               + " (see server log for details)"
         );
      } else {
         helper.succeed();
      }
   }

   public record Mismatch(BlockPos pos, BlockState clearAndFlattenState, BlockState compileStepsState) {
      public String toString() {
         return String.format("  pos=%s : clearAndFlatten=%s, compileSteps=%s", this.pos, this.clearAndFlattenState, this.compileStepsState);
      }
   }

   public record ParityResult(List<TerrainParityTest.Mismatch> mismatches, int fixBlockBelowDiffs) {
      public boolean passed() {
         return this.mismatches.isEmpty();
      }
   }
}
