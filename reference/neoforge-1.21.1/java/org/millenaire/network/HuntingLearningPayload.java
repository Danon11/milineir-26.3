package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.item.MoneyHelper;
import org.millenaire.village.PlayerCultureReputation;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;

public record HuntingLearningPayload(String villageId, String dropKey) implements CustomPacketPayload {
   public static final Type<HuntingLearningPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "hunting_learning"));
   public static final StreamCodec<ByteBuf, HuntingLearningPayload> STREAM_CODEC = StreamCodec.of(
      HuntingLearningPayload::encode, HuntingLearningPayload::decode
   );
   private static final int MAX_ID_LENGTH = 128;

   private static void encode(ByteBuf buf, HuntingLearningPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageId);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.dropKey);
   }

   private static HuntingLearningPayload decode(ByteBuf buf) {
      String villageId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (villageId.length() > 128) {
         villageId = villageId.substring(0, 128);
      }

      String dropKey = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (dropKey.length() > 128) {
         dropKey = dropKey.substring(0, 128);
      }

      return new HuntingLearningPayload(villageId, dropKey);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(HuntingLearningPayload payload, IPayloadContext context) {
      context.enqueueWork(
         () -> {
            if (context.player() instanceof ServerPlayer player) {
               if (player.level() instanceof ServerLevel serverLevel) {
                  UUID uuid;
                  try {
                     uuid = UUID.fromString(payload.villageId);
                  } catch (IllegalArgumentException e) {
                     return;
                  }

                  VillageManager vm = VillageSavedData.get(serverLevel).getVillageManager();
                  Village village = vm.getVillage(new VillageId(uuid));
                  if (village != null) {
                     if (!(player.distanceToSqr(village.getCenter().getX(), village.getCenter().getY(), village.getCenter().getZ()) > 4096.0)) {
                        Culture culture = ModCultures.getCulture(village.getCultureId());
                        if (culture != null) {
                           if (culture.knownHuntingDrops().contains(payload.dropKey)) {
                              int reputation = village.getCombinedReputation(serverLevel, player.getUUID());
                              if (reputation >= 8192) {
                                 if (MoneyHelper.removeDeniers(player.getInventory(), 512)) {
                                    PlayerCultureReputation cultureRep = PlayerCultureReputation.get(serverLevel);
                                    cultureRep.learnHuntingDrop(player.getUUID(), payload.dropKey);
                                    player.sendSystemMessage(
                                       Component.translatable(
                                          "message.millenaire.hunting_learned", new Object[]{Component.translatable("millenaire.hunting." + payload.dropKey)}
                                       )
                                    );
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      );
   }
}
