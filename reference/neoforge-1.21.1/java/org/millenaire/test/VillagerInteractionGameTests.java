package org.millenaire.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.gametest.GameTestPlayer;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.ModEntities;
import org.millenaire.entity.VillagerSpawnFactory;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class VillagerInteractionGameTests {
   private static VillagerInteractionGameTests.InteractionContext setupContext(GameTestHelper helper) {
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
      VillageSavedData.get(level).getVillageManager().addVillage(village);
      ResourceLocation typeId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/farmer");
      MillVillager villager = VillagerSpawnFactory.spawnInVillage(level, village, typeId, center, null);
      GameTestPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(center.getX(), center.getY(), center.getZ());
      return new VillagerInteractionGameTests.InteractionContext(village, villager, player, center);
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testInteraction_sleepingVillagerBlocked(GameTestHelper helper) {
      VillagerInteractionGameTests.InteractionContext ctx = setupContext(helper);
      ctx.villager().setVillagerSleeping(true);
      InteractionResult result = ctx.villager().mobInteract(ctx.player(), InteractionHand.MAIN_HAND);
      if (result != InteractionResult.PASS) {
         helper.fail("Expected PASS for sleeping villager, got " + result);
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testInteraction_normalVillagerSuccess(GameTestHelper helper) {
      VillagerInteractionGameTests.InteractionContext ctx = setupContext(helper);
      InteractionResult result = ctx.villager().mobInteract(ctx.player(), InteractionHand.MAIN_HAND);
      if (result != InteractionResult.SUCCESS) {
         helper.fail("Expected SUCCESS for normal villager interaction, got " + result);
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testInteraction_hitAppliesReputationPenalty(GameTestHelper helper) {
      VillagerInteractionGameTests.InteractionContext ctx = setupContext(helper);
      ServerLevel level = helper.getLevel();
      int repBefore = ctx.village().getReputation().get(ctx.player().getUUID());
      ctx.villager().hurt(level.damageSources().playerAttack(ctx.player()), 5.0F);
      int repAfter = ctx.village().getReputation().get(ctx.player().getUUID());
      int repChange = repAfter - repBefore;
      if (repChange != -50) {
         helper.fail("Expected rep change of -50 (5.0 * 10), got " + repChange);
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testInteraction_multipleHitsAccumulate(GameTestHelper helper) {
      VillagerInteractionGameTests.InteractionContext ctx = setupContext(helper);
      ServerLevel level = helper.getLevel();
      int repBefore = ctx.village().getReputation().get(ctx.player().getUUID());
      ctx.villager().hurt(level.damageSources().playerAttack(ctx.player()), 2.0F);
      ctx.villager().invulnerableTime = 0;
      ctx.villager().hurt(level.damageSources().playerAttack(ctx.player()), 2.0F);
      ctx.villager().invulnerableTime = 0;
      ctx.villager().hurt(level.damageSources().playerAttack(ctx.player()), 2.0F);
      int repAfter = ctx.village().getReputation().get(ctx.player().getUUID());
      int totalChange = repAfter - repBefore;
      if (totalChange != -60) {
         helper.fail("Expected total rep change of -60 (3 hits * 2.0 * 10), got " + totalChange);
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testInteraction_hitVillagerWithoutVillage(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();

      for (int x = 0; x < 10; x++) {
         for (int z = 0; z < 10; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
         }
      }

      BlockPos spawnPos = helper.absolutePos(new BlockPos(5, 1, 5));
      MillVillager villager = (MillVillager)((EntityType)ModEntities.MILL_VILLAGER.get()).create(level);
      if (villager == null) {
         helper.fail("Failed to create MillVillager");
      } else {
         villager.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0.0F, 0.0F);
         level.addFreshEntity(villager);
         GameTestPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
         player.moveTo(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
         boolean result = villager.hurt(level.damageSources().playerAttack(player), 3.0F);
         if (!result) {
            helper.fail("Expected hurtServer to return true (damage applied), got false");
         } else {
            helper.succeed();
         }
      }
   }

   private record InteractionContext(Village village, MillVillager villager, GameTestPlayer player, BlockPos center) {
   }
}
