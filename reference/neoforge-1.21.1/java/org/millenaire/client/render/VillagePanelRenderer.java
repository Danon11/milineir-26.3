package org.millenaire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.millenaire.block.VillagePanelBlock;
import org.millenaire.block.VillagePanelBlockEntity;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.item.ItemHelper;
import org.millenaire.language.DisplayNameResolver;
import org.millenaire.village.panel.PanelContentGenerator;
import org.slf4j.Logger;

public class VillagePanelRenderer implements BlockEntityRenderer<VillagePanelBlockEntity> {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final float TEXT_SCALE = 0.010416667F;
   private static final int TEXT_COLOR = -16777216;
   private static final int MAX_LINES = 8;
   private static final int MAX_TEXT_WIDTH = 80;
   private static final int LINE_SPACING = 10;
   private static final int START_Y = -15;
   private static final int LEFT_X = -29;
   private static final int RIGHT_COLUMN_X = 11;
   private static final ResourceLocation DEFAULT_TEXTURE = ResourceLocation.fromNamespaceAndPath("millenaire", "textures/entity/panels/default.png");
   private static final float PANEL_HALF_W = 0.5F;
   private static final float PANEL_HALF_H = 0.5F;
   private static final float PANEL_DEPTH = 0.083333336F;
   private static final float FRONT_U0 = 0.4375F;
   private static final float FRONT_V0 = 0.0625F;
   private static final float FRONT_U1 = 0.8125F;
   private static final float FRONT_V1 = 0.8125F;
   private static final float BACK_U0 = 0.03125F;
   private static final float BACK_V0 = 0.0625F;
   private static final float BACK_U1 = 0.40625F;
   private static final float BACK_V1 = 0.8125F;
   private static final float LEFT_U0 = 0.0F;
   private static final float LEFT_V0 = 0.0625F;
   private static final float LEFT_U1 = 0.03125F;
   private static final float LEFT_V1 = 0.8125F;
   private static final float RIGHT_U0 = 0.40625F;
   private static final float RIGHT_V0 = 0.0625F;
   private static final float RIGHT_U1 = 0.4375F;
   private static final float RIGHT_V1 = 0.8125F;
   private static final float TOP_U0 = 0.03125F;
   private static final float TOP_V0 = 0.0F;
   private static final float TOP_U1 = 0.40625F;
   private static final float TOP_V1 = 0.0625F;
   private static final float BOT_U0 = 0.40625F;
   private static final float BOT_V0 = 0.0F;
   private static final float BOT_U1 = 0.78125F;
   private static final float BOT_V1 = 0.0625F;
   private final Font font;

   public VillagePanelRenderer(Context context) {
      this.font = context.getFont();
   }

   public void render(
      VillagePanelBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay
   ) {
      Direction facing = Direction.NORTH;
      if (blockEntity.getBlockState().hasProperty(VillagePanelBlock.FACING)) {
         facing = (Direction)blockEntity.getBlockState().getValue(VillagePanelBlock.FACING);
      }

      poseStack.pushPose();
      poseStack.translate(0.5, 0.5, 0.5);
      poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
      renderBackgroundQuad(blockEntity, poseStack, bufferSource, packedLight, packedOverlay);
      List<PanelContentGenerator.DisplayLine> lines = blockEntity.getDisplayLines();
      if (lines.isEmpty()) {
         poseStack.popPose();
      } else {
         poseStack.translate(0.0, 0.0, -0.4375);
         poseStack.translate(0.0F, 0.25F, 0.046666667F);
         poseStack.scale(0.010416667F, -0.010416667F, 0.010416667F);
         Matrix4f matrix = poseStack.last().pose();

         for (int i = 0; i < lines.size() && i < 8; i++) {
            PanelContentGenerator.DisplayLine line = lines.get(i);
            float y = -15 + i * 10;
            if (line.leftColumn().isEmpty() && line.rightColumn().isEmpty()) {
               String text = line.text();
               if (line.translatable() && !text.isEmpty()) {
                  String originalKey = text;
                  String resolved = I18n.get(text, new Object[0]);
                  if (line.nativePrefix() != null) {
                     text = DisplayNameResolver.resolve(resolved, true, line.nativePrefix(), originalKey);
                  } else {
                     text = resolved;
                  }
               }

               text = stripFormattingCodes(text);
               int maxWidth = !line.leftIcon().isEmpty() ? 62 : 80;
               text = this.truncateWithEllipsis(text, maxWidth);
               Component comp = Component.literal(text);
               float x = line.centered() ? -this.font.width(comp) / 2.0F : -29.0F;
               this.font.drawInBatch(comp, x, y, -16777216, false, matrix, bufferSource, DisplayMode.POLYGON_OFFSET, 0, packedLight);
            } else {
               if (!line.leftColumn().isEmpty()) {
                  String leftText = stripFormattingCodes(line.leftColumn());
                  leftText = this.truncateWithEllipsis(leftText, 32);
                  this.font
                     .drawInBatch(Component.literal(leftText), -29.0F, y, -16777216, false, matrix, bufferSource, DisplayMode.POLYGON_OFFSET, 0, packedLight);
               }

               if (!line.rightColumn().isEmpty()) {
                  String rightText = stripFormattingCodes(line.rightColumn());
                  rightText = this.truncateWithEllipsis(rightText, 32);
                  this.font
                     .drawInBatch(Component.literal(rightText), 11.0F, y, -16777216, false, matrix, bufferSource, DisplayMode.POLYGON_OFFSET, 0, packedLight);
               }
            }
         }

         this.renderIcons(blockEntity, lines, poseStack, bufferSource, packedLight, packedOverlay);
         poseStack.popPose();
      }
   }

   private static void renderBackgroundQuad(
      VillagePanelBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay
   ) {
      ResourceLocation texture = resolveTexture(blockEntity.getCultureId());
      VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture));
      Matrix4f matrix = poseStack.last().pose();
      float zMid = -0.4375F;
      float zFront = zMid - 0.041666668F;
      float zBack = zMid + 0.041666668F;
      float x0 = -0.5F;
      float x1 = 0.5F;
      float y0 = -0.5F;
      float y1 = 0.5F;
      consumer.addVertex(matrix, x0, y1, zFront)
         .setColor(255, 255, 255, 255)
         .setUv(0.4375F, 0.0625F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(0.0F, 0.0F, -1.0F);
      consumer.addVertex(matrix, x0, y0, zFront)
         .setColor(255, 255, 255, 255)
         .setUv(0.4375F, 0.8125F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(0.0F, 0.0F, -1.0F);
      consumer.addVertex(matrix, x1, y0, zFront)
         .setColor(255, 255, 255, 255)
         .setUv(0.8125F, 0.8125F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(0.0F, 0.0F, -1.0F);
      consumer.addVertex(matrix, x1, y1, zFront)
         .setColor(255, 255, 255, 255)
         .setUv(0.8125F, 0.0625F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(0.0F, 0.0F, -1.0F);
      consumer.addVertex(matrix, x1, y1, zBack)
         .setColor(255, 255, 255, 255)
         .setUv(0.03125F, 0.0625F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(0.0F, 0.0F, 1.0F);
      consumer.addVertex(matrix, x1, y0, zBack)
         .setColor(255, 255, 255, 255)
         .setUv(0.03125F, 0.8125F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(0.0F, 0.0F, 1.0F);
      consumer.addVertex(matrix, x0, y0, zBack)
         .setColor(255, 255, 255, 255)
         .setUv(0.40625F, 0.8125F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(0.0F, 0.0F, 1.0F);
      consumer.addVertex(matrix, x0, y1, zBack)
         .setColor(255, 255, 255, 255)
         .setUv(0.40625F, 0.0625F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(0.0F, 0.0F, 1.0F);
      consumer.addVertex(matrix, x0, y1, zBack)
         .setColor(255, 255, 255, 255)
         .setUv(0.03125F, 0.0F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(0.0F, 1.0F, 0.0F);
      consumer.addVertex(matrix, x0, y1, zFront)
         .setColor(255, 255, 255, 255)
         .setUv(0.03125F, 0.0625F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(0.0F, 1.0F, 0.0F);
      consumer.addVertex(matrix, x1, y1, zFront)
         .setColor(255, 255, 255, 255)
         .setUv(0.40625F, 0.0625F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(0.0F, 1.0F, 0.0F);
      consumer.addVertex(matrix, x1, y1, zBack)
         .setColor(255, 255, 255, 255)
         .setUv(0.40625F, 0.0F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(0.0F, 1.0F, 0.0F);
      consumer.addVertex(matrix, x0, y0, zFront)
         .setColor(255, 255, 255, 255)
         .setUv(0.40625F, 0.0F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(0.0F, -1.0F, 0.0F);
      consumer.addVertex(matrix, x0, y0, zBack)
         .setColor(255, 255, 255, 255)
         .setUv(0.40625F, 0.0625F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(0.0F, -1.0F, 0.0F);
      consumer.addVertex(matrix, x1, y0, zBack)
         .setColor(255, 255, 255, 255)
         .setUv(0.78125F, 0.0625F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(0.0F, -1.0F, 0.0F);
      consumer.addVertex(matrix, x1, y0, zFront)
         .setColor(255, 255, 255, 255)
         .setUv(0.78125F, 0.0F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(0.0F, -1.0F, 0.0F);
      consumer.addVertex(matrix, x0, y1, zBack)
         .setColor(255, 255, 255, 255)
         .setUv(0.0F, 0.0625F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(-1.0F, 0.0F, 0.0F);
      consumer.addVertex(matrix, x0, y0, zBack)
         .setColor(255, 255, 255, 255)
         .setUv(0.0F, 0.8125F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(-1.0F, 0.0F, 0.0F);
      consumer.addVertex(matrix, x0, y0, zFront)
         .setColor(255, 255, 255, 255)
         .setUv(0.03125F, 0.8125F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(-1.0F, 0.0F, 0.0F);
      consumer.addVertex(matrix, x0, y1, zFront)
         .setColor(255, 255, 255, 255)
         .setUv(0.03125F, 0.0625F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(-1.0F, 0.0F, 0.0F);
      consumer.addVertex(matrix, x1, y1, zFront)
         .setColor(255, 255, 255, 255)
         .setUv(0.40625F, 0.0625F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(1.0F, 0.0F, 0.0F);
      consumer.addVertex(matrix, x1, y0, zFront)
         .setColor(255, 255, 255, 255)
         .setUv(0.40625F, 0.8125F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(1.0F, 0.0F, 0.0F);
      consumer.addVertex(matrix, x1, y0, zBack)
         .setColor(255, 255, 255, 255)
         .setUv(0.4375F, 0.8125F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(1.0F, 0.0F, 0.0F);
      consumer.addVertex(matrix, x1, y1, zBack)
         .setColor(255, 255, 255, 255)
         .setUv(0.4375F, 0.0625F)
         .setOverlay(packedOverlay)
         .setLight(packedLight)
         .setNormal(1.0F, 0.0F, 0.0F);
   }

   private static ResourceLocation resolveTexture(ResourceLocation cultureId) {
      if (cultureId == null) {
         return DEFAULT_TEXTURE;
      }

      Culture culture = ModCultures.getCulture(cultureId);
      return culture == null ? DEFAULT_TEXTURE : culture.panelTexture();
   }

   private void renderIcons(
      VillagePanelBlockEntity blockEntity,
      List<PanelContentGenerator.DisplayLine> lines,
      PoseStack poseStack,
      MultiBufferSource bufferSource,
      int packedLight,
      int packedOverlay
   ) {
      ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
      BufferSource immediate = Minecraft.getInstance().renderBuffers().bufferSource();
      float iconSize = 10.0F;
      float iconLeftX = -48.0F;
      float iconRightX = 48.0F - iconSize;

      for (int i = 0; i < lines.size() && i < 8; i++) {
         PanelContentGenerator.DisplayLine line = lines.get(i);
         float y = -15 + i * 10;
         if (!line.leftIconStack().isEmpty()) {
            drawSingleIconStack(blockEntity, line.leftIconStack(), iconLeftX, y, iconSize, poseStack, immediate, packedLight, packedOverlay, itemRenderer);
         } else if (!line.leftIcon().isEmpty()) {
            drawSingleIcon(blockEntity, line.leftIcon(), iconLeftX, y, iconSize, poseStack, immediate, packedLight, packedOverlay, itemRenderer);
         }

         if (!line.middleIcon().isEmpty()) {
            drawSingleIcon(blockEntity, line.middleIcon(), -iconSize / 2.0F, y, iconSize, poseStack, immediate, packedLight, packedOverlay, itemRenderer);
         }

         if (!line.rightIconStack().isEmpty()) {
            drawSingleIconStack(blockEntity, line.rightIconStack(), iconRightX, y, iconSize, poseStack, immediate, packedLight, packedOverlay, itemRenderer);
         } else if (!line.rightIcon().isEmpty()) {
            drawSingleIcon(blockEntity, line.rightIcon(), iconRightX, y, iconSize, poseStack, immediate, packedLight, packedOverlay, itemRenderer);
         }
      }

      immediate.endBatch();
   }

   private static void drawSingleIcon(
      VillagePanelBlockEntity blockEntity,
      String iconId,
      float x,
      float y,
      float size,
      PoseStack poseStack,
      MultiBufferSource bufferSource,
      int packedLight,
      int packedOverlay,
      ItemRenderer itemRenderer
   ) {
      if (iconId != null && !iconId.isEmpty()) {
         ItemStack iconStack = resolveItemStack(iconId);
         if (!iconStack.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(x + size / 2.0F, y + size / 2.0F, 0.0F);
            poseStack.scale(size, -size, size);
            itemRenderer.renderStatic(iconStack, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, bufferSource, blockEntity.getLevel(), 0);
            poseStack.popPose();
         }
      }
   }

   private static void drawSingleIconStack(
      VillagePanelBlockEntity blockEntity,
      ItemStack iconStack,
      float x,
      float y,
      float size,
      PoseStack poseStack,
      MultiBufferSource bufferSource,
      int packedLight,
      int packedOverlay,
      ItemRenderer itemRenderer
   ) {
      if (!iconStack.isEmpty()) {
         poseStack.pushPose();
         poseStack.translate(x + size / 2.0F, y + size / 2.0F, 0.0F);
         poseStack.scale(size, -size, size);
         itemRenderer.renderStatic(iconStack, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, bufferSource, blockEntity.getLevel(), 0);
         poseStack.popPose();
      }
   }

   private static ItemStack resolveItemStack(String itemId) {
      try {
         Item item = ItemHelper.resolve(itemId);
         return item != null ? new ItemStack(item) : ItemStack.EMPTY;
      } catch (Exception e) {
         LOGGER.warn("Failed to resolve item stack for '{}': {}", itemId, e.getMessage());
         return ItemStack.EMPTY;
      }
   }

   private String truncateWithEllipsis(String text, int maxWidth) {
      if (this.font.width(text) <= maxWidth) {
         return text;
      }

      String ellipsis = "...";

      while (text.length() > 0 && this.font.width(text + ellipsis) > maxWidth) {
         text = text.substring(0, text.length() - 1);
      }

      return text + ellipsis;
   }

   private static String stripFormattingCodes(String text) {
      if (text != null && !text.isEmpty()) {
         StringBuilder sb = new StringBuilder(text.length());

         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 167 && i + 1 < text.length()) {
               i++;
            } else {
               sb.append(c);
            }
         }

         return sb.toString();
      } else {
         return text;
      }
   }
}
