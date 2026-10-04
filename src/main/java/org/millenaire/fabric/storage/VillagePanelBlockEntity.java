package org.millenaire.fabric.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Optional;

public final class VillagePanelBlockEntity extends SignBlockEntity {
    private BuildingBinding binding;
    public VillagePanelBlockEntity(BlockPos pos, BlockState state) { this(VillageStorageContent.PANEL_TYPE, pos, state); }
    VillagePanelBlockEntity(BlockEntityType<? extends SignBlockEntity> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    public Optional<BuildingBinding> binding() { return Optional.ofNullable(binding); }
    public void bind(BuildingBinding value) {
        binding = value;
        setText(labelText(value), SignTextSlot.FRONT); setWaxed(true); setChanged();
    }
    static SignText labelText(BuildingBinding value) {
        var text = SignText.EMPTY.asMutable();
        text.setLine(0, Component.literal("Millénaire"), Component.literal("Millénaire"));
        // Short lines stay within the vanilla sign's text area. Full identity remains in saved data.
        int[] name = value.name().codePoints().toArray();
        for (int i = 0; i < 3; i++) {
            int begin = Math.min(i * 10, name.length), end = Math.min((i + 1) * 10, name.length);
            var line = Component.literal(new String(name, begin, end - begin)); text.setLine(i + 1, line, line);
        }
        return text.asImmutable();
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input); binding = input.read("millenaire_building", BuildingBinding.CODEC).orElse(null);
    }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output); output.storeNullable("millenaire_building", BuildingBinding.CODEC, binding);
    }
}
