package org.millenaire.test;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.millenaire.entity.MillWallDecoration;
import org.millenaire.item.ModItems;
import org.millenaire.item.WallDecorationItem;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class WallDecorationGameTests {
   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testWallDecoration_rejectedOnUpFace(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 5));
      level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(pos.getX(), pos.getY() + 2, pos.getZ());
      ItemStack decoItem = new ItemStack((ItemLike)ModItems.TAPESTRY.get(), 3);
      player.setItemInHand(InteractionHand.MAIN_HAND, decoItem);
      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
      UseOnContext ctx = new UseOnContext(level, player, InteractionHand.MAIN_HAND, decoItem, hit);
      InteractionResult result = ((WallDecorationItem)ModItems.TAPESTRY.get()).useOn(ctx);
      if (result != InteractionResult.FAIL) {
         helper.fail("Clicking UP face should return FAIL, got " + result);
      } else if (decoItem.getCount() != 3) {
         helper.fail("Stack should not be consumed on FAIL, count=" + decoItem.getCount());
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testWallDecoration_rejectedOnDownFace(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 5));
      level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(pos.getX(), pos.getY() - 1, pos.getZ());
      ItemStack decoItem = new ItemStack((ItemLike)ModItems.TAPESTRY.get(), 3);
      player.setItemInHand(InteractionHand.MAIN_HAND, decoItem);
      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.DOWN, pos, false);
      UseOnContext ctx = new UseOnContext(level, player, InteractionHand.MAIN_HAND, decoItem, hit);
      InteractionResult result = ((WallDecorationItem)ModItems.TAPESTRY.get()).useOn(ctx);
      if (result != InteractionResult.FAIL) {
         helper.fail("Clicking DOWN face should return FAIL, got " + result);
      } else if (decoItem.getCount() != 3) {
         helper.fail("Stack should not be consumed on FAIL, count=" + decoItem.getCount());
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testWallDecoration_placedOnWall(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();

      for (int x = 4; x <= 6; x++) {
         for (int y = 1; y <= 3; y++) {
            BlockPos wallPos = helper.absolutePos(new BlockPos(x, y, 5));
            level.setBlock(wallPos, Blocks.STONE.defaultBlockState(), 3);
         }
      }

      BlockPos clickedPos = helper.absolutePos(new BlockPos(5, 2, 5));
      BlockPos hangingPos = helper.absolutePos(new BlockPos(5, 2, 6));
      if (!level.getBlockState(hangingPos).isAir()) {
         helper.fail("hangingPos should be air before placement, got " + level.getBlockState(hangingPos));
      } else {
         ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
         player.moveTo(hangingPos.getX() + 0.5, hangingPos.getY() + 0.5, hangingPos.getZ() + 1.0);
         ItemStack decoItem = new ItemStack((ItemLike)ModItems.TAPESTRY.get(), 3);
         player.setItemInHand(InteractionHand.MAIN_HAND, decoItem);
         BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(clickedPos), Direction.SOUTH, clickedPos, false);
         UseOnContext ctx = new UseOnContext(level, player, InteractionHand.MAIN_HAND, decoItem, hit);
         InteractionResult result = ((WallDecorationItem)ModItems.TAPESTRY.get()).useOn(ctx);
         if (result != InteractionResult.SUCCESS) {
            helper.fail("Clicking SOUTH face of a solid wall should return SUCCESS, got " + result);
         } else if (decoItem.getCount() != 2) {
            helper.fail("Stack should be shrunk by 1 after placement, count=" + decoItem.getCount());
         } else {
            AABB searchArea = new AABB(hangingPos).inflate(2.0);
            List<MillWallDecoration> entities = level.getEntitiesOfClass(MillWallDecoration.class, searchArea);
            if (entities.isEmpty()) {
               helper.fail("No MillWallDecoration entity found near " + hangingPos.toShortString() + " after placement");
            } else {
               helper.succeed();
            }
         }
      }
   }
}
