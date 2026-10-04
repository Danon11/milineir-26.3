package org.millenaire.entity;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.millenaire.item.ModItems;
import org.slf4j.Logger;

public class MillWallDecoration extends HangingEntity {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(MillWallDecoration.class, EntityDataSerializers.INT);
   private WallDecorationVariant variant = WallDecorationVariant.Griffon;

   public MillWallDecoration(EntityType<? extends MillWallDecoration> type, Level level) {
      super(type, level);
   }

   public MillWallDecoration(Level level, BlockPos pos, Direction direction, WallDecorationVariant variant) {
      super((EntityType)ModEntities.WALL_DECORATION.get(), level, pos);
      this.variant = variant;
      this.entityData.set(DATA_VARIANT, variant.ordinal());
      this.setDirection(direction);
   }

   @Nullable
   public static MillWallDecoration createForBuilding(Level level, BlockPos pos, WallDecorationType type) {
      Direction facing = guessOrientation(level, pos);
      int maxWidth = measureWallWidth(level, pos, facing);
      int maxHeight = measureWallHeight(level, pos);
      WallDecorationVariant selected = WallDecorationVariant.selectRandom(type, maxWidth, maxHeight, true, level.getRandom());
      if (selected == null) {
         LOGGER.debug("No variant {} fits on the wall at {} (max {}x{})", new Object[]{type.typeName(), pos.toShortString(), maxWidth, maxHeight});
         return null;
      } else {
         MillWallDecoration decoration = new MillWallDecoration(level, pos, facing, selected);
         if (decoration.survives()) {
            LOGGER.debug("Decoration {} ({}) created at {}/{}", new Object[]{selected.title(), type.typeName(), pos.toShortString(), facing});
            return decoration;
         } else {
            LOGGER.debug("Decoration {} does not survive at {}", selected.title(), pos.toShortString());
            return null;
         }
      }
   }

   @Nullable
   public static MillWallDecoration createForPlayer(Level level, BlockPos pos, Direction facing, WallDecorationType type) {
      int maxWidth = measureWallWidth(level, pos, facing);
      int maxHeight = measureWallHeight(level, pos);
      WallDecorationVariant selected = WallDecorationVariant.selectRandom(type, maxWidth, maxHeight, false, level.getRandom());
      if (selected == null) {
         return null;
      }

      MillWallDecoration decoration = new MillWallDecoration(level, pos, facing, selected);
      return decoration.survives() ? decoration : null;
   }

   private static Direction guessOrientation(Level level, BlockPos pos) {
      if (isSolidWall(level, pos.north())) {
         return Direction.SOUTH;
      } else if (isSolidWall(level, pos.south())) {
         return Direction.NORTH;
      } else if (isSolidWall(level, pos.east())) {
         return Direction.WEST;
      } else {
         return isSolidWall(level, pos.west()) ? Direction.EAST : Direction.WEST;
      }
   }

   private static boolean isSolidWall(Level level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return state.isSolidRender(level, pos);
   }

   private static int measureWallWidth(Level level, BlockPos pos, Direction facing) {
      Direction right = facing.getClockWise();
      int width = 1;

      for (int i = 1; i <= 16; i++) {
         BlockPos check = pos.relative(right, i);
         BlockPos behind = check.relative(facing.getOpposite());
         if (!isSolidWall(level, behind) || !level.getBlockState(check).isAir()) {
            break;
         }

         width++;
      }

      for (int i = 1; i <= 16; i++) {
         BlockPos check = pos.relative(right, -i);
         BlockPos behind = check.relative(facing.getOpposite());
         if (!isSolidWall(level, behind) || !level.getBlockState(check).isAir()) {
            break;
         }

         width++;
      }

      return width;
   }

   private static int measureWallHeight(Level level, BlockPos pos) {
      int height = 1;

      for (int i = 1; i <= 4 && level.getBlockState(pos.above(i)).isAir(); i++) {
         height++;
      }

      for (int i = 1; i <= 4 && level.getBlockState(pos.below(i)).isAir(); i++) {
         height++;
      }

      return height;
   }

   protected AABB calculateBoundingBox(BlockPos pos, Direction direction) {
      return computeBoundingBox(pos, direction, this.variant.widthBlocks(), this.variant.heightBlocks());
   }

   static AABB computeBoundingBox(BlockPos pos, Direction direction, int width, int height) {
      double depth = 0.0625;
      double halfWidth = width / 2.0;
      double halfHeight = height / 2.0;
      double cx = pos.getX() + 0.5;
      double cy = pos.getY() + 0.5;
      double cz = pos.getZ() + 0.5;
      cx -= direction.getStepX() * (0.5 - depth / 2.0);
      cz -= direction.getStepZ() * (0.5 - depth / 2.0);
      Direction perpendicular = direction.getCounterClockWise();
      if (width % 2 == 0) {
         cx += perpendicular.getStepX() * 0.5;
         cz += perpendicular.getStepZ() * 0.5;
      }

      if (height % 2 == 0) {
         cy += 0.5;
      }

      return direction.getAxis() == Axis.Z
         ? new AABB(cx - halfWidth, cy - halfHeight, cz - depth / 2.0, cx + halfWidth, cy + halfHeight, cz + depth / 2.0)
         : new AABB(cx - depth / 2.0, cy - halfHeight, cz - halfWidth, cx + depth / 2.0, cy + halfHeight, cz + halfWidth);
   }

   public void dropItem(@Nullable Entity breaker) {
      if (this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
         this.playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
         if (!(breaker instanceof Player player && player.getAbilities().instabuild)) {
            ItemStack drop = this.getDropStack();
            if (!drop.isEmpty()) {
               this.spawnAtLocation(drop);
            }
         }
      }
   }

   private ItemStack getDropStack() {
      return switch (this.variant.type()) {
         case NORMAN_TAPESTRY -> new ItemStack((ItemLike)ModItems.TAPESTRY.get());
         case INDIAN_STATUE -> new ItemStack((ItemLike)ModItems.INDIAN_STATUE.get());
         case MAYAN_STATUE -> new ItemStack((ItemLike)ModItems.MAYAN_STATUE.get());
         case BYZANTINE_ICON_SMALL -> new ItemStack((ItemLike)ModItems.BYZANTINE_ICON_SMALL.get());
         case BYZANTINE_ICON_MEDIUM -> new ItemStack((ItemLike)ModItems.BYZANTINE_ICON_MEDIUM.get());
         case BYZANTINE_ICON_LARGE -> new ItemStack((ItemLike)ModItems.BYZANTINE_ICON_LARGE.get());
         case HIDE_HANGING -> new ItemStack((ItemLike)ModItems.HIDE_HANGING.get());
         case WALL_CARPET_SMALL -> new ItemStack((ItemLike)ModItems.WALL_CARPET_SMALL.get());
         case WALL_CARPET_MEDIUM -> new ItemStack((ItemLike)ModItems.WALL_CARPET_MEDIUM.get());
         case WALL_CARPET_LARGE -> new ItemStack((ItemLike)ModItems.WALL_CARPET_LARGE.get());
      };
   }

   public void playPlacementSound() {
      this.playSound(SoundEvents.PAINTING_PLACE, 1.0F, 1.0F);
   }

   public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
      return new ClientboundAddEntityPacket(this, this.getDirection().get3DDataValue(), this.getPos());
   }

   public void recreateFromPacket(ClientboundAddEntityPacket packet) {
      super.recreateFromPacket(packet);
      this.setDirection(Direction.from3DDataValue(packet.getData()));
   }

   protected void defineSynchedData(Builder builder) {
      builder.define(DATA_VARIANT, WallDecorationVariant.Griffon.ordinal());
   }

   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (DATA_VARIANT.equals(key)) {
         int ordinal = (Integer)this.entityData.get(DATA_VARIANT);
         WallDecorationVariant[] variants = WallDecorationVariant.values();
         if (ordinal >= 0 && ordinal < variants.length) {
            this.variant = variants[ordinal];
            if (this.getDirection() != null) {
               this.setDirection(this.getDirection());
            }
         }
      }
   }

   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putInt("Type", this.variant.type().legacyId());
      tag.putString("Motive", this.variant.title());
   }

   public void readAdditionalSaveData(CompoundTag tag) {
      String motive = tag.getString("Motive");
      WallDecorationVariant loaded = WallDecorationVariant.fromTitle(motive);
      if (loaded != null) {
         this.variant = loaded;
      } else {
         int typeId = tag.getInt("Type");
         WallDecorationType type = WallDecorationType.fromLegacyId(typeId);
         if (type != null) {
            for (WallDecorationVariant v : WallDecorationVariant.values()) {
               if (v.type() == type) {
                  this.variant = v;
                  break;
               }
            }
         }

         LOGGER.warn("Unknown wall variant '{}', fallback to {}", motive, this.variant.title());
      }

      this.entityData.set(DATA_VARIANT, this.variant.ordinal());
      super.readAdditionalSaveData(tag);
   }

   public WallDecorationVariant getVariant() {
      return this.variant;
   }

   public WallDecorationType getDecorationType() {
      return this.variant.type();
   }

   public String toString() {
      return "WallDecoration (" + this.variant.title() + ") " + super.toString();
   }
}
