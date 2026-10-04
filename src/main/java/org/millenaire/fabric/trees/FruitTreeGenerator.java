package org.millenaire.fabric.trees;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.fabric.LegacyContentRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public final class FruitTreeGenerator {
    enum Kind {
        APPLE("sapling_appletree", "leaves_appletree", Blocks.OAK_LOG, 5, 2),
        OLIVE("sapling_olivetree", "leaves_olivetree", Blocks.ACACIA_LOG, 5, 2),
        PISTACHIO("sapling_pistachio", "leaves_pistachio", Blocks.OAK_LOG, 6, 3),
        CHERRY("sapling_cherry", "cherry_leaves", Blocks.SPRUCE_LOG, 6, 2),
        SAKURA("sapling_sakura", "sakura_leaves", Blocks.SPRUCE_LOG, 5, 2);

        final String sapling;
        final String leaves;
        final net.minecraft.world.level.block.Block log;
        final int minHeight;
        final int heightRange;

        Kind(String sapling, String leaves, net.minecraft.world.level.block.Block log, int minHeight, int heightRange) {
            this.sapling = sapling;
            this.leaves = leaves;
            this.log = log;
            this.minHeight = minHeight;
            this.heightRange = heightRange;
        }

        static Kind fromSapling(String name) {
            for (Kind kind : values()) if (kind.sapling.equals(name)) return kind;
            throw new IllegalArgumentException("Unknown Millenaire sapling: " + name);
        }
    }

    enum Part { LEAF, LOG_Y, LOG_X, LOG_Z }
    record Offset(int x, int y, int z) {
        BlockPos at(BlockPos origin) { return origin.offset(x, y, z); }
    }
    record Shape(int height, Map<Offset, Part> parts) {
        Shape { parts = Map.copyOf(parts); }
    }
    interface TreeWorld {
        boolean isInBounds(BlockPos pos);
        boolean isLoaded(BlockPos pos);
        boolean hasBlockEntity(BlockPos pos);
        BlockState getBlockState(BlockPos pos);
        boolean setBlock(BlockPos pos, BlockState state);
    }

    private FruitTreeGenerator() {}

    public static boolean supportsMarker(String marker) {
        return switch (marker) {
            case "appletreespawn", "olivetreespawn", "pistachiotreespawn",
                    "cherrytreespawn", "sakuratreespawn" -> true;
            default -> false;
        };
    }

    public static Map<BlockPos, BlockState> plannedBlocks(String marker, BlockPos origin, long seed) {
        Kind kind = switch (marker) {
            case "appletreespawn" -> Kind.APPLE;
            case "olivetreespawn" -> Kind.OLIVE;
            case "pistachiotreespawn" -> Kind.PISTACHIO;
            case "cherrytreespawn" -> Kind.CHERRY;
            case "sakuratreespawn" -> Kind.SAKURA;
            default -> throw new IllegalArgumentException("Unsupported tree marker: " + marker);
        };
        Shape shape = shape(kind, RandomSource.create(seed ^ origin.asLong()));
        BlockState leaf = LegacyContentRegistry.block(kind.leaves).defaultBlockState()
                .setValue(LeavesBlock.DISTANCE, 1);
        BlockState log = kind.log.defaultBlockState();
        Map<BlockPos, BlockState> result = new LinkedHashMap<>();
        for (var entry : shape.parts().entrySet()) {
            BlockState state = switch (entry.getValue()) {
                case LEAF -> leaf;
                case LOG_Y -> log;
                case LOG_X -> log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
                case LOG_Z -> log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
            };
            result.put(entry.getKey().at(origin), state);
        }
        return Map.copyOf(result);
    }

    static Shape shape(Kind kind, RandomSource random) {
        int height = kind.minHeight + random.nextInt(kind.heightRange);
        Map<Offset, Part> parts = new LinkedHashMap<>();
        if (kind == Kind.APPLE || kind == Kind.OLIVE) {
            branchy(parts, height, random);
        } else {
            crowned(parts, kind, height, random);
        }
        return new Shape(height, parts);
    }

    private static void branchy(Map<Offset, Part> parts, int height, RandomSource random) {
        for (int y = 0; y < 5; y++) putLog(parts, new Offset(0, y, 0), Part.LOG_Y);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int length = 2 + random.nextInt(2);
            int y = 3;
            int x = 0;
            int z = 0;
            int curve = random.nextBoolean() ? 1 : -1;
            for (int step = 0; step < length; step++) {
                if (y < height && random.nextFloat() < 0.7F) y++;
                x += direction.getStepX();
                z += direction.getStepZ();
                if (random.nextFloat() < 0.15F) {
                    if (direction.getStepX() != 0) z += curve;
                    else x += curve;
                }
                Offset branch = new Offset(x, y, z);
                putLog(parts, branch, direction.getStepX() != 0 ? Part.LOG_X : Part.LOG_Z);
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dz = -1; dz <= 1; dz++)
                            if (random.nextBoolean()) putLeaf(parts, new Offset(x + dx, y + dy, z + dz));
            }
        }
    }

    private static void crowned(Map<Offset, Part> parts, Kind kind, int height, RandomSource random) {
        boolean pistachio = kind == Kind.PISTACHIO;
        int firstY = pistachio ? 3 : 2;
        int lastY = pistachio ? height : height + 1;
        for (int y = firstY; y <= lastY; y++) {
            int radius = pistachio ? 4 : 3;
            int lower = pistachio ? 5 : 4;
            int upper = pistachio ? height - 3 : height - 2;
            if (y < lower) radius -= lower - y;
            else if (y > upper) radius -= y - upper;
            if (radius < 0) continue;
            for (int x = -radius; x <= radius; x++)
                for (int z = -radius; z <= radius; z++) {
                    int chance = Math.abs(x) == radius && Math.abs(z) == radius ? 0
                            : Math.abs(x) == radius || Math.abs(z) == radius ? 80
                            : pistachio ? 100 : 95;
                    if (random.nextInt(100) < chance) putLeaf(parts, new Offset(x, y, z));
                }
        }
        for (int y = 0; y < height; y++) putLog(parts, new Offset(0, y, 0), Part.LOG_Y);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (random.nextInt(100) >= (pistachio ? 70 : 60)) continue;
            int branchTop = height - random.nextInt(2);
            int branchStart = 3 + random.nextInt(2);
            int horizontal = (pistachio ? 3 : 2) - random.nextInt(2);
            int x = 0;
            int z = 0;
            for (int y = 0; y < branchTop; y++) {
                if (y >= branchStart && horizontal > 0) {
                    x += direction.getStepX();
                    z += direction.getStepZ();
                    horizontal--;
                }
                putLog(parts, new Offset(x, y, z), Part.LOG_Y);
            }
        }
    }

    private static void putLeaf(Map<Offset, Part> parts, Offset offset) {
        parts.putIfAbsent(offset, Part.LEAF);
    }

    private static void putLog(Map<Offset, Part> parts, Offset offset, Part part) {
        parts.put(offset, part);
    }

    static boolean grow(ServerLevel level, BlockPos origin, BlockState sapling, Kind kind, RandomSource random) {
        Shape shape = shape(kind, random);
        if (!sapling.canSurvive(level, origin)) return false;
        BlockState leafState = LegacyContentRegistry.block(kind.leaves).defaultBlockState()
                .setValue(LeavesBlock.DISTANCE, 1);
        BlockState logState = kind.log.defaultBlockState();
        return place(shape, origin, sapling, leafState, logState, new TreeWorld() {
            public boolean isInBounds(BlockPos pos) { return level.isInWorldBounds(pos); }
            public boolean isLoaded(BlockPos pos) { return level.isLoaded(pos); }
            public boolean hasBlockEntity(BlockPos pos) { return level.getBlockEntity(pos) != null; }
            public BlockState getBlockState(BlockPos pos) { return level.getBlockState(pos); }
            public boolean setBlock(BlockPos pos, BlockState state) { return level.setBlock(pos, state, 3); }
        });
    }

    static boolean place(Shape shape, BlockPos origin, BlockState sapling, BlockState leafState,
                         BlockState logState, TreeWorld level) {
        for (int y = 0; y <= shape.height() + 1; y++) {
            int radius = y == 0 ? 0 : y >= shape.height() - 1 ? 2 : 1;
            for (int x = -radius; x <= radius; x++)
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!level.isInBounds(pos) || !level.isLoaded(pos)
                            || !replaceable(level.getBlockState(pos), pos.equals(origin), sapling))
                        return false;
                }
        }
        Map<BlockPos, BlockState> writes = new LinkedHashMap<>();
        Map<BlockPos, BlockState> previousStates = new LinkedHashMap<>();
        for (var entry : shape.parts().entrySet()) {
            BlockPos pos = entry.getKey().at(origin);
            if (!level.isInBounds(pos) || !level.isLoaded(pos)) return false;
            BlockState previous = level.getBlockState(pos);
            if (!pos.equals(origin) && level.hasBlockEntity(pos)) return false;
            if (entry.getValue() != Part.LEAF && !replaceable(previous, pos.equals(origin), sapling)) return false;
            if (entry.getValue() == Part.LEAF && !replaceable(previous, false, sapling)) continue;
            BlockState next = switch (entry.getValue()) {
                case LEAF -> leafState;
                case LOG_Y -> logState;
                case LOG_X -> logState.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
                case LOG_Z -> logState.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
            };
            writes.put(pos, next);
            previousStates.put(pos, previous);
        }
        if (!writes.containsKey(origin) || writes.get(origin).getBlock() != logState.getBlock()) return false;
        List<BlockPos> changed = new ArrayList<>();
        try {
            for (boolean logs : new boolean[]{false, true}) {
                for (var entry : writes.entrySet()) {
                    if ((entry.getValue().getBlock() == logState.getBlock()) != logs) continue;
                    changed.add(entry.getKey());
                    if (!level.setBlock(entry.getKey(), entry.getValue()))
                        throw new IllegalStateException("Could not place tree block at " + entry.getKey());
                }
            }
            return true;
        } catch (RuntimeException exception) {
            for (int index = changed.size() - 1; index >= 0; index--) {
                BlockPos pos = changed.get(index);
                level.setBlock(pos, previousStates.get(pos));
            }
            return false;
        }
    }

    private static boolean replaceable(BlockState state, boolean origin, BlockState sapling) {
        return origin && state.is(sapling.getBlock()) || state.isAir()
                || state.canBeReplaced() || state.getBlock() instanceof LeavesBlock
                || state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE);
    }
}
