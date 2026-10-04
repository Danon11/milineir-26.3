package org.millenaire.test;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.gametest.GameTestPlayer;
import org.millenaire.block.LockedChestBlock;
import org.millenaire.block.LockedChestBlockEntity;
import org.millenaire.block.ModBlocks;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class LockedChestGameTests {
   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200, required = false)
   public static void testLockedChest_lockedWithInhabitants(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos chestPos = helper.absolutePos(new BlockPos(3, 1, 3));
      level.setBlock(chestPos, ((LockedChestBlock)ModBlocks.LOCKED_CHEST.get()).defaultBlockState(), 3);
      BlockPos center = helper.absolutePos(new BlockPos(5, 1, 5));
      VillageId villageId = VillageId.random();
      ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman");
      ResourceLocation villageTypeId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/agricole");
      Village village = new Village(villageId, cultureId, villageTypeId, center);
      village.setVillageName("TestVillage");
      BuildingId buildingId = BuildingId.random();
      BuildingInstance building = new BuildingInstance(
         buildingId,
         ResourceLocation.fromNamespaceAndPath("millenaire", "test_building"),
         chestPos,
         Rotation.NONE,
         BuildingInstance.Status.COMPLETE,
         ResourceLocation.fromNamespaceAndPath("millenaire", "test/building"),
         "a",
         0
      );
      village.addBuilding(building);
      village.addVillager(UUID.randomUUID(), ResourceLocation.fromNamespaceAndPath("millenaire", "norman/farmer"), buildingId);
      village.setChestLocked(true);
      VillageSavedData.get(level).getVillageManager().addVillage(village);
      if (level.getBlockEntity(chestPos) instanceof LockedChestBlockEntity chestEntity) {
         chestEntity.setBuildingId(building.getId());
         GameTestPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
         player.moveTo(chestPos.getX(), chestPos.getY(), chestPos.getZ());
         if (!chestEntity.isLockedFor(player)) {
            helper.fail("Chest should report locked (building has inhabitants)");
         } else {
            helper.succeed();
         }
      } else {
         helper.fail("No LockedChestBlockEntity at expected position");
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200, required = false)
   public static void testLockedChest_unlockedWithoutInhabitants(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos chestPos = helper.absolutePos(new BlockPos(3, 1, 3));
      level.setBlock(chestPos, ((LockedChestBlock)ModBlocks.LOCKED_CHEST.get()).defaultBlockState(), 3);
      BlockPos center = helper.absolutePos(new BlockPos(5, 1, 5));
      VillageId villageId = VillageId.random();
      ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman");
      ResourceLocation villageTypeId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/agricole");
      Village village = new Village(villageId, cultureId, villageTypeId, center);
      village.setVillageName("TestVillage");
      BuildingInstance building = new BuildingInstance(
         BuildingId.random(),
         ResourceLocation.fromNamespaceAndPath("millenaire", "test_building"),
         chestPos,
         Rotation.NONE,
         BuildingInstance.Status.COMPLETE,
         ResourceLocation.fromNamespaceAndPath("millenaire", "test/building"),
         "a",
         0
      );
      village.addBuilding(building);
      VillageSavedData.get(level).getVillageManager().addVillage(village);
      if (level.getBlockEntity(chestPos) instanceof LockedChestBlockEntity chestEntity) {
         chestEntity.setBuildingId(building.getId());
         GameTestPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
         player.moveTo(chestPos.getX(), chestPos.getY(), chestPos.getZ());
         BlockState blockState = level.getBlockState(chestPos);
         blockState.useWithoutItem(level, player, BlockHitResult.miss(Vec3.atCenterOf(chestPos), Direction.NORTH, chestPos));
         if (player.containerMenu == player.inventoryMenu) {
            helper.fail("Chest should be unlocked (village has no villagers), but menu did not open");
         } else {
            helper.succeed();
         }
      } else {
         helper.fail("No LockedChestBlockEntity at expected position");
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200, required = false)
   public static void testLockedChest_doubleChestBothLocked(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos chestPosA = helper.absolutePos(new BlockPos(3, 1, 3));
      BlockPos chestPosB = helper.absolutePos(new BlockPos(4, 1, 3));
      level.setBlock(chestPosA, ((LockedChestBlock)ModBlocks.LOCKED_CHEST.get()).defaultBlockState(), 3);
      level.setBlock(chestPosB, ((LockedChestBlock)ModBlocks.LOCKED_CHEST.get()).defaultBlockState(), 3);
      BlockPos center = helper.absolutePos(new BlockPos(5, 1, 5));
      VillageId villageId = VillageId.random();
      ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman");
      ResourceLocation villageTypeId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/agricole");
      Village village = new Village(villageId, cultureId, villageTypeId, center);
      village.setVillageName("TestVillage");
      BuildingId buildingIdA = BuildingId.random();
      BuildingInstance buildingA = new BuildingInstance(
         buildingIdA,
         ResourceLocation.fromNamespaceAndPath("millenaire", "test_building_a"),
         chestPosA,
         Rotation.NONE,
         BuildingInstance.Status.COMPLETE,
         ResourceLocation.fromNamespaceAndPath("millenaire", "test/building_a"),
         "a",
         0
      );
      village.addBuilding(buildingA);
      village.addVillager(UUID.randomUUID(), ResourceLocation.fromNamespaceAndPath("millenaire", "norman/farmer"), buildingIdA);
      BuildingId buildingIdB = BuildingId.random();
      BuildingInstance buildingB = new BuildingInstance(
         buildingIdB,
         ResourceLocation.fromNamespaceAndPath("millenaire", "test_building_b"),
         chestPosB,
         Rotation.NONE,
         BuildingInstance.Status.COMPLETE,
         ResourceLocation.fromNamespaceAndPath("millenaire", "test/building_b"),
         "a",
         0
      );
      village.addBuilding(buildingB);
      village.setChestLocked(true);
      VillageSavedData.get(level).getVillageManager().addVillage(village);
      if (level.getBlockEntity(chestPosA) instanceof LockedChestBlockEntity chestA) {
         chestA.setBuildingId(buildingIdA);
         if (level.getBlockEntity(chestPosB) instanceof LockedChestBlockEntity chestB) {
            chestB.setBuildingId(buildingIdB);
            GameTestPlayer var17 = GameTestPlayers.create(helper, GameType.SURVIVAL);
            if (!chestA.isLockedFor(var17)) {
               helper.fail("Chest A should be locked (village has live villagers)");
            } else if (!chestB.isLockedFor(var17)) {
               helper.fail("Chest B should also be locked (village has live villagers, lock is village-wide)");
            } else {
               helper.succeed();
            }
         } else {
            helper.fail("No LockedChestBlockEntity at position B");
         }
      } else {
         helper.fail("No LockedChestBlockEntity at position A");
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200, required = false)
   public static void testLockedChest_unlockedWithoutVillage(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos chestPos = helper.absolutePos(new BlockPos(3, 1, 3));
      level.setBlock(chestPos, ((LockedChestBlock)ModBlocks.LOCKED_CHEST.get()).defaultBlockState(), 3);
      if (!(level.getBlockEntity(chestPos) instanceof LockedChestBlockEntity)) {
         helper.fail("No LockedChestBlockEntity at expected position");
      } else {
         GameTestPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
         player.moveTo(chestPos.getX(), chestPos.getY(), chestPos.getZ());
         BlockState blockState = level.getBlockState(chestPos);
         blockState.useWithoutItem(level, player, BlockHitResult.miss(Vec3.atCenterOf(chestPos), Direction.NORTH, chestPos));
         if (player.containerMenu == player.inventoryMenu) {
            helper.fail("Chest without buildingId should always open, but menu did not open");
         } else {
            helper.succeed();
         }
      }
   }
}
