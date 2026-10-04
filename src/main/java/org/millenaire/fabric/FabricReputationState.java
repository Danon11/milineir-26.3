package org.millenaire.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

/** Player reputation per village (keyed by settlement or building), raised by trade and quests. */
public final class FabricReputationState extends SavedData {
    private static final Codec<Map<String, Map<String, Integer>>> TABLE =
            Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.INT));
    private static final Codec<FabricReputationState> CODEC = RecordCodecBuilder.create(i -> i.group(
            TABLE.optionalFieldOf("reputation", Map.of()).forGetter(s -> s.reputation)
    ).apply(i, FabricReputationState::new));
    private static final SavedDataType<FabricReputationState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("millenaire", "reputation"), FabricReputationState::new, CODEC, DataFixTypes.LEVEL);
    private final Map<String, Map<String, Integer>> reputation = new TreeMap<>();

    public FabricReputationState() {}
    private FabricReputationState(Map<String, Map<String, Integer>> values) {
        values.forEach((village, players) -> reputation.put(village, new TreeMap<>(players)));
    }

    public static FabricReputationState get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(TYPE); }
    static Codec<FabricReputationState> codec() { return CODEC; }

    public int get(String village, UUID player) {
        return reputation.getOrDefault(village, Map.of()).getOrDefault(player.toString(), 0);
    }

    public int add(String village, UUID player, int delta) {
        var players = reputation.computeIfAbsent(village, ignored -> new TreeMap<>());
        long next = (long) players.getOrDefault(player.toString(), 0) + delta;
        int clamped = (int) Math.max(Integer.MIN_VALUE / 2, Math.min(Integer.MAX_VALUE / 2, next));
        players.put(player.toString(), clamped);
        setDirty();
        return clamped;
    }
}
