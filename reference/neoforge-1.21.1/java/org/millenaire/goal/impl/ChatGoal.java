package org.millenaire.goal.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.dialogue.Dialogue;
import org.millenaire.dialogue.DialogueLoader;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.ModelType;
import org.millenaire.entity.VillagerNavigationManager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TaskLabels;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.language.SpeechRefCodec;
import org.millenaire.village.Village;

public class ChatGoal implements VillagerGoal {
   private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "chat");
   private static final double SEARCH_RADIUS = 5.0;

   public ResourceLocation id() {
      return ID;
   }

   public int computePriority(GoalContext context) {
      return 10;
   }

   public boolean isLeisure() {
      return true;
   }

   public boolean canStart(GoalContext context) {
      return this.findPartner(context) != null;
   }

   public VillagerTask start(GoalContext context) {
      MillVillager partner = this.findPartner(context);
      return new ChatGoal.ChatTask(partner, context.villager());
   }

   @Nullable
   private MillVillager findPartner(GoalContext ctx) {
      MillVillager self = ctx.villager();
      AABB searchBox = self.getBoundingBox().inflate(5.0);
      List<MillVillager> nearby = ctx.level()
         .getEntitiesOfClass(
            MillVillager.class,
            searchBox,
            other -> other != self && other.getVillageId() != null && other.getVillageId().equals(self.getVillageId()) && isAvailableForChat(other)
         );
      return nearby.isEmpty() ? null : nearby.get(ThreadLocalRandom.current().nextInt(nearby.size()));
   }

   private static boolean isAvailableForChat(MillVillager villager) {
      GoalScheduler scheduler = villager.getGoalScheduler();
      if (scheduler == null) {
         return false;
      } else {
         return scheduler.getCurrentTask() instanceof SocialiseGoal.SocialiseTask socialiseTask ? socialiseTask.isSocialising() : false;
      }
   }

   static class ChatTask implements VillagerTask {
      private static final double CHAT_DISTANCE = 3.0;
      private static final double WALK_SPEED = 0.5;
      private static final int FALLBACK_DISPLAY_TICKS = 80;
      private final MillVillager partner;
      private final MillVillager self;
      private ChatGoal.ChatTask.Phase phase = ChatGoal.ChatTask.Phase.WALKING;
      @Nullable
      private Dialogue dialogue;
      private int lineIndex;
      private int lineTimer;
      private boolean selfIsSpeaker1 = true;
      private boolean displaySubtitles = true;
      @Nullable
      private ChatGoal.CompanionChatTask companionTask;

      ChatTask(@Nullable MillVillager partner, MillVillager self) {
         this.partner = partner;
         this.self = self;
      }

      public ResourceLocation goalId() {
         return ChatGoal.ID;
      }

      public void tick(GoalContext ctx) {
         if (this.partner != null && this.partner.isAlive()) {
            switch (this.phase) {
               case WALKING:
                  this.tickWalking(ctx);
                  break;
               case CHATTING:
                  this.tickChatting();
               case DONE:
            }
         } else {
            this.phase = ChatGoal.ChatTask.Phase.DONE;
         }
      }

      private void tickWalking(GoalContext ctx) {
         VillagerNavigationManager nav = ctx.villager().getNavManager();
         if (nav.getDestination() == null) {
            nav.navigateTo(ctx.villager(), this.partner.blockPosition(), 0.5);
         }

         if (nav.isArrived(ctx.villager(), 3.0)) {
            nav.stop(ctx.villager());
            ctx.villager().getLookControl().setLookAt(this.partner);
            this.startChatting(ctx);
         } else if (nav.isAbandoned()) {
            nav.stop(ctx.villager());
            this.phase = ChatGoal.ChatTask.Phase.DONE;
         }
      }

      private void startChatting(GoalContext ctx) {
         ResourceLocation cultureId = ctx.village().getCultureId();
         String lang = "native";
         List<Dialogue> allDialogues = DialogueLoader.getDialogues(cultureId, lang);
         if (allDialogues.isEmpty()) {
            this.phase = ChatGoal.ChatTask.Phase.DONE;
         } else {
            ServerLevel level = ctx.level();
            boolean isRaining = level.isRaining();
            List<Dialogue> eligible = new ArrayList<>();
            List<Boolean> eligibleMapping = new ArrayList<>();

            for (Dialogue d : allDialogues) {
               if (checkTags(d.tags(), isRaining)
                  && checkBuildingConditions(d, ctx.village())
                  && checkVillagerConditions(d, ctx.village())
                  && d.relations().isEmpty()) {
                  if (checkVillagerConstraint(d.v1Constraint(), this.self) && checkVillagerConstraint(d.v2Constraint(), this.partner)) {
                     eligible.add(d);
                     eligibleMapping.add(true);
                  } else if (checkVillagerConstraint(d.v1Constraint(), this.partner) && checkVillagerConstraint(d.v2Constraint(), this.self)) {
                     eligible.add(d);
                     eligibleMapping.add(false);
                  }
               }
            }

            if (eligible.isEmpty()) {
               this.phase = ChatGoal.ChatTask.Phase.DONE;
            } else {
               int totalWeight = 0;

               for (Dialogue d : eligible) {
                  totalWeight += d.weight();
               }

               if (totalWeight <= 0) {
                  this.phase = ChatGoal.ChatTask.Phase.DONE;
               } else {
                  int roll = ThreadLocalRandom.current().nextInt(totalWeight);
                  Dialogue chosen = eligible.get(0);
                  boolean chosenMapping = eligibleMapping.get(0);
                  int cumulative = 0;

                  for (int i = 0; i < eligible.size(); i++) {
                     cumulative += eligible.get(i).weight();
                     if (roll < cumulative) {
                        chosen = eligible.get(i);
                        chosenMapping = eligibleMapping.get(i);
                        break;
                     }
                  }

                  this.dialogue = chosen;
                  this.selfIsSpeaker1 = chosenMapping;
                  this.lineIndex = 0;
                  this.phase = ChatGoal.ChatTask.Phase.CHATTING;
                  AABB zone = this.self.getBoundingBox().inflate(5.0);

                  for (MillVillager nearby : ctx.level().getEntitiesOfClass(MillVillager.class, zone, e -> e != this.self && e != this.partner)) {
                     GoalScheduler s = nearby.getGoalScheduler();
                     if (s != null && s.getCurrentTask() instanceof ChatGoal.ChatTask ct && ct.phase == ChatGoal.ChatTask.Phase.CHATTING && ct.displaySubtitles
                        )
                      {
                        this.displaySubtitles = false;
                        break;
                     }
                  }

                  GoalScheduler partnerScheduler = this.partner.getGoalScheduler();
                  if (partnerScheduler != null) {
                     this.companionTask = new ChatGoal.CompanionChatTask(this.self);
                     partnerScheduler.forceTask(this.companionTask, null);
                  }

                  this.showCurrentLine();
               }
            }
         }
      }

      private static boolean checkTags(List<String> tags, boolean isRaining) {
         for (String tag : tags) {
            if ("raining".equals(tag) && !isRaining) {
               return false;
            }

            if ("notraining".equals(tag) && isRaining) {
               return false;
            }
         }

         return true;
      }

      private static boolean checkBuildingConditions(Dialogue d, Village village) {
         for (String required : d.buildings()) {
            if (!villagHasBuildingTag(village, required)) {
               return false;
            }
         }

         for (String excluded : d.notBuildings()) {
            if (villagHasBuildingTag(village, excluded)) {
               return false;
            }
         }

         return true;
      }

      private static boolean villagHasBuildingTag(Village village, String tag) {
         return !village.getBuildingsWithTag(tag).isEmpty();
      }

      private static boolean checkVillagerConditions(Dialogue d, Village village) {
         for (String required : d.villagers()) {
            if (!villageHasVillagerType(village, required)) {
               return false;
            }
         }

         for (String excluded : d.notVillagers()) {
            if (villageHasVillagerType(village, excluded)) {
               return false;
            }
         }

         return true;
      }

      private static boolean villageHasVillagerType(Village village, String typeKey) {
         for (ResourceLocation typeId : village.getVillagerTypes().values()) {
            String path = typeId.getPath();
            int slashIdx = path.indexOf(47);
            String shortType = slashIdx >= 0 ? path.substring(slashIdx + 1) : path;
            if (shortType.equals(typeKey)) {
               return true;
            }
         }

         return false;
      }

      private static boolean checkVillagerConstraint(@Nullable String constraint, MillVillager villager) {
         if (constraint != null && !constraint.isEmpty()) {
            VillagerType vType = ModCultures.getVillagerType(villager.getVillagerTypeId());
            if (vType == null) {
               return true;
            }

            for (String part : constraint.split(",")) {
               String c = part.trim();
               if (!c.isEmpty()) {
                  if ("child".equals(c) && !vType.isChild()) {
                     return false;
                  }

                  if ("adult".equals(c) && vType.isChild()) {
                     return false;
                  }

                  if ("male".equals(c) && vType.modelType() != ModelType.MALE) {
                     return false;
                  }

                  if ("female".equals(c) && vType.modelType() == ModelType.MALE) {
                     return false;
                  }

                  if (c.startsWith("vtype:")) {
                     String expected = c.substring(6);
                     String effectiveKey = getEffectiveVillagerKey(villager, vType);
                     boolean matches = false;

                     for (String vt : expected.split("-")) {
                        if (effectiveKey.equals(vt)) {
                           matches = true;
                           break;
                        }
                     }

                     if (!matches) {
                        return false;
                     }
                  }

                  if (c.startsWith("notvtype:")) {
                     String excluded = c.substring(9);
                     String effectiveKey = getEffectiveVillagerKey(villager, vType);

                     for (String vt : excluded.split("-")) {
                        if (effectiveKey.equals(vt)) {
                           return false;
                        }
                     }
                  }
               }
            }

            return true;
         } else {
            return true;
         }
      }

      private static String getEffectiveVillagerKey(MillVillager villager, VillagerType vType) {
         if (vType.isChild() && vType.altKey() != null && villager.getChildSize() >= 20) {
            return vType.altKey();
         }

         String typeId = villager.getVillagerTypeId() != null ? villager.getVillagerTypeId().getPath() : "";
         int slashIdx = typeId.lastIndexOf(47);
         return slashIdx >= 0 ? typeId.substring(slashIdx + 1) : typeId;
      }

      private void tickChatting() {
         if (this.dialogue == null) {
            this.phase = ChatGoal.ChatTask.Phase.DONE;
         } else {
            this.self.getLookControl().setLookAt(this.partner);
            this.partner.getLookControl().setLookAt(this.self);
            this.lineTimer--;
            if (this.lineTimer <= 0) {
               this.lineIndex++;
               if (this.lineIndex >= this.dialogue.lines().size()) {
                  this.self.setSpeechText("");
                  this.partner.setSpeechText("");
                  if (this.companionTask != null) {
                     this.companionTask.markDone();
                  }

                  this.phase = ChatGoal.ChatTask.Phase.DONE;
               } else {
                  this.showCurrentLine();
               }
            }
         }
      }

      private void showCurrentLine() {
         if (this.dialogue != null) {
            Dialogue.Line line = this.dialogue.lines().get(this.lineIndex);
            MillVillager speaker;
            MillVillager listener;
            if (this.selfIsSpeaker1) {
               speaker = line.speaker() == 1 ? this.self : this.partner;
               listener = line.speaker() == 1 ? this.partner : this.self;
            } else {
               speaker = line.speaker() == 1 ? this.partner : this.self;
               listener = line.speaker() == 1 ? this.self : this.partner;
            }

            ResourceLocation speakerTypeId = speaker.getVillagerTypeId();
            if (speakerTypeId != null) {
               ResourceLocation cultureId = ModCultures.extractCultureId(speakerTypeId);
               String cultureKey = cultureId != null ? cultureId.getPath() : "unknown";
               String encodedTarget = SpeechRefCodec.encodeTargetName(listener.getFirstName());
               String speechRef = "d:" + cultureKey + ":" + this.dialogue.key() + ":" + this.lineIndex + ":" + encodedTarget;
               if (this.displaySubtitles) {
                  speaker.setSpeechText(speechRef);
                  listener.setSpeechText("");
               }

               if (this.lineIndex + 1 < this.dialogue.lines().size()) {
                  int nextDelay = this.dialogue.lines().get(this.lineIndex + 1).delay();
                  int currentDelay = line.delay();
                  this.lineTimer = Math.max(nextDelay - currentDelay, 80);
               } else {
                  this.lineTimer = 80;
               }
            }
         }
      }

      public boolean isFinished() {
         return this.phase == ChatGoal.ChatTask.Phase.DONE;
      }

      public void stop(GoalContext ctx, StopReason reason) {
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
            this.self.setSpeechText("");
            if (this.partner != null && this.partner.isAlive()) {
               this.partner.setSpeechText("");
            }

            if (this.companionTask != null) {
               this.companionTask.markDone();
            }
         }
      }

      public TravelPhase getTravelPhase() {
         return TaskLabels.phaseFor(this.phase != ChatGoal.ChatTask.Phase.WALKING);
      }

      @Nullable
      public Component getGoalLabel() {
         return TaskLabels.labelForPhase(this.phase != ChatGoal.ChatTask.Phase.WALKING, "chat");
      }

      private enum Phase {
         WALKING,
         CHATTING,
         DONE;
      }
   }

   static class CompanionChatTask implements VillagerTask {
      private final MillVillager initiator;
      private boolean done;

      CompanionChatTask(MillVillager initiator) {
         this.initiator = initiator;
      }

      public ResourceLocation goalId() {
         return ChatGoal.ID;
      }

      public void tick(GoalContext ctx) {
         if (!this.initiator.isAlive()) {
            this.done = true;
         } else {
            ctx.villager().getLookControl().setLookAt(this.initiator);
         }
      }

      public boolean isFinished() {
         return this.done;
      }

      public void stop(GoalContext ctx, StopReason reason) {
         if (ctx != null) {
            ctx.villager().setSpeechText("");
         }
      }

      public TravelPhase getTravelPhase() {
         return TravelPhase.AT_DESTINATION;
      }

      @Nullable
      public Component getGoalLabel() {
         return TaskLabels.labelForPhase(true, "chat");
      }

      void markDone() {
         this.done = true;
      }
   }
}
