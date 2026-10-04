package org.millenaire.language;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.content.BuiltInCultures;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.ReputationLabel;
import org.millenaire.culture.VillagerType;
import org.slf4j.Logger;

public final class I18nKeyAuditor {
   private static final Logger LOGGER = LogUtils.getLogger();

   private I18nKeyAuditor() {
   }

   public static void audit() {
      List<String> missingCultures = new ArrayList<>();
      List<String> missingRoles = new ArrayList<>();
      List<String> missingGoals = new ArrayList<>();
      List<String> missingReputations = new ArrayList<>();
      Set<String> builtIn = new HashSet<>(BuiltInCultures.IDS);
      Map<ResourceLocation, Culture> cultures = ModCultures.getAllCultures();

      for (ResourceLocation cId : cultures.keySet()) {
         if (builtIn.contains(cId.getPath())) {
            String key = "culture.millenaire." + cId.getPath();
            if (!ServerTranslationCache.has(key)) {
               missingCultures.add(key);
            }
         }
      }

      TreeSet<String> seenGoalKeys = new TreeSet<>();

      for (Entry<ResourceLocation, VillagerType> entry : ModCultures.getAllVillagerTypes().entrySet()) {
         VillagerType vt = entry.getValue();
         ResourceLocation vtCulture = ModCultures.extractCultureId(vt.id());
         if (builtIn.contains(vtCulture.getPath())) {
            String qualifiedRole = vt.id().getPath().replace('/', '_');
            String roleKey = "role.millenaire." + qualifiedRole;
            if (!ServerTranslationCache.has(roleKey)) {
               missingRoles.add(roleKey);
            }

            for (ResourceLocation goalId : vt.goals()) {
               String goalKey = "goal.millenaire." + goalId.getPath();
               if (seenGoalKeys.add(goalKey) && !ServerTranslationCache.has(goalKey)) {
                  missingGoals.add(goalKey);
               }
            }
         }
      }

      TreeSet<String> seenRepKeys = new TreeSet<>();

      for (ResourceLocation cId : cultures.keySet()) {
         if (builtIn.contains(cId.getPath())) {
            collectReputationGaps(ModCultures.getReputationLabels(cId), seenRepKeys, missingReputations);
            collectReputationGaps(ModCultures.getCultureReputationLabels(cId), seenRepKeys, missingReputations);
         }
      }

      int total = missingCultures.size() + missingRoles.size() + missingGoals.size() + missingReputations.size();
      if (total == 0) {
         LOGGER.info("[I18nKeyAuditor] All referenced i18n keys are present in en_us.json");
      } else {
         StringBuilder sb = new StringBuilder();
         sb.append("[I18nKeyAuditor] ").append(total).append(" i18n keys referenced by loaded content but missing from en_us.json:");
         appendCategory(sb, "cultures", missingCultures);
         appendCategory(sb, "roles", missingRoles);
         appendCategory(sb, "goals", missingGoals);
         appendCategory(sb, "reputations", missingReputations);
         LOGGER.warn(sb.toString());
      }
   }

   private static void collectReputationGaps(List<ReputationLabel> labels, TreeSet<String> seen, List<String> missing) {
      if (labels != null) {
         for (ReputationLabel label : labels) {
            String key = label.key();
            if (seen.add(key) && !ServerTranslationCache.has(key)) {
               missing.add(key);
            }
         }
      }
   }

   private static void appendCategory(StringBuilder sb, String name, List<String> missing) {
      if (!missing.isEmpty()) {
         sb.append("\n  ").append(name).append(" (").append(missing.size()).append("):");

         for (String key : missing) {
            sb.append("\n    - ").append(key);
         }
      }
   }
}
