package org.millenaire.goal.gathering.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import org.millenaire.building.BuildingInstance;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.gathering.GatheringTarget;
import org.millenaire.goal.gathering.GatheringType;
import org.millenaire.item.ItemHelper;
import org.slf4j.Logger;

public class SlaughterHandler extends AbstractGatheringHandler {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int SCAN_RADIUS_XZ = 25;
   private static final int SCAN_RADIUS_Y = 10;
   private static final int MIN_ANIMALS_KEEP = 2;

   public String id() {
      return "slaughter";
   }

   public String getHeldToolCategoryId(GatheringType type) {
      return "toolsaxe";
   }

   public List<String> validate(GatheringType type) {
      List<String> errors = new ArrayList<>();
      if (this.getBuildingTag(type) == null) {
         errors.add("missing required handlerParam 'buildingTag'");
      }

      String animalId = GsonHelper.getAsString(type.handlerParams(), "animalType", null);
      if (animalId == null) {
         errors.add("missing required handlerParam 'animalType'");
      } else if (EntityType.byString(animalId).isEmpty()) {
         errors.add("unresolvable entity type '" + animalId + "' in 'animalType'");
      }

      return errors;
   }

   public boolean canStart(GoalContext ctx, GatheringType type) {
      String buildingTag = this.getBuildingTag(type);
      EntityType<?> animalType = this.getAnimalType(type);
      if (buildingTag != null && animalType != null) {
         List<BuildingInstance> farms = this.findBuildingsWithTag(ctx.village(), buildingTag);
         ServerLevel level = ctx.level();

         for (BuildingInstance farm : farms) {
            List<Mob> adults = this.findAdultMobs(level, farm, animalType);
            int animalSpawnCount = countAnimalSpawnPoints(farm);
            if (adults.size() > Math.max(animalSpawnCount, 2)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   @Nullable
   public GatheringTarget findTarget(GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget) {
      String buildingTag = this.getBuildingTag(type);
      EntityType<?> animalType = this.getAnimalType(type);
      if (buildingTag != null && animalType != null) {
         ServerLevel level = ctx.level();
         List<BuildingInstance> farms = this.findBuildingsWithTag(ctx.village(), buildingTag);
         BlockPos reference = lastTarget != null ? lastTarget.navigationPos() : ctx.villager().blockPosition();
         List<Mob> slaughterable = new ArrayList<>();

         for (BuildingInstance farm : farms) {
            List<Mob> adults = this.findAdultMobs(level, farm, animalType);
            int animalSpawnCount = countAnimalSpawnPoints(farm);
            if (adults.size() > Math.max(animalSpawnCount, 2)) {
               slaughterable.addAll(adults);
            }
         }

         Mob bestMob = findClosestEntity(slaughterable, reference, lastTarget, type.batchRadius());
         return bestMob != null ? new GatheringTarget.EntityTarget(bestMob) : null;
      } else {
         return null;
      }
   }

   public boolean performAction(GoalContext ctx, GatheringType type, GatheringTarget target) {
      if (target instanceof GatheringTarget.EntityTarget entityTarget) {
         if (entityTarget.entity() instanceof Mob mob) {
            if (!mob.isAlive()) {
               this.pickupNearbyItems(ctx, mob.blockPosition());
               return true;
            } else {
               float damage = this.getDamage(type);
               DamageSource source = ctx.level().damageSources().mobAttack(ctx.villager());
               mob.hurt(source, damage);
               if (!mob.isAlive()) {
                  this.pickupNearbyItems(ctx, mob.blockPosition());
                  this.giveBonusItems(ctx, type);
                  return true;
               } else {
                  return false;
               }
            }
         } else {
            return true;
         }
      } else {
         return true;
      }
   }

   private void pickupNearbyItems(GoalContext ctx, BlockPos pos) {
      AABB pickupBox = new AABB(pos.getX() - 3.0, pos.getY() - 2.0, pos.getZ() - 3.0, pos.getX() + 3.0, pos.getY() + 2.0, pos.getZ() + 3.0);

      for (ItemEntity itemEntity : ctx.level().getEntitiesOfClass(ItemEntity.class, pickupBox)) {
         if (itemEntity.isAlive()) {
            ctx.villager().getInventory().add(itemEntity.getItem().getItem(), itemEntity.getItem().getCount());
            itemEntity.discard();
         }
      }
   }

   private void giveBonusItems(GoalContext ctx, GatheringType type) {
      JsonArray bonusArray = null;
      if (type.handlerParams().has("bonusItems")) {
         bonusArray = GsonHelper.getAsJsonArray(type.handlerParams(), "bonusItems");
      }

      if (bonusArray != null) {
         for (JsonElement element : bonusArray) {
            JsonObject bonus = element.getAsJsonObject();
            String itemId = GsonHelper.getAsString(bonus, "item");
            int count = GsonHelper.getAsInt(bonus, "count", 1);
            int chance = GsonHelper.getAsInt(bonus, "chance", 100);
            if (chance >= 100 || ctx.level().random.nextInt(100) < chance) {
               Item item = ItemHelper.resolve(itemId);
               if (item != null) {
                  ctx.villager().getInventory().add(item, count);
               } else {
                  LOGGER.warn("Bonus item not found: {}", itemId);
               }
            }
         }
      }
   }

   @Nullable
   private EntityType<?> getAnimalType(GatheringType type) {
      String animalId = GsonHelper.getAsString(type.handlerParams(), "animalType", null);
      if (animalId == null) {
         return null;
      }

      Optional<EntityType<?>> opt = EntityType.byString(animalId);
      return opt.orElse(null);
   }

   private float getDamage(GatheringType type) {
      return GsonHelper.getAsFloat(type.handlerParams(), "damage", 4.0F);
   }

   private List<Mob> findAdultMobs(ServerLevel level, BuildingInstance farm, EntityType<?> animalType) {
      AABB searchBox = scanBoxAround(farm.getOrigin(), 25, 10);
      List<Mob> result = new ArrayList<>();

      for (Entity entity : level.getEntities((Entity)null, searchBox, e -> e.getType() == animalType && e instanceof Mob)) {
         Mob mob = (Mob)entity;
         if (mob.isAlive() && !mob.isBaby()) {
            result.add(mob);
         }
      }

      return result;
   }
}
