package org.millenaire.fabric.economy;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.millenaire.fabric.content.LegacyCatalogLoader;
import org.millenaire.fabric.content.LegacyDocument;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LegacyGoodsCatalogTest {
    @TempDir Path temp;
    @Test void overlaysIndividualAliasesAndDoesNotReuseBrokenOverrides() throws Exception {
        Path base = temp.resolve("base"), custom = temp.resolve("custom"); Files.createDirectories(base); Files.createDirectories(custom);
        Files.writeString(base.resolve("itemlist.txt"), "bread;minecraft:bread;0\nwheat;minecraft:wheat;0\n");
        Files.writeString(custom.resolve("itemlist.txt"), "bread;minecraft:cookie;0\nextra;minecraft:stone;0\n");
        var catalog = LegacyCatalogLoader.load(base, custom);
        assertEquals("minecraft:cookie", catalog.goods().require("bread").legacyId());
        assertEquals("minecraft:wheat", catalog.goods().require("WHEAT").legacyId()); assertEquals(3, catalog.goods().goods().size());
        assertThrows(UnsupportedOperationException.class, () -> catalog.goods().goods().clear());
        Files.writeString(custom.resolve("itemlist.txt"), "bread;minecraft:bread;broken\n");
        var broken = LegacyCatalogLoader.load(base, custom);
        assertThrows(IllegalArgumentException.class, () -> broken.goods().require("bread")); assertFalse(broken.diagnostics().isEmpty());
        assertEquals("minecraft:wheat", broken.goods().require("wheat").legacyId());
    }
    @Test void convertsEveryConcreteBundledAliasAndValidatesEveryStartingDeclaration() throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        Path bundle = Path.of(getClass().getResource("/todeploy/millenaire/itemlist.txt").toURI()).getParent();
        var goods = LegacyGoodsCatalog.empty().overlay(LegacyDocument.read(bundle.resolve("itemlist.txt"), "itemlist.txt"));
        assertTrue(goods.diagnostics().isEmpty(), goods.diagnostics().toString());
        Set<String> modItems = new HashSet<>();
        for (String manifest : List.of("legacy_blocks.txt", "legacy_items.txt")) {
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(getClass().getResourceAsStream("/data/millenaire/" + manifest), java.nio.charset.StandardCharsets.UTF_8))) {
                reader.lines().map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#") && !line.startsWith("//")).forEach(name -> modItems.add("millenaire:" + name));
            }
        }
        modItems.add("millenaire:paintbucketwhite");
        int converted = 0, wildcards = 0;
        List<String> missing = new ArrayList<>();
        for (var good : goods.goods().values()) {
            if (good.metadata() == -1) { wildcards++; continue; }
            var target = LegacyItemResolver.resolve(good);
            assertEquals(0, target.damage(), good.toString());
            if (!(target.id().getNamespace().equals("millenaire") ? modItems.contains(target.id().toString())
                    : BuiltInRegistries.ITEM.containsKey(target.id()))) missing.add(good + " -> " + target);
            converted++;
        }
        assertTrue(missing.isEmpty(), "Unregistered goods: " + missing);
        var catalog = LegacyCatalogLoader.load(bundle);
        int rules = 0;
        for (var plan : catalog.plans().values()) for (String declaration : plan.parameters().getOrDefault("startinggood", List.of())) {
            var rule = StartingStock.Rule.parse(declaration); var good = goods.require(rule.good());
            assertTrue(good.metadata() >= 0); LegacyItemResolver.resolve(good); rules++;
        }
        System.out.println("Goods: aliases=" + goods.goods().size() + ", concrete=" + converted + ", wildcard=" + wildcards + ", indexed starting rules=" + rules);
        // The legacy file contains nine repeated keys; later declarations replace earlier ones.
        assertEquals(492, goods.goods().size()); assertEquals(491, converted); assertEquals(1, wildcards); assertTrue(rules > 900);
    }
}
