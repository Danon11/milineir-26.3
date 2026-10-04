package org.millenaire.fabric.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.fabric.FabricBuildingState;
import org.millenaire.fabric.villager.MillVillagerEntity;

import java.util.*;
import java.util.random.RandomGenerator;

/**
 * Targets and effects of the resource goals: mining sources, gathering fruit blocks, slaughtering animals,
 * planting saplings, tending furnaces, and the lumberman's tree chopping and replanting.
 */
final class ResourceGoals {
    /** Work target and the effect applied when the work time is over. */
    record Plan(BlockPos target, java.util.function.BooleanSupplier effect) {}

    static final int SEARCH_RADIUS = 20, SEARCH_HEIGHT = 8, KEEP_ANIMALS = 2, MAX_TREE_LOGS = 32;

    private ResourceGoals() {}

    static BlockPos centre(FabricBuildingState.PlacedBuilding building) {
        return new BlockPos(building.origin().x(), building.origin().y(), building.origin().z());
    }

    /** Mining a resource source never depletes it, as the original quarry sources were permanent. */
    static Optional<Plan> mining(GoalDefinition goal, VillageContext context, FabricBuildingState.PlacedBuilding building, GoodsStore store,
                                 RandomGenerator random) {
        var source = WorldBlocks.pattern(goal.blockState());
        if (source.isEmpty() || goal.loot().isEmpty() || !belowVillageLimits(goal, store)) return Optional.empty();
        List<BlockPos> sources = new ArrayList<>();
        building.servicePoints().forEach((key, points) -> {
            if (key.endsWith("source")) points.forEach(p -> sources.add(new BlockPos(p.x(), p.y(), p.z())));
        });
        ServerLevel level = context.level();
        List<BlockPos> matching = sources.stream().filter(pos -> level.isLoaded(pos) && source.get().matches(level.getBlockState(pos))).toList();
        BlockPos target = !matching.isEmpty() ? matching.get(random.nextInt(matching.size()))
                : WorldBlocks.nearest(level, centre(building), Math.max(goal.range(), 6), 4, source.get()::matches).orElse(null);
        if (target == null) return Optional.empty();
        return Optional.of(new Plan(target, () -> {
            var current = context.store(building);
            goal.loot().forEach(current::add);
            return true;
        }));
    }

    /** Fruit and similar blocks: replace the ripe state with the resulting state and roll the harvest. */
    static Optional<Plan> gather(GoalDefinition goal, VillageContext context, FabricBuildingState.PlacedBuilding building, GoodsStore store,
                                 GoodsStore townhall, MillVillagerEntity villager, RandomGenerator random) {
        var ripe = WorldBlocks.pattern(goal.blockState());
        var result = WorldBlocks.pattern(goal.resultingBlockState());
        if (ripe.isEmpty() || result.isEmpty() || !GoalRules.belowLimits(goal, store, townhall)) return Optional.empty();
        ServerLevel level = context.level();
        var target = WorldBlocks.nearest(level, centre(building), SEARCH_RADIUS, SEARCH_HEIGHT, ripe.get()::matches);
        if (target.isEmpty()) return Optional.empty();
        boolean intoBuilding = Boolean.parseBoolean(goal.source().first("collectinbuilding", "true").trim());
        return Optional.of(new Plan(target.get(), () -> {
            BlockState state = level.getBlockState(target.get());
            if (!ripe.get().matches(state)) return false;
            BlockState next = result.get().state();
            if (next.is(state.getBlock())) {
                // Keep the block's own properties (such as leaf persistence) and change only the declared ones.
                for (var property : next.getProperties())
                    if (result.get().properties().containsKey(property.getName())) state = copy(state, next, property);
                next = state;
            }
            level.setBlockAndUpdate(target.get(), next);
            var harvest = GoalRules.rollHarvest(goal, random);
            if (intoBuilding) harvest.forEach(context.store(building)::add); else harvest.forEach(villager::changeCarried);
            return true;
        }));
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState target, BlockState source, net.minecraft.world.level.block.state.properties.Property<T> property) {
        return target.setValue(property, source.getValue(property));
    }

    /** Slaughters an adult animal of the goal's kind near the building while more than two remain. */
    static Optional<Plan> slaughter(GoalDefinition goal, VillageContext context, FabricBuildingState.PlacedBuilding building,
                                    MillVillagerEntity villager, RandomGenerator random) {
        if (goal.animal().isEmpty()) return Optional.empty();
        Identifier id = Identifier.tryParse(goal.animal().contains(":") ? goal.animal() : "minecraft:" + goal.animal());
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return Optional.empty();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
        ServerLevel level = context.level();
        var box = new net.minecraft.world.phys.AABB(centre(building)).inflate(SEARCH_RADIUS, SEARCH_HEIGHT, SEARCH_RADIUS);
        List<Entity> animals = level.getEntities((Entity) null, box, entity -> entity.getType() == type && entity.isAlive()
                && !(entity instanceof net.minecraft.world.entity.AgeableMob ageable && ageable.isBaby()));
        if (animals.size() <= KEEP_ANIMALS) return Optional.empty();
        Entity victim = animals.get(random.nextInt(animals.size()));
        return Optional.of(new Plan(victim.blockPosition(), () -> {
            if (!victim.isAlive() || victim.distanceToSqr(villager) > 16) return false;
            if (victim instanceof LivingEntity living) living.hurtServer(level, level.damageSources().mobAttack(villager), Float.MAX_VALUE);
            else victim.discard();
            GoalRules.rollHarvest(goal, random).forEach(context.store(building)::add);
            return true;
        }));
    }

    /** Plants a held sapling on open ground near the building. */
    static Optional<Plan> plantSapling(GoalDefinition goal, VillageContext context, FabricBuildingState.PlacedBuilding building,
                                       MillVillagerEntity villager, GoodsStore store, RandomGenerator random) {
        String sapling = goal.heldItems().isEmpty() ? "" : goal.heldItems().getFirst();
        if (sapling.isEmpty() || villager.carried(sapling) <= 0 && store.count(sapling) <= 0) return Optional.empty();
        var prototype = new ChestGoodsStore(context.level(), List.of(), context.catalog().goods()).prototype(sapling);
        if (prototype.isEmpty() || !(prototype.get().getItem() instanceof net.minecraft.world.item.BlockItem blockItem)) return Optional.empty();
        BlockState plant = blockItem.getBlock().defaultBlockState();
        int range = Math.max(goal.range(), 5);
        return spot(context.level(), centre(building), range, plant, random).map(pos -> new Plan(pos, () -> {
            if (!context.level().getBlockState(pos).isAir() || !plant.canSurvive(context.level(), pos)) return false;
            if (villager.changeCarried(sapling, -1) == 0 && context.store(building).remove(sapling, 1) == 0) return false;
            context.level().setBlockAndUpdate(pos, plant);
            return true;
        }));
    }

    /** Random open ground cell where the plant survives and no tree stands within two blocks. */
    static Optional<BlockPos> spot(ServerLevel level, BlockPos centre, int range, BlockState plant, RandomGenerator random) {
        for (int attempt = 0; attempt < 24; attempt++) {
            BlockPos column = centre.offset(random.nextInt(-range, range + 1), 0, random.nextInt(-range, range + 1));
            for (int dy = 4; dy >= -4; dy--) {
                BlockPos pos = column.above(dy);
                if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir() || !plant.canSurvive(level, pos)) continue;
                boolean crowded = WorldBlocks.nearest(level, pos, 2, 2, state -> state.is(BlockTags.LOGS) || state.is(BlockTags.SAPLINGS)).isPresent();
                if (!crowded) return Optional.of(pos);
            }
        }
        return Optional.empty();
    }

    /** Puts fuel from the building chests into an empty furnace fuel slot. */
    static Optional<Plan> tendFurnace(GoalDefinition goal, VillageContext context, FabricBuildingState.PlacedBuilding building, GoodsStore store) {
        String fuel = goal.heldItems().isEmpty() ? "" : goal.heldItems().getFirst();
        int amount = Math.max(goal.minimum(), 1);
        if (fuel.isEmpty() || store.count(fuel) < amount) return Optional.empty();
        ServerLevel level = context.level();
        for (BlockPos furnace : context.points(building, "furnaces")) {
            if (!level.isLoaded(furnace) || !(level.getBlockEntity(furnace) instanceof AbstractFurnaceBlockEntity entity)) continue;
            if (!entity.getItem(1).isEmpty()) continue;
            return Optional.of(new Plan(furnace, () -> {
                if (!(level.getBlockEntity(furnace) instanceof AbstractFurnaceBlockEntity current) || !current.getItem(1).isEmpty()) return false;
                var prototype = new ChestGoodsStore(level, List.of(), context.catalog().goods()).prototype(fuel);
                if (prototype.isEmpty()) return false;
                int taken = context.store(building).remove(fuel, Math.min(64, store.count(fuel)));
                current.setItem(1, prototype.get().copyWithCount(taken));
                current.setChanged();
                return taken > 0;
            }));
        }
        return Optional.empty();
    }

    /** Fells the nearest natural tree around the home and carries its logs as wood goods. */
    static Optional<Plan> chopTree(VillageContext context, MillVillagerEntity villager) {
        ServerLevel level = context.level();
        var log = WorldBlocks.nearest(level, centre(context.home()), 24, 10, state -> WorldBlocks.logGood(state).isPresent());
        if (log.isEmpty()) return Optional.empty();
        BlockPos base = log.get();
        while (WorldBlocks.logGood(level.getBlockState(base.below())).isPresent()) base = base.below();
        BlockPos trunk = base;
        return Optional.of(new Plan(trunk, () -> {
            int felled = 0;
            Deque<BlockPos> open = new ArrayDeque<>(List.of(trunk));
            Set<BlockPos> seen = new HashSet<>();
            while (!open.isEmpty() && felled < MAX_TREE_LOGS) {
                BlockPos pos = open.poll();
                if (!seen.add(pos)) continue;
                var good = WorldBlocks.logGood(level.getBlockState(pos));
                if (good.isEmpty()) continue;
                level.destroyBlock(pos, false, villager);
                villager.changeCarried(good.get(), 1);
                felled++;
                for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) open.add(pos.offset(dx, dy, dz));
            }
            return felled > 0;
        }));
    }

    /** Replants carried saplings (generic {@code sapling} is an oak sapling) around the home. */
    static Optional<Plan> plantCarriedSapling(VillageContext context, MillVillagerEntity villager, RandomGenerator random) {
        for (var entry : villager.carriedGoods().entrySet()) {
            if (!entry.getKey().startsWith("sapling") || entry.getValue() <= 0) continue;
            var prototype = new ChestGoodsStore(context.level(), List.of(), context.catalog().goods()).prototype(entry.getKey());
            if (prototype.isEmpty() || !(prototype.get().getItem() instanceof net.minecraft.world.item.BlockItem blockItem)) continue;
            BlockState plant = blockItem.getBlock().defaultBlockState();
            String good = entry.getKey();
            long saplings = countNear(context.level(), centre(context.home()), 16, state -> state.is(BlockTags.SAPLINGS));
            if (saplings >= 12) return Optional.empty();
            return spot(context.level(), centre(context.home()), 16, plant, random).map(pos -> new Plan(pos, () -> {
                if (!context.level().getBlockState(pos).isAir() || !plant.canSurvive(context.level(), pos) || villager.changeCarried(good, -1) == 0)
                    return false;
                context.level().setBlockAndUpdate(pos, plant);
                return true;
            }));
        }
        return Optional.empty();
    }

    private static long countNear(ServerLevel level, BlockPos centre, int radius, java.util.function.Predicate<BlockState> test) {
        long count = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -4; dy <= 4; dy++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
            if (level.isLoaded(cursor) && test.test(level.getBlockState(cursor))) count++;
        }
        return count;
    }

    private static boolean belowVillageLimits(GoalDefinition goal, GoodsStore store) {
        for (var limit : goal.villageLimits().entrySet()) if (store.count(limit.getKey()) >= limit.getValue()) return false;
        return true;
    }

    static boolean isAirOrPlant(BlockState state) { return state.isAir() || state.is(Blocks.SHORT_GRASS); }
}
