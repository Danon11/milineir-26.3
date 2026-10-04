package org.millenaire.test;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.millenaire.block.ModBlocks;
import org.millenaire.block.mock.FacingMarkerType;
import org.millenaire.block.mock.MockFacingMarkerBlock;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.SpecialPoint;
import org.millenaire.culture.ModCultures;
import org.millenaire.world.BuildingPlacer;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class MockFacingMarkerGameTests {
   private static void prepareFlatGround(GameTestHelper helper, int size) {
      for (int x = 0; x < size; x++) {
         for (int z = 0; z < size; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
         }
      }
   }

   private static List<MockFacingMarkerGameTests.FurnaceInfo> findFurnaces(GameTestHelper helper, BlockPos from, BlockPos to) {
      List<MockFacingMarkerGameTests.FurnaceInfo> furnaces = new ArrayList<>();
      ServerLevel level = helper.getLevel();

      for (int x = from.getX(); x <= to.getX(); x++) {
         for (int y = from.getY(); y <= to.getY(); y++) {
            for (int z = from.getZ(); z <= to.getZ(); z++) {
               BlockPos pos = new BlockPos(x, y, z);
               BlockEntity be = level.getBlockEntity(pos);
               if (be instanceof AbstractFurnaceBlockEntity) {
                  BlockState state = level.getBlockState(pos);
                  Direction facing = (Direction)state.getValue(FurnaceBlock.FACING);
                  furnaces.add(new MockFacingMarkerGameTests.FurnaceInfo(pos, facing));
               }
            }
         }
      }

      return furnaces;
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testFacingMarker_furnaceReplacementPreservesFacing(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();

      for (Direction dir : Plane.HORIZONTAL) {
         BlockPos pos = helper.absolutePos(new BlockPos(2 + dir.ordinal() * 2, 1, 2));
         BlockState mockState = (BlockState)((BlockState)((BlockState)((MockFacingMarkerBlock)ModBlocks.MOCK_FACING_MARKER.get())
                  .defaultBlockState()
                  .setValue(MockFacingMarkerBlock.TYPE, FacingMarkerType.FURNACE))
               .setValue(MockFacingMarkerBlock.FACING, dir))
            .setValue(MockFacingMarkerBlock.GUESS, false);
         BlockState replacement = ((MockFacingMarkerBlock)ModBlocks.MOCK_FACING_MARKER.get()).getReplacementState(mockState);
         if (replacement == null) {
            helper.fail("FURNACE replacement returned null for facing " + dir);
            return;
         }

         level.setBlock(pos, replacement, 3);
         BlockState placed = level.getBlockState(pos);
         if (placed.getBlock() != Blocks.FURNACE) {
            helper.fail("Expected furnace block, got " + placed.getBlock());
            return;
         }

         if (placed.getValue(FurnaceBlock.FACING) != dir) {
            helper.fail("Furnace facing mismatch: expected " + dir + ", got " + placed.getValue(FurnaceBlock.FACING));
            return;
         }
      }

      helper.succeed();
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testFacingMarker_signPosReplacementIsAir(GameTestHelper helper) {
      BlockState mockState = (BlockState)((BlockState)((BlockState)((MockFacingMarkerBlock)ModBlocks.MOCK_FACING_MARKER.get())
               .defaultBlockState()
               .setValue(MockFacingMarkerBlock.TYPE, FacingMarkerType.SIGN_POS))
            .setValue(MockFacingMarkerBlock.FACING, Direction.EAST))
         .setValue(MockFacingMarkerBlock.GUESS, false);
      BlockState replacement = ((MockFacingMarkerBlock)ModBlocks.MOCK_FACING_MARKER.get()).getReplacementState(mockState);
      if (replacement != null) {
         helper.fail("SIGN_POS replacement should be null (air), got " + replacement);
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testFacingMarker_toSpecialPointPreservesFacing(GameTestHelper helper) {
      BlockPos testPos = new BlockPos(5, 1, 5);

      for (Direction dir : Plane.HORIZONTAL) {
         BlockState mockState = (BlockState)((BlockState)((BlockState)((MockFacingMarkerBlock)ModBlocks.MOCK_FACING_MARKER.get())
                  .defaultBlockState()
                  .setValue(MockFacingMarkerBlock.TYPE, FacingMarkerType.FURNACE))
               .setValue(MockFacingMarkerBlock.FACING, dir))
            .setValue(MockFacingMarkerBlock.GUESS, false);
         SpecialPoint sp = ((MockFacingMarkerBlock)ModBlocks.MOCK_FACING_MARKER.get()).toSpecialPoint(mockState, testPos);
         if (!sp.isType("furnace")) {
            helper.fail("Expected type FURNACE, got " + sp.type());
            return;
         }

         if (!dir.getSerializedName().equals(sp.orientation())) {
            helper.fail("Facing mismatch in SpecialPoint: expected " + dir.getSerializedName() + ", got " + sp.orientation());
            return;
         }
      }

      helper.succeed();
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testFacingMarker_rotationTransformsFacing(GameTestHelper helper) {
      BlockState base = (BlockState)((BlockState)((MockFacingMarkerBlock)ModBlocks.MOCK_FACING_MARKER.get())
            .defaultBlockState()
            .setValue(MockFacingMarkerBlock.TYPE, FacingMarkerType.FURNACE))
         .setValue(MockFacingMarkerBlock.FACING, Direction.NORTH);
      BlockState rotated90 = base.rotate(Rotation.CLOCKWISE_90);
      if (rotated90.getValue(MockFacingMarkerBlock.FACING) != Direction.EAST) {
         helper.fail("CW90: expected EAST, got " + rotated90.getValue(MockFacingMarkerBlock.FACING));
      } else {
         BlockState rotated180 = base.rotate(Rotation.CLOCKWISE_180);
         if (rotated180.getValue(MockFacingMarkerBlock.FACING) != Direction.SOUTH) {
            helper.fail("CW180: expected SOUTH, got " + rotated180.getValue(MockFacingMarkerBlock.FACING));
         } else {
            BlockState rotatedCCW = base.rotate(Rotation.COUNTERCLOCKWISE_90);
            if (rotatedCCW.getValue(MockFacingMarkerBlock.FACING) != Direction.WEST) {
               helper.fail("CCW90: expected WEST, got " + rotatedCCW.getValue(MockFacingMarkerBlock.FACING));
            } else {
               helper.succeed();
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testNormanForge_hasFurnacesAfterPlacement(GameTestHelper helper) {
      prepareFlatGround(helper, 20);
      ResourceLocation planId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/forge_a_0");
      BuildingPlan plan = ModCultures.getBuildingPlan(planId);
      if (plan == null) {
         helper.fail("Plan 'norman/forge_a_0' not loaded");
      } else {
         BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
         boolean placed = BuildingPlacer.placeInstantly(helper.getLevel(), plan, origin, Rotation.NONE);
         if (!placed) {
            helper.fail("placeInstantly failed");
         } else {
            BlockPos scanFrom = helper.absolutePos(new BlockPos(0, 0, 0));
            BlockPos scanTo = helper.absolutePos(new BlockPos(19, 15, 19));
            List<MockFacingMarkerGameTests.FurnaceInfo> furnaces = findFurnaces(helper, scanFrom, scanTo);
            if (furnaces.isEmpty()) {
               helper.fail("No furnaces found in norman forge after placement");
            } else {
               for (MockFacingMarkerGameTests.FurnaceInfo f : furnaces) {
                  BlockEntity be = helper.getLevel().getBlockEntity(f.pos());
                  if (!(be instanceof AbstractFurnaceBlockEntity)) {
                     helper.fail("Block at " + f.pos() + " is not a real furnace block entity");
                     return;
                  }
               }

               helper.succeed();
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testIndianBrickkiln_level1HasFurnaces(GameTestHelper helper) {
      prepareFlatGround(helper, 20);
      ResourceLocation plan0Id = ResourceLocation.fromNamespaceAndPath("millenaire", "indian/brickkiln_a_0");
      BuildingPlan plan0 = ModCultures.getBuildingPlan(plan0Id);
      if (plan0 == null) {
         helper.fail("Plan 'indian/brickkiln_a_0' not loaded");
      } else {
         BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
         BuildingPlacer.placeInstantly(helper.getLevel(), plan0, origin, Rotation.NONE);
         ResourceLocation plan1Id = ResourceLocation.fromNamespaceAndPath("millenaire", "indian/brickkiln_a_1");
         BuildingPlan plan1 = ModCultures.getBuildingPlan(plan1Id);
         if (plan1 == null) {
            helper.fail("Plan 'indian/brickkiln_a_1' not loaded");
         } else {
            BuildingPlacer.placeUpgradeInstantly(helper.getLevel(), plan1, origin, Rotation.NONE);
            BlockPos scanFrom = helper.absolutePos(new BlockPos(0, 0, 0));
            BlockPos scanTo = helper.absolutePos(new BlockPos(19, 15, 19));
            List<MockFacingMarkerGameTests.FurnaceInfo> furnaces = findFurnaces(helper, scanFrom, scanTo);
            if (furnaces.isEmpty()) {
               helper.fail("No furnaces found in brickkiln after level 1 upgrade");
            } else {
               helper.succeed();
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testNormanForge_specialPointsFurnaceExtracted(GameTestHelper helper) {
      ResourceLocation planId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/forge_a_0");
      BuildingPlan plan = ModCultures.getBuildingPlan(planId);
      if (plan == null) {
         helper.fail("Plan not loaded");
      } else {
         List<SpecialPoint> specialPoints = plan.specialPoints();
         long furnaceCount = specialPoints.stream().filter(spx -> spx.isType("furnace")).count();
         if (furnaceCount == 0L) {
            helper.fail(
               "No FURNACE special points found in norman/forge_a_0. Total special points: "
                  + specialPoints.size()
                  + ". Types: "
                  + specialPoints.stream().map(SpecialPoint::type).distinct().toList()
            );
         } else {
            for (SpecialPoint sp : specialPoints) {
               if (sp.isType("furnace") && (sp.orientation() == null || sp.orientation().isEmpty())) {
                  helper.fail("FURNACE special point at " + sp.pos() + " has no orientation");
                  return;
               }
            }

            helper.succeed();
         }
      }
   }

   private record FurnaceInfo(BlockPos pos, Direction facing) {
   }
}
