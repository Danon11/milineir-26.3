package org.millenaire.fabric.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.fabric.FabricBuildingState;
import org.millenaire.fabric.FabricSettlementState;
import org.millenaire.fabric.content.LegacyBuildingPlan;
import org.millenaire.fabric.content.LegacyContentCatalog;

import java.util.*;

/** The buildings a villager can work with: its home, its settlement and the settlement centre. */
public record VillageContext(ServerLevel level, LegacyContentCatalog catalog, FabricBuildingState.PlacedBuilding home,
                             List<FabricBuildingState.PlacedBuilding> buildings, Optional<FabricBuildingState.PlacedBuilding> townhall) {
    public VillageContext { buildings = List.copyOf(buildings); }

    /** Parses the {@code plan@x,y,z} building key stored on villagers. */
    public static Optional<VillageContext> of(ServerLevel level, LegacyContentCatalog catalog, String buildingKey) {
        int at = buildingKey.lastIndexOf('@');
        if (at <= 0) return Optional.empty();
        String plan = buildingKey.substring(0, at);
        String[] coordinates = buildingKey.substring(at + 1).split(",");
        if (coordinates.length != 3) return Optional.empty();
        LegacyBuildingPlan.Position origin;
        try {
            origin = new LegacyBuildingPlan.Position(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]), Integer.parseInt(coordinates[2]));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
        var dimension = level.dimension().identifier();
        var home = FabricBuildingState.get(level.getServer()).buildings().stream()
                // Upgrades change the plan's level suffix (A0 -> A1), not the building a villager belongs to.
                .filter(b -> b.dimension().equals(dimension) && b.origin().equals(origin)
                        && org.millenaire.fabric.village.VillagePopulation.baseKey(b.plan() + "@").equals(org.millenaire.fabric.village.VillagePopulation.baseKey(plan + "@")))
                .findFirst();
        if (home.isEmpty()) return Optional.empty();
        var settlement = FabricSettlementState.get(level.getServer()).settlements().stream()
                .filter(s -> s.dimension().equals(dimension) && s.buildings().stream().anyMatch(b -> b.placement().origin().equals(origin)))
                .findFirst();
        List<FabricBuildingState.PlacedBuilding> buildings = settlement.map(s -> s.buildings().stream().map(FabricSettlementState.Building::placement).toList())
                .orElse(List.of(home.get()));
        var townhall = settlement.flatMap(s -> s.buildings().stream().filter(FabricSettlementState.Building::centre)
                .map(FabricSettlementState.Building::placement).findFirst());
        return Optional.of(new VillageContext(level, catalog, home.get(), buildings, townhall));
    }

    public List<String> tags(FabricBuildingState.PlacedBuilding building) {
        var plan = catalog.plan(building.plan());
        if (plan == null) return List.of();
        List<String> tags = new ArrayList<>();
        for (String value : plan.parameters().getOrDefault("tag", List.of()))
            for (String tag : value.split(",")) if (!tag.isBlank()) tags.add(tag.trim().toLowerCase(Locale.ROOT));
        return tags;
    }

    /** Buildings carrying any of the tags, home first so villagers prefer their own workplace. */
    public List<FabricBuildingState.PlacedBuilding> withTags(Collection<String> wanted) {
        List<FabricBuildingState.PlacedBuilding> result = new ArrayList<>();
        if (!Collections.disjoint(tags(home), wanted)) result.add(home);
        for (var building : buildings) if (building != home && !Collections.disjoint(tags(building), wanted)) result.add(building);
        return result;
    }

    public List<BlockPos> points(FabricBuildingState.PlacedBuilding building, String key) {
        return building.servicePoints().getOrDefault(key, List.of()).stream().map(p -> new BlockPos(p.x(), p.y(), p.z())).toList();
    }

    public ChestGoodsStore store(FabricBuildingState.PlacedBuilding building) {
        return new ChestGoodsStore(level, points(building, "chests"), catalog.goods());
    }

    /** A point to walk to for work in the building: crafting spot, chest, or the plan origin. */
    public BlockPos workPoint(FabricBuildingState.PlacedBuilding building) {
        for (String key : List.of("craftingPos", "sellingPos", "chests", "sleepingPos")) {
            var points = points(building, key);
            if (!points.isEmpty()) return points.getFirst();
        }
        return new BlockPos(building.origin().x(), building.origin().y() + 1, building.origin().z());
    }

    public Optional<BlockPos> leisurePoint(FabricBuildingState.PlacedBuilding building) {
        var points = points(building, "leisurePos");
        return points.isEmpty() ? Optional.empty() : Optional.of(points.get(level.getRandom().nextInt(points.size())));
    }
}
