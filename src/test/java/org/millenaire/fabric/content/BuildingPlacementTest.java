package org.millenaire.fabric.content;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.millenaire.fabric.economy.LegacyGoodsCatalog;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BuildingPlacementTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void unsupportedPaletteRejectsTheWholePlanWithoutWriting() throws Exception {
        var prepared = BuildingPlacement.prepare(plan(), palette(true), BlockPos.ZERO, 0);
        var world = new MemoryWorld();
        assertFalse(prepared.supported());
        assertTrue(prepared.changes().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> BuildingPlacement.place(prepared, world, true));
        assertEquals(0, world.writes);
    }

    @Test void checksEveryDestinationBeforeTheFirstWrite() throws Exception {
        var prepared = BuildingPlacement.prepare(plan(), palette(false), BlockPos.ZERO, 0);
        var world = new MemoryWorld();
        world.blocked = prepared.changes().getLast().pos();
        assertThrows(IllegalArgumentException.class, () -> BuildingPlacement.place(prepared, world, true));
        assertEquals(0, world.writes);
    }

    @Test void restoresEarlierChangesAfterAWriteFailure() throws Exception {
        var prepared = BuildingPlacement.prepare(plan(), palette(false), BlockPos.ZERO, 0);
        var world = new MemoryWorld();
        world.failAt = 2;
        assertThrows(IllegalStateException.class, () -> BuildingPlacement.place(prepared, world, true));
        for (var change : prepared.changes()) assertTrue(world.get(change.pos()).isAir());
    }

    @Test void placesSupportedBlocksAndReportsActualChangedCount() throws Exception {
        var prepared = BuildingPlacement.prepare(plan(), palette(false), BlockPos.ZERO, 1);
        var world = new MemoryWorld();
        assertEquals(2, BuildingPlacement.place(prepared, world, false));
        for (var change : prepared.changes()) assertEquals(change.state(), world.get(change.pos()));
        assertEquals(0, BuildingPlacement.place(prepared, world, true));
    }

    @Test void preparesAnActualBundledFountainWithARegistryStandInForTheModPath() throws Exception {
        var root = Path.of(getClass().getResource("/todeploy/millenaire/blocklist.txt").toURI()).getParent();
        var catalog = LegacyCatalogLoader.load(root);
        // Plain JUnit has a frozen vanilla registry. A dirt stand-in represents
        // the mod path for this plan geometry test; it does not test path gameplay.
        var prepared = BuildingPlacement.prepare(catalog.plans().get("norman:fountain_A0"), catalog.palette(), BlockPos.ZERO, 0,
                point -> point.block().equals("millenaire:pathdirt") ? Blocks.DIRT.defaultBlockState() : LegacyBlockStateResolver.resolve(point));
        assertTrue(prepared.supported(), prepared.issues().toString());
        assertFalse(prepared.changes().isEmpty());
        assertTrue(prepared.changes().stream().anyMatch(change -> change.state().is(Blocks.WATER)));
        assertTrue(prepared.servicePoints().containsKey("sleepingPos"));
    }

    @Test void treeMarkerJoinsTheBuildingTransaction() throws Exception {
        var origin = new BlockPos(0, 64, 0);
        var prepared = treePlacement(origin, false);
        assertTrue(prepared.supported(), prepared.issues().toString());
        assertEquals(List.of(origin), prepared.treeRoots());
        var world = new MemoryWorld();
        world.blocks.put(origin.below(), Blocks.DIRT.defaultBlockState());
        assertEquals(2, BuildingPlacement.place(prepared, world, false));
        assertTrue(world.get(origin).is(Blocks.OAK_LOG));
        assertTrue(world.get(origin.above()).is(Blocks.OAK_LEAVES));
    }

    @Test void treeMarkerRejectsMissingSoilBeforeWriting() throws Exception {
        var prepared = treePlacement(new BlockPos(0, 64, 0), false);
        var world = new MemoryWorld();
        assertTrue(BuildingPlacement.checkDestinations(prepared, world, false).stream()
                .anyMatch(issue -> issue.contains("needs dirt")));
        assertThrows(IllegalArgumentException.class, () -> BuildingPlacement.place(prepared, world, false));
        assertEquals(0, world.writes);
    }

    @Test void treeMarkerKeepsPlannedBuildingBlocksWhereTheyOverlap() throws Exception {
        // The original generator never overwrote solid blocks; the tree yields instead of rejecting the plan.
        var plain = treePlacement(new BlockPos(0, 64, 0), false);
        var prepared = treePlacement(new BlockPos(0, 64, 0), true);
        assertTrue(prepared.supported(), prepared.issues().toString());
        var states = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        prepared.changes().forEach(change -> states.put(change.pos(), change.state()));
        assertTrue(states.values().stream().anyMatch(state -> state.is(Blocks.STONE)), "planned building block kept");
        assertTrue(prepared.changes().size() < plain.changes().size() + 1);
    }

    @Test void treeMarkerRollsBackWithBuildingOnWriteFailure() throws Exception {
        var origin = new BlockPos(0, 64, 0);
        var prepared = treePlacement(origin, false);
        var world = new MemoryWorld();
        world.blocks.put(origin.below(), Blocks.DIRT.defaultBlockState());
        world.failAt = 2;
        assertThrows(IllegalStateException.class, () -> BuildingPlacement.place(prepared, world, false));
        assertTrue(world.get(origin).isAir());
        assertTrue(world.get(origin.above()).isAir());
        assertTrue(world.get(origin.below()).is(Blocks.DIRT));
    }

    @Test void vineSoilPlacesGroundAndRetainsTheFieldServicePoint() throws Exception {
        Path image = temp.resolve("vine_A0.png");
        var pixels = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        pixels.setRGB(0, 0, 0x00ffff);
        ImageIO.write(pixels, "png", image.toFile());
        var plan = new LegacyBuildingPlan("byzantine", "vine", 'A', 0, 1, 1, 0, image, Map.of());
        var palette = new LegacyPalette(Map.of(0x00ffff,
                new LegacyPalette.Point(0x00ffff, "vinesoil", "", "0", false, "", "", 0)));
        var origin = new BlockPos(12, 64, 24);
        var prepared = BuildingPlacement.prepare(plan, palette, origin, 0);
        assertTrue(prepared.supported(), prepared.issues().toString());
        assertEquals(1, prepared.changes().size());
        assertTrue(prepared.changes().getFirst().state().is(Blocks.DIRT));
        assertEquals(List.of(new LegacyBuildingPlan.Position(12, 64, 24)), prepared.servicePoints().get("vinesoil"));
        var world = new MemoryWorld();
        assertEquals(1, BuildingPlacement.place(prepared, world, false));
        assertTrue(world.get(origin).is(Blocks.DIRT));
        assertTrue(world.get(origin.above()).isAir());
    }

    @Test void cacaoSpotPreservesTheExistingBlockForLaterFarmerUse() throws Exception {
        var prepared = singleSpecialPoint("cacaospot", new BlockPos(0, 64, 0),
                LegacyBlockStateResolver::resolve);
        assertTrue(prepared.supported(), prepared.issues().toString());
        assertTrue(prepared.changes().isEmpty());
        assertEquals(List.of(new LegacyBuildingPlan.Position(0, 64, 0)), prepared.servicePoints().get("cacaospot"));
        var world = new MemoryWorld();
        world.blocks.put(new BlockPos(0, 64, 0), Blocks.JUNGLE_LOG.defaultBlockState());
        assertEquals(0, BuildingPlacement.place(prepared, world, false));
        assertTrue(world.get(new BlockPos(0, 64, 0)).is(Blocks.JUNGLE_LOG));
        assertEquals(0, world.writes);
    }

    @Test void freePaintedBrickResolvesTheRegisteredWhiteBlock() throws Exception {
        var prepared = singleSpecialPoint("freepaintedbrick", BlockPos.ZERO, point -> {
            assertEquals("millenaire:painted_brick_white", point.block());
            return Blocks.STONE.defaultBlockState();
        });
        assertTrue(prepared.supported(), prepared.issues().toString());
        assertEquals(1, prepared.changes().size());
        assertTrue(prepared.changes().getFirst().state().is(Blocks.STONE));
    }

    @Test void guessedFurnaceFacesAwayFromItsWallAfterEveryRotation() throws Exception {
        Path image = temp.resolve("furnace_A0.png");
        var pixels = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < 3; z++) for (int x = 0; x < 3; x++) pixels.setRGB(x, z, 0xffffff);
        pixels.setRGB(1, 1, 0x0000ff);
        pixels.setRGB(1, 2, 0xff0000);
        ImageIO.write(pixels, "png", image.toFile());
        var plan = new LegacyBuildingPlan("norman", "furnace", 'A', 0, 3, 3, 0, image, Map.of());
        var palette = new LegacyPalette(Map.of(
                0xffffff, new LegacyPalette.Point(0xffffff, "empty", "", "0", false, "", "", 0),
                0x0000ff, new LegacyPalette.Point(0x0000ff, "furnaceGuess", "", "0", false, "", "", 0),
                0xff0000, new LegacyPalette.Point(0xff0000, "stone", "minecraft:stone", "0", false, "", "", 1)));
        var origin = new BlockPos(20, 64, 30);
        var expected = List.of(net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.SOUTH,
                net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.NORTH);
        for (int rotation = 0; rotation < 4; rotation++) {
            var prepared = BuildingPlacement.prepare(plan, palette, origin, rotation);
            assertTrue(prepared.supported(), prepared.issues().toString());
            var furnace = prepared.changes().stream().filter(change -> change.state().is(Blocks.FURNACE)).findFirst().orElseThrow();
            assertEquals(expected.get(rotation), furnace.state().getValue(FurnaceBlock.FACING));
            assertTrue(furnace.secondPass());
            assertEquals(List.of(new LegacyBuildingPlan.Position(furnace.pos().getX(), furnace.pos().getY(), furnace.pos().getZ())),
                    prepared.servicePoints().get("furnaces"));
        }
    }

    @Test void guessedLadderFacesItsOpenSideAndIsPlacedAfterSupportForEveryRotation() throws Exception {
        var origin = new BlockPos(20, 64, 30);
        var expected = List.of(net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.SOUTH,
                net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.NORTH);
        for (int rotation = 0; rotation < 4; rotation++) {
            var prepared = ladderPlacement(origin, rotation);
            assertTrue(prepared.supported(), prepared.issues().toString());
            var ladder = prepared.changes().stream().filter(change -> change.state().is(Blocks.LADDER)).findFirst().orElseThrow();
            var facing = ladder.state().getValue(net.minecraft.world.level.block.LadderBlock.FACING);
            assertEquals(expected.get(rotation), facing);
            assertTrue(ladder.secondPass());
            var world = new MemoryWorld() {
                @Override public boolean set(BlockPos pos, BlockState state) {
                    if (state.is(Blocks.LADDER)) assertTrue(get(pos.relative(facing.getOpposite())).is(Blocks.STONE),
                            "Supporting wall must be placed before the ladder");
                    return super.set(pos, state);
                }
            };
            BuildingPlacement.place(prepared, world, false);
            assertEquals(ladder.state(), world.get(ladder.pos()));
            assertEquals(List.of(new LegacyBuildingPlan.Position(ladder.pos().getX(), ladder.pos().getY(), ladder.pos().getZ())),
                    prepared.servicePoints().get("ladderGuess"));
        }
    }

    @Test void guessedLadderWithoutAPlannedWallRejectsBeforeWriting() throws Exception {
        var prepared = singleSpecialPoint("ladderGuess", BlockPos.ZERO, LegacyBlockStateResolver::resolve);
        assertFalse(prepared.supported());
        assertTrue(prepared.issues().stream().anyMatch(issue -> issue.contains("Ladder has no planned support")));
        assertTrue(prepared.changes().isEmpty());
        var world = new MemoryWorld();
        assertThrows(IllegalArgumentException.class, () -> BuildingPlacement.place(prepared, world, true));
        assertEquals(0, world.writes);
    }

    @Test void guessedLadderAndItsWallRestoreOriginalBlocksAfterFailure() throws Exception {
        var prepared = ladderPlacement(new BlockPos(20, 64, 30), 0);
        var world = new MemoryWorld() {
            @Override public void finish(List<BuildingPlacement.Change> changes) { throw new IllegalStateException("Finish failed"); }
        };
        for (var change : prepared.changes()) world.blocks.put(change.pos(), Blocks.DIRT.defaultBlockState());
        var original = Map.copyOf(world.blocks);
        assertThrows(IllegalStateException.class, () -> BuildingPlacement.place(prepared, world, true));
        assertEquals(original, world.blocks);
    }

    private BuildingPlacement.Prepared ladderPlacement(BlockPos origin, int rotation) throws Exception {
        Path image = temp.resolve("ladder_A0.png");
        var pixels = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < 3; z++) for (int x = 0; x < 3; x++) pixels.setRGB(x, z, 0xffffff);
        pixels.setRGB(1, 1, 0x0000ff);
        pixels.setRGB(1, 2, 0xff0000);
        ImageIO.write(pixels, "png", image.toFile());
        var plan = new LegacyBuildingPlan("norman", "ladder", 'A', 0, 3, 3, 0, image, Map.of());
        var palette = new LegacyPalette(Map.of(
                0xffffff, new LegacyPalette.Point(0xffffff, "empty", "", "0", false, "", "", 0),
                0x0000ff, new LegacyPalette.Point(0x0000ff, "ladderGuess", "", "0", false, "", "", 0),
                0xff0000, new LegacyPalette.Point(0xff0000, "stone", "minecraft:stone", "0", false, "", "", 1)));
        return BuildingPlacement.prepare(plan, palette, origin, rotation);
    }

    @Test void doorsExpandBothHalvesWithMatchingPropertiesForAllRotations() throws Exception {
        for (String block : List.of("wooden_door", "spruce_door", "birch_door", "jungle_door", "acacia_door", "dark_oak_door", "iron_door")) {
            for (int rotation = 0; rotation < 4; rotation++) {
                var prepared = doorPlacement(block, rotation, false);
                assertTrue(prepared.supported(), prepared.issues().toString());
                assertEquals(2, prepared.changes().size());
                var lower = prepared.changes().getFirst();
                var upper = prepared.changes().getLast();
                assertEquals(lower.pos().above(), upper.pos());
                assertEquals(upper.state(), lower.state().setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
                assertEquals(List.of(net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.WEST,
                        net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.EAST).get(rotation),
                        lower.state().getValue(net.minecraft.world.level.block.DoorBlock.FACING));
                var world = new MemoryWorld();
                world.blocks.put(lower.pos().below(), Blocks.STONE.defaultBlockState());
                assertEquals(2, BuildingPlacement.place(prepared, world, false));
                assertEquals(upper.state(), world.get(upper.pos()));
            }
        }
    }

    @Test void doorUpperDestinationAndFloorAreCheckedBeforeAnyWrite() throws Exception {
        var prepared = doorPlacement("wooden_door", 0, false);
        var world = new MemoryWorld();
        assertThrows(IllegalArgumentException.class, () -> BuildingPlacement.place(prepared, world, false));
        assertEquals(0, world.writes);
        world.blocks.put(prepared.changes().getFirst().pos().below(), Blocks.STONE.defaultBlockState());
        world.blocked = prepared.changes().getLast().pos();
        assertThrows(IllegalArgumentException.class, () -> BuildingPlacement.place(prepared, world, true));
        assertEquals(0, world.writes);
    }

    @Test void oakDoorUsesLegacyHingeRuleAfterRotationWhileSpruceKeepsItsHinge() throws Exception {
        Path image = temp.resolve("hinge_A0.png");
        var pixels = new BufferedImage(1, 2, BufferedImage.TYPE_INT_RGB);
        pixels.setRGB(0, 0, 0x0000ff);
        pixels.setRGB(0, 1, 0xff0000);
        ImageIO.write(pixels, "png", image.toFile());
        var plan = new LegacyBuildingPlan("norman", "hinge", 'A', 0, 1, 2, 0, image, Map.of());
        for (String block : List.of("wooden_door", "spruce_door")) for (int rotation = 0; rotation < 4; rotation++) {
            var palette = new LegacyPalette(Map.of(
                    0x0000ff, new LegacyPalette.Point(0x0000ff, "doorLeft", "minecraft:" + block, "facing=north", true, "", "", 1),
                    0xff0000, new LegacyPalette.Point(0xff0000, "stone", "minecraft:stone", "0", false, "", "", 1)));
            var prepared = BuildingPlacement.prepare(plan, palette, BlockPos.ZERO, rotation);
            assertTrue(prepared.supported(), prepared.issues().toString());
            for (var change : prepared.changes()) if (change.state().getBlock() instanceof net.minecraft.world.level.block.DoorBlock)
                assertEquals(block.equals("wooden_door") ? net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT
                        : net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT,
                        change.state().getValue(net.minecraft.world.level.block.DoorBlock.HINGE));
        }
    }

    private BuildingPlacement.Prepared declaredDoor(boolean lower) throws Exception {
        Path image = temp.resolve("declared_A0.png");
        // Plans store floors side by side: x=0 is floor 0, x=2 is floor 1 (one separator column).
        var pixels = new BufferedImage(3, 1, BufferedImage.TYPE_INT_RGB);
        pixels.setRGB(0, 0, lower ? 0x0000ff : 0xffffff);
        pixels.setRGB(1, 0, 0x000000);
        pixels.setRGB(2, 0, 0x00ff00);
        ImageIO.write(pixels, "png", image.toFile());
        var plan = new LegacyBuildingPlan("norman", "declared", 'A', 0, 1, 1, 0, image, Map.of());
        var palette = new LegacyPalette(Map.of(
                0x0000ff, new LegacyPalette.Point(0x0000ff, "doorLower", "minecraft:spruce_door", "facing=north,half=lower,hinge=left", true, "", "", 1),
                0x00ff00, new LegacyPalette.Point(0x00ff00, "doorUpper", "minecraft:spruce_door", "facing=north,half=upper,hinge=right", true, "", "", 1),
                0xffffff, new LegacyPalette.Point(0xffffff, "empty", "", "", false, "", "", 0)));
        return BuildingPlacement.prepare(plan, palette, new BlockPos(0, 64, 0), 0);
    }

    @Test void declaredUpperDoorHalfSuppliesTheLegacyHinge() throws Exception {
        var prepared = declaredDoor(true);
        assertTrue(prepared.supported(), prepared.issues().toString());
        var doors = prepared.changes().stream().filter(change -> change.state().getBlock() instanceof net.minecraft.world.level.block.DoorBlock).toList();
        assertEquals(2, doors.size());
        for (var door : doors) assertEquals(net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT,
                door.state().getValue(net.minecraft.world.level.block.DoorBlock.HINGE));
    }

    @Test void orphanUpperDoorHalfIsLeftOutInsteadOfBlockingThePlan() throws Exception {
        var prepared = declaredDoor(false);
        assertTrue(prepared.supported(), prepared.issues().toString());
        assertTrue(prepared.changes().stream().noneMatch(change -> change.state().getBlock() instanceof net.minecraft.world.level.block.DoorBlock));
    }

    @Test void doorExpansionRejectsConflictingPlannedUpperBlock() throws Exception {
        var prepared = doorPlacement("wooden_door", 0, true);
        assertFalse(prepared.supported());
        assertTrue(prepared.changes().isEmpty());
        assertTrue(prepared.issues().stream().anyMatch(issue -> issue.contains("Door upper half overlaps")));
    }

    @Test void bothDoorHalvesRollBackAfterUpperWriteFails() throws Exception {
        var prepared = doorPlacement("wooden_door", 0, false);
        var world = new MemoryWorld();
        world.blocks.put(prepared.changes().getFirst().pos().below(), Blocks.STONE.defaultBlockState());
        for (var change : prepared.changes()) world.blocks.put(change.pos(), Blocks.DIRT.defaultBlockState());
        var original = Map.copyOf(world.blocks);
        world.failAt = 2;
        assertThrows(IllegalStateException.class, () -> BuildingPlacement.place(prepared, world, true));
        assertEquals(original, world.blocks);
    }

    private BuildingPlacement.Prepared doorPlacement(String block, int rotation, boolean collision) throws Exception {
        Path image = temp.resolve("door_A0.png");
        var pixels = new BufferedImage(collision ? 3 : 1, 1, BufferedImage.TYPE_INT_RGB);
        pixels.setRGB(0, 0, 0x0000ff);
        if (collision) pixels.setRGB(2, 0, 0xff0000);
        ImageIO.write(pixels, "png", image.toFile());
        var plan = new LegacyBuildingPlan("norman", "door", 'A', 0, 1, 1, 0, image, Map.of());
        var palette = new LegacyPalette(Map.of(
                0x0000ff, new LegacyPalette.Point(0x0000ff, "doorLeft", "minecraft:" + block, "facing=north", true, "", "", 1),
                0xff0000, new LegacyPalette.Point(0xff0000, "stone", "minecraft:stone", "0", false, "", "", 1)));
        return BuildingPlacement.prepare(plan, palette, new BlockPos(20, 64, 30), rotation);
    }

    @Test void bedHeadsExpandToMatchingFeetForEveryFacingAndRotation() throws Exception {
        for (String facing : List.of("north", "south", "west", "east")) for (int rotation = 0; rotation < 4; rotation++) {
            var prepared = bedPlacement("facing=" + facing + ",part=head", rotation, "");
            assertTrue(prepared.supported(), prepared.issues().toString());
            assertEquals(2, prepared.changes().size());
            var head = prepared.changes().stream().filter(change -> change.state().getValue(net.minecraft.world.level.block.BedBlock.PART)
                    == net.minecraft.world.level.block.state.properties.BedPart.HEAD).findFirst().orElseThrow();
            var foot = prepared.changes().stream().filter(change -> change.state().getValue(net.minecraft.world.level.block.BedBlock.PART)
                    == net.minecraft.world.level.block.state.properties.BedPart.FOOT).findFirst().orElseThrow();
            assertEquals(Identifier.parse("minecraft:red_bed"), net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(head.state().getBlock()));
            assertEquals(head.pos().relative(head.state().getValue(net.minecraft.world.level.block.BedBlock.FACING).getOpposite()), foot.pos());
            assertEquals(head.state().setValue(net.minecraft.world.level.block.BedBlock.PART,
                    net.minecraft.world.level.block.state.properties.BedPart.FOOT), foot.state());
            var world = new MemoryWorld();
            assertEquals(2, BuildingPlacement.place(prepared, world, false));
            assertEquals(foot.state(), world.get(foot.pos()));
        }
    }

    @Test void bedFootDestinationIsCheckedBeforeWritingTheHead() throws Exception {
        var prepared = bedPlacement("facing=north,part=head", 0, "");
        var world = new MemoryWorld();
        world.blocked = prepared.changes().getLast().pos();
        assertThrows(IllegalArgumentException.class, () -> BuildingPlacement.place(prepared, world, true));
        assertEquals(0, world.writes);
    }

    @Test void bedCounterpartRejectsConflictsAndAcceptsAnAlreadyDeclaredFoot() throws Exception {
        for (String occupied : List.of("stone", "wrongfoot")) {
            var prepared = bedPlacement("facing=north,part=head", 0, occupied);
            // The bed is left out; the block occupying the foot stays.
            assertTrue(prepared.supported(), prepared.issues().toString());
            assertTrue(prepared.changes().stream().noneMatch(change -> change.state().getBlock() instanceof net.minecraft.world.level.block.BedBlock));
        }
        var prepared = bedPlacement("facing=north,part=head", 0, "foot");
        assertTrue(prepared.supported(), prepared.issues().toString());
        assertEquals(2, prepared.changes().size());
        assertEquals(2, prepared.changes().stream().map(BuildingPlacement.Change::pos).distinct().count());
    }

    @Test void orphanBedFootIsLeftOut() throws Exception {
        var prepared = bedPlacement("facing=north,part=foot", 0, "");
        assertTrue(prepared.supported(), prepared.issues().toString());
        assertTrue(prepared.changes().stream().noneMatch(change -> change.state().getBlock() instanceof net.minecraft.world.level.block.BedBlock));
    }

    @Test void bedFootFailureRestoresBothOriginalCells() throws Exception {
        var prepared = bedPlacement("facing=north,part=head", 0, "");
        var world = new MemoryWorld();
        for (var change : prepared.changes()) world.blocks.put(change.pos(), Blocks.DIRT.defaultBlockState());
        var original = Map.copyOf(world.blocks);
        world.failAt = 2;
        assertThrows(IllegalStateException.class, () -> BuildingPlacement.place(prepared, world, true));
        assertEquals(original, world.blocks);
    }

    private BuildingPlacement.Prepared bedPlacement(String headState, int rotation, String counterpart) throws Exception {
        int width = counterpart.isEmpty() ? 1 : 2;
        Path image = temp.resolve("bed_A0.png");
        var pixels = new BufferedImage(width, 1, BufferedImage.TYPE_INT_RGB);
        pixels.setRGB(width - 1, 0, 0x0000ff);
        if (width == 2) pixels.setRGB(0, 0, 0xff0000);
        ImageIO.write(pixels, "png", image.toFile());
        var plan = new LegacyBuildingPlan("norman", "bed", 'A', 0, width, 1, 0, image, Map.of());
        var palette = new LegacyPalette(Map.of(
                0x0000ff, new LegacyPalette.Point(0x0000ff, "bedRight", "minecraft:bed", headState, true, "", "", 1),
                0xff0000, new LegacyPalette.Point(0xff0000, "counterpart", counterpart.equals("stone") ? "minecraft:stone" : "minecraft:bed",
                        counterpart.equals("stone") ? "0" : "facing=" + (counterpart.equals("wrongfoot") ? "south" : "north") + ",part=foot", true, "", "", 1)));
        return BuildingPlacement.prepare(plan, palette, new BlockPos(20, 64, 30), rotation);
    }

    @Test void allLegacyTallPlantsExpandBothHalvesInEveryRotation() throws Exception {
        for (String variant : List.of("sunflower", "syringa", "double_grass", "double_fern", "double_rose", "paeonia")) {
            for (int rotation = 0; rotation < 4; rotation++) {
                var prepared = tallPlantPlacement(variant, "lower", "", rotation);
                assertTrue(prepared.supported(), prepared.issues().toString());
                assertEquals(2, prepared.changes().size());
                var lower = prepared.changes().getFirst();
                var upper = prepared.changes().getLast();
                assertEquals(lower.pos().above(), upper.pos());
                assertEquals(lower.state().setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF,
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), upper.state());
                var world = new MemoryWorld();
                world.blocks.put(lower.pos().below(), Blocks.DIRT.defaultBlockState());
                assertEquals(2, BuildingPlacement.place(prepared, world, false));
                assertEquals(upper.state(), world.get(upper.pos()));
            }
        }
    }

    @Test void portalSeedFillsFrameAndRotatesAxisForEveryBuildingOrientation() throws Exception {
        for (int rotation = 0; rotation < 4; rotation++) {
            var prepared = portalPlacement(rotation, false);
            assertTrue(prepared.supported(), prepared.issues().toString());
            var portals = prepared.changes().stream().filter(change -> change.state().is(Blocks.NETHER_PORTAL)).toList();
            assertEquals(6, portals.size());
            var axis = rotation % 2 == 0 ? net.minecraft.core.Direction.Axis.Z : net.minecraft.core.Direction.Axis.X;
            assertTrue(portals.stream().allMatch(change -> change.state().getValue(net.minecraft.world.level.block.NetherPortalBlock.AXIS) == axis && change.secondPass()));
            var world = new MemoryWorld();
            BuildingPlacement.place(prepared, world, false);
            for (var portal : portals) assertEquals(portal.state(), world.get(portal.pos()));
        }
    }

    @Test void portalRejectsMissingFrameAndChecksExpandedDestinationsBeforeWrites() throws Exception {
        var broken = portalPlacement(0, true);
        assertFalse(broken.supported()); assertTrue(broken.changes().isEmpty());
        var prepared = portalPlacement(0, false);
        var world = new MemoryWorld();
        world.blocked = prepared.changes().stream().filter(change -> change.state().is(Blocks.NETHER_PORTAL)).toList().getLast().pos();
        assertThrows(IllegalArgumentException.class, () -> BuildingPlacement.place(prepared, world, true));
        assertEquals(0, world.writes);
    }

    @Test void portalInteriorAndFrameRollBackAfterFailure() throws Exception {
        var prepared = portalPlacement(0, false);
        var world = new MemoryWorld() {
            @Override public void finish(List<BuildingPlacement.Change> changes) { throw new IllegalStateException("Finish failed"); }
        };
        for (var change : prepared.changes()) world.blocks.put(change.pos(), Blocks.DIRT.defaultBlockState());
        var original = Map.copyOf(world.blocks);
        assertThrows(IllegalStateException.class, () -> BuildingPlacement.place(prepared, world, true));
        assertEquals(original, world.blocks);
    }

    private BuildingPlacement.Prepared portalPlacement(int rotation, boolean broken) throws Exception {
        Path image = temp.resolve("portal_A0.png");
        var pixels = new BufferedImage(24, 1, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 5; y++) for (int x = 0; x < 4; x++) {
            boolean corner = (x == 0 || x == 3) && (y == 0 || y == 4);
            boolean edge = x == 0 || x == 3 || y == 0 || y == 4;
            int color = corner || !edge ? 0xffffff : 0xff0000;
            if (x == 1 && y == 1) color = 0x0000ff;
            if (broken && x == 0 && y == 2) color = 0xffffff;
            pixels.setRGB(y * 5 + 3 - x, 0, color);
        }
        ImageIO.write(pixels, "png", image.toFile());
        var plan = new LegacyBuildingPlan("norman", "portal", 'A', 0, 4, 1, 0, image, Map.of());
        var palette = new LegacyPalette(Map.of(
                0xffffff, new LegacyPalette.Point(0xffffff, "empty", "", "0", false, "", "", 0),
                0xff0000, new LegacyPalette.Point(0xff0000, "obsidian", "minecraft:obsidian", "0", false, "", "", 1),
                0x0000ff, new LegacyPalette.Point(0x0000ff, "portal", "minecraft:portal", "0", true, "", "", 0)));
        return BuildingPlacement.prepare(plan, palette, new BlockPos(20, 64, 30), rotation);
    }

    @Test void bundledVineAttachmentsRotateWithTheBuilding() throws Exception {
        var palette = LegacyPalette.read(Path.of(getClass().getResource("/todeploy/millenaire/blocklist.txt").toURI()));
        var vine = palette.points().values().stream().filter(point -> point.label().equals("vinesTop")).findFirst().orElseThrow();
        Path image = temp.resolve("vine_A0.png");
        var pixels = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        pixels.setRGB(0, 0, vine.color()); ImageIO.write(pixels, "png", image.toFile());
        var plan = new LegacyBuildingPlan("norman", "vine", 'A', 0, 1, 1, 0, image, Map.of());
        var expected = List.of(net.minecraft.world.level.block.VineBlock.EAST, net.minecraft.world.level.block.VineBlock.NORTH,
                net.minecraft.world.level.block.VineBlock.WEST, net.minecraft.world.level.block.VineBlock.SOUTH);
        for (int rotation = 0; rotation < 4; rotation++) {
            var prepared = BuildingPlacement.prepare(plan, palette, new BlockPos(20, 64, 30), rotation);
            assertTrue(prepared.supported(), prepared.issues().toString());
            var world = new MemoryWorld();
            assertEquals(1, BuildingPlacement.place(prepared, world, false));
            var expectedPos = List.of(new BlockPos(20, 64, 30), new BlockPos(20, 64, 29),
                    new BlockPos(19, 64, 29), new BlockPos(19, 64, 30)).get(rotation);
            assertEquals(expectedPos, prepared.changes().getFirst().pos());
            var state = world.get(expectedPos);
            assertTrue(state.getValue(expected.get(rotation)));
            assertEquals(1, expected.stream().filter(property -> state.getValue(property)).count());
        }
    }

    @Test void bundledFilledCauldronAndTopPurpurSlabPlaceWithTheirOriginalState() throws Exception {
        var palette = LegacyPalette.read(Path.of(getClass().getResource("/todeploy/millenaire/blocklist.txt").toURI()));
        var cauldron = palette.points().values().stream().filter(point -> point.label().equals("cauldronfull")).findFirst().orElseThrow();
        var slab = palette.points().values().stream().filter(point -> point.label().equals("purpurSlabInv")).findFirst().orElseThrow();
        Path image = temp.resolve("filled_A0.png");
        var pixels = new BufferedImage(1, 2, BufferedImage.TYPE_INT_RGB);
        pixels.setRGB(0, 0, cauldron.color()); pixels.setRGB(0, 1, slab.color());
        ImageIO.write(pixels, "png", image.toFile());
        var plan = new LegacyBuildingPlan("norman", "filled", 'A', 0, 1, 2, 0, image, Map.of());
        for (int rotation = 0; rotation < 4; rotation++) {
            var prepared = BuildingPlacement.prepare(plan, palette, new BlockPos(20, 64, 30), rotation);
            assertTrue(prepared.supported(), prepared.issues().toString());
            var world = new MemoryWorld();
            assertEquals(2, BuildingPlacement.place(prepared, world, false));
            var water = world.blocks.values().stream().filter(state -> state.is(Blocks.WATER_CAULDRON)).findFirst().orElseThrow();
            assertEquals(3, water.getValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL));
            var top = world.blocks.values().stream().filter(state -> state.is(Blocks.PURPUR_SLAB)).findFirst().orElseThrow();
            assertEquals(net.minecraft.world.level.block.state.properties.SlabType.TOP, top.getValue(net.minecraft.world.level.block.SlabBlock.TYPE));
        }
    }

    @Test void bundledDecorativeLeavesRemainPersistentAlongsideTheirSaplings() throws Exception {
        var palette = LegacyPalette.read(Path.of(getClass().getResource("/todeploy/millenaire/blocklist.txt").toURI()));
        var leaves = palette.points().values().stream().filter(point -> point.label().equals("oak leaves")).findFirst().orElseThrow();
        var sapling = palette.points().values().stream().filter(point -> point.label().equals("saplings pine")).findFirst().orElseThrow();
        Path image = temp.resolve("foliage_A0.png");
        var pixels = new BufferedImage(1, 2, BufferedImage.TYPE_INT_RGB);
        pixels.setRGB(0, 0, leaves.color()); pixels.setRGB(0, 1, sapling.color());
        ImageIO.write(pixels, "png", image.toFile());
        var plan = new LegacyBuildingPlan("norman", "foliage", 'A', 0, 1, 2, 0, image, Map.of());
        for (int rotation = 0; rotation < 4; rotation++) {
            var prepared = BuildingPlacement.prepare(plan, palette, new BlockPos(20, 64, 30), rotation);
            assertTrue(prepared.supported(), prepared.issues().toString());
            var world = new MemoryWorld();
            assertEquals(2, BuildingPlacement.place(prepared, world, false));
            var leafState = world.blocks.values().stream().filter(state -> state.is(Blocks.OAK_LEAVES)).findFirst().orElseThrow();
            assertTrue(leafState.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT));
            var saplingState = world.blocks.values().stream().filter(state -> state.is(Blocks.SPRUCE_SAPLING)).findFirst().orElseThrow();
            assertEquals(0, saplingState.getValue(net.minecraft.world.level.block.SaplingBlock.STAGE));
        }
    }

    @Test void buttonsKeepTheirSupportSideForAllLegacyDirectionsAndRotations() throws Exception {
        var directions = List.of(net.minecraft.core.Direction.DOWN, net.minecraft.core.Direction.EAST,
                net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.UP);
        for (int meta = 0; meta < 6; meta++) for (int rotation = 0; rotation < 4; rotation++) {
            var prepared = buttonPlacement(meta, rotation, false);
            assertTrue(prepared.supported(), prepared.issues().toString());
            var button = prepared.changes().getFirst();
            var outward = directions.get(meta);
            if (outward.getAxis().isHorizontal()) outward = switch (rotation) {
                case 1 -> outward.getCounterClockWise(); case 2 -> outward.getOpposite();
                case 3 -> outward.getClockWise(); default -> outward;
            };
            var world = new MemoryWorld();
            world.blocks.put(button.pos().relative(outward.getOpposite()), Blocks.STONE.defaultBlockState());
            assertTrue(button.secondPass());
            assertEquals(1, BuildingPlacement.place(prepared, world, false));
            assertEquals(button.state(), world.get(button.pos()));
        }
    }

    @Test void buttonWithoutSupportRejectsBeforeWritingAndPlannedAirCannotUseOldWall() throws Exception {
        var prepared = buttonPlacement(1, 0, false);
        var world = new MemoryWorld();
        assertThrows(IllegalArgumentException.class, () -> BuildingPlacement.place(prepared, world, true));
        assertEquals(0, world.writes);
        var button = prepared.changes().getFirst();
        var support = button.pos().west();
        world.blocks.put(support, Blocks.STONE.defaultBlockState());
        var clearing = new BuildingPlacement.Prepared(List.of(new BuildingPlacement.Change(support, Blocks.AIR.defaultBlockState(), false), button), List.of(), Map.of());
        assertThrows(IllegalArgumentException.class, () -> BuildingPlacement.place(clearing, world, true));
        assertEquals(0, world.writes);
    }

    @Test void buttonIsPlacedAfterItsPlannedWallEvenWithoutPaletteSecondPass() throws Exception {
        var prepared = buttonPlacement(2, 0, true);
        var button = prepared.changes().stream().filter(change -> change.state().getBlock() instanceof net.minecraft.world.level.block.ButtonBlock).findFirst().orElseThrow();
        var world = new MemoryWorld() {
            @Override public boolean set(BlockPos pos, BlockState state) {
                if (pos.equals(button.pos()) && state.getBlock() instanceof net.minecraft.world.level.block.ButtonBlock)
                    assertTrue(get(pos.east()).is(Blocks.STONE));
                return super.set(pos, state);
            }
        };
        assertEquals(2, BuildingPlacement.place(prepared, world, false));
    }

    @Test void buttonWriteFailureRollsBackItsSupportingWall() throws Exception {
        var prepared = buttonPlacement(2, 0, true);
        var world = new MemoryWorld();
        for (var change : prepared.changes()) world.blocks.put(change.pos(), Blocks.DIRT.defaultBlockState());
        var original = Map.copyOf(world.blocks);
        world.failAt = 2;
        assertThrows(IllegalStateException.class, () -> BuildingPlacement.place(prepared, world, true));
        assertEquals(original, world.blocks);
    }

    private BuildingPlacement.Prepared buttonPlacement(int meta, int rotation, boolean wall) throws Exception {
        Path image = temp.resolve("button_A0.png");
        var pixels = new BufferedImage(1, wall ? 2 : 1, BufferedImage.TYPE_INT_RGB);
        pixels.setRGB(0, 0, 0x0000ff);
        if (wall) pixels.setRGB(0, 1, 0xff0000);
        ImageIO.write(pixels, "png", image.toFile());
        var plan = new LegacyBuildingPlan("norman", "button", 'A', 0, 1, wall ? 2 : 1, 0, image, Map.of());
        var palette = new LegacyPalette(Map.of(
                0x0000ff, new LegacyPalette.Point(0x0000ff, "button", "minecraft:stone_button", Integer.toString(meta), false, "", "", 1),
                0xff0000, new LegacyPalette.Point(0xff0000, "stone", "minecraft:stone", "0", false, "", "", 1)));
        return BuildingPlacement.prepare(plan, palette, new BlockPos(20, 64, 30), rotation);
    }

    @Test void bundledSnowPalettePreservesFullCubeAndLayerHeightDuringPlacement() throws Exception {
        var palette = LegacyPalette.read(Path.of(getClass().getResource("/todeploy/millenaire/blocklist.txt").toURI()));
        var full = palette.points().values().stream().filter(point -> point.label().equals("snowblock")).findFirst().orElseThrow();
        var layer = palette.points().values().stream().filter(point -> point.label().equals("snow_layer_6")).findFirst().orElseThrow();
        Path image = temp.resolve("snow_A0.png");
        var pixels = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        pixels.setRGB(0, 0, full.color()); pixels.setRGB(1, 0, layer.color());
        ImageIO.write(pixels, "png", image.toFile());
        var plan = new LegacyBuildingPlan("norman", "snow", 'A', 0, 2, 1, 0, image, Map.of());
        var prepared = BuildingPlacement.prepare(plan, palette, new BlockPos(20, 64, 30), 0);
        assertTrue(prepared.supported(), prepared.issues().toString());
        assertEquals(2, prepared.changes().size());
        var world = new MemoryWorld();
        assertEquals(2, BuildingPlacement.place(prepared, world, false));
        assertEquals(1, world.blocks.values().stream().filter(state -> state.is(Blocks.SNOW_BLOCK)).count());
        var snowLayer = world.blocks.values().stream().filter(state -> state.is(Blocks.SNOW)).findFirst().orElseThrow();
        assertEquals(7, snowLayer.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS));
    }

    @Test void tallPlantChecksSoilAndUpperDestinationBeforeWrites() throws Exception {
        var prepared = tallPlantPlacement("paeonia", "lower", "", 0);
        var world = new MemoryWorld();
        assertThrows(IllegalArgumentException.class, () -> BuildingPlacement.place(prepared, world, true));
        assertEquals(0, world.writes);
        world.blocks.put(prepared.changes().getFirst().pos().below(), Blocks.DIRT.defaultBlockState());
        world.blocked = prepared.changes().getLast().pos();
        assertThrows(IllegalArgumentException.class, () -> BuildingPlacement.place(prepared, world, true));
        assertEquals(0, world.writes);
    }

    @Test void tallPlantRejectsConflictsAndOrphansButReusesDeclaredUpperHalf() throws Exception {
        var collision = tallPlantPlacement("sunflower", "lower", "stone", 0);
        assertFalse(collision.supported());
        assertTrue(collision.changes().isEmpty());
        assertTrue(collision.issues().stream().anyMatch(issue -> issue.contains("Tall plant upper half overlaps")));
        var orphan = tallPlantPlacement("sunflower", "upper", "", 0);
        assertFalse(orphan.supported());
        assertTrue(orphan.issues().stream().anyMatch(issue -> issue.contains("no matching lower half")));
        var matching = tallPlantPlacement("sunflower", "lower", "upper", 0);
        assertTrue(matching.supported(), matching.issues().toString());
        assertEquals(2, matching.changes().size());
    }

    @Test void tallPlantUpperWriteFailureRestoresBothCells() throws Exception {
        var prepared = tallPlantPlacement("double_fern", "lower", "", 0);
        var world = new MemoryWorld();
        world.blocks.put(prepared.changes().getFirst().pos().below(), Blocks.DIRT.defaultBlockState());
        for (var change : prepared.changes()) world.blocks.put(change.pos(), Blocks.STONE.defaultBlockState());
        var original = Map.copyOf(world.blocks);
        world.failAt = 2;
        assertThrows(IllegalStateException.class, () -> BuildingPlacement.place(prepared, world, true));
        assertEquals(original, world.blocks);
    }

    private BuildingPlacement.Prepared tallPlantPlacement(String variant, String half, String upper, int rotation) throws Exception {
        Path image = temp.resolve("plant_A0.png");
        var pixels = new BufferedImage(upper.isEmpty() ? 1 : 3, 1, BufferedImage.TYPE_INT_RGB);
        pixels.setRGB(0, 0, 0x0000ff);
        if (!upper.isEmpty()) pixels.setRGB(2, 0, 0xff0000);
        ImageIO.write(pixels, "png", image.toFile());
        var plan = new LegacyBuildingPlan("norman", "plant", 'A', 0, 1, 1, 0, image, Map.of());
        var palette = new LegacyPalette(Map.of(
                0x0000ff, new LegacyPalette.Point(0x0000ff, variant, "minecraft:double_plant", "variant=" + variant + ",facing=north,half=" + half, false, "", "", 0),
                0xff0000, new LegacyPalette.Point(0xff0000, "upper", upper.equals("stone") ? "minecraft:stone" : "minecraft:double_plant",
                        upper.equals("stone") ? "0" : "variant=" + variant + ",half=upper", false, "", "", 0)));
        return BuildingPlacement.prepare(plan, palette, new BlockPos(20, 64, 30), rotation);
    }

    @Test void sericultureAndSnailSpecialPointsPlaceTheirFourStageBlocks() throws Exception {
        var silk = singleSpecialPoint("silkwormblock", BlockPos.ZERO, point ->
                Blocks.STONE.defaultBlockState());
        var snail = singleSpecialPoint("snailsoilblock", new BlockPos(1, 64, 0), point ->
                Blocks.DIRT.defaultBlockState());
        assertTrue(silk.supported(), silk.issues().toString());
        assertTrue(snail.supported(), snail.issues().toString());
        assertEquals(1, silk.changes().size());
        assertEquals(1, snail.changes().size());
        assertTrue(silk.changes().getFirst().state().is(Blocks.STONE));
        assertTrue(snail.changes().getFirst().state().is(Blocks.DIRT));
        assertEquals(4, LegacyProgressBlock.PROGRESS.getPossibleValues().size());
    }

    @Test void animalSpawnMarkersRemainServicePositionsWithoutSpawningDuringPlacement() throws Exception {
        for (String marker : List.of("cowspawn", "pigspawn", "sheepspawn", "chickenspawn", "squidspawn", "wolfspawn", "polarbearspawn")) {
            BlockPos origin = new BlockPos(marker.hashCode() & 15, 64, 0);
            var prepared = singleSpecialPoint(marker, origin, point -> Blocks.AIR.defaultBlockState());
            assertTrue(prepared.supported(), marker + ": " + prepared.issues());
            if (marker.equals("squidspawn")) {
                assertEquals(1, prepared.changes().size());
                assertTrue(prepared.changes().getFirst().state().is(Blocks.WATER));
            } else {
                assertTrue(prepared.changes().isEmpty());
                var world = new MemoryWorld();
                world.blocks.put(origin, Blocks.STONE.defaultBlockState());
                assertEquals(0, BuildingPlacement.place(prepared, world, true));
                assertTrue(world.get(origin).is(Blocks.STONE));
            }
            assertEquals(List.of(new LegacyBuildingPlan.Position(origin.getX(), origin.getY(), origin.getZ())),
                    prepared.servicePoints().get(marker));
        }
    }

    @Test void mobSpawnerMarkersCreateConfiguredSpawnerSetups() throws Exception {
        for (String marker : List.of("spawnerskeleton", "spawnerzombie", "spawnerspider", "spawnercavespider", "spawnercreeper", "spawnerblaze")) {
            BlockPos origin = new BlockPos(marker.hashCode() & 15, 64, 1);
            var prepared = singleSpecialPoint(marker, origin, point -> Blocks.SPAWNER.defaultBlockState());
            assertTrue(prepared.supported(), marker + ": " + prepared.issues());
            assertEquals(1, prepared.setups().size());
            assertEquals(BuildingPlacement.SetupKind.SPAWNER, prepared.setups().getFirst().kind());
            assertTrue(prepared.changes().getFirst().state().is(Blocks.SPAWNER));
            assertEquals(List.of(new LegacyBuildingPlan.Position(origin.getX(), origin.getY(), origin.getZ())),
                    prepared.servicePoints().get("spawners"));
            assertTrue(prepared.setups().getFirst().binding().name().startsWith("minecraft:"));
        }
    }

    @Test void workMarkersClearTheirCellAndKeepTheirServicePosition() throws Exception {
        for (String marker : List.of("stall", "brickspot", "healingspot", "fishingspot")) {
            var origin = new BlockPos(11, 64, 22);
            var prepared = singleSpecialPoint(marker, origin, LegacyBlockStateResolver::resolve);
            assertTrue(prepared.supported(), prepared.issues().toString());
            var world = new MemoryWorld();
            world.blocks.put(origin, Blocks.STONE.defaultBlockState());
            assertEquals(1, BuildingPlacement.place(prepared, world, true));
            assertTrue(world.get(origin).isAir());
            assertEquals(List.of(new LegacyBuildingPlan.Position(11, 64, 22)), prepared.servicePoints().get(marker));
        }
    }

    @Test void brewingStandIsPlacedEmptyAndItsDestinationIsChecked() throws Exception {
        var prepared = singleSpecialPoint("brewingstand", new BlockPos(0, 64, 0), LegacyBlockStateResolver::resolve);
        assertTrue(prepared.supported(), prepared.issues().toString());
        var state = prepared.changes().getFirst().state();
        assertTrue(state.is(Blocks.BREWING_STAND));
        for (var bottle : net.minecraft.world.level.block.BrewingStandBlock.HAS_BOTTLE) assertFalse(state.getValue(bottle));
        var world = new MemoryWorld();
        world.blocked = prepared.changes().getFirst().pos();
        assertThrows(IllegalArgumentException.class, () -> BuildingPlacement.place(prepared, world, true));
        assertEquals(0, world.writes);
        assertEquals(List.of(new LegacyBuildingPlan.Position(0, 64, 0)), prepared.servicePoints().get("brewingstand"));
    }

    private BuildingPlacement.Prepared singleSpecialPoint(String label, BlockPos origin,
            java.util.function.Function<LegacyPalette.Point, BlockState> resolver) throws Exception {
        Path image = temp.resolve(label + "_A0.png");
        var pixels = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        pixels.setRGB(0, 0, 0x00ffff);
        ImageIO.write(pixels, "png", image.toFile());
        var plan = new LegacyBuildingPlan("byzantine", label, 'A', 0, 1, 1, 0, image, Map.of());
        var palette = new LegacyPalette(Map.of(0x00ffff,
                new LegacyPalette.Point(0x00ffff, label, "", "0", false, "", "", 0)));
        return BuildingPlacement.prepare(plan, palette, origin, 0, resolver);
    }

    @Test void powderDispenserKeepsLegacyDownwardFacingAndRequiresSetupSupport() throws Exception {
        var prepared = singleSpecialPoint("dispenserunknownpowder", BlockPos.ZERO, LegacyBlockStateResolver::resolve);
        assertTrue(prepared.supported(), prepared.issues().toString());
        assertEquals(net.minecraft.core.Direction.DOWN, prepared.changes().getFirst().state()
                .getValue(net.minecraft.world.level.block.DispenserBlock.FACING));
        assertEquals(BuildingPlacement.SetupKind.DISPENSER, prepared.setups().getFirst().kind());
        assertEquals("millenaire:unknownpowder", prepared.setups().getFirst().binding().name());
        var world = new MemoryWorld();
        assertThrows(IllegalArgumentException.class, () -> BuildingPlacement.place(prepared, world, true));
        assertEquals(0, world.writes);
    }

    @Test void filledDispenserIsEmptiedBeforeRollbackRemovesItsBlock() throws Exception {
        var prepared = singleSpecialPoint("dispenserunknownpowder", BlockPos.ZERO, LegacyBlockStateResolver::resolve);
        class DispenserWorld extends MemoryWorld {
            boolean filled, cleaned;
            @Override public String setupRejection(BuildingPlacement.Setup setup) { return null; }
            @Override public void initialize(BuildingPlacement.Setup setup) { filled = true; }
            @Override public void finish(List<BuildingPlacement.Change> changes) { throw new IllegalStateException("Finish failed"); }
            @Override public void discardSetupContents(List<BuildingPlacement.Setup> setups) {
                assertTrue(get(BlockPos.ZERO).is(Blocks.DISPENSER));
                filled = false; cleaned = true;
            }
            @Override public boolean set(BlockPos pos, BlockState state) {
                if (state.isAir()) assertFalse(filled, "Rollback must not drop dispenser inventory");
                return super.set(pos, state);
            }
        }
        var world = new DispenserWorld();
        assertThrows(IllegalStateException.class, () -> BuildingPlacement.place(prepared, world, true));
        assertTrue(world.cleaned);
        assertTrue(world.get(BlockPos.ZERO).isAir());
    }

    private BuildingPlacement.Prepared treePlacement(BlockPos origin, boolean collision) throws Exception {
        Path image = temp.resolve("tree_A0.png");
        var pixels = new BufferedImage(collision ? 2 : 1, 1, BufferedImage.TYPE_INT_RGB);
        pixels.setRGB(0, 0, collision ? 0xff0000 : 0x0000ff);
        if (collision) pixels.setRGB(1, 0, 0x0000ff);
        ImageIO.write(pixels, "png", image.toFile());
        var plan = new LegacyBuildingPlan("norman", "tree", 'A', 0, collision ? 2 : 1, 1, 0, image, Map.of());
        var palette = new LegacyPalette(Map.of(
                0x0000ff, new LegacyPalette.Point(0x0000ff, "appletreespawn", "", "0", false, "", "", 1),
                0xff0000, new LegacyPalette.Point(0xff0000, "stone", "minecraft:stone", "0", false, "", "", 1)));
        return BuildingPlacement.prepare(plan, palette, origin, 0, LegacyBlockStateResolver::resolve,
                LegacyGoodsCatalog.empty(), 4,
                (marker, root, seed) -> Map.of(root, Blocks.OAK_LOG.defaultBlockState(),
                        collision ? root.offset(0, 0, 1) : root.above(), Blocks.OAK_LEAVES.defaultBlockState()));
    }

    private LegacyBuildingPlan plan() throws Exception {
        Path image = temp.resolve("demo_A0.png");
        var pixels = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        pixels.setRGB(0, 0, 0xff0000); pixels.setRGB(1, 0, 0x0000ff);
        ImageIO.write(pixels, "png", image.toFile());
        return new LegacyBuildingPlan("norman", "demo", 'A', 0, 2, 1, 0, image, Map.of());
    }
    private LegacyPalette palette(boolean unsupported) {
        return new LegacyPalette(Map.of(
                0xff0000, new LegacyPalette.Point(0xff0000, "stone", "minecraft:stone", "0", false, "", "", 1),
                0x0000ff, new LegacyPalette.Point(0x0000ff, "brick", "minecraft:stonebrick", unsupported ? "unknown=value" : "0", false, "", "", 1)));
    }
    @Test void explicitWallAttachmentsUseSecondPassAndExistingSupportsForEveryRotation() throws Exception {
        for (String block : List.of("ladder", "wall_torch", "redstone_wall_torch")) for (int rotation = 0; rotation < 4; rotation++) {
            var prepared = wallAttachmentPlacement(block, rotation);
            assertTrue(prepared.supported(), prepared.issues().toString());
            var attachment = prepared.changes().getFirst();
            assertTrue(attachment.secondPass());
            var facing = attachment.state().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
            var world = new MemoryWorld();
            world.blocks.put(attachment.pos().relative(facing.getOpposite()), Blocks.STONE.defaultBlockState());
            assertEquals(1, BuildingPlacement.place(prepared, world, false));
            assertEquals(attachment.state(), world.get(attachment.pos()));
        }
    }

    @Test void wallAttachmentsRejectMissingWrongSideAndRemovedSupportsBeforeWriting() throws Exception {
        for (String block : List.of("ladder", "wall_torch", "redstone_wall_torch")) {
            var prepared = wallAttachmentPlacement(block, 0);
            var attachment = prepared.changes().getFirst();
            var facing = attachment.state().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
            var world = new MemoryWorld();
            world.blocks.put(attachment.pos().relative(facing), Blocks.STONE.defaultBlockState());
            assertThrows(IllegalArgumentException.class, () -> BuildingPlacement.place(prepared, world, true));
            assertEquals(0, world.writes);
            BlockPos support = attachment.pos().relative(facing.getOpposite());
            world.blocks.put(support, Blocks.STONE.defaultBlockState());
            var clearing = new BuildingPlacement.Prepared(List.of(new BuildingPlacement.Change(support, Blocks.AIR.defaultBlockState(), false), attachment), List.of(), Map.of());
            assertThrows(IllegalArgumentException.class, () -> BuildingPlacement.place(clearing, world, true));
            assertEquals(0, world.writes);
        }
    }

    @Test void wallAttachmentWriteFailureRestoresItsNewSupport() throws Exception {
        var attachment = wallAttachmentPlacement("redstone_wall_torch", 0).changes().getFirst();
        var facing = attachment.state().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
        BlockPos support = attachment.pos().relative(facing.getOpposite());
        var prepared = new BuildingPlacement.Prepared(List.of(new BuildingPlacement.Change(support, Blocks.STONE.defaultBlockState(), false), attachment), List.of(), Map.of());
        var world = new MemoryWorld();
        world.blocks.put(support, Blocks.DIRT.defaultBlockState());
        world.blocks.put(attachment.pos(), Blocks.DIRT.defaultBlockState());
        var original = Map.copyOf(world.blocks);
        world.failAt = 2;
        assertThrows(IllegalStateException.class, () -> BuildingPlacement.place(prepared, world, true));
        assertEquals(original, world.blocks);
    }

    private BuildingPlacement.Prepared wallAttachmentPlacement(String block, int rotation) throws Exception {
        Path image = temp.resolve("attachment_A0.png");
        var pixels = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        pixels.setRGB(0, 0, 0x0000ff);
        ImageIO.write(pixels, "png", image.toFile());
        var plan = new LegacyBuildingPlan("norman", "attachment", 'A', 0, 1, 1, 0, image, Map.of());
        var palette = new LegacyPalette(Map.of(0x0000ff, new LegacyPalette.Point(0x0000ff, "attachment", "minecraft:" + block, "facing=west", false, "", "", 1)));
        return BuildingPlacement.prepare(plan, palette, new BlockPos(20, 64, 30), rotation);
    }
    private static class MemoryWorld implements BuildingPlacement.WorldAccess {
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        int writes, failAt = -1;
        BlockPos blocked;
        public BlockState get(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        public String rejection(BlockPos pos, boolean replace) { return pos.equals(blocked) ? "Protected position" : null; }
        public boolean set(BlockPos pos, BlockState state) {
            writes++;
            if (writes == failAt) return false;
            blocks.put(pos, state);
            return true;
        }
    }
}
