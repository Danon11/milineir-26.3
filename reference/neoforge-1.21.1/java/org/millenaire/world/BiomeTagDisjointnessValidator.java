package org.millenaire.world;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.HolderSet.Named;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;

public final class BiomeTagDisjointnessValidator {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final List<BiomeTagDisjointnessValidator.TagPair> DISJOINT_PAIRS = List.of(
      new BiomeTagDisjointnessValidator.TagPair("c", "is_forest", "c", "is_birch_forest"),
      new BiomeTagDisjointnessValidator.TagPair("c", "is_forest", "c", "is_dark_forest"),
      new BiomeTagDisjointnessValidator.TagPair("c", "is_plains", "c", "is_meadow")
   );

   private BiomeTagDisjointnessValidator() {
   }

   public static void validate(RegistryAccess registryAccess) {
      Registry<Biome> biomes = registryAccess.registryOrThrow(Registries.BIOME);

      for (BiomeTagDisjointnessValidator.TagPair pair : DISJOINT_PAIRS) {
         List<ResourceLocation> overlap = findOverlap(biomes, pair.left(), pair.right());
         if (!overlap.isEmpty()) {
            LOGGER.warn(
               "[Millenaire] Biome tag overlap: {} biome(s) belong to both #{}:{} and #{}:{} — village/LB biome redistribution assumes these tags are disjoint. Overlapping biomes: {}",
               new Object[]{
                  overlap.size(),
                  pair.left().location().getNamespace(),
                  pair.left().location().getPath(),
                  pair.right().location().getNamespace(),
                  pair.right().location().getPath(),
                  overlap
               }
            );
         }
      }
   }

   private static List<ResourceLocation> findOverlap(Registry<Biome> biomes, TagKey<Biome> a, TagKey<Biome> b) {
      Named<Biome> setA = (Named<Biome>)biomes.getTag(a).orElse(null);
      Named<Biome> setB = (Named<Biome>)biomes.getTag(b).orElse(null);
      if (setA != null && setB != null) {
         List<ResourceLocation> overlap = new ArrayList<>();

         for (Holder<Biome> holder : setA) {
            if (StreamSupport.<Holder<Biome>>stream(setB.spliterator(), false).anyMatch(h -> h.equals(holder))) {
               holder.unwrapKey().ifPresent(key -> overlap.add(key.location()));
            }
         }

         return overlap;
      } else {
         return List.of();
      }
   }

   private record TagPair(TagKey<Biome> left, TagKey<Biome> right) {
      TagPair(String nsA, String pathA, String nsB, String pathB) {
         this(
            TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(nsA, pathA)),
            TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(nsB, pathB))
         );
      }
   }
}
