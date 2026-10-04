package org.millenaire.goal.impl;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.HearthApproach;
import org.millenaire.culture.ModCultures;
import org.millenaire.entity.VillagerNavigationManager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.ProgressAwareTask;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.village.HearthResidentResolver;

public class LightHearthGoal implements VillagerGoal {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "light_hearth");
   private static final int PRIORITY = 2000;
   static final long WINDOW_START = 1000L;
   static final long WINDOW_END = 3000L;
   private static final double ARRIVE_DISTANCE = 2.0;
   private static final double WALK_SPEED = 0.5;
   private static final int ACTION_DURATION = 20;
   private static final int MAX_OCCUPANT_RETRIES = 100;

   public ResourceLocation id() {
      return ID;
   }

   public int computePriority(GoalContext context) {
      if (!isMorningWindow(context.dayTime())) {
         return 0;
      } else if (!isDesignatedResidentOfHearthHome(context)) {
         return 0;
      } else {
         return findUnlitHearth(context) == null ? 0 : 2000;
      }
   }

   public boolean canStart(GoalContext context) {
      return this.computePriority(context) > 0;
   }

   public VillagerTask start(GoalContext context) {
      return new LightHearthGoal.LightHearthTask();
   }

   public boolean canBeDoneInDayTime() {
      return true;
   }

   public boolean canBeDoneAtNight() {
      return false;
   }

   static boolean isMorningWindow(long dayTime) {
      long normalized = dayTime % 24000L;
      if (normalized < 0L) {
         normalized += 24000L;
      }

      return normalized >= 1000L && normalized <= 3000L;
   }

   static boolean isDesignatedResidentOfHearthHome(GoalContext ctx) {
      BuildingInstance home = ctx.resolveHomeBuilding().orElse(null);
      if (home == null) {
         return false;
      } else {
         return home.getHearthPositions().isEmpty() ? false : HearthResidentResolver.isDesignatedResident(ctx.village(), home, ctx.villager().getUUID());
      }
   }

   @Nullable
   static BlockPos findUnlitHearth(GoalContext ctx) {
      BuildingInstance home = ctx.resolveHomeBuilding().orElse(null);
      if (home == null) {
         return null;
      }

      for (BlockPos pos : home.getHearthPositions()) {
         BlockState state = ctx.level().getBlockState(pos);
         if (state.getBlock() instanceof CampfireBlock && state.hasProperty(CampfireBlock.LIT) && !(Boolean)state.getValue(CampfireBlock.LIT)) {
            return pos;
         }
      }

      return null;
   }

   static class LightHearthTask extends ProgressAwareTask {
      private LightHearthGoal.LightHearthTask.State state = LightHearthGoal.LightHearthTask.State.WALKING;
      @Nullable
      private BlockPos target;
      @Nullable
      private BlockPos standPos;
      private int actionTicks;
      private int occupantRetries;

      public ResourceLocation goalId() {
         return LightHearthGoal.ID;
      }

      public void tick(GoalContext ctx) {
         switch (this.state) {
            case WALKING:
               this.tickWalking(ctx);
               break;
            case ACTING:
               this.tickActing(ctx);
            case DONE:
         }
      }

      private void tickWalking(GoalContext ctx) {
         if (this.target == null) {
            this.target = LightHearthGoal.findUnlitHearth(ctx);
            if (this.target == null) {
               this.state = LightHearthGoal.LightHearthTask.State.DONE;
               return;
            }

            this.standPos = HearthApproach.findStandPosition(ctx.level(), this.target);
         }

         VillagerNavigationManager nav = ctx.villager().getNavManager();
         if (nav.getDestination() == null) {
            nav.navigateTo(ctx.villager(), this.standPos, 0.5);
         }

         if (nav.isAbandoned()) {
            this.state = LightHearthGoal.LightHearthTask.State.DONE;
            nav.stop(ctx.villager());
         } else {
            if (nav.isArrivedHorizontal(ctx.villager(), 2.0)) {
               nav.stop(ctx.villager());
               this.state = LightHearthGoal.LightHearthTask.State.ACTING;
               this.actionTicks = 0;
            }
         }
      }

      private void tickActing(GoalContext ctx) {
         ctx.villager().swing(InteractionHand.MAIN_HAND);
         this.actionTicks++;
         if (this.actionTicks >= 20) {
            if (this.target == null) {
               this.state = LightHearthGoal.LightHearthTask.State.DONE;
            } else {
               ServerLevel level = ctx.level();
               if (hasOtherOccupantOnHearth(level, this.target, ctx.villager())) {
                  this.occupantRetries++;
                  if (this.occupantRetries >= 100) {
                     this.state = LightHearthGoal.LightHearthTask.State.DONE;
                  } else {
                     this.actionTicks = 19;
                  }
               } else {
                  BlockState current = level.getBlockState(this.target);
                  if (current.getBlock() instanceof CampfireBlock && current.hasProperty(CampfireBlock.LIT) && !(Boolean)current.getValue(CampfireBlock.LIT)) {
                     BlockState lit = (BlockState)current.setValue(CampfireBlock.LIT, true);
                     if (isTownHallHearth(ctx) && lit.hasProperty(CampfireBlock.SIGNAL_FIRE)) {
                        lit = (BlockState)lit.setValue(CampfireBlock.SIGNAL_FIRE, true);
                     }

                     level.setBlock(this.target, lit, 3);
                     level.playSound(null, this.target, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.4F + 0.8F);
                  }

                  this.reportProgress();
                  this.state = LightHearthGoal.LightHearthTask.State.DONE;
               }
            }
         }
      }

      static boolean hasOtherOccupantOnHearth(ServerLevel level, BlockPos hearth, LivingEntity actor) {
         AABB zone = new AABB(hearth.getX(), hearth.getY(), hearth.getZ(), hearth.getX() + 1, hearth.getY() + 2, hearth.getZ() + 1);

         for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, zone)) {
            if (e != actor) {
               return true;
            }
         }

         return false;
      }

      private static boolean isTownHallHearth(GoalContext ctx) {
         BuildingInstance home = ctx.resolveHomeBuilding().orElse(null);
         if (home != null && home.getPlanSetId() != null) {
            BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(home.getPlanSetId());
            return planSet != null && planSet.isTownHall();
         } else {
            return false;
         }
      }

      public boolean isFinished() {
         return this.state == LightHearthGoal.LightHearthTask.State.DONE;
      }

      public void stop(GoalContext ctx, StopReason reason) {
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
         }
      }

      private enum State {
         WALKING,
         ACTING,
         DONE;
      }
   }
}
