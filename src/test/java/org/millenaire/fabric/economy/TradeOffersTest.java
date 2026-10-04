package org.millenaire.fabric.economy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TradeOffersTest {
    private static TradeCatalog.CultureTrade culture() {
        var goods = Map.of(
                "wood", new TradeCatalog.TradeGood("wood", 2, 1, 128, 1024, 0, false, 0, "construction"),
                "calva", new TradeCatalog.TradeGood("calva", 200, 0, 0, 0, 0, false, 64, "food"),
                "bread", new TradeCatalog.TradeGood("bread", 5, 0, 0, 0, 0, true, 0, "food"),
                "iron", new TradeCatalog.TradeGood("iron", 25, 20, 0, 10, 0, false, 0, "crafting"));
        return new TradeCatalog.CultureTrade("norman", goods, Map.of(), Map.of("trading", Map.of("wood", 3)), Map.of());
    }

    @Test
    void sellsStockAboveReserveAndBuysUpToTarget() {
        var shop = new TradeCatalog.Shop("forge", List.of("wood", "calva", "bread"), List.of("iron", "wood"), List.of(), List.of());
        var stock = Map.of("wood", 130, "iron", 7);
        var offers = TradeOffers.offers(culture(), shop, "agricole", good -> stock.getOrDefault(good, 0), 0);
        assertTrue(offers.contains(new TradeOffers.Offer("wood", TradeOffers.Direction.VILLAGE_SELLS, 2, 2)), offers.toString());
        assertTrue(offers.contains(new TradeOffers.Offer("bread", TradeOffers.Direction.VILLAGE_SELLS, 5, 64)), "auto-generated");
        assertTrue(offers.stream().noneMatch(o -> o.good().equals("calva")), "reputation 64 required");
        assertTrue(offers.contains(new TradeOffers.Offer("iron", TradeOffers.Direction.VILLAGE_BUYS, 20, 3)), "target 10, has 7");
        assertTrue(offers.contains(new TradeOffers.Offer("wood", TradeOffers.Direction.VILLAGE_BUYS, 1, 64)));
        assertTrue(TradeOffers.offers(culture(), shop, "agricole", good -> 0, 64).stream().noneMatch(o -> o.good().equals("calva")), "no stock, no offer");
        assertTrue(TradeOffers.offers(culture(), shop, "agricole", good -> good.equals("calva") ? 1 : 0, 64).stream()
                .anyMatch(o -> o.good().equals("calva")), "enough reputation and stock");
        var overridden = TradeOffers.offers(culture(), shop, "trading", good -> stock.getOrDefault(good, 0), 0);
        assertTrue(overridden.contains(new TradeOffers.Offer("wood", TradeOffers.Direction.VILLAGE_SELLS, 3, 2)), "village price override");
    }

    @Test
    void splitsPricesIntoCoinStacks() {
        assertEquals(new TradeOffers.Coins(0, 0, 40), TradeOffers.cost(40));
        assertEquals(new TradeOffers.Coins(0, 3, 8), TradeOffers.cost(200));
        assertEquals(200, TradeOffers.cost(200).total());
        assertEquals(new TradeOffers.Coins(0, 64, 10), TradeOffers.cost(4106), "exact while two stacks suffice");
        var large = TradeOffers.cost(3 * 4096 + 100);
        assertTrue(large.total() >= 3 * 4096 + 100 && large.silver() <= 64 && large.denier() == 0, large.toString());
        assertEquals(new TradeOffers.Coins(0, 0, 20), TradeOffers.payment(20));
        assertEquals(new TradeOffers.Coins(0, 3, 0), TradeOffers.payment(200));
        assertThrows(IllegalArgumentException.class, () -> TradeOffers.cost(0));
    }
}
