package org.millenaire.world;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.millenaire.block.mock.MockBlock;

public class MockBlockProcessor extends StructureProcessor {
   public static final MockBlockProcessor INSTANCE = new MockBlockProcessor();
   public static final MapCodec<MockBlockProcessor> CODEC = MapCodec.unit(INSTANCE);

   @Nullable
   public StructureBlockInfo process(
      LevelReader level,
      BlockPos offset,
      BlockPos pos,
      StructureBlockInfo original,
      StructureBlockInfo transformed,
      StructurePlaceSettings settings,
      @Nullable StructureTemplate template
   ) {
      BlockState state = transformed.state();
      if (state.getBlock() instanceof MockBlock mock) {
         BlockState replacement = mock.getReplacementState(state);
         if (replacement == null) {
            return null;
         }

         CompoundTag nbt = null;
         if (replacement.getBlock() instanceof EntityBlock entityBlock) {
            BlockEntity tempBe = entityBlock.newBlockEntity(transformed.pos(), replacement);
            if (tempBe != null) {
               nbt = tempBe.saveWithoutMetadata(level.registryAccess());
            }
         }

         return new StructureBlockInfo(transformed.pos(), replacement, nbt);
      } else {
         return transformed;
      }
   }

   protected StructureProcessorType<?> getType() {
      return ModProcessors.MOCK_BLOCK_PROCESSOR.get();
   }
}
