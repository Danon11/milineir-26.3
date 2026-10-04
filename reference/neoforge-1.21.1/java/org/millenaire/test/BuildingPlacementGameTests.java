package org.millenaire.test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.millenaire.block.LockedChestBlock;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.PlacementPhase;
import org.millenaire.building.PlacementStep;
import org.millenaire.building.SpecialPoint;
import org.millenaire.culture.ModCultures;
import org.millenaire.world.BuildingPlacer;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class BuildingPlacementGameTests {
   private static void prepareFlatGround(GameTestHelper helper, int size) {
      for (int x = 0; x < size; x++) {
         for (int z = 0; z < size; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testLumbermanHut_allChestsAreLocked(GameTestHelper helper) {
      prepareFlatGround(helper, 20);
      ResourceLocation planId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/lumbermanhut_a_0");
      BuildingPlan plan = ModCultures.getBuildingPlan(planId);
      if (plan == null) {
         helper.fail("Plan 'norman/lumbermanhut_a_0' not loaded");
      } else {
         BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
         boolean placed = BuildingPlacer.placeInstantly(helper.getLevel(), plan, origin, Rotation.NONE);
         if (!placed) {
            helper.fail("placeInstantly failed");
         } else {
            ServerLevel level = helper.getLevel();
            BlockPos scanFrom = helper.absolutePos(new BlockPos(0, 0, 0));
            BlockPos scanTo = helper.absolutePos(new BlockPos(19, 15, 19));
            int vanillaChestCount = 0;
            int lockedChestCount = 0;

            for (int x = scanFrom.getX(); x <= scanTo.getX(); x++) {
               for (int y = scanFrom.getY(); y <= scanTo.getY(); y++) {
                  for (int z = scanFrom.getZ(); z <= scanTo.getZ(); z++) {
                     BlockPos pos = new BlockPos(x, y, z);
                     BlockState state = level.getBlockState(pos);
                     Block block = state.getBlock();
                     if (block instanceof LockedChestBlock) {
                        lockedChestCount++;
                     } else if (block instanceof ChestBlock) {
                        vanillaChestCount++;
                     }
                  }
               }
            }

            if (lockedChestCount == 0 && vanillaChestCount == 0) {
               helper.fail("No chests found in the lumberman hut");
            } else if (vanillaChestCount > 0) {
               helper.fail(
                  "Found "
                     + vanillaChestCount
                     + " vanilla chest(es) instead of locked. Locked: "
                     + lockedChestCount
                     + ". The mock_chest MAIN should produce a LockedChestBlock."
               );
            } else {
               helper.succeed();
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testLumbermanHut_bedsHaveHeadAndFoot(GameTestHelper helper) {
      prepareFlatGround(helper, 20);
      ResourceLocation planId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/lumbermanhut_a_0");
      BuildingPlan plan = ModCultures.getBuildingPlan(planId);
      if (plan == null) {
         helper.fail("Plan 'norman/lumbermanhut_a_0' not loaded");
      } else {
         BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
         boolean placed = BuildingPlacer.placeInstantly(helper.getLevel(), plan, origin, Rotation.NONE);
         if (!placed) {
            helper.fail("placeInstantly failed");
         } else {
            ServerLevel level = helper.getLevel();
            BlockPos scanFrom = helper.absolutePos(new BlockPos(0, 0, 0));
            BlockPos scanTo = helper.absolutePos(new BlockPos(19, 15, 19));
            int headCount = 0;
            int footCount = 0;
            int orphanHeadCount = 0;

            for (int x = scanFrom.getX(); x <= scanTo.getX(); x++) {
               for (int y = scanFrom.getY(); y <= scanTo.getY(); y++) {
                  for (int z = scanFrom.getZ(); z <= scanTo.getZ(); z++) {
                     BlockPos pos = new BlockPos(x, y, z);
                     BlockState state = level.getBlockState(pos);
                     if (state.getBlock() instanceof BedBlock) {
                        BedPart part = (BedPart)state.getValue(BedBlock.PART);
                        if (part == BedPart.HEAD) {
                           headCount++;
                           Direction facing = (Direction)state.getValue(BedBlock.FACING);
                           BlockPos footPos = pos.relative(facing.getOpposite());
                           BlockState footState = level.getBlockState(footPos);
                           if (!(footState.getBlock() instanceof BedBlock) || footState.getValue(BedBlock.PART) != BedPart.FOOT) {
                              orphanHeadCount++;
                           }
                        } else {
                           footCount++;
                        }
                     }
                  }
               }
            }

            if (headCount == 0 && footCount == 0) {
               helper.fail("No bed found in lumberman hut. Bed HEADs were probably removed by updateShape() because FOOT did not exist at placement time.");
            } else if (headCount == 0 && footCount > 0) {
               helper.fail("Found " + footCount + " FOOT but no HEAD. HEADs were removed by updateShape().");
            } else if (orphanHeadCount > 0) {
               helper.fail(
                  "Found "
                     + orphanHeadCount
                     + " bed(s) HEAD without corresponding FOOT (total HEAD: "
                     + headCount
                     + ", total FOOT: "
                     + footCount
                     + "). generateBedFeet() did not generate bed feet."
               );
            } else if (headCount != footCount) {
               helper.fail("Unequal number of HEAD (" + headCount + ") and FOOT (" + footCount + ").");
            } else {
               helper.succeed();
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testLumbermanHut_noUpperDoorInSteps(GameTestHelper helper) {
      ResourceLocation planId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/lumbermanhut_a_0");
      BuildingPlan plan = ModCultures.getBuildingPlan(planId);
      if (plan == null) {
         helper.fail("Plan 'norman/lumbermanhut_a_0' not loaded");
      } else {
         BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
         List<PlacementStep> steps = BuildingPlacer.compilePlacementSteps(helper.getLevel(), plan, origin, Rotation.NONE);
         long upperDoorCount = steps.stream()
            .filter(
               s -> s.blockState().getBlock() instanceof DoorBlock
                  && s.blockState().hasProperty(DoorBlock.HALF)
                  && s.blockState().getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
            )
            .count();
         if (upperDoorCount > 0L) {
            helper.fail("Found " + upperDoorCount + " UPPER door half step(s) in compiled steps. They should be filtered out by compilePlacementSteps.");
         } else {
            long lowerDoorCount = steps.stream()
               .filter(
                  s -> s.blockState().getBlock() instanceof DoorBlock
                     && s.blockState().hasProperty(DoorBlock.HALF)
                     && s.blockState().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
               )
               .count();
            if (lowerDoorCount == 0L) {
               helper.fail("No LOWER door steps found — plan has no doors, test is meaningless.");
            } else {
               helper.succeed();
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testLumbermanHut_doorsHaveLowerAndUpper(GameTestHelper helper) {
      prepareFlatGround(helper, 20);
      ResourceLocation planId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/lumbermanhut_a_0");
      BuildingPlan plan = ModCultures.getBuildingPlan(planId);
      if (plan == null) {
         helper.fail("Plan 'norman/lumbermanhut_a_0' not loaded");
      } else {
         BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
         boolean placed = BuildingPlacer.placeInstantly(helper.getLevel(), plan, origin, Rotation.NONE);
         if (!placed) {
            helper.fail("placeInstantly failed");
         } else {
            ServerLevel level = helper.getLevel();
            BlockPos scanFrom = helper.absolutePos(new BlockPos(0, 0, 0));
            BlockPos scanTo = helper.absolutePos(new BlockPos(19, 15, 19));
            int lowerCount = 0;
            int upperCount = 0;
            int orphanLowerCount = 0;

            for (int x = scanFrom.getX(); x <= scanTo.getX(); x++) {
               for (int y = scanFrom.getY(); y <= scanTo.getY(); y++) {
                  for (int z = scanFrom.getZ(); z <= scanTo.getZ(); z++) {
                     BlockPos pos = new BlockPos(x, y, z);
                     BlockState state = level.getBlockState(pos);
                     if (state.getBlock() instanceof DoorBlock) {
                        DoubleBlockHalf half = (DoubleBlockHalf)state.getValue(DoorBlock.HALF);
                        if (half == DoubleBlockHalf.LOWER) {
                           lowerCount++;
                           BlockPos upperPos = pos.above();
                           BlockState upperState = level.getBlockState(upperPos);
                           if (!(upperState.getBlock() instanceof DoorBlock) || upperState.getValue(DoorBlock.HALF) != DoubleBlockHalf.UPPER) {
                              orphanLowerCount++;
                           }
                        } else {
                           upperCount++;
                        }
                     }
                  }
               }
            }

            if (lowerCount == 0 && upperCount == 0) {
               helper.fail("No door found in lumberman hut.");
            } else if (orphanLowerCount > 0) {
               helper.fail("Found " + orphanLowerCount + " door LOWER half(s) without UPPER. Total LOWER: " + lowerCount + ", total UPPER: " + upperCount);
            } else if (lowerCount != upperCount) {
               helper.fail("Unequal LOWER (" + lowerCount + ") and UPPER (" + upperCount + ") door halves.");
            } else {
               helper.succeed();
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testByzantineMine_subSurfaceStructureIsTopDown(GameTestHelper helper) {
      ResourceLocation planId = ResourceLocation.fromNamespaceAndPath("millenaire", "byzantines/mine_a_0");
      BuildingPlan plan = ModCultures.getBuildingPlan(planId);
      if (plan == null) {
         helper.fail("Plan 'byzantines/mine_a_0' not loaded");
      } else if (plan.groundLevel() >= 0) {
         helper.fail("byzantine mine plan expected to have groundLevel < 0, got " + plan.groundLevel());
      } else {
         BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
         List<PlacementStep> steps = BuildingPlacer.compilePlacementSteps(helper.getLevel(), plan, origin, Rotation.NONE);
         int groundLevel = plan.groundLevel();
         List<PlacementStep> structure = steps.stream().filter(s -> s.phase() == PlacementPhase.STRUCTURE).toList();
         if (structure.isEmpty()) {
            helper.fail("No STRUCTURE steps compiled for byzantine mine plan.");
         } else {
            Set<BlockPos> preserveGroundPositions = new HashSet<>();

            for (SpecialPoint sp : plan.specialPoints()) {
               if (sp.isType("preserve_ground")) {
                  preserveGroundPositions.add(sp.pos());
               }
            }

            int lastSubY = Integer.MAX_VALUE;
            int firstAboveIdx = -1;
            int subSurfaceCount = 0;
            int maxSubY = Integer.MIN_VALUE;
            int minSubY = Integer.MAX_VALUE;

            for (int i = 0; i < structure.size(); i++) {
               PlacementStep step = structure.get(i);
               if (!preserveGroundPositions.contains(step.relativePos())) {
                  int y = step.relativePos().getY();
                  boolean subSurface = y + groundLevel < 0;
                  if (subSurface) {
                     subSurfaceCount++;
                     if (y > maxSubY) {
                        maxSubY = y;
                     }

                     if (y < minSubY) {
                        minSubY = y;
                     }

                     if (y > lastSubY) {
                        helper.fail(
                           "Sub-surface STRUCTURE step at index "
                              + i
                              + " (relY="
                              + y
                              + ") breaks top-down order — previous sub-surface relY was "
                              + lastSubY
                              + " (iso-legacy BUG-156)."
                        );
                        return;
                     }

                     lastSubY = y;
                     if (firstAboveIdx != -1) {
                        helper.fail(
                           "Sub-surface step at index "
                              + i
                              + " (relY="
                              + y
                              + ") appears after above-ground step at index "
                              + firstAboveIdx
                              + " — sub-surface must come first (iso-legacy BUG-156)."
                        );
                        return;
                     }
                  } else if (firstAboveIdx == -1) {
                     firstAboveIdx = i;
                  }
               }
            }

            if (subSurfaceCount == 0) {
               helper.fail("No sub-surface STRUCTURE steps found (excluding preserve_ground) — test meaningless.");
            } else {
               helper.succeed();
            }
         }
      }
   }
}
