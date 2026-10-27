package de.hugoprices.logic;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Wählt aus den Marktdaten den niedrigsten Auktionspreis und den höchsten Order-Preis. */
public final class MarketSelect {
    private MarketSelect() {
    }

    /** Niedrigster Stückpreis aller Angebote dieses Items, die nicht älter als maxAge sind. */
    public static Optional<Quote> lowestAuction(MarketParser.Snapshot snap, String itemKey, Instant now, Duration maxAge) {
        Quote best = null;
        for (MarketParser.Entry e : snap.auctions()) {
            if (!usable(e, itemKey, now, maxAge)) {
                continue;
            }
            if (best == null || e.priceUnit() < best.price()
                    || (e.priceUnit() == best.price() && e.observedAt().isAfter(best.seenAt()))) {
                best = new Quote(e.priceUnit(), e.observedAt());
            }
        }
        return Optional.ofNullable(best);
    }

    /** Höchster Stückpreis aller Orders dieses Items, die nicht älter als maxAge sind. */
    public static Optional<Quote> highestOrder(MarketParser.Snapshot snap, String itemKey, Instant now, Duration maxAge) {
        Quote best = null;
        for (MarketParser.Entry e : snap.orders()) {
            if (!usable(e, itemKey, now, maxAge)) {
                continue;
            }
            if (best == null || e.priceUnit() > best.price()
                    || (e.priceUnit() == best.price() && e.observedAt().isAfter(best.seenAt()))) {
                best = new Quote(e.priceUnit(), e.observedAt());
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean usable(MarketParser.Entry e, String itemKey, Instant now, Duration maxAge) {
        if (!e.itemKey().equals(itemKey) || e.priceUnit() <= 0) {
            return false;
        }
        if (maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
            return true; // 0 = kein Zeitlimit
        }
        return Duration.between(e.observedAt(), now).compareTo(maxAge) <= 0;
    }
}
