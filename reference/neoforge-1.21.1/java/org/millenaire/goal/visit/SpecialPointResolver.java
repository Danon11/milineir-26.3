package org.millenaire.goal.visit;

import java.util.Set;
import javax.annotation.Nullable;
import org.millenaire.building.SpecialPoint;

public final class SpecialPointResolver {
   private static final Set<String> VALID_VALUES = SpecialPoint.values();

   private SpecialPointResolver() {
   }

   @Nullable
   public static String resolveOrThrow(@Nullable String value, String context) {
      if (value == null) {
         return null;
      } else if (VALID_VALUES.contains(value)) {
         return value;
      } else {
         throw new IllegalArgumentException("Unknown SpecialPoint value '" + value + "' in " + context + " — expected one of " + VALID_VALUES);
      }
   }
}
