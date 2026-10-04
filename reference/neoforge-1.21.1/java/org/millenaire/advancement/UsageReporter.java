package org.millenaire.advancement;

import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.millenaire.Millenaire;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.village.PlayerCultureReputation;
import org.slf4j.Logger;

public final class UsageReporter {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String REPORT_URL = "http://millenaire.org/php/mlnuse.php";
   private static final AtomicBoolean reported = new AtomicBoolean(false);

   private UsageReporter() {
   }

   public static void tryReport(MinecraftServer server) {
      if (!reported.get()) {
         if ((Boolean)MillenaireServerConfig.SERVER.sendStatistics.get()) {
            if (!server.getPlayerList().getPlayers().isEmpty()) {
               if (reported.compareAndSet(false, true)) {
                  ServerLevel overworld = server.getLevel(Level.OVERWORLD);
                  if (overworld != null) {
                     try {
                        String url = buildUrl(server, overworld);
                        Thread reporter = new Thread(() -> doReport(url), "Millenaire-UsageReport");
                        reporter.setDaemon(true);
                        reporter.start();
                     } catch (Exception e) {
                        LOGGER.debug("Failed to build usage report URL: {}", e.getMessage());
                     }
                  }
               }
            }
         }
      }
   }

   private static String buildUrl(MinecraftServer server, ServerLevel overworld) {
      AdvancementStatsManager stats = AdvancementStatsManager.get(overworld);
      PlayerCultureReputation cultureRep = PlayerCultureReputation.get(overworld);
      String mode = server.isDedicatedServer() ? "s" : "l";
      int nbPlayers = Math.max(1, server.getPlayerCount());
      String os = encode(System.getProperty("os.name", "unknown"));
      StringBuilder survivalBuilder = new StringBuilder();
      StringBuilder creativeBuilder = new StringBuilder();
      long totalExp = 0L;
      long validationKey = 0L;
      ServerPlayer firstPlayer = server.getPlayerList().getPlayers().isEmpty() ? null : (ServerPlayer)server.getPlayerList().getPlayers().getFirst();
      if (firstPlayer != null) {
         UUID playerId = firstPlayer.getUUID();
         buildAdvancementString(stats.getSurvivalStats(playerId), survivalBuilder);
         buildAdvancementString(stats.getCreativeStats(playerId), creativeBuilder);
         validationKey = stats.computeKey(playerId);

         for (String culture : MillAdvancements.ADVANCEMENT_CULTURES) {
            ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", culture);
            totalExp += Math.abs(cultureRep.get(playerId, cultureId));
         }
      }

      StringBuilder url = new StringBuilder("http://millenaire.org/php/mlnuse.php");
      url.append("?uid=").append(getOrCreateUid(overworld));
      url.append("&mlnversion=").append(encode(Millenaire.getModVersion()));
      url.append("&mode=").append(mode);
      url.append("&lang=").append(encode(getLanguage(firstPlayer)));
      url.append("&nbplayers=").append(nbPlayers);
      url.append("&os=").append(os);
      url.append("&totalexp=").append(totalExp);
      url.append("&advancementssurvival=").append(encode(survivalBuilder.toString()));
      url.append("&advancementscreative=").append(encode(creativeBuilder.toString()));
      url.append("&validation=").append(validationKey);
      if (firstPlayer != null && (Boolean)MillenaireServerConfig.SERVER.sendPlayerName.get()) {
         url.append("&login=").append(encode(firstPlayer.getName().getString()));
      }

      return url.toString();
   }

   private static void buildAdvancementString(Set<String> earned, StringBuilder sb) {
      StringJoiner joiner = new StringJoiner(",");

      for (String field : getAllAdvancementKeys()) {
         joiner.add(field + ":" + earned.contains(field));
      }

      sb.append(joiner);
   }

   private static List<String> getAllAdvancementKeys() {
      List<String> keys = new ArrayList<>();
      keys.add("firstcontact");
      keys.add("cresus");
      keys.add("summoningwand");
      keys.add("amateurarchitect");
      keys.add("medievalmetropolis");
      keys.add("thequest");
      keys.add("maitreapenser");
      keys.add("explorer");
      keys.add("marcopolo");
      keys.add("magellan");
      keys.add("selfdefense");
      keys.add("pantheon");
      keys.add("darkside");
      keys.add("scipio");
      keys.add("attila");
      keys.add("viking");
      keys.add("cheers");
      keys.add("hired");
      keys.add("masterfarmer");
      keys.add("greathunter");
      keys.add("friendindeed");
      keys.add("rainbow");
      keys.add("seljuk_istanbul");
      keys.add("byzantines_nottoday");
      keys.add("mp_weapon");
      keys.add("mp_hiredgoon");
      keys.add("mp_raidonplayer");
      keys.add("mp_neighbourtrade");
      keys.add("mp_friendlyvillage");
      keys.add("wq_indian");
      keys.add("wq_norman");
      keys.add("wq_mayan");
      keys.add("puja");
      keys.add("sacrifice");
      keys.add("marvel_norman");

      for (String culture : MillAdvancements.ADVANCEMENT_CULTURES) {
         keys.add("rep_" + culture);
         keys.add("complete_" + culture);
         keys.add("leader_" + culture);
      }

      return keys;
   }

   private static long getOrCreateUid(ServerLevel overworld) {
      AdvancementStatsManager stats = AdvancementStatsManager.get(overworld);
      return stats.getOrCreateUid();
   }

   private static String getLanguage(@Nullable ServerPlayer player) {
      if (player != null) {
         String locale = player.clientInformation().language();
         if (locale != null && !locale.isEmpty()) {
            return locale;
         }
      }

      Locale jvmLocale = Locale.getDefault();
      String country = jvmLocale.getCountry();
      return country.isEmpty() ? jvmLocale.getLanguage() : jvmLocale.getLanguage() + "_" + country;
   }

   private static String encode(String value) {
      return URLEncoder.encode(value, StandardCharsets.UTF_8);
   }

   private static void doReport(String url) {
      try {
         InputStream stream = URI.create(url).toURL().openStream();
         stream.close();
         LOGGER.debug("Usage report sent successfully");
      } catch (Exception e) {
         LOGGER.debug("Usage report failed (non-critical): {}", e.getMessage());
      }
   }

   public static void resetForTesting() {
      reported.set(false);
   }
}
