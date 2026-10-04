package org.millenaire.diagnostics;

public record NavEvent(long tick, NavEvent.Layer layer, NavEvent.Type type, String detail) {
   public String toString() {
      return "[t=" + this.tick + "] " + this.layer + "/" + this.type + (this.detail.isEmpty() ? "" : " " + this.detail);
   }

   public enum Layer {
      VNM,
      WAYPOINT,
      GATHERING,
      REST,
      SCHEDULER,
      RELOAD;
   }

   public enum Type {
      NAV_START,
      NAV_STOP,
      REPATH,
      SHORT_JUMP,
      TELEPORT,
      STUCK_DETECTED,
      TARGET_INVALID,
      ACTING_WATCHDOG_FIRED,
      GOAL_ABANDONED,
      POSE_SLEEPING_RESTORED,
      POSE_SLEEPING_CLEARED,
      BED_SUFFOCATION,
      LEAF_CLEAR_SKIPPED;
   }
}
