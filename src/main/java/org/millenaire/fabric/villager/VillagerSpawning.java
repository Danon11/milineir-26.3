package org.millenaire.fabric.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import org.millenaire.fabric.FabricBuildingState;
import org.millenaire.fabric.FabricVillagerState;
import org.millenaire.fabric.content.LegacyBuildingPlan;
import org.millenaire.fabric.content.LegacyContentCatalog;

import java.util.*;
import java.util.random.RandomGenerator;

/** Villager profiles of the active content catalog and the server-side spawning of residents. */
public final class VillagerSpawning {
    public record Snapshot(Map<String, VillagerProfile> profiles, Map<String, Map<String, List<String>>> nameLists, List<String> diagnostics) {
        public Snapshot {
            profiles = Map.copyOf(profiles);
            nameLists = Map.copyOf(nameLists);
            diagnostics = List.copyOf(diagnostics);
        }
        public static Snapshot of(LegacyContentCatalog catalog) {
            List<String> diagnostics = new ArrayList<>();
            Map<String, VillagerProfile> profiles = VillagerProfile.all(catalog, diagnostics);
            Map<String, Map<String, List<String>>> names = new HashMap<>();
            catalog.cultures().forEach((id, culture) -> names.put(id, VillagerProfile.nameLists(culture)));
            return new Snapshot(profiles, names, diagnostics);
        }
    }

    /** One resident slot of a building plan: {@code male=}/{@code female=} lists of the plan's culture. */
    public record Resident(String profileId, LegacyBuildingPlan.Position position) {}

    private static volatile Snapshot snapshot = new Snapshot(Map.of(), Map.of(), List.of());

    private VillagerSpawning() {}

    public static void update(LegacyContentCatalog catalog) { snapshot = Snapshot.of(catalog); }
    public static Snapshot snapshot() { return snapshot; }

    /** Resident types listed by the plan, each assigned a sleeping position when the plan has enough. */
    public static List<Resident> residents(LegacyBuildingPlan plan, FabricBuildingState.PlacedBuilding placed) {
        List<String> types = new ArrayList<>();
        for (String key : List.of("male", "female"))
            for (String value : plan.parameters().getOrDefault(key, List.of()))
                for (String type : value.split(",")) if (!type.isBlank()) types.add(plan.culture() + "/" + type.trim().toLowerCase(Locale.ROOT));
        List<LegacyBuildingPlan.Position> beds = placed.servicePoints().getOrDefault("sleepingPos", List.of());
        LegacyBuildingPlan.Position fallback = new LegacyBuildingPlan.Position(placed.origin().x(), placed.origin().y() + 1, placed.origin().z());
        List<Resident> result = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) result.add(new Resident(types.get(i), i < beds.size() ? beds.get(i) : fallback));
        return result;
    }

    public static MillVillagerEntity spawn(ServerLevel level, BlockPos pos, VillagerProfile profile, String building, RandomGenerator random) {
        var names = snapshot.nameLists().getOrDefault(profile.culture(), Map.of());
        MillVillagerEntity villager = VillagerContent.VILLAGER.create(level, EntitySpawnReason.STRUCTURE);
        if (villager == null) throw new IllegalStateException("Could not create villager entity");
        villager.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
        var appearance = profile.roll(random, names);
        villager.initialize(profile, appearance, building);
        if (!level.addFreshEntity(villager)) throw new IllegalStateException("Villager could not be added to the level");
        record(level, villager, profile, true);
        return villager;
    }

    /** Writes the persistent record of a villager: identity, home building and whether it is alive. */
    public static void record(ServerLevel level, MillVillagerEntity villager, VillagerProfile profile, boolean alive) {
        BlockPos pos = villager.blockPosition();
        String name = villager.familyName().isEmpty() ? villager.firstName() : villager.firstName() + " " + villager.familyName();
        FabricVillagerState.get(level.getServer()).upsert(new FabricVillagerState.Villager(villager.getStringUUID(),
                level.dimension().identifier(), new LegacyBuildingPlan.Position(pos.getX(), pos.getY(), pos.getZ()),
                profile.culture(), profile.type(), name.isBlank() ? profile.type() : name, profile.female() ? "female" : "male",
                villager.building(), alive));
    }

    /** Farm animals for the legacy spawn markers: two adults per marker, as a starting herd. */
    private static final Map<String, String> ANIMAL_MARKERS = Map.of("cowspawn", "cow", "pigspawn", "pig", "sheepspawn", "sheep",
            "chickenspawn", "chicken", "squidspawn", "squid", "wolfspawn", "wolf", "polarbearspawn", "polar_bear");

    public static int spawnAnimals(ServerLevel level, FabricBuildingState.PlacedBuilding placed) {
        int spawned = 0;
        for (var marker : ANIMAL_MARKERS.entrySet())
            for (var point : placed.servicePoints().getOrDefault(marker.getKey(), List.of())) {
                var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(marker.getValue()));
                for (int i = 0; i < 2; i++) {
                    var entity = type.create(level, EntitySpawnReason.STRUCTURE);
                    if (entity == null) continue;
                    entity.snapTo(point.x() + 0.5, point.y(), point.z() + 0.5, level.getRandom().nextFloat() * 360F, 0F);
                    if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setPersistenceRequired();
                    if (level.addFreshEntity(entity)) spawned++;
                }
            }
        return spawned;
    }

    /** Spawns the residents of a newly placed building; unknown resident types are returned as issues. */
    public static List<String> populate(ServerLevel level, LegacyBuildingPlan plan, FabricBuildingState.PlacedBuilding placed, RandomGenerator random) {
        spawnAnimals(level, placed);
        return spawnResidents(level, plan, placed, random);
    }

    /** Spawns the residents a plan lists, without the starting herd (player-built buildings bring their own). */
    public static List<String> spawnResidents(ServerLevel level, LegacyBuildingPlan plan, FabricBuildingState.PlacedBuilding placed, RandomGenerator random) {
        List<String> issues = new ArrayList<>();
        for (Resident resident : residents(plan, placed)) {
            VillagerProfile profile = snapshot.profiles().get(resident.profileId());
            if (profile == null) {
                issues.add(plan.id() + ": unknown resident type " + resident.profileId());
                continue;
            }
            var p = resident.position();
            spawn(level, new BlockPos(p.x(), p.y(), p.z()), profile, plan.id() + "@" + placed.origin().x() + "," + placed.origin().y() + "," + placed.origin().z(), random);
        }
        return issues;
    }
}
