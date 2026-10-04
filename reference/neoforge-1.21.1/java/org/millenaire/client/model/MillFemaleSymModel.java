package org.millenaire.client.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.entity.MillVillager;

@OnlyIn(Dist.CLIENT)
public class MillFemaleSymModel extends HumanoidModel<MillVillager> {
   public MillFemaleSymModel(ModelPart root) {
      super(root);
   }

   public static LayerDefinition createBodyLayer(CubeDeformation deformation) {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      PartDefinition head = root.addOrReplaceChild(
         "head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, deformation), PartPose.ZERO
      );
      root.addOrReplaceChild(
         "hat", CubeListBuilder.create().texOffs(32, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, deformation.extend(0.5F)), PartPose.ZERO
      );
      PartDefinition body = root.addOrReplaceChild(
         "body", CubeListBuilder.create().texOffs(16, 17).addBox(-3.5F, 0.0F, -1.5F, 7.0F, 12.0F, 3.0F, deformation), PartPose.ZERO
      );
      body.addOrReplaceChild("breast", CubeListBuilder.create().texOffs(17, 18).addBox(-3.5F, 0.75F, -3.0F, 7.0F, 4.0F, 2.0F, deformation), PartPose.ZERO);
      root.addOrReplaceChild(
         "right_arm", CubeListBuilder.create().texOffs(36, 17).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 12.0F, 3.0F, deformation), PartPose.offset(-5.0F, 2.0F, 0.0F)
      );
      root.addOrReplaceChild(
         "left_arm",
         CubeListBuilder.create().texOffs(36, 17).mirror().addBox(-1.5F, -2.0F, -1.5F, 3.0F, 12.0F, 3.0F, deformation),
         PartPose.offset(5.0F, 2.0F, 0.0F)
      );
      root.addOrReplaceChild(
         "right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, deformation), PartPose.offset(-2.0F, 12.0F, 0.0F)
      );
      root.addOrReplaceChild(
         "left_leg",
         CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, deformation),
         PartPose.offset(2.0F, 12.0F, 0.0F)
      );
      return LayerDefinition.create(mesh, 64, 32);
   }
}
