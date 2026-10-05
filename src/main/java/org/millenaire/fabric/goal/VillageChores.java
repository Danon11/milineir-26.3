package org.millenaire.fabric.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.millenaire.fabric.FabricBuildingState;
import org.millenaire.fabric.FabricVillagerState;
import org.millenaire.fabric.MillenaireCommands;
import org.millenaire.fabric.economy.TradeCatalog;
import org.millenaire.fabric.village.VillagePopulation;
import org.millenaire.fabric.villager.MillVillagerEntity;
import org.millenaire.fabric.villager.VillagerProfile;
import org.millenaire.fabric.FabricSettlementState;
import org.millenaire.fabric.villager.VillagerSpawning;

import java.util.*;
import java.util.random.RandomGenerator;

/**
 * Built-in village chores of the original mod that move goods between buildings or tend animals and special
 * crops: household supply ({@code getgoodshousehold}), shop deliveries ({@code deliverresourcesshop},
 * {@code gethousethresources}), picking up dropped goods ({@code gathergoods}), breeding, shearing, sugar cane
 * and fishing. Each returns a plan for the brain to walk to and carry out, or nothing when there is no work.
 */
final class VillageChores {
    /** A carried load worth a trip on its own; smaller loads wait. */
    static final int LOAD = 16;
    /** Most goods moved per trip. */
    static final int TRIP = 64;
    static final int ANIMAL_RADIUS = 12, MAX_ANIMALS_PER_MARKER = 6;

    private VillageChores() {}

    // ------------------------------------------------------------------ goods logistics

    /** Goods the residents of a building keep in stock ({@code requiredgood}), the largest amount per good. */
    static Map<String, Integer> required(VillageContext context, FabricBuildingState.PlacedBuilding building) {
        String key = VillagePopulation.baseKey(VillagePopulation.buildingKey(building.plan(), building.origin()));
        var profiles = VillagerSpawning.snapshot().profiles();
        Map<String, Integer> result = new HashMap<>();
        for (var record : FabricVillagerState.get(context.level().getServer()).villagers()) {
            if (!record.alive() || !VillagePopulation.baseKey(record.building()).equals(key)) continue;
            VillagerProfile profile = profiles.get(record.culture() + "/" + record.type());
            if (profile != null) profile.requiredGoods().forEach((good, count) -> result.merge(good, count, Math::max));
        }
        return result;
    }

    static Optional<TradeCatalog.Shop> shop(VillageContext context, FabricBuildingState.PlacedBuilding building) {
        var plan = context.catalog().plan(building.plan());
        if (plan == null) return Optional.empty();
        String shopId = plan.parameters().getOrDefault("shop", List.of("")).getLast().trim().toLowerCase(Locale.ROOT);
        if (shopId.isEmpty()) return Optional.empty();
        var culture = MillenaireCommands.tradeCatalog() == null ? null : MillenaireCommands.tradeCatalog().cultures().get(plan.culture());
        return culture == null ? Optional.empty() : Optional.ofNullable(culture.shops().get(shopId));
    }

    /** Carried goods beyond the villager's own starting stock (seeds, tools it keeps). */
    static int spare(MillVillagerEntity villager, VillagerProfile profile, String good) {
        return Math.max(0, villager.carried(good) - profile.startingInventory().getOrDefault(good, 0));
    }

    /** Moves carried goods into a building's store; returns whether anything was stored. */
    static boolean unload(MillVillagerEntity villager, GoodsStore store, Map<String, Integer> goods) {
        boolean stored = false;
        for (var entry : goods.entrySet()) {
            int added = store.add(entry.getKey(), entry.getValue());
            villager.changeCarried(entry.getKey(), -added);
            stored |= added > 0;
        }
        return stored;
    }

    /** {@code deliverresourcesshop}: carried goods a shop takes deliveries of go to that shop. */
    static Optional<ResourceGoals.Plan> deliverToShop(VillageContext context, MillVillagerEntity villager, VillagerProfile profile, boolean anyLoad) {
        for (var building : context.buildings()) {
            var shop = shop(context, building);
            if (shop.isEmpty() || shop.get().deliverTo().isEmpty()) continue;
            Map<String, Integer> load = new LinkedHashMap<>();
            for (String good : shop.get().deliverTo()) {
                int spare = spare(villager, profile, good);
                if (spare > 0) load.put(good, spare);
            }
            int total = load.values().stream().mapToInt(Integer::intValue).sum();
            if (total == 0 || !anyLoad && total < LOAD) continue;
            var store = context.store(building);
            if (load.keySet().stream().noneMatch(good -> store.space(good) > 0)) continue;
            return Optional.of(new ResourceGoals.Plan(context.workPoint(building), () -> unload(villager, context.store(building), load)));
        }
        return Optional.empty();
    }

    /**
     * {@code gethousethresources}: fetch goods a shop takes deliveries of from the other buildings, leaving what
     * their residents keep in stock; the carried load is then delivered to the shop.
     */
    static Optional<ResourceGoals.Plan> collectForShops(VillageContext context, MillVillagerEntity villager, VillagerProfile profile) {
        var delivery = deliverToShop(context, villager, profile, true);
        if (delivery.isPresent()) return delivery;
        for (var shopBuilding : context.buildings()) {
            var shop = shop(context, shopBuilding);
            if (shop.isEmpty() || shop.get().deliverTo().isEmpty()) continue;
            var shopStore = context.store(shopBuilding);
            for (var source : context.buildings()) {
                if (source == shopBuilding) continue;
                var store = context.store(source);
                if (store.isEmpty()) continue;
                var keep = required(context, source);
                for (String good : shop.get().deliverTo()) {
                    int surplus = store.count(good) - keep.getOrDefault(good, 0);
                    int amount = Math.min(Math.min(surplus, TRIP), shopStore.space(good));
                    if (amount < LOAD) continue;
                    return Optional.of(new ResourceGoals.Plan(context.workPoint(source), () -> {
                        int taken = context.store(source).remove(good, amount);
                        villager.changeCarried(good, taken);
                        return taken > 0;
                    }));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * {@code getgoodshousehold}: bring home what the household keeps in stock, fetching it from the buildings
     * that have more than their own residents need.
     */
    static Optional<ResourceGoals.Plan> supplyHousehold(VillageContext context, MillVillagerEntity villager) {
        var home = context.home();
        var needs = required(context, home);
        if (needs.isEmpty()) return Optional.empty();
        var homeStore = context.store(home);
        if (homeStore.isEmpty()) return Optional.empty();
        Map<String, Integer> carriedNeeded = new LinkedHashMap<>();
        Map<String, Integer> missing = new LinkedHashMap<>();
        for (var need : needs.entrySet()) {
            int lacking = need.getValue() - homeStore.count(need.getKey());
            if (lacking <= 0) continue;
            int carried = villager.carried(need.getKey());
            if (carried > 0) carriedNeeded.put(need.getKey(), Math.min(carried, lacking));
            else missing.put(need.getKey(), lacking);
        }
        if (!carriedNeeded.isEmpty())
            return Optional.of(new ResourceGoals.Plan(context.workPoint(home), () -> unload(villager, context.store(home), carriedNeeded)));
        for (var need : missing.entrySet())
            for (var source : context.buildings()) {
                if (source == home) continue;
                var store = context.store(source);
                if (store.isEmpty()) continue;
                int available = store.count(need.getKey()) - required(context, source).getOrDefault(need.getKey(), 0);
                int amount = Math.min(Math.min(available, need.getValue()), TRIP);
                if (amount <= 0) continue;
                String good = need.getKey();
                return Optional.of(new ResourceGoals.Plan(context.workPoint(source), () -> {
                    int taken = context.store(source).remove(good, amount);
                    villager.changeCarried(good, taken);
                    return taken > 0;
                }));
            }
        return Optional.empty();
    }

    /** {@code gathergoods}: pick up dropped items of the villager's {@code collectgood} list near the village. */
    static Optional<ResourceGoals.Plan> gatherDropped(VillageContext context, MillVillagerEntity villager, VillagerProfile profile) {
        if (profile.collectGoods().isEmpty()) return Optional.empty();
        var store = new ChestGoodsStore(context.level(), List.of(), context.catalog().goods());
        Map<net.minecraft.world.item.Item, String> wanted = new HashMap<>();
        for (String good : profile.collectGoods()) store.prototype(good).ifPresent(stack -> wanted.putIfAbsent(stack.getItem(), good));
        if (wanted.isEmpty()) return Optional.empty();
        BlockPos home = new BlockPos(context.home().origin().x(), context.home().origin().y(), context.home().origin().z());
        var items = context.level().getEntitiesOfClass(ItemEntity.class, villager.getBoundingBox().inflate(20, 6, 20),
                item -> item.isAlive() && wanted.containsKey(item.getItem().getItem()) && item.blockPosition().closerThan(home, 40));
        return items.stream().min(Comparator.comparingDouble(villager::distanceToSqr)).map(item -> new ResourceGoals.Plan(item.blockPosition(), () -> {
            if (!item.isAlive() || item.distanceToSqr(villager) > 16) return false;
            villager.changeCarried(wanted.get(item.getItem().getItem()), item.getItem().getCount());
            item.discard();
            return true;
        }));
    }

    // ------------------------------------------------------------------ animals

    private static final Map<String, EntityType<?>> MARKERS = Map.of("cowspawn", net.minecraft.world.entity.EntityTypes.COW, "pigspawn", net.minecraft.world.entity.EntityTypes.PIG,
            "sheepspawn", net.minecraft.world.entity.EntityTypes.SHEEP, "chickenspawn", net.minecraft.world.entity.EntityTypes.CHICKEN);

    /** {@code breed}: pairs two adults at a pen of the home or of the village, while the pen is not full. */
    static Optional<ResourceGoals.Plan> breed(VillageContext context) {
        List<FabricBuildingState.PlacedBuilding> pens = new ArrayList<>();
        pens.add(context.home());
        context.buildings().stream().filter(b -> b != context.home()).forEach(pens::add);
        for (var building : pens)
            for (var marker : MARKERS.entrySet())
                for (BlockPos point : context.points(building, marker.getKey())) {
                    if (!context.level().isLoaded(point)) continue;
                    var animals = context.level().getEntitiesOfClass(Animal.class, new AABB(point).inflate(ANIMAL_RADIUS),
                            animal -> animal.getType() == marker.getValue() && animal.isAlive());
                    if (animals.size() >= MAX_ANIMALS_PER_MARKER) continue;
                    var ready = animals.stream().filter(animal -> !animal.isBaby() && animal.canFallInLove()).limit(2).toList();
                    if (ready.size() < 2) continue;
                    return Optional.of(new ResourceGoals.Plan(point, () -> {
                        boolean paired = false;
                        for (Animal animal : ready) if (animal.isAlive() && animal.canFallInLove()) { animal.setInLove(null); paired = true; }
                        return paired;
                    }));
                }
        return Optional.empty();
    }

    /** {@code shearsheep}: shears a woolly sheep near the home's pens; the wool is carried home. */
    static Optional<ResourceGoals.Plan> shear(VillageContext context, MillVillagerEntity villager, RandomGenerator random) {
        List<BlockPos> centres = new ArrayList<>(context.points(context.home(), "sheepspawn"));
        if (centres.isEmpty()) centres.add(context.workPoint(context.home()));
        for (BlockPos centre : centres) {
            var sheep = context.level().getEntitiesOfClass(Sheep.class, new AABB(centre).inflate(16), Sheep::readyForShearing);
            if (sheep.isEmpty()) continue;
            Sheep target = sheep.stream().min(Comparator.comparingDouble(villager::distanceToSqr)).get();
            return Optional.of(new ResourceGoals.Plan(target.blockPosition(), () -> {
                if (!target.isAlive() || !target.readyForShearing() || target.distanceToSqr(villager) > 16) return false;
                target.setSheared(true);
                target.playSound(net.minecraft.sounds.SoundEvents.SHEEP_SHEAR, 1.0F, 1.0F);
                villager.changeCarried("wool_" + target.getColor().getName().replace("_", ""), 1 + random.nextInt(3));
                return true;
            }));
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ special crops and fishing

    /** {@code plantsugarcane}: sugar cane on the {@code sugarcanesoil} points that can hold it. */
    static Optional<ResourceGoals.Plan> plantSugarCane(VillageContext context, FabricBuildingState.PlacedBuilding building) {
        ServerLevel level = context.level();
        for (BlockPos soil : context.points(building, "sugarcanesoil")) {
            BlockPos at = soil.above();
            if (!level.isLoaded(at) || !level.getBlockState(at).isAir() || !Blocks.SUGAR_CANE.defaultBlockState().canSurvive(level, at)) continue;
            return Optional.of(new ResourceGoals.Plan(at, () -> {
                if (!level.getBlockState(at).isAir() || !Blocks.SUGAR_CANE.defaultBlockState().canSurvive(level, at)) return false;
                level.setBlockAndUpdate(at, Blocks.SUGAR_CANE.defaultBlockState());
                return true;
            }));
        }
        return Optional.empty();
    }

    /** {@code harvestsugarcane}: cuts the cane above the first block, which grows back. */
    static Optional<ResourceGoals.Plan> harvestSugarCane(VillageContext context, FabricBuildingState.PlacedBuilding building, MillVillagerEntity villager) {
        ServerLevel level = context.level();
        for (BlockPos soil : context.points(building, "sugarcanesoil")) {
            BlockPos second = soil.above(2);
            if (!level.isLoaded(second) || !level.getBlockState(second).is(Blocks.SUGAR_CANE)) continue;
            return Optional.of(new ResourceGoals.Plan(soil.above(), () -> {
                int cut = 0;
                for (BlockPos pos = second; level.getBlockState(pos).is(Blocks.SUGAR_CANE); pos = pos.above()) cut++;
                if (cut == 0) return false;
                // Top down, so no cane block is left floating.
                for (int i = cut - 1; i >= 0; i--) level.destroyBlock(second.above(i), false, villager);
                villager.changeCarried("sugarcane", cut);
                return true;
            }));
        }
        return Optional.empty();
    }

    /** {@code fish}: a spell at a fishing spot of the home or village, with a catch about every other time. */
    static Optional<ResourceGoals.Plan> fish(VillageContext context, MillVillagerEntity villager, RandomGenerator random) {
        List<BlockPos> spots = new ArrayList<>(context.points(context.home(), "fishingspot"));
        if (spots.isEmpty()) for (var building : context.buildings()) spots.addAll(context.points(building, "fishingspot"));
        if (spots.isEmpty()) return Optional.empty();
        BlockPos spot = spots.get(random.nextInt(spots.size()));
        return Optional.of(new ResourceGoals.Plan(spot, () -> {
            if (random.nextBoolean()) return false;
            villager.changeCarried("fishraw", 1 + random.nextInt(2));
            return true;
        }));
    }

    // ------------------------------------------------------------------ mud bricks

    private static Optional<net.minecraft.world.level.block.state.BlockState> mudBrick() {
        var id = net.minecraft.resources.Identifier.fromNamespaceAndPath("millenaire", "mudbrick");
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(id).map(net.minecraft.world.level.block.Block::defaultBlockState);
    }

    /** {@code drybrick}: lays a mud brick out to dry on a free {@code brickspot}. */
    static Optional<ResourceGoals.Plan> layBricks(VillageContext context, FabricBuildingState.PlacedBuilding building) {
        var brick = mudBrick();
        if (brick.isEmpty()) return Optional.empty();
        ServerLevel level = context.level();
        for (BlockPos spot : context.points(building, "brickspot")) {
            if (!level.isLoaded(spot) || !level.getBlockState(spot).isAir()) continue;
            return Optional.of(new ResourceGoals.Plan(spot, () -> {
                if (!level.getBlockState(spot).isAir()) return false;
                level.setBlockAndUpdate(spot, brick.get());
                return true;
            }));
        }
        return Optional.empty();
    }

    /** {@code gatherbrick}: collects the dried bricks once every spot of the yard is full. */
    static Optional<ResourceGoals.Plan> gatherBricks(VillageContext context, FabricBuildingState.PlacedBuilding building, MillVillagerEntity villager) {
        var brick = mudBrick();
        var spots = context.points(building, "brickspot");
        if (brick.isEmpty() || spots.isEmpty()) return Optional.empty();
        ServerLevel level = context.level();
        if (!spots.stream().allMatch(spot -> level.isLoaded(spot) && level.getBlockState(spot).is(brick.get().getBlock()))) return Optional.empty();
        return Optional.of(new ResourceGoals.Plan(spots.getFirst(), () -> {
            int gathered = 0;
            for (BlockPos spot : spots)
                if (level.getBlockState(spot).is(brick.get().getBlock())) {
                    level.setBlockAndUpdate(spot, Blocks.AIR.defaultBlockState());
                    gathered++;
                }
            villager.changeCarried("mudbrick", gathered);
            return gathered > 0;
        }));
    }

    // ------------------------------------------------------------------ minor crops and crafts

    private static Optional<Block> millBlock(String name) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.Identifier.fromNamespaceAndPath("millenaire", name));
    }

    /** {@code plantwarts}: nether wart on the {@code netherwartsoil} points (the soil is soul sand). */
    static Optional<ResourceGoals.Plan> plantWarts(VillageContext context, FabricBuildingState.PlacedBuilding building) {
        ServerLevel level = context.level();
        for (BlockPos soil : context.points(building, "netherwartsoil")) {
            BlockPos at = soil.above();
            if (!level.isLoaded(at) || !level.getBlockState(at).isAir()) continue;
            return Optional.of(new ResourceGoals.Plan(at, () -> {
                if (!level.getBlockState(soil).is(Blocks.SOUL_SAND)) level.setBlockAndUpdate(soil, Blocks.SOUL_SAND.defaultBlockState());
                if (!level.getBlockState(at).isAir()) return false;
                level.setBlockAndUpdate(at, Blocks.NETHER_WART.defaultBlockState());
                return true;
            }));
        }
        return Optional.empty();
    }

    /** {@code harvestwarts}: ripe nether wart is picked and replanted. */
    static Optional<ResourceGoals.Plan> harvestWarts(VillageContext context, FabricBuildingState.PlacedBuilding building, MillVillagerEntity villager, RandomGenerator random) {
        ServerLevel level = context.level();
        for (BlockPos soil : context.points(building, "netherwartsoil")) {
            BlockPos at = soil.above();
            var state = level.isLoaded(at) ? level.getBlockState(at) : null;
            if (state == null || !state.is(Blocks.NETHER_WART) || state.getValue(net.minecraft.world.level.block.NetherWartBlock.AGE) < 3) continue;
            return Optional.of(new ResourceGoals.Plan(at, () -> {
                var now = level.getBlockState(at);
                if (!now.is(Blocks.NETHER_WART) || now.getValue(net.minecraft.world.level.block.NetherWartBlock.AGE) < 3) return false;
                level.setBlockAndUpdate(at, Blocks.NETHER_WART.defaultBlockState());
                villager.changeCarried("netherwart", 2 + random.nextInt(3));
                return true;
            }));
        }
        return Optional.empty();
    }

    /** {@code plantcocoa}: cocoa pods on the {@code cacaospot} points, against a neighbouring jungle log. */
    static Optional<ResourceGoals.Plan> plantCocoa(VillageContext context, FabricBuildingState.PlacedBuilding building) {
        ServerLevel level = context.level();
        for (BlockPos spot : context.points(building, "cacaospot")) {
            if (!level.isLoaded(spot) || !level.getBlockState(spot).isAir()) continue;
            for (var side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                if (!level.getBlockState(spot.relative(side)).is(net.minecraft.tags.BlockTags.JUNGLE_LOGS)) continue;
                var pod = Blocks.COCOA.defaultBlockState().setValue(net.minecraft.world.level.block.CocoaBlock.FACING, side);
                return Optional.of(new ResourceGoals.Plan(spot, () -> {
                    if (!level.getBlockState(spot).isAir() || !pod.canSurvive(level, spot)) return false;
                    level.setBlockAndUpdate(spot, pod);
                    return true;
                }));
            }
        }
        return Optional.empty();
    }

    /** {@code harvestcocoa}: ripe pods give cocoa beans (dye_brown) and are planted again. */
    static Optional<ResourceGoals.Plan> harvestCocoa(VillageContext context, FabricBuildingState.PlacedBuilding building, MillVillagerEntity villager, RandomGenerator random) {
        ServerLevel level = context.level();
        for (BlockPos spot : context.points(building, "cacaospot")) {
            var state = level.isLoaded(spot) ? level.getBlockState(spot) : null;
            if (state == null || !state.is(Blocks.COCOA) || state.getValue(net.minecraft.world.level.block.CocoaBlock.AGE) < 2) continue;
            return Optional.of(new ResourceGoals.Plan(spot, () -> {
                var now = level.getBlockState(spot);
                if (!now.is(Blocks.COCOA) || now.getValue(net.minecraft.world.level.block.CocoaBlock.AGE) < 2) return false;
                level.setBlockAndUpdate(spot, now.setValue(net.minecraft.world.level.block.CocoaBlock.AGE, 0));
                villager.changeCarried("dye_brown", 2 + random.nextInt(2));
                return true;
            }));
        }
        return Optional.empty();
    }

    /**
     * {@code gathersilk}: silkworm frames near the home ({@code silkwormfull}) give silk and become empty; an
     * empty frame is tended back to full in the next visit.
     */
    static Optional<ResourceGoals.Plan> gatherSilk(VillageContext context, MillVillagerEntity villager, RandomGenerator random) {
        var full = millBlock("silkwormfull");
        var empty = millBlock("silkwormempty");
        if (full.isEmpty() || empty.isEmpty()) return Optional.empty();
        BlockPos home = context.workPoint(context.home());
        ServerLevel level = context.level();
        var ripe = WorldBlocks.nearest(level, home, 16, 6, state -> state.is(full.get()));
        if (ripe.isPresent()) return Optional.of(new ResourceGoals.Plan(ripe.get(), () -> {
            if (!level.getBlockState(ripe.get()).is(full.get())) return false;
            level.setBlockAndUpdate(ripe.get(), empty.get().defaultBlockState());
            villager.changeCarried("silk", 1 + random.nextInt(2));
            return true;
        }));
        return WorldBlocks.nearest(level, home, 16, 6, state -> state.is(empty.get())).map(frame -> new ResourceGoals.Plan(frame, () -> {
            if (!level.getBlockState(frame).is(empty.get())) return false;
            level.setBlockAndUpdate(frame, full.get().defaultBlockState());
            return true;
        }));
    }

    /** {@code gathersnails}: snails from the snail soil near the home give purple dye. */
    static Optional<ResourceGoals.Plan> gatherSnails(VillageContext context, MillVillagerEntity villager, RandomGenerator random) {
        var soil = millBlock("snail_soil");
        if (soil.isEmpty()) return Optional.empty();
        BlockPos home = context.workPoint(context.home());
        return WorldBlocks.nearest(context.level(), home, 16, 6, state -> state.is(soil.get())).map(at -> new ResourceGoals.Plan(at.above(), () -> {
            villager.changeCarried("dye_purple", 1 + random.nextInt(2));
            return true;
        }));
    }

    /** {@code mining} as a plain goal: a few blocks from the home's stone, sand, gravel or clay sources. */
    static Optional<ResourceGoals.Plan> quarry(VillageContext context, MillVillagerEntity villager, RandomGenerator random) {
        Map<String, String> goods = Map.of("stonesource", "stone", "sandsource", "sand", "gravelsource", "gravel", "claysource", "clay");
        for (var entry : goods.entrySet()) {
            var sources = context.points(context.home(), entry.getKey());
            if (sources.isEmpty()) continue;
            BlockPos at = sources.get(random.nextInt(sources.size()));
            // Sources are inexhaustible in the original: the block stays and the goods come from it.
            return Optional.of(new ResourceGoals.Plan(at.above(), () -> {
                villager.changeCarried(entry.getValue(), 2 + random.nextInt(3));
                return true;
            }));
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ building materials

    /** The town hall's chests, or the home's when the village has no town hall. */
    private static FabricBuildingState.PlacedBuilding storehouse(VillageContext context) {
        return context.townhall().orElse(context.home());
    }

    /** Raw material a carried good counts as, through its item. */
    static Optional<org.millenaire.fabric.village.BuildingMaterials.Raw> rawOf(VillageContext context, String good) {
        var prototype = new ChestGoodsStore(context.level(), List.of(), context.catalog().goods()).prototype(good);
        if (prototype.isEmpty()) return Optional.empty();
        for (var raw : org.millenaire.fabric.village.BuildingMaterials.Raw.values())
            if (org.millenaire.fabric.village.BuildingMaterials.provides(raw, prototype.get()) > 0) return Optional.of(raw);
        return Optional.empty();
    }

    /** Takes carried building materials the village needs to the town hall, once there are enough for a trip. */
    static Optional<ResourceGoals.Plan> deliverMaterials(VillageContext context, MillVillagerEntity villager,
                                                         Map<org.millenaire.fabric.village.BuildingMaterials.Raw, Integer> needs, VillagerProfile profile) {
        Map<String, Integer> load = new LinkedHashMap<>();
        for (var entry : villager.carriedGoods().entrySet()) {
            int spare = spare(villager, profile, entry.getKey());
            if (spare <= 0) continue;
            var raw = rawOf(context, entry.getKey());
            if (raw.isPresent() && needs.containsKey(raw.get())) load.put(entry.getKey(), spare);
        }
        int total = load.values().stream().mapToInt(Integer::intValue).sum();
        if (total < 8) return Optional.empty();
        var store = storehouse(context);
        return Optional.of(new ResourceGoals.Plan(context.workPoint(store), () -> unload(villager, context.store(store), load)));
    }

    private static boolean insideVillageBuilding(VillageContext context, BlockPos pos) {
        var origin = context.home().origin();
        var settlement = org.millenaire.fabric.FabricSettlementState.get(context.level().getServer())
                .containing(context.home().dimension(), origin.x(), origin.z());
        return settlement.map(s -> s.buildings().stream().anyMatch(b -> {
            var area = b.reservedArea();
            return pos.getX() >= area.minX() && pos.getX() <= area.maxX() && pos.getZ() >= area.minZ() && pos.getZ() <= area.maxZ();
        })).orElse(false);
    }

    /**
     * Builders gather what the village lacks themselves: they fell a tree for wood, or dig exposed stone, sand or
     * clay outside the village buildings (a few blocks per trip, the first block and its exposed neighbours).
     */
    static Optional<ResourceGoals.Plan> gatherMaterials(VillageContext context, MillVillagerEntity villager,
                                                        Map<org.millenaire.fabric.village.BuildingMaterials.Raw, Integer> needs) {
        if (needs.containsKey(org.millenaire.fabric.village.BuildingMaterials.Raw.WOOD)) {
            var chop = ResourceGoals.chopTree(context, villager);
            if (chop.isPresent()) return chop;
        }
        ServerLevel level = context.level();
        BlockPos centre = context.workPoint(storehouse(context));
        for (var raw : needs.keySet()) {
            java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> test = switch (raw) {
                case STONE -> state -> state.is(Blocks.STONE) || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE);
                case SAND -> state -> state.is(Blocks.SAND) || state.is(Blocks.RED_SAND);
                case CLAY -> state -> state.is(Blocks.CLAY);
                default -> null;
            };
            if (test == null) continue;
            String good = raw == org.millenaire.fabric.village.BuildingMaterials.Raw.STONE ? "cobblestone" : raw.good;
            // Only surface blocks outside the village buildings: villagers dig open pits, not under houses.
            BlockPos first = null;
            double best = Double.MAX_VALUE;
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int dx = -40; dx <= 40; dx += 2)
                for (int dz = -40; dz <= 40; dz += 2) {
                    int x = centre.getX() + dx, z = centre.getZ() + dz;
                    if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
                    int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    cursor.set(x, y, z);
                    double distance = dx * dx + dz * dz;
                    if (distance < best && test.test(level.getBlockState(cursor)) && !insideVillageBuilding(context, cursor)) {
                        best = distance;
                        first = cursor.immutable();
                    }
                }
            if (first == null) continue;
            BlockPos target = first;
            return Optional.of(new ResourceGoals.Plan(target.above(), () -> {
                int dug = 0;
                List<BlockPos> candidates = new ArrayList<>(List.of(target));
                for (var side : net.minecraft.core.Direction.Plane.HORIZONTAL) candidates.add(target.relative(side));
                candidates.add(target.below());
                for (BlockPos pos : candidates) {
                    if (dug >= 4 || !test.test(level.getBlockState(pos)) || insideVillageBuilding(context, pos)) continue;
                    level.destroyBlock(pos, false, villager);
                    dug++;
                }
                if (dug > 0) villager.changeCarried(good, raw == org.millenaire.fabric.village.BuildingMaterials.Raw.CLAY ? dug * 4 : dug);
                return dug > 0;
            }));
        }
        return Optional.empty();
    }
}
