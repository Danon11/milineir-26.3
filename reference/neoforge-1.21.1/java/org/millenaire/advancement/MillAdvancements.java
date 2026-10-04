package org.millenaire.advancement;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

public final class MillAdvancements {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final DeferredRegister<CriterionTrigger<?>> TRIGGER_TYPES = DeferredRegister.create(Registries.TRIGGER_TYPE, "millenaire");
   public static final Supplier<MillTrigger> TRIGGER = TRIGGER_TYPES.register("millenaire_trigger", MillTrigger::new);
   public static final ResourceLocation FIRST_CONTACT = id("firstcontact");
   public static final ResourceLocation CRESUS = id("cresus");
   public static final ResourceLocation CHEERS = id("cheers");
   public static final ResourceLocation MASTER_FARMER = id("masterfarmer");
   public static final ResourceLocation GREAT_HUNTER = id("greathunter");
   public static final ResourceLocation HIRED = id("hired");
   public static final ResourceLocation RAINBOW = id("rainbow");
   public static final ResourceLocation SUMMONING_WAND = id("summoningwand");
   public static final ResourceLocation AMATEUR_ARCHITECT = id("amateurarchitect");
   public static final ResourceLocation MEDIEVAL_METROPOLIS = id("medievalmetropolis");
   public static final ResourceLocation EXPLORER = id("explorer");
   public static final ResourceLocation MARCO_POLO = id("marcopolo");
   public static final ResourceLocation MAGELLAN = id("magellan");
   public static final ResourceLocation PANTHEON = id("pantheon");
   public static final ResourceLocation THE_QUEST = id("thequest");
   public static final ResourceLocation MAITRE_A_PENSER = id("maitreapenser");
   public static final ResourceLocation WQ_NORMAN = id("wq_norman");
   public static final ResourceLocation WQ_INDIAN = id("wq_indian");
   public static final ResourceLocation WQ_MAYAN = id("wq_mayan");
   public static final ResourceLocation PUJA = id("puja");
   public static final ResourceLocation SACRIFICE = id("sacrifice");
   public static final ResourceLocation FRIEND_INDEED = id("friendindeed");
   public static final ResourceLocation SELF_DEFENSE = id("selfdefense");
   public static final ResourceLocation DARK_SIDE = id("darkside");
   public static final ResourceLocation ATTILA = id("attila");
   public static final ResourceLocation SCIPIO = id("scipio");
   public static final ResourceLocation VIKING = id("viking");
   public static final ResourceLocation SELJUK_ISTANBUL = id("seljuk_istanbul");
   public static final ResourceLocation BYZANTINES_NOTTODAY = id("byzantines_nottoday");
   public static final ResourceLocation MARVEL_NORMAN = id("marvel_norman");
   public static final ResourceLocation MP_WEAPON = id("mp_weapon");
   public static final ResourceLocation MP_HIREDGOON = id("mp_hiredgoon");
   public static final ResourceLocation MP_RAIDONPLAYER = id("mp_raidonplayer");
   public static final ResourceLocation MP_NEIGHBOURTRADE = id("mp_neighbourtrade");
   public static final ResourceLocation MP_FRIENDLYVILLAGE = id("mp_friendlyvillage");
   public static final List<String> ADVANCEMENT_CULTURES = List.of("norman", "indian", "mayan", "japanese", "byzantines", "inuits", "seljuk");
   public static final Map<String, ResourceLocation> REP = new HashMap<>();
   public static final Map<String, ResourceLocation> LEADER = new HashMap<>();
   public static final Map<String, ResourceLocation> COMPLETE = new HashMap<>();

   private MillAdvancements() {
   }

   public static void register(IEventBus modEventBus) {
      TRIGGER_TYPES.register(modEventBus);
   }

   public static void grant(ServerPlayer player, ResourceLocation advancementId) {
      ServerAdvancementManager serverAdvancements = player.server.getAdvancements();
      AdvancementHolder holder = serverAdvancements.get(advancementId);
      if (holder == null) {
         LOGGER.debug("Advancement not found: {}", advancementId);
      } else {
         AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
         if (!progress.isDone()) {
            for (String criterion : progress.getRemainingCriteria()) {
               player.getAdvancements().award(holder, criterion);
            }

            LOGGER.debug("Advancement {} granted to {}", advancementId, player.getName().getString());
            AdvancementStatsManager.onAdvancementEarned(player, advancementId);
         }
      }
   }

   private static ResourceLocation id(String path) {
      return ResourceLocation.fromNamespaceAndPath("millenaire", path);
   }

   static {
      for (String culture : ADVANCEMENT_CULTURES) {
         REP.put(culture, id("rep_" + culture));
         LEADER.put(culture, id("leader_" + culture));
         COMPLETE.put(culture, id("complete_" + culture));
      }
   }
}
