package org.millenaire.fabric.pathing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathingValueTypesTest {
    @Test
    void pathingTileCopiesPositionAndLaddersAreNotWalkable() {
        short[] position = {2, 3, 4};
        PathingPathCalcTile tile = new PathingPathCalcTile(true, true, position);
        position[0] = 9;

        assertTrue(tile.ladder);
        assertFalse(tile.isWalkable);
        assertArrayEquals(new short[]{2, 3, 4}, tile.position);

        PathingPathCalcTile copy = new PathingPathCalcTile(tile);
        assertNotSame(tile.position, copy.position);
        assertArrayEquals(tile.position, copy.position);
    }

    @Test
    void astarConfigPreservesMovementOptionsAndOptionalTolerance() {
        AStarConfig regular = new AStarConfig(true, false, true, false, false);
        assertTrue(regular.canUseDoors);
        assertFalse(regular.canTakeDiagonals);
        assertTrue(regular.allowDropping);
        assertFalse(regular.canSwim);
        assertFalse(regular.canClearLeaves);
        assertFalse(regular.tolerance);

        AStarConfig tolerant = new AStarConfig(false, true, false, true, true, 4, 2);
        assertTrue(tolerant.canTakeDiagonals);
        assertTrue(tolerant.canSwim);
        assertTrue(tolerant.canClearLeaves);
        assertTrue(tolerant.tolerance);
        org.junit.jupiter.api.Assertions.assertEquals(4, tolerant.toleranceHorizontal);
        org.junit.jupiter.api.Assertions.assertEquals(2, tolerant.toleranceVertical);
    }
}
