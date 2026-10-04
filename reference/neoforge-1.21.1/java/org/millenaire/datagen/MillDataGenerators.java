package org.millenaire.datagen;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = "millenaire")
public class MillDataGenerators {
   @SubscribeEvent
   public static void gatherData(GatherDataEvent event) {
      DataGenerator generator = event.getGenerator();
      PackOutput output = generator.getPackOutput();
      ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
      generator.addProvider(event.includeClient(), new MillBlockStateProvider(output, existingFileHelper));
      generator.addProvider(event.includeServer(), MillLootTableProvider.create(output, event.getLookupProvider()));
   }
}
