package org.millenaire.content;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;

public final class ContentLoadReport {
   private static final Map<String, Set<String>> MISSINGS_BY_CULTURE = new LinkedHashMap<>();

   private ContentLoadReport() {
   }

   public static void clear() {
      synchronized (MISSINGS_BY_CULTURE) {
         MISSINGS_BY_CULTURE.clear();
      }
   }

   public static void recordMissingClasspathFile(String culture, String classpathPath) {
      if (culture != null && classpathPath != null) {
         if (!BuiltInCultures.IDS.contains(culture)) {
            synchronized (MISSINGS_BY_CULTURE) {
               MISSINGS_BY_CULTURE.computeIfAbsent(culture, k -> new TreeSet<>()).add(classpathPath);
            }
         }
      }
   }

   public static Map<String, List<String>> snapshot() {
      Map<String, List<String>> out = new TreeMap<>();
      synchronized (MISSINGS_BY_CULTURE) {
         for (Entry<String, Set<String>> e : MISSINGS_BY_CULTURE.entrySet()) {
            out.put(e.getKey(), List.copyOf(e.getValue()));
         }
      }

      return Collections.unmodifiableMap(out);
   }

   public static boolean isEmpty() {
      synchronized (MISSINGS_BY_CULTURE) {
         return MISSINGS_BY_CULTURE.isEmpty();
      }
   }
}
