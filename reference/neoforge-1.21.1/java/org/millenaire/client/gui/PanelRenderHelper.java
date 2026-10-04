package org.millenaire.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.millenaire.DisplayUtils;
import org.millenaire.item.ItemHelper;
import org.millenaire.language.DisplayNameResolver;
import org.millenaire.network.MapData;
import org.millenaire.village.panel.PanelLine;
import org.slf4j.Logger;

public final class PanelRenderHelper {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final int PARCHMENT_BG = -1518173;
   public static final int PARCHMENT_BG_DARK = -2835846;
   public static final int PARCHMENT_BORDER = -7638187;
   public static final int TITLE_COLOR = -11193600;
   public static final int TEXT_COLOR = -13421773;
   public static final int SEPARATOR_COLOR = -5601178;
   public static final int MAP_BUILDING_COMPLETE = -12558081;
   public static final int MAP_BUILDING_CONSTRUCTION = -48897;
   public static final int MAP_BUILDING_UPGRADING = -32704;
   public static final int MAP_BUILDING_PLANNED = -16777120;
   public static final int MAP_BUILDING_PENDING = -8372032;
   public static final int MAP_VILLAGER_MALE = -16711681;
   public static final int MAP_VILLAGER_FEMALE = -49088;
   public static final int MAP_VILLAGER_CHILD = -256;
   public static final int MAP_VILLAGER_CHIEF = -3390209;
   public static final int MAP_PLAYER = -1;
   public static final int MAP_TERRAIN_WATER = -1439682305;
   public static final int MAP_TERRAIN_DANGER = 1090453504;
   public static final int MAP_TERRAIN_FORBIDDEN = 1090518784;
   public static final int MAP_TERRAIN_UNBUILDABLE = Integer.MIN_VALUE;
   public static final int MAP_TERRAIN_BUILDABLE = 1073807104;
   public static final int MAP_TERRAIN_OCCUPIED = 0;
   public static final int MAP_PATH_LEVEL_0 = -7639225;
   public static final int MAP_PATH_LEVEL_1 = -5592406;
   public static final int MAP_PATH_LEVEL_2 = -2041688;
   public static final int MAP_PATH_LEVEL_FALLBACK = -1;
   public static final int LINE_HEIGHT = 11;
   public static final ResourceLocation PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath("millenaire", "textures/gui/panel.png");
   public static final ResourceLocation PARCHMENT_TEXTURE = ResourceLocation.fromNamespaceAndPath("millenaire", "textures/gui/parchment.png");
   public static final ResourceLocation VILLAGE_CHIEF_TEXTURE = ResourceLocation.fromNamespaceAndPath("millenaire", "textures/gui/village_chief.png");
   public static final ResourceLocation QUEST_TEXTURE = ResourceLocation.fromNamespaceAndPath("millenaire", "textures/gui/quest.png");
   private static final int PANEL_TEX_SIZE = 256;
   public static final int LINK_COLOR = -15645526;
   public static final int LINK_HOVER_COLOR = -14522659;
   public static final int CHUNK_LOADED = 1610664960;
   public static final int CHUNK_UNLOADED = 1619034240;
   public static final int CHUNK_GRID = 1073741824;
   private static final int BUTTON_BG = -7631989;
   private static final int BUTTON_HIGHLIGHT = -3158065;
   private static final int BUTTON_SHADOW = -11184811;
   private static final int BUTTON_HOVER_BG = -6250336;

   private PanelRenderHelper() {
   }

   public static void renderTexturedBackground(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height) {
      graphics.blit(texture, x, y, 0.0F, 0.0F, width, height, 256, 256);
   }

   public static void renderParchmentBackground(GuiGraphics graphics, int x, int y, int width, int height) {
      graphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, 1140850688);
      graphics.fill(x, y, x + width, y + height, -1518173);
      graphics.fill(x, y, x + width, y + 3, -2835846);
      graphics.fill(x, y + height - 3, x + width, y + height, -2835846);
      graphics.fill(x, y, x + 3, y + height, -2835846);
      graphics.fill(x + width - 3, y, x + width, y + height, -2835846);
      graphics.renderOutline(x, y, width, height, -7638187);
   }

   public static void renderPanelLines(GuiGraphics graphics, Font font, List<PanelLine> wrappedLines, int x, int y, int width, int startLine, int maxLines) {
      renderPanelLines(graphics, font, wrappedLines, x, y, width, startLine, maxLines, -1, -1);
   }

   public static void renderPanelLines(
      GuiGraphics graphics, Font font, List<PanelLine> wrappedLines, int x, int y, int width, int startLine, int maxLines, int mouseX, int mouseY
   ) {
      int endLine = Math.min(startLine + maxLines, wrappedLines.size());
      int rightEdge = x + width;
      int currentY = y;

      for (int i = startLine; i < endLine; i++) {
         PanelLine line = wrappedLines.get(i);
         if (line.isSeparator()) {
            graphics.fill(x, currentY + 4, rightEdge, currentY + 5, -5601178);
            currentY += 11;
         } else if (line.leftIcon() != null && line.translatable() && !line.isColumns()) {
            int textColor = getLineColor(line, x, currentY, width, mouseX, mouseY);
            int iconOffset = 0;
            ItemStack iconStack = resolveItemIcon(line.leftIcon());
            if (!iconStack.isEmpty()) {
               int iconX = x;
               int iconY = currentY - 2;
               if (line.isClickable()) {
                  renderButtonFrame(graphics, iconX - 1, iconY - 1, 18, 18, mouseX, mouseY);
               }

               graphics.renderItem(iconStack, iconX, iconY);
               iconOffset = 18;
            }

            String resolvedName = resolveDisplayText(line);
            graphics.drawString(font, resolvedName, x + iconOffset, currentY, textColor, false);
            if (line.rightColumn() != null) {
               int rightWidth = font.width(line.rightColumn());
               int rightX = rightEdge - rightWidth;
               graphics.drawString(font, line.rightColumn(), rightX, currentY, textColor, false);
            }

            currentY += 11;
         } else if (line.isColumns()) {
            int textColor = getLineColor(line, x, currentY, width, mouseX, mouseY);
            int iconOffset = 0;
            if (line.leftIcon() != null) {
               ItemStack iconStack = resolveItemIcon(line.leftIcon());
               if (!iconStack.isEmpty()) {
                  int iconX = x;
                  int iconY = currentY - 2;
                  if (line.isClickable()) {
                     renderButtonFrame(graphics, iconX - 1, iconY - 1, 18, 18, mouseX, mouseY);
                  }

                  graphics.renderItem(iconStack, iconX, iconY);
                  iconOffset = 18;
               } else {
                  LOGGER.debug("[Panel] Empty icon for '{}' on line '{}'", line.leftIcon(), line.leftColumn());
               }
            }

            graphics.drawString(font, line.leftColumn(), x + iconOffset, currentY, textColor, false);
            int rightWidth = font.width(line.rightColumn());
            int rightX = rightEdge - rightWidth;
            graphics.drawString(font, line.rightColumn(), rightX, currentY, textColor, false);
            currentY += 11;
         } else {
            int textColor = getLineColor(line, x, currentY, width, mouseX, mouseY);
            String displayText = resolveDisplayText(line);
            if (line.bold()) {
               Component styled = Component.literal(displayText).withStyle(s -> s.withBold(true));
               graphics.drawCenteredString(font, styled, x + width / 2, currentY, textColor);
            } else {
               graphics.drawString(font, displayText, x, currentY, textColor, false);
            }

            currentY += 11;
         }
      }
   }

   private static int getLineColor(PanelLine line, int x, int lineY, int width, int mouseX, int mouseY) {
      if (line.isClickable()) {
         return mouseX >= x && mouseX < x + width && mouseY >= lineY && mouseY < lineY + 11 ? -14522659 : -15645526;
      } else {
         return line.color() != -1 ? line.color() : -13421773;
      }
   }

   @Nullable
   public static PanelLine.PanelNavTarget getClickedNavTarget(
      List<PanelLine> wrappedLines, int contentX, int contentY, int width, int startLine, int maxLines, double mouseX, double mouseY
   ) {
      int endLine = Math.min(startLine + maxLines, wrappedLines.size());
      int currentY = contentY;

      for (int i = startLine; i < endLine; i++) {
         PanelLine line = wrappedLines.get(i);
         if (line.isClickable() && mouseX >= contentX && mouseX < contentX + width && mouseY >= currentY && mouseY < currentY + 11) {
            return line.navTarget();
         }

         currentY += 11;
      }

      return null;
   }

   @Nullable
   public static String renderVillageMap(
      GuiGraphics graphics,
      Font font,
      List<MapData.MapBuilding> mapBuildings,
      List<MapData.MapVillager> mapVillagers,
      int playerX,
      int playerZ,
      float playerYaw,
      int centerX,
      int centerZ,
      MapData.MapTerrain terrain,
      List<MapData.MapPath> mapPaths,
      int drawX,
      int drawY,
      int drawWidth,
      int drawHeight,
      int mouseX,
      int mouseY
   ) {
      int minX;
      int maxX;
      int minZ;
      int maxZ;
      if (terrain.width() > 0) {
         minX = terrain.startX();
         maxX = terrain.startX() + terrain.width();
         minZ = terrain.startZ();
         maxZ = terrain.startZ() + terrain.depth();
      } else {
         minX = centerX - 80;
         maxX = centerX + 80;
         minZ = centerZ - 80;
         maxZ = centerZ + 80;
      }

      for (MapData.MapBuilding b : mapBuildings) {
         minX = Math.min(minX, b.x() - 2);
         maxX = Math.max(maxX, b.x() + b.width() + 2);
         minZ = Math.min(minZ, b.z() - 2);
         maxZ = Math.max(maxZ, b.z() + b.depth() + 2);
      }

      int worldW = maxX - minX;
      int worldD = maxZ - minZ;
      float scaleX = (float)drawWidth / worldW;
      float scaleZ = (float)drawHeight / worldD;
      float scale = Math.min(scaleX, scaleZ);
      int offsetX = drawX + (int)((drawWidth - worldW * scale) / 2.0F);
      int offsetZ = drawY + (int)((drawHeight - worldD * scale) / 2.0F);
      graphics.fill(offsetX, offsetZ, offsetX + (int)(worldW * scale), offsetZ + (int)(worldD * scale), 536870912);
      if (terrain.width() > 0) {
         int screenW = (int)(worldW * scale);
         int screenD = (int)(worldD * scale);
         float invScale = 1.0F / scale;

         for (int px = 0; px < screenW; px++) {
            int wx = minX + (int)((px + 0.5F) * invScale);
            int lastColor = 0;
            int lastPz = 0;

            for (int pz = 0; pz <= screenD; pz++) {
               int color;
               if (pz < screenD) {
                  int wz = minZ + (int)((pz + 0.5F) * invScale);
                  color = terrainColor(terrain.tileAt(wx, wz));
               } else {
                  color = 0;
               }

               if (color != lastColor) {
                  if (lastColor != 0) {
                     graphics.fill(offsetX + px, offsetZ + lastPz, offsetX + px + 1, offsetZ + pz, lastColor);
                  }

                  lastColor = color;
                  lastPz = pz;
               }
            }
         }
      }

      if (mapPaths != null && !mapPaths.isEmpty()) {
         int pathPx = scale > 1.5F ? 2 : 1;

         for (MapData.MapPath p : mapPaths) {
            int px = offsetX + (int)((p.x() - minX) * scale);
            int pz = offsetZ + (int)((p.z() - minZ) * scale);
            graphics.fill(px, pz, px + pathPx, pz + pathPx, pathColor(p.level()));
         }
      }

      String hoveredBuilding = null;

      for (MapData.MapBuilding b : mapBuildings) {
         int bx1 = offsetX + (int)((b.x() - minX) * scale);
         int bz1 = offsetZ + (int)((b.z() - minZ) * scale);
         int minPx = b.isWall() ? 1 : 2;
         int bx2 = bx1 + Math.max(minPx, (int)(b.width() * scale));
         int bz2 = bz1 + Math.max(minPx, (int)(b.depth() * scale));

         int color = switch (b.status()) {
            case "COMPLETE", "IDLE" -> -12558081;
            case "PLANNED" -> -16777120;
            case "PENDING" -> -8372032;
            case "UPGRADING" -> -32704;
            default -> -48897;
         };
         graphics.fill(bx1, bz1, bx2, bz2, color);
         graphics.renderOutline(bx1, bz1, bx2 - bx1, bz2 - bz1, -16777152);
         if (mouseX >= bx1 && mouseX < bx2 && mouseY >= bz1 && mouseY < bz2) {
            String statusKey = switch (b.status()) {
               case "COMPLETE" -> "panel.millenaire.map_status.complete";
               case "IDLE" -> null;
               case "PLANNED" -> "panel.millenaire.map_status.planned";
               case "PENDING" -> "panel.millenaire.map_status.pending";
               case "UPGRADING" -> "panel.millenaire.map_status.upgrading";
               default -> "panel.millenaire.map_status.construction";
            };
            String levelSuffix = b.level() > 0 ? " Lv." + b.level() : "";
            String statusStr = statusKey != null ? " (" + Component.translatable(statusKey).getString() + ")" : "";
            String resolvedName;
            if (b.nameTranslatable()) {
               String translated = I18n.get(b.name(), new Object[0]);
               resolvedName = DisplayNameResolver.resolve(translated, true, b.nativePrefix(), b.name());
            } else {
               resolvedName = b.name();
            }

            hoveredBuilding = resolvedName + levelSuffix + statusStr;
         }
      }

      String hoveredVillager = null;

      for (MapData.MapVillager v : mapVillagers) {
         int vx = offsetX + (int)((v.x() - minX) * scale);
         int vz = offsetZ + (int)((v.z() - minZ) * scale);
         int color;
         int halfSize;
         if (v.isChief()) {
            color = -3390209;
            halfSize = 2;
         } else {
            color = switch (v.gender()) {
               case "FEMALE" -> -49088;
               case "CHILD" -> -256;
               default -> -16711681;
            };
            halfSize = 1;
         }

         graphics.fill(vx - halfSize, vz - halfSize, vx + halfSize + 1, vz + halfSize + 1, color);
         if (mouseX >= vx - 2 && mouseX <= vx + 3 && mouseY >= vz - 2 && mouseY <= vz + 3) {
            String rawGoal = v.goalLabel();
            String resolvedGoalStr = DisplayUtils.resolveGoalLabel(rawGoal);
            String goalSuffix = resolvedGoalStr != null && !resolvedGoalStr.isEmpty() ? " - " + resolvedGoalStr : "";
            hoveredVillager = v.name() + " (" + I18n.get(v.role(), new Object[0]) + ")" + goalSuffix;
         }
      }

      int px = offsetX + (int)((playerX - minX) * scale);
      int pz = offsetZ + (int)((playerZ - minZ) * scale);
      PoseStack pose = graphics.pose();
      pose.pushPose();
      pose.translate(px, pz, 0.0F);
      pose.mulPose(Axis.ZP.rotationDegrees(playerYaw + 180.0F));
      graphics.fill(0, -5, 1, 3, -1);
      graphics.fill(-3, 0, 4, 1, -1);
      pose.popPose();
      return hoveredVillager != null ? hoveredVillager : hoveredBuilding;
   }

   @Nullable
   public static String renderChunkMap(
      GuiGraphics graphics,
      Font font,
      List<MapData.MapChunk> chunks,
      List<MapData.MapBuilding> mapBuildings,
      int centerX,
      int centerZ,
      int drawX,
      int drawY,
      int drawWidth,
      int drawHeight,
      int mouseX,
      int mouseY
   ) {
      if (chunks.isEmpty()) {
         return null;
      }

      int minX = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int minZ = Integer.MAX_VALUE;
      int maxZ = Integer.MIN_VALUE;

      for (MapData.MapChunk c : chunks) {
         int bx = c.chunkX() << 4;
         int bz = c.chunkZ() << 4;
         minX = Math.min(minX, bx);
         maxX = Math.max(maxX, bx + 16);
         minZ = Math.min(minZ, bz);
         maxZ = Math.max(maxZ, bz + 16);
      }

      int worldW = maxX - minX;
      int worldD = maxZ - minZ;
      if (worldW > 0 && worldD > 0) {
         int legendHeight = 24;
         int mapHeight = drawHeight - legendHeight;
         float scaleX = (float)drawWidth / worldW;
         float scaleZ = (float)mapHeight / worldD;
         float scale = Math.min(scaleX, scaleZ);
         int offsetX = drawX + (int)((drawWidth - worldW * scale) / 2.0F);
         int offsetZ = drawY + (int)((mapHeight - worldD * scale) / 2.0F);
         int screenW = (int)(worldW * scale);
         int screenD = (int)(worldD * scale);
         graphics.fill(offsetX, offsetZ, offsetX + screenW, offsetZ + screenD, 536870912);
         String hoveredChunk = null;

         for (MapData.MapChunk c : chunks) {
            int bx = c.chunkX() << 4;
            int bz = c.chunkZ() << 4;
            int cx1 = offsetX + (int)((bx - minX) * scale);
            int cz1 = offsetZ + (int)((bz - minZ) * scale);
            int cx2 = offsetX + (int)((bx + 16 - minX) * scale);
            int cz2 = offsetZ + (int)((bz + 16 - minZ) * scale);
            int color = c.loaded() ? 1610664960 : 1619034240;
            graphics.fill(cx1, cz1, cx2, cz2, color);
            graphics.renderOutline(cx1, cz1, cx2 - cx1, cz2 - cz1, 1073741824);
            if (mouseX >= cx1 && mouseX < cx2 && mouseY >= cz1 && mouseY < cz2) {
               String statusKey = c.loaded() ? "panel.millenaire.chunk_loaded" : "panel.millenaire.chunk_unloaded";
               hoveredChunk = I18n.get(statusKey, new Object[]{String.valueOf(c.chunkX()), String.valueOf(c.chunkZ())});
            }
         }

         for (MapData.MapBuilding b : mapBuildings) {
            int bx1 = offsetX + (int)((b.x() - minX) * scale);
            int bz1 = offsetZ + (int)((b.z() - minZ) * scale);
            int minPx = b.isWall() ? 1 : 2;
            int bx2 = bx1 + Math.max(minPx, (int)(b.width() * scale));
            int bz2 = bz1 + Math.max(minPx, (int)(b.depth() * scale));
            graphics.fill(bx1, bz1, bx2, bz2, -2130706433);
            graphics.renderOutline(bx1, bz1, bx2 - bx1, bz2 - bz1, -16777152);
         }

         int legendY = drawY + mapHeight + 4;
         int legendBoxSize = 8;
         graphics.fill(drawX, legendY, drawX + legendBoxSize, legendY + legendBoxSize, -16724992);
         graphics.renderOutline(drawX, legendY, legendBoxSize, legendBoxSize, -16777216);
         String loadedLabel = I18n.get("panel.millenaire.chunk_legend_loaded", new Object[0]);
         graphics.drawString(font, loadedLabel, drawX + legendBoxSize + 3, legendY, -13421773, false);
         int unloadedX = drawX + legendBoxSize + 3 + font.width(loadedLabel) + 10;
         graphics.fill(unloadedX, legendY, unloadedX + legendBoxSize, legendY + legendBoxSize, -8355712);
         graphics.renderOutline(unloadedX, legendY, legendBoxSize, legendBoxSize, -16777216);
         String unloadedLabel = I18n.get("panel.millenaire.chunk_legend_unloaded", new Object[0]);
         graphics.drawString(font, unloadedLabel, unloadedX + legendBoxSize + 3, legendY, -13421773, false);
         return hoveredChunk;
      } else {
         return null;
      }
   }

   private static int terrainColor(byte tile) {
      return switch (tile) {
         case 1 -> -1439682305;
         case 2 -> 1090453504;
         case 3 -> 1090518784;
         case 4 -> Integer.MIN_VALUE;
         case 5 -> 1073807104;
         default -> 0;
      };
   }

   private static int pathColor(byte level) {
      return switch (level) {
         case 0 -> -7639225;
         case 1 -> -5592406;
         case 2 -> -2041688;
         default -> -1;
      };
   }

   public static List<PanelLine> wrapLines(List<PanelLine> source, Font font, int maxWidth) {
      List<PanelLine> result = new ArrayList<>();

      for (PanelLine line : source) {
         if (line.isSeparator() || line.isColumns() || line.leftIcon() != null && line.translatable()) {
            result.add(line);
         } else if (line.text().isEmpty()) {
            result.add(line);
         } else {
            String text = resolveDisplayText(line);
            if (font.width(text) <= maxWidth) {
               result.add(line);
            } else {
               String[] words = text.split(" ");
               StringBuilder current = new StringBuilder();
               boolean isFirst = true;

               for (String word : words) {
                  String test = current.isEmpty() ? word : current + " " + word;
                  if (font.width(test) > maxWidth && !current.isEmpty()) {
                     String activeColor = extractLastColorCode(current.toString());
                     if (isFirst) {
                        result.add(new PanelLine(current.toString(), false, null, null, null, false, line.navTarget(), null, line.color(), line.bold()));
                        isFirst = false;
                     } else {
                        result.add(new PanelLine(current.toString(), false, null, null, null, false, null, null, line.color(), line.bold()));
                     }

                     current = new StringBuilder(activeColor + word);
                  } else {
                     current = new StringBuilder(test);
                  }
               }

               if (!current.isEmpty()) {
                  if (isFirst) {
                     result.add(new PanelLine(current.toString(), false, null, null, null, false, line.navTarget(), null, line.color(), line.bold()));
                  } else {
                     result.add(new PanelLine(current.toString(), false, null, null, null, false, null, null, line.color(), line.bold()));
                  }
               }
            }
         }
      }

      return result;
   }

   public static String resolveDisplayText(PanelLine line) {
      if (line.translatable() && !line.text().isEmpty()) {
         String resolved;
         if (line.translatableArgs() != null) {
            Object[] args = resolveTypedArgs(line.translatableArgs(), line.translatableArgMask());
            resolved = I18n.get(line.text(), args);
         } else {
            resolved = I18n.get(line.text(), new Object[0]);
         }

         return DisplayNameResolver.resolve(resolved, true, line.nativePrefix(), line.text());
      } else {
         return line.text();
      }
   }

   private static Object[] resolveTypedArgs(String[] args, int mask) {
      Object[] resolved = new Object[args.length];

      for (int i = 0; i < args.length; i++) {
         if ((mask & 1 << i) != 0 && args[i] != null) {
            resolved[i] = I18n.get(args[i], new Object[0]);
         } else {
            resolved[i] = args[i] != null ? args[i] : "";
         }
      }

      return resolved;
   }

   public static int computePageCount(int totalLines, int linesPerPage) {
      return Math.max(1, (totalLines + linesPerPage - 1) / linesPerPage);
   }

   public static ItemStack resolveItemIcon(String itemId) {
      try {
         Item item = ItemHelper.resolve(itemId);
         if (item == null) {
            LOGGER.warn("[Panel] Icon not found in registry: {}", itemId);
            return ItemStack.EMPTY;
         } else {
            return new ItemStack(item);
         }
      } catch (Exception e) {
         LOGGER.warn("[Panel] Error resolving icon '{}': {}", itemId, e.getMessage());
         return ItemStack.EMPTY;
      }
   }

   static String extractLastColorCode(String text) {
      String prefix = "";

      for (int i = 0; i < text.length() - 1; i++) {
         if (text.charAt(i) == 167) {
            prefix = text.substring(i, i + 2);
            i++;
         }
      }

      return prefix;
   }

   private static void renderButtonFrame(GuiGraphics graphics, int x, int y, int w, int h, int mouseX, int mouseY) {
      boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
      int bg = hovered ? -6250336 : -7631989;
      graphics.fill(x, y, x + w, y + h, bg);
      graphics.fill(x, y, x + w, y + 1, -3158065);
      graphics.fill(x, y, x + 1, y + h, -3158065);
      graphics.fill(x, y + h - 1, x + w, y + h, -11184811);
      graphics.fill(x + w - 1, y, x + w, y + h, -11184811);
   }
}
