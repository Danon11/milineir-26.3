package org.millenaire.fabric.content;

import java.util.*;
import org.millenaire.fabric.economy.LegacyGoodsCatalog;

/** Published as one immutable snapshot so readers never observe a partial reload. */
public record LegacyContentCatalog(Map<String, Culture> cultures, Map<String, LegacyDocument> globalDocuments,
                                   Map<String, LegacyBuildingPlan> plans, LegacyPalette palette,
                                   LegacyGoodsCatalog goods, List<String> diagnostics) {
    /** A PNG plan or a custom (player-built) building plan by id, or null. */
    public LegacyBuildingPlan plan(String id) {
        var plan = plans.get(id);
        return plan != null ? plan : CustomBuildings.plans(this).get(id);
    }

    public record Culture(String id, Map<String, LegacyDocument> documents) {
        public Culture { documents = Collections.unmodifiableMap(new LinkedHashMap<>(documents)); }
        public Map<String, VillageTypeDefinition> villageTypes() {
            Map<String, VillageTypeDefinition> result = new LinkedHashMap<>();
            category("villages").forEach((path, document) -> {
                String key = documentId(path);
                if (result.putIfAbsent(key, VillageTypeDefinition.from(id, key, document)) != null)
                    throw new IllegalStateException("Ambiguous village definition: " + id + ":" + key);
            });
            return Collections.unmodifiableMap(result);
        }
        public Map<String, VillagerTypeDefinition> villagerTypes() {
            Map<String, VillagerTypeDefinition> result = new LinkedHashMap<>();
            category("villagers").forEach((path, document) -> {
                String key = documentId(path);
                if (result.putIfAbsent(key, VillagerTypeDefinition.from(id, key, document)) != null)
                    throw new IllegalStateException("Ambiguous villager definition: " + id + ":" + key);
            });
            return Collections.unmodifiableMap(result);
        }
        private static String documentId(String path) {
            return path.substring(path.lastIndexOf('/') + 1, path.length() - 4).toLowerCase(Locale.ROOT);
        }
        public Map<String, LegacyDocument> category(String category) {
            Map<String, LegacyDocument> result = new LinkedHashMap<>();
            documents.forEach((path, document) -> {
                if (path.startsWith(category + "/")) result.put(path, document);
            });
            return Collections.unmodifiableMap(result);
        }
    }
    public LegacyContentCatalog {
        cultures = Collections.unmodifiableMap(new LinkedHashMap<>(cultures));
        globalDocuments = Collections.unmodifiableMap(new LinkedHashMap<>(globalDocuments));
        plans = Collections.unmodifiableMap(new LinkedHashMap<>(plans));
        Objects.requireNonNull(goods);
        diagnostics = List.copyOf(diagnostics);
    }
    public LegacyContentCatalog(Map<String, Culture> cultures, Map<String, LegacyDocument> globalDocuments,
                                Map<String, LegacyBuildingPlan> plans, LegacyPalette palette, List<String> diagnostics) {
        this(cultures, globalDocuments, plans, palette, LegacyGoodsCatalog.empty(), diagnostics);
    }
    public static LegacyContentCatalog empty() {
        return new LegacyContentCatalog(Map.of(), Map.of(), Map.of(), new LegacyPalette(Map.of()), List.of());
    }
    public int count(String category) {
        return cultures.values().stream().mapToInt(culture -> culture.category(category).size()).sum();
    }
}
