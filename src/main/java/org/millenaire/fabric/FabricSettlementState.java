package org.millenaire.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.millenaire.fabric.content.LegacyBuildingPlan.Position;
import org.millenaire.fabric.content.VillageLayout;
import org.millenaire.fabric.content.VillagePlacement;

import java.util.*;

/** Persisted successful starter layouts. Planning and failed placement never create records. */
public final class FabricSettlementState extends SavedData {
    private static final Codec<VillageLayout.Bounds> BOUNDS = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("min_x").forGetter(VillageLayout.Bounds::minX), Codec.INT.fieldOf("min_z").forGetter(VillageLayout.Bounds::minZ),
            Codec.INT.fieldOf("max_x").forGetter(VillageLayout.Bounds::maxX), Codec.INT.fieldOf("max_z").forGetter(VillageLayout.Bounds::maxZ)
    ).apply(i, VillageLayout.Bounds::new));
    private static final Codec<Building> BUILDING = RecordCodecBuilder.create(i -> i.group(
            FabricBuildingState.buildingCodec().fieldOf("placement").forGetter(Building::placement),
            BOUNDS.fieldOf("reserved_area").forGetter(Building::reservedArea),
            Codec.BOOL.fieldOf("centre").forGetter(Building::centre)
    ).apply(i, Building::new));
    private static final Codec<Settlement> SETTLEMENT = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("dimension").forGetter(Settlement::dimension),
            Codec.STRING.fieldOf("type").forGetter(Settlement::type), Codec.STRING.fieldOf("name").forGetter(Settlement::name),
            FabricBuildingState.positionCodec().fieldOf("origin").forGetter(Settlement::origin),
            Codec.LONG.fieldOf("seed").forGetter(Settlement::seed),
            Codec.intRange(1, VillageLayout.MAX_RADIUS).fieldOf("radius").forGetter(Settlement::radius),
            BUILDING.listOf().fieldOf("buildings").forGetter(Settlement::buildings)
    ).apply(i, Settlement::new));
    private static final Codec<FabricSettlementState> CODEC = RecordCodecBuilder.create(i -> i.group(
            SETTLEMENT.listOf().optionalFieldOf("settlements", List.of()).forGetter(FabricSettlementState::settlements)
    ).apply(i, FabricSettlementState::new));
    private static final SavedDataType<FabricSettlementState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("millenaire", "settlements"), FabricSettlementState::new, CODEC, DataFixTypes.LEVEL);
    private final List<Settlement> settlements;
    public FabricSettlementState() { this(List.of()); }
    private FabricSettlementState(List<Settlement> settlements) { this.settlements = new ArrayList<>(settlements); }
    public static FabricSettlementState get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(TYPE); }
    static Codec<FabricSettlementState> codec() { return CODEC; }
    public List<Settlement> settlements() { return List.copyOf(settlements); }
    public Optional<Settlement> containing(Identifier dimension, int x, int z) {
        return settlements.stream().filter(s -> s.dimension().equals(dimension)
                && x >= s.origin().x() - s.radius() && x <= s.origin().x() + s.radius()
                && z >= s.origin().z() - s.radius() && z <= s.origin().z() + s.radius()).findFirst();
    }

    public List<String> overlapIssues(Identifier dimension, VillageLayout.Layout layout) {
        return overlaps(dimension, layout.buildings().stream().map(VillageLayout.Building::reservedArea).toList());
    }
    private List<String> overlaps(Identifier dimension, List<VillageLayout.Bounds> areas) {
        List<String> issues = new ArrayList<>();
        for (var previous : settlements) if (previous.dimension().equals(dimension)
                && previous.buildings().stream().anyMatch(building -> areas.stream().anyMatch(area -> area.intersects(building.reservedArea()))))
            issues.add("Overlaps saved settlement " + previous.type() + " at " + previous.origin());
        return List.copyOf(issues);
    }
    public void record(Settlement settlement) {
        var issues = overlaps(settlement.dimension(), settlement.buildings().stream().map(Building::reservedArea).toList());
        if (!issues.isEmpty()) throw new IllegalArgumentException(String.join("; ", issues));
        settlements.add(settlement); setDirty();
    }
    public static Settlement from(Identifier dimension, VillagePlacement.Prepared prepared) {
        if (!prepared.supported()) throw new IllegalArgumentException("Cannot record an unsupported layout");
        var layout = prepared.layout();
        List<Building> buildings = prepared.buildings().stream().map(part -> {
            var building = part.building();
            return new Building(new FabricBuildingState.PlacedBuilding(dimension, building.plan().id(), building.origin(),
                    building.rotation(), part.blocks().servicePoints()), building.reservedArea(), building.centre());
        }).toList();
        return new Settlement(dimension, layout.type().culture() + ":" + layout.type().id(), layout.type().name(), layout.origin(),
                layout.seed(), layout.radius(), buildings);
    }
    public record Building(FabricBuildingState.PlacedBuilding placement, VillageLayout.Bounds reservedArea, boolean centre) {
        public Building { Objects.requireNonNull(placement); Objects.requireNonNull(reservedArea); }
    }
    public record Settlement(Identifier dimension, String type, String name, Position origin, long seed, int radius, List<Building> buildings) {
        public Settlement {
            Objects.requireNonNull(dimension); Objects.requireNonNull(type); Objects.requireNonNull(name); Objects.requireNonNull(origin);
            buildings = List.copyOf(buildings);
            if (radius < 1 || radius > VillageLayout.MAX_RADIUS) throw new IllegalArgumentException("Invalid radius");
            if (buildings.isEmpty() || buildings.size() > VillageLayout.MAX_BUILDINGS
                    || buildings.stream().filter(Building::centre).count() != 1) throw new IllegalArgumentException("Invalid starter buildings");
            if (buildings.stream().anyMatch(building -> !building.placement().dimension().equals(dimension)))
                throw new IllegalArgumentException("Building dimension mismatch");
        }
    }
}
