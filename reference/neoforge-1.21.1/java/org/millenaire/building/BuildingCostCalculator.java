package org.millenaire.building;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public final class BuildingCostCalculator {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Set<String> FREE_BLOCKS = Set.of(
      "minecraft:air",
      "minecraft:cave_air",
      "minecraft:void_air",
      "minecraft:structure_void",
      "minecraft:dirt",
      "minecraft:grass_block",
      "minecraft:coarse_dirt",
      "minecraft:podzol",
      "minecraft:mud",
      "minecraft:farmland",
      "minecraft:dirt_path",
      "minecraft:water",
      "minecraft:lava",
      "minecraft:snow",
      "minecraft:snow_block",
      "minecraft:powder_snow",
      "minecraft:clay",
      "minecraft:short_grass",
      "minecraft:tall_grass",
      "minecraft:fern",
      "minecraft:large_fern",
      "minecraft:dead_bush",
      "minecraft:dandelion",
      "minecraft:poppy",
      "minecraft:blue_orchid",
      "minecraft:allium",
      "minecraft:azure_bluet",
      "minecraft:red_tulip",
      "minecraft:orange_tulip",
      "minecraft:white_tulip",
      "minecraft:pink_tulip",
      "minecraft:oxeye_daisy",
      "minecraft:cornflower",
      "minecraft:lily_of_the_valley",
      "minecraft:sunflower",
      "minecraft:lilac",
      "minecraft:rose_bush",
      "minecraft:peony",
      "minecraft:wither_rose",
      "minecraft:torchflower",
      "minecraft:pitcher_plant",
      "minecraft:note_block",
      "minecraft:redstone_wire",
      "minecraft:redstone_torch",
      "minecraft:redstone_wall_torch",
      "minecraft:redstone_lamp",
      "minecraft:cake",
      "minecraft:nether_wart",
      "minecraft:nether_portal"
   );
   private static final Set<String> FREE_MILLENAIRE_BLOCKS = Set.of(
      "millenaire:path_dirt",
      "millenaire:path_dirt_slab",
      "millenaire:path_gravel",
      "millenaire:path_gravel_slab",
      "millenaire:path_ochre_tiles",
      "millenaire:path_sandstone",
      "millenaire:path_slabs",
      "millenaire:path_slabs_slab",
      "millenaire:dirt_wall"
   );
   private static final Set<String> FREE_MOCK_BLOCKS = Set.of(
      "millenaire:mock_soil", "millenaire:mock_source", "millenaire:mock_free", "millenaire:mock_tree_spawn", "millenaire:mock_animal_spawn"
   );
   private static final String OAK_PLANKS = "minecraft:oak_planks";
   private static final String ANYWOOD_PLANKS = "millenaire:anywood_planks";
   private static final Map<String, String> WOOD_TYPES = Map.ofEntries(
      Map.entry("oak", "oak"),
      Map.entry("spruce", "spruce"),
      Map.entry("birch", "birch"),
      Map.entry("jungle", "jungle"),
      Map.entry("acacia", "acacia"),
      Map.entry("dark_oak", "dark_oak"),
      Map.entry("mangrove", "mangrove"),
      Map.entry("cherry", "cherry"),
      Map.entry("bamboo", "bamboo"),
      Map.entry("crimson", "crimson"),
      Map.entry("warped", "warped")
   );
   private static final String[] VARIANT_SUFFIXES = new String[]{"_stairs", "_slab", "_wall"};
   private static final Set<String> NOT_VARIANTS = Set.of("millenaire:paper_wall", "millenaire:dirt_wall");
   private static final Map<String, String> VARIANT_OVERRIDES = Map.of(
      "millenaire:snow_wall",
      "millenaire:snow_brick",
      "millenaire:timber_frame_stairs",
      "millenaire:timber_frame_plain",
      "millenaire:timber_frame_slab",
      "millenaire:timber_frame_plain"
   );
   private static final Map<String, String> COST_ALIASES = Map.of(
      "millenaire:byzantine_stone_tiles",
      "millenaire:byzantine_tiles",
      "millenaire:byzantine_sandstone_tiles",
      "millenaire:byzantine_tiles",
      "millenaire:byzantine_stone_ornament",
      "millenaire:byzantine_tiles",
      "millenaire:byzantine_sandstone_ornament",
      "millenaire:byzantine_tiles"
   );
   private static final Set<String> TILE_SLAB_HALF_COST = Set.of(
      "millenaire:byzantine_tiles_slab", "millenaire:gray_tiles_slab", "millenaire:green_tiles_slab", "millenaire:red_tiles_slab"
   );

   private BuildingCostCalculator() {
   }

   public static Map<ResourceLocation, Integer> computeCost(CompoundTag templateNbt) {
      List<BuildingCostCalculator.PaletteEntry> palette = parsePalette(templateNbt);
      if (palette.isEmpty()) {
         return Map.of();
      }

      Map<Integer, Integer> blockCounts = countBlocks(templateNbt);
      Map<ResourceLocation, Integer> costs = new HashMap<>();

      for (Entry<Integer, Integer> entry : blockCounts.entrySet()) {
         int paletteIndex = entry.getKey();
         int count = entry.getValue();
         if (paletteIndex >= 0 && paletteIndex < palette.size()) {
            BuildingCostCalculator.PaletteEntry pe = palette.get(paletteIndex);
            classifyAndAccumulate(pe, count, costs);
         }
      }

      postProcess(costs);
      return costs;
   }

   private static List<BuildingCostCalculator.PaletteEntry> parsePalette(CompoundTag nbt) {
      List<BuildingCostCalculator.PaletteEntry> result = new ArrayList<>();
      ListTag paletteTag = NbtPaletteHelper.resolvePaletteTag(nbt);
      if (paletteTag == null) {
         return result;
      }

      for (int i = 0; i < paletteTag.size(); i++) {
         CompoundTag entry = paletteTag.getCompound(i);
         String name = entry.getString("Name");
         Map<String, String> props = new HashMap<>();
         if (entry.contains("Properties", 10)) {
            CompoundTag propsTag = entry.getCompound("Properties");

            for (String key : propsTag.getAllKeys()) {
               props.put(key, propsTag.getString(key));
            }
         }

         result.add(new BuildingCostCalculator.PaletteEntry(name, props));
      }

      return result;
   }

   private static Map<Integer, Integer> countBlocks(CompoundTag nbt) {
      Map<Integer, Integer> counts = new HashMap<>();
      if (!nbt.contains("blocks", 9)) {
         return counts;
      }

      ListTag blocksTag = nbt.getList("blocks", 10);

      for (int i = 0; i < blocksTag.size(); i++) {
         CompoundTag blockEntry = blocksTag.getCompound(i);
         int stateIndex = blockEntry.getInt("state");
         counts.merge(stateIndex, 1, Integer::sum);
      }

      return counts;
   }

   private static void classifyAndAccumulate(BuildingCostCalculator.PaletteEntry pe, int count, Map<ResourceLocation, Integer> costs) {
      String name = pe.name();
      if (!isFree(pe)) {
         if (name.equals("millenaire:mock_chest")) {
            addCost(costs, ResourceLocation.parse("millenaire:anywood_planks"), count * 8);
         } else if (name.equals("millenaire:mock_marker")) {
            String type = pe.prop("type");
            switch (type) {
               case "torch":
                  addCost(costs, ResourceLocation.parse("millenaire:anywood_planks"), count);
                  break;
               case "furnace":
                  addCost(costs, ResourceLocation.parse("minecraft:cobblestone"), count * 8);
            }
         } else if (name.equals("millenaire:mock_facing_marker")) {
            String type = pe.prop("type");
            switch (type) {
               case "furnace":
                  addCost(costs, ResourceLocation.parse("minecraft:cobblestone"), count * 8);
                  break;
               case "sign_pos":
                  addCost(costs, ResourceLocation.parse("millenaire:anywood_planks"), count * 7);
            }
         } else if (name.equals("millenaire:mock_decor")) {
            String decorType = pe.prop("decor_type");
            String itemName = decorType.startsWith("wall_") ? decorType : "wall_" + decorType;
            addCost(costs, ResourceLocation.fromNamespaceAndPath("millenaire", itemName), count);
         } else if (!"upper".equals(pe.prop("half"))) {
            if (!"foot".equals(pe.prop("part"))) {
               Integer anywoodPlanks = getAnywoodCost(name);
               if (anywoodPlanks != null) {
                  addCost(costs, ResourceLocation.parse("millenaire:anywood_planks"), count * anywoodPlanks);
               } else {
                  String woodType = detectWoodType(name);
                  if (woodType != null) {
                     int planks = getTypedWoodCost(name, woodType);
                     String planksKey = "minecraft:" + woodType + "_planks";
                     addCost(costs, ResourceLocation.parse(planksKey), count * planks);
                  } else {
                     ResourceLocation stoneCost = getStoneCost(name);
                     if (stoneCost != null) {
                        int qty = name.contains("pressure_plate") ? 2 : 1;
                        addCost(costs, stoneCost, count * qty);
                     } else if (name.contains("glass_pane")) {
                        addCost(costs, ResourceLocation.parse("minecraft:glass_pane"), count);
                     } else if (name.equals("minecraft:glass")) {
                        addCost(costs, ResourceLocation.parse("minecraft:glass"), count);
                     } else {
                        BuildingCostCalculator.MetalCost metalCost = getMetalCost(name);
                        if (metalCost != null) {
                           addCost(costs, metalCost.item, count * metalCost.quantity);
                        } else if (name.contains("_wool")) {
                           addCost(costs, ResourceLocation.parse("minecraft:white_wool"), count);
                        } else if (name.equals("millenaire:straw_bed") || name.equals("millenaire:charpoy") || name.equals("millenaire:futon")) {
                           addCost(costs, ResourceLocation.parse(name), count);
                        } else if (name.startsWith("minecraft:") && name.endsWith("_bed")) {
                           addCost(costs, ResourceLocation.parse("minecraft:white_wool"), count * 3);
                           addCost(costs, ResourceLocation.parse("millenaire:anywood_planks"), count * 3);
                        } else if (name.equals("minecraft:furnace")) {
                           addCost(costs, ResourceLocation.parse("minecraft:cobblestone"), count * 8);
                        } else if (name.equals("minecraft:campfire") || name.equals("minecraft:soul_campfire")) {
                           addCost(costs, ResourceLocation.parse("millenaire:anywood_planks"), count * 16);
                           if (name.equals("minecraft:soul_campfire")) {
                              addCost(costs, ResourceLocation.parse("minecraft:soul_sand"), count);
                           }
                        } else if (name.equals("millenaire:mock_banner_standing") || name.equals("millenaire:mock_banner_wall")) {
                           addCost(costs, ResourceLocation.parse("minecraft:white_banner"), count);
                        } else if (name.startsWith("millenaire:mock_")) {
                           LOGGER.warn("Unrecognized millenaire mock block '{}' treated as free (not added to cost)", name);
                        } else if (!name.startsWith("millenaire:painted_brick_") && !name.startsWith("millenaire:decorated_brick_")) {
                           String alias = COST_ALIASES.get(name);
                           if (alias != null) {
                              addCost(costs, ResourceLocation.parse(alias), count);
                           } else if (TILE_SLAB_HALF_COST.contains(name)) {
                              String base = name.substring(0, name.length() - "_slab".length());
                              addCost(costs, ResourceLocation.parse(base + "_slab_halves"), count);
                           } else {
                              String milBase = getMillenaireVariantBase(name);
                              if (milBase != null) {
                                 addCost(costs, ResourceLocation.parse(milBase), count);
                              } else {
                                 addCost(costs, ResourceLocation.parse(name), count);
                              }
                           }
                        } else {
                           boolean decorated = name.startsWith("millenaire:decorated_brick_");
                           String baseItem = decorated ? "millenaire:decorated_brick_white" : "millenaire:painted_brick_white";
                           addCost(costs, ResourceLocation.parse(baseItem), count);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean isFree(BuildingCostCalculator.PaletteEntry pe) {
      String name = pe.name();
      if (FREE_BLOCKS.contains(name)) {
         return true;
      } else if (FREE_MOCK_BLOCKS.contains(name)) {
         return true;
      } else if (FREE_MILLENAIRE_BLOCKS.contains(name)) {
         return true;
      } else {
         return name.contains("leaves") || name.contains("_sapling") ? true : name.contains("flower_pot") || name.contains("potted_");
      }
   }

   private static Integer getAnywoodCost(String name) {
      return switch (name) {
         case "minecraft:torch", "minecraft:wall_torch", "minecraft:soul_torch", "minecraft:soul_wall_torch" -> 1;
         case "minecraft:ladder" -> 2;
         case "minecraft:oak_pressure_plate" -> 2;
         case "minecraft:crafting_table" -> 4;
         case "minecraft:oak_fence_gate" -> 4;
         case "minecraft:oak_fence" -> 1;
         case "minecraft:oak_door" -> 6;
         case "minecraft:oak_trapdoor" -> 6;
         case "minecraft:oak_sign", "minecraft:oak_wall_sign", "minecraft:oak_hanging_sign" -> 7;
         case "minecraft:chest", "minecraft:trapped_chest" -> 8;
         default -> null;
      };
   }

   private static String detectWoodType(String name) {
      String path = name.startsWith("minecraft:") ? name.substring(10) : null;
      if (path == null) {
         return null;
      }

      if (path.startsWith("stripped_")) {
         String rest = path.substring(9);

         for (String wood : WOOD_TYPES.keySet()) {
            if (rest.startsWith(wood + "_log") || rest.startsWith(wood + "_wood")) {
               return wood;
            }
         }
      }

      for (String wood : WOOD_TYPES.keySet()) {
         if (path.startsWith(wood + "_")) {
            String suffix = path.substring(wood.length() + 1);
            if (isWoodBlockSuffix(suffix)) {
               return wood;
            }
         }
      }

      return null;
   }

   private static boolean isWoodBlockSuffix(String suffix) {
      return switch (suffix) {
         case "planks", "stairs", "slab", "door", "trapdoor", "fence", "fence_gate", "log", "wood", "sign", "wall_sign", "hanging_sign", "pressure_plate" -> true;
         default -> false;
      };
   }

   private static int getTypedWoodCost(String name, String woodType) {
      String path = name.startsWith("minecraft:") ? name.substring(10) : name;
      if (path.startsWith("stripped_")) {
         return 4;
      }

      String suffix = path.substring(woodType.length() + 1);

      return switch (suffix) {
         case "planks" -> 1;
         case "stairs", "slab", "fence", "pressure_plate" -> 1;
         case "door" -> 6;
         case "trapdoor" -> 6;
         case "fence_gate" -> 4;
         case "log", "wood" -> 4;
         case "sign", "wall_sign", "hanging_sign" -> 7;
         default -> 1;
      };
   }

   private static ResourceLocation getStoneCost(String name) {
      if (name.equals("minecraft:mossy_stone_bricks")
         || name.equals("minecraft:mossy_stone_brick_stairs")
         || name.equals("minecraft:mossy_stone_brick_slab")
         || name.equals("minecraft:mossy_stone_brick_wall")) {
         return ResourceLocation.parse("minecraft:mossy_stone_bricks");
      } else if (name.equals("minecraft:mossy_cobblestone")
         || name.equals("minecraft:mossy_cobblestone_stairs")
         || name.equals("minecraft:mossy_cobblestone_slab")
         || name.equals("minecraft:mossy_cobblestone_wall")) {
         return ResourceLocation.parse("minecraft:mossy_cobblestone");
      } else if (name.equals("minecraft:stone")
         || name.equals("minecraft:smooth_stone")
         || name.contains("stone_brick")
         || name.equals("minecraft:stone_stairs")
         || name.equals("minecraft:stone_slab")
         || name.equals("minecraft:smooth_stone_slab")
         || name.equals("minecraft:stone_pressure_plate")
         || name.equals("minecraft:stone_brick_stairs")
         || name.equals("minecraft:stone_brick_slab")
         || name.equals("minecraft:stone_brick_wall")) {
         return ResourceLocation.parse("minecraft:stone");
      } else if (name.startsWith("minecraft:") && name.contains("cobblestone")) {
         return ResourceLocation.parse("minecraft:cobblestone");
      } else if (name.startsWith("minecraft:") && name.contains("red_sandstone")) {
         return ResourceLocation.parse("minecraft:red_sandstone");
      } else if (name.startsWith("minecraft:") && name.contains("sandstone")) {
         return ResourceLocation.parse("minecraft:sandstone");
      } else if (name.contains("nether_brick")) {
         return ResourceLocation.parse("minecraft:nether_bricks");
      } else if (name.startsWith("minecraft:") && name.contains("brick") && !name.contains("nether")) {
         return ResourceLocation.parse("minecraft:bricks");
      } else {
         return name.contains("quartz") && !name.contains("ore") ? ResourceLocation.parse("minecraft:quartz_block") : null;
      }
   }

   private static String getMillenaireVariantBase(String name) {
      if (!name.startsWith("millenaire:")) {
         return null;
      }

      if (NOT_VARIANTS.contains(name)) {
         return null;
      }

      String override = VARIANT_OVERRIDES.get(name);
      if (override != null) {
         return override;
      }

      for (String suffix : VARIANT_SUFFIXES) {
         if (name.endsWith(suffix)) {
            return name.substring(0, name.length() - suffix.length());
         }
      }

      return null;
   }

   private static BuildingCostCalculator.MetalCost getMetalCost(String name) {
      ResourceLocation iron = ResourceLocation.parse("minecraft:iron_ingot");
      ResourceLocation gold = ResourceLocation.parse("minecraft:gold_ingot");

      return switch (name) {
         case "minecraft:iron_bars" -> new BuildingCostCalculator.MetalCost(iron, 1);
         case "minecraft:iron_door" -> new BuildingCostCalculator.MetalCost(iron, 6);
         case "minecraft:iron_block" -> new BuildingCostCalculator.MetalCost(iron, 9);
         case "minecraft:heavy_weighted_pressure_plate" -> new BuildingCostCalculator.MetalCost(iron, 2);
         case "minecraft:gold_block" -> new BuildingCostCalculator.MetalCost(gold, 9);
         case "minecraft:light_weighted_pressure_plate" -> new BuildingCostCalculator.MetalCost(gold, 2);
         case "minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil" -> new BuildingCostCalculator.MetalCost(iron, 31);
         default -> null;
      };
   }

   private static void postProcess(Map<ResourceLocation, Integer> costs) {
      ResourceLocation anywoodPlanksKey = ResourceLocation.parse("millenaire:anywood_planks");
      if (costs.containsKey(anywoodPlanksKey)) {
         int planks = costs.remove(anywoodPlanksKey);
         int logs = (int)Math.ceil(planks / 4.0);
         addCost(costs, AnywoodHelper.ANYWOOD_LOG, logs);
      }

      for (String wood : WOOD_TYPES.keySet()) {
         ResourceLocation planksKey = ResourceLocation.parse("minecraft:" + wood + "_planks");
         if (costs.containsKey(planksKey)) {
            int planks = costs.remove(planksKey);
            ResourceLocation logKey = ResourceLocation.parse("minecraft:" + wood + "_log");
            int logs = (int)Math.ceil(planks / 4.0);
            addCost(costs, logKey, logs);
         }
      }

      ResourceLocation paneKey = ResourceLocation.parse("minecraft:glass_pane");
      if (costs.containsKey(paneKey)) {
         int panes = costs.remove(paneKey);
         int glassBlocks = (int)Math.ceil(panes * 6.0 / 16.0);
         addCost(costs, ResourceLocation.parse("minecraft:glass"), glassBlocks);
      }

      for (String tileSlab : TILE_SLAB_HALF_COST) {
         String base = tileSlab.substring(0, tileSlab.length() - "_slab".length());
         ResourceLocation halvesKey = ResourceLocation.parse(base + "_slab_halves");
         if (costs.containsKey(halvesKey)) {
            int halves = costs.remove(halvesKey);
            int fullBlocks = (int)Math.max(Math.ceil(halves / 2.0), 1.0);
            addCost(costs, ResourceLocation.parse(base), fullBlocks);
         }
      }
   }

   private static void addCost(Map<ResourceLocation, Integer> costs, ResourceLocation item, int count) {
      if (count > 0) {
         costs.merge(item, count, Integer::sum);
      }
   }

   private record MetalCost(ResourceLocation item, int quantity) {
   }

   record PaletteEntry(String name, Map<String, String> properties) {
      String prop(String key) {
         return this.properties.getOrDefault(key, "");
      }
   }
}
