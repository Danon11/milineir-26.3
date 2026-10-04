package org.millenaire.language;

import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.client.ClientLanguageCache;
import org.millenaire.dialogue.DialogueLoader;
import org.millenaire.dialogue.SentenceLoader;

public final class SpeechResolver {
   private SpeechResolver() {
   }

   @OnlyIn(Dist.CLIENT)
   public static String[] resolve(String speechRef, @Nullable ResourceLocation cultureIdForCache, int languageScore) {
      if (speechRef != null && !speechRef.isEmpty()) {
         String playerLang = resolveContentLang(Minecraft.getInstance().options.languageCode);
         String localPlayerName = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getName().getString() : "";
         if (speechRef.startsWith("s:")) {
            return resolveSentence(speechRef, playerLang, localPlayerName, cultureIdForCache, languageScore);
         } else {
            return speechRef.startsWith("d:")
               ? resolveDialogue(speechRef, playerLang, localPlayerName, cultureIdForCache, languageScore)
               : new String[]{speechRef, null};
         }
      } else {
         return new String[]{null, null};
      }
   }

   @OnlyIn(Dist.CLIENT)
   public static String[] resolve(String speechRef) {
      return resolve(speechRef, null, -1);
   }

   @OnlyIn(Dist.CLIENT)
   private static String resolveContentLang(String requested) {
      Set<String> supported = new HashSet<>(DialogueLoader.getLoadedLanguages());
      supported.addAll(SentenceLoader.getLoadedLanguages());
      String resolved = LocaleResolver.resolveSupported(requested, supported);
      return resolved != null ? resolved : requested;
   }

   @OnlyIn(Dist.CLIENT)
   private static String[] resolveSentence(
      String speechRef, String playerLang, String playerName, @Nullable ResourceLocation cultureIdForCache, int languageScore
   ) {
      String[] parts = speechRef.substring(2).split(":", 4);
      if (parts.length < 4) {
         return new String[]{null, null};
      }

      String cultureKey = parts[0];
      String role = parts[1];
      String goalKey = parts[2];

      int idx;
      try {
         idx = Integer.parseInt(parts[3]);
      } catch (NumberFormatException e) {
         return new String[]{null, null};
      }

      ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", cultureKey);
      String nativeText = SentenceLoader.getSentence(cultureId, "native", role, goalKey, idx);
      if (nativeText != null) {
         nativeText = nativeText.replace("$name", playerName);
      }

      String translation = null;
      String raw = SentenceLoader.getSentence(cultureId, playerLang, role, goalKey, idx);
      if (raw != null) {
         raw = raw.replace("$name", playerName);
      }

      if (raw != null && !DisplayNameResolver.equivalent(nativeText, raw)) {
         translation = maskTranslation(raw, cultureId, cultureIdForCache, languageScore);
      }

      return new String[]{nativeText, translation};
   }

   @OnlyIn(Dist.CLIENT)
   private static String[] resolveDialogue(
      String speechRef, String playerLang, String playerName, @Nullable ResourceLocation cultureIdForCache, int languageScore
   ) {
      String[] parts = speechRef.substring(2).split(":", 4);
      if (parts.length < 3) {
         return new String[]{null, null};
      }

      String cultureKey = parts[0];
      String dialogueKey = parts[1];

      int lineIdx;
      try {
         lineIdx = Integer.parseInt(parts[2]);
      } catch (NumberFormatException e) {
         return new String[]{null, null};
      }

      String targetFirstName = parts.length >= 4 ? SpeechRefCodec.decodeTargetName(parts[3]) : "";
      ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", cultureKey);
      String nativeText = DialogueLoader.getDialogueLine(cultureId, "native", dialogueKey, lineIdx);
      if (nativeText != null) {
         nativeText = SpeechRefCodec.applyDialogueSubstitutions(nativeText, playerName, targetFirstName);
      }

      String translation = null;
      String raw = DialogueLoader.getDialogueLine(cultureId, playerLang, dialogueKey, lineIdx);
      if (raw != null) {
         raw = SpeechRefCodec.applyDialogueSubstitutions(raw, playerName, targetFirstName);
         if (!DisplayNameResolver.equivalent(nativeText, raw)) {
            translation = maskTranslation(raw, cultureId, cultureIdForCache, languageScore);
         }
      }

      return new String[]{nativeText, translation};
   }

   @OnlyIn(Dist.CLIENT)
   private static String maskTranslation(String raw, ResourceLocation cultureId, @Nullable ResourceLocation cultureIdForCache, int languageScore) {
      int score = languageScore >= 0 ? languageScore : ClientLanguageCache.get(cultureIdForCache != null ? cultureIdForCache : cultureId);
      double ratio = SentenceRenderer.languageRatio(score);
      if (ratio >= 1.0) {
         return raw;
      } else {
         return ratio <= 0.0 ? null : SentenceRenderer.maskTranslation(raw, ratio);
      }
   }
}
