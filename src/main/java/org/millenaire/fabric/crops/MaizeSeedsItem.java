package org.millenaire.fabric.crops;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class MaizeSeedsItem extends Item {
    private final net.minecraft.world.level.block.CropBlock crop;

    public MaizeSeedsItem(net.minecraft.world.level.block.CropBlock crop, Item.Properties properties) {
        super(properties);
        this.crop = crop;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos soilPos = context.getClickedPos();
        BlockPos cropPos = soilPos.above();
        BlockState soil = level.getBlockState(soilPos);
        if (!soil.is(BlockTags.SUPPORTS_CROPS)
                || !level.getBlockState(cropPos).canBeReplaced()
                || !crop.defaultBlockState().canSurvive(level, cropPos)) {
            return InteractionResult.PASS;
        }
        if (crop instanceof GrapeVineBlock vine && !vine.canPlacePair(level, cropPos)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            boolean planted = crop instanceof GrapeVineBlock vine ? vine.placePair(level, cropPos)
                    : level.setBlock(cropPos, crop.defaultBlockState(), Block.UPDATE_ALL);
            if (!planted) {
                return InteractionResult.FAIL;
            }

            Player player = context.getPlayer();
            if (player != null && !player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
