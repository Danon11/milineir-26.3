package org.millenaire.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.millenaire.fabric.content.LegacyBuildingPlan.Position;

import java.util.*;

/** Persistent villager records used by the settlement simulation and future entity layer. */
public final class FabricVillagerState extends SavedData {
    private static final Codec<Position> POSITION = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("x").forGetter(Position::x), Codec.INT.fieldOf("y").forGetter(Position::y),
            Codec.INT.fieldOf("z").forGetter(Position::z)).apply(i, Position::new));
    private static final Codec<Villager> VILLAGER = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(Villager::id),
            Identifier.CODEC.fieldOf("dimension").forGetter(Villager::dimension),
            POSITION.fieldOf("position").forGetter(Villager::position),
            Codec.STRING.fieldOf("culture").forGetter(Villager::culture),
            Codec.STRING.fieldOf("type").forGetter(Villager::type),
            Codec.STRING.fieldOf("name").forGetter(Villager::name),
            Codec.STRING.fieldOf("gender").forGetter(Villager::gender),
            Codec.STRING.optionalFieldOf("building", "").forGetter(Villager::building),
            Codec.BOOL.fieldOf("alive").forGetter(Villager::alive)
    ).apply(i, Villager::new));
    private static final Codec<FabricVillagerState> CODEC = RecordCodecBuilder.create(i -> i.group(
            VILLAGER.listOf().optionalFieldOf("villagers", List.of()).forGetter(s -> List.copyOf(s.villagers))
    ).apply(i, FabricVillagerState::new));
    private static final SavedDataType<FabricVillagerState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("millenaire", "villagers"), FabricVillagerState::new, CODEC, DataFixTypes.LEVEL);
    private final List<Villager> villagers;
    public FabricVillagerState() { this(List.of()); }
    private FabricVillagerState(List<Villager> villagers) { this.villagers = new ArrayList<>(villagers); }
    public static FabricVillagerState get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(TYPE); }
    static Codec<FabricVillagerState> codec() { return CODEC; }
    public List<Villager> villagers() { return List.copyOf(villagers); }
    public List<Villager> inSettlement(FabricSettlementState.Settlement settlement) {
        if (settlement == null) return List.of();
        return villagers.stream().filter(v -> v.dimension().equals(settlement.dimension())
                && settlement.buildings().stream().anyMatch(b -> b.placement().origin().x() - settlement.radius() <= v.position().x()
                && b.placement().origin().x() + settlement.radius() >= v.position().x()
                && b.placement().origin().z() - settlement.radius() <= v.position().z()
                && b.placement().origin().z() + settlement.radius() >= v.position().z())).toList();
    }
    public Optional<Villager> find(String id) { return villagers.stream().filter(v -> v.id().equals(id)).findFirst(); }
    public void upsert(Villager villager) {
        Objects.requireNonNull(villager);
        villagers.removeIf(previous -> previous.id().equals(villager.id()));
        villagers.add(villager); setDirty();
    }
    public boolean remove(String id) { boolean changed = villagers.removeIf(v -> v.id().equals(id)); if (changed) setDirty(); return changed; }
    public record Villager(String id, Identifier dimension, Position position, String culture, String type,
                           String name, String gender, String building, boolean alive) {
        public Villager {
            if (id == null || id.isBlank() || dimension == null || position == null || culture == null || culture.isBlank()
                    || type == null || type.isBlank() || name == null || name.isBlank() || gender == null) throw new IllegalArgumentException("Invalid villager");
            building = building == null ? "" : building;
        }
    }
}
