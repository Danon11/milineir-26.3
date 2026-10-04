package org.millenaire.fabric.firepit;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Optional;

public final class FirePitBlockEntity extends AbstractFurnaceBlockEntity
        implements ExtendedMenuProvider<BlockPos>, ContainerData {
    private static final int INPUT_COUNT = 3;
    private static final int FUEL_SLOT = 3;
    private static final int OUTPUT_START = 4;
    private static final int COOK_DURATION = 200;

    private int burnTime;
    private int totalBurnTime;
    private final int[] cookTimes = new int[INPUT_COUNT];

    public FirePitBlockEntity(BlockPos pos, BlockState state) {
        super(FirePitContent.BLOCK_ENTITY_TYPE, pos, state, RecipeType.SMELTING);
        this.items = NonNullList.withSize(7, ItemStack.EMPTY);
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < INPUT_COUNT) {
            return getLevel() instanceof ServerLevel serverLevel
                    ? isFirePitBurnable(serverLevel, stack)
                    : stack.has(DataComponents.FOOD);
        }
        return slot == FUEL_SLOT && isFuel(stack);
    }

    @Override
    public int[] getSlotsForFace(net.minecraft.core.Direction side) {
        return switch (side) {
            case UP -> new int[]{0, 1, 2};
            case DOWN -> new int[]{4, 5, 6};
            default -> new int[]{3};
        };
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, net.minecraft.core.Direction side) {
        return canPlaceItem(slot, stack)
                && (side == net.minecraft.core.Direction.UP && slot < INPUT_COUNT
                || side != net.minecraft.core.Direction.UP && side != net.minecraft.core.Direction.DOWN && slot == FUEL_SLOT);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, net.minecraft.core.Direction side) {
        return side == net.minecraft.core.Direction.DOWN && slot >= OUTPUT_START && slot < OUTPUT_START + INPUT_COUNT;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.millenaire.fire_pit");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new FirePitMenu(containerId, inventory, this);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return getBlockPos();
    }

    public int getBurnTime() {
        return burnTime;
    }

    public int getTotalBurnTime() {
        return totalBurnTime;
    }

    public int getCookTime(int index) {
        return cookTimes[index];
    }

    @Override
    public int get(int index) {
        return switch (index) {
            case 0, 1, 2 -> cookTimes[index];
            case 3 -> burnTime;
            case 4 -> totalBurnTime;
            default -> 0;
        };
    }

    @Override
    public void set(int index, int value) {
        switch (index) {
            case 0, 1, 2 -> cookTimes[index] = value;
            case 3 -> burnTime = value;
            case 4 -> totalBurnTime = value;
            default -> {
            }
        }
    }

    @Override
    public int getCount() {
        return 5;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        burnTime = input.getIntOr("FirePitBurnTime", 0);
        totalBurnTime = input.getIntOr("FirePitTotalBurnTime", 0);
        int[] savedCookTimes = input.getIntArray("FirePitCookTimes").orElse(new int[0]);
        Arrays.fill(cookTimes, 0);
        System.arraycopy(savedCookTimes, 0, cookTimes, 0, Math.min(savedCookTimes.length, cookTimes.length));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("FirePitBurnTime", burnTime);
        output.putInt("FirePitTotalBurnTime", totalBurnTime);
        output.putIntArray("FirePitCookTimes", cookTimes);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (getLevel() instanceof ServerLevel serverLevel) {
            Containers.dropContents(serverLevel, pos, this);
        }
        super.preRemoveSideEffects(pos, state);
    }

    public static boolean isFuel(ItemStack stack) {
        return stack.has(DataComponents.COOKING_FUEL) || stack.is(Items.LAVA_BUCKET);
    }

    public static boolean isFirePitBurnable(ServerLevel level, ItemStack stack) {
        Optional<RecipeHolder<SmeltingRecipe>> recipe = smeltingRecipe(level, stack);
        if (recipe.isEmpty()) {
            return false;
        }
        ItemStack result = recipe.get().value().assemble(new SingleRecipeInput(stack));
        return stack.has(DataComponents.FOOD) || result.has(DataComponents.FOOD);
    }

    private static Optional<RecipeHolder<SmeltingRecipe>> smeltingRecipe(ServerLevel level, ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return level.getServer().getRecipeManager().getRecipeFor(
                RecipeType.SMELTING, new SingleRecipeInput(stack), level);
    }

    private boolean canSmelt(ServerLevel level, int index) {
        ItemStack input = getItem(index);
        Optional<RecipeHolder<SmeltingRecipe>> recipe = smeltingRecipe(level, input);
        if (recipe.isEmpty() || !isFirePitBurnable(level, input)) {
            return false;
        }

        ItemStack result = recipe.get().value().assemble(new SingleRecipeInput(input));
        ItemStack output = getItem(OUTPUT_START + index);
        if (output.isEmpty()) {
            return !result.isEmpty() && result.getCount() <= result.getMaxStackSize();
        }
        return ItemStack.isSameItemSameComponents(result, output)
                && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void smelt(ServerLevel level, int index) {
        if (!canSmelt(level, index)) {
            return;
        }
        ItemStack input = getItem(index);
        ItemStack result = smeltingRecipe(level, input).orElseThrow().value()
                .assemble(new SingleRecipeInput(input));
        int outputSlot = OUTPUT_START + index;
        ItemStack output = getItem(outputSlot);
        if (output.isEmpty()) {
            setItem(outputSlot, result.copy());
        } else {
            output.grow(result.getCount());
        }
        input.shrink(1);
        setChanged();
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, FirePitBlockEntity blockEntity) {
        boolean changed = false;
        if (blockEntity.burnTime > 0) {
            blockEntity.burnTime--;
        }

        boolean hasSmeltableInput = false;
        for (int i = 0; i < INPUT_COUNT; i++) {
            if (blockEntity.canSmelt(level, i)) {
                hasSmeltableInput = true;
                break;
            }
        }

        if (blockEntity.burnTime <= 0 && hasSmeltableInput) {
            ItemStack fuel = blockEntity.getItem(FUEL_SLOT);
            int duration = blockEntity.getBurnDuration(level, fuel);
            if (duration > 0) {
                boolean lavaBucket = fuel.is(Items.LAVA_BUCKET);
                blockEntity.burnTime = duration;
                blockEntity.totalBurnTime = duration;
                fuel.shrink(1);
                if (fuel.isEmpty() && lavaBucket) {
                    blockEntity.setItem(FUEL_SLOT, new ItemStack(Items.BUCKET));
                }
                changed = true;
            }
        }

        for (int i = 0; i < INPUT_COUNT; i++) {
            if (blockEntity.burnTime > 0 && blockEntity.canSmelt(level, i)) {
                blockEntity.cookTimes[i]++;
                if (blockEntity.cookTimes[i] >= COOK_DURATION) {
                    blockEntity.cookTimes[i] = 0;
                    blockEntity.smelt(level, i);
                }
                changed = true;
            } else if (blockEntity.cookTimes[i] > 0) {
                blockEntity.cookTimes[i] = Math.max(0, blockEntity.cookTimes[i] - 2);
                changed = true;
            }
        }

        boolean lit = blockEntity.burnTime > 0;
        if (state.getValue(FirePitBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(FirePitBlock.LIT, lit), Block.UPDATE_ALL);
            changed = true;
        }
        if (changed) {
            blockEntity.setChanged();
        }
    }
}
