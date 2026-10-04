package org.millenaire.fabric.pathing;

/**
 * Ported from Millenaire 8.1.2's {@code org.millenaire.common.pathing.atomicstryker.AStarConfig}.
 * The supplied 1.12.2 JAR was decompiled with CFR 0.152; this class has no game API dependencies.
 */
public final class AStarConfig {
    public boolean canUseDoors;
    public boolean canTakeDiagonals;
    public boolean allowDropping;
    public boolean canSwim;
    public boolean canClearLeaves = true;
    public boolean tolerance;
    public int toleranceHorizontal;
    public int toleranceVertical;

    public AStarConfig(boolean canUseDoors, boolean makePathDiagonals, boolean allowDropping,
                       boolean canSwim, boolean canClearLeaves) {
        this.canUseDoors = canUseDoors;
        this.canTakeDiagonals = makePathDiagonals;
        this.allowDropping = allowDropping;
        this.canSwim = canSwim;
        this.canClearLeaves = canClearLeaves;
    }

    public AStarConfig(boolean canUseDoors, boolean makePathDiagonals, boolean allowDropping,
                       boolean canSwim, boolean canClearLeaves,
                       int toleranceHorizontal, int toleranceVertical) {
        this(canUseDoors, makePathDiagonals, allowDropping, canSwim, canClearLeaves);
        this.toleranceHorizontal = toleranceHorizontal;
        this.toleranceVertical = toleranceVertical;
        this.tolerance = true;
    }
}
