package org.millenaire.item;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.item.ArmorMaterial.Layer;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.SimpleTier;

public final class ByzantineMaterials {
   public static final Tier BYZANTINE_TOOL = new SimpleTier(
      BlockTags.INCORRECT_FOR_IRON_TOOL, 250, 6.0F, 2.0F, 14, () -> Ingredient.of(new ItemLike[]{Items.IRON_INGOT})
   );
   public static final int BYZANTINE_ARMOR_DURABILITY_MULTIPLIER = 33;
   public static final Holder<ArmorMaterial> BYZANTINE_ARMOR = Registry.registerForHolder(
      BuiltInRegistries.ARMOR_MATERIAL,
      ResourceLocation.fromNamespaceAndPath("millenaire", "byzantine"),
      new ArmorMaterial(
         (Map)Util.make(new EnumMap(Type.class), map -> {
            map.put(Type.BOOTS, 3);
            map.put(Type.LEGGINGS, 8);
            map.put(Type.CHESTPLATE, 6);
            map.put(Type.HELMET, 3);
         }),
         20,
         SoundEvents.ARMOR_EQUIP_IRON,
         () -> Ingredient.of(new ItemLike[]{Items.IRON_INGOT}),
         List.of(new Layer(ResourceLocation.fromNamespaceAndPath("millenaire", "byzantine"))),
         0.0F,
         0.0F
      )
   );

   private ByzantineMaterials() {
   }
}
