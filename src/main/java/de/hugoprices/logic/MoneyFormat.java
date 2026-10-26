package de.hugoprices.logic;

/** Formatierung von Preisen und Zeiten für den Tooltip. */
public final class MoneyFormat {
    private MoneyFormat() {
    }

    /** 1500000 -> "$1.500.000" */
    public static String format(long value, String symbol) {
        String digits = Long.toString(Math.abs(value));
        StringBuilder sb = new StringBuilder();
        int first = digits.length() % 3;
        if (first > 0) {
            sb.append(digits, 0, first);
        }
        for (int i = first; i < digits.length(); i += 3) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(digits, i, i + 3);
        }
        return (value < 0 ? "-" : "") + symbol + sb;
    }

    public static String age(long millis) {
        long minutes = millis / 60_000L;
        if (minutes < 1) {
            return "gerade eben";
        }
        return "vor " + minutes + " Min";
    }
}
