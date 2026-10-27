package de.hugoprices.logic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Liest einen Server-Sent-Events-Strom nur so weit, bis der Start-Block ("init") komplett da ist. */
public final class SseReader {
    private SseReader() {
    }

    public static Optional<MarketParser.Snapshot> readInit(InputStream in, int maxChars) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16);
        StringBuilder line = new StringBuilder();
        StringBuilder data = new StringBuilder();
        int ch;
        while ((ch = r.read()) != -1) {
            if (ch != '\n') {
                line.append((char) ch);
                if (line.length() > maxChars) {
                    throw new IOException("Zeile zu lang");
                }
                continue;
            }
            String l = line.toString();
            line.setLength(0);
            if (l.endsWith("\r")) {
                l = l.substring(0, l.length() - 1);
            }
            if (l.isEmpty()) {
                data.setLength(0); // Ende eines Ereignisses
                continue;
            }
            if (!l.startsWith("data:")) {
                continue; // Kommentare, retry:, event: ... ignorieren
            }
            String part = l.substring(5);
            if (part.startsWith(" ")) {
                part = part.substring(1);
            }
            if (data.length() > 0) {
                data.append('\n');
            }
            data.append(part);
            if (data.length() > maxChars) {
                throw new IOException("Ereignis zu gross");
            }
            try {
                Object root = MiniJson.parse(data.toString());
                if (MarketParser.isInit(root)) {
                    return Optional.of(MarketParser.parseSnapshot(root));
                }
                data.setLength(0); // vollständiges, aber anderes Ereignis
            } catch (IllegalArgumentException incompleteOrInvalid) {
                // mehrzeiliges Ereignis, weiterlesen
            }
        }
        return Optional.empty();
    }
}
