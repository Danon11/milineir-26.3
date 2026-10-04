package org.millenaire.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import org.millenaire.fabric.content.BuildingPlacement;
import org.millenaire.fabric.content.LegacyBuildingPlan;
import org.millenaire.fabric.content.VillageLayout;
import org.millenaire.fabric.content.VillagePlacement;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.fabric.culture.CultureDescriptor;
import org.millenaire.fabric.content.LegacyContentCatalog;
import org.millenaire.fabric.content.LegacyCatalogLoader;
import org.millenaire.fabric.quest.QuestCatalog;
import org.millenaire.fabric.quest.QuestDefinition;
import org.millenaire.fabric.quest.QuestTexts;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;

import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class MillenaireCommands {
    private static volatile List<CultureDescriptor> cultureDescriptors = List.of();
    private static volatile LegacyContentCatalog contentCatalog = LegacyContentCatalog.empty();
    private static volatile QuestCatalog questCatalog = QuestCatalog.empty();
    private static volatile QuestTexts questTexts = QuestTexts.empty();

    public static void setContentCatalog(LegacyContentCatalog catalog) {
        QuestCatalog quests = QuestCatalog.from(catalog);
        contentCatalog = catalog;
        questCatalog = quests;
    }

    public static QuestCatalog questCatalog() { return questCatalog; }

    /** Loads quest strings from the same bundled and custom roots as the content catalog. */
    public static void loadQuestTexts(java.nio.file.Path game) throws IOException {
        java.nio.file.Path mods = game.toAbsolutePath().normalize().resolve("mods");
        questTexts = QuestTexts.load(QuestTexts.FALLBACK_LANGUAGE, mods.resolve("millenaire"), mods.resolve("millenaire-custom"));
    }

    private MillenaireCommands() {
    }

    public static void setCultureDescriptors(List<CultureDescriptor> cultures) {
        cultureDescriptors = List.copyOf(cultures);
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) ->
                register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("millenaire")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("village")
                        .then(Commands.literal("mark").executes(context -> mark(context.getSource())))
                        .then(Commands.literal("list").executes(context -> list(context.getSource())))
                        .then(Commands.literal("settlements").executes(context -> listSettlements(context.getSource())))
                        .then(lifecycleCommand())
                        .then(Commands.literal("villagers").executes(context -> listVillagers(context.getSource())))
                        .then(villageAction("plan", false))
                        .then(villageAction("check", false))
                        .then(villageAction("checkreplace", true))
                        .then(villageAction("place", false))
                        .then(villageAction("replace", true))
                        .then(Commands.literal("types").then(Commands.argument("culture", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    contentCatalog.cultures().keySet().stream().filter(id -> id.startsWith(builder.getRemaining()))
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> villageTypes(context.getSource(), StringArgumentType.getString(context, "culture"))))))
                .then(Commands.literal("quest")
                        .then(Commands.literal("list").executes(context -> listQuests(context.getSource(), ""))
                                .then(Commands.argument("group", StringArgumentType.string())
                                        .suggests((context, builder) -> {
                                            questCatalog.quests().values().stream().map(QuestDefinition::group).distinct()
                                                    .filter(group -> group.startsWith(builder.getRemaining())).forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> listQuests(context.getSource(), StringArgumentType.getString(context, "group")))))
                        .then(Commands.literal("info").then(Commands.argument("quest", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    questCatalog.quests().keySet().stream().filter(path -> path.startsWith(builder.getRemaining()))
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> questInfo(context.getSource(), StringArgumentType.getString(context, "quest"))))))
                .then(Commands.literal("culture")
                        .then(Commands.literal("list").executes(context -> listCultures(context.getSource()))))
                .then(Commands.literal("content")
                        .then(Commands.literal("stats").executes(context -> contentStats(context.getSource())))
                        .then(Commands.literal("goods").then(Commands.argument("good", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    contentCatalog.goods().goods().keySet().stream().filter(key -> key.startsWith(builder.getRemaining())).forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> goodInfo(context.getSource(), StringArgumentType.getString(context, "good")))))
                        .then(Commands.literal("reload").executes(context -> reloadContent(context.getSource()))))
                .then(Commands.literal("building")
                        .then(Commands.literal("list").executes(context -> listBuildings(context.getSource())))
                        .then(buildingAction("check", false))
                        .then(buildingAction("place", false))
                        .then(buildingAction("replace", true))
                        .then(Commands.literal("info")
                                .then(Commands.argument("plan", StringArgumentType.greedyString())
                                        .suggests((context, builder) -> {
                                            contentCatalog.plans().keySet().stream()
                                                    .filter(id -> id.startsWith(builder.getRemaining())).forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> buildingInfo(context.getSource(), StringArgumentType.getString(context, "plan")))))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> lifecycleCommand() {
        var add = Commands.literal("add");
        var addMaterials = Commands.argument("materials", IntegerArgumentType.integer(0))
                .executes(context -> addLifecycleResources(context.getSource(),
                        IntegerArgumentType.getInteger(context, "food"),
                        IntegerArgumentType.getInteger(context, "materials")));
        add.then(Commands.argument("food", IntegerArgumentType.integer(0)).then(addMaterials));

        var queue = Commands.literal("queue");
        var queueMaterials = Commands.argument("materials", IntegerArgumentType.integer(0))
                .executes(context -> queueLifecycleProject(context.getSource(),
                        StringArgumentType.getString(context, "project"),
                        StringArgumentType.getString(context, "plan"),
                        IntegerArgumentType.getInteger(context, "work"),
                        IntegerArgumentType.getInteger(context, "materials")));
        queue.then(Commands.argument("project", StringArgumentType.word())
                .then(Commands.argument("plan", StringArgumentType.word())
                        .then(Commands.argument("work", IntegerArgumentType.integer(1)).then(queueMaterials))));

        return Commands.literal("lifecycle")
                .executes(context -> listLifecycle(context.getSource()))
                .then(add)
                .then(queue);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> villageAction(String action, boolean replace) {
        return Commands.literal(action).then(Commands.argument("seed", LongArgumentType.longArg())
                .then(Commands.argument("type", StringArgumentType.greedyString())
                        .suggests((context, builder) -> {
                            contentCatalog.cultures().values().forEach(culture -> culture.villageTypes().keySet().stream()
                                    .map(id -> culture.id() + ":" + id).filter(id -> id.startsWith(builder.getRemaining())).forEach(builder::suggest));
                            return builder.buildFuture();
                        })
                        .executes(context -> runVillageAction(context.getSource(), StringArgumentType.getString(context, "type"),
                                LongArgumentType.getLong(context, "seed"), action, replace))));
    }

    private static int runVillageAction(CommandSourceStack source, String id, long seed, String action, boolean replace) {
        var catalog = contentCatalog;
        String[] parts = id.trim().split(":", -1);
        var culture = parts.length == 2 ? catalog.cultures().get(parts[0]) : null;
        var type = culture == null ? null : culture.villageTypes().get(parts[1]);
        if (type == null) { source.sendFailure(Component.literal("Unknown village type: " + id)); return 0; }
        try {
            var pos = BlockPos.containing(source.getPosition());
            var layout = VillageLayout.create(catalog, type, new LegacyBuildingPlan.Position(pos.getX(), pos.getY(), pos.getZ()), seed);
            var prepared = VillagePlacement.prepare(layout, catalog.palette(), catalog.goods());
            if (action.equals("plan")) {
                String description = layout.buildings().stream().map(building -> (building.centre() ? "Centre " : "Start ")
                        + building.plan().id() + " at " + building.origin().x() + " " + building.origin().y() + " " + building.origin().z()
                        + " rotation=" + building.rotation()).collect(Collectors.joining("\n"));
                source.sendSuccess(() -> Component.literal(id + ": seed=" + seed + ", radius=" + layout.radius()
                        + ", starting layout on command height\n" + description), false);
                if (!prepared.supported()) reportVillageIssues(source, prepared.combined().issues());
                return layout.buildings().size();
            }
            var dimension = source.getLevel().dimension().identifier();
            var settlements = FabricSettlementState.get(source.getServer());
            var world = BuildingPlacement.world(source.getLevel());
            var issues = new java.util.ArrayList<>(BuildingPlacement.checkDestinations(prepared.combined(), world, replace));
            issues.addAll(settlements.overlapIssues(dimension, layout));
            if (!issues.isEmpty()) { reportVillageIssues(source, issues); return 0; }
            if (action.equals("check") || action.equals("checkreplace")) {
                source.sendSuccess(() -> Component.literal(id + ": " + layout.buildings().size() + " starting buildings, "
                        + prepared.combined().changes().size() + " block operations. "
                        + (replace ? "Terrain replacement passes" : "Placement into empty space passes") + "; no blocks changed."), false);
                return layout.buildings().size();
            }
            // Initialize and validate records before mutating blocks; publish only after the whole write succeeds.
            var record = FabricSettlementState.from(dimension, prepared);
            var buildingState = FabricBuildingState.get(source.getServer());
            int changed = VillagePlacement.place(prepared, world, replace);
            settlements.record(record);
            record.buildings().forEach(building -> buildingState.record(building.placement()));
            FabricSettlementLifecycleState.get(source.getServer()).ensure(record);
            source.sendSuccess(() -> Component.literal("Placed starting layout " + id + ": " + layout.buildings().size()
                    + " buildings, " + changed + " changed blocks. Villagers and village simulation are not implemented yet."), true);
            return layout.buildings().size();
        } catch (IOException | RuntimeException exception) {
            source.sendFailure(Component.literal("Village operation failed: " + exception.getMessage()));
            return 0;
        }
    }

    private static void reportVillageIssues(CommandSourceStack source, List<String> issues) {
        source.sendFailure(Component.literal("Starting layout blocked (" + issues.size() + " issues): "
                + issues.stream().limit(10).collect(Collectors.joining("; "))));
    }

    private static int listSettlements(CommandSourceStack source) {
        var settlements = FabricSettlementState.get(source.getServer()).settlements();
        String message = settlements.stream().map(settlement -> settlement.type() + " (" + settlement.name() + ") "
                + settlement.dimension() + " " + settlement.origin().x() + " " + settlement.origin().y() + " " + settlement.origin().z()
                + " seed=" + settlement.seed() + " buildings=" + settlement.buildings().size())
                .collect(Collectors.joining("\n", "Placed starting layouts:\n", ""));
        source.sendSuccess(() -> Component.literal(message), false);
        return settlements.size();
    }

    private static int listVillagers(CommandSourceStack source) {
        var state = FabricVillagerState.get(source.getServer());
        var visible = state.villagers().stream().filter(v -> v.dimension().equals(source.getLevel().dimension().identifier()))
                .sorted(Comparator.comparing(FabricVillagerState.Villager::id)).toList();
        String message = visible.stream().map(v -> v.id() + " " + v.name() + " [" + v.culture() + ":" + v.type() + "] "
                + v.position().x() + " " + v.position().y() + " " + v.position().z() + (v.alive() ? " alive" : " dead"))
                .collect(Collectors.joining("\n", "Villagers in dimension:\n", ""));
        source.sendSuccess(() -> Component.literal(message), false);
        return visible.size();
    }

    private static int listLifecycle(CommandSourceStack source) {
        var state = FabricSettlementLifecycleState.get(source.getServer());
        var visible = state.lifecycles().stream()
                .filter(lifecycle -> lifecycle.key().startsWith(source.getLevel().dimension().identifier() + "@"))
                .toList();
        String message = visible.stream().map(lifecycle -> lifecycle.key()
                + " population=" + lifecycle.population() + "/" + lifecycle.capacity()
                + " food=" + lifecycle.food() + " materials=" + lifecycle.materials()
                + " reputation=" + lifecycle.reputation()
                + " projects=" + lifecycle.projects().stream()
                        .map(project -> project.id() + "(" + project.status() + " " + project.progress() + "/" + project.requiredWork() + ")")
                        .collect(Collectors.joining(", ")))
                .collect(Collectors.joining("\n", "Settlement lifecycle state:\n", ""));
        source.sendSuccess(() -> Component.literal(message), false);
        return visible.size();
    }

    private static int addLifecycleResources(CommandSourceStack source, int food, int materials) {
        var settlement = FabricSettlementState.get(source.getServer()).containing(
                source.getLevel().dimension().identifier(), BlockPos.containing(source.getPosition()).getX(),
                BlockPos.containing(source.getPosition()).getZ());
        if (settlement.isEmpty()) {
            source.sendFailure(Component.literal("You are not inside a saved settlement."));
            return 0;
        }
        try {
            String key = FabricSettlementLifecycleState.key(settlement.get());
            FabricSettlementLifecycleState.get(source.getServer()).addResources(key, food, materials);
            source.sendSuccess(() -> Component.literal("Added food=" + food + " materials=" + materials + " to " + key), true);
            return 1;
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal("Could not update settlement resources: " + exception.getMessage()));
            return 0;
        }
    }

    private static int queueLifecycleProject(CommandSourceStack source, String projectId, String plan,
                                              int requiredWork, int materialCost) {
        var settlement = FabricSettlementState.get(source.getServer()).containing(
                source.getLevel().dimension().identifier(), BlockPos.containing(source.getPosition()).getX(),
                BlockPos.containing(source.getPosition()).getZ());
        if (settlement.isEmpty()) {
            source.sendFailure(Component.literal("You are not inside a saved settlement."));
            return 0;
        }
        try {
            String key = FabricSettlementLifecycleState.key(settlement.get());
            FabricSettlementLifecycleState.get(source.getServer()).queue(key, projectId, plan, requiredWork, materialCost);
            source.sendSuccess(() -> Component.literal("Queued project " + projectId + " for " + key), true);
            return 1;
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal("Could not queue settlement project: " + exception.getMessage()));
            return 0;
        }
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildingAction(String action, boolean replace) {
        return Commands.literal(action).then(Commands.argument("rotation", IntegerArgumentType.integer(0, 3))
                .then(Commands.argument("plan", StringArgumentType.greedyString())
                        .suggests((context, builder) -> {
                            contentCatalog.plans().keySet().stream().filter(id -> id.startsWith(builder.getRemaining())).forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> runBuildingAction(context.getSource(), StringArgumentType.getString(context, "plan"),
                                IntegerArgumentType.getInteger(context, "rotation"), action, replace))));
    }

    private static int runBuildingAction(CommandSourceStack source, String id, int rotation, String action, boolean replace) {
        var catalog = contentCatalog;
        var plan = catalog.plans().get(id.trim());
        if (plan == null) { source.sendFailure(Component.literal("Unknown building plan: " + id)); return 0; }
        try {
            var prepared = BuildingPlacement.prepare(plan, catalog.palette(), BlockPos.containing(source.getPosition()), rotation,
                    catalog.goods(), source.getLevel().getSeed());
            var world = BuildingPlacement.world(source.getLevel());
            var issues = BuildingPlacement.checkDestinations(prepared, world, action.equals("check") || replace);
            if (!issues.isEmpty()) {
                source.sendFailure(Component.literal("Building blocked (" + issues.size() + " issues): "
                        + issues.stream().limit(8).collect(Collectors.joining("; "))));
                return 0;
            }
            if (action.equals("check")) {
                source.sendSuccess(() -> Component.literal(plan.id() + ": " + prepared.changes().size()
                        + " supported block operations. Placement and terrain replacement have not been performed."), false);
                return prepared.changes().size();
            }
            int changed = BuildingPlacement.place(prepared, world, replace);
            var origin = BlockPos.containing(source.getPosition());
            FabricBuildingState.get(source.getServer()).record(new FabricBuildingState.PlacedBuilding(
                    source.getLevel().dimension().identifier(), plan.id(),
                    new org.millenaire.fabric.content.LegacyBuildingPlan.Position(origin.getX(), origin.getY(), origin.getZ()),
                    rotation, prepared.servicePoints()));
            source.sendSuccess(() -> Component.literal("Placed " + plan.id() + ": " + changed + " changed blocks, rotation " + rotation), true);
            return changed;
        } catch (IOException | RuntimeException exception) {
            source.sendFailure(Component.literal("Building operation failed: " + exception.getMessage()));
            return 0;
        }
    }

    private static int listBuildings(CommandSourceStack source) {
        var buildings = FabricBuildingState.get(source.getServer()).buildings();
        String message = buildings.stream().map(building -> building.plan() + " " + building.dimension() + " "
                + building.origin().x() + " " + building.origin().y() + " " + building.origin().z() + " rotation=" + building.rotation())
                .collect(Collectors.joining("\n", "Placed Millenaire buildings:\n", ""));
        source.sendSuccess(() -> Component.literal(message), false);
        return buildings.size();
    }

    private static int villageTypes(CommandSourceStack source, String cultureId) {
        var culture = contentCatalog.cultures().get(cultureId);
        if (culture == null) {
            source.sendFailure(Component.literal("Unknown culture: " + cultureId));
            return 0;
        }
        var types = culture.villageTypes();
        String message = types.values().stream().map(type -> type.id() + " (" + type.name()
                + ", centre=" + type.centre() + ", starting buildings=" + type.startBuildings().size() + ")")
                .collect(Collectors.joining("\n", "Village types for " + cultureId + ":\n", ""));
        source.sendSuccess(() -> Component.literal(message), false);
        return types.size();
    }

    private static int contentStats(CommandSourceStack source) {
        var catalog = contentCatalog;
        String message = "Millenaire content: " + catalog.cultures().size() + " cultures, " + catalog.plans().size()
                + " building PNG plans, " + catalog.count("villages") + " village definitions, "
                + catalog.count("villagers") + " villager definitions, " + catalog.count("shops") + " shops, "
                + catalog.globalDocuments().size() + " shared documents, " + catalog.goods().goods().size() + " goods aliases, "
                + questCatalog.quests().size() + " quests, "
                + (catalog.diagnostics().size() + questCatalog.diagnostics().size()) + " diagnostics.";
        source.sendSuccess(() -> Component.literal(message), false);
        return catalog.plans().size();
    }

    private static int reloadContent(CommandSourceStack source) {
        try {
            setContentCatalog(LegacyCatalogLoader.loadGame(FabricLoader.getInstance().getGameDir()));
            loadQuestTexts(FabricLoader.getInstance().getGameDir());
            return contentStats(source);
        } catch (IOException | IllegalArgumentException exception) {
            source.sendFailure(Component.literal("Content reload failed: " + exception.getMessage()));
            return 0;
        }
    }

    private static int listQuests(CommandSourceStack source, String group) {
        var quests = questCatalog.quests().values().stream()
                .filter(quest -> group.isEmpty() || quest.group().equals(group)).toList();
        if (quests.isEmpty()) {
            source.sendFailure(Component.literal(group.isEmpty() ? "No quests are loaded." : "No quests in group " + group));
            return 0;
        }
        String message = quests.stream().map(quest -> quest.path() + " (" + quest.steps().size() + " steps"
                        + (quest.requiresWorldActions() ? ", world actions" : "") + ")")
                .collect(Collectors.joining("\n", quests.size() + " quests:\n", ""));
        source.sendSuccess(() -> Component.literal(message), false);
        return quests.size();
    }

    private static int questInfo(CommandSourceStack source, String path) {
        var found = questCatalog.get(path.trim());
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("Unknown quest: " + path));
            return 0;
        }
        QuestDefinition quest = found.get();
        var texts = questTexts;
        StringBuilder message = new StringBuilder(quest.path()).append(": chance/hour=").append(quest.chancePerHour())
                .append(", max simultaneous=").append(quest.maxSimultaneous()).append(", min reputation=").append(quest.minReputation());
        if (!quest.requiredPlayerTags().isEmpty()) message.append("\nrequires player tags ").append(quest.requiredPlayerTags());
        if (!quest.forbiddenPlayerTags().isEmpty()) message.append("\nforbidden player tags ").append(quest.forbiddenPlayerTags());
        for (var villager : quest.villagers()) {
            message.append("\nvillager ").append(villager.key()).append(": ")
                    .append(villager.types().isEmpty() ? "tags " + villager.requiredTags() : villager.types());
            villager.relatedTo().ifPresent(related -> message.append(", ").append(villager.relation().orElseThrow()
                    .name().toLowerCase(java.util.Locale.ROOT)).append(" of ").append(related));
        }
        for (var step : quest.steps()) {
            message.append("\nstep ").append(step.index()).append(" [").append(step.villager()).append(", ")
                    .append("duration ").append(step.duration()).append("] ")
                    .append(texts.text(quest.key(), step.index(), QuestTexts.Field.LABEL).orElse("(no label)"));
            if (!step.requiredGoods().isEmpty()) message.append("; requires ").append(step.requiredGoods());
            if (!step.rewardGoods().isEmpty()) message.append("; rewards ").append(step.rewardGoods());
            if (step.rewardMoney() > 0) message.append("; money ").append(step.rewardMoney());
            if (step.rewardReputation() > 0) message.append("; reputation +").append(step.rewardReputation());
            if (step.penaltyReputation() > 0) message.append("; penalty -").append(step.penaltyReputation());
        }
        String text = message.toString();
        source.sendSuccess(() -> Component.literal(text), false);
        return quest.steps().size();
    }

    private static int goodInfo(CommandSourceStack source, String key) {
        try {
            var good = contentCatalog.goods().require(key);
            if (good.metadata() == -1) {
                source.sendSuccess(() -> Component.literal(good.key() + ": " + good.legacyId() + " wildcard metadata; cannot be used as concrete starting stock."), false);
                return 1;
            }
            var target = org.millenaire.fabric.economy.LegacyItemResolver.resolve(good);
            var stack = target.stack();
            source.sendSuccess(() -> Component.literal(good.key() + ": " + good.legacyId() + "=" + good.metadata()
                    + " -> " + target.id() + ", stack limit=" + stack.getMaxStackSize()
                    + target.potion().map(potion -> ", potion=" + potion).orElse("")), false);
            return 1;
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal("Good inspection failed: " + exception.getMessage())); return 0;
        }
    }

    private static int buildingInfo(CommandSourceStack source, String id) {
        var catalog = contentCatalog;
        var plan = catalog.plans().get(id.trim());
        if (plan == null) {
            source.sendFailure(Component.literal("Unknown Millenaire building plan: " + id));
            return 0;
        }
        try {
            var decoded = plan.decode(catalog.palette());
            var position = BlockPos.containing(source.getPosition());
            var services = plan.servicePoints(decoded, catalog.palette(), position.getX(), position.getY(), position.getZ(), 0);
            String message = plan.id() + ": " + plan.width() + " x " + plan.length() + " x " + decoded.floors()
                    + ", ground offset " + plan.startLevel() + ", unknown palette colors " + decoded.unknownColors()
                    + ", starting stock rules " + plan.parameters().getOrDefault("startinggood", List.of()).size()
                    + ", service points " + services.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue().size())
                    .collect(Collectors.joining(", "));
            source.sendSuccess(() -> Component.literal(message), false);
            return decoded.floors();
        } catch (IOException exception) {
            source.sendFailure(Component.literal("Cannot decode plan: " + exception.getMessage()));
            return 0;
        }
    }

    private static int mark(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos position = BlockPos.containing(source.getPosition());
        boolean added = FabricVillageState.get(source.getServer()).mark(
                level.dimension().identifier(), position.getX(), position.getY(), position.getZ());

        String message = added
                ? "Marked village position " + format(level, position)
                : "Village position is already marked: " + format(level, position);
        source.sendSuccess(() -> Component.literal(message), false);
        return added ? 1 : 0;
    }

    private static int list(CommandSourceStack source) {
        List<FabricVillageState.VillageMark> marks = FabricVillageState.get(source.getServer()).marks();
        if (marks.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No village positions are marked."), false);
            return 0;
        }

        String message = marks.stream()
                .sorted(Comparator.comparing((FabricVillageState.VillageMark mark) -> mark.dimension().toString())
                        .thenComparingInt(FabricVillageState.VillageMark::x)
                        .thenComparingInt(FabricVillageState.VillageMark::y)
                        .thenComparingInt(FabricVillageState.VillageMark::z))
                .map(mark -> mark.dimension() + " " + mark.x() + " " + mark.y() + " " + mark.z())
                .collect(Collectors.joining("\n", "Village positions (dimension x y z):\n", ""));
        Supplier<Component> response = () -> Component.literal(message);
        source.sendSuccess(response, false);
        return marks.size();
    }

    private static int listCultures(CommandSourceStack source) {
        List<CultureDescriptor> cultures = cultureDescriptors;
        if (cultures.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No Millenaire cultures were loaded."), false);
            return 0;
        }

        String message = cultures.stream()
                .map(CultureDescriptor::id)
                .collect(Collectors.joining(", ", "Loaded Millenaire cultures: ", ""));
        source.sendSuccess(() -> Component.literal(message), false);
        return cultures.size();
    }

    private static String format(ServerLevel level, BlockPos position) {
        return level.dimension().identifier() + " " + position.getX() + " " + position.getY() + " " + position.getZ();
    }
}
