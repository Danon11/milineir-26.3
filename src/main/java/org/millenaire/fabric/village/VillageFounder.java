package org.millenaire.fabric.village;

import net.minecraft.server.level.ServerLevel;
import org.millenaire.fabric.FabricBuildingState;
import org.millenaire.fabric.FabricSettlementLifecycleState;
import org.millenaire.fabric.FabricSettlementState;
import org.millenaire.fabric.FabricVillagerState;
import org.millenaire.fabric.content.BuildingPlacement;
import org.millenaire.fabric.content.LegacyBuildingPlan;
import org.millenaire.fabric.content.LegacyContentCatalog;
import org.millenaire.fabric.content.VillageLayout;
import org.millenaire.fabric.content.VillagePlacement;
import org.millenaire.fabric.content.VillageTypeDefinition;
import org.millenaire.fabric.villager.VillagerSpawning;

import java.io.IOException;
import java.util.*;

/** Founds a village: lays it out, checks and places every building, records it, and spawns residents. */
public final class VillageFounder {
    public record Result(boolean placed, int buildings, int changedBlocks, int residents, List<String> issues) {
        public Result { issues = List.copyOf(issues); }
    }

    private VillageFounder() {}

    static int surface(ServerLevel level, int x, int z) {
        return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    /** Moves each building to the mean ground height of its footprint; sub-buildings follow their parent. */
    static VillageLayout.Layout adapt(ServerLevel level, VillageLayout.Layout layout) {
        Map<LegacyBuildingPlan.Position, Integer> heights = new HashMap<>();
        List<VillageLayout.Building> buildings = new ArrayList<>();
        for (var building : layout.buildings()) {
            var area = VillageLayout.bounds(building.plan(), building.origin(), building.rotation(), false);
            int ground = heights.computeIfAbsent(building.origin(), ignored -> {
                long sum = 0;
                int count = 0;
                for (int x = area.minX(); x <= area.maxX(); x += 2) for (int z = area.minZ(); z <= area.maxZ(); z += 2) {
                    sum += surface(level, x, z);
                    count++;
                }
                return (int) Math.round((double) sum / Math.max(1, count));
            });
            int offset = building.origin().y() - layout.origin().y(); // altitudeoffset of the plan
            var origin = new LegacyBuildingPlan.Position(building.origin().x(), ground + offset, building.origin().z());
            buildings.add(new VillageLayout.Building(building.plan(), origin, building.rotation(), building.centre(), building.reservedArea(), building.role()));
        }
        smoothWalls(layout.origin(), buildings);
        return new VillageLayout.Layout(layout.type(), layout.origin(), layout.seed(), layout.radius(), buildings, layout.issues());
    }

    /**
     * Wall smoothing: wall segments, taken in order around the village, have their height averaged with their
     * neighbours twice, so the wall follows the land without steps between segments.
     */
    static void smoothWalls(LegacyBuildingPlan.Position centre, List<VillageLayout.Building> buildings) {
        List<Integer> walls = new ArrayList<>();
        for (int i = 0; i < buildings.size(); i++) if (buildings.get(i).role() == VillageLayout.Role.WALL) walls.add(i);
        if (walls.size() < 3) return;
        walls.sort(Comparator.comparingDouble(i -> Math.atan2(buildings.get(i).origin().z() - centre.z(), buildings.get(i).origin().x() - centre.x())));
        int n = walls.size();
        int[] heights = new int[n];
        for (int k = 0; k < n; k++) heights[k] = buildings.get(walls.get(k)).origin().y();
        for (int pass = 0; pass < 2; pass++) {
            int[] next = new int[n];
            for (int k = 0; k < n; k++) next[k] = Math.round((heights[(k + n - 1) % n] + 2 * heights[k] + heights[(k + 1) % n]) / 4.0f);
            heights = next;
        }
        for (int k = 0; k < n; k++) {
            var b = buildings.get(walls.get(k));
            var origin = new LegacyBuildingPlan.Position(b.origin().x(), heights[k], b.origin().z());
            buildings.set(walls.get(k), new VillageLayout.Building(b.plan(), origin, b.rotation(), b.centre(), b.reservedArea(), b.role()));
        }
    }

    /** Fills air and water under a building's lowest floor down to the ground, so nothing floats. */
    static void fillFoundation(ServerLevel level, VillageLayout.Building building) {
        if (building.role() == VillageLayout.Role.SUB) return;
        var area = VillageLayout.bounds(building.plan(), building.origin(), building.rotation(), false);
        int floor = building.origin().y() + building.plan().startLevel();
        var mutable = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int x = area.minX(); x <= area.maxX(); x++) for (int z = area.minZ(); z <= area.maxZ(); z++) {
            for (int y = floor - 1, depth = 0; depth < 12; y--, depth++) {
                mutable.set(x, y, z);
                var state = level.getBlockState(mutable);
                if (!state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced()) break;
                level.setBlock(mutable, depth == 0 ? net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()
                        : net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
    }

    public static Result found(ServerLevel level, LegacyContentCatalog catalog, VillageTypeDefinition type,
                               LegacyBuildingPlan.Position origin, long seed, boolean replace) throws IOException {
        return found(level, catalog, type, origin, seed, replace, false);
    }

    /**
     * @param adaptTerrain place every building at the average ground height of its own footprint and fill the
     *                     ground below it, instead of using one level for the whole village
     */
    public static Result found(ServerLevel level, LegacyContentCatalog catalog, VillageTypeDefinition type,
                               LegacyBuildingPlan.Position origin, long seed, boolean replace, boolean adaptTerrain) throws IOException {
        var layout = VillageLayout.create(catalog, type, origin, seed);
        if (adaptTerrain) {
            layout = adapt(level, layout);
            // Foundations go in first so doors, torches and wall decorations find their supports.
            for (var building : layout.buildings()) fillFoundation(level, building);
        }
        var prepared = VillagePlacement.prepare(layout, catalog.palette(), catalog.goods());
        // Basements must not reach below the world floor (shallow superflat worlds): lift the whole village instead.
        int lowest = prepared.combined().changes().stream().mapToInt(change -> change.pos().getY()).min().orElse(origin.y());
        if (!adaptTerrain && lowest <= level.getMinY()) { // the floor layer itself is bedrock
            var lifted = new LegacyBuildingPlan.Position(origin.x(), origin.y() + level.getMinY() + 1 - lowest, origin.z());
            layout = VillageLayout.create(catalog, type, lifted, seed);
            for (var building : layout.buildings()) fillFoundation(level, building);
            prepared = VillagePlacement.prepare(layout, catalog.palette(), catalog.goods());
        }
        var dimension = level.dimension().identifier();
        var settlements = FabricSettlementState.get(level.getServer());
        var world = BuildingPlacement.world(level);
        List<String> issues = new ArrayList<>(BuildingPlacement.checkDestinations(prepared.combined(), world, replace));
        issues.addAll(settlements.overlapIssues(dimension, layout));
        if (!issues.isEmpty()) return new Result(false, layout.buildings().size(), 0, 0, issues);
        // Initialize and validate records before mutating blocks; publish only after the whole write succeeds.
        var record = FabricSettlementState.from(dimension, prepared);
        var buildingState = FabricBuildingState.get(level.getServer());
        int changed = VillagePlacement.place(prepared, world, replace);
        settlements.record(record);
        record.buildings().forEach(building -> buildingState.record(building.placement()));
        FabricSettlementLifecycleState.get(level.getServer()).ensure(record);
        var random = new SplittableRandom(seed);
        List<String> residentIssues = new ArrayList<>();
        int before = FabricVillagerState.get(level.getServer()).villagers().size();
        for (var building : record.buildings()) {
            var plan = catalog.plan(building.placement().plan());
            if (plan != null) residentIssues.addAll(VillagerSpawning.populate(level, plan, building.placement(), random));
        }
        int residents = FabricVillagerState.get(level.getServer()).villagers().size() - before;
        return new Result(true, layout.buildings().size(), changed, residents, residentIssues);
    }
}
