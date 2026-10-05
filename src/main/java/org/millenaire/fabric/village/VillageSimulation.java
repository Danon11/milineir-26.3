package org.millenaire.fabric.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.millenaire.fabric.FabricSettlementState;
import org.millenaire.fabric.FabricVillagerState;
import org.millenaire.fabric.villager.VillagerSpawning;

import java.util.*;

/**
 * Villages live on while nobody is near: the game only runs loaded chunks, so each village remembers when it
 * last ran, and when it is loaded again after more than two days it catches up on the days it missed (up to a
 * week): its woodcutters, miners and shepherds bring in a day's work each, it builds one affordable project
 * per day, and its families grow.
 */
public final class VillageSimulation extends SavedData {
    static final long DAY = 24000, CATCH_UP_AFTER = 2 * DAY;
    static final int MAX_DAYS = 7, WOOD_PER_DAY = 24, STONE_PER_DAY = 24, WOOL_PER_DAY = 8;

    private static final Codec<VillageSimulation> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("last_run", Map.of()).forGetter(s -> s.lastRun)
    ).apply(i, VillageSimulation::new));
    private static final SavedDataType<VillageSimulation> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("millenaire", "village_clock"), VillageSimulation::new, CODEC, DataFixTypes.LEVEL);

    private final Map<String, Long> lastRun = new TreeMap<>();

    public VillageSimulation() {}
    private VillageSimulation(Map<String, Long> values) { lastRun.putAll(values); }

    public static VillageSimulation get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(TYPE); }

    /** Called with the growth tick for every loaded village. */
    static void seen(ServerLevel level, FabricSettlementState.Settlement settlement) {
        var clock = get(level.getServer());
        String key = VillageGrowth.key(settlement);
        long now = level.getGameTime();
        Long last = clock.lastRun.get(key);
        clock.lastRun.put(key, now);
        clock.setDirty();
        if (last == null || now - last < CATCH_UP_AFTER) return;
        int days = (int) Math.min(MAX_DAYS, (now - last) / DAY);
        String report = catchUp(level, settlement, days);
        org.slf4j.LoggerFactory.getLogger("Millenaire").info("{} caught up {} days: {}", settlement.name(), days, report);
    }

    /** Simulates {@code days} days of work and growth; returns what happened. */
    public static String catchUp(ServerLevel level, FabricSettlementState.Settlement settlement, int days) {
        var profiles = VillagerSpawning.snapshot().profiles();
        int woodcutters = 0, miners = 0, shepherds = 0, builders = 0;
        for (var record : FabricVillagerState.get(level.getServer()).inSettlement(settlement)) {
            if (!record.alive()) continue;
            var profile = profiles.get(record.culture() + "/" + record.type());
            if (profile == null || profile.child()) continue;
            if (profile.goals().stream().anyMatch(goal -> goal.contains("chop") || goal.contains("lumber"))) woodcutters++;
            if (profile.goals().stream().anyMatch(goal -> goal.contains("mine") || goal.contains("quarry") || goal.contains("stone"))) miners++;
            if (profile.goals().contains("shearsheep")) shepherds++;
            if (profile.goals().contains("construction")) builders++; // builders fetch wood and stone between sites
        }
        List<String> built = new ArrayList<>();
        for (int day = 0; day < days; day++) {
            List<ItemStack> produce = new ArrayList<>();
            stacks(produce, Items.OAK_LOG, woodcutters * WOOD_PER_DAY + builders * WOOD_PER_DAY / 2);
            stacks(produce, Items.COBBLESTONE, miners * STONE_PER_DAY + builders * STONE_PER_DAY / 2);
            stacks(produce, Items.WOOL.white(), shepherds * WOOL_PER_DAY);
            var current = FabricSettlementState.get(level.getServer()).settlements().stream()
                    .filter(s -> VillageGrowth.key(s).equals(VillageGrowth.key(settlement))).findFirst().orElse(settlement);
            VillageRaids.put(level, current, produce); // what does not fit is lost, as a full storehouse would
            String result = VillageGrowth.evaluate(level, current, false, true);
            if (result.startsWith("built")) built.add(result.substring(6));
            VillagePopulation.step(level, current, new Random(level.getGameTime() + day), day % 2 == 1);
        }
        return built.isEmpty() ? "nothing built" : "built " + String.join(", ", built);
    }

    private static void stacks(List<ItemStack> into, net.minecraft.world.item.Item item, int count) {
        while (count > 0) {
            int part = Math.min(64, count);
            into.add(new ItemStack(item, part));
            count -= part;
        }
    }
}
