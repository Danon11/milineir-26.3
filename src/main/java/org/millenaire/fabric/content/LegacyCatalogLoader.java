package org.millenaire.fabric.content;

import org.millenaire.fabric.culture.CultureDescriptorLoader;
import org.millenaire.fabric.economy.LegacyGoodsCatalog;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Reads the deployed bundle, then applies custom files at the same relative paths. */
public final class LegacyCatalogLoader {
    private static final Pattern PLAN = Pattern.compile("(.+)_([A-Z])(\\d+)\\.png");
    private static final Set<String> ADDITIVE_UPGRADE_FIELDS = Set.of("male", "female", "visitor", "subbuilding",
            "tag", "cleartag", "forbiddentaginvillage", "requiredtag", "villagetag", "requiredvillagetag",
            "parenttag", "requiredparenttags", "translatedname");
    private LegacyCatalogLoader() {}

    public static LegacyContentCatalog loadGame(Path game) throws IOException {
        Path mods = game.toAbsolutePath().normalize().resolve("mods");
        return load(mods.resolve("millenaire"), mods.resolve("millenaire-custom"));
    }

    public static LegacyContentCatalog load(Path... roots) throws IOException {
        Map<String, Map<String, LegacyDocument>> cultures = new TreeMap<>();
        Map<String, LegacyDocument> global = new TreeMap<>();
        Map<String, Path> images = new TreeMap<>();
        Map<Integer, LegacyPalette.Point> colors = new LinkedHashMap<>();
        List<String> diagnostics = new ArrayList<>();
        LegacyGoodsCatalog goods = LegacyGoodsCatalog.empty();
        for (Path input : roots) {
            Path root = input.toAbsolutePath().normalize();
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) continue;
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsafe content root: " + root);
            Path palette = root.resolve("blocklist.txt");
            if (Files.exists(palette, LinkOption.NOFOLLOW_LINKS)) colors.putAll(LegacyPalette.read(palette).points());
            Path itemList = root.resolve("itemlist.txt");
            if (Files.exists(itemList, LinkOption.NOFOLLOW_LINKS))
                goods = goods.overlay(LegacyDocument.read(itemList, root.getFileName() + "/itemlist.txt"));
            List<Path> files;
            try (var walk = Files.walk(root)) {
                files = walk.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).sorted().toList();
            }
            for (Path file : files) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                String[] parts = relative.split("/", 3);
                if (parts.length == 3 && parts[0].equals("cultures")) {
                    String culture = parts[1];
                    CultureDescriptorLoader.validateCultureId(culture);
                    if (relative.endsWith(".txt")) {
                        cultures.computeIfAbsent(culture, ignored -> new TreeMap<>()).put(parts[2], LegacyDocument.read(file, relative));
                    } else if (parts[2].startsWith("buildings/") && relative.endsWith(".png")) {
                        images.put(culture + "/" + parts[2], file);
                    }
                } else if (relative.endsWith(".txt") && !relative.startsWith("languages/") && !relative.startsWith("help/")) {
                    global.put(relative, LegacyDocument.read(file, relative));
                }
            }
        }
        Map<String, LegacyBuildingPlan> plans = new TreeMap<>();
        for (var entry : images.entrySet()) {
            Path image = entry.getValue();
            var matcher = PLAN.matcher(image.getFileName().toString());
            if (!matcher.matches()) continue;
            String culture = entry.getKey().substring(0, entry.getKey().indexOf('/'));
            String relative = entry.getKey().substring(culture.length() + 1);
            String metadataPath = relative.substring(0, relative.lastIndexOf('/') + 1) + matcher.group(1) + "_" + matcher.group(2) + ".txt";
            LegacyDocument metadata = cultures.getOrDefault(culture, Map.of()).get(metadataPath);
            if (metadata == null) {
                diagnostics.add("Missing building metadata: " + culture + "/" + metadataPath);
                continue;
            }
            try {
                int upgrade = Integer.parseInt(matcher.group(3));
                Map<String, List<String>> parameters = planParameters(metadata, upgrade);
                LegacyBuildingPlan plan = new LegacyBuildingPlan(culture, matcher.group(1), matcher.group(2).charAt(0), upgrade,
                        integer(parameters, "width", 0), integer(parameters, "length", 0), integer(parameters, "startlevel", 0), image, parameters);
                if (plans.putIfAbsent(plan.id(), plan) != null) diagnostics.add("Duplicate plan ID: " + plan.id() + " at " + image);
            } catch (RuntimeException exception) {
                diagnostics.add("Invalid building metadata " + culture + "/" + metadataPath + ": " + exception.getMessage());
            }
        }
        Map<String, LegacyContentCatalog.Culture> result = new TreeMap<>();
        cultures.forEach((id, documents) -> result.put(id, new LegacyContentCatalog.Culture(id, documents)));
        diagnostics.addAll(goods.diagnostics());
        return new LegacyContentCatalog(result, global, plans, new LegacyPalette(colors), goods, diagnostics);
    }

    static Map<String, List<String>> planParameters(LegacyDocument document, int upgrade) {
        List<String> active = document.lines().stream().map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("//")).toList();
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (!active.isEmpty() && active.getFirst().contains("length:")) {
            for (int level = 0; level <= upgrade && level < active.size(); level++) {
                Map<String, List<String>> row = new LinkedHashMap<>();
                for (String parameter : active.get(level).split(";")) {
                    int colon = parameter.indexOf(':');
                    if (colon > 0) row.computeIfAbsent(parameter.substring(0, colon).trim().toLowerCase(Locale.ROOT), ignored -> new ArrayList<>())
                            .add(parameter.substring(colon + 1).trim());
                }
                result.putAll(row);
            }
        } else {
            applyPrefix(result, document, "building.");
            applyPrefix(result, document, "initial.");
            for (int level = 1; level <= upgrade; level++) {
                String prefix = "upgrade" + level + ".";
                document.fields().forEach((name, values) -> {
                    if (!name.startsWith(prefix)) return;
                    String key = name.substring(prefix.length());
                    if (ADDITIVE_UPGRADE_FIELDS.contains(key)) {
                        List<String> combined = new ArrayList<>(result.getOrDefault(key, List.of()));
                        combined.addAll(values);
                        result.put(key, List.copyOf(combined));
                    } else result.put(key, values);
                });
            }
        }
        return result;
    }

    private static void applyPrefix(Map<String, List<String>> result, LegacyDocument document, String prefix) {
        document.fields().forEach((name, values) -> {
            if (name.startsWith(prefix)) result.put(name.substring(prefix.length()), values);
        });
    }
    private static int integer(Map<String, List<String>> parameters, String key, int fallback) {
        List<String> values = parameters.get(key);
        return values == null || values.isEmpty() ? fallback : Integer.parseInt(values.getLast());
    }
}
