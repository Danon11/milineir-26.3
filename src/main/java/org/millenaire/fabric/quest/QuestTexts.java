package org.millenaire.fabric.quest;

import org.millenaire.fabric.content.LegacyDocument;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quest strings from {@code languages/<lang>/quests_*.txt}, keyed {@code <quest>_<step>_<field>}.
 * Later roots override earlier ones per key; a missing translation falls back to English.
 */
public record QuestTexts(String language, Map<String, String> texts, Map<String, String> fallback) {
    public static final String FALLBACK_LANGUAGE = "en";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$([a-z0-9_]+)\\$", Pattern.CASE_INSENSITIVE);

    public enum Field {
        LABEL("label"), DESCRIPTION("description"), DESCRIPTION_SUCCESS("description_success"),
        DESCRIPTION_REFUSE("description_refuse"), DESCRIPTION_TIMEUP("description_timeup"), LISTING("listing");

        private final String suffix;
        Field(String suffix) { this.suffix = suffix; }
        public String suffix() { return suffix; }
    }

    public QuestTexts {
        language = language.toLowerCase(Locale.ROOT);
        texts = Map.copyOf(texts);
        fallback = Map.copyOf(fallback);
    }

    public static QuestTexts empty() { return new QuestTexts(FALLBACK_LANGUAGE, Map.of(), Map.of()); }

    public static QuestTexts load(String language, Path... roots) throws IOException {
        String normalized = language.toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z]{2,3}(_[a-z]{2,4})?")) throw new IllegalArgumentException("Invalid language: " + language);
        Map<String, String> fallback = read(FALLBACK_LANGUAGE, roots);
        Map<String, String> texts = normalized.equals(FALLBACK_LANGUAGE) ? fallback : read(normalized, roots);
        return new QuestTexts(normalized, texts, fallback);
    }

    private static Map<String, String> read(String language, Path... roots) throws IOException {
        Map<String, String> result = new HashMap<>();
        for (Path input : roots) {
            Path directory = input.toAbsolutePath().normalize().resolve("languages").resolve(language);
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) continue;
            List<Path> files;
            try (var list = Files.list(directory)) {
                files = list.filter(path -> {
                    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    return name.startsWith("quests") && name.endsWith(".txt") && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
                }).sorted().toList();
            }
            for (Path file : files) {
                var document = LegacyDocument.read(file, "languages/" + language + "/" + file.getFileName());
                for (String raw : document.lines()) {
                    String line = raw.replace("﻿", "").trim();
                    if (line.isEmpty() || line.startsWith("//")) continue;
                    int equals = line.indexOf('=');
                    if (equals <= 0) continue;
                    result.put(line.substring(0, equals).trim().toLowerCase(Locale.ROOT), line.substring(equals + 1).trim());
                }
            }
        }
        return result;
    }

    public Optional<String> text(String questKey, int step, Field field) {
        String key = (questKey + "_" + step + "_" + field.suffix()).toLowerCase(Locale.ROOT);
        String value = texts.get(key);
        if (value == null) value = fallback.get(key);
        return Optional.ofNullable(value);
    }

    /** Replaces {@code $name$} tokens; unknown tokens stay visible so missing context is noticed. */
    public static String render(String text, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1).toLowerCase(Locale.ROOT));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? matcher.group() : value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
