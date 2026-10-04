package org.millenaire.fabric.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Building identity and the access policy that future village ownership can update. */
public record BuildingBinding(BlockPos origin, String plan, String name, boolean mainChest,
                              boolean locked, Optional<UUID> owner) {
    public static final Codec<BuildingBinding> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("origin").forGetter(BuildingBinding::origin),
            Codec.STRING.fieldOf("plan").forGetter(BuildingBinding::plan),
            Codec.STRING.fieldOf("name").forGetter(BuildingBinding::name),
            Codec.BOOL.optionalFieldOf("main_chest", false).forGetter(BuildingBinding::mainChest),
            Codec.BOOL.optionalFieldOf("locked", true).forGetter(BuildingBinding::locked),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(BuildingBinding::owner)
    ).apply(i, BuildingBinding::new));
    public BuildingBinding {
        origin = Objects.requireNonNull(origin).immutable();
        Objects.requireNonNull(plan); Objects.requireNonNull(name); Objects.requireNonNull(owner);
    }
    public boolean allows(UUID player, boolean administrator) {
        return !locked || administrator || owner.filter(id -> id.equals(player)).isPresent();
    }
}
