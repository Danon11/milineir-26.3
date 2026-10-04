package org.millenaire.block;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class FirePitInputSlot extends Slot {
   public FirePitInputSlot(Container container, int slot, int x, int y) {
      super(container, slot, x, y);
   }

   public boolean mayPlace(ItemStack stack) {
      if (this.container instanceof FirePitBlockEntity blockEntity) {
         Level level = blockEntity.getLevel();
         if (level != null) {
            return FirePitBlockEntity.isFirePitBurnable(stack, level);
         }
      }

      return FirePitBlockEntity.isFirePitBurnable(stack);
   }
}
