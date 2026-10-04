package org.millenaire.fabric.villager;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.millenaire.fabric.FabricBuildingState;
import org.millenaire.fabric.content.LegacyBuildingPlan;
import org.millenaire.fabric.content.LegacyCatalogLoader;
import org.millenaire.fabric.villager.client.MillVillagerModels;
import org.millenaire.fabric.villager.client.MillVillagerRenderState;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VillagerModelAndResidentsTest {
    @Test
    void allBodyAndClothingLayersBakeIntoHumanoidModels() {
        for (VillagerProfile.Model model : VillagerProfile.Model.values())
            for (boolean cloth : new boolean[]{false, true}) {
                var root = MillVillagerModels.create(model, cloth).bakeRoot();
                var humanoid = new HumanoidModel<MillVillagerRenderState>(root);
                assertNotNull(humanoid.hat, model + " cloth=" + cloth);
                assertEquals(model != VillagerProfile.Model.MALE, root.getChild("body").hasChild("breast"));
            }
    }

    @Test
    void residentsComeFromPlanAndUseSleepingPositionsInOrder() throws Exception {
        Path root = Path.of(getClass().getResource("/todeploy/millenaire/blocklist.txt").toURI()).getParent();
        var catalog = LegacyCatalogLoader.load(root);
        var plan = catalog.plans().get("norman:carpenterhouse_A0");
        assertNotNull(plan);
        var beds = List.of(new LegacyBuildingPlan.Position(1, 65, 1));
        var placed = new FabricBuildingState.PlacedBuilding(Identifier.withDefaultNamespace("overworld"), plan.id(),
                new LegacyBuildingPlan.Position(0, 64, 0), 0, Map.of("sleepingPos", beds));
        var residents = VillagerSpawning.residents(plan, placed);
        assertFalse(residents.isEmpty());
        assertEquals(beds.getFirst(), residents.getFirst().position());
        if (residents.size() > 1) assertEquals(new LegacyBuildingPlan.Position(0, 65, 0), residents.get(1).position());
        var snapshot = VillagerSpawning.Snapshot.of(catalog);
        assertEquals(List.of(), snapshot.diagnostics());
        // Every resident named by any bundled plan resolves to a villager type of its culture.
        List<String> unknown = new ArrayList<>();
        for (var candidate : catalog.plans().values())
            for (var resident : VillagerSpawning.residents(candidate, new FabricBuildingState.PlacedBuilding(
                    Identifier.withDefaultNamespace("overworld"), candidate.id(), new LegacyBuildingPlan.Position(0, 0, 0), 0, Map.of())))
                if (!snapshot.profiles().containsKey(resident.profileId())) unknown.add(candidate.id() + " -> " + resident.profileId());
        assertEquals(List.of(), unknown);
    }
}
