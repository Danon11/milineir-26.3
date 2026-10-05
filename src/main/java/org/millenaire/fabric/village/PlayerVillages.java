package org.millenaire.fabric.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.millenaire.fabric.FabricBuildingState;
import org.millenaire.fabric.FabricSettlementLifecycleState;
import org.millenaire.fabric.FabricSettlementState;
import org.millenaire.fabric.FabricVillageOwnership;
import org.millenaire.fabric.MillenaireCommands;
import org.millenaire.fabric.content.CustomBuildings;
import org.millenaire.fabric.content.LegacyBuildingPlan.Position;
import org.millenaire.fabric.content.VillageLayout;
import org.millenaire.fabric.content.VillageTypeDefinition;
import org.millenaire.fabric.villager.VillagerSpawning;

import java.util.*;

/**
 * Villages founded and run by a player, as with the original summoning wand: a gold block becomes the centre of
 * a {@code playerControlled} village type; types with a {@code customcentre} take the player's own building
 * around the gold block as their town hall. Signs inside the village register further player-built buildings,
 * and the owner orders which new buildings the villagers construct.
 */
public final class PlayerVillages {
    /** How close the player must stay to the block they used the wand on. */
    public static final double REACH = 8.0;

    public record Outcome(boolean success, String message) {
        static Outcome ok(String message) { return new Outcome(true, message); }
        static Outcome fail(String message) { return new Outcome(false, message); }
    }

    private PlayerVillages() {}

    public static boolean playerControlled(VillageTypeDefinition type) {
        return type.source().first("playercontrolled", "false").trim().equalsIgnoreCase("true");
    }

    /** Village types a player can found, as {@code culture:type}. */
    public static List<VillageTypeDefinition> foundable() {
        List<VillageTypeDefinition> result = new ArrayList<>();
        var catalog = MillenaireCommands.contentCatalog();
        if (catalog == null) return result;
        catalog.cultures().values().forEach(culture -> culture.villageTypes().values().stream()
                .filter(PlayerVillages::playerControlled).forEach(result::add));
        return result;
    }

    public static Optional<VillageTypeDefinition> type(String id) {
        String[] parts = id.split(":", 2);
        var catalog = MillenaireCommands.contentCatalog();
        if (parts.length != 2 || catalog == null || !catalog.cultures().containsKey(parts[0])) return Optional.empty();
        return Optional.ofNullable(catalog.cultures().get(parts[0]).villageTypes().get(parts[1]));
    }

    public static Map<String, CustomBuildings.Definition> customBuildings() {
        var catalog = MillenaireCommands.contentCatalog();
        return catalog == null ? Map.of() : CustomBuildings.all(catalog, new ArrayList<>());
    }

    public static Optional<FabricSettlementState.Settlement> settlementAt(ServerLevel level, BlockPos pos) {
        return FabricSettlementState.get(level.getServer()).containing(level.dimension().identifier(), pos.getX(), pos.getZ());
    }

    private static Optional<String> reachIssue(ServerPlayer player, BlockPos pos) {
        if (player.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > REACH) return Optional.of("You are too far from that block.");
        return Optional.empty();
    }

    /** Founds a village of {@code typeId} on the gold block at {@code pos}, owned by the player. */
    public static Outcome found(ServerPlayer player, String typeId, BlockPos pos) {
        ServerLevel level = player.level();
        var type = type(typeId).filter(PlayerVillages::playerControlled);
        if (type.isEmpty()) return Outcome.fail("Unknown player village type: " + typeId);
        var reach = reachIssue(player, pos);
        if (reach.isPresent()) return Outcome.fail(reach.get());
        if (!level.getBlockState(pos).is(Blocks.GOLD_BLOCK)) return Outcome.fail("A village is founded on a block of gold.");
        if (settlementAt(level, pos).isPresent()) return Outcome.fail("This place already belongs to a village.");
        String custom = type.get().source().first("customcentre", "").trim().toLowerCase(Locale.ROOT);
        return custom.isEmpty() ? foundBuilt(player, type.get(), pos) : foundCustom(player, type.get(), custom, pos);
    }

    /** A village type with a plan centre: the starting layout is built around the gold block. */
    private static Outcome foundBuilt(ServerPlayer player, VillageTypeDefinition type, BlockPos pos) {
        ServerLevel level = player.level();
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        try {
            var result = VillageFounder.found(level, MillenaireCommands.contentCatalog(), type,
                    new Position(pos.getX(), ground, pos.getZ()), level.getRandom().nextLong(), true, true);
            if (!result.placed()) {
                level.setBlockAndUpdate(pos, Blocks.GOLD_BLOCK.defaultBlockState());
                return Outcome.fail("The village does not fit here: " + String.join("; ", result.issues().stream().limit(3).toList()));
            }
            var settlement = settlementAt(level, pos).orElseThrow();
            FabricVillageOwnership.get(level.getServer()).claim(VillageGrowth.key(settlement), player.getUUID(), player.getName().getString());
            return Outcome.ok("You founded " + type.name() + " with " + result.residents() + " villagers.");
        } catch (java.io.IOException | RuntimeException exception) {
            level.setBlockAndUpdate(pos, Blocks.GOLD_BLOCK.defaultBlockState());
            return Outcome.fail("The village could not be founded: " + exception.getMessage());
        }
    }

    /** A village whose town hall is the player's own building around the gold block. */
    private static Outcome foundCustom(ServerPlayer player, VillageTypeDefinition type, String centreKey, BlockPos pos) {
        ServerLevel level = player.level();
        var definition = customBuildings().get(type.culture() + ":" + centreKey);
        if (definition == null) return Outcome.fail("Unknown custom centre " + centreKey + " for " + type.culture());
        var scan = CustomBuildingScanner.scan(level, pos, definition);
        if (!scan.complete()) return Outcome.fail(definition.nativeName() + " still needs: " + String.join(", ", scan.missing()));
        var building = building(level, definition, pos, scan, true);
        int radius = Math.max(64, Integer.parseInt(type.source().first("radius", "64").trim()));
        var settlement = new FabricSettlementState.Settlement(level.dimension().identifier(), type.culture() + ":" + type.id(), type.name(),
                new Position(pos.getX(), pos.getY(), pos.getZ()), level.getRandom().nextLong(), Math.min(radius, VillageLayout.MAX_RADIUS), List.of(building));
        try {
            FabricSettlementState.get(level.getServer()).record(settlement);
        } catch (IllegalArgumentException exception) {
            return Outcome.fail("Too close to another village: " + exception.getMessage());
        }
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        FabricBuildingState.get(level.getServer()).record(building.placement());
        FabricSettlementLifecycleState.get(level.getServer()).ensure(settlement);
        FabricVillageOwnership.get(level.getServer()).claim(VillageGrowth.key(settlement), player.getUUID(), player.getName().getString());
        int residents = spawn(level, definition, building.placement());
        return Outcome.ok("You founded " + type.name() + " around your " + definition.nativeName() + " with " + residents + " villagers.");
    }

    /** Registers the player-built building around the sign at {@code pos} in the player's village. */
    public static Outcome registerCustom(ServerPlayer player, String definitionId, BlockPos pos) {
        ServerLevel level = player.level();
        var reach = reachIssue(player, pos);
        if (reach.isPresent()) return Outcome.fail(reach.get());
        if (!(level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.SignBlock))
            return Outcome.fail("Custom buildings are registered on a sign.");
        var settlement = settlementAt(level, pos);
        if (settlement.isEmpty()) return Outcome.fail("This sign is not inside a village.");
        var ownership = FabricVillageOwnership.get(level.getServer());
        if (!ownership.owns(VillageGrowth.key(settlement.get()), player.getUUID())) return Outcome.fail("This is not your village.");
        var definition = customBuildings().get(definitionId);
        if (definition == null || !allowedCustom(settlement.get()).contains(definition.id())) return Outcome.fail("That building cannot be built in this village.");
        var origin = new Position(pos.getX(), pos.getY(), pos.getZ());
        if (settlement.get().buildings().stream().anyMatch(b -> b.placement().origin().equals(origin)))
            return Outcome.fail("This sign already marks a building.");
        var scan = CustomBuildingScanner.scan(level, pos, definition);
        if (!scan.complete()) return Outcome.fail(definition.nativeName() + " still needs: " + String.join(", ", scan.missing()));
        var building = building(level, definition, pos, scan, false);
        FabricBuildingState.get(level.getServer()).record(building.placement());
        FabricSettlementState.get(level.getServer()).upsertBuilding(settlement.get(), building, false);
        int residents = spawn(level, definition, building.placement());
        return Outcome.ok("Registered " + definition.nativeName() + (residents > 0 ? "; " + residents + " villagers move in." : "."));
    }

    /** Custom buildings the village type lists ({@code customBuilding=}), as {@code culture:key}. */
    public static List<String> allowedCustom(FabricSettlementState.Settlement settlement) {
        return type(settlement.type()).map(type -> type.source().values("custombuilding").stream()
                .map(key -> type.culture() + ":" + key.trim().toLowerCase(Locale.ROOT)).toList()).orElse(List.of());
    }

    /** New buildings the owner can order: the type's core and secondary lists, without duplicates. */
    public static List<String> orderable(FabricSettlementState.Settlement settlement) {
        return type(settlement.type()).map(type -> {
            LinkedHashSet<String> keys = new LinkedHashSet<>();
            type.coreBuildings().forEach(key -> keys.add(key.trim().toLowerCase(Locale.ROOT)));
            type.secondaryBuildings().forEach(key -> keys.add(key.trim().toLowerCase(Locale.ROOT)));
            var catalog = MillenaireCommands.contentCatalog();
            return keys.stream().filter(key -> catalog.plans().containsKey(type.culture() + ":" + key + "_A0")).toList();
        }).orElse(List.of());
    }

    public static Outcome order(ServerPlayer player, String building) {
        ServerLevel level = player.level();
        var settlement = settlementAt(level, player.blockPosition());
        if (settlement.isEmpty()) return Outcome.fail("You are not in a village.");
        var ownership = FabricVillageOwnership.get(level.getServer());
        String key = VillageGrowth.key(settlement.get());
        if (!ownership.owns(key, player.getUUID())) return Outcome.fail("This is not your village.");
        String plan = building.trim().toLowerCase(Locale.ROOT);
        if (!orderable(settlement.get()).contains(plan)) return Outcome.fail("This village cannot build " + building + ".");
        if (!ownership.order(key, plan)) return Outcome.fail(building + " is already ordered.");
        return Outcome.ok("Ordered " + plan + ". Villagers build it once the town hall holds the materials.");
    }

    private static FabricSettlementState.Building building(ServerLevel level, CustomBuildings.Definition definition, BlockPos pos,
                                                           CustomBuildingScanner.Scan scan, boolean centre) {
        var placed = new FabricBuildingState.PlacedBuilding(level.dimension().identifier(), definition.planId(),
                new Position(pos.getX(), pos.getY(), pos.getZ()), 0, scan.points());
        int r = definition.radius();
        return new FabricSettlementState.Building(placed, new VillageLayout.Bounds(pos.getX() - r, pos.getZ() - r, pos.getX() + r, pos.getZ() + r), centre);
    }

    private static int spawn(ServerLevel level, CustomBuildings.Definition definition, FabricBuildingState.PlacedBuilding placed) {
        var villagers = org.millenaire.fabric.FabricVillagerState.get(level.getServer());
        int before = villagers.villagers().size();
        VillagerSpawning.spawnResidents(level, definition.plan(), placed, new SplittableRandom(level.getRandom().nextLong()));
        return villagers.villagers().size() - before;
    }
}
