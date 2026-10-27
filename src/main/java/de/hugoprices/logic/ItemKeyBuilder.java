package de.hugoprices.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Baut den Schlüssel, unter dem hugosmp-market.net ein verzaubertes Item führt.
 * Bestätigtes Beispiel: enchanted_book/multishot:1  (Item / Verzauberung:Stufe).
 * Bei mehreren Verzauberungen ist das Format nicht bestätigt: alphabetisch sortiert, mit Trennzeichen getrennt.
 */
public final class ItemKeyBuilder {
    private ItemKeyBuilder() {
    }

    /** @param enchants Einträge der Form "minecraft:sharpness=5" */
    public static String build(String itemId, List<String> enchants, String separator) {
        String id = strip(itemId);
        if (enchants == null || enchants.isEmpty()) {
            return id;
        }
        List<String> parts = new ArrayList<>();
        for (String e : enchants) {
            int eq = e.lastIndexOf('=');
            String name = strip(eq < 0 ? e : e.substring(0, eq));
            String level = eq < 0 ? "1" : e.substring(eq + 1);
            parts.add(name + ":" + level);
        }
        Collections.sort(parts);
        return id + "/" + String.join(separator, parts);
    }

    private static String strip(String s) {
        return s.startsWith("minecraft:") ? s.substring("minecraft:".length()) : s;
    }
}
