package org.millenaire.fabric.villager.client;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import org.millenaire.fabric.villager.VillagerProfile;

/** The three legacy 64x32 body models; clothing layers reuse them slightly inflated. */
public final class MillVillagerModels {
    private static final float CLOTH_INFLATION = 0.1F;
    private MillVillagerModels() {}

    public static ModelLayerLocation layer(VillagerProfile.Model model, boolean cloth) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath("millenaire", "villager_" + model.name().toLowerCase(java.util.Locale.ROOT)),
                cloth ? "cloth" : "main");
    }

    public static LayerDefinition create(VillagerProfile.Model model, boolean cloth) {
        CubeDeformation deformation = cloth ? new CubeDeformation(CLOTH_INFLATION) : CubeDeformation.NONE;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8, deformation), PartPose.ZERO);
        head.addOrReplaceChild("hat",
                CubeListBuilder.create().texOffs(32, 0).addBox(-4, -8, -4, 8, 8, 8, deformation.extend(0.5F)), PartPose.ZERO);
        if (model == VillagerProfile.Model.MALE) {
            root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4, 0, -2, 8, 12, 4, deformation), PartPose.ZERO);
            root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-3, -2, -2, 4, 12, 4, deformation),
                    PartPose.offset(-5, 2, 0));
            root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1, -2, -2, 4, 12, 4, deformation),
                    PartPose.offset(5, 2, 0));
            root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2, 0, -2, 4, 12, 4, deformation),
                    PartPose.offset(-1.9F, 12, 0));
            root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2, 0, -2, 4, 12, 4, deformation),
                    PartPose.offset(1.9F, 12, 0));
        } else {
            PartDefinition body = root.addOrReplaceChild("body",
                    CubeListBuilder.create().texOffs(16, 17).addBox(-3.5F, 0, -1.5F, 7, 12, 3, deformation), PartPose.ZERO);
            body.addOrReplaceChild("breast",
                    CubeListBuilder.create().texOffs(17, 18).addBox(-3.5F, 0.75F, -3, 7, 4, 2, deformation), PartPose.ZERO);
            root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(36, 17).addBox(-1.5F, -2, -1.5F, 3, 12, 3, deformation),
                    PartPose.offset(-5, 2, 0));
            root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(36, 17).mirror().addBox(-1.5F, -2, -1.5F, 3, 12, 3, deformation),
                    PartPose.offset(5, 2, 0));
            root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2, 0, -2, 4, 12, 4, deformation),
                    PartPose.offset(-2, 12, 0));
            // The asymmetrical skins paint the left leg separately at u=48.
            CubeListBuilder leftLeg = model == VillagerProfile.Model.FEMALE_ASYMMETRICAL
                    ? CubeListBuilder.create().texOffs(48, 16).addBox(-2, 0, -2, 4, 12, 4, deformation)
                    : CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2, 0, -2, 4, 12, 4, deformation);
            root.addOrReplaceChild("left_leg", leftLeg, PartPose.offset(2, 12, 0));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }
}
