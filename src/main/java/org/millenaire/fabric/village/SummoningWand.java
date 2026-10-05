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
import org.millenaire.fabric.ui.MillMenu;
import org.millenaire.fabric.ui.MillMenus;
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
    private static final Identifier NEGATION = Identifier.fromNamespaceAndPath("millenaire", "negationwand");

    private SummoningWand() {}

    public static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            var item = BuiltInRegistries.ITEM.getKey(player.getItemInHand(hand).getItem());
            if (!item.equals(WAND) && !item.equals(NEGATION)) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
            if (item.equals(WAND)) use(serverPlayer, hit.getBlockPos());
            else negate(serverPlayer, hit.getBlockPos());
            return InteractionResult.SUCCESS;
        });
    }

    private static String cmd(String arguments) { return "/" + COMMAND + " " + arguments; }

    static void use(ServerPlayer player, BlockPos pos) {
        var level = player.level();
        var state = level.getBlockState(pos);
        var settlement = PlayerVillages.settlementAt(level, pos);
        String at = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        if (state.is(Blocks.GOLD_BLOCK) && settlement.isEmpty()) {
            var menu = MillMenu.builder("Found a village").subtitle("The gold block becomes its centre");
            String culture = "";
            for (var type : PlayerVillages.foundable()) {
                if (!type.culture().equals(culture)) { culture = type.culture(); menu.heading(capitalise(culture)); }
                boolean custom = !type.source().first("customcentre", "").isBlank();
                menu.row(type.name() + (custom ? " — around your own town hall" : ""),
                        MillMenu.button("Found", cmd("found " + at + " " + type.culture() + ":" + type.id()), MillMenu.Tone.GOOD));
            }
            MillMenus.show(player, menu.build());
            return;
        }
        if (settlement.isEmpty()) {
            player.sendSystemMessage(Component.literal("Use the wand on a block of gold to found a village.").withStyle(ChatFormatting.GRAY));
            return;
        }
        String key = VillageGrowth.key(settlement.get());
        var ownership = FabricVillageOwnership.get(player.level().getServer());
        if (ownership.owns(key, player.getUUID()) && state.getBlock() instanceof SignBlock) {
            var menu = MillMenu.builder("Register a building").subtitle("What did you build around this sign?");
            var definitions = PlayerVillages.customBuildings();
            for (String id : PlayerVillages.allowedCustom(settlement.get())) {
                var definition = definitions.get(id);
                if (definition == null) continue;
                menu.row((definition.nativeName().isEmpty() ? definition.key() : definition.nativeName()) + ": " + requirements(definition),
                        MillMenu.button("Register", cmd("custom " + at + " " + id), MillMenu.Tone.GOOD));
            }
            MillMenus.show(player, menu.build());
            return;
        }
        MillMenus.show(player, status(player, settlement.get()));
    }

    private static String capitalise(String text) { return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1); }

    /** The negation wand asks for confirmation before undoing anything. */
    static void negate(ServerPlayer player, BlockPos pos) {
        var level = player.level();
        var settlement = PlayerVillages.settlementAt(level, pos);
        if (settlement.isEmpty() || !FabricVillageOwnership.get(level.getServer()).owns(VillageGrowth.key(settlement.get()), player.getUUID())) {
            player.sendSystemMessage(Component.literal("The negation wand only works in a village you own.").withStyle(ChatFormatting.GRAY));
            return;
        }
        String at = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        var origin = new org.millenaire.fabric.content.LegacyBuildingPlan.Position(pos.getX(), pos.getY(), pos.getZ());
        boolean building = level.getBlockState(pos).getBlock() instanceof SignBlock
                && settlement.get().buildings().stream().anyMatch(b -> !b.centre() && b.placement().origin().equals(origin));
        var menu = MillMenu.builder(building ? "Remove a building" : "Dissolve " + settlement.get().name());
        menu.text(building ? "This building will no longer belong to " + settlement.get().name() + "; its residents leave."
                : "The village will be forgotten and its villagers will leave. The buildings stay where they are.");
        menu.row("", MillMenu.button(building ? "Remove building" : "Dissolve village", cmd((building ? "unregister " : "dissolve ") + at + " confirm"), MillMenu.Tone.BAD));
        MillMenus.show(player, menu.build());
    }

    static String requirements(org.millenaire.fabric.content.CustomBuildings.Definition definition) {
        StringBuilder text = new StringBuilder();
        definition.resources().forEach((resource, range) -> {
            if (!text.isEmpty()) text.append(", ");
            text.append(resource).append(' ').append(range.min() == range.max() ? String.valueOf(range.min()) : range.min() + "-" + range.max());
        });
        return text.toString();
    }

    /** The village screen: population, construction, needs, buildings; owners also order buildings here. */
    static MillMenu status(ServerPlayer player, org.millenaire.fabric.FabricSettlementState.Settlement settlement) {
        var server = player.level().getServer();
        String key = VillageGrowth.key(settlement);
        var owner = FabricVillageOwnership.get(server).owner(key);
        boolean owns = owner.map(o -> o.player().equals(player.getStringUUID())).orElse(false);
        long living = FabricVillagerState.get(server).inSettlement(settlement).stream().filter(FabricVillagerState.Villager::alive).count();
        var menu = MillMenu.builder(settlement.name()).subtitle(settlement.type() + owner.map(o -> ", ruled by " + o.name()).orElse(""));
        menu.text(living + " villagers, " + settlement.buildings().size() + " buildings. Your reputation: "
                + org.millenaire.fabric.FabricReputationState.get(server).get(key, player.getUUID()));
        menu.heading("Construction");
        var site = VillageConstruction.site(key);
        if (site.isPresent()) menu.text("Building " + site.get().project().label().replaceFirst("^(build|upgrade) [a-z]+:", "$1 ") + ": " + site.get().progress() + "%");
        else VillageGrowth.pending(key).ifPresentOrElse(p -> menu.text("Waiting for a builder: " + p.label()), () -> menu.text("No construction under way."));
        var needs = VillageGrowth.needs(key);
        if (!needs.isEmpty()) menu.text("Villagers are gathering: " + String.join(", ", needs.entrySet().stream()
                .map(e -> e.getValue() + " " + e.getKey().name().toLowerCase(java.util.Locale.ROOT)).toList()));
        menu.row("Relations with the neighbours", MillMenu.button("Diplomacy", cmd("diplomacy"), MillMenu.Tone.INFO));
        if (owns) {
            menu.heading("Orders");
            menu.text(owner.get().orders().isEmpty() ? "No buildings ordered." : "Ordered: " + String.join(", ", owner.get().orders()));
            for (String plan : PlayerVillages.orderable(settlement)) menu.row(plan, MillMenu.button("Order", cmd("order " + plan), MillMenu.Tone.GOOD));
        }
        menu.heading("Buildings");
        var catalog = org.millenaire.fabric.MillenaireCommands.contentCatalog();
        for (var building : settlement.buildings()) {
            var plan = catalog.plan(building.placement().plan());
            menu.text((plan == null ? building.placement().plan() : plan.parameters().getOrDefault("nativename", java.util.List.of(plan.key())).getLast())
                    + (building.centre() ? " (town hall)" : ""));
        }
        return menu.build();
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
                .then(Commands.literal("dissolve").then(position(Commands.literal("confirm").executes(context -> reply(context.getSource(),
                        PlayerVillages.dissolve(context.getSource().getPlayerOrException(), pos(context)))))))
                .then(Commands.literal("unregister").then(position(Commands.literal("confirm").executes(context -> reply(context.getSource(),
                        PlayerVillages.unregister(context.getSource().getPlayerOrException(), pos(context)))))))
                .then(Commands.literal("hire").then(Commands.argument("villager", StringArgumentType.word()).executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    String result = org.millenaire.fabric.villager.VillagerHiring.hire(player, uuid(StringArgumentType.getString(context, "villager")));
                    context.getSource().sendSuccess(() -> Component.literal(result), false);
                    return 1;
                })))
                .then(Commands.literal("bless").then(Commands.argument("villager", StringArgumentType.word())
                        .then(Commands.argument("enchantment", StringArgumentType.greedyString()).executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            String result = org.millenaire.fabric.villager.VillageRituals.bless(player, uuid(StringArgumentType.getString(context, "villager")),
                                    StringArgumentType.getString(context, "enchantment"));
                            context.getSource().sendSuccess(() -> Component.literal(result), false);
                            return 1;
                        }))))
                .then(Commands.literal("release").then(Commands.argument("villager", StringArgumentType.word()).executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    String result = org.millenaire.fabric.villager.VillagerHiring.release(player, uuid(StringArgumentType.getString(context, "villager")));
                    context.getSource().sendSuccess(() -> Component.literal(result), false);
                    return 1;
                })))
                .then(Commands.literal("journal").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    MillMenus.show(player, journal(player));
                    return 1;
                }))
                .then(Commands.literal("diplomacy").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    MillMenus.show(player, diplomacyMenu(player));
                    return 1;
                }))
                .then(diplomacyAction("raid")).then(diplomacyAction("gift")).then(diplomacyAction("insult"))
                .then(Commands.literal("order").then(Commands.argument("building", StringArgumentType.word())
                        .executes(context -> reply(context.getSource(), PlayerVillages.order(context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "building"))))))
                .then(Commands.literal("info").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    var settlement = PlayerVillages.settlementAt(player.level(), player.blockPosition());
                    if (settlement.isEmpty()) return reply(context.getSource(), new PlayerVillages.Outcome(false, "You are not in a village."));
                    MillMenus.show(player, status(player, settlement.get()));
                    return 1;
                })));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> diplomacyAction(String action) {
        return Commands.literal(action).then(Commands.argument("village", StringArgumentType.greedyString()).executes(context -> reply(context.getSource(),
                PlayerVillages.diplomacy(context.getSource().getPlayerOrException(), action, StringArgumentType.getString(context, "village")))));
    }

    /** The traveller's journal: villages within 2000 blocks or where the player has a reputation, nearest first. */
    static MillMenu journal(ServerPlayer player) {
        var server = player.level().getServer();
        var reputation = org.millenaire.fabric.FabricReputationState.get(server);
        var ownership = FabricVillageOwnership.get(server);
        var dimension = player.level().dimension().identifier();
        var menu = MillMenu.builder("Traveller's journal").subtitle("Villages you know");
        var known = org.millenaire.fabric.FabricSettlementState.get(server).settlements().stream().filter(s -> s.dimension().equals(dimension))
                .map(s -> java.util.Map.entry(s, Math.hypot(s.origin().x() - player.getX(), s.origin().z() - player.getZ())))
                .filter(e -> e.getValue() <= 2000 || reputation.get(VillageGrowth.key(e.getKey()), player.getUUID()) != 0)
                .sorted(java.util.Map.Entry.comparingByValue()).limit(30).toList();
        if (known.isEmpty()) menu.text("No village known nearby.");
        for (var entry : known) {
            var s = entry.getKey();
            String key = VillageGrowth.key(s);
            String direction = org.millenaire.fabric.quest.QuestSites.direction(s.origin().x() - player.getBlockX(), s.origin().z() - player.getBlockZ());
            menu.heading(s.name());
            menu.text(s.type() + ", " + Math.round(entry.getValue()) + " blocks " + direction + " at " + s.origin().x() + " " + s.origin().z()
                    + ". Reputation " + reputation.get(key, player.getUUID()) + ownership.owner(key).map(o -> ", ruled by " + o.name()).orElse(""));
        }
        return menu.build();
    }

    /** Neighbours of the player's village with their relation and, for the owner, gifts, insults and raids. */
    static MillMenu diplomacyMenu(ServerPlayer player) {
        var settlement = PlayerVillages.settlementAt(player.level(), player.blockPosition());
        if (settlement.isEmpty()) return MillMenu.builder("Diplomacy").text("You are not in a village.").build();
        var server = player.level().getServer();
        String key = VillageGrowth.key(settlement.get());
        boolean owner = FabricVillageOwnership.get(server).owns(key, player.getUUID());
        var relations = org.millenaire.fabric.FabricVillageRelations.get(server);
        var menu = MillMenu.builder("Diplomacy").subtitle("Neighbours of " + settlement.get().name());
        var neighbours = VillageRaids.neighbours(server, settlement.get());
        if (neighbours.isEmpty()) menu.text("No village within 1000 blocks.");
        for (var other : neighbours) {
            String otherKey = VillageGrowth.key(other);
            int value = relations.get(key, otherKey);
            menu.heading(other.name());
            String line = other.type() + ", " + (int) VillageRaids.distance(settlement.get(), other) + " blocks: "
                    + org.millenaire.fabric.FabricVillageRelations.describe(value) + " (" + value + ")";
            if (owner) menu.row(line, MillMenu.button("Gift", cmd("gift " + otherKey), MillMenu.Tone.GOOD),
                    MillMenu.button("Insult", cmd("insult " + otherKey)), MillMenu.button("Raid", cmd("raid " + otherKey), MillMenu.Tone.BAD));
            else menu.text(line);
        }
        return menu.build();
    }

    private static java.util.UUID uuid(String text) {
        try { return java.util.UUID.fromString(text); } catch (IllegalArgumentException exception) { return new java.util.UUID(0, 0); }
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
