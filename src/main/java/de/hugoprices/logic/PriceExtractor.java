package de.hugoprices.logic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

/** Sucht in den Beschreibungszeilen (Lore) eines Menü-Items nach dem Preis. Ohne Minecraft-Klassen. */
public final class PriceExtractor {
    private PriceExtractor() {
    }

    /** @param perPieceHint true, wenn in der Preiszeile steht, dass der Preis pro Stück gilt */
    public record Result(long price, boolean perPieceHint) {
    }

    public static Optional<Result> extract(List<String> loreLines, Settings s) {
        // 1. Zeilen mit einem Preiswort, z. B. "Preis: $1.500"
        for (String raw : loreLines) {
            String line = stripFormatting(raw);
            String lower = line.toLowerCase(Locale.ROOT);
            for (String label : s.priceLabels) {
                int idx = lower.indexOf(label);
                if (idx < 0) {
                    continue;
                }
                OptionalLong v = PriceParser.findFirst(line.substring(idx + label.length()));
                if (v.isPresent()) {
                    return Optional.of(new Result(v.getAsLong(), hasHint(lower, s)));
                }
            }
        }
        // 2. Ersatz: Zeile mit Währungszeichen
        for (String raw : loreLines) {
            String line = stripFormatting(raw);
            for (String cur : s.currencySymbols) {
                if (!cur.isEmpty() && line.contains(cur)) {
                    OptionalLong v = PriceParser.findFirst(line);
                    if (v.isPresent()) {
                        return Optional.of(new Result(v.getAsLong(), hasHint(line.toLowerCase(Locale.ROOT), s)));
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Preis pro Stück aus dem Gesamtpreis (auf ganze Einheiten gerundet). */
    public static long perPiece(long total, int count) {
        if (count <= 1) {
            return total;
        }
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP).longValue();
    }

    static String stripFormatting(String raw) {
        return raw == null ? "" : raw.replaceAll("§.", "");
    }

    private static boolean hasHint(String lowerLine, Settings s) {
        for (String hint : s.perPieceHints) {
            if (!hint.isEmpty() && lowerLine.contains(hint)) {
                return true;
            }
        }
        return false;
    }
}
