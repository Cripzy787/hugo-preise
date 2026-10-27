package de.hugoprices.logic;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** Einstellungen aus config/hugoprices.properties. Ohne Minecraft-Klassen. */
public final class Settings {
    /** Wörter im Titel des Menüs, an denen das Auktionshaus erkannt wird (Kleinschreibung). */
    public List<String> ahTitleKeywords = List.of("auktion", "auction", "ah");
    /** Wörter im Titel des Menüs, an denen Order erkannt wird. */
    public List<String> orderTitleKeywords = List.of("order", "bestell");
    /** Wörter vor dem Preis in der Item-Beschreibung (Lore). */
    public List<String> priceLabels = List.of("preis", "price");
    /** Falls kein Preiswort gefunden wird: Zeilen mit diesen Zeichen gelten als Preiszeile. */
    public List<String> currencySymbols = List.of("$");
    /** Hinweise in der Preiszeile, dass der Preis schon pro Stück gilt. */
    public List<String> perPieceHints = List.of(
            "pro stück", "pro stueck", "pro stk", "pro item", "per item", "per piece", "each", "/stück", "/stk", "/item");
    /** true: der Preis im AH gilt für den ganzen Stapel und wird durch die Stückzahl geteilt. */
    public boolean ahPriceIsTotal = true;
    /** true: der Preis in Order gilt für die ganze Menge und wird durch die Stückzahl geteilt. */
    public boolean orderPriceIsTotal = false;
    public boolean showAh = true;
    public boolean showOrder = true;
    public String currencySymbol = "$";
    /** Wie lange ein gemerkter Preis angezeigt wird, ohne dass er erneut gesehen wurde. */
    public long maxAgeMinutes = 15;
    /** Alle wie viele Ticks (20 Ticks = 1 Sekunde) das offene Menü gelesen wird. */
    public int scanEveryTicks = 5;

    /** Live-Preise von hugosmp-market.net abrufen (true) oder nur im Spiel mitgelesene Preise nutzen (false). */
    public boolean webEnabled = true;
    /** Adresse des Marktdaten-Stroms. Der Item-Schlüssel wird hinten angehängt. */
    public String webBaseUrl = "https://hugosmp-market.net/api/stream/item?item=";
    /** So lange (Sekunden) werden abgerufene Preise zwischengespeichert, bevor neu gefragt wird. */
    public long webCacheSeconds = 60;
    /** Angebote im Auktionshaus, die älter sind (Minuten), zählen nicht. 0 = kein Limit. */
    public long ahMaxAgeMinutes = 60;
    /** Orders, die älter sind (Stunden), zählen nicht. 0 = kein Limit. */
    public long orderMaxAgeHours = 168;
    /** true: auch Items ohne Verzauberung abfragen (mehr Abrufe). */
    public boolean queryPlainItems = false;
    /** Trennzeichen zwischen mehreren Verzauberungen im Item-Schlüssel (nicht bestätigt). */
    public String enchantSeparator = ",";

    private static final String DEFAULT_FILE = """
            # Cripzy's Hugo Preise - Einstellungen. Listen werden mit Komma getrennt, Gross-/Kleinschreibung egal.
            # Nach Aenderungen Minecraft neu starten.

            # Woran das Menue erkannt wird (Teil des Titels). Kurze Woerter (bis 3 Buchstaben) muessen ein ganzes Wort sein.
            ahTitleKeywords=auktion,auction,ah
            orderTitleKeywords=order,bestell

            # Woran die Preiszeile in der Item-Beschreibung erkannt wird.
            priceLabels=preis,price
            # Falls kein Preiswort vorkommt: Zeile mit diesem Zeichen gilt als Preiszeile.
            currencySymbols=$
            # Steht so etwas in der Preiszeile, gilt der Preis pro Stueck (egal wie die Optionen unten stehen).
            perPieceHints=pro stück,pro stueck,pro stk,pro item,per item,per piece,each,/stück,/stk,/item

            # true = der angezeigte Preis gilt fuer den ganzen Stapel und wird durch die Stueckzahl geteilt.
            ahPriceIsTotal=true
            orderPriceIsTotal=false

            showAh=true
            showOrder=true
            currencySymbol=$

            # So lange (Minuten) bleibt ein gemerkter Preis sichtbar, wenn du ihn nicht erneut siehst.
            maxAgeMinutes=15
            scanEveryTicks=5

            # Live-Preise von hugosmp-market.net (true) oder nur Preise nutzen, die du im Spiel gesehen hast (false).
            webEnabled=true
            webBaseUrl=https://hugosmp-market.net/api/stream/item?item=
            # So lange (Sekunden) wird ein abgerufener Preis zwischengespeichert.
            webCacheSeconds=60
            # Auktionen aelter als so viele Minuten zaehlen nicht (0 = kein Limit).
            ahMaxAgeMinutes=60
            # Orders aelter als so viele Stunden zaehlen nicht (0 = kein Limit).
            orderMaxAgeHours=168
            # true = auch Items ohne Verzauberung abfragen.
            queryPlainItems=false
            # Trennzeichen zwischen mehreren Verzauberungen im Item-Schluessel.
            enchantSeparator=,
            """;

    public static Settings load(Path file) {
        Settings s = new Settings();
        try {
            if (!Files.exists(file)) {
                Path parent = file.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(file, DEFAULT_FILE, StandardCharsets.UTF_8);
                return s;
            }
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(r);
            }
            s.ahTitleKeywords = list(p, "ahTitleKeywords", s.ahTitleKeywords);
            s.orderTitleKeywords = list(p, "orderTitleKeywords", s.orderTitleKeywords);
            s.priceLabels = list(p, "priceLabels", s.priceLabels);
            s.currencySymbols = list(p, "currencySymbols", s.currencySymbols);
            s.perPieceHints = list(p, "perPieceHints", s.perPieceHints);
            s.ahPriceIsTotal = bool(p, "ahPriceIsTotal", s.ahPriceIsTotal);
            s.orderPriceIsTotal = bool(p, "orderPriceIsTotal", s.orderPriceIsTotal);
            s.showAh = bool(p, "showAh", s.showAh);
            s.showOrder = bool(p, "showOrder", s.showOrder);
            String cur = p.getProperty("currencySymbol");
            if (cur != null && !cur.isBlank()) {
                s.currencySymbol = cur.trim();
            }
            s.maxAgeMinutes = Math.max(1, longValue(p, "maxAgeMinutes", s.maxAgeMinutes));
            s.scanEveryTicks = (int) Math.max(1, longValue(p, "scanEveryTicks", s.scanEveryTicks));
            s.webEnabled = bool(p, "webEnabled", s.webEnabled);
            String url = p.getProperty("webBaseUrl");
            if (url != null && !url.isBlank()) {
                s.webBaseUrl = url.trim();
            }
            s.webCacheSeconds = Math.max(10, longValue(p, "webCacheSeconds", s.webCacheSeconds));
            s.ahMaxAgeMinutes = Math.max(0, longValue(p, "ahMaxAgeMinutes", s.ahMaxAgeMinutes));
            s.orderMaxAgeHours = Math.max(0, longValue(p, "orderMaxAgeHours", s.orderMaxAgeHours));
            s.queryPlainItems = bool(p, "queryPlainItems", s.queryPlainItems);
            String sep = p.getProperty("enchantSeparator");
            if (sep != null && !sep.isEmpty()) {
                s.enchantSeparator = sep;
            }
        } catch (IOException | RuntimeException e) {
            // Bei Fehlern gelten die Standardwerte.
        }
        return s;
    }

    private static List<String> list(Properties p, String key, List<String> fallback) {
        String raw = p.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String t = part.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out.isEmpty() ? fallback : List.copyOf(out);
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        String raw = p.getProperty(key);
        return raw == null ? fallback : Boolean.parseBoolean(raw.trim());
    }

    private static long longValue(Properties p, String key, long fallback) {
        String raw = p.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
