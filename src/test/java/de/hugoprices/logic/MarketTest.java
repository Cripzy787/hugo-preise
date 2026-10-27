package de.hugoprices.logic;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Tests für den Live-Preis-Teil: java de.hugoprices.logic.MarketTest */
public final class MarketTest {
    private static int failed = 0;
    private static int passed = 0;

    private static void eq(Object expected, Object actual, String name) {
        if (java.util.Objects.equals(expected, actual)) {
            passed++;
        } else {
            failed++;
            System.out.println("FEHLER " + name + ": erwartet " + expected + " war " + actual);
        }
    }

    private static final String KEY = "enchanted_book/multishot:1";
    private static final Instant NOW = Instant.parse("2026-10-27T22:20:00Z");

    private static String auction(String player, long price, String at, String key) {
        return "{\"id\":1,\"player_name\":\"" + player + "\",\"item_key\":\"" + key + "\",\"price_unit\":" + price
                + ",\"price_total\":" + price + ",\"quantity\":1,\"observed_at\":\"" + at
                + "\",\"star_tool_unique_id\":null,\"star_tool_expire_at\":null,\"signed_at\":null,\"signed_by\":null,"
                + "\"map_id\":null,\"map_name\":null,\"custom_item_meta\":null,\"star_tool_listed_ttl_ms\":null}";
    }

    private static String order(String player, long unit, int qty, String at, String key) {
        return "{\"id\":2,\"player_name\":\"" + player + "\",\"item_key\":\"" + key + "\",\"price_unit\":" + unit
                + ",\"price_total\":" + (unit * qty) + ",\"quantity\":" + qty + ",\"observed_at\":\"" + at + "\"}";
    }

    /** Nachgebaut nach den Screenshots von hugosmp-market.net. */
    private static String initJson() {
        String nested = "{\"id\":9,\"player_name\":\"Nested\",\"item_key\":\"" + KEY + "\",\"price_unit\":46000,"
                + "\"quantity\":1,\"observed_at\":\"2026-10-27T22:10:00.000Z\","
                + "\"custom_item_meta\":{\"a\":{\"b\":[1,2,{\"c\":\"}\"}]}}}";
        return "{\"type\":\"init\",\"auctions\":["
                + auction("Biloabi", 48000, "2026-10-27T22:17:55.048Z", KEY) + ","
                + auction("Biloabi", 49000, "2026-10-27T22:17:23.909Z", KEY) + ","
                + auction("Biloabi", 47000, "2026-10-27T22:16:44.444Z", KEY) + ","
                + auction("Le0n478", 59000, "2026-10-27T21:55:42.209Z", KEY) + ","
                + auction("Alt", 40000, "2026-10-27T10:00:00.000Z", KEY) + ","         // zu alt
                + auction("Anderer", 1000, "2026-10-27T22:19:00.000Z", "enchanted_book/multishot:2") + "," // anderes Item
                + nested + "],\"orders\":["
                + order("Hcnnez", 15000, 27, "2026-10-27T22:09:10.897Z", KEY) + ","
                + order("Ericsson199", 100000, 108, "2026-10-27T18:38:20.251Z", KEY) + ","
                + order("Pixelschaf456", 100000, 3, "2026-10-27T16:36:51.520Z", KEY) + ","
                + order("Uralt", 150000, 5, "2026-10-01T00:00:00.000Z", KEY) + ","      // aelter als 7 Tage
                + order("lob674535", 1, 30, "2026-10-27T17:21:08.805Z", KEY) + ","
                + order("Anderer", 999999, 1, "2026-10-27T18:00:00.000Z", "enchanted_book/multishot:2")
                + "]}";
    }

    public static void main(String[] args) throws Exception {
        testJson();
        testParserAndSelect();
        testSse();
        testItemKey();
        testAge();
        testClientWithLocalServer();
        System.out.println(passed + " Tests bestanden, " + failed + " fehlgeschlagen");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testJson() {
        Object o = MiniJson.parse("{\"a\":[1,2.5,-3e2,\"x\\n\\u00e4\\\"\",true,false,null],\"b\":{}}");
        Map<?, ?> m = (Map<?, ?>) o;
        List<?> a = (List<?>) m.get("a");
        eq(1L, a.get(0), "json long");
        eq(2.5, a.get(1), "json double");
        eq(-300.0, a.get(2), "json exp");
        eq("x\nä\"", a.get(3), "json string");
        eq(true, a.get(4), "json true");
        eq(null, a.get(6), "json null");
        boolean threw = false;
        try {
            MiniJson.parse("{\"a\":[1,2");
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        eq(true, threw, "json unvollstaendig wirft");
        threw = false;
        try {
            MiniJson.parse("<html>Cloudflare</html>");
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        eq(true, threw, "json html wirft");
    }

    private static void testParserAndSelect() {
        MarketParser.Snapshot snap = MarketParser.parse(initJson());
        eq(7, snap.auctions().size(), "anzahl auktionen");
        eq(6, snap.orders().size(), "anzahl orders");
        Optional<Quote> ah = MarketSelect.lowestAuction(snap, KEY, NOW, Duration.ofMinutes(60));
        eq(46000L, ah.get().price(), "AH niedrigster (mit verschachteltem Eintrag)");
        // ohne den verschachtelten Eintrag
        Optional<Quote> ah0 = MarketSelect.lowestAuction(snap, KEY, NOW, Duration.ofMinutes(5));
        eq(47000L, ah0.get().price(), "AH niedrigster in 5 Min");
        eq(Instant.parse("2026-10-27T22:16:44.444Z"), ah0.get().seenAt(), "AH Zeitpunkt");
        Optional<Quote> ahAll = MarketSelect.lowestAuction(snap, KEY, NOW, Duration.ZERO);
        eq(40000L, ahAll.get().price(), "AH ohne Zeitlimit nimmt auch alte");
        Optional<Quote> none = MarketSelect.lowestAuction(snap, KEY, NOW.plus(Duration.ofDays(3)), Duration.ofMinutes(60));
        eq(true, none.isEmpty(), "AH alles zu alt");
        Optional<Quote> o = MarketSelect.highestOrder(snap, KEY, NOW, Duration.ofHours(168));
        eq(100000L, o.get().price(), "Order hoechster");
        eq(Instant.parse("2026-10-27T18:38:20.251Z"), o.get().seenAt(), "Order bei Gleichstand der neuere");
        Optional<Quote> oAll = MarketSelect.highestOrder(snap, KEY, NOW, Duration.ZERO);
        eq(150000L, oAll.get().price(), "Order ohne Zeitlimit");
        eq(true, MarketSelect.highestOrder(snap, "diamond_sword/sharpness:5", NOW, Duration.ZERO).isEmpty(),
                "unbekanntes Item");
    }

    private static void testSse() throws Exception {
        String stream = ": hallo\n\nretry: 1000\n\n"
                + "data: {\"type\":\"update\",\"x\":1}\n\n"
                + "data: " + initJson() + "\n\n"
                + "data: {\"type\":\"update\",\"x\":2}\n\n";
        Optional<MarketParser.Snapshot> s = SseReader.readInit(
                new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8)), 1_000_000);
        eq(true, s.isPresent(), "sse init gefunden");
        eq(7, s.get().auctions().size(), "sse auktionen");

        // mehrzeiliges Ereignis
        String multi = "data: {\"type\":\"init\",\r\ndata: \"auctions\":[],\r\ndata: \"orders\":[]}\r\n\r\n";
        s = SseReader.readInit(new ByteArrayInputStream(multi.getBytes(StandardCharsets.UTF_8)), 1_000_000);
        eq(true, s.isPresent(), "sse mehrzeilig");

        // HTML statt Datenstrom
        s = SseReader.readInit(new ByteArrayInputStream("<html>Just a moment...</html>\n".getBytes(StandardCharsets.UTF_8)),
                1_000_000);
        eq(true, s.isEmpty(), "sse html ergibt nichts");

        // Strom ohne init
        s = SseReader.readInit(new ByteArrayInputStream("data: {\"type\":\"update\"}\n\n".getBytes(StandardCharsets.UTF_8)),
                1_000_000);
        eq(true, s.isEmpty(), "sse nur update ergibt nichts");

        // zu grosse Zeile
        boolean threw = false;
        try {
            SseReader.readInit(new ByteArrayInputStream(("data: " + "x".repeat(5000) + "\n").getBytes(StandardCharsets.UTF_8)), 1000);
        } catch (java.io.IOException e) {
            threw = true;
        }
        eq(true, threw, "sse zu lange zeile");
    }

    private static void testItemKey() {
        eq("enchanted_book/multishot:1",
                ItemKeyBuilder.build("minecraft:enchanted_book", List.of("minecraft:multishot=1"), ","), "key buch");
        eq("diamond_sword/sharpness:5,unbreaking:3",
                ItemKeyBuilder.build("minecraft:diamond_sword",
                        List.of("minecraft:unbreaking=3", "minecraft:sharpness=5"), ","), "key mehrere sortiert");
        eq("iron_ingot", ItemKeyBuilder.build("minecraft:iron_ingot", List.of(), ","), "key ohne enchants");
        eq("diamond_axe/unbreaking:1", ItemKeyBuilder.build("minecraft:diamond_axe", List.of("minecraft:unbreaking=1"), ","),
                "key axt");
    }

    private static void testAge() {
        eq("gerade eben", MoneyFormat.age(20_000), "alter sek");
        eq("vor 3 Min", MoneyFormat.age(3 * 60_000L), "alter min");
        eq("vor 2 Std", MoneyFormat.age(125 * 60_000L), "alter std");
        eq("vor 2 Tg", MoneyFormat.age(50L * 3600_000L), "alter tage");
        eq("gerade eben", MoneyFormat.age(-5000), "alter negativ");
    }

    private static void testClientWithLocalServer() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        AtomicInteger mode = new AtomicInteger(0); // 0 = ok (Strom bleibt offen), 1 = 503, 2 = HTML
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/stream/item", ex -> {
            hits.incrementAndGet();
            String rawQuery = ex.getRequestURI().getRawQuery();
            int m = mode.get();
            if (m == 1) {
                ex.sendResponseHeaders(503, -1);
                ex.close();
                return;
            }
            if (m == 2) {
                byte[] html = "<html>Just a moment...</html>".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "text/html");
                ex.sendResponseHeaders(200, html.length);
                ex.getResponseBody().write(html);
                ex.close();
                return;
            }
            if (!"item=enchanted_book%2Fmultishot%3A1".equals(rawQuery)) {
                ex.sendResponseHeaders(404, -1);
                ex.close();
                return;
            }
            ex.getResponseHeaders().add("Content-Type", "text/event-stream");
            ex.sendResponseHeaders(200, 0);
            OutputStream os = ex.getResponseBody();
            os.write(("data: " + initJson() + "\n\n").getBytes(StandardCharsets.UTF_8));
            os.flush();
            try {
                Thread.sleep(30_000); // Strom bleibt offen wie beim echten Server
            } catch (InterruptedException ignored) {
                // Test ist vorbei
            }
            ex.close();
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        }));
        server.start();
        try {
            int port = server.getAddress().getPort();
            Settings s = new Settings();
            s.webBaseUrl = "http://127.0.0.1:" + port + "/api/stream/item?item=";
            long fixedNow = NOW.toEpochMilli();
            MarketClient client = new MarketClient(s, () -> fixedNow);

            long t0 = System.nanoTime();
            MarketClient.Lookup first = client.lookup(KEY);
            eq(true, first.result() == null && first.loading(), "erster Aufruf blockiert nicht, laedt");
            eq(true, (System.nanoTime() - t0) < 200_000_000L, "lookup ist sofort zurueck");

            MarketClient.Lookup l = first;
            long deadline = System.currentTimeMillis() + 8000;
            while (l.result() == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
                l = client.lookup(KEY);
            }
            long took = System.nanoTime() - t0;
            eq(true, l.result() != null, "Ergebnis kam an");
            if (l.result() != null) {
                eq(46000L, l.result().auction().get().price(), "client AH");
                eq(100000L, l.result().order().get().price(), "client Order");
                eq(true, took < 5_000_000_000L, "Ergebnis schnell, obwohl der Strom offen bleibt");
            }
            eq(1, hits.get(), "genau ein Abruf");
            client.lookup(KEY);
            client.lookup(KEY);
            Thread.sleep(200);
            eq(1, hits.get(), "Zwischenspeicher verhindert neue Abrufe");

            // Unbekanntes Item -> 404 -> Fehler, kein Absturz, danach Pause
            MarketClient c2 = new MarketClient(s, () -> fixedNow);
            c2.lookup("nichts/da:9");
            Thread.sleep(1500);
            int hitsAfterMiss = hits.get();
            MarketClient.Lookup miss = c2.lookup("nichts/da:9");
            Thread.sleep(300);
            eq(true, miss.result() == null, "404 ergibt kein Ergebnis");
            eq(hitsAfterMiss, hits.get(), "nach Fehler keine sofortige Wiederholung");

            // 503
            mode.set(1);
            MarketClient c3 = new MarketClient(s, () -> fixedNow);
            c3.lookup(KEY);
            Thread.sleep(1500);
            eq(true, c3.lookup(KEY).result() == null, "503 ergibt kein Ergebnis");

            // HTML statt Daten (z. B. Cloudflare-Schutzseite)
            mode.set(2);
            MarketClient c4 = new MarketClient(s, () -> fixedNow);
            c4.lookup(KEY);
            Thread.sleep(1500);
            eq(true, c4.lookup(KEY).result() == null, "HTML ergibt kein Ergebnis");
        } finally {
            server.stop(0);
        }
    }
}
