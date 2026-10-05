package org.millenaire.fabric.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.millenaire.fabric.content.CustomBuildings;
import org.millenaire.fabric.content.LegacyBuildingPlan.Position;
import org.millenaire.fabric.goal.GoalRules;

import java.util.*;

/**
 * Finds the service points of a player-built building in a cube around its sign. What counts, by resource:
 * chests and barrels ({@code chest}), crafting tables ({@code craft}), signs ({@code sign}), furnaces, smokers
 * and blast furnaces ({@code furnace}), farmland ({@code field}), hay bales for the animal pens ({@code spawn}),
 * saplings ({@code sapling}), carpets for market stalls ({@code stall}), exposed stone, sand, sandstone, gravel
 * or clay ({@code mining}), dirt paths as brick-drying spots ({@code mudbrick}), sugar cane ({@code sugar}),
 * water with air above ({@code fishing}, {@code squid}) and cocoa ({@code cacao}). Beds become sleeping places.
 */
public final class CustomBuildingScanner {
    /** Blocks scanned below and above the sign. */
    static final int BELOW = 4, ABOVE = 8;

    public record Scan(Map<String, List<Position>> points, Map<String, Integer> counts, List<String> missing) {
        public boolean complete() { return missing.isEmpty(); }
    }

    private CustomBuildingScanner() {}

    public static Scan scan(ServerLevel level, BlockPos centre, CustomBuildings.Definition definition) {
        Map<String, List<Position>> points = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        int radius = definition.radius();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -BELOW; dy <= ABOVE; dy++)
            for (int dx = -radius; dx <= radius; dx++)
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (!level.isLoaded(cursor)) continue;
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) continue;
                    classify(level, cursor.immutable(), state, definition, points, counts);
                }
        List<String> missing = new ArrayList<>();
        for (var requirement : definition.resources().entrySet()) {
            int found = counts.getOrDefault(requirement.getKey(), 0);
            if (found < requirement.getValue().min())
                missing.add(requirement.getKey() + " " + found + "/" + requirement.getValue().min());
        }
        return new Scan(points, counts, missing);
    }

    private static void classify(ServerLevel level, BlockPos pos, BlockState state, CustomBuildings.Definition definition,
                                 Map<String, List<Position>> points, Map<String, Integer> counts) {
        Block block = state.getBlock();
        var resources = definition.resources();
        if (block instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) add(points, "sleepingPos", pos);
        if (block instanceof ChestBlock || block instanceof BarrelBlock) found(points, counts, resources, "chest", "chests", pos);
        else if (block == Blocks.CRAFTING_TABLE) found(points, counts, resources, "craft", "craftingPos", pos);
        else if (block instanceof SignBlock) found(points, counts, resources, "sign", "signs", pos);
        else if (block == Blocks.FURNACE || block == Blocks.SMOKER || block == Blocks.BLAST_FURNACE) found(points, counts, resources, "furnace", "furnaces", pos);
        else if (block instanceof FarmlandBlock) found(points, counts, resources, "field",
                GoalRules.soilFor(definition.cropType().isEmpty() ? "wheat" : definition.cropType()), pos);
        else if (block == Blocks.HAY_BLOCK) found(points, counts, resources, "spawn",
                (definition.spawnType().isEmpty() ? "cow" : definition.spawnType()) + "spawn", pos.above());
        else if (block instanceof SaplingBlock) found(points, counts, resources, "sapling", treeSpawn(block), pos);
        else if (state.is(BlockTags.WOOL_CARPETS)) found(points, counts, resources, "stall", "sellingPos", pos);
        else if (block == Blocks.DIRT_PATH && level.getBlockState(pos.above()).isAir()) found(points, counts, resources, "mudbrick", "brickspot", pos.above());
        else if (block == Blocks.SUGAR_CANE && !level.getBlockState(pos.below()).is(Blocks.SUGAR_CANE))
            found(points, counts, resources, "sugar", "sugarcanesoil", pos.below());
        else if (block == Blocks.COCOA) found(points, counts, resources, "cacao", "cacaospot", pos);
        else if (block == Blocks.WATER && level.getBlockState(pos.above()).isAir()) {
            if (resources.containsKey("fishing")) found(points, counts, resources, "fishing", "fishingspot", pos);
            if (resources.containsKey("squid")) found(points, counts, resources, "squid", "squidspawn", pos);
        } else if (level.getBlockState(pos.above()).isAir()) {
            String source = miningSource(block);
            if (source != null) found(points, counts, resources, "mining", source, pos);
        }
    }

    private static String miningSource(Block block) {
        if (block == Blocks.STONE) return "stonesource";
        if (block == Blocks.SAND) return "sandsource";
        if (block == Blocks.SANDSTONE) return "sandstonesource";
        if (block == Blocks.GRAVEL) return "gravelsource";
        if (block == Blocks.CLAY) return "claysource";
        return null;
    }

    private static String treeSpawn(Block sapling) {
        String path = BuiltInRegistries.BLOCK.getKey(sapling).getPath();
        return switch (path) {
            case "spruce_sapling" -> "pinespawn";
            case "birch_sapling" -> "birchspawn";
            case "jungle_sapling" -> "junglespawn";
            case "acacia_sapling" -> "acaciaspawn";
            case "dark_oak_sapling" -> "darkoakspawn";
            default -> "oakspawn";
        };
    }

    /** Counts a resource the building asks for and keeps up to its maximum as service points. */
    private static void found(Map<String, List<Position>> points, Map<String, Integer> counts, Map<String, CustomBuildings.Range> resources,
                              String resource, String point, BlockPos pos) {
        var range = resources.get(resource);
        // Chests, crafting tables and furnaces always serve the building; other resources only when asked for.
        if (range == null && !List.of("chest", "craft", "furnace").contains(resource)) return;
        int count = counts.merge(resource, 1, Integer::sum);
        if (range == null || count <= range.max()) add(points, point, pos);
    }

    private static void add(Map<String, List<Position>> points, String key, BlockPos pos) {
        points.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new Position(pos.getX(), pos.getY(), pos.getZ()));
    }
}
