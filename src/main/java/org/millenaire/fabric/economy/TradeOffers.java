package org.millenaire.fabric.economy;

import java.util.*;
import java.util.function.ToIntFunction;

/**
 * Offers of a village shop: goods it sells to players and goods it buys from them, with prices after village
 * overrides, stock limits and reputation requirements, following the original trade rules.
 */
public final class TradeOffers {
    public static final int SILVER = 64, GOLD = 64 * 64;

    public enum Direction { VILLAGE_SELLS, VILLAGE_BUYS }

    /** One offer; {@code maxUses} is the stock (selling) or remaining demand (buying). */
    public record Offer(String good, Direction direction, int price, int maxUses) {}

    /** Coins for a price: at most two stacks of 64, as a trade has two cost slots. */
    public record Coins(int gold, int silver, int denier) {
        public int total() { return gold * GOLD + silver * SILVER + denier; }
    }

    private TradeOffers() {}

    /**
     * @param stock goods currently in the shop building; ignored for auto-generated goods
     * @param reputation the player's reputation with the village
     */
    public static List<Offer> offers(TradeCatalog.CultureTrade culture, TradeCatalog.Shop shop, String villageType,
                                     ToIntFunction<String> stock, int reputation) {
        List<Offer> result = new ArrayList<>();
        for (String key : shop.sells()) {
            var good = culture.goods().get(key);
            if (good == null || reputation < good.minReputation()) continue;
            int price = culture.sellingPrice(villageType, good);
            if (price <= 0) continue;
            int available = good.autoGenerate() ? 64 : Math.max(0, stock.applyAsInt(key) - good.reservedQuantity());
            if (available > 0) result.add(new Offer(key, Direction.VILLAGE_SELLS, price, Math.min(available, 64)));
        }
        List<String> buys = new ArrayList<>(shop.buys());
        buys.addAll(shop.buysOptional());
        for (String key : buys) {
            var good = culture.goods().get(key);
            if (good == null || reputation < good.minReputation()) continue;
            int price = culture.buyingPrice(villageType, good);
            if (price <= 0) continue;
            int demand = good.targetQuantity() > 0 ? good.targetQuantity() - stock.applyAsInt(key) : 64;
            if (demand > 0) result.add(new Offer(key, Direction.VILLAGE_BUYS, price, Math.min(demand, 64)));
        }
        return result;
    }

    /** What a player pays: exact when two coin stacks suffice, otherwise rounded up to the next silver. */
    public static Coins cost(int price) {
        if (price <= 0) throw new IllegalArgumentException("Price must be positive");
        if (price <= SILVER) return new Coins(0, 0, price);
        if (price < GOLD + SILVER) {
            int silver = price / SILVER, denier = price % SILVER;
            if (silver <= 64 && denier <= 64) return new Coins(0, silver, denier);
        }
        int gold = price / GOLD;
        int silver = (price % GOLD + SILVER - 1) / SILVER;
        if (silver == SILVER) { gold++; silver = 0; }
        if (gold > 64) throw new IllegalArgumentException("Price exceeds two stacks of gold deniers");
        return new Coins(gold, silver, 0);
    }

    /** What a village pays: a single coin stack, rounded down to the largest denomination that fits. */
    public static Coins payment(int price) {
        if (price <= 0) throw new IllegalArgumentException("Price must be positive");
        if (price < SILVER) return new Coins(0, 0, price);
        if (price < GOLD) return new Coins(0, Math.min(64, price / SILVER), 0);
        return new Coins(Math.min(64, price / GOLD), 0, 0);
    }
}
