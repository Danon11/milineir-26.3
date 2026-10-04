package org.millenaire.fabric.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.millenaire.fabric.FabricBuildingState;
import org.millenaire.fabric.MillenaireCommands;
import org.millenaire.fabric.villager.MillVillagerEntity;
import org.millenaire.fabric.villager.VillagerSpawning;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chooses and runs one Millénaire goal at a time: sleep at night, data goals of the villager type during the
 * day, and the built-in rest, socialise and chat goals. A task walks to its target, works there for the goal
 * duration, then applies its effect. While no task runs, the vanilla wander goal moves the villager within
 * its village.
 */
public final class MillenaireBrain extends Goal {
    private static final int DECISION_INTERVAL = 40, TRAVEL_TIMEOUT = 900, NIGHT_START = 12500, NIGHT_END = 23500;
    private static final double ARRIVAL_DISTANCE_SQR = 2.5 * 2.5;
    /** Villagers per goal and building, for {@code maxsimultaneousinbuilding}. */
    private static final Map<String, Integer> ACTIVE = new ConcurrentHashMap<>();

    private final MillVillagerEntity villager;
    private final Map<String, Long> nextAllowed = new HashMap<>();
    private final SplittableRandom random = new SplittableRandom();
    private Task task;
    private int cooldown;

    public MillenaireBrain(MillVillagerEntity villager) {
        this.villager = villager;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** What the villager is doing, for status displays and commands. */
    public Optional<String> currentLabel() { return Optional.ofNullable(task).map(t -> t.label); }

    @Override
    public boolean canUse() {
        if (!(villager.level() instanceof ServerLevel)) return false;
        if (task != null) return true;
        if (--cooldown > 0) return false;
        cooldown = DECISION_INTERVAL + villager.getRandom().nextInt(20);
        task = choose();
        return task != null;
    }

    @Override
    public boolean canContinueToUse() { return task != null && !task.finished; }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void start() { if (task != null) task.begin(); }

    @Override
    public void tick() {
        if (task == null) return;
        task.tick();
    }

    @Override
    public void stop() {
        if (task != null) task.end();
        task = null;
        villager.getNavigation().stop();
    }

    private static boolean night(long dayTime) {
        long time = Math.floorMod(dayTime, 24000L);
        return time >= NIGHT_START && time < NIGHT_END;
    }

    private Task choose() {
        ServerLevel level = (ServerLevel) villager.level();
        var context = VillageContext.of(level, MillenaireCommands.contentCatalog(), villager.building()).orElse(null);
        long dayTime = level.getDefaultClockTime();
        if (context != null) {
            var origin = context.home().origin();
            villager.setHomeTo(new BlockPos(origin.x(), origin.y(), origin.z()), 48);
            if (night(dayTime)) {
                var beds = context.points(context.home(), "sleepingPos");
                if (!beds.isEmpty()) return new SleepTask(beds.get(Math.floorMod(villager.getUUID().hashCode(), beds.size())));
            }
        }
        if (villager.isSleeping()) villager.stopSleeping();
        var profile = VillagerSpawning.snapshot().profiles().get(villager.profileId());
        if (profile == null) return null;
        var goals = MillenaireCommands.goalCatalog();
        long now = level.getGameTime();
        Task best = null;
        int bestScore = Integer.MIN_VALUE;
        for (String name : profile.goals()) {
            if (nextAllowed.getOrDefault(name, 0L) > now) continue;
            Task candidate;
            int score;
            var definition = goals.get(name);
            if (definition.isPresent()) {
                var goal = definition.get();
                if (!goal.activeAt(dayTime) || context == null || night(dayTime) && !goal.leisure()) continue;
                candidate = dataTask(goal, context);
                score = GoalRules.score(goal, random);
            } else {
                candidate = builtInTask(name, context);
                score = builtInPriority(name) + villager.getRandom().nextInt(10);
            }
            if (candidate != null && score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static int builtInPriority(String name) {
        return switch (name) {
            case "chat" -> 15;
            case "gosocialise" -> 10;
            case "gorest" -> 5;
            default -> 0;
        };
    }

    private Task builtInTask(String name, VillageContext context) {
        return switch (name) {
            case "gorest" -> context == null ? null : new IdleTask("rest", context.leisurePoint(context.home())
                    .orElse(context.workPoint(context.home())), 200 + villager.getRandom().nextInt(200), name);
            case "gosocialise" -> {
                if (context == null) yield null;
                var place = context.townhall().flatMap(context::leisurePoint).or(() -> context.townhall().map(context::workPoint));
                yield place.map(pos -> new IdleTask("socialise", pos, 300 + villager.getRandom().nextInt(300), name)).orElse(null);
            }
            case "chat" -> {
                var partner = villager.level().getEntitiesOfClass(MillVillagerEntity.class, villager.getBoundingBox().inflate(12),
                        other -> other != villager && !other.isSleeping()).stream().findAny();
                yield partner.map(other -> (Task) new ChatTask(other)).orElse(null);
            }
            default -> null; // other built-in goals are not ported yet
        };
    }

    private Task dataTask(GoalDefinition goal, VillageContext context) {
        List<FabricBuildingState.PlacedBuilding> places = goal.townhallGoal() ? context.townhall().stream().toList()
                : goal.buildingTags().isEmpty() ? List.of(context.home()) : context.withTags(goal.buildingTags());
        GoodsStore townhall = context.townhall().map(context::store).orElse(null);
        for (var building : places) {
            String slot = goal.key() + "@" + building.plan() + building.origin();
            if (goal.maxInBuilding() > 0 && ACTIVE.getOrDefault(slot, 0) >= goal.maxInBuilding()) continue;
            var store = context.store(building);
            Task task = switch (goal.kind()) {
                case CRAFTING -> GoalRules.canCraft(goal, store, townhall) ? new WorkTask(goal, slot, context.workPoint(building),
                        () -> GoalRules.craft(goal, context.store(building), context.townhall().map(context::store).orElse(null))) : null;
                case COOKING -> {
                    int batch = GoalRules.cookBatch(goal, store, townhall);
                    if (batch == 0) yield null;
                    var furnaces = context.points(building, "furnaces");
                    BlockPos at = furnaces.isEmpty() ? context.workPoint(building) : furnaces.getFirst();
                    yield new WorkTask(goal, slot, at, () -> {
                        var current = context.store(building);
                        int cooked = current.remove(goal.itemToCook(), batch);
                        current.add(GoalRules.cookedGood(goal).orElseThrow(), cooked);
                        return cooked > 0;
                    });
                }
                case TAKE_FROM_BUILDING -> {
                    if (building == context.home()) yield null;
                    var moves = GoalRules.pickup(goal, store, context.store(context.home()));
                    yield moves.isEmpty() ? null : new WorkTask(goal, slot, context.workPoint(building), () -> {
                        var source = context.store(building);
                        var home = context.store(context.home());
                        boolean moved = false;
                        for (var move : moves.entrySet()) {
                            int taken = source.remove(move.getKey(), move.getValue());
                            int stored = home.add(move.getKey(), taken);
                            if (stored < taken) source.add(move.getKey(), taken - stored);
                            moved |= stored > 0;
                        }
                        return moved;
                    });
                }
                case PLANTING -> plantingTask(goal, slot, context, building, store);
                case HARVESTING -> harvestingTask(goal, slot, context, building, store, townhall);
                case VISIT -> {
                    var at = context.leisurePoint(building).orElse(context.workPoint(building));
                    yield new IdleTask(goal.key(), at, Math.max(100, goal.durationTicks()), goal.key());
                }
                default -> null; // mining, block gathering, slaughter, saplings and furnace tending are not ported yet
            };
            if (task != null) return task;
        }
        return null;
    }

    private Task plantingTask(GoalDefinition goal, String slot, VillageContext context, FabricBuildingState.PlacedBuilding building, GoodsStore store) {
        Optional<BlockState> crop = cropState(goal);
        if (crop.isEmpty()) return null;
        String seed = goal.source().first("seed", "").trim().toLowerCase(Locale.ROOT);
        if (!seed.isEmpty() && store.count(seed) <= 0) return null;
        ServerLevel level = context.level();
        for (BlockPos soil : context.points(building, GoalRules.soilFor(goal.cropType()))) {
            if (!level.isLoaded(soil) || !level.getBlockState(soil.above()).isAir()) continue;
            return new WorkTask(goal, slot, soil.above(), () -> {
                if (!level.getBlockState(soil.above()).isAir()) return false;
                if (!seed.isEmpty() && context.store(building).remove(seed, 1) == 0) return false;
                BlockState ground = level.getBlockState(soil);
                if (!(crop.get().getBlock() instanceof CropBlock) || ground.getBlock() instanceof FarmlandBlock) {
                    // flowers grow on the existing dirt or grass
                } else if (ground.is(Blocks.DIRT) || ground.is(Blocks.GRASS_BLOCK)) {
                    level.setBlockAndUpdate(soil, Blocks.FARMLAND.defaultBlockState());
                }
                if (!crop.get().canSurvive(level, soil.above())) return false;
                level.setBlockAndUpdate(soil.above(), crop.get());
                return true;
            });
        }
        return null;
    }

    private Task harvestingTask(GoalDefinition goal, String slot, VillageContext context, FabricBuildingState.PlacedBuilding building,
                                GoodsStore store, GoodsStore townhall) {
        if (!GoalRules.belowLimits(goal, store, townhall)) return null;
        Optional<BlockState> crop = cropState(goal);
        if (crop.isEmpty()) return null;
        ServerLevel level = context.level();
        for (BlockPos soil : context.points(building, GoalRules.soilFor(goal.cropType()))) {
            BlockPos plant = soil.above();
            if (!level.isLoaded(plant) || !ripe(level.getBlockState(plant), crop.get())) continue;
            return new WorkTask(goal, slot, plant, () -> {
                if (!ripe(level.getBlockState(plant), crop.get())) return false;
                level.destroyBlock(plant, false, villager);
                var target = context.store(building);
                GoalRules.rollHarvest(goal, random).forEach(target::add);
                return true;
            });
        }
        return null;
    }

    private static boolean ripe(BlockState state, BlockState crop) {
        if (!state.is(crop.getBlock())) return false;
        return !(state.getBlock() instanceof CropBlock cropBlock) || cropBlock.isMaxAge(state);
    }

    /** Block placed by planting goals: vanilla or Millénaire crops, or the legacy flower names. */
    static Optional<BlockState> cropState(GoalDefinition goal) {
        String crop = goal.cropType().toLowerCase(Locale.ROOT);
        if (crop.equals("flower")) {
            String plant = goal.blockState().toLowerCase(Locale.ROOT);
            String id;
            if (plant.startsWith("yellow_flower")) id = "dandelion";
            else if (plant.startsWith("red_flower;type=")) id = plant.substring("red_flower;type=".length()).split("[,;]")[0];
            else return Optional.empty(); // double plants need two-block placement
            return block(Identifier.withDefaultNamespace(id));
        }
        if (crop.isEmpty()) return Optional.empty();
        Identifier id = crop.contains(":") ? Identifier.tryParse(crop) : Identifier.withDefaultNamespace(crop);
        return id == null ? Optional.empty() : block(id);
    }

    private static Optional<BlockState> block(Identifier id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) return Optional.empty();
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        return block == Blocks.AIR ? Optional.empty() : Optional.of(block.defaultBlockState());
    }

    /** Walk to a point, wait there, then act. */
    private abstract class Task {
        final String label;
        final BlockPos target;
        int workTicks, travelTicks;
        boolean arrived, finished;

        Task(String label, BlockPos target, int workTicks) {
            this.label = label;
            this.target = target;
            this.workTicks = workTicks;
        }

        void begin() { moveTowards(); }

        void moveTowards() {
            villager.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.6);
        }

        Vec3 lookTarget() { return Vec3.atCenterOf(target); }

        void tick() {
            if (!arrived) {
                if (villager.position().distanceToSqr(Vec3.atBottomCenterOf(target)) <= ARRIVAL_DISTANCE_SQR) {
                    arrived = true;
                    villager.getNavigation().stop();
                    onArrival();
                } else if (++travelTicks > TRAVEL_TIMEOUT) {
                    finished = true;
                } else if (travelTicks % 40 == 0 || villager.getNavigation().isDone()) {
                    moveTowards();
                }
                return;
            }
            villager.getLookControl().setLookAt(lookTarget());
            if (--workTicks <= 0) {
                finished = true;
                complete();
            } else working();
        }

        void onArrival() {}
        void working() {}
        void complete() {}
        void end() {}
    }

    private final class IdleTask extends Task {
        private final String goalName;
        IdleTask(String label, BlockPos target, int ticks, String goalName) {
            super(label, target, ticks);
            this.goalName = goalName;
        }
        @Override void complete() {
            nextAllowed.put(goalName, villager.level().getGameTime() + 200);
        }
    }

    private final class SleepTask extends Task {
        SleepTask(BlockPos bed) { super("sleep", bed, Integer.MAX_VALUE); }
        @Override void onArrival() { villager.startSleeping(target); }
        @Override void working() {
            if (villager.tickCount % 100 == 0 && !night(((ServerLevel) villager.level()).getDefaultClockTime())) finished = true;
        }
        @Override void end() { if (villager.isSleeping()) villager.stopSleeping(); }
    }

    private final class ChatTask extends Task {
        private final MillVillagerEntity partner;
        ChatTask(MillVillagerEntity partner) {
            super("chat", partner.blockPosition(), 100);
            this.partner = partner;
        }
        @Override void moveTowards() { villager.getNavigation().moveTo(partner, 0.6); }
        @Override Vec3 lookTarget() { return partner.getEyePosition(); }
        @Override void working() {
            if (!partner.isAlive() || partner.distanceToSqr(villager) > 64) finished = true;
            else partner.getLookControl().setLookAt(villager);
        }
        @Override void complete() { nextAllowed.put("chat", villager.level().getGameTime() + 1200); }
    }

    private final class WorkTask extends Task {
        private final GoalDefinition goal;
        private final String slot;
        private final java.util.function.BooleanSupplier effect;

        WorkTask(GoalDefinition goal, String slot, BlockPos target, java.util.function.BooleanSupplier effect) {
            super(goal.key(), target, Math.min(goal.durationTicks(), 600));
            this.goal = goal;
            this.slot = slot;
            this.effect = effect;
        }

        @Override void begin() {
            ACTIVE.merge(slot, 1, Integer::sum);
            hold(InteractionHand.MAIN_HAND, goal.heldItems().isEmpty() ? null : goal.heldItems().getFirst());
            super.begin();
        }

        @Override void working() {
            if (workTicks % 20 == 0) villager.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT);
        }

        @Override void complete() {
            effect.getAsBoolean();
            if (goal.reoccurDelayTicks() > 0) nextAllowed.put(goal.key(), villager.level().getGameTime() + goal.reoccurDelayTicks());
        }

        @Override void end() {
            ACTIVE.computeIfPresent(slot, (key, count) -> count <= 1 ? null : count - 1);
            villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }

        private void hold(InteractionHand hand, String good) {
            if (good == null) return;
            var prototype = new ChestGoodsStore((ServerLevel) villager.level(), List.of(), MillenaireCommands.contentCatalog().goods()).prototype(good);
            prototype.ifPresent(stack -> villager.setItemInHand(hand, stack.copyWithCount(1)));
        }
    }
}
