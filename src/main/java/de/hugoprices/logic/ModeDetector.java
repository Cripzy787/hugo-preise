package de.hugoprices.logic;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Entscheidet anhand des Menütitels, ob gerade das Auktionshaus oder Order offen ist. */
public final class ModeDetector {
    private ModeDetector() {
    }

    public static Optional<Mode> detect(String title, Settings settings) {
        String t = title == null ? "" : title.replaceAll("§.", "").toLowerCase(Locale.ROOT);
        if (matches(t, settings.orderTitleKeywords)) {
            return Optional.of(Mode.ORDER);
        }
        if (matches(t, settings.ahTitleKeywords)) {
            return Optional.of(Mode.AH);
        }
        return Optional.empty();
    }

    static boolean matches(String lowerTitle, List<String> keywords) {
        for (String kw : keywords) {
            if (kw.isEmpty()) {
                continue;
            }
            if (kw.length() <= 3) {
                Pattern p = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(kw) + "(?![\\p{L}\\p{N}])");
                if (p.matcher(lowerTitle).find()) {
                    return true;
                }
            } else if (lowerTitle.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
