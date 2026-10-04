package org.millenaire.fabric.equipment;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.registries.Registries;

import java.util.LinkedHashMap;
import java.util.Map;

/** Attribute and durability migration for the six ItemMillenaireSword entries. */
public final class LegacyWeapons {
    public record Definition(ToolMaterial material, int enchantability, boolean knockback) {}
    private static final Map<String, Definition> DEFINITIONS = definitions();
    private LegacyWeapons() {}

    public static Map<String, Definition> all() { return DEFINITIONS; }

    public static Item.Properties configure(String name, Item.Properties properties) {
        Definition definition = DEFINITIONS.get(name);
        if (definition == null) throw new IllegalArgumentException("Unknown Millenaire weapon: " + name);
        ToolMaterial material = definition.material();
        float damage = 3.0F + material.attackDamageBonus();
        properties.durability(material.durability())
                .enchantable(definition.enchantability() >= 0 ? definition.enchantability() : material.enchantmentValue())
                .attributes(ItemAttributeModifiers.builder()
                        .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.4F, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                        .build())
                .component(DataComponents.WEAPON, new Weapon(2, 0.0F));
        if (definition.knockback()) {
            properties.delayedComponent(DataComponents.ENCHANTMENTS, lookup -> {
                var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                enchantments.set(lookup.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.KNOCKBACK), 2);
                return enchantments.toImmutable();
            });
        }
        return properties;
    }

    private static Map<String, Definition> definitions() {
        Map<String, Definition> result = new LinkedHashMap<>();
        ToolMaterial norman = LegacyTools.all().get("normanpickaxe").material();
        ToolMaterial mayan = LegacyTools.all().get("mayanpickaxe").material();
        ToolMaterial iron = ToolMaterial.IRON;
        ToolMaterial betterSteel = new ToolMaterial(net.minecraft.tags.BlockTags.INCORRECT_FOR_IRON_TOOL, 1561, 5.0F, 3.0F, 10, net.minecraft.tags.ItemTags.IRON_TOOL_MATERIALS);
        result.put("normanbroadsword", new Definition(norman, -1, false));
        result.put("mayanmace", new Definition(iron, -1, false));
        result.put("tachisword", new Definition(mayan, -1, false));
        result.put("byzantinemace", new Definition(iron, -1, true));
        result.put("inuittrident", new Definition(iron, 20, false));
        result.put("seljukscimitar", new Definition(betterSteel, -1, false));
        return java.util.Collections.unmodifiableMap(result);
    }
}
