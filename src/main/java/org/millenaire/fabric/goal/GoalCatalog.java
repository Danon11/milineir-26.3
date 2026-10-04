package org.millenaire.fabric.goal;

import org.millenaire.fabric.content.LegacyContentCatalog;

import java.util.*;

/**
 * Data-driven goals keyed by lowercase file name, plus the names of goals implemented in code. Villager
 * types reference goals by name only, so a name defined in two folders keeps the first in path order and
 * reports the duplicate.
 */
public record GoalCatalog(Map<String, GoalDefinition> goals, List<String> diagnostics) {
    /** Goals the original implemented in Java rather than in data files. */
    public static final Set<String> BUILT_IN = Set.of("gorest", "chat", "gosocialise", "sleep", "bringbackresourceshome",
            "getgoodshousehold", "delivergoodshousehold", "deliverresourcesshop", "huntmonster", "gethousethresources",
            "gathergoods", "getresourcesforbuild", "keepstall", "construction", "buildpath", "breed", "clearoldpath",
            "becomeadult", "plantsaplings", "choptrees", "shearsheep", "gatherbrick", "drybrick", "visitinn", "visitbuilding",
            "plantsugarcane", "harvestsugarcane", "fish", "fishinuit", "performpujas", "bepujaperformer", "plantwarts",
            "harvestwarts", "plantcocoa", "harvestcocoa", "mining", "gathersnails", "gathersilk", "brewpotions", "cookfish",
            "gopray", "godrinkcacauhaa", "teach", "listentospeech1", "listentospeech2", "defendvillage", "raidvillage",
            "hidefromraid", "foreignmerchantkeepstall", "merchantvisitinn", "merchantvisitbuilding", "getfoodhome", "play");

    public GoalCatalog {
        goals = Collections.unmodifiableMap(new LinkedHashMap<>(goals));
        diagnostics = List.copyOf(diagnostics);
    }

    public static GoalCatalog empty() { return new GoalCatalog(Map.of(), List.of()); }

    public static GoalCatalog from(LegacyContentCatalog catalog) {
        Set<String> goods = catalog.goods().goods().keySet();
        Map<String, GoalDefinition> goals = new TreeMap<>();
        Map<String, String> origins = new HashMap<>();
        List<String> diagnostics = new ArrayList<>();
        catalog.globalDocuments().forEach((path, document) -> {
            if (!path.startsWith("goals/") || !path.endsWith(".txt")) return;
            String[] parts = path.split("/");
            if (parts.length < 3) return;
            var kind = GoalDefinition.Kind.byFolder(parts[1]);
            if (kind.isEmpty()) {
                diagnostics.add(path + ": unknown goal folder " + parts[1]);
                return;
            }
            String key = parts[parts.length - 1].substring(0, parts[parts.length - 1].length() - 4).toLowerCase(Locale.ROOT);
            try {
                GoalDefinition goal = GoalDefinition.parse(key, kind.get(), document);
                Set<String> unknown = new TreeSet<>(goal.referencedGoods());
                unknown.removeAll(goods);
                if (!unknown.isEmpty()) {
                    diagnostics.add(path + ": unknown goods " + unknown);
                    return;
                }
                String previous = origins.putIfAbsent(key, path);
                if (previous != null) diagnostics.add(path + ": goal name " + key + " already defined by " + previous);
                else goals.put(key, goal);
            } catch (RuntimeException exception) {
                diagnostics.add(path + ": " + exception.getMessage());
            }
        });
        return new GoalCatalog(goals, diagnostics);
    }

    public Optional<GoalDefinition> get(String key) { return Optional.ofNullable(goals.get(key.toLowerCase(Locale.ROOT))); }

    /** Goal names of a villager type that are neither data goals nor implemented in code. */
    public List<String> unknown(Collection<String> names) {
        return names.stream().map(name -> name.toLowerCase(Locale.ROOT)).filter(name -> !goals.containsKey(name) && !BUILT_IN.contains(name)).distinct().toList();
    }
}
