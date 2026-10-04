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

/** Manual placements and their service points survive world reloads. */
public final class FabricBuildingState extends SavedData {
    private static final Codec<Position> POSITION = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("x").forGetter(Position::x), Codec.INT.fieldOf("y").forGetter(Position::y),
            Codec.INT.fieldOf("z").forGetter(Position::z)).apply(i, Position::new));
    private static final Codec<PlacedBuilding> BUILDING = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("dimension").forGetter(PlacedBuilding::dimension),
            Codec.STRING.fieldOf("plan").forGetter(PlacedBuilding::plan), POSITION.fieldOf("origin").forGetter(PlacedBuilding::origin),
            Codec.intRange(0, 3).fieldOf("rotation").forGetter(PlacedBuilding::rotation),
            Codec.unboundedMap(Codec.STRING, POSITION.listOf()).optionalFieldOf("service_points", Map.of()).forGetter(PlacedBuilding::servicePoints)
    ).apply(i, PlacedBuilding::new));
    private static final Codec<FabricBuildingState> CODEC = RecordCodecBuilder.create(i -> i.group(
            BUILDING.listOf().optionalFieldOf("buildings", List.of()).forGetter(FabricBuildingState::buildings)
    ).apply(i, FabricBuildingState::new));
    private static final SavedDataType<FabricBuildingState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("millenaire", "buildings"), FabricBuildingState::new, CODEC, DataFixTypes.LEVEL);
    private final List<PlacedBuilding> buildings;
    public FabricBuildingState() { this(List.of()); }
    private FabricBuildingState(List<PlacedBuilding> buildings) { this.buildings = new ArrayList<>(buildings); }
    public static FabricBuildingState get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(TYPE); }
    static Codec<FabricBuildingState> codec() { return CODEC; }
    static Codec<PlacedBuilding> buildingCodec() { return BUILDING; }
    static Codec<Position> positionCodec() { return POSITION; }
    public List<PlacedBuilding> buildings() { return List.copyOf(buildings); }
    public void record(PlacedBuilding building) {
        // Upgrades replace the record at the same origin; sub-buildings share it under a different plan key.
        buildings.removeIf(previous -> previous.dimension().equals(building.dimension()) && previous.origin().equals(building.origin())
                && planKey(previous.plan()).equals(planKey(building.plan())));
        buildings.add(building);
        setDirty();
    }
    /** Plan identifier without its upgrade level: {@code norman:fountain_A1} becomes {@code norman:fountain_A}. */
    static String planKey(String plan) { return plan.replaceAll("\\d+$", ""); }

    public record PlacedBuilding(Identifier dimension, String plan, Position origin, int rotation, Map<String, List<Position>> servicePoints) {
        public PlacedBuilding {
            Objects.requireNonNull(dimension); Objects.requireNonNull(plan); Objects.requireNonNull(origin);
            if (rotation < 0 || rotation > 3) throw new IllegalArgumentException("Rotation must be 0..3");
            Map<String, List<Position>> copy = new LinkedHashMap<>();
            servicePoints.forEach((key, points) -> copy.put(key, List.copyOf(points)));
            servicePoints = Collections.unmodifiableMap(copy);
        }
    }
}
