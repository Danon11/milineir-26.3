package org.millenaire.fabric.villager;

import org.millenaire.fabric.content.LegacyContentCatalog;
import org.millenaire.fabric.content.LegacyDocument;

import java.util.*;
import java.util.random.RandomGenerator;

/**
 * Appearance and identity rules of one legacy villager type ({@code cultures/<c>/villagers/**.txt}):
 * body model, skins, clothing sets per layer, base height, health and name lists.
 */
public record VillagerProfile(String culture, String type, Model model, boolean female, List<String> textures,
                              Map<String, List<List<String>>> clothes, float baseScale, int health,
                              String firstNameList, String familyNameList, List<String> goals, List<String> tags,
                              Map<String, Integer> startingInventory, List<String> bringBackHomeGoods, String defaultWeapon,
                              String maleChild, String femaleChild, Map<String, Integer> requiredGoods, List<String> collectGoods) {
    public static final String FREE_CLOTHES = "free";
    public static final String NATURAL_CLOTHES = "natural";
    private static final float SCALE_MIN = 0.8F, SCALE_VARIATION = 0.09F;

    public enum Model {
        MALE, FEMALE_SYMMETRICAL, FEMALE_ASYMMETRICAL;

        public static Model byId(int id) { return id >= 0 && id < values().length ? values()[id] : MALE; }
    }

    /** Result of rolling a new villager: texture paths are relative to {@code assets/millenaire/}. */
    public boolean child() { return tags.contains("child"); }
    public boolean canHaveChildren() { return female && !child() && (!maleChild.isEmpty() || !femaleChild.isEmpty()); }

    public record Appearance(String texture, Optional<String> cloth0, Optional<String> cloth1, float scale,
                             String firstName, String familyName) {
        public String fullName() { return familyName.isEmpty() ? firstName : firstName + " " + familyName; }
    }

    public VillagerProfile {
        textures = List.copyOf(textures);
        Map<String, List<List<String>>> copy = new LinkedHashMap<>();
        clothes.forEach((name, layers) -> copy.put(name, List.of(List.copyOf(layers.get(0)), List.copyOf(layers.get(1)))));
        clothes = Collections.unmodifiableMap(copy);
        goals = List.copyOf(goals);
        tags = List.copyOf(tags);
        startingInventory = Collections.unmodifiableMap(new LinkedHashMap<>(startingInventory));
        bringBackHomeGoods = List.copyOf(bringBackHomeGoods);
        defaultWeapon = defaultWeapon == null ? "" : defaultWeapon;
        maleChild = maleChild == null ? "" : maleChild;
        femaleChild = femaleChild == null ? "" : femaleChild;
        requiredGoods = requiredGoods == null ? Map.of() : Map.copyOf(requiredGoods);
        collectGoods = collectGoods == null ? List.of() : List.copyOf(collectGoods);
        if (textures.isEmpty()) throw new IllegalArgumentException("Villager type " + culture + "/" + type + " has no texture");
        if (!(baseScale > 0) || health <= 0) throw new IllegalArgumentException("Invalid scale or health for " + culture + "/" + type);
    }

    public String id() { return culture + "/" + type; }

    public static VillagerProfile from(String culture, String type, LegacyDocument document) {
        boolean female = document.first("gender", "male").trim().equalsIgnoreCase("female");
        String modelName = document.first("model", "").trim().toLowerCase(Locale.ROOT);
        Model model = !female ? Model.MALE : modelName.equals("femaleasymmetrical") ? Model.FEMALE_ASYMMETRICAL : Model.FEMALE_SYMMETRICAL;
        Map<String, List<List<String>>> clothes = new LinkedHashMap<>();
        for (String raw : document.values("clothes")) {
            String[] parts = raw.split(",");
            String name, texture;
            int layer = 0;
            if (parts.length >= 3) {
                name = parts[0].trim();
                layer = Integer.parseInt(parts[1].trim());
                texture = parts[2].trim();
            } else if (parts.length == 2) {
                name = parts[0].trim();
                texture = parts[1].trim();
            } else throw new IllegalArgumentException("Invalid clothes entry '" + raw + "' in " + culture + "/" + type);
            texture = texture.toLowerCase(Locale.ROOT); // asset files were lowercased to valid identifiers
            if (layer < 0 || layer > 1) throw new IllegalArgumentException("Invalid clothes layer in " + culture + "/" + type);
            clothes.computeIfAbsent(name.toLowerCase(Locale.ROOT), ignored -> List.of(new ArrayList<>(), new ArrayList<>())).get(layer).add(texture);
        }
        float scale = Float.parseFloat(last(document, "baseheight", "1"));
        int health = Integer.parseInt(last(document, "health", "20"));
        String firstNames = document.first("firstnamelist", female ? "women_names" : "men_names").trim().toLowerCase(Locale.ROOT);
        List<String> tags = document.values("tag").stream().map(tag -> tag.trim().toLowerCase(Locale.ROOT)).toList();
        String familyNames = document.first("familynamelist", tags.contains("noble") ? "noble_family_names" : "family_names").trim().toLowerCase(Locale.ROOT);
        Map<String, Integer> inventory = new LinkedHashMap<>();
        for (String value : document.values("startinginv")) {
            String[] parts = value.split(",");
            if (parts.length != 2) throw new IllegalArgumentException("Invalid startingInv '" + value + "' in " + culture + "/" + type);
            inventory.merge(parts[0].trim().toLowerCase(Locale.ROOT), org.millenaire.fabric.content.LegacyNumbers.product(parts[1]), Integer::sum);
        }
        // requiredGood=good,count: what the villager's household keeps in stock (fetched by getgoodshousehold).
        Map<String, Integer> required = new LinkedHashMap<>();
        for (String value : document.values("requiredgood")) {
            String[] parts = value.split(",");
            if (parts.length != 2) continue;
            try { required.merge(parts[0].trim().toLowerCase(Locale.ROOT), Integer.parseInt(parts[1].trim()), Math::max); }
            catch (NumberFormatException ignored) {}
        }
        List<String> bringBack = document.values("bringbackhomegood").stream().map(good -> good.trim().toLowerCase(Locale.ROOT)).toList();
        return new VillagerProfile(culture, type, model, female, document.values("texture").stream().map(texture -> texture.trim().toLowerCase(Locale.ROOT)).toList(),
                clothes, scale, health, firstNames, familyNames, document.values("goal").stream().map(goal -> goal.trim().toLowerCase(Locale.ROOT)).toList(), tags,
                inventory, bringBack, last(document, "defaultweapon", "").toLowerCase(Locale.ROOT),
                last(document, "malechild", "").toLowerCase(Locale.ROOT), last(document, "femalechild", "").toLowerCase(Locale.ROOT),
                required, document.values("collectgood").stream().map(good -> good.trim().toLowerCase(Locale.ROOT)).filter(good -> !good.isEmpty()).toList());
    }

    private static String last(LegacyDocument document, String key, String fallback) {
        List<String> values = document.values(key);
        return values.isEmpty() ? fallback : values.getLast().trim();
    }

    /** All villager types of a catalog keyed {@code culture/type}; invalid types are reported. */
    public static Map<String, VillagerProfile> all(LegacyContentCatalog catalog, List<String> diagnostics) {
        Map<String, VillagerProfile> result = new TreeMap<>();
        catalog.cultures().forEach((culture, content) -> content.category("villagers").forEach((path, document) -> {
            String type = path.substring(path.lastIndexOf('/') + 1, path.length() - 4).toLowerCase(Locale.ROOT);
            try {
                result.put(culture + "/" + type, from(culture, type, document));
            } catch (RuntimeException exception) {
                diagnostics.add(document.source() + ": " + exception.getMessage());
            }
        }));
        return result;
    }

    /**
     * Rolls a new appearance. Only the {@code free} clothing set is available without cloth items in the
     * villager's inventory; a {@code natural} layer (hair, beards) always overrides that layer.
     */
    public Appearance roll(RandomGenerator random, Map<String, List<String>> nameLists) {
        String texture = textures.get(random.nextInt(textures.size()));
        List<List<String>> chosen = clothes.get(FREE_CLOTHES);
        List<List<String>> natural = clothes.get(NATURAL_CLOTHES);
        List<Optional<String>> layers = new ArrayList<>(2);
        for (int layer = 0; layer < 2; layer++) {
            List<String> options = natural != null && !natural.get(layer).isEmpty() ? natural.get(layer)
                    : chosen != null ? chosen.get(layer) : List.of();
            layers.add(options.isEmpty() ? Optional.empty() : Optional.of(options.get(random.nextInt(options.size()))));
        }
        float scale = baseScale * (SCALE_MIN + random.nextFloat() * SCALE_VARIATION);
        String first = pick(nameLists.get(firstNameList), random, type);
        String family = pick(nameLists.get(familyNameList), random, "");
        return new Appearance(texture, layers.get(0), layers.get(1), scale, first, family);
    }

    private static String pick(List<String> names, RandomGenerator random, String fallback) {
        return names == null || names.isEmpty() ? fallback : names.get(random.nextInt(names.size()));
    }

    /** Name lists of a culture ({@code namelists/<list>.txt}, one name per line). */
    public static Map<String, List<String>> nameLists(LegacyContentCatalog.Culture culture) {
        Map<String, List<String>> result = new HashMap<>();
        culture.category("namelists").forEach((path, document) -> {
            String list = path.substring(path.lastIndexOf('/') + 1, path.length() - 4).toLowerCase(Locale.ROOT);
            result.put(list, document.lines().stream().map(line -> line.replace("﻿", "").trim())
                    .filter(line -> !line.isEmpty() && !line.startsWith("//")).toList());
        });
        return result;
    }
}
