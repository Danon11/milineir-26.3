package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.village.panel.PanelContent;
import org.millenaire.village.panel.PanelLine;
import org.millenaire.village.panel.PanelType;

public record VillageBookPayload(
   String villageName,
   String cultureId,
   List<PanelContent> sections,
   List<MapData.MapBuilding> mapBuildings,
   List<MapData.MapVillager> mapVillagers,
   int mapPlayerX,
   int mapPlayerZ,
   int mapCenterX,
   int mapCenterZ,
   MapData.MapTerrain mapTerrain,
   List<MapData.MapPath> mapPaths,
   boolean hasMapData,
   boolean degraded
) implements CustomPacketPayload {
   private static final int MAX_SECTIONS = 7;
   private static final int MAX_LINES = 200;
   private static final int MAX_MAP_ENTRIES = 256;
   private static final int MAX_ARGS = 8;
   private static final int MAX_PATHS = 8192;
   public static final Type<VillageBookPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "village_book"));
   public static final StreamCodec<ByteBuf, VillageBookPayload> STREAM_CODEC = StreamCodec.of(VillageBookPayload::encode, VillageBookPayload::decode);

   private static void encode(ByteBuf buf, VillageBookPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageName);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.cultureId);
      ByteBufCodecs.BOOL.encode(buf, payload.hasMapData);
      ByteBufCodecs.BOOL.encode(buf, payload.degraded);
      ByteBufCodecs.VAR_INT.encode(buf, payload.sections.size());

      for (PanelContent section : payload.sections) {
         ByteBufCodecs.STRING_UTF8.encode(buf, section.type().name());
         ByteBufCodecs.STRING_UTF8.encode(buf, section.title());
         ByteBufCodecs.BOOL.encode(buf, section.titleTranslatable());
         String[] titleArgs = section.titleArgs();
         int titleArgCount = titleArgs != null ? titleArgs.length : 0;
         ByteBufCodecs.VAR_INT.encode(buf, titleArgCount);
         if (titleArgs != null) {
            for (String tArg : titleArgs) {
               ByteBufCodecs.STRING_UTF8.encode(buf, tArg != null ? tArg : "");
            }
         }

         ByteBufCodecs.VAR_INT.encode(buf, section.lines().size());

         for (PanelLine line : section.lines()) {
            ByteBufCodecs.STRING_UTF8.encode(buf, line.leftColumn() != null ? line.leftColumn() : line.text());
            ByteBufCodecs.STRING_UTF8.encode(buf, line.rightColumn() != null ? line.rightColumn() : "");
            ByteBufCodecs.STRING_UTF8.encode(buf, line.leftIcon() != null ? line.leftIcon() : "");
            ByteBufCodecs.BOOL.encode(buf, line.isSeparator());
            ByteBufCodecs.BOOL.encode(buf, line.translatable());
            String[] args = line.translatableArgs();
            int argsCount = args != null ? args.length : 0;
            ByteBufCodecs.VAR_INT.encode(buf, argsCount);
            if (args != null) {
               for (String arg : args) {
                  ByteBufCodecs.STRING_UTF8.encode(buf, arg);
               }
            }

            ByteBufCodecs.VAR_INT.encode(buf, line.color());
            ByteBufCodecs.BOOL.encode(buf, line.bold());
            ByteBufCodecs.BYTE.encode(buf, (byte)line.translatableArgMask());
            ByteBufCodecs.STRING_UTF8.encode(buf, line.nativePrefix() != null ? line.nativePrefix() : "");
         }
      }

      if (payload.hasMapData) {
         MapData.encodeBuildingList(buf, payload.mapBuildings);
         MapData.encodeVillagerList(buf, payload.mapVillagers);
         ByteBufCodecs.VAR_INT.encode(buf, payload.mapPlayerX);
         ByteBufCodecs.VAR_INT.encode(buf, payload.mapPlayerZ);
         ByteBufCodecs.VAR_INT.encode(buf, payload.mapCenterX);
         ByteBufCodecs.VAR_INT.encode(buf, payload.mapCenterZ);
         MapData.encodeTerrain(buf, payload.mapTerrain);
         MapData.encodePathList(buf, payload.mapPaths);
      }
   }

   private static VillageBookPayload decode(ByteBuf buf) {
      String villageName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String cultureId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      boolean hasMapData = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      boolean degraded = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      int rawSectionCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int sectionCount = Math.min(rawSectionCount, 7);
      List<PanelContent> sections = new ArrayList<>(sectionCount);

      for (int s = 0; s < sectionCount; s++) {
         String typeName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         String title = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         boolean titleTranslatable = (Boolean)ByteBufCodecs.BOOL.decode(buf);
         int rawTitleArgCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int titleArgCount = Math.min(rawTitleArgCount, 8);
         String[] titleArgs = null;
         if (titleArgCount > 0) {
            titleArgs = new String[titleArgCount];

            for (int ta = 0; ta < titleArgCount; ta++) {
               titleArgs[ta] = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
            }
         }

         for (int ta = titleArgCount; ta < rawTitleArgCount; ta++) {
            ByteBufCodecs.STRING_UTF8.decode(buf);
         }

         int rawLineCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int lineCount = Math.min(rawLineCount, 200);
         List<PanelLine> lines = new ArrayList<>(lineCount);

         for (int i = 0; i < lineCount; i++) {
            String leftText = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
            String rightText = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
            String leftIcon = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
            boolean isSeparator = (Boolean)ByteBufCodecs.BOOL.decode(buf);
            boolean translatable = (Boolean)ByteBufCodecs.BOOL.decode(buf);
            int rawArgsCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
            int argsCount = Math.min(rawArgsCount, 8);
            String[] transArgs = null;
            if (argsCount > 0) {
               transArgs = new String[argsCount];

               for (int a = 0; a < argsCount; a++) {
                  transArgs[a] = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
               }
            }

            for (int a = argsCount; a < rawArgsCount; a++) {
               ByteBufCodecs.STRING_UTF8.decode(buf);
            }

            int color = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
            boolean bold = (Boolean)ByteBufCodecs.BOOL.decode(buf);
            int argMask = (Byte)ByteBufCodecs.BYTE.decode(buf) & 255;
            String nativePfx = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
            String nativePrefixVal = nativePfx.isEmpty() ? null : nativePfx;
            if (isSeparator) {
               lines.add(PanelLine.separator());
            } else if (!leftIcon.isEmpty() && translatable) {
               String rightColVal = rightText.isEmpty() ? null : rightText;
               lines.add(new PanelLine(leftText, false, null, rightColVal, leftIcon, true, null, transArgs, color, bold, argMask, nativePrefixVal));
            } else if (!leftIcon.isEmpty()) {
               lines.add(new PanelLine("", false, leftText, rightText, leftIcon, translatable, null, transArgs, color, bold, argMask, nativePrefixVal));
            } else if (!rightText.isEmpty()) {
               lines.add(new PanelLine("", false, leftText, rightText, null, translatable, null, transArgs, color, bold, argMask, nativePrefixVal));
            } else {
               lines.add(new PanelLine(leftText, false, null, null, null, translatable, null, transArgs, color, bold, argMask, nativePrefixVal));
            }
         }

         for (int i = lineCount; i < rawLineCount; i++) {
            ByteBufCodecs.STRING_UTF8.decode(buf);
            ByteBufCodecs.STRING_UTF8.decode(buf);
            ByteBufCodecs.STRING_UTF8.decode(buf);
            ByteBufCodecs.BOOL.decode(buf);
            ByteBufCodecs.BOOL.decode(buf);
            int skipArgs = (Integer)ByteBufCodecs.VAR_INT.decode(buf);

            for (int a = 0; a < skipArgs; a++) {
               ByteBufCodecs.STRING_UTF8.decode(buf);
            }

            ByteBufCodecs.VAR_INT.decode(buf);
            ByteBufCodecs.BOOL.decode(buf);
            ByteBufCodecs.BYTE.decode(buf);
            ByteBufCodecs.STRING_UTF8.decode(buf);
         }

         PanelType type = PanelType.fromName(typeName);
         sections.add(new PanelContent(type, title, lines, titleTranslatable, titleArgs));
      }

      for (int s = sectionCount; s < rawSectionCount; s++) {
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.BOOL.decode(buf);
         int skipTitleArgs = (Integer)ByteBufCodecs.VAR_INT.decode(buf);

         for (int ta = 0; ta < skipTitleArgs; ta++) {
            ByteBufCodecs.STRING_UTF8.decode(buf);
         }

         int skipLineCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);

         for (int i = 0; i < skipLineCount; i++) {
            ByteBufCodecs.STRING_UTF8.decode(buf);
            ByteBufCodecs.STRING_UTF8.decode(buf);
            ByteBufCodecs.STRING_UTF8.decode(buf);
            ByteBufCodecs.BOOL.decode(buf);
            ByteBufCodecs.BOOL.decode(buf);
            int skipArgs = (Integer)ByteBufCodecs.VAR_INT.decode(buf);

            for (int a = 0; a < skipArgs; a++) {
               ByteBufCodecs.STRING_UTF8.decode(buf);
            }

            ByteBufCodecs.VAR_INT.decode(buf);
            ByteBufCodecs.BOOL.decode(buf);
            ByteBufCodecs.BYTE.decode(buf);
            ByteBufCodecs.STRING_UTF8.decode(buf);
         }
      }

      List<MapData.MapBuilding> buildings = List.of();
      List<MapData.MapVillager> villagers = List.of();
      int playerX = 0;
      int playerZ = 0;
      int centerX = 0;
      int centerZ = 0;
      MapData.MapTerrain terrain = MapData.MapTerrain.EMPTY;
      List<MapData.MapPath> paths = List.of();
      if (hasMapData) {
         buildings = MapData.decodeBuildingList(buf, 256);
         villagers = MapData.decodeVillagerList(buf, 256);
         playerX = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         playerZ = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         centerX = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         centerZ = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         terrain = MapData.decodeTerrain(buf);
         paths = MapData.decodePathList(buf, 8192);
      }

      return new VillageBookPayload(
         villageName, cultureId, sections, buildings, villagers, playerX, playerZ, centerX, centerZ, terrain, paths, hasMapData, degraded
      );
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
