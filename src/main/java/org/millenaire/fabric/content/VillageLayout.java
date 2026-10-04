package org.millenaire.fabric.content;

import org.millenaire.fabric.content.LegacyBuildingPlan.Position;

import java.util.*;

/** Deterministic manual layout on a common ground plane. No world queries or writes. */
public final class VillageLayout {
    public static final int DEFAULT_RADIUS = 80;
    public static final int MAX_RADIUS = 256;
    public static final int MAX_BUILDINGS = 1024;
    /** Extra search distance for start buildings that do not fit inside a walled village. */
    static final int OVERFLOW = 32;

    public record Bounds(int minX, int minZ, int maxX, int maxZ) {
        public Bounds {
            if (minX > maxX || minZ > maxZ) throw new IllegalArgumentException("Invalid footprint");
        }
        public boolean intersects(Bounds other) {
            return minX <= other.maxX && maxX >= other.minX && minZ <= other.maxZ && maxZ >= other.minZ;
        }
        public boolean within(Position centre, int radius) {
            return minX >= centre.x() - radius && maxX <= centre.x() + radius
                    && minZ >= centre.z() - radius && maxZ <= centre.z() + radius;
        }
    }

    /** Why a building is in the layout: the centre, a declared start building, a wall piece, or a sub-building overlay. */
    public enum Role { CENTRE, START, WALL, SUB }

    public record Building(LegacyBuildingPlan plan, Position origin, int rotation, boolean centre, Bounds reservedArea, Role role) {
        public Building(LegacyBuildingPlan plan, Position origin, int rotation, boolean centre, Bounds reservedArea) {
            this(plan, origin, rotation, centre, reservedArea, centre ? Role.CENTRE : Role.START);
        }
    }
    public record Layout(VillageTypeDefinition type, Position origin, long seed, int radius,
                         List<Building> buildings, List<String> issues) {
        public Layout { buildings = List.copyOf(buildings); issues = List.copyOf(issues); }
        public boolean complete() {
            return issues.isEmpty() && buildings.stream().filter(b -> b.role() == Role.CENTRE || b.role() == Role.START).count()
                    == 1 + type.startBuildings().size();
        }
    }

    private VillageLayout() {}

    public static Layout create(LegacyContentCatalog catalog, VillageTypeDefinition type, Position origin, long seed) {
        List<Building> buildings = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        int radius;
        try {
            radius = integer(type.source().fields(), "radius", DEFAULT_RADIUS);
            if (radius < 1 || radius > MAX_RADIUS) throw new IllegalArgumentException("Village radius must be 1.." + MAX_RADIUS);
            if (type.startBuildings().size() + 1 > MAX_BUILDINGS) throw new IllegalArgumentException("Too many starting buildings (limit " + MAX_BUILDINGS + ")");
            if (Math.abs((long) origin.x()) + radius + 4096 > Integer.MAX_VALUE
                    || Math.abs((long) origin.z()) + radius + 4096 > Integer.MAX_VALUE)
                throw new IllegalArgumentException("Origin exceeds coordinate range");
        } catch (IllegalArgumentException exception) {
            return new Layout(type, origin, seed, 0, List.of(), List.of(exception.getMessage()));
        }
        if (type.centre().isBlank()) {
            String custom = type.source().first("customcentre", "");
            String issue = custom.isBlank() ? "Village has no centre declaration" : "Custom village centre is not supported yet: " + custom;
            return new Layout(type, origin, seed, radius, List.of(), List.of(issue));
        }
        var random = new Random(seed);
        int usedRadius = radius;
        boolean walled = !type.source().first("innerwalltype", "").isBlank();
        List<String> references = new ArrayList<>();
        references.add(type.centre()); references.addAll(type.startBuildings());
        for (int index = 0; index < references.size(); index++) {
            String key = references.get(index);
            try {
                LegacyBuildingPlan plan = choose(catalog, type.culture(), key, random);
                validate(plan);
                boolean centre = index == 0;
                Building building = centre ? at(plan, origin, 3, true) : find(plan, origin, radius, buildings, random);
                // Walled villages leave little room inside the ring; large start buildings may then sit just outside it.
                if (building == null && !centre && walled && radius + OVERFLOW <= MAX_RADIUS) {
                    building = find(plan, origin, radius + OVERFLOW, buildings, random);
                    if (building != null) usedRadius = Math.max(usedRadius, radius + OVERFLOW);
                }
                if (building == null || !bounds(plan, building.origin(), building.rotation(), false).within(origin, usedRadius))
                    throw new IllegalArgumentException("No space within radius " + usedRadius + " for " + plan.id());
                buildings.add(building);
                addSubBuildings(catalog, building, buildings, random);
                // Walls are laid out right after the centre so that every later building keeps clear of them.
                if (centre) addWalls(catalog, type, origin, radius, buildings, issues);
            } catch (IllegalArgumentException | ArithmeticException exception) {
                issues.add((index == 0 ? "Centre" : "Start " + index) + " (" + key + "): " + exception.getMessage());
            }
        }
        return new Layout(type, origin, seed, usedRadius, buildings, issues);
    }

    /** {@code startingsubbuilding} plans overlay their parent at the same origin and rotation. */
    private static void addSubBuildings(LegacyContentCatalog catalog, Building parent, List<Building> buildings, Random random) {
        for (String key : parent.plan().parameters().getOrDefault("startingsubbuilding", List.of())) {
            if (key.isBlank()) continue;
            LegacyBuildingPlan sub = choose(catalog, parent.plan().culture(), key.trim(), random);
            buildings.add(new Building(sub, parent.origin(), parent.rotation(), false, parent.reservedArea(), Role.SUB));
        }
    }

    private static void addWalls(LegacyContentCatalog catalog, VillageTypeDefinition type, Position origin, int radius,
                                 List<Building> buildings, List<String> issues) {
        String inner = type.source().first("innerwalltype", "").trim();
        String outer = type.source().first("outerwalltype", "").trim();
        int innerRadius = integer(type.source().fields(), "innerwallradius", 0);
        for (String[] wall : new String[][]{{inner, Integer.toString(innerRadius)}, {outer, "0"}}) {
            if (wall[0].isEmpty()) continue;
            try {
                var wallType = VillageWalls.type(catalog, type.culture(), wall[0]);
                for (var piece : VillageWalls.pieces(catalog, type.culture(), wallType, origin, radius, Integer.parseInt(wall[1]))) {
                    Building building = at(piece.plan(), piece.centre(), piece.facing(), false);
                    buildings.add(new Building(building.plan(), building.origin(), building.rotation(), false,
                            bounds(piece.plan(), building.origin(), building.rotation(), false), Role.WALL));
                }
            } catch (IllegalArgumentException exception) {
                issues.add("Walls (" + wall[0] + "): " + exception.getMessage());
            }
        }
    }

    static LegacyBuildingPlan choose(LegacyContentCatalog catalog, String culture, String key, Random random) {
        var variants = catalog.plans().values().stream()
                .filter(plan -> plan.culture().equals(culture) && plan.key().equals(key) && plan.upgrade() == 0)
                .sorted(Comparator.comparingInt(LegacyBuildingPlan::variation)).toList();
        if (variants.isEmpty()) throw new IllegalArgumentException("Missing starting plan: " + culture + ":" + key);
        long total = 0;
        for (var plan : variants) {
            int weight = integer(plan.parameters(), "weight", 1);
            if (weight < 0) throw new IllegalArgumentException("Negative variant weight in " + plan.id());
            total += weight;
        }
        if (total == 0) throw new IllegalArgumentException("All variant weights are zero: " + key);
        long picked = random.nextLong(total);
        for (var plan : variants) {
            picked -= integer(plan.parameters(), "weight", 1);
            if (picked < 0) return plan;
        }
        throw new IllegalStateException("Variant weight mismatch");
    }

    private static Building find(LegacyBuildingPlan plan, Position centre, int radius, List<Building> previous, Random random) {
        int minRadius = (int) (radius * decimal(plan.parameters(), "mindistance", 0));
        int maxRadius = (int) (radius * decimal(plan.parameters(), "maxdistance", 1));
        var far = tagDistances(plan, "farfromtag");
        var close = tagDistances(plan, "closetotag");
        for (int ring = minRadius; ring < maxRadius; ring++) {
            int count = ring == 0 ? 1 : 8 * ring;
            int offset = random.nextInt(count);
            for (int n = 0; n < count; n++) {
                int step = (n + offset) % count;
                int side = ring == 0 ? 0 : step / (2 * ring);
                int along = ring == 0 ? 0 : step % (2 * ring) - ring;
                int dx = switch (side) { case 0 -> along; case 1 -> ring; case 2 -> -along; default -> -ring; };
                int dz = switch (side) { case 0 -> -ring; case 1 -> along; case 2 -> ring; default -> -along; };
                Position site = new Position(centre.x() + dx, centre.y(), centre.z() + dz);
                if (!tagConstraints(site, previous, far, close)) continue;
                int fixed = fixedOrientation(plan);
                int facing = fixed >= 0 ? fixed : facingCentre(dx, dz);
                Building building = at(plan, site, facing, false);
                // The footprint must lie in the village; its clearance margin may extend past the edge.
                if (!bounds(plan, building.origin(), building.rotation(), false).within(centre, radius)) continue;
                if (previous.stream().noneMatch(other -> other.reservedArea().intersects(building.reservedArea()))) return building;
            }
        }
        return null;
    }

    private static Building at(LegacyBuildingPlan plan, Position site, int facing, boolean centre) {
        int rotation = Math.floorMod(facing + integer(plan.parameters(), "buildingorientation", 1), 4);
        Position origin = new Position(site.x(), Math.addExact(site.y(), integer(plan.parameters(), "altitudeoffset", 0)), site.z());
        return new Building(plan, origin, rotation, centre, bounds(plan, origin, rotation, true));
    }

    /** Includes the original asymmetric clear areas, plus one reserved block on each edge. */
    public static Bounds bounds(LegacyBuildingPlan plan, Position origin, int rotation, boolean clearance) {
        int area = clearance ? integer(plan.parameters(), "areatoclear", 5) : 0;
        int beforeLength = clearance ? margin(plan, "areatoclearlengthbefore", area) + 1 : 0;
        int afterLength = clearance ? margin(plan, "areatoclearlengthafter", area) + 1 : 0;
        int beforeWidth = clearance ? margin(plan, "areatoclearwidthbefore", area) + 1 : 0;
        int afterWidth = clearance ? margin(plan, "areatoclearwidthafter", area) + 1 : 0;
        var first = plan.worldPosition(origin.x(), 0, origin.z(), -beforeWidth, 0, -beforeLength, rotation);
        var last = plan.worldPosition(origin.x(), 0, origin.z(), plan.width() - 1 + afterWidth, 0, plan.length() - 1 + afterLength, rotation);
        return new Bounds(Math.min(first.x(), last.x()), Math.min(first.z(), last.z()), Math.max(first.x(), last.x()), Math.max(first.z(), last.z()));
    }

    private static int margin(LegacyBuildingPlan plan, String key, int fallback) {
        int value = integer(plan.parameters(), key, -1);
        if (value < -1 || value > MAX_RADIUS) throw new IllegalArgumentException("Invalid " + key + " in " + plan.id());
        return value == -1 ? fallback : value;
    }

    private static int facingCentre(int dx, int dz) {
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? 0 : 2;
        return dz > 0 ? 3 : 1;
    }

    private static boolean tagConstraints(Position site, List<Building> previous, Map<String, Integer> far, Map<String, Integer> close) {
        for (var rule : far.entrySet()) {
            int distance = rule.getValue();
            if (previous.stream().anyMatch(building -> tagged(building, rule.getKey()) && distanceSquared(site, building.origin()) < (long) distance * distance)) return false;
        }
        for (var rule : close.entrySet()) {
            int distance = rule.getValue();
            if (previous.stream().noneMatch(building -> tagged(building, rule.getKey()) && distanceSquared(site, building.origin()) < (long) distance * distance)) return false;
        }
        return true;
    }

    private static boolean tagged(Building building, String tag) { return building.plan().parameters().getOrDefault("tag", List.of()).contains(tag); }
    private static long distanceSquared(Position first, Position second) {
        long dx = (long) first.x() - second.x(), dz = (long) first.z() - second.z();
        return dx * dx + dz * dz;
    }
    private static void validate(LegacyBuildingPlan plan) {
        double min = decimal(plan.parameters(), "mindistance", 0), max = decimal(plan.parameters(), "maxdistance", 1);
        if (min < 0 || max > 1 || min > max) throw new IllegalArgumentException("Invalid distance range in " + plan.id());
        int clear = integer(plan.parameters(), "areatoclear", 5);
        if (clear < 0 || clear > MAX_RADIUS) throw new IllegalArgumentException("Invalid clear area in " + plan.id());
        int orientation = integer(plan.parameters(), "buildingorientation", 1);
        if (orientation < 0 || orientation > 3) throw new IllegalArgumentException("Invalid building orientation in " + plan.id());
        fixedOrientation(plan);
        tagDistances(plan, "farfromtag"); tagDistances(plan, "closetotag");
    }
    private static Map<String, Integer> tagDistances(LegacyBuildingPlan plan, String key) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String rule : plan.parameters().getOrDefault(key, List.of())) {
            String[] parts = rule.split(",", -1);
            if (parts.length != 2 || parts[0].isBlank()) throw new IllegalArgumentException("Invalid " + key + " rule in " + plan.id());
            int distance = Integer.parseInt(parts[1].trim());
            if (distance < 0 || distance > 65536) throw new IllegalArgumentException("Invalid tag distance in " + plan.id());
            result.put(parts[0].trim(), distance); // Original STRING_INTEGER_ADD map: last rule for a tag wins.
        }
        return result;
    }
    private static int fixedOrientation(LegacyBuildingPlan plan) {
        String value = scalar(plan.parameters(), "fixedorientation", "-1").toLowerCase(Locale.ROOT);
        return switch (value) {
            case "-1" -> -1; case "0", "north" -> 0; case "1", "west" -> 1;
            case "2", "south" -> 2; case "3", "east" -> 3;
            default -> throw new IllegalArgumentException("Invalid fixed orientation in " + plan.id());
        };
    }
    static int integer(Map<String, List<String>> fields, String key, int fallback) { return Integer.parseInt(scalar(fields, key, Integer.toString(fallback))); }
    private static double decimal(Map<String, List<String>> fields, String key, double fallback) {
        double value = Double.parseDouble(scalar(fields, key, Double.toString(fallback)));
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite " + key);
        return value;
    }
    private static String scalar(Map<String, List<String>> fields, String key, String fallback) {
        List<String> values = fields.get(key);
        return values == null || values.isEmpty() ? fallback : values.getLast();
    }
}
