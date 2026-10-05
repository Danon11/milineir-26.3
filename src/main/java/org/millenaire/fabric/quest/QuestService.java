package org.millenaire.fabric.quest;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.millenaire.fabric.FabricReputationState;
import org.millenaire.fabric.FabricSettlementState;
import org.millenaire.fabric.FabricVillagerState;
import org.millenaire.fabric.MillenaireCommands;
import org.millenaire.fabric.goal.ChestGoodsStore;
import org.millenaire.fabric.villager.MillVillagerEntity;

import java.util.*;

/**
 * Runs quests in the world: offers them hourly during the day, shows steps when the player talks to the step's
 * villager, completes or refuses steps from chat buttons, and applies rewards, penalties, tags and time-outs.
 */
public final class QuestService {
    public static final String COMMAND = "millenaire_quest";
    private static final double TALK_DISTANCE_SQR = 8 * 8;
    private static final SplittableRandom RANDOM = new SplittableRandom();

    private QuestService() {}

    // ---------------------------------------------------------------- villagers and villages

    /** Village key for reputation: the settlement origin, or the building origin outside a settlement. */
    static Optional<String> villageKey(MinecraftServer server, Identifier dimension, String building) {
        int at = building.lastIndexOf('@');
        if (at <= 0) return Optional.empty();
        String[] xyz = building.substring(at + 1).split(",");
        if (xyz.length != 3) return Optional.empty();
        int x = Integer.parseInt(xyz[0]), y = Integer.parseInt(xyz[1]), z = Integer.parseInt(xyz[2]);
        var settlement = FabricSettlementState.get(server).settlements().stream().filter(s -> s.dimension().equals(dimension)
                && s.buildings().stream().anyMatch(b -> b.placement().origin().x() == x && b.placement().origin().y() == y
                && b.placement().origin().z() == z)).findFirst();
        return Optional.of(settlement.map(s -> dimension + "@" + s.origin().x() + "," + s.origin().y() + "," + s.origin().z())
                .orElse(dimension + "@" + x + "," + y + "," + z));
    }

    private static String villageName(MinecraftServer server, String villageKey) {
        return FabricSettlementState.get(server).settlements().stream()
                .filter(s -> villageKey.equals(s.dimension() + "@" + s.origin().x() + "," + s.origin().y() + "," + s.origin().z()))
                .map(FabricSettlementState.Settlement::name).findFirst().orElse("?");
    }

    static List<QuestRuntime.VillagerInfo> villagers(MinecraftServer server) {
        List<QuestRuntime.VillagerInfo> result = new ArrayList<>();
        for (var record : FabricVillagerState.get(server).villagers()) {
            if (!record.alive() || record.building().isEmpty()) continue;
            UUID id;
            try { id = UUID.fromString(record.id()); } catch (IllegalArgumentException exception) { continue; }
            var village = villageKey(server, record.dimension(), record.building());
            if (village.isEmpty()) continue;
            String[] origin = village.get().substring(village.get().indexOf('@') + 1).split(",");
            result.add(new QuestRuntime.VillagerInfo(id, record.culture() + "/" + record.type(), record.building(), village.get(),
                    Integer.parseInt(origin[0]), Integer.parseInt(origin[2])));
        }
        return result;
    }

    // ---------------------------------------------------------------- tick

    /** Called every server tick; works once per game hour. */
    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        long now = overworld.getGameTime();
        if (now % QuestRuntime.HOUR != 0) return;
        var catalog = MillenaireCommands.questCatalog();
        var state = FabricQuestState.get(server);
        for (var instance : state.instances()) {
            var quest = catalog.get(instance.quest()).orElse(null);
            ServerPlayer player = server.getPlayerList().getPlayer(instance.player());
            if (quest == null) { state.remove(instance.id()); continue; }
            if (QuestRuntime.expired(instance, quest, now)) {
                fail(server, state, quest, instance, player, QuestTexts.Field.DESCRIPTION_TIMEUP);
            }
        }
        long dayTime = Math.floorMod(overworld.getDefaultClockTime(), 24000L);
        if (dayTime >= 12000) return;
        var villagers = villagers(server);
        if (villagers.isEmpty()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (var quest : catalog.quests().values()) {
                if (RANDOM.nextDouble() > quest.chancePerHour() || !QuestRuntime.eligible(quest, player.getUUID(), state)) continue;
                var assignment = QuestRuntime.assign(quest, player.getUUID(), villagers, state, RANDOM);
                if (assignment.isEmpty()) continue;
                var instance = new QuestRuntime.Instance(RANDOM.nextLong(Long.MAX_VALUE), quest.path(), player.getUUID(), assignment.get(), 0, now);
                state.add(instance);
                announce(server, quest, instance, player);
            }
        }
    }

    /** Offers a quest at once, ignoring the hourly chance; for testing and admin use. */
    public static Optional<QuestRuntime.Instance> force(MinecraftServer server, QuestDefinition quest, ServerPlayer player) {
        var state = FabricQuestState.get(server);
        if (!QuestRuntime.eligible(quest, player.getUUID(), state)) return Optional.empty();
        var assignment = QuestRuntime.assign(quest, player.getUUID(), villagers(server), state, RANDOM);
        if (assignment.isEmpty()) return Optional.empty();
        var instance = new QuestRuntime.Instance(RANDOM.nextLong(Long.MAX_VALUE), quest.path(), player.getUUID(), assignment.get(), 0,
                server.overworld().getGameTime());
        state.add(instance);
        announce(server, quest, instance, player);
        return Optional.of(instance);
    }

    private static void announce(MinecraftServer server, QuestDefinition quest, QuestRuntime.Instance instance, ServerPlayer player) {
        String label = text(quest, instance, 0, QuestTexts.Field.LABEL, server, player).orElse(quest.key());
        String villager = villagerName(server, instance.villagers().get(quest.steps().getFirst().villager()));
        player.sendSystemMessage(Component.literal("[Millénaire] " + villager + ": " + label).withStyle(ChatFormatting.GOLD));
    }

    // ---------------------------------------------------------------- talking

    /** Shows the player's current quest step with this villager; returns false when there is none. */
    public static boolean talk(MillVillagerEntity villager, Player player) {
        if (!(villager.level() instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return false;
        var server = level.getServer();
        var state = FabricQuestState.get(server);
        for (var instance : state.active(player.getUUID())) {
            var quest = MillenaireCommands.questCatalog().get(instance.quest()).orElse(null);
            if (quest == null || instance.step() >= quest.steps().size()) continue;
            var step = quest.steps().get(instance.step());
            if (!villager.getUUID().equals(instance.villagers().get(step.villager()))) continue;
            String label = text(quest, instance, instance.step(), QuestTexts.Field.LABEL, server, serverPlayer).orElse(quest.key());
            var menu = org.millenaire.fabric.ui.MillMenu.builder(label).subtitle(villager.getName().getString());
            text(quest, instance, instance.step(), QuestTexts.Field.DESCRIPTION, server, serverPlayer).ifPresent(menu::text);
            if (!step.requiredGoods().isEmpty() && step.showRequiredGoods()) {
                menu.heading("Required");
                step.requiredGoods().forEach((good, count) -> menu.text(count + " " + good));
            }
            menu.row("", org.millenaire.fabric.ui.MillMenu.button("Accept", "/" + COMMAND + " accept " + instance.id(), org.millenaire.fabric.ui.MillMenu.Tone.GOOD),
                    org.millenaire.fabric.ui.MillMenu.button("Refuse", "/" + COMMAND + " refuse " + instance.id(), org.millenaire.fabric.ui.MillMenu.Tone.BAD));
            org.millenaire.fabric.ui.MillMenus.show(serverPlayer, menu.build());
            return true;
        }
        return false;
    }

    private static MutableComponent button(String label, ChatFormatting colour, String arguments) {
        return Component.literal(label).withStyle(style -> style.withColor(colour)
                .withClickEvent(new ClickEvent.RunCommand("/" + COMMAND + " " + arguments)));
    }

    private static Optional<MillVillagerEntity> nearbyVillager(ServerPlayer player, UUID id) {
        if (id == null) return Optional.empty();
        var entity = player.level().getEntity(id);
        return entity instanceof MillVillagerEntity villager && villager.distanceToSqr(player) <= TALK_DISTANCE_SQR
                ? Optional.of(villager) : Optional.empty();
    }

    /** Completes the current step if the player stands by its villager and carries the required goods. */
    public static Component accept(ServerPlayer player, long id) {
        var server = player.level().getServer();
        var state = FabricQuestState.get(server);
        var instance = state.instance(id).filter(i -> i.player().equals(player.getUUID())).orElse(null);
        if (instance == null) return Component.literal("This quest is no longer active.");
        var quest = MillenaireCommands.questCatalog().get(instance.quest()).orElse(null);
        if (quest == null) { state.remove(id); return Component.literal("This quest no longer exists."); }
        var step = quest.steps().get(instance.step());
        if (nearbyVillager(player, instance.villagers().get(step.villager())).isEmpty())
            return Component.literal("Talk to " + villagerName(server, instance.villagers().get(step.villager())) + " to continue this quest.");
        if (!QuestRuntime.stepAllowed(step, player.getUUID(), state))
            return Component.literal(text(quest, instance, instance.step(), QuestTexts.Field.LISTING, server, player).orElse("Not yet."));
        Map<String, Integer> missing = missingGoods(player, step);
        if (!missing.isEmpty()) return Component.literal("You still need: " + missing);
        step.requiredGoods().forEach((good, count) -> take(player, good, count));
        step.rewardGoods().forEach((good, count) -> give(player, good, count));
        if (step.rewardMoney() > 0) giveMoney(player, step.rewardMoney());
        String village = villageOf(server, instance.villagers().get(step.villager()));
        if (step.rewardReputation() > 0 && village != null)
            FabricReputationState.get(server).add(village, player.getUUID(), step.rewardReputation());
        apply(state, instance, step.success());
        step.actionData().forEach(entry -> state.setActionData(player.getUUID(), entry.key(), entry.value()));
        // World buildings of the step are placed now; the next step usually asks the player to explore them.
        step.bedrockBuildings().forEach(building -> player.sendSystemMessage(QuestSites.place(player, building)));
        String success = text(quest, instance, instance.step(), QuestTexts.Field.DESCRIPTION_SUCCESS, server, player).orElse("");
        if (instance.step() + 1 >= quest.steps().size()) {
            state.remove(id);
            return Component.literal(success.isEmpty() ? "Quest completed." : success).withStyle(ChatFormatting.GREEN);
        }
        var next = instance.next(server.overworld().getGameTime());
        state.replace(next);
        String listing = text(quest, next, next.step(), QuestTexts.Field.LISTING, server, player).orElse("");
        return Component.literal(success + (listing.isEmpty() ? "" : "\n" + listing)).withStyle(ChatFormatting.GREEN);
    }

    public static Component refuse(ServerPlayer player, long id) {
        var server = player.level().getServer();
        var state = FabricQuestState.get(server);
        var instance = state.instance(id).filter(i -> i.player().equals(player.getUUID())).orElse(null);
        if (instance == null) return Component.literal("This quest is no longer active.");
        var quest = MillenaireCommands.questCatalog().get(instance.quest()).orElse(null);
        if (quest == null) { state.remove(id); return Component.literal("This quest no longer exists."); }
        return fail(server, state, quest, instance, player, QuestTexts.Field.DESCRIPTION_REFUSE);
    }

    private static Component fail(MinecraftServer server, FabricQuestState state, QuestDefinition quest, QuestRuntime.Instance instance,
                                  ServerPlayer player, QuestTexts.Field field) {
        var step = quest.steps().get(Math.min(instance.step(), quest.steps().size() - 1));
        String village = villageOf(server, instance.villagers().get(step.villager()));
        if (step.penaltyReputation() > 0 && village != null)
            FabricReputationState.get(server).add(village, instance.player(), -step.penaltyReputation());
        apply(state, instance, step.failure());
        state.remove(instance.id());
        String text = player == null ? "" : text(quest, instance, instance.step(), field, server, player).orElse("");
        if (step.penaltyReputation() > 0) text += (text.isEmpty() ? "" : " ") + "(Reputation lost: " + step.penaltyReputation() + ")";
        Component message = Component.literal(text.isEmpty() ? "Quest abandoned." : text).withStyle(ChatFormatting.RED);
        if (player != null && field == QuestTexts.Field.DESCRIPTION_TIMEUP) player.sendSystemMessage(message);
        return message;
    }

    private static void apply(FabricQuestState state, QuestRuntime.Instance instance, QuestDefinition.Outcome outcome) {
        UUID player = instance.player();
        for (var tag : outcome.setVillagerTags()) {
            UUID villager = instance.villagers().get(tag.villager());
            if (villager != null) state.setVillagerTag(villager, player, tag.tag(), true);
        }
        for (var tag : outcome.clearVillagerTags()) {
            UUID villager = instance.villagers().get(tag.villager());
            if (villager != null) state.setVillagerTag(villager, player, tag.tag(), false);
        }
        outcome.setPlayerTags().forEach(tag -> state.setPlayerTag(player, tag, true));
        outcome.clearPlayerTags().forEach(tag -> state.setPlayerTag(player, tag, false));
        outcome.setGlobalTags().forEach(tag -> state.setGlobalTag(tag, true));
        outcome.clearGlobalTags().forEach(tag -> state.setGlobalTag(tag, false));
    }

    // ---------------------------------------------------------------- goods

    private static Optional<ItemStack> prototype(MinecraftServer server, String good) {
        return new ChestGoodsStore(server.overworld(), List.of(), MillenaireCommands.contentCatalog().goods()).prototype(good);
    }

    /** {@code enchantedsword} accepts any enchanted sword; other goods match their item. */
    private static boolean matches(MinecraftServer server, String good, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (good.equals("enchantedsword")) return stack.is(ItemTags.SWORDS) && stack.isEnchanted();
        return prototype(server, good).map(p -> stack.is(p.getItem())).orElse(false);
    }

    static Map<String, Integer> missingGoods(ServerPlayer player, QuestDefinition.Step step) {
        var server = player.level().getServer();
        Map<String, Integer> missing = new LinkedHashMap<>();
        for (var entry : step.requiredGoods().entrySet()) {
            int count = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (matches(server, entry.getKey(), stack)) count += stack.getCount();
            }
            if (count < entry.getValue()) missing.put(entry.getKey(), entry.getValue() - count);
        }
        return missing;
    }

    private static void take(ServerPlayer player, String good, int count) {
        var server = player.level().getServer();
        int remaining = count;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!matches(server, good, stack)) continue;
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
        }
        player.getInventory().setChanged();
    }

    private static void give(ServerPlayer player, String good, int count) {
        var prototype = prototype(player.level().getServer(), good);
        if (prototype.isEmpty()) return;
        int remaining = count;
        while (remaining > 0) {
            int amount = Math.min(remaining, prototype.get().getMaxStackSize());
            ItemStack stack = prototype.get().copyWithCount(amount);
            if (!player.getInventory().add(stack)) player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack));
            remaining -= amount;
        }
    }

    private static void giveMoney(ServerPlayer player, int deniers) {
        int gold = deniers / 4096, silver = deniers % 4096 / 64, denier = deniers % 64;
        giveCoin(player, "denieror", gold);
        giveCoin(player, "denierargent", silver);
        giveCoin(player, "denier", denier);
    }

    private static void giveCoin(ServerPlayer player, String name, int count) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("millenaire", name));
        while (count > 0) {
            int amount = Math.min(64, count);
            ItemStack stack = new ItemStack(item, amount);
            if (!player.getInventory().add(stack)) player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack));
            count -= amount;
        }
    }

    // ---------------------------------------------------------------- texts

    private static String villageOf(MinecraftServer server, UUID villager) {
        if (villager == null) return null;
        return FabricVillagerState.get(server).find(villager.toString())
                .flatMap(record -> villageKey(server, record.dimension(), record.building())).orElse(null);
    }

    private static String villagerName(MinecraftServer server, UUID villager) {
        if (villager == null) return "?";
        return FabricVillagerState.get(server).find(villager.toString()).map(FabricVillagerState.Villager::name).orElse("?");
    }

    static Optional<String> text(QuestDefinition quest, QuestRuntime.Instance instance, int step, QuestTexts.Field field,
                                 MinecraftServer server, ServerPlayer player) {
        var raw = MillenaireCommands.questTexts().text(quest.key(), step, field);
        if (raw.isEmpty()) return Optional.empty();
        Map<String, String> values = new HashMap<>();
        values.put("name", player.getName().getString());
        instance.villagers().forEach((key, id) -> {
            values.put(key + "_villagername", villagerName(server, id));
            String village = villageOf(server, id);
            values.put(key + "_villagename", village == null ? "?" : villageName(server, village));
        });
        return Optional.of(QuestTexts.render(raw.get(), values));
    }

    /**
     * Admin self-test: a fake player with high reputation receives the quest, is given each step's required goods,
     * stands next to each step villager and accepts the step. Returns the transcript.
     */
    public static List<String> selfTest(MinecraftServer server, QuestDefinition quest) {
        List<String> log = new ArrayList<>();
        ServerLevel level = server.overworld();
        var fake = net.fabricmc.fabric.api.entity.FakePlayer.get(level);
        var state = FabricQuestState.get(server);
        state.active(fake.getUUID()).forEach(instance -> state.remove(instance.id()));
        for (var villager : villagers(server)) FabricReputationState.get(server).add(villager.village(), fake.getUUID(), 100000);
        quest.requiredPlayerTags().forEach(tag -> state.setPlayerTag(fake.getUUID(), tag, true));
        fake.getInventory().clearContent();
        var instance = force(server, quest, fake).orElse(null);
        if (instance == null) { log.add("not offered: no eligible villagers"); return log; }
        log.add("offered with " + instance.villagers());
        for (int guard = 0; guard < quest.steps().size() + 1; guard++) {
            var current = state.instance(instance.id()).orElse(null);
            if (current == null) break;
            var step = quest.steps().get(current.step());
            var villager = level.getEntity(current.villagers().get(step.villager()));
            if (villager == null) { log.add("step " + current.step() + ": villager not loaded"); break; }
            fake.snapTo(villager.getX(), villager.getY(), villager.getZ(), 0, 0);
            step.requiredGoods().forEach((good, count) -> {
                if (good.equals("enchantedsword")) {
                    ItemStack sword = new ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
                    var sharpness = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                            .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS);
                    sword.enchant(sharpness, 1);
                    fake.getInventory().add(sword);
                } else give(fake, good, count);
            });
            log.add("step " + current.step() + ": " + accept(fake, current.id()).getString());
        }
        List<String> inventory = new ArrayList<>();
        for (int slot = 0; slot < fake.getInventory().getContainerSize(); slot++) {
            ItemStack stack = fake.getInventory().getItem(slot);
            if (!stack.isEmpty()) inventory.add(stack.getCount() + "x " + BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        log.add("finished=" + state.instance(instance.id()).isEmpty() + ", player tags=" + state.playerTags(fake.getUUID()) + ", inventory=" + inventory);
        return log;
    }

    /** One line per running quest of the player, for the quest list command. */
    public static List<String> describe(ServerPlayer player) {
        var server = player.level().getServer();
        List<String> lines = new ArrayList<>();
        for (var instance : FabricQuestState.get(server).active(player.getUUID())) {
            var quest = MillenaireCommands.questCatalog().get(instance.quest()).orElse(null);
            if (quest == null || instance.step() >= quest.steps().size()) continue;
            var step = quest.steps().get(instance.step());
            long left = instance.stepStart() + step.duration() * QuestRuntime.HOUR - server.overworld().getGameTime();
            String label = text(quest, instance, instance.step(), QuestTexts.Field.LABEL, server, player).orElse(quest.key());
            String listing = text(quest, instance, instance.step(), QuestTexts.Field.LISTING, server, player)
                    .orElse("Talk to " + villagerName(server, instance.villagers().get(step.villager())));
            lines.add(label + ": " + listing + " (" + Math.max(0, left / QuestRuntime.HOUR) + " h left)");
        }
        return lines;
    }
}
