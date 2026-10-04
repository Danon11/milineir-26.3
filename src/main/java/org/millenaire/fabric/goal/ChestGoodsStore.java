package org.millenaire.fabric.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.millenaire.fabric.economy.LegacyGoodsCatalog;
import org.millenaire.fabric.economy.LegacyItemResolver;

import java.util.*;

/**
 * Goods in the loaded chests of one building. Goods match by item; wildcard aliases (metadata -1) and
 * aliases that do not resolve to a registered item count as absent rather than matching anything.
 */
public final class ChestGoodsStore implements GoodsStore {
    private final List<Container> containers;
    private final LegacyGoodsCatalog goods;
    private final Map<String, Optional<ItemStack>> prototypes = new HashMap<>();

    public ChestGoodsStore(ServerLevel level, Collection<BlockPos> chests, LegacyGoodsCatalog goods) {
        List<Container> found = new ArrayList<>();
        for (BlockPos pos : chests)
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof Container container && !found.contains(container)) found.add(container);
        this.containers = List.copyOf(found);
        this.goods = goods;
    }

    public boolean isEmpty() { return containers.isEmpty(); }

    public Optional<ItemStack> prototype(String good) {
        return prototypes.computeIfAbsent(good, key -> {
            var definition = goods.goods().get(key);
            if (definition == null || definition.metadata() < 0) return Optional.empty();
            try {
                return Optional.of(LegacyItemResolver.resolve(definition).stack());
            } catch (RuntimeException exception) {
                return Optional.empty();
            }
        });
    }

    @Override
    public int count(String good) {
        var prototype = prototype(good);
        if (prototype.isEmpty()) return 0;
        int total = 0;
        for (Container container : containers)
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.is(prototype.get().getItem())) total += stack.getCount();
            }
        return total;
    }

    @Override
    public int remove(String good, int amount) {
        var prototype = prototype(good);
        if (prototype.isEmpty() || amount <= 0) return 0;
        int removed = 0;
        for (Container container : containers) {
            for (int slot = 0; slot < container.getContainerSize() && removed < amount; slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.is(prototype.get().getItem())) continue;
                int taken = Math.min(stack.getCount(), amount - removed);
                stack.shrink(taken);
                removed += taken;
                container.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
            }
            container.setChanged();
        }
        return removed;
    }

    @Override
    public int space(String good) {
        var prototype = prototype(good);
        if (prototype.isEmpty()) return 0;
        int space = 0;
        for (Container container : containers) {
            int limit = container.getMaxStackSize(prototype.get());
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty()) space += limit;
                else if (ItemStack.isSameItemSameComponents(stack, prototype.get())) space += Math.max(0, limit - stack.getCount());
            }
        }
        return space;
    }

    @Override
    public int add(String good, int amount) {
        var prototype = prototype(good);
        if (prototype.isEmpty() || amount <= 0) return 0;
        int added = 0;
        // Fill partial stacks first, then empty slots, like a player inserting items.
        for (boolean emptySlots : new boolean[]{false, true})
            for (Container container : containers) {
                int limit = container.getMaxStackSize(prototype.get());
                for (int slot = 0; slot < container.getContainerSize() && added < amount; slot++) {
                    ItemStack stack = container.getItem(slot);
                    if (emptySlots != stack.isEmpty()) continue;
                    if (!emptySlots && !ItemStack.isSameItemSameComponents(stack, prototype.get())) continue;
                    int room = emptySlots ? limit : limit - stack.getCount();
                    int moved = Math.min(room, amount - added);
                    if (moved <= 0) continue;
                    if (emptySlots) container.setItem(slot, prototype.get().copyWithCount(moved));
                    else stack.grow(moved);
                    added += moved;
                }
                container.setChanged();
            }
        return added;
    }
}
