package org.millenaire.fabric.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.fabric.FabricSettlementState;
import org.millenaire.fabric.FabricVillagerState;
import org.millenaire.fabric.MillenaireCommands;
import org.millenaire.fabric.content.LegacyBuildingPlan;
import org.millenaire.fabric.villager.VillagerProfile;
import org.millenaire.fabric.villager.VillagerSpawning;
import org.millenaire.fabric.villager.MillVillagerEntity;

import java.util.*;

/**
 * Keeps a village populated, following the original mod: mothers whose type names a {@code malechild} or
 * {@code femalechild} have children while the house has room, grown children take a free adult place of their
 * gender somewhere in the village, and residents who died come back after a while unless tagged
 * {@code noresurrect} (or a child of the village is growing up to take their place).
 */
public final class VillagePopulation {
    public static final int INTERVAL = 1200;
    /** About once per game day for a missing resident (20 checks per day). */
    static final int RESURRECT_CHANCE = 20;
    /** About every two game days for a house with a couple and room for a child. */
    static final int BIRTH_CHANCE = 40;

    /** A free adult place: the resident type the plan lists, in which building, and where its bed is. */
    public record Vacancy(String profileId, String building, LegacyBuildingPlan.Position bed) {}

    private VillagePopulation() {}

    public static void tick(MinecraftServer server) {
        if (server.overworld().getGameTime() % INTERVAL != 600) return;
        if (MillenaireCommands.contentCatalog() == null) return;
        Random random = new Random(server.overworld().getGameTime());
        for (var settlement : FabricSettlementState.get(server).settlements()) {
            ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, settlement.dimension()));
            if (level == null || !level.isLoaded(new BlockPos(settlement.origin().x(), settlement.origin().y(), settlement.origin().z()))) continue;
            step(level, settlement, random, false);
        }
    }

    public static String buildingKey(String plan, LegacyBuildingPlan.Position origin) {
        return plan + "@" + origin.x() + "," + origin.y() + "," + origin.z();
    }

    /** A building key without the plan's upgrade level, which changes as the building is upgraded. */
    public static String baseKey(String buildingKey) {
        int at = buildingKey.indexOf('@');
        if (at < 0) return buildingKey;
        String plan = buildingKey.substring(0, at);
        int end = plan.length();
        while (end > 0 && Character.isDigit(plan.charAt(end - 1))) end--;
        return plan.substring(0, end) + buildingKey.substring(at);
    }

    /** Living villager records of the settlement, by base building key. */
    private static Map<String, List<FabricVillagerState.Villager>> living(MinecraftServer server, FabricSettlementState.Settlement settlement) {
        Set<String> keys = new HashSet<>();
        for (var building : settlement.buildings()) keys.add(baseKey(buildingKey(building.placement().plan(), building.placement().origin())));
        Map<String, List<FabricVillagerState.Villager>> result = new HashMap<>();
        for (var record : FabricVillagerState.get(server).villagers())
            if (record.alive() && record.dimension().equals(settlement.dimension()) && keys.contains(baseKey(record.building())))
                result.computeIfAbsent(baseKey(record.building()), key -> new ArrayList<>()).add(record);
        return result;
    }

    /** Resident places of every building that no living villager of that type fills. */
    public static List<Vacancy> vacancies(MinecraftServer server, FabricSettlementState.Settlement settlement) {
        var catalog = MillenaireCommands.contentCatalog();
        var living = living(server, settlement);
        List<Vacancy> result = new ArrayList<>();
        for (var building : settlement.buildings()) {
            var placed = building.placement();
            var plan = catalog.plan(placed.plan());
            if (plan == null) continue;
            String key = buildingKey(placed.plan(), placed.origin());
            Map<String, Integer> present = new HashMap<>();
            for (var record : living.getOrDefault(baseKey(key), List.of())) present.merge(record.culture() + "/" + record.type(), 1, Integer::sum);
            for (var resident : VillagerSpawning.residents(plan, placed)) {
                if (present.merge(resident.profileId(), -1, Integer::sum) >= 0) continue;
                result.add(new Vacancy(resident.profileId(), key, resident.position()));
            }
        }
        return result;
    }

    /**
     * One population pass: at most one resurrection or birth. With {@code force} the chances are skipped
     * (admin command). Returns what happened.
     */
    public static String step(ServerLevel level, FabricSettlementState.Settlement settlement, Random random, boolean force) {
        var server = level.getServer();
        var profiles = VillagerSpawning.snapshot().profiles();
        var living = living(server, settlement);
        List<FabricVillagerState.Villager> children = living.values().stream().flatMap(List::stream)
                .filter(record -> Optional.ofNullable(profiles.get(record.culture() + "/" + record.type())).map(VillagerProfile::child).orElse(false))
                .toList();

        // Marriages: an unmarried man and woman of the same house marry.
        for (var household : living.values()) {
            List<MillVillagerEntity> single = new ArrayList<>();
            for (var record : household)
                if (level.getEntity(UUID.fromString(record.id())) instanceof MillVillagerEntity villager && villager.spouse().isEmpty()
                        && !villager.isChildVillager()) single.add(villager);
            var man = single.stream().filter(v -> !v.profile().map(VillagerProfile::female).orElse(false)).findFirst();
            var woman = single.stream().filter(v -> v.profile().map(VillagerProfile::female).orElse(false)).findFirst();
            if (man.isPresent() && woman.isPresent()) {
                MillVillagerEntity.marry(man.get(), woman.get());
                VillagerSpawning.record(level, woman.get(), woman.get().profile().orElseThrow(), true);
            }
        }

        // Resurrection: a dead resident comes back, unless a child of the same gender will grow into the place.
        for (Vacancy vacancy : vacancies(server, settlement)) {
            VillagerProfile profile = profiles.get(vacancy.profileId());
            if (profile == null || profile.tags().contains("noresurrect") || (!force && random.nextInt(RESURRECT_CHANCE) != 0)) continue;
            String gender = profile.female() ? "female" : "male";
            if (children.stream().anyMatch(child -> child.gender().equals(gender))) continue;
            var bed = vacancy.bed();
            var back = VillagerSpawning.spawn(level, new BlockPos(bed.x(), bed.y(), bed.z()), profile, vacancy.building(), new SplittableRandom(random.nextLong()));
            return "resurrected " + profile.id() + " " + back.getName().getString(); // one change per pass
        }

        // Births: a mother and a man in the same house, the house not over its beds, and no other child there yet.
        var catalog = MillenaireCommands.contentCatalog();
        for (var building : settlement.buildings()) {
            var placed = building.placement();
            var plan = catalog.plan(placed.plan());
            if (plan == null) continue;
            String key = buildingKey(placed.plan(), placed.origin());
            var household = living.getOrDefault(baseKey(key), List.of());
            if (household.isEmpty() || household.stream().anyMatch(children::contains)) continue;
            int beds = Math.max(placed.servicePoints().getOrDefault("sleepingPos", List.of()).size(), VillagerSpawning.residents(plan, placed).size());
            if (household.size() >= beds + 1) continue;
            var motherRecord = household.stream().filter(record -> Optional.ofNullable(profiles.get(record.culture() + "/" + record.type()))
                    .map(VillagerProfile::canHaveChildren).orElse(false)).findFirst();
            Optional<VillagerProfile> mother = motherRecord.map(record -> profiles.get(record.culture() + "/" + record.type()));
            Optional<FabricVillagerState.Villager> fatherRecord = household.stream().filter(record -> record.gender().equals("male")
                    && !Optional.ofNullable(profiles.get(record.culture() + "/" + record.type())).map(VillagerProfile::child).orElse(true)).findFirst();
            // Children are born to married couples: the father is the mother's husband when she is loaded.
            if (motherRecord.isPresent() && level.getEntity(UUID.fromString(motherRecord.get().id())) instanceof MillVillagerEntity wife) {
                if (wife.spouse().isEmpty()) continue;
                fatherRecord = household.stream().filter(record -> record.id().equals(wife.spouse())).findFirst();
            }
            if (mother.isEmpty() || fatherRecord.isEmpty() || (!force && random.nextInt(BIRTH_CHANCE) != 0)) continue;
            String type = childType(mother.get(), random);
            VillagerProfile child = type.isEmpty() ? null : profiles.get(mother.get().culture() + "/" + type);
            if (child == null) continue;
            var beds2 = placed.servicePoints().getOrDefault("sleepingPos", List.of());
            var at = beds2.isEmpty() ? new LegacyBuildingPlan.Position(placed.origin().x(), placed.origin().y() + 1, placed.origin().z())
                    : beds2.get(random.nextInt(beds2.size()));
            var born = VillagerSpawning.spawn(level, new BlockPos(at.x(), at.y(), at.z()), child, key, new SplittableRandom(random.nextLong()));
            // The child carries the father's family name and knows its parents.
            String fatherName = fatherRecord.get().name();
            String family = fatherName.contains(" ") ? fatherName.substring(fatherName.indexOf(' ') + 1) : born.familyName();
            born.setFamily(family, motherRecord.get().id(), fatherRecord.get().id());
            VillagerSpawning.record(level, born, child, true);
            return "born " + child.id() + " " + born.getName().getString() + " in " + key;
        }
        return "no change";
    }

    static String childType(VillagerProfile mother, Random random) {
        boolean boy = random.nextBoolean();
        String first = boy ? mother.maleChild() : mother.femaleChild();
        return first.isEmpty() ? (boy ? mother.femaleChild() : mother.maleChild()) : first;
    }

    /** The settlement holding a building key, if any. */
    public static Optional<FabricSettlementState.Settlement> settlementOf(MinecraftServer server, net.minecraft.resources.Identifier dimension, String buildingKey) {
        return FabricSettlementState.get(server).settlements().stream().filter(settlement -> settlement.dimension().equals(dimension)
                && settlement.buildings().stream().anyMatch(b -> baseKey(buildingKey(b.placement().plan(), b.placement().origin())).equals(baseKey(buildingKey)))).findFirst();
    }
}
