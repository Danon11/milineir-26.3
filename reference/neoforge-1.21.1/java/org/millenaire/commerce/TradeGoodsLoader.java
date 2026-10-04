package org.millenaire.commerce;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import org.millenaire.content.Resource;
import org.millenaire.culture.JsonLoaderUtils;
import org.millenaire.item.ItemHelper;
import org.slf4j.Logger;

public final class TradeGoodsLoader {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Gson GSON = JsonLoaderUtils.GSON;
   private static final Map<ResourceLocation, List<TradeGood>> GOODS = new ConcurrentHashMap<>();
   private static final Map<ResourceLocation, Map<Item, Integer>> TARGET_QTY_CACHE = new ConcurrentHashMap<>();
   private static final Set<String> WARNED_REPLACE_IDS = ConcurrentHashMap.newKeySet();

   private TradeGoodsLoader() {
   }

   public static void loadFromResources(ResourceLocation cultureId, List<Resource> resources) {
      Map<String, TradeGood> byId = new LinkedHashMap<>();
      int total = 0;
      int removed = 0;
      int duplicatesIgnored = 0;
      if (resources != null && !resources.isEmpty()) {
         for (int i = resources.size() - 1; i >= 0; i--) {
            Resource res = resources.get(i);
            String layerLabel = res.source().displayName();

            try (InputStream stream = res.open()) {
               List<TradeGoodsLoader.Entry> entries = parseEntries(cultureId, stream, layerLabel);
               if (entries != null) {
                  for (TradeGoodsLoader.Entry e : entries) {
                     String id = e.good().id();
                     if (e.disabled()) {
                        if (byId.remove(id) != null) {
                           removed++;
                        }
                     } else if (byId.containsKey(id)) {
                        duplicatesIgnored++;
                        String warnKey = "traded_goods:" + cultureId + ":" + id;
                        if (WARNED_REPLACE_IDS.add(warnKey)) {
                           LOGGER.debug(
                              "Traded good '{}' already declared for culture {}; ignoring duplicate from '{}' (use \"disabled\":true to override)",
                              new Object[]{id, cultureId, layerLabel}
                           );
                        }
                     } else {
                        byId.put(id, e.good());
                        total++;
                     }
                  }
               }
            } catch (IOException e) {
               LOGGER.error("[Millenaire] Error reading traded_goods {} for {}: {}", new Object[]{res.relPath(), cultureId, e.getMessage(), e});
            }
         }

         GOODS.put(cultureId, Collections.unmodifiableList(new ArrayList<>(byId.values())));
         TARGET_QTY_CACHE.remove(cultureId);
         if (resources.size() <= 1 && duplicatesIgnored <= 0 && removed <= 0) {
            LOGGER.info("[Millenaire] Loaded {} goods for culture {}", byId.size(), cultureId);
         } else {
            LOGGER.info(
               "[Millenaire] Loaded {} goods for culture {} (sources={}, removed={}, duplicates ignored={})",
               new Object[]{byId.size(), cultureId, resources.size(), removed, duplicatesIgnored}
            );
         }
      } else {
         GOODS.put(cultureId, Collections.unmodifiableList(new ArrayList<>(byId.values())));
         TARGET_QTY_CACHE.remove(cultureId);
         LOGGER.info("[Millenaire] Loaded 0 goods for culture {}", cultureId);
      }
   }

   @Nullable
   private static List<TradeGoodsLoader.Entry> parseEntries(ResourceLocation cultureId, InputStream stream, String layer) {
      try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
         JsonObject root = (JsonObject)GSON.fromJson(reader, JsonObject.class);
         JsonArray goodsArray = root.getAsJsonArray("goods");
         if (goodsArray == null) {
            LOGGER.warn("[Millenaire] traded_goods for {} ({} layer): no 'goods' array", cultureId, layer);
            return null;
         }

         List<TradeGoodsLoader.Entry> out = new ArrayList<>();

         for (JsonElement element : goodsArray) {
            JsonObject obj = element.getAsJsonObject();
            String id = GsonHelper.getAsString(obj, "id");
            boolean disabled = GsonHelper.getAsBoolean(obj, "disabled", false);
            if (disabled) {
               out.add(new TradeGoodsLoader.Entry(new TradeGood(id, "", 0, 0, 0, 0, false, 0, "misc", true, 0), true));
            } else {
               String item = GsonHelper.getAsString(obj, "item");
               int sellingPrice = GsonHelper.getAsInt(obj, "selling_price", 0);
               int buyingPrice = GsonHelper.getAsInt(obj, "buying_price", 0);
               int reservedQuantity = GsonHelper.getAsInt(obj, "reserved_quantity", 0);
               int targetQuantity = GsonHelper.getAsInt(obj, "target_quantity", 0);
               boolean autoGenerate = GsonHelper.getAsBoolean(obj, "auto_generate", false);
               int minReputation = GsonHelper.getAsInt(obj, "min_reputation", 0);
               String category = GsonHelper.getAsString(obj, "category", "misc");
               boolean travelBookDisplay = GsonHelper.getAsBoolean(obj, "travel_book_display", true);
               int foreignMerchantPrice = GsonHelper.getAsInt(obj, "foreign_merchant_price", 0);
               out.add(
                  new TradeGoodsLoader.Entry(
                     new TradeGood(
                        id,
                        item,
                        sellingPrice,
                        buyingPrice,
                        reservedQuantity,
                        targetQuantity,
                        autoGenerate,
                        minReputation,
                        category,
                        travelBookDisplay,
                        foreignMerchantPrice
                     ),
                     false
                  )
               );
            }
         }

         return out;
      } catch (Exception e) {
         LOGGER.error("[Millenaire] Error parsing traded_goods ({} layer) for {}", new Object[]{layer, cultureId, e});
         return null;
      }
   }

   public static List<TradeGood> getGoods(ResourceLocation cultureId) {
      return GOODS.getOrDefault(cultureId, Collections.emptyList());
   }

   @Nullable
   public static TradeGood getGoodById(ResourceLocation cultureId, String goodId) {
      for (TradeGood good : getGoods(cultureId)) {
         if (good.id().equals(goodId)) {
            return good;
         }
      }

      return null;
   }

   public static Map<Item, Integer> getTargetQuantities(ResourceLocation cultureId) {
      return TARGET_QTY_CACHE.computeIfAbsent(cultureId, id -> {
         List<TradeGood> goods = getGoods(id);
         Map<Item, Integer> map = new HashMap<>();

         for (TradeGood good : goods) {
            if (!good.isTag() && good.targetQuantity() > 0) {
               Item item = ItemHelper.resolve(good.item());
               if (item != null) {
                  map.merge(item, good.targetQuantity(), Integer::sum);
               }
            }
         }

         return Collections.unmodifiableMap(map);
      });
   }

   public static void clear() {
      GOODS.clear();
      TARGET_QTY_CACHE.clear();
      WARNED_REPLACE_IDS.clear();
   }

   private record Entry(TradeGood good, boolean disabled) {
   }
}
