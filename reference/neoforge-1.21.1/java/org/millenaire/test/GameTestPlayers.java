package org.millenaire.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.testframework.gametest.GameTestPlayer;

public final class GameTestPlayers {
   private GameTestPlayers() {
   }

   public static GameTestPlayer create(GameTestHelper helper, GameType gameType) {
      CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
      GameTestPlayer player = new GameTestPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation(), helper);
      Connection connection = new Connection(PacketFlow.SERVERBOUND);
      new EmbeddedChannel(new ChannelHandler[]{connection});
      NetworkRegistry.configureMockConnection(connection);
      MinecraftServer server = helper.getLevel().getServer();
      server.getPlayerList().placeNewPlayer(connection, player, cookie);
      server.getConnection().getConnections().add(connection);
      helper.testInfo.addListener(player);
      player.gameMode.changeGameModeForPlayer(gameType);
      return player;
   }
}
