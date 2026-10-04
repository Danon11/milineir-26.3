package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.codec.ByteBufCodecs;

public final class MapData {
   public static final byte TERRAIN_EMPTY = 0;
   public static final byte TERRAIN_WATER = 1;
   public static final byte TERRAIN_DANGER = 2;
   public static final byte TERRAIN_FORBIDDEN = 3;
   public static final byte TERRAIN_UNBUILDABLE = 4;
   public static final byte TERRAIN_BUILDABLE = 5;
   public static final byte TERRAIN_OCCUPIED = 6;

   private MapData() {
   }

   public static void encodeChunkList(ByteBuf buf, List<MapData.MapChunk> chunks) {
      ByteBufCodecs.VAR_INT.encode(buf, chunks.size());

      for (MapData.MapChunk c : chunks) {
         ByteBufCodecs.VAR_INT.encode(buf, c.chunkX());
         ByteBufCodecs.VAR_INT.encode(buf, c.chunkZ());
         ByteBufCodecs.BOOL.encode(buf, c.loaded());
      }
   }

   public static List<MapData.MapChunk> decodeChunkList(ByteBuf buf, int maxEntries) {
      int rawCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int count = Math.min(rawCount, maxEntries);
      List<MapData.MapChunk> chunks = new ArrayList<>(count);

      for (int i = 0; i < rawCount; i++) {
         int cx = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int cz = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         boolean loaded = (Boolean)ByteBufCodecs.BOOL.decode(buf);
         if (i < count) {
            chunks.add(new MapData.MapChunk(cx, cz, loaded));
         }
      }

      return chunks;
   }

   public static void encodeBuildingList(ByteBuf buf, List<MapData.MapBuilding> buildings) {
      ByteBufCodecs.VAR_INT.encode(buf, buildings.size());

      for (MapData.MapBuilding b : buildings) {
         ByteBufCodecs.VAR_INT.encode(buf, b.x());
         ByteBufCodecs.VAR_INT.encode(buf, b.z());
         ByteBufCodecs.VAR_INT.encode(buf, b.width());
         ByteBufCodecs.VAR_INT.encode(buf, b.depth());
         ByteBufCodecs.STRING_UTF8.encode(buf, b.name());
         ByteBufCodecs.STRING_UTF8.encode(buf, b.status());
         ByteBufCodecs.VAR_INT.encode(buf, b.level());
         ByteBufCodecs.BOOL.encode(buf, b.nameTranslatable());
         ByteBufCodecs.STRING_UTF8.encode(buf, b.nativePrefix() != null ? b.nativePrefix() : "");
         ByteBufCodecs.BOOL.encode(buf, b.isWall());
      }
   }

   public static List<MapData.MapBuilding> decodeBuildingList(ByteBuf buf, int maxEntries) {
      int rawCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int count = Math.min(rawCount, maxEntries);
      List<MapData.MapBuilding> buildings = new ArrayList<>(count);

      for (int i = 0; i < rawCount; i++) {
         int x = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int z = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int w = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int d = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         String name = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         String status = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         int level = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         boolean nameTranslatable = (Boolean)ByteBufCodecs.BOOL.decode(buf);
         String nativePrefix = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         String nativePrefixVal = nativePrefix.isEmpty() ? null : nativePrefix;
         boolean isWall = (Boolean)ByteBufCodecs.BOOL.decode(buf);
         if (i < count) {
            buildings.add(new MapData.MapBuilding(x, z, w, d, name, status, level, nameTranslatable, nativePrefixVal, isWall));
         }
      }

      return buildings;
   }

   public static void encodeVillagerList(ByteBuf buf, List<MapData.MapVillager> villagers) {
      ByteBufCodecs.VAR_INT.encode(buf, villagers.size());

      for (MapData.MapVillager v : villagers) {
         ByteBufCodecs.VAR_INT.encode(buf, v.x());
         ByteBufCodecs.VAR_INT.encode(buf, v.z());
         ByteBufCodecs.STRING_UTF8.encode(buf, v.name());
         ByteBufCodecs.STRING_UTF8.encode(buf, v.gender());
         ByteBufCodecs.STRING_UTF8.encode(buf, v.role());
         ByteBufCodecs.STRING_UTF8.encode(buf, v.goalLabel());
         ByteBufCodecs.BOOL.encode(buf, v.isChief());
      }
   }

   public static void encodeTerrain(ByteBuf buf, MapData.MapTerrain terrain) {
      ByteBufCodecs.VAR_INT.encode(buf, terrain.startX());
      ByteBufCodecs.VAR_INT.encode(buf, terrain.startZ());
      ByteBufCodecs.VAR_INT.encode(buf, terrain.width());
      ByteBufCodecs.VAR_INT.encode(buf, terrain.depth());
      byte[] data = terrain.data();
      if (data.length == 0) {
         ByteBufCodecs.VAR_INT.encode(buf, 0);
      } else {
         List<byte[]> rleChunks = new ArrayList<>();
         int i = 0;

         while (i < data.length) {
            byte val = data[i];
            int run = 1;

            while (i + run < data.length && data[i + run] == val && run < 255) {
               run++;
            }

            rleChunks.add(new byte[]{(byte)run, val});
            i += run;
         }

         ByteBufCodecs.VAR_INT.encode(buf, rleChunks.size());

         for (byte[] chunk : rleChunks) {
            buf.writeByte(chunk[0]);
            buf.writeByte(chunk[1]);
         }
      }
   }

   public static MapData.MapTerrain decodeTerrain(ByteBuf buf) {
      int startX = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int startZ = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int width = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int depth = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int rleCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      if (rleCount != 0 && width > 0 && depth > 0 && width <= 512 && depth <= 512) {
         int totalSize = width * depth;
         byte[] data = new byte[totalSize];
         int pos = 0;

         for (int r = 0; r < rleCount; r++) {
            int run = buf.readByte() & 255;
            byte val = buf.readByte();
            int end = Math.min(pos + run, totalSize);

            for (int j = pos; j < end; j++) {
               data[j] = val;
            }

            pos = end;
         }

         return new MapData.MapTerrain(startX, startZ, width, depth, data);
      } else {
         for (int r = 0; r < rleCount; r++) {
            buf.readByte();
            buf.readByte();
         }

         return MapData.MapTerrain.EMPTY;
      }
   }

   public static List<MapData.MapVillager> decodeVillagerList(ByteBuf buf, int maxEntries) {
      int rawCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int count = Math.min(rawCount, maxEntries);
      List<MapData.MapVillager> villagers = new ArrayList<>(count);

      for (int i = 0; i < rawCount; i++) {
         int x = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int z = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         String name = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         String gender = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         String role = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         String goalLabel = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         boolean isChief = (Boolean)ByteBufCodecs.BOOL.decode(buf);
         if (i < count) {
            villagers.add(new MapData.MapVillager(x, z, name, gender, role, goalLabel, isChief));
         }
      }

      return villagers;
   }

   public static void encodePathList(ByteBuf buf, List<MapData.MapPath> paths) {
      ByteBufCodecs.VAR_INT.encode(buf, paths.size());

      for (MapData.MapPath p : paths) {
         ByteBufCodecs.VAR_INT.encode(buf, p.x());
         ByteBufCodecs.VAR_INT.encode(buf, p.z());
         buf.writeByte(p.level());
      }
   }

   public static List<MapData.MapPath> decodePathList(ByteBuf buf, int maxEntries) {
      int rawCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int count = Math.min(rawCount, maxEntries);
      List<MapData.MapPath> paths = new ArrayList<>(count);

      for (int i = 0; i < rawCount; i++) {
         int x = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int z = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         byte level = buf.readByte();
         if (i < count) {
            paths.add(new MapData.MapPath(x, z, level));
         }
      }

      return paths;
   }

   public record MapBuilding(
      int x, int z, int width, int depth, String name, String status, int level, boolean nameTranslatable, @Nullable String nativePrefix, boolean isWall
   ) {
      public MapBuilding(int x, int z, int width, int depth, String name, String status, int level, boolean nameTranslatable, @Nullable String nativePrefix) {
         this(x, z, width, depth, name, status, level, nameTranslatable, nativePrefix, false);
      }

      public MapBuilding(int x, int z, int width, int depth, String name, String status, int level, boolean nameTranslatable) {
         this(x, z, width, depth, name, status, level, nameTranslatable, null, false);
      }

      public MapBuilding(int x, int z, int width, int depth, String name, String status, int level) {
         this(x, z, width, depth, name, status, level, false, null, false);
      }
   }

   public record MapChunk(int chunkX, int chunkZ, boolean loaded) {
   }

   public record MapPath(int x, int z, byte level) {
   }

   public record MapTerrain(int startX, int startZ, int width, int depth, byte[] data) {
      public static final MapData.MapTerrain EMPTY = new MapData.MapTerrain(0, 0, 0, 0, new byte[0]);

      public byte tileAt(int worldX, int worldZ) {
         int lx = worldX - this.startX;
         int lz = worldZ - this.startZ;
         return lx >= 0 && lx < this.width && lz >= 0 && lz < this.depth ? this.data[lx * this.depth + lz] : 0;
      }
   }

   public record MapVillager(int x, int z, String name, String gender, String role, String goalLabel, boolean isChief) {
   }
}
