package org.millenaire.test.terrain;

import net.minecraft.core.BlockPos;

public record Violation(String invariant, BlockPos position, String expected, String actual, String context) {
   public String toString() {
      return "[%s] at (%d,%d,%d) — %s: expected %s, got %s"
         .formatted(this.invariant, this.position.getX(), this.position.getY(), this.position.getZ(), this.context, this.expected, this.actual);
   }
}
