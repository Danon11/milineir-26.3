package org.millenaire.fabric.culture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Parsed, immutable contents of a legacy culture.txt descriptor. */
public final class CultureDescriptor {
    private final String id;
    private final Map<String, List<String>> fields;

    CultureDescriptor(String id, Map<String, List<String>> fields) {
        this.id = id;
        Map<String, List<String>> copy = new LinkedHashMap<>();
        fields.forEach((key, values) -> copy.put(key, List.copyOf(values)));
        this.fields = Collections.unmodifiableMap(copy);
    }

    public String id() {
        return id;
    }

    /** Returns every value for a key, in the order it appeared in the file. */
    public List<String> values(String key) {
        return fields.getOrDefault(normalizeKey(key), List.of());
    }

    /** Returns the first value for a key, if the descriptor defines it. */
    public Optional<String> firstValue(String key) {
        List<String> values = values(key);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    /** Returns all fields in first-seen key order, with repeated values in file order. */
    public Map<String, List<String>> fields() {
        return fields;
    }

    static String normalizeKey(String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }
}
