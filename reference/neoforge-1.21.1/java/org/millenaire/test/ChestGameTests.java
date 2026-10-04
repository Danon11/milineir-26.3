package org.millenaire.test;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.millenaire.block.LockedChestBlock;
import org.millenaire.block.ModBlocks;
import org.millenaire.building.BuildingPlan;
import org.millenaire.culture.ModCultures;
import org.millenaire.world.BuildingPlacer;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class ChestGameTests {
   private static void prepareFlatGround(GameTestHelper helper, int size) {
      for (int x = 0; x < size; x++) {
         for (int z = 0; z < size; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
         }
      }
   }

   private static List<ChestGameTests.ChestInfo> findChests(GameTestHelper helper, BlockPos from, BlockPos to) {
      List<ChestGameTests.ChestInfo> chests = new ArrayList<>();
      ServerLevel level = helper.getLevel();

      for (int x = from.getX(); x <= to.getX(); x++) {
         for (int y = from.getY(); y <= to.getY(); y++) {
            for (int z = from.getZ(); z <= to.getZ(); z++) {
               BlockPos pos = new BlockPos(x, y, z);
               BlockState state = level.getBlockState(pos);
               if (state.getBlock() instanceof ChestBlock) {
                  chests.add(
                     new ChestGameTests.ChestInfo(
                        pos,
                        (Direction)state.getValue(ChestBlock.FACING),
                        (ChestType)state.getValue(ChestBlock.TYPE),
                        state.getBlock().getClass().getSimpleName()
                     )
                  );
               }
            }
         }
      }

      return chests;
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testFarmChests_doubleChestFormed(GameTestHelper helper) {
      prepareFlatGround(helper, 25);
      ResourceLocation planId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/farm_a_0");
      BuildingPlan plan = ModCultures.getBuildingPlan(planId);
      if (plan == null) {
         helper.fail("Plan 'norman/farm_a_0' not loaded");
      } else {
         BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
         boolean placed = BuildingPlacer.placeInstantly(helper.getLevel(), plan, origin, Rotation.NONE);
         if (!placed) {
            helper.fail("placeInstantly failed");
         } else {
            BlockPos scanFrom = helper.absolutePos(new BlockPos(0, 0, 0));
            BlockPos scanTo = helper.absolutePos(new BlockPos(24, 15, 24));
            List<ChestGameTests.ChestInfo> chests = findChests(helper, scanFrom, scanTo);
            if (chests.isEmpty()) {
               helper.fail("No chest found in the farm");
            } else {
               long leftCount = chests.stream().filter(cx -> cx.type() == ChestType.LEFT).count();
               long rightCount = chests.stream().filter(cx -> cx.type() == ChestType.RIGHT).count();
               if (leftCount != 0L && rightCount != 0L) {
                  for (ChestGameTests.ChestInfo c : chests) {
                     if (c.type() == ChestType.LEFT || c.type() == ChestType.RIGHT) {
                        if (c.type() == ChestType.LEFT) {
                           c.facing().getClockWise();
                        } else {
                           c.facing().getCounterClockWise();
                        }
                     }
                  }

                  helper.succeed();
               } else {
                  StringBuilder sb = new StringBuilder("No double chest found. Chests:");

                  for (ChestGameTests.ChestInfo c : chests) {
                     sb.append("\n  ")
                        .append(c.pos())
                        .append(" facing=")
                        .append(c.facing())
                        .append(" type=")
                        .append(c.type())
                        .append(" class=")
                        .append(c.blockClass());
                  }

                  helper.fail(sb.toString());
               }
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testManualLockedChests_allFacings(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos p1a = helper.absolutePos(new BlockPos(2, 1, 2));
      BlockPos p1b = helper.absolutePos(new BlockPos(3, 1, 2));
      placeLockedChestPair(level, p1a, p1b, Direction.SOUTH);
      BlockPos p2a = helper.absolutePos(new BlockPos(6, 1, 2));
      BlockPos p2b = helper.absolutePos(new BlockPos(7, 1, 2));
      placeLockedChestPair(level, p2a, p2b, Direction.NORTH);
      BlockPos p3a = helper.absolutePos(new BlockPos(10, 1, 2));
      BlockPos p3b = helper.absolutePos(new BlockPos(10, 1, 3));
      placeLockedChestPair(level, p3a, p3b, Direction.EAST);
      BlockPos p4a = helper.absolutePos(new BlockPos(14, 1, 2));
      BlockPos p4b = helper.absolutePos(new BlockPos(14, 1, 3));
      placeLockedChestPair(level, p4a, p4b, Direction.WEST);

      for (ChestGameTests.DummyPos step : List.of(
         dummyStep(p1a), dummyStep(p1b), dummyStep(p2a), dummyStep(p2b), dummyStep(p3a), dummyStep(p3b), dummyStep(p4a), dummyStep(p4b)
      )) {
         BlockState state = level.getBlockState(step.pos);
         if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            helper.fail("Chest at " + step.pos + " is not SINGLE before fixDoubleChests");
            return;
         }
      }

      helper.succeed();
   }

   private static void placeLockedChestPair(ServerLevel level, BlockPos a, BlockPos b, Direction facing) {
      BlockState chest = (BlockState)((LockedChestBlock)ModBlocks.LOCKED_CHEST.get()).defaultBlockState().setValue(ChestBlock.FACING, facing);
      level.setBlock(a, chest, 2);
      level.setBlock(b, chest, 2);
   }

   private static ChestGameTests.DummyPos dummyStep(BlockPos pos) {
      return new ChestGameTests.DummyPos(pos);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testWellChests_rotated(GameTestHelper helper) {
      prepareFlatGround(helper, 15);
      ResourceLocation planId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/well_a_0");
      BuildingPlan plan = ModCultures.getBuildingPlan(planId);
      if (plan == null) {
         helper.fail("Plan not loaded");
      } else {
         for (Rotation rot : Rotation.values()) {
            BlockPos origin = helper.absolutePos(new BlockPos(5, 1, 5));

            for (int x = 0; x < 15; x++) {
               for (int y = 1; y < 10; y++) {
                  for (int z = 0; z < 15; z++) {
                     helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                  }
               }
            }

            prepareFlatGround(helper, 15);
            BuildingPlacer.placeInstantly(helper.getLevel(), plan, origin, rot);
         }

         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testForgeChests_separateRemainSingle(GameTestHelper helper) {
      prepareFlatGround(helper, 20);
      ResourceLocation planId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/forge_a_0");
      BuildingPlan plan = ModCultures.getBuildingPlan(planId);
      if (plan == null) {
         helper.fail("Plan not loaded");
      } else {
         BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
         BuildingPlacer.placeInstantly(helper.getLevel(), plan, origin, Rotation.NONE);
         BlockPos scanFrom = helper.absolutePos(new BlockPos(0, 0, 0));
         BlockPos scanTo = helper.absolutePos(new BlockPos(19, 15, 19));
         List<ChestGameTests.ChestInfo> chests = findChests(helper, scanFrom, scanTo);
         if (chests.isEmpty()) {
            helper.fail("No chest found in the forge");
         } else {
            for (ChestGameTests.ChestInfo c : chests) {
               if (c.type() != ChestType.SINGLE) {
                  Direction facing = c.facing();
                  BlockPos partnerPos;
                  ChestType expectedPartner;
                  if (c.type() == ChestType.LEFT) {
                     partnerPos = c.pos().relative(facing.getClockWise());
                     expectedPartner = ChestType.RIGHT;
                  } else {
                     partnerPos = c.pos().relative(facing.getCounterClockWise());
                     expectedPartner = ChestType.LEFT;
                  }

                  BlockState partnerState = helper.getLevel().getBlockState(partnerPos);
                  if (!(partnerState.getBlock() instanceof ChestBlock)) {
                     helper.fail("Paired chest at " + c.pos() + " (" + c.type() + ") has no chest partner at " + partnerPos);
                     return;
                  }

                  ChestType partnerType = (ChestType)partnerState.getValue(ChestBlock.TYPE);
                  if (partnerType != expectedPartner) {
                     helper.fail(
                        "Paired chest at "
                           + c.pos()
                           + " ("
                           + c.type()
                           + ") expected partner "
                           + expectedPartner
                           + " at "
                           + partnerPos
                           + " but got "
                           + partnerType
                     );
                     return;
                  }
               }
            }

            helper.succeed();
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testChestFacing_preservedFromTemplate(GameTestHelper helper) {
      prepareFlatGround(helper, 25);
      ResourceLocation planId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/farm_a_0");
      BuildingPlan plan = ModCultures.getBuildingPlan(planId);
      if (plan == null) {
         helper.fail("Plan not loaded");
      } else {
         BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
         BuildingPlacer.placeInstantly(helper.getLevel(), plan, origin, Rotation.NONE);
         BlockPos scanFrom = helper.absolutePos(new BlockPos(0, 0, 0));
         BlockPos scanTo = helper.absolutePos(new BlockPos(24, 15, 24));
         List<ChestGameTests.ChestInfo> chests = findChests(helper, scanFrom, scanTo);
         List<ChestGameTests.ChestInfo> paired = chests.stream().filter(cx -> cx.type() == ChestType.LEFT || cx.type() == ChestType.RIGHT).toList();
         if (paired.size() < 2) {
            helper.fail("Not enough paired chests to verify facing");
         } else {
            Direction pairFacing = paired.get(0).facing();

            for (ChestGameTests.ChestInfo c : paired) {
               if (c.facing() != pairFacing) {
                  boolean hasPartner = paired.stream()
                     .filter(other -> other != c)
                     .anyMatch(other -> other.facing() == c.facing() && isAdjacent(c.pos(), other.pos()));
                  if (!hasPartner) {
                     helper.fail("Chest at " + c.pos() + " facing=" + c.facing() + " has no partner with the same facing");
                     return;
                  }
               }
            }

            helper.succeed();
         }
      }
   }

   private static boolean isAdjacent(BlockPos a, BlockPos b) {
      int dx = Math.abs(a.getX() - b.getX());
      int dy = Math.abs(a.getY() - b.getY());
      int dz = Math.abs(a.getZ() - b.getZ());
      return dx + dy + dz == 1;
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testFarmUpgrade_chestsStillMerged(GameTestHelper helper) {
      prepareFlatGround(helper, 25);
      ResourceLocation plan0Id = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/farm_a_0");
      BuildingPlan plan0 = ModCultures.getBuildingPlan(plan0Id);
      if (plan0 == null) {
         helper.fail("Plan farm_a_0 not loaded");
      } else {
         BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
         BuildingPlacer.placeInstantly(helper.getLevel(), plan0, origin, Rotation.NONE);
         ResourceLocation plan1Id = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/farm_a_1");
         BuildingPlan plan1 = ModCultures.getBuildingPlan(plan1Id);
         if (plan1 == null) {
            helper.fail("Plan farm_a_1 not loaded");
         } else {
            BuildingPlacer.placeUpgradeInstantly(helper.getLevel(), plan1, origin, Rotation.NONE);
            BlockPos scanFrom = helper.absolutePos(new BlockPos(0, 0, 0));
            BlockPos scanTo = helper.absolutePos(new BlockPos(24, 15, 24));
            List<ChestGameTests.ChestInfo> chests = findChests(helper, scanFrom, scanTo);
            if (chests.isEmpty()) {
               helper.fail("No chests found after upgrade");
            } else {
               long leftCount = chests.stream().filter(cx -> cx.type() == ChestType.LEFT).count();
               long rightCount = chests.stream().filter(cx -> cx.type() == ChestType.RIGHT).count();
               if (leftCount != 0L && rightCount != 0L) {
                  helper.succeed();
               } else {
                  StringBuilder sb = new StringBuilder("After upgrade: no double chest. Chests:");

                  for (ChestGameTests.ChestInfo c : chests) {
                     sb.append("\n  ").append(c.pos()).append(" type=").append(c.type());
                  }

                  helper.fail(sb.toString());
               }
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testLockedChests_sameClassMerge(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos pos1 = helper.absolutePos(new BlockPos(5, 1, 5));
      BlockPos pos2 = helper.absolutePos(new BlockPos(6, 1, 5));
      BlockState chest = (BlockState)((LockedChestBlock)ModBlocks.LOCKED_CHEST.get()).defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
      level.setBlock(pos1, chest, 2);
      level.setBlock(pos2, chest, 2);
      BlockState placed1 = level.getBlockState(pos1);
      BlockState placed2 = level.getBlockState(pos2);
      if (!(placed1.getBlock() instanceof ChestBlock)) {
         helper.fail("LockedChest is not a ChestBlock");
      } else if (placed1.getBlock().getClass() != placed2.getBlock().getClass()) {
         helper.fail("Both locked chests do not have the same class");
      } else if (placed1.getValue(ChestBlock.FACING) != placed2.getValue(ChestBlock.FACING)) {
         helper.fail("Both locked chests do not have the same facing");
      } else {
         level.setBlock(pos1, (BlockState)placed1.setValue(ChestBlock.TYPE, ChestType.LEFT), 2);
         level.setBlock(pos2, (BlockState)placed2.setValue(ChestBlock.TYPE, ChestType.RIGHT), 2);
         BlockState after1 = level.getBlockState(pos1);
         BlockState after2 = level.getBlockState(pos2);
         if (after1.getValue(ChestBlock.TYPE) != ChestType.LEFT) {
            helper.fail("Locked chest 1 did not keep LEFT: " + after1.getValue(ChestBlock.TYPE));
         } else if (after2.getValue(ChestBlock.TYPE) != ChestType.RIGHT) {
            helper.fail("Locked chest 2 did not keep RIGHT: " + after2.getValue(ChestBlock.TYPE));
         } else {
            helper.succeed();
         }
      }
   }

   record ChestInfo(BlockPos pos, Direction facing, ChestType type, String blockClass) {
   }

   private record DummyPos(BlockPos pos) {
   }
}
