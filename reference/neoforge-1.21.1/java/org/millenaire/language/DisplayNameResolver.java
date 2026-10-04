package org.millenaire.language;

import javax.annotation.Nullable;

public final class DisplayNameResolver {
   private DisplayNameResolver() {
   }

   public static String resolve(String resolvedText, boolean translatable, @Nullable String nativePrefix) {
      return resolve(resolvedText, translatable, nativePrefix, null);
   }

   public static String resolve(String resolvedText, boolean translatable, @Nullable String nativePrefix, @Nullable String originalKey) {
      if (!translatable) {
         return resolvedText;
      } else if (nativePrefix == null) {
         return resolvedText;
      } else if (resolvedText.isEmpty()) {
         return nativePrefix;
      } else if (originalKey != null && resolvedText.equals(originalKey)) {
         return nativePrefix;
      } else {
         return equivalent(nativePrefix, resolvedText) ? nativePrefix : nativePrefix + " (" + resolvedText + ")";
      }
   }

   public static boolean equivalent(String a, String b) {
      return a != null && b != null ? a.trim().equalsIgnoreCase(b.trim()) : a == b;
   }
}
