package org.millenaire.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.CommonHooks;
import org.millenaire.item.ModItems;
import org.millenaire.village.PlayerCultureReputation;

public class RicePaddyBlock extends Block implements SimpleWaterloggedBlock, BonemealableBlock {
   public static final IntegerProperty AGE = BlockStateProperties.AGE_7;
   public static final BooleanProperty PLANTED = BooleanProperty.create("planted");
   public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
   private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 7.0, 16.0);

   public RicePaddyBlock(Properties properties) {
      super(properties);
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(PLANTED, false)).setValue(AGE, 0)).setValue(WATERLOGGED, true)
      );
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{PLANTED, AGE, WATERLOGGED});
   }

   public BlockState getStateForPlacement(BlockPlaceContext context) {
      return (BlockState)this.defaultBlockState().setValue(WATERLOGGED, true);
   }

   protected FluidState getFluidState(BlockState state) {
      return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
   }

   protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
      if ((Boolean)state.getValue(WATERLOGGED)) {
         level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
      }

      return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
   }

   protected ItemInteractionResult useItemOn(
      ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
   ) {
      if (!canPlant(state)) {
         return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
      }

      if (!stack.is((Item)ModItems.RICE.get())) {
         return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
      }

      if (!level.isClientSide) {
         if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            PlayerCultureReputation cultureRep = PlayerCultureReputation.get(serverLevel);
            if (!cultureRep.hasLearnedCrop(serverPlayer.getUUID(), "rice")) {
               serverPlayer.sendSystemMessage(Component.translatable("message.millenaire.crop_planting_knowledge"));
               return ItemInteractionResult.FAIL;
            }
         }

         level.setBlock(pos, (BlockState)((BlockState)state.setValue(PLANTED, true)).setValue(AGE, 0), 3);
         if (!player.getAbilities().instabuild) {
            stack.shrink(1);
         }

         level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
      }

      return ItemInteractionResult.SUCCESS;
   }

   protected boolean isRandomlyTicking(BlockState state) {
      return (Boolean)state.getValue(PLANTED) && (Integer)state.getValue(AGE) < 7;
   }

   protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      if ((Boolean)state.getValue(PLANTED)) {
         int age = (Integer)state.getValue(AGE);
         if (age < 7) {
            if (CommonHooks.canCropGrow(level, pos, state, random.nextInt(3) == 0)) {
               BlockState newState = (BlockState)state.setValue(AGE, age + 1);
               level.setBlock(pos, newState, 2);
               CommonHooks.fireCropGrowPost(level, pos, state);
            }
         }
      }
   }

   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return SHAPE;
   }

   protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return SHAPE;
   }

   @Nullable
   public PathType getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob mob) {
      return PathType.WALKABLE;
   }

   @Nullable
   public PathType getAdjacentBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob mob, PathType originalType) {
      return originalType;
   }

   public static boolean canPlant(BlockState state) {
      return state.getBlock() instanceof RicePaddyBlock && !(Boolean)state.getValue(PLANTED);
   }

   public static boolean canHarvest(BlockState state) {
      return state.getBlock() instanceof RicePaddyBlock && (Boolean)state.getValue(PLANTED) && (Integer)state.getValue(AGE) >= 7;
   }

   public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
      return (Boolean)state.getValue(PLANTED) && (Integer)state.getValue(AGE) < 7;
   }

   public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
      return true;
   }

   public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
      int newAge = Math.min((Integer)state.getValue(AGE) + random.nextIntBetweenInclusive(2, 5), 7);
      level.setBlock(pos, (BlockState)state.setValue(AGE, newAge), 2);
   }
}
