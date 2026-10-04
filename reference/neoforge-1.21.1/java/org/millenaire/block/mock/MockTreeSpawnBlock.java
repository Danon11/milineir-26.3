package org.millenaire.block.mock;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.millenaire.building.SpecialPoint;

public class MockTreeSpawnBlock extends MockBlock {
   public static final EnumProperty<TreeSpawnType> TREE = EnumProperty.create("tree", TreeSpawnType.class);
   private static final VoxelShape CARPET_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);

   public MockTreeSpawnBlock(Properties properties) {
      super(properties);
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(TREE, TreeSpawnType.OAK));
   }

   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return CARPET_SHAPE;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{TREE});
   }

   protected Property<? extends Comparable<?>> variantProperty() {
      return TREE;
   }

   protected String translationKeyPrefix() {
      return "block.millenaire.mock_tree_spawn.";
   }

   public SpecialPoint toSpecialPoint(BlockState state, BlockPos pos) {
      TreeSpawnType tree = (TreeSpawnType)state.getValue(TREE);
      return new SpecialPoint("treeSpawn", tree.getSerializedName(), null, pos);
   }

   @Nullable
   public BlockState getReplacementState(BlockState mockState) {
      return null;
   }
}
