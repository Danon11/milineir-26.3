package org.millenaire.fabric.firepit;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class FirePitMenu extends AbstractContainerMenu {
    private static final int INPUT_START = 0;
    private static final int FUEL_SLOT = 3;
    private static final int OUTPUT_START = 4;
    private static final int PLAYER_START = 7;
    private static final int MAIN_INVENTORY_END = 34;
    private static final int HOTBAR_END = 43;

    private static final int[][] INPUT_POSITIONS = {{56, 8}, {44, 28}, {56, 48}};
    private static final int[][] OUTPUT_POSITIONS = {{104, 8}, {116, 28}, {104, 48}};

    private final FirePitBlockEntity firePit;
    private final Container inventory;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public FirePitMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, findFirePit(playerInventory, pos), pos);
    }

    public FirePitMenu(int containerId, Inventory playerInventory, FirePitBlockEntity firePit) {
        this(containerId, playerInventory, firePit, firePit.getBlockPos());
    }

    private FirePitMenu(int containerId, Inventory playerInventory, FirePitBlockEntity firePit, BlockPos pos) {
        super(FirePitContent.MENU_TYPE, containerId);
        this.firePit = firePit;
        this.inventory = firePit == null ? new SimpleContainer(7) : firePit;
        this.data = firePit == null ? new net.minecraft.world.inventory.SimpleContainerData(5) : firePit;
        Level level = playerInventory.player.level();
        this.access = ContainerLevelAccess.create(level, pos);

        for (int i = 0; i < 3; i++) {
            addSlot(new FirePitSlot(this.inventory, i, INPUT_POSITIONS[i][0], INPUT_POSITIONS[i][1], stack ->
                    this.firePit != null && this.firePit.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel
                            ? FirePitBlockEntity.isFirePitBurnable(serverLevel, stack)
                            : stack.has(net.minecraft.core.component.DataComponents.FOOD)));
        }
        addSlot(new FirePitSlot(this.inventory, FUEL_SLOT, 80, 70, FirePitBlockEntity::isFuel));
        for (int i = 0; i < 3; i++) {
            addSlot(new FirePitSlot(this.inventory, OUTPUT_START + i,
                    OUTPUT_POSITIONS[i][0], OUTPUT_POSITIONS[i][1], stack -> false));
        }
        addStandardInventorySlots(playerInventory, 8, 93);
        addDataSlots(this.data);
    }

    private static FirePitBlockEntity findFirePit(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof FirePitBlockEntity firePit ? firePit : null;
    }

    public int getCookTime(int index) {
        return data.get(index);
    }

    public int getBurnTime() {
        return data.get(3);
    }

    public int getTotalBurnTime() {
        return data.get(4);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, FirePitContent.FIRE_PIT);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (slotIndex >= OUTPUT_START && slotIndex < PLAYER_START) {
            if (!moveItemStackTo(stack, PLAYER_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, original);
        } else if (slotIndex >= PLAYER_START && slotIndex < HOTBAR_END) {
            boolean moved = false;
            if (firePit != null && firePit.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel
                    && FirePitBlockEntity.isFirePitBurnable(serverLevel, stack)) {
                moved = moveItemStackTo(stack, INPUT_START, INPUT_START + 3, false);
            }
            if (!moved && FirePitBlockEntity.isFuel(stack)) {
                moved = moveItemStackTo(stack, FUEL_SLOT, FUEL_SLOT + 1, false);
            }
            if (!moved) {
                if (slotIndex < MAIN_INVENTORY_END) {
                    moved = moveItemStackTo(stack, MAIN_INVENTORY_END, HOTBAR_END, false);
                } else {
                    moved = moveItemStackTo(stack, PLAYER_START, MAIN_INVENTORY_END, false);
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, PLAYER_START, HOTBAR_END, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    private static final class FirePitSlot extends Slot {
        private final java.util.function.Predicate<ItemStack> predicate;

        private FirePitSlot(Container container, int index, int x, int y,
                            java.util.function.Predicate<ItemStack> predicate) {
            super(container, index, x, y);
            this.predicate = predicate;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return predicate.test(stack);
        }
    }
}
