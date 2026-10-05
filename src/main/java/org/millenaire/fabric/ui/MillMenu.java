package org.millenaire.fabric.ui;

import com.google.gson.Gson;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * A Millénaire menu: a title and rows of text, each row with optional buttons that run a command. Clients with
 * the mod show it as a Minecraft-style screen ({@code MillMenuScreen}); other clients get the same content as
 * clickable chat.
 */
public record MillMenu(String title, String subtitle, List<Row> rows) {
    public enum Tone { NORMAL, GOOD, BAD, INFO }
    public record Button(String label, String command, Tone tone) {}
    public record Row(String text, boolean heading, List<Button> buttons) {
        public Row { buttons = buttons == null ? List.of() : List.copyOf(buttons); text = text == null ? "" : text; }
    }

    public MillMenu {
        subtitle = subtitle == null ? "" : subtitle;
        rows = List.copyOf(rows);
    }

    private static final Gson GSON = new Gson();

    public String toJson() { return GSON.toJson(this); }

    public static MillMenu fromJson(String json) { return GSON.fromJson(json, MillMenu.class); }

    public static Builder builder(String title) { return new Builder(title); }

    /** The same menu as chat text with clickable buttons. */
    public Component toChat() {
        MutableComponent chat = Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        if (!subtitle.isEmpty()) chat.append(Component.literal(" — " + subtitle).withStyle(ChatFormatting.GRAY));
        for (Row row : rows) {
            chat.append(Component.literal("\n"));
            if (!row.text().isEmpty())
                chat.append(Component.literal((row.heading() ? "" : " ") + row.text() + " ")
                        .withStyle(row.heading() ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
            for (Button button : row.buttons()) {
                ChatFormatting colour = switch (button.tone()) {
                    case GOOD -> ChatFormatting.GREEN;
                    case BAD -> ChatFormatting.RED;
                    case INFO -> ChatFormatting.AQUA;
                    default -> ChatFormatting.LIGHT_PURPLE;
                };
                chat.append(Component.literal("[" + button.label() + "]").withStyle(style -> style.withColor(colour)
                        .withClickEvent(new ClickEvent.RunCommand(button.command())))).append(Component.literal(" "));
            }
        }
        return chat;
    }

    public static final class Builder {
        private final String title;
        private String subtitle = "";
        private final List<Row> rows = new ArrayList<>();
        Builder(String title) { this.title = title; }
        public Builder subtitle(String text) { subtitle = text; return this; }
        public Builder heading(String text) { rows.add(new Row(text, true, List.of())); return this; }
        public Builder text(String text) { rows.add(new Row(text, false, List.of())); return this; }
        public Builder row(String text, Button... buttons) { rows.add(new Row(text, false, List.of(buttons))); return this; }
        public boolean isEmpty() { return rows.isEmpty(); }
        public MillMenu build() { return new MillMenu(title, subtitle, rows); }
    }

    public static Button button(String label, String command) { return new Button(label, command, Tone.NORMAL); }
    public static Button button(String label, String command, Tone tone) { return new Button(label, command, tone); }
}
