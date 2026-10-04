package org.millenaire.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.millenaire.block.MillPathBlock;
import org.millenaire.block.MillPathSlabBlock;
import org.millenaire.block.ModBlocks;
import org.millenaire.block.PathTier;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.MillWalkNodeEvaluator;
import org.millenaire.entity.ModEntities;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class PathPreferenceGameTests {
   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void pathBlocks_haveTieredSpeedFactor(GameTestHelper helper) {
      assertSpeed(helper, (Block)ModBlocks.PATH_DIRT.get(), PathTier.RUSTIC);
      assertSpeed(helper, (Block)ModBlocks.PATH_DIRT_SLAB.get(), PathTier.RUSTIC);
      assertSpeed(helper, (Block)ModBlocks.PATH_GRAVEL.get(), PathTier.RUSTIC);
      assertSpeed(helper, (Block)ModBlocks.PATH_GRAVEL_SLAB.get(), PathTier.RUSTIC);
      assertSpeed(helper, (Block)ModBlocks.PATH_SNOW.get(), PathTier.RUSTIC);
      assertSpeed(helper, (Block)ModBlocks.PATH_SNOW_SLAB.get(), PathTier.RUSTIC);
      assertSpeed(helper, (Block)ModBlocks.PATH_SLABS.get(), PathTier.PAVED);
      assertSpeed(helper, (Block)ModBlocks.PATH_SLABS_SLAB.get(), PathTier.PAVED);
      assertSpeed(helper, (Block)ModBlocks.PATH_GRAVEL_SLABS.get(), PathTier.PAVED);
      assertSpeed(helper, (Block)ModBlocks.PATH_GRAVEL_SLABS_SLAB.get(), PathTier.PAVED);
      assertSpeed(helper, (Block)ModBlocks.PATH_SANDSTONE.get(), PathTier.STONE);
      assertSpeed(helper, (Block)ModBlocks.PATH_SANDSTONE_SLAB.get(), PathTier.STONE);
      assertSpeed(helper, (Block)ModBlocks.PATH_OCHRE_TILES.get(), PathTier.STONE);
      assertSpeed(helper, (Block)ModBlocks.PATH_OCHRE_TILES_SLAB.get(), PathTier.STONE);
      helper.succeed();
   }

   private static void assertSpeed(GameTestHelper helper, Block block, PathTier expected) {
      if (Math.abs(block.getSpeedFactor() - expected.speedFactor()) > 1.0E-6) {
         helper.fail(block + " speedFactor=" + block.getSpeedFactor() + " (expected " + expected + "=" + expected.speedFactor() + ")");
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 5L, timeoutTicks = 100)
   public static void villager_pathfindingMalusesConfiguredForPreference(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      MillVillager villager = (MillVillager)((EntityType)ModEntities.MILL_VILLAGER.get()).create(level);
      if (villager == null) {
         helper.fail("Failed to spawn MillVillager");
      } else {
         BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
         villager.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
         level.addFreshEntity(villager);
         float walkable = villager.getPathfindingMalus(PathType.WALKABLE);
         float cocoa = villager.getPathfindingMalus(PathType.COCOA);
         if (Math.abs(walkable - 1.2F) > 1.0E-6) {
            helper.fail("WALKABLE malus=" + walkable + " (expected 1.2)");
         }

         if (Math.abs(cocoa - 0.0F) > 1.0E-6) {
            helper.fail("COCOA malus=" + cocoa + " (expected 0.0 — path tiles must be free)");
         }

         villager.remove(RemovalReason.DISCARDED);
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 5L, timeoutTicks = 200)
   public static void villager_canPathOverPathTiles(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      paveGrassSlab(helper, 0, 0, 25, 5);
      pavePathRow(helper, 0, 2, 21);
      BlockPos start = helper.absolutePos(new BlockPos(0, 1, 2));
      BlockPos target = helper.absolutePos(new BlockPos(20, 1, 2));
      MillVillager villager = (MillVillager)((EntityType)ModEntities.MILL_VILLAGER.get()).create(level);
      if (villager == null) {
         helper.fail("Failed to spawn MillVillager");
      } else {
         villager.setPersistenceRequired();
         villager.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0.0F, 0.0F);
         villager.setOnGround(true);
         level.addFreshEntity(villager);
         Path path = villager.getNavigation().createPath(target, 0);
         if (path == null) {
            helper.fail("Navigation returned a null path — COCOA-typed nodes may be treated as blocked");
         } else {
            int pathTileNodes = 0;

            for (int i = 0; i < path.getNodeCount(); i++) {
               Node node = path.getNode(i);
               if (isPathTileAt(level, node.x, node.y, node.z) || isPathTileAt(level, node.x, node.y - 1, node.z)) {
                  pathTileNodes++;
               }
            }

            int required = 15;
            if (pathTileNodes < required) {
               helper.fail("Expected at least " + required + " path-tile nodes along the path row, got " + pathTileNodes + " of " + path.getNodeCount());
            } else {
               villager.remove(RemovalReason.DISCARDED);
               helper.succeed();
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 5L, timeoutTicks = 200)
   public static void villager_prefersParallelPathOverDirectGrass(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      paveGrassSlab(helper, 0, 0, 25, 7);
      pavePathRow(helper, 0, 3, 21);
      BlockPos start = helper.absolutePos(new BlockPos(0, 1, 0));
      BlockPos target = helper.absolutePos(new BlockPos(20, 1, 0));
      MillVillager villager = (MillVillager)((EntityType)ModEntities.MILL_VILLAGER.get()).create(level);
      if (villager == null) {
         helper.fail("Failed to spawn MillVillager");
      } else {
         villager.setPersistenceRequired();
         villager.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0.0F, 0.0F);
         villager.setOnGround(true);
         level.addFreshEntity(villager);
         Path path = villager.getNavigation().createPath(target, 0);
         if (path == null) {
            helper.fail("Navigation returned a null path");
         } else {
            int pathTileNodes = 0;
            int maxDetourZ = 0;

            for (int i = 0; i < path.getNodeCount(); i++) {
               Node node = path.getNode(i);
               if (isPathTileAt(level, node.x, node.y, node.z) || isPathTileAt(level, node.x, node.y - 1, node.z)) {
                  pathTileNodes++;
               }

               int relZ = node.z - helper.absolutePos(new BlockPos(0, 0, 0)).getZ();
               if (relZ > maxDetourZ) {
                  maxDetourZ = relZ;
               }
            }

            if (maxDetourZ < 3) {
               helper.fail(
                  "Villager stayed near z=0 instead of detouring south to the path row at z=3. maxDetourZ="
                     + maxDetourZ
                     + " pathTileNodes="
                     + pathTileNodes
                     + "/"
                     + path.getNodeCount()
                     + " — preference mechanism may be broken."
               );
            } else {
               int required = 10;
               if (pathTileNodes < required) {
                  helper.fail(
                     "Path dipped south (maxDetourZ="
                        + maxDetourZ
                        + ") but did not follow the path row. pathTileNodes="
                        + pathTileNodes
                        + " of "
                        + path.getNodeCount()
                        + " — expected at least "
                        + required
                  );
               } else {
                  villager.remove(RemovalReason.DISCARDED);
                  helper.succeed();
               }
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 5L, timeoutTicks = 100)
   public static void walkNodeEvaluator_reclassifiesPathTilesToCocoa(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      paveGrassSlab(helper, 0, 0, 8, 4);
      helper.setBlock(new BlockPos(2, 0, 0), (Block)ModBlocks.PATH_DIRT.get());
      helper.setBlock(
         new BlockPos(4, 0, 0), (BlockState)((SlabBlock)ModBlocks.PATH_DIRT_SLAB.get()).defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)
      );
      helper.setBlock(new BlockPos(6, 1, 0), (BlockState)((SlabBlock)ModBlocks.PATH_DIRT_SLAB.get()).defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
      MillVillager villager = (MillVillager)((EntityType)ModEntities.MILL_VILLAGER.get()).create(level);
      if (villager == null) {
         helper.fail("Failed to spawn MillVillager");
      } else {
         BlockPos spawn = helper.absolutePos(new BlockPos(0, 1, 0));
         villager.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
         level.addFreshEntity(villager);
         MillWalkNodeEvaluator eval = new MillWalkNodeEvaluator();
         PathfindingContext ctx = new PathfindingContext(level, villager);
         BlockPos plainFeet = helper.absolutePos(new BlockPos(0, 1, 0));
         assertPathType(helper, eval, ctx, plainFeet, PathType.WALKABLE, "plain grass feet");
         BlockPos pathBlockFeet = helper.absolutePos(new BlockPos(2, 0, 0));
         assertPathType(helper, eval, ctx, pathBlockFeet, PathType.COCOA, "MillPathBlock feet (here branch)");
         BlockPos slabBottomFeet = helper.absolutePos(new BlockPos(4, 0, 0));
         assertPathType(helper, eval, ctx, slabBottomFeet, PathType.COCOA, "MillPathSlabBlock BOTTOM feet (here branch)");
         BlockPos slabTopFeet = helper.absolutePos(new BlockPos(6, 1, 0));
         assertPathType(helper, eval, ctx, slabTopFeet, PathType.COCOA, "MillPathSlabBlock TOP feet (here branch)");
         villager.remove(RemovalReason.DISCARDED);
         helper.succeed();
      }
   }

   private static void assertPathType(GameTestHelper helper, MillWalkNodeEvaluator eval, PathfindingContext ctx, BlockPos pos, PathType expected, String label) {
      PathType actual = eval.getPathType(ctx, pos.getX(), pos.getY(), pos.getZ());
      if (actual != expected) {
         helper.fail(label + ": expected " + expected + " at " + pos.toShortString() + " but got " + actual);
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 5L, timeoutTicks = 200)
   public static void villager_tierGradientAppliedToCostMalus(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      paveGrassSlab(helper, 0, 0, 10, 3);
      helper.setBlock(new BlockPos(3, 0, 1), (Block)ModBlocks.PATH_DIRT.get());
      helper.setBlock(new BlockPos(5, 0, 1), (Block)ModBlocks.PATH_SLABS.get());
      helper.setBlock(new BlockPos(7, 0, 1), (Block)ModBlocks.PATH_SANDSTONE.get());
      BlockPos start = helper.absolutePos(new BlockPos(0, 1, 1));
      BlockPos target = helper.absolutePos(new BlockPos(9, 1, 1));
      MillVillager villager = (MillVillager)((EntityType)ModEntities.MILL_VILLAGER.get()).create(level);
      if (villager == null) {
         helper.fail("Failed to spawn MillVillager");
      } else {
         villager.setPersistenceRequired();
         villager.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0.0F, 0.0F);
         villager.setOnGround(true);
         level.addFreshEntity(villager);
         Path path = villager.getNavigation().createPath(target, 0);
         if (path == null) {
            helper.fail("Navigation returned a null path");
         } else {
            BlockPos rusticAbs = helper.absolutePos(new BlockPos(3, 0, 1));
            BlockPos pavedAbs = helper.absolutePos(new BlockPos(5, 0, 1));
            BlockPos stoneAbs = helper.absolutePos(new BlockPos(7, 0, 1));
            assertNodeMalus(helper, path, rusticAbs, PathTier.RUSTIC);
            assertNodeMalus(helper, path, pavedAbs, PathTier.PAVED);
            assertNodeMalus(helper, path, stoneAbs, PathTier.STONE);
            villager.remove(RemovalReason.DISCARDED);
            helper.succeed();
         }
      }
   }

   private static void assertNodeMalus(GameTestHelper helper, Path path, BlockPos pos, PathTier expected) {
      for (int i = 0; i < path.getNodeCount(); i++) {
         Node node = path.getNode(i);
         if (node.x == pos.getX() && node.z == pos.getZ() && (node.y == pos.getY() || node.y == pos.getY() - 1)) {
            if (Math.abs(node.costMalus - expected.preferenceMalus()) > 1.0E-6) {
               helper.fail(
                  "Node at " + pos.toShortString() + " (" + expected + "): costMalus=" + node.costMalus + " (expected " + expected.preferenceMalus() + ")"
               );
            }

            return;
         }
      }

      helper.fail("Path does not visit " + expected + " tile at " + pos.toShortString() + " — cannot verify tier malus");
   }

   private static boolean isPathTileAt(ServerLevel level, int x, int y, int z) {
      Block b = level.getBlockState(new BlockPos(x, y, z)).getBlock();
      return b instanceof MillPathBlock || b instanceof MillPathSlabBlock;
   }

   private static void paveGrassSlab(GameTestHelper helper, int x0, int z0, int x1, int z1) {
      for (int x = x0; x < x1; x++) {
         for (int z = z0; z < z1; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);

            for (int y = 1; y < 5; y++) {
               helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
         }
      }
   }

   private static void pavePathRow(GameTestHelper helper, int x0, int z, int x1) {
      for (int x = x0; x < x1; x++) {
         helper.setBlock(new BlockPos(x, 0, z), (Block)ModBlocks.PATH_DIRT.get());
      }
   }
}
