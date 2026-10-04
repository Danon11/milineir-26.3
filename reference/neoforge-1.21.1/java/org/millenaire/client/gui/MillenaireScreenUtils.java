package org.millenaire.client.gui;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.millenaire.entity.MillVillager;

public final class MillenaireScreenUtils {
   public static final int COLOR_WHITE = 16777215;
   public static final int COLOR_GRAY = 5592405;
   public static final int COLOR_GREEN = 5635925;
   public static final int COLOR_REP_POSITIVE = 5635925;
   public static final int COLOR_REP_NEGATIVE = 16733525;
   public static final int COLOR_REP_NEUTRAL = 13421568;
   public static final int COLOR_REP_DARKGREEN = 43520;
   public static final int COLOR_REP_DARKBLUE = 170;
   public static final int COLOR_REP_LIGHTRED = 16733525;
   public static final int COLOR_REP_DARKRED = 11141120;

   private MillenaireScreenUtils() {
   }

   @Nullable
   public static LivingEntity findEntityById(int entityId) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return null;
      } else {
         return mc.level.getEntity(entityId) instanceof LivingEntity living ? living : null;
      }
   }

   public static int renderVillagerPreview(
      GuiGraphics graphics, int panelX, int panelY, int panelWidth, int padding, int y, int mouseX, int mouseY, int entityId
   ) {
      LivingEntity entity = findEntityById(entityId);
      if (entity == null) {
         return y;
      }

      int entityAreaHeight = 60;
      int centerX = panelX + panelWidth / 2;
      int entityY = panelY + padding + entityAreaHeight;
      boolean wasPreview = false;
      if (entity instanceof MillVillager mv) {
         wasPreview = mv.isGuiPreviewMode();
         mv.setGuiPreviewMode(true);
      }

      try {
         InventoryScreen.renderEntityInInventoryFollowsMouse(
            graphics, centerX - 25, panelY + padding + 5, centerX + 25, entityY, 25, 0.0625F, mouseX, mouseY, entity
         );
      } catch (Exception e) {
         return y;
      } finally {
         if (entity instanceof MillVillager mv) {
            mv.setGuiPreviewMode(wasPreview);
         }
      }

      return panelY + padding + entityAreaHeight + 4;
   }

   public static void renderStaticVillagerPreview(GuiGraphics graphics, int centerX, int topY, int bottomY, int scale, LivingEntity entity) {
      float fixedMouseX = centerX + 40;
      float fixedMouseY = (topY + bottomY) / 2.0F - 10.0F;
      boolean wasPreview = false;
      if (entity instanceof MillVillager mv) {
         wasPreview = mv.isGuiPreviewMode();
         mv.setGuiPreviewMode(true);
      }

      try {
         InventoryScreen.renderEntityInInventoryFollowsMouse(
            graphics, centerX - 30, topY, centerX + 30, bottomY, scale, 0.0625F, fixedMouseX, fixedMouseY, entity
         );
      } catch (Exception var14) {
      } finally {
         if (entity instanceof MillVillager mv) {
            mv.setGuiPreviewMode(wasPreview);
         }
      }
   }

   public static int getReputationColor(int reputation) {
      if (reputation >= 32768) {
         return 43520;
      } else if (reputation >= 4096) {
         return 170;
      } else if (reputation < -256) {
         return 11141120;
      } else {
         return reputation < 0 ? 16733525 : 13421568;
      }
   }

   public static String resolveReputationLabel(String reputationLabel) {
      return reputationLabel.startsWith("reputation.") ? Component.translatable(reputationLabel).getString() : reputationLabel;
   }
}
