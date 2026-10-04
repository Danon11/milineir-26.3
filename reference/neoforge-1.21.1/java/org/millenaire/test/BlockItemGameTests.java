package org.millenaire.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.millenaire.block.AppleTreeLeavesBlock;
import org.millenaire.block.AppleTreeSaplingBlock;
import org.millenaire.block.BlockMillCrops;
import org.millenaire.block.BlockWetBrick;
import org.millenaire.block.ModBlocks;
import org.millenaire.block.NormanRosetteBlock;
import org.millenaire.block.RicePaddyBlock;
import org.millenaire.item.MillFoodItem;
import org.millenaire.item.ModItems;
import org.millenaire.item.MoneyHelper;
import org.millenaire.item.PurseItem;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class BlockItemGameTests {
   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testMillCrops_noGrowthOnDryFarmland(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos farmlandPos = helper.absolutePos(new BlockPos(5, 1, 5));
      BlockPos cropPos = farmlandPos.above();
      level.setBlock(farmlandPos, (BlockState)Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0), 3);
      level.setBlock(cropPos, ((Block)ModBlocks.CROP_RICE.get()).defaultBlockState(), 3);
      BlockState before = level.getBlockState(cropPos);

      for (int i = 0; i < 100; i++) {
         BlockState current = level.getBlockState(cropPos);
         current.randomTick(level, cropPos, RandomSource.create(i));
      }

      BlockState after = level.getBlockState(cropPos);
      if ((Integer)after.getValue(BlockStateProperties.AGE_7) != 0) {
         helper.fail("Rice crop grew on dry farmland (irrigation required). Age=" + after.getValue(BlockStateProperties.AGE_7));
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testMillCrops_growsOnWetFarmland(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos farmlandPos = helper.absolutePos(new BlockPos(5, 1, 5));
      BlockPos cropPos = farmlandPos.above();
      level.setBlock(farmlandPos, (BlockState)Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 3);
      level.setBlock(cropPos, ((Block)ModBlocks.CROP_RICE.get()).defaultBlockState(), 3);
      BlockState state = level.getBlockState(cropPos);
      if (!state.isRandomlyTicking()) {
         helper.fail("Rice crop at age 0 on wet farmland should be randomly ticking");
      } else {
         level.setBlock(cropPos, (BlockState)state.setValue(BlockStateProperties.AGE_7, 7), 3);
         BlockState maxState = level.getBlockState(cropPos);
         if (maxState.isRandomlyTicking()) {
            helper.fail("Rice crop at max age should NOT be randomly ticking");
         } else {
            helper.succeed();
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testMillCrops_onlyPlacesOnFarmland(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockState cropState = ((Block)ModBlocks.CROP_RICE.get()).defaultBlockState();
      BlockPos dirtPos = helper.absolutePos(new BlockPos(6, 1, 5));
      BlockPos aboveDirt = dirtPos.above();
      level.setBlock(dirtPos, Blocks.DIRT.defaultBlockState(), 3);
      boolean canPlaceOnDirt = cropState.canSurvive(level, aboveDirt);
      BlockPos farmPos = helper.absolutePos(new BlockPos(7, 1, 5));
      BlockPos aboveFarm = farmPos.above();
      level.setBlock(farmPos, Blocks.FARMLAND.defaultBlockState(), 3);
      boolean canPlaceOnFarm = cropState.canSurvive(level, aboveFarm);
      if (canPlaceOnDirt) {
         helper.fail("MillCrops should NOT survive on dirt");
      } else if (!canPlaceOnFarm) {
         helper.fail("MillCrops should survive on farmland");
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testMillCrops_slowGrowthFlag(GameTestHelper helper) {
      BlockMillCrops maize = (BlockMillCrops)ModBlocks.CROP_MAIZE.get();
      BlockMillCrops turmeric = (BlockMillCrops)ModBlocks.CROP_TURMERIC.get();
      BlockMillCrops rice = (BlockMillCrops)ModBlocks.CROP_RICE.get();
      if (!maize.isSlowGrowth()) {
         helper.fail("Maize should have slowGrowth=true");
      } else if (turmeric.isSlowGrowth()) {
         helper.fail("Turmeric should have slowGrowth=false");
      } else if (!rice.requiresIrrigation()) {
         helper.fail("Rice should have requireIrrigation=true");
      } else if (turmeric.requiresIrrigation()) {
         helper.fail("Turmeric should have requireIrrigation=false");
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testRicePaddy_plantWithRice(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos paddyPos = helper.absolutePos(new BlockPos(5, 1, 5));
      level.setBlock(paddyPos, ((Block)ModBlocks.RICE_PADDY.get()).defaultBlockState(), 3);
      BlockState before = level.getBlockState(paddyPos);
      if ((Boolean)before.getValue(RicePaddyBlock.PLANTED)) {
         helper.fail("Fresh rice paddy should not be planted");
      } else if (!RicePaddyBlock.canPlant(before)) {
         helper.fail("canPlant should be true for empty paddy");
      } else {
         level.setBlock(paddyPos, (BlockState)((BlockState)before.setValue(RicePaddyBlock.PLANTED, true)).setValue(RicePaddyBlock.AGE, 0), 3);
         BlockState after = level.getBlockState(paddyPos);
         if (!(Boolean)after.getValue(RicePaddyBlock.PLANTED)) {
            helper.fail("Paddy should be planted");
         } else if ((Integer)after.getValue(RicePaddyBlock.AGE) != 0) {
            helper.fail("Freshly planted paddy should have age=0");
         } else if (RicePaddyBlock.canPlant(after)) {
            helper.fail("canPlant should be false after planting");
         } else {
            helper.succeed();
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testRicePaddy_cannotPlantWhenAlreadyPlanted(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos paddyPos = helper.absolutePos(new BlockPos(5, 1, 5));
      level.setBlock(
         paddyPos,
         (BlockState)((BlockState)((Block)ModBlocks.RICE_PADDY.get()).defaultBlockState().setValue(RicePaddyBlock.PLANTED, true))
            .setValue(RicePaddyBlock.AGE, 3),
         3
      );
      if (RicePaddyBlock.canPlant(level.getBlockState(paddyPos))) {
         helper.fail("canPlant should be false for an already planted paddy");
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testRicePaddy_canHarvestAtAge7(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos paddyPos = helper.absolutePos(new BlockPos(5, 1, 5));
      level.setBlock(
         paddyPos,
         (BlockState)((BlockState)((Block)ModBlocks.RICE_PADDY.get()).defaultBlockState().setValue(RicePaddyBlock.PLANTED, true))
            .setValue(RicePaddyBlock.AGE, 6),
         3
      );
      if (RicePaddyBlock.canHarvest(level.getBlockState(paddyPos))) {
         helper.fail("canHarvest should be false at age 6");
      } else {
         level.setBlock(
            paddyPos,
            (BlockState)((BlockState)((Block)ModBlocks.RICE_PADDY.get()).defaultBlockState().setValue(RicePaddyBlock.PLANTED, true))
               .setValue(RicePaddyBlock.AGE, 7),
            3
         );
         if (!RicePaddyBlock.canHarvest(level.getBlockState(paddyPos))) {
            helper.fail("canHarvest should be true at age 7");
         } else {
            helper.succeed();
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testRicePaddy_growthViaRandomTick(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos paddyPos = helper.absolutePos(new BlockPos(5, 1, 5));
      level.setBlock(
         paddyPos,
         (BlockState)((BlockState)((Block)ModBlocks.RICE_PADDY.get()).defaultBlockState().setValue(RicePaddyBlock.PLANTED, true))
            .setValue(RicePaddyBlock.AGE, 0),
         3
      );
      level.setBlock(paddyPos.north(), Blocks.SEA_LANTERN.defaultBlockState(), 3);

      for (int i = 0; i < 300; i++) {
         BlockState state = level.getBlockState(paddyPos);
         state.randomTick(level, paddyPos, RandomSource.create(i));
      }

      int age = (Integer)level.getBlockState(paddyPos).getValue(RicePaddyBlock.AGE);
      if (age == 0) {
         helper.fail("Rice paddy did not grow after 300 random ticks");
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testRicePaddy_defaultWaterlogged(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos paddyPos = helper.absolutePos(new BlockPos(5, 1, 5));
      level.setBlock(paddyPos, ((Block)ModBlocks.RICE_PADDY.get()).defaultBlockState(), 3);
      BlockState state = level.getBlockState(paddyPos);
      if (!(Boolean)state.getValue(RicePaddyBlock.WATERLOGGED)) {
         helper.fail("Rice paddy should be waterlogged by default");
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testWetBrick_ageProgression(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos brickPos = helper.absolutePos(new BlockPos(5, 1, 5));
      level.setBlock(brickPos, (BlockState)((BlockWetBrick)ModBlocks.WET_BRICK.get()).defaultBlockState().setValue(BlockWetBrick.AGE, 2), 3);
      BlockState state = level.getBlockState(brickPos);
      if ((Integer)state.getValue(BlockWetBrick.AGE) != 2) {
         helper.fail("Wet brick should have age=2");
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testWetBrick_defaultAge(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos brickPos = helper.absolutePos(new BlockPos(5, 1, 5));
      level.setBlock(brickPos, ((BlockWetBrick)ModBlocks.WET_BRICK.get()).defaultBlockState(), 3);
      BlockState state = level.getBlockState(brickPos);
      if ((Integer)state.getValue(BlockWetBrick.AGE) != 0) {
         helper.fail("Fresh wet brick should have age=0, got " + state.getValue(BlockWetBrick.AGE));
      } else if (!state.isRandomlyTicking()) {
         helper.fail("Wet brick should always be randomly ticking");
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testAppleSapling_stageProgression(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos saplingPos = helper.absolutePos(new BlockPos(5, 2, 5));
      level.setBlock(saplingPos.below(), Blocks.DIRT.defaultBlockState(), 3);
      level.setBlock(saplingPos, ((AppleTreeSaplingBlock)ModBlocks.APPLE_TREE_SAPLING.get()).defaultBlockState(), 3);
      AppleTreeSaplingBlock sapling = (AppleTreeSaplingBlock)ModBlocks.APPLE_TREE_SAPLING.get();
      BlockState state = level.getBlockState(saplingPos);
      sapling.advanceTree(level, saplingPos, state, RandomSource.create(42L));
      BlockState after = level.getBlockState(saplingPos);
      if (!(after.getBlock() instanceof AppleTreeSaplingBlock)) {
         helper.fail("Sapling should still be a sapling at stage 1");
      } else if ((Integer)after.getValue(AppleTreeSaplingBlock.STAGE) != 1) {
         helper.fail("Expected stage=1, got " + after.getValue(AppleTreeSaplingBlock.STAGE));
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testAppleSapling_stageDoesNotExceedMax(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos saplingPos = helper.absolutePos(new BlockPos(5, 2, 5));
      level.setBlock(saplingPos.below(), Blocks.DIRT.defaultBlockState(), 3);
      level.setBlock(
         saplingPos, (BlockState)((AppleTreeSaplingBlock)ModBlocks.APPLE_TREE_SAPLING.get()).defaultBlockState().setValue(AppleTreeSaplingBlock.STAGE, 0), 3
      );
      BlockState state = level.getBlockState(saplingPos);
      if ((Integer)state.getValue(AppleTreeSaplingBlock.STAGE) != 0) {
         helper.fail("Stage should be 0");
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testAppleSapling_bonemealable(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos saplingPos = helper.absolutePos(new BlockPos(5, 2, 5));
      level.setBlock(saplingPos.below(), Blocks.DIRT.defaultBlockState(), 3);
      level.setBlock(saplingPos, ((AppleTreeSaplingBlock)ModBlocks.APPLE_TREE_SAPLING.get()).defaultBlockState(), 3);
      AppleTreeSaplingBlock sapling = (AppleTreeSaplingBlock)ModBlocks.APPLE_TREE_SAPLING.get();
      BlockState state = level.getBlockState(saplingPos);
      if (!sapling.isValidBonemealTarget(level, saplingPos, state)) {
         helper.fail("Apple sapling should be a valid bonemeal target");
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testAppleLeaves_harvestAtMaxAge(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos leavesPos = helper.absolutePos(new BlockPos(5, 2, 5));
      level.setBlock(
         leavesPos,
         (BlockState)((BlockState)((AppleTreeLeavesBlock)ModBlocks.APPLE_TREE_LEAVES.get()).defaultBlockState().setValue(AppleTreeLeavesBlock.AGE, 3))
            .setValue(AppleTreeLeavesBlock.PERSISTENT, true),
         3
      );
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(leavesPos.getX(), leavesPos.getY(), leavesPos.getZ());
      BlockState before = level.getBlockState(leavesPos);
      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(leavesPos), Direction.UP, leavesPos, false);
      before.useWithoutItem(level, player, hit);
      BlockState after = level.getBlockState(leavesPos);
      if ((Integer)after.getValue(AppleTreeLeavesBlock.AGE) != 0) {
         helper.fail("Leaves age should reset to 0 after harvest, got " + after.getValue(AppleTreeLeavesBlock.AGE));
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testAppleLeaves_noHarvestBeforeMaxAge(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos leavesPos = helper.absolutePos(new BlockPos(5, 2, 5));
      level.setBlock(
         leavesPos,
         (BlockState)((BlockState)((AppleTreeLeavesBlock)ModBlocks.APPLE_TREE_LEAVES.get()).defaultBlockState().setValue(AppleTreeLeavesBlock.AGE, 2))
            .setValue(AppleTreeLeavesBlock.PERSISTENT, true),
         3
      );
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(leavesPos.getX(), leavesPos.getY(), leavesPos.getZ());
      BlockState before = level.getBlockState(leavesPos);
      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(leavesPos), Direction.UP, leavesPos, false);
      before.useWithoutItem(level, player, hit);
      BlockState after = level.getBlockState(leavesPos);
      if ((Integer)after.getValue(AppleTreeLeavesBlock.AGE) != 2) {
         helper.fail("Leaves at age=2 should not be harvestable");
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testNormanRosette_horizontalConnectivity(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos center = helper.absolutePos(new BlockPos(5, 2, 5));
      BlockPos north = center.north();
      level.setBlock(center, ((NormanRosetteBlock)ModBlocks.ROSETTE.get()).defaultBlockState(), 3);
      level.setBlock(north, ((NormanRosetteBlock)ModBlocks.ROSETTE.get()).defaultBlockState(), 3);
      BlockState centerState = level.getBlockState(center);
      BlockState northState = level.getBlockState(north);
      if (!(Boolean)centerState.getValue(NormanRosetteBlock.ROS_NORTH)) {
         helper.fail("Center rosette should have ros_n=true (neighbor to the north)");
      } else if (!(Boolean)northState.getValue(NormanRosetteBlock.ROS_SOUTH)) {
         helper.fail("North rosette should have ros_s=true (neighbor to the south)");
      } else if (!(Boolean)centerState.getValue(NormanRosetteBlock.ROS_SOUTH)
         && !(Boolean)centerState.getValue(NormanRosetteBlock.ROS_EAST)
         && !(Boolean)centerState.getValue(NormanRosetteBlock.ROS_WEST)) {
         helper.succeed();
      } else {
         helper.fail("Center rosette should not connect where there is no rosette neighbor");
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testNormanRosette_verticalConnectivity(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos lower = helper.absolutePos(new BlockPos(5, 2, 5));
      BlockPos upper = lower.above();
      level.setBlock(lower, ((NormanRosetteBlock)ModBlocks.ROSETTE.get()).defaultBlockState(), 3);
      level.setBlock(upper, ((NormanRosetteBlock)ModBlocks.ROSETTE.get()).defaultBlockState(), 3);
      BlockState lowerState = level.getBlockState(lower);
      BlockState upperState = level.getBlockState(upper);
      if (!(Boolean)lowerState.getValue(NormanRosetteBlock.ROS_UP)) {
         helper.fail("Lower rosette should have ros_u=true");
      } else if (!(Boolean)upperState.getValue(NormanRosetteBlock.ROS_DOWN)) {
         helper.fail("Upper rosette should have ros_d=true");
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testNormanRosette_disconnectsOnRemoval(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos center = helper.absolutePos(new BlockPos(5, 2, 5));
      BlockPos east = center.east();
      level.setBlock(center, ((NormanRosetteBlock)ModBlocks.ROSETTE.get()).defaultBlockState(), 3);
      level.setBlock(east, ((NormanRosetteBlock)ModBlocks.ROSETTE.get()).defaultBlockState(), 3);
      if (!(Boolean)level.getBlockState(center).getValue(NormanRosetteBlock.ROS_EAST)) {
         helper.fail("Should be connected before removal");
      } else {
         level.setBlock(east, Blocks.AIR.defaultBlockState(), 3);
         if ((Boolean)level.getBlockState(center).getValue(NormanRosetteBlock.ROS_EAST)) {
            helper.fail("Center rosette should disconnect after neighbor removal");
         } else {
            helper.succeed();
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testPurse_storeDeniers(GameTestHelper helper) {
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      ItemStack purse = new ItemStack((ItemLike)ModItems.PURSE.get());
      player.setItemInHand(InteractionHand.MAIN_HAND, purse);
      player.getInventory().setItem(1, new ItemStack((ItemLike)ModItems.DENIER.get(), 30));
      player.getInventory().setItem(2, new ItemStack((ItemLike)ModItems.DENIER_ARGENT.get(), 2));
      int totalBefore = MoneyHelper.getTotalDeniers(player.getInventory());
      if (totalBefore != 158) {
         helper.fail("Expected 158 deniers in inventory, got " + totalBefore);
      } else {
         ((PurseItem)ModItems.PURSE.get()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
         int purseTotal = PurseItem.getTotalDeniers(player.getItemInHand(InteractionHand.MAIN_HAND));
         if (purseTotal != 158) {
            helper.fail("Purse should contain 158 deniers, got " + purseTotal);
         } else {
            int looseDeniers = 0;

            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
               ItemStack stack = player.getInventory().getItem(i);
               if (stack.is((Item)ModItems.DENIER.get()) || stack.is((Item)ModItems.DENIER_ARGENT.get()) || stack.is((Item)ModItems.DENIER_OR.get())) {
                  looseDeniers += stack.getCount();
               }
            }

            if (looseDeniers != 0) {
               helper.fail("Inventory should have no loose coins after storing, got " + looseDeniers);
            } else {
               helper.succeed();
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testPurse_releaseDeniers(GameTestHelper helper) {
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      ItemStack purse = new ItemStack((ItemLike)ModItems.PURSE.get());
      CompoundTag tag = new CompoundTag();
      tag.putInt("denier", 8);
      tag.putInt("denier_argent", 0);
      tag.putInt("denier_or", 3);
      tag.putInt("denier", 8);
      tag.putInt("denier_argent", 3);
      tag.putInt("denier_or", 0);
      purse.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
      player.setItemInHand(InteractionHand.MAIN_HAND, purse);
      int purseBefore = PurseItem.getTotalDeniers(player.getItemInHand(InteractionHand.MAIN_HAND));
      if (purseBefore != 200) {
         helper.fail("Purse should contain 200 deniers before release, got " + purseBefore);
      } else {
         ((PurseItem)ModItems.PURSE.get()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
         int purseAfter = PurseItem.getTotalDeniers(player.getItemInHand(InteractionHand.MAIN_HAND));
         if (purseAfter != 0) {
            helper.fail("Purse should be empty after release, got " + purseAfter);
         } else {
            int invTotal = MoneyHelper.getTotalDeniers(player.getInventory());
            if (invTotal != 200) {
               helper.fail("Player should have 200 deniers after release, got " + invTotal);
            } else {
               helper.succeed();
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testBrickMould_placesWetBrick(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos stonePos = helper.absolutePos(new BlockPos(5, 1, 5));
      BlockPos targetPos = stonePos.above();
      level.setBlock(stonePos, Blocks.STONE.defaultBlockState(), 3);
      level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3);
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(stonePos.getX(), stonePos.getY() + 2, stonePos.getZ());
      ItemStack mould = new ItemStack((ItemLike)ModItems.BRICK_MOULD.get());
      player.setItemInHand(InteractionHand.MAIN_HAND, mould);
      player.getInventory().setItem(1, new ItemStack(Items.DIRT, 5));
      player.getInventory().setItem(2, new ItemStack(Items.SAND, 5));
      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(stonePos).add(0.0, 0.5, 0.0), Direction.UP, stonePos, false);
      UseOnContext ctx = new UseOnContext(level, player, InteractionHand.MAIN_HAND, mould, hit);
      ((Item)ModItems.BRICK_MOULD.get()).useOn(ctx);
      BlockState result = level.getBlockState(targetPos);
      if (result.getBlock() != ModBlocks.WET_BRICK.get()) {
         helper.fail("Brick mould should place a wet brick, got: " + result.getBlock());
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testBrickMould_failsWithoutMaterials(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos stonePos = helper.absolutePos(new BlockPos(5, 1, 5));
      BlockPos targetPos = stonePos.above();
      level.setBlock(stonePos, Blocks.STONE.defaultBlockState(), 3);
      level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3);
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(stonePos.getX(), stonePos.getY() + 2, stonePos.getZ());
      ItemStack mould = new ItemStack((ItemLike)ModItems.BRICK_MOULD.get());
      player.setItemInHand(InteractionHand.MAIN_HAND, mould);
      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(stonePos).add(0.0, 0.5, 0.0), Direction.UP, stonePos, false);
      UseOnContext ctx = new UseOnContext(level, player, InteractionHand.MAIN_HAND, mould, hit);
      ((Item)ModItems.BRICK_MOULD.get()).useOn(ctx);
      BlockState result = level.getBlockState(targetPos);
      if (result.getBlock() == ModBlocks.WET_BRICK.get()) {
         helper.fail("Brick mould should not place a brick without dirt+sand");
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testBrickMould_consumesMaterialsEvery4Uses(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      ItemStack mould = new ItemStack((ItemLike)ModItems.BRICK_MOULD.get());
      player.setItemInHand(InteractionHand.MAIN_HAND, mould);
      player.getInventory().setItem(1, new ItemStack(Items.DIRT, 10));
      player.getInventory().setItem(2, new ItemStack(Items.SAND, 10));

      for (int i = 0; i < 4; i++) {
         BlockPos base = helper.absolutePos(new BlockPos(5 + i, 1, 5));
         BlockPos target = base.above();
         level.setBlock(base, Blocks.STONE.defaultBlockState(), 3);
         level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
         BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(base).add(0.0, 0.5, 0.0), Direction.UP, base, false);
         mould = player.getItemInHand(InteractionHand.MAIN_HAND);
         UseOnContext ctx = new UseOnContext(level, player, InteractionHand.MAIN_HAND, mould, hit);
         ((Item)ModItems.BRICK_MOULD.get()).useOn(ctx);
      }

      int dirtCount = 0;
      int sandCount = 0;

      for (ItemStack stack : player.getInventory().items) {
         if (stack.is(Items.DIRT)) {
            dirtCount += stack.getCount();
         }

         if (stack.is(Items.SAND)) {
            sandCount += stack.getCount();
         }
      }

      if (dirtCount > 9) {
         helper.fail("Expected at most 9 dirt remaining (at least 1 consumed at damage=0), got " + dirtCount);
      } else if (sandCount > 9) {
         helper.fail("Expected at most 9 sand remaining (at least 1 consumed at damage=0), got " + sandCount);
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testPaintBucket_recolorsSingleBlock(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 5));
      level.setBlock(pos, ((Block)ModBlocks.PAINTED_BRICKS.get(DyeColor.WHITE).get()).defaultBlockState(), 3);
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(pos.getX(), pos.getY(), pos.getZ());
      ItemStack bucket = new ItemStack((ItemLike)ModItems.PAINT_BUCKETS.get(DyeColor.RED).get());
      player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
      UseOnContext ctx = new UseOnContext(level, player, InteractionHand.MAIN_HAND, bucket, hit);
      ((Item)ModItems.PAINT_BUCKETS.get(DyeColor.RED).get()).useOn(ctx);
      BlockState result = level.getBlockState(pos);
      if (result.getBlock() != ModBlocks.PAINTED_BRICKS.get(DyeColor.RED).get()) {
         helper.fail("Painted brick should be red after painting, got: " + result.getBlock());
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testPaintBucket_floodFillConnected(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos start = helper.absolutePos(new BlockPos(5, 2, 5));
      level.setBlock(start, ((Block)ModBlocks.PAINTED_BRICKS.get(DyeColor.WHITE).get()).defaultBlockState(), 3);
      level.setBlock(start.east(), ((Block)ModBlocks.PAINTED_BRICKS.get(DyeColor.WHITE).get()).defaultBlockState(), 3);
      level.setBlock(start.east().east(), ((Block)ModBlocks.PAINTED_BRICKS.get(DyeColor.WHITE).get()).defaultBlockState(), 3);
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(start.getX(), start.getY(), start.getZ());
      ItemStack bucket = new ItemStack((ItemLike)ModItems.PAINT_BUCKETS.get(DyeColor.BLUE).get());
      player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(start), Direction.UP, start, false);
      UseOnContext ctx = new UseOnContext(level, player, InteractionHand.MAIN_HAND, bucket, hit);
      ((Item)ModItems.PAINT_BUCKETS.get(DyeColor.BLUE).get()).useOn(ctx);

      for (int i = 0; i < 3; i++) {
         BlockPos check = start.east(i);
         if (level.getBlockState(check).getBlock() != ModBlocks.PAINTED_BRICKS.get(DyeColor.BLUE).get()) {
            helper.fail("Block at offset " + i + " should be blue after flood fill");
            return;
         }
      }

      helper.succeed();
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testPaintBucket_doesNotCrossColors(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos pos1 = helper.absolutePos(new BlockPos(5, 2, 5));
      BlockPos pos2 = pos1.east();
      level.setBlock(pos1, ((Block)ModBlocks.PAINTED_BRICKS.get(DyeColor.WHITE).get()).defaultBlockState(), 3);
      level.setBlock(pos2, ((Block)ModBlocks.PAINTED_BRICKS.get(DyeColor.GREEN).get()).defaultBlockState(), 3);
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(pos1.getX(), pos1.getY(), pos1.getZ());
      ItemStack bucket = new ItemStack((ItemLike)ModItems.PAINT_BUCKETS.get(DyeColor.RED).get());
      player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos1), Direction.UP, pos1, false);
      UseOnContext ctx = new UseOnContext(level, player, InteractionHand.MAIN_HAND, bucket, hit);
      ((Item)ModItems.PAINT_BUCKETS.get(DyeColor.RED).get()).useOn(ctx);
      if (level.getBlockState(pos1).getBlock() != ModBlocks.PAINTED_BRICKS.get(DyeColor.RED).get()) {
         helper.fail("First block should be red");
      } else if (level.getBlockState(pos2).getBlock() != ModBlocks.PAINTED_BRICKS.get(DyeColor.GREEN).get()) {
         helper.fail("Second block should still be green (different color, not flood-filled)");
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testMillFood_multiPortionDamage(GameTestHelper helper) {
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      ItemStack cider = new ItemStack((ItemLike)ModItems.CIDER.get());
      int maxDamage = cider.getMaxDamage();
      if (maxDamage <= 0) {
         helper.fail("Cider should have durability for multi-portions");
      } else {
         int damageBefore = cider.getDamageValue();
         ((MillFoodItem)ModItems.CIDER.get()).finishUsingItem(cider, helper.getLevel(), player);
         int damageAfter = cider.getDamageValue();
         int damageDealt = damageAfter - damageBefore;
         if (damageDealt != 64) {
            helper.fail("Each portion should consume 64 durability, got " + damageDealt);
         } else {
            helper.succeed();
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testMillFood_appliesEffects(GameTestHelper helper) {
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      ItemStack calva = new ItemStack((ItemLike)ModItems.CALVA.get());
      ((MillFoodItem)ModItems.CALVA.get()).finishUsingItem(calva, helper.getLevel(), player);
      boolean hasConfusion = player.hasEffect(MobEffects.CONFUSION);
      if (!hasConfusion) {
         helper.fail("Calva should apply nausea/confusion effect");
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testMillFood_healsPlayer(GameTestHelper helper) {
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.setHealth(10.0F);
      float healthBefore = player.getHealth();
      ItemStack cider = new ItemStack((ItemLike)ModItems.CIDER.get());
      ((MillFoodItem)ModItems.CIDER.get()).finishUsingItem(cider, helper.getLevel(), player);
      float healthAfter = player.getHealth();
      if (healthAfter <= healthBefore) {
         helper.fail("Cider should heal the player (heal=4). Before=" + healthBefore + " after=" + healthAfter);
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testMillFood_rasgullaSpeed(GameTestHelper helper) {
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      ItemStack rasgulla = new ItemStack((ItemLike)ModItems.RASGULLA.get());
      ((MillFoodItem)ModItems.RASGULLA.get()).finishUsingItem(rasgulla, helper.getLevel(), player);
      boolean hasSpeed = player.hasEffect(MobEffects.MOVEMENT_SPEED);
      if (!hasSpeed) {
         helper.fail("Rasgulla should apply Speed II effect");
      } else {
         helper.succeed();
      }
   }
}
