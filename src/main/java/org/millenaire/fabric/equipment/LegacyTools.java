package org.millenaire.fabric.equipment;

import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.component.BlockTransformers;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Repairable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The twelve plain tools registered by the recovered MillItems implementation. */
public final class LegacyTools {
    public enum Kind { PICKAXE, AXE, SHOVEL, HOE }
    public record Definition(Kind kind, ToolMaterial material, int harvestLevel) {}

    private static final Map<String, Definition> DEFINITIONS = definitions();

    private LegacyTools() {}

    public static Map<String, Definition> all() { return DEFINITIONS; }

    public static Item.Properties configure(String name, Item.Properties properties) {
        Definition definition = DEFINITIONS.get(name);
        if (definition == null) throw new IllegalArgumentException("Unknown Millenaire tool: " + name);
        ToolMaterial material = definition.material();
        float damage = switch (definition.kind()) {
            case PICKAXE -> 1.0F + material.attackDamageBonus();
            case AXE -> 8.0F; // Forge's explicit ItemAxe constructor overrides, rather than adds, damage.
            case SHOVEL -> 1.5F + material.attackDamageBonus();
            case HOE -> 0.0F;
        };
        float speed = switch (definition.kind()) {
            case PICKAXE -> -2.8F; case AXE, SHOVEL -> -3.0F; case HOE -> material.attackDamageBonus() - 3.0F;
        };
        properties.durability(material.durability())
                .attributes(ItemAttributeModifiers.builder()
                        .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, speed, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build())
                .component(DataComponents.WEAPON, new Weapon(definition.kind() == Kind.HOE ? 1 : 2, definition.kind() == Kind.AXE ? 5.0F : 0.0F));
        if (definition.kind() == Kind.HOE) {
            // 1.12 ItemHoe did not override Item's zero table enchantability, accelerate
            // mining or wear when mining. Its attack speed came from damage bonus + 1.
            properties.component(DataComponents.TOOL, new Tool(List.of(), 1.0F, 0, true));
        } else {
            properties.enchantable(material.enchantmentValue());
            var mineable = switch (definition.kind()) {
                case PICKAXE -> BlockTags.MINEABLE_WITH_PICKAXE;
                case AXE -> BlockTags.MINEABLE_WITH_AXE;
                case SHOVEL -> BlockTags.MINEABLE_WITH_SHOVEL;
                default -> throw new AssertionError();
            };
            properties.delayedComponent(DataComponents.TOOL, lookup -> {
                var blocks = lookup.lookupOrThrow(Registries.BLOCK);
                return new Tool(List.of(Tool.Rule.deniesDrops(blocks.getOrThrow(material.incorrectBlocksForDrops())),
                        Tool.Rule.minesAndDrops(blocks.getOrThrow(mineable), material.speed())), 1.0F, 1, true);
            });
        }
        switch (definition.kind()) {
            case AXE -> properties.delayedComponent(DataComponents.BLOCK_TRANSFORMER, lookup -> lookup.getOrThrow(BlockTransformers.AXE));
            case SHOVEL -> properties.delayedComponent(DataComponents.BLOCK_TRANSFORMER, lookup -> lookup.getOrThrow(BlockTransformers.SHOVEL));
            case HOE -> properties.delayedComponent(DataComponents.BLOCK_TRANSFORMER, lookup -> lookup.getOrThrow(BlockTransformers.HOE));
            case PICKAXE -> { }
        }
        // These Forge custom materials had no configured repair ingredient. Keep same-item
        // repair available through vanilla anvils, without adding ingot/obsidian repair.
        return properties.component(DataComponents.REPAIRABLE, new Repairable(HolderSet.direct()));
    }

    private static Map<String, Definition> definitions() {
        Map<String, Definition> result = new LinkedHashMap<>();
        add(result, "norman", 2, 10.0F, 4.0F, 10);
        add(result, "byzantine", 2, 12.0F, 3.0F, 15);
        add(result, "mayan", 3, 6.0F, 2.0F, 25);
        return java.util.Collections.unmodifiableMap(result);
    }

    private static void add(Map<String, Definition> result, String culture, int level, float speed, float damage, int enchantment) {
        // ToolMaterial is the parameter record; configure supplies the empty ingredient
        // set directly, without invoking bootstrap-only material factories.
        ToolMaterial material = new ToolMaterial(level == 3 ? BlockTags.INCORRECT_FOR_DIAMOND_TOOL : BlockTags.INCORRECT_FOR_IRON_TOOL,
                1561, speed, damage, enchantment, ItemTags.IRON_TOOL_MATERIALS);
        for (Kind kind : Kind.values()) result.put(culture + kind.name().toLowerCase(java.util.Locale.ROOT), new Definition(kind, material, level));
    }
}
