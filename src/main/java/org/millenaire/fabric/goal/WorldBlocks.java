package org.millenaire.fabric.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.*;

/** Block lookups for resource goals: legacy block-state strings, log species and searches near a building. */
public final class WorldBlocks {
    /** A block with optional property constraints, parsed from {@code namespace:block;key=value,key=value}. */
    public record Pattern(Block block, Map<String, String> properties) {
        public boolean matches(BlockState state) {
            if (!state.is(block)) return false;
            for (var entry : properties.entrySet()) {
                Property<?> property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
                if (property != null && !state.getValue(property).toString().equalsIgnoreCase(entry.getValue())
                        && !(state.getValue(property) instanceof net.minecraft.util.StringRepresentable named
                        && named.getSerializedName().equalsIgnoreCase(entry.getValue()))) return false;
            }
            return true;
        }

        /** The block's default state with every recognised property applied; unknown legacy keys are ignored. */
        public BlockState state() {
            BlockState state = block.defaultBlockState();
            for (var entry : properties.entrySet()) {
                Property<?> property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
                if (property != null) state = apply(state, property, entry.getValue());
            }
            return state;
        }

        private static <T extends Comparable<T>> BlockState apply(BlockState state, Property<T> property, String value) {
            return property.getValue(value.toLowerCase(Locale.ROOT)).map(v -> state.setValue(property, v)).orElse(state);
        }
    }

    private WorldBlocks() {}

    public static Optional<Pattern> pattern(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String[] parts = text.trim().split(";", 2);
        String name = parts[0].trim().toLowerCase(Locale.ROOT);
        Identifier id = name.contains(":") ? Identifier.tryParse(name) : Identifier.withDefaultNamespace(name);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return Optional.empty();
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        if (block == Blocks.AIR) return Optional.empty();
        Map<String, String> properties = new LinkedHashMap<>();
        if (parts.length == 2)
            for (String pair : parts[1].split(",")) {
                int equals = pair.indexOf('=');
                if (equals > 0) properties.put(pair.substring(0, equals).trim().toLowerCase(Locale.ROOT), pair.substring(equals + 1).trim());
            }
        return Optional.of(new Pattern(block, properties));
    }

    /** Nearest matching block within a cube around the centre, scanning loaded blocks only. */
    public static Optional<BlockPos> nearest(ServerLevel level, BlockPos centre, int radius, int height, java.util.function.Predicate<BlockState> test) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -height; dy <= height; dy++)
            for (int dx = -radius; dx <= radius; dx++)
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (!level.isLoaded(cursor) || !test.test(level.getBlockState(cursor))) continue;
                    double distance = cursor.distSqr(centre);
                    if (distance < bestDistance) { bestDistance = distance; best = cursor.immutable(); }
                }
        return Optional.ofNullable(best);
    }

    /** Goods alias for a vanilla log, following itemlist.txt. */
    public static Optional<String> logGood(BlockState state) {
        if (!state.is(BlockTags.LOGS)) return Optional.empty();
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (path.startsWith("stripped_") || path.endsWith("_wood") || path.endsWith("_hyphae")) return Optional.empty();
        return Optional.of(switch (path) {
            case "spruce_log" -> "wood_pine";
            case "birch_log" -> "wood_birch";
            case "jungle_log" -> "wood_jungle";
            case "acacia_log" -> "wood_acacia";
            case "dark_oak_log" -> "wood_darkoak";
            default -> "wood";
        });
    }
}
