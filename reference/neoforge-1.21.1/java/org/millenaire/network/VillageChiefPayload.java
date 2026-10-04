package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record VillageChiefPayload(
   int entityId,
   String chiefName,
   String roleName,
   String villageName,
   String cultureName,
   int reputation,
   String reputationLabel,
   int totalBuildings,
   int completeBuildings,
   int underConstructionBuildings,
   int totalVillagers,
   List<String> buildingEntries,
   String villageId,
   int playerMoney,
   int playerReputation,
   List<VillageChiefPayload.PlayerBuildingEntry> playerBuildings,
   List<VillageChiefPayload.RelationEntry> relationEntries,
   int diplomacyPoints,
   List<VillageChiefPayload.LearningOffer> cropOffers,
   List<VillageChiefPayload.LearningOffer> huntingOffers,
   boolean cultureControlAvailable,
   boolean hasCultureControl,
   String cultureId,
   String chiefTypeKey
) implements CustomPacketPayload {
   public static final int CROP_REPUTATION = 8192;
   public static final int CROP_PRICE = 512;
   public static final int CULTURE_CONTROL_REPUTATION = 131072;
   private static final int MAX_BUILDING_ENTRIES = 256;
   private static final int MAX_RELATION_ENTRIES = 64;
   public static final Type<VillageChiefPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "village_chief"));
   public static final StreamCodec<ByteBuf, VillageChiefPayload> STREAM_CODEC = StreamCodec.of(VillageChiefPayload::encode, VillageChiefPayload::decode);

   private static void encode(ByteBuf buf, VillageChiefPayload payload) {
      ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.chiefName);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.roleName);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageName);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.cultureName);
      ByteBufCodecs.VAR_INT.encode(buf, payload.reputation);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.reputationLabel);
      ByteBufCodecs.VAR_INT.encode(buf, payload.totalBuildings);
      ByteBufCodecs.VAR_INT.encode(buf, payload.completeBuildings);
      ByteBufCodecs.VAR_INT.encode(buf, payload.underConstructionBuildings);
      ByteBufCodecs.VAR_INT.encode(buf, payload.totalVillagers);
      ByteBufCodecs.VAR_INT.encode(buf, payload.buildingEntries.size());

      for (String entry : payload.buildingEntries) {
         ByteBufCodecs.STRING_UTF8.encode(buf, entry);
      }

      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageId);
      ByteBufCodecs.VAR_INT.encode(buf, payload.playerMoney);
      ByteBufCodecs.VAR_INT.encode(buf, payload.playerReputation);
      ByteBufCodecs.VAR_INT.encode(buf, payload.playerBuildings.size());

      for (VillageChiefPayload.PlayerBuildingEntry entry : payload.playerBuildings) {
         ByteBufCodecs.STRING_UTF8.encode(buf, entry.planSetId);
         ByteBufCodecs.STRING_UTF8.encode(buf, entry.nativeName);
         ByteBufCodecs.VAR_INT.encode(buf, entry.price);
         ByteBufCodecs.VAR_INT.encode(buf, entry.reputation);
         ByteBufCodecs.STRING_UTF8.encode(buf, entry.status);
      }

      ByteBufCodecs.VAR_INT.encode(buf, payload.relationEntries.size());

      for (VillageChiefPayload.RelationEntry re : payload.relationEntries) {
         ByteBufCodecs.STRING_UTF8.encode(buf, re.villageId);
         ByteBufCodecs.STRING_UTF8.encode(buf, re.villageName);
         ByteBufCodecs.STRING_UTF8.encode(buf, re.cultureName);
         ByteBufCodecs.VAR_INT.encode(buf, re.relation);
         ByteBufCodecs.STRING_UTF8.encode(buf, re.relationLabel);
      }

      ByteBufCodecs.VAR_INT.encode(buf, payload.diplomacyPoints);
      ByteBufCodecs.VAR_INT.encode(buf, payload.cropOffers.size());

      for (VillageChiefPayload.LearningOffer offer : payload.cropOffers) {
         ByteBufCodecs.STRING_UTF8.encode(buf, offer.key);
         ByteBufCodecs.STRING_UTF8.encode(buf, offer.itemName);
         ByteBufCodecs.STRING_UTF8.encode(buf, offer.status);
      }

      ByteBufCodecs.VAR_INT.encode(buf, payload.huntingOffers.size());

      for (VillageChiefPayload.LearningOffer offer : payload.huntingOffers) {
         ByteBufCodecs.STRING_UTF8.encode(buf, offer.key);
         ByteBufCodecs.STRING_UTF8.encode(buf, offer.itemName);
         ByteBufCodecs.STRING_UTF8.encode(buf, offer.status);
      }

      ByteBufCodecs.BOOL.encode(buf, payload.cultureControlAvailable);
      ByteBufCodecs.BOOL.encode(buf, payload.hasCultureControl);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.cultureId);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.chiefTypeKey);
   }

   private static VillageChiefPayload decode(ByteBuf buf) {
      int entityId = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      String chiefName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String roleName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String villageName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String cultureName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      int reputation = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      String reputationLabel = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      int totalBuildings = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int completeBuildings = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int underConstructionBuildings = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int totalVillagers = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int rawBuildingCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int buildingCount = Math.min(rawBuildingCount, 256);
      List<String> buildingEntries = new ArrayList<>(buildingCount);

      for (int i = 0; i < buildingCount; i++) {
         buildingEntries.add((String)ByteBufCodecs.STRING_UTF8.decode(buf));
      }

      for (int i = buildingCount; i < rawBuildingCount; i++) {
         ByteBufCodecs.STRING_UTF8.decode(buf);
      }

      String villageId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      int playerMoney = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int playerReputation = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int pbCount = Math.min((Integer)ByteBufCodecs.VAR_INT.decode(buf), 32);
      List<VillageChiefPayload.PlayerBuildingEntry> playerBuildings = new ArrayList<>(pbCount);

      for (int i = 0; i < pbCount; i++) {
         playerBuildings.add(
            new VillageChiefPayload.PlayerBuildingEntry(
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (Integer)ByteBufCodecs.VAR_INT.decode(buf),
               (Integer)ByteBufCodecs.VAR_INT.decode(buf),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf)
            )
         );
      }

      int rawReCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int reCount = Math.min(rawReCount, 64);
      List<VillageChiefPayload.RelationEntry> relationEntries = new ArrayList<>(reCount);

      for (int i = 0; i < reCount; i++) {
         relationEntries.add(
            new VillageChiefPayload.RelationEntry(
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (Integer)ByteBufCodecs.VAR_INT.decode(buf),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf)
            )
         );
      }

      for (int i = reCount; i < rawReCount; i++) {
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.VAR_INT.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
      }

      int diplomacyPoints = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int rawCropCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int cropCount = Math.min(rawCropCount, 32);
      List<VillageChiefPayload.LearningOffer> cropOffers = new ArrayList<>(cropCount);

      for (int i = 0; i < cropCount; i++) {
         cropOffers.add(
            new VillageChiefPayload.LearningOffer(
               (String)ByteBufCodecs.STRING_UTF8.decode(buf), (String)ByteBufCodecs.STRING_UTF8.decode(buf), (String)ByteBufCodecs.STRING_UTF8.decode(buf)
            )
         );
      }

      for (int i = cropCount; i < rawCropCount; i++) {
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
      }

      int rawHuntCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int huntCount = Math.min(rawHuntCount, 32);
      List<VillageChiefPayload.LearningOffer> huntingOffers = new ArrayList<>(huntCount);

      for (int i = 0; i < huntCount; i++) {
         huntingOffers.add(
            new VillageChiefPayload.LearningOffer(
               (String)ByteBufCodecs.STRING_UTF8.decode(buf), (String)ByteBufCodecs.STRING_UTF8.decode(buf), (String)ByteBufCodecs.STRING_UTF8.decode(buf)
            )
         );
      }

      for (int i = huntCount; i < rawHuntCount; i++) {
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
      }

      boolean cultureControlAvailable = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      boolean hasCultureControl = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      String cultureId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String chiefTypeKey = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      return new VillageChiefPayload(
         entityId,
         chiefName,
         roleName,
         villageName,
         cultureName,
         reputation,
         reputationLabel,
         totalBuildings,
         completeBuildings,
         underConstructionBuildings,
         totalVillagers,
         buildingEntries,
         villageId,
         playerMoney,
         playerReputation,
         playerBuildings,
         relationEntries,
         diplomacyPoints,
         cropOffers,
         huntingOffers,
         cultureControlAvailable,
         hasCultureControl,
         cultureId,
         chiefTypeKey
      );
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public record LearningOffer(String key, String itemName, String status) {
   }

   public record PlayerBuildingEntry(String planSetId, String nativeName, int price, int reputation, String status) {
   }

   public record RelationEntry(String villageId, String villageName, String cultureName, int relation, String relationLabel) {
   }
}
