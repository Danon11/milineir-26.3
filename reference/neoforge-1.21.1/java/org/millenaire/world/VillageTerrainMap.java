package org.millenaire.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.building.ClearMargins;
import org.millenaire.tag.ModTags;

public class VillageTerrainMap {
   private static final int ALTITUDE_TOLERANCE = 10;
   private static final int SCAN_RANGE = 25;
   private static final int SMALL_BUILDING_FIXED_ERRORS = 10;
   private static final int MEDIUM_BUILDING_THRESHOLD = 200;
   private static final int HUGE_BUILDING_THRESHOLD = 2000;
   private final BlockPos origin;
   private final int size;
   private final int baseline;
   private final int[][] topGround;
   private final boolean[][] canBuild;
   private final boolean[][] danger;
   private final boolean[][] water;
   private final boolean[][] buildingForbidden;
   private final short[][] spaceAbove;
   private final boolean[][] occupied;

   private VillageTerrainMap(
      BlockPos origin,
      int size,
      int baseline,
      int[][] topGround,
      boolean[][] canBuild,
      boolean[][] danger,
      boolean[][] water,
      boolean[][] buildingForbidden,
      short[][] spaceAbove
   ) {
      this.origin = origin;
      this.size = size;
      this.baseline = baseline;
      this.topGround = topGround;
      this.canBuild = canBuild;
      this.danger = danger;
      this.water = water;
      this.buildingForbidden = buildingForbidden;
      this.spaceAbove = spaceAbove;
      this.occupied = new boolean[size][size];
   }

   static VillageTerrainMap forTest(BlockPos origin, int size, int baseline, int[][] topGround, boolean[][] canBuild) {
      boolean[][] danger = new boolean[size][size];
      boolean[][] water = new boolean[size][size];
      boolean[][] forbidden = new boolean[size][size];
      short[][] spaceAbove = new short[size][size];

      for (int x = 0; x < size; x++) {
         for (int z = 0; z < size; z++) {
            spaceAbove[x][z] = 3;
         }
      }

      return new VillageTerrainMap(origin, size, baseline, topGround, canBuild, danger, water, forbidden, spaceAbove);
   }

   static VillageTerrainMap forTest(
      BlockPos origin,
      int size,
      int baseline,
      int[][] topGround,
      boolean[][] canBuild,
      boolean[][] danger,
      boolean[][] water,
      boolean[][] buildingForbidden,
      short[][] spaceAbove
   ) {
      return new VillageTerrainMap(origin, size, baseline, topGround, canBuild, danger, water, buildingForbidden, spaceAbove);
   }

   private static boolean isDangerous(BlockState state) {
      return state.is(Blocks.LAVA)
         || state.is(Blocks.FIRE)
         || state.is(Blocks.SOUL_FIRE)
         || state.is(Blocks.CACTUS)
         || state.is(Blocks.TNT)
         || state.is(Blocks.MAGMA_BLOCK)
         || state.is(Blocks.SWEET_BERRY_BUSH);
   }

   private static boolean isForbidden(ServerLevel level, BlockPos pos, BlockState state) {
      if (state.isAir()) {
         return false;
      } else if (state.is(ModTags.Blocks.FORBIDDEN_EXCEPTIONS)) {
         return false;
      } else if (level.getBlockEntity(pos) != null) {
         return true;
      } else {
         return state.getBlock() instanceof StainedGlassBlock || state.getBlock() instanceof StainedGlassPaneBlock
            ? true
            : state.is(ModTags.Blocks.ARTIFICIAL_BLOCKS);
      }
   }

   private static boolean isGround(BlockState state) {
      if (state.isAir()) {
         return false;
      } else if (!state.getFluidState().isEmpty()) {
         return false;
      } else if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
         return false;
      } else {
         return TerrainPreparer.isHugeMushroomBlock(state) ? false : state.canOcclude();
      }
   }

   public static VillageTerrainMap compute(ServerLevel level, BlockPos villageCenter, int radius) {
      int size = 2 * radius + 1;
      int originX = villageCenter.getX() - radius;
      int originZ = villageCenter.getZ() - radius;
      BlockPos origin = new BlockPos(originX, villageCenter.getY(), originZ);
      int baseline = villageCenter.getY();
      int miny = Math.max(baseline - 25, level.getMinBuildHeight());
      int maxy = Math.min(baseline + 25, level.getMaxBuildHeight());
      int[][] topGround = new int[size][size];
      boolean[][] canBuild = new boolean[size][size];
      boolean[][] danger = new boolean[size][size];
      boolean[][] water = new boolean[size][size];
      boolean[][] buildingForbidden = new boolean[size][size];
      short[][] spaceAbove = new short[size][size];

      for (int lx = 0; lx < size; lx++) {
         for (int lz = 0; lz < size; lz++) {
            int wx = originX + lx;
            int wz = originZ + lz;
            boolean hasForbidden = false;
            int groundY = miny;

            for (int y = maxy; y >= miny; y--) {
               BlockPos pos = new BlockPos(wx, y, wz);
               BlockState state = level.getBlockState(pos);
               if (isForbidden(level, pos, state)) {
                  hasForbidden = true;
               }

               if (isGround(state)) {
                  groundY = y;
                  break;
               }
            }

            boolean onGround = true;
            int lastLiquid = groundY;
            int surfaceY = groundY;

            for (int y = groundY + 1; y <= maxy; surfaceY = y++) {
               BlockState state = level.getBlockState(new BlockPos(wx, y, wz));
               if (!state.getFluidState().isEmpty()) {
                  onGround = false;
                  lastLiquid = y;
               } else {
                  if (!state.canOcclude()) {
                     surfaceY = y;
                     break;
                  }

                  onGround = true;
               }
            }

            if (!onGround) {
               surfaceY = lastLiquid;
            }

            topGround[lx][lz] = surfaceY;
            BlockState groundState = level.getBlockState(new BlockPos(wx, groundY, wz));
            boolean hasDanger = isDangerous(groundState);
            if (!hasDanger) {
               BlockState surfaceState = level.getBlockState(new BlockPos(wx, surfaceY, wz));
               hasDanger = isDangerous(surfaceState);
            }

            boolean hasWater = !level.getFluidState(new BlockPos(wx, surfaceY, wz)).isEmpty();

            for (int y = surfaceY; y <= maxy; y++) {
               BlockPos pos = new BlockPos(wx, y, wz);
               BlockState state = level.getBlockState(pos);
               if (isForbidden(level, pos, state)) {
                  hasForbidden = true;
               }

               if (isDangerous(state)) {
                  hasDanger = true;
               }
            }

            short sa = 0;
            boolean blocked = false;

            for (int y = surfaceY; y < surfaceY + 3 && y <= maxy; y++) {
               BlockState state = level.getBlockState(new BlockPos(wx, y, wz));
               if (!blocked && !state.canOcclude()) {
                  sa++;
               } else {
                  blocked = true;
               }
            }

            danger[lx][lz] = hasDanger;
            water[lx][lz] = hasWater;
            buildingForbidden[lx][lz] = hasForbidden;
            spaceAbove[lx][lz] = sa;
            boolean altitudeOk = surfaceY > baseline - 10 && surfaceY < baseline + 10;
            canBuild[lx][lz] = altitudeOk && !hasDanger && !hasForbidden;
         }
      }

      return new VillageTerrainMap(origin, size, baseline, topGround, canBuild, danger, water, buildingForbidden, spaceAbove);
   }

   public int toLocalX(int worldX) {
      return worldX - this.origin.getX();
   }

   public int toLocalZ(int worldZ) {
      return worldZ - this.origin.getZ();
   }

   public boolean inBounds(int localX, int localZ) {
      return localX >= 0 && localX < this.size && localZ >= 0 && localZ < this.size;
   }

   public int testFootprint(int worldX, int worldZ, int width, int depth, ClearMargins margins, Rotation rotation) {
      VillageTerrainMap.FootprintRect rect = computeFootprintRect(worldX, worldZ, width, depth, margins, rotation);
      int surface = rect.width() * rect.depth();
      boolean hugeBuilding = surface > 2000;
      int allowedErrors;
      if (surface > 2000) {
         allowedErrors = surface / 10;
      } else if (surface > 200) {
         allowedErrors = surface / 20;
      } else {
         allowedErrors = 10;
      }

      int nbError = 0;

      for (int dx = 0; dx < rect.width(); dx++) {
         for (int dz = 0; dz < rect.depth(); dz++) {
            int lx = this.toLocalX(rect.startX() + dx);
            int lz = this.toLocalZ(rect.startZ() + dz);
            if (!this.inBounds(lx, lz)) {
               return -1;
            }

            if (this.occupied[lx][lz]) {
               return -1;
            }

            if (this.buildingForbidden[lx][lz]) {
               if (!hugeBuilding || nbError > allowedErrors) {
                  return -1;
               }

               nbError++;
            } else if (this.danger[lx][lz]) {
               if (nbError > allowedErrors) {
                  return -1;
               }

               nbError++;
            } else if (!this.canBuild[lx][lz]) {
               if (nbError > allowedErrors) {
                  return -1;
               }

               nbError++;
            }
         }
      }

      return nbError;
   }

   public int computeAverageAltitude(int worldX, int worldZ, int width, int depth, ClearMargins margins, Rotation rotation) {
      VillageTerrainMap.FootprintRect rect = computeFootprintRect(worldX, worldZ, width, depth, margins, rotation);
      long altitudeTotal = 0L;
      int nbPoints = 0;

      for (int dx = 0; dx < rect.width(); dx++) {
         for (int dz = 0; dz < rect.depth(); dz++) {
            int lx = this.toLocalX(rect.startX() + dx);
            int lz = this.toLocalZ(rect.startZ() + dz);
            if (this.inBounds(lx, lz)) {
               altitudeTotal += this.topGround[lx][lz];
               nbPoints++;
            }
         }
      }

      return nbPoints == 0 ? this.baseline : Math.round((float)altitudeTotal / nbPoints);
   }

   public void markBuildingFootprint(int worldX, int worldZ, int width, int depth, ClearMargins margins, Rotation rotation, int buildingY) {
      VillageTerrainMap.FootprintRect fullRect = computeFootprintRect(worldX, worldZ, width, depth, margins, rotation);
      VillageTerrainMap.FootprintRect coreRect = computeFootprintRect(worldX, worldZ, width, depth, ClearMargins.symmetric(0), rotation);

      for (int dx = 0; dx < coreRect.width(); dx++) {
         for (int dz = 0; dz < coreRect.depth(); dz++) {
            int lx = this.toLocalX(coreRect.startX() + dx);
            int lz = this.toLocalZ(coreRect.startZ() + dz);
            if (this.inBounds(lx, lz)) {
               this.occupied[lx][lz] = true;
            }
         }
      }

      for (int dx = 0; dx < coreRect.width(); dx++) {
         for (int dz = 0; dz < coreRect.depth(); dz++) {
            int lx = this.toLocalX(coreRect.startX() + dx);
            int lz = this.toLocalZ(coreRect.startZ() + dz);
            if (this.inBounds(lx, lz)) {
               this.topGround[lx][lz] = buildingY;
               this.spaceAbove[lx][lz] = 3;
               this.water[lx][lz] = false;
               this.danger[lx][lz] = false;
               this.canBuild[lx][lz] = true;
            }
         }
      }

      for (int dx = 0; dx < fullRect.width(); dx++) {
         for (int dz = 0; dz < fullRect.depth(); dz++) {
            int wx = fullRect.startX() + dx;
            int wz = fullRect.startZ() + dz;
            if (wx < coreRect.startX() || wx >= coreRect.startX() + coreRect.width() || wz < coreRect.startZ() || wz >= coreRect.startZ() + coreRect.depth()) {
               int lx = this.toLocalX(wx);
               int lz = this.toLocalZ(wz);
               if (this.inBounds(lx, lz) && !this.occupied[lx][lz]) {
                  this.canBuild[lx][lz] = true;
                  this.water[lx][lz] = false;
                  this.danger[lx][lz] = false;
               }
            }
         }
      }
   }

   public void markOccupied(int worldX, int worldZ, int width, int depth, Rotation rotation) {
      VillageTerrainMap.FootprintRect rect = computeFootprintRect(worldX, worldZ, width, depth, ClearMargins.symmetric(0), rotation);

      for (int dx = 0; dx < rect.width(); dx++) {
         for (int dz = 0; dz < rect.depth(); dz++) {
            int lx = this.toLocalX(rect.startX() + dx);
            int lz = this.toLocalZ(rect.startZ() + dz);
            if (this.inBounds(lx, lz)) {
               this.occupied[lx][lz] = true;
            }
         }
      }
   }

   public int getSize() {
      return this.size;
   }

   public int getBaseline() {
      return this.baseline;
   }

   public int getGroundHeight(int worldX, int worldZ) {
      int lx = this.toLocalX(worldX);
      int lz = this.toLocalZ(worldZ);
      return !this.inBounds(lx, lz) ? this.baseline : this.topGround[lx][lz];
   }

   public int getTopGround(int localX, int localZ) {
      return this.inBounds(localX, localZ) ? this.topGround[localX][localZ] : 0;
   }

   public boolean canBuildAt(int localX, int localZ) {
      return this.inBounds(localX, localZ) && this.canBuild[localX][localZ];
   }

   public boolean isDangerAt(int localX, int localZ) {
      return this.inBounds(localX, localZ) && this.danger[localX][localZ];
   }

   public boolean isWaterAt(int localX, int localZ) {
      return this.inBounds(localX, localZ) && this.water[localX][localZ];
   }

   public boolean isBuildingForbiddenAt(int localX, int localZ) {
      return this.inBounds(localX, localZ) && this.buildingForbidden[localX][localZ];
   }

   public short getSpaceAbove(int localX, int localZ) {
      return this.inBounds(localX, localZ) ? this.spaceAbove[localX][localZ] : 0;
   }

   public boolean isOccupied(int localX, int localZ) {
      return this.inBounds(localX, localZ) && this.occupied[localX][localZ];
   }

   public static VillageTerrainMap.FootprintRect computeFootprintRect(int worldX, int worldZ, int width, int depth, ClearMargins margins, Rotation rotation) {
      ClearMargins effective = margins.forRotation(rotation);
      int startX;
      int startZ;
      int effectiveWidth;
      int effectiveDepth;
      switch (rotation) {
         case CLOCKWISE_90:
            startX = worldX - depth + 1 - effective.lengthBefore();
            startZ = worldZ - effective.widthBefore();
            effectiveWidth = depth + effective.lengthBefore() + effective.lengthAfter();
            effectiveDepth = width + effective.widthBefore() + effective.widthAfter();
            break;
         case CLOCKWISE_180:
            startX = worldX - width + 1 - effective.lengthBefore();
            startZ = worldZ - depth + 1 - effective.widthBefore();
            effectiveWidth = width + effective.lengthBefore() + effective.lengthAfter();
            effectiveDepth = depth + effective.widthBefore() + effective.widthAfter();
            break;
         case COUNTERCLOCKWISE_90:
            startX = worldX - effective.lengthBefore();
            startZ = worldZ - width + 1 - effective.widthBefore();
            effectiveWidth = depth + effective.lengthBefore() + effective.lengthAfter();
            effectiveDepth = width + effective.widthBefore() + effective.widthAfter();
            break;
         default:
            startX = worldX - effective.lengthBefore();
            startZ = worldZ - effective.widthBefore();
            effectiveWidth = width + effective.lengthBefore() + effective.lengthAfter();
            effectiveDepth = depth + effective.widthBefore() + effective.widthAfter();
      }

      return new VillageTerrainMap.FootprintRect(startX, startZ, effectiveWidth, effectiveDepth);
   }

   public record FootprintRect(int startX, int startZ, int width, int depth) {
   }
}
