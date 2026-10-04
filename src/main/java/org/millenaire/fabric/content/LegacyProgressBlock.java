package org.millenaire.fabric.content;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Four-stage legacy sericulture and snail-soil blocks. */
public final class LegacyProgressBlock extends Block {
    public static final IntegerProperty PROGRESS = IntegerProperty.create("progress", 0, 3);

    public enum Kind { SILKWORM, SNAIL_SOIL }

    private final Kind kind;

    public LegacyProgressBlock(BlockBehaviour.Properties properties, Kind kind) {
        super(properties.randomTicks());
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(PROGRESS, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PROGRESS);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int progress = state.getValue(PROGRESS);
        if (progress >= 3 || random.nextInt(2) != 0) return;
        if (kind == Kind.SILKWORM) {
            if (level.getRawBrightness(pos.above(), 0) >= 7) return;
        } else {
            BlockState water = level.getBlockState(pos.above());
            BlockState headspace = level.getBlockState(pos.above(2));
            if (!water.is(net.minecraft.world.level.block.Blocks.WATER)
                    || !(headspace.isAir() || headspace.canBeReplaced())) return;
        }
        level.setBlock(pos, state.setValue(PROGRESS, progress + 1), Block.UPDATE_CLIENTS);
    }

    public Kind kind() {
        return kind;
    }
}
