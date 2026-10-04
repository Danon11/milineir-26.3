package org.millenaire.block.mock;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.millenaire.building.SpecialPoint;

public class MockBannerWallBlock extends MockBlock {
   public static final EnumProperty<BannerSubtype> SUBTYPE = EnumProperty.create("subtype", BannerSubtype.class);
   public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

   public MockBannerWallBlock(Properties properties) {
      super(properties);
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(SUBTYPE, BannerSubtype.VILLAGE)).setValue(FACING, Direction.NORTH)
      );
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{SUBTYPE, FACING});
   }

   @Nullable
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      return (BlockState)this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
   }

   public BlockState rotate(BlockState state, Rotation rotation) {
      return (BlockState)state.setValue(FACING, rotation.rotate((Direction)state.getValue(FACING)));
   }

   public BlockState mirror(BlockState state, Mirror mirror) {
      return state.rotate(mirror.getRotation((Direction)state.getValue(FACING)));
   }

   protected Property<? extends Comparable<?>> variantProperty() {
      return SUBTYPE;
   }

   protected String translationKeyPrefix() {
      return "block.millenaire.mock_banner_wall.";
   }

   public SpecialPoint toSpecialPoint(BlockState state, BlockPos pos) {
      BannerSubtype subtype = (BannerSubtype)state.getValue(SUBTYPE);
      Direction facing = (Direction)state.getValue(FACING);
      String placement = "wall_" + facing.getSerializedName();
      return new SpecialPoint("banner", subtype.specialPointSubtype(), facing.getSerializedName(), placement, pos);
   }

   @Nullable
   public BlockState getReplacementState(BlockState mockState) {
      Direction facing = (Direction)mockState.getValue(FACING);
      return (BlockState)Blocks.WHITE_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, facing);
   }
}
