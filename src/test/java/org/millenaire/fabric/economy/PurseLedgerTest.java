package org.millenaire.fabric.economy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PurseLedgerTest {
    @Test void normalizesLegacyBase64Denominations() {
        assertEquals(new PurseLedger(0, 1, 1), PurseLedger.fromTotal(4160));
        assertEquals(4160, new PurseLedger(0, 1, 1).total());
        assertEquals(new PurseLedger(63, 63, 1), PurseLedger.fromTotal(8191));
    }
    @Test void depositsAndWithdrawalsPreserveTotal() {
        var purse = PurseLedger.fromTotal(63).plus(1, 1, 1);
        assertEquals(new PurseLedger(0, 2, 1), purse);
        assertEquals(new PurseLedger(32, 1, 1), purse.subtract(32));
    }
    @Test void rejectsNegativeAndUnnormalizedState() {
        assertThrows(IllegalArgumentException.class, () -> new PurseLedger(64, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> PurseLedger.fromTotal(-1));
        assertThrows(IllegalArgumentException.class, () -> PurseLedger.fromTotal(1).subtract(2));
    }
}
