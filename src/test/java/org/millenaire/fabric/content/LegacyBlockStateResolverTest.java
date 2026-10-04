package org.millenaire.fabric.content;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class LegacyBlockStateResolverTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void migratesBlockFamiliesAndMetadata() {
        assertTrue(resolve("planks", "variant=spruce").is(Blocks.SPRUCE_PLANKS));
        assertTrue(resolve("log2", "variant=dark_oak,axis=x").is(Blocks.DARK_OAK_LOG));
        assertTrue(resolve("wool", "8").is(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("minecraft:light_gray_wool"))));
        assertTrue(resolve("log", "4").is(Blocks.OAK_LOG));
        assertTrue(resolve("log", "12").is(Blocks.OAK_WOOD));
        assertTrue(resolve("stone", "variant=smooth_diorite").is(Blocks.POLISHED_DIORITE));
        assertTrue(resolve("stonebrick", "3").is(Blocks.CHISELED_STONE_BRICKS));
        assertEquals(SlabType.TOP, resolve("wooden_slab", "9").getValue(SlabBlock.TYPE));
        assertTrue(resolve("stone_slab", "variant=quartz,half=top").is(Blocks.QUARTZ_SLAB));
        assertEquals(Direction.WEST, resolve("torch", "facing=west").getValue(WallTorchBlock.FACING));
        assertTrue(resolve("torch", "facing=up").is(Blocks.TORCH));
        assertTrue(resolve("flower_pot", "15").is(Blocks.POTTED_ACACIA_SAPLING));
        assertTrue(resolve("standing_sign", "rotation=4").is(Blocks.OAK_SIGN));
        assertEquals(Direction.EAST, resolve("wall_sign", "facing=east").getValue(net.minecraft.world.level.block.WallSignBlock.FACING));
    }
    @Test void rejectsUnknownVariantsPropertiesAndNumericOrientation() {
        assertThrows(IllegalArgumentException.class, () -> resolve("wool", "100"));
        assertThrows(IllegalArgumentException.class, () -> resolve("sandstone", "variant=unknown"));
        assertThrows(IllegalArgumentException.class, () -> resolve("stone", "facing=north"));
        assertThrows(IllegalArgumentException.class, () -> resolve("oak_stairs", "0"));
    }
    @Test void migratesBedMetadataAndRejectsValuesOutsideFourBits() {
        for (int meta = 0; meta < 16; meta++) {
            var state = resolve("bed", Integer.toString(meta));
            assertEquals(net.minecraft.resources.Identifier.parse("minecraft:red_bed"),
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()));
            assertEquals(List.of(Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST).get(meta & 3),
                    state.getValue(net.minecraft.world.level.block.BedBlock.FACING));
            assertEquals((meta & 8) != 0 ? net.minecraft.world.level.block.state.properties.BedPart.HEAD
                    : net.minecraft.world.level.block.state.properties.BedPart.FOOT,
                    state.getValue(net.minecraft.world.level.block.BedBlock.PART));
            assertEquals((meta & 4) != 0, state.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED));
        }
        assertThrows(IllegalArgumentException.class, () -> resolve("bed", "16"));
    }
    @Test void auditsBundledVanillaPaletteAgainstTheCurrentRegistry() throws Exception {
        var palette = LegacyPalette.read(Path.of(getClass().getResource("/todeploy/millenaire/blocklist.txt").toURI()));
        int supported = 0, unsupported = 0;
        var report = new ArrayList<String>();
        report.add("status\tcolor\tlabel\tlegacy_block\tlegacy_state\tresult");
        for (var point : palette.points().values()) {
            if (!point.block().startsWith("minecraft:")) continue;
            String prefix = Integer.toHexString(point.color()) + "\t" + point.label() + "\t" + point.block() + "\t" + point.state() + "\t";
            try { var state = LegacyBlockStateResolver.resolve(point); supported++; report.add("supported\t" + prefix + state); }
            catch (IllegalArgumentException error) { unsupported++; report.add("unsupported\t" + prefix + error.getMessage()); }
        }
        Path output = Path.of("build", "reports", "porting", "vanilla-palette.tsv");
        Files.createDirectories(output.getParent());
        Files.write(output, report);
        System.out.println("Vanilla palette states: supported=" + supported + ", unsupported=" + unsupported);
        assertTrue(supported > 250);
    }
    @Test void legacyPortalMetadataMigratesToHorizontalAxisOnly() {
        assertEquals(Direction.Axis.X, resolve("portal", "0").getValue(net.minecraft.world.level.block.NetherPortalBlock.AXIS));
        assertEquals(resolve("portal", "0"), resolve("portal", "1"));
        assertEquals(Direction.Axis.Z, resolve("portal", "2").getValue(net.minecraft.world.level.block.NetherPortalBlock.AXIS));
        assertThrows(IllegalArgumentException.class, () -> resolve("portal", "3"));
        assertThrows(IllegalArgumentException.class, () -> resolve("portal", "axis=y"));
    }
    @Test void dispenserMetadataKeepsSixDirectionsAndTriggerBit() {
        var directions = List.of(Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);
        for (int index = 0; index < 6; index++) for (int triggered = 0; triggered < 2; triggered++) {
            var state = resolve("dispenser", Integer.toString(index | (triggered == 1 ? 8 : 0)));
            assertEquals(directions.get(index), state.getValue(net.minecraft.world.level.block.DispenserBlock.FACING));
            assertEquals(triggered == 1, state.getValue(net.minecraft.world.level.block.DispenserBlock.TRIGGERED));
        }
        assertThrows(IllegalArgumentException.class, () -> resolve("dispenser", "6"));
        assertThrows(IllegalArgumentException.class, () -> resolve("dispenser", "16"));
    }

    @Test void fenceGateMetadataKeepsDirectionOpenAndPoweredBits() {
        for (int meta = 0; meta < 16; meta++) {
            var state = resolve("fence_gate", Integer.toString(meta));
            assertBlockId("oak_fence_gate", state);
            assertEquals(List.of(Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST).get(meta & 3),
                    state.getValue(net.minecraft.world.level.block.FenceGateBlock.FACING));
            assertEquals((meta & 4) != 0, state.getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN));
            assertEquals((meta & 8) != 0, state.getValue(net.minecraft.world.level.block.FenceGateBlock.POWERED));
        }
        assertThrows(IllegalArgumentException.class, () -> resolve("fence_gate", "16"));
    }

    @Test void vineMetadataKeepsAllFourAttachmentBits() {
        for (int meta = 0; meta < 16; meta++) {
            var state = resolve("vine", Integer.toString(meta));
            assertEquals((meta & 1) != 0, state.getValue(net.minecraft.world.level.block.VineBlock.SOUTH));
            assertEquals((meta & 2) != 0, state.getValue(net.minecraft.world.level.block.VineBlock.WEST));
            assertEquals((meta & 4) != 0, state.getValue(net.minecraft.world.level.block.VineBlock.NORTH));
            assertEquals((meta & 8) != 0, state.getValue(net.minecraft.world.level.block.VineBlock.EAST));
        }
        assertThrows(IllegalArgumentException.class, () -> resolve("vine", "16"));
    }

    @Test void redstoneTorchesKeepTheirStandingOrWallOrientation() {
        assertTrue(resolve("redstone_torch", "0").is(Blocks.REDSTONE_TORCH));
        assertEquals(resolve("redstone_torch", "0"), resolve("redstone_torch", "5"));
        var directions = List.of(Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH);
        for (int index = 0; index < directions.size(); index++) {
            var state = resolve("redstone_torch", Integer.toString(index + 1));
            assertTrue(state.is(Blocks.REDSTONE_WALL_TORCH));
            assertEquals(directions.get(index), state.getValue(net.minecraft.world.level.block.RedstoneWallTorchBlock.FACING));
            assertEquals(state, resolve("redstone_torch", "facing=" + directions.get(index).getName()));
        }
        assertThrows(IllegalArgumentException.class, () -> resolve("redstone_torch", "6"));
    }
    @Test void numericDirtAndSandstoneMatchTheirNamedLegacyVariants() {
        var dirt = List.of("dirt", "coarse_dirt", "podzol");
        for (int meta = 0; meta < 3; meta++) {
            assertBlockId(dirt.get(meta), resolve("dirt", Integer.toString(meta)));
            assertEquals(resolve("dirt", "variant=" + dirt.get(meta)), resolve("dirt", Integer.toString(meta)));
        }
        for (String family : List.of("sandstone", "red_sandstone")) {
            assertBlockId(family, resolve(family, "0"));
            assertBlockId("chiseled_" + family, resolve(family, "1"));
            assertBlockId("cut_" + family, resolve(family, "2"));
            assertEquals(resolve(family, "variant=smooth_" + family), resolve(family, "2"));
            assertThrows(IllegalArgumentException.class, () -> resolve(family, "3"));
        }
        assertThrows(IllegalArgumentException.class, () -> resolve("dirt", "3"));
        assertThrows(IllegalArgumentException.class, () -> resolve("dirt", "variant=stone"));
    }

    @Test void cauldronLevelsSelectEmptyOrWaterCauldronWithoutLosingFill() {
        assertTrue(resolve("cauldron", "0").is(Blocks.CAULDRON));
        assertEquals(resolve("cauldron", "0"), resolve("cauldron", "level=0"));
        for (int level = 1; level <= 3; level++) {
            var state = resolve("cauldron", Integer.toString(level));
            assertTrue(state.is(Blocks.WATER_CAULDRON));
            assertEquals(level, state.getValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL));
            assertEquals(state, resolve("cauldron", "level=" + level));
        }
        assertThrows(IllegalArgumentException.class, () -> resolve("cauldron", "4"));
        assertThrows(IllegalArgumentException.class, () -> resolve("cauldron", "level=-1"));
    }

    @Test void netherWartAgeAndPurpurSlabHeightArePreserved() {
        for (int age = 0; age <= 3; age++) {
            var state = resolve("nether_wart", Integer.toString(age));
            assertEquals(age, state.getValue(net.minecraft.world.level.block.NetherWartBlock.AGE));
            assertEquals(state, resolve("nether_wart", "age=" + age));
        }
        assertEquals(SlabType.BOTTOM, resolve("purpur_slab", "0").getValue(SlabBlock.TYPE));
        assertEquals(SlabType.TOP, resolve("purpur_slab", "8").getValue(SlabBlock.TYPE));
        assertThrows(IllegalArgumentException.class, () -> resolve("nether_wart", "4"));
        assertThrows(IllegalArgumentException.class, () -> resolve("purpur_slab", "1"));
    }
    @Test void migratesRenamedMaterialsAndTheirVariants() {
        assertBlockId("nether_bricks", resolve("nether_brick", "0"));
        assertBlockId("melon", resolve("melon_block", "0"));
        assertBlockId("note_block", resolve("noteblock", "0"));
        assertBlockId("oak_pressure_plate", resolve("wooden_pressure_plate", "0"));
        assertBlockId("light_gray_glazed_terracotta", resolve("silver_glazed_terracotta", "facing=west"));
        assertEquals(Direction.WEST, resolve("silver_glazed_terracotta", "facing=west").getValue(net.minecraft.world.level.block.GlazedTerracottaBlock.FACING));
        assertBlockId("red_sand", resolve("sand", "1"));
        assertBlockId("wet_sponge", resolve("sponge", "1"));
        assertBlockId("mossy_cobblestone_wall", resolve("cobblestone_wall", "1"));
        assertThrows(IllegalArgumentException.class, () -> resolve("sand", "2"));
        assertThrows(IllegalArgumentException.class, () -> resolve("cobblestone_wall", "variant=unknown"));
    }

    @Test void redSandstoneSlabsKeepHalfAndDoubleShape() {
        assertBlockId("red_sandstone_slab", resolve("stone_slab2", "0"));
        assertEquals(SlabType.BOTTOM, resolve("stone_slab2", "0").getValue(SlabBlock.TYPE));
        assertEquals(SlabType.TOP, resolve("stone_slab2", "8").getValue(SlabBlock.TYPE));
        assertEquals(SlabType.TOP, resolve("stone_slab2", "variant=red_sandstone,half=top").getValue(SlabBlock.TYPE));
        assertEquals(SlabType.DOUBLE, resolve("double_stone_slab2", "0").getValue(SlabBlock.TYPE));
        assertThrows(IllegalArgumentException.class, () -> resolve("stone_slab2", "1"));
        assertThrows(IllegalArgumentException.class, () -> resolve("stone_slab2", "half=unknown"));
    }

    @Test void litBlocksAndCarvedPumpkinsKeepTheirLegacyStates() {
        for (int meta = 0; meta < 4; meta++) for (String block : List.of("pumpkin", "lit_pumpkin")) {
            var state = resolve(block, Integer.toString(meta));
            assertBlockId(block.equals("pumpkin") ? "carved_pumpkin" : "jack_o_lantern", state);
            assertEquals(List.of(Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST).get(meta),
                    state.getValue(net.minecraft.world.level.block.CarvedPumpkinBlock.FACING));
        }
        var lamp = resolve("lit_redstone_lamp", "0");
        assertTrue(lamp.is(Blocks.REDSTONE_LAMP));
        assertTrue(lamp.getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT));
        assertThrows(IllegalArgumentException.class, () -> resolve("pumpkin", "4"));
        assertThrows(IllegalArgumentException.class, () -> resolve("lit_redstone_lamp", "1"));
    }
    @Test void migratesAllSmallFlowerAndGrassVariants() {
        var flowers = List.of("poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy");
        for (int index = 0; index < flowers.size(); index++) assertBlockId(flowers.get(index), resolve("red_flower", Integer.toString(index)));
        assertBlockId("dandelion", resolve("yellow_flower", "0"));
        var grasses = List.of("dead_bush", "short_grass", "fern");
        for (int index = 0; index < grasses.size(); index++) assertBlockId(grasses.get(index), resolve("tallgrass", Integer.toString(index)));
        assertBlockId("azure_bluet", resolve("red_flower", "type=houstonia"));
        assertBlockId("short_grass", resolve("tallgrass", "type=grass"));
        assertThrows(IllegalArgumentException.class, () -> resolve("red_flower", "9"));
        assertThrows(IllegalArgumentException.class, () -> resolve("yellow_flower", "1"));
        assertThrows(IllegalArgumentException.class, () -> resolve("tallgrass", "3"));
    }

    @Test void saplingsKeepAllSixSpeciesAndBothGrowthStages() {
        var woods = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak");
        for (int index = 0; index < woods.size(); index++) {
            var named = resolve("sapling", "type=" + woods.get(index));
            assertBlockId(woods.get(index) + "_sapling", named);
            assertEquals(named, resolve("sapling", Integer.toString(index)));
            var mature = resolve("sapling", Integer.toString(index | 8));
            assertEquals(1, mature.getValue(net.minecraft.world.level.block.SaplingBlock.STAGE));
            assertEquals(named.getBlock(), mature.getBlock());
        }
        assertThrows(IllegalArgumentException.class, () -> resolve("sapling", "6"));
        assertThrows(IllegalArgumentException.class, () -> resolve("sapling", "16"));
        assertThrows(IllegalArgumentException.class, () -> resolve("sapling", "type=unknown"));
    }

    @Test void leavesKeepTheirSpeciesAndLegacyDecayFlag() {
        var woods = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak");
        for (int index = 0; index < woods.size(); index++) {
            String family = index < 4 ? "leaves" : "leaves2";
            int variant = index < 4 ? index : index - 4;
            var persistent = resolve(family, "variant=" + woods.get(index) + ",decayable=false");
            assertBlockId(woods.get(index) + "_leaves", persistent);
            assertTrue(persistent.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT));
            assertEquals(persistent, resolve(family, Integer.toString(variant | 4)));
            assertFalse(resolve(family, Integer.toString(variant)).getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT));
            assertEquals(persistent, resolve(family, "variant=" + woods.get(index) + ",decayable=false,check_decay=true"));
        }
        assertThrows(IllegalArgumentException.class, () -> resolve("leaves2", "2"));
        assertThrows(IllegalArgumentException.class, () -> resolve("leaves", "variant=acacia"));
        assertThrows(IllegalArgumentException.class, () -> resolve("leaves", "decayable=unknown"));
    }

    private static void assertBlockId(String id, BlockState state) {
        assertEquals(net.minecraft.resources.Identifier.parse("minecraft:" + id), net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }
    @Test void buttonMetadataKeepsAttachmentDirectionAndPowerBit() {
        for (String block : List.of("stone_button", "wooden_button")) for (int meta = 0; meta < 16; meta++) {
            var state = resolve(block, Integer.toString(meta));
            int direction = meta & 7;
            assertEquals(direction == 0 ? net.minecraft.world.level.block.state.properties.AttachFace.CEILING
                    : direction >= 5 ? net.minecraft.world.level.block.state.properties.AttachFace.FLOOR
                    : net.minecraft.world.level.block.state.properties.AttachFace.WALL,
                    state.getValue(net.minecraft.world.level.block.ButtonBlock.FACE));
            assertEquals(List.of(Direction.NORTH, Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH, Direction.NORTH,
                    Direction.NORTH, Direction.NORTH).get(direction), state.getValue(net.minecraft.world.level.block.ButtonBlock.FACING));
            assertEquals((meta & 8) != 0, state.getValue(net.minecraft.world.level.block.ButtonBlock.POWERED));
        }
        assertEquals(resolve("stone_button", "0"), resolve("stone_button", "facing=down,powered=false"));
        assertEquals(resolve("wooden_button", "5"), resolve("wooden_button", "facing=up,powered=false"));
        assertThrows(IllegalArgumentException.class, () -> resolve("stone_button", "16"));
    }
    @Test void distinguishesLegacyFullSnowFromAllEightLayerHeights() {
        assertTrue(resolve("snow", "0").is(Blocks.SNOW_BLOCK));
        for (int meta = 0; meta < 8; meta++) {
            var state = resolve("snow_layer", Integer.toString(meta));
            assertTrue(state.is(Blocks.SNOW));
            assertEquals(meta + 1, state.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS));
        }
        assertThrows(IllegalArgumentException.class, () -> resolve("snow_layer", "8"));
        assertThrows(IllegalArgumentException.class, () -> resolve("snow", "1"));
    }

    @Test void acceptsBothLegacyEmptyPotCodesAndRejectsUnknownContents() {
        assertTrue(resolve("flower_pot", "0").is(Blocks.FLOWER_POT));
        assertEquals(resolve("flower_pot", "0"), resolve("flower_pot", "255"));
        assertTrue(resolve("flower_pot", "21").is(Blocks.POTTED_CACTUS));
        assertThrows(IllegalArgumentException.class, () -> resolve("flower_pot", "22"));
        assertThrows(IllegalArgumentException.class, () -> resolve("flower_pot", "254"));
    }
    @Test void migratesAllDoublePlantVariantsAndRejectsUnknownOrAmbiguousMetadata() {
        var oldNames = List.of("sunflower", "syringa", "double_grass", "double_fern", "double_rose", "paeonia");
        var newNames = List.of("sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony");
        for (int index = 0; index < oldNames.size(); index++) {
            var state = resolve("double_plant", "variant=" + oldNames.get(index) + ",facing=north,half=lower");
            assertEquals(net.minecraft.resources.Identifier.parse("minecraft:" + newNames.get(index)),
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()));
            assertEquals(state, resolve("double_plant", Integer.toString(index)));
        }
        assertThrows(IllegalArgumentException.class, () -> resolve("double_plant", "variant=unknown"));
        assertThrows(IllegalArgumentException.class, () -> resolve("double_plant", "8"));
    }
    private static BlockState resolve(String block, String state) {
        return LegacyBlockStateResolver.resolve(new LegacyPalette.Point(0, "test", "minecraft:" + block, state, false, "", "", 1));
    }
}
