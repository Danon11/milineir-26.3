package org.millenaire.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.gametest.GameTestPlayer;
import org.millenaire.block.ModBlocks;
import org.millenaire.block.VillagePanelBlock;
import org.millenaire.block.VillagePanelBlockEntity;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.panel.PanelType;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class VillagePanelGameTests {
   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testPanel_linkedToBuilding(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos panelPos = helper.absolutePos(new BlockPos(3, 1, 3));
      level.setBlock(panelPos, ((VillagePanelBlock)ModBlocks.VILLAGE_PANEL.get()).defaultBlockState(), 3);
      if (level.getBlockEntity(panelPos) instanceof VillagePanelBlockEntity panel) {
         BlockPos var14 = helper.absolutePos(new BlockPos(5, 1, 5));
         VillageId villageId = VillageId.random();
         ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman");
         ResourceLocation villageTypeId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/agricole");
         Village village = new Village(villageId, cultureId, villageTypeId, var14);
         village.setVillageName("TestVillage");
         BuildingInstance building = new BuildingInstance(
            BuildingId.random(),
            ResourceLocation.fromNamespaceAndPath("millenaire", "test_building"),
            panelPos,
            Rotation.NONE,
            BuildingInstance.Status.COMPLETE,
            ResourceLocation.fromNamespaceAndPath("millenaire", "test/building"),
            "a",
            0
         );
         village.addBuilding(building);
         panel.setBuildingId(building.getId());
         panel.setPanelType(PanelType.VILLAGE_SUMMARY);
         VillageSavedData.get(level).getVillageManager().addVillage(village);
         GameTestPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
         player.moveTo(panelPos.getX(), panelPos.getY(), panelPos.getZ());
         BlockState state = level.getBlockState(panelPos);
         BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(panelPos), Direction.SOUTH, panelPos, false);
         InteractionResult result = state.useWithoutItem(level, player, hit);
         if (result != InteractionResult.SUCCESS) {
            helper.fail("Expected SUCCESS but got: " + result);
         } else {
            helper.succeed();
         }
      } else {
         helper.fail("No VillagePanelBlockEntity at expected position");
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testPanel_unlinkedSendsError(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos panelPos = helper.absolutePos(new BlockPos(3, 1, 3));
      level.setBlock(panelPos, ((VillagePanelBlock)ModBlocks.VILLAGE_PANEL.get()).defaultBlockState(), 3);
      if (level.getBlockEntity(panelPos) instanceof VillagePanelBlockEntity panel) {
         if (panel.getBuildingId() != null) {
            helper.fail("Panel should have no buildingId by default");
         } else {
            GameTestPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
            player.moveTo(panelPos.getX(), panelPos.getY(), panelPos.getZ());
            BlockState state = level.getBlockState(panelPos);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(panelPos), Direction.SOUTH, panelPos, false);
            InteractionResult result = state.useWithoutItem(level, player, hit);
            if (result != InteractionResult.SUCCESS) {
               helper.fail("Expected SUCCESS even for unlinked panel but got: " + result);
            } else {
               helper.succeed();
            }
         }
      } else {
         helper.fail("No VillagePanelBlockEntity at expected position");
      }
   }
}
