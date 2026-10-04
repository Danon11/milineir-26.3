package org.millenaire.test;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.millenaire.block.VillagePanelBlockEntity;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.SpecialPoint;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.panel.PanelPlacer;
import org.millenaire.village.panel.PanelType;
import org.millenaire.world.BuildingPlacer;
import org.millenaire.world.VillageSpawner;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class ControlledVillagePanelGameTests {
   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void controlledVillage_playerTannery_panelsAreTownHallType(GameTestHelper helper) {
      runPlayerTHPanelCheck(helper, "inuits/player_tannery", "inuits/huntingvillage_controlled", 20);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 800)
   public static void controlledVillage_indianPlayerPalace_panelsAreTownHallType(GameTestHelper helper) {
      runPlayerTHPanelCheck(helper, "indian/playerpalace", "indian/controlled", 60);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 1200)
   public static void controlledVillage_inuit_viaVillageSpawner_panelsAreTownHallType(GameTestHelper helper) {
      runVillageSpawnerCheck(helper, "inuits/huntingvillage_controlled", 96);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 2400)
   public static void controlledVillage_indian_viaVillageSpawner_panelsAreTownHallType(GameTestHelper helper) {
      runVillageSpawnerCheck(helper, "indian/controlled", 160);
   }

   private static void runVillageSpawnerCheck(GameTestHelper helper, String villageTypePath, int groundSize) {
      ServerLevel level = helper.getLevel();

      for (int x = 0; x < groundSize; x++) {
         for (int z = 0; z < groundSize; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
         }
      }

      ResourceLocation villageTypeId = ResourceLocation.fromNamespaceAndPath("millenaire", villageTypePath);
      VillageType villageType = ModCultures.getVillageType(villageTypeId);
      if (villageType == null) {
         helper.fail("VillageType not loaded: " + villageTypeId);
      } else {
         BlockPos center = helper.absolutePos(new BlockPos(groundSize / 2, 1, groundSize / 2));
         Component failure = VillageSpawner.spawnVillage(level, center, villageType, 0, null, null, null);
         if (failure != null) {
            helper.fail("VillageSpawner.spawnVillage failed: " + failure.getString());
         } else {
            VillageManager manager = VillageSavedData.get(level).getVillageManager();
            Village village = null;

            for (Village v : manager.getAllVillages()) {
               if (v.getVillageTypeId().equals(villageTypeId)) {
                  village = v;
                  break;
               }
            }

            if (village == null) {
               helper.fail("Spawned village not found in manager");
            } else {
               BuildingInstance townhall = village.getTownhall();
               if (townhall == null) {
                  helper.fail("Village has no townhall building");
               } else {
                  ResourceLocation thPlanSetId = townhall.getPlanSetId();
                  if (thPlanSetId == null) {
                     helper.fail("Townhall building has no planSetId — explains BUILDING_DEFAULT panels");
                  } else {
                     BuildingPlanSet thPlanSet = ModCultures.getBuildingPlanSet(thPlanSetId);
                     if (thPlanSet == null) {
                        helper.fail("Townhall planSet not found in registry: " + thPlanSetId);
                     } else if (!thPlanSet.isTownHall()) {
                        helper.fail("Townhall planSet " + thPlanSetId + " has isTownHall=false");
                     } else {
                        List<SpecialPoint> signPoints = townhall.getPointsByType("signPos");
                        if (signPoints.isEmpty()) {
                           helper.fail("Townhall has no SIGN_POS resolved");
                        } else {
                           int totalPanels = 0;
                           int buildingDefaultPanels = 0;
                           StringBuilder report = new StringBuilder();

                           for (SpecialPoint sp : signPoints) {
                              BlockPos pos = sp.pos();
                              if (level.getBlockEntity(pos) instanceof VillagePanelBlockEntity be) {
                                 totalPanels++;
                                 PanelType var26 = be.getPanelType();
                                 report.append("\n  panel at ").append(pos).append(" → ").append(var26);
                                 if (var26 == PanelType.BUILDING_DEFAULT || var26 == PanelType.HOUSE) {
                                    buildingDefaultPanels++;
                                 }
                              } else {
                                 report.append("\n  no panel BE at ").append(pos);
                              }
                           }

                           if (totalPanels == 0) {
                              helper.fail("No panel BE placed after VillageSpawner.spawnVillage on the townhall." + report);
                           } else if (buildingDefaultPanels > 0) {
                              helper.fail(
                                 "Townhall has "
                                    + buildingDefaultPanels
                                    + " / "
                                    + totalPanels
                                    + " BUILDING_DEFAULT/HOUSE panels via the real spawn pipeline. Reproduces the user-reported bug."
                                    + report
                              );
                           } else {
                              helper.succeed();
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void runPlayerTHPanelCheck(GameTestHelper helper, String planSetPath, String villageTypePath, int groundSize) {
      ServerLevel level = helper.getLevel();

      for (int x = 0; x < groundSize; x++) {
         for (int z = 0; z < groundSize; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
         }
      }

      ResourceLocation planSetId = ResourceLocation.fromNamespaceAndPath("millenaire", planSetPath);
      BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planSetId);
      if (planSet == null) {
         helper.fail("BuildingPlanSet not loaded: " + planSetId);
      } else if (!planSet.isTownHall()) {
         helper.fail("Plan set " + planSetId + " has isTownHall=false — the JSON regression would explain the in-game bug. Re-run the converter.");
      } else {
         BuildingPlanSet.LevelDef levelDef = planSet.getLevel("a", 0);
         if (levelDef == null) {
            helper.fail("No level 0 for variant 'a' in " + planSetId);
         } else {
            BuildingPlan plan = ModCultures.getBuildingPlan(levelDef.planId());
            if (plan == null) {
               helper.fail("BuildingPlan not loaded: " + levelDef.planId());
            } else {
               BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
               boolean placed = BuildingPlacer.placeInstantly(level, plan, origin, Rotation.NONE);
               if (!placed) {
                  helper.fail("placeInstantly failed for " + plan.id());
               } else {
                  BuildingInstance building = new BuildingInstance(
                     BuildingId.random(), plan.id(), origin, Rotation.NONE, BuildingInstance.Status.COMPLETE, planSetId, "a", 0
                  );
                  building.resolveSpecialPoints(plan);
                  building.initInventory();
                  ResourceLocation villageTypeId = ResourceLocation.fromNamespaceAndPath("millenaire", villageTypePath);
                  VillageType villageType = ModCultures.getVillageType(villageTypeId);
                  if (villageType == null) {
                     helper.fail("VillageType not loaded: " + villageTypeId);
                  } else if (!villageType.showTownHallSigns()) {
                     helper.fail("VillageType " + villageTypeId + " has showTownHallSigns=false — that alone would make PanelPlacer skip placement.");
                  } else {
                     Village village = new Village(VillageId.random(), villageType.culture(), villageTypeId, origin);
                     village.setVillageName("TestJaagiir");
                     village.addBuilding(building);
                     VillageSavedData.get(level).getVillageManager().addVillage(village);
                     PanelPlacer.placePanels(level, village, building);
                     List<SpecialPoint> signPoints = building.getPointsByType("signPos");
                     if (signPoints.isEmpty()) {
                        helper.fail("No SIGN_POS in resolved special points of " + plan.id() + " — converter regression in NBT extraction");
                     } else {
                        int expectedPanels = signPoints.size();
                        int totalPanels = 0;
                        int buildingDefaultPanels = 0;
                        StringBuilder report = new StringBuilder();

                        for (SpecialPoint sp : signPoints) {
                           BlockPos pos = sp.pos();
                           if (level.getBlockEntity(pos) instanceof VillagePanelBlockEntity be) {
                              totalPanels++;
                              PanelType var27 = be.getPanelType();
                              report.append("\n  panel at ").append(pos).append(" → ").append(var27);
                              if (var27 == PanelType.BUILDING_DEFAULT || var27 == PanelType.HOUSE) {
                                 buildingDefaultPanels++;
                              }
                           } else {
                              report.append("\n  no panel BE at ").append(pos);
                           }
                        }

                        if (totalPanels < expectedPanels) {
                           helper.fail(
                              "Only "
                                 + totalPanels
                                 + " / "
                                 + expectedPanels
                                 + " panels placed for "
                                 + plan.id()
                                 + " — structure footprint likely outside the loaded chunks. Test inconclusive for the user-reported bug."
                                 + report
                           );
                        } else if (buildingDefaultPanels > 0) {
                           helper.fail(
                              "Controlled village TH has "
                                 + buildingDefaultPanels
                                 + " / "
                                 + totalPanels
                                 + " panels marked as BUILDING_DEFAULT/HOUSE instead of TOWNHALL types. This reproduces the in-game bug."
                                 + report
                           );
                        } else {
                           helper.succeed();
                        }
                     }
                  }
               }
            }
         }
      }
   }
}
