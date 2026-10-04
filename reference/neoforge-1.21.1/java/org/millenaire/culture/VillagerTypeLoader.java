package org.millenaire.culture;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.millenaire.content.ContentFs;
import org.millenaire.tool.ToolCategoryRegistry;
import org.slf4j.Logger;

final class VillagerTypeLoader {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Map<ResourceLocation, String> SEEN_VILLAGER_IDS = new HashMap<>();
   private static final Set<ResourceLocation> FAILED_CULTURES = new HashSet<>();

   private VillagerTypeLoader() {
   }

   static void resetCrossFolderTracking() {
      SEEN_VILLAGER_IDS.clear();
      FAILED_CULTURES.clear();
   }

   static void loadFromContentFs(String contentId, ContentFs cultureFs, String relPath) {
      ResourceLocation id = ResourceLocation.fromNamespaceAndPath("millenaire", contentId);
      JsonObject json = CultureLoader.readJsonFromContentFs(cultureFs, relPath);
      if (json != null) {
         loadFromJson(id, relPath, json);
      }
   }

   static void loadFromClasspath(String contentId, String classpathPath) {
      ResourceLocation id = ResourceLocation.fromNamespaceAndPath("millenaire", contentId);
      JsonObject json = CultureLoader.readJson(classpathPath);
      if (json != null) {
         loadFromJson(id, classpathPath, json);
      }
   }

   private static void loadFromJson(ResourceLocation id, String pathKey, JsonObject json) {
      try {
         ResourceLocation culture = ResourceLocation.parse(GsonHelper.getAsString(json, "culture"));
         if (FAILED_CULTURES.contains(culture)) {
            return;
         }

         String previousJsonPath = SEEN_VILLAGER_IDS.putIfAbsent(id, pathKey);
         if (previousJsonPath != null && !previousJsonPath.equals(pathKey)) {
            LOGGER.error(
               "[Millenaire] Duplicate villager_type id '{}': loaded from '{}', later occurrence at '{}'. Cross-folder logical-id collisions are fatal — culture '{}' will not register this villager type. Move or rename one of the files so each id is unique within the culture.",
               new Object[]{id, previousJsonPath, pathKey, culture}
            );
            FAILED_CULTURES.add(culture);
            throw new CrossFolderConflictException(
               culture, "Duplicate villager_type id '" + id + "' (first at '" + previousJsonPath + "', second at '" + pathKey + "')"
            );
         }

         String model = GsonHelper.getAsString(json, "model", "male");
         List<ResourceLocation> textures = new ArrayList<>();
         if (json.has("textures")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "textures")) {
               textures.add(ResourceLocation.parse(e.getAsString()));
            }
         } else if (json.has("texture")) {
            textures.add(ResourceLocation.parse(GsonHelper.getAsString(json, "texture")));
         }

         Map<String, VillagerType.ClothSet> clothes = new LinkedHashMap<>();
         if (json.has("clothes")) {
            JsonObject clothesJson = GsonHelper.getAsJsonObject(json, "clothes");

            for (Entry<String, JsonElement> entry : clothesJson.entrySet()) {
               JsonObject clothObj = entry.getValue().getAsJsonObject();
               List<ResourceLocation> layer0 = CultureLoader.parseResourceLocationList(clothObj, "layer0");
               List<ResourceLocation> layer1 = CultureLoader.parseResourceLocationList(clothObj, "layer1");
               clothes.put(entry.getKey(), new VillagerType.ClothSet(layer0, layer1));
            }
         }

         float baseScale = GsonHelper.getAsFloat(json, "base_scale", 1.0F);
         boolean isChild = GsonHelper.getAsBoolean(json, "is_child", false);
         List<ResourceLocation> goals = new ArrayList<>();

         for (JsonElement e : GsonHelper.getAsJsonArray(json, "goals")) {
            goals.add(ResourceLocation.parse(e.getAsString()));
         }

         List<String> tags = JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(json, "tags"));
         int spawnWeight = GsonHelper.getAsInt(json, "spawn_weight");
         Map<ResourceLocation, Integer> initialInventory = json.has("initial_inventory")
            ? CultureLoader.parseResourceIntMap(GsonHelper.getAsJsonObject(json, "initial_inventory"))
            : Map.of();
         Gender gender;
         if (json.has("gender")) {
            gender = Gender.fromString(GsonHelper.getAsString(json, "gender"));
         } else {
            gender = model.startsWith("female") ? Gender.FEMALE : Gender.MALE;
         }

         String firstNameList = json.has("first_name_list") ? GsonHelper.getAsString(json, "first_name_list") : null;
         String familyNameList = json.has("family_name_list") ? GsonHelper.getAsString(json, "family_name_list") : null;
         String maleChild = json.has("male_child") ? GsonHelper.getAsString(json, "male_child") : null;
         String femaleChild = json.has("female_child") ? GsonHelper.getAsString(json, "female_child") : null;
         List<String> bringBackHomeGoods = json.has("bring_back_home_goods")
            ? JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(json, "bring_back_home_goods"))
            : List.of();
         List<String> collectGoods = json.has("collect_goods") ? JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(json, "collect_goods")) : List.of();
         Map<String, Integer> requiredGoods = new HashMap<>();
         if (json.has("required_goods")) {
            JsonObject rgObj = GsonHelper.getAsJsonObject(json, "required_goods");

            for (Entry<String, JsonElement> entry : rgObj.entrySet()) {
               requiredGoods.put(entry.getKey(), entry.getValue().getAsInt());
            }
         }

         String villagerIcon = json.has("icon") ? GsonHelper.getAsString(json, "icon") : null;
         List<String> toolNeededClasses = new ArrayList<>();
         if (json.has("tool_needed_classes")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "tool_needed_classes")) {
               toolNeededClasses.addAll(ToolCategoryRegistry.expandToolClass(e.getAsString()));
            }
         }

         List<ResourceLocation> itemsNeeded = new ArrayList<>();
         if (json.has("items_needed")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "items_needed")) {
               itemsNeeded.add(ResourceLocation.parse(e.getAsString()));
            }
         }

         float maxHealth = json.has("max_health") ? GsonHelper.getAsFloat(json, "max_health") : 20.0F;
         String villagerConfigKey = json.has("villager_config") ? GsonHelper.getAsString(json, "villager_config") : null;
         String travelBookCategory = json.has("travel_book_category") ? GsonHelper.getAsString(json, "travel_book_category") : null;
         boolean travelBookDisplay = GsonHelper.getAsBoolean(json, "travel_book_display", true);
         String nativeName = GsonHelper.getAsString(json, "native_name", CultureLoader.formatSimpleName(id.getPath()));
         Map<ResourceLocation, Integer> foreignMerchantStock = new HashMap<>();
         if (json.has("foreign_merchant_stock")) {
            JsonObject fmsObj = GsonHelper.getAsJsonObject(json, "foreign_merchant_stock");

            for (Entry<String, JsonElement> entry : fmsObj.entrySet()) {
               foreignMerchantStock.put(ResourceLocation.parse(entry.getKey()), entry.getValue().getAsInt());
            }
         }

         int hiringCost = 0;
         if (json.has("hiring_cost")) {
            JsonElement hcElem = json.get("hiring_cost");
            if (hcElem.isJsonPrimitive() && hcElem.getAsJsonPrimitive().isNumber()) {
               hiringCost = hcElem.getAsInt();
            } else {
               try {
                  hiringCost = Integer.parseInt(hcElem.getAsString());
               } catch (NumberFormatException var38) {
               }
            }
         }

         String travelBookHeldItem = json.has("travelbook_held_item") ? GsonHelper.getAsString(json, "travelbook_held_item") : null;
         String travelBookHeldItemOffHand = json.has("travelbook_held_item_off_hand") ? GsonHelper.getAsString(json, "travelbook_held_item_off_hand") : null;
         String altNativeName = json.has("alt_native_name") ? GsonHelper.getAsString(json, "alt_native_name") : null;
         String altKey = json.has("alt_key") ? GsonHelper.getAsString(json, "alt_key") : null;
         boolean travelBookMainCultureVillager = GsonHelper.getAsBoolean(json, "travelbook_main_culture_villager", false);
         ResourceLocation defaultWeapon = json.has("default_weapon") ? ResourceLocation.parse(GsonHelper.getAsString(json, "default_weapon")) : null;
         ModCultures.registerVillagerType(
            new VillagerType(
               id,
               culture,
               model,
               textures,
               clothes,
               baseScale,
               isChild,
               goals,
               tags,
               spawnWeight,
               initialInventory,
               gender,
               firstNameList,
               familyNameList,
               maleChild,
               femaleChild,
               bringBackHomeGoods,
               collectGoods,
               requiredGoods,
               villagerIcon,
               toolNeededClasses,
               itemsNeeded,
               maxHealth,
               villagerConfigKey,
               travelBookCategory,
               travelBookDisplay,
               nativeName,
               foreignMerchantStock,
               hiringCost,
               travelBookHeldItem,
               travelBookHeldItemOffHand,
               altNativeName,
               altKey,
               travelBookMainCultureVillager,
               defaultWeapon,
               Set.of(),
               Set.of(),
               Map.of()
            )
         );
      } catch (CrossFolderConflictException e) {
         throw e;
      } catch (Exception e) {
         LOGGER.error("Error parsing villager type {}: {}", new Object[]{id, e.getMessage(), e});
      }
   }
}
