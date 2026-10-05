package org.millenaire.fabric.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.millenaire.fabric.FabricSettlementState;
import org.millenaire.fabric.content.LegacyDocument;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Keeps the chunks of every village a player has visited force-loaded, so villagers keep working while nobody is
 * nearby. The loaded area is the rectangle around the village's buildings plus a margin. Forced chunks persist with
 * the world; only chunks this class forced are ever released, when their village is gone or the feature is disabled
 * with {@code keep_discovered_villages_loaded=false} in {@code config.txt}.
 */
public final class VillageChunkLoader {
    static final int MARGIN = 16, DISCOVERY_DISTANCE = 32, CHECK_INTERVAL = 100, MAX_CHANGES_PER_CHECK = 32;
    private static volatile boolean enabled = true;

    private VillageChunkLoader() {}

    public static final class State extends SavedData {
        private static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().optionalFieldOf("discovered", List.of()).forGetter(s -> List.copyOf(s.discovered)),
                Codec.STRING.listOf().optionalFieldOf("forced", List.of()).forGetter(s -> List.copyOf(s.forced))
        ).apply(i, State::new));
        private static final SavedDataType<State> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath("millenaire", "village_chunks"),
                State::new, CODEC, DataFixTypes.LEVEL);
        private final Set<String> discovered = new TreeSet<>();
        private final Set<String> forced = new TreeSet<>();
        public State() {}
        private State(List<String> discovered, List<String> forced) { this.discovered.addAll(discovered); this.forced.addAll(forced); }
        public static State get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(TYPE); }
        public int discoveredCount() { return discovered.size(); }
        public int forcedCount() { return forced.size(); }
    }

    public static void configure(Path game) {
        boolean value = true;
        for (String root : List.of("millenaire", "millenaire-custom")) {
            Path file = game.resolve("mods").resolve(root).resolve("config.txt");
            if (!Files.isRegularFile(file)) continue;
            try {
                var values = LegacyDocument.read(file, root + "/config.txt").fields().get("keep_discovered_villages_loaded");
                if (values != null && !values.isEmpty()) value = !values.getLast().trim().equalsIgnoreCase("false");
            } catch (java.io.IOException ignored) {
                // Keep the default when the file cannot be read.
            }
        }
        enabled = value;
    }

    static String key(FabricSettlementState.Settlement settlement) {
        var origin = settlement.origin();
        return settlement.dimension() + "|" + origin.x() + "," + origin.y() + "," + origin.z();
    }

    /** Block rectangle {minX, minZ, maxX, maxZ} around the village's buildings, without margin. */
    static int[] area(FabricSettlementState.Settlement settlement) {
        var origin = settlement.origin();
        int minX = origin.x(), minZ = origin.z(), maxX = origin.x(), maxZ = origin.z();
        for (var building : settlement.buildings()) {
            var bounds = building.reservedArea();
            minX = Math.min(minX, bounds.minX()); minZ = Math.min(minZ, bounds.minZ());
            maxX = Math.max(maxX, bounds.maxX()); maxZ = Math.max(maxZ, bounds.maxZ());
        }
        return new int[] {minX, minZ, maxX, maxZ};
    }

    /** Chunk keys "dimension|chunkX,chunkZ" covering the village area plus the margin. */
    static Set<String> chunks(FabricSettlementState.Settlement settlement) {
        int[] area = area(settlement);
        Set<String> chunks = new TreeSet<>();
        for (int cx = (area[0] - MARGIN) >> 4; cx <= (area[2] + MARGIN) >> 4; cx++)
            for (int cz = (area[1] - MARGIN) >> 4; cz <= (area[3] + MARGIN) >> 4; cz++)
                chunks.add(settlement.dimension() + "|" + cx + "," + cz);
        return chunks;
    }

    public static void tick(MinecraftServer server) {
        if (server.overworld().getGameTime() % CHECK_INTERVAL != 0) return;
        var state = State.get(server);
        var settlements = FabricSettlementState.get(server).settlements();
        if (enabled) discover(server, state, settlements);
        Set<String> wanted = new TreeSet<>();
        if (enabled) for (var settlement : settlements) if (state.discovered.contains(key(settlement))) wanted.addAll(chunks(settlement));
        apply(server, state, wanted);
    }

    private static void discover(MinecraftServer server, State state, List<FabricSettlementState.Settlement> settlements) {
        for (var settlement : settlements) {
            String key = key(settlement);
            if (state.discovered.contains(key)) continue;
            int[] area = area(settlement);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.level().dimension().identifier().equals(settlement.dimension()) || player.isSpectator()) continue;
                if (player.getX() >= area[0] - DISCOVERY_DISTANCE && player.getX() <= area[2] + DISCOVERY_DISTANCE
                        && player.getZ() >= area[1] - DISCOVERY_DISTANCE && player.getZ() <= area[3] + DISCOVERY_DISTANCE) {
                    state.discovered.add(key);
                    state.setDirty();
                    org.slf4j.LoggerFactory.getLogger("Millenaire").info("{} discovered {}; keeping its {} chunks loaded",
                            player.getName().getString(), settlement.name(), chunks(settlement).size());
                    break;
                }
            }
        }
    }

    /** Marks the village nearest to the position in the level as discovered; returns its name, if any. */
    public static Optional<String> markNearest(ServerLevel level, double x, double z) {
        var dimension = level.dimension().identifier();
        var nearest = FabricSettlementState.get(level.getServer()).settlements().stream()
                .filter(settlement -> settlement.dimension().equals(dimension))
                .min(Comparator.comparingDouble(settlement -> Math.hypot(settlement.origin().x() - x, settlement.origin().z() - z)));
        nearest.ifPresent(settlement -> {
            var state = State.get(level.getServer());
            if (state.discovered.add(key(settlement))) state.setDirty();
        });
        return nearest.map(FabricSettlementState.Settlement::name);
    }

    /**
     * Forces missing chunks and releases the ones no longer wanted, a few per check so loading never stalls a tick.
     * A chunk that was already forced, for example with /forceload, is not recorded, so it is never released here.
     */
    private static void apply(MinecraftServer server, State state, Set<String> wanted) {
        int budget = MAX_CHANGES_PER_CHECK;
        for (String chunk : List.copyOf(state.forced)) {
            if (budget == 0) return;
            if (wanted.contains(chunk)) continue;
            setForced(server, chunk, false);
            budget--;
            state.forced.remove(chunk);
            state.setDirty();
        }
        for (String chunk : wanted) {
            if (budget == 0) return;
            if (state.forced.contains(chunk)) continue;
            if (!setForced(server, chunk, true)) continue; // already forced by someone else: a cheap no-op
            budget--;
            state.forced.add(chunk);
            state.setDirty();
        }
    }

    /** Returns whether the chunk's forced state changed. */
    private static boolean setForced(MinecraftServer server, String chunk, boolean forced) {
        int bar = chunk.lastIndexOf('|'), comma = chunk.lastIndexOf(',');
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(chunk.substring(0, bar))));
        if (level == null) return false;
        return level.setChunkForced(Integer.parseInt(chunk.substring(bar + 1, comma)), Integer.parseInt(chunk.substring(comma + 1)), forced);
    }
}
