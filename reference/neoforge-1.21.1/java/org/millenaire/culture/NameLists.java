package org.millenaire.culture;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public record NameLists(Map<String, List<String>> lists) {
   public String randomFrom(String listKey) {
      List<String> names = this.lists.get(listKey);
      return names != null && !names.isEmpty() ? names.get(ThreadLocalRandom.current().nextInt(names.size())) : null;
   }
}
