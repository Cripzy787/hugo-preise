package de.hugoprices.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kleiner JSON-Leser ohne Abhängigkeiten.
 * Ergebnis: Map (Objekt), List (Array), String, Long oder Double (Zahl), Boolean oder null.
 * Bei ungültigem JSON wird eine IllegalArgumentException geworfen.
 */
public final class MiniJson {
    private final String s;
    private int i;

    private MiniJson(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("kein Text");
        }
        MiniJson p = new MiniJson(text);
        p.skipWs();
        Object v = p.value(0);
        p.skipWs();
        if (p.i != p.s.length()) {
            throw p.error("Unerwartetes Zeichen am Ende");
        }
        return v;
    }

    private IllegalArgumentException error(String msg) {
        return new IllegalArgumentException(msg + " (Position " + i + ")");
    }

    private void skipWs() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }

    private Object value(int depth) {
        if (depth > 64) {
            throw error("zu tief verschachtelt");
        }
        if (i >= s.length()) {
            throw error("unerwartetes Ende");
        }
        char c = s.charAt(i);
        switch (c) {
            case '{':
                return object(depth);
            case '[':
                return array(depth);
            case '"':
                return string();
            case 't':
                literal("true");
                return Boolean.TRUE;
            case 'f':
                literal("false");
                return Boolean.FALSE;
            case 'n':
                literal("null");
                return null;
            default:
                return number();
        }
    }

    private void literal(String word) {
        if (!s.startsWith(word, i)) {
            throw error("'" + word + "' erwartet");
        }
        i += word.length();
    }

    private Map<String, Object> object(int depth) {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; // {
        skipWs();
        if (i < s.length() && s.charAt(i) == '}') {
            i++;
            return m;
        }
        while (true) {
            skipWs();
            if (i >= s.length() || s.charAt(i) != '"') {
                throw error("Schlüssel erwartet");
            }
            String key = string();
            skipWs();
            if (i >= s.length() || s.charAt(i) != ':') {
                throw error("':' erwartet");
            }
            i++;
            skipWs();
            m.put(key, value(depth + 1));
            skipWs();
            if (i >= s.length()) {
                throw error("unerwartetes Ende");
            }
            char c = s.charAt(i++);
            if (c == '}') {
                return m;
            }
            if (c != ',') {
                throw error("',' oder '}' erwartet");
            }
        }
    }

    private List<Object> array(int depth) {
        List<Object> list = new ArrayList<>();
        i++; // [
        skipWs();
        if (i < s.length() && s.charAt(i) == ']') {
            i++;
            return list;
        }
        while (true) {
            skipWs();
            list.add(value(depth + 1));
            skipWs();
            if (i >= s.length()) {
                throw error("unerwartetes Ende");
            }
            char c = s.charAt(i++);
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw error("',' oder ']' erwartet");
            }
        }
    }

    private String string() {
        i++; // "
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (i >= s.length()) {
                throw error("Text nicht beendet");
            }
            char c = s.charAt(i++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (i >= s.length()) {
                throw error("Text nicht beendet");
            }
            char e = s.charAt(i++);
            switch (e) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (i + 4 > s.length()) {
                        throw error("ungültiges \\u");
                    }
                    try {
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw error("ungültiges \\u");
                    }
                    i += 4;
                }
                default -> throw error("ungültiges Escape");
            }
        }
    }

    private Object number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        if (start == i) {
            throw error("Wert erwartet");
        }
        String t = s.substring(start, i);
        try {
            if (t.indexOf('.') >= 0 || t.indexOf('e') >= 0 || t.indexOf('E') >= 0) {
                return Double.parseDouble(t);
            }
            try {
                return Long.parseLong(t);
            } catch (NumberFormatException overflow) {
                return Double.parseDouble(t);
            }
        } catch (NumberFormatException ex) {
            throw error("ungültige Zahl");
        }
    }
}
