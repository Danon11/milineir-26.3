package org.millenaire.fabric.village;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

/**
 * What villages build with: the blocks of a plan cost raw materials villagers can gather themselves — wood
 * (any log), stone (cobblestone and natural stone), sand, clay and wool — in about the amounts a crafter would
 * use (four planks per log, a slab is half a block, and so on). Blocks made of anything else are crafted by the
 * builders from village stock and cost nothing extra, so a village never waits for goods it cannot produce.
 */
public final class BuildingMaterials {
    /** The raw materials, each represented by one item in costs. */
    public enum Raw {
        WOOD(Items.OAK_LOG, "wood"), STONE(Items.COBBLESTONE, "cobblestone"), SAND(Items.SAND, "sand"),
        CLAY(Items.CLAY_BALL, "clay"), WOOL(Items.WOOL.white(), "wool_white");
        public final Item item;
        /** Goods alias villagers carry it under. */
        public final String good;
        Raw(Item item, String good) { this.item = item; this.good = good; }
        public static Optional<Raw> of(Item item) {
            for (Raw raw : values()) if (raw.item == item) return Optional.of(raw);
            return Optional.empty();
        }
    }

    private static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
            "bamboo", "crimson", "warped", "pale_oak");
    private static final Set<String> STONES = Set.of("cobblestone", "stone", "stone_bricks", "mossy_cobblestone", "mossy_stone_bricks",
            "smooth_stone", "andesite", "diorite", "granite", "polished_andesite", "polished_diorite", "polished_granite",
            "cracked_stone_bricks", "chiseled_stone_bricks", "cobbled_deepslate", "deepslate", "tuff", "calcite");

    private BuildingMaterials() {}

    /** Raw material and amount for one placed block of this item, or empty when it costs nothing. */
    public static Optional<Map.Entry<Raw, Double>> rawCost(Item item) {
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        boolean wooden = WOODS.stream().anyMatch(path::startsWith) || path.contains("planks") || path.contains("timber") || path.contains("wood_deco");
        if (path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem") || path.endsWith("_hyphae")) return entry(Raw.WOOD, 1);
        if (wooden) {
            if (path.endsWith("_planks")) return entry(Raw.WOOD, 0.25);
            if (path.endsWith("_stairs")) return entry(Raw.WOOD, 0.375);
            if (path.endsWith("_slab")) return entry(Raw.WOOD, 0.125);
            if (path.endsWith("_fence") || path.endsWith("_fence_gate") || path.endsWith("_door")) return entry(Raw.WOOD, 0.5);
            if (path.endsWith("_trapdoor")) return entry(Raw.WOOD, 0.75);
            if (path.endsWith("_sign") || path.endsWith("_button") || path.endsWith("_pressure_plate")) return entry(Raw.WOOD, 0.25);
            return entry(Raw.WOOD, 0.5); // timber frames and other Millénaire wooden blocks
        }
        if (Set.of("chest", "barrel", "crafting_table", "bookshelf", "lectern", "composter").contains(path)) return entry(Raw.WOOD, path.equals("bookshelf") ? 1.5 : 2);
        if (path.equals("ladder") || path.equals("stick")) return entry(Raw.WOOD, 0.15);
        if (path.endsWith("_bed")) return entry(Raw.WOOD, 0.75);
        if (STONES.contains(path)) return entry(Raw.STONE, 1);
        boolean stony = STONES.stream().anyMatch(stone -> path.startsWith(stone + "_")) || path.contains("stone_deco") || path.contains("cobblestone");
        if (stony) {
            if (path.endsWith("_stairs")) return entry(Raw.STONE, 0.75);
            if (path.endsWith("_slab")) return entry(Raw.STONE, 0.5);
            return entry(Raw.STONE, 1);
        }
        if (path.equals("furnace") || path.equals("smoker")) return entry(Raw.STONE, 8);
        if (path.contains("sandstone")) return entry(Raw.SAND, path.endsWith("_slab") ? 2 : 4);
        if (path.endsWith("glass_pane")) return entry(Raw.SAND, 0.375);
        if (path.endsWith("glass")) return entry(Raw.SAND, 1);
        if (path.contains("brick") || path.contains("terracotta") || path.contains("tile") || path.contains("mud")) {
            if (path.endsWith("_slab")) return entry(Raw.CLAY, 2);
            if (path.endsWith("_stairs")) return entry(Raw.CLAY, 3);
            return entry(Raw.CLAY, 4);
        }
        if (path.endsWith("_wool")) return entry(Raw.WOOL, 1);
        if (path.endsWith("_carpet")) return entry(Raw.WOOL, 0.67);
        return Optional.empty();
    }

    private static Optional<Map.Entry<Raw, Double>> entry(Raw raw, double amount) { return Optional.of(Map.entry(raw, amount)); }

    /** Raw cost of a list of placed block items, rounded up per material. */
    public static Map<Item, Integer> cost(Map<Item, Integer> blocks) {
        Map<Raw, Double> total = new EnumMap<>(Raw.class);
        blocks.forEach((item, count) -> rawCost(item).ifPresent(e -> total.merge(e.getKey(), e.getValue() * count, Double::sum)));
        Map<Item, Integer> result = new LinkedHashMap<>();
        total.forEach((raw, amount) -> { if (amount > 0) result.put(raw.item, (int) Math.ceil(amount)); });
        return result;
    }

    /** How much of a raw material a stack provides. */
    public static int provides(Raw raw, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        return switch (raw) {
            case WOOD -> stack.is(ItemTags.LOGS) ? stack.getCount() : 0;
            case STONE -> {
                String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
                yield Set.of("cobblestone", "stone", "cobbled_deepslate", "andesite", "diorite", "granite", "tuff").contains(path) ? stack.getCount() : 0;
            }
            case SAND -> stack.is(Items.SAND) || stack.is(Items.RED_SAND) ? stack.getCount() : 0;
            case CLAY -> stack.is(Items.CLAY_BALL) ? stack.getCount() : stack.is(Items.CLAY) ? stack.getCount() * 4 : 0;
            case WOOL -> stack.is(ItemTags.WOOL) ? stack.getCount() : 0;
        };
    }

    /** Units of material one item of the stack is worth (clay blocks hold four clay). */
    static int unit(Raw raw, ItemStack stack) { return raw == Raw.CLAY && stack.is(Items.CLAY) ? 4 : 1; }
}
