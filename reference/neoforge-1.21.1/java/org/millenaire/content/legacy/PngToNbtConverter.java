package org.millenaire.content.legacy;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

public class PngToNbtConverter {
   private static final int DATA_VERSION = computeDataVersion();
   static final int DATA_VERSION_FALLBACK = 3955;
   private static final int WHITE = rgb(255, 255, 255);
   private static final int GREEN_PRESERVE = rgb(0, 200, 0);
   private static final int ALLBUTTREES = rgb(150, 255, 150);
   private static final int GRASS_SPECIAL = rgb(0, 128, 0);
   private final Map<Integer, PngToNbtConverter.BlockState> colorMap = new HashMap<>();
   private final Set<Integer> specialColors = new HashSet<>();
   private final Map<Integer, PngToNbtConverter.SpecialPointInfo> specialPointMap = new HashMap<>();
   private final ConversionMode mode;
   private final Set<String> unmappedSpecialPointSignatures = new LinkedHashSet<>();
   private final Set<Integer> unmappedColourSet = new LinkedHashSet<>();

   static int computeDataVersion() {
      try {
         return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
      } catch (NullPointerException | NoClassDefFoundError | ExceptionInInitializerError | IllegalStateException e) {
         return 3955;
      }
   }

   public PngToNbtConverter(ConversionMode mode) {
      this.mode = mode;
   }

   @Deprecated
   public PngToNbtConverter() {
      this(ConversionMode.AUTO);
   }

   public Set<String> drainUnmappedSpecialPoints() {
      Set<String> out = new LinkedHashSet<>(this.unmappedSpecialPointSignatures);
      this.unmappedSpecialPointSignatures.clear();
      return out;
   }

   public Set<Integer> drainUnmappedColours() {
      Set<Integer> copy = new LinkedHashSet<>(this.unmappedColourSet);
      this.unmappedColourSet.clear();
      return copy;
   }

   public void clearPerCultureState() {
      this.unmappedColourSet.clear();
      this.unmappedSpecialPointSignatures.clear();
   }

   public void loadBlocklist(Path blocklistPath, Set<Integer> usedColors) throws IOException {
      List<PngToNbtConverter.BlocklistEntry> entries = this.parseBlocklist(blocklistPath);
      this.applyBlocklist(entries, usedColors);
   }

   public void loadBlocklist(InputStream blocklistStream) throws IOException {
      this.loadBlocklist(blocklistStream, null);
   }

   public void loadBlocklist(InputStream blocklistStream, Set<Integer> usedColors) throws IOException {
      List<PngToNbtConverter.BlocklistEntry> entries = this.parseBlocklist(blocklistStream);
      this.applyBlocklist(entries, usedColors);
   }

   private void applyBlocklist(List<PngToNbtConverter.BlocklistEntry> entries, Set<Integer> usedColors) {
      this.specialColors.add(WHITE);
      this.addSpecialPoint(GREEN_PRESERVE, "preserve_ground", "surface", null, null);
      this.addSpecialPoint(rgb(150, 255, 150), "preserve_ground", "allbuttrees", null, null);
      this.addSpecialPoint(rgb(178, 255, 177), "preserve_ground", "allbuttrees", null, null);
      this.addSpecialPoint(GRASS_SPECIAL, "preserve_ground", "grass", null, null);
      this.addSpecialPoint(rgb(255, 255, 0), "torchGuess", null, "guess", null);
      this.addSpecialPoint(rgb(0, 128, 255), "sleepingPos", null, null, null);
      this.addSpecialPoint(rgb(200, 0, 0), "sellingPos", null, null, null);
      this.addSpecialPoint(rgb(200, 125, 0), "craftingPos", null, null, null);
      this.addSpecialPoint(rgb(200, 150, 0), "defendingPos", null, null, null);
      this.addSpecialPoint(rgb(200, 175, 0), "shelterPos", null, null, null);
      this.addSpecialPoint(rgb(200, 250, 0), "pathStartPos", null, null, null);
      this.addSpecialPoint(rgb(50, 50, 250), "leisurePos", null, null, null);
      Map<Integer, PngToNbtConverter.BlocklistEntry> rgbToEntry = new LinkedHashMap<>();

      for (PngToNbtConverter.BlocklistEntry entry : entries) {
         rgbToEntry.put(entry.rgb, entry);
      }

      for (PngToNbtConverter.BlocklistEntry entry : entries) {
         PngToNbtConverter.SpecialPointInfo spInfo = this.mapBlocklistToSpecialPoint(entry);
         if (spInfo != null) {
            this.addSpecialPoint(entry.rgb, spInfo.type, spInfo.subtype, spInfo.orientation, spInfo.placement);
         }
      }

      for (int rgb : usedColors != null ? usedColors : rgbToEntry.keySet()) {
         if ((!this.specialColors.contains(rgb) || this.specialPointMap.containsKey(rgb)) && !this.colorMap.containsKey(rgb)) {
            PngToNbtConverter.BlocklistEntry entry = rgbToEntry.get(rgb);
            if (entry != null) {
               PngToNbtConverter.SpecialPointInfo spInfo = this.mapBlocklistToSpecialPoint(entry);
               if (spInfo == null || this.specialPointToMockBlockState(spInfo) == null) {
                  PngToNbtConverter.BlockState special = this.mapSpecialEntry(entry);
                  if (special != null) {
                     this.colorMap.put(rgb, special);
                  } else {
                     PngToNbtConverter.BlockState state = this.mapTo121(entry);
                     if (state != null) {
                        this.colorMap.put(rgb, state);
                     }
                  }
               }
            }
         }
      }
   }

   private void addSpecialPoint(int rgb, String type, String subtype, String orientation, String placement) {
      this.specialColors.add(rgb);
      this.specialPointMap.put(rgb, new PngToNbtConverter.SpecialPointInfo(type, subtype, orientation, placement));
   }

   private PngToNbtConverter.SpecialPointInfo mapBlocklistToSpecialPoint(PngToNbtConverter.BlocklistEntry entry) {
      String name = entry.name.toLowerCase();
      if (name.startsWith("mainchest")) {
         String orientation = this.extractLegacyOrientation(name, "mainchest");
         return new PngToNbtConverter.SpecialPointInfo("chest", "locked", orientation, null);
      }

      if (name.startsWith("lockedchest")) {
         String orientation = this.extractLegacyOrientation(name, "lockedchest");
         return new PngToNbtConverter.SpecialPointInfo("chest", "locked", orientation, null);
      }

      if (name.equals("soil")) {
         return new PngToNbtConverter.SpecialPointInfo("soil", "wheat", null, null);
      }

      if (name.equals("ricesoil")) {
         return new PngToNbtConverter.SpecialPointInfo("soil", "rice", null, null);
      }

      if (name.equals("turmericsoil")) {
         return new PngToNbtConverter.SpecialPointInfo("soil", "turmeric", null, null);
      }

      if (name.equals("sugarcanesoil")) {
         return new PngToNbtConverter.SpecialPointInfo("soil", "sugarcane", null, null);
      }

      if (name.equals("potatosoil")) {
         return new PngToNbtConverter.SpecialPointInfo("soil", "potato", null, null);
      }

      if (name.equals("netherwartsoil")) {
         return new PngToNbtConverter.SpecialPointInfo("soil", "netherwart", null, null);
      }

      if (name.equals("vinesoil")) {
         return new PngToNbtConverter.SpecialPointInfo("soil", "vine", null, null);
      }

      if (name.equals("maizesoil")) {
         return new PngToNbtConverter.SpecialPointInfo("soil", "maize", null, null);
      }

      if (name.equals("carrotsoil")) {
         return new PngToNbtConverter.SpecialPointInfo("soil", "carrot", null, null);
      }

      if (name.equals("flowersoil")) {
         return new PngToNbtConverter.SpecialPointInfo("soil", "flower", null, null);
      }

      if (name.equals("cottonsoil")) {
         return new PngToNbtConverter.SpecialPointInfo("soil", "cotton", null, null);
      }

      if (name.equals("oakspawn")) {
         return new PngToNbtConverter.SpecialPointInfo("treeSpawn", "oak", null, null);
      }

      if (name.equals("pinespawn")) {
         return new PngToNbtConverter.SpecialPointInfo("treeSpawn", "pine", null, null);
      }

      if (name.equals("birchspawn")) {
         return new PngToNbtConverter.SpecialPointInfo("treeSpawn", "birch", null, null);
      }

      if (name.equals("junglespawn")) {
         return new PngToNbtConverter.SpecialPointInfo("treeSpawn", "jungle", null, null);
      }

      if (name.equals("acaciaspawn")) {
         return new PngToNbtConverter.SpecialPointInfo("treeSpawn", "acacia", null, null);
      }

      if (name.equals("darkoakspawn")) {
         return new PngToNbtConverter.SpecialPointInfo("treeSpawn", "dark_oak", null, null);
      }

      if (name.equals("appletreespawn")) {
         return new PngToNbtConverter.SpecialPointInfo("treeSpawn", "apple", null, null);
      }

      if (name.equals("olivetreespawn")) {
         return new PngToNbtConverter.SpecialPointInfo("treeSpawn", "olive", null, null);
      }

      if (name.equals("pistachiotreespawn")) {
         return new PngToNbtConverter.SpecialPointInfo("treeSpawn", "pistachio", null, null);
      }

      if (name.equals("cowspawn")) {
         return new PngToNbtConverter.SpecialPointInfo("animalSpawn", "cow", null, null);
      }

      if (name.equals("pigspawn")) {
         return new PngToNbtConverter.SpecialPointInfo("animalSpawn", "pig", null, null);
      }

      if (name.equals("sheepspawn")) {
         return new PngToNbtConverter.SpecialPointInfo("animalSpawn", "sheep", null, null);
      }

      if (name.equals("chickenspawn")) {
         return new PngToNbtConverter.SpecialPointInfo("animalSpawn", "chicken", null, null);
      }

      if (name.equals("squidspawn")) {
         return new PngToNbtConverter.SpecialPointInfo("animalSpawn", "squid", null, null);
      }

      if (name.equals("wolfspawn")) {
         return new PngToNbtConverter.SpecialPointInfo("animalSpawn", "wolf", null, null);
      }

      if (name.equals("polarbearspawn")) {
         return new PngToNbtConverter.SpecialPointInfo("animalSpawn", "polar_bear", null, null);
      }

      if (name.equals("stonesource")) {
         return new PngToNbtConverter.SpecialPointInfo("source", "stone", null, null);
      }

      if (name.equals("sandsource")) {
         return new PngToNbtConverter.SpecialPointInfo("source", "sand", null, null);
      }

      if (name.equals("sandstonesource")) {
         return new PngToNbtConverter.SpecialPointInfo("source", "sandstone", null, null);
      }

      if (name.equals("claysource")) {
         return new PngToNbtConverter.SpecialPointInfo("source", "clay", null, null);
      }

      if (name.equals("gravelsource")) {
         return new PngToNbtConverter.SpecialPointInfo("source", "gravel", null, null);
      }

      if (name.equals("granitesource")) {
         return new PngToNbtConverter.SpecialPointInfo("source", "granite", null, null);
      }

      if (name.equals("dioritesource")) {
         return new PngToNbtConverter.SpecialPointInfo("source", "diorite", null, null);
      }

      if (name.equals("andesitesource")) {
         return new PngToNbtConverter.SpecialPointInfo("source", "andesite", null, null);
      }

      if (name.equals("snowsource")) {
         return new PngToNbtConverter.SpecialPointInfo("source", "snow", null, null);
      }

      if (name.equals("icesource")) {
         return new PngToNbtConverter.SpecialPointInfo("source", "ice", null, null);
      }

      if (name.equals("redsandstonesource")) {
         return new PngToNbtConverter.SpecialPointInfo("source", "red_sandstone", null, null);
      }

      if (name.equals("quartzsource")) {
         return new PngToNbtConverter.SpecialPointInfo("source", "quartz", null, null);
      }

      if (name.equals("freestone")) {
         return new PngToNbtConverter.SpecialPointInfo("freeBlock", "stone", null, null);
      }

      if (name.equals("freesand")) {
         return new PngToNbtConverter.SpecialPointInfo("freeBlock", "sand", null, null);
      }

      if (name.equals("freegravel")) {
         return new PngToNbtConverter.SpecialPointInfo("freeBlock", "gravel", null, null);
      }

      if (name.equals("freewool")) {
         return new PngToNbtConverter.SpecialPointInfo("freeBlock", "wool", null, null);
      }

      if (name.equals("freesandstone")) {
         return new PngToNbtConverter.SpecialPointInfo("freeBlock", "sandstone", null, null);
      }

      if (name.equals("freecobblestone")) {
         return new PngToNbtConverter.SpecialPointInfo("freeBlock", "cobblestone", null, null);
      }

      if (name.equals("freestonebrick")) {
         return new PngToNbtConverter.SpecialPointInfo("freeBlock", "stone_brick", null, null);
      }

      if (name.equals("freepaintedbrick")) {
         return new PngToNbtConverter.SpecialPointInfo("freeBlock", "painted_brick", null, null);
      }

      if (name.equals("freegrass_block")) {
         return new PngToNbtConverter.SpecialPointInfo("freeBlock", "grass_block", null, null);
      }

      if (name.startsWith("furnace")) {
         String orientation = this.extractLegacyOrientation(name, "furnace");
         return new PngToNbtConverter.SpecialPointInfo("furnace", null, orientation, null);
      }

      if (name.equals("stall")) {
         return new PngToNbtConverter.SpecialPointInfo("stall", null, null, null);
      }

      if (name.equals("fishingspot")) {
         return new PngToNbtConverter.SpecialPointInfo("fishingSpot", null, null, null);
      }

      if (name.equals("brickspot")) {
         return new PngToNbtConverter.SpecialPointInfo("brickSpot", null, null, null);
      }

      if (name.equals("cacaospot")) {
         return new PngToNbtConverter.SpecialPointInfo("cacaoSpot", null, null, null);
      }

      if (name.equals("silkwormblock")) {
         return new PngToNbtConverter.SpecialPointInfo("silkwormBlock", null, null, null);
      }

      if (name.equals("snailsoilblock")) {
         return new PngToNbtConverter.SpecialPointInfo("snailSoilBlock", null, null, null);
      }

      if (name.equals("healingspot")) {
         return new PngToNbtConverter.SpecialPointInfo("healingSpot", null, null, null);
      }

      if (name.equals("brewingstand")) {
         return new PngToNbtConverter.SpecialPointInfo("brewingStand", null, null, null);
      }

      if (name.equals("dispenserunknownpowder")) {
         return new PngToNbtConverter.SpecialPointInfo("dispenserUnknownPowder", null, null, null);
      }

      if (name.equals("plainsignguess")) {
         return new PngToNbtConverter.SpecialPointInfo("plainSign", null, null, null);
      }

      if (name.startsWith("signwall")) {
         String wallDir = this.extractLegacyOrientation(name, "signwall");
         String faceDir = "guess".equals(wallDir) ? "guess" : this.oppositeDirection(wallDir);
         return new PngToNbtConverter.SpecialPointInfo("signPos", null, faceDir, null);
      }

      if (name.startsWith("spawner")) {
         String mob = name.substring("spawner".length());
         if (mob.equals("cavespider")) {
            mob = "cave_spider";
         }

         return new PngToNbtConverter.SpecialPointInfo("spawner", mob, null, null);
      } else if (name.equals("tapestry")) {
         return new PngToNbtConverter.SpecialPointInfo("decorative", "tapestry", null, null);
      } else if (name.equals("indianstatue")) {
         return new PngToNbtConverter.SpecialPointInfo("decorative", "indian_statue", null, null);
      } else if (name.equals("mayanstatue")) {
         return new PngToNbtConverter.SpecialPointInfo("decorative", "mayan_statue", null, null);
      } else if (name.equals("byzantineiconsmall")) {
         return new PngToNbtConverter.SpecialPointInfo("decorative", "byzantine_icon_small", null, null);
      } else if (name.equals("byzantineiconmedium")) {
         return new PngToNbtConverter.SpecialPointInfo("decorative", "byzantine_icon_medium", null, null);
      } else if (name.equals("byzantineiconlarge")) {
         return new PngToNbtConverter.SpecialPointInfo("decorative", "byzantine_icon_large", null, null);
      } else if (name.equals("hidehanging")) {
         return new PngToNbtConverter.SpecialPointInfo("decorative", "hide_hanging", null, null);
      } else if (name.equals("wallcarpetsmall")) {
         return new PngToNbtConverter.SpecialPointInfo("decorative", "wall_carpet_small", null, null);
      } else if (name.equals("wallcarpetmedium")) {
         return new PngToNbtConverter.SpecialPointInfo("decorative", "wall_carpet_medium", null, null);
      } else if (name.equals("wallcarpetlarge")) {
         return new PngToNbtConverter.SpecialPointInfo("decorative", "wall_carpet_large", null, null);
      } else if (name.startsWith("villagebanner")) {
         String placement = this.extractBannerPlacement(name, "villagebanner");
         return new PngToNbtConverter.SpecialPointInfo("banner", "village", null, placement);
      } else if (name.startsWith("culturebanner")) {
         String placement = this.extractBannerPlacement(name, "culturebanner");
         return new PngToNbtConverter.SpecialPointInfo("banner", "culture", null, placement);
      } else {
         return null;
      }
   }

   private String extractLegacyOrientation(String name, String prefix) {
      if (name.contains("top")) {
         return "west";
      } else if (name.contains("bottom")) {
         return "east";
      } else if (name.contains("left")) {
         return "south";
      } else {
         return name.contains("right") ? "north" : "guess";
      }
   }

   private String oppositeDirection(String dir) {
      return switch (dir) {
         case "north" -> "south";
         case "south" -> "north";
         case "east" -> "west";
         case "west" -> "east";
         default -> dir;
      };
   }

   private String extractBannerPlacement(String name, String prefix) {
      String suffix = name.substring(prefix.length());
      if (suffix.equals("wallnorth")) {
         return "wall_north";
      } else if (suffix.equals("walleast")) {
         return "wall_east";
      } else if (suffix.equals("wallsouth")) {
         return "wall_south";
      } else if (suffix.equals("wallwest")) {
         return "wall_west";
      } else if (suffix.startsWith("standing")) {
         String num = suffix.substring("standing".length());
         return "standing_" + num;
      } else {
         return null;
      }
   }

   private List<PngToNbtConverter.BlocklistEntry> parseBlocklist(Path path) throws IOException {
      return this.parseBlocklistLines(Files.readAllLines(path));
   }

   private List<PngToNbtConverter.BlocklistEntry> parseBlocklist(InputStream stream) throws IOException {
      List<String> lines = new ArrayList<>();

      String line;
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
         while ((line = reader.readLine()) != null) {
            lines.add(line);
         }
      }

      return this.parseBlocklistLines(lines);
   }

   private List<PngToNbtConverter.BlocklistEntry> parseBlocklistLines(List<String> rawLines) {
      List<PngToNbtConverter.BlocklistEntry> entries = new ArrayList<>();

      for (String line : rawLines) {
         line = line.trim();
         if (!line.isEmpty() && !line.startsWith("//")) {
            String[] parts = line.split(";", -1);
            if (parts.length >= 5) {
               String name = parts[0].trim();
               String blockId = parts[1].trim();
               String variant = parts[2].trim();
               String setAfterStr = parts[3].trim();
               String colorStr = parts[4].trim();
               if (!colorStr.isEmpty()) {
                  String[] colorParts = colorStr.split("/");
                  if (colorParts.length == 3) {
                     try {
                        int r = Integer.parseInt(colorParts[0].trim());
                        int g = Integer.parseInt(colorParts[1].trim());
                        int b = Integer.parseInt(colorParts[2].trim());
                        int color = (r << 16) + (g << 8) + b;
                        boolean setAfter = "true".equalsIgnoreCase(setAfterStr) || setAfterStr.startsWith("true");
                        entries.add(new PngToNbtConverter.BlocklistEntry(name, blockId, variant, setAfter, color));
                     } catch (NumberFormatException var17) {
                     }
                  }
               }
            }
         }
      }

      return entries;
   }

   private PngToNbtConverter.BlockState mapSpecialEntry(PngToNbtConverter.BlocklistEntry entry) {
      String name = entry.name.toLowerCase();
      if (name.startsWith("lockedchest")) {
         Map<String, String> props = new HashMap<>();
         if (name.contains("top")) {
            props.put("facing", "west");
         } else if (name.contains("bottom")) {
            props.put("facing", "east");
         } else if (name.contains("left")) {
            props.put("facing", "south");
         } else if (name.contains("right")) {
            props.put("facing", "north");
         }

         return new PngToNbtConverter.BlockState("minecraft:chest", props);
      } else if (name.startsWith("mainchest")) {
         Map<String, String> props = new HashMap<>();
         if (name.contains("top")) {
            props.put("facing", "west");
         } else if (name.contains("bottom")) {
            props.put("facing", "east");
         } else if (name.contains("left")) {
            props.put("facing", "south");
         } else if (name.contains("right")) {
            props.put("facing", "north");
         }

         return new PngToNbtConverter.BlockState("minecraft:chest", props);
      } else if (name.equals("soil")
         || name.equals("potatosoil")
         || name.equals("carrotsoil")
         || name.equals("maizesoil")
         || name.equals("flowersoil")
         || name.equals("ricesoil")
         || name.equals("turmericsoil")
         || name.equals("sugarcanesoil")
         || name.equals("netherwartsoil")
         || name.equals("vinesoil")
         || name.equals("cottonsoil")) {
         return new PngToNbtConverter.BlockState("minecraft:farmland");
      } else if (name.equals("freestone")) {
         return new PngToNbtConverter.BlockState("minecraft:stone");
      } else if (name.equals("freesand")) {
         return new PngToNbtConverter.BlockState("minecraft:sand");
      } else if (name.equals("freegravel")) {
         return new PngToNbtConverter.BlockState("minecraft:gravel");
      } else if (name.equals("freewool")) {
         return new PngToNbtConverter.BlockState("minecraft:white_wool");
      } else if (name.equals("freesandstone")) {
         return new PngToNbtConverter.BlockState("minecraft:sandstone");
      } else if (name.equals("freecobblestone")) {
         return new PngToNbtConverter.BlockState("minecraft:cobblestone");
      } else if (name.equals("freestonebrick")) {
         return new PngToNbtConverter.BlockState("minecraft:stone_bricks");
      } else if (name.equals("freepaintedbrick")) {
         return new PngToNbtConverter.BlockState("minecraft:stone_bricks");
      } else {
         return name.equals("furnaceguess") ? new PngToNbtConverter.BlockState("minecraft:furnace") : null;
      }
   }

   private PngToNbtConverter.BlockState mapTo121(PngToNbtConverter.BlocklistEntry entry) {
      String id = entry.blockId;
      String variant = entry.variant;
      if (id.isEmpty() || id.equals("0")) {
         return null;
      } else if ("minecraft:air".equals(id)) {
         return new PngToNbtConverter.BlockState("minecraft:air");
      } else {
         return id.startsWith("millenaire:") ? this.mapMillenaireBlock(id, variant) : this.flattenVanillaBlock(id, variant);
      }
   }

   private PngToNbtConverter.BlockState mapMillenaireBlock(String id, String variant) {
      return switch (id) {
         case "millenaire:earth_deco" -> new PngToNbtConverter.BlockState("millenaire:dirt_wall");
         case "millenaire:wood_deco" -> !variant.contains("2") && !variant.contains("thatch")
            ? (
               !variant.contains("1") && !variant.contains("cross")
                  ? new PngToNbtConverter.BlockState("millenaire:timber_frame_plain")
                  : new PngToNbtConverter.BlockState("millenaire:timber_frame_cross")
            )
            : new PngToNbtConverter.BlockState("millenaire:thatch");
         case "millenaire:stone_deco" -> variant.equals("2")
            ? new PngToNbtConverter.BlockState("millenaire:mayan_gold_block")
            : (
               variant.contains("byzantine_mosaic_red")
                  ? new PngToNbtConverter.BlockState("millenaire:byzantine_mosaic_red")
                  : (
                     variant.contains("byzantine_mosaic_blue")
                        ? new PngToNbtConverter.BlockState("millenaire:byzantine_mosaic_blue")
                        : (
                           variant.contains("byzantine")
                              ? new PngToNbtConverter.BlockState("millenaire:byzantine_mosaic_red")
                              : new PngToNbtConverter.BlockState("millenaire:mud_brick")
                        )
                  )
            );
         case "millenaire:panel" -> {
            Map<String, String> props = this.parseFacingProps(variant);
            yield new PngToNbtConverter.BlockState("minecraft:oak_wall_sign", props);
         }
         case "millenaire:stairs_timberframe" -> {
            Map<String, String> props = this.parseFacingProps(variant);
            yield new PngToNbtConverter.BlockState("millenaire:timber_frame_stairs", props);
         }
         case "millenaire:stairs_thatch" -> {
            Map<String, String> props = this.parseFacingProps(variant);
            yield new PngToNbtConverter.BlockState("millenaire:thatch_stairs", props);
         }
         case "millenaire:stairs_mudbrick" -> {
            Map<String, String> props = this.parseFacingProps(variant);
            yield new PngToNbtConverter.BlockState("millenaire:mud_brick_stairs", props);
         }
         case "millenaire:slab_wood_deco" -> {
            Map<String, String> props = this.parseSlabProps(variant);
            yield variant.contains("thatch")
               ? new PngToNbtConverter.BlockState("millenaire:thatch_slab", props)
               : new PngToNbtConverter.BlockState("millenaire:timber_frame_slab", props);
         }
         case "millenaire:slab_stone_deco" -> {
            Map<String, String> props = this.parseSlabProps(variant);
            yield variant.contains("cookedbrick")
               ? new PngToNbtConverter.BlockState("millenaire:painted_brick_white_slab", props)
               : new PngToNbtConverter.BlockState("millenaire:mud_brick_slab", props);
         }
         case "millenaire:wall_mud_brick" -> new PngToNbtConverter.BlockState("millenaire:mud_brick_wall");
         case "millenaire:extended_mud_brick" -> variant.contains("mudbrick_seljuk_ornamented")
            ? new PngToNbtConverter.BlockState("millenaire:mud_brick_seljuk_ornamented")
            : (
               variant.contains("mudbrick_seljuk_decorated")
                  ? new PngToNbtConverter.BlockState("millenaire:mud_brick_seljuk_decorated")
                  : (
                     variant.contains("mudbrick_smooth")
                        ? new PngToNbtConverter.BlockState("millenaire:mud_brick_smooth")
                        : new PngToNbtConverter.BlockState("millenaire:mud_brick")
                  )
            );
         case "millenaire:wooden_bars" -> new PngToNbtConverter.BlockState("millenaire:wooden_bars");
         case "millenaire:wooden_bars_indian" -> new PngToNbtConverter.BlockState("millenaire:wooden_bars_indian");
         case "millenaire:wooden_bars_rosette" -> {
            Map<String, String> props = this.parseRosetteProps(variant);
            yield new PngToNbtConverter.BlockState("millenaire:wooden_bars_rosette", props);
         }
         case "millenaire:rosette" -> new PngToNbtConverter.BlockState("millenaire:rosette");
         case "millenaire:sandstone_carved" -> new PngToNbtConverter.BlockState("millenaire:sandstone_carved");
         case "millenaire:sandstone_red_carved" -> new PngToNbtConverter.BlockState("millenaire:red_sandstone_carved");
         case "millenaire:sandstone_ochre_carved" -> new PngToNbtConverter.BlockState("millenaire:ochre_sandstone_carved");
         case "millenaire:stairs_sandstone_carved" -> {
            Map<String, String> props = this.parseFacingProps(variant);
            yield new PngToNbtConverter.BlockState("millenaire:sandstone_carved_stairs", props);
         }
         case "millenaire:stairs_sandstone_red_carved" -> {
            Map<String, String> props = this.parseFacingProps(variant);
            yield new PngToNbtConverter.BlockState("millenaire:red_sandstone_carved_stairs", props);
         }
         case "millenaire:stairs_sandstone_ochre_carved" -> {
            Map<String, String> props = this.parseFacingProps(variant);
            yield new PngToNbtConverter.BlockState("millenaire:ochre_sandstone_carved_stairs", props);
         }
         case "millenaire:slab_sandstone_carved" -> {
            Map<String, String> props = this.parseSlabProps(variant);
            yield new PngToNbtConverter.BlockState("millenaire:sandstone_carved_slab", props);
         }
         case "millenaire:slab_sandstone_red_carved" -> {
            Map<String, String> props = this.parseSlabProps(variant);
            yield new PngToNbtConverter.BlockState("millenaire:red_sandstone_carved_slab", props);
         }
         case "millenaire:slab_sandstone_ochre_carved" -> {
            Map<String, String> props = this.parseSlabProps(variant);
            yield new PngToNbtConverter.BlockState("millenaire:ochre_sandstone_carved_slab", props);
         }
         case "millenaire:wall_sandstone_carved" -> new PngToNbtConverter.BlockState("millenaire:sandstone_carved_wall");
         case "millenaire:wall_sandstone_red_carved" -> new PngToNbtConverter.BlockState("millenaire:red_sandstone_carved_wall");
         case "millenaire:wall_sandstone_ochre_carved" -> new PngToNbtConverter.BlockState("millenaire:ochre_sandstone_carved_wall");
         case "millenaire:bed_charpoy" -> {
            Map<String, String> props = this.parseFacingProps(variant);
            props.put("part", "head");
            yield new PngToNbtConverter.BlockState("millenaire:charpoy", props);
         }
         case "millenaire:paper_wall" -> new PngToNbtConverter.BlockState("millenaire:paper_wall");
         case "millenaire:japanese_tiles" -> {
            Map<String, String> props = new HashMap<>();
            if (variant.contains("axis=z")) {
               props.put("axis", "z");
            } else if (variant.contains("axis=x")) {
               props.put("axis", "x");
            }

            yield new PngToNbtConverter.BlockState("millenaire:japanese_tiles", props.isEmpty() ? null : props);
         }
         case "millenaire:japanese_stone_tiles" -> {
            Map<String, String> props = new HashMap<>();
            if (variant.contains("axis=z")) {
               props.put("axis", "z");
            } else if (variant.contains("axis=x")) {
               props.put("axis", "x");
            }

            yield new PngToNbtConverter.BlockState("millenaire:japanese_stone_tiles", props.isEmpty() ? null : props);
         }
         case "millenaire:japanese_tiles_stairs" -> {
            Map<String, String> props = this.parseFacingProps(variant);
            yield new PngToNbtConverter.BlockState("millenaire:japanese_tiles_stairs", props);
         }
         case "millenaire:japanese_tiles_slab" -> {
            Map<String, String> props = this.parseSlabProps(variant);
            yield new PngToNbtConverter.BlockState("millenaire:japanese_tiles_slab", props);
         }
         case "millenaire:bed_futon" -> {
            Map<String, String> props = this.parseFacingProps(variant);
            props.put("part", "head");
            yield new PngToNbtConverter.BlockState("millenaire:futon", props);
         }
         case "millenaire:wooden_sliding_door" -> {
            Map<String, String> props = this.parseFacingProps(variant);
            yield new PngToNbtConverter.BlockState("millenaire:wooden_sliding_door", props);
         }
         case "millenaire:japanese_sliding_door" -> {
            Map<String, String> props = this.parseFacingProps(variant);
            yield new PngToNbtConverter.BlockState("millenaire:japanese_sliding_door", props);
         }
         case "millenaire:pathdirt" -> new PngToNbtConverter.BlockState("millenaire:path_dirt");
         case "millenaire:pathgravel" -> new PngToNbtConverter.BlockState("millenaire:path_gravel");
         case "millenaire:pathslabs" -> new PngToNbtConverter.BlockState("millenaire:path_slabs");
         case "millenaire:pathsandstone" -> new PngToNbtConverter.BlockState("millenaire:path_sandstone");
         case "millenaire:pathochretiles" -> new PngToNbtConverter.BlockState("millenaire:path_ochre_tiles");
         case "millenaire:pathgravelslabs" -> new PngToNbtConverter.BlockState("millenaire:path_gravel");
         case "millenaire:pathsnow" -> new PngToNbtConverter.BlockState("millenaire:path_snow");
         case "millenaire:pathdirt_slab" -> new PngToNbtConverter.BlockState("millenaire:path_dirt_slab", Map.of("type", "bottom"));
         case "millenaire:pathgravel_slab" -> new PngToNbtConverter.BlockState("millenaire:path_gravel_slab", Map.of("type", "bottom"));
         case "millenaire:pathslabs_slab" -> new PngToNbtConverter.BlockState("millenaire:path_slabs_slab", Map.of("type", "bottom"));
         case "millenaire:pathsandstone_slab" -> new PngToNbtConverter.BlockState("millenaire:path_sandstone");
         case "millenaire:pathochretiles_slab" -> new PngToNbtConverter.BlockState("millenaire:path_ochre_tiles");
         case "millenaire:pathgravelslabs_slab" -> new PngToNbtConverter.BlockState("millenaire:path_gravel");
         case "millenaire:pathsnow_slab" -> new PngToNbtConverter.BlockState("millenaire:path_snow_slab", Map.of("type", "bottom"));
         case "millenaire:stained_glass" -> {
            String color = this.extractVariant(variant, "variant");
            yield new PngToNbtConverter.BlockState("millenaire:stained_glass_" + (color != null ? color : "white"));
         }
         case "millenaire:bed_straw" -> {
            Map<String, String> props = this.parseFacingProps(variant);
            props.put("part", "head");
            yield new PngToNbtConverter.BlockState("millenaire:straw_bed", props);
         }
         case "millenaire:byzantine_tiles" -> {
            Map<String, String> props = new HashMap<>();
            if (variant.contains("axis=z")) {
               props.put("axis", "z");
            } else if (variant.contains("axis=x")) {
               props.put("axis", "x");
            }

            yield new PngToNbtConverter.BlockState("millenaire:byzantine_tiles", props.isEmpty() ? null : props);
         }
         case "millenaire:byzantine_stone_tiles" -> {
            Map<String, String> props = new HashMap<>();
            if (variant.contains("axis=z")) {
               props.put("axis", "z");
            } else if (variant.contains("axis=x")) {
               props.put("axis", "x");
            }

            yield new PngToNbtConverter.BlockState("millenaire:byzantine_stone_tiles", props.isEmpty() ? null : props);
         }
         case "millenaire:byzantine_sandstone_tiles" -> {
            Map<String, String> props = new HashMap<>();
            if (variant.contains("axis=z")) {
               props.put("axis", "z");
            } else if (variant.contains("axis=x")) {
               props.put("axis", "x");
            }

            yield new PngToNbtConverter.BlockState("millenaire:byzantine_sandstone_tiles", props.isEmpty() ? null : props);
         }
         case "millenaire:byzantine_sandstone_ornament" -> new PngToNbtConverter.BlockState("millenaire:byzantine_sandstone_ornament");
         case "millenaire:byzantine_stone_ornament" -> new PngToNbtConverter.BlockState("millenaire:byzantine_stone_ornament");
         case "millenaire:stairs_byzantine_tiles" -> {
            Map<String, String> props = this.parseFacingProps(variant);
            yield new PngToNbtConverter.BlockState("millenaire:byzantine_tiles_stairs", props);
         }
         case "millenaire:byzantine_tiles_slab" -> {
            Map<String, String> props = this.parseSlabProps(variant);
            yield new PngToNbtConverter.BlockState("millenaire:byzantine_tiles_slab", props);
         }
         case "millenaire:leaves_olivetree" -> new PngToNbtConverter.BlockState("millenaire:olive_tree_leaves");
         case "millenaire:crop_rice" -> new PngToNbtConverter.BlockState("millenaire:crop_rice");
         case "millenaire:crop_turmeric" -> new PngToNbtConverter.BlockState("millenaire:crop_turmeric");
         case "millenaire:crop_cotton" -> new PngToNbtConverter.BlockState("millenaire:crop_cotton");
         case "millenaire:crop_maize" -> new PngToNbtConverter.BlockState("millenaire:crop_maize");
         case "millenaire:sod" -> {
            String sodVariant = this.extractVariant(variant, "variant");
            yield new PngToNbtConverter.BlockState("millenaire:sod_" + (sodVariant != null ? sodVariant : "spruce"));
         }
         case "millenaire:snowbrick" -> new PngToNbtConverter.BlockState("millenaire:snow_brick");
         case "millenaire:icebrick" -> new PngToNbtConverter.BlockState("millenaire:ice_brick");
         case "millenaire:snowwall" -> new PngToNbtConverter.BlockState("millenaire:snow_wall");
         case "millenaire:inuitcarving" -> new PngToNbtConverter.BlockState("millenaire:inuit_carving");
         case "millenaire:fire_pit" -> {
            String alignment = this.extractVariant(variant, "alignment");
            yield new PngToNbtConverter.BlockState("millenaire:fire_pit", Map.of("alignment", alignment != null ? alignment : "x", "lit", "false"));
         }
         default -> {
            if (id.startsWith("millenaire:painted_brick_decorated_")) {
               String color = this.remapLegacyColor(id.substring("millenaire:painted_brick_decorated_".length()));
               yield new PngToNbtConverter.BlockState("millenaire:decorated_brick_" + color);
            } else if (id.startsWith("millenaire:painted_brick_")) {
               String color = this.remapLegacyColor(id.substring("millenaire:painted_brick_".length()));
               yield new PngToNbtConverter.BlockState("millenaire:painted_brick_" + color);
            } else if (id.startsWith("millenaire:stairs_painted_brick_")) {
               String color = this.remapLegacyColor(id.substring("millenaire:stairs_painted_brick_".length()));
               Map<String, String> props = this.parseFacingProps(variant);
               yield new PngToNbtConverter.BlockState("millenaire:painted_brick_" + color + "_stairs", props);
            } else if (id.startsWith("millenaire:slab_painted_brick_")) {
               String color = this.remapLegacyColor(id.substring("millenaire:slab_painted_brick_".length()));
               Map<String, String> props = this.parseSlabProps(variant);
               yield new PngToNbtConverter.BlockState("millenaire:painted_brick_" + color + "_slab", props);
            } else if (id.startsWith("millenaire:wall_painted_brick_")) {
               String color = this.remapLegacyColor(id.substring("millenaire:wall_painted_brick_".length()));
               yield new PngToNbtConverter.BlockState("millenaire:painted_brick_" + color + "_wall");
            } else {
               System.out.println("  WARNING: unknown modded block → cobblestone: " + id);
               yield new PngToNbtConverter.BlockState("minecraft:cobblestone");
            }
         }
      };
   }

   private String remapLegacyColor(String color) {
      return "silver".equals(color) ? "light_gray" : color;
   }

   PngToNbtConverter.BlockState flattenVanillaBlock(String id, String variant) {
      if ("minecraft:planks".equals(id)) {
         String woodType = this.extractVariant(variant, "variant");
         return new PngToNbtConverter.BlockState("minecraft:" + (woodType != null ? woodType : "oak") + "_planks");
      }

      if ("minecraft:stone".equals(id)) {
         String v = this.extractVariant(variant, "variant");
         if (v == null) {
            v = "stone";
         }
         return switch (v) {
            case "stone" -> new PngToNbtConverter.BlockState("minecraft:stone");
            case "granite" -> new PngToNbtConverter.BlockState("minecraft:granite");
            case "smooth_granite" -> new PngToNbtConverter.BlockState("minecraft:polished_granite");
            case "diorite" -> new PngToNbtConverter.BlockState("minecraft:diorite");
            case "smooth_diorite" -> new PngToNbtConverter.BlockState("minecraft:polished_diorite");
            case "andesite" -> new PngToNbtConverter.BlockState("minecraft:andesite");
            case "smooth_andesite" -> new PngToNbtConverter.BlockState("minecraft:polished_andesite");
            default -> new PngToNbtConverter.BlockState("minecraft:stone");
         };
      } else if ("minecraft:dirt".equals(id)) {
         String v = this.extractVariant(variant, "variant");
         if ("coarse_dirt".equals(v)) {
            return new PngToNbtConverter.BlockState("minecraft:coarse_dirt");
         } else {
            return "podzol".equals(v) ? new PngToNbtConverter.BlockState("minecraft:podzol") : new PngToNbtConverter.BlockState("minecraft:dirt");
         }
      } else {
         if ("minecraft:cobblestone".equals(id)) {
            return new PngToNbtConverter.BlockState("minecraft:cobblestone");
         }

         if ("minecraft:mossy_cobblestone".equals(id)) {
            return new PngToNbtConverter.BlockState("minecraft:mossy_cobblestone");
         }

         if ("minecraft:stonebrick".equals(id)) {
            return switch (variant) {
               case "0" -> new PngToNbtConverter.BlockState("minecraft:stone_bricks");
               case "1" -> new PngToNbtConverter.BlockState("minecraft:mossy_stone_bricks");
               case "2" -> new PngToNbtConverter.BlockState("minecraft:cracked_stone_bricks");
               case "3" -> new PngToNbtConverter.BlockState("minecraft:chiseled_stone_bricks");
               default -> new PngToNbtConverter.BlockState("minecraft:stone_bricks");
            };
         } else if ("minecraft:log".equals(id) || "minecraft:log2".equals(id)) {
            String woodVariant = this.extractVariant(variant, "variant");
            String axis = this.extractVariant(variant, "axis");
            if (woodVariant == null) {
               woodVariant = "oak";
            }

            Map<String, String> props = new HashMap<>();
            if (axis != null) {
               props.put("axis", axis);
            }

            return new PngToNbtConverter.BlockState("minecraft:" + woodVariant + "_log", props);
         } else {
            if (id.endsWith("_stairs")) {
               Map<String, String> props = this.parseFacingProps(variant);
               String remapped = "minecraft:stone_stairs".equals(id) ? "minecraft:cobblestone_stairs" : id;
               return new PngToNbtConverter.BlockState(remapped, props);
            }

            if ("minecraft:wooden_slab".equals(id)) {
               String[] woods = new String[]{"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
               int meta = this.parseIntSafe(variant);
               int idx = meta % 8;
               boolean top = meta >= 8;
               if (idx >= 0 && idx < woods.length) {
                  Map<String, String> props = new HashMap<>();
                  props.put("type", top ? "top" : "bottom");
                  return new PngToNbtConverter.BlockState("minecraft:" + woods[idx] + "_slab", props);
               } else {
                  return new PngToNbtConverter.BlockState("minecraft:oak_slab");
               }
            } else {
               if ("minecraft:stone_slab".equals(id) || "minecraft:stone_slab2".equals(id)) {
                  return this.mapStoneSlab(id, variant);
               }

               if ("minecraft:double_stone_slab".equals(id)) {
                  return new PngToNbtConverter.BlockState("minecraft:smooth_stone");
               }

               if ("minecraft:torch".equals(id)) {
                  String facing = this.extractVariant(variant, "facing");
                  if (facing != null && !"up".equals(facing)) {
                     Map<String, String> props = new HashMap<>();
                     props.put("facing", facing);
                     return new PngToNbtConverter.BlockState("minecraft:wall_torch", props);
                  } else {
                     return new PngToNbtConverter.BlockState("minecraft:torch");
                  }
               } else {
                  if ("minecraft:furnace".equals(id)) {
                     Map<String, String> props = this.parseFacingProps(variant);
                     return new PngToNbtConverter.BlockState("minecraft:furnace", props);
                  }

                  if ("minecraft:glass".equals(id)) {
                     return new PngToNbtConverter.BlockState("minecraft:glass");
                  }

                  if ("minecraft:glass_pane".equals(id)) {
                     return new PngToNbtConverter.BlockState("minecraft:glass_pane");
                  }

                  if ("minecraft:water".equals(id) || "minecraft:flowing_water".equals(id)) {
                     return new PngToNbtConverter.BlockState("minecraft:water");
                  }

                  if ("minecraft:ladder".equals(id)) {
                     Map<String, String> props = this.parseFacingProps(variant);
                     return new PngToNbtConverter.BlockState("minecraft:ladder", props);
                  }

                  if (id.contains("_door") || "minecraft:wooden_door".equals(id)) {
                     return this.mapDoor(id, variant);
                  }

                  if ("minecraft:trapdoor".equals(id)) {
                     Map<String, String> props = this.parseFacingProps(variant);
                     return new PngToNbtConverter.BlockState("minecraft:oak_trapdoor", props);
                  }

                  if ("minecraft:fence".equals(id)) {
                     return new PngToNbtConverter.BlockState("minecraft:oak_fence");
                  }

                  if ("minecraft:spruce_fence".equals(id)) {
                     return new PngToNbtConverter.BlockState("minecraft:spruce_fence");
                  }

                  if ("minecraft:birch_fence".equals(id)) {
                     return new PngToNbtConverter.BlockState("minecraft:birch_fence");
                  }

                  if ("minecraft:acacia_fence".equals(id)) {
                     return new PngToNbtConverter.BlockState("minecraft:acacia_fence");
                  }

                  if ("minecraft:jungle_fence".equals(id)) {
                     return new PngToNbtConverter.BlockState("minecraft:jungle_fence");
                  }

                  if ("minecraft:dark_oak_fence".equals(id)) {
                     return new PngToNbtConverter.BlockState("minecraft:dark_oak_fence");
                  }

                  if (id.contains("fence_gate")) {
                     Map<String, String> props = this.parseFacingProps(variant);
                     if (id.contains("spruce")) {
                        return new PngToNbtConverter.BlockState("minecraft:spruce_fence_gate", props);
                     } else if (id.contains("birch")) {
                        return new PngToNbtConverter.BlockState("minecraft:birch_fence_gate", props);
                     } else if (id.contains("acacia")) {
                        return new PngToNbtConverter.BlockState("minecraft:acacia_fence_gate", props);
                     } else if (id.contains("jungle")) {
                        return new PngToNbtConverter.BlockState("minecraft:jungle_fence_gate", props);
                     } else {
                        return id.contains("dark_oak")
                           ? new PngToNbtConverter.BlockState("minecraft:dark_oak_fence_gate", props)
                           : new PngToNbtConverter.BlockState("minecraft:oak_fence_gate", props);
                     }
                  } else {
                     if ("minecraft:chest".equals(id)) {
                        Map<String, String> props = this.parseFacingProps(variant);
                        return new PngToNbtConverter.BlockState("minecraft:chest", props);
                     }

                     if ("minecraft:crafting_table".equals(id)) {
                        return new PngToNbtConverter.BlockState("minecraft:crafting_table");
                     }

                     if ("minecraft:wool".equals(id)) {
                        return this.mapColoredBlock(variant, "_wool", "minecraft:white_wool");
                     }

                     if ("minecraft:carpet".equals(id)) {
                        return this.mapCarpet(variant);
                     }

                     if ("minecraft:hardened_clay".equals(id)) {
                        return new PngToNbtConverter.BlockState("minecraft:terracotta");
                     }

                     if ("minecraft:stained_hardened_clay".equals(id)) {
                        return this.mapColoredTerracotta(variant);
                     }

                     if ("minecraft:leaves".equals(id) || "minecraft:leaves2".equals(id)) {
                        String v = this.extractVariant(variant, "variant");
                        if (v == null) {
                           v = "oak";
                        }

                        Map<String, String> props = new HashMap<>();
                        props.put("persistent", "true");
                        return new PngToNbtConverter.BlockState("minecraft:" + v + "_leaves", props);
                     } else {
                        if ("minecraft:brick_block".equals(id)) {
                           return new PngToNbtConverter.BlockState("minecraft:bricks");
                        }

                        if ("minecraft:sand".equals(id)) {
                           return new PngToNbtConverter.BlockState("minecraft:sand");
                        }

                        if ("minecraft:gravel".equals(id)) {
                           return new PngToNbtConverter.BlockState("minecraft:gravel");
                        }

                        if ("minecraft:sandstone".equals(id)) {
                           return switch (variant) {
                              case "1" -> new PngToNbtConverter.BlockState("minecraft:chiseled_sandstone");
                              case "2" -> new PngToNbtConverter.BlockState("minecraft:smooth_sandstone");
                              default -> new PngToNbtConverter.BlockState("minecraft:sandstone");
                           };
                        } else if ("minecraft:red_sandstone".equals(id)) {
                           return switch (variant) {
                              case "1" -> new PngToNbtConverter.BlockState("minecraft:chiseled_red_sandstone");
                              case "2" -> new PngToNbtConverter.BlockState("minecraft:smooth_red_sandstone");
                              default -> new PngToNbtConverter.BlockState("minecraft:red_sandstone");
                           };
                        } else {
                           if ("minecraft:purpur_slab".equals(id)) {
                              int meta = this.parseIntSafe(variant);
                              Map<String, String> props = new HashMap<>();
                              props.put("type", meta >= 8 ? "top" : "bottom");
                              return new PngToNbtConverter.BlockState("minecraft:purpur_slab", props);
                           }

                           if ("minecraft:sponge".equals(id)) {
                              return "1".equals(variant)
                                 ? new PngToNbtConverter.BlockState("minecraft:wet_sponge")
                                 : new PngToNbtConverter.BlockState("minecraft:sponge");
                           }

                           if ("minecraft:hay_block".equals(id)) {
                              String axis = this.extractVariant(variant, "axis");
                              Map<String, String> props = new HashMap<>();
                              if (axis != null) {
                                 props.put("axis", axis);
                              }

                              return new PngToNbtConverter.BlockState("minecraft:hay_block", props);
                           } else {
                              if ("minecraft:bookshelf".equals(id)) {
                                 return new PngToNbtConverter.BlockState("minecraft:bookshelf");
                              }

                              if ("minecraft:bed".equals(id)) {
                                 Map<String, String> props = this.parseFacingProps(variant);
                                 String part = this.extractVariant(variant, "part");
                                 if (part != null) {
                                    props.put("part", part);
                                 }

                                 return new PngToNbtConverter.BlockState("minecraft:white_bed", props);
                              } else {
                                 if ("minecraft:iron_bars".equals(id)) {
                                    return new PngToNbtConverter.BlockState("minecraft:iron_bars");
                                 }

                                 if ("minecraft:standing_sign".equals(id)) {
                                    String rotation = this.extractVariant(variant, "rotation");
                                    Map<String, String> props = new HashMap<>();
                                    if (rotation != null) {
                                       props.put("rotation", rotation);
                                    }

                                    return new PngToNbtConverter.BlockState("minecraft:oak_sign", props);
                                 } else {
                                    if ("minecraft:redstone_wire".equals(id)) {
                                       return new PngToNbtConverter.BlockState("minecraft:redstone_wire");
                                    }

                                    if ("minecraft:redstone_torch".equals(id)) {
                                       return new PngToNbtConverter.BlockState("minecraft:redstone_torch");
                                    }

                                    if ("minecraft:stone_pressure_plate".equals(id)) {
                                       return new PngToNbtConverter.BlockState("minecraft:stone_pressure_plate");
                                    }

                                    if ("minecraft:wooden_pressure_plate".equals(id)) {
                                       return new PngToNbtConverter.BlockState("minecraft:oak_pressure_plate");
                                    }

                                    if ("minecraft:stone_button".equals(id)) {
                                       return new PngToNbtConverter.BlockState("minecraft:stone_button");
                                    }

                                    if ("minecraft:wooden_button".equals(id)) {
                                       Map<String, String> props = this.parseFacingProps(variant);
                                       return new PngToNbtConverter.BlockState("minecraft:oak_button", props);
                                    }

                                    if ("minecraft:wall_sign".equals(id)) {
                                       Map<String, String> props = this.parseFacingProps(variant);
                                       return new PngToNbtConverter.BlockState("minecraft:oak_wall_sign", props);
                                    }

                                    if ("minecraft:brewing_stand".equals(id)) {
                                       return new PngToNbtConverter.BlockState("minecraft:brewing_stand");
                                    }

                                    if ("minecraft:grass_path".equals(id)) {
                                       return new PngToNbtConverter.BlockState("minecraft:dirt_path");
                                    }

                                    if ("minecraft:grass".equals(id)) {
                                       return new PngToNbtConverter.BlockState("minecraft:grass_block");
                                    }

                                    if ("minecraft:tallgrass".equals(id)) {
                                       return new PngToNbtConverter.BlockState("minecraft:short_grass");
                                    }

                                    if ("minecraft:vine".equals(id)) {
                                       return new PngToNbtConverter.BlockState("minecraft:vine");
                                    }

                                    if ("minecraft:snow_layer".equals(id)) {
                                       return new PngToNbtConverter.BlockState("minecraft:snow");
                                    }

                                    if ("minecraft:snow".equals(id)) {
                                       return new PngToNbtConverter.BlockState("minecraft:snow_block");
                                    }

                                    if ("minecraft:ice".equals(id)) {
                                       return new PngToNbtConverter.BlockState("minecraft:ice");
                                    }

                                    if ("minecraft:pumpkin".equals(id)) {
                                       return new PngToNbtConverter.BlockState("minecraft:pumpkin");
                                    }

                                    if ("minecraft:lit_pumpkin".equals(id)) {
                                       return new PngToNbtConverter.BlockState("minecraft:jack_o_lantern");
                                    }

                                    if ("minecraft:melon_block".equals(id)) {
                                       return new PngToNbtConverter.BlockState("minecraft:melon");
                                    }

                                    if ("minecraft:nether_brick".equals(id)) {
                                       return new PngToNbtConverter.BlockState("minecraft:nether_bricks");
                                    }

                                    if ("minecraft:nether_brick_fence".equals(id)) {
                                       return new PngToNbtConverter.BlockState("minecraft:nether_brick_fence");
                                    }

                                    if ("minecraft:quartz_block".equals(id)) {
                                       String v = this.extractVariant(variant, "variant");
                                       if ("chiseled".equals(v)) {
                                          return new PngToNbtConverter.BlockState("minecraft:chiseled_quartz_block");
                                       } else if (v != null && v.startsWith("lines_")) {
                                          Map<String, String> props = new HashMap<>();
                                          props.put("axis", v.substring(6));
                                          return new PngToNbtConverter.BlockState("minecraft:quartz_pillar", props);
                                       } else {
                                          return new PngToNbtConverter.BlockState("minecraft:quartz_block");
                                       }
                                    } else if ("minecraft:bone_block".equals(id)) {
                                       String axis = this.extractVariant(variant, "axis");
                                       Map<String, String> props = new HashMap<>();
                                       if (axis != null) {
                                          props.put("axis", axis);
                                       }

                                       return new PngToNbtConverter.BlockState("minecraft:bone_block", props);
                                    } else {
                                       if ("minecraft:clay".equals(id)) {
                                          return new PngToNbtConverter.BlockState("minecraft:clay");
                                       }

                                       if ("minecraft:obsidian".equals(id)) {
                                          return new PngToNbtConverter.BlockState("minecraft:obsidian");
                                       }

                                       if ("minecraft:tnt".equals(id)) {
                                          return new PngToNbtConverter.BlockState("minecraft:tnt");
                                       }

                                       if ("minecraft:netherrack".equals(id)) {
                                          return new PngToNbtConverter.BlockState("minecraft:netherrack");
                                       }

                                       if ("minecraft:soul_sand".equals(id)) {
                                          return new PngToNbtConverter.BlockState("minecraft:soul_sand");
                                       }

                                       if ("minecraft:glowstone".equals(id)) {
                                          return new PngToNbtConverter.BlockState("minecraft:glowstone");
                                       }

                                       if ("minecraft:lapis_block".equals(id)) {
                                          return new PngToNbtConverter.BlockState("minecraft:lapis_block");
                                       }

                                       if ("minecraft:iron_block".equals(id)) {
                                          return new PngToNbtConverter.BlockState("minecraft:iron_block");
                                       }

                                       if ("minecraft:diamond_block".equals(id)) {
                                          return new PngToNbtConverter.BlockState("minecraft:diamond_block");
                                       }

                                       if ("minecraft:emerald_block".equals(id)) {
                                          return new PngToNbtConverter.BlockState("minecraft:emerald_block");
                                       }

                                       if ("minecraft:gold_block".equals(id)) {
                                          return new PngToNbtConverter.BlockState("minecraft:gold_block");
                                       }

                                       if ("minecraft:redstone_block".equals(id)) {
                                          return new PngToNbtConverter.BlockState("minecraft:redstone_block");
                                       }

                                       if ("minecraft:redstone_lamp".equals(id)) {
                                          return new PngToNbtConverter.BlockState("minecraft:redstone_lamp");
                                       }

                                       if ("minecraft:lit_redstone_lamp".equals(id)) {
                                          return new PngToNbtConverter.BlockState("minecraft:redstone_lamp");
                                       }

                                       if ("minecraft:jukebox".equals(id)) {
                                          return new PngToNbtConverter.BlockState("minecraft:jukebox");
                                       }

                                       if ("minecraft:noteblock".equals(id)) {
                                          return new PngToNbtConverter.BlockState("minecraft:note_block");
                                       }

                                       if ("minecraft:bedrock".equals(id)) {
                                          return new PngToNbtConverter.BlockState("minecraft:bedrock");
                                       }

                                       if ("minecraft:rail".equals(id)) {
                                          return new PngToNbtConverter.BlockState("minecraft:rail");
                                       }

                                       if ("minecraft:sea_lantern".equals(id)) {
                                          return new PngToNbtConverter.BlockState("minecraft:sea_lantern");
                                       }

                                       if ("minecraft:cobblestone_wall".equals(id)) {
                                          return new PngToNbtConverter.BlockState("minecraft:cobblestone_wall");
                                       }

                                       if ("minecraft:cauldron".equals(id)) {
                                          return new PngToNbtConverter.BlockState("minecraft:cauldron");
                                       }

                                       if ("minecraft:anvil".equals(id)) {
                                          Map<String, String> props = this.parseFacingProps(variant);
                                          return new PngToNbtConverter.BlockState("minecraft:anvil", props);
                                       }

                                       if ("minecraft:sapling".equals(id)) {
                                          String type = this.extractVariant(variant, "type");
                                          if (type == null) {
                                             type = "oak";
                                          }

                                          return new PngToNbtConverter.BlockState("minecraft:" + type + "_sapling");
                                       } else {
                                          if ("minecraft:yellow_flower".equals(id)) {
                                             return new PngToNbtConverter.BlockState("minecraft:dandelion");
                                          }

                                          if ("minecraft:red_flower".equals(id)) {
                                             int meta = this.parseIntSafe(variant);

                                             return switch (meta) {
                                                case 0 -> new PngToNbtConverter.BlockState("minecraft:poppy");
                                                case 1 -> new PngToNbtConverter.BlockState("minecraft:blue_orchid");
                                                case 2 -> new PngToNbtConverter.BlockState("minecraft:allium");
                                                case 3 -> new PngToNbtConverter.BlockState("minecraft:azure_bluet");
                                                case 4 -> new PngToNbtConverter.BlockState("minecraft:red_tulip");
                                                case 5 -> new PngToNbtConverter.BlockState("minecraft:orange_tulip");
                                                case 6 -> new PngToNbtConverter.BlockState("minecraft:white_tulip");
                                                case 7 -> new PngToNbtConverter.BlockState("minecraft:pink_tulip");
                                                case 8 -> new PngToNbtConverter.BlockState("minecraft:oxeye_daisy");
                                                default -> new PngToNbtConverter.BlockState("minecraft:poppy");
                                             };
                                          } else {
                                             if ("minecraft:flower_pot".equals(id)) {
                                                return new PngToNbtConverter.BlockState("minecraft:flower_pot");
                                             }

                                             if ("minecraft:farmland".equals(id)) {
                                                return new PngToNbtConverter.BlockState("minecraft:farmland");
                                             }

                                             if ("minecraft:wheat".equals(id)) {
                                                return new PngToNbtConverter.BlockState("minecraft:wheat");
                                             }

                                             if ("minecraft:nether_wart".equals(id)) {
                                                return new PngToNbtConverter.BlockState("minecraft:nether_wart");
                                             }

                                             if ("minecraft:waterlily".equals(id)) {
                                                return new PngToNbtConverter.BlockState("minecraft:lily_pad");
                                             }

                                             if ("minecraft:brown_mushroom_block".equals(id)) {
                                                return new PngToNbtConverter.BlockState("minecraft:brown_mushroom_block");
                                             }

                                             if ("minecraft:red_mushroom_block".equals(id)) {
                                                return new PngToNbtConverter.BlockState("minecraft:red_mushroom_block");
                                             }

                                             if ("minecraft:brown_mushroom".equals(id)) {
                                                return new PngToNbtConverter.BlockState("minecraft:brown_mushroom");
                                             }

                                             if ("minecraft:red_mushroom".equals(id)) {
                                                return new PngToNbtConverter.BlockState("minecraft:red_mushroom");
                                             }

                                             if ("minecraft:stained_glass_pane".equals(id)) {
                                                String[] colors = new String[]{
                                                   "white",
                                                   "orange",
                                                   "magenta",
                                                   "light_blue",
                                                   "yellow",
                                                   "lime",
                                                   "pink",
                                                   "gray",
                                                   "light_gray",
                                                   "cyan",
                                                   "purple",
                                                   "blue",
                                                   "brown",
                                                   "green",
                                                   "red",
                                                   "black"
                                                };
                                                int meta = this.parseIntSafe(variant);
                                                return meta >= 0 && meta < colors.length
                                                   ? new PngToNbtConverter.BlockState("minecraft:" + colors[meta] + "_stained_glass_pane")
                                                   : new PngToNbtConverter.BlockState("minecraft:glass_pane");
                                             }

                                             if ("minecraft:concrete".equals(id)) {
                                                String[] colors = new String[]{
                                                   "white",
                                                   "orange",
                                                   "magenta",
                                                   "light_blue",
                                                   "yellow",
                                                   "lime",
                                                   "pink",
                                                   "gray",
                                                   "light_gray",
                                                   "cyan",
                                                   "purple",
                                                   "blue",
                                                   "brown",
                                                   "green",
                                                   "red",
                                                   "black"
                                                };
                                                int meta = this.parseIntSafe(variant);
                                                return meta >= 0 && meta < colors.length
                                                   ? new PngToNbtConverter.BlockState("minecraft:" + colors[meta] + "_concrete")
                                                   : new PngToNbtConverter.BlockState("minecraft:white_concrete");
                                             }

                                             if (id.contains("glazed_terracotta")) {
                                                Map<String, String> props = this.parseFacingProps(variant);
                                                return new PngToNbtConverter.BlockState(id, props);
                                             }

                                             if ("minecraft:purpur_block".equals(id)) {
                                                return new PngToNbtConverter.BlockState("minecraft:purpur_block");
                                             }

                                             if ("minecraft:purpur_pillar".equals(id)) {
                                                String axis = this.extractVariant(variant, "axis");
                                                Map<String, String> props = new HashMap<>();
                                                if (axis != null) {
                                                   props.put("axis", axis);
                                                }

                                                return new PngToNbtConverter.BlockState("minecraft:purpur_pillar", props);
                                             } else {
                                                if ("minecraft:end_rod".equals(id)) {
                                                   Map<String, String> props = this.parseFacingProps(variant);
                                                   return new PngToNbtConverter.BlockState("minecraft:end_rod", props);
                                                }

                                                if ("minecraft:dispenser".equals(id)) {
                                                   return new PngToNbtConverter.BlockState("minecraft:dispenser");
                                                }

                                                if ("minecraft:cake".equals(id)) {
                                                   return new PngToNbtConverter.BlockState("minecraft:cake");
                                                }

                                                if ("minecraft:flowing_lava".equals(id) || "minecraft:lava".equals(id)) {
                                                   return new PngToNbtConverter.BlockState("minecraft:lava");
                                                }

                                                if ("minecraft:portal".equals(id)) {
                                                   return new PngToNbtConverter.BlockState("minecraft:nether_portal");
                                                }

                                                if ("minecraft:dead_bush".equals(id)) {
                                                   return new PngToNbtConverter.BlockState("minecraft:dead_bush");
                                                }

                                                if ("minecraft:double_plant".equals(id)) {
                                                   String plantVariant = this.extractVariant(variant, "variant");
                                                   String half = this.extractVariant(variant, "half");

                                                   String blockName = switch (plantVariant != null ? plantVariant : "") {
                                                      case "double_rose" -> "minecraft:rose_bush";
                                                      case "double_grass" -> "minecraft:tall_grass";
                                                      case "double_fern" -> "minecraft:large_fern";
                                                      case "paeonia" -> "minecraft:peony";
                                                      case "sunflower" -> "minecraft:sunflower";
                                                      case "syringa" -> "minecraft:lilac";
                                                      default -> "minecraft:tall_grass";
                                                   };
                                                   Map<String, String> props = new HashMap<>();
                                                   props.put("half", "lower".equals(half) ? "lower" : "upper");
                                                   return new PngToNbtConverter.BlockState(blockName, props);
                                                } else if (!id.startsWith("minecraft:")
                                                   || !id.endsWith("_ore")
                                                      && !id.equals("minecraft:heavy_weighted_pressure_plate")
                                                      && !id.equals("minecraft:light_weighted_pressure_plate")) {
                                                   System.out.println("  [WARN] No explicit mapping for: " + id + ";" + variant + " → using directly");
                                                   return new PngToNbtConverter.BlockState(id);
                                                } else {
                                                   return new PngToNbtConverter.BlockState(id);
                                                }
                                             }
                                          }
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void fixDoorPairs(ListTag blocksList, List<PngToNbtConverter.BlockState> palette, Map<PngToNbtConverter.BlockState, Integer> paletteIndex) {
      Map<Long, Integer> posIndex = new HashMap<>();

      for (int i = 0; i < blocksList.size(); i++) {
         CompoundTag tag = blocksList.getCompound(i);
         ListTag pos = tag.getList("pos", 3);
         long key = packPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
         posIndex.put(key, i);
      }

      for (int i = 0; i < blocksList.size(); i++) {
         CompoundTag tag = blocksList.getCompound(i);
         int stateIdx = tag.getInt("state");
         PngToNbtConverter.BlockState state = palette.get(stateIdx);
         if (isDoorBlock(state.name) && "lower".equals(state.properties.get("half"))) {
            ListTag pos = tag.getList("pos", 3);
            int x = pos.getInt(0);
            int y = pos.getInt(1);
            int z = pos.getInt(2);
            String facing = state.properties.getOrDefault("facing", "north");
            long upperKey = packPos(x, y + 1, z);
            Integer upperIdx = posIndex.get(upperKey);
            String hinge;
            if (upperIdx != null) {
               CompoundTag upperTag = blocksList.getCompound(upperIdx);
               PngToNbtConverter.BlockState upperState = palette.get(upperTag.getInt("state"));
               if (isDoorBlock(upperState.name) && "upper".equals(upperState.properties.get("half"))) {
                  hinge = upperState.properties.getOrDefault("hinge", "left");
                  PngToNbtConverter.BlockState fixedUpper = new PngToNbtConverter.BlockState(
                     upperState.name, Map.of("facing", facing, "half", "upper", "hinge", hinge, "open", "false")
                  );
                  int fixedUpperIdx = paletteIndex.computeIfAbsent(fixedUpper, s -> {
                     int idx = palette.size();
                     palette.add(s);
                     return idx;
                  });
                  upperTag.putInt("state", fixedUpperIdx);
               } else {
                  hinge = "left";
                  PngToNbtConverter.BlockState generatedUpper = new PngToNbtConverter.BlockState(
                     state.name, Map.of("facing", facing, "half", "upper", "hinge", hinge, "open", "false")
                  );
                  int generatedUpperIdx = paletteIndex.computeIfAbsent(generatedUpper, s -> {
                     int idx = palette.size();
                     palette.add(s);
                     return idx;
                  });
                  upperTag.putInt("state", generatedUpperIdx);
                  System.out
                     .println("  [WARN] Door at (" + x + "," + y + "," + z + "): replaced non-door block above with auto-generated upper half (iso-legacy)");
               }
            } else {
               hinge = "left";
               PngToNbtConverter.BlockState generatedUpper = new PngToNbtConverter.BlockState(
                  state.name, Map.of("facing", facing, "half", "upper", "hinge", hinge, "open", "false")
               );
               int generatedUpperIdx = paletteIndex.computeIfAbsent(generatedUpper, s -> {
                  int idx = palette.size();
                  palette.add(s);
                  return idx;
               });
               CompoundTag newUpperTag = new CompoundTag();
               ListTag upperPos = new ListTag();
               upperPos.add(IntTag.valueOf(x));
               upperPos.add(IntTag.valueOf(y + 1));
               upperPos.add(IntTag.valueOf(z));
               newUpperTag.put("pos", upperPos);
               newUpperTag.putInt("state", generatedUpperIdx);
               blocksList.add(newUpperTag);
               posIndex.put(upperKey, blocksList.size() - 1);
               System.out.println("  [WARN] Door at (" + x + "," + y + "," + z + "): auto-generated missing upper half (iso-legacy)");
            }

            PngToNbtConverter.BlockState fixedLower = new PngToNbtConverter.BlockState(
               state.name, Map.of("facing", facing, "half", "lower", "hinge", hinge, "open", "false")
            );
            int fixedLowerIdx = paletteIndex.computeIfAbsent(fixedLower, s -> {
               int idx = palette.size();
               palette.add(s);
               return idx;
            });
            tag.putInt("state", fixedLowerIdx);
         }
      }
   }

   private static long packPos(int x, int y, int z) {
      return x & 1048575L | (y & 1048575L) << 20 | (z & 1048575L) << 40;
   }

   private static boolean isDoorBlock(String name) {
      return name.endsWith("_door");
   }

   void fixDoubleDoorHinges(
      ListTag blocksList, List<PngToNbtConverter.BlockState> palette, Map<PngToNbtConverter.BlockState, Integer> paletteIndex, int sizeX, int sizeZ
   ) {
      Map<Long, Integer> posIndex = new HashMap<>();
      Map<Long, PngToNbtConverter.BlockState> posState = new HashMap<>();

      for (int i = 0; i < blocksList.size(); i++) {
         CompoundTag tag = blocksList.getCompound(i);
         ListTag pos = tag.getList("pos", 3);
         long key = packPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
         posIndex.put(key, i);
         posState.put(key, palette.get(tag.getInt("state")));
      }

      int fixed = 0;

      for (int i = 0; i < blocksList.size(); i++) {
         CompoundTag tag = blocksList.getCompound(i);
         PngToNbtConverter.BlockState state = palette.get(tag.getInt("state"));
         if (isDoorBlock(state.name) && "lower".equals(state.properties.get("half"))) {
            ListTag pos = tag.getList("pos", 3);
            int x = pos.getInt(0);
            int y = pos.getInt(1);
            int z = pos.getInt(2);
            String facing = state.properties.getOrDefault("facing", "north");
            String doorType = state.name;
            int openX = x;
            int openZ = z;
            int presentX = x;
            int presentZ = z;
            switch (facing) {
               case "north":
                  openX = x - 1;
                  presentX = x + 1;
                  break;
               case "east":
                  openZ = z - 1;
                  presentZ = z + 1;
                  break;
               case "south":
                  openX = x + 1;
                  presentX = x - 1;
                  break;
               case "west":
                  openZ = z + 1;
                  presentZ = z - 1;
                  break;
               default:
                  continue;
            }

            long openKey = packPos(openX, y, openZ);
            PngToNbtConverter.BlockState openState = posState.get(openKey);
            boolean openSideOpen;
            if (openState == null) {
               openSideOpen = true;
            } else {
               openSideOpen = "minecraft:air".equals(openState.name) || doorType.equals(openState.name);
            }

            boolean presentSideInBounds = presentX >= 0 && presentX < sizeX && presentZ >= 0 && presentZ < sizeZ;
            String targetHinge = openSideOpen && presentSideInBounds ? "right" : "left";
            String currentHinge = state.properties.getOrDefault("hinge", "left");
            if (!targetHinge.equals(currentHinge)) {
               Map<String, String> lowerProps = new HashMap<>(state.properties);
               lowerProps.put("hinge", targetHinge);
               PngToNbtConverter.BlockState fixedLower = new PngToNbtConverter.BlockState(state.name, lowerProps);
               int fixedLowerIdx = paletteIndex.computeIfAbsent(fixedLower, s -> {
                  int idx = palette.size();
                  palette.add(s);
                  return idx;
               });
               tag.putInt("state", fixedLowerIdx);
               long upperKey = packPos(x, y + 1, z);
               Integer upperIdx = posIndex.get(upperKey);
               if (upperIdx != null) {
                  CompoundTag upperTag = blocksList.getCompound(upperIdx);
                  PngToNbtConverter.BlockState upperState = palette.get(upperTag.getInt("state"));
                  if (doorType.equals(upperState.name) && "upper".equals(upperState.properties.get("half"))) {
                     Map<String, String> upperProps = new HashMap<>(upperState.properties);
                     upperProps.put("hinge", targetHinge);
                     PngToNbtConverter.BlockState fixedUpper = new PngToNbtConverter.BlockState(upperState.name, upperProps);
                     int fixedUpperIdx = paletteIndex.computeIfAbsent(fixedUpper, s -> {
                        int idx = palette.size();
                        palette.add(s);
                        return idx;
                     });
                     upperTag.putInt("state", fixedUpperIdx);
                  }
               }

               fixed++;
               System.out
                  .println(
                     "  [DOOR] Fixed hinge at (" + x + "," + y + "," + z + ") " + doorType + " facing=" + facing + ": " + currentHinge + " -> " + targetHinge
                  );
            }
         }
      }

      if (fixed > 0) {
         System.out.println("  [DOOR] Fixed " + fixed + " double-door hinge(s)");
      }
   }

   private PngToNbtConverter.BlockState mapDoor(String id, String variant) {
      Map<String, String> props = new HashMap<>();
      String facing = this.extractVariant(variant, "facing");
      String half = this.extractVariant(variant, "half");
      String hinge = this.extractVariant(variant, "hinge");
      props.put("facing", facing != null ? facing : "north");
      props.put("half", half != null ? half : "lower");
      props.put("hinge", hinge != null ? hinge : "left");
      props.put("open", "false");

      String doorBlock = switch (id) {
         case "minecraft:wooden_door" -> "minecraft:oak_door";
         case "minecraft:spruce_door" -> "minecraft:spruce_door";
         case "minecraft:birch_door" -> "minecraft:birch_door";
         case "minecraft:jungle_door" -> "minecraft:jungle_door";
         case "minecraft:acacia_door" -> "minecraft:acacia_door";
         case "minecraft:dark_oak_door" -> "minecraft:dark_oak_door";
         case "minecraft:iron_door" -> "minecraft:iron_door";
         default -> "minecraft:oak_door";
      };
      return new PngToNbtConverter.BlockState(doorBlock, props);
   }

   private PngToNbtConverter.BlockState mapStoneSlab(String id, String variant) {
      Map<String, String> props = new HashMap<>();
      int meta = this.parseIntSafe(variant);
      boolean top = meta >= 8;
      if (top) {
         props.put("type", "top");
         meta -= 8;
      } else if (variant.contains("half=top")) {
         props.put("type", "top");
      } else {
         props.put("type", "bottom");
      }

      if (variant.contains("variant=quartz")) {
         return new PngToNbtConverter.BlockState("minecraft:quartz_slab", props);
      }

      if ("minecraft:stone_slab2".equals(id)) {
         return new PngToNbtConverter.BlockState("minecraft:red_sandstone_slab", props);
      }

      return switch (meta) {
         case 0 -> new PngToNbtConverter.BlockState("minecraft:smooth_stone_slab", props);
         case 1 -> new PngToNbtConverter.BlockState("minecraft:sandstone_slab", props);
         default -> new PngToNbtConverter.BlockState("minecraft:smooth_stone_slab", props);
         case 3 -> new PngToNbtConverter.BlockState("minecraft:cobblestone_slab", props);
         case 4 -> new PngToNbtConverter.BlockState("minecraft:brick_slab", props);
         case 5 -> new PngToNbtConverter.BlockState("minecraft:stone_brick_slab", props);
      };
   }

   private PngToNbtConverter.BlockState mapColoredBlock(String variant, String suffix, String defaultBlock) {
      String[] colors = new String[]{
         "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
      };
      int meta = this.parseIntSafe(variant);
      return meta >= 0 && meta < colors.length
         ? new PngToNbtConverter.BlockState("minecraft:" + colors[meta] + suffix)
         : new PngToNbtConverter.BlockState(defaultBlock);
   }

   private PngToNbtConverter.BlockState mapCarpet(String variant) {
      String[] colors = new String[]{
         "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
      };
      int meta = this.parseIntSafe(variant);
      return meta >= 0 && meta < colors.length
         ? new PngToNbtConverter.BlockState("minecraft:" + colors[meta] + "_carpet")
         : new PngToNbtConverter.BlockState("minecraft:white_carpet");
   }

   private PngToNbtConverter.BlockState mapColoredTerracotta(String variant) {
      String color = this.extractVariant(variant, "color");
      if (color == null) {
         int meta = this.parseIntSafe(variant);
         String[] colors = new String[]{
            "white",
            "orange",
            "magenta",
            "light_blue",
            "yellow",
            "lime",
            "pink",
            "gray",
            "light_gray",
            "cyan",
            "purple",
            "blue",
            "brown",
            "green",
            "red",
            "black"
         };
         if (meta >= 0 && meta < colors.length) {
            color = colors[meta];
         }
      }

      if (color == null) {
         return new PngToNbtConverter.BlockState("minecraft:terracotta");
      }

      if ("silver".equals(color)) {
         color = "light_gray";
      }

      return new PngToNbtConverter.BlockState("minecraft:" + color + "_terracotta");
   }

   private Map<String, String> parseFacingProps(String variant) {
      Map<String, String> props = new HashMap<>();
      if (variant != null && !variant.isEmpty()) {
         for (String part : variant.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
               String key = kv[0].trim();
               String value = kv[1].trim();
               if ("facing".equals(key) || "half".equals(key) || "axis".equals(key) || "part".equals(key) || "open".equals(key) || "hinge".equals(key)) {
                  props.put(key, value);
               }
            }
         }

         return props;
      } else {
         return props;
      }
   }

   private Map<String, String> parseRosetteProps(String variant) {
      Map<String, String> props = new HashMap<>();
      if (variant != null && !variant.isEmpty()) {
         for (String part : variant.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
               String key = kv[0].trim();
               String value = kv[1].trim();
               switch (key) {
                  case "facing":
                     props.put("facing", value);
                     break;
                  case "topbottom":
                     props.put("half", value);
               }
            }
         }

         return props;
      } else {
         return props;
      }
   }

   private Map<String, String> parseSlabProps(String variant) {
      Map<String, String> props = new HashMap<>();
      if (variant.contains("half=top")) {
         props.put("type", "top");
      } else {
         props.put("type", "bottom");
      }

      return props;
   }

   private String extractVariant(String variant, String key) {
      if (variant != null && !variant.isEmpty()) {
         for (String part : variant.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals(key)) {
               return kv[1].trim();
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private int parseIntSafe(String s) {
      if (s != null && !s.isEmpty()) {
         try {
            return Integer.parseInt(s.trim());
         } catch (NumberFormatException e) {
            return -1;
         }
      } else {
         return -1;
      }
   }

   static int rgb(int r, int g, int b) {
      return r << 16 | g << 8 | b;
   }

   public int[][][] decodePng(Path pngPath, int width, int length) throws IOException {
      BufferedImage img;
      try (InputStream is = Files.newInputStream(pngPath)) {
         img = ImageIO.read(is);
      }

      int pngWidth = img.getWidth();
      int pngHeight = img.getHeight();
      int nbFloors = (pngWidth + 1) / (width + 1);
      System.out.println("  PNG: " + pngWidth + "x" + pngHeight + ", building: " + width + "x" + length + ", floors: " + nbFloors);
      if (pngHeight != length) {
         System.out.println("  [WARN] PNG height (" + pngHeight + ") != building length (" + length + ")");
      }

      int[][][] grid = new int[nbFloors][width][length];

      for (int floor = 0; floor < nbFloors; floor++) {
         for (int widthPos = 0; widthPos < width; widthPos++) {
            int px = floor * (width + 1) + (width - widthPos - 1);

            for (int z = 0; z < length; z++) {
               int py = z;
               if (px < pngWidth && py < pngHeight) {
                  int argb = img.getRGB(px, py);
                  if ((argb >> 24 & 0xFF) != 255) {
                     grid[floor][widthPos][z] = WHITE;
                  } else {
                     int r = argb >> 16 & 0xFF;
                     int g = argb >> 8 & 0xFF;
                     int b = argb & 0xFF;
                     grid[floor][widthPos][z] = rgb(r, g, b);
                  }
               } else {
                  grid[floor][widthPos][z] = WHITE;
               }
            }
         }
      }

      return grid;
   }

   public Set<Integer> collectColors(Path pngPath, int width, int length) throws IOException {
      int[][][] grid = this.decodePng(pngPath, width, length);
      Set<Integer> colors = new HashSet<>();

      for (int[][] floor : grid) {
         for (int[] row : floor) {
            for (int c : row) {
               colors.add(c);
            }
         }
      }

      return colors;
   }

   public PngToNbtConverter.ConversionResult convert(Path pngPath, int width, int length, Path outputPath, String name) throws IOException {
      System.out.println("Converting " + name + "...");
      int[][][] grid = this.decodePng(pngPath, width, length);
      int nbFloors = grid.length;
      int height = nbFloors;
      List<PngToNbtConverter.BlockState> palette = new ArrayList<>();
      Map<PngToNbtConverter.BlockState, Integer> paletteIndex = new LinkedHashMap<>();
      ListTag blocksList = new ListTag();
      int airSkipped = 0;
      Set<Integer> unmappedColors = new LinkedHashSet<>();
      Map<String, Integer> blockCounts = new TreeMap<>();
      List<PngToNbtConverter.SpecialPoint> specialPoints = new ArrayList<>();

      for (int floor = 0; floor < nbFloors; floor++) {
         for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
               int color = grid[floor][x][z];
               int templateX = z;
               int templateY = floor;
               int templateZ = x;
               PngToNbtConverter.SpecialPointInfo spInfo = this.specialPointMap.get(color);
               if (spInfo != null) {
                  specialPoints.add(
                     new PngToNbtConverter.SpecialPoint(spInfo.type, spInfo.subtype, spInfo.orientation, spInfo.placement, templateX, templateY, templateZ)
                  );
                  PngToNbtConverter.BlockState mockState = this.specialPointToMockBlockState(spInfo);
                  if (mockState != null) {
                     int idx = paletteIndex.computeIfAbsent(mockState, s -> {
                        int i = palette.size();
                        palette.add(s);
                        return i;
                     });
                     CompoundTag blockTag = new CompoundTag();
                     ListTag posTag = new ListTag();
                     posTag.add(IntTag.valueOf(templateX));
                     posTag.add(IntTag.valueOf(templateY));
                     posTag.add(IntTag.valueOf(templateZ));
                     blockTag.put("pos", posTag);
                     blockTag.putInt("state", idx);
                     blocksList.add(blockTag);
                     blockCounts.merge(mockState.name, 1, Integer::sum);
                     continue;
                  }
               }

               PngToNbtConverter.BlockState state = this.colorMap.get(color);
               if (state != null) {
                  int idx = paletteIndex.computeIfAbsent(state, s -> {
                     int i = palette.size();
                     palette.add(s);
                     return i;
                  });
                  CompoundTag blockTag = new CompoundTag();
                  ListTag posTag = new ListTag();
                  posTag.add(IntTag.valueOf(templateX));
                  posTag.add(IntTag.valueOf(templateY));
                  posTag.add(IntTag.valueOf(templateZ));
                  blockTag.put("pos", posTag);
                  blockTag.putInt("state", idx);
                  blocksList.add(blockTag);
                  blockCounts.merge(state.name, 1, Integer::sum);
               } else if (this.isAirColor(color)) {
                  airSkipped++;
               } else {
                  unmappedColors.add(color);
                  this.unmappedColourSet.add(color);
                  airSkipped++;
               }
            }
         }
      }

      this.fixDoorPairs(blocksList, palette, paletteIndex);
      this.fixDoubleDoorHinges(blocksList, palette, paletteIndex, length, width);
      CompoundTag root = new CompoundTag();
      root.putInt("DataVersion", DATA_VERSION);
      ListTag sizeTag = new ListTag();
      sizeTag.add(IntTag.valueOf(length));
      sizeTag.add(IntTag.valueOf(height));
      sizeTag.add(IntTag.valueOf(width));
      root.put("size", sizeTag);
      ListTag paletteTag = new ListTag();

      for (PngToNbtConverter.BlockState state : palette) {
         paletteTag.add(state.toNbt());
      }

      root.put("palette", paletteTag);
      root.put("blocks", blocksList);
      root.put("entities", new ListTag());
      Files.createDirectories(outputPath.getParent());
      NbtIo.writeCompressed(root, outputPath);
      int totalBlocks = blocksList.size();
      System.out.println("  Size: " + width + "x" + height + "x" + length);
      System.out.println("  Blocks placed: " + totalBlocks);
      System.out.println("  Air/skip: " + airSkipped);
      System.out.println("  Special points: " + specialPoints.size());
      System.out.println("  Palette: " + palette.size() + " entries");
      if (!unmappedColors.isEmpty()) {
         System.out.println("  Unmapped colors: " + unmappedColors.size());

         for (int c : unmappedColors) {
            int r = c >> 16 & 0xFF;
            int g = c >> 8 & 0xFF;
            int b = c & 0xFF;
            System.out.println("    RGB(" + r + "," + g + "," + b + ")");
         }
      }

      System.out.println("  File: " + outputPath);
      return new PngToNbtConverter.ConversionResult(
         name, width, height, length, totalBlocks, airSkipped, specialPoints.size(), unmappedColors, blockCounts, root
      );
   }

   private PngToNbtConverter.BlockState specialPointToMockBlockState(PngToNbtConverter.SpecialPointInfo info) {
      return switch (info.type) {
         case "sleepingPos" -> this.mockMarkerState("sleeping_pos");
         case "sellingPos" -> this.mockMarkerState("selling_pos");
         case "craftingPos" -> this.mockMarkerState("crafting_pos");
         case "defendingPos" -> this.mockMarkerState("defending_pos");
         case "shelterPos" -> this.mockMarkerState("shelter_pos");
         case "pathStartPos" -> this.mockMarkerState("path_start_pos");
         case "leisurePos" -> this.mockMarkerState("leisure_pos");
         case "stall" -> this.mockMarkerState("stall");
         case "fishingSpot" -> this.mockMarkerState("fishing_spot");
         case "preserve_ground" -> {
            String markerType = switch (info.subtype) {
               case "surface" -> "preserve_ground";
               case "depth" -> "preserve_ground_depth";
               case "allbuttrees" -> "preserve_ground_allbuttrees";
               case "grass" -> "preserve_ground_grass";
               default -> "preserve_ground";
            };
            yield this.mockMarkerState(markerType);
         }
         case "torchGuess" -> this.mockMarkerState("torch");
         case "furnace", "signPos" -> {
            String mockType = info.type.equals("furnace") ? "furnace" : "sign_pos";
            Map<String, String> props = new HashMap<>();
            props.put("type", mockType);
            if (info.orientation != null && !"guess".equals(info.orientation)) {
               props.put("facing", info.orientation);
               props.put("guess", "false");
            } else {
               props.put("facing", "north");
               props.put("guess", "true");
            }

            yield new PngToNbtConverter.BlockState("millenaire:mock_facing_marker", props);
         }
         case "healingSpot" -> this.mockMarkerState("healing_spot");
         case "brickSpot" -> this.mockMarkerState("brick_spot");
         case "silkwormBlock" -> this.mockMarkerState("silkworm_block");
         case "snailSoilBlock" -> this.mockMarkerState("snail_soil_block");
         case "cacaoSpot" -> this.mockMarkerState("cacao_spot");
         case "plainSign" -> {
            Map<String, String> props = new HashMap<>();
            props.put("type", "sign_pos");
            props.put("facing", "north");
            props.put("guess", "true");
            yield new PngToNbtConverter.BlockState("millenaire:mock_facing_marker", props);
         }
         case "decorative" -> this.mockBlockState("millenaire:mock_decor", "decor_type", info.subtype);
         case "banner" -> {
            String placement = info.placement;
            if (placement == null) {
               yield null;
            } else {
               Map<String, String> props = new HashMap<>();
               props.put("subtype", info.subtype);
               if (placement.startsWith("wall_")) {
                  props.put("facing", placement.substring("wall_".length()));
                  yield new PngToNbtConverter.BlockState("millenaire:mock_banner_wall", props);
               } else if (placement.startsWith("standing_")) {
                  props.put("rotation", placement.substring("standing_".length()));
                  yield new PngToNbtConverter.BlockState("millenaire:mock_banner_standing", props);
               } else {
                  yield null;
               }
            }
         }
         case "soil" -> this.mockBlockState("millenaire:mock_soil", "crop", info.subtype);
         case "source" -> this.mockBlockState("millenaire:mock_source", "material", info.subtype);
         case "freeBlock" -> this.mockBlockState("millenaire:mock_free", "material", info.subtype);
         case "treeSpawn" -> this.mockBlockState("millenaire:mock_tree_spawn", "tree", info.subtype);
         case "animalSpawn" -> this.mockBlockState("millenaire:mock_animal_spawn", "animal", info.subtype);
         case "chest" -> {
            Map<String, String> props = new HashMap<>();
            props.put("chest_type", info.subtype);
            if (info.orientation != null && !"guess".equals(info.orientation)) {
               props.put("facing", info.orientation);
               props.put("guess", "false");
            } else {
               props.put("facing", "north");
               props.put("guess", "true");
            }

            yield new PngToNbtConverter.BlockState("millenaire:mock_chest", props);
         }
         default -> {
            String sig = info.type + "/" + info.subtype;
            if (this.unmappedSpecialPointSignatures.add(sig)) {
               System.out.println("  WARNING: special point type not converted to mock block: " + sig);
            }

            yield null;
         }
      };
   }

   private PngToNbtConverter.BlockState mockMarkerState(String type) {
      return this.mockBlockState("millenaire:mock_marker", "type", type);
   }

   private PngToNbtConverter.BlockState mockBlockState(String name, String propKey, String propValue) {
      Map<String, String> props = new HashMap<>();
      props.put(propKey, propValue);
      return new PngToNbtConverter.BlockState(name, props);
   }

   private boolean isAirColor(int color) {
      if (color != WHITE && !this.specialColors.contains(color)) {
         int r = color >> 16 & 0xFF;
         int g = color >> 8 & 0xFF;
         int b = color & 0xFF;
         return r > 245 && g > 245 && b > 245;
      } else {
         return true;
      }
   }

   public Map<Integer, PngToNbtConverter.BlockState> getColorMap() {
      return Collections.unmodifiableMap(this.colorMap);
   }

   public Set<Integer> getSpecialColors() {
      return Collections.unmodifiableSet(this.specialColors);
   }

   public record BlockState(String name, Map<String, String> properties) {
      public BlockState(String name) {
         this(name, Map.of());
      }

      public CompoundTag toNbt() {
         CompoundTag tag = new CompoundTag();
         tag.putString("Name", this.name);
         if (!this.properties.isEmpty()) {
            CompoundTag props = new CompoundTag();
            this.properties.forEach(props::putString);
            tag.put("Properties", props);
         }

         return tag;
      }
   }

   private record BlocklistEntry(String name, String blockId, String variant, boolean setAfter, int rgb) {
   }

   public record ConversionResult(
      String buildingName,
      int width,
      int height,
      int depth,
      int totalBlocks,
      int airSkipped,
      int specialPointCount,
      Set<Integer> unmappedColors,
      Map<String, Integer> blockCounts,
      CompoundTag templateNbt
   ) {
   }

   public record SpecialPoint(String type, String subtype, String orientation, String placement, int x, int y, int z) {
   }

   private record SpecialPointInfo(String type, String subtype, String orientation, String placement) {
   }
}
