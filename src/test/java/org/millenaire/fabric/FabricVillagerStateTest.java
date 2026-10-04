package org.millenaire.fabric;

import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.millenaire.fabric.content.LegacyBuildingPlan.Position;

import static org.junit.jupiter.api.Assertions.*;

class FabricVillagerStateTest {
    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");
    @Test void upsertReplaceAndRemove() {
        var state = new FabricVillagerState();
        var first = villager("a", "Alice", true);
        state.upsert(first); state.upsert(villager("a", "Alicia", true));
        assertEquals(1, state.villagers().size()); assertEquals("Alicia", state.find("a").orElseThrow().name());
        assertTrue(state.remove("a")); assertFalse(state.remove("a"));
    }
    @Test void codecRoundTrip() {
        var state = new FabricVillagerState(); state.upsert(villager("a", "Alice", true));
        var encoded = FabricVillagerState.codec().encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        var decoded = FabricVillagerState.codec().parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(state.villagers(), decoded.villagers());
    }
    @Test void rejectsInvalidIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new FabricVillagerState.Villager("", OVERWORLD,
                new Position(0, 64, 0), "norman", "worker", "Alice", "female", "", true));
    }
    private static FabricVillagerState.Villager villager(String id, String name, boolean alive) {
        return new FabricVillagerState.Villager(id, OVERWORLD, new Position(1, 64, 2), "norman", "worker", name, "female", "hall", alive);
    }
}
