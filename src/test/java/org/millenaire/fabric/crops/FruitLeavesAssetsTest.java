package org.millenaire.fabric.crops;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FruitLeavesAssetsTest {
    private static final Map<String, String> LEAVES = Map.of(
            "leaves_appletree", "ciderapple",
            "leaves_olivetree", "olives",
            "leaves_pistachio", "pistachios",
            "cherry_leaves", "cherries",
            "sakura_leaves", "cherry_blossom");

    @Test
    void allFruitStagesHaveModelsAndHarvestItems() throws Exception {
        for (var entry : LEAVES.entrySet()) {
            var path = "/assets/millenaire/blockstates/" + entry.getKey() + ".json";
            var stream = getClass().getResourceAsStream(path);
            assertNotNull(stream, path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                var multipart = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("multipart");
                assertEquals(4, multipart.size(), entry.getKey());
                for (int age = 0; age < 4; age++) {
                    var part = multipart.get(age).getAsJsonObject();
                    assertEquals(Integer.toString(age), part.getAsJsonObject("when").get("age").getAsString());
                    var model = part.getAsJsonObject("apply").get("model").getAsString();
                    assertNotNull(getClass().getResource("/assets/millenaire/models/"
                            + model.split(":", 2)[1] + ".json"), model);
                }
            }
            assertNotNull(getClass().getResource("/assets/millenaire/items/" + entry.getValue() + ".json"),
                    entry.getValue());
        }
    }

    @Test
    void leafLootUsesSilkOrShearsAndOtherwiseDropsSapling() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var saplings = Map.of(
                "leaves_appletree", "sapling_appletree",
                "leaves_olivetree", "sapling_olivetree",
                "leaves_pistachio", "sapling_pistachio",
                "cherry_leaves", "sapling_cherry",
                "sakura_leaves", "sapling_sakura");
        for (var entry : saplings.entrySet()) {
            var path = "/data/millenaire/loot_table/blocks/" + entry.getKey() + ".json";
            var stream = getClass().getResourceAsStream(path);
            assertNotNull(stream, path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                var table = JsonParser.parseReader(reader).getAsJsonObject();
                var children = table.getAsJsonArray("pools").get(0).getAsJsonObject()
                        .getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonArray("children");
                assertEquals("millenaire:" + entry.getKey(), children.get(0).getAsJsonObject().get("name").getAsString());
                assertEquals("millenaire:" + entry.getValue(), children.get(1).getAsJsonObject().get("name").getAsString());
                var silk = children.get(0).getAsJsonObject();
                assertEquals("minecraft:any_of", silk.getAsJsonObject("condition").get("type").getAsString());
                var sapling = children.get(1).getAsJsonObject();
                assertEquals("minecraft:table_bonus", sapling.getAsJsonObject("condition")
                        .getAsJsonArray("terms").get(1).getAsJsonObject().get("type").getAsString());
                // The JUnit registry lacks dynamic predicate and enchantment entries.
                silk.add("condition", JsonParser.parseString("{\"type\":\"minecraft:survives_explosion\"}"));
                sapling.add("condition", JsonParser.parseString("{\"type\":\"minecraft:survives_explosion\"}"));
                children.get(0).getAsJsonObject().addProperty("name", "minecraft:oak_leaves");
                children.get(1).getAsJsonObject().addProperty("name", "minecraft:oak_sapling");
                var result = LootTable.DIRECT_CODEC.parse(ops, table);
                assertTrue(result.error().isEmpty(), entry.getKey() + ": " + result.error());
            }
        }
    }
}
