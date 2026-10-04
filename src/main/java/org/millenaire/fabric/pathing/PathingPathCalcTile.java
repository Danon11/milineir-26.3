package org.millenaire.fabric.pathing;

/**
 * Ported from Millenaire 8.1.2's {@code org.millenaire.common.pathing.PathingPathCalcTile}.
 * The supplied 1.12.2 JAR was decompiled with CFR 0.152; this coordinate holder has no game API dependencies.
 */
public final class PathingPathCalcTile {
    public boolean ladder;
    public boolean isWalkable;
    public short[] position;

    public PathingPathCalcTile(boolean walkable, boolean ladder, short[] position) {
        this.ladder = ladder;
        this.isWalkable = !ladder && walkable;
        this.position = position.clone();
    }

    public PathingPathCalcTile(PathingPathCalcTile tile) {
        this.ladder = tile.ladder;
        this.isWalkable = tile.isWalkable;
        this.position = tile.position.clone();
    }
}
