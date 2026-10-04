package org.millenaire.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Map.Entry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.TemplateLoader;
import org.millenaire.culture.ModCultures;
import org.millenaire.world.BuildingPlacer;
import org.slf4j.Logger;

public final class RoundtripCommand {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path OUTPUT_DIR = Path.of("millenaire-export", "roundtrip");
   private static final Random RANDOM = new Random();
   private static final int PLACE_X = 500;
   private static final int PLACE_Z = 500;
   private static final int BASE_Y = 100;
   private static final SuggestionProvider<CommandSourceStack> PLAN_SET_SUGGESTIONS = (ctx, builder) -> SharedSuggestionProvider.suggest(
      ModCultures.getAllBuildingPlanSets().keySet().stream().map(ResourceLocation::getPath), builder
   );
   private static final SuggestionProvider<CommandSourceStack> VARIANT_SUGGESTIONS = (ctx, builder) -> {
      BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(resolvePlanSetId(ctx));
      return planSet != null ? SharedSuggestionProvider.suggest(planSet.variants().keySet().stream(), builder) : builder.buildFuture();
   };
   private static final String AIR = "minecraft:air";

   private RoundtripCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(
         Commands.literal("roundtrip")
            .then(
               ((RequiredArgumentBuilder)Commands.argument("plan_set", ResourceLocationArgument.id())
                     .suggests(PLAN_SET_SUGGESTIONS)
                     .executes(ctx -> execute(ctx, null, 0)))
                  .then(
                     ((RequiredArgumentBuilder)Commands.argument("variant", StringArgumentType.string())
                           .suggests(VARIANT_SUGGESTIONS)
                           .executes(ctx -> execute(ctx, StringArgumentType.getString(ctx, "variant"), 0)))
                        .then(
                           Commands.argument("level", IntegerArgumentType.integer(0))
                              .executes(ctx -> execute(ctx, StringArgumentType.getString(ctx, "variant"), IntegerArgumentType.getInteger(ctx, "level")))
                        )
                  )
            )
      );
   }

   private static int execute(CommandContext<CommandSourceStack> ctx, String variant, int level) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel serverLevel = source.getServer().getLevel(Level.OVERWORLD);
      if (serverLevel == null) {
         source.sendFailure(Component.literal("No Overworld available."));
         return 0;
      }

      ResourceLocation planSetId = resolvePlanSetId(ctx);
      BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planSetId);
      if (planSet == null) {
         source.sendFailure(Component.literal("Unknown BuildingPlanSet: " + planSetId));
         return 0;
      }

      if (variant == null) {
         variant = planSet.pickRandomVariant(RANDOM);
      }

      if (!planSet.variants().containsKey(variant)) {
         source.sendFailure(Component.literal("Unknown variant: " + variant));
         return 0;
      }

      BuildingPlan[] plans = new BuildingPlan[level + 1];

      for (int i = 0; i <= level; i++) {
         BuildingPlanSet.LevelDef def = planSet.getLevel(variant, i);
         if (def == null) {
            source.sendFailure(Component.literal("Level " + i + " does not exist."));
            return 0;
         }

         plans[i] = ModCultures.getBuildingPlan(def.planId());
         if (plans[i] == null) {
            source.sendFailure(Component.literal("BuildingPlan not found: " + def.planId()));
            return 0;
         }
      }

      BuildingPlan level0Plan = plans[0];
      BuildingPlan targetPlan = plans[level];
      int maxWidth = 0;
      int maxHeight = 0;
      int maxDepth = 0;
      int minGroundLevel = 0;

      for (BuildingPlan p : plans) {
         maxWidth = Math.max(maxWidth, p.width());
         maxHeight = Math.max(maxHeight, p.height());
         maxDepth = Math.max(maxDepth, p.depth());
         minGroundLevel = Math.min(minGroundLevel, p.groundLevel());
      }

      int margin = 5;
      int foundationDepth = Math.abs(minGroundLevel) + 5;

      for (int x = 500 - margin; x < 500 + maxWidth + margin; x++) {
         for (int z = 500 - margin; z < 500 + maxDepth + margin; z++) {
            for (int y = 100 - foundationDepth; y < 100; y++) {
               serverLevel.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 2);
            }

            serverLevel.setBlock(new BlockPos(x, 100, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);

            for (int y = 101; y < 100 + maxHeight + 20; y++) {
               serverLevel.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
         }
      }

      BlockPos origin = new BlockPos(500, 100 + level0Plan.groundLevel(), 500);
      if (!BuildingPlacer.placeInstantly(serverLevel, level0Plan, origin, Rotation.NONE, false)) {
         source.sendFailure(Component.literal("Failed to place level 0."));
         return 0;
      }

      for (int lvl = 1; lvl <= level; lvl++) {
         BlockPos upgOrigin = new BlockPos(500, 100 + plans[lvl].groundLevel(), 500);
         BuildingPlacer.placeUpgradeInstantly(serverLevel, plans[lvl], upgOrigin, Rotation.NONE, false);
      }

      Vec3i captureSize = new Vec3i(maxWidth, maxHeight, maxDepth);
      BlockPos captureOrigin = new BlockPos(500, 100 + minGroundLevel, 500);
      StructureTemplate recaptured = new StructureTemplate();
      recaptured.fillFromWorld(serverLevel, captureOrigin, captureSize, false, null);
      CompoundTag recapturedNbt = recaptured.save(new CompoundTag());
      CompoundTag referenceNbt;
      if (level == 0) {
         Optional<StructureTemplate> templateOpt = TemplateLoader.load(level0Plan, serverLevel, TemplateLoader.cultureFsForImport(level0Plan.culture()));
         referenceNbt = templateOpt.<CompoundTag>map(t -> t.save(new CompoundTag())).orElse(new CompoundTag());
      } else {
         Optional<StructureTemplate> templateOpt = TemplateLoader.load(targetPlan, serverLevel, TemplateLoader.cultureFsForImport(targetPlan.culture()));
         referenceNbt = templateOpt.<CompoundTag>map(t -> t.save(new CompoundTag())).orElse(new CompoundTag());
      }

      JsonObject diff = analyzeNbtDiff(referenceNbt, recapturedNbt, planSetId, variant, level);

      try {
         ensureDir(OUTPUT_DIR);
         String baseName = planSetId.getPath().replace("/", "_") + "_" + variant + "_" + level;
         NbtIo.writeCompressed(referenceNbt, OUTPUT_DIR.resolve(baseName + "_original.nbt"));
         NbtIo.writeCompressed(recapturedNbt, OUTPUT_DIR.resolve(baseName + "_roundtrip.nbt"));
         Path diffPath = OUTPUT_DIR.resolve(baseName + "_diff.json");
         Files.writeString(diffPath, GSON.toJson(diff));
         int paletteOriginal = diff.get("palette_original_count").getAsInt();
         int paletteRoundtrip = diff.get("palette_roundtrip_count").getAsInt();
         int blocksOriginal = diff.get("blocks_original_count").getAsInt();
         int blocksRoundtrip = diff.get("blocks_roundtrip_count").getAsInt();
         int addedProps = diff.getAsJsonObject("categories").getAsJsonArray("default_properties_added").size();
         int addedBlocks = diff.getAsJsonObject("categories").getAsJsonArray("blocks_added").size();
         int removedBlocks = diff.getAsJsonObject("categories").getAsJsonArray("blocks_removed").size();
         int changedBlocks = diff.getAsJsonObject("categories").getAsJsonArray("blocks_changed").size();
         String summary = String.format(
            "Roundtrip %s %s L%d: palette %d→%d, blocks %d→%d, +props=%d, +blocks=%d, -blocks=%d, changed=%d → %s",
            planSetId.getPath(),
            variant,
            level,
            paletteOriginal,
            paletteRoundtrip,
            blocksOriginal,
            blocksRoundtrip,
            addedProps,
            addedBlocks,
            removedBlocks,
            changedBlocks,
            diffPath
         );
         source.sendSuccess(() -> Component.literal(summary), false);
         LOGGER.info(summary);
         return 1;
      } catch (IOException e) {
         source.sendFailure(Component.literal("IO error: " + e.getMessage()));
         LOGGER.error("Roundtrip IO error", e);
         return 0;
      }
   }

   private static JsonObject analyzeNbtDiff(CompoundTag original, CompoundTag roundtrip, ResourceLocation planSetId, String variant, int lvl) {
      JsonObject result = new JsonObject();
      result.addProperty("plan_set", planSetId.toString());
      result.addProperty("variant", variant);
      result.addProperty("level", lvl);
      ListTag origPalette = original.getList("palette", 10);
      ListTag rtPalette = roundtrip.getList("palette", 10);
      result.addProperty("palette_original_count", origPalette.size());
      result.addProperty("palette_roundtrip_count", rtPalette.size());
      Map<String, RoundtripCommand.BlockEntry> origBlocks = parseBlocks(original);
      Map<String, RoundtripCommand.BlockEntry> rtBlocks = parseBlocks(roundtrip);
      result.addProperty("blocks_original_count", origBlocks.size());
      result.addProperty("blocks_roundtrip_count", rtBlocks.size());
      JsonArray defaultPropsAdded = new JsonArray();
      JsonArray blocksAdded = new JsonArray();
      JsonArray blocksRemoved = new JsonArray();
      JsonArray blocksChanged = new JsonArray();
      JsonArray nbtAdded = new JsonArray();
      JsonArray nbtChanged = new JsonArray();
      Iterator categories = rtBlocks.entrySet().iterator();

      while (true) {
         String pos;
         RoundtripCommand.BlockEntry rtBlock;
         RoundtripCommand.BlockEntry origBlock;
         do {
            if (!categories.hasNext()) {
               for (Entry<String, RoundtripCommand.BlockEntry> entry : origBlocks.entrySet()) {
                  if (!rtBlocks.containsKey(entry.getKey()) && !isAir(entry.getValue().stateKey)) {
                     JsonObject removed = new JsonObject();
                     removed.addProperty("pos", entry.getKey());
                     removed.addProperty("state", entry.getValue().stateKey);
                     blocksRemoved.add(removed);
                  }
               }

               JsonObject categoriesx = new JsonObject();
               categoriesx.add("default_properties_added", defaultPropsAdded);
               categoriesx.add("blocks_added", blocksAdded);
               categoriesx.add("blocks_removed", blocksRemoved);
               categoriesx.add("blocks_changed", blocksChanged);
               categoriesx.add("nbt_added", nbtAdded);
               categoriesx.add("nbt_changed", nbtChanged);
               result.add("categories", categoriesx);
               JsonObject propStats = new JsonObject();
               Map<String, Integer> propCounts = new HashMap<>();

               for (int i = 0; i < defaultPropsAdded.size(); i++) {
                  JsonObject entry = defaultPropsAdded.get(i).getAsJsonObject();
                  JsonObject props = entry.getAsJsonObject("added_properties");

                  for (String key : props.keySet()) {
                     String propKey = key + "=" + props.get(key).getAsString();
                     propCounts.merge(propKey, 1, Integer::sum);
                  }
               }

               propCounts.forEach(propStats::addProperty);
               result.add("default_property_stats", propStats);
               return result;
            }

            Entry<String, RoundtripCommand.BlockEntry> entry = (Entry<String, RoundtripCommand.BlockEntry>)categories.next();
            pos = entry.getKey();
            rtBlock = entry.getValue();
            if (!isAir(rtBlock.stateKey)) {
               break;
            }

            origBlock = origBlocks.get(pos);
         } while (origBlock == null || isAir(origBlock.stateKey));

         origBlock = origBlocks.get(pos);
         if (origBlock == null) {
            if (!isAir(rtBlock.stateKey)) {
               JsonObject added = new JsonObject();
               added.addProperty("pos", pos);
               added.addProperty("state", rtBlock.stateKey);
               blocksAdded.add(added);
            }
         } else {
            if (!origBlock.stateKey.equals(rtBlock.stateKey)) {
               if (isAir(origBlock.stateKey) && isAir(rtBlock.stateKey)) {
                  continue;
               }

               String origBase = extractBlockName(origBlock.stateKey);
               String rtBase = extractBlockName(rtBlock.stateKey);
               if (origBase.equals(rtBase)) {
                  Map<String, String> origProps = extractProperties(origBlock.stateKey);
                  Map<String, String> rtProps = extractProperties(rtBlock.stateKey);
                  Map<String, String> addedProperties = new LinkedHashMap<>();

                  for (Entry<String, String> prop : rtProps.entrySet()) {
                     if (!origProps.containsKey(prop.getKey())) {
                        addedProperties.put(prop.getKey(), prop.getValue());
                     }
                  }

                  Map<String, String> changedProperties = new LinkedHashMap<>();

                  for (Entry<String, String> prop : rtProps.entrySet()) {
                     String origVal = origProps.get(prop.getKey());
                     if (origVal != null && !origVal.equals(prop.getValue())) {
                        changedProperties.put(prop.getKey(), origVal + " → " + prop.getValue());
                     }
                  }

                  if (!addedProperties.isEmpty() && changedProperties.isEmpty()) {
                     JsonObject propDiff = new JsonObject();
                     propDiff.addProperty("pos", pos);
                     propDiff.addProperty("block", origBase);
                     JsonObject propsObj = new JsonObject();
                     addedProperties.forEach(propsObj::addProperty);
                     propDiff.add("added_properties", propsObj);
                     defaultPropsAdded.add(propDiff);
                  } else {
                     JsonObject change = new JsonObject();
                     change.addProperty("pos", pos);
                     change.addProperty("original", origBlock.stateKey);
                     change.addProperty("roundtrip", rtBlock.stateKey);
                     if (!addedProperties.isEmpty()) {
                        JsonObject propsObj = new JsonObject();
                        addedProperties.forEach(propsObj::addProperty);
                        change.add("added_properties", propsObj);
                     }

                     if (!changedProperties.isEmpty()) {
                        JsonObject propsObj = new JsonObject();
                        changedProperties.forEach(propsObj::addProperty);
                        change.add("changed_properties", propsObj);
                     }

                     blocksChanged.add(change);
                  }
               } else {
                  JsonObject change = new JsonObject();
                  change.addProperty("pos", pos);
                  change.addProperty("original", origBlock.stateKey);
                  change.addProperty("roundtrip", rtBlock.stateKey);
                  blocksChanged.add(change);
               }
            }

            if (rtBlock.nbt != null && origBlock.nbt == null) {
               JsonObject nbtAdd = new JsonObject();
               nbtAdd.addProperty("pos", pos);
               nbtAdd.addProperty("block", extractBlockName(rtBlock.stateKey));
               nbtAdd.addProperty("nbt_keys", rtBlock.nbt.getAllKeys().toString());
               nbtAdded.add(nbtAdd);
            } else if (rtBlock.nbt != null && origBlock.nbt != null && !rtBlock.nbt.equals(origBlock.nbt)) {
               JsonObject nbtDiff = new JsonObject();
               nbtDiff.addProperty("pos", pos);
               nbtDiff.addProperty("block", extractBlockName(rtBlock.stateKey));
               JsonObject keyDiffs = new JsonObject();

               for (String key : rtBlock.nbt.getAllKeys()) {
                  Tag origTag = origBlock.nbt.get(key);
                  Tag rtTag = rtBlock.nbt.get(key);
                  if (origTag == null) {
                     keyDiffs.addProperty(key, "ADDED: " + rtTag);
                  } else if (!origTag.equals(rtTag)) {
                     keyDiffs.addProperty(key, origTag + " → " + rtTag);
                  }
               }

               for (String key : origBlock.nbt.getAllKeys()) {
                  if (!rtBlock.nbt.contains(key)) {
                     keyDiffs.addProperty(key, "REMOVED");
                  }
               }

               nbtDiff.add("diffs", keyDiffs);
               nbtChanged.add(nbtDiff);
            }
         }
      }
   }

   private static boolean isAir(String stateKey) {
      return stateKey.equals("minecraft:air") || stateKey.equals("minecraft:cave_air") || stateKey.equals("minecraft:void_air");
   }

   private static Map<String, RoundtripCommand.BlockEntry> parseBlocks(CompoundTag nbt) {
      Map<String, RoundtripCommand.BlockEntry> result = new LinkedHashMap<>();
      ListTag palette = nbt.getList("palette", 10);
      ListTag blocks = nbt.getList("blocks", 10);
      String[] paletteKeys = new String[palette.size()];

      for (int i = 0; i < palette.size(); i++) {
         paletteKeys[i] = blockStateToString(palette.getCompound(i));
      }

      for (int i = 0; i < blocks.size(); i++) {
         CompoundTag block = blocks.getCompound(i);
         int stateIdx = block.getInt("state");
         ListTag posList = block.getList("pos", 3);
         String posKey = posList.getInt(0) + "," + posList.getInt(1) + "," + posList.getInt(2);
         String stateKey = stateIdx < paletteKeys.length ? paletteKeys[stateIdx] : "UNKNOWN_" + stateIdx;
         CompoundTag blockNbt = block.contains("nbt", 10) ? block.getCompound("nbt") : null;
         result.put(posKey, new RoundtripCommand.BlockEntry(stateKey, blockNbt));
      }

      return result;
   }

   private static String blockStateToString(CompoundTag paletteEntry) {
      String name = paletteEntry.getString("Name");
      if (!paletteEntry.contains("Properties", 10)) {
         return name;
      }

      CompoundTag props = paletteEntry.getCompound("Properties");
      if (props.isEmpty()) {
         return name;
      }

      StringBuilder sb = new StringBuilder(name).append('[');
      boolean first = true;

      for (String key : props.getAllKeys().stream().sorted().toList()) {
         if (!first) {
            sb.append(',');
         }

         sb.append(key).append('=').append(props.getString(key));
         first = false;
      }

      sb.append(']');
      return sb.toString();
   }

   private static String extractBlockName(String stateKey) {
      int bracket = stateKey.indexOf(91);
      return bracket < 0 ? stateKey : stateKey.substring(0, bracket);
   }

   private static Map<String, String> extractProperties(String stateKey) {
      Map<String, String> result = new LinkedHashMap<>();
      int start = stateKey.indexOf(91);
      int end = stateKey.lastIndexOf(93);
      if (start >= 0 && end >= 0) {
         String propsStr = stateKey.substring(start + 1, end);
         if (propsStr.isEmpty()) {
            return result;
         }

         for (String pair : propsStr.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
               result.put(kv[0], kv[1]);
            }
         }

         return result;
      } else {
         return result;
      }
   }

   private static ResourceLocation resolvePlanSetId(CommandContext<CommandSourceStack> ctx) {
      ResourceLocation raw = ResourceLocationArgument.getId(ctx, "plan_set");
      return raw.getNamespace().equals("minecraft") ? ResourceLocation.fromNamespaceAndPath("millenaire", raw.getPath()) : raw;
   }

   private static void ensureDir(Path dir) throws IOException {
      if (!Files.exists(dir)) {
         Files.createDirectories(dir);
      }
   }

   private record BlockEntry(String stateKey, CompoundTag nbt) {
   }
}
