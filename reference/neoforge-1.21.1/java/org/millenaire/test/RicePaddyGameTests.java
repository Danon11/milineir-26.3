package org.millenaire.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.gametest.GameTestPlayer;
import org.millenaire.block.ModBlocks;
import org.millenaire.block.RicePaddyBlock;
import org.millenaire.item.ModItems;
import org.millenaire.village.PlayerCultureReputation;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class RicePaddyGameTests {
   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testRicePaddy_plantViaUseItemOn(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 5));
      level.setBlock(pos, (BlockState)((Block)ModBlocks.RICE_PADDY.get()).defaultBlockState().setValue(RicePaddyBlock.PLANTED, false), 3);
      GameTestPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(pos.getX(), pos.getY() + 1, pos.getZ());
      PlayerCultureReputation.get(level).learnCrop(player.getUUID(), "rice");
      ItemStack rice = new ItemStack((ItemLike)ModItems.RICE.get(), 3);
      player.setItemInHand(InteractionHand.MAIN_HAND, rice);
      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
      BlockState state = level.getBlockState(pos);
      ItemInteractionResult result = state.useItemOn(rice, level, player, InteractionHand.MAIN_HAND, hit);
      if (result != ItemInteractionResult.SUCCESS) {
         helper.fail("Expected SUCCESS when planting rice on empty paddy, got " + result);
      } else {
         BlockState after = level.getBlockState(pos);
         if (!(Boolean)after.getValue(RicePaddyBlock.PLANTED)) {
            helper.fail("Block should have planted=true after planting");
         } else if ((Integer)after.getValue(RicePaddyBlock.AGE) != 0) {
            helper.fail("Block should have age=0 after planting, got " + after.getValue(RicePaddyBlock.AGE));
         } else if (player.getItemInHand(InteractionHand.MAIN_HAND).getCount() != 2) {
            helper.fail("Rice stack should have been shrunk by 1 (3 → 2), got " + player.getItemInHand(InteractionHand.MAIN_HAND).getCount());
         } else {
            helper.succeed();
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testRicePaddy_cannotPlantOnPlanted(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 5));
      level.setBlock(
         pos,
         (BlockState)((BlockState)((Block)ModBlocks.RICE_PADDY.get()).defaultBlockState().setValue(RicePaddyBlock.PLANTED, true))
            .setValue(RicePaddyBlock.AGE, 3),
         3
      );
      GameTestPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(pos.getX(), pos.getY() + 1, pos.getZ());
      ItemStack rice = new ItemStack((ItemLike)ModItems.RICE.get(), 2);
      player.setItemInHand(InteractionHand.MAIN_HAND, rice);
      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
      BlockState state = level.getBlockState(pos);
      ItemInteractionResult result = state.useItemOn(rice, level, player, InteractionHand.MAIN_HAND, hit);
      if (result != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
         helper.fail("Expected PASS when paddy is already planted, got " + result);
      } else {
         BlockState after = level.getBlockState(pos);
         if ((Integer)after.getValue(RicePaddyBlock.AGE) != 3) {
            helper.fail("Age should remain 3, got " + after.getValue(RicePaddyBlock.AGE));
         } else if (player.getItemInHand(InteractionHand.MAIN_HAND).getCount() != 2) {
            helper.fail("Rice should not be consumed when PASS is returned");
         } else {
            helper.succeed();
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testRicePaddy_wrongItemDoesNotPlant(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 5));
      level.setBlock(pos, (BlockState)((Block)ModBlocks.RICE_PADDY.get()).defaultBlockState().setValue(RicePaddyBlock.PLANTED, false), 3);
      GameTestPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(pos.getX(), pos.getY() + 1, pos.getZ());
      ItemStack wheatSeeds = new ItemStack(Items.WHEAT_SEEDS, 1);
      player.setItemInHand(InteractionHand.MAIN_HAND, wheatSeeds);
      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
      BlockState state = level.getBlockState(pos);
      ItemInteractionResult result = state.useItemOn(wheatSeeds, level, player, InteractionHand.MAIN_HAND, hit);
      if (result != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
         helper.fail("Expected PASS when using wrong item on empty paddy, got " + result);
      } else {
         BlockState after = level.getBlockState(pos);
         if ((Boolean)after.getValue(RicePaddyBlock.PLANTED)) {
            helper.fail("Paddy should remain unplanted after using wrong item");
         } else {
            helper.succeed();
         }
      }
   }
}
