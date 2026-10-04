package org.millenaire.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.millenaire.network.InfoPanelContentPayload;
import org.millenaire.village.panel.PanelLine;

public class InfoPanelScreen extends AbstractMillenaireScreen {
   private static final int PARCHMENT_WIDTH = 256;
   private static final int PARCHMENT_HEIGHT = 220;
   private static final int LINES_PER_PAGE = computeMaxLinesPerPage(220);
   private static final int COLOR_DARK_BLUE = -16777046;
   private static final int COLOR_DARK_GREEN = -16733696;
   private static final int COLOR_DARK_RED = -5636096;
   private final InfoPanelContentPayload payload;
   private List<PanelLine> contentLines = List.of();
   private static final int BUTTON_AREA_HEIGHT = 50;

   public InfoPanelScreen(InfoPanelContentPayload payload) {
      super(Component.translatable("gui.millenaire.info_panel.title"));
      this.payload = payload;
   }

   public boolean isPauseScreen() {
      return false;
   }

   protected void init() {
      super.init();
      int parchX = (this.width - 256) / 2;
      int parchY = (this.height - 220) / 2;
      int contentX = parchX + 16;
      int buttonWidth = 220;
      int buttonY = parchY + 42;
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.millenaire.info_panel.help"), btn -> this.minecraft.setScreen(new HelpScreen(this)))
            .bounds(contentX, buttonY, buttonWidth, 20)
            .build()
      );
      buttonY += 22;
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.millenaire.info_panel.travel_book"), btn -> TravelBookNavHelper.openHome(this))
            .bounds(contentX, buttonY, buttonWidth, 20)
            .build()
      );
      buttonY += 24;
      this.buildContentLines(buttonY - parchY);
      int availableHeight = 220 - (buttonY - parchY) - 15;
      int linesPerPage = availableHeight / 11;
      this.totalPages = PanelRenderHelper.computePageCount(this.contentLines.size(), Math.max(1, linesPerPage));
      this.currentPage = 0;
      this.initPaginationButtons(parchX, parchY, 256, 220, 16);
      this.updatePaginationButtons();
   }

   private void buildContentLines(int buttonAreaHeight) {
      List<PanelLine> lines = new ArrayList<>();
      lines.add(PanelLine.separator());
      lines.add(PanelLine.colored(I18n.get("gui.millenaire.info_panel.cultures", new Object[0]), -16777046));
      lines.add(PanelLine.empty());

      for (InfoPanelContentPayload.CultureEntry entry : this.payload.cultures()) {
         String cultureName = I18n.get(entry.cultureNameKey(), new Object[0]);
         lines.add(PanelLine.text(I18n.get("gui.millenaire.info_panel.culture", new Object[]{cultureName})));
         String repLabelResolved = MillenaireScreenUtils.resolveReputationLabel(entry.reputationLabel());
         String repText = I18n.get("gui.millenaire.info_panel.reputation", new Object[]{repLabelResolved});
         int repColor;
         if (entry.reputation() > 0) {
            repColor = -16733696;
         } else if (entry.reputation() < 0) {
            repColor = -5636096;
         } else {
            repColor = -13421773;
         }

         lines.add(PanelLine.colored(repText, repColor));
         String langLevel = resolveLanguageLevel(entry.languageScore());
         lines.add(PanelLine.text(I18n.get("gui.millenaire.info_panel.language_level", new Object[]{langLevel})));
         lines.add(PanelLine.empty());
      }

      this.contentLines = lines;
   }

   private static String resolveLanguageLevel(int score) {
      if (score >= 500) {
         return I18n.get("gui.millenaire.info_panel.lang_fluent", new Object[0]);
      } else if (score >= 200) {
         return I18n.get("gui.millenaire.info_panel.lang_moderate", new Object[0]);
      } else {
         return score >= 100
            ? I18n.get("gui.millenaire.info_panel.lang_beginner", new Object[0])
            : I18n.get("gui.millenaire.info_panel.lang_minimal", new Object[0]);
      }
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, 1610612736);
      int parchX = (this.width - 256) / 2;
      int parchY = (this.height - 220) / 2;
      PanelRenderHelper.renderTexturedBackground(graphics, PanelRenderHelper.QUEST_TEXTURE, parchX, parchY, 256, 220);
      this.renderParchmentHeader(graphics, Component.translatable("gui.millenaire.info_panel.title"), parchX, parchY, 256);
      if (!this.contentLines.isEmpty()) {
         int contentX = parchX + 16;
         int contentY = parchY + 42 + 50;
         int availableHeight = 113;
         int linesPerPage = Math.max(1, availableHeight / 11);
         int startLine = this.currentPage * linesPerPage;
         PanelRenderHelper.renderPanelLines(graphics, this.font, this.contentLines, contentX, contentY, textWidth(256), startLine, linesPerPage);
      }

      this.renderPageCounter(graphics, parchX, parchY, 256, 220);
      super.render(graphics, mouseX, mouseY, partialTick);
   }
}
