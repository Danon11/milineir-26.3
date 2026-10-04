package org.millenaire.content;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

final class OverlayContentFs implements ContentFs {
   private final LinkedHashMap<String, Resource> overlay;
   private final Map<String, List<Resource>> history;
   private final String prefix;

   OverlayContentFs(LinkedHashMap<String, Resource> overlay, Map<String, List<Resource>> history) {
      this(overlay, history, "");
   }

   private OverlayContentFs(LinkedHashMap<String, Resource> overlay, Map<String, List<Resource>> history, String prefix) {
      this.overlay = overlay;
      this.history = history;
      this.prefix = prefix;
   }

   public Optional<Resource> findFirst(String relPath) {
      if (relPath == null) {
         return Optional.empty();
      }

      String key = (this.prefix + normalise(relPath)).toLowerCase(Locale.ROOT);
      return Optional.ofNullable(this.overlay.get(key));
   }

   public List<Resource> findAll(String relPath) {
      if (relPath == null) {
         return List.of();
      }

      String key = (this.prefix + normalise(relPath)).toLowerCase(Locale.ROOT);
      List<Resource> hist = this.history.get(key);
      return hist == null ? List.of() : hist;
   }

   public Stream<Resource> walk(String relDir, int maxDepth) {
      String norm = normalise(relDir);
      String base = norm.isEmpty() ? stripTrailingSlash(this.prefix) : this.prefix + norm;
      String dir = base.toLowerCase(Locale.ROOT);
      String matchPrefix = dir.isEmpty() ? "" : dir + "/";
      boolean unbounded = maxDepth < 0 || maxDepth == Integer.MAX_VALUE;
      int prefixSlashes = countSlashes(matchPrefix);
      return this.overlay.keySet().stream().filter(k -> matchPrefix.isEmpty() || k.startsWith(matchPrefix)).filter(k -> {
         if (unbounded) {
            return true;
         }

         int slashes = countSlashes(k) - prefixSlashes;
         return slashes <= maxDepth;
      }).map(this.overlay::get);
   }

   public ContentFs sub(String relPath) {
      String norm = normalise(relPath);
      String newPrefix = norm.isEmpty() ? this.prefix : this.prefix + norm + "/";
      return new OverlayContentFs(this.overlay, this.history, newPrefix.toLowerCase(Locale.ROOT));
   }

   private static String normalise(String relPath) {
      if (relPath == null) {
         return "";
      }

      String s = relPath;
      int i = 0;

      while (i < s.length() && s.charAt(i) == '/') {
         i++;
      }

      if (i > 0) {
         s = s.substring(i);
      }

      int j = s.length();

      while (j > 0 && s.charAt(j - 1) == '/') {
         j--;
      }

      if (j < s.length()) {
         s = s.substring(0, j);
      }

      return s;
   }

   private static int countSlashes(String s) {
      int n = 0;

      for (int i = 0; i < s.length(); i++) {
         if (s.charAt(i) == '/') {
            n++;
         }
      }

      return n;
   }

   private static String stripTrailingSlash(String s) {
      return !s.isEmpty() && s.charAt(s.length() - 1) == '/' ? s.substring(0, s.length() - 1) : s;
   }
}
