package org.millenaire.fabric.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.millenaire.fabric.storage.BuildingBinding;
import org.millenaire.fabric.storage.VillageChestBlockEntity;
import org.millenaire.fabric.storage.VillagePanelBlockEntity;
import org.millenaire.fabric.economy.LegacyGoodsCatalog;
import org.millenaire.fabric.economy.StartingStock;
import org.millenaire.fabric.trees.FruitTreeGenerator;
import org.millenaire.fabric.LegacyContentRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.util.*;

/** Manual structure placement. All palette and destination checks precede the first write. */
public final class BuildingPlacement {
    public static final int MAX_BLOCKS = 1 << 22;
    public record Change(BlockPos pos, BlockState state, boolean secondPass) {}
    public enum SetupKind { CHEST, PANEL, SPAWNER, DISPENSER }
    public record Setup(BlockPos pos, SetupKind kind, BuildingBinding binding) {
        public Setup { pos = pos.immutable(); Objects.requireNonNull(kind); Objects.requireNonNull(binding); }
    }
    public record Prepared(List<Change> changes, List<String> issues,
                           Map<String, List<LegacyBuildingPlan.Position>> servicePoints, List<Setup> setups,
                           List<StartingStock.Inventory> startingStock, List<BlockPos> treeRoots) {
        public Prepared {
            changes = List.copyOf(changes); issues = List.copyOf(issues); setups = List.copyOf(setups);
            startingStock = List.copyOf(startingStock); treeRoots = List.copyOf(treeRoots);
            Map<String, List<LegacyBuildingPlan.Position>> services = new LinkedHashMap<>();
            servicePoints.forEach((key, positions) -> services.put(key, List.copyOf(positions)));
            servicePoints = Collections.unmodifiableMap(services);
        }
        public Prepared(List<Change> changes, List<String> issues, Map<String, List<LegacyBuildingPlan.Position>> servicePoints) {
            this(changes, issues, servicePoints, List.of(), List.of(), List.of());
        }
        public Prepared(List<Change> changes, List<String> issues, Map<String, List<LegacyBuildingPlan.Position>> servicePoints, List<Setup> setups) {
            this(changes, issues, servicePoints, setups, List.of(), List.of());
        }
        public Prepared(List<Change> changes, List<String> issues, Map<String, List<LegacyBuildingPlan.Position>> servicePoints,
                        List<Setup> setups, List<StartingStock.Inventory> startingStock) {
            this(changes, issues, servicePoints, setups, startingStock, List.of());
        }
        public boolean supported() { return issues.isEmpty(); }
    }
    public interface WorldAccess {
        BlockState get(BlockPos pos);
        String rejection(BlockPos pos, boolean replace);
        boolean set(BlockPos pos, BlockState state);
        default String setupRejection(Setup setup) { return "World does not support block entity setup at " + setup.pos(); }
        default void initialize(Setup setup) { throw new UnsupportedOperationException("Block entity setup is unavailable"); }
        default String stockRejection(StartingStock.Inventory stock) { return "World does not support starting stock at " + stock.pos(); }
        default void fill(StartingStock.Inventory stock) { throw new UnsupportedOperationException("Starting stock is unavailable"); }
        default void discardStartingStock(List<StartingStock.Inventory> stocks) {}
        default void discardSetupContents(List<Setup> setups) {}
        default void finish(List<Change> changes) {}
    }
    @FunctionalInterface
    interface TreePlanner {
        Map<BlockPos, BlockState> plan(String marker, BlockPos origin, long seed);
    }
    private BuildingPlacement() {}

    public static Prepared prepare(LegacyBuildingPlan plan, LegacyPalette palette, BlockPos origin, int orientation) throws IOException {
        return prepare(plan, palette, origin, orientation, LegacyBlockStateResolver::resolve);
    }

    static Prepared prepare(LegacyBuildingPlan plan, LegacyPalette palette, BlockPos origin, int orientation,
                            java.util.function.Function<LegacyPalette.Point, BlockState> resolver) throws IOException {
        return prepare(plan, palette, origin, orientation, resolver, LegacyGoodsCatalog.empty(), 0);
    }

    public static Prepared prepare(LegacyBuildingPlan plan, LegacyPalette palette, BlockPos origin, int orientation,
                                   LegacyGoodsCatalog goods, long seed) throws IOException {
        return prepare(plan, palette, origin, orientation, LegacyBlockStateResolver::resolve, goods, seed);
    }

    static Prepared prepare(LegacyBuildingPlan plan, LegacyPalette palette, BlockPos origin, int orientation,
                            java.util.function.Function<LegacyPalette.Point, BlockState> resolver,
                            LegacyGoodsCatalog goods, long seed) throws IOException {
        return prepare(plan, palette, origin, orientation, resolver, goods, seed, FruitTreeGenerator::plannedBlocks);
    }

    static Prepared prepare(LegacyBuildingPlan plan, LegacyPalette palette, BlockPos origin, int orientation,
                            java.util.function.Function<LegacyPalette.Point, BlockState> resolver,
                            LegacyGoodsCatalog goods, long seed, TreePlanner trees) throws IOException {
        if (orientation < 0 || orientation > 3) throw new IllegalArgumentException("Orientation must be 0..3");
        var decoded = plan.decode(palette);
        long cells = (long) decoded.width() * decoded.length() * decoded.floors();
        if (cells > MAX_BLOCKS) return new Prepared(List.of(), List.of("Plan exceeds " + MAX_BLOCKS + " cells"), Map.of());
        Map<Integer, BlockState> states = new HashMap<>();
        Set<Integer> preserve = new HashSet<>();
        Set<Integer> rejected = new HashSet<>();
        Set<String> issues = new TreeSet<>();
        Set<String> markers = Set.of("sleepingPos", "sellingPos", "craftingPos", "defendingPos", "shelterPos", "pathStartPos", "leisurePos",
                "stall", "brickspot", "healingspot", "fishingspot");
        Set<String> animalMarkers = Set.of("cowspawn", "pigspawn", "sheepspawn", "chickenspawn", "squidspawn", "wolfspawn", "polarbearspawn");
        for (int y = 0; y < decoded.floors(); y++) for (int z = 0; z < plan.length(); z++) for (int x = 0; x < plan.width(); x++) {
            int color = decoded.colorAt(x, y, z);
            if (states.containsKey(color) || preserve.contains(color) || rejected.contains(color)) continue;
            var point = palette.points().get(color);
            if (point == null) { rejected.add(color); issues.add("Unknown palette color: " + Integer.toHexString(color)); continue; }
            try {
                BlockState state;
                if (!point.special()) state = resolver.apply(point);
                else if (chestMarker(point.label())) {
                    Direction facing = chestFacing(point.label());
                    state = resolver.apply(new LegacyPalette.Point(color, point.label(), "millenaire:locked_chest",
                            "facing=" + facing.getName(), false, "", "", 0));
                }
                else if (point.label().equals("signwallGuess") || point.label().equals("plainSignGuess")) {
                    state = resolver.apply(new LegacyPalette.Point(color, point.label(), point.label().equals("signwallGuess")
                            ? "millenaire:panel" : "minecraft:oak_wall_sign", "facing=north", true, "", "", 0));
                }
                else if (point.label().equals("preserveground") || point.label().equals("cacaospot")
                        || (animalMarkers.contains(point.label()) && !point.label().equals("squidspawn")) || (point.label().equals("empty") && (plan.upgrade() > 0
                        || plan.parameters().getOrDefault("issubbuilding", List.of()).contains("true")))) { preserve.add(color); continue; }
                else if (point.label().equals("empty") || markers.contains(point.label())) state = Blocks.AIR.defaultBlockState();
                else if (point.label().equals("squidspawn")) state = Blocks.WATER.defaultBlockState();
                else if (point.label().equals("brewingstand")) state = Blocks.BREWING_STAND.defaultBlockState();
                else if (FruitTreeGenerator.supportsMarker(point.label())) state = Blocks.AIR.defaultBlockState();
                else if (point.label().equals("torchGuess")) state = Blocks.TORCH.defaultBlockState();
                else if (point.label().equals("ladderGuess")) state = Blocks.LADDER.defaultBlockState();
                else if (point.label().equals("furnaceGuess")) state = Blocks.FURNACE.defaultBlockState();
                else if (point.label().equals("grass") || Set.of("soil", "ricesoil", "turmericsoil", "maizesoil", "carrotsoil", "potatosoil", "flowersoil", "sugarcanesoil", "vinesoil", "cottonsoil").contains(point.label())) state = Blocks.DIRT.defaultBlockState();
                else if (point.label().equals("netherwartsoil")) state = Blocks.SOUL_SAND.defaultBlockState();
                else if (point.label().equals("silkwormblock")) state = resolver.apply(new LegacyPalette.Point(color,
                        point.label(), "millenaire:silk_worm", "progress=0", false, "", "", 0));
                else if (point.label().equals("snailsoilblock")) state = resolver.apply(new LegacyPalette.Point(color,
                        point.label(), "millenaire:snail_soil", "progress=0", false, "", "", 0));
                else if (spawnerEntity(point.label()) != null) state = resolver.apply(new LegacyPalette.Point(color,
                        point.label(), "minecraft:spawner", "", false, "", "", 0));
                else if (point.label().equals("dispenserunknownpowder")) state = resolver.apply(new LegacyPalette.Point(color,
                        point.label(), "minecraft:dispenser", "facing=down", false, "", "", 0));
                else if (point.label().equals("freepaintedbrick")) state = resolver.apply(new LegacyPalette.Point(color,
                        point.label(), "millenaire:painted_brick_white", "", false, "", "", 0));
                else if (point.label().startsWith("free")) {
                    String block = switch (point.label()) {
                        case "freestone" -> "stone";
                        case "freecobblestone" -> "cobblestone";
                        case "freestonebrick" -> "stone_bricks";
                        case "freesand" -> "sand";
                        case "freesandstone" -> "sandstone";
                        case "freegravel" -> "gravel";
                        case "freewool" -> "white_wool";
                        case "freegrass_block" -> "grass_block";
                        default -> throw new IllegalArgumentException("Unsupported free material: " + point.label());
                    };
                    state = LegacyBlockStateResolver.resolve(new LegacyPalette.Point(color, point.label(), "minecraft:" + block, "", false, "", "", 0));
                }
                else if (SOURCE_BLOCKS.containsKey(point.label())) state = LegacyBlockStateResolver.resolve(new LegacyPalette.Point(color,
                        point.label(), "minecraft:" + SOURCE_BLOCKS.get(point.label()), "", false, "", "", 0));
                else if (TREE_SPAWNS.containsKey(point.label())) state = LegacyBlockStateResolver.resolve(new LegacyPalette.Point(color,
                        point.label(), "minecraft:" + TREE_SPAWNS.get(point.label()), "", false, "", "", 0));
                else if (bannerFacing(point.label()) != null) state = Blocks.WALL_BANNER.white().defaultBlockState()
                        .setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, bannerFacing(point.label()));
                else if (point.label().startsWith("cultureBannerStanding") || point.label().startsWith("villageBannerStanding"))
                    state = Blocks.BANNER.white().defaultBlockState();
                // Wall decorations were entities in the original; their positions stay indexed for the decoration layer.
                else if (DECORATION_MARKERS.stream().anyMatch(point.label()::startsWith)) state = Blocks.AIR.defaultBlockState();
                else throw new IllegalArgumentException("Unsupported special point: " + point.label());
                Rotation rotation = switch (orientation) {
                    case 1 -> Rotation.COUNTERCLOCKWISE_90;
                    case 2 -> Rotation.CLOCKWISE_180;
                    case 3 -> Rotation.CLOCKWISE_90;
                    default -> Rotation.NONE;
                };
                states.put(color, state.rotate(rotation));
            } catch (IllegalArgumentException exception) { rejected.add(color); issues.add(point.label() + ": " + exception.getMessage()); }
        }
        if (!issues.isEmpty()) return new Prepared(List.of(), new ArrayList<>(issues), Map.of());
        List<Change> changes = new ArrayList<>();
        List<Setup> setups = new ArrayList<>();
        Set<BlockPos> guessedTorches = new HashSet<>();
        Set<BlockPos> guessedChests = new HashSet<>(), guessedSigns = new HashSet<>(), guessedFurnaces = new HashSet<>();
        Set<BlockPos> guessedLadders = new HashSet<>();
        Set<BlockPos> furnacePoints = new LinkedHashSet<>();
        Map<BlockPos, String> treeMarkers = new LinkedHashMap<>();
        for (int y = 0; y < decoded.floors(); y++) for (int z = 0; z < plan.length(); z++) for (int x = 0; x < plan.width(); x++) {
            int color = decoded.colorAt(x, y, z);
            if (preserve.contains(color)) continue;
            var position = plan.worldPosition(origin.getX(), origin.getY(), origin.getZ(), x, y, z, orientation);
            var point = palette.points().get(color);
            BlockPos pos = new BlockPos(position.x(), position.y(), position.z());
            if (point.label().equals("torchGuess")) guessedTorches.add(pos);
            if (point.label().equals("ladderGuess")) guessedLadders.add(pos);
            if (point.label().equals("mainchestGuess") || point.label().equals("lockedchestGuess")) guessedChests.add(pos);
            if (point.label().equals("signwallGuess") || point.label().equals("plainSignGuess")) guessedSigns.add(pos);
            if (point.label().equals("furnaceGuess")) guessedFurnaces.add(pos);
            if (point.label().equals("furnaceGuess") || point.block().equals("minecraft:furnace")) furnacePoints.add(pos);
            String entityId = spawnerEntity(point.label());
            boolean dispenser = point.label().equals("dispenserunknownpowder");
            if (FruitTreeGenerator.supportsMarker(point.label())) treeMarkers.put(pos, point.label());
            boolean chest = chestMarker(point.label()) || Set.of("millenaire:locked_chest", "millenaire:mainchest").contains(point.block());
            boolean panel = point.label().equals("signwallGuess") || point.block().equals("millenaire:panel");
            if (chest || panel || entityId != null || dispenser) {
                List<String> names = plan.parameters().getOrDefault("native_name", List.of());
                String name = names.isEmpty() ? plan.key().replace('_', ' ') : names.getLast();
                var binding = new BuildingBinding(origin, plan.id(), name,
                        point.label().startsWith("mainchest") || point.block().equals("millenaire:mainchest"), true, Optional.empty());
                setups.add(new Setup(pos, entityId != null ? SetupKind.SPAWNER : dispenser ? SetupKind.DISPENSER : chest ? SetupKind.CHEST : SetupKind.PANEL,
                        entityId != null ? new BuildingBinding(origin, "spawner:" + entityId, entityId, false, false, Optional.empty())
                                : dispenser ? new BuildingBinding(origin, "dispenser:unknownpowder", "millenaire:unknownpowder", false, false, Optional.empty()) : binding));
            }
            changes.add(new Change(pos, states.get(color), point.secondPass() || panel || guessedSigns.contains(pos)
                    || guessedTorches.contains(pos) || guessedLadders.contains(pos) || states.get(color).getBlock() instanceof ButtonBlock || wallAttachment(states.get(color))));
        }
        Map<BlockPos, BlockState> planned = new HashMap<>();
        changes.forEach(change -> planned.put(change.pos(), change.state()));
        expandDoors(changes, planned, issues);
        expandBeds(changes, planned, issues);
        expandTallPlants(changes, planned, issues);
        expandPortals(changes, planned, issues);
        for (int i = 0; i < changes.size(); i++) {
            var change = changes.get(i);
            if (guessedChests.contains(change.pos())) {
                BlockState state = change.state().setValue(ChestBlock.FACING, guessChestFacing(planned, change.pos()));
                changes.set(i, new Change(change.pos(), state, change.secondPass())); planned.put(change.pos(), state);
            }
            if (guessedFurnaces.contains(change.pos())) {
                BlockState state = change.state().setValue(FurnaceBlock.FACING, guessChestFacing(planned, change.pos()));
                changes.set(i, new Change(change.pos(), state, true)); planned.put(change.pos(), state);
            }
            if (guessedSigns.contains(change.pos())) {
                Direction facing = guessWallFacing(planned, change.pos());
                if (facing == null) { issues.add("Wall sign has no planned support at " + change.pos()); continue; }
                BlockState state = change.state().setValue(WallSignBlock.FACING, facing);
                changes.set(i, new Change(change.pos(), state, true)); planned.put(change.pos(), state);
            }
            if (guessedLadders.contains(change.pos())) {
                // Forge autoGuessLaddersDoorsStairs resolves single-wall ladders.
                // Use wall-support priority for ambiguous modern attachments.
                Direction facing = guessWallFacing(planned, change.pos());
                if (facing == null) { issues.add("Ladder has no planned support at " + change.pos()); continue; }
                BlockState state = change.state().setValue(LadderBlock.FACING, facing);
                changes.set(i, new Change(change.pos(), state, true)); planned.put(change.pos(), state);
            }
        }
        connectChestPairs(changes, setups, planned, issues);
        // Panels hang on the first sturdy wall; a panel with no wall is left out, as the original sign would drop.
        Set<BlockPos> unsupportedPanels = new HashSet<>();
        for (var setup : setups) if (setup.kind() == SetupKind.PANEL) {
            BlockState panel = planned.get(setup.pos());
            Direction facing = panel.getValue(WallSignBlock.FACING);
            if (sturdy(planned, setup.pos().relative(facing.getOpposite()), facing)) continue;
            Direction supported = null;
            for (Direction direction : Direction.Plane.HORIZONTAL)
                if (sturdy(planned, setup.pos().relative(direction.getOpposite()), direction)) { supported = direction; break; }
            if (supported == null) { unsupportedPanels.add(setup.pos()); continue; }
            BlockState turned = panel.setValue(WallSignBlock.FACING, supported);
            planned.put(setup.pos(), turned);
            for (int i = 0; i < changes.size(); i++)
                if (changes.get(i).pos().equals(setup.pos())) changes.set(i, new Change(setup.pos(), turned, changes.get(i).secondPass()));
        }
        if (!unsupportedPanels.isEmpty()) {
            setups.removeIf(setup -> unsupportedPanels.contains(setup.pos()));
            for (int i = 0; i < changes.size(); i++)
                if (unsupportedPanels.contains(changes.get(i).pos())) changes.set(i, new Change(changes.get(i).pos(), Blocks.AIR.defaultBlockState(), false));
            unsupportedPanels.forEach(pos -> planned.put(pos, Blocks.AIR.defaultBlockState()));
        }
        for (int i = 0; i < changes.size(); i++) {
            var change = changes.get(i);
            if (!guessedTorches.contains(change.pos())) continue;
            BlockState below = planned.get(change.pos().below());
            // A torch on unplanned ground, a fence or a wall post stands as in the original.
            if (below == null || below.isFaceSturdy(EmptyBlockGetter.INSTANCE, change.pos().below(), Direction.UP, net.minecraft.world.level.block.SupportType.CENTER)) continue;
            BlockState wallTorch = null;
            for (Direction direction : List.of(Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH)) {
                BlockPos neighbor = change.pos().relative(direction);
                BlockState support = planned.get(neighbor);
                if (support != null && support.isFaceSturdy(EmptyBlockGetter.INSTANCE, neighbor, direction.getOpposite())) {
                    wallTorch = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, direction.getOpposite());
                    break;
                }
            }
            // Unsupported torches were dropped by the original second pass; skip them instead of rejecting the plan.
            if (wallTorch == null) changes.set(i, new Change(change.pos(), Blocks.AIR.defaultBlockState(), false));
            else changes.set(i, new Change(change.pos(), wallTorch, true));
        }
        Map<BlockPos, BlockState> treeBlocks = new LinkedHashMap<>();
        for (var marker : treeMarkers.entrySet()) {
            Map<BlockPos, BlockState> generated;
            try {
                generated = trees.plan(marker.getValue(), marker.getKey(), seed);
            } catch (RuntimeException exception) {
                issues.add("Tree marker " + marker.getValue() + " at " + marker.getKey() + ": " + exception.getMessage());
                continue;
            }
            if (!generated.containsKey(marker.getKey()) || generated.get(marker.getKey()).isAir()) {
                issues.add("Tree marker has no trunk at " + marker.getKey());
                continue;
            }
            // Building blocks and earlier trees win; the original generator never overwrote solid blocks.
            if (treeBlocks.containsKey(marker.getKey())) continue;
            for (var block : generated.entrySet()) {
                BlockState plannedState = planned.get(block.getKey());
                if (plannedState != null && !plannedState.isAir()) continue;
                treeBlocks.putIfAbsent(block.getKey(), block.getValue());
            }
        }
        if (!treeBlocks.isEmpty()) {
            changes.removeIf(change -> treeBlocks.containsKey(change.pos()));
            treeBlocks.forEach((pos, state) -> changes.add(new Change(pos, state, state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock)));
        }
        if (changes.size() > MAX_BLOCKS) issues.add("Plan exceeds " + MAX_BLOCKS + " block operations after expansion");
        if (!issues.isEmpty()) return new Prepared(List.of(), new ArrayList<>(issues), Map.of());
        // Clear first, place supporting blocks bottom-up, then fluids and attached blocks.
        var stock = StartingStock.prepare(plan.parameters().getOrDefault("startinggood", List.of()),
                setups.stream().filter(setup -> setup.kind() == SetupKind.CHEST).map(Setup::pos).toList(), goods,
                StartingStock.buildingSeed(seed, origin, plan.id(), orientation));
        if (!stock.supported()) return new Prepared(List.of(), stock.issues(), Map.of());
        changes.sort(Comparator.comparingInt((Change c) -> c.state().isAir() ? 0 : c.secondPass() ? 2 : 1)
                .thenComparingInt(c -> c.pos().getY()));
        Map<String, List<LegacyBuildingPlan.Position>> services = new LinkedHashMap<>(plan.servicePoints(decoded, palette, origin.getX(), origin.getY(), origin.getZ(), orientation));
        for (BlockPos pos : furnacePoints)
            addService(services, "furnaces", new LegacyBuildingPlan.Position(pos.getX(), pos.getY(), pos.getZ()));
        for (var setup : setups) {
            var position = new LegacyBuildingPlan.Position(setup.pos().getX(), setup.pos().getY(), setup.pos().getZ());
            addService(services, setup.kind() == SetupKind.CHEST ? "chests" : setup.kind() == SetupKind.PANEL ? "panels"
                    : setup.kind() == SetupKind.SPAWNER ? "spawners" : "dispensers", position);
            if (setup.kind() == SetupKind.CHEST && setup.binding().mainChest()) addService(services, "mainChests", position);
        }
        return new Prepared(changes, List.of(), services, setups, stock.inventories(), List.copyOf(treeMarkers.keySet()));
    }

    private static void addService(Map<String, List<LegacyBuildingPlan.Position>> services, String key, LegacyBuildingPlan.Position position) {
        List<LegacyBuildingPlan.Position> positions = new ArrayList<>(services.getOrDefault(key, List.of()));
        if (!positions.contains(position)) positions.add(position); services.put(key, List.copyOf(positions));
    }

    private static void expandDoors(List<Change> changes, Map<BlockPos, BlockState> planned, Set<String> issues) {
        Map<BlockPos, BlockState> expanded = new LinkedHashMap<>();
        Set<BlockPos> orphans = new HashSet<>();
        for (var change : changes) {
            BlockState lower = change.state();
            if (!(lower.getBlock() instanceof DoorBlock) || lower.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
            BlockState declaredUpper = planned.get(change.pos().above());
            boolean explicitUpper = declaredUpper != null && declaredUpper.is(lower.getBlock())
                    && declaredUpper.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER;
            if (explicitUpper) {
                // Legacy door metadata stores the hinge on the upper half and the facing on the lower half.
                lower = lower.setValue(DoorBlock.HINGE, declaredUpper.getValue(DoorBlock.HINGE));
            } else if (lower.is(Blocks.OAK_DOOR)) {
                // Original autoGuessLaddersDoorsStairs only adjusts oak door hinges.
                Direction facing = lower.getValue(DoorBlock.FACING);
                BlockPos left = change.pos().relative(facing.getCounterClockWise());
                BlockState neighbor = planned.get(left);
                if ((neighbor == null || neighbor.isAir() || neighbor.is(Blocks.OAK_DOOR))
                        && planned.containsKey(change.pos().relative(facing.getClockWise())))
                    lower = lower.setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT);
            }
            BlockPos upperPos = change.pos().above();
            BlockState upper = lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
            BlockState existing = planned.get(upperPos);
            if (existing != null && !existing.isAir() && !existing.equals(upper) && !explicitUpper) {
                issues.add("Door upper half overlaps planned block at " + upperPos);
                continue;
            }
            expanded.put(change.pos(), lower);
            expanded.put(upperPos, upper);
        }
        for (var change : changes) if (change.state().getBlock() instanceof DoorBlock
                && change.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                && !(expanded.get(change.pos()) instanceof BlockState generated && generated.is(change.state().getBlock())))
            // An upper half without its lower half cannot survive; the original lost it on the first block update.
            orphans.add(change.pos());
        changes.removeIf(change -> expanded.containsKey(change.pos()) || orphans.contains(change.pos()));
        orphans.forEach(pos -> { if (!expanded.containsKey(pos)) planned.remove(pos); });
        expanded.forEach((pos, state) -> { changes.add(new Change(pos, state, true)); planned.put(pos, state); });
    }

    /** Resource blocks that mining goals harvest, as in the original quarry and mine plans. */
    static final Map<String, String> SOURCE_BLOCKS = Map.of("stonesource", "stone", "sandsource", "sand", "sandstonesource", "sandstone",
            "gravelsource", "gravel", "claysource", "clay", "dioritesource", "diorite", "redsandstonesource", "red_sandstone",
            "snowsource", "snow_block", "icesource", "ice");
    /** Grove markers become saplings that grow into the planned species. */
    static final Map<String, String> TREE_SPAWNS = Map.of("oakspawn", "oak_sapling", "pinespawn", "spruce_sapling",
            "birchspawn", "birch_sapling", "acaciaspawn", "acacia_sapling", "darkoakspawn", "dark_oak_sapling", "junglespawn", "jungle_sapling");
    static final List<String> DECORATION_MARKERS = List.of("byzantineicon", "wallcarpet", "tapestry", "indianstatue", "mayanstatue", "hidehanging");

    static Direction bannerFacing(String label) {
        if (!label.startsWith("cultureBannerWall") && !label.startsWith("villageBannerWall")) return null;
        String side = label.substring(label.indexOf("Wall") + 4).toLowerCase(Locale.ROOT);
        return switch (side) {
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            default -> null;
        };
    }

    private static boolean chestMarker(String label) {
        return label.startsWith("mainchest") || label.startsWith("lockedchest");
    }

    private static void expandPortals(List<Change> changes, Map<BlockPos, BlockState> planned, Set<String> issues) {
        Map<BlockPos, BlockState> expanded = new LinkedHashMap<>();
        for (var change : changes) if (change.state().is(Blocks.NETHER_PORTAL) && !expanded.containsKey(change.pos())) {
            try {
                var portal = PlannedPortal.expand(planned, change.pos(), change.state().getValue(NetherPortalBlock.AXIS));
                for (var entry : portal.entrySet()) {
                    BlockState previous = expanded.putIfAbsent(entry.getKey(), entry.getValue());
                    if (previous != null && !previous.equals(entry.getValue())) issues.add("Portal frames overlap at " + entry.getKey());
                }
            } catch (IllegalArgumentException failure) { issues.add(failure.getMessage()); }
        }
        changes.removeIf(change -> expanded.containsKey(change.pos()));
        expanded.forEach((pos, state) -> { changes.add(new Change(pos, state, true)); planned.put(pos, state); });
    }

    private static boolean legacyTallPlant(BlockState state) {
        return state.getBlock() instanceof DoublePlantBlock && Set.of("sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony")
                .contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
    }

    private static void expandTallPlants(List<Change> changes, Map<BlockPos, BlockState> planned, Set<String> issues) {
        Map<BlockPos, BlockState> expanded = new LinkedHashMap<>();
        for (var change : changes) {
            BlockState lower = change.state();
            if (!legacyTallPlant(lower) || lower.getValue(DoublePlantBlock.HALF) != DoubleBlockHalf.LOWER) continue;
            BlockPos upperPos = change.pos().above();
            BlockState upper = lower.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
            BlockState existing = planned.get(upperPos);
            if (existing != null && !existing.isAir() && !existing.equals(upper)) {
                issues.add("Tall plant upper half overlaps planned block at " + upperPos);
                continue;
            }
            expanded.put(change.pos(), lower); expanded.put(upperPos, upper);
        }
        for (var change : changes) if (legacyTallPlant(change.state())
                && change.state().getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER
                && !change.state().equals(expanded.get(change.pos())))
            issues.add("Tall plant upper half has no matching lower half at " + change.pos());
        changes.removeIf(change -> expanded.containsKey(change.pos()));
        expanded.forEach((pos, state) -> { changes.add(new Change(pos, state, true)); planned.put(pos, state); });
    }

    private static void expandBeds(List<Change> changes, Map<BlockPos, BlockState> planned, Set<String> issues) {
        Map<BlockPos, BlockState> expanded = new LinkedHashMap<>();
        Set<BlockPos> dropped = new HashSet<>();
        for (var change : changes) {
            BlockState head = change.state();
            if (!(head.getBlock() instanceof BedBlock) || head.getValue(BedBlock.PART) != BedPart.HEAD) continue;
            BlockPos footPos = change.pos().relative(head.getValue(BedBlock.FACING).getOpposite());
            BlockState foot = head.setValue(BedBlock.PART, BedPart.FOOT);
            BlockState existing = planned.get(footPos);
            if (existing != null && !existing.isAir() && !existing.equals(foot)) {
                // A few legacy plans put a solid block where the foot would go: leave that bed out.
                dropped.add(change.pos());
                continue;
            }
            expanded.put(change.pos(), head);
            if (expanded.putIfAbsent(footPos, foot) != null && !expanded.get(footPos).equals(foot))
                issues.add("Bed counterparts overlap at " + footPos);
        }
        for (var change : changes) if (change.state().getBlock() instanceof BedBlock
                && change.state().getValue(BedBlock.PART) == BedPart.FOOT
                && !change.state().equals(expanded.get(change.pos())))
            dropped.add(change.pos()); // an orphan or mismatched foot is left out with its bed
        changes.removeIf(change -> expanded.containsKey(change.pos()) || dropped.contains(change.pos()));
        dropped.forEach(planned::remove);
        expanded.forEach((pos, state) -> { changes.add(new Change(pos, state, true)); planned.put(pos, state); });
    }

    private static String spawnerEntity(String label) {
        return switch (label) {
            case "spawnerskeleton" -> "minecraft:skeleton";
            case "spawnerzombie" -> "minecraft:zombie";
            case "spawnerspider" -> "minecraft:spider";
            case "spawnercavespider" -> "minecraft:cave_spider";
            case "spawnercreeper" -> "minecraft:creeper";
            case "spawnerblaze" -> "minecraft:blaze";
            default -> null;
        };
    }
    private static Direction chestFacing(String label) {
        String suffix = label.startsWith("mainchest") ? label.substring(9) : label.substring(11);
        return switch (suffix) {
            case "Top" -> Direction.WEST; case "Bottom" -> Direction.EAST;
            case "Left" -> Direction.SOUTH; case "Right" -> Direction.NORTH; case "Guess" -> Direction.SOUTH;
            default -> throw new IllegalArgumentException("Unsupported chest marker: " + label);
        };
    }
    private static boolean sturdy(Map<BlockPos, BlockState> states, BlockPos pos, Direction face) {
        BlockState state = states.get(pos);
        return state != null && state.isFaceSturdy(EmptyBlockGetter.INSTANCE, pos, face);
    }
    private static Direction guessWallFacing(Map<BlockPos, BlockState> states, BlockPos pos) {
        // Legacy guessWallOrientation priority: east, west, south, north supports.
        for (Direction support : List.of(Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH))
            if (sturdy(states, pos.relative(support), support.getOpposite())) return support.getOpposite();
        return null;
    }
    private static Direction guessChestFacing(Map<BlockPos, BlockState> states, BlockPos pos) {
        for (Direction wall : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST))
            if (sturdy(states, pos.relative(wall), wall.getOpposite())
                    && !states.get(pos.relative(wall)).is(Blocks.FURNACE)
                    && !(states.get(pos.relative(wall)).getBlock() instanceof ChestBlock)
                    && !sturdy(states, pos.relative(wall.getOpposite()), wall)) return wall.getOpposite();
        for (Direction facing : List.of(Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST))
            if (!sturdy(states, pos.relative(facing), facing.getOpposite())) return facing;
        return Direction.NORTH;
    }
    private static void connectChestPairs(List<Change> changes, List<Setup> setups, Map<BlockPos, BlockState> states, Set<String> issues) {
        Set<BlockPos> chests = new HashSet<>();
        setups.stream().filter(setup -> setup.kind() == SetupKind.CHEST).forEach(setup -> chests.add(setup.pos()));
        for (int i = 0; i < changes.size(); i++) {
            var change = changes.get(i); if (!chests.contains(change.pos())) continue;
            Direction facing = change.state().getValue(ChestBlock.FACING);
            Direction leftSide = facing.getClockWise(), rightSide = facing.getCounterClockWise();
            boolean left = compatibleChest(states, chests, change.pos().relative(leftSide), change.state(), facing);
            boolean right = compatibleChest(states, chests, change.pos().relative(rightSide), change.state(), facing);
            if (left && right) { issues.add("More than two aligned village chests at " + change.pos()); continue; }
            ChestType type = left ? ChestType.LEFT : right ? ChestType.RIGHT : ChestType.SINGLE;
            BlockState state = change.state().setValue(ChestBlock.TYPE, type);
            changes.set(i, new Change(change.pos(), state, change.secondPass())); states.put(change.pos(), state);
        }
    }
    private static boolean compatibleChest(Map<BlockPos, BlockState> states, Set<BlockPos> chests, BlockPos pos, BlockState original, Direction facing) {
        BlockState neighbor = states.get(pos);
        return chests.contains(pos) && neighbor != null && neighbor.getBlock() == original.getBlock() && neighbor.getValue(ChestBlock.FACING) == facing;
    }

    private static boolean wallAttachment(BlockState state) {
        return state.is(Blocks.LADDER) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.REDSTONE_WALL_TORCH);
    }
    /**
     * An upgrade is built over its own level: blocks that already stand are skipped, and existing chests and
     * other block entities of the same block keep their contents instead of blocking the upgrade.
     */
    public static Prepared forUpgrade(Prepared prepared, WorldAccess world) {
        Set<BlockPos> kept = new HashSet<>();
        List<Change> changes = new ArrayList<>();
        for (var change : prepared.changes()) {
            BlockState existing = world.get(change.pos());
            if (existing.equals(change.state())) { kept.add(change.pos()); continue; }
            if (existing.hasBlockEntity() && existing.getBlock() == change.state().getBlock()) { kept.add(change.pos()); continue; }
            changes.add(change);
        }
        return new Prepared(changes, prepared.issues(), prepared.servicePoints(),
                prepared.setups().stream().filter(setup -> !kept.contains(setup.pos())).toList(),
                prepared.startingStock().stream().filter(stock -> !kept.contains(stock.pos())).toList(), prepared.treeRoots());
    }

    public static List<String> checkDestinations(Prepared prepared, WorldAccess world, boolean replace) {
        if (!prepared.supported()) return prepared.issues();
        Set<String> errors = new LinkedHashSet<>();
        for (var change : prepared.changes()) {
            String error = world.rejection(change.pos(), replace);
            if (error != null) errors.add(error);
        }
        Map<BlockPos, BlockState> planned = new HashMap<>();
        prepared.changes().forEach(change -> planned.put(change.pos(), change.state()));
        for (var change : prepared.changes()) if (wallAttachment(change.state())) {
            Direction outward = change.state().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
            BlockPos supportPos = change.pos().relative(outward.getOpposite());
            BlockState support = planned.getOrDefault(supportPos, world.get(supportPos));
            if (!support.isFaceSturdy(EmptyBlockGetter.INSTANCE, supportPos, outward))
                errors.add("Wall attachment needs a sturdy support face at " + change.pos());
        }
        for (var change : prepared.changes()) if (change.state().getBlock() instanceof ButtonBlock) {
            Direction outward = switch (change.state().getValue(ButtonBlock.FACE)) {
                case FLOOR -> Direction.UP;
                case CEILING -> Direction.DOWN;
                case WALL -> change.state().getValue(ButtonBlock.FACING);
            };
            BlockPos supportPos = change.pos().relative(outward.getOpposite());
            BlockState support = planned.getOrDefault(supportPos, world.get(supportPos));
            if (!support.isFaceSturdy(EmptyBlockGetter.INSTANCE, supportPos, outward))
                errors.add("Button needs a sturdy support face at " + change.pos());
        }
        for (var change : prepared.changes()) if (legacyTallPlant(change.state())
                && change.state().getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER) {
            BlockPos below = change.pos().below();
            BlockState ground = planned.getOrDefault(below, world.get(below));
            if (!ground.is(BlockTags.DIRT) && !ground.is(Blocks.DIRT) && !ground.is(Blocks.GRASS_BLOCK) && !ground.is(Blocks.FARMLAND))
                errors.add("Tall plant needs dirt or farmland below at " + change.pos());
        }
        for (var change : prepared.changes()) if (change.state().getBlock() instanceof DoorBlock
                && change.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
            BlockPos below = change.pos().below();
            BlockState support = planned.getOrDefault(below, world.get(below));
            if (!support.isFaceSturdy(EmptyBlockGetter.INSTANCE, below, Direction.UP))
                errors.add("Door needs solid support below at " + change.pos());
        }
        for (BlockPos root : prepared.treeRoots()) {
            BlockPos below = root.below();
            BlockState ground = planned.getOrDefault(below, world.get(below));
            if (!ground.is(BlockTags.DIRT) && !ground.is(Blocks.DIRT) && !ground.is(Blocks.GRASS_BLOCK)
                    && !ground.is(Blocks.PODZOL) && !ground.is(Blocks.COARSE_DIRT)
                    && !ground.is(Blocks.ROOTED_DIRT) && !ground.is(Blocks.FARMLAND))
                errors.add("Tree marker needs dirt or grass below at " + root);
        }
        for (var setup : prepared.setups()) {
            String error = world.setupRejection(setup); if (error != null) errors.add(error);
        }
        for (var stock : prepared.startingStock()) {
            String error = world.stockRejection(stock); if (error != null) errors.add(error);
        }
        return List.copyOf(errors);
    }

    public static int place(Prepared prepared, WorldAccess world, boolean replace) {
        List<String> issues = checkDestinations(prepared, world, replace);
        if (!issues.isEmpty()) throw new IllegalArgumentException(String.join("; ", issues));
        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        try {
            for (var change : prepared.changes()) {
                BlockState before = world.get(change.pos());
                if (before.equals(change.state())) continue;
                previous.put(change.pos(), before);
                if (!world.set(change.pos(), change.state())) throw new IllegalStateException("Block write failed at " + change.pos());
            }
            for (var setup : prepared.setups()) world.initialize(setup);
            for (var stock : prepared.startingStock()) world.fill(stock);
            world.finish(prepared.changes());
            return previous.size();
        } catch (RuntimeException failure) {
            // Removing a filled chest can spawn its contents. Empty newly created stocks first.
            try { world.discardStartingStock(prepared.startingStock()); }
            catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            try { world.discardSetupContents(prepared.setups()); }
            catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            List<BlockPos> written = new ArrayList<>(previous.keySet());
            Collections.reverse(written);
            for (BlockPos pos : written) {
                try { if (!world.set(pos, previous.get(pos))) failure.addSuppressed(new IllegalStateException("Rollback failed at " + pos)); }
                catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        }
    }

    public static WorldAccess world(ServerLevel level) {
        return new WorldAccess() {
            public BlockState get(BlockPos pos) { return level.getBlockState(pos); }
            public String rejection(BlockPos pos, boolean replace) {
                if (pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY()) return "Outside world height at " + pos;
                if (!level.getWorldBorder().isWithinBounds(pos)) return "Outside world border at " + pos;
                if (!level.hasChunkAt(pos)) return "Unloaded chunk at " + pos;
                if (level.getBlockEntity(pos) != null) return "Existing block entity at " + pos;
                BlockState current = get(pos);
                if (current.is(Blocks.BEDROCK)) return "Bedrock at " + pos;
                if (!replace && !current.isAir() && !current.canBeReplaced()) return "Occupied block at " + pos + "; use explicit replace command";
                return null;
            }
            public boolean set(BlockPos pos, BlockState state) {
                if (get(pos).equals(state)) return true;
                return level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            public String setupRejection(Setup setup) {
                if (setup.kind() == SetupKind.DISPENSER && !LegacyContentRegistry.items().containsKey("unknownpowder"))
                    return "Unknown item millenaire:unknownpowder";
                return null;
            }
            public void initialize(Setup setup) {
                var entity = level.getBlockEntity(setup.pos());
                if (setup.kind() == SetupKind.CHEST && entity instanceof VillageChestBlockEntity chest) chest.bind(setup.binding());
                else if (setup.kind() == SetupKind.PANEL && entity instanceof VillagePanelBlockEntity panel) panel.bind(setup.binding());
                else if (setup.kind() == SetupKind.SPAWNER && entity instanceof SpawnerBlockEntity spawner) {
                    Identifier id = Identifier.parse(setup.binding().name());
                    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
                    if (type == null) throw new IllegalStateException("Unknown spawner entity " + id);
                    spawner.setEntityId(type, RandomSource.create());
                }
                else if (setup.kind() == SetupKind.DISPENSER && entity instanceof DispenserBlockEntity dispenser) {
                    var item = LegacyContentRegistry.items().get("unknownpowder");
                    if (item == null) throw new IllegalStateException("Unknown item millenaire:unknownpowder");
                    dispenser.setItem(0, new ItemStack(item, 2));
                    dispenser.setChanged();
                }
                else throw new IllegalStateException("Missing " + setup.kind() + " block entity at " + setup.pos());
                var state = get(setup.pos()); level.sendBlockUpdated(setup.pos(), state, state, Block.UPDATE_CLIENTS);
            }
            public String stockRejection(StartingStock.Inventory stock) { return null; }
            public void fill(StartingStock.Inventory stock) {
                if (!(level.getBlockEntity(stock.pos()) instanceof VillageChestBlockEntity chest))
                    throw new IllegalStateException("Missing stock chest at " + stock.pos());
                chest.fillStartingStock(stock);
            }
            public void discardStartingStock(List<StartingStock.Inventory> stocks) {
                for (var stock : stocks) if (level.getBlockEntity(stock.pos()) instanceof VillageChestBlockEntity chest)
                    chest.clearContent();
            }
            public void discardSetupContents(List<Setup> setups) {
                for (var setup : setups) if (setup.kind() == SetupKind.DISPENSER
                        && level.getBlockEntity(setup.pos()) instanceof DispenserBlockEntity dispenser)
                    dispenser.clearContent();
            }
            public void finish(List<Change> changes) {
                for (var change : changes) {
                    level.getBlockState(change.pos()).updateNeighbourShapes(level, change.pos(), Block.UPDATE_ALL);
                    level.updateNeighborsAt(change.pos(), level.getBlockState(change.pos()).getBlock());
                }
            }
        };
    }
}
