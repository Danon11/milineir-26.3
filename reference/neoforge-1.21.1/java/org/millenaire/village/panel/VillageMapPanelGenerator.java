package org.millenaire.village.panel;

import java.util.ArrayList;
import java.util.List;
import org.millenaire.village.Village;

public final class VillageMapPanelGenerator {
   private VillageMapPanelGenerator() {
   }

   public static PanelContent generateVillageMap(Village village) {
      List<PanelLine> lines = new ArrayList<>();
      String title = village.getVillageName();
      lines.add(PanelLine.translatable("panel.millenaire.map_legend_title"));
      lines.add(PanelLine.text(""));
      lines.add(PanelLine.translatableColored("panel.millenaire.map_blue", 5592575));
      lines.add(PanelLine.translatableColored("panel.millenaire.map_purple", 16733695));
      lines.add(PanelLine.translatableColored("panel.millenaire.map_dark_blue", 170));
      lines.add(PanelLine.translatableColored("panel.millenaire.map_cyan", 5636095));
      lines.add(PanelLine.translatableColored("panel.millenaire.map_red", 11141120));
      lines.add(PanelLine.translatableColored("panel.millenaire.map_yellow", 13421568));
      lines.add(PanelLine.translatableColored("panel.millenaire.map_white", 0));
      return new PanelContent(PanelType.VILLAGE_MAP, title, lines);
   }

   static void addVillageMap3D(PanelContentGenerator.DisplayData data, Village village) {
      data.addCenteredWithIcons(PanelHelper.t("panel.millenaire.panel_type.village_map"), "minecraft:filled_map", "minecraft:filled_map");
      data.addCentered("");
      int nbBuildings = village.getBuildings().size();
      data.addCentered(nbBuildings + " " + PanelHelper.t("panel.millenaire.buildings"));
   }
}
