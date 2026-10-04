package org.millenaire.goal.gathering.handler;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.millenaire.building.BuildingInstance;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.gathering.GatheringTarget;
import org.millenaire.goal.gathering.GatheringType;
import org.slf4j.Logger;

public class ShearingHandler extends AbstractGatheringHandler {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int SCAN_RADIUS_XZ = 30;
   private static final int SCAN_RADIUS_Y = 10;
   private static final int WOOL_PER_SHEAR = 3;

   public String id() {
      return "shearing";
   }

   public Item getDefaultHeldItem(GatheringType type) {
      return Items.SHEARS;
   }

   public List<String> validate(GatheringType type) {
      return this.getBuildingTag(type) == null ? List.of("missing required handlerParam 'buildingTag'") : List.of();
   }

   public boolean canStart(GoalContext ctx, GatheringType type) {
      String buildingTag = this.getBuildingTag(type);
      if (buildingTag == null) {
         return false;
      }

      List<BuildingInstance> farms = this.findBuildingsWithTag(ctx.village(), buildingTag);
      if (farms.isEmpty()) {
         return false;
      }

      ServerLevel level = ctx.level();

      for (BuildingInstance farm : farms) {
         List<Sheep> shearable = this.findShearableSheep(level, farm);
         if (!shearable.isEmpty()) {
            return true;
         }
      }

      return false;
   }

   @Nullable
   public GatheringTarget findTarget(GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget) {
      String buildingTag = this.getBuildingTag(type);
      if (buildingTag == null) {
         return null;
      }

      ServerLevel level = ctx.level();
      List<BuildingInstance> farms = this.findBuildingsWithTag(ctx.village(), buildingTag);
      BlockPos reference = lastTarget != null ? lastTarget.navigationPos() : ctx.villager().blockPosition();
      List<Sheep> allShearable = new ArrayList<>();

      for (BuildingInstance farm : farms) {
         allShearable.addAll(this.findShearableSheep(level, farm));
      }

      Sheep bestSheep = findClosestEntity(allShearable, reference, lastTarget, type.batchRadius());
      return bestSheep != null ? new GatheringTarget.EntityTarget(bestSheep) : null;
   }

   public boolean isTargetStillValid(GoalContext ctx, GatheringType type, GatheringTarget target) {
      if (target instanceof GatheringTarget.EntityTarget entityTarget) {
         return !(entityTarget.entity() instanceof Sheep sheep) ? false : sheep.isAlive() && !sheep.isBaby() && !sheep.isSheared();
      } else {
         return true;
      }
   }

   public boolean performAction(GoalContext ctx, GatheringType type, GatheringTarget target) {
      if (target instanceof GatheringTarget.EntityTarget entityTarget) {
         if (entityTarget.entity() instanceof Sheep sheep) {
            if (sheep.isAlive() && !sheep.isBaby() && !sheep.isSheared()) {
               sheep.setSheared(true);
               Item woolItem = this.getWoolForColor(sheep.getColor());
               ctx.villager().getInventory().add(woolItem, 3);
               LOGGER.debug("Shearing: sheep sheared at {}, {} wool {}", new Object[]{sheep.blockPosition(), 3, woolItem});
               return true;
            } else {
               return true;
            }
         } else {
            return true;
         }
      } else {
         return true;
      }
   }

   private List<Sheep> findShearableSheep(ServerLevel level, BuildingInstance farm) {
      AABB searchBox = scanBoxAround(farm.getOrigin(), 30, 10);
      List<Sheep> result = new ArrayList<>();

      for (Sheep s : level.getEntitiesOfClass(Sheep.class, searchBox)) {
         if (s.isAlive() && !s.isBaby() && !s.isSheared()) {
            result.add(s);
         }
      }

      return result;
   }

   private Item getWoolForColor(DyeColor color) {
      return switch (color) {
         case WHITE -> Items.WHITE_WOOL;
         case ORANGE -> Items.ORANGE_WOOL;
         case MAGENTA -> Items.MAGENTA_WOOL;
         case LIGHT_BLUE -> Items.LIGHT_BLUE_WOOL;
         case YELLOW -> Items.YELLOW_WOOL;
         case LIME -> Items.LIME_WOOL;
         case PINK -> Items.PINK_WOOL;
         case GRAY -> Items.GRAY_WOOL;
         case LIGHT_GRAY -> Items.LIGHT_GRAY_WOOL;
         case CYAN -> Items.CYAN_WOOL;
         case PURPLE -> Items.PURPLE_WOOL;
         case BLUE -> Items.BLUE_WOOL;
         case BROWN -> Items.BROWN_WOOL;
         case GREEN -> Items.GREEN_WOOL;
         case RED -> Items.RED_WOOL;
         case BLACK -> Items.BLACK_WOOL;
         default -> throw new MatchException(null, null);
      };
   }
}
