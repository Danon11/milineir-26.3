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

/**
 * Millenaire stage based crops. The original Forge implementation used a
 * growth chance of 8 (or 4 for slow crops) and optionally required irrigation
 * in the soil block. Fabric keeps the same age 0..7 semantics while using the
 * modern CropBlock placement and bonemeal support.
 */
public class GenericCropBlock extends CropBlock {
    private final Identifier seedId;
    private final boolean requireIrrigation;
    private final boolean slowGrowth;

    public GenericCropBlock(BlockBehaviour.Properties properties, String seedName, boolean requireIrrigation, boolean slowGrowth) {
        super(properties);
        this.seedId = Identifier.fromNamespaceAndPath("millenaire", seedName);
        this.requireIrrigation = requireIrrigation;
        this.slowGrowth = slowGrowth;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return BuiltInRegistries.ITEM.getValue(seedId);
    }

    public boolean canPlantAt(LevelReader level, BlockPos pos) {
        return canSurvive(defaultBlockState(), level, pos);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = getAge(state);
        if (age >= getMaxAge() || level.getRawBrightness(pos.above(), 0) < 9) {
            return;
        }
        // BlockMillCrops checked the block below for irrigation metadata.
        // Modern farmland exposes moisture directly; treat wet farmland as
        // irrigated and retain normal growth for crops without that requirement.
        if (requireIrrigation && !isIrrigated(level, pos.below())) {
            return;
        }
        // Forge used nextInt((int)(25 / growthChance)): 3 ticks for normal
        // crops (chance 8) and 6 for slow crops (chance 4).
        int denominator = slowGrowth ? 6 : 3;
        if (random.nextInt(denominator) == 0) {
            level.setBlock(pos, getStateForAge(age + 1), Block.UPDATE_CLIENTS);
        }
    }

    private static boolean isIrrigated(ServerLevel level, BlockPos soilPos) {
        BlockState soil = level.getBlockState(soilPos);
        if (soil.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.MOISTURE)) {
            return soil.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.MOISTURE) > 0;
        }
        return soil.is(net.minecraft.world.level.block.Blocks.FARMLAND);
    }
}
