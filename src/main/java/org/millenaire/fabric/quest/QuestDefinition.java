package org.millenaire.fabric.quest;

import java.util.*;

/**
 * A legacy quest file in typed form. Keys and tags keep their original spelling so saved progress,
 * text lookup ({@code <key>_<step>_label}) and custom content written for 1.12 continue to match.
 */
public record QuestDefinition(String path, String key, double chancePerHour, int maxSimultaneous, int minReputation,
                              List<String> requiredGlobalTags, List<String> forbiddenGlobalTags,
                              List<String> requiredPlayerTags, List<String> forbiddenPlayerTags,
                              List<VillagerDefinition> villagers, List<Step> steps) {
    public QuestDefinition {
        Objects.requireNonNull(path);
        Objects.requireNonNull(key);
        requiredGlobalTags = List.copyOf(requiredGlobalTags);
        forbiddenGlobalTags = List.copyOf(forbiddenGlobalTags);
        requiredPlayerTags = List.copyOf(requiredPlayerTags);
        forbiddenPlayerTags = List.copyOf(forbiddenPlayerTags);
        villagers = List.copyOf(villagers);
        steps = List.copyOf(steps);
        if (villagers.isEmpty()) throw new IllegalArgumentException("Quest has no villagers: " + path);
        if (steps.isEmpty()) throw new IllegalArgumentException("Quest has no steps: " + path);
    }

    /** Source folder, e.g. {@code normanbasic} or {@code worldquest-indian}. */
    public String group() {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    public Optional<VillagerDefinition> villager(String key) {
        return villagers.stream().filter(villager -> villager.key().equals(key)).findFirst();
    }

    /** World quests drive buildings and action data that need dedicated world mechanics. */
    public boolean requiresWorldActions() {
        return steps.stream().anyMatch(step -> !step.bedrockBuildings().isEmpty() || !step.actionData().isEmpty());
    }

    public enum Relation {
        SAMEHOUSE, SAMEVILLAGE, NEARBYVILLAGE, ANYVILLAGE;

        public static Relation parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown villager relation: " + value);
            }
        }
    }

    /** {@code definevillager:key=...,type=culture/type,relatedto=...,relation=...,requiredtag=...,forbiddentag=...}. */
    public record VillagerDefinition(String key, List<String> types, Optional<String> relatedTo, Optional<Relation> relation,
                                     List<String> requiredTags, List<String> forbiddenTags) {
        public VillagerDefinition {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("Villager definition has no key");
            types = List.copyOf(types);
            requiredTags = List.copyOf(requiredTags);
            forbiddenTags = List.copyOf(forbiddenTags);
            if (relatedTo.isPresent() != relation.isPresent())
                throw new IllegalArgumentException("Villager " + key + " needs both relatedto and relation");
            if (types.isEmpty() && requiredTags.isEmpty())
                throw new IllegalArgumentException("Villager " + key + " has neither type nor requiredtag");
        }
    }

    public record VillagerTag(String villager, String tag) {}
    public record ActionData(String key, String value) {}
    public record RelationChange(String first, String second, int change) {}
    public record BedrockBuilding(String culture, String building) {}

    /** Tag changes applied when a step completes or fails. */
    public record Outcome(List<VillagerTag> setVillagerTags, List<VillagerTag> clearVillagerTags,
                          List<String> setPlayerTags, List<String> clearPlayerTags,
                          List<String> setGlobalTags, List<String> clearGlobalTags) {
        public Outcome {
            setVillagerTags = List.copyOf(setVillagerTags);
            clearVillagerTags = List.copyOf(clearVillagerTags);
            setPlayerTags = List.copyOf(setPlayerTags);
            clearPlayerTags = List.copyOf(clearPlayerTags);
            setGlobalTags = List.copyOf(setGlobalTags);
            clearGlobalTags = List.copyOf(clearGlobalTags);
        }
    }

    /** Goods are legacy itemlist aliases; counts for repeated aliases are summed in file order. */
    public record Step(int index, String villager, int duration, boolean showRequiredGoods,
                       Map<String, Integer> requiredGoods, Map<String, Integer> rewardGoods,
                       int rewardMoney, int rewardReputation, int penaltyReputation,
                       Outcome success, Outcome failure,
                       List<String> requiredGlobalTags, List<String> forbiddenGlobalTags,
                       List<String> requiredPlayerTags, List<String> forbiddenPlayerTags,
                       List<ActionData> actionData, List<RelationChange> relationChanges,
                       List<BedrockBuilding> bedrockBuildings) {
        public Step {
            if (villager == null || villager.isBlank()) throw new IllegalArgumentException("Step " + index + " has no villager");
            if (duration <= 0) throw new IllegalArgumentException("Step " + index + " has no positive duration");
            requiredGoods = Collections.unmodifiableMap(new LinkedHashMap<>(requiredGoods));
            rewardGoods = Collections.unmodifiableMap(new LinkedHashMap<>(rewardGoods));
            requiredGlobalTags = List.copyOf(requiredGlobalTags);
            forbiddenGlobalTags = List.copyOf(forbiddenGlobalTags);
            requiredPlayerTags = List.copyOf(requiredPlayerTags);
            forbiddenPlayerTags = List.copyOf(forbiddenPlayerTags);
            actionData = List.copyOf(actionData);
            relationChanges = List.copyOf(relationChanges);
            bedrockBuildings = List.copyOf(bedrockBuildings);
        }
    }
}
