package io.github.diegofranciscog.inventory.gs1;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intérprete de cadenas GS1 (R-14). Soporta:
 * <ul>
 *   <li>Cadena de elementos con separador FNC1 (carácter GS, 0x1D), con o sin identificador de simbología
 *       ({@code ]C1}, {@code ]d2}, {@code ]Q3}, {@code ]e0}, {@code ]J1}).</li>
 *   <li>Formato legible con paréntesis: {@code (01)09520000000013(17)261231(10)L01}.</li>
 *   <li>URI GS1 Digital Link: {@code https://dominio/01/09520000000013/10/L01?17=261231}.</li>
 * </ul>
 * Longitudes de AI según la tabla oficial de GS1 (ref.gs1.org/ai).
 */
public final class Gs1Parser {

    private static final char GROUP_SEPARATOR = '\u001D';
    private static final Pattern SYMBOLOGY_ID = Pattern.compile("^\\][A-Za-z][0-9]");
    private static final Pattern BRACKETED = Pattern.compile("\\((\\d{2,4})\\)([^(]*)");

    /** AI de longitud fija: AI → longitud del dato. */
    private static final Map<String, Integer> FIXED_LENGTH = Map.ofEntries(
            Map.entry("00", 18), Map.entry("01", 14), Map.entry("02", 14),
            Map.entry("11", 6), Map.entry("12", 6), Map.entry("13", 6), Map.entry("15", 6),
            Map.entry("16", 6), Map.entry("17", 6), Map.entry("20", 2));

    /** AI de longitud variable: AI → longitud máxima (terminan con FNC1 o al final). */
    private static final Map<String, Integer> VARIABLE_LENGTH = Map.ofEntries(
            Map.entry("10", 20), Map.entry("21", 20), Map.entry("22", 20), Map.entry("30", 8),
            Map.entry("37", 8), Map.entry("240", 30), Map.entry("241", 30), Map.entry("400", 30));

    private Gs1Parser() {
    }

    /** Devuelve los datos si la entrada es una cadena GS1 reconocible; vacío si no lo es. */
    public static Optional<Gs1Data> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String input = raw.strip();
        Map<String, String> elements;
        if (input.regionMatches(true, 0, "http://", 0, 7) || input.regionMatches(true, 0, "https://", 0, 8)) {
            elements = parseDigitalLink(input);
        } else if (input.startsWith("(")) {
            elements = parseBracketed(input);
        } else {
            elements = parseElementString(input);
        }
        if (elements.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toData(elements));
    }

    static Map<String, String> parseElementString(String input) {
        String data = input;
        Matcher symbology = SYMBOLOGY_ID.matcher(data);
        boolean hasSymbologyId = symbology.find();
        if (hasSymbologyId) {
            data = data.substring(3);
        }
        if (!data.isEmpty() && data.charAt(0) == GROUP_SEPARATOR) {
            data = data.substring(1);
        }
        // Sin identificador de simbología ni separadores, solo aceptamos cadenas que empiecen con (01) de 16 caracteres o más;
        // así un SKU o un GTIN suelto no se confunden con una cadena GS1.
        if (!hasSymbologyId && !(data.startsWith("01") && data.length() >= 16) && data.indexOf(GROUP_SEPARATOR) < 0) {
            return Map.of();
        }
        Map<String, String> elements = new LinkedHashMap<>();
        int position = 0;
        while (position < data.length()) {
            String ai = matchAi(data, position);
            if (ai == null) {
                return elements.containsKey("01") ? elements : Map.of();
            }
            position += ai.length();
            Integer fixed = FIXED_LENGTH.get(ai);
            String value;
            if (fixed != null) {
                if (position + fixed > data.length()) {
                    return Map.of();
                }
                value = data.substring(position, position + fixed);
                position += fixed;
                if (position < data.length() && data.charAt(position) == GROUP_SEPARATOR) {
                    position++;
                }
            } else {
                int max = VARIABLE_LENGTH.get(ai);
                int end = data.indexOf(GROUP_SEPARATOR, position);
                int stop = end < 0 ? data.length() : end;
                if (stop - position > max || stop == position) {
                    return Map.of();
                }
                value = data.substring(position, stop);
                position = end < 0 ? data.length() : end + 1;
            }
            elements.put(ai, value);
        }
        return elements;
    }

    private static String matchAi(String data, int position) {
        for (int length = 2; length <= 3; length++) {
            if (position + length > data.length()) {
                return null;
            }
            String candidate = data.substring(position, position + length);
            if (FIXED_LENGTH.containsKey(candidate) || VARIABLE_LENGTH.containsKey(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    static Map<String, String> parseBracketed(String input) {
        Map<String, String> elements = new LinkedHashMap<>();
        Matcher matcher = BRACKETED.matcher(input);
        int consumed = 0;
        while (matcher.find()) {
            if (matcher.start() != consumed) {
                return Map.of();
            }
            String ai = matcher.group(1);
            String value = matcher.group(2).strip();
            if (!isValidLength(ai, value)) {
                return Map.of();
            }
            elements.put(ai, value);
            consumed = matcher.end();
        }
        return consumed == input.length() ? elements : Map.of();
    }

    static Map<String, String> parseDigitalLink(String input) {
        URI uri;
        try {
            uri = new URI(input);
        } catch (URISyntaxException e) {
            return Map.of();
        }
        Map<String, String> elements = new LinkedHashMap<>();
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String[] segments = path.split("/");
        int start = -1;
        for (int i = 0; i < segments.length - 1; i++) {
            if ("01".equals(segments[i]) || "gtin".equalsIgnoreCase(segments[i])) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return Map.of();
        }
        for (int i = start; i + 1 < segments.length; i += 2) {
            String ai = "gtin".equalsIgnoreCase(segments[i]) ? "01" : segments[i];
            String value = decode(segments[i + 1]);
            if (!isValidLength(ai, value)) {
                return Map.of();
            }
            elements.put(ai, value);
        }
        String query = uri.getRawQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    String ai = pair.substring(0, eq);
                    String value = decode(pair.substring(eq + 1));
                    if (ai.chars().allMatch(Character::isDigit) && isValidLength(ai, value)) {
                        elements.put(ai, value);
                    }
                }
            }
        }
        return elements;
    }

    private static boolean isValidLength(String ai, String value) {
        Integer fixed = FIXED_LENGTH.get(ai);
        if (fixed != null) {
            return value.length() == fixed;
        }
        Integer max = VARIABLE_LENGTH.get(ai);
        return max != null && !value.isEmpty() && value.length() <= max;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static Gs1Data toData(Map<String, String> elements) {
        Gtin gtin = Optional.ofNullable(elements.get("01")).flatMap(Gtin::parse).orElse(null);
        Integer count = null;
        if (elements.containsKey("37")) {
            try {
                count = Integer.valueOf(elements.get("37"));
            } catch (NumberFormatException ignored) {
                count = null;
            }
        }
        return new Gs1Data(
                gtin,
                elements.get("10"),
                parseDate(elements.get("17")),
                parseDate(elements.get("11")),
                parseDate(elements.get("15")),
                elements.get("21"),
                count,
                Map.copyOf(elements));
    }

    /**
     * Fecha GS1 YYMMDD. Siglo según la regla de GS1 (ventana de −49 a +50 años respecto al año actual).
     * Día {@code 00}: fin de mes (supuesto S-04).
     */
    static LocalDate parseDate(String yymmdd) {
        if (yymmdd == null || !yymmdd.matches("\\d{6}")) {
            return null;
        }
        int yy = Integer.parseInt(yymmdd.substring(0, 2));
        int month = Integer.parseInt(yymmdd.substring(2, 4));
        int day = Integer.parseInt(yymmdd.substring(4, 6));
        int currentYear = LocalDate.now().getYear();
        int year = (currentYear / 100) * 100 + yy;
        if (year - currentYear > 50) {
            year -= 100;
        } else if (year - currentYear < -49) {
            year += 100;
        }
        try {
            YearMonth yearMonth = YearMonth.of(year, month);
            return day == 0 ? yearMonth.atEndOfMonth() : yearMonth.atDay(day);
        } catch (DateTimeException e) {
            return null;
        }
    }
}
