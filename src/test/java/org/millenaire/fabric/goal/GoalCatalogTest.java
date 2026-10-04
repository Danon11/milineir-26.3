package org.millenaire.fabric.goal;

import org.junit.jupiter.api.Test;
import org.millenaire.fabric.content.LegacyCatalogLoader;
import org.millenaire.fabric.content.LegacyContentCatalog;
import org.millenaire.fabric.content.LegacyDocument;
import org.millenaire.fabric.villager.VillagerProfile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GoalCatalogTest {
    private static LegacyContentCatalog bundle() throws Exception {
        return LegacyCatalogLoader.load(Path.of(GoalCatalogTest.class.getResource("/todeploy/millenaire/blocklist.txt").toURI()).getParent());
    }

    /** Map-backed store with a fixed capacity per good. */
    static final class MapStore implements GoodsStore {
        final Map<String, Integer> goods = new HashMap<>();
        final int capacity;
        MapStore(int capacity, Object... entries) {
            this.capacity = capacity;
            for (int i = 0; i < entries.length; i += 2) goods.put((String) entries[i], (Integer) entries[i + 1]);
        }
        public int count(String good) { return goods.getOrDefault(good, 0); }
        public int remove(String good, int amount) { int taken = Math.min(amount, count(good)); goods.merge(good, -taken, Integer::sum); return taken; }
        public int space(String good) { return capacity - count(good); }
        public int add(String good, int amount) { int added = Math.min(amount, space(good)); goods.merge(good, added, Integer::sum); return added; }
    }

    private GoalDefinition goal(GoalDefinition.Kind kind, String text) throws Exception {
        Path file = Files.createTempFile("goal", ".txt");
        Files.writeString(file, text);
        return GoalDefinition.parse("test", kind, LegacyDocument.read(file, "goals/test.txt"));
    }

    @Test
    void loadsEveryBundledGoalAndResolvesEveryVillagerGoalName() throws Exception {
        var catalog = bundle();
        var goals = GoalCatalog.from(catalog);
        assertEquals(453, goals.goals().size());
        assertEquals(3, goals.diagnostics().size(), goals.diagnostics().toString());
        assertTrue(goals.diagnostics().stream().allMatch(d -> d.contains("already defined")));
        Set<String> unknown = new TreeSet<>();
        VillagerProfile.all(catalog, new ArrayList<>()).values().forEach(profile -> unknown.addAll(goals.unknown(profile.goals())));
        assertEquals(Set.of(), unknown);
        var bonemeal = goals.get("makebonemeal").orElseThrow();
        assertEquals(GoalDefinition.Kind.CRAFTING, bonemeal.kind());
        var drink = goals.get("godrink").orElseThrow();
        assertTrue(drink.activeAt(10000) && !drink.activeAt(2000), "minimumhour is a day tick");
    }

    @Test
    void craftingConsumesInputsOnlyBelowLimits() throws Exception {
        var goal = goal(GoalDefinition.Kind.CRAFTING, "priority=50\nduration=5000\ninput=bone,1\noutput=dye_white,3\nbuildinglimit=dye_white,6\n");
        assertEquals(100, goal.durationTicks());
        var store = new MapStore(64, "bone", 2);
        assertTrue(GoalRules.craft(goal, store, null));
        assertEquals(1, store.count("bone"));
        assertEquals(3, store.count("dye_white"));
        assertTrue(GoalRules.craft(goal, store, null));
        assertFalse(GoalRules.canCraft(goal, store, null), "no bone left");
        store.add("bone", 5);
        assertFalse(GoalRules.canCraft(goal, store, null), "building limit 6 reached");
        assertFalse(GoalRules.canCraft(goal, new MapStore(2, "bone", 5), null), "outputs must fit");
    }

    @Test
    void townhallLimitFallsBackToTheBuildingOutsideAVillage() throws Exception {
        var goal = goal(GoalDefinition.Kind.CRAFTING, "output=bed_straw,1\ntownhalllimit=bed_straw,16\n");
        var building = new MapStore(64, "bed_straw", 16);
        assertFalse(GoalRules.canCraft(goal, building, null));
        assertTrue(GoalRules.canCraft(goal, building, new MapStore(64, "bed_straw", 3)));
    }

    @Test
    void cookingTakesBatchesAboveMinimum() throws Exception {
        var goal = goal(GoalDefinition.Kind.COOKING, "itemtocook=fishraw\nminimumtocook=3\nbuildinglimit=fishcooked,60\nhelditems=fishcooked\n");
        assertEquals(Optional.of("fishcooked"), GoalRules.cookedGood(goal));
        assertEquals(0, GoalRules.cookBatch(goal, new MapStore(64, "fishraw", 2), null));
        assertEquals(5, GoalRules.cookBatch(goal, new MapStore(64, "fishraw", 5), null));
        assertEquals(0, GoalRules.cookBatch(goal, new MapStore(64, "fishraw", 5, "fishcooked", 60), null));
    }

    @Test
    void harvestRowsAreIndependentChanceRolls() throws Exception {
        var goal = goal(GoalDefinition.Kind.HARVESTING, "croptype=wheat\nharvestitem=wheat,100\nharvestitem=seeds,100\nharvestitem=seeds,0\n");
        assertEquals(3, goal.harvestRolls().size());
        assertEquals(Map.of("wheat", 1, "seeds", 1), GoalRules.rollHarvest(goal, new SplittableRandom(1)));
        assertEquals("soil", GoalRules.soilFor("wheat"));
        assertEquals("maizesoil", GoalRules.soilFor("millenaire:crop_maize"));
        assertEquals("cottonsoil", GoalRules.soilFor("millenaire:crop_cotton"));
    }

    @Test
    void pickupMovesOnlyWhatHomeLacks() throws Exception {
        var goal = goal(GoalDefinition.Kind.TAKE_FROM_BUILDING, "buildingTag=bakery\ncollect_good=bread,8\nminimumpickup=2\n");
        assertEquals(Map.of("bread", 5), GoalRules.pickup(goal, new MapStore(64, "bread", 20), new MapStore(64, "bread", 3)));
        assertEquals(Map.of(), GoalRules.pickup(goal, new MapStore(64, "bread", 20), new MapStore(64, "bread", 7)), "below minimum pickup");
        assertEquals(Map.of(), GoalRules.pickup(goal, new MapStore(64, "bread", 1), new MapStore(64)));
    }

    @Test
    void rejectsMalformedGoals() {
        assertThrows(IllegalArgumentException.class, () -> goal(GoalDefinition.Kind.CRAFTING, "input=bone\n"));
        assertThrows(IllegalArgumentException.class, () -> goal(GoalDefinition.Kind.CRAFTING, "townhallgoal=maybe\n"));
        assertThrows(IllegalArgumentException.class, () -> goal(GoalDefinition.Kind.HARVESTING, "harvestitem=wheat,150\n"));
        assertThrows(IllegalArgumentException.class, () -> goal(GoalDefinition.Kind.VISIT, "minimumhour=30000\n"));
    }
}
