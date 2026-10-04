package org.millenaire.fabric.crops;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class MaizeCropBlock extends CropBlock {
    private static final Identifier MAIZE_ID = Identifier.fromNamespaceAndPath("millenaire", "maize");

    public MaizeCropBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    public boolean canPlantAt(LevelReader level, BlockPos pos) {
        return canSurvive(defaultBlockState(), level, pos);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = getAge(state);
        if (age < getMaxAge() && level.getRawBrightness(pos.above(), 0) >= 9 && random.nextInt(6) == 0) {
            level.setBlock(pos, getStateForAge(age + 1), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return BuiltInRegistries.ITEM.getValue(MAIZE_ID);
    }
}
