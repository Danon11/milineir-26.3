package org.millenaire.building;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.millenaire.block.ImportTableBlock;
import org.millenaire.block.ImportTableBlockEntity;
import org.millenaire.block.ModBlocks;
import org.millenaire.content.ContentFs;
import org.millenaire.culture.ModCultures;
import org.millenaire.world.BuildingPlacer;
import org.slf4j.Logger;

public final class BuildingImporter {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int IMPORT_ALL_SPACING = 4;
   private static final BlockState WHITE_WOOL = Blocks.WHITE_WOOL.defaultBlockState();
   private static final BlockState ORANGE_WOOL = Blocks.ORANGE_WOOL.defaultBlockState();
   private static final BlockState YELLOW_WOOL = Blocks.YELLOW_WOOL.defaultBlockState();
   private static final int CLEAR_ABOVE = 20;

   private BuildingImporter() {
   }

   public static void importLevelFromCulture(
      ServerLevel level,
      ImportTableBlockEntity be,
      ServerPlayer player,
      String cultureKey,
      String buildingId,
      String variant,
      int upgradeLevel,
      boolean isSubBuilding,
      String parentBuildingId
   ) {
      Optional<ImportTablePlanResolver.LevelView> levelOpt = ImportTablePlanResolver.resolveLevel(cultureKey, buildingId, variant, upgradeLevel);
      if (levelOpt.isEmpty()) {
         ResourceLocation planSetId = ResourceLocation.tryParse(cultureKey + "/" + buildingId);
         if (planSetId == null) {
            player.sendSystemMessage(Component.literal("§c[ImportTable] Invalid building ID: " + buildingId));
         } else if (ModCultures.getBuildingPlanSet(planSetId) == null) {
            player.sendSystemMessage(Component.literal("§c[ImportTable] Building plan set not found: " + planSetId));
         } else {
            player.sendSystemMessage(Component.literal("§c[ImportTable] Variant '" + variant + "' level " + upgradeLevel + " not found for " + buildingId));
         }
      } else {
         ImportTablePlanResolver.LevelView levelView = levelOpt.get();
         ResourceLocation cultureRl = ResourceLocation.tryParse(cultureKey);
         if (cultureRl == null) {
            player.sendSystemMessage(Component.literal("§c[ImportTable] Invalid culture key: " + cultureKey));
         } else {
            ContentFs cultureFs = TemplateLoader.cultureFsForImport(cultureRl);
            Optional<StructureTemplate> templateOpt = TemplateLoader.loadFromPath(levelView.nbtPath(), level, cultureFs);
            if (templateOpt.isEmpty()) {
               player.sendSystemMessage(Component.literal("§c[ImportTable] Template not found: " + levelView.nbtPath()));
            } else {
               boolean isFreshLoad = !buildingId.equals(be.getBuildingId()) || !variant.equals(be.getVariant()) || !cultureKey.equals(be.getCultureKey());
               be.setBuildingId(buildingId);
               be.setVariant(variant);
               be.setCultureKey(cultureKey);
               be.setUpgradeLevel(upgradeLevel);
               be.setWidth(levelView.width());
               be.setLength(levelView.depth());
               be.setHeight(levelView.height());
               be.setStartingLevel(levelView.groundLevel());
               if (isSubBuilding) {
                  be.setParentBuildingId(parentBuildingId);
               }

               if (isFreshLoad) {
                  be.setOrientation(levelView.buildingOrientation());
               }

               be.setImportedFromCulture(true);
               clearPlotVolume(level, be);
               placePreviousLevels(level, be, upgradeLevel, player);
               Rotation rotation = Rotation.NONE;
               boolean processMockBlocks = !be.isImportMockBlocks();
               if (upgradeLevel == 0) {
                  placeTemplateWithFixes(level, be, templateOpt.get());
               } else {
                  BlockPos origin = BuildingExporter.computeScanOrigin(be);
                  BuildingPlacer.placeUpgradeFromTemplate(level, templateOpt.get(), origin, rotation, processMockBlocks);
               }

               HearthLightingUtil.lightHearthsInArea(level, BuildingExporter.computeScanOrigin(be), BuildingExporter.computeScanSize(be));
               be.captureSavedState(BuildingExporter.computeBlocksHash(level, be));
               be.setDirty(false);
               placeConstructionBorder(level, be);
               placeImportLabel(level, be, labelFor(buildingId, variant, upgradeLevel));
               player.sendSystemMessage(Component.literal("§a[ImportTable] Imported " + buildingId + " " + variant + " level " + upgradeLevel));
            }
         }
      }
   }

   public static void importLevelFromExports(
      ServerLevel level, ImportTableBlockEntity be, ServerPlayer player, String buildingId, String variant, int upgradeLevel
   ) {
      Optional<Path> nbtPathOpt = BuildingExporter.findExportedNbt(level, buildingId, variant, upgradeLevel);
      String nbtFile = buildingId + "_" + variant + "_" + upgradeLevel + ".nbt";
      if (nbtPathOpt.isEmpty()) {
         player.sendSystemMessage(Component.literal("§c[ImportTable] NBT file not found: " + nbtFile));
      } else {
         Path nbtPath = nbtPathOpt.get();
         Path exportDir = nbtPath.getParent();
         StructureTemplate template = new StructureTemplate();

         try (InputStream is = Files.newInputStream(nbtPath)) {
            CompoundTag nbt = NbtIo.readCompressed(is, NbtAccounter.unlimitedHeap());
            HearthTemplateSanitizer.sanitize(nbt, null);
            template.load(level.holderLookup(Registries.BLOCK), nbt);
         } catch (IOException e) {
            LOGGER.error("Failed to load NBT: {}", nbtPath, e);
            player.sendSystemMessage(Component.literal("§c[ImportTable] Failed to load NBT: " + e.getMessage()));
            return;
         }

         boolean isFreshLoad = !buildingId.equals(be.getBuildingId()) || !variant.equals(be.getVariant());
         loadDimensionsFromJson(exportDir, buildingId, variant, upgradeLevel, be, isFreshLoad);
         be.setBuildingId(buildingId);
         be.setVariant(variant);
         be.setUpgradeLevel(upgradeLevel);
         if (isFreshLoad) {
            be.setImportedFromCulture(false);
         }

         clearPlotVolume(level, be);
         placePreviousLevels(level, be, upgradeLevel, player);
         if (upgradeLevel == 0) {
            placeTemplateWithFixes(level, be, template);
         } else {
            Rotation rotation = Rotation.NONE;
            boolean processMockBlocks = !be.isImportMockBlocks();
            BlockPos origin = BuildingExporter.computeScanOrigin(be);
            BuildingPlacer.placeUpgradeFromTemplate(level, template, origin, rotation, processMockBlocks);
         }

         HearthLightingUtil.lightHearthsInArea(level, BuildingExporter.computeScanOrigin(be), BuildingExporter.computeScanSize(be));
         be.captureSavedState(BuildingExporter.computeBlocksHash(level, be));
         be.setDirty(false);
         placeConstructionBorder(level, be);
         placeImportLabel(level, be, labelFor(buildingId, variant, upgradeLevel));
         player.sendSystemMessage(Component.literal("§a[ImportTable] Imported " + buildingId + " " + variant + " level " + upgradeLevel + " from exports"));
      }
   }

   public static void importAllFromCulture(
      ServerLevel level, ImportTableBlockEntity be, ServerPlayer player, String cultureKey, String buildingId, String variant
   ) {
      BuildingImporter.ImportAabb aabb = importAllVariantsFromCulture(level, be, player, cultureKey, buildingId);
      if (aabb != null) {
         player.sendSystemMessage(Component.literal("§a[ImportTable] Imported all plans of " + buildingId));
      }
   }

   @Nullable
   public static BuildingImporter.ImportAabb importAllVariantsFromCulture(
      ServerLevel level, ImportTableBlockEntity be, ServerPlayer player, String cultureKey, String buildingId
   ) {
      Optional<ImportTablePlanResolver.PlanView> planOpt = ImportTablePlanResolver.resolvePlan(cultureKey, buildingId);
      if (planOpt.isEmpty()) {
         ResourceLocation planSetId = ResourceLocation.tryParse(cultureKey + "/" + buildingId);
         if (planSetId == null) {
            return null;
         }

         if (ModCultures.getBuildingPlanSet(planSetId) == null) {
            player.sendSystemMessage(Component.literal("§c[ImportTable] Building plan set not found: " + planSetId));
         } else {
            player.sendSystemMessage(Component.literal("§c[ImportTable] Could not read on-disk JSON for " + buildingId));
         }

         return null;
      } else {
         ImportTablePlanResolver.PlanView planView = planOpt.get();
         be.setIsMainTable(true);
         BlockPos mainPos = be.getBlockPos();
         Direction facing = (Direction)level.getBlockState(mainPos).getValue(ImportTableBlock.FACING);
         int globalMinX = Integer.MAX_VALUE;
         int globalMaxX = Integer.MIN_VALUE;
         int globalMinZ = Integer.MAX_VALUE;
         int globalMaxZ = Integer.MIN_VALUE;
         int imported = 0;
         int nextVariantMinX = Integer.MIN_VALUE;

         for (String variantKey : new ArrayList<>(planView.variants().keySet())) {
            ImportTablePlanResolver.VariantView variantView = planView.variants().get(variantKey);
            if (variantView != null && !variantView.levels().isEmpty()) {
               List<Integer> sortedLevels = variantView.levels().keySet().stream().sorted().toList();
               int variantMinX = Integer.MAX_VALUE;
               int variantMaxX = Integer.MIN_VALUE;
               int nextLevelMinZ = Integer.MIN_VALUE;
               boolean variantAnchored = false;
               int importedInVariant = 0;

               for (int levelNum : sortedLevels) {
                  ImportTableBlockEntity targetBe;
                  if (imported == 0) {
                     targetBe = be;
                  } else {
                     int desiredMinX;
                     int desiredMinZ;
                     if (!variantAnchored) {
                        desiredMinX = nextVariantMinX;
                        desiredMinZ = mainPos.getZ();
                     } else {
                        desiredMinX = variantMinX;
                        desiredMinZ = nextLevelMinZ;
                     }

                     BlockPos tablePos = tablePosForBuildingAabb(desiredMinX, desiredMinZ, mainPos.getY());
                     level.setBlock(
                        tablePos, (BlockState)((ImportTableBlock)ModBlocks.IMPORT_TABLE.get()).defaultBlockState().setValue(ImportTableBlock.FACING, facing), 2
                     );
                     if (!(level.getBlockEntity(tablePos) instanceof ImportTableBlockEntity childBe)) {
                        LOGGER.warn("Failed to create child ImportTable at {}", tablePos);
                        continue;
                     }

                     be.copySettingsTo(childBe);
                     targetBe = childBe;
                  }

                  importLevelFromCulture(level, targetBe, player, cultureKey, buildingId, variantKey, levelNum, false, "");
                  int actualOrient = normaliseOrientation(targetBe.getOrientation());
                  int actualW = targetBe.getWidth();
                  int actualL = targetBe.getLength();
                  int[] aabb = buildingAabb(targetBe.getBlockPos(), actualOrient, actualW, actualL);
                  int aMinX = aabb[0];
                  int aMinZ = aabb[1];
                  int aMaxX = aabb[2];
                  int aMaxZ = aabb[3];
                  if (!variantAnchored) {
                     variantMinX = aMinX;
                     variantMaxX = aMaxX;
                     variantAnchored = true;
                  } else {
                     variantMinX = Math.min(variantMinX, aMinX);
                     variantMaxX = Math.max(variantMaxX, aMaxX);
                  }

                  nextLevelMinZ = aMaxZ + 1 + 4;
                  globalMinX = Math.min(globalMinX, aMinX);
                  globalMinZ = Math.min(globalMinZ, aMinZ);
                  globalMaxX = Math.max(globalMaxX, aMaxX);
                  globalMaxZ = Math.max(globalMaxZ, aMaxZ);
                  imported++;
                  importedInVariant++;
               }

               if (importedInVariant > 0) {
                  nextVariantMinX = variantMaxX + 1 + 4;
               }
            }
         }

         return imported == 0 ? null : new BuildingImporter.ImportAabb(globalMinX, globalMinZ, globalMaxX, globalMaxZ);
      }
   }

   private static int normaliseOrientation(int orient) {
      return (orient % 4 + 4) % 4;
   }

   public static int[] buildingAabb(BlockPos tablePos, int orient, int w, int l) {
      int tx = tablePos.getX();
      int tz = tablePos.getZ();
      return new int[]{tx + 1, tz + 1, tx + w, tz + l};
   }

   public static BlockPos tablePosForBuildingAabb(int worldMinX, int worldMinZ, int y) {
      return new BlockPos(worldMinX - 1, y, worldMinZ - 1);
   }

   @Deprecated
   public static BlockPos tablePosForBuildingAabb(int worldMinX, int worldMinZ, int y, int orient, int w, int l) {
      return tablePosForBuildingAabb(worldMinX, worldMinZ, y);
   }

   public static void importAllFromExports(ServerLevel level, ImportTableBlockEntity be, ServerPlayer player, String buildingId, String variant) {
      if (BuildingExporter.findExportedNbt(level, buildingId, variant, 0).isEmpty()) {
         player.sendSystemMessage(Component.literal("§c[ImportTable] No levels found for " + buildingId + " " + variant));
      } else {
         int maxLevel = 0;

         while (BuildingExporter.findExportedNbt(level, buildingId, variant, maxLevel + 1).isPresent()) {
            maxLevel++;
         }

         be.setIsMainTable(true);
         be.setBuildingId(buildingId);
         be.setVariant(variant);
         int totalLevels = maxLevel + 1;
         importAllLevels(level, be, player, totalLevels, (lvl, childBe) -> importLevelFromExports(level, childBe, player, buildingId, variant, lvl));
         player.sendSystemMessage(
            Component.literal("§a[ImportTable] Imported all " + totalLevels + " levels of " + buildingId + " " + variant + " from exports")
         );
      }
   }

   public static void reimport(ServerLevel level, ImportTableBlockEntity be, ServerPlayer player) {
      if (!be.hasPlan()) {
         player.sendSystemMessage(Component.literal("§c[ImportTable] No plan loaded on this table"));
      } else {
         String cultureKey = be.getCultureKey();
         if (!cultureKey.isEmpty() && !cultureKey.startsWith("millenaire-custom")) {
            importLevelFromCulture(
               level,
               be,
               player,
               cultureKey,
               be.getBuildingId(),
               be.getVariant(),
               be.getUpgradeLevel(),
               !be.getParentBuildingId().isEmpty(),
               be.getParentBuildingId()
            );
         } else {
            importLevelFromExports(level, be, player, be.getBuildingId(), be.getVariant(), be.getUpgradeLevel());
         }
      }
   }

   public static void reimportAll(ServerLevel level, ImportTableBlockEntity be, ServerPlayer player) {
      ImportTableBlockEntity mainTable = be.resolveMainTable(level);
      mainTable.propagatePlanIdentity(level);
      reimport(level, mainTable, player);
      List<ImportTableBlockEntity> children = mainTable.findChildTables(level);

      for (ImportTableBlockEntity child : children) {
         reimport(level, child, player);
      }

      player.sendSystemMessage(Component.literal("§a[ImportTable] Re-imported all " + (1 + children.size()) + " levels"));
   }

   public static void createNewBuilding(
      ServerLevel level, ImportTableBlockEntity be, ServerPlayer player, int length, int width, int startingLevel, int height, boolean clearGround
   ) {
      Path exportDir = BuildingExporter.getExportDir(level, "", "lone");

      try {
         Files.createDirectories(exportDir);
      } catch (IOException e) {
         LOGGER.error("Failed to create export dir", e);
         return;
      }

      int nextN = 0;

      while (BuildingExporter.findExportedJson(level, "export" + nextN).isPresent()) {
         nextN++;
      }

      String buildingId = "export" + nextN;
      be.setBuildingId(buildingId);
      be.setVariant("a");
      be.setUpgradeLevel(0);
      be.setLength(length);
      be.setWidth(width);
      be.setStartingLevel(startingLevel);
      be.setHeight(height);
      be.setClearGround(clearGround);
      be.setCultureKey("");
      be.setOrientation(1);
      be.setImportedFromCulture(false);
      StructureTemplate template = new StructureTemplate();
      CompoundTag nbt = template.save(new CompoundTag());
      String nbtFile = buildingId + "_a_0.nbt";
      Path nbtPath = exportDir.resolve(nbtFile);

      try {
         NbtIo.writeCompressed(nbt, nbtPath);
      } catch (IOException e) {
         LOGGER.error("Failed to write empty NBT", e);
         player.sendSystemMessage(Component.literal("§c[ImportTable] Failed to create NBT: " + e.getMessage()));
         return;
      }

      Path jsonPath = exportDir.resolve(buildingId + ".json");
      JsonObject root = new JsonObject();
      root.addProperty("culture", "millenaire:custom");
      root.addProperty("building_id", buildingId);
      root.addProperty("category", "extra");
      root.addProperty("native_name", buildingId);
      JsonArray variants = new JsonArray();
      JsonObject variantObj = new JsonObject();
      variantObj.addProperty("variant", "a");
      JsonArray levels = new JsonArray();
      JsonObject levelObj = new JsonObject();
      levelObj.addProperty("level", 0);
      JsonObject footprint = new JsonObject();
      footprint.addProperty("width", width);
      footprint.addProperty("height", height);
      footprint.addProperty("depth", length);
      levelObj.add("footprint", footprint);
      levelObj.addProperty("ground_level", startingLevel);
      levels.add(levelObj);
      variantObj.add("levels", levels);
      variants.add(variantObj);
      root.add("variants", variants);

      try {
         Files.writeString(jsonPath, new GsonBuilder().setPrettyPrinting().create().toJson(root));
      } catch (IOException e) {
         LOGGER.error("Failed to write building JSON", e);
      }

      if (clearGround) {
         clearBuildingGround(level, be);
      }

      be.captureSavedState(BuildingExporter.computeBlocksHash(level, be));
      be.setDirty(false);
      placeConstructionBorder(level, be);
      player.sendSystemMessage(Component.literal("§a[ImportTable] Created new building: " + buildingId + " (" + width + "x" + length + ")"));
   }

   private static void placePreviousLevels(ServerLevel level, ImportTableBlockEntity be, int upgradeLevel, ServerPlayer player) {
      if (upgradeLevel > 0) {
         String cultureKey = be.getCultureKey();
         String buildingId = be.getBuildingId();
         String variant = be.getVariant();
         boolean processMockBlocks = !be.isImportMockBlocks();
         Rotation rotation = Rotation.NONE;
         BlockPos scanOrigin = BuildingExporter.computeScanOrigin(be);
         int currentGroundLevel = be.getStartingLevel();
         int baseY = be.getBlockPos().getY();
         Path exportJsonPath = BuildingExporter.findExportedJson(level, buildingId).orElse(null);
         boolean hasCulture = cultureKey != null && !cultureKey.isEmpty();
         ImportTablePlanResolver.PlanView planView = hasCulture ? ImportTablePlanResolver.resolvePlan(cultureKey, buildingId).orElse(null) : null;
         ImportTablePlanResolver.VariantView variantView = planView != null ? planView.variants().get(variant) : null;

         for (int lvl = 0; lvl < upgradeLevel; lvl++) {
            int glLvl;
            StructureTemplate tmpl;
            if (variantView != null && variantView.levels().containsKey(lvl)) {
               ImportTablePlanResolver.LevelView lv = variantView.levels().get(lvl);
               glLvl = lv.groundLevel();
               ContentFs cultureFs = TemplateLoader.cultureFsForImport(ResourceLocation.parse(cultureKey));
               tmpl = TemplateLoader.loadFromPath(lv.nbtPath(), level, cultureFs).orElse(null);
            } else {
               tmpl = TemplateLoader.resolve(level, cultureKey, buildingId, variant, lvl).orElse(null);
               glLvl = readGroundLevelFromJsonPath(exportJsonPath, variant, lvl, currentGroundLevel);
            }

            if (tmpl == null) {
               if (lvl == 0) {
                  player.sendSystemMessage(Component.literal("§e[ImportTable] Warning: level 0 template not found, skipping previous levels"));
                  return;
               }

               player.sendSystemMessage(Component.literal("§e[ImportTable] Warning: level " + lvl + " template not found, skipping"));
            } else {
               BlockPos originLvl = new BlockPos(scanOrigin.getX(), baseY + glLvl, scanOrigin.getZ());
               if (lvl == 0) {
                  BuildingPlacer.placeFromTemplate(level, tmpl, originLvl, rotation, processMockBlocks);
               } else {
                  BuildingPlacer.placeUpgradeFromTemplate(level, tmpl, originLvl, rotation, processMockBlocks);
               }
            }
         }
      }
   }

   private static int readGroundLevelFromJsonPath(@Nullable Path jsonPath, String variant, int upgradeLevel, int fallback) {
      if (jsonPath != null && Files.exists(jsonPath)) {
         try {
            String content = Files.readString(jsonPath);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            JsonArray variants = root.has("variants") ? root.getAsJsonArray("variants") : null;
            if (variants == null) {
               return fallback;
            }

            for (JsonElement ve : variants) {
               JsonObject v = ve.getAsJsonObject();
               if (variant.equals(v.get("variant").getAsString())) {
                  Integer variantGroundLevel = v.has("ground_level") ? v.get("ground_level").getAsInt() : null;
                  JsonArray levels = v.has("levels") ? v.getAsJsonArray("levels") : null;
                  if (levels != null) {
                     for (JsonElement le : levels) {
                        JsonObject l = le.getAsJsonObject();
                        if (l.get("level").getAsInt() == upgradeLevel) {
                           if (l.has("ground_level")) {
                              return l.get("ground_level").getAsInt();
                           }
                           break;
                        }
                     }
                  }

                  return variantGroundLevel != null ? variantGroundLevel : fallback;
               }
            }
         } catch (Exception e) {
            LOGGER.warn("Failed to read ground_level from export JSON: {}", jsonPath, e);
         }

         return fallback;
      } else {
         return fallback;
      }
   }

   private static void placeTemplateWithFixes(ServerLevel level, ImportTableBlockEntity be, StructureTemplate template) {
      BlockPos origin = BuildingExporter.computeScanOrigin(be);
      Rotation rotation = Rotation.NONE;
      boolean processMockBlocks = !be.isImportMockBlocks();
      BuildingPlacer.placeFromTemplate(level, template, origin, rotation, processMockBlocks);
   }

   private static void clearPlotVolume(ServerLevel level, ImportTableBlockEntity be) {
      BlockPos origin = BuildingExporter.computeScanOrigin(be);
      BlockPos tablePos = be.getBlockPos();
      Vec3i size = BuildingExporter.computeScanSize(be);
      int w = size.getX();
      int l = size.getZ();
      int h = size.getY();
      int clearH = h + 20;

      for (int x = 0; x < w; x++) {
         for (int z = 0; z < l; z++) {
            BlockPos foundationPos = origin.offset(x, -1, z);
            if (!foundationPos.equals(tablePos)) {
               level.setBlock(foundationPos, Blocks.STONE.defaultBlockState(), 2);
            }

            for (int y = 0; y < clearH; y++) {
               BlockPos pos = origin.offset(x, y, z);
               if (!pos.equals(tablePos)) {
                  level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
               }
            }
         }
      }
   }

   public static void placeConstructionBorder(ServerLevel level, ImportTableBlockEntity be) {
      BlockPos origin = BuildingExporter.computeScanOrigin(be);
      Vec3i size = BuildingExporter.computeScanSize(be);
      int w = size.getX();
      int l = size.getZ();
      int orientation = be.getOrientation();
      BlockPos tablePos = be.getBlockPos();
      int groundY = tablePos.getY() - 1;
      int minX = origin.getX() - 1;
      int maxX = origin.getX() + w;
      int minZ = origin.getZ() - 1;
      int maxZ = origin.getZ() + l;
      BlockState neutralWool = be.isDirty() ? YELLOW_WOOL : WHITE_WOOL;
      BlockState northWool = orientation == 0 ? ORANGE_WOOL : neutralWool;

      for (int x = minX; x <= maxX; x++) {
         placeBorderBlock(level, new BlockPos(x, groundY, minZ), northWool, tablePos);
      }

      BlockState southWool = orientation == 2 ? ORANGE_WOOL : neutralWool;

      for (int x = minX; x <= maxX; x++) {
         placeBorderBlock(level, new BlockPos(x, groundY, maxZ), southWool, tablePos);
      }

      BlockState westWool = orientation == 3 ? ORANGE_WOOL : neutralWool;

      for (int z = minZ; z <= maxZ; z++) {
         placeBorderBlock(level, new BlockPos(minX, groundY, z), westWool, tablePos);
      }

      BlockState eastWool = orientation == 1 ? ORANGE_WOOL : neutralWool;

      for (int z = minZ; z <= maxZ; z++) {
         placeBorderBlock(level, new BlockPos(maxX, groundY, z), eastWool, tablePos);
      }

      int margin = 2;
      int clearMinX = minX - margin;
      int clearMaxX = maxX + margin;
      int clearMinZ = minZ - margin;
      int clearMaxZ = maxZ + margin;
      int clearHeight = size.getY() + 20;

      for (int x = clearMinX; x <= clearMaxX; x++) {
         for (int z = clearMinZ; z <= clearMaxZ; z++) {
            boolean insidePlot = x >= origin.getX() && x < origin.getX() + w && z >= origin.getZ() && z < origin.getZ() + l;
            if (!insidePlot) {
               for (int y = 1; y <= clearHeight; y++) {
                  BlockPos pos = new BlockPos(x, groundY + y, z);
                  if (!pos.equals(tablePos)) {
                     level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                  }
               }
            }
         }
      }
   }

   private static void placeBorderBlock(ServerLevel level, BlockPos pos, BlockState wool, BlockPos tablePos) {
      if (!pos.equals(tablePos)) {
         level.setBlock(pos, wool, 2);
      }
   }

   private static void clearBuildingGround(ServerLevel level, ImportTableBlockEntity be) {
      BlockPos origin = BuildingExporter.computeScanOrigin(be);
      int w = be.getWidth();
      int l = be.getLength();
      int h = be.getHeight();
      int surfaceY = be.getBlockPos().getY() - 1;
      int originY = origin.getY();

      for (int x = 0; x < w; x++) {
         for (int z = 0; z < l; z++) {
            int worldX = origin.getX() + x;
            int worldZ = origin.getZ() + z;

            for (int y = originY; y <= surfaceY; y++) {
               level.setBlock(new BlockPos(worldX, y, worldZ), Blocks.DIRT.defaultBlockState(), 2);
            }

            for (int y = surfaceY + 1; y < originY + h; y++) {
               level.setBlock(new BlockPos(worldX, y, worldZ), Blocks.AIR.defaultBlockState(), 2);
            }
         }
      }
   }

   private static void importAllLevels(
      ServerLevel level, ImportTableBlockEntity mainBe, ServerPlayer player, int totalLevels, BuildingImporter.LevelImporter importer
   ) {
      mainBe.setUpgradeLevel(0);
      importer.importLevel(0, mainBe);
      int spacing = tableZExtent(mainBe) + 4;

      for (int lvl = 1; lvl < totalLevels; lvl++) {
         BlockPos childPos = mainBe.getBlockPos().offset(0, 0, spacing * lvl);
         level.setBlock(
            childPos,
            (BlockState)((ImportTableBlock)ModBlocks.IMPORT_TABLE.get())
               .defaultBlockState()
               .setValue(ImportTableBlock.FACING, (Direction)level.getBlockState(mainBe.getBlockPos()).getValue(ImportTableBlock.FACING)),
            2
         );
         ImportTableBlockEntity childBe = (ImportTableBlockEntity)level.getBlockEntity(childPos);
         if (childBe == null) {
            LOGGER.warn("Failed to create child ImportTable at {}", childPos);
         } else {
            mainBe.copySettingsTo(childBe);
            childBe.setUpgradeLevel(lvl);
            importer.importLevel(lvl, childBe);
         }
      }
   }

   private static int tableZExtent(ImportTableBlockEntity be) {
      return be.getLength();
   }

   private static String labelFor(String buildingId, String variant, int upgradeLevel) {
      return variant != null && !variant.isBlank() && !"a".equals(variant) ? buildingId + "_" + variant + "_" + upgradeLevel : buildingId + "_" + upgradeLevel;
   }

   private static void placeImportLabel(ServerLevel level, ImportTableBlockEntity be, String label) {
      BlockPos labelPos = be.getBlockPos().offset(0, 0, -1);
      BlockState state = (BlockState)Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8);
      level.setBlock(labelPos, state, 2);
      if (level.getBlockEntity(labelPos) instanceof SignBlockEntity sign) {
         SignText text = new SignText().setMessage(0, Component.literal(label)).setColor(DyeColor.BLACK);
         sign.setText(text, true);
         sign.setText(text, false);
         sign.setChanged();
         level.sendBlockUpdated(labelPos, state, state, 3);
      }
   }

   private static void loadDimensionsFromJson(
      Path exportDir, String buildingId, String variant, int upgradeLevel, ImportTableBlockEntity be, boolean isFreshLoad
   ) {
      Path jsonPath = exportDir.resolve(buildingId + ".json");
      if (Files.exists(jsonPath)) {
         try {
            String content = Files.readString(jsonPath);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            applyJsonOverrides(root, variant, upgradeLevel, be, isFreshLoad);
         } catch (Exception e) {
            LOGGER.warn("Failed to read export JSON: {}", jsonPath, e);
         }
      }
   }

   private static void applyJsonOverrides(JsonObject root, String variant, int upgradeLevel, ImportTableBlockEntity be, boolean isFreshLoad) {
      JsonArray variants = root.getAsJsonArray("variants");
      if (variants != null) {
         Integer planSetOrientation = root.has("building_orientation") ? root.get("building_orientation").getAsInt() : null;

         for (JsonElement ve : variants) {
            JsonObject v = ve.getAsJsonObject();
            if (variant.equals(v.get("variant").getAsString())) {
               Integer variantGroundLevel = v.has("ground_level") ? v.get("ground_level").getAsInt() : null;
               Integer resolvedOrientation = v.has("building_orientation") ? v.get("building_orientation").getAsInt() : planSetOrientation;
               if (resolvedOrientation != null && isFreshLoad) {
                  be.setOrientation(resolvedOrientation & 3);
               }

               JsonArray levels = v.getAsJsonArray("levels");
               if (levels != null) {
                  for (JsonElement le : levels) {
                     JsonObject l = le.getAsJsonObject();
                     if (l.get("level").getAsInt() == upgradeLevel) {
                        if (l.has("footprint")) {
                           JsonObject fp = l.getAsJsonObject("footprint");
                           be.setWidth(fp.get("width").getAsInt());
                           be.setHeight(fp.get("height").getAsInt());
                           be.setLength(fp.get("depth").getAsInt());
                        }

                        if (l.has("ground_level")) {
                           be.setStartingLevel(l.get("ground_level").getAsInt());
                        } else if (variantGroundLevel != null) {
                           be.setStartingLevel(variantGroundLevel);
                        } else {
                           be.setStartingLevel(0);
                        }

                        return;
                     }
                  }
               }
            }
         }
      }
   }

   public record ImportAabb(int minX, int minZ, int maxX, int maxZ) {
      public int xSpan() {
         return this.maxX - this.minX + 1;
      }

      public int zSpan() {
         return this.maxZ - this.minZ + 1;
      }
   }

   @FunctionalInterface
   private interface LevelImporter {
      void importLevel(int var1, ImportTableBlockEntity var2);
   }
}
