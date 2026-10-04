package org.millenaire.fabric.equipment;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacyArmorTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void allTwentyNineArmorEntriesHaveSlotsAndModernMaterials() {
        assertEquals(29, LegacyArmor.all().size());
        for (var entry : LegacyArmor.all().entrySet()) {
            assertNotNull(entry.getValue().slot());
            assertNotNull(entry.getValue().type());
            assertNotNull(entry.getValue().material());
            assertTrue(entry.getKey().endsWith("helmet") || entry.getKey().endsWith("plate") || entry.getKey().endsWith("legs") || entry.getKey().endsWith("boots") || entry.getKey().equals("seljukturban"));
        }
        assertEquals(ArmorMaterials.LEATHER, LegacyArmor.all().get("furhelmet").material());
        assertEquals(ArmorMaterials.LEATHER, LegacyArmor.all().get("seljukturban").material());
        assertEquals(ArmorType.CHESTPLATE, LegacyArmor.all().get("normanplate").type());
        assertEquals(ArmorType.LEGGINGS, LegacyArmor.all().get("byzantinelegs").type());
    }

    @Test void unsupportedNamesAreRejectedBeforeItemCreation() {
        assertThrows(IllegalArgumentException.class, () -> LegacyArmor.configure("japaneseguard", new net.minecraft.world.item.Item.Properties()));
    }
}
