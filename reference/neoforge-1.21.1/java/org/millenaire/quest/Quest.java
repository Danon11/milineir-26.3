package org.millenaire.quest;

import java.util.List;

public record Quest(
   String key,
   double chancePerHour,
   int maxSimultaneous,
   int minReputation,
   List<String> globalTagsRequired,
   List<String> globalTagsForbidden,
   List<String> playerTagsRequired,
   List<String> playerTagsForbidden,
   List<QuestVillagerDef> villagerDefs,
   List<QuestStep> steps
) {
}
