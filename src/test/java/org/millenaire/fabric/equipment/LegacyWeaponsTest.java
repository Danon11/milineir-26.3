package org.millenaire.fabric.equipment;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyWeaponsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void sixSwordEntriesKeepLegacyMaterialsDamageSpeedAndDurability() throws Exception {
        assertEquals(6, LegacyWeapons.all().size());
        for (var entry : LegacyWeapons.all().entrySet()) {
            var components = components(entry.getKey());
            assertEquals(entry.getValue().material().durability(), components.get(DataComponents.MAX_DAMAGE));
            assertEquals(1, components.get(DataComponents.MAX_STACK_SIZE));
            double expectedDamage = 3 + entry.getValue().material().attackDamageBonus();
            assertEquals(expectedDamage, components.get(DataComponents.ATTRIBUTE_MODIFIERS).compute(Attributes.ATTACK_DAMAGE, 0, EquipmentSlot.MAINHAND), 0.00001);
            assertEquals(-2.4, components.get(DataComponents.ATTRIBUTE_MODIFIERS).compute(Attributes.ATTACK_SPEED, 0, EquipmentSlot.MAINHAND), 0.00001);
            int expectedEnchantability = entry.getValue().enchantability() >= 0 ? entry.getValue().enchantability() : entry.getValue().material().enchantmentValue();
            assertEquals(expectedEnchantability, components.get(DataComponents.ENCHANTABLE).value());
            assertNotNull(components.get(DataComponents.WEAPON));
        }
        assertTrue(LegacyWeapons.all().get("byzantinemace").knockback());
        assertEquals(20, LegacyWeapons.all().get("inuittrident").enchantability());
        // The knockback II component is a deferred registry component and is applied
        // when the item registry builds its component initializer.
    }

    @Test void weaponComponentsUseMainHandOnlyAndNoOffhandDamage() throws Exception {
        for (String name : LegacyWeapons.all().keySet()) {
            var modifiers = components(name).get(DataComponents.ATTRIBUTE_MODIFIERS);
            assertEquals(0, modifiers.compute(Attributes.ATTACK_DAMAGE, 0, EquipmentSlot.OFFHAND));
            assertEquals(0, modifiers.compute(Attributes.ATTACK_SPEED, 0, EquipmentSlot.OFFHAND));
            assertEquals(2, components(name).get(DataComponents.WEAPON).itemDamagePerAttack());
        }
    }

    private static DataComponentMap components(String name) throws Exception {
        var key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("millenaire", name));
        var properties = LegacyWeapons.configure(name, new Item.Properties().setId(key));
        var field = Item.Properties.class.getDeclaredField("componentInitializer");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var initializer = (DataComponentInitializers.Initializer<Item>) field.get(properties);
        var builder = DataComponentMap.builder();
        initializer.run(builder, VanillaRegistries.createWorldLookup(), key);
        return builder.build();
    }
}
