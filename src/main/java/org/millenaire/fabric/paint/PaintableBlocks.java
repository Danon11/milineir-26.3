package org.millenaire.fabric.paint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class PaintableBlocks {
    /** One use affects at most the legacy bucket's 2048 durability points. */
    public static final int MAX_BLOCKS_PER_USE = 2048;
    private static final int MAX_QUEUED_POSITIONS = MAX_BLOCKS_PER_USE * 6 + 1;
    private static final String MOD_ID = "millenaire";
    private static final Set<String> COLORS = Set.of(
            "black", "blue", "brown", "cyan", "gray", "green", "light_blue", "lime",
            "magenta", "orange", "pink", "purple", "red", "silver", "white", "yellow");

    private PaintableBlocks() {
    }

    /** Creates the correctly shaped block for a supported legacy painted-brick ID. */
    public static Block create(String name, BlockState stairBase, BlockBehaviour.Properties properties) {
        Variant variant = parseBlockName(name)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported painted-brick block ID: " + name));
        return switch (variant.shape()) {
            case DECORATED_BLOCK -> new DecoratedBrickBlock(variant.color(), properties);
            case BLOCK -> new Block(properties);
            case SLAB -> new SlabBlock(properties);
            case STAIRS -> new StairBlock(stairBase, properties);
            case WALL -> new WallBlock(properties);
        };
    }

    /** Parses the legacy registry path, retaining the shape family and legacy color spelling. */
    static Optional<Variant> parseBlockName(String name) {
        String path = name.toLowerCase(Locale.ROOT);
        for (Shape shape : Shape.values()) {
            if (!path.startsWith(shape.prefix())) {
                continue;
            }
            String color = path.substring(shape.prefix().length());
            if (COLORS.contains(color)) {
                return Optional.of(new Variant(shape, color));
            }
        }
        return Optional.empty();
    }

    static String blockNameForColor(Variant source, String targetColor) {
        String color = normalizeColor(targetColor);
        if (!COLORS.contains(color)) {
            throw new IllegalArgumentException("Unsupported paint color: " + targetColor);
        }
        return source.shape().prefix() + color;
    }

    /** Maps old Millénaire's silver naming to the equivalent current Minecraft dye name. */
    static String vanillaDyeName(String legacyColor) {
        String color = normalizeColor(legacyColor);
        return color.equals("silver") ? "light_gray" : color;
    }

    static String normalizeColor(String color) {
        return color.toLowerCase(Locale.ROOT);
    }

    static BlockState copyCompatibleProperties(BlockState source, Block targetBlock) {
        BlockState target = targetBlock.defaultBlockState();
        for (Property.Value<?> sourceValue : source.getValues().toList()) {
            Property<?> targetProperty = targetBlock.getStateDefinition()
                    .getProperty(sourceValue.property().getName());
            if (targetProperty == null || !targetProperty.getValueClass().isInstance(sourceValue.value())
                    || !targetProperty.getPossibleValues().contains(sourceValue.value())) {
                continue;
            }
            target = setProperty(target, targetProperty, sourceValue.value());
        }
        return target;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState setProperty(BlockState state, Property property, Comparable value) {
        return state.setValue(property, value);
    }

    static InteractionResult recolor(ItemStack stack, String targetColor, ServerLevel level,
                                     BlockPos clickedPos, Player player, InteractionHand hand) {
        if (player.isSpectator() || !player.mayInteract(level, clickedPos)) {
            return InteractionResult.PASS;
        }

        BlockState clickedState = level.getBlockState(clickedPos);
        Optional<Variant> clickedVariant = variantForBlock(clickedState.getBlock());
        String normalizedTarget = normalizeColor(targetColor);
        if (clickedVariant.isEmpty() || clickedVariant.get().color().equals(normalizedTarget)) {
            return InteractionResult.PASS;
        }

        Variant sourceVariant = clickedVariant.get();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        BlockPos origin = clickedPos.immutable();
        queue.add(origin);
        visited.add(origin);
        int painted = 0;

        while (!queue.isEmpty() && painted < MAX_BLOCKS_PER_USE) {
            BlockPos pos = queue.removeFirst();
            if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos) || !player.mayInteract(level, pos)) {
                continue;
            }

            BlockState oldState = level.getBlockState(pos);
            Optional<Variant> variant = variantForBlock(oldState.getBlock());
            if (variant.isEmpty() || !variant.get().color().equals(sourceVariant.color())) {
                continue;
            }

            String newName = blockNameForColor(variant.get(), normalizedTarget);
            Block targetBlock = BuiltInRegistries.BLOCK.getOptional(
                    Identifier.fromNamespaceAndPath(MOD_ID, newName)).orElse(null);
            if (targetBlock == null || targetBlock == net.minecraft.world.level.block.Blocks.AIR) {
                continue;
            }

            BlockState newState = copyCompatibleProperties(oldState, targetBlock);
            if (!level.setBlock(pos, newState, Block.UPDATE_ALL)) {
                continue;
            }
            painted++;

            if (painted == MAX_BLOCKS_PER_USE) {
                break;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction).immutable();
                if (visited.size() >= MAX_QUEUED_POSITIONS) {
                    break;
                }
                if (visited.add(next)) {
                    queue.addLast(next);
                }
            }
        }

        if (painted == 0) {
            return InteractionResult.PASS;
        }
        ItemStack remainder = stack.hurtAndConvertOnBreak(
                painted, net.minecraft.world.item.Items.BUCKET, player, hand.asEquipmentSlot());
        player.setItemInHand(hand, remainder);
        return InteractionResult.SUCCESS;
    }

    static Optional<Variant> variantForBlock(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null || !id.getNamespace().equals(MOD_ID)) {
            return Optional.empty();
        }
        return parseBlockName(id.getPath());
    }

    public enum Shape {
        DECORATED_BLOCK("painted_brick_decorated_"),
        BLOCK("painted_brick_"),
        SLAB("slab_painted_brick_"),
        STAIRS("stairs_painted_brick_"),
        WALL("wall_painted_brick_");

        private final String prefix;

        Shape(String prefix) {
            this.prefix = prefix;
        }

        String prefix() {
            return prefix;
        }
    }

    record Variant(Shape shape, String color) {
    }

    private static final class DecoratedBrickBlock extends Block {
        private static final BooleanProperty TOP_FRIEZE = BooleanProperty.create("top_frieze");
        private static final BooleanProperty BOTTOM_FRIEZE = BooleanProperty.create("bottom_frieze");
        private final String color;

        private DecoratedBrickBlock(String color, BlockBehaviour.Properties properties) {
            super(properties);
            this.color = color;
            registerDefaultState(defaultBlockState()
                    .setValue(TOP_FRIEZE, false)
                    .setValue(BOTTOM_FRIEZE, false));
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return withFrieze(defaultBlockState(), context.getLevel(), context.getClickedPos());
        }

        @Override
        protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
                                         BlockPos pos, Direction direction, BlockPos neighborPos,
                                         BlockState neighborState, RandomSource random) {
            BlockState updated = super.updateShape(state, level, ticks, pos, direction, neighborPos,
                    neighborState, random);
            return withFrieze(updated, level, pos);
        }

        private BlockState withFrieze(BlockState state, LevelReader level, BlockPos pos) {
            BlockPos abovePos = pos.above();
            BlockPos belowPos = pos.below();
            int topPriority = friezePriority(level, abovePos, level.getBlockState(abovePos), Direction.DOWN);
            int bottomPriority = friezePriority(level, belowPos, level.getBlockState(belowPos), Direction.UP);
            if (topPriority > bottomPriority) {
                return state.setValue(TOP_FRIEZE, true).setValue(BOTTOM_FRIEZE, false);
            }
            if (bottomPriority > 0) {
                return state.setValue(TOP_FRIEZE, false).setValue(BOTTOM_FRIEZE, true);
            }
            return state.setValue(TOP_FRIEZE, false).setValue(BOTTOM_FRIEZE, false);
        }

        private int friezePriority(LevelReader level, BlockPos pos, BlockState other, Direction side) {
            Optional<Variant> otherVariant = variantForBlock(other.getBlock());
            if (otherVariant.isPresent()) {
                if (otherVariant.get().color().equals(color)) {
                    return other.isFaceSturdy(level, pos, side) ? 0 : 3;
                }
                return 1;
            }
            if (other.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty()) {
                return 5;
            }
            if (other.getBlock() instanceof IronBarsBlock) {
                return 2;
            }
            return 10;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(TOP_FRIEZE, BOTTOM_FRIEZE);
        }
    }
}
