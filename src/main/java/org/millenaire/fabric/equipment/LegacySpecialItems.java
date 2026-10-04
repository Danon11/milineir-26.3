package org.millenaire.fabric.equipment;

import net.minecraft.world.item.Item;
import java.util.Set;

/** Registration properties for currency and utility items while their old GUI actions are ported. */
public final class LegacySpecialItems {
    private static final Set<String> COINS = Set.of("denier", "denierargent", "denieror");
    private static final Set<String> SINGLETONS = Set.of("purse", "summoningwand", "negationwand");
    private LegacySpecialItems() {}
    public static boolean supports(String name) { return COINS.contains(name) || SINGLETONS.contains(name); }
    public static Item.Properties configure(String name, Item.Properties properties) {
        if (COINS.contains(name)) return properties.stacksTo(64);
        if (SINGLETONS.contains(name)) return properties.stacksTo(1);
        throw new IllegalArgumentException("Unknown special item: " + name);
    }
}
