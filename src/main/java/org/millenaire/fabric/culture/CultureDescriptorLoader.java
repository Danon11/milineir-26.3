package org.millenaire.fabric.culture;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Loads legacy Millenaire culture descriptors without depending on Minecraft or Fabric APIs. */
public final class CultureDescriptorLoader {
    private static final Pattern CULTURE_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    private CultureDescriptorLoader() {
    }

    /**
     * Loads all {@code mods/millenaire/cultures/<culture>/culture.txt} files beneath the game
     * directory. Culture folders and results are processed in lexicographic order.
     */
    public static List<CultureDescriptor> loadAll(Path gameDirectory) throws IOException {
        Path culturesDirectory = gameDirectory.toAbsolutePath().normalize()
                .resolve("mods").resolve("millenaire").resolve("cultures").normalize();
        if (!Files.exists(culturesDirectory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        requireDirectory(culturesDirectory);

        List<Path> directories;
        try (Stream<Path> entries = Files.list(culturesDirectory)) {
            directories = entries.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted((left, right) -> left.getFileName().toString()
                            .compareTo(right.getFileName().toString()))
                    .toList();
        }

        List<CultureDescriptor> cultures = new ArrayList<>(directories.size());
        for (Path directory : directories) {
            String id = directory.getFileName().toString();
            validateCultureId(id);
            requireDirectory(directory);
            cultures.add(parse(id, directory.resolve("culture.txt")));
        }
        return List.copyOf(cultures);
    }

    /** Loads one culture by ID from the game's mods directory. */
    public static CultureDescriptor load(Path gameDirectory, String cultureId) throws IOException {
        validateCultureId(cultureId);
        Path modsDirectory = gameDirectory.toAbsolutePath().normalize().resolve("mods").normalize();
        Path directory = modsDirectory.resolve("millenaire").resolve("cultures")
                .resolve(cultureId).normalize();
        if (!directory.startsWith(modsDirectory)) {
            throw new IOException("Culture path escapes mods directory: " + cultureId);
        }
        requireDirectory(directory);
        return parse(cultureId, directory.resolve("culture.txt"));
    }

    /** Rejects culture IDs that are not safe single directory names in canonical lowercase form. */
    public static void validateCultureId(String cultureId) {
        if (cultureId == null || !CULTURE_ID.matcher(cultureId).matches()) {
            throw new IllegalArgumentException("Invalid culture ID: " + cultureId);
        }
    }

    private static CultureDescriptor parse(String id, Path descriptor) throws IOException {
        if (Files.isSymbolicLink(descriptor) || !Files.isRegularFile(descriptor, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Missing or unsafe culture descriptor: " + descriptor);
        }

        Map<String, List<String>> fields = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(descriptor, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                    continue;
                }

                int separator = line.indexOf('=');
                if (separator < 0) {
                    separator = line.indexOf(':');
                }
                if (separator < 0) {
                    throw malformedLine(descriptor, lineNumber, line);
                }

                String key = CultureDescriptor.normalizeKey(line.substring(0, separator));
                if (key.isEmpty()) {
                    throw malformedLine(descriptor, lineNumber, line);
                }
                String value = line.substring(separator + 1);
                fields.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
            }
        }
        return new CultureDescriptor(id, fields);
    }

    private static IOException malformedLine(Path descriptor, int lineNumber, String line) {
        return new IOException("Invalid culture descriptor line " + lineNumber + " in " + descriptor
                + ": " + line);
    }

    private static void requireDirectory(Path directory) throws IOException {
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Missing or unsafe culture directory: " + directory);
        }
    }
}
