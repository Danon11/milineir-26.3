package org.millenaire.fabric.quest;

import org.millenaire.fabric.content.LegacyContentCatalog;

import java.util.*;

/**
 * Quest definitions from the shared {@code quests/} documents, checked against the loaded cultures,
 * villager types and goods aliases. A quest whose references cannot be resolved is rejected with a
 * diagnostic instead of being offered with missing villagers or items.
 */
public record QuestCatalog(Map<String, QuestDefinition> quests, List<String> diagnostics) {
    private static final String PREFIX = "quests/";
    /**
     * Goods checked by a rule rather than an itemlist alias. The Sadhu quest asks the player to
     * "show an enchanted sword": any sword carrying at least one enchantment satisfies it.
     */
    public static final Set<String> SPECIAL_GOODS = Set.of("enchantedsword");

    public QuestCatalog {
        quests = Collections.unmodifiableMap(new LinkedHashMap<>(quests));
        diagnostics = List.copyOf(diagnostics);
    }

    public static QuestCatalog empty() { return new QuestCatalog(Map.of(), List.of()); }

    public static QuestCatalog from(LegacyContentCatalog catalog) {
        Map<String, Set<String>> villagerTypes = new HashMap<>();
        catalog.cultures().forEach((id, culture) -> {
            Set<String> types = new HashSet<>();
            culture.category("villagers").keySet().forEach(path ->
                    types.add(path.substring(path.lastIndexOf('/') + 1, path.length() - 4).toLowerCase(Locale.ROOT)));
            villagerTypes.put(id, types);
        });
        Set<String> goods = new HashSet<>(catalog.goods().goods().keySet());
        goods.addAll(SPECIAL_GOODS);
        Map<String, QuestDefinition> quests = new TreeMap<>();
        List<String> diagnostics = new ArrayList<>();
        Map<String, String> keys = new HashMap<>();
        catalog.globalDocuments().forEach((relative, document) -> {
            if (!relative.startsWith(PREFIX) || !relative.endsWith(".txt")) return;
            String path = relative.substring(PREFIX.length(), relative.length() - 4);
            var result = QuestDefinitionParser.parse(path, document);
            diagnostics.addAll(result.diagnostics());
            if (result.quest().isEmpty()) return;
            QuestDefinition quest = result.quest().get();
            List<String> problems = references(quest, villagerTypes, goods, catalog);
            if (!problems.isEmpty()) {
                problems.forEach(problem -> diagnostics.add(relative + ": " + problem));
                return;
            }
            String previous = keys.putIfAbsent(quest.key(), path);
            if (previous != null)
                diagnostics.add(relative + ": shares quest text key '" + quest.key() + "' with quests/" + previous + ".txt");
            quests.put(path, quest);
        });
        return new QuestCatalog(quests, diagnostics);
    }

    static List<String> references(QuestDefinition quest, Map<String, Set<String>> villagerTypes, Set<String> goods,
                                   LegacyContentCatalog catalog) {
        List<String> problems = new ArrayList<>();
        for (var villager : quest.villagers()) {
            for (String type : villager.types()) {
                int slash = type.indexOf('/');
                Set<String> known = villagerTypes.get(type.substring(0, slash));
                if (known == null) problems.add("villager " + villager.key() + " uses unknown culture in " + type);
                else if (!known.contains(type.substring(slash + 1))) problems.add("villager " + villager.key() + " uses unknown type " + type);
            }
        }
        for (var step : quest.steps()) {
            for (String good : step.requiredGoods().keySet())
                if (!goods.contains(good)) problems.add("step " + step.index() + " requires unknown good " + good);
            for (String good : step.rewardGoods().keySet())
                if (!goods.contains(good)) problems.add("step " + step.index() + " rewards unknown good " + good);
            for (var building : step.bedrockBuildings()) {
                var culture = catalog.cultures().get(building.culture());
                if (culture == null) problems.add("step " + step.index() + " uses unknown culture " + building.culture());
                else if (!hasBuilding(culture, building.building()))
                    problems.add("step " + step.index() + " uses unknown building " + building.culture() + "/" + building.building());
            }
        }
        return problems;
    }

    private static boolean hasBuilding(LegacyContentCatalog.Culture culture, String building) {
        String suffix = "/" + building + ".txt";
        return culture.documents().keySet().stream().anyMatch(path ->
                (path.startsWith("villages/") || path.startsWith("lonebuildings/")) && path.toLowerCase(Locale.ROOT).endsWith(suffix));
    }

    public Optional<QuestDefinition> get(String path) { return Optional.ofNullable(quests.get(path)); }
}
