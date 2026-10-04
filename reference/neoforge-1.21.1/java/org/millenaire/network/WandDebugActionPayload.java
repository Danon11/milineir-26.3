package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.item.WandDebugActions;

public record WandDebugActionPayload(String actionId, int targetEntityId, BlockPos targetPos) implements CustomPacketPayload {
   private static final int MAX_STRING_LENGTH = 256;
   public static final Type<WandDebugActionPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "wand_debug_action"));
   public static final StreamCodec<ByteBuf, WandDebugActionPayload> STREAM_CODEC = StreamCodec.of(
      WandDebugActionPayload::encode, WandDebugActionPayload::decode
   );

   private static void encode(ByteBuf buf, WandDebugActionPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.actionId);
      ByteBufCodecs.VAR_INT.encode(buf, payload.targetEntityId);
      ByteBufCodecs.VAR_INT.encode(buf, payload.targetPos.getX());
      ByteBufCodecs.VAR_INT.encode(buf, payload.targetPos.getY());
      ByteBufCodecs.VAR_INT.encode(buf, payload.targetPos.getZ());
   }

   private static WandDebugActionPayload decode(ByteBuf buf) {
      String actionId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (actionId.length() > 256) {
         actionId = actionId.substring(0, 256);
      }

      int targetEntityId = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int x = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int y = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int z = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      BlockPos targetPos = new BlockPos(x, y, z);
      return new WandDebugActionPayload(actionId, targetEntityId, targetPos);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(WandDebugActionPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (context.player() instanceof ServerPlayer player) {
            if (player.hasPermissions(2) || player.server.isSingleplayer()) {
               if (player.level() instanceof ServerLevel level) {
                  WandDebugActions.execute(level, player, payload);
               }
            }
         }
      });
   }
}
