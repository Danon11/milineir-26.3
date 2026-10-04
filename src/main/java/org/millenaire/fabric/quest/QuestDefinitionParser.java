package org.millenaire.fabric.quest;

import org.millenaire.fabric.content.LegacyDocument;
import org.millenaire.fabric.content.LegacyNumbers;

import java.util.*;

/**
 * Reads legacy {@code quests/<group>/<key>.txt} files. Quest-level conditions may appear anywhere
 * (several bundled files declare {@code minreputation} after the last step); every other key
 * belongs to the most recent {@code step:new}. A malformed file is rejected as a whole.
 */
public final class QuestDefinitionParser {
    public record Result(Optional<QuestDefinition> quest, List<String> diagnostics) {
        public Result { diagnostics = List.copyOf(diagnostics); }
    }

    private QuestDefinitionParser() {}

    /** @param path quest path without extension, relative to {@code quests/} */
    public static Result parse(String path, LegacyDocument document) {
        String key = path.substring(path.lastIndexOf('/') + 1);
        List<String> diagnostics = new ArrayList<>();
        double chance = 0;
        int maxSimultaneous = 5, minReputation = 0;
        List<String> requiredGlobal = new ArrayList<>(), forbiddenGlobal = new ArrayList<>();
        List<String> requiredPlayer = new ArrayList<>(), forbiddenPlayer = new ArrayList<>();
        List<QuestDefinition.VillagerDefinition> villagers = new ArrayList<>();
        List<StepBuilder> steps = new ArrayList<>();
        StepBuilder step = null;
        int lineNumber = 0;
        boolean failed = false;
        for (String raw : document.lines()) {
            lineNumber++;
            String line = raw.replace("﻿", "").trim();
            if (line.isEmpty() || line.startsWith("//")) continue;
            String where = document.source() + ":" + lineNumber + ": ";
            int colon = line.indexOf(':');
            if (colon <= 0) {
                diagnostics.add(where + "missing ':' separator");
                failed = true;
                continue;
            }
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            try {
                switch (name) {
                    case "step" -> {
                        if (!value.equalsIgnoreCase("new")) throw new IllegalArgumentException("expected step:new");
                        step = new StepBuilder(steps.size());
                        steps.add(step);
                    }
                    case "definevillager" -> villagers.add(villager(value));
                    case "chanceperhour" -> chance = Double.parseDouble(value);
                    case "maxsimultaneous" -> maxSimultaneous = Integer.parseInt(value);
                    case "minreputation" -> minReputation = product(value);
                    case "requiredglobaltag" -> requiredGlobal.add(value);
                    case "forbiddenglobaltag" -> forbiddenGlobal.add(value);
                    case "requiredplayertag" -> requiredPlayer.add(value);
                    case "forbiddenplayertag" -> forbiddenPlayer.add(value);
                    default -> {
                        if (step == null) throw new IllegalArgumentException("'" + name + "' before the first step:new");
                        step.accept(name, value);
                    }
                }
            } catch (IllegalArgumentException exception) {
                diagnostics.add(where + exception.getMessage());
                failed = true;
            }
        }
        if (failed) return new Result(Optional.empty(), diagnostics);
        try {
            if (!(chance >= 0) || Double.isInfinite(chance)) throw new IllegalArgumentException("invalid chanceperhour " + chance);
            if (maxSimultaneous < 0) throw new IllegalArgumentException("negative maxsimultaneous");
            Set<String> keys = new HashSet<>();
            for (var villager : villagers) {
                if (!keys.add(villager.key())) throw new IllegalArgumentException("duplicate villager key " + villager.key());
                if (villager.relatedTo().isPresent() && !keys.contains(villager.relatedTo().get()))
                    throw new IllegalArgumentException("villager " + villager.key() + " is related to undeclared villager " + villager.relatedTo().get());
            }
            List<QuestDefinition.Step> built = new ArrayList<>();
            for (StepBuilder builder : steps) {
                var definition = builder.build();
                for (String villager : builder.referencedVillagers())
                    if (!keys.contains(villager)) throw new IllegalArgumentException("step " + definition.index() + " references undeclared villager " + villager);
                built.add(definition);
            }
            var quest = new QuestDefinition(path, key, chance, maxSimultaneous, minReputation, requiredGlobal, forbiddenGlobal,
                    requiredPlayer, forbiddenPlayer, villagers, built);
            return new Result(Optional.of(quest), diagnostics);
        } catch (IllegalArgumentException exception) {
            diagnostics.add(document.source() + ": " + exception.getMessage());
            return new Result(Optional.empty(), diagnostics);
        }
    }

    static QuestDefinition.VillagerDefinition villager(String value) {
        String key = null, relatedTo = null;
        QuestDefinition.Relation relation = null;
        List<String> types = new ArrayList<>(), required = new ArrayList<>(), forbidden = new ArrayList<>();
        for (String pair : value.split(",")) {
            int equals = pair.indexOf('=');
            if (equals <= 0) throw new IllegalArgumentException("invalid definevillager field '" + pair.trim() + "'");
            String field = pair.substring(0, equals).trim().toLowerCase(Locale.ROOT);
            String fieldValue = pair.substring(equals + 1).trim();
            if (fieldValue.isEmpty()) throw new IllegalArgumentException("empty definevillager field " + field);
            switch (field) {
                case "key" -> key = fieldValue;
                case "type" -> {
                    if (fieldValue.indexOf('/') <= 0) throw new IllegalArgumentException("villager type needs culture/type: " + fieldValue);
                    types.add(fieldValue.toLowerCase(Locale.ROOT));
                }
                case "relatedto" -> relatedTo = fieldValue;
                case "relation" -> relation = QuestDefinition.Relation.parse(fieldValue);
                case "requiredtag" -> required.add(fieldValue);
                case "forbiddentag" -> forbidden.add(fieldValue);
                default -> throw new IllegalArgumentException("unknown definevillager field " + field);
            }
        }
        return new QuestDefinition.VillagerDefinition(key, types, Optional.ofNullable(relatedTo), Optional.ofNullable(relation), required, forbidden);
    }

    static int product(String value) { return LegacyNumbers.product(value); }

    private static String[] split(String value, int expected, String name) {
        String[] parts = value.split(",", -1);
        if (parts.length != expected) throw new IllegalArgumentException(name + " expects " + expected + " comma-separated values");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
            if (parts[i].isEmpty()) throw new IllegalArgumentException(name + " has an empty value");
        }
        return parts;
    }

    private static final class StepBuilder {
        private final int index;
        private String villager;
        private int duration;
        private boolean showRequiredGoods = true;
        private final Map<String, Integer> requiredGoods = new LinkedHashMap<>(), rewardGoods = new LinkedHashMap<>();
        private int rewardMoney, rewardReputation, penaltyReputation;
        private final OutcomeBuilder success = new OutcomeBuilder(), failure = new OutcomeBuilder();
        private final List<String> requiredGlobal = new ArrayList<>(), forbiddenGlobal = new ArrayList<>();
        private final List<String> requiredPlayer = new ArrayList<>(), forbiddenPlayer = new ArrayList<>();
        private final List<QuestDefinition.ActionData> actionData = new ArrayList<>();
        private final List<QuestDefinition.RelationChange> relationChanges = new ArrayList<>();
        private final List<QuestDefinition.BedrockBuilding> bedrockBuildings = new ArrayList<>();

        StepBuilder(int index) { this.index = index; }

        void accept(String name, String value) {
            switch (name) {
                case "villager" -> {
                    if (villager != null) throw new IllegalArgumentException("step " + index + " declares villager twice");
                    villager = value;
                }
                case "duration" -> duration = product(value);
                case "showrequiredgoods" -> showRequiredGoods = bool(value);
                case "requiredgood" -> good(requiredGoods, value, name);
                case "rewardgood" -> good(rewardGoods, value, name);
                case "rewardmoney" -> rewardMoney = nonNegative(value, name);
                case "rewardreputation" -> rewardReputation = nonNegative(value, name);
                case "penaltyreputation" -> penaltyReputation = nonNegative(value, name);
                case "settagsuccess" -> success.setVillagerTags.add(villagerTag(value, name));
                case "settagfailure" -> failure.setVillagerTags.add(villagerTag(value, name));
                case "cleartagsuccess" -> success.clearVillagerTags.add(villagerTag(value, name));
                case "cleartagfailure" -> failure.clearVillagerTags.add(villagerTag(value, name));
                case "setplayertagsuccess" -> success.setPlayerTags.add(value);
                case "setplayertagfailure" -> failure.setPlayerTags.add(value);
                case "clearplayertagsuccess" -> success.clearPlayerTags.add(value);
                case "clearplayertagfailure" -> failure.clearPlayerTags.add(value);
                case "setglobaltagsuccess" -> success.setGlobalTags.add(value);
                case "setglobaltagfailure" -> failure.setGlobalTags.add(value);
                case "clearglobaltagsuccess" -> success.clearGlobalTags.add(value);
                case "clearglobaltagfailure" -> failure.clearGlobalTags.add(value);
                case "steprequiredglobaltag" -> requiredGlobal.add(value);
                case "stepforbiddenglobaltag" -> forbiddenGlobal.add(value);
                case "steprequiredplayertag" -> requiredPlayer.add(value);
                case "stepforbiddenplayertag" -> forbiddenPlayer.add(value);
                case "setactiondatasuccess" -> {
                    String[] parts = split(value, 2, name);
                    actionData.add(new QuestDefinition.ActionData(parts[0], parts[1]));
                }
                case "relationchange" -> {
                    String[] parts = split(value, 3, name);
                    relationChanges.add(new QuestDefinition.RelationChange(parts[0], parts[1], product(parts[2])));
                }
                case "bedrockbuilding" -> {
                    String[] parts = split(value, 2, name);
                    bedrockBuildings.add(new QuestDefinition.BedrockBuilding(parts[0].toLowerCase(Locale.ROOT), parts[1].toLowerCase(Locale.ROOT)));
                }
                default -> throw new IllegalArgumentException("unknown quest step key '" + name + "'");
            }
        }

        private static boolean bool(String value) {
            if (value.equalsIgnoreCase("true")) return true;
            if (value.equalsIgnoreCase("false")) return false;
            throw new IllegalArgumentException("expected true or false, got '" + value + "'");
        }
        private static int nonNegative(String value, String name) {
            int result = product(value);
            if (result < 0) throw new IllegalArgumentException(name + " must not be negative");
            return result;
        }
        private static void good(Map<String, Integer> goods, String value, String name) {
            String[] parts = split(value, 2, name);
            int count = product(parts[1]);
            if (count <= 0) throw new IllegalArgumentException(name + " count must be positive");
            goods.merge(parts[0].toLowerCase(Locale.ROOT), count, Math::addExact);
        }
        private static QuestDefinition.VillagerTag villagerTag(String value, String name) {
            String[] parts = split(value, 2, name);
            return new QuestDefinition.VillagerTag(parts[0], parts[1]);
        }

        List<String> referencedVillagers() {
            List<String> result = new ArrayList<>();
            if (villager != null) result.add(villager);
            for (OutcomeBuilder outcome : List.of(success, failure)) {
                outcome.setVillagerTags.forEach(tag -> result.add(tag.villager()));
                outcome.clearVillagerTags.forEach(tag -> result.add(tag.villager()));
            }
            relationChanges.forEach(change -> { result.add(change.first()); result.add(change.second()); });
            return result;
        }

        QuestDefinition.Step build() {
            return new QuestDefinition.Step(index, villager, duration, showRequiredGoods, requiredGoods, rewardGoods,
                    rewardMoney, rewardReputation, penaltyReputation, success.build(), failure.build(),
                    requiredGlobal, forbiddenGlobal, requiredPlayer, forbiddenPlayer, actionData, relationChanges, bedrockBuildings);
        }
    }

    private static final class OutcomeBuilder {
        final List<QuestDefinition.VillagerTag> setVillagerTags = new ArrayList<>(), clearVillagerTags = new ArrayList<>();
        final List<String> setPlayerTags = new ArrayList<>(), clearPlayerTags = new ArrayList<>();
        final List<String> setGlobalTags = new ArrayList<>(), clearGlobalTags = new ArrayList<>();

        QuestDefinition.Outcome build() {
            return new QuestDefinition.Outcome(setVillagerTags, clearVillagerTags, setPlayerTags, clearPlayerTags, setGlobalTags, clearGlobalTags);
        }
    }
}
