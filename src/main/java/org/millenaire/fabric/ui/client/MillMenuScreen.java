package org.millenaire.fabric.ui.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.millenaire.fabric.ui.MillMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows a {@link MillMenu} the way Minecraft draws its own containers: a light grey panel with a bevelled edge,
 * dark grey text, a sunken scrolling list and vanilla buttons. Buttons send their command and close the screen;
 * the server may answer with a fresh menu.
 */
public final class MillMenuScreen extends Screen {
    private static final int PANEL = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, SHADOW = 0xFF555555, OUTLINE = 0xFF000000,
            WELL = 0xFF8B8B8B, WELL_BODY = 0xFFD6D6D6, TEXT = 0xFF404040, HEADING = 0xFF7A4B12, SUBTITLE = 0xFF6F6F6F;
    private static final int LINE = 10, BUTTON_HEIGHT = 16, PADDING = 8;

    private final MillMenu menu;
    private int left, top, panelWidth, panelHeight, listTop, listBottom, listLeft, listWidth;
    private int scroll, contentHeight;

    private record Placed(Button button, int relativeY) {}
    private record TextLine(FormattedCharSequence text, int relativeY, int colour, boolean heading) {}
    private final List<Placed> buttons = new ArrayList<>();
    private final List<TextLine> lines = new ArrayList<>();

    public MillMenuScreen(MillMenu menu) {
        super(Component.literal(menu.title()));
        this.menu = menu;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(340, width - 20);
        panelHeight = Math.min(260, height - 20);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        listLeft = left + PADDING + 2;
        listWidth = panelWidth - 2 * PADDING - 4;
        listTop = top + (menu.subtitle().isEmpty() ? 22 : 32);
        listBottom = top + panelHeight - 30;
        layout();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + panelWidth / 2 - 50, top + panelHeight - 24, 100, 18).build());
        reposition();
    }

    /** Lays the rows out in list coordinates: wrapped text on the left, buttons right of it or on lines below. */
    private void layout() {
        buttons.clear();
        lines.clear();
        int y = 2;
        for (MillMenu.Row row : menu.rows()) {
            int buttonsWidth = 0;
            for (var b : row.buttons()) buttonsWidth += buttonWidth(b) + 3;
            boolean inline = !row.text().isEmpty() && buttonsWidth <= listWidth * 0.45;
            int textWidth = inline ? listWidth - buttonsWidth - 6 : listWidth - 6;
            if (row.heading() && y > 2) y += 4;
            int rowTop = y;
            if (!row.text().isEmpty()) {
                for (var part : font.split(Component.literal(row.text()), textWidth)) {
                    lines.add(new TextLine(part, y, row.heading() ? HEADING : TEXT, row.heading()));
                    y += LINE;
                }
                if (row.heading()) y += 2;
            }
            if (row.buttons().isEmpty()) continue;
            int x;
            int buttonY;
            if (inline) {
                x = listWidth - buttonsWidth;
                buttonY = rowTop - 1;
                y = Math.max(y, rowTop + BUTTON_HEIGHT + 1);
            } else {
                x = 4;
                buttonY = y;
                y += BUTTON_HEIGHT + 2;
            }
            for (var b : row.buttons()) {
                int w = buttonWidth(b);
                if (!inline && x + w > listWidth) { x = 4; buttonY = y; y += BUTTON_HEIGHT + 2; }
                String command = b.command().startsWith("/") ? b.command().substring(1) : b.command();
                Button widget = Button.builder(Component.literal(colour(b.tone()) + b.label()), pressed -> run(command))
                        .bounds(listLeft + x, 0, w, BUTTON_HEIGHT).build();
                buttons.add(new Placed(addRenderableWidget(widget), buttonY));
                x += w + 3;
            }
            y += 2;
        }
        contentHeight = y + 2;
    }

    private static String colour(MillMenu.Tone tone) {
        // Bright tones read well on the grey vanilla button, like Minecraft's own coloured button text.
        return switch (tone) { case GOOD -> "§a"; case BAD -> "§c"; case INFO -> "§b"; default -> ""; };
    }

    private int buttonWidth(MillMenu.Button button) { return Math.max(36, font.width(button.label()) + 12); }

    private void run(String command) {
        if (minecraft != null && minecraft.player != null) minecraft.player.connection.sendCommand(command);
        onClose();
    }

    private void reposition() {
        for (Placed placed : buttons) {
            int y = listTop + placed.relativeY() - scroll;
            placed.button().setY(y);
            boolean inside = y >= listTop && y + BUTTON_HEIGHT <= listBottom;
            placed.button().visible = inside;
            placed.button().active = inside;
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int max = Math.max(0, contentHeight - (listBottom - listTop));
        scroll = (int) Math.max(0, Math.min(max, scroll - scrollY * 12));
        reposition();
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        // Minecraft's container look: black outline, white top-left bevel, dark bottom-right bevel.
        g.fill(left, top, left + panelWidth, top + panelHeight, OUTLINE);
        g.fill(left + 1, top + 1, left + panelWidth - 1, top + panelHeight - 1, PANEL);
        g.fill(left + 1, top + 1, left + panelWidth - 2, top + 3, LIGHT);
        g.fill(left + 1, top + 1, left + 3, top + panelHeight - 2, LIGHT);
        g.fill(left + 3, top + panelHeight - 3, left + panelWidth - 1, top + panelHeight - 1, SHADOW);
        g.fill(left + panelWidth - 3, top + 3, left + panelWidth - 1, top + panelHeight - 1, SHADOW);
        // Sunken list well, like an inventory slot area.
        int x0 = listLeft - 2, y0 = listTop - 2, x1 = listLeft + listWidth + 2, y1 = listBottom + 2;
        g.fill(x0, y0, x1, y1, LIGHT);
        g.fill(x0, y0, x1 - 1, y1 - 1, SHADOW);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, WELL_BODY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.centeredText(font, title, left + panelWidth / 2, top + 8, TEXT);
        if (!menu.subtitle().isEmpty()) g.centeredText(font, Component.literal(menu.subtitle()), left + panelWidth / 2, top + 19, SUBTITLE);
        g.enableScissor(listLeft - 1, listTop, listLeft + listWidth + 1, listBottom);
        for (TextLine line : lines) {
            int y = listTop + line.relativeY() - scroll;
            if (y + LINE < listTop || y > listBottom) continue;
            g.text(font, line.text(), listLeft + 3, y + 2, line.colour(), false);
            if (line.heading()) g.fill(listLeft + 3, y + 11, listLeft + listWidth - 3, y + 12, WELL);
        }
        g.disableScissor();
        super.extractRenderState(g, mouseX, mouseY, delta);
        // Scroll bar when the list is longer than the well.
        int visible = listBottom - listTop;
        if (contentHeight > visible) {
            int barHeight = Math.max(12, visible * visible / contentHeight);
            int barTop = listTop + (visible - barHeight) * scroll / Math.max(1, contentHeight - visible);
            g.fill(listLeft + listWidth - 3, barTop, listLeft + listWidth, barTop + barHeight, SHADOW);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
