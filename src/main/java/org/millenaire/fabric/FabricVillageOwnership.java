package org.millenaire.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

/**
 * Player-controlled villages: who owns each settlement (by settlement key) and the buildings the owner has
 * ordered, in order. Natural villages have no entry here.
 */
public final class FabricVillageOwnership extends SavedData {
    public record Owner(String player, String name, List<String> orders) {
        public Owner {
            Objects.requireNonNull(player); Objects.requireNonNull(name);
            orders = List.copyOf(orders);
        }
        public UUID uuid() { return UUID.fromString(player); }
    }

    private static final Codec<Owner> OWNER = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("player").forGetter(Owner::player),
            Codec.STRING.optionalFieldOf("name", "").forGetter(Owner::name),
            Codec.STRING.listOf().optionalFieldOf("orders", List.of()).forGetter(Owner::orders)
    ).apply(i, Owner::new));
    private static final Codec<FabricVillageOwnership> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, OWNER).optionalFieldOf("villages", Map.of()).forGetter(s -> s.owners)
    ).apply(i, FabricVillageOwnership::new));
    private static final SavedDataType<FabricVillageOwnership> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("millenaire", "ownership"), FabricVillageOwnership::new, CODEC, DataFixTypes.LEVEL);

    private final Map<String, Owner> owners = new TreeMap<>();

    public FabricVillageOwnership() {}
    private FabricVillageOwnership(Map<String, Owner> owners) { this.owners.putAll(owners); }

    public static FabricVillageOwnership get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(TYPE); }
    static Codec<FabricVillageOwnership> codec() { return CODEC; }

    public Optional<Owner> owner(String village) { return Optional.ofNullable(owners.get(village)); }

    public boolean owns(String village, UUID player) {
        return owner(village).map(owner -> owner.player().equals(player.toString())).orElse(false);
    }

    public Map<String, Owner> all() { return Collections.unmodifiableMap(owners); }

    public void claim(String village, UUID player, String name) {
        owners.put(village, new Owner(player.toString(), name, owner(village).map(Owner::orders).orElse(List.of())));
        setDirty();
    }

    /** Adds a building order; the same plan is not queued twice. */
    public boolean order(String village, String plan) {
        var owner = owners.get(village);
        if (owner == null || owner.orders().contains(plan)) return false;
        List<String> orders = new ArrayList<>(owner.orders());
        orders.add(plan);
        owners.put(village, new Owner(owner.player(), owner.name(), orders));
        setDirty();
        return true;
    }

    public void completeOrder(String village, String plan) {
        var owner = owners.get(village);
        if (owner == null || !owner.orders().contains(plan)) return;
        List<String> orders = new ArrayList<>(owner.orders());
        orders.remove(plan);
        owners.put(village, new Owner(owner.player(), owner.name(), orders));
        setDirty();
    }
}
