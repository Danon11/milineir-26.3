package org.millenaire.block;

import java.util.Arrays;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class FirePitBlockEntity extends BlockEntity implements MenuProvider, Container {
   public static final int SLOT_COUNT = 7;
   public static final int INPUT_START = 0;
   public static final int INPUT_END = 3;
   public static final int FUEL_SLOT = 3;
   public static final int OUTPUT_START = 4;
   public static final int OUTPUT_END = 7;
   public static final int COOK_TIME_TOTAL = 200;
   private final NonNullList<ItemStack> items = NonNullList.withSize(7, ItemStack.EMPTY);
   private int[] cookTimes = new int[3];
   private int burnTime = 0;
   private int totalBurnTime = 0;
   private float[] pendingXp = new float[3];
   private int[] itemsSmelted = new int[3];
   private final ContainerData dataAccess = new ContainerData() {
      public int get(int index) {
         return switch (index) {
            case 0 -> FirePitBlockEntity.this.cookTimes[0];
            case 1 -> FirePitBlockEntity.this.cookTimes[1];
            case 2 -> FirePitBlockEntity.this.cookTimes[2];
            case 3 -> FirePitBlockEntity.this.burnTime;
            case 4 -> FirePitBlockEntity.this.totalBurnTime;
            default -> 0;
         };
      }

      public void set(int index, int value) {
         switch (index) {
            case 0:
               FirePitBlockEntity.this.cookTimes[0] = value;
               break;
            case 1:
               FirePitBlockEntity.this.cookTimes[1] = value;
               break;
            case 2:
               FirePitBlockEntity.this.cookTimes[2] = value;
               break;
            case 3:
               FirePitBlockEntity.this.burnTime = value;
               break;
            case 4:
               FirePitBlockEntity.this.totalBurnTime = value;
         }
      }

      public int getCount() {
         return 5;
      }
   };

   public FirePitBlockEntity(BlockPos pos, BlockState state) {
      super((BlockEntityType)ModBlockEntities.FIRE_PIT.get(), pos, state);
   }

   public static boolean isFirePitBurnable(ItemStack stack) {
      return stack.isEmpty() ? false : stack.getFoodProperties(null) != null;
   }

   public static boolean isFirePitBurnable(ItemStack stack, Level level) {
      if (stack.isEmpty()) {
         return false;
      }

      boolean isFood = stack.getFoodProperties(null) != null;
      Optional<RecipeHolder<SmeltingRecipe>> recipeHolder = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level);
      if (recipeHolder.isEmpty()) {
         return false;
      }

      ItemStack result = ((SmeltingRecipe)recipeHolder.get().value()).getResultItem(level.registryAccess());
      if (result.isEmpty()) {
         return false;
      }

      boolean resultIsFood = result.getFoodProperties(null) != null;
      return isFood || resultIsFood;
   }

   public int getContainerSize() {
      return 7;
   }

   public boolean isEmpty() {
      for (ItemStack stack : this.items) {
         if (!stack.isEmpty()) {
            return false;
         }
      }

      return true;
   }

   public ItemStack getItem(int slot) {
      return (ItemStack)this.items.get(slot);
   }

   public ItemStack removeItem(int slot, int amount) {
      ItemStack result = ContainerHelper.removeItem(this.items, slot, amount);
      if (!result.isEmpty()) {
         this.setChanged();
      }

      return result;
   }

   public ItemStack removeItemNoUpdate(int slot) {
      return ContainerHelper.takeItem(this.items, slot);
   }

   public void setItem(int slot, ItemStack stack) {
      if (slot >= 4 && slot < 7 && stack.isEmpty()) {
         int lane = slot - 4;
         this.pendingXp[lane] = 0.0F;
         this.itemsSmelted[lane] = 0;
      }

      this.items.set(slot, stack);
      if (!stack.isEmpty() && stack.getCount() > stack.getMaxStackSize()) {
         stack.setCount(stack.getMaxStackSize());
      }

      this.setChanged();
   }

   public boolean stillValid(Player player) {
      return true;
   }

   public void clearContent() {
      this.items.clear();
   }

   public boolean canPlaceItem(int slot, ItemStack stack) {
      if (slot >= 4 && slot < 7) {
         return false;
      } else if (slot == 3) {
         return AbstractFurnaceBlockEntity.isFuel(stack);
      } else if (slot >= 0 && slot < 3) {
         Level level = this.getLevel();
         return level != null ? isFirePitBurnable(stack, level) : isFirePitBurnable(stack);
      } else {
         return false;
      }
   }

   public NonNullList<ItemStack> getItems() {
      return this.items;
   }

   public ItemStack getInputItem(int lane) {
      return (ItemStack)this.items.get(0 + lane);
   }

   public ItemStack getFuelItem() {
      return (ItemStack)this.items.get(3);
   }

   public ItemStack getOutputItem(int lane) {
      return (ItemStack)this.items.get(4 + lane);
   }

   public int getBurnTime() {
      return this.burnTime;
   }

   public int getTotalBurnTime() {
      return this.totalBurnTime;
   }

   public int getCookTime(int lane) {
      return this.cookTimes[lane];
   }

   public ContainerData getDataAccess() {
      return this.dataAccess;
   }

   public float claimPendingXp(int lane, int itemsTaken) {
      if (this.itemsSmelted[lane] > 0 && !(this.pendingXp[lane] <= 0.0F)) {
         int taken = Math.min(itemsTaken, this.itemsSmelted[lane]);
         float fraction = (float)taken / this.itemsSmelted[lane];
         float xp = this.pendingXp[lane] * fraction;
         this.pendingXp[lane] = this.pendingXp[lane] - xp;
         this.itemsSmelted[lane] = this.itemsSmelted[lane] - taken;
         if (this.itemsSmelted[lane] <= 0) {
            this.pendingXp[lane] = 0.0F;
            this.itemsSmelted[lane] = 0;
         }

         return xp;
      } else {
         return 0.0F;
      }
   }

   public void discardPendingXp(int lane, int itemsRemoved) {
      if (this.itemsSmelted[lane] > 0) {
         int removed = Math.min(itemsRemoved, this.itemsSmelted[lane]);
         float fraction = (float)removed / this.itemsSmelted[lane];
         this.pendingXp[lane] = this.pendingXp[lane] - this.pendingXp[lane] * fraction;
         this.itemsSmelted[lane] = this.itemsSmelted[lane] - removed;
         if (this.itemsSmelted[lane] <= 0) {
            this.pendingXp[lane] = 0.0F;
            this.itemsSmelted[lane] = 0;
         }
      }
   }

   public static void serverTick(Level level, BlockPos pos, BlockState state, FirePitBlockEntity blockEntity) {
      boolean wasBurning = blockEntity.burnTime > 0;
      boolean dirty = false;
      if (wasBurning) {
         blockEntity.burnTime--;
      }

      ItemStack fuelStack = blockEntity.getFuelItem();

      for (int i = 0; i < 3; i++) {
         ItemStack inputStack = blockEntity.getInputItem(i);
         if ((blockEntity.burnTime > 0 || !fuelStack.isEmpty()) && !inputStack.isEmpty()) {
            if (blockEntity.burnTime <= 0 && blockEntity.canSmelt(i)) {
               blockEntity.burnTime = blockEntity.getFuelBurnTime(fuelStack);
               blockEntity.totalBurnTime = blockEntity.burnTime;
               if (blockEntity.burnTime > 0) {
                  dirty = true;
                  if (!fuelStack.isEmpty()) {
                     fuelStack.shrink(1);
                     if (fuelStack.isEmpty()) {
                        ItemStack containerItem = fuelStack.getCraftingRemainingItem();
                        blockEntity.setItem(3, containerItem);
                        fuelStack = blockEntity.getFuelItem();
                     }
                  }
               }
            }

            if (blockEntity.burnTime > 0 && blockEntity.canSmelt(i)) {
               blockEntity.cookTimes[i]++;
               if (blockEntity.cookTimes[i] == 200) {
                  blockEntity.cookTimes[i] = 0;
                  blockEntity.smeltItem(i);
                  dirty = true;
               }
            } else {
               blockEntity.cookTimes[i] = 0;
            }
         } else if (blockEntity.burnTime <= 0 && blockEntity.cookTimes[i] > 0) {
            dirty = true;
            blockEntity.cookTimes[i] = Mth.clamp(blockEntity.cookTimes[i] - 2, 0, 200);
         }
      }

      if (wasBurning != blockEntity.burnTime > 0) {
         dirty = true;
         BlockState currentState = level.getBlockState(pos);
         if (currentState.getBlock() instanceof FirePitBlock) {
            level.setBlock(pos, (BlockState)currentState.setValue(FirePitBlock.LIT, blockEntity.burnTime > 0), 3);
         }
      }

      if (dirty) {
         blockEntity.setChanged();
      }
   }

   private boolean canSmelt(int lane) {
      ItemStack input = this.getInputItem(lane);
      if (input.isEmpty()) {
         return false;
      }

      Level level = this.getLevel();
      if (level == null) {
         return false;
      }

      Optional<RecipeHolder<SmeltingRecipe>> recipeHolder = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), level);
      if (recipeHolder.isEmpty()) {
         return false;
      }

      ItemStack result = ((SmeltingRecipe)recipeHolder.get().value()).getResultItem(level.registryAccess());
      if (result.isEmpty()) {
         return false;
      }

      ItemStack output = this.getOutputItem(lane);
      return output.isEmpty() || ItemStack.isSameItemSameComponents(result, output) && output.getCount() + result.getCount() <= result.getMaxStackSize();
   }

   private void smeltItem(int lane) {
      if (this.canSmelt(lane)) {
         ItemStack input = this.getInputItem(lane);
         Level level = this.getLevel();
         if (level != null) {
            Optional<RecipeHolder<SmeltingRecipe>> recipeHolder = level.getRecipeManager()
               .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), level);
            if (!recipeHolder.isEmpty()) {
               SmeltingRecipe recipe = (SmeltingRecipe)recipeHolder.get().value();
               ItemStack result = recipe.getResultItem(level.registryAccess());
               ItemStack output = this.getOutputItem(lane);
               if (output.isEmpty()) {
                  this.setItem(4 + lane, result.copy());
               } else {
                  output.grow(result.getCount());
               }

               input.shrink(1);
               this.pendingXp[lane] = this.pendingXp[lane] + recipe.getExperience();
               this.itemsSmelted[lane] = this.itemsSmelted[lane] + result.getCount();
            }
         }
      }
   }

   private int getFuelBurnTime(ItemStack stack) {
      return stack.isEmpty() ? 0 : stack.getBurnTime(RecipeType.SMELTING);
   }

   public void dropAllItems() {
      Level level = this.getLevel();
      if (level != null) {
         BlockPos pos = this.getBlockPos();

         for (int i = 0; i < this.items.size(); i++) {
            ItemStack stack = (ItemStack)this.items.get(i);
            if (!stack.isEmpty()) {
               Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
               this.items.set(i, ItemStack.EMPTY);
            }
         }
      }
   }

   protected void saveAdditional(CompoundTag tag, Provider registries) {
      super.saveAdditional(tag, registries);
      ContainerHelper.saveAllItems(tag, this.items, registries);
      tag.putInt("BurnTime", this.burnTime);
      tag.putIntArray("CookTime", this.cookTimes);
      tag.putInt("TotalBurnTime", this.totalBurnTime);
      tag.putFloat("PendingXp0", this.pendingXp[0]);
      tag.putFloat("PendingXp1", this.pendingXp[1]);
      tag.putFloat("PendingXp2", this.pendingXp[2]);
      tag.putInt("ItemsSmelted0", this.itemsSmelted[0]);
      tag.putInt("ItemsSmelted1", this.itemsSmelted[1]);
      tag.putInt("ItemsSmelted2", this.itemsSmelted[2]);
   }

   protected void loadAdditional(CompoundTag tag, Provider registries) {
      super.loadAdditional(tag, registries);
      ContainerHelper.loadAllItems(tag, this.items, registries);
      this.burnTime = tag.getInt("BurnTime");
      this.cookTimes = Arrays.copyOf(tag.getIntArray("CookTime"), 3);
      this.totalBurnTime = tag.getInt("TotalBurnTime");
      this.pendingXp[0] = tag.getFloat("PendingXp0");
      this.pendingXp[1] = tag.getFloat("PendingXp1");
      this.pendingXp[2] = tag.getFloat("PendingXp2");
      this.itemsSmelted[0] = tag.getInt("ItemsSmelted0");
      this.itemsSmelted[1] = tag.getInt("ItemsSmelted1");
      this.itemsSmelted[2] = tag.getInt("ItemsSmelted2");
   }

   @Nullable
   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }

   public CompoundTag getUpdateTag(Provider registries) {
      CompoundTag tag = new CompoundTag();
      this.saveAdditional(tag, registries);
      return tag;
   }

   public Component getDisplayName() {
      return Component.translatable("block.millenaire.fire_pit");
   }

   @Nullable
   public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
      return new FirePitMenu(containerId, playerInventory, this, this.dataAccess);
   }
}
