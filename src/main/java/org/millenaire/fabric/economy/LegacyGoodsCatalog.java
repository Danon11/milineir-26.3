package org.millenaire.fabric.economy;

import org.millenaire.fabric.content.LegacyDocument;
import net.minecraft.resources.Identifier;

import java.util.*;

/** Item aliases from itemlist.txt; later files override individual aliases. */
public record LegacyGoodsCatalog(Map<String, Good> goods, List<String> diagnostics) {
    public record Good(String key, String legacyId, int metadata) {
        public Good {
            key = key.trim().toLowerCase(Locale.ROOT);
            legacyId = legacyId.trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty() || legacyId.isEmpty() || metadata < -1
                    || Identifier.tryParse(legacyId.contains(":") ? legacyId : "minecraft:" + legacyId) == null)
                throw new IllegalArgumentException("Invalid goods definition");
        }
    }
    public LegacyGoodsCatalog {
        goods = Collections.unmodifiableMap(new LinkedHashMap<>(goods));
        diagnostics = List.copyOf(diagnostics);
    }
    public static LegacyGoodsCatalog empty() { return new LegacyGoodsCatalog(Map.of(), List.of()); }
    public Good require(String key) {
        Good good = goods.get(key.trim().toLowerCase(Locale.ROOT));
        if (good == null) throw new IllegalArgumentException("Unknown good: " + key);
        return good;
    }
    public LegacyGoodsCatalog overlay(LegacyDocument document) {
        Map<String, Good> result = new LinkedHashMap<>(goods);
        List<String> errors = new ArrayList<>(diagnostics);
        int lineNumber = 0;
        for (String raw : document.lines()) {
            lineNumber++;
            String line = raw.replace("\uFEFF", "").trim();
            if (line.isEmpty() || line.startsWith("//")) continue;
            String[] parts = line.split(";", -1);
            String key = parts[0].trim().toLowerCase(Locale.ROOT);
            try {
                if (parts.length < 3) throw new IllegalArgumentException("Expected key;item;metadata");
                result.put(key, new Good(key, parts[1], Integer.parseInt(parts[2].trim())));
            } catch (IllegalArgumentException exception) {
                result.remove(key); // A broken override must not silently fall back to a different item.
                errors.add(document.source() + ":" + lineNumber + ": " + exception.getMessage());
            }
        }
        return new LegacyGoodsCatalog(result, errors);
    }
}
