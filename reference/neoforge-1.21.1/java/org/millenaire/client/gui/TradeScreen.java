package org.millenaire.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.millenaire.commerce.TradeAction;
import org.millenaire.commerce.TradeMenu;
import org.millenaire.item.MoneyHelper;

public class TradeScreen extends AbstractContainerScreen<TradeMenu> {
   private static final ResourceLocation TRADE_TEXTURE = ResourceLocation.fromNamespaceAndPath("millenaire", "textures/gui/trade.png");
   private static final int LABEL_COLOR = 4210752;
   private static final int COLOR_WHITE = 16777215;
   private static final int COLOR_GRAY = 5592405;
   private static final int COLOR_RED = 16733525;
   private static final int COLOR_GREEN = 5635925;
   private static final int COLOR_YELLOW = 13421568;
   private static final int DARK_OVERLAY = Integer.MIN_VALUE;
   private static final int GRID_COLS = 13;
   private static final int CELL_SPACING = 18;
   private static final int VISIBLE_ROWS = 2;
   private static final int SELL_GRID_Y = 32;
   private static final int BUY_GRID_Y = 86;
   private static final int GRID_X = 8;
   private static final int SELL_ARROW_Y = 68;
   private static final int BUY_ARROW_Y = 122;
   private static final int ARROW_UP_X = 216;
   private static final int ARROW_DOWN_X = 230;
   private static final int ARROW_W = 11;
   private static final int ARROW_H = 7;
   private static final int ARROW_DISABLED_U = 5;
   private static final int ARROW_DISABLED_V = 5;
   private static final int DONATION_X = 8;
   private static final int DONATION_Y = 122;
   private static final int DONATION_ICON_SIZE = 16;
   private int sellRowOffset = 0;
   private int buyRowOffset = 0;
   private List<TradeMenu.ClientGoodEntry> sellGoods = List.of();
   private List<TradeMenu.ClientGoodEntry> buyGoods = List.of();
   private int knownGoodsVersion = -1;

   public TradeScreen(TradeMenu menu, Inventory playerInventory, Component title) {
      super(menu, playerInventory, title);
      this.imageWidth = 248;
      this.imageHeight = 222;
      this.inventoryLabelY = this.imageHeight - 96 + 2;
      this.inventoryLabelX = 44;
   }

   protected void init() {
      super.init();
      this.refreshGoodLists();
      this.addRenderableWidget(
         Button.builder(
               Component.translatable("gui.millenaire.common.help_btn"),
               button -> TravelBookNavHelper.openCulture(this, ((TradeMenu)this.menu).getCultureKey())
            )
            .bounds(this.leftPos + this.imageWidth - 20, this.topPos + 4, 16, 16)
            .build()
      );
   }

   private void refreshGoodLists() {
      this.knownGoodsVersion = ((TradeMenu)this.menu).getGoodsVersion();
      this.sellGoods = new ArrayList<>();
      this.buyGoods = new ArrayList<>();

      for (TradeMenu.ClientGoodEntry entry : ((TradeMenu)this.menu).getClientGoods()) {
         if (entry.isSelling()) {
            this.sellGoods.add(entry);
         } else {
            this.buyGoods.add(entry);
         }
      }
   }

   protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
      int x = this.leftPos;
      int y = this.topPos;
      graphics.blit(TRADE_TEXTURE, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
      if (this.sellRowOffset == 0) {
         graphics.blit(TRADE_TEXTURE, x + 216, y + 68, 5.0F, 5.0F, 11, 7, 256, 256);
      }

      int sellTotalRows = (this.sellGoods.size() + 13 - 1) / 13;
      if (this.sellRowOffset >= sellTotalRows - 2) {
         graphics.blit(TRADE_TEXTURE, x + 230, y + 68, 5.0F, 5.0F, 11, 7, 256, 256);
      }

      if (this.buyRowOffset == 0) {
         graphics.blit(TRADE_TEXTURE, x + 216, y + 122, 5.0F, 5.0F, 11, 7, 256, 256);
      }

      int buyTotalRows = (this.buyGoods.size() + 13 - 1) / 13;
      if (this.buyRowOffset >= buyTotalRows - 2) {
         graphics.blit(TRADE_TEXTURE, x + 230, y + 122, 5.0F, 5.0F, 11, 7, 256, 256);
      }

      boolean donating = ((TradeMenu)this.menu).isDonationMode();
      if (!donating) {
         graphics.blit(TRADE_TEXTURE, x + 8, y + 122, 0.0F, 238.0F, 16, 16, 256, 256);
         graphics.blit(TRADE_TEXTURE, x + 8 + 16, y + 122, 16.0F, 222.0F, 16, 16, 256, 256);
      } else {
         graphics.blit(TRADE_TEXTURE, x + 8, y + 122, 0.0F, 222.0F, 16, 16, 256, 256);
         graphics.blit(TRADE_TEXTURE, x + 8 + 16, y + 122, 16.0F, 238.0F, 16, 16, 256, 256);
      }
   }

   protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
      String titleText = ((TradeMenu)this.menu).getBuildingName() + " - " + ((TradeMenu)this.menu).getVillageName();
      int maxTitleWidth = this.imageWidth - 16 - 20;
      if (this.font.width(titleText) > maxTitleWidth) {
         String ellipsis = "...";
         int ellipsisWidth = this.font.width(ellipsis);
         StringBuilder truncated = new StringBuilder();

         for (int i = 0; i < titleText.length() && this.font.width(truncated.toString() + titleText.charAt(i)) + ellipsisWidth <= maxTitleWidth; i++) {
            truncated.append(titleText.charAt(i));
         }

         titleText = truncated + ellipsis;
      }

      graphics.drawString(this.font, titleText, 8, 6, 4210752, false);
      if (!this.sellGoods.isEmpty()) {
         String sellHeader = Component.translatable("gui.millenaire.trade.selling").getString();
         graphics.drawString(this.font, sellHeader, 8, 22, 4210752, false);
      }

      if (!this.buyGoods.isEmpty()) {
         String buyHeader = Component.translatable("gui.millenaire.trade.buying").getString();
         graphics.drawString(this.font, buyHeader, 8, 76, 4210752, false);
      }

      String invLabel = Component.translatable("container.inventory").getString();
      graphics.drawString(this.font, invLabel, 44, this.imageHeight - 96 + 2, 4210752, false);
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      if (((TradeMenu)this.menu).getGoodsVersion() != this.knownGoodsVersion) {
         this.refreshGoodLists();
      }

      super.render(graphics, mouseX, mouseY, partialTick);
      int x = this.leftPos;
      int y = this.topPos;
      if (!this.sellGoods.isEmpty()) {
         this.renderGoodGrid(graphics, this.sellGoods, x + 8, y + 32, mouseX, mouseY, true, this.sellRowOffset);
      }

      if (!this.buyGoods.isEmpty()) {
         this.renderGoodGrid(graphics, this.buyGoods, x + 8, y + 86, mouseX, mouseY, false, this.buyRowOffset);
      }

      this.renderGoodTooltips(graphics, mouseX, mouseY);
      this.renderDonationTooltips(graphics, mouseX, mouseY);
      this.renderTooltip(graphics, mouseX, mouseY);
   }

   private void renderGoodGrid(
      GuiGraphics graphics, List<TradeMenu.ClientGoodEntry> goods, int startX, int startY, int mouseX, int mouseY, boolean isSellSection, int rowOffset
   ) {
      int playerRep = ((TradeMenu)this.menu).getPlayerReputation();
      int balance = MoneyHelper.getTotalDeniers(this.minecraft.player.getInventory());
      int startIndex = rowOffset * 13;
      int endIndex = Math.min(goods.size(), startIndex + 26);

      for (int i = startIndex; i < endIndex; i++) {
         int localIdx = i - startIndex;
         int col = localIdx % 13;
         int row = localIdx / 13;
         int cellX = startX + col * 18;
         int cellY = startY + row * 18;
         TradeMenu.ClientGoodEntry entry = goods.get(i);
         ItemStack displayStack = new ItemStack(entry.item());
         boolean unavailable = false;
         if (isSellSection) {
            if (playerRep < entry.minReputation()) {
               unavailable = true;
            }

            if (balance < entry.sellingPrice()) {
               unavailable = true;
            }

            if (!entry.autoGenerate() && entry.stock() <= 0) {
               unavailable = true;
            }
         } else {
            if (!this.playerHasItem(entry)) {
               unavailable = true;
            }

            if (entry.targetQuantity() > 0 && entry.stock() >= entry.targetQuantity()) {
               unavailable = true;
            }
         }

         graphics.renderItem(displayStack, cellX + 1, cellY + 1);
         int displayCount;
         if (isSellSection) {
            displayCount = entry.autoGenerate() ? 99 : Math.max(Math.min(entry.stock(), 99), 1);
         } else {
            displayCount = Math.max(Math.min(this.countPlayerItem(entry), 99), 1);
         }

         if (displayCount > 1) {
            ItemStack countStack = new ItemStack(entry.item(), displayCount);
            graphics.renderItemDecorations(this.font, countStack, cellX + 1, cellY + 1);
         }

         if (unavailable) {
            graphics.fill(cellX + 1, cellY + 1, cellX + 17, cellY + 17, Integer.MIN_VALUE);
         }
      }
   }

   private void renderGoodTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
      int x = this.leftPos;
      int y = this.topPos;
      int playerRep = ((TradeMenu)this.menu).getPlayerReputation();
      int balance = MoneyHelper.getTotalDeniers(this.minecraft.player.getInventory());
      if (!this.sellGoods.isEmpty()) {
         int idx = this.findIndexInVisibleGrid(this.sellGoods, x + 8, y + 32, mouseX, mouseY, this.sellRowOffset);
         if (idx >= 0) {
            List<Component> tooltip = this.buildTooltip(this.sellGoods.get(idx), true, playerRep, balance);
            graphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
            return;
         }
      }

      if (!this.buyGoods.isEmpty()) {
         int idx = this.findIndexInVisibleGrid(this.buyGoods, x + 8, y + 86, mouseX, mouseY, this.buyRowOffset);
         if (idx >= 0) {
            List<Component> tooltip = this.buildTooltip(this.buyGoods.get(idx), false, playerRep, balance);
            graphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
            return;
         }
      }
   }

   private void renderDonationTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
      int x = this.leftPos;
      int y = this.topPos;
      int dx = mouseX - x;
      int dy = mouseY - y;
      if (dy >= 122 && dy <= 138) {
         if (dx >= 8 && dx <= 24) {
            graphics.renderTooltip(this.font, Component.translatable("ui.trade_buying"), mouseX, mouseY);
         } else if (dx >= 24 && dx <= 40) {
            graphics.renderTooltip(this.font, Component.translatable("ui.trade_donation"), mouseX, mouseY);
         }
      }
   }

   private int findIndexInVisibleGrid(List<TradeMenu.ClientGoodEntry> goods, int startX, int startY, int mouseX, int mouseY, int rowOffset) {
      int startIndex = rowOffset * 13;
      int endIndex = Math.min(goods.size(), startIndex + 26);

      for (int i = startIndex; i < endIndex; i++) {
         int localIdx = i - startIndex;
         int col = localIdx % 13;
         int row = localIdx / 13;
         int cellX = startX + col * 18;
         int cellY = startY + row * 18;
         if (mouseX >= cellX && mouseX < cellX + 18 && mouseY >= cellY && mouseY < cellY + 18) {
            return i;
         }
      }

      return -1;
   }

   private List<Component> buildTooltip(TradeMenu.ClientGoodEntry entry, boolean isSellSection, int playerRep, int balance) {
      List<Component> lines = new ArrayList<>();
      ItemStack stack = new ItemStack(entry.item());
      lines.add(stack.getHoverName());
      if (isSellSection) {
         lines.add(
            Component.translatable("gui.millenaire.trade.price_line", new Object[]{MoneyHelper.formatPrice(entry.sellingPrice())})
               .withStyle(s -> s.withColor(16777215))
         );
         if (!entry.autoGenerate()) {
            int availableStock = Math.min(Math.max(0, entry.stock()), 99);
            int stockColor = availableStock > 0 ? 5635925 : 16733525;
            lines.add(Component.translatable("gui.millenaire.trade.stock", new Object[]{availableStock}).withStyle(s -> s.withColor(stockColor)));
            if (availableStock <= 0) {
               lines.add(Component.translatable("gui.millenaire.trade.out_of_stock").withStyle(s -> s.withColor(16733525)));
            }
         }

         if (balance < entry.sellingPrice()) {
            int missing = entry.sellingPrice() - balance;
            lines.add(
               Component.translatable("gui.millenaire.trade.not_enough_money", new Object[]{MoneyHelper.formatPrice(missing)})
                  .withStyle(s -> s.withColor(16733525))
            );
         }

         if (playerRep < entry.minReputation()) {
            lines.add(
               Component.translatable("gui.millenaire.trade.reputation_too_low", new Object[]{String.valueOf(entry.minReputation())})
                  .withStyle(s -> s.withColor(16733525))
            );
         }
      } else {
         if (((TradeMenu)this.menu).isDonationMode()) {
            lines.add(Component.translatable("gui.millenaire.trade.donating").withStyle(s -> s.withColor(13421568)));
            lines.add(Component.translatable("gui.millenaire.trade.donation_rep_bonus").withStyle(s -> s.withColor(5635925)));
         } else {
            lines.add(
               Component.translatable("gui.millenaire.trade.price_line", new Object[]{MoneyHelper.formatPrice(entry.buyingPrice())})
                  .withStyle(s -> s.withColor(16777215))
            );
         }

         if (entry.targetQuantity() > 0 && entry.stock() >= entry.targetQuantity()) {
            lines.add(Component.translatable("gui.millenaire.trade.village_has_enough").withStyle(s -> s.withColor(16733525)));
         }
      }

      if (isSellSection) {
         int repGain = entry.sellingPrice();
         lines.add(Component.translatable("gui.millenaire.trade.rep_gain", new Object[]{String.valueOf(repGain)}).withStyle(s -> s.withColor(5635925)));
      } else {
         int repGain = entry.buyingPrice();
         if (((TradeMenu)this.menu).isDonationMode()) {
            repGain *= 4;
         }

         lines.add(Component.translatable("gui.millenaire.trade.rep_gain", new Object[]{String.valueOf(repGain)}).withStyle(s -> s.withColor(5635925)));
      }

      lines.add(Component.translatable("gui.millenaire.trade.click_hint").withStyle(s -> s.withColor(5592405).withItalic(true)));
      return lines;
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      int mx = (int)mouseX;
      int my = (int)mouseY;
      int x = this.leftPos;
      int y = this.topPos;
      if (!this.sellGoods.isEmpty()) {
         int sellStartY = y + 32;
         int sellEndY = sellStartY + 36;
         if (mx >= x + 8 && mx < x + 8 + 234 && my >= sellStartY && my < sellEndY) {
            int maxRow = Math.max(0, (this.sellGoods.size() + 13 - 1) / 13 - 2);
            this.sellRowOffset = Math.max(0, Math.min(maxRow, this.sellRowOffset - (int)Math.signum(scrollY)));
            return true;
         }
      }

      if (!this.buyGoods.isEmpty()) {
         int buyStartY = y + 86;
         int buyEndY = buyStartY + 36;
         if (mx >= x + 8 && mx < x + 8 + 234 && my >= buyStartY && my < buyEndY) {
            int maxRow = Math.max(0, (this.buyGoods.size() + 13 - 1) / 13 - 2);
            this.buyRowOffset = Math.max(0, Math.min(maxRow, this.buyRowOffset - (int)Math.signum(scrollY)));
            return true;
         }
      }

      return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0) {
         int dx = (int)mouseX - this.leftPos;
         int dy = (int)mouseY - this.topPos;
         if (dy >= 68 && dy <= 75) {
            if (dx >= 216 && dx <= 227) {
               if (this.sellRowOffset > 0) {
                  this.sellRowOffset--;
                  return true;
               }
            } else if (dx >= 230 && dx <= 241) {
               int maxRow = Math.max(0, (this.sellGoods.size() + 13 - 1) / 13 - 2);
               if (this.sellRowOffset < maxRow) {
                  this.sellRowOffset++;
                  return true;
               }
            }
         }

         if (dy >= 122 && dy <= 129) {
            if (dx >= 216 && dx <= 227) {
               if (this.buyRowOffset > 0) {
                  this.buyRowOffset--;
                  return true;
               }
            } else if (dx >= 230 && dx <= 241) {
               int maxRow = Math.max(0, (this.buyGoods.size() + 13 - 1) / 13 - 2);
               if (this.buyRowOffset < maxRow) {
                  this.buyRowOffset++;
                  return true;
               }
            }
         }

         if (dy >= 122 && dy <= 138) {
            if (dx >= 8 && dx <= 24) {
               if (((TradeMenu)this.menu).isDonationMode()) {
                  ((TradeMenu)this.menu).toggleDonationModeClient();
                  this.sendButtonClick(TradeMenu.getToggleDonationButtonId());
                  return true;
               }
            } else if (dx >= 24 && dx <= 40 && !((TradeMenu)this.menu).isDonationMode()) {
               ((TradeMenu)this.menu).toggleDonationModeClient();
               this.sendButtonClick(TradeMenu.getToggleDonationButtonId());
               return true;
            }
         }
      }

      int[] result = this.findGoodAndSection((int)mouseX, (int)mouseY);
      if (result != null) {
         boolean selling = result[0] == 1;
         int globalIndex = result[1];
         TradeMenu.ClientGoodEntry entry = selling ? this.sellGoods.get(globalIndex) : this.buyGoods.get(globalIndex);
         int menuIndex = this.findGlobalIndex(entry);
         if (menuIndex >= 0) {
            int qty = this.getClickQuantity(button);
            TradeAction action = TradeAction.fromDirectionAndQuantity(selling, qty);
            if (action == null) {
               return super.mouseClicked(mouseX, mouseY, button);
            }

            this.sendButtonClick(action.toButtonId(menuIndex));
            return true;
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   private void sendButtonClick(int buttonId) {
      if (this.minecraft != null && this.minecraft.gameMode != null) {
         this.minecraft.gameMode.handleInventoryButtonClick(((TradeMenu)this.menu).containerId, buttonId);
      }
   }

   private int getClickQuantity(int button) {
      boolean shift = hasShiftDown();
      if (shift) {
         return 64;
      } else {
         return button == 1 ? 8 : 1;
      }
   }

   private int[] findGoodAndSection(int mouseX, int mouseY) {
      int x = this.leftPos;
      int y = this.topPos;
      if (!this.sellGoods.isEmpty()) {
         int idx = this.findIndexInVisibleGrid(this.sellGoods, x + 8, y + 32, mouseX, mouseY, this.sellRowOffset);
         if (idx >= 0) {
            return new int[]{1, idx};
         }
      }

      if (!this.buyGoods.isEmpty()) {
         int idx = this.findIndexInVisibleGrid(this.buyGoods, x + 8, y + 86, mouseX, mouseY, this.buyRowOffset);
         if (idx >= 0) {
            return new int[]{0, idx};
         }
      }

      return null;
   }

   private int findGlobalIndex(TradeMenu.ClientGoodEntry entry) {
      List<TradeMenu.ClientGoodEntry> allGoods = ((TradeMenu)this.menu).getClientGoods();

      for (int i = 0; i < allGoods.size(); i++) {
         TradeMenu.ClientGoodEntry candidate = allGoods.get(i);
         if (candidate.id().equals(entry.id()) && candidate.isSelling() == entry.isSelling()) {
            return i;
         }
      }

      return -1;
   }

   private int countPlayerItem(TradeMenu.ClientGoodEntry entry) {
      if (this.minecraft != null && this.minecraft.player != null) {
         Inventory inventory = this.minecraft.player.getInventory();
         int total = 0;
         TagKey<Item> tag = this.resolveTag(entry);

         for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && this.matchesEntry(stack, entry, tag) && !isDamaged(stack)) {
               total += stack.getCount();
            }
         }

         return total;
      } else {
         return 0;
      }
   }

   private boolean playerHasItem(TradeMenu.ClientGoodEntry entry) {
      if (this.minecraft != null && this.minecraft.player != null) {
         Inventory inventory = this.minecraft.player.getInventory();
         TagKey<Item> tag = this.resolveTag(entry);

         for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && this.matchesEntry(stack, entry, tag) && !isDamaged(stack)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static boolean isDamaged(ItemStack stack) {
      return stack.isDamageableItem() && stack.isDamaged();
   }

   @Nullable
   private TagKey<Item> resolveTag(TradeMenu.ClientGoodEntry entry) {
      if (entry.tagId() == null) {
         return null;
      }

      String raw = entry.tagId();
      String tagPath = raw.startsWith("#") ? raw.substring(1) : raw;
      return TagKey.create(Registries.ITEM, ResourceLocation.parse(tagPath));
   }

   private boolean matchesEntry(ItemStack stack, TradeMenu.ClientGoodEntry entry, @Nullable TagKey<Item> tag) {
      return tag != null ? stack.is(tag) : stack.is(entry.item());
   }

   public boolean isPauseScreen() {
      return false;
   }
}
