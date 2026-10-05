package org.millenaire.fabric.content;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class CustomBuildingsTest {
    private static LegacyContentCatalog catalog() throws Exception {
        return LegacyCatalogLoader.load(Path.of(CustomBuildingsTest.class.getResource("/todeploy/millenaire/blocklist.txt").toURI()).getParent());
    }

    @Test void parsesRangesResidentsAndTags() {
        var farm = CustomBuildings.parse("norman", "custom_farm",
                "native:Ferme;gameNameKey:farm_A0;male:farmer;female:Wife;moveinpriority:10;cropType:wheat;chest:1-5;sign:1;field:10-30");
        assertEquals(new CustomBuildings.Range(10, 30), farm.resources().get("field"));
        assertEquals(new CustomBuildings.Range(1, 1), farm.resources().get("sign"));
        assertEquals(CustomBuildings.DEFAULT_RADIUS, farm.radius());
        var plan = farm.plan();
        assertEquals("norman:custom_farm_A0", plan.id());
        assertEquals("wife", plan.parameters().get("female").getFirst());
        assertEquals("true", plan.parameters().get("custom").getFirst());
    }

    @Test void everyBundledCustomBuildingAndCustomCentreResolves() throws Exception {
        var catalog = catalog();
        var diagnostics = new ArrayList<String>();
        var all = CustomBuildings.all(catalog, diagnostics);
        assertTrue(diagnostics.isEmpty(), diagnostics.toString());
        assertTrue(all.size() > 90, "custom buildings: " + all.size());
        for (var culture : catalog.cultures().values())
            for (var type : culture.villageTypes().values()) {
                String centre = type.source().first("customcentre", "").trim().toLowerCase(Locale.ROOT);
                if (!centre.isEmpty()) assertTrue(all.containsKey(culture.id() + ":" + centre), type.id() + " centre " + centre);
                for (String custom : type.source().values("custombuilding"))
                    assertTrue(all.containsKey(culture.id() + ":" + custom.trim().toLowerCase(Locale.ROOT)), type.id() + " lists " + custom);
            }
        var centre = all.get("norman:custom_villagecentre");
        assertNotNull(catalog.plan(centre.planId()));
        assertNull(catalog.plans().get(centre.planId()), "custom plans stay out of the PNG plan list");
    }
}
