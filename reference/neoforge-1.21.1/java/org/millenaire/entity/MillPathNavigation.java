package org.millenaire.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;

public class MillPathNavigation extends GroundPathNavigation {
   private static final int NODE_BUDGET_MULTIPLIER = 2;

   public MillPathNavigation(Mob mob, Level level) {
      super(mob, level);
      this.setCanOpenDoors(true);
      this.setCanWalkOverFences(false);
      this.setCanFloat(true);
   }

   protected PathFinder createPathFinder(int maxVisitedNodes) {
      this.nodeEvaluator = new MillWalkNodeEvaluator(this.mob);
      this.nodeEvaluator.setCanPassDoors(true);
      this.nodeEvaluator.setCanOpenDoors(true);
      return new PathFinder(this.nodeEvaluator, maxVisitedNodes * 2);
   }
}
