package org.millenaire.fabric.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.LinkedHashMap;
import java.util.Map;

/** Expands a portal only inside a complete obsidian frame present in the plan. */
final class PlannedPortal {
    private PlannedPortal() {}

    static Map<BlockPos, BlockState> expand(Map<BlockPos, BlockState> plan, BlockPos seed, Direction.Axis preferred) {
        for (Direction.Axis axis : new Direction.Axis[]{preferred, preferred == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X}) {
            var result = find(plan, seed, axis);
            if (!result.isEmpty()) return result;
        }
        throw new IllegalArgumentException("Portal needs a complete planned obsidian frame (interior 2-21 by 3-21) at " + seed);
    }

    private static Map<BlockPos, BlockState> find(Map<BlockPos, BlockState> plan, BlockPos seed, Direction.Axis axis) {
        Direction right = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        BlockPos bottom = seed;
        for (int down = 0; down < 21 && interior(plan.get(bottom.below())); down++) bottom = bottom.below();
        if (!frame(plan.get(bottom.below()))) return Map.of();
        BlockPos left = bottom;
        for (int offset = 0; offset < 21 && interior(plan.get(left.relative(right.getOpposite()))); offset++)
            left = left.relative(right.getOpposite());
        if (!frame(plan.get(left.relative(right.getOpposite())))) return Map.of();
        int width = 0;
        while (width <= 21 && interior(plan.get(left.relative(right, width)))) {
            if (!frame(plan.get(left.relative(right, width).below()))) return Map.of();
            width++;
        }
        if (width < 2 || width > 21 || !frame(plan.get(left.relative(right, width)))) return Map.of();
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, axis);
        for (int height = 0; height <= 21; height++) {
            BlockPos row = left.above(height);
            boolean top = true;
            for (int x = 0; x < width; x++) top &= frame(plan.get(row.relative(right, x)));
            if (top) return height >= 3 ? blocks : Map.of();
            if (height == 21 || !frame(plan.get(row.relative(right.getOpposite()))) || !frame(plan.get(row.relative(right, width)))) return Map.of();
            for (int x = 0; x < width; x++) {
                BlockPos pos = row.relative(right, x);
                if (!interior(plan.get(pos))) return Map.of();
                blocks.put(pos, portal);
            }
        }
        return Map.of();
    }

    private static boolean frame(BlockState state) { return state != null && state.is(Blocks.OBSIDIAN); }
    private static boolean interior(BlockState state) {
        return state != null && (state.isAir() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.NETHER_PORTAL));
    }
}
