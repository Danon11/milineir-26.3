package org.millenaire.village.panel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.village.Village;

public final class MilitaryPanelGenerator {
   private MilitaryPanelGenerator() {
   }

   public static PanelContent generateMilitary(Village village, @Nullable ServerLevel level) {
      List<PanelLine> lines = new ArrayList<>();
      String titleKey = "panel.millenaire.title.military";
      String[] titleArgs = new String[]{village.getVillageName()};
      lines.add(PanelLine.translatable("panel.millenaire.village_at_peace"));
      lines.add(PanelLine.separator());
      double totalOffense = 0.0;
      double totalDefense = 0.0;
      int fighters = 0;
      List<PanelLine> fighterLines = new ArrayList<>();

      for (Entry<UUID, ResourceLocation> entry : village.getVillagerTypes().entrySet()) {
         VillagerType vType = ModCultures.getVillagerType(entry.getValue());
         if (vType != null) {
            boolean isDefender = vType.hasTag("helpInAttacks");
            boolean isRaider = vType.hasTag("isRaider");
            if (isDefender || isRaider) {
               fighters++;
               String roleKey = PanelHelper.resolveRoleKey(entry.getValue());
               String statusKey;
               if (isDefender && isRaider) {
                  statusKey = "panel.millenaire.fighter_status_both";
               } else if (isDefender) {
                  statusKey = "panel.millenaire.fighter_status_defender";
               } else {
                  statusKey = "panel.millenaire.fighter_status_raider";
               }

               String displayName = "???";
               List<PanelLine> statsLines = new ArrayList<>();
               if (level != null && level.getEntity(entry.getKey()) instanceof MillVillager mv) {
                  String first = mv.getFirstName();
                  String family = mv.getFamilyName();
                  if (first != null && !first.isEmpty()) {
                     displayName = first + (family != null && !family.isEmpty() ? " " + family : "");
                  }

                  statsLines.add(PanelLine.translatableWithArgsColored("panel.millenaire.health_value", 5592405, String.valueOf(Math.round(mv.getMaxHealth()))));
                  int armorValue = mv.getArmorValue();
                  statsLines.add(PanelLine.translatableWithArgsColored("panel.millenaire.armour_value", 5592405, String.valueOf(armorValue)));
                  ItemStack mainHand = mv.getMainHandItem();
                  if (!mainHand.isEmpty()) {
                     String weaponDescId = mainHand.getItem().getDescriptionId();
                     statsLines.add(PanelLine.translatableWithMixedArgsColored("panel.millenaire.weapons_value", 5592405, 1, weaponDescId));
                  }

                  totalOffense += mv.getAttributeValue(Attributes.ATTACK_DAMAGE);
                  totalDefense += armorValue;
               }

               fighterLines.add(PanelLine.translatableWithMixedArgs("panel.millenaire.fighter_name_role", 2, displayName, roleKey));
               fighterLines.add(PanelLine.translatableColored(statusKey, 170));
               fighterLines.addAll(statsLines);
               fighterLines.add(PanelLine.text(""));
            }
         }
      }

      lines.add(PanelLine.translatableWithArgs("panel.millenaire.offensive_value", String.valueOf(Math.round(totalOffense))));
      lines.add(PanelLine.translatableWithArgs("panel.millenaire.defensive_value", String.valueOf(Math.round(totalDefense))));
      lines.add(PanelLine.text(""));
      lines.add(PanelLine.translatableWithArgs("panel.millenaire.defenders_value", String.valueOf(fighters)));
      lines.add(PanelLine.text(""));
      if (fighters == 0) {
         lines.add(PanelLine.translatable("panel.millenaire.no_defenders"));
      } else {
         lines.addAll(fighterLines);
      }

      return new PanelContent(PanelType.MILITARY, titleKey, lines, true, titleArgs);
   }

   static void addMilitary3D(PanelContentGenerator.DisplayData data, Village village) {
      data.addCenteredWithIcons(PanelHelper.t("panel.millenaire.military_title"), "minecraft:iron_sword", "minecraft:iron_sword");
      data.addCentered("");
      data.addCentered(PanelHelper.t("panel.millenaire.village_at_peace"));
      int nbDefenders = 0;

      for (ResourceLocation typeId : village.getVillagerTypes().values()) {
         VillagerType vType = ModCultures.getVillagerType(typeId);
         if (vType != null && vType.tags() != null && vType.tags().contains("helpInAttacks")) {
            nbDefenders++;
         }
      }

      data.addCenteredWithIcons(PanelHelper.t("panel.millenaire.offensive_strength") + ": ?", "minecraft:iron_axe", "minecraft:iron_axe");
      data.addCenteredWithIcons(
         PanelHelper.t("panel.millenaire.defensive_strength") + ": " + nbDefenders, "minecraft:iron_chestplate", "minecraft:iron_chestplate"
      );
   }
}
