package org.millenaire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.millenaire.block.LockedChestBlock;
import org.millenaire.block.LockedChestBlockEntity;

public class LockedChestRenderer implements BlockEntityRenderer<LockedChestBlockEntity> {
   private static final Material LOCKED_CHEST = new Material(
      Sheets.CHEST_SHEET, ResourceLocation.fromNamespaceAndPath("millenaire", "entity/chest/locked_normal")
   );
   private static final Material LOCKED_CHEST_LEFT = new Material(
      Sheets.CHEST_SHEET, ResourceLocation.fromNamespaceAndPath("millenaire", "entity/chest/locked_normal_left")
   );
   private static final Material LOCKED_CHEST_RIGHT = new Material(
      Sheets.CHEST_SHEET, ResourceLocation.fromNamespaceAndPath("millenaire", "entity/chest/locked_normal_right")
   );
   private final ModelPart singleBottom;
   private final ModelPart singleLid;
   private final ModelPart singleLock;
   private final ModelPart leftBottom;
   private final ModelPart leftLid;
   private final ModelPart leftLock;
   private final ModelPart rightBottom;
   private final ModelPart rightLid;
   private final ModelPart rightLock;

   public LockedChestRenderer(Context context) {
      ModelPart single = context.bakeLayer(ModelLayers.CHEST);
      this.singleBottom = single.getChild("bottom");
      this.singleLid = single.getChild("lid");
      this.singleLock = single.getChild("lock");
      ModelPart left = context.bakeLayer(ModelLayers.DOUBLE_CHEST_LEFT);
      this.leftBottom = left.getChild("bottom");
      this.leftLid = left.getChild("lid");
      this.leftLock = left.getChild("lock");
      ModelPart right = context.bakeLayer(ModelLayers.DOUBLE_CHEST_RIGHT);
      this.rightBottom = right.getChild("bottom");
      this.rightLid = right.getChild("lid");
      this.rightLock = right.getChild("lock");
   }

   public void render(
      LockedChestBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay
   ) {
      BlockState state = blockEntity.getBlockState();
      ChestType chestType = state.hasProperty(ChestBlock.TYPE) ? (ChestType)state.getValue(ChestBlock.TYPE) : ChestType.SINGLE;
      poseStack.pushPose();
      Direction facing = state.hasProperty(LockedChestBlock.FACING) ? (Direction)state.getValue(LockedChestBlock.FACING) : Direction.NORTH;
      float rotation = facing.toYRot();
      poseStack.translate(0.5F, 0.5F, 0.5F);
      poseStack.mulPose(Axis.YP.rotationDegrees(-rotation));
      poseStack.translate(-0.5F, -0.5F, -0.5F);
      ModelPart lid;
      ModelPart lock;
      ModelPart bottom;
      Material material;
      switch (chestType) {
         case LEFT:
            lid = this.leftLid;
            lock = this.leftLock;
            bottom = this.leftBottom;
            material = LOCKED_CHEST_LEFT;
            break;
         case RIGHT:
            lid = this.rightLid;
            lock = this.rightLock;
            bottom = this.rightBottom;
            material = LOCKED_CHEST_RIGHT;
            break;
         default:
            lid = this.singleLid;
            lock = this.singleLock;
            bottom = this.singleBottom;
            material = LOCKED_CHEST;
      }

      float openness = blockEntity.getOpenNess(partialTick);
      openness = 1.0F - openness;
      openness = 1.0F - openness * openness * openness;
      lid.xRot = -(openness * (float) (Math.PI / 2));
      lock.xRot = lid.xRot;
      VertexConsumer vertexConsumer = material.buffer(bufferSource, RenderType::entityCutout);
      lid.render(poseStack, vertexConsumer, packedLight, packedOverlay);
      lock.render(poseStack, vertexConsumer, packedLight, packedOverlay);
      bottom.render(poseStack, vertexConsumer, packedLight, packedOverlay);
      poseStack.popPose();
   }
}
