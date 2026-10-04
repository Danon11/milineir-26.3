package org.millenaire.fabric.quest;

import org.junit.jupiter.api.Test;
import org.millenaire.fabric.content.LegacyCatalogLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class QuestCatalogBundleTest {
    private static Path bundle() throws Exception {
        return Path.of(QuestCatalogBundleTest.class.getResource("/todeploy/millenaire/blocklist.txt").toURI()).getParent();
    }

    @Test
    void loadsEveryBundledQuestWithResolvedVillagersGoodsAndBuildings() throws Exception {
        var quests = QuestCatalog.from(LegacyCatalogLoader.load(bundle()));
        assertEquals(91, quests.quests().size());
        // Both cultures ship a "fishingfrenzy" quest; the legacy text keys are shared, as in the original.
        assertEquals(List.of("quests/japanesebasic/fishingfrenzy.txt: shares quest text key 'fishingfrenzy' with quests/inuitbasic/fishingfrenzy.txt"),
                quests.diagnostics());
        Map<String, Integer> groups = new TreeMap<>();
        quests.quests().values().forEach(quest -> groups.merge(quest.group(), 1, Integer::sum));
        assertEquals(Map.ofEntries(Map.entry("byzantinesbasic", 2), Map.entry("common", 1), Map.entry("indianbasic", 3),
                Map.entry("inuitbasic", 6), Map.entry("japanesebasic", 3), Map.entry("marvel-norman", 5), Map.entry("mysterybasic", 5),
                Map.entry("normanbasic", 9), Map.entry("seljukbasic", 9), Map.entry("worldquest-indian", 21),
                Map.entry("worldquest-mayan", 10), Map.entry("worldquest-norman", 17)), groups);
        assertEquals(41, quests.quests().values().stream().filter(QuestDefinition::requiresWorldActions).count());

        var imam = quests.get("seljukbasic/imambook").orElseThrow();
        assertEquals(200, imam.minReputation());
        assertEquals(Map.of("book", 1), imam.steps().get(1).requiredGoods());
        var sword = quests.get("worldquest-indian/sadhu_6_enchantedsword").orElseThrow();
        assertEquals(Map.of("enchantedsword", 1), sword.steps().get(1).requiredGoods());
        assertEquals(128, sword.steps().get(1).rewardReputation());
    }

    @Test
    void everyBundledStepHasAnEnglishLabelAndTranslationsFallBack() throws Exception {
        Path root = bundle();
        var quests = QuestCatalog.from(LegacyCatalogLoader.load(root));
        var english = QuestTexts.load("en", root);
        for (var quest : quests.quests().values())
            for (var step : quest.steps())
                assertTrue(english.text(quest.key(), step.index(), QuestTexts.Field.LABEL).isPresent(), quest.path() + " step " + step.index());
        assertEquals("Husband's lunch", english.text("guardwife", 0, QuestTexts.Field.LABEL).orElseThrow());
        var french = QuestTexts.load("fr", root);
        assertNotEquals(english.text("guardwife", 0, QuestTexts.Field.LABEL), french.text("guardwife", 0, QuestTexts.Field.LABEL));
        assertTrue(french.text("guardwife", 1, QuestTexts.Field.LISTING).orElseThrow().contains("$guard_villagername$"));
        assertTrue(QuestTexts.load("xx", root).text("guardwife", 0, QuestTexts.Field.LABEL).isPresent());
        assertThrows(IllegalArgumentException.class, () -> QuestTexts.load("../en", root));
    }

    @Test
    void customQuestOverridesBundledFileAndBrokenReferencesAreRejected() throws Exception {
        Path custom = Files.createTempDirectory("millenaire-custom");
        Path override = custom.resolve("quests/normanbasic/guardwife.txt");
        Files.createDirectories(override.getParent());
        Files.writeString(override, "definevillager:key=startvillager,type=norman/no_such_type\nstep:new\nvillager:startvillager\nduration:1\n");
        Path extra = custom.resolve("quests/custom/extra.txt");
        Files.createDirectories(extra.getParent());
        Files.writeString(extra, "definevillager:key=v,type=norman/knight\nstep:new\nvillager:v\nduration:2\nrewardgood:no_such_good,1\n");
        var quests = QuestCatalog.from(LegacyCatalogLoader.load(bundle(), custom));
        assertTrue(quests.get("normanbasic/guardwife").isEmpty());
        assertTrue(quests.get("custom/extra").isEmpty());
        assertTrue(quests.diagnostics().contains("quests/normanbasic/guardwife.txt: villager startvillager uses unknown type norman/no_such_type"),
                quests.diagnostics().toString());
        assertTrue(quests.diagnostics().contains("quests/custom/extra.txt: step 0 rewards unknown good no_such_good"), quests.diagnostics().toString());
        assertEquals(90, quests.quests().size());
    }
}
