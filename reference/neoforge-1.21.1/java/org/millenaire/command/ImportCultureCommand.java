package org.millenaire.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.block.ImportTableBlock;
import org.millenaire.block.ImportTableBlockEntity;
import org.millenaire.block.ModBlocks;
import org.millenaire.building.BuildingImporter;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.ModCultures;
import org.millenaire.world.TerrainPreparer;

public final class ImportCultureCommand {
   private static final int BUILDING_GROUP_SPACING = 5;
   private static final SuggestionProvider<CommandSourceStack> CULTURE_SUGGESTIONS = (ctx, builder) -> SharedSuggestionProvider.suggest(
      ModCultures.getAllCultures().keySet().stream().map(ResourceLocation::getPath), builder
   );

   private ImportCultureCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(
         Commands.literal("importculture")
            .then(
               ((RequiredArgumentBuilder)Commands.argument("culture", StringArgumentType.string())
                     .suggests(CULTURE_SUGGESTIONS)
                     .executes(ImportCultureCommand::execute))
                  .then(
                     Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer()).executes(ImportCultureCommand::execute))
                  )
            )
      );
   }

   private static int execute(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = source.getPlayer();
      if (player == null) {
         source.sendFailure(Component.literal("This command must be run by a player."));
         return 0;
      }

      String cultureArg = StringArgumentType.getString(ctx, "culture");
      ResourceLocation cultureId = resolveCultureId(cultureArg);
      if (ModCultures.getCulture(cultureId) == null) {
         source.sendFailure(
            Component.literal(
               "Unknown culture: "
                  + cultureArg
                  + ". Available: "
                  + ModCultures.getAllCultures().keySet().stream().map(ResourceLocation::getPath).sorted().toList()
            )
         );
         return 0;
      }

      List<BuildingPlanSet> planSets = ModCultures.getAllBuildingPlanSets()
         .values()
         .stream()
         .filter(ps -> cultureId.equals(ps.culture()))
         .filter(ps -> !ps.isSubBuilding())
         .sorted(Comparator.comparing(BuildingPlanSet::buildingId))
         .toList();
      if (planSets.isEmpty()) {
         source.sendFailure(Component.literal("No top-level building plan sets found for culture: " + cultureId));
         return 0;
      }

      ServerLevel level = player.serverLevel();
      BlockPos startPos = resolveStartPos(ctx, player, level);
      int nextBuildingMinX = startPos.getX();
      int importedBuildings = 0;
      int importedPlans = 0;
      int failedCount = 0;

      for (BuildingPlanSet planSet : planSets) {
         BuildingPlan firstPlan = resolveFirstPlan(planSet);
         if (firstPlan == null) {
            source.sendSystemMessage(Component.literal("§c[ImportCulture] No usable plan for " + planSet.id()));
            failedCount++;
         } else {
            BlockPos tablePos = BuildingImporter.tablePosForBuildingAabb(nextBuildingMinX, startPos.getZ(), startPos.getY());
            ImportTableBlockEntity table = placeImportTable(level, tablePos, player.getDirection());
            if (table == null) {
               source.sendSystemMessage(
                  Component.literal("§c[ImportCulture] Failed to create ImportTable for " + planSet.id() + " at " + tablePos.toShortString())
               );
               failedCount++;
               nextBuildingMinX += 5;
            } else {
               source.sendSystemMessage(Component.literal("§f[ImportCulture] Importing " + planSet.id()));
               BuildingImporter.ImportAabb aabb = BuildingImporter.importAllVariantsFromCulture(
                  level, table, player, cultureId.toString(), planSet.buildingId()
               );
               if (aabb != null) {
                  nextBuildingMinX = aabb.maxX() + 1 + 5;
                  importedBuildings++;
                  importedPlans += countPlans(planSet);
               } else {
                  failedCount++;
                  nextBuildingMinX += 5;
               }
            }
         }
      }

      int importedBuildingsFinal = importedBuildings;
      int importedPlansFinal = importedPlans;
      int failedFinal = failedCount;
      source.sendSuccess(
         () -> Component.literal(
            "Imported culture "
               + cultureId.getPath()
               + " (buildings="
               + importedBuildingsFinal
               + "/"
               + planSets.size()
               + ", plans="
               + importedPlansFinal
               + ", failed="
               + failedFinal
               + ")"
         ),
         true
      );
      return 1;
   }

   private static BlockPos resolveStartPos(CommandContext<CommandSourceStack> ctx, ServerPlayer player, ServerLevel level) {
      Integer x = getOptionalInt(ctx, "x");
      Integer z = getOptionalInt(ctx, "z");
      if (x != null && z != null) {
         int y = TerrainPreparer.getGroundHeight(level, x, z);
         return new BlockPos(x, y, z);
      } else {
         BlockPos inFront = player.blockPosition().relative(player.getDirection(), 2);
         int y = TerrainPreparer.getGroundHeight(level, inFront.getX(), inFront.getZ());
         return new BlockPos(inFront.getX(), y, inFront.getZ());
      }
   }

   private static ImportTableBlockEntity placeImportTable(ServerLevel level, BlockPos pos, Direction playerDirection) {
      Direction facing = playerDirection.getOpposite();
      level.setBlock(pos, (BlockState)((ImportTableBlock)ModBlocks.IMPORT_TABLE.get()).defaultBlockState().setValue(ImportTableBlock.FACING, facing), 2);
      if (level.getBlockEntity(pos) instanceof ImportTableBlockEntity table) {
         table.setOrientation(0);
         table.setImportMockBlocks(true);
         return table;
      } else {
         return null;
      }
   }

   private static int countPlans(BuildingPlanSet planSet) {
      return planSet.variants().values().stream().mapToInt(List::size).sum();
   }

   @Nullable
   private static BuildingPlan resolveFirstPlan(BuildingPlanSet planSet) {
      for (String variantKey : planSet.variants().keySet().stream().sorted().toList()) {
         List<BuildingPlanSet.LevelDef> levels = planSet.variants().get(variantKey);
         if (levels != null && !levels.isEmpty()) {
            BuildingPlanSet.LevelDef first = levels.stream().min(Comparator.comparingInt(BuildingPlanSet.LevelDef::level)).orElse(null);
            if (first != null) {
               BuildingPlan plan = ModCultures.getBuildingPlan(first.planId());
               if (plan != null) {
                  return plan;
               }
            }
         }
      }

      return null;
   }

   private static ResourceLocation resolveCultureId(String arg) {
      if (arg.contains(":")) {
         ResourceLocation parsed = ResourceLocation.tryParse(arg);
         return parsed != null ? parsed : ResourceLocation.fromNamespaceAndPath("millenaire", arg);
      } else {
         return ResourceLocation.fromNamespaceAndPath("millenaire", arg);
      }
   }

   private static Integer getOptionalInt(CommandContext<CommandSourceStack> ctx, String name) {
      try {
         return IntegerArgumentType.getInteger(ctx, name);
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
