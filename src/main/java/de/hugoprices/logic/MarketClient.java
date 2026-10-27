package de.hugoprices.logic;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Holt Live-Preise von hugosmp-market.net, ohne das Spiel zu bremsen:
 * Abrufe laufen im Hintergrund, nacheinander, mit Zwischenspeicher und Pause nach Fehlern.
 * lookup() blockiert nie.
 */
public final class MarketClient {
    private static final System.Logger LOG = System.getLogger("hugoprices");
    private static final String USER_AGENT = "CripzysHugoPreise/1.0 (Minecraft Fabric client mod)";
    private static final int MAX_CHARS = 8_000_000;
    private static final int MAX_PENDING = 8;
    private static final long MIN_GAP_MILLIS = 750;
    private static final long ITEM_RETRY_MILLIS = 120_000;
    private static final long GLOBAL_PAUSE_MILLIS = 300_000;

    public record Result(Optional<Quote> auction, Optional<Quote> order, long fetchedAtMillis) {
    }

    /**
     * result kann null sein (noch nichts bekannt). loading = ein Abruf läuft gerade.
     * error = Grund des letzten fehlgeschlagenen Abrufs (null, wenn zuletzt alles gut ging).
     */
    public record Lookup(Result result, boolean loading, String error) {
    }

    private static final class State {
        Result result;
        boolean inFlight;
        long retryAfterMillis;
        String lastError;
    }

    private final Settings settings;
    private final LongSupplier clock;
    private final HttpClient http;
    private final Map<String, State> states = new HashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> daemon(r, "hugoprices-net"));
    private final ScheduledExecutorService killer =
            Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "hugoprices-timeout"));
    private int pending = 0;
    private int consecutiveFailures = 0;
    private long globalRetryAfterMillis = 0;
    private long lastRequestMillis = 0;

    public MarketClient(Settings settings) {
        this(settings, System::currentTimeMillis);
    }

    public MarketClient(Settings settings, LongSupplier clock) {
        this.settings = settings;
        this.clock = clock;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    /** Gibt sofort zurück, was bekannt ist, und stößt bei Bedarf im Hintergrund einen neuen Abruf an. */
    public synchronized Lookup lookup(String key) {
        long now = clock.getAsLong();
        if (states.size() > 2000) {
            states.values().removeIf(s -> !s.inFlight);
        }
        State st = states.computeIfAbsent(key, k -> new State());
        long ttl = settings.webCacheSeconds * 1000L;
        boolean fresh = st.result != null && now - st.result.fetchedAtMillis() <= ttl;
        if (!fresh && !st.inFlight && now >= st.retryAfterMillis && now >= globalRetryAfterMillis
                && pending < MAX_PENDING) {
            st.inFlight = true;
            pending++;
            worker.execute(() -> run(key));
        }
        String error = st.lastError;
        if (error == null && st.result == null && now < globalRetryAfterMillis) {
            error = "Pause nach mehreren Fehlern";
        }
        return new Lookup(st.result, st.inFlight, error);
    }

    private void run(String key) {
        Result result = null;
        Exception error = null;
        try {
            throttle();
            result = fetch(key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            error = e;
        } catch (Exception e) {
            error = e;
        }
        synchronized (this) {
            State st = states.computeIfAbsent(key, k -> new State());
            st.inFlight = false;
            pending--;
            long now = clock.getAsLong();
            if (result != null) {
                st.result = result;
                st.retryAfterMillis = 0;
                st.lastError = null;
                consecutiveFailures = 0;
            } else {
                st.lastError = describe(error);
                st.retryAfterMillis = now + ITEM_RETRY_MILLIS;
                consecutiveFailures++;
                if (consecutiveFailures >= 3) {
                    globalRetryAfterMillis = now + GLOBAL_PAUSE_MILLIS;
                    consecutiveFailures = 0;
                }
                LOG.log(System.Logger.Level.WARNING,
                        "Marktabruf fehlgeschlagen fuer " + key + ": " + error);
            }
        }
    }

    /** Kurze, lesbare Fehlerbeschreibung für den Tooltip. */
    static String describe(Exception e) {
        if (e == null) {
            return "unbekannter Fehler";
        }
        String m = e.getMessage();
        if (e instanceof java.net.http.HttpTimeoutException || e instanceof java.net.http.HttpConnectTimeoutException) {
            return "Zeitueberschreitung";
        }
        if (e instanceof java.net.ConnectException || e instanceof java.nio.channels.UnresolvedAddressException) {
            return "keine Verbindung";
        }
        if (m == null || m.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return m.length() > 60 ? m.substring(0, 60) : m;
    }

    private void throttle() throws InterruptedException {
        long wait;
        synchronized (this) {
            long now = clock.getAsLong();
            wait = lastRequestMillis + MIN_GAP_MILLIS - now;
            lastRequestMillis = Math.max(now, lastRequestMillis + MIN_GAP_MILLIS);
        }
        if (wait > 0) {
            Thread.sleep(wait);
        }
    }

    private Result fetch(String key) throws IOException, InterruptedException {
        String url = settings.webBaseUrl + URLEncoder.encode(key, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "text/event-stream")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        InputStream body = resp.body();
        if (resp.statusCode() != 200) {
            closeQuietly(body);
            throw new IOException("HTTP " + resp.statusCode());
        }
        // Der Strom bleibt offen; nach 20 s wird er hart geschlossen, falls nichts ankommt.
        ScheduledFuture<?> guard = killer.schedule(() -> closeQuietly(body), 20, TimeUnit.SECONDS);
        try (body) {
            Optional<MarketParser.Snapshot> snap = SseReader.readInit(body, MAX_CHARS);
            if (snap.isEmpty()) {
                throw new IOException("keine Daten erhalten");
            }
            Instant now = Instant.ofEpochMilli(clock.getAsLong());
            Optional<Quote> auction = MarketSelect.lowestAuction(
                    snap.get(), key, now, Duration.ofMinutes(settings.ahMaxAgeMinutes));
            Optional<Quote> order = MarketSelect.highestOrder(
                    snap.get(), key, now, Duration.ofHours(settings.orderMaxAgeHours));
            return new Result(auction, order, clock.getAsLong());
        } finally {
            guard.cancel(false);
        }
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // egal
        }
    }
}
