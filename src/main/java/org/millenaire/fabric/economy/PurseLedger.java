package org.millenaire.fabric.economy;

/** Lossless denomination state used by the legacy purse (1 denier, 64 per silver, 4096 per gold). */
public record PurseLedger(int denier, int silver, int gold) {
    public PurseLedger {
        if (denier < 0 || silver < 0 || gold < 0) throw new IllegalArgumentException("Purse denominations cannot be negative");
        if (denier >= 64 || silver >= 64) throw new IllegalArgumentException("Purse denominations must be normalized below 64");
    }

    public static PurseLedger fromTotal(int total) {
        if (total < 0) throw new IllegalArgumentException("Purse total cannot be negative");
        int denier = total % 64;
        int silver = (total / 64) % 64;
        return new PurseLedger(denier, silver, total / 4096);
    }

    public int total() { return denier + silver * 64 + gold * 4096; }

    public PurseLedger plus(int deniers, int silverDeniers, int goldDeniers) {
        if (deniers < 0 || silverDeniers < 0 || goldDeniers < 0) throw new IllegalArgumentException("Purse deposit cannot be negative");
        long result = (long) total() + deniers + (long) silverDeniers * 64 + (long) goldDeniers * 4096;
        if (result > Integer.MAX_VALUE) throw new IllegalArgumentException("Purse total is too large");
        return fromTotal((int) result);
    }

    public PurseLedger subtract(int amount) {
        if (amount < 0 || amount > total()) throw new IllegalArgumentException("Purse withdrawal exceeds balance");
        return fromTotal(total() - amount);
    }
}
