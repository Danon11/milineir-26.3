package org.millenaire.fabric.firepit;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public final class FirePitClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        registerClient();
    }

    public static void registerClient() {
        MenuScreens.register(FirePitContent.MENU_TYPE, FirePitScreen::new);
    }
}
