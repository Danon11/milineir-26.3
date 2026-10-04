package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record BuildingProjectListPayload(String villageUuid, String villageName, List<BuildingProjectListPayload.BuildingEntry> entries)
   implements CustomPacketPayload {
   private static final int MAX_ENTRIES = 64;
   public static final Type<BuildingProjectListPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "building_project_list"));
   public static final StreamCodec<ByteBuf, BuildingProjectListPayload> STREAM_CODEC = StreamCodec.of(
      BuildingProjectListPayload::encode, BuildingProjectListPayload::decode
   );

   private static void encode(ByteBuf buf, BuildingProjectListPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageUuid);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageName);
      int count = Math.min(payload.entries.size(), 64);
      ByteBufCodecs.VAR_INT.encode(buf, count);

      for (int i = 0; i < count; i++) {
         BuildingProjectListPayload.BuildingEntry entry = payload.entries.get(i);
         ByteBufCodecs.STRING_UTF8.encode(buf, entry.planSetId());
         ByteBufCodecs.STRING_UTF8.encode(buf, entry.displayName());
      }
   }

   private static BuildingProjectListPayload decode(ByteBuf buf) {
      String villageUuid = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String villageName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      int count = Math.min((Integer)ByteBufCodecs.VAR_INT.decode(buf), 64);
      List<BuildingProjectListPayload.BuildingEntry> entries = new ArrayList<>(count);

      for (int i = 0; i < count; i++) {
         entries.add(new BuildingProjectListPayload.BuildingEntry((String)ByteBufCodecs.STRING_UTF8.decode(buf), (String)ByteBufCodecs.STRING_UTF8.decode(buf)));
      }

      return new BuildingProjectListPayload(villageUuid, villageName, entries);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public record BuildingEntry(String planSetId, String displayName) {
   }
}
