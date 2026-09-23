package io.github.diegofranciscog.inventory.gs1;

import java.time.LocalDate;
import java.util.Map;

/**
 * Datos extraídos de un código GS1 (GS1-128, GS1 DataMatrix, GS1 QR o GS1 Digital Link).
 *
 * @param gtin           AI (01) normalizado a 14 dígitos
 * @param lotNumber      AI (10)
 * @param expiryDate     AI (17)
 * @param productionDate AI (11)
 * @param bestBeforeDate AI (15)
 * @param serialNumber   AI (21)
 * @param count          AI (37)
 * @param elements       todos los AI leídos, en crudo
 */
public record Gs1Data(
        Gtin gtin,
        String lotNumber,
        LocalDate expiryDate,
        LocalDate productionDate,
        LocalDate bestBeforeDate,
        String serialNumber,
        Integer count,
        Map<String, String> elements) {

    public boolean hasGtin() {
        return gtin != null;
    }
}
