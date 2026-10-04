package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.ReputationLabel;
import org.millenaire.village.PlayerCultureReputation;
import org.millenaire.village.VillageReputation;
import org.slf4j.Logger;

public record InfoPanelRequestPayload() implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final Type<InfoPanelRequestPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "info_panel_request"));
   public static final StreamCodec<ByteBuf, InfoPanelRequestPayload> STREAM_CODEC = StreamCodec.of((buf, payload) -> {}, buf -> new InfoPanelRequestPayload());

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(InfoPanelRequestPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (context.player() instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.level() instanceof ServerLevel serverLevel) {
               PlayerCultureReputation var16 = PlayerCultureReputation.get(serverLevel);
               ArrayList entries = new ArrayList();
               Map<ResourceLocation, Culture> allCultures = ModCultures.getAllCultures();

               for (Entry<ResourceLocation, Culture> entry : allCultures.entrySet()) {
                  ResourceLocation cultureId = entry.getKey();
                  Culture culture = entry.getValue();
                  int repValue = var16.get(serverPlayer.getUUID(), cultureId);
                  List<ReputationLabel> labels = ModCultures.getCultureReputationLabels(cultureId);
                  if (labels == null) {
                     labels = ModCultures.getReputationLabels(cultureId);
                  }

                  String repLabel = VillageReputation.getLabel(repValue, labels);
                  if (repLabel == null) {
                     repLabel = "reputation.neutral";
                  }

                  int languageScore = var16.getLanguageKnowledge(serverPlayer.getUUID(), cultureId);
                  String cultureNameKey = "culture.millenaire." + cultureId.getPath();
                  entries.add(new InfoPanelContentPayload.CultureEntry(cultureNameKey, repValue, repLabel, languageScore));
               }

               PacketDistributor.sendToPlayer(serverPlayer, new InfoPanelContentPayload(entries), new CustomPacketPayload[0]);
               LOGGER.debug("InfoPanel payload sent to {} with {} cultures", serverPlayer.getName().getString(), entries.size());
            }
         }
      });
   }
}
