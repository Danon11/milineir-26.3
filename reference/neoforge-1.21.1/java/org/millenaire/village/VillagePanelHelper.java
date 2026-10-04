package org.millenaire.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.block.VillagePanelBlockEntity;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.SpecialPoint;
import org.millenaire.village.panel.PanelContentGenerator;
import org.millenaire.village.panel.PanelPlacer;
import org.millenaire.village.panel.PanelType;

public final class VillagePanelHelper {
   private VillagePanelHelper() {
   }

   public static void updatePanelDisplayLines(ServerLevel level, Village village) {
      for (BuildingInstance building : village.getBuildings()) {
         if (building.isOperational()) {
            for (SpecialPoint sp : building.getResolvedPoints()) {
               if (sp.isType("signPos")) {
                  BlockPos panelPos = sp.pos();
                  if (level.isLoaded(panelPos)) {
                     boolean playerNearby = false;

                     for (Player player : level.players()) {
                        if (player.blockPosition().closerThan(panelPos, 48.0)) {
                           playerNearby = true;
                           break;
                        }
                     }

                     if (playerNearby) {
                        if (level.getBlockState(panelPos).isAir()) {
                           PanelPlacer.recreatePanel(level, village, building, panelPos);
                        }

                        if (level.getBlockEntity(panelPos) instanceof VillagePanelBlockEntity panel && panel.getPanelType() != PanelType.HALL_OF_FAME) {
                           PanelContentGenerator.DisplayData displayData = PanelContentGenerator.generateDisplayLines(
                              panel.getPanelType(), village, building, level, panel.getSignIndex()
                           );
                           if (!displayData.displayLines().equals(panel.getDisplayLines())) {
                              panel.updateDisplayLines(displayData.displayLines());
                              BlockState state = level.getBlockState(panelPos);
                              level.sendBlockUpdated(panelPos, state, state, 3);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }
}
