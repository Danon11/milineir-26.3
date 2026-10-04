package org.millenaire.fabric.villager.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.millenaire.fabric.villager.MillVillagerEntity;
import org.millenaire.fabric.villager.VillagerProfile;

import java.util.EnumMap;
import java.util.Map;

/** Draws the legacy skins on the matching body model, then up to two clothing layers. */
public class MillVillagerRenderer extends HumanoidMobRenderer<MillVillagerEntity, MillVillagerRenderState, HumanoidModel<MillVillagerRenderState>> {
    private static final Identifier FALLBACK = Identifier.fromNamespaceAndPath("millenaire", MillVillagerEntity.DEFAULT_TEXTURE);
    private final Map<VillagerProfile.Model, HumanoidModel<MillVillagerRenderState>> bodies = new EnumMap<>(VillagerProfile.Model.class);
    private final Map<VillagerProfile.Model, HumanoidModel<MillVillagerRenderState>> clothes = new EnumMap<>(VillagerProfile.Model.class);

    public MillVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(MillVillagerModels.layer(VillagerProfile.Model.MALE, false))), 0.5F);
        for (VillagerProfile.Model model : VillagerProfile.Model.values()) {
            bodies.put(model, model == VillagerProfile.Model.MALE ? this.model : new HumanoidModel<>(context.bakeLayer(MillVillagerModels.layer(model, false))));
            clothes.put(model, new HumanoidModel<>(context.bakeLayer(MillVillagerModels.layer(model, true))));
        }
        addLayer(new ClothingLayer(this, 0));
        addLayer(new ClothingLayer(this, 1));
    }

    @Override
    public MillVillagerRenderState createRenderState() { return new MillVillagerRenderState(); }

    @Override
    public void extractRenderState(MillVillagerEntity entity, MillVillagerRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.texture = asset(entity.texture());
        state.cloth0 = entity.cloth(0).map(MillVillagerRenderer::asset).orElse(null);
        state.cloth1 = entity.cloth(1).map(MillVillagerRenderer::asset).orElse(null);
        state.model = entity.model();
    }

    private static Identifier asset(String path) {
        Identifier id = Identifier.tryParse("millenaire:" + path);
        return id == null ? FALLBACK : id;
    }

    @Override
    public void submit(MillVillagerRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        this.model = bodies.get(state.model);
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    public Identifier getTextureLocation(MillVillagerRenderState state) { return state.texture == null ? FALLBACK : state.texture; }

    private final class ClothingLayer extends RenderLayer<MillVillagerRenderState, HumanoidModel<MillVillagerRenderState>> {
        private final int layer;

        ClothingLayer(MillVillagerRenderer parent, int layer) {
            super(parent);
            this.layer = layer;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, MillVillagerRenderState state, float yRot, float xRot) {
            Identifier texture = layer == 0 ? state.cloth0 : state.cloth1;
            if (texture == null || state.isInvisible) return;
            coloredCutoutModelCopyLayerRender(clothes.get(state.model), texture, poseStack, collector, light, state, -1, layer + 1);
        }
    }
}
