package org.millenaire.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class FabricVillageState extends SavedData {
    private static final Codec<FabricVillageState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            VillageMark.CODEC.listOf().optionalFieldOf("marks", List.of())
                    .forGetter(state -> List.copyOf(state.marks))
    ).apply(instance, FabricVillageState::new));

    private static final SavedDataType<FabricVillageState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("millenaire", "villages"),
            FabricVillageState::new,
            CODEC,
            DataFixTypes.LEVEL);

    private final List<VillageMark> marks;

    public FabricVillageState() {
        this(List.of());
    }

    private FabricVillageState(List<VillageMark> marks) {
        this.marks = new ArrayList<>(marks);
    }

    public static FabricVillageState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    static Codec<FabricVillageState> codec() {
        return CODEC;
    }

    public boolean mark(Identifier dimension, int x, int y, int z) {
        VillageMark mark = new VillageMark(dimension, x, y, z);
        if (marks.contains(mark)) {
            return false;
        }

        marks.add(mark);
        setDirty();
        return true;
    }

    public List<VillageMark> marks() {
        return List.copyOf(marks);
    }

    public record VillageMark(Identifier dimension, int x, int y, int z) {
        private static final Codec<VillageMark> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("dimension").forGetter(VillageMark::dimension),
                Codec.INT.fieldOf("x").forGetter(VillageMark::x),
                Codec.INT.fieldOf("y").forGetter(VillageMark::y),
                Codec.INT.fieldOf("z").forGetter(VillageMark::z)
        ).apply(instance, VillageMark::new));

        public VillageMark {
            Objects.requireNonNull(dimension, "dimension");
        }
    }
}
