package de.hugoprices.logic;

import java.util.List;
import java.util.Optional;

/** Einfacher Test ohne Bibliotheken: java de.hugoprices.logic.LogicTest */
public final class LogicTest {
    private static int failed = 0;
    private static int passed = 0;

    private static void eq(Object expected, Object actual, String name) {
        if (expected.equals(actual)) {
            passed++;
        } else {
            failed++;
            System.out.println("FEHLER " + name + ": erwartet " + expected + " war " + actual);
        }
    }

    private static Object p(String text) {
        var v = PriceParser.findFirst(text);
        return v.isPresent() ? (Object) v.getAsLong() : "leer";
    }

    public static void main(String[] args) {
        // Preis-Parser
        eq(1500000L, p("$1.500.000"), "punkt-gruppen");
        eq(1500000L, p("Preis: $1,500,000"), "komma-gruppen");
        eq(1500000L, p("1 500 000$"), "leerzeichen");
        eq(2500000L, p("2,5M"), "2,5M");
        eq(2500000L, p("2.5m"), "2.5m");
        eq(1500000L, p("1,5 Mio"), "mio");
        eq(750L, p("$750"), "einfach");
        eq(1500L, p("$1.500"), "tausenderpunkt");
        eq(1500L, p("$1,500"), "tausenderkomma");
        eq(1250L, p("1,25k"), "1,25k");
        eq(3000000000L, p("3 Mrd"), "mrd");
        eq(12345678L, p("$12.345.678,00"), "mit nachkomma");
        eq(2L, p("$1,5"), "eins-komma-fuenf rundet");
        eq("leer", p("kein preis hier"), "keine zahl");
        eq("leer", p(""), "leer");

        Settings s = new Settings();

        // Preis aus Lore
        Optional<PriceExtractor.Result> r = PriceExtractor.extract(
                List.of("§7Verkäufer: Steve", "§6Preis: §a$1.500.000", "§7Läuft ab in 2h"), s);
        eq(1500000L, r.get().price(), "lore preis");
        eq(false, r.get().perPieceHint(), "kein stueck-hinweis");

        r = PriceExtractor.extract(List.of("Preis: $2.000 pro Stück"), s);
        eq(true, r.get().perPieceHint(), "stueck-hinweis");

        r = PriceExtractor.extract(List.of("Verkäufer: 4711", "Kosten: $900"), s);
        eq(900L, r.get().price(), "ersatz waehrungszeichen");

        r = PriceExtractor.extract(List.of("Verkäufer: Steve", "Klick zum Kaufen"), s);
        eq(true, r.isEmpty(), "kein preis gefunden");

        // Pro Stück
        eq(250L, PriceExtractor.perPiece(1000, 4), "pro stueck");
        eq(1000L, PriceExtractor.perPiece(1000, 1), "pro stueck 1");

        // Menü-Erkennung
        eq(Mode.AH, ModeDetector.detect("Auktionshaus", s).get(), "ah titel");
        eq(Mode.AH, ModeDetector.detect("§8AH » Seite 1", s).get(), "ah kurz");
        eq(Mode.ORDER, ModeDetector.detect("Orders", s).get(), "order titel");
        eq(true, ModeDetector.detect("Truhe", s).isEmpty(), "keine erkennung truhe");
        eq(true, ModeDetector.detect("Shop", s).isEmpty(), "ah nicht in shop");

        // Preis-Speicher: AH = niedrigster, Order = höchster
        PriceBook b = new PriceBook();
        long max = 15 * 60_000L;
        b.observe(Mode.AH, "x", 500, 0, max);
        b.observe(Mode.AH, "x", 300, 1000, max);
        b.observe(Mode.AH, "x", 400, 2000, max);
        eq(300L, b.get(Mode.AH, "x", 3000, max).get().price(), "ah niedrigster");
        b.observe(Mode.ORDER, "x", 100, 0, max);
        b.observe(Mode.ORDER, "x", 250, 1000, max);
        b.observe(Mode.ORDER, "x", 200, 2000, max);
        eq(250L, b.get(Mode.ORDER, "x", 3000, max).get().price(), "order hoechster");
        // Abgelaufen
        eq(true, b.get(Mode.AH, "x", max + 5000, max).isEmpty(), "abgelaufen");
        // Nach Ablauf wird ein neuer (auch teurerer) AH-Preis übernommen
        b.observe(Mode.AH, "x", 900, max + 6000, max);
        eq(900L, b.get(Mode.AH, "x", max + 7000, max).get().price(), "neu nach ablauf");

        // Formatierung
        eq("$1.500.000", MoneyFormat.format(1500000, "$"), "format");
        eq("$750", MoneyFormat.format(750, "$"), "format klein");
        eq("$1.000", MoneyFormat.format(1000, "$"), "format tausend");
        eq("$0", MoneyFormat.format(0, "$"), "format null");

        System.out.println(passed + " Tests bestanden, " + failed + " fehlgeschlagen");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
