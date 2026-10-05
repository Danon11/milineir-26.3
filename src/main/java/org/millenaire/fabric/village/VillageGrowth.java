package org.millenaire.fabric.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.millenaire.fabric.FabricBuildingState;
import org.millenaire.fabric.FabricSettlementState;
import org.millenaire.fabric.MillenaireCommands;
import org.millenaire.fabric.content.BuildingPlacement;
import org.millenaire.fabric.content.LegacyBuildingPlan;
import org.millenaire.fabric.content.VillageLayout;
import org.millenaire.fabric.villager.MillVillagerEntity;
import org.millenaire.fabric.villager.VillagerSpawning;

import java.util.*;

/**
 * Village growth: each settlement picks its next project — an upgrade of one of its buildings or a new building
 * from the village type's core list, then its secondary list — weighted by plan priority. The project costs the
 * items of its blocks, paid from the chests of all village buildings, and a villager with the construction goal
 * builds it; villages without builders complete projects directly.
 */
public final class VillageGrowth {
    public static final int INTERVAL = 1200;
    private static final Set<String> FREE = Set.of("dirt", "grass_block", "coarse_dirt", "podzol", "farmland", "dirt_path", "water", "lava",
            "snow", "snow_block", "clay", "short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "dandelion", "poppy", "blue_orchid",
            "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy", "sunflower", "lilac",
            "rose_bush", "peony", "nether_wart", "nether_portal", "torch", "wall_torch", "gravel", "sand", "fire");

    public record Project(String settlementKey, boolean upgrade, LegacyBuildingPlan plan, LegacyBuildingPlan.Position origin, int rotation,
                          VillageLayout.Bounds area, Map<Item, Integer> cost) {
        public BlockPos site() { return new BlockPos(origin.x(), origin.y(), origin.z()); }
        public String label() { return (upgrade ? "upgrade " : "build ") + plan.id(); }
    }

    private static final Map<String, Project> PENDING = new HashMap<>();
    private static final SplittableRandom RANDOM = new SplittableRandom();

    private VillageGrowth() {}

    public static String key(FabricSettlementState.Settlement settlement) {
        return settlement.dimension() + "@" + settlement.origin().x() + "," + settlement.origin().y() + "," + settlement.origin().z();
    }

    public static Optional<Project> pending(String settlementKey) { return Optional.ofNullable(PENDING.get(settlementKey)); }

    /** Settlement key of the village containing a building origin. */
    public static Optional<String> settlementOf(MinecraftServer server, net.minecraft.resources.Identifier dimension, LegacyBuildingPlan.Position origin) {
        return FabricSettlementState.get(server).settlements().stream().filter(s -> s.dimension().equals(dimension)
                && s.buildings().stream().anyMatch(b -> b.placement().origin().equals(origin))).findFirst().map(VillageGrowth::key);
    }

    // ---------------------------------------------------------------- cost

    /** Items a project consumes: one per placed block, except free terrain, plants and the second half of doors and beds. */
    public static Map<Item, Integer> cost(BuildingPlacement.Prepared prepared) {
        Map<Item, Integer> cost = new LinkedHashMap<>();
        for (var change : prepared.changes()) {
            BlockState state = change.state();
            if (state.isAir() || FREE.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath())) continue;
            if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) continue;
            if (state.getBlock() instanceof DoublePlantBlock) continue;
            if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT) continue;
            Item item = state.getBlock().asItem();
            if (item == Items.AIR) continue;
            cost.merge(item, 1, Integer::sum);
        }
        return cost;
    }

    private static List<Container> containers(ServerLevel level, FabricSettlementState.Settlement settlement) {
        List<Container> result = new ArrayList<>();
        for (var building : settlement.buildings())
            for (var point : building.placement().servicePoints().getOrDefault("chests", List.of())) {
                BlockPos pos = new BlockPos(point.x(), point.y(), point.z());
                if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof Container container && !result.contains(container)) result.add(container);
            }
        return result;
    }

    static Map<Item, Integer> missing(List<Container> containers, Map<Item, Integer> cost) {
        Map<Item, Integer> have = new HashMap<>();
        for (Container container : containers)
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty()) have.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        Map<Item, Integer> missing = new LinkedHashMap<>();
        cost.forEach((item, count) -> { if (have.getOrDefault(item, 0) < count) missing.put(item, count - have.getOrDefault(item, 0)); });
        return missing;
    }

    private static void consume(List<Container> containers, Map<Item, Integer> cost) {
        for (var entry : cost.entrySet()) {
            int remaining = entry.getValue();
            for (Container container : containers) {
                for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
                    ItemStack stack = container.getItem(slot);
                    if (!stack.is(entry.getKey())) continue;
                    int taken = Math.min(remaining, stack.getCount());
                    stack.shrink(taken);
                    remaining -= taken;
                }
                container.setChanged();
            }
        }
    }

    // ---------------------------------------------------------------- planning

    private static LegacyBuildingPlan nextLevel(LegacyBuildingPlan plan) {
        return MillenaireCommands.contentCatalog().plans().get(plan.culture() + ":" + plan.key() + "_" + plan.variation() + (plan.upgrade() + 1));
    }

    private static int priority(LegacyBuildingPlan plan) {
        var values = plan.parameters().getOrDefault("priority", List.of());
        try { return values.isEmpty() ? 1 : Math.max(1, Integer.parseInt(values.getLast().trim())); }
        catch (NumberFormatException exception) { return 1; }
    }

    /** Candidate projects in preference order: weighted by priority, upgrades and the lowest unsatisfied new-building tier. */
    static List<Project> candidates(ServerLevel level, FabricSettlementState.Settlement settlement) {
        var catalog = MillenaireCommands.contentCatalog();
        String[] typeId = settlement.type().split(":", 2);
        var culture = catalog.cultures().get(typeId[0]);
        var type = culture == null ? null : culture.villageTypes().get(typeId[1]);
        record Weighted(Project project, int weight) {}
        List<Weighted> weighted = new ArrayList<>();
        Map<String, Integer> counts = new HashMap<>();
        for (var building : settlement.buildings()) {
            var plan = catalog.plan(building.placement().plan());
            if (plan == null) continue;
            counts.merge(plan.key(), 1, Integer::sum);
            var next = nextLevel(plan);
            if (next == null || plan.parameters().getOrDefault("issubbuilding", List.of()).contains("true")) continue;
            weighted.add(new Weighted(new Project(key(settlement), true, next, building.placement().origin(), building.placement().rotation(),
                    building.reservedArea(), Map.of()), priority(next)));
        }
        // A player-controlled village builds only what its owner ordered; upgrades still come by themselves.
        var owner = org.millenaire.fabric.FabricVillageOwnership.get(level.getServer()).owner(key(settlement));
        List<List<String>> tiers = owner.isPresent() ? List.of(owner.get().orders())
                : type == null ? List.of() : List.of(type.coreBuildings(), type.secondaryBuildings());
        if (!tiers.isEmpty()) {
            for (List<String> tier : tiers) {
                Map<String, Integer> wanted = new LinkedHashMap<>();
                tier.forEach(key -> wanted.merge(key.trim(), 1, Integer::sum));
                // Orders are built one at a time in the order given, even when the village already has such a building.
                List<String> missing = owner.isPresent() ? tier.stream().limit(1).toList()
                        : wanted.entrySet().stream().filter(e -> counts.getOrDefault(e.getKey(), 0) < e.getValue()).map(Map.Entry::getKey).toList();
                if (missing.isEmpty()) continue;
                var existing = settlement.buildings().stream().map(b -> {
                    var plan = catalog.plan(b.placement().plan());
                    return plan == null ? null : new VillageLayout.Building(plan, b.placement().origin(), b.placement().rotation(), b.centre(), b.reservedArea());
                }).filter(Objects::nonNull).toList();
                for (String key : missing) {
                    var plan = catalog.plans().values().stream().filter(p -> p.culture().equals(typeId[0]) && p.key().equals(key) && p.upgrade() == 0)
                            .min(Comparator.comparingInt(LegacyBuildingPlan::variation)).orElse(null);
                    if (plan == null) continue;
                    VillageLayout.Building site;
                    try { site = VillageLayout.locate(plan, settlement.origin(), settlement.radius(), existing, new Random(RANDOM.nextLong())); }
                    catch (IllegalArgumentException exception) { continue; }
                    if (site == null) continue;
                    weighted.add(new Weighted(new Project(key(settlement), false, plan, site.origin(), site.rotation(), site.reservedArea(), Map.of()),
                            priority(plan) * 2));
                }
                break; // only the lowest unsatisfied tier grows, as in the original
            }
        }
        // Weighted shuffle: higher priority projects come first more often.
        List<Project> ordered = new ArrayList<>();
        List<Weighted> pool = new ArrayList<>(weighted);
        while (!pool.isEmpty()) {
            int total = pool.stream().mapToInt(Weighted::weight).sum();
            int pick = RANDOM.nextInt(total);
            for (int i = 0; i < pool.size(); i++) {
                pick -= pool.get(i).weight();
                if (pick < 0) { ordered.add(pool.remove(i).project()); break; }
            }
        }
        return ordered;
    }

    private static Optional<BuildingPlacement.Prepared> prepare(ServerLevel level, Project project) {
        try {
            var prepared = BuildingPlacement.prepare(project.plan(), MillenaireCommands.contentCatalog().palette(), project.site(), project.rotation(),
                    MillenaireCommands.contentCatalog().goods(), level.getSeed() ^ project.site().asLong());
            if (!prepared.supported()) return Optional.empty();
            var world = BuildingPlacement.world(level);
            if (project.upgrade()) prepared = BuildingPlacement.forUpgrade(prepared, world);
            if (!BuildingPlacement.checkDestinations(prepared, world, true).isEmpty()) return Optional.empty();
            return Optional.of(prepared);
        } catch (java.io.IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------- ticking

    public static void tick(MinecraftServer server) {
        if (server.overworld().getGameTime() % INTERVAL != 0) return;
        for (var settlement : FabricSettlementState.get(server).settlements()) {
            ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, settlement.dimension()));
            if (level == null || !level.isLoaded(new BlockPos(settlement.origin().x(), settlement.origin().y(), settlement.origin().z()))) continue;
            evaluate(level, settlement, false);
        }
    }

    /**
     * Chooses the next affordable project. With {@code rush}, costs are ignored and the project is built at once.
     * Returns a description of what happened.
     */
    public static String evaluate(ServerLevel level, FabricSettlementState.Settlement settlement, boolean rush) {
        String key = key(settlement);
        if (!rush && PENDING.containsKey(key)) return "waiting for a builder: " + PENDING.get(key).label();
        var containers = containers(level, settlement);
        String lastShortage = "nothing to build";
        for (Project candidate : candidates(level, settlement)) {
            var prepared = prepare(level, candidate);
            if (prepared.isEmpty()) continue;
            var cost = cost(prepared.get());
            // A rushed project is free; otherwise the village pays the cost of its blocks.
            var project = new Project(candidate.settlementKey(), candidate.upgrade(), candidate.plan(), candidate.origin(), candidate.rotation(),
                    candidate.area(), rush ? Map.of() : cost);
            if (!rush) {
                var missing = missing(containers, cost);
                if (!missing.isEmpty()) { lastShortage = project.label() + " needs " + describe(missing); continue; }
                if (hasBuilder(level, settlement)) {
                    PENDING.put(key, project);
                    return "builder assigned: " + project.label();
                }
            }
            return complete(level, project);
        }
        return lastShortage;
    }

    private static String describe(Map<Item, Integer> items) {
        return items.entrySet().stream().limit(5).map(e -> e.getValue() + " " + BuiltInRegistries.ITEM.getKey(e.getKey()).getPath())
                .reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static boolean hasBuilder(ServerLevel level, FabricSettlementState.Settlement settlement) {
        int radius = settlement.radius() + 16;
        var box = new net.minecraft.world.phys.AABB(new BlockPos(settlement.origin().x(), settlement.origin().y(), settlement.origin().z())).inflate(radius, 64, radius);
        return level.getEntitiesOfClass(MillVillagerEntity.class, box, villager -> {
            var profile = VillagerSpawning.snapshot().profiles().get(villager.profileId());
            return profile != null && profile.goals().contains("construction");
        }).stream().findAny().isPresent();
    }

    /** Pays for and places a project, then records it; new buildings receive their residents. */
    public static String complete(ServerLevel level, Project project) {
        PENDING.remove(project.settlementKey());
        var settlements = FabricSettlementState.get(level.getServer());
        var settlement = settlements.settlements().stream().filter(s -> key(s).equals(project.settlementKey())).findFirst().orElse(null);
        if (settlement == null) return "settlement no longer exists";
        var prepared = prepare(level, project);
        if (prepared.isEmpty()) return "site of " + project.label() + " is no longer free";
        var containers = containers(level, settlement);
        if (!project.cost().isEmpty()) {
            if (!missing(containers, project.cost()).isEmpty()) return "resources for " + project.label() + " are gone";
            consume(containers, project.cost());
        }
        BuildingPlacement.place(prepared.get(), BuildingPlacement.world(level), true);
        // Upgrade images hold only the changed blocks; keep the service points of the levels below.
        Map<String, List<LegacyBuildingPlan.Position>> points = new LinkedHashMap<>();
        if (project.upgrade()) settlement.buildings().stream().filter(b -> b.placement().origin().equals(project.origin())
                        && FabricBuildingState.planKey(b.placement().plan()).equals(FabricBuildingState.planKey(project.plan().id())))
                .findFirst().ifPresent(previous -> previous.placement().servicePoints().forEach((k, v) -> points.put(k, new ArrayList<>(v))));
        prepared.get().servicePoints().forEach((k, v) -> {
            var list = points.computeIfAbsent(k, ignored -> new ArrayList<>());
            for (var point : v) if (!list.contains(point)) list.add(point);
        });
        var placed = new FabricBuildingState.PlacedBuilding(level.dimension().identifier(), project.plan().id(), project.origin(), project.rotation(),
                points);
        FabricBuildingState.get(level.getServer()).record(placed);
        settlements.upsertBuilding(settlement, new FabricSettlementState.Building(placed, project.area(), false), project.upgrade());
        if (!project.upgrade()) {
            VillagerSpawning.populate(level, project.plan(), placed, new SplittableRandom(level.getSeed() ^ project.site().asLong()));
            org.millenaire.fabric.FabricVillageOwnership.get(level.getServer()).completeOrder(project.settlementKey(), project.plan().key());
        }
        return "built " + project.label();
    }
}
