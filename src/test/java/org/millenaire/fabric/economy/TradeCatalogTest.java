package org.millenaire.fabric.economy;

import org.junit.jupiter.api.Test;
import org.millenaire.fabric.content.LegacyCatalogLoader;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class TradeCatalogTest {
    private static TradeCatalog bundled() throws Exception {
        Path root = Path.of(TradeCatalogTest.class.getResource("/todeploy/millenaire/blocklist.txt").toURI()).getParent();
        return TradeCatalog.from(LegacyCatalogLoader.load(root));
    }

    @Test
    void loadsEveryTradedGoodsRowOfAllCultures() throws Exception {
        var trade = bundled();
        Map<String, Integer> counts = new TreeMap<>();
        trade.cultures().forEach((id, culture) -> counts.put(id, culture.goods().size()));
        assertEquals(Map.of("byzantines", 189, "indian", 135, "inuits", 172, "japanese", 130, "mayan", 129,
                "norman", 186, "seljuk", 189), counts);
        assertTrue(trade.diagnostics().stream().allMatch(d -> d.contains("skipped goods missing from traded_goods.txt")),
                trade.diagnostics().toString());

        var norman = trade.cultures().get("norman");
        var wood = norman.goods().get("wood");
        assertEquals(new TradeCatalog.TradeGood("wood", 2, 1, 128, 1024, 0, false, 0, "construction"), wood);
        assertTrue(wood.villageSells());
        assertFalse(norman.goods().get("wool_orange").villageSells());
        assertEquals(14, norman.goods().get("wool_orange").foreignMerchantPrice());
        // Six-column foreign merchant rows keep defaults; products are evaluated.
        var dye = trade.cultures().get("byzantines").goods().get("dye_brown");
        assertEquals(16 * 64, dye.foreignMerchantPrice());
        assertEquals("", dye.category());
        assertFalse(norman.shops().isEmpty());
    }

    @Test
    void appliesVillagePriceOverridesInMoneyNotation() throws Exception {
        assertEquals(10, TradeCatalog.price("10"));
        assertEquals(5 * 64 + 32, TradeCatalog.price("5/32"));
        assertEquals(64 * 64 + 10, TradeCatalog.price("1/0/10"));
        assertEquals(128, TradeCatalog.price("2*64"));
        assertThrows(IllegalArgumentException.class, () -> TradeCatalog.price("1/2/3/4"));
        assertThrows(IllegalArgumentException.class, () -> TradeCatalog.price("-1"));

        var japanese = bundled().cultures().get("japanese");
        assertEquals(Optional.of(2 * 64), japanese.sellingOverride("trading", "sake"));
        var sake = japanese.goods().get("sake");
        if (sake != null) assertEquals(2 * 64, japanese.sellingPrice("trading", sake));
    }
}
