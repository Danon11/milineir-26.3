package org.millenaire.fabric.equipment;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacyBowsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void allThreeBowsKeepLegacyRangedMetadata() throws Exception {
        assertEquals(3, LegacyBows.all().size());
        for (var entry : LegacyBows.all().entrySet()) {
            var components = components(entry.getKey());
            assertEquals(384, components.get(DataComponents.MAX_DAMAGE));
            assertEquals(1, components.get(DataComponents.MAX_STACK_SIZE));
            assertEquals(entry.getValue().enchantability(), components.get(DataComponents.ENCHANTABLE).value());
            assertEquals(entry.getValue(), LegacyBows.all().get(entry.getKey()));
        }
    }

    @Test void unknownBowIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> LegacyBows.create("bow", new Item.Properties()));
    }

    private static DataComponentMap components(String name) throws Exception {
        var key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("millenaire", name));
        var properties = new Item.Properties().setId(key).durability(384).enchantable(LegacyBows.all().get(name).enchantability());
        var field = Item.Properties.class.getDeclaredField("componentInitializer"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var initializer = (DataComponentInitializers.Initializer<Item>) field.get(properties);
        var builder = DataComponentMap.builder(); initializer.run(builder, VanillaRegistries.createWorldLookup(), key); return builder.build();
    }
}
