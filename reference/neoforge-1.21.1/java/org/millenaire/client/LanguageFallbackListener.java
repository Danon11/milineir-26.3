package org.millenaire.client;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.locale.Language;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;

public class LanguageFallbackListener implements ResourceManagerReloadListener {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Field I18N_LANGUAGE_FIELD;

   public void onResourceManagerReload(ResourceManager resourceManager) {
      LanguageManager langManager = Minecraft.getInstance().getLanguageManager();
      String currentCode = langManager.getSelected();
      if (!"en_us".equals(currentCode)) {
         String parentCode = findParentLocale(langManager, currentCode);
         if (parentCode != null) {
            LanguageInfo currentInfo = langManager.getLanguage(currentCode);
            boolean rtl = currentInfo != null && currentInfo.bidirectional();
            List<String> chain = new ArrayList<>(3);
            chain.add("en_us");
            chain.add(parentCode);
            chain.add(currentCode);
            ClientLanguage clientLanguage = ClientLanguage.loadFrom(resourceManager, chain, rtl);
            Language.inject(clientLanguage);
            if (I18N_LANGUAGE_FIELD != null) {
               try {
                  I18N_LANGUAGE_FIELD.set(null, clientLanguage);
               } catch (ReflectiveOperationException e) {
                  LOGGER.warn("Failed to update I18n.language field", e);
               }
            }

            LOGGER.debug("Language fallback chain: {} -> {} -> en_us", currentCode, parentCode);
         }
      }
   }

   private static String findParentLocale(LanguageManager langManager, String currentCode) {
      String[] parts = currentCode.split("_");
      if (parts.length != 2) {
         return null;
      }

      String langPrefix = parts[0];
      String candidate = langPrefix + "_" + langPrefix;
      if (!candidate.equals(currentCode) && langManager.getLanguage(candidate) != null) {
         return candidate;
      }

      for (String code : langManager.getLanguages().keySet()) {
         if (!code.equals(currentCode) && code.startsWith(langPrefix + "_")) {
            return code;
         }
      }

      return null;
   }

   static {
      Field field = null;

      try {
         Class<?> i18nClass = Class.forName("net.minecraft.client.resources.language.I18n");
         field = i18nClass.getDeclaredField("language");
         field.setAccessible(true);
      } catch (ReflectiveOperationException e) {
         LOGGER.warn("Could not access I18n.language field; regional fallback may not apply to I18n.get() calls", e);
      }

      I18N_LANGUAGE_FIELD = field;
   }
}
