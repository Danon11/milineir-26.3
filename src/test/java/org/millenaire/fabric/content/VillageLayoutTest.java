package org.millenaire.fabric.content;

import org.junit.jupiter.api.Test;
import org.millenaire.fabric.content.LegacyBuildingPlan.Position;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class VillageLayoutTest {
    @Test void keepsRepeatedStartsSelectsOnlyInitialVariantsAndIsReproducible() {
        var centre = plan("hall", 'A', 0, Map.of());
        var disabled = plan("house", 'A', 0, Map.of("weight", List.of("0")));
        var enabled = plan("house", 'B', 0, Map.of("weight", List.of("5")));
        var upgrade = plan("house", 'C', 1, Map.of("weight", List.of("1000")));
        var type = type("centre=hall\nstart=house\nstart=house\n");
        var first = VillageLayout.create(catalog(centre, disabled, enabled, upgrade), type, new Position(0, 64, 0), 1234);
        var reordered = VillageLayout.create(catalog(upgrade, enabled, disabled, centre), type, new Position(0, 64, 0), 1234);
        assertTrue(first.complete(), first.issues().toString());
        assertEquals(first, reordered);
        assertEquals(3, first.buildings().size());
        assertTrue(first.buildings().getFirst().centre());
        assertEquals(List.of("demo:hall_A0", "demo:house_B0", "demo:house_B0"), first.buildings().stream().map(b -> b.plan().id()).toList());
        assertNotEquals(first.buildings().get(1).origin(), first.buildings().get(2).origin());
        assertNoOverlaps(first);
    }

    @Test void respectsDistanceTagsFixedLegacyDirectionAndAltitude() {
        var centre = plan("hall", 'A', 0, Map.of("tag", List.of("civic")));
        var house = plan("house", 'A', 0, Map.of("mindistance", List.of("0.2"), "maxdistance", List.of("0.5"),
                "farfromtag", List.of("civic,12"), "closetotag", List.of("civic,1", "civic,30"),
                "fixedorientation", List.of("west"), "altitudeoffset", List.of("2")));
        var layout = VillageLayout.create(catalog(centre, house), type("centre=hall\nstart=house\n"), new Position(0, 64, 0), 5);
        assertTrue(layout.complete(), layout.issues().toString());
        var placed = layout.buildings().get(1);
        long squared = (long) placed.origin().x() * placed.origin().x() + (long) placed.origin().z() * placed.origin().z();
        assertTrue(squared >= 12 * 12 && squared < 30 * 30);
        assertTrue(Math.max(Math.abs(placed.origin().x()), Math.abs(placed.origin().z())) >= 16);
        // DirectionIO in the original source maps west to 1, then adds buildingOrientation=1.
        assertEquals(2, placed.rotation());
        assertEquals(66, placed.origin().y());
    }

    @Test void transformsAsymmetricClearanceAndReservesTouchingEdges() {
        var plan = new LegacyBuildingPlan("demo", "hall", 'A', 0, 2, 3, -1, Path.of("unused.png"),
                Map.of("areatoclear", List.of("0"), "areatoclearlengthbefore", List.of("2"), "areatoclearwidthafter", List.of("1")));
        assertEquals(new VillageLayout.Bounds(-4, -2, 2, 2), VillageLayout.bounds(plan, new Position(0, 64, 0), 0, true));
        assertEquals(new VillageLayout.Bounds(-2, -3, 2, 3), VillageLayout.bounds(plan, new Position(0, 64, 0), 1, true));
        assertEquals(new VillageLayout.Bounds(-1, -1, 1, 0), VillageLayout.bounds(plan, new Position(0, 64, 0), 0, false));
        assertTrue(new VillageLayout.Bounds(0, 0, 3, 3).intersects(new VillageLayout.Bounds(3, 3, 4, 4)));
        assertFalse(new VillageLayout.Bounds(0, 0, 3, 3).intersects(new VillageLayout.Bounds(4, 0, 5, 3)));
    }

    @Test void reportsMissingPlansTooLittleSpaceAndInvalidMetadata() {
        var centre = plan("hall", 'A', 0, Map.of());
        var missing = VillageLayout.create(catalog(centre), type("centre=hall\nstart=absent\n"), new Position(0, 64, 0), 0);
        assertFalse(missing.complete());
        assertTrue(missing.issues().getFirst().contains("Missing starting plan"));
        var small = VillageLayout.create(catalog(centre), type("centre=hall\nstart=hall\nradius=3\n"), new Position(0, 64, 0), 0);
        assertFalse(small.complete());
        assertTrue(small.issues().getFirst().contains("No space"));
        var invalid = VillageLayout.create(catalog(centre), type("centre=hall\nradius=999999\n"), new Position(0, 64, 0), 0);
        assertFalse(invalid.complete());
        var nan = plan("hall", 'A', 0, Map.of("mindistance", List.of("NaN")));
        assertFalse(VillageLayout.create(catalog(nan), type("centre=hall\n"), new Position(0, 64, 0), 0).complete());
        var custom = VillageLayout.create(catalog(centre), type("customcentre=playercentre\n"), new Position(0, 64, 0), 0);
        assertFalse(custom.complete()); assertTrue(custom.issues().getFirst().contains("Custom village centre"));
    }

    @Test void rejectsZeroAndNegativeWeightsInsteadOfChoosingAnArbitraryBuilding() {
        for (String weight : List.of("0", "-1")) {
            var centre = plan("hall", 'A', 0, Map.of("weight", List.of(weight)));
            var layout = VillageLayout.create(catalog(centre), type("centre=hall\n"), new Position(0, 64, 0), 0);
            assertFalse(layout.complete()); assertTrue(layout.buildings().isEmpty());
            assertTrue(layout.issues().getFirst().contains("weight"));
        }
    }

    @Test void auditsAllBundledDefinitionsAndLaysOutAnActualNormanAgriculturalVillage() throws Exception {
        var root = Path.of(getClass().getResource("/todeploy/millenaire/blocklist.txt").toURI()).getParent();
        var catalog = LegacyCatalogLoader.load(root);
        int total = 0, complete = 0;
        for (var culture : catalog.cultures().values()) for (var type : culture.villageTypes().values()) {
            var layout = VillageLayout.create(catalog, type, new Position(0, 64, 0), 1234);
            total++;
            if (layout.complete()) complete++;
            else System.out.println(type.culture() + ":" + type.id() + ": " + layout.issues());
            assertNoOverlaps(layout);
            for (var building : layout.buildings()) {
                assertEquals(type.culture(), building.plan().culture()); assertEquals(0, building.plan().upgrade());
                assertTrue(building.reservedArea().within(layout.origin(), layout.radius()));
            }
        }
        assertEquals(52, total);
        var type = catalog.cultures().get("norman").villageTypes().get("agricole");
        var layout = VillageLayout.create(catalog, type, new Position(0, 64, 0), 1234);
        assertTrue(layout.complete(), layout.issues().toString());
        assertEquals(6, layout.buildings().size());
        System.out.println("Bundled starting-layout audit: " + complete + "/" + total + " complete geometric layouts; terrain and palette support checked separately.");
    }

    private static void assertNoOverlaps(VillageLayout.Layout layout) {
        for (int i = 0; i < layout.buildings().size(); i++) for (int j = i + 1; j < layout.buildings().size(); j++)
            assertFalse(layout.buildings().get(i).reservedArea().intersects(layout.buildings().get(j).reservedArea()));
    }
    private static LegacyBuildingPlan plan(String key, char variant, int upgrade, Map<String, List<String>> fields) {
        Map<String, List<String>> params = new HashMap<>(fields); params.put("areatoclear", List.of("0"));
        return new LegacyBuildingPlan("demo", key, variant, upgrade, 3, 3, -1, Path.of(key + "_" + variant + upgrade + ".png"), params);
    }
    private static LegacyContentCatalog catalog(LegacyBuildingPlan... plans) {
        Map<String, LegacyBuildingPlan> map = new LinkedHashMap<>();
        for (var plan : plans) map.put(plan.id(), plan);
        return new LegacyContentCatalog(Map.of(), Map.of(), map, new LegacyPalette(Map.of()), List.of());
    }
    private static VillageTypeDefinition type(String text) {
        Map<String, List<String>> fields = new HashMap<>();
        for (String line : text.lines().toList()) {
            var parts = line.split("=", 2); fields.computeIfAbsent(parts[0], ignored -> new ArrayList<>()).add(parts[1]);
        }
        return VillageTypeDefinition.from("demo", "test", new LegacyDocument("test", "UTF-8", text.lines().toList(), fields));
    }
}
