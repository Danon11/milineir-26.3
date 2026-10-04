package org.millenaire.building;

import com.mojang.logging.LogUtils;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

public final class MillenaireStructureLoader {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final ConcurrentHashMap<ResourceLocation, StructureTemplate> CACHE = new ConcurrentHashMap<>();

   private MillenaireStructureLoader() {
   }

   public static Optional<StructureTemplate> get(ServerLevel level, ResourceLocation id) {
      if (id == null) {
         return Optional.empty();
      }

      StructureTemplate cached = CACHE.get(id);
      if (cached != null) {
         return Optional.of(cached);
      }

      Optional<StructureTemplate> opt = level.getStructureManager().get(id);
      if (opt.isEmpty()) {
         return opt;
      }

      StructureTemplate original = opt.get();
      StructureTemplate sanitized = sanitizeOrReturnOriginal(level, original, id);
      CACHE.put(id, sanitized);
      return Optional.of(sanitized);
   }

   public static StructureTemplate getOrLoadExternal(ServerLevel level, ResourceLocation id, CompoundTag nbt) {
      StructureTemplate cached = id != null ? CACHE.get(id) : null;
      if (cached != null) {
         return cached;
      }

      HearthTemplateSanitizer.sanitize(nbt, level.registryAccess());
      StructureTemplate template = new StructureTemplate();
      HolderGetter<Block> blockLookup = level.holderLookup(Registries.BLOCK);
      template.load(blockLookup, nbt);
      if (id != null) {
         CACHE.put(id, template);
      }

      return template;
   }

   public static StructureTemplate getOrLoadExport(ServerLevel level, String stableKey, CompoundTag nbt) {
      ResourceLocation id = stableKey != null ? ResourceLocation.fromNamespaceAndPath("millenaire", "export/" + stableKey) : null;
      return getOrLoadExternal(level, id, nbt);
   }

   public static void clearCache() {
      int size = CACHE.size();
      CACHE.clear();
      if (size > 0) {
         LOGGER.debug("MillenaireStructureLoader: cleared {} cached template(s)", size);
      }
   }

   private static StructureTemplate sanitizeOrReturnOriginal(ServerLevel level, StructureTemplate original, @Nullable ResourceLocation id) {
      try {
         CompoundTag nbt = original.save(new CompoundTag());
         boolean mutated = HearthTemplateSanitizer.sanitize(nbt, level.registryAccess());
         if (!mutated) {
            return original;
         }

         StructureTemplate sanitized = new StructureTemplate();
         HolderGetter<Block> blockLookup = level.holderLookup(Registries.BLOCK);
         sanitized.load(blockLookup, nbt);
         return sanitized;
      } catch (Exception e) {
         LOGGER.warn("MillenaireStructureLoader: failed to sanitize template {}: {}", id, e.getMessage());
         return original;
      }
   }
}
