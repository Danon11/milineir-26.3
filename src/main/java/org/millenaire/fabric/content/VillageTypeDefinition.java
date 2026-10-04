package org.millenaire.fabric.content;

import java.util.List;

/** References retain repeated starting buildings: repeats mean multiple copies, not duplicates to remove. */
public record VillageTypeDefinition(String culture, String id, String name, String centre,
                                    List<String> startBuildings, List<String> coreBuildings,
                                    List<String> secondaryBuildings, List<String> playerBuildings,
                                    List<String> neverBuildings, List<String> biomes,
                                    LegacyDocument source) {
    public VillageTypeDefinition {
        startBuildings = List.copyOf(startBuildings); coreBuildings = List.copyOf(coreBuildings);
        secondaryBuildings = List.copyOf(secondaryBuildings); playerBuildings = List.copyOf(playerBuildings);
        neverBuildings = List.copyOf(neverBuildings); biomes = List.copyOf(biomes);
    }
    public static VillageTypeDefinition from(String culture, String id, LegacyDocument source) {
        return new VillageTypeDefinition(culture, id, source.first("name", id), source.first("centre", ""),
                source.values("start"), source.values("core"), source.values("secondary"),
                source.values("player"), source.values("never"), source.values("biome"), source);
    }
}
