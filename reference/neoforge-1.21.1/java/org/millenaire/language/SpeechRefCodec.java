package org.millenaire.language;

public final class SpeechRefCodec {
   private SpeechRefCodec() {
   }

   public static String encodeTargetName(String name) {
      return name != null && !name.isEmpty() ? name.replace("%", "%25").replace(":", "%3A") : "";
   }

   public static String decodeTargetName(String encoded) {
      return encoded != null && !encoded.isEmpty() ? encoded.replace("%3A", ":").replace("%25", "%") : "";
   }

   public static String applyDialogueSubstitutions(String text, String playerName, String targetFirstName) {
      String out = text.replace("$name", playerName);
      if (targetFirstName != null && !targetFirstName.isEmpty()) {
         out = out.replace("$targetfirstname", targetFirstName);
      } else {
         out = out.replace("$targetfirstname, ", "");
         out = out.replace("$targetfirstname,", "");
         out = out.replace(" $targetfirstname", "");
         out = out.replace("$targetfirstname", "");
      }

      return out;
   }
}
