package io.github.diegofranciscog.inventory.gs1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class GtinTest {

    @Test
    void calculatesCheckDigitLikeTheOfficialGs1Example() {
        // Ejemplo oficial de GS1 US (GTIN-12 61414121022): impares 18 × 3 = 54, pares 6, suma 60 → dígito 0
        assertThat(Gtin.checkDigit("61414121022")).isEqualTo(0);
        assertThat(Gtin.isValid("614141210220")).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "96385074, 00000096385074",
            "614141210220, 00614141210220",
            "9520000000011, 09520000000011",
            "19520000000018, 19520000000018"})
    void normalizesEveryLengthTo14Digits(String raw, String expected) {
        assertThat(Gtin.of(raw).value()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"9520000000018", "123", "95200000000111", "abcdefghijklm", "", "  "})
    void rejectsWrongLengthCharactersOrCheckDigit(String raw) {
        assertThat(Gtin.parse(raw)).isEmpty();
    }

    @Test
    void rejectsNull() {
        assertThat(Gtin.parse(null)).isEmpty();
        assertThatThrownBy(() -> Gtin.of("123")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gtin13AndItsPadded14DigitFormAreTheSameProduct() {
        assertThat(Gtin.of("9520000000011")).isEqualTo(Gtin.of("09520000000011"));
        assertThat(Gtin.of("9520000000011").hashCode()).isEqualTo(Gtin.of("09520000000011").hashCode());
    }

    @Test
    void shortFormRemovesPadding() {
        assertThat(Gtin.of("9520000000011").toShortForm()).isEqualTo("9520000000011");
        assertThat(Gtin.of("96385074").toShortForm()).isEqualTo("96385074");
        assertThat(Gtin.of("614141210220").toShortForm()).isEqualTo("614141210220");
        assertThat(Gtin.of("19520000000018").toShortForm()).isEqualTo("19520000000018");
        assertThat(Gtin.of("19520000000018").toString()).isEqualTo("19520000000018");
    }
}
