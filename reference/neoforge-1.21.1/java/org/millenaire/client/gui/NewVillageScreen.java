package org.millenaire.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.item.ModItems;
import org.millenaire.network.VillageCreationRequestPayload;
import org.millenaire.network.VillageTypeListPayload;

public class NewVillageScreen extends AbstractMillenaireScreen {
   private static final int PARCHMENT_WIDTH = 204;
   private static final int PARCHMENT_HEIGHT = 220;
   private static final int TEXT_WIDTH = textWidth(204);
   private static final int CULTURE_HEADER_HEIGHT = 14;
   private static final int BUTTON_ROW_HEIGHT = 16;
   private static final int BUTTON_WIDTH = 160;
   private static final int BUTTON_HEIGHT = 14;
   private static final int INFO_BUTTON_WIDTH = 20;
   private static final int INFO_BUTTON_GAP = 4;
   private final VillageTypeListPayload data;
   private final Map<String, List<VillageTypeListPayload.VillageTypeEntry>> groupedByCulture;
   private List<NewVillageScreen.DisplayRow> displayRows = List.of();
   private final List<Button> pageButtons = new ArrayList<>();
   private int rowsPerPage;

   public NewVillageScreen(VillageTypeListPayload data) {
      super(Component.translatable("gui.millenaire.new_village.title"));
      this.data = data;
      this.groupedByCulture = new LinkedHashMap<>();

      for (VillageTypeListPayload.VillageTypeEntry entry : data.entries()) {
         this.groupedByCulture.computeIfAbsent(entry.cultureKey(), k -> new ArrayList<>()).add(entry);
      }
   }

   protected void init() {
      super.init();
      List<NewVillageScreen.DisplayRow> rows = new ArrayList<>();

      for (Entry<String, List<VillageTypeListPayload.VillageTypeEntry>> group : this.groupedByCulture.entrySet()) {
         List<VillageTypeListPayload.VillageTypeEntry> entries = group.getValue();
         if (!entries.isEmpty()) {
            rows.add(new NewVillageScreen.CultureHeaderRow(entries.getFirst().cultureName(), group.getKey()));

            for (VillageTypeListPayload.VillageTypeEntry entry : entries) {
               rows.add(new NewVillageScreen.VillageTypeRow(entry));
            }
         }
      }

      this.displayRows = rows;
      int parchY = (this.height - 220) / 2;
      int contentStartY = parchY + 42;
      int contentEndY = parchY + 220 - 15;
      int availableHeight = contentEndY - contentStartY;
      this.rowsPerPage = Math.max(1, availableHeight / 16);
      this.totalPages = Math.max(1, (this.displayRows.size() + this.rowsPerPage - 1) / this.rowsPerPage);
      this.currentPage = 0;
      int parchX = (this.width - 204) / 2;
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
      int y = parchY + 42;
      int startIdx = this.currentPage * this.rowsPerPage;
      int endIdx = Math.min(startIdx + this.rowsPerPage, this.displayRows.size());
      int mainBtnWidth = 136;

      for (int i = startIdx; i < endIdx; i++) {
         NewVillageScreen.DisplayRow row = this.displayRows.get(i);
         if (row instanceof NewVillageScreen.VillageTypeRow vtRow) {
            VillageTypeListPayload.VillageTypeEntry entry = vtRow.entry();
            String label = entry.requiresControl()
               ? entry.displayName() + " " + Component.translatable("gui.millenaire.new_village.controlled_suffix").getString()
               : entry.displayName();
            boolean enabled = !entry.requiresControl() || entry.hasControl();
            int btnX = contentX + (TEXT_WIDTH - 160) / 2;
            Button btn = Button.builder(
                  Component.literal(label),
                  button -> {
                     PacketDistributor.sendToServer(
                        new VillageCreationRequestPayload(this.data.targetPos(), entry.cultureKey(), entry.typeKey()), new CustomPacketPayload[0]
                     );
                     this.onClose();
                  }
               )
               .bounds(btnX, y, mainBtnWidth, 14)
               .build();
            btn.active = enabled;
            this.addRenderableWidget(btn);
            this.pageButtons.add(btn);
            ItemStack bookIcon = new ItemStack((ItemLike)ModItems.TRAVEL_BOOK.get());
            IconButton infoBtn = new IconButton(
               btnX + mainBtnWidth + 4,
               y,
               20,
               14,
               bookIcon,
               Component.translatable("gui.millenaire.new_village.info_tooltip"),
               button -> TravelBookNavHelper.openVillageType(this, entry.cultureKey(), entry.typeKey())
            );
            this.addRenderableWidget(infoBtn);
            this.pageButtons.add(infoBtn);
         }

         y += 16;
      }
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, 1610612736);
      int parchX = (this.width - 204) / 2;
      int parchY = (this.height - 220) / 2;
      PanelRenderHelper.renderTexturedBackground(graphics, PanelRenderHelper.PANEL_TEXTURE, parchX, parchY, 204, 220);
      int y = this.renderParchmentHeader(graphics, Component.translatable("gui.millenaire.new_village.title"), parchX, parchY, 204);
      int contentX = parchX + 16;
      int startIdx = this.currentPage * this.rowsPerPage;
      int endIdx = Math.min(startIdx + this.rowsPerPage, this.displayRows.size());

      for (int i = startIdx; i < endIdx; i++) {
         NewVillageScreen.DisplayRow row = this.displayRows.get(i);
         if (row instanceof NewVillageScreen.CultureHeaderRow header) {
            graphics.drawString(this.font, Component.literal(header.cultureName()).withStyle(s -> s.withBold(true)), contentX, y + 3, -11193600);
         }

         y += 16;
      }

      this.renderPageCounter(graphics, parchX, parchY, 204, 220);
      super.render(graphics, mouseX, mouseY, partialTick);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0) {
         int parchX = (this.width - 204) / 2;
         int parchY = (this.height - 220) / 2;
         int contentX = parchX + 16;
         int y = parchY + 42;
         int startIdx = this.currentPage * this.rowsPerPage;
         int endIdx = Math.min(startIdx + this.rowsPerPage, this.displayRows.size());

         for (int i = startIdx; i < endIdx; i++) {
            NewVillageScreen.DisplayRow row = this.displayRows.get(i);
            if (row instanceof NewVillageScreen.CultureHeaderRow header
               && mouseY >= y
               && mouseY < y + 16
               && mouseX >= contentX
               && mouseX < contentX + TEXT_WIDTH) {
               TravelBookNavHelper.openCulture(this, header.cultureKey());
               return true;
            }

            y += 16;
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   private record CultureHeaderRow(String cultureName, String cultureKey) implements NewVillageScreen.DisplayRow {
   }

   private sealed interface DisplayRow permits NewVillageScreen.CultureHeaderRow, NewVillageScreen.VillageTypeRow {
   }

   private record VillageTypeRow(VillageTypeListPayload.VillageTypeEntry entry) implements NewVillageScreen.DisplayRow {
   }
}
