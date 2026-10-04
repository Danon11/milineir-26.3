package org.millenaire.building;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.millenaire.block.ImportTableBlockEntity;
import org.millenaire.block.mock.MockBlock;
import org.millenaire.content.BuiltInCultures;
import org.millenaire.content.ContentDirectoryManager;
import org.millenaire.content.CustomContentIndex;
import org.millenaire.culture.ModCultures;
import org.millenaire.village.BrickColourTheme;
import org.slf4j.Logger;

public final class BuildingExporter {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final String PRESERVE_GROUND_BLOCK = "millenaire:mock_marker[type=preserve_ground]";
   private static final Set<String> GROUND_BLOCKS = Set.of("minecraft:grass_block", "minecraft:dirt", "minecraft:sand");
   private static final String SNOW_BLOCK_PREFIX = "minecraft:snow";

   private BuildingExporter() {
   }

   public static void exportLevel(ServerLevel level, ImportTableBlockEntity be, ServerPlayer player) {
      Path exportDir = getExportDirFor(level, be);
      ensureDir(exportDir);
      String buildingId = be.getBuildingId();
      String variant = be.getVariant();
      int upgradeLevel = be.getUpgradeLevel();
      BlockPos origin = computeScanOrigin(be);
      int detectedHeight = detectBuildingHeight(level, origin, be, upgradeLevel);
      be.setHeight(detectedHeight);
      Vec3i size = computeScanSize(be);
      StructureTemplate template = new StructureTemplate();
      template.fillFromWorld(level, origin, size, false, null);
      CompoundTag nbt = template.save(new CompoundTag());
      postProcessNbt(nbt, be);
      stripPreviousLevelBlocks(nbt, be, level);
      compactPalette(nbt);
      String nbtFileName = buildingId + "_" + variant + "_" + upgradeLevel + ".nbt";
      Path nbtPath = exportDir.resolve(nbtFileName);

      try {
         NbtIo.writeCompressed(nbt, nbtPath);
      } catch (IOException e) {
         LOGGER.error("Failed to write NBT export: {}", nbtPath, e);
         player.sendSystemMessage(Component.literal("§c[ImportTable] Failed to export NBT: " + e.getMessage()));
         return;
      }

      updateBuildingJson(exportDir, be);
      int copiedLevels = copyMissingCultureVariantLevelsToExports(level, exportDir, be);
      ensureExportJsonHasCopiedVariantLevels(exportDir, be);
      refreshOverlay();
      player.sendSystemMessage(
         Component.literal(
            "§a[ImportTable] Exported "
               + buildingId
               + " "
               + variant
               + " level "
               + upgradeLevel
               + " to "
               + nbtFileName
               + " (height: "
               + detectedHeight
               + (copiedLevels > 0 ? ", copied " + copiedLevels + " linked upgrade(s)" : "")
               + ")"
         )
      );
      be.captureSavedState(computeBlocksHash(level, be));
      be.setDirty(false);
   }

   public static void exportNewLevel(ServerLevel level, ImportTableBlockEntity be, ServerPlayer player) {
      Path exportDir = getExportDirFor(level, be);
      ensureDir(exportDir);
      String buildingId = be.getBuildingId();
      String variant = be.getVariant();
      int currentMax = be.getUpgradeLevel();
      String sourceFile = buildingId + "_" + variant + "_" + currentMax + ".nbt";
      int nextLevel = currentMax + 1;
      String destFile = buildingId + "_" + variant + "_" + nextLevel + ".nbt";
      Path sourcePath = exportDir.resolve(sourceFile);
      Path destPath = exportDir.resolve(destFile);
      if (!Files.exists(sourcePath)) {
         player.sendSystemMessage(Component.literal("§c[ImportTable] Source NBT not found: " + sourceFile));
      } else {
         try {
            Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
         } catch (IOException e) {
            LOGGER.error("Failed to copy NBT for new level: {} -> {}", new Object[]{sourcePath, destPath, e});
            player.sendSystemMessage(Component.literal("§c[ImportTable] Failed to create new level: " + e.getMessage()));
            return;
         }

         be.setUpgradeLevel(nextLevel);
         updateBuildingJson(exportDir, be);
         refreshOverlay();
         player.sendSystemMessage(Component.literal("§a[ImportTable] Created new level " + nextLevel + " for " + buildingId + " " + variant));
      }
   }

   private static void postProcessNbt(CompoundTag nbt, ImportTableBlockEntity be) {
      if (nbt.contains("palette", 9)) {
         if (nbt.contains("blocks", 9)) {
            ListTag palette = nbt.getList("palette", 10);
            ListTag blocks = nbt.getList("blocks", 10);
            Map<Integer, String> paletteIndexToName = new HashMap<>();

            for (int i = 0; i < palette.size(); i++) {
               CompoundTag entry = palette.getCompound(i);
               paletteIndexToName.put(i, entry.getString("Name"));
            }

            if (!be.isExportSnow()) {
               removeSnowBlocks(blocks, paletteIndexToName);
            }

            if (be.isConvertToPreserveGround()) {
               convertToPreserveGround(palette, blocks, paletteIndexToName);
            }
         }
      }
   }

   private static boolean isSnowLayer(String name) {
      String baseName = name.contains("[") ? name.substring(0, name.indexOf(91)) : name;
      return "minecraft:snow".equals(baseName);
   }

   private static void removeSnowBlocks(ListTag blocks, Map<Integer, String> paletteIndexToName) {
      Set<Integer> snowIndices = new HashSet<>();

      for (Entry<Integer, String> entry : paletteIndexToName.entrySet()) {
         if (isSnowLayer(entry.getValue())) {
            snowIndices.add(entry.getKey());
         }
      }

      if (!snowIndices.isEmpty()) {
         for (int i = blocks.size() - 1; i >= 0; i--) {
            CompoundTag block = blocks.getCompound(i);
            int state = block.getInt("state");
            if (snowIndices.contains(state)) {
               blocks.remove(i);
            }
         }
      }
   }

   private static void convertToPreserveGround(ListTag palette, ListTag blocks, Map<Integer, String> paletteIndexToName) {
      Set<Integer> groundIndices = new HashSet<>();

      for (Entry<Integer, String> entry : paletteIndexToName.entrySet()) {
         String name = entry.getValue();
         String baseName = name.contains("[") ? name.substring(0, name.indexOf(91)) : name;
         if (GROUND_BLOCKS.contains(baseName)) {
            groundIndices.add(entry.getKey());
         }
      }

      if (!groundIndices.isEmpty()) {
         int preserveIndex = -1;

         for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            if ("millenaire:mock_marker".equals(entry.getString("Name"))) {
               CompoundTag props = entry.getCompound("Properties");
               if ("preserve_ground".equals(props.getString("type"))) {
                  preserveIndex = i;
                  break;
               }
            }
         }

         if (preserveIndex == -1) {
            CompoundTag preserveEntry = new CompoundTag();
            preserveEntry.putString("Name", "millenaire:mock_marker");
            CompoundTag properties = new CompoundTag();
            properties.putString("type", "preserve_ground");
            preserveEntry.put("Properties", properties);
            palette.add(preserveEntry);
            preserveIndex = palette.size() - 1;
         }

         for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            int state = block.getInt("state");
            if (groundIndices.contains(state)) {
               ListTag pos = block.getList("pos", 3);
               if (pos.size() >= 3 && pos.getInt(1) == 0) {
                  block.putInt("state", preserveIndex);
               }
            }
         }
      }
   }

   private static void stripPreviousLevelBlocks(CompoundTag nbt, ImportTableBlockEntity be, ServerLevel level) {
      int upgradeLevel = be.getUpgradeLevel();
      if (upgradeLevel > 0) {
         String cultureKey = be.getCultureKey();
         String buildingId = be.getBuildingId();
         String variant = be.getVariant();
         boolean importMockBlocks = be.isImportMockBlocks();
         StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(Rotation.NONE);
         int baseY = be.getBlockPos().getY();
         BlockPos scanOrigin = computeScanOrigin(be);
         int currentGroundLevel = be.getStartingLevel();
         Map<BlockPos, BlockState> consolidated = new HashMap<>();

         for (int lvl = 0; lvl < upgradeLevel; lvl++) {
            StructureTemplate template = TemplateLoader.resolve(level, cultureKey, buildingId, variant, lvl).orElse(null);
            if (template != null) {
               int glLvl = TemplateLoader.resolveGroundLevel(cultureKey, buildingId, variant, lvl, currentGroundLevel);
               int yOffset = baseY + glLvl - scanOrigin.getY();
               extractBlocksFromTemplateRelative(template, settings, yOffset, level, consolidated);
            }
         }

         if (!consolidated.isEmpty()) {
            if (!importMockBlocks) {
               Map<BlockPos, BlockState> converted = new HashMap<>();

               for (Entry<BlockPos, BlockState> entry : consolidated.entrySet()) {
                  BlockState state = entry.getValue();
                  if (state.getBlock() instanceof MockBlock mockBlock) {
                     BlockState replacement = mockBlock.getReplacementState(state);
                     converted.put(entry.getKey(), replacement != null ? replacement : Blocks.AIR.defaultBlockState());
                  } else {
                     converted.put(entry.getKey(), state);
                  }
               }

               consolidated = converted;
            }

            ListTag paletteTag = NbtPaletteHelper.resolvePaletteTag(nbt);
            if (paletteTag != null) {
               ListTag blocks = nbt.getList("blocks", 10);
               List<BlockState> palette = new ArrayList<>();

               for (int i = 0; i < paletteTag.size(); i++) {
                  CompoundTag stateTag = paletteTag.getCompound(i);
                  BlockState state = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), stateTag);
                  palette.add(state);
               }

               Map<BlockPos, Integer> exportedPositions = new HashMap<>();

               for (int i = 0; i < blocks.size(); i++) {
                  CompoundTag block = blocks.getCompound(i);
                  ListTag pos = block.getList("pos", 3);
                  BlockPos relPos = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
                  exportedPositions.put(relPos, i);
               }

               Set<Integer> toRemove = new HashSet<>();

               for (int i = 0; i < blocks.size(); i++) {
                  CompoundTag block = blocks.getCompound(i);
                  ListTag pos = block.getList("pos", 3);
                  BlockPos relPos = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
                  int stateIndex = block.getInt("state");
                  if (stateIndex >= 0 && stateIndex < palette.size()) {
                     BlockState exportedState = palette.get(stateIndex);
                     BlockState consolidatedState = consolidated.get(relPos);
                     if (consolidatedState != null && consolidatedState.equals(exportedState)) {
                        toRemove.add(i);
                     }
                  }
               }

               List<Integer> sortedRemove = new ArrayList<>(toRemove);
               sortedRemove.sort(Comparator.reverseOrder());

               for (int idx : sortedRemove) {
                  blocks.remove(idx);
               }

               int airIndex = -1;

               for (int i = 0; i < palette.size(); i++) {
                  if (palette.get(i).isAir()) {
                     airIndex = i;
                     break;
                  }
               }

               if (airIndex == -1) {
                  CompoundTag airEntry = new CompoundTag();
                  airEntry.putString("Name", "minecraft:air");
                  paletteTag.add(airEntry);
                  airIndex = paletteTag.size() - 1;
               }

               for (Entry<BlockPos, BlockState> entry : consolidated.entrySet()) {
                  BlockPos relPos = entry.getKey();
                  if (!entry.getValue().isAir()) {
                     Integer existingIdx = exportedPositions.get(relPos);
                     if ((existingIdx == null || !toRemove.contains(existingIdx)) && existingIdx == null) {
                        CompoundTag airBlock = new CompoundTag();
                        ListTag posTag = new ListTag();
                        posTag.add(IntTag.valueOf(relPos.getX()));
                        posTag.add(IntTag.valueOf(relPos.getY()));
                        posTag.add(IntTag.valueOf(relPos.getZ()));
                        airBlock.put("pos", posTag);
                        airBlock.putInt("state", airIndex);
                        blocks.add(airBlock);
                     }
                  }
               }
            }
         }
      }
   }

   static void compactPalette(CompoundTag nbt) {
      if (nbt.contains("palette", 9)) {
         if (nbt.contains("blocks", 9)) {
            ListTag palette = nbt.getList("palette", 10);
            ListTag blocks = nbt.getList("blocks", 10);
            int[] refCount = new int[palette.size()];

            for (int i = 0; i < blocks.size(); i++) {
               CompoundTag block = blocks.getCompound(i);
               int state = block.getInt("state");
               if (state >= 0 && state < refCount.length) {
                  refCount[state]++;
               }
            }

            int[] remap = new int[palette.size()];
            ListTag newPalette = new ListTag();

            for (int i = 0; i < palette.size(); i++) {
               if (refCount[i] > 0) {
                  remap[i] = newPalette.size();
                  newPalette.add(palette.getCompound(i));
               } else {
                  remap[i] = -1;
               }
            }

            if (newPalette.size() != palette.size()) {
               for (int i = 0; i < blocks.size(); i++) {
                  CompoundTag block = blocks.getCompound(i);
                  int oldState = block.getInt("state");
                  if (oldState >= 0 && oldState < remap.length && remap[oldState] >= 0) {
                     block.putInt("state", remap[oldState]);
                  }
               }

               nbt.put("palette", newPalette);
            }
         }
      }
   }

   private static void updateBuildingJson(Path exportDir, ImportTableBlockEntity be) {
      String buildingId = be.getBuildingId();
      Path jsonPath = exportDir.resolve(buildingId + ".json");
      JsonObject root;
      if (Files.exists(jsonPath)) {
         try {
            String content = Files.readString(jsonPath);
            root = JsonParser.parseString(content).getAsJsonObject();
         } catch (Exception e) {
            LOGGER.warn("Failed to read existing JSON, creating new: {}", jsonPath, e);
            root = createNewBuildingJson(be);
         }
      } else {
         root = createNewBuildingJson(be);
      }

      updateLevelEntry(root, be);

      try {
         Files.writeString(jsonPath, GSON.toJson(root));
      } catch (IOException e) {
         LOGGER.error("Failed to write building JSON: {}", jsonPath, e);
      }
   }

   private static int copyMissingCultureVariantLevelsToExports(ServerLevel level, Path exportDir, ImportTableBlockEntity be) {
      String cultureKey = be.getCultureKey();
      String buildingId = be.getBuildingId();
      String currentVariant = be.getVariant();
      int currentLevel = be.getUpgradeLevel();
      if (cultureKey != null && !cultureKey.isEmpty()) {
         if (ContentDirectoryManager.isInitialized() && CustomContentIndex.current().customCultureIds().contains(cultureKey)) {
            return 0;
         }

         BuildingPlanSet planSet = getPlanSet(cultureKey, buildingId);
         if (planSet == null) {
            return 0;
         }

         int copied = 0;

         for (Entry<String, List<BuildingPlanSet.LevelDef>> variantEntry : planSet.variants().entrySet()) {
            String variantKey = variantEntry.getKey();
            List<BuildingPlanSet.LevelDef> levels = variantEntry.getValue();
            if (levels != null && !levels.isEmpty()) {
               for (BuildingPlanSet.LevelDef levelDef : levels) {
                  if (!variantKey.equals(currentVariant) || levelDef.level() != currentLevel) {
                     String nbtFileName = buildingId + "_" + variantKey + "_" + levelDef.level() + ".nbt";
                     Path nbtPath = exportDir.resolve(nbtFileName);
                     StructureTemplate template = TemplateLoader.resolve(level, cultureKey, buildingId, variantKey, levelDef.level()).orElse(null);
                     if (template == null) {
                        LOGGER.warn("Could not copy missing export template for {} {} level {}", new Object[]{buildingId, variantKey, levelDef.level()});
                     } else {
                        try {
                           NbtIo.writeCompressed(template.save(new CompoundTag()), nbtPath);
                           copied++;
                        } catch (IOException e) {
                           LOGGER.warn("Failed to copy linked template to exports: {}", nbtPath, e);
                        }
                     }
                  }
               }
            }
         }

         return copied;
      } else {
         return 0;
      }
   }

   private static void ensureExportJsonHasCopiedVariantLevels(Path exportDir, ImportTableBlockEntity be) {
      String cultureKey = be.getCultureKey();
      String buildingId = be.getBuildingId();
      BuildingPlanSet planSet = getPlanSet(cultureKey, buildingId);
      if (planSet != null) {
         Path jsonPath = exportDir.resolve(buildingId + ".json");

         JsonObject root;
         try {
            if (Files.exists(jsonPath)) {
               root = JsonParser.parseString(Files.readString(jsonPath)).getAsJsonObject();
            } else {
               root = createNewBuildingJson(be);
            }
         } catch (Exception e) {
            LOGGER.warn("Failed to read export JSON for linked entries: {}", jsonPath, e);
            root = createNewBuildingJson(be);
         }

         applyPlanSetToJson(root, exportDir, buildingId, be.getVariant(), be.getUpgradeLevel(), planSet);

         try {
            Files.writeString(jsonPath, GSON.toJson(root));
         } catch (IOException e) {
            LOGGER.error("Failed to write export JSON with linked entries: {}", jsonPath, e);
         }
      }
   }

   static void applyPlanSetToJson(JsonObject root, Path exportDir, String buildingId, String currentVariant, int currentLevel, BuildingPlanSet planSet) {
      JsonArray variants = root.has("variants") ? root.getAsJsonArray("variants") : new JsonArray();
      root.add("variants", variants);

      for (Entry<String, List<BuildingPlanSet.LevelDef>> variantEntry : planSet.variants().entrySet()) {
         String variantKey = variantEntry.getKey();
         List<BuildingPlanSet.LevelDef> levels = variantEntry.getValue();
         if (levels != null && !levels.isEmpty()) {
            JsonObject variantObj = findOrCreateVariant(variants, variantKey);
            JsonArray levelArray = variantObj.has("levels") ? variantObj.getAsJsonArray("levels") : new JsonArray();
            variantObj.add("levels", levelArray);

            for (BuildingPlanSet.LevelDef levelDef : levels) {
               Path nbtPath = exportDir.resolve(buildingId + "_" + variantKey + "_" + levelDef.level() + ".nbt");
               if (Files.exists(nbtPath)) {
                  JsonObject levelObj = findOrCreateLevel(levelArray, levelDef.level());
                  boolean isCurrent = variantKey.equals(currentVariant) && levelDef.level() == currentLevel;
                  if (!isCurrent) {
                     JsonObject footprint = new JsonObject();
                     footprint.addProperty("width", levelDef.width());
                     footprint.addProperty("height", levelDef.height());
                     footprint.addProperty("depth", levelDef.depth());
                     levelObj.add("footprint", footprint);
                     levelObj.addProperty("ground_level", levelDef.groundLevel());
                  }
               }
            }
         }
      }
   }

   private static BuildingPlanSet getPlanSet(String cultureKey, String buildingId) {
      if (cultureKey != null && !cultureKey.isEmpty() && buildingId != null && !buildingId.isEmpty()) {
         ResourceLocation planSetId = ResourceLocation.tryParse(cultureKey + "/" + buildingId);
         return planSetId != null ? ModCultures.getBuildingPlanSet(planSetId) : null;
      } else {
         return null;
      }
   }

   private static JsonObject findOrCreateVariant(JsonArray variants, String variant) {
      for (JsonElement elem : variants) {
         JsonObject obj = elem.getAsJsonObject();
         if (variant.equals(obj.get("variant").getAsString())) {
            return obj;
         }
      }

      JsonObject obj = new JsonObject();
      obj.addProperty("variant", variant);
      obj.add("levels", new JsonArray());
      variants.add(obj);
      return obj;
   }

   private static JsonObject findOrCreateLevel(JsonArray levels, int level) {
      for (JsonElement elem : levels) {
         JsonObject obj = elem.getAsJsonObject();
         if (obj.has("level") && obj.get("level").getAsInt() == level) {
            return obj;
         }
      }

      JsonObject obj = new JsonObject();
      obj.addProperty("level", level);
      levels.add(obj);
      return obj;
   }

   private static JsonObject createNewBuildingJson(ImportTableBlockEntity be) {
      String cultureKey = be.getCultureKey();
      String buildingId = be.getBuildingId();
      if (!cultureKey.isEmpty() && !buildingId.isEmpty()) {
         ResourceLocation planSetId = ResourceLocation.tryParse(cultureKey + "/" + buildingId);
         if (planSetId != null) {
            BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planSetId);
            if (planSet != null) {
               return serializeFromExistingPlanSet(planSet);
            }
         }
      }

      JsonObject root = new JsonObject();
      root.addProperty("culture", cultureKey.isEmpty() ? "millenaire:custom" : cultureKey);
      root.addProperty("building_id", buildingId);
      root.addProperty("category", "extra");
      root.addProperty("native_name", buildingId);
      root.addProperty("converter_skip", true);
      root.add("variants", new JsonArray());
      return root;
   }

   static JsonObject serializeFromExistingPlanSet(BuildingPlanSet planSet) {
      JsonObject root = new JsonObject();
      root.addProperty("culture", planSet.culture().toString());
      root.addProperty("building_id", planSet.buildingId());
      if (!"houses".equals(planSet.category())) {
         root.addProperty("category", planSet.category());
      }

      root.addProperty("native_name", planSet.nativeName());
      root.addProperty("converter_skip", true);
      if (planSet.maxCount() != 1) {
         root.addProperty("max_count", planSet.maxCount());
      }

      if (planSet.minDistance() != 0.0) {
         root.addProperty("min_distance", planSet.minDistance());
      }

      if (planSet.maxDistance() != 1.0) {
         root.addProperty("max_distance", planSet.maxDistance());
      }

      if (!planSet.maleResidents().isEmpty()) {
         root.add("male", toStringArray(planSet.maleResidents()));
      }

      if (!planSet.femaleResidents().isEmpty()) {
         root.add("female", toStringArray(planSet.femaleResidents()));
      }

      root.addProperty("priority_move_in", planSet.priorityMoveIn());
      if (!planSet.tags().isEmpty()) {
         root.add("tags", toStringArray(planSet.tags()));
      }

      if (!"clear_and_flatten".equals(planSet.terrainPolicy())) {
         root.addProperty("terrain_policy", planSet.terrainPolicy());
      }

      if (!"bottom_up".equals(planSet.constructionOrder())) {
         root.addProperty("construction_order", planSet.constructionOrder());
      }

      if (planSet.icon() != null) {
         root.addProperty("icon", planSet.icon());
      }

      ClearMargins margins = planSet.clearMargins();
      if (margins.lengthBefore() != 5 || margins.lengthAfter() != 5 || margins.widthBefore() != 5 || margins.widthAfter() != 5) {
         root.addProperty("area_to_clear_length_before", margins.lengthBefore());
         root.addProperty("area_to_clear_length_after", margins.lengthAfter());
         root.addProperty("area_to_clear_width_before", margins.widthBefore());
         root.addProperty("area_to_clear_width_after", margins.widthAfter());
      }

      if (planSet.price() > 0) {
         root.addProperty("price", planSet.price());
      }

      if (planSet.reputation() > 0) {
         root.addProperty("reputation", planSet.reputation());
      }

      BuildingPlan firstPlan = findFirstPlan(planSet);
      if (firstPlan != null && firstPlan.shopId() != null) {
         root.addProperty("shop", firstPlan.shopId());
      }

      Map<String, Integer> variantOrientations = collectVariantOrientations(planSet);
      Map<String, Integer> variantOrientationOverrides = new LinkedHashMap<>();
      if (variantOrientations.isEmpty()) {
         int planSetOrientation = 1;
      } else {
         boolean allEqual = true;
         int firstValue = variantOrientations.values().iterator().next();

         for (int v : variantOrientations.values()) {
            if (v != firstValue) {
               allEqual = false;
               break;
            }
         }

         if (allEqual) {
            int planSetOrientation = firstValue;
            if (planSetOrientation != 1) {
               root.addProperty("building_orientation", planSetOrientation);
            }
         } else {
            String firstKey = new TreeSet<>(variantOrientations.keySet()).first();
            int planSetOrientation = variantOrientations.get(firstKey);
            root.addProperty("building_orientation", planSetOrientation);

            for (Entry<String, Integer> entry : variantOrientations.entrySet()) {
               if (entry.getValue() != planSetOrientation) {
                  variantOrientationOverrides.put(entry.getKey(), entry.getValue());
               }
            }
         }
      }

      if (!planSet.startingSubBuildings().isEmpty()) {
         root.add("starting_sub_buildings", toStringArray(planSet.startingSubBuildings()));
      }

      if (!planSet.startingGoods().isEmpty()) {
         JsonArray sgArray = new JsonArray();

         for (BuildingPlanSet.StartingGood sg : planSet.startingGoods()) {
            JsonObject sgObj = new JsonObject();
            sgObj.addProperty("item", sg.item());
            if (sg.probability() != 1.0) {
               sgObj.addProperty("probability", sg.probability());
            }

            if (sg.fixedNumber() != 0) {
               sgObj.addProperty("fixed", sg.fixedNumber());
            }

            if (sg.randomNumber() != 0) {
               sgObj.addProperty("random", sg.randomNumber());
            }

            sgArray.add(sgObj);
         }

         root.add("starting_goods", sgArray);
      }

      if (!planSet.randomBrickColours().isEmpty()) {
         JsonObject rbcObj = new JsonObject();

         for (Entry<DyeColor, List<BrickColourTheme.WeightedColor>> entry : planSet.randomBrickColours().entrySet()) {
            JsonArray colors = new JsonArray();

            for (BrickColourTheme.WeightedColor wc : entry.getValue()) {
               JsonObject wcObj = new JsonObject();
               wcObj.addProperty("color", wc.color().getName());
               wcObj.addProperty("weight", wc.weight());
               colors.add(wcObj);
            }

            rbcObj.add(entry.getKey().getName(), colors);
         }

         root.add("random_brick_colours", rbcObj);
      }

      if (planSet.travelBookCategory() != null) {
         root.addProperty("travel_book_category", planSet.travelBookCategory());
      }

      if (!planSet.travelBookDisplay()) {
         root.addProperty("travel_book_display", false);
      }

      JsonArray variantsArray = new JsonArray();

      for (Entry<String, List<BuildingPlanSet.LevelDef>> variantEntry : planSet.variants().entrySet()) {
         JsonObject variantObj = new JsonObject();
         variantObj.addProperty("variant", variantEntry.getKey());
         Integer overrideOrientation = variantOrientationOverrides.get(variantEntry.getKey());
         if (overrideOrientation != null) {
            variantObj.addProperty("building_orientation", overrideOrientation);
         }

         List<BuildingPlanSet.LevelDef> levelDefs = variantEntry.getValue();
         boolean groundLevelConstant = true;
         boolean tagsConstant = true;
         int firstGround = levelDefs.isEmpty() ? 0 : levelDefs.get(0).groundLevel();
         List<String> firstTags = planTagsFor(levelDefs.isEmpty() ? null : levelDefs.get(0));

         for (int i = 1; i < levelDefs.size(); i++) {
            if (levelDefs.get(i).groundLevel() != firstGround) {
               groundLevelConstant = false;
            }

            if (!planTagsFor(levelDefs.get(i)).equals(firstTags)) {
               tagsConstant = false;
            }
         }

         if (groundLevelConstant && !levelDefs.isEmpty() && firstGround != 0) {
            variantObj.addProperty("ground_level", firstGround);
         }

         if (tagsConstant && !firstTags.isEmpty()) {
            variantObj.add("tags", toStringArray(firstTags));
         }

         JsonArray levelsArray = new JsonArray();

         for (BuildingPlanSet.LevelDef ld : levelDefs) {
            levelsArray.add(serializeLevelDef(ld, groundLevelConstant, tagsConstant));
         }

         variantObj.add("levels", levelsArray);
         variantsArray.add(variantObj);
      }

      root.add("variants", variantsArray);
      return root;
   }

   private static List<String> planTagsFor(@Nullable BuildingPlanSet.LevelDef ld) {
      if (ld == null) {
         return List.of();
      }

      BuildingPlan plan = ModCultures.getBuildingPlan(ld.planId());
      return plan != null ? plan.tags() : List.of();
   }

   private static JsonObject serializeLevelDef(BuildingPlanSet.LevelDef ld, boolean groundLevelConstant, boolean tagsConstant) {
      JsonObject levelObj = new JsonObject();
      levelObj.addProperty("level", ld.level());
      JsonObject footprint = new JsonObject();
      footprint.addProperty("width", ld.width());
      footprint.addProperty("height", ld.height());
      footprint.addProperty("depth", ld.depth());
      levelObj.add("footprint", footprint);
      if (!groundLevelConstant) {
         levelObj.addProperty("ground_level", ld.groundLevel());
      }

      if (!tagsConstant) {
         List<String> planTags = planTagsFor(ld);
         if (!planTags.isEmpty()) {
            levelObj.add("tags", toStringArray(planTags));
         }
      }

      if (ld.priority() != 100) {
         levelObj.addProperty("priority", ld.priority());
      }

      if (ld.nativeName() != null) {
         levelObj.addProperty("native_name", ld.nativeName());
      }

      if (!ld.requiredTags().isEmpty()) {
         levelObj.add("required_tags", toStringArray(ld.requiredTags()));
      }

      if (!ld.forbiddenTagsInVillage().isEmpty()) {
         levelObj.add("forbidden_tags_in_village", toStringArray(ld.forbiddenTagsInVillage()));
      }

      if (!ld.requiredVillageTags().isEmpty()) {
         levelObj.add("required_village_tags", toStringArray(ld.requiredVillageTags()));
      }

      if (!ld.parentTags().isEmpty()) {
         levelObj.add("parent_tags", toStringArray(ld.parentTags()));
      }

      if (!ld.requiredParentTags().isEmpty()) {
         levelObj.add("required_parent_tags", toStringArray(ld.requiredParentTags()));
      }

      if (!ld.clearTags().isEmpty()) {
         levelObj.add("clear_tags", toStringArray(ld.clearTags()));
      }

      if (!ld.villageTags().isEmpty()) {
         levelObj.add("village_tags", toStringArray(ld.villageTags()));
      }

      if (!ld.subBuildings().isEmpty()) {
         levelObj.add("sub_buildings", toStringArray(ld.subBuildings()));
      }

      if (ld.signOrder() != null) {
         StringBuilder sb = new StringBuilder();

         for (int i = 0; i < ld.signOrder().length; i++) {
            if (i > 0) {
               sb.append(",");
            }

            sb.append(ld.signOrder()[i]);
         }

         levelObj.addProperty("signs", sb.toString());
      }

      if (ld.pathLevel() != 0) {
         levelObj.addProperty("path_level", ld.pathLevel());
      }

      if (ld.rebuildPath()) {
         levelObj.addProperty("rebuild_path", true);
      }

      if (ld.pathWidth() != 2) {
         levelObj.addProperty("path_width", ld.pathWidth());
      }

      if (!ld.abstractedProduction().isEmpty()) {
         JsonArray apArray = new JsonArray();

         for (Entry<String, Integer> apEntry : ld.abstractedProduction().entrySet()) {
            apArray.add(apEntry.getKey() + "," + apEntry.getValue());
         }

         levelObj.add("abstracted_production", apArray);
      }

      return levelObj;
   }

   @Nullable
   private static BuildingPlan findFirstPlan(BuildingPlanSet planSet) {
      for (List<BuildingPlanSet.LevelDef> levels : planSet.variants().values()) {
         if (!levels.isEmpty()) {
            return ModCultures.getBuildingPlan(levels.get(0).planId());
         }
      }

      return null;
   }

   static Map<String, Integer> collectVariantOrientations(BuildingPlanSet planSet) {
      Map<String, Integer> result = new LinkedHashMap<>();

      for (Entry<String, List<BuildingPlanSet.LevelDef>> entry : planSet.variants().entrySet()) {
         List<BuildingPlanSet.LevelDef> levels = entry.getValue();
         if (levels != null && !levels.isEmpty()) {
            BuildingPlan plan = ModCultures.getBuildingPlan(levels.get(0).planId());
            if (plan != null) {
               result.put(entry.getKey(), plan.buildingOrientation());
            }
         }
      }

      return result;
   }

   private static JsonArray toStringArray(List<String> list) {
      JsonArray array = new JsonArray();

      for (String s : list) {
         array.add(s);
      }

      return array;
   }

   static void updateLevelEntry(JsonObject root, ImportTableBlockEntity be) {
      JsonArray variants = root.has("variants") ? root.getAsJsonArray("variants") : new JsonArray();
      JsonObject variantObj = null;

      for (JsonElement elem : variants) {
         JsonObject v = elem.getAsJsonObject();
         if (be.getVariant().equals(v.get("variant").getAsString())) {
            variantObj = v;
            break;
         }
      }

      if (variantObj == null) {
         variantObj = new JsonObject();
         variantObj.addProperty("variant", be.getVariant());
         variantObj.add("levels", new JsonArray());
         variants.add(variantObj);
      }

      JsonArray levels = variantObj.has("levels") ? variantObj.getAsJsonArray("levels") : new JsonArray();
      JsonObject levelObj = null;

      for (JsonElement elem : levels) {
         JsonObject l = elem.getAsJsonObject();
         if (l.get("level").getAsInt() == be.getUpgradeLevel()) {
            levelObj = l;
            break;
         }
      }

      if (levelObj == null) {
         levelObj = new JsonObject();
         levelObj.addProperty("level", be.getUpgradeLevel());
         levels.add(levelObj);
      }

      JsonObject footprint = new JsonObject();
      footprint.addProperty("width", be.getWidth());
      footprint.addProperty("height", be.getHeight());
      footprint.addProperty("depth", be.getLength());
      levelObj.add("footprint", footprint);
      int levelGroundLevel = be.getStartingLevel();
      int variantGroundLevel = variantObj.has("ground_level") ? variantObj.get("ground_level").getAsInt() : 0;
      if (levelGroundLevel != variantGroundLevel) {
         levelObj.addProperty("ground_level", levelGroundLevel);
      } else {
         levelObj.remove("ground_level");
      }

      reconcileVariantOrientation(root, variantObj, be.getOrientation());
      variantObj.add("levels", levels);
      root.add("variants", variants);
   }

   static void reconcileVariantOrientation(JsonObject root, JsonObject variantObj, int tableOrientation) {
      int planSetOrientation = root.has("building_orientation") ? root.get("building_orientation").getAsInt() : 1;
      if (tableOrientation == planSetOrientation) {
         variantObj.remove("building_orientation");
      } else {
         variantObj.addProperty("building_orientation", tableOrientation);
      }
   }

   public static BlockPos computeScanOrigin(ImportTableBlockEntity be) {
      BlockPos tablePos = be.getBlockPos();
      int startingLevel = be.getStartingLevel();
      return tablePos.offset(1, startingLevel, 1);
   }

   private static int detectBuildingHeight(ServerLevel level, BlockPos origin, ImportTableBlockEntity be, int upgradeLevel) {
      int scanX = be.getWidth();
      int scanZ = be.getLength();
      int minHeight = be.getHeight();
      int maxY = level.getMaxBuildHeight();
      int maxPossibleHeight = maxY - origin.getY();
      int height = 0;

      for (int dy = 0; dy < maxPossibleHeight; dy++) {
         boolean blockFound = false;

         for (int dx = 0; dx < scanX && !blockFound; dx++) {
            for (int dz = 0; dz < scanZ && !blockFound; dz++) {
               BlockPos pos = origin.offset(dx, dy, dz);
               if (!level.getBlockState(pos).isAir()) {
                  blockFound = true;
               }
            }
         }

         if (blockFound) {
            height = dy + 1;
         } else if (dy >= minHeight) {
            break;
         }
      }

      return Math.max(height, 1);
   }

   public static Vec3i computeScanSize(ImportTableBlockEntity be) {
      return new Vec3i(be.getWidth(), be.getHeight(), be.getLength());
   }

   public static long computeBlocksHash(ServerLevel level, ImportTableBlockEntity be) {
      BlockPos origin = computeScanOrigin(be);
      Vec3i size = computeScanSize(be);
      long hash = 1469598103934665603L;
      MutableBlockPos cursor = new MutableBlockPos();
      int sx = size.getX();
      int sy = size.getY();
      int sz = size.getZ();

      for (int dx = 0; dx < sx; dx++) {
         for (int dy = 0; dy < sy; dy++) {
            for (int dz = 0; dz < sz; dz++) {
               cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
               BlockState state = level.getBlockState(cursor);
               BlockState hashState;
               if (state.getBlock() != Blocks.WATER && state.getBlock() != Blocks.LAVA) {
                  hashState = state;
               } else {
                  hashState = state.getFluidState().isSource() ? state.getBlock().defaultBlockState() : Blocks.AIR.defaultBlockState();
               }

               hash ^= Block.getId(hashState);
               hash *= 1099511628211L;
               BlockEntity blockEntity = level.getBlockEntity(cursor);
               if (blockEntity != null) {
                  try {
                     CompoundTag tag = blockEntity.saveWithoutMetadata(level.registryAccess());
                     hash ^= tag.hashCode();
                     hash *= 1099511628211L;
                  } catch (Exception var17) {
                  }
               }
            }
         }
      }

      return hash;
   }

   public static Rotation orientationToRotation(int orientation) {
      return switch (orientation) {
         case 1 -> Rotation.CLOCKWISE_90;
         case 2 -> Rotation.CLOCKWISE_180;
         case 3 -> Rotation.COUNTERCLOCKWISE_90;
         default -> Rotation.NONE;
      };
   }

   public static Path getExportDir(ServerLevel level, @Nullable String cultureKey, @Nullable String category) {
      return computeExportDir(level.getServer().getServerDirectory(), cultureKey, category);
   }

   static Path computeExportDir(Path gameDir, @Nullable String cultureKey, @Nullable String category) {
      String culturePath = culturePathComponent(cultureKey);
      String categoryDir = category != null && !category.isEmpty() ? category : "lone";
      return gameDir.resolve("millenaire-custom").resolve("exported").resolve("cultures").resolve(culturePath).resolve("buildings").resolve(categoryDir);
   }

   public static Path getLegacyExportDir(ServerLevel level) {
      return getLegacyExportDir(level.getServer().getServerDirectory());
   }

   public static Path getLegacyExportDir(Path gameDir) {
      return gameDir.resolve("millenaire-custom").resolve("exports");
   }

   public static Path getExportedRoot(ServerLevel level) {
      return getExportedRoot(level.getServer().getServerDirectory());
   }

   public static Path getExportedRoot(Path gameDir) {
      return gameDir.resolve("millenaire-custom").resolve("exported");
   }

   @Deprecated
   public static Path getExportDir(ServerLevel level) {
      return getLegacyExportDir(level);
   }

   static String culturePathComponent(@Nullable String cultureKey) {
      if (cultureKey != null && !cultureKey.isEmpty()) {
         ResourceLocation rl = ResourceLocation.tryParse(cultureKey);
         return rl != null && !rl.getPath().isEmpty() ? rl.getPath() : cultureKey;
      } else {
         return "custom";
      }
   }

   public static Optional<Path> findExportedNbt(ServerLevel level, String buildingId, String variant, int upgradeLevel) {
      String fileName = buildingId + "_" + variant + "_" + upgradeLevel + ".nbt";
      Path legacy = getLegacyExportDir(level).resolve(fileName);
      if (Files.exists(legacy)) {
         return Optional.of(legacy);
      }

      Path exportedRoot = getExportedRoot(level).resolve("cultures");
      if (!Files.isDirectory(exportedRoot)) {
         return Optional.empty();
      }

      try (Stream<Path> cultures = Files.list(exportedRoot)) {
         for (Path culturePath : cultures.filter(x$0 -> Files.isDirectory(x$0)).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
            Path buildingsDir = culturePath.resolve("buildings");
            if (Files.isDirectory(buildingsDir)) {
               try (Stream<Path> categories = Files.list(buildingsDir)) {
                  for (Path categoryPath : categories.filter(x$0 -> Files.isDirectory(x$0))
                     .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                     .toList()) {
                     Path candidate = categoryPath.resolve(fileName);
                     if (Files.exists(candidate)) {
                        return Optional.of(candidate);
                     }
                  }
               }
            }
         }
      } catch (IOException e) {
         LOGGER.warn("Failed to walk exported/cultures while looking for {}: {}", fileName, e.getMessage());
      }

      return Optional.empty();
   }

   public static Optional<Path> findExportedJson(ServerLevel level, String buildingId) {
      String fileName = buildingId + ".json";
      Path legacy = getLegacyExportDir(level).resolve(fileName);
      if (Files.exists(legacy)) {
         return Optional.of(legacy);
      }

      Path exportedRoot = getExportedRoot(level).resolve("cultures");
      if (!Files.isDirectory(exportedRoot)) {
         return Optional.empty();
      }

      try (Stream<Path> cultures = Files.list(exportedRoot)) {
         for (Path culturePath : cultures.filter(x$0 -> Files.isDirectory(x$0)).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
            Path buildingsDir = culturePath.resolve("buildings");
            if (Files.isDirectory(buildingsDir)) {
               try (Stream<Path> categories = Files.list(buildingsDir)) {
                  for (Path categoryPath : categories.filter(x$0 -> Files.isDirectory(x$0))
                     .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                     .toList()) {
                     Path candidate = categoryPath.resolve(fileName);
                     if (Files.exists(candidate)) {
                        return Optional.of(candidate);
                     }
                  }
               }
            }
         }
      } catch (IOException e) {
         LOGGER.warn("Failed to walk exported/cultures while looking for {}: {}", fileName, e.getMessage());
      }

      return Optional.empty();
   }

   public static String categoryFor(@Nullable String cultureKey, @Nullable String buildingId) {
      BuildingPlanSet planSet = getPlanSet(cultureKey, buildingId);
      return planSet != null && planSet.category() != null && !planSet.category().isEmpty() ? planSet.category() : "lone";
   }

   public static Path getExportDirFor(ServerLevel level, ImportTableBlockEntity be) {
      String cultureKey = be.getCultureKey();
      String category = categoryFor(cultureKey, be.getBuildingId());
      return getExportDir(level, cultureKey, category);
   }

   public static List<String> listExportedBuildingIds(Path gameDir) {
      TreeSet<String> ids = new TreeSet<>();
      collectJsonStems(getLegacyExportDir(gameDir), ids);
      Path culturesRoot = getExportedRoot(gameDir).resolve("cultures");
      if (Files.isDirectory(culturesRoot)) {
         try (Stream<Path> cultures = Files.list(culturesRoot)) {
            cultures.filter(x$0 -> Files.isDirectory(x$0)).forEach(culturePath -> {
               Path buildingsDir = culturePath.resolve("buildings");
               if (Files.isDirectory(buildingsDir)) {
                  try (Stream<Path> categories = Files.list(buildingsDir)) {
                     categories.filter(x$0 -> Files.isDirectory(x$0)).forEach(categoryPath -> collectJsonStems(categoryPath, ids));
                  } catch (IOException e) {
                     LOGGER.warn("Failed to list {}: {}", buildingsDir, e.getMessage());
                  }
               }
            });
         } catch (IOException e) {
            LOGGER.warn("Failed to walk exported/cultures: {}", e.getMessage());
         }
      }

      return new ArrayList<>(ids);
   }

   private static void collectJsonStems(Path dir, Set<String> out) {
      if (Files.isDirectory(dir)) {
         try (Stream<Path> files = Files.list(dir)) {
            files.filter(x$0 -> Files.isRegularFile(x$0))
               .map(p -> p.getFileName().toString())
               .filter(n -> n.endsWith(".json"))
               .map(n -> n.substring(0, n.length() - 5))
               .forEach(out::add);
         } catch (IOException e) {
            LOGGER.warn("Failed to list {}: {}", dir, e.getMessage());
         }
      }
   }

   public static Optional<Path> findExportedDir(Path gameDir, String buildingId) {
      String fileName = buildingId + ".json";
      Path legacy = getLegacyExportDir(gameDir);
      if (Files.isRegularFile(legacy.resolve(fileName))) {
         return Optional.of(legacy);
      }

      Path culturesRoot = getExportedRoot(gameDir).resolve("cultures");
      if (!Files.isDirectory(culturesRoot)) {
         return Optional.empty();
      }

      try (Stream<Path> cultures = Files.list(culturesRoot)) {
         for (Path culturePath : cultures.filter(x$0 -> Files.isDirectory(x$0)).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
            Path buildingsDir = culturePath.resolve("buildings");
            if (Files.isDirectory(buildingsDir)) {
               try (Stream<Path> categories = Files.list(buildingsDir)) {
                  for (Path categoryPath : categories.filter(x$0 -> Files.isDirectory(x$0))
                     .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                     .toList()) {
                     if (Files.isRegularFile(categoryPath.resolve(fileName))) {
                        return Optional.of(categoryPath);
                     }
                  }
               }
            }
         }
      } catch (IOException e) {
         LOGGER.warn("Failed to walk exported/cultures looking for {}: {}", fileName, e.getMessage());
      }

      return Optional.empty();
   }

   public static List<String> listExportedVariants(Path gameDir, String buildingId) {
      Optional<Path> dir = findExportedDir(gameDir, buildingId);
      if (dir.isEmpty()) {
         return List.of();
      }

      String prefix = buildingId + "_";
      TreeSet<String> variants = new TreeSet<>();

      try (Stream<Path> files = Files.list(dir.get())) {
         files.filter(x$0 -> Files.isRegularFile(x$0))
            .map(p -> p.getFileName().toString())
            .filter(n -> n.startsWith(prefix) && n.endsWith(".nbt"))
            .forEach(name -> {
               String stripped = name.substring(prefix.length(), name.length() - 4);
               int us = stripped.lastIndexOf(95);
               if (us > 0) {
                  variants.add(stripped.substring(0, us));
               }
            });
      } catch (IOException e) {
         LOGGER.warn("Failed to list variants for {} in {}: {}", new Object[]{buildingId, dir.get(), e.getMessage()});
      }

      return new ArrayList<>(variants);
   }

   public static List<Integer> listExportedLevels(Path gameDir, String buildingId, String variant) {
      Optional<Path> dir = findExportedDir(gameDir, buildingId);
      if (dir.isEmpty()) {
         return List.of();
      }

      List<Integer> levels = new ArrayList<>();

      for (int lvl = 0; Files.isRegularFile(dir.get().resolve(buildingId + "_" + variant + "_" + lvl + ".nbt")); lvl++) {
         levels.add(lvl);
      }

      return levels;
   }

   private static void ensureDir(Path dir) {
      try {
         Files.createDirectories(dir);
      } catch (IOException e) {
         LOGGER.error("Failed to create export directory: {}", dir, e);
      }
   }

   private static void refreshOverlay() {
      CustomContentIndex.rebuild(BuiltInCultures.IDS, true);
   }

   private static void extractBlocksFromTemplateRelative(
      StructureTemplate template, StructurePlaceSettings settings, int yOffset, ServerLevel level, Map<BlockPos, BlockState> combined
   ) {
      CompoundTag nbt = template.save(new CompoundTag());
      List<BlockState> palette = new ArrayList<>();
      ListTag paletteTag = NbtPaletteHelper.resolvePaletteTag(nbt);
      if (paletteTag != null) {
         for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag stateTag = paletteTag.getCompound(i);
            BlockState state = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), stateTag);
            palette.add(state);
         }

         if (!palette.isEmpty()) {
            ListTag blocksTag = nbt.getList("blocks", 10);

            for (int i = 0; i < blocksTag.size(); i++) {
               CompoundTag blockEntry = blocksTag.getCompound(i);
               ListTag posTag = blockEntry.getList("pos", 3);
               BlockPos templatePos = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
               int stateIndex = blockEntry.getInt("state");
               if (stateIndex >= 0 && stateIndex < palette.size()) {
                  BlockState state = palette.get(stateIndex);
                  if (!state.isAir()) {
                     BlockPos rotatedPos = StructureTemplate.calculateRelativePosition(settings, templatePos);
                     BlockState rotatedState = state.rotate(settings.getRotation());
                     BlockPos relPos = rotatedPos.offset(0, yOffset, 0);
                     combined.put(relPos, rotatedState);
                  }
               }
            }
         }
      }
   }
}
