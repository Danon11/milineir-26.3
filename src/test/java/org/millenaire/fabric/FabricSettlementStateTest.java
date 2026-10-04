package org.millenaire.fabric;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.millenaire.fabric.content.LegacyBuildingPlan.Position;
import org.millenaire.fabric.content.VillageLayout.Bounds;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FabricSettlementStateTest {
    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Identifier NETHER = Identifier.fromNamespaceAndPath("minecraft", "the_nether");

    @Test void roundTripPreservesAllBuildingsSeedServicePointsAndReservedAreas() {
        var state = new FabricSettlementState(); state.record(settlement(OVERWORLD, 0));
        var encoded = FabricSettlementState.codec().encodeStart(NbtOps.INSTANCE, state).getOrThrow();
        var decoded = FabricSettlementState.codec().parse(NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals(state.settlements(), decoded.settlements());
        assertEquals(2, decoded.settlements().getFirst().buildings().size());
        assertEquals(Long.MAX_VALUE, decoded.settlements().getFirst().seed());
    }

    @Test void rejectsOverlapsWithoutDirtyingStateAndKeepsDifferentDimensions() {
        var state = new FabricSettlementState(); state.record(settlement(OVERWORLD, 0)); state.setDirty(false);
        assertThrows(IllegalArgumentException.class, () -> state.record(settlement(OVERWORLD, 1)));
        assertFalse(state.isDirty()); assertEquals(1, state.settlements().size());
        state.record(settlement(NETHER, 0)); state.record(settlement(OVERWORLD, 100));
        assertTrue(state.isDirty()); assertEquals(3, state.settlements().size());
    }

    @Test void emptySavedDataIsCompatibleAndBuildingDimensionsMustMatch() {
        assertTrue(FabricSettlementState.codec().parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow().settlements().isEmpty());
        var sample = settlement(OVERWORLD, 0);
        assertThrows(IllegalArgumentException.class, () -> new FabricSettlementState.Settlement(NETHER, sample.type(), sample.name(),
                sample.origin(), sample.seed(), sample.radius(), sample.buildings()));
        assertThrows(IllegalArgumentException.class, () -> new FabricSettlementState.Settlement(OVERWORLD, sample.type(), sample.name(),
                sample.origin(), sample.seed(), sample.radius(), List.of()));
    }

    private static FabricSettlementState.Settlement settlement(Identifier dimension, int offset) {
        var origin = new Position(offset, 64, 0);
        var hall = new FabricBuildingState.PlacedBuilding(dimension, "demo:hall_A0", origin, 0,
                Map.of("sellingPos", List.of(new Position(offset + 1, 65, 0))));
        var house = new FabricBuildingState.PlacedBuilding(dimension, "demo:house_B0", new Position(offset + 12, 64, 0), 2,
                Map.of("sleepingPos", List.of(new Position(offset + 12, 65, 1))));
        return new FabricSettlementState.Settlement(dimension, "demo:test", "Demo", origin, Long.MAX_VALUE, 80,
                List.of(new FabricSettlementState.Building(hall, new Bounds(offset - 3, -3, offset + 3, 3), true),
                        new FabricSettlementState.Building(house, new Bounds(offset + 9, -3, offset + 15, 3), false)));
    }
}
