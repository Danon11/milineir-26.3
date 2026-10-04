package org.millenaire.fabric.quest;

import org.junit.jupiter.api.Test;
import org.millenaire.fabric.content.LegacyCatalogLoader;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class QuestRuntimeTest {
    static final class FakeState implements QuestRuntime.State {
        final Set<String> player = new HashSet<>(), global = new HashSet<>(), villager = new HashSet<>();
        final Map<String, Integer> reputation = new HashMap<>();
        final List<QuestRuntime.Instance> active = new ArrayList<>();
        public boolean playerTag(UUID p, String t) { return player.contains(t); }
        public boolean globalTag(String t) { return global.contains(t); }
        public boolean villagerTag(UUID v, UUID p, String t) { return villager.contains(v + "/" + t); }
        public int reputation(String village, UUID p) { return reputation.getOrDefault(village, 0); }
        public List<QuestRuntime.Instance> active(UUID p) { return active; }
    }

    private static QuestCatalog quests() throws Exception {
        return QuestCatalog.from(LegacyCatalogLoader.load(Path.of(QuestRuntimeTest.class.getResource("/todeploy/millenaire/blocklist.txt").toURI()).getParent()));
    }

    private static QuestRuntime.VillagerInfo villager(String type, String home, String village) {
        return new QuestRuntime.VillagerInfo(UUID.randomUUID(), type, home, village, village.equals("far") ? 5000 : 0, 0);
    }

    @Test
    void assignsStartingAndSameHouseVillagersWithEnoughReputation() throws Exception {
        var quest = quests().get("seljukbasic/imambook").orElseThrow();
        UUID player = UUID.randomUUID();
        var state = new FakeState();
        var wife = villager("seljuk/turk_imamswife", "mosque", "v1");
        var imam = villager("seljuk/turk_imam", "mosque", "v1");
        var otherImam = villager("seljuk/turk_imam", "house", "v1");
        var villagers = List.of(wife, imam, otherImam);
        assertTrue(QuestRuntime.assign(quest, player, villagers, state, new SplittableRandom(1)).isEmpty(), "minreputation 200");
        state.reputation.put("v1", 200);
        var assignment = QuestRuntime.assign(quest, player, villagers, state, new SplittableRandom(1)).orElseThrow();
        assertEquals(wife.id(), assignment.get("startvillager"));
        assertEquals(imam.id(), assignment.get("turk_imam"), "samehouse");
        state.villager.add(wife.id() + "/imambook_done");
        assertTrue(QuestRuntime.assign(quest, player, villagers, state, new SplittableRandom(1)).isEmpty(), "forbiddentag");
    }

    @Test
    void eligibilityFollowsTagsAndSimultaneousLimit() throws Exception {
        var quests = quests();
        var quest = quests.get("worldquest-indian/sadhu_6_enchantedsword").orElseThrow();
        UUID player = UUID.randomUUID();
        var state = new FakeState();
        assertFalse(QuestRuntime.eligible(quest, player, state), "requires sadhu_5_adivasi");
        state.player.add("sadhu_5_adivasi");
        assertTrue(QuestRuntime.eligible(quest, player, state));
        state.active.add(new QuestRuntime.Instance(1, quest.path(), player, Map.of(), 0, 0));
        assertFalse(QuestRuntime.eligible(quest, player, state), "maxsimultaneous 1");
        state.active.clear();
        state.player.add("sadhu_6_enchantedsword");
        assertFalse(QuestRuntime.eligible(quest, player, state), "forbidden after completion");
    }

    @Test
    void relationsAndTimeouts() throws Exception {
        var near = villager("a/b", "h1", "v1");
        var sameVillage = villager("a/b", "h2", "v1");
        var otherVillage = villager("a/b", "h3", "v2");
        var far = villager("a/b", "h4", "far");
        assertTrue(QuestRuntime.relationHolds(QuestDefinition.Relation.SAMEVILLAGE, near, sameVillage));
        assertFalse(QuestRuntime.relationHolds(QuestDefinition.Relation.SAMEVILLAGE, near, otherVillage));
        assertTrue(QuestRuntime.relationHolds(QuestDefinition.Relation.NEARBYVILLAGE, near, otherVillage));
        assertFalse(QuestRuntime.relationHolds(QuestDefinition.Relation.NEARBYVILLAGE, near, far));
        assertTrue(QuestRuntime.relationHolds(QuestDefinition.Relation.ANYVILLAGE, near, far));
        var quest = quests().get("seljukbasic/imambook").orElseThrow();
        var instance = new QuestRuntime.Instance(1, quest.path(), UUID.randomUUID(), Map.of(), 0, 1000);
        assertFalse(QuestRuntime.expired(instance, quest, 1000 + 3 * 1000 - 1));
        assertTrue(QuestRuntime.expired(instance, quest, 1000 + 3 * 1000));
    }
}
