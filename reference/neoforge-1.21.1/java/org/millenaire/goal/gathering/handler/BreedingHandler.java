package org.millenaire.goal.gathering.handler;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.gathering.GatheringTarget;
import org.millenaire.goal.gathering.GatheringType;
import org.millenaire.item.ItemHelper;
import org.slf4j.Logger;

public class BreedingHandler extends AbstractGatheringHandler {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int SCAN_RADIUS_XZ = 15;
   private static final int SCAN_RADIUS_Y = 10;
   private static final int MIN_ADULTS_FOR_BREEDING = 2;

   public String id() {
      return "breeding";
   }

   public Item getDefaultHeldItem(GatheringType type) {
      return Items.WHEAT;
   }

   public List<String> validate(GatheringType type) {
      List<String> errors = new ArrayList<>();
      if (this.getBuildingTag(type) == null) {
         errors.add("missing required handlerParam 'buildingTag'");
      }

      errors.addAll(validateItemIdList(type, "foodItems", true));
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
      if (buildingTag == null) {
         return false;
      }

      EntityType<?> animalType = this.getAnimalType(type);
      if (animalType == null) {
         return false;
      }

      List<Item> foodItems = this.getFoodItems(type);
      if (foodItems.isEmpty()) {
         return false;
      }

      List<BuildingInstance> farms = this.findBuildingsWithTag(ctx.village(), buildingTag);
      if (farms.isEmpty()) {
         return false;
      }

      ServerLevel level = ctx.level();

      for (BuildingInstance farm : farms) {
         if (this.hasFoodInBuilding(farm, level, foodItems) && this.canBreedAtFarm(level, farm, animalType, foodItems)) {
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

      EntityType<?> animalType = this.getAnimalType(type);
      if (animalType == null) {
         return null;
      }

      List<Item> foodItems = this.getFoodItems(type);
      if (foodItems.isEmpty()) {
         return null;
      }

      ServerLevel level = ctx.level();
      List<BuildingInstance> farms = this.findBuildingsWithTag(ctx.village(), buildingTag);
      BlockPos reference = lastTarget != null ? lastTarget.navigationPos() : ctx.villager().blockPosition();
      List<Animal> breedable = new ArrayList<>();

      for (BuildingInstance farm : farms) {
         if (this.hasFoodInBuilding(farm, level, foodItems) && this.canBreedAtFarm(level, farm, animalType, foodItems)) {
            breedable.addAll(this.findBreedableAnimals(level, farm, animalType, foodItems));
         }
      }

      Animal bestAnimal = findClosestEntity(breedable, reference, lastTarget, type.batchRadius());
      return bestAnimal != null ? new GatheringTarget.EntityTarget(bestAnimal) : null;
   }

   public boolean isTargetStillValid(GoalContext ctx, GatheringType type, GatheringTarget target) {
      if (target instanceof GatheringTarget.EntityTarget entityTarget) {
         return !(entityTarget.entity() instanceof Animal animal)
            ? false
            : animal.isAlive() && !animal.isBaby() && !animal.isInLove() && animal.canFallInLove();
      } else {
         return true;
      }
   }

   public boolean performAction(GoalContext ctx, GatheringType type, GatheringTarget target) {
      if (target instanceof GatheringTarget.EntityTarget entityTarget) {
         if (!(entityTarget.entity() instanceof Animal animal)) {
            return true;
         } else if (animal.isAlive() && !animal.isBaby() && !animal.isInLove() && animal.canFallInLove()) {
            List<Item> foodItems = this.getFoodItems(type);
            String buildingTag = this.getBuildingTag(type);
            Item usedFood = null;
            BuildingInstance foodSource = null;
            if (buildingTag != null) {
               for (BuildingInstance farm : this.findBuildingsWithTag(ctx.village(), buildingTag)) {
                  BuildingInventory inv = farm.getInventory();
                  if (inv != null) {
                     for (Item food : foodItems) {
                        if (inv.getCount(ctx.level(), food) >= 1) {
                           usedFood = food;
                           foodSource = farm;
                           break;
                        }
                     }

                     if (usedFood != null) {
                        break;
                     }
                  }
               }
            }

            if (usedFood != null && foodSource != null) {
               if (!animal.isFood(new ItemStack(usedFood))) {
                  return true;
               }

               foodSource.getInventory().remove(ctx.level(), usedFood, 1);
               animal.setInLove(null);
               LOGGER.debug("Breeding: animal {} put in love mode at {}", animal.getType().getDescriptionId(), animal.blockPosition());
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

   private boolean hasFoodInBuilding(BuildingInstance building, ServerLevel level, List<Item> foodItems) {
      BuildingInventory inv = building.getInventory();
      if (inv == null) {
         return false;
      }

      for (Item food : foodItems) {
         if (inv.getCount(level, food) >= 1) {
            return true;
         }
      }

      return false;
   }

   private List<Item> getFoodItems(GatheringType type) {
      JsonObject params = type.handlerParams();
      List<Item> items = new ArrayList<>();
      if (!params.has("foodItems")) {
         return items;
      }

      for (JsonElement elem : params.getAsJsonArray("foodItems")) {
         String itemId = elem.getAsString();
         Item item = ItemHelper.resolve(itemId);
         if (item != null && item != Items.AIR) {
            items.add(item);
         }
      }

      return items;
   }

   private List<Animal> findBreedableAnimals(ServerLevel level, BuildingInstance farm, EntityType<?> animalType, List<Item> foodItems) {
      AABB searchBox = scanBoxAround(farm.getOrigin(), 15, 10);
      List<Animal> result = new ArrayList<>();

      for (Animal animal : this.findAnimalsOfType(level, searchBox, animalType)) {
         if (animal.isAlive() && !animal.isBaby() && !animal.isInLove() && animal.canFallInLove()) {
            for (Item food : foodItems) {
               if (animal.isFood(new ItemStack(food))) {
                  result.add(animal);
                  break;
               }
            }
         }
      }

      return result;
   }

   private List<Animal> findAnimalsOfType(ServerLevel level, AABB box, EntityType<?> animalType) {
      List<Animal> result = new ArrayList<>();

      for (Entity e : level.getEntities((Entity)null, box, ex -> ex.getType() == animalType && ex instanceof Animal && ex.isAlive())) {
         result.add((Animal)e);
      }

      return result;
   }

   private boolean canBreedAtFarm(ServerLevel level, BuildingInstance farm, EntityType<?> animalType, List<Item> foodItems) {
      List<Animal> breedable = this.findBreedableAnimals(level, farm, animalType, foodItems);
      if (breedable.size() < 2) {
         return false;
      }

      AABB searchBox = scanBoxAround(farm.getOrigin(), 15, 10);
      int totalAnimals = this.findAnimalsOfType(level, searchBox, animalType).size();
      int spawnCount = countAnimalSpawnPoints(farm);
      return totalAnimals < spawnCount * 2;
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
}
