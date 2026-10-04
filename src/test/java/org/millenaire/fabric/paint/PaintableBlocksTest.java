package org.millenaire.fabric.paint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaintableBlocksTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void mapsEveryPaintedShapeToItsMatchingTargetId() {
        assertEquals("painted_brick_red", PaintableBlocks.blockNameForColor(
                PaintableBlocks.parseBlockName("painted_brick_blue").orElseThrow(), "red"));
        assertEquals("slab_painted_brick_red", PaintableBlocks.blockNameForColor(
                PaintableBlocks.parseBlockName("slab_painted_brick_blue").orElseThrow(), "red"));
        assertEquals("stairs_painted_brick_red", PaintableBlocks.blockNameForColor(
                PaintableBlocks.parseBlockName("stairs_painted_brick_blue").orElseThrow(), "red"));
        assertEquals("wall_painted_brick_red", PaintableBlocks.blockNameForColor(
                PaintableBlocks.parseBlockName("wall_painted_brick_blue").orElseThrow(), "red"));
    }

    @Test
    void mapsDecoratedFamilyAndSilverToLightGray() {
        assertEquals("painted_brick_decorated_red", PaintableBlocks.blockNameForColor(
                PaintableBlocks.parseBlockName("painted_brick_decorated_white").orElseThrow(), "red"));
        assertEquals("wall_painted_brick_silver", PaintableBlocks.blockNameForColor(
                PaintableBlocks.parseBlockName("wall_painted_brick_white").orElseThrow(), "silver"));
        assertEquals("light_gray", PaintableBlocks.vanillaDyeName("silver"));
    }

    @Test
    void preservesSharedStairStateWhenChangingBlockId() {
        BlockState source = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.WEST)
                .setValue(StairBlock.HALF, Half.TOP)
                .setValue(StairBlock.SHAPE, StairsShape.OUTER_RIGHT)
                .setValue(StairBlock.WATERLOGGED, true);

        BlockState remapped = PaintableBlocks.copyCompatibleProperties(source, Blocks.STONE_STAIRS);

        assertEquals(propertyValues(source), propertyValues(remapped));
    }

    private static Map<String, String> propertyValues(BlockState state) {
        return state.getValues().collect(Collectors.toMap(
                value -> value.property().getName(), value -> value.valueName()));
    }
}
