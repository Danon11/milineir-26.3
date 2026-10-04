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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.item.ModItems;
import org.millenaire.item.NegationWandItem;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

public record NegationWandConfirmPayload(String villageUuid) implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final double MAX_CONFIRM_DISTANCE = 256.0;
   private static final int MAX_UUID_LENGTH = 64;
   public static final Type<NegationWandConfirmPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "negation_wand_confirm"));
   public static final StreamCodec<ByteBuf, NegationWandConfirmPayload> STREAM_CODEC = StreamCodec.of(
      NegationWandConfirmPayload::encode, NegationWandConfirmPayload::decode
   );

   private static void encode(ByteBuf buf, NegationWandConfirmPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageUuid);
   }

   private static NegationWandConfirmPayload decode(ByteBuf buf) {
      String uuid = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (uuid.length() > 64) {
         uuid = uuid.substring(0, 64);
      }

      return new NegationWandConfirmPayload(uuid);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(NegationWandConfirmPayload payload, ServerPlayer player) {
      if (player != null) {
         if (player.level() instanceof ServerLevel serverLevel) {
            ItemStack var14 = player.getMainHandItem();
            ItemStack offHand = player.getOffhandItem();
            boolean holdsWand = var14.is((Item)ModItems.NEGATION_WAND.get()) || offHand.is((Item)ModItems.NEGATION_WAND.get());
            if (!holdsWand) {
               LOGGER.warn("[Millenaire] Player {} sent negation wand confirm without holding wand", player.getName().getString());
            } else {
               UUID uuid;
               try {
                  uuid = UUID.fromString(payload.villageUuid);
               } catch (IllegalArgumentException e) {
                  return;
               }

               VillageSavedData savedData = VillageSavedData.get(serverLevel);
               VillageManager manager = savedData.getVillageManager();
               VillageId villageId = new VillageId(uuid);
               Village village = manager.getVillage(villageId);
               if (village == null) {
                  LOGGER.warn("[Millenaire] Player {} tried to delete non-existent village {}", player.getName().getString(), payload.villageUuid);
               } else {
                  double distSq = player.blockPosition().distSqr(village.getCenter());
                  if (distSq > 65536.0) {
                     LOGGER.warn(
                        "[Millenaire] Player {} too far from village {} for negation wand confirm (dist={})",
                        new Object[]{player.getName().getString(), payload.villageUuid, String.format("%.0f", Math.sqrt(distSq))}
                     );
                  } else if (village.areChestsLocked()) {
                     LOGGER.warn("[Millenaire] Player {} tried to delete locked village {}", player.getName().getString(), payload.villageUuid);
                  } else {
                     NegationWandItem.performDeletion(serverLevel, savedData, village, player);
                  }
               }
            }
         }
      }
   }

   public static void handleOnServer(NegationWandConfirmPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (context.player() instanceof ServerPlayer player) {
            handleOnServer(payload, player);
         }
      });
   }
}
