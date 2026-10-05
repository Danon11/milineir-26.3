package org.millenaire.fabric.village;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.millenaire.fabric.FabricSettlementState;
import org.millenaire.fabric.FabricVillageOwnership;
import org.millenaire.fabric.FabricVillageRelations;
import org.millenaire.fabric.villager.MillVillagerEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Raids between villages. Village types with {@code carriesraid=true} send their {@code raider} villagers against
 * a neighbour they are hostile to (or that their owner names): the raiders march to its town hall, fight its
 * defenders, loot its chests if they hold the place for half a minute, and carry the loot home. Raids worsen
 * relations; raids are kept in memory, so a restart calls the raiders home.
 */
public final class VillageRaids {
    public static final int NEIGHBOURHOOD = 1000, MAX_RAIDERS = 8;
    static final int CHECK_INTERVAL = 6000, LOOT_TICKS = 600, RAID_TIMEOUT = 12000, ARRIVAL = 6, LOOT_STACKS = 4;
    static final int RAID_PENALTY = 30, START_THRESHOLD = -40;

    enum Phase { MARCHING, LOOTING, RETURNING }

    static final class Raid {
        final String attacker, target;
        final BlockPos home, destination;
        final Set<UUID> raiders;
        final long started;
        Phase phase = Phase.MARCHING;
        long phaseStarted;
        final List<ItemStack> loot = new ArrayList<>();
        Raid(String attacker, String target, BlockPos home, BlockPos destination, Set<UUID> raiders, long started) {
            this.attacker = attacker; this.target = target; this.home = home; this.destination = destination;
            this.raiders = raiders; this.started = started; this.phaseStarted = started;
        }
    }

    private static final Map<String, Raid> ACTIVE = new ConcurrentHashMap<>();

    private VillageRaids() {}

    public static boolean carriesRaids(FabricSettlementState.Settlement settlement) {
        return PlayerVillages.type(settlement.type()).map(type -> type.source().first("carriesraid", "false").trim().equalsIgnoreCase("true")).orElse(false);
    }

    /** Whether a villager of {@code villageKey} is under attack by the raid of {@code raider}. */
    public static Optional<String> raidTarget(MillVillagerEntity raider) {
        for (Raid raid : ACTIVE.values())
            if (raid.raiders.contains(raider.getUUID()) && raid.phase != Phase.RETURNING) return Optional.of(raid.target);
        return Optional.empty();
    }

    public static Optional<BlockPos> destination(MillVillagerEntity raider) {
        for (Raid raid : ACTIVE.values())
            if (raid.raiders.contains(raider.getUUID())) return Optional.of(raid.phase == Phase.RETURNING ? raid.home : raid.destination);
        return Optional.empty();
    }

    public static boolean raiding(MillVillagerEntity villager) {
        return ACTIVE.values().stream().anyMatch(raid -> raid.raiders.contains(villager.getUUID()));
    }

    static BlockPos townHall(FabricSettlementState.Settlement settlement) {
        var centre = settlement.buildings().stream().filter(FabricSettlementState.Building::centre).findFirst()
                .orElse(settlement.buildings().getFirst()).placement();
        var points = centre.servicePoints().getOrDefault("chests", List.of());
        var at = points.isEmpty() ? centre.origin() : points.getFirst();
        return new BlockPos(at.x(), at.y(), at.z());
    }

    static double distance(FabricSettlementState.Settlement a, FabricSettlementState.Settlement b) {
        double dx = a.origin().x() - b.origin().x(), dz = a.origin().z() - b.origin().z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Villages within reach of a settlement, nearest first. */
    public static List<FabricSettlementState.Settlement> neighbours(MinecraftServer server, FabricSettlementState.Settlement settlement) {
        return FabricSettlementState.get(server).settlements().stream()
                .filter(other -> other != settlement && other.dimension().equals(settlement.dimension()) && distance(other, settlement) <= NEIGHBOURHOOD)
                .sorted(Comparator.comparingDouble(other -> distance(other, settlement))).toList();
    }

    /** First meeting: villages of one culture start friendly, others anywhere from wary to friendly. */
    static void meet(FabricVillageRelations relations, FabricSettlementState.Settlement a, FabricSettlementState.Settlement b) {
        String ka = VillageGrowth.key(a), kb = VillageGrowth.key(b);
        if (relations.known(ka, kb)) return;
        Random random = new Random(ka.hashCode() * 31L + kb.hashCode());
        boolean sameCulture = a.type().split(":")[0].equals(b.type().split(":")[0]);
        relations.set(ka, kb, sameCulture ? 20 + random.nextInt(31) : -50 + random.nextInt(81));
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now % 20 == 0) for (Raid raid : List.copyOf(ACTIVE.values())) update(server, raid, now);
        if (now % CHECK_INTERVAL != 4500) return;
        var relations = FabricVillageRelations.get(server);
        var ownership = FabricVillageOwnership.get(server);
        Random random = new Random(now);
        for (var settlement : FabricSettlementState.get(server).settlements()) {
            var level = level(server, settlement);
            if (level == null || !level.isLoaded(townHall(settlement))) continue;
            String key = VillageGrowth.key(settlement);
            for (var other : neighbours(server, settlement)) meet(relations, settlement, other);
            // Natural villages decide raids themselves; a player's village raids only when its owner says so.
            if (!carriesRaids(settlement) || ownership.owner(key).isPresent() || ACTIVE.containsKey(key) || random.nextInt(4) != 0) continue;
            neighbours(server, settlement).stream().filter(other -> relations.get(key, VillageGrowth.key(other)) <= START_THRESHOLD)
                    .min(Comparator.comparingInt(other -> relations.get(key, VillageGrowth.key(other))))
                    .ifPresent(target -> start(level, settlement, target));
        }
    }

    private static ServerLevel level(MinecraftServer server, FabricSettlementState.Settlement settlement) {
        return server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, settlement.dimension()));
    }

    /** Sends the attacker's raiders against the target; returns a description or why it cannot happen. */
    public static String start(ServerLevel level, FabricSettlementState.Settlement attacker, FabricSettlementState.Settlement target) {
        String attackerKey = VillageGrowth.key(attacker), targetKey = VillageGrowth.key(target);
        if (ACTIVE.containsKey(attackerKey)) return attacker.name() + " is already raiding.";
        BlockPos home = townHall(attacker);
        var raiders = level.getEntitiesOfClass(MillVillagerEntity.class, new net.minecraft.world.phys.AABB(home).inflate(attacker.radius(), 64, attacker.radius()),
                villager -> villager.isAlive() && !villager.isHired() && !villager.isChildVillager()
                        && villager.profile().map(p -> p.tags().contains("raider")).orElse(false)
                        && attackerKey.equals(villager.villageKey()));
        if (raiders.isEmpty()) return attacker.name() + " has no raiders at home.";
        Set<UUID> ids = new LinkedHashSet<>();
        raiders.stream().limit(MAX_RAIDERS).forEach(raider -> { ids.add(raider.getUUID()); raider.setTarget(null); raider.getNavigation().stop(); });
        Raid raid = new Raid(attackerKey, targetKey, home, townHall(target), ids, level.getGameTime());
        ACTIVE.put(attackerKey, raid);
        FabricVillageRelations.get(level.getServer()).add(attackerKey, targetKey, -RAID_PENALTY);
        announce(level, target, attacker.name() + " is raiding " + target.name() + " with " + ids.size() + " raiders!", ChatFormatting.RED);
        return attacker.name() + " sends " + ids.size() + " raiders against " + target.name() + ".";
    }

    private static void update(MinecraftServer server, Raid raid, long now) {
        var settlements = FabricSettlementState.get(server).settlements();
        var attacker = settlements.stream().filter(s -> VillageGrowth.key(s).equals(raid.attacker)).findFirst().orElse(null);
        var target = settlements.stream().filter(s -> VillageGrowth.key(s).equals(raid.target)).findFirst().orElse(null);
        ServerLevel level = attacker == null ? null : level(server, attacker);
        if (attacker == null || target == null || level == null || now - raid.started > RAID_TIMEOUT) { end(raid, null, null); return; }
        List<MillVillagerEntity> alive = new ArrayList<>();
        for (UUID id : raid.raiders) if (level.getEntity(id) instanceof MillVillagerEntity raider && raider.isAlive()) alive.add(raider);
        raid.raiders.retainAll(alive.stream().map(MillVillagerEntity::getUUID).toList());
        if (alive.isEmpty()) {
            announce(level, target, target.name() + " drove off the raiders of " + attacker.name() + ".", ChatFormatting.GREEN);
            end(raid, null, null);
            return;
        }
        BlockPos goal = raid.phase == Phase.RETURNING ? raid.home : raid.destination;
        boolean there = alive.stream().anyMatch(raider -> raider.blockPosition().closerThan(goal, ARRIVAL));
        switch (raid.phase) {
            case MARCHING -> { if (there) { raid.phase = Phase.LOOTING; raid.phaseStarted = now; } }
            case LOOTING -> {
                if (!there) { raid.phase = Phase.MARCHING; break; }
                if (now - raid.phaseStarted < LOOT_TICKS) break;
                raid.loot.addAll(take(level, target, LOOT_STACKS));
                announce(level, target, "The raiders of " + attacker.name() + " looted " + target.name()
                        + (raid.loot.isEmpty() ? ", but found nothing." : " and carry off " + describe(raid.loot) + "."), ChatFormatting.RED);
                raid.phase = Phase.RETURNING;
                raid.phaseStarted = now;
            }
            case RETURNING -> { if (there || now - raid.phaseStarted > RAID_TIMEOUT / 2) end(raid, level, attacker); }
        }
    }

    private static void end(Raid raid, ServerLevel level, FabricSettlementState.Settlement attacker) {
        ACTIVE.remove(raid.attacker);
        if (level != null && attacker != null && !raid.loot.isEmpty()) {
            List<ItemStack> left = put(level, attacker, raid.loot);
            BlockPos home = townHall(attacker);
            for (ItemStack stack : left)
                level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, home.getX() + 0.5, home.getY() + 1, home.getZ() + 0.5, stack));
        }
    }

    private static List<Container> chests(ServerLevel level, FabricSettlementState.Settlement settlement) {
        List<Container> result = new ArrayList<>();
        var centre = settlement.buildings().stream().filter(FabricSettlementState.Building::centre).findFirst()
                .orElse(settlement.buildings().getFirst()).placement();
        for (var p : centre.servicePoints().getOrDefault("chests", List.of())) {
            BlockPos pos = new BlockPos(p.x(), p.y(), p.z());
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof Container container && !result.contains(container)) result.add(container);
        }
        return result;
    }

    /** Takes up to {@code stacks} non-empty stacks from the town hall chests, largest first. */
    static List<ItemStack> take(ServerLevel level, FabricSettlementState.Settlement settlement, int stacks) {
        record Slot(Container container, int index, int count) {}
        List<Slot> slots = new ArrayList<>();
        for (Container container : chests(level, settlement))
            for (int i = 0; i < container.getContainerSize(); i++)
                if (!container.getItem(i).isEmpty()) slots.add(new Slot(container, i, container.getItem(i).getCount()));
        slots.sort(Comparator.comparingInt(Slot::count).reversed());
        List<ItemStack> taken = new ArrayList<>();
        for (Slot slot : slots.subList(0, Math.min(stacks, slots.size()))) {
            taken.add(slot.container().removeItemNoUpdate(slot.index()));
            slot.container().setChanged();
        }
        return taken;
    }

    /** Stores stacks in the town hall chests; returns what did not fit. */
    static List<ItemStack> put(ServerLevel level, FabricSettlementState.Settlement settlement, List<ItemStack> stacks) {
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack original : stacks) {
            ItemStack stack = original.copy();
            for (Container container : chests(level, settlement)) {
                for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
                    ItemStack present = container.getItem(i);
                    if (present.isEmpty()) { container.setItem(i, stack.copy()); stack.setCount(0); }
                    else if (ItemStack.isSameItemSameComponents(present, stack) && present.getCount() < present.getMaxStackSize()) {
                        int moved = Math.min(stack.getCount(), present.getMaxStackSize() - present.getCount());
                        present.grow(moved); stack.shrink(moved);
                    }
                }
                container.setChanged();
                if (stack.isEmpty()) break;
            }
            if (!stack.isEmpty()) left.add(stack);
        }
        return left;
    }

    private static String describe(List<ItemStack> loot) {
        return String.join(", ", loot.stream().map(stack -> stack.getCount() + " " + stack.getHoverName().getString()).toList());
    }

    /** Tells the players near the target village and its owner. */
    static void announce(ServerLevel level, FabricSettlementState.Settlement target, String text, ChatFormatting colour) {
        var owner = FabricVillageOwnership.get(level.getServer()).owner(VillageGrowth.key(target));
        BlockPos centre = new BlockPos(target.origin().x(), target.origin().y(), target.origin().z());
        for (var player : level.getServer().getPlayerList().getPlayers()) {
            boolean near = player.level() == level && player.blockPosition().closerThan(centre, 256);
            boolean owns = owner.map(o -> o.player().equals(player.getStringUUID())).orElse(false);
            if (near || owns) player.sendSystemMessage(Component.literal("[Millénaire] " + text).withStyle(colour));
        }
    }

    public static List<String> describeActive(ServerLevel level) {
        return ACTIVE.values().stream().map(raid -> raid.attacker + " -> " + raid.target + " " + raid.phase + " with " + raid.raiders.size()
                + " at " + String.join(" ", raid.raiders.stream().map(id -> level.getEntity(id) instanceof MillVillagerEntity v
                ? v.blockPosition().toShortString() : "?").toList()) + " towards " + raid.destination.toShortString()).toList();
    }
}
