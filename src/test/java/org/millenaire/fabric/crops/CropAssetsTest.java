package org.millenaire.fabric.crops;

import com.google.gson.JsonObject;
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

class CropAssetsTest {
    @Test
    void cropLootSchemaDecodesWithMinecraft26Codec() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        for (String crop : new String[]{"crop_maize", "crop_cotton", "crop_rice", "crop_turmeric", "crop_vine"}) {
            var table = json("/data/millenaire/loot_table/blocks/" + crop + ".json");
            // Vanilla registry stand-ins let the plain JUnit runtime validate
            // the codec without attempting Fabric registration after freeze.
            for (var pool : table.getAsJsonArray("pools")) {
                pool.getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject()
                        .addProperty("name", "minecraft:wheat_seeds");
            }
            table.getAsJsonArray("pools").get(1).getAsJsonObject().getAsJsonObject("condition")
                    .getAsJsonArray("terms").get(1).getAsJsonObject().addProperty("blocks", "minecraft:wheat");
            var result = LootTable.DIRECT_CODEC.parse(ops, table);
            assertTrue(result.error().isEmpty(), crop + ": " + result.error());
            var encoded = LootTable.DIRECT_CODEC.encodeStart(ops, result.result().orElseThrow())
                    .result().orElseThrow().getAsJsonObject();
            assertTrue(encoded.getAsJsonArray("pools").get(1).getAsJsonObject().has("condition"));
        }
    }
    @Test
    void everyLegacyCropStageHasAnExistingModel() throws Exception {
        for (String crop : new String[]{"crop_maize", "crop_cotton", "crop_rice", "crop_turmeric", "crop_vine"}) {
            JsonObject variants = json("/assets/millenaire/blockstates/" + crop + ".json").getAsJsonObject("variants");
            String[] halves = crop.equals("crop_vine") ? new String[]{",half=lower", ",half=upper"} : new String[]{""};
            assertEquals(8 * halves.length, variants.size());
            for (String half : halves) {
                for (int age = 0; age <= 7; age++) {
                    String key = "age=" + age + half;
                    assertTrue(variants.has(key), crop + ":" + key);
                    String model = variants.getAsJsonObject(key).get("model").getAsString();
                    assertNotNull(getClass().getResource("/assets/millenaire/models/" + model.split(":")[1] + ".json"), model);
                }
            }
        }
    }
    @Test
    void harvestDropsPlantableItemsAndRequiresMaturityForExtraSeeds() throws Exception {
        Map<String, String> seeds = Map.of("crop_maize", "maize", "crop_cotton", "cotton",
                "crop_rice", "rice", "crop_turmeric", "turmeric", "crop_vine", "grapes");
        for (var crop : seeds.entrySet()) {
            var pools = json("/data/millenaire/loot_table/blocks/" + crop.getKey() + ".json").getAsJsonArray("pools");
            assertEquals(2, pools.size());
            for (var pool : pools) {
                assertEquals("millenaire:" + crop.getValue(), pool.getAsJsonObject().getAsJsonArray("entries")
                        .get(0).getAsJsonObject().get("name").getAsString());
            }
            var maturity = pools.get(1).getAsJsonObject().getAsJsonObject("condition")
                    .getAsJsonArray("terms").get(1).getAsJsonObject();
            assertEquals("millenaire:" + crop.getKey(), maturity.get("blocks").getAsString());
            assertEquals("7", maturity.getAsJsonObject("state").get("age").getAsString());
        }
    }

    private JsonObject json(String path) throws Exception {
        var stream = getClass().getResourceAsStream(path);
        assertNotNull(stream, path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}

