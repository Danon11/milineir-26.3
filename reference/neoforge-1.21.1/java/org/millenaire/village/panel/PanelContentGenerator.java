package org.millenaire.village.panel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import org.millenaire.block.VillagePanelBlockEntity;
import org.millenaire.building.AnywoodHelper;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.ClearMargins;
import org.millenaire.building.ConstructionTask;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.item.ItemHelper;
import org.millenaire.language.BuildingNameHelper;
import org.millenaire.language.LanguageHelper;
import org.millenaire.network.MapData;
import org.millenaire.network.PanelContentPayload;
import org.millenaire.village.Village;
import org.millenaire.village.VillageGrowthManager;
import org.millenaire.village.path.VillagePathManager;
import org.millenaire.world.VillageTerrainMap;

public final class PanelContentGenerator {
   private PanelContentGenerator() {
   }

   public static PanelContent generate(
      PanelType type, Village village, @Nullable BuildingInstance building, @Nullable ServerLevel level, int signIndex, @Nullable ServerPlayer player
   ) {
      return switch (type) {
         case VILLAGE_SUMMARY -> VillageOverviewPanelGenerator.generateSummary(village, building, level);
         case POPULATION -> VillageOverviewPanelGenerator.generatePopulation(village, level);
         case CONSTRUCTIONS -> ConstructionPanelGenerator.generateConstructions(village, player);
         case PROJECTS -> ConstructionPanelGenerator.generateProjects(village, player);
         case HOUSE, BUILDING_DEFAULT -> BuildingPanelGenerator.generateBuildingDefault(village, building, level);
         case RESOURCES -> ConstructionPanelGenerator.generateResources(village, level);
         case VILLAGE_MAP -> VillageMapPanelGenerator.generateVillageMap(village);
         case MILITARY -> MilitaryPanelGenerator.generateMilitary(village, level);
         case ARCHIVES -> BuildingPanelGenerator.generateArchives(village, building, level, signIndex);
         case INN_VISITORS -> CommercePanelGenerator.generateInnVisitors(village, building);
         case INN_TRADE_GOODS -> CommercePanelGenerator.generateInnGoods(village, building);
         case MARKET_MERCHANTS -> CommercePanelGenerator.generateVisitors(village, building, true);
         case VISITORS -> CommercePanelGenerator.generateVisitors(village, building, false);
         case WALLS -> generateWalls(village);
         case CONTROLLED_PROJECTS -> generateStubControlled(village, PanelType.CONTROLLED_PROJECTS, "panel.millenaire.panel_type.controlled_projects");
         case CONTROLLED_MILITARY -> generateStubControlled(village, PanelType.CONTROLLED_MILITARY, "panel.millenaire.panel_type.controlled_military");
         case MARVEL_PROJECTS -> MarvelPanelGenerator.generateMarvelProjects(village);
         case MARVEL_DONATIONS -> MarvelPanelGenerator.generateMarvelDonations(village);
         case MARVEL_RESOURCES -> MarvelPanelGenerator.generateMarvelResources(village, level);
         case HALL_OF_FAME -> new PanelContent(PanelType.HALL_OF_FAME, "panel.millenaire.panel_type.hall_of_fame", List.of(), true, null);
         case CHRONICLE -> VillageOverviewPanelGenerator.generateChronicle(village);
      };
   }

   public static PanelContent buildHoFContent(VillagePanelBlockEntity panel) {
      List<PanelLine> lines = new ArrayList<>();

      for (PanelContentGenerator.DisplayLine dl : panel.getDisplayLines()) {
         if (dl.translatable()) {
            lines.add(PanelLine.translatable(dl.text()));
         } else {
            lines.add(PanelLine.text(dl.text()));
         }
      }

      return new PanelContent(PanelType.HALL_OF_FAME, "panel.millenaire.panel_type.hall_of_fame", lines);
   }

   static PanelContentGenerator.ResourcePanelData collectResourcePanelData(Village village) {
      Village.PendingProject pending = village.getPendingProject();
      if (pending != null) {
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(pending.planSetId());
         String projectName = planSet != null ? planSet.nativeName() : pending.planSetId().getPath();
         if (planSet == null) {
            return new PanelContentGenerator.ResourcePanelData(projectName, pending.isUpgrade(), pending.level(), false, Map.of());
         }

         BuildingPlanSet.LevelDef levelDef = planSet.getLevel(pending.variant(), pending.level());
         Map<ResourceLocation, Integer> reqs = levelDef != null && levelDef.requiredResources() != null ? levelDef.requiredResources() : Map.of();
         return new PanelContentGenerator.ResourcePanelData(projectName, pending.isUpgrade(), pending.level(), false, reqs);
      } else {
         for (BuildingInstance b : village.getBuildings()) {
            if (b.isBeingBuilt() && b.getPlanSetId() != null && b.getVariant() != null) {
               BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(b.getPlanSetId());
               if (planSet != null) {
                  String projectName = BuildingNameHelper.getServerFallbackName(b);
                  boolean isUpgrade = b.getStatus() == BuildingInstance.Status.UPGRADING;
                  BuildingPlanSet.LevelDef levelDef = planSet.getLevel(b.getVariant(), b.getLevel());
                  Map<ResourceLocation, Integer> reqs = levelDef != null && levelDef.requiredResources() != null ? levelDef.requiredResources() : Map.of();
                  return new PanelContentGenerator.ResourcePanelData(projectName, isUpgrade, b.getLevel(), true, reqs);
               }
            }
         }

         return PanelContentGenerator.ResourcePanelData.EMPTY;
      }
   }

   private static PanelContent generateWalls(Village village) {
      String[] titleArgs = new String[]{"panel.millenaire.panel_type.walls", village.getVillageName()};
      int planned = 0;
      int underConstruction = 0;
      int complete = 0;
      int upgrading = 0;
      TreeMap<Integer, Integer> levelCounts = new TreeMap<>();

      for (BuildingInstance b : village.getBuildings()) {
         if (VillageGrowthManager.isWallSegment(b)) {
            switch (b.getStatus()) {
               case PLANNED:
                  planned++;
                  break;
               case UNDER_CONSTRUCTION:
                  underConstruction++;
                  break;
               case COMPLETE:
                  complete++;
                  levelCounts.merge(b.getLevel(), 1, Integer::sum);
                  break;
               case UPGRADING:
                  complete++;
                  upgrading++;
                  levelCounts.merge(b.getLevel(), 1, Integer::sum);
            }
         }
      }

      int total = planned + underConstruction + complete;
      List<PanelLine> lines = new ArrayList<>();
      if (total == 0) {
         lines.add(PanelLine.translatableColored("panel.millenaire.no_walls", 5592405));
         return new PanelContent(PanelType.WALLS, "panel.millenaire.format.label_value", lines, true, titleArgs);
      }

      lines.add(PanelLine.translatableWithMixedArgsColored("panel.millenaire.format.label_value", 0, 1, "panel.millenaire.walls_built", complete + "/" + total));
      int activeWork = underConstruction + upgrading;
      if (activeWork > 0) {
         lines.add(
            PanelLine.translatableWithMixedArgsColored(
               "panel.millenaire.format.label_value", 0, 1, "panel.millenaire.walls_in_construction", String.valueOf(activeWork)
            )
         );
      }

      if (planned > 0) {
         lines.add(
            PanelLine.translatableWithMixedArgsColored(
               "panel.millenaire.format.label_value", 5592405, 1, "panel.millenaire.walls_planned", String.valueOf(planned)
            )
         );
      }

      if (levelCounts.size() > 1) {
         StringBuilder levels = new StringBuilder();

         for (Entry<Integer, Integer> e : levelCounts.entrySet()) {
            if (!levels.isEmpty()) {
               levels.append(", ");
            }

            levels.append("L").append(e.getKey()).append("=").append(e.getValue());
         }

         lines.add(
            PanelLine.translatableWithMixedArgsColored("panel.millenaire.format.label_value", 5592405, 1, "panel.millenaire.walls_levels", levels.toString())
         );
      }

      return new PanelContent(PanelType.WALLS, "panel.millenaire.format.label_value", lines, true, titleArgs);
   }

   private static PanelContent generateStubControlled(Village village, PanelType type, String typeKey) {
      List<PanelLine> lines = new ArrayList<>();
      String ownerName = village.getOwnerName();
      if (ownerName != null) {
         lines.add(PanelLine.text(ownerName));
      } else {
         lines.add(PanelLine.translatable("panel.millenaire.not_controlled"));
      }

      return new PanelContent(type, typeKey, lines, true, null);
   }

   public static PanelContentPayload createMapPayload(PanelContent content, Village village, ServerLevel level, ServerPlayer player) {
      Village.PendingProject pending = village.getPendingProject();
      BuildingId pendingUpgradeTarget = pending != null && pending.isUpgrade() ? pending.buildingId() : null;
      List<MapData.MapBuilding> buildings = new ArrayList<>();

      for (BuildingInstance b : village.getBuildings()) {
         if (!b.isSubBuilding()) {
            BuildingPlan plan = ModCultures.getBuildingPlan(b.getPlanId());
            int w = plan != null ? plan.width() : 5;
            int d = plan != null ? plan.depth() : 5;
            VillageTerrainMap.FootprintRect rect = VillageTerrainMap.computeFootprintRect(
               b.getOrigin().getX(), b.getOrigin().getZ(), w, d, ClearMargins.symmetric(0), b.getRotation()
            );
            boolean canRead = LanguageHelper.canReadBuildingNames(player, village.getCultureId());
            String nativePrefix = null;
            BuildingPlanSet bps = b.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(b.getPlanSetId()) : null;
            String buildingName;
            boolean nameTranslatable;
            if (canRead) {
               buildingName = PanelHelper.getBuildingTranslationKey(b);
               nameTranslatable = true;
               nativePrefix = bps != null ? bps.nativeName() : null;
            } else {
               buildingName = BuildingNameHelper.getServerFallbackName(b);
               nameTranslatable = false;
            }

            String status;
            if (b.getStatus() == BuildingInstance.Status.COMPLETE) {
               if (pendingUpgradeTarget != null && b.getId().equals(pendingUpgradeTarget)) {
                  status = "PENDING";
               } else {
                  BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(b.getPlanSetId());
                  if (planSet != null && planSet.hasNextLevel(b.getVariant(), b.getLevel())) {
                     status = "IDLE";
                  } else {
                     status = "COMPLETE";
                  }
               }
            } else {
               status = b.getStatus().name();
            }

            boolean isWall = VillageGrowthManager.isWallSegment(b);
            buildings.add(
               new MapData.MapBuilding(
                  rect.startX(), rect.startZ(), rect.width(), rect.depth(), buildingName, status, b.getLevel(), nameTranslatable, nativePrefix, isWall
               )
            );
         }
      }

      List<MapData.MapVillager> villagers = new ArrayList<>();

      for (Entry<UUID, ResourceLocation> entry : village.getVillagerTypes().entrySet()) {
         if (level.getEntity(entry.getKey()) instanceof MillVillager mv) {
            String name = mv.getFirstName() != null ? mv.getFirstName() : "???";
            VillagerType vtype = ModCultures.getVillagerType(entry.getValue());
            String gender;
            if (vtype != null && vtype.isChild()) {
               gender = "CHILD";
            } else {
               gender = vtype != null ? vtype.gender().name() : "MALE";
            }

            String roleKey = PanelHelper.resolveRoleKey(entry.getValue());
            String goalLabel = mv.getGoalLabel() != null ? mv.getGoalLabel() : "";
            villagers.add(new MapData.MapVillager(mv.blockPosition().getX(), mv.blockPosition().getZ(), name, gender, roleKey, goalLabel, mv.isChief()));
         }
      }

      MapData.MapTerrain terrain = computeTerrainGrid(village, level);
      List<MapData.MapChunk> chunkList = new ArrayList<>();
      Set<ChunkPos> loadedChunks = village.getLoadedChunks();

      for (ChunkPos cp : village.computeVillageChunks()) {
         chunkList.add(new MapData.MapChunk(cp.x, cp.z, loadedChunks.contains(cp)));
      }

      List<MapData.MapPath> paths = new ArrayList<>();
      VillagePathManager pm = village.getPathManager();
      if (pm != null) {
         pm.forEachPath((pos, level2) -> paths.add(new MapData.MapPath(pos.getX(), pos.getZ(), (byte)level2.intValue())));
      }

      return PanelContentPayload.fromContentWithMap(
         content,
         buildings,
         villagers,
         player.blockPosition().getX(),
         player.blockPosition().getZ(),
         village.getCenter().getX(),
         village.getCenter().getZ(),
         terrain,
         chunkList,
         paths
      );
   }

   private static MapData.MapTerrain computeTerrainGrid(Village village, ServerLevel level) {
      VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
      int radius = vt != null ? vt.radius() : 90;
      VillageTerrainMap terrainMap = VillageTerrainMap.compute(level, village.getCenter(), radius);
      int size = terrainMap.getSize();
      int originX = village.getCenter().getX() - radius;
      int originZ = village.getCenter().getZ() - radius;
      byte[] data = new byte[size * size];

      for (int lx = 0; lx < size; lx++) {
         for (int lz = 0; lz < size; lz++) {
            byte tile;
            if (terrainMap.isWaterAt(lx, lz)) {
               tile = 1;
            } else if (terrainMap.isDangerAt(lx, lz)) {
               tile = 2;
            } else if (terrainMap.isBuildingForbiddenAt(lx, lz)) {
               tile = 3;
            } else if (terrainMap.isOccupied(lx, lz)) {
               tile = 6;
            } else if (!terrainMap.canBuildAt(lx, lz)) {
               tile = 4;
            } else {
               tile = 5;
            }

            data[lx * size + lz] = tile;
         }
      }

      return new MapData.MapTerrain(originX, originZ, size, size, data);
   }

   public static PanelContentGenerator.DisplayData generateDisplayLines(
      PanelType type, Village village, @Nullable BuildingInstance building, @Nullable ServerLevel level, int signIndex
   ) {
      PanelContentGenerator.DisplayData data = new PanelContentGenerator.DisplayData(new ArrayList<>());
      switch (type) {
         case VILLAGE_SUMMARY:
            VillageOverviewPanelGenerator.addSummary3D(data, village);
            break;
         case POPULATION:
            VillageOverviewPanelGenerator.addPopulation3D(data, village);
            break;
         case CONSTRUCTIONS:
            ConstructionPanelGenerator.addConstructions3D(data, village);
            break;
         case PROJECTS:
            ConstructionPanelGenerator.addProjects3D(data, village);
            break;
         case HOUSE:
            if (building != null) {
               BuildingPanelGenerator.addBuildingDefault3D(data, village, building, level);
            }
            break;
         case BUILDING_DEFAULT:
            if (building != null) {
               BuildingPanelGenerator.addBuildingDefault3D(data, village, building, level);
            }
            break;
         case RESOURCES:
            ConstructionPanelGenerator.addResources3D(data, village, level);
            break;
         case VILLAGE_MAP:
            VillageMapPanelGenerator.addVillageMap3D(data, village);
            break;
         case MILITARY:
            MilitaryPanelGenerator.addMilitary3D(data, village);
            break;
         case ARCHIVES:
            if (building != null && level != null) {
               BuildingPanelGenerator.addArchives3D(data, village, building, level, signIndex);
            } else {
               data.addCentered(PanelHelper.t("panel.millenaire.reserved_for_future", ""));
            }
            break;
         case INN_VISITORS:
         case INN_TRADE_GOODS:
         case MARKET_MERCHANTS:
         case VISITORS:
            CommercePanelGenerator.addCommerce3D(data, type, village, building);
            break;
         case WALLS:
            data.addCenteredTranslatable("panel.millenaire.panel_type.walls");
            data.addCentered("");
            int wallsPlanned = 0;
            int wallsUnderConstruction = 0;
            int wallsComplete = 0;
            int wallsUpgrading = 0;

            for (BuildingInstance b : village.getBuildings()) {
               if (VillageGrowthManager.isWallSegment(b)) {
                  switch (b.getStatus()) {
                     case PLANNED:
                        wallsPlanned++;
                        break;
                     case UNDER_CONSTRUCTION:
                        wallsUnderConstruction++;
                        break;
                     case COMPLETE:
                        wallsComplete++;
                        break;
                     case UPGRADING:
                        wallsComplete++;
                        wallsUpgrading++;
                  }
               }
            }

            int wallsTotal = wallsPlanned + wallsUnderConstruction + wallsComplete;
            if (wallsTotal == 0) {
               data.addCenteredTranslatable("panel.millenaire.no_walls");
            } else {
               data.addCentered(PanelHelper.t("panel.millenaire.walls_built") + ": " + wallsComplete + "/" + wallsTotal);
               int wallsActiveWork = wallsUnderConstruction + wallsUpgrading;
               if (wallsActiveWork > 0) {
                  data.addCentered(PanelHelper.t("panel.millenaire.walls_in_construction") + ": " + wallsActiveWork);
               }

               if (wallsPlanned > 0) {
                  data.addCentered(PanelHelper.t("panel.millenaire.walls_planned") + ": " + wallsPlanned);
               }
            }
            break;
         case CONTROLLED_PROJECTS:
         case CONTROLLED_MILITARY:
         default:
            data.addCentered(PanelHelper.t("panel.millenaire.panel_type." + type.name().toLowerCase(Locale.ROOT)));
            if (building != null) {
               data.addCenteredBuildingName(PanelHelper.getBuildingTranslationKey(building), BuildingNameHelper.getServerFallbackName(building));
            }
            break;
         case MARVEL_PROJECTS:
            MarvelPanelGenerator.addMarvelProjects3D(data, village);
            break;
         case MARVEL_DONATIONS:
            MarvelPanelGenerator.addMarvelDonations3D(data, village);
            break;
         case MARVEL_RESOURCES:
            MarvelPanelGenerator.addMarvelResources3D(data, village, level);
         case HALL_OF_FAME:
      }

      return data.displayLines().size() > 8 ? new PanelContentGenerator.DisplayData(new ArrayList<>(data.displayLines().subList(0, 8))) : data;
   }

   static void addResourceLines(List<PanelLine> lines, Map<ResourceLocation, Integer> required, Village village, @Nullable ServerLevel level) {
      BuildingInstance townhall = village.getTownhall();
      BuildingInventory inv = townhall != null ? townhall.getInventory() : null;
      if (inv != null) {
         inv.invalidateCache();
      }

      TreeMap<String, PanelContentGenerator.ResourceLineInfo> sorted = new TreeMap<>();

      for (Entry<ResourceLocation, Integer> entry : required.entrySet()) {
         if (!entry.getKey().getPath().startsWith("mock_")) {
            if (AnywoodHelper.isAnywood(entry.getKey())) {
               int need = entry.getValue();
               int have = 0;
               if (inv != null && level != null) {
                  have = inv.getCountByTag(level, AnywoodHelper.LOGS_TAG);
                  have += countBuildersInventoryByTag(village, level, AnywoodHelper.LOGS_TAG);
               }

               have = Math.min(have, need);
               String anywoodDescId = "item.millenaire.anywood_log";
               String itemName = Component.translatable(anywoodDescId).getString();
               sorted.put(itemName, new PanelContentGenerator.ResourceLineInfo(have, need, "minecraft:oak_log", anywoodDescId));
            } else {
               int need = entry.getValue();
               int have = 0;
               if (inv != null && level != null) {
                  Item item = ItemHelper.resolve(entry.getKey());
                  if (item != null) {
                     have = inv.getCount(level, item);
                     have += countBuildersInventory(village, level, item);
                  }
               }

               have = Math.min(have, need);
               Item item = ItemHelper.resolve(entry.getKey());
               String descId = null;
               String itemName;
               if (item != null) {
                  descId = item.getDescriptionId();
                  itemName = Component.translatable(descId).getString();
               } else {
                  itemName = entry.getKey().getPath().replace('_', ' ');
               }

               String itemId = entry.getKey().toString();
               sorted.put(itemName, new PanelContentGenerator.ResourceLineInfo(have, need, itemId, descId));
            }
         }
      }

      for (Entry<String, PanelContentGenerator.ResourceLineInfo> entry : sorted.entrySet()) {
         PanelContentGenerator.ResourceLineInfo info = entry.getValue();
         int have = info.have;
         int need = info.need;
         String color = have >= need ? "§2" : "§4";
         String right = color + have + "/" + need;
         int colorInt = have >= need ? 43520 : 11141120;
         if (info.descId != null && info.itemId != null) {
            lines.add(PanelLine.withIconTranslatable(info.descId, right, info.itemId, colorInt));
         } else if (info.itemId != null) {
            String left = "  " + color + entry.getKey();
            lines.add(PanelLine.withIcon(left, right, info.itemId));
         } else {
            String left = "  " + color + entry.getKey();
            lines.add(PanelLine.columns(left, right));
         }
      }
   }

   private static int countBuildersInventoryByTag(Village village, ServerLevel level, TagKey<Item> tag) {
      int total = 0;

      for (BuildingInstance b : village.getBuildings()) {
         ConstructionTask task = b.getConstructionTask();
         if (task != null && !task.isComplete()) {
            UUID builderUuid = task.getReservedBuilder();
            if (builderUuid != null && level.getEntity(builderUuid) instanceof MillVillager villager) {
               total += villager.getInventory().getCountByTag(tag);
            }
         }
      }

      return total;
   }

   private static int countBuildersInventory(Village village, ServerLevel level, Item item) {
      int total = 0;

      for (UUID uuid : village.getVillagerUuids()) {
         if (level.getEntity(uuid) instanceof MillVillager mv) {
            GoalScheduler scheduler = mv.getGoalScheduler();
            if (scheduler != null && scheduler.getCurrentTask() != null && "build".equals(scheduler.getCurrentTask().goalId().getPath())) {
               total += mv.getInventory().getCount(item);
            }
         }
      }

      return total;
   }

   public record DisplayData(List<PanelContentGenerator.DisplayLine> displayLines) {
      void addCentered(String text) {
         this.displayLines.add(PanelContentGenerator.DisplayLine.centered(text));
      }

      void addCenteredTranslatable(String key) {
         this.displayLines.add(PanelContentGenerator.DisplayLine.centeredTranslatable(key));
      }

      void addCenteredBuildingName(String translationKey, @Nullable String nativePrefix) {
         if (nativePrefix != null) {
            this.displayLines.add(PanelContentGenerator.DisplayLine.centeredTranslatableNative(translationKey, nativePrefix));
         } else {
            this.displayLines.add(PanelContentGenerator.DisplayLine.centeredTranslatable(translationKey));
         }
      }

      void addLeft(String text) {
         this.displayLines.add(PanelContentGenerator.DisplayLine.left(text));
      }

      void addCenteredWithLeftIcon(String text, String icon) {
         this.displayLines.add(PanelContentGenerator.DisplayLine.centeredWithLeftIcon(text, icon));
      }

      void addLeftWithIcon(String text, String icon) {
         this.displayLines.add(PanelContentGenerator.DisplayLine.leftWithIcon(text, icon));
      }

      void addCenteredWithIcons(String text, String leftIcon, String rightIcon) {
         this.displayLines.add(PanelContentGenerator.DisplayLine.centeredWithIcons(text, leftIcon, rightIcon));
      }

      void addCenteredWithIconsTranslatable(String key, String leftIcon, String rightIcon) {
         this.displayLines.add(PanelContentGenerator.DisplayLine.centeredWithIconsTranslatable(key, leftIcon, rightIcon));
      }

      void addCenteredBuildingNameWithIcons(String translationKey, String leftIcon, String rightIcon, @Nullable String nativePrefix) {
         if (nativePrefix != null) {
            this.displayLines.add(PanelContentGenerator.DisplayLine.centeredWithIconsTranslatableNative(translationKey, leftIcon, rightIcon, nativePrefix));
         } else {
            this.displayLines.add(PanelContentGenerator.DisplayLine.centeredWithIconsTranslatable(translationKey, leftIcon, rightIcon));
         }
      }

      void addCenteredWithIconStacks(String text, ItemStack leftStack, ItemStack rightStack) {
         this.displayLines.add(PanelContentGenerator.DisplayLine.centeredWithIconStacks(text, leftStack, rightStack));
      }

      void addColumns(String left, String right) {
         this.displayLines.add(PanelContentGenerator.DisplayLine.columns(left, right));
      }
   }

   public record DisplayLine(
      String text,
      String leftIcon,
      String middleIcon,
      String rightIcon,
      String leftColumn,
      String rightColumn,
      boolean centered,
      boolean translatable,
      ItemStack leftIconStack,
      ItemStack rightIconStack,
      @Nullable String nativePrefix
   ) {
      public DisplayLine(String text, String leftIcon, String middleIcon, String rightIcon, String leftColumn, String rightColumn, boolean centered) {
         this(text, leftIcon, middleIcon, rightIcon, leftColumn, rightColumn, centered, false, ItemStack.EMPTY, ItemStack.EMPTY, null);
      }

      public DisplayLine(
         String text, String leftIcon, String middleIcon, String rightIcon, String leftColumn, String rightColumn, boolean centered, boolean translatable
      ) {
         this(text, leftIcon, middleIcon, rightIcon, leftColumn, rightColumn, centered, translatable, ItemStack.EMPTY, ItemStack.EMPTY, null);
      }

      public DisplayLine(
         String text,
         String leftIcon,
         String middleIcon,
         String rightIcon,
         String leftColumn,
         String rightColumn,
         boolean centered,
         boolean translatable,
         ItemStack leftIconStack,
         ItemStack rightIconStack
      ) {
         this(text, leftIcon, middleIcon, rightIcon, leftColumn, rightColumn, centered, translatable, leftIconStack, rightIconStack, null);
      }

      public static PanelContentGenerator.DisplayLine centered(String text) {
         return new PanelContentGenerator.DisplayLine(text, "", "", "", "", "", true, false);
      }

      public static PanelContentGenerator.DisplayLine centeredTranslatable(String key) {
         return new PanelContentGenerator.DisplayLine(key, "", "", "", "", "", true, true);
      }

      public static PanelContentGenerator.DisplayLine centeredTranslatableNative(String key, @Nullable String nativePrefix) {
         return new PanelContentGenerator.DisplayLine(key, "", "", "", "", "", true, true, ItemStack.EMPTY, ItemStack.EMPTY, nativePrefix);
      }

      public static PanelContentGenerator.DisplayLine left(String text) {
         return new PanelContentGenerator.DisplayLine(text, "", "", "", "", "", false, false);
      }

      public static PanelContentGenerator.DisplayLine centeredWithLeftIcon(String text, String leftIcon) {
         return new PanelContentGenerator.DisplayLine(text, leftIcon != null ? leftIcon : "", "", "", "", "", true, false);
      }

      public static PanelContentGenerator.DisplayLine leftWithIcon(String text, String leftIcon) {
         return new PanelContentGenerator.DisplayLine(text, leftIcon != null ? leftIcon : "", "", "", "", "", false, false);
      }

      public static PanelContentGenerator.DisplayLine centeredWithIcons(String text, String leftIcon, String rightIcon) {
         return new PanelContentGenerator.DisplayLine(text, leftIcon != null ? leftIcon : "", "", rightIcon != null ? rightIcon : "", "", "", true, false);
      }

      public static PanelContentGenerator.DisplayLine centeredWithIconsTranslatable(String key, String leftIcon, String rightIcon) {
         return new PanelContentGenerator.DisplayLine(key, leftIcon != null ? leftIcon : "", "", rightIcon != null ? rightIcon : "", "", "", true, true);
      }

      public static PanelContentGenerator.DisplayLine centeredWithIconsTranslatableNative(
         String key, String leftIcon, String rightIcon, @Nullable String nativePrefix
      ) {
         return new PanelContentGenerator.DisplayLine(
            key, leftIcon != null ? leftIcon : "", "", rightIcon != null ? rightIcon : "", "", "", true, true, ItemStack.EMPTY, ItemStack.EMPTY, nativePrefix
         );
      }

      public static PanelContentGenerator.DisplayLine centeredWithIconStacks(String text, ItemStack leftStack, ItemStack rightStack) {
         return new PanelContentGenerator.DisplayLine(
            text, "", "", "", "", "", true, false, leftStack != null ? leftStack : ItemStack.EMPTY, rightStack != null ? rightStack : ItemStack.EMPTY
         );
      }

      public static PanelContentGenerator.DisplayLine columns(String leftCol, String rightCol) {
         return new PanelContentGenerator.DisplayLine("", "", "", "", leftCol != null ? leftCol : "", rightCol != null ? rightCol : "", false, false);
      }
   }

   private record ResourceLineInfo(int have, int need, @Nullable String itemId, @Nullable String descId) {
   }

   record ResourcePanelData(@Nullable String projectNameKey, boolean isUpgrade, int level, boolean inProgress, Map<ResourceLocation, Integer> resources) {
      static final PanelContentGenerator.ResourcePanelData EMPTY = new PanelContentGenerator.ResourcePanelData(null, false, 0, false, Map.of());
   }
}
