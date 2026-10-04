package org.millenaire.fabric.content;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.millenaire.fabric.content.LegacyBuildingPlan.Position;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class VillagePlacementTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void validatesEveryBuildingBeforeWritingAnyOfThem() throws Exception {
        var fixture = fixture(true, false);
        var prepared = VillagePlacement.prepare(fixture.layout(), fixture.palette());
        assertEquals(2, prepared.buildings().size());
        assertTrue(prepared.buildings().getFirst().blocks().supported());
        assertFalse(prepared.supported()); assertTrue(prepared.combined().changes().isEmpty());
        var world = new MemoryWorld();
        assertThrows(IllegalArgumentException.class, () -> VillagePlacement.place(prepared, world, true));
        assertEquals(0, world.writes);
    }

    @Test void destinationFailureInLastHousePreventsTheCentreFromBeingPlaced() throws Exception {
        var fixture = fixture(false, false);
        var prepared = VillagePlacement.prepare(fixture.layout(), fixture.palette());
        var world = new MemoryWorld(); world.blocked = prepared.buildings().getLast().blocks().changes().getFirst().pos();
        assertThrows(IllegalArgumentException.class, () -> VillagePlacement.place(prepared, world, true));
        assertEquals(0, world.writes);
    }

    @Test void writeFailureRestoresBlocksAcrossBuildingBoundaries() throws Exception {
        var fixture = fixture(false, false);
        var prepared = VillagePlacement.prepare(fixture.layout(), fixture.palette());
        var world = new MemoryWorld(); world.failAt = 2;
        assertThrows(IllegalStateException.class, () -> VillagePlacement.place(prepared, world, true));
        for (var change : prepared.combined().changes()) assertTrue(world.get(change.pos()).isAir());
        var success = new MemoryWorld();
        assertEquals(2, VillagePlacement.place(prepared, success, false));
        for (var change : prepared.combined().changes()) assertEquals(change.state(), success.get(change.pos()));
    }

    @Test void declaredWallsAreReportedAndNeverSilentlyOmitted() throws Exception {
        var fixture = fixture(false, true);
        var prepared = VillagePlacement.prepare(fixture.layout(), fixture.palette());
        assertFalse(prepared.supported());
        assertTrue(prepared.combined().issues().stream().anyMatch(issue -> issue.contains("outerwalltype")));
        var world = new MemoryWorld();
        assertThrows(IllegalArgumentException.class, () -> VillagePlacement.place(prepared, world, true));
        assertEquals(0, world.writes);
    }

    @Test void unportedHamletAndSubBuildingGeneratorsAlsoPreventPartialResults() throws Exception {
        var fixture = fixture(false, false);
        var base = fixture.layout();
        var source = new LegacyDocument("test", "UTF-8", List.of(), Map.of("centre", List.of("hall"),
                "start", List.of("house"), "hameau", List.of("nearbyhamlet")));
        var type = VillageTypeDefinition.from("demo", "test", source);
        var first = base.buildings().getFirst();
        var plan = first.plan();
        var withSubBuilding = new LegacyBuildingPlan(plan.culture(), plan.key(), plan.variation(), plan.upgrade(),
                plan.width(), plan.length(), plan.startLevel(), plan.image(), Map.of("startingsubbuilding", List.of("annex")));
        var buildings = new ArrayList<>(base.buildings());
        buildings.set(0, new VillageLayout.Building(withSubBuilding, first.origin(), first.rotation(), true, first.reservedArea()));
        var layout = new VillageLayout.Layout(type, base.origin(), base.seed(), base.radius(), buildings, List.of());
        var prepared = VillagePlacement.prepare(layout, fixture.palette());
        assertFalse(prepared.supported());
        assertTrue(prepared.combined().issues().stream().anyMatch(issue -> issue.contains("hameau")));
        assertTrue(prepared.combined().issues().stream().anyMatch(issue -> issue.contains("sub-buildings")));
        assertTrue(prepared.combined().changes().isEmpty());
    }

    @Test void groupPlacementInitializesEveryChestWithItsOwnBuildingIdentity() throws Exception {
        var fixture = fixture(false, false);
        var palette = new LegacyPalette(Map.of(
                0xff0000, new LegacyPalette.Point(0xff0000, "mainchestGuess", "", "0", false, "", "", 0),
                0x0000ff, new LegacyPalette.Point(0x0000ff, "lockedchestGuess", "", "0", false, "", "", 0)));
        var prepared = VillagePlacement.prepare(fixture.layout(), palette, point -> Blocks.CHEST.defaultBlockState());
        assertTrue(prepared.supported(), prepared.combined().issues().toString());
        var world = new MemoryWorld(); assertEquals(2, VillagePlacement.place(prepared, world, false));
        assertEquals(2, world.initialized.size());
        for (var building : fixture.layout().buildings()) assertTrue(world.initialized.values().stream()
                .anyMatch(setup -> setup.binding().plan().equals(building.plan().id())
                        && setup.binding().origin().equals(new BlockPos(building.origin().x(), building.origin().y(), building.origin().z()))));
    }

    private record Fixture(VillageLayout.Layout layout, LegacyPalette palette) {}
    private Fixture fixture(boolean unsupported, boolean walls) throws Exception {
        var hall = plan("hall", 0xff0000); var house = plan("house", 0x0000ff);
        var palette = new LegacyPalette(Map.of(
                0xff0000, new LegacyPalette.Point(0xff0000, "stone", "minecraft:stone", "0", false, "", "", 1),
                0x0000ff, new LegacyPalette.Point(0x0000ff, "second", unsupported ? "minecraft:missing_block" : "minecraft:stonebrick", "0", false, "", "", 1)));
        Map<String, List<String>> fields = new HashMap<>(Map.of("centre", List.of("hall"), "start", List.of("house")));
        if (walls) fields.put("outerwalltype", List.of("borderposts"));
        var type = VillageTypeDefinition.from("demo", "test", new LegacyDocument("test", "UTF-8", List.of(), fields));
        var catalog = new LegacyContentCatalog(Map.of(), Map.of(), Map.of(hall.id(), hall, house.id(), house), palette, List.of());
        var layout = VillageLayout.create(catalog, type, new Position(0, 64, 0), 1234);
        assertTrue(layout.complete(), layout.issues().toString());
        return new Fixture(layout, palette);
    }
    private LegacyBuildingPlan plan(String key, int color) throws Exception {
        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB); image.setRGB(0, 0, color);
        Path path = temp.resolve(key + "_A0.png"); ImageIO.write(image, "png", path.toFile());
        return new LegacyBuildingPlan("demo", key, 'A', 0, 1, 1, 0, path, Map.of("areatoclear", List.of("0")));
    }
    private static class MemoryWorld implements BuildingPlacement.WorldAccess {
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        final Map<BlockPos, BuildingPlacement.Setup> initialized = new HashMap<>();
        int writes, failAt = -1; BlockPos blocked;
        public BlockState get(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        public String rejection(BlockPos pos, boolean replace) { return pos.equals(blocked) ? "Protected position" : null; }
        public boolean set(BlockPos pos, BlockState state) {
            if (++writes == failAt) return false;
            blocks.put(pos, state); return true;
        }
        public String setupRejection(BuildingPlacement.Setup setup) { return null; }
        public void initialize(BuildingPlacement.Setup setup) { initialized.put(setup.pos(), setup); }
    }
}
