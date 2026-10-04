package org.millenaire.fabric.quest;

import java.util.*;
import java.util.random.RandomGenerator;

/**
 * World-independent quest rules from the original QuestManager: who may receive a quest, which villagers
 * take part, and when a step has run out of time. Durations are game hours of 1000 ticks.
 */
public final class QuestRuntime {
    public static final long HOUR = 1000L;
    /** Villages farther apart than this do not count as {@code nearbyvillage}. */
    public static final double NEARBY_VILLAGE_DISTANCE = 2000.0;

    /** A villager that can take part in quests; {@code type} is {@code culture/type}. */
    public record VillagerInfo(UUID id, String type, String home, String village, int villageX, int villageZ) {}

    /** A running quest of one player; villagers map definition keys to villager ids. */
    public record Instance(long id, String quest, UUID player, Map<String, UUID> villagers, int step, long stepStart) {
        public Instance { villagers = Map.copyOf(villagers); }
        public Instance next(long now) { return new Instance(id, quest, player, villagers, step + 1, now); }
    }

    /** Quest tags and reputation as seen by the rules. Villager tags are stored per player. */
    public interface State {
        boolean playerTag(UUID player, String tag);
        boolean globalTag(String tag);
        boolean villagerTag(UUID villager, UUID player, String tag);
        int reputation(String village, UUID player);
        List<Instance> active(UUID player);
    }

    private QuestRuntime() {}

    /** Tag conditions and the per-player simultaneous limit. */
    public static boolean eligible(QuestDefinition quest, UUID player, State state) {
        long running = state.active(player).stream().filter(instance -> instance.quest().equals(quest.path())).count();
        if (running >= quest.maxSimultaneous()) return false;
        for (String tag : quest.requiredGlobalTags()) if (!state.globalTag(tag)) return false;
        for (String tag : quest.forbiddenGlobalTags()) if (state.globalTag(tag)) return false;
        for (String tag : quest.requiredPlayerTags()) if (!state.playerTag(player, tag)) return false;
        for (String tag : quest.forbiddenPlayerTags()) if (state.playerTag(player, tag)) return false;
        return true;
    }

    /** Step-level tag conditions that must hold before the step can be completed. */
    public static boolean stepAllowed(QuestDefinition.Step step, UUID player, State state) {
        for (String tag : step.requiredGlobalTags()) if (!state.globalTag(tag)) return false;
        for (String tag : step.forbiddenGlobalTags()) if (state.globalTag(tag)) return false;
        for (String tag : step.requiredPlayerTags()) if (!state.playerTag(player, tag)) return false;
        for (String tag : step.forbiddenPlayerTags()) if (state.playerTag(player, tag)) return false;
        return true;
    }

    static boolean matches(QuestDefinition.VillagerDefinition definition, VillagerInfo villager, UUID player, State state, Set<UUID> busy) {
        if (busy.contains(villager.id())) return false;
        if (!definition.types().isEmpty() && !definition.types().contains(villager.type())) return false;
        for (String tag : definition.requiredTags()) if (!state.villagerTag(villager.id(), player, tag)) return false;
        for (String tag : definition.forbiddenTags()) if (state.villagerTag(villager.id(), player, tag)) return false;
        return true;
    }

    /**
     * Picks the villagers of a new quest: a starting villager in a village where the player has enough reputation,
     * then every related villager by its relation to an already chosen one.
     */
    public static Optional<Map<String, UUID>> assign(QuestDefinition quest, UUID player, List<VillagerInfo> villagers, State state, RandomGenerator random) {
        Set<UUID> busy = new HashSet<>();
        for (Instance instance : state.active(player)) busy.addAll(instance.villagers().values());
        var start = quest.villagers().getFirst();
        List<VillagerInfo> starters = villagers.stream().filter(v -> state.reputation(v.village(), player) >= quest.minReputation()
                && matches(start, v, player, state, busy)).toList();
        List<Map<String, UUID>> options = new ArrayList<>();
        Map<UUID, VillagerInfo> byId = new HashMap<>();
        villagers.forEach(v -> byId.put(v.id(), v));
        for (VillagerInfo starter : starters) {
            Map<String, UUID> chosen = new LinkedHashMap<>();
            chosen.put(start.key(), starter.id());
            boolean complete = true;
            for (var definition : quest.villagers().subList(1, quest.villagers().size())) {
                VillagerInfo related = definition.relatedTo().map(chosen::get).map(byId::get).orElse(null);
                if (related == null || definition.relation().isEmpty()) { complete = false; break; }
                Set<UUID> taken = new HashSet<>(busy);
                taken.addAll(chosen.values());
                List<VillagerInfo> candidates = villagers.stream().filter(v -> relationHolds(definition.relation().get(), related, v)
                        && matches(definition, v, player, state, taken)).toList();
                if (candidates.isEmpty()) { complete = false; break; }
                chosen.put(definition.key(), candidates.get(random.nextInt(candidates.size())).id());
            }
            if (complete) options.add(chosen);
        }
        return options.isEmpty() ? Optional.empty() : Optional.of(options.get(random.nextInt(options.size())));
    }

    static boolean relationHolds(QuestDefinition.Relation relation, VillagerInfo related, VillagerInfo candidate) {
        return switch (relation) {
            case SAMEHOUSE -> candidate.home().equals(related.home());
            case SAMEVILLAGE -> candidate.village().equals(related.village()) && !candidate.home().equals(related.home());
            case NEARBYVILLAGE -> !candidate.village().equals(related.village())
                    && Math.hypot(candidate.villageX() - related.villageX(), candidate.villageZ() - related.villageZ()) < NEARBY_VILLAGE_DISTANCE;
            case ANYVILLAGE -> !candidate.village().equals(related.village());
        };
    }

    public static boolean expired(Instance instance, QuestDefinition quest, long now) {
        if (instance.step() < 0 || instance.step() >= quest.steps().size()) return true;
        return instance.stepStart() + quest.steps().get(instance.step()).duration() * HOUR <= now;
    }
}
