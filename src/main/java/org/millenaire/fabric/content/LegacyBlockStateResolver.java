package org.millenaire.fabric.content;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.millenaire.fabric.LegacyContentRegistry;

import java.util.*;

/** Strict conversion: unsupported states are reported before any world mutation. */
public final class LegacyBlockStateResolver {
    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
    private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private LegacyBlockStateResolver() {}

    public static BlockState resolve(LegacyPalette.Point point) {
        if (point.special()) throw new IllegalArgumentException("Special point requires building logic: " + point.label());
        String id = point.block().toLowerCase(Locale.ROOT);
        Map<String, String> values = new LinkedHashMap<>();
        Integer meta = null;
        String text = point.state().trim();
        if (text.matches("\\d+")) meta = Integer.parseInt(text);
        else if (!text.isEmpty()) {
            for (String pair : text.split(",")) {
                String[] field = pair.trim().split("=", 2);
                if (field.length != 2) throw new IllegalArgumentException("Invalid block state: " + text);
                values.put(field[0].trim(), field[1].trim());
            }
        }
        String name = id.substring(id.indexOf(':') + 1);
        if (id.startsWith("minecraft:")) {
            String variant = values.remove("variant");
            if (name.equals("torch") && values.containsKey("facing")) {
                if (values.get("facing").equals("up")) values.remove("facing");
                else id = "minecraft:wall_torch";
            } else if (name.equals("portal")) {
                id = "minecraft:nether_portal";
                if (meta != null) {
                    if (meta < 0 || meta > 2) throw new IllegalArgumentException("Invalid portal metadata: " + meta);
                    values.put("axis", meta == 2 ? "z" : "x");
                    meta = null;
                }
            } else if (name.equals("dispenser") && meta != null) {
                if (meta > 15 || (meta & 7) > 5) throw new IllegalArgumentException("Invalid dispenser metadata: " + meta);
                values.put("facing", new String[]{"down", "up", "north", "south", "west", "east"}[meta & 7]);
                values.put("triggered", Boolean.toString((meta & 8) != 0));
                meta = null;
            } else if (name.equals("fence_gate") && meta != null) {
                id = "minecraft:oak_fence_gate";
                if (meta > 15) throw new IllegalArgumentException("Invalid fence gate metadata: " + meta);
                values.put("facing", new String[]{"south", "west", "north", "east"}[meta & 3]);
                values.put("open", Boolean.toString((meta & 4) != 0));
                values.put("powered", Boolean.toString((meta & 8) != 0));
                meta = null;
            } else if (name.equals("vine") && meta != null) {
                if (meta > 15) throw new IllegalArgumentException("Invalid vine metadata: " + meta);
                values.put("south", Boolean.toString((meta & 1) != 0));
                values.put("west", Boolean.toString((meta & 2) != 0));
                values.put("north", Boolean.toString((meta & 4) != 0));
                values.put("east", Boolean.toString((meta & 8) != 0));
                meta = null;
            } else if (name.equals("redstone_torch")) {
                String facing = values.remove("facing");
                if (meta != null) {
                    if (meta > 5) throw new IllegalArgumentException("Invalid redstone torch metadata: " + meta);
                    facing = new String[]{"up", "east", "west", "south", "north", "up"}[meta];
                    meta = null;
                }
                if (facing != null && !facing.equals("up")) {
                    id = "minecraft:redstone_wall_torch";
                    values.put("facing", facing);
                }
            } else if (name.equals("stone_button") || name.equals("wooden_button")) {
                if (name.equals("wooden_button")) id = "minecraft:oak_button";
                if (meta != null) {
                    if (meta > 15) throw new IllegalArgumentException("Invalid button metadata: " + meta);
                    int direction = meta & 7;
                    values.put("face", direction == 0 ? "ceiling" : direction >= 5 ? "floor" : "wall");
                    values.put("facing", switch (direction) { case 1 -> "east"; case 2 -> "west"; case 3 -> "south"; default -> "north"; });
                    values.put("powered", Boolean.toString((meta & 8) != 0));
                    meta = null;
                } else if (values.containsKey("facing")) {
                    String facing = values.get("facing");
                    if (facing.equals("up") || facing.equals("down")) {
                        values.put("face", facing.equals("up") ? "floor" : "ceiling");
                        values.put("facing", "north");
                    } else values.putIfAbsent("face", "wall");
                }
            } else if (name.equals("bed")) {
                // The pre-colour palette bed used the default red bed tile entity.
                id = "minecraft:red_bed";
                if (meta != null) {
                    if (meta > 15) throw new IllegalArgumentException("Invalid bed metadata: " + meta);
                    values.put("facing", switch (meta & 3) { case 0 -> "south"; case 1 -> "west"; case 2 -> "north"; default -> "east"; });
                    values.put("part", (meta & 8) != 0 ? "head" : "foot");
                    values.put("occupied", Boolean.toString((meta & 4) != 0));
                    meta = null;
                }
            } else if (name.equals("snow")) {
                // Forge's snow is the full cube; its layered block was snow_layer.
                id = "minecraft:snow_block";
                if (meta != null && meta != 0) throw new IllegalArgumentException("Invalid snow block metadata: " + meta);
                meta = null;
            } else if (name.equals("snow_layer")) {
                id = "minecraft:snow";
                if (meta != null) {
                    if (meta > 7) throw new IllegalArgumentException("Invalid snow layer metadata: " + meta);
                    values.put("layers", Integer.toString(meta + 1));
                    meta = null;
                }
            } else if (name.equals("flower_pot") && meta != null) {
                String[] contents = {"", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy", "dandelion", "oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling", "acacia_sapling", "dark_oak_sapling", "red_mushroom", "brown_mushroom", "dead_bush", "fern", "cactus"};
                if (meta != 0 && meta != 255) id = "minecraft:potted_" + contents[index(meta, contents.length)];
                meta = null;
            } else if (name.equals("dirt")) {
                String[] dirt = {"dirt", "coarse_dirt", "podzol"};
                String selected = variant != null ? variant : dirt[index(meta == null ? 0 : meta, dirt.length)];
                if (!Arrays.asList(dirt).contains(selected)) throw new IllegalArgumentException("Invalid dirt variant: " + selected);
                id = "minecraft:" + selected;
                meta = null;
            } else if (name.equals("sandstone") || name.equals("red_sandstone")) {
                String selected = variant != null ? variant : new String[]{name, "chiseled_" + name, "smooth_" + name}[index(meta == null ? 0 : meta, 3)];
                if (!Set.of(name, "default", "chiseled_" + name, "smooth_" + name).contains(selected))
                    throw new IllegalArgumentException("Invalid sandstone variant: " + selected);
                id = "minecraft:" + (selected.startsWith("chiseled_") ? "chiseled_" : selected.startsWith("smooth_") ? "cut_" : "") + name;
                meta = null;
            } else if (name.equals("cauldron")) {
                String declared = values.remove("level");
                int level = declared != null ? Integer.parseInt(declared) : meta == null ? 0 : meta;
                if (level < 0 || level > 3) throw new IllegalArgumentException("Invalid cauldron level: " + level);
                if (level > 0) { id = "minecraft:water_cauldron"; values.put("level", Integer.toString(level)); }
                meta = null;
            } else if (name.equals("nether_wart")) {
                if (meta != null) {
                    if (meta > 3) throw new IllegalArgumentException("Invalid nether wart age: " + meta);
                    values.put("age", meta.toString());
                    meta = null;
                }
            } else if (name.equals("purpur_slab")) {
                if (meta != null) {
                    if (meta != 0 && meta != 8) throw new IllegalArgumentException("Invalid purpur slab metadata: " + meta);
                    values.put("type", meta == 8 ? "top" : "bottom");
                    meta = null;
                }
            } else if (name.equals("sand") || name.equals("sponge") || name.equals("cobblestone_wall")) {
                if (meta != null && meta > 1) throw new IllegalArgumentException("Invalid material metadata: " + meta);
                String selected = variant != null ? variant : meta != null && meta == 1 ? switch (name) {
                    case "sand" -> "red_sand"; case "sponge" -> "wet_sponge"; default -> "mossy_cobblestone";
                } : name.equals("cobblestone_wall") ? "cobblestone" : name;
                Set<String> allowed = switch (name) {
                    case "sand" -> Set.of("sand", "red_sand"); case "sponge" -> Set.of("sponge", "wet_sponge");
                    default -> Set.of("cobblestone", "mossy_cobblestone");
                };
                if (!allowed.contains(selected)) throw new IllegalArgumentException("Invalid material variant: " + selected);
                id = "minecraft:" + selected + (name.equals("cobblestone_wall") ? "_wall" : "");
                meta = null;
            } else if (name.equals("stone_slab2") || name.equals("double_stone_slab2")) {
                if (variant != null && !variant.equals("red_sandstone")) throw new IllegalArgumentException("Invalid slab variant: " + variant);
                if (meta != null && meta != 0 && meta != 8) throw new IllegalArgumentException("Invalid red sandstone slab metadata: " + meta);
                id = "minecraft:red_sandstone_slab";
                String half = values.remove("half");
                if (half != null && !Set.of("top", "bottom").contains(half)) throw new IllegalArgumentException("Invalid slab half: " + half);
                values.put("type", name.startsWith("double_") ? "double" : "top".equals(half) || (meta != null && meta == 8) ? "top" : "bottom");
                meta = null;
            } else if (name.equals("pumpkin") || name.equals("lit_pumpkin")) {
                id = name.equals("pumpkin") ? "minecraft:carved_pumpkin" : "minecraft:jack_o_lantern";
                if (meta != null) {
                    values.put("facing", new String[]{"south", "west", "north", "east"}[index(meta, 4)]);
                    meta = null;
                }
            } else if (name.equals("lit_redstone_lamp")) {
                id = "minecraft:redstone_lamp";
                if (meta != null && meta != 0) throw new IllegalArgumentException("Invalid lit lamp metadata: " + meta);
                values.put("lit", "true");
                meta = null;
            } else if (name.equals("sapling")) {
                String type = values.remove("type");
                if (meta != null && meta > 15) throw new IllegalArgumentException("Invalid sapling metadata: " + meta);
                String wood = type != null ? type : variant != null ? variant : WOODS[index(meta == null ? 0 : meta & 7, WOODS.length)];
                if (!Arrays.asList(WOODS).contains(wood)) throw new IllegalArgumentException("Invalid sapling type: " + wood);
                id = "minecraft:" + wood + "_sapling";
                if (meta != null) values.put("stage", Integer.toString((meta & 8) != 0 ? 1 : 0));
                meta = null;
            } else if (name.equals("leaves") || name.equals("leaves2")) {
                if (meta != null && meta > 15) throw new IllegalArgumentException("Invalid leaves metadata: " + meta);
                int offset = name.equals("leaves2") ? 4 : 0;
                String wood = variant != null ? variant : WOODS[offset + index(meta == null ? 0 : meta & 3, offset == 0 ? 4 : 2)];
                if (!Arrays.asList(WOODS).subList(offset, offset == 0 ? 4 : 6).contains(wood))
                    throw new IllegalArgumentException("Invalid leaves variant: " + wood);
                id = "minecraft:" + wood + "_leaves";
                String decayable = values.remove("decayable");
                if (decayable != null) values.put("persistent", switch (decayable) {
                    case "true" -> "false"; case "false" -> "true";
                    default -> throw new IllegalArgumentException("Invalid decayable value: " + decayable);
                });
                else if (meta != null) values.put("persistent", Boolean.toString((meta & 4) != 0));
                String checkDecay = values.remove("check_decay");
                if (checkDecay != null && !Set.of("true", "false").contains(checkDecay))
                    throw new IllegalArgumentException("Invalid check_decay value: " + checkDecay);
                // Modern leaves recompute distance; the old pending-check flag is not stored.
                meta = null;
            } else if (Set.of("red_flower", "yellow_flower", "tallgrass").contains(name)) {
                String type = values.remove("type");
                String[] plants = switch (name) {
                    case "red_flower" -> new String[]{"poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy"};
                    case "yellow_flower" -> new String[]{"dandelion"};
                    default -> new String[]{"dead_bush", "short_grass", "fern"};
                };
                String selected = type != null ? type : variant != null ? variant : plants[index(meta == null ? 0 : meta, plants.length)];
                if (selected.equals("houstonia")) selected = "azure_bluet";
                if (selected.equals("grass")) selected = "short_grass";
                if (!Arrays.asList(plants).contains(selected)) throw new IllegalArgumentException("Invalid plant variant: " + selected);
                id = "minecraft:" + selected;
                meta = null;
            } else if (name.equals("double_plant")) {
                String[] plants = {"sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony"};
                String selected = variant == null ? plants[index(meta == null ? 0 : meta, plants.length)] : switch (variant) {
                    case "sunflower" -> "sunflower"; case "syringa" -> "lilac";
                    case "double_grass" -> "tall_grass"; case "double_fern" -> "large_fern";
                    case "double_rose" -> "rose_bush"; case "paeonia" -> "peony";
                    default -> throw new IllegalArgumentException("Unsupported double plant variant: " + variant);
                };
                id = "minecraft:" + selected;
                // Legacy facing is unused by these modern plants.
                values.remove("facing");
                meta = null;
            } else if (name.equals("planks") || name.equals("log") || name.equals("log2") || name.equals("wooden_slab")) {
                String wood = variant == null ? WOODS[index(meta == null ? 0 : meta & (name.equals("log2") ? 1 : name.equals("log") ? 3 : 7), name.equals("log2") ? 2 : name.equals("log") ? 4 : WOODS.length) + (name.equals("log2") ? 4 : 0)] : variant;
                String suffix = name.equals("planks") ? "_planks" : name.equals("wooden_slab") ? "_slab" : "_log";
                id = "minecraft:" + wood + suffix;
                if (name.equals("wooden_slab")) values.put("type", "top".equals(values.remove("half")) || (meta != null && (meta & 8) != 0) ? "top" : "bottom");
                if (name.startsWith("log") && meta != null) {
                    int axis = meta & 12;
                    if (axis == 12) id = "minecraft:" + wood + "_wood";
                    else values.put("axis", axis == 4 ? "x" : axis == 8 ? "z" : "y");
                }
                meta = null;
            } else if (Set.of("wool", "carpet", "concrete", "stained_glass", "stained_glass_pane", "stained_hardened_clay").contains(name)) {
                String color = values.remove("color");
                if (color == null) color = COLORS[index(meta == null ? 0 : meta, 16)];
                if (color.equals("silver")) color = "light_gray";
                id = "minecraft:" + color + "_" + (name.equals("stained_hardened_clay") ? "terracotta" : name);
                meta = null;
            } else if (name.equals("stone")) {
                String[] stones = {"stone", "granite", "polished_granite", "diorite", "polished_diorite", "andesite", "polished_andesite"};
                String selected = variant == null ? stones[index(meta == null ? 0 : meta, stones.length)] : variant.replace("smooth_", "polished_");
                id = "minecraft:" + selected;
                meta = null;
            } else if (name.equals("stonebrick")) {
                String[] variants = {"stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks"};
                id = "minecraft:" + variants[index(meta == null ? 0 : meta, 4)];
                meta = null;
            } else if (name.equals("stone_slab") || name.equals("double_stone_slab")) {
                String[] slabs = {"smooth_stone", "sandstone", "petrified_oak", "cobblestone", "brick", "stone_brick", "nether_brick", "quartz"};
                String selected = variant == null ? slabs[index(meta == null ? 0 : meta & 7, 8)] : variant;
                if (selected.equals("stone")) selected = "smooth_stone";
                id = "minecraft:" + selected + "_slab";
                values.put("type", name.startsWith("double_") ? "double" : "top".equals(values.remove("half")) || (meta != null && (meta & 8) != 0) ? "top" : "bottom");
                meta = null;
            } else if (variant != null) {
                if (name.equals("quartz_block")) {
                    id = "minecraft:" + (variant.equals("chiseled") ? "chiseled_quartz_block" : variant.startsWith("lines") ? "quartz_pillar" : "quartz_block");
                    if (variant.startsWith("lines")) values.put("axis", variant.endsWith("x") ? "x" : variant.endsWith("z") ? "z" : "y");
                } else throw new IllegalArgumentException("Unsupported legacy variant: " + point.block() + " " + variant);
            }
            id = Map.ofEntries(Map.entry("minecraft:brick_block", "minecraft:bricks"), Map.entry("minecraft:grass", "minecraft:grass_block"),
                    Map.entry("minecraft:flowing_water", "minecraft:water"), Map.entry("minecraft:flowing_lava", "minecraft:lava"),
                    Map.entry("minecraft:waterlily", "minecraft:lily_pad"), Map.entry("minecraft:stone_stairs", "minecraft:cobblestone_stairs"),
                    Map.entry("minecraft:stone_brick_stairs", "minecraft:stone_brick_stairs"), Map.entry("minecraft:wooden_door", "minecraft:oak_door"),
                    Map.entry("minecraft:trapdoor", "minecraft:oak_trapdoor"), Map.entry("minecraft:fence", "minecraft:oak_fence"),
                    Map.entry("minecraft:fence_gate", "minecraft:oak_fence_gate"), Map.entry("minecraft:reeds", "minecraft:sugar_cane"),
                    Map.entry("minecraft:snow_layer", "minecraft:snow"), Map.entry("minecraft:hardened_clay", "minecraft:terracotta"),
                    Map.entry("minecraft:nether_brick", "minecraft:nether_bricks"), Map.entry("minecraft:melon_block", "minecraft:melon"),
                    Map.entry("minecraft:noteblock", "minecraft:note_block"), Map.entry("minecraft:wooden_pressure_plate", "minecraft:oak_pressure_plate"),
                    Map.entry("minecraft:silver_glazed_terracotta", "minecraft:light_gray_glazed_terracotta")).getOrDefault(id, id);
            if (id.equals("minecraft:standing_sign")) id = "minecraft:oak_sign";
            if (id.equals("minecraft:wall_sign")) id = "minecraft:oak_wall_sign";
            if (meta != null && meta != 0) {
                if (id.equals("minecraft:snow")) values.put("layers", Integer.toString(meta + 1));
                else if (id.equals("minecraft:water") || id.equals("minecraft:lava")) values.put("level", meta.toString());
                else throw new IllegalArgumentException("Unsupported numeric metadata: " + point.block() + "=" + meta);
            }
        } else if (id.startsWith("millenaire:")) {
            if (meta != null && Set.of("panel", "locked_chest", "mainchest").contains(name)) {
                values.put("facing", switch (meta) { case 0, 1, 2 -> "north"; case 3 -> "south"; case 4 -> "west"; case 5 -> "east";
                    default -> throw new IllegalArgumentException("Invalid storage facing metadata: " + meta); });
                meta = null;
            }
            int number = meta == null ? 0 : meta;
            if (Set.of("pathdirt", "pathgravel", "pathslabs", "pathsandstone", "pathgravelslabs",
                    "pathdirt_slab", "pathgravel_slab", "pathslabs_slab", "pathsandstone_slab", "pathgravelslabs_slab").contains(name)) {
                if ((number & ~(name.endsWith("_slab") ? 9 : 1)) != 0) throw new IllegalArgumentException("Invalid path metadata: " + number);
                values.put("stable", Boolean.toString((number & 1) != 0));
                if (name.endsWith("_slab")) values.put("type", (number & 8) != 0 ? "top" : "bottom");
                number = 0;
                meta = null;
            }
            // Snow paths store the same stability bit as other paths; leaves store decay flags in bits 4 and 8.
            if ((name.equals("pathsnow") || name.equals("pathsnow_slab")) && (number & ~9) == 0) { number = 0; meta = null; }
            if (name.startsWith("leaves_") && (number & ~12) == 0) { number = 0; meta = null; }
            final int mappedMetadata = number;
            String legacyId = id;
            var matches = LegacyContentRegistry.origins().values().stream().filter(origin -> origin.kind().equals("block")
                    && origin.legacyId().equalsIgnoreCase(legacyId) && origin.metadata() == mappedMetadata).toList();
            if (matches.size() == 1) id = "millenaire:" + matches.getFirst().name();
            else if (number != 0) throw new IllegalArgumentException("No unique migrated block for " + legacyId + "=" + number);
            String variant = values.remove("variant");
            if (variant != null) id = variantBlock(legacyId, variant);
        }
        Identifier key = Identifier.parse(id);
        if (!BuiltInRegistries.BLOCK.containsKey(key)) throw new IllegalArgumentException("Unregistered migrated block: " + id);
        BlockState state = BuiltInRegistries.BLOCK.getValue(key).defaultBlockState();
        if (meta != null && state.getBlock().getStateDefinition().getProperty("facing") != null && !values.containsKey("facing"))
            throw new IllegalArgumentException("Numeric orientation is not converted for " + point.block());
        // 1.12 slabs used half=bottom|top; modern slabs call it type.
        if (values.containsKey("half") && state.getBlock().getStateDefinition().getProperty("half") == null
                && state.getBlock().getStateDefinition().getProperty("type") != null) values.put("type", values.remove("half"));
        for (var value : values.entrySet()) {
            Property<?> property = state.getBlock().getStateDefinition().getProperty(value.getKey());
            if (property == null) throw new IllegalArgumentException("Unsupported property " + value.getKey() + " on " + id);
            state = apply(state, property, value.getValue());
        }
        return state;
    }
    /**
     * Legacy blocks with a {@code variant} property were split into one block per variant. Picks the block named
     * after the variant, then {@code <block>_<variant>}, then the longest variant block name the value starts with
     * (mosaic colours share one mosaic block). Single-model slabs keep their only block.
     */
    static String variantBlock(String legacyId, String variant) {
        String value = variant.toLowerCase(java.util.Locale.ROOT);
        String base = legacyId.substring(legacyId.indexOf(':') + 1).toLowerCase(java.util.Locale.ROOT);
        var origins = LegacyContentRegistry.origins().values().stream()
                .filter(origin -> origin.kind().equals("block") && origin.legacyId().equalsIgnoreCase(legacyId)).toList();
        for (String candidate : List.of(value, base + "_" + value))
            if (origins.stream().anyMatch(origin -> origin.name().equals(candidate)) || registered(candidate) && origins.isEmpty()) return "millenaire:" + candidate;
        var prefix = origins.stream().filter(origin -> value.startsWith(origin.name()))
                .max(java.util.Comparator.comparingInt(origin -> origin.name().length()));
        if (prefix.isPresent()) return "millenaire:" + prefix.get().name();
        if (base.contains("slab") && registered(base)) return "millenaire:" + base;
        throw new IllegalArgumentException("No migrated block for " + legacyId + " variant=" + variant);
    }

    private static boolean registered(String name) {
        return BuiltInRegistries.BLOCK.containsKey(Identifier.fromNamespaceAndPath("millenaire", name));
    }

    private static int index(int value, int size) {
        if (value < 0 || value >= size) throw new IllegalArgumentException("Invalid legacy variant index: " + value);
        return value;
    }
    private static <T extends Comparable<T>> BlockState apply(BlockState state, Property<T> property, String value) {
        return state.setValue(property, property.getValue(value).orElseThrow(() -> new IllegalArgumentException("Invalid " + property.getName() + "=" + value)));
    }
}
