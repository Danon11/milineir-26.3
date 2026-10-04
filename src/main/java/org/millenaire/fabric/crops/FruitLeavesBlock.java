package org.millenaire.fabric.crops;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

public final class FruitLeavesBlock extends LeavesBlock implements BonemealableBlock {
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
    private final Supplier<Item> fruit;

    public FruitLeavesBlock(BlockBehaviour.Properties properties, Supplier<Item> fruit) {
        super(AmbientLeavesBlockSoundPlayer.noAmbientSound(), properties);
        this.fruit = fruit;
        registerDefaultState(defaultBlockState().setValue(AGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AGE);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);
        if (level.getBlockState(pos).getBlock() != this) return;
        int next = nextAge(state.getValue(AGE), level.getDefaultClockTime());
        if (state.getValue(AGE) != next) level.setBlock(pos, state.setValue(AGE, next), 2);
    }

    static int nextAge(int current, long clockTime) {
        long time = Math.floorMod(clockTime, 24_000);
        int target = time > 6_000 && time < 10_000 ? 3
                : time > 5_000 && time < 6_000 ? 2
                : time > 3_000 && time < 5_000 ? 1 : 0;
        int previous = target == 0 ? 3 : target - 1;
        return current == previous ? target : current;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(AGE) != 3) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            ItemStack harvest = new ItemStack(fruit.get());
            Block.popResource(level, pos, harvest);
            level.setBlock(pos, state.setValue(AGE, 0), 2);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
        return state.getValue(AGE) < 3;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
        level.setBlock(pos, state.setValue(AGE, 3), 2);
    }
}
