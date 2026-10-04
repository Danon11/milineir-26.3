package org.millenaire.culture;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.millenaire.config.VillagerConfig;
import org.millenaire.entity.ModelType;
import org.millenaire.item.ItemHelper;

public record VillagerType(
   ResourceLocation id,
   ResourceLocation culture,
   String model,
   List<ResourceLocation> textures,
   Map<String, VillagerType.ClothSet> clothes,
   float baseScale,
   boolean isChild,
   List<ResourceLocation> goals,
   List<String> tags,
   int spawnWeight,
   Map<ResourceLocation, Integer> initialInventory,
   Gender gender,
   @Nullable String firstNameList,
   @Nullable String familyNameList,
   @Nullable String maleChild,
   @Nullable String femaleChild,
   List<String> bringBackHomeGoods,
   List<String> collectGoods,
   Map<String, Integer> requiredGoods,
   @Nullable String icon,
   List<String> toolNeededClasses,
   List<ResourceLocation> itemsNeeded,
   float maxHealth,
   @Nullable String villagerConfigKey,
   @Nullable String travelBookCategory,
   boolean travelBookDisplay,
   String nativeName,
   Map<ResourceLocation, Integer> foreignMerchantStock,
   int hiringCost,
   @Nullable String travelBookHeldItem,
   @Nullable String travelBookHeldItemOffHand,
   @Nullable String altNativeName,
   @Nullable String altKey,
   boolean travelBookMainCultureVillager,
   @Nullable ResourceLocation defaultWeapon,
   Set<Item> resolvedBringBackHomeGoods,
   Set<ResourceLocation> resolvedCollectGoods,
   Map<Item, Integer> resolvedRequiredGoods
) {
   public boolean hasTag(String tag) {
      return this.tags.contains(tag);
   }

   public ModelType modelType() {
      return ModelType.fromString(this.model);
   }

   public VillagerType withResolvedGoods() {
      Set<Item> resolvedBBH = new LinkedHashSet<>();

      for (String s : this.bringBackHomeGoods) {
         Item item = ItemHelper.resolve(s);
         if (item != null) {
            resolvedBBH.add(item);
         }
      }

      Set<ResourceLocation> resolvedCG = new LinkedHashSet<>();

      for (String s : this.collectGoods) {
         resolvedCG.add(ResourceLocation.parse(s));
      }

      Map<Item, Integer> resolvedRG = new LinkedHashMap<>();

      for (Entry<String, Integer> e : this.requiredGoods.entrySet()) {
         Item item = ItemHelper.resolve(e.getKey());
         if (item != null) {
            resolvedRG.put(item, e.getValue());
         }
      }

      VillagerConfig config = VillagerConfig.get(this.villagerConfigKey);
      if (this.isChild) {
         for (Entry<Item, Integer> entry : config.getFoodGrowth().entrySet()) {
            resolvedRG.putIfAbsent(entry.getKey(), 2);
         }
      }

      if (this.hasChildren()) {
         for (Entry<Item, Integer> entry : config.getFoodConception().entrySet()) {
            resolvedRG.putIfAbsent(entry.getKey(), 2);
         }
      }

      List<ResourceLocation> resolvedGoals = this.goals;
      if (!this.toolNeededClasses.isEmpty()) {
         ResourceLocation getToolId = ResourceLocation.fromNamespaceAndPath("millenaire", "get_tool");
         boolean found = false;

         for (ResourceLocation g : this.goals) {
            if (g.equals(getToolId)) {
               found = true;
               break;
            }
         }

         if (!found) {
            resolvedGoals = new ArrayList<>(this.goals);
            resolvedGoals.add(getToolId);
         }
      }

      return new VillagerType(
         this.id,
         this.culture,
         this.model,
         this.textures,
         this.clothes,
         this.baseScale,
         this.isChild,
         resolvedGoals,
         this.tags,
         this.spawnWeight,
         this.initialInventory,
         this.gender,
         this.firstNameList,
         this.familyNameList,
         this.maleChild,
         this.femaleChild,
         this.bringBackHomeGoods,
         this.collectGoods,
         this.requiredGoods,
         this.icon,
         this.toolNeededClasses,
         this.itemsNeeded,
         this.maxHealth,
         this.villagerConfigKey,
         this.travelBookCategory,
         this.travelBookDisplay,
         this.nativeName,
         this.foreignMerchantStock,
         this.hiringCost,
         this.travelBookHeldItem,
         this.travelBookHeldItemOffHand,
         this.altNativeName,
         this.altKey,
         this.travelBookMainCultureVillager,
         this.defaultWeapon,
         Set.copyOf(resolvedBBH),
         Set.copyOf(resolvedCG),
         Map.copyOf(resolvedRG)
      );
   }

   public boolean hasChildren() {
      return this.maleChild != null && this.femaleChild != null;
   }

   public boolean hasClothSet(String clothSetName) {
      return this.clothes.containsKey(clothSetName);
   }

   public boolean hasNaturalLayer(int layer) {
      VillagerType.ClothSet natural = this.clothes.get("natural");
      if (natural == null) {
         return false;
      }

      List<ResourceLocation> textures = layer == 0 ? natural.layer0() : natural.layer1();
      return textures != null && !textures.isEmpty();
   }

   public record ClothSet(List<ResourceLocation> layer0, List<ResourceLocation> layer1) {
   }
}
