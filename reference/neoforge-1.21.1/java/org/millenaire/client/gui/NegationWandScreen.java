package org.millenaire.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.network.NegationWandConfirmPayload;
import org.millenaire.network.NegationWandPayload;

public class NegationWandScreen extends AbstractMillenaireScreen {
   private static final int PARCHMENT_WIDTH = 204;
   private static final int PARCHMENT_HEIGHT = 220;
   private final NegationWandPayload payload;

   public NegationWandScreen(NegationWandPayload payload) {
      super(Component.translatable("negationwand.title"));
      this.payload = payload;
   }

   protected void init() {
      super.init();
      int parchX = (this.width - 204) / 2;
      int parchY = (this.height - 220) / 2;
      int buttonY = parchY + 220 - 15 - 25;
      int centerX = parchX + 102;
      int buttonWidth = 80;
      int buttonGap = 10;
      this.addRenderableWidget(Button.builder(Component.translatable("negationwand.confirm"), btn -> {
         PacketDistributor.sendToServer(new NegationWandConfirmPayload(this.payload.villageId()), new CustomPacketPayload[0]);
         this.onClose();
      }).bounds(centerX - buttonWidth - buttonGap / 2, buttonY, buttonWidth, 20).build());
      this.addRenderableWidget(
         Button.builder(Component.translatable("negationwand.cancel"), btn -> this.onClose()).bounds(centerX + buttonGap / 2, buttonY, buttonWidth, 20).build()
      );
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, 1610612736);
      int parchX = (this.width - 204) / 2;
      int parchY = (this.height - 220) / 2;
      PanelRenderHelper.renderParchmentBackground(graphics, parchX, parchY, 204, 220);
      int y = this.renderParchmentHeader(graphics, this.getTitle(), parchX, parchY, 204);
      String displayName = this.payload.villageName().isEmpty() ? this.payload.villageTypeId() : this.payload.villageName();
      Component warningText = Component.translatable("negationwand.confirmmessage", new Object[]{displayName});
      int textW = 172;
      int textX = parchX + 16;

      for (FormattedCharSequence line : this.font.split(warningText, textW)) {
         graphics.drawString(this.font, line, textX, y, 4141088, false);
         y += 9 + 2;
      }

      super.render(graphics, mouseX, mouseY, partialTick);
   }
}
