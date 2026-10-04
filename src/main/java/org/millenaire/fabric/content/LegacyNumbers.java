package org.millenaire.fabric.content;

/** Integer fields in legacy content may be written as products such as {@code 2*64*64}. */
public final class LegacyNumbers {
    private LegacyNumbers() {}

    public static int product(String value) {
        long result = 1;
        for (String factor : value.split("\\*", -1)) {
            try {
                result = Math.multiplyExact(result, Integer.parseInt(factor.trim()));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("invalid number '" + value + "'");
            }
            if (result > Integer.MAX_VALUE || result < Integer.MIN_VALUE) throw new IllegalArgumentException("number out of range '" + value + "'");
        }
        return (int) result;
    }
}
