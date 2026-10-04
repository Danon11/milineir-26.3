package org.millenaire.client.gui;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.item.MoneyHelper;
import org.millenaire.network.BuildingPurchasePayload;
import org.millenaire.network.CropLearningPayload;
import org.millenaire.network.CultureControlPurchasePayload;
import org.millenaire.network.DiplomacyActionPayload;
import org.millenaire.network.HuntingLearningPayload;
import org.millenaire.network.VillageChiefPayload;
import org.millenaire.network.VillageScrollPurchasePayload;
import org.millenaire.village.VillageRelations;
import org.millenaire.village.panel.PanelLine;

public class VillageChiefScreen extends AbstractMillenaireScreen {
   private static final int PARCHMENT_WIDTH = 256;
   private static final int PARCHMENT_HEIGHT = 200;
   private static final int LINES_PER_PAGE = computeMaxLinesPerPage(200);
   private static final int PREVIEW_LINES_COST = 5;
   private static final int TEXT_WIDTH = textWidth(256);
   private final VillageChiefPayload data;
   private List<PanelLine> contentLines = List.of();
   private int scrollButtonPage = -1;
   private int scrollButtonLineIndex = -1;
   private final List<VillageChiefScreen.PurchaseButtonEntry> purchaseButtonEntries = new ArrayList<>();
   private final List<Button> purchaseButtons = new ArrayList<>();
   private final List<VillageChiefScreen.DiplomacyButtonEntry> diplomacyButtonEntries = new ArrayList<>();
   private final List<Button> diplomacyButtons = new ArrayList<>();
   private final List<VillageChiefScreen.LearningButtonEntry> learningButtonEntries = new ArrayList<>();
   private final List<Button> learningButtons = new ArrayList<>();
   private int cultureControlButtonLineIndex = -1;
   private int cultureControlButtonPage = -1;
   @Nullable
   private Button cultureControlButton;

   public VillageChiefScreen(VillageChiefPayload data) {
      super(Component.translatable("gui.millenaire.chief.title"));
      this.data = data;
   }

   protected void init() {
      super.init();
      this.purchaseButtonEntries.clear();
      this.purchaseButtons.clear();
      this.diplomacyButtonEntries.clear();
      this.diplomacyButtons.clear();
      this.learningButtonEntries.clear();
      this.learningButtons.clear();
      this.scrollButtonLineIndex = -1;
      this.cultureControlButtonLineIndex = -1;
      List<PanelLine> rawLines = this.buildContentLines();
      this.contentLines = PanelRenderHelper.wrapLines(rawLines, this.font, TEXT_WIDTH);
      this.recalcButtonIndices(rawLines);
      this.currentPage = 0;
      int page0Lines = LINES_PER_PAGE - 5;
      int remaining = Math.max(0, this.contentLines.size() - page0Lines);
      this.totalPages = 1 + (remaining > 0 ? PanelRenderHelper.computePageCount(remaining, LINES_PER_PAGE) : 0);
      int parchX = (this.width - 256) / 2;
      int parchY = (this.height - 200) / 2;
      this.initPaginationButtons(parchX, parchY, 256, 200, 16);
      if (this.scrollButtonLineIndex >= 0) {
         VillageChiefScreen.ButtonPlacement placement = placeButton(this.scrollButtonLineIndex, parchY);
         this.scrollButtonPage = placement.page();
         this.addScrollButton(parchX, placement.y());
      }

      for (VillageChiefScreen.PurchaseButtonEntry pbe : this.purchaseButtonEntries) {
         VillageChiefScreen.ButtonPlacement placement = placeButton(pbe.lineIndex, parchY);
         int buttonWidth = TEXT_WIDTH;
         Component label = Component.translatable("gui.millenaire.chief.building_buy", new Object[]{pbe.entry.nativeName(), pbe.entry.price()});
         int finalBtnPage = placement.page();
         String planSetId = pbe.entry.planSetId();
         Button purchaseBtn = Button.builder(label, button -> {
            PacketDistributor.sendToServer(new BuildingPurchasePayload(this.data.villageId(), planSetId), new CustomPacketPayload[0]);
            this.onClose();
         }).bounds(parchX + 16, placement.y(), buttonWidth, 16).build();
         purchaseBtn.visible = this.currentPage == finalBtnPage;
         this.addRenderableWidget(purchaseBtn);
         this.purchaseButtons.add(purchaseBtn);
      }

      for (VillageChiefScreen.DiplomacyButtonEntry dbe : this.diplomacyButtonEntries) {
         VillageChiefScreen.ButtonPlacement placement = placeButton(dbe.lineIndex, parchY);
         int buttonWidth = TEXT_WIDTH;
         Component label = dbe.isPraise
            ? Component.translatable("gui.millenaire.chief.praise_btn")
            : Component.translatable("gui.millenaire.chief.slander_btn");
         int finalBtnPage2 = placement.page();
         String targetId = dbe.targetVillageId;
         boolean praise = dbe.isPraise;
         Button dipBtn = Button.builder(
               label, button -> PacketDistributor.sendToServer(new DiplomacyActionPayload(this.data.villageId(), targetId, praise), new CustomPacketPayload[0])
            )
            .bounds(parchX + 16, placement.y(), buttonWidth, 16)
            .build();
         dipBtn.visible = this.currentPage == finalBtnPage2;
         this.addRenderableWidget(dipBtn);
         this.diplomacyButtons.add(dipBtn);
      }

      for (VillageChiefScreen.LearningButtonEntry lbe : this.learningButtonEntries) {
         VillageChiefScreen.ButtonPlacement placement = placeButton(lbe.lineIndex, parchY);
         int buttonWidth = TEXT_WIDTH;
         String priceStr = MoneyHelper.formatPrice(512);
         Component label = lbe.isCrop
            ? Component.translatable("gui.millenaire.chief.crop_learn", new Object[]{priceStr})
            : Component.translatable("gui.millenaire.chief.hunting_learn", new Object[]{priceStr});
         int finalBtnPage3 = placement.page();
         String key = lbe.key;
         boolean isCrop = lbe.isCrop;
         Button learnBtn = Button.builder(label, button -> {
            if (isCrop) {
               PacketDistributor.sendToServer(new CropLearningPayload(this.data.villageId(), key), new CustomPacketPayload[0]);
            } else {
               PacketDistributor.sendToServer(new HuntingLearningPayload(this.data.villageId(), key), new CustomPacketPayload[0]);
            }

            this.onClose();
         }).bounds(parchX + 16, placement.y(), buttonWidth, 16).build();
         learnBtn.visible = this.currentPage == finalBtnPage3;
         this.addRenderableWidget(learnBtn);
         this.learningButtons.add(learnBtn);
      }

      if (this.cultureControlButtonLineIndex >= 0) {
         VillageChiefScreen.ButtonPlacement placement = placeButton(this.cultureControlButtonLineIndex, parchY);
         this.cultureControlButtonPage = placement.page();
         int buttonWidth = TEXT_WIDTH;
         Component label = Component.translatable("gui.millenaire.chief.control_get");
         this.cultureControlButton = Button.builder(label, button -> {
            PacketDistributor.sendToServer(new CultureControlPurchasePayload(this.data.villageId()), new CustomPacketPayload[0]);
            this.onClose();
         }).bounds(parchX + 16, placement.y(), buttonWidth, 16).build();
         this.cultureControlButton.visible = this.currentPage == this.cultureControlButtonPage;
         this.addRenderableWidget(this.cultureControlButton);
      }

      this.updatePaginationButtons();
   }

   private static VillageChiefScreen.ButtonPlacement placeButton(int lineIndex, int parchY) {
      int page0Lines = LINES_PER_PAGE - 5;
      if (lineIndex < page0Lines) {
         int contentY = parchY + 42 + 55;
         return new VillageChiefScreen.ButtonPlacement(0, contentY + lineIndex * 11);
      } else {
         int adjustedIdx = lineIndex - page0Lines;
         int lineOnPage = adjustedIdx % LINES_PER_PAGE;
         int contentY = parchY + 42;
         return new VillageChiefScreen.ButtonPlacement(1 + adjustedIdx / LINES_PER_PAGE, contentY + lineOnPage * 11);
      }
   }

   private static int pageForLine(int lineIndex) {
      int page0Lines = LINES_PER_PAGE - 5;
      return lineIndex < page0Lines ? 0 : 1 + (lineIndex - page0Lines) / LINES_PER_PAGE;
   }

   private void addScrollButton(int parchX, int buttonY) {
      int buttonWidth = TEXT_WIDTH;
      Component label = Component.translatable("gui.millenaire.chief.scroll_buy", new Object[]{128});
      Button scrollBtn = Button.builder(label, button -> {
         PacketDistributor.sendToServer(new VillageScrollPurchasePayload(this.data.villageId()), new CustomPacketPayload[0]);
         this.onClose();
      }).bounds(parchX + 16, buttonY, buttonWidth, 16).build();
      this.addRenderableWidget(scrollBtn);
      scrollBtn.visible = this.currentPage == this.scrollButtonPage;
   }

   protected void updatePaginationButtons() {
      super.updatePaginationButtons();

      for (GuiEventListener w : this.children()) {
         if (w instanceof Button btn && btn != this.prevButton && btn != this.nextButton) {
            String msg = btn.getMessage().getString();
            if (msg.contains(String.valueOf(128))) {
               btn.visible = this.currentPage == this.scrollButtonPage;
            }
         }
      }

      for (int i = 0; i < this.purchaseButtons.size(); i++) {
         if (i < this.purchaseButtonEntries.size()) {
            int lineIdx = this.purchaseButtonEntries.get(i).lineIndex;
            this.purchaseButtons.get(i).visible = this.currentPage == pageForLine(lineIdx);
         }
      }

      for (int i = 0; i < this.diplomacyButtons.size(); i++) {
         if (i < this.diplomacyButtonEntries.size()) {
            int lineIdx = this.diplomacyButtonEntries.get(i).lineIndex;
            this.diplomacyButtons.get(i).visible = this.currentPage == pageForLine(lineIdx);
         }
      }

      for (int i = 0; i < this.learningButtons.size(); i++) {
         if (i < this.learningButtonEntries.size()) {
            int lineIdx = this.learningButtonEntries.get(i).lineIndex;
            this.learningButtons.get(i).visible = this.currentPage == pageForLine(lineIdx);
         }
      }

      if (this.cultureControlButton != null) {
         this.cultureControlButton.visible = this.currentPage == this.cultureControlButtonPage;
      }
   }

   private List<PanelLine> buildContentLines() {
      List<PanelLine> lines = new ArrayList<>();
      String roleStr = this.data.roleName().startsWith("role.") ? Component.translatable(this.data.roleName()).getString() : this.data.roleName();
      if (!this.data.chiefTypeKey().isEmpty()) {
         String cultureKey = this.data.cultureId();
         PanelLine.PanelNavTarget chiefTarget = new PanelLine.PanelNavTarget("VILLAGER_DETAIL", cultureKey, "villagers", this.data.chiefTypeKey());
         lines.add(PanelLine.clickableText(this.data.chiefName() + ", " + roleStr, chiefTarget));
      } else {
         lines.add(PanelLine.bold(this.data.chiefName() + ", " + roleStr));
      }

      lines.add(PanelLine.empty());
      lines.add(PanelLine.text(Component.translatable("gui.millenaire.village").getString() + " : " + this.data.villageName()));
      lines.add(PanelLine.text(Component.translatable("gui.millenaire.culture").getString() + " : " + this.data.cultureName()));
      lines.add(PanelLine.empty());
      lines.add(PanelLine.text(Component.translatable("gui.millenaire.chief.villagers").getString() + " : " + this.data.totalVillagers()));
      lines.add(
         PanelLine.text(
            Component.translatable("gui.millenaire.chief.buildings").getString()
               + " : "
               + this.data.totalBuildings()
               + " ("
               + this.data.completeBuildings()
               + " "
               + Component.translatable("gui.millenaire.chief.complete").getString()
               + ", "
               + this.data.underConstructionBuildings()
               + " "
               + Component.translatable("gui.millenaire.chief.under_construction").getString()
               + ")"
         )
      );
      lines.add(PanelLine.empty());
      String repLabel = MillenaireScreenUtils.resolveReputationLabel(this.data.reputationLabel());
      int repColor = MillenaireScreenUtils.getReputationColor(this.data.reputation());
      lines.add(
         PanelLine.colored(Component.translatable("gui.millenaire.reputation").getString() + " : " + this.data.reputation() + " (" + repLabel + ")", repColor)
      );
      lines.add(PanelLine.separator());
      if (this.data.playerReputation() < 8192) {
         lines.add(PanelLine.colored(Component.translatable("gui.millenaire.chief.scroll_no_rep").getString(), -13421773));
      } else if (this.data.playerMoney() < 128) {
         int manque = 128 - this.data.playerMoney();
         lines.add(PanelLine.colored(Component.translatable("gui.millenaire.chief.scroll_no_money", new Object[]{manque}).getString(), 16755200));
      } else {
         this.scrollButtonLineIndex = lines.size();
         PanelLine.addButtonPlaceholder(lines);
      }

      lines.add(PanelLine.separator());
      List<VillageChiefPayload.PlayerBuildingEntry> pbs = this.data.playerBuildings();
      if (!pbs.isEmpty()) {
         lines.add(PanelLine.colored(Component.translatable("gui.millenaire.chief.building_purchase").getString(), -11193600));

         for (VillageChiefPayload.PlayerBuildingEntry pb : pbs) {
            switch (pb.status()) {
               case "BUILT":
                  lines.add(
                     PanelLine.colored(
                        "  " + pb.nativeName() + " — " + Component.translatable("gui.millenaire.chief.building_already_built").getString(), 5592405
                     )
                  );
                  break;
               case "BOUGHT":
                  lines.add(
                     PanelLine.colored(
                        "  " + pb.nativeName() + " — " + Component.translatable("gui.millenaire.chief.building_already_bought").getString(), 13421568
                     )
                  );
                  break;
               case "NO_REP":
                  lines.add(
                     PanelLine.colored(
                        "  "
                           + pb.nativeName()
                           + " — "
                           + Component.translatable("gui.millenaire.chief.building_no_rep", new Object[]{pb.reputation()}).getString(),
                        16755200
                     )
                  );
                  break;
               case "NO_MONEY":
                  int manque = pb.price() - this.data.playerMoney();
                  lines.add(
                     PanelLine.colored(
                        "  " + pb.nativeName() + " — " + Component.translatable("gui.millenaire.chief.building_no_money", new Object[]{manque}).getString(),
                        16755200
                     )
                  );
                  break;
               case "AVAILABLE":
                  this.purchaseButtonEntries.add(new VillageChiefScreen.PurchaseButtonEntry(lines.size(), pb));
                  PanelLine.addButtonPlaceholder(lines);
            }
         }

         lines.add(PanelLine.separator());
      }

      lines.add(PanelLine.colored(Component.translatable("gui.millenaire.chief.building_list").getString(), -11193600));

      for (String entry : this.data.buildingEntries()) {
         int sep = entry.lastIndexOf(124);
         String displayName = sep > 0 ? entry.substring(0, sep) : entry;
         String status = sep > 0 ? entry.substring(sep + 1) : "";
         String statusKey;
         int statusColor;
         if ("COMPLETE".equals(status)) {
            statusKey = "gui.millenaire.chief.status_complete";
            statusColor = 5635925;
         } else if (!"UNDER_CONSTRUCTION".equals(status) && !"UPGRADING".equals(status)) {
            statusKey = "gui.millenaire.chief.status_planned";
            statusColor = 5592405;
         } else {
            statusKey = "gui.millenaire.chief.status_construction";
            statusColor = 13421568;
         }

         String statusText = Component.translatable(statusKey).getString();
         lines.add(PanelLine.colored("  " + displayName + " — " + statusText, statusColor));
      }

      lines.add(PanelLine.separator());
      if (!this.data.cropOffers().isEmpty()) {
         lines.add(PanelLine.colored(Component.translatable("gui.millenaire.chief.crops_known").getString(), -11193600));
         lines.add(PanelLine.empty());

         for (VillageChiefPayload.LearningOffer offer : this.data.cropOffers()) {
            String itemName = Component.translatable(offer.itemName()).getString();
            switch (offer.status()) {
               case "LEARNED":
                  lines.add(
                     PanelLine.colored("  " + Component.translatable("gui.millenaire.chief.crop_already_learned", new Object[]{itemName}).getString(), 5592405)
                  );
                  break;
               case "NO_REP":
                  lines.add(PanelLine.colored("  " + Component.translatable("gui.millenaire.chief.crop_no_rep", new Object[]{itemName}).getString(), 16755200));
                  break;
               case "NO_MONEY":
                  lines.add(
                     PanelLine.colored("  " + Component.translatable("gui.millenaire.chief.crop_no_money", new Object[]{itemName}).getString(), 16755200)
                  );
                  break;
               case "AVAILABLE":
                  lines.add(
                     PanelLine.colored("  " + Component.translatable("gui.millenaire.chief.crop_available", new Object[]{itemName}).getString(), -13421773)
                  );
                  this.learningButtonEntries.add(new VillageChiefScreen.LearningButtonEntry(lines.size(), offer.key(), true));
                  PanelLine.addButtonPlaceholder(lines);
            }
         }

         lines.add(PanelLine.empty());
      }

      if (!this.data.huntingOffers().isEmpty()) {
         lines.add(PanelLine.colored(Component.translatable("gui.millenaire.chief.hunting_known").getString(), -11193600));
         lines.add(PanelLine.empty());

         for (VillageChiefPayload.LearningOffer offer : this.data.huntingOffers()) {
            String itemName = Component.translatable(offer.itemName()).getString();
            switch (offer.status()) {
               case "LEARNED":
                  lines.add(
                     PanelLine.colored(
                        "  " + Component.translatable("gui.millenaire.chief.hunting_already_learned", new Object[]{itemName}).getString(), 5592405
                     )
                  );
                  break;
               case "NO_REP":
                  lines.add(
                     PanelLine.colored("  " + Component.translatable("gui.millenaire.chief.hunting_no_rep", new Object[]{itemName}).getString(), 16755200)
                  );
                  break;
               case "NO_MONEY":
                  lines.add(
                     PanelLine.colored("  " + Component.translatable("gui.millenaire.chief.hunting_no_money", new Object[]{itemName}).getString(), 16755200)
                  );
                  break;
               case "AVAILABLE":
                  lines.add(
                     PanelLine.colored("  " + Component.translatable("gui.millenaire.chief.hunting_available", new Object[]{itemName}).getString(), -13421773)
                  );
                  this.learningButtonEntries.add(new VillageChiefScreen.LearningButtonEntry(lines.size(), offer.key(), false));
                  PanelLine.addButtonPlaceholder(lines);
            }
         }

         lines.add(PanelLine.empty());
      }

      if (this.data.hasCultureControl()) {
         lines.add(
            PanelLine.colored(Component.translatable("gui.millenaire.chief.control_already", new Object[]{this.data.cultureName()}).getString(), 5592405)
         );
      } else if (this.data.cultureControlAvailable()) {
         lines.add(
            PanelLine.colored(Component.translatable("gui.millenaire.chief.control_available", new Object[]{this.data.cultureName()}).getString(), -13421773)
         );
         this.cultureControlButtonLineIndex = lines.size();
         PanelLine.addButtonPlaceholder(lines);
      } else {
         lines.add(
            PanelLine.colored(Component.translatable("gui.millenaire.chief.control_no_rep", new Object[]{this.data.cultureName()}).getString(), 16755200)
         );
      }

      lines.add(PanelLine.separator());
      lines.add(PanelLine.colored(Component.translatable("gui.millenaire.chief.diplomacy").getString(), -11193600));
      if (this.data.relationEntries().isEmpty()) {
         lines.add(PanelLine.colored(Component.translatable("gui.millenaire.chief.diplomacy_none").getString(), -13421773));
      } else {
         lines.add(
            PanelLine.colored(Component.translatable("gui.millenaire.chief.diplomacy_points", new Object[]{this.data.diplomacyPoints()}).getString(), -13421773)
         );
         boolean canAct = this.data.diplomacyPoints() > 0 && this.data.playerReputation() > 0;

         for (VillageChiefPayload.RelationEntry re : this.data.relationEntries()) {
            ChatFormatting cf = VillageRelations.getRelationColor(re.relation());
            int relColor = cf.getColor() != null ? cf.getColor() : -13421773;
            String relLabel = Component.translatable(re.relationLabel()).getString();
            lines.add(PanelLine.colored("  " + re.villageName() + " (" + re.cultureName() + ") — " + relLabel, relColor));
            if (canAct) {
               if (re.relation() < 100) {
                  this.diplomacyButtonEntries.add(new VillageChiefScreen.DiplomacyButtonEntry(lines.size(), re.villageId(), true));
                  PanelLine.addButtonPlaceholder(lines);
               }

               if (re.relation() > -100) {
                  this.diplomacyButtonEntries.add(new VillageChiefScreen.DiplomacyButtonEntry(lines.size(), re.villageId(), false));
                  PanelLine.addButtonPlaceholder(lines);
               }
            }
         }
      }

      lines.add(PanelLine.separator());
      lines.add(PanelLine.colored(Component.translatable("gui.millenaire.chief.help_title").getString(), -11193600));
      lines.add(PanelLine.empty());
      lines.add(PanelLine.text(Component.translatable("gui.millenaire.chief.relation_help").getString()));
      return lines;
   }

   private void recalcButtonIndices(List<PanelLine> rawLines) {
      List<PanelLine> wrapped = this.contentLines;
      int[] rawToWrapped = new int[rawLines.size()];
      int wrappedIdx = 0;

      for (int rawIdx = 0; rawIdx < rawLines.size(); rawIdx++) {
         rawToWrapped[rawIdx] = wrappedIdx;
         PanelLine rawLine = rawLines.get(rawIdx);
         if (!rawLine.isSeparator() && !rawLine.text().isEmpty() && !rawLine.isColumns()) {
            String text = PanelRenderHelper.resolveDisplayText(rawLine);
            if (this.font.width(text) <= TEXT_WIDTH) {
               wrappedIdx++;
            } else {
               String[] words = text.split(" ");
               StringBuilder current = new StringBuilder();
               int segments = 0;

               for (String word : words) {
                  String test = current.isEmpty() ? word : current + " " + word;
                  if (this.font.width(test) > TEXT_WIDTH && !current.isEmpty()) {
                     segments++;
                     current = new StringBuilder(word);
                  } else {
                     current = new StringBuilder(test);
                  }
               }

               if (!current.isEmpty()) {
                  segments++;
               }

               wrappedIdx += segments;
            }
         } else {
            wrappedIdx++;
         }
      }

      if (this.scrollButtonLineIndex >= 0 && this.scrollButtonLineIndex < rawToWrapped.length) {
         this.scrollButtonLineIndex = rawToWrapped[this.scrollButtonLineIndex];
      }

      List<VillageChiefScreen.PurchaseButtonEntry> remapped = new ArrayList<>();

      for (VillageChiefScreen.PurchaseButtonEntry pbe : this.purchaseButtonEntries) {
         if (pbe.lineIndex < rawToWrapped.length) {
            remapped.add(new VillageChiefScreen.PurchaseButtonEntry(rawToWrapped[pbe.lineIndex], pbe.entry));
         }
      }

      this.purchaseButtonEntries.clear();
      this.purchaseButtonEntries.addAll(remapped);
      List<VillageChiefScreen.DiplomacyButtonEntry> remappedDip = new ArrayList<>();

      for (VillageChiefScreen.DiplomacyButtonEntry dbe : this.diplomacyButtonEntries) {
         if (dbe.lineIndex < rawToWrapped.length) {
            remappedDip.add(new VillageChiefScreen.DiplomacyButtonEntry(rawToWrapped[dbe.lineIndex], dbe.targetVillageId, dbe.isPraise));
         }
      }

      this.diplomacyButtonEntries.clear();
      this.diplomacyButtonEntries.addAll(remappedDip);
      List<VillageChiefScreen.LearningButtonEntry> remappedLearn = new ArrayList<>();

      for (VillageChiefScreen.LearningButtonEntry lbe : this.learningButtonEntries) {
         if (lbe.lineIndex < rawToWrapped.length) {
            remappedLearn.add(new VillageChiefScreen.LearningButtonEntry(rawToWrapped[lbe.lineIndex], lbe.key, lbe.isCrop));
         }
      }

      this.learningButtonEntries.clear();
      this.learningButtonEntries.addAll(remappedLearn);
      if (this.cultureControlButtonLineIndex >= 0 && this.cultureControlButtonLineIndex < rawToWrapped.length) {
         this.cultureControlButtonLineIndex = rawToWrapped[this.cultureControlButtonLineIndex];
      }
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0) {
         int parchX = (this.width - 256) / 2;
         int parchY = (this.height - 200) / 2;
         int contentX = parchX + 16;
         int page0Lines = LINES_PER_PAGE - 5;
         int startLine;
         int maxLines;
         int contentY;
         if (this.currentPage == 0) {
            startLine = 0;
            maxLines = page0Lines;
            contentY = parchY + 42 + 55;
         } else {
            startLine = page0Lines + (this.currentPage - 1) * LINES_PER_PAGE;
            maxLines = LINES_PER_PAGE;
            contentY = parchY + 42;
         }

         PanelLine.PanelNavTarget target = PanelRenderHelper.getClickedNavTarget(
            this.contentLines, contentX, contentY, TEXT_WIDTH, startLine, maxLines, mouseX, mouseY
         );
         if (target != null) {
            TravelBookNavHelper.openFromNavTarget(this, target);
            return true;
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, 1610612736);
      int parchX = (this.width - 256) / 2;
      int parchY = (this.height - 200) / 2;
      PanelRenderHelper.renderTexturedBackground(graphics, PanelRenderHelper.VILLAGE_CHIEF_TEXTURE, parchX, parchY, 256, 200);
      int y = this.renderParchmentHeader(graphics, Component.translatable("gui.millenaire.chief.title"), parchX, parchY, 256);
      if (this.currentPage == 0) {
         y = MillenaireScreenUtils.renderVillagerPreview(graphics, parchX, parchY, 256, 16, y, mouseX, mouseY, this.data.entityId());
      }

      int page0Lines = LINES_PER_PAGE - 5;
      int startLine;
      int maxLines;
      if (this.currentPage == 0) {
         startLine = 0;
         maxLines = page0Lines;
      } else {
         startLine = page0Lines + (this.currentPage - 1) * LINES_PER_PAGE;
         maxLines = LINES_PER_PAGE;
      }

      PanelRenderHelper.renderPanelLines(graphics, this.font, this.contentLines, parchX + 16, y, TEXT_WIDTH, startLine, maxLines);
      this.renderPageCounter(graphics, parchX, parchY, 256, 200);
      super.render(graphics, mouseX, mouseY, partialTick);
   }

   private record ButtonPlacement(int page, int y) {
   }

   private record DiplomacyButtonEntry(int lineIndex, String targetVillageId, boolean isPraise) {
   }

   private record LearningButtonEntry(int lineIndex, String key, boolean isCrop) {
   }

   private record PurchaseButtonEntry(int lineIndex, VillageChiefPayload.PlayerBuildingEntry entry) {
   }
}
