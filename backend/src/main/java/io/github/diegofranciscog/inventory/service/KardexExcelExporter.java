package io.github.diegofranciscog.inventory.service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import io.github.diegofranciscog.inventory.config.AppProperties;
import io.github.diegofranciscog.inventory.entity.Direction;
import io.github.diegofranciscog.inventory.entity.MovementLine;
import io.github.diegofranciscog.inventory.entity.Product;
import io.github.diegofranciscog.inventory.entity.Warehouse;
import io.github.diegofranciscog.inventory.repository.MovementLineRepository;

/**
 * Genera el kárdex en Excel con Apache POI en modo streaming (SXSSF: solo 100 filas en memoria). Los textos se escriben
 * como celdas de texto, nunca como fórmulas, así un dato que empiece con "=" no se ejecuta (inyección de fórmulas).
 */
@Component
public class KardexExcelExporter {

    static final String[] COLUMNS = {
            "Fecha", "Movimiento", "Tipo", "Documento", "Bodega", "Ubicación", "Lote",
            "Entrada cant.", "Entrada c.u.", "Entrada total",
            "Salida cant.", "Salida c.u.", "Salida total",
            "Saldo cant.", "Costo promedio", "Saldo valor", "Saldo bodega"};

    private static final int WINDOW_SIZE = 100;
    private static final int HEADER_ROW = 6;

    private final AppProperties properties;

    public KardexExcelExporter(AppProperties properties) {
        this.properties = properties;
    }

    public void write(Header header, List<MovementLine> lines, MovementLineRepository.Totals totals, OutputStream output)
            throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(WINDOW_SIZE)) {
            Styles styles = new Styles(workbook);
            SXSSFSheet sheet = workbook.createSheet("Kardex");
            writeTitle(sheet, styles, header);
            writeColumnHeaders(sheet, styles);
            int rowIndex = HEADER_ROW + 1;
            for (MovementLine line : lines) {
                writeLine(sheet.createRow(rowIndex++), styles, line);
            }
            writeTotals(sheet.createRow(rowIndex), styles, totals);
            sheet.createFreezePane(0, HEADER_ROW + 1);
            sheet.setAutoFilter(new CellRangeAddress(HEADER_ROW, Math.max(HEADER_ROW, rowIndex - 1), 0, COLUMNS.length - 1));
            for (int column = 0; column < COLUMNS.length; column++) {
                sheet.setColumnWidth(column, column == 0 ? 20 * 256 : 15 * 256);
            }
            workbook.write(output);
        }
    }

    private void writeTitle(SXSSFSheet sheet, Styles styles, Header header) {
        text(sheet.createRow(0), 0, "Kárdex valorizado — " + header.product().getSku() + " · " + header.product().getName(),
                styles.title);
        Row row = sheet.createRow(1);
        text(row, 0, "Bodega:", styles.bold);
        text(row, 1, header.warehouse() == null ? "Todas" : header.warehouse().getCode() + " · " + header.warehouse().getName(), null);
        row = sheet.createRow(2);
        text(row, 0, "Período:", styles.bold);
        text(row, 1, (header.from() == null ? "inicio" : header.from().toString()) + " a "
                + (header.to() == null ? "hoy" : header.to().toString()), null);
        row = sheet.createRow(3);
        text(row, 0, "Método:", styles.bold);
        text(row, 1, header.costMethod(), null);
        row = sheet.createRow(4);
        text(row, 0, "Unidad:", styles.bold);
        text(row, 1, header.product().getBaseUom().getCode() + " · " + header.product().getBaseUom().getName(), null);
    }

    private void writeColumnHeaders(SXSSFSheet sheet, Styles styles) {
        Row row = sheet.createRow(HEADER_ROW);
        for (int column = 0; column < COLUMNS.length; column++) {
            text(row, column, COLUMNS[column], styles.header);
        }
    }

    private void writeLine(Row row, Styles styles, MovementLine line) {
        LocalDateTime occurred = LocalDateTime.ofInstant(line.getOccurredAt(), properties.inventory().timeZone());
        Cell date = row.createCell(0);
        date.setCellValue(occurred);
        date.setCellStyle(styles.dateTime);
        text(row, 1, line.getMovement().getNumber(), null);
        text(row, 2, line.getMovement().getType().name(), null);
        text(row, 3, line.getMovement().getReferenceType().getCode() + " " + line.getMovement().getReferenceNumber(), null);
        text(row, 4, line.getWarehouse().getCode(), null);
        text(row, 5, line.getLocation().getCode(), null);
        text(row, 6, line.getLot() == null ? "" : line.getLot().getLotNumber(), null);
        int offset = line.getDirection() == Direction.IN ? 7 : 10;
        number(row, offset, line.getQuantity(), styles.quantity);
        number(row, offset + 1, line.getUnitCost(), styles.cost);
        number(row, offset + 2, line.getTotalCost(), styles.money);
        number(row, 13, line.getBalanceQuantity(), styles.quantity);
        number(row, 14, line.getAverageCost(), styles.cost);
        number(row, 15, line.getBalanceValue(), styles.money);
        number(row, 16, line.getWarehouseBalanceQuantity(), styles.quantity);
    }

    private void writeTotals(Row row, Styles styles, MovementLineRepository.Totals totals) {
        text(row, 0, "Totales del período", styles.bold);
        number(row, 7, totals.inQuantity(), styles.quantity);
        number(row, 9, totals.inValue(), styles.money);
        number(row, 10, totals.outQuantity(), styles.quantity);
        number(row, 12, totals.outValue(), styles.money);
    }

    private static void text(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private static void number(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? 0 : value.doubleValue());
        cell.setCellStyle(style);
    }

    public record Header(Product product, Warehouse warehouse, LocalDate from, LocalDate to, String costMethod) {
    }

    private static final class Styles {

        private final CellStyle title;
        private final CellStyle bold;
        private final CellStyle header;
        private final CellStyle dateTime;
        private final CellStyle quantity;
        private final CellStyle cost;
        private final CellStyle money;

        Styles(SXSSFWorkbook workbook) {
            DataFormat format = workbook.createDataFormat();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);

            title = workbook.createCellStyle();
            title.setFont(titleFont);
            bold = workbook.createCellStyle();
            bold.setFont(boldFont);
            header = workbook.createCellStyle();
            header.setFont(boldFont);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);
            dateTime = workbook.createCellStyle();
            dateTime.setDataFormat(format.getFormat("yyyy-mm-dd hh:mm"));
            quantity = workbook.createCellStyle();
            quantity.setDataFormat(format.getFormat("#,##0.0000"));
            cost = workbook.createCellStyle();
            cost.setDataFormat(format.getFormat("#,##0.000000"));
            money = workbook.createCellStyle();
            money.setDataFormat(format.getFormat("\"$\"#,##0.00"));
        }
    }
}
