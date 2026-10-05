package org.millenaire.fabric.paint;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

import java.util.Locale;
import java.util.Optional;

public final class PaintBucketItem extends Item {
    private final String color;

    public PaintBucketItem(String color, Properties properties) {
        super(properties);
        this.color = PaintableBlocks.normalizeColor(color);
        if (!PaintableBlocks.parseBlockName("painted_brick_" + this.color).isPresent()) {
            throw new IllegalArgumentException("Unsupported paint-bucket color: " + color);
        }
    }

    /** Creates a behavior-backed bucket from either supported legacy item ID spelling. */
    public static PaintBucketItem createForName(String name, Properties properties) {
        String color = colorFromName(name)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported paint-bucket item ID: " + name));
        return new PaintBucketItem(color, properties);
    }

    static Optional<String> colorFromName(String name) {
        String path = name;
        int namespaceSeparator = path.indexOf(':');
        if (namespaceSeparator >= 0) {
            path = path.substring(namespaceSeparator + 1);
        }
        path = path.toLowerCase(Locale.ROOT);
        if (path.equals("paintbucketwhite")) {
            return Optional.of("white");
        }
        if (path.startsWith("paint_bucket_")) {
            String color = path.substring("paint_bucket_".length());
            if (PaintableBlocks.parseBlockName("painted_brick_" + color).isPresent()) {
                return Optional.of(color);
            }
        }
        return Optional.empty();
    }

    public String color() {
        return color;
    }

    /** Returns the current vanilla dye name corresponding to this legacy bucket color. */
    public String vanillaDyeName() {
        return PaintableBlocks.vanillaDyeName(color);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos clickedPos = context.getClickedPos();
        var clickedVariant = PaintableBlocks.variantForBlock(
                context.getLevel().getBlockState(clickedPos).getBlock());
        if (clickedVariant.isEmpty() || clickedVariant.get().color().equals(color)) {
            return InteractionResult.PASS;
        }

        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (context.getPlayer() == null || !(context.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        var result = PaintableBlocks.recolor(context.getItemInHand(), color, serverLevel, clickedPos,
                context.getPlayer(), context.getHand());
        if (result.consumesAction() && context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)
            org.millenaire.fabric.village.VillageEtiquette.painted(player, color);
        return result;
    }
}
