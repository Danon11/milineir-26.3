package org.millenaire.block;

import net.minecraft.world.item.DyeColor;

public interface IPaintedBlock {
   DyeColor getColor();

   PaintedBrickBlock.BrickType getBrickType();
}
