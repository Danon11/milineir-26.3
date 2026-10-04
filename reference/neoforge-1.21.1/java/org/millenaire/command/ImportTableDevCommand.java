package org.millenaire.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.millenaire.block.ImportTableBlock;
import org.millenaire.block.ImportTableBlockEntity;
import org.millenaire.block.ModBlocks;
import org.millenaire.building.BuildingExporter;
import org.millenaire.building.BuildingImporter;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.ModCultures;

public final class ImportTableDevCommand {
   private static final SuggestionProvider<CommandSourceStack> CULTURE_KEY_SUGGESTIONS = (ctx, builder) -> SharedSuggestionProvider.suggest(
      ModCultures.getAllCultures().keySet().stream().map(Object::toString), builder
   );

   private ImportTableDevCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(
         ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                 "import-table"
                              )
                              .then(
                                 Commands.literal("place")
                                    .then(pos3d(z -> (RequiredArgumentBuilder<CommandSourceStack, Integer>)z.executes(ImportTableDevCommand::executePlace)))
                              ))
                           .then(
                              Commands.literal("info")
                                 .then(pos3d(z -> (RequiredArgumentBuilder<CommandSourceStack, Integer>)z.executes(ImportTableDevCommand::executeInfo)))
                           ))
                        .then(
                           Commands.literal("load")
                              .then(
                                 pos3d(
                                    z -> (RequiredArgumentBuilder<CommandSourceStack, Integer>)z.then(
                                       Commands.argument("cultureKey", StringArgumentType.string())
                                          .suggests(CULTURE_KEY_SUGGESTIONS)
                                          .then(
                                             ((RequiredArgumentBuilder)Commands.argument("buildingId", StringArgumentType.string())
                                                   .executes(ctx -> executeLoad(ctx, "a", 0)))
                                                .then(
                                                   ((RequiredArgumentBuilder)Commands.argument("variant", StringArgumentType.string())
                                                         .executes(ctx -> executeLoad(ctx, StringArgumentType.getString(ctx, "variant"), 0)))
                                                      .then(
                                                         Commands.argument("level", IntegerArgumentType.integer(0))
                                                            .executes(
                                                               ctx -> executeLoad(
                                                                  ctx,
                                                                  StringArgumentType.getString(ctx, "variant"),
                                                                  IntegerArgumentType.getInteger(ctx, "level")
                                                               )
                                                            )
                                                      )
                                                )
                                          )
                                    )
                                 )
                              )
                        ))
                     .then(
                        Commands.literal("load-all")
                           .then(
                              pos3d(
                                 z -> (RequiredArgumentBuilder<CommandSourceStack, Integer>)z.then(
                                    Commands.argument("cultureKey", StringArgumentType.string())
                                       .suggests(CULTURE_KEY_SUGGESTIONS)
                                       .then(
                                          ((RequiredArgumentBuilder)Commands.argument("buildingId", StringArgumentType.string())
                                                .executes(ctx -> executeLoadAll(ctx, "a")))
                                             .then(
                                                Commands.argument("variant", StringArgumentType.string())
                                                   .executes(ctx -> executeLoadAll(ctx, StringArgumentType.getString(ctx, "variant")))
                                             )
                                       )
                                 )
                              )
                           )
                     ))
                  .then(
                     Commands.literal("export")
                        .then(pos3d(z -> (RequiredArgumentBuilder<CommandSourceStack, Integer>)z.executes(ImportTableDevCommand::executeExport)))
                  ))
               .then(
                  Commands.literal("export-new-level")
                     .then(pos3d(z -> (RequiredArgumentBuilder<CommandSourceStack, Integer>)z.executes(ImportTableDevCommand::executeExportNewLevel)))
               ))
            .then(Commands.literal("create").then(pos3d(z -> (RequiredArgumentBuilder<CommandSourceStack, Integer>)z.then(createTail()))))
      );
   }

   private static RequiredArgumentBuilder<CommandSourceStack, Integer> createTail() {
      return (RequiredArgumentBuilder<CommandSourceStack, Integer>)Commands.argument("length", IntegerArgumentType.integer(1, 256))
         .then(
            Commands.argument("width", IntegerArgumentType.integer(1, 256))
               .then(
                  Commands.argument("startingLevel", IntegerArgumentType.integer(-64, 320))
                     .then(
                        ((RequiredArgumentBuilder)Commands.argument("height", IntegerArgumentType.integer(1, 256)).executes(ctx -> executeCreate(ctx, false)))
                           .then(
                              Commands.argument("clearGround", BoolArgumentType.bool())
                                 .executes(ctx -> executeCreate(ctx, BoolArgumentType.getBool(ctx, "clearGround")))
                           )
                     )
               )
         );
   }

   private static int executePlace(CommandContext<CommandSourceStack> ctx) {
      BlockPos pos = readPos(ctx);
      ServerLevel level = ((CommandSourceStack)ctx.getSource()).getLevel();
      level.setBlock(pos, ((ImportTableBlock)ModBlocks.IMPORT_TABLE.get()).defaultBlockState(), 3);
      BlockEntity be = level.getBlockEntity(pos);
      if (!(be instanceof ImportTableBlockEntity)) {
         ((CommandSourceStack)ctx.getSource())
            .sendFailure(Component.translatable("command.millenaire.importtable.place_failed", new Object[]{pos.toShortString()}));
         return 0;
      } else {
         ((CommandSourceStack)ctx.getSource())
            .sendSuccess(() -> Component.translatable("command.millenaire.importtable.placed", new Object[]{pos.toShortString()}), false);
         return 1;
      }
   }

   private static int executeInfo(CommandContext<CommandSourceStack> ctx) {
      ImportTableBlockEntity be = requireImportTable(ctx);
      if (be == null) {
         return 0;
      }

      String culture = be.getCultureKey().isEmpty() ? "<none>" : be.getCultureKey();
      String building = be.getBuildingId().isEmpty() ? "<none>" : be.getBuildingId();
      String variant = be.getVariant().isEmpty() ? "<none>" : be.getVariant();
      ((CommandSourceStack)ctx.getSource())
         .sendSuccess(
            () -> Component.translatable(
               "command.millenaire.importtable.info",
               new Object[]{
                  be.getBlockPos().toShortString(),
                  culture,
                  building,
                  variant,
                  be.getUpgradeLevel(),
                  be.hasPlan(),
                  be.getLength(),
                  be.getWidth(),
                  be.getHeight(),
                  be.getStartingLevel(),
                  be.getOrientation()
               }
            ),
            false
         );
      return 1;
   }

   private static int executeLoad(CommandContext<CommandSourceStack> ctx, String variant, int level) {
      ImportTableBlockEntity be = requireImportTable(ctx);
      if (be == null) {
         return 0;
      } else {
         String cultureKey = StringArgumentType.getString(ctx, "cultureKey");
         String buildingId = StringArgumentType.getString(ctx, "buildingId");
         ServerLevel serverLevel = ((CommandSourceStack)ctx.getSource()).getLevel();
         ServerPlayer player = resolvePlayer(ctx);
         BuildingImporter.importLevelFromCulture(serverLevel, be, player, cultureKey, buildingId, variant, level, false, "");
         if (buildingId.equals(be.getBuildingId()) && cultureKey.equals(be.getCultureKey())) {
            ((CommandSourceStack)ctx.getSource())
               .sendSuccess(() -> Component.translatable("command.millenaire.importtable.loaded", new Object[]{cultureKey, buildingId, variant, level}), false);
            return 1;
         } else {
            ((CommandSourceStack)ctx.getSource())
               .sendFailure(Component.translatable("command.millenaire.importtable.load_failed", new Object[]{cultureKey, buildingId, variant, level}));
            return 0;
         }
      }
   }

   private static int executeLoadAll(CommandContext<CommandSourceStack> ctx, String variant) {
      ImportTableBlockEntity be = requireImportTable(ctx);
      if (be == null) {
         return 0;
      } else {
         String cultureKey = StringArgumentType.getString(ctx, "cultureKey");
         String buildingId = StringArgumentType.getString(ctx, "buildingId");
         ServerLevel serverLevel = ((CommandSourceStack)ctx.getSource()).getLevel();
         ServerPlayer player = resolvePlayer(ctx);
         BuildingImporter.importAllFromCulture(serverLevel, be, player, cultureKey, buildingId, variant);
         if (buildingId.equals(be.getBuildingId()) && cultureKey.equals(be.getCultureKey())) {
            BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(ResourceLocation.tryParse(cultureKey + "/" + buildingId));
            int count = planSet != null ? planSet.getLevelCount(variant) : -1;
            ((CommandSourceStack)ctx.getSource())
               .sendSuccess(
                  () -> Component.translatable("command.millenaire.importtable.loaded_all", new Object[]{count, cultureKey, buildingId, variant}), false
               );
            return 1;
         } else {
            ((CommandSourceStack)ctx.getSource())
               .sendFailure(Component.translatable("command.millenaire.importtable.load_all_failed", new Object[]{cultureKey, buildingId, variant}));
            return 0;
         }
      }
   }

   private static int executeExport(CommandContext<CommandSourceStack> ctx) {
      ImportTableBlockEntity be = requireImportTable(ctx);
      if (be == null) {
         return 0;
      } else if (!be.hasPlan()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.translatable("command.millenaire.importtable.export_no_plan"));
         return 0;
      } else {
         String buildingId = be.getBuildingId();
         String variant = be.getVariant();
         int level = be.getUpgradeLevel();
         ServerLevel serverLevel = ((CommandSourceStack)ctx.getSource()).getLevel();
         ServerPlayer player = resolvePlayer(ctx);
         BuildingExporter.exportLevel(serverLevel, be, player);
         ((CommandSourceStack)ctx.getSource())
            .sendSuccess(() -> Component.translatable("command.millenaire.importtable.export_triggered", new Object[]{buildingId, variant, level}), false);
         return 1;
      }
   }

   private static int executeExportNewLevel(CommandContext<CommandSourceStack> ctx) {
      ImportTableBlockEntity be = requireImportTable(ctx);
      if (be == null) {
         return 0;
      } else if (!be.hasPlan()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.translatable("command.millenaire.importtable.export_new_level_no_plan"));
         return 0;
      } else {
         ServerLevel serverLevel = ((CommandSourceStack)ctx.getSource()).getLevel();
         ServerPlayer player = resolvePlayer(ctx);
         BuildingExporter.exportNewLevel(serverLevel, be, player);
         ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.translatable("command.millenaire.importtable.export_new_level_triggered"), false);
         return 1;
      }
   }

   private static int executeCreate(CommandContext<CommandSourceStack> ctx, boolean clearGround) {
      ImportTableBlockEntity be = requireImportTable(ctx);
      if (be == null) {
         return 0;
      }

      int length = IntegerArgumentType.getInteger(ctx, "length");
      int width = IntegerArgumentType.getInteger(ctx, "width");
      int startingLevel = IntegerArgumentType.getInteger(ctx, "startingLevel");
      int height = IntegerArgumentType.getInteger(ctx, "height");
      ServerLevel serverLevel = ((CommandSourceStack)ctx.getSource()).getLevel();
      ServerPlayer player = resolvePlayer(ctx);
      BuildingImporter.createNewBuilding(serverLevel, be, player, length, width, startingLevel, height, clearGround);
      String buildingId = be.getBuildingId();
      ((CommandSourceStack)ctx.getSource())
         .sendSuccess(() -> Component.translatable("command.millenaire.importtable.created", new Object[]{buildingId, width, length}), false);
      return 1;
   }

   private static RequiredArgumentBuilder<CommandSourceStack, Integer> pos3d(
      Function<RequiredArgumentBuilder<CommandSourceStack, Integer>, RequiredArgumentBuilder<CommandSourceStack, Integer>> zTail
   ) {
      return (RequiredArgumentBuilder<CommandSourceStack, Integer>)Commands.argument("x", IntegerArgumentType.integer())
         .then(Commands.argument("y", IntegerArgumentType.integer()).then((ArgumentBuilder)zTail.apply(Commands.argument("z", IntegerArgumentType.integer()))));
   }

   private static BlockPos readPos(CommandContext<CommandSourceStack> ctx) {
      return new BlockPos(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z"));
   }

   private static ImportTableBlockEntity requireImportTable(CommandContext<CommandSourceStack> ctx) {
      BlockPos pos = readPos(ctx);
      ServerLevel level = ((CommandSourceStack)ctx.getSource()).getLevel();
      level.getChunk(pos);
      if (level.getBlockEntity(pos) instanceof ImportTableBlockEntity table) {
         return table;
      } else {
         ((CommandSourceStack)ctx.getSource())
            .sendFailure(
               Component.translatable("command.millenaire.importtable.no_table_at", new Object[]{pos.toShortString(), pos.getX(), pos.getY(), pos.getZ()})
            );
         return null;
      }
   }

   private static ServerPlayer resolvePlayer(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayer();
      return (ServerPlayer)(player != null ? player : FakePlayerFactory.getMinecraft(((CommandSourceStack)ctx.getSource()).getLevel()));
   }
}
