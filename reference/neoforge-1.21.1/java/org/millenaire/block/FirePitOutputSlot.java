package org.millenaire.block;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class FirePitOutputSlot extends Slot {
   private final Player player;
   private int removeCount;

   public FirePitOutputSlot(Player player, Container container, int slot, int x, int y) {
      super(container, slot, x, y);
      this.player = player;
   }

   public boolean mayPlace(ItemStack stack) {
      return false;
   }

   public ItemStack remove(int amount) {
      if (this.hasItem()) {
         this.removeCount = this.removeCount + Math.min(amount, this.getItem().getCount());
      }

      return super.remove(amount);
   }

   protected void onQuickCraft(ItemStack stack, int amount) {
      this.removeCount += amount;
      this.checkTakeAchievements(stack);
   }

   protected void checkTakeAchievements(ItemStack stack) {
      stack.onCraftedBy(this.player.level(), this.player, this.removeCount);
      if (this.player instanceof ServerPlayer serverPlayer) {
         ServerLevel level = serverPlayer.serverLevel();
         float experience = 0.0F;
         if (this.container instanceof FirePitBlockEntity blockEntity) {
            int lane = this.getContainerSlot() - 4;
            experience = blockEntity.claimPendingXp(lane, this.removeCount);
         }

         int xpAmount = Mth.floor(experience);
         float fractional = experience - xpAmount;
         if (fractional > 0.0F && Math.random() < fractional) {
            xpAmount++;
         }

         if (xpAmount > 0) {
            ExperienceOrb.award(level, serverPlayer.position().add(0.0, 0.5, 0.0), xpAmount);
         }
      }

      this.removeCount = 0;
   }

   public void onTake(Player player, ItemStack stack) {
      this.checkTakeAchievements(stack);
      super.onTake(player, stack);
   }
}
