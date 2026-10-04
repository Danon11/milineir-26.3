package org.millenaire.test;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.millenaire.block.BlockGrapeVine;
import org.millenaire.block.BlockSnailSoil;
import org.millenaire.block.IPaintedBlock;
import org.millenaire.block.ModBlocks;
import org.millenaire.block.OliveTreeLeavesBlock;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.ClearMargins;
import org.millenaire.building.ConstructionTask;
import org.millenaire.building.PlacementPhase;
import org.millenaire.building.PlacementStep;
import org.millenaire.commerce.ShopProfile;
import org.millenaire.commerce.TradeGood;
import org.millenaire.commerce.TradeMenu;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.ModEntities;
import org.millenaire.entity.VillagerSpawnFactory;
import org.millenaire.item.MoneyHelper;
import org.millenaire.test.terrain.TerrainParityTest;
import org.millenaire.test.terrain.TerrainTestBench;
import org.millenaire.test.terrain.TestBuilding;
import org.millenaire.test.terrain.TestTerrain;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.millenaire.world.BuildingPlacer;
import org.millenaire.world.SiteValidator;
import org.millenaire.world.TerrainPreparer;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class MillenaireGameTests {
   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testSiteValidation_flat(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      int surfaceY = 64;
      int size = 5;
      int offset = (33 - size) / 2;
      BlockPos origin = helper.absolutePos(new BlockPos(offset, 0, offset)).atY(surfaceY);
      int margin = 8;

      for (int x = -margin; x < size + margin; x++) {
         for (int z = -margin; z < size + margin; z++) {
            int wx = origin.getX() + x;
            int wz = origin.getZ() + z;

            for (int y = surfaceY + 1; y <= surfaceY + 20; y++) {
               level.setBlock(new BlockPos(wx, y, wz), Blocks.AIR.defaultBlockState(), 3);
            }

            level.setBlock(new BlockPos(wx, surfaceY - 1, wz), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(wx, surfaceY, wz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
         }
      }

      BlockPos center = origin.offset(size / 2, 0, size / 2);
      boolean valid = SiteValidator.validate(level, center, size / 2);
      if (!valid) {
         helper.fail("Flat terrain above sea level should be validated by SiteValidator");
      }

      helper.succeed();
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, required = false)
   public static void testSiteValidation_steep(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      int lowY = 64;
      int highY = 80;
      int radius = 25;
      BlockPos center = helper.absolutePos(new BlockPos(16, 0, 16)).atY(lowY);

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            int wx = center.getX() + dx;
            int wz = center.getZ() + dz;
            int surfaceY = dx < 0 ? lowY : highY;

            for (int y = lowY; y <= highY + 5; y++) {
               level.setBlock(new BlockPos(wx, y, wz), Blocks.AIR.defaultBlockState(), 3);
            }

            level.setBlock(new BlockPos(wx, surfaceY - 1, wz), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(wx, surfaceY, wz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
         }
      }

      boolean valid = SiteValidator.validate(level, center, radius);
      if (valid) {
         helper.fail("Steep terrain (height jump of 16 > MAX_HEIGHT_VARIANCE=10) should be rejected");
      }

      helper.succeed();
   }

   @GameTest(template = "empty_platform", setupTicks = 10L, required = false)
   public static void testSiteValidation_lava(GameTestHelper helper) {
      int gridSize = 17;

      for (int x = 0; x < gridSize; x++) {
         for (int z = 0; z < gridSize; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            if (x < 11) {
               helper.setBlock(new BlockPos(x, 1, z), Blocks.LAVA);
            } else {
               helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
            }

            for (int y = 2; y < 10; y++) {
               helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
         }
      }

      BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
      BlockPos center = origin.offset(gridSize / 2, 0, gridSize / 2);
      boolean valid = SiteValidator.validate(helper.getLevel(), center, gridSize / 2);
      if (valid) {
         helper.fail("Terrain with > 30% lava should be rejected by SiteValidator");
      }

      helper.succeed();
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainPreparation(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      int surfaceY = 4;
      int buildingSize = 4;
      int buildingHeight = 4;
      int offset = (33 - buildingSize) / 2;
      BlockPos origin = helper.absolutePos(new BlockPos(offset, 0, offset)).atY(surfaceY);
      int margin = 8;

      for (int x = -margin; x < buildingSize + margin; x++) {
         for (int z = -margin; z < buildingSize + margin; z++) {
            int wx = origin.getX() + x;
            int wz = origin.getZ() + z;

            for (int y = surfaceY - 12; y <= surfaceY - 2; y++) {
               level.setBlock(new BlockPos(wx, y, wz), Blocks.STONE.defaultBlockState(), 3);
            }

            level.setBlock(new BlockPos(wx, surfaceY - 1, wz), Blocks.DIRT.defaultBlockState(), 3);
            int columnHeight = x >= 0 && x < buildingSize && z >= 0 && z < buildingSize ? 3 + x % 2 : 0;
            level.setBlock(new BlockPos(wx, surfaceY, wz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);

            for (int dy = 1; dy <= columnHeight; dy++) {
               level.setBlock(new BlockPos(wx, surfaceY + dy, wz), Blocks.DIRT.defaultBlockState(), 3);
            }
         }
      }

      int baseY = TerrainPreparer.clearAndFlatten(level, origin, buildingSize, buildingHeight, buildingSize);

      for (int x = 0; x < buildingSize; x++) {
         for (int z = 0; z < buildingSize; z++) {
            BlockPos aboveConstruction = new BlockPos(origin.getX() + x, baseY + buildingHeight, origin.getZ() + z);
            BlockState above = level.getBlockState(aboveConstruction);
            if (!above.isAir()) {
               helper.fail("The block at " + aboveConstruction + " (baseY+height) should be air, found " + above.getBlock());
               return;
            }
         }
      }

      boolean hasFoundation = false;

      for (int y = baseY - 1; y >= baseY - 10; y--) {
         BlockState state = level.getBlockState(new BlockPos(origin.getX(), y, origin.getZ()));
         if (!state.isAir()) {
            hasFoundation = true;
            break;
         }
      }

      if (!hasFoundation) {
         helper.fail("Aucune fondation trouvée sous baseY=" + baseY);
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testVillageCreation(GameTestHelper helper) {
      VillageManager manager = new VillageManager();
      VillageId villageId = VillageId.random();
      ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman");
      ResourceLocation villageTypeId = ResourceLocation.fromNamespaceAndPath("millenaire", "hamlet");
      BlockPos center = helper.absolutePos(new BlockPos(4, 1, 4));
      Village village = new Village(villageId, cultureId, villageTypeId, center);
      manager.addVillage(village);
      Village retrieved = manager.getVillage(villageId);
      if (retrieved == null) {
         helper.fail("Le village devrait être récupérable via VillageManager.getVillage()");
      } else if (!retrieved.getId().equals(villageId)) {
         helper.fail("L'ID du village récupéré ne correspond pas");
      } else {
         ResourceLocation planId = ResourceLocation.fromNamespaceAndPath("millenaire", "townhall");
         ResourceLocation planSetId = ResourceLocation.fromNamespaceAndPath("millenaire", "test/townhall");
         BuildingInstance b1 = new BuildingInstance(
            BuildingId.random(),
            planId,
            new BlockPos(center.getX() - 10, center.getY(), center.getZ()),
            Rotation.NONE,
            BuildingInstance.Status.COMPLETE,
            planSetId,
            "a",
            0
         );
         BuildingInstance b2 = new BuildingInstance(
            BuildingId.random(),
            planId,
            new BlockPos(center.getX() + 10, center.getY(), center.getZ()),
            Rotation.NONE,
            BuildingInstance.Status.UNDER_CONSTRUCTION,
            planSetId,
            "a",
            0
         );
         b2.setConstructionTask(new ConstructionTask(List.of(new PlacementStep(BlockPos.ZERO, Blocks.OAK_PLANKS.defaultBlockState())), 0));
         village.addBuilding(b1);
         village.addBuilding(b2);
         AABB bounds = village.computeBounds();
         if (bounds.minX > center.getX() - 10) {
            helper.fail("Les bounds devraient inclure le bâtiment à x-10");
         } else if (bounds.maxX < center.getX() + 10) {
            helper.fail("Les bounds devraient inclure le bâtiment à x+10");
         } else {
            BuildingInstance unreserved = village.findUnreservedConstruction();
            if (unreserved == null) {
               helper.fail("findUnreservedConstruction devrait retourner b2");
            } else if (!unreserved.getId().equals(b2.getId())) {
               helper.fail("findUnreservedConstruction devrait retourner le bâtiment UNDER_CONSTRUCTION non réservé");
            } else {
               helper.succeed();
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testPersistenceRoundTrip(GameTestHelper helper) {
      VillageSavedData savedData = VillageSavedData.get(helper.getLevel());
      VillageManager manager = savedData.getVillageManager();
      manager.clear();
      VillageId villageId = VillageId.random();
      ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman");
      ResourceLocation villageTypeId = ResourceLocation.fromNamespaceAndPath("millenaire", "hamlet");
      BlockPos center = new BlockPos(100, 64, 200);
      Village village = new Village(villageId, cultureId, villageTypeId, center);
      BuildingId buildingId1 = BuildingId.random();
      ResourceLocation planId = ResourceLocation.fromNamespaceAndPath("millenaire", "townhall");
      ResourceLocation planSetId = ResourceLocation.fromNamespaceAndPath("millenaire", "test/townhall");
      BuildingInstance b1 = new BuildingInstance(
         buildingId1, planId, new BlockPos(90, 64, 200), Rotation.CLOCKWISE_90, BuildingInstance.Status.COMPLETE, planSetId, "a", 0
      );
      village.addBuilding(b1);
      BuildingId buildingId2 = BuildingId.random();
      ResourceLocation planId2 = ResourceLocation.fromNamespaceAndPath("millenaire", "house");
      ResourceLocation planSetId2 = ResourceLocation.fromNamespaceAndPath("millenaire", "test/house");
      BuildingInstance b2 = new BuildingInstance(
         buildingId2, planId2, new BlockPos(110, 64, 200), Rotation.NONE, BuildingInstance.Status.UNDER_CONSTRUCTION, planSetId2, "a", 0
      );
      List<PlacementStep> steps = List.of(
         new PlacementStep(BlockPos.ZERO, Blocks.OAK_PLANKS.defaultBlockState()),
         new PlacementStep(new BlockPos(1, 0, 0), Blocks.OAK_PLANKS.defaultBlockState()),
         new PlacementStep(new BlockPos(2, 0, 0), Blocks.OAK_PLANKS.defaultBlockState())
      );
      ConstructionTask task = new ConstructionTask(steps, 2);
      b2.setConstructionTask(task);
      village.addBuilding(b2);
      UUID villagerUuid = UUID.randomUUID();
      village.addVillager(villagerUuid, ResourceLocation.fromNamespaceAndPath("millenaire", "norman/builder"));
      manager.addVillage(village);
      CompoundTag saved = new CompoundTag();
      savedData.save(saved, helper.getLevel().registryAccess());
      VillageSavedData loaded = (VillageSavedData)VillageSavedData.factory().deserializer().apply(saved, helper.getLevel().registryAccess());
      VillageManager loadedManager = loaded.getVillageManager();
      Village loadedVillage = loadedManager.getVillage(villageId);
      if (loadedVillage == null) {
         helper.fail("Le village devrait exister après désérialisation");
      } else if (!loadedVillage.getCultureId().equals(cultureId)) {
         helper.fail("Le cultureId ne correspond pas après round-trip");
      } else if (!loadedVillage.getVillageTypeId().equals(villageTypeId)) {
         helper.fail("Le villageTypeId ne correspond pas après round-trip");
      } else if (!loadedVillage.getCenter().equals(center)) {
         helper.fail("Le center ne correspond pas après round-trip");
      } else if (loadedVillage.getBuildings().size() != 2) {
         helper.fail("Le village devrait avoir 2 bâtiments, obtenu : " + loadedVillage.getBuildings().size());
      } else {
         BuildingInstance loadedB1 = loadedVillage.getBuilding(buildingId1);
         if (loadedB1 == null) {
            helper.fail("Le bâtiment 1 devrait exister après round-trip");
         } else if (loadedB1.getStatus() != BuildingInstance.Status.COMPLETE) {
            helper.fail("Le statut du bâtiment 1 devrait être COMPLETE");
         } else if (loadedB1.getRotation() != Rotation.CLOCKWISE_90) {
            helper.fail("La rotation du bâtiment 1 devrait être CLOCKWISE_90");
         } else {
            BuildingInstance loadedB2 = loadedVillage.getBuilding(buildingId2);
            if (loadedB2 == null) {
               helper.fail("Le bâtiment 2 devrait exister après round-trip");
            } else if (loadedB2.getStatus() != BuildingInstance.Status.UNDER_CONSTRUCTION) {
               helper.fail("Le statut du bâtiment 2 devrait être UNDER_CONSTRUCTION");
            } else if (loadedVillage.getVillagerUuids().size() != 1) {
               helper.fail("Le village devrait avoir 1 villageois, obtenu : " + loadedVillage.getVillagerUuids().size());
            } else if (!loadedVillage.getVillagerUuids().contains(villagerUuid)) {
               helper.fail("L'UUID du villageois ne correspond pas après round-trip");
            } else {
               manager.clear();
               helper.succeed();
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testBuildingPlacerCompileSteps(GameTestHelper helper) {
      ResourceLocation planId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/well_a_0");
      BuildingPlan plan = ModCultures.getBuildingPlan(planId);
      if (plan == null) {
         helper.fail("Plan 'norman/well_a_0' non chargé — CultureLoader pas actif en GameTest ?");
      } else {
         BlockPos origin = helper.absolutePos(new BlockPos(5, 1, 5));
         List<PlacementStep> steps = BuildingPlacer.compilePlacementSteps(helper.getLevel(), plan, origin, Rotation.NONE);
         if (steps.isEmpty()) {
            helper.fail("compilePlacementSteps a retourné 0 steps pour well_a_0");
         } else {
            boolean hasStructure = steps.stream().anyMatch(s -> s.phase() == PlacementPhase.STRUCTURE);
            if (!hasStructure) {
               helper.fail("Aucun step de phase STRUCTURE trouvé");
            } else {
               helper.succeed();
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testBuildingPlacerPlaceInstantly(GameTestHelper helper) {
      for (int x = 0; x < 20; x++) {
         for (int z = 0; z < 20; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
         }
      }

      BlockPos origin = helper.absolutePos(new BlockPos(5, 1, 5));
      ResourceLocation planId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/well_a_0");
      BuildingPlan plan = ModCultures.getBuildingPlan(planId);
      if (plan == null) {
         helper.fail("Plan non chargé");
      } else {
         boolean placed = BuildingPlacer.placeInstantly(helper.getLevel(), plan, origin, Rotation.NONE);
         if (!placed) {
            helper.fail("placeInstantly a retourné false");
         } else {
            boolean foundBlock = false;

            for (int x = 0; x < 10 && !foundBlock; x++) {
               for (int y = 1; y < 10 && !foundBlock; y++) {
                  for (int z = 0; z < 10 && !foundBlock; z++) {
                     BlockPos check = new BlockPos(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                     if (!helper.getLevel().getBlockState(check).isAir()) {
                        foundBlock = true;
                     }
                  }
               }
            }

            if (!foundBlock) {
               helper.fail("Aucun bloc non-air trouvé après placeInstantly");
            } else {
               helper.succeed();
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainPreparerIgnoresLeaves(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      int surfaceY = 4;
      BlockPos center = helper.absolutePos(new BlockPos(16, 0, 16)).atY(surfaceY);
      int cx = center.getX();
      int cz = center.getZ();

      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            for (int y = surfaceY - 5; y < surfaceY; y++) {
               level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.DIRT.defaultBlockState(), 3);
            }

            level.setBlock(new BlockPos(cx + dx, surfaceY, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
         }
      }

      for (int dy = 1; dy <= 3; dy++) {
         level.setBlock(new BlockPos(cx, surfaceY + dy, cz), Blocks.OAK_LOG.defaultBlockState(), 3);
      }

      for (int dy = 4; dy <= 5; dy++) {
         level.setBlock(new BlockPos(cx, surfaceY + dy, cz), Blocks.OAK_LEAVES.defaultBlockState(), 3);
      }

      int groundHeight = TerrainPreparer.getGroundHeight(level, cx, cz);
      int expectedMax = surfaceY + 1;
      if (groundHeight > expectedMax) {
         helper.fail("getGroundHeight retourne " + groundHeight + " — devrait ignorer les feuilles/logs (sol attendu à " + expectedMax + ")");
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testVillagerSpawnWithType(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();

      for (int x = 0; x < 10; x++) {
         for (int z = 0; z < 10; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
         }
      }

      BlockPos spawnPos = helper.absolutePos(new BlockPos(5, 1, 5));
      VillageId villageId = VillageId.random();
      ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman");
      ResourceLocation villageTypeId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/agricole");
      Village village = new Village(villageId, cultureId, villageTypeId, spawnPos);
      VillageSavedData savedData = VillageSavedData.get(level);
      savedData.getVillageManager().addVillage(village);
      ResourceLocation typeId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/farmer");
      MillVillager villager = VillagerSpawnFactory.spawnInVillage(level, village, typeId, spawnPos, null);
      if (villager == null) {
         helper.fail("spawnInVillage a retourné null — VillagerType 'norman/farmer' pas chargé ?");
      } else {
         helper.succeedWhen(() -> {
            ResourceLocation actualType = villager.getVillagerTypeId();
            if (actualType == null || !actualType.equals(typeId)) {
               helper.fail("Type attendu " + typeId + ", obtenu " + actualType);
            }

            VillageId actualVillageId = villager.getVillageId();
            if (actualVillageId == null || !actualVillageId.equals(villageId)) {
               helper.fail("VillageId attendu " + villageId + ", obtenu " + actualVillageId);
            }

            String displayName = villager.getVillagerDisplayName();
            if (displayName == null || displayName.isEmpty() || displayName.equals("entity.millenaire.villager")) {
               helper.fail("Le nom d'affichage ne devrait pas être vide ou un placeholder, obtenu : '" + displayName + "'");
            }
         });
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testVillagerInitialInventory(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();

      for (int x = 0; x < 10; x++) {
         for (int z = 0; z < 10; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
         }
      }

      BlockPos spawnPos = helper.absolutePos(new BlockPos(5, 1, 5));
      VillageId villageId = VillageId.random();
      ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman");
      ResourceLocation villageTypeId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/agricole");
      Village village = new Village(villageId, cultureId, villageTypeId, spawnPos);
      VillageSavedData savedData = VillageSavedData.get(level);
      savedData.getVillageManager().addVillage(village);
      ResourceLocation typeId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/farmer");
      MillVillager villager = VillagerSpawnFactory.spawnInVillage(level, village, typeId, spawnPos, null);
      if (villager == null) {
         helper.fail("spawnInVillage a retourné null — VillagerType 'norman/farmer' pas chargé ?");
      } else {
         int seedCount = villager.getInventory().getCount(Items.WHEAT_SEEDS);
         if (seedCount != 100) {
            helper.fail("Le farmer devrait avoir 100 wheat_seeds, obtenu : " + seedCount);
         } else {
            helper.succeed();
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testTerrainPreparerDamNearWater(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));

      for (int x = -7; x < 22; x++) {
         for (int z = -4; z < 21; z++) {
            level.setBlock(new BlockPos(origin.getX() + x, origin.getY() - 2, origin.getZ() + z), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(origin.getX() + x, origin.getY() - 1, origin.getZ() + z), Blocks.DIRT.defaultBlockState(), 3);
            level.setBlock(new BlockPos(origin.getX() + x, origin.getY(), origin.getZ() + z), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
         }
      }

      for (int x = -7; x < 22; x++) {
         for (int z = 0; z < 3; z++) {
            level.setBlock(new BlockPos(origin.getX() + x, origin.getY() - 1, origin.getZ() + z), Blocks.WATER.defaultBlockState(), 3);
            level.setBlock(new BlockPos(origin.getX() + x, origin.getY(), origin.getZ() + z), Blocks.WATER.defaultBlockState(), 3);
         }
      }

      BlockPos buildOrigin = new BlockPos(origin.getX(), origin.getY(), origin.getZ() + 5);
      int baseY = origin.getY();
      TerrainPreparer.clearAndFlattenAtY(level, buildOrigin, 10, 5, 5, Rotation.NONE, 0, baseY, ClearMargins.defaults());
      boolean waterInBuildZone = false;

      for (int x = 0; x < 10; x++) {
         for (int z = 5; z < 10; z++) {
            for (int dy = -1; dy <= 3; dy++) {
               BlockPos check = new BlockPos(origin.getX() + x, baseY + dy, origin.getZ() + z);
               if (level.getBlockState(check).is(Blocks.WATER)) {
                  waterInBuildZone = true;
                  break;
               }
            }

            if (waterInBuildZone) {
               break;
            }
         }

         if (waterInBuildZone) {
            break;
         }
      }

      if (waterInBuildZone) {
         helper.fail("Water propagated into build zone — anti-flood dam failed");
      } else {
         boolean hasDam = false;

         for (int x = 0; x < 10; x++) {
            BlockPos damPos = new BlockPos(origin.getX() + x, baseY, origin.getZ() + 2);
            BlockState state = level.getBlockState(damPos);
            if (!state.isAir() && !state.is(Blocks.WATER)) {
               hasDam = true;
               break;
            }
         }

         if (!hasDam) {
            helper.fail("No anti-flood dam found at z=2 (transition margin between river and building) at baseY=" + baseY);
         } else {
            helper.succeed();
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 400)
   public static void testVillagerPersistenceRoundTrip(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();

      for (int x = 0; x < 10; x++) {
         for (int z = 0; z < 10; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
         }
      }

      BlockPos spawnPos = helper.absolutePos(new BlockPos(5, 1, 5));
      VillageId villageId = VillageId.random();
      ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman");
      ResourceLocation villageTypeId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/agricole");
      Village village = new Village(villageId, cultureId, villageTypeId, spawnPos);
      VillageSavedData savedData = VillageSavedData.get(level);
      savedData.getVillageManager().addVillage(village);
      ResourceLocation typeId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/farmer");
      MillVillager villager = VillagerSpawnFactory.spawnInVillage(level, village, typeId, spawnPos, null);
      if (villager == null) {
         helper.fail("spawnInVillage a retourné null — VillagerType 'norman/farmer' pas chargé ?");
      } else {
         ResourceLocation originalTypeId = villager.getVillagerTypeId();
         VillageId originalVillageId = villager.getVillageId();
         String originalFirstName = villager.getFirstName();
         String originalFamilyName = villager.getFamilyName();
         CompoundTag tag = new CompoundTag();
         villager.save(tag);
         MillVillager loaded = (MillVillager)((EntityType)ModEntities.MILL_VILLAGER.get()).create(level);
         if (loaded == null) {
            helper.fail("Impossible de créer le villageois pour le load");
         } else {
            loaded.load(tag);
            if (!typeId.equals(loaded.getVillagerTypeId())) {
               helper.fail("VillagerTypeId perdu : attendu " + typeId + ", obtenu " + loaded.getVillagerTypeId());
            } else if (!originalVillageId.equals(loaded.getVillageId())) {
               helper.fail("VillageId perdu : attendu " + originalVillageId + ", obtenu " + loaded.getVillageId());
            } else if (!originalFirstName.equals(loaded.getFirstName())) {
               helper.fail("FirstName perdu : attendu '" + originalFirstName + "', obtenu '" + loaded.getFirstName() + "'");
            } else if (!originalFamilyName.equals(loaded.getFamilyName())) {
               helper.fail("FamilyName perdu : attendu '" + originalFamilyName + "', obtenu '" + loaded.getFamilyName() + "'");
            } else {
               int seedCount = loaded.getInventory().getCount(Items.WHEAT_SEEDS);
               if (seedCount != 100) {
                  helper.fail("Inventaire perdu : attendu 100 wheat_seeds, obtenu " + seedCount);
               } else {
                  helper.succeed();
               }
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testTradeMenuBuyAutoGenerate(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();

      for (int x = 0; x < 10; x++) {
         for (int z = 0; z < 10; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
         }
      }

      BlockPos center = helper.absolutePos(new BlockPos(5, 1, 5));
      VillageId villageId = VillageId.random();
      ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman");
      ResourceLocation villageTypeId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/agricole");
      Village village = new Village(villageId, cultureId, villageTypeId, center);
      village.setVillageName("TestVillage");
      VillageSavedData savedData = VillageSavedData.get(level);
      savedData.getVillageManager().addVillage(village);
      ResourceLocation planId = ResourceLocation.fromNamespaceAndPath("millenaire", "test_shop");
      BuildingInstance building = new BuildingInstance(
         BuildingId.random(),
         planId,
         center,
         Rotation.NONE,
         BuildingInstance.Status.COMPLETE,
         ResourceLocation.fromNamespaceAndPath("millenaire", "test/shop"),
         "a",
         0
      );
      TradeGood appleGood = new TradeGood("apple", "minecraft:apple", 10, 5, 0, 0, true, 0, "food", true, 0);
      List<TradeGood> catalog = List.of(appleGood);
      ShopProfile shopProfile = new ShopProfile(List.of("apple"), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(center.getX(), center.getY(), center.getZ());
      MoneyHelper.addDeniers(player.getInventory(), 100);
      int moneyBefore = MoneyHelper.getTotalDeniers(player.getInventory());
      if (moneyBefore != 100) {
         helper.fail("Le joueur devrait avoir 100 deniers, obtenu : " + moneyBefore);
      } else {
         TradeMenu menu = new TradeMenu(1, player.getInventory(), village, building, shopProfile, catalog, center);
         boolean result = menu.clickMenuButton(player, 0);
         if (!result) {
            helper.fail("clickMenuButton a retourné false pour un achat valide");
         } else {
            int moneyAfter = MoneyHelper.getTotalDeniers(player.getInventory());
            if (moneyAfter != 90) {
               helper.fail("Le joueur devrait avoir 90 deniers après l'achat, obtenu : " + moneyAfter);
            } else {
               int appleCount = 0;

               for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                  ItemStack stack = player.getInventory().getItem(i);
                  if (stack.is(Items.APPLE)) {
                     appleCount += stack.getCount();
                  }
               }

               if (appleCount != 1) {
                  helper.fail("Le joueur devrait avoir 1 pomme après l'achat, obtenu : " + appleCount);
               } else {
                  helper.succeed();
               }
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testTradeMenuSellItem(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();

      for (int x = 0; x < 10; x++) {
         for (int z = 0; z < 10; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
         }
      }

      BlockPos center = helper.absolutePos(new BlockPos(5, 1, 5));
      VillageId villageId = VillageId.random();
      ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman");
      ResourceLocation villageTypeId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/agricole");
      Village village = new Village(villageId, cultureId, villageTypeId, center);
      village.setVillageName("TestVillage");
      VillageSavedData savedData = VillageSavedData.get(level);
      savedData.getVillageManager().addVillage(village);
      ResourceLocation planId = ResourceLocation.fromNamespaceAndPath("millenaire", "test_shop");
      BuildingInstance building = new BuildingInstance(
         BuildingId.random(),
         planId,
         center,
         Rotation.NONE,
         BuildingInstance.Status.COMPLETE,
         ResourceLocation.fromNamespaceAndPath("millenaire", "test/shop"),
         "a",
         0
      );
      TradeGood appleGood = new TradeGood("apple", "minecraft:apple", 10, 5, 0, 0, false, 0, "food", true, 0);
      List<TradeGood> catalog = List.of(appleGood);
      ShopProfile shopProfile = new ShopProfile(List.of("apple"), List.of("apple"), Collections.emptyList(), Collections.emptyList());
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(center.getX(), center.getY(), center.getZ());
      player.getInventory().add(new ItemStack(Items.APPLE, 10));
      int applesBefore = 0;

      for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (stack.is(Items.APPLE)) {
            applesBefore += stack.getCount();
         }
      }

      if (applesBefore != 10) {
         helper.fail("Le joueur devrait avoir 10 pommes au départ, obtenu : " + applesBefore);
      } else {
         TradeMenu menu = new TradeMenu(1, player.getInventory(), village, building, shopProfile, catalog, center);
         boolean result = menu.clickMenuButton(player, 9);
         if (!result) {
            helper.fail("clickMenuButton a retourné false pour une vente valide");
         } else {
            int moneyAfter = MoneyHelper.getTotalDeniers(player.getInventory());
            if (moneyAfter != 5) {
               helper.fail("Le joueur devrait avoir 5 deniers après la vente, obtenu : " + moneyAfter);
            } else {
               int applesAfter = 0;

               for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                  ItemStack stack = player.getInventory().getItem(i);
                  if (stack.is(Items.APPLE)) {
                     applesAfter += stack.getCount();
                  }
               }

               if (applesAfter != 9) {
                  helper.fail("Le joueur devrait avoir 9 pommes après la vente, obtenu : " + applesAfter);
               } else {
                  helper.succeed();
               }
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testTerrainPreparerFoundationDepth(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      int surfaceY = 80;
      BlockPos origin = helper.absolutePos(new BlockPos(2, 0, 2)).atY(surfaceY);

      for (int x = -6; x < 12; x++) {
         for (int z = -6; z < 12; z++) {
            int wx = origin.getX() + x;
            int wz = origin.getZ() + z;
            level.setBlock(new BlockPos(wx, surfaceY, wz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            level.setBlock(new BlockPos(wx, surfaceY - 1, wz), Blocks.DIRT.defaultBlockState(), 3);

            for (int dy = -12; dy < -1; dy++) {
               level.setBlock(new BlockPos(wx, surfaceY + dy, wz), Blocks.AIR.defaultBlockState(), 3);
            }
         }
      }

      int cx = origin.getX() + 3;
      int cz = origin.getZ() + 3;
      BlockState preGrass = level.getBlockState(new BlockPos(cx, surfaceY, cz));
      BlockState preDirt = level.getBlockState(new BlockPos(cx, surfaceY - 1, cz));
      BlockState preAir = level.getBlockState(new BlockPos(cx, surfaceY - 5, cz));
      if (preGrass.is(Blocks.GRASS_BLOCK) && preDirt.is(Blocks.DIRT) && preAir.isAir()) {
         int baseY = TerrainPreparer.clearAndFlattenAtY(level, origin, 6, 5, 6, Rotation.NONE, 0, surfaceY + 1, ClearMargins.defaults());
         int solidCount = 0;
         int airCount = 0;
         StringBuilder diag = new StringBuilder();

         for (int dy = -10; dy < 0; dy++) {
            BlockPos checkPos = new BlockPos(cx, baseY + dy, cz);
            BlockState state = level.getBlockState(checkPos);
            boolean solid = !state.isAir() && state.getFluidState().isEmpty();
            if (solid) {
               solidCount++;
            } else {
               airCount++;
            }

            diag.append("  Y=").append(baseY + dy).append(": ").append(state).append(solid ? " [OK]" : " [VOID]").append("\n");
         }

         if (solidCount < 8) {
            helper.fail(
               "Foundation: " + solidCount + " solid, " + airCount + " void. baseY=" + baseY + " origin=" + origin + " center=(" + cx + "," + cz + ")\n" + diag
            );
         } else {
            helper.succeed();
         }
      } else {
         helper.fail(
            "Pre-condition failed! Y=" + surfaceY + ": " + preGrass + ", Y=" + (surfaceY - 1) + ": " + preDirt + ", Y=" + (surfaceY - 5) + ": " + preAir
         );
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400, required = false)
   public static void testTerrainPreparerHillTransition(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      int baseY = 80;
      BlockPos origin = helper.absolutePos(new BlockPos(6, 0, 6)).atY(baseY);

      for (int x = -7; x < 14; x++) {
         for (int z = -7; z < 14; z++) {
            int wx = origin.getX() + x;
            int wz = origin.getZ() + z;
            int hillHeight = x > 6 ? Math.min(4, x - 6) : 0;
            int surfaceY = baseY + hillHeight;

            for (int y = baseY - 1; y <= baseY + 30; y++) {
               level.setBlock(new BlockPos(wx, y, wz), Blocks.AIR.defaultBlockState(), 3);
            }

            for (int y = baseY - 2; y <= surfaceY; y++) {
               level.setBlock(new BlockPos(wx, y, wz), Blocks.DIRT.defaultBlockState(), 3);
            }

            level.setBlock(new BlockPos(wx, surfaceY, wz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
         }
      }

      int computedBaseY = TerrainPreparer.clearAndFlatten(level, origin, 6, 5, 6);
      boolean interiorClean = true;

      for (int x = 0; x < 6; x++) {
         for (int z = 0; z < 6; z++) {
            for (int dy = 0; dy < 5; dy++) {
               BlockPos pos = new BlockPos(origin.getX() + x, computedBaseY + dy, origin.getZ() + z);
               BlockState state = level.getBlockState(pos);
               if (!state.isAir() && !state.is(Blocks.SHORT_GRASS) && !state.is(Blocks.FERN)) {
                  interiorClean = false;
                  break;
               }
            }
         }
      }

      if (!interiorClean) {
         helper.fail("Building interior (above computedBaseY) should be air");
      } else {
         boolean hasTransition = false;

         for (int margin = 1; margin <= 4; margin++) {
            int wx = origin.getX() + 6 + margin;
            int wz = origin.getZ() + 3;

            for (int y = computedBaseY + 5; y >= computedBaseY; y--) {
               BlockState state = level.getBlockState(new BlockPos(wx, y, wz));
               if (!state.isAir()) {
                  if (y > computedBaseY) {
                     hasTransition = true;
                  }
                  break;
               }
            }
         }

         if (!hasTransition) {
            helper.fail("La marge devrait avoir une transition en pente, pas un nivellement brutal");
         } else {
            helper.succeed();
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testTerrainPreparerClearsTreesInBuildZone(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos origin = helper.absolutePos(new BlockPos(6, 1, 6));

      for (int x = -7; x < 14; x++) {
         for (int z = -7; z < 14; z++) {
            int wx = origin.getX() + x;
            int wz = origin.getZ() + z;
            level.setBlock(new BlockPos(wx, origin.getY() - 1, wz), Blocks.DIRT.defaultBlockState(), 3);
            level.setBlock(new BlockPos(wx, origin.getY(), wz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
         }
      }

      int treeX = origin.getX() + 3;
      int treeZ = origin.getZ() + 3;

      for (int dy = 1; dy <= 5; dy++) {
         level.setBlock(new BlockPos(treeX, origin.getY() + dy, treeZ), Blocks.OAK_LOG.defaultBlockState(), 3);
      }

      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            for (int dy = 4; dy <= 6; dy++) {
               BlockPos leafPos = new BlockPos(treeX + dx, origin.getY() + dy, treeZ + dz);
               if (level.getBlockState(leafPos).isAir()) {
                  level.setBlock(leafPos, Blocks.OAK_LEAVES.defaultBlockState(), 3);
               }
            }
         }
      }

      int baseY = TerrainPreparer.clearAndFlatten(level, origin, 6, 5, 6);
      boolean logRemaining = false;

      for (int x = 0; x < 6; x++) {
         for (int z = 0; z < 6; z++) {
            for (int dy = 0; dy < 10; dy++) {
               BlockPos pos = new BlockPos(origin.getX() + x, baseY + dy, origin.getZ() + z);
               if (level.getBlockState(pos).is(Blocks.OAK_LOG)) {
                  logRemaining = true;
                  break;
               }
            }
         }
      }

      if (logRemaining) {
         helper.fail("Des bûches restent dans la zone intérieure du bâtiment après clearAndFlatten");
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testTerrainPreparerPreservesFoundationBlocks(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      int surfaceY = 4;
      int buildingW = 6;
      int buildingH = 5;
      int buildingD = 6;
      int offset = (33 - buildingW) / 2;
      BlockPos origin = helper.absolutePos(new BlockPos(offset, 0, offset)).atY(surfaceY);
      int margin = 8;

      for (int x = -margin; x < buildingW + margin; x++) {
         for (int z = -margin; z < buildingD + margin; z++) {
            int wx = origin.getX() + x;
            int wz = origin.getZ() + z;

            for (int dy = -15; dy < 0; dy++) {
               level.setBlock(new BlockPos(wx, surfaceY + dy, wz), Blocks.STONE.defaultBlockState(), 3);
            }

            level.setBlock(new BlockPos(wx, surfaceY, wz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
         }
      }

      int baseY = TerrainPreparer.clearAndFlatten(level, origin, buildingW, buildingH, buildingD);
      int cx = origin.getX() + 3;
      int cz = origin.getZ() + 3;
      int stoneCount = 0;

      for (int dy = -10; dy < 0; dy++) {
         BlockState state = level.getBlockState(new BlockPos(cx, baseY + dy, cz));
         if (state.is(Blocks.STONE)) {
            stoneCount++;
         }
      }

      if (stoneCount < 5) {
         helper.fail(
            "La stone existante devrait être préservée dans les fondations (PRESERVEGROUNDDEPTH), trouvé seulement " + stoneCount + "/10 couches de stone"
         );
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testTerrainPreparerWaterDoesNotInfiltrateFoundations(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos origin = helper.absolutePos(new BlockPos(6, 1, 6));

      for (int x = -7; x < 14; x++) {
         for (int z = -7; z < 14; z++) {
            int wx = origin.getX() + x;
            int wz = origin.getZ() + z;

            for (int dy = -5; dy <= 0; dy++) {
               level.setBlock(new BlockPos(wx, origin.getY() + dy, wz), Blocks.DIRT.defaultBlockState(), 3);
            }

            level.setBlock(new BlockPos(wx, origin.getY(), wz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
         }
      }

      for (int x = -7; x < -2; x++) {
         for (int z = -3; z < 10; z++) {
            int wx = origin.getX() + x;
            int wz = origin.getZ() + z;

            for (int dy = -2; dy <= 0; dy++) {
               level.setBlock(new BlockPos(wx, origin.getY() + dy, wz), Blocks.WATER.defaultBlockState(), 3);
            }
         }
      }

      int baseY = TerrainPreparer.clearAndFlatten(level, origin, 6, 5, 6);
      boolean waterFound = false;
      String waterLocation = "";

      for (int x = 0; x < 6; x++) {
         for (int z = 0; z < 6; z++) {
            for (int dy = -10; dy < 5; dy++) {
               BlockPos pos = new BlockPos(origin.getX() + x, baseY + dy, origin.getZ() + z);
               if (level.getBlockState(pos).is(Blocks.WATER)) {
                  waterFound = true;
                  waterLocation = "(" + x + ", " + dy + ", " + z + ")";
                  break;
               }
            }

            if (waterFound) {
               break;
            }
         }

         if (waterFound) {
            break;
         }
      }

      if (waterFound) {
         helper.fail("Eau trouvée dans les fondations à " + waterLocation + " — l'anti-inondation devrait empêcher l'infiltration");
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testTerrainPreparerMixedTerrain(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos origin = helper.absolutePos(new BlockPos(6, 5, 6));

      for (int x = -7; x < 14; x++) {
         for (int z = -7; z < 14; z++) {
            int wx = origin.getX() + x;
            int wz = origin.getZ() + z;
            int variation = (x + z) % 2 == 0 ? 3 : -2;
            int surfaceY = origin.getY() + variation;

            for (int y = origin.getY() - 8; y <= surfaceY; y++) {
               level.setBlock(new BlockPos(wx, y, wz), Blocks.DIRT.defaultBlockState(), 3);
            }

            level.setBlock(new BlockPos(wx, surfaceY, wz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);

            for (int y = surfaceY + 1; y < surfaceY + 10; y++) {
               level.setBlock(new BlockPos(wx, y, wz), Blocks.AIR.defaultBlockState(), 3);
            }
         }
      }

      int baseY = TerrainPreparer.clearAndFlatten(level, origin, 6, 5, 6);
      int nonAirCount = 0;
      String firstNonAir = "";

      for (int x = 0; x < 6; x++) {
         for (int z = 0; z < 6; z++) {
            for (int dy = 0; dy < 5; dy++) {
               BlockPos pos = new BlockPos(origin.getX() + x, baseY + dy, origin.getZ() + z);
               BlockState state = level.getBlockState(pos);
               if (!state.isAir() && !state.is(Blocks.SHORT_GRASS) && !state.is(Blocks.FERN) && !state.is(Blocks.TALL_GRASS)) {
                  nonAirCount++;
                  if (firstNonAir.isEmpty()) {
                     firstNonAir = state.getBlock().toString() + " à (" + x + "," + dy + "," + z + ")";
                  }
               }
            }
         }
      }

      if (nonAirCount > 0) {
         helper.fail("The building's interior contains " + nonAirCount + " non-air blocks after clearAndFlatten on mixed terrain. First: " + firstNonAir);
      } else {
         int gapCount = 0;

         for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 6; z++) {
               BlockPos pos = new BlockPos(origin.getX() + x, baseY - 1, origin.getZ() + z);
               BlockState state = level.getBlockState(pos);
               if (state.isAir() || !state.getFluidState().isEmpty()) {
                  gapCount++;
               }
            }
         }

         if (gapCount > 0) {
            helper.fail("The layer below baseY contains " + gapCount + " holes — hollows should be filled");
         } else {
            helper.succeed();
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testMedianSurfaceHeightIgnoresTreeCanopy(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      int surfaceY = 4;
      int footprint = 8;
      int offset = (33 - footprint) / 2;
      BlockPos origin = helper.absolutePos(new BlockPos(offset, 0, offset)).atY(surfaceY);
      int groundY = surfaceY;
      int scanMargin = 7;

      for (int x = -scanMargin; x < footprint + scanMargin; x++) {
         for (int z = -scanMargin; z < footprint + scanMargin; z++) {
            int wx = origin.getX() + x;
            int wz = origin.getZ() + z;

            for (int y = groundY + 1; y <= groundY + 80; y++) {
               level.setBlock(new BlockPos(wx, y, wz), Blocks.AIR.defaultBlockState(), 3);
            }

            for (int y = groundY - 5; y < groundY; y++) {
               level.setBlock(new BlockPos(wx, y, wz), Blocks.DIRT.defaultBlockState(), 3);
            }

            level.setBlock(new BlockPos(wx, groundY, wz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
         }
      }

      int[][] treePositions = new int[][]{{1, 1}, {6, 1}, {1, 6}, {6, 6}};

      for (int[] tp : treePositions) {
         int tx = origin.getX() + tp[0];
         int tz = origin.getZ() + tp[1];

         for (int dy = 1; dy <= 7; dy++) {
            level.setBlock(new BlockPos(tx, groundY + dy, tz), Blocks.OAK_LOG.defaultBlockState(), 3);
         }

         for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
               for (int dy = 5; dy <= 8; dy++) {
                  BlockPos lp = new BlockPos(tx + dx, groundY + dy, tz + dz);
                  if (level.getBlockState(lp).isAir()) {
                     level.setBlock(lp, Blocks.OAK_LEAVES.defaultBlockState(), 3);
                  }
               }
            }
         }
      }

      int average = TerrainPreparer.computeAverageSurfaceHeight(level, origin, footprint, footprint, ClearMargins.defaults());
      if (Math.abs(average - (groundY + 1)) > 1) {
         helper.fail("La moyenne devrait être ~" + (groundY + 1) + " (niveau du sol), obtenu " + average + " — les arbres faussent probablement le calcul");
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testTerrainPreparerRiverCrossing(GameTestHelper helper) {
      TerrainTestBench.runTest(helper, TestTerrain.RIVER_EAST, TestBuilding.CUBE_5);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testTerrainPreparerSteepSlopeClearing(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos origin = helper.absolutePos(new BlockPos(6, 1, 6));

      for (int x = -7; x < 14; x++) {
         for (int z = -7; z < 14; z++) {
            int wx = origin.getX() + x;
            int wz = origin.getZ() + z;
            int hillHeight = x >= 4 ? 8 : 0;
            int surfaceY = origin.getY() + hillHeight;

            for (int y = origin.getY() - 3; y <= surfaceY; y++) {
               level.setBlock(new BlockPos(wx, y, wz), Blocks.DIRT.defaultBlockState(), 3);
            }

            level.setBlock(new BlockPos(wx, surfaceY, wz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
         }
      }

      int baseY = TerrainPreparer.clearAndFlatten(level, origin, 6, 5, 6);
      int blockingCount = 0;

      for (int x = 0; x < 6; x++) {
         for (int z = 0; z < 6; z++) {
            for (int dy = 0; dy < 5; dy++) {
               BlockPos pos = new BlockPos(origin.getX() + x, baseY + dy, origin.getZ() + z);
               BlockState state = level.getBlockState(pos);
               if (!state.isAir() && !state.is(Blocks.SHORT_GRASS) && !state.is(Blocks.FERN)) {
                  blockingCount++;
               }
            }
         }
      }

      if (blockingCount > 0) {
         helper.fail("La falaise à l'est bloque encore " + blockingCount + " blocs dans le bâtiment après clearAndFlatten");
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testTerrainPreparerReplacesNonOccludingInFoundation(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      int surfaceY = 4;
      int buildingW = 6;
      int buildingH = 5;
      int buildingD = 6;
      int offset = (33 - buildingW) / 2;
      BlockPos origin = helper.absolutePos(new BlockPos(offset, 0, offset)).atY(surfaceY);
      int margin = 8;

      for (int x = -margin; x < buildingW + margin; x++) {
         for (int z = -margin; z < buildingD + margin; z++) {
            int wx = origin.getX() + x;
            int wz = origin.getZ() + z;

            for (int dy = -15; dy < 0; dy++) {
               level.setBlock(new BlockPos(wx, surfaceY + dy, wz), Blocks.STONE.defaultBlockState(), 3);
            }

            level.setBlock(new BlockPos(wx, surfaceY, wz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);

            for (int dy = 1; dy <= 10; dy++) {
               level.setBlock(new BlockPos(wx, surfaceY + dy, wz), Blocks.AIR.defaultBlockState(), 3);
            }
         }
      }

      for (int x = 1; x < buildingW - 1; x++) {
         for (int z = 1; z < buildingD - 1; z++) {
            int wx = origin.getX() + x;
            int wz = origin.getZ() + z;
            level.setBlock(new BlockPos(wx, surfaceY, wz), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(new BlockPos(wx, surfaceY - 1, wz), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(new BlockPos(wx, surfaceY - 2, wz), Blocks.DIRT.defaultBlockState(), 3);
            level.setBlock(new BlockPos(wx, surfaceY - 1, wz), Blocks.SWEET_BERRY_BUSH.defaultBlockState(), 2);
         }
      }

      int baseY = TerrainPreparer.clearAndFlatten(level, origin, buildingW, buildingH, buildingD);
      int nonOccludingCount = 0;

      for (int x = 0; x < buildingW; x++) {
         for (int z = 0; z < buildingD; z++) {
            int wx = origin.getX() + x;
            int wz = origin.getZ() + z;

            for (int dy = -10; dy < 0; dy++) {
               BlockPos pos = new BlockPos(wx, baseY + dy, wz);
               BlockState state = level.getBlockState(pos);
               if (!state.isAir() && !state.canOcclude()) {
                  nonOccludingCount++;
               }
            }
         }
      }

      if (nonOccludingCount > 0) {
         helper.fail("Found " + nonOccludingCount + " non-occluding blocks (e.g. bushes) in foundation zone — they should have been replaced by fill material");
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainBench_cube5_flat(GameTestHelper helper) {
      TerrainTestBench.runTest(helper, TestTerrain.FLAT, TestBuilding.CUBE_5);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainBench_cube6_flat(GameTestHelper helper) {
      TerrainTestBench.runTest(helper, TestTerrain.FLAT, TestBuilding.CUBE_6);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainBench_cube5_hillNorth(GameTestHelper helper) {
      TerrainTestBench.runTest(helper, TestTerrain.HILL_NORTH, TestBuilding.CUBE_5);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainBench_cube5_valley(GameTestHelper helper) {
      TerrainTestBench.runTest(helper, TestTerrain.VALLEY, TestBuilding.CUBE_5);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainBench_cube5_forest(GameTestHelper helper) {
      TerrainTestBench.runTest(helper, TestTerrain.FOREST, TestBuilding.CUBE_5);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainBench_cube5_cliffWest(GameTestHelper helper) {
      TerrainTestBench.runTest(helper, TestTerrain.CLIFF_WEST, TestBuilding.CUBE_5);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainBench_cube6_hillNorth(GameTestHelper helper) {
      TerrainTestBench.runTest(helper, TestTerrain.HILL_NORTH, TestBuilding.CUBE_6);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainBench_slabWide_flat(GameTestHelper helper) {
      TerrainTestBench.runTest(helper, TestTerrain.FLAT, TestBuilding.SLAB_WIDE);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainBench_slabWide_hillNorth(GameTestHelper helper) {
      TerrainTestBench.runTest(helper, TestTerrain.HILL_NORTH, TestBuilding.SLAB_WIDE);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainBench_tower_forest(GameTestHelper helper) {
      TerrainTestBench.runTest(helper, TestTerrain.FOREST, TestBuilding.TOWER);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainBench_tower_flat(GameTestHelper helper) {
      TerrainTestBench.runTest(helper, TestTerrain.FLAT, TestBuilding.TOWER);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainBench_cube5_riverEast(GameTestHelper helper) {
      TerrainTestBench.runTest(helper, TestTerrain.RIVER_EAST, TestBuilding.CUBE_5);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainBench_cube5_lakeAdjacent(GameTestHelper helper) {
      TerrainTestBench.runTest(helper, TestTerrain.LAKE_ADJACENT, TestBuilding.CUBE_5);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainBench_cube6_riverEast(GameTestHelper helper) {
      TerrainTestBench.runTest(helper, TestTerrain.RIVER_EAST, TestBuilding.CUBE_6);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainBench_slabWide_lakeAdjacent(GameTestHelper helper) {
      TerrainTestBench.runTest(helper, TestTerrain.LAKE_ADJACENT, TestBuilding.SLAB_WIDE);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainBench_slabWide_step(GameTestHelper helper) {
      TerrainTestBench.runTest(helper, TestTerrain.STEP, TestBuilding.SLAB_WIDE);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainBench_cube5_step(GameTestHelper helper) {
      TerrainTestBench.runTest(helper, TestTerrain.STEP, TestBuilding.CUBE_5);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainBench_cubeFloor_flat(GameTestHelper helper) {
      TerrainTestBench.runTest(helper, TestTerrain.FLAT, TestBuilding.CUBE_FLOOR);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainBench_cubeCellar_flat(GameTestHelper helper) {
      TerrainTestBench.runTest(helper, TestTerrain.FLAT, TestBuilding.CUBE_CELLAR);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainBench_cubeDeepCellar_flat(GameTestHelper helper) {
      TerrainTestBench.runTest(helper, TestTerrain.FLAT, TestBuilding.CUBE_DEEP_CELLAR);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainBench_cubeCellar_hillNorth(GameTestHelper helper) {
      TerrainTestBench.runTest(helper, TestTerrain.HILL_NORTH, TestBuilding.CUBE_CELLAR);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainBench_cubeDeepCellar_hillNorth(GameTestHelper helper) {
      TerrainTestBench.runTest(helper, TestTerrain.HILL_NORTH, TestBuilding.CUBE_DEEP_CELLAR);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainParity_flat_noRotation(GameTestHelper helper) {
      TerrainParityTest.runAndReport(helper, TestTerrain.FLAT, TestBuilding.PARITY_8x6, Rotation.NONE);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainParity_flat_clockwise90(GameTestHelper helper) {
      TerrainParityTest.runAndReport(helper, TestTerrain.FLAT, TestBuilding.PARITY_8x6, Rotation.CLOCKWISE_90);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainParity_hillNorth_noRotation(GameTestHelper helper) {
      TerrainParityTest.runAndReport(helper, TestTerrain.HILL_NORTH, TestBuilding.PARITY_8x6, Rotation.NONE);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainParity_forest_noRotation(GameTestHelper helper) {
      TerrainParityTest.runAndReport(helper, TestTerrain.FOREST, TestBuilding.PARITY_8x6, Rotation.NONE);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainParity_step_noRotation(GameTestHelper helper) {
      TerrainParityTest.runAndReport(helper, TestTerrain.STEP, TestBuilding.PARITY_8x6, Rotation.NONE);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainParity_cube5_noRotation(GameTestHelper helper) {
      TerrainParityTest.runAndReport(helper, TestTerrain.FLAT, TestBuilding.CUBE_5, Rotation.NONE);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testTerrainParity_cube5_clockwise90(GameTestHelper helper) {
      TerrainParityTest.runAndReport(helper, TestTerrain.FLAT, TestBuilding.CUBE_5, Rotation.CLOCKWISE_90);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testBrickColourRemapOnUpgrade(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));

      for (int x = -2; x < 10; x++) {
         for (int z = -2; z < 10; z++) {
            level.setBlock(new BlockPos(origin.getX() + x, origin.getY(), origin.getZ() + z), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
         }
      }

      ResourceLocation plan0Id = ResourceLocation.fromNamespaceAndPath("millenaire", "indian/hindushrine_a_0");
      BuildingPlan plan0 = ModCultures.getBuildingPlan(plan0Id);
      if (plan0 == null) {
         helper.fail("Plan hindushrine_a_0 not loaded");
      } else {
         ResourceLocation plan1Id = ResourceLocation.fromNamespaceAndPath("millenaire", "indian/hindushrine_a_1");
         BuildingPlan plan1 = ModCultures.getBuildingPlan(plan1Id);
         if (plan1 == null) {
            helper.fail("Plan hindushrine_a_1 not loaded");
         } else {
            BuildingInstance instance = new BuildingInstance(
               BuildingId.random(),
               plan0.id(),
               origin,
               Rotation.NONE,
               BuildingInstance.Status.COMPLETE,
               ResourceLocation.fromNamespaceAndPath("millenaire", "indian/hindushrine"),
               "a",
               0
            );
            Map<DyeColor, DyeColor> mapping = new EnumMap<>(DyeColor.class);

            for (DyeColor c : DyeColor.values()) {
               mapping.put(c, c);
            }

            mapping.put(DyeColor.WHITE, DyeColor.YELLOW);
            mapping.put(DyeColor.LIGHT_BLUE, DyeColor.RED);
            instance.setBrickColourMapping(mapping);
            BuildingPlacer.placeInstantly(level, plan0, origin, Rotation.NONE, instance);
            int whiteCount = 0;
            int yellowCount = 0;

            for (int x = 0; x < 6; x++) {
               for (int y = 0; y < 5; y++) {
                  for (int z = 0; z < 6; z++) {
                     BlockPos pos = new BlockPos(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                     BlockState state = level.getBlockState(pos);
                     if (state.getBlock() instanceof IPaintedBlock painted) {
                        if (painted.getColor() == DyeColor.WHITE) {
                           whiteCount++;
                        }

                        if (painted.getColor() == DyeColor.YELLOW) {
                           yellowCount++;
                        }
                     }
                  }
               }
            }

            if (whiteCount > 0 && yellowCount == 0) {
               helper.fail("L0: painted_brick_white not remapped — found " + whiteCount + " WHITE, 0 YELLOW");
            } else {
               BuildingPlacer.placeUpgradeInstantly(level, plan1, origin, Rotation.NONE, instance);
               int whiteAfterUpgrade = 0;
               int yellowAfterUpgrade = 0;
               int redAfterUpgrade = 0;

               for (int x = 0; x < 6; x++) {
                  for (int y = 0; y < 5; y++) {
                     for (int z = 0; z < 6; z++) {
                        BlockPos pos = new BlockPos(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                        BlockState state = level.getBlockState(pos);
                        if (state.getBlock() instanceof IPaintedBlock painted) {
                           if (painted.getColor() == DyeColor.WHITE) {
                              whiteAfterUpgrade++;
                           }

                           if (painted.getColor() == DyeColor.YELLOW) {
                              yellowAfterUpgrade++;
                           }

                           if (painted.getColor() == DyeColor.RED) {
                              redAfterUpgrade++;
                           }
                        }
                     }
                  }
               }

               if (whiteAfterUpgrade > 0) {
                  helper.fail("After upgrade: " + whiteAfterUpgrade + " WHITE bricks remain (should be 0, remapped to YELLOW)");
               } else {
                  int remappedTotal = yellowAfterUpgrade + redAfterUpgrade;
                  if (remappedTotal == 0) {
                     helper.fail("After upgrade: no remapped bricks found (expected YELLOW or RED)");
                  } else {
                     helper.succeed();
                  }
               }
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testSnailSoilGrowthWithWater(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 5));
      level.setBlock(pos, (BlockState)((BlockSnailSoil)ModBlocks.SNAIL_SOIL.get()).defaultBlockState().setValue(BlockSnailSoil.AGE, 0), 3);
      level.setBlock(pos.above(), Blocks.WATER.defaultBlockState(), 3);
      level.setBlock(pos.above(2), Blocks.GLASS.defaultBlockState(), 3);
      RandomSource random = RandomSource.create(42L);

      for (int i = 0; i < 200; i++) {
         BlockState current = level.getBlockState(pos);
         if (current.is((Block)ModBlocks.SNAIL_SOIL.get())) {
            current.randomTick(level, pos, random);
         }
      }

      int finalAge = (Integer)level.getBlockState(pos).getValue(BlockSnailSoil.AGE);
      if (finalAge != 3) {
         helper.fail("SnailSoil did not reach full growth (age 3) with water above after 200 ticks, age=" + finalAge);
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testSnailSoilNoGrowthWithoutWater(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 5));
      level.setBlock(pos, (BlockState)((BlockSnailSoil)ModBlocks.SNAIL_SOIL.get()).defaultBlockState().setValue(BlockSnailSoil.AGE, 0), 3);
      level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
      RandomSource random = RandomSource.create(42L);

      for (int i = 0; i < 100; i++) {
         BlockState current = level.getBlockState(pos);
         if (current.is((Block)ModBlocks.SNAIL_SOIL.get())) {
            current.randomTick(level, pos, random);
         }
      }

      int finalAge = (Integer)level.getBlockState(pos).getValue(BlockSnailSoil.AGE);
      if (finalAge != 0) {
         helper.fail("SnailSoil grew without water! Age = " + finalAge);
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testGrapeVineDoubleHeight(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos farmlandPos = helper.absolutePos(new BlockPos(5, 1, 5));
      BlockPos lowerPos = farmlandPos.above();
      BlockPos upperPos = lowerPos.above();
      level.setBlock(farmlandPos, Blocks.FARMLAND.defaultBlockState(), 3);
      level.setBlock(upperPos, Blocks.AIR.defaultBlockState(), 3);
      level.setBlock(upperPos.above(), Blocks.AIR.defaultBlockState(), 3);
      level.setBlock(
         lowerPos,
         (BlockState)((BlockState)((BlockGrapeVine)ModBlocks.CROP_VINE.get()).defaultBlockState().setValue(BlockGrapeVine.AGE, 0))
            .setValue(BlockGrapeVine.HALF, Half.BOTTOM),
         3
      );
      BlockState lower = level.getBlockState(lowerPos);
      if (!lower.is((Block)ModBlocks.CROP_VINE.get())) {
         helper.fail("GrapeVine lower half not placed on farmland");
      } else {
         RandomSource random = RandomSource.create(42L);

         for (int i = 0; i < 500; i++) {
            BlockState current = level.getBlockState(lowerPos);
            if (current.is((Block)ModBlocks.CROP_VINE.get())) {
               current.randomTick(level, lowerPos, random);
            }
         }

         BlockState lowerAfter = level.getBlockState(lowerPos);
         if (!lowerAfter.is((Block)ModBlocks.CROP_VINE.get())) {
            helper.fail("GrapeVine lower half disappeared during growth");
         } else {
            int lowerAge = (Integer)lowerAfter.getValue(BlockGrapeVine.AGE);
            if (lowerAge < 2) {
               helper.fail("GrapeVine did not grow to age 2+ after 500 ticks, age = " + lowerAge);
            } else {
               BlockState upper = level.getBlockState(upperPos);
               if (!upper.is((Block)ModBlocks.CROP_VINE.get())) {
                  helper.fail("GrapeVine upper half not spawned at age " + lowerAge);
               } else {
                  int upperAge = (Integer)upper.getValue(BlockGrapeVine.AGE);
                  if (upperAge != lowerAge) {
                     helper.fail("GrapeVine age mismatch: lower=" + lowerAge + " upper=" + upperAge);
                  } else {
                     level.setBlock(lowerPos, Blocks.AIR.defaultBlockState(), 3);
                     BlockState upperAfterBreak = level.getBlockState(upperPos);
                     if (upperAfterBreak.is((Block)ModBlocks.CROP_VINE.get())) {
                        helper.fail("GrapeVine upper half survived after lower was broken");
                     } else {
                        helper.succeed();
                     }
                  }
               }
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void testOliveTreeLeavesMature(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 5));
      level.setBlock(
         pos,
         (BlockState)((BlockState)((OliveTreeLeavesBlock)ModBlocks.OLIVE_TREE_LEAVES.get()).defaultBlockState().setValue(OliveTreeLeavesBlock.AGE, 0))
            .setValue(LeavesBlock.PERSISTENT, true),
         3
      );
      RandomSource random = RandomSource.create(42L);
      level.setDayTime(4000L);

      for (int i = 0; i < 50; i++) {
         BlockState current = level.getBlockState(pos);
         if (current.is((Block)ModBlocks.OLIVE_TREE_LEAVES.get())) {
            current.randomTick(level, pos, random);
         }
      }

      level.setDayTime(5500L);

      for (int i = 0; i < 50; i++) {
         BlockState current = level.getBlockState(pos);
         if (current.is((Block)ModBlocks.OLIVE_TREE_LEAVES.get())) {
            current.randomTick(level, pos, random);
         }
      }

      level.setDayTime(7000L);

      for (int i = 0; i < 50; i++) {
         BlockState current = level.getBlockState(pos);
         if (current.is((Block)ModBlocks.OLIVE_TREE_LEAVES.get())) {
            current.randomTick(level, pos, random);
         }
      }

      BlockState after = level.getBlockState(pos);
      if (!after.is((Block)ModBlocks.OLIVE_TREE_LEAVES.get())) {
         helper.fail("Olive leaves disappeared");
      } else {
         int finalAge = (Integer)after.getValue(OliveTreeLeavesBlock.AGE);
         if (finalAge != 3) {
            helper.fail("Olive leaves did not reach full maturity (age 3), age=" + finalAge + " (world-time driven)");
         } else {
            helper.succeed();
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testByzantineCultureLoaded(GameTestHelper helper) {
      ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", "byzantines");
      Culture culture = ModCultures.getCulture(cultureId);
      if (culture == null) {
         helper.fail("Byzantine culture not loaded in ModCultures");
      } else {
         ResourceLocation churchSetId = ResourceLocation.fromNamespaceAndPath("millenaire", "byzantines/church");
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(churchSetId);
         if (planSet == null) {
            long count = ModCultures.getAllBuildingPlanSets().keySet().stream().filter(k -> k.getPath().startsWith("byzantines")).count();
            helper.fail("Byzantine plan set 'byzantines/church' not loaded (total byz sets: " + count + ")");
         } else {
            ResourceLocation workerId = ResourceLocation.fromNamespaceAndPath("millenaire", "byzantines/worker_byzantine");
            VillagerType villagerType = ModCultures.getVillagerType(workerId);
            if (villagerType == null) {
               long count = ModCultures.getAllVillagerTypes().keySet().stream().filter(k -> k.getPath().startsWith("byzantines")).count();
               helper.fail("Byzantine villager type 'byzantines/worker_byzantine' not loaded (total byz types: " + count + ")");
            } else {
               helper.succeed();
            }
         }
      }
   }
}
