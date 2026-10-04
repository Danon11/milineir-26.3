package org.millenaire.block.mock;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.millenaire.block.LockedChestBlock;
import org.millenaire.block.ModBlocks;
import org.millenaire.building.SpecialPoint;

public class MockChestBlock extends MockBlock {
   public static final EnumProperty<MockChestType> CHEST_TYPE = EnumProperty.create("chest_type", MockChestType.class);
   public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
   public static final BooleanProperty GUESS = BooleanProperty.create("guess");

   public MockChestBlock(Properties properties) {
      super(properties);
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(CHEST_TYPE, MockChestType.MAIN))
               .setValue(FACING, Direction.NORTH))
            .setValue(GUESS, false)
      );
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{CHEST_TYPE, FACING, GUESS});
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
      return CHEST_TYPE;
   }

   protected String translationKeyPrefix() {
      return "block.millenaire.mock_chest.";
   }

   public SpecialPoint toSpecialPoint(BlockState state, BlockPos pos) {
      MockChestType chestType = (MockChestType)state.getValue(CHEST_TYPE);
      Direction facing = (Direction)state.getValue(FACING);
      return new SpecialPoint("chest", chestType.getSerializedName(), facing.getSerializedName(), pos);
   }

   @Nullable
   public BlockState getReplacementState(BlockState mockState) {
      MockChestType chestType = (MockChestType)mockState.getValue(CHEST_TYPE);
      Direction facing = (Direction)mockState.getValue(FACING);
      return (BlockState)((LockedChestBlock)ModBlocks.LOCKED_CHEST.get()).defaultBlockState().setValue(ChestBlock.FACING, facing);
   }
}
