package org.millenaire.fabric;

import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.millenaire.fabric.content.LegacyBuildingPlan.Position;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FabricBuildingStateTest {
    @Test void preservesPlacementAndServicePointsThroughTheCodec() {
        var state = new FabricBuildingState();
        var building = new FabricBuildingState.PlacedBuilding(Identifier.parse("minecraft:overworld"), "norman:fountain_A0",
                new Position(10, 64, 20), 2, Map.of("sleepingPos", List.of(new Position(11, 65, 20))));
        state.record(building);
        var json = FabricBuildingState.codec().encodeStart(JsonOps.INSTANCE, state).result().orElseThrow();
        var loaded = FabricBuildingState.codec().parse(JsonOps.INSTANCE, json).result().orElseThrow();
        assertEquals(List.of(building), loaded.buildings());
        assertThrows(UnsupportedOperationException.class, () -> loaded.buildings().clear());
    }
    @Test void updatesTheRecordAtTheSameOriginAndDimension() {
        var state = new FabricBuildingState();
        var dimension = Identifier.parse("minecraft:overworld");
        state.record(new FabricBuildingState.PlacedBuilding(dimension, "norman:fountain_A0", new Position(0, 64, 0), 0, Map.of()));
        state.record(new FabricBuildingState.PlacedBuilding(dimension, "norman:fountain_A1", new Position(0, 64, 0), 1, Map.of()));
        assertEquals(1, state.buildings().size());
        assertEquals("norman:fountain_A1", state.buildings().getFirst().plan());
        state.record(new FabricBuildingState.PlacedBuilding(dimension, "norman:manor_A_inn_A0", new Position(0, 64, 0), 1, Map.of()));
        assertEquals(2, state.buildings().size(), "a sub-building keeps its parent record");
    }
}
