package org.millenaire.culture;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import org.millenaire.village.BrickColourTheme;

public record VillageType(
   ResourceLocation id,
   ResourceLocation culture,
   String name,
   int weight,
   List<TagKey<Biome>> biomeTags,
   List<VillageType.LayoutSlot> layout,
   Map<String, Integer> sellingPriceOverrides,
   Map<String, Integer> buyingPriceOverrides,
   int maxSimultaneousConstructions,
   List<String> qualifiers,
   @Nullable String forestQualifier,
   @Nullable String hillQualifier,
   @Nullable String mountainQualifier,
   @Nullable String desertQualifier,
   @Nullable String lavaQualifier,
   @Nullable String lakeQualifier,
   @Nullable String oceanQualifier,
   List<ResourceLocation> playerBuildings,
   List<BrickColourTheme> brickColourThemes,
   List<String> neverBuildings,
   boolean loneBuilding,
   int minDistanceFromSpawn,
   int max,
   boolean keyLoneBuilding,
   @Nullable String keyLoneBuildingGenerateTag,
   boolean generatedForPlayer,
   boolean spawnable,
   boolean showTownHallSigns,
   @Nullable String nameList,
   int radius,
   float minimumBiomeValidity,
   List<String> pathMaterials,
   boolean travelBookDisplay,
   List<ResourceLocation> hamlets,
   @Nullable String specialType,
   boolean allowExtraBuildings,
   @Nullable String icon,
   boolean playerControlled,
   @Nullable ResourceLocation outerWallType,
   @Nullable ResourceLocation innerWallType,
   int innerWallRadius,
   int maxSimultaneousWallConstructions,
   List<String> bannerJsons
) {
   public boolean isMarvel() {
      return "marvel".equals(this.specialType);
   }

   public boolean isRegularVillage() {
      return this.specialType == null && !this.loneBuilding;
   }

   public boolean isHamlet() {
      return "hameau".equals(this.specialType);
   }

   public boolean isPlayerControlled() {
      return this.playerControlled;
   }

   public record LayoutSlot(
      ResourceLocation plan,
      @Nullable BlockPos offset,
      @Nullable Rotation rotation,
      String role,
      double minDistance,
      double maxDistance,
      int priority,
      Map<String, Integer> farFromTags,
      Map<String, Integer> closeToTags,
      int clearMargin,
      @Nullable Integer fixedOrientation
   ) {
      public boolean hasLegacyOffset() {
         return this.offset != null;
      }

      public boolean hasMinDistanceOverride() {
         return this.minDistance >= 0.0;
      }

      public boolean hasMaxDistanceOverride() {
         return this.maxDistance >= 0.0;
      }
   }
}
