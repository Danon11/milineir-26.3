package org.millenaire.fabric.villager.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import org.millenaire.fabric.villager.VillagerContent;
import org.millenaire.fabric.villager.VillagerProfile;

public final class VillagerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        for (VillagerProfile.Model model : VillagerProfile.Model.values()) {
            ModelLayerRegistry.registerModelLayer(MillVillagerModels.layer(model, false), () -> MillVillagerModels.create(model, false));
            ModelLayerRegistry.registerModelLayer(MillVillagerModels.layer(model, true), () -> MillVillagerModels.create(model, true));
        }
        EntityRendererRegistry.register(VillagerContent.VILLAGER, MillVillagerRenderer::new);
        // Millénaire menus open as Minecraft-style screens.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(org.millenaire.fabric.ui.MillMenus.Payload.TYPE,
                (payload, context) -> context.client().gui.setScreen(new org.millenaire.fabric.ui.client.MillMenuScreen(
                        org.millenaire.fabric.ui.MillMenu.fromJson(payload.json()))));
    }
}
