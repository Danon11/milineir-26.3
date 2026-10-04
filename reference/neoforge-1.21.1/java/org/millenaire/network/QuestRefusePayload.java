package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.quest.QuestInstance;
import org.millenaire.quest.QuestRegistry;
import org.millenaire.village.PlayerQuestData;
import org.slf4j.Logger;

public record QuestRefusePayload(long questUniqueId) implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final Type<QuestRefusePayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "quest_refuse"));
   public static final StreamCodec<ByteBuf, QuestRefusePayload> STREAM_CODEC = StreamCodec.of(QuestRefusePayload::encode, QuestRefusePayload::decode);

   private static void encode(ByteBuf buf, QuestRefusePayload payload) {
      ByteBufCodecs.VAR_LONG.encode(buf, payload.questUniqueId);
   }

   private static QuestRefusePayload decode(ByteBuf buf) {
      return new QuestRefusePayload((Long)ByteBufCodecs.VAR_LONG.decode(buf));
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(QuestRefusePayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (context.player() instanceof ServerPlayer player) {
            if (player.level() instanceof ServerLevel serverLevel) {
               ServerLevel overworld = serverLevel.getServer().getLevel(Level.OVERWORLD);
               if (overworld != null) {
                  PlayerQuestData data = PlayerQuestData.get(overworld, QuestRegistry::get);
                  UUID playerId = player.getUUID();
                  QuestInstance quest = null;

                  for (QuestInstance qi : data.getActiveQuests(playerId)) {
                     if (qi.getUniqueId() == payload.questUniqueId) {
                        quest = qi;
                        break;
                     }
                  }

                  if (quest == null) {
                     LOGGER.debug("QuestRefuse: quest {} not found for player {}", payload.questUniqueId, player.getName().getString());
                  } else {
                     quest.refuseQuest(player);
                  }
               }
            }
         }
      });
   }
}
