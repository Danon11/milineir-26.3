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

public final class NormanMaterials {
   public static final Tier NORMAN_TOOL = new SimpleTier(
      BlockTags.INCORRECT_FOR_IRON_TOOL, 1561, 10.0F, 4.0F, 10, () -> Ingredient.of(new ItemLike[]{Items.IRON_INGOT})
   );
   public static final int NORMAN_ARMOR_DURABILITY_MULTIPLIER = 66;
   public static final Holder<ArmorMaterial> NORMAN_ARMOR = Registry.registerForHolder(
      BuiltInRegistries.ARMOR_MATERIAL,
      ResourceLocation.fromNamespaceAndPath("millenaire", "norman"),
      new ArmorMaterial(
         (Map)Util.make(new EnumMap(Type.class), map -> {
            map.put(Type.BOOTS, 3);
            map.put(Type.LEGGINGS, 8);
            map.put(Type.CHESTPLATE, 6);
            map.put(Type.HELMET, 3);
         }),
         10,
         SoundEvents.ARMOR_EQUIP_IRON,
         () -> Ingredient.of(new ItemLike[]{Items.IRON_INGOT}),
         List.of(new Layer(ResourceLocation.fromNamespaceAndPath("millenaire", "norman"))),
         0.0F,
         0.0F
      )
   );

   private NormanMaterials() {
   }
}
