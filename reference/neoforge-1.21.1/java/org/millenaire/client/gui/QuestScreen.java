package org.millenaire.client.gui;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.client.ClientQuestCache;
import org.millenaire.network.QuestCompleteStepPayload;
import org.millenaire.network.QuestOpenPayload;
import org.millenaire.network.QuestRefusePayload;
import org.millenaire.village.panel.PanelLine;
import org.slf4j.Logger;

public class QuestScreen extends AbstractMillenaireScreen {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int PARCHMENT_WIDTH = 256;
   private static final int PARCHMENT_HEIGHT = 200;
   private static final int LINES_PER_PAGE = computeMaxLinesPerPage(200);
   private static final int TEXT_WIDTH = textWidth(256);
   private static final int BUTTON_WIDTH = 80;
   private static final int BUTTON_HEIGHT = 20;
   private static final int BUTTON_SPACING = 8;
   private static final int BUTTON_TIMEOUT_TICKS = 100;
   private final QuestOpenPayload data;
   private List<PanelLine> allLines = List.of();
   @Nullable
   private Button acceptButton;
   @Nullable
   private Button refuseButton;
   @Nullable
   private Button continueButton;
   @Nullable
   private Button closeButton;
   private boolean pendingResponse = false;
   private int pendingTicks = 0;
   @Nullable
   private String resultText = null;
   private boolean resultSuccess = false;

   public QuestScreen(QuestOpenPayload data) {
      super(Component.literal(data.villagerDisplayName()));
      this.data = data;
   }

   protected void init() {
      super.init();
      this.allLines = this.buildContentLines();
      this.totalPages = PanelRenderHelper.computePageCount(this.allLines.size(), LINES_PER_PAGE);
      this.currentPage = 0;
      int parchX = (this.width - 256) / 2;
      int parchY = (this.height - 200) / 2;
      this.initPaginationButtons(parchX, parchY, 256, 200, 16);
      if (!this.data.villagerTypeKey().isEmpty()) {
         this.addTravelBookButton(
            parchX, parchY, 256, button -> TravelBookNavHelper.openVillagerDetail(this, this.data.cultureKey(), this.data.villagerTypeKey())
         );
      }

      int buttonY = parchY + 200 + 4;
      int centerX = this.width / 2;
      if (this.resultText != null) {
         this.closeButton = Button.builder(Component.translatable("gui.millenaire.quest.close"), b -> this.onClose())
            .bounds(centerX - 40, buttonY, 80, 20)
            .build();
         this.addRenderableWidget(this.closeButton);
      } else if (this.data.isFirstStep()) {
         if (this.data.conditionsMet()) {
            this.refuseButton = Button.builder(Component.translatable("gui.millenaire.quest.refuse"), b -> this.onRefuse())
               .bounds(centerX - 80 - 4, buttonY, 80, 20)
               .build();
            this.addRenderableWidget(this.refuseButton);
            this.acceptButton = Button.builder(Component.translatable("gui.millenaire.quest.accept"), b -> this.onAccept())
               .bounds(centerX + 4, buttonY, 80, 20)
               .build();
            this.addRenderableWidget(this.acceptButton);
         } else {
            this.closeButton = Button.builder(Component.translatable("gui.millenaire.quest.close"), b -> this.onClose())
               .bounds(centerX - 40, buttonY, 80, 20)
               .build();
            this.addRenderableWidget(this.closeButton);
         }
      } else if (this.data.conditionsMet()) {
         this.continueButton = Button.builder(Component.translatable("gui.millenaire.quest.continue"), b -> this.onContinue())
            .bounds(centerX - 40, buttonY, 80, 20)
            .build();
         this.addRenderableWidget(this.continueButton);
      } else {
         this.closeButton = Button.builder(Component.translatable("gui.millenaire.quest.close"), b -> this.onClose())
            .bounds(centerX - 40, buttonY, 80, 20)
            .build();
         this.addRenderableWidget(this.closeButton);
      }

      this.updatePaginationButtons();
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, 1610612736);
      int parchX = (this.width - 256) / 2;
      int parchY = (this.height - 200) / 2;
      PanelRenderHelper.renderTexturedBackground(graphics, PanelRenderHelper.QUEST_TEXTURE, parchX, parchY, 256, 200);
      Component headerTitle = Component.literal(this.data.villagerDisplayName());
      int contentY = this.renderParchmentHeader(graphics, headerTitle, parchX, parchY, 256);
      String occupations = this.data.villagerGameOccupation();
      if (!this.data.villagerNativeOccupation().isEmpty()) {
         if (!occupations.isEmpty()) {
            occupations = this.data.villagerNativeOccupation() + " — " + occupations;
         } else {
            occupations = this.data.villagerNativeOccupation();
         }
      }

      if (!occupations.isEmpty()) {
         graphics.drawCenteredString(this.font, Component.literal(occupations).withStyle(s -> s.withItalic(true)), parchX + 128, contentY, -13421773);
         contentY += 11;
      }

      int startLine = this.currentPage * LINES_PER_PAGE;
      int maxLines = LINES_PER_PAGE;
      if (!occupations.isEmpty() && this.currentPage == 0) {
         maxLines--;
      }

      int contentX = parchX + 16;
      PanelRenderHelper.renderPanelLines(graphics, this.font, this.allLines, contentX, contentY, TEXT_WIDTH, startLine, maxLines);
      this.renderPageCounter(graphics, parchX, parchY, 256, 200);
      super.render(graphics, mouseX, mouseY, partialTick);
   }

   public void tick() {
      super.tick();
      if (this.pendingResponse) {
         this.pendingTicks++;
         if (this.pendingTicks >= 100) {
            this.pendingResponse = false;
            this.setButtonsEnabled(true);
         }
      }
   }

   private void onAccept() {
      this.setPendingState();
      ClientQuestCache.CachedQuest cq = ClientQuestCache.getQuest(this.data.questUniqueId());
      if (cq != null) {
         String villagerUuid = this.resolveCurrentStepVillagerUuid(cq);
         if (villagerUuid != null) {
            PacketDistributor.sendToServer(new QuestCompleteStepPayload(this.data.questUniqueId(), villagerUuid), new CustomPacketPayload[0]);
         }
      }
   }

   private void onRefuse() {
      this.setPendingState();
      PacketDistributor.sendToServer(new QuestRefusePayload(this.data.questUniqueId()), new CustomPacketPayload[0]);
   }

   private void onContinue() {
      this.setPendingState();
      ClientQuestCache.CachedQuest cq = ClientQuestCache.getQuest(this.data.questUniqueId());
      if (cq != null) {
         String villagerUuid = this.resolveCurrentStepVillagerUuid(cq);
         if (villagerUuid != null) {
            PacketDistributor.sendToServer(new QuestCompleteStepPayload(this.data.questUniqueId(), villagerUuid), new CustomPacketPayload[0]);
         }
      }
   }

   private void setPendingState() {
      this.pendingResponse = true;
      this.pendingTicks = 0;
      this.setButtonsEnabled(false);
   }

   private void setButtonsEnabled(boolean enabled) {
      if (this.acceptButton != null) {
         this.acceptButton.active = enabled;
      }

      if (this.refuseButton != null) {
         this.refuseButton.active = enabled;
      }

      if (this.continueButton != null) {
         this.continueButton.active = enabled;
      }
   }

   public void onQuestResult(String text, boolean success) {
      this.resultText = text;
      this.resultSuccess = success;
      this.pendingResponse = false;
      this.rebuildWidgets();
   }

   private List<PanelLine> buildContentLines() {
      List<PanelLine> lines = new ArrayList<>();
      if (!this.data.descriptionText().isEmpty()) {
         addSplitLines(lines, this.data.descriptionText(), null);
      }

      if (!this.data.conditionsMet() && !this.data.conditionText().isEmpty()) {
         lines.add(PanelLine.separator());
         addSplitLines(lines, this.data.conditionText(), "§c");
      }

      if (this.resultText != null && !this.resultText.isEmpty()) {
         lines.add(PanelLine.separator());
         String prefix = this.resultSuccess ? "§a" : "§c";
         addSplitLines(lines, this.resultText, prefix);
      }

      return PanelRenderHelper.wrapLines(lines, this.font, TEXT_WIDTH);
   }

   private static void addSplitLines(List<PanelLine> lines, String text, @Nullable String colorPrefix) {
      String normalized = text.replace("\n", "<ret>");
      String[] segments = normalized.split("<ret>");

      for (int i = 0; i < segments.length; i++) {
         String segment = segments[i].trim();
         if (segment.isEmpty()) {
            lines.add(PanelLine.empty());
         } else {
            lines.add(PanelLine.text(colorPrefix != null ? colorPrefix + segment : segment));
         }
      }
   }

   @Nullable
   private String resolveCurrentStepVillagerUuid(ClientQuestCache.CachedQuest cq) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         Entity entity = mc.level.getEntity(this.data.villagerEntityId());
         if (entity != null) {
            return entity.getUUID().toString();
         }
      }

      return null;
   }
}
