package org.millenaire.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.village.BuildingFinalizer;
import org.millenaire.village.Village;
import org.millenaire.village.VillageGrowthManager;
import org.millenaire.village.VillageSavedData;

public final class GrowCommand {
   private GrowCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(Commands.literal("grow").then(Commands.argument("n", IntegerArgumentType.integer(1, 200)).executes(GrowCommand::execute)));
   }

   private static int execute(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      int n = IntegerArgumentType.getInteger(ctx, "n");
      ServerLevel level = src.getLevel();
      ServerPlayer player = src.getPlayer();
      BlockPos searchPos = player != null ? player.blockPosition() : BlockPos.containing(src.getPosition());
      Village v = VillageSavedData.get(level).getVillageManager().findNearestVillage(searchPos, 5000.0);
      if (v == null) {
         src.sendFailure(Component.translatable("command.millenaire.error.no_village_radius", new Object[]{5000}));
         return 0;
      }

      int placed = 0;

      for (int i = 0; i < n; i++) {
         boolean ok = VillageGrowthManager.rushOneProject(level, v);
         if (!ok) {
            break;
         }

         placed++;
         v.getPathManager().recalculatePaths(level, v, false);
      }

      if (placed > 0) {
         BuildingFinalizer.applyVillageUpdates(level, v);
      }

      v.markDirty();
      int p = placed;
      int skipped = n - placed;
      src.sendSuccess(() -> Component.translatable("command.millenaire.grow.success", new Object[]{p, skipped}), false);
      return placed;
   }
}
