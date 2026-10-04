package org.millenaire.test;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.slf4j.Logger;

public final class TestPlayerManager {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String PLAYER_NAME = "test-player";
   @Nullable
   private static ServerPlayer instance;
   private static final AtomicInteger teleportIdCounter = new AtomicInteger(0);

   private TestPlayerManager() {
   }

   public static void confirmTeleport() {
      if (instance != null && instance.connection != null) {
         int id = teleportIdCounter.incrementAndGet();
         instance.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(id));
      }
   }

   public static void acknowledgeChunkBatch() {
      if (instance != null && instance.connection != null) {
         instance.connection.chunkSender.onChunkBatchReceivedByClient(64.0F);
      }
   }

   public static ServerPlayer spawn(MinecraftServer server, ServerLevel level, BlockPos pos) {
      if (instance != null) {
         throw new IllegalStateException("TestPlayer already active. Call remove() first.");
      }

      teleportIdCounter.set(0);
      GameProfile profile = new GameProfile(UUID.randomUUID(), "test-player");
      ClientInformation clientInfo = new ClientInformation("en_us", 32, ChatVisiblity.FULL, true, 0, HumanoidArm.RIGHT, false, true);
      CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, clientInfo, false);
      ServerPlayer player = new ServerPlayer(server, level, profile, clientInfo);
      Connection connection = new Connection(PacketFlow.SERVERBOUND);
      new EmbeddedChannel(new ChannelHandler[]{connection});
      NetworkRegistry.configureMockConnection(connection);
      server.getPlayerList().placeNewPlayer(connection, player, cookie);
      player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
      instance = player;
      confirmTeleport();
      player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot());
      confirmTeleport();
      acknowledgeChunkBatch();
      LOGGER.info("[Millenaire] TestPlayer created at {} in {}", pos, level.dimension().location());
      return player;
   }

   public static void remove() {
      if (instance != null) {
         ServerPlayer player = instance;
         instance = null;
         player.closeContainer();
         player.server.getPlayerList().remove(player);
         LOGGER.info("[Millenaire] TestPlayer removed");
      }
   }

   @Nullable
   public static ServerPlayer get() {
      return instance;
   }

   public static boolean isActive() {
      return instance != null;
   }

   public static void onServerStopping() {
      if (instance != null) {
         LOGGER.info("[Millenaire] Cleanup TestPlayer (server shutdown)");
         remove();
      }
   }
}
