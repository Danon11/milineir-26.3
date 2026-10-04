package org.millenaire.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.SimpleTier;

public final class MayanMaterials {
   public static final Tier OBSIDIAN_TOOL = new SimpleTier(
      BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1561, 6.0F, 2.0F, 25, () -> Ingredient.of(new ItemLike[]{Items.DIAMOND})
   );
   public static final Tier IRON_TOOL = new SimpleTier(
      BlockTags.INCORRECT_FOR_IRON_TOOL, 250, 6.0F, 2.0F, 14, () -> Ingredient.of(new ItemLike[]{Items.IRON_INGOT})
   );

   private MayanMaterials() {
   }
}
