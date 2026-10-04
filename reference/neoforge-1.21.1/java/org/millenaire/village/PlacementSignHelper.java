package org.millenaire.village;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.ClearMargins;
import org.millenaire.world.PlacedLocation;
import org.millenaire.world.TerrainPreparer;
import org.millenaire.world.VillageTerrainMap;
import org.slf4j.Logger;

public final class PlacementSignHelper {
   private static final Logger LOGGER = LogUtils.getLogger();

   private PlacementSignHelper() {
   }

   public static void placeCornerSigns(ServerLevel level, BuildingPlan plan, PlacedLocation location, String projectName) {
      for (BlockPos corner : footprintCorners(plan, location)) {
         placeSign(level, corner.getX(), corner.getZ(), projectName);
      }
   }

   public static void removeCornerSigns(ServerLevel level, BuildingPlan plan, PlacedLocation location) {
      for (BlockPos corner : footprintCorners(plan, location)) {
         removeSignAt(level, corner.getX(), corner.getZ());
      }
   }

   private static BlockPos[] footprintCorners(BuildingPlan plan, PlacedLocation location) {
      VillageTerrainMap.FootprintRect rect = VillageTerrainMap.computeFootprintRect(
         location.position().getX(), location.position().getZ(), plan.width(), plan.depth(), ClearMargins.symmetric(0), location.rotation()
      );
      int x1 = rect.startX();
      int z1 = rect.startZ();
      int x2 = rect.startX() + rect.width() - 1;
      int z2 = rect.startZ() + rect.depth() - 1;
      return new BlockPos[]{new BlockPos(x1, 0, z1), new BlockPos(x2, 0, z1), new BlockPos(x1, 0, z2), new BlockPos(x2, 0, z2)};
   }

   private static void placeSign(ServerLevel level, int x, int z, String label) {
      int placeY = TerrainPreparer.getGroundHeight(level, x, z);
      BlockPos pos = new BlockPos(x, placeY, z);
      BlockState signState = (BlockState)Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8);
      if (level.setBlock(pos, signState, 3)) {
         if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            SignText text = new SignText()
               .setMessage(0, Component.literal(label))
               .setMessage(1, Component.empty())
               .setMessage(2, Component.literal("(project)"))
               .setMessage(3, Component.empty())
               .setColor(DyeColor.BLACK);
            sign.setText(text, true);
            sign.setText(text, false);
            sign.setChanged();
            level.sendBlockUpdated(pos, signState, signState, 3);
         } else {
            LOGGER.warn("[Millenaire] Placement sign at {} has no SignBlockEntity", pos);
         }
      }
   }

   private static void removeSignAt(ServerLevel level, int x, int z) {
      int placeY = TerrainPreparer.getGroundHeight(level, x, z);

      for (int dy = -2; dy <= 3; dy++) {
         BlockPos pos = new BlockPos(x, placeY + dy, z);
         BlockState state = level.getBlockState(pos);
         if (state.is(Blocks.OAK_SIGN)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            return;
         }
      }
   }
}
