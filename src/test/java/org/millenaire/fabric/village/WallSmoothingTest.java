package org.millenaire.fabric.village;

import org.junit.jupiter.api.Test;
import org.millenaire.fabric.content.LegacyBuildingPlan;
import org.millenaire.fabric.content.VillageLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WallSmoothingTest {
    @Test void wallStepsAreSmoothedAroundTheVillage() {
        var plan = new LegacyBuildingPlan("norman", "wall", 'A', 0, 5, 5, 0, null, Map.of());
        var bounds = new VillageLayout.Bounds(0, 0, 4, 4);
        List<VillageLayout.Building> buildings = new ArrayList<>();
        int[] heights = {64, 64, 72, 64, 64, 64, 64, 64};
        for (int i = 0; i < heights.length; i++) {
            double angle = Math.PI * 2 * i / heights.length;
            var origin = new LegacyBuildingPlan.Position((int) (Math.cos(angle) * 40), heights[i], (int) (Math.sin(angle) * 40));
            buildings.add(new VillageLayout.Building(plan, origin, 0, false, bounds, VillageLayout.Role.WALL));
        }
        var house = new VillageLayout.Building(plan, new LegacyBuildingPlan.Position(5, 90, 5), 0, false, bounds, VillageLayout.Role.START);
        buildings.add(house);
        VillageFounder.smoothWalls(new LegacyBuildingPlan.Position(0, 64, 0), buildings);
        int max = 0;
        for (int i = 0; i < heights.length; i++) {
            int a = buildings.get(i).origin().y(), b = buildings.get((i + 1) % heights.length).origin().y();
            max = Math.max(max, Math.abs(a - b));
        }
        assertTrue(max <= 3, "largest step " + max);
        assertEquals(90, buildings.getLast().origin().y(), "other buildings keep their height");
    }
}
