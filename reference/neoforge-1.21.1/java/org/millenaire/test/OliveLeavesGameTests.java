package org.millenaire.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.millenaire.block.ModBlocks;
import org.millenaire.block.OliveTreeLeavesBlock;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class OliveLeavesGameTests {
   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testOliveLeaves_harvestAtMaxAge(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos leavesPos = helper.absolutePos(new BlockPos(5, 2, 5));
      level.setBlock(
         leavesPos,
         (BlockState)((BlockState)((OliveTreeLeavesBlock)ModBlocks.OLIVE_TREE_LEAVES.get()).defaultBlockState().setValue(OliveTreeLeavesBlock.AGE, 3))
            .setValue(OliveTreeLeavesBlock.PERSISTENT, true),
         3
      );
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(leavesPos.getX(), leavesPos.getY(), leavesPos.getZ());
      BlockState before = level.getBlockState(leavesPos);
      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(leavesPos), Direction.UP, leavesPos, false);
      before.useWithoutItem(level, player, hit);
      BlockState after = level.getBlockState(leavesPos);
      if ((Integer)after.getValue(OliveTreeLeavesBlock.AGE) != 0) {
         helper.fail("Leaves age should reset to 0 after harvest, got " + after.getValue(OliveTreeLeavesBlock.AGE));
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testOliveLeaves_noHarvestBeforeMaxAge(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos leavesPos = helper.absolutePos(new BlockPos(5, 2, 5));
      level.setBlock(
         leavesPos,
         (BlockState)((BlockState)((OliveTreeLeavesBlock)ModBlocks.OLIVE_TREE_LEAVES.get()).defaultBlockState().setValue(OliveTreeLeavesBlock.AGE, 2))
            .setValue(OliveTreeLeavesBlock.PERSISTENT, true),
         3
      );
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(leavesPos.getX(), leavesPos.getY(), leavesPos.getZ());
      BlockState before = level.getBlockState(leavesPos);
      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(leavesPos), Direction.UP, leavesPos, false);
      before.useWithoutItem(level, player, hit);
      BlockState after = level.getBlockState(leavesPos);
      if ((Integer)after.getValue(OliveTreeLeavesBlock.AGE) != 2) {
         helper.fail("Leaves at age=2 should not be harvestable");
      } else {
         helper.succeed();
      }
   }
}
