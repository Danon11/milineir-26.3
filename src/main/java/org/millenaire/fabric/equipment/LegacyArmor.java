package org.millenaire.fabric.equipment;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.LinkedHashMap;
import java.util.Map;

/** Maps the recovered armour entries to modern humanoid armour components. */
public final class LegacyArmor {
    public record Definition(ArmorMaterial material, ArmorType type, EquipmentSlot slot) {}
    private static final Map<String, Definition> DEFINITIONS = definitions();
    private LegacyArmor() {}
    public static Map<String, Definition> all() { return DEFINITIONS; }

    public static Item.Properties configure(String name, Item.Properties properties) {
        Definition definition = DEFINITIONS.get(name);
        if (definition == null) throw new IllegalArgumentException("Unknown Millenaire armour: " + name);
        return properties.humanoidArmor(definition.material(), definition.type());
    }

    private static Map<String, Definition> definitions() {
        Map<String, Definition> result = new LinkedHashMap<>();
        addSet(result, "norman", ArmorMaterials.IRON);
        addSet(result, "byzantine", ArmorMaterials.IRON);
        addSet(result, "japaneseblue", ArmorMaterials.IRON);
        addSet(result, "japanesered", ArmorMaterials.IRON);
        addSet(result, "japaneseguard", ArmorMaterials.IRON);
        addSet(result, "fur", ArmorMaterials.LEATHER);
        addSet(result, "seljuk", ArmorMaterials.IRON);
        addOne(result, "seljukturban", ArmorMaterials.LEATHER, ArmorType.HELMET);
        return java.util.Collections.unmodifiableMap(result);
    }

    private static void addSet(Map<String, Definition> result, String prefix, ArmorMaterial material) {
        addOne(result, prefix + "helmet", material, ArmorType.HELMET);
        addOne(result, prefix + "plate", material, ArmorType.CHESTPLATE);
        addOne(result, prefix + "legs", material, ArmorType.LEGGINGS);
        addOne(result, prefix + "boots", material, ArmorType.BOOTS);
    }

    private static void addOne(Map<String, Definition> result, String name, ArmorMaterial material, ArmorType type) {
        result.put(name, new Definition(material, type, switch (type) {
            case HELMET -> EquipmentSlot.HEAD; case CHESTPLATE -> EquipmentSlot.CHEST;
            case LEGGINGS -> EquipmentSlot.LEGS; case BOOTS -> EquipmentSlot.FEET; default -> throw new AssertionError();
        }));
    }
}
