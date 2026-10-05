package org.millenaire.fabric.village;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.component.WrittenBookContent;
import org.millenaire.fabric.FabricReputationState;
import org.millenaire.fabric.FabricSettlementState;
import org.millenaire.fabric.FabricVillageOwnership;
import org.millenaire.fabric.FabricVillageRelations;
import org.millenaire.fabric.FabricVillagerState;
import org.millenaire.fabric.MillenaireCommands;
import org.millenaire.fabric.economy.TradeOffers;

import java.util.*;

/**
 * The parchments of the original mod as readable books: {@code parchment_<culture>villagers|buildings|items|full}
 * describe a culture from its data, {@code parchment_villagescroll} the village the reader stands in, and
 * {@code parchment_sadhu} the sadhu's teaching. Using one opens the vanilla book screen.
 */
public final class VillageBooks {
    private static final int PAGE_CHARS = 220;

    private VillageBooks() {}

    public static void register() {
        UseItemCallback.EVENT.register((player, level, hand) -> {
            var stack = player.getItemInHand(hand);
            var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!id.getNamespace().equals("millenaire") || !id.getPath().startsWith("parchment_")) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
            var pages = pages(serverPlayer, id.getPath().substring("parchment_".length()));
            if (pages.isEmpty()) return InteractionResult.PASS;
            stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(stack.getHoverName().getString()),
                    "Millénaire", 0, pages.stream().map(page -> Filterable.<Component>passThrough(Component.literal(page))).toList(), true));
            // The client opens the book from its copy of the item, so it must have the pages first.
            serverPlayer.inventoryMenu.broadcastChanges();
            serverPlayer.containerMenu.broadcastChanges();
            serverPlayer.openItemGui(stack, hand);
            return InteractionResult.SUCCESS;
        });
    }

    public static List<String> pages(ServerPlayer player, String kind) {
        if (kind.equals("villagescroll")) return paginate(villageScroll(player));
        if (kind.equals("sadhu")) return paginate(List.of("The sadhu teaches that the world is one great village. Those who walk "
                + "between its houses with open hands are welcomed everywhere; those who take by force are remembered everywhere.",
                "Seek the temples. A pandit leading a puja will bless the tools you bring, if your offering is worthy."));
        for (String part : List.of("villagers", "buildings", "items", "full")) {
            if (!kind.endsWith(part)) continue;
            String culture = cultureId(kind.substring(0, kind.length() - part.length()));
            if (culture == null) return List.of();
            List<String> entries = new ArrayList<>();
            if (part.equals("villagers") || part.equals("full")) entries.addAll(villagers(culture));
            if (part.equals("buildings") || part.equals("full")) entries.addAll(buildings(culture));
            if (part.equals("items") || part.equals("full")) entries.addAll(items(culture));
            return paginate(entries);
        }
        return List.of();
    }

    /** Parchment culture names: norman, indian, japanese, mayan (folders: norman, indian, japanese, mayan). */
    static String cultureId(String name) {
        var catalog = MillenaireCommands.contentCatalog();
        if (catalog == null) return null;
        if (catalog.cultures().containsKey(name)) return name;
        return catalog.cultures().keySet().stream().filter(id -> id.startsWith(name) || name.startsWith(id)).findFirst().orElse(null);
    }

    static List<String> villagers(String culture) {
        var documents = MillenaireCommands.contentCatalog().cultures().get(culture).category("villagers");
        List<String> entries = new ArrayList<>(List.of("§l" + capitalise(culture) + " villagers§r"));
        documents.forEach((path, document) -> {
            String key = path.substring(path.lastIndexOf('/') + 1, path.length() - 4);
            String name = document.first("native_name", key).trim();
            List<String> tags = document.values("tag").stream().map(t -> t.trim().toLowerCase(Locale.ROOT)).toList();
            String role = tags.contains("hostile") ? "bandit" : tags.contains("chief") ? "chief" : tags.contains("seller") ? "merchant"
                    : tags.contains("child") ? "child" : tags.stream().anyMatch(t -> t.equals("helpinattacks") || t.equals("raider")) ? "warrior" : "villager";
            entries.add(name + " (" + role + (document.first("hiringcost", "0").trim().equals("0") ? "" : ", for hire") + ")");
        });
        return entries;
    }

    static List<String> buildings(String culture) {
        List<String> entries = new ArrayList<>(List.of("§l" + capitalise(culture) + " buildings§r"));
        MillenaireCommands.contentCatalog().plans().values().stream()
                .filter(plan -> plan.culture().equals(culture) && plan.upgrade() == 0 && plan.variation() == 'A')
                .sorted(Comparator.comparing(plan -> plan.key())).forEach(plan -> {
                    String name = plan.parameters().getOrDefault("nativename", List.of(plan.key())).getLast();
                    List<String> residents = new ArrayList<>();
                    for (String gender : List.of("male", "female"))
                        plan.parameters().getOrDefault(gender, List.of()).forEach(value -> residents.addAll(List.of(value.split(","))));
                    entries.add(name + (residents.isEmpty() ? "" : ": " + String.join(", ", residents)));
                });
        return entries;
    }

    static List<String> items(String culture) {
        var trade = MillenaireCommands.tradeCatalog() == null ? null : MillenaireCommands.tradeCatalog().cultures().get(culture);
        if (trade == null) return List.of();
        List<String> entries = new ArrayList<>(List.of("§l" + capitalise(culture) + " goods§r"));
        trade.goods().values().forEach(good -> {
            int price = trade.sellingPrice("", good);
            entries.add(good.key() + (price > 0 ? ": " + money(price) : ""));
        });
        return entries;
    }

    static List<String> villageScroll(ServerPlayer player) {
        var server = player.level().getServer();
        var settlement = PlayerVillages.settlementAt(player.level(), player.blockPosition());
        if (settlement.isEmpty()) return List.of("The scroll stays blank: you are not in a village.");
        var s = settlement.get();
        String key = VillageGrowth.key(s);
        List<String> entries = new ArrayList<>();
        entries.add("§l" + s.name() + "§r (" + s.type() + ")");
        FabricVillageOwnership.get(server).owner(key).ifPresent(owner -> entries.add("Ruled by " + owner.name()));
        long living = FabricVillagerState.get(server).inSettlement(s).stream().filter(FabricVillagerState.Villager::alive).count();
        entries.add(living + " villagers, " + s.buildings().size() + " buildings");
        entries.add("Your reputation: " + FabricReputationState.get(server).get(key, player.getUUID()));
        VillageGrowth.pending(key).ifPresent(project -> entries.add("Building: " + project.label()));
        var catalog = MillenaireCommands.contentCatalog();
        entries.add("§lBuildings§r");
        for (var building : s.buildings()) {
            var plan = catalog.plan(building.placement().plan());
            entries.add((plan == null ? building.placement().plan() : plan.parameters().getOrDefault("nativename", List.of(plan.key())).getLast())
                    + (building.centre() ? " (town hall)" : ""));
        }
        entries.add("§lNeighbours§r");
        var relations = FabricVillageRelations.get(server);
        for (var other : VillageRaids.neighbours(server, s)) {
            int value = relations.get(key, VillageGrowth.key(other));
            entries.add(other.name() + ": " + FabricVillageRelations.describe(value) + " (" + value + ")");
        }
        return entries;
    }

    static String money(int deniers) {
        int gold = deniers / TradeOffers.GOLD, silver = deniers % TradeOffers.GOLD / TradeOffers.SILVER, copper = deniers % TradeOffers.SILVER;
        return (gold > 0 ? gold + "g " : "") + (silver > 0 ? silver + "s " : "") + (copper > 0 || gold + silver == 0 ? copper + "d" : "").trim();
    }

    private static String capitalise(String text) { return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1); }

    /** About 20 characters fit a book line and 13 lines a page. */
    static final int LINE_CHARS = 20, PAGE_LINES = 13;

    static int lines(String text) { return Math.max(1, (text.replaceAll("§.", "").length() + LINE_CHARS - 1) / LINE_CHARS); }

    /** Fills pages with whole entries, within {@link #PAGE_LINES} wrapped lines (long entries are split). */
    static List<String> paginate(List<String> entries) {
        List<String> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        int used = 0;
        for (String entry : entries) {
            String text = entry;
            while (text.length() > PAGE_CHARS) {
                if (!page.isEmpty()) { pages.add(page.toString()); page.setLength(0); used = 0; }
                pages.add(text.substring(0, PAGE_CHARS));
                text = text.substring(PAGE_CHARS);
            }
            if (!page.isEmpty() && (page.length() + text.length() + 1 > PAGE_CHARS || used + lines(text) > PAGE_LINES)) {
                pages.add(page.toString()); page.setLength(0); used = 0;
            }
            if (!page.isEmpty()) page.append('\n');
            page.append(text);
            used += lines(text);
        }
        if (!page.isEmpty()) pages.add(page.toString());
        return pages.stream().limit(100).toList();
    }
}
