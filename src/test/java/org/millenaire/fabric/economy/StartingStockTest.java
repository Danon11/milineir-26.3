package org.millenaire.fabric.economy;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class StartingStockTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        for (var item : List.of(Items.BREAD, Items.WHEAT, Items.IRON_SWORD, Items.POTION)) {
            if (item.builtInRegistryHolder().areComponentsBound()) continue;
            var data = DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, item == Items.IRON_SWORD || item == Items.POTION ? 1 : 64);
            if (item == Items.IRON_SWORD) data.set(DataComponents.MAX_DAMAGE, 250).set(DataComponents.DAMAGE, 0);
            item.builtInRegistryHolder().bindComponents(data.build());
        }
    }
    private static LegacyGoodsCatalog goods() {
        return new LegacyGoodsCatalog(Map.of("bread", new LegacyGoodsCatalog.Good("bread", "minecraft:bread", 0),
                "wheat", new LegacyGoodsCatalog.Good("wheat", "minecraft:wheat", 0),
                "sword", new LegacyGoodsCatalog.Good("sword", "minecraft:iron_sword", 0)), List.of());
    }
    private static int count(StartingStock.Prepared prepared) {
        return prepared.inventories().stream().flatMap(stock -> stock.slots().stream()).mapToInt(StartingStock.Slot::count).sum();
    }
    @Test void reproducesChanceAndInclusiveBonusWithDeterministicChestSelection() {
        List<BlockPos> chests = List.of(BlockPos.ZERO, new BlockPos(1, 0, 0));
        Set<Integer> bonuses = new HashSet<>(); Set<BlockPos> chosen = new HashSet<>(); boolean accepted = false, skipped = false;
        for (int seed = 0; seed < 256; seed++) {
            var prepared = StartingStock.prepare(List.of("bread,2.25,8,5", "wheat,0,99,0"), chests, goods(), seed);
            assertTrue(prepared.supported(), prepared.issues().toString()); assertEquals(2, prepared.inventories().size());
            assertEquals(prepared, StartingStock.prepare(List.of("bread,2.25,8,5", "wheat,0,99,0"), chests, goods(), seed));
            bonuses.add(count(prepared) - 8);
            prepared.inventories().stream().filter(stock -> !stock.slots().isEmpty()).forEach(stock -> chosen.add(stock.pos()));
            var chance = StartingStock.prepare(List.of("bread,0.5,1,0"), chests, goods(), seed * 8191L);
            accepted |= count(chance) == 1; skipped |= count(chance) == 0;
        }
        assertEquals(Set.of(0, 1, 2, 3, 4, 5), bonuses); assertEquals(Set.copyOf(chests), chosen);
        assertTrue(accepted); assertTrue(skipped);
    }
    @Test void mergesRepeatedGoodsAndSplitsAtActualItemStackLimits() {
        var prepared = StartingStock.prepare(List.of("bread,1,70,0", "bread,1,60,0", "sword,1,2,0"), List.of(BlockPos.ZERO), goods(), 7);
        assertTrue(prepared.supported(), prepared.issues().toString());
        assertEquals(List.of(64, 64, 2, 1, 1), prepared.inventories().getFirst().slots().stream().map(StartingStock.Slot::count).toList());
        var container = new SimpleContainer(27); StartingStock.apply(container, prepared.inventories().getFirst());
        assertEquals(64, container.getItem(0).getCount()); assertEquals(Items.IRON_SWORD, container.getItem(3).getItem());
        assertThrows(IllegalStateException.class, () -> StartingStock.apply(container, prepared.inventories().getFirst()));
    }
    @Test void rejectsBadUnknownWildcardAndOverflowRulesWithoutReturningPartialStock() {
        for (String rule : List.of("bread,NaN,1,0", "bread,Infinity,1,0", "bread,1,-1,0", "bread,1,0,-1",
                "bread,1,1000000,1", "bread,1,2", "unknown,1,1,0", "bread,1,1729,0", "sword,1,28,0")) {
            var stock = StartingStock.prepare(List.of(rule), List.of(BlockPos.ZERO), goods(), 1);
            assertFalse(stock.supported(), rule); assertTrue(stock.inventories().isEmpty());
        }
        var wildcard = new LegacyGoodsCatalog(Map.of("wood", new LegacyGoodsCatalog.Good("wood", "minecraft:log", -1)), List.of());
        assertFalse(StartingStock.prepare(List.of("wood,1,1,0"), List.of(BlockPos.ZERO), wildcard, 0).supported());
        assertFalse(StartingStock.prepare(List.of("bread,1,1,0"), List.of(), goods(), 0).supported());
        assertTrue(StartingStock.prepare(List.of(), List.of(), goods(), 0).supported());
    }
    @Test void convertsMetadataAndRetainsDamageAndPotionComponents() {
        assertEquals("minecraft:spruce_log", LegacyItemResolver.translate("minecraft:log", 1).id().toString());
        assertEquals("minecraft:bone_meal", LegacyItemResolver.translate("minecraft:dye", 15).id().toString());
        assertEquals("minecraft:lapis_lazuli", LegacyItemResolver.translate("minecraft:dye", 4).id().toString());
        assertEquals("minecraft:white_banner", LegacyItemResolver.translate("minecraft:banner", 15).id().toString());
        assertEquals("minecraft:snow_block", LegacyItemResolver.translate("minecraft:snow", 0).id().toString());
        assertEquals("millenaire:paintbucketwhite", LegacyItemResolver.translate("millenaire:paint_bucket_white", 0).id().toString());
        assertEquals("millenaire:mudbrick", LegacyItemResolver.translate("millenaire:stone_deco", 0).id().toString());
        assertEquals(12, LegacyItemResolver.translate("minecraft:iron_sword", 12).stack().getDamageValue());
        var potion = LegacyItemResolver.translate("minecraft:potion", 16).stack();
        assertEquals(Identifier.withDefaultNamespace("awkward"), potion.get(DataComponents.POTION_CONTENTS).potion().orElseThrow().unwrapKey().orElseThrow().identifier());
        assertThrows(IllegalArgumentException.class, () -> LegacyItemResolver.translate("minecraft:bread", 3).stack());
        assertThrows(IllegalArgumentException.class, () -> LegacyItemResolver.translate("minecraft:wool", 16));
    }
}
