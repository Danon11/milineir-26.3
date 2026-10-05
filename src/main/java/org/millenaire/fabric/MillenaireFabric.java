package org.millenaire.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.millenaire.fabric.culture.CultureDescriptor;
import org.millenaire.fabric.culture.CultureDescriptorLoader;
import org.millenaire.fabric.firepit.FirePitContent;
import org.millenaire.fabric.content.LegacyCatalogLoader;

import java.io.IOException;
import java.util.List;

public final class MillenaireFabric implements ModInitializer {
    private static final System.Logger LOGGER = System.getLogger("Millenaire");

    @Override
    public void onInitialize() {
        LOGGER.log(System.Logger.Level.INFO, "Millenaire Fabric bootstrap initialized");
        FirePitContent.registerCommon();
        LegacyContentRegistry.register();
        org.millenaire.fabric.storage.VillageStorageContent.registerBlockEntities();
        org.millenaire.fabric.villager.VillagerContent.register();
        MillenaireCommands.register();
        org.millenaire.fabric.village.SummoningWand.register();
        ServerTickEvents.END_SERVER_TICK.register(server ->
                {
                    FabricSettlementLifecycleState.get(server).tick();
                    org.millenaire.fabric.quest.QuestService.tick(server);
                    org.millenaire.fabric.village.VillageGrowth.tick(server);
                    org.millenaire.fabric.village.VillagePopulation.tick(server);
                    org.millenaire.fabric.village.VillageRaids.tick(server);
                    org.millenaire.fabric.quest.QuestSites.tick(server);
                    org.millenaire.fabric.village.WorldVillageGenerator.tick(server);
                });
        FabricLoader.getInstance().getModContainer("millenaire").ifPresentOrElse(
                modContainer -> {
                    FabricContentDeployer.deploy(modContainer);
                    try {
                        List<CultureDescriptor> cultures = CultureDescriptorLoader.loadAll(
                                FabricLoader.getInstance().getGameDir());
                        MillenaireCommands.setCultureDescriptors(cultures);
                        var catalog = LegacyCatalogLoader.loadGame(FabricLoader.getInstance().getGameDir());
                        MillenaireCommands.setContentCatalog(catalog);
                        LOGGER.log(System.Logger.Level.INFO, "Indexed " + catalog.plans().size() + " building plans, "
                                + catalog.count("villages") + " village types, " + catalog.count("villagers") + " villager types");
                        for (String diagnostic : catalog.diagnostics()) LOGGER.log(System.Logger.Level.WARNING, diagnostic);
                        MillenaireCommands.loadQuestTexts(FabricLoader.getInstance().getGameDir());
                        org.millenaire.fabric.village.WorldVillageGenerator.configure(FabricLoader.getInstance().getGameDir());
                        var quests = MillenaireCommands.questCatalog();
                        LOGGER.log(System.Logger.Level.INFO, "Loaded " + quests.quests().size() + " quest definitions");
                        for (String diagnostic : quests.diagnostics()) LOGGER.log(System.Logger.Level.WARNING, diagnostic);
                        LOGGER.log(System.Logger.Level.INFO, "Loaded " + cultures.size()
                                + " Millenaire culture descriptors");
                    } catch (IOException exception) {
                        LOGGER.log(System.Logger.Level.ERROR, "Could not load Millenaire culture descriptors", exception);
                    }
                },
                () -> LOGGER.log(System.Logger.Level.ERROR, "Millenaire mod container was not found"));
    }
}
