package org.millenaire.building;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.millenaire.content.ContentFs;
import org.millenaire.content.Resource;
import org.slf4j.Logger;

public final class SpecialPointsLoader {
   private static final Logger LOGGER = LogUtils.getLogger();

   private SpecialPointsLoader() {
   }

   public static List<SpecialPoint> load(ResourceLocation templateId, CompoundTag templateNbt) {
      List<SpecialPoint> fromNbt = MockBlockExtractor.extract(templateNbt);
      if (!fromNbt.isEmpty()) {
         LOGGER.debug("Loaded {} special points from NBT: {}", fromNbt.size(), templateId);
         return fromNbt;
      } else {
         return Collections.emptyList();
      }
   }

   public static List<SpecialPoint> load(ResourceLocation templateId, String nbtPath, ContentFs cultureFs) {
      if (nbtPath != null && cultureFs != null) {
         Optional<Resource> resource = cultureFs.findFirst(nbtPath + "_special_points.json");
         if (resource.isEmpty()) {
            LOGGER.debug("No special points sidecar at {}", nbtPath);
            return Collections.emptyList();
         }

         try (InputStream is = resource.get().open()) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray pointsArray = root.getAsJsonArray("points");
            if (pointsArray == null) {
               return Collections.emptyList();
            }

            List<SpecialPoint> points = new ArrayList<>();

            for (JsonElement elem : pointsArray) {
               JsonObject obj = elem.getAsJsonObject();
               String type = GsonHelper.getAsString(obj, "type");
               String subtype = GsonHelper.getAsString(obj, "subtype", null);
               String orientation = GsonHelper.getAsString(obj, "orientation", null);
               String placement = GsonHelper.getAsString(obj, "placement", null);
               int x;
               int y;
               int z;
               if (obj.has("pos")) {
                  JsonArray posArr = obj.getAsJsonArray("pos");
                  x = posArr.get(0).getAsInt();
                  y = posArr.get(1).getAsInt();
                  z = posArr.get(2).getAsInt();
               } else {
                  x = obj.get("x").getAsInt();
                  y = obj.get("y").getAsInt();
                  z = obj.get("z").getAsInt();
               }

               points.add(new SpecialPoint(type, subtype, orientation, placement, new BlockPos(x, y, z)));
            }

            LOGGER.debug("Loaded {} special points from {}", points.size(), resource.get().relPath());
            return Collections.unmodifiableList(points);
         } catch (Exception e) {
            LOGGER.warn("Error loading sidecar {}", resource.get().relPath(), e);
            return Collections.emptyList();
         }
      } else {
         LOGGER.debug("SpecialPoints: missing path/fs for {}", templateId);
         return Collections.emptyList();
      }
   }
}
