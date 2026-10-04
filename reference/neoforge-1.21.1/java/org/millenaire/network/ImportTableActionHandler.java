package org.millenaire.network;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.block.ImportTableBlockEntity;
import org.millenaire.building.BuildingCostCalculator;
import org.millenaire.building.BuildingExporter;
import org.millenaire.building.BuildingImporter;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.TemplateLoader;
import org.millenaire.culture.ModCultures;
import org.millenaire.item.ImportTableItem;
import org.slf4j.Logger;

public final class ImportTableActionHandler {
   private static final Logger LOGGER = LogUtils.getLogger();

   private ImportTableActionHandler() {
   }

   public static void handleAction(ImportTableActionPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (context.player() instanceof ServerPlayer player) {
            if (player.level() instanceof ServerLevel serverLevel) {
               BlockPos var11 = payload.blockPos();
               if (serverLevel.getBlockEntity(var11) instanceof ImportTableBlockEntity importTable) {
                  boolean nearBlock = player.blockPosition().distSqr(var11) <= 64.0;
                  boolean insidePlot = importTable.hasPlan() && ImportTableItem.isInsidePlot(player.blockPosition(), importTable);
                  if (!nearBlock && !insidePlot) {
                     player.sendSystemMessage(Component.literal("§c[ImportTable] Too far from the table"));
                  } else {
                     CompoundTag data = payload.actionData();
                     if (payload.action() == null) {
                        LOGGER.warn("Received ImportTable action with invalid ordinal from player {} at {}", player.getName().getString(), var11);
                     } else {
                        switch (payload.action()) {
                           case CREATE_NEW:
                              handleCreateNew(serverLevel, importTable, player, data);
                              break;
                           case IMPORT_LEVEL:
                              handleImportLevel(serverLevel, importTable, player, data);
                              break;
                           case IMPORT_ALL:
                              handleImportAll(serverLevel, importTable, player, data);
                              break;
                           case IMPORT_LEVEL_EXPORT:
                              handleImportLevelExport(serverLevel, importTable, player, data);
                              break;
                           case IMPORT_ALL_EXPORT:
                              handleImportAllExport(serverLevel, importTable, player, data);
                              break;
                           case REIMPORT:
                              BuildingImporter.reimport(serverLevel, importTable, player);
                              break;
                           case REIMPORT_ALL:
                              BuildingImporter.reimportAll(serverLevel, importTable, player);
                              break;
                           case EXPORT:
                              BuildingExporter.exportLevel(serverLevel, importTable, player);
                              break;
                           case EXPORT_NEW_LEVEL:
                              BuildingExporter.exportNewLevel(serverLevel, importTable, player);
                              break;
                           case UPDATE_SETTINGS:
                              handleUpdateSettings(serverLevel, importTable, data);
                              break;
                           case SHOW_COSTS:
                              handleShowCosts(serverLevel, importTable, player);
                        }
                     }
                  }
               } else {
                  LOGGER.warn("ImportTable action received for non-ImportTable block at {}", var11);
               }
            }
         }
      });
   }

   private static void handleShowCosts(ServerLevel level, ImportTableBlockEntity be, ServerPlayer player) {
      if (!be.hasPlan()) {
         player.sendSystemMessage(Component.literal("§c[ImportTable] No plan loaded on this table"));
      } else {
         String buildingId = be.getBuildingId();
         String variant = be.getVariant();
         int upgradeLevel = be.getUpgradeLevel();
         CompoundTag templateNbt = loadTemplateNbt(level, be);
         if (templateNbt == null) {
            player.sendSystemMessage(
               Component.literal("§c[ImportTable] Could not load template NBT for " + buildingId + " " + variant + " level " + upgradeLevel)
            );
         } else {
            Map<ResourceLocation, Integer> cost = BuildingCostCalculator.computeCost(templateNbt);
            List<ImportTableCostsPayload.Entry> entries = new ArrayList<>(cost.size());
            cost.forEach((id, qty) -> entries.add(new ImportTableCostsPayload.Entry(id.toString(), qty)));
            entries.sort(Comparator.comparing(ImportTableCostsPayload.Entry::itemId));
            ImportTableCostsPayload payload = new ImportTableCostsPayload(be.getBlockPos(), buildingId, variant, upgradeLevel, entries);
            PacketDistributor.sendToPlayer(player, payload, new CustomPacketPayload[0]);
         }
      }
   }

   private static CompoundTag loadTemplateNbt(ServerLevel level, ImportTableBlockEntity be) {
      String cultureKey = be.getCultureKey();
      String buildingId = be.getBuildingId();
      String variant = be.getVariant();
      int upgradeLevel = be.getUpgradeLevel();
      if (!cultureKey.isEmpty() && !cultureKey.startsWith("millenaire-custom")) {
         ResourceLocation planSetId = ResourceLocation.tryParse(cultureKey + "/" + buildingId);
         if (planSetId != null) {
            BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planSetId);
            if (planSet != null) {
               BuildingPlanSet.LevelDef levelDef = planSet.getLevel(variant, upgradeLevel);
               if (levelDef != null) {
                  BuildingPlan plan = ModCultures.getBuildingPlan(levelDef.planId());
                  if (plan != null) {
                     Optional<StructureTemplate> opt = TemplateLoader.load(plan, level, TemplateLoader.cultureFsForImport(plan.culture()));
                     if (opt.isPresent()) {
                        return opt.get().save(new CompoundTag());
                     }
                  }
               }
            }
         }
      }

      Optional<Path> nbtPathOpt = BuildingExporter.findExportedNbt(level, buildingId, variant, upgradeLevel);
      if (nbtPathOpt.isEmpty()) {
         return null;
      }

      Path nbtPath = nbtPathOpt.get();

      try (InputStream is = Files.newInputStream(nbtPath)) {
         return NbtIo.readCompressed(is, NbtAccounter.unlimitedHeap());
      } catch (IOException e) {
         LOGGER.warn("Failed to load NBT for costs: {}", nbtPath, e);
         return null;
      }
   }

   private static void handleCreateNew(ServerLevel level, ImportTableBlockEntity be, ServerPlayer player, CompoundTag data) {
      int length = data.getInt("length");
      int width = data.getInt("width");
      int startingLevel = data.getInt("startingLevel");
      int height = data.getInt("height");
      boolean clearGround = data.getBoolean("clearGround");
      length = Math.max(1, Math.min(length, 256));
      width = Math.max(1, Math.min(width, 256));
      height = Math.max(1, Math.min(height, 256));
      BuildingImporter.createNewBuilding(level, be, player, length, width, startingLevel, height, clearGround);
   }

   private static void handleImportLevel(ServerLevel level, ImportTableBlockEntity be, ServerPlayer player, CompoundTag data) {
      String cultureKey = data.getString("cultureKey");
      String buildingId = data.getString("buildingId");
      String variant = data.getString("variant");
      int upgradeLevel = data.getInt("level");
      boolean isSubBuilding = data.getBoolean("isSubBuilding");
      String parentBuildingId = data.getString("parentBuildingId");
      BuildingImporter.importLevelFromCulture(level, be, player, cultureKey, buildingId, variant, upgradeLevel, isSubBuilding, parentBuildingId);
   }

   private static void handleImportAll(ServerLevel level, ImportTableBlockEntity be, ServerPlayer player, CompoundTag data) {
      String cultureKey = data.getString("cultureKey");
      String buildingId = data.getString("buildingId");
      String variant = data.getString("variant");
      BuildingImporter.importAllFromCulture(level, be, player, cultureKey, buildingId, variant);
   }

   private static void handleImportLevelExport(ServerLevel level, ImportTableBlockEntity be, ServerPlayer player, CompoundTag data) {
      String buildingId = data.getString("buildingId");
      String variant = data.getString("variant");
      int upgradeLevel = data.getInt("level");
      BuildingImporter.importLevelFromExports(level, be, player, buildingId, variant, upgradeLevel);
   }

   private static void handleImportAllExport(ServerLevel level, ImportTableBlockEntity be, ServerPlayer player, CompoundTag data) {
      String buildingId = data.getString("buildingId");
      String variant = data.getString("variant");
      BuildingImporter.importAllFromExports(level, be, player, buildingId, variant);
   }

   private static void handleUpdateSettings(ServerLevel level, ImportTableBlockEntity be, CompoundTag data) {
      boolean orientationChanged = false;
      boolean anyChange = false;
      if (data.contains("orientation")) {
         int newOrient = data.getInt("orientation") & 3;
         if (newOrient != be.getOrientation()) {
            be.setOrientation(newOrient);
            orientationChanged = true;
            anyChange = true;
         }
      }

      anyChange |= updateInt(data, "startingLevel", be.getStartingLevel(), be::setStartingLevel, v -> v);
      anyChange |= updateInt(data, "height", be.getHeight(), be::setHeight, v -> Math.max(1, v));
      anyChange |= updateBool(data, "exportSnow", be.isExportSnow(), be::setExportSnow);
      anyChange |= updateBool(data, "importMockBlocks", be.isImportMockBlocks(), be::setImportMockBlocks);
      anyChange |= updateBool(data, "convertToPreserveGround", be.isConvertToPreserveGround(), be::setConvertToPreserveGround);
      if (anyChange && be.hasPlan()) {
         be.setDirty(true);
      }

      if (orientationChanged && be.hasPlan()) {
         propagateOrientationAndRepaint(level, be);
      }
   }

   private static boolean updateInt(CompoundTag data, String key, int current, IntConsumer setter, IntUnaryOperator transform) {
      if (!data.contains(key)) {
         return false;
      }

      int newValue = transform.applyAsInt(data.getInt(key));
      if (newValue == current) {
         return false;
      }

      setter.accept(newValue);
      return true;
   }

   private static boolean updateBool(CompoundTag data, String key, boolean current, Consumer<Boolean> setter) {
      if (!data.contains(key)) {
         return false;
      }

      boolean newValue = data.getBoolean(key);
      if (newValue == current) {
         return false;
      }

      setter.accept(newValue);
      return true;
   }

   private static void propagateOrientationAndRepaint(ServerLevel level, ImportTableBlockEntity be) {
      int newOrient = be.getOrientation();
      String buildingId = be.getBuildingId();
      String variant = be.getVariant();
      ImportTableBlockEntity main = be.resolveMainTable(level);
      List<ImportTableBlockEntity> all = new ArrayList<>();
      all.add(main);
      all.addAll(main.findChildTables(level));

      for (ImportTableBlockEntity table : all) {
         if (buildingId.equals(table.getBuildingId()) && variant.equals(table.getVariant())) {
            if (table != be) {
               table.setOrientation(newOrient);
               if (table.hasPlan()) {
                  table.setDirty(true);
               }
            }

            if (table.hasPlan()) {
               BuildingImporter.placeConstructionBorder(level, table);
            }
         }
      }
   }
}
