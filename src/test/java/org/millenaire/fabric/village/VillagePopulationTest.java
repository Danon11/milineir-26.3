package org.millenaire.fabric.village;

import org.junit.jupiter.api.Test;
import org.millenaire.fabric.content.LegacyCatalogLoader;
import org.millenaire.fabric.villager.VillagerProfile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class VillagePopulationTest {
    @Test void buildingKeysIgnoreTheUpgradeLevel() {
        assertEquals("norman:farm_A@1,2,3", VillagePopulation.baseKey("norman:farm_A0@1,2,3"));
        assertEquals(VillagePopulation.baseKey("norman:farm_A0@1,2,3"), VillagePopulation.baseKey("norman:farm_A12@1,2,3"));
        assertNotEquals(VillagePopulation.baseKey("norman:farm_A0@1,2,3"), VillagePopulation.baseKey("norman:farm_B0@1,2,3"));
        assertEquals("plain", VillagePopulation.baseKey("plain"));
    }

    @Test void mothersHaveChildrenOfTheirCultureTypes() throws Exception {
        var catalog = LegacyCatalogLoader.load(Path.of(VillagePopulationTest.class.getResource("/todeploy/millenaire/blocklist.txt").toURI()).getParent());
        var profiles = VillagerProfile.all(catalog, new ArrayList<>());
        VillagerProfile wife = profiles.get("norman/wife");
        assertTrue(wife.canHaveChildren());
        assertFalse(profiles.get("norman/knight").canHaveChildren());
        var random = new Random(3);
        for (int i = 0; i < 20; i++) {
            String type = VillagePopulation.childType(wife, random);
            VillagerProfile child = profiles.get("norman/" + type);
            assertNotNull(child, type);
            assertTrue(child.child(), type);
        }
    }
}
