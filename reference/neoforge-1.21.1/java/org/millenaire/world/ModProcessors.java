package org.millenaire.world;

import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModProcessors {
   public static final DeferredRegister<StructureProcessorType<?>> PROCESSORS = DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, "millenaire");
   public static final Supplier<StructureProcessorType<MockBlockProcessor>> MOCK_BLOCK_PROCESSOR = PROCESSORS.register(
      "mock_block", () -> () -> MockBlockProcessor.CODEC
   );

   public static void register(IEventBus bus) {
      PROCESSORS.register(bus);
   }

   private ModProcessors() {
   }
}
