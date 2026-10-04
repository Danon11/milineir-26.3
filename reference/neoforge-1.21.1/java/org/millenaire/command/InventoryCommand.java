package org.millenaire.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.item.ItemHelper;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;

public final class InventoryCommand {
   private InventoryCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(
         ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("inventory")
                        .then(Commands.literal("list").executes(InventoryCommand::listInventory)))
                     .then(
                        Commands.literal("add")
                           .then(
                              Commands.argument("item", ResourceLocationArgument.id())
                                 .then(Commands.argument("count", IntegerArgumentType.integer(1)).executes(InventoryCommand::addItem))
                           )
                     ))
                  .then(
                     Commands.literal("remove")
                        .then(
                           Commands.argument("item", ResourceLocationArgument.id())
                              .then(Commands.argument("count", IntegerArgumentType.integer(1)).executes(InventoryCommand::removeItem))
                        )
                  ))
               .then(
                  Commands.literal("add-to")
                     .then(
                        Commands.argument("building", StringArgumentType.string())
                           .then(
                              Commands.argument("item", ResourceLocationArgument.id())
                                 .then(Commands.argument("count", IntegerArgumentType.integer(1)).executes(InventoryCommand::addToBuilding))
                           )
                     )
               ))
            .then(Commands.literal("fill").executes(InventoryCommand::fillTestGoods))
      );
   }

   private static Village findVillage(CommandSourceStack source) {
      ServerLevel level = source.getLevel();
      if (level.dimension() != Level.OVERWORLD) {
         return null;
      }

      BlockPos searchPos = source.getPlayer() != null ? source.getPlayer().blockPosition() : BlockPos.ZERO;
      return VillageSavedData.get(level).getVillageManager().findNearestVillage(searchPos, 5000.0);
   }

   private static BuildingInventory getTownHallInventory(CommandSourceStack source) {
      Village village = findVillage(source);
      if (village == null) {
         source.sendFailure(Component.translatable("command.millenaire.inv.no_village"));
         return null;
      } else {
         BuildingInstance th = village.getTownhall();
         if (th == null) {
            source.sendFailure(Component.translatable("command.millenaire.inv.no_townhall"));
            return null;
         } else {
            BuildingInventory inv = th.getInventory();
            if (inv == null) {
               source.sendFailure(Component.translatable("command.millenaire.inv.no_townhall_inv"));
               return null;
            } else {
               return inv;
            }
         }
      }
   }

   private static int listInventory(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      BuildingInventory inv = getTownHallInventory(source);
      if (inv == null) {
         return 0;
      }

      Map<Item, Integer> contents = inv.scanChests(level);
      if (contents.isEmpty()) {
         source.sendSuccess(() -> Component.translatable("command.millenaire.inv.empty"), false);
         return 1;
      }

      source.sendSuccess(() -> Component.translatable("command.millenaire.inv.list_header"), false);

      for (Entry<Item, Integer> entry : contents.entrySet()) {
         String itemName = BuiltInRegistries.ITEM.getKey(entry.getKey()).toString();
         int count = entry.getValue();
         source.sendSuccess(() -> Component.literal("  " + itemName + " x" + count), false);
      }

      return 1;
   }

   private static int addItem(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      BuildingInventory inv = getTownHallInventory(source);
      if (inv == null) {
         return 0;
      } else {
         ResourceLocation itemId = ResourceLocationArgument.getId(ctx, "item");
         int count = IntegerArgumentType.getInteger(ctx, "count");
         Item item = ItemHelper.resolve(itemId);
         if (item == null) {
            source.sendFailure(Component.translatable("command.millenaire.error.unknown_item", new Object[]{itemId.toString()}));
            return 0;
         } else {
            int added = inv.add(level, item, count);
            String itemName = itemId.toString();
            source.sendSuccess(() -> Component.translatable("command.millenaire.inv.added", new Object[]{added, itemName}), false);
            return 1;
         }
      }
   }

   private static int fillTestGoods(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      BuildingInventory inv = getTownHallInventory(source);
      if (inv == null) {
         return 0;
      }

      inv.add(level, Items.OAK_LOG, 2048);
      inv.add(level, Items.SPRUCE_LOG, 1024);
      inv.add(level, Items.DARK_OAK_LOG, 1024);
      inv.add(level, Items.COBBLESTONE, 2048);
      inv.add(level, Items.STONE, 3500);
      inv.add(level, Items.GLASS, 512);
      inv.add(level, Items.SAND, 256);
      inv.add(level, Items.OAK_PLANKS, 1024);
      inv.add(level, Items.WHITE_WOOL, 256);
      inv.add(level, Items.IRON_INGOT, 128);
      inv.add(level, Items.GOLD_INGOT, 32);
      inv.add(level, Items.WHEAT_SEEDS, 256);
      inv.add(level, Items.WHEAT, 128);
      inv.add(level, Items.BREAD, 64);
      inv.add(level, Items.OAK_SAPLING, 64);
      source.sendSuccess(() -> Component.translatable("command.millenaire.inv.filled"), false);
      return 1;
   }

   private static int addToBuilding(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      String buildingPattern = StringArgumentType.getString(ctx, "building");
      ResourceLocation itemId = ResourceLocationArgument.getId(ctx, "item");
      int count = IntegerArgumentType.getInteger(ctx, "count");
      Village village = findVillage(source);
      if (village == null) {
         source.sendFailure(Component.translatable("command.millenaire.inv.no_village"));
         return 0;
      }

      Item item = ItemHelper.resolve(itemId);
      if (item == null) {
         source.sendFailure(Component.translatable("command.millenaire.error.unknown_item", new Object[]{itemId.toString()}));
         return 0;
      }

      for (BuildingInstance building : village.getBuildings()) {
         if (building.getStatus() == BuildingInstance.Status.COMPLETE && building.getPlanId().toString().contains(buildingPattern)) {
            BuildingInventory inv = building.getInventory();
            if (inv != null) {
               int added = inv.add(level, item, count);
               String planId = building.getPlanId().toString();
               source.sendSuccess(() -> Component.translatable("command.millenaire.inv.added_to", new Object[]{added, itemId.toString(), planId}), false);
               return 1;
            }
         }
      }

      source.sendFailure(Component.translatable("command.millenaire.inv.no_complete_building", new Object[]{buildingPattern}));
      return 0;
   }

   private static int removeItem(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      BuildingInventory inv = getTownHallInventory(source);
      if (inv == null) {
         return 0;
      } else {
         ResourceLocation itemId = ResourceLocationArgument.getId(ctx, "item");
         int count = IntegerArgumentType.getInteger(ctx, "count");
         Item item = ItemHelper.resolve(itemId);
         if (item == null) {
            source.sendFailure(Component.translatable("command.millenaire.error.unknown_item", new Object[]{itemId.toString()}));
            return 0;
         } else {
            int removed = inv.remove(level, item, count);
            String itemName = itemId.toString();
            source.sendSuccess(() -> Component.translatable("command.millenaire.inv.removed", new Object[]{removed, itemName}), false);
            return 1;
         }
      }
   }
}
