package org.millenaire.item;

import com.mojang.logging.LogUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import org.joml.Vector3f;
import org.millenaire.block.LockedChestBlockEntity;
import org.millenaire.block.VillagePanelBlockEntity;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.ConstructionTask;
import org.millenaire.building.SpecialPoint;
import org.millenaire.commerce.TradeGood;
import org.millenaire.commerce.TradeGoodsLoader;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.culture.VillagerType;
import org.millenaire.diagnostics.WaypointVisualizationManager;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerNavigationManager;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.goal.VillagerTask;
import org.millenaire.goal.WaypointNavigator;
import org.millenaire.network.WandDebugActionPayload;
import org.millenaire.village.BuildingFinalizer;
import org.millenaire.village.LocalMerchantHelper;
import org.millenaire.village.NightActionHelper;
import org.millenaire.village.Village;
import org.millenaire.village.VillageGrowthManager;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillageWaypointGraph;
import org.millenaire.village.panel.PanelType;
import org.millenaire.village.path.VillagePathManager;
import org.millenaire.world.TerrainReachability;
import org.millenaire.world.VillageTerrainMap;
import org.slf4j.Logger;

public final class WandDebugActions {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final DustParticleOptions PATH_DONE_DUST = new DustParticleOptions(new Vector3f(0.2F, 0.4F, 1.0F), 1.2F);
   private static final DustParticleOptions PATH_TODO_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.95F, 0.0F), 1.2F);
   private static final int CLEAR_VIZ_RADIUS = 96;

   private WandDebugActions() {
   }

   public static void execute(ServerLevel level, ServerPlayer player, WandDebugActionPayload payload) {
      switch (payload.actionId()) {
         case "building_info":
            handleBuildingInfo(level, player, payload.targetPos());
            break;
         case "rush_construction":
            handleRushConstruction(level, player, payload.targetPos());
            break;
         case "fill_resources":
            handleFillResources(level, player, payload.targetPos());
            break;
         case "recalculate_paths":
            handleRecalculatePaths(level, player, payload.targetPos());
            break;
         case "clear_paths":
            handleClearPaths(level, player, payload.targetPos());
            break;
         case "force_merchant":
            handleForceMerchant(level, player, payload.targetPos());
            break;
         case "villager_info":
            handleVillagerInfo(level, player, payload.targetEntityId());
            break;
         case "grow_child":
            handleGrowChild(level, player, payload.targetEntityId());
            break;
         case "force_child":
            handleForceChild(level, player, payload.targetEntityId());
            break;
         case "visualize_path":
            handleVisualizePath(level, player, payload.targetEntityId());
            break;
         case "visualize_waypoints":
            handleVisualizeWaypoints(level, player, payload.targetEntityId());
            break;
         case "clear_waypoint_blocks":
            handleClearWaypointBlocks(level, player, payload.targetEntityId());
            break;
         case "nav_state":
            handleNavState(level, player, payload.targetEntityId());
            break;
         case "rebuild_waypoint_graph":
            handleRebuildWaypointGraph(level, player, payload.targetEntityId());
            break;
         default:
            LOGGER.warn("Unknown wand debug action: {}", payload.actionId());
      }
   }

   @Nullable
   private static WandDebugActions.BuildingContext resolveBuildingContext(ServerLevel level, ServerPlayer player, BlockPos pos) {
      BlockEntity be = level.getBlockEntity(pos);
      BuildingId buildingId = null;
      if (be instanceof LockedChestBlockEntity chest) {
         buildingId = chest.getBuildingId();
      } else if (be instanceof VillagePanelBlockEntity panel) {
         buildingId = panel.getBuildingId();
      }

      if (buildingId == null) {
         player.sendSystemMessage(Component.literal("§c[Wand] No building block at target position."));
         return null;
      } else {
         Village village = VillageSavedData.get(level).getVillageManager().findVillageContaining(buildingId);
         if (village == null) {
            player.sendSystemMessage(Component.literal("§c[Wand] No village found for this building."));
            return null;
         } else {
            BuildingInstance building = village.findBuildingById(buildingId);
            if (building == null) {
               player.sendSystemMessage(Component.literal("§c[Wand] Building not found: " + buildingId.uuid()));
               return null;
            } else {
               return new WandDebugActions.BuildingContext(village, building);
            }
         }
      }
   }

   private static void handleBuildingInfo(ServerLevel level, ServerPlayer player, BlockPos pos) {
      WandDebugActions.BuildingContext ctx = resolveBuildingContext(level, player, pos);
      if (ctx != null) {
         BuildingInstance b = ctx.building();
         Village village = ctx.village();
         player.sendSystemMessage(Component.literal("§6═══ Debug Building ═══"));
         player.sendSystemMessage(Component.literal("§ePlan: §f" + b.getPlanId().getPath()));
         if (b.getPlanSetId() != null) {
            player.sendSystemMessage(Component.literal("§ePlanSet: §f" + b.getPlanSetId().getPath() + " §7[" + b.getVariant() + " L" + b.getLevel() + "]"));
         }

         player.sendSystemMessage(Component.literal("§eStatus: §f" + b.getStatus().name()));
         player.sendSystemMessage(Component.literal("§eOrigin: §f" + b.getOrigin().toShortString() + " §eRotation: §f" + b.getRotation().name()));
         ConstructionTask task = b.getConstructionTask();
         if (task != null) {
            player.sendSystemMessage(
               Component.literal(
                  "§eConstruction: §f" + Math.round(task.progress() * 100.0F) + "% (" + task.getNextStepIndex() + "/" + task.totalSteps() + " steps)"
               )
            );
         }

         player.sendSystemMessage(Component.literal("§eSpecial points: §f" + b.getResolvedPoints().size()));
         if (b.getInventory() != null) {
            player.sendSystemMessage(Component.literal("§eInventory: §fpresent (" + b.getInventory().getChestCount() + " chests)"));
         }

         player.sendSystemMessage(Component.literal("§eVillage: §f" + village.getVillageName() + " §7(" + village.getBuildings().size() + " buildings)"));
         if (level.getBlockEntity(pos) instanceof VillagePanelBlockEntity panelBe) {
            player.sendSystemMessage(Component.literal("§ePanelType (BE): §f" + panelBe.getPanelType() + " §7signIdx=" + panelBe.getSignIndex()));
         }

         List<SpecialPoint> signs = b.getPointsByType("signPos");
         if (!signs.isEmpty()) {
            int placed = 0;
            int td = 0;
            int bd = 0;
            StringBuilder details = new StringBuilder();

            for (int i = 0; i < signs.size(); i++) {
               SpecialPoint sp = signs.get(i);
               if (level.getBlockEntity(sp.pos()) instanceof VillagePanelBlockEntity be) {
                  placed++;
                  PanelType t = be.getPanelType();
                  if (t != PanelType.BUILDING_DEFAULT && t != PanelType.HOUSE) {
                     td++;
                  } else {
                     bd++;
                  }

                  if (details.length() > 0) {
                     details.append(", ");
                  }

                  details.append("#").append(i).append("=").append(t.name());
               }
            }

            player.sendSystemMessage(
               Component.literal("§ePanels: §f" + placed + "/" + signs.size() + " placed, " + td + " TH-like, " + bd + " BUILDING_DEFAULT/HOUSE")
            );
            if (bd > 0) {
               player.sendSystemMessage(Component.literal("§6  " + details));
            }
         }
      }
   }

   private static void handleRushConstruction(ServerLevel level, ServerPlayer player, BlockPos pos) {
      WandDebugActions.BuildingContext ctx = resolveBuildingContext(level, player, pos);
      if (ctx != null) {
         Village village = ctx.village();
         VillageType villageType = ModCultures.getVillageType(village.getVillageTypeId());
         if (villageType == null) {
            player.sendSystemMessage(Component.literal("§c[Wand] Village type not found."));
         } else {
            VillageTerrainMap terrainMap = VillageTerrainMap.compute(level, village.getCenter(), villageType.radius());
            BuildingInstance townhall = village.getTownhall();
            TerrainReachability reachability = null;
            if (townhall != null) {
               reachability = TerrainReachability.compute(terrainMap, townhall.getOrigin());
            }

            Set<ResourceLocation> rushExcluded = new HashSet<>();
            int rushed = 0;

            for (int i = 0; i < 50; i++) {
               boolean progress = VillageGrowthManager.rushOneProject(level, village, terrainMap, rushExcluded, reachability);
               if (!progress) {
                  break;
               }

               rushed++;
            }

            if (rushed > 0) {
               BuildingFinalizer.applyVillageUpdates(level, village);
            }

            VillageSavedData.get(level).setDirty();
            if (rushed > 0) {
               player.sendSystemMessage(Component.literal("§a[Wand] Rush: " + rushed + " projects completed."));
            } else {
               player.sendSystemMessage(Component.literal("§7[Wand] No projects to rush."));
            }
         }
      }
   }

   private static void handleFillResources(ServerLevel level, ServerPlayer player, BlockPos pos) {
      WandDebugActions.BuildingContext ctx = resolveBuildingContext(level, player, pos);
      if (ctx != null) {
         Village village = ctx.village();
         BuildingInstance building = ctx.building();
         BuildingPlanSet planSet = building.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(building.getPlanSetId()) : null;
         if (planSet != null && planSet.isTownHall()) {
            BuildingInventory inv = building.getInventory();
            if (inv == null) {
               player.sendSystemMessage(Component.literal("§c[Wand] Town hall has no inventory."));
            } else {
               int totalAdded = 0;

               for (TradeGood good : TradeGoodsLoader.getGoods(village.getCultureId())) {
                  if (good.targetQuantity() > 0) {
                     Item item = ItemHelper.resolve(good.item());
                     if (item != null) {
                        int added = inv.add(level, item, good.targetQuantity());
                        totalAdded += added;
                     }
                  }
               }

               player.sendSystemMessage(Component.literal("§a[Wand] Filled " + totalAdded + " items into town hall inventory."));
            }
         } else {
            player.sendSystemMessage(Component.literal("§c[Wand] Fill resources only works on the town hall."));
         }
      }
   }

   private static void handleRecalculatePaths(ServerLevel level, ServerPlayer player, BlockPos pos) {
      WandDebugActions.BuildingContext ctx = resolveBuildingContext(level, player, pos);
      if (ctx != null) {
         Village village = ctx.village();
         VillagePathManager pathManager = village.getPathManager();
         if (pathManager == null) {
            player.sendSystemMessage(Component.literal("§c[Wand] Village has no path manager."));
         } else {
            pathManager.recalculatePaths(level, village, true);
            player.sendSystemMessage(Component.literal("§a[Wand] Paths recalculated (autobuild)."));
         }
      }
   }

   private static void handleClearPaths(ServerLevel level, ServerPlayer player, BlockPos pos) {
      WandDebugActions.BuildingContext ctx = resolveBuildingContext(level, player, pos);
      if (ctx != null) {
         Village village = ctx.village();
         VillagePathManager pathManager = village.getPathManager();
         if (pathManager == null) {
            player.sendSystemMessage(Component.literal("§c[Wand] Village has no path manager."));
         } else {
            pathManager.clearAllPathsNow(level);
            player.sendSystemMessage(Component.literal("§a[Wand] All village paths cleared."));
         }
      }
   }

   private static void handleForceMerchant(ServerLevel level, ServerPlayer player, BlockPos pos) {
      WandDebugActions.BuildingContext ctx = resolveBuildingContext(level, player, pos);
      if (ctx != null) {
         Village village = ctx.village();
         LocalMerchantHelper.forceAttemptMerchantMoves(level, village);
         player.sendSystemMessage(Component.literal("§a[Wand] Forced merchant move attempts for " + village.getVillageName() + "."));
      }
   }

   @Nullable
   private static MillVillager resolveVillager(ServerLevel level, ServerPlayer player, int entityId) {
      if (level.getEntity(entityId) instanceof MillVillager villager) {
         return villager;
      } else {
         player.sendSystemMessage(Component.literal("§c[Wand] Target is not a Millenaire villager."));
         return null;
      }
   }

   private static void handleVillagerInfo(ServerLevel level, ServerPlayer player, int entityId) {
      MillVillager villager = resolveVillager(level, player, entityId);
      if (villager != null) {
         player.sendSystemMessage(Component.literal("§6═══ Debug Villager ═══"));
         player.sendSystemMessage(Component.literal("§eName: §f" + villager.getVillagerDisplayName()));
         ResourceLocation debugTypeId = villager.getVillagerTypeId();
         player.sendSystemMessage(Component.literal("§eType: §f" + (debugTypeId != null ? debugTypeId.getPath() : "unknown")));
         player.sendSystemMessage(Component.literal("§ePos: §f" + villager.blockPosition().toShortString()));
         VillagerType vType = ModCultures.getVillagerType(villager.getVillagerTypeId());
         if (vType != null) {
            player.sendSystemMessage(Component.literal("§eChild: §f" + vType.isChild()));
         }

         if (villager.getVillageId() != null) {
            VillageManager vm = VillageSavedData.get(level).getVillageManager();
            Village village = vm.getVillage(villager.getVillageId());
            if (village != null) {
               player.sendSystemMessage(Component.literal("§eVillage: §f" + village.getVillageName()));
            }
         }

         BuildingId home = villager.getHomeBuilding();
         if (home != null) {
            player.sendSystemMessage(Component.literal("§eHome: §f" + home.uuid().toString().substring(0, 8)));
         }

         GoalScheduler scheduler = villager.getGoalScheduler();
         VillagerTask currentTask = scheduler.getCurrentTask();
         if (currentTask != null) {
            player.sendSystemMessage(Component.literal("§eGoal: §f" + currentTask.goalId().getPath()));
         } else {
            player.sendSystemMessage(Component.literal("§eGoal: §7(idle)"));
         }
      }
   }

   private static void handleGrowChild(ServerLevel level, ServerPlayer player, int entityId) {
      MillVillager villager = resolveVillager(level, player, entityId);
      if (villager != null) {
         VillagerType vType = ModCultures.getVillagerType(villager.getVillagerTypeId());
         if (vType != null && vType.isChild()) {
            villager.setChildSize(20);
            player.sendSystemMessage(Component.literal("§a[Wand] Child " + villager.getVillagerDisplayName() + " grown to adult size."));
         } else {
            player.sendSystemMessage(Component.literal("§c[Wand] This villager is not a child."));
         }
      }
   }

   private static void handleForceChild(ServerLevel level, ServerPlayer player, int entityId) {
      MillVillager villager = resolveVillager(level, player, entityId);
      if (villager != null) {
         VillagerType vType = ModCultures.getVillagerType(villager.getVillagerTypeId());
         if (vType == null) {
            player.sendSystemMessage(Component.literal("§c[Wand] Unknown villager type."));
         } else if (villager.getVillageId() == null) {
            player.sendSystemMessage(Component.literal("§c[Wand] Villager has no village."));
         } else {
            VillageManager vm = VillageSavedData.get(level).getVillageManager();
            Village village = vm.getVillage(villager.getVillageId());
            if (village == null) {
               player.sendSystemMessage(Component.literal("§c[Wand] Village not found."));
            } else {
               NightActionHelper.forceSpawnChild(level, village, villager, vType);
               player.sendSystemMessage(Component.literal("§a[Wand] Forced child spawn for " + villager.getVillagerDisplayName() + "."));
            }
         }
      }
   }

   private static void handleVisualizePath(ServerLevel level, ServerPlayer player, int entityId) {
      MillVillager villager = resolveVillager(level, player, entityId);
      if (villager != null) {
         Path path = villager.getNavigation().getPath();
         if (path == null) {
            player.sendSystemMessage(Component.literal("§7[Wand] Villager has no active path (getNavigation().getPath() == null)."));
         } else {
            int total = path.getNodeCount();
            int next = path.getNextNodeIndex();
            BlockPos target = path.getTarget();
            BlockPos endNode = total > 0 ? path.getEndNode().asBlockPos() : null;
            player.sendSystemMessage(Component.literal("§6═══ Path snapshot ═══"));
            player.sendSystemMessage(
               Component.literal(
                  String.format(
                     "§eNodes: §f%d total§7, §fnext index = %d §7(%s)",
                     total,
                     next,
                     next >= total ? "§cpath consumed — nav thinks it's arrived" : "§a" + (total - next) + " remaining"
                  )
               )
            );
            if (target != null) {
               player.sendSystemMessage(Component.literal("§eTarget (moveTo): §f" + target.toShortString()));
            }

            if (endNode != null) {
               player.sendSystemMessage(Component.literal("§eEnd node:        §f" + endNode.toShortString()));
            }

            if (total > 0) {
               int firstShown = Math.min(3, total);
               StringBuilder firstNodes = new StringBuilder();

               for (int i = 0; i < firstShown; i++) {
                  if (i > 0) {
                     firstNodes.append(", ");
                  }

                  firstNodes.append(path.getNode(i).asBlockPos().toShortString());
               }

               player.sendSystemMessage(Component.literal("§eFirst nodes: §7" + firstNodes));
               if (total > firstShown + 3) {
                  int last = Math.max(total - 3, firstShown);
                  StringBuilder lastNodes = new StringBuilder();

                  for (int i = last; i < total; i++) {
                     if (i > last) {
                        lastNodes.append(", ");
                     }

                     lastNodes.append(path.getNode(i).asBlockPos().toShortString());
                  }

                  player.sendSystemMessage(Component.literal("§eLast nodes:  §7" + lastNodes));
               }
            }

            for (int burst = 0; burst < 2; burst++) {
               for (int i = 0; i < total; i++) {
                  BlockPos nodePos = path.getNode(i).asBlockPos();
                  DustParticleOptions dust = i < next ? PATH_DONE_DUST : PATH_TODO_DUST;
                  level.sendParticles(player, dust, true, nodePos.getX() + 0.5, nodePos.getY() + 1.2, nodePos.getZ() + 0.5, 3, 0.15, 0.05, 0.15, 0.0);
               }
            }

            player.sendSystemMessage(Component.literal(String.format("§7[Wand] Particles: §9blue§7 = traversed, §eyellow§7 = remaining (visible ~2s).")));
         }
      }
   }

   private static void handleVisualizeWaypoints(ServerLevel level, ServerPlayer player, int entityId) {
      MillVillager villager = resolveVillager(level, player, entityId);
      if (villager != null) {
         if (villager.getVillageId() == null) {
            player.sendSystemMessage(Component.literal("§c[Wand] Villager has no village."));
         } else {
            VillageManager vm = VillageSavedData.get(level).getVillageManager();
            Village village = vm.getVillage(villager.getVillageId());
            if (village == null) {
               player.sendSystemMessage(Component.literal("§c[Wand] Village not found."));
            } else if (village.getWaypointGraph().getWaypoints().isEmpty()) {
               player.sendSystemMessage(Component.literal("§7[Wand] Waypoint graph is empty."));
            } else {
               WaypointVisualizationManager.toggle(player, level, village);
            }
         }
      }
   }

   private static void handleClearWaypointBlocks(ServerLevel level, ServerPlayer player, int entityId) {
      MillVillager villager = resolveVillager(level, player, entityId);
      if (villager != null) {
         BlockPos origin = villager.blockPosition();
         int removed = 0;

         for (int dx = -96; dx <= 96; dx++) {
            for (int dz = -96; dz <= 96; dz++) {
               for (int dy = -8; dy <= 8; dy++) {
                  BlockPos pos = origin.offset(dx, dy, dz);
                  if (level.isLoaded(pos)) {
                     BlockState state = level.getBlockState(pos);
                     if (state.is(Blocks.REDSTONE_BLOCK) || state.is(Blocks.SEA_LANTERN)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        removed++;
                     }
                  }
               }
            }
         }

         player.sendSystemMessage(Component.literal(String.format("§a[Wand] Removed %d viz blocks (redstone + sea lanterns) within %d blocks.", removed, 96)));
      }
   }

   private static void handleNavState(ServerLevel level, ServerPlayer player, int entityId) {
      MillVillager villager = resolveVillager(level, player, entityId);
      if (villager != null) {
         VillagerNavigationManager nav = villager.getNavManager();
         GoalScheduler scheduler = villager.getGoalScheduler();
         VillagerTask task = scheduler != null ? scheduler.getCurrentTask() : null;
         boolean hasPath = villager.getNavigation().getPath() != null && !villager.getNavigation().isDone();
         BlockPos dest = nav.getDestination();
         WaypointNavigator wpn = nav.getWaypointNavigator();
         player.sendSystemMessage(Component.literal("§6═══ NavManager State ═══"));
         player.sendSystemMessage(Component.literal("§eVillager: §f" + villager.getVillagerDisplayName() + " §7at " + villager.blockPosition().toShortString()));
         player.sendSystemMessage(Component.literal("§eGoal: §f" + (task != null ? task.goalId().getPath() : "(idle)")));
         player.sendSystemMessage(Component.literal("§eDestination: §f" + (dest != null ? dest.toShortString() : "null")));
         player.sendSystemMessage(Component.literal("§eHas active path: §f" + hasPath));
         player.sendSystemMessage(Component.literal("§eAbandoned: §f" + nav.isAbandoned() + "  §eteleports: §f" + nav.getTeleportCount()));
         player.sendSystemMessage(Component.literal("§eStuck (local/long): §f" + nav.getLocalStuck() + " / " + nav.getLongDistanceStuck()));
         if (wpn != null) {
            player.sendSystemMessage(
               Component.literal(
                  "§eWaypointNavigator: §f"
                     + wpn.getState().name()
                     + " §7(idx "
                     + wpn.getDebugWaypointIndex()
                     + "/"
                     + wpn.getDebugPathSize()
                     + ", stuckTicks="
                     + wpn.getDebugStuckTicks()
                     + ", tp="
                     + wpn.getDebugTeleportCount()
                     + ")"
               )
            );
         } else {
            player.sendSystemMessage(Component.literal("§eWaypointNavigator: §7(none)"));
         }

         if (task != null) {
            Map<String, String> taskInfo = task.getNavDebugInfo();
            if (taskInfo != null && !taskInfo.isEmpty()) {
               player.sendSystemMessage(Component.literal("§eTask debug:"));

               for (Entry<String, String> entry : taskInfo.entrySet()) {
                  player.sendSystemMessage(Component.literal("  §7" + entry.getKey() + "§f=§f" + entry.getValue()));
               }
            }
         }
      }
   }

   private static void handleRebuildWaypointGraph(ServerLevel level, ServerPlayer player, int entityId) {
      MillVillager villager = resolveVillager(level, player, entityId);
      if (villager != null) {
         if (villager.getVillageId() == null) {
            player.sendSystemMessage(Component.literal("§c[Wand] Villager has no village."));
         } else {
            VillageManager vm = VillageSavedData.get(level).getVillageManager();
            Village village = vm.getVillage(villager.getVillageId());
            if (village == null) {
               player.sendSystemMessage(Component.literal("§c[Wand] Village not found."));
            } else {
               long start = System.nanoTime();
               village.rebuildWaypointGraph(level);
               long elapsedMs = (System.nanoTime() - start) / 1000000L;
               VillageWaypointGraph graph = village.getWaypointGraph();
               int nodes = graph.waypointCount();
               int edges = graph.getEdges().size();
               player.sendSystemMessage(
                  Component.literal(
                     String.format("§a[Wand] Waypoint graph rebuilt: §f%d nodes, %d edges §7(in %dms). See server log for details.", nodes, edges, elapsedMs)
                  )
               );
            }
         }
      }
   }

   private record BuildingContext(Village village, BuildingInstance building) {
   }
}
