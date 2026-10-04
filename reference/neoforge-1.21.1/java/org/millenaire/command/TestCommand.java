package org.millenaire.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.millenaire.advancement.MillAdvancements;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.commerce.TradeAction;
import org.millenaire.commerce.TradeMenu;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.item.ItemHelper;
import org.millenaire.item.MoneyHelper;
import org.millenaire.test.TestPlayerManager;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;

public final class TestCommand {
   private static final Gson GSON = new GsonBuilder().create();

   private TestCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("millenaire")
            .then(
               ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                                         "test"
                                                      )
                                                      .requires(source -> source.hasPermission(2)))
                                                   .then(
                                                      ((LiteralArgumentBuilder)Commands.literal("spawn-player").executes(TestCommand::spawnPlayerDefault))
                                                         .then(
                                                            Commands.argument("x", IntegerArgumentType.integer())
                                                               .then(
                                                                  Commands.argument("y", IntegerArgumentType.integer())
                                                                     .then(
                                                                        Commands.argument("z", IntegerArgumentType.integer())
                                                                           .executes(TestCommand::spawnPlayerAt)
                                                                     )
                                                               )
                                                         )
                                                   ))
                                                .then(Commands.literal("remove-player").executes(TestCommand::removePlayer)))
                                             .then(Commands.literal("status").executes(TestCommand::status)))
                                          .then(
                                             Commands.literal("give")
                                                .then(
                                                   Commands.argument("item", ResourceLocationArgument.id())
                                                      .then(Commands.argument("count", IntegerArgumentType.integer(1, 64)).executes(TestCommand::give))
                                                )
                                          ))
                                       .then(
                                          Commands.literal("move")
                                             .then(
                                                Commands.argument("x", IntegerArgumentType.integer())
                                                   .then(
                                                      Commands.argument("y", IntegerArgumentType.integer())
                                                         .then(Commands.argument("z", IntegerArgumentType.integer()).executes(TestCommand::move))
                                                   )
                                             )
                                       ))
                                    .then(
                                       Commands.literal("interact")
                                          .then(Commands.argument("uuid_prefix", StringArgumentType.string()).executes(TestCommand::interact))
                                    ))
                                 .then(
                                    ((LiteralArgumentBuilder)Commands.literal("interact-nearest").executes(TestCommand::interactNearest))
                                       .then(Commands.argument("tag", StringArgumentType.string()).executes(TestCommand::interactNearestTag))
                                 ))
                              .then(
                                 ((LiteralArgumentBuilder)Commands.literal("trade")
                                       .then(
                                          Commands.literal("buy")
                                             .then(
                                                ((RequiredArgumentBuilder)Commands.argument("goodIndex", IntegerArgumentType.integer(0))
                                                      .executes(ctx -> trade(ctx, false, 1)))
                                                   .then(
                                                      Commands.argument("qty", IntegerArgumentType.integer(1))
                                                         .executes(ctx -> trade(ctx, false, IntegerArgumentType.getInteger(ctx, "qty")))
                                                   )
                                             )
                                       ))
                                    .then(
                                       Commands.literal("sell")
                                          .then(
                                             ((RequiredArgumentBuilder)Commands.argument("goodIndex", IntegerArgumentType.integer(0))
                                                   .executes(ctx -> trade(ctx, true, 1)))
                                                .then(
                                                   Commands.argument("qty", IntegerArgumentType.integer(1))
                                                      .executes(ctx -> trade(ctx, true, IntegerArgumentType.getInteger(ctx, "qty")))
                                                )
                                          )
                                    )
                              ))
                           .then(Commands.literal("close-menu").executes(TestCommand::closeMenu)))
                        .then(Commands.literal("find-shop").executes(TestCommand::findShop)))
                     .then(Commands.literal("advancements").executes(TestCommand::advancements)))
                  .then(
                     Commands.literal("generate-chunks")
                        .then(
                           Commands.argument("x", IntegerArgumentType.integer())
                              .then(
                                 Commands.argument("z", IntegerArgumentType.integer())
                                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 20)).executes(TestCommand::generateChunks))
                              )
                        )
                  )
            )
      );
   }

   private static ServerPlayer requirePlayer(CommandSourceStack source) {
      ServerPlayer player = TestPlayerManager.get();
      if (player == null) {
         source.sendFailure(Component.literal("No active TestPlayer. Use spawn-player first."));
      }

      return player;
   }

   private static void sendJson(CommandSourceStack source, Map<String, Object> data) {
      source.sendSuccess(() -> Component.literal(GSON.toJson(data)), false);
   }

   private static int spawnPlayerDefault(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);
      if (level == null) {
         source.sendFailure(Component.literal("No Overworld available."));
         return 0;
      } else {
         BlockPos pos = level.getSharedSpawnPos();
         return doSpawn(source, level, pos);
      }
   }

   private static int spawnPlayerAt(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);
      if (level == null) {
         source.sendFailure(Component.literal("No Overworld available."));
         return 0;
      } else {
         int x = IntegerArgumentType.getInteger(ctx, "x");
         int y = IntegerArgumentType.getInteger(ctx, "y");
         int z = IntegerArgumentType.getInteger(ctx, "z");
         return doSpawn(source, level, new BlockPos(x, y, z));
      }
   }

   private static int doSpawn(CommandSourceStack source, ServerLevel level, BlockPos pos) {
      if (TestPlayerManager.isActive()) {
         source.sendFailure(Component.literal("TestPlayer already active. Use remove-player first."));
         return 0;
      }

      try {
         TestPlayerManager.spawn(source.getServer(), level, pos);
         source.sendSuccess(() -> Component.literal("TestPlayer created at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()), false);
         return 1;
      } catch (Exception e) {
         source.sendFailure(Component.literal("Error: " + e.getMessage()));
         return 0;
      }
   }

   private static int removePlayer(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      if (!TestPlayerManager.isActive()) {
         source.sendFailure(Component.literal("No active TestPlayer."));
         return 0;
      } else {
         TestPlayerManager.remove();
         source.sendSuccess(() -> Component.literal("TestPlayer removed."), false);
         return 1;
      }
   }

   private static int status(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = requirePlayer(source);
      if (player == null) {
         return 0;
      }

      Map<String, Object> data = new LinkedHashMap<>();
      data.put("name", player.getGameProfile().getName());
      Map<String, Object> pos = new LinkedHashMap<>();
      BlockPos bp = player.blockPosition();
      pos.put("x", bp.getX());
      pos.put("y", bp.getY());
      pos.put("z", bp.getZ());
      data.put("pos", pos);
      data.put("deniers", MoneyHelper.getTotalDeniers(player.getInventory()));
      int nonEmpty = 0;

      for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
         if (!player.getInventory().getItem(i).isEmpty()) {
            nonEmpty++;
         }
      }

      data.put("inventory_size", nonEmpty);
      boolean menuOpen = player.containerMenu != player.inventoryMenu;
      data.put("menu_open", menuOpen);
      if (menuOpen) {
         data.put("menu_type", player.containerMenu.getClass().getSimpleName());
      }

      sendJson(source, data);
      return 1;
   }

   private static int give(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = requirePlayer(source);
      if (player == null) {
         return 0;
      } else {
         ResourceLocation itemId = ResourceLocationArgument.getId(ctx, "item");
         int count = IntegerArgumentType.getInteger(ctx, "count");
         Item item = ItemHelper.resolve(itemId);
         if (item == null) {
            source.sendFailure(Component.literal("Unknown item: " + itemId));
            return 0;
         } else {
            player.getInventory().add(new ItemStack(item, count));
            source.sendSuccess(() -> Component.literal("Given " + count + "x " + itemId + " to TestPlayer."), false);
            return 1;
         }
      }
   }

   private static int move(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = requirePlayer(source);
      if (player == null) {
         return 0;
      } else {
         int x = IntegerArgumentType.getInteger(ctx, "x");
         int y = IntegerArgumentType.getInteger(ctx, "y");
         int z = IntegerArgumentType.getInteger(ctx, "z");
         ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);
         if (level == null) {
            source.sendFailure(Component.literal("No Overworld."));
            return 0;
         } else {
            player.teleportTo(level, x + 0.5, y, z + 0.5, Set.of(), player.getYRot(), player.getXRot());
            TestPlayerManager.confirmTeleport();
            TestPlayerManager.acknowledgeChunkBatch();
            source.sendSuccess(() -> Component.literal("TestPlayer moved to " + x + " " + y + " " + z), false);
            return 1;
         }
      }
   }

   private static int interact(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = requirePlayer(source);
      if (player == null) {
         return 0;
      } else {
         String prefix = StringArgumentType.getString(ctx, "uuid_prefix").toLowerCase();
         ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);
         if (level == null) {
            return 0;
         } else {
            List<MillVillager> matches = level.getEntitiesOfClass(MillVillager.class, new AABB(player.blockPosition()).inflate(100.0))
               .stream()
               .filter(v -> v.getUUID().toString().startsWith(prefix))
               .sorted(Comparator.comparingDouble(v -> v.distanceToSqr(player)))
               .toList();
            if (matches.isEmpty()) {
               source.sendFailure(Component.literal("No villager found with prefix " + prefix));
               return 0;
            } else {
               MillVillager target = matches.getFirst();
               target.mobInteract(player, InteractionHand.MAIN_HAND);
               source.sendSuccess(
                  () -> Component.literal("Interacted with " + target.getUUID().toString().substring(0, 8) + " (" + target.getVillagerTypeId() + ")"), false
               );
               return 1;
            }
         }
      }
   }

   private static int interactNearest(CommandContext<CommandSourceStack> ctx) {
      return doInteractNearest(ctx, null);
   }

   private static int interactNearestTag(CommandContext<CommandSourceStack> ctx) {
      String tag = StringArgumentType.getString(ctx, "tag");
      return doInteractNearest(ctx, tag);
   }

   private static int doInteractNearest(CommandContext<CommandSourceStack> ctx, String tag) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = requirePlayer(source);
      if (player == null) {
         return 0;
      }

      ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);
      if (level == null) {
         return 0;
      }

      List<MillVillager> candidates = level.getEntitiesOfClass(MillVillager.class, new AABB(player.blockPosition()).inflate(10.0));
      if (tag != null) {
         candidates = candidates.stream().filter(v -> {
            VillagerType vType = ModCultures.getVillagerType(v.getVillagerTypeId());
            return vType != null && vType.hasTag(tag);
         }).toList();
      }

      if (candidates.isEmpty()) {
         String msg = tag != null ? "No villager found with tag " + tag : "No villager found in range";
         source.sendFailure(Component.literal(msg));
         return 0;
      } else {
         MillVillager target = candidates.stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(player))).orElseThrow();
         target.mobInteract(player, InteractionHand.MAIN_HAND);
         source.sendSuccess(
            () -> Component.literal(
               "Interacted with "
                  + target.getUUID().toString().substring(0, 8)
                  + " ("
                  + target.getVillagerTypeId()
                  + ")"
                  + (tag != null ? " [tag=" + tag + "]" : "")
            ),
            false
         );
         return 1;
      }
   }

   private static int trade(CommandContext<CommandSourceStack> ctx, boolean isSell, int qty) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = requirePlayer(source);
      if (player == null) {
         return 0;
      }

      if (qty != 1 && qty != 8 && qty != 64) {
         source.sendFailure(Component.literal("Invalid quantity: " + qty + ". Accepted values: 1, 8, 64."));
         return 0;
      }

      if (player.containerMenu == player.inventoryMenu) {
         source.sendFailure(Component.literal("No menu open on TestPlayer."));
         return 0;
      }

      if (player.containerMenu instanceof TradeMenu menu) {
         int goodIndex = IntegerArgumentType.getInteger(ctx, "goodIndex");
         if (goodIndex >= menu.getGoodsCount()) {
            source.sendFailure(Component.literal("Good index " + goodIndex + " invalid (0-" + (menu.getGoodsCount() - 1) + ")."));
            return 0;
         } else {
            TradeAction action = TradeAction.fromDirectionAndQuantity(!isSell, qty);
            if (action == null) {
               source.sendFailure(Component.literal("Invalid trade action."));
               return 0;
            } else {
               int buttonId = action.toButtonId(goodIndex);
               int deniersBefore = MoneyHelper.getTotalDeniers(player.getInventory());
               boolean accepted = menu.clickMenuButton(player, buttonId);
               int deniersAfter = MoneyHelper.getTotalDeniers(player.getInventory());
               int delta = deniersAfter - deniersBefore;
               boolean success = !accepted ? false : delta != 0;
               Map<String, Object> result = new LinkedHashMap<>();
               result.put("deniers_before", deniersBefore);
               result.put("deniers_after", deniersAfter);
               result.put("deniers_delta", delta);
               result.put("success", success);
               result.put("accepted", accepted);
               sendJson(source, result);
               return accepted ? 1 : 0;
            }
         }
      } else {
         source.sendFailure(Component.literal("Open menu is not a TradeMenu (" + player.containerMenu.getClass().getSimpleName() + ")."));
         return 0;
      }
   }

   private static int closeMenu(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = requirePlayer(source);
      if (player == null) {
         return 0;
      } else if (player.containerMenu == player.inventoryMenu) {
         source.sendSuccess(() -> Component.literal("No menu open."), false);
         return 1;
      } else {
         player.closeContainer();
         source.sendSuccess(() -> Component.literal("Menu closed."), false);
         return 1;
      }
   }

   private static int findShop(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);
      if (level == null) {
         source.sendFailure(Component.literal("No Overworld."));
         return 0;
      }

      VillageSavedData savedData = VillageSavedData.get(level);
      Village village = savedData.getVillageManager().findNearestVillage(BlockPos.ZERO, 5000.0);
      if (village == null) {
         source.sendFailure(Component.literal("No village found."));
         return 0;
      }

      for (BuildingInstance b : village.getBuildings()) {
         if (b.getStatus() == BuildingInstance.Status.COMPLETE) {
            BuildingPlan plan = ModCultures.getBuildingPlan(b.getPlanId());
            if (plan != null && plan.shopId() != null) {
               BlockPos sp = b.getFirstPointPos("sellingPos");
               if (sp == null) {
                  sp = b.getFirstPointPos("sleepingPos");
               }

               if (sp != null) {
                  Map<String, Object> result = new LinkedHashMap<>();
                  result.put("building", b.getPlanId().toString());
                  result.put("shop_id", plan.shopId());
                  result.put("x", sp.getX());
                  result.put("y", sp.getY());
                  result.put("z", sp.getZ());
                  sendJson(source, result);
                  return 1;
               }
            }
         }
      }

      source.sendFailure(Component.literal("No shop building with selling position found."));
      return 0;
   }

   private static int advancements(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = requirePlayer(source);
      if (player == null) {
         return 0;
      }

      List<String> earned = new ArrayList<>();
      ServerAdvancementManager serverAdvancements = player.server.getAdvancements();
      PlayerAdvancements playerAdvancements = player.getAdvancements();

      for (ResourceLocation advId : getAllMillenaireAdvancementIds()) {
         AdvancementHolder holder = serverAdvancements.get(advId);
         if (holder != null) {
            AdvancementProgress progress = playerAdvancements.getOrStartProgress(holder);
            if (progress.isDone()) {
               earned.add(advId.getPath());
            }
         }
      }

      Map<String, Object> data = new LinkedHashMap<>();
      data.put("count", earned.size());
      data.put("earned", earned);
      sendJson(source, data);
      return 1;
   }

   private static List<ResourceLocation> getAllMillenaireAdvancementIds() {
      List<ResourceLocation> ids = new ArrayList<>();
      ids.add(MillAdvancements.FIRST_CONTACT);
      ids.add(MillAdvancements.CRESUS);
      ids.add(MillAdvancements.CHEERS);
      ids.add(MillAdvancements.MASTER_FARMER);
      ids.add(MillAdvancements.GREAT_HUNTER);
      ids.add(MillAdvancements.HIRED);
      ids.add(MillAdvancements.RAINBOW);
      ids.add(MillAdvancements.SUMMONING_WAND);
      ids.add(MillAdvancements.AMATEUR_ARCHITECT);
      ids.add(MillAdvancements.MEDIEVAL_METROPOLIS);
      ids.add(MillAdvancements.EXPLORER);
      ids.add(MillAdvancements.MARCO_POLO);
      ids.add(MillAdvancements.MAGELLAN);
      ids.add(MillAdvancements.PANTHEON);
      ids.add(MillAdvancements.THE_QUEST);
      ids.add(MillAdvancements.MAITRE_A_PENSER);
      ids.add(MillAdvancements.WQ_NORMAN);
      ids.add(MillAdvancements.WQ_INDIAN);
      ids.add(MillAdvancements.WQ_MAYAN);
      ids.add(MillAdvancements.PUJA);
      ids.add(MillAdvancements.SACRIFICE);
      ids.add(MillAdvancements.FRIEND_INDEED);
      ids.add(MillAdvancements.SELF_DEFENSE);
      ids.add(MillAdvancements.DARK_SIDE);
      ids.add(MillAdvancements.ATTILA);
      ids.add(MillAdvancements.SCIPIO);
      ids.add(MillAdvancements.VIKING);
      ids.add(MillAdvancements.SELJUK_ISTANBUL);
      ids.add(MillAdvancements.BYZANTINES_NOTTODAY);
      ids.add(MillAdvancements.MARVEL_NORMAN);
      ids.add(MillAdvancements.MP_WEAPON);
      ids.add(MillAdvancements.MP_HIREDGOON);
      ids.add(MillAdvancements.MP_RAIDONPLAYER);
      ids.add(MillAdvancements.MP_NEIGHBOURTRADE);
      ids.add(MillAdvancements.MP_FRIENDLYVILLAGE);

      for (String culture : MillAdvancements.ADVANCEMENT_CULTURES) {
         ResourceLocation rep = MillAdvancements.REP.get(culture);
         if (rep != null) {
            ids.add(rep);
         }

         ResourceLocation leader = MillAdvancements.LEADER.get(culture);
         if (leader != null) {
            ids.add(leader);
         }

         ResourceLocation complete = MillAdvancements.COMPLETE.get(culture);
         if (complete != null) {
            ids.add(complete);
         }
      }

      return ids;
   }

   private static int generateChunks(CommandContext<CommandSourceStack> ctx) {
      final CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      final ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);
      if (level == null) {
         source.sendFailure(Component.literal("No Overworld."));
         return 0;
      } else {
         int cx = IntegerArgumentType.getInteger(ctx, "x") >> 4;
         int cz = IntegerArgumentType.getInteger(ctx, "z") >> 4;
         int radius = IntegerArgumentType.getInteger(ctx, "radius");
         final int side = 2 * radius + 1;
         final int startDx = -radius;
         final int fCx = cx;
         final int fCz = cz;
         final int fRadius = radius;
         source.getServer()
            .execute(
               new Runnable() {
                  int dx = startDx;

                  public void run() {
                     for (int dz = -fRadius; dz <= fRadius; dz++) {
                        level.getChunk(fCx + this.dx, fCz + dz);
                     }

                     this.dx++;
                     if (this.dx <= fRadius) {
                        source.getServer().execute(this);
                     } else {
                        int total = side * side;
                        source.sendSuccess(
                           () -> Component.literal("Generated " + total + " chunks around chunk " + fCx + "," + fCz + " (radius=" + fRadius + ")"), false
                        );
                     }
                  }
               }
            );
         source.sendSuccess(() -> Component.literal("Generating chunks (async, " + side + " rows)..."), false);
         return 1;
      }
   }
}
