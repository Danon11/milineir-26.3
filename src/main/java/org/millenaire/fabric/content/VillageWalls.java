package org.millenaire.fabric.content;

import org.millenaire.fabric.content.LegacyBuildingPlan.Position;

import java.util.*;

/**
 * Wall rings of a village ({@code walls/<type>.txt}): a gateway in the middle of each side, wall segments and
 * towers outwards from it, and a corner piece. Follows the original wall location algorithm on the layout's
 * common ground plane, so height smoothing, slopes and terrain-based gaps do not apply yet.
 */
public final class VillageWalls {
    public record WallType(String id, Map<String, String> plans, Map<String, Boolean> spawn, int wallsBetweenTowers) {
        public WallType { plans = Map.copyOf(plans); spawn = Map.copyOf(spawn); }

        public static WallType from(String id, LegacyDocument document) {
            Map<String, String> plans = new HashMap<>();
            Map<String, Boolean> spawn = new HashMap<>();
            for (String part : List.of("wall", "tower", "gate", "corner")) {
                String plan = document.first("village_wall" + (part.equals("wall") ? "" : "_" + part), "").trim();
                if (!plan.isEmpty()) plans.put(part, plan);
                spawn.put(part, Boolean.parseBoolean(document.first("village_wall" + (part.equals("wall") ? "" : "_" + part) + "_spawn", "true").trim()));
            }
            if (!plans.containsKey("gate")) throw new IllegalArgumentException("Wall type " + id + " has no gateway");
            int between = Integer.parseInt(document.first("village_wall_nb_between_towers", "3").trim());
            if (between < 0) throw new IllegalArgumentException("Negative towers spacing in " + id);
            return new WallType(id, plans, spawn, between);
        }
    }

    /** One wall piece: plan, centre position and facing (0..3, before the plan's own building orientation). */
    public record Piece(LegacyBuildingPlan plan, Position centre, int facing) {}

    private record Side(int xMultiplier, int zMultiplier, int direction, int facing) {}
    private static final List<Side> SIDES = List.of(new Side(1, 0, 1, 0), new Side(0, 1, -1, 3), new Side(-1, 0, -1, 2), new Side(0, -1, 1, 1));

    private VillageWalls() {}

    public static WallType type(LegacyContentCatalog catalog, String culture, String id) {
        var document = catalog.cultures().getOrDefault(culture, new LegacyContentCatalog.Culture(culture, Map.of()))
                .documents().get("walls/" + id.trim().toLowerCase(Locale.ROOT) + ".txt");
        if (document == null) throw new IllegalArgumentException("Unknown wall type " + culture + ":" + id);
        return WallType.from(id, document);
    }

    /** First variant at level 0, as the original wall generator used. */
    static LegacyBuildingPlan plan(LegacyContentCatalog catalog, String culture, String key) {
        return catalog.plans().values().stream()
                .filter(plan -> plan.culture().equals(culture) && plan.key().equalsIgnoreCase(key) && plan.upgrade() == 0)
                .min(Comparator.comparingInt(LegacyBuildingPlan::variation))
                .orElseThrow(() -> new IllegalArgumentException("Missing wall plan: " + culture + ":" + key));
    }

    /**
     * @param maxRadius the {@code innerwallradius} of the village type, or 0 for the outer wall at the village radius
     */
    public static List<Piece> pieces(LegacyContentCatalog catalog, String culture, WallType type, Position centre, int villageRadius, int maxRadius) {
        LegacyBuildingPlan wall = type.plans().containsKey("wall") ? plan(catalog, culture, type.plans().get("wall")) : null;
        LegacyBuildingPlan tower = type.plans().containsKey("tower") ? plan(catalog, culture, type.plans().get("tower")) : null;
        LegacyBuildingPlan gate = plan(catalog, culture, type.plans().get("gate"));
        LegacyBuildingPlan corner = type.plans().containsKey("corner") ? plan(catalog, culture, type.plans().get("corner")) : tower;
        // The plan's length runs along the wall once rotated to face the village centre.
        int wallLength = wall != null ? wall.length() : 1;
        int towerLength = tower != null ? tower.length() : 0;
        int cornerLength = corner != null ? corner.length() : 0;
        int limit = maxRadius > 0 ? maxRadius : villageRadius - wallLength - cornerLength;
        int wallRadius = gate.length() / 2;
        for (int n = 0; wallRadius < limit; n++)
            wallRadius += n % (type.wallsBetweenTowers() + 1) == type.wallsBetweenTowers() ? towerLength : wallLength;
        wallRadius += wallLength + cornerLength / 2;

        List<Piece> pieces = new ArrayList<>();
        for (Side side : SIDES) {
            List<Piece> forward = new ArrayList<>(), backward = new ArrayList<>();
            int pos = gate.length() / 2;
            for (int i = 0; pos < limit; i++) {
                boolean towerSlot = i % (type.wallsBetweenTowers() + 1) == type.wallsBetweenTowers();
                LegacyBuildingPlan current = towerSlot ? tower : wall;
                if (current != null && type.spawn().getOrDefault(towerSlot ? "tower" : "wall", true))
                    along(current, side, centre, wallRadius, pos, true, forward, backward);
                pos += towerSlot ? towerLength : wallLength;
            }
            if (wall != null && type.spawn().getOrDefault("wall", true)) along(wall, side, centre, wallRadius, pos, true, forward, backward);
            pos += wallLength;
            if (corner != null && type.spawn().getOrDefault("corner", true)) along(corner, side, centre, wallRadius, pos, false, forward, backward);
            Collections.reverse(backward);
            pieces.addAll(backward);
            if (type.spawn().getOrDefault("gate", true))
                pieces.add(new Piece(gate, new Position(centre.x() + wallRadius * side.xMultiplier(), centre.y(), centre.z() + wallRadius * side.zMultiplier()), side.facing()));
            pieces.addAll(forward);
        }
        return pieces;
    }

    private static void along(LegacyBuildingPlan plan, Side side, Position centre, int radius, int pos, boolean mirrored,
                              List<Piece> forward, List<Piece> backward) {
        int length = plan.length();
        int delta = (pos + length / 2) * side.direction();
        int mirroredDelta = delta + (length % 2 == 1 ? side.direction() : 0);
        if (side.xMultiplier() != 0) {
            forward.add(new Piece(plan, new Position(centre.x() + radius * side.xMultiplier(), centre.y(), centre.z() + delta), side.facing()));
            if (mirrored) backward.add(new Piece(plan, new Position(centre.x() + radius * side.xMultiplier(), centre.y(), centre.z() - mirroredDelta), side.facing()));
        } else {
            forward.add(new Piece(plan, new Position(centre.x() + delta, centre.y(), centre.z() + radius * side.zMultiplier()), side.facing()));
            if (mirrored) backward.add(new Piece(plan, new Position(centre.x() - mirroredDelta, centre.y(), centre.z() + radius * side.zMultiplier()), side.facing()));
        }
    }
}
