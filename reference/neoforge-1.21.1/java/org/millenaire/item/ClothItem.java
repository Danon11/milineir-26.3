package org.millenaire.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;

public class ClothItem extends Item {
   private final String clothName;
   private final int priority;

   public ClothItem(String clothName, int priority, Properties properties) {
      super(properties);
      this.clothName = clothName;
      this.priority = priority;
   }

   public String getClothName() {
      return this.clothName;
   }

   public int getPriority() {
      return this.priority;
   }
}
