package org.millenaire.entity;

import com.mojang.logging.LogUtils;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.millenaire.Millenaire;
import org.millenaire.building.BuildingId;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.goal.GoalRegistry;
import org.millenaire.item.ItemHelper;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;
import org.slf4j.Logger;

public final class VillagerSpawnFactory {
   private static final Logger LOGGER = LogUtils.getLogger();

   private VillagerSpawnFactory() {
   }

   @Nullable
   public static MillVillager spawnInVillage(
      ServerLevel level, Village village, ResourceLocation villagerTypeId, BlockPos spawnPos, @Nullable BuildingId homeBuilding
   ) {
      return spawnInVillage(level, village, villagerTypeId, spawnPos, homeBuilding, true);
   }

   @Nullable
   public static MillVillager spawnInVillage(
      ServerLevel level, Village village, ResourceLocation villagerTypeId, BlockPos spawnPos, @Nullable BuildingId homeBuilding, boolean applyInitialInventory
   ) {
      return spawnInVillage(level, village, villagerTypeId, spawnPos, homeBuilding, applyInitialInventory, null);
   }

   @Nullable
   public static MillVillager spawnInVillage(
      ServerLevel level,
      Village village,
      ResourceLocation villagerTypeId,
      BlockPos spawnPos,
      @Nullable BuildingId homeBuilding,
      @Nullable VillagerRecord existingRecord
   ) {
      return spawnInVillage(level, village, villagerTypeId, spawnPos, homeBuilding, existingRecord == null, existingRecord);
   }

   @Nullable
   private static MillVillager spawnInVillage(
      ServerLevel level,
      Village village,
      ResourceLocation villagerTypeId,
      BlockPos spawnPos,
      @Nullable BuildingId homeBuilding,
      boolean applyInitialInventory,
      @Nullable VillagerRecord existingRecord
   ) {
      VillagerType vType = ModCultures.getVillagerType(villagerTypeId);
      if (vType == null) {
         LOGGER.warn("[Millénaire] Villager type not found: {}", villagerTypeId);
         return null;
      }

      MillVillager villager = (MillVillager)((EntityType)ModEntities.MILL_VILLAGER.get()).create(level);
      if (villager == null) {
         return null;
      }

      villager.setVillageId(village.getId());
      villager.setVillagerTypeId(villagerTypeId);
      villager.setHomeBuilding(homeBuilding);
      if (vType.maxHealth() != 20.0F) {
         AttributeInstance healthAttr = villager.getAttribute(Attributes.MAX_HEALTH);
         if (healthAttr != null) {
            healthAttr.setBaseValue(vType.maxHealth());
            villager.setHealth(vType.maxHealth());
         }
      }

      BlockPos safePos = findSafeSpawnPos(level, spawnPos);
      villager.moveTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5, 0.0F, 0.0F);
      GoalRegistry registry = Millenaire.getGoalRegistry();
      if (registry != null) {
         villager.initGoals(registry, vType);
      }

      if (existingRecord != null) {
         existingRecord.applyToEntity(villager);
      } else {
         VillagerAppearanceFactory.randomizeAppearance(villager, vType);
         if (applyInitialInventory) {
            for (Entry<ResourceLocation, Integer> entry : vType.initialInventory().entrySet()) {
               Item item = ItemHelper.resolve(entry.getKey());
               if (item != null) {
                  villager.getInventory().add(item, entry.getValue());
               }
            }
         }
      }

      level.addFreshEntity(villager);
      if (existingRecord != null) {
         existingRecord.setUuid(villager.getUUID());
         existingRecord.setKilled(false);
         village.addVillager(existingRecord);
      } else {
         village.addVillager(villager.getUUID(), villagerTypeId, homeBuilding);
         VillagerRecord newRecord = village.getVillagerRecord(villager.getUUID());
         if (newRecord != null) {
            newRecord.updateFromEntity(villager);
         }
      }

      return villager;
   }

   private static BlockPos findSafeSpawnPos(ServerLevel level, BlockPos pos) {
      for (int nudge = 1; nudge <= 5; nudge++) {
         BlockPos candidate = pos.above(nudge);
         if (!level.getBlockState(candidate).isSuffocating(level, candidate) && !level.getBlockState(candidate.above()).isSuffocating(level, candidate.above())
            )
          {
            return candidate;
         }
      }

      int surfaceY = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
      LOGGER.warn("[Millenaire] findSafeSpawnPos — all nudges failed at {}, falling back to surface Y={}", pos.toShortString(), surfaceY);
      return new BlockPos(pos.getX(), surfaceY, pos.getZ());
   }
}
