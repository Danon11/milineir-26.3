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
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.item.ArmorMaterial.Layer;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

public final class InuitMaterials {
   public static final int FUR_ARMOR_DURABILITY_MULTIPLIER = 7;
   public static final Holder<ArmorMaterial> FUR_ARMOR = Registry.registerForHolder(
      BuiltInRegistries.ARMOR_MATERIAL,
      ResourceLocation.fromNamespaceAndPath("millenaire", "fur"),
      new ArmorMaterial(
         (Map)Util.make(new EnumMap(Type.class), map -> {
            map.put(Type.BOOTS, 2);
            map.put(Type.LEGGINGS, 5);
            map.put(Type.CHESTPLATE, 3);
            map.put(Type.HELMET, 1);
         }),
         25,
         SoundEvents.ARMOR_EQUIP_LEATHER,
         () -> Ingredient.of(new ItemLike[]{Items.LEATHER}),
         List.of(new Layer(ResourceLocation.fromNamespaceAndPath("millenaire", "fur"))),
         0.0F,
         0.0F
      )
   );

   private InuitMaterials() {
   }
}
