package org.millenaire.fabric.content;

import java.util.*;

/**
 * Player-built ("custom") buildings of the original mod: {@code cultures/<culture>/custombuildings/*.txt}, one
 * line of {@code key:value} pairs separated by {@code ;}. Counted resources ({@code chest:1-5}, {@code field:10-30},
 * ...) are found by scanning what the player built around a sign; residents, tags and shop work as for plans.
 */
public final class CustomBuildings {
    /** Resources a custom building can ask for; the value is the scanned service point kind. */
    public static final List<String> RESOURCES = List.of("chest", "craft", "sign", "furnace", "field", "spawn", "sapling",
            "stall", "mining", "mudbrick", "sugar", "fishing", "cacao", "squid");
    public static final int DEFAULT_RADIUS = 6;

    public record Range(int min, int max) {
        public Range {
            if (min < 0 || max < min) throw new IllegalArgumentException("Invalid range " + min + "-" + max);
        }
        static Range parse(String text) {
            String[] parts = text.trim().split("-", 2);
            int min = Integer.parseInt(parts[0].trim());
            return new Range(min, parts.length == 2 ? Integer.parseInt(parts[1].trim()) : min);
        }
    }

    public record Definition(String culture, String key, String nativeName, String gameNameKey, Map<String, Range> resources,
                             int radius, List<String> tags, String shop, List<String> male, List<String> female,
                             String cropType, String spawnType) {
        public Definition {
            resources = Collections.unmodifiableMap(new LinkedHashMap<>(resources));
            tags = List.copyOf(tags); male = List.copyOf(male); female = List.copyOf(female);
        }

        public String id() { return culture + ":" + key; }

        /** The plan identifier the building is recorded under, alongside the PNG plans. */
        public String planId() { return culture + ":" + key + "_A0"; }

        /** A plan without image carrying the building's residents, tags and shop for the runtime. */
        public LegacyBuildingPlan plan() {
            Map<String, List<String>> parameters = new LinkedHashMap<>();
            parameters.put("custom", List.of("true"));
            if (!nativeName.isEmpty()) parameters.put("nativename", List.of(nativeName));
            if (!tags.isEmpty()) parameters.put("tag", tags);
            if (!shop.isEmpty()) parameters.put("shop", List.of(shop));
            if (!male.isEmpty()) parameters.put("male", List.of(String.join(",", male)));
            if (!female.isEmpty()) parameters.put("female", List.of(String.join(",", female)));
            int size = 2 * radius + 1;
            return new LegacyBuildingPlan(culture, key, 'A', 0, size, size, 0, null, parameters);
        }
    }

    private CustomBuildings() {}

    public static Definition parse(String culture, String key, String text) {
        Map<String, Range> resources = new LinkedHashMap<>();
        List<String> tags = new ArrayList<>(), male = new ArrayList<>(), female = new ArrayList<>();
        String nativeName = "", gameNameKey = "", shop = "", cropType = "", spawnType = "";
        int radius = DEFAULT_RADIUS;
        for (String pair : text.split(";")) {
            int colon = pair.indexOf(':');
            if (colon <= 0) continue;
            String name = pair.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = pair.substring(colon + 1).trim();
            switch (name) {
                case "native" -> nativeName = value;
                case "gamenamekey" -> gameNameKey = value;
                case "radius" -> radius = Integer.parseInt(value);
                case "tag" -> tags.add(value.toLowerCase(Locale.ROOT));
                case "shop" -> shop = value.toLowerCase(Locale.ROOT);
                case "male" -> male.add(value.toLowerCase(Locale.ROOT));
                case "female" -> female.add(value.toLowerCase(Locale.ROOT));
                case "croptype" -> cropType = value.toLowerCase(Locale.ROOT);
                case "spawntype" -> spawnType = value.toLowerCase(Locale.ROOT);
                case "moveinpriority" -> {}
                default -> { if (RESOURCES.contains(name)) resources.put(name, Range.parse(value)); }
            }
        }
        if (radius < 1 || radius > 32) throw new IllegalArgumentException("Invalid radius " + radius);
        return new Definition(culture, key.toLowerCase(Locale.ROOT), nativeName, gameNameKey, resources, radius, tags, shop, male, female, cropType, spawnType);
    }

    /** Every custom building of the catalog by {@code culture:key}; broken files are reported in {@code diagnostics}. */
    public static Map<String, Definition> all(LegacyContentCatalog catalog, List<String> diagnostics) {
        Map<String, Definition> result = new TreeMap<>();
        catalog.cultures().forEach((cultureId, culture) -> culture.category("custombuildings").forEach((path, document) -> {
            String key = path.substring(path.lastIndexOf('/') + 1, path.length() - 4);
            String text = String.join(";", document.lines().stream().map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("//")).toList());
            try {
                var definition = parse(cultureId, key, text);
                result.put(definition.id(), definition);
            } catch (RuntimeException exception) {
                diagnostics.add(cultureId + "/" + path + ": " + exception.getMessage());
            }
        }));
        return Collections.unmodifiableMap(result);
    }

    private static volatile LegacyContentCatalog cachedCatalog;
    private static volatile Map<String, LegacyBuildingPlan> cachedPlans = Map.of();

    /** Custom building plans of a catalog by plan id, computed once per catalog snapshot. */
    static Map<String, LegacyBuildingPlan> plans(LegacyContentCatalog catalog) {
        if (cachedCatalog != catalog) {
            Map<String, LegacyBuildingPlan> plans = new HashMap<>();
            all(catalog, new ArrayList<>()).values().forEach(definition -> plans.put(definition.planId(), definition.plan()));
            cachedPlans = Map.copyOf(plans);
            cachedCatalog = catalog;
        }
        return cachedPlans;
    }
}
