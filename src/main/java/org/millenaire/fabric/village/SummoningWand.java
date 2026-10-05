package org.millenaire.fabric.village;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SignBlock;
import org.millenaire.fabric.FabricVillageOwnership;
import org.millenaire.fabric.FabricVillagerState;

/**
 * The summoning wand: used on a gold block it offers the village types a player can found; on a sign in the
 * player's village, the custom buildings to register there; anywhere else in the village, its status and the
 * buildings to order. Choices are chat buttons running {@code /millenaire_village}, which needs no operator rights.
 */
public final class SummoningWand {
    public static final String COMMAND = "millenaire_village";
    private static final Identifier WAND = Identifier.fromNamespaceAndPath("millenaire", "summoningwand");

    private SummoningWand() {}

    public static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!BuiltInRegistries.ITEM.getKey(player.getItemInHand(hand).getItem()).equals(WAND)) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
            use(serverPlayer, hit.getBlockPos());
            return InteractionResult.SUCCESS;
        });
    }

    static void use(ServerPlayer player, BlockPos pos) {
        var level = player.level();
        var state = level.getBlockState(pos);
        var settlement = PlayerVillages.settlementAt(level, pos);
        if (state.is(Blocks.GOLD_BLOCK) && settlement.isEmpty()) {
            MutableComponent menu = Component.literal("Found a village here:").withStyle(ChatFormatting.GOLD);
            for (var type : PlayerVillages.foundable())
                menu.append(Component.literal("\n ")).append(button("[" + type.name() + "]", ChatFormatting.GREEN,
                        "found " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + type.culture() + ":" + type.id()))
                        .append(Component.literal(" " + type.culture() + (type.source().first("customcentre", "").isBlank() ? "" : ", around your own building"))
                                .withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(menu);
            return;
        }
        if (settlement.isEmpty()) {
            player.sendSystemMessage(Component.literal("Use the wand on a block of gold to found a village.").withStyle(ChatFormatting.GRAY));
            return;
        }
        String key = VillageGrowth.key(settlement.get());
        var ownership = FabricVillageOwnership.get(player.level().getServer());
        if (!ownership.owns(key, player.getUUID())) {
            String owner = ownership.owner(key).map(FabricVillageOwnership.Owner::name).orElse("nobody");
            player.sendSystemMessage(Component.literal(settlement.get().name() + " (" + settlement.get().type() + ") is ruled by " + owner + ".")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        if (state.getBlock() instanceof SignBlock) {
            MutableComponent menu = Component.literal("Register the building around this sign as:").withStyle(ChatFormatting.GOLD);
            var definitions = PlayerVillages.customBuildings();
            for (String id : PlayerVillages.allowedCustom(settlement.get())) {
                var definition = definitions.get(id);
                if (definition == null) continue;
                menu.append(Component.literal("\n ")).append(button("[" + (definition.nativeName().isEmpty() ? definition.key() : definition.nativeName()) + "]",
                        ChatFormatting.GREEN, "custom " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + id))
                        .append(Component.literal(" " + requirements(definition)).withStyle(ChatFormatting.GRAY));
            }
            player.sendSystemMessage(menu);
            return;
        }
        player.sendSystemMessage(status(player, settlement.get()));
    }

    static String requirements(org.millenaire.fabric.content.CustomBuildings.Definition definition) {
        StringBuilder text = new StringBuilder();
        definition.resources().forEach((resource, range) -> {
            if (!text.isEmpty()) text.append(", ");
            text.append(resource).append(' ').append(range.min() == range.max() ? String.valueOf(range.min()) : range.min() + "-" + range.max());
        });
        return text.toString();
    }

    static MutableComponent status(ServerPlayer player, org.millenaire.fabric.FabricSettlementState.Settlement settlement) {
        var server = player.level().getServer();
        String key = VillageGrowth.key(settlement);
        var owner = FabricVillageOwnership.get(server).owner(key);
        long living = FabricVillagerState.get(server).inSettlement(settlement).stream().filter(FabricVillagerState.Villager::alive).count();
        MutableComponent message = Component.literal(settlement.name() + ": " + settlement.buildings().size() + " buildings, " + living + " villagers")
                .withStyle(ChatFormatting.GOLD);
        owner.ifPresent(o -> message.append(Component.literal(o.orders().isEmpty() ? "\nNo buildings ordered." : "\nOrdered: " + String.join(", ", o.orders()))
                .withStyle(ChatFormatting.WHITE)));
        message.append(Component.literal("\nOrder a building:").withStyle(ChatFormatting.GOLD));
        for (String plan : PlayerVillages.orderable(settlement))
            message.append(Component.literal(" ")).append(button("[" + plan + "]", ChatFormatting.GREEN, "order " + plan));
        return message;
    }

    private static MutableComponent button(String label, ChatFormatting colour, String arguments) {
        return Component.literal(label).withStyle(style -> style.withColor(colour)
                .withClickEvent(new ClickEvent.RunCommand("/" + COMMAND + " " + arguments)));
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(COMMAND)
                .then(Commands.literal("found").then(position(Commands.argument("type", StringArgumentType.greedyString())
                        .executes(context -> reply(context.getSource(), PlayerVillages.found(context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "type"), pos(context)))))))
                .then(Commands.literal("custom").then(position(Commands.argument("building", StringArgumentType.greedyString())
                        .executes(context -> reply(context.getSource(), PlayerVillages.registerCustom(context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "building"), pos(context)))))))
                .then(Commands.literal("order").then(Commands.argument("building", StringArgumentType.word())
                        .executes(context -> reply(context.getSource(), PlayerVillages.order(context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "building"))))))
                .then(Commands.literal("info").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    var settlement = PlayerVillages.settlementAt(player.level(), player.blockPosition());
                    if (settlement.isEmpty()) return reply(context.getSource(), new PlayerVillages.Outcome(false, "You are not in a village."));
                    context.getSource().sendSuccess(() -> status(player, settlement.get()), false);
                    return 1;
                })));
    }

    /** {@code <x> <y> <z>} followed by {@code tail}. */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> position(
            com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> tail) {
        return Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("y", IntegerArgumentType.integer())
                .then(Commands.argument("z", IntegerArgumentType.integer()).then(tail)));
    }

    private static BlockPos pos(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        return new BlockPos(IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "y"), IntegerArgumentType.getInteger(context, "z"));
    }

    private static int reply(CommandSourceStack source, PlayerVillages.Outcome outcome) {
        if (outcome.success()) source.sendSuccess(() -> Component.literal(outcome.message()).withStyle(ChatFormatting.GREEN), true);
        else source.sendFailure(Component.literal(outcome.message()));
        return outcome.success() ? 1 : 0;
    }
}
