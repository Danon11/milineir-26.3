package org.millenaire.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import org.millenaire.village.PlayerCultureReputation;

public final class InuitHuntingDropHandler {
   private InuitHuntingDropHandler() {
   }

   public static void onLivingDrops(LivingDropsEvent event) {
      LivingEntity entity = event.getEntity();
      if (entity instanceof Squid || entity instanceof Guardian) {
         handleSeafoodDrop(event);
      } else if (entity instanceof Wolf) {
         handleWolfMeatDrop(event);
      } else if (entity instanceof PolarBear) {
         int quantity = 1 + entity.level().random.nextInt(2);
         addDrop(event, new ItemStack((ItemLike)ModItems.BEARMEAT_RAW.get(), quantity));
      }
   }

   private static void handleSeafoodDrop(LivingDropsEvent event) {
      LivingEntity entity = event.getEntity();
      DamageSource source = event.getSource();
      if (source.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel serverLevel) {
         PlayerCultureReputation rep = PlayerCultureReputation.get(serverLevel);
         if (!rep.hasLearnedHuntingDrop(player.getUUID(), "seafood_raw")) {
            return;
         }

         int quantity = 0;
         if (entity instanceof ElderGuardian) {
            quantity = 5 + entity.level().random.nextInt(5);
         } else if (entity instanceof Guardian) {
            quantity = 2 + entity.level().random.nextInt(2);
         } else if (entity instanceof Squid && entity.level().random.nextInt(10) == 0) {
            quantity = 1;
         }

         if (quantity > 0) {
            addDrop(event, new ItemStack((ItemLike)ModItems.SEAFOOD_RAW.get(), quantity));
         }
      }
   }

   private static void handleWolfMeatDrop(LivingDropsEvent event) {
      LivingEntity entity = event.getEntity();
      DamageSource source = event.getSource();
      if (source.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel serverLevel) {
         PlayerCultureReputation rep = PlayerCultureReputation.get(serverLevel);
         if (!rep.hasLearnedHuntingDrop(player.getUUID(), "wolfmeat_raw")) {
            return;
         }

         int quantity = entity.level().random.nextInt(3);
         if (quantity > 0) {
            addDrop(event, new ItemStack((ItemLike)ModItems.WOLFMEAT_RAW.get(), quantity));
         }
      }
   }

   private static void addDrop(LivingDropsEvent event, ItemStack stack) {
      LivingEntity entity = event.getEntity();
      event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), stack));
   }
}
