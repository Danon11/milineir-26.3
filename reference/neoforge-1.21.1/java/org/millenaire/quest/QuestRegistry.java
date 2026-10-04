package org.millenaire.quest;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

public final class QuestRegistry {
   private static final Map<String, Quest> QUESTS = new LinkedHashMap<>();

   private QuestRegistry() {
   }

   public static void clear() {
      QUESTS.clear();
   }

   public static void register(Quest q) {
      QUESTS.put(q.key(), q);
   }

   @Nullable
   public static Quest get(String key) {
      return QUESTS.get(key);
   }

   public static Collection<Quest> all() {
      return Collections.unmodifiableCollection(QUESTS.values());
   }

   public static int size() {
      return QUESTS.size();
   }
}
