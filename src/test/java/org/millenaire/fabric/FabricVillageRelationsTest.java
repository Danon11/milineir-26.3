package org.millenaire.fabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FabricVillageRelationsTest {
    @Test void relationsAreSymmetricClampedAndDescribed() {
        var relations = new FabricVillageRelations();
        assertFalse(relations.known("a", "b"));
        relations.set("b", "a", 30);
        assertTrue(relations.known("a", "b"));
        assertEquals(30, relations.get("a", "b"));
        assertEquals(-100, relations.add("a", "b", -500));
        assertEquals(100, relations.set("a", "c", 250));
        assertEquals(2, relations.of("a").size());
        assertEquals(-100, relations.of("b").get("a"));
        assertEquals("at war", FabricVillageRelations.describe(-100));
        assertEquals("neutral", FabricVillageRelations.describe(0));
        assertEquals("allied", FabricVillageRelations.describe(100));
    }
}
