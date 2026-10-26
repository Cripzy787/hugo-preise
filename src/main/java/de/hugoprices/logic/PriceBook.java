package de.hugoprices.logic;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Merkt sich pro Item den günstigsten AH-Preis und den höchsten Order-Preis, den du zuletzt gesehen hast.
 * Ein Wert gilt nur, solange er nicht älter als die eingestellte Zeit ist.
 */
public final class PriceBook {
    public record Entry(long price, long seenAtMillis) {
    }

    private final Map<String, Entry> ah = new HashMap<>();
    private final Map<String, Entry> order = new HashMap<>();

    private Map<String, Entry> map(Mode mode) {
        return mode == Mode.AH ? ah : order;
    }

    public synchronized void observe(Mode mode, String key, long price, long nowMillis, long maxAgeMillis) {
        Map<String, Entry> m = map(mode);
        Entry cur = m.get(key);
        boolean expired = cur != null && nowMillis - cur.seenAtMillis() > maxAgeMillis;
        boolean better = cur == null || expired
                || (mode == Mode.AH ? price < cur.price() : price > cur.price());
        if (better || price == cur.price()) {
            m.put(key, new Entry(price, nowMillis));
        }
    }

    public synchronized Optional<Entry> get(Mode mode, String key, long nowMillis, long maxAgeMillis) {
        Entry e = map(mode).get(key);
        if (e == null || nowMillis - e.seenAtMillis() > maxAgeMillis) {
            return Optional.empty();
        }
        return Optional.of(e);
    }

    public synchronized void clear() {
        ah.clear();
        order.clear();
    }

    public synchronized int size() {
        return ah.size() + order.size();
    }
}
