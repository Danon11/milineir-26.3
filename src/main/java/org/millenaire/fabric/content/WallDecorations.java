package org.millenaire.fabric.content;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.painting.Painting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The original's wall decorations — Norman tapestries, Indian and Mayan statues, Byzantine icons — hang on walls
 * as paintings: each piece is a painting variant ({@code data/millenaire/painting_variant}) cut from the old
 * atlases, and using the item on a wall hangs a random piece of its kind that fits there.
 */
public final class WallDecorations {
    private static final Set<String> KINDS = Set.of("tapestry", "indianstatue", "mayanstatue", "byzantineiconsmall",
            "byzantineiconmedium", "byzantineiconlarge");

    private WallDecorations() {}

    public static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            var stack = player.getItemInHand(hand);
            var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!id.getNamespace().equals("millenaire") || !KINDS.contains(id.getPath())) return InteractionResult.PASS;
            Direction face = hit.getDirection();
            if (face.getAxis().isVertical()) return InteractionResult.PASS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            var pos = hit.getBlockPos().relative(face);
            var variants = level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT);
            List<net.minecraft.core.Holder<net.minecraft.world.entity.decoration.painting.PaintingVariant>> kind = new ArrayList<>();
            variants.listElements().forEach(holder -> {
                var key = holder.key().identifier();
                if (key.getNamespace().equals("millenaire") && key.getPath().startsWith(id.getPath() + "_")) kind.add(holder);
            });
            Collections.shuffle(kind);
            // Largest pieces that fit first, like vanilla paintings.
            kind.sort((a, b) -> Integer.compare(b.value().area(), a.value().area()));
            for (var variant : kind) {
                var painting = new Painting(level, pos, face, variant);
                if (!painting.survives()) continue;
                level.addFreshEntity(painting);
                painting.playPlacementSound();
                if (!player.getAbilities().instabuild) stack.shrink(1);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.FAIL;
        });
    }
}
