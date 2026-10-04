package org.millenaire.fabric.economy;

import com.google.gson.JsonParser;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Converts old item IDs and metadata without dropping variant, damage or potion information. */
public final class LegacyItemResolver {
    private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
    private static final Map<String, String> MIGRATED = migratedIds();
    private static final Map<String, String> RENAMED = Map.ofEntries(
            Map.entry("brick_block", "bricks"), Map.entry("fence", "oak_fence"), Map.entry("sign", "oak_sign"),
            Map.entry("reeds", "sugar_cane"), Map.entry("melon", "melon_slice"), Map.entry("yellow_flower", "dandelion"),
            Map.entry("noteblock", "note_block"), Map.entry("snow", "snow_block"));

    public record Target(Identifier id, int damage, Optional<Identifier> potion) {
        public Target { Objects.requireNonNull(id); Objects.requireNonNull(potion); if (damage < 0) throw new IllegalArgumentException("Negative item damage"); }
        public ItemStack stack() {
            if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("Unregistered migrated item: " + id);
            var item = BuiltInRegistries.ITEM.getValue(id);
            if (item == Items.AIR) throw new IllegalArgumentException("Good has no obtainable item: " + id);
            ItemStack stack = new ItemStack(item);
            if (damage > 0) {
                if (!stack.isDamageableItem() || damage >= stack.getMaxDamage())
                    throw new IllegalArgumentException("Unsupported item damage: " + id + "=" + damage);
                stack.setDamageValue(damage);
            }
            if (potion.isPresent()) {
                var holder = BuiltInRegistries.POTION.get(potion.get())
                        .orElseThrow(() -> new IllegalArgumentException("Unknown potion: " + potion.get()));
                stack.set(DataComponents.POTION_CONTENTS, new PotionContents(holder));
            }
            return stack;
        }
    }
    private LegacyItemResolver() {}

    public static Target resolve(LegacyGoodsCatalog.Good good) {
        return translate(good.legacyId(), good.metadata());
    }
    public static Target translate(String legacyId, int metadata) {
        if (metadata < 0) throw new IllegalArgumentException("Wildcard metadata is not a concrete inventory item: " + legacyId);
        String id = legacyId.trim().toLowerCase(Locale.ROOT);
        if (!id.contains(":")) id = "minecraft:" + id;
        String mapped = MIGRATED.get(id + "#" + metadata);
        if (mapped != null) return target(mapped, 0);
        if (id.equals("millenaire:paint_bucket_white") && metadata == 0) return target("millenaire:paintbucketwhite", 0);
        if (!id.startsWith("minecraft:")) {
            if (metadata != 0) throw new IllegalArgumentException("Unconverted item metadata: " + id + "=" + metadata);
            return target(id, 0);
        }
        String name = id.substring(10);
        String converted = switch (name) {
            case "planks" -> variant(WOODS, metadata) + "_planks";
            case "sapling" -> variant(WOODS, metadata) + "_sapling";
            case "log" -> variant(Arrays.copyOf(WOODS, 4), metadata) + "_log";
            case "log2" -> variant(new String[]{"acacia", "dark_oak"}, metadata) + "_log";
            case "wool", "carpet", "stained_glass", "stained_glass_pane", "stained_hardened_clay", "concrete", "concrete_powder", "bed" ->
                    variant(COLORS, metadata) + "_" + (name.equals("stained_hardened_clay") ? "terracotta" : name);
            case "banner" -> variant(COLORS, 15 - metadata) + "_banner";
            case "dye" -> variant(new String[]{"ink_sac", "red_dye", "green_dye", "cocoa_beans", "lapis_lazuli", "purple_dye", "cyan_dye",
                    "light_gray_dye", "gray_dye", "pink_dye", "lime_dye", "yellow_dye", "light_blue_dye", "magenta_dye", "orange_dye", "bone_meal"}, metadata);
            case "coal" -> variant(new String[]{"coal", "charcoal"}, metadata);
            case "dirt" -> variant(new String[]{"dirt", "coarse_dirt", "podzol"}, metadata);
            case "stone" -> variant(new String[]{"stone", "granite", "polished_granite", "diorite", "polished_diorite", "andesite", "polished_andesite"}, metadata);
            case "stonebrick" -> variant(new String[]{"stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks"}, metadata);
            case "sandstone", "red_sandstone" -> variant(new String[]{name, "chiseled_" + name, "cut_" + name}, metadata);
            case "sand" -> variant(new String[]{"sand", "red_sand"}, metadata);
            case "quartz_block" -> variant(new String[]{"quartz_block", "chiseled_quartz_block", "quartz_pillar", "quartz_pillar", "quartz_pillar"}, metadata);
            case "fish" -> variant(new String[]{"cod", "salmon", "tropical_fish", "pufferfish"}, metadata);
            case "cooked_fish" -> variant(new String[]{"cooked_cod", "cooked_salmon"}, metadata);
            case "red_flower" -> variant(new String[]{"poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy"}, metadata);
            case "sponge" -> variant(new String[]{"sponge", "wet_sponge"}, metadata);
            case "cobblestone_wall" -> variant(new String[]{"cobblestone_wall", "mossy_cobblestone_wall"}, metadata);
            case "anvil" -> variant(new String[]{"anvil", "chipped_anvil", "damaged_anvil"}, metadata);
            case "potion" -> {
                if (metadata != 0 && metadata != 16) throw new IllegalArgumentException("Unsupported legacy potion metadata: " + metadata);
                yield "potion";
            }
            default -> null;
        };
        if (name.equals("potion")) return new Target(Identifier.withDefaultNamespace("potion"), 0,
                Optional.of(Identifier.withDefaultNamespace(metadata == 16 ? "awkward" : "water")));
        return converted == null ? target("minecraft:" + RENAMED.getOrDefault(name, name), metadata)
                : target("minecraft:" + converted, 0);
    }
    private static String variant(String[] variants, int metadata) {
        if (metadata < 0 || metadata >= variants.length) throw new IllegalArgumentException("Invalid item variant metadata: " + metadata);
        return variants[metadata];
    }
    private static Target target(String id, int damage) { return new Target(Identifier.parse(id), damage, Optional.empty()); }
    private static Map<String, String> migratedIds() {
        Map<String, String> result = new HashMap<>();
        var stream = LegacyItemResolver.class.getResourceAsStream("/data/millenaire/legacy_id_map.json");
        if (stream == null) throw new IllegalStateException("Missing legacy item ID map");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            for (var value : JsonParser.parseReader(reader).getAsJsonArray()) {
                var entry = value.getAsJsonObject();
                String key = entry.get("legacy_id").getAsString().toLowerCase(Locale.ROOT) + "#" + entry.get("metadata").getAsString();
                String target = "millenaire:" + entry.get("name").getAsString();
                String previous = result.putIfAbsent(key, target);
                if (previous != null && !previous.equals(target)) throw new IllegalStateException("Ambiguous legacy item mapping: " + key);
            }
        } catch (java.io.IOException exception) { throw new IllegalStateException("Could not read legacy item ID map", exception); }
        return Map.copyOf(result);
    }
}
