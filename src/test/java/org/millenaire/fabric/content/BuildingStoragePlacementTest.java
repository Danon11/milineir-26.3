package org.millenaire.fabric.content;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BuildingStoragePlacementTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void connectsMainAndSecondaryChestMarkersAfterEveryRotation() throws Exception {
        var palette = palette("mainchestTop", "lockedchestTop");
        var plan = plan(new int[][]{{0xffffff, 0xffffff, 0xffffff}, {0xffffff, 0x0000ff, 0xff0000}, {0xffffff, 0xffffff, 0xffffff}});
        for (int rotation = 0; rotation < 4; rotation++) {
            var prepared = prepare(plan, palette, rotation);
            assertTrue(prepared.supported(), prepared.issues().toString()); assertEquals(2, prepared.setups().size());
            var states = states(prepared);
            var first = prepared.setups().get(0); var second = prepared.setups().get(1);
            assertNotEquals(states.get(first.pos()).getValue(ChestBlock.TYPE), ChestType.SINGLE);
            assertEquals(second.pos(), ChestBlock.getConnectedBlockPos(first.pos(), states.get(first.pos())));
            assertEquals(first.pos(), ChestBlock.getConnectedBlockPos(second.pos(), states.get(second.pos())));
            assertTrue(prepared.setups().stream().anyMatch(setup -> setup.binding().mainChest()));
            assertEquals(2, prepared.servicePoints().get("chests").size()); assertEquals(1, prepared.servicePoints().get("mainChests").size());
            assertTrue(prepared.setups().stream().allMatch(setup -> setup.binding().locked() && setup.binding().owner().isEmpty()));
        }
    }

    @Test void guessesChestAndPanelFacingFromSupportingBlocksInThePlan() throws Exception {
        var palette = palette("mainchestGuess", "signwallGuess");
        var plan = plan(new int[][]{{0xffffff, 0x555555, 0xffffff}, {0xffffff, 0x0000ff, 0xffffff}, {0x555555, 0xff0000, 0xffffff}});
        var prepared = prepare(plan, palette, 0); assertTrue(prepared.supported(), prepared.issues().toString());
        var states = states(prepared);
        var chest = prepared.setups().stream().filter(s -> s.kind() == BuildingPlacement.SetupKind.CHEST).findFirst().orElseThrow();
        var panel = prepared.setups().stream().filter(s -> s.kind() == BuildingPlacement.SetupKind.PANEL).findFirst().orElseThrow();
        assertEquals(Direction.EAST, states.get(chest.pos()).getValue(ChestBlock.FACING));
        assertEquals(Direction.SOUTH, states.get(panel.pos()).getValue(WallSignBlock.FACING));
        assertEquals(2, prepared.setups().size());
        assertEquals(1, prepared.servicePoints().get("panels").size());
    }

    @Test void rejectsUnsupportedEntityInitializationBeforeAnyBlockWrites() throws Exception {
        var prepared = prepare(plan(new int[][]{{0x0000ff}}), palette("mainchestGuess", "lockedchestGuess"), 0);
        class UnsupportedWorld extends MemoryWorld {
            @Override public String setupRejection(BuildingPlacement.Setup setup) { return "No entity support"; }
        }
        var world = new UnsupportedWorld();
        assertThrows(IllegalArgumentException.class, () -> BuildingPlacement.place(prepared, world, true)); assertEquals(0, world.writes);
    }

    @Test void entityInitializationFailureRollsBackCreatedChestsAndTheirMetadata() throws Exception {
        var prepared = prepare(plan(new int[][]{{0x0000ff, 0xff0000}}), palette("mainchestTop", "lockedchestTop"), 0);
        var world = new MemoryWorld(); world.failSetup = 2;
        assertThrows(IllegalStateException.class, () -> BuildingPlacement.place(prepared, world, true));
        assertTrue(world.initialized.isEmpty()); for (var change : prepared.changes()) assertTrue(world.get(change.pos()).isAir());
    }

    @Test void unsupportedPanelsAndChestRowsRejectTheWholePlanButChestlessStockIsDropped() throws Exception {
        var floating = prepare(plan(new int[][]{{0xff0000}}), palette("mainchestGuess", "signwallGuess"), 0);
        assertFalse(floating.supported()); assertTrue(floating.changes().isEmpty());
        var triple = prepare(plan(new int[][]{{0x0000ff, 0x0000ff, 0x0000ff}}), palette("lockedchestTop", "mainchestTop"), 0);
        assertFalse(triple.supported()); assertTrue(triple.issues().stream().anyMatch(issue -> issue.contains("More than two")));
        var empty = plan(new int[][]{{0xffffff}});
        var stocked = new LegacyBuildingPlan(empty.culture(), empty.key(), empty.variation(), empty.upgrade(),
                empty.width(), empty.length(), empty.startLevel(), empty.image(), Map.of("startinggood", List.of("bread,1.0,2,6")));
        var inventory = prepare(stocked, palette("mainchestGuess", "lockedchestGuess"), 0);
        assertTrue(inventory.supported(), inventory.issues().toString()); assertTrue(inventory.startingStock().isEmpty());
    }

    private static BuildingPlacement.Prepared prepare(LegacyBuildingPlan plan, LegacyPalette palette, int rotation) throws Exception {
        // These stand-ins test plan conversion; production uses the registered mod block entity types.
        return BuildingPlacement.prepare(plan, palette, new BlockPos(0, 64, 0), rotation, point -> {
            if (point.block().equals("millenaire:locked_chest")) return Blocks.CHEST.defaultBlockState()
                    .setValue(ChestBlock.FACING, Direction.byName(point.state().substring("facing=".length())));
            if (point.block().equals("millenaire:panel")) return Blocks.OAK_WALL_SIGN.defaultBlockState();
            return LegacyBlockStateResolver.resolve(point);
        });
    }
    private LegacyBuildingPlan plan(int[][] pixels) throws Exception {
        int length = pixels.length, width = pixels[0].length;
        var image = new BufferedImage(width, length, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < length; z++) for (int x = 0; x < width; x++) image.setRGB(width - x - 1, z, pixels[z][x]);
        Path path = temp.resolve(UUID.randomUUID() + ".png"); ImageIO.write(image, "png", path.toFile());
        return new LegacyBuildingPlan("demo", "hall", 'A', 0, width, length, 0, path, Map.of());
    }
    private static LegacyPalette palette(String first, String second) {
        return new LegacyPalette(Map.of(
                0x0000ff, new LegacyPalette.Point(0x0000ff, first, "", "0", false, "", "", 0),
                0xff0000, new LegacyPalette.Point(0xff0000, second, "", "0", false, "", "", 0),
                0xffffff, new LegacyPalette.Point(0xffffff, "empty", "", "0", false, "", "", 0),
                0x555555, new LegacyPalette.Point(0x555555, "stone", "minecraft:stone", "0", false, "", "", 1)));
    }
    private static Map<BlockPos, BlockState> states(BuildingPlacement.Prepared prepared) {
        Map<BlockPos, BlockState> states = new HashMap<>(); prepared.changes().forEach(c -> states.put(c.pos(), c.state())); return states;
    }
    private static class MemoryWorld implements BuildingPlacement.WorldAccess {
        final Map<BlockPos, BlockState> blocks = new HashMap<>(); final Map<BlockPos, BuildingPlacement.Setup> initialized = new HashMap<>();
        int writes, setups, failSetup = -1;
        public BlockState get(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        public String rejection(BlockPos pos, boolean replace) { return null; }
        public boolean set(BlockPos pos, BlockState state) { writes++; blocks.put(pos, state); if (state.isAir()) initialized.remove(pos); return true; }
        public String setupRejection(BuildingPlacement.Setup setup) { return null; }
        public void initialize(BuildingPlacement.Setup setup) {
            if (++setups == failSetup) throw new IllegalStateException("Entity initialization failed"); initialized.put(setup.pos(), setup);
        }
    }
}
