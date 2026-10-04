package org.millenaire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.entity.MillWallDecoration;
import org.millenaire.entity.WallDecorationVariant;

@OnlyIn(Dist.CLIENT)
public class MillWallDecorationRenderer extends EntityRenderer<MillWallDecoration> {
   private static final float ATLAS_SIZE = 256.0F;

   public MillWallDecorationRenderer(Context context) {
      super(context);
   }

   public ResourceLocation getTextureLocation(MillWallDecoration entity) {
      return entity.getVariant().type().texture();
   }

   public void render(MillWallDecoration entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
      WallDecorationVariant variant = entity.getVariant();
      ResourceLocation texture = variant.type().texture();
      poseStack.pushPose();
      float yaw = directionToYaw(entity.getDirection());
      poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
      poseStack.scale(0.0625F, 0.0625F, 0.0625F);
      VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entitySolid(texture));
      this.renderDecoration(
         poseStack, vertexConsumer, packedLight, variant.widthPixels(), variant.heightPixels(), variant.textureOffsetX(), variant.textureOffsetY()
      );
      poseStack.popPose();
   }

   private void renderDecoration(PoseStack poseStack, VertexConsumer consumer, int packedLight, int width, int height, int textureU, int textureV) {
      float halfWidth = -width / 2.0F;
      float halfHeight = -height / 2.0F;

      for (int col = 0; col < width / 16; col++) {
         for (int row = 0; row < height / 16; row++) {
            float x1 = halfWidth + (col + 1) * 16;
            float x0 = halfWidth + col * 16;
            float y1 = halfHeight + (row + 1) * 16;
            float y0 = halfHeight + row * 16;
            float u0 = (textureU + width - col * 16) / 256.0F;
            float u1 = (textureU + width - (col + 1) * 16) / 256.0F;
            float v0 = (textureV + height - row * 16) / 256.0F;
            float v1 = (textureV + height - (row + 1) * 16) / 256.0F;
            float backU0 = 0.75F;
            float backU1 = 0.8125F;
            float backV0 = 0.0F;
            float backV1 = 0.0625F;
            float edgeU = 0.75390625F;
            float edgeV0 = 0.0F;
            float edgeV1 = 0.00390625F;
            Pose pose = poseStack.last();
            vertex(consumer, pose, x1, y0, -0.5F, u1, v0, 0, 0, -1, packedLight);
            vertex(consumer, pose, x0, y0, -0.5F, u0, v0, 0, 0, -1, packedLight);
            vertex(consumer, pose, x0, y1, -0.5F, u0, v1, 0, 0, -1, packedLight);
            vertex(consumer, pose, x1, y1, -0.5F, u1, v1, 0, 0, -1, packedLight);
            vertex(consumer, pose, x1, y1, 0.5F, backU0, backV0, 0, 0, 1, packedLight);
            vertex(consumer, pose, x0, y1, 0.5F, backU1, backV0, 0, 0, 1, packedLight);
            vertex(consumer, pose, x0, y0, 0.5F, backU1, backV1, 0, 0, 1, packedLight);
            vertex(consumer, pose, x1, y0, 0.5F, backU0, backV1, 0, 0, 1, packedLight);
            vertex(consumer, pose, x1, y1, -0.5F, edgeU, edgeV0, 0, 1, 0, packedLight);
            vertex(consumer, pose, x0, y1, -0.5F, edgeU, edgeV0, 0, 1, 0, packedLight);
            vertex(consumer, pose, x0, y1, 0.5F, edgeU, edgeV1, 0, 1, 0, packedLight);
            vertex(consumer, pose, x1, y1, 0.5F, edgeU, edgeV1, 0, 1, 0, packedLight);
            vertex(consumer, pose, x1, y0, 0.5F, edgeU, edgeV0, 0, -1, 0, packedLight);
            vertex(consumer, pose, x0, y0, 0.5F, edgeU, edgeV0, 0, -1, 0, packedLight);
            vertex(consumer, pose, x0, y0, -0.5F, edgeU, edgeV1, 0, -1, 0, packedLight);
            vertex(consumer, pose, x1, y0, -0.5F, edgeU, edgeV1, 0, -1, 0, packedLight);
            vertex(consumer, pose, x1, y1, 0.5F, edgeU, edgeV0, -1, 0, 0, packedLight);
            vertex(consumer, pose, x1, y0, 0.5F, edgeU, edgeV1, -1, 0, 0, packedLight);
            vertex(consumer, pose, x1, y0, -0.5F, edgeU, edgeV1, -1, 0, 0, packedLight);
            vertex(consumer, pose, x1, y1, -0.5F, edgeU, edgeV0, -1, 0, 0, packedLight);
            vertex(consumer, pose, x0, y1, -0.5F, edgeU, edgeV0, 1, 0, 0, packedLight);
            vertex(consumer, pose, x0, y0, -0.5F, edgeU, edgeV1, 1, 0, 0, packedLight);
            vertex(consumer, pose, x0, y0, 0.5F, edgeU, edgeV1, 1, 0, 0, packedLight);
            vertex(consumer, pose, x0, y1, 0.5F, edgeU, edgeV0, 1, 0, 0, packedLight);
         }
      }
   }

   private static void vertex(VertexConsumer consumer, Pose pose, float x, float y, float z, float u, float v, int nx, int ny, int nz, int packedLight) {
      consumer.addVertex(pose, x, y, z)
         .setColor(255, 255, 255, 255)
         .setUv(u, v)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(packedLight)
         .setNormal(pose, nx, ny, nz);
   }

   private static float directionToYaw(Direction direction) {
      return switch (direction) {
         case SOUTH -> 0.0F;
         case WEST -> 90.0F;
         case NORTH -> 180.0F;
         case EAST -> 270.0F;
         default -> 0.0F;
      };
   }
}
