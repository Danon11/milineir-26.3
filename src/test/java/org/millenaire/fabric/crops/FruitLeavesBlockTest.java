package org.millenaire.fabric.crops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FruitLeavesBlockTest {
    @Test
    void followsLegacyDailyFruitStagesWithoutImmediateRegrowth() {
        assertEquals(1, FruitLeavesBlock.nextAge(0, 4_000));
        assertEquals(2, FruitLeavesBlock.nextAge(1, 5_500));
        assertEquals(3, FruitLeavesBlock.nextAge(2, 7_000));
        assertEquals(0, FruitLeavesBlock.nextAge(3, 12_000));
        assertEquals(0, FruitLeavesBlock.nextAge(0, 7_000));
        assertEquals(1, FruitLeavesBlock.nextAge(1, 7_000));
    }
}
