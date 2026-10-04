package org.millenaire.village;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;

public record BrickColourTheme(String name, int weight, Map<DyeColor, List<BrickColourTheme.WeightedColor>> colorPools) {
   public DyeColor rollColor(DyeColor input, RandomSource random) {
      List<BrickColourTheme.WeightedColor> pool = this.colorPools.get(input);
      if (pool != null && !pool.isEmpty()) {
         int totalWeight = 0;

         for (BrickColourTheme.WeightedColor wc : pool) {
            totalWeight += wc.weight();
         }

         int roll = random.nextInt(totalWeight);
         int cumulative = 0;

         for (BrickColourTheme.WeightedColor wc : pool) {
            cumulative += wc.weight();
            if (roll < cumulative) {
               return wc.color();
            }
         }

         return DyeColor.WHITE;
      } else {
         return input;
      }
   }

   public Map<DyeColor, DyeColor> rollBuildingMapping(RandomSource random) {
      Map<DyeColor, DyeColor> mapping = new EnumMap<>(DyeColor.class);

      for (DyeColor color : DyeColor.values()) {
         mapping.put(color, this.rollColor(color, random));
      }

      return mapping;
   }

   public record WeightedColor(DyeColor color, int weight) {
   }
}
