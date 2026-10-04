package org.millenaire.fabric.economy;

import org.millenaire.fabric.content.LegacyContentCatalog;
import org.millenaire.fabric.content.LegacyDocument;
import org.millenaire.fabric.content.LegacyNumbers;

import java.util.*;

/**
 * Per-culture commerce data: {@code traded_goods.txt}, {@code shops/*.txt} and the
 * {@code sellingPrice}/{@code buyingPrice} overrides of village types. Rows that cannot be traded as
 * written (unknown goods alias, malformed numbers) are rejected with a diagnostic.
 */
public record TradeCatalog(Map<String, CultureTrade> cultures, List<String> diagnostics) {
    /**
     * One {@code traded_goods.txt} row. Prices are in deniers; selling means the village sells to the player.
     * Six-column rows (foreign merchant goods only) keep the defaults for the trailing columns.
     */
    public record TradeGood(String key, int sellingPrice, int buyingPrice, int reservedQuantity, int targetQuantity,
                            int foreignMerchantPrice, boolean autoGenerate, int minReputation, String category) {
        public TradeGood {
            key = key.toLowerCase(Locale.ROOT);
            category = category.toLowerCase(Locale.ROOT);
            if (key.isBlank()) throw new IllegalArgumentException("empty good key");
            if (sellingPrice < 0 || buyingPrice < 0 || reservedQuantity < 0 || targetQuantity < 0 || foreignMerchantPrice < 0)
                throw new IllegalArgumentException("negative price or quantity for " + key);
        }
        public boolean villageSells() { return sellingPrice > 0; }
        public boolean villageBuys() { return buyingPrice > 0; }
    }

    /** {@code sells}/{@code buys}/{@code buysoptional}/{@code deliverto} lists of a shop building. */
    public record Shop(String id, List<String> sells, List<String> buys, List<String> buysOptional, List<String> deliverTo) {
        public Shop {
            sells = List.copyOf(sells); buys = List.copyOf(buys); buysOptional = List.copyOf(buysOptional); deliverTo = List.copyOf(deliverTo);
        }
    }

    public record CultureTrade(String culture, Map<String, TradeGood> goods, Map<String, Shop> shops,
                               Map<String, Map<String, Integer>> sellingOverrides, Map<String, Map<String, Integer>> buyingOverrides) {
        public CultureTrade {
            goods = Collections.unmodifiableMap(new LinkedHashMap<>(goods));
            shops = Collections.unmodifiableMap(new LinkedHashMap<>(shops));
            sellingOverrides = copy(sellingOverrides);
            buyingOverrides = copy(buyingOverrides);
        }
        private static Map<String, Map<String, Integer>> copy(Map<String, Map<String, Integer>> source) {
            Map<String, Map<String, Integer>> result = new LinkedHashMap<>();
            source.forEach((key, value) -> result.put(key, Map.copyOf(value)));
            return Collections.unmodifiableMap(result);
        }
        /** Price at which a village of this type sells the good to a player; 0 means not for sale. */
        public int sellingPrice(String villageType, TradeGood good) {
            return sellingOverrides.getOrDefault(villageType, Map.of()).getOrDefault(good.key(), good.sellingPrice());
        }
        /** Village-specific price for a good that may not appear in traded_goods.txt. */
        public Optional<Integer> sellingOverride(String villageType, String good) {
            return Optional.ofNullable(sellingOverrides.getOrDefault(villageType, Map.of()).get(good));
        }
        /** Price a village of this type pays the player for the good; 0 means it does not buy. */
        public int buyingPrice(String villageType, TradeGood good) {
            return buyingOverrides.getOrDefault(villageType, Map.of()).getOrDefault(good.key(), good.buyingPrice());
        }
    }

    public TradeCatalog {
        cultures = Collections.unmodifiableMap(new LinkedHashMap<>(cultures));
        diagnostics = List.copyOf(diagnostics);
    }

    public static TradeCatalog empty() { return new TradeCatalog(Map.of(), List.of()); }

    public static TradeCatalog from(LegacyContentCatalog catalog) {
        Set<String> aliases = catalog.goods().goods().keySet();
        Map<String, CultureTrade> cultures = new TreeMap<>();
        List<String> diagnostics = new ArrayList<>();
        catalog.cultures().forEach((id, culture) -> {
            Map<String, TradeGood> goods = new LinkedHashMap<>();
            LegacyDocument traded = culture.documents().get("traded_goods.txt");
            if (traded != null) readGoods(traded, aliases, goods, diagnostics);
            Map<String, Shop> shops = new TreeMap<>();
            culture.category("shops").forEach((path, document) -> {
                String shopId = path.substring(path.lastIndexOf('/') + 1, path.length() - 4).toLowerCase(Locale.ROOT);
                // Goods the culture does not trade are skipped individually; the rest of the shop stays usable.
                Set<String> missing = new TreeSet<>();
                shops.put(shopId, new Shop(shopId, traded(document, "sells", goods, missing), traded(document, "buys", goods, missing),
                        traded(document, "buysoptional", goods, missing), traded(document, "deliverto", goods, missing)));
                if (!missing.isEmpty()) diagnostics.add(document.source() + ": skipped goods missing from traded_goods.txt " + missing);
            });
            Map<String, Map<String, Integer>> selling = new TreeMap<>(), buying = new TreeMap<>();
            culture.category("villages").forEach((path, document) -> {
                String type = path.substring(path.lastIndexOf('/') + 1, path.length() - 4).toLowerCase(Locale.ROOT);
                overrides(document, "sellingprice", aliases, type, selling, diagnostics);
                overrides(document, "buyingprice", aliases, type, buying, diagnostics);
            });
            cultures.put(id, new CultureTrade(id, goods, shops, selling, buying));
        });
        return new TradeCatalog(cultures, diagnostics);
    }

    static void readGoods(LegacyDocument document, Set<String> aliases, Map<String, TradeGood> goods, List<String> diagnostics) {
        int lineNumber = 0;
        for (String raw : document.lines()) {
            lineNumber++;
            String line = raw.replace("﻿", "").trim();
            if (line.isEmpty() || line.startsWith("//")) continue;
            String[] parts = line.split(",", -1);
            try {
                if (parts.length < 6) throw new IllegalArgumentException("expected at least 6 columns");
                String key = parts[0].trim().toLowerCase(Locale.ROOT);
                if (!aliases.contains(key)) throw new IllegalArgumentException("unknown goods alias " + key);
                boolean auto = parts.length > 6 && Boolean.parseBoolean(parts[6].trim());
                int minReputation = parts.length > 8 && !parts[8].isBlank() ? LegacyNumbers.product(parts[8]) : 0;
                String category = parts.length > 9 ? parts[9].trim() : "";
                TradeGood good = new TradeGood(key, number(parts[1]), number(parts[2]),
                        number(parts[3]), number(parts[4]), number(parts[5]),
                        auto, minReputation, category);
                if (goods.put(key, good) != null) diagnostics.add(document.source() + ":" + lineNumber + ": duplicate good " + key + " replaces earlier row");
            } catch (IllegalArgumentException exception) {
                diagnostics.add(document.source() + ":" + lineNumber + ": " + exception.getMessage());
            }
        }
    }

    /** Empty numeric columns mean 0, as in the original loader. */
    private static int number(String value) { return value.isBlank() ? 0 : LegacyNumbers.product(value); }

    private static List<String> traded(LegacyDocument document, String key, Map<String, TradeGood> goods, Set<String> missing) {
        List<String> result = new ArrayList<>();
        for (String value : document.values(key))
            for (String part : value.split(",")) {
                String good = part.trim().toLowerCase(Locale.ROOT);
                if (good.isEmpty() || result.contains(good)) continue;
                if (goods.containsKey(good)) result.add(good); else missing.add(good);
            }
        return result;
    }

    /**
     * Village prices may use money notation {@code gold/silver/denier} (64 deniers per silver, 64 silver
     * per gold), e.g. {@code 1/0/10} = 4106 deniers and {@code 5/32} = 352 deniers.
     */
    static int price(String value) {
        String[] parts = value.trim().split("/", -1);
        if (parts.length > 3) throw new IllegalArgumentException("invalid price '" + value + "'");
        long total = 0;
        for (String part : parts) {
            int amount = LegacyNumbers.product(part);
            if (amount < 0) throw new IllegalArgumentException("negative price '" + value + "'");
            total = Math.addExact(Math.multiplyExact(total, 64), amount);
        }
        if (total > Integer.MAX_VALUE) throw new IllegalArgumentException("price out of range '" + value + "'");
        return (int) total;
    }

    /** Village types may price goods outside traded_goods.txt; any known goods alias is accepted. */
    private static void overrides(LegacyDocument document, String key, Set<String> aliases, String type,
                                  Map<String, Map<String, Integer>> target, List<String> diagnostics) {
        for (String value : document.values(key)) {
            String[] parts = value.split(",", -1);
            try {
                if (parts.length != 2) throw new IllegalArgumentException("expected good,price");
                String good = parts[0].trim().toLowerCase(Locale.ROOT);
                if (!aliases.contains(good)) throw new IllegalArgumentException("unknown goods alias " + good);
                int price = price(parts[1]);
                target.computeIfAbsent(type, ignored -> new LinkedHashMap<>()).put(good, price);
            } catch (IllegalArgumentException exception) {
                diagnostics.add(document.source() + ": " + key + "=" + value + ": " + exception.getMessage());
            }
        }
    }
}
