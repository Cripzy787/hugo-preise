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
        long minutes = Math.max(0, millis) / 60_000L;
        if (minutes < 1) {
            return "gerade eben";
        }
        if (minutes < 60) {
            return "vor " + minutes + " Min";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return "vor " + hours + " Std";
        }
        return "vor " + (hours / 24) + " Tg";
    }
}
