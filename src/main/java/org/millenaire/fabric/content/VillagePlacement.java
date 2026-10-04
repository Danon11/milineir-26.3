package org.millenaire.fabric.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import org.millenaire.fabric.economy.LegacyGoodsCatalog;
import org.millenaire.fabric.economy.StartingStock;

/** Compiles every starting building into one placement operation and one rollback footprint. */
public final class VillagePlacement {
    public static final int MAX_OPERATIONS = 1 << 22;
    public record PreparedBuilding(VillageLayout.Building building, BuildingPlacement.Prepared blocks) {}
    public record Prepared(VillageLayout.Layout layout, List<PreparedBuilding> buildings, BuildingPlacement.Prepared combined) {
        public Prepared { buildings = List.copyOf(buildings); }
        public boolean supported() { return combined.supported(); }
    }
    private VillagePlacement() {}

    public static Prepared prepare(VillageLayout.Layout layout, LegacyPalette palette) throws IOException {
        return prepare(layout, palette, LegacyGoodsCatalog.empty());
    }
    public static Prepared prepare(VillageLayout.Layout layout, LegacyPalette palette, LegacyGoodsCatalog goods) throws IOException {
        return prepare(layout, palette, LegacyBlockStateResolver::resolve, goods);
    }

    static Prepared prepare(VillageLayout.Layout layout, LegacyPalette palette, Function<LegacyPalette.Point, BlockState> resolver) throws IOException {
        return prepare(layout, palette, resolver, LegacyGoodsCatalog.empty());
    }
    static Prepared prepare(VillageLayout.Layout layout, LegacyPalette palette, Function<LegacyPalette.Point, BlockState> resolver,
                            LegacyGoodsCatalog goods) throws IOException {
        List<PreparedBuilding> buildings = new ArrayList<>();
        List<BuildingPlacement.Change> changes = new ArrayList<>();
        List<BuildingPlacement.Setup> setups = new ArrayList<>();
        List<StartingStock.Inventory> stocks = new ArrayList<>();
        List<BlockPos> treeRoots = new ArrayList<>();
        Set<String> issues = new LinkedHashSet<>(layout.issues());
        // These declarations create additional structures in the original generator.
        // Refuse incomplete results until their generators have been migrated.
        for (String key : List.of("customcentre", "hameau", "hamlet")) {
            if (layout.type().source().values(key).stream().anyMatch(value -> !value.isBlank()))
                issues.add("Unsupported village feature: " + key + "=" + layout.type().source().values(key));
        }
        Map<BlockPos, BuildingPlacement.Change> byPosition = new LinkedHashMap<>();
        for (var building : layout.buildings()) {
            var plan = building.plan();
            var origin = building.origin();
            var prepared = BuildingPlacement.prepare(plan, palette, new BlockPos(origin.x(), origin.y(), origin.z()), building.rotation(), resolver, goods, layout.seed());
            buildings.add(new PreparedBuilding(building, prepared));
            setups.addAll(prepared.setups());
            stocks.addAll(prepared.startingStock());
            treeRoots.addAll(prepared.treeRoots());
            prepared.issues().forEach(issue -> issues.add(plan.id() + ": " + issue));
            for (var change : prepared.changes()) {
                // A sub-building overlays its parent; any other overlap is a layout error.
                if (byPosition.containsKey(change.pos()) && building.role() != VillageLayout.Role.SUB) {
                    issues.add("Overlapping building blocks at " + change.pos());
                    continue;
                }
                byPosition.put(change.pos(), change);
                if (byPosition.size() > MAX_OPERATIONS) issues.add("Village exceeds " + MAX_OPERATIONS + " block operations");
            }
        }
        changes.addAll(byPosition.values());
        if (!layout.complete() && issues.isEmpty()) issues.add("Incomplete starting layout");
        if (!issues.isEmpty()) { changes.clear(); setups.clear(); stocks.clear(); treeRoots.clear(); }
        changes.sort(Comparator.comparingInt((BuildingPlacement.Change c) -> c.state().isAir() ? 0 : c.secondPass() ? 2 : 1)
                .thenComparingInt(c -> c.pos().getY()));
        return new Prepared(layout, buildings, new BuildingPlacement.Prepared(changes, List.copyOf(issues), Map.of(), setups, stocks, treeRoots));
    }

    public static int place(Prepared prepared, BuildingPlacement.WorldAccess world, boolean replace) {
        return BuildingPlacement.place(prepared.combined(), world, replace);
    }
}
