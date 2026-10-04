package org.millenaire.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.millenaire.quest.Quest;
import org.millenaire.quest.QuestInstance;
import org.millenaire.quest.QuestManager;
import org.millenaire.quest.QuestRegistry;
import org.millenaire.village.PlayerQuestData;

public final class QuestCommand {
   private static final SuggestionProvider<CommandSourceStack> QUEST_KEY_SUGGESTIONS = (ctx, builder) -> SharedSuggestionProvider.suggest(
      QuestRegistry.all().stream().map(Quest::key), builder
   );

   private QuestCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(
         ((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("quest").then(Commands.literal("test").executes(QuestCommand::testQuests)))
               .then(
                  Commands.literal("force")
                     .then(Commands.argument("questKey", StringArgumentType.string()).suggests(QUEST_KEY_SUGGESTIONS).executes(QuestCommand::forceQuest))
               ))
            .then(Commands.literal("list").executes(QuestCommand::listQuests))
      );
   }

   private static int testQuests(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = source.getPlayer();
      if (player == null) {
         source.sendFailure(Component.translatable("command.millenaire.error.player_required"));
         return 0;
      }

      ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);
      if (level == null) {
         source.sendFailure(Component.translatable("command.millenaire.error.no_overworld"));
         return 0;
      }

      ServerLevel overworld = source.getServer().getLevel(Level.OVERWORLD);
      PlayerQuestData data = PlayerQuestData.get(overworld, QuestRegistry::get);
      int before = data.getActiveQuests(player.getUUID()).size();
      QuestManager.testQuestsNow(player, level, false);
      int after = data.getActiveQuests(player.getUUID()).size();
      int newQuests = after - before;
      if (newQuests > 0) {
         source.sendSuccess(() -> Component.translatable("command.millenaire.quest.test_new", new Object[]{newQuests, after}), false);
      } else {
         source.sendSuccess(() -> Component.translatable("command.millenaire.quest.test_none", new Object[]{after}), false);
      }

      return 1;
   }

   private static int forceQuest(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = source.getPlayer();
      if (player == null) {
         source.sendFailure(Component.translatable("command.millenaire.error.player_required"));
         return 0;
      }

      String questKey = StringArgumentType.getString(ctx, "questKey");
      Quest quest = QuestRegistry.get(questKey);
      if (quest == null) {
         source.sendFailure(Component.translatable("command.millenaire.quest.unknown", new Object[]{questKey}));
         return 0;
      }

      ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);
      if (level == null) {
         source.sendFailure(Component.translatable("command.millenaire.error.no_overworld"));
         return 0;
      }

      boolean success = QuestManager.tryInstantiateForced(quest, player, level);
      if (success) {
         source.sendSuccess(() -> Component.translatable("command.millenaire.quest.forced", new Object[]{questKey}), false);
      } else {
         source.sendFailure(
            Component.translatable(
               "command.millenaire.quest.force_failed", new Object[]{questKey, "preconditions not met: tags, reputation, or no matching villagers"}
            )
         );
      }

      return success ? 1 : 0;
   }

   private static int listQuests(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = source.getPlayer();
      if (player == null) {
         source.sendFailure(Component.translatable("command.millenaire.error.player_required"));
         return 0;
      }

      ServerLevel overworld = source.getServer().getLevel(Level.OVERWORLD);
      if (overworld == null) {
         source.sendFailure(Component.translatable("command.millenaire.error.no_overworld"));
         return 0;
      }

      PlayerQuestData data = PlayerQuestData.get(overworld, QuestRegistry::get);
      List<QuestInstance> active = data.getActiveQuests(player.getUUID());
      if (active.isEmpty()) {
         source.sendSuccess(() -> Component.translatable("command.millenaire.quest.none_active"), false);
         return 1;
      }

      source.sendSuccess(() -> Component.translatable("command.millenaire.quest.list_header", new Object[]{active.size()}), false);

      for (QuestInstance qi : active) {
         String questKey = qi.getQuest().key();
         int step = qi.getCurrentStepIndex();
         int totalSteps = qi.getQuest().steps().size();
         source.sendSuccess(() -> Component.translatable("command.millenaire.quest.list_line", new Object[]{questKey, step + 1, totalSteps}), false);
      }

      return 1;
   }
}
