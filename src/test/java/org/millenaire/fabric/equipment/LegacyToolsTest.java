package org.millenaire.fabric.equipment;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LegacyToolsTest {
    private static final Map<TagKey<Block>, List<Holder<Block>>> SAVED_TAGS = new HashMap<>();
    private static HolderLookup.Provider lookup;

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var registry = BuiltInRegistries.BLOCK;
        registry.getTags().filter(HolderSet.Named::isBound).forEach(tag -> SAVED_TAGS.put(tag.key(), tag.stream().toList()));
        var tags = new HashMap<>(SAVED_TAGS);
        tags.put(BlockTags.MINEABLE_WITH_PICKAXE, List.of(Blocks.STONE.builtInRegistryHolder(), Blocks.OBSIDIAN.builtInRegistryHolder()));
        tags.put(BlockTags.MINEABLE_WITH_AXE, List.of(Blocks.OAK_LOG.builtInRegistryHolder()));
        tags.put(BlockTags.MINEABLE_WITH_SHOVEL, List.of(Blocks.DIRT.builtInRegistryHolder()));
        tags.put(BlockTags.MINEABLE_WITH_HOE, List.of(Blocks.OAK_LEAVES.builtInRegistryHolder()));
        tags.put(BlockTags.INCORRECT_FOR_IRON_TOOL, List.of(Blocks.OBSIDIAN.builtInRegistryHolder()));
        tags.put(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, List.of());
        registry.prepareTagReload(new TagLoader.LoadResult<>(Registries.BLOCK, tags)).apply();
        lookup = VanillaRegistries.createWorldLookup();
    }

    @AfterAll static void restoreTags() {
        BuiltInRegistries.BLOCK.prepareTagReload(new TagLoader.LoadResult<>(Registries.BLOCK, SAVED_TAGS)).apply();
    }

    @Test void everyToolGetsLegacyDurabilityStackLimitAndEnchantmentValue() throws Exception {
        assertEquals(12, LegacyTools.all().size());
        for (var entry : LegacyTools.all().entrySet()) {
            var components = components(entry.getKey());
            assertEquals(1561, components.get(DataComponents.MAX_DAMAGE));
            assertEquals(1, components.get(DataComponents.MAX_STACK_SIZE));
            int value = entry.getKey().startsWith("norman") ? 10 : entry.getKey().startsWith("byzantine") ? 15 : 25;
            if (entry.getValue().kind() == LegacyTools.Kind.HOE) assertNull(components.get(DataComponents.ENCHANTABLE));
            else assertEquals(value, components.get(DataComponents.ENCHANTABLE).value());
            assertEquals(0, components.get(DataComponents.REPAIRABLE).items().size());
        }
        assertThrows(IllegalArgumentException.class, () -> LegacyTools.configure("normanbroadsword", new Item.Properties()));
    }

    @Test void materialDamageAndAxeOverrideMatchRecoveredCombatAttributes() throws Exception {
        for (var entry : LegacyTools.all().entrySet()) {
            var attributes = components(entry.getKey()).get(DataComponents.ATTRIBUTE_MODIFIERS);
            double bonus = entry.getKey().startsWith("norman") ? 4 : entry.getKey().startsWith("byzantine") ? 3 : 2;
            double expectedDamage = switch (entry.getValue().kind()) {
                case PICKAXE -> 1 + bonus; case AXE -> 8; case SHOVEL -> 1.5 + bonus; case HOE -> 0;
            };
            double expectedSpeed = switch (entry.getValue().kind()) {
                case PICKAXE -> -2.8F; case AXE, SHOVEL -> -3; case HOE -> bonus - 3;
            };
            assertEquals(expectedDamage, attributes.compute(Attributes.ATTACK_DAMAGE, 0, EquipmentSlot.MAINHAND), 0.00001);
            assertEquals(expectedSpeed, attributes.compute(Attributes.ATTACK_SPEED, 0, EquipmentSlot.MAINHAND), 0.00001);
            assertEquals(0, attributes.compute(Attributes.ATTACK_DAMAGE, 0, EquipmentSlot.OFFHAND));
        }
    }

    @Test void miningSpeedAndHarvestTierAreIndependentOfDurability() throws Exception {
        for (String culture : List.of("norman", "byzantine", "mayan")) {
            float speed = culture.equals("norman") ? 10 : culture.equals("byzantine") ? 12 : 6;
            var pickaxe = components(culture + "pickaxe").get(DataComponents.TOOL);
            assertEquals(speed, pickaxe.getMiningSpeed(Blocks.STONE.defaultBlockState()));
            assertTrue(pickaxe.isCorrectForDrops(Blocks.STONE.defaultBlockState()));
            assertEquals(culture.equals("mayan"), pickaxe.isCorrectForDrops(Blocks.OBSIDIAN.defaultBlockState()));
            assertEquals(1, pickaxe.getMiningSpeed(Blocks.DIRT.defaultBlockState()));
            assertFalse(pickaxe.isCorrectForDrops(Blocks.DIRT.defaultBlockState()));
            assertEquals(speed, components(culture + "axe").get(DataComponents.TOOL).getMiningSpeed(Blocks.OAK_LOG.defaultBlockState()));
            assertEquals(speed, components(culture + "shovel").get(DataComponents.TOOL).getMiningSpeed(Blocks.DIRT.defaultBlockState()));
        }
    }

    @Test void hoesKeepLegacyMiningWearAndUseModernBlockTransformers() throws Exception {
        for (String culture : List.of("norman", "byzantine", "mayan")) {
            var hoe = components(culture + "hoe");
            assertEquals(1, hoe.get(DataComponents.TOOL).getMiningSpeed(Blocks.OAK_LEAVES.defaultBlockState()));
            assertEquals(0, hoe.get(DataComponents.TOOL).damagePerBlock());
            assertEquals(1, hoe.get(DataComponents.WEAPON).itemDamagePerAttack());
            assertNotNull(hoe.get(DataComponents.BLOCK_TRANSFORMER));
            assertNotNull(components(culture + "axe").get(DataComponents.BLOCK_TRANSFORMER));
            assertNotNull(components(culture + "shovel").get(DataComponents.BLOCK_TRANSFORMER));
            assertNull(components(culture + "pickaxe").get(DataComponents.BLOCK_TRANSFORMER));
        }
    }

    @Test void toolTagsAppendAllTwelveIdsForVanillaEnchantmentEligibility() throws Exception {
        var all = new HashSet<String>();
        for (String kind : List.of("pickaxe", "axe", "shovel", "hoe")) {
            try (var stream = getClass().getResourceAsStream("/data/minecraft/tags/item/" + kind + "s.json")) {
                assertNotNull(stream);
                var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                assertFalse(json.get("replace").getAsBoolean());
                var actual = new HashSet<String>();
                json.getAsJsonArray("values").forEach(value -> actual.add(value.getAsString()));
                assertEquals(java.util.Set.of("millenaire:norman" + kind, "millenaire:byzantine" + kind, "millenaire:mayan" + kind), actual);
                all.addAll(actual);
            }
        }
        assertEquals(LegacyTools.all().keySet().stream().map(name -> "millenaire:" + name).collect(java.util.stream.Collectors.toSet()), all);
    }

    private static DataComponentMap components(String name) throws Exception {
        var key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("millenaire", name));
        var properties = LegacyTools.configure(name, new Item.Properties().setId(key));
        // Inspect resolved properties without registering new intrusive holders into frozen test registries.
        var field = Item.Properties.class.getDeclaredField("componentInitializer");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var initializer = (DataComponentInitializers.Initializer<Item>) field.get(properties);
        var builder = DataComponentMap.builder();
        initializer.run(builder, lookup, key);
        return builder.build();
    }
}
