package org.millenaire.village.panel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.DisplayUtils;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.Gender;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.language.BuildingNameHelper;
import org.millenaire.village.Village;
import org.millenaire.village.VillageEvent;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageRelations;
import org.millenaire.village.VillageSavedData;

public final class VillageOverviewPanelGenerator {
   private static final int SUMMARY_CHRONICLE_LIMIT = 20;

   private VillageOverviewPanelGenerator() {
   }

   public static PanelContent generateSummary(Village village, @Nullable BuildingInstance building, @Nullable ServerLevel level) {
      List<PanelLine> lines = new ArrayList<>();
      String titleKey = "panel.millenaire.title.village_summary";
      String[] titleArgs = new String[]{village.getVillageName()};
      VillageType villageType = ModCultures.getVillageType(village.getVillageTypeId());
      if (villageType != null) {
         lines.add(PanelLine.translatableWithArgs("panel.millenaire.village_type_value", villageType.name()));
      }

      VillageOverviewPanelGenerator.PopulationCounts population = countPopulation(village);
      lines.add(PanelLine.translatableWithArgs("panel.millenaire.population_value", String.valueOf(population.total())));
      lines.add(
         PanelLine.translatableWithArgs(
            "panel.millenaire.adults_value", String.valueOf(population.adults()), String.valueOf(population.men()), String.valueOf(population.women())
         )
      );
      if (population.children() > 0) {
         lines.add(PanelLine.translatableWithArgs("panel.millenaire.children_value", String.valueOf(population.children())));
      }

      lines.add(PanelLine.separator());
      BuildingInstance currentProject = null;

      for (BuildingInstance b : village.getBuildings()) {
         if (b.isBeingBuilt()) {
            currentProject = b;
            break;
         }
      }

      if (currentProject != null) {
         String projectNative = BuildingNameHelper.getServerFallbackName(currentProject);
         if (currentProject.getStatus() == BuildingInstance.Status.UPGRADING) {
            lines.add(
               PanelLine.translatableWithMixedArgs(
                  "panel.millenaire.building_project_upgrading",
                  1,
                  "panel.millenaire.building_project",
                  projectNative,
                  String.valueOf(currentProject.getLevel())
               )
            );
         } else {
            lines.add(
               PanelLine.translatableWithMixedArgs("panel.millenaire.building_project_constructing", 1, "panel.millenaire.building_project", projectNative)
            );
         }

         lines.add(PanelLine.text(""));
         if (currentProject.getPlanSetId() != null && currentProject.getVariant() != null) {
            BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(currentProject.getPlanSetId());
            if (planSet != null) {
               BuildingPlanSet.LevelDef levelDef = planSet.getLevel(currentProject.getVariant(), currentProject.getLevel());
               if (levelDef != null && !levelDef.requiredResources().isEmpty()) {
                  lines.add(PanelLine.translatable("panel.millenaire.resources_needed"));
                  PanelContentGenerator.addResourceLines(lines, levelDef.requiredResources(), village, level);
               }
            }
         }
      } else {
         Village.PendingProject pending = village.getPendingProject();
         if (pending != null) {
            String pendingKey = PanelHelper.getPendingProjectKey(pending);
            lines.add(PanelLine.translatableWithMixedArgs("panel.millenaire.pending_project_value", 1, pendingKey));
            lines.add(PanelLine.translatable("panel.millenaire.awaiting_resources"));
         } else {
            lines.add(PanelLine.translatable("panel.millenaire.goals_completed"));
         }
      }

      lines.add(PanelLine.separator());
      lines.add(PanelLine.translatable("panel.millenaire.current_constructions"));
      boolean anyConstruction = false;

      for (BuildingInstance b : village.getBuildings()) {
         if (b.isBeingBuilt()) {
            anyConstruction = true;
            String nameArg = BuildingNameHelper.getServerFallbackName(b);
            PanelHelper.DirectionInfo dir = PanelHelper.computeDirectionInfo(village.getCenter(), b.getOrigin());
            if (b.getStatus() == BuildingInstance.Status.UPGRADING) {
               if (dir.atCenter()) {
                  lines.add(
                     PanelLine.translatableWithMixedArgs("panel.millenaire.construction_line_upgrading_center", 0, nameArg, String.valueOf(b.getLevel()))
                  );
               } else {
                  lines.add(
                     PanelLine.translatableWithMixedArgs(
                        "panel.millenaire.construction_line_upgrading_dir",
                        8,
                        nameArg,
                        String.valueOf(b.getLevel()),
                        String.valueOf(dir.distance()),
                        dir.cardinalKey()
                     )
                  );
               }
            } else if (dir.atCenter()) {
               lines.add(PanelLine.translatableWithMixedArgs("panel.millenaire.construction_line_building_center", 0, nameArg));
            } else {
               lines.add(
                  PanelLine.translatableWithMixedArgs(
                     "panel.millenaire.construction_line_building_dir", 4, nameArg, String.valueOf(dir.distance()), dir.cardinalKey()
                  )
               );
            }
         }
      }

      if (!anyConstruction) {
         lines.add(PanelLine.translatable("panel.millenaire.no_construction"));
      }

      if (!village.getRelations().isEmpty()) {
         lines.add(PanelLine.separator());
         lines.add(PanelLine.translatable("panel.millenaire.diplomacy"));
         VillageManager vm = level != null ? VillageSavedData.get(level).getVillageManager() : null;

         for (Entry<VillageId, Integer> relEntry : village.getRelations().entrySet()) {
            Village other = vm != null ? vm.getVillage(relEntry.getKey()) : null;
            if (other != null
               && (village.getParentVillageId() == null || !village.getParentVillageId().equals(other.getId()))
               && (other.getParentVillageId() == null || !other.getParentVillageId().equals(village.getId()))) {
               String otherName = other.getVillageName() != null ? other.getVillageName() : other.getVillageTypeId().getPath();
               int rel = relEntry.getValue();
               String relKey = VillageRelations.getRelationKey(rel);
               String diplomacyKey;
               if (rel >= 50) {
                  diplomacyKey = "panel.millenaire.diplomacy_friendly";
               } else if (rel >= 0) {
                  diplomacyKey = "panel.millenaire.diplomacy_neutral";
               } else if (rel > -50) {
                  diplomacyKey = "panel.millenaire.diplomacy_cold";
               } else {
                  diplomacyKey = "panel.millenaire.diplomacy_hostile";
               }

               lines.add(PanelLine.translatableWithMixedArgs(diplomacyKey, 2, otherName, relKey));
            }
         }
      }

      appendChronicleLines(lines, village);
      return new PanelContent(PanelType.VILLAGE_SUMMARY, titleKey, lines, true, titleArgs);
   }

   public static PanelContent generatePopulation(Village village, @Nullable ServerLevel level) {
      List<PanelLine> lines = new ArrayList<>();
      String titleKey = "panel.millenaire.title.population";
      String[] titleArgs = new String[]{village.getVillageName()};
      Map<UUID, ResourceLocation> villagerTypes = village.getVillagerTypes();
      if (villagerTypes.isEmpty()) {
         lines.add(PanelLine.translatable("panel.millenaire.no_inhabitants"));
         return new PanelContent(PanelType.POPULATION, titleKey, lines, true, titleArgs);
      }

      for (Entry<UUID, ResourceLocation> entry : villagerTypes.entrySet()) {
         UUID uuid = entry.getKey();
         ResourceLocation typeId = entry.getValue();
         String roleKey = PanelHelper.resolveRoleKey(typeId);
         String displayName = "???";
         boolean entityLoaded = false;
         String goalLabel = null;
         boolean isMissing = false;
         boolean isAbsent = false;
         if (level != null) {
            if (level.getEntity(uuid) instanceof MillVillager mv) {
               entityLoaded = true;
               String first = mv.getFirstName();
               String family = mv.getFamilyName();
               if (first != null && !first.isEmpty()) {
                  displayName = first + (family != null && !family.isEmpty() ? " " + family : "");
               }

               goalLabel = mv.getGoalLabel();
            } else {
               int missing = village.getMissingCount(uuid);
               if (missing >= 3) {
                  isMissing = true;
               } else {
                  isAbsent = true;
               }
            }
         }

         VillagerType vtype = ModCultures.getVillagerType(typeId);
         String iconItem = vtype != null && vtype.icon() != null ? vtype.icon() : "";
         PanelLine.PanelNavTarget nav = PanelHelper.villagerNavTarget(typeId);
         String nameText = "§0" + displayName;
         if (!iconItem.isEmpty()) {
            if (nav != null) {
               lines.add(PanelLine.clickableWithIcon(nameText, "", iconItem, nav));
            } else {
               lines.add(PanelLine.withIcon(nameText, "", iconItem));
            }
         } else if (nav != null) {
            lines.add(PanelLine.clickableText(nameText, nav));
         } else {
            lines.add(PanelLine.text(nameText));
         }

         if (isMissing) {
            lines.add(PanelLine.translatableWithMixedArgs("panel.millenaire.missing_villager_status", 1, roleKey));
         } else if (isAbsent) {
            lines.add(PanelLine.translatableWithMixedArgs("panel.millenaire.absent_status", 1, roleKey));
         } else if (goalLabel != null && !goalLabel.isEmpty()) {
            int argMask = DisplayUtils.isGoalTranslationKey(goalLabel) ? 3 : 1;
            lines.add(PanelLine.translatableWithMixedArgs("panel.millenaire.villager_goal", argMask, roleKey, goalLabel));
         } else {
            lines.add(PanelLine.translatableWithMixedArgs("panel.millenaire.role_and_activity", 1, roleKey, ""));
         }
      }

      return new PanelContent(PanelType.POPULATION, titleKey, lines, true, titleArgs);
   }

   public static void appendChronicleLines(List<PanelLine> lines, Village village) {
      List<VillageEvent> events = village.getChronicle();
      if (!events.isEmpty()) {
         lines.add(PanelLine.separator());
         lines.add(PanelLine.translatable("gui.millenaire.book.section.chronicle"));
         int start = Math.max(0, events.size() - 20);

         for (int i = events.size() - 1; i >= start; i--) {
            VillageEvent event = events.get(i);
            long dayNumber = event.gameTime() / 24000L + 1L;
            String dayStr = String.valueOf(dayNumber);
            if (event.param2() != null) {
               lines.add(PanelLine.translatableWithArgs(event.type().i18nKey(), dayStr, event.param1(), event.param2()));
            } else {
               lines.add(PanelLine.translatableWithArgs(event.type().i18nKey(), dayStr, event.param1()));
            }
         }
      }
   }

   public static PanelContent generateChronicle(Village village) {
      List<PanelLine> lines = new ArrayList<>();
      List<VillageEvent> events = village.getChronicle();
      if (events.isEmpty()) {
         lines.add(PanelLine.translatable("chronicle.millenaire.empty"));
      } else {
         for (int i = events.size() - 1; i >= 0; i--) {
            VillageEvent event = events.get(i);
            long dayNumber = event.gameTime() / 24000L + 1L;
            String dayStr = String.valueOf(dayNumber);
            if (event.param2() != null) {
               lines.add(PanelLine.translatableWithArgs(event.type().i18nKey(), dayStr, event.param1(), event.param2()));
            } else {
               lines.add(PanelLine.translatableWithArgs(event.type().i18nKey(), dayStr, event.param1()));
            }
         }
      }

      return new PanelContent(PanelType.CHRONICLE, "panel.millenaire.panel_type.chronicle", lines, true, null);
   }

   private static VillageOverviewPanelGenerator.PopulationCounts countPopulation(Village village) {
      Map<UUID, ResourceLocation> villagerTypes = village.getVillagerTypes();
      int nbMen = 0;
      int nbWomen = 0;
      int nbChildren = 0;

      for (ResourceLocation typeId : villagerTypes.values()) {
         VillagerType vType = ModCultures.getVillagerType(typeId);
         if (vType != null) {
            if (vType.isChild()) {
               nbChildren++;
            } else if (vType.gender() == Gender.FEMALE) {
               nbWomen++;
            } else {
               nbMen++;
            }
         }
      }

      return new VillageOverviewPanelGenerator.PopulationCounts(villagerTypes.size(), nbMen, nbWomen, nbChildren);
   }

   static void addSummary3D(PanelContentGenerator.DisplayData data, Village village) {
      String banner = "minecraft:white_banner";
      data.addCenteredWithIcons(village.getVillageName(), banner, banner);
      VillageType villageType = ModCultures.getVillageType(village.getVillageTypeId());
      if (villageType != null) {
         data.addCentered(villageType.name());
      }

      data.addCentered("");
      data.addCentered(PanelHelper.t("panel.millenaire.population") + ": " + village.getVillagerUuids().size());
      data.addCentered("");
   }

   static void addPopulation3D(PanelContentGenerator.DisplayData data, Village village) {
      data.addCenteredWithIcons(PanelHelper.t("panel.millenaire.population_title"), "minecraft:blue_orchid", "minecraft:pink_tulip");
      data.addCentered("");
      VillageOverviewPanelGenerator.PopulationCounts population = countPopulation(village);
      data.addCentered(PanelHelper.t("panel.millenaire.adults") + ": " + population.adults() + " (" + population.men() + "M / " + population.women() + "F)");
      if (population.children() > 0) {
         data.addCentered(PanelHelper.t("panel.millenaire.children_section") + ": " + population.children());
      }
   }

   private record PopulationCounts(int total, int men, int women, int children) {
      int adults() {
         return this.men + this.women;
      }
   }
}
