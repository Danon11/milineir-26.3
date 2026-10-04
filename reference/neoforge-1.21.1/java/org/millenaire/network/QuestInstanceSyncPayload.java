package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record QuestInstanceSyncPayload(
   long uniqueId,
   String questKey,
   int currentStep,
   long startTime,
   long currentStepStart,
   Map<String, QuestInstanceSyncPayload.VillagerData> villagers,
   String displayLabel,
   String currentStepVillagerId
) implements CustomPacketPayload {
   public static final Type<QuestInstanceSyncPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "quest_sync"));
   public static final StreamCodec<ByteBuf, QuestInstanceSyncPayload> STREAM_CODEC = StreamCodec.of(
      QuestInstanceSyncPayload::encode, QuestInstanceSyncPayload::decode
   );

   private static void encode(ByteBuf buf, QuestInstanceSyncPayload payload) {
      ByteBufCodecs.VAR_LONG.encode(buf, payload.uniqueId);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.questKey);
      ByteBufCodecs.VAR_INT.encode(buf, payload.currentStep);
      ByteBufCodecs.VAR_LONG.encode(buf, payload.startTime);
      ByteBufCodecs.VAR_LONG.encode(buf, payload.currentStepStart);
      ByteBufCodecs.VAR_INT.encode(buf, payload.villagers.size());

      for (Entry<String, QuestInstanceSyncPayload.VillagerData> entry : payload.villagers.entrySet()) {
         ByteBufCodecs.STRING_UTF8.encode(buf, entry.getKey());
         ByteBufCodecs.STRING_UTF8.encode(buf, entry.getValue().villagerId.toString());
         ByteBufCodecs.STRING_UTF8.encode(buf, entry.getValue().villageId.toString());
      }

      ByteBufCodecs.STRING_UTF8.encode(buf, payload.displayLabel);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.currentStepVillagerId);
   }

   private static QuestInstanceSyncPayload decode(ByteBuf buf) {
      long uniqueId = (Long)ByteBufCodecs.VAR_LONG.decode(buf);
      String questKey = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      int currentStep = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      long startTime = (Long)ByteBufCodecs.VAR_LONG.decode(buf);
      long currentStepStart = (Long)ByteBufCodecs.VAR_LONG.decode(buf);
      int villagerCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      Map<String, QuestInstanceSyncPayload.VillagerData> villagers = new HashMap<>();

      for (int i = 0; i < villagerCount; i++) {
         String key = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         UUID villagerId = UUID.fromString((String)ByteBufCodecs.STRING_UTF8.decode(buf));
         UUID villageId = UUID.fromString((String)ByteBufCodecs.STRING_UTF8.decode(buf));
         villagers.put(key, new QuestInstanceSyncPayload.VillagerData(villagerId, villageId));
      }

      String displayLabel = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String currentStepVillagerId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      return new QuestInstanceSyncPayload(uniqueId, questKey, currentStep, startTime, currentStepStart, villagers, displayLabel, currentStepVillagerId);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public record VillagerData(UUID villagerId, UUID villageId) {
   }
}
