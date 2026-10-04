package org.millenaire.fabric.content;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Immutable lazy PNG plan source. Coordinates reproduce PngPlanLoader's mirrored floor layout. */
public record LegacyBuildingPlan(String culture, String key, char variation, int upgrade,
                                 int width, int length, int startLevel, Path image,
                                 Map<String, List<String>> parameters) {
    public LegacyBuildingPlan {
        if (width <= 0 || length <= 0 || width > 2048 || length > 2048) throw new IllegalArgumentException("Invalid plan dimensions");
        Map<String, List<String>> copy = new LinkedHashMap<>();
        parameters.forEach((name, values) -> copy.put(name, List.copyOf(values)));
        parameters = Collections.unmodifiableMap(copy);
    }

    public String id() { return culture + ":" + key + "_" + variation + upgrade; }

    public record Position(int x, int y, int z) {}

    /** Floor length runs along world X; PNG floor width runs along world Z. */
    public Position worldPosition(int originX, int originY, int originZ, int x, int y, int z, int orientation) {
        int dx = z - length / 2, dz = x - width / 2;
        int worldY = originY + startLevel + y;
        return switch (orientation) {
            case 0 -> new Position(originX + dx, worldY, originZ + dz);
            case 1 -> new Position(originX + dz, worldY, originZ - dx - 1);
            case 2 -> new Position(originX - dx - 1, worldY, originZ - dz - 1);
            case 3 -> new Position(originX - dz - 1, worldY, originZ + dx);
            default -> throw new IllegalArgumentException("Orientation must be 0..3");
        };
    }

    public Map<String, List<Position>> servicePoints(Decoded decoded, LegacyPalette palette,
                                                   int originX, int originY, int originZ, int orientation) throws IOException {
        if (decoded.width() != width || decoded.length() != length) throw new IOException("Decoded plan dimensions do not match " + id());
        if (orientation < 0 || orientation > 3) throw new IllegalArgumentException("Orientation must be 0..3");
        Map<String, List<Position>> points = new LinkedHashMap<>();
        Set<String> terrain = Set.of("empty", "preserveground", "allbuttrees", "grass");
        for (int y = 0; y < decoded.floors(); y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    var point = palette.points().get(decoded.colorAt(x, y, z));
                    if (point == null) throw new IOException("Unknown palette color in " + id());
                    if (point.special() && !terrain.contains(point.label())) {
                        points.computeIfAbsent(point.label(), ignored -> new ArrayList<>())
                                .add(worldPosition(originX, originY, originZ, x, y, z, orientation));
                    }
                }
            }
        }
        Map<String, List<Position>> result = new LinkedHashMap<>();
        points.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(result);
    }

    public Decoded decode(LegacyPalette palette) throws IOException {
        if (java.nio.file.Files.isSymbolicLink(image)) throw new IOException("Unsafe plan image: " + image);
        BufferedImage png = ImageIO.read(image.toFile());
        if (png == null) throw new IOException("Unreadable PNG: " + image);
        if (png.getHeight() != length || (png.getWidth() + 1) % (width + 1) != 0) {
            throw new IOException("PNG dimensions do not match metadata for " + id());
        }
        int floors = (png.getWidth() + 1) / (width + 1);
        long cells = (long) floors * width * length;
        if (floors <= 0 || floors > 1024 || cells > 16_000_000) throw new IOException("Plan too large: " + id());
        int[] colors = new int[(int) cells];
        Map<Integer, Integer> unknown = new TreeMap<>();
        for (int y = 0; y < floors; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    int argb = png.getRGB(y * (width + 1) + width - x - 1, z);
                    int color = (argb >>> 24) == 255 ? argb & 0xffffff : 0xffffff;
                    if (!palette.points().containsKey(color)) unknown.merge(color, 1, Integer::sum);
                    colors[(y * length + z) * width + x] = color;
                }
            }
        }
        return new Decoded(width, length, floors, colors, unknown);
    }

    public static final class Decoded {
        private final int width, length, floors;
        private final int[] colors;
        private final Map<Integer, Integer> unknownColors;
        private Decoded(int width, int length, int floors, int[] colors, Map<Integer, Integer> unknown) {
            this.width = width; this.length = length; this.floors = floors; this.colors = colors;
            this.unknownColors = Collections.unmodifiableMap(new TreeMap<>(unknown));
        }
        public int width() { return width; }
        public int length() { return length; }
        public int floors() { return floors; }
        public Map<Integer, Integer> unknownColors() { return unknownColors; }
        public int colorAt(int x, int y, int z) {
            if (x < 0 || x >= width || y < 0 || y >= floors || z < 0 || z >= length) throw new IndexOutOfBoundsException();
            return colors[(y * length + z) * width + x];
        }
    }
}
