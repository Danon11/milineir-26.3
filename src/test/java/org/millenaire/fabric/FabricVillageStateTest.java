package org.millenaire.fabric;

import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricVillageStateTest {
    @Test
    void codecRoundTripPreservesPositionsAndDimensions() {
        FabricVillageState state = new FabricVillageState();
        state.mark(Identifier.fromNamespaceAndPath("minecraft", "overworld"), 12, 64, -31);
        state.mark(Identifier.fromNamespaceAndPath("minecraft", "the_nether"), 12, 64, -31);

        Tag encoded = FabricVillageState.codec().encodeStart(NbtOps.INSTANCE, state).getOrThrow();
        FabricVillageState decoded = FabricVillageState.codec().parse(NbtOps.INSTANCE, encoded).getOrThrow();

        assertEquals(state.marks(), decoded.marks());
    }

    @Test
    void markRejectsExactDuplicatesButKeepsDifferentDimensions() {
        FabricVillageState state = new FabricVillageState();
        Identifier overworld = Identifier.fromNamespaceAndPath("minecraft", "overworld");
        Identifier nether = Identifier.fromNamespaceAndPath("minecraft", "the_nether");

        assertTrue(state.mark(overworld, 0, 70, 0));
        assertTrue(state.isDirty());
        state.setDirty(false);
        assertFalse(state.mark(overworld, 0, 70, 0));
        assertFalse(state.isDirty());
        assertTrue(state.mark(nether, 0, 70, 0));
        assertTrue(state.isDirty());
        assertEquals(2, state.marks().size());
    }
}
