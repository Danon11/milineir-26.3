package org.millenaire.fabric.content;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.portal.PortalShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlannedPortalTest {
    static final Map<net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block>, List<net.minecraft.core.Holder<net.minecraft.world.level.block.Block>>> savedTags = new HashMap<>();
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var registry = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        registry.getTags().filter(net.minecraft.core.HolderSet.Named::isBound).forEach(tag -> savedTags.put(tag.key(), tag.stream().toList()));
        var tags = new HashMap<>(savedTags);
        // Plain Bootstrap does not load data-pack tags. Bind the vanilla frame tag
        // for the native validator, then restore the previous tags after this class.
        tags.put(net.minecraft.tags.BlockTags.NETHER_PORTAL_FRAME, List.of(Blocks.OBSIDIAN.builtInRegistryHolder()));
        registry.prepareTagReload(new net.minecraft.tags.TagLoader.LoadResult<>(net.minecraft.core.registries.Registries.BLOCK, tags)).apply();
    }
    @AfterAll static void restoreTags() {
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.prepareTagReload(
                new net.minecraft.tags.TagLoader.LoadResult<>(net.minecraft.core.registries.Registries.BLOCK, savedTags)).apply();
    }
    static final BlockPos ROOT = new BlockPos(20, 64, 30);

    @Test void minimalCornerlessFramesMatchNativeValidationOnBothAxes() {
        for (var axis : List.of(Direction.Axis.X, Direction.Axis.Z)) {
            var plan = frame(2, 3, axis);
            assertTrue(PortalShape.findAnyShape(view(plan), ROOT, axis).isValid());
            var blocks = PlannedPortal.expand(plan, ROOT, axis);
            assertEquals(6, blocks.size());
            for (var state : blocks.values()) assertEquals(axis, state.getValue(NetherPortalBlock.AXIS));
        }
    }

    @Test void largestNativeFrameFillsAll441InteriorCells() {
        var plan = frame(21, 21, Direction.Axis.X);
        assertTrue(PortalShape.findAnyShape(view(plan), ROOT, Direction.Axis.X).isValid());
        assertEquals(441, PlannedPortal.expand(plan, ROOT.above(20).east(20), Direction.Axis.X).size());
    }

    @Test void rejectsWrongDimensionsMissingFrameUnknownCellsAndSolidInterior() {
        for (int[] dimensions : List.of(new int[]{1, 3}, new int[]{2, 2}, new int[]{22, 3}, new int[]{2, 22}))
            assertThrows(IllegalArgumentException.class, () -> PlannedPortal.expand(frame(dimensions[0], dimensions[1], Direction.Axis.X), ROOT, Direction.Axis.X));
        for (int fault = 0; fault < 3; fault++) {
            var plan = frame(2, 3, Direction.Axis.X);
            if (fault == 0) plan.remove(ROOT.west());
            else if (fault == 1) plan.remove(ROOT.east().above());
            else plan.put(ROOT.east().above(), Blocks.STONE.defaultBlockState());
            assertThrows(IllegalArgumentException.class, () -> PlannedPortal.expand(plan, ROOT, Direction.Axis.X));
        }
    }

    @Test void fallsBackToOtherAxisAndAcceptsExistingPortalOrFireInterior() {
        var plan = frame(2, 3, Direction.Axis.Z);
        plan.put(ROOT, Blocks.NETHER_PORTAL.defaultBlockState());
        plan.put(ROOT.south(), Blocks.FIRE.defaultBlockState());
        var blocks = PlannedPortal.expand(plan, ROOT.above(), Direction.Axis.X);
        assertEquals(6, blocks.size());
        assertTrue(blocks.values().stream().allMatch(state -> state.getValue(NetherPortalBlock.AXIS) == Direction.Axis.Z));
    }

    private static Map<BlockPos, BlockState> frame(int width, int height, Direction.Axis axis) {
        var plan = new HashMap<BlockPos, BlockState>();
        var right = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        for (int x = 0; x < width; x++) {
            plan.put(ROOT.relative(right, x).below(), Blocks.OBSIDIAN.defaultBlockState());
            plan.put(ROOT.relative(right, x).above(height), Blocks.OBSIDIAN.defaultBlockState());
            for (int y = 0; y < height; y++) plan.put(ROOT.relative(right, x).above(y), Blocks.AIR.defaultBlockState());
        }
        for (int y = 0; y < height; y++) {
            plan.put(ROOT.relative(right.getOpposite()).above(y), Blocks.OBSIDIAN.defaultBlockState());
            plan.put(ROOT.relative(right, width).above(y), Blocks.OBSIDIAN.defaultBlockState());
        }
        return plan;
    }

    private static BlockGetter view(Map<BlockPos, BlockState> plan) {
        return new BlockGetter() {
            public BlockState getBlockState(BlockPos pos) { return plan.getOrDefault(pos, Blocks.BEDROCK.defaultBlockState()); }
            public BlockEntity getBlockEntity(BlockPos pos) { return null; }
            public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
            public int getMinY() { return 0; }
            public int getHeight() { return 256; }
        };
    }
}
