package org.millenaire.fabric.equipment;

import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;

import java.util.LinkedHashMap;
import java.util.Map;

/** Modern bow items for the three recovered ItemMillenaireBow entries. */
public final class LegacyBows {
    public record Definition(float speedFactor, float damageBonus, int enchantability) {}
    private static final Map<String, Definition> DEFINITIONS = Map.of(
            "yumibow", new Definition(2.0F, 0.5F, 1),
            "inuitbow", new Definition(1.0F, 0.0F, 20),
            "seljukbow", new Definition(1.5F, 1.5F, 20));
    private LegacyBows() {}
    public static Map<String, Definition> all() { return DEFINITIONS; }
    public static Item.Properties configure(String name, Item.Properties properties) {
        Definition definition = DEFINITIONS.get(name);
        if (definition == null) throw new IllegalArgumentException("Unknown Millenaire bow: " + name);
        return properties.durability(384).enchantable(definition.enchantability());
    }
    public static Item create(String name, Item.Properties properties) {
        // Fabric's BowItem supplies the vanilla projectile, durability and use flow.
        // The recovered damage/speed fields are retained in the catalog until the
        // projectile attribute hook is ported; enchantability remains visible here.
        return new BowItem(configure(name, properties));
    }
}
