package org.millenaire.block;

import net.minecraft.core.Direction.Axis;
import net.minecraft.util.StringRepresentable;

public enum FirePitAlignment implements StringRepresentable {
   X("x", 90.0),
   Z("z", 0.0);

   private final String name;
   public final double angle;

   FirePitAlignment(String name, double angle) {
      this.name = name;
      this.angle = angle;
   }

   public String getSerializedName() {
      return this.name;
   }

   public static FirePitAlignment fromAxis(Axis axis) {
      return axis == Axis.X ? Z : X;
   }
}
