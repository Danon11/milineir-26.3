package org.millenaire.village.panel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.millenaire.DisplayUtils;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.commerce.ShopProfile;
import org.millenaire.commerce.ShopProfileLoader;
import org.millenaire.commerce.TradeGood;
import org.millenaire.commerce.TradeGoodsLoader;
import org.millenaire.culture.Gender;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.language.BuildingNameHelper;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;

public final class BuildingPanelGenerator {
   private BuildingPanelGenerator() {
   }

   public static PanelContent generateBuildingDefault(Village village, @Nullable BuildingInstance building, @Nullable ServerLevel level) {
      String titleKey = building != null ? BuildingNameHelper.getServerFallbackName(building) : village.getVillageName();
      List<PanelLine> lines = new ArrayList<>();
      if (building == null) {
         return new PanelContent(PanelType.BUILDING_DEFAULT, titleKey, lines);
      }

      addResidentsSection(lines, village, building, level);
      addTradedGoodsSection(lines, village, building);
      addStoredResourcesSection(lines, building, level);
      return new PanelContent(PanelType.BUILDING_DEFAULT, titleKey, lines, false, null);
   }

   private static void addResidentsSection(List<PanelLine> lines, Village village, BuildingInstance building, @Nullable ServerLevel level) {
      List<BuildingPanelGenerator.ResidentInfo> residents = new ArrayList<>();

      for (Entry<UUID, ResourceLocation> entry : village.getVillagerTypes().entrySet()) {
         UUID uuid = entry.getKey();
         if (building.getId().equals(village.getVillagerHome(uuid))) {
            ResourceLocation typeId = entry.getValue();
            VillagerType vType = ModCultures.getVillagerType(typeId);
            String roleKey = PanelHelper.resolveRoleKey(typeId);
            String displayName = "???";
            if (level != null && level.getEntity(uuid) instanceof MillVillager mv) {
               String first = mv.getFirstName();
               String family = mv.getFamilyName();
               if (first != null && !first.isEmpty()) {
                  displayName = first + (family != null && !family.isEmpty() ? " " + family : "");
               }
            }

            Gender gender = vType != null ? vType.gender() : Gender.MALE;
            boolean isChild = vType != null && vType.isChild();
            residents.add(new BuildingPanelGenerator.ResidentInfo(displayName, roleKey, gender, isChild, typeId));
         }
      }

      if (!residents.isEmpty()) {
         List<BuildingPanelGenerator.ResidentInfo> adults = new ArrayList<>();
         List<BuildingPanelGenerator.ResidentInfo> children = new ArrayList<>();

         for (BuildingPanelGenerator.ResidentInfo r : residents) {
            if (r.isChild()) {
               children.add(r);
            } else {
               adults.add(r);
            }
         }

         List<BuildingPanelGenerator.ResidentInfo> men = new ArrayList<>();
         List<BuildingPanelGenerator.ResidentInfo> women = new ArrayList<>();

         for (BuildingPanelGenerator.ResidentInfo r : adults) {
            if (r.gender() == Gender.FEMALE) {
               women.add(r);
            } else {
               men.add(r);
            }
         }

         BuildingPlanSet planSet = building.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(building.getPlanSetId()) : null;
         boolean expectsFemale = planSet != null && !planSet.femaleResidents().isEmpty();
         boolean expectsMale = planSet != null && !planSet.maleResidents().isEmpty();
         if (men.size() == 1 && women.size() == 1) {
            BuildingPanelGenerator.ResidentInfo wife = women.get(0);
            BuildingPanelGenerator.ResidentInfo husband = men.get(0);
            addResidentTranslatableLine(lines, "panel.millenaire.woman_resident", wife.name(), wife.roleKey(), wife.typeId());
            addResidentTranslatableLine(lines, "panel.millenaire.man_resident", husband.name(), husband.roleKey(), husband.typeId());
         } else if (men.size() == 1 && women.isEmpty() && expectsFemale) {
            BuildingPanelGenerator.ResidentInfo husband = men.get(0);
            addResidentTranslatableLine(lines, "panel.millenaire.man_resident", husband.name(), husband.roleKey(), husband.typeId());
            lines.add(PanelLine.translatable("panel.millenaire.bachelor"));
         } else if (women.size() == 1 && men.isEmpty() && expectsMale) {
            BuildingPanelGenerator.ResidentInfo wife = women.get(0);
            addResidentTranslatableLine(lines, "panel.millenaire.woman_resident", wife.name(), wife.roleKey(), wife.typeId());
            lines.add(PanelLine.translatable("panel.millenaire.spinster"));
         } else {
            for (BuildingPanelGenerator.ResidentInfo r : adults) {
               addResidentTranslatableLine(lines, "panel.millenaire.resident", r.name(), r.roleKey(), r.typeId());
            }
         }

         if (!children.isEmpty()) {
            lines.add(PanelLine.text(""));
            lines.add(PanelLine.translatable("panel.millenaire.children_section"));

            for (BuildingPanelGenerator.ResidentInfo child : children) {
               lines.add(PanelLine.text("  §0" + child.name()));
            }
         }
      }
   }

   private static void addResidentTranslatableLine(List<PanelLine> lines, String key, String name, String roleKey, ResourceLocation typeId) {
      PanelLine.PanelNavTarget nav = PanelHelper.villagerNavTarget(typeId);
      if (nav != null) {
         lines.add(PanelLine.clickableTranslatableWithMixedArgs(key, nav, 2, name, roleKey));
      } else {
         lines.add(PanelLine.translatableWithMixedArgs(key, 2, name, roleKey));
      }
   }

   private static void addTradedGoodsSection(List<PanelLine> lines, Village village, BuildingInstance building) {
      BuildingPlan plan = ModCultures.getBuildingPlan(building.getPlanId());
      String shopId = plan != null ? plan.shopId() : null;
      ResourceLocation cultureId = village.getCultureId();
      if (shopId != null && cultureId != null) {
         ShopProfile profile = ShopProfileLoader.getProfile(cultureId, shopId);
         if (profile != null) {
            String cultureKey = cultureId.getPath();
            if (!profile.sells().isEmpty()) {
               boolean headerAdded = false;

               for (String goodId : profile.sells()) {
                  TradeGood good = TradeGoodsLoader.getGoodById(cultureId, goodId);
                  if (good != null && good.canSell()) {
                     Item item = good.resolveItem();
                     if (item != null) {
                        if (!headerAdded) {
                           lines.add(PanelLine.separator());
                           lines.add(PanelLine.translatable("panel.millenaire.sold_here"));
                           headerAdded = true;
                        }

                        addTradeGoodLine(lines, good, item, cultureKey);
                     }
                  }
               }
            }

            if (!profile.buys().isEmpty()) {
               boolean headerAdded = false;

               for (String goodId : profile.buys()) {
                  TradeGood good = TradeGoodsLoader.getGoodById(cultureId, goodId);
                  if (good != null && good.canBuy()) {
                     Item item = good.resolveItem();
                     if (item != null) {
                        if (!headerAdded) {
                           lines.add(PanelLine.separator());
                           lines.add(PanelLine.translatable("panel.millenaire.bought_here"));
                           headerAdded = true;
                        }

                        addTradeGoodLine(lines, good, item, cultureKey);
                     }
                  }
               }
            }
         }
      }
   }

   private static void addTradeGoodLine(List<PanelLine> lines, TradeGood good, Item item, String cultureKey) {
      String descId = item.getDescriptionId();
      String iconId = BuiltInRegistries.ITEM.getKey(item).toString();
      PanelLine.PanelNavTarget nav = PanelHelper.tradeGoodNavTarget(good, cultureKey);
      if (nav != null) {
         lines.add(PanelLine.clickableWithIconTranslatable(descId, "", iconId, nav, 5592405));
      } else {
         lines.add(PanelLine.withIconTranslatable(descId, "", iconId, 5592405));
      }
   }

   private static void addStoredResourcesSection(List<PanelLine> lines, BuildingInstance building, @Nullable ServerLevel level) {
      if (building.getInventory() != null && level != null) {
         BuildingInventory inv = building.getInventory();
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

         lines.add(PanelLine.separator());
         lines.add(PanelLine.translatable("panel.millenaire.stored_resources"));
         lines.add(PanelLine.text(""));
         if (sorted.isEmpty()) {
            lines.add(PanelLine.translatable("panel.millenaire.no_stored_resources"));
         } else {
            for (Entry<String, InvEntry> sortedEntry : sorted.entrySet()) {
               InvEntry entry = sortedEntry.getValue();
               lines.add(PanelLine.withIconTranslatable(entry.descId, "§1" + entry.count, entry.itemId, 0));
            }
         }
      }
   }

   public static PanelContent generateArchives(Village village, @Nullable BuildingInstance building, @Nullable ServerLevel level, int signIndex) {
      List<PanelLine> lines = new ArrayList<>();
      String titleKey = "panel.millenaire.title.archives";
      List<Entry<UUID, VillagerRecord>> records = new ArrayList<>(village.getVillagerRecords().entrySet());
      if (signIndex >= 0 && signIndex < records.size()) {
         Entry<UUID, VillagerRecord> entry = records.get(signIndex);
         UUID uuid = entry.getKey();
         VillagerRecord record = entry.getValue();
         String firstName = record.getFirstName() != null ? record.getFirstName() : "???";
         String familyName = record.getFamilyName() != null ? record.getFamilyName() : "";
         String fullName = firstName + (familyName.isEmpty() ? "" : " " + familyName);
         VillagerType vtype = ModCultures.getVillagerType(record.getVillagerTypeId());
         String iconItem = vtype != null && vtype.icon() != null ? vtype.icon() : "";
         if (!iconItem.isEmpty()) {
            lines.add(PanelLine.withIcon("§1" + fullName, "", iconItem));
         } else {
            lines.add(PanelLine.text("§1" + fullName));
         }

         lines.add(PanelLine.translatableColored(PanelHelper.resolveRoleKey(record.getVillagerTypeId()), 0));
         lines.add(PanelLine.text(""));
         String mother = record.getMothersName();
         if (mother != null && !mother.isEmpty()) {
            lines.add(PanelLine.translatableWithArgs("panel.millenaire.mother_value", mother));
         }

         String father = record.getFathersName();
         if (father != null && !father.isEmpty()) {
            lines.add(PanelLine.translatableWithArgs("panel.millenaire.father_value", father));
         }

         String spouse = record.getSpousesName();
         if (spouse != null && !spouse.isEmpty()) {
            lines.add(PanelLine.translatableWithArgs("panel.millenaire.spouse_value", spouse));
         }

         lines.add(PanelLine.text(""));
         MillVillager villager = null;
         if (level != null && level.getEntity(uuid) instanceof MillVillager mv) {
            villager = mv;
         }

         lines.add(PanelLine.text(""));
         if (villager == null) {
            if (record.isKilled()) {
               lines.add(PanelLine.translatable("panel.millenaire.dead"));
            } else {
               lines.add(PanelLine.translatable("panel.millenaire.missing"));
            }
         } else {
            String occupation = "";
            String goalLabel = villager.getGoalLabel();
            if (goalLabel != null && !goalLabel.isEmpty()) {
               occupation = goalLabel;
            }

            if (DisplayUtils.isGoalTranslationKey(occupation)) {
               lines.add(PanelLine.translatableWithMixedArgs("panel.millenaire.occupation_value", 1, occupation));
            } else {
               lines.add(PanelLine.translatableWithArgs("panel.millenaire.occupation_value", occupation));
            }
         }

         return new PanelContent(PanelType.ARCHIVES, titleKey, lines, true, null);
      } else {
         lines.add(PanelLine.translatableWithArgs("panel.millenaire.reserved_for_future", String.valueOf(signIndex + 1)));
         return new PanelContent(PanelType.ARCHIVES, titleKey, lines, true, null);
      }
   }

   static void addBuildingDefault3D(PanelContentGenerator.DisplayData data, Village village, BuildingInstance building, @Nullable ServerLevel level) {
      String bldIcon = PanelHelper.resolveBuildingIcon(building);
      data.addCenteredBuildingNameWithIcons(
         PanelHelper.getBuildingTranslationKey(building), bldIcon, bldIcon, BuildingNameHelper.getServerFallbackName(building)
      );
      data.addCentered("");
      MillVillager wife = null;
      MillVillager husband = null;
      String wifeIcon = "";
      String husbandIcon = "";
      int nbMaleAdults = 0;
      int nbFemaleAdults = 0;
      int nbResidents = 0;
      List<Entry<UUID, ResourceLocation>> residents = new ArrayList<>();

      for (Entry<UUID, ResourceLocation> entry : village.getVillagerTypes().entrySet()) {
         if (building.getId().equals(village.getVillagerHome(entry.getKey()))) {
            nbResidents++;
            residents.add(entry);
            VillagerType vtype = ModCultures.getVillagerType(entry.getValue());
            if (vtype != null && !vtype.isChild()) {
               String icon = vtype.icon() != null ? vtype.icon() : "";
               MillVillager mv = level != null ? PanelHelper.findVillager(level, entry.getKey()) : null;
               if (vtype.gender() == Gender.FEMALE) {
                  nbFemaleAdults++;
                  if (wife == null) {
                     wife = mv;
                     wifeIcon = icon;
                  }
               } else {
                  nbMaleAdults++;
                  if (husband == null) {
                     husband = mv;
                     husbandIcon = icon;
                  }
               }
            }
         }
      }

      if (nbResidents != 0) {
         if (nbMaleAdults <= 1 && nbFemaleAdults <= 1 && (wife != null || husband != null)) {
            if (wife != null) {
               String name = wife.getFirstName() != null ? wife.getFirstName() : PanelHelper.resolveRoleName(PanelHelper.findTypeId(village, wife));
               data.addCenteredWithIcons(name, wifeIcon, wifeIcon);
            }

            if (husband != null) {
               String name = husband.getFirstName() != null ? husband.getFirstName() : PanelHelper.resolveRoleName(PanelHelper.findTypeId(village, husband));
               data.addCenteredWithIcons(name, husbandIcon, husbandIcon);
            }

            if (husband != null && husband.getFamilyName() != null) {
               data.addCentered(husband.getFamilyName());
            } else if (wife != null && wife.getFamilyName() != null) {
               data.addCentered(wife.getFamilyName());
            }
         } else {
            for (Entry<UUID, ResourceLocation> resEntry : residents) {
               VillagerType vtype = ModCultures.getVillagerType(resEntry.getValue());
               String icon = vtype != null && vtype.icon() != null ? vtype.icon() : "";
               MillVillager mv = level != null ? PanelHelper.findVillager(level, resEntry.getKey()) : null;
               String name = mv != null && mv.getFirstName() != null ? mv.getFirstName() : PanelHelper.resolveRoleName(resEntry.getValue());
               data.addCenteredWithIcons(name, icon, icon);
               if (data.displayLines().size() >= 7) {
                  break;
               }
            }
         }
      }
   }

   static void addArchives3D(PanelContentGenerator.DisplayData data, Village village, BuildingInstance building, ServerLevel level, int signIndex) {
      int idx = 0;

      for (Entry<UUID, ResourceLocation> entry : village.getVillagerTypes().entrySet()) {
         if (idx == signIndex) {
            VillagerType vtype = ModCultures.getVillagerType(entry.getValue());
            String icon = vtype != null && vtype.icon() != null ? vtype.icon() : "";
            if (level.getEntity(entry.getKey()) instanceof MillVillager mv && mv.getFirstName() != null) {
               data.addCenteredWithIcons(mv.getFirstName(), icon, icon);
               if (mv.getFamilyName() != null) {
                  data.addCentered(mv.getFamilyName());
               }

               data.addCentered("");
               data.addCentered(PanelHelper.computeDirection(building.getOrigin(), mv.blockPosition()));
               String goalLabel = mv.getGoalLabel();
               if (goalLabel == null || goalLabel.isEmpty()) {
                  data.addCentered(PanelHelper.resolveRoleName(entry.getValue()));
               } else if (DisplayUtils.isGoalTranslationKey(goalLabel)) {
                  data.addCenteredTranslatable(goalLabel);
               } else {
                  data.addCentered(goalLabel);
               }
               break;
            }

            data.addCenteredWithIcons(PanelHelper.resolveRoleName(entry.getValue()), icon, icon);
            data.addCentered("");
            data.addCentered(PanelHelper.t("panel.millenaire.missing"));
            break;
         }

         idx++;
      }

      if (data.displayLines().isEmpty()) {
         data.addCentered(PanelHelper.t("panel.millenaire.reserved_for_future", ""));
      }
   }

   record ResidentInfo(String name, String roleKey, Gender gender, boolean isChild, ResourceLocation typeId) {
   }
}
