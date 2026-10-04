package org.millenaire.fabric.crops;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SaplingAssetsTest {
    @Test
    void everySaplingGrowthStageUsesAnExistingModel() throws Exception {
        for (String name : new String[]{"sapling_appletree", "sapling_cherry", "sapling_olivetree",
                "sapling_pistachio", "sapling_sakura"}) {
            var path = "/assets/millenaire/blockstates/" + name + ".json";
            var stream = getClass().getResourceAsStream(path);
            assertNotNull(stream, path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                var variants = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("variants");
                assertEquals(2, variants.size(), name);
                for (int stage = 0; stage <= 1; stage++) {
                    var model = variants.getAsJsonObject("stage=" + stage).get("model").getAsString();
                    assertNotNull(getClass().getResource("/assets/millenaire/models/"
                            + model.split(":", 2)[1] + ".json"), model);
                }
            }
        }
    }
}
