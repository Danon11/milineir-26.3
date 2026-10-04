package org.millenaire.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.gametest.GameTestPlayer;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class TestFrameworkSmokeTest {
   @GameTest(template = "empty_platform", setupTicks = 1L)
   public static void testGameTestPlayer_smokeTest(GameTestHelper helper) {
      GameTestPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      if (!(player instanceof ServerPlayer)) {
         helper.fail("Expected ServerPlayer, got " + player.getClass().getName());
      } else if (!player.isAlive()) {
         helper.fail("GameTestPlayer should be alive after creation");
      } else {
         helper.succeed();
      }
   }
}
