package org.millenaire.culture;

public record ReputationLabel(int threshold, String key) implements Comparable<ReputationLabel> {
   public int compareTo(ReputationLabel o) {
      return Integer.compare(this.threshold, o.threshold);
   }
}
