package org.millenaire.fabric.firepit;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public final class FirePitScreen extends AbstractContainerScreen<FirePitMenu> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            "millenaire", "textures/gui/firepit.png");
    private static final int TEXTURE_SIZE = 256;
    private static final int[][] ARROWS = {{77, 22, 23, 31, 8}, {71, 28, 37, 14, 16}, {77, 42, 23, 31, 8}};

    public FirePitScreen(FirePitMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 175);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(TEXTURE, leftPos, topPos, imageWidth, imageHeight,
                0.0F, 0.0F, (float) imageWidth / TEXTURE_SIZE, (float) imageHeight / TEXTURE_SIZE);
        int burn = scaled(menu.getBurnTime(), menu.getTotalBurnTime(), 13);
        if (burn > 0) {
            blitRegion(graphics, 81, 54 + 12 - burn, 14, burn + 1, 176, 12 - burn, 14, burn + 1);
        }
        for (int i = 0; i < ARROWS.length; i++) {
            int[] arrow = ARROWS[i];
            int progress = scaled(menu.getCookTime(i), 200, arrow[2]);
            if (progress > 0) {
                blitRegion(graphics, arrow[0], arrow[1], progress, arrow[4],
                        176, arrow[3], progress, arrow[4]);
            }
        }
    }

    private void blitRegion(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                            int u, int v, int sourceWidth, int sourceHeight) {
        graphics.blit(TEXTURE, leftPos + x, topPos + y, width, height,
                (float) u / TEXTURE_SIZE, (float) v / TEXTURE_SIZE,
                (float) sourceWidth / TEXTURE_SIZE, (float) sourceHeight / TEXTURE_SIZE);
    }

    private static int scaled(int value, int total, int pixels) {
        return total <= 0 ? 0 : Math.min(pixels, value * pixels / total);
    }
}
