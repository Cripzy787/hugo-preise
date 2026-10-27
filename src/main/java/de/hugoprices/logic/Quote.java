package de.hugoprices.logic;

import java.time.Instant;

/** Ein gefundener Preis (pro Stück) und der Zeitpunkt, zu dem er auf dem Markt gesehen wurde. */
public record Quote(long price, Instant seenAt) {
}
