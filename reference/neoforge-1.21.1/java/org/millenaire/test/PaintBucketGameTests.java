package org.millenaire.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.millenaire.block.IPaintedBlock;
import org.millenaire.block.ModBlocks;
import org.millenaire.item.ModItems;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class PaintBucketGameTests {
   private static InteractionResult paintBlock(ServerLevel level, ServerPlayer player, BlockPos pos, DyeColor color) {
      ItemStack bucket = new ItemStack((ItemLike)ModItems.PAINT_BUCKETS.get(color).get());
      player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
      UseOnContext ctx = new UseOnContext(level, player, InteractionHand.MAIN_HAND, bucket, hit);
      return ((Item)ModItems.PAINT_BUCKETS.get(color).get()).useOn(ctx);
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testPaintBucket_stopsAtAirGap(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos left = helper.absolutePos(new BlockPos(5, 2, 5));
      BlockPos gap = left.east();
      BlockPos right = gap.east();
      BlockState white = ((Block)ModBlocks.PAINTED_BRICKS.get(DyeColor.WHITE).get()).defaultBlockState();
      level.setBlock(left, white, 3);
      level.setBlock(right, white, 3);
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(left.getX(), left.getY(), left.getZ());
      paintBlock(level, player, left, DyeColor.RED);
      if (!(level.getBlockState(left).getBlock() instanceof IPaintedBlock p1 && p1.getColor() == DyeColor.RED)) {
         helper.fail("Left block should be red");
      } else if (level.getBlockState(right).getBlock() instanceof IPaintedBlock p2 && p2.getColor() == DyeColor.WHITE) {
         helper.succeed();
      } else {
         helper.fail("Right block should remain white (air gap blocks flood fill)");
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testPaintBucket_floodFillMixedTypes(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos base = helper.absolutePos(new BlockPos(5, 2, 5));
      level.setBlock(base, ((Block)ModBlocks.PAINTED_BRICKS.get(DyeColor.WHITE).get()).defaultBlockState(), 3);
      level.setBlock(base.east(), ((Block)ModBlocks.PAINTED_BRICK_SLABS.get(DyeColor.WHITE).get()).defaultBlockState(), 3);
      level.setBlock(base.above(), ((Block)ModBlocks.PAINTED_BRICK_STAIRS.get(DyeColor.WHITE).get()).defaultBlockState(), 3);
      level.setBlock(base.north(), ((Block)ModBlocks.PAINTED_BRICK_WALLS.get(DyeColor.WHITE).get()).defaultBlockState(), 3);
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(base.getX(), base.getY(), base.getZ());
      paintBlock(level, player, base, DyeColor.BLUE);
      BlockPos[] positions = new BlockPos[]{base, base.east(), base.above(), base.north()};

      for (BlockPos pos : positions) {
         BlockState state = level.getBlockState(pos);
         if (!(state.getBlock() instanceof IPaintedBlock painted) || painted.getColor() != DyeColor.BLUE) {
            helper.fail("Block at " + pos + " should be blue after flood fill, got: " + state.getBlock());
            return;
         }
      }

      helper.succeed();
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testPaintBucket_preservesStairProperties(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 5));
      BlockState stair = (BlockState)((BlockState)((Block)ModBlocks.PAINTED_BRICK_STAIRS.get(DyeColor.WHITE).get())
            .defaultBlockState()
            .setValue(StairBlock.FACING, Direction.EAST))
         .setValue(StairBlock.HALF, Half.TOP);
      level.setBlock(pos, stair, 3);
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(pos.getX(), pos.getY(), pos.getZ());
      paintBlock(level, player, pos, DyeColor.ORANGE);
      BlockState result = level.getBlockState(pos);
      if (!(result.getBlock() instanceof IPaintedBlock painted && painted.getColor() == DyeColor.ORANGE)) {
         helper.fail("Stair should be orange after painting");
      } else if (result.getValue(StairBlock.FACING) != Direction.EAST) {
         helper.fail("Stair facing should be preserved (EAST), got " + result.getValue(StairBlock.FACING));
      } else if (result.getValue(StairBlock.HALF) != Half.TOP) {
         helper.fail("Stair half should be preserved (TOP), got " + result.getValue(StairBlock.HALF));
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testPaintBucket_decoratedStaysDecorated(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 5));
      level.setBlock(pos, ((Block)ModBlocks.DECORATED_BRICKS.get(DyeColor.WHITE).get()).defaultBlockState(), 3);
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(pos.getX(), pos.getY(), pos.getZ());
      paintBlock(level, player, pos, DyeColor.PURPLE);
      BlockState result = level.getBlockState(pos);
      if (result.getBlock() != ModBlocks.DECORATED_BRICKS.get(DyeColor.PURPLE).get()) {
         helper.fail("Decorated brick should remain decorated after recolor, got: " + result.getBlock());
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testPaintBucket_noOpSameColor(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 5));
      level.setBlock(pos, ((Block)ModBlocks.PAINTED_BRICKS.get(DyeColor.RED).get()).defaultBlockState(), 3);
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(pos.getX(), pos.getY(), pos.getZ());
      ItemStack bucket = new ItemStack((ItemLike)ModItems.PAINT_BUCKETS.get(DyeColor.RED).get());
      player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
      UseOnContext ctx = new UseOnContext(level, player, InteractionHand.MAIN_HAND, bucket, hit);
      InteractionResult result = ((Item)ModItems.PAINT_BUCKETS.get(DyeColor.RED).get()).useOn(ctx);
      if (result != InteractionResult.PASS) {
         helper.fail("Painting same color should return PASS, got " + result);
      } else if (bucket.getDamageValue() != 0) {
         helper.fail("Bucket should not lose durability for same-color no-op, damage=" + bucket.getDamageValue());
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testPaintBucket_durabilityCost(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos start = helper.absolutePos(new BlockPos(5, 2, 5));
      BlockState white = ((Block)ModBlocks.PAINTED_BRICKS.get(DyeColor.WHITE).get()).defaultBlockState();
      level.setBlock(start, white, 3);
      level.setBlock(start.east(), white, 3);
      level.setBlock(start.above(), white, 3);
      level.setBlock(start.east().above(), white, 3);
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(start.getX(), start.getY(), start.getZ());
      ItemStack bucket = new ItemStack((ItemLike)ModItems.PAINT_BUCKETS.get(DyeColor.YELLOW).get());
      player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(start), Direction.UP, start, false);
      UseOnContext ctx = new UseOnContext(level, player, InteractionHand.MAIN_HAND, bucket, hit);
      ((Item)ModItems.PAINT_BUCKETS.get(DyeColor.YELLOW).get()).useOn(ctx);
      ItemStack afterBucket = player.getItemInHand(InteractionHand.MAIN_HAND);
      if (afterBucket.getDamageValue() != 4) {
         helper.fail("Bucket durability should decrease by 4 (blocks recolored), got " + afterBucket.getDamageValue());
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testPaintBucket_mixedDecoratedAndPlain(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos plainPos = helper.absolutePos(new BlockPos(5, 2, 5));
      BlockPos decoPos = plainPos.east();
      level.setBlock(plainPos, ((Block)ModBlocks.PAINTED_BRICKS.get(DyeColor.WHITE).get()).defaultBlockState(), 3);
      level.setBlock(decoPos, ((Block)ModBlocks.DECORATED_BRICKS.get(DyeColor.WHITE).get()).defaultBlockState(), 3);
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(plainPos.getX(), plainPos.getY(), plainPos.getZ());
      paintBlock(level, player, plainPos, DyeColor.CYAN);
      if (level.getBlockState(plainPos).getBlock() != ModBlocks.PAINTED_BRICKS.get(DyeColor.CYAN).get()) {
         helper.fail("Plain brick should be cyan");
      } else if (level.getBlockState(decoPos).getBlock() != ModBlocks.DECORATED_BRICKS.get(DyeColor.CYAN).get()) {
         helper.fail("Decorated brick should be cyan decorated, got: " + level.getBlockState(decoPos).getBlock());
      } else {
         helper.succeed();
      }
   }
}
