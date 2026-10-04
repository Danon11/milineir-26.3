package org.millenaire.test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.ClearMargins;
import org.millenaire.building.SpecialPoint;
import org.millenaire.culture.ModCultures;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerSpawnFactory;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.village.HearthResidentResolver;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class HearthGameTests {
   private static final ResourceLocation INDIAN_PEASANT_TYPE = ResourceLocation.fromNamespaceAndPath("millenaire", "indian/peasant");
   private static final ResourceLocation INDIAN_CULTURE = ResourceLocation.fromNamespaceAndPath("millenaire", "indian");
   private static final ResourceLocation INDIAN_VILLAGE_TYPE = ResourceLocation.fromNamespaceAndPath("millenaire", "indian/agricole");
   private static final int PLATFORM_SIZE = 16;

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 800, batch = "hearth_lighting")
   public static void hearthLightingFiresInMorning(GameTestHelper helper) {
      HearthGameTests.HearthScenario scenario = setupHearthScenario(helper, false, false);
      if (scenario != null) {
         ServerLevel level = helper.getLevel();
         level.setDayTime(1200L);
         if (!HearthResidentResolver.isDesignatedResident(scenario.village(), scenario.home(), scenario.resident().getUUID())) {
            helper.fail(
               "Spawned villager "
                  + scenario.resident().getUUID()
                  + " is not the designated hearth resident — village.getVillagerRecords() = "
                  + scenario.village().getVillagerRecords().keySet()
            );
         } else {
            helper.succeedWhen(
               () -> {
                  BlockState state = level.getBlockState(scenario.hearthPos());
                  if (!(state.getBlock() instanceof CampfireBlock)) {
                     helper.fail(
                        "Block at hearth pos "
                           + scenario.hearthPos()
                           + " is no longer a CampfireBlock: "
                           + state
                           + " | currentGoal="
                           + describeCurrentGoal(scenario.resident())
                     );
                  } else {
                     if (!(Boolean)state.getValue(CampfireBlock.LIT)) {
                        helper.fail(
                           "Campfire still unlit at "
                              + scenario.hearthPos()
                              + " — waiting for LightHearthGoal | dayTime="
                              + level.getDayTime()
                              + " isDay="
                              + level.isDay()
                              + " currentGoal="
                              + describeCurrentGoal(scenario.resident())
                              + " villagerPos="
                              + scenario.resident().blockPosition().toShortString()
                              + " home="
                              + scenario.resident().getHomeBuilding()
                        );
                     }
                  }
               }
            );
         }
      }
   }

   private static String describeCurrentGoal(MillVillager v) {
      GoalScheduler sched = v.getGoalScheduler();
      if (sched == null) {
         return "<no scheduler>";
      }

      ResourceLocation id = sched.getCurrentGoalId();
      return id == null ? "<idle>" : id.toString();
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 800, batch = "hearth_extinguish")
   public static void hearthExtinguishingFiresAtRest(GameTestHelper helper) {
      HearthGameTests.HearthScenario scenario = setupHearthScenario(helper, true, false);
      if (scenario != null) {
         ServerLevel level = helper.getLevel();
         level.setDayTime(13000L);
         if (!HearthResidentResolver.isDesignatedResident(scenario.village(), scenario.home(), scenario.resident().getUUID())) {
            helper.fail("Spawned villager is not the designated hearth resident");
         } else {
            helper.succeedWhen(
               () -> {
                  BlockState state = level.getBlockState(scenario.hearthPos());
                  if (!(state.getBlock() instanceof CampfireBlock)) {
                     helper.fail(
                        "Block at hearth pos "
                           + scenario.hearthPos()
                           + " is no longer a CampfireBlock: "
                           + state
                           + " | currentGoal="
                           + describeCurrentGoal(scenario.resident())
                     );
                  } else {
                     if ((Boolean)state.getValue(CampfireBlock.LIT)) {
                        helper.fail(
                           "Campfire still lit at "
                              + scenario.hearthPos()
                              + " — waiting for RestGoal extinguish phase | dayTime="
                              + level.getDayTime()
                              + " isDay="
                              + level.isDay()
                              + " currentGoal="
                              + describeCurrentGoal(scenario.resident())
                              + " villagerPos="
                              + scenario.resident().blockPosition().toShortString()
                        );
                     }
                  }
               }
            );
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200, batch = "hearth_negative")
   public static void hearthStaysUnlitOutsideMorningWindow(GameTestHelper helper) {
      HearthGameTests.HearthScenario scenario = setupHearthScenario(helper, false, false);
      if (scenario != null) {
         ServerLevel level = helper.getLevel();
         level.setDayTime(8000L);
         helper.runAfterDelay(
            150L,
            () -> {
               BlockState state = level.getBlockState(scenario.hearthPos());
               if (!(state.getBlock() instanceof CampfireBlock)) {
                  helper.fail("Hearth block disappeared during the wait");
               } else if ((Boolean)state.getValue(CampfireBlock.LIT)) {
                  helper.fail(
                     "Campfire was lit at "
                        + scenario.hearthPos()
                        + " despite being outside the morning window — gating regression | dayTime="
                        + level.getDayTime()
                  );
               } else {
                  helper.succeed();
               }
            }
         );
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 800, batch = "hearth_th_signal")
   public static void townHallHearthLightsAsSignalFire(GameTestHelper helper) {
      HearthGameTests.HearthScenario scenario = setupHearthScenario(helper, false, true);
      if (scenario != null) {
         ServerLevel level = helper.getLevel();
         level.setDayTime(1200L);
         if (!HearthResidentResolver.isDesignatedResident(scenario.village(), scenario.home(), scenario.resident().getUUID())) {
            helper.fail("Spawned villager is not the designated hearth resident");
         } else {
            helper.succeedWhen(
               () -> {
                  BlockState state = level.getBlockState(scenario.hearthPos());
                  if (!(state.getBlock() instanceof CampfireBlock)) {
                     helper.fail("Block at hearth pos " + scenario.hearthPos() + " is no longer a CampfireBlock: " + state);
                  } else if (!(Boolean)state.getValue(CampfireBlock.LIT)) {
                     helper.fail(
                        "TownHall campfire still unlit at "
                           + scenario.hearthPos()
                           + " | dayTime="
                           + level.getDayTime()
                           + " currentGoal="
                           + describeCurrentGoal(scenario.resident())
                     );
                  } else {
                     if (!(Boolean)state.getValue(CampfireBlock.SIGNAL_FIRE)) {
                        helper.fail("TownHall campfire lit but signal_fire=false at " + scenario.hearthPos() + " — TH must light as a signal fire");
                     }
                  }
               }
            );
         }
      }
   }

   private static HearthGameTests.HearthScenario setupHearthScenario(GameTestHelper helper, boolean lit, boolean townHall) {
      ServerLevel level = helper.getLevel();

      for (int x = 0; x < 16; x++) {
         for (int z = 0; z < 16; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
            helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
            helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
         }
      }

      BlockPos buildingOrigin = helper.absolutePos(new BlockPos(2, 1, 2));
      BlockPos hearthPos = helper.absolutePos(new BlockPos(6, 1, 6));
      BlockPos spawnPos = helper.absolutePos(new BlockPos(8, 1, 8));
      BlockState campfire = (BlockState)((BlockState)Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, lit))
         .setValue(CampfireBlock.SIGNAL_FIRE, false);
      level.setBlock(hearthPos, campfire, 3);
      BlockPos relHearth = hearthPos.subtract(buildingOrigin);
      ResourceLocation planId = ResourceLocation.fromNamespaceAndPath("millenaire", "test_hearth_plan_" + UUID.randomUUID());
      BuildingPlan fakePlan = new BuildingPlan(
         planId,
         INDIAN_CULTURE,
         "test_template",
         10,
         5,
         10,
         0,
         1,
         BlockPos.ZERO,
         List.of("houses"),
         "default",
         "flat",
         List.of(new SpecialPoint("hearth", null, null, relHearth)),
         null
      );
      ModCultures.registerBuildingPlan(fakePlan);
      ResourceLocation planSetId = null;
      if (townHall) {
         planSetId = ResourceLocation.fromNamespaceAndPath("millenaire", "test_hearth_planset_" + UUID.randomUUID());
         ModCultures.registerBuildingPlanSet(fakeTownHallPlanSet(planSetId, planId));
      }

      VillageId villageId = VillageId.random();
      Village village = new Village(villageId, INDIAN_CULTURE, INDIAN_VILLAGE_TYPE, buildingOrigin);
      VillageSavedData.get(level).getVillageManager().addVillage(village);
      BuildingInstance home = townHall
         ? new BuildingInstance(BuildingId.random(), planId, buildingOrigin, Rotation.NONE, BuildingInstance.Status.COMPLETE, planSetId, "default", 0)
         : new BuildingInstance(BuildingId.random(), planId, buildingOrigin, Rotation.NONE, BuildingInstance.Status.COMPLETE);
      home.resolveSpecialPoints(fakePlan);
      village.addBuilding(home);
      List<BlockPos> hearths = home.getHearthPositions();
      if (hearths.size() == 1 && hearths.get(0).equals(hearthPos)) {
         MillVillager villager = VillagerSpawnFactory.spawnInVillage(level, village, INDIAN_PEASANT_TYPE, spawnPos, home.getId());
         if (villager == null) {
            helper.fail("VillagerSpawnFactory.spawnInVillage returned null for type " + INDIAN_PEASANT_TYPE + " — culture content not loaded?");
            return null;
         } else {
            return new HearthGameTests.HearthScenario(village, home, villager, hearthPos);
         }
      } else {
         helper.fail("Hearth resolution mismatch — expected " + hearthPos + ", got " + hearths);
         return null;
      }
   }

   private static BuildingPlanSet fakeTownHallPlanSet(ResourceLocation planSetId, ResourceLocation planId) {
      BuildingPlanSet.LevelDef level0 = new BuildingPlanSet.LevelDef(
         0,
         planId,
         "test_template",
         10,
         5,
         10,
         0,
         1,
         null,
         List.of(),
         List.of(),
         List.of(),
         List.of(),
         List.of(),
         List.of(),
         List.of(),
         Map.of(),
         List.of(),
         null,
         0,
         false,
         2,
         Map.of()
      );
      return new BuildingPlanSet(
         planSetId,
         INDIAN_CULTURE,
         "test_townhall",
         "townhalls",
         "Test TH",
         0,
         0.0,
         1.0,
         List.of(),
         List.of(),
         10,
         List.of("townhall"),
         "flat",
         "default",
         Map.of("default", List.of(level0)),
         List.of(),
         null,
         ClearMargins.defaults(),
         0,
         0,
         Map.of(),
         List.of(),
         null,
         true,
         false,
         true,
         Map.of(),
         Map.of(),
         null,
         false,
         false,
         0
      );
   }

   private record HearthScenario(Village village, BuildingInstance home, MillVillager resident, BlockPos hearthPos) {
   }
}
