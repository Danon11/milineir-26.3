package org.millenaire.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class DevCommand {
   private DevCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      LiteralArgumentBuilder<CommandSourceStack> dev = (LiteralArgumentBuilder<CommandSourceStack>)Commands.literal("dev")
         .requires(source -> source.hasPermission(2));
      DebugCommand.registerUnder(dev);
      StatusCommand.registerUnder(dev);
      InventoryCommand.registerUnder(dev);
      SpawnBuildingCommand.registerUnder(dev);
      RoundtripCommand.registerUnder(dev);
      ExportCommand.registerUnder(dev);
      QuestCommand.registerUnder(dev);
      NavDiagCommand.registerUnder(dev);
      WaypointDiagCommand.registerUnder(dev);
      WaypointGraphCommand.registerUnder(dev);
      SpawnInfoCommand.registerUnder(dev);
      PathAuditCommand.registerUnder(dev);
      PathsCommand.registerUnder(dev);
      GrowCommand.registerUnder(dev);
      ConvertAddonCommand.registerUnder(dev);
      ImportCultureCommand.registerUnder(dev);
      ImportTableDevCommand.registerUnder(dev);
      SwitchVillageControlCommand.registerUnder(dev);
      GrantCultureControlCommand.registerUnder(dev);
      dispatcher.register((LiteralArgumentBuilder)Commands.literal("millenaire").then(dev));
   }
}
