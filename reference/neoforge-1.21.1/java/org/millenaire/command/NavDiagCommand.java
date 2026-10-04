package org.millenaire.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.millenaire.diagnostics.NavEvent;
import org.millenaire.diagnostics.NavigationCounters;
import org.millenaire.diagnostics.NavigationEventLog;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

public final class NavDiagCommand {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int FIND_STUCK_WINDOW_TICKS = 1200;
   private static final int FIND_STUCK_MIN_TELEPORTS = 2;

   private NavDiagCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(
         ((LiteralArgumentBuilder)Commands.literal("nav-trace").executes(ctx -> runTrace(ctx, "nearest")))
            .then(Commands.argument("selector", StringArgumentType.string()).executes(ctx -> runTrace(ctx, StringArgumentType.getString(ctx, "selector"))))
      );
      parent.then(Commands.literal("nav-counters").executes(NavDiagCommand::runCounters));
      parent.then(Commands.literal("nav-counters-reset").executes(ctx -> {
         NavigationCounters.resetAll();
         ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("Nav counters reset."), false);
         return 1;
      }));
      parent.then(
         Commands.literal("nav-watch")
            .then(Commands.argument("selector", StringArgumentType.string()).executes(ctx -> runWatch(ctx, StringArgumentType.getString(ctx, "selector"))))
      );
      parent.then(Commands.literal("find-stuck").executes(NavDiagCommand::runFindStuck));
   }

   private static int runTrace(CommandContext<CommandSourceStack> ctx, String selector) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      MillVillager v = resolveVillager(source, level, selector);
      if (v == null) {
         source.sendFailure(Component.literal("Villager not found: " + selector));
         return 0;
      }

      NavigationEventLog log = v.getNavEventLog();
      List<NavEvent> events = log.snapshot();
      long now = level.getGameTime();
      String header = "Nav trace — "
         + describe(v)
         + " @ "
         + v.blockPosition().toShortString()
         + " (events="
         + events.size()
         + "/256, lastEvent t-"
         + (log.lastEventTick() < 0L ? "∞" : now - log.lastEventTick())
         + ")";
      source.sendSuccess(() -> Component.literal(header), false);
      if (events.isEmpty()) {
         source.sendSuccess(() -> Component.literal("  (no events recorded)"), false);
         return 1;
      }

      for (NavEvent e : events) {
         long age = now - e.tick();
         String line = String.format(Locale.ROOT, "  t-%-5d %s/%s %s", age, e.layer(), e.type(), e.detail());
         source.sendSuccess(() -> Component.literal(line), false);
      }

      GoalScheduler scheduler = v.getGoalScheduler();
      if (scheduler != null) {
         VillagerGoal goal = scheduler.getCurrentGoal();
         String goalId = goal != null ? goal.id().toString() : "idle";
         BlockPos dest = v.getNavManager().getDestination();
         String tail = "Goal="
            + goalId
            + " localStuck="
            + v.getNavManager().getLocalStuck()
            + " longDistStuck="
            + v.getNavManager().getLongDistanceStuck()
            + " teleports="
            + v.getNavManager().getTeleportCount()
            + (dest != null ? " dest=" + dest.toShortString() : "");
         source.sendSuccess(() -> Component.literal(tail), false);
      }

      return 1;
   }

   private static int runCounters(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      StringBuilder sb = new StringBuilder();
      sb.append("Nav counters — ");
      sb.append("teleport.total=").append(NavigationCounters.teleportTotal()).append(" ");

      for (NavEvent.Layer l : NavEvent.Layer.values()) {
         long c = NavigationCounters.teleportFor(l);
         if (c > 0L) {
            sb.append("tp.").append(l).append("=").append(c).append(" ");
         }
      }

      sb.append("short-jump=").append(NavigationCounters.shortJump()).append(" ");
      sb.append("goal.abandoned=").append(NavigationCounters.goalAbandoned()).append(" ");
      sb.append("target.invalid=").append(NavigationCounters.targetInvalid()).append(" ");
      sb.append("reload.pose_restored=").append(NavigationCounters.poseSleepingRestored()).append(" ");
      sb.append("reload.pose_cleared=").append(NavigationCounters.poseSleepingCleared()).append(" ");
      sb.append("bed.suffocation=").append(NavigationCounters.bedSuffocation()).append(" ");
      sb.append("leaf_clear.skipped_in_building=").append(NavigationCounters.leafClearSkippedInBuilding());
      String line = sb.toString();
      source.sendSuccess(() -> Component.literal(line), false);
      return 1;
   }

   private static int runWatch(CommandContext<CommandSourceStack> ctx, String selector) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      MillVillager v = resolveVillager(source, level, selector);
      if (v == null) {
         source.sendFailure(Component.literal("Villager not found: " + selector));
         return 0;
      }

      NavigationEventLog log = v.getNavEventLog();
      if (log.isWatched()) {
         log.disableWatch();
         source.sendSuccess(() -> Component.literal("Watch OFF — " + describe(v)), false);
      } else {
         log.enableWatch(LOGGER, "[nav-watch " + describe(v) + "]");
         source.sendSuccess(() -> Component.literal("Watch ON — " + describe(v) + " (see server log)"), false);
      }

      return 1;
   }

   private static int runFindStuck(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      if (level.dimension() != Level.OVERWORLD) {
         source.sendFailure(Component.literal("Run this command in the Overworld."));
         return 0;
      }

      long now = level.getGameTime();
      List<MillVillager> flagged = new ArrayList<>();
      int scanned = 0;

      for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         for (UUID uuid : village.getVillagerUuids()) {
            if (level.getEntity(uuid) instanceof MillVillager v) {
               scanned++;
               NavigationEventLog log = v.getNavEventLog();
               int recentTps = log.countRecent(NavEvent.Type.TELEPORT, now, 1200L);
               int recentAbandons = log.countRecent(NavEvent.Type.GOAL_ABANDONED, now, 1200L);
               boolean flag = recentTps >= 2 || recentAbandons > 0 || log.hasStuckSinceLastStart();
               if (flag) {
                  flagged.add(v);
               }
            }
         }
      }

      int scannedFinal = scanned;
      source.sendSuccess(() -> Component.literal("Scanned " + scannedFinal + " loaded villagers — " + flagged.size() + " flagged"), false);

      for (MillVillager v : flagged) {
         NavigationEventLog log = v.getNavEventLog();
         int tp = log.countRecent(NavEvent.Type.TELEPORT, now, 1200L);
         int ab = log.countRecent(NavEvent.Type.GOAL_ABANDONED, now, 1200L);
         String line = "  "
            + describe(v)
            + " @ "
            + v.blockPosition().toShortString()
            + " tps20s="
            + tp
            + " abandons20s="
            + ab
            + " stuckSig="
            + log.hasStuckSinceLastStart();
         source.sendSuccess(() -> Component.literal(line), false);
      }

      return 1;
   }

   private static MillVillager resolveVillager(CommandSourceStack source, ServerLevel level, String selector) {
      if (selector != null && !selector.isBlank() && !"-".equals(selector) && !selector.equalsIgnoreCase("nearest")) {
         String needle = selector.toLowerCase(Locale.ROOT);

         for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
            for (UUID uuid : village.getVillagerUuids()) {
               if (level.getEntity(uuid) instanceof MillVillager v) {
                  if (uuid.toString().startsWith(needle)) {
                     return v;
                  }

                  String typeName = v.getVillagerTypeId() != null ? v.getVillagerTypeId().getPath() : "";
                  if (typeName.toLowerCase(Locale.ROOT).contains(needle)) {
                     return v;
                  }

                  if (v.getName() != null && v.getName().getString().toLowerCase(Locale.ROOT).contains(needle)) {
                     return v;
                  }
               }
            }
         }

         return null;
      } else {
         BlockPos origin = source.getPlayer() != null ? source.getPlayer().blockPosition() : BlockPos.ZERO;
         MillVillager best = null;
         double bestSq = Double.MAX_VALUE;

         for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
            for (UUID uuid : village.getVillagerUuids()) {
               if (level.getEntity(uuid) instanceof MillVillager v) {
                  double ds = v.blockPosition().distSqr(origin);
                  if (ds < bestSq) {
                     bestSq = ds;
                     best = v;
                  }
               }
            }
         }

         return best;
      }
   }

   private static String describe(MillVillager v) {
      String type = v.getVillagerTypeId() != null ? v.getVillagerTypeId().getPath() : "?";
      String uuid = v.getUUID().toString().substring(0, 8);
      return type + "[" + uuid + "]";
   }
}
