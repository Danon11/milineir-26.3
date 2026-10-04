package org.millenaire.fabric.equipment;

import org.junit.jupiter.api.Test;
import net.minecraft.world.item.Item;
import static org.junit.jupiter.api.Assertions.*;

class LegacySpecialItemsTest {
    @Test void currencyAndUtilityStackRulesMatchForgeItems() {
        for (String coin : new String[]{"denier", "denierargent", "denieror"}) assertTrue(LegacySpecialItems.supports(coin));
        for (String item : new String[]{"purse", "summoningwand", "negationwand"}) assertTrue(LegacySpecialItems.supports(item));
        assertThrows(IllegalArgumentException.class, () -> LegacySpecialItems.configure("unknown", new Item.Properties()));
    }
}
