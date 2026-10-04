package org.millenaire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.ModelType;

@OnlyIn(Dist.CLIENT)
public class ClothingLayer extends RenderLayer<MillVillager, HumanoidModel<MillVillager>> {
   private final Map<ModelType, HumanoidModel<MillVillager>> clothModels;
   private final int layerIndex;

   public ClothingLayer(
      RenderLayerParent<MillVillager, HumanoidModel<MillVillager>> parent, Map<ModelType, HumanoidModel<MillVillager>> clothModels, int layerIndex
   ) {
      super(parent);
      this.clothModels = clothModels;
      this.layerIndex = layerIndex;
   }

   public void render(
      PoseStack poseStack,
      MultiBufferSource bufferSource,
      int packedLight,
      MillVillager entity,
      float limbSwing,
      float limbSwingAmount,
      float partialTick,
      float ageInTicks,
      float netHeadYaw,
      float headPitch
   ) {
      ResourceLocation clothTexture = this.layerIndex == 0 ? entity.getClothTexture0() : entity.getClothTexture1();
      if (clothTexture != null) {
         HumanoidModel<MillVillager> clothModel = this.clothModels.get(entity.getModelType());
         if (clothModel != null) {
            HumanoidModel<MillVillager> parentModel = (HumanoidModel<MillVillager>)this.getParentModel();
            parentModel.copyPropertiesTo(clothModel);
            VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(clothTexture));
            int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);
            clothModel.renderToBuffer(poseStack, consumer, packedLight, overlay, -1);
         }
      }
   }
}
