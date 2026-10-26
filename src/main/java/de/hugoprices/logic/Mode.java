package de.hugoprices.logic;

/** Welche Art Menü gerade geöffnet ist. */
public enum Mode {
    /** Auktionshaus: wir merken uns den NIEDRIGSTEN Preis. */
    AH,
    /** Order: wir merken uns den HÖCHSTEN Preis. */
    ORDER
}
