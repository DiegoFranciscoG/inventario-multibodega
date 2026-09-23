package io.github.diegofranciscog.inventory.gs1;

import java.util.Optional;

/**
 * Global Trade Item Number (GS1). Acepta GTIN-8, GTIN-12, GTIN-13 y GTIN-14 y los normaliza a 14 dígitos
 * con ceros a la izquierda (R-11). El dígito verificador sigue el algoritmo módulo 10 de GS1: desde la derecha
 * (sin contar el verificador) se multiplica por 3 y 1 alternadamente.
 */
public final class Gtin {

    private final String value;

    private Gtin(String value) {
        this.value = value;
    }

    /** Crea un GTIN validado o lanza {@link IllegalArgumentException}. */
    public static Gtin of(String raw) {
        return parse(raw).orElseThrow(() -> new IllegalArgumentException("GTIN inválido: " + raw));
    }

    public static Optional<Gtin> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String digits = raw.strip();
        int length = digits.length();
        if (!(length == 8 || length == 12 || length == 13 || length == 14) || !digits.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        if (!hasValidCheckDigit(digits)) {
            return Optional.empty();
        }
        return Optional.of(new Gtin("0".repeat(14 - length) + digits));
    }

    public static boolean isValid(String raw) {
        return parse(raw).isPresent();
    }

    /** Calcula el dígito verificador para un número sin él (7, 11, 12 o 13 dígitos). */
    public static int checkDigit(String withoutCheckDigit) {
        int sum = 0;
        boolean timesThree = true;
        for (int i = withoutCheckDigit.length() - 1; i >= 0; i--) {
            int digit = withoutCheckDigit.charAt(i) - '0';
            sum += timesThree ? digit * 3 : digit;
            timesThree = !timesThree;
        }
        return (10 - (sum % 10)) % 10;
    }

    private static boolean hasValidCheckDigit(String digits) {
        int expected = checkDigit(digits.substring(0, digits.length() - 1));
        return expected == digits.charAt(digits.length() - 1) - '0';
    }

    /** Valor normalizado de 14 dígitos (el que se guarda en la base de datos). */
    public String value() {
        return value;
    }

    /** Representación corta: quita los ceros de relleno hasta llegar a 13, 12 u 8 dígitos. */
    public String toShortForm() {
        if (value.startsWith("000000")) {
            return value.substring(6);
        }
        if (value.startsWith("00")) {
            return value.substring(2);
        }
        if (value.startsWith("0")) {
            return value.substring(1);
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Gtin gtin && gtin.value.equals(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
