package org.millenaire.building;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.millenaire.content.ChainedContentFs;
import org.millenaire.content.ContentFs;
import org.millenaire.content.CustomContentIndex;
import org.millenaire.content.Resource;
import org.millenaire.content.SourceKind;
import org.millenaire.culture.ModCultures;
import org.slf4j.Logger;

public final class TemplateLoader {
   private static final Logger LOGGER = LogUtils.getLogger();

   private TemplateLoader() {
   }

   public static Optional<StructureTemplate> load(BuildingPlan plan, ServerLevel level, ContentFs cultureFs) {
      if (plan == null) {
         throw new IllegalArgumentException("plan null");
      } else {
         return loadFromPath(plan.nbtPath(), level, cultureFs);
      }
   }

   public static Optional<StructureTemplate> resolve(ServerLevel level, String cultureKey, String buildingId, String variant, int upgradeLevel) {
      if (level == null) {
         throw new IllegalArgumentException("level null");
      } else {
         BuildingPlan plan = resolvePlan(cultureKey, buildingId, variant, upgradeLevel);
         if (plan != null) {
            ContentFs cultureFs = cultureFsFor(plan.culture());
            return load(plan, level, cultureFs);
         } else {
            return cultureKey != null && !cultureKey.isEmpty() ? Optional.empty() : resolveFromExportsFallback(level, buildingId, variant, upgradeLevel);
         }
      }
   }

   private static Optional<StructureTemplate> resolveFromExportsFallback(ServerLevel level, String buildingId, String variant, int upgradeLevel) {
      Optional<Path> path = BuildingExporter.findExportedNbt(level, buildingId, variant, upgradeLevel);
      return path.isEmpty() ? Optional.empty() : resolveFromExportsPath(path.get(), level.holderLookup(Registries.BLOCK), buildingId, variant, upgradeLevel);
   }

   static Optional<StructureTemplate> resolveFromExportsPath(Path nbtFile, HolderLookup<Block> blocks, String buildingId, String variant, int upgradeLevel) {
      try (InputStream is = Files.newInputStream(nbtFile)) {
         CompoundTag nbt = NbtIo.readCompressed(is, NbtAccounter.unlimitedHeap());
         HearthTemplateSanitizer.sanitize(nbt, null);
         StructureTemplate tmpl = new StructureTemplate();
         tmpl.load(blocks, nbt);
         return Optional.of(tmpl);
      } catch (IOException e) {
         LOGGER.warn("HIGH#3 exports/ fallback failed for {}_{}_{}: {}", new Object[]{buildingId, variant, upgradeLevel, e.getMessage()});
         return Optional.empty();
      }
   }

   @Nullable
   public static BuildingPlan resolvePlan(String cultureKey, String buildingId, String variant, int upgradeLevel) {
      if (cultureKey != null && !cultureKey.isEmpty()) {
         ResourceLocation cultureRL = ResourceLocation.parse(cultureKey);
         ResourceLocation planSetId = ResourceLocation.fromNamespaceAndPath(cultureRL.getNamespace(), cultureRL.getPath() + "/" + buildingId);
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planSetId);
         if (planSet == null) {
            return null;
         }

         BuildingPlanSet.LevelDef levelDef = planSet.getLevel(variant, upgradeLevel);
         return levelDef == null ? null : ModCultures.getBuildingPlan(levelDef.planId());
      } else {
         return null;
      }
   }

   public static int resolveGroundLevel(String cultureKey, String buildingId, String variant, int upgradeLevel, int fallback) {
      BuildingPlan plan = resolvePlan(cultureKey, buildingId, variant, upgradeLevel);
      return plan != null ? plan.groundLevel() : fallback;
   }

   public static ContentFs cultureFsFor(ResourceLocation culture) {
      if (culture == null) {
         throw new IllegalArgumentException("culture null");
      } else {
         return CustomContentIndex.current().forCulture(culture.getPath());
      }
   }

   public static ContentFs cultureFsFor(BuildingPlan plan) {
      if (plan == null) {
         throw new IllegalArgumentException("plan null");
      } else {
         return cultureFsFor(plan.culture());
      }
   }

   public static ContentFs cultureFsForImport(ResourceLocation culture) {
      if (culture == null) {
         throw new IllegalArgumentException("culture null");
      }

      ContentFs primary = CustomContentIndex.current().forCulture(culture.getPath());
      ContentFs fallback = CustomContentIndex.current().exportedFs().sub("cultures/" + culture.getPath());
      return new ChainedContentFs(primary, fallback);
   }

   public static Optional<StructureTemplate> loadFromPath(String nbtPath, ServerLevel level, ContentFs cultureFs) {
      if (level == null) {
         throw new IllegalArgumentException("level null");
      } else {
         return loadFromPath(nbtPath, level.holderLookup(Registries.BLOCK), cultureFs);
      }
   }

   static Optional<StructureTemplate> loadFromPath(String nbtPath, HolderLookup<Block> blocks, ContentFs cultureFs) {
      if (nbtPath == null) {
         throw new IllegalArgumentException("nbtPath null");
      }

      if (blocks == null) {
         throw new IllegalArgumentException("blocks null");
      }

      if (cultureFs == null) {
         throw new IllegalArgumentException("cultureFs null");
      }

      Optional<Resource> nbt = cultureFs.findFirst(nbtPath + ".nbt");
      return nbt.isEmpty() ? Optional.empty() : Optional.of(loadStructureFromResource(nbt.get(), blocks));
   }

   public static StructureTemplate loadStructureFromResource(Resource res, ServerLevel level) {
      if (level == null) {
         throw new IllegalArgumentException("level null");
      } else {
         return loadStructureFromResource(res, level.holderLookup(Registries.BLOCK));
      }
   }

   static StructureTemplate loadStructureFromResource(Resource res, HolderLookup<Block> blocks) {
      if (res == null) {
         throw new IllegalArgumentException("res null");
      }

      if (blocks == null) {
         throw new IllegalArgumentException("blocks null");
      }

      try (InputStream is = res.open()) {
         CompoundTag nbt = NbtIo.readCompressed(is, accounterFor(res.kind()));
         HearthTemplateSanitizer.sanitize(nbt, null);
         StructureTemplate tmpl = new StructureTemplate();
         tmpl.load(blocks, nbt);
         return tmpl;
      } catch (IOException e) {
         throw new UncheckedIOException("Failed to load NBT " + res.relPath() + " from " + res.source().displayName(), e);
      }
   }

   public static NbtAccounter accounterFor(SourceKind kind) {
      return switch (kind) {
         case CLASSPATH, STANDARD -> NbtAccounter.unlimitedHeap();
         case SUBMOD -> NbtAccounter.create(10000000L);
      };
   }
}
