package org.millenaire.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.gametest.GameTestPlayer;
import org.millenaire.item.ModItems;
import org.millenaire.item.NegationWandItem;
import org.millenaire.network.NegationWandConfirmPayload;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class NegationWandGameTests {
   private static final ResourceLocation CULTURE_ID = ResourceLocation.fromNamespaceAndPath("millenaire", "norman");
   private static final ResourceLocation VILLAGE_TYPE_ID = ResourceLocation.fromNamespaceAndPath("millenaire", "norman_agricole");

   private static Village createVillage(ServerLevel level, BlockPos center) {
      Village village = new Village(VillageId.random(), CULTURE_ID, VILLAGE_TYPE_ID, center);
      village.setVillageName("TestVillage");
      VillageSavedData.get(level).getVillageManager().addVillage(village);
      return village;
   }

   private static InteractionResult wandClick(GameTestHelper helper, GameTestPlayer player, BlockPos pos) {
      ItemStack wand = new ItemStack((ItemLike)ModItems.NEGATION_WAND.get());
      player.setItemInHand(InteractionHand.MAIN_HAND, wand);
      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
      UseOnContext ctx = new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, wand, hit);
      return ((NegationWandItem)ModItems.NEGATION_WAND.get()).useOn(ctx);
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testNegationWand_firstClickNoDeletion(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos center = helper.absolutePos(new BlockPos(5, 1, 5));
      Village village = createVillage(level, center);
      village.setChestLocked(false);
      VillageId villageId = village.getId();
      GameTestPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(center.getX(), center.getY(), center.getZ());
      InteractionResult result = wandClick(helper, player, center);
      if (result != InteractionResult.SUCCESS) {
         helper.fail("Expected SUCCESS on first click, got " + result);
      } else {
         Village stillExists = Village.resolve(level, villageId);
         if (stillExists == null) {
            helper.fail("Village was deleted on first click — expected only a warning");
         } else {
            helper.succeed();
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testNegationWand_confirmDeletesVillage(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos center = helper.absolutePos(new BlockPos(5, 1, 5));
      Village village = createVillage(level, center);
      village.setChestLocked(false);
      VillageId villageId = village.getId();
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(center.getX(), center.getY(), center.getZ());
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack((ItemLike)ModItems.NEGATION_WAND.get()));
      NegationWandConfirmPayload payload = new NegationWandConfirmPayload(villageId.uuid().toString());
      NegationWandConfirmPayload.handleOnServer(payload, player);
      Village shouldBeGone = Village.resolve(level, villageId);
      if (shouldBeGone != null) {
         helper.fail("Village was not deleted after confirm payload with wand");
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testNegationWand_deletionCleansUpRelations(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos centerA = helper.absolutePos(new BlockPos(2, 1, 5));
      BlockPos centerB = helper.absolutePos(new BlockPos(14, 1, 5));
      Village villageA = createVillage(level, centerA);
      Village villageB = createVillage(level, centerB);
      villageA.setChestLocked(false);
      villageB.setChestLocked(false);
      villageB.setParentVillageId(villageA.getId());
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(centerA.getX(), centerA.getY(), centerA.getZ());
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack((ItemLike)ModItems.NEGATION_WAND.get()));
      NegationWandConfirmPayload payload = new NegationWandConfirmPayload(villageA.getId().uuid().toString());
      NegationWandConfirmPayload.handleOnServer(payload, player);
      if (Village.resolve(level, villageA.getId()) != null) {
         helper.fail("Village A was not deleted");
      } else {
         Village remainingB = Village.resolve(level, villageB.getId());
         if (remainingB == null) {
            helper.fail("Village B was deleted — only A should have been removed");
         } else if (remainingB.getParentVillageId() != null) {
            helper.fail("Village B still has parentVillageId after A was deleted — ARCH-10 cleanup failed");
         } else {
            helper.succeed();
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testNegationWand_noVillageInRange(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos farCenter = helper.absolutePos(new BlockPos(50, 1, 50));
      createVillage(level, farCenter);
      BlockPos clickPos = helper.absolutePos(new BlockPos(5, 1, 5));
      GameTestPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(clickPos.getX(), clickPos.getY(), clickPos.getZ());
      InteractionResult result = wandClick(helper, player, clickPos);
      if (result != InteractionResult.FAIL) {
         helper.fail("Expected FAIL when no village in range, got " + result);
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testNegationWand_lockedVillageBlocked(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos center = helper.absolutePos(new BlockPos(5, 1, 5));
      Village village = createVillage(level, center);
      VillageId villageId = village.getId();
      village.setChestLocked(true);
      if (!village.areChestsLocked()) {
         helper.fail("Precondition failed: areChestsLocked() should be true");
      } else {
         GameTestPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
         player.moveTo(center.getX(), center.getY(), center.getZ());
         InteractionResult result = wandClick(helper, player, center);
         if (result != InteractionResult.SUCCESS) {
            helper.fail("Expected SUCCESS (locked message) on click, got " + result);
         } else {
            Village stillExists = Village.resolve(level, villageId);
            if (stillExists == null) {
               helper.fail("Village was deleted despite chests being locked");
            } else {
               InteractionResult result2 = wandClick(helper, player, center);
               Village stillExists2 = Village.resolve(level, villageId);
               if (stillExists2 == null) {
                  helper.fail("Village was deleted on second click despite chests being locked");
               } else {
                  helper.succeed();
               }
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testConfirmRejectedWithoutWand(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos center = helper.absolutePos(new BlockPos(5, 1, 5));
      Village village = createVillage(level, center);
      village.setChestLocked(false);
      VillageId villageId = village.getId();
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(center.getX(), center.getY(), center.getZ());
      player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
      player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
      NegationWandConfirmPayload payload = new NegationWandConfirmPayload(villageId.uuid().toString());
      NegationWandConfirmPayload.handleOnServer(payload, player);
      Village shouldStillExist = Village.resolve(level, villageId);
      if (shouldStillExist == null) {
         helper.fail("Village was deleted by confirm payload even though player had no wand");
      } else {
         helper.succeed();
      }
   }
}
