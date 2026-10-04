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
import org.millenaire.village.PlayerCultureReputation;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;

public record CultureControlPurchasePayload(String villageId) implements CustomPacketPayload {
   public static final Type<CultureControlPurchasePayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "culture_control_purchase"));
   public static final StreamCodec<ByteBuf, CultureControlPurchasePayload> STREAM_CODEC = StreamCodec.of(
      CultureControlPurchasePayload::encode, CultureControlPurchasePayload::decode
   );
   private static final int MAX_ID_LENGTH = 128;

   private static void encode(ByteBuf buf, CultureControlPurchasePayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageId);
   }

   private static CultureControlPurchasePayload decode(ByteBuf buf) {
      String villageId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (villageId.length() > 128) {
         villageId = villageId.substring(0, 128);
      }

      return new CultureControlPurchasePayload(villageId);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(CultureControlPurchasePayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
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
                     int reputation = village.getCombinedReputation(serverLevel, player.getUUID());
                     if (reputation >= 131072) {
                        PlayerCultureReputation cultureRep = PlayerCultureReputation.get(serverLevel);
                        if (!cultureRep.hasCultureControl(player.getUUID(), village.getCultureId())) {
                           cultureRep.grantCultureControl(player.getUUID(), village.getCultureId());
                           Culture culture = ModCultures.getCulture(village.getCultureId());
                           String cultureName = culture != null ? culture.displayName() : village.getCultureId().getPath();
                           player.sendSystemMessage(Component.translatable("millenaire.ui.control_gotten", new Object[]{cultureName}));
                        }
                     }
                  }
               }
            }
         }
      });
   }
}
