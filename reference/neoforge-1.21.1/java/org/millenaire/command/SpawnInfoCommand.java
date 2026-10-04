package org.millenaire.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.millenaire.Millenaire;
import org.millenaire.world.VillageSpawnQueue;

public final class SpawnInfoCommand {
   private static final Gson GSON = new GsonBuilder().create();

   private SpawnInfoCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(Commands.literal("spawn-info").executes(SpawnInfoCommand::execute));
   }

   private static int execute(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      VillageSpawnQueue queue = Millenaire.getSpawnQueue();
      if (queue == null) {
         source.sendFailure(Component.literal("Spawn queue not initialized."));
         return 0;
      } else {
         Map<String, Object> stats = queue.getStats();
         source.sendSuccess(() -> Component.literal(GSON.toJson(stats)), false);
         return 1;
      }
   }
}
