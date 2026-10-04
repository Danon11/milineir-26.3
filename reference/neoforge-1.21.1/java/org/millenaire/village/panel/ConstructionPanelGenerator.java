package org.millenaire.village.panel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import org.millenaire.building.AnywoodHelper;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.ConstructionTask;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.item.ItemHelper;
import org.millenaire.language.BuildingNameHelper;
import org.millenaire.language.LanguageHelper;
import org.millenaire.village.Village;

public final class ConstructionPanelGenerator {
   private ConstructionPanelGenerator() {
   }

   public static PanelContent generateConstructions(Village village, @Nullable ServerPlayer player) {
      List<PanelLine> lines = new ArrayList<>();
      String titleKey = "panel.millenaire.title.constructions";
      String[] titleArgs = new String[]{village.getVillageName()};
      boolean canRead = player != null && LanguageHelper.canReadBuildingNames(player, village.getCultureId());
      boolean found = false;

      for (BuildingInstance b : village.getBuildings()) {
         if (b.isBeingBuilt()) {
            found = true;
            int progress = 0;
            ConstructionTask task = b.getConstructionTask();
            if (task != null) {
               progress = Math.round(task.progress() * 100.0F);
            }

            PanelHelper.DirectionInfo dir = PanelHelper.computeDirectionInfo(village.getCenter(), b.getOrigin());
            PanelLine.PanelNavTarget nav = PanelHelper.buildingNavTarget(b);
            BuildingPlanSet activePlanSet = b.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(b.getPlanSetId()) : null;
            if (activePlanSet != null) {
               lines.add(buildingNameLine(activePlanSet, canRead, nav, 170));
            } else {
               String fallbackName = b.getPlanId().getPath();
               if (nav != null) {
                  lines.add(new PanelLine(fallbackName, false, null, null, null, false, nav, null, 170, false, 0));
               } else {
                  lines.add(PanelLine.colored(fallbackName, 170));
               }
            }

            if (b.getStatus() == BuildingInstance.Status.UPGRADING) {
               if (dir.atCenter()) {
                  lines.add(
                     PanelLine.translatableWithArgs(
                        "panel.millenaire.construction_detail_upgrading_center", String.valueOf(b.getLevel()), String.valueOf(progress)
                     )
                  );
               } else {
                  lines.add(
                     PanelLine.translatableWithMixedArgs(
                        "panel.millenaire.construction_detail_upgrading_dir",
                        8,
                        String.valueOf(b.getLevel()),
                        String.valueOf(progress),
                        String.valueOf(dir.distance()),
                        dir.cardinalKey()
                     )
                  );
               }
            } else if (dir.atCenter()) {
               lines.add(PanelLine.translatableWithArgs("panel.millenaire.construction_detail_building_center", String.valueOf(progress)));
            } else {
               lines.add(
                  PanelLine.translatableWithMixedArgs(
                     "panel.millenaire.construction_detail_building_dir", 4, String.valueOf(progress), String.valueOf(dir.distance()), dir.cardinalKey()
                  )
               );
            }
         }
      }

      if (!found) {
         lines.add(PanelLine.translatable("panel.millenaire.no_construction"));
      }

      lines.add(PanelLine.separator());
      lines.add(PanelLine.translatable("panel.millenaire.existing_buildings"));

      for (BuildingInstance b : village.getBuildings()) {
         if (!b.isSubBuilding()) {
            String nameKey = PanelHelper.getBuildingTranslationKey(b);
            BuildingPlanSet allBps = b.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(b.getPlanSetId()) : null;
            String nameArg = canRead ? nameKey : (allBps != null ? allBps.nativeName() : b.getPlanId().getPath());
            int centerMask = canRead ? 3 : 2;
            int dirMask = canRead ? 19 : 18;
            PanelHelper.DirectionInfo dir = PanelHelper.computeDirectionInfo(village.getCenter(), b.getOrigin());
            PanelLine.PanelNavTarget bNav = PanelHelper.buildingNavTarget(b);
            if (dir.atCenter()) {
               if (bNav != null) {
                  lines.add(
                     PanelLine.clickableTranslatableWithMixedArgs(
                        "panel.millenaire.building_with_level_center", bNav, centerMask, nameArg, "panel.millenaire.level", String.valueOf(b.getLevel())
                     )
                  );
               } else {
                  lines.add(
                     PanelLine.translatableWithMixedArgs(
                        "panel.millenaire.building_with_level_center", centerMask, nameArg, "panel.millenaire.level", String.valueOf(b.getLevel())
                     )
                  );
               }
            } else if (bNav != null) {
               lines.add(
                  PanelLine.clickableTranslatableWithMixedArgs(
                     "panel.millenaire.building_with_level_dir",
                     bNav,
                     dirMask,
                     nameArg,
                     "panel.millenaire.level",
                     String.valueOf(b.getLevel()),
                     String.valueOf(dir.distance()),
                     dir.cardinalKey()
                  )
               );
            } else {
               lines.add(
                  PanelLine.translatableWithMixedArgs(
                     "panel.millenaire.building_with_level_dir",
                     dirMask,
                     nameArg,
                     "panel.millenaire.level",
                     String.valueOf(b.getLevel()),
                     String.valueOf(dir.distance()),
                     dir.cardinalKey()
                  )
               );
            }
         }
      }

      return new PanelContent(PanelType.CONSTRUCTIONS, titleKey, lines, true, titleArgs);
   }

   public static PanelContent generateProjects(Village village, @Nullable ServerPlayer player) {
      List<PanelLine> lines = new ArrayList<>();
      String titleKey = "panel.millenaire.title.projects";
      String[] titleArgs = new String[]{village.getVillageName()};
      boolean canRead = player != null && LanguageHelper.canReadBuildingNames(player, village.getCultureId());
      VillageType villageType = ModCultures.getVillageType(village.getVillageTypeId());
      if (villageType == null) {
         lines.add(PanelLine.translatable("panel.millenaire.unknown_village_type"));
         return new PanelContent(PanelType.PROJECTS, titleKey, lines, true, titleArgs);
      }

      Village.PendingProject pending = village.getPendingProject();
      if (pending != null) {
         String pendingKey = PanelHelper.getPendingProjectKey(pending);
         lines.add(PanelLine.translatableWithMixedArgs("panel.millenaire.pending_project_value", 1, pendingKey));
         lines.add(PanelLine.translatable("panel.millenaire.awaiting_resources"));
         lines.add(PanelLine.separator());
      }

      Map<String, List<VillageType.LayoutSlot>> slotsByRole = new LinkedHashMap<>();

      for (VillageType.LayoutSlot slot : villageType.layout()) {
         slotsByRole.computeIfAbsent(slot.role(), k -> new ArrayList<>()).add(slot);
      }

      Set<BuildingId> claimedInstances = new HashSet<>();

      for (Entry<String, List<VillageType.LayoutSlot>> roleEntry : slotsByRole.entrySet()) {
         String role = roleEntry.getKey();
         List<VillageType.LayoutSlot> slots = roleEntry.getValue();
         String roleDisplay = role.substring(0, 1).toUpperCase() + role.substring(1);
         lines.add(PanelLine.text("§1" + roleDisplay));

         for (VillageType.LayoutSlot slot : slots) {
            BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(slot.plan());
            if (planSet != null) {
               BuildingInstance existing = null;

               for (BuildingInstance b : village.getBuildings()) {
                  if (slot.plan().equals(b.getPlanSetId()) && !claimedInstances.contains(b.getId())) {
                     existing = b;
                     break;
                  }
               }

               if (existing != null) {
                  claimedInstances.add(existing.getId());
               }

               PanelLine.PanelNavTarget psNav = PanelHelper.planSetNavTarget(slot.plan());
               if (existing == null) {
                  lines.add(buildingNameLine(planSet, canRead, psNav, 5592575));
                  lines.add(PanelLine.translatable("panel.millenaire.not_yet_built"));
               } else if (!planSet.hasNextLevel(existing.getVariant(), existing.getLevel())) {
                  PanelHelper.DirectionInfo dir = PanelHelper.computeDirectionInfo(village.getCenter(), existing.getOrigin());
                  lines.add(buildingNameLine(planSet, canRead, psNav, 43520));
                  if (dir.atCenter()) {
                     lines.add(PanelLine.translatable("panel.millenaire.finished_center"));
                  } else {
                     lines.add(PanelLine.translatableWithMixedArgs("panel.millenaire.finished_dir", 2, String.valueOf(dir.distance()), dir.cardinalKey()));
                  }
               } else {
                  int maxLevel = planSet.getLevelCount(existing.getVariant()) - 1;
                  int remaining = maxLevel - existing.getLevel();
                  PanelHelper.DirectionInfo dir = PanelHelper.computeDirectionInfo(village.getCenter(), existing.getOrigin());
                  lines.add(buildingNameLine(planSet, canRead, psNav, 0));
                  if (dir.atCenter()) {
                     lines.add(PanelLine.translatableWithArgs("panel.millenaire.upgrades_left_center", String.valueOf(remaining)));
                  } else {
                     lines.add(
                        PanelLine.translatableWithMixedArgs(
                           "panel.millenaire.upgrades_left_dir", 4, String.valueOf(remaining), String.valueOf(dir.distance()), dir.cardinalKey()
                        )
                     );
                  }
               }
            }
         }
      }

      return new PanelContent(PanelType.PROJECTS, titleKey, lines, true, titleArgs);
   }

   private static PanelLine buildingNameLine(BuildingPlanSet planSet, boolean canRead, @Nullable PanelLine.PanelNavTarget nav, int color) {
      if (canRead) {
         String key = BuildingNameHelper.getTranslationKey(planSet);
         return nav != null
            ? PanelLine.clickableWithTranslationColored(planSet.nativeName(), key, nav, color)
            : PanelLine.withTranslationColored(planSet.nativeName(), key, color);
      } else {
         return nav != null
            ? new PanelLine(planSet.nativeName(), false, null, null, null, false, nav, null, color, false, 0)
            : PanelLine.colored(planSet.nativeName(), color);
      }
   }

   public static PanelContent generateResources(Village village, @Nullable ServerLevel level) {
      List<PanelLine> lines = new ArrayList<>();
      String titleKey = "panel.millenaire.title.resources";
      String[] titleArgs = new String[]{village.getVillageName()};
      PanelContentGenerator.ResourcePanelData data = PanelContentGenerator.collectResourcePanelData(village);
      if (data.projectNameKey() != null) {
         lines.add(PanelLine.translatable("panel.millenaire.resources_needed"));
         if (data.isUpgrade()) {
            lines.add(PanelLine.translatableWithMixedArgs("panel.millenaire.project_name_upgrade", 0, data.projectNameKey(), String.valueOf(data.level())));
         } else {
            lines.add(PanelLine.translatableWithMixedArgs("panel.millenaire.project_name_construction", 0, data.projectNameKey()));
         }

         lines.add(PanelLine.text(""));
         if (!data.resources().isEmpty()) {
            if (data.inProgress()) {
               addResourceLinesAllMet(lines, data.resources());
            } else {
               PanelContentGenerator.addResourceLines(lines, data.resources(), village, level);
            }
         } else {
            lines.add(PanelLine.translatable("panel.millenaire.no_resources_needed"));
         }
      } else {
         lines.add(PanelLine.translatable("panel.millenaire.no_project"));
      }

      lines.add(PanelLine.separator());
      lines.add(PanelLine.translatable("panel.millenaire.resources_available"));
      lines.add(PanelLine.text(""));
      BuildingInstance townhall = village.getTownhall();
      if (townhall != null && townhall.getInventory() != null && level != null) {
         BuildingInventory inv = townhall.getInventory();
         Map<Item, Integer> contents = inv.scanChests(level);

         record InvEntry(String descId, String itemId, int count) {
         }

         TreeMap<String, InvEntry> sorted = new TreeMap<>();

         for (Entry<Item, Integer> itemEntry : contents.entrySet()) {
            if (itemEntry.getValue() > 0) {
               String descId = itemEntry.getKey().getDescriptionId();
               String sortName = Component.translatable(descId).getString();
               ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(itemEntry.getKey());
               sorted.put(sortName, new InvEntry(descId, itemKey.toString(), itemEntry.getValue()));
            }
         }

         if (sorted.isEmpty()) {
            lines.add(PanelLine.translatableColored("panel.millenaire.no_chests", 5592405));
         } else {
            for (Entry<String, InvEntry> sortedEntry : sorted.entrySet()) {
               InvEntry entry = sortedEntry.getValue();
               lines.add(PanelLine.withIconTranslatable(entry.descId, "§1" + entry.count, entry.itemId, 0));
            }
         }
      } else if (level == null) {
         lines.add(PanelLine.translatable("panel.millenaire.resources_unavailable_offline"));
      } else {
         lines.add(PanelLine.translatable("panel.millenaire.no_chests"));
      }

      return new PanelContent(PanelType.RESOURCES, titleKey, lines, true, titleArgs);
   }

   private static void addResourceLinesAllMet(List<PanelLine> lines, Map<ResourceLocation, Integer> required) {
      for (Entry<ResourceLocation, Integer> entry : required.entrySet()) {
         if (!entry.getKey().getPath().startsWith("mock_")) {
            int cost = entry.getValue();
            String itemId;
            String descId;
            if (AnywoodHelper.isAnywood(entry.getKey())) {
               itemId = "minecraft:oak_log";
               descId = "item.millenaire.anywood_log";
            } else {
               itemId = entry.getKey().toString();
               Item item = ItemHelper.resolve(entry.getKey());
               descId = item != null ? item.getDescriptionId() : null;
            }

            String right = "§2" + cost + "/" + cost;
            if (descId != null) {
               lines.add(PanelLine.withIconTranslatable(descId, right, itemId, 43520));
            } else {
               String name = entry.getKey().getPath().replace('_', ' ');
               lines.add(PanelLine.withIcon("  §2" + name, right, itemId));
            }
         }
      }
   }

   static void addConstructions3D(PanelContentGenerator.DisplayData data, Village village) {
      int nbActive = 0;

      for (BuildingInstance b : village.getBuildings()) {
         if (b.isBeingBuilt()) {
            nbActive++;
         }
      }

      if (nbActive == 1) {
         for (BuildingInstance b : village.getBuildings()) {
            if (b.isBeingBuilt()) {
               String bldIcon = PanelHelper.resolveBuildingIcon(b);
               data.addCenteredBuildingNameWithIcons(PanelHelper.getBuildingTranslationKey(b), bldIcon, bldIcon, BuildingNameHelper.getServerFallbackName(b));
               data.addCentered("");
               int progress = 0;
               ConstructionTask task = b.getConstructionTask();
               if (task != null) {
                  progress = Math.round(task.progress() * 100.0F);
               }

               data.addCentered(PanelHelper.t("panel.millenaire.in_construction") + " " + progress + "%");
               BuildingInstance th = village.getTownhall();
               if (th != null) {
                  data.addCentered(PanelHelper.computeDirection(th.getOrigin(), b.getOrigin()));
               }
               break;
            }
         }
      } else if (nbActive > 1) {
         data.addCentered(nbActive + " " + PanelHelper.t("panel.millenaire.constructions_title"));
         data.addCentered("");

         for (BuildingInstance b : village.getBuildings()) {
            if (b.isBeingBuilt()) {
               String bldIcon = PanelHelper.resolveBuildingIcon(b);
               data.addCenteredBuildingNameWithIcons(PanelHelper.getBuildingTranslationKey(b), bldIcon, bldIcon, BuildingNameHelper.getServerFallbackName(b));
               if (data.displayLines().size() >= 7) {
                  break;
               }
            }
         }
      } else {
         data.addCentered(PanelHelper.t("panel.millenaire.no_construction"));
         data.addCentered("");
      }
   }

   static void addProjects3D(PanelContentGenerator.DisplayData data, Village village) {
      BuildingInstance activeBuild = null;

      for (BuildingInstance b : village.getBuildings()) {
         if (b.isBeingBuilt()) {
            activeBuild = b;
            break;
         }
      }

      Village.PendingProject pending = village.getPendingProject();
      if (activeBuild != null) {
         BuildingPlanSet goalSet = activeBuild.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(activeBuild.getPlanSetId()) : null;
         String goalIcon = goalSet != null && goalSet.icon() != null ? goalSet.icon() : "";
         data.addCenteredWithIcons(PanelHelper.t("panel.millenaire.projects_title"), goalIcon, goalIcon);
         data.addCentered("");
         data.addCenteredBuildingName(PanelHelper.getBuildingTranslationKey(activeBuild), BuildingNameHelper.getServerFallbackName(activeBuild));
         data.addCentered("");
         data.addCentered(PanelHelper.t("panel.millenaire.in_construction"));
      } else if (pending != null) {
         BuildingPlanSet goalSet = ModCultures.getBuildingPlanSet(pending.planSetId());
         String goalIcon = goalSet != null && goalSet.icon() != null ? goalSet.icon() : "";
         data.addCenteredWithIcons(PanelHelper.t("panel.millenaire.projects_title"), goalIcon, goalIcon);
         data.addCentered("");
         data.addCenteredTranslatable(PanelHelper.getPendingProjectKey(pending));
         data.addCentered("");
         data.addCentered(PanelHelper.t("panel.millenaire.awaiting_resources"));
      } else {
         data.addCentered("");
         data.addCentered("");
         data.addCentered(PanelHelper.t("panel.millenaire.goals_completed"));
      }
   }

   static void addResources3D(PanelContentGenerator.DisplayData data, Village village, @Nullable ServerLevel level) {
      PanelContentGenerator.ResourcePanelData rpd = PanelContentGenerator.collectResourcePanelData(village);
      data.addCenteredWithIcons(PanelHelper.t("panel.millenaire.resources_title"), "minecraft:chest", "minecraft:chest");
      if (rpd.projectNameKey() != null && !rpd.resources().isEmpty()) {
         if (rpd.resources().size() < 6) {
            data.addCentered("");
         }

         BuildingInstance townhall = village.getTownhall();
         BuildingInventory inv = townhall != null ? townhall.getInventory() : null;

         for (Entry<ResourceLocation, Integer> req : rpd.resources().entrySet()) {
            if (!req.getKey().getPath().startsWith("mock_")) {
               int cost = req.getValue();
               int has;
               String itemId;
               if (rpd.inProgress()) {
                  has = cost;
                  itemId = AnywoodHelper.isAnywood(req.getKey()) ? "minecraft:oak_log" : req.getKey().toString();
               } else if (AnywoodHelper.isAnywood(req.getKey())) {
                  has = inv != null && level != null ? Math.min(inv.getCountByTag(level, AnywoodHelper.LOGS_TAG), cost) : 0;
                  itemId = "minecraft:oak_log";
               } else {
                  has = 0;
                  if (inv != null && level != null) {
                     Item item = ItemHelper.resolve(req.getKey());
                     if (item != null) {
                        has = Math.min(inv.getCount(level, item), cost);
                     }
                  }

                  itemId = req.getKey().toString();
               }

               data.addLeftWithIcon(has + "/" + cost, itemId);
               if (data.displayLines().size() >= 8) {
                  break;
               }
            }
         }
      } else if (rpd.projectNameKey() != null) {
         data.addCentered("");
         data.addCentered(PanelHelper.t("panel.millenaire.no_resources_needed"));
      } else {
         data.addCentered("");
         data.addCentered(PanelHelper.t("panel.millenaire.no_project"));
      }
   }
}
