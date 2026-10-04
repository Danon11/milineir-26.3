package org.millenaire.fabric;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FabricSettlementLifecycleStateTest {
    @Test void queuesConsumesMaterialsAndCompletes() {
        var state = new FabricSettlementLifecycleState();
        String key = "minecraft:overworld@0,64,0";
        state.ensure(new FabricSettlementState.Settlement(
                net.minecraft.resources.Identifier.withDefaultNamespace("overworld"), "norman:test", "Test",
                new org.millenaire.fabric.content.LegacyBuildingPlan.Position(0, 64, 0), 1, 80,
                List.of(new FabricSettlementState.Building(
                        new FabricBuildingState.PlacedBuilding(net.minecraft.resources.Identifier.withDefaultNamespace("overworld"), "norman:hall", new org.millenaire.fabric.content.LegacyBuildingPlan.Position(0,64,0), 0, java.util.Map.of()),
                        new org.millenaire.fabric.content.VillageLayout.Bounds(-2,-2,2,2), true))));
        state.addResources(key, 0, 2); state.queue(key, "hall-upgrade", "norman:hall", 2, 2);
        state.tick(); state.tick();
        var lifecycle = state.find(key).orElseThrow();
        assertEquals(0, lifecycle.materials()); assertEquals(FabricSettlementLifecycleState.Project.Status.COMPLETE, lifecycle.projects().getFirst().status());
    }
    @Test void codecRoundTrip() {
        var state = new FabricSettlementLifecycleState();
        var lifecycle = new FabricSettlementLifecycleState.Lifecycle("test", 1, 4, 3, 2, 0, 7,
                List.of(new FabricSettlementLifecycleState.Project("p", "plan", 3, 1, 1, FabricSettlementLifecycleState.Project.Status.ACTIVE)));
        var encoded = FabricSettlementLifecycleState.codec().encodeStart(JsonOps.INSTANCE, new FabricSettlementLifecycleState(List.of(lifecycle))).getOrThrow();
        assertEquals(lifecycle, FabricSettlementLifecycleState.codec().parse(JsonOps.INSTANCE, encoded).getOrThrow().lifecycles().getFirst());
    }
    @Test void rejectsDuplicateProjects() {
        var state = new FabricSettlementLifecycleState();
        String key = "minecraft:overworld@0,64,0";
        state.ensure(new FabricSettlementState.Settlement(
                net.minecraft.resources.Identifier.withDefaultNamespace("overworld"), "norman:test", "Test",
                new org.millenaire.fabric.content.LegacyBuildingPlan.Position(0, 64, 0), 1, 80,
                List.of(new FabricSettlementState.Building(
                        new FabricBuildingState.PlacedBuilding(net.minecraft.resources.Identifier.withDefaultNamespace("overworld"), "norman:hall", new org.millenaire.fabric.content.LegacyBuildingPlan.Position(0,64,0), 0, java.util.Map.of()),
                        new org.millenaire.fabric.content.VillageLayout.Bounds(-2,-2,2,2), true))));
        state.addResources(key, 0, 1);
        state.queue(key, "same", "norman:hall", 1, 1);
        assertThrows(IllegalArgumentException.class, () -> state.queue(key, "same", "norman:hall", 1, 1));
    }
}
