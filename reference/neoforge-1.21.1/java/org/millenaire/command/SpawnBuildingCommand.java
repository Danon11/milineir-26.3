package org.millenaire.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.ClearMargins;
import org.millenaire.building.HearthLightingUtil;
import org.millenaire.culture.ModCultures;
import org.millenaire.world.BuildingPlacer;
import org.millenaire.world.TerrainPreparer;

public final class SpawnBuildingCommand {
   private static final Random RANDOM = new Random();
   private static final SuggestionProvider<CommandSourceStack> PLAN_SET_SUGGESTIONS = (ctx, builder) -> SharedSuggestionProvider.suggest(
      ModCultures.getAllBuildingPlanSets().keySet().stream().map(id -> id.getPath().replace('/', '_')), builder
   );
   private static final SuggestionProvider<CommandSourceStack> VARIANT_SUGGESTIONS = (ctx, builder) -> {
      BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(resolvePlanSetId(ctx));
      return planSet != null ? SharedSuggestionProvider.suggest(planSet.variants().keySet().stream(), builder) : builder.buildFuture();
   };
   private static final SuggestionProvider<CommandSourceStack> ROTATION_SUGGESTIONS = (ctx, builder) -> SharedSuggestionProvider.suggest(
      Arrays.stream(Rotation.values()).map(r -> r.name().toLowerCase()), builder
   );
   private static final SuggestionProvider<CommandSourceStack> LEVEL_SUGGESTIONS = (ctx, builder) -> {
      BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(resolvePlanSetId(ctx));
      if (planSet == null) {
         return builder.buildFuture();
      }

      String variant = StringArgumentType.getString(ctx, "variant");
      int count = planSet.getLevelCount(variant);
      List<String> levels = new ArrayList<>();

      for (int i = 0; i < count; i++) {
         levels.add(String.valueOf(i));
      }

      return SharedSuggestionProvider.suggest(levels, builder);
   };

   private SpawnBuildingCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> herePlanSet = (RequiredArgumentBuilder<CommandSourceStack, ResourceLocation>)((RequiredArgumentBuilder)Commands.argument(
               "plan_set", ResourceLocationArgument.id()
            )
            .suggests(PLAN_SET_SUGGESTIONS)
            .executes(ctx -> executeHere(ctx)))
         .then(
            ((RequiredArgumentBuilder)Commands.argument("variant", StringArgumentType.string()).suggests(VARIANT_SUGGESTIONS).executes(ctx -> executeHere(ctx)))
               .then(
                  ((RequiredArgumentBuilder)Commands.argument("level", IntegerArgumentType.integer(0))
                        .suggests(LEVEL_SUGGESTIONS)
                        .executes(ctx -> executeHere(ctx)))
                     .then(
                        ((RequiredArgumentBuilder)Commands.argument("rotation", StringArgumentType.string())
                              .suggests(ROTATION_SUGGESTIONS)
                              .executes(ctx -> executeHere(ctx)))
                           .then(Commands.argument("showMockBlocks", BoolArgumentType.bool()).executes(ctx -> executeHere(ctx)))
                     )
               )
         );
      RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> atPlanSet = (RequiredArgumentBuilder<CommandSourceStack, ResourceLocation>)((RequiredArgumentBuilder)Commands.argument(
               "plan_set", ResourceLocationArgument.id()
            )
            .suggests(PLAN_SET_SUGGESTIONS)
            .executes(ctx -> executeAt(ctx)))
         .then(
            ((RequiredArgumentBuilder)Commands.argument("variant", StringArgumentType.string()).suggests(VARIANT_SUGGESTIONS).executes(ctx -> executeAt(ctx)))
               .then(
                  ((RequiredArgumentBuilder)Commands.argument("level", IntegerArgumentType.integer(0))
                        .suggests(LEVEL_SUGGESTIONS)
                        .executes(ctx -> executeAt(ctx)))
                     .then(
                        ((RequiredArgumentBuilder)Commands.argument("rotation", StringArgumentType.string())
                              .suggests(ROTATION_SUGGESTIONS)
                              .executes(ctx -> executeAt(ctx)))
                           .then(Commands.argument("showMockBlocks", BoolArgumentType.bool()).executes(ctx -> executeAt(ctx)))
                     )
               )
         );
      LiteralArgumentBuilder<CommandSourceStack> atBranch = (LiteralArgumentBuilder<CommandSourceStack>)Commands.literal("at")
         .then(
            Commands.argument("x", IntegerArgumentType.integer())
               .then(Commands.argument("y", IntegerArgumentType.integer()).then(Commands.argument("z", IntegerArgumentType.integer()).then(atPlanSet)))
         );
      parent.then(((LiteralArgumentBuilder)Commands.literal("building").then(herePlanSet)).then(atBranch));
   }

   private static int executeHere(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayer();
      if (player == null) {
         ((CommandSourceStack)ctx.getSource())
            .sendFailure(Component.literal("This command must be run by a player. Use: /millenaire building at <x> <y> <z> <plan_set>"));
         return 0;
      } else {
         BlockPos playerPos = player.blockPosition();
         Direction direction = player.getDirection();
         BlockPos spawnPos = playerPos.relative(direction, 2);
         return doSpawn(ctx, player.serverLevel(), spawnPos);
      }
   }

   private static int executeAt(CommandContext<CommandSourceStack> ctx) {
      int x = IntegerArgumentType.getInteger(ctx, "x");
      int y = IntegerArgumentType.getInteger(ctx, "y");
      int z = IntegerArgumentType.getInteger(ctx, "z");
      return doSpawn(ctx, ((CommandSourceStack)ctx.getSource()).getLevel(), new BlockPos(x, y, z));
   }

   private static int doSpawn(CommandContext<CommandSourceStack> ctx, ServerLevel level, BlockPos pos) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ResourceLocation planSetId = resolvePlanSetId(ctx);
      BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planSetId);
      if (planSet == null) {
         List<String> available = ModCultures.getAllBuildingPlanSets().keySet().stream().<String>map(ResourceLocation::getPath).sorted().toList();
         source.sendFailure(Component.literal("Unknown BuildingPlanSet: " + planSetId + ". Available: " + available));
         return 0;
      }

      String variant = getOptionalString(ctx, "variant");
      if (variant == null) {
         variant = planSet.pickRandomVariant(RANDOM);
      } else if (!planSet.variants().containsKey(variant)) {
         source.sendFailure(Component.literal("Unknown variant '" + variant + "' for " + planSetId + ". Available: " + planSet.variants().keySet()));
         return 0;
      }

      int requestedLevel = getOptionalInt(ctx, "level", 0);
      BuildingPlanSet.LevelDef levelDef = planSet.getLevel(variant, requestedLevel);
      if (levelDef == null) {
         int maxLevel = planSet.getLevelCount(variant) - 1;
         source.sendFailure(Component.literal("Level " + requestedLevel + " does not exist for " + planSetId + " variant " + variant + ". Max: " + maxLevel));
         return 0;
      }

      BuildingPlan plan = ModCultures.getBuildingPlan(levelDef.planId());
      if (plan == null) {
         source.sendFailure(Component.literal("BuildingPlan not found: " + levelDef.planId()));
         return 0;
      }

      Rotation rotation = Rotation.NONE;
      String rotationArg = getOptionalString(ctx, "rotation");
      if (rotationArg != null) {
         rotation = parseRotation(rotationArg);
         if (rotation == null) {
            source.sendFailure(Component.literal("Unknown rotation: " + rotationArg + ". Values: none, clockwise_90, clockwise_180, counterclockwise_90"));
            return 0;
         }
      }

      boolean showMockBlocks = getOptionalBool(ctx, "showMockBlocks", false);
      BuildingPlanSet.LevelDef level0Def = planSet.getLevel(variant, 0);
      BuildingPlan level0Plan = level0Def != null ? ModCultures.getBuildingPlan(level0Def.planId()) : plan;
      if (level0Plan == null) {
         level0Plan = plan;
      }

      ClearMargins margins = planSet.clearMargins();
      boolean[][] snowMap = TerrainPreparer.checkForSnow(level, pos, level0Plan.width(), level0Plan.depth(), rotation, margins);
      int baseY = TerrainPreparer.clearAndFlatten(
         level, pos, level0Plan.width(), level0Plan.height(), level0Plan.depth(), rotation, level0Plan.groundLevel(), margins
      );
      TerrainPreparer.decayOrphanedLeaves(level, pos, level0Plan.width(), level0Plan.depth(), rotation, baseY, margins);
      int placementY = baseY + level0Plan.groundLevel();
      BlockPos origin = new BlockPos(pos.getX(), placementY, pos.getZ());
      if (!BuildingPlacer.placeInstantly(level, level0Plan, origin, rotation, !showMockBlocks)) {
         source.sendFailure(Component.literal("Failed to place level 0 (template not found?)."));
         return 0;
      }

      HearthLightingUtil.lightHearthsInArea(level, origin, new Vec3i(level0Plan.width(), level0Plan.height(), level0Plan.depth()));

      for (int lvl = 1; lvl <= requestedLevel; lvl++) {
         BuildingPlanSet.LevelDef upgradeDef = planSet.getLevel(variant, lvl);
         if (upgradeDef != null) {
            BuildingPlan upgradePlan = ModCultures.getBuildingPlan(upgradeDef.planId());
            if (upgradePlan != null) {
               int upgradePlacementY = baseY + upgradePlan.groundLevel();
               BlockPos upgradeOrigin = new BlockPos(pos.getX(), upgradePlacementY, pos.getZ());
               BuildingPlacer.placeUpgradeInstantly(level, upgradePlan, upgradeOrigin, rotation);
               HearthLightingUtil.lightHearthsInArea(level, upgradeOrigin, new Vec3i(upgradePlan.width(), upgradePlan.height(), upgradePlan.depth()));
            }
         }
      }

      TerrainPreparer.restoreSnow(level, pos, level0Plan.width(), level0Plan.depth(), rotation, snowMap, margins);
      String finalVariant = variant;
      Rotation finalRotation = rotation;
      source.sendSuccess(
         () -> Component.literal(
            "Building placed: "
               + planSetId
               + " [variant="
               + finalVariant
               + ", level=0→"
               + requestedLevel
               + ", rotation="
               + finalRotation.name().toLowerCase()
               + ", mockBlocks="
               + showMockBlocks
               + "] at "
               + origin.toShortString()
               + " (baseY="
               + baseY
               + ", groundLevel="
               + plan.groundLevel()
               + ", dim="
               + plan.width()
               + "×"
               + plan.height()
               + "×"
               + plan.depth()
               + ")"
         ),
         true
      );
      return 1;
   }

   private static ResourceLocation resolvePlanSetId(CommandContext<CommandSourceStack> ctx) {
      ResourceLocation raw = ResourceLocationArgument.getId(ctx, "plan_set");
      ResourceLocation id = raw.getNamespace().equals("minecraft") ? ResourceLocation.fromNamespaceAndPath("millenaire", raw.getPath()) : raw;
      if (ModCultures.getBuildingPlanSet(id) == null) {
         String path = id.getPath();
         int idx = path.indexOf(95);
         if (idx > 0) {
            ResourceLocation converted = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path.substring(0, idx) + "/" + path.substring(idx + 1));
            if (ModCultures.getBuildingPlanSet(converted) != null) {
               return converted;
            }
         }
      }

      return id;
   }

   private static Rotation parseRotation(String arg) {
      return switch (arg.toLowerCase()) {
         case "none" -> Rotation.NONE;
         case "clockwise_90", "cw90", "90" -> Rotation.CLOCKWISE_90;
         case "clockwise_180", "180" -> Rotation.CLOCKWISE_180;
         case "counterclockwise_90", "ccw90", "270" -> Rotation.COUNTERCLOCKWISE_90;
         default -> null;
      };
   }

   private static String getOptionalString(CommandContext<CommandSourceStack> ctx, String name) {
      try {
         return StringArgumentType.getString(ctx, name);
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   private static int getOptionalInt(CommandContext<CommandSourceStack> ctx, String name, int defaultValue) {
      try {
         return IntegerArgumentType.getInteger(ctx, name);
      } catch (IllegalArgumentException e) {
         return defaultValue;
      }
   }

   private static boolean getOptionalBool(CommandContext<CommandSourceStack> ctx, String name, boolean defaultValue) {
      try {
         return BoolArgumentType.getBool(ctx, name);
      } catch (IllegalArgumentException e) {
         return defaultValue;
      }
   }
}
