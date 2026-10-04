package org.millenaire.fabric.trees;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FruitTreeGeneratorTest {
    @Test
    void everySaplingHasItsOriginalWoodAndLeafShape() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (var kind : FruitTreeGenerator.Kind.values()) {
            assertSame(kind, FruitTreeGenerator.Kind.fromSapling(kind.sapling));
            for (long seed = 0; seed < 20; seed++) {
                var shape = FruitTreeGenerator.shape(kind, RandomSource.create(seed));
                assertTrue(shape.height() >= kind.minHeight);
                assertTrue(shape.height() < kind.minHeight + kind.heightRange);
                assertEquals(FruitTreeGenerator.Part.LOG_Y,
                        shape.parts().get(new FruitTreeGenerator.Offset(0, 0, 0)));
                assertTrue(shape.parts().containsValue(FruitTreeGenerator.Part.LEAF));
                assertTrue(shape.parts().values().stream()
                        .filter(part -> part != FruitTreeGenerator.Part.LEAF).count() >= kind.minHeight);
            }
        }
    }

    @Test
    void obstaclePreventsEveryWriteAndKeepsSapling() {
        bootstrap();
        var origin = new BlockPos(0, 64, 0);
        var world = new FakeWorld(origin);
        world.blocks.put(origin.above(), Blocks.STONE.defaultBlockState());
        var before = Map.copyOf(world.blocks);
        assertFalse(placeApple(world, origin));
        assertEquals(before, world.blocks);
        assertEquals(0, world.successfulWrites);
    }

    @Test
    void failedWriteRestoresTheWholeTreeFootprint() {
        bootstrap();
        var origin = new BlockPos(0, 64, 0);
        var world = new FakeWorld(origin);
        world.failAfter = 3;
        var before = Map.copyOf(world.blocks);
        assertFalse(placeApple(world, origin));
        assertEquals(before, world.blocks);
        assertTrue(world.successfulWrites >= 3);
    }

    @Test
    void failedWriteThatMutatesBeforeReturningStillRestoresFootprint() {
        bootstrap();
        var origin = new BlockPos(0, 64, 0);
        var world = new FakeWorld(origin);
        world.failAfter = 3;
        world.mutateOnFailure = true;
        var before = Map.copyOf(world.blocks);
        assertFalse(placeApple(world, origin));
        assertEquals(before, world.blocks);
    }

    @Test
    void successfulGrowthReplacesSaplingWithLogsAndLeaves() {
        bootstrap();
        var origin = new BlockPos(0, 64, 0);
        var world = new FakeWorld(origin);
        assertTrue(placeApple(world, origin));
        assertTrue(world.getBlockState(origin).is(Blocks.OAK_LOG));
        assertTrue(world.blocks.values().stream().anyMatch(state -> state.is(Blocks.OAK_LEAVES)));
        assertFalse(world.blocks.values().stream().anyMatch(state -> state.is(Blocks.OAK_SAPLING)));
    }

    private static boolean placeApple(FakeWorld world, BlockPos origin) {
        var shape = FruitTreeGenerator.shape(FruitTreeGenerator.Kind.APPLE, RandomSource.create(4));
        return FruitTreeGenerator.place(shape, origin, Blocks.OAK_SAPLING.defaultBlockState(),
                Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1),
                Blocks.OAK_LOG.defaultBlockState(), world);
    }

    private static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final class FakeWorld implements FruitTreeGenerator.TreeWorld {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private int failAfter = Integer.MAX_VALUE;
        private int successfulWrites;
        private boolean mutateOnFailure;

        private FakeWorld(BlockPos origin) {
            blocks.put(origin, Blocks.OAK_SAPLING.defaultBlockState());
        }

        public boolean isInBounds(BlockPos pos) { return pos.getY() >= 0 && pos.getY() < 320; }
        public boolean isLoaded(BlockPos pos) { return true; }
        public boolean hasBlockEntity(BlockPos pos) { return false; }
        public BlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        public boolean setBlock(BlockPos pos, BlockState state) {
            if (successfulWrites == failAfter) {
                failAfter = Integer.MAX_VALUE;
                if (mutateOnFailure) blocks.put(pos, state);
                return false;
            }
            successfulWrites++;
            if (state.isAir()) blocks.remove(pos);
            else blocks.put(pos, state);
            return true;
        }
    }
}
