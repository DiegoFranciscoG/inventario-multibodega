package io.github.diegofranciscog.inventory.gs1;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Gs1ParserTest {

    private static final char GS = '\u001D';
    private static final String GTIN = "09520000000011";

    @Test
    void parsesElementStringWithFnc1SeparatorAfterVariableLengthLot() {
        String scanned = "]C1" + "01" + GTIN + "10" + "L-2026A" + GS + "17" + "271231" + "21" + "SN001";

        Gs1Data data = Gs1Parser.parse(scanned).orElseThrow();

        assertThat(data.gtin().value()).isEqualTo(GTIN);
        assertThat(data.lotNumber()).isEqualTo("L-2026A");
        assertThat(data.expiryDate()).isEqualTo(LocalDate.of(2027, 12, 31));
        assertThat(data.serialNumber()).isEqualTo("SN001");
    }

    @Test
    void parsesGs1QrWithoutSymbologyIdentifierWhenItStartsWithAi01() {
        Gs1Data data = Gs1Parser.parse("01" + GTIN + "17" + "280630" + "10" + "LOTE7").orElseThrow();

        assertThat(data.hasGtin()).isTrue();
        assertThat(data.lotNumber()).isEqualTo("LOTE7");
        assertThat(data.expiryDate()).isEqualTo(LocalDate.of(2028, 6, 30));
    }

    @Test
    void parsesHumanReadableBracketFormat() {
        Gs1Data data = Gs1Parser.parse("(01)" + GTIN + "(11)260101(17)270101(10)ABC/12(37)24").orElseThrow();

        assertThat(data.productionDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(data.expiryDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(data.lotNumber()).isEqualTo("ABC/12");
        assertThat(data.count()).isEqualTo(24);
    }

    @Test
    void parsesGs1DigitalLinkWithExpiryInQueryString() {
        Gs1Data data = Gs1Parser.parse("https://id.example.com/01/" + GTIN + "/10/LOT%2F9?17=270531").orElseThrow();

        assertThat(data.gtin().value()).isEqualTo(GTIN);
        assertThat(data.lotNumber()).isEqualTo("LOT/9");
        assertThat(data.expiryDate()).isEqualTo(LocalDate.of(2027, 5, 31));
    }

    @Test
    void dayZeroMeansEndOfMonth() {
        assertThat(Gs1Parser.parseDate("270200")).isEqualTo(YearMonth.of(2027, 2).atEndOfMonth());
        assertThat(Gs1Parser.parseDate("271399")).isNull();
        assertThat(Gs1Parser.parseDate("27AB01")).isNull();
    }

    @Test
    void appliesGs1CenturyWindow() {
        int currentYear = LocalDate.now().getYear();
        int farPast = (currentYear - 60) % 100;
        assertThat(Gs1Parser.parseDate("%02d0101".formatted(farPast)).getYear()).isGreaterThan(currentYear);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SKU-123", "7501031311309", "(01)123", "https://example.com/products/1", "   ",
            "]C1" + "01" + "0952000000001", "(99)abc"})
    void ignoresInputsThatAreNotGs1Strings(String input) {
        assertThat(Gs1Parser.parse(input).filter(Gs1Data::hasGtin)).isEmpty();
    }

    @Test
    void rejectsVariableLengthFieldLongerThanAllowed() {
        String tooLongLot = "L".repeat(21);
        assertThat(Gs1Parser.parse("]d2" + "01" + GTIN + "10" + tooLongLot)).isEmpty();
    }

    @Test
    void keepsAllElementsForTraceability() {
        Gs1Data data = Gs1Parser.parse("]Q3" + "01" + GTIN + "15" + "270101").orElseThrow();
        assertThat(data.elements()).containsEntry("01", GTIN).containsEntry("15", "270101");
        assertThat(data.bestBeforeDate()).isEqualTo(LocalDate.of(2027, 1, 1));
    }
}
