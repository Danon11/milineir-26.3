package org.millenaire.fabric.economy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The deniers a player carries: copper (1), silver (64) and gold (4096) coins in the inventory. */
public final class Wallet {
    private Wallet() {}

    private static Item coin(String name) {
        return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("millenaire", name));
    }

    static int value(ItemStack stack) {
        if (stack.is(coin("denier"))) return stack.getCount();
        if (stack.is(coin("denierargent"))) return stack.getCount() * TradeOffers.SILVER;
        if (stack.is(coin("denieror"))) return stack.getCount() * TradeOffers.GOLD;
        return 0;
    }

    public static int total(Player player) {
        int total = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) total += value(inventory.getItem(slot));
        return total;
    }

    /** Takes {@code amount} deniers, giving change in the largest coins; false (nothing taken) if too poor. */
    public static boolean pay(Player player, int amount) {
        if (amount < 0 || total(player) < amount) return false;
        var inventory = player.getInventory();
        int taken = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            int value = value(stack);
            if (value == 0) continue;
            taken += value;
            inventory.setItem(slot, ItemStack.EMPTY);
        }
        int change = taken - amount;
        give(player, "denieror", change / TradeOffers.GOLD);
        give(player, "denierargent", change % TradeOffers.GOLD / TradeOffers.SILVER);
        give(player, "denier", change % TradeOffers.SILVER);
        return true;
    }

    private static void give(Player player, String coin, int count) {
        while (count > 0) {
            int part = Math.min(64, count);
            var stack = new ItemStack(coin(coin), part);
            if (!player.getInventory().add(stack) && player.level() instanceof net.minecraft.server.level.ServerLevel level) player.spawnAtLocation(level, stack);
            count -= part;
        }
    }
}
