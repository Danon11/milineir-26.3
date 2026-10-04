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

public final class JapaneseMaterials {
   public static final Tier JAPANESE_TOOL = new SimpleTier(
      BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1561, 6.0F, 2.0F, 25, () -> Ingredient.of(new ItemLike[]{Items.DIAMOND})
   );
   public static final int JAPANESE_GUARD_ARMOR_DURABILITY_MULTIPLIER = 25;
   public static final Holder<ArmorMaterial> JAPANESE_GUARD_ARMOR = Registry.registerForHolder(
      BuiltInRegistries.ARMOR_MATERIAL,
      ResourceLocation.fromNamespaceAndPath("millenaire", "japanese_guard"),
      new ArmorMaterial(
         (Map)Util.make(new EnumMap(Type.class), map -> {
            map.put(Type.BOOTS, 2);
            map.put(Type.LEGGINGS, 5);
            map.put(Type.CHESTPLATE, 4);
            map.put(Type.HELMET, 1);
         }),
         25,
         SoundEvents.ARMOR_EQUIP_IRON,
         () -> Ingredient.of(new ItemLike[]{Items.IRON_INGOT}),
         List.of(new Layer(ResourceLocation.fromNamespaceAndPath("millenaire", "japanese_guard"))),
         0.0F,
         0.0F
      )
   );
   public static final int JAPANESE_BLUE_ARMOR_DURABILITY_MULTIPLIER = 33;
   public static final Holder<ArmorMaterial> JAPANESE_BLUE_ARMOR = Registry.registerForHolder(
      BuiltInRegistries.ARMOR_MATERIAL,
      ResourceLocation.fromNamespaceAndPath("millenaire", "japanese_blue"),
      new ArmorMaterial(
         (Map)Util.make(new EnumMap(Type.class), map -> {
            map.put(Type.BOOTS, 2);
            map.put(Type.LEGGINGS, 6);
            map.put(Type.CHESTPLATE, 5);
            map.put(Type.HELMET, 2);
         }),
         25,
         SoundEvents.ARMOR_EQUIP_IRON,
         () -> Ingredient.of(new ItemLike[]{Items.IRON_INGOT}),
         List.of(new Layer(ResourceLocation.fromNamespaceAndPath("millenaire", "japanese_blue"))),
         0.0F,
         0.0F
      )
   );
   public static final int JAPANESE_RED_ARMOR_DURABILITY_MULTIPLIER = 33;
   public static final Holder<ArmorMaterial> JAPANESE_RED_ARMOR = Registry.registerForHolder(
      BuiltInRegistries.ARMOR_MATERIAL,
      ResourceLocation.fromNamespaceAndPath("millenaire", "japanese_red"),
      new ArmorMaterial(
         (Map)Util.make(new EnumMap(Type.class), map -> {
            map.put(Type.BOOTS, 2);
            map.put(Type.LEGGINGS, 6);
            map.put(Type.CHESTPLATE, 5);
            map.put(Type.HELMET, 2);
         }),
         25,
         SoundEvents.ARMOR_EQUIP_IRON,
         () -> Ingredient.of(new ItemLike[]{Items.IRON_INGOT}),
         List.of(new Layer(ResourceLocation.fromNamespaceAndPath("millenaire", "japanese_red"))),
         0.0F,
         0.0F
      )
   );

   private JapaneseMaterials() {
   }
}
