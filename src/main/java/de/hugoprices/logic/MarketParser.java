package de.hugoprices.logic;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Liest den ersten Block ("init") des Marktdaten-Stroms von hugosmp-market.net. */
public final class MarketParser {
    private MarketParser() {
    }

    /** Ein Angebot oder eine Order. priceUnit ist der Preis pro Stück. */
    public record Entry(String itemKey, long priceUnit, int quantity, Instant observedAt) {
    }

    public record Snapshot(List<Entry> auctions, List<Entry> orders) {
    }

    /** Ist das der Start-Block mit den Listen? Späteren Update-Blöcken fehlt "init". */
    public static boolean isInit(Object root) {
        if (!(root instanceof Map<?, ?> m)) {
            return false;
        }
        Object type = m.get("type");
        boolean hasLists = m.get("auctions") instanceof List<?> || m.get("orders") instanceof List<?>;
        if (type != null) {
            return "init".equals(type) && hasLists;
        }
        return hasLists;
    }

    public static Snapshot parse(String json) {
        return parseSnapshot(MiniJson.parse(json));
    }

    public static Snapshot parseSnapshot(Object root) {
        if (!(root instanceof Map<?, ?> m)) {
            throw new IllegalArgumentException("Objekt erwartet");
        }
        return new Snapshot(entries(m.get("auctions")), entries(m.get("orders")));
    }

    private static List<Entry> entries(Object listObj) {
        List<Entry> out = new ArrayList<>();
        if (!(listObj instanceof List<?> list)) {
            return out;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> e)) {
                continue;
            }
            Object key = e.get("item_key");
            Object price = e.get("price_unit");
            Object seen = e.get("observed_at");
            if (!(key instanceof String k) || !(price instanceof Number p) || !(seen instanceof String s)) {
                continue;
            }
            Instant at;
            try {
                at = Instant.parse(s);
            } catch (RuntimeException ex) {
                continue;
            }
            int qty = e.get("quantity") instanceof Number q ? q.intValue() : 1;
            out.add(new Entry(k, Math.round(p.doubleValue()), qty, at));
        }
        return out;
    }
}
