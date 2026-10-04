package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record VillagerInfoPayload(
   int entityId,
   String displayName,
   String roleName,
   String nativeOccupation,
   String villageName,
   String goalLabel,
   float health,
   float maxHealth,
   int reputation,
   String reputationLabel,
   String cultureName,
   int languageScore,
   List<VillagerInfoPayload.InvEntry> inventory,
   List<String> possibleGoals,
   String cultureKey,
   String villagerTypeKey,
   boolean travelBookVisible
) implements CustomPacketPayload {
   public static final Type<VillagerInfoPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "villager_info"));
   public static final StreamCodec<ByteBuf, VillagerInfoPayload> STREAM_CODEC = StreamCodec.of(VillagerInfoPayload::encode, VillagerInfoPayload::decode);

   private static void encode(ByteBuf buf, VillagerInfoPayload payload) {
      ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.displayName);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.roleName);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.nativeOccupation);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageName);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.goalLabel);
      ByteBufCodecs.FLOAT.encode(buf, payload.health);
      ByteBufCodecs.FLOAT.encode(buf, payload.maxHealth);
      ByteBufCodecs.VAR_INT.encode(buf, payload.reputation);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.reputationLabel);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.cultureName);
      ByteBufCodecs.VAR_INT.encode(buf, payload.languageScore);
      ByteBufCodecs.VAR_INT.encode(buf, payload.inventory.size());

      for (VillagerInfoPayload.InvEntry entry : payload.inventory) {
         ByteBufCodecs.STRING_UTF8.encode(buf, entry.itemId);
         ByteBufCodecs.VAR_INT.encode(buf, entry.count);
      }

      ByteBufCodecs.VAR_INT.encode(buf, payload.possibleGoals.size());

      for (String goalKey : payload.possibleGoals) {
         ByteBufCodecs.STRING_UTF8.encode(buf, goalKey);
      }

      ByteBufCodecs.STRING_UTF8.encode(buf, payload.cultureKey);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villagerTypeKey);
      buf.writeBoolean(payload.travelBookVisible);
   }

   private static VillagerInfoPayload decode(ByteBuf buf) {
      int entityId = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      String displayName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String roleName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String nativeOccupation = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String villageName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String goalLabel = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      float health = (Float)ByteBufCodecs.FLOAT.decode(buf);
      float maxHealth = (Float)ByteBufCodecs.FLOAT.decode(buf);
      int reputation = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      String reputationLabel = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String cultureName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      int languageScore = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int invSize = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int maxInvSize = Math.min(invSize, 256);
      List<VillagerInfoPayload.InvEntry> inventory = new ArrayList<>(maxInvSize);

      for (int i = 0; i < invSize; i++) {
         String itemId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         int count = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         if (i < maxInvSize) {
            inventory.add(new VillagerInfoPayload.InvEntry(itemId, count));
         }
      }

      int goalsSize = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int maxGoalsSize = Math.min(goalsSize, 128);
      List<String> possibleGoals = new ArrayList<>(maxGoalsSize);

      for (int i = 0; i < goalsSize; i++) {
         String goalKey = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         if (i < maxGoalsSize) {
            possibleGoals.add(goalKey);
         }
      }

      String cultureKey = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String villagerTypeKey = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      boolean travelBookVisible = buf.readBoolean();
      return new VillagerInfoPayload(
         entityId,
         displayName,
         roleName,
         nativeOccupation,
         villageName,
         goalLabel,
         health,
         maxHealth,
         reputation,
         reputationLabel,
         cultureName,
         languageScore,
         inventory,
         possibleGoals,
         cultureKey,
         villagerTypeKey,
         travelBookVisible
      );
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public record InvEntry(String itemId, int count) {
   }
}
