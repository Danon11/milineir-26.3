package org.millenaire.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.millenaire.village.panel.PanelContent;
import org.millenaire.village.panel.PanelLine;
import org.millenaire.village.panel.PanelType;

public record PanelContentPayload(
   String panelTypeName,
   String title,
   List<String> lineTexts,
   List<Boolean> lineSeparators,
   List<String> lineLeftColumns,
   List<String> lineRightColumns,
   List<String> lineLeftIcons,
   List<Boolean> lineTranslatables,
   List<String> lineNavTargets,
   List<String[]> lineTranslatableArgs,
   List<Integer> lineColors,
   List<Boolean> lineBolds,
   List<Integer> lineArgMasks,
   List<String> lineNativePrefixes,
   List<ItemStack> lineLeftIconStacks,
   boolean titleTranslatable,
   List<String> titleArgs,
   List<MapData.MapBuilding> mapBuildings,
   List<MapData.MapVillager> mapVillagers,
   int mapPlayerX,
   int mapPlayerZ,
   int mapCenterX,
   int mapCenterZ,
   MapData.MapTerrain mapTerrain,
   List<MapData.MapChunk> forceLoadedChunks,
   List<MapData.MapPath> mapPaths
) implements CustomPacketPayload {
   private static final int MAX_LINES = 512;
   private static final int MAX_PATHS = 8192;
   private static final int MAX_TITLE_ARGS = 4;
   private static final String NAV_SEP = "|";
   public static final Type<PanelContentPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "panel_content"));
   public static final StreamCodec<RegistryFriendlyByteBuf, PanelContentPayload> STREAM_CODEC = StreamCodec.of(
      PanelContentPayload::encode, PanelContentPayload::decode
   );
   private static final int MAX_ARGS = 8;

   public static PanelContentPayload fromContent(PanelContent content) {
      return fromContentWithMap(content, List.of(), List.of(), 0, 0, 0, 0, MapData.MapTerrain.EMPTY, List.of(), List.of());
   }

   public static PanelContentPayload fromContentWithMap(
      PanelContent content,
      List<MapData.MapBuilding> buildings,
      List<MapData.MapVillager> villagers,
      int playerX,
      int playerZ,
      int centerX,
      int centerZ,
      MapData.MapTerrain terrain,
      List<MapData.MapChunk> forceLoadedChunks,
      List<MapData.MapPath> mapPaths
   ) {
      int size = content.lines().size();
      List<String> texts = new ArrayList<>(size);
      List<Boolean> separators = new ArrayList<>(size);
      List<String> leftCols = new ArrayList<>(size);
      List<String> rightCols = new ArrayList<>(size);
      List<String> leftIcons = new ArrayList<>(size);
      List<Boolean> translatables = new ArrayList<>(size);
      List<String> navTargets = new ArrayList<>(size);
      List<String[]> transArgsList = new ArrayList<>(size);
      List<Integer> colors = new ArrayList<>(size);
      List<Boolean> bolds = new ArrayList<>(size);
      List<Integer> argMasks = new ArrayList<>(size);
      List<String> nativePrefixes = new ArrayList<>(size);
      List<ItemStack> leftIconStacks = new ArrayList<>(size);

      for (PanelLine line : content.lines()) {
         texts.add(line.text());
         separators.add(line.isSeparator());
         leftCols.add(line.leftColumn() != null ? line.leftColumn() : "");
         rightCols.add(line.rightColumn() != null ? line.rightColumn() : "");
         leftIcons.add(line.leftIcon() != null ? line.leftIcon() : "");
         translatables.add(line.translatable());
         navTargets.add(encodeNavTarget(line.navTarget()));
         transArgsList.add(line.translatableArgs());
         colors.add(line.color());
         bolds.add(line.bold());
         argMasks.add(line.translatableArgMask());
         nativePrefixes.add(line.nativePrefix() != null ? line.nativePrefix() : "");
         leftIconStacks.add(line.leftIconStack() != null ? line.leftIconStack() : ItemStack.EMPTY);
      }

      List<String> titleArgsList = content.titleArgs() != null ? List.of(content.titleArgs()) : List.of();
      return new PanelContentPayload(
         content.type().name(),
         content.title(),
         texts,
         separators,
         leftCols,
         rightCols,
         leftIcons,
         translatables,
         navTargets,
         transArgsList,
         colors,
         bolds,
         argMasks,
         nativePrefixes,
         leftIconStacks,
         content.titleTranslatable(),
         titleArgsList,
         buildings,
         villagers,
         playerX,
         playerZ,
         centerX,
         centerZ,
         terrain,
         forceLoadedChunks,
         mapPaths
      );
   }

   public boolean hasMapData() {
      return !this.mapBuildings.isEmpty() || !this.forceLoadedChunks.isEmpty();
   }

   public PanelContent toContent() {
      PanelType type = PanelType.fromName(this.panelTypeName);
      List<PanelLine> lines = new ArrayList<>(this.lineTexts.size());

      for (int i = 0; i < this.lineTexts.size(); i++) {
         boolean sep = i < this.lineSeparators.size() && this.lineSeparators.get(i);
         String leftCol = i < this.lineLeftColumns.size() ? this.lineLeftColumns.get(i) : "";
         String rightCol = i < this.lineRightColumns.size() ? this.lineRightColumns.get(i) : "";
         String icon = i < this.lineLeftIcons.size() ? this.lineLeftIcons.get(i) : "";
         boolean trans = i < this.lineTranslatables.size() && this.lineTranslatables.get(i);
         String navStr = i < this.lineNavTargets.size() ? this.lineNavTargets.get(i) : "";
         PanelLine.PanelNavTarget nav = decodeNavTarget(navStr);
         String[] transArgs = i < this.lineTranslatableArgs.size() ? this.lineTranslatableArgs.get(i) : null;
         int color = i < this.lineColors.size() ? this.lineColors.get(i) : -1;
         boolean bold = i < this.lineBolds.size() && this.lineBolds.get(i);
         int argMask = i < this.lineArgMasks.size() ? this.lineArgMasks.get(i) : 0;
         String nativePfx = i < this.lineNativePrefixes.size() ? this.lineNativePrefixes.get(i) : "";
         String nativePrefixVal = nativePfx.isEmpty() ? null : nativePfx;
         ItemStack iconStack = i < this.lineLeftIconStacks.size() ? this.lineLeftIconStacks.get(i) : ItemStack.EMPTY;
         if (iconStack == null) {
            iconStack = ItemStack.EMPTY;
         }

         if (!leftCol.isEmpty()) {
            String iconVal = icon.isEmpty() ? null : icon;
            lines.add(new PanelLine("", sep, leftCol, rightCol, iconVal, trans, nav, transArgs, color, bold, argMask, nativePrefixVal, iconStack));
         } else if (!icon.isEmpty()) {
            String rightColVal = rightCol.isEmpty() ? null : rightCol;
            lines.add(
               new PanelLine(this.lineTexts.get(i), sep, null, rightColVal, icon, trans, nav, transArgs, color, bold, argMask, nativePrefixVal, iconStack)
            );
         } else {
            lines.add(new PanelLine(this.lineTexts.get(i), sep, null, null, null, trans, nav, transArgs, color, bold, argMask, nativePrefixVal, iconStack));
         }
      }

      String[] tArgs = this.titleArgs != null && !this.titleArgs.isEmpty() ? this.titleArgs.toArray(new String[0]) : null;
      return new PanelContent(type, this.title, lines, this.titleTranslatable, tArgs);
   }

   private static String encodeNavTarget(PanelLine.PanelNavTarget target) {
      return target == null ? "" : target.targetStateName() + "|" + target.cultureKey() + "|" + target.categoryKey() + "|" + target.itemKey();
   }

   private static PanelLine.PanelNavTarget decodeNavTarget(String encoded) {
      if (encoded != null && !encoded.isEmpty()) {
         String[] parts = encoded.split("\\|", -1);
         return parts.length < 4 ? null : new PanelLine.PanelNavTarget(parts[0], parts[1], parts[2], parts[3]);
      } else {
         return null;
      }
   }

   private static void encode(RegistryFriendlyByteBuf buf, PanelContentPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.panelTypeName);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.title);
      ByteBufCodecs.BOOL.encode(buf, payload.titleTranslatable);
      int titleArgCount = payload.titleArgs != null ? payload.titleArgs.size() : 0;
      ByteBufCodecs.VAR_INT.encode(buf, titleArgCount);

      for (int i = 0; i < titleArgCount; i++) {
         ByteBufCodecs.STRING_UTF8.encode(buf, payload.titleArgs.get(i));
      }

      ByteBufCodecs.VAR_INT.encode(buf, payload.lineTexts.size());

      for (int i = 0; i < payload.lineTexts.size(); i++) {
         ByteBufCodecs.STRING_UTF8.encode(buf, payload.lineTexts.get(i));
         ByteBufCodecs.BOOL.encode(buf, i < payload.lineSeparators.size() && payload.lineSeparators.get(i));
         ByteBufCodecs.STRING_UTF8.encode(buf, i < payload.lineLeftColumns.size() ? payload.lineLeftColumns.get(i) : "");
         ByteBufCodecs.STRING_UTF8.encode(buf, i < payload.lineRightColumns.size() ? payload.lineRightColumns.get(i) : "");
         ByteBufCodecs.STRING_UTF8.encode(buf, i < payload.lineLeftIcons.size() ? payload.lineLeftIcons.get(i) : "");
         ByteBufCodecs.BOOL.encode(buf, i < payload.lineTranslatables.size() && payload.lineTranslatables.get(i));
         ByteBufCodecs.STRING_UTF8.encode(buf, i < payload.lineNavTargets.size() ? payload.lineNavTargets.get(i) : "");
         String[] args = i < payload.lineTranslatableArgs.size() ? payload.lineTranslatableArgs.get(i) : null;
         int argsCount = args != null ? args.length : 0;
         ByteBufCodecs.VAR_INT.encode(buf, argsCount);
         if (args != null) {
            for (String arg : args) {
               ByteBufCodecs.STRING_UTF8.encode(buf, arg != null ? arg : "");
            }
         }

         ByteBufCodecs.VAR_INT.encode(buf, i < payload.lineColors.size() ? payload.lineColors.get(i) : -1);
         ByteBufCodecs.BOOL.encode(buf, i < payload.lineBolds.size() && payload.lineBolds.get(i));
         ByteBufCodecs.BYTE.encode(buf, (byte)(i < payload.lineArgMasks.size() ? payload.lineArgMasks.get(i) : 0));
         ByteBufCodecs.STRING_UTF8.encode(buf, i < payload.lineNativePrefixes.size() ? payload.lineNativePrefixes.get(i) : "");
         ItemStack iconStack = i < payload.lineLeftIconStacks.size() ? payload.lineLeftIconStacks.get(i) : ItemStack.EMPTY;
         if (iconStack == null) {
            iconStack = ItemStack.EMPTY;
         }

         ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, iconStack);
      }

      MapData.encodeBuildingList(buf, payload.mapBuildings);
      MapData.encodeVillagerList(buf, payload.mapVillagers);
      ByteBufCodecs.VAR_INT.encode(buf, payload.mapPlayerX);
      ByteBufCodecs.VAR_INT.encode(buf, payload.mapPlayerZ);
      ByteBufCodecs.VAR_INT.encode(buf, payload.mapCenterX);
      ByteBufCodecs.VAR_INT.encode(buf, payload.mapCenterZ);
      MapData.encodeTerrain(buf, payload.mapTerrain);
      MapData.encodeChunkList(buf, payload.forceLoadedChunks);
      MapData.encodePathList(buf, payload.mapPaths);
   }

   private static PanelContentPayload decode(RegistryFriendlyByteBuf buf) {
      String typeName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String title = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      boolean titleTranslatable = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      int rawTitleArgCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int titleArgCount = Math.min(rawTitleArgCount, 4);
      List<String> titleArgs = new ArrayList<>(titleArgCount);

      for (int i = 0; i < titleArgCount; i++) {
         titleArgs.add((String)ByteBufCodecs.STRING_UTF8.decode(buf));
      }

      for (int i = titleArgCount; i < rawTitleArgCount; i++) {
         ByteBufCodecs.STRING_UTF8.decode(buf);
      }

      int rawCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int count = Math.min(rawCount, 512);
      List<String> texts = new ArrayList<>(count);
      List<Boolean> seps = new ArrayList<>(count);
      List<String> leftCols = new ArrayList<>(count);
      List<String> rightCols = new ArrayList<>(count);
      List<String> leftIcons = new ArrayList<>(count);
      List<Boolean> translatables = new ArrayList<>(count);
      List<String> navTargets = new ArrayList<>(count);
      List<String[]> transArgsList = new ArrayList<>(count);
      List<Integer> colors = new ArrayList<>(count);
      List<Boolean> bolds = new ArrayList<>(count);
      List<Integer> argMasks = new ArrayList<>(count);
      List<String> nativePrefixes = new ArrayList<>(count);
      List<ItemStack> iconStacks = new ArrayList<>(count);

      for (int i = 0; i < count; i++) {
         texts.add((String)ByteBufCodecs.STRING_UTF8.decode(buf));
         seps.add((Boolean)ByteBufCodecs.BOOL.decode(buf));
         leftCols.add((String)ByteBufCodecs.STRING_UTF8.decode(buf));
         rightCols.add((String)ByteBufCodecs.STRING_UTF8.decode(buf));
         leftIcons.add((String)ByteBufCodecs.STRING_UTF8.decode(buf));
         translatables.add((Boolean)ByteBufCodecs.BOOL.decode(buf));
         navTargets.add((String)ByteBufCodecs.STRING_UTF8.decode(buf));
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

         transArgsList.add(transArgs);
         colors.add((Integer)ByteBufCodecs.VAR_INT.decode(buf));
         bolds.add((Boolean)ByteBufCodecs.BOOL.decode(buf));
         argMasks.add((Byte)ByteBufCodecs.BYTE.decode(buf) & 255);
         nativePrefixes.add((String)ByteBufCodecs.STRING_UTF8.decode(buf));
         iconStacks.add((ItemStack)ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
      }

      for (int i = count; i < rawCount; i++) {
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.BOOL.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.BOOL.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
         int skipArgs = (Integer)ByteBufCodecs.VAR_INT.decode(buf);

         for (int a = 0; a < skipArgs; a++) {
            ByteBufCodecs.STRING_UTF8.decode(buf);
         }

         ByteBufCodecs.VAR_INT.decode(buf);
         ByteBufCodecs.BOOL.decode(buf);
         ByteBufCodecs.BYTE.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
      }

      List<MapData.MapBuilding> buildings = MapData.decodeBuildingList(buf, 256);
      List<MapData.MapVillager> villagers = MapData.decodeVillagerList(buf, 256);
      int playerX = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int playerZ = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int centerX = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int centerZ = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      MapData.MapTerrain terrain = MapData.decodeTerrain(buf);
      List<MapData.MapChunk> chunks = MapData.decodeChunkList(buf, 1024);
      List<MapData.MapPath> paths = MapData.decodePathList(buf, 8192);
      return new PanelContentPayload(
         typeName,
         title,
         texts,
         seps,
         leftCols,
         rightCols,
         leftIcons,
         translatables,
         navTargets,
         transArgsList,
         colors,
         bolds,
         argMasks,
         nativePrefixes,
         iconStacks,
         titleTranslatable,
         titleArgs,
         buildings,
         villagers,
         playerX,
         playerZ,
         centerX,
         centerZ,
         terrain,
         chunks,
         paths
      );
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
