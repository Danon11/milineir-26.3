package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.millenaire.block.AppleTreeSaplingBlock;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.SpecialPoint;
import org.slf4j.Logger;

public final class VillageEnvironmentHelper {
   private static final Logger LOGGER = LogUtils.getLogger();

   private VillageEnvironmentHelper() {
   }

   public static void despawnDangerousMobs(ServerLevel level, AABB dangerousMobsArea) {
      for (Monster mob : level.getEntitiesOfClass(Monster.class, dangerousMobsArea, e -> e instanceof Creeper || e instanceof EnderMan)) {
         mob.discard();
      }
   }

   public static void updatePens(ServerLevel level, Village village, boolean completeRespawn) {
      boolean isDay = level.isDay();
      if (!isDay || completeRespawn) {
         for (BuildingInstance building : village.getBuildings()) {
            if (building.isOperational()) {
               List<SpecialPoint> animalPoints = building.getPointsByType("animalSpawn");
               if (!animalPoints.isEmpty()) {
                  long lastSpawn = building.getLastAnimalSpawnTick();
                  if (completeRespawn || lastSpawn <= 0L || level.getGameTime() - lastSpawn >= 12000L) {
                     Map<String, List<BlockPos>> pointsByAnimal = new HashMap<>();

                     for (SpecialPoint sp : animalPoints) {
                        String animalType = sp.subtype() != null ? sp.subtype() : "cow";
                        pointsByAnimal.computeIfAbsent(animalType, k -> new ArrayList<>()).add(sp.pos());
                     }

                     boolean spawned = false;

                     for (Entry<String, List<BlockPos>> entry : pointsByAnimal.entrySet()) {
                        String animalType = entry.getKey();
                        List<BlockPos> spawnPositions = entry.getValue();
                        int target = Math.min(spawnPositions.size(), 8);
                        EntityType<?> entityType = resolveAnimalType(animalType);
                        if (entityType != null) {
                           BlockPos origin = building.getOrigin();
                           AABB area = new AABB(
                              origin.getX() - 15, origin.getY() - 20, origin.getZ() - 15, origin.getX() + 15, origin.getY() + 20, origin.getZ() + 15
                           );
                           int existing = level.getEntitiesOfClass(Animal.class, area, e -> e.getType() == entityType && !e.isBaby()).size();
                           if (existing < target) {
                              for (int i = existing; i < target; i++) {
                                 if (completeRespawn || level.random.nextInt(10) == 0) {
                                    BlockPos spawnPos = spawnPositions.get(level.random.nextInt(spawnPositions.size()));
                                    Entity animal = entityType.create(level);
                                    if (animal != null) {
                                       animal.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
                                       level.addFreshEntity(animal);
                                       spawned = true;
                                    }
                                 }
                              }
                           }
                        }
                     }

                     if (spawned) {
                        building.setLastAnimalSpawnTick(level.getGameTime());
                     }
                  }
               }
            }
         }
      }
   }

   @Nullable
   private static EntityType<?> resolveAnimalType(String animalType) {
      return (EntityType<?>)EntityType.byString("minecraft:" + animalType).orElse(null);
   }

   public static void tickGroveSaplings(ServerLevel level, Village village) {
      for (BuildingInstance building : village.getOperationalBuildingsWithTag("grove")) {
         for (SpecialPoint sp : building.getResolvedPoints()) {
            if (sp.isType("treeSpawn") && level.random.nextInt(200) == 0) {
               BlockPos saplingPos = sp.pos();
               if (level.isLoaded(saplingPos)) {
                  BlockState state = level.getBlockState(saplingPos);
                  if (state.getBlock() instanceof SaplingBlock saplingBlock) {
                     saplingBlock.advanceTree(level, saplingPos, state, level.random);
                  } else if (state.getBlock() instanceof AppleTreeSaplingBlock appleTreeSapling) {
                     appleTreeSapling.advanceTree(level, saplingPos, state, level.random);
                  }
               }
            }
         }
      }
   }
}
