package org.millenaire.village.path;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import org.millenaire.block.ModBlocks;

public final class PathMaterials {
   private static Map<String, PathMaterials.MaterialPair> materials;

   private PathMaterials() {
   }

   private static Map<String, PathMaterials.MaterialPair> getMaterials() {
      if (materials == null) {
         materials = new HashMap<>();
         materials.put("pathdirt", new PathMaterials.MaterialPair((Block)ModBlocks.PATH_DIRT.get(), (SlabBlock)ModBlocks.PATH_DIRT_SLAB.get()));
         materials.put("pathgravel", new PathMaterials.MaterialPair((Block)ModBlocks.PATH_GRAVEL.get(), (SlabBlock)ModBlocks.PATH_GRAVEL_SLAB.get()));
         materials.put("pathslabs", new PathMaterials.MaterialPair((Block)ModBlocks.PATH_SLABS.get(), (SlabBlock)ModBlocks.PATH_SLABS_SLAB.get()));
         materials.put("pathsandstone", new PathMaterials.MaterialPair((Block)ModBlocks.PATH_SANDSTONE.get(), (SlabBlock)ModBlocks.PATH_SANDSTONE_SLAB.get()));
         materials.put(
            "pathochretiles", new PathMaterials.MaterialPair((Block)ModBlocks.PATH_OCHRE_TILES.get(), (SlabBlock)ModBlocks.PATH_OCHRE_TILES_SLAB.get())
         );
         materials.put(
            "pathgravelslabs", new PathMaterials.MaterialPair((Block)ModBlocks.PATH_GRAVEL_SLABS.get(), (SlabBlock)ModBlocks.PATH_GRAVEL_SLABS_SLAB.get())
         );
         materials.put("pathsnow", new PathMaterials.MaterialPair((Block)ModBlocks.PATH_SNOW.get(), (SlabBlock)ModBlocks.PATH_SNOW_SLAB.get()));
      }

      return materials;
   }

   @Nullable
   public static PathMaterials.MaterialPair resolve(String materialName) {
      return getMaterials().get(materialName);
   }

   public record MaterialPair(Block fullBlock, SlabBlock slabBlock) {
   }
}
