package org.millenaire.quest;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.network.QuestNetworkHelper;
import org.millenaire.village.PlayerCultureReputation;
import org.millenaire.village.PlayerQuestData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;
import org.millenaire.world.VillageSpawner;
import org.slf4j.Logger;

public final class QuestManager {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int TICK_INTERVAL = 1000;
   private static final double NEARBY_VILLAGE_MAX_DISTANCE = 2000.0;

   private QuestManager() {
   }

   public static void tickQuests(ServerPlayer player, ServerLevel level) {
      long worldTime = level.getDayTime();
      if (level.isDay()) {
         if (worldTime % 1000L == 0L) {
            testQuestsNow(player, level, false);
         }
      }
   }

   public static void testQuestsNow(ServerPlayer player, ServerLevel level, boolean skipChanceRoll) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      if (overworld != null) {
         PlayerQuestData data = PlayerQuestData.get(overworld, QuestRegistry::get);
         UUID playerId = player.getUUID();
         long worldTime = level.getDayTime();
         List<QuestInstance> active = new ArrayList<>(data.getActiveQuests(playerId));

         for (int i = active.size() - 1; i >= 0; i--) {
            try {
               active.get(i).checkStatus(worldTime, player, data);
            } catch (Exception e) {
               LOGGER.error("Quest check error, destroying: {}", active.get(i).getQuest().key(), e);
               active.get(i).destroyQuest(data, playerId);
            }
         }

         VillageSavedData savedData = VillageSavedData.get(overworld);

         for (Quest quest : QuestRegistry.all()) {
            tryInstantiate(quest, player, overworld, data, savedData, skipChanceRoll);
         }
      }
   }

   public static boolean tryInstantiateForced(Quest quest, ServerPlayer player, ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      if (overworld == null) {
         return false;
      }

      PlayerQuestData data = PlayerQuestData.get(overworld, QuestRegistry::get);
      VillageSavedData savedData = VillageSavedData.get(overworld);
      int before = data.getActiveQuests(player.getUUID()).size();
      tryInstantiate(quest, player, overworld, data, savedData, true);
      return data.getActiveQuests(player.getUUID()).size() > before;
   }

   static void tryInstantiate(Quest quest, ServerPlayer player, ServerLevel overworld, PlayerQuestData data, VillageSavedData savedData, boolean skipChanceRoll) {
      if (skipChanceRoll || !(Math.random() > quest.chancePerHour())) {
         UUID playerId = player.getUUID();
         int count = 0;

         for (QuestInstance qi : data.getActiveQuests(playerId)) {
            if (qi.getQuest().key().equals(quest.key())) {
               count++;
            }
         }

         if (count < quest.maxSimultaneous()) {
            for (String tag : quest.globalTagsRequired()) {
               if (!savedData.isGlobalTagSet(tag)) {
                  return;
               }
            }

            for (String tag : quest.globalTagsForbidden()) {
               if (savedData.isGlobalTagSet(tag)) {
                  return;
               }
            }

            for (String tag : quest.playerTagsRequired()) {
               if (!data.hasPlayerTag(playerId, tag)) {
                  return;
               }
            }

            for (String tag : quest.playerTagsForbidden()) {
               if (data.hasPlayerTag(playerId, tag)) {
                  return;
               }
            }

            LOGGER.debug("Testing quest {} for player {}", quest.key(), player.getName().getString());
            QuestVillagerDef startingDef = quest.villagerDefs().get(0);
            List<Map<String, QuestInstanceVillager>> possibleVillagers = new ArrayList<>();

            for (Village village : savedData.getVillageManager().getAllVillages()) {
               if (village.isActive()) {
                  int villageRep = village.getReputation().get(playerId);
                  int cultureRep = PlayerCultureReputation.get(overworld).get(playerId, village.getCultureId());
                  if (villageRep + cultureRep >= quest.minReputation()) {
                     LOGGER.debug("Looking for starting villager in: {}", village.getVillageName());

                     for (Entry<UUID, VillagerRecord> entry : village.getVillagerRecords().entrySet()) {
                        VillagerRecord vr = entry.getValue();
                        if (testVillager(startingDef, playerId, vr, data)) {
                           Map<String, QuestInstanceVillager> villagers = new HashMap<>();
                           villagers.put(startingDef.key(), new QuestInstanceVillager(vr.getUuid(), village.getId().uuid()));
                           boolean error = false;
                           LOGGER.debug("Found possible starting villager: {} ({})", vr.getFirstName(), vr.getVillagerTypeId());

                           label240:
                           for (QuestVillagerDef qvd : quest.villagerDefs()) {
                              if (error) {
                                 break;
                              }

                              if (qvd != startingDef) {
                                 QuestInstanceVillager relatedQiv = villagers.get(qvd.relatedTo());
                                 if (relatedQiv == null) {
                                    error = true;
                                    break;
                                 }

                                 VillagerRecord relatedRecord = findVillagerRecord(savedData, relatedQiv.getVillagerId());
                                 if (relatedRecord == null) {
                                    error = true;
                                    break;
                                 }

                                 Village relatedVillage = savedData.getVillageManager().getVillage(new VillageId(relatedQiv.getVillageId()));
                                 if (relatedVillage == null) {
                                    error = true;
                                    break;
                                 }

                                 String relation = qvd.relation();
                                 if (relation == null) {
                                    error = true;
                                    break;
                                 }

                                 Set<UUID> assignedUuids = new HashSet<>();

                                 for (QuestInstanceVillager qiv : villagers.values()) {
                                    assignedUuids.add(qiv.getVillagerId());
                                 }

                                 switch (relation) {
                                    case "samevillage": {
                                       List<VillagerRecord> candidates = new ArrayList<>();

                                       for (VillagerRecord vr2 : relatedVillage.getVillagerRecords().values()) {
                                          if (!assignedUuids.contains(vr2.getUuid())
                                             && (
                                                vr2.getHomeBuilding() == null
                                                   || relatedRecord.getHomeBuilding() == null
                                                   || !vr2.getHomeBuilding().equals(relatedRecord.getHomeBuilding())
                                             )
                                             && testVillager(qvd, playerId, vr2, data)) {
                                             candidates.add(vr2);
                                          }
                                       }

                                       if (!candidates.isEmpty()) {
                                          VillagerRecord chosen = candidates.get((int)(Math.random() * candidates.size()));
                                          villagers.put(qvd.key(), new QuestInstanceVillager(chosen.getUuid(), relatedVillage.getId().uuid()));
                                       } else {
                                          error = true;
                                       }
                                       break;
                                    }
                                    case "samehouse": {
                                       List<VillagerRecord> candidates = new ArrayList<>();

                                       for (VillagerRecord vr2 : relatedVillage.getVillagerRecords().values()) {
                                          if (!assignedUuids.contains(vr2.getUuid())
                                             && vr2.getHomeBuilding() != null
                                             && relatedRecord.getHomeBuilding() != null
                                             && vr2.getHomeBuilding().equals(relatedRecord.getHomeBuilding())
                                             && testVillager(qvd, playerId, vr2, data)) {
                                             candidates.add(vr2);
                                          }
                                       }

                                       if (!candidates.isEmpty()) {
                                          VillagerRecord chosen = candidates.get((int)(Math.random() * candidates.size()));
                                          villagers.put(qvd.key(), new QuestInstanceVillager(chosen.getUuid(), relatedVillage.getId().uuid()));
                                       } else {
                                          error = true;
                                       }
                                       break;
                                    }
                                    case "nearbyvillage":
                                    case "anyvillage": {
                                       List<QuestInstanceVillager> candidates = new ArrayList<>();
                                       Iterator chosen = savedData.getVillageManager().getAllVillages().iterator();

                                       while (true) {
                                          Village v2;
                                          while (true) {
                                             if (!chosen.hasNext()) {
                                                if (!candidates.isEmpty()) {
                                                   villagers.put(qvd.key(), candidates.get((int)(Math.random() * candidates.size())));
                                                } else {
                                                   error = true;
                                                }
                                                continue label240;
                                             }

                                             v2 = (Village)chosen.next();
                                             if (!v2.getId().equals(relatedVillage.getId())) {
                                                if (!"nearbyvillage".equals(relation)) {
                                                   break;
                                                }

                                                double dist = Math.sqrt(v2.getCenter().distSqr(relatedVillage.getCenter()));
                                                if (!(dist >= 2000.0)) {
                                                   break;
                                                }
                                             }
                                          }

                                          for (VillagerRecord vr2 : v2.getVillagerRecords().values()) {
                                             if (!assignedUuids.contains(vr2.getUuid()) && testVillager(qvd, playerId, vr2, data)) {
                                                candidates.add(new QuestInstanceVillager(vr2.getUuid(), v2.getId().uuid()));
                                             }
                                          }
                                       }
                                    }
                                    default:
                                       LOGGER.error("Unknown relation: {}", relation);
                                       error = true;
                                 }
                              }
                           }

                           if (!error) {
                              possibleVillagers.add(villagers);
                              LOGGER.debug("Found all the villagers needed: {}", villagers.size());
                           }
                        }
                     }
                  }
               }
            }

            if (!possibleVillagers.isEmpty()) {
               Map<String, QuestInstanceVillager> selectedOption = possibleVillagers.get((int)(Math.random() * possibleVillagers.size()));
               long worldTime = overworld.getDayTime();
               long uniqueId = (long)(Math.random() * 9.223372E18F);
               QuestInstance qi = new QuestInstance(quest, playerId, selectedOption, 0, worldTime, worldTime, uniqueId);
               data.addQuest(playerId, qi);
               PacketDistributor.sendToPlayer(player, QuestNetworkHelper.buildSyncPayload(qi, player), new CustomPacketPayload[0]);
               LOGGER.info(
                  "Quest '{}' instantiated for player {} with {} villagers", new Object[]{quest.key(), player.getName().getString(), selectedOption.size()}
               );
            }
         }
      }
   }

   private static boolean testVillager(QuestVillagerDef def, UUID playerId, VillagerRecord vr, PlayerQuestData data) {
      if (data.isVillagerInQuest(playerId, vr.getUuid())) {
         return false;
      }

      if (!def.villagerTypes().isEmpty()) {
         String typeIdPath = vr.getVillagerTypeId() != null ? vr.getVillagerTypeId().getPath() : "";
         if (!def.villagerTypes().contains(typeIdPath)) {
            return false;
         }
      }

      for (String tag : def.requiredTags()) {
         String tagPlayer = playerId + "_" + tag;
         if (!vr.hasQuestTag(tagPlayer)) {
            return false;
         }
      }

      for (String tag : def.forbiddenTags()) {
         String tagPlayer = playerId + "_" + tag;
         if (vr.hasQuestTag(tagPlayer)) {
            return false;
         }
      }

      return true;
   }

   private static VillagerRecord findVillagerRecord(VillageSavedData savedData, UUID villagerId) {
      for (Village village : savedData.getVillageManager().getAllVillages()) {
         VillagerRecord vr = village.getVillagerRecord(villagerId);
         if (vr != null) {
            return vr;
         }
      }

      return null;
   }

   public static void tickSpecialActions(ServerPlayer player, ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      if (overworld != null) {
         PlayerQuestData data = PlayerQuestData.get(overworld, QuestRegistry::get);
         UUID playerId = player.getUUID();
         checkNormanMarvelPickLocationComplete(playerId, data);
         if (level.getGameTime() % 10L == 0L) {
            normanMarvelGenerateMarvel(playerId, data, overworld);
         }
      }
   }

   private static void checkNormanMarvelPickLocationComplete(UUID playerId, PlayerQuestData data) {
   }

   private static void normanMarvelGenerateMarvel(UUID playerId, PlayerQuestData data, ServerLevel overworld) {
      if (data.hasPlayerTag(playerId, "normanmarvel_generate")) {
         String locationStr = data.getActionData(playerId, "normanmarvel_location");
         if (locationStr == null) {
            LOGGER.warn("normanmarvel_generate tag set but no normanmarvel_location action data for player {}", playerId);
            data.clearPlayerTag(playerId, "normanmarvel_generate");
         } else {
            String[] parts = locationStr.split("/");
            if (parts.length != 3) {
               LOGGER.warn("Invalid normanmarvel_location format '{}' for player {}", locationStr, playerId);
               data.clearPlayerTag(playerId, "normanmarvel_generate");
            } else {
               try {
                  int x = Integer.parseInt(parts[0]);
                  int y = Integer.parseInt(parts[1]);
                  int z = Integer.parseInt(parts[2]);
                  BlockPos pos = new BlockPos(x, y, z);
                  ResourceLocation notredameId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/notredame");
                  VillageType notredameType = ModCultures.getVillageType(notredameId);
                  if (notredameType == null) {
                     LOGGER.warn("Village type 'norman/notredame' not found — cannot generate marvel");
                     data.clearPlayerTag(playerId, "normanmarvel_generate");
                     return;
                  }

                  Component failure = VillageSpawner.spawnVillage(overworld, pos, notredameType);
                  if (failure == null) {
                     data.clearPlayerTag(playerId, "normanmarvel_picklocation");
                     data.clearPlayerTag(playerId, "normanmarvel_picklocation_complete");
                     data.clearPlayerTag(playerId, "normanmarvel_generate");
                     data.setActionData(playerId, "normanmarvel_villagepos", x + "/" + y + "/" + z);
                     LOGGER.info("Marvel village spawned at {},{},{} for player {}", new Object[]{x, y, z, playerId});
                     ServerPlayer player = overworld.getServer().getPlayerList().getPlayer(playerId);
                     if (player != null) {
                        player.sendSystemMessage(Component.translatable("actions.normanmarvel_generated"));
                     }

                     return;
                  }

                  LOGGER.warn("Failed to spawn marvel village at {},{},{}: {}", new Object[]{x, y, z, failure.getString()});
                  data.clearPlayerTag(playerId, "normanmarvel_picklocation_complete");
                  data.clearPlayerTag(playerId, "normanmarvel_generate");
                  ServerPlayer player = overworld.getServer().getPlayerList().getPlayer(playerId);
                  if (player != null) {
                     player.sendSystemMessage(Component.translatable("actions.normanmarvel_notgenerated"));
                  }
               } catch (NumberFormatException e) {
                  LOGGER.warn("Invalid coordinates in normanmarvel_location '{}' for player {}", locationStr, playerId);
                  data.clearPlayerTag(playerId, "normanmarvel_generate");
               }
            }
         }
      }
   }
}
