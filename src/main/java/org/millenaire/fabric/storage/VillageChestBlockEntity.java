package org.millenaire.fabric.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import org.millenaire.fabric.economy.StartingStock;

/** Vanilla inventory, lid, menus and persistence with building access and no automation. */
public final class VillageChestBlockEntity extends ChestBlockEntity implements WorldlyContainer {
    private BuildingBinding binding;
    private boolean startingStockInitialized;
    public VillageChestBlockEntity(BlockPos pos, BlockState state) { this(VillageStorageContent.CHEST_TYPE, pos, state); }
    VillageChestBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    public Optional<BuildingBinding> binding() { return Optional.ofNullable(binding); }
    public void bind(BuildingBinding value) { binding = value; setChanged(); }
    public boolean startingStockInitialized() { return startingStockInitialized; }
    public void fillStartingStock(StartingStock.Inventory stock) {
        if (startingStockInitialized || binding == null || !getBlockPos().equals(stock.pos()))
            throw new IllegalStateException("Starting stock requires a new bound chest at " + stock.pos());
        StartingStock.apply(this, stock); startingStockInitialized = true; setChanged();
    }
    public boolean allows(java.util.UUID player, boolean administrator) { return binding == null || binding.allows(player, administrator); }
    private boolean allowed(Player player) {
        boolean administrator = player.isCreative() || player instanceof ServerPlayer serverPlayer
                && serverPlayer.createCommandSourceStack().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        return allows(player.getUUID(), administrator) || ownsVillage(player);
    }
    /** The owner of a player-controlled village may use every chest of it. */
    private boolean ownsVillage(Player player) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return true; // the server decides
        return org.millenaire.fabric.village.PlayerVillages.settlementAt(serverLevel, getBlockPos())
                .map(settlement -> org.millenaire.fabric.FabricVillageOwnership.get(serverLevel.getServer())
                        .owns(org.millenaire.fabric.village.VillageGrowth.key(settlement), player.getUUID()))
                .orElse(false);
    }
    @Override public boolean canOpen(Player player) {
        if (!allowed(player)) {
            sendChestLockedNotifications(Vec3.atCenterOf(getBlockPos()), player, getDisplayName());
            return false;
        }
        return super.canOpen(player);
    }
    @Override public boolean stillValid(Player player) { return allowed(player) && super.stillValid(player); }
    @Override protected Component getDefaultName() {
        return binding == null ? Component.translatable("block.millenaire.locked_chest") : Component.literal(binding.name());
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input); binding = input.read("millenaire_building", BuildingBinding.CODEC).orElse(null);
        startingStockInitialized = input.getBooleanOr("millenaire_starting_stock", false);
    }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output); output.storeNullable("millenaire_building", BuildingBinding.CODEC, binding);
        output.putBoolean("millenaire_starting_stock", startingStockInitialized);
    }
    @Override public int[] getSlotsForFace(Direction side) { return new int[0]; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }
}
