package org.millenaire.item;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.advancement.MillAdvancements;
import org.millenaire.block.LockedChestBlock;
import org.millenaire.block.LockedChestBlockEntity;
import org.millenaire.block.VillagePanelBlock;
import org.millenaire.block.VillagePanelBlockEntity;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.network.BuildingProjectListPayload;
import org.millenaire.network.VillageTypeListPayload;
import org.millenaire.network.WandDebugMenuPayload;
import org.millenaire.quest.QuestRegistry;
import org.millenaire.village.LocalMerchantHelper;
import org.millenaire.village.PlayerCultureReputation;
import org.millenaire.village.PlayerQuestData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.millenaire.world.VillageSpawner;
import org.slf4j.Logger;

public class SummoningWandItem extends Item {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final ResourceLocation DEFAULT_VILLAGE_TYPE = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/agricole");
   private static final int MARVEL_MIN_DISTANCE = 200;

   public SummoningWandItem(Properties properties) {
      super(properties);
   }

   public InteractionResult useOn(UseOnContext context) {
      Level level = context.getLevel();
      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      }

      if (!level.dimension().equals(Level.OVERWORLD)) {
         if (context.getPlayer() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.literal("The summoning wand only works in the Overworld."));
         }

         return InteractionResult.FAIL;
      } else {
         ServerLevel serverLevel = (ServerLevel)level;
         BlockPos clickedPos = context.getClickedPos();
         BlockState clickedBlock = serverLevel.getBlockState(clickedPos);
         BlockPos spawnPos = clickedPos.above();
         if (clickedBlock.getBlock() instanceof LockedChestBlock || clickedBlock.getBlock() instanceof VillagePanelBlock) {
            if (!(context.getPlayer() instanceof ServerPlayer player && (player.hasPermissions(2) || player.server.isSingleplayer()))) {
               return InteractionResult.PASS;
            } else {
               this.openBuildingDebugMenu(serverLevel, player, clickedPos);
               return InteractionResult.SUCCESS;
            }
         } else if (clickedBlock.is(Blocks.GOLD_BLOCK)) {
            if (context.getPlayer() instanceof ServerPlayer player) {
               List<VillageType> selectable = this.getWandSelectableVillageTypes();
               if (selectable.isEmpty()) {
                  player.sendSystemMessage(Component.literal("No village type available."));
                  return InteractionResult.FAIL;
               }

               List<VillageTypeListPayload.VillageTypeEntry> entries = new ArrayList<>();
               PlayerCultureReputation cultureRep = PlayerCultureReputation.get(serverLevel);

               for (VillageType vt : selectable) {
                  String cultureKey = vt.culture().getPath();
                  Culture culture = ModCultures.getCulture(vt.culture());
                  String cultureName = culture != null ? culture.displayName() : cultureKey;
                  boolean requiresControl = vt.playerControlled();
                  boolean hasControl = requiresControl && cultureRep.hasCultureControl(player.getUUID(), vt.culture());
                  if (!requiresControl || hasControl) {
                     entries.add(
                        new VillageTypeListPayload.VillageTypeEntry(
                           cultureKey, cultureName, vt.id().getPath(), vt.name(), vt.weight(), requiresControl, hasControl
                        )
                     );
                  }
               }

               PacketDistributor.sendToPlayer(player, new VillageTypeListPayload(spawnPos, entries), new CustomPacketPayload[0]);
            }

            return InteractionResult.SUCCESS;
         } else if (clickedBlock.is(Blocks.OBSIDIAN)) {
            return this.spawnRandomVillage(serverLevel, spawnPos, context);
         } else if (context.getPlayer() instanceof ServerPlayer player && this.handleMarvelLocationPick(serverLevel, player)) {
            return InteractionResult.SUCCESS;
         } else {
            if (context.getPlayer() instanceof ServerPlayer player) {
               Village closestVillage = this.findClosestVillageInRange(serverLevel, spawnPos);
               if (closestVillage != null && closestVillage.isPlayerControlled()) {
                  if (closestVillage.isControlledBy(player.getUUID())) {
                     this.sendBuildingProjectList(serverLevel, player, closestVillage);
                     return InteractionResult.SUCCESS;
                  }

                  String villageName = closestVillage.getVillageName() != null ? closestVillage.getVillageName() : "";
                  player.sendSystemMessage(Component.translatable("ui.wand_invillagerange", new Object[]{villageName}));
                  return InteractionResult.FAIL;
               }
            }

            return this.spawnSpecificVillage(serverLevel, spawnPos, DEFAULT_VILLAGE_TYPE, context);
         }
      }
   }

   private boolean handleMarvelLocationPick(ServerLevel level, ServerPlayer player) {
      BlockPos pos = player.blockPosition();
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      if (overworld == null) {
         return false;
      }

      PlayerQuestData data = PlayerQuestData.get(overworld, QuestRegistry::get);
      if (!data.hasPlayerTag(player.getUUID(), "normanmarvel_picklocation")) {
         return false;
      }

      VillageSavedData savedData = VillageSavedData.get(overworld);
      double closestDist = this.getClosestVillageDistance(savedData, pos);
      if (closestDist < 200.0) {
         player.sendSystemMessage(
            Component.translatable("actions.normanmarvel_villagetooclose", new Object[]{String.valueOf(200), String.valueOf(Math.round(closestDist))})
         );
         return true;
      }

      ResourceLocation notredameId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/notredame");
      VillageType notredameType = ModCultures.getVillageType(notredameId);
      if (notredameType == null) {
         LOGGER.warn("Village type 'norman/notredame' not found — skipping site validation");
      } else {
         Component siteError = VillageSpawner.validateSite(overworld, pos, notredameType);
         if (siteError != null) {
            player.sendSystemMessage(Component.translatable("actions.normanmarvel_notgenerated"));
            LOGGER.info("Marvel site rejected at {} for player {}: {}", new Object[]{pos.toShortString(), player.getName().getString(), siteError.getString()});
            return true;
         }
      }

      String locationStr = pos.getX() + "/" + pos.getY() + "/" + pos.getZ();
      data.setActionData(player.getUUID(), "normanmarvel_location", locationStr);
      data.setPlayerTag(player.getUUID(), "normanmarvel_picklocation_complete");
      player.sendSystemMessage(Component.translatable("actions.normanmarvel_locationset"));
      LOGGER.info("Marvel location set at {} for player {}", locationStr, player.getName().getString());
      return true;
   }

   private double getClosestVillageDistance(VillageSavedData savedData, BlockPos pos) {
      double closest = Double.MAX_VALUE;

      for (Village v : savedData.getVillageManager().getAllVillages()) {
         double dist = Math.sqrt(v.getCenter().distSqr(pos));
         if (dist < closest) {
            closest = dist;
         }
      }

      for (VillageSavedData.LoneBuildingEntry entry : savedData.getLoneBuildingPositions()) {
         double dist = Math.sqrt(entry.pos().distSqr(pos));
         if (dist < closest) {
            closest = dist;
         }
      }

      return closest;
   }

   @Nullable
   private Village findClosestVillageInRange(ServerLevel level, BlockPos pos) {
      return VillageSavedData.get(level).getVillageManager().findNearestVillage(pos, 100.0);
   }

   private void sendBuildingProjectList(ServerLevel level, ServerPlayer player, Village village) {
      VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
      if (vt != null) {
         List<BuildingProjectListPayload.BuildingEntry> entries = new ArrayList<>();

         for (VillageType.LayoutSlot slot : vt.layout()) {
            String role = slot.role();
            if (!role.equals("centre") && !role.equals("start")) {
               BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(slot.plan());
               if (planSet != null && !planSet.isTownHall()) {
                  String name = planSet.nativeName() != null ? planSet.nativeName() : slot.plan().getPath();
                  String planIdStr = slot.plan().toString();
                  if (!entries.stream().anyMatch(e -> e.planSetId().equals(planIdStr))) {
                     entries.add(new BuildingProjectListPayload.BuildingEntry(planIdStr, name));
                  }
               }
            }
         }

         String villageName = village.getVillageName() != null ? village.getVillageName() : "";
         PacketDistributor.sendToPlayer(
            player, new BuildingProjectListPayload(village.getId().uuid().toString(), villageName, entries), new CustomPacketPayload[0]
         );
      }
   }

   private InteractionResult spawnRandomVillage(ServerLevel serverLevel, BlockPos spawnPos, UseOnContext context) {
      if (this.isVillageTooClose(serverLevel, spawnPos, context)) {
         return InteractionResult.FAIL;
      }

      List<VillageType> compatible = this.getCompatibleVillageTypes(serverLevel, spawnPos);
      if (compatible.isEmpty()) {
         if (context.getPlayer() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.literal("No village type compatible with this biome."));
         }

         return InteractionResult.FAIL;
      } else {
         int totalWeight = 0;

         for (VillageType vt : compatible) {
            totalWeight += vt.weight();
         }

         if (totalWeight <= 0) {
            if (context.getPlayer() instanceof ServerPlayer player) {
               player.sendSystemMessage(Component.literal("No village type with weight > 0 for this biome."));
            }

            return InteractionResult.FAIL;
         } else {
            int roll = ThreadLocalRandom.current().nextInt(totalWeight);
            VillageType chosen = compatible.getLast();
            int cumulative = 0;

            for (VillageType vt : compatible) {
               cumulative += vt.weight();
               if (roll < cumulative) {
                  chosen = vt;
                  break;
               }
            }

            Component failure = VillageSpawner.spawnVillage(serverLevel, spawnPos, chosen);
            if (context.getPlayer() instanceof ServerPlayer player) {
               if (failure == null) {
                  player.sendSystemMessage(
                     Component.literal("Village " + chosen.name() + " (" + chosen.id().getPath() + ") created at " + spawnPos.toShortString())
                  );
                  MillAdvancements.grant(player, MillAdvancements.SUMMONING_WAND);
               } else {
                  player.sendSystemMessage(failure);
               }
            }

            return failure == null ? InteractionResult.SUCCESS : InteractionResult.FAIL;
         }
      }
   }

   private InteractionResult spawnSpecificVillage(ServerLevel serverLevel, BlockPos spawnPos, ResourceLocation villageTypeId, UseOnContext context) {
      if (this.isVillageTooClose(serverLevel, spawnPos, context)) {
         return InteractionResult.FAIL;
      }

      VillageType villageType = ModCultures.getVillageType(villageTypeId);
      if (villageType == null) {
         if (context.getPlayer() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.literal("Village type not found: " + villageTypeId));
         }

         return InteractionResult.FAIL;
      } else {
         Component failure = VillageSpawner.spawnVillage(serverLevel, spawnPos, villageType);
         if (context.getPlayer() instanceof ServerPlayer player) {
            if (failure == null) {
               player.sendSystemMessage(Component.literal("Village " + villageType.name() + " created at " + spawnPos.toShortString()));
               MillAdvancements.grant(player, MillAdvancements.SUMMONING_WAND);
            } else {
               player.sendSystemMessage(failure);
            }
         }

         return failure == null ? InteractionResult.SUCCESS : InteractionResult.FAIL;
      }
   }

   private boolean isVillageTooClose(ServerLevel serverLevel, BlockPos spawnPos, UseOnContext context) {
      VillageManager villageManager = VillageSavedData.get(serverLevel).getVillageManager();
      if (villageManager.isWithinMinDistance(spawnPos, 100.0)) {
         if (context.getPlayer() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.literal("A village already exists within 100 blocks."));
         }

         return true;
      } else {
         return false;
      }
   }

   private void openBuildingDebugMenu(ServerLevel level, ServerPlayer player, BlockPos blockPos) {
      BlockEntity be = level.getBlockEntity(blockPos);
      BuildingId buildingId = null;
      if (be instanceof LockedChestBlockEntity chest) {
         buildingId = chest.getBuildingId();
      } else if (be instanceof VillagePanelBlockEntity panel) {
         buildingId = panel.getBuildingId();
      }

      if (buildingId != null) {
         Village village = VillageSavedData.get(level).getVillageManager().findVillageContaining(buildingId);
         if (village != null) {
            BuildingInstance building = village.findBuildingById(buildingId);
            if (building != null) {
               String header = building.getPlanId().getPath();
               if (village.getVillageName() != null) {
                  header = header + " — " + village.getVillageName();
               }

               ArrayList<WandDebugMenuPayload.ActionEntry> actions = new ArrayList<>();
               actions.add(new WandDebugMenuPayload.ActionEntry("building_info", "wand_debug.building_info"));
               BuildingPlanSet planSet = building.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(building.getPlanSetId()) : null;
               if (planSet != null && planSet.isTownHall()) {
                  actions.add(new WandDebugMenuPayload.ActionEntry("rush_construction", "wand_debug.rush_construction"));
                  if (!village.isLoneBuilding()) {
                     actions.add(new WandDebugMenuPayload.ActionEntry("fill_resources", "wand_debug.fill_resources"));
                  }
               }

               if (village.getPathManager() != null) {
                  actions.add(new WandDebugMenuPayload.ActionEntry("recalculate_paths", "wand_debug.recalculate_paths"));
                  actions.add(new WandDebugMenuPayload.ActionEntry("clear_paths", "wand_debug.clear_paths"));
               }

               if (LocalMerchantHelper.getMerchantRecord(village, building) != null) {
                  actions.add(new WandDebugMenuPayload.ActionEntry("force_merchant", "wand_debug.force_merchant"));
               }

               PacketDistributor.sendToPlayer(player, new WandDebugMenuPayload(-1, blockPos, header, actions), new CustomPacketPayload[0]);
            }
         }
      }
   }

   private void openVillagerDebugMenu(ServerLevel level, ServerPlayer player, MillVillager villager) {
      String header = villager.getVillagerDisplayName();
      VillagerType vType = ModCultures.getVillagerType(villager.getVillagerTypeId());
      ArrayList<WandDebugMenuPayload.ActionEntry> actions = new ArrayList<>();
      actions.add(new WandDebugMenuPayload.ActionEntry("villager_info", "wand_debug.villager_info"));
      actions.add(new WandDebugMenuPayload.ActionEntry("nav_state", "wand_debug.nav_state"));
      if (vType != null && vType.isChild()) {
         actions.add(new WandDebugMenuPayload.ActionEntry("grow_child", "wand_debug.grow_child"));
      } else if (vType != null && (vType.maleChild() != null || vType.femaleChild() != null)) {
         actions.add(new WandDebugMenuPayload.ActionEntry("force_child", "wand_debug.force_child"));
      }

      actions.add(new WandDebugMenuPayload.ActionEntry("visualize_path", "wand_debug.visualize_path"));
      actions.add(new WandDebugMenuPayload.ActionEntry("visualize_waypoints", "wand_debug.visualize_waypoints"));
      actions.add(new WandDebugMenuPayload.ActionEntry("rebuild_waypoint_graph", "wand_debug.rebuild_waypoint_graph"));
      actions.add(new WandDebugMenuPayload.ActionEntry("clear_waypoint_blocks", "wand_debug.clear_waypoint_blocks"));
      PacketDistributor.sendToPlayer(player, new WandDebugMenuPayload(villager.getId(), BlockPos.ZERO, header, actions), new CustomPacketPayload[0]);
   }

   public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
      if (player.level().isClientSide()) {
         return InteractionResult.SUCCESS;
      }

      if (target instanceof MillVillager villager) {
         if (player instanceof ServerPlayer serverPlayer) {
            if (!serverPlayer.hasPermissions(2) && !serverPlayer.server.isSingleplayer()) {
               return InteractionResult.PASS;
            }

            ServerLevel level = serverPlayer.serverLevel();
            this.openVillagerDebugMenu(level, serverPlayer, villager);
            return InteractionResult.SUCCESS;
         } else {
            return InteractionResult.PASS;
         }
      } else {
         return InteractionResult.PASS;
      }
   }

   private List<VillageType> getCompatibleVillageTypes(ServerLevel level, BlockPos pos) {
      Holder<Biome> biome = level.getBiome(pos);
      List<VillageType> compatible = new ArrayList<>();

      for (VillageType vt : ModCultures.getAllVillageTypes().values()) {
         if ((vt.weight() > 0 || vt.playerControlled()) && vt.spawnable() && !vt.isHamlet()) {
            if (vt.biomeTags().isEmpty()) {
               compatible.add(vt);
            } else {
               boolean matches = false;
               Iterator var8 = vt.biomeTags().iterator();

               while (true) {
                  if (var8.hasNext()) {
                     TagKey<Biome> tag = (TagKey<Biome>)var8.next();
                     if (!biome.is(tag)) {
                        continue;
                     }

                     matches = true;
                  }

                  if (matches) {
                     compatible.add(vt);
                  }
                  break;
               }
            }
         }
      }

      return compatible;
   }

   private List<VillageType> getWandSelectableVillageTypes() {
      List<VillageType> selectable = new ArrayList<>();

      for (VillageType vt : ModCultures.getAllVillageTypes().values()) {
         if (vt.spawnable() && !vt.isHamlet()) {
            selectable.add(vt);
         }
      }

      return selectable;
   }
}
