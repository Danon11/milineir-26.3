package org.millenaire.fabric.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.millenaire.fabric.FabricSettlementState;
import org.millenaire.fabric.MillenaireCommands;
import org.millenaire.fabric.content.LegacyBuildingPlan;
import org.millenaire.fabric.content.LegacyDocument;
import org.millenaire.fabric.content.VillageTypeDefinition;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Natural village generation near players, following the original config: one candidate site per region of
 * {@code min_village_distance}, no villages inside {@code spawn_protection_radius} of the world spawn, a village
 * type chosen by biome and weight, and a dry, reasonably flat site.
 */
public final class WorldVillageGenerator {
    public record Config(boolean enabled, int minDistance, int spawnProtection) {
        public static Config load(Path game) {
            Map<String, String> values = new HashMap<>();
            for (String root : List.of("millenaire", "millenaire-custom")) {
                Path file = game.resolve("mods").resolve(root).resolve("config.txt");
                if (!Files.isRegularFile(file)) continue;
                try {
                    LegacyDocument.read(file, root + "/config.txt").fields().forEach((key, list) -> values.put(key, list.getLast().trim()));
                } catch (java.io.IOException ignored) {
                    // Keep the defaults when the file cannot be read.
                }
            }
            return new Config(!values.getOrDefault("generate_villages", "true").equalsIgnoreCase("false"),
                    parse(values.get("min_village_distance"), 600), parse(values.get("spawn_protection_radius"), 150));
        }
        private static int parse(String value, int fallback) {
            try { return value == null ? fallback : Math.max(64, Integer.parseInt(value)); } catch (NumberFormatException e) { return fallback; }
        }
    }

    /** Regions already tried, so a failed site is not retried on every tick. */
    public static final class State extends SavedData {
        private static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().optionalFieldOf("attempted", List.of()).forGetter(s -> List.copyOf(s.attempted))
        ).apply(i, State::new));
        private static final SavedDataType<State> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath("millenaire", "worldgen"),
                State::new, CODEC, DataFixTypes.LEVEL);
        private final Set<String> attempted = new TreeSet<>();
        public State() {}
        private State(List<String> attempted) { this.attempted.addAll(attempted); }
        static State get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(TYPE); }
        boolean tryMark(String region) { boolean added = attempted.add(region); if (added) setDirty(); return added; }
    }

    /** Modern biome paths for the legacy biome names used by village types. */
    static final Map<String, List<String>> LEGACY_BIOMES = Map.ofEntries(
            Map.entry("plains", List.of("plains")), Map.entry("sunflower plains", List.of("sunflower_plains")),
            Map.entry("forest", List.of("forest")), Map.entry("forested hills", List.of("forest", "windswept_forest")),
            Map.entry("birch forest", List.of("birch_forest")), Map.entry("birch forest m", List.of("old_growth_birch_forest")),
            Map.entry("birch forested hills", List.of("birch_forest", "old_growth_birch_forest")),
            Map.entry("roofed forest", List.of("dark_forest")), Map.entry("taiga", List.of("taiga")), Map.entry("taiga hills", List.of("taiga")),
            Map.entry("taiga m", List.of("taiga")), Map.entry("mega taiga", List.of("old_growth_pine_taiga")),
            Map.entry("mega spruce taiga", List.of("old_growth_spruce_taiga")), Map.entry("cold taiga", List.of("snowy_taiga")),
            Map.entry("cold taiga hills", List.of("snowy_taiga")), Map.entry("cold taiga m", List.of("snowy_taiga")),
            Map.entry("ice plains", List.of("snowy_plains")), Map.entry("ice plains spikes", List.of("ice_spikes")),
            Map.entry("frozen river", List.of("frozen_river")), Map.entry("extreme hills", List.of("windswept_hills")),
            Map.entry("extreme hills+", List.of("windswept_forest")), Map.entry("extreme hills+ m", List.of("windswept_gravelly_hills")),
            Map.entry("savanna", List.of("savanna")), Map.entry("savanna m", List.of("windswept_savanna")),
            Map.entry("savanna plateau", List.of("savanna_plateau")), Map.entry("desert", List.of("desert")), Map.entry("desert m", List.of("desert")),
            Map.entry("jungle", List.of("jungle")), Map.entry("jungleedge", List.of("sparse_jungle")), Map.entry("jungleedge m", List.of("sparse_jungle")),
            Map.entry("mesa", List.of("badlands")), Map.entry("mesa m", List.of("eroded_badlands")), Map.entry("badlands", List.of("badlands")),
            Map.entry("mesa plateau", List.of("wooded_badlands")), Map.entry("mesa plateau f", List.of("wooded_badlands")),
            Map.entry("mesa plateau m", List.of("wooded_badlands")), Map.entry("mesa plateau f m", List.of("wooded_badlands")),
            Map.entry("swampland", List.of("swamp")), Map.entry("swampland m", List.of("mangrove_swamp")),
            Map.entry("meadow", List.of("meadow")), Map.entry("grove", List.of("grove")), Map.entry("cherry grove", List.of("cherry_grove")));

    private static final int SITE_MIN = 48, SITE_MAX = 192, FLATNESS = 24, CHECK_INTERVAL = 400, SEARCH = 128, SEARCH_STEP = 32, VILLAGE_AREA = 96;
    private static volatile Config config = new Config(true, 600, 150);

    private WorldVillageGenerator() {}

    public static void configure(Path game) { config = Config.load(game); }

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (!config.enabled() || level.getGameTime() % CHECK_INTERVAL != 0) return;
        for (ServerPlayer player : level.players()) {
            int region = config.minDistance();
            int rx = Math.floorDiv(player.getBlockX(), region), rz = Math.floorDiv(player.getBlockZ(), region);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) tryRegion(level, player, rx + dx, rz + dz, region);
        }
    }

    private static void tryRegion(ServerLevel level, ServerPlayer player, int rx, int rz, int region) {
        long seed = level.getSeed() ^ (rx * 341873128712L + rz * 132897987541L) ^ 0x4D696C6C656E6169L;
        var random = new Random(seed);
        int margin = region / 6;
        int x = rx * region + margin + random.nextInt(region - 2 * margin);
        int z = rz * region + margin + random.nextInt(region - 2 * margin);
        double distance = Math.hypot(player.getX() - x, player.getZ() - z);
        if (distance < SITE_MIN || distance > SITE_MAX) return;
        var spawn = level.getRespawnData().pos();
        if (Math.hypot(spawn.getX() - x, spawn.getZ() - z) < config.spawnProtection()) return;
        if (!loaded(level, x, z, SEARCH + 40)) return; // try again once the surroundings are loaded
        if (!State.get(level.getServer()).tryMark(level.dimension().identifier() + "/" + rx + "," + rz)) return;
        String result = generate(level, x, z, random);
        org.slf4j.LoggerFactory.getLogger("Millenaire").info("Village site {} {}: {}", x, z, result);
    }

    /** True when every chunk within the radius of the column is loaded. */
    static boolean loaded(ServerLevel level, int x, int z, int radius) {
        for (int cx = (x - radius) >> 4; cx <= (x + radius) >> 4; cx++)
            for (int cz = (z - radius) >> 4; cz <= (z + radius) >> 4; cz++)
                if (!level.hasChunk(cx, cz)) return false;
        return true;
    }

    private static int terrainRange(ServerLevel level, int x, int z) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, wet = 0;
        for (int dx = -32; dx <= 32; dx += 8) for (int dz = -32; dz <= 32; dz += 8) {
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
            if (!level.getFluidState(new BlockPos(x + dx, h - 1, z + dz)).isEmpty()) wet++;
            min = Math.min(min, h); max = Math.max(max, h);
        }
        return wet > 8 ? Integer.MAX_VALUE - 1 : max - min;
    }

    /** Attempts a village near the column; returns what happened. */
    public static String generate(ServerLevel level, int x, int z, Random random) {
        for (var settlement : FabricSettlementState.get(level.getServer()).settlements())
            if (settlement.dimension().equals(level.dimension().identifier())
                    && Math.hypot(settlement.origin().x() - x, settlement.origin().z() - z) < config.minDistance())
                return "too close to " + settlement.name();
        // Search the surroundings for the flattest dry site; the layout uses one ground level.
        int bestX = x, bestZ = z, bestRange = Integer.MAX_VALUE, ground = 0;
        for (int sx = x - SEARCH; sx <= x + SEARCH; sx += SEARCH_STEP)
            for (int sz = z - SEARCH; sz <= z + SEARCH; sz += SEARCH_STEP) {
                if (!loaded(level, sx, sz, 40)) continue;
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
                if (!level.getFluidState(new BlockPos(sx, h - 1, sz)).isEmpty()) continue;
                int range = terrainRange(level, sx, sz);
                if (range < bestRange) { bestRange = range; bestX = sx; bestZ = sz; ground = h; }
            }
        if (bestRange == Integer.MAX_VALUE) return "water or unloaded";
        if (bestRange > FLATNESS) return "too steep (" + bestRange + ")";
        x = bestX;
        z = bestZ;
        // Never generate chunks synchronously: the whole village area must already be loaded.
        if (!loaded(level, x, z, VILLAGE_AREA)) return "area not loaded yet";
        var biome = level.getBiome(new BlockPos(x, ground, z)).unwrapKey().map(key -> key.identifier().getPath()).orElse("");
        var type = pickType(biome, random);
        if (type.isEmpty()) return "no village type for biome " + biome;
        try {
            var result = VillageFounder.found(level, MillenaireCommands.contentCatalog(), type.get(),
                    new LegacyBuildingPlan.Position(x, ground, z), random.nextLong(), true, true);
            return result.placed() ? "founded " + type.get().culture() + ":" + type.get().id() + " with " + result.residents() + " residents"
                    : "blocked: " + result.issues().stream().limit(3).toList();
        } catch (java.io.IOException | RuntimeException exception) {
            return "failed: " + exception.getMessage();
        }
    }

    static Optional<VillageTypeDefinition> pickType(String biome, Random random) {
        List<VillageTypeDefinition> candidates = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        for (var culture : MillenaireCommands.contentCatalog().cultures().values())
            for (var type : culture.villageTypes().values()) {
                var source = type.source();
                if (source.first("playercontrolled", "false").trim().equalsIgnoreCase("true")) continue;
                if (source.first("type", "").trim().equalsIgnoreCase("marvel")) continue;
                int weight;
                try { weight = Integer.parseInt(source.first("weight", "10").trim()); } catch (NumberFormatException e) { weight = 10; }
                if (weight <= 0) continue;
                boolean matches = type.biomes().stream().map(b -> b.trim().toLowerCase(Locale.ROOT))
                        .anyMatch(name -> LEGACY_BIOMES.getOrDefault(name, List.of(name.replace(' ', '_'))).contains(biome));
                if (!matches) continue;
                candidates.add(type);
                weights.add(weight);
            }
        int total = weights.stream().mapToInt(Integer::intValue).sum();
        if (total == 0) return Optional.empty();
        int pick = random.nextInt(total);
        for (int i = 0; i < candidates.size(); i++) {
            pick -= weights.get(i);
            if (pick < 0) return Optional.of(candidates.get(i));
        }
        return Optional.empty();
    }
}
