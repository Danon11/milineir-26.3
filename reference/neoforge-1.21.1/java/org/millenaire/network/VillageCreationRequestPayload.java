package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.advancement.MillAdvancements;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.village.PlayerCultureReputation;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.millenaire.world.VillageSpawner;
import org.slf4j.Logger;

public record VillageCreationRequestPayload(BlockPos pos, String cultureKey, String typeKey) implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final Type<VillageCreationRequestPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "village_creation_request"));
   public static final StreamCodec<ByteBuf, VillageCreationRequestPayload> STREAM_CODEC = StreamCodec.of(
      VillageCreationRequestPayload::encode, VillageCreationRequestPayload::decode
   );
   private static final int MAX_KEY_LENGTH = 128;

   private static void encode(ByteBuf buf, VillageCreationRequestPayload payload) {
      ByteBufCodecs.VAR_INT.encode(buf, payload.pos.getX());
      ByteBufCodecs.VAR_INT.encode(buf, payload.pos.getY());
      ByteBufCodecs.VAR_INT.encode(buf, payload.pos.getZ());
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.cultureKey);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.typeKey);
   }

   private static VillageCreationRequestPayload decode(ByteBuf buf) {
      int x = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int y = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int z = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      String cultureKey = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (cultureKey.length() > 128) {
         cultureKey = cultureKey.substring(0, 128);
      }

      String typeKey = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (typeKey.length() > 128) {
         typeKey = typeKey.substring(0, 128);
      }

      return new VillageCreationRequestPayload(new BlockPos(x, y, z), cultureKey, typeKey);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(VillageCreationRequestPayload payload, IPayloadContext context) {
      context.enqueueWork(
         () -> {
            if (context.player() instanceof ServerPlayer player) {
               if (player.level() instanceof ServerLevel serverLevel) {
                  if (!serverLevel.dimension().equals(Level.OVERWORLD)) {
                     player.sendSystemMessage(Component.literal("The summoning wand only works in the Overworld."));
                  } else {
                     ResourceLocation villageTypeId = ResourceLocation.fromNamespaceAndPath("millenaire", payload.typeKey);
                     VillageType villageType = ModCultures.getVillageType(villageTypeId);
                     if (villageType == null) {
                        player.sendSystemMessage(Component.literal("Village type not found: " + payload.typeKey));
                     } else {
                        VillageManager villageManager = VillageSavedData.get(serverLevel).getVillageManager();
                        if (villageManager.isWithinMinDistance(payload.pos, 100.0)) {
                           player.sendSystemMessage(Component.literal("A village already exists within 100 blocks."));
                        } else {
                           if (villageType.playerControlled()) {
                              PlayerCultureReputation cultureRep = PlayerCultureReputation.get(serverLevel);
                              if (!cultureRep.hasCultureControl(player.getUUID(), villageType.culture())) {
                                 player.sendSystemMessage(Component.translatable("millenaire.error.no_culture_control", new Object[]{villageType.name()}));
                                 return;
                              }
                           }

                           Component failure = VillageSpawner.spawnVillage(
                              serverLevel, payload.pos, villageType, 0, null, null, villageType.playerControlled() ? player : null
                           );
                           if (failure == null) {
                              player.sendSystemMessage(
                                 Component.literal(
                                    "Village " + villageType.name() + " (" + villageTypeId.getPath() + ") created at " + payload.pos.toShortString()
                                 )
                              );
                              MillAdvancements.grant(player, MillAdvancements.SUMMONING_WAND);
                           } else {
                              player.sendSystemMessage(failure);
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
