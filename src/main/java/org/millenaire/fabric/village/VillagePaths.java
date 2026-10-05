package org.millenaire.fabric.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.fabric.FabricSettlementState;
import org.millenaire.fabric.MillenaireCommands;
import org.millenaire.fabric.content.LegacyBuildingPlan;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Village paths: the buildings' {@code pathStartPos} points are joined by a minimum spanning tree rooted at the
 * town hall, each link routed over the ground with A* (around buildings and water, at most one block up or down
 * per step), and laid in the village type's {@code pathmaterial} for the building's {@code pathlevel}. Villagers
 * with the {@code buildpath} goal lay the cells a few at a time.
 */
public final class VillagePaths {
    public record Cell(BlockPos ground, Block material) {}
    private record Network(int buildings, List<Cell> cells) {}

    static final int MAX_LINK = 96, MAX_NODES = 12_000, CLEARANCE = 2, DETOUR = 24;
    private static final Map<String, Network> NETWORKS = new ConcurrentHashMap<>();

    private VillagePaths() {}

    /** Cells of the settlement's path network, computed once per set of buildings. */
    public static List<Cell> network(ServerLevel level, FabricSettlementState.Settlement settlement) {
        String key = VillageGrowth.key(settlement);
        var cached = NETWORKS.get(key);
        if (cached != null && cached.buildings() == settlement.buildings().size()) return cached.cells();
        var network = new Network(settlement.buildings().size(), compute(level, settlement));
        NETWORKS.put(key, network);
        return network.cells();
    }

    /** Cells not laid yet, nearest to {@code from} first. */
    public static List<Cell> pending(ServerLevel level, FabricSettlementState.Settlement settlement, BlockPos from, int limit) {
        return network(level, settlement).stream().filter(cell -> level.isLoaded(cell.ground()) && needsPath(level, cell))
                .sorted(Comparator.comparingDouble(cell -> cell.ground().distSqr(from))).limit(limit).toList();
    }

    static boolean needsPath(ServerLevel level, Cell cell) {
        BlockState ground = level.getBlockState(cell.ground());
        return !ground.is(cell.material()) && natural(ground);
    }

    /** Lays one cell: the ground block becomes path and small plants on it are cleared. */
    public static boolean lay(ServerLevel level, Cell cell) {
        if (!needsPath(level, cell)) return false;
        BlockPos above = cell.ground().above();
        BlockState plant = level.getBlockState(above);
        if (!plant.isAir() && plant.canBeReplaced() && plant.getFluidState().isEmpty()) level.destroyBlock(above, false);
        if (!level.getBlockState(above).isAir()) return false;
        level.setBlockAndUpdate(cell.ground(), cell.material().defaultBlockState());
        return true;
    }

    /** Ground a path may replace: soil, sand, gravel, stone and snow, never building blocks. */
    static boolean natural(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.STONE) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.DIRT_PATH) || path(state);
    }

    /** A Millénaire path block, which a better material of a later link may replace. */
    static boolean path(BlockState state) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id.getNamespace().equals("millenaire") && id.getPath().startsWith("path");
    }

    private static List<Cell> compute(ServerLevel level, FabricSettlementState.Settlement settlement) {
        var catalog = MillenaireCommands.contentCatalog();
        var type = PlayerVillages.type(settlement.type()).orElse(null);
        List<Block> materials = new ArrayList<>();
        if (type != null) for (String name : type.source().values("pathmaterial")) {
            var id = Identifier.fromNamespaceAndPath("millenaire", name.trim().toLowerCase(Locale.ROOT));
            BuiltInRegistries.BLOCK.getOptional(id).ifPresent(materials::add);
        }
        if (materials.isEmpty()) return List.of();
        // Path ends: each building's pathStartPos (or its origin), with its path level.
        record End(BlockPos pos, int level, boolean centre) {}
        List<End> ends = new ArrayList<>();
        for (var building : settlement.buildings()) {
            var plan = catalog.plan(building.placement().plan());
            int pathLevel = 1;
            if (plan != null) {
                var tags = plan.parameters().getOrDefault("tag", List.of());
                if (tags.stream().anyMatch(tag -> tag.equalsIgnoreCase("nopaths"))) continue;
                try { pathLevel = Integer.parseInt(plan.parameters().getOrDefault("pathlevel", List.of("1")).getLast().trim()); }
                catch (NumberFormatException ignored) {}
            }
            if (pathLevel <= 0) continue;
            var starts = building.placement().servicePoints().getOrDefault("pathStartPos", List.of());
            LegacyBuildingPlan.Position start = starts.isEmpty() ? building.placement().origin() : starts.getFirst();
            ends.add(new End(new BlockPos(start.x(), start.y(), start.z()), pathLevel, building.centre()));
        }
        if (ends.size() < 2) return List.of();
        ends.sort(Comparator.comparing(end -> !end.centre())); // the town hall is the root
        // Prim's minimum spanning tree over horizontal distance.
        List<int[]> links = new ArrayList<>();
        Set<Integer> joined = new HashSet<>(List.of(0));
        while (joined.size() < ends.size()) {
            int bestFrom = -1, bestTo = -1;
            double best = Double.MAX_VALUE;
            for (int from : joined)
                for (int to = 0; to < ends.size(); to++) {
                    if (joined.contains(to)) continue;
                    double distance = horizontal(ends.get(from).pos(), ends.get(to).pos());
                    if (distance < best) { best = distance; bestFrom = from; bestTo = to; }
                }
            joined.add(bestTo);
            if (best <= MAX_LINK) links.add(new int[]{bestFrom, bestTo});
        }
        List<FabricSettlementState.Building> buildings = settlement.buildings();
        Map<BlockPos, Block> cells = new LinkedHashMap<>();
        for (int[] link : links) {
            End a = ends.get(link[0]), b = ends.get(link[1]);
            Block material = materials.get(Math.min(materials.size(), Math.max(a.level(), b.level())) - 1);
            for (BlockPos ground : route(level, buildings, a.pos(), b.pos()))
                cells.merge(ground, material, (old, next) -> materials.indexOf(old) >= materials.indexOf(next) ? old : next);
        }
        List<Cell> result = new ArrayList<>();
        cells.forEach((pos, material) -> result.add(new Cell(pos, material)));
        return List.copyOf(result);
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Where a walker coming from feet height {@code feet} stands in column x/z: one block up, level or one down,
     * on a solid block with two free blocks above (roofs and eaves overhead do not matter). Returns the ground
     * block, or null when the column is not loaded or not walkable from there.
     */
    private static BlockPos ground(ServerLevel level, int x, int z, int feet) {
        if (!level.isLoaded(new BlockPos(x, feet, z))) return null;
        for (int dy : new int[]{0, 1, -1}) {
            BlockPos stand = new BlockPos(x, feet + dy, z);
            BlockState below = level.getBlockState(stand.below());
            if (below.isAir() || !below.getFluidState().isEmpty() && !below.is(Blocks.WATER)) continue;
            if (passable(level.getBlockState(stand)) && passable(level.getBlockState(stand.above()))) return stand.below();
        }
        return null;
    }

    private static boolean passable(BlockState state) {
        return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    /** A* over ground columns from {@code from} to {@code to}; returns the ground blocks of the route, or none. */
    static List<BlockPos> route(ServerLevel level, List<FabricSettlementState.Building> buildings, BlockPos from, BlockPos to) {
        record Node(int x, int z, double f) {}
        long goal = BlockPos.asLong(to.getX(), 0, to.getZ());
        Map<Long, Double> cost = new HashMap<>();
        Map<Long, Long> parent = new HashMap<>();
        Map<Long, Integer> heights = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f));
        // pathStartPos is a feet-level point at the building's door.
        BlockPos startGround = from.below();
        if (!level.isLoaded(startGround)) return List.of();
        long start = BlockPos.asLong(from.getX(), 0, from.getZ());
        cost.put(start, 0.0);
        heights.put(start, startGround.getY());
        open.add(new Node(from.getX(), from.getZ(), horizontal(from, to)));
        int expanded = 0;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!open.isEmpty() && expanded++ < MAX_NODES) {
            Node node = open.poll();
            long key = BlockPos.asLong(node.x(), 0, node.z());
            if (key == goal) break;
            double base = cost.get(key);
            int height = heights.get(key);
            for (int[] step : steps) {
                int x = node.x() + step[0], z = node.z() + step[1];
                // Stay in a box around the link so that a blocked link fails quickly.
                if (x < Math.min(from.getX(), to.getX()) - DETOUR || x > Math.max(from.getX(), to.getX()) + DETOUR
                        || z < Math.min(from.getZ(), to.getZ()) - DETOUR || z > Math.max(from.getZ(), to.getZ()) + DETOUR) continue;
                long next = BlockPos.asLong(x, 0, z);
                BlockPos ground = ground(level, x, z, height + 1);
                if (ground == null) continue;
                boolean nearEnd = Math.abs(x - from.getX()) + Math.abs(z - from.getZ()) <= CLEARANCE + 1
                        || Math.abs(x - to.getX()) + Math.abs(z - to.getZ()) <= CLEARANCE + 1;
                int dy = Math.abs(ground.getY() - height); // at most 1 by construction
                BlockState state = level.getBlockState(ground);
                if (dy > 1 || !state.getFluidState().isEmpty()) continue;
                // Paving and floors can be crossed, but natural ground (where a path is laid) is preferred.
                double g = base + 1 + dy * 2 + (natural(state) || nearEnd ? 0 : 3);
                if (g >= cost.getOrDefault(next, Double.MAX_VALUE)) continue;
                cost.put(next, g);
                parent.put(next, key);
                heights.put(next, ground.getY());
                open.add(new Node(x, z, g + Math.abs(x - to.getX()) + Math.abs(z - to.getZ())));
            }
        }
        if (!parent.containsKey(goal)) return List.of();
        List<BlockPos> result = new ArrayList<>();
        for (Long at = goal; at != null && at != start; at = parent.get(at)) {
            BlockPos column = BlockPos.of(at);
            result.add(new BlockPos(column.getX(), heights.get(at), column.getZ()));
        }
        Collections.reverse(result);
        return result;
    }

    /** Lays the whole network at once (admin command); returns the number of cells laid. */
    public static int layAll(ServerLevel level, FabricSettlementState.Settlement settlement) {
        int laid = 0;
        for (Cell cell : network(level, settlement)) if (level.isLoaded(cell.ground()) && lay(level, cell)) laid++;
        return laid;
    }
}
