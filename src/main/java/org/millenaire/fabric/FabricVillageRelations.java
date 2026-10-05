package org.millenaire.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

/**
 * Diplomacy: the relation between two villages (by settlement key), from -100 (war) to 100 (alliance). A pair
 * is stored once, under its keys in sorted order.
 */
public final class FabricVillageRelations extends SavedData {
    public static final int MIN = -100, MAX = 100;
    private static final Codec<FabricVillageRelations> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("relations", Map.of()).forGetter(s -> s.relations)
    ).apply(i, FabricVillageRelations::new));
    private static final SavedDataType<FabricVillageRelations> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("millenaire", "relations"), FabricVillageRelations::new, CODEC, DataFixTypes.LEVEL);

    private final Map<String, Integer> relations = new TreeMap<>();

    public FabricVillageRelations() {}
    private FabricVillageRelations(Map<String, Integer> relations) { this.relations.putAll(relations); }

    public static FabricVillageRelations get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(TYPE); }
    static Codec<FabricVillageRelations> codec() { return CODEC; }

    static String pair(String a, String b) { return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a; }

    public boolean known(String a, String b) { return relations.containsKey(pair(a, b)); }

    public int get(String a, String b) { return relations.getOrDefault(pair(a, b), 0); }

    public int set(String a, String b, int value) {
        int clamped = Math.max(MIN, Math.min(MAX, value));
        relations.put(pair(a, b), clamped);
        setDirty();
        return clamped;
    }

    public int add(String a, String b, int delta) { return set(a, b, get(a, b) + delta); }

    /** Relations of one village with every village it knows. */
    public Map<String, Integer> of(String village) {
        Map<String, Integer> result = new TreeMap<>();
        relations.forEach((pair, value) -> {
            String[] keys = pair.split("\\|", 2);
            if (keys[0].equals(village)) result.put(keys[1], value);
            else if (keys[1].equals(village)) result.put(keys[0], value);
        });
        return result;
    }

    public static String describe(int relation) {
        if (relation <= -60) return "at war";
        if (relation <= -20) return "hostile";
        if (relation < 20) return "neutral";
        if (relation < 60) return "friendly";
        return "allied";
    }
}
