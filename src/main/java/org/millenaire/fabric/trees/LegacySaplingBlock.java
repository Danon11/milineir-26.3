package org.millenaire.fabric.trees;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class LegacySaplingBlock extends SaplingBlock {
    private final FruitTreeGenerator.Kind kind;

    public LegacySaplingBlock(String name, BlockBehaviour.Properties properties) {
        super(TreeGrower.OAK, properties);
        this.kind = FruitTreeGenerator.Kind.fromSapling(name);
    }

    @Override
    public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (state.getValue(STAGE) == 0) {
            level.setBlock(pos, state.setValue(STAGE, 1), 4);
        } else {
            FruitTreeGenerator.grow(level, pos, state, kind, random);
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
        return level instanceof ServerLevel && level.isInsideBuildHeight(pos.above(10))
                && state.canSurvive(level, pos);
    }
}
