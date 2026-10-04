package org.millenaire.building;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.slf4j.Logger;

public final class MockBlockExtractor {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String MOD_PREFIX = "millenaire:mock_";

   private MockBlockExtractor() {
   }

   public static List<SpecialPoint> extract(CompoundTag nbt) {
      ListTag paletteTag = NbtPaletteHelper.resolvePaletteTag(nbt);
      if (paletteTag == null) {
         return Collections.emptyList();
      }

      MockBlockExtractor.SpecialPointTemplate[] mockInfos = new MockBlockExtractor.SpecialPointTemplate[paletteTag.size()];
      boolean hasSpecialPoints = false;

      for (int i = 0; i < paletteTag.size(); i++) {
         CompoundTag entry = paletteTag.getCompound(i);
         String name = entry.getString("Name");
         if (name.startsWith("millenaire:mock_")) {
            CompoundTag props = entry.contains("Properties", 10) ? entry.getCompound("Properties") : new CompoundTag();
            MockBlockExtractor.SpecialPointTemplate info = parseEntry(name, props);
            if (info != null) {
               mockInfos[i] = info;
               hasSpecialPoints = true;
            }
         } else if ("minecraft:campfire".equals(name)) {
            mockInfos[i] = new MockBlockExtractor.SpecialPointTemplate("hearth", null, null);
            hasSpecialPoints = true;
         }
      }

      if (!hasSpecialPoints) {
         return Collections.emptyList();
      }

      ListTag blocksTag = nbt.getList("blocks", 10);
      List<SpecialPoint> points = new ArrayList<>();

      for (int i = 0; i < blocksTag.size(); i++) {
         CompoundTag blockEntry = blocksTag.getCompound(i);
         int stateIndex = blockEntry.getInt("state");
         if (stateIndex >= 0 && stateIndex < mockInfos.length) {
            MockBlockExtractor.SpecialPointTemplate info = mockInfos[stateIndex];
            if (info != null) {
               ListTag posTag = blockEntry.getList("pos", 3);
               BlockPos pos = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
               points.add(new SpecialPoint(info.type, info.subtype, info.orientation, info.placement, pos));
            }
         }
      }

      LOGGER.debug("Extracted {} special points from NBT", points.size());
      return Collections.unmodifiableList(points);
   }

   private static MockBlockExtractor.SpecialPointTemplate parseEntry(String blockName, CompoundTag props) {
      String suffix = blockName.substring("millenaire:mock_".length());

      return switch (suffix) {
         case "marker" -> parseMarker(props.getString("type"));
         case "facing_marker" -> parseFacingMarker(props);
         case "soil" -> new MockBlockExtractor.SpecialPointTemplate("soil", props.getString("crop"), null);
         case "source" -> new MockBlockExtractor.SpecialPointTemplate("source", props.getString("material"), null);
         case "free" -> new MockBlockExtractor.SpecialPointTemplate("freeBlock", props.getString("material"), null);
         case "tree_spawn" -> new MockBlockExtractor.SpecialPointTemplate("treeSpawn", props.getString("tree"), null);
         case "animal_spawn" -> new MockBlockExtractor.SpecialPointTemplate("animalSpawn", props.getString("animal"), null);
         case "chest" -> {
            String chestType = props.getString("chest_type");
            if ("main".equals(chestType)) {
               chestType = "locked";
            }

            yield new MockBlockExtractor.SpecialPointTemplate("chest", chestType, props.getString("facing"));
         }
         case "decor" -> new MockBlockExtractor.SpecialPointTemplate("wall_decoration", props.getString("decor_type"), null);
         case "banner_wall" -> {
            String facing = props.getString("facing");
            String subtype = props.contains("subtype", 8) ? props.getString("subtype") : "village";
            yield new MockBlockExtractor.SpecialPointTemplate("banner", subtype, facing, "wall_" + facing);
         }
         case "banner_standing" -> {
            String rotation = props.getString("rotation");
            String subtype = props.contains("subtype", 8) ? props.getString("subtype") : "village";
            yield new MockBlockExtractor.SpecialPointTemplate("banner", subtype, rotation, "standing_" + rotation);
         }
         default -> {
            LOGGER.warn("Unknown mock block: {}", blockName);
            yield null;
         }
      };
   }

   private static MockBlockExtractor.SpecialPointTemplate parseFacingMarker(CompoundTag props) {
      String typeStr = props.getString("type");
      String facing = props.getString("facing");
      boolean guess = "true".equals(props.getString("guess"));
      String orientation = guess ? "guess" : facing;

      return switch (typeStr) {
         case "furnace" -> new MockBlockExtractor.SpecialPointTemplate("furnace", null, orientation);
         case "sign_pos" -> new MockBlockExtractor.SpecialPointTemplate("signPos", null, orientation);
         default -> null;
      };
   }

   private static MockBlockExtractor.SpecialPointTemplate parseMarker(String typeStr) {
      return switch (typeStr) {
         case "sleeping_pos" -> new MockBlockExtractor.SpecialPointTemplate("sleepingPos", null, null);
         case "selling_pos" -> new MockBlockExtractor.SpecialPointTemplate("sellingPos", null, null);
         case "crafting_pos" -> new MockBlockExtractor.SpecialPointTemplate("craftingPos", null, null);
         case "defending_pos" -> new MockBlockExtractor.SpecialPointTemplate("defendingPos", null, null);
         case "shelter_pos" -> new MockBlockExtractor.SpecialPointTemplate("shelterPos", null, null);
         case "path_start_pos" -> new MockBlockExtractor.SpecialPointTemplate("pathStartPos", null, null);
         case "leisure_pos" -> new MockBlockExtractor.SpecialPointTemplate("leisurePos", null, null);
         case "stall" -> new MockBlockExtractor.SpecialPointTemplate("stall", null, null);
         case "fishing_spot" -> new MockBlockExtractor.SpecialPointTemplate("fishingSpot", null, null);
         case "preserve_ground" -> new MockBlockExtractor.SpecialPointTemplate("preserve_ground", "surface", null);
         case "preserve_ground_depth" -> new MockBlockExtractor.SpecialPointTemplate("preserve_ground", "depth", null);
         case "preserve_ground_allbuttrees" -> new MockBlockExtractor.SpecialPointTemplate("preserve_ground", "allbuttrees", null);
         case "preserve_ground_grass" -> new MockBlockExtractor.SpecialPointTemplate("preserve_ground", "grass", null);
         case "torch" -> new MockBlockExtractor.SpecialPointTemplate("torchGuess", null, "guess");
         case "healing_spot" -> new MockBlockExtractor.SpecialPointTemplate("healingSpot", null, null);
         case "brick_spot" -> new MockBlockExtractor.SpecialPointTemplate("brick_spot", null, null);
         case "silkworm_block" -> new MockBlockExtractor.SpecialPointTemplate("silkwormBlock", null, null);
         case "snail_soil_block" -> new MockBlockExtractor.SpecialPointTemplate("snailSoilBlock", null, null);
         case "fireplace" -> new MockBlockExtractor.SpecialPointTemplate("fireplace", null, null);
         default -> null;
      };
   }

   private record SpecialPointTemplate(String type, String subtype, String orientation, String placement) {
      SpecialPointTemplate(String type, String subtype, String orientation) {
         this(type, subtype, orientation, null);
      }
   }
}
