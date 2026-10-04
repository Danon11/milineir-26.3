package org.millenaire.world;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.neoforged.neoforge.common.Tags.Biomes;
import org.jetbrains.annotations.Nullable;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.ClearMargins;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.NameLists;
import org.millenaire.culture.VillageType;
import org.millenaire.culture.WallType;
import org.millenaire.village.BrickColourTheme;
import org.millenaire.village.BuildingFinalizer;
import org.millenaire.village.SubBuildingHelper;
import org.millenaire.village.Village;
import org.millenaire.village.VillageBookService;
import org.millenaire.village.VillageChunkLoader;
import org.millenaire.village.VillageEventType;
import org.millenaire.village.VillageGrowthManager;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageReputation;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

public final class VillageSpawner {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Random RANDOM = new Random();
   private static final int HAMLET_ATTEMPT_ANGLE_STEPS = 36;
   private static final int HAMLET_MIN_DISTANCE = 250;
   private static final int HAMLET_MAX_DISTANCE = 350;
   private static final int HAMLET_RADIUS_STEP = 50;

   private VillageSpawner() {
   }

   @Nullable
   public static Component spawnVillage(ServerLevel level, BlockPos center, VillageType villageType) {
      return spawnVillage(level, center, villageType, 0, null, null, null);
   }

   @Nullable
   public static Component spawnVillage(ServerLevel level, BlockPos center, VillageType villageType, int completion) {
      return spawnVillage(level, center, villageType, completion, null, null, null);
   }

   @Nullable
   public static Component spawnVillage(
      ServerLevel level,
      BlockPos center,
      VillageType villageType,
      int completion,
      @javax.annotation.Nullable String parentBaseName,
      @javax.annotation.Nullable VillageId parentVillageId
   ) {
      return spawnVillage(level, center, villageType, completion, parentBaseName, parentVillageId, null);
   }

   @Nullable
   public static Component validateSite(ServerLevel level, BlockPos center, VillageType villageType) {
      VillageSpawner.ValidationResult result = dryRunPlacement(level, center, villageType);
      return result.error;
   }

   private static VillageSpawner.ValidationResult dryRunPlacement(ServerLevel level, BlockPos center, VillageType villageType) {
      ResourceLocation villageTypeId = villageType.id();
      List<VillageSpawner.SlotWithPlan> resolvedSlots = resolveSlots(villageType);
      if (resolvedSlots.isEmpty()) {
         LOGGER.error("[Millenaire] No valid slots in village type {}", villageTypeId);
         return VillageSpawner.ValidationResult.fail(Component.translatable("millenaire.spawn.error.no_valid_slots", new Object[]{villageTypeId.getPath()}));
      }

      VillageSpawner.SlotWithPlan centreSlot = null;
      List<VillageSpawner.SlotWithPlan> otherSlots = new ArrayList<>();

      for (VillageSpawner.SlotWithPlan s : resolvedSlots) {
         if (isCentreRole(s.slot.role())) {
            centreSlot = s;
         } else {
            otherSlots.add(s);
         }
      }

      if (centreSlot == null) {
         LOGGER.error("[Millenaire] No center slot in village type {}", villageTypeId);
         return VillageSpawner.ValidationResult.fail(Component.translatable("millenaire.spawn.error.no_centre", new Object[]{villageTypeId.getPath()}));
      }

      List<VillageSpawner.SlotWithPlan> slotsToPlace = new ArrayList<>(otherSlots.stream().filter(s -> isStartRole(s.slot.role())).toList());
      Collections.shuffle(slotsToPlace, RANDOM);
      BlockPos townhallRequestedOrigin = center;
      Rotation townhallRotation = Rotation.NONE;
      if (centreSlot.slot.hasLegacyOffset()) {
         townhallRequestedOrigin = center.offset(centreSlot.slot.offset());
         townhallRotation = centreSlot.slot.rotation();
      }

      ClearMargins centreClearMargins = ClearMargins.symmetric(PlacementConstraints.getDefaultClearMargin());
      if (centreSlot.planSetId != null) {
         BuildingPlanSet centrePlanSetForMargins = ModCultures.getBuildingPlanSet(centreSlot.planSetId);
         if (centrePlanSetForMargins != null) {
            centreClearMargins = centrePlanSetForMargins.clearMargins();
         }
      }

      int effWidth = TerrainPreparer.effectiveWidth(centreSlot.plan.width(), centreSlot.plan.depth(), townhallRotation);
      int effDepth = TerrainPreparer.effectiveDepth(centreSlot.plan.width(), centreSlot.plan.depth(), townhallRotation);
      int townhallBaseY = TerrainPreparer.computeAverageSurfaceHeight(
         level, townhallRequestedOrigin, effWidth, effDepth, centreClearMargins.forRotation(townhallRotation)
      );
      VillageTerrainMap.FootprintRect thRect = VillageTerrainMap.computeFootprintRect(
         townhallRequestedOrigin.getX(),
         townhallRequestedOrigin.getZ(),
         centreSlot.plan.width(),
         centreSlot.plan.depth(),
         ClearMargins.symmetric(0),
         townhallRotation
      );
      int thCenterX = thRect.startX() + thRect.width() / 2;
      int thCenterZ = thRect.startZ() + thRect.depth() / 2;
      BlockPos centerAtGround = new BlockPos(thCenterX, townhallBaseY, thCenterZ);
      VillageTerrainMap terrainMap = VillageTerrainMap.compute(level, centerAtGround, villageType.radius());
      terrainMap.markBuildingFootprint(
         townhallRequestedOrigin.getX(),
         townhallRequestedOrigin.getZ(),
         centreSlot.plan.width(),
         centreSlot.plan.depth(),
         centreClearMargins,
         townhallRotation,
         townhallBaseY
      );
      TerrainReachability reachability = TerrainReachability.compute(terrainMap, centerAtGround);
      List<VillageSpawner.PrecomputedWalls> precomputedWalls = new ArrayList<>();
      if (!villageType.playerControlled()) {
         VillageWallGenerator wallGenerator = new VillageWallGenerator(level);
         ResourceLocation innerWallTypeId = villageType.innerWallType();
         if (innerWallTypeId != null) {
            WallType innerWall = ModCultures.getWallType(innerWallTypeId);
            if (innerWall != null) {
               VillageWallGenerator.WallLocationResult innerResult = wallGenerator.computeWallBuildingLocations(
                  villageType, innerWall, villageType.innerWallRadius(), terrainMap, reachability, centerAtGround
               );
               if (!innerResult.segments().isEmpty()) {
                  precomputedWalls.add(new VillageSpawner.PrecomputedWalls(innerWall, innerResult));
                  markWallFootprints(terrainMap, innerWall, innerResult);
                  reachability = TerrainReachability.compute(terrainMap, centerAtGround);
               }
            }
         }

         ResourceLocation outerWallTypeId = villageType.outerWallType();
         if (outerWallTypeId != null) {
            WallType outerWall = ModCultures.getWallType(outerWallTypeId);
            if (outerWall != null) {
               VillageWallGenerator.WallLocationResult outerResult = wallGenerator.computeWallBuildingLocations(
                  villageType, outerWall, 0, terrainMap, reachability, centerAtGround
               );
               if (!outerResult.segments().isEmpty()) {
                  precomputedWalls.add(new VillageSpawner.PrecomputedWalls(outerWall, outerResult));
                  markWallFootprints(terrainMap, outerWall, outerResult);
                  reachability = TerrainReachability.compute(terrainMap, centerAtGround);
               }
            }
         }
      }

      List<VillageSpawner.PlacedBuilding> placedBuildings = new ArrayList<>();
      List<BuildingInstance> existingBuildings = new ArrayList<>();
      int townhallPlacementY = townhallBaseY + centreSlot.plan.groundLevel();
      existingBuildings.add(
         new BuildingInstance(
            BuildingId.random(),
            centreSlot.plan.id(),
            new BlockPos(townhallRequestedOrigin.getX(), townhallPlacementY, townhallRequestedOrigin.getZ()),
            townhallRotation,
            BuildingInstance.Status.PLANNED
         )
      );

      for (VillageSpawner.SlotWithPlan swp : slotsToPlace) {
         BuildingPlan plan = swp.plan;
         VillageType.LayoutSlot slot = swp.slot;
         BlockPos requestedOrigin;
         Rotation rotation;
         if (slot.hasLegacyOffset()) {
            requestedOrigin = center.offset(slot.offset());
            rotation = slot.rotation();
         } else {
            BuildingPlanSet spawnPlanSet = swp.planSetId != null ? ModCultures.getBuildingPlanSet(swp.planSetId) : null;
            PlacementConstraints constraints = spawnPlanSet != null
               ? PlacementConstraints.resolve(spawnPlanSet, slot, villageType.radius())
               : new PlacementConstraints(5, 60, slot.farFromTags(), slot.closeToTags(), slot.clearMargin(), slot.fixedOrientation());
            ClearMargins searchMargins = ClearMargins.symmetric(PlacementConstraints.getDefaultClearMargin());
            if (spawnPlanSet != null) {
               searchMargins = spawnPlanSet.clearMargins().atLeast(slot.clearMargin());
            }

            PlacedLocation location = BuildingLocationFinder.findLocation(
               terrainMap, plan, centerAtGround, constraints, searchMargins, existingBuildings, reachability
            );
            if (location == null) {
               if (isStartRole(slot.role())) {
                  LOGGER.warn("[Millenaire] No location found for start building '{}' — village rejected", swp.planSetId);
                  return VillageSpawner.ValidationResult.fail(
                     Component.translatable("millenaire.spawn.error.no_terrain", new Object[]{String.valueOf(swp.planSetId)})
                  );
               }

               LOGGER.warn("[Millenaire] No location found for '{}', skipped", swp.planSetId);
               continue;
            }

            requestedOrigin = location.position();
            rotation = location.rotation();
         }

         ClearMargins slotMargins = ClearMargins.symmetric(PlacementConstraints.getDefaultClearMargin());
         if (swp.planSetId != null) {
            BuildingPlanSet slotPlanSet = ModCultures.getBuildingPlanSet(swp.planSetId);
            if (slotPlanSet != null) {
               slotMargins = slotPlanSet.clearMargins().atLeast(slot.clearMargin());
            }
         }

         int baseY = terrainMap.computeAverageAltitude(requestedOrigin.getX(), requestedOrigin.getZ(), plan.width(), plan.depth(), slotMargins, rotation);
         int placementY = baseY + plan.groundLevel();
         BlockPos adjustedOrigin = new BlockPos(requestedOrigin.getX(), placementY, requestedOrigin.getZ());
         terrainMap.markBuildingFootprint(requestedOrigin.getX(), requestedOrigin.getZ(), plan.width(), plan.depth(), slotMargins, rotation, baseY);
         reachability = TerrainReachability.compute(terrainMap, centerAtGround);
         BuildingInstance tempInstance = new BuildingInstance(BuildingId.random(), plan.id(), adjustedOrigin, rotation, BuildingInstance.Status.PLANNED);
         existingBuildings.add(tempInstance);
         placedBuildings.add(new VillageSpawner.PlacedBuilding(swp, adjustedOrigin, rotation, requestedOrigin, baseY));
      }

      return new VillageSpawner.ValidationResult(
         null, centreSlot, placedBuildings, townhallRequestedOrigin, townhallRotation, centreClearMargins, townhallBaseY, centerAtGround, precomputedWalls
      );
   }

   @Nullable
   public static Component spawnVillage(
      ServerLevel level,
      BlockPos center,
      VillageType villageType,
      int completion,
      @javax.annotation.Nullable String parentBaseName,
      @javax.annotation.Nullable VillageId parentVillageId,
      @javax.annotation.Nullable ServerPlayer controller
   ) {
      VillageSpawner.ValidationResult validation = dryRunPlacement(level, center, villageType);
      if (validation.error != null) {
         return validation.error;
      }

      VillageSpawner.SlotWithPlan centreSlot = validation.centreSlot;
      List<VillageSpawner.PlacedBuilding> placedBuildings = validation.placedBuildings;
      BlockPos townhallRequestedOrigin = validation.townhallRequestedOrigin;
      Rotation townhallRotation = validation.townhallRotation;
      ClearMargins centreClearMargins = validation.centreClearMargins;
      int townhallBaseY = validation.townhallBaseY;
      int townhallPlacementY = townhallBaseY + centreSlot.plan.groundLevel();
      BlockPos centerAtGround = validation.centerAtGround;
      ResourceLocation villageTypeId = villageType.id();
      boolean[][] townhallSnow = TerrainPreparer.checkForSnow(
         level, townhallRequestedOrigin, centreSlot.plan.width(), centreSlot.plan.depth(), townhallRotation, centreClearMargins
      );
      TerrainPreparer.clearAndFlattenAtY(
         level,
         townhallRequestedOrigin,
         centreSlot.plan.width(),
         centreSlot.plan.height(),
         centreSlot.plan.depth(),
         townhallRotation,
         centreSlot.plan.groundLevel(),
         townhallBaseY,
         centreClearMargins
      );
      TerrainPreparer.decayOrphanedLeaves(
         level, townhallRequestedOrigin, centreSlot.plan.width(), centreSlot.plan.depth(), townhallRotation, townhallBaseY, centreClearMargins
      );
      BlockPos townhallOrigin = new BlockPos(townhallRequestedOrigin.getX(), townhallPlacementY, townhallRequestedOrigin.getZ());
      VillageId villageId = VillageId.random();
      Village village = new Village(villageId, villageType.culture(), villageTypeId, centerAtGround);
      if (parentBaseName != null) {
         String qualifier = pickQualifier(level, centerAtGround, villageType);
         String villageName = qualifier != null ? parentBaseName + " " + qualifier : parentBaseName;
         village.setVillageName(villageName);
      } else {
         String nameListKey = villageType.nameList();
         if (nameListKey != null) {
            NameLists nameLists = ModCultures.getNameLists(villageType.culture());
            if (nameLists != null) {
               String villageName = nameLists.randomFrom(nameListKey);
               if (villageName != null) {
                  String qualifier = pickQualifier(level, centerAtGround, villageType);
                  if (qualifier != null) {
                     villageName = villageName + " " + qualifier;
                  }

                  village.setVillageName(villageName);
               }
            }
         } else {
            village.setVillageName(villageType.name());
         }
      }

      if (village.getVillageName() == null) {
         LOGGER.warn("Village name was null after naming logic for type {} — falling back to type name", villageTypeId);
         village.setVillageName(villageType.name() != null ? villageType.name() : villageTypeId.getPath());
      }

      if (!villageType.brickColourThemes().isEmpty()) {
         BrickColourTheme chosen = pickWeightedTheme(villageType.brickColourThemes(), level.getRandom());
         village.setBrickTheme(chosen);
         LOGGER.debug("Brick theme '{}' chosen for village {}", chosen.name(), village.getVillageName());
      }

      if (!villageType.bannerJsons().isEmpty()) {
         List<String> pool = villageType.bannerJsons();
         String pick = pool.get(level.getRandom().nextInt(pool.size()));
         village.setBannerNbt(pick);
      }

      BuildingId buildingId = BuildingId.random();
      BuildingInstance instance = new BuildingInstance(
         buildingId, centreSlot.plan.id(), townhallOrigin, townhallRotation, BuildingInstance.Status.COMPLETE, centreSlot.planSetId, centreSlot.variant, 0
      );
      if (village.getBrickTheme() != null) {
         instance.initBrickColours(village.getBrickTheme(), level.getRandom());
      }

      BuildingPlacer.placeInstantly(level, centreSlot.plan, townhallOrigin, townhallRotation, instance);
      TerrainPreparer.restoreSnow(
         level, townhallRequestedOrigin, centreSlot.plan.width(), centreSlot.plan.depth(), townhallRotation, townhallSnow, centreClearMargins
      );
      BuildingFinalizer.applyPostPlacement(level, village, instance, centreSlot.plan);
      ItemStack scroll = VillageBookService.createScrollForVillage(village);
      if (instance.getInventory() != null) {
         instance.getInventory().addStack(level, scroll);
      } else {
         LOGGER.warn("Unable to place scroll: townhall without chests (village {})", village.getVillageName());
      }

      village.addBuilding(instance);
      BuildingFinalizer.applyCompletionEffects(level, village, instance);
      if (centreSlot.planSetId != null) {
         BuildingPlanSet townhallPlanSet = ModCultures.getBuildingPlanSet(centreSlot.planSetId);
         if (townhallPlanSet != null) {
            SubBuildingHelper.spawnStartingSubBuildings(level, village, townhallPlanSet, instance, true);
            BuildingPlanSet.LevelDef thLevel0 = townhallPlanSet.getLevel(centreSlot.variant, 0);
            if (thLevel0 != null) {
               SubBuildingHelper.spawnUpgradeSubBuildings(level, village, townhallPlanSet, thLevel0, instance, false);
            }
         }
      }

      if (!villageType.playerControlled() && !validation.precomputedWalls.isEmpty()) {
         VillageTerrainMap wallsTerrainMap = VillageTerrainMap.compute(level, centerAtGround, villageType.radius());

         for (BuildingInstance existing : village.getBuildings()) {
            if (!existing.isSubBuilding()) {
               BuildingPlan ep = ModCultures.getBuildingPlan(existing.getPlanId());
               if (ep != null) {
                  ClearMargins wallsMargins = ClearMargins.symmetric(PlacementConstraints.getDefaultClearMargin());
                  if (existing.getPlanSetId() != null) {
                     BuildingPlanSet wallsPlanSet = ModCultures.getBuildingPlanSet(existing.getPlanSetId());
                     if (wallsPlanSet != null) {
                        wallsMargins = wallsPlanSet.clearMargins();
                     }
                  }

                  int surfaceY = existing.getOrigin().getY() - ep.groundLevel();
                  wallsTerrainMap.markBuildingFootprint(
                     existing.getOrigin().getX(), existing.getOrigin().getZ(), ep.width(), ep.depth(), wallsMargins, existing.getRotation(), surfaceY
                  );
               }
            }
         }

         spawnWalls(level, village, villageType, centerAtGround, wallsTerrainMap, validation.precomputedWalls);
      }

      for (VillageSpawner.PlacedBuilding pb : placedBuildings) {
         BuildingPlan plan = pb.swp.plan;
         BuildingId buildingIdx = BuildingId.random();
         BuildingInstance instancex = new BuildingInstance(
            buildingIdx, plan.id(), pb.origin, pb.rotation, BuildingInstance.Status.COMPLETE, pb.swp.planSetId, pb.swp.variant, 0
         );
         if (village.getBrickTheme() != null) {
            instancex.initBrickColours(village.getBrickTheme(), level.getRandom());
         }

         ClearMargins starterMargins = ClearMargins.symmetric(PlacementConstraints.getDefaultClearMargin());
         if (pb.swp.planSetId != null) {
            BuildingPlanSet starterPlanSetForMargins = ModCultures.getBuildingPlanSet(pb.swp.planSetId);
            if (starterPlanSetForMargins != null) {
               starterMargins = starterPlanSetForMargins.clearMargins();
            }
         }

         boolean[][] starterSnow = TerrainPreparer.checkForSnow(level, pb.requestedOrigin, plan.width(), plan.depth(), pb.rotation, starterMargins);
         TerrainPreparer.clearAndFlattenAtY(
            level, pb.requestedOrigin, plan.width(), plan.height(), plan.depth(), pb.rotation, plan.groundLevel(), pb.baseY, starterMargins
         );
         TerrainPreparer.decayOrphanedLeaves(level, pb.requestedOrigin, plan.width(), plan.depth(), pb.rotation, pb.baseY, starterMargins);
         BuildingPlacer.placeInstantly(level, plan, pb.origin, pb.rotation, instancex);
         TerrainPreparer.restoreSnow(level, pb.requestedOrigin, plan.width(), plan.depth(), pb.rotation, starterSnow, starterMargins);
         BuildingFinalizer.applyPostPlacement(level, village, instancex, plan);
         village.addBuilding(instancex);
         BuildingFinalizer.applyCompletionEffects(level, village, instancex);
         if (pb.swp.planSetId != null) {
            BuildingPlanSet starterPlanSet = ModCultures.getBuildingPlanSet(pb.swp.planSetId);
            if (starterPlanSet != null) {
               SubBuildingHelper.spawnStartingSubBuildings(level, village, starterPlanSet, instancex, true);
               BuildingPlanSet.LevelDef starterLevel0 = starterPlanSet.getLevel(pb.swp.variant, 0);
               if (starterLevel0 != null) {
                  SubBuildingHelper.spawnUpgradeSubBuildings(level, village, starterPlanSet, starterLevel0, instancex, false);
               }
            }
         }
      }

      for (BuildingInstance building : village.getBuildings()) {
         if (!building.isSubBuilding()) {
            ResourceLocation planSetId = building.getPlanSetId();
            if (planSetId != null) {
               BuildingPlanSet bps = ModCultures.getBuildingPlanSet(planSetId);
               if (bps != null) {
                  VillageGrowthManager.spawnBuildingOccupants(level, village, bps, building);
               }
            }
         }
      }

      BuildingInstance centreBuilding = village.getBuildings().isEmpty() ? null : village.getBuildings().get(0);
      if (centreBuilding != null && centreBuilding.getPlanSetId() != null) {
         BuildingPlanSet centrePlanSet = ModCultures.getBuildingPlanSet(centreBuilding.getPlanSetId());
         if (centrePlanSet != null && !centrePlanSet.startingGoods().isEmpty()) {
            village.fillStartingGoods(level, centreBuilding, centrePlanSet, true);
            village.setLastGoodsRefresh(level.getGameTime());
         }
      }

      VillageSavedData savedData = VillageSavedData.get(level);
      VillageManager villageManager = savedData.getVillageManager();
      villageManager.addVillage(village);
      savedData.setDirty();
      if (parentVillageId != null) {
         village.setParentVillageId(parentVillageId);
      }

      initializeRelations(level, village, villageManager);
      if (villageType.playerControlled() && controller != null) {
         village.setOwner(controller.getUUID(), controller.getName().getString());
         VillageReputation rep = village.getReputation();
         rep.add(controller.getUUID(), 20000);

         for (Village other : villageManager.getAllVillages()) {
            if (other != village && controller.getUUID().equals(other.getOwnerUUID())) {
               village.setRelation(other.getId(), 100);
               other.setRelation(village.getId(), 100);
            }
         }
      }

      Set<ChunkPos> chunks = village.computeVillageChunks();
      VillageChunkLoader.forceVillageChunks(level, village.getCenter(), chunks);
      village.setLoadedChunks(chunks);
      village.setChunksForceLoaded(true);
      village.rebuildWaypointGraph(level);
      if (completion > 0 && !villageType.playerControlled()) {
         int totalProjects = VillageGrowthManager.countTotalProjects(villageType);
         int target = totalProjects * completion / 100;
         VillageTerrainMap rushTerrainMap = VillageTerrainMap.compute(level, centerAtGround, villageType.radius());

         for (BuildingInstance existing : village.getBuildings()) {
            if (!existing.isSubBuilding()) {
               BuildingPlan ep = ModCultures.getBuildingPlan(existing.getPlanId());
               if (ep != null) {
                  ClearMargins rushMargins = ClearMargins.symmetric(PlacementConstraints.getDefaultClearMargin());
                  if (existing.getPlanSetId() != null) {
                     BuildingPlanSet rushPlanSet = ModCultures.getBuildingPlanSet(existing.getPlanSetId());
                     if (rushPlanSet != null) {
                        rushMargins = rushPlanSet.clearMargins();
                     }
                  }

                  int surfaceY = existing.getOrigin().getY() - ep.groundLevel();
                  rushTerrainMap.markBuildingFootprint(
                     existing.getOrigin().getX(), existing.getOrigin().getZ(), ep.width(), ep.depth(), rushMargins, existing.getRotation(), surfaceY
                  );
               }
            }
         }

         TerrainReachability rushReachability = TerrainReachability.compute(rushTerrainMap, centerAtGround);
         int rushed = 0;
         int consecutiveFailures = 0;
         HashSet<ResourceLocation> rushExcluded = new HashSet<>();
         int maxIterations = completion == 100 ? target + 200 : target;

         for (int i = 0; i < maxIterations; i++) {
            boolean progress = VillageGrowthManager.rushOneProject(level, village, rushTerrainMap, rushExcluded, rushReachability);
            if (progress) {
               rushed++;
               consecutiveFailures = 0;
               rushReachability = TerrainReachability.compute(rushTerrainMap, centerAtGround);
            } else if (++consecutiveFailures >= 5) {
               break;
            }
         }

         if (rushed > 0) {
            savedData.setDirty();
            LOGGER.info("[Millenaire] Rush completion: {}/{} projects completed (target={}%)", new Object[]{rushed, totalProjects, completion});
         }

         double wallPlacementRatio = completion / 100.0;
         int wallUpgradePasses = completion >= 100 ? 5 : completion / 25;
         int wallRushed = VillageGrowthManager.rushWallProjects(level, village, wallPlacementRatio, wallUpgradePasses);
         if (wallRushed > 0) {
            savedData.setDirty();
            LOGGER.info("[Millenaire] Rush walls: {} segments (upgrade passes={})", wallRushed, wallUpgradePasses);
         }
      }

      village.updatePens(level, true);
      village.getPathManager().recalculatePaths(level, village, true);
      if (completion > 0) {
         BuildingFinalizer.applyVillageUpdates(level, village);
      } else {
         village.sendFireplacePositions(level);
      }

      VillageNotifier.notifySpawn(level, center, village.getVillageName(), villageType);
      village.recordEvent(level, "Village founded (type: " + villageTypeId.getPath() + ", completion: " + completion + "%)");
      village.recordChronicleEvent(level, VillageEventType.FOUNDED, villageType.name(), null);
      LOGGER.info(
         "[Millenaire] Village {} spawned: {} buildings, completion={}%",
         new Object[]{villageId.uuid().toString().substring(0, 8), village.getBuildings().size(), completion}
      );
      if (!villageType.hamlets().isEmpty()) {
         String baseName = extractBaseName(village.getVillageName(), villageType);
         generateHamlets(level, village, villageManager, villageType, baseName, completion);
      }

      return null;
   }

   private static void generateHamlets(ServerLevel level, Village parent, VillageManager vm, VillageType parentType, String baseName, int parentCompletion) {
      List<ResourceLocation> hamletTypes = parentType.hamlets();
      int hamletCount = hamletTypes.size();
      double baseAngle = 0.06283185307179587 * RANDOM.nextInt(100);

      for (int hamletIdx = 0; hamletIdx < hamletCount; hamletIdx++) {
         ResourceLocation hamletTypeId = hamletTypes.get(hamletIdx);
         VillageType hamletType = ModCultures.getVillageType(hamletTypeId);
         if (hamletType == null) {
            LOGGER.warn("[Millenaire] Hamlet type not found: {}", hamletTypeId);
         } else {
            boolean placed = false;
            int minRadius = 250;
            double sectorOffset = hamletIdx * ((Math.PI * 2) / hamletCount);

            while (minRadius < 350 && !placed) {
               double startAngle = baseAngle + sectorOffset;

               for (int step = 0; step < 36 && !placed; step++) {
                  double angle = startAngle + (step + 1) * (Math.PI / 18);
                  int radius = minRadius + RANDOM.nextInt(40);
                  int dx = (int)(Math.cos(angle) * radius);
                  int dz = (int)(Math.sin(angle) * radius);
                  BlockPos candidate = parent.getCenter().offset(dx, 0, dz);
                  if (isBiomeValidForHamlet(level, candidate, hamletType)) {
                     ChunkAccess chunk = level.getChunk(candidate.getX() >> 4, candidate.getZ() >> 4);
                     if (chunk != null) {
                        int surfaceY = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, candidate.getX(), candidate.getZ());
                        if (surfaceY > level.getMinBuildHeight()) {
                           BlockPos surfacePos = new BlockPos(candidate.getX(), surfaceY, candidate.getZ());
                           if (!vm.isWithinMinDistance(surfacePos, 100.0)) {
                              Component failure = spawnVillage(level, surfacePos, hamletType, parentCompletion, baseName, parent.getId());
                              if (failure == null) {
                                 placed = true;
                                 LOGGER.info(
                                    "[Millenaire] Hamlet {} spawned at {} for parent {}",
                                    new Object[]{hamletTypeId, surfacePos.toShortString(), parent.getVillageName()}
                                 );
                              }
                           }
                        }
                     }
                  }
               }

               minRadius += 50;
            }

            if (!placed) {
               LOGGER.warn("[Millenaire] Failed to place hamlet {} for parent {}", hamletTypeId, parent.getVillageName());
            }
         }
      }
   }

   private static boolean isBiomeValidForHamlet(ServerLevel level, BlockPos pos, VillageType hamletType) {
      if (hamletType.biomeTags().isEmpty()) {
         return true;
      }

      int validCount = 0;
      int totalCount = 0;

      for (int gx = -hamletType.radius(); gx <= hamletType.radius(); gx += 16) {
         for (int gz = -hamletType.radius(); gz <= hamletType.radius(); gz += 16) {
            totalCount++;
            BlockPos samplePos = pos.offset(gx, 0, gz);
            Holder<Biome> sampleBiome = level.getBiome(samplePos);

            for (TagKey<Biome> tag : hamletType.biomeTags()) {
               if (sampleBiome.is(tag)) {
                  validCount++;
                  break;
               }
            }
         }
      }

      float validPerc = (float)validCount / totalCount;
      return validPerc >= hamletType.minimumBiomeValidity();
   }

   static String extractBaseName(String fullName, VillageType villageType) {
      if (fullName == null) {
         return null;
      }

      String[] terrainQualifiers = new String[]{
         villageType.forestQualifier(),
         villageType.hillQualifier(),
         villageType.mountainQualifier(),
         villageType.desertQualifier(),
         villageType.lavaQualifier(),
         villageType.lakeQualifier(),
         villageType.oceanQualifier()
      };

      for (String terrainQ : terrainQualifiers) {
         if (terrainQ != null) {
            String suffix = " " + terrainQ;
            if (fullName.endsWith(suffix)) {
               return fullName.substring(0, fullName.length() - suffix.length());
            }
         }
      }

      for (String qualifier : villageType.qualifiers()) {
         String suffix = " " + qualifier;
         if (fullName.endsWith(suffix)) {
            return fullName.substring(0, fullName.length() - suffix.length());
         }
      }

      return fullName;
   }

   private static void initializeRelations(ServerLevel level, Village newVillage, VillageManager manager) {
      if (!newVillage.isLoneBuilding()) {
         int backgroundRadius = MillenaireServerConfig.SERVER.backgroundRadius.getAsInt();
         long bgRadiusSq = (long)backgroundRadius * backgroundRadius;

         for (Village other : manager.getAllVillages()) {
            if (!other.getId().equals(newVillage.getId()) && !other.isLoneBuilding() && !(newVillage.getCenter().distSqr(other.getCenter()) >= bgRadiusSq)) {
               VillageId newParent = newVillage.getParentVillageId();
               VillageId otherParent = other.getParentVillageId();
               int initialRelation;
               if (newParent != null && newParent.equals(other.getId())) {
                  initialRelation = 100;
               } else if (otherParent != null && otherParent.equals(newVillage.getId())) {
                  initialRelation = 100;
               } else if (newParent != null && otherParent != null && newParent.equals(otherParent)) {
                  initialRelation = 100;
               } else if (newVillage.getCultureId().equals(other.getCultureId())) {
                  initialRelation = 50;
               } else {
                  initialRelation = -30;
               }

               newVillage.setRelation(other.getId(), initialRelation);
               other.setRelation(newVillage.getId(), initialRelation);
            }
         }
      }
   }

   @javax.annotation.Nullable
   private static String pickQualifier(ServerLevel level, BlockPos center, VillageType villageType) {
      boolean hasAnyQualifier = !villageType.qualifiers().isEmpty()
         || villageType.forestQualifier() != null
         || villageType.hillQualifier() != null
         || villageType.mountainQualifier() != null
         || villageType.desertQualifier() != null
         || villageType.lavaQualifier() != null
         || villageType.lakeQualifier() != null
         || villageType.oceanQualifier() != null;
      if (!hasAnyQualifier) {
         return null;
      }

      List<String> candidates = new ArrayList<>(villageType.qualifiers());
      Holder<Biome> biomeHolder = level.getBiome(center);
      if (biomeHolder.is(Biomes.IS_FOREST) && villageType.forestQualifier() != null) {
         candidates.add(villageType.forestQualifier());
      }

      if (biomeHolder.is(Biomes.IS_HILL) && villageType.hillQualifier() != null) {
         candidates.add(villageType.hillQualifier());
      }

      if (biomeHolder.is(Biomes.IS_MOUNTAIN) && villageType.mountainQualifier() != null) {
         candidates.add(villageType.mountainQualifier());
      }

      if (biomeHolder.is(Biomes.IS_DESERT) && villageType.desertQualifier() != null) {
         candidates.add(villageType.desertQualifier());
      }

      if (biomeHolder.is(Biomes.IS_OCEAN) && villageType.oceanQualifier() != null) {
         candidates.add(villageType.oceanQualifier());
      }

      if (villageType.lavaQualifier() != null || villageType.lakeQualifier() != null) {
         boolean lavaFound = false;
         boolean lakeFound = false;

         for (int dx = -50; dx <= 50 && (!lavaFound || !lakeFound); dx += 4) {
            for (int dz = -50; dz <= 50 && (!lavaFound || !lakeFound); dz += 4) {
               for (int dy = -10; dy <= 20 && (!lavaFound || !lakeFound); dy += 2) {
                  BlockPos pos = center.offset(dx, dy, dz);
                  BlockState state = level.getBlockState(pos);
                  if (!lavaFound && state.is(Blocks.LAVA)) {
                     lavaFound = true;
                  }

                  if (!lakeFound && state.is(Blocks.WATER) && pos.getY() >= 65 && level.getBlockState(pos.above()).isAir()) {
                     lakeFound = true;
                  }
               }
            }
         }

         if (lavaFound && villageType.lavaQualifier() != null) {
            candidates.add(villageType.lavaQualifier());
         }

         if (lakeFound && villageType.lakeQualifier() != null) {
            candidates.add(villageType.lakeQualifier());
         }
      }

      return candidates.isEmpty() ? null : candidates.get(level.random.nextInt(candidates.size()));
   }

   private static BrickColourTheme pickWeightedTheme(List<BrickColourTheme> themes, RandomSource random) {
      int totalWeight = 0;

      for (BrickColourTheme theme : themes) {
         totalWeight += theme.weight();
      }

      int roll = random.nextInt(totalWeight);
      int cumulative = 0;

      for (BrickColourTheme theme : themes) {
         cumulative += theme.weight();
         if (roll < cumulative) {
            return theme;
         }
      }

      return themes.getLast();
   }

   private static List<VillageSpawner.SlotWithPlan> resolveSlots(VillageType villageType) {
      List<VillageSpawner.SlotWithPlan> result = new ArrayList<>();

      for (VillageType.LayoutSlot slot : villageType.layout()) {
         ResourceLocation planRef = slot.plan();
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planRef);
         if (planSet != null) {
            String variant = planSet.pickRandomVariant(RANDOM);
            BuildingPlanSet.LevelDef levelDef = planSet.getLevel(variant, 0);
            if (levelDef != null) {
               BuildingPlan plan = ModCultures.getBuildingPlan(levelDef.planId());
               if (plan != null) {
                  result.add(new VillageSpawner.SlotWithPlan(slot, plan, planSet.id(), variant));
                  continue;
               }
            }

            LOGGER.warn("[Millenaire] BuildingPlanSet {} found but plan level 0 not found", planRef);
         }

         BuildingPlan directPlan = ModCultures.getBuildingPlan(planRef);
         if (directPlan != null) {
            result.add(new VillageSpawner.SlotWithPlan(slot, directPlan, null, null));
         } else {
            LOGGER.warn("[Millenaire] Plan not found: {} (neither set nor direct plan)", planRef);
         }
      }

      return result;
   }

   private static boolean isCentreRole(String role) {
      return "centre".equals(role) || "townhall".equals(role);
   }

   private static boolean isStartRole(String role) {
      return "start".equals(role) || "starter".equals(role);
   }

   private static void spawnWalls(
      ServerLevel level,
      Village village,
      VillageType villageType,
      BlockPos centre,
      VillageTerrainMap terrainMap,
      List<VillageSpawner.PrecomputedWalls> precomputedWalls
   ) {
      TerrainReachability reachability = TerrainReachability.compute(terrainMap, centre);

      for (VillageSpawner.PrecomputedWalls pw : precomputedWalls) {
         reachability = placeWallSegments(level, village, centre, terrainMap, reachability, pw.wallType(), pw.result());
      }
   }

   private static TerrainReachability placeWallSegments(
      ServerLevel level,
      Village village,
      BlockPos centre,
      VillageTerrainMap terrainMap,
      @javax.annotation.Nullable TerrainReachability reachability,
      WallType wallType,
      VillageWallGenerator.WallLocationResult result
   ) {
      if (result.segments().isEmpty()) {
         return reachability != null ? reachability : TerrainReachability.compute(terrainMap, centre);
      }

      Map<ResourceLocation, VillageSpawner.PlanSetRef> planToSet = buildWallPlanSetMap(wallType);
      int placed = 0;
      int planned = 0;
      boolean terrainChanged = false;

      record ResolvedSegment(
         VillageWallGenerator.PlannedWallSegment seg,
         BuildingPlan plan,
         int targetLevel,
         VillageSpawner.PlanSetRef ref,
         Rotation rotation,
         BlockPos centredOrigin,
         BlockPos adjustedOrigin,
         boolean spawn,
         BuildingId buildingId,
         @javax.annotation.Nullable boolean[][] snowSnapshot,
         ClearMargins margins
      ) {
      }

      List<ResolvedSegment> resolvedSegments = new ArrayList<>(result.segments().size());

      for (VillageWallGenerator.PlannedWallSegment seg : result.segments()) {
         VillageSpawner.PlanSetRef ref = planToSet.get(seg.planId());
         if (ref == null) {
            LOGGER.warn("[Millenaire] Wall segment plan {} has no matching plan set in wall type {}", seg.planId(), wallType.id());
         } else {
            int targetLevel = Math.max(0, seg.level());
            BuildingPlanSet wallSet = ModCultures.getBuildingPlanSet(ref.planSetId());
            BuildingPlan plan = null;
            if (wallSet != null) {
               for (; targetLevel >= 0; targetLevel--) {
                  BuildingPlanSet.LevelDef levelDef = wallSet.getLevel(ref.variant(), targetLevel);
                  if (levelDef != null) {
                     plan = ModCultures.getBuildingPlan(levelDef.planId());
                     if (plan != null) {
                        break;
                     }
                  }
               }
            }

            if (plan == null) {
               plan = ModCultures.getBuildingPlan(seg.planId());
               targetLevel = 0;
            }

            if (plan != null) {
               Rotation rotation = intToRotation(seg.orientation());
               BlockPos centredOrigin = centreOriginOnPos(seg.pos(), plan.width(), plan.depth(), rotation);
               BlockPos adjustedOrigin = new BlockPos(centredOrigin.getX(), centredOrigin.getY() + plan.groundLevel(), centredOrigin.getZ());
               boolean spawn = seg.level() >= 0;
               ClearMargins segmentMargins = wallSet != null ? wallSet.clearMargins() : ClearMargins.defaults();
               boolean[][] snowSnapshot = null;
               if (spawn) {
                  snowSnapshot = TerrainPreparer.checkForSnow(level, centredOrigin, plan.width(), plan.depth(), rotation, segmentMargins);
                  TerrainPreparer.clearAndFlattenAtY(
                     level, centredOrigin, plan.width(), plan.height(), plan.depth(), rotation, plan.groundLevel(), centredOrigin.getY(), segmentMargins
                  );
                  TerrainPreparer.decayOrphanedLeaves(level, centredOrigin, plan.width(), plan.depth(), rotation, centredOrigin.getY(), segmentMargins);
               }

               resolvedSegments.add(
                  new ResolvedSegment(
                     seg, plan, targetLevel, ref, rotation, centredOrigin, adjustedOrigin, spawn, BuildingId.random(), snowSnapshot, segmentMargins
                  )
               );
            }
         }
      }

      for (ResolvedSegment rs : resolvedSegments) {
         BuildingPlan plan = rs.plan();
         BuildingInstance.Status status = rs.spawn() ? BuildingInstance.Status.COMPLETE : BuildingInstance.Status.PLANNED;
         BuildingInstance instance = new BuildingInstance(
            rs.buildingId(), plan.id(), rs.adjustedOrigin(), rs.rotation(), status, rs.ref().planSetId(), rs.ref().variant(), rs.targetLevel()
         );
         if (rs.spawn()) {
            if (village.getBrickTheme() != null) {
               instance.initBrickColours(village.getBrickTheme(), level.getRandom());
            }

            BuildingPlacer.placeInstantly(level, plan, rs.adjustedOrigin(), rs.rotation(), instance);
            TerrainPreparer.restoreSnow(level, rs.centredOrigin(), plan.width(), plan.depth(), rs.rotation(), rs.snowSnapshot(), rs.margins());
            BuildingFinalizer.applyPostPlacement(level, village, instance, plan);
            placed++;
            terrainMap.markBuildingFootprint(
               rs.centredOrigin().getX(),
               rs.centredOrigin().getZ(),
               plan.width(),
               plan.depth(),
               ClearMargins.symmetric(0),
               rs.rotation(),
               rs.centredOrigin().getY()
            );
            terrainChanged = true;
         } else {
            planned++;
            terrainMap.markOccupied(rs.centredOrigin().getX(), rs.centredOrigin().getZ(), plan.width(), plan.depth(), rs.rotation());
            terrainChanged = true;
         }

         village.addBuilding(instance);
         if (rs.spawn()) {
            BuildingFinalizer.applyCompletionEffects(level, village, instance);
         }
      }

      LOGGER.info("[Millenaire] Wall type {}: {} segments placed, {} planned", new Object[]{wallType.id(), placed, planned});
      return terrainChanged
         ? TerrainReachability.compute(terrainMap, centre)
         : (reachability != null ? reachability : TerrainReachability.compute(terrainMap, centre));
   }

   private static void markWallFootprints(VillageTerrainMap terrainMap, WallType wallType, VillageWallGenerator.WallLocationResult result) {
      Map<ResourceLocation, VillageSpawner.PlanSetRef> planToSet = buildWallPlanSetMap(wallType);

      for (VillageWallGenerator.PlannedWallSegment seg : result.segments()) {
         VillageSpawner.PlanSetRef ref = planToSet.get(seg.planId());
         BuildingPlanSet wallSet = ref != null ? ModCultures.getBuildingPlanSet(ref.planSetId()) : null;
         BuildingPlan plan = null;
         if (wallSet != null) {
            for (int targetLevel = Math.max(0, seg.level()); targetLevel >= 0; targetLevel--) {
               BuildingPlanSet.LevelDef levelDef = wallSet.getLevel(ref.variant(), targetLevel);
               if (levelDef != null) {
                  plan = ModCultures.getBuildingPlan(levelDef.planId());
                  if (plan != null) {
                     break;
                  }
               }
            }
         }

         if (plan == null) {
            plan = ModCultures.getBuildingPlan(seg.planId());
         }

         if (plan != null) {
            Rotation rotation = intToRotation(seg.orientation());
            BlockPos centredOrigin = centreOriginOnPos(seg.pos(), plan.width(), plan.depth(), rotation);
            terrainMap.markOccupied(centredOrigin.getX(), centredOrigin.getZ(), plan.width(), plan.depth(), rotation);
         }
      }
   }

   private static Map<ResourceLocation, VillageSpawner.PlanSetRef> buildWallPlanSetMap(WallType wallType) {
      Map<ResourceLocation, VillageSpawner.PlanSetRef> map = new HashMap<>();
      addWallSetToMap(map, wallType.wallPlanSet());
      addWallSetToMap(map, wallType.towerPlanSet());
      addWallSetToMap(map, wallType.gatewayPlanSet());
      addWallSetToMap(map, wallType.cornerPlanSet());
      addWallSetToMap(map, wallType.capRightPlanSet());
      addWallSetToMap(map, wallType.capLeftPlanSet());
      addWallSetToMap(map, wallType.capBothPlanSet());
      addWallSetToMap(map, wallType.slope1LeftPlanSet());
      addWallSetToMap(map, wallType.slope1RightPlanSet());
      addWallSetToMap(map, wallType.slope2LeftPlanSet());
      addWallSetToMap(map, wallType.slope2RightPlanSet());
      addWallSetToMap(map, wallType.slope3LeftPlanSet());
      addWallSetToMap(map, wallType.slope3RightPlanSet());
      return map;
   }

   private static void addWallSetToMap(Map<ResourceLocation, VillageSpawner.PlanSetRef> map, @javax.annotation.Nullable ResourceLocation setId) {
      if (setId != null) {
         BuildingPlanSet set = ModCultures.getBuildingPlanSet(setId);
         if (set != null) {
            String variant = set.variants().keySet().stream().findFirst().orElse(null);
            if (variant != null) {
               BuildingPlanSet.LevelDef level0 = set.getLevel(variant, 0);
               if (level0 != null) {
                  map.putIfAbsent(level0.planId(), new VillageSpawner.PlanSetRef(setId, variant));
               }
            }
         }
      }
   }

   private static Rotation intToRotation(int orientation) {
      return Rotation.values()[Math.floorMod(1 - orientation, 4)];
   }

   private static BlockPos centreOriginOnPos(BlockPos pos, int planWidth, int planDepth, Rotation rotation) {
      int L = planWidth;
      int W = planDepth;

      return switch (rotation) {
         case CLOCKWISE_90 -> new BlockPos(pos.getX() + W / 2 - 1, pos.getY(), pos.getZ() - L / 2);
         case CLOCKWISE_180 -> new BlockPos(pos.getX() + L / 2 - 1, pos.getY(), pos.getZ() + W / 2 - 1);
         case COUNTERCLOCKWISE_90 -> new BlockPos(pos.getX() - W / 2, pos.getY(), pos.getZ() + L / 2 - 1);
         default -> new BlockPos(pos.getX() - L / 2, pos.getY(), pos.getZ() - W / 2);
      };
   }

   private record PlacedBuilding(VillageSpawner.SlotWithPlan swp, BlockPos origin, Rotation rotation, BlockPos requestedOrigin, int baseY) {
   }

   private record PlanSetRef(ResourceLocation planSetId, String variant) {
   }

   private record PrecomputedWalls(WallType wallType, VillageWallGenerator.WallLocationResult result) {
   }

   private record SlotWithPlan(
      VillageType.LayoutSlot slot, BuildingPlan plan, @javax.annotation.Nullable ResourceLocation planSetId, @javax.annotation.Nullable String variant
   ) {
   }

   private record ValidationResult(
      @javax.annotation.Nullable Component error,
      @javax.annotation.Nullable VillageSpawner.SlotWithPlan centreSlot,
      List<VillageSpawner.PlacedBuilding> placedBuildings,
      BlockPos townhallRequestedOrigin,
      Rotation townhallRotation,
      ClearMargins centreClearMargins,
      int townhallBaseY,
      BlockPos centerAtGround,
      List<VillageSpawner.PrecomputedWalls> precomputedWalls
   ) {
      static VillageSpawner.ValidationResult fail(Component error) {
         return new VillageSpawner.ValidationResult(
            error, null, List.of(), BlockPos.ZERO, Rotation.NONE, ClearMargins.symmetric(0), 0, BlockPos.ZERO, List.of()
         );
      }
   }
}
