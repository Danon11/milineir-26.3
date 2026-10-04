package org.millenaire.command.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.millenaire.village.Village;

public final class ScanExporter {
   private static final Map<Block, Character> BLOCK_CHAR_MAP = new LinkedHashMap<>();

   private ScanExporter() {
   }

   public static Path export(ServerLevel level, Village village, int radius, Path dir) throws IOException {
      Path file = dir.resolve("village-scan.txt");
      BlockPos center = village.getCenter();
      int centerX = center.getX();
      int centerZ = center.getZ();
      int surfaceY = level.getHeight(Types.WORLD_SURFACE, centerX, centerZ) - 1;
      int[] layers = new int[]{surfaceY - 1, surfaceY, surfaceY + 1, surfaceY + 2, surfaceY + 3, surfaceY + 4};
      String[] layerLabels = new String[]{"foundation", "surface", "surface+1", "surface+2", "surface+3", "surface+4"};
      int minX = centerX - radius;
      int maxX = centerX + radius;
      int minZ = centerZ - radius;
      int maxZ = centerZ + radius;
      Set<Block> seenBlocks = new LinkedHashSet<>();
      StringBuilder sb = new StringBuilder();
      sb.append("=== Scan @ center (%d, %d, %d) radius=%d ===\n".formatted(centerX, surfaceY, centerZ, radius));
      sb.append("Village: %s\n".formatted(village.getId()));
      sb.append("Surface Y: %d\n".formatted(surfaceY));
      int zLabelWidth = Math.max(String.valueOf(minZ).length(), String.valueOf(maxZ).length());

      for (int layerIdx = 0; layerIdx < layers.length; layerIdx++) {
         int y = layers[layerIdx];
         sb.append("\n--- Y=%d (%s) ---\n".formatted(y, layerLabels[layerIdx]));
         sb.append(" ".repeat(zLabelWidth + 1));

         for (int x = minX; x <= maxX; x++) {
            if (x % 5 == 0) {
               String label = String.valueOf(x);
               sb.append(label);
               int skip = label.length() - 1;
               x += skip;
            } else {
               sb.append(' ');
            }
         }

         sb.append('\n');

         for (int z = minZ; z <= maxZ; z++) {
            sb.append(String.format("%" + zLabelWidth + "d ", z));

            for (int x = minX; x <= maxX; x++) {
               Block block = level.getBlockState(new BlockPos(x, y, z)).getBlock();
               Character ch = BLOCK_CHAR_MAP.get(block);
               if (ch != null) {
                  sb.append(ch);
               } else {
                  sb.append('?');
               }

               if (block != Blocks.AIR && block != Blocks.CAVE_AIR) {
                  seenBlocks.add(block);
               }
            }

            sb.append('\n');
         }
      }

      sb.append("\nLegend:\n");

      for (Block block : seenBlocks) {
         Character ch = BLOCK_CHAR_MAP.get(block);
         String symbol = ch != null ? String.valueOf(ch) : "?";
         String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
         sb.append("  %s = %s\n".formatted(symbol, name));
      }

      Files.writeString(file, sb.toString());
      return file;
   }

   static {
      BLOCK_CHAR_MAP.put(Blocks.AIR, ' ');
      BLOCK_CHAR_MAP.put(Blocks.CAVE_AIR, ' ');
      BLOCK_CHAR_MAP.put(Blocks.GRASS_BLOCK, 'G');
      BLOCK_CHAR_MAP.put(Blocks.DIRT, 'D');
      BLOCK_CHAR_MAP.put(Blocks.STONE, 'S');
      BLOCK_CHAR_MAP.put(Blocks.COBBLESTONE, 'C');
      BLOCK_CHAR_MAP.put(Blocks.OAK_PLANKS, 'P');
      BLOCK_CHAR_MAP.put(Blocks.SPRUCE_PLANKS, 'P');
      BLOCK_CHAR_MAP.put(Blocks.OAK_LOG, 'L');
      BLOCK_CHAR_MAP.put(Blocks.SPRUCE_LOG, 'L');
      BLOCK_CHAR_MAP.put(Blocks.OAK_STAIRS, '/');
      BLOCK_CHAR_MAP.put(Blocks.COBBLESTONE_STAIRS, '/');
      BLOCK_CHAR_MAP.put(Blocks.STONE_STAIRS, '/');
      BLOCK_CHAR_MAP.put(Blocks.OAK_SLAB, '-');
      BLOCK_CHAR_MAP.put(Blocks.COBBLESTONE_SLAB, '-');
      BLOCK_CHAR_MAP.put(Blocks.OAK_FENCE, '|');
      BLOCK_CHAR_MAP.put(Blocks.OAK_DOOR, 'd');
      BLOCK_CHAR_MAP.put(Blocks.GLASS_PANE, '=');
      BLOCK_CHAR_MAP.put(Blocks.GLASS, '=');
      BLOCK_CHAR_MAP.put(Blocks.CHEST, '$');
      BLOCK_CHAR_MAP.put(Blocks.CRAFTING_TABLE, 'T');
      BLOCK_CHAR_MAP.put(Blocks.FURNACE, 'F');
      BLOCK_CHAR_MAP.put(Blocks.WATER, '~');
      BLOCK_CHAR_MAP.put(Blocks.FARMLAND, 'f');
      BLOCK_CHAR_MAP.put(Blocks.WHEAT, 'w');
      BLOCK_CHAR_MAP.put(Blocks.TORCH, 't');
      BLOCK_CHAR_MAP.put(Blocks.WALL_TORCH, 't');
      BLOCK_CHAR_MAP.put(Blocks.OAK_LEAVES, '@');
      BLOCK_CHAR_MAP.put(Blocks.SPRUCE_LEAVES, '@');
   }
}
