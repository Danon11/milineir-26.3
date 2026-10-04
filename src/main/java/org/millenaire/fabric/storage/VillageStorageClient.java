package org.millenaire.fabric.storage;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.StandingSignRenderer;

public final class VillageStorageClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        BlockEntityRenderers.register(VillageStorageContent.CHEST_TYPE, ChestRenderer::new);
        BlockEntityRenderers.register(VillageStorageContent.PANEL_TYPE, StandingSignRenderer::new);
    }
}
