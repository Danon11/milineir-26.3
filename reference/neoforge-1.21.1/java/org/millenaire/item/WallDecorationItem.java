package org.millenaire.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.millenaire.entity.MillWallDecoration;
import org.millenaire.entity.WallDecorationType;

public class WallDecorationItem extends Item {
   private final WallDecorationType decorationType;

   public WallDecorationItem(Properties properties, WallDecorationType decorationType) {
      super(properties);
      this.decorationType = decorationType;
   }

   public WallDecorationType getDecorationType() {
      return this.decorationType;
   }

   public InteractionResult useOn(UseOnContext context) {
      Direction clickedFace = context.getClickedFace();
      if (clickedFace != Direction.DOWN && clickedFace != Direction.UP) {
         Level level = context.getLevel();
         BlockPos hangingPos = context.getClickedPos().relative(clickedFace);
         if (context.getPlayer() != null && !context.getPlayer().mayUseItemAt(hangingPos, clickedFace, context.getItemInHand())) {
            return InteractionResult.FAIL;
         }

         MillWallDecoration decoration = MillWallDecoration.createForPlayer(level, hangingPos, clickedFace, this.decorationType);
         if (decoration == null) {
            return InteractionResult.FAIL;
         }

         if (!level.isClientSide) {
            decoration.playSound(SoundEvents.PAINTING_PLACE, 1.0F, 1.0F);
            level.addFreshEntity(decoration);
         }

         context.getItemInHand().shrink(1);
         return InteractionResult.SUCCESS;
      } else {
         return InteractionResult.FAIL;
      }
   }
}
