package org.millenaire.fabric.quest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.millenaire.fabric.content.LegacyDocument;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class QuestDefinitionParserTest {
    @TempDir Path temp;

    private QuestDefinitionParser.Result parse(String text) throws Exception {
        Path file = temp.resolve("quest.txt");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return QuestDefinitionParser.parse("seljukbasic/imambook", LegacyDocument.read(file, "quests/seljukbasic/imambook.txt"));
    }

    @Test
    void parsesQuestLevelFieldsVillagersAndStepsInOrder() throws Exception {
        // Old Mac line endings and a trailing quest-level field, as in the bundled files.
        var result = parse("minreputation:2*64\rchanceperhour:0.1\rmaxsimultaneous:2\rrequiredplayertag:intro\r"
                + "step:new\r"
                + "definevillager:key=startvillager,type=Seljuk/turk_imamswife,forbiddentag=imambook_done\r"
                + "definevillager:key=turk_imam,type=seljuk/turk_imam,relatedto=startvillager,relation=samehouse\r"
                + "villager:startvillager\rrewardgood:book,1\rrewardgood:book,2\rduration:3\r\r"
                + "step:new\rvillager:turk_imam\rduration:3\rrequiredgood:book,1\rrewardreputation:128\rpenaltyreputation:64\r"
                + "rewardmoney:12*64\rshowrequiredgoods:false\r"
                + "settagsuccess:startvillager,imambook_done\rsettagfailure:startvillager,imambook_done\r"
                + "setplayertagsuccess:next\rclearplayertagsuccess:intro\rsetactiondatasuccess:status,2\r"
                + "relationchange:startvillager,turk_imam,200\rforbiddenplayertag:late\r");
        assertEquals(List.of(), result.diagnostics());
        QuestDefinition quest = result.quest().orElseThrow();
        assertEquals("imambook", quest.key());
        assertEquals("seljukbasic", quest.group());
        assertEquals(128, quest.minReputation());
        assertEquals(0.1, quest.chancePerHour());
        assertEquals(2, quest.maxSimultaneous());
        assertEquals(List.of("intro"), quest.requiredPlayerTags());
        assertEquals(List.of("late"), quest.forbiddenPlayerTags());
        var imam = quest.villager("turk_imam").orElseThrow();
        assertEquals(List.of("seljuk/turk_imam"), imam.types());
        assertEquals(Optional.of("startvillager"), imam.relatedTo());
        assertEquals(Optional.of(QuestDefinition.Relation.SAMEHOUSE), imam.relation());
        assertEquals(List.of("seljuk/turk_imamswife"), quest.villager("startvillager").orElseThrow().types());
        assertEquals(List.of("imambook_done"), quest.villager("startvillager").orElseThrow().forbiddenTags());

        assertEquals(2, quest.steps().size());
        var first = quest.steps().get(0);
        assertEquals(0, first.index());
        assertEquals(Map.of("book", 3), first.rewardGoods());
        assertTrue(first.showRequiredGoods());
        var second = quest.steps().get(1);
        assertEquals("turk_imam", second.villager());
        assertEquals(Map.of("book", 1), second.requiredGoods());
        assertEquals(768, second.rewardMoney());
        assertEquals(128, second.rewardReputation());
        assertEquals(64, second.penaltyReputation());
        assertFalse(second.showRequiredGoods());
        assertEquals(List.of(new QuestDefinition.VillagerTag("startvillager", "imambook_done")), second.success().setVillagerTags());
        assertEquals(List.of(new QuestDefinition.VillagerTag("startvillager", "imambook_done")), second.failure().setVillagerTags());
        assertEquals(List.of("next"), second.success().setPlayerTags());
        assertEquals(List.of("intro"), second.success().clearPlayerTags());
        assertEquals(List.of(new QuestDefinition.ActionData("status", "2")), second.actionData());
        assertEquals(List.of(new QuestDefinition.RelationChange("startvillager", "turk_imam", 200)), second.relationChanges());
        assertTrue(quest.requiresWorldActions());
    }

    @Test
    void rejectsQuestsThatCannotRunAsWritten() throws Exception {
        String villager = "definevillager:key=a,type=norman/knight\n";
        assertTrue(parse(villager + "villager:a\n").quest().isEmpty(), "step field before step:new");
        assertTrue(parse(villager + "step:new\nduration:3\n").quest().isEmpty(), "step without villager");
        assertTrue(parse(villager + "step:new\nvillager:a\n").quest().isEmpty(), "step without duration");
        assertTrue(parse(villager + "step:new\nvillager:b\nduration:1\n").quest().isEmpty(), "undeclared villager");
        assertTrue(parse("step:new\nvillager:a\nduration:1\n").quest().isEmpty(), "no villagers");
        assertTrue(parse(villager + "step:new\nvillager:a\nduration:1\nrewardgood:book\n").quest().isEmpty(), "good without count");
        assertTrue(parse(villager + "step:new\nvillager:a\nduration:1\nrewardgood:book,0\n").quest().isEmpty(), "zero count");
        assertTrue(parse(villager + "step:new\nvillager:a\nduration:1\nsettagsuccess:b,tag\n").quest().isEmpty(), "tag on unknown villager");
        assertTrue(parse(villager + "step:new\nvillager:a\nduration:1\nunknownkey:1\n").quest().isEmpty(), "unknown key");
        assertTrue(parse(villager + "step:new\nvillager:a\nduration:1\nshowrequiredgoods:maybe\n").quest().isEmpty(), "bad boolean");
        assertTrue(parse("definevillager:key=a,type=knight\nstep:new\nvillager:a\nduration:1\n").quest().isEmpty(), "type without culture");
        assertTrue(parse("definevillager:key=a,type=norman/knight,relatedto=b,relation=samehouse\nstep:new\nvillager:a\nduration:1\n")
                .quest().isEmpty(), "relation to undeclared villager");
        assertTrue(parse("definevillager:key=a,type=norman/knight\ndefinevillager:key=b,type=norman/knight,relatedto=a\n"
                + "step:new\nvillager:a\nduration:1\n").quest().isEmpty(), "relatedto without relation");
        assertTrue(parse("definevillager:key=a,type=norman/knight\ndefinevillager:key=a,type=norman/guard\n"
                + "step:new\nvillager:a\nduration:1\n").quest().isEmpty(), "duplicate villager key");
        var overflow = parse(villager + "step:new\nvillager:a\nduration:1\nrewardmoney:65536*65536\n");
        assertTrue(overflow.quest().isEmpty());
        assertTrue(overflow.diagnostics().getFirst().contains("quests/seljukbasic/imambook.txt:5"), overflow.diagnostics().toString());
    }

    @Test
    void acceptsVillagersMatchedOnlyByTag() throws Exception {
        var quest = parse("definevillager:key=leader,requiredtag=fallenking_villageleader\nstep:new\nvillager:leader\nduration:2\n")
                .quest().orElseThrow();
        assertEquals(List.of(), quest.villagers().getFirst().types());
        assertEquals(List.of("fallenking_villageleader"), quest.villagers().getFirst().requiredTags());
        assertFalse(quest.requiresWorldActions());
    }

    @Test
    void rendersPlaceholdersAndKeepsUnknownOnes() {
        assertEquals("Give it to Guillaume in $guard_villagename$.",
                QuestTexts.render("Give it to $guard_villagername$ in $guard_villagename$.", Map.of("guard_villagername", "Guillaume")));
        assertEquals("cost: $5", QuestTexts.render("cost: $5", Map.of()));
    }
}
