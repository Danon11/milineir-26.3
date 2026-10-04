package org.millenaire.content;

public record SourceLabel(String displayName) {
   public SourceLabel(String displayName) {
      if (displayName != null && !displayName.isEmpty()) {
         this.displayName = displayName;
      } else {
         throw new IllegalArgumentException("SourceLabel.displayName must be non-empty");
      }
   }
}
