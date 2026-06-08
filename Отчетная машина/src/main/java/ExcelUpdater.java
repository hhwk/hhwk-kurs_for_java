import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.List;

/**
 * Writes report segments into an existing Excel file.
 *
 * Data layout (columns A–D, 1-indexed):
 *   A – № (row number)
 *   B – Позиция (start timecode after speed adjustment)
 *   C – Продолжительность (duration = adjusted end − adjusted start)
 *   D – Комментарий (comment text)
 *
 * Columns E and beyond (reference tables, notes, etc.) are never touched.
 */
public class ExcelUpdater {

    /** Number of data columns managed by this class (A through D). */
    private static final int DATA_COLUMNS = 4;

    public void update(File excelFile, Report report, double speed, Config config) throws IOException {
        // Use XSSFWorkbook directly to avoid service-loader issues in fat JARs
        Workbook workbook;
        try (InputStream in = new FileInputStream(excelFile)) {
            workbook = new XSSFWorkbook(in);
        }

        Sheet sheet = findSheet(workbook, config.getDefaultSheet());

        // default.start.row is 1-based (e.g. 13 → POI index 12)
        int firstDataRow = config.getDefaultStartRow() - 1;

        if (config.isClearExistingData()) {
            clearDataColumns(sheet, firstDataRow);
        }

        List<Segment> segments = report.getSegments();
        for (int i = 0; i < segments.size(); i++) {
            Segment seg = segments.get(i);
            int rowIndex = firstDataRow + i;

            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
            }

            setNumeric(row, 0, i + 1);
            setText(row, 1, seg.getAdjustedStart(speed).toString());
            setText(row, 2, seg.getAdjustedDuration(speed).toString());
            setText(row, 3, seg.getComment());
        }

        // Write back to the same file
        try (OutputStream out = new FileOutputStream(excelFile)) {
            workbook.write(out);
        }
        workbook.close();

        System.out.println("  Записано строк: " + segments.size());
        System.out.println("  Файл сохранён: " + excelFile.getAbsolutePath());
    }

    /**
     * Finds the sheet by name. Falls back to the first sheet with a warning
     * if the exact name is not found (handles minor encoding differences).
     */
    private static Sheet findSheet(Workbook wb, String sheetName) throws IOException {
        Sheet sheet = wb.getSheet(sheetName);
        if (sheet != null) return sheet;

        // Case-insensitive fallback
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            if (wb.getSheetName(i).equalsIgnoreCase(sheetName)) {
                System.out.println("Предупреждение: лист '" + sheetName
                    + "' не найден, используется '" + wb.getSheetName(i) + "'");
                return wb.getSheetAt(i);
            }
        }

        if (wb.getNumberOfSheets() > 0) {
            System.out.println("Предупреждение: лист '" + sheetName
                + "' не найден, используется первый лист: " + wb.getSheetName(0));
            return wb.getSheetAt(0);
        }

        throw new IOException("Книга не содержит листов");
    }

    /**
     * Clears only columns A–D starting from firstDataRow.
     * Rows beyond the current last row are ignored.
     * All other columns (E+) are left untouched.
     */
    private static void clearDataColumns(Sheet sheet, int firstDataRow) {
        for (int r = firstDataRow; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int col = 0; col < DATA_COLUMNS; col++) {
                Cell cell = row.getCell(col);
                if (cell != null) {
                    cell.setBlank();
                }
            }
        }
    }

    private static void setText(Row row, int col, String value) {
        getOrCreate(row, col).setCellValue(value);
    }

    private static void setNumeric(Row row, int col, double value) {
        getOrCreate(row, col).setCellValue(value);
    }

    private static Cell getOrCreate(Row row, int col) {
        Cell cell = row.getCell(col);
        return (cell != null) ? cell : row.createCell(col);
    }
}
