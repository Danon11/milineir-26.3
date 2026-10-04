package org.millenaire.fabric.content;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Keeps the exact Forge block state, cost and special-point declarations. */
public record LegacyPalette(Map<Integer, Point> points) {
    public record Point(int color, String label, String block, String state, boolean secondPass,
                        String costItem, String costState, int costQuantity) {
        public boolean special() { return block.isEmpty(); }
    }

    public LegacyPalette { points = Collections.unmodifiableMap(new LinkedHashMap<>(points)); }

    public static LegacyPalette read(Path path) throws IOException {
        Map<Integer, Point> points = new LinkedHashMap<>();
        int lineNumber = 0;
        for (String raw : LegacyDocument.read(path, path.toString()).lines()) {
            lineNumber++;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("//")) continue;
            try {
                String[] fields = line.split(";", -1);
                if (fields.length != 5 && fields.length != 8) throw new IllegalArgumentException("expected 5 or 8 fields");
                String[] rgb = fields[4].trim().split("/", -1);
                if (rgb.length != 3) throw new IllegalArgumentException("expected RGB triplet");
                // Addition preserves the original handling of legacy entries containing 256.
                int color = (Integer.parseInt(rgb[0]) << 16) + (Integer.parseInt(rgb[1]) << 8) + Integer.parseInt(rgb[2]);
                Point point = new Point(color, fields[0].trim(), fields[1].trim(), fields[2].trim(),
                        Boolean.parseBoolean(fields[3].trim()), fields.length == 8 ? fields[5].trim() : "",
                        fields.length == 8 ? fields[6].trim() : "", fields.length == 8 ? Integer.parseInt(fields[7].trim()) : 1);
                points.put(color, point); // the legacy palette also lets later colors override earlier ones
            } catch (RuntimeException error) {
                throw new IOException("Invalid palette at " + path + ":" + lineNumber + ": " + error.getMessage(), error);
            }
        }
        return new LegacyPalette(points);
    }
}
