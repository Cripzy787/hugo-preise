package de.hugoprices.logic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest eine Zahl aus einem Text wie "$1.500.000", "2,5M", "1,5 Mio", "1 500 000$" oder "750".
 * Enthält bewusst KEINE Minecraft-Klassen, damit die Logik ohne Spiel getestet werden kann.
 */
public final class PriceParser {
    private PriceParser() {
    }

    /**
     * Gruppe 1: die Zahl. Entweder mit Tausendergruppen (je genau 3 Ziffern, getrennt durch . , ' oder Leerzeichen)
     * und optionalem Dezimalteil, oder eine einfache Zahl mit optionalem Dezimalteil.
     * Gruppe 2: optionale Abkürzung (k, m, mio, mrd, b, bn, tsd).
     */
    private static final Pattern TOKEN = Pattern.compile(
            "(\\d{1,3}(?:[.,'\\u00A0\\u202F ]\\d{3})+(?:[.,]\\d+)?|\\d+(?:[.,]\\d+)?)"
                    + "(?:\\s*(mrd|mio|tsd|bn|k|m|b)(?!\\p{L}))?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Liefert die erste erkennbare Zahl im Text (auf ganze Einheiten gerundet). */
    public static OptionalLong findFirst(String text) {
        if (text == null) {
            return OptionalLong.empty();
        }
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            OptionalLong v = toValue(m.group(1), m.group(2));
            if (v.isPresent()) {
                return v;
            }
        }
        return OptionalLong.empty();
    }

    static OptionalLong toValue(String number, String suffix) {
        String s = number
                .replace(" ", "")
                .replace(" ", "")
                .replace(" ", "")
                .replace("'", "");

        int dot = s.lastIndexOf('.');
        int comma = s.lastIndexOf(',');
        String normalized;

        if (dot >= 0 && comma >= 0) {
            // Beide Zeichen vorhanden: das letzte ist das Komma der Dezimalstelle.
            char decimal = dot > comma ? '.' : ',';
            char group = decimal == '.' ? ',' : '.';
            normalized = s.replace(String.valueOf(group), "").replace(decimal, '.');
        } else if (dot >= 0 || comma >= 0) {
            char sep = dot >= 0 ? '.' : ',';
            int count = 0;
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) == sep) {
                    count++;
                }
            }
            int last = s.lastIndexOf(sep);
            String before = s.substring(0, last);
            String after = s.substring(last + 1);
            boolean thousands = count > 1
                    || (after.length() == 3 && before.length() >= 1 && before.length() <= 3 && !before.equals("0"));
            normalized = thousands ? s.replace(String.valueOf(sep), "") : s.replace(sep, '.');
        } else {
            normalized = s;
        }

        BigDecimal value;
        try {
            value = new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }

        if (suffix != null) {
            switch (suffix.toLowerCase(Locale.ROOT)) {
                case "k", "tsd" -> value = value.multiply(BigDecimal.valueOf(1_000L));
                case "m", "mio" -> value = value.multiply(BigDecimal.valueOf(1_000_000L));
                case "b", "bn", "mrd" -> value = value.multiply(BigDecimal.valueOf(1_000_000_000L));
                default -> {
                }
            }
        }

        try {
            return OptionalLong.of(value.setScale(0, RoundingMode.HALF_UP).longValueExact());
        } catch (ArithmeticException e) {
            return OptionalLong.empty();
        }
    }
}
