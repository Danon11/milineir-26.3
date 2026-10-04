package org.millenaire.goal;

public abstract class ProgressAwareTask implements VillagerTask {
   private boolean progressMade;

   public void reportProgress() {
      this.progressMade = true;
   }

   public boolean consumeProgress() {
      boolean had = this.progressMade;
      this.progressMade = false;
      return had;
   }
}
