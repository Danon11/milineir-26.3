package org.millenaire.fabric.village;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BuildingMaterialsTest {
    @Test void blocksCostRawMaterials() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        Map<Item, Integer> blocks = new LinkedHashMap<>();
        blocks.put(Items.OAK_PLANKS, 40);      // 10 logs
        blocks.put(Items.SPRUCE_STAIRS, 8);    // 3 logs
        blocks.put(Items.COBBLESTONE, 20);     // 20 stone
        blocks.put(Items.STONE_BRICK_SLAB, 4); // 2 stone
        blocks.put(Items.GLASS_PANE, 8);       // 3 sand
        blocks.put(Items.IRON_BARS, 5);        // crafted from stock: free
        var cost = BuildingMaterials.cost(blocks);
        assertEquals(13, cost.get(Items.OAK_LOG));
        assertEquals(22, cost.get(Items.COBBLESTONE));
        assertEquals(3, cost.get(Items.SAND));
        assertFalse(cost.containsKey(Items.IRON_BARS));
    }
}
