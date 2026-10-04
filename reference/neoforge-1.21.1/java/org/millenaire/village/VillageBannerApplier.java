package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.SpecialPoint;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.slf4j.Logger;

public final class VillageBannerApplier {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Map<DyeColor, Block> WALL_BANNER_BY_COLOR = buildWallBannerMap();

   private VillageBannerApplier() {
   }

   private static Map<DyeColor, Block> buildWallBannerMap() {
      Map<DyeColor, Block> m = new EnumMap<>(DyeColor.class);
      m.put(DyeColor.WHITE, Blocks.WHITE_WALL_BANNER);
      m.put(DyeColor.ORANGE, Blocks.ORANGE_WALL_BANNER);
      m.put(DyeColor.MAGENTA, Blocks.MAGENTA_WALL_BANNER);
      m.put(DyeColor.LIGHT_BLUE, Blocks.LIGHT_BLUE_WALL_BANNER);
      m.put(DyeColor.YELLOW, Blocks.YELLOW_WALL_BANNER);
      m.put(DyeColor.LIME, Blocks.LIME_WALL_BANNER);
      m.put(DyeColor.PINK, Blocks.PINK_WALL_BANNER);
      m.put(DyeColor.GRAY, Blocks.GRAY_WALL_BANNER);
      m.put(DyeColor.LIGHT_GRAY, Blocks.LIGHT_GRAY_WALL_BANNER);
      m.put(DyeColor.CYAN, Blocks.CYAN_WALL_BANNER);
      m.put(DyeColor.PURPLE, Blocks.PURPLE_WALL_BANNER);
      m.put(DyeColor.BLUE, Blocks.BLUE_WALL_BANNER);
      m.put(DyeColor.BROWN, Blocks.BROWN_WALL_BANNER);
      m.put(DyeColor.GREEN, Blocks.GREEN_WALL_BANNER);
      m.put(DyeColor.RED, Blocks.RED_WALL_BANNER);
      m.put(DyeColor.BLACK, Blocks.BLACK_WALL_BANNER);
      return Map.copyOf(m);
   }

   public static void applyBanners(ServerLevel level, BuildingInstance building, Village village) {
      List<SpecialPoint> points = building.getResolvedPoints();
      if (!points.isEmpty()) {
         for (SpecialPoint sp : points) {
            if (sp.isType("banner")) {
               applyOne(level, sp, village);
            }
         }
      }
   }

   private static void applyOne(ServerLevel level, SpecialPoint sp, Village village) {
      ItemStack bannerStack = resolveBannerStack(level, sp, village);
      if (!bannerStack.isEmpty()) {
         DyeColor color = extractColor(bannerStack);
         if (color == null) {
            LOGGER.warn("[banner] resolved banner stack has no DyeColor: {}", bannerStack);
         } else {
            BlockPos pos = sp.pos();
            BlockState current = level.getBlockState(pos);
            Block currentBlock = current.getBlock();
            BlockState replacement;
            if (currentBlock instanceof WallBannerBlock) {
               Direction facing = (Direction)current.getValue(WallBannerBlock.FACING);
               Block dyed = WALL_BANNER_BY_COLOR.get(color);
               if (dyed == null) {
                  LOGGER.warn("[banner] no wall banner for color {}", color);
                  return;
               }

               replacement = (BlockState)dyed.defaultBlockState().setValue(WallBannerBlock.FACING, facing);
            } else {
               if (!(currentBlock instanceof BannerBlock)) {
                  LOGGER.warn("[banner] expected a banner block at {}, found {}", pos, currentBlock);
                  return;
               }

               int rotation = (Integer)current.getValue(BannerBlock.ROTATION);
               replacement = (BlockState)BannerBlock.byColor(color).defaultBlockState().setValue(BannerBlock.ROTATION, rotation);
            }

            level.setBlock(pos, replacement, 3);
            if (level.getBlockEntity(pos) instanceof BannerBlockEntity be) {
               be.applyComponentsFromItemStack(bannerStack);
               be.setChanged();
               level.sendBlockUpdated(pos, replacement, replacement, 2);
            }
         }
      }
   }

   private static ItemStack resolveBannerStack(ServerLevel level, SpecialPoint sp, Village village) {
      String subtype = sp.subtype();
      if ("culture".equals(subtype)) {
         if (village == null) {
            return ItemStack.EMPTY;
         }

         Culture culture = ModCultures.getCulture(village.getCultureId());
         return culture == null ? ItemStack.EMPTY : VillageBannerService.getCultureBanner(culture, level.registryAccess());
      } else {
         return village == null ? ItemStack.EMPTY : village.getBannerStack(level.registryAccess());
      }
   }

   private static DyeColor extractColor(ItemStack stack) {
      return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof AbstractBannerBlock ab ? ab.getColor() : null;
   }
}
