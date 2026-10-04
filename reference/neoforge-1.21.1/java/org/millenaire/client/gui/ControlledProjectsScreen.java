package org.millenaire.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.network.BuildingProjectCancelRequestPayload;
import org.millenaire.network.BuildingUpgradeToggleRequestPayload;
import org.millenaire.network.ControlledProjectsPayload;

public class ControlledProjectsScreen extends AbstractMillenaireScreen {
   private static final int PARCHMENT_WIDTH = 204;
   private static final int PARCHMENT_HEIGHT = 220;
   private static final int ROW_HEIGHT = 44;
   private static final int TOGGLE_WIDTH = 80;
   private static final int TOGGLE_HEIGHT = 14;
   private final ControlledProjectsPayload data;
   private final List<Button> pageButtons = new ArrayList<>();
   private int rowsPerPage;
   private int rowCount;

   public ControlledProjectsScreen(ControlledProjectsPayload data) {
      super(Component.translatable("gui.millenaire.controlled_projects.title"));
      this.data = data;
   }

   protected void init() {
      super.init();
      int parchY = (this.height - 220) / 2;
      int parchX = (this.width - 204) / 2;
      int contentStartY = parchY + 42 + (this.data.villageName().isEmpty() ? 0 : 9 + 4);
      int contentEndY = parchY + 220 - 15;
      int availableHeight = contentEndY - contentStartY;
      this.rowsPerPage = Math.max(1, availableHeight / 44);
      this.rowCount = this.data.entries().size() + (this.data.pendingPlanName().isEmpty() ? 0 : 1);
      this.totalPages = this.rowCount == 0 ? 1 : (this.rowCount + this.rowsPerPage - 1) / this.rowsPerPage;
      this.currentPage = 0;
      this.initPaginationButtons(parchX, parchY, 204, 220, 16);
      this.rebuildPageButtons();
      this.updatePaginationButtons();
   }

   protected void onPageChanged() {
      super.onPageChanged();
      this.rebuildPageButtons();
   }

   private void rebuildPageButtons() {
      for (Button btn : this.pageButtons) {
         this.removeWidget(btn);
      }

      this.pageButtons.clear();
      int parchX = (this.width - 204) / 2;
      int parchY = (this.height - 220) / 2;
      int contentX = parchX + 16;
      int subtitleOffset = this.data.villageName().isEmpty() ? 0 : 9 + 4;
      int rowY = parchY + 42 + subtitleOffset;
      int startIdx = this.currentPage * this.rowsPerPage;
      int endIdx = Math.min(startIdx + this.rowsPerPage, this.rowCount);
      boolean hasPending = !this.data.pendingPlanName().isEmpty();

      for (int i = startIdx; i < endIdx; i++) {
         int btnY = rowY + 2 * (9 + 1) + 2;
         int btnX = contentX + textWidth(204) - 80;
         if (hasPending && i == 0) {
            Button cancelBtn = Button.builder(
                  Component.translatable("gui.millenaire.controlled_projects.cancel"),
                  b -> PacketDistributor.sendToServer(new BuildingProjectCancelRequestPayload(this.data.villageUuid()), new CustomPacketPayload[0])
               )
               .bounds(btnX, btnY, 80, 14)
               .build();
            this.addRenderableWidget(cancelBtn);
            this.pageButtons.add(cancelBtn);
         } else {
            int entryIdx = hasPending ? i - 1 : i;
            ControlledProjectsPayload.ProjectEntry entry = this.data.entries().get(entryIdx);
            if (entry.maxLevel() > 1 && entry.currentLevel() < entry.maxLevel() - 1) {
               String labelKey = entry.upgradesAllowed() ? "gui.millenaire.controlled_projects.forbid" : "gui.millenaire.controlled_projects.allow";
               boolean newAllow = !entry.upgradesAllowed();
               Button toggle = Button.builder(
                     Component.translatable(labelKey),
                     b -> PacketDistributor.sendToServer(
                        new BuildingUpgradeToggleRequestPayload(this.data.villageUuid(), entry.buildingId(), newAllow), new CustomPacketPayload[0]
                     )
                  )
                  .bounds(btnX, btnY, 80, 14)
                  .build();
               this.addRenderableWidget(toggle);
               this.pageButtons.add(toggle);
            }
         }

         rowY += 44;
      }
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, 1610612736);
      int parchX = (this.width - 204) / 2;
      int parchY = (this.height - 220) / 2;
      PanelRenderHelper.renderTexturedBackground(graphics, PanelRenderHelper.PANEL_TEXTURE, parchX, parchY, 204, 220);
      int y = this.renderParchmentHeader(graphics, this.getTitle(), parchX, parchY, 204);
      if (!this.data.villageName().isEmpty()) {
         int textX = parchX + 16;
         graphics.drawString(this.font, Component.literal(this.data.villageName()), textX, y, 5914656, false);
         y += 9 + 4;
      }

      if (this.rowCount == 0) {
         graphics.drawString(this.font, Component.translatable("gui.millenaire.controlled_projects.empty"), parchX + 16, y, 5914656, false);
      } else {
         this.renderRows(graphics, parchX, y);
      }

      this.renderPageCounter(graphics, parchX, parchY, 204, 220);
      super.render(graphics, mouseX, mouseY, partialTick);
   }

   private void renderRows(GuiGraphics graphics, int parchX, int startY) {
      int contentX = parchX + 16;
      int rowY = startY;
      int startIdx = this.currentPage * this.rowsPerPage;
      int endIdx = Math.min(startIdx + this.rowsPerPage, this.rowCount);
      boolean hasPending = !this.data.pendingPlanName().isEmpty();

      for (int i = startIdx; i < endIdx; i++) {
         if (hasPending && i == 0) {
            graphics.drawString(this.font, Component.translatable("gui.millenaire.controlled_projects.pending_label"), contentX, rowY, 6955040, false);
            graphics.drawString(this.font, Component.literal(this.data.pendingPlanName()), contentX, rowY + 9 + 1, 4202512, false);
         } else {
            int entryIdx = hasPending ? i - 1 : i;
            ControlledProjectsPayload.ProjectEntry entry = this.data.entries().get(entryIdx);
            graphics.drawString(this.font, Component.literal(entry.displayName()), contentX, rowY, 4202512, false);
            String levelStr = Component.translatable(
                  "gui.millenaire.controlled_projects.level", new Object[]{String.valueOf(entry.currentLevel() + 1), String.valueOf(entry.maxLevel())}
               )
               .getString();
            String suffix = entry.distanceLabel().isEmpty() ? levelStr : levelStr + " — " + entry.distanceLabel();
            graphics.drawString(this.font, Component.literal(suffix), contentX, rowY + 9 + 1, 6963232, false);
         }

         rowY += 44;
      }
   }

   public boolean isPauseScreen() {
      return false;
   }
}
