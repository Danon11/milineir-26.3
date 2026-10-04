package org.millenaire.village.panel;

import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;

public final class HallOfFameLoader {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String HOF_RESOURCE = "/millenaire/hof.txt";
   private static List<String> cachedData;

   private HallOfFameLoader() {
   }

   public static List<String> getHoFData() {
      if (cachedData != null) {
         return cachedData;
      }

      List<String> hofData = new ArrayList<>();

      try {
         InputStream is = HallOfFameLoader.class.getResourceAsStream("/millenaire/hof.txt");
         if (is == null) {
            LOGGER.warn("[Millenaire] HoF file not found: {}", "/millenaire/hof.txt");
            cachedData = Collections.emptyList();
            return cachedData;
         }

         String line;
         try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            while ((line = reader.readLine()) != null) {
               line = line.trim();
               if (!line.isEmpty() && !line.startsWith("//")) {
                  hofData.add(line);
               }
            }
         }
      } catch (Exception e) {
         LOGGER.error("[Millenaire] Error loading HoF", e);
      }

      cachedData = Collections.unmodifiableList(hofData);
      LOGGER.debug("[Millenaire] HoF loaded: {} entries", cachedData.size());
      return cachedData;
   }
}
