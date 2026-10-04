package org.millenaire.goal.impl;

import com.mojang.logging.LogUtils;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Plane;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import org.millenaire.building.BedManager;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.HearthApproach;
import org.millenaire.diagnostics.NavEvent;
import org.millenaire.diagnostics.NavigationCounters;
import org.millenaire.entity.BlockHazards;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerNavigationManager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.ProgressAwareTask;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TaskLabels;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.village.HearthResidentResolver;
import org.millenaire.village.NightActionHelper;
import org.slf4j.Logger;

public class RestGoal implements VillagerGoal {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "rest");
   private static final int DEBT_PRIORITY_DIVISOR = 30;
   private static final int DEBT_PRIORITY_CAP = 200;

   public ResourceLocation id() {
      return ID;
   }

   public int computePriority(GoalContext context) {
      int debt = context.villager().getSleepDebtTicks();
      int boost = Math.min(200, debt / 30);
      return 50 + boost;
   }

   public boolean canStart(GoalContext context) {
      return true;
   }

   public boolean canBeDoneAtNight() {
      return true;
   }

   public boolean canBeDoneInDayTime() {
      return false;
   }

   public VillagerTask start(GoalContext context) {
      return new RestGoal.RestTask();
   }

   static class RestTask extends ProgressAwareTask {
      private static final double ARRIVE_DISTANCE = 3.0;
      private static final double WALK_SPEED = 0.5;
      private static final int MAX_REST_TICKS = 12000;
      private static final int SEARCH_RADIUS = 6;
      private static final float GROUND_FLOATING_HEIGHT = 0.2F;
      private static final int HEARTH_ACTION_DURATION = 20;
      private static final double HEARTH_ARRIVE_DISTANCE = 2.0;
      private boolean arrived;
      private boolean positioned;
      private boolean nightActionPerformed;
      private boolean lastWasDay = false;
      private boolean ticked = false;
      private int tickCount;
      @Nullable
      private BlockPos sleepTarget;
      @Nullable
      private BlockPos sleepBedPos;
      private boolean hearthPhaseDone;
      @Nullable
      private BlockPos hearthTarget;
      @Nullable
      private BlockPos hearthStandPos;
      private boolean hearthArrived;
      private int hearthActionTicks;

      public ResourceLocation goalId() {
         return RestGoal.ID;
      }

      public void tick(GoalContext ctx) {
         this.lastWasDay = ctx.level().isDay();
         this.ticked = true;
         this.tickCount++;
         this.reportProgress();
         if (!this.hearthPhaseDone) {
            this.tickPreRestExtinguishHearth(ctx);
            if (!this.hearthPhaseDone) {
               return;
            }
         }

         if (this.arrived) {
            if (!this.positioned) {
               this.positioned = true;
               this.positionForSleep(ctx);
            }

            if (!ctx.villager().isSleeping() && !ctx.villager().isVillagerSleeping()) {
               this.startSleeping(ctx);
               ctx.villager().getNavigation().stop();
            }

            if (!this.nightActionPerformed) {
               this.nightActionPerformed = true;
               NightActionHelper.perform(ctx);
            }
         } else {
            if (this.sleepTarget == null) {
               this.sleepTarget = this.resolveSleepTarget(ctx);
            }

            if (this.sleepTarget == null) {
               this.arrived = true;
            } else {
               VillagerNavigationManager nav = ctx.villager().getNavManager();
               if (nav.getDestination() == null) {
                  nav.navigateTo(ctx.villager(), this.sleepTarget, 0.5);
               }

               if (nav.isArrived(ctx.villager(), 3.0) || nav.isAbandoned()) {
                  this.arrived = true;
                  nav.stop(ctx.villager());
               }
            }
         }
      }

      private void tickPreRestExtinguishHearth(GoalContext ctx) {
         if (this.hearthTarget == null && !this.hearthArrived) {
            BlockPos target = findLitHearthToExtinguish(ctx);
            if (target == null) {
               this.hearthPhaseDone = true;
               return;
            }

            this.hearthTarget = target;
            this.hearthStandPos = HearthApproach.findStandPosition(ctx.level(), this.hearthTarget);
         }

         if (!this.hearthArrived) {
            VillagerNavigationManager nav = ctx.villager().getNavManager();
            if (nav.getDestination() == null) {
               nav.navigateTo(ctx.villager(), this.hearthStandPos, 0.5);
            }

            if (nav.isAbandoned()) {
               nav.stop(ctx.villager());
               this.hearthPhaseDone = true;
            } else {
               if (nav.isArrivedHorizontal(ctx.villager(), 2.0)) {
                  nav.stop(ctx.villager());
                  this.hearthArrived = true;
                  this.hearthActionTicks = 0;
               }
            }
         } else {
            ctx.villager().swing(InteractionHand.MAIN_HAND);
            this.hearthActionTicks++;
            if (this.hearthActionTicks >= 20) {
               ServerLevel level = ctx.level();
               BlockState current = level.getBlockState(this.hearthTarget);
               if (current.getBlock() instanceof CampfireBlock && current.hasProperty(CampfireBlock.LIT) && (Boolean)current.getValue(CampfireBlock.LIT)) {
                  level.setBlock(this.hearthTarget, (BlockState)current.setValue(CampfireBlock.LIT, false), 3);
                  level.playSound(
                     null, this.hearthTarget, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 0.8F, level.getRandom().nextFloat() * 0.4F + 0.8F
                  );
               }

               this.hearthPhaseDone = true;
            }
         }
      }

      @Nullable
      static BlockPos findLitHearthToExtinguish(GoalContext ctx) {
         BuildingInstance home = ctx.resolveHomeBuilding().orElse(null);
         if (home == null) {
            return null;
         }

         if (home.getHearthPositions().isEmpty()) {
            return null;
         }

         if (!HearthResidentResolver.isDesignatedResident(ctx.village(), home, ctx.villager().getUUID())) {
            return null;
         }

         for (BlockPos pos : home.getHearthPositions()) {
            BlockState state = ctx.level().getBlockState(pos);
            if (state.getBlock() instanceof CampfireBlock && state.hasProperty(CampfireBlock.LIT) && (Boolean)state.getValue(CampfireBlock.LIT)) {
               return pos;
            }
         }

         return null;
      }

      @Nullable
      private BlockPos resolveSleepTarget(GoalContext ctx) {
         BuildingInstance home = ctx.resolveHomeBuilding().orElse(null);
         if (home != null) {
            return home.getSleepingPos();
         }

         BuildingInstance th = ctx.village().getTownhall();
         return th != null ? th.getSleepingPos() : ctx.villager().blockPosition();
      }

      private void positionForSleep(GoalContext ctx) {
         MillVillager villager = ctx.villager();
         if (villager.level() instanceof ServerLevel level) {
            BlockPos searchCenter = this.sleepTarget != null ? this.sleepTarget : villager.blockPosition();
            BlockPos claimedBed = this.findBedViaClaiming(ctx, level);
            if (claimedBed != null) {
               this.sleepBedPos = claimedBed;
            } else {
               BuildingInstance home = ctx.resolveHomeBuilding().orElse(null);
               BedManager bedMgr = home != null && home.hasBedManager() ? home.getBedManager() : null;
               BlockPos legacyBed = this.findBedLegacy(level, searchCenter, bedMgr, level.getGameTime());
               if (legacyBed != null) {
                  this.sleepBedPos = legacyBed;
               } else {
                  BlockPos groundPos = this.findSheltered(level, searchCenter);
                  if (groundPos != null) {
                     this.positionOnSurface(villager, level, groundPos, 1.2F);
                  } else {
                     villager.setPos(villager.getX(), villager.getY() + 0.2F, villager.getZ());
                     nudgeOffHazard(villager, level);
                  }
               }
            }
         }
      }

      private static void nudgeOffHazard(MillVillager villager, ServerLevel level) {
         BlockPos feet = villager.blockPosition();
         if (BlockHazards.isHazardousAt(level, feet)) {
            for (Direction dir : Plane.HORIZONTAL) {
               BlockPos shifted = feet.relative(dir);
               if (!BlockHazards.isHazardousAt(level, shifted)) {
                  BlockState below = level.getBlockState(shifted.below());
                  if (below.isSolidRender(level, shifted.below()) && level.getBlockState(shifted).isAir() && level.getBlockState(shifted.above()).isAir()) {
                     RestGoal.LOGGER
                        .info(
                           "[Millenaire] Nudged {} off hazard at {} → {}",
                           new Object[]{villager.getVillagerDisplayName(), feet.toShortString(), shifted.toShortString()}
                        );
                     villager.setPos(shifted.getX() + 0.5, shifted.getY() + 0.2F, shifted.getZ() + 0.5);
                     return;
                  }
               }
            }

            RestGoal.LOGGER.warn("[Millenaire] {} stuck on hazard at {} with no safe neighbor", villager.getVillagerDisplayName(), feet.toShortString());
         }
      }

      private void startSleeping(GoalContext ctx) {
         MillVillager villager = ctx.villager();
         if (this.sleepBedPos != null && villager.level() instanceof ServerLevel level) {
            BlockPos headPos = this.resolveHeadPos(level, this.sleepBedPos);
            if (!this.wouldSuffocate(level, headPos)) {
               villager.startSleeping(headPos);
               return;
            }

            this.recordBedSuffocation(ctx, villager, this.sleepBedPos);
            BlockPos alt = this.tryOneAlternativeBed(ctx, level, villager, this.sleepBedPos);
            if (alt != null) {
               BlockPos altHeadPos = this.resolveHeadPos(level, alt);
               if (!this.wouldSuffocate(level, altHeadPos)) {
                  this.sleepBedPos = alt;
                  this.positionOnSurface(villager, level, alt, 0.7F);
                  villager.startSleeping(altHeadPos);
                  return;
               }

               this.recordBedSuffocation(ctx, villager, alt);
            }

            villager.setVillagerSleeping(true);
         } else {
            villager.setVillagerSleeping(true);
         }
      }

      private void recordBedSuffocation(GoalContext ctx, MillVillager villager, BlockPos bedPos) {
         RestGoal.LOGGER.info("[Millenaire] BED_SUFFOCATION — {} at bed {} (flagged for 1 MC day)", villager.getVillagerDisplayName(), bedPos.toShortString());
         villager.getNavEventLog().record(villager.level().getGameTime(), NavEvent.Layer.REST, NavEvent.Type.BED_SUFFOCATION, "bed=" + bedPos.toShortString());
         NavigationCounters.incBedSuffocation();
         BuildingInstance home = ctx.resolveHomeBuilding().orElse(null);
         if (home != null && home.hasBedManager()) {
            BedManager bm = home.getBedManager();
            bm.markSuffocating(bedPos, villager.level().getGameTime());
            bm.releaseBed(bedPos);
            ctx.village().markDirty();
         }
      }

      @Nullable
      private BlockPos tryOneAlternativeBed(GoalContext ctx, ServerLevel level, MillVillager villager, BlockPos excluded) {
         BuildingInstance home = ctx.resolveHomeBuilding().orElse(null);
         if (home != null && home.hasBedManager()) {
            BedManager bm = home.getBedManager();
            BlockPos searchCenter = this.sleepTarget != null ? this.sleepTarget : villager.blockPosition();
            Optional<BlockPos> alt = bm.findNearestUnclaimedBed(searchCenter, 6.0, villager.level().getGameTime());
            if (alt.isPresent() && !alt.get().equals(excluded) && bm.claimBed(alt.get(), villager.getUUID())) {
               ctx.village().markDirty();
               return alt.get();
            } else {
               return null;
            }
         } else {
            return null;
         }
      }

      private boolean wouldSuffocate(ServerLevel level, BlockPos pos) {
         return level.getBlockState(pos).isSuffocating(level, pos) || level.getBlockState(pos.above()).isSuffocating(level, pos.above());
      }

      private void positionOnSurface(MillVillager villager, ServerLevel level, BlockPos pos, float yOffset) {
         float angle = this.determineSleepAngle(level, pos);
         double dx = 0.5;
         double dz = 0.5;
         if (angle == 0.0F) {
            dx = 0.95;
         } else if (angle == 90.0F) {
            dz = 0.95;
         } else if (angle == 180.0F) {
            dx = 0.05;
         } else if (angle == 270.0F) {
            dz = 0.05;
         }

         villager.setPos(pos.getX() + dx, pos.getY() + yOffset, pos.getZ() + dz);
         villager.setYRot((angle + 90.0F) % 360.0F);
      }

      private BlockPos resolveHeadPos(ServerLevel level, BlockPos footPos) {
         BlockState state = level.getBlockState(footPos);
         return state.getBlock() instanceof BedBlock
               && state.hasProperty(BedBlock.PART)
               && state.getValue(BedBlock.PART) == BedPart.FOOT
               && state.hasProperty(BedBlock.FACING)
            ? footPos.relative((Direction)state.getValue(BedBlock.FACING))
            : footPos;
      }

      @Nullable
      private BlockPos findBedViaClaiming(GoalContext ctx, ServerLevel level) {
         BuildingInstance home = ctx.resolveHomeBuilding().orElse(null);
         if (home != null && home.hasBedManager()) {
            BedManager bedManager = home.getBedManager();
            UUID villagerUuid = ctx.villager().getUUID();
            Optional<BlockPos> existing = bedManager.getClaimedBed(villagerUuid);
            if (existing.isPresent()) {
               BlockPos pos = existing.get();
               BlockState state = level.getBlockState(pos);
               if (state.getBlock() instanceof BedBlock) {
                  return pos;
               }

               bedManager.releaseBedByVillager(villagerUuid);
               ctx.village().markDirty();
            }

            BlockPos searchCenter = this.sleepTarget != null ? this.sleepTarget : ctx.villager().blockPosition();
            Optional<BlockPos> unclaimed = bedManager.findNearestUnclaimedBed(searchCenter, 6.0, level.getGameTime());
            if (unclaimed.isPresent()) {
               BlockPos pos = unclaimed.get();
               if (bedManager.claimBed(pos, villagerUuid)) {
                  ctx.village().markDirty();
                  return pos;
               }
            }

            return null;
         } else {
            return null;
         }
      }

      @Nullable
      private BlockPos findBedLegacy(ServerLevel level, BlockPos center, @Nullable BedManager bedManager, long gameTime) {
         MutableBlockPos mutable = new MutableBlockPos();

         for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -6; dy <= 6; dy++) {
               for (int dz = -6; dz <= 6; dz++) {
                  mutable.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                  BlockState state = level.getBlockState(mutable);
                  if (state.getBlock() instanceof BedBlock
                     && (!state.hasProperty(BedBlock.PART) || state.getValue(BedBlock.PART) != BedPart.HEAD)
                     && !this.isBedOccupied(level, mutable)
                     && (bedManager == null || !bedManager.isSuffocatingMarked(mutable.immutable(), gameTime))) {
                     return mutable.immutable();
                  }
               }
            }
         }

         return null;
      }

      @Nullable
      private BlockPos findSheltered(ServerLevel level, BlockPos center) {
         MutableBlockPos mutable = new MutableBlockPos();

         for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -6; dy <= 6; dy++) {
               for (int dz = -6; dz <= 6; dz++) {
                  mutable.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                  if (level.getBlockState(mutable).isSolidRender(level, mutable)
                     && level.getBlockState(mutable.above()).isAir()
                     && level.getBlockState(mutable.above(2)).isAir()) {
                     boolean hasRoof = false;
                     int maxY = level.getMaxBuildHeight();

                     for (int checkY = mutable.getY() + 3; checkY <= maxY; checkY++) {
                        if (!level.getBlockState(mutable.atY(checkY)).isAir()) {
                           hasRoof = true;
                           break;
                        }
                     }

                     if (hasRoof) {
                        mutable.setY(center.getY() + dy);
                        return mutable.immutable();
                     }
                  }
               }
            }
         }

         return null;
      }

      private float determineSleepAngle(ServerLevel level, BlockPos pos) {
         BlockState state = level.getBlockState(pos);
         if (state.getBlock() instanceof BedBlock && state.hasProperty(BedBlock.FACING)) {
            return switch ((Direction)state.getValue(BedBlock.FACING)) {
               case SOUTH -> 0.0F;
               case WEST -> 90.0F;
               case NORTH -> 180.0F;
               case EAST -> 270.0F;
               default -> 0.0F;
            };
         } else {
            BlockPos above = pos.above();
            if (level.getBlockState(above.south()).isAir()) {
               return 0.0F;
            } else if (level.getBlockState(above.west()).isAir()) {
               return 90.0F;
            } else if (level.getBlockState(above.north()).isAir()) {
               return 180.0F;
            } else {
               return level.getBlockState(above.east()).isAir() ? 270.0F : 0.0F;
            }
         }
      }

      private boolean isBedOccupied(ServerLevel level, BlockPos bedPos) {
         AABB box = new AABB(bedPos).inflate(0.5);

         for (MillVillager mv : level.getEntitiesOfClass(MillVillager.class, box)) {
            if (mv.isSleeping() || mv.isVillagerSleeping()) {
               return true;
            }
         }

         return false;
      }

      public boolean isFinished() {
         return !this.ticked ? false : this.lastWasDay || this.tickCount >= 12000;
      }

      public void stop(GoalContext ctx, StopReason reason) {
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
            if (ctx.villager().isSleeping()) {
               ctx.villager().stopSleeping();
            }

            ctx.villager().setVillagerSleeping(false);
         }
      }

      public TravelPhase getTravelPhase() {
         return TaskLabels.phaseFor(this.arrived);
      }

      @Nullable
      public Component getGoalLabel() {
         return TaskLabels.labelForPhase(this.arrived, "rest");
      }
   }
}
