package org.millenaire.client.gui;

import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.millenaire.language.LocaleResolver;
import org.slf4j.Logger;

public final class HelpContentLoader {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int NUM_CHAPTERS = 13;

   private HelpContentLoader() {
   }

   private static Optional<Resource> resolveHelpResource(int chapter) {
      String langCode = Minecraft.getInstance().getLanguageManager().getSelected();
      ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
      Set<String> supported = new LinkedHashSet<>();
      if (hasHelpFile(resourceManager, "en", chapter)) {
         supported.add("en");
      }

      for (String lc : Minecraft.getInstance().getLanguageManager().getLanguages().keySet()) {
         if (hasHelpFile(resourceManager, lc, chapter)) {
            supported.add(lc);
         }
      }

      String resolved = LocaleResolver.resolveSupported(langCode, supported);
      if (resolved == null) {
         resolved = "en";
      }

      return resourceManager.getResource(ResourceLocation.fromNamespaceAndPath("millenaire", "help/" + resolved + "/help_" + chapter + ".txt"));
   }

   private static boolean hasHelpFile(ResourceManager rm, String locale, int chapter) {
      return rm.getResource(ResourceLocation.fromNamespaceAndPath("millenaire", "help/" + locale + "/help_" + chapter + ".txt")).isPresent();
   }

   public static List<List<String>> loadChapter(int chapter) {
      if (chapter >= 1 && chapter <= 13) {
         try {
            Optional<Resource> res = resolveHelpResource(chapter);
            if (res.isEmpty()) {
               LOGGER.warn("Help file not found for chapter {}", chapter);
               return List.of(List.of("Help file not found for chapter " + chapter));
            }

            List<String> allLines = new ArrayList<>();

            String line;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(res.get().open(), StandardCharsets.UTF_8))) {
               while ((line = reader.readLine()) != null) {
                  allLines.add(line);
               }
            }

            int headerEnd = Math.min(allLines.size(), 5);

            for (int var12 = headerEnd - 1; var12 >= 0; var12--) {
               if (allLines.get(var12).trim().startsWith("version:")) {
                  allLines.remove(var12);
               }
            }

            while (!allLines.isEmpty() && allLines.get(0).trim().isEmpty()) {
               allLines.remove(0);
            }

            List<List<String>> pages = new ArrayList<>();
            List<String> currentPage = new ArrayList<>();

            for (String linex : allLines) {
               if ("NEW_PAGE".equals(linex.trim())) {
                  if (!currentPage.isEmpty()) {
                     pages.add(currentPage);
                     currentPage = new ArrayList<>();
                  }
               } else {
                  currentPage.add(processColorTags(linex));
               }
            }

            if (!currentPage.isEmpty()) {
               pages.add(currentPage);
            }

            return pages;
         } catch (Exception e) {
            LOGGER.error("Failed to load help chapter {}", chapter, e);
            return List.of(List.of("Error loading help chapter " + chapter));
         }
      } else {
         return List.of();
      }
   }

   private static String processColorTags(String line) {
      return line.replace("<darkblue>", "§1")
         .replace("<blue>", "§9")
         .replace("<darkgreen>", "§2")
         .replace("<green>", "§a")
         .replace("<darkred>", "§4")
         .replace("<red>", "§c")
         .replace("<black>", "§0")
         .replace("<gray>", "§7")
         .replace("<darkgray>", "§8")
         .replace("<gold>", "§6")
         .replace("<yellow>", "§e")
         .replace("<white>", "§f");
   }
}
