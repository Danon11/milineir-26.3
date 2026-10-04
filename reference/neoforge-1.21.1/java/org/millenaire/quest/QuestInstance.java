package org.millenaire.quest;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.advancement.MillAdvancements;
import org.millenaire.entity.MillVillager;
import org.millenaire.item.ItemHelper;
import org.millenaire.item.MoneyHelper;
import org.millenaire.network.QuestInstanceDestroyPayload;
import org.millenaire.network.QuestNetworkHelper;
import org.millenaire.network.QuestResultTextPayload;
import org.millenaire.village.PlayerCultureReputation;
import org.millenaire.village.PlayerQuestData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;
import org.slf4j.Logger;

public class QuestInstance {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int QUEST_LANGUAGE_BONUS = 50;
   private final Quest quest;
   private final UUID playerId;
   private final Map<String, QuestInstanceVillager> villagers;
   private int currentStep;
   private final long startTime;
   private long currentStepStart;
   private final long uniqueId;

   public QuestInstance(
      Quest quest, UUID playerId, Map<String, QuestInstanceVillager> villagers, int currentStep, long startTime, long currentStepStart, long uniqueId
   ) {
      this.quest = quest;
      this.playerId = playerId;
      this.villagers = new HashMap<>(villagers);
      this.currentStep = currentStep;
      this.startTime = startTime;
      this.currentStepStart = currentStepStart;
      this.uniqueId = uniqueId;
   }

   public Quest getQuest() {
      return this.quest;
   }

   public UUID getPlayerId() {
      return this.playerId;
   }

   public Map<String, QuestInstanceVillager> getVillagers() {
      return this.villagers;
   }

   public int getCurrentStepIndex() {
      return this.currentStep;
   }

   public long getStartTime() {
      return this.startTime;
   }

   public long getCurrentStepStart() {
      return this.currentStepStart;
   }

   public void setCurrentStepStart(long currentStepStart) {
      this.currentStepStart = currentStepStart;
   }

   public long getUniqueId() {
      return this.uniqueId;
   }

   @Nullable
   public QuestStep getCurrentStep() {
      return this.currentStep >= 0 && this.currentStep < this.quest.steps().size() ? this.quest.steps().get(this.currentStep) : null;
   }

   @Nullable
   public UUID getCurrentStepVillagerId() {
      QuestStep step = this.getCurrentStep();
      if (step == null) {
         return null;
      }

      QuestInstanceVillager qiv = this.villagers.get(step.villagerKey());
      return qiv != null ? qiv.getVillagerId() : null;
   }

   public String completeStep(ServerPlayer player, MillVillager villager) {
      QuestStep step = this.getCurrentStep();
      if (step == null) {
         return "";
      }

      UUID expectedVillagerId = this.getCurrentStepVillagerId();
      if (expectedVillagerId != null && expectedVillagerId.equals(villager.getUUID())) {
         ServerLevel level = (ServerLevel)villager.level();
         ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
         if (overworld == null) {
            return "";
         }

         PlayerQuestData questData = PlayerQuestData.get(overworld, QuestRegistry::get);
         VillageSavedData savedDataCheck = VillageSavedData.get(overworld);

         for (String tag : step.stepRequiredPlayerTags()) {
            if (!questData.hasPlayerTag(this.playerId, tag)) {
               LOGGER.debug("Step prerequisite not met: player tag '{}' required", tag);
               return "";
            }
         }

         for (String tag : step.stepForbiddenPlayerTags()) {
            if (questData.hasPlayerTag(this.playerId, tag)) {
               LOGGER.debug("Step prerequisite not met: player tag '{}' forbidden", tag);
               return "";
            }
         }

         for (String tag : step.stepRequiredGlobalTags()) {
            if (!savedDataCheck.isGlobalTagSet(tag)) {
               LOGGER.debug("Step prerequisite not met: global tag '{}' required", tag);
               return "";
            }
         }

         for (String tag : step.stepForbiddenGlobalTags()) {
            if (savedDataCheck.isGlobalTagSet(tag)) {
               LOGGER.debug("Step prerequisite not met: global tag '{}' forbidden", tag);
               return "";
            }
         }

         StringBuilder reward = new StringBuilder();

         for (Entry<QuestItemRef, Integer> entry : step.requiredGoods().entrySet()) {
            QuestItemRef ref = entry.getKey();
            int count = entry.getValue();
            if (ref.meta() == 0) {
               Item item = ItemHelper.resolve(ref.itemId());
               if (item != null) {
                  villager.getInventory().add(item, count);
                  removeItemsFromPlayer(player, item, count);
               }
            }
         }

         for (Entry<QuestItemRef, Integer> entry : step.rewardGoods().entrySet()) {
            QuestItemRef ref = entry.getKey();
            int count = entry.getValue();
            Item item = ItemHelper.resolve(ref.itemId());
            if (item != null) {
               ItemStack stack = new ItemStack(item, count);
               if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                  ItemEntity entityItem = new ItemEntity(level, villager.getX(), villager.getY() + 0.5, villager.getZ(), stack);
                  level.addFreshEntity(entityItem);
               }

               if (reward.length() > 0) {
                  reward.append(", ");
               }

               reward.append(count).append(" ").append(item.getDescription().getString());
            }
         }

         if (step.rewardMoney() > 0) {
            MoneyHelper.addDeniers(player.getInventory(), step.rewardMoney(), player);
            if (reward.length() > 0) {
               reward.append(", ");
            }

            reward.append(step.rewardMoney()).append(" ").append(Component.translatable("gui.millenaire.quest.reward_deniers").getString());
         }

         if (step.rewardReputation() > 0) {
            QuestInstanceVillager currentQiv = this.villagers.get(step.villagerKey());
            if (currentQiv != null) {
               Village village = Village.resolve(overworld, new VillageId(currentQiv.getVillageId()));
               if (village != null) {
                  village.adjustReputation(overworld, this.playerId, step.rewardReputation());
               }
            }

            if (reward.length() > 0) {
               reward.append(", ");
            }

            reward.append(step.rewardReputation()).append(" ").append(Component.translatable("gui.millenaire.quest.reward_reputation").getString());
            int experience = Math.min(step.rewardReputation() / 32, 16);
            if (experience > 0) {
               reward.append(", ").append(experience).append(" ").append(Component.translatable("gui.millenaire.quest.reward_experience").getString());
               BlockPos xpPos = villager.blockPosition().above(2);
               player.level().addFreshEntity(new ExperienceOrb(level, xpPos.getX() + 0.5, xpPos.getY(), xpPos.getZ() + 0.5, experience));
            }
         }

         QuestInstanceVillager currentQiv = this.villagers.get(step.villagerKey());
         if (currentQiv != null) {
            Village village = Village.resolve(overworld, new VillageId(currentQiv.getVillageId()));
            if (village != null) {
               PlayerCultureReputation.get(overworld).addLanguageKnowledge(this.playerId, village.getCultureId(), 50);
            }
         }

         VillageSavedData savedData = VillageSavedData.get(overworld);
         this.applyVillagerTags(step.villagerTagsSuccess(), step.clearTagsSuccess(), overworld, savedData);
         this.applyGlobalTags(step.globalTagsSuccess(), step.clearGlobalTagsSuccess(), savedData);
         this.applyPlayerTags(step.playerTagsSuccess(), step.clearPlayerTagsSuccess(), PlayerQuestData.get(overworld, QuestRegistry::get));
         this.applyActionData(step.actionDataSuccess(), PlayerQuestData.get(overworld, QuestRegistry::get));
         this.applyRelationChanges(step.relationChanges(), overworld, savedData);
         String playerName = player.getName().getString();
         String locale = QuestTextRenderer.playerLocale(player);
         String successKey = this.quest.key() + "_" + this.currentStep + "_description_success";
         String inlineSuccess = step.descriptionsSuccess().getOrDefault(locale, step.descriptionsSuccess().getOrDefault("en", ""));
         String res = QuestTextRenderer.lookupText(successKey, locale, inlineSuccess);
         res = QuestTextRenderer.substitute(res, this, playerName, overworld);
         if (reward.length() > 0) {
            if (!res.isEmpty()) {
               res = res + "<ret><ret>";
            }

            String rewardLabel = Component.translatable("gui.millenaire.quest.reward_label").getString();
            res = res + rewardLabel + " " + reward.toString();
         }

         this.currentStep++;
         if (this.currentStep >= this.quest.steps().size()) {
            MillAdvancements.grant(player, MillAdvancements.THE_QUEST);
            this.destroyQuest(PlayerQuestData.get(overworld, QuestRegistry::get), this.playerId);
            PacketDistributor.sendToPlayer(player, new QuestInstanceDestroyPayload(this.uniqueId), new CustomPacketPayload[0]);
         } else {
            this.currentStepStart = level.getDayTime();
            questData.setDirty();
            PacketDistributor.sendToPlayer(player, QuestNetworkHelper.buildSyncPayload(this, player), new CustomPacketPayload[0]);
         }

         PacketDistributor.sendToPlayer(player, new QuestResultTextPayload(this.uniqueId, res, true), new CustomPacketPayload[0]);
         return res;
      } else {
         LOGGER.warn("completeStep called with wrong villager {} (expected {})", villager.getUUID(), expectedVillagerId);
         return "";
      }
   }

   public String refuseQuest(ServerPlayer player) {
      QuestStep step = this.getCurrentStep();
      if (step == null) {
         return "";
      }

      ServerLevel level = (ServerLevel)player.level();
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      if (overworld == null) {
         return "";
      }

      String repLost = "";
      if (step.penaltyReputation() > 0) {
         QuestInstanceVillager currentQiv = this.villagers.get(step.villagerKey());
         if (currentQiv != null) {
            Village village = Village.resolve(overworld, new VillageId(currentQiv.getVillageId()));
            if (village != null) {
               village.adjustReputation(overworld, this.playerId, -step.penaltyReputation());
               repLost = " (Reputation lost: " + step.penaltyReputation() + ")";
            }
         }
      }

      VillageSavedData savedData = VillageSavedData.get(overworld);
      this.applyVillagerTags(step.villagerTagsFailure(), step.clearTagsFailure(), overworld, savedData);
      this.applyGlobalTags(step.globalTagsFailure(), step.clearGlobalTagsFailure(), savedData);
      this.applyPlayerTags(step.playerTagsFailure(), step.clearPlayerTagsFailure(), PlayerQuestData.get(overworld, QuestRegistry::get));
      String playerName = player.getName().getString();
      String locale = QuestTextRenderer.playerLocale(player);
      String refuseKey = this.quest.key() + "_" + this.currentStep + "_description_refuse";
      String inlineRefuse = step.descriptionsRefuse().getOrDefault(locale, step.descriptionsRefuse().getOrDefault("en", ""));
      String text = QuestTextRenderer.lookupText(refuseKey, locale, inlineRefuse);
      text = QuestTextRenderer.substitute(text, this, playerName, overworld);
      if (!repLost.isEmpty()) {
         if (!text.isEmpty()) {
            text = text + "\n";
         }

         text = text + repLost;
      }

      this.destroyQuest(PlayerQuestData.get(overworld, QuestRegistry::get), this.playerId);
      PacketDistributor.sendToPlayer(player, new QuestInstanceDestroyPayload(this.uniqueId), new CustomPacketPayload[0]);
      PacketDistributor.sendToPlayer(player, new QuestResultTextPayload(this.uniqueId, text, false), new CustomPacketPayload[0]);
      return text;
   }

   public void checkStatus(long worldTime, ServerPlayer player, PlayerQuestData questData) {
      QuestStep step = this.getCurrentStep();
      if (step != null) {
         if (this.currentStepStart + step.duration() * 1000L <= worldTime) {
            ServerLevel level = (ServerLevel)player.level();
            ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
            if (overworld != null) {
               for (QuestInstanceVillager qiv : this.villagers.values()) {
                  Village village = Village.resolve(overworld, new VillageId(qiv.getVillageId()));
                  if (village == null) {
                     LOGGER.debug("Dropping quest as village {} is null", qiv.getVillageId());
                     this.destroyQuest(questData, this.playerId);
                     PacketDistributor.sendToPlayer(player, new QuestInstanceDestroyPayload(this.uniqueId), new CustomPacketPayload[0]);
                     return;
                  }

                  VillagerRecord vr = village.getVillagerRecord(qiv.getVillagerId());
                  if (vr == null || vr.isKilled()) {
                     LOGGER.debug("Dropping quest as villager {} is dead or missing", qiv.getVillagerId());
                     this.destroyQuest(questData, this.playerId);
                     PacketDistributor.sendToPlayer(player, new QuestInstanceDestroyPayload(this.uniqueId), new CustomPacketPayload[0]);
                     return;
                  }
               }

               if (step.penaltyReputation() > 0) {
                  QuestInstanceVillager currentQiv = this.villagers.get(step.villagerKey());
                  if (currentQiv != null) {
                     Village village = Village.resolve(overworld, new VillageId(currentQiv.getVillageId()));
                     if (village != null) {
                        village.adjustReputation(overworld, this.playerId, -step.penaltyReputation());
                     }
                  }
               }

               VillageSavedData savedData = VillageSavedData.get(overworld);
               this.applyVillagerTags(step.villagerTagsFailure(), step.clearTagsFailure(), overworld, savedData);
               this.applyGlobalTags(step.globalTagsFailure(), step.clearGlobalTagsFailure(), savedData);
               this.applyPlayerTags(step.playerTagsFailure(), step.clearPlayerTagsFailure(), questData);
               String playerName = player.getName().getString();
               String locale = QuestTextRenderer.playerLocale(player);
               String timeupKey = this.quest.key() + "_" + this.currentStep + "_description_timeup";
               String inlineTimeup = step.descriptionsTimeUp().getOrDefault(locale, step.descriptionsTimeUp().getOrDefault("en", ""));
               String timeupText = QuestTextRenderer.lookupText(timeupKey, locale, inlineTimeup);
               timeupText = QuestTextRenderer.substitute(timeupText, this, playerName, overworld);
               if (timeupText.isEmpty() && step.penaltyReputation() > 0) {
                  timeupText = Component.translatable("gui.millenaire.quest.timeout", new Object[]{step.penaltyReputation()}).getString();
               } else if (!timeupText.isEmpty() && step.penaltyReputation() > 0) {
                  timeupText = timeupText
                     + " ("
                     + Component.translatable("gui.millenaire.quest.rep_lost", new Object[]{step.penaltyReputation()}).getString()
                     + ")";
               }

               if (!timeupText.isEmpty()) {
                  player.sendSystemMessage(Component.literal(timeupText).withStyle(ChatFormatting.RED));
               }

               this.destroyQuest(questData, this.playerId);
               PacketDistributor.sendToPlayer(player, new QuestInstanceDestroyPayload(this.uniqueId), new CustomPacketPayload[0]);
               return;
            }
         }
      }
   }

   public void destroyQuest(PlayerQuestData questData, UUID playerId) {
      questData.removeQuest(playerId, this);
   }

   private void applyVillagerTags(List<VillagerTagAction> setTags, List<VillagerTagAction> clearTags, ServerLevel overworld, VillageSavedData savedData) {
      for (VillagerTagAction vta : setTags) {
         String tag = this.playerId + "_" + vta.tag();
         QuestInstanceVillager qiv = this.villagers.get(vta.villagerKey());
         if (qiv != null) {
            Village village = savedData.getVillageManager().getVillage(new VillageId(qiv.getVillageId()));
            if (village != null) {
               VillagerRecord vr = village.getVillagerRecord(qiv.getVillagerId());
               if (vr != null) {
                  vr.addQuestTag(tag);
                  savedData.setDirty();
                  LOGGER.debug("Set quest tag '{}' on villager {}", tag, vr.getFirstName());
               }
            }
         }
      }

      for (VillagerTagAction vta : clearTags) {
         String tag = this.playerId + "_" + vta.tag();
         QuestInstanceVillager qiv = this.villagers.get(vta.villagerKey());
         if (qiv != null) {
            Village village = savedData.getVillageManager().getVillage(new VillageId(qiv.getVillageId()));
            if (village != null) {
               VillagerRecord vr = village.getVillagerRecord(qiv.getVillagerId());
               if (vr != null) {
                  vr.removeQuestTag(tag);
                  savedData.setDirty();
                  LOGGER.debug("Cleared quest tag '{}' on villager {}", tag, vr.getFirstName());
               }
            }
         }
      }
   }

   private void applyGlobalTags(List<String> setTags, List<String> clearTags, VillageSavedData savedData) {
      for (String tag : setTags) {
         savedData.setGlobalTag(tag);
      }

      for (String tag : clearTags) {
         savedData.clearGlobalTag(tag);
      }
   }

   private void applyPlayerTags(List<String> setTags, List<String> clearTags, PlayerQuestData questData) {
      for (String tag : setTags) {
         questData.setPlayerTag(this.playerId, tag);
      }

      for (String tag : clearTags) {
         questData.clearPlayerTag(this.playerId, tag);
      }
   }

   private void applyActionData(List<ActionDataEntry> entries, PlayerQuestData questData) {
      for (ActionDataEntry entry : entries) {
         questData.setActionData(this.playerId, entry.key(), entry.value());
      }
   }

   private void applyRelationChanges(List<RelationChange> changes, ServerLevel overworld, VillageSavedData savedData) {
      for (RelationChange change : changes) {
         QuestInstanceVillager qiv1 = this.villagers.get(change.firstVillager());
         QuestInstanceVillager qiv2 = this.villagers.get(change.secondVillager());
         if (qiv1 == null) {
            LOGGER.error("Unknown villager reference in relation change: {}", change.firstVillager());
         } else if (qiv2 == null) {
            LOGGER.error("Unknown villager reference in relation change: {}", change.secondVillager());
         } else {
            Village village1 = savedData.getVillageManager().getVillage(new VillageId(qiv1.getVillageId()));
            VillageId villageId2 = new VillageId(qiv2.getVillageId());
            if (village1 != null) {
               village1.adjustRelationSymmetric(overworld, villageId2, change.change(), false);
               LOGGER.debug("Adjusted relation (symmetric) between {} and {} by {}", new Object[]{village1.getVillageName(), villageId2, change.change()});
            }
         }
      }
   }

   private static void removeItemsFromPlayer(ServerPlayer player, Item item, int count) {
      int remaining = count;

      for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (stack.is(item)) {
            int toRemove = Math.min(remaining, stack.getCount());
            stack.shrink(toRemove);
            remaining -= toRemove;
            if (remaining <= 0) {
               break;
            }
         }
      }
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putString("quest", this.quest.key());
      tag.putInt("step", this.currentStep);
      tag.putLong("startTime", this.startTime);
      tag.putLong("stepStart", this.currentStepStart);
      tag.putLong("uniqueId", this.uniqueId);
      ListTag villagersList = new ListTag();

      for (Entry<String, QuestInstanceVillager> entry : this.villagers.entrySet()) {
         CompoundTag villagerTag = entry.getValue().save();
         villagerTag.putString("key", entry.getKey());
         villagersList.add(villagerTag);
      }

      tag.put("villagers", villagersList);
      return tag;
   }

   @Nullable
   public static QuestInstance load(CompoundTag tag, UUID playerId, Function<String, Quest> questLookup) {
      String questKey = tag.getString("quest");
      Quest quest = questLookup.apply(questKey);
      if (quest == null) {
         LOGGER.warn("Unknown quest type '{}' in saved data — quest instance dropped", questKey);
         return null;
      }

      int step = tag.getInt("step");
      if (step >= 0 && step < quest.steps().size()) {
         long startTime = tag.getLong("startTime");
         long stepStart = tag.getLong("stepStart");
         long uniqueId = tag.getLong("uniqueId");
         Map<String, QuestInstanceVillager> villagers = new HashMap<>();
         ListTag villagersList = tag.getList("villagers", 10);

         for (int i = 0; i < villagersList.size(); i++) {
            CompoundTag villagerTag = villagersList.getCompound(i);
            String key = villagerTag.getString("key");
            villagers.put(key, QuestInstanceVillager.load(villagerTag));
         }

         return new QuestInstance(quest, playerId, villagers, step, startTime, stepStart, uniqueId);
      } else {
         LOGGER.warn("Quest '{}' has out-of-range step {} (max {}) — quest instance dropped", new Object[]{questKey, step, quest.steps().size() - 1});
         return null;
      }
   }
}
