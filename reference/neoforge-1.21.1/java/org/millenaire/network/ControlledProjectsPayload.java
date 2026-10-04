package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record ControlledProjectsPayload(String villageUuid, String villageName, String pendingPlanName, List<ControlledProjectsPayload.ProjectEntry> entries)
   implements CustomPacketPayload {
   private static final int MAX_ENTRIES = 128;
   public static final Type<ControlledProjectsPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "controlled_projects"));
   public static final StreamCodec<ByteBuf, ControlledProjectsPayload> STREAM_CODEC = StreamCodec.of(
      ControlledProjectsPayload::encode, ControlledProjectsPayload::decode
   );

   private static void encode(ByteBuf buf, ControlledProjectsPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageUuid);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageName);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.pendingPlanName);
      int count = Math.min(payload.entries.size(), 128);
      ByteBufCodecs.VAR_INT.encode(buf, count);

      for (int i = 0; i < count; i++) {
         ControlledProjectsPayload.ProjectEntry e = payload.entries.get(i);
         ByteBufCodecs.STRING_UTF8.encode(buf, e.buildingId());
         ByteBufCodecs.STRING_UTF8.encode(buf, e.displayName());
         ByteBufCodecs.STRING_UTF8.encode(buf, e.planSetId());
         ByteBufCodecs.VAR_INT.encode(buf, e.currentLevel());
         ByteBufCodecs.VAR_INT.encode(buf, e.maxLevel());
         buf.writeBoolean(e.upgradesAllowed());
         ByteBufCodecs.STRING_UTF8.encode(buf, e.distanceLabel());
      }
   }

   private static ControlledProjectsPayload decode(ByteBuf buf) {
      String villageUuid = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String villageName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String pendingPlanName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      int count = Math.min((Integer)ByteBufCodecs.VAR_INT.decode(buf), 128);
      List<ControlledProjectsPayload.ProjectEntry> entries = new ArrayList<>(count);

      for (int i = 0; i < count; i++) {
         entries.add(
            new ControlledProjectsPayload.ProjectEntry(
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (Integer)ByteBufCodecs.VAR_INT.decode(buf),
               (Integer)ByteBufCodecs.VAR_INT.decode(buf),
               buf.readBoolean(),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf)
            )
         );
      }

      return new ControlledProjectsPayload(villageUuid, villageName, pendingPlanName, entries);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public record ProjectEntry(
      String buildingId, String displayName, String planSetId, int currentLevel, int maxLevel, boolean upgradesAllowed, String distanceLabel
   ) {
   }
}
