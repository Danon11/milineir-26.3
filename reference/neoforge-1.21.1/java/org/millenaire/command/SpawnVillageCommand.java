package org.millenaire.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.millenaire.world.VillageSpawner;

public final class SpawnVillageCommand {
   private static final SuggestionProvider<CommandSourceStack> VILLAGE_TYPE_SUGGESTIONS = (ctx, builder) -> {
      String remaining = builder.getRemainingLowerCase();
      ModCultures.getAllVillageTypes().entrySet().stream().filter(e -> !e.getValue().loneBuilding()).forEach(e -> {
         String id = e.getKey().getPath().replace('/', '_');
         if (id.toLowerCase().startsWith(remaining)) {
            builder.suggest(id, () -> ((VillageType)e.getValue()).name());
         }
      });
      return builder.buildFuture();
   };

   private SpawnVillageCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("millenaire")
            .then(
               ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("spawn").requires(source -> source.hasPermission(2)))
                        .executes(ctx -> execute(ctx, null, 0)))
                     .then(
                        ((RequiredArgumentBuilder)Commands.argument("village_type", StringArgumentType.string())
                              .suggests(VILLAGE_TYPE_SUGGESTIONS)
                              .executes(ctx -> execute(ctx, StringArgumentType.getString(ctx, "village_type"), 0)))
                           .then(
                              Commands.argument("completion", IntegerArgumentType.integer(0, 100))
                                 .executes(
                                    ctx -> execute(ctx, StringArgumentType.getString(ctx, "village_type"), IntegerArgumentType.getInteger(ctx, "completion"))
                                 )
                           )
                     ))
                  .then(
                     Commands.literal("at")
                        .then(
                           Commands.argument("x", IntegerArgumentType.integer())
                              .then(
                                 Commands.argument("y", IntegerArgumentType.integer())
                                    .then(
                                       ((RequiredArgumentBuilder)Commands.argument("z", IntegerArgumentType.integer())
                                             .executes(
                                                ctx -> executeAt(
                                                   ctx,
                                                   null,
                                                   IntegerArgumentType.getInteger(ctx, "x"),
                                                   IntegerArgumentType.getInteger(ctx, "y"),
                                                   IntegerArgumentType.getInteger(ctx, "z"),
                                                   0
                                                )
                                             ))
                                          .then(
                                             ((RequiredArgumentBuilder)Commands.argument("village_type", StringArgumentType.string())
                                                   .suggests(VILLAGE_TYPE_SUGGESTIONS)
                                                   .executes(
                                                      ctx -> executeAt(
                                                         ctx,
                                                         StringArgumentType.getString(ctx, "village_type"),
                                                         IntegerArgumentType.getInteger(ctx, "x"),
                                                         IntegerArgumentType.getInteger(ctx, "y"),
                                                         IntegerArgumentType.getInteger(ctx, "z"),
                                                         0
                                                      )
                                                   ))
                                                .then(
                                                   Commands.argument("completion", IntegerArgumentType.integer(0, 100))
                                                      .executes(
                                                         ctx -> executeAt(
                                                            ctx,
                                                            StringArgumentType.getString(ctx, "village_type"),
                                                            IntegerArgumentType.getInteger(ctx, "x"),
                                                            IntegerArgumentType.getInteger(ctx, "y"),
                                                            IntegerArgumentType.getInteger(ctx, "z"),
                                                            IntegerArgumentType.getInteger(ctx, "completion")
                                                         )
                                                      )
                                                )
                                          )
                                    )
                              )
                        )
                  )
            )
      );
   }

   private static int executeAt(CommandContext<CommandSourceStack> ctx, String villageTypeArg, int x, int y, int z, int completion) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      BlockPos center = new BlockPos(x, y, z);
      return doSpawn(source, level, center, villageTypeArg, completion);
   }

   private static int execute(CommandContext<CommandSourceStack> ctx, String villageTypeArg, int completion) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = source.getPlayer();
      if (player == null) {
         source.sendFailure(Component.literal("This command must be run by a player. Use: /millenaire spawn at <x> <y> <z>"));
         return 0;
      } else {
         ServerLevel level = player.serverLevel();
         BlockPos center = player.blockPosition();
         return doSpawn(source, level, center, villageTypeArg, completion);
      }
   }

   private static int doSpawn(CommandSourceStack source, ServerLevel level, BlockPos center, String villageTypeArg, int completion) {
      if (level.dimension() != Level.OVERWORLD) {
         source.sendFailure(Component.literal("Villages can only be spawned in the Overworld."));
         return 0;
      } else {
         ResourceLocation resolvedId = resolveVillageType(villageTypeArg);
         VillageType villageType = ModCultures.getVillageType(resolvedId);
         if (villageType == null) {
            List<String> shortNames = ModCultures.getAllVillageTypes()
               .keySet()
               .stream()
               .filter(id -> !ModCultures.getVillageType(id).loneBuilding())
               .<String>map(ResourceLocation::getPath)
               .toList();
            source.sendFailure(Component.literal("Unknown village type: " + villageTypeArg + ". Available types: " + shortNames));
            return 0;
         } else if (villageType.loneBuilding()) {
            source.sendFailure(Component.literal(villageTypeArg + " is a lone building. Use /millenaire spawn_lb instead."));
            return 0;
         } else {
            ResourceLocation villageTypeId = resolvedId;
            VillageSavedData savedData = VillageSavedData.get(level);
            VillageManager villageManager = savedData.getVillageManager();
            if (villageManager.isWithinMinDistance(center, 100.0)) {
               source.sendFailure(Component.literal("A village already exists within 100 blocks."));
               return 0;
            } else {
               Component failure = VillageSpawner.spawnVillage(level, center, villageType, completion);
               if (failure != null) {
                  source.sendFailure(failure);
                  return 0;
               } else {
                  String completionStr = completion > 0 ? " (completion=" + completion + "%)" : "";
                  source.sendSuccess(
                     () -> Component.literal("Village " + villageTypeId.getPath() + " spawned at " + center.toShortString() + completionStr), true
                  );
                  return 1;
               }
            }
         }
      }
   }

   private static ResourceLocation resolveVillageType(String arg) {
      if (arg == null) {
         arg = "norman_agricole";
      }

      if (arg.contains(":")) {
         return ResourceLocation.parse(arg);
      }

      ResourceLocation direct = ResourceLocation.fromNamespaceAndPath("millenaire", arg);
      if (ModCultures.getVillageType(direct) != null) {
         return direct;
      }

      int idx = arg.indexOf(95);
      if (idx > 0) {
         String asContentId = arg.substring(0, idx) + "/" + arg.substring(idx + 1);
         ResourceLocation converted = ResourceLocation.fromNamespaceAndPath("millenaire", asContentId);
         if (ModCultures.getVillageType(converted) != null) {
            return converted;
         }
      }

      return direct;
   }
}
