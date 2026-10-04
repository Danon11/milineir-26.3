package org.millenaire.fabric.villager;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.millenaire.fabric.FabricReputationState;
import org.millenaire.fabric.FabricSettlementState;
import org.millenaire.fabric.MillenaireCommands;
import org.millenaire.fabric.economy.TradeOffers;
import org.millenaire.fabric.goal.VillageContext;

import java.util.*;

/** Builds a villager's shop offers in the vanilla trade screen and applies completed trades to the village. */
public final class VillagerTrading {
    public record Session(MerchantOffers offers, List<TradeOffers.Offer> specs, String village, VillageContext context) {}

    private VillagerTrading() {}

    /** Settlement key used for reputation: the settlement origin, or the building itself outside a village. */
    public static String villageKey(VillageContext context) {
        var origin = context.home().origin();
        var settlement = FabricSettlementState.get(context.level().getServer()).settlements().stream()
                .filter(s -> s.dimension().equals(context.home().dimension())
                        && s.buildings().stream().anyMatch(b -> b.placement().origin().equals(origin))).findFirst();
        var key = settlement.map(s -> s.origin()).orElse(origin);
        return context.home().dimension() + "@" + key.x() + "," + key.y() + "," + key.z();
    }

    public static Optional<Session> open(MillVillagerEntity villager, Player player) {
        return openFor(villager, player.getUUID());
    }

    /** Why a villager has no shop, for players and the offers command. */
    public static String explain(MillVillagerEntity villager) {
        if (!(villager.level() instanceof ServerLevel level)) return "client";
        var catalog = MillenaireCommands.contentCatalog();
        var context = VillageContext.of(level, catalog, villager.building());
        if (context.isEmpty()) return "no recorded home building '" + villager.building() + "'";
        var plan = catalog.plans().get(context.get().home().plan());
        String shopId = plan == null ? "" : plan.parameters().getOrDefault("shop", List.of("")).getLast().trim().toLowerCase(Locale.ROOT);
        if (shopId.isEmpty()) return "home " + context.get().home().plan() + " has no shop";
        var culture = MillenaireCommands.tradeCatalog().cultures().get(villager.culture());
        if (culture == null || !culture.shops().containsKey(shopId)) return "shop " + shopId + " is not defined for " + villager.culture();
        return "shop " + shopId + " has nothing to trade now";
    }

    public static Optional<Session> openFor(MillVillagerEntity villager, UUID playerId) {
        if (!(villager.level() instanceof ServerLevel level)) return Optional.empty();
        var catalog = MillenaireCommands.contentCatalog();
        var context = VillageContext.of(level, catalog, villager.building()).orElse(null);
        if (context == null) return Optional.empty();
        var plan = catalog.plans().get(context.home().plan());
        String shopId = plan == null ? "" : plan.parameters().getOrDefault("shop", List.of("")).getLast().trim().toLowerCase(Locale.ROOT);
        if (shopId.isEmpty()) return Optional.empty();
        var culture = MillenaireCommands.tradeCatalog().cultures().get(villager.culture());
        if (culture == null || !culture.shops().containsKey(shopId)) return Optional.empty();
        String village = villageKey(context);
        int reputation = FabricReputationState.get(level.getServer()).get(village, playerId);
        String villageType = FabricSettlementState.get(level.getServer()).settlements().stream()
                .filter(s -> s.buildings().stream().anyMatch(b -> b.placement().origin().equals(context.home().origin())))
                .map(s -> s.type().substring(s.type().indexOf(':') + 1)).findFirst().orElse("");
        var store = context.store(context.home());
        var specs = TradeOffers.offers(culture, culture.shops().get(shopId), villageType, store::count, reputation);
        MerchantOffers offers = new MerchantOffers();
        List<TradeOffers.Offer> kept = new ArrayList<>();
        for (var spec : specs) {
            var good = store.prototype(spec.good());
            if (good.isEmpty()) continue;
            MerchantOffer offer = spec.direction() == TradeOffers.Direction.VILLAGE_SELLS
                    ? sell(good.get(), spec) : buy(good.get(), spec);
            if (offer == null) continue;
            offers.add(offer);
            kept.add(spec);
        }
        return offers.isEmpty() ? Optional.empty() : Optional.of(new Session(offers, kept, village, context));
    }

    private static MerchantOffer sell(ItemStack good, TradeOffers.Offer spec) {
        var coins = TradeOffers.cost(spec.price());
        List<ItemCost> costs = new ArrayList<>();
        if (coins.gold() > 0) costs.add(new ItemCost(coin("denieror"), coins.gold()));
        if (coins.silver() > 0) costs.add(new ItemCost(coin("denierargent"), coins.silver()));
        if (coins.denier() > 0) costs.add(new ItemCost(coin("denier"), coins.denier()));
        if (costs.isEmpty() || costs.size() > 2) return null;
        return new MerchantOffer(costs.getFirst(), costs.size() > 1 ? Optional.of(costs.get(1)) : Optional.empty(),
                good.copyWithCount(1), spec.maxUses(), 0, 0.0F);
    }

    private static MerchantOffer buy(ItemStack good, TradeOffers.Offer spec) {
        var coins = TradeOffers.payment(spec.price());
        ItemStack paid = coins.gold() > 0 ? new ItemStack(coin("denieror"), coins.gold())
                : coins.silver() > 0 ? new ItemStack(coin("denierargent"), coins.silver()) : new ItemStack(coin("denier"), coins.denier());
        return new MerchantOffer(new ItemCost(good.getItem(), 1), paid, spec.maxUses(), 0, 0.0F);
    }

    private static Item coin(String name) {
        return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("millenaire", name));
    }

    /** Moves the traded good in or out of the shop chests and raises the player's reputation by the price. */
    public static void completed(Session session, MerchantOffer offer, Player player) {
        int index = session.offers().indexOf(offer);
        if (index < 0) return;
        var spec = session.specs().get(index);
        var store = session.context().store(session.context().home());
        if (spec.direction() == TradeOffers.Direction.VILLAGE_SELLS) store.remove(spec.good(), 1);
        else store.add(spec.good(), 1);
        if (session.context().level() instanceof ServerLevel level)
            FabricReputationState.get(level.getServer()).add(session.village(), player.getUUID(), spec.price());
    }

    public static Component noShop(MillVillagerEntity villager) {
        return Component.literal(villager.getName().getString() + ": " + villager.activity().orElse(villager.isSleeping() ? "sleeping" : "idle"));
    }
}
