package org.millenaire.goal.gathering.handler;

import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.gathering.GatheringTarget;
import org.millenaire.goal.gathering.GatheringType;
import org.slf4j.Logger;

public class FishingInuitHandler extends FishingHandler {
   private static final Logger LOGGER = LogUtils.getLogger();

   public String id() {
      return "fishing_inuit";
   }

   public boolean performAction(GoalContext ctx, GatheringType type, GatheringTarget target) {
      Map<Item, Integer> loot = this.parseItemList(type.handlerParams(), "loot");
      if (!loot.isEmpty()) {
         for (Entry<Item, Integer> entry : loot.entrySet()) {
            ctx.villager().getInventory().add(entry.getKey(), entry.getValue());
         }
      } else {
         ctx.villager().getInventory().add(Items.COD, 1);
      }

      if (ctx.villager().getRandom().nextInt(4) == 0) {
         ctx.villager().getInventory().add(Blocks.BONE_BLOCK.asItem(), 1);
      }

      LOGGER.debug("FishingInuit: fish + bone chance at {}", target.navigationPos());
      return true;
   }
}
