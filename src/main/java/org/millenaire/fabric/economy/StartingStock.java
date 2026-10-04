package org.millenaire.fabric.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Function;

/** Prepares the complete starting inventory before placement; never silently loses overflow. */
public final class StartingStock {
    public static final int CHEST_SLOTS = 27;
    public static final int MAX_RULES = 1024;
    public static final int MAX_RULE_COUNT = 1_000_000;
    public record Rule(String good, double probability, int fixed, int bonus) {
        public Rule {
            good = good.trim().toLowerCase(Locale.ROOT);
            if (good.isEmpty() || !Double.isFinite(probability)
                    || fixed < 0 || bonus < 0 || (long) fixed + bonus > MAX_RULE_COUNT)
                throw new IllegalArgumentException("Invalid starting good: " + good);
        }
        public static Rule parse(String value) {
            String[] parts = value.split(",", -1);
            if (parts.length != 4) throw new IllegalArgumentException("Expected good,chance,fixed,bonus: " + value);
            try { return new Rule(parts[0], Double.parseDouble(parts[1].trim()), Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim())); }
            catch (NumberFormatException exception) { throw new IllegalArgumentException("Invalid starting good numbers: " + value, exception); }
        }
    }
    public record Slot(int index, LegacyItemResolver.Target item, int count) {
        public Slot {
            if (index < 0 || index >= CHEST_SLOTS || count <= 0) throw new IllegalArgumentException("Invalid starting inventory slot");
            Objects.requireNonNull(item);
        }
    }
    public record Inventory(BlockPos pos, List<Slot> slots) {
        public Inventory {
            pos = pos.immutable(); slots = List.copyOf(slots);
            if (slots.stream().map(Slot::index).distinct().count() != slots.size()) throw new IllegalArgumentException("Duplicate inventory slot");
        }
    }
    public record Prepared(List<Inventory> inventories, List<String> issues) {
        public Prepared { inventories = List.copyOf(inventories); issues = List.copyOf(issues); }
        public boolean supported() { return issues.isEmpty(); }
    }
    private record Resolved(Rule rule, LegacyItemResolver.Target target, int stackLimit) {}
    private StartingStock() {}

    public static Prepared prepare(List<String> declarations, List<BlockPos> chests, LegacyGoodsCatalog goods, long seed) {
        return prepare(declarations, chests, goods, seed, LegacyItemResolver::resolve);
    }
    public static Prepared prepare(List<String> declarations, List<BlockPos> chests, LegacyGoodsCatalog goods, long seed,
                                   Function<LegacyGoodsCatalog.Good, LegacyItemResolver.Target> resolver) {
        List<String> active = declarations.stream().filter(value -> !value.isBlank()).toList();
        if (active.isEmpty()) return new Prepared(List.of(), List.of());
        if (active.size() > MAX_RULES) return failure("Too many starting good rules: " + active.size());
        // Lone buildings without a chest simply start empty, as in the original mod.
        if (chests.isEmpty()) return new Prepared(List.of(), List.of());
        if (chests.stream().distinct().count() != chests.size()) return failure("Duplicate starting chest position");
        List<Resolved> rules = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        var capacity = new SimpleContainer(CHEST_SLOTS);
        for (String value : active) {
            try {
                Rule rule = Rule.parse(value);
                var target = resolver.apply(goods.require(rule.good()));
                int limit = capacity.getMaxStackSize(target.stack());
                if (limit <= 0) throw new IllegalArgumentException("Unstackable starting good: " + rule.good());
                rules.add(new Resolved(rule, target, limit));
            } catch (IllegalArgumentException exception) { issues.add("Starting inventory: " + exception.getMessage()); }
        }
        if (!issues.isEmpty()) return new Prepared(List.of(), issues);
        List<List<Slot>> contents = new ArrayList<>();
        chests.forEach(pos -> contents.add(new ArrayList<>()));
        Random random = new Random(seed);
        for (Resolved resolved : rules) {
            Rule rule = resolved.rule();
            if (random.nextDouble() >= rule.probability()) continue;
            int count = rule.fixed() + (rule.bonus() > 0 ? random.nextInt(rule.bonus() + 1) : 0);
            if (count <= 0) continue;
            int chest = random.nextInt(chests.size());
            // A full chest keeps what fits; the rest of the roll is lost rather than blocking the building.
            insert(contents.get(chest), resolved.target(), count, resolved.stackLimit());
        }
        List<Inventory> inventories = new ArrayList<>();
        for (int i = 0; i < chests.size(); i++) inventories.add(new Inventory(chests.get(i), contents.get(i)));
        return new Prepared(inventories, List.of());
    }
    private static boolean insert(List<Slot> slots, LegacyItemResolver.Target target, int count, int limit) {
        int remaining = count;
        // Match the old helper: merge matching stacks first, then fill empty slots from the start.
        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            Slot slot = slots.get(i);
            if (!slot.item().equals(target)) continue;
            int added = Math.min(remaining, limit - slot.count());
            slots.set(i, new Slot(slot.index(), target, slot.count() + added)); remaining -= added;
        }
        while (remaining > 0 && slots.size() < CHEST_SLOTS) {
            int added = Math.min(remaining, limit);
            slots.add(new Slot(slots.size(), target, added)); remaining -= added;
        }
        return remaining == 0;
    }
    /** Validate every stack before modifying the new empty container. */
    public static void apply(Container container, Inventory inventory) {
        if (container.getContainerSize() != CHEST_SLOTS || !container.isEmpty())
            throw new IllegalStateException("Starting stock requires a new empty 27-slot chest");
        Map<Integer, ItemStack> stacks = new LinkedHashMap<>();
        for (Slot slot : inventory.slots()) {
            ItemStack stack = slot.item().stack();
            if (slot.count() > container.getMaxStackSize(stack)) throw new IllegalArgumentException("Starting stack exceeds item capacity");
            stack.setCount(slot.count()); stacks.put(slot.index(), stack);
        }
        stacks.forEach(container::setItem); container.setChanged();
    }
    public static long buildingSeed(long seed, BlockPos origin, String plan, int rotation) {
        long value = seed ^ origin.asLong() ^ ((long) plan.hashCode() << 32) ^ rotation;
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
    private static Prepared failure(String issue) { return new Prepared(List.of(), List.of(issue)); }
}
